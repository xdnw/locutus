package com.boydti.discord.commands.external.guild;

import com.boydti.discord.Locutus;
import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.commands.manager.CommandCategory;
import com.boydti.discord.user.Roles;
import com.boydti.discord.util.FileUtil;
import com.boydti.discord.util.PnwUtil;
import com.boydti.discord.util.RateLimitUtil;
import com.boydti.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Emote;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Icon;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;

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
    public String onCommand(MessageReceivedEvent event, List<String> args) throws Exception {
        if (args.size() != 1) {
            return usage(event);
        }
        String arg = args.get(0);
        if (arg.startsWith("https://discord.com/channels/")) {

        } else if (arg.startsWith("http")) {
            byte[] bytes = FileUtil.readBytesFromUrl(arg);
            if (bytes != null) {
                Icon icon = Icon.from(bytes);
                String[] split = arg.split("/");
                String name = split[split.length - 1];
                if (name.lastIndexOf('.') != -1) {
                    name = name.substring(0, name.lastIndexOf('.'));
                }
                event.getGuild().createEmote(name, icon).complete();
            }
            return "Added: `" + arg + "`";
        }
        Guild other = Locutus.imp().getDiscordApi().getGuildById(Long.parseLong(args.get(0)));
        if (other == null) return "Unknown guild id: `" + args.get(0) + "`";

        List<Emote> emotes = other.getEmotes();

        Message msg = com.boydti.discord.util.RateLimitUtil.complete(event.getChannel().sendMessage("Creating emotes..."));


        Guild guild = event.getGuild();
        for (Emote emote : emotes) {
            if (emote.isManaged() || !emote.isAvailable()) {
                continue;
            }
            String url = emote.getImageUrl();
            byte[] bytes = FileUtil.readBytesFromUrl(url);

            RateLimitUtil.queue(event.getChannel().editMessageById(msg.getIdLong(), "Creating emote: " + emote.getName() + " | " + url));

            if (bytes != null) {
                Icon icon = Icon.from(bytes);
                guild.createEmote(emote.getName(), icon).complete();
            }
        }
        return "Done!";
    }


}
