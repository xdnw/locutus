package link.locutus.discord.db.entities.metric;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.Locutus;
import link.locutus.discord.Logg;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv3.csv.DataDumpParser;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.scheduler.ThrowingFunction;

import java.io.IOException;
import java.text.ParseException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.function.Predicate;

public class GrowthSummary {

    private final Map<Integer, AllianceGrowthSummary> byAlliance = new Int2ObjectOpenHashMap<>();
    public static int RECRUITED_DAYS = 7;
    public static int MAX_DAYS = 365;
    private final Set<Integer> allowedAlliances;
    private final long dayStart;
    private final long dayEnd;

    public GrowthSummary(Set<DBAlliance> alliances, long dayStart, long dayEnd) throws IOException, ParseException {
        this.allowedAlliances = new IntOpenHashSet();
        for (DBAlliance alliance : alliances) allowedAlliances.add(alliance.getId());

        this.dayStart = dayStart;
        this.dayEnd = dayEnd;
    }

    private Map<Integer, Set<Integer>> allianceMembership(Map<Integer, DBNation> nations, Set<Integer> allowedAlliances) {
        Map<Integer, Set<Integer>> allianceMembership = new Int2ObjectOpenHashMap<>();
        for (Map.Entry<Integer, DBNation> entry : nations.entrySet()) {
            DBNation nation = entry.getValue();
            int aaId = nation.getAlliance_id();
            if (aaId == 0 || !allowedAlliances.contains(aaId)) continue;
            if (nation.getPositionEnum().id >= Rank.MEMBER.id) {
                allianceMembership.computeIfAbsent(aaId, f -> new IntOpenHashSet()).add(entry.getKey());
            }
        }
        return allianceMembership;
    }

    private void applyAlliance(ForkJoinPool pool, Set<Integer> allowedAlliances, long day, Map<Integer, DBNation> from, Map<Integer, Set<Integer>> fromNatByAA, Map<Integer, DBNation> to, Map<Integer, Set<Integer>> toNatByAA) {
        for (int aaId : allowedAlliances) {
            apply(aaId, day, from, fromNatByAA, to, toNatByAA);
        }
    }

    public void apply(int allianceId, long day, Map<Integer, DBNation> from, Map<Integer, Set<Integer>> fromNatByAA, Map<Integer, DBNation> to, Map<Integer, Set<Integer>> toNatByAA) {
        AllianceGrowthSummary summary = byAlliance.computeIfAbsent(allianceId, _ -> new AllianceGrowthSummary());
        Set<Integer> nationIds = new IntOpenHashSet();

        Set<Integer> memberBefore = fromNatByAA.getOrDefault(allianceId, Set.of());
        Set<Integer> memberAfter = toNatByAA.getOrDefault(allianceId, Set.of());
        nationIds.addAll(memberBefore);
        nationIds.addAll(memberAfter);

        if (nationIds.isEmpty()) return;
        updateDay(day, summary, allianceId, from, to, nationIds, memberBefore, memberAfter);
    }

    public GrowthSummary run() throws IOException, ParseException {
        DataDumpParser dumper = Locutus.imp().getDataDumper(true).load();
        if (dayEnd > TimeUtil.getDay()) {
            throw new IllegalArgumentException("Invalid future day specified for growth summary:" + dayEnd);
        }
        if (dayStart >= dayEnd) {
            throw new IllegalArgumentException("Invalid day range specified for growth summary: " + dayStart + " to " + dayEnd);
        }
        if (dayEnd - dayStart > MAX_DAYS) {
            throw new IllegalArgumentException("Invalid day range specified for growth summary: " + dayStart + " to " + dayEnd + " (max " + MAX_DAYS + ")");
        }

        ForkJoinPool forkJoinPool = new ForkJoinPool();
        List<ForkJoinTask<?>> tasks = new ObjectArrayList<>();

        long start = System.nanoTime();
        Map<Long, Map<Integer, DBNation>> byDay = new ConcurrentHashMap<>();
        Map<Long, Map<Integer, Set<Integer>>> memberShip = new ConcurrentHashMap<>();

        int dayEndWithCurrent = dayEnd == TimeUtil.getDay() ? (int) dayEnd + 1 : (int) dayEnd;

        ThrowingFunction<Long, Map<Integer, DBNation>> fetchNations = new ThrowingFunction<Long, Map<Integer, DBNation>>() {
            @Override
            public Map<Integer, DBNation> applyThrows(Long day) throws Exception {
                if (day == dayEnd + 1) {
                    return Locutus.imp().getNationDB().getNationsById();
                }
                return (Map) dumper.getNations(day);
            }
        };

        List<ForkJoinTask<Map<Integer, DBNation>>> fetchTasks = new ObjectArrayList<>();

        for (long day = dayStart; day <= dayEndWithCurrent; day++) {
            long finalDay = day;
            fetchTasks.add(forkJoinPool.submit(() -> {
                Map<Integer, DBNation> now = fetchNations.apply(finalDay);
                byDay.put(finalDay, now);
                memberShip.put(finalDay, allianceMembership(now, allowedAlliances));
                return now;
            }));
        }

        // Ensure all fetch tasks are done
        for (ForkJoinTask<Map<Integer, DBNation>> task : fetchTasks) {
            task.join();
        }
        // shutdown the pool
        forkJoinPool.shutdown();

        // Process the data sequentially
        for (long day = dayStart + 1; day <= dayEndWithCurrent; day++) {
            Map<Integer, DBNation> now = byDay.get(day);
            Map<Integer, DBNation> last = byDay.get(day - 1);
            Map<Integer, Set<Integer>> nowMembership = memberShip.get(day);
            Map<Integer, Set<Integer>> lastMembership = memberShip.get(day - 1);

            applyAlliance(
                    forkJoinPool,
                    allowedAlliances,
                    day,
                    last,
                    lastMembership,
                    now,
                    nowMembership);
        }


        long diff = System.nanoTime() - start;

        long diffMs = diff / 1000000;
        Logg.info("GrowthSummary task: Consume + Get: " + diffMs + "ms");

        return this;
    }

    public Map<Integer, AllianceGrowthSummary> getSummaries() {
        return byAlliance;
    }

    public void updateDay(long day, AllianceGrowthSummary summary, int aaId, Map<Integer, DBNation> nationsFrom, Map<Integer, DBNation> nationsTo, Set<Integer> relevantIds, Set<Integer> memberBefore, Set<Integer> memberAfter) {
        for (int nationId : relevantIds) {
            DBNation from = nationsFrom.get(nationId);
            DBNation to = nationsTo.get(nationId);
            if (from == null && to == null) continue;

            boolean fromMember = memberBefore.contains(nationId);
            boolean toMember = memberAfter.contains(nationId);

            MembershipChangeReason reason;
            if (to == null) {
                if (DBNation.getById(nationId) == null) {
                    reason = MembershipChangeReason.DELETED;
                } else {
                    reason = MembershipChangeReason.VM_LEFT;
                }
            } else if (from == null) {
                if (to.getAgeDays() > RECRUITED_DAYS) {
                    reason = MembershipChangeReason.VM_RETURNED;
                } else {
                    reason = MembershipChangeReason.RECRUITED;
                }
            } else {
                boolean joined = toMember && !fromMember;
                boolean left = !joined && from != null && fromMember && !toMember;

                if (joined) {
                    if (from.getAgeDays() <= RECRUITED_DAYS) { // (from.getAlliance_id() == 0 || from.getAlliance_id() == aaId) &&
                        reason = MembershipChangeReason.RECRUITED;
                    } else {
                        reason = MembershipChangeReason.JOINED;
                    }
                } else if (left) {
                    reason = MembershipChangeReason.LEFT;
                } else {
                    reason = MembershipChangeReason.UNCHANGED;
                }
            }

            { // Set initial
                 if (reason == MembershipChangeReason.UNCHANGED) {
                     if (fromMember) {
                         summary.initialState.putIfAbsent(nationId, MembershipChangeReason.UNCHANGED);
                     } else {
                         summary.initialState.putIfAbsent(nationId, null);
                     }
                 } else {
                     summary.initialState.putIfAbsent(nationId, reason);
                 }
            }
            { // Set final
                if (reason == MembershipChangeReason.UNCHANGED) {
                    if (toMember) {
                        summary.finalState.put(nationId, MembershipChangeReason.UNCHANGED);
                    } else {
                        summary.finalState.put(nationId, null);
                    }
                } else {
                    summary.finalState.put(nationId, reason);
                }
            }

            if (reason != null && reason != MembershipChangeReason.UNCHANGED) {
                summary.countByReason.compute(reason, (k, v) -> v == null ? 1 : v + 1);
                summary.uniqueByReason.computeIfAbsent(reason, k -> new IntOpenHashSet()).add(nationId);

                for (GrowthAsset asset : link.locutus.discord.db.entities.metric.GrowthAsset.values()) {
                    int lastAmt = summary.lastCount.computeIfAbsent(nationId, k -> new Object2IntOpenHashMap<>()).getOrDefault(asset, 0);
                    double[] lastValue = summary.lastValue.computeIfAbsent(nationId, k -> new Object2ObjectOpenHashMap<>()).getOrDefault(asset, null);

                    int currentAmt;
                    double[] currentValue;
                    if (reason.afterwardsMember()) {
                        currentAmt = asset.get(to);
                        currentValue = asset.value(ResourceType.getBuffer(), to);
                    } else if (reason.previouslyMember()) {
                        currentAmt = asset.get(from);
                        currentValue = asset.value(ResourceType.getBuffer(), from);
                    } else {
                        throw new IllegalArgumentException("Invalid state: " + reason);
                    }
                    int amtDiff = currentAmt - lastAmt;

                    summary.growthCountByNation.computeIfAbsent(asset, k -> new Object2ObjectOpenHashMap<>())
                            .computeIfAbsent(reason, k -> new Int2IntOpenHashMap()).merge(nationId, amtDiff, Integer::sum);
                    double[] growthValue = summary.growthValueByNation.computeIfAbsent(asset, k -> new Object2ObjectOpenHashMap<>())
                            .computeIfAbsent(reason, k -> new Int2ObjectOpenHashMap<>()).computeIfAbsent(nationId, k -> ResourceType.getBuffer());
                    ResourceType.add(growthValue, currentValue);
                    if (lastValue != null) {
                        ResourceType.subtract(growthValue, lastValue);
                    }

                    summary.lastCount.computeIfAbsent(nationId, k -> new Object2IntOpenHashMap<>()).put(asset, currentAmt);
                    summary.lastValue.computeIfAbsent(nationId, k -> new Object2ObjectOpenHashMap<>()).put(asset, currentValue);
                }
            }
            if (reason == MembershipChangeReason.UNCHANGED && toMember) {
                for (GrowthAsset asset : link.locutus.discord.db.entities.metric.GrowthAsset.values()) {
                    int amtDiff = asset.get(to) - asset.get(from);

                    summary.growthCountByNation.computeIfAbsent(asset, k -> new Object2ObjectOpenHashMap<>())
                            .computeIfAbsent(reason, k -> new Int2IntOpenHashMap()).merge(nationId, amtDiff, Integer::sum);
                    double[] growthValue = summary.growthValueByNation.computeIfAbsent(asset, k -> new Object2ObjectOpenHashMap<>())
                            .computeIfAbsent(reason, k -> new Int2ObjectOpenHashMap<>()).computeIfAbsent(nationId, k -> ResourceType.getBuffer());
                    growthValue = asset.value(growthValue, from, to);
                }
            }
        }
    }

    public static class AllianceGrowthSummary {
        public Map<Integer, MembershipChangeReason> initialState = new Int2ObjectOpenHashMap<>();
        public Map<Integer, MembershipChangeReason> finalState = new Int2ObjectOpenHashMap<>();

        public Map<Integer, Map<GrowthAsset, Integer>> lastCount = new Int2ObjectOpenHashMap<>();
        public Map<Integer, Map<GrowthAsset, double[]>> lastValue = new Int2ObjectOpenHashMap<>();

        public Map<GrowthAsset, Map<MembershipChangeReason, Map<Integer, Integer>>> growthCountByNation = new Object2ObjectOpenHashMap<>();

        public Map<GrowthAsset, Map<MembershipChangeReason, Map<Integer, double[]>>> growthValueByNation = new Object2ObjectOpenHashMap<>();

        public Map<MembershipChangeReason, Integer> countByReason = new Object2ObjectOpenHashMap<>();
        public Map<MembershipChangeReason, Set<Integer>> uniqueByReason = new Object2ObjectOpenHashMap<>();

        public double[] getSpending(Predicate<GrowthAsset> assetPredicate, Predicate<MembershipChangeReason> reasonPredicate, boolean effective) {
            double[] total = ResourceType.getBuffer();
            // if effective, only do when finalState is member
            for (Map.Entry<GrowthAsset, Map<MembershipChangeReason, Map<Integer, double[]>>> entry : growthValueByNation.entrySet()) {
                if (!assetPredicate.test(entry.getKey())) continue;
                for (Map.Entry<MembershipChangeReason, Map<Integer, double[]>> entry2 : entry.getValue().entrySet()) {
                    MembershipChangeReason reason = entry2.getKey();
                    if (!reasonPredicate.test(reason)) continue;
                    for (Map.Entry<Integer, double[]> entry3 : entry2.getValue().entrySet()) {
                        int nationId = entry3.getKey();
                        double[] amt = entry3.getValue();
                        if (effective) {
                            MembershipChangeReason state = finalState.get(nationId);
                            if (state == null || !state.afterwardsMember()) continue;
                        }
                        total = ResourceType.add(total, amt);
                    }
                }
            }
            return total;
        }

        public double getSpendingValue(Predicate<GrowthAsset> assetPredicate, Predicate<MembershipChangeReason> reasonPredicate, boolean effective) {
            return ResourceType.convertedTotal(getSpending(assetPredicate, reasonPredicate, effective));
        }

        public int getReasonCounts(Predicate<MembershipChangeReason> reasonPredicate) {
            int total = 0;
            for (Map.Entry<MembershipChangeReason, Set<Integer>> entry : uniqueByReason.entrySet()) {
                if (reasonPredicate.test(entry.getKey())) {
                    total += entry.getValue().size();
                }
            }
            return total;
        }

        public int getAssetCounts(Predicate<GrowthAsset> assetPredicate, Predicate<MembershipChangeReason> reasonPredicate, boolean effective) {
            int total = 0;
            // if effective, only do when finalState is member
            for (Map.Entry<GrowthAsset, Map<MembershipChangeReason, Map<Integer, Integer>>> entry : growthCountByNation.entrySet()) {
                if (!assetPredicate.test(entry.getKey())) continue;
                for (Map.Entry<MembershipChangeReason, Map<Integer, Integer>> entry2 : entry.getValue().entrySet()) {
                    MembershipChangeReason reason = entry2.getKey();
                    if (!reasonPredicate.test(reason)) continue;
                    for (Map.Entry<Integer, Integer> entry3 : entry2.getValue().entrySet()) {
                        int nationId = entry3.getKey();
                        int amt = entry3.getValue();
                        if (effective) {
                            MembershipChangeReason state = finalState.get(nationId);
                            if (state == null || !state.afterwardsMember()) continue;
                        }
                        total += amt;
                    }
                }
            }
            return total;
        }
    }
}
