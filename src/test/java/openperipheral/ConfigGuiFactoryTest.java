package openperipheral;

import org.junit.Assert;
import org.junit.Test;

import openperipheral.adapter.FeatureGroupManager;

public class ConfigGuiFactoryTest {

    @Test
    public void featureGroupElementUsesAndMutatesTheConfigCopy() {
        final String group = "gametest_config_" + System.nanoTime();
        final String architecture = "ComputerCraft";

        FeatureGroupManager.INSTANCE.ensureExists(group);
        FeatureGroupManager.INSTANCE.disable(group, architecture);

        try {
            new ConfigGuiFactory().initialize(null);
            final ConfigGuiFactory.FeatureConfigElement element = new ConfigGuiFactory.FeatureConfigElement(
                architecture,
                group);

            Assert.assertEquals(Boolean.FALSE, element.get());
            element.set(Boolean.TRUE);
            Assert.assertEquals(Boolean.TRUE, element.get());
            Assert.assertEquals(Boolean.FALSE, element.getDefault());
        } finally {
            FeatureGroupManager.INSTANCE.enable(group, architecture);
        }
    }
}
