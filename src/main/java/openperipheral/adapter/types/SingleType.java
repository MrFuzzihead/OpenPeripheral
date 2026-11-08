package openperipheral.adapter.types;

import openperipheral.api.adapter.IScriptType;

public class SingleType implements IScriptType {

    public static final IScriptType VOID = new SingleType("()");
    public static final IScriptType WILDCHAR = new SingleType("*");
    public static final IScriptType UNKNOWN = new SingleType("?");
    public static final IScriptType TAIL = new SingleType("...");

    private final String type;

    public SingleType(String type) {
        this.type = type;
    }

    @Override
    public String describe() {
        return type;
    }

}
