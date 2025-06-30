package org.jenkinsci.plugins.workflow.steps;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;

import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.TestExtension;

public class SynchronousNonBlockingStepExecutorServiceAugmentorTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Rule
    public LoggerRule loggerRule =
            new LoggerRule().record(AugmentorTestExtension.class, Level.FINE).capture(10);

    @Test
    public void smokes() throws Exception {
        SynchronousNonBlockingStepExecution.getExecutorService();
        assertThat(loggerRule.getMessages(), hasItem("Augmenting"));
    }

    @TestExtension
    public static class AugmentorTestExtension
            implements SynchronousNonBlockingStepExecution.SynchronousNonBlockingStepExecutorServiceAugmentor {

        private static final Logger LOGGER = Logger.getLogger(AugmentorTestExtension.class.getName());

        @Override
        public ExecutorService augment(ExecutorService executorService) {
            LOGGER.fine(() -> "Augmenting");
            return executorService;
        }
    }
}
