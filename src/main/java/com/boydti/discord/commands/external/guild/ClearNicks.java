package com.boydti.discord.commands.external.guild;

import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.commands.manager.CommandCategory;
import com.boydti.discord.user.Roles;
import com.boydti.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClearNicks extends Command {
    public ClearNicks() {
        super(CommandCategory.GUILD_MANAGEMENT);
    }
    @Override
    public boolean checkPermission(Guild server, User user) {
        return Roles.ADMIN.has(user, server);
    }

    @Override
    public String help() {
        return super.help();
    }

    private Map<Long, String> previous = new HashMap<>();

    @Override
    public String onCommand(MessageReceivedEvent event, List<String> args) throws Exception {
        int failed = 0;
        String msg = null;
        for (Member member : event.getGuild().getMembers()) {
            if (member.getNickname() != null) {
                try {
                    String nick;
                    if (args.isEmpty()) {
                        previous.put(member.getIdLong(), member.getNickname());
                        nick = null;
                    } else {
                        if (args.get(0).equalsIgnoreCase("*")) {
                            nick = previous.get(member.getIdLong());
                        } else {
                            previous.put(member.getIdLong(), member.getNickname());
                            nick = DiscordUtil.trimContent(event.getMessage().getContentRaw());
                            nick = nick.substring(nick.indexOf(' ') + 1);
                        }
                    }
                    member.modifyNickname(nick).complete();
                } catch (Throwable e) {
                    msg = e.getMessage();
                    failed++;
                }
            }
        }
        if (failed != 0) {
            return "Failed to clear " + failed + " nicknames for reason: " + msg;
        }
        return "Cleared all nicknames (that I have permission to clear)!";
    }
}
