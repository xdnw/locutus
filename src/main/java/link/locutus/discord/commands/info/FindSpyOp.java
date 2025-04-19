package link.locutus.discord.commands.info;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DBSpyUpdate;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.SpyCount;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.scheduler.KeyValue;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class FindSpyOp extends Command {
    public FindSpyOp() {
        super(CommandCategory.MILCOM, CommandCategory.MEMBER);
    }

    @Override
    public List<CommandRef> getSlashReference() {
        return List.of(CM.settings_war_alerts.ESPIONAGE_ALERT_CHANNEL.cmd);
    }
    @Override
    public String help() {
        return super.help() + " <time> <defender-spies> [defender-nation]";
    }

    @Override
    public String desc() {
        return "See who was online at the time of a spy op e.g.\n" +
                "`" + Settings.commandPrefix(true) + "findspyop \"08/05 04:33 pm\" 50`";
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() < 2) return usage(args.size(), 2, channel);

        int defenderSpies = Integer.parseInt(args.get(1));

        DBNation defender;
        if (args.size() == 3) {
            defender = DiscordUtil.parseNation(args.get(2), true);
        } else if (args.size() == 2) {
            defender = me;
        } else return usage(args.size(), 2, 3, channel);

        Set<Integer> ids = new HashSet<>();
        Map<DBSpyUpdate, Long> updatesTmp = new HashMap<>();
        long interval = TimeUnit.MINUTES.toMillis(3);

        String[] times = args.get(0).split(",");
        for (String timeStr : times) {
            long timestamp = TimeUtil.parseDate(TimeUtil.MMDD_HH_MM_A, timeStr, true);
            List<DBSpyUpdate> updates = Locutus.imp().getNationDB().getSpyActivity(timestamp, interval);
            for (DBSpyUpdate update : updates) {
                DBNation nation = DBNation.getById(update.nation_id);
                if (nation == null) continue;
                assert defender != null;
                if (!defender.isInSpyRange(nation)) continue;

                if (ids.contains(update.nation_id)) continue;
                ids.add(update.nation_id);
                updatesTmp.put(update, Math.abs(timestamp - update.timestamp));

            }
        }

        if (updatesTmp.isEmpty()) return "No results.";

        Map<DBNation, Map.Entry<Double, String>> allOdds = new HashMap<>();

        for (Map.Entry<DBSpyUpdate, Long> entry : updatesTmp.entrySet()) {
            DBSpyUpdate update = entry.getKey();
            long diff = entry.getValue();

            DBNation attacker = Locutus.imp().getNationDB().getNationById(update.nation_id);
            if (attacker == null || !attacker.isInSpyRange(defender) || attacker.getNation_id() == defender.getNation_id())
                continue;

            int spiesUsed = update.spies;


            int safety = 3;
            int uncertainty = -1;
            boolean foundOp = false;
            boolean spySatellite = Projects.SPY_SATELLITE.hasBit(update.projects);
            boolean intelligence = Projects.INTELLIGENCE_AGENCY.hasBit(update.projects);

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

            message.append(MathMan.format(odds)).append("%");
            if (spySatellite) message.append(" | SAT");
            if (intelligence) message.append(" | IA");
            message.append(" | ").append(spiesUsed).append("? spies (").append(safety).append(")");
            long diff_m = Math.abs(diff / TimeUnit.MINUTES.toMillis(1));
            message.append(" | ").append(diff_m).append("m");

            allOdds.put(attacker, new KeyValue<>(ratio, message.toString()));
        }

        List<Map.Entry<DBNation, Map.Entry<Double, String>>> sorted = new ArrayList<>(allOdds.entrySet());
        sorted.sort((o1, o2) -> Double.compare(o2.getValue().getKey(), o1.getValue().getKey()));

        if (sorted.isEmpty()) {
            return "No results";
        }

        StringBuilder response = new StringBuilder();
        for (Map.Entry<DBNation, Map.Entry<Double, String>> entry : sorted) {
            DBNation att = entry.getKey();

            response.append(att.getNation()).append(" | ").append(att.getAllianceName()).append("\n");
            response.append(entry.getValue().getValue()).append("\n\n");
        }

        return response.toString();
    }
}
