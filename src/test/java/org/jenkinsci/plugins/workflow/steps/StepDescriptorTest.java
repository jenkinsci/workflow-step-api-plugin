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
import org.junit.Test;

import java.util.HashMap;
import java.util.concurrent.TimeUnit;

/**
 * Tests some specific {@link StepDescriptor} APIs
 */
public class StepDescriptorTest {

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
}
