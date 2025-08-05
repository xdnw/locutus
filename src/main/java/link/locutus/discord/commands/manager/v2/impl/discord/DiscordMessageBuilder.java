package link.locutus.discord.commands.manager.v2.impl.discord;

import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.commands.manager.v2.command.AMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.command.shrink.EmbedShrink;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.scheduler.KeyValue;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditData;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static link.locutus.discord.util.discord.DiscordUtil.EMBED_META_URL;
import static net.dv8tion.jda.api.components.buttons.Button.ID_MAX_LENGTH;

public class DiscordMessageBuilder extends AMessageBuilder {

    public final Map<String, String> remapLongCommands = new LinkedHashMap<>();

    public DiscordMessageBuilder(MessageChannel channel, Message message) {
        this(new DiscordChannelIO(channel, () -> message), message);
    }

    public DiscordMessageBuilder(IMessageIO discordHookIO, List<Message> responseMsgs) {
        this(discordHookIO, responseMsgs.isEmpty() ? null : responseMsgs.get(responseMsgs.size() - 1));
        for (int i = 0; i < responseMsgs.size() - 1; i++) {
            appendMessage(responseMsgs.get(i));
        }
    }

    public DiscordMessageBuilder(IMessageIO parent, @Nullable Message message) {
        super(parent, 0, 0, null);
        if (message != null) {
            id = message.getIdLong();
            appendMessage(message);
            try {
                this.timeCreated = message.getTimeCreated().toInstant().toEpochMilli();
                this.author = message.getAuthor();
            } catch (UnsupportedOperationException ignore) {
            }
        }
    }

    public DiscordMessageBuilder appendMessage(Message message) {
        contentShrink.append(message.getContentRaw());
        message.getButtons().forEach(b -> {
            String url = b.getUrl();
            if (url != null && !url.isEmpty()) {
                links.put(b.getUrl(), b.getLabel());
            } else {
                buttons.put(b.getId(), b.getLabel());
            }
        });
        for (MessageEmbed embed : message.getEmbeds()) {
            embed(embed);
        }
        return this;
    }

    @Override
    public IMessageBuilder removeButtonByLabel(String label) {
        Set<Map.Entry<String, String>> removed = buttons.entrySet().stream().filter(b -> b.getValue().equals(label)).collect(Collectors.toSet());
        for (Map.Entry<String, String> button : removed) {
            buttons.remove(button.getKey());
            remapLongCommands.remove(button.getKey());
        }
        return this;
    }

    @Override
    public IMessageBuilder writeTo(IMessageBuilder output) {
        if (output instanceof DiscordMessageBuilder discMsg) {
            discMsg.remapLongCommands.putAll(remapLongCommands);
        }
        return super.writeTo(output);
    }

    @Override
    public IMessageBuilder embed(MessageEmbed embed) {
        embeds.add(new EmbedShrink(embed));
        Map<String, String> reactions = DiscordUtil.getReactions(embed);
        if (reactions != null && !reactions.isEmpty()) {
            for (Map.Entry<String, String> entry : reactions.entrySet()) {
                String id = entry.getKey();
                String command = entry.getValue();
                remapLongCommands.put(id, command);
            }

        }
        return this;
    }

    @Override
    public void appendJson(JsonObject json) {
        super.appendJson(json);
    }

    @Override
    public void sendWhenFree() {
        RateLimitUtil.queueMessage(getParent(), new Function<IMessageBuilder, Boolean>() {
            @Override
            public Boolean apply(IMessageBuilder msg) {
                if (embeds.isEmpty() && images.isEmpty() && tables.isEmpty() && files.isEmpty() && buttons.isEmpty() && contentShrink.isEmpty()) return false;
                writeTo(msg);
                return true;
            }
        }, true, null);
    }

    public MessageEditData buildEdit(boolean includeContent) {
        MessageEditBuilder discBuilder = new MessageEditBuilder();
        List<Button> buttonObjs = toButtonObjs();
        if (!buttonObjs.isEmpty()) {
            if (buttonObjs.size() > 5) {
                List<ActionRow> rows = new ArrayList<>();
                for (int i = 0; i < buttonObjs.size(); i += 5) {
                    List<Button> group = buttonObjs.subList(i, Math.min(i + 5, buttonObjs.size()));
                    rows.add(ActionRow.of(group));
                }
                discBuilder.setComponents(rows);
            } else {
                discBuilder.setComponents(ActionRow.of(buttonObjs));
            }
        }
        if (!embeds.isEmpty()) {
            List<MessageEmbed> discEmbeds = new ArrayList<>(embeds.stream().map(EmbedShrink::build).toList());
            if (!remapLongCommands.isEmpty()) {
                MessageEmbed embed = discEmbeds.get(0);
                EmbedBuilder builder = new EmbedBuilder(embed);
//                List<NameValuePair> pairs = remapLongCommands.entrySet().stream()
//                        .map((Function<Map.Entry<String, String>, NameValuePair>)
//                                e -> new BasicNameValuePair(e.getKey(), e.getValue()))
//                        .collect(Collectors.toList());
//                String query = URLEncodedUtils.format(pairs, "UTF-8");
                Pair<String, String> encodedPair = DiscordUtil.encodeCommands(remapLongCommands, MessageEmbed.URL_MAX_LENGTH - EMBED_META_URL.length());
                if (encodedPair.first() != null) {
                    builder.setThumbnail(EMBED_META_URL + encodedPair.first());
                }
                if (encodedPair.second() != null) {
                    builder.setImage(EMBED_META_URL + encodedPair.second());
                }
                discEmbeds.set(0, builder.build());
            }
            discBuilder.setEmbeds(discEmbeds);
        } else if (!remapLongCommands.isEmpty()) {
            throw new IllegalStateException("Cannot remap long commands without embeds: " + StringMan.getString(remapLongCommands));
        }

        if (includeContent && !contentShrink.isEmpty()) {
            contentShrink.shrink(Message.MAX_CONTENT_LENGTH);
            discBuilder.setContent(contentShrink.toString().trim());
        }

        return discBuilder.build();
    }

    private List<Button> toButtonObjs() {
        List<Button> buttonObjs = new ArrayList<>();
        for (Map.Entry<String, String> entry : buttons.entrySet()) {
            String id = entry.getKey();
            String label = entry.getValue();
            if (id.startsWith("http://") || id.startsWith("https://")) {
                buttonObjs.add(Button.link(id, label));
            } else if (label.equalsIgnoreCase("edit")) {
                buttonObjs.add(Button.success(id, label));
            } else if (label.equalsIgnoreCase("cancel")) {
                buttonObjs.add(Button.danger(id, label));
            } else {
                buttonObjs.add(Button.primary(id, label));
            }
        }
        for (Map.Entry<String, String> entry : links.entrySet()) {
            String url = entry.getKey();
            String label = entry.getValue();
            buttonObjs.add(Button.link(url, label));
        }
        return buttonObjs;
    }

    public List<MessageCreateData> build(boolean includeContent) {
        List<MessageCreateBuilder> all = new ObjectArrayList<>();
        MessageCreateBuilder latest = new MessageCreateBuilder();
        all.add(latest);
        // add embeds
        if (!embeds.isEmpty()) {
            List<EmbedBuilder> toAdd = new ObjectArrayList<>();
            if (embeds.size() == 1 && ((images.size() == 1 && tables.size() == 0) || (images.size() == 0 && tables.size() == 1))) {
                EmbedShrink embed = embeds.get(0);
                EmbedBuilder builder = new EmbedShrink(embed).builder();
                String imgName;
                if (images.size() == 1) {
                    Map.Entry<String, byte[]> entry = images.entrySet().iterator().next();
                    imgName = entry.getKey();
                } else {
                    imgName = "img.png";
                }
                String name = "attachment://" + imgName;
                builder.setImage(name);
                toAdd.add(builder);
            } else {
                for (EmbedShrink embed : embeds) {
                    EmbedBuilder builder = new EmbedShrink(embed).builder();
                    toAdd.add(builder);
                }
            }
            if (!remapLongCommands.isEmpty()) {
                EmbedBuilder builder = toAdd.get(toAdd.size() - 1);
                Pair<String, String> encodedPair = DiscordUtil.encodeCommands(remapLongCommands, MessageEmbed.URL_MAX_LENGTH - EMBED_META_URL.length());
                if (encodedPair.first() != null) {
                    builder.setThumbnail(EMBED_META_URL + encodedPair.first());
                }
                if (encodedPair.second() != null) {
                    builder.setImage(EMBED_META_URL + encodedPair.second());
                }
            }
            for (int i = 0; i < toAdd.size(); i++) {
                EmbedBuilder builder = toAdd.get(i);
                latest.addEmbeds(builder.build());
                if (latest.getEmbeds().size() == 10 && i < toAdd.size() - 1) {
                    latest = new MessageCreateBuilder();
                    all.add(latest);
                }
            }
        } else if (!remapLongCommands.isEmpty()) {
            throw new IllegalStateException("Cannot remap long commands without embeds: " + StringMan.getString(remapLongCommands));
        }
        // add buttons
        List<Button> buttons = toButtonObjs();
        if (!buttons.isEmpty()) {
            List<ActionRow> rows = new ArrayList<>();
            while (!buttons.isEmpty()) {
                List<Button> group = buttons.subList(0, Math.min(5, buttons.size()));
                rows.add(ActionRow.of(group));
                buttons = buttons.subList(group.size(), buttons.size());
            }
            latest.setComponents(rows);
        }
        // Files
        if (includeContent && (!files.isEmpty() || !images.isEmpty() || !tables.isEmpty())) {
            List<FileUpload> upload = new ArrayList<>();
            for (Map.Entry<String, byte[]> entry : files.entrySet()) {
                upload.add(FileUpload.fromData(entry.getValue(), entry.getKey()));
            }
            for (Map.Entry<String, byte[]> entry : images.entrySet()) {
                upload.add(FileUpload.fromData(entry.getValue(), entry.getKey()));
            }
            List<Map.Entry<String, byte[]>> tableData = buildTables();
            for (Map.Entry<String, byte[]> entry : tableData) {
                upload.add(FileUpload.fromData(entry.getValue(), entry.getKey()));
            }
            if (!upload.isEmpty()) {
                latest.setFiles(upload);
            }
        }
        // Content
        if (includeContent && !contentShrink.isEmpty()) {
            int size = contentShrink.getSize();
            if (size > 20000) {
                String str = contentShrink.toString();
                byte[] data = str.getBytes(StandardCharsets.UTF_8);
                int maxSize = Message.MAX_FILE_SIZE;
                if (data.length >= maxSize) {
                    data = Arrays.copyOf(data, maxSize - 2);
                    latest.setContent("Error: Message too long, truncated to " + (maxSize - 2) + " bytes.");
                }
                latest.setFiles(List.of(FileUpload.fromData(data, "message.txt")));
            } else {
                List<String> messages = contentShrink.split(Message.MAX_CONTENT_LENGTH, Message.MAX_CONTENT_LENGTH / 4);
                for (int i = 0; i < messages.size(); i++) {
                    MessageCreateBuilder builder;
                    if (all.size() >= i + 1) {
                        builder = all.get(i);
                    } else {
                        builder = new MessageCreateBuilder();
                        all.add(builder);
                    }
                    builder.setContent(messages.get(i));
                }
            }
        }
        return all.stream().map(MessageCreateBuilder::build).collect(Collectors.toList());
    }

    public List<Map.Entry<String, byte[]>> buildTables() {
        List<Map.Entry<String, byte[]>> tables = new ArrayList<>();
        for (GraphMessageInfo gmi : this.tables) {
            try {
                byte[] imgData = gmi.table().write(gmi.timeFormat(), gmi.numberFormat(), gmi.type(), gmi.origin());
                String fileName = gmi.table().getName().replaceAll("[^a-zA-Z0-9.-]", "") + ".png";
                tables.add(new KeyValue<>(fileName, imgData));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return tables;
    }

    @Override
    public IMessageBuilder clearButtons() {
        this.buttons.clear();
        this.remapLongCommands.clear();
        return this;
    }

    @Override
    public IMessageBuilder embed(String title, String body, String footer) {
        EmbedBuilder builder = new EmbedBuilder().setTitle(title);
        if (body != null && !body.isEmpty()) {
            try {
                builder = builder.appendDescription(body);
            } catch (IllegalArgumentException e) {
                return file(title + ".txt", body);
            }
        }
        return embed(builder.setFooter(footer == null || footer.isEmpty() ? null : footer).build());
    }

    @Override
    public IMessageBuilder commandButton(String command, String message) {
        if (command.length() > ID_MAX_LENGTH) {
            int id = remapLongCommands.size();
            for (; remapLongCommands.containsKey(id + ""); id++) ;
            String cmdLong = command;
            command = id + "";
            remapLongCommands.put(command, cmdLong);
        }
        buttons.put(command, message);
        return this;
    }
}
