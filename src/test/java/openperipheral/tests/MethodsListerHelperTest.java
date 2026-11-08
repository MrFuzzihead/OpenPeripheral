package openperipheral.tests;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;

import openperipheral.adapter.ArgumentDescriptionBase;
import openperipheral.adapter.IMethodDescription;
import openperipheral.adapter.IMethodExecutor;
import openperipheral.adapter.composed.MethodsListerHelper;
import openperipheral.adapter.property.SimpleMethodDescription;
import openperipheral.adapter.types.SingleArgType;
import openperipheral.adapter.types.TypeHelper;
import openperipheral.api.adapter.method.ReturnType;
import openperipheral.util.DocUtils;

public class MethodsListerHelperTest {

    private static IMethodExecutor executor() {
        final IMethodDescription description = new SimpleMethodDescription(
            "test",
            "description",
            "source-a",
            ImmutableList.<IMethodDescription.IArgumentDescription>of(
                new ArgumentDescriptionBase("value", SingleArgType.STRING, "desc")),
            TypeHelper.single(ReturnType.STRING));

        final IMethodExecutor executor = mock(IMethodExecutor.class);
        when(executor.description()).thenReturn(description);
        return executor;
    }

    private static MethodsListerHelper helper() {
        final Map<String, IMethodExecutor> methods = Maps.newHashMap();
        methods.put("test", executor());
        return new MethodsListerHelper(methods, ImmutableSet.of("source-a", "source-b"));
    }

    @Test
    public void listMethodsFiltersBySource() {
        final MethodsListerHelper helper = helper();
        Assert.assertTrue(
            helper.listMethods(null)
                .contains("test("));
        Assert.assertTrue(
            helper.listMethods("source-a")
                .contains("test("));
        assertEquals("", helper.listMethods("missing"));
    }

    @Test
    public void listSourcesIsSorted() {
        final Map<String, Boolean> sources = helper().listSources();
        assertEquals(ImmutableList.of("source-a", "source-b"), ImmutableList.copyOf(sources.keySet()));
        assertEquals(Boolean.TRUE, sources.get("source-a"));
    }

    @Test
    public void docReturnsMethodDocumentation() {
        final MethodsListerHelper helper = helper();
        final String doc = helper.doc("test");
        Assert.assertTrue(doc.startsWith("function("));
        Assert.assertTrue(doc.contains("description"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void docRejectsUnknownMethod() {
        helper().doc("missing");
    }

    @Test
    public void advancedMethodsDataSupportsSingleAndAll() {
        final MethodsListerHelper helper = helper();

        @SuppressWarnings("unchecked")
        final Map<String, Object> single = (Map<String, Object>) helper.getAdvancedMethodsData("test");
        assertEquals("description", single.get(DocUtils.DESCRIPTION));
        assertEquals("source-a", single.get(DocUtils.SOURCE));

        final Map<?, ?> all = helper.getAdvancedMethodsData(null);
        Assert.assertTrue(all.containsKey("test"));

        final List<?> args = (List<?>) single.get(DocUtils.ARGS);
        assertEquals(1, args.size());
    }
}
