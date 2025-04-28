package link.locutus.discord.commands.sync;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.event.Event;
import link.locutus.discord.user.Roles;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.List;
import java.util.Set;

public class SyncTreaties extends Command {
    public SyncTreaties() {
        super(CommandCategory.LOCUTUS_ADMIN, CommandCategory.FOREIGN_AFFAIRS);
    }

    @Override
    public List<CommandRef> getSlashReference() {
        return List.of(CM.admin.sync.treaties.cmd);
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return super.checkPermission(server, user) && Roles.ADMIN.hasOnRoot(user);
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        Locutus.imp().getNationDB().updateTreaties(Event::post);
        return "Done";
    }
}
