package link.locutus.discord.util.math;

import com.google.gson.reflect.TypeToken;
import link.locutus.discord.commands.manager.v2.binding.bindings.Placeholders;

import java.lang.reflect.*;
import java.util.Arrays;
import java.util.List;

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

    public static Type buildNestedType(Class<?>... sequence) {
        if (sequence == null || sequence.length == 0) {
            throw new IllegalArgumentException("Empty type sequence");
        }
        Cursor cursor = new Cursor();
        Type result = parse(sequence, cursor);
        if (cursor.pos != sequence.length) {
            throw new IllegalArgumentException("Unused trailing classes starting at index " + cursor.pos + "\n" +
                    "- Used: " + String.join(", ", Arrays.stream(sequence).limit(cursor.pos).map(Class::getSimpleName).toList()) + "\n" +
                    "- Unused: " + String.join(", ", Arrays.stream(sequence).skip(cursor.pos).map(Class::getSimpleName).toList()));
        }
        return result;
    }

    private static final List<?> holder = null;
    private static final Type wildcard;
    static
    {
        try {
            Field f = ReflectionUtil.class.getDeclaredField("holder");
            Type listType = f.getGenericType();                         // ParameterizedType
            ParameterizedType listPT = (ParameterizedType) listType;
            wildcard = listPT.getActualTypeArguments()[0];
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    private static Type parse(Class<?>[] seq, Cursor c) {
        if (c.pos >= seq.length) {
            throw new IllegalArgumentException("Unexpected end of sequence");
        }
        Class<?> raw = seq[c.pos++];

        int paramCount = Math.min(raw.getTypeParameters().length, seq.length - c.pos);
        if (paramCount == 0) {
            if (raw == WildcardType.class) {
                return wildcard;
            }
            return raw;
        }
        Type[] args = new Type[paramCount];
        for (int i = 0; i < paramCount; i++) {
            args[i] = parse(seq, c);
        }
        return TypeToken.getParameterized(raw, args).getType();
    }

    private static final class Cursor {
        int pos = 0;
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
