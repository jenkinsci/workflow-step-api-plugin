/*
 * The MIT License
 *
 * Copyright (c) 2013-2014, CloudBees, Inc.
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

import hudson.ExtensionList;
import hudson.model.Descriptor;
import org.jenkinsci.plugins.structs.describable.DescribableModel;
import org.jenkinsci.plugins.structs.describable.DescribableParameter;

import javax.annotation.CheckForNull;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * {@link Descriptor} that provides information about {@link Step}.
 *
 * @author Kohsuke Kawaguchi
 * @author Jesse Glick
 */
public abstract class StepDescriptor extends Descriptor<Step> {
    /**
     * Returns the context {@link Step} needs to access.
     *
     * This allows the system to statically infer which steps are applicable in which context
     * (say in freestyle or in workflow).
     * @see StepContext#get(Class)
     */
    public abstract Set<Class<?>> getRequiredContext();

    /**
     * Returns the context {@link Step} adds/sets/modifies when executing a body.
     *
     * <p>
     * This is used to diagnose a "missing context" problem by suggesting what wrapper steps were likely missing.
     * Steps that {@linkplain #takesImplicitBlockArgument() does not take a body block} must return the empty set
     * as it has nothing to contribute to the context.
     *
     * <p>
     * This set and {@link #getRequiredContext()} can be compared to determine context variables that are newly
     * added (as opposed to get merely decorated.)
     *
     * @see MissingContextVariableException
     */
    public Set<Class<?>> getProvidedContext() {
        return Collections.emptySet();
    }


    /**
     * Return a short string that is a valid identifier for programming languages
     * ([A-Za-z_] in the first char and [A-Za-z0-9_]" for all the other chars.
     *
     * Step will be referenced by this name when used in a programming language.
     */
    public abstract String getFunctionName();

    /**
     * Return true if this step can accept an implicit block argument.
     * (If it can, but it is called without a block, {@link StepContext#hasBody} will be false.)
     * @see StepContext#newBodyInvoker()
     */
    public boolean takesImplicitBlockArgument() {
        return false;
    }

    /**
     * For UI presentation purposes, allows a plugin to mark a step as deprecated or advanced.
     * @return true to exclude from main list of steps
     */
    public boolean isAdvanced() {
        return false;
    }

    /**
     * Used when a {@link Step} is instantiated programmatically.
     * The default implementation just uses {@link DescribableModel#instantiate}.
     * @param arguments
     *      Named arguments and values, à la Ant task or Maven mojos.
     *      Generally should follow the semantics of {@link DescribableModel#instantiate}.
     * @return an instance of {@link #clazz}
     */
    public Step newInstance(Map<String,Object> arguments) throws Exception {
        return new DescribableModel<>(clazz).instantiate(arguments);
    }

    /**
     * Determine which arguments went into the configuration of a step configured through a form submission.
     * @param step a fully-configured step (assignable to {@link #clazz})
     * @return arguments that could be passed to {@link #newInstance} to create a similar step instance
     * @throws UnsupportedOperationException if this descriptor lacks the ability to do such a calculation
     */
    public Map<String,Object> defineArguments(Step step) throws UnsupportedOperationException {
        return DescribableModel.uninstantiate_(step);
    }

    /**
     * Some {@link Step} representation in text (such as Jenkins Pipeline) benefits from
     * the short hand syntax that omits the explicit argument name when there's only one argument.
     * This method provides that mechanism in which {@link StepDescriptor} can normalize
     * such single value into a map that can then be fed into {@link #newInstance(Map)}.
     *
     * @return
     *      map that assigns the name to the sole argument. Otherwise
     *      null if this step does not support the single argument form.
     */
    public @CheckForNull Map<String,Object> singleArgument(Object arg) {
        DescribableParameter p = new DescribableModel<>(clazz).getSoleRequiredParameter();
        if (p!=null) {
            return Collections.singletonMap(p.getName(), arg);
        } else {
            return null;
        }
    }


    /**
     * Makes sure that the given {@link StepContext} has all the context parameters this descriptor wants to see,
     * and if not, throw {@link MissingContextVariableException} indicating which variable is missing.
     */
    public final void checkContextAvailability(StepContext c) throws MissingContextVariableException, IOException, InterruptedException {
        // TODO the order here is nondeterministic; should we pick the lexicographic first? Or extend MissingContextVariableException to take a Set<Class<?>> types?
        for (Class<?> type : getRequiredContext()) {
            Object v = c.get(type);
            if (v==null)
                throw new MissingContextVariableException(type);
        }
    }

    public static ExtensionList<StepDescriptor> all() {
        return ExtensionList.lookup(StepDescriptor.class);
    }
}
