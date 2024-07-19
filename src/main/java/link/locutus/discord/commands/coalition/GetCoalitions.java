package link.locutus.discord.commands.coalition;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.StringMan;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.*;

public class GetCoalitions extends Command {
    public GetCoalitions() {
        super("coalitions", CommandCategory.FOREIGN_AFFAIRS, CommandCategory.MEMBER);
    }

    @Override
    public List<CommandRef> getSlashReference() {
        return List.of(CM.coalition.list.cmd);
    }
    @Override
    public String help() {
        return Settings.commandPrefix(true) + "coalitions [filter]";
    }

    @Override
    public String desc() {
        return "List all coalitions\n" +
                "Add `-i` to list only IDs\n" +
                "Add `-d` to ignore deleted AAs\n" +
                "e.g. `" + Settings.commandPrefix(true) + "coalitions enemies`";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        boolean isAdmin = Roles.ADMIN.has(author, guild);
        if (args.size() > 1) return usage(args.size(), 1, channel);

        GuildDB db = Locutus.imp().getGuildDB(guild);
//        Map<String, Set<Long>> coalitions = Locutus.imp().getGuildDB(guild).getCoalitionsRaw();
        List<String> coalitionNames = new ArrayList<>(db.getCoalitionNames());
        Collections.sort(coalitionNames);

        StringBuilder response = new StringBuilder();
        for (String coalition : coalitionNames) {
            if (coalition.equalsIgnoreCase("offshore") && !isAdmin) {
                continue;
            }
            Set<Long> alliances = db.getCoalitionRaw(coalition);
            List<String> names = new ArrayList<>();
            for (long allianceOrGuildId : alliances) {
                String name;
                if (allianceOrGuildId > Integer.MAX_VALUE) {
                    GuildDB guildDb = Locutus.imp().getGuildDB(allianceOrGuildId);
                    if (guildDb == null) {
                        if (flags.contains('d')) continue;
                        name = "guild:" + allianceOrGuildId;
                    } else {
                        name = guildDb.getGuild().toString();
                    }
                } else {
                    name = Locutus.imp().getNationDB().getAllianceName((int) allianceOrGuildId);
                    if (name == null) {
                        if (flags.contains('d')) continue;
                        name = "AA:" + allianceOrGuildId;
                    }
                }
                if (flags.contains('i')) {
                    names.add(allianceOrGuildId + "");
                } else {
                    names.add(name);
                }
            }
            if (args.size() == 1) {
                String arg = args.get(0).toLowerCase();
                if (!coalition.contains(arg)) {
                    names.removeIf(f -> !f.toLowerCase().contains(arg));
                    if (names.isEmpty()) continue;
                }
            }

            response.append('\n').append("**").append(coalition).append("**: ").append(StringMan.join(names, ","));
        }
        if (response.length() == 0) return "No coalitions found.";
        return response.toString().trim();
    }
}
