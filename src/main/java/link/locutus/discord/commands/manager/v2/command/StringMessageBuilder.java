package link.locutus.discord.commands.manager.v2.command;

import link.locutus.discord.commands.rankings.table.TableNumberFormat;
import link.locutus.discord.commands.rankings.table.TimeFormat;
import link.locutus.discord.commands.rankings.table.TimeNumericTable;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    @Override
    public IMessageBuilder removeButtonByLabel(String label) {
        buttons.entrySet().removeIf(entry -> entry.getValue().equals(label));
        return this;
    }

    @Override
    public void sendWhenFree() {
        this.toString();
    }

    public String build() {
        StringBuilder result = new StringBuilder(content);

        if (!embeds.isEmpty()) {
            for (Map.Entry<String, String> entry : embeds.entrySet()) {
                result.append("> # ").append(entry.getKey()).append("\n").append(">>> " + entry.getValue()).append("\n\n");
            }
        }
        for (Map.Entry<String, String> entry : buttons.entrySet()) {
            result.append("\n").append(entry.getKey()).append(": ").append(entry.getValue());
        }


        return result.toString();
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
    public IMessageBuilder graph(TimeNumericTable table, TimeFormat timeFormat, TableNumberFormat numberFormat) {
        try {
            images.put(table.getName(), table.write(timeFormat, numberFormat));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return this;
    }
}
