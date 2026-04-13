package link.locutus.discord.web.commands.api;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Arg;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.annotation.Timediff;
import link.locutus.discord.commands.manager.v2.binding.annotation.Switch;
import link.locutus.discord.commands.manager.v2.binding.bindings.PlaceholderCache;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.IsMemberIngameOrDiscord;
import link.locutus.discord.commands.war.RaidCommand;
import link.locutus.discord.commands.war.WarTargetFinder;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBBounty;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DBTreasure;
import link.locutus.discord.web.commands.ReturnType;
import link.locutus.discord.web.commands.binding.value_types.CacheType;
import link.locutus.discord.web.commands.binding.value_types.WebTarget;
import link.locutus.discord.web.commands.binding.value_types.WebTargets;
import link.locutus.discord.web.commands.page.PageHelper;
import net.dv8tion.jda.api.entities.User;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static link.locutus.discord.commands.manager.v2.impl.pw.binding.PermissionBinding.checkMembership;

public class WarEndpoints extends PageHelper {
    @Command(desc = "Compute raid targets based on provided parameters", viewable = true)
    @ReturnType(value = WebTargets.class, cache = CacheType.SessionStorage, duration = 30)
    public WebTargets raid(@Me @Default GuildDB db, @Me @Default DBNation me, @Me @Default User user,
                           @Default DBNation nation,
                           @Default("*,#position<=1") Set<DBNation> nations,
                           @Default("false") boolean weak_ground,
                           @Default("0") int vm_turns,
                           @Default("0") int beige_turns,
                           @Default("false") boolean ignore_dnr,
                           @Default("7d") @Timediff long time_inactive,
                           @Default("-1") double min_loot,
                           @Default("8") int num_results) throws InterruptedException {
        if (nation == null) nation = me;
        if (nation == null) {
            throw new IllegalArgumentException("Please sign, or provide a nation to raid as");
        }
        if (db == null) db = nation.getGuildDB();
        if (db != null) {
            if (!checkMembership(db, null, user, me, false)) db = null;
        }

        List<Map.Entry<DBNation, Map.Entry<Double, Double>>> raidResult = RaidCommand.getNations(
                db,
                nation,
                nations,
                weak_ground,
                vm_turns,
                -1,
                beige_turns > 0,
                !ignore_dnr,
                Collections.emptySet(),
                false,
                false,
                time_inactive / TimeUnit.MINUTES.toMillis(1),
                nation.getScore(),
                min_loot, beige_turns,
                false, false, num_results
        );
        List<WebTarget> targets = new ObjectArrayList<>();
        for (Map.Entry<DBNation, Map.Entry<Double, Double>> entry : raidResult) {
            DBNation other = entry.getKey();
            double expected = entry.getValue().getKey();
            double loot = entry.getValue().getValue();
            targets.add(new WebTarget(other, expected, loot, 0));
        }
        return targets(nation, targets);
    }

    @Command(desc = "List unprotected counter targets with various filters", viewable = true)
    @ReturnType(WebTargets.class)
    public WebTargets unprotected(@Me @Default GuildDB db, @Me @Default DBNation me, @Default @Me User user,
                                  ValueStore store,
                                  @Default DBNation nation,
                                  @Default("*") Set<DBNation> nations,
                                  @Switch("a") boolean includeAllies,
                                  @Switch("o") boolean ignoreODP,
                                  @Default("false") boolean ignore_dnr,
                                  @Arg("The maximum allowed military strength of the target nation relative to you")
                                  @Switch("s") @Default("1.2") Double maxRelativeTargetStrength,
                                  @Arg("The maximum allowed military strength of counters relative to you")
                                  @Switch("c") @Default("1.2") Double maxRelativeCounterStrength,
                                  @Arg("Only list targets within range of ALL attackers")
                                  @Default("8") int num_results) throws InterruptedException {
        if (nation == null) nation = me;
        if (nation == null) {
            throw new IllegalArgumentException("Please sign, or provide a nation to raid as");
        }
        if (db == null) db = nation.getGuildDB();
        if (db != null) {
            if (!checkMembership(db, null, user, me, false)) db = null;
        }

        WarTargetFinder.CounterChanceContext counterContext = WarTargetFinder.buildCounterChanceContext(db, nations, ignore_dnr, includeAllies, Set.of(nation), false, ignoreODP, true);
        List<Map.Entry<DBNation, Double>> counterChance = WarTargetFinder.scoreCounterChance(counterContext, maxRelativeTargetStrength, maxRelativeCounterStrength);
        double myStrength = counterContext.blitzStrength();

        if (counterChance.size() > num_results) {
            counterChance = counterChance.subList(0, num_results);
        }

        List<DBNation> counterNations = counterChance.stream().map(Map.Entry::getKey).collect(Collectors.toList());
        ValueStore cacheStore = PlaceholderCache.createCache(store, counterNations, DBNation.class);

        List<WebTarget> targets = new ObjectArrayList<>();
        for (Map.Entry<DBNation, Double> entry : counterChance) {
            DBNation other = entry.getKey();
            double strength = entry.getValue();
            double loot = other.lootTotal(cacheStore);
            double strengthPercent = myStrength <= 0 ? 0 : 100 * strength / myStrength;
            targets.add(new WebTarget(other, loot, loot, strengthPercent));
        }
        WebTargets result = targets(nation, targets);
        result.include_strength = true;
        return result;
    }

    @Command(desc = "Find damage targets with infra-based sorting", viewable = true)
    @ReturnType(WebTargets.class)
    public WebTargets damage(@Me @Default GuildDB db, @Me @Default DBNation me, @Me @Default User user,
                             @Default DBNation nation,
                             @Default("*") Set<DBNation> nations,
                             @Switch("a") boolean includeApps,
                             @Switch("i") boolean includeInactives,
                             @Switch("w") boolean filterWeak,
                             @Switch("n") boolean noNavy,
                             @Switch("m") boolean targetMeanInfra,
                             @Switch("c") boolean targetCityMax,
                             @Switch("tb") boolean targetBeigeMax,
                             @Switch("b") boolean includeBeige,
                             @Switch("r") Double relativeNavalStrength,
                             @Switch("s") Double warRange,
                             @Default("15") int numResults) {
        if (nation == null) nation = me;
        if (nation == null) {
            throw new IllegalArgumentException("Please sign, or provide a nation to raid as");
        }
        if (db == null) db = nation.getGuildDB();
        if (db != null) {
            if (!checkMembership(db, null, user, me, false)) db = null;
        }

        int targetingOptions = 0;
        if (targetMeanInfra) targetingOptions++;
        if (targetCityMax) targetingOptions++;
        if (targetBeigeMax) targetingOptions++;
        if (targetingOptions > 1) {
            throw new IllegalArgumentException("Please select only one targeting option: `targetMeanInfra`, `targetCityMax`, or `targetBeigeMax`.");
        }

        WarTargetFinder.DamageTargets damageTargets = WarTargetFinder.getWarDamageTargets(
                nation,
                nations,
                includeApps,
                includeInactives,
                filterWeak,
                noNavy,
                includeBeige,
                relativeNavalStrength,
                warRange
        );

        List<Map.Entry<DBNation, Double>> maxInfraSorted = damageTargets.topTargets(numResults, targetMeanInfra, targetCityMax, targetBeigeMax);
        if (maxInfraSorted.isEmpty()) {
            return targets(nation, Collections.emptyList());
        }

        List<WebTarget> targets = new ObjectArrayList<>();
        for (int i = 0; i < Math.min(numResults, maxInfraSorted.size()); i++) {
            Map.Entry<DBNation, Double> entry = maxInfraSorted.get(i);
            DBNation other = entry.getKey();
            double expected = entry.getValue();
            double actual = damageTargets.damageEstByNation().getOrDefault(other.getNation_id(), expected);
            double strength = WarTargetFinder.damageHitCount(nation, other);
            targets.add(new WebTarget(other, expected, actual, strength));
        }

        WebTargets result = targets(nation, targets);
        result.include_strength = true;
        return result;
    }

    @Command(desc = "Find nations in war range that have a treasure", viewable = true)
    @ReturnType(WebTargets.class)
    public WebTargets treasure(@Me @Default GuildDB db, @Me @Default DBNation me, @Me @Default User user,
                               @Default DBNation nation,
                               @Switch("r") boolean onlyWeaker,
                               @Switch("d") boolean ignoreDNR,
                               @Default("5") int numResults) {
        if (nation == null) nation = me;
        if (nation == null) {
            throw new IllegalArgumentException("Please sign, or provide a nation to raid as");
        }
        if (db == null) db = nation.getGuildDB();
        if (db != null) {
            if (!checkMembership(db, null, user, me, false)) db = null;
        }

        Map<DBNation, Set<DBTreasure>> nationTreasures = WarTargetFinder.getTreasureTargets(nation, db, onlyWeaker, ignoreDNR);
        List<WebTarget> targets = new ArrayList<>(nationTreasures.size());
        for (Map.Entry<DBNation, Set<DBTreasure>> entry : nationTreasures.entrySet()) {
            DBNation other = entry.getKey();
            Set<DBTreasure> treasures = entry.getValue();
            double treasureBonus = treasures.stream().mapToDouble(DBTreasure::getBonus).sum();
            targets.add(new WebTarget(other, treasureBonus, treasureBonus, treasures.size()));
        }

        targets.sort(Comparator.comparingDouble((WebTarget target) -> target.expected).reversed()
                .thenComparingInt(target -> target.id));
        if (targets.size() > numResults) {
            targets = targets.subList(0, numResults);
        }

        WebTargets result = targets(nation, targets);
        result.include_strength = true;
        return result;
    }

    @Command(desc = "Find nations with high bounties within your war range", viewable = true)
    @ReturnType(WebTargets.class)
    public WebTargets bounty(@Me @Default GuildDB db, @Me @Default DBNation me, @Me @Default User user,
                             @Default DBNation nation,
                             @Switch("r") boolean onlyWeaker,
                             @Switch("d") boolean ignoreDNR,
                             @Switch("b") Set<WarType> bountyTypes,
                             @Default("5") int numResults) {
        if (nation == null) nation = me;
        if (nation == null) {
            throw new IllegalArgumentException("Please sign, or provide a nation to raid as");
        }
        if (db == null) db = nation.getGuildDB();
        if (db != null) {
            if (!checkMembership(db, null, user, me, false)) db = null;
        }

        Map<DBNation, Set<DBBounty>> nationBounties = WarTargetFinder.getBountyTargets(nation, db, onlyWeaker, ignoreDNR, bountyTypes);
        List<Map.Entry<DBNation, Double>> sorted = new ArrayList<>();
        Map<Integer, Double> totalByNationId = new java.util.HashMap<>();
        Map<Integer, Double> maxByNationId = new java.util.HashMap<>();
        Map<Integer, Double> countByNationId = new java.util.HashMap<>();

        for (Map.Entry<DBNation, Set<DBBounty>> entry : nationBounties.entrySet()) {
            DBNation other = entry.getKey();
            Set<DBBounty> bounties = entry.getValue();
            Map<WarType, Long> bountySum = bounties.stream().collect(Collectors.groupingBy(DBBounty::getType, Collectors.summingLong(DBBounty::getAmount)));
            double total = bountySum.values().stream().mapToLong(Long::longValue).sum();
            double max = bountySum.values().stream().mapToLong(Long::longValue).max().orElse(0);
            totalByNationId.put(other.getNation_id(), total);
            maxByNationId.put(other.getNation_id(), max);
            countByNationId.put(other.getNation_id(), bounties.size() * 1d);
            sorted.add(new java.util.AbstractMap.SimpleEntry<>(other, max));
        }

        sorted.sort(Comparator.comparingDouble(Map.Entry<DBNation, Double>::getValue).reversed());
        List<WebTarget> targets = new ObjectArrayList<>();
        for (int i = 0; i < Math.min(numResults, sorted.size()); i++) {
            DBNation other = sorted.get(i).getKey();
            double max = maxByNationId.getOrDefault(other.getNation_id(), 0d);
            double total = totalByNationId.getOrDefault(other.getNation_id(), 0d);
            double count = countByNationId.getOrDefault(other.getNation_id(), 0d);
            targets.add(new WebTarget(other, max, total, count));
        }

        WebTargets result = targets(nation, targets);
        result.include_strength = true;
        return result;
    }

    private WebTargets targets(DBNation self, List<WebTarget> targets) {
        WebTargets result = new WebTargets(self);
        result.targets = targets;
        return result;
    }
}