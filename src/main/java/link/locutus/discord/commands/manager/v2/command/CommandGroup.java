package link.locutus.discord.commands.manager.v2.command;

import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.validator.ValidatorStore;
import link.locutus.discord.commands.manager.v2.perm.PermissionHandler;
import link.locutus.discord.config.yaml.file.YamlConfiguration;
import link.locutus.discord.util.StringMan;

import java.io.File;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class CommandGroup implements ICommandGroup {
    private final ValueStore store;
    private final ValidatorStore validators;
    private final CommandCallable parent;
    private final List<String> aliases;
    private Map<String, CommandCallable> subcommands = new LinkedHashMap<>();
    private String help,desc;

    public CommandGroup(CommandCallable parent, String[] aliases, ValueStore store, ValidatorStore validators) {
        this.store = store;
        this.validators = validators;
        this.parent = parent;
        this.aliases = Arrays.asList(aliases);
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

    public static CommandGroup createRoot(ValueStore store, ValidatorStore validators) {
        CommandGroup group = new CommandGroup(null, new String[0], store, validators);
        return group;
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

    public void registerCommands(Object object) {
        List<ParametricCallable> cmds = ParametricCallable.generateFromClass(this, object, store);
        for (ParametricCallable cmd : cmds) {
            register(cmd, cmd.aliases());
        }
    }

    public void registerSubCommands(Object object, String... aliases) {
        CommandGroup subCmd = new CommandGroup(this, aliases, store, validators);
        subCmd.registerCommands(object);
        register(subCmd, aliases);
    }

    public CommandGroup createSubGroup(String... aliases) {
        return new CommandGroup(this, aliases, store, validators);
    }


    @Override
    public Object call(ArgumentStack stack) {
        ValueStore store = stack.getStore();
        if (!stack.hasNext()) {
            throw new CommandUsageException(this, "No subcommand specified", help(store), desc(store));
        }
        String arg = stack.consumeNext();
        CommandCallable subcommand = subcommands.get(arg.toLowerCase());
        if (subcommand == null) {
            throw new CommandUsageException(this, "Invalid subcommand: `" + arg + "`", help(store), desc(store));
        }
        Object result = subcommand.call(stack);
        return result;
    }

    @Override
    public String help(ValueStore store) {
        return getPrimaryCommandId() + " <subcommand>";
    }

    @Override
    public String desc(ValueStore store) {
        return " - " + StringMan.join(primarySubCommandIds(), "\n - ");
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
    public String toHtml(ValueStore store, PermissionHandler permHandler, String endpoint) {
        Map<String, CommandCallable> allowed = getAllowedCommands(store, permHandler);
        if (endpoint == null) endpoint = "";
        return rocker.command.commandgroup.template(store, this, allowed, endpoint).render().toString();
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
        output.append(prefix + id).append("\n");
        for (String subId : primarySubCommandIds()) {
            CommandCallable command = get(subId);
            if (command instanceof CommandGroup) {
                ((CommandGroup) command).printCommandMap(output, subId, indent + 1);
            } else {
                output.append(prefix + " --<" + subId + ">").append("\n");
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
            if (path.size() > maxDepth) throw new IllegalArgumentException("Path " + StringMan.getString(path) + " is too deep");

            registerWithPath(callable, path, split[split.length - 1]);
        }

        if (!ignoreMissingMapping) {
            Set<Method> legacyMethod = commands.getParametricCallables(f -> true).stream().map(f -> f.getMethod()).collect(Collectors.toSet());
            Set<Method> currentMethods = getParametricCallables(f -> true).stream().map(f -> f.getMethod()).collect(Collectors.toSet());
            for (Method method : legacyMethod) {
                if (!currentMethods.contains(method)) {
                    throw new IllegalArgumentException("Could not find mapping for method " + method.getName() + " | " + StringMan.getString(method.getGenericParameterTypes()));
                }
            }
        }
    }
}