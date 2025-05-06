package link.locutus.discord.commands.manager.v2.command;

import gg.jte.generated.precompiled.command.JtecommandgroupGenerated;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import link.locutus.discord.Logg;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.WebStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Arg;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.validator.ValidatorStore;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.manager.v2.perm.PermissionHandler;
import link.locutus.discord.config.yaml.file.YamlConfiguration;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.scheduler.KeyValue;
import org.apache.commons.lang3.ArrayUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class CommandGroup implements ICommandGroup {
    private final ValueStore store;
    private final ValidatorStore validators;
    private final CommandCallable parent;
    private final List<String> aliases;
    private final Map<String, CommandCallable> subcommands = new ConcurrentHashMap<>();
    private String help, desc;

    public CommandGroup(CommandCallable parent, String[] aliases, ValueStore store, ValidatorStore validators) {
        this.store = store;
        this.validators = validators;
        this.parent = parent;
        this.aliases = Arrays.asList(aliases);
    }

    public Map.Entry<CommandCallable, String> getCallableAndPath(List<String> args) {
        CommandCallable root = this;
        List<String> path = new ArrayList<>();

        Queue<String> stack = new ArrayDeque<>(args);
        while (!stack.isEmpty()) {
            String arg = stack.poll();
            path.add(arg);
            if (root instanceof CommandGroup cg && cg.get(arg) != null) {
                root = cg.get(arg);
            } else {
                String primary = root.getPrimaryCommandId();
                if (primary.isEmpty()) primary = "/";
                String msg = "Command: `" + primary + "` of type " + root.getClass().getSimpleName() + " has no subcommand: `" + arg + "`";
                if (root instanceof CommandGroup group) {
                    msg += "\nValid subcommands: `" + StringMan.join(group.primarySubCommandIds(), ", ") + "`";
                }
                throw new IllegalArgumentException(msg);
            }
        }
        return new KeyValue<>(root, StringMan.join(path, " "));
    }

    @Override
    public boolean isViewable() {
        return false;
    }

    @Override
    public Map<String, Object> toJson(PermissionHandler permHandler, boolean includeReturnType) {
        Map<String, Object> root = new LinkedHashMap<>();

        for (Map.Entry<String, CommandCallable> entry : getSubcommandsSorted().entrySet()) {
            root.put(entry.getKey(), entry.getValue().toJson(permHandler, includeReturnType));
        }
        return root;
    }

    public static CommandGroup createRoot(ValueStore store, ValidatorStore validators) {
        return new CommandGroup(null, new String[0], store, validators);
    }

    @Override
    public CommandCallable clone(CommandCallable parent, List<String> aliases) {
        return new CommandGroup(parent, aliases.toArray(new String[0]), store, validators);
    }

    public void setHelp(String help) {
        this.help = help;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    @Override
    public String simpleDesc() {
        return desc == null ? "" : desc;
    }

    @Override
    public String simpleHelp() {
        return help == null ? "" : help;
    }

    @Override
    public Map<String, CommandCallable> getSubcommands() {
        return subcommands;
    }

    @Override
    public List<String> aliases() {
        return this.aliases;
    }

    @Override
    public String getPrimaryCommandId() {
        return this.aliases.isEmpty() ? "" : this.aliases.get(0);
    }

    @Override
    public CommandCallable getParent() {
        return parent;
    }

    public CommandGroup register(CommandCallable command, String... aliases) {
        for (String alias : aliases) {
            subcommands.put(alias.toLowerCase(), command);
        }
        return this;
    }

    private void register(ParametricCallable cmd, List<String> aliases) {
        register(cmd, aliases.toArray(new String[0]));
    }

    private void registerWithPath(CommandCallable callable, List<String> path, String... aliases) {
        if (path.isEmpty()) {
            callable = callable.clone(this, Arrays.asList(aliases));
            register(callable, aliases);
            return;
        }
        String path0 = path.get(0);
        CommandCallable existing = get(path0);
        if (existing == null) {
            existing = new CommandGroup(this, new String[]{path0}, store, validators);
            subcommands.put(path0, existing);
        }
        if (existing instanceof CommandGroup group) {
            group.registerWithPath(callable, path.subList(1, path.size()), aliases);
        } else {
            throw new IllegalArgumentException("Cannot register " + StringMan.getString(aliases) + " at " + getFullPath() + " " + StringMan.getString(path) + " because " + existing.getPrimaryCommandId() + " already exists there");
        }
    }

    public void registerCommandsClass(Class clazz) {
        List<ParametricCallable> cmds = ParametricCallable.generateFromClass(this, clazz, null, store);
        for (ParametricCallable cmd : cmds) {
            register(cmd, cmd.aliases());
        }
    }

    public void registerCommands(Object object) {
        List<ParametricCallable> cmds = ParametricCallable.generateFromObj(this, object, store);
        for (ParametricCallable cmd : cmds) {
            register(cmd, cmd.aliases());
        }
    }

    public void registerMethod(Object object, List<String> path, String methodName, String commandName) {
        Method found = null;
        Class<?> clazz = object.getClass();
        for (Method method : clazz.getMethods()) {
            if (method.getDeclaringClass() != clazz) continue;
            if (method.getName().equals(methodName)) {
                if (found != null) throw new IllegalStateException("Duplicate method found for: " + methodName);
                found = method;
            }
        }
        if (found == null) throw new IllegalArgumentException("No method found for " + methodName);

        ParametricCallable parametric = ParametricCallable.generateFromMethod(parent, object, found, store);
        if (commandName == null) {
            assert parametric != null;
            commandName = parametric.getPrimaryCommandId();
        }
        registerWithPath(parametric, path, commandName);
    }

    public void registerSubCommands(Object object, String... aliases) {
        for (String alias : aliases) {
            CommandCallable cmd = get(alias);
            if (cmd == null) {
                cmd = new CommandGroup(this, ArrayUtils.toArray(alias), store, validators);
                register(cmd, alias);
            }
            if (cmd instanceof CommandGroup group) {
                group.registerCommands(object);
            } else {
                throw new IllegalArgumentException("Cannot register " + StringMan.getString(aliases) + " at " + getFullPath() + " because primary command " + cmd.getPrimaryCommandId() + " already exists there");
            }
        }
    }

    public CommandGroup createSubGroup(String... aliases) {
        return new CommandGroup(this, aliases, store, validators);
    }


    @Override
    public Object call(ArgumentStack stack) {
        if (!stack.hasNext()) {
            String prefix = "/" + getFullPath();
            throw new CommandUsageException(this, "No subcommand specified (2). Valid subcommands\n" +
                    "- `" + prefix + StringMan.join(primarySubCommandIds(), "`\n- `" + prefix) + "`");
        }
        String arg = stack.consumeNext();
        CommandCallable subcommand = subcommands.get(arg.toLowerCase());
        if (subcommand == null) {
            throw new CommandUsageException(this, "Invalid subcommand: `" + arg + "`\n" +
                    "Valid subcommands:\n- " + StringMan.join(primarySubCommandIds(), "\n- "));
        }
        return subcommand.call(stack);
    }

    @Override
    public String help(ValueStore store) {
        return getFullPath() + " <subcommand>";
    }

    @Override
    public String desc(ValueStore store) {
        return "- " + StringMan.join(primarySubCommandIds(), "\n- ");
//        return null;
    }

    public Map<String, CommandCallable> getAllowedCommands(ValueStore store, PermissionHandler permHandler) {
        Map<String, CommandCallable> allowed = new LinkedHashMap<>();
        for (Map.Entry<String, CommandCallable> entry : subcommands.entrySet()) {
            String id = entry.getKey();
            CommandCallable cmd = entry.getValue();
            if (cmd.hasPermission(store, permHandler)) {
                allowed.put(id.toLowerCase(), cmd);
            }
        }
        return allowed;
    }

    @Override
    public String toHtml(WebStore ws, PermissionHandler permHandler, String endpoint, boolean sse) {
        Map<String, CommandCallable> allowed = getAllowedCommands(ws.store(), permHandler);
        return WebStore.render(f -> JtecommandgroupGenerated.render(f, null, ws, this, allowed, endpoint == null ? "" : endpoint));
    }

    public CommandCallable get(String arg0) {
        return subcommands.get(arg0.toLowerCase());
    }

    public CommandCallable get(List<String> args) {
        CommandCallable cmd = get(args.get(0));
        if (args.size() > 1) {
            if (cmd instanceof CommandGroup) {
                return ((CommandGroup) cmd).get(args.subList(1, args.size()));
            }
            throw new IllegalArgumentException("Command " + StringMan.getString(args) + " is not a group in " + getFullPath());
        }
        return cmd;
    }

    public String printCommandMap() {
        StringBuilder output = new StringBuilder();
        printCommandMap(output, getPrimaryCommandId(), 0);
        return output.toString();
    }

    public void printCommandMap(StringBuilder output, String id, int indent) {
        String prefix = "";
        if (indent > 0) prefix = StringMan.repeat(" --", indent);
        output.append(prefix).append(id).append("\n");
        for (String subId : primarySubCommandIds()) {
            CommandCallable command = get(subId);
            if (command instanceof CommandGroup) {
                ((CommandGroup) command).printCommandMap(output, subId, indent + 1);
            } else {
                output.append(prefix).append(" --<").append(subId).append(">").append("\n");
            }
        }
    }

    @Override
    public Set<ParametricCallable> getParametricCallables(Predicate<ParametricCallable> returnIf) {
        Set<ParametricCallable> result = new HashSet<>();
        for (CommandCallable sub : new HashSet<>(getSubcommands().values())) {
            result.addAll(sub.getParametricCallables(returnIf));
        }
        return result;
    }

    public void registerCommandsWithMapping(CommandGroup commands, YamlConfiguration commandMapping, int maxDepth, boolean ignoreMissingMapping) {
        for (String key : commandMapping.getKeys(true)) {
            Object obj = commandMapping.get(key);
            if (!(obj instanceof String)) continue;
            String[] split = key.split("\\.");
            String[] legacyPath = split[split.length - 1].replaceAll("[<>]", "").split(" ");
            split[split.length - 1] = legacyPath[legacyPath.length - 1];
            String newName = obj.toString();
            if (!newName.isEmpty()) split[split.length - 1] = newName;

            CommandCallable callable;
            try {
                callable = commands.get(Arrays.asList(legacyPath));
                if (callable == null) {
                    if (ignoreMissingMapping) continue;
                    throw new IllegalArgumentException("Could not find root command with path " + StringMan.getString(legacyPath));
                }
            } catch (IllegalArgumentException e) {
                if (!ignoreMissingMapping) throw e;
                continue;
            }

            List<String> path = new ArrayList<>(Arrays.asList(split));
            path.remove(path.size() - 1);
            if (path.size() > maxDepth)
                throw new IllegalArgumentException("Path " + StringMan.getString(path) + " is too deep.");

            registerWithPath(callable, path, split[split.length - 1]);
        }

        if (!ignoreMissingMapping) {
            Set<Method> legacyMethod = commands.getParametricCallables(f -> true).stream().map(ParametricCallable::getMethod).collect(Collectors.toSet());
            Set<Method> currentMethods = getParametricCallables(f -> true).stream().map(ParametricCallable::getMethod).collect(Collectors.toSet());
            for (Method method : legacyMethod) {
                if (!currentMethods.contains(method)) {
                    throw new IllegalArgumentException("Could not find mapping for method " + method.getName() + " | " + StringMan.getString(method.getGenericParameterTypes()) + " please add it to the commands.yml file or register it after legacy remapping.");
                }
            }
        }
    }

    public void registerCommandsWithMapping(Class<CM> remapping) {
        Map<Class<?>, Object> instanceCache = new HashMap<>();
        register(remapping, new ArrayList<>(), instanceCache, true);

        Set<ParametricCallable> allRegistered = getParametricCallables(f -> true);
        Set<Method> registeredMethods = new HashSet<>();
        for (ParametricCallable callable : allRegistered) {
            if (callable.getMethod() != null) {
                registeredMethods.add(callable.getMethod());
            }
        }

//        for (Map.Entry<Class<?>, Object> entry : instanceCache.entrySet()) {
//            Object instance = entry.getValue();
//            for (Method declaredMethod : entry.getKey().getDeclaredMethods()) {
//                if (declaredMethod.getAnnotation(Command.class) == null) continue;
//                if (!registeredMethods.contains(declaredMethod)) {
//                    String msg = "No mapping found for method " + entry.getKey().getSimpleName() + " | " + declaredMethod.getName();
//                    if (checkUnregisteredMethods) {
//                        throw new IllegalArgumentException(msg);
//                    } else {
//                        System.out.println(msg);
//                    }
//                }
//            }
//        }

    }

    private void register(Class<?> clazz, List<String> path, Map<Class<?>, Object> instanceCache, boolean isRoot) {
        AutoRegister methodInfo = clazz.getAnnotation(AutoRegister.class);
        if (methodInfo != null) {
            String field = methodInfo.field();
            Object instance;
            if (!field.isEmpty()) {
                try {
                    Class<?> infoClazz = Class.forName(methodInfo.clazz().getName());
                    Field declaredField = infoClazz.getDeclaredField(field);
                    declaredField.setAccessible(true);
                    instance = declaredField.get(null);
                } catch (IllegalAccessException | NoSuchFieldException | ClassNotFoundException e) {
                    throw new RuntimeException(e);
                }
            } else {
                instance = instanceCache.computeIfAbsent(methodInfo.clazz(), f -> {
                    try {
                        return f.newInstance();
                    } catch (InstantiationException | IllegalAccessException e) {
                        Logg.text("Failed to instantiate " + f.getName() + " for " + clazz.getName() + " | " + methodInfo.method());
                        throw new RuntimeException(e);
                    }
                });
            }
            Method found = null;
            Class<?> methClazz = instance.getClass();
            for (Method method : methClazz.getMethods()) {
                if (method.getDeclaringClass() != methClazz) continue;
                if (method.getName().equalsIgnoreCase(methodInfo.method()) && method.getAnnotation(Command.class) != null) {
                    if (found != null)
                        throw new IllegalStateException("Duplicate method found in " + methodInfo.clazz().getName() + " for " + methodInfo.method());
                    found = method;
                }
            }
            if (found == null) {
                throw new IllegalStateException("No method found " + clazz + " | " + methodInfo.method());
            }
            ParametricCallable callable = ParametricCallable.generateFromMethod(this, instance, found, store);
            if (callable == null) {
                throw new IllegalStateException("Method " + methodInfo.method() + " in " + methodInfo.clazz().getName() + " is not a valid @Command");
            }
            Set<String> userParamsLower = new ObjectOpenHashSet<>();
            Set<String> uniqueUserParamsLower = new ObjectOpenHashSet<>();
            for (Map.Entry<String, ParameterData> entry : callable.getUserParameterMap().entrySet()) {
                String argLower = entry.getKey().toLowerCase(Locale.ROOT);
                userParamsLower.add(argLower);
                uniqueUserParamsLower.add(argLower);
                Arg arg = entry.getValue().getAnnotation(Arg.class);
                if (arg != null && arg.aliases() != null) {
                    for (String alias : arg.aliases()) {
                        userParamsLower.add(alias.toLowerCase(Locale.ROOT));
                    }
                }
            }

            try {
                CommandRef ref = (CommandRef) clazz.getDeclaredField("cmd").get(null);
                Set<String> argsPresent = new ObjectLinkedOpenHashSet<>();
                Class<? extends CommandRef> refClazz = ref.getClass();
                for (Method method : refClazz.getMethods()) {
                    if (method.getDeclaringClass() != refClazz) continue;
                    if (method.getReturnType().equals(ref.getClass()) && method.getParameterCount() == 1 && method.getParameterTypes()[0].equals(String.class)) {
                        argsPresent.add(method.getName().toLowerCase(Locale.ROOT));
                    }
                }
                for (String arg : uniqueUserParamsLower) {
                    if (!argsPresent.contains(arg)) {
                        System.out.println("Missing parameter for " + methodInfo.clazz().getSimpleName() + "#" + methodInfo.method() + " | " + methodInfo.field() + "/" + clazz.getSimpleName() + " | " + arg + " not in " + argsPresent);
                    }
                }
                for (String arg : argsPresent) {
                    if (!userParamsLower.contains(arg)) {
                        System.out.println("Missing parameter (2) for " + methodInfo.clazz().getSimpleName() + "#" + methodInfo.method() + " | " + methodInfo.field() + "/" + clazz.getSimpleName() + " | " + arg + " not in " + argsPresent);
                    }
                }
            } catch (IllegalAccessException | NoSuchFieldException e) {
                throw new RuntimeException(e);
            }


            this.registerWithPath(callable, path, clazz.getSimpleName());
        }
        ArrayList<String> subPath = new ArrayList<>(path);

        String simpleName = clazz.getSimpleName();
        if (!isRoot) subPath.add(simpleName);

        for (Class<?> subClass : clazz.getDeclaredClasses()) {
            register(subClass, subPath, instanceCache, false);
        }
    }

    public void checkUnregisteredMethods(boolean throwError) {
        Set<Class> classes = new HashSet<>();
        Set<Method> methods = new HashSet<>();
        for (ParametricCallable callable : getParametricCallables(f -> true)) {
            Method method = callable.getMethod();
            if (methods.contains(method)) continue;
            methods.add(method);
            classes.add(method.getDeclaringClass());
        }
        Map<Class, List<Method>> missingMethods = new HashMap<>();
        for (Class clazz : classes) {
            // get methods with @Command annotation
            // Add any missing methods to missing methods
            for (Method declaredMethod : clazz.getMethods()) {
                if (declaredMethod.getDeclaringClass() != clazz) continue;
                if (declaredMethod.getAnnotation(Command.class) == null) continue;
                if (!methods.contains(declaredMethod)) {
                    missingMethods.computeIfAbsent(clazz, f -> new ArrayList<>()).add(declaredMethod);
                }
            }
        }
        // throw error if there are any missing methods
        if (!missingMethods.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<Class, List<Method>> entry : missingMethods.entrySet()) {
                sb.append("Missing methods for ").append(entry.getKey().getSimpleName()).append(":\n");
                for (Method method : entry.getValue()) {
                    sb.append(" - ").append(method.getName()).append("\n");
                }
                sb.append("\nSee example in CommandManager2#registerDefaults");
            }
            if (throwError) {
                throw new IllegalStateException(sb.toString());
            } else {
                System.out.println(sb.toString());
            }
        }

    }
}