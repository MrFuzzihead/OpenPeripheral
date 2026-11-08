package openperipheral.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Test;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import dan200.computercraft.api.lua.ILuaContext;
import openperipheral.ArchitectureChecker;
import openperipheral.adapter.AdapterRegistry;
import openperipheral.adapter.AnnotationMetaExtractor;
import openperipheral.adapter.FeatureGroupManager;
import openperipheral.adapter.IMethodExecutor;
import openperipheral.adapter.composed.ClassMethodsComposer;
import openperipheral.adapter.composed.ComposedMethodsFactory;
import openperipheral.adapter.composed.IndexedMethodMap;
import openperipheral.adapter.composed.MethodSelector;
import openperipheral.adapter.composed.NamedMethodMap;
import openperipheral.adapter.wrappers.AdapterWrapper;
import openperipheral.api.Constants;
import openperipheral.api.adapter.Asynchronous;
import openperipheral.api.adapter.IAdapterWithConstraints;
import openperipheral.api.adapter.IPeripheralAdapter;
import openperipheral.api.adapter.ReturnSignal;
import openperipheral.api.adapter.method.Arg;
import openperipheral.api.adapter.method.ReturnType;
import openperipheral.api.adapter.method.ScriptCallable;
import openperipheral.api.architecture.ExcludeArchitecture;
import openperipheral.api.architecture.FeatureGroup;

public class AdapterFrameworkTest {

    public static class TestTarget {
    }

    public static class OtherTarget {
    }

    public static class BaseAdapter implements IPeripheralAdapter {

        @Override
        public String getSourceId() {
            return "base";
        }

        @Override
        public Class<?> getTargetClass() {
            return TestTarget.class;
        }

        @ScriptCallable(returnTypes = ReturnType.STRING)
        public String inherited(TestTarget target, @Arg(name = "value") int value) {
            return "inherited:" + value;
        }
    }

    public static class ChildAdapter extends BaseAdapter {

        @ScriptCallable(returnTypes = ReturnType.STRING)
        public String child(TestTarget target, @Arg(name = "value") int value) {
            return "child:" + value;
        }
    }

    public static class ConstrainedAdapter implements IPeripheralAdapter, IAdapterWithConstraints {

        private final Class<?> allowed;

        public ConstrainedAdapter(Class<?> allowed) {
            this.allowed = allowed;
        }

        @Override
        public String getSourceId() {
            return "constrained";
        }

        @Override
        public Class<?> getTargetClass() {
            return TestTarget.class;
        }

        @Override
        public boolean canApply(Class<?> target) {
            return allowed.isAssignableFrom(target);
        }

        @ScriptCallable
        public void ping(TestTarget target) {}
    }

    public static class InlineTarget {

        @ScriptCallable(name = "zeta", returnTypes = ReturnType.STRING)
        public String zeta() {
            return "zeta";
        }

        @ScriptCallable(name = "alpha", returnTypes = ReturnType.STRING)
        public String alpha() {
            return "alpha";
        }

        @ScriptCallable(name = "excluded", returnTypes = ReturnType.STRING)
        @ExcludeArchitecture(Constants.ARCH_COMPUTER_CRAFT)
        public String excluded() {
            return "excluded";
        }
    }

    public static class InvalidTarget {

        @ScriptCallable
        public void bad(String noAnnotation) {}
    }

    private static MethodSelector selector(String architecture) {
        return new MethodSelector(architecture).addDefaultEnv();
    }

    @Test
    public void architectureChecker() {
        final ArchitectureChecker checker = new ArchitectureChecker();
        checker.register("A", new ArchitectureChecker.IArchitecturePredicate() {

            @Override
            public boolean isEnabled() {
                return true;
            }
        });
        checker.register("B", new ArchitectureChecker.IArchitecturePredicate() {

            @Override
            public boolean isEnabled() {
                return false;
            }
        });

        assertTrue(checker.isEnabled("A"));
        assertFalse(checker.isEnabled("B"));
        assertFalse(checker.isEnabled("missing"));
        assertEquals(
            2,
            checker.knownArchitectures()
                .size());

        try {
            checker.register("A", new ArchitectureChecker.IArchitecturePredicate() {

                @Override
                public boolean isEnabled() {
                    return false;
                }
            });
            Assert.fail("Duplicate registration must fail");
        } catch (IllegalStateException expected) {
            // expected
        }
    }

    @Test
    public void featureGroupManagerRoundTripAndMalformedEntries() {
        final FeatureGroupManager manager = new FeatureGroupManager();
        manager.ensureExists("group");

        assertTrue(manager.isEnabled("group", "CC"));
        manager.disable("group", "CC");
        assertFalse(manager.isEnabled("group", "CC"));
        assertTrue(manager.isEnabled("group", "OC"));

        final String[] saved = manager.saveBlacklist();
        Assert.assertArrayEquals(new String[] { "group:CC" }, saved);

        manager.enable("group", "CC");
        manager.loadBlacklist(new String[] { "malformed", "group:CC" });
        assertFalse(manager.isEnabled("group", "CC"));
        assertTrue(manager.isEnabled("group", "OC"));

        manager.loadBlacklist(null);
        assertTrue(manager.isEnabled("group", "CC"));

        manager.disable("group", "CC");
        final FeatureGroupManager copy = manager.copy();
        manager.enable("group", "CC");
        assertFalse(copy.isEnabled("group", "CC"));
    }

    @Test
    public void methodSelectorFiltersByEnvironmentAndSignal() {
        final IMethodExecutor executor = mock(IMethodExecutor.class);
        when(executor.canInclude("CC")).thenReturn(true);
        when(executor.getReturnSignal()).thenReturn(Optional.<String>absent());
        when(executor.requiredEnv()).thenReturn(ImmutableMap.<String, Class<?>>of());

        final MethodSelector selector = selector("CC");
        assertTrue(selector.apply(executor));

        when(executor.requiredEnv())
            .thenReturn(ImmutableMap.<String, Class<?>>of(Constants.ARG_CONTEXT, ILuaContext.class));
        assertFalse(selector.apply(executor));
        assertTrue(
            selector.addProvidedEnv(Constants.ARG_CONTEXT, ILuaContext.class)
                .apply(executor));

        when(executor.requiredEnv()).thenReturn(ImmutableMap.<String, Class<?>>of());
        when(executor.getReturnSignal()).thenReturn(Optional.of("signal"));
        assertFalse(selector.apply(executor));
        assertTrue(
            selector.allowReturnSignal()
                .apply(executor));

        when(executor.canInclude("CC")).thenReturn(false);
        assertFalse(selector.apply(executor));
    }

    @Test
    public void externalAdaptersIncludeInheritedMethods() {
        final AdapterRegistry registry = new AdapterRegistry();
        assertTrue(registry.addAdapter(new ChildAdapter()));

        final List<AdapterWrapper> wrappers = Lists.newArrayList(registry.getExternalAdapters(TestTarget.class));
        assertEquals(1, wrappers.size());

        final List<String> names = Lists.newArrayList();
        for (IMethodExecutor method : wrappers.get(0)
            .getMethods())
            names.addAll(
                method.description()
                    .getNames());

        Collections.sort(names);
        assertEquals(ImmutableList.of("child", "inherited"), names);
    }

    @Test
    public void constrainedAdapterIsHonoured() {
        final AdapterRegistry registry = new AdapterRegistry();
        assertTrue(registry.addAdapter(new ConstrainedAdapter(OtherTarget.class)));

        final AdapterWrapper wrapper = registry.getExternalAdapters(TestTarget.class)
            .iterator()
            .next();
        assertFalse(wrapper.canUse(TestTarget.class));
        assertTrue(wrapper.canUse(OtherTarget.class));
    }

    @Test(expected = IllegalArgumentException.class)
    public void adapterForObjectIsRejected() {
        final AdapterRegistry registry = new AdapterRegistry();
        registry.addAdapter(new IPeripheralAdapter() {

            @Override
            public String getSourceId() {
                return "bad";
            }

            @Override
            public Class<?> getTargetClass() {
                return Object.class;
            }
        });
    }

    @Test
    public void inlineMethodsAreCollectedDeterministicallyAndExcluded() {
        final ClassMethodsComposer composer = new ClassMethodsComposer(selector(Constants.ARCH_COMPUTER_CRAFT));
        final Map<String, IMethodExecutor> methods = composer
            .createMethodsList(InlineTarget.class, new AdapterRegistry());

        assertTrue(methods.containsKey("alpha"));
        assertTrue(methods.containsKey("zeta"));
        assertFalse(methods.containsKey("excluded"));
        assertTrue(methods.containsKey("listMethods"));

        final IndexedMethodMap map = new IndexedMethodMap(methods);
        final List<String> names = Lists.newArrayList(map.getMethodNames());
        final List<String> sorted = Lists.newArrayList(names);
        Collections.sort(sorted);
        assertEquals(sorted, names);
    }

    @Test
    public void methodMapsAreSorted() {
        final Map<String, IMethodExecutor> methods = Maps.newHashMap();
        methods.put("z", mock(IMethodExecutor.class));
        methods.put("a", mock(IMethodExecutor.class));
        methods.put("m", mock(IMethodExecutor.class));

        Assert.assertArrayEquals(new String[] { "a", "m", "z" }, new IndexedMethodMap(methods).getMethodNames());
        Assert.assertArrayEquals(new String[] { "a", "m", "z" }, new NamedMethodMap(methods).getMethodNames());
    }

    private static class TestFactory extends ComposedMethodsFactory<NamedMethodMap> {

        private int wrapCount;

        public TestFactory(AdapterRegistry adapters, MethodSelector selector) {
            super(adapters, selector);
        }

        @Override
        protected NamedMethodMap wrapMethods(Class<?> targetCls, Map<String, IMethodExecutor> methods) {
            wrapCount++;
            return new NamedMethodMap(methods);
        }
    }

    @Test
    public void composedFactoryCachesAndSerializesGeneration() throws Exception {
        final TestFactory factory = new TestFactory(new AdapterRegistry(), selector("CC"));
        final NamedMethodMap first = factory.getAdaptedClass(InlineTarget.class);
        Assert.assertSame(first, factory.getAdaptedClass(InlineTarget.class));
        assertEquals(1, factory.wrapCount);
        assertTrue(first.getMethodNames().length > 0);

        final ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            final CountDownLatch start = new CountDownLatch(1);
            final List<Future<NamedMethodMap>> futures = Lists.newArrayList();
            for (int i = 0; i < 8; i++) futures.add(executor.submit(new Callable<NamedMethodMap>() {

                @Override
                public NamedMethodMap call() throws Exception {
                    start.await(5, TimeUnit.SECONDS);
                    return factory.getAdaptedClass(InlineTarget.class);
                }
            }));

            start.countDown();
            for (Future<NamedMethodMap> future : futures) Assert.assertSame(first, future.get(10, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void composedFactoryRemembersInvalidClasses() {
        final TestFactory factory = new TestFactory(new AdapterRegistry(), selector("CC"));
        try {
            factory.getAdaptedClass(InvalidTarget.class);
            Assert.fail("Expected invalid class");
        } catch (ComposedMethodsFactory.InvalidClassException expected) {
            Assert.assertNotNull(expected.getCause());
        }

        try {
            factory.getAdaptedClass(InvalidTarget.class);
            Assert.fail("Expected cached invalid class");
        } catch (ComposedMethodsFactory.InvalidClassException expected) {
            Assert.assertNull(expected.getCause());
        }
    }

    @Asynchronous
    @ExcludeArchitecture("classArch")
    @FeatureGroup("classGroup")
    @ReturnSignal("classSignal")
    public static class AnnotatedTarget {

        @Asynchronous(false)
        @ExcludeArchitecture("methodArch")
        @FeatureGroup("methodGroup")
        @ReturnSignal("methodSignal")
        public void method() {}
    }

    @Test
    public void annotationMetaAccumulatesPackageClassAndMethodValues() throws Exception {
        final AnnotationMetaExtractor extractor = new AnnotationMetaExtractor(AnnotatedTarget.class);
        final java.lang.reflect.Method method = AnnotatedTarget.class.getMethod("method");

        Assert.assertEquals(
            com.google.common.collect.ImmutableSet.of("classArch", "methodArch"),
            extractor.getExcludedArchitectures(method));
        Assert.assertEquals(
            com.google.common.collect.ImmutableSet.of("classGroup", "methodGroup"),
            extractor.getFeatureGroups(method));
        Assert.assertFalse(extractor.isAsync(method));
        Assert.assertEquals(
            "methodSignal",
            extractor.getReturnSignal(method)
                .get());
    }
}
