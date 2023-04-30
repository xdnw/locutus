package link.locutus.discord.commands.external.guild;

import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.RateLimitUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;

public class KickLocutus extends Command {
    public KickLocutus() {
        super(CommandCategory.LOCUTUS_ADMIN);
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return super.checkPermission(server, user) && Roles.ADMIN.hasOnRoot(user);
    }

    @Override
    public String onCommand(MessageReceivedEvent event, List<String> args) throws Exception {
        RateLimitUtil.complete(event.getChannel().sendMessage("Goodbye."));
        RateLimitUtil.queue(event.getGuild().leave());
        return null;
    }
}
