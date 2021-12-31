/*
 * The MIT License
 *
 * Copyright 2015 Jesse Glick.
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

import hudson.EnvVars;
import hudson.ExtensionList;
import hudson.model.Computer;
import hudson.model.Run;
import hudson.model.TaskListener;

import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Interface destined for {@link StepContext#get} instead of raw {@link EnvVars}.
 * Pass into {@link BodyInvoker#withContext}.
 */
public abstract class EnvironmentExpander implements Serializable {

    /**
     * May add environment variables to a context.
     * @param env an original set of environment variables
     */
    public abstract void expand(@NonNull EnvVars env) throws IOException, InterruptedException;

    /**
     * Get the names of environment variables known to contain sensitive values, such as secrets, in the current context.
     * Should be overridden by subclasses that bind secret values to environment variables.
     *
     * @return a set of environment variables known to contain sensitive values
     */
    public @NonNull Set<String> getSensitiveVariables() {
        return Collections.emptySet();
    }

    /**
     * Provides an expander for a constant map of string keys and string values. Supports {@link EnvVars#override(String, String)}
     * behavior, such as {@code PATH+XYZ} overrides.
     *
     * @param env A non-null map of string keys and string values.
     * @return An expander which will provide the given map.
     */
    public static EnvironmentExpander constant(@NonNull Map<String,String> env) {
        return new ConstantEnvironmentExpander(env);
    }

    private static class ConstantEnvironmentExpander extends EnvironmentExpander {
        private static final long serialVersionUID = 1;
        private final Map<String,String> envMap;

        ConstantEnvironmentExpander(@NonNull Map<String,String> envMap) {
            this.envMap = new HashMap<>();
            this.envMap.putAll(envMap);
        }

        @Override public void expand(EnvVars env) throws IOException, InterruptedException {
            env.overrideAll(envMap);
        }
    }

    /**
     * Merge together two expanders.
     * @param original an original one, such as one already found in a context
     * @param subsequent what you are adding
     * @return an expander which runs them both in that sequence (or, as a convenience, just {@code subsequent} in case {@code original} is null)
     */
    public static EnvironmentExpander merge(@CheckForNull EnvironmentExpander original, @NonNull EnvironmentExpander subsequent) {
        if (original == null) {
            return subsequent;
        }
        return new MergedEnvironmentExpander(original, subsequent);
    }
    private static class MergedEnvironmentExpander extends EnvironmentExpander {
        private static final long serialVersionUID = 1;
        private final @NonNull EnvironmentExpander original, subsequent;
        MergedEnvironmentExpander(EnvironmentExpander original, EnvironmentExpander subsequent) {
            this.original = original;
            this.subsequent = subsequent;
        }

        @Override public void expand(EnvVars env) throws IOException, InterruptedException {
            original.expand(env);
            subsequent.expand(env);
        }

        @Override
        public Set<String> getSensitiveVariables() {
            Set<String> originalSensitive = original.getSensitiveVariables();
            Set<String> subsequentSensitive = subsequent.getSensitiveVariables();
            if (originalSensitive.isEmpty() && subsequentSensitive.isEmpty()) {
                return Collections.emptySet();
            } else {
                Set<String> mergedWatch = new HashSet<>();
                mergedWatch.addAll(originalSensitive);
                mergedWatch.addAll(subsequentSensitive);
                return mergedWatch;
            }
        }
    }

    /**
     * @deprecated Use {@link #getEffectiveEnvironment(EnvVars, EnvVars, EnvironmentExpander, StepContext, TaskListener)} to allow {@link StepEnvironmentContributor}s to run.
     */
    @Deprecated
    public static @NonNull EnvVars getEffectiveEnvironment(@NonNull EnvVars customEnvironment, @CheckForNull EnvVars contextualEnvironment, @CheckForNull EnvironmentExpander expander) throws IOException, InterruptedException {
        return getEffectiveEnvironment(customEnvironment, contextualEnvironment, expander, null, TaskListener.NULL);
    }

    /**
     * Computes an effective environment in a given context.
     * Used from {@code DefaultStepContext} and {@code EnvActionImpl}.
     * The precedence order is:
     * <ol>
     * <li>{@link StepEnvironmentContributor}s (if any)
     * <li>{@code expander} (if any)
     * <li>{@code customEnvironment}
     * <li>{@code contextualEnvironment} (if any)
     * </ol>
     * @param customEnvironment {@link Run#getEnvironment(TaskListener)}, or {@code EnvironmentAction#getEnvironment}
     * @param contextualEnvironment a possible override as per {@link BodyInvoker#withContext} (such as from {@link Computer#getEnvironment} called from {@code PlaceholderExecutable})
     * @param expander a possible expander
     * @param stepContext the context of the step being executed
     * @param listener Connected to the build console. Can be used to report errors.
     * @return the effective environment
     */
    public static @NonNull EnvVars getEffectiveEnvironment(@NonNull EnvVars customEnvironment, @CheckForNull EnvVars contextualEnvironment, @CheckForNull EnvironmentExpander expander, @CheckForNull StepContext stepContext, @NonNull TaskListener listener) throws IOException, InterruptedException {
        EnvVars env;
        if (contextualEnvironment != null) {
            env = new EnvVars(contextualEnvironment);
            env.putAll(customEnvironment);
        } else {
            env = new EnvVars(customEnvironment);
        }
        if (expander != null) {
            expander.expand(env);
        }
        if (stepContext != null) {
            // apply them in a reverse order so that higher ordinal ones can modify values added by lower ordinal ones
            for (StepEnvironmentContributor contributor: ExtensionList.lookup(StepEnvironmentContributor.class).reverseView()) {
                contributor.buildEnvironmentFor(stepContext, env, listener);
            }
        }
        return env;
    }
}
