package link.locutus.discord.web.commands;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.command.AMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.config.Settings;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.web.commands.binding.value_types.DiscordRole;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class WebMessage extends AMessageBuilder {

    private final Map<Long, String> usernames = new Long2ObjectOpenHashMap<>();
    private final Map<Long, DiscordRole> roles = new Long2ObjectOpenHashMap<>();
    private final Map<Long, String> channels = new Long2ObjectOpenHashMap<>();

    public WebMessage(IMessageIO parent) {
        super(parent, UUID.randomUUID().getMostSignificantBits(), System.currentTimeMillis(), null);
    }

    private String formatDiscordMarkdown(String text, Guild guild) {
        return MarkupUtil.mapDiscordMarkdown(text, guild, usernames, roles, channels);
    }

    @Override
    public IMessageBuilder embed(String title, String body) {
        return embed(title, formatDiscordMarkdown(body, getParent().getGuildOrNull()), null);
    }

    @Override
    public IMessageBuilder embed(String title, String body, String footer) {
        embeds.add(new EmbedBuilder().setTitle(title).appendDescription(formatDiscordMarkdown(body, getParent().getGuildOrNull())).setFooter(footer).build());
        return this;
    }

    @Override
    public IMessageBuilder embed(MessageEmbed embed) {
        MessageEmbed newEmbed = new EmbedBuilder(embed).setDescription(formatDiscordMarkdown(embed.getDescription(), getParent().getGuildOrNull())).build();
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

//    private List<Map.Entry<String, File>> diskFiles = null;
//
//    private List<Map.Entry<String, File>> generateFileList() {
//        if (diskFiles == null || diskFiles.size() != files.size() + images.size()) {
//            diskFiles = new ArrayList<>();
//            List<Map.Entry<String, byte[]>> allFiles = new ArrayList<>();
//            allFiles.addAll(files.entrySet());
//            allFiles.addAll(images.entrySet());
//            for (Map.Entry<String, byte[]> entry : allFiles) {
//                String filename = entry.getKey();
//                byte[] bytes = entry.getValue();
//                try {
//                    String fileId = UUID.randomUUID().toString();
//                    File file = new File(WebRoot.getInstance().getFileRoot(), fileId);
//                    file.getParentFile().mkdirs();
//                    Files.write(file.toPath(), bytes);
//                    diskFiles.add(new AbstractMap.SimpleEntry<>(filename, file));
//                    file.deleteOnExit();
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//            }
//        }
//        return diskFiles;
//    }

//    public Map<String, String> getUrlFileNames() {
//        Map<String, String> urlFileNames = new LinkedHashMap<>();
//        for (Map.Entry<String, File> entry : generateFileList()) {
//            urlFileNames.put(entry.getValue().getName(), entry.getKey());
//        }
//        return urlFileNames;
//    }

    public Map<String, Object> build() {
        Map<String, Object> root = new LinkedHashMap<>();
        addJson(root, false, true, true);
        if (!files.isEmpty() || !images.isEmpty()) {
            Map<String, byte[]> dataByName = new LinkedHashMap<>();
            dataByName.putAll(files);
            dataByName.putAll(images);
            root.put("files", dataByName);
        }
        if (!usernames.isEmpty()) {
            Map<String, String> strIds = new Object2ObjectOpenHashMap<>();
            this.usernames.entrySet().forEach(entry -> strIds.put(entry.getKey() + "", entry.getValue()));
            root.put("users", strIds);
        }
        if (!roles.isEmpty()) {
            Map<String, DiscordRole> strIds = new Object2ObjectOpenHashMap<>();
            roles.entrySet().forEach(entry -> strIds.put(entry.getKey() + "", entry.getValue()));
            root.put("roles", strIds);
        }
        if (!channels.isEmpty()) {
            Map<String, String> strIds = new Object2ObjectOpenHashMap<>();
            channels.entrySet().forEach(entry -> strIds.put(entry.getKey() + "", entry.getValue()));
            root.put("channels", strIds);
        }
        return root;
    }
}
