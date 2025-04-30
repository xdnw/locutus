package link.locutus.discord.commands.bank;

import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.List;
import java.util.Set;

public class SyncBanks extends Command {
    public SyncBanks() {
        super(CommandCategory.ECON, CommandCategory.LOCUTUS_ADMIN);
    }


    @Override
    public List<CommandRef> getSlashReference() {
        return List.of(CM.admin.sync.syncBanks.cmd);
    }


    @Override
    public boolean checkPermission(Guild server, User user) {
        return super.checkPermission(server, user) && Roles.ADMIN.hasOnRoot(user);
    }

    @Override
    public String help() {
        return Settings.commandPrefix(true) + "syncbanks <alliance> [epoch]";
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() < 2) return usage();
        DBAlliance aa = DBAlliance.parse(args.get(0), true);
        Long latest = args.size() == 2 ? Long.parseLong(args.get(1)) : null;

        aa.getBank().sync(latest, false);
        return "Done!";
    }
}