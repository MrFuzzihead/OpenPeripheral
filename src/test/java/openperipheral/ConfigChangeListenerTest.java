package openperipheral;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;

import net.minecraftforge.common.config.Configuration;

import org.junit.Assert;
import org.junit.Test;

import cpw.mods.fml.client.event.ConfigChangedEvent.OnConfigChangedEvent;
import cpw.mods.fml.relauncher.FMLInjectionData;
import openperipheral.adapter.FeatureGroupManager;

public class ConfigChangeListenerTest {

    @Test
    public void featureGroupChangesArePersisted() throws Exception {
        final Field minecraftHome = FMLInjectionData.class.getDeclaredField("minecraftHome");
        minecraftHome.setAccessible(true);
        final Object previousHome = minecraftHome.get(null);

        final String group = "gametest_listener_" + System.nanoTime();
        final String architecture = "ComputerCraft";

        try {
            final File dir = Files.createTempDirectory("op-config-listener")
                .toFile();
            minecraftHome.set(null, dir);

            FeatureGroupManager.INSTANCE.ensureExists(group);
            FeatureGroupManager.INSTANCE.disable(group, architecture);
            new ConfigGuiFactory().initialize(null);

            final File configFile = new File(dir, "OpenPeripheralCore.cfg");
            final Configuration config = new Configuration(configFile);
            new ConfigGuiFactory.ConfigChangeListener(config)
                .onConfigChange(new OnConfigChangedEvent(ModInfo.ID, null, false, false));

            final String[] saved = config
                .get(Config.CATEGORY_FEATURE_GROUPS, Config.FIELD_FEATURE_GROUPS, new String[0])
                .getStringList();
            Assert.assertTrue(
                java.util.Arrays.asList(saved)
                    .contains(group + ":" + architecture));
            Assert.assertTrue(configFile.isFile());
        } finally {
            minecraftHome.set(null, previousHome);
            FeatureGroupManager.INSTANCE.enable(group, architecture);
        }
    }
}
