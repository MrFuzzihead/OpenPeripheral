package openperipheral.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import dan200.computercraft.api.filesystem.IMount;
import dan200.computercraft.api.peripheral.IComputerAccess;
import openperipheral.adapter.composed.IndexedMethodMap;
import openperipheral.api.peripheral.PeripheralTypeId;
import openperipheral.interfaces.cc.wrappers.AdapterPeripheral;

public class AdapterPeripheralMountTest {

    @PeripheralTypeId("mount_test")
    public static class MountTarget {
    }

    private static IComputerAccess accessWithUniqueMounts(final Set<String> mounted) {
        final IComputerAccess access = mock(IComputerAccess.class);
        when(access.getAttachmentName()).thenReturn("back");

        when(access.mount(anyString(), any(IMount.class))).thenAnswer(new Answer<String>() {

            @Override
            public String answer(InvocationOnMock invocation) {
                final String name = invocation.getArgument(0);
                if (!mounted.add(name)) throw new IllegalStateException("Duplicate mount: " + name);
                return name;
            }
        });

        doAnswer(new Answer<Void>() {

            @Override
            public Void answer(InvocationOnMock invocation) {
                mounted.remove(invocation.getArgument(0));
                return null;
            }
        }).when(access)
            .unmount(anyString());

        return access;
    }

    private static AdapterPeripheral peripheral() {
        return new AdapterPeripheral(
            new IndexedMethodMap(Collections.<String, openperipheral.adapter.IMethodExecutor>emptyMap()),
            new MountTarget());
    }

    @Test
    public void sharedDocumentationMountIsReferenceCounted() {
        final Set<String> mounted = new HashSet<String>();
        final IComputerAccess access = accessWithUniqueMounts(mounted);

        final AdapterPeripheral first = peripheral();
        final AdapterPeripheral second = peripheral();

        first.attach(access);
        second.attach(access);
        assertEquals(new HashSet<String>(java.util.Arrays.asList("openp", "rom/help/back")), mounted);

        first.detach(access);
        assertTrue(mounted.contains("openp"));
        assertTrue(mounted.contains("rom/help/back"));

        second.detach(access);
        assertTrue(mounted.isEmpty());
    }
}
