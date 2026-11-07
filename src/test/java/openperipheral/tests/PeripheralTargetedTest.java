package openperipheral.tests;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Set;

import net.minecraft.inventory.IInventory;

import org.junit.Assert;
import org.junit.Test;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import dan200.computercraft.api.peripheral.IPeripheralTargeted;
import openperipheral.adapter.IMethodExecutor;
import openperipheral.adapter.composed.IndexedMethodMap;
import openperipheral.api.peripheral.PeripheralTypeId;
import openperipheral.interfaces.cc.providers.PeripheralProvider;
import openperipheral.interfaces.cc.wrappers.AdapterPeripheral;
import openperipheral.interfaces.cc.wrappers.ProxyAdapterPeripheral;

public class PeripheralTargetedTest {

    /** Annotated so {@code PeripheralTypeProvider} does not fall back to name lookups that need a running game */
    @PeripheralTypeId("test_target")
    public static class TestTarget {
    }

    private static final Map<String, IMethodExecutor> NO_METHODS = ImmutableMap.of();

    private static AdapterPeripheral createPeripheral(Object target) {
        return new AdapterPeripheral(new IndexedMethodMap(NO_METHODS), target);
    }

    private static ProxyAdapterPeripheral createHandler(Object target) {
        return new ProxyAdapterPeripheral(new IndexedMethodMap(NO_METHODS), target);
    }

    private static Object invokeProxyMethod(ProxyAdapterPeripheral handler, Class<?> declaringClass, String name) {
        try {
            final Method method = declaringClass.getMethod(name);
            return ((InvocationHandler) handler).invoke(null, method, null);
        } catch (Throwable t) {
            throw Throwables.propagate(t);
        }
    }

    @Test
    public void adapterPeripheralExposesItsTarget() {
        final TestTarget target = new TestTarget();
        final AdapterPeripheral peripheral = createPeripheral(target);

        Assert.assertTrue(
            "AdapterPeripheral must satisfy CC's IPeripheralTargeted contract",
            peripheral instanceof IPeripheralTargeted);
        Assert.assertSame(target, ((IPeripheralTargeted) peripheral).getTarget());
    }

    @Test
    public void proxyAdapterPeripheralServesTargetedInterface() {
        final TestTarget target = new TestTarget();
        final ProxyAdapterPeripheral handler = createHandler(target);

        Assert.assertSame(target, invokeProxyMethod(handler, IPeripheralTargeted.class, "getTarget"));
    }

    @Test
    public void proxyAdapterPeripheralStillForwardsToTarget() {
        final StringBuilder calls = new StringBuilder();
        final IInventory target = (IInventory) Proxy.newProxyInstance(
            PeripheralTargetedTest.class.getClassLoader(),
            new Class<?>[] { IInventory.class },
            (proxy, method, args) -> {
                if ("getInventoryName".equals(method.getName())) {
                    calls.append(method.getName());
                    return "fake";
                }
                return null;
            });

        final ProxyAdapterPeripheral handler = createHandler(target);

        // peripheral type generation already asked the target for its inventory name
        calls.setLength(0);

        Assert.assertEquals("fake", invokeProxyMethod(handler, IInventory.class, "getInventoryName"));
        Assert.assertEquals("getInventoryName", calls.toString());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void generatedPeripheralsGetTargetedInterface() {
        try {
            final Method method = PeripheralProvider.class.getDeclaredMethod("appendCommonInterfaces", Set.class);
            method.setAccessible(true);

            final Set<Class<?>> interfaces = (Set<Class<?>>) method.invoke(null, ImmutableSet.of(IInventory.class));

            Assert.assertTrue(interfaces.contains(IPeripheralTargeted.class));
            Assert.assertTrue(interfaces.contains(IInventory.class));
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }
}
