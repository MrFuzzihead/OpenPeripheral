package openperipheral.tests;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.Assert;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import openperipheral.adapter.ArgumentDescriptionBase;
import openperipheral.adapter.IMethodDescription;
import openperipheral.adapter.IMethodDescription.IArgumentDescription;
import openperipheral.adapter.IMethodExecutor;
import openperipheral.adapter.composed.IndexedMethodMap;
import openperipheral.adapter.property.SimpleMethodDescription;
import openperipheral.adapter.types.SingleArgType;
import openperipheral.adapter.types.TypeHelper;
import openperipheral.api.adapter.Doc;
import openperipheral.api.adapter.method.ReturnType;
import openperipheral.util.DocBuilder;
import openperipheral.util.DocUtils;

public class DocumentationTest {

    private static class OptionalArgument extends ArgumentDescriptionBase {

        private OptionalArgument(String name) {
            super(name, SingleArgType.STRING, "");
        }

        @Override
        public boolean optional() {
            return true;
        }
    }

    private static class VariadicArgument extends ArgumentDescriptionBase {

        private VariadicArgument(String name) {
            super(name, SingleArgType.NUMBER, "");
        }

        @Override
        public boolean variadic() {
            return true;
        }
    }

    private static IMethodDescription description() {
        final List<IArgumentDescription> args = Lists.newArrayList();
        args.add(new ArgumentDescriptionBase("number", SingleArgType.NUMBER, "a number"));
        args.add(new OptionalArgument("text"));
        args.add(new VariadicArgument("rest"));
        return new SimpleMethodDescription(
            "test",
            "does a thing",
            "unit-test",
            args,
            TypeHelper.single(ReturnType.STRING));
    }

    @Doc({ "First line", "Second line" })
    public static class DocumentedTarget {
    }

    @Test
    public void signatureAndDocFormatting() {
        final IMethodDescription description = description();
        Assert.assertEquals("(number,text?,rest...)", DocUtils.signature(description));
        Assert.assertEquals(
            "function(number:number,text:string?,rest:number...):string -- does a thing",
            DocUtils.doc(description));
        Assert.assertEquals(
            "function test(number:number,text:string?,rest:number...):string -- does a thing",
            DocUtils.doc("test", description));
    }

    @Test
    public void describeContainsArgumentsAndReturnType() {
        final Map<String, Object> described = DocUtils.describe(description());
        Assert.assertEquals("does a thing", described.get(DocUtils.DESCRIPTION));
        Assert.assertEquals("unit-test", described.get(DocUtils.SOURCE));
        Assert.assertEquals("string", described.get(DocUtils.RETURN_TYPES));

        @SuppressWarnings("unchecked")
        final List<Map<String, Object>> args = (List<Map<String, Object>>) described.get(DocUtils.ARGS);
        Assert.assertEquals(3, args.size());
        Assert.assertEquals(
            "number",
            args.get(0)
                .get(DocUtils.NAME));
        Assert.assertEquals(
            Boolean.TRUE,
            args.get(1)
                .get("optional"));
        Assert.assertEquals(
            Boolean.TRUE,
            args.get(2)
                .get("vararg"));
    }

    @Test
    public void peripheralHelpTextContainsDocAndMethods() {
        final IMethodExecutor executor = mock(IMethodExecutor.class);
        when(executor.description()).thenReturn(description());

        final Map<String, IMethodExecutor> methods = Maps.newHashMap();
        methods.put("test", executor);

        final String text = DocUtils
            .createPeripheralHelpText(DocumentedTarget.class, "test_peripheral", new IndexedMethodMap(methods));

        Assert.assertTrue(text.contains("Peripheral type: test_peripheral"));
        Assert.assertTrue(text.contains("First line"));
        Assert.assertTrue(text.contains("Second line"));
        Assert.assertTrue(text.contains("function test("));
    }

    @Test
    public void docBuilderWritesValidXmlAndXhtml() throws Exception {
        final IMethodExecutor executor = mock(IMethodExecutor.class);
        final IMethodDescription description = description();
        when(executor.description()).thenReturn(description);
        when(executor.isAsynchronous()).thenReturn(false);
        when(executor.getReturnSignal()).thenReturn(com.google.common.base.Optional.<String>absent());
        when(executor.featureGroups()).thenReturn(java.util.Collections.<String>emptySet());

        final Map<String, IMethodExecutor> methods = Maps.newHashMap();
        methods.put("test", executor);

        final DocBuilder builder = new DocBuilder();
        builder.createDocForArchitecture("ComputerCraft", true);
        builder.createDocForFeatureGroup(
            "testGroup",
            ImmutableList.of("ComputerCraft"),
            new openperipheral.adapter.FeatureGroupManager());
        builder.createDocForClass(
            "ComputerCraft",
            "peripheral",
            DocBuilder.NULL_DECORATOR,
            DocumentedTarget.class,
            new IndexedMethodMap(methods));

        final File xml = File.createTempFile("openperipheral-doc", ".xml");
        builder.dumpXml(xml, false);

        final Document document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(xml);
        Assert.assertEquals(
            "documentation",
            document.getDocumentElement()
                .getTagName());

        final NodeList classMethods = document.getElementsByTagName("classMethods");
        Assert.assertEquals(1, classMethods.getLength());
        final Element classMethod = (Element) classMethods.item(0);
        Assert.assertEquals("ComputerCraft", classMethod.getAttribute("architecture"));
        Assert.assertEquals("peripheral", classMethod.getAttribute("type"));

        final NodeList methodNodes = classMethod.getElementsByTagName("method");
        Assert.assertEquals(1, methodNodes.getLength());

        final File xhtml = File.createTempFile("openperipheral-doc", ".xhtml");
        builder.dumpXml(xhtml, true);
        Assert.assertTrue(xhtml.length() > 0);
        final String content = new String(Files.readAllBytes(xhtml.toPath()), java.nio.charset.StandardCharsets.UTF_8);
        Assert.assertTrue(
            content.toLowerCase()
                .contains("html"));
    }
}
