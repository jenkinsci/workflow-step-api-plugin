package org.jenkinsci.plugins.workflow.steps;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.security.ACL;
import hudson.util.DaemonThreadFactory;
import hudson.util.NamingThreadFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.annotation.Nonnull;
import jenkins.model.Jenkins;
import jenkins.security.NotReallyRoleSensitiveCallable;
import org.acegisecurity.Authentication;

/**
 * Similar to {@link SynchronousStepExecution} (it executes synchronously too) but it does not block the CPS VM thread.
 * @see StepExecution
 * @param <T> the type of the return value (may be {@link Void})
 */
public abstract class SynchronousNonBlockingStepExecution<T> extends StepExecution {

    private transient volatile Future<?> task;
    private transient String threadName;

    private static ExecutorService executorService;

    protected SynchronousNonBlockingStepExecution(@Nonnull StepContext context) {
        super(context);
    }

    /**
     * Meat of the execution.
     *
     * When this method returns, a step execution is over.
     */
    protected abstract T run() throws Exception;

    @Override
    public final boolean start() throws Exception {
        final Authentication auth = Jenkins.getAuthentication();
        task = getExecutorService().submit(new Runnable() {
            @SuppressFBWarnings(value="SE_BAD_FIELD", justification="not serializing anything here")
            @Override public void run() {
                try {
                    getContext().onSuccess(ACL.impersonate(auth, new NotReallyRoleSensitiveCallable<T, Exception>() {
                        @Override public T call() throws Exception {
                            threadName = Thread.currentThread().getName();
                            return SynchronousNonBlockingStepExecution.this.run();
                        }
                    }));
                } catch (Exception e) {
                    getContext().onFailure(e);
                }
            }
        });
        return false;
    }

    /**
     * If the computation is going synchronously, try to cancel that.
     */
    @Override
    public void stop(Throwable cause) throws Exception {
        if (task != null) {
            task.cancel(true);
        }
    }

    @Override
    public void onResume() {
        getContext().onFailure(new Exception("Resume after a restart not supported for non-blocking synchronous steps"));
    }

    @Override public @Nonnull String getStatus() {
        if (threadName != null) {
            return "running in thread: " + threadName;
        } else {
            return "not yet scheduled";
        }
    }

    static synchronized ExecutorService getExecutorService() {
        if (executorService == null) {
            executorService = Executors.newCachedThreadPool(new NamingThreadFactory(new DaemonThreadFactory(), "org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution"));
        }
        return executorService;
    }

}
