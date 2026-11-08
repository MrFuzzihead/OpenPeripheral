package openperipheral;

import cpw.mods.fml.common.FMLCommonHandler;
import openmods.Log;

/**
 * Development/CI only. When the {@code openperipheral.smokeTest} system property is set, the game logs a
 * machine-readable marker after FML has finished loading and then exits. This lets a CI job boot the real
 * dedicated server (or the client under Xvfb) without needing an operator to type {@code stop}.
 *
 * The hook is a no-op in normal gameplay: the property is only set by the {@code -PsmokeTest} Gradle property.
 */
public class SmokeTestHook {

    public static final String ENABLE_PROPERTY = "openperipheral.smokeTest";
    public static final String DELAY_PROPERTY = "openperipheral.smokeTest.delay";
    public static final String SUCCESS_MARKER = "OPENPERIPHERAL_SMOKE_TEST_OK";

    private SmokeTestHook() {}

    public static void scheduleExitIfRequested() {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) return;

        final long delay = Long.getLong(DELAY_PROPERTY, 2000L);
        final Thread thread = new Thread(new Runnable() {

            @Override
            public void run() {
                try {
                    Thread.sleep(Math.max(0L, delay));
                } catch (InterruptedException e) {
                    Thread.currentThread()
                        .interrupt();
                    return;
                }

                Log.info("%s: FML load complete, shutting down", SUCCESS_MARKER);
                FMLCommonHandler.instance()
                    .exitJava(0, false);
            }
        }, "OpenPeripheral-smoke-test");
        thread.setDaemon(true);
        thread.start();
    }
}
