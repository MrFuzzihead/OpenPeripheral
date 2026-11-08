package openperipheral.tests;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import com.google.common.collect.ImmutableSet;

import openperipheral.interfaces.oc.providers.EnvironmentMethodsFactory;

public class EnvironmentMethodsFactoryTest {

    public static class Target implements Runnable {

        @Override
        public void run() {}
    }

    @SuppressWarnings("unchecked")
    private static Set<Class<?>> getInterfaces(Class<?> target, Class<?>... interfaces) throws Exception {
        final Method method = EnvironmentMethodsFactory.class
            .getDeclaredMethod("getInterfaces", Class.class, Class[].class);
        method.setAccessible(true);
        return (Set<Class<?>>) method.invoke(null, target, interfaces);
    }

    @Test
    public void acceptsImplementedInterface() throws Exception {
        Assert.assertEquals(ImmutableSet.<Class<?>>of(Runnable.class), getInterfaces(Target.class, Runnable.class));
    }

    @Test
    public void rejectsUnimplementedInterfaceWithDescriptiveMessage() throws Exception {
        try {
            getInterfaces(Target.class, Comparable.class);
            Assert.fail("Expected rejection");
        } catch (InvocationTargetException e) {
            Assert.assertTrue(e.getCause() instanceof IllegalArgumentException);
            final String message = e.getCause()
                .getMessage();
            Assert.assertTrue(message.contains(Target.class.getName()));
            Assert.assertTrue(message.contains(Comparable.class.getName()));
            Assert.assertFalse(message.contains("%s"));
        }
    }
}
