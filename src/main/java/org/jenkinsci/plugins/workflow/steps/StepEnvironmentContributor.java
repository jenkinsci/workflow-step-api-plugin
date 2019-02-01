package org.jenkinsci.plugins.workflow.steps;

import hudson.EnvVars;
import hudson.ExtensionPoint;
import hudson.model.TaskListener;
import java.io.IOException;
import javax.annotation.Nonnull;

/**
 * Contributes environment variables to workflow steps.
 *
 * <p>
 * This extension point can be used to externally add environment variables per workflow step.
 *
 * @author Thomas Weißschuh, Amadeus Germany GmbH
 * @since XXX
 * @see hudson.model.EnvironmentContributor
 */
public abstract class StepEnvironmentContributor implements ExtensionPoint {

  /**
   * Contributes environment variables used for a workflow step.
   *
   * <p>
   * This method can be called repeatedly for the same {@link Step}, thus
   * the computation of this method needs to be efficient.
   *
   * <p>
   * This method gets invoked concurrently for multiple {@link Step}s that are being built at the same time,
   * so it must be concurrent-safe.
   *
   * <p>
   * When building environment variables for a build, Jenkins will also invoke
   * {@link hudson.model.EnvironmentContributor#buildEnvironmentFor(hudson.model.Run, EnvVars, TaskListener)} and
   * {@link hudson.model.EnvironmentContributor#buildEnvironmentFor(hudson.model.Job, EnvVars, TaskListener)}
   * This method only needs to add variables that are scoped to a workflow step.

   * @param stepContext
   *      Context of step that's being executed
   * @param envs
   *      Partially built environment variable map. Implementation of this method is expected to
   *      add additional variables here.
   * @param listener
   *      Connected to the build console. Can be used to report errors.
   */
  public void buildEnvironmentFor(@Nonnull StepContext stepContext, @Nonnull EnvVars envs, @Nonnull TaskListener listener) throws IOException, InterruptedException {}
}
