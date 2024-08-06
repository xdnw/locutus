package link.locutus.discord.commands.manager.v2.impl.discord;

import com.google.gson.JsonObject;
import link.locutus.discord.commands.manager.v2.command.AMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.rankings.table.TableNumberFormat;
import link.locutus.discord.commands.rankings.table.TimeFormat;
import link.locutus.discord.commands.rankings.table.TimeNumericTable;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.LayoutComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

import static net.dv8tion.jda.api.interactions.components.buttons.Button.ID_MAX_LENGTH;

public class DiscordMessageBuilder extends AMessageBuilder {

    public final Map<String, String> remapLongCommands = new HashMap<>();

    public DiscordMessageBuilder(MessageChannel channel, Message message) {
        this(new DiscordChannelIO(channel, () -> message), message);
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

    private void loadCommandsFromEmbeds() {
        for (MessageEmbed embed : embeds) {
            Map<String, String> reactions = DiscordUtil.getReactions(embed);
            if (reactions != null && !reactions.isEmpty()) {
                remapLongCommands.putAll(reactions);
            }
        }
    }

    @Override
    public void appendJson(JsonObject json) {
        super.appendJson(json);
        loadCommandsFromEmbeds();
    }

    @Override
    public void sendWhenFree() {
        RateLimitUtil.queueMessage(getParent(), new Function<IMessageBuilder, Boolean>() {
            @Override
            public Boolean apply(IMessageBuilder msg) {
                if (embeds.isEmpty() && images.isEmpty() && tables.isEmpty() && files.isEmpty() && buttons.isEmpty() && content.isEmpty()) return false;
                writeTo(msg);
                return true;
            }
        }, true, null);
    }

    public DiscordMessageBuilder(IMessageIO parent, @Nullable Message message) {
        super(parent, 0, 0, null);
        if (message != null) {
            id = message.getIdLong();
            content.append(message.getContentRaw());
            message.getButtons().forEach(b -> {
                String url = b.getUrl();
                if (url != null && !url.isEmpty()) {
                    links.put(b.getUrl(), b.getLabel());
                } else {
                    buttons.put(b.getId(), b.getLabel());
                }
            });
            embeds.addAll(message.getEmbeds());
            loadCommandsFromEmbeds();
            try {
                this.timeCreated = message.getTimeCreated().toInstant().toEpochMilli();
                this.author = message.getAuthor();
            } catch (UnsupportedOperationException ignore) {
            }
        }
    }

    public MessageEditData buildEdit(boolean includeContent) {
        MessageEditBuilder discBuilder = new MessageEditBuilder();
        List<Button> buttonObjs = toButtonObjs();
        if (!buttonObjs.isEmpty()) {
            if (buttonObjs.size() > 5) {
                List<LayoutComponent> rows = new ArrayList<>();
                for (int i = 0; i < buttonObjs.size(); i += 5) {
                    List<Button> group = buttonObjs.subList(i, Math.min(i + 5, buttonObjs.size()));
                    rows.add(ActionRow.of(group));
                }
                discBuilder.setComponents(rows);
            } else {
                discBuilder.setActionRow(buttonObjs);
            }
        }
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

    private List<Button> toButtonObjs() {
        List<Button> buttonObjs = new ArrayList<>();
        for (Map.Entry<String, String> entry : buttons.entrySet()) {
            String id = entry.getKey();
            String label = entry.getValue();
            if (id.startsWith("http://") || id.startsWith("https://")) {
                buttonObjs.add(Button.link(id, label));
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

    public MessageCreateData build(boolean includeContent) {
        MessageCreateBuilder discBuilder = new MessageCreateBuilder();
        List<Button> buttons = toButtonObjs();
        if (!buttons.isEmpty()) {
            while (!buttons.isEmpty()) {
                List<Button> group = buttons.subList(0, Math.min(5, buttons.size()));
                buttons = buttons.subList(group.size(), buttons.size());
                discBuilder.addActionRow(group);
            }
        }

        if (!embeds.isEmpty()) {
            if (embeds.size() == 1 && ((images.size() == 1 && tables.size() == 0) || (images.size() == 0 && tables.size() == 1))) {
                MessageEmbed embed = embeds.get(0);
                EmbedBuilder builder = new EmbedBuilder(embed);
                String imgName;
                if (images.size() == 1) {
                    Map.Entry<String, byte[]> entry = images.entrySet().iterator().next();
                    imgName = entry.getKey();
                } else {
                    imgName = "img.png";
                }
                String name = "attachment://" + imgName;
                builder.setImage(name);
                embeds.set(0, builder.build());
            }
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
            }
            discBuilder.setEmbeds(embeds);
        } else if (!remapLongCommands.isEmpty()) {
            throw new IllegalStateException("Cannot remap long commands without embeds: " + StringMan.getString(remapLongCommands));
        }
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
                discBuilder.setFiles(upload);
            }
        }
        if (includeContent && !content.isEmpty()) discBuilder.setContent(content.toString());

        return discBuilder.build();
    }

    public List<Map.Entry<String, byte[]>> buildTables() {
        List<Map.Entry<String, byte[]>> tables = new ArrayList<>();
        for (GraphMessageInfo gmi : this.tables) {
            try {
                byte[] imgData = gmi.table().write(gmi.timeFormat(), gmi.numberFormat());
                String fileName = gmi.table().getName().replaceAll("[^a-zA-Z0-9.-]", "") + ".png";
                tables.add(new AbstractMap.SimpleEntry<>(fileName, imgData));
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
        if (message.equalsIgnoreCase("cancel") || message.equalsIgnoreCase("dismiss")) {
            buttons.put(command, message);
        } else {
            buttons.put(command, message);
        }
        return this;
    }
}
