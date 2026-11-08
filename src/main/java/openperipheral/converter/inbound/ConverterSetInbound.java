package openperipheral.converter.inbound;

import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.google.common.base.Strings;
import com.google.common.collect.Sets;
import com.google.common.reflect.TypeToken;

import openperipheral.api.converter.IConverter;
import openperipheral.api.converter.IGenericInboundTypeConverter;
import openperipheral.converter.TypeConverter;

public class ConverterSetInbound implements IGenericInboundTypeConverter {

    private static final TypeVariable<?> TYPE_PARAM = Set.class.getTypeParameters()[0];

    private static boolean isTruthish(Object v) {
        if (v == null) return false;
        if (v instanceof Boolean) return (Boolean) v;
        if (v instanceof Number) return ((Number) v).doubleValue() != 0;
        if (v instanceof String) return !Strings.isNullOrEmpty((String) v);
        if (v instanceof Map) return !((Map<?, ?>) v).isEmpty();
        if (v instanceof Collection) return !((Collection<?>) v).isEmpty();

        return false;
    }

    @Override
    public Object toJava(IConverter registry, Object obj, Type expected) {
        if (obj instanceof Map) {
            final TypeToken<?> type = TypeToken.of(expected);
            final Class<?> rawType = type.getRawType();
            if (rawType.isAssignableFrom(HashSet.class) || rawType.isAssignableFrom(TreeSet.class)
                || (!rawType.isInterface() && !Modifier.isAbstract(rawType.getModifiers())
                    && Set.class.isAssignableFrom(rawType))) {
                final Type valueType = type.resolveType(TYPE_PARAM)
                    .getType();

                final Set<Object> result;
                if (rawType.isAssignableFrom(HashSet.class)) result = Sets.newHashSet();
                else if (rawType.isAssignableFrom(TreeSet.class)) result = new TreeSet<Object>();
                else {
                    try {
                        @SuppressWarnings("unchecked")
                        final Set<Object> instance = (Set<Object>) rawType.newInstance();
                        result = instance;
                    } catch (Exception e) {
                        throw new IllegalArgumentException("Failed to create set implementation " + rawType, e);
                    }
                }

                for (Map.Entry<?, ?> e : ((Map<?, ?>) obj).entrySet()) {
                    final Object value = e.getKey();

                    Object marker = e.getValue();
                    if (isTruthish(marker)) {
                        Object converted = TypeConverter.nullableToJava(registry, value, valueType);
                        result.add(converted);
                    }
                }

                return result;
            }
        }

        return null;
    }

}
