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

import java.io.IOException;
import org.junit.Test;
import static org.junit.Assert.*;

public class DynamicContextTest {

    @Test public void subclassing() throws Exception {
        class Super {}
        class Sub extends Super {}
        DynamicContext.DelegatedContext nullContext = new DynamicContext.DelegatedContext() {
            @Override public <T> T get(Class<T> key) throws IOException, InterruptedException {
                return null;
            }
        };
        class Dyn extends DynamicContext.Typed<Super> {
            @Override protected Class<Super> type() {
                return Super.class;
            }
            @Override protected Super get(DelegatedContext context) {
                return new Sub();
            }
        }
        DynamicContext ctx = new Dyn();
        assertNotNull("can look up via superclass", ctx.get(Super.class, nullContext));
        assertNotNull("can look up via subclass", ctx.get(Sub.class, nullContext));
    }

}
