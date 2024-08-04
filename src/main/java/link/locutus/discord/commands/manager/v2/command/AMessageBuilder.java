package link.locutus.discord.commands.manager.v2.command;

import com.google.gson.JsonObject;
import link.locutus.discord.commands.rankings.table.TableNumberFormat;
import link.locutus.discord.commands.rankings.table.TimeFormat;
import link.locutus.discord.commands.rankings.table.TimeNumericTable;
import link.locutus.discord.util.MarkupUtil;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public abstract class AMessageBuilder implements IMessageBuilder {

    public final StringBuilder content = new StringBuilder();
    public final Map<String, String> buttons = new LinkedHashMap<>();
    public final Map<String, String> links = new LinkedHashMap<>();
    public final List<GraphMessageInfo> tables = new ArrayList<>();
    public final Map<String, String> embeds = new LinkedHashMap<>();
    public final Map<String, byte[]> images = new HashMap<>();
    public final Map<String, byte[]> files = new HashMap<>();
    private final IMessageIO parent;
    public long id;
    public long timeCreated;
    public User author;

    public record GraphMessageInfo(TimeNumericTable table, TimeFormat timeFormat, TableNumberFormat numberFormat, long origin) { }

    public AMessageBuilder(IMessageIO parent, long id, long timeCreated, User author) {
        this.parent = parent;
        this.id = id;
        this.timeCreated = timeCreated;
        this.author = author;
    }

    public String getContent() {
        return content.toString();
    }

    public Map<String, String> getButtons() {
        return buttons;
    }

    public Map<String, String> getLinks() {
        return links;
    }

    public List<GraphMessageInfo> getTables() {
        return tables;
    }

    public Map<String, String> getEmbedDescriptions() {
        return embeds;
    }

    public Map<String, byte[]> getImages() {
        return images;
    }

    public Map<String, byte[]> getFiles() {
        return files;
    }

    public IMessageIO getParent() {
        return parent;
    }

    @Override
    public IMessageBuilder graph(TimeNumericTable table, TimeFormat timeFormat, TableNumberFormat numberFormat, long originDate) {
        tables.add(new GraphMessageInfo(table, timeFormat, numberFormat, originDate));
        return this;
    }

    @Override
    public void addJson(Map<String, Object> root, boolean includeFiles, boolean includeButtons) {
        if (!content.isEmpty()) {
            String existing = root.computeIfAbsent("content", k -> "").toString();
            root.put("content", existing + content.toString());
        }
        if (!embeds.isEmpty()) {
            List<Map<String, String>> embedArray = (List<Map<String, String>>) root.computeIfAbsent("embeds", k -> new ArrayList<>());
            for (Map.Entry<String, String> entry : embeds.entrySet()) {
                Map<String, String> embed = new LinkedHashMap<>();
                embed.put("title", entry.getKey());
                embed.put("description", entry.getValue());
                embedArray.add(embed);
            }
            root.put("embeds", embedArray);
        }
        if (includeButtons) {
            List<Map<String, String>> buttonInfo = (List<Map<String, String>>) root.computeIfAbsent("buttons", k -> new ArrayList<>());
            if (!buttons.isEmpty()) {
                for (Map.Entry<String, String> entry : buttons.entrySet()) {
                    buttonInfo.add(Map.of("cmd", entry.getKey(), "label", entry.getValue()));
                }
            }
            if (!links.isEmpty()) {
                for (Map.Entry<String, String> entry : links.entrySet()) {
                    buttonInfo.add(Map.of("href", entry.getKey(), "label", entry.getValue()));
                }
            }
            if (!buttonInfo.isEmpty()) {
                root.put("buttons", buttonInfo);
            }
        }
        if (!tables.isEmpty()) {
            List<JsonObject> tableArray = (List<JsonObject>) root.computeIfAbsent("tables", k -> new ArrayList<>());
            for (GraphMessageInfo tableInfo : tables) {
                tableArray.add(tableInfo.table.toHtmlJson());
            }
            root.put("tables", tableArray);
        }
        if (includeFiles) {
            Map<String, String> attachments = root.computeIfAbsent("attachments", k -> new LinkedHashMap<>());
            for (Map.Entry<String, byte[]> entry : images.entrySet()) {
                String name = entry.getKey();
                String base64String = Base64.getEncoder().encodeToString(entry.getValue());
                attachments.put(name, base64String);
            }
            for (Map.Entry<String, byte[]> entry : files.entrySet()) {
                String name = entry.getKey();
                String base64String = Base64.getEncoder().encodeToString(entry.getValue());
                attachments.put(name, base64String);
            }
            if (!attachments.isEmpty()) {
                root.put("attachments", attachments);
            }
        }
        root.put("id", id);
        root.put("timestamp", timeCreated);
        root.put("author", author.getId());
    }

    @Override
    public IMessageBuilder removeButtonByLabel(String label) {
        buttons.entrySet().removeIf(entry -> entry.getValue().equals(label));
        return this;
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

        todo;

        return result.toString();
    }

    public String toSimpleHtml(boolean includeFiles) {
        StringBuilder html = new StringBuilder(build(false));
        for (Map.Entry<String, byte[]> entry : images.entrySet()) {
            String name = entry.getKey();
            String base64String = Base64.getEncoder().encodeToString(entry.getValue());
            html.append("<img src=\"data:image/png;base64,").append(base64String).append("\" alt=\"").append(name).append("\">");
        }
        if (includeFiles) {
            for (Map.Entry<String, byte[]> entry : files.entrySet()) {
                String name = entry.getKey();
                // use <pre>
                html.append("### `").append(name).append("`\n");
                html.append("<pre>").append(new String(entry.getValue())).append("</pre>\n");
            }
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
        todo;
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
        todo;
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
        this.links.clear();
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

    public boolean isEmpty() {
        return content.isEmpty() && embeds.isEmpty() && buttons.isEmpty() && images.isEmpty() && files.isEmpty() && links.isEmpty() && tables.isEmpty();
    }
}
