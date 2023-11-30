package link.locutus.discord.commands.sheets;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.Activity;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.guild.SheetKey;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.sheet.SpreadSheet;
import link.locutus.discord.apiv1.enums.Rank;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class DeserterSheet extends Command {
    public DeserterSheet() {
        super(CommandCategory.MILCOM, CommandCategory.GAME_INFO_AND_TOOLS, CommandCategory.GOV);
    }

    @Override
    public String help() {
        return super.help() + " <alliances> [time] [currently-in]";
    }

    @Override
    public String desc() {
        return "A sheet of all the nations that left an alliance in the specified timeframe.\n" +
                "`currently-in` is optional and only checks those nations\n" +
                "Add `-a` to remove inactive nations\n" +
                "Add `-v` to remove vm nations\n" +
                "Add `-n` to remove applicants (current)";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return super.checkPermission(server, user) && Roles.MILCOM.has(user, server);
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() != 2) return usage(args.size(), 2, channel);

        Set<Integer> aaIds = DiscordUtil.parseAllianceIds(guild, args.get(0));
        if (aaIds == null || aaIds.isEmpty()) return "Unknown alliances: " + aaIds;

        Map<Integer, Map.Entry<Long, Rank>> removes = new HashMap<>();
        Map<Integer, Integer> nationPreviousAA = new HashMap<>();

        for (Integer aaId : aaIds) {
            Map<Integer, Map.Entry<Long, Rank>> removesId = Locutus.imp().getNationDB().getRemovesByAlliance(aaId);
            for (Map.Entry<Integer, Map.Entry<Long, Rank>> entry : removesId.entrySet()) {
                Map.Entry<Long, Rank> existing = removes.get(entry.getKey());
                if (existing != null && entry.getValue().getKey() > existing.getKey()) {
                    continue;
                }
                nationPreviousAA.put(entry.getKey(), aaId);
                removes.put(entry.getKey(), entry.getValue());
            }

            removes.putAll(removesId);
        }

        if (removes.isEmpty()) return "No history found";

        long timeDiff = TimeUtil.timeToSec(args.get(1)) * 1000L;
        if (timeDiff == 0) return "Invalid time: `" + args.get(1) + "`";
        long cuttOff = System.currentTimeMillis() - timeDiff;

        if (removes.isEmpty()) return "No history found";
        Set<DBNation> filter = null;
        if (args.size() == 3) {
            filter = DiscordUtil.parseNations(guild, author, me, args.get(2), false, false);
        }

        List<Map.Entry<DBNation, Map.Entry<Long, Rank>>> nations = new ArrayList<>();

        for (Map.Entry<Integer, Map.Entry<Long, Rank>> entry : removes.entrySet()) {
            if (entry.getValue().getKey() < cuttOff) continue;

            DBNation nation = Locutus.imp().getNationDB().getNation(entry.getKey());
            if (nation != null && (filter == null || filter.contains(nation))) {
                nations.add(new AbstractMap.SimpleEntry<>(nation, entry.getValue()));
            }
        }

        if (flags.contains('a')) nations.removeIf(n -> n.getKey().getActive_m() > 10000);
        if (flags.contains('v')) nations.removeIf(n -> n.getKey().getVm_turns() != 0);
        if (flags.contains('n')) nations.removeIf(n -> n.getKey().getPosition() > 1);
        if (nations.isEmpty()) return "No nations find over the specified timeframe";

        GuildDB db = Locutus.imp().getGuildDB(guild);
        SpreadSheet sheet = SpreadSheet.create(db, SheetKey.DESERTER_SHEET);
        List<Object> header = new ArrayList<>(Arrays.asList(
                "AA-before",
                "AA-now",
                "date-left",
                "position-left",
                "nation",
                "cities",
                "infra",
                "soldiers",
                "tanks",
                "planes",
                "ships",
                "spies",
                "score",
                "beige",
                "inactive",
                "login_chance"
        ));

        sheet.setHeader(header);

        for (Map.Entry<DBNation, Map.Entry<Long, Rank>> entry : nations) {
            DBNation defender = entry.getKey();
            Map.Entry<Long, Rank> dateRank = entry.getValue();
            Long date = dateRank.getKey();

            String dateStr = TimeUtil.YYYY_MM_DD_HH_MM_A.format(new Date(date));
            Rank rank = dateRank.getValue();

            ArrayList<Object> row = new ArrayList<>();
            Integer prevAA = nationPreviousAA.get(defender.getNation_id());
            String prevAAName = PnwUtil.getName(prevAA, true);
            row.add(MarkupUtil.sheetUrl(prevAAName, PnwUtil.getUrl(prevAA, true)));
            row.add(MarkupUtil.sheetUrl(defender.getAllianceName(), defender.getAllianceUrl()));

            row.add(dateStr);
            row.add(rank.name());

            row.add(MarkupUtil.sheetUrl(defender.getNation(), defender.getNationUrl()));

            row.add(defender.getCities());
            row.add(defender.getAvg_infra());
            row.add(defender.getSoldiers() + "");
            row.add(defender.getTanks() + "");
            row.add(defender.getAircraft() + "");
            row.add(defender.getShips() + "");
            row.add(defender.getSpies() + "");
            row.add(defender.getScore() + "");
            row.add(defender.getBeigeTurns() + "");
            row.add(TimeUtil.secToTime(TimeUnit.MINUTES, defender.getActive_m()));

            Activity activity = defender.getActivity(12 * 7 * 2);
            row.add(activity.getAverageByDay());

            sheet.addRow(row);
        }

        sheet.updateClearFirstTab();
        sheet.updateWrite();


        sheet.attach(channel.create(), "deserter").send();
        return null;
    }
}
