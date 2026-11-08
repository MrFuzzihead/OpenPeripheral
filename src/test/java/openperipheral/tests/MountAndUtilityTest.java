package openperipheral.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;

import openmods.config.properties.ConfigurationChange;
import openperipheral.Config;
import openperipheral.adapter.PeripheralTypeProvider;
import openperipheral.adapter.TileEntityBlacklist;
import openperipheral.api.helpers.Index;
import openperipheral.api.peripheral.Ignore;
import openperipheral.api.peripheral.PeripheralTypeId;
import openperipheral.interfaces.cc.StringMount;
import openperipheral.interfaces.cc.UtilsResourceMount;
import openperipheral.util.NameUtils;

public class MountAndUtilityTest {

    @PeripheralTypeId("Test_Type")
    public static class AnnotatedType {
    }

    public static class NormalType {
    }

    @Ignore
    public static class IgnoredType {
    }

    public static class FieldIgnoredType {

        public static final boolean OPENPERIPHERAL_IGNORE = true;
    }

    @Test
    public void stringMountUsesUtf8Size() throws Exception {
        final String text = "h\u00e9llo";
        final StringMount mount = new StringMount(text);

        assertTrue(mount.exists("anything"));
        assertFalse(mount.isDirectory("anything"));
        assertEquals(text.getBytes(Charsets.UTF_8).length, mount.getSize("anything"));

        try (InputStream stream = mount.openForRead("anything")) {
            Assert.assertEquals(text, new String(ByteStreams.toByteArray(stream), Charsets.UTF_8));
        }
    }

    @Test
    public void resourceMountDescribesFlatFileList() throws Exception {
        final UtilsResourceMount mount = new UtilsResourceMount();

        assertTrue(mount.exists(""));
        assertTrue(mount.exists("docs"));
        assertFalse(mount.exists("missing"));
        assertTrue(mount.isDirectory(""));
        assertFalse(mount.isDirectory("docs"));

        final List<String> entries = Lists.newArrayList();
        mount.list("", entries);
        Assert.assertTrue(entries.contains("docs"));
        Assert.assertTrue(entries.contains("github"));
        Assert.assertTrue(entries.contains("multiperipheral"));
        Assert.assertTrue(entries.contains("twitter"));

        final long size = mount.getSize("docs");
        Assert.assertTrue(size > 0);
        try (InputStream stream = mount.openForRead("docs")) {
            Assert.assertEquals(size, ByteStreams.toByteArray(stream).length);
        }

        try {
            mount.openForRead("missing");
            Assert.fail("Expected IOException for missing resource");
        } catch (java.io.IOException expected) {
            // expected
        }
    }

    @Test
    public void peripheralTypeProviderPersistsNames() throws Exception {
        final File tempDir = Files.createTempDirectory("op-peripheral-names")
            .toFile();
        final PeripheralTypeProvider provider = new PeripheralTypeProvider();
        provider.initialize(tempDir);

        assertNull(provider.getType(AnnotatedType.class));
        assertEquals("test_type", provider.generateType(new AnnotatedType()));
        assertEquals("test_type", provider.getType(AnnotatedType.class));

        provider.setType(AnnotatedType.class, "custom_name");
        assertEquals("custom_name", provider.getType(AnnotatedType.class));
        assertEquals("invalid", provider.generateType(null));

        final File overlay = new File(tempDir, "peripheral_names.txt");
        Assert.assertTrue(overlay.isFile());
        final String contents = new String(Files.readAllBytes(overlay.toPath()), Charsets.UTF_8);
        Assert.assertTrue(contents.contains("custom_name"));
    }

    @Test
    public void tileEntityBlacklistRecognizesAllMechanisms() {
        final TileEntityBlacklist blacklist = new TileEntityBlacklist();

        assertFalse(blacklist.isBlacklisted(NormalType.class));
        assertTrue(blacklist.isBlacklisted(IgnoredType.class));
        assertTrue(blacklist.isBlacklisted(FieldIgnoredType.class));

        blacklist.addToBlacklist(NormalType.class);
        assertTrue(blacklist.isBlacklisted(NormalType.class));
    }

    @Test
    public void tileEntityBlacklistReadsConfig() {
        final String[] previous = Config.teBlacklist;
        try {
            Config.teBlacklist = new String[] { NormalType.class.getName() };
            final TileEntityBlacklist blacklist = new TileEntityBlacklist();

            final ConfigurationChange event = mock(ConfigurationChange.class);
            when(event.check("integration", "disableClasses")).thenReturn(true);
            blacklist.onConfigChange(event);

            assertTrue(blacklist.isBlacklisted(NormalType.class));
        } finally {
            Config.teBlacklist = previous;
        }
    }

    @Test
    public void grumizeRoundTrips() {
        final Class<?> cls = String.class;
        Assert.assertEquals(cls.getName(), NameUtils.degrumize(NameUtils.grumize(cls)));
    }

    @Test
    public void indexArithmeticAndBounds() {
        final Index index = Index.fromJava(2, 1);
        Assert.assertEquals(2, index.value);
        Assert.assertEquals(1, index.offset);
        Assert.assertEquals(3, index.intValue());
        Assert.assertEquals(3L, index.longValue());
        Assert.assertEquals(3.0f, index.floatValue(), 0.0f);
        Assert.assertEquals(3.0, index.doubleValue(), 0.0);
        Assert.assertEquals("3", index.toString());

        final Index fromScript = Index.toJava(3, 1);
        assertEquals(index, fromScript);
        assertEquals(0, index.compareTo(fromScript));
        assertEquals(index.hashCode(), fromScript.hashCode());

        index.checkElementIndex(3);
        try {
            index.checkElementIndex(2);
            Assert.fail("Expected out-of-bounds");
        } catch (IndexOutOfBoundsException expected) {
            // expected
        }

        try {
            Index.fromJava(-1, 1)
                .checkElementIndex(3);
            Assert.fail("Expected negative index rejection");
        } catch (IndexOutOfBoundsException expected) {
            // expected
        }
    }
}
