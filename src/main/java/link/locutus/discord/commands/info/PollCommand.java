package link.locutus.discord.commands.info;

import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.pnw.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.discord.DiscordUtil;
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
