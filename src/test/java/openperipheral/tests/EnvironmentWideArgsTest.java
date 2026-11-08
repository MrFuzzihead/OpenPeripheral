package openperipheral.tests;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Collections;

import org.junit.Test;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSet;

import openperipheral.adapter.composed.IndexedMethodMap;
import openperipheral.interfaces.oc.asm.ICodeGenerator;
import openperipheral.interfaces.oc.asm.MethodsStore;
import openperipheral.interfaces.oc.asm.object.ObjectCodeGenerator;

public class EnvironmentWideArgsTest {

    private static Method defineClass;

    static {
        try {
            defineClass = ClassLoader.class
                .getDeclaredMethod("defineClass", String.class, byte[].class, int.class, int.class);
            defineClass.setAccessible(true);
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    public static interface WideInterface {

        public long mix(long a, double b, int c);
    }

    public abstract static class WideTarget implements WideInterface {
    }

    @Test
    public void exposedInterfaceForwardsWidePrimitiveArguments() throws Exception {
        final IndexedMethodMap methods = new IndexedMethodMap(Collections.emptyMap());
        final int methodsId = MethodsStore.drop(methods.getMethods());

        final ICodeGenerator generator = new ObjectCodeGenerator();
        final byte[] bytes = generator.generate(
            "WideArgsTestClass",
            WideTarget.class,
            ImmutableSet.<Class<?>>of(WideInterface.class),
            methods,
            methodsId);

        @SuppressWarnings("unchecked")
        final Class<? extends WideTarget> cls = (Class<? extends WideTarget>) defineClass
            .invoke(getClass().getClassLoader(), "WideArgsTestClass", bytes, 0, bytes.length);

        final WideTarget target = mock(WideTarget.class);
        when(target.mix(1234567890123L, 2.5, 7)).thenReturn(99L);

        final WideInterface wrapper = (WideInterface) cls.getConstructor(WideTarget.class)
            .newInstance(target);

        assertEquals(99L, wrapper.mix(1234567890123L, 2.5, 7));
        verify(target).mix(1234567890123L, 2.5, 7);
    }
}
