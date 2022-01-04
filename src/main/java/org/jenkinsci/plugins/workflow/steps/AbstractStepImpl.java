package org.jenkinsci.plugins.workflow.steps;

import com.google.inject.Injector;
import jenkins.model.Jenkins;

import edu.umd.cs.findbugs.annotations.Nullable;
import javax.inject.Inject;

/**
 * Partial convenient step implementation.
 * Used with {@link AbstractStepDescriptorImpl} and {@link AbstractStepExecutionImpl}.
 * @author Kohsuke Kawaguchi
 */
public abstract class AbstractStepImpl extends Step {

    /**
     * @deprecated Directly extend {@link Step} and avoid Guice.
     * Or see {@link #AbstractStepImpl(boolean)} for an existing step.
     */
    @Deprecated
    protected AbstractStepImpl() {}

    /**
     * Constructor for compatibility.
     * Retain this constructor and override {@link #start} if your step historically extended {@link AbstractStepImpl},
     * and your {@link AbstractStepExecutionImpl} kept a non-{@code transient} reference to the {@link AbstractStepImpl},
     * for serial form compatibility.
     * For new steps, extend {@link Step} directly.
     * @param ignored ignored, just to differentiate this constructor from {@link #AbstractStepImpl()} as a marker that the supertype must be retained
     */
    protected AbstractStepImpl(boolean ignored) {}

    /** Constructs a step execution automatically according to {@link AbstractStepDescriptorImpl#getExecutionType}. */
    @Override public StepExecution start(StepContext context) throws Exception {
        AbstractStepDescriptorImpl d = (AbstractStepDescriptorImpl) getDescriptor();
        return prepareInjector(context, this).getInstance(d.getExecutionType());
    }

    /**
     * Creates an {@link Injector} that performs injection to {@link Inject} and {@link StepContextParameter}.
     */
    protected static Injector prepareInjector(final StepContext context, @Nullable final Step step) {
        Injector injector = Jenkins.get().getInjector();
        if (injector == null) {
            throw new IllegalStateException();
        }
        return injector.createChildInjector(new ContextParameterModule(step,context));
    }
}
