package openperipheral.tests;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import openperipheral.CommandDump;

public class CommandDumpTest {

    private static CommandDump command(boolean restricted) {
        return new CommandDump("op_dump", restricted);
    }

    @Test
    public void usageAndAliases() {
        final CommandDump command = command(false);
        Assert.assertEquals("op_dump <type> <file>", command.getCommandUsage(null));
        Assert.assertTrue(
            command.getCommandAliases()
                .isEmpty());
        Assert.assertFalse(command.isUsernameIndex(new String[] { "op_dump", "xml" }, 1));
    }

    @Test
    public void tabCompletionOffersFormatsOnlyForFirstArgument() {
        final CommandDump command = command(false);
        final List<String> first = command.addTabCompletionOptions(null, new String[] { "x" });
        Assert.assertEquals(java.util.Arrays.asList("xml", "xhtml"), first);
        Assert.assertNull(command.addTabCompletionOptions(null, new String[] { "x", "y" }));
    }
}
