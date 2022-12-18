package link.locutus.discord.commands.sheets;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.Noformat;
import link.locutus.discord.commands.rankings.builder.RankBuilder;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.sheet.SpreadSheet;
import link.locutus.discord.apiv1.domains.subdomains.SAllianceContainer;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class AllianceSheet extends Command implements Noformat {
    public AllianceSheet() {
        super(CommandCategory.GAME_INFO_AND_TOOLS, CommandCategory.GOV);
    }

    @Override
    public String help() {
        return super.help() + " [nations] <column1> <column2> ...";
    }

    @Override
    public String desc() {
        return "Create a nation sheet, with the following column placeholders\n - {" +
                StringMan.join(DiscordUtil.getParser().getPlaceholders(), "}\n - {") + "}";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return super.checkPermission(server, user) && (Roles.MILCOM.has(user, server) || Roles.ECON.has(user, server) || Roles.INTERNAL_AFFAIRS.has(user, server));
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() < 2) return usage(event);

        List<String> header = new ArrayList<>(args);
        header.remove(0);
        for (int i = 0; i < header.size(); i++) {
            String arg = header.get(i);
            arg = arg.replace("{", "").replace("}", "").replace("=", "");
            header.set(i, arg);
        }

        Set<DBNation> nations = DiscordUtil.parseNations(guild, args.get(0));
        if (nations.isEmpty()) return "No nations found for `" + args.get(0) + "`";

        List<SAllianceContainer> allianceList = Locutus.imp().getPnwApi().getAlliances().getAlliances();

        Map<Integer, SAllianceContainer> alliances = allianceList.stream().collect(Collectors.toMap(f -> Integer.parseInt(f.getId()), f -> f));
        Map<Integer, List<DBNation>> nationMap = new RankBuilder<>(nations).group(n -> n.getAlliance_id()).get();

        Map<Integer, DBNation> totals = new HashMap<>();
        for (Map.Entry<Integer, List<DBNation>> entry : nationMap.entrySet()) {
            Integer id = entry.getKey();
            DBNation total = DBNation.createFromList(PnwUtil.getName(id, true), entry.getValue(), false);
            totals.put(id, total);
        }

        SpreadSheet sheet = SpreadSheet.create(Locutus.imp().getGuildDB(guild), GuildDB.Key.ALLIANCES_SHEET);

        sheet.setHeader(header);

        for (Map.Entry<Integer, DBNation> entry : totals.entrySet()) {
            SAllianceContainer alliance = alliances.get(entry.getKey());
            if (alliance == null) continue;
            DBAlliance dbAlliance = DBAlliance.getOrCreate(entry.getKey());

            DBNation nation = entry.getValue();
            for (int i = 1; i < args.size(); i++) {
                String arg = args.get(i);

                // Format alliance id
                for (Field field : SAllianceContainer.class.getDeclaredFields()) {
                    String placeholder = "{" + field.getName() + "}";
                    if (arg.contains(placeholder)) {
                        field.setAccessible(true);
                        arg = arg.replace(placeholder, field.get(alliance) + "");
                    }
                }

                for (Field field : DBAlliance.class.getDeclaredFields()) {
                    String placeholder = "{" + field.getName() + "}";
                    if (arg.contains(placeholder)) {
                        field.setAccessible(true);
                        arg = arg.replace(placeholder, field.get(dbAlliance) + "");
                    }
                }

                String formatted = DiscordUtil.format(guild, event.getGuildChannel(), author, nation, arg);

                header.set(i - 1, formatted);
            }

            sheet.addRow(new ArrayList<>(header));
        }

        sheet.clearAll();
        sheet.set(0, 0);

        return sheet.getURL(true, true);
    }
}
