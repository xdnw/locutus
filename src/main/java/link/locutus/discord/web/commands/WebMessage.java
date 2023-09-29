package link.locutus.discord.web.commands;

import com.google.gson.JsonObject;
import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.rankings.table.TimeNumericTable;
import link.locutus.discord.config.Settings;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.web.jooby.WebRoot;
import link.locutus.discord.config.Settings;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.data.DataArray;
import net.dv8tion.jda.api.utils.data.DataObject;
import net.dv8tion.jda.internal.utils.IOUtil;
import org.jooq.meta.derby.sys.Sys;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class WebMessage implements IMessageBuilder {
    private final IMessageIO parent;
    public long id;

    public final StringBuilder content = new StringBuilder();
    public final Map<String, String> buttons = new LinkedHashMap<>();
    public final Map<String, String> links = new LinkedHashMap<>();
    public final List<MessageEmbed> embeds = new ArrayList<>();
    public final Map<String, byte[]> attachments = new HashMap<>();
    public User author;
    private long timeCreated;

    public WebMessage(IMessageIO parent) {
        this.parent = parent;
        this.id = UUID.randomUUID().getMostSignificantBits();
    }

    @Override
    public IMessageBuilder removeButtonByLabel(String label) {
        buttons.entrySet().removeIf(entry -> entry.getValue().equals(label));
        return this;
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public IMessageBuilder clear() {
        content.setLength(0);
        buttons.clear();
        links.clear();
        embeds.clear();
        attachments.clear();
        files = null;
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
        embeds.add(new EmbedBuilder().setTitle(title).appendDescription(body).setFooter(footer).build());
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
        if (!name.endsWith(".png") || !name.endsWith(".jpg")) throw new IllegalArgumentException("Invalid image extension (only png jpg supported): `" + name + "`");
        attachments.put(name, data);
        return this;
    }

    @Override
    public IMessageBuilder file(String name, byte[] data) {
        attachments.put(name, data);
        return this;
    }

    @Override
    public IMessageBuilder graph(TimeNumericTable table) {
        try {
            attachments.put(table.getName() + ".png", table.write(true));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    @Override
    public CompletableFuture<IMessageBuilder> send() {
        author = Locutus.imp().getDiscordApi().getUserById(Settings.INSTANCE.APPLICATION_ID);
        timeCreated = System.currentTimeMillis();
        return parent.send(this);
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
    public IMessageBuilder clearEmbeds() {
        this.embeds.clear();
        return this;
    }

    @Override
    public IMessageBuilder clearButtons() {
        this.buttons.clear();
        this.links.clear();
        return this;
    }

    private List<Map.Entry<String, File>> files = null;

    private List<Map.Entry<String, File>> generateFileList() {
        if (files == null || files.size() != attachments.size()) {
            files = new ArrayList<>();
            for (Map.Entry<String, byte[]> entry : attachments.entrySet()) {
                String filename = entry.getKey();
                byte[] bytes = entry.getValue();
                try {
                    String fileId = UUID.randomUUID().toString();
                    File file = new File(WebRoot.getInstance().getFileRoot(), fileId);
                    Files.write(file.toPath(), bytes);
                    files.add(new AbstractMap.SimpleEntry<>(filename, file));
                    file.deleteOnExit();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return files;
    }

    public DataObject build() {
        DataObject obj = DataObject.empty();
        if (this.content.length() != 0) {
            obj.put("content", MarkupUtil.transformURLIntoMarkup(content.toString()));
        }
        if (!this.embeds.isEmpty()) {
            if (this.embeds.size() == 1) {
                obj.put("embed", embeds.get(0));
            } else {
                DataArray array = DataArray.fromCollection(embeds);
                obj.put("embeds", array);
            }
            // reactions
        }
        if (!attachments.isEmpty()) {
            Map<String, String> urlFileNames = getUrlFileNames();
            if (!urlFileNames.isEmpty()) {
                obj.put("files", urlFileNames);
            }
        }
        if (!this.links.isEmpty()) {
            List<DataObject> buttonsData = new ArrayList<>();
            for (Map.Entry<String, String> entry : links.entrySet()) {
                DataObject buttonData = DataObject.empty();
                buttonData.put("href", entry.getKey());
                buttonData.put("label", entry.getValue());
                buttonsData.add(buttonData);
            }
            obj.put("buttons", DataArray.fromCollection(buttonsData));
        }
        if (!buttons.isEmpty()) {
            List<DataObject> buttonsData = new ArrayList<>();

            for (Map.Entry<String, String> entry : buttons.entrySet()) {
                DataObject buttonData = DataObject.empty();
                buttonData.put("cmd", entry.getKey());
                buttonData.put("label", entry.getValue());
                buttonsData.add(buttonData);
            }
            obj.put("buttons", DataArray.fromCollection(buttonsData));
        }
        obj.put("id", id + "");
        return obj;
    }

    public Map<String, String> getUrlFileNames() {
        Map<String, String> urlFileNames = new LinkedHashMap<>();
        for (Map.Entry<String, File> entry : generateFileList()) {
            urlFileNames.put(entry.getValue().getName(), entry.getKey());
        }
        return urlFileNames;
    }
}
