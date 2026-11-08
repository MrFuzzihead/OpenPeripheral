package openperipheral.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.ILuaTask;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.MethodResult;
import dan200.computercraft.api.peripheral.IComputerAccess;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.Node;
import openperipheral.adapter.IMethodCall;
import openperipheral.api.Constants;
import openperipheral.api.architecture.IArchitecture;
import openperipheral.api.architecture.IArchitectureAccess;
import openperipheral.api.converter.IConverter;
import openperipheral.api.helpers.Index;
import openperipheral.interfaces.cc.ComputerCraftEnv;
import openperipheral.interfaces.cc.SynchronousExecutor;
import openperipheral.interfaces.oc.OpenComputersEnv;

public class EnvironmentTest {

    @Test
    public void computerCraftArchitectureAccess() {
        final IConverter converter = mock(IConverter.class);
        final ComputerCraftEnv env = new ComputerCraftEnv(converter);

        final IComputerAccess access = mock(IComputerAccess.class);
        when(access.getID()).thenReturn(3);
        when(access.getAttachmentName()).thenReturn("back");

        final IArchitectureAccess architecture = env.createAccess(access);
        assertEquals("3", architecture.callerName());
        assertEquals("back", architecture.peripheralName());
        assertTrue(architecture.canSignal());
        assertTrue(architecture.signal("event", "a", 1));

        final ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(access).queueEvent(eq("event"), args.capture());
        Assert.assertArrayEquals(new Object[] { "a", 1 }, args.getValue());

        final Index index = architecture.createIndex(5);
        assertEquals(5, index.value);
        assertEquals(1, index.offset);
        Assert.assertSame(converter, architecture.getConverter());
        assertEquals(Constants.ARCH_COMPUTER_CRAFT, architecture.architecture());
    }

    @Test
    public void computerCraftAccessFailureIsReportedAsFalse() {
        final ComputerCraftEnv env = new ComputerCraftEnv(mock(IConverter.class));
        final IComputerAccess access = mock(IComputerAccess.class);
        when(access.getAttachmentName()).thenThrow(new IllegalStateException("detached"));
        when(access.getID()).thenReturn(1);
        org.mockito.Mockito.doThrow(new IllegalStateException("detached"))
            .when(access)
            .queueEvent(anyString(), any(Object[].class));

        final IArchitectureAccess architecture = env.createAccess(access);
        assertFalse(architecture.canSignal());
        assertFalse(architecture.signal("event"));
    }

    @Test
    public void computerCraftEnvInjectsExpectedVariables() {
        final IConverter converter = mock(IConverter.class);
        final ComputerCraftEnv env = new ComputerCraftEnv(converter);
        final IComputerAccess access = mock(IComputerAccess.class);
        final ILuaContext context = mock(ILuaContext.class);

        final IMethodCall objectCall = mock(IMethodCall.class);
        when(objectCall.setEnv(anyString(), any())).thenReturn(objectCall);
        assertEquals(objectCall, env.addObjectArgs(objectCall, context));
        verify(objectCall).setEnv(Constants.ARG_CONVERTER, converter);
        verify(objectCall).setEnv(Constants.ARG_CONTEXT, context);
        verify(objectCall).setEnv(eq(Constants.ARG_ARCHITECTURE), any(IArchitecture.class));

        final IMethodCall peripheralCall = mock(IMethodCall.class);
        when(peripheralCall.setEnv(anyString(), any())).thenReturn(peripheralCall);
        assertEquals(peripheralCall, env.addPeripheralArgs(peripheralCall, access, context));
        verify(peripheralCall).setEnv(Constants.ARG_CONVERTER, converter);
        verify(peripheralCall).setEnv(Constants.ARG_CONTEXT, context);
        verify(peripheralCall).setEnv(eq(Constants.ARG_ACCESS), any(IArchitectureAccess.class));
        verify(peripheralCall).setEnv(Constants.ARG_COMPUTER, access);
    }

    @Test
    public void openComputersArchitectureAccess() {
        final IConverter converter = mock(IConverter.class);
        final OpenComputersEnv env = new OpenComputersEnv(converter);

        final Node ownNode = mock(Node.class);
        when(ownNode.address()).thenReturn("own");
        final Node callerNode = mock(Node.class);
        when(callerNode.address()).thenReturn("caller");
        final Context context = mock(Context.class);
        when(context.node()).thenReturn(callerNode);
        when(context.isRunning()).thenReturn(true);
        when(context.signal(eq("event"), any())).thenReturn(true);

        final IArchitectureAccess architecture = env.createAccess(ownNode, context);
        assertEquals("caller", architecture.callerName());
        assertEquals("own", architecture.peripheralName());
        assertTrue(architecture.canSignal());
        assertTrue(architecture.signal("event", "a"));
        verify(context).signal("event", "a");

        final Index index = architecture.createIndex(2);
        assertEquals(2, index.value);
        assertEquals(1, index.offset);
    }

    @Test
    public void openComputersEnvInjectsExpectedVariables() {
        final IConverter converter = mock(IConverter.class);
        final OpenComputersEnv env = new OpenComputersEnv(converter);
        final Node node = mock(Node.class);
        final Context context = mock(Context.class);
        final IMethodCall objectCall = mock(IMethodCall.class);
        when(objectCall.setEnv(anyString(), any())).thenReturn(objectCall);
        assertEquals(objectCall, env.addObjectArgs(objectCall, context));
        verify(objectCall).setEnv(Constants.ARG_CONVERTER, converter);
        verify(objectCall).setEnv(Constants.ARG_CONTEXT, context);
        verify(objectCall).setEnv(eq(Constants.ARG_ARCHITECTURE), any(IArchitecture.class));

        final IMethodCall peripheralCall = mock(IMethodCall.class);
        when(peripheralCall.setEnv(anyString(), any())).thenReturn(peripheralCall);
        assertEquals(peripheralCall, env.addPeripheralArgs(peripheralCall, node, context));
        verify(peripheralCall).setEnv(Constants.ARG_CONVERTER, converter);
        verify(peripheralCall).setEnv(Constants.ARG_CONTEXT, context);
        verify(peripheralCall).setEnv(eq(Constants.ARG_ACCESS), any(IArchitectureAccess.class));
        verify(peripheralCall).setEnv(Constants.ARG_NODE, node);
    }

    @Test
    public void synchronousExecutorQueuesTaskAndReportsResult() throws Exception {
        final ILuaContext context = mock(ILuaContext.class);
        when(context.issueMainThreadTask(any(ILuaTask.class))).thenReturn(7L);

        final MethodResult result = SynchronousExecutor.executeInMainThread(context, new SynchronousExecutor.Task() {

            @Override
            public Object[] execute() {
                return new Object[] { "ok" };
            }
        });

        assertNotNull(result);
        assertFalse(result.isImmediate());

        final ArgumentCaptor<ILuaTask> task = ArgumentCaptor.forClass(ILuaTask.class);
        verify(context).issueMainThreadTask(task.capture());
        Assert.assertArrayEquals(
            new Object[] { "ok" },
            task.getValue()
                .execute());
    }

    @Test
    public void synchronousExecutorConvertsFailuresToLuaExceptions() throws Exception {
        final ILuaContext context = mock(ILuaContext.class);
        when(context.issueMainThreadTask(any(ILuaTask.class))).thenReturn(8L);

        SynchronousExecutor.executeInMainThread(context, new SynchronousExecutor.Task() {

            @Override
            public Object[] execute() {
                throw new IllegalStateException("boom");
            }
        });

        final ArgumentCaptor<ILuaTask> task = ArgumentCaptor.forClass(ILuaTask.class);
        verify(context).issueMainThreadTask(task.capture());
        try {
            task.getValue()
                .execute();
            Assert.fail("Expected LuaException");
        } catch (LuaException expected) {
            Assert.assertTrue(
                expected.getMessage()
                    .contains("boom"));
        }
    }

    @Test
    public void synchronousExecutorDoesNotSwallowErrors() throws Exception {
        final ILuaContext context = mock(ILuaContext.class);
        when(context.issueMainThreadTask(any(ILuaTask.class))).thenReturn(10L);

        SynchronousExecutor.executeInMainThread(context, new SynchronousExecutor.Task() {

            @Override
            public Object[] execute() {
                throw new AssertionError("fatal");
            }
        });

        final ArgumentCaptor<ILuaTask> task = ArgumentCaptor.forClass(ILuaTask.class);
        verify(context).issueMainThreadTask(task.capture());
        try {
            task.getValue()
                .execute();
            Assert.fail("Expected AssertionError");
        } catch (AssertionError expected) {
            assertEquals("fatal", expected.getMessage());
        }
    }

    @Test
    public void synchronousExecutorKeepsLuaExceptions() throws Exception {
        final ILuaContext context = mock(ILuaContext.class);
        when(context.issueMainThreadTask(any(ILuaTask.class))).thenReturn(9L);

        SynchronousExecutor.executeInMainThread(context, new SynchronousExecutor.Task() {

            @Override
            public Object[] execute() throws LuaException {
                throw new LuaException("lua-error");
            }
        });

        final ArgumentCaptor<ILuaTask> task = ArgumentCaptor.forClass(ILuaTask.class);
        verify(context).issueMainThreadTask(task.capture());
        try {
            task.getValue()
                .execute();
            Assert.fail("Expected LuaException");
        } catch (LuaException expected) {
            assertEquals("lua-error", expected.getMessage());
        }
    }
}
