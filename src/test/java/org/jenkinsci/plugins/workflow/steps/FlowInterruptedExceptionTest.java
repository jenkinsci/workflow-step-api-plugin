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
}
