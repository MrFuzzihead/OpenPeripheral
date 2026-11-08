package openperipheral;

import org.junit.Test;

public class ApiSetupTest {

    @Test
    public void setupApisRegistersTheDefaultProviders() {
        new ApiSetup().setupApis();
    }
}
