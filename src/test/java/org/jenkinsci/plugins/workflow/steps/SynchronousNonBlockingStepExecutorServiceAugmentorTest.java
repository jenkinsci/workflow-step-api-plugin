package org.jenkinsci.plugins.workflow.steps;

import static org.hamcrest.MatcherAssert.assertThat;

import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.hamcrest.Matchers;
import org.junit.AfterClass;
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

    /**
     * As the JVM and classes are loaded only once for the whole test, {@link SynchronousNonBlockingStepExecution#getExecutorService()} augments only once. The current boolean keeps track of the augmentation status.
     */
    private static boolean augmented = false;

    @Test
    public void smokes_configured_once_A() throws Exception {
        SynchronousNonBlockingStepExecution.getExecutorService();
        checkAugmentation();
    }

    @Test
    public void smokes_configured_once_B() throws Exception {
        SynchronousNonBlockingStepExecution.getExecutorService();
        checkAugmentation();
    }

    private void checkAugmentation() {
        if (augmented) {
            assertThat(loggerRule.getMessages(), Matchers.emptyIterable());
        } else {
            assertThat(loggerRule.getMessages(), Matchers.hasItem("Augmenting"));
            augmented = true;
        }
    }
    
    @AfterClass
    public static void afterClass() {
        // Reset the static state to ensure that the test can be run multiple times without issues.
        assertThat(augmented, Matchers.is(true));
    }

    @TestExtension
    public static class AugmentorTestExtension implements SynchronousNonBlockingStepExecution.ExecutorServiceAugmentor {

        private static final Logger LOGGER = Logger.getLogger(AugmentorTestExtension.class.getName());

        @Override
        public ExecutorService augment(ExecutorService executorService) {
            LOGGER.fine(() -> "Augmenting");
            return executorService;
        }
    }
}
