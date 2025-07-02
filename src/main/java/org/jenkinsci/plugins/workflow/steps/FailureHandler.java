package org.jenkinsci.plugins.workflow.steps;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

import java.io.Serializable;

/**
 * Interface destined for {@link StepContext#get} to rewrite or wrap {@link Throwable}s.
 * Pass into {@link BodyInvoker#withContext}.
 */
@FunctionalInterface
@Restricted(Beta.class)
public interface FailureHandler extends Serializable {

    /**
     * Intercept the supplied {@code Throwable}.
     * @param ctx the context of the step being executed
     * @param t the original {@code Throwable}
     * @return the new {@code Throwable} to propagate
     */
    @NonNull
    Throwable handle(@NonNull StepContext ctx, @NonNull Throwable t);

    /**
     * Looks up in the current context for a {@link FailureHandler} and runs it against the given {@code Throwable}.
     * @param ctx the context of the step being executed
     * @param t the original {@code Throwable}
     * @return the new {@code Throwable} to propagate
     */
    static @NonNull Throwable apply(@NonNull StepContext ctx,
                                    @NonNull Throwable t) {
        try {
            FailureHandler h = ctx.get(FailureHandler.class);
            if (h == null) {
                return t;
            }
            return h.handle(ctx, t);
        } catch (Throwable x) {
            t.addSuppressed(x);
            return t;
        }
    }

    /**
     * Merge together two {@link FailureHandler}.
     * @param original an original one, such as one already found in a context
     * @param subsequent what you are adding
     * @return a {@link FailureHandler} which runs them both in that sequence (or, as a convenience, just {@code subsequent} in case {@code original} is null)
     */
    static FailureHandler merge(@CheckForNull FailureHandler original, @NonNull FailureHandler subsequent) {
        if (original == null) {
            return subsequent;
        }
        return (ctx, t) -> subsequent.handle(ctx, original.handle(ctx, t));
    }
}
