package openperipheral.tests;

import java.lang.reflect.Method;

import org.junit.Assert;
import org.junit.Test;

import openperipheral.adapter.method.MethodDeclaration;
import openperipheral.api.Constants;
import openperipheral.api.adapter.method.Alias;
import openperipheral.api.adapter.method.Arg;
import openperipheral.api.adapter.method.Env;
import openperipheral.api.adapter.method.Optionals;
import openperipheral.api.adapter.method.ReturnType;
import openperipheral.api.adapter.method.ScriptCallable;
import openperipheral.interfaces.cc.TypeConversionRegistryCC;

public class MethodDeclarationExtraTest {

    public static class Methods {

        @ScriptCallable(returnTypes = ReturnType.STRING)
        public String nullable(@Arg(name = "value", isNullable = true) String value) {
            return value == null ? "nil" : value;
        }

        @ScriptCallable(name = "aliased", returnTypes = ReturnType.STRING)
        @Alias({ "alias.one", "alias.two" })
        public String aliased() {
            return "value";
        }

        @ScriptCallable(returnTypes = ReturnType.STRING)
        public String optional(@Optionals @Arg(name = "value") String value) {
            return value == null ? "none" : value;
        }

        @ScriptCallable(returnTypes = ReturnType.STRING)
        public String duplicateOptionals(@Optionals @Arg(name = "a") String a, @Optionals @Arg(name = "b") String b) {
            return a + b;
        }

        @ScriptCallable(returnTypes = ReturnType.STRING)
        public String env(@Env(Constants.ARG_CONTEXT) Object context, @Arg(name = "value") String value) {
            return value;
        }
    }

    private static MethodDeclaration declaration(String name) throws Exception {
        final Method method = Methods.class.getMethod(name, parameterTypes(name));
        return new MethodDeclaration(Methods.class, method, method.getAnnotation(ScriptCallable.class), "test");
    }

    private static Class<?>[] parameterTypes(String name) {
        if ("nullable".equals(name)) return new Class<?>[] { String.class };
        if ("optional".equals(name)) return new Class<?>[] { String.class };
        if ("duplicateOptionals".equals(name)) return new Class<?>[] { String.class, String.class };
        if ("env".equals(name)) return new Class<?>[] { Object.class, String.class };
        return new Class<?>[0];
    }

    private static Object[] call(MethodDeclaration declaration, Object target, Object... args) throws Exception {
        return declaration.startCall(target)
            .setEnv(Constants.ARG_CONVERTER, new TypeConversionRegistryCC())
            .setEnv(Constants.ARG_CONTEXT, new Object())
            .call(args);
    }

    @Test
    public void nullableArgumentAcceptsNil() throws Exception {
        final MethodDeclaration declaration = declaration("nullable");
        Assert.assertArrayEquals(new Object[] { "nil" }, call(declaration, new Methods(), new Object[] { null }));
        Assert.assertArrayEquals(new Object[] { "value" }, call(declaration, new Methods(), "value"));
    }

    @Test
    public void optionalArgumentCanBeOmittedOrNull() throws Exception {
        final MethodDeclaration declaration = declaration("optional");
        Assert.assertArrayEquals(new Object[] { "none" }, call(declaration, new Methods()));
        Assert.assertArrayEquals(new Object[] { "none" }, call(declaration, new Methods(), new Object[] { null }));
        Assert.assertArrayEquals(new Object[] { "value" }, call(declaration, new Methods(), "value"));
    }

    @Test
    public void aliasesAreAllExposed() throws Exception {
        final MethodDeclaration declaration = declaration("aliased");
        Assert.assertEquals(java.util.Arrays.asList("aliased", "alias.one", "alias.two"), declaration.getNames());
    }

    @Test(expected = MethodDeclaration.ArgumentDefinitionException.class)
    public void duplicateOptionalsAreRejected() throws Exception {
        declaration("duplicateOptionals");
    }

    @Test
    public void envArgumentIsNamedAndNotScriptVisible() throws Exception {
        final MethodDeclaration declaration = declaration("env");
        Assert.assertEquals(
            1,
            declaration.arguments()
                .size());
        Assert.assertEquals(
            1,
            declaration.getOptionalArgs()
                .size());
        Assert.assertArrayEquals(new Object[] { "value" }, call(declaration, new Methods(), "value"));
    }
}
