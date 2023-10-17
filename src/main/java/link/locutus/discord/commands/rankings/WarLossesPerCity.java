package link.locutus.discord.commands.rankings;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AbstractCursor;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static link.locutus.discord.util.MathMan.format;

public class WarLossesPerCity extends Command {
    public WarLossesPerCity() {
        super(CommandCategory.MILCOM, CommandCategory.GAME_INFO_AND_TOOLS);
    }

    @Override
    public String help() {
        return Settings.commandPrefix(true) + "WarLossesPerCity <alliance|coalition|*> <days>";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() != 2) {
            return usage(args.size(), 2, channel);
        }
        Collection<DBNation> nations = DiscordUtil.parseNations(guild, author, me, args.get(0), false, true);
        if (nations == null || nations.isEmpty()) {
            return "Invalid alliance or coalition: `" + args.get(0) + "`";
        }
        Map<Integer, DBNation> nationMap = nations.stream().collect(Collectors.toMap(DBNation::getNation_id, n -> n));

        Integer days = MathMan.parseInt(args.get(1));
        if (days == null) {
            return "Invalid number of days: `" + args.get(1) + "`";
        }
        long cutoffMs = ZonedDateTime.now(ZoneOffset.UTC).minusDays(days).toEpochSecond() * 1000L;

        double[] buffer = ResourceType.getBuffer();
        Map<Integer, Double> totals = new HashMap<>();
        Map<Integer, Integer> counters = new HashMap<>();
        List<AbstractCursor> attacks = Locutus.imp().getWarDb().getAttacksEither(nationMap.keySet(), cutoffMs);
        for (AbstractCursor attack : attacks) {
            if (attack.getVictor() != 0) {
                if (nationMap.containsKey(attack.getDefender_id())) {
                    Arrays.fill(buffer, 0d);
                    double defLoss = attack.getLossesConverted(buffer, false);
                    totals.put(attack.getDefender_id(), totals.getOrDefault(attack.getDefender_id(), 0d) + defLoss);
                    counters.put(attack.getDefender_id(), counters.getOrDefault(attack.getDefender_id(), 0) + 1);
                }
            }
        }

        List<Map.Entry<Integer, Double>> sorted = new ArrayList<>(totals.entrySet())
                .stream()
                .peek(entry -> {
                    DBNation nation = nationMap.get(entry.getKey());
                    int totalCounters = counters.getOrDefault(entry.getKey(), 0);
                    entry.setValue(entry.getValue() / (nation.getCities() * totalCounters));
                })
                .sorted(Comparator.comparingDouble(
                        o -> -o.getValue()))
                .toList();

        String title = args.get(0) + " City Losses per Def War (" + days + " days):";
        StringBuilder response = new StringBuilder();

        for (int i = 0; i < Math.min(20, sorted.size()); i++) {
            Map.Entry<Integer, Double> entry = sorted.get(i);
            int nationId = entry.getKey();
            DBNation nation = nationMap.get(nationId);
            double value = entry.getValue();
            String name = nation.getNation();
            name = name.substring(0, Math.min(32, name.length()));
            response.append('\n').append(String.format("%4s", i + 1)).append(". ").append(String.format("%32s", name)).append(": $").append(format(value));
        }

        String emoji = "Refresh";
        response.append("\n\nPress `").append(emoji).append("` to refresh");
        channel.create().embed(title, response.toString())
                        .commandButton(DiscordUtil.trimContent(fullCommandRaw), emoji).send();

        return null;
    }
}
