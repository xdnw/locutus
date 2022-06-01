package link.locutus.discord.util.math;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public class ReflectionUtil {
    public static Class getClassType(Type type) {
        if (type instanceof Class) {
            return (Class) type;
        }
        if (type instanceof ParameterizedType) {
            return getClassType(((ParameterizedType) type).getRawType());
        }
        return null;
    }
}
