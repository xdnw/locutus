package link.locutus.discord.commands.manager.v2.command;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.impl.SlashCommandManager;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.Example;
import link.locutus.discord.web.jooby.WebRoot;
import org.json.JSONObject;

import java.util.*;

public class CommandRef {
    private final Map<String, String> arguments = new LinkedHashMap<>();
    private final String path;
    private List<Example> examples;

    public CommandRef() {
        String[] split = getClass().getName().split("\\$");
        split = Arrays.copyOfRange(split, 1, split.length);
        path = String.join(" ", split);
    }

    public String getName() {
        String[] split = path.split(" ");
        return split[split.length - 1];
    }

    public List<Example> getExamples() {
        return examples == null ? Collections.emptyList() : examples;
    }

    public void addExample(CommandRef root, String label, String desc) {
        if (label.length() > 25) {
            throw new IllegalArgumentException("Example label is too long: " + label);
        }
        if (arguments.isEmpty()) throw new IllegalArgumentException("Cannot add empty example to root command");
        if (!root.arguments.isEmpty()) throw new IllegalArgumentException("Cannot add example to non-root command");
        root.examples.add(new Example(this, label, desc));
    }

    public String getPath() {
        return path;
    }

    public Map<String, String> getArguments() {
        return arguments;
    }

    public <T extends CommandRef> T createArgs(String... args) {
        Map<String, String> argMap = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i += 2) {
            argMap.put(args[i], args[i + 1]);
        }
        return createArgs(argMap);
    }

    public <T extends CommandRef> T createEmpty() {
        return createArgs(new HashMap<>());
    }

    public <T extends CommandRef> T set(String arg, String value) {
        CommandRef instance = this;
        if (arguments.isEmpty()) {
            instance = createArgs();
        }
        if (value != null) {
            instance.arguments.put(arg, value);
        } else {
            instance.arguments.remove(arg);
        }
        return (T) instance;
    }

    public <T extends CommandRef> T createArgs(Map<String, String> args) {
        try {
            CommandRef instance = getClass().newInstance();
            instance.arguments.putAll(args);
            return (T) instance;
        } catch (InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public String toSlashMention() {
        return SlashCommandManager.getSlashMention(path);
    }

    public String toSlashCommand() {
        return toSlashCommand(true);
    }

    public String toSlashCommand(boolean backTicks) {
        return SlashCommandManager.getSlashCommand(path, arguments, backTicks);
    }

    public Map<String, String> toMap() {
        Map<String, String> data = new LinkedHashMap<>(arguments);
        data.put("", path);
        return data;
    }

    public JSONObject toJson() {
        return new JSONObject(toMap());
    }

    public String toCommandArgs() {
        return toJson().toString();
    }

    public ParametricCallable getCallable(boolean throwError) {
        CommandCallable callable = Locutus.imp().getCommandManager().getV2().getCallable(Arrays.asList(path.split(" ")));
        if (callable == null) {
            if (throwError) throw new IllegalArgumentException("No command found for " + path);
            return null;
        }
        if (!(callable instanceof ParametricCallable)) {
            if (throwError) throw new IllegalArgumentException("Command " + path + " is not parametric.");
            return null;
        }
        return (ParametricCallable) callable;
    }

    @Override
    public String toString() {
        return toSlashCommand();
    }

    public String toPageUrl() {
        return WebRoot.REDIRECT + "/" + path.replace(" ", "/");
    }

    public String toCommandUrl() {
        return WebRoot.REDIRECT + "/command/" + path.replace(" ", "/");
    }
}