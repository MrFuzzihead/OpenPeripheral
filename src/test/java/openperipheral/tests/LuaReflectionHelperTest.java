package openperipheral.tests;

import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import openperipheral.Config;
import openperipheral.adapter.AdapterRegistry;
import openperipheral.adapter.IMethodCall;
import openperipheral.adapter.IMethodExecutor;
import openperipheral.adapter.composed.ClassMethodsComposer;
import openperipheral.adapter.composed.MethodSelector;
import openperipheral.api.Constants;
import openperipheral.interfaces.cc.TypeConversionRegistryCC;

public class LuaReflectionHelperTest {

    public static class ReflectedTarget {

        public String value() {
            return "value";
        }
    }

    @Test
    public void devMethodsExposeReflectionMetadata() throws Exception {
        final boolean previous = Config.devMethods;
        try {
            Config.devMethods = true;
            final ClassMethodsComposer composer = new ClassMethodsComposer(
                new MethodSelector(Constants.ARCH_COMPUTER_CRAFT).addDefaultEnv());
            final Map<String, IMethodExecutor> methods = composer
                .createMethodsList(ReflectedTarget.class, new AdapterRegistry());

            Assert.assertTrue(methods.containsKey("getClass"));
            Assert.assertTrue(methods.containsKey("getSuperclass"));
            Assert.assertTrue(methods.containsKey("getInterfaces"));
            Assert.assertTrue(methods.containsKey("getMethods"));
            Assert.assertTrue(methods.containsKey("getDeclaredMethods"));

            final ReflectedTarget target = new ReflectedTarget();

            final Object[] cls = call(methods, "getClass", target);
            Assert.assertTrue(
                String.valueOf(cls[0])
                    .contains("ReflectedTarget"));

            final Object[] superclass = call(methods, "getSuperclass", target);
            Assert.assertTrue(
                String.valueOf(superclass[0])
                    .contains("Object"));

            final Object[] interfaces = call(methods, "getInterfaces", target);
            Assert.assertTrue(interfaces[0] instanceof Map);

            final Object[] declared = call(methods, "getDeclaredMethods", target);
            Assert.assertTrue(declared[0] instanceof Map);
            Assert.assertTrue(((Map<?, ?>) declared[0]).containsKey("value"));
        } finally {
            Config.devMethods = previous;
        }
    }

    private static Object[] call(Map<String, IMethodExecutor> methods, String name, Object target) throws Exception {
        final IMethodExecutor executor = methods.get(name);
        Assert.assertNotNull("Missing method " + name, executor);
        final IMethodCall call = executor.startCall(target)
            .setEnv(Constants.ARG_CONVERTER, new TypeConversionRegistryCC());
        return call.call();
    }
}
