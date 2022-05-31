package com.boydti.discord.commands.coalition;

import com.boydti.discord.Locutus;
import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.commands.manager.CommandCategory;
import com.boydti.discord.db.entities.Coalition;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.user.Roles;
import com.boydti.discord.util.MathMan;
import com.boydti.discord.util.PnwUtil;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.internal.utils.PermissionUtil;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RemoveCoalition extends Command {
    public RemoveCoalition() {
        super("removecoalition", "delcoalition", CommandCategory.FOREIGN_AFFAIRS, CommandCategory.GOV);
    }

    @Override
    public String help() {
        return "!removecoalition <coalition> [alliance]";
    }

    @Override
    public String desc() {
        return "Delete an entire coalition, or a coalition-alliance mapping";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return Roles.hasAny(user, server, Roles.ADMIN, Roles.MILCOM, Roles.FOREIGN_AFFAIRS, Roles.INTERNAL_AFFAIRS, Roles.ECON);
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (args.isEmpty()) return usage();
        String coalition = args.get(0);
        Coalition.checkPermission(coalition, guild, author);

        switch (args.size()) {
            default:
                return "Usage: !removecoalition <coalition> [alliance]";
            case 1:
                Locutus.imp().getGuildDB(event).removeCoalition(args.get(0));
                return "Removed coalition: `" + coalition + "`";
            case 2:
                Set<Long> alliancesOrGuilds = new HashSet<>();
                if (MathMan.isInteger(args.get(1)) && Long.parseLong(args.get(1)) > Integer.MAX_VALUE) {
                    alliancesOrGuilds.add(Long.parseLong(args.get(1)));
                } else {
                    Set<Integer> alliances = PnwUtil.parseAlliances(Locutus.imp().getGuildDB(guild), args.get(1));
                    if (alliances == null || alliances.isEmpty()) {
                        return "Invalid alliance: `" + args.get(1) + "`";
                    }
                    for (Integer aaId : alliances) {
                        alliancesOrGuilds.add(aaId.longValue());
                    }
                }
                StringBuilder result = new StringBuilder();
                for (Long allianceOrGuild : alliancesOrGuilds) {
                    Locutus.imp().getGuildDB(event).removeCoalition(allianceOrGuild, coalition);
                    String name = allianceOrGuild <= Integer.MAX_VALUE ? PnwUtil.getName(allianceOrGuild, true) : allianceOrGuild + "";
                    result.append("Removed `" + name + "`" + " from `" + coalition + "`").append("\n");
                }
                return result.toString();
        }
    }

}
