package link.locutus.discord.commands.sheets;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.Noformat;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.guild.SheetKeys;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.sheet.SpreadSheet;
import link.locutus.discord.apiv1.enums.Rank;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class NationSheet extends Command implements Noformat {
    public NationSheet() {
        super(CommandCategory.GOV, CommandCategory.GENERAL_INFO_AND_TOOLS);
    }

    @Override
    public String help() {
        return super.help() + " [nations] <column1> <column2> ...";
    }

    @Override
    public String desc() {
        return "Create a nation sheet, with the following column placeholders\n - {" +
                StringMan.join(DiscordUtil.getParser().getPlaceholders(), "}\n - {") + "}\n" +
                "Add `-s` to force update spies";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return (Roles.MILCOM.has(user, server) || Roles.ECON.has(user, server) || Roles.INTERNAL_AFFAIRS.has(user, server));
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() < 2) return usage(event);

        GuildDB db = Locutus.imp().getGuildDB(guild);
        SpreadSheet sheet = null;
        Iterator<String> iter = args.iterator();
        while (iter.hasNext()) {
            String arg = iter.next();
            if (arg.startsWith("sheet:")) {
                sheet = SpreadSheet.create(arg);
                iter.remove();;
            }
        }

        List<String> header = new ArrayList<>(args);
        header.remove(0);
        for (int i = 0; i < header.size(); i++) {
            String arg = header.get(i);
            arg = arg.replace("{", "").replace("}", "").replace("=", "");
            header.set(i, arg);
        }

        Set<DBNation> nations = DiscordUtil.parseNations(guild, DiscordUtil.format(guild, null, author, me, args.get(0)));
        if (nations.isEmpty()) return "No nations found for `" + args.get(0) + "`";

        if (sheet == null) {
            sheet = SpreadSheet.create(Locutus.imp().getGuildDB(guild), SheetKeys.NATION_SHEET);
        }

        sheet.setHeader(header);
        if (flags.contains('s')) {
            Set<DBNation> toUpdate = new HashSet<>(nations);
            Set<Integer> alliances = new HashSet<>();
            for (DBNation nation : toUpdate) {
                if (nation.getPosition() > Rank.APPLICANT.id) {
                    alliances.add(nation.getAlliance_id());
                }
            }
            for (Integer allianceId : alliances) {
                Set<Integer> updated = DBAlliance.getOrCreate(allianceId).updateSpies(false);
                toUpdate.removeIf(f -> updated.contains(f.getNation_id()));
            }
            for (DBNation nation : toUpdate) {
                nation.updateSpies();
            }
        }

        List<DBNation> nationsSorted = new ArrayList<>(nations);
        Collections.sort(nationsSorted, new Comparator<DBNation>() {
            @Override
            public int compare(DBNation o1, DBNation o2) {
                if (o1.getAlliance_id() != o2.getAlliance_id()) return Integer.compare(o1.getAlliance_id(), o2.getAlliance_id());
                if (o1.getCities() != o2.getCities()) return Integer.compare(o2.getCities(), o1.getCities());
                return Double.compare(o2.getScore(), o1.getScore());
            }
        });
        for (DBNation nation : nationsSorted) {
            for (int i = 1; i < args.size(); i++) {
                String arg = args.get(i);
                String formatted = DiscordUtil.format(guild, event.getGuildChannel(), author, nation, arg);

                header.set(i - 1, formatted);
            }

            sheet.addRow(new ArrayList<>(header));
        }

        sheet.clear("A:ZZ");
        sheet.set(0, 0);

        sheet.attach(new DiscordChannelIO(event).create()).send();
        return null;
//        I need, Nation name, nation link, score, war range, offensive/defensive slots open, military count (planes/tanks/ships/soldiers)
    }
}
