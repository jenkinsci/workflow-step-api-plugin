package org.jenkinsci.plugins.workflow.steps;

import com.google.inject.Inject;
import java.io.Serializable;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Partial implementation of {@link StepExecution} that injects {@link StepContextParameter} upon resume.
 * Declare any {@code transient} fields with {@link StepContextParameter} that you might need.
 * <p>The originating {@link Step} may also be {@link Inject}ed.
 * It must be marked {@link Inject#optional}.
 * Normally it is only used for the benefit of {@link #start}, so it should be {@code transient}.
 * <strong>Beware</strong> that injecting a step this way does not currently work if that step
 * has a no-argument (“default”) constructor (typically a {@link DataBoundConstructor}).
 * <p>If you need any information from the step definition after a restart,
 * make sure the {@link Step} is {@link Serializable} and do not mark it {@code transient}.
 * (For a {@link AbstractSynchronousStepExecution} these considerations are irrelevant.)
 * @author Kohsuke Kawaguchi
 */
public abstract class AbstractStepExecutionImpl extends StepExecution {

    /**
     * @deprecated Directly extend {@link StepExecution} and avoid Guice for a new step.
     * Or see {@link #AbstractStepExecutionImpl(StepContext)} for an existing step.
     */
    @Deprecated
    protected AbstractStepExecutionImpl() {
    }

    /**
     * Constructor for compatibility.
     * Retain this constructor and override {@link #onResume} (do <strong>not</strong> call the {@code super} implementation)
     * if your execution historically extended {@link AbstractStepExecutionImpl}, for serial form compatibility.
     * For new steps, extend {@link StepExecution} directly.
     */
    protected AbstractStepExecutionImpl(StepContext context) {
        super(context);
    }


    /**
     * Reinject {@link StepContextParameter}s.
     * The {@link Step} will not be reinjected.
     */
    // Cannot mark this @Deprecated without producing a warning for overriders.
    @Override
    public void onResume() {
        // TODO do this only if using the deprecated constructor
        inject();
    }

    @Deprecated
    protected void inject() {
        try {
            AbstractStepImpl.prepareInjector(getContext(), null).injectMembers(this);
        } catch (Exception e) {
            getContext().onFailure(e);
        }
    }
}
