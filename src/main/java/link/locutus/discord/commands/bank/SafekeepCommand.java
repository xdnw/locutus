package link.locutus.discord.commands.bank;

import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.List;
import java.util.Set;

public class SafekeepCommand extends Command {
    public SafekeepCommand() {
        super("safekeep", CommandCategory.ECON, CommandCategory.MEMBER);
    }

    @Override
    public List<CommandRef> getSlashReference() {
        return List.of(CM.bank.deposit.cmd.nations("").useApi("true").amount(""));
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return super.checkPermission(server, user) && Roles.ADMIN.hasOnRoot(user);
    }

    @Override
    public String help() {
        return super.help() + " <resources>";
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() != 1) return usage();
        return me.getAuth(true).safekeep(ResourceType.parseResources(args.get(0))) + "\nDone!";
    }
}
