package org.jenkinsci.plugins.workflow.steps;

import hudson.model.Result;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.Collections;
import java.util.Set;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

public class BodyExecutionCallbackITest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void unhandledAssertionsShouldNotCreateZombieExecutions() throws Exception {
        j.jenkins.setNumExecutors(1);
        WorkflowJob job = j.createProject(WorkflowJob.class);
        job.setDefinition(new CpsFlowDefinition("node('master') { withStartFailure { echo 'oh dear' } }"));
        j.buildAndAssertStatus(Result.FAILURE, job);
        assertThat(j.jenkins.getComputers()[0].getExecutors().get(0).getCurrentWorkUnit(), nullValue());
    }

    public static class WithStartFailureStep extends Step {

        @DataBoundConstructor
        public WithStartFailureStep() {}

        @TestExtension("unhandledAssertionsShouldNotCreateZombieExecutions")
        public static class DescriptorImpl extends StepDescriptor {
            @Override
            public String getFunctionName() {
                return "withStartFailure";
            }

            @Override
            public Set<? extends Class<?>> getRequiredContext() {
                return Collections.emptySet();
            }

            @Override
            public boolean takesImplicitBlockArgument() {
                return true;
            }
        }

        @Override
        public StepExecution start(StepContext context) throws Exception {
            return new WithStartFailureStepExecution(context);
        }

        static class WithStartFailureStepExecution extends AbstractStepExecutionImpl {

            WithStartFailureStepExecution(final StepContext context) {
                super(context);
            }

            @Override
            public boolean start() throws Exception {
                getContext().newBodyInvoker().withCallback(new WithStartFailureStepCallback()).start();
                return false;
            }

            static class WithStartFailureStepCallback extends BodyExecutionCallback {
                @Override
                public void onStart(StepContext context) {
                    throw new RuntimeException("onStart broken");
                }

                @Override
                public void onSuccess(StepContext context, Object result) {
                    context.onSuccess(result);
                }

                @Override
                public void onFailure(StepContext context, Throwable t) {
                    context.onFailure(t);
                }
            }
        }
    }

}
