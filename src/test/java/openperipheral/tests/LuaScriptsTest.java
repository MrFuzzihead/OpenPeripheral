package openperipheral.tests;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import dan200.computercraft.shadow.org.squiddev.cobalt.Constants;
import dan200.computercraft.shadow.org.squiddev.cobalt.LuaError;
import dan200.computercraft.shadow.org.squiddev.cobalt.LuaState;
import dan200.computercraft.shadow.org.squiddev.cobalt.LuaString;
import dan200.computercraft.shadow.org.squiddev.cobalt.LuaTable;
import dan200.computercraft.shadow.org.squiddev.cobalt.LuaThread;
import dan200.computercraft.shadow.org.squiddev.cobalt.LuaValue;
import dan200.computercraft.shadow.org.squiddev.cobalt.ValueFactory;
import dan200.computercraft.shadow.org.squiddev.cobalt.compiler.LuaC;
import dan200.computercraft.shadow.org.squiddev.cobalt.debug.Upvalue;
import dan200.computercraft.shadow.org.squiddev.cobalt.function.LibFunction;
import dan200.computercraft.shadow.org.squiddev.cobalt.function.LuaInterpretedFunction;
import dan200.computercraft.shadow.org.squiddev.cobalt.lib.BaseLib;
import dan200.computercraft.shadow.org.squiddev.cobalt.lib.MathLib;
import dan200.computercraft.shadow.org.squiddev.cobalt.lib.StringLib;
import dan200.computercraft.shadow.org.squiddev.cobalt.lib.TableLib;

/**
 * Exercises the Lua programs bundled in {@code src/main/resources/openperipheral/lua} with the same Cobalt
 * interpreter ComputerCraft uses. The network-dependent scripts are tested with stubbed APIs; the pure
 * program logic is executed for real.
 */
public class LuaScriptsTest {

    private static final String LUA_ROOT = "/openperipheral/lua/";

    private static String readResource(String path) throws IOException {
        try (InputStream stream = LuaScriptsTest.class.getResourceAsStream(path)) {
            Assert.assertNotNull("Missing resource: " + path, stream);
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final byte[] buffer = new byte[4096];
            int read;
            while ((read = stream.read(buffer)) != -1) out.write(buffer, 0, read);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static List<String> manifestEntries() throws IOException {
        final List<String> result = new ArrayList<String>();
        for (String line : readResource(LUA_ROOT + "files.lst").split("\\r?\\n")) {
            line = line.trim();
            if (!line.isEmpty()) result.add(line);
        }
        return result;
    }

    @Test
    public void manifestListsEveryBundledProgramAndEveryProgramParses() throws Exception {
        final List<String> expected = Arrays.asList("docs", "github", "multiperipheral");
        Assert.assertEquals("Unexpected bundled Lua programs", expected, manifestEntries());

        for (String name : expected) {
            final String source = readResource(LUA_ROOT + name);
            Assert.assertFalse(
                name + " is empty",
                source.trim()
                    .isEmpty());
            compile(name, source);
        }
    }

    @Test
    public void manifestDoesNotListFilesThatAreNotShipped() throws Exception {
        for (String name : manifestEntries()) {
            Assert.assertNotNull(
                "files.lst lists missing program " + name,
                LuaScriptsTest.class.getResource(LUA_ROOT + name));
        }

        final URL directory = LuaScriptsTest.class.getResource(LUA_ROOT);
        if (directory != null && "file".equals(directory.getProtocol())) {
            final File[] files;
            try {
                files = new File(directory.toURI()).listFiles();
            } catch (URISyntaxException e) {
                throw new AssertionError(e);
            }

            if (files != null) {
                for (File file : files) {
                    if (file.isFile() && !file.getName()
                        .equals("files.lst")) {
                        Assert.assertTrue(
                            "Unlisted Lua program: " + file.getName(),
                            manifestEntries().contains(file.getName()));
                    }
                }
            }
        }
    }

    @Test
    public void docsListsMethodsAndRendersDetails() throws Exception {
        final Execution execution = new Execution();
        execution.installChestDocs();

        execution.run("docs", "back");
        Assert.assertTrue(execution.output.contains("asyncPing()"));
        Assert.assertTrue(execution.output.contains("getSlotCount()"));
        Assert.assertTrue(execution.output.contains("getStack(slot)"));
        Assert.assertTrue(execution.output.contains("setStack(slot,stack)"));

        execution.clear();
        execution.run("docs", "back", "getStack");
        Assert.assertTrue(execution.output.contains("getStack: Gets an item stack"));
        Assert.assertTrue(execution.output.contains("source: chest"));
        Assert.assertTrue(execution.output.contains("returns: table"));
        Assert.assertTrue(execution.output.contains(" - (number)slot: Slot index"));
    }

    @Test
    public void docsHandlesUnknownMethodsAndPeripherals() throws Exception {
        final Execution execution = new Execution();
        execution.installChestDocs();

        execution.run("docs", "back", "missing");
        Assert.assertTrue(execution.output.contains("No function named 'missing'"));

        execution.clear();
        execution.run("docs", "back");
        execution.set("peripheral", peripheralTable(null));
        execution.run("docs", "back");
        Assert.assertTrue(execution.output.contains("No peripheral on 'back'"));
    }

    @Test
    public void multiperipheralDispatchesToEveryTargetAndKeepsClosuresSeparate() throws Exception {
        final Execution execution = new Execution();
        final List<String> calls = new ArrayList<String>();

        final LuaTable leftMethods = list(str("ping"), str("getSlotCount"));
        final LuaTable rightMethods = list(str("ping"), str("getStack"));

        final LuaTable peripheral = table();
        peripheral.rawset("getMethods", fn((state, args) -> {
            final String name = args.arg(1)
                .toString();
            if ("left".equals(name)) return ValueFactory.varargsOf(leftMethods);
            if ("right".equals(name)) return ValueFactory.varargsOf(rightMethods);
            return Constants.NONE;
        }));
        peripheral.rawset(
            "getNames",
            fn((state, args) -> ValueFactory.varargsOf(ValueFactory.listOf(str("left"), str("right"), str("other")))));
        peripheral.rawset("getType", fn((state, args) -> {
            final String name = args.arg(1)
                .toString();
            if ("right".equals(name)) return ValueFactory.varargsOf(str("furnace"));
            return ValueFactory.varargsOf(str("chest"));
        }));
        peripheral.rawset("call", fn((state, args) -> {
            final String name = args.arg(1)
                .toString();
            final String method = args.arg(2)
                .toString();
            final String value = args.arg(3)
                .toString();
            calls.add(name + ":" + method + ":" + value);
            return ValueFactory.varargsOf(str(name + ":" + method + ":" + value));
        }));
        execution.set("peripheral", peripheral);

        execution.run("multiperipheral");
        execution.runSource(
            "multiperipheral-test",
            "local mp = new()\n" + "mp:wrap('left', 'right')\n"
                + "mp:wrap('left')\n"
                + "print('names=' .. table.concat(mp:getNames(), ','))\n"
                + "local results = mp:ping(7)\n"
                + "print('count=' .. #results)\n"
                + "print('results=' .. table.concat(results, ','))\n"
                + "print('text=' .. tostring(mp))\n"
                + "local typed = new()\n"
                + "typed:types('chest')\n"
                + "print('types=' .. table.concat(typed:getNames(), ','))\n");

        Assert.assertTrue(execution.output.contains("names=left,right"));
        Assert.assertTrue(execution.output.contains("count=2"));
        Assert.assertTrue(execution.output.contains("results=left:ping:7,right:ping:7"));
        Assert.assertTrue(execution.output.contains("text=multiperipheral(left,right)"));
        Assert.assertTrue(execution.output.contains("types=left,other"));
        Assert.assertEquals(Arrays.asList("left:ping:7", "right:ping:7"), calls);
    }

    @Test
    public void githubDownloadsFromRawGithubAndWritesTheFile() throws Exception {
        final Execution execution = new Execution();
        final List<String> requestedUrls = new ArrayList<String>();
        final List<String> written = new ArrayList<String>();
        final boolean[] closed = new boolean[1];

        final LuaTable response = table();
        response.rawset("readAll", fn((state, args) -> ValueFactory.varargsOf(str("payload"))));
        response.rawset("close", fn((state, args) -> {
            closed[0] = true;
            return Constants.NONE;
        }));

        final LuaTable file = table();
        file.rawset("write", fn((state, args) -> {
            written.add(
                args.arg(1)
                    .toString());
            return Constants.NONE;
        }));
        file.rawset("close", fn((state, args) -> Constants.NONE));

        final LuaTable fs = table();
        fs.rawset("exists", fn((state, args) -> ValueFactory.varargsOf(Constants.FALSE)));
        fs.rawset("open", fn((state, args) -> ValueFactory.varargsOf(file)));

        final LuaTable http = table();
        http.rawset("get", fn((state, args) -> {
            requestedUrls.add(
                args.arg(1)
                    .toString());
            return ValueFactory.varargsOf(response);
        }));

        final LuaTable shell = table();
        shell.rawset(
            "resolve",
            fn(
                (state, args) -> ValueFactory.varargsOf(
                    str(
                        "/downloads/" + args.arg(1)
                            .toString()))));

        execution.set("http", http);
        execution.set("fs", fs);
        execution.set("shell", shell);

        execution.run("github", "get", "owner", "repo", "main", "dir/file.lua", "out.lua");

        Assert.assertEquals(
            Collections.singletonList("https://raw.githubusercontent.com/owner/repo/main/dir/file.lua"),
            requestedUrls);
        Assert.assertEquals(Collections.singletonList("payload"), written);
        Assert.assertTrue(closed[0]);
        Assert.assertTrue(execution.output.contains("Downloaded as out.lua"));
    }

    @Test
    public void githubRunWithoutATargetPrintsUsageInsteadOfCrashing() throws Exception {
        final Execution execution = new Execution();
        execution.set("http", table());
        execution.run("github", "run");
        Assert.assertTrue(execution.output.contains("Usages:"));
    }

    @Test
    public void githubReportsHttpFailureWithoutWriting() throws Exception {
        final Execution execution = new Execution();
        final boolean[] opened = new boolean[1];

        final LuaTable http = table();
        http.rawset("get", fn((state, args) -> Constants.NONE));

        final LuaTable fs = table();
        fs.rawset("exists", fn((state, args) -> ValueFactory.varargsOf(Constants.FALSE)));
        fs.rawset("open", fn((state, args) -> {
            opened[0] = true;
            return Constants.NONE;
        }));

        final LuaTable shell = table();
        shell.rawset("resolve", fn((state, args) -> ValueFactory.varargsOf(str("out.lua"))));

        execution.set("http", http);
        execution.set("fs", fs);
        execution.set("shell", shell);

        execution.run("github", "get", "owner", "repo", "main", "file.lua", "out.lua");

        Assert.assertFalse(opened[0]);
        Assert.assertTrue(execution.output.contains("Failed."));
    }

    private static void compile(String name, String source) throws Exception {
        final LuaState state = new LuaState();
        BaseLib.add(state);
        // Calling through LuaC.compile keeps the test on the exact parser used by ComputerCraft.
        LuaC.compile(state, new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)), LuaString.valueOf(name));
    }

    private static LuaString str(String value) {
        return ValueFactory.valueOf(value);
    }

    private static LuaTable table() {
        return ValueFactory.tableOf();
    }

    private static LuaTable list(LuaValue... values) {
        return ValueFactory.listOf(values);
    }

    private static LuaValue fn(LibFunction.ManyArgs function) {
        return LibFunction.createV(function);
    }

    private static LuaTable peripheralTable(LuaTable methods) {
        final LuaTable peripheral = table();
        peripheral
            .rawset("wrap", fn((state, args) -> methods != null ? ValueFactory.varargsOf(methods) : Constants.NONE));
        peripheral.rawset("getType", fn((state, args) -> ValueFactory.varargsOf(str("chest"))));
        return peripheral;
    }

    private static final class Execution {

        final LuaState state;
        final List<String> output = new ArrayList<String>();

        Execution() throws LuaError {
            state = new LuaState();
            BaseLib.add(state);
            TableLib.add(state);
            StringLib.add(state);
            MathLib.add(state);

            state.globals()
                .rawset("print", fn((s, args) -> {
                    output.add(
                        args.arg(1)
                            .toString());
                    return Constants.NONE;
                }));
            state.globals()
                .rawset("write", fn((s, args) -> {
                    output.add(
                        args.arg(1)
                            .toString());
                    return Constants.NONE;
                }));
            state.globals()
                .rawset("printError", fn((s, args) -> {
                    output.add(
                        args.arg(1)
                            .toString());
                    return Constants.NONE;
                }));
            state.globals()
                .rawset("term", table());
        }

        void clear() {
            output.clear();
        }

        void set(String name, LuaValue value) {
            state.globals()
                .rawset(name, value);
        }

        void run(String name, String... args) throws Exception {
            runSource(name, readResource(LUA_ROOT + name), args);
        }

        void runSource(String name, String source, String... args) throws Exception {
            final LuaValue[] values = new LuaValue[args.length];
            for (int i = 0; i < args.length; i++) values[i] = str(args[i]);

            final dan200.computercraft.shadow.org.squiddev.cobalt.Prototype prototype = LuaC.compile(
                state,
                new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)),
                LuaString.valueOf(name));
            final LuaInterpretedFunction function = new LuaInterpretedFunction(prototype);
            function.nilUpvalues();
            function.setUpvalue(0, new Upvalue(state.globals()));
            LuaThread.runMain(state, function, ValueFactory.varargsOf(values));
        }

        void installChestDocs() {
            final LuaTable slot = table();
            slot.rawset("name", str("slot"));
            slot.rawset("type", str("NUMBER"));
            slot.rawset("optional", Constants.FALSE);
            slot.rawset("vararg", Constants.FALSE);
            slot.rawset("description", str("Slot index"));

            final LuaTable stack = table();
            stack.rawset("name", str("stack"));
            stack.rawset("type", str("TABLE"));
            stack.rawset("optional", Constants.FALSE);
            stack.rawset("vararg", Constants.FALSE);
            stack.rawset("description", str("Item stack"));

            final LuaTable getStack = table();
            getStack.rawset("description", str("Gets an item stack"));
            getStack.rawset("source", str("chest"));
            getStack.rawset("returnTypes", str("TABLE"));
            getStack.rawset("args", list(slot));

            final LuaTable setStack = table();
            setStack.rawset("description", str("Sets an item stack"));
            setStack.rawset("source", str("chest"));
            setStack.rawset("returnTypes", str("STRING"));
            setStack.rawset("args", list(slot, stack));

            final LuaTable info = table();
            info.rawset(
                "getSlotCount",
                tableOf(
                    "description",
                    str("Number of slots"),
                    "source",
                    str("chest"),
                    "returnTypes",
                    str("NUMBER"),
                    "args",
                    list()));
            info.rawset("getStack", getStack);
            info.rawset("setStack", setStack);
            info.rawset(
                "asyncPing",
                tableOf(
                    "description",
                    str("Ping"),
                    "source",
                    str("chest"),
                    "returnTypes",
                    str("STRING"),
                    "args",
                    list()));

            final LuaTable methods = table();
            methods.rawset("getAdvancedMethodsData", fn((state, args) -> ValueFactory.varargsOf(info)));
            set("peripheral", peripheralTable(methods));
        }

        private static LuaTable tableOf(Object... pairs) {
            final LuaTable result = table();
            for (int i = 0; i < pairs.length; i += 2) {
                result.rawset((String) pairs[i], (LuaValue) pairs[i + 1]);
            }
            return result;
        }
    }
}
