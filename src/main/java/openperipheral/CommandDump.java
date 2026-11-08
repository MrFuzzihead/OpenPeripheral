package openperipheral;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;

import com.google.common.collect.Lists;

import cpw.mods.fml.common.Loader;
import openmods.Log;
import openmods.OpenMods;
import openmods.utils.SidedCommand;
import openperipheral.adapter.AdapterRegistry;
import openperipheral.adapter.FeatureGroupManager;
import openperipheral.adapter.composed.ComposedMethodsFactory;
import openperipheral.adapter.composed.IMethodMap;
import openperipheral.adapter.wrappers.AdapterWrapper;
import openperipheral.util.DocBuilder;
import openperipheral.util.DocBuilder.IClassDecorator;

public class CommandDump extends SidedCommand {

    public CommandDump(String name, boolean restricted) {
        super(name, restricted);
    }

    private interface IArchSerializer {

        public void serialize(DocBuilder builder);
    }

    private static final List<IArchSerializer> archSerializers = new CopyOnWriteArrayList<IArchSerializer>();

    public static void addArchSerializer(final String architecture, final String type, final IClassDecorator decorator,
        final ComposedMethodsFactory<? extends IMethodMap> methods) {
        archSerializers.add(new IArchSerializer() {

            @Override
            public void serialize(DocBuilder builder) {
                for (Map.Entry<Class<?>, ? extends IMethodMap> e : methods.listCollectedClasses()
                    .entrySet()) builder.createDocForClass(architecture, type, decorator, e.getKey(), e.getValue());
            }
        });
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return name + " <type> <file>";
    }

    private static void processExternalAdapters(DocBuilder builder, AdapterRegistry registry, String type) {
        for (Map.Entry<Class<?>, Collection<AdapterWrapper>> e : registry.listExternalAdapters()
            .entrySet()) {
            final Class<?> cls = e.getKey();
            for (AdapterWrapper w : e.getValue()) builder.createDocForAdapter(type, "external", cls, w);
        }
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        final String format = (args.length >= 1) ? args[0] : "xhtml";
        final String name = (args.length >= 2) ? args[1] : "openperipheral_docs";
        final String filename = name + '.' + format;

        try {
            final long start = System.currentTimeMillis();
            final File output;
            try {
                output = resolveOutputFile(filename);
            } catch (IOException e) {
                sender.addChatMessage(new ChatComponentText("Invalid output path: " + filename));
                return;
            }

            final DocBuilder builder = new DocBuilder();

            builder.setRootAttribute("generatedIn", getModVersion());

            builder.setRootAttribute("generatedOn", getCurrentTime());
            builder.setRootAttribute("generatedBy", sender.getCommandSenderName());

            final Set<String> knownArchitectures = ArchitectureChecker.INSTANCE.knownArchitectures();
            for (String architecture : knownArchitectures) {
                final boolean isEnabled = ArchitectureChecker.INSTANCE.isEnabled(architecture);
                builder.createDocForArchitecture(architecture, isEnabled);
            }

            for (String fg : FeatureGroupManager.INSTANCE.knownFeatureGroups())
                builder.createDocForFeatureGroup(fg, knownArchitectures, FeatureGroupManager.INSTANCE);

            for (IArchSerializer serializer : archSerializers) serializer.serialize(builder);

            processExternalAdapters(builder, AdapterRegistry.PERIPHERAL_ADAPTERS, "peripheral");
            processExternalAdapters(builder, AdapterRegistry.OBJECT_ADAPTERS, "object");

            if (format.equalsIgnoreCase("xhtml")) builder.dumpXml(output, true);
            else if (format.equalsIgnoreCase("xml")) builder.dumpXml(output, false);
            else {
                sender.addChatMessage(new ChatComponentText("Invalid format: " + format));
                return;
            }

            long duration = System.currentTimeMillis() - start;
            sender.addChatMessage(
                new ChatComponentTranslation(
                    "openperipheralcore.dump.done",
                    format,
                    output.getAbsolutePath(),
                    duration));
        } catch (Throwable t) {
            Log.warn(t, "Failed to execute dump command");
            sender.addChatMessage(new ChatComponentTranslation("openperipheralcore.dump.fail"));
        }
    }

    static File resolveOutputFile(String filename) throws IOException {
        final File minecraftDir = OpenMods.proxy.getMinecraftDir();
        final File requested = new File(filename);
        final File output = requested.isAbsolute() ? requested : new File(minecraftDir, filename);

        final String root = minecraftDir.getCanonicalPath();
        final String resolved = output.getCanonicalPath();
        if (!resolved.equals(root) && !resolved.startsWith(root + File.separator)) {
            throw new IOException("Refusing to write outside the Minecraft directory");
        }

        return output;
    }

    private static String getCurrentTime() {
        final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z");
        final TimeZone tz = Calendar.getInstance()
            .getTimeZone();
        dateFormat.setTimeZone(tz);
        return dateFormat.format(new Date());
    }

    private static String getModVersion() {
        try {
            return Loader.instance()
                .getIndexedModList()
                .get(ModInfo.ID)
                .getDisplayVersion();
        } catch (Exception e) {
            Log.info(e, "Failed to get OpenPeripheral version");
        }
        return "unknown";
    }

    @Override
    public List<String> getCommandAliases() {
        return Collections.emptyList();
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender icommandsender, String[] astring) {
        if (astring.length == 1) return Lists.newArrayList("xml", "xhtml");
        return null;
    }

    @Override
    public boolean isUsernameIndex(String[] astring, int i) {
        return false;
    }

}
