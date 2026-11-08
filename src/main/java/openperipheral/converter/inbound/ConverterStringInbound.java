package openperipheral.converter.inbound;

import java.lang.reflect.Type;
import java.util.UUID;

import openperipheral.api.converter.IConverter;
import openperipheral.api.converter.IGenericInboundTypeConverter;

public class ConverterStringInbound implements IGenericInboundTypeConverter {

    @Override
    public Object toJava(IConverter converter, Object obj, Type expected) {
        if (expected != String.class) return null;
        if (obj == null) return null;

        // Deliberately not String.valueOf(Map/Collection/array): a Lua table passed to a string argument is a
        // script error, not an implicit toString().
        if (obj instanceof CharSequence || obj instanceof Number
            || obj instanceof Boolean
            || obj instanceof Character
            || obj instanceof Enum
            || obj instanceof UUID) return String.valueOf(obj);

        return null;
    }
}
