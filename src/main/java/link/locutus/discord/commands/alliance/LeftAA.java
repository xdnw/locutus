package link.locutus.discord.commands.alliance;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.pnw.Alliance;
import link.locutus.discord.pnw.DBNation;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.apiv1.enums.Rank;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        return "List the departures of nations from alliances\n" +
                "Add `-a` to remove inactives\n" +
                "Add `-v` to remove VM\n" +
                "Add `-m` to remove members\n" +
                "Add `-i` to list nation ids";
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (args.isEmpty()) {
            return usage(event);
        }
        StringBuilder response = new StringBuilder();
        Map<Integer, Map.Entry<Long, Rank>> removes;
        List<Map.Entry<Map.Entry<DBNation, Alliance>, Map.Entry<Long, Rank>>> toPrint = new ArrayList<>();

        boolean showCurrentAA = false;
        Integer aaId = PnwUtil.parseAllianceId(args.get(0));
        if (aaId == null || args.get(0).contains("/nation/")) {
            Integer nationId = DiscordUtil.parseNationId(args.get(0));
            if (nationId == null) return usage(event);

            DBNation nation = DBNation.byId(nationId);
            removes = Locutus.imp().getNationDB().getRemovesByNation(nationId);
            for (Map.Entry<Integer, Map.Entry<Long, Rank>> entry : removes.entrySet()) {
                Alliance aa = new Alliance(entry.getKey());
                DBNation tmp = nation;
                if (tmp == null) {
                    tmp = new DBNation();
                    tmp.setNation_id(nationId);
                    tmp.setAlliance_id(aa.getAlliance_id());
                    tmp.setNation(nationId + "");
                    tmp.setAlliance(aa.getName());
                }
                AbstractMap.SimpleEntry<DBNation, Alliance> key = new AbstractMap.SimpleEntry<>(tmp, aa);
                Map.Entry<Long, Rank> value = entry.getValue();
                toPrint.add(new AbstractMap.SimpleEntry<>(key, value));
            }

        } else {
            showCurrentAA = true;
            removes = Locutus.imp().getNationDB().getRemovesByAlliance(aaId);

            if (args.size() != 2 && args.size() != 3) return usage(event);

            long timeDiff = TimeUtil.timeToSec(args.get(1)) * 1000L;
            if (timeDiff == 0) return "Invalid time: `" + args.get(1) + "`";
            long cuttOff = System.currentTimeMillis() - timeDiff;

            if (removes.isEmpty()) return "No history found";
            Set<DBNation> filter = null;
            if (args.size() == 3) {
                filter = DiscordUtil.parseNations(guild, args.get(2));
            }

            for (Map.Entry<Integer, Map.Entry<Long, Rank>> entry : removes.entrySet()) {
                if (entry.getValue().getKey() < cuttOff) continue;

                DBNation nation = Locutus.imp().getNationDB().getNation(entry.getKey());
                if (nation != null && (filter == null || filter.contains(nation))) {

                    if (flags.contains('a') && nation.getActive_m() > 10000) continue;
                    if (flags.contains('v') && nation.getVm_turns() != 0) continue;
                    if (flags.contains('m') && nation.getPosition() > 1) continue;

                    AbstractMap.SimpleEntry<DBNation, Alliance> key = new AbstractMap.SimpleEntry<>(nation, new Alliance(aaId));
                    toPrint.add(new AbstractMap.SimpleEntry<>(key, entry.getValue()));
                }
            }
        }

        Set<Integer> ids = new LinkedHashSet<>();
        long now = System.currentTimeMillis();
        for (Map.Entry<Map.Entry<DBNation, Alliance>, Map.Entry<Long, Rank>> entry : toPrint) {
            long diff = now - entry.getValue().getKey();
            Rank rank = entry.getValue().getValue();
            String timeStr = TimeUtil.secToTime(TimeUnit.MILLISECONDS, diff);

            Map.Entry<DBNation, Alliance> nationAA = entry.getKey();
            DBNation nation = nationAA.getKey();
            ids.add(nation.getNation_id());

            response.append(timeStr + " ago: " + nationAA.getKey().getNation() + " left " + nationAA.getValue().getName() + " | " + rank.name());
            if (showCurrentAA && nation.getAlliance_id() != 0) {
                response.append(" and joined " + nation.getAlliance());
            }
            response.append("\n");
        }

        if (flags.contains('i')) {
            DiscordUtil.upload(event.getChannel(), "ids.txt", StringMan.join(ids, ","));
        }
        if (response.length() == 0) return "No history found in the specified timeframe";

        return response.toString();
    }
}
