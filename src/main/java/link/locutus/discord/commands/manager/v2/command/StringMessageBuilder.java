package link.locutus.discord.commands.manager.v2.command;

import link.locutus.discord.commands.rankings.table.TableNumberFormat;
import link.locutus.discord.commands.rankings.table.TimeFormat;
import link.locutus.discord.commands.rankings.table.TimeNumericTable;
import link.locutus.discord.util.MarkupUtil;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import org.json.JSONArray;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class StringMessageBuilder implements IMessageBuilder {

    public final StringBuilder content = new StringBuilder();
    public final Map<String, String> buttons = new LinkedHashMap<>();
    public final Map<String, String> embeds = new LinkedHashMap<>();
    public final Map<String, byte[]> images = new HashMap<>();
    public final Map<String, byte[]> files = new HashMap<>();
    private final StringMessageIO parent;
    public long id;
    public long timeCreated;
    public User author;

    public StringMessageBuilder(StringMessageIO parent, long id, long timeCreated, User author) {
        this.parent = parent;
        this.id = id;
        this.timeCreated = timeCreated;
        this.author = author;
    }

    public static List<StringMessageBuilder> list(User author, String... message) {
        List<StringMessageBuilder> result = new ArrayList<>();
        for (String s : message) {
            result.add(of(author, s));
        }
        return result;
    }

    public static StringMessageBuilder of(User author, String message) {
        StringMessageBuilder builder = new StringMessageBuilder(null, 0, System.currentTimeMillis(), author);
        builder.append(message);
        return builder;
    }

    public static String toHtml(List<StringMessageBuilder> messages, boolean includeFiles) {
        StringBuilder response = new StringBuilder();
        if (messages != null) {
            for (StringMessageBuilder message : messages) {
                response.append(message.toSimpleHtml(includeFiles));
            }
        }
        return response.toString();
    }

    public static JSONArray toJsonArray(String bodyFormat, List<StringMessageBuilder> messages, boolean includeFiles, boolean includeButtons) {
        JSONArray array = new JSONArray();
        if (messages != null) {
            for (StringMessageBuilder message : messages) {
                array.put(message.toJson(bodyFormat, includeFiles, includeButtons));
            }
        }
        return array;
    }

    @Override
    public IMessageBuilder removeButtonByLabel(String label) {
        buttons.entrySet().removeIf(entry -> entry.getValue().equals(label));
        return this;
    }

    @Override
    public void sendWhenFree() {
        this.toString();
    }

    @Override
    public String toString() {
        return build(false);
    }

    public String build(boolean includeButtons) {
        StringBuilder result = new StringBuilder(content);

        if (!embeds.isEmpty()) {
            for (Map.Entry<String, String> entry : embeds.entrySet()) {
                result.append("## ").append(entry.getKey()).append("\n").append(">>> " + entry.getValue()).append("\n\n");
            }
        }
        if (includeButtons) {
            for (Map.Entry<String, String> entry : buttons.entrySet()) {
                result.append("\n").append(MarkupUtil.markdownUrl("button:" + entry.getValue(), entry.getKey()));
            }
        }
        return result.toString();
    }

    public String toSimpleHtml(boolean includeFiles) {
        StringBuilder html = new StringBuilder(build(false));
        for (Map.Entry<String, byte[]> entry : images.entrySet()) {
            String name = entry.getKey();
            String base64String = Base64.getEncoder().encodeToString(entry.getValue());
            html.append("<img src=\"data:image/png;base64,").append(base64String).append("\" alt=\"").append(name).append("\">");
        }
        for (Map.Entry<String, byte[]> entry : files.entrySet()) {
            String name = entry.getKey();
            // use <pre>
            html.append("### `").append(name).append("`\n");
            html.append("<pre>").append(new String(entry.getValue())).append("</pre>\n");
        }
        return html.toString();
    }

    /**
     * Write the content of this message to the output
     * @param output
     * @return the output
     */
    public IMessageBuilder writeTo(IMessageBuilder output) {
        if (!content.isEmpty()) output.append(content.toString());
        for (Map.Entry<String, String> entry : embeds.entrySet()) {
            output.embed(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, String> entry : buttons.entrySet()) {
            output.commandButton(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, byte[]> entry : images.entrySet()) {
            output.image(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, byte[]> entry : files.entrySet()) {
            output.file(entry.getKey(), entry.getValue());
        }
        return output;
    }

    @Override
    public User getAuthor() {
        return author;
    }

    @Override
    public List<MessageEmbed> getEmbeds() {
        throw new UnsupportedOperationException();
    }

    @Override
    public long getTimeCreated() {
        return timeCreated;
    }

    @Override
    public CompletableFuture<IMessageBuilder> send() {
        return parent.send(this);
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public IMessageBuilder clear() {
        content.setLength(0);
        buttons.clear();
        embeds.clear();
        images.clear();
        files.clear();
        return this;
    }

    @Override
    public IMessageBuilder clearEmbeds() {
        embeds.clear();
        return this;
    }

    @Override
    public IMessageBuilder clearButtons() {
        this.buttons.clear();
        return this;
    }

    @Override
    public IMessageBuilder append(String content) {
        this.content.append(content);
        return this;
    }

    @Override
    public IMessageBuilder embed(String title, String body) {
        return embed(title, body, null);
    }

    @Override
    public IMessageBuilder embed(String title, String body, String footer) {
        body += footer;
        embeds.put(title, body);
        return this;
    }

    @Override
    public IMessageBuilder embed(MessageEmbed embed) {
        this.embeds.put(embed.getTitle(), embed.getDescription());
        return this;
    }

    @Override
    public IMessageBuilder commandInline(CommandRef ref) {
        content.append(ref.toSlashCommand());
        return this;
    }

    @Override
    public IMessageBuilder commandLinkInline(CommandRef ref) {
        content.append(ref.toSlashMention());
        return this;
    }

    @Override
    public IMessageBuilder commandButton(String command, String message) {
        buttons.put(command, message);
        return this;
    }

    @Override
    public IMessageBuilder linkButton(String url, String message) {
        buttons.put(url, message);
        return this;
    }

    @Override
    public IMessageBuilder image(String name, byte[] data) {
        images.put(name, data);
        return this;
    }

    @Override
    public IMessageBuilder file(String name, byte[] data) {
        files.put(name, data);
        return this;
    }

    @Override
    public IMessageBuilder graph(TimeNumericTable table, TimeFormat timeFormat, TableNumberFormat numberFormat, long origin) {
        try {
            images.put(table.getName(), table.write(timeFormat, numberFormat));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    public boolean isEmpty() {
        return content.isEmpty() && embeds.isEmpty() && buttons.isEmpty() && images.isEmpty() && files.isEmpty();
    }
}
