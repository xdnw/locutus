package com.boydti.discord.commands.rankings;

import com.boydti.discord.Locutus;
import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.commands.manager.CommandCategory;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.util.discord.DiscordUtil;
import com.boydti.discord.util.MathMan;
import com.boydti.discord.util.PnwUtil;
import com.google.common.collect.BiMap;
import com.boydti.discord.apiv1.domains.subdomains.DBAttack;
import com.boydti.discord.apiv1.enums.ResourceType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.boydti.discord.util.MathMan.format;

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
    public String onCommand(MessageReceivedEvent event, List<String> args) throws Exception {
        if (args.isEmpty()) return usage(event);
        Integer days = MathMan.parseInt(args.get(0));
        if (days == null) {
            return "Invalid number of days: `" + args.get(0) + "`";
        }
        long cutoffMs = ZonedDateTime.now(ZoneOffset.UTC).minusDays(days).toEpochSecond() * 1000L;

        List<DBAttack> attacks = Locutus.imp().getWarDb().getAttacks(cutoffMs);

        Map<Integer, DBNation> nations = Locutus.imp().getNationDB().getNations();
        Map<Integer, Map<ResourceType, Double>> byAlliance = new HashMap<>();

        for (DBAttack attack : attacks) {
//            if (attack.attack_type != AttackType.VICTORY && attack.attack_type != AttackType.A_LOOT) continue;
//            if (attack.attack_type == AttackType.A_LOOT) continue;

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
            } else if (attack.victor != 0 && attack.money_looted != 0) {
                DBNation nation = nations.get(attack.victor);
                int allianceId = nation == null ? 0 : nation.getAlliance_id();
                Map<ResourceType, Double> map = byAlliance.get(allianceId);
                if (map == null) {
                    map = new HashMap<>();
                }
                map.put(ResourceType.MONEY, (map.getOrDefault(ResourceType.MONEY, 0d) + attack.money_looted));
                byAlliance.put(allianceId, map);
            }
        }

        List<Map.Entry<Integer, Map<ResourceType, Double>>> sorted = new ArrayList<>(byAlliance.entrySet())
                .stream()
                .sorted(Comparator.comparingDouble(
//                        o -> -PnwUtil.convertedTotal(o.getValue())))
                        o -> -o.getValue().getOrDefault(ResourceType.MONEY, 0d)))
                .collect(Collectors.toList()
                );

        String title = "Loot (" + days + " days):";
        StringBuilder response = new StringBuilder();

        BiMap<Integer, String> alliances = Locutus.imp().getNationDB().getAlliances();
        for (int i = 0; i < Math.min(25, sorted.size()); i++) {
            Map.Entry<Integer, Map<ResourceType, Double>> entry = sorted.get(i);
            int allianceId = entry.getKey();
            Double value = entry.getValue().getOrDefault(ResourceType.MONEY, 0d);

            String name = alliances.getOrDefault(allianceId, Integer.toString(allianceId));
            name = name.substring(0, Math.min(32, name.length()));

            response.append('\n').append(String.format("%4s", i + 1) + ". ").append(String.format("%32s", name)).append(": $").append(format(value));
        }

        String emoji = "\uD83D\uDD04";
        String cmd = DiscordUtil.trimContent(event.getMessage().getContentRaw());
        response.append("\n\npress " + emoji + " to refresh");
        Message msg = DiscordUtil.createEmbedCommand(event.getChannel(), title, response.toString(), emoji, cmd);

        return null;
    }
}
