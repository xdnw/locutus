package link.locutus.discord.commands.manager.v2.command;

import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.perm.PermissionHandler;
import link.locutus.discord.util.StringMan;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public interface CommandCallable {
    CommandCallable clone(CommandCallable parent, List<String> aliases);

    CommandCallable getParent();

    Object call(ArgumentStack stack);

    String help(ValueStore store);

    String desc(ValueStore store);

    String simpleDesc();

    String simpleHelp();

    List<String> aliases();

    default void validatePermissions(ValueStore store, PermissionHandler permisser) throws IllegalArgumentException {

    }

    default boolean hasPermission(ValueStore store, PermissionHandler permisser) {
        try {
            validatePermissions(store, permisser);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    default String toHtml(ValueStore store, PermissionHandler permisser) {
        return toHtml(store, permisser, null);
    }

    String toHtml(ValueStore store, PermissionHandler permisser, String endpoint);

    default CommandCallable getCallable(List<String> args) {
        return getCallable(new ArrayList<>(args), false);
    }

    default CommandCallable getCallable(List<String> args, boolean allowRemainder) {
        CommandCallable root = this;

        while (!args.isEmpty()) {
            if (root instanceof ParametricCallable) {
                if (!allowRemainder)
                    throw new IllegalArgumentException("Parametric command: " + root.getFullPath() + " has no sub command: " + StringMan.getString(args));
                return root;
            }
            String arg = args.remove(0);
            if (root instanceof CommandGroup) {
                CommandCallable tmp = ((CommandGroup) root).get(arg);
                if (tmp == null) {
                    throw new IllegalArgumentException("Command: " + root.getPrimaryCommandId() + " of type " + root.getClass().getSimpleName() + " has no subcommand matching: " + arg);
                }
                root = tmp;
            } else {
                throw new IllegalArgumentException("Command: " + root.getPrimaryCommandId() + " of type " + root.getClass().getSimpleName() + " has no subcommand: " + arg);
            }
        }
        return root;
    }

    default String getFullPath() {
        return getFullPath(" ");
    }

    default String getFullPath(String delim) {
        List<String> pathList = new ArrayList<>();
        CommandCallable root = this;
        while (root != null) {
            if (root.getPrimaryCommandId() != null && !root.getPrimaryCommandId().isEmpty()) {
                pathList.add(root.getPrimaryCommandId());
            }
            root = root.getParent();
        }
        Collections.reverse(pathList);
        return StringMan.join(pathList, delim);
    }

    default String getPrimaryCommandId() {
        if (aliases().isEmpty()) return "";
        return aliases().get(0);
    }

    default Set<ParametricCallable> getParametricCallables(Predicate<ParametricCallable> returnIf) {
        return Collections.emptySet();
    }

    default void generatePojo(String parentPath, StringBuilder output, int indent) {
        String indentStr = StringMan.repeat(" ", indent);
        String name = getPrimaryCommandId();
        String path = (parentPath + " " + name).trim();
        if (this instanceof CommandGroup group) {
            if (!name.isEmpty()) output.append(indentStr).append("public static class ").append(name).append("{\n");
            for (CommandCallable callable : new HashSet<>(group.getSubcommands().values())) {
                callable.generatePojo(path, output, indent + 4);
            }
            if (!name.isEmpty()) output.append(indentStr).append("}\n");
        } else if (this instanceof ParametricCallable callable) {
            Method method = callable.getMethod();
            List<String> params = callable.getUserParameterMap().values().stream().map(ParameterData::getName).toList();
            // join with comma
            String typeArgs = params.stream().map(f -> "String " + f).collect(Collectors.joining(", "));
            String args = params.stream().map(f -> "\"" + f + "\", " + f).collect(Collectors.joining(", "));

            Class<?> clazz = method.getDeclaringClass();
            String className = clazz.getName();
            String fieldExt = "";
            if (className.contains("$")) {
                String[] split = className.split("\\$");
                try {
                    for (Field field : Class.forName(split[0]).getDeclaredFields()) {
                        if (!Modifier.isStatic(field.getModifiers())) {
                            continue;
                        }
                        Object instance = instance = field.get(null);
                        if (instance == callable.getObject()) {
                            fieldExt = ", field=\"" + field.getName() + "\"";
                            className = split[0];
                            break;
                        }
                    }
                } catch (IllegalAccessException | ClassNotFoundException e) {
                    throw new RuntimeException(e);
                }
                if (fieldExt.isEmpty()) {
                    throw new IllegalArgumentException("Cannot find class " + className + " for " + method.getName() + " in " + clazz.getName());
                }
            }

            output.append(String.format("""
                            %1$s@AutoRegister(clazz=%2$s.class,method="%3$s"%7$s)
                            %1$spublic static class %4$s extends CommandRef {
                            %1$s    public static final %4$s cmd = new %4$s();
                            %1$s    public %4$s create(%5$s) {
                            %1$s        return createArgs(%6$s);
                            %1$s    }
                            %1$s}
                            """,
                    indentStr, className, method.getName(), name, typeArgs, args, fieldExt));
        } else {
            throw new IllegalArgumentException("Unknown callable type: " + this.getClass().getSimpleName());
        }
    }
}
