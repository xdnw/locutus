package link.locutus.discord.commands.coalition;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.Coalition;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PW;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.List;
import java.util.Set;

public class RemoveCoalition extends Command {
    public RemoveCoalition() {
        super("removecoalition", "delcoalition", CommandCategory.FOREIGN_AFFAIRS, CommandCategory.GOV);
    }

    @Override
    public List<CommandRef> getSlashReference() {
        return List.of(CM.coalition.remove.cmd);
    }
    @Override
    public String help() {
        return Settings.commandPrefix(true) + "removecoalition <coalition> [alliance]";
    }

    @Override
    public String desc() {
        return "Delete an entire coalition, or a coalition-alliance mapping.";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return Roles.hasAny(user, server, Roles.ADMIN, Roles.MILCOM, Roles.FOREIGN_AFFAIRS, Roles.INTERNAL_AFFAIRS, Roles.ECON);
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.isEmpty()) return usage();
        String coalition = args.get(0);
        Coalition.checkPermission(coalition, guild, author);

        switch (args.size()) {
            default -> {
                return "Usage: " + Settings.commandPrefix(true) + "removecoalition <coalition> [alliance]";
            }
            case 1 -> {
                Locutus.imp().getGuildDB(guild).removeCoalition(args.get(0));
                return "Removed coalition: `" + coalition + "`";
            }
            case 2 -> {
                Set<Long> alliancesOrGuilds = new LongOpenHashSet();
                if (MathMan.isInteger(args.get(1)) && Long.parseLong(args.get(1)) > Integer.MAX_VALUE) {
                    alliancesOrGuilds.add(Long.parseLong(args.get(1)));
                } else {
                    Set<Integer> alliances = PW.parseAlliances(Locutus.imp().getGuildDB(guild), args.get(1));
                    if (alliances.isEmpty()) {
                        return "Invalid alliance: `" + args.get(1) + "`";
                    }
                    for (Integer aaId : alliances) {
                        alliancesOrGuilds.add(aaId.longValue());
                    }
                }
                StringBuilder result = new StringBuilder();
                for (Long allianceOrGuild : alliancesOrGuilds) {
                    Locutus.imp().getGuildDB(guild).removeCoalition(allianceOrGuild, coalition);
                    String name = allianceOrGuild <= Integer.MAX_VALUE ? PW.getName(allianceOrGuild, true) : allianceOrGuild + "";
                    result.append("Removed `").append(name).append("`").append(" from `").append(coalition).append("`").append("\n");
                }
                return result.toString();
            }
        }
    }

}
