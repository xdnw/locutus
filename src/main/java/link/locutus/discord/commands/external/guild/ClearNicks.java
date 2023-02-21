package link.locutus.discord.commands.external.guild;

import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClearNicks extends Command {
    private final Map<Long, String> previous = new HashMap<>();

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
        return "Cleared all possible nicknames!";
    }
}
