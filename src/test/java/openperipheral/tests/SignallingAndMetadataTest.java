package openperipheral.tests;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Test;

import com.google.common.io.ByteStreams;

import openperipheral.ModInfo;
import openperipheral.adapter.wrappers.SignallingGlobals;

public class SignallingAndMetadataTest {

    @Test
    public void callbackIdsAreUnique() {
        final int first = SignallingGlobals.instance.nextCallbackId();
        final int second = SignallingGlobals.instance.nextCallbackId();
        Assert.assertEquals(first + 1, second);
    }

    @Test
    public void signallingTasksRunOnDaemonWorkerThreads() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicBoolean daemon = new AtomicBoolean();
        final AtomicInteger runs = new AtomicInteger();

        SignallingGlobals.instance.scheduleTask(new Runnable() {

            @Override
            public void run() {
                daemon.set(
                    Thread.currentThread()
                        .isDaemon());
                runs.incrementAndGet();
                latch.countDown();
            }
        });

        Assert.assertTrue(latch.await(10, TimeUnit.SECONDS));
        Assert.assertEquals(1, runs.get());
        Assert.assertTrue(daemon.get());

        // null tasks are ignored rather than failing a peripheral call
        SignallingGlobals.instance.scheduleTask(null);
    }

    @Test
    public void mcmodInfoHasWellFormedUrls() throws Exception {
        try (InputStream stream = getClass().getClassLoader()
            .getResourceAsStream("mcmod.info")) {
            Assert.assertNotNull("mcmod.info is missing from the runtime classpath", stream);
            final String content = new String(ByteStreams.toByteArray(stream), StandardCharsets.UTF_8);

            Assert.assertFalse(content.contains("github.com/MrFuzzihead/https"));
            Assert.assertTrue(content.contains("\"url\": \"https://github.com/MrFuzzihead/OpenPeripheral\""));
            Assert.assertTrue(
                content.contains("\"updateUrl\": \"https://github.com/MrFuzzihead/OpenPeripheral/releases/latest\""));
            Assert.assertTrue(content.contains("\"modid\": \"OpenPeripheralCore\""));
        }
    }

    @Test
    public void modDependenciesDeclareExpectedRanges() {
        Assert.assertTrue(ModInfo.DEPENDENCIES.contains("required-after:OpenMods@["));
        Assert.assertTrue(ModInfo.DEPENDENCIES.contains("after:ComputerCraft@[3.0.0,]"));
        Assert.assertTrue(ModInfo.DEPENDENCIES.contains("after:OpenComputers@["));
    }
}
