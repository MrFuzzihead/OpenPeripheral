package openperipheral.gametest;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLLoadCompleteEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.filesystem.IMount;
import dan200.computercraft.api.filesystem.IWritableMount;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.ILuaTask;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.MethodResult;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.shared.computer.blocks.TileComputer;
import dan200.computercraft.shared.computer.core.ComputerFamily;
import dan200.computercraft.shared.computer.core.ServerComputer;
import openmods.Log;
import openperipheral.Config;
import openperipheral.adapter.TileEntityBlacklist;
import openperipheral.api.ApiAccess;
import openperipheral.api.Constants;
import openperipheral.api.adapter.IPeripheralAdapter;
import openperipheral.api.adapter.IPeripheralAdapterRegistry;
import openperipheral.api.adapter.IPeripheralTypeProvider;
import openperipheral.api.adapter.method.Arg;
import openperipheral.api.adapter.method.ReturnType;
import openperipheral.api.adapter.method.ScriptCallable;
import openperipheral.api.architecture.IArchitectureChecker;
import openperipheral.api.architecture.IFeatureGroupManager;
import openperipheral.api.converter.IConverter;
import openperipheral.api.converter.IConverterManager;
import openperipheral.api.meta.IEntityMetaBuilder;
import openperipheral.api.meta.IEntityMetaProvider;
import openperipheral.api.meta.IEntityPartialMetaBuilder;
import openperipheral.api.meta.IItemStackMetaBuilder;
import openperipheral.api.meta.IItemStackMetaProvider;
import openperipheral.api.meta.IItemStackPartialMetaBuilder;
import openperipheral.api.meta.IMetaProviderProxy;
import openperipheral.api.peripheral.IPeripheralBlacklist;
import openperipheral.converter.TypeConvertersProvider;
import openperipheral.interfaces.cc.providers.PeripheralProvider;

@Mod(
    modid = "openperipheral_gametest",
    name = "OpenPeripheral Game Tests",
    version = "1.0",
    acceptableRemoteVersions = "*")
public class GameTestMod {

    private static final boolean ENABLED = Boolean.getBoolean("openperipheral.gameTest");

    private static IPeripheral realComputerPeripheral;

    public static class ChestTestAdapter implements IPeripheralAdapter {

        @Override
        public String getSourceId() {
            return "gametest";
        }

        @Override
        public Class<?> getTargetClass() {
            return TileEntityChest.class;
        }

        @ScriptCallable(returnTypes = ReturnType.NUMBER)
        public int getSlotCount(TileEntityChest chest) {
            return chest.getSizeInventory();
        }

        @ScriptCallable(returnTypes = ReturnType.OBJECT)
        public ItemStack getStack(TileEntityChest chest, @Arg(name = "slot") int slot) {
            return chest.getStackInSlot(slot);
        }

        @ScriptCallable(returnTypes = ReturnType.STRING)
        public String setStack(TileEntityChest chest, @Arg(name = "slot") int slot,
            @Arg(name = "stack") ItemStack stack) {
            chest.setInventorySlotContents(slot, stack);
            return stack != null ? stack.getDisplayName() : "nil";
        }

        @ScriptCallable(returnTypes = ReturnType.STRING)
        @openperipheral.api.adapter.Asynchronous
        public String asyncPing(TileEntityChest chest) {
            return "pong";
        }
    }

    public static class InlinePropertyObject {

        @openperipheral.api.adapter.Property
        public int value = 7;

        @ScriptCallable(returnTypes = ReturnType.STRING)
        public String label() {
            return "inline";
        }
    }

    private static class FakeComputerAccess implements IComputerAccess {

        final List<String> mounted = new ArrayList<String>();
        final List<String> unmounted = new ArrayList<String>();
        final List<Object[]> events = new ArrayList<Object[]>();

        @Override
        public String mount(String path, IMount mount) {
            mounted.add(path);
            return path;
        }

        @Override
        public String mount(String path, IMount mount, String drive) {
            mounted.add(path);
            return path;
        }

        @Override
        public String mountWritable(String path, IWritableMount mount) {
            mounted.add(path);
            return path;
        }

        @Override
        public String mountWritable(String path, IWritableMount mount, String drive) {
            mounted.add(path);
            return path;
        }

        @Override
        public void unmount(String path) {
            unmounted.add(path);
        }

        @Override
        public int getID() {
            return 1;
        }

        @Override
        public void queueEvent(String event, Object[] args) {
            events.add(new Object[] { event, args });
        }

        @Override
        public String getAttachmentName() {
            return "back";
        }
    }

    private static class FakeLuaContext implements ILuaContext {

        private long nextId = 1;
        private long lastId;
        private final Map<Long, Object[]> taskResults = new HashMap<Long, Object[]>();

        @Override
        public MethodResult executeMainThreadTask(ILuaTask task) throws LuaException {
            return MethodResult.of(task.execute());
        }

        @Override
        public long issueMainThreadTask(ILuaTask task) throws LuaException {
            final long id = nextId++;
            try {
                taskResults.put(id, task.execute());
            } catch (LuaException e) {
                taskResults.put(id, new Object[] { false, e.getMessage() });
            }
            lastId = id;
            return id;
        }

        Object[] complete(Object result) throws LuaException {
            if (!(result instanceof MethodResult)) return (Object[]) result;

            final MethodResult methodResult = (MethodResult) result;
            if (methodResult.isImmediate()) return methodResult.getResults();

            final Object[] taskResult = taskResults.get(lastId);
            if (taskResult == null) throw new AssertionError("No fake task result for id " + lastId);

            final Object[] event = new Object[3 + taskResult.length];
            event[0] = "task_complete";
            event[1] = lastId;
            event[2] = true;
            System.arraycopy(taskResult, 0, event, 3, taskResult.length);

            final MethodResult resumed = methodResult.resumeWith(event);
            if (resumed == null || !resumed.isImmediate()) throw new AssertionError("Task did not resume");
            return resumed.getResults();
        }
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent evt) {
        if (!ENABLED) return;

        final IPeripheralAdapterRegistry registry = ApiAccess.getApi(IPeripheralAdapterRegistry.class);
        check(registry != null, "IPeripheralAdapterRegistry API is missing");
        check(registry.register(new ChestTestAdapter()), "Failed to register test adapter");
    }

    @Mod.EventHandler
    public void clientLoad(FMLLoadCompleteEvent evt) {
        if (!ENABLED) return;
        if (!FMLCommonHandler.instance()
            .getSide()
            .isClient()) return;

        ClientGameTests.run();
    }

    @Mod.EventHandler
    public void serverStarted(FMLServerStartedEvent evt) {
        if (!ENABLED) return;

        final MinecraftServer server = FMLCommonHandler.instance()
            .getMinecraftServerInstance();
        try {
            final World world = runTests(server);

            // The real computer test must not block the server thread: SynchronousExecutor queues work
            // on the server thread, which is exactly what the computer is waiting for.
            final Thread computerTest = new Thread(new Runnable() {

                @Override
                public void run() {
                    try {
                        testRealComputer(world);
                        success();
                    } catch (Throwable t) {
                        failure(t);
                    }
                }
            }, "OpenPeripheral-GameTest-Computer");
            computerTest.setDaemon(true);
            computerTest.start();
        } catch (Throwable t) {
            failure(t);
        }
    }

    private static void success() {
        Log.info("OPENPERIPHERAL_GAMETEST_OK");
        FMLCommonHandler.instance()
            .exitJava(0, false);
    }

    private static void failure(Throwable t) {
        t.printStackTrace();
        Log.severe(t, "OPENPERIPHERAL_GAMETEST_FAILED");
        FMLCommonHandler.instance()
            .exitJava(1, false);
    }

    private static World runTests(MinecraftServer server) throws Exception {
        final World world = server.worldServerForDimension(0);
        check(world != null, "Overworld is missing");
        check(Config.interfaceComputerCraft, "ComputerCraft integration is disabled");
        check(
            ApiAccess.getApi(IArchitectureChecker.class)
                .isEnabled(Constants.ARCH_COMPUTER_CRAFT),
            "ComputerCraft architecture is not enabled");

        final IItemStackMetaBuilder itemMeta = ApiAccess.getApi(IItemStackMetaBuilder.class);
        check(itemMeta != null, "IItemStackMetaBuilder API is missing");
        check(ApiAccess.getApi(IEntityMetaBuilder.class) != null, "IEntityMetaBuilder API is missing");
        check(ApiAccess.getApi(IPeripheralBlacklist.class) != null, "IPeripheralBlacklist API is missing");
        check(ApiAccess.getApi(IFeatureGroupManager.class) != null, "IFeatureGroupManager API is missing");
        check(ApiAccess.getApi(IConverterManager.class) != null, "IConverterManager API is missing");
        check(ApiAccess.getApi(IPeripheralTypeProvider.class) != null, "IPeripheralTypeProvider API is missing");

        testProviderAndAdapter(world);
        testBlockComputerDiscovery(world);
        testInlinePropertyObject();
        testRealItemAndEntityMetadata(world, itemMeta);
        testMetadataProxies(world, itemMeta, ApiAccess.getApi(IEntityMetaBuilder.class));
        testFluidConverters();
        testBlacklist(world);
        testCommandDump(server);
        return world;
    }

    private static void testProviderAndAdapter(World world) throws Exception {
        final int x = 0;
        final int y = 100;
        final int z = 0;

        world.setBlock(x, y, z, Blocks.chest, 0, 3);
        final TileEntity te = world.getTileEntity(x, y, z);
        check(te instanceof TileEntityChest, "Chest TE was not created: " + te);

        final TileEntityChest chest = (TileEntityChest) te;
        chest.setInventorySlotContents(0, new ItemStack(Items.diamond, 3));

        final IPeripheral peripheral = new PeripheralProvider().getPeripheral(world, x, y, z, 2);
        check(peripheral != null, "PeripheralProvider returned null for a chest with the test adapter");
        check("chest".equals(peripheral.getType()), "Unexpected peripheral type: " + peripheral.getType());
        check(indexOf(peripheral.getMethodNames(), "getSlotCount") >= 0, "getSlotCount is missing");
        check(indexOf(peripheral.getMethodNames(), "getStack") >= 0, "getStack is missing");
        check(indexOf(peripheral.getMethodNames(), "setStack") >= 0, "setStack is missing");
        check(indexOf(peripheral.getMethodNames(), "asyncPing") >= 0, "asyncPing is missing");
        realComputerPeripheral = peripheral;

        final FakeComputerAccess access = new FakeComputerAccess();
        final FakeLuaContext context = new FakeLuaContext();
        peripheral.attach(access);
        check(access.mounted.contains("openp"), "Peripheral docs mount was not created");

        final Object[] count = call(peripheral, access, context, "getSlotCount");
        check(((Number) count[0]).intValue() == 27, "Unexpected chest slot count: " + count[0]);

        final Object[] stack = call(peripheral, access, context, "getStack", 0.0);
        check(stack[0] instanceof Map, "getStack did not return a converted table: " + stack[0]);
        final Map<?, ?> stackMap = (Map<?, ?>) stack[0];
        check("minecraft:diamond".equals(stackMap.get("id")), "Unexpected item id: " + stackMap.get("id"));
        check(((Number) stackMap.get("qty")).intValue() == 3, "Unexpected item qty: " + stackMap.get("qty"));

        final Map<String, Object> newNode = new java.util.HashMap<String, Object>();
        newNode.put("id", "minecraft:diamond");
        newNode.put("qty", 5.0);
        call(peripheral, access, context, "setStack", 1.0, newNode);
        final ItemStack updated = chest.getStackInSlot(1);
        check(updated != null && updated.getItem() == Items.diamond, "setStack did not set the chest slot");
        check(updated.stackSize == 5, "setStack used the wrong stack size: " + updated.stackSize);

        final Object[] pong = call(peripheral, access, context, "asyncPing");
        check("pong".equals(pong[0]), "Asynchronous call returned " + pong[0]);

        peripheral.detach(access);
    }

    private static void testBlockComputerDiscovery(World world) {
        final int x = 1;
        final int y = 100;
        final int z = 0;

        world.setBlock(x, y, z, ComputerCraft.Blocks.computer, 0, 3);
        final TileEntity tile = world.getTileEntity(x, y, z);
        check(tile instanceof TileComputer, "Computer TE was not created: " + tile);

        final TileComputer computerTile = (TileComputer) tile;
        computerTile.updateEntity();
        computerTile.updateInput();

        final ServerComputer computer = computerTile.getServerComputer();
        check(computer != null, "Computer did not create a ServerComputer");

        boolean found = false;
        for (int side = 0; side < 6; side++) {
            if (computer.getPeripheral(side) != null) {
                found = true;
                break;
            }
        }
        check(found, "Computer block did not discover the adjacent chest peripheral");
        Log.info("GAMETEST_BLOCK_COMPUTER_OK");
    }

    private static void testInlinePropertyObject() throws Exception {
        final InlinePropertyObject target = new InlinePropertyObject();
        final IPeripheral peripheral = PeripheralProvider.createAdaptedPeripheralWrapped(target);
        check(peripheral != null, "Inline property object did not produce a peripheral");

        final FakeComputerAccess access = new FakeComputerAccess();
        final FakeLuaContext context = new FakeLuaContext();

        final Object[] initial = call(peripheral, access, context, "getValue");
        check(((Number) initial[0]).intValue() == 7, "Initial property value was wrong: " + initial[0]);

        call(peripheral, access, context, "setValue", 42.0);
        check(target.value == 42, "Property setter did not update the object");

        final Object[] label = call(peripheral, access, context, "label");
        check("inline".equals(label[0]), "Inline method returned " + label[0]);
    }

    private static void testRealItemAndEntityMetadata(World world, IItemStackMetaBuilder itemMeta) {
        final ItemStack stack = new ItemStack(Items.diamond, 3, 0);
        final Map<String, Object> metadata = itemMeta.getItemStackMetadata(stack);
        check(metadata != null, "ItemStack metadata is null");
        check("minecraft:diamond".equals(metadata.get("id")), "Unexpected item metadata id: " + metadata.get("id"));
        check(((Number) metadata.get("qty")).intValue() == 3, "Unexpected item metadata qty: " + metadata.get("qty"));

        final EntityItem entity = new EntityItem(world, 0.5, 101, 0.5, stack.copy());
        check(world.spawnEntityInWorld(entity), "Could not spawn test entity");
        final Map<String, Object> entityMeta = ApiAccess.getApi(IEntityMetaBuilder.class)
            .getEntityMetadata(entity, null);
        check(entityMeta != null, "Entity metadata is null");
        check(entityMeta.get("id") != null, "Entity metadata is missing id");
        check(entityMeta.get("name") != null, "Entity metadata is missing name");
    }

    private static void testMetadataProxies(World world, IItemStackMetaBuilder itemBuilder,
        IEntityMetaBuilder entityBuilder) {
        final ItemStack stack = new ItemStack(Items.diamond, 4);
        final IItemStackPartialMetaBuilder itemPartial = (IItemStackPartialMetaBuilder) itemBuilder;

        check(
            itemPartial.getBasicItemStackMetadata(stack)
                .get("id") != null,
            "Basic item metadata is missing id");
        check(itemPartial.getKeys(stack) != null, "Item provider keys are null");

        final IMetaProviderProxy itemProxy = itemPartial.createProxy(stack);
        check(itemProxy != null, "Item metadata proxy is null");
        check(
            itemProxy.basic()
                .get("id") != null,
            "Item proxy basic metadata is missing id");
        check(
            itemProxy.all()
                .get("qty") != null,
            "Item proxy all metadata is missing qty");
        check(itemProxy.keys() != null, "Item proxy keys are null");
        check(
            itemProxy.select("id")
                .get("id") != null,
            "Item proxy select lookup failed");

        itemBuilder.register(new IItemStackMetaProvider<Item>() {

            @Override
            public Class<? extends Item> getTargetClass() {
                return Item.class;
            }

            @Override
            public String getKey() {
                return "gametest_item_meta";
            }

            @Override
            public Object getMeta(Item target, ItemStack stack) {
                return "item-meta";
            }
        });
        check(
            "item-meta".equals(
                itemBuilder.getItemStackMetadata(stack)
                    .get("gametest_item_meta")),
            "Registered item provider was not used");
        check(
            itemPartial.getKeys(stack)
                .contains("gametest_item_meta"),
            "Registered item provider key is missing");

        final IMetaProviderProxy itemProxyWithCustom = itemPartial.createProxy(stack);
        check(
            "item-meta".equals(itemProxyWithCustom.single("gametest_item_meta")),
            "Item proxy did not expose the registered provider");
        check(
            itemProxyWithCustom.keys()
                .contains("gametest_item_meta"),
            "Item proxy keys did not include the registered provider");

        final IEntityPartialMetaBuilder entityPartial = (IEntityPartialMetaBuilder) entityBuilder;
        final EntityItem entity = new EntityItem(world, 0.5, 101, 0.5, stack.copy());
        check(world.spawnEntityInWorld(entity), "Could not spawn metadata proxy entity");

        check(
            entityPartial.getBasicEntityMetadata(entity, null)
                .get("id") != null,
            "Basic entity metadata is missing id");
        final IMetaProviderProxy entityProxy = entityPartial.createProxy(entity, null);
        check(entityProxy != null, "Entity metadata proxy is null");
        check(
            entityProxy.basic()
                .get("id") != null,
            "Entity proxy basic metadata is missing id");
        check(
            entityProxy.all()
                .get("name") != null,
            "Entity proxy all metadata is missing name");
        check(entityProxy.keys() != null, "Entity proxy keys is null");
        entityProxy.select("name");

        entityBuilder.register(new IEntityMetaProvider<Entity>() {

            @Override
            public Class<? extends Entity> getTargetClass() {
                return Entity.class;
            }

            @Override
            public String getKey() {
                return "gametest_entity_meta";
            }

            @Override
            public Object getMeta(Entity target, net.minecraft.util.Vec3 relativePos) {
                return "entity-meta";
            }
        });
        check(
            "entity-meta".equals(
                entityBuilder.getEntityMetadata(entity, null)
                    .get("gametest_entity_meta")),
            "Registered entity provider was not used");
        Log.info("GAMETEST_METADATA_PROXY_OK");
    }

    private static void testFluidConverters() {
        final IConverter converter = TypeConvertersProvider.INSTANCE.getConverter(Constants.ARCH_COMPUTER_CRAFT);
        final FluidStack fluid = new FluidStack(FluidRegistry.WATER, 1000);

        final Map<?, ?> fluidMap = (Map<?, ?>) converter.fromJava(fluid);
        check(fluidMap != null, "FluidStack conversion returned null");
        check(((Number) fluidMap.get("amount")).intValue() == 1000, "Unexpected fluid amount");
        check("water".equals(fluidMap.get("name")), "Unexpected fluid name: " + fluidMap.get("name"));

        final FluidTankInfo tank = new FluidTankInfo(fluid, 4000);
        final Map<?, ?> tankMap = (Map<?, ?>) converter.fromJava(tank);
        check(tankMap != null, "FluidTankInfo conversion returned null");
        check(((Number) tankMap.get("capacity")).intValue() == 4000, "Unexpected tank capacity");
        check(tankMap.get("contents") instanceof Map, "Tank contents were not converted to a table");
        Log.info("GAMETEST_FLUID_OK");
    }

    private static void testBlacklist(World world) {
        final int x = 3;
        final int y = 100;
        final int z = 0;

        world.setBlock(x, y, z, Blocks.furnace, 0, 3);
        check(world.getTileEntity(x, y, z) instanceof TileEntityFurnace, "Furnace TE was not created");

        TileEntityBlacklist.INSTANCE.addToBlacklist(TileEntityFurnace.class);
        final IPeripheral peripheral = new PeripheralProvider().getPeripheral(world, x, y, z, 2);
        check(peripheral == null, "Blacklisted furnace still produced a peripheral");
    }

    private static void testCommandDump(MinecraftServer server) throws Exception {
        final int result = server.getCommandManager()
            .executeCommand(server, "op_dump xml gametest_dump");
        check(result > 0, "op_dump command did not report success");

        final File dump = new File(openmods.OpenMods.proxy.getMinecraftDir(), "gametest_dump.xml");
        check(dump.isFile() && dump.length() > 0, "op_dump did not create gametest_dump.xml");
    }

    private static void testRealComputer(World world) throws Exception {
        final ServerComputer computer = new ServerComputer(world, 0, "gametest", 0, ComputerFamily.Normal, 51, 19);
        computer.setPosition(0, 100, 0);

        final IPeripheral peripheral = realComputerPeripheral;
        check(peripheral != null, "Real computer test could not find the chest peripheral");
        computer.setPeripheral(2, peripheral);

        final IWritableMount root = computer.getRootMount();
        check(root != null, "Computer root mount is null");
        final String program = "print(\"GAMETEST_HELLO\")\n" + "local p = peripheral.wrap(\"back\")\n"
            + "if not p then print(\"GAMETEST_NO_PERIPHERAL\") return end\n"
            + "print(\"GAMETEST_SLOTS=\" .. tostring(p.getSlotCount()))\n"
            + "local s = p.getStack(0)\n"
            + "print(\"GAMETEST_ID=\" .. tostring(s.id))\n"
            + "print(\"GAMETEST_QTY=\" .. tostring(s.qty))\n"
            + "p.setStack(2, { id = \"minecraft:diamond\", qty = 7 })\n"
            + "local s2 = p.getStack(2)\n"
            + "print(\"GAMETEST_SET=\" .. tostring(s2.qty))\n";
        try {
            try (OutputStream out = root.openForWrite("startup")) {
                out.write(program.getBytes(StandardCharsets.UTF_8));
            }
            try (OutputStream out = root.openForWrite("startup.lua")) {
                out.write(program.getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new AssertionError("Could not write computer startup program", e);
        }
        Log.info("GAMETEST startup exists=%s size=%s", root.exists("startup"), root.getSize("startup"));

        computer.turnOn();

        final long deadline = System.currentTimeMillis() + 20000;
        String terminal = null;
        while (System.currentTimeMillis() < deadline) {
            computer.update();
            Thread.sleep(50);
            terminal = readTerminal(computer);
            if (terminal.contains("GAMETEST_SET=7")) break;
        }

        check(terminal != null, "Computer terminal was not readable");
        check(terminal.contains("GAMETEST_SLOTS=27"), "Lua did not read the chest size. Terminal:\n" + terminal);
        check(
            terminal.contains("GAMETEST_ID=minecraft:diamond"),
            "Lua did not read the item id. Terminal:\n" + terminal);
        check(terminal.contains("GAMETEST_QTY=3"), "Lua did not read the item qty. Terminal:\n" + terminal);
        check(terminal.contains("GAMETEST_SET=7"), "Lua did not write the item back. Terminal:\n" + terminal);

        Log.info("GAMETEST_ASSERTIONS_OK");
        computer.shutdown();
    }

    private static String readTerminal(ServerComputer computer) {
        final StringBuilder result = new StringBuilder();
        for (int i = 0; i < computer.getTerminal()
            .getHeight(); i++) {
            final dan200.computercraft.core.terminal.TextBuffer line = computer.getTerminal()
                .getLine(i);
            if (line != null) result.append(line.toString())
                .append('\n');
        }
        return result.toString();
    }

    private static Object[] call(IPeripheral peripheral, FakeComputerAccess access, FakeLuaContext context,
        String methodName, Object... args) throws Exception {
        final int index = indexOf(peripheral.getMethodNames(), methodName);
        check(index >= 0, "Method not found: " + methodName);
        final Object result = peripheral.callMethodResult(access, context, index, args);
        return context.complete(result);
    }

    private static int indexOf(String[] names, String name) {
        for (int i = 0; i < names.length; i++) if (name.equals(names[i])) return i;
        return -1;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
