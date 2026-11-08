package openperipheral.converter.inbound;

import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import com.google.common.collect.Maps;
import com.google.common.reflect.TypeToken;

import openmods.reflection.TypeUtils;
import openperipheral.api.converter.IConverter;
import openperipheral.api.converter.IGenericInboundTypeConverter;
import openperipheral.converter.TypeConverter;

public class ConverterMapInbound implements IGenericInboundTypeConverter {

    @Override
    public Object toJava(IConverter registry, Object obj, Type expected) {

        if (obj instanceof Map) {
            final TypeToken<?> type = TypeToken.of(expected);
            final Class<?> rawType = type.getRawType();
            if (rawType.isAssignableFrom(HashMap.class) || rawType.isAssignableFrom(TreeMap.class)
                || (!rawType.isInterface() && !Modifier.isAbstract(rawType.getModifiers())
                    && Map.class.isAssignableFrom(rawType))) {
                final Type keyType = type.resolveType(TypeUtils.MAP_KEY_PARAM)
                    .getType();
                final Type valueType = type.resolveType(TypeUtils.MAP_VALUE_PARAM)
                    .getType();

                final Map<Object, Object> result;
                if (rawType.isAssignableFrom(HashMap.class)) result = Maps.newHashMap();
                else if (rawType.isAssignableFrom(TreeMap.class)) result = new TreeMap<Object, Object>();
                else {
                    try {
                        @SuppressWarnings("unchecked")
                        final Map<Object, Object> instance = (Map<Object, Object>) rawType.newInstance();
                        result = instance;
                    } catch (Exception e) {
                        throw new IllegalArgumentException("Failed to create map implementation " + rawType, e);
                    }
                }

                for (Map.Entry<?, ?> e : ((Map<?, ?>) obj).entrySet()) {
                    Object key = TypeConverter.nullableToJava(registry, e.getKey(), keyType);
                    Object value = TypeConverter.nullableToJava(registry, e.getValue(), valueType);
                    result.put(key, value);
                }

                return result;
            }
        }

        return null;
    }

}
