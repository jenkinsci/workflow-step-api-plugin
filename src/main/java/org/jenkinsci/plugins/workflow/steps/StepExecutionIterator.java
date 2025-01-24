package org.jenkinsci.plugins.workflow.steps;

import com.google.common.base.Function;
import com.google.common.util.concurrent.ListenableFuture;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.Util;
import java.util.function.Consumer;

/**
 * Enumerates active running {@link StepExecution}s in the system.
 * @see StepExecution#acceptAll(Class, Consumer)
 * @author Kohsuke Kawaguchi
 */
public abstract class StepExecutionIterator implements ExtensionPoint {
    /**
     * Finds all the ongoing {@link StepExecution} and apply the action.
     *
     * The control flow is inverted because a major use case (workflow) loads
     * {@link StepExecution}s asynchronously (for example when workflow run
     * is blocked trying to restore pickles.)
     *
     * @return
     *      {@link ListenableFuture} to signal the completion of the application.
     */
    public /* abstract */ ListenableFuture<?> accept(Consumer<StepExecution> f) {
        return Util.ifOverridden(() -> apply(toGuava(f)), StepExecutionIterator.class, getClass(), "apply", Function.class);
    }

    /**
     * @deprecated use {@link #accept}
     */
    @Deprecated
    public /* abstract */ ListenableFuture<?> apply(Function<StepExecution, Void> f) {
        return Util.ifOverridden(() -> accept(fromGuava(f)), StepExecutionIterator.class, getClass(), "accept", Consumer.class);
    }

    private static <T> Consumer<T> fromGuava(Function<T, Void> func) {
        return func::apply;
    }

    private static <T> Function<T, Void> toGuava(Consumer<T> consumer) {
        return v -> {
            consumer.accept(v);
            return null;
        };
    }

    public static ExtensionList<StepExecutionIterator> all() {
        return ExtensionList.lookup(StepExecutionIterator.class);
    }
}
