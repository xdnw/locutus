package link.locutus.discord.commands.coalition;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.Coalition;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SetCoalition extends Command {

    public SetCoalition() {
        super("setcoalition", "addcoalition", CommandCategory.FOREIGN_AFFAIRS, CommandCategory.GOV);
    }

    @Override
    public String help() {
        return Settings.commandPrefix(true) + "setcoalition <alliance> <coalition>";
    }

    @Override
    public String desc() {
        return "Set an alliance to be in a coalition.";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return Roles.hasAny(user, server, Roles.ADMIN, Roles.MILCOM, Roles.FOREIGN_AFFAIRS, Roles.INTERNAL_AFFAIRS, Roles.ECON);
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() != 2) {
            return "Usage: `" + Settings.commandPrefix(true) + "setcoalition <alliance> <coalition>";
        }
        Coalition.checkPermission(args.get(1), guild, author);

        GuildDB db = Locutus.imp().getGuildDB(guild);
        try {
            StringBuilder response = new StringBuilder();
            if (!db.getCoalition(args.get(0)).isEmpty()) { // check
                return usage();
            }
            String coalition = args.get(1);
            if (coalition.equalsIgnoreCase(Coalition.OFFSHORE.name()) && !Roles.ADMIN.has(author, guild)) {
                return "Only admin can set offshore coalitions.";
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
                response.append('\n').append("Added ").append(allianceOrGuild).append(" to `").append(args.get(1)).append("`");
            }
            return response.toString().trim() + "\nDone!";
        } catch (Throwable e) {
            e.printStackTrace();
            throw e;
        }
    }
}
