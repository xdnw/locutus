package link.locutus.discord.commands.manager.v2.impl.discord;

import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.rankings.table.TimeNumericTable;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import rocker.guild.ia.message;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;

import static net.dv8tion.jda.api.interactions.components.buttons.Button.ID_MAX_LENGTH;

public class DiscordMessageBuilder implements IMessageBuilder {

    private final IMessageIO parent;
    public long id;
    public long timeCreated;
    public User author;

    public final StringBuilder content = new StringBuilder();
    public final List<Button> buttons = new ArrayList<>();
    public final List<MessageEmbed> embeds = new ArrayList<>();
    public final Map<String, byte[]> images = new HashMap<>();
    public final Map<String, byte[]> files = new HashMap<>();
    public final Map<String, String> remapLongCommands = new HashMap<>();

    public DiscordMessageBuilder(MessageChannel channel, Message message) {
        this(new DiscordChannelIO(channel, () -> message), message);
    }

    public Message build(boolean includeContent) {
        MessageBuilder discBuilder = new MessageBuilder();
        if (!buttons.isEmpty()) discBuilder.setActionRows(ActionRow.partitionOf(buttons));

        if (!embeds.isEmpty()) {
            if (!remapLongCommands.isEmpty()) {
                MessageEmbed embed = embeds.get(0);
                EmbedBuilder builder = new EmbedBuilder(embed);
                List<NameValuePair> pairs = remapLongCommands.entrySet().stream()
                        .map((Function<Map.Entry<String, String>, NameValuePair>)
                                e -> new BasicNameValuePair(e.getKey(), e.getValue()))
                        .collect(Collectors.toList());
                String query = URLEncodedUtils.format(pairs, "UTF-8");

                builder.setThumbnail("https://example.com?" + query);
                embeds.set(0, builder.build());
//            embed.setImage("https://example.com?" + query);
            }

            discBuilder.setEmbeds(embeds);
        } else if (!remapLongCommands.isEmpty()) {
            throw new IllegalStateException("Cannot remap long commands without embeds: " + StringMan.getString(remapLongCommands));
        }

        if (includeContent && !content.isEmpty()) discBuilder.setContent(content.toString());

        return discBuilder.build();
    }
    public DiscordMessageBuilder(IMessageIO parent, @Nullable Message message) {
        if (message != null) {
            id = message.getIdLong();
            content.append(message.getContentRaw());
            buttons.addAll(message.getButtons());
            embeds.addAll(message.getEmbeds());
            if (!this.embeds.isEmpty()) {
                Map<String, String> reactions = DiscordUtil.getReactions(embeds.get(0));
                if (reactions != null && !reactions.isEmpty()) {
                    remapLongCommands.putAll(reactions);
                    Set<String> buttonIds = buttons.stream().map(Button::getId).collect(Collectors.toSet());
                    for (Map.Entry<String, String> entry : reactions.entrySet()) {
                        String label = entry.getKey();
                        if (!buttonIds.contains(label)) {
                            String cmd = entry.getValue();
                            buttons.add(Button.primary(label, label));
                        }
                    }
                }
            }
            try {
            this.timeCreated = message.getTimeCreated().toInstant().toEpochMilli();
            this.author = message.getAuthor();
            } catch (UnsupportedOperationException ignore) {}
//            for (Message.Attachment attachment : message.getAttachments()) {
//
//            }
        }
        this.parent = parent;
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
        this.remapLongCommands.clear();
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
        EmbedBuilder builder = new EmbedBuilder().setTitle(title);
        if (body != null && !body.isEmpty()) {
            builder = builder.appendDescription(body);
        }
        return embed(builder.setFooter(footer == null || footer.isEmpty() ? null : footer).build());
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
        if (command.length() > ID_MAX_LENGTH) {
            int id = remapLongCommands.size();
            for (; remapLongCommands.containsKey(id + ""); id++);
            String cmdLong = command;
            command = id + "";
            remapLongCommands.put(command, cmdLong);
        }
        if (message.equalsIgnoreCase("cancel")) {
            buttons.add(Button.danger(command, message));
        } else {
            buttons.add(Button.primary(command, message));
        }
        return this;
    }

    @Override
    public IMessageBuilder linkButton(String url, String message) {
        buttons.add(Button.link(url, message));
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
    public IMessageBuilder graph(TimeNumericTable table) {
        try {
            images.put(table.getName(), table.write());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return this;
    }
}
