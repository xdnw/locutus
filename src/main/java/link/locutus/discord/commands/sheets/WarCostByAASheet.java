package link.locutus.discord.commands.sheets;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.List;
import java.util.Set;

public class WarCostByAASheet extends Command {
    public WarCostByAASheet() {
        super(CommandCategory.GOV, CommandCategory.GAME_INFO_AND_TOOLS, CommandCategory.MILCOM);
    }

    @Override
    public String help() {
        return "`" + super.help() + " <nations> <time>`";
    }

    @Override
    public String desc() {
        return "Warcost (for each alliance) broken down\n" +
                "Add -i to include inactives\n" +
                "Add -a to include applicants\n";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return Locutus.imp().getGuildDB(server).isValidAlliance() && Roles.MILCOM.has(user, server);
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.isEmpty()) return usage();
//        boolean includeUntaxable = flags.contains('t');
        boolean includeInactive = flags.contains('i');
        boolean includeApps = flags.contains('a');

        long cutoff = 0;
        if (args.size() == 2) cutoff = System.currentTimeMillis() - TimeUtil.timeToSec(args.get(1)) * 1000l;

        Set<DBNation> nationSet = DiscordUtil.parseNations(guild, author, me, args.get(0), false, true);
//        Map<Integer, List<DBNation>> nationsByAA = Locutus.imp().getNationDB().getNationsByAlliance(nationSet, false, !includeInactive, !includeApps, true);
        GuildDB guildDb = Locutus.imp().getGuildDB(guild);

        return StatCommands.WarCostByAllianceSheet(
                channel,
                guild,
                nationSet,
                cutoff,
                flags.contains('i'),
                flags.contains('a')
        );
    }
}
