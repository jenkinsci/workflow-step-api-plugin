/*
 * The MIT License
 *
 * Copyright 2021 CloudBees, Inc.
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
 * May be reported from {@link StepExecution#onResume} when the step does not support resumption.
 * Thrown by default from {@link SynchronousNonBlockingStepExecution},
 * as well as from {@link GeneralNonBlockingStepExecution} when running step code rather than a block.
 * ({@link SynchronousStepExecution} does not even bother implementing this method
 * since it should never be listed as in progress to begin with.)
 */
public class SynchronousResumeNotSupportedException extends Exception {

    /**
     * @deprecated Use {@link #SynchronousResumeNotSupportedException(String)} instead.
     */
    @Deprecated
    public SynchronousResumeNotSupportedException() {
        super("Resume after a restart not supported for non-blocking synchronous steps");
    }

    public SynchronousResumeNotSupportedException(String stepDisplayFunctionName) {
        super(String.format("""
            Step `%s` is a non-blocking synchronous step, it doesn't support resume after a restart. \
            You may wrap it within `retry(conditions: [nonresumable()], count: 2) {...}`, \
            or in declarative syntax, use the `retries` option to an `agent` directive) to resume it.""",
                            stepDisplayFunctionName)
        );
    }
}
