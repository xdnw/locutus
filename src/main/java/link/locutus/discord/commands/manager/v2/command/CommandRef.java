package link.locutus.discord.commands.manager.v2.command;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.impl.SlashCommandManager;
import link.locutus.discord.web.jooby.WebRoot;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class CommandRef {
    private final Map<String, String> arguments = new LinkedHashMap<>();
    private final String path;

    public CommandRef() {
        String[] split = getClass().getName().split("\\$");
        split = Arrays.copyOfRange(split, 1, split.length);
        path = String.join(" ", split);
    }

    public String getPath() {
        return path;
    }

    public Map<String, String> getArguments() {
        return arguments;
    }

    public <T extends CommandRef> T createArgs(String... args) {
        try {
            CommandRef instance = getClass().newInstance();
            for (int i = 0; i < args.length; i += 2) {
                instance.arguments.put(args[i], args[i + 1]);
            }
            return (T) instance;
        } catch (InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public long getSlashId() {
        Locutus locutus = Locutus.imp();
        if (locutus == null) return -1;
        SlashCommandManager slashManager = Locutus.imp().getSlashCommands();
        if (slashManager == null) return -1;
        Long id = slashManager.getCommandId(path);
        return id == null ? -1 : id;
    }

    public String toSlashMention() {
        SlashCommandManager slashManager = Locutus.imp().getSlashCommands();
        if (slashManager != null) {
            String mention = slashManager.getSlashMention(path);
            if (mention != null) return mention;
        }
        return toSlashCommand();
    }

    public String toSlashCommand() {
        return toSlashCommand(true);
    }

    public String toSlashCommand(boolean backTicks) {
        StringBuilder builder = new StringBuilder();
        builder.append("/").append(getPath());
        if (!arguments.isEmpty()) {
            // join on " "
            for (Map.Entry<String, String> entry : arguments.entrySet()) {
                if (entry.getValue() == null) continue;
                builder.append(" ").append(entry.getKey()).append(": ").append(entry.getValue());
            }
        }
        if (backTicks) return "`" + builder + "`";
        return builder.toString();
    }


    public JSONObject toJson() {
        return new JSONObject(toCommandArgs());
    }

    public String toCommandArgs() {
        Map<String, String> data = new HashMap<>(arguments);
        data.put("", path);
        return new JSONObject(data).toString();
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
        return WebRoot.REDIRECT + "/page/" + path.replace(" ", "/");
    }

    public String toCommandUrl() {
        return WebRoot.REDIRECT + "/command/" + path.replace(" ", "/");
    }
}