/*
 * The MIT License
 *
 * Copyright 2014 Jesse Glick.
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

import hudson.AbortException;
import hudson.Functions;
import hudson.model.Executor;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import jenkins.model.CauseOfInterruption;
import jenkins.model.InterruptedBuildAction;

/**
 * Special exception that can be thrown out of {@link StepContext#onFailure} to indicate that the flow was aborted from the inside.
 * (This could be caught like any other exception and rethrown or ignored. It only takes effect if thrown all the way up.
 * Consumers, such as steps, may find {@link #isActualInterruption} useful in deciding whether to ignore or rethrow the exception.)
 * <p>No stack trace is printed (except by {@link #getCause} and/or {@link #getSuppressed} if present),
 * and you can control the {@link Result} and {@link CauseOfInterruption}.
 * <p>Analogous to {@link Executor#interrupt(Result, CauseOfInterruption...)} but does not assume we are running inside an executor thread.
 * <p>There is no need to call this from {@link StepExecution#stop} since in that case the execution owner
 * should have set a {@link jenkins.model.CauseOfInterruption.UserInterruption} and {@link Result#ABORTED}.
 */
public final class FlowInterruptedException extends InterruptedException {

    private static final long serialVersionUID = 630482382622970136L;

    private final @NonNull Result result;
    private final @NonNull List<CauseOfInterruption> causes;
    /**
     * If true, this exception represents an actual build interruption, rather than a general error with a result and
     * no stack trace.
     * Used by steps like {@code RetryStep} to decide whether to handle or rethrow a {@link FlowInterruptedException}.
     * Non-null, except momentarily during deserialization before {@link #readResolve} sets the field to {@code true}
     * for old instances serialized before this field was added.
     */
    private Boolean actualInterruption = true;

    /**
     * Creates a new exception.
     * @param result the desired result for the flow, typically {@link Result#ABORTED}
     * @param causes any indications
     */
    public FlowInterruptedException(@NonNull Result result, @NonNull CauseOfInterruption... causes) {
        this.result = result;
        this.causes = Arrays.asList(causes);
        this.actualInterruption = true;
    }

    /**
     * Creates a new exception.
     * @param result the desired result for the flow, typically {@link Result#ABORTED}
     * @param causes any indications
     * @param actualInterruption true if this is an actual build interruption (e.g. the user wants to abort the build)
     */
    public FlowInterruptedException(@NonNull Result result, boolean actualInterruption, @NonNull CauseOfInterruption... causes) {
        this.result = result;
        this.causes = Arrays.asList(causes);
        this.actualInterruption = actualInterruption;
    }

    public @NonNull Result getResult() {
        return result;
    }

    public @NonNull List<CauseOfInterruption> getCauses() {
        return causes;
    }

    public boolean isActualInterruption() {
        return actualInterruption;
    }

    public void setActualInterruption(boolean actualInterruption) {
        this.actualInterruption = actualInterruption;
    }

    private Object readResolve() {
        if (actualInterruption == null) {
            actualInterruption = true;
        }
        return this;
    }

    /**
     * If a build catches this exception, it should use this method to report it.
     */
    public void handle(Run<?,?> run, TaskListener listener) {
        Set<CauseOfInterruption> boundCauses = new HashSet<>();
        for (InterruptedBuildAction a : run.getActions(InterruptedBuildAction.class)) {
            boundCauses.addAll(a.getCauses());
        }
        Set<CauseOfInterruption> diff = new LinkedHashSet<>(causes);
        diff.removeAll(boundCauses);
        if (!diff.isEmpty()) {
            run.addAction(new InterruptedBuildAction(diff));
            for (CauseOfInterruption cause : diff) {
                cause.print(listener);
            }
        }
        print(getCause(), run, listener);
        for (Throwable t : getSuppressed()) {
            print(t, run, listener);
        }
    }
    private static void print(@CheckForNull Throwable t, Run<?,?> run, @NonNull TaskListener listener) {
        if (t instanceof AbortException) {
            listener.getLogger().println(t.getMessage());
        } else if (t instanceof FlowInterruptedException) {
            ((FlowInterruptedException) t).handle(run, listener);
        } else if (t != null) {
            Functions.printStackTrace(t, listener.getLogger());
        }
    }

}
