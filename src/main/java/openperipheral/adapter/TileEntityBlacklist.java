package openperipheral.adapter;

import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import openmods.Log;
import openmods.config.properties.ConfigurationChange;
import openperipheral.Config;
import openperipheral.api.peripheral.IPeripheralBlacklist;
import openperipheral.api.peripheral.Ignore;

public class TileEntityBlacklist implements IPeripheralBlacklist {

    public static final TileEntityBlacklist INSTANCE = new TileEntityBlacklist();

    private final Set<String> imcBlacklist = newSet();

    private final Set<String> fullBlacklist = newSet();

    private static Set<String> newSet() {
        return Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    }

    @SubscribeEvent
    public void onConfigChange(ConfigurationChange evt) {
        if (evt.check("integration", "disableClasses")) {
            fullBlacklist.clear();
            fullBlacklist.addAll(imcBlacklist);
            for (String cls : Config.teBlacklist) fullBlacklist.add(normalize(cls));
        }
    }

    private static String normalize(String className) {
        return className != null ? className.toLowerCase(Locale.ENGLISH) : null;
    }

    @Override
    public boolean isBlacklisted(Class<?> teClass) {
        if (teClass == null) return false;

        final String teClassName = teClass.getName()
            .toLowerCase(Locale.ENGLISH);
        if (fullBlacklist.contains(teClassName)) return true;

        if (teClass.isAnnotationPresent(Ignore.class)) {
            fullBlacklist.add(teClassName);
            return true;
        }

        try {
            teClass.getField("OPENPERIPHERAL_IGNORE");
            fullBlacklist.add(teClassName);
            return true;
        } catch (NoSuchFieldException e) {
            // uff, we are not ignored
        } catch (Throwable t) {
            Log.warn(t, "Class %s doesn't cooperate", teClass);
        }

        return false;
    }

    @Override
    public void addToBlacklist(String className) {
        if (className == null) return;

        final String normalized = normalize(className);
        // IMC messages are processed after the initial config event, so update the live set as well.
        // Otherwise an addToBlacklist() from a mod would only take effect after the next config change.
        imcBlacklist.add(normalized);
        fullBlacklist.add(normalized);
    }

    @Override
    public void addToBlacklist(Class<?> cls) {
        if (cls != null) addToBlacklist(cls.getName());
    }
}
