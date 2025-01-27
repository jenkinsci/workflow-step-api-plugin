package org.jenkinsci.plugins.workflow.steps;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.security.ACL;
import java.util.concurrent.Future;
import edu.umd.cs.findbugs.annotations.NonNull;
import jenkins.model.Jenkins;
import jenkins.security.NotReallyRoleSensitiveCallable;
import org.springframework.security.core.Authentication;

/**
 * Similar to {@link AbstractSynchronousStepExecution} (it executes synchronously too) but it does not block the CPS VM thread.
 * @see StepExecution
 * @param <T> the type of the return value (may be {@link Void})
 * @deprecated Extend {@link SynchronousNonBlockingStepExecution} and avoid Guice.
 */
@Deprecated
public abstract class AbstractSynchronousNonBlockingStepExecution<T> extends AbstractStepExecutionImpl {

    private transient volatile Future<?> task;
    private transient String threadName;

    protected AbstractSynchronousNonBlockingStepExecution() {
    }

    protected AbstractSynchronousNonBlockingStepExecution(StepContext context) {
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
        final Authentication auth = Jenkins.getAuthentication2();
        task = SynchronousNonBlockingStepExecution.getExecutorService().submit(new Runnable() {
            @SuppressFBWarnings(value="SE_BAD_FIELD", justification="not serializing anything here")
            @Override public void run() {
                try {
                    getContext().onSuccess(ACL.impersonate2(auth, new NotReallyRoleSensitiveCallable<T, Exception>() {
                        @Override public T call() throws Exception {
                            threadName = Thread.currentThread().getName();
                            return AbstractSynchronousNonBlockingStepExecution.this.run();
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
        getContext().onFailure(new SynchronousResumeNotSupportedException());
    }

    @Override public @NonNull String getStatus() {
        if (threadName != null) {
            return "running in thread: " + threadName;
        } else {
            return "not yet scheduled";
        }
    }

}
