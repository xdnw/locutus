package link.locutus.discord.commands.manager.v2.command;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import link.locutus.discord.commands.rankings.table.TableNumberFormat;
import link.locutus.discord.commands.rankings.table.TimeFormat;
import link.locutus.discord.commands.rankings.table.TimeNumericTable;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static link.locutus.discord.util.MarkupUtil.formatDiscordMarkdown;
import static link.locutus.discord.util.MarkupUtil.markdownToHTML;

public abstract class AMessageBuilder implements IMessageBuilder {
    public final StringBuilder content = new StringBuilder();
    public final Map<String, String> buttons = new LinkedHashMap<>();
    public final Map<String, String> links = new LinkedHashMap<>();
    public final List<GraphMessageInfo> tables = new ArrayList<>();
    public final List<MessageEmbed> embeds = new ArrayList<>();
    public final Map<String, byte[]> images = new HashMap<>();
    public final Map<String, byte[]> files = new HashMap<>();
    private final IMessageIO parent;
    public long id;
    public long timeCreated;
    public User author;

    public void flatten() {
        for (MessageEmbed embed : embeds) {
            content.append("## " + embed.getTitle() + "\n");
            content.append(">>> " + embed.getDescription() + "\n");
            MessageEmbed.Footer embedFooter = embed.getFooter();
            String footerText = embedFooter != null ? embedFooter.getText() : null;
            List<MessageEmbed.Field> fields = embed.getFields();
            if (fields != null) {
                for (MessageEmbed.Field field : fields) {
                    content.append("> **" + field.getName() + "**: " + field.getValue() + "\n");
                }
            }
            if (footerText != null && footerText.isEmpty()) {
                content.append("> _" + footerText + "_\n");
            }
            embeds.clear();
            buttons.clear();
            for (Map.Entry<String, String> entry : links.entrySet()) {
                content.append("> [" + entry.getValue() + "](" + entry.getKey() + ")\n");
            }
            links.clear();
        }

    }

    public AMessageBuilder(IMessageIO parent, long id, long timeCreated, User author) {
        this.parent = parent;
        this.id = id;
        this.timeCreated = timeCreated;
        this.author = author;
    }

    public String getContent() {
        return content.toString().trim();
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
    public void addJson(Map<String, Object> root, boolean includeFiles, boolean includeButtons, boolean includeTables) {
        if (!content.isEmpty()) {
            String existing = root.computeIfAbsent("content", k -> "").toString().trim();
            root.put("content", existing + content.toString().trim());
        }
        if (!embeds.isEmpty()) {
            List<Map<String, Object>> embedArray = (List<Map<String, Object>>) root.computeIfAbsent("embeds", k -> new ArrayList<>());
            for (MessageEmbed embed : embeds) {
                embedArray.add(embed.toData().toMap());
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
        if (includeTables) {
            if (!tables.isEmpty()) {
                List<JsonObject> tableArray = (List<JsonObject>) root.computeIfAbsent("tables", k -> new ArrayList<>());
                for (GraphMessageInfo tableInfo : tables) {
                    tableArray.add(tableInfo.table().toHtmlJson(tableInfo.timeFormat(), tableInfo.numberFormat(), tableInfo.origin()));
                }
                root.put("tables", tableArray);
            }
        }
        if (includeFiles) {
            Map<String, String> attachments = (Map<String, String>) root.computeIfAbsent("attachments", k -> new LinkedHashMap<>());
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

    public void appendJson(JsonObject json) {
        // Load content
        if (json.has("content")) {
            this.content.append(json.get("content").getAsString());
        }

        // Load embeds
        if (json.has("embeds")) {
            for (var embedJson : json.getAsJsonArray("embeds")) {
                JsonObject embedObj = embedJson.getAsJsonObject();
                EmbedBuilder builder = new EmbedBuilder();
                String titleOrNull = embedObj.has("title") ? embedObj.get("title").getAsString() : null;
                if (titleOrNull != null) builder.setTitle(titleOrNull);
                String descriptionOrNull = embedObj.has("description") ? embedObj.get("description").getAsString() : null;
                if (descriptionOrNull != null) builder.setDescription(descriptionOrNull);
                String footerOrNull = embedObj.has("footer") ? embedObj.get("footer").getAsString() : null;
                if (footerOrNull != null) builder.setFooter(footerOrNull);
                String urlOrNull = embedObj.has("url") ? embedObj.get("url").getAsString() : null;
                if (urlOrNull != null) builder.setUrl(urlOrNull);
                JsonArray fieldsOrNull = embedObj.has("fields") ? embedObj.getAsJsonArray("fields") : null;
                if (fieldsOrNull != null) {
                    for (var fieldJson : fieldsOrNull) {
                        JsonObject fieldObj = fieldJson.getAsJsonObject();
                        String name = fieldObj.get("name").getAsString();
                        String value = fieldObj.get("value").getAsString();
                        boolean inline = fieldObj.has("inline") && fieldObj.get("inline").getAsBoolean();
                        builder.addField(name, value, inline);
                    }
                }
                this.embeds.add(builder.build());
            }
        }

        // Load buttons
        if (json.has("buttons")) {
            for (var buttonJson : json.getAsJsonArray("buttons")) {
                JsonObject buttonObj = buttonJson.getAsJsonObject();
                if (buttonObj.has("cmd")) {
                    commandButton(buttonObj.get("cmd").getAsString(), buttonObj.get("label").getAsString());
                } else if (buttonObj.has("href")) {
                    linkButton(buttonObj.get("href").getAsString(), buttonObj.get("label").getAsString());
                }
            }
        }

        // Load tables
        if (json.has("tables")) {
            for (var tableJson : json.getAsJsonArray("tables")) {
                this.tables.add(GraphMessageInfo.fromJson(tableJson.getAsJsonObject()));
            }
        }

        // Load attachments (images and files)
        if (json.has("attachments")) {
            JsonObject attachments = json.getAsJsonObject("attachments");
            for (var entry : attachments.entrySet()) {
                String name = entry.getKey();
                byte[] data = Base64.getDecoder().decode(entry.getValue().getAsString());
                if (name.endsWith(".png") || name.endsWith(".jpg")) {
                    this.images.put(name, data);
                } else {
                    this.files.put(name, data);
                }
            }
        }

        // Load id
        if (json.has("id")) {
            this.id = json.get("id").getAsLong();
        }

        // Load timestamp
        if (json.has("timestamp")) {
            this.timeCreated = json.get("timestamp").getAsLong();
        }

        // Load author
        if (json.has("author")) {
            long id = json.get("author").getAsLong();
            this.author = DiscordUtil.getUser(id);
        }
    }

    @Override
    public IMessageBuilder removeButtonByLabel(String label) {
        buttons.entrySet().removeIf(entry -> entry.getValue().equals(label));
        return this;
    }

    private Guild getGuildOrNull() {
        return parent != null ? parent.getGuildOrNull() : null;
    }

    public String toSimpleHtml(boolean includeFiles, boolean includeButtons) {
        StringBuilder html = new StringBuilder();
        html.append("<p>").append(markdownToHTML(formatDiscordMarkdown(content.toString().trim(), getGuildOrNull()))).append("</p>");
        for (MessageEmbed embed : embeds) {
            String title = embed.getTitle();
            String description = markdownToHTML(formatDiscordMarkdown(embed.getDescription(), getGuildOrNull()));
            String footerText = null;
            MessageEmbed.Footer footer = embed.getFooter();
            if (footer != null) {
                footerText = markdownToHTML(formatDiscordMarkdown(footer.getText(), getGuildOrNull()));
            }
            List<MessageEmbed.Field> fields = embed.getFields();
            StringBuilder embedHtml = new StringBuilder();
            embedHtml.append("<div class=\"bg-danger img-rounded img-thumbnail card\">");
            embedHtml.append("<h3>").append(title).append("</h3>");
            embedHtml.append("<p>").append(description).append("</p>");
            if (fields != null && !fields.isEmpty()) {
                embedHtml.append("<table class=\"table table-striped table-bordered table-hover\">");
                embedHtml.append("<tr><th>Field</th><th>Value</th></tr>");
                for (MessageEmbed.Field field : fields) {
                    embedHtml.append("<tr><td>").append(field.getName()).append("</td><td>").append(field.getValue()).append("</td></tr>");
                }
                embedHtml.append("</table>");
            }
            // footer = <sub> (if present)
            if (footerText != null && !footerText.isEmpty()) {
                embedHtml.append("<sub>").append(footerText).append("</sub>");
            }
            embedHtml.append("</div>");
            html.append(embedHtml);
        }


        for (Map.Entry<String, byte[]> entry : images.entrySet()) {
            String name = entry.getKey();
            String base64String = Base64.getEncoder().encodeToString(entry.getValue());
            //
            html.append("<img class=\"img-rounded\" src=\"data:image/png;base64,").append(base64String).append("\" alt=\"").append(name).append("\">");
        }
        if (includeFiles) {
            for (Map.Entry<String, byte[]> entry : files.entrySet()) {
                String name = entry.getKey();
                // use <pre>
                html.append("<h3>").append(name).append("</h3>");
                html.append("<pre>").append(new String(entry.getValue())).append("</pre><br />");
            }
        }
        if (includeButtons) {
            for (Map.Entry<String, String> entry : buttons.entrySet()) {
                html.append("<br /><b>").append(entry.getValue()).append(":</b><kbd>").append(entry.getKey()).append("</kbd>");
            }

            for (Map.Entry<String, String> entry : links.entrySet()) {
                html.append("<br /><button class=\"btn btn-primary\" href=\"").append(entry.getKey()).append("'\">").append(entry.getValue()).append("</button>");
            }
        }

        for (GraphMessageInfo gi : tables) {
            try {
                byte[] imgData = gi.table().write(gi.timeFormat(), gi.numberFormat());
                String base64String = Base64.getEncoder().encodeToString(imgData);
                html.append("<img class=\"img-rounded\" src=\"data:image/png;base64,").append(base64String).append("\" alt=\"").append(gi.table().getName()).append("\">");
            } catch (IOException e) {
                throw new RuntimeException(e);
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
        if (!content.isEmpty()) output.append(content.toString().trim());
        for (MessageEmbed embed : embeds) {
            output.embed(embed);
        }
        for (Map.Entry<String, String> entry : buttons.entrySet()) {
            output.commandButton(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, String> entry : links.entrySet()) {
            output.linkButton(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, byte[]> entry : images.entrySet()) {
            output.image(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, byte[]> entry : files.entrySet()) {
            output.file(entry.getKey(), entry.getValue());
        }
        for (GraphMessageInfo table : tables) {
            output.graph(table.table(), table.timeFormat(), table.numberFormat(), table.origin());
        }
        return output;
    }

    @Override
    public User getAuthor() {
        return author;
    }

    @Override
    public List<MessageEmbed> getEmbeds() {
        return embeds;
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
        links.clear();
        tables.clear();
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
        EmbedBuilder embed = new EmbedBuilder().setTitle(title).setDescription(body);
        if (footer != null && !footer.isEmpty()) {
            embed.setFooter(footer);
        }
        embeds.add(embed.build());
        return this;
    }

    @Override
    public IMessageBuilder embed(MessageEmbed embed) {
        this.embeds.add(embed);
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
        links.put(url, message);
        return this;
    }

    @Override
    public IMessageBuilder image(String name, byte[] data) {
        if (!name.endsWith(".png") && !name.endsWith(".jpg")) throw new IllegalArgumentException("Invalid image extension (only png jpg supported): `" + name + "`");
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
