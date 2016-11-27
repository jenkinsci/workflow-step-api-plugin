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

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.ExtensionList;
import hudson.Util;
import hudson.model.Describable;
import hudson.model.Descriptor;
import org.jenkinsci.plugins.structs.describable.DescribableModel;
import org.jenkinsci.plugins.structs.describable.DescribableParameter;

import javax.annotation.CheckForNull;
import org.jenkinsci.plugins.structs.SymbolLookup;
import org.jenkinsci.plugins.structs.describable.DescribableModel;
import org.jenkinsci.plugins.structs.describable.DescribableParameter;
import org.jenkinsci.plugins.structs.describable.UninstantiatedDescribable;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

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
     * Return a short string that is a valid identifier for programming languages.
     * Follow the pattern {@code [a-z][A-Za-z0-9_]*}.
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
     * Some steps, such as {@code CoreStep} or {@code GenericSCMStep} can take
     * arbitrary {@link Describable}s of a certain type and execute it as a step.
     * Such a step should return true from this method so that {@link Describable}s that
     * it supports can be directly written as a step as a short-hand.
     *
     * <p>
     * Meta-step works as an invisible adapter that creates an illusion that {@link Describable}s are
     * steps.
     *
     * <p>
     * For example, in Jenkins Pipeline, if there is a meta step that can handle a {@link Describable},
     * and it has a symbol, it allows the following short-hand:
     *
     * <pre>
     * public class Xyz extends Foo {
     *     &#64;DataBoundConstructor
     *     public Xyz(String value) { ... }
     *
     *     &#64;Extension &#64;Symbol("xyz")
     *     public static class DescriptorImpl extends FooDescriptor { ... }
     * }
     *
     * public class MetaStepForFoo extends AbstractStepImpl {
     *     &#64;DataBoundConstructor
     *     public MetaStepForFoo(Foo delegate) {
     *         ...
     *     }
     *
     *     ...
     *     &#64;Extension
     *     public static class DescriptorImpl extends AbstractStepDescriptorImpl {
     *         &#64;Override
     *         public String getFunctionName() {
     *             return "metaStepForFoo";
     *         }
     *         &#64;Override
     *         public boolean isMetaStep() {
     *             return true;
     *         }
     *     }
     * }
     *
     * // this is the short-hand that users will use
     * xyz('hello')
     * // but this is how it actually gets executed
     * metaStepForFoo(xyz('hello'))
     * </pre>
     *
     * <p>
     * Meta-step must have a {@link DataBoundConstructor} whose first argument represents a
     * {@link Describable} that it handles.
     */
    public boolean isMetaStep() {
        return false;
    }

    /**
     * For a {@linkplain #isMetaStep() meta step}, return the type that this meta step handles.
     * Otherwise null.
     */
    public final @Nullable Class<?> getMetaStepArgumentType() {
        if (!isMetaStep())  return null;

        DescribableModel<?> m = new DescribableModel<>(clazz);
        DescribableParameter p = m.getFirstRequiredParameter();
        if (p==null) {
            LOGGER.log(Level.WARNING, "{0} claims to be a meta-step but it has no parameter in @DataBoundConstructor", getClass().getName());
            // don't punish users for a mistake by a plugin developer. return an error value instead of throwing an error
            // or return null which usually breaks the caller.
            // here, returning a type that doesn't match anything normally prevents this broken StepDescriptor from getting used.
            return Void.TYPE;
        }

        return p.getErasedType();
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
     * @deprecated
     *      Use {@link #uninstantiate(Step)}
     */
    @Deprecated
    public Map<String,Object> defineArguments(Step step) throws UnsupportedOperationException {
        if (Util.isOverridden(StepDescriptor.class, getClass(), "uninstantiate", Step.class)) {
            // if the subtype has defined the uninstantiate() method, delegate to that
            return uninstantiate(step).toMap();
        } else {
            // otherwise assume legacy usage
            return DescribableModel.uninstantiate_(step);
        }
    }

    /**
     * Determine which arguments went into the configuration of a step configured through a form submission.
     * @param step a fully-configured step (assignable to {@link #clazz})
     * @return arguments that could be passed to {@link #newInstance} to create a similar step instance
     * @throws UnsupportedOperationException if this descriptor lacks the ability to do such a calculation
     */
    public UninstantiatedDescribable uninstantiate(Step step) throws UnsupportedOperationException {
        if (Util.isOverridden(StepDescriptor.class, getClass(), "uninstantiate", Step.class)) {
            // Newer clients are called older implementations.
            // We could conceivably inspect the returned Map and try to recreate UninstantiatedDescribable recursively,
            // but there's a little gain to be had from that.
            //
            // A literal map is a valid part of the object graph in UninstantiatedDescribable, and here we'll just
            // produce that with a map entry named "$class", thus return value is already legal as per contract.
            //
            // Even if we try, we cannot reliably look at such a map and recreate a UninstantiatedDescribable
            // without knowing the expected type.
            //
            // Jesse and KK talked about this during this change and we felt it's OK for legacy step implementations
            // to behave poorly wrt snippetizer (as in, produce ugly example that uses $class)
            return new UninstantiatedDescribable(defineArguments(step));
        } else {
            // the default behaviour in the absence of any overrides
            return DescribableModel.uninstantiate2_(step);
        }
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

    /**
     * Convenience method to iterate all meta step descriptors.
     */
    public static Iterable<StepDescriptor> allMeta() {
        return Iterables.filter(all(), new Predicate<StepDescriptor>() {
            @SuppressFBWarnings(value="NP_PARAMETER_MUST_BE_NONNULL_BUT_MARKED_AS_NULLABLE", justification="all() will not return nulls")
            @Override
            public boolean apply(StepDescriptor i) {
                return i.isMetaStep();
            }
        });
    }

    /**
     * Obtains a {@link StepDescriptor} by its function name, or null if not found.
     */
    public static @Nullable StepDescriptor byFunctionName(String name) {
        for (StepDescriptor d : all()) {
            if (d.getFunctionName().equals(name))
                return d;
        }
        return null;
    }

    /**
     * Given a symbol, attempt to find all the meta-steps that can consume this symbol.
     *
     * When the returned list is bigger than size 1, it means there's ambiguity in how to process it.
     */
    public static @Nonnull List<StepDescriptor> metaStepsOf(String symbol) {
        List<StepDescriptor> r = new ArrayList<>();
        // honor ordinals among meta-steps
        for (StepDescriptor d : StepDescriptor.allMeta()) {
            Class<?> a = d.getMetaStepArgumentType();
            if (a==null)    continue;   // defensive check
            if (SymbolLookup.get().findDescriptor(a,symbol)!=null)
                r.add(d);
        }
        return r;
    }


    private static final Logger LOGGER = Logger.getLogger(StepDescriptor.class.getName());
}
