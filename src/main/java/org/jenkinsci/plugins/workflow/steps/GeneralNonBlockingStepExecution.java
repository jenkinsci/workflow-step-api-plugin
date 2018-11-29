/*
 * The MIT License
 *
 * Copyright 2018 CloudBees, Inc.
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

import hudson.security.ACL;
import hudson.security.ACLContext;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import javax.annotation.Nonnull;
import jenkins.model.Jenkins;
import org.acegisecurity.Authentication;

/**
 * Generalization of {@link SynchronousNonBlockingStepExecution} that can be used for block-scoped steps.
 * The step may at any given time be running either CPS VM code, background code, or waiting for events (for example running a block).
 */
public abstract class GeneralNonBlockingStepExecution extends StepExecution {

    private transient volatile Future<?> task;
    private String threadName;
    private transient boolean stopping;

    protected GeneralNonBlockingStepExecution(StepContext context) {
        super(context);
    }

    /**
     * Initiate background work that should not block the CPS VM thread.
     * Call this from a CPS VM thread, such as from {@link #start} or {@link BodyExecutionCallback#onSuccess}.
     * The block may finish by calling {@link BodyInvoker#start}, {@link StepContext#onSuccess}, etc.
     * @param block some code to run in a utility thread
     */
    protected final void run(Callable<Void> block) {
        if (stopping) {
            return;
        }
        final Authentication auth = Jenkins.getAuthentication();
        task = SynchronousNonBlockingStepExecution.getExecutorService().submit(() -> {
            threadName = Thread.currentThread().getName();
            try {
                try (ACLContext acl = ACL.as(auth)) {
                    block.call();
                }
            } catch (Throwable e) {
                if (!stopping) {
                    getContext().onFailure(e);
                }
            } finally {
                threadName = null;
                task = null;
            }
        });
    }

    /**
     * If the computation is going synchronously, try to cancel that.
     */
    @Override
    public void stop(Throwable cause) throws Exception {
        stopping = true;
        if (task != null) {
            task.cancel(true);
        }
        super.stop(cause);
    }

    @Override
    public void onResume() {
        if (threadName != null) {
            getContext().onFailure(new Exception("Resume after a restart not supported while running background code"));
        }
    }

    @Override public @Nonnull String getStatus() {
        if (threadName != null) {
            return "running in thread: " + threadName;
        } else {
            return "not currently scheduled, or running blocks";
        }
    }

}
