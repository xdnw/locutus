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
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static link.locutus.discord.util.MathMan.format;

public class NationLootRanking extends Command {
    public NationLootRanking() {
        super(CommandCategory.GAME_INFO_AND_TOOLS, CommandCategory.MILCOM);
    }
    @Override
    public String help() {
        return Settings.commandPrefix(true) + "NationLootRanking <alliances|coalitions|*> <days>";
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
        List<DBAttack> attacks = Locutus.imp().getWarDb().getAttacks(cutoffMs);
        for (DBAttack attack : attacks) {
            if (attack.victor != 0 && attack.money_looted != 0) {
                if (nationMap.containsKey(attack.victor)) {
                    totals.put(attack.victor, totals.getOrDefault(attack.victor, 0d) + attack.money_looted);
                }
            }
        }

        List<Map.Entry<Integer, Double>> sorted = totals.entrySet().stream().sorted((o1, o2) -> -Double.compare(o1.getValue(), o2.getValue())).collect(Collectors.toList());

        String title = args.get(0) + " Looted from nations (" + days + " days):";
        StringBuilder response = new StringBuilder();

        for (int i = 0; i < Math.min(20, sorted.size()); i++) {
            Map.Entry<Integer, Double> entry = sorted.get(i);
            int nationId = entry.getKey();
            double value = entry.getValue();

            DBNation nation = nationMap.get(nationId);
            String name = nation == null ? Integer.toString(nationId) : nation.getNation();
            name = name.substring(0, Math.min(32, name.length()));

            response.append('\n').append(String.format("%4s", i + 1) + ". ").append(String.format("%32s", name)).append(": $").append(format(value));
        }

        String emoji = "\uD83D\uDD04";
        response.append("\n\npress " + emoji + " to refresh");
        Message msg = DiscordUtil.createEmbedCommand(event.getChannel(), title, response.toString(), emoji, DiscordUtil.trimContent(event.getMessage().getContentRaw()));

        return null;
    }
}
