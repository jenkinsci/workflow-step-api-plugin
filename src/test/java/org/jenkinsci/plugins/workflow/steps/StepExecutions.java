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

import java.io.Serializable;

/**
 * Builder for simple {@link StepExecution} implementations.
 * Convenient for use from {@link Step#start} during tests, when a permanent serial form is unimportant.
 * Use {@link StepContext#get} to access contextual objects as usual.
 * <p>The lambda arguments may be {@link Step} instance method references like {@code this::run}
 * so long as the {@link Step} is declared to be {@link Serializable}.
 * That allows you to use step parameter fields directly.
 */
public class StepExecutions {

    @FunctionalInterface
    public interface SynchronousBody extends Serializable {
        Object call(StepContext context) throws Exception;
    }

    /**
     * Creates a {@link SynchronousStepExecution} running a given block.
     */
    public static StepExecution synchronous(StepContext context, SynchronousBody body) {
        return new SynchronousStepExecution<Object>(context) {
            @Override protected Object run() throws Exception {
                return body.call(getContext());
            }
        };
    }
    
    /**
     * Creates a {@link SynchronousNonBlockingStepExecution} running a given block.
     */
    public static StepExecution synchronousNonBlocking(StepContext context, SynchronousBody body) {
        return new SynchronousNonBlockingStepExecution<Object>(context) {
            @Override protected Object run() throws Exception {
                return body.call(getContext());
            }
        };
    }

    @FunctionalInterface
    public interface BlockBody extends Serializable {
        void call(StepContext context, BodyInvoker invoker) throws Exception;
    }

    /**
     * Creates a block-scoped execution allowing various initial actions including {@link BodyInvoker#withContext}.
     * There is no pluggable final action, since {@link BodyExecutionCallback#wrap} is used, so it is a simple tail call.
     */
    public static StepExecution block(StepContext context, BlockBody body) {
        return new StepExecution(context) {
            @Override public boolean start() throws Exception {
                StepContext context = getContext();
                BodyInvoker invoker = context.newBodyInvoker();
                body.call(context, invoker);
                invoker.withCallback(BodyExecutionCallback.wrap(context)).start();
                return false;
            }
        };
    }

    private StepExecutions() {}

}
