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

    public GrowthSummary run() throws IOException, ParseException {
        DataDumpParser dumper = Locutus.imp().getDataDumper(true);
        if (dayEnd > TimeUtil.getDay()) {
            throw new IllegalArgumentException("Invalid future day specified for growth summary:" + dayEnd);
        }
        if (dayStart >= dayEnd) {
            throw new IllegalArgumentException("Invalid day range specified for growth summary: " + dayStart + " to " + dayEnd);
        }
        if (dayEnd - dayStart > MAX_DAYS) {
            throw new IllegalArgumentException("Invalid day range specified for growth summary: " + dayStart + " to " + dayEnd + " (max " + MAX_DAYS + ")");
        }

        QuadConsumer<Integer, Long, Map<Integer, DBNation>, Map<Integer, DBNation>> apply = new QuadConsumer<Integer, Long, Map<Integer, DBNation>, Map<Integer, DBNation>>() {
            @Override
            public void consume(Integer aaId, Long day, Map<Integer, DBNation> from, Map<Integer, DBNation> to) {
                AllianceGrowthSummary summary = byAlliance.computeIfAbsent(aaId, _ -> new AllianceGrowthSummary());
                Set<Integer> nationIds = new IntOpenHashSet();
                for (Map.Entry<Integer, DBNation> entry : from.entrySet()) {
                    if (entry.getValue().getAlliance_id() == aaId && entry.getValue().getPositionEnum().id >= Rank.MEMBER.id) {
                        nationIds.add(entry.getKey());
                    }
                }
                for (Map.Entry<Integer, DBNation> entry : to.entrySet()) {
                    if (entry.getValue().getAlliance_id() == aaId && entry.getValue().getPositionEnum().id >= Rank.MEMBER.id) {
                        nationIds.add(entry.getKey());
                    }
                }
                updateDay(day, summary, aaId, from, to, nationIds);
            }
        };

        Map<Integer, DBNationSnapshot> last = null;
        for (long day = dayStart; day <= dayEnd; day++) {
            Map<Integer, DBNationSnapshot> now = dumper.getNations(day);
            System.out.println("Get nations " + day + " | " + now.size());
            if (last == null) {
                last = now;
                continue;
            }

            for (int aaId : allowedAlliances) {
                apply.consume(aaId, day, (Map) last, (Map) now);
            }

            last = now;
        }
        if (dayEnd == TimeUtil.getDay()) {
            Map<Integer, DBNation> now = Locutus.imp().getNationDB().getNationsById();
            for (int aaId : allowedAlliances) {
                apply.consume(aaId, dayEnd, (Map) last, now);
            }
        }
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
                if (DBNation.getById(from.getId()) == null) {
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