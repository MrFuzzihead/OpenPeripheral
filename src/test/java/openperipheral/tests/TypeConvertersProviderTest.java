package openperipheral.tests;

import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.Assert;
import org.junit.Test;

import openperipheral.api.converter.IConverter;
import openperipheral.api.converter.IGenericTypeConverter;
import openperipheral.api.converter.IOutboundTypeConverter;
import openperipheral.converter.TypeConvertersProvider;

public class TypeConvertersProviderTest {

    @Test
    public void convertersAreRegisteredPerArchitecture() {
        final TypeConvertersProvider provider = new TypeConvertersProvider();
        final IConverter computerCraft = mock(IConverter.class);
        final IConverter openComputers = mock(IConverter.class);

        provider.registerConverter("CC", computerCraft);
        provider.registerConverter("OC", openComputers);

        assertSame(computerCraft, provider.getConverter("CC"));
        assertSame(openComputers, provider.getConverter("OC"));

        final IOutboundTypeConverter outbound = mock(IOutboundTypeConverter.class);
        final IGenericTypeConverter generic = mock(IGenericTypeConverter.class);
        provider.register(outbound);
        provider.register(generic);
        provider.registerIgnored(String.class, true);

        verify(computerCraft).register(outbound);
        verify(openComputers).register(outbound);
        verify(computerCraft).register(generic);
        verify(openComputers).register(generic);
        verify(computerCraft).registerIgnored(String.class, true);
        verify(openComputers).registerIgnored(String.class, true);
    }

    @Test(expected = NullPointerException.class)
    public void missingArchitectureIsRejected() {
        new TypeConvertersProvider().getConverter("missing");
    }

    @Test
    public void registeringForUnknownArchitectureIsANoop() {
        final TypeConvertersProvider provider = new TypeConvertersProvider();
        provider.register(mock(IOutboundTypeConverter.class));
        provider.registerIgnored(String.class, false);
        Assert.assertNotNull(provider);
    }
}
