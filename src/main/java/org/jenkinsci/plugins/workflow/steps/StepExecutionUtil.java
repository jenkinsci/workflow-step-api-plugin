package org.jenkinsci.plugins.workflow.steps;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class StepExecutionUtil {

    private static final Logger LOGGER = Logger.getLogger(StepExecutionUtil.class.getName());

    static String getStepDisplayFunctionName(StepExecution stepExecution) {
        StepDescriptor descriptor = null;
        try {
            descriptor = stepExecution.getContext().get(StepDescriptor.class);
        } catch (IOException | InterruptedException e) {
            LOGGER.log(Level.FINE, "Failed get descriptor: ", e);
        }
        return descriptor != null ? descriptor.getFunctionName() : "";
    }
}
