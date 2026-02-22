package link.locutus.discord.db.entities.metric;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
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
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public class GrowthSummary {
    private static final GrowthAsset[] GROWTH_ASSETS = GrowthAsset.values();

    private final Map<Integer, AllianceGrowthSummary> byAlliance = new Int2ObjectOpenHashMap<>();
    public static int RECRUITED_DAYS = 7;
    public static int MAX_DAYS = 365;
    private final Set<Integer> allowedAlliances;
    private final long dayStart;
    private final long dayEnd;

    public GrowthSummary(Set<DBAlliance> alliances, long dayStart, long dayEnd) throws IOException, ParseException {
        this.allowedAlliances = new IntOpenHashSet();
        for (DBAlliance alliance : alliances)
            allowedAlliances.add(alliance.getId());

        this.dayStart = dayStart;
        this.dayEnd = dayEnd;
    }

    private Map<Integer, Set<Integer>> allianceMembership(Map<Integer, DBNation> nations,
            Set<Integer> allowedAlliances) {
        Map<Integer, Set<Integer>> allianceMembership = new Int2ObjectOpenHashMap<>();
        for (Map.Entry<Integer, DBNation> entry : nations.entrySet()) {
            DBNation nation = entry.getValue();
            int aaId = nation.getAlliance_id();
            if (aaId == 0 || !allowedAlliances.contains(aaId))
                continue;
            if (nation.getPositionEnum().id >= Rank.MEMBER.id) {
                allianceMembership.computeIfAbsent(aaId, f -> new IntOpenHashSet()).add(entry.getKey());
            }
        }
        return allianceMembership;
    }

    private void applyAlliance(Set<Integer> allowedAlliances, Map<Integer, DBNation> from,
            Map<Integer, Set<Integer>> fromNatByAA, Map<Integer, DBNation> to, Map<Integer, Set<Integer>> toNatByAA) {
        for (int aaId : allowedAlliances) {
            apply(aaId, from, fromNatByAA, to, toNatByAA);
        }
    }

    public void apply(int allianceId, Map<Integer, DBNation> from, Map<Integer, Set<Integer>> fromNatByAA,
            Map<Integer, DBNation> to, Map<Integer, Set<Integer>> toNatByAA) {
        AllianceGrowthSummary summary = byAlliance.computeIfAbsent(allianceId, _ -> new AllianceGrowthSummary());

        Set<Integer> memberBefore = fromNatByAA.getOrDefault(allianceId, Set.of());
        Set<Integer> memberAfter = toNatByAA.getOrDefault(allianceId, Set.of());
        if (memberBefore.isEmpty() && memberAfter.isEmpty()) {
            return;
        }
        updateDay(summary, from, to, memberBefore, memberAfter);
    }

    public GrowthSummary run() throws IOException, ParseException {
        DataDumpParser dumper = Locutus.imp().getDataDumper(true).load();
        if (dayEnd > TimeUtil.getDay()) {
            throw new IllegalArgumentException("Invalid future day specified for growth summary:" + dayEnd);
        }
        if (dayStart >= dayEnd) {
            throw new IllegalArgumentException(
                    "Invalid day range specified for growth summary: " + dayStart + " to " + dayEnd);
        }
        if (dayEnd - dayStart > MAX_DAYS) {
            throw new IllegalArgumentException("Invalid day range specified for growth summary: " + dayStart + " to "
                    + dayEnd + " (max " + MAX_DAYS + ")");
        }

        long start = System.nanoTime();
        int dayEndWithCurrent = dayEnd == TimeUtil.getDay() ? (int) dayEnd + 1 : (int) dayEnd;

        ThrowingFunction<Long, Map<Integer, DBNation>> fetchNations = day -> {
            if (day == dayEnd + 1) {
                return Locutus.imp().getNationDB().getNationsById();
            }
            return (Map) dumper.getNations(day, true);
        };

        Map<Integer, DBNation> last = fetchNations.apply(dayStart);
        Map<Integer, Set<Integer>> lastMembership = allianceMembership(last, allowedAlliances);

        for (long day = dayStart + 1; day <= dayEndWithCurrent; day++) {
            Map<Integer, DBNation> now = fetchNations.apply(day);
            Map<Integer, Set<Integer>> nowMembership = allianceMembership(now, allowedAlliances);

            applyAlliance(allowedAlliances, last, lastMembership, now, nowMembership);

            last = now;
            lastMembership = nowMembership;
        }

        long diff = System.nanoTime() - start;

        long diffMs = diff / 1000000;
        Logg.info("GrowthSummary task: Consume + Get: " + diffMs + "ms");

        return this;
    }

    public Map<Integer, AllianceGrowthSummary> getSummaries() {
        return byAlliance;
    }

    public void updateDay(AllianceGrowthSummary summary, Map<Integer, DBNation> nationsFrom,
            Map<Integer, DBNation> nationsTo, Set<Integer> memberBefore,
            Set<Integer> memberAfter) {
        for (int nationId : memberBefore) {
            processNation(summary, nationsFrom, nationsTo, memberBefore, memberAfter, nationId);
        }
        for (int nationId : memberAfter) {
            if (memberBefore.contains(nationId)) {
                continue;
            }
            processNation(summary, nationsFrom, nationsTo, memberBefore, memberAfter, nationId);
        }
    }

    private void processNation(AllianceGrowthSummary summary, Map<Integer, DBNation> nationsFrom,
            Map<Integer, DBNation> nationsTo, Set<Integer> memberBefore, Set<Integer> memberAfter, int nationId) {
        DBNation from = nationsFrom.get(nationId);
        DBNation to = nationsTo.get(nationId);
        if (from == null && to == null) {
            return;
        }

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
            boolean left = !joined && fromMember && !toMember;

            if (joined) {
                if (from.getAgeDays() <= RECRUITED_DAYS) {
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

        if (reason != MembershipChangeReason.UNCHANGED) {
            summary.countByReason.compute(reason, (k, v) -> v == null ? 1 : v + 1);
            summary.uniqueByReason.computeIfAbsent(reason, k -> new IntOpenHashSet()).add(nationId);

            for (GrowthAsset asset : GROWTH_ASSETS) {
                // For membership-change reasons, track the asset holdings at the transition
                // boundary.
                // This keeps reason buckets intuitive (assets brought in vs assets removed).
                int amtDiff;
                double[] valueDiff = ResourceType.getBuffer();
                if (reason.afterwardsMember() && !reason.previouslyMember()) {
                    amtDiff = to == null ? 0 : asset.getAndValue(to, valueDiff);
                } else if (reason.previouslyMember() && !reason.afterwardsMember()) {
                    amtDiff = from == null ? 0 : asset.getAndValue(from, valueDiff);
                } else {
                    throw new IllegalArgumentException("Invalid state: " + reason);
                }

                Int2IntOpenHashMap countByNation = summary.getOrCreateCountMap(asset, reason);
                countByNation.merge(nationId, amtDiff, Integer::sum);

                Int2ObjectOpenHashMap<double[]> valueByNation = summary.getOrCreateValueMap(asset, reason);
                double[] growthValue = valueByNation.computeIfAbsent(nationId, k -> ResourceType.getBuffer());
                ResourceType.add(growthValue, valueDiff);
            }
        }
        if (reason == MembershipChangeReason.UNCHANGED && toMember) {
            for (GrowthAsset asset : GROWTH_ASSETS) {
                double[] valueDiff = ResourceType.getBuffer();
                int amtDiff = asset.getDeltaAndValue(from, to, valueDiff);

                Int2IntOpenHashMap countByNation = summary.getOrCreateCountMap(asset, reason);
                countByNation.merge(nationId, amtDiff, Integer::sum);

                Int2ObjectOpenHashMap<double[]> valueByNation = summary.getOrCreateValueMap(asset, reason);
                double[] growthValue = valueByNation.computeIfAbsent(nationId, k -> ResourceType.getBuffer());
                ResourceType.add(growthValue, valueDiff);
            }
        }
    }

    public static class AllianceGrowthSummary {
        public Map<Integer, MembershipChangeReason> initialState = new Int2ObjectOpenHashMap<>();
        public Map<Integer, MembershipChangeReason> finalState = new Int2ObjectOpenHashMap<>();

        private final Int2IntOpenHashMap[][] growthCountByNation;
        private final Int2ObjectOpenHashMap<double[]>[][] growthValueByNation;

        public Map<MembershipChangeReason, Integer> countByReason = new Object2ObjectOpenHashMap<>();
        public Map<MembershipChangeReason, Set<Integer>> uniqueByReason = new Object2ObjectOpenHashMap<>();

        @SuppressWarnings("unchecked")
        public AllianceGrowthSummary() {
            int assets = GROWTH_ASSETS.length;
            int reasons = MembershipChangeReason.values().length;
            this.growthCountByNation = new Int2IntOpenHashMap[assets][reasons];
            this.growthValueByNation = new Int2ObjectOpenHashMap[assets][reasons];
        }

        Int2IntOpenHashMap getOrCreateCountMap(GrowthAsset asset, MembershipChangeReason reason) {
            Int2IntOpenHashMap map = growthCountByNation[asset.ordinal()][reason.ordinal()];
            if (map == null) {
                map = new Int2IntOpenHashMap();
                growthCountByNation[asset.ordinal()][reason.ordinal()] = map;
            }
            return map;
        }

        Int2ObjectOpenHashMap<double[]> getOrCreateValueMap(GrowthAsset asset, MembershipChangeReason reason) {
            Int2ObjectOpenHashMap<double[]> map = growthValueByNation[asset.ordinal()][reason.ordinal()];
            if (map == null) {
                map = new Int2ObjectOpenHashMap<>();
                growthValueByNation[asset.ordinal()][reason.ordinal()] = map;
            }
            return map;
        }

        /**
         * Aggregates resource deltas for the selected assets and membership-change
         * reasons.
         *
         * UNCHANGED reason entries are day-over-day deltas while a nation remained a
         * member.
         * Non-UNCHANGED reason entries use boundary holdings at membership transitions:
         * join/recruit/return adds the nation snapshot at entry, leave/delete/vm-left
         * adds
         * the nation snapshot at exit.
         *
         * If {@code effective} is true, only nations whose final state is still a
         * member are included.
         */
        public double[] getSpending(Predicate<GrowthAsset> assetPredicate,
                Predicate<MembershipChangeReason> reasonPredicate, boolean effective) {
            double[] total = ResourceType.getBuffer();
            for (GrowthAsset asset : GROWTH_ASSETS) {
                if (!assetPredicate.test(asset))
                    continue;
                for (MembershipChangeReason reason : MembershipChangeReason.values()) {
                    if (!reasonPredicate.test(reason))
                        continue;
                    Int2ObjectOpenHashMap<double[]> byNation = growthValueByNation[asset.ordinal()][reason.ordinal()];
                    if (byNation == null) {
                        continue;
                    }
                    for (Int2ObjectMap.Entry<double[]> entry3 : byNation.int2ObjectEntrySet()) {
                        int nationId = entry3.getKey();
                        double[] amt = entry3.getValue();
                        if (effective) {
                            MembershipChangeReason state = finalState.get(nationId);
                            if (state == null || !state.afterwardsMember())
                                continue;
                        }
                        total = ResourceType.add(total, amt);
                    }
                }
            }
            return total;
        }

        public double getSpendingValue(Predicate<GrowthAsset> assetPredicate,
                Predicate<MembershipChangeReason> reasonPredicate, boolean effective) {
            return ResourceType.convertedTotal(getSpending(assetPredicate, reasonPredicate, effective));
        }

        /**
         * Counts membership-change events for matching reasons.
         *
         * A nation can contribute multiple events over the time window.
         */
        public int getReasonCounts(Predicate<MembershipChangeReason> reasonPredicate) {
            int total = 0;
            for (Map.Entry<MembershipChangeReason, Integer> entry : countByReason.entrySet()) {
                if (reasonPredicate.test(entry.getKey())) {
                    total += entry.getValue();
                }
            }
            return total;
        }

        /**
         * Counts unique nations that had at least one matching reason in the time
         * window.
         */
        public int getUniqueReasonNationCounts(Predicate<MembershipChangeReason> reasonPredicate) {
            int total = 0;
            for (Map.Entry<MembershipChangeReason, Set<Integer>> entry : uniqueByReason.entrySet()) {
                if (reasonPredicate.test(entry.getKey())) {
                    total += entry.getValue().size();
                }
            }
            return total;
        }

        /**
         * Aggregates asset count deltas for the selected assets and membership-change
         * reasons.
         *
         * For non-UNCHANGED reasons, uses boundary holdings at join/leave transitions.
         *
         * If {@code effective} is true, only nations whose final state is still a
         * member are included.
         */
        public int getAssetCounts(Predicate<GrowthAsset> assetPredicate,
                Predicate<MembershipChangeReason> reasonPredicate, boolean effective) {
            int total = 0;
            for (GrowthAsset asset : GROWTH_ASSETS) {
                if (!assetPredicate.test(asset))
                    continue;
                for (MembershipChangeReason reason : MembershipChangeReason.values()) {
                    if (!reasonPredicate.test(reason))
                        continue;
                    Int2IntOpenHashMap byNation = growthCountByNation[asset.ordinal()][reason.ordinal()];
                    if (byNation == null) {
                        continue;
                    }
                    for (it.unimi.dsi.fastutil.ints.Int2IntMap.Entry entry3 : byNation.int2IntEntrySet()) {
                        int nationId = entry3.getKey();
                        int amt = entry3.getIntValue();
                        if (effective) {
                            MembershipChangeReason state = finalState.get(nationId);
                            if (state == null || !state.afterwardsMember())
                                continue;
                        }
                        total += amt;
                    }
                }
            }
            return total;
        }
    }
}
