package link.locutus.discord.db.entities.metric;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv3.csv.DataDumpParser;
import link.locutus.discord.db.entities.nation.DBNationSnapshot;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.scheduler.QuadConsumer;

import java.io.IOException;
import java.text.ParseException;
import java.util.Map;
import java.util.Set;
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

    private Map<Integer, Set<Integer>> allianceMembership(Map<Integer, DBNation> nations) {
        Map<Integer, Set<Integer>> allianceMembership = new Int2ObjectOpenHashMap<>();
        for (Map.Entry<Integer, DBNation> entry : nations.entrySet()) {
            DBNation nation = entry.getValue();
            int aaId = nation.getAlliance_id();
            if (aaId != 0 && nation.getPositionEnum().id >= Rank.MEMBER.id) {
                allianceMembership.computeIfAbsent(aaId, f -> new IntOpenHashSet()).add(entry.getKey());
            }
        }
        return allianceMembership;
    }

    public void apply(int allianceId, long day, Map<Integer, DBNation> from, Map<Integer, Set<Integer>> fromNatByAA, Map<Integer, DBNation> to, Map<Integer, Set<Integer>> toNatByAA) {
        AllianceGrowthSummary summary = byAlliance.computeIfAbsent(allianceId, _ -> new AllianceGrowthSummary());
        Set<Integer> nationIds = new IntOpenHashSet();
        nationIds.addAll(fromNatByAA.getOrDefault(allianceId, Set.of()));
        nationIds.addAll(toNatByAA.getOrDefault(allianceId, Set.of()));
        if (nationIds.isEmpty()) return;
        updateDay(day, summary, allianceId, from, to, nationIds);
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

        Map<Integer, DBNation> last2 = null;
        Map<Integer, Set<Integer>> lastMembership = null;
        long diffNanoConsume = 0;
        long diffNanoGet = 0;
        for (long day = dayStart; day <= dayEnd; day++) {
            long start1 = System.nanoTime();
            Map<Integer, DBNation> now = (Map) dumper.getNations(day);
            Map<Integer, Set<Integer>> nowMembership = allianceMembership(now);
            diffNanoGet += System.nanoTime() - start1;
            if (last2 == null) {
                last2 = now;
                lastMembership = nowMembership;
                continue;
            }

            start1 = System.nanoTime();
            for (int aaId : allowedAlliances) {
                apply(aaId, day, last2, lastMembership, now, nowMembership);
            }
            diffNanoConsume += System.nanoTime() - start1;

            last2 = now;
            lastMembership = nowMembership;
        }
        if (dayEnd == TimeUtil.getDay()) {
            Map<Integer, DBNation> now = Locutus.imp().getNationDB().getNationsById();
            Map<Integer, Set<Integer>> nowMembership = allianceMembership(now);
            long start1 = System.nanoTime();
            for (int aaId : allowedAlliances) {
                apply(aaId, dayEnd, last2, lastMembership, now, nowMembership);
            }
            diffNanoConsume += System.nanoTime() - start1;
        }

        long diffConsumeMs = diffNanoConsume / 1000000;
        long diffGetMs = diffNanoGet / 1000000;
        System.out.println("GrowthSummary: Consume: " + diffConsumeMs + "ms, Get: " + diffGetMs + "ms");

        return this;
    }

    public Map<Integer, AllianceGrowthSummary> getSummaries() {
        return byAlliance;
    }

    public void updateDay(long day, AllianceGrowthSummary summary, int aaId, Map<Integer, DBNation> nationsFrom, Map<Integer, DBNation> nationsTo, Set<Integer> relevantIds) {
        for (int nationId : relevantIds) {
            DBNation from = nationsFrom.get(nationId);
            DBNation to = nationsTo.get(nationId);
            if (from == null && to == null) continue;

            boolean fromMember = from != null && from.getAlliance_id() == aaId && from.getPositionEnum().id >= Rank.MEMBER.id;
            boolean toMember = to != null && to.getAlliance_id() == aaId && to.getPositionEnum().id >= Rank.MEMBER.id;

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
                boolean joined = to.getAlliance_id() == aaId && to.getPositionEnum().id >= Rank.MEMBER.id && (
                            from == null ||
                            from.getAlliance_id() != aaId ||
                            from.getPositionEnum().id < Rank.MEMBER.id
                        );
                boolean left = !joined && from != null && from.getAlliance_id() == aaId && from.getPositionEnum().id >= Rank.MEMBER.id && (
                            to == null ||
                            to.getAlliance_id() != aaId ||
                            to.getPositionEnum().id < Rank.MEMBER.id
                        );

                if (joined) {
                    if ((from.getAlliance_id() == 0 || from.getAlliance_id() == aaId) && from.getAgeDays() <= RECRUITED_DAYS) {
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

            MembershipChangeReason initialState = summary.initialState.get(nationId);

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
        public Map<Integer, MembershipChangeReason> initialState = new Int2ObjectOpenHashMap();
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
