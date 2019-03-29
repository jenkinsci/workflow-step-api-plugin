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

import java.io.IOException;
import java.io.Serializable;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Allows {@link StepContext#get} to provide a dynamically computed value.
 */
public abstract class DynamicContext implements Serializable {

    /**
     * Restricted version of {@link StepContext} used only for delegation in {@link #get(Class, DelegatedContext)}.
     */
    public interface DelegatedContext {

        /**
         * Look for objects of the same or another type defined in this context.
         * <p>A {@link DynamicContext} may use this handle to query the {@link StepContext} for objects of other types.
         * <p>It may even look for objects of the same type as is being requested,
         * as is typically done for merge calls such as those enumerated in {@link BodyInvoker#withContext},
         * and it may ask for objects also provided by {@link DynamicContext};
         * but no recursive call to {@link DynamicContext#get(Class, DelegatedContext)} on the same type will be made
         * (even on different {@link DynamicContext} instances)—null will be returned instead.
         * <p>Note that since merge calls may be applied at different scopes,
         * a non-idempotent merge may be observed as multiply applied in a nested scope.
         * @param <T> same as {@link StepContext#get}
         * @param key same as {@link StepContext#get}
         * @return same as {@link StepContext#get}, but may additionally return null to break recursion
         * @throws IOException same as {@link StepContext#get}
         * @throws InterruptedException same as {@link StepContext#get}
         */
         @Nullable <T> T get(Class<T> key) throws IOException, InterruptedException;

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
         * As {@link #get(Class, DelegatedContext)}.
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

}
