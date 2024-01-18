package org.jenkinsci.plugins.workflow.steps;

import com.google.common.base.Function;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import jakarta.inject.Inject;
import jenkins.model.queue.AsynchronousExecution;
import jenkins.util.Timer;

/**
 * Scoped to a single execution of {@link Step}, and provides insights into what's going on
 * asynchronously and aborting the activity if need be.
 *
 * <p>
 * {@link StepExecution}s are persisted whenever used to run an asynchronous operation.
 *
 * @author Kohsuke Kawaguchi
 * @author Jesse Glick
 * @see Step#start(StepContext)
 * @see StepExecutions#block
 */
public abstract class StepExecution implements Serializable {

    private static final Logger LOGGER = Logger.getLogger(StepExecution.class.getName());
    
    @Inject private StepContext context;

    /**
     * Default constructor used by injection.
     * @see AbstractStepImpl#start
     * @deprecated Avoid Guice.
     */
    @Deprecated
    protected StepExecution() {
    }

    /**
     * If manually created, {@link StepContext} must be passed in.
     */
    protected StepExecution(@NonNull StepContext context) {
        this.context = context;
    }

    public @NonNull StepContext getContext() {
        if (context == null) {
            throw new IllegalStateException("you must either pass in a StepContext to the StepExecution constructor, or have the StepExecution be created automatically");
        }
        return context;
    }

    /**
     * Start execution of something and report the end result back to the given callback.
     *
     * <p>Arguments are passed when {@linkplain StepDescriptor#newInstance(Map) instantiating steps}.
     *
     * <p>This method will run in the CPS VM thread and as such should not perform I/O or block.
     * Use {@link SynchronousNonBlockingStepExecution} or {@link GeneralNonBlockingStepExecution} as needed.
     *
     * @return
     *      true if the execution of this step has synchronously completed before this method returns.
     *      It is the callee's responsibility to set the return value via {@link StepContext#onSuccess(Object)}
     *      or {@link StepContext#onFailure(Throwable)}.
     *
     *      false if the asynchronous execution has started and that {@link StepContext}
     *      will be notified when the result comes in. (Note that the nature of asynchrony is such that it is possible
     *      for the {@link StepContext} to be already notified before this method returns.)
     * @throws Exception
     *      if any exception is thrown, {@link Step} is assumed to have completed abnormally synchronously
     *      (as if {@link StepContext#onFailure} is called and the method returned true.)
     */
    public abstract boolean start() throws Exception;

    /**
     * May be called if someone asks a running step to abort.
     * <p>
     * Just like {@link Thread#interrupt()},
     * the step might not honor the request immediately.
     * Multiple stop requests might be sent.
     * It is always responsible for calling {@link StepContext#onSuccess(Object)} or (more likely)
     * {@link StepContext#onFailure(Throwable)} eventually,
     * whether or not it was asked to stop.
     * <p>
     * The default behavior is to call {@link StepContext#onFailure} immediately.
     * This may be overridden by non-block-scoped steps which wish to halt some processing prior to failing the step,
     * or even to send a cancellation signal to some process but leave the step running until that is handled gracefully.
     * Block-scoped steps which merely call their bodies generally need not override this method,
     * as the interrupt will be sent to the step(s) actually running at the time
     * (so no special call to {@link BodyExecution#cancel(Throwable)} is needed),
     * though an override may be necessary if it is possible for there to be no body currently running.
     * <p>
     * This method is meant to be used by {@code FlowExecution}, not called from UI or other human requests to pause.
     * Use {@link BodyExecution#cancel(Throwable)} for programmatic cancellation of bodies.
     *
     * @param cause
     *      Contextual information that lets the step know what resulted in stopping an executing step,
     *      passed in the hope that this will assist diagnostics.
     */
    public void stop(@NonNull Throwable cause) throws Exception {
        getContext().onFailure(cause);
    }

    /**
     * Called when {@link StepExecution} is brought back into memory after restart.
     * Convenient for re-establishing the polling.
     * <p>Currently not permitted to throw exceptions, but may report errors via {@link StepContext#onFailure}.
     * @see SynchronousResumeNotSupportedException
     */
    public void onResume() {}

    /**
     * May be overridden to provide specific information about what a step is currently doing, for diagnostic purposes.
     * Typical format should be a short, lowercase phrase.
     * It should not be localized as this is intended for use by developers as well as users.
     * May include technical details about Jenkins internals if relevant.
     * @return current status, or null if unimplemented
     * @see #getStatusBounded
     */
    public @CheckForNull String getStatus() {
        return null;
    }

    /**
     * Allows a step to indicate that {@link AsynchronousExecution#blocksRestart} should be true.
     * Typically this would be true if {@link #getStatus} indicates that the step is in the middle of something active,
     * as opposed to waiting for an external event or a body to complete.
     * <p>Note that activity in the CPS VM thread automatically blocks restart,
     * so overriding this is only necessary for steps using a background thread,
     * such as {@link SynchronousNonBlockingStepExecution} or {@link GeneralNonBlockingStepExecution}.
     * @return false by default
     */
    public boolean blocksRestart() {
        return false;
    }

    /**
     * Like {@link #getStatus} but more robust.
     * Waits a most a given amount of time for the result, and handles {@link RuntimeException}s.
     * @param timeout maximum amount of time to spend
     * @param unit time unit
     */
    public final @CheckForNull String getStatusBounded(long timeout, TimeUnit unit) {
        Future<String> task = null;
        try {
            task = Timer.get().submit(new Callable<String>() {
                @Override public String call() throws Exception {
                    return getStatus();
                }
            });
            return task.get(timeout, unit);
        } catch (Exception x) { // ExecutionException, RejectedExecutionException, CancellationException, TimeoutException, InterruptedException
            if (task != null) {
                task.cancel(true); // in case of TimeoutException especially, we do not want this thread continuing
            }
            LOGGER.log(Level.FINE, "failed to check status of " + super.toString(), x);
            return x.toString();
        }
    }

    /**
     * Apply the given function to all the active running {@link StepExecution}s in the system.
     *
     * @return
     *      Future object that signals when the function application is complete.
     * @see StepExecutionIterator
     */
    public static ListenableFuture<?> applyAll(Function<StepExecution,Void> f) {
        List<ListenableFuture<?>> futures = new ArrayList<>();
        for (StepExecutionIterator i : StepExecutionIterator.all())
            futures.add(i.apply(f));
        return Futures.allAsList(futures);
    }

    /**
     * Applies only to the specific subtypes.
     */
    public static <T extends StepExecution> ListenableFuture<?> applyAll(final Class<T> type, final Function<T,Void> f) {
        return applyAll(new Function<StepExecution, Void>() {
            @Override
            public Void apply(StepExecution e) {
                if (type.isInstance(e))
                    f.apply(type.cast(e));
                return null;
            }
        });
    }

    private static final long serialVersionUID = 1L;
}
