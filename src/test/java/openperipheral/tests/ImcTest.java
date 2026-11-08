package openperipheral.tests;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

import org.junit.Assert;
import org.junit.Test;

import com.google.common.collect.ImmutableList;

import cpw.mods.fml.common.event.FMLInterModComms;
import openperipheral.OpenPeripheralCore;
import openperipheral.adapter.PeripheralTypeProvider;
import openperipheral.adapter.TileEntityBlacklist;
import openperipheral.api.Constants;

public class ImcTest {

    public static class ImcTarget {
    }

    private static FMLInterModComms.IMCMessage message(String key, String value) throws Exception {
        final Class<?> messageClass = Class.forName("cpw.mods.fml.common.event.FMLInterModComms$IMCMessage");
        final Class<?> markerClass = Class.forName("cpw.mods.fml.common.event.FMLInterModComms$1");
        final Constructor<?> ctor = messageClass.getDeclaredConstructor(String.class, Object.class, markerClass);
        ctor.setAccessible(true);
        return (FMLInterModComms.IMCMessage) ctor.newInstance(key, value, null);
    }

    private static FMLInterModComms.IMCEvent event(FMLInterModComms.IMCMessage... messages) throws Exception {
        final FMLInterModComms.IMCEvent event = new FMLInterModComms.IMCEvent();
        final Field field = FMLInterModComms.IMCEvent.class.getDeclaredField("currentList");
        field.setAccessible(true);
        field.set(event, ImmutableList.copyOf(messages));
        return event;
    }

    @Test
    public void ignoreMessageAddsToLiveBlacklist() throws Exception {
        final FMLInterModComms.IMCEvent event = event(message(Constants.IMC_IGNORE, ImcTarget.class.getName()));
        new OpenPeripheralCore().processMessage(event);

        Assert.assertTrue(TileEntityBlacklist.INSTANCE.isBlacklisted(ImcTarget.class));
    }

    @Test
    public void classTypeMessageOverridesPeripheralName() throws Exception {
        final String type = "imc_" + System.nanoTime();
        final String value = ImcTarget.class.getName() + " " + type;
        final FMLInterModComms.IMCEvent event = event(message(Constants.IMC_NAME_CLASS, value));
        new OpenPeripheralCore().processMessage(event);

        Assert.assertEquals(type, PeripheralTypeProvider.INSTANCE.getType(ImcTarget.class));
    }

    @Test
    public void malformedClassTypeMessageIsIgnored() throws Exception {
        final FMLInterModComms.IMCEvent event = event(message(Constants.IMC_NAME_CLASS, "not-enough-fields"));
        new OpenPeripheralCore().processMessage(event);
        // No exception is the assertion: a bad third-party IMC must not crash preInit.
    }
}
