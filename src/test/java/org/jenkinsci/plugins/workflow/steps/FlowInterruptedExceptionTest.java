/*
 * The MIT License
 *
 * Copyright 2025 Damian Szczepanik
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import jenkins.model.CauseOfInterruption;
import org.jenkinsci.plugins.workflow.job.properties.DisableConcurrentBuildsJobProperty;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

/**
 * Tests some specific {@link FlowInterruptedException} APIs
 */
public class FlowInterruptedExceptionTest {

    @Test
    public void getMessageReturnsCauses() {
        // given
        Result result = Result.ABORTED;
        CauseOfInterruption cause1 = new ExceptionCause(new IllegalStateException("something went wrong"));
        CauseOfInterruption cause2 = new CauseOfInterruption.UserInterruption("admin");
        CauseOfInterruption[] causes = {cause1, cause2};

        // when
        FlowInterruptedException exception = new FlowInterruptedException(result, true, causes);

        // then
        assertThat(exception.getMessage(), equalTo(cause1.getShortDescription() + ", " + cause2.getShortDescription()));
    }

    @Test
    public void toStringContainsCauses() {
        // given
        Result result = Result.FAILURE;
        Run run = Mockito.mock(Run.class);
        Mockito.when(run.getDisplayName()).thenReturn("fracture.account");
        CauseOfInterruption cause = new DisableConcurrentBuildsJobProperty.CancelledCause(run);

        // when
        FlowInterruptedException exception = new FlowInterruptedException(result, true, cause);

        // then
        assertThat(exception.toString(), containsString(cause.getShortDescription()));
    }

    // Cause and suppressed exceptions can form a cycle when a parallel step's branches are
    // interrupted simultaneously and cross-attach each other as suppressed. handle() must not
    // recurse forever over such a graph.

    @Test(timeout = 5000)
    public void cyclicExceptionGraphDoesNotOverflowTheStack() {
        // given
        FlowInterruptedException a = new FlowInterruptedException(Result.ABORTED, true);
        FlowInterruptedException b = new FlowInterruptedException(Result.ABORTED, true);
        a.initCause(b);
        b.initCause(a);
        a.addSuppressed(new Throwable("marker-a"));
        a.addSuppressed(b);
        b.addSuppressed(new Throwable("marker-b"));
        b.addSuppressed(a);
        Run run = Mockito.mock(Run.class);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        TaskListener listener = mockListener(out);

        // when
        a.handle(run, listener);

        // then
        String output = out.toString(StandardCharsets.UTF_8);
        assertThat(countOccurrences(output, "marker-a"), equalTo(1));
        assertThat(countOccurrences(output, "marker-b"), equalTo(1));
    }

    // Diamond: the same instance reachable via two distinct, non-cyclic paths shares the
    // visited set, so it prints once. That is a deliberate, accepted trade-off.

    @Test
    public void diamondSharedExceptionIsPrintedOnce() {
        // given
        FlowInterruptedException shared = new FlowInterruptedException(Result.ABORTED, true);
        shared.addSuppressed(new Throwable("shared-marker"));
        FlowInterruptedException branch1 = new FlowInterruptedException(Result.ABORTED, true);
        branch1.addSuppressed(shared);
        FlowInterruptedException branch2 = new FlowInterruptedException(Result.ABORTED, true);
        branch2.addSuppressed(shared);
        FlowInterruptedException top = new FlowInterruptedException(Result.ABORTED, true);
        top.addSuppressed(branch1);
        top.addSuppressed(branch2);
        Run run = Mockito.mock(Run.class);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        TaskListener listener = mockListener(out);

        // when
        top.handle(run, listener);

        // then
        assertThat(countOccurrences(out.toString(StandardCharsets.UTF_8), "shared-marker"), equalTo(1));
    }

    private static TaskListener mockListener(ByteArrayOutputStream out) {
        TaskListener listener = Mockito.mock(TaskListener.class);
        Mockito.when(listener.getLogger()).thenReturn(new PrintStream(out, true, StandardCharsets.UTF_8));
        return listener;
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int index = 0;
        while ((index = haystack.indexOf(needle, index)) != -1) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
