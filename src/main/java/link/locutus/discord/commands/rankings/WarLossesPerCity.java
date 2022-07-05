package link.locutus.discord.commands.rankings;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.apiv1.domains.subdomains.DBAttack;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static link.locutus.discord.util.MathMan.format;

public class WarLossesPerCity extends Command {
    public WarLossesPerCity() {
        super(CommandCategory.MILCOM, CommandCategory.GAME_INFO_AND_TOOLS);
    }
    @Override
    public String help() {
        return Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "WarLossesPerCity <alliance|coalition|*> <days>";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String onCommand(MessageReceivedEvent event, List<String> args) throws Exception {
        if (args.size() != 2) {
            return usage(event);
        }
        Collection<DBNation> nations = DiscordUtil.parseNations(event.getGuild(), args.get(0));
        if (nations == null || nations.isEmpty()) {
            return "Invalid alliance or coalition: `" + args.get(0) + "`";
        }
        Map<Integer, DBNation> nationMap = nations.stream().collect(Collectors.toMap(DBNation::getNation_id, n -> n));

        Integer days = MathMan.parseInt(args.get(1));
        if (days == null) {
            return "Invalid number of days: `" + args.get(1) + "`";
        }
        long cutoffMs = ZonedDateTime.now(ZoneOffset.UTC).minusDays(days).toEpochSecond() * 1000L;

        Map<Integer, Double> totals = new HashMap<>();
        Map<Integer, Integer> counters = new HashMap<>();
        List<DBAttack> attacks = Locutus.imp().getWarDb().getAttacks(cutoffMs);
        for (DBAttack attack : attacks) {
            if (attack.victor != 0) {
//                if (nationMap.containsKey(attack.attacker_nation_id)) {
//                    double attLoss = attack.getLossesConverted(true);
//                    totals.put(attack.attacker_nation_id, totals.getOrDefault(attack.attacker_nation_id, 0d) + attLoss);
//                    counters.put(attack.attacker_nation_id, counters.getOrDefault(attack.attacker_nation_id, 0) + 1);
//                }
                if (nationMap.containsKey(attack.defender_nation_id)) {
                    double defLoss = attack.getLossesConverted(false);
                    totals.put(attack.defender_nation_id, totals.getOrDefault(attack.defender_nation_id, 0d) + defLoss);
                    counters.put(attack.defender_nation_id, counters.getOrDefault(attack.defender_nation_id, 0) + 1);
                }
            }
        }

        List<Map.Entry<Integer, Double>> sorted = new ArrayList<>(totals.entrySet())
                .stream()
                .map(entry -> {
                    DBNation nation = nationMap.get(entry.getKey());
                    int totalCounters = counters.getOrDefault(entry.getKey(), 0);
                    entry.setValue(entry.getValue() / (nation.getCities() * totalCounters));
                    return entry;
                })
                .sorted(Comparator.comparingDouble(
                        o -> -o.getValue()))
                .collect(Collectors.toList()
                );

        String title = args.get(0) + " City Losses per Def War (" + days + " days):";
        StringBuilder response = new StringBuilder();

        for (int i = 0; i < Math.min(20, sorted.size()); i++) {
            Map.Entry<Integer, Double> entry = sorted.get(i);
            int nationId = entry.getKey();
            DBNation nation = nationMap.get(nationId);
            double value = entry.getValue();
            String name = nation.getNation();
            name = name.substring(0, Math.min(32, name.length()));
            response.append('\n').append(String.format("%4s", i + 1) + ". ").append(String.format("%32s", name)).append(": $").append(format(value));
        }

        String emoji = "\uD83D\uDD04";
        response.append("\n\npress " + emoji + " to refresh");
        Message msg = DiscordUtil.createEmbedCommand(event.getChannel(), title, response.toString(), emoji, DiscordUtil.trimContent(event.getMessage().getContentRaw()));

        return null;
    }
}
