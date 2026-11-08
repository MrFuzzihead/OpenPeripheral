package openperipheral.tests;

import java.lang.reflect.Field;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.google.common.base.Throwables;
import com.google.common.collect.Lists;

import openperipheral.adapter.AnnotationMetaExtractor;
import openperipheral.adapter.IMethodCall;
import openperipheral.adapter.IMethodDescription;
import openperipheral.adapter.IMethodDescription.IArgumentDescription;
import openperipheral.adapter.IMethodExecutor;
import openperipheral.adapter.property.IndexedManipulatorProvider;
import openperipheral.adapter.property.PropertyListBuilder;
import openperipheral.api.Constants;
import openperipheral.api.adapter.IndexedProperty;
import openperipheral.api.adapter.Property;
import openperipheral.api.adapter.method.ArgType;
import openperipheral.api.converter.IConverter;
import openperipheral.api.helpers.Index;
import openperipheral.api.property.IIndexedCustomProperty;
import openperipheral.api.property.ISingleCustomProperty;
import openperipheral.interfaces.cc.TypeConversionRegistryCC;

public class PropertyExecutorTest {

    private static final IConverter CONVERTER = new TypeConversionRegistryCC();

    public static class SingleHolder {

        @Property
        public String value;
    }

    public static class NullableHolder {

        @Property(nullable = true)
        public String value;
    }

    public static class IndexedDocsHolder {

        @IndexedProperty(keyType = ArgType.STRING, valueType = ArgType.BOOLEAN)
        public List<Integer> values;
    }

    public static class MergedHolder {

        @Property(name = "both")
        @IndexedProperty(name = "both", setterDesc = "INDEXED-SETTER")
        public List<String> both;
    }

    public static class IndexedCustomHolder {

        @IndexedProperty
        public CustomIndexedProperty property = new CustomIndexedProperty();
    }

    public static class CustomIndexedProperty implements IIndexedCustomProperty<String, String> {

        public Object lastOwner;
        public Field lastField;
        public String lastKey;
        public String lastValue;

        @Override
        public String get(Object owner, Field field, String key) {
            lastOwner = owner;
            lastField = field;
            lastKey = key;
            return "value:" + key;
        }

        @Override
        public void set(Object owner, Field field, String key, String value) {
            lastOwner = owner;
            lastField = field;
            lastKey = key;
            lastValue = value;
        }
    }

    public static class SingleCustomHolder {

        @Property
        public CustomSingleProperty property = new CustomSingleProperty();
    }

    public static class CustomSingleProperty implements ISingleCustomProperty<String> {

        public Object lastOwner;
        public String lastValue;

        @Override
        public String get(Object owner, Field field) {
            lastOwner = owner;
            return "value";
        }

        @Override
        public void set(Object owner, Field field, String value) {
            lastOwner = owner;
            lastValue = value;
        }
    }

    private static List<IMethodExecutor> build(Class<?> cls) {
        final List<IMethodExecutor> result = Lists.newArrayList();
        PropertyListBuilder.buildPropertyList(cls, cls, "test", new AnnotationMetaExtractor(cls), result);
        return result;
    }

    private static IMethodExecutor find(List<IMethodExecutor> methods, String name) {
        for (IMethodExecutor method : methods) if (method.description()
            .getNames()
            .contains(name)) return method;

        throw new AssertionError("Method not found: " + name + " in " + methods);
    }

    private static Object[] call(IMethodExecutor executor, Object target, Object... args) throws Exception {
        final IMethodCall call = executor.startCall(target)
            .setEnv(Constants.ARG_CONVERTER, CONVERTER);
        return call.call(args);
    }

    private static Field field(Class<?> cls, String name) {
        try {
            return cls.getField(name);
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    @Test
    public void singlePropertyGetterAndSetter() throws Exception {
        final SingleHolder holder = new SingleHolder();
        holder.value = "before";
        final List<IMethodExecutor> methods = build(SingleHolder.class);

        Assert.assertEquals("before", call(find(methods, "getValue"), holder)[0]);
        call(find(methods, "setValue"), holder, "after");
        Assert.assertEquals("after", holder.value);
    }

    @Test
    public void nullablePropertyAcceptsNil() throws Exception {
        final NullableHolder holder = new NullableHolder();
        holder.value = "before";
        final List<IMethodExecutor> methods = build(NullableHolder.class);

        call(find(methods, "setValue"), holder, new Object[] { null });
        Assert.assertNull(holder.value);
    }

    @Test(expected = IllegalArgumentException.class)
    public void nonNullablePropertyRejectsNil() throws Exception {
        final SingleHolder holder = new SingleHolder();
        call(find(build(SingleHolder.class), "setValue"), holder, new Object[] { null });
    }

    @Test
    public void indexedPropertyUsesDeclaredDocumentationTypes() {
        final List<IMethodExecutor> methods = build(IndexedDocsHolder.class);
        final IMethodDescription description = find(methods, "getValues").description();

        final List<IArgumentDescription> args = description.arguments();
        Assert.assertEquals(1, args.size());
        Assert.assertEquals(
            "string",
            args.get(0)
                .type()
                .describe());
        Assert.assertEquals(
            "boolean",
            description.returnTypes()
                .describe());
    }

    @Test
    public void mergedSetterFallsBackToIndexedDescription() {
        final List<IMethodExecutor> methods = build(MergedHolder.class);
        Assert.assertEquals(
            "INDEXED-SETTER",
            find(methods, "setBoth").description()
                .description());
    }

    @Test
    public void indexedCustomPropertyReceivesOwnerNotContainer() throws Exception {
        final IndexedCustomHolder holder = new IndexedCustomHolder();
        final List<IMethodExecutor> methods = build(IndexedCustomHolder.class);

        call(find(methods, "setProperty"), holder, "value", "key");
        Assert.assertSame(holder, holder.property.lastOwner);
        Assert.assertEquals("key", holder.property.lastKey);
        Assert.assertEquals("value", holder.property.lastValue);
        Assert.assertEquals(field(IndexedCustomHolder.class, "property"), holder.property.lastField);

        holder.property.lastOwner = null;
        Assert.assertEquals("value:key", call(find(methods, "getProperty"), holder, "key")[0]);
        Assert.assertSame(holder, holder.property.lastOwner);
    }

    @Test
    public void singleCustomPropertyReceivesOwner() throws Exception {
        final SingleCustomHolder holder = new SingleCustomHolder();
        final List<IMethodExecutor> methods = build(SingleCustomHolder.class);

        call(find(methods, "setProperty"), holder, "value");
        Assert.assertSame(holder, holder.property.lastOwner);
        Assert.assertEquals("value", holder.property.lastValue);
        Assert.assertEquals("value", call(find(methods, "getProperty"), holder)[0]);
    }

    @Test(expected = IllegalArgumentException.class)
    public void nullIndexIsRejectedWithAHelpfulMessage() {
        IndexedManipulatorProvider.getIndex(null);
    }

    @Test
    public void negativeArrayIndexReturnsNull() throws Exception {
        final Field field = field(ArrayHolder.class, "values");
        final int[] values = { 1, 2, 3 };
        final Object result = IndexedManipulatorProvider.ARRAY_MANIPULATOR
            .getField(new ArrayHolder(), values, field, Index.fromJava(-1, 0));
        Assert.assertNull(result);
    }

    public static class ArrayHolder {

        public int[] values;
    }

    public static class StructHolder {

        @openperipheral.api.struct.ScriptStruct
        public static class Struct {

            @openperipheral.api.struct.StructField
            public int value;
        }

        @IndexedProperty
        public Struct struct = new Struct();
    }
}
