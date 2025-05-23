package link.locutus.discord.util.math;

import link.locutus.discord.commands.manager.v2.binding.bindings.Placeholders;

import java.lang.reflect.Method;
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

    public static Class<?> getWrapperClass(Class<?> primitiveClass) {
        if (primitiveClass == int.class) return Integer.class;
        if (primitiveClass == boolean.class) return Boolean.class;
        if (primitiveClass == byte.class) return Byte.class;
        if (primitiveClass == short.class) return Short.class;
        if (primitiveClass == long.class) return Long.class;
        if (primitiveClass == float.class) return Float.class;
        if (primitiveClass == double.class) return Double.class;
        if (primitiveClass == char.class) return Character.class;
        return null;
    }

    public static Object castToPrimitive(Object obj, Class<?> primitiveClass) {
        if (primitiveClass == int.class && obj instanceof Integer) {
            return (Integer) obj;
        } else if (primitiveClass == boolean.class && obj instanceof Boolean) {
            return (Boolean) obj;
        } else if (primitiveClass == byte.class && obj instanceof Byte) {
            return (Byte) obj;
        } else if (primitiveClass == short.class && obj instanceof Short) {
            return (Short) obj;
        } else if (primitiveClass == long.class && obj instanceof Long) {
            return (Long) obj;
        } else if (primitiveClass == float.class && obj instanceof Float) {
            return (Float) obj;
        } else if (primitiveClass == double.class && obj instanceof Double) {
            return (Double) obj;
        } else if (primitiveClass == char.class && obj instanceof Character) {
            return (Character) obj;
        }

        return null; // Return null if the casting is not possible
    }

    public static Method getMethodByName(Class<? extends Placeholders> aClass, String create) {
        for (Method method : aClass.getDeclaredMethods()) {
            if (method.getName().equals(create)) {
                return method;
            }
        }
        return null;
    }
}
