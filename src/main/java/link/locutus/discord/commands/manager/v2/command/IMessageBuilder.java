package link.locutus.discord.commands.manager.v2.command;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.vandermeer.asciitable.AT_Context;
import de.vandermeer.asciitable.AsciiTable;
import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.manager.v2.table.TableNumberFormat;
import link.locutus.discord.commands.manager.v2.table.TimeFormat;
import link.locutus.discord.commands.manager.v2.table.TimeNumericTable;
import link.locutus.discord.config.Settings;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.web.WebUtil;
import link.locutus.discord.web.commands.binding.value_types.GraphType;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import org.json.JSONObject;

import javax.annotation.CheckReturnValue;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public interface IMessageBuilder {
    public record GraphMessageInfo(TimeNumericTable table, TimeFormat timeFormat, TableNumberFormat numberFormat, GraphType type, long origin) {
        public static GraphMessageInfo fromJson(JsonObject tableObj) {
            TimeNumericTable<?> table = new TimeNumericTable<>(tableObj) {
                @Override
                public void add(long day, Object ignore) {
                }
            };
            TimeFormat timeFormat = TimeFormat.valueOf(tableObj.get("timeFormat").getAsString());
            TableNumberFormat numberFormat = TableNumberFormat.valueOf(tableObj.get("numberFormat").getAsString());
            long origin = tableObj.get("origin").getAsLong();
            GraphType type = null;
            if (tableObj.has("type")) {
                type = GraphType.valueOf(tableObj.get("type").getAsString());
            }
            return new GraphMessageInfo(table, timeFormat, numberFormat, type, origin);
        }
    }

    public abstract Map<String, String> getButtons();

    public Map<String, String> getLinks();

    public List<GraphMessageInfo> getTables();

    public Map<String, byte[]> getImages();

    public Map<String, byte[]> getFiles();

    static JsonElement toJson(String appendText, List<? extends IMessageBuilder> messages, boolean includeFiles, boolean includeButtons, boolean includeTables, boolean shrinkEmbeds) {
        Map<String, Object> root = new LinkedHashMap<>();
        if (appendText != null && !appendText.isEmpty()) root.put("content", appendText);
        if (messages != null) {
            for (IMessageBuilder message : messages) {
                message.addJson(root, includeFiles, includeButtons, includeTables, shrinkEmbeds);
            }
        }
        return StringMan.toJson(root);
    }

    public abstract void addJson(Map<String, Object> root, boolean includeFiles, boolean includeButtons, boolean includeTables, boolean shrinkEmbeds);

    default JsonElement toJson() {
        Map<String, Object> root = new LinkedHashMap<>();
        addJson(root, true, true, true, true);
        return StringMan.toJson(root);
    }

    long getId();

    IMessageBuilder clear();

    @CheckReturnValue
    IMessageBuilder append(String content);

    @CheckReturnValue
    IMessageBuilder append(Shrinkable msg);

    @CheckReturnValue
    default IMessageBuilder embed(String title, String body) {
        return embed(title, body, null);
    }

    @CheckReturnValue
    default IMessageBuilder embed(String title, String body, String footer) {
        ShrinkableEmbed embed = new ShrinkableEmbed().title(Shrinkable.of(title)).description(Shrinkable.of(body));
        if (footer != null) embed.footer(Shrinkable.of(footer));
        return embed(embed);
    }

    @CheckReturnValue
    IMessageBuilder commandInline(CommandRef ref);

    @CheckReturnValue
    IMessageBuilder commandLinkInline(CommandRef ref);

    @CheckReturnValue
    default IMessageBuilder modal(CommandBehavior behavior, CommandRef cmd, String message) {
        return modal(behavior, null, cmd, message);
    }

    @CheckReturnValue
    default IMessageBuilder modal(CommandBehavior behavior, Long outputChannel, ICommand cmd, Map<String, String> arguments, String message) {
        return modal(behavior, outputChannel, cmd.getFullPath(), arguments, message);
    }

    @CheckReturnValue
    default IMessageBuilder modal(CommandBehavior behavior, Long outputChannel, CommandRef cmd, String message) {
        return modal(behavior, outputChannel, cmd.getPath(), cmd.getArguments(), message);
    }

    @CheckReturnValue
    default IMessageBuilder modal(CommandBehavior behavior, Long outputChannel, String path, Map<String, String> arguments, String message) {
            Iterator<Map.Entry<String, String>> iter = arguments.entrySet().iterator();
            Set<String> promptFor = new LinkedHashSet<>();
            while (iter.hasNext()) {
                Map.Entry<String, String> entry = iter.next();
                if (entry.getValue() == null) {
                    iter.remove();
                } else if (entry.getValue().isEmpty()) {
                    promptFor.add(entry.getKey().toLowerCase(Locale.ROOT));
                    iter.remove();
                }
            }
            // convert keys to lowercase
            arguments = arguments.entrySet().stream().collect(Collectors.toMap(e -> e.getKey().toLowerCase(Locale.ROOT),
                    Map.Entry::getValue, (a, b) -> b, LinkedHashMap::new));
            String argumentJson = arguments.isEmpty() ? null : WebUtil.GSON.toJson(arguments);
            CM.modal.create attach = CM.modal.create.cmd.command(path).arguments(StringMan.join(promptFor, ",")).defaults(argumentJson);
            return commandButton(behavior, attach, message);
        }

    @CheckReturnValue
    default IMessageBuilder commandButton(CommandBehavior behavior, CommandRef ref, String message) {
        return commandButton(behavior, null, ref, message);
    }

    @CheckReturnValue
    default IMessageBuilder commandButton(CommandRef ref, String message) {
        return commandButton(CommandBehavior.DELETE_MESSAGE, null, ref, message);
    }

    @CheckReturnValue
    default IMessageBuilder commandButton(CommandBehavior behavior, Long outputChannel, CommandRef ref, String message) {
        return commandButton(behavior, outputChannel, ref.toCommandArgs(), message);
    }

    @CheckReturnValue
    default IMessageBuilder commandButton(CommandBehavior behavior, Long outputChannel, ICommand ref, Map<String, String> args, String message) {
        return commandButton(behavior, outputChannel, ref.toCommandArgs(args), message);
    }

    @CheckReturnValue
    default IMessageBuilder commandButton(CommandBehavior behavior, Long outputChannel, String command, String message) {
        StringBuilder cmd = new StringBuilder();
        if (outputChannel != null) cmd.append("<#").append(outputChannel).append("> ");
        if (behavior != null && behavior != CommandBehavior.DELETE_MESSAGE) cmd.append(behavior.getValue());
        cmd.append(command);
        return commandButton(cmd.toString(), message);
    }

    @CheckReturnValue
    default IMessageBuilder modalLegacy(CommandBehavior behavior, CommandRef ref, String message) {
        CM.fun.say say = CM.fun.say.cmd.msg(behavior.getValue() + ref.toSlashCommand());
        commandButton(behavior, say, message);
        return this;
    }

    default IMessageBuilder paginate(String title, CommandRef ref, Integer page, int perPage, List<String> results) {
        return paginate(title, ref, page, perPage, results, null, false);
    }

    default IMessageBuilder paginate(String title, CommandRef ref, Integer page, int perPage, List<String> results, String footer, boolean inline) {
        return paginate(title, new JSONObject(ref.toCommandArgs()), page, perPage, results, footer, inline);
    }

    @CheckReturnValue
    default IMessageBuilder paginate(String title, JSONObject command, Integer page, int perPage, List<String> results) {
        return paginate(title, command, page, perPage, results, null, false);
    }

    @CheckReturnValue
    default IMessageBuilder paginate(String title, JSONObject command, Integer page, int perPage, List<String> results, String footer, boolean inline) {
        return paginate(title, command.toString(), page, perPage, results, footer, inline);
    }

    @CheckReturnValue
    default IMessageBuilder writeTable(String title, List<List<String>> tableList, boolean embed, String footer) {
        if (tableList.size() == 0) return this;
        String tableStr;
        try {
            AsciiTable at = new AsciiTable(new AT_Context().setWidth(36).setLineSeparator("\n"));
            at.addRow(tableList.get(0).toArray(new Object[0]));
            at.addRule();
            for (int i = 1; i < tableList.size(); i++) {
                at.addRow(tableList.get(i).toArray(new Object[0]));
                if (i != tableList.size() - 1) {
                    at.addRule();
                }
            }
            if (footer == null) footer = "";
            else footer = "\n" + footer;

            tableStr = at.render();
        } catch (Throwable e) {
            AsciiTable at = new AsciiTable(new AT_Context().setLineSeparator("\n"));
            at.addRow(tableList.get(0).toArray(new Object[0]));
            at.addRule();
            for (int i = 1; i < tableList.size(); i++) {
                at.addRow(tableList.get(i).toArray(new Object[0]));
                if (i != tableList.size() - 1) {
                    at.addRule();
                }
            }
            if (footer == null) footer = "";
            else footer = "\n" + footer;

            tableStr = at.render();
        }
        tableStr = tableStr.lines().map(f -> f.substring(1, f.length() - 1)).collect(Collectors.joining("\n"));
        if (embed) {
            return embed(title, "```\n" + tableStr + "\n```" + footer);
        }
        return append("```\n" + tableStr + "\n```" + footer);
    }

    @CheckReturnValue
    @Deprecated
    default IMessageBuilder paginate(String title, String command, Integer page, int perPage, List<String> results, String footer, boolean inline) {
        if (results.isEmpty()) {
            System.out.println("Results are empty.");
            return this;
        }

        int numResults = results.size();
        int maxPage = (numResults - 1) / perPage;
        if (page == null) page = 0;

        int start = page * perPage;
        int end = Math.min(numResults, start + perPage);

        StringBuilder body = new StringBuilder();
        for (int i = start; i < end; i++) {
            body.append(results.get(i)).append('\n');
        }

        Map<String, String> reactions = new LinkedHashMap<>();
        boolean hasPrev = page > 0;
        boolean hasNext = page < maxPage;

        String previousPageCmd;
        String nextPageCmd;

        if (command.charAt(0) == Settings.commandPrefix(true).charAt(0)) {
            String cmdCleared = command.replaceAll("page:" + "[0-9]+", "");
            previousPageCmd = cmdCleared + " page:" + (page - 1);
            nextPageCmd = cmdCleared + " page:" + (page + 1);
        } else if (command.charAt(0) == '{') {
            JSONObject json = new JSONObject(command);
            Map<String, Object> arguments = json.toMap();

            previousPageCmd = new JSONObject(arguments).put("page", page - 1).toString();
            nextPageCmd = new JSONObject(arguments).put("page", page + 1).toString();
        } else {
            String cmdCleared = command.replaceAll("-p " + "[0-9]+", "");
            if (!cmdCleared.isEmpty() && !Locutus.cmd().isModernPrefix(cmdCleared.charAt(0))) {
                cmdCleared = Settings.commandPrefix(false) + cmdCleared;
            }
            previousPageCmd = cmdCleared + " -p " + (page - 1);
            nextPageCmd = cmdCleared + " -p " + (page + 1);
        }

        String prefix = inline ? "~" : "";
        if (hasPrev) {
            reactions.put("\u2B05\uFE0F Previous", prefix + previousPageCmd);
        }
        if (hasNext) {
            reactions.put("Next \u27A1\uFE0F", prefix + nextPageCmd);
        }

        ShrinkableEmbed builder = new ShrinkableEmbed();
        builder.setTitle(title);
        builder.setDescription(body.toString());
        if (footer != null) builder.setFooter(footer);

        clearEmbeds();
        embed(builder);
        clearButtons();
        addCommands(reactions);

        return this;
    }

    @CheckReturnValue
    IMessageBuilder removeButtonByLabel(String label);

    @CheckReturnValue
    @Deprecated
    IMessageBuilder commandButton(String command, String message);

    @CheckReturnValue
    default IMessageBuilder commandButton(JSONObject command, String message) {
        return commandButton(command.toString(), message);
    }

    @CheckReturnValue
    default IMessageBuilder commandButton(CommandBehavior behavior, JSONObject command, String message) {
        return commandButton(behavior, command.toString(), message);
    }

    @CheckReturnValue
    default IMessageBuilder confirmation(String title, String body, CommandRef command) {
        return confirmation(title, body, new JSONObject(command.toCommandArgs()));
    }

    @CheckReturnValue
    default IMessageBuilder confirmation(String title, String body, JSONObject command) {
        return confirmation(title, body, command, "force");
    }

    @CheckReturnValue
    default IMessageBuilder confirmation(JSONObject command) {
        return commandButton(command.put("force", "true").toString(), "Confirm");
    }

    @CheckReturnValue
    default IMessageBuilder confirmation(CommandRef command) {
        return confirmation(command.toJson());
    }

    default IMessageBuilder confirmation(String title, String body, JSONObject command, String parameter) {
        return confirmation(title, body, command, parameter, "Confirm");
    }

    default IMessageBuilder confirmation(String title, String body, JSONObject command, String parameter, String message) {
        return embed(title, body).commandButton(command.put(parameter, "true").toString(), message);
    }

    default IMessageBuilder confirmation(JSONObject command, String parameter, String message) {
        return commandButton(command.put(parameter, "true").toString(), message);
    }

    @CheckReturnValue
    @Deprecated
    default IMessageBuilder commandButton(CommandBehavior behavior, String command, String message) {
        return commandButton(behavior.getValue() + command, message);
    }

    @CheckReturnValue
    IMessageBuilder linkButton(String url, String message);

    @CheckReturnValue
    IMessageBuilder image(String name, byte[] data);

    @CheckReturnValue
    IMessageBuilder file(String name, byte[] data);

    @CheckReturnValue
    IMessageBuilder graph(TimeNumericTable table, TimeFormat timeFormat, TableNumberFormat numberFormat, GraphType type, long originDate);

    CompletableFuture<IMessageBuilder> send();

    default void sendWhenFree() {
        send();
    }

    default void sendIfFree() {
        if (RateLimitUtil.getCurrentUsed() < RateLimitUtil.getLimitPerMinute()) {
            send();
        }
    }

    User getAuthor();

    List<ShrinkableEmbed> getEmbeds();

    long getTimeCreated();

    IMessageBuilder clearEmbeds();

    @CheckReturnValue
    IMessageBuilder embed(ShrinkableEmbed embed);

    /**
     * Key pair (name, command)
     */
    @CheckReturnValue
    default IMessageBuilder addCommands(Map<String, String> reactions) {
        for (Map.Entry<String, String> entry : reactions.entrySet()) {
            commandButton(entry.getValue(), entry.getKey());
        }
        return this;
    }

    IMessageBuilder clearButtons();

    @CheckReturnValue
    default IMessageBuilder cancelButton() {
        return commandButton(CommandBehavior.DELETE_MESSAGE, " ", "Cancel");
    }

    @CheckReturnValue
    default IMessageBuilder cancelButton(String label) {
        return commandButton(CommandBehavior.DELETE_MESSAGE, " ", label);
    }

    @CheckReturnValue
    default IMessageBuilder file(String name, String data) {
        return file(name, data.getBytes(StandardCharsets.UTF_8));
    }
}