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
import java.io.PrintStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

/**
 * Interface destined for {@link StepContext#get} instead of raw {@link EnvVars}.
 * Pass into {@link BodyInvoker#withContext}.
 */
public abstract class EnvironmentExpander implements Serializable {
    private Map<String, String> watchedVars;

    /**
     * May add environment variables to a context.
     * @param env an original set of environment variables
     */
    public abstract void expand(@Nonnull EnvVars env) throws IOException, InterruptedException;

    public void callback(PrintStream stream) {
    }
    public void watch(String var, String val) {
        if (watchedVars == null) {
            watchedVars = new HashMap<>();
        }
        watchedVars.put(var, val);
    }
    public void watchAll(Map<String, String> vars) {//Collection<?  extends String> vars) {
        if (watchedVars == null) {
            watchedVars = new HashMap<>();
        }
        watchedVars.putAll(vars);
    }
    @CheckForNull
    public List<String> findWatchedVars(String text) {
        if (watchedVars == null) {
            return null;
        }
        return watchedVars.entrySet().stream()
                .filter(e -> text.contains(e.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }
    @CheckForNull
    public Map<String, String> getWatchedVars() {
        return watchedVars;
    }

    /**
     * Provides an expander for a constant map of string keys and string values. Supports {@link EnvVars#override(String, String)}
     * behavior, such as {@code PATH+XYZ} overrides.
     *
     * @param env A non-null map of string keys and string values.
     * @return An expander which will provide the given map.
     */
    public static EnvironmentExpander constant(@Nonnull Map<String,String> env) {
        return new ConstantEnvironmentExpander(env);
    }

    private static class ConstantEnvironmentExpander extends EnvironmentExpander {
        private static final long serialVersionUID = 1;
        private final Map<String,String> envMap;

        ConstantEnvironmentExpander(@Nonnull Map<String,String> envMap) {
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
    public static EnvironmentExpander merge(@CheckForNull EnvironmentExpander original, @Nonnull EnvironmentExpander subsequent) {
        if (original == null) {
            return subsequent;
        }
        return new MergedEnvironmentExpander(original, subsequent);
    }
    private static class MergedEnvironmentExpander extends EnvironmentExpander {
        private static final long serialVersionUID = 1;
        private final @Nonnull EnvironmentExpander original, subsequent;
        MergedEnvironmentExpander(EnvironmentExpander original, EnvironmentExpander subsequent) {
            this.original = original;
            this.subsequent = subsequent;
//            List<String> originalWatch = original.getWatchList();
//            List<String> subsequentWatch = subsequent.getWatchList();
//            if (originalWatch != null) {
//                this.watchAll(originalWatch);
//            }
//            if (subsequentWatch != null) {
//                this.watchAll(subsequentWatch);
//            }
        }
        @Override public void expand(EnvVars env) throws IOException, InterruptedException {
            original.expand(env);
            subsequent.expand(env);
        }
    }

    /**
     * @deprecated Use {@link #getEffectiveEnvironment(EnvVars, EnvVars, EnvironmentExpander, StepContext, TaskListener)} to allow {@link StepEnvironmentContributor}s to run.
     */
    @Deprecated
    public static @Nonnull EnvVars getEffectiveEnvironment(@Nonnull EnvVars customEnvironment, @CheckForNull EnvVars contextualEnvironment, @CheckForNull EnvironmentExpander expander) throws IOException, InterruptedException {
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
    public static @Nonnull EnvVars getEffectiveEnvironment(@Nonnull EnvVars customEnvironment, @CheckForNull EnvVars contextualEnvironment, @CheckForNull EnvironmentExpander expander, @CheckForNull StepContext stepContext, @Nonnull TaskListener listener) throws IOException, InterruptedException {
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

//    public static void fireCallbacks(EnvironmentExpander expander) {
//        if (expander instanceof MergedEnvironmentExpander) {
//            EnvironmentExpander.fireCallbacks(((MergedEnvironmentExpander) expander).original);
//            EnvironmentExpander.fireCallbacks(((MergedEnvironmentExpander) expander).subsequent);
//        } else {
//            expander.callback();
//        }
//    }

}
