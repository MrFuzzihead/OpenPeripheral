package openperipheral.tests;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Vec3;

import org.junit.Assert;
import org.junit.Test;

import openperipheral.api.meta.IEntityCustomMetaProvider;
import openperipheral.api.meta.IEntityMetaProvider;
import openperipheral.api.meta.IItemStackMetaProvider;
import openperipheral.meta.EntityMetadataBuilder;
import openperipheral.meta.ItemStackMetadataBuilder;
import openperipheral.meta.MetaProvidersRegistry;

public class MetadataTest {

    private static class SimpleEntityProvider implements IEntityMetaProvider<Entity> {

        private final String key;
        private final Object value;

        private SimpleEntityProvider(String key, Object value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public Class<? extends Entity> getTargetClass() {
            return Entity.class;
        }

        @Override
        public String getKey() {
            return key;
        }

        @Override
        public Object getMeta(Entity target, Vec3 relativePos) {
            return value;
        }
    }

    private static class ApplicableProvider extends SimpleEntityProvider implements IEntityCustomMetaProvider<Entity> {

        private final boolean applies;

        private ApplicableProvider(String key, Object value, boolean applies) {
            super(key, value);
            this.applies = applies;
        }

        @Override
        public boolean canApply(Entity target) {
            return applies;
        }
    }

    private static Entity mockEntity() {
        final Entity entity = mock(Entity.class);
        when(entity.getCommandSenderName()).thenReturn("TestEntity");
        when(entity.getEntityId()).thenReturn(42);
        when(entity.getUniqueID()).thenReturn(UUID.randomUUID());
        return entity;
    }

    @Test
    public void entityBasicMetadata() {
        final EntityMetadataBuilder builder = new EntityMetadataBuilder();
        final Entity entity = mockEntity();

        final Map<String, Object> basic = builder.getBasicEntityMetadata(entity, null);
        Assert.assertEquals("TestEntity", basic.get("name"));
        Assert.assertEquals(42, basic.get("id"));

        @SuppressWarnings("unchecked")
        final Map<String, Double> position = (Map<String, Double>) basic.get("position");
        Assert.assertEquals(0.0, position.get("x"), 0.0001);
        Assert.assertEquals(0.0, position.get("y"), 0.0001);
        Assert.assertEquals(0.0, position.get("z"), 0.0001);
    }

    @Test
    public void customEntityProviderIsRemovedWhenItDoesNotApply() {
        final String appliedKey = "test_applied_" + UUID.randomUUID();
        final String skippedKey = "test_skipped_" + UUID.randomUUID();

        final EntityMetadataBuilder builder = new EntityMetadataBuilder();
        builder.register(new ApplicableProvider(appliedKey, "yes", true));
        builder.register(new ApplicableProvider(skippedKey, "no", false));

        final Entity entity = mockEntity();
        final Map<String, Object> metadata = builder.getEntityMetadata(entity, null);

        Assert.assertEquals("yes", metadata.get(appliedKey));
        Assert.assertFalse(metadata.containsKey(skippedKey));

        final Set<String> keys = builder.getKeys(entity);
        Assert.assertTrue(keys.contains(appliedKey));
        Assert.assertFalse(keys.contains(skippedKey));
    }

    @Test
    public void entityProviderRegistryRejectsDuplicateKeys() {
        final MetaProvidersRegistry<IEntityMetaProvider<?>> registry = new MetaProvidersRegistry<IEntityMetaProvider<?>>(
            "test-entity",
            Entity.class);

        registry.addProvider(new SimpleEntityProvider("duplicate", "a"));
        registry.addProvider(new SimpleEntityProvider("duplicate", "b"));

        try {
            registry.getProviders(Entity.class);
            Assert.fail("Expected duplicate key rejection");
        } catch (IllegalStateException expected) {
            // expected
        }
    }

    @Test
    public void itemMetadataHandlesNullStack() {
        final ItemStackMetadataBuilder builder = new ItemStackMetadataBuilder();
        Assert.assertEquals(
            "invalid",
            builder.getBasicItemStackMetadata(null)
                .get("id"));
        Assert.assertEquals(
            "invalid",
            builder.getItemStackMetadata(null)
                .get("id"));
        Assert.assertNull(builder.getItemStackMetadata("anything", null));
        Assert.assertTrue(
            builder.getKeys(null)
                .isEmpty());
    }

    @Test
    public void rawItemNameLowercasesWithEnglishLocale() throws Exception {
        final ItemStack stack = mock(ItemStack.class);
        when(stack.getUnlocalizedName()).thenReturn("TILE.Something");

        Assert.assertEquals("tile.something", ItemStackMetadataBuilder.getRawNameForStack(stack));
    }

    @Test
    public void itemProxyReturnsNullWhenStackHasNoItem() {
        final ItemStack stack = mock(ItemStack.class);
        when(stack.getItem()).thenReturn(null);
        Assert.assertNull(new ItemStackMetadataBuilder().createProxy(stack));
    }

    @Test
    public void itemProviderRegistryRejectsInvalidTarget() {
        final MetaProvidersRegistry<IItemStackMetaProvider<?>> registry = new MetaProvidersRegistry<IItemStackMetaProvider<?>>(
            "test-item",
            Item.class);
        try {
            registry.addProvider(new IItemStackMetaProvider<Object>() {

                @Override
                public Class<? extends Object> getTargetClass() {
                    return Object.class;
                }

                @Override
                public String getKey() {
                    return "bad";
                }

                @Override
                public Object getMeta(Object target, ItemStack stack) {
                    return null;
                }
            });
            Assert.fail("Expected invalid target rejection");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

}
