/*
 * The MIT License
 *
 * Copyright 2017 CloudBees, Inc.
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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Tests some specific {@link StepDescriptor} APIs
 */
public class StepDescriptorTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Inject
    private StepA.DescriptorImpl descriptorA;

    @Inject
    private StepB.DescriptorImpl descriptorB;

    @Before
    public void setUp() {
        j.getInstance().getInjector().injectMembers(this);
    }

    @Test
    public void testStringFormattingAllowed() {
        Assert.assertFalse(StepDescriptor.isAbleToUseToStringForDisplay(null));
        Assert.assertFalse(StepDescriptor.isAbleToUseToStringForDisplay(new HashMap<String, String>()));
        Assert.assertTrue(StepDescriptor.isAbleToUseToStringForDisplay("cheese"));
        Assert.assertTrue(StepDescriptor.isAbleToUseToStringForDisplay(-1));
        Assert.assertTrue(StepDescriptor.isAbleToUseToStringForDisplay(Boolean.FALSE));
        Assert.assertTrue(StepDescriptor.isAbleToUseToStringForDisplay(TimeUnit.MINUTES));
        Assert.assertTrue(StepDescriptor.isAbleToUseToStringForDisplay(new StringBuffer("gopher")));
    }

    @Test
    public void testGetExecutionType() {
        Assert.assertEquals(descriptorA.getExecutionType(), StepA.StepExecutionImpl.class);
        Assert.assertEquals(descriptorB.getExecutionType(), StepB.StepExecutionImpl.class);
    }

    @Test
    public void testGetResultType() {
        Assert.assertEquals(descriptorA.getResultType(), String.class);
        Type resultTypeB = descriptorB.getResultType();
        Assert.assertTrue(resultTypeB instanceof ParameterizedType);
        Assert.assertEquals(((ParameterizedType)resultTypeB).getRawType(), List.class);
        Assert.assertArrayEquals(((ParameterizedType)resultTypeB).getActualTypeArguments(), new Type[] { Integer.class });
    }

    public static final class StepA extends Step {
        @DataBoundConstructor
        public StepA() {
        }

        @Override
        public StepExecutionImpl start(StepContext context) throws Exception {
            return new StepExecutionImpl(context);
        }

        private static final class StepExecutionImpl extends StepExecution {
            StepExecutionImpl(@Nonnull StepContext context) {
                super(context);
            }

            @Override
            public boolean start() throws Exception {
                getContext().onSuccess("hello");
                return true;
            }
        }

        @TestExtension
        public static final class DescriptorImpl extends StepDescriptor {
            @Override
            public Set<? extends Class<?>> getRequiredContext() {
                return null;
            }

            @Override
            public String getFunctionName() {
                return "stepA";
            }

            @Override @Nonnull
            public Type getResultType() {
                return String.class;
            }
        }
    }

    public static final class StepB extends AbstractStepImpl {
        @DataBoundConstructor
        public StepB() {
        }

        private static class StepExecutionImpl extends AbstractSynchronousStepExecution<List<Integer>> {
            @Override
            protected List<Integer> run() throws Exception {
                return Arrays.asList(1, 2, 3);
            }
        }

        @TestExtension
        public static final class DescriptorImpl extends AbstractStepDescriptorImpl {
            public DescriptorImpl() {
                super(StepExecutionImpl.class);
            }

            protected DescriptorImpl(Class<? extends StepExecution> executionType) {
                super(executionType);
            }

            @Override
            public String getFunctionName() {
                return "stepB";
            }
        }
    }
}
