package link.locutus.discord.web.commands;

import com.google.gson.JsonObject;
import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.command.AMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.config.Settings;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.web.jooby.WebRoot;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.utils.data.DataArray;
import net.dv8tion.jda.api.utils.data.DataObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class WebMessage extends AMessageBuilder {

    public WebMessage(IMessageIO parent) {
        super(parent, UUID.randomUUID().getMostSignificantBits(), System.currentTimeMillis(), null);
    }

    @Override
    public IMessageBuilder embed(String title, String body) {
        return embed(title, MarkupUtil.formatDiscordMarkdown(body, getParent().getGuildOrNull()), null);
    }

    @Override
    public IMessageBuilder embed(String title, String body, String footer) {
        embeds.add(new EmbedBuilder().setTitle(title).appendDescription(MarkupUtil.formatDiscordMarkdown(body, getParent().getGuildOrNull())).setFooter(footer).build());
        return this;
    }

    @Override
    public IMessageBuilder embed(MessageEmbed embed) {
        MessageEmbed newEmbed = new EmbedBuilder(embed).setDescription(MarkupUtil.formatDiscordMarkdown(embed.getDescription(), getParent().getGuildOrNull())).build();
        this.embeds.add(newEmbed);
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
    public CompletableFuture<IMessageBuilder> send() {
        author = Locutus.imp().getDiscordApi().getUserById(Settings.INSTANCE.APPLICATION_ID);
        timeCreated = System.currentTimeMillis();
        return getParent().send(this);
    }

    private List<Map.Entry<String, File>> diskFiles = null;

    private List<Map.Entry<String, File>> generateFileList() {
        if (diskFiles == null || diskFiles.size() != files.size() + images.size()) {
            diskFiles = new ArrayList<>();
            List<Map.Entry<String, byte[]>> allFiles = new ArrayList<>();
            allFiles.addAll(files.entrySet());
            allFiles.addAll(images.entrySet());
            for (Map.Entry<String, byte[]> entry : allFiles) {
                String filename = entry.getKey();
                byte[] bytes = entry.getValue();
                try {
                    String fileId = UUID.randomUUID().toString();
                    File file = new File(WebRoot.getInstance().getFileRoot(), fileId);
                    file.getParentFile().mkdirs();
                    Files.write(file.toPath(), bytes);
                    diskFiles.add(new AbstractMap.SimpleEntry<>(filename, file));
                    file.deleteOnExit();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return diskFiles;
    }

    public JsonObject build() {
        Map<String, Object> root = new LinkedHashMap<>();
        addJson(root, false, true, true);
        if (!diskFiles.isEmpty()) {

        }
        if (!files.isEmpty() || !images.isEmpty()) {
            Map<String, String> urlFileNames = getUrlFileNames();
            System.out.println("Add files " + urlFileNames);
            if (!urlFileNames.isEmpty()) {
                root.put("files", urlFileNames);
            }
        }
        return StringMan.toJson(root).getAsJsonObject();
    }

    public Map<String, String> getUrlFileNames() {
        Map<String, String> urlFileNames = new LinkedHashMap<>();
        for (Map.Entry<String, File> entry : generateFileList()) {
            urlFileNames.put(entry.getValue().getName(), entry.getKey());
        }
        return urlFileNames;
    }
}
