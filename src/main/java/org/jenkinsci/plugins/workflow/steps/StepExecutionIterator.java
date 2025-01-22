package org.jenkinsci.plugins.workflow.steps;

import com.google.common.util.concurrent.ListenableFuture;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.Util;
import java.util.function.Function;

/**
 * Enumerates active running {@link StepExecution}s in the system.
 * @see StepExecution#applyAll(Class, Function)
 * @author Kohsuke Kawaguchi
 */
public abstract class StepExecutionIterator implements ExtensionPoint {
    /**
     * Finds all the ongoing {@link StepExecution} and apply the function.
     *
     * The control flow is inverted because a major use case (workflow) loads
     * {@link StepExecution}s asynchronously (for example when workflow run
     * is blocked trying to restore pickles.)
     *
     * @return
     *      {@link ListenableFuture} to signal the completion of the application.
     */
    public /* abstract */ ListenableFuture<?> apply(Function<StepExecution, Void> f) {
        return Util.ifOverridden(
                () -> apply(toGuava(f)),
                StepExecutionIterator.class,
                getClass(),
                "apply",
                com.google.common.base.Function.class);
    }

    /**
     * @deprecated use {@link #apply(Function)}
     */
    @Deprecated
    public /* abstract */ ListenableFuture<?> apply(com.google.common.base.Function<StepExecution, Void> f) {
        return Util.ifOverridden(
                () -> apply(fromGuava(f)), StepExecutionIterator.class, getClass(), "apply", Function.class);
    }

    private static <T, R> Function<T, R> fromGuava(com.google.common.base.Function<T, R> func) {
        return func::apply;
    }

    private static <T, R> com.google.common.base.Function<T, R> toGuava(Function<T, R> func) {
        return func::apply;
    }

    public static ExtensionList<StepExecutionIterator> all() {
        return ExtensionList.lookup(StepExecutionIterator.class);
    }
}
