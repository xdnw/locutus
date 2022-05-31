package com.boydti.discord.commands.coalition;

import com.boydti.discord.Locutus;
import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.commands.manager.CommandCategory;
import com.boydti.discord.db.DiscordDB;
import com.boydti.discord.db.GuildDB;
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

public class SetCoalition extends Command {

    public SetCoalition() {
        super("setcoalition", "addcoalition", CommandCategory.FOREIGN_AFFAIRS, CommandCategory.GOV);
    }

    @Override
    public String help() {
        return "!setcoalition <alliance> <coalition>";
    }

    @Override
    public String desc() {
        return "Set an alliance to be in a coalition";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return Roles.hasAny(user, server, Roles.ADMIN, Roles.MILCOM, Roles.FOREIGN_AFFAIRS, Roles.INTERNAL_AFFAIRS, Roles.ECON);
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() != 2) {
            return "Usage: `!setcoalition <alliance> <coalition>";
        }
        Coalition.checkPermission(args.get(1), guild, author);

        GuildDB db = Locutus.imp().getGuildDB(guild);
        try {
            StringBuilder response = new StringBuilder();
            if (!db.getCoalition(args.get(0)).isEmpty()){ // check
                return usage();
            }
            String coalition = args.get(1);
            if (coalition.equalsIgnoreCase(Coalition.OFFSHORE.name()) && !Roles.ADMIN.has(author, guild)) {
                return "Only admin can set offshore coalitions";
            }
            Set<Long> alliancesOrGuilds = new HashSet<>();
            if (MathMan.isInteger(args.get(0)) && Long.parseLong(args.get(0)) > Integer.MAX_VALUE) {
                alliancesOrGuilds.add(Long.parseLong(args.get(0)));
            } else {
                Set<Integer> alliances = PnwUtil.parseAlliances(Locutus.imp().getGuildDB(guild), args.get(0));
                for (Integer aaId : alliances) {
                    alliancesOrGuilds.add(aaId.longValue());
                }
            }
            for (Long allianceOrGuild : alliancesOrGuilds) {
                Locutus.imp().getGuildDB(event).addCoalition(allianceOrGuild, coalition);
                response.append('\n').append("Added " + allianceOrGuild + " to `" + args.get(1) + "`");
            }
            return response.toString().trim() + "\nDone!";
        } catch (Throwable e) {
            e.printStackTrace();
            throw e;
        }
    }
}
