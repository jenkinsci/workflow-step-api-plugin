package org.jenkinsci.plugins.workflow.steps;

import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.model.Run;

import java.io.File;
import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import jenkins.model.Jenkins;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.graph.StepNode;
import org.jenkinsci.plugins.workflow.graph.FlowGraphWalker;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.BuildWatcherExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertTrue;

@WithJenkins
class SynchronousNonBlockingStepExecutionTest {

    @SuppressWarnings("unused")
    @RegisterExtension
    private static final BuildWatcherExtension BUILD_WATCHER = new BuildWatcherExtension();

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void basicNonBlockingStep() throws Exception {
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("""
                node {
                echo 'First message'
                syncnonblocking 'wait'
                echo 'Second message'
                }""", true));
        WorkflowRun b = p.scheduleBuild2(0).getStartCondition().get();

        // Wait for syncnonblocking to be started
        System.out.println("Waiting to syncnonblocking to start...");
        SynchronousNonBlockingStep.waitForStart("wait", b);
        Thread.sleep(250); // give CPS thread some time to go back to sleep
        assertTrue(b.getExecutor().getAsynchronousExecution().blocksRestart());

        // At this point the execution is paused inside the synchronous non-blocking step
        // Check for FlowNode created
        FlowGraphWalker walker = new FlowGraphWalker(b.getExecution());
        boolean found = false;
        for (FlowNode n : walker) {
            if (n instanceof StepNode && ((StepNode) n).getDescriptor() instanceof SynchronousNonBlockingStep.DescriptorImpl) {
                found = true;
                break;
            }
        }

        System.out.println("Checking flow node added...");
        assertTrue(found, "FlowNode has to be added just when the step starts running");

        // Check for message the test message sent to context listener
        System.out.println("Checking build log message present...");
        j.waitForMessage("Test Sync Message", b);
        // The last step did not run yet
        j.assertLogContains("First message", b);
        j.assertLogNotContains("Second message", b);

        // Let syncnonblocking to continue
        SynchronousNonBlockingStep.notify("wait");

        System.out.println("Waiting until syncnonblocking (and the full flow) finishes");
        j.waitForCompletion(b);
        System.out.println("Build finished. Continue.");
        // Check for the last message
        j.assertLogContains("Second message", b);
        j.assertBuildStatusSuccess(b);
    }

    @Test
    void interruptedTest() throws Exception {
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("""
                node {
                echo 'First message'
                try { syncnonblocking 'wait' } catch(InterruptedException e) { echo 'Interrupted!' }
                echo 'Second message'
                }""", true));
        WorkflowRun b = p.scheduleBuild2(0).getStartCondition().get();

        // Wait for syncnonblocking to be started
        System.out.println("Waiting to syncnonblocking to start...");
        SynchronousNonBlockingStep.waitForStart("wait", b);

        // At this point syncnonblocking is waiting for an interruption

        // Let's force a call to stop. This will try to send an interruption to the run Thread
        b.getExecutor().interrupt();
        System.out.println("Looking for interruption received log message");
        j.waitForMessage("Interrupted!", b);
        j.waitForCompletion(b);
        j.assertBuildStatus(Result.ABORTED, b);

        // Also check that timeouts produce the right status.
        p.setDefinition(new CpsFlowDefinition("timeout(time: 1, unit: 'SECONDS') {syncnonblocking 'wait2'}", true));
        j.assertLogContains(new TimeoutStepExecution.ExceededTimeout().getShortDescription(), j.assertBuildStatus(Result.ABORTED, p.scheduleBuild2(0)));
    }

    @Test
    void parallelTest() throws Exception {
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("""
                node {
                echo 'First message'
                parallel( a: { syncnonblocking 'wait0'; echo 'a branch'; }, b: { semaphore 'wait1'; echo 'b branch'; } )
                echo 'Second message'
                }""", true));
        WorkflowRun b = p.scheduleBuild2(0).getStartCondition().get();

        SynchronousNonBlockingStep.waitForStart("wait0", b);
        SemaphoreStep.success("wait1/1", null);

        // Wait for "b" branch to print its message
        j.waitForMessage("b branch", b);
        System.out.println("b branch finishes");

        // Check that "a" branch is effectively blocked
        j.assertLogNotContains("a branch", b);

        // Notify "a" branch
        System.out.println("Continue on wait0");
        SynchronousNonBlockingStep.notify("wait0");

        // Wait for "a" branch to finish
        j.waitForMessage("a branch", b);
        j.waitForCompletion(b);
    }

    public static final class SynchronousNonBlockingStep extends Step implements Serializable {

        public static final class State {
            private static final Map<File,State> states = new HashMap<>();
            static synchronized State get() {
                File home = Jenkins.get().getRootDir();
                State state = states.get(home);
                if (state == null) {
                    state = new State();
                    states.put(home, state);
                }
                return state;
            }
            private State() {}
            final Set<String> started = new HashSet<>();
        }

        private final String id;

        @DataBoundConstructor
        public SynchronousNonBlockingStep(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        @Override
        public StepExecution start(StepContext context) {
            return new StepExecutionImpl(this, context);
        }

        public static void waitForStart(String id, Run<?,?> b) throws InterruptedException {
            State s = State.get();
            synchronized (s) {
                while (!s.started.contains(id)) {
                    if (b != null && !b.isBuilding()) {
                        throw new AssertionError();
                    }
                    s.wait(1000);
                }
            }
        }

        public static void notify(String id) {
            State s = State.get();
            synchronized (s) {
                if (s.started.remove(id)) {
                    s.notifyAll();
                }
            }
        }

        private static class StepExecutionImpl extends SynchronousNonBlockingStepExecution<Void> {
            @Serial
            private static final long serialVersionUID = 1L;
            private final transient SynchronousNonBlockingStep step;

            StepExecutionImpl(SynchronousNonBlockingStep step, StepContext context) {
                super(context);
                this.step = step;
            }

            @Override
            protected Void run() throws Exception {
                System.out.println("Starting syncnonblocking " + step.getId());
                // Send a test message to the listener
                getContext().get(TaskListener.class).getLogger().println("Test Sync Message");

                State s = State.get();
                synchronized (s) {
                    s.started.add(step.getId());
                    s.notifyAll();
                }

                // Wait until somone (main test thread) notify us
                System.out.println("Sleeping inside the syncnonblocking thread (" + step.getId() + ")");
                synchronized (s) {
                    while (s.started.contains(step.getId())) {
                        s.wait(1000);
                    }
                }
                System.out.println("Continue syncnonblocking " + step.getId());

                return null;
            }
        }

        @TestExtension
        public static final class DescriptorImpl extends StepDescriptor {

            @Override
            public String getFunctionName() {
                return "syncnonblocking";
            }

            @Override
            public String getDisplayName() {
                return "Sync non-blocking Test step";
            }

            @Override
            public Set<? extends Class<?>> getRequiredContext() {
                return Collections.singleton(TaskListener.class);
            }
        }
    }

    @Test
    void errors() throws Exception {
        WorkflowJob p = j.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("erroneous()", true));
        j.assertLogContains("ought to fail", j.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0)));
    }

    @SuppressWarnings("unused")
    public static final class Erroneous extends Step {

        @DataBoundConstructor
        public Erroneous() {}

        @Override
        public StepExecution start(StepContext context) {
            return new Exec(context);
        }

        private static final class Exec extends SynchronousNonBlockingStepExecution<Void> {

            Exec(StepContext context) {
                super(context);
            }

            @Override
            protected Void run() {
                throw new AssertionError("ought to fail");
            }
        }

        @TestExtension("errors")
        public static final class DescriptorImpl extends StepDescriptor {

            @Override
            public Set<? extends Class<?>> getRequiredContext() {
                return Collections.emptySet();
            }

            @Override
            public String getFunctionName() {
                return "erroneous";
            }
        }
    }

    @Issue("JENKINS-53305")
    @Test
    void contextClassLoader() throws Exception {
        WorkflowJob p = j.createProject(WorkflowJob.class, "p");
        // Sets the class loader used to invoke steps to null to demonstrate a potential problem.
        // I am not sure how this could occur in practice, so this is a very artificial reproduction.
        p.setDefinition(new CpsFlowDefinition("""
                org.jenkinsci.plugins.workflow.cps.CpsVmExecutorService.ORIGINAL_CONTEXT_CLASS_LOADER.set(null)
                checkClassLoader()
                """, false));
        j.assertBuildStatus(Result.SUCCESS, p.scheduleBuild2(0));
    }

    @SuppressWarnings("unused")
    public static final class CheckClassLoader extends Step {

        @DataBoundConstructor
        public CheckClassLoader() {}

        @Override
        public StepExecution start(StepContext context) {
            return new Exec(context);
        }

        private static final class Exec extends SynchronousNonBlockingStepExecution<Void> {

            Exec(StepContext context) {
                super(context);
            }

            @Override
            protected Void run() {
                if (Thread.currentThread().getContextClassLoader() == null) {
                    throw new AssertionError("Context class loader should not be null!");
                }
                return null;
            }
        }

        @TestExtension("contextClassLoader")
        public static final class DescriptorImpl extends StepDescriptor {

            @Override
            public Set<? extends Class<?>> getRequiredContext() {
                return Collections.emptySet();
            }

            @Override
            public String getFunctionName() {
                return "checkClassLoader";
            }
        }
    }
}
