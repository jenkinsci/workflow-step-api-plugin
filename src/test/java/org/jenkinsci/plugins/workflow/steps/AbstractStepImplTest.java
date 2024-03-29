package org.jenkinsci.plugins.workflow.steps;

import hudson.Extension;
import hudson.model.Node;
import jenkins.model.Jenkins;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import jakarta.inject.Inject;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.*;

/**
 * @author Kohsuke Kawaguchi
 */
@SuppressWarnings("deprecation") // it is all deprecated
public class AbstractStepImplTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Inject
    BogusStep.DescriptorImpl d;

    @Before
    public void setUp() {
        j.getInstance().getInjector().injectMembers(this);
    }

    @Test
    public void inject() throws Exception {

        Map<String, Object> r = new HashMap<>();
        r.put("a",3);
        r.put("b","bbb");
        r.put("c",null);
        r.put("d","ddd");
        BogusStep step = (BogusStep) d.newInstance(r);

        assertEquals(step.a,3);
        assertEquals(step.b,"bbb");
        assertNull(step.c);
        assertEquals(step.d,"ddd");

        StepContext c = mock(StepContext.class);
        when(c.get(Node.class)).thenReturn(j.getInstance());

        BogusStepExecution b = (BogusStepExecution)step.start(c);
        b.start();
    }

    public static class BogusStep extends AbstractStepImpl {
        int a;
        String b;
        @DataBoundSetter String c;
        Object d;

        @DataBoundConstructor
        public BogusStep(int a, String b) {
            this.a = a;
            this.b = b;
        }

        @DataBoundSetter
        void setD(Object value) {
            this.d = value;
        }

        @Extension
        public static class DescriptorImpl extends AbstractStepDescriptorImpl {

            public DescriptorImpl() {
                super(BogusStepExecution.class);
            }

            @Override
            public String getFunctionName() {
                return "fff";
            }

            @Override
            public String getDisplayName() {
                return "ggg";
            }
        }
    }

    public static class BogusStepExecution extends AbstractSynchronousStepExecution<Void> {
        @Inject
        Jenkins jenkins;

        @StepContextParameter
        Node n;

        @Override
        protected Void run() {
            assertSame(jenkins, Jenkins.getInstance());
            assertSame(n, Jenkins.getInstance());
            return null;
        }
    }
}
