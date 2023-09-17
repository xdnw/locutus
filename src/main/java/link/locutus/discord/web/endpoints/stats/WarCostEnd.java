package link.locutus.discord.web.endpoints.stats;

import link.locutus.discord.Locutus;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.entities.AttackCost;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.web.endpoints.Endpoint;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AbstractCursor;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Function;

public class WarCostEnd extends Endpoint {
    public WarCostEnd() {
        super("warcost");
    }

    @Override
    public String apply(long userId, List<String> path) {
        if (path.size() != 3) {
            return "TODO: prompt for AB";
        }

        String args0 = path.get(0);
        String args1 = path.get(1);
        int days = Integer.parseInt(path.get(2));

        List<AbstractCursor> attacks;
        Function<AbstractCursor, Boolean> isPrimary;
        Function<AbstractCursor, Boolean> isSecondary;
        String nameA;
        String nameB;

        long cutoffMs = ZonedDateTime.now(ZoneOffset.UTC).minusDays(days).toEpochSecond() * 1000L;

        Map<Integer, DBNation> nations = Locutus.imp().getNationDB().getNations();

        Set<Integer> aaIdss1 = DiscordUtil.parseAllianceIds(null, args0);
        Set<Integer> aaIdss2 = DiscordUtil.parseAllianceIds(null, args1);
        if (aaIdss1 != null && aaIdss2 != null && !aaIdss1.isEmpty() && !aaIdss2.isEmpty()) {
            HashSet<Integer> alliances = new HashSet<>();
            alliances.addAll(aaIdss1);
            alliances.addAll(aaIdss2);
            List<DBWar> wars = Locutus.imp().getWarDb().getWars(alliances, cutoffMs);
            Map<Integer, DBWar> warMap = new HashMap<>();
            for (DBWar war : wars) warMap.put(war.warId, war);
            attacks = Locutus.imp().getWarDb().getAttacksByWars(wars, cutoffMs);
            isPrimary = a -> {
                DBWar war = warMap.get(a.getWar_id());
                int aa1 = war.attacker_id == a.getAttacker_id() ? war.attacker_aa : war.defender_aa;
                int aa2 = war.attacker_id == a.getAttacker_id() ? war.defender_aa : war.attacker_aa;
                return aaIdss1.contains(aa1) && aaIdss2.contains(aa2);
            };
            isSecondary = a -> {
                DBWar war = warMap.get(a.getWar_id());
                int aa1 = war.attacker_id == a.getAttacker_id() ? war.attacker_aa : war.defender_aa;
                int aa2 = war.attacker_id == a.getAttacker_id() ? war.defender_aa : war.attacker_aa;
                return aaIdss2.contains(aa1) && aaIdss1.contains(aa2);
            };
            nameA = args0;
            nameB = args1;
        } else {
            Set<DBNation> alliances1 = DiscordUtil.parseNations(null, args0);
            Set<DBNation> alliances2 = DiscordUtil.parseNations(null, args1);
            Set<Integer> allIds = new HashSet<>();

            for (DBNation nation : alliances1) allIds.add(nation.getNation_id());
            for (DBNation nation : alliances2) allIds.add(nation.getNation_id());

            nameA = alliances1.size() == 1 ? alliances1.iterator().next().getNation() : args0;
            nameB = alliances2.size() == 1 ? alliances2.iterator().next().getNation() : args1;

            if (alliances1 == null || alliances1.isEmpty()) {
                return "Invalid alliance: `" + 0 + "`";
            }
            if (alliances2 == null || alliances2.isEmpty()) {
                return "Invalid alliance: `" + args1 + "`";
            }


            if (alliances1.size() == 1) {
                attacks = Locutus.imp().getWarDb().getAttacks(alliances1.iterator().next().getNation_id(), cutoffMs);
            } else if (alliances2.size() == 1) {
                attacks = Locutus.imp().getWarDb().getAttacks(alliances2.iterator().next().getNation_id(), cutoffMs);
            } else {
                attacks = Locutus.imp().getWarDb().getAttacks(allIds, cutoffMs);
            }

            isPrimary = a -> {
                DBNation n1 = nations.get(a.getAttacker_id());
                DBNation n2 = nations.get(a.getDefender_id());
                return n1 != null && n2 != null && alliances1.contains(n1) && alliances2.contains(n2);
            };
            isSecondary = a -> {
                DBNation n1 = nations.get(a.getAttacker_id());
                DBNation n2 = nations.get(a.getDefender_id());
                return n1 != null && n2 != null && alliances1.contains(n2) && alliances2.contains(n1);
            };
        }

        AttackCost cost = new AttackCost(nameA, nameB);
        cost.addCost(attacks, isPrimary, isSecondary);

        StringBuilder result = new StringBuilder(cost.toString());

        return result.toString();
    }
}