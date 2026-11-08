package openperipheral.api.peripheral;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import openperipheral.api.Constants;
import openperipheral.api.adapter.AdapterSourceName;

/**
 * Used for creating custom names for peripherals. Useable only on TileEntities. Will also double as @link
 * {@link AdapterSourceName}.
 * For alternative method, see comment for {@link Constants#IMC_NAME_CLASS}
 * <p>
 * <strong>Deprecated / superseded note:</strong> for inventory ({@code IInventory}) and fluid
 * ({@code IFluidHandler}) tiles, this naming is shadowed by ComputerCraft 3.3.1+'s native generic
 * peripherals ({@code inventory}, {@code fluid_storage}). Names are kept for the OpenComputers
 * path and backward compatibility; OP cannot override CC's native peripherals.
 * </p>
 *
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface PeripheralTypeId {

    public String value();
}
