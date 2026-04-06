package org.jenkinsci.plugins.workflow.steps;

import hudson.Extension;
import hudson.model.Node;
import jenkins.model.Jenkins;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
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
@WithJenkins
class AbstractStepImplTest {
    
    private JenkinsRule j;

    @Inject
    private BogusStep.DescriptorImpl d;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
        j.getInstance().getInjector().injectMembers(this);
    }

    @Test
    void inject() throws Exception {
        Map<String, Object> r = new HashMap<>();
        r.put("a",3);
        r.put("b","bbb");
        r.put("c",null);
        r.put("d","ddd");
        BogusStep step = (BogusStep) d.newInstance(r);

        assertEquals(3, step.a);
        assertEquals("bbb", step.b);
        assertNull(step.c);
        assertEquals("ddd", step.d);

        StepContext c = mock(StepContext.class);
        when(c.get(Node.class)).thenReturn(j.getInstance());

        BogusStepExecution b = (BogusStepExecution)step.start(c);
        b.start();
    }

    public static class BogusStep extends AbstractStepImpl {

        private int a;
        private String b;
        @DataBoundSetter
        private String c;
        private Object d;

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
        private Jenkins jenkins;

        @StepContextParameter
        private Node n;

        @Override
        protected Void run() {
            assertSame(jenkins, Jenkins.getInstance());
            assertSame(n, Jenkins.getInstance());
            return null;
        }
    }
}
