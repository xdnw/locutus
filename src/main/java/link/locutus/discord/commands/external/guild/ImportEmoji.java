package link.locutus.discord.commands.external.guild;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.FileUtil;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.io.PagePriority;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

public class ImportEmoji extends Command {
    public ImportEmoji() {
        super(CommandCategory.GUILD_MANAGEMENT);
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return Roles.ADMIN.has(user, server);
    }

    @Override
    public String help() {
        return super.help() + " <guildId>";
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() != 1) {
            return usage(args.size(), 1, channel);
        }
        String arg = args.get(0);
        if (arg.startsWith("https://discord.com/channels/")) {
        } else if (arg.startsWith("http")) {
            List<Future<?>> tasks = new ArrayList<>();
            byte[] bytes = FileUtil.readBytesFromUrl(PagePriority.DISCORD_EMOJI_URL.ordinal(), arg);
            if (bytes != null) {
                Icon icon = Icon.from(bytes);
                String[] split = arg.split("/");
                String name = split[split.length - 1];
                if (name.lastIndexOf('.') != -1) {
                    name = name.substring(0, name.lastIndexOf('.'));
                }
                tasks.add(RateLimitUtil.queue(guild.createEmote(name, icon)));
            }
            for (Future<?> task : tasks) {
                task.get();
            }
            return "Added: `" + arg + "`";
        }
        Guild other = Locutus.imp().getDiscordApi().getGuildById(Long.parseLong(args.get(0)));
        if (other == null) return "Unknown guild id: `" + args.get(0) + "`";

        List<Emote> emotes = other.getEmotes();

        Message msg = RateLimitUtil.complete(channel.sendMessage("Creating emotes..."));


        Guild guild = guild;
        List<Future<?>> tasks = new ArrayList<>();
        for (Emote emote : emotes) {
            if (emote.isManaged() || !emote.isAvailable()) {
                continue;
            }
            String url = emote.getImageUrl();
            byte[] bytes = FileUtil.readBytesFromUrl(PagePriority.DISCORD_EMOJI_URL.ordinal(), url);

            RateLimitUtil.queue(channel.editMessageById(msg.getIdLong(), "Creating emote: " + emote.getName() + " | " + url));

            if (bytes != null) {
                Icon icon = Icon.from(bytes);
                tasks.add(RateLimitUtil.queue(guild.createEmote(emote.getName(), icon)));
            }
        }
        for (Future<?> task : tasks) {
            task.get();
        }
        return "Done!";
    }


}
