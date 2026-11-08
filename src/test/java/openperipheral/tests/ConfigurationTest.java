package openperipheral.tests;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;

import cpw.mods.fml.relauncher.FMLInjectionData;
import openperipheral.Config;
import openperipheral.OpenPeripheralCore;

public class ConfigurationTest {

    @Test
    public void loadConfigAppliesInterfaceTogglesBeforeModulesStart() throws Exception {
        final boolean previousComputerCraft = Config.interfaceComputerCraft;
        final boolean previousOpenComputers = Config.interfaceOpenComputers;
        final boolean previousDevMethods = Config.devMethods;

        final Field minecraftHome = FMLInjectionData.class.getDeclaredField("minecraftHome");
        minecraftHome.setAccessible(true);
        final Object previousHome = minecraftHome.get(null);

        try {
            final File dir = Files.createTempDirectory("op-config")
                .toFile();

            // Forge's Configuration constructor reads FMLInjectionData.data()[6] (the MC home directory),
            // which is null outside a launched game. Point it at the temp dir for this unit test.
            minecraftHome.set(null, dir);

            final File configFile = new File(dir, "OpenPeripheralCore.cfg");
            Files.write(
                configFile.toPath(),
                Arrays.asList(
                    "interfaces {",
                    "    B:ComputerCraft=false",
                    "    B:OpenComputers=false",
                    "}",
                    "dev {",
                    "    B:enableDevMethods=true",
                    "}"),
                StandardCharsets.UTF_8);

            final OpenPeripheralCore core = new OpenPeripheralCore();
            final Method loadConfig = OpenPeripheralCore.class.getDeclaredMethod("loadConfig", File.class);
            loadConfig.setAccessible(true);
            loadConfig.invoke(core, configFile);

            Assert.assertFalse(Config.interfaceComputerCraft);
            Assert.assertFalse(Config.interfaceOpenComputers);
            Assert.assertTrue(Config.devMethods);

            // Second call is idempotent and must not overwrite config with defaults.
            loadConfig.invoke(core, configFile);
            Assert.assertFalse(Config.interfaceComputerCraft);
        } finally {
            minecraftHome.set(null, previousHome);
            Config.interfaceComputerCraft = previousComputerCraft;
            Config.interfaceOpenComputers = previousOpenComputers;
            Config.devMethods = previousDevMethods;
        }
    }
}
