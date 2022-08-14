package link.locutus.discord.commands.manager.v2.command;

import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.validator.ValidatorStore;
import link.locutus.discord.commands.manager.v2.perm.PermissionHandler;
import link.locutus.discord.util.StringMan;

import java.util.*;
import java.util.function.Predicate;

public class CommandGroup implements ICommandGroup {
    private final ValueStore store;
    private final ValidatorStore validators;
    private final CommandCallable parent;
    private final List<String> aliases;
    private Map<String, CommandCallable> subcommands = new HashMap<>();
    private String help,desc;

    public CommandGroup(CommandCallable parent, String[] aliases, ValueStore store, ValidatorStore validators) {
        this.store = store;
        this.validators = validators;
        this.parent = parent;
        this.aliases = Arrays.asList(aliases);
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
        return null;
    }

    public Map<String, CommandCallable> getAllowedCommands(ValueStore store, PermissionHandler permHandler) {
        Map<String, CommandCallable> allowed = new LinkedHashMap<>();
        for (CommandCallable cmd : subcommands.values()) {
            if (cmd.hasPermission(store, permHandler)) {
                allowed.put(cmd.getPrimaryAlias().toLowerCase(), cmd);
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

    public String printCommandMap() {
        StringBuilder output = new StringBuilder();
        printCommandMap(output, 0);
        return output.toString();
    }

    public void printCommandMap(StringBuilder output, int indent) {
        String prefix = "";
        if (indent > 0) prefix = StringMan.repeat(" --", indent);
        output.append(prefix + getPrimaryAlias()).append("\n");
        for (String id : primarySubCommandIds()) {
            CommandCallable command = get(id);
            if (command instanceof CommandGroup) {
                ((CommandGroup) command).printCommandMap(output, indent + 1);
            } else {
                output.append(prefix + " --<" + command.getPrimaryAlias() + ">").append("\n");
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
}