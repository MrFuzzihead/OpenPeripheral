package openperipheral.interfaces.cc.providers;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import net.minecraft.tileentity.TileEntity;

import org.junit.Test;

import dan200.computercraft.api.peripheral.IPeripheral;

public class SafePeripheralFactoryTest {

    private static class ThrowingFactory extends SafePeripheralFactory {

        @Override
        protected IPeripheral createPeripheral(TileEntity tile, int side) throws Exception {
            throw new IllegalStateException("intentional");
        }
    }

    private static class NullTileFactory extends SafePeripheralFactory {

        @Override
        protected IPeripheral createPeripheral(TileEntity tile, int side) {
            throw new AssertionError("should not be called");
        }
    }

    @Test
    public void nullTileReturnsNull() {
        assertNull(new NullTileFactory().getPeripheral(null, 0));
    }

    @Test
    public void failingFactoryReturnsStableBrokenPeripheral() throws Exception {
        final SafePeripheralFactory factory = new ThrowingFactory();
        final TileEntity tile = new TileEntity() {};

        final IPeripheral peripheral = factory.getPeripheral(tile, 0);
        assertNotNull(peripheral);
        assertEquals("broken_peripheral", peripheral.getType());
        assertArrayEquals(peripheral.getMethodNames(), peripheral.getMethodNames());

        final Object[] result = peripheral.callMethod(null, null, 0, new Object[0]);
        assertEquals(1, result.length);
        assertEquals("This peripheral is broken. You can show your log in #OpenMods", result[0]);
    }

    @Test
    public void brokenFactoryAlwaysReturnsPlaceholder() {
        final IPeripheral peripheral = SafePeripheralFactory.BROKEN_FACTORY.getPeripheral(null, 0);
        assertNotNull(peripheral);
        assertEquals("broken_peripheral", peripheral.getType());
        assertArrayEquals(peripheral.getMethodNames(), peripheral.getMethodNames());
    }
}
