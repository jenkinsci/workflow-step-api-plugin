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

import hudson.model.Result;
import hudson.model.TaskListener;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;
import static org.hamcrest.Matchers.*;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.cps.CpsStepContext;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.ClassRule;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Rule;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;

public class GeneralNonBlockingStepExecutionTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();

    @Rule public JenkinsRule r = new JenkinsRule();

    @Rule public LoggerRule logging = new LoggerRule();

    @Test public void getStatus() throws Exception {
        WorkflowJob p = r.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("slowBlock {semaphore 'wait'}", true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        startEnter.acquire();
        assertThat(((CpsFlowExecution) b.getExecution()).getThreadDump().toString(), containsString("at DSL.slowBlock(running in thread: org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution [#"));
        Thread.sleep(250); // give CPS thread some time to go back to sleep
        assertTrue(b.getExecutor().getAsynchronousExecution().blocksRestart());
        startExit.release();
        SemaphoreStep.waitForStart("wait/1", b);
        assertThat(((CpsFlowExecution) b.getExecution()).getThreadDump().toString(), containsString("at DSL.slowBlock(not currently scheduled, or running blocks)"));
        while (b.getExecutor().getAsynchronousExecution().blocksRestart()) {
            Thread.sleep(100); // as above
        }
        SemaphoreStep.success("wait/1", null);
        endEnter.acquire();
        assertThat(((CpsFlowExecution) b.getExecution()).getThreadDump().toString(), containsString("at DSL.slowBlock(running in thread: org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution [#"));
        Thread.sleep(250); // as above
        assertTrue(b.getExecutor().getAsynchronousExecution().blocksRestart());
        endExit.release();
        r.assertBuildStatusSuccess(r.waitForCompletion(b));
    }

    @Test public void stop() throws Exception {
        WorkflowJob p = r.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("slowBlock {semaphore 'wait'}", true));
        logging.record(CpsStepContext.class, Level.WARNING).capture(100);
        {
            WorkflowRun b = p.scheduleBuild2(0).waitForStart();
            startEnter.acquire();
            b.getExecutor().interrupt();
            r.assertBuildStatus(Result.ABORTED, r.waitForCompletion(b));
            startExit.release();
        }
        {
            WorkflowRun b = p.scheduleBuild2(0).waitForStart();
            startEnter.acquire();
            startExit.release();
            SemaphoreStep.waitForStart("wait/1", b);
            b.getExecutor().interrupt();
            endEnter.acquire();
            endExit.release();
            r.assertBuildStatus(Result.ABORTED, r.waitForCompletion(b));
            SemaphoreStep.success("wait/1", null);
        }
        {
            WorkflowRun b = p.scheduleBuild2(0).waitForStart();
            startEnter.acquire();
            startExit.release();
            SemaphoreStep.waitForStart("wait/2", b);
            SemaphoreStep.success("wait/2", null);
            endEnter.acquire();
            b.getExecutor().interrupt();
            r.assertBuildStatus(Result.ABORTED, r.waitForCompletion(b));
            endExit.release();
        }
        assertThat(logging.getRecords(), empty());
    }

    @Issue("JENKINS-58878")
    @Test public void shouldNotHang() throws Exception {
        int iterations = 50;
        startExit.release(iterations); // Prevents the semaphores from blocking inside of the slowBlock step.
        endExit.release(iterations);
        WorkflowJob p = r.createProject(WorkflowJob.class);
        p.setDefinition(new CpsFlowDefinition(
                "for (int i = 0; i < " + iterations + "; i++) {\n" +
                "  slowBlock {\n" +
                "    echo(/At $i/)\n" +
                "  }\n" +
                "}", true));
        r.buildAndAssertSuccess(p);
    }

    private static Semaphore startEnter, startExit, endEnter, endExit;

    @Before public void semaphores() {
        startEnter = new Semaphore(0);
        startExit = new Semaphore(0);
        endEnter = new Semaphore(0);
        endExit = new Semaphore(0);
    }

    public static final class SlowBlockStep extends Step {
        @DataBoundConstructor public SlowBlockStep() {}
        @Override public StepExecution start(StepContext context) throws Exception {
            return new Execution(context, this);
        }
        private static final class Execution extends GeneralNonBlockingStepExecution {
            private final transient SlowBlockStep step;
            Execution(StepContext context, SlowBlockStep step) {
                super(context);
                this.step = step;
            }
            private void println(String msg) throws Exception {
                getContext().get(TaskListener.class).getLogger().println(msg);
            }
            @Override public boolean start() throws Exception {
                println("starting step");
                run(this::doStart);
                return false;
            }
            private void doStart() throws Exception {
                println("starting background part of step");
                startEnter.release();
                startExit.acquire();
                println("starting body");
                getContext().newBodyInvoker().withCallback(new Callback()).start();
            }
            private final class Callback extends TailCall {
                @Override protected void finished(StepContext context) throws Exception {
                    println("body completed, starting background end part of step");
                    endEnter.release();
                    endExit.acquire();
                    println("ending step");
                }
            }
        }
        @TestExtension public static final class DescriptorImpl extends StepDescriptor {
            @Override public String getFunctionName() {
                return "slowBlock";
            }
            @Override public Set<? extends Class<?>> getRequiredContext() {
                return Collections.singleton(TaskListener.class);
            }
            @Override public boolean takesImplicitBlockArgument() {
                return true;
            }
        }
    }

}
