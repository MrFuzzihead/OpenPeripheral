package openperipheral.gametest;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import cpw.mods.fml.common.FMLCommonHandler;
import openmods.Log;

/**
 * Client-side half of the game-test mod. It opens the real config screen and verifies that Forge can
 * construct and display it. The client is expected to run under Xvfb in CI.
 */
public class ClientGameTests {

    public static void run() {
        final Minecraft minecraft = Minecraft.getMinecraft();
        minecraft.func_152344_a(new Runnable() {

            @Override
            public void run() {
                try {
                    final GuiScreen screen = new openperipheral.ConfigGuiFactory.ConfigScreen(null);
                    minecraft.displayGuiScreen(screen);
                    if (!(minecraft.currentScreen instanceof openperipheral.ConfigGuiFactory.ConfigScreen)) {
                        throw new AssertionError("Config screen was not displayed");
                    }

                    Log.info("OPENPERIPHERAL_CLIENT_GAMETEST_OK");
                    FMLCommonHandler.instance()
                        .exitJava(0, false);
                } catch (Throwable t) {
                    t.printStackTrace();
                    Log.severe(t, "OPENPERIPHERAL_CLIENT_GAMETEST_FAILED");
                    FMLCommonHandler.instance()
                        .exitJava(1, false);
                }
            }
        });
    }
}
