package link.locutus.discord.commands.manager.v2.command;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import link.locutus.discord.commands.manager.v2.table.TableNumberFormat;
import link.locutus.discord.commands.manager.v2.table.TimeFormat;
import link.locutus.discord.commands.manager.v2.table.TimeNumericTable;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.web.commands.binding.value_types.GraphType;
import link.locutus.discord.web.commands.binding.value_types.WebGraph;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static link.locutus.discord.util.MarkupUtil.formatDiscordMarkdown;
import static link.locutus.discord.util.MarkupUtil.markdownToHTML;

public abstract class AMessageBuilder implements IMessageBuilder {
    public final ShrinkList contentShrink = new ShrinkList();

    public final Map<String, String> buttons = new LinkedHashMap<>();
    public final Map<String, String> links = new LinkedHashMap<>();

    public final List<GraphMessageInfo> tables = new ArrayList<>();

    public final List<ShrinkableEmbed> embeds = new ArrayList<>();

    public final Map<String, byte[]> images = new HashMap<>();
    public final Map<String, byte[]> files = new HashMap<>();

    private final IMessageIO parent;
    public long id;
    public long timeCreated;
    public User author;

    public void flatten() {
        for (ShrinkableEmbed embed : embeds) {
            embed.shrinkDefault();
            contentShrink.add("## ").add(embed.getTitle()).add("\n")
                    .add(">>> ").add(embed.getDescription()).add("\n");
            Shrinkable footer = embed.getFooter();
            if (footer != null && !footer.get().isEmpty()) {
                contentShrink.add("_").add(footer).add("_\n");
            }
            if (embed.getFields() != null) {
                for (ShrinkableField field : embed.getFields()) {
                    contentShrink.add("> **").add(field.name).add("**: ").add(field.value).add("\n");
                }
            }
        }
        embeds.clear();
        buttons.clear();
        for (Map.Entry<String, String> entry : links.entrySet()) {
            contentShrink.add("> [" + entry.getValue() + "](" + entry.getKey() + ")\n");
        }
        links.clear();
    }

    public AMessageBuilder(IMessageIO parent, long id, long timeCreated, User author) {
        this.parent = parent;
        this.id = id;
        this.timeCreated = timeCreated;
        this.author = author;
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
    public IMessageBuilder graph(TimeNumericTable table, TimeFormat timeFormat, TableNumberFormat numberFormat, GraphType type, long originDate) {
        tables.add(new GraphMessageInfo(table, timeFormat, numberFormat, type, originDate));
        return this;
    }

    @Override
    public void addJson(Map<String, Object> root, boolean includeFiles, boolean includeButtons, boolean includeTables, boolean shrinkEmbeds) {
        if (!contentShrink.isEmpty()) {
            String existing = root.computeIfAbsent("content", k -> "").toString().trim();
            root.put("content", existing + contentShrink.toString().trim());
        }
        if (!embeds.isEmpty()) {
            List<Map<String, Object>> embedArray = (List<Map<String, Object>>) root.computeIfAbsent("embeds", k -> new ArrayList<>());
            for (ShrinkableEmbed embed : embeds) {
                if (shrinkEmbeds) embed.shrinkDefault();
                embedArray.add(embed.toData());
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
                List<Object> tableArray = (List<Object>) root.computeIfAbsent("tables", k -> new ArrayList<>());
                for (GraphMessageInfo tableInfo : tables) {
                    GraphType type = tableInfo.type();
                    if (type == null) {
                        type = tableInfo.table().isBar() ? GraphType.SIDE_BY_SIDE_BAR : GraphType.LINE;
                    }
                    WebGraph html = tableInfo.table().toHtmlJson(tableInfo.timeFormat(), tableInfo.numberFormat(), tableInfo.type(), tableInfo.origin());
                    tableArray.add(html);
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
            this.contentShrink.add(json.get("content").getAsString());
        }

        // Load embeds
        if (json.has("embeds")) {
            for (var embedJson : json.getAsJsonArray("embeds")) {
                JsonObject embedObj = embedJson.getAsJsonObject();
                ShrinkableEmbed builder = new ShrinkableEmbed();
                String titleOrNull = embedObj.has("title") ? embedObj.get("title").getAsString() : null;
                if (titleOrNull != null) builder.setTitle(titleOrNull);
                String descriptionOrNull = embedObj.has("description") ? embedObj.get("description").getAsString() : null;
                if (descriptionOrNull != null) builder.setDescription(descriptionOrNull);
                String footerOrNull = embedObj.has("footer") ? embedObj.get("footer").getAsString() : null;
                if (footerOrNull != null) builder.setFooter(footerOrNull);
                String urlOrNull = embedObj.has("url") ? embedObj.get("url").getAsString() : null;
//                if (urlOrNull != null) builder.setUrl(urlOrNull);
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
                this.embeds.add(builder);
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
        html.append("<p>").append(markdownToHTML(formatDiscordMarkdown(contentShrink.toString().trim(), getGuildOrNull()))).append("</p>");
        for (ShrinkableEmbed embed : embeds) {
            String title = embed.getTitle().get();
            String description = markdownToHTML(formatDiscordMarkdown(embed.getDescription().get(), getGuildOrNull()));
            String footerText = null;
            Shrinkable footer = embed.getFooter();
            if (footer != null) {
                footerText = markdownToHTML(formatDiscordMarkdown(footer.get(), getGuildOrNull()));
            }
            List<ShrinkableField> fields = embed.getFields();
            StringBuilder embedHtml = new StringBuilder();
            embedHtml.append("<div class=\"bg-danger img-rounded img-thumbnail card\">");
            embedHtml.append("<h3>").append(title).append("</h3>");
            embedHtml.append("<p>").append(description).append("</p>");
            if (fields != null && !fields.isEmpty()) {
                embedHtml.append("<table class=\"table table-striped table-bordered table-hover\">");
                embedHtml.append("<tr><th>Field</th><th>Value</th></tr>");
                for (ShrinkableField field : fields) {
                    embedHtml.append("<tr><td>").append(field.name.get()).append("</td><td>").append(field.value.get()).append("</td></tr>");
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
                byte[] imgData = gi.table().write(gi.timeFormat(), gi.numberFormat(), gi.type(), gi.origin());
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
        if (!contentShrink.isEmpty()) {
            contentShrink.items.forEach(output::append);
        }
        for (ShrinkableEmbed embed : embeds) {
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
            output.graph(table.table(), table.timeFormat(), table.numberFormat(), table.type(), table.origin());
        }
        return output;
    }

    @Override
    public User getAuthor() {
        return author;
    }

    @Override
    public List<ShrinkableEmbed> getEmbeds() {
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
        contentShrink.items.clear();
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
        this.contentShrink.add(content);
        return this;
    }

    @Override
    public IMessageBuilder append(Shrinkable msg) {
        this.contentShrink.add(msg);
        return this;
    }

    @Override
    public IMessageBuilder embed(String title, String body) {
        return embed(title, body, null);
    }

    @Override
    public IMessageBuilder embed(String title, String body, String footer) {
        ShrinkableEmbed embed = new ShrinkableEmbed().setTitle(title).setDescription(body);
        if (footer != null && !footer.isEmpty()) {
            embed.setFooter(footer);
        }
        embeds.add(embed);
        return this;
    }

    @Override
    public IMessageBuilder embed(ShrinkableEmbed embed) {
        this.embeds.add(embed);
        return this;
    }

    @Override
    public IMessageBuilder commandInline(CommandRef ref) {
        contentShrink.add(ref.toSlashCommand());
        return this;
    }

    @Override
    public IMessageBuilder commandLinkInline(CommandRef ref) {
        contentShrink.add(ref.toSlashMention());
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
        return contentShrink.isEmpty() && embeds.isEmpty() && buttons.isEmpty() && images.isEmpty() && files.isEmpty() && links.isEmpty() && tables.isEmpty();
    }
}
