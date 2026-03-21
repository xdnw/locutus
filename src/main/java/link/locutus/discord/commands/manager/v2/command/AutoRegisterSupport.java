package link.locutus.discord.commands.manager.v2.command;

import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.bindings.PlaceholderRegistry;
import link.locutus.discord.commands.manager.v2.binding.bindings.Placeholders;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

final class AutoRegisterSupport {
    static final String PLACEHOLDER_FIELD_PREFIX = "placeholder:";

    private AutoRegisterSupport() {
    }

    static OwnerReference describeOwner(Method method, Object owner) {
        String className = method.getDeclaringClass().getName();
        if (!className.contains("$")) {
            return new OwnerReference(className, "");
        }

        String outerClassName = className.substring(0, className.indexOf('$'));
        String staticFieldName = findStaticOwnerField(outerClassName, owner);
        if (staticFieldName != null) {
            return new OwnerReference(outerClassName, staticFieldName);
        }

        if (owner instanceof Placeholders<?, ?> placeholders) {
            return new OwnerReference(
                    outerClassName,
                    PLACEHOLDER_FIELD_PREFIX + placeholders.getType().getName());
        }

        throw new IllegalArgumentException(
                "Cannot find stable owner for " + method.getDeclaringClass().getName() + "#" + method.getName());
    }

    static Object resolveDynamicOwner(ValueStore store, AutoRegister methodInfo, Class<?> generatedClass) {
        PlaceholderRegistry registry = PlaceholderRegistry.resolve(store);
        if (registry == null) {
            return null;
        }

        String field = methodInfo.field();
        if (field.startsWith(PLACEHOLDER_FIELD_PREFIX)) {
            String typeName = field.substring(PLACEHOLDER_FIELD_PREFIX.length());
            try {
                Class<?> type = Class.forName(typeName);
                Placeholders<?, ?> placeholders = registry.get(type);
                if (declaresCommand(placeholders, methodInfo.method())) {
                    return placeholders;
                }
            } catch (ClassNotFoundException ignored) {
                return null;
            }
        }

        if (!PlaceholdersMap.class.equals(methodInfo.clazz())) {
            return null;
        }

        String commandRoot = generatedClass.getSimpleName();
        for (Class<?> type : registry.getTypes()) {
            if (!PlaceholdersMap.getClassName(type).equalsIgnoreCase(commandRoot)) {
                continue;
            }
            Placeholders<?, ?> placeholders = registry.get(type);
            if (declaresCommand(placeholders, methodInfo.method())) {
                return placeholders;
            }
        }
        return null;
    }

    private static boolean declaresCommand(Placeholders<?, ?> placeholders, String methodName) {
        if (placeholders == null) {
            return false;
        }
        Class<?> placeholderClass = placeholders.getClass();
        for (Method method : placeholderClass.getMethods()) {
            if (method.getDeclaringClass() != placeholderClass) {
                continue;
            }
            if (!method.getName().equalsIgnoreCase(methodName)) {
                continue;
            }
            if (method.getAnnotation(Command.class) != null) {
                return true;
            }
        }
        return false;
    }

    private static String findStaticOwnerField(String outerClassName, Object owner) {
        try {
            Class<?> outerClass = Class.forName(outerClassName);
            for (Field field : outerClass.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                field.setAccessible(true);
                if (field.get(null) == owner) {
                    return field.getName();
                }
            }
        } catch (ClassNotFoundException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    static final class OwnerReference {
        private final String ownerClassName;
        private final String fieldValue;

        private OwnerReference(String ownerClassName, String fieldValue) {
            this.ownerClassName = ownerClassName;
            this.fieldValue = fieldValue;
        }

        String ownerClassName() {
            return ownerClassName;
        }

        String fieldValue() {
            return fieldValue;
        }
    }
}