package openperipheral.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.Assert;
import org.junit.Test;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.reflect.TypeToken;

import openperipheral.api.converter.IConverter;
import openperipheral.api.converter.IGenericInboundTypeConverter;
import openperipheral.api.converter.IInboundTypeConverter;
import openperipheral.api.converter.IOutboundTypeConverter;
import openperipheral.api.helpers.Index;
import openperipheral.converter.TypeConverter;
import openperipheral.converter.outbound.ConverterArrayOutbound;
import openperipheral.converter.outbound.ConverterBoolean;
import openperipheral.converter.outbound.ConverterEnumOutbound;
import openperipheral.converter.outbound.ConverterFluidTankInfoOutbound;
import openperipheral.converter.outbound.ConverterGameProfileOutbound;
import openperipheral.converter.outbound.ConverterListOutbound;
import openperipheral.converter.outbound.ConverterMapOutbound;
import openperipheral.converter.outbound.ConverterNumberOutbound;
import openperipheral.converter.outbound.ConverterSetOutbound;
import openperipheral.interfaces.cc.TypeConversionRegistryCC;

public class TypeConverterTest {

    private static final Type STRING_LIST = new TypeToken<List<String>>() {

        private static final long serialVersionUID = 1L;
    }.getType();

    private static final Type INTEGER_MAP = new TypeToken<Map<String, Integer>>() {

        private static final long serialVersionUID = 1L;
    }.getType();

    private static final Type STRING_SET = new TypeToken<Set<String>>() {

        private static final long serialVersionUID = 1L;
    }.getType();

    private static final Type STRING_COLLECTION = new TypeToken<java.util.Collection<String>>() {

        private static final long serialVersionUID = 1L;
    }.getType();

    private static final Type STRING_HASH_MAP = new TypeToken<java.util.HashMap<String, Integer>>() {

        private static final long serialVersionUID = 1L;
    }.getType();

    private static final Type STRING_HASH_SET = new TypeToken<java.util.HashSet<String>>() {

        private static final long serialVersionUID = 1L;
    }.getType();

    private static final Type STRING_LINKED_LIST = new TypeToken<java.util.LinkedList<String>>() {

        private static final long serialVersionUID = 1L;
    }.getType();

    private static final Type STRING_LINKED_MAP = new TypeToken<java.util.LinkedHashMap<String, Integer>>() {

        private static final long serialVersionUID = 1L;
    }.getType();

    private static final Type STRING_TREE_SET = new TypeToken<java.util.TreeSet<String>>() {

        private static final long serialVersionUID = 1L;
    }.getType();

    public static enum TestEnum {
        ALPHA,
        BETA
    }

    public static class Ignored {
    }

    public static class IgnoredChild extends Ignored {
    }

    private static TypeConverter converter() {
        return new TypeConversionRegistryCC();
    }

    private static Map<Object, Object> table(Object... values) {
        final Map<Object, Object> result = Maps.newHashMap();
        for (int i = 0; i < values.length; i += 2) result.put(values[i], values[i + 1]);
        return result;
    }

    @Test
    public void nilIsAcceptedForReferenceTypes() {
        assertNull(converter().toJava(null, String.class));
    }

    @Test(expected = IllegalArgumentException.class)
    public void nilIsRejectedForPrimitiveTypes() {
        converter().toJava(null, int.class);
    }

    @Test
    public void numberConversionCoversAllPrimitiveWrappers() {
        final TypeConverter converter = converter();
        Assert.assertEquals(Integer.valueOf(3), converter.toJava(3.0, int.class));
        Assert.assertEquals(Integer.valueOf(3), converter.toJava(3.0, Integer.class));
        Assert.assertEquals(Long.valueOf(3), converter.toJava(3.0, long.class));
        Assert.assertEquals(Short.valueOf((short) 3), converter.toJava(3.0, short.class));
        Assert.assertEquals(Byte.valueOf((byte) 3), converter.toJava(3.0, byte.class));
        Assert.assertEquals(Float.valueOf(3.0f), converter.toJava(3.0, float.class));
        Assert.assertEquals(Double.valueOf(3.0), converter.toJava(3.0, double.class));
        Assert.assertEquals(Boolean.TRUE, converter.toJava(1.0, boolean.class));
        Assert.assertEquals(Boolean.FALSE, converter.toJava(0.0, boolean.class));
    }

    @Test
    public void nativeBooleanIsConverted() {
        Assert.assertEquals(Boolean.TRUE, converter().toJava(Boolean.TRUE, boolean.class));
        Assert.assertEquals(Boolean.FALSE, converter().toJava(Boolean.FALSE, Boolean.class));
    }

    @Test
    public void numericStringsAreConverted() {
        Assert.assertEquals(Integer.valueOf(42), converter().toJava("42", int.class));
        Assert.assertEquals(Boolean.TRUE, converter().toJava("1", boolean.class));
    }

    @Test
    public void indicesUseArchitectureOffset() {
        final Index index = (Index) converter().toJava(3.0, Index.class);
        Assert.assertEquals(2, index.value);
        Assert.assertEquals(1, index.offset);
        Assert.assertEquals(3, index.intValue());
    }

    @Test
    public void enumConversionByNameAndOrdinal() {
        Assert.assertEquals(TestEnum.BETA, converter().toJava("beta", TestEnum.class));
        Assert.assertEquals(TestEnum.ALPHA, converter().toJava(1.0, TestEnum.class));
        Assert.assertEquals(TestEnum.BETA, converter().toJava(2.0, TestEnum.class));
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidEnumValueIsRejected() {
        converter().toJava("gamma", TestEnum.class);
    }

    @Test
    public void uuidConversion() {
        final UUID uuid = UUID.randomUUID();
        Assert.assertEquals(uuid, converter().toJava(uuid.toString(), UUID.class));
    }

    @Test
    public void rawObjectConversionIsBypass() {
        final Object value = new Object();
        assertSame(value, converter().toJava(value, Object.class));
    }

    @Test
    public void customInboundConverterTakesPrecedence() {
        final TypeConverter converter = converter();
        converter.register(new IInboundTypeConverter() {

            @Override
            public Object toJava(IConverter registry, Object obj, Class<?> expected) {
                return expected == String.class ? "custom" : null;
            }
        });
        assertEquals("custom", converter.toJava("ignored", String.class));
    }

    @Test
    public void genericInboundConverterTakesPrecedence() {
        final TypeConverter converter = converter();
        converter.register(new IGenericInboundTypeConverter() {

            @Override
            public Object toJava(IConverter registry, Object obj, Type expected) {
                return expected == String.class ? "generic" : null;
            }
        });
        assertEquals("generic", converter.toJava("ignored", String.class));
    }

    @Test
    public void ignoredClassesArePassedThrough() {
        final TypeConverter converter = converter();
        converter.registerIgnored(Ignored.class, true);
        final IgnoredChild child = new IgnoredChild();
        assertSame(child, converter.fromJava(child));
    }

    @Test
    public void stringConverterIsCatchAll() {
        final Object value = new Object();
        assertEquals(value.toString(), converter().fromJava(value));
    }

    @Test
    public void nullableToJavaRespectsFlag() {
        final TypeConverter converter = converter();
        assertNull(TypeConverter.nullableToJava(converter, true, null, String.class));

        try {
            TypeConverter.nullableToJava(converter, false, null, String.class);
            Assert.fail("Expected rejection of null");
        } catch (IllegalArgumentException expected) {
            // expected
        }

        assertEquals("abc", TypeConverter.nullableToJava(converter, false, "abc", String.class));
    }

    @Test
    public void classOverloadAcceptsPrimitiveClasses() {
        final TypeConverter converter = converter();
        final Integer result = converter.toJava(3.0, int.class);
        Assert.assertEquals(Integer.valueOf(3), result);
    }

    @Test(expected = IllegalArgumentException.class)
    public void classOverloadRejectsIncompatibleResult() {
        converter().toJava("not a number", Integer.class);
    }

    @Test
    public void primitiveArrayFromTableSkipsMissingValues() {
        final int[] result = (int[]) converter().toJava(table(1.0, 10.0, 3.0, 30.0), int[].class);
        Assert.assertArrayEquals(new int[] { 10, 0, 30 }, result);
    }

    @Test
    public void objectArrayFromTablePreservesNulls() {
        final Integer[] result = (Integer[]) converter().toJava(table(1.0, 10.0, 3.0, 30.0), Integer[].class);
        Assert.assertArrayEquals(new Integer[] { 10, null, 30 }, result);
    }

    @Test
    public void listFromTable() {
        @SuppressWarnings("unchecked")
        final List<String> result = (List<String>) converter().toJava(table(1.0, "a", 2.0, "b"), STRING_LIST);
        Assert.assertEquals(java.util.Arrays.asList("a", "b"), result);
    }

    @Test
    public void mapFromTable() {
        @SuppressWarnings("unchecked")
        final Map<String, Integer> result = (Map<String, Integer>) converter()
            .toJava(table("a", 1.0, "b", 2.0), INTEGER_MAP);
        Assert.assertEquals(Integer.valueOf(1), result.get("a"));
        Assert.assertEquals(Integer.valueOf(2), result.get("b"));
    }

    @Test
    public void collectionFromTable() {
        @SuppressWarnings("unchecked")
        final java.util.Collection<String> result = (java.util.Collection<String>) converter()
            .toJava(table(1.0, "a", 2.0, "b"), STRING_COLLECTION);
        Assert.assertEquals(java.util.Arrays.asList("a", "b"), new java.util.ArrayList<String>(result));
    }

    @Test
    public void concreteCollectionsFromTable() {
        @SuppressWarnings("unchecked")
        final java.util.HashMap<String, Integer> map = (java.util.HashMap<String, Integer>) converter()
            .toJava(table("a", 1.0), STRING_HASH_MAP);
        Assert.assertEquals(Integer.valueOf(1), map.get("a"));

        @SuppressWarnings("unchecked")
        final java.util.HashSet<String> set = (java.util.HashSet<String>) converter()
            .toJava(table("a", Boolean.TRUE), STRING_HASH_SET);
        Assert.assertTrue(set.contains("a"));
    }

    @Test
    public void concreteListFromTable() {
        @SuppressWarnings("unchecked")
        final java.util.LinkedList<String> result = (java.util.LinkedList<String>) converter()
            .toJava(table(1.0, "a", 2.0, "b"), STRING_LINKED_LIST);
        Assert.assertEquals(java.util.Arrays.asList("a", "b"), result);
    }

    @Test
    public void concreteMapFromTable() {
        @SuppressWarnings("unchecked")
        final java.util.LinkedHashMap<String, Integer> result = (java.util.LinkedHashMap<String, Integer>) converter()
            .toJava(table("a", 1.0), STRING_LINKED_MAP);
        Assert.assertEquals(Integer.valueOf(1), result.get("a"));
    }

    @Test
    public void concreteSetFromTable() {
        @SuppressWarnings("unchecked")
        final java.util.TreeSet<String> result = (java.util.TreeSet<String>) converter()
            .toJava(table("b", Boolean.TRUE, "a", Boolean.TRUE), STRING_TREE_SET);
        Assert.assertEquals(java.util.Arrays.asList("a", "b"), new java.util.ArrayList<String>(result));
    }

    @Test
    public void stringConverterAcceptsScalars() {
        Assert.assertEquals("3", converter().toJava(3, String.class));
        Assert.assertEquals("true", converter().toJava(Boolean.TRUE, String.class));
    }

    @Test(expected = IllegalArgumentException.class)
    public void stringConverterRejectsTables() {
        converter().toJava(table(1.0, "a"), String.class);
    }

    @Test
    public void inboundRecursionIsStopped() {
        final TypeConverter converter = converter();
        converter.register(new IGenericInboundTypeConverter() {

            @Override
            public Object toJava(IConverter registry, Object obj, Type expected) {
                return registry.toJava(obj, expected);
            }
        });

        try {
            converter.toJava("x", String.class);
            Assert.fail("Expected recursion guard");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(
                expected.getMessage()
                    .contains("recursion"));
        }
    }

    @Test
    public void outboundRecursionIsStopped() {
        final TypeConverter converter = converter();
        converter.register(new IOutboundTypeConverter() {

            @Override
            public Object fromJava(IConverter registry, Object obj) {
                return registry.fromJava(obj);
            }
        });

        try {
            converter.fromJava("x");
            Assert.fail("Expected recursion guard");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(
                expected.getMessage()
                    .contains("recursion"));
        }
    }

    @Test
    public void setFromTableUsesTruthyValues() {
        @SuppressWarnings("unchecked")
        final Set<String> result = (Set<String>) converter()
            .toJava(table("a", Boolean.TRUE, "b", Boolean.FALSE), STRING_SET);
        Assert.assertEquals(java.util.Collections.singleton("a"), result);
    }

    @Test(expected = IllegalArgumentException.class)
    public void itemStackRequiresStringId() {
        converter().toJava(table("id", 1.0), net.minecraft.item.ItemStack.class);
    }

    @Test(expected = IllegalArgumentException.class)
    public void itemStackRequiresNamespacedId() {
        converter().toJava(table("id", "invalid"), net.minecraft.item.ItemStack.class);
    }

    @Test
    public void itemStackOutboundRejectsNull() {
        assertNull(converter().fromJava(null));
    }

    @Test
    public void structRoundTrip() {
        final TypeConverter converter = converter();
        final ConverterTestStruct input = (ConverterTestStruct) converter
            .toJava(table("name", "test", "count", 4.0), ConverterTestStruct.class);
        Assert.assertEquals("test", input.name);
        Assert.assertEquals(4, input.count);

        @SuppressWarnings("unchecked")
        final Map<String, Object> output = (Map<String, Object>) converter.fromJava(input);
        Assert.assertEquals("test", output.get("name"));
        Assert.assertEquals(4.0, output.get("count"));
    }

    @Test
    public void outboundScalars() {
        final TypeConverter converter = converter();
        Assert.assertEquals(Boolean.TRUE, converter.fromJava(Boolean.TRUE));
        Assert.assertEquals(3.0, converter.fromJava(3));
        assertEquals("beta", converter.fromJava(TestEnum.BETA));
    }

    @Test
    public void enumOutboundUsesEnglishLocale() {
        final java.util.Locale previous = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(new java.util.Locale("tr", "TR"));
            assertEquals("ii", new ConverterEnumOutbound().fromJava(null, TestEnumWithDottedI.II));
        } finally {
            java.util.Locale.setDefault(previous);
        }
    }

    public static enum TestEnumWithDottedI {
        II
    }

    @Test
    public void outboundArrayAndListUseOffset() {
        final int[] array = { 10, 20 };
        @SuppressWarnings("unchecked")
        final Map<Object, Object> arrayResult = (Map<Object, Object>) converter().fromJava(array);
        Assert.assertEquals(2, arrayResult.size());
        Assert.assertEquals(10.0, arrayResult.get(1));
        Assert.assertEquals(20.0, arrayResult.get(2));

        @SuppressWarnings("unchecked")
        final Map<Object, Object> listResult = (Map<Object, Object>) converter()
            .fromJava(java.util.Arrays.asList("a", "b"));
        Assert.assertEquals("a", listResult.get(1));
        Assert.assertEquals("b", listResult.get(2));
    }

    @Test
    public void outboundMapAndSet() {
        final TypeConverter converter = converter();
        @SuppressWarnings("unchecked")
        final Map<Object, Object> map = (Map<Object, Object>) converter.fromJava(ImmutableMap.of("a", 1));
        Assert.assertEquals(1.0, map.get("a"));

        @SuppressWarnings("unchecked")
        final Map<Object, Object> set = (Map<Object, Object>) converter
            .fromJava(new java.util.HashSet<String>(java.util.Arrays.asList("a", "b")));
        Assert.assertEquals(Boolean.TRUE, set.get("a"));
        Assert.assertEquals(Boolean.TRUE, set.get("b"));
    }

    @Test
    public void outboundGameProfileHandlesMissingId() {
        final IConverter registry = mock(IConverter.class);
        @SuppressWarnings("unchecked")
        final Map<String, Object> result = (Map<String, Object>) new ConverterGameProfileOutbound()
            .fromJava(registry, new com.mojang.authlib.GameProfile(null, "Player"));
        Assert.assertEquals("Player", result.get("name"));
        assertNull(result.get("uuid"));
    }

    @Test
    public void outboundGameProfileIncludesId() {
        final UUID id = UUID.randomUUID();
        @SuppressWarnings("unchecked")
        final Map<String, Object> result = (Map<String, Object>) new ConverterGameProfileOutbound()
            .fromJava(mock(IConverter.class), new com.mojang.authlib.GameProfile(id, "Player"));
        Assert.assertEquals(id.toString(), result.get("uuid"));
    }

    @Test
    public void outboundFluidTankInfoHandlesEmptyTank() {
        final IConverter registry = mock(IConverter.class);
        when(registry.fromJava(any())).thenReturn(null);

        @SuppressWarnings("unchecked")
        final Map<String, Object> result = (Map<String, Object>) new ConverterFluidTankInfoOutbound()
            .fromJava(registry, new net.minecraftforge.fluids.FluidTankInfo(null, 1000));
        Assert.assertEquals(1000, result.get("capacity"));
        assertNull(result.get("contents"));
    }

    @Test
    public void outboundConvertersHandleNullWhereTheContractRequiresIt() {
        assertNull(converter().fromJava(null));
        assertNull(new ConverterBoolean().fromJava(null, null));
        assertNull(new ConverterNumberOutbound().fromJava(null, null));
        assertNull(new ConverterListOutbound(1).fromJava(null, null));
        assertNull(new ConverterMapOutbound().fromJava(null, null));
        assertNull(new ConverterSetOutbound().fromJava(null, null));
        assertNull(new ConverterArrayOutbound(1).fromJava(null, null));
    }

    @Test
    public void customOutboundConverterTakesPrecedence() {
        final TypeConverter converter = converter();
        converter.register(new IOutboundTypeConverter() {

            @Override
            public Object fromJava(IConverter registry, Object obj) {
                return obj instanceof Integer ? "custom" : null;
            }
        });
        assertEquals("custom", converter.fromJava(1));
    }

    @Test
    public void outboundNumberIsAlwaysDouble() {
        Assert.assertEquals(3.0, new ConverterNumberOutbound().fromJava(null, 3));
        Assert.assertEquals(3.0, new ConverterNumberOutbound().fromJava(null, 3L));
    }

    @Test
    public void outboundBooleanOnlyHandlesBooleans() {
        assertNull(new ConverterBoolean().fromJava(null, "true"));
        assertTrue(new ConverterBoolean().fromJava(null, Boolean.TRUE) instanceof Boolean);
    }

    @openperipheral.api.struct.ScriptStruct
    public static class ConverterTestStruct {

        @openperipheral.api.struct.StructField
        public String name;

        @openperipheral.api.struct.StructField
        public int count;
    }
}
