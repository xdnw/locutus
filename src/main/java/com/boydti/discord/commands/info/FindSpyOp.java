package com.boydti.discord.commands.info;

import com.boydti.discord.Locutus;
import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.commands.manager.CommandCategory;
import com.boydti.discord.db.entities.DBSpyUpdate;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.util.discord.DiscordUtil;
import com.boydti.discord.util.MathMan;
import com.boydti.discord.util.SpyCount;
import com.boydti.discord.util.TimeUtil;
import com.boydti.discord.util.update.NationUpdateProcessor;
import com.boydti.discord.apiv1.enums.city.project.Projects;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class FindSpyOp extends Command {
    public FindSpyOp() {
        super(CommandCategory.MILCOM, CommandCategory.MEMBER);
    }

    @Override
    public String help() {
        return super.help() + " <time> <defender-spies> [defender-nation]";
    }

    @Override
    public String desc() {
        return "See who was online at the time of a spy op e.g.\n" +
                "`!findspyop \"08/05 04:33 pm\" 50`";
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() < 2) return usage(event);

        int defenderSpies = Integer.parseInt(args.get(1));

        DBNation defender;
        if (args.size() == 3) {
            defender = DiscordUtil.parseNation(args.get(2));
        } else if (args.size() == 2) {
            defender = me;
        } else return usage(event);

        Set<Integer> ids = new HashSet<>();
        Map<DBSpyUpdate, Long> updatesTmp = new HashMap<>();
        long interval = TimeUnit.MINUTES.toMillis(3);

        String[] times = args.get(0).split(",");
        for (String timeStr : times) {
            long timestamp = TimeUtil.parseDate(TimeUtil.MMDD_HH_MM_A, timeStr, true);
            List<DBSpyUpdate> updates = Locutus.imp().getNationDB().getSpyActivity(timestamp, interval);
            for (DBSpyUpdate update : updates) {
                DBNation nation = DBNation.byId(update.nation_id);
                if (nation == null) continue;
                if (!defender.isInSpyRange(nation)) continue;

                if (ids.contains(update.nation_id)) continue;
                ids.add(update.nation_id);
                updatesTmp.put(update, Math.abs(timestamp - update.timestamp));

            }
        }

        if (updatesTmp.isEmpty()) return "No results (0)";

        Map<DBNation, Map.Entry<Double, String>> allOdds = new HashMap<>();

        for (Map.Entry<DBSpyUpdate, Long> entry : updatesTmp.entrySet()) {
            DBSpyUpdate update = entry.getKey();
            long diff = entry.getValue();

            DBNation attacker = Locutus.imp().getNationDB().getNation(update.nation_id);
            if (attacker == null || (defender != null && !attacker.isInSpyRange(defender)) || attacker.getNation_id() == defender.getNation_id()) continue;

            int spiesUsed = update.spies;


            int safety = 3;
            int uncertainty = -1;
            boolean foundOp = false;
            boolean spySatellite = Projects.SPY_SATELLITE.has(update.projects);
            boolean intelligence = Projects.INTELLIGENCE_AGENCY.has(update.projects);

            if (update.change < 0) {
                Map.Entry<SpyCount.SpyOp, Integer> estimate = SpyCount.estimateSpiesUsed(update.change, update.spies);
                if (estimate != null) {
                    uncertainty = estimate.getValue();

                    SpyCount.SpyOp op = estimate.getKey();
                    spiesUsed = op.spies;
                    safety = op.safety;

                    diff = 0;

                    foundOp = true;
                }
            }

            if (spiesUsed == -1) spiesUsed = attacker.getSpies();

            double odds = SpyCount.getOdds(spiesUsed, defenderSpies, safety, SpyCount.Operation.SPIES, defender);
            if (spySatellite) odds = Math.min(100, odds * 1.2);
//            if (odds < 10) continue;

            double ratio = odds;

            int numOps = (int) Math.ceil((double) spiesUsed / attacker.getSpies());

            if (!foundOp) {
                ratio -= ratio * 0.1 * Math.abs((double) diff / interval);

                ratio *= 0.1;

                if (attacker.getPosition() <= 1) ratio *= 0.1;
            } else {
                ratio -= 0.1 * ratio * uncertainty;

                if (attacker.getPosition() <= 1) ratio *= 0.5;
            }

            StringBuilder message = new StringBuilder();

            if (foundOp) message.append("**");
            message.append(MathMan.format(odds) + "%");
            if (spySatellite) message.append(" | SAT");
            if (intelligence) message.append(" | IA");
            message.append(" | " + spiesUsed + "? spies (" + safety + ")");
            long diff_m = Math.abs(diff / TimeUnit.MINUTES.toMillis(1));
            message.append(" | " + diff_m + "m");
            if (foundOp) message.append("**");

            allOdds.put(attacker, new AbstractMap.SimpleEntry<>(ratio, message.toString()));
        }

        List<Map.Entry<DBNation, Map.Entry<Double, String>>> sorted = new ArrayList<>(allOdds.entrySet());
        sorted.sort((o1, o2) -> Double.compare(o2.getValue().getKey(), o1.getValue().getKey()));

        if (sorted.isEmpty()) {
            return "No results";
        }

        StringBuilder response = new StringBuilder();
        for (Map.Entry<DBNation, Map.Entry<Double, String>> entry : sorted) {
            DBNation att = entry.getKey();

            response.append(att.getNation() + " | " + att.getAlliance() + "\n");
            response.append(entry.getValue().getValue()).append("\n\n");
        }

        return response.toString();
    }
}
