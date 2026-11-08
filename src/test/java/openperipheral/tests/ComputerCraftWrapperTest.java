package openperipheral.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.Collections;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.ILuaObject;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IPeripheral;
import openperipheral.adapter.IMethodExecutor;
import openperipheral.adapter.composed.IndexedMethodMap;
import openperipheral.api.adapter.Asynchronous;
import openperipheral.api.adapter.method.Arg;
import openperipheral.api.adapter.method.ReturnType;
import openperipheral.api.adapter.method.ScriptCallable;
import openperipheral.api.peripheral.ExposeInterface;
import openperipheral.interfaces.cc.ModuleComputerCraft;
import openperipheral.interfaces.cc.providers.PeripheralProvider;
import openperipheral.interfaces.cc.wrappers.AdapterPeripheral;
import openperipheral.interfaces.cc.wrappers.LuaObjectWrapper;

public class ComputerCraftWrapperTest {

    @BeforeClass
    public static void initComputerCraft() {
        ModuleComputerCraft.init();
    }

    @Asynchronous
    public static class WrappedObject {

        @ScriptCallable(name = "echo", returnTypes = ReturnType.STRING)
        public String echo(@Arg(name = "value") String value) {
            return value;
        }

        @ScriptCallable(name = "fail", returnTypes = ReturnType.STRING)
        public String fail() {
            throw new IllegalStateException("boom");
        }
    }

    public static class EmptyObject {
    }

    @ExposeInterface({ Runnable.class })
    @Asynchronous
    public static class ExposedObject implements Runnable {

        public boolean ran;

        @Override
        public void run() {
            ran = true;
        }

        @ScriptCallable(name = "ping", returnTypes = ReturnType.STRING)
        public String ping() {
            return "pong";
        }
    }

    private static int indexOf(String[] names, String name) {
        for (int i = 0; i < names.length; i++) if (name.equals(names[i])) return i;
        throw new AssertionError("Method not found: " + name);
    }

    @Test
    public void luaObjectWrapperExposesInlineMethods() throws Exception {
        final ILuaObject object = LuaObjectWrapper.wrap(new WrappedObject());
        assertNotNull(object);

        final String[] names = object.getMethodNames();
        Assert.assertTrue(names.length >= 2);

        final Object[] result = object
            .callMethod(mock(ILuaContext.class), indexOf(names, "echo"), new Object[] { "x" });
        assertEquals(1, result.length);
        assertEquals("x", result[0]);

        final Object result2 = object
            .callMethodResult(mock(ILuaContext.class), indexOf(names, "echo"), new Object[] { "y" });
        Assert.assertArrayEquals(new Object[] { "y" }, (Object[]) result2);
    }

    @Test
    public void luaObjectWrapperConvertsFailures() throws Exception {
        final ILuaObject object = LuaObjectWrapper.wrap(new WrappedObject());
        final int index = indexOf(object.getMethodNames(), "fail");
        try {
            object.callMethod(mock(ILuaContext.class), index, new Object[0]);
            Assert.fail("Expected LuaException");
        } catch (LuaException expected) {
            Assert.assertTrue(
                expected.getMessage()
                    .contains("boom"));
        }
    }

    @Test
    public void luaObjectWrapperRejectsEmptyAndNullTargets() {
        assertNull(LuaObjectWrapper.wrap(new EmptyObject()));
        try {
            LuaObjectWrapper.wrap(null);
            Assert.fail("Expected null rejection");
        } catch (NullPointerException expected) {
            // expected
        }
    }

    @Test
    public void peripheralProviderWrapsInlineMethods() {
        final IPeripheral peripheral = PeripheralProvider.createAdaptedPeripheralWrapped(new WrappedObject());
        assertNotNull(peripheral);
        assertTrue(peripheral instanceof AdapterPeripheral);
        Assert.assertTrue(
            peripheral.getType()
                .contains("wrapped"));
        assertTrue(peripheral.getMethodNames().length >= 2);
    }

    @Test
    public void peripheralProviderReturnsNullForUnsupportedObject() {
        assertNull(PeripheralProvider.createAdaptedPeripheralWrapped(new EmptyObject()));
    }

    @Test
    public void peripheralProviderCreatesProxyForExposedInterfaces() {
        final ExposedObject target = new ExposedObject();
        final IPeripheral peripheral = PeripheralProvider.createAdaptedPeripheralWrapped(target);

        assertNotNull(peripheral);
        Assert.assertTrue(java.lang.reflect.Proxy.isProxyClass(peripheral.getClass()));
        assertTrue(peripheral instanceof Runnable);

        ((Runnable) peripheral).run();
        assertTrue(target.ran);

        Assert.assertTrue(
            peripheral.getType()
                .contains("exposed"));
    }

    @Test
    public void indexedMethodMapRejectsOutOfBoundsIndices() {
        final IndexedMethodMap empty = new IndexedMethodMap(Collections.<String, IMethodExecutor>emptyMap());
        assertTrue(empty.isEmpty());
        assertEquals(0, empty.size());
        assertNull(empty.getMethod(0));
        assertNull(empty.getMethodName(0));
        assertNull(empty.getMethod(-1));
        assertNull(empty.getMethodName(-1));

        final int[] visits = { 0 };
        empty.visitMethods(new openperipheral.adapter.composed.IMethodMap.IMethodVisitor() {

            @Override
            public void visit(String name, IMethodExecutor executor) {
                visits[0]++;
            }
        });
        assertEquals(0, visits[0]);
    }
}
