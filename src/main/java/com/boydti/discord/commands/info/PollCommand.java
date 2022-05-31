package com.boydti.discord.commands.info;

import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.user.Roles;
import com.boydti.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;
import java.util.Set;

public class PollCommand extends Command {

    private String[] emojis = {
            ""
    };

    @Override
    public String help() {
        return super.help() + "<title> [poll options...]";
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() < 2 || args.size() > 10) return usage(event);
        if (!Roles.INTERNAL_AFFAIRS.has(author, guild)) return "No permission (ia)";



        String title = "Roll call";

        StringBuilder body = new StringBuilder();
        body.append("");

        Message message = DiscordUtil.createEmbedCommand(event.getChannel(), title, body.toString());

        String emoji = "\uD83D\uDD96";
        String command = ".!dummy";

        return null;
    }
}
