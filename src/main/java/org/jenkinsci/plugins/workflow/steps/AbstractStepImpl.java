package org.jenkinsci.plugins.workflow.steps;

import com.google.inject.Injector;
import jenkins.model.Jenkins;

import edu.umd.cs.findbugs.annotations.Nullable;
import javax.inject.Inject;

/**
 * Partial convenient step implementation.
 * Used with {@link AbstractStepDescriptorImpl} and {@link AbstractStepExecutionImpl}.
 * @author Kohsuke Kawaguchi
 * @deprecated Directly extend {@link Step} and avoid Guice.
 */
@Deprecated
public abstract class AbstractStepImpl extends Step {

    /** Constructs a step execution automatically according to {@link AbstractStepDescriptorImpl#getExecutionType}. */
    @Override public final StepExecution start(StepContext context) throws Exception {
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
