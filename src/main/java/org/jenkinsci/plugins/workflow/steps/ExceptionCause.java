package org.jenkinsci.plugins.workflow.steps;

import jenkins.model.CauseOfInterruption;
import org.kohsuke.stapler.export.Exported;

import java.io.Serializable;

/**
 * {@link CauseOfInterruption} that captures random {@link Throwable},
 * which is used when the cancellation is in response to some failures.
 *
 * TODO: move this to core
 * TODO: better summary.jelly
 * @author Kohsuke Kawaguchi
 */
class ExceptionCause extends CauseOfInterruption implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Throwable t;

    public ExceptionCause(Throwable t) {
        this.t = t;
    }

    @Override
    public String getShortDescription() {
        return "Exception: "+t.getMessage();
    }

    @Exported
    public Throwable getException() {
        return t;
    }
}
