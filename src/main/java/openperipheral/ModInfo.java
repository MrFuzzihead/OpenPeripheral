package openperipheral;

public class ModInfo {

    public static final String ID = "OpenPeripheralCore";
    public static final String API_ID = "OpenPeripheralApi";
    public static final String NAME = "OpenPeripheralCore";
    public static final String VERSION = Tags.VERSION;
    public static final String API_VERSION = Tags.OP_API_VERSION;
    public static final String PROXY_SERVER = "openperipheral.core.CommonProxy";
    public static final String PROXY_CLIENT = "openperipheral.core.client.ClientProxy";
    public static final String DEPENDENCIES = "required-after:OpenMods@[" + Tags.OPENMODS_LIB_VERSION
        + ","
        + Tags.OPENMODS_LIB_NEXT_VERSION
        + ");after:ComputerCraft@[1.70,];after:OpenComputers@[1.5.0,];";
}
