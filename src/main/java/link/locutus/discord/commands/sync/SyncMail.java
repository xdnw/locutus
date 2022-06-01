package link.locutus.discord.commands.sync;

import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.pnw.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.task.mail.AlertMailTask;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;
import java.util.Set;

public class SyncMail extends Command {
    public SyncMail() {
        super(CommandCategory.LOCUTUS_ADMIN);
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return Roles.ADMIN.hasOnRoot(user);
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        new AlertMailTask(me.getAuth(null), event.getChannel().getIdLong()).run();
        return "Done!";
    }
}
