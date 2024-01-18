package org.jenkinsci.plugins.workflow.steps;

import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.util.ClassLoaderSanityThreadFactory;
import hudson.util.DaemonThreadFactory;
import hudson.util.NamingThreadFactory;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import edu.umd.cs.findbugs.annotations.NonNull;
import jenkins.model.Jenkins;
import org.acegisecurity.Authentication;

/**
 * Similar to {@link SynchronousStepExecution} (it executes synchronously too) but it does not block the CPS VM thread.
 * @param <T> the type of the return value (may be {@link Void})
 * @see StepExecutions#synchronousNonBlocking
 */
public abstract class SynchronousNonBlockingStepExecution<T> extends StepExecution {

    private transient volatile Future<?> task;
    private transient String threadName;
    private transient volatile Throwable stopCause;

    private static ExecutorService executorService;

    protected SynchronousNonBlockingStepExecution(@NonNull StepContext context) {
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
        task = getExecutorService().submit(() -> {
            threadName = Thread.currentThread().getName();
            try {
                T ret;
                try (ACLContext acl = ACL.as(auth)) {
                    ret = run();
                }
                getContext().onSuccess(ret);
            } catch (Throwable x) {
                if (stopCause == null) {
                    getContext().onFailure(x);
                } else {
                    stopCause.addSuppressed(x);
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
            stopCause = cause;
            task.cancel(true);
        }
        super.stop(cause);
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

    @Override public boolean blocksRestart() {
        return threadName != null;
    }

    static synchronized ExecutorService getExecutorService() {
        if (executorService == null) {
            executorService = Executors.newCachedThreadPool(new NamingThreadFactory(new ClassLoaderSanityThreadFactory(new DaemonThreadFactory()), "org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution"));
        }
        return executorService;
    }

}
