package link.locutus.discord.commands.rankings;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.domains.subdomains.attack.DBAttack;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;

import static link.locutus.discord.util.MathMan.format;

public class AllianceLootRanking extends Command {

    public AllianceLootRanking() {
        super(CommandCategory.GAME_INFO_AND_TOOLS);
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String usage() {
        return super.usage() + " <days>";
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.isEmpty()) return usage(args.size(), 1, channel);
        Integer days = MathMan.parseInt(args.get(0));
        if (days == null) {
            return "Invalid number of days: `" + args.get(0) + "`";
        }
        long cutoffMs = ZonedDateTime.now(ZoneOffset.UTC).minusDays(days).toEpochSecond() * 1000L;

        List<DBAttack> attacks = Locutus.imp().getWarDb().getAttacks(cutoffMs);

        Map<Integer, DBNation> nations = Locutus.imp().getNationDB().getNations();
        Map<Integer, Map<ResourceType, Double>> byAlliance = new HashMap<>();

        for (DBAttack attack : attacks) {

            Map<ResourceType, Double> loot = attack.getLoot();
            Integer looter = attack.getLooter();
            if (looter != null) {
                DBNation nation = nations.get(looter);
                int allianceId = nation == null ? 0 : nation.getAlliance_id();
                Map<ResourceType, Double> map = byAlliance.get(allianceId);
                if (map == null) {
                    map = new HashMap<>();
                }
                Map<ResourceType, Double> add = PnwUtil.add(map, loot);
                byAlliance.put(allianceId, add);
            } else if (attack.getVictor() != 0 && attack.getMoney_looted() != 0) {
                DBNation nation = nations.get(attack.getVictor());
                int allianceId = nation == null ? 0 : nation.getAlliance_id();
                Map<ResourceType, Double> map = byAlliance.get(allianceId);
                if (map == null) {
                    map = new HashMap<>();
                }
                map.put(ResourceType.MONEY, (map.getOrDefault(ResourceType.MONEY, 0d) + attack.getMoney_looted()));
                byAlliance.put(allianceId, map);
            }
        }

        List<Map.Entry<Integer, Map<ResourceType, Double>>> sorted = new ArrayList<>(byAlliance.entrySet())
                .stream()
                .sorted(Comparator.comparingDouble(
//                        o -> -PnwUtil.convertedTotal(o.getValue())))
                        o -> -o.getValue().getOrDefault(ResourceType.MONEY, 0d)))
                .toList();

        String title = "Loot (" + days + " days):";
        StringBuilder response = new StringBuilder();

        for (int i = 0; i < Math.min(25, sorted.size()); i++) {
            Map.Entry<Integer, Map<ResourceType, Double>> entry = sorted.get(i);
            int allianceId = entry.getKey();
            Double value = entry.getValue().getOrDefault(ResourceType.MONEY, 0d);

            String name = PnwUtil.getName(allianceId, true);
            name = name.substring(0, Math.min(32, name.length()));

            response.append('\n').append(String.format("%4s", i + 1)).append(". ").append(String.format("%32s", name)).append(": $").append(format(value));
        }

        String emoji = "Refresh";
        String cmd = DiscordUtil.trimContent(fullCommandRaw);
        response.append("\n\nPress `").append(emoji).append("` to refresh");
        channel.create().embed(title, response.toString())
                        .commandButton(cmd, emoji).send();

        return null;
    }
}
