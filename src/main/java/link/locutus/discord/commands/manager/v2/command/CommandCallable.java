package link.locutus.discord.commands.manager.v2.command;

import com.google.gson.JsonObject;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.WebStore;
import link.locutus.discord.commands.manager.v2.perm.PermissionHandler;
import link.locutus.discord.util.FileUtil;
import link.locutus.discord.util.StringMan;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.StandardOpenOption;
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

    JsonObject toJson(PermissionHandler permHandler);

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

    default String toHtml(WebStore store, PermissionHandler permisser, boolean sse) {
        return toHtml(store, permisser, null, sse);
    }

    String toHtml(WebStore store, PermissionHandler permisser, String endpoint, boolean sse);

    default CommandCallable getCallable(List<String> args) {
        return getCallable(new ArrayList<>(args), false);
    }

    default CommandCallable getCallable(String fullCommand, StringBuilder remainder) {
        if (!(this instanceof CommandGroup)) {
            remainder.append(fullCommand);
            return this;
        }
        CommandGroup root = (CommandGroup) this;
        while (!fullCommand.isEmpty()) {
            String rootRemaining = fullCommand;

            int index = fullCommand.indexOf(' ');
            if (index == -1) index = fullCommand.length();
            String word = fullCommand.substring(0, index);
            fullCommand = fullCommand.substring(index).trim();

            CommandCallable subCommand = root.get(word);
            if (subCommand == null) {
                remainder.append(rootRemaining);
                return root;
            }
            if (subCommand instanceof CommandGroup) {
                root = (CommandGroup) subCommand;
            } else {
                remainder.append(fullCommand);
                return subCommand;
            }
        }
        return root;
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
                    throw new IllegalArgumentException("Command: " + root.getPrimaryCommandId() + " of type " + root.getClass().getSimpleName() + " has no subcommand matching: " + arg + "\n" +
                            "Available subcommands: " + ((CommandGroup) root).getSubcommands().keySet());
                }
                root = tmp;
            } else {
                throw new IllegalArgumentException("Command: " + root.getPrimaryCommandId() + " of type " + root.getClass().getSimpleName() + " has no subcommands. Arg: " + arg);
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

    default void savePojo(File root, Class clazz, String renameTo) throws IOException {
        if (root == null) {
            root = new File("src/main/java");
        }
        if (renameTo == null) {
            renameTo = clazz.getSimpleName();
        }
        // get the path for the class
        File javaFilePath = new File(root, clazz.getPackageName().replace('.', '/'));
        File javaFile = new File(javaFilePath, renameTo + ".java");

        String header = """
                package {package};
                import link.locutus.discord.commands.manager.v2.command.AutoRegister;
                import link.locutus.discord.commands.manager.v2.command.CommandRef;
                public class""";
        header = header.replace("{package}", clazz.getPackageName()) + " " + renameTo + " {\n";

        StringBuilder output = new StringBuilder(header);
        generatePojo("", output, 4);
        output.append("\n}\n");


        // save to javaFile
        // create if not exist using open option java
        Files.write(javaFile.toPath(), output.toString().getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    default void generatePojo(String parentPath, StringBuilder output, int indent) {
        String indentStr = StringMan.repeat(" ", indent);
        String name = getPrimaryCommandId();
        String path = (parentPath + " " + name).trim();
        if (this instanceof CommandGroup group) {
            if (!name.isEmpty()) output.append(indentStr).append("public static class ").append(name).append("{\n");
            List<CommandCallable> subCommands = new ArrayList<>(new HashSet<>(group.getSubcommands().values()));
            subCommands.sort(Comparator.comparing(CommandCallable::getPrimaryCommandId));
            for (CommandCallable callable : subCommands) {
                callable.generatePojo(path, output, indent + 4);
            }
            if (!name.isEmpty()) output.append(indentStr).append("}\n");
        } else if (this instanceof ParametricCallable callable) {
            Method method = callable.getMethod();
            List<String> params = callable.getUserParameterMap().values().stream().map(ParameterData::getName).toList();

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
                        field.setAccessible(true);
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

            List<String> argMethods = new ArrayList<>();
            for (String arg : params) {
                argMethods.add(String.format("""
                        %1$spublic %2$s %3$s(String value) {
                        %1$s    return set("%3$s", value);
                        %1$s}
                        """, indentStr, name, arg));
            }

            output.append(String.format("""
                            %1$s@AutoRegister(clazz=%2$s.class,method="%3$s"%5$s)
                            %1$spublic static class %4$s extends CommandRef {
                            %1$s    public static final %4$s cmd = new %4$s();
                            %6$s
                            %1$s}
                            """,
                    indentStr, className, method.getName(), name, fieldExt, String.join("\n", argMethods)));
        } else {
            throw new IllegalArgumentException("Unknown callable type: " + this.getClass().getSimpleName());
        }
    }
}
