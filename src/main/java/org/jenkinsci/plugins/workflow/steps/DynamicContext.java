/*
 * The MIT License
 *
 * Copyright 2019 CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jenkinsci.plugins.workflow.steps;

import hudson.console.ConsoleLogFilter;
import java.io.IOException;
import java.io.Serializable;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

/**
 * Allows {@link StepContext#get} to provide a dynamically computed value.
 */
public abstract class DynamicContext implements Serializable {

    private static final Logger LOGGER = Logger.getLogger(DynamicContext.class.getName());

    /**
     * Restricted version of {@link StepContext} used only for delegation in {@link #get(Class, DelegatedContext)}.
     */
    public interface DelegatedContext {

        /**
         * Look for objects of the same or another type defined in this context.
         * A {@link DynamicContext} may use this handle to query the {@link StepContext} for objects of other types.
         * It may even look for objects of the same type as is being requested,
         * as is typically done for merge calls such as those enumerated in {@link BodyInvoker#withContext},
         * and it may ask for objects also provided by {@link DynamicContext},
         * but no recursive call to {@link DynamicContext#get(Class, DelegatedContext)} on the same type will be made
         * (even on different {@link DynamicContext} instances)—null will be returned instead.
         * @param <T> same as {@link StepContext#get}
         * @param key same as {@link StepContext#get}
         * @return same as {@link StepContext#get}, but may additionally return null to break recursion
         * @throws IOException same as {@link StepContext#get}
         * @throws InterruptedException same as {@link StepContext#get}
         */
         @CheckForNull <T> T get(Class<T> key) throws IOException, InterruptedException;

    }

    /**
     * Actually look up a given object in a particular context.
     * @param <T> same as {@link StepContext#get}
     * @param key same as {@link StepContext#get}
     * @param context the context being queried
     * @return same as {@link StepContext#get}
     * @throws IOException same as {@link StepContext#get}
     * @throws InterruptedException same as {@link StepContext#get}
     */
    public abstract @CheckForNull <T> T get(Class<T> key, DelegatedContext context) throws IOException, InterruptedException;

    /**
     * Applies a more specific dynamic context to a nested scope.
     * @param original any dynamic context object found in the parent scope
     * @param subsequent overrides
     * @return a merger which preferentially looks up objects in {@code subsequent}
     */
    public static @Nonnull DynamicContext merge(@CheckForNull DynamicContext original, @Nonnull DynamicContext subsequent) {
        if (original == null) {
            return subsequent;
        } else {
            return new Merged(original, subsequent);
        }
    }
    private static final class Merged extends DynamicContext {

        private static final long serialVersionUID = 1;

        private final @Nonnull DynamicContext original;
        private final @Nonnull DynamicContext subsequent;

        Merged(DynamicContext original, DynamicContext subsequent) {
            this.original = original;
            this.subsequent = subsequent;
        }

        @Override public <T> T get(Class<T> key, DelegatedContext context) throws IOException, InterruptedException {
            T val = subsequent.get(key, context);
            if (val != null) {
                return val;
            } else {
                return original.get(key, context);
            }
        }

        @Override public String toString() {
            return "DynamicContext.Merged[" + original + ", " + subsequent + "]";
        }
        
    }

    /**
     * A convenience subclass for the common case that you are returning only one kind of object.
     * @param <T> the type of object
     */
    public static abstract class Typed<T> extends DynamicContext {

        private static final long serialVersionUID = 1;

        /**
         * A type token.
         */
        protected abstract @Nonnull Class<T> type();

        /**
         * As {@link #get(Class, StaticContext)}.
         */
        protected abstract @CheckForNull T get(DelegatedContext context) throws IOException, InterruptedException;

        @Override public final <T> T get(Class<T> key, DelegatedContext context) throws IOException, InterruptedException {
            if (key.isAssignableFrom(type())) {
                return key.cast(get(context));
            } else {
                return null;
            }
        }

    }

    /**
     * Wraps a dynamic context with logic to prevent it from being instantiated in nested scopes.
     * Useful for object types which are merged (as in {@link BodyInvoker#withContext}),
     * especially when the merge is not idempotent (as for example some {@link ConsoleLogFilter}s).
     * <p>There is no need to call {@link #merge} in this case.
     */
    public static void invokeNonNesting(@Nonnull BodyInvoker invoker, @Nonnull StepContext context, @Nonnull DynamicContext dynamicContext) throws IOException, InterruptedException {
        DynamicContext original = context.get(DynamicContext.class);
        LOGGER.log(Level.FINE, "nesting on {0} + {1}", new Object[] {original, dynamicContext});
        DynamicContext mergedContext = merge(original, dynamicContext);
        invoker.withContexts(new NonNesting(mergedContext), new NestingBlock(context.get(NestingBlock.class), mergedContext));
    }
    private static final class NonNesting extends DynamicContext {
        
        private static final long serialVersionUID = 1;

        private final @Nonnull DynamicContext original;

        NonNesting(DynamicContext original) {
            this.original = original;
        }

        @Override public <T> T get(Class<T> key, DelegatedContext context) throws IOException, InterruptedException {
            for (NestingBlock nestingBlock = context.get(NestingBlock.class); ; nestingBlock = nestingBlock.parent) {
                assert nestingBlock != null;
                if (nestingBlock.dynamicContext == original) {
                    if (nestingBlock.value != null) {
                        if (key.isInstance(nestingBlock.value)) {
                            LOGGER.log(Level.FINE, "reusing {0} from {1}", new Object[] {nestingBlock.value, original});
                            // TODO wrong, should return null, but only if called from a different scope
                            return key.cast(nestingBlock.value);
                        } else {
                            return null;
                        }
                    } else {
                        T value = original.get(key, context);
                        if (value == null) {
                            return null;
                        } else {
                            LOGGER.log(Level.FINE, "registering {0} from {1}", new Object[] {value, original});
                            nestingBlock.value = value;
                            return value;
                        }
                    }
                }
            }
        }

        @Override public String toString() {
            return "NonNesting[" + original + "]";
        }

    }
    /** Contextual object recording which have been used. */
    private static final class NestingBlock implements Serializable {

        private static final long serialVersionUID = 1;

        private final @CheckForNull NestingBlock parent;
        private final @Nonnull DynamicContext dynamicContext;
        private Object value;

        private NestingBlock(@CheckForNull NestingBlock parent, @Nonnull DynamicContext dynamicContext) {
            this.parent = parent;
            this.dynamicContext = dynamicContext;
        }

    }

}
