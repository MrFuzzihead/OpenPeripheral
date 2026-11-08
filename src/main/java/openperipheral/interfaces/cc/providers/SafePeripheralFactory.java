package openperipheral.interfaces.cc.providers;

import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.tileentity.TileEntity;

import org.apache.commons.lang3.ArrayUtils;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import openmods.Log;
import openperipheral.adapter.composed.ComposedMethodsFactory.InvalidClassException;
import openperipheral.api.peripheral.IBrokenOpenPeripheral;

abstract class SafePeripheralFactory implements IPeripheralFactory<TileEntity> {

    private static final Random RANDOM = new Random();

    private static final String[] BOGUS_METHODS = new String[] { "help", "whats_going_on", "wtf", "lol_nope", "derp",
        "guru_meditation", "woof", "nothing_to_see_here", "kernel_panic", "hello_segfault", "i_see_dead_bytes", "xyzzy",
        "abort_retry_fail_continue" };

    private static class BrokenPeripheral implements IPeripheral, IBrokenOpenPeripheral {

        // Method names must be stable: ComputerCraft maps indices from getMethodNames() to callMethod().
        private final String[] methodNames;

        public BrokenPeripheral() {
            methodNames = ArrayUtils.toArray(BOGUS_METHODS[RANDOM.nextInt(BOGUS_METHODS.length)]);
        }

        @Override
        public String getType() {
            return "broken_peripheral";
        }

        @Override
        public String[] getMethodNames() {
            return methodNames;
        }

        @Override
        public void detach(IComputerAccess computer) {}

        @Override
        public Object[] callMethod(IComputerAccess computer, ILuaContext context, int method, Object[] arguments) {
            return ArrayUtils.toArray("This peripheral is broken. You can show your log in #OpenMods");
        }

        @Override
        public void attach(IComputerAccess computer) {}

        @Override
        public boolean equals(IPeripheral other) {
            return other == this;
        }
    }

    private static final IPeripheral PLACEHOLDER = new BrokenPeripheral();

    private static final Set<Class<?>> LOGGED_CLASSES = ConcurrentHashMap.newKeySet();

    public static final IPeripheralFactory<TileEntity> BROKEN_FACTORY = new IPeripheralFactory<TileEntity>() {

        @Override
        public IPeripheral getPeripheral(TileEntity obj, int side) {
            return PLACEHOLDER;
        }
    };

    private static String dimension(TileEntity tile) {
        if (tile.getWorldObj() == null || tile.getWorldObj().provider == null) return "?";
        return Integer.toString(tile.getWorldObj().provider.dimensionId);
    }

    @Override
    public IPeripheral getPeripheral(TileEntity tile, int side) {
        if (tile == null) return null;

        try {
            return createPeripheral(tile, side);
        } catch (InvalidClassException e) {
            // A broken TE class is queried every time a computer scans its neighbourhood. Log once per class
            // so a single bad mod cannot flood the log every tick.
            if (LOGGED_CLASSES.add(tile.getClass())) {
                Throwable cause = e.getCause();
                if (cause != null) {
                    Log.severe(
                        cause,
                        "Can't create peripheral for TE %s @ (%d,%d,%d) in world %s due to error in class",
                        tile.getClass(),
                        tile.xCoord,
                        tile.yCoord,
                        tile.zCoord,
                        dimension(tile));
                } else {
                    Log.severe(
                        "Can't create peripheral for TE %s @ (%d,%d,%d) in world %s due to error in class %s",
                        tile.getClass(),
                        tile.xCoord,
                        tile.yCoord,
                        tile.zCoord,
                        dimension(tile),
                        tile.getClass());
                }
            }
        } catch (Throwable t) {
            if (LOGGED_CLASSES.add(tile.getClass())) {
                Log.severe(
                    t,
                    "Can't create peripheral for TE %s @ (%d,%d,%d) in world %s",
                    tile.getClass(),
                    tile.xCoord,
                    tile.yCoord,
                    tile.zCoord,
                    dimension(tile));
            }
        }
        return PLACEHOLDER;
    }

    protected abstract IPeripheral createPeripheral(TileEntity tile, int side) throws Exception;

}
