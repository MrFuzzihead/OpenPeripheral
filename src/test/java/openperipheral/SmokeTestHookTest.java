package openperipheral;

import org.junit.Assert;
import org.junit.Test;

public class SmokeTestHookTest {

    @Test
    public void hookIsInertWithoutTheProperty() {
        final String previous = System.getProperty(SmokeTestHook.ENABLE_PROPERTY);
        System.clearProperty(SmokeTestHook.ENABLE_PROPERTY);
        try {
            SmokeTestHook.scheduleExitIfRequested();
            Assert.fail("Smoke hook should be inert without its property");
        } catch (AssertionError expected) {
            // The test process is still alive: scheduleExitIfRequested() must have returned.
        } finally {
            if (previous != null) System.setProperty(SmokeTestHook.ENABLE_PROPERTY, previous);
        }
    }
}
