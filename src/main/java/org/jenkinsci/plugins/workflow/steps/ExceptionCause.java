package org.jenkinsci.plugins.workflow.steps;

import jenkins.model.CauseOfInterruption;

import java.io.Serializable;

@Deprecated
class ExceptionCause extends CauseOfInterruption implements Serializable {
    private final Throwable t;

    public ExceptionCause(Throwable t) {
        this.t = t;
    }

    @Override
    public String getShortDescription() {
        return "Exception: "+t.getMessage();
    }

    private static final long serialVersionUID = 1L;
}
