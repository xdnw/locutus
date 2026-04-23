package link.locutus.discord.web.commands.api;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.db.entities.Activity;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.sim.DamageObjective;
import link.locutus.discord.sim.GlobalPriorActivityProvider;
import link.locutus.discord.sim.SimTuning;
import link.locutus.discord.sim.planners.AdHocPlan;
import link.locutus.discord.sim.planners.AdHocSimulationOptions;
import link.locutus.discord.sim.planners.AdHocTargetPlanner;
import link.locutus.discord.sim.planners.AdHocTargetRecommendation;
import link.locutus.discord.sim.planners.AvailabilityWindow;
import link.locutus.discord.sim.planners.BlitzAssignment;
import link.locutus.discord.sim.planners.BlitzPlanner;
import link.locutus.discord.sim.planners.DBNationSnapshot;
import link.locutus.discord.sim.planners.OverrideSet;
import link.locutus.discord.sim.planners.ScheduledAttacker;
import link.locutus.discord.sim.planners.SnapshotActivityProvider;
import link.locutus.discord.sim.planners.ScheduledTargetPlan;
import link.locutus.discord.sim.combat.ResolutionMode;
import link.locutus.discord.sim.planners.ScheduledTargetPlanner;
import link.locutus.discord.sim.planners.TreatyProvider;
import link.locutus.discord.sim.planners.providers.DbActivityProvider;
import link.locutus.discord.web.commands.ReturnType;
import link.locutus.discord.web.commands.binding.value_types.CacheType;
import link.locutus.discord.web.commands.binding.value_types.WebSimAdHocPlan;
import link.locutus.discord.web.commands.binding.value_types.WebSimAdHocTarget;
import link.locutus.discord.web.commands.binding.value_types.WebTarget;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class SimEndpoints {

    @Command(desc = "Run the blitz planner on a fixed attacker and defender set", viewable = true)
    @ReturnType(value = BlitzAssignment.class, cache = CacheType.SessionStorage, duration = 30)
    public BlitzAssignment simBlitz(
            @Default("*") Set<DBNation> attackers,
            @Default("*") Set<DBNation> defenders,
            @Default("false") boolean stochastic,
            @Default("16") int stochasticSamples,
            @Default("1") long stochasticSeed
    ) {
        if (attackers == null || attackers.isEmpty()) {
            throw new IllegalArgumentException("Please provide at least one attacker");
        }
        if (defenders == null || defenders.isEmpty()) {
            throw new IllegalArgumentException("Please provide at least one defender");
        }
        SnapshotActivityProvider activityProvider = activityProviderFor(attackers, defenders);
        int currentTurn = currentWeekTurnUtc();
        List<DBNationSnapshot> attackerSnapshots = snapshots(attackers, false);
        List<DBNationSnapshot> defenderSnapshots = snapshots(defenders, false);
        return new BlitzPlanner(tuningFor(stochastic, stochasticSamples, stochasticSeed), TreatyProvider.NONE, OverrideSet.EMPTY,
                new DamageObjective(), activityProvider)
            .assign(attackerSnapshots, defenderSnapshots, currentTurn);
    }

    @Command(desc = "Rank ad-hoc targets for one attacker over a short deterministic horizon", viewable = true)
    @ReturnType(value = WebSimAdHocPlan.class, cache = CacheType.SessionStorage, duration = 30)
    public WebSimAdHocPlan simAdhoc(
            @Me @Default DBNation me,
            @Default DBNation attacker,
            @Default("*") Set<DBNation> defenders,
            @Default("8") int numResults,
            @Default("6") int horizonTurns,
            @Default("false") boolean stochastic,
            @Default("16") int stochasticSamples,
            @Default("1") long stochasticSeed
    ) {
        if (attacker == null) {
            attacker = me;
        }
        if (attacker == null) {
            throw new IllegalArgumentException("Please sign in or provide an attacker nation");
        }
        if (defenders == null || defenders.isEmpty()) {
            throw new IllegalArgumentException("Please provide at least one defender");
        }

        SnapshotActivityProvider activityProvider = activityProviderFor(attacker, defenders);
        int currentTurn = currentWeekTurnUtc();
        DBNationSnapshot attackerSnapshot = snapshots(java.util.List.of(attacker), false).get(0);
        List<DBNationSnapshot> defenderSnapshots = snapshots(defenders, false);
        AdHocTargetPlanner planner = new AdHocTargetPlanner(
                tuningFor(stochastic, stochasticSamples, stochasticSeed),
                TreatyProvider.NONE,
                OverrideSet.EMPTY,
                new DamageObjective(),
                activityProvider
        );
        AdHocPlan plan = planner.rankTargets(
            attackerSnapshot,
            defenderSnapshots,
                horizonTurns,
                numResults,
                currentTurn,
                AdHocSimulationOptions.DEFAULT
        );

        Map<Integer, DBNation> defenderById = new LinkedHashMap<>();
        for (DBNation defender : defenders) {
            defenderById.put(defender.getNation_id(), defender);
        }

        Activity activity = attacker.getActivity(14 * 12);
        double currentChance = activity.loginChance(currentTurn, Math.max(1, horizonTurns), true);
        int suggestedWaitTurns = 0;
        double futureChance = currentChance;
        double currentObjectiveScore = plan.bestObjectiveScore();
        double futureObjectiveScore = currentObjectiveScore;
        for (int delay = 1; delay <= 12; delay++) {
            double candidateChance = activity.loginChance(currentTurn + delay, Math.max(1, horizonTurns), true);
            AdHocPlan delayedPlan = planner.rankTargets(
                    attackerSnapshot,
                    defenderSnapshots,
                    horizonTurns,
                    1,
                    currentTurn + delay,
                    AdHocSimulationOptions.DEFAULT
            );
            double candidateScore = delayedPlan.bestObjectiveScore();
            if (candidateScore > futureObjectiveScore + 1e-9) {
                futureObjectiveScore = candidateScore;
                futureChance = candidateChance;
                suggestedWaitTurns = delay;
            }
        }

        List<WebSimAdHocTarget> targets = new ArrayList<>();
        boolean worthWaiting = suggestedWaitTurns > 0 && futureObjectiveScore > currentObjectiveScore + 1e-9;
        for (AdHocTargetRecommendation recommendation : plan.recommendations()) {
            DBNation defender = defenderById.get(recommendation.defenderId());
            if (defender == null) {
                continue;
            }
            double lootEstimate = defender.lootTotal(null);
            targets.add(new WebSimAdHocTarget(
                    new WebTarget(defender, recommendation.objectiveScore(), lootEstimate, recommendation.counterRisk()),
                    recommendation.objectiveScore(),
                    recommendation.counterRisk(),
                    lootEstimate,
                    recommendation.scoreSummary()
            ));
        }
        return new WebSimAdHocPlan(
                attacker.getNation_id(),
                horizonTurns,
                worthWaiting,
                suggestedWaitTurns,
                currentChance,
                futureChance,
                currentObjectiveScore,
                futureObjectiveScore,
                targets,
                plan.diagnostics(),
                plan.metadata()
        );
    }

    @Command(desc = "Plan rolling scheduled blitz buckets from per-attacker availability windows", viewable = true)
    @ReturnType(value = ScheduledTargetPlan.class, cache = CacheType.SessionStorage, duration = 30)
    public ScheduledTargetPlan simSchedule(
            @Default("*") Set<DBNation> attackers,
            @Default("*") Set<DBNation> defenders,
            @Default("") String availability,
            @Default("6") int bucketSizeTurns,
            @Default("false") boolean stochastic,
            @Default("16") int stochasticSamples,
            @Default("1") long stochasticSeed
    ) {
        if (attackers == null || attackers.isEmpty()) {
            throw new IllegalArgumentException("Please provide at least one attacker");
        }
        if (defenders == null || defenders.isEmpty()) {
            throw new IllegalArgumentException("Please provide at least one defender");
        }
        Map<Integer, DBNation> attackerById = new LinkedHashMap<>();
        for (DBNation attacker : attackers) {
            attackerById.put(attacker.getNation_id(), attacker);
        }

        SnapshotActivityProvider activityProvider = activityProviderFor(attackers, defenders);

        List<ScheduledAttacker> scheduledAttackers = parseAvailability(attackerById, availability, bucketSizeTurns);
        return new ScheduledTargetPlanner(tuningFor(stochastic, stochasticSamples, stochasticSeed), TreatyProvider.NONE, OverrideSet.EMPTY,
                new DamageObjective(), activityProvider)
            .assign(scheduledAttackers, snapshots(defenders, false), bucketSizeTurns);
    }

    private static SimTuning tuningFor(boolean stochastic, int stochasticSamples, long stochasticSeed) {
        if (!stochastic) {
            return SimTuning.defaults();
        }
        return SimTuning.defaults()
                .withStateResolutionMode(ResolutionMode.STOCHASTIC)
                .withStochasticSampleCount(stochasticSamples)
                .withStochasticSeed(stochasticSeed);
    }

    private static int currentWeekTurnUtc() {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        return now.getHour() / 2 + now.getDayOfWeek().ordinal() * 12;
    }

    private static List<DBNationSnapshot> snapshots(Collection<DBNation> nations, boolean includeCurrentDayUnitBuys) {
        Map<Integer, Map<link.locutus.discord.apiv1.enums.MilitaryUnit, Integer>> unitBuysTodayByNationId =
                includeCurrentDayUnitBuys
                        ? Locutus.imp().getNationDB().getSimUnitBuysToday(nations)
                        : Map.of();
        return DBNationSnapshot.of(nations, unitBuysTodayByNationId).stream()
                .sorted(Comparator.comparingInt(DBNationSnapshot::nationId))
                .toList();
    }

    private static SnapshotActivityProvider activityProviderFor(Collection<DBNation> attackers, Collection<DBNation> defenders) {
        Set<DBNation> allNations = new LinkedHashSet<>();
        allNations.addAll(attackers);
        allNations.addAll(defenders);
        return activityProviderFor(allNations);
    }

    private static SnapshotActivityProvider activityProviderFor(DBNation attacker, Collection<DBNation> defenders) {
        Set<DBNation> allNations = new LinkedHashSet<>();
        allNations.add(attacker);
        allNations.addAll(defenders);
        return activityProviderFor(allNations);
    }

    private static SnapshotActivityProvider activityProviderFor(Collection<DBNation> nations) {
        return new DbActivityProvider(
                DbActivityProvider.loadActivity(nations, 14 * 12),
                System.currentTimeMillis())
                .asSnapshotProvider(GlobalPriorActivityProvider.DEFAULT_PRIOR);
    }

    private static List<ScheduledAttacker> parseAvailability(
            Map<Integer, DBNation> attackerById,
            String availability,
            int bucketSizeTurns
    ) {
        if (availability == null || availability.isBlank()) {
            return attackerById.values().stream()
                    .map(DBNationSnapshot::of)
                    .map(snapshot -> new ScheduledAttacker(snapshot, List.of(new AvailabilityWindow(0, bucketSizeTurns - 1))))
                    .sorted(Comparator.comparingInt(entry -> entry.attacker().nationId()))
                    .toList();
        }

        Map<Integer, List<AvailabilityWindow>> windowsByNationId = new LinkedHashMap<>();
        String[] nationEntries = availability.split(";");
        for (String nationEntry : nationEntries) {
            if (nationEntry.isBlank()) {
                continue;
            }
            String[] parts = nationEntry.trim().split(":", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid availability entry: " + nationEntry);
            }
            int nationId = Integer.parseInt(parts[0].trim());
            DBNation attackerNation = attackerById.get(nationId);
            if (attackerNation == null) {
                throw new IllegalArgumentException("Availability references unknown attacker nationId=" + nationId);
            }
            List<AvailabilityWindow> windows = windowsByNationId.computeIfAbsent(nationId, ignored -> new ArrayList<>());
            for (String range : parts[1].split("\\|")) {
                String[] bounds = range.trim().split("-", 2);
                if (bounds.length != 2) {
                    throw new IllegalArgumentException("Invalid availability range: " + range);
                }
                windows.add(new AvailabilityWindow(Integer.parseInt(bounds[0].trim()), Integer.parseInt(bounds[1].trim())));
            }
        }

        Set<Integer> remainingNationIds = new LinkedHashSet<>(attackerById.keySet());
        remainingNationIds.removeAll(windowsByNationId.keySet());
        for (Integer nationId : remainingNationIds) {
            windowsByNationId.put(nationId, List.of(new AvailabilityWindow(0, bucketSizeTurns - 1)));
        }

        return windowsByNationId.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new ScheduledAttacker(
                        snapshots(java.util.List.of(attackerById.get(entry.getKey())), false).get(0),
                        entry.getValue()
                ))
                .toList();
    }
}
