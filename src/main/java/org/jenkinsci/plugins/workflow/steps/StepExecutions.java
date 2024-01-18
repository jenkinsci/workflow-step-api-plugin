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

/**
 * Builder for simple {@link StepExecution} implementations.
 * Convenient for use from {@link Step#start} when a permanent serial form is unimportant.
 * Use {@link StepContext#get} to access contextual objects as usual.
 * <p>The lambda arguments may refer to {@link Step} parameter fields directly.
 */
public class StepExecutions {

    @FunctionalInterface
    public interface SynchronousBody {
        Object call(StepContext context) throws Exception;
    }

    @FunctionalInterface
    public interface SynchronousBodyVoid {
        void call(StepContext context) throws Exception;
        default SynchronousBody asReturn() {
            return c -> {
                call(c);
                return null;
            };
        }
    }

    /**
     * Creates a {@link SynchronousStepExecution} running a given block with a return value.
     */
    public static StepExecution synchronous(StepContext context, SynchronousBody body) {
        return new SynchronousImpl(context, body);
    }

    /**
     * Creates a {@link SynchronousStepExecution} running a given block.
     */
    public static StepExecution synchronousVoid(StepContext context, SynchronousBodyVoid body) {
        return new SynchronousImpl(context, body.asReturn());
    }

    private static class SynchronousImpl extends SynchronousStepExecution<Object> {
        private static final long serialVersionUID = 1;
        private transient final SynchronousBody body;
        SynchronousImpl(StepContext context, SynchronousBody body) {
            super(context);
            this.body = body;
        }
        @Override protected Object run() throws Exception {
            return body.call(getContext());
        }
    }

    /**
     * Creates a {@link SynchronousNonBlockingStepExecution} running a given block with a return value.
     */
    public static StepExecution synchronousNonBlocking(StepContext context, SynchronousBody body) {
        return new SynchronousNonBlockingImpl(context, body);
    }

    /**
     * Creates a {@link SynchronousNonBlockingStepExecution} running a given block.
     */
    public static StepExecution synchronousNonBlockingVoid(StepContext context, SynchronousBodyVoid body) {
        return new SynchronousNonBlockingImpl(context, body.asReturn());
    }

    private static class SynchronousNonBlockingImpl extends SynchronousNonBlockingStepExecution<Object> {
        private static final long serialVersionUID = 1;
        private transient final SynchronousBody body;
        SynchronousNonBlockingImpl(StepContext context, SynchronousBody body) {
            super(context);
            this.body = body;
        }
        @Override protected Object run() throws Exception {
            return body.call(getContext());
        }
    }

    @FunctionalInterface
    public interface BlockBody {
        void call(StepContext context, BodyInvoker invoker) throws Exception;
    }

    /**
     * Creates a block-scoped execution allowing various initial actions including {@link BodyInvoker#withContext}.
     * There is no pluggable final action, since {@link BodyExecutionCallback#wrap} is used, so it is a simple tail call.
     */
    public static StepExecution block(StepContext context, BlockBody body) {
        return new BlockImpl(context, body);
    }

    private static class BlockImpl extends StepExecution {
        private static final long serialVersionUID = 1;
        private transient final BlockBody body;
        BlockImpl(StepContext context, BlockBody body) {
            super(context);
            this.body = body;
        }
        @Override public boolean start() throws Exception {
            StepContext context = getContext();
            BodyInvoker invoker = context.newBodyInvoker();
            body.call(context, invoker);
            invoker.withCallback(BodyExecutionCallback.wrap(context)).start();
            return false;
        }
    }

    private StepExecutions() {}

}
