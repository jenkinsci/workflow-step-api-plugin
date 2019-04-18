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

import hudson.ExtensionPoint;
import java.io.IOException;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Allows {@link StepContext#get} to provide a dynamically computed value.
 * <p>This is registered as an extension, so it may be injected into any build.
 * If you would like to restrict action to a particular step block,
 * use {@link BodyInvoker#withContext} to insert some serializable struct
 * that the dynamic context implementation will look for.
 */
public interface DynamicContext extends ExtensionPoint {

    /**
     * Restricted version of {@link StepContext} used only for delegation in {@link #get(Class, DelegatedContext)}.
     */
    public interface DelegatedContext {

        /**
         * Look for objects of the same or another type defined in this context.
         * <p>A {@link DynamicContext} may use this handle to query the {@link StepContext} for objects of other types.
         * <p>It may even look for objects of the same type as is being requested,
         * as is typically done for merge calls such as those enumerated in {@link BodyInvoker#withContext},
         * and it may ask for objects also provided by some {@link DynamicContext};
         * but no recursive call to {@link DynamicContext#get(Class, DelegatedContext)}
         * on the same type from the same extension will be made—null will be returned instead.
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
     @CheckForNull <T> T get(Class<T> key, DelegatedContext context) throws IOException, InterruptedException;

    /**
     * A convenience subclass for the common case that you are returning only one kind of object.
     * @param <T> the type of object
     */
    abstract class Typed<T> implements DynamicContext {

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
