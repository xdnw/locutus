package link.locutus.discord.commands.alliance;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class LeftAA extends Command {
    public LeftAA() {
        super("LeftAA", "AllianceHistory", CommandCategory.GAME_INFO_AND_TOOLS);
    }

    @Override
    public String help() {
        return super.help() + " <alliance|nation> [time] [nation-filter]";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String desc() {
        return """
                List the departures of nations from alliances
                Add `-a` to remove inactives
                Add `-v` to remove VM
                Add `-m` to remove members
                Add `-i` to list nation ids""";
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.isEmpty()) {
            return usage(args.size(), 1, channel);
        }
        StringBuilder response = new StringBuilder();
        Map<Integer, Map.Entry<Long, Rank>> removes;
        List<Map.Entry<Map.Entry<DBNation, DBAlliance>, Map.Entry<Long, Rank>>> toPrint = new ArrayList<>();

        boolean showCurrentAA = false;
        Integer aaId = PnwUtil.parseAllianceId(args.get(0));
        if (aaId == null || args.get(0).contains("/nation/")) {
            Integer nationId = DiscordUtil.parseNationId(args.get(0));
            if (nationId == null) return usage("Invalid nation id: `" + args.get(0) + "`", channel);

            DBNation nation = DBNation.getById(nationId);
            removes = Locutus.imp().getNationDB().getRemovesByNation(nationId);
            for (Map.Entry<Integer, Map.Entry<Long, Rank>> entry : removes.entrySet()) {
                DBAlliance aa = DBAlliance.getOrCreate(entry.getKey());
                DBNation tmp = nation;
                if (tmp == null) {
                    tmp = new DBNation();
                    tmp.setNation_id(nationId);
                    tmp.setAlliance_id(aa.getAlliance_id());
                    tmp.setNation(nationId + "");
                }
                AbstractMap.SimpleEntry<DBNation, DBAlliance> key = new AbstractMap.SimpleEntry<>(tmp, aa);
                Map.Entry<Long, Rank> value = entry.getValue();
                toPrint.add(new AbstractMap.SimpleEntry<>(key, value));
            }

        } else {
            showCurrentAA = true;
            removes = Locutus.imp().getNationDB().getRemovesByAlliance(aaId);

            if (args.size() != 2 && args.size() != 3) return usage(args.size(), 2, channel);

            long timeDiff = TimeUtil.timeToSec(args.get(1)) * 1000L;
            if (timeDiff == 0) return "Invalid time: `" + args.get(1) + "`";
            long cuttOff = System.currentTimeMillis() - timeDiff;

            if (removes.isEmpty()) return "No history found.";
            Set<DBNation> filter = null;
            if (args.size() == 3) {
                filter = DiscordUtil.parseNations(guild, author, me, args.get(2), false, true);
            }

            for (Map.Entry<Integer, Map.Entry<Long, Rank>> entry : removes.entrySet()) {
                if (entry.getValue().getKey() < cuttOff) continue;

                DBNation nation = Locutus.imp().getNationDB().getNation(entry.getKey());
                if (nation != null && (filter == null || filter.contains(nation))) {

                    if (flags.contains('a') && nation.active_m() > 10000) continue;
                    if (flags.contains('v') && nation.getVm_turns() != 0) continue;
                    if (flags.contains('m') && nation.getPosition() > 1) continue;

                    AbstractMap.SimpleEntry<DBNation, DBAlliance> key = new AbstractMap.SimpleEntry<>(nation, DBAlliance.getOrCreate(aaId));
                    toPrint.add(new AbstractMap.SimpleEntry<>(key, entry.getValue()));
                }
            }
        }

        Set<Integer> ids = new LinkedHashSet<>();
        long now = System.currentTimeMillis();
        for (Map.Entry<Map.Entry<DBNation, DBAlliance>, Map.Entry<Long, Rank>> entry : toPrint) {
            long diff = now - entry.getValue().getKey();
            Rank rank = entry.getValue().getValue();
            String timeStr = TimeUtil.secToTime(TimeUnit.MILLISECONDS, diff);

            Map.Entry<DBNation, DBAlliance> nationAA = entry.getKey();
            DBNation nation = nationAA.getKey();
            ids.add(nation.getNation_id());

            response.append(timeStr).append(" ago: ").append(nationAA.getKey().getNation()).append(" left ").append(nationAA.getValue().getName()).append(" | ").append(rank.name());
            if (showCurrentAA && nation.getAlliance_id() != 0) {
                response.append(" and joined ").append(nation.getAllianceName());
            }
            response.append("\n");
        }

        IMessageBuilder msg = channel.create();
        if (flags.contains('i')) {
            msg.file("ids.txt", StringMan.join(ids, ","));
        }
        if (response.length() == 0) {
            msg.append("No history found in the specified timeframe.");
        } else {
            msg.append(response.toString());
        }
        msg.send();
        return null;
    }
}
