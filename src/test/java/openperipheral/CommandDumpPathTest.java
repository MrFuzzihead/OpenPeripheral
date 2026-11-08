package openperipheral;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.junit.Assert;
import org.junit.Test;

import openmods.OpenMods;
import openmods.proxy.IOpenModsProxy;

public class CommandDumpPathTest {

    @Test
    public void outputIsConfinedToTheMinecraftDirectory() throws Exception {
        final IOpenModsProxy previous = OpenMods.proxy;
        final File dir = Files.createTempDirectory("op-dump")
            .toFile();
        final IOpenModsProxy proxy = mock(IOpenModsProxy.class);
        when(proxy.getMinecraftDir()).thenReturn(dir);
        OpenMods.proxy = proxy;

        try {
            final File inside = CommandDump.resolveOutputFile("docs.xml");
            assertTrue(
                inside.getCanonicalPath()
                    .startsWith(dir.getCanonicalPath()));

            try {
                CommandDump.resolveOutputFile("../escape.xml");
                Assert.fail("Expected path traversal rejection");
            } catch (IOException expected) {
                // expected
            }
        } finally {
            OpenMods.proxy = previous;
        }
    }
}
