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
import link.locutus.discord.db.DBNationSnapshot;
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
            Map<Integer, DBNationSnapshot> now = dumper.getNations(day, true, true, f -> true, f -> true, null);
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

            GrowthReason reason;
            if (to == null) {
                if (DBNation.getById(from.getId()) == null) {
                    reason = GrowthReason.DELETED;
                } else {
                    reason = GrowthReason.VM_LEFT;
                }
            } else if (from == null) {
                if (to.getAgeDays() > RECRUITED_DAYS) {
                    reason = GrowthReason.VM_RETURNED;
                } else {
                    reason = GrowthReason.RECRUITED;
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
                        reason = GrowthReason.RECRUITED;
                    } else {
                        reason = GrowthReason.JOINED;
                    }
                } else if (left) {
                    reason = GrowthReason.LEFT;
                } else {
                    reason = GrowthReason.UNCHANGED;
                }
            }

            { // Set initial
                 if (reason == GrowthReason.UNCHANGED) {
                     if (fromMember) {
                         summary.initialState.putIfAbsent(nationId, GrowthReason.UNCHANGED);
                     } else {
                         summary.initialState.putIfAbsent(nationId, null);
                     }
                 } else {
                     summary.initialState.putIfAbsent(nationId, reason);
                 }
            }
            { // Set final
                if (reason == GrowthReason.UNCHANGED) {
                    if (toMember) {
                        summary.finalState.put(nationId, GrowthReason.UNCHANGED);
                    } else {
                        summary.finalState.put(nationId, null);
                    }
                } else {
                    summary.finalState.put(nationId, reason);
                }
            }

            GrowthReason initialState = summary.initialState.get(nationId);

            if (reason != null && reason != GrowthReason.UNCHANGED) {
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
            if (reason == GrowthReason.UNCHANGED && toMember) {
                for (GrowthAsset asset : link.locutus.discord.db.entities.metric.GrowthAsset.values()) {
                    int amtDiff = asset.get(to) - asset.get(from);

                    summary.growthCountByNation.computeIfAbsent(asset, k -> new Object2ObjectOpenHashMap<>())
                            .computeIfAbsent(reason, k -> new Int2IntOpenHashMap()).merge(nationId, amtDiff, Integer::sum);
                    double[] growthValue = summary.growthValueByNation.computeIfAbsent(asset, k -> new Object2ObjectOpenHashMap<>())
                            .computeIfAbsent(reason, k -> new Int2ObjectOpenHashMap<>()).computeIfAbsent(nationId, k -> ResourceType.getBuffer());
                    growthValue = asset.value(growthValue, from, to);
                }

//                double[] revenue = to.getRevenue();
//                summary.revenue = ResourceType.add(summary.revenue, revenue);
            }
        }
    }

    public static enum GrowthReason {
        RECRUITED, JOINED, LEFT, DELETED, VM_LEFT, VM_RETURNED,
        UNCHANGED,

        BOUGHT, SOLD
        ;

        public boolean previouslyMember() {
            return switch (this) {
                case LEFT, VM_LEFT, DELETED, UNCHANGED -> true;
                default -> false;
            };
        }

        public boolean afterwardsMember() {
            return switch (this) {
                case JOINED, RECRUITED, VM_RETURNED, UNCHANGED -> true;
                default -> false;
            };
        }
    }

    public static class AllianceGrowthSummary {
        public Map<Integer, GrowthReason> initialState = new Int2ObjectOpenHashMap();
        public Map<Integer, GrowthReason> finalState = new Int2ObjectOpenHashMap<>();

        public Map<Integer, Map<GrowthAsset, Integer>> lastCount = new Int2ObjectOpenHashMap<>();
        public Map<Integer, Map<GrowthAsset, double[]>> lastValue = new Int2ObjectOpenHashMap<>();

        public Map<GrowthAsset, Map<GrowthReason, Map<Integer, Integer>>> growthCountByNation = new Object2ObjectOpenHashMap<>();

        public Map<GrowthAsset, Map<GrowthReason, Map<Integer, double[]>>> growthValueByNation = new Object2ObjectOpenHashMap<>();
        public double[] revenue = ResourceType.getBuffer();

        public Map<GrowthReason, Integer> countByReason = new Object2ObjectOpenHashMap<>();
        public Map<GrowthReason, Set<Integer>> uniqueByReason = new Object2ObjectOpenHashMap<>();

        public double[] getSpending(Predicate<GrowthAsset> assetPredicate, Predicate<GrowthReason> reasonPredicate, boolean effective) {
            double[] total = ResourceType.getBuffer();
            // if effective, only do when finalState is member
            for (Map.Entry<GrowthAsset, Map<GrowthReason, Map<Integer, double[]>>> entry : growthValueByNation.entrySet()) {
                if (!assetPredicate.test(entry.getKey())) continue;
                for (Map.Entry<GrowthReason, Map<Integer, double[]>> entry2 : entry.getValue().entrySet()) {
                    GrowthReason reason = entry2.getKey();
                    if (!reasonPredicate.test(reason)) continue;
                    for (Map.Entry<Integer, double[]> entry3 : entry2.getValue().entrySet()) {
                        int nationId = entry3.getKey();
                        double[] amt = entry3.getValue();
                        if (effective) {
                            GrowthReason state = finalState.get(nationId);
                            if (state == null || !state.afterwardsMember()) continue;
                        }
                        total = ResourceType.add(total, amt);
                    }
                }
            }
            return total;
        }

        public double getSpendingValue(Predicate<GrowthAsset> assetPredicate, Predicate<GrowthReason> reasonPredicate, boolean effective) {
            return ResourceType.convertedTotal(getSpending(assetPredicate, reasonPredicate, effective));
        }

        public int getReasonCounts(Predicate<GrowthReason> reasonPredicate) {
            int total = 0;
            for (Map.Entry<GrowthReason, Set<Integer>> entry : uniqueByReason.entrySet()) {
                if (reasonPredicate.test(entry.getKey())) {
                    total += entry.getValue().size();
                }
            }
            return total;
        }

        public int getAssetCounts(Predicate<GrowthAsset> assetPredicate, Predicate<GrowthReason> reasonPredicate, boolean effective) {
            int total = 0;
            // if effective, only do when finalState is member
            for (Map.Entry<GrowthAsset, Map<GrowthReason, Map<Integer, Integer>>> entry : growthCountByNation.entrySet()) {
                if (!assetPredicate.test(entry.getKey())) continue;
                for (Map.Entry<GrowthReason, Map<Integer, Integer>> entry2 : entry.getValue().entrySet()) {
                    GrowthReason reason = entry2.getKey();
                    if (!reasonPredicate.test(reason)) continue;
                    for (Map.Entry<Integer, Integer> entry3 : entry2.getValue().entrySet()) {
                        int nationId = entry3.getKey();
                        int amt = entry3.getValue();
                        if (effective) {
                            GrowthReason state = finalState.get(nationId);
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
