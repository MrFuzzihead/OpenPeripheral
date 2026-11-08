package openperipheral.tests;

import org.junit.Assert;
import org.junit.Test;

import openperipheral.adapter.types.IntegerRange;
import openperipheral.adapter.types.TypeHelper;
import openperipheral.api.adapter.method.ArgType;

public class MiscTests {

    private static void testRange(String expected, IntegerRange range) {
        Assert.assertEquals(expected, range.describe());
    }

    @Test
    public void tupleAndVoidTypes() {
        Assert.assertEquals(
            "()",
            TypeHelper.createFromArg(java.util.Collections.<ArgType>emptyList())
                .describe());
        Assert.assertEquals(
            "(string,number)",
            TypeHelper.create(ArgType.STRING, ArgType.NUMBER)
                .describe());
        Assert.assertTrue(TypeHelper.isVoid(TypeHelper.createFromArg(java.util.Collections.<ArgType>emptyList())));
        Assert.assertFalse(TypeHelper.isVoid(TypeHelper.create(ArgType.STRING)));
        Assert
            .assertTrue(TypeHelper.compareTypes(TypeHelper.create(ArgType.STRING), TypeHelper.single(ArgType.STRING)));
        Assert
            .assertFalse(TypeHelper.compareTypes(TypeHelper.create(ArgType.STRING), TypeHelper.single(ArgType.NUMBER)));
    }

    @Test
    public void testIntegerRange() {
        testRange("(..)", new IntegerRange(null, null));
        testRange("(3..)", IntegerRange.leftBounded(3, true));
        testRange("[3..)", IntegerRange.leftBounded(3, false));
        testRange("(..5)", IntegerRange.rightBounded(5, true));
        testRange("(..5]", IntegerRange.rightBounded(5, false));
        testRange("(3..5)", IntegerRange.open(5, 3));
        testRange("(3..5)", IntegerRange.open(3, 5));
        testRange("[3..5]", IntegerRange.closed(3, 5));
        testRange("(3..5]", IntegerRange.leftOpen(3, 5));
        testRange("[3..5)", IntegerRange.rightOpen(3, 5));
    }

}
