package openperipheral.tests;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import com.google.common.collect.ImmutableMap;

import openperipheral.api.struct.ScriptStruct;
import openperipheral.api.struct.ScriptStruct.Output;
import openperipheral.api.struct.StructField;
import openperipheral.converter.StructHandlerProvider;
import openperipheral.converter.StructHandlerProvider.IStructHandler;
import openperipheral.converter.StructHandlerProvider.InvalidStructureException;
import openperipheral.interfaces.cc.TypeConversionRegistryCC;

public class StructEdgeTest {

    @ScriptStruct
    public static class Named {

        @StructField
        public String name;

        @StructField(optional = true)
        public Integer count;
    }

    @ScriptStruct(defaultOutput = Output.TABLE)
    public static class TableStruct {

        @StructField(index = 0)
        public String a;

        @StructField(index = 1)
        public String b;
    }

    public static class NotAStruct {

        public String value;
    }

    @ScriptStruct
    public static class PrivateStruct {

        @StructField
        private String secret;

        @StructField
        public int visible;
    }

    @ScriptStruct
    public class InnerStruct {

        @StructField
        public String value;
    }

    @Test(expected = IllegalArgumentException.class)
    public void negativeTableIndexIsRejected() {
        final IStructHandler handler = StructHandlerProvider.instance.getHandler(TableStruct.class);
        final Map<Integer, Object> input = ImmutableMap.of(-1, "a", 0, "b");
        handler.toJava(new TypeConversionRegistryCC(), input, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void missingRequiredFieldIsRejected() {
        final IStructHandler handler = StructHandlerProvider.instance.getHandler(Named.class);
        handler.toJava(new TypeConversionRegistryCC(), ImmutableMap.of("count", 1), 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void unknownFieldIsRejected() {
        final IStructHandler handler = StructHandlerProvider.instance.getHandler(Named.class);
        handler.toJava(new TypeConversionRegistryCC(), ImmutableMap.of("name", "x", "unknown", 1), 0);
    }

    @Test
    public void optionalFieldMayBeMissing() {
        final IStructHandler handler = StructHandlerProvider.instance.getHandler(Named.class);
        final Named result = (Named) handler.toJava(new TypeConversionRegistryCC(), ImmutableMap.of("name", "x"), 0);
        Assert.assertEquals("x", result.name);
        Assert.assertNull(result.count);
    }

    @Test
    public void privateFieldsAreAccessible() {
        final IStructHandler handler = StructHandlerProvider.instance.getHandler(PrivateStruct.class);
        final PrivateStruct result = (PrivateStruct) handler
            .toJava(new TypeConversionRegistryCC(), ImmutableMap.of("secret", "hidden", "visible", 2), 0);
        Assert.assertEquals("hidden", result.secret);
        Assert.assertEquals(2, result.visible);

        final Map<?, ?> output = handler.fromJava(new TypeConversionRegistryCC(), result, 0);
        Assert.assertEquals("hidden", output.get("secret"));
        Assert.assertEquals(2.0, output.get("visible"));
    }

    @Test
    public void structDetection() {
        assertTrue(StructHandlerProvider.instance.isStruct(Named.class));
        assertFalse(StructHandlerProvider.instance.isStruct(NotAStruct.class));
    }

    @Test
    public void nonStaticInnerClassIsRejected() {
        try {
            StructHandlerProvider.instance.getHandler(InnerStruct.class);
            Assert.fail("Expected InvalidStructureException");
        } catch (InvalidStructureException expected) {
            // expected
        }
    }

    @Test
    public void outputTableStartsAtConfiguredOffset() {
        final IStructHandler handler = StructHandlerProvider.instance.getHandler(TableStruct.class);
        final TableStruct value = new TableStruct();
        value.a = "a";
        value.b = "b";

        final Map<?, ?> output = handler.fromJava(new TypeConversionRegistryCC(), value, 1);
        Assert.assertEquals("a", output.get(1));
        Assert.assertEquals("b", output.get(2));
    }
}
