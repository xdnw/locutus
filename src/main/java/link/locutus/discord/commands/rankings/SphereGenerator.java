package link.locutus.discord.commands.rankings;

import it.unimi.dsi.fastutil.ints.Int2DoubleMap;
import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.TreatyType;
import link.locutus.discord.db.NationDB;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.Treaty;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.math.CIEDE2000;
import link.locutus.discord.util.scheduler.KeyValue;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SphereGenerator {
    public static boolean REMOVE_INACTIVE = true;
    public static boolean REMOVE_VM = true;
    public static boolean REMOVE_APPLICANTS = true;
    public static Double METRIC_POWER = null;

    private static final int DEFAULT_MAJOR_ALLIANCE_COUNT = 12;
    private static final int DEFAULT_MIN_SHARED_ALLIES_TO_MERGE_MAJORS = 1;
    private static final double DEFAULT_INCOMING_TIE_MULTIPLIER = 0.75d;
    private static final double DEFAULT_PROTECTORATE_INCOMING_MULTIPLIER = 0.2d;
    private static final double DEFAULT_OFFSHORE_INCOMING_MULTIPLIER = 0d;
    private static final double DEFAULT_MUTUAL_TIE_BONUS = 1.5d;
    private static final double DEFAULT_SHARED_MUTUAL_SPHERE_BONUS = 0.75d;

    private static final Object CACHE_LOCK = new Object();
    private static final Int2ObjectOpenHashMap<CachedSnapshot> CACHED_BY_TOP_X = new Int2ObjectOpenHashMap<>();

    private final Computation computation;

    public SphereGenerator(int topX) {
        this(getCachedComputation(topX));
    }

    public SphereGenerator(Collection<DBAlliance> alliances) {
        this(compute(sortByMetric(alliances, buildMetricMap(alliances))));
    }

    private SphereGenerator(Computation computation) {
        this.computation = computation;
    }

    public static SphereGenerator cached() {
        return new SphereGenerator(getCachedComputation(0));
    }

    public static SphereGenerator cached(int topX) {
        return new SphereGenerator(getCachedComputation(topX));
    }

    public List<DBAlliance> getSeedAlliances() {
        return toAllianceList(computation.seedAllianceIds, null);
    }

    public double getMetric(int sphereId) {
        return computation.sphereMetric.get(sphereId);
    }

    public double getAllianceMetric(int allianceId) {
        return computation.metricByAlliance.get(allianceId);
    }

    public Map.Entry<Integer, List<DBAlliance>> getSphere(DBAlliance alliance) {
        if (alliance == null) {
            return null;
        }
        int sphereId = computation.sphereByAlliance.getOrDefault(alliance.getAlliance_id(), 0);
        if (sphereId == 0) {
            return null;
        }
        return new KeyValue<>(sphereId, getAlliances(sphereId));
    }

    public List<DBAlliance> getSphereAllianceList(int allianceId, Map<Integer, DBAlliance> aaCache) {
        int sphereId = computation.sphereByAlliance.getOrDefault(allianceId, 0);
        if (sphereId == 0) {
            return Collections.emptyList();
        }
        IntArrayList memberIds = computation.allianceIdsBySphere.get(sphereId);
        return memberIds == null ? Collections.emptyList() : toAllianceList(memberIds, aaCache);
    }

    public Set<DBAlliance> getSphereAllianceSet(int allianceId, Map<Integer, DBAlliance> aaCache) {
        return new ObjectLinkedOpenHashSet<>(getSphereAllianceList(allianceId, aaCache));
    }

    public DBAlliance getAlliance(int allianceId) {
        return DBAlliance.get(allianceId);
    }

    public Map<Integer, DBAlliance> getAlliancesMap() {
        Int2ObjectOpenHashMap<DBAlliance> result = new Int2ObjectOpenHashMap<>();
        for (int i = 0; i < computation.sortedAllianceIds.size(); i++) {
            int allianceId = computation.sortedAllianceIds.getInt(i);
            DBAlliance alliance = DBAlliance.get(allianceId);
            if (alliance != null) {
                result.put(allianceId, alliance);
            }
        }
        return result;
    }

    public Set<DBAlliance> getAlliances() {
        return new ObjectLinkedOpenHashSet<>(toAllianceList(computation.sortedAllianceIds, null));
    }

    public String getSphereName(int sphereId) {
        return PW.getSphereName(sphereId);
    }

    public Color getColor(int sphereId) {
        return computation.sphereColors.get(sphereId);
    }

    public List<DBAlliance> getAlliances(int sphereId) {
        IntArrayList memberIds = computation.allianceIdsBySphere.get(sphereId);
        return memberIds == null ? Collections.emptyList() : toAllianceList(memberIds, null);
    }

    public List<Integer> getSpheres() {
        ArrayList<Integer> result = new ArrayList<>(computation.spheresRanked.size());
        for (int i = 0; i < computation.spheresRanked.size(); i++) {
            result.add(computation.spheresRanked.getInt(i));
        }
        return result;
    }

    public String describeSpheres(int maxSpheres, int ignoredMaxMembersPerSphere) {
        StringBuilder body = new StringBuilder();
        int shown = 0;
        for (int i = 0; i < computation.spheresRanked.size(); i++) {
            int sphereId = computation.spheresRanked.getInt(i);
            if (maxSpheres > 0 && shown >= maxSpheres) {
                break;
            }
            shown++;
            IntArrayList memberIds = computation.allianceIdsBySphere.getOrDefault(sphereId, IntArrayList.of());
            body.append("#")
                    .append(shown)
                    .append(' ')
                    .append(PW.getName(sphereId, true))
                    .append(" [")
                    .append(sphereId)
                    .append("] | sphereMetric=")
                    .append(Math.round(computation.sphereMetric.get(sphereId)))
                    .append(" | alliances=")
                    .append(memberIds.size())
                    .append('\n');
            for (int j = 0; j < memberIds.size(); j++) {
                int allianceId = memberIds.getInt(j);
                DBAlliance alliance = DBAlliance.get(allianceId);
                String allianceName = alliance == null ? PW.getName(allianceId, true) : alliance.getName();
                body.append("- ")
                        .append(allianceName)
                        .append(" [")
                        .append(allianceId)
                        .append("] metric=")
                        .append(Math.round(computation.metricByAlliance.get(allianceId)))
                        .append('\n');
            }
            if (i + 1 < computation.spheresRanked.size() && (maxSpheres <= 0 || shown < maxSpheres)) {
                body.append('\n');
            }
        }
        return body.toString().trim();
    }

    private static Computation getCachedComputation(int topX) {
        NationDB nationDb = Locutus.imp().getNationDB();
        long treatyVersion = nationDb.getTreatyVersion();
        synchronized (CACHE_LOCK) {
            CachedSnapshot cached = CACHED_BY_TOP_X.get(topX);
            if (cached != null && cached.treatyVersion == treatyVersion) {
                return cached.computation;
            }
            Computation computation = compute(selectTopByMetric(topX));
            CACHED_BY_TOP_X.put(topX, new CachedSnapshot(treatyVersion, computation));
            return computation;
        }
    }

    private static Computation compute(ObjectArrayList<DBAlliance> sortedSeeds) {
        Int2DoubleOpenHashMap metricByAlliance = buildMetricMap(sortedSeeds);
        TreatyGraph graph = TreatyGraph.build(sortedSeeds, metricByAlliance);
        Int2ObjectOpenHashMap<SphereSeed> sphereSeeds = seedSpheres(graph);
        IntOpenHashSet assigned = new IntOpenHashSet();
        for (SphereSeed seed : sphereSeeds.values()) {
            assigned.addAll(seed.memberIds);
        }

        for (int i = 0; i < graph.sortedAllianceIds.size(); i++) {
            int allianceId = graph.sortedAllianceIds.getInt(i);
            if (assigned.contains(allianceId)) {
                continue;
            }
            SphereChoice choice = chooseSphere(allianceId, sphereSeeds, graph);
            if (choice == null) {
                sphereSeeds.put(allianceId, SphereSeed.singleton(allianceId, graph.metricByAlliance));
            } else {
                choice.seed.addMember(allianceId, graph.metricByAlliance);
            }
            assigned.add(allianceId);
        }

        return finalizeComputation(graph, sphereSeeds);
    }

    private static Computation finalizeComputation(TreatyGraph graph,
                                                   Int2ObjectOpenHashMap<SphereSeed> sphereSeeds) {
        Int2ObjectOpenHashMap<IntArrayList> allianceIdsBySphere = new Int2ObjectOpenHashMap<>();
        Int2DoubleOpenHashMap sphereMetric = new Int2DoubleOpenHashMap();
        sphereMetric.defaultReturnValue(0d);
        Int2ObjectOpenHashMap<Color> sphereColors = new Int2ObjectOpenHashMap<>();
        Int2IntOpenHashMap sphereByAlliance = new Int2IntOpenHashMap();
        sphereByAlliance.defaultReturnValue(0);

        for (SphereSeed seed : sphereSeeds.values()) {
            if (seed.memberIds.isEmpty()) {
                continue;
            }
            seed.sortMembers(graph.metricByAlliance);
            int sphereId = seed.leaderId;
            IntArrayList memberIds = new IntArrayList(seed.memberIds);
            allianceIdsBySphere.put(sphereId, memberIds);
            sphereMetric.put(sphereId, seed.metricTotal);
            sphereColors.put(sphereId, CIEDE2000.randomColor(sphereId, DiscordUtil.BACKGROUND_COLOR, sphereColors.values()));
            for (int i = 0; i < memberIds.size(); i++) {
                sphereByAlliance.put(memberIds.getInt(i), sphereId);
            }
        }

        IntArrayList spheresRanked = new IntArrayList(allianceIdsBySphere.keySet());
        spheresRanked.sort((left, right) -> compareSphereIds(right, left, sphereMetric, graph.metricByAlliance));

        return new Computation(
                new IntArrayList(graph.sortedAllianceIds),
                allianceIdsBySphere,
                spheresRanked,
                sphereByAlliance,
                sphereMetric,
                graph.metricByAlliance,
                sphereColors,
                collectSeedAllianceIds(sphereSeeds, graph.metricByAlliance)
        );
    }

    private static IntArrayList collectSeedAllianceIds(Int2ObjectOpenHashMap<SphereSeed> sphereSeeds,
                                                       Int2DoubleOpenHashMap metricByAlliance) {
        IntArrayList result = new IntArrayList(sphereSeeds.size());
        for (SphereSeed seed : sphereSeeds.values()) {
            seed.sortMembers(metricByAlliance);
            result.add(seed.leaderId);
        }
        result.sort((left, right) -> compareAllianceIds(right, left, metricByAlliance));
        return result;
    }

    private static Int2ObjectOpenHashMap<SphereSeed> seedSpheres(TreatyGraph graph) {
        IntArrayList majors = new IntArrayList();
        int cap = Math.min(DEFAULT_MAJOR_ALLIANCE_COUNT, graph.sortedAllianceIds.size());
        for (int i = 0; i < cap; i++) {
            majors.add(graph.sortedAllianceIds.getInt(i));
        }
        if (majors.isEmpty()) {
            majors.addAll(graph.sortedAllianceIds);
        }

        DisjointSet groups = new DisjointSet();
        for (int i = 0; i < majors.size(); i++) {
            groups.add(majors.getInt(i));
        }
        for (int i = 0; i < majors.size(); i++) {
            int leftId = majors.getInt(i);
            for (int j = i + 1; j < majors.size(); j++) {
                int rightId = majors.getInt(j);
                if (shouldMergeMajors(leftId, rightId, graph)) {
                    groups.union(leftId, rightId);
                }
            }
        }

        Int2ObjectOpenHashMap<SphereSeed> grouped = new Int2ObjectOpenHashMap<>();
        for (int i = 0; i < majors.size(); i++) {
            int majorId = majors.getInt(i);
            int root = groups.find(majorId);
            grouped.computeIfAbsent(root, ignored -> new SphereSeed()).addMember(majorId, graph.metricByAlliance);
        }

        Int2ObjectOpenHashMap<SphereSeed> result = new Int2ObjectOpenHashMap<>();
        for (SphereSeed seed : grouped.values()) {
            seed.sortMembers(graph.metricByAlliance);
            result.put(seed.leaderId, seed);
        }
        return result;
    }

    private static boolean shouldMergeMajors(int leftId, int rightId, TreatyGraph graph) {
        DirectedTie leftToRight = graph.tie(leftId, rightId);
        DirectedTie rightToLeft = graph.tie(rightId, leftId);
        TieLevel level = TieLevel.best(leftToRight.level, rightToLeft.level);
        if (level == TieLevel.NONE) {
            return false;
        }
        if (level == TieLevel.SAME_ALLIANCE) {
            return true;
        }
        if (level == TieLevel.PROTECTORATE || level == TieLevel.OFFSHORE) {
            return false;
        }
        return countSharedMutualNeighbors(leftId, rightId, graph) >= DEFAULT_MIN_SHARED_ALLIES_TO_MERGE_MAJORS;
    }

    private static int countSharedMutualNeighbors(int leftId, int rightId, TreatyGraph graph) {
        IntSet left = graph.mutualNeighbors.get(leftId);
        IntSet right = graph.mutualNeighbors.get(rightId);
        if (left == null || right == null || left.isEmpty() || right.isEmpty()) {
            return 0;
        }
        IntSet smaller = left.size() <= right.size() ? left : right;
        IntSet larger = smaller == left ? right : left;
        int count = 0;
        for (int neighborId : smaller) {
            if (neighborId != leftId && neighborId != rightId && larger.contains(neighborId)) {
                count++;
            }
        }
        return count;
    }

    private static SphereChoice chooseSphere(int allianceId,
                                             Int2ObjectOpenHashMap<SphereSeed> sphereSeeds,
                                             TreatyGraph graph) {
        SphereChoice direct = chooseDirectSphere(allianceId, sphereSeeds, graph);
        if (direct != null) {
            return direct;
        }

        SphereChoice best = null;
        for (Int2ObjectMap.Entry<SphereSeed> entry : sphereSeeds.int2ObjectEntrySet()) {
            int sphereId = entry.getIntKey();
            SphereSeed seed = entry.getValue();
            double total = 0d;
            double bestTie = 0d;
            int mutualCount = 0;
            for (int i = 0; i < seed.memberIds.size(); i++) {
                int memberId = seed.memberIds.getInt(i);
                DirectedTie out = graph.tie(allianceId, memberId);
                DirectedTie in = graph.tie(memberId, allianceId);
                double pair = out.weight + incomingContribution(in);
                if (out.level.isMutual() && in.level.isMutual() && out.level != TieLevel.NONE && in.level != TieLevel.NONE) {
                    pair += DEFAULT_MUTUAL_TIE_BONUS;
                    mutualCount++;
                }
                total += pair;
                bestTie = Math.max(bestTie, Math.max(out.weight, in.weight));
            }
            if (total <= 0d) {
                continue;
            }
            double score = total + mutualCount * DEFAULT_SHARED_MUTUAL_SPHERE_BONUS;
            SphereChoice choice = new SphereChoice(seed, score, bestTie, mutualCount, graph.metricByAlliance.get(sphereId), seed.metricTotal);
            if (best == null || choice.compareTo(best) > 0) {
                best = choice;
            }
        }
        return best;
    }

    private static SphereChoice chooseDirectSphere(int allianceId,
                                                   Int2ObjectOpenHashMap<SphereSeed> sphereSeeds,
                                                   TreatyGraph graph) {
        SphereChoice best = null;
        TieLevel bestLevel = TieLevel.NONE;
        double bestWeight = 0d;
        double bestMemberMetric = 0d;
        for (SphereSeed seed : sphereSeeds.values()) {
            for (int i = 0; i < seed.memberIds.size(); i++) {
                int memberId = seed.memberIds.getInt(i);
                DirectedTie tie = graph.tie(allianceId, memberId);
                if (!tie.level.isDirectBinding()) {
                    continue;
                }
                double memberMetric = graph.metricByAlliance.get(memberId);
                SphereChoice choice = new SphereChoice(seed, tie.weight, tie.weight, 0, graph.metricByAlliance.get(seed.leaderId), seed.metricTotal);
                if (best == null
                        || tie.level.weight > bestLevel.weight
                        || (tie.level == bestLevel && tie.weight > bestWeight)
                        || (tie.level == bestLevel && tie.weight == bestWeight && memberMetric > bestMemberMetric)) {
                    best = choice;
                    bestLevel = tie.level;
                    bestWeight = tie.weight;
                    bestMemberMetric = memberMetric;
                }
            }
        }
        return best;
    }

    private static double incomingContribution(DirectedTie tie) {
        return switch (tie.level) {
            case NONE -> 0d;
            case OPTIONAL, MANDATORY, SAME_ALLIANCE -> tie.weight * DEFAULT_INCOMING_TIE_MULTIPLIER;
            case PROTECTORATE -> tie.weight * DEFAULT_PROTECTORATE_INCOMING_MULTIPLIER;
            case OFFSHORE -> tie.weight * DEFAULT_OFFSHORE_INCOMING_MULTIPLIER;
        };
    }

    private static ObjectArrayList<DBAlliance> selectTopByMetric(int topX) {
        NationDB nationDb = Locutus.imp().getNationDB();
        ObjectArrayList<DBAlliance> alliances = new ObjectArrayList<>(nationDb.getAlliances());
        Int2DoubleOpenHashMap metricByAlliance = buildMetricMap(alliances);
        alliances.sort(metricComparator(metricByAlliance));
        if (topX > 0 && alliances.size() > topX) {
            return new ObjectArrayList<>(alliances.subList(0, topX));
        }
        return alliances;
    }

    private static Int2DoubleOpenHashMap buildMetricMap(Collection<DBAlliance> alliances) {
        Int2DoubleOpenHashMap metricByAlliance = new Int2DoubleOpenHashMap();
        metricByAlliance.defaultReturnValue(0d);
        for (DBAlliance alliance : alliances) {
            if (alliance != null) {
                metricByAlliance.put(alliance.getAlliance_id(), metric(alliance));
            }
        }
        return metricByAlliance;
    }

    private static ObjectArrayList<DBAlliance> sortByMetric(Collection<DBAlliance> alliances,
                                                            Int2DoubleOpenHashMap metricByAlliance) {
        ObjectArrayList<DBAlliance> sorted = new ObjectArrayList<>(new ObjectLinkedOpenHashSet<>(alliances));
        sorted.sort(metricComparator(metricByAlliance));
        return sorted;
    }

    private static Comparator<DBAlliance> metricComparator(Int2DoubleOpenHashMap metricByAlliance) {
        return (left, right) -> compareAllianceIds(right.getAlliance_id(), left.getAlliance_id(), metricByAlliance);
    }

    private static int compareAllianceIds(int leftId, int rightId, Int2DoubleOpenHashMap metricByAlliance) {
        int metricCompare = Double.compare(metricByAlliance.get(leftId), metricByAlliance.get(rightId));
        if (metricCompare != 0) {
            return metricCompare;
        }
        return Integer.compare(rightId, leftId);
    }

    private static int compareSphereIds(int leftSphereId,
                                        int rightSphereId,
                                        Int2DoubleOpenHashMap sphereMetric,
                                        Int2DoubleOpenHashMap allianceMetric) {
        int metricCompare = Double.compare(sphereMetric.get(leftSphereId), sphereMetric.get(rightSphereId));
        if (metricCompare != 0) {
            return metricCompare;
        }
        return compareAllianceIds(leftSphereId, rightSphereId, allianceMetric);
    }

    private static double metric(DBAlliance alliance) {
        return alliance.exponentialCityStrength(METRIC_POWER, REMOVE_VM, REMOVE_INACTIVE, REMOVE_APPLICANTS);
    }

    private static List<DBAlliance> toAllianceList(IntArrayList allianceIds, Map<Integer, DBAlliance> aaCache) {
        ArrayList<DBAlliance> result = new ArrayList<>(allianceIds.size());
        for (int i = 0; i < allianceIds.size(); i++) {
            int allianceId = allianceIds.getInt(i);
            DBAlliance alliance;
            if (aaCache == null) {
                alliance = DBAlliance.get(allianceId);
            } else {
                alliance = aaCache.computeIfAbsent(allianceId, DBAlliance::getOrCreate);
            }
            if (alliance != null) {
                result.add(alliance);
            }
        }
        return result;
    }

    private record CachedSnapshot(long treatyVersion, Computation computation) {
    }

    private record Computation(
            IntArrayList sortedAllianceIds,
            Int2ObjectOpenHashMap<IntArrayList> allianceIdsBySphere,
            IntArrayList spheresRanked,
            Int2IntOpenHashMap sphereByAlliance,
            Int2DoubleOpenHashMap sphereMetric,
            Int2DoubleOpenHashMap metricByAlliance,
            Int2ObjectOpenHashMap<Color> sphereColors,
            IntArrayList seedAllianceIds
    ) {
    }

    private record SphereChoice(
            SphereSeed seed,
            double score,
            double strongestTie,
            int mutualCount,
            double leaderMetric,
            double sphereMetric
    ) implements Comparable<SphereChoice> {
        @Override
        public int compareTo(SphereChoice other) {
            int scoreCompare = Double.compare(score, other.score);
            if (scoreCompare != 0) {
                return scoreCompare;
            }
            int tieCompare = Double.compare(strongestTie, other.strongestTie);
            if (tieCompare != 0) {
                return tieCompare;
            }
            int mutualCompare = Integer.compare(mutualCount, other.mutualCount);
            if (mutualCompare != 0) {
                return mutualCompare;
            }
            int leaderCompare = Double.compare(leaderMetric, other.leaderMetric);
            if (leaderCompare != 0) {
                return leaderCompare;
            }
            return Double.compare(sphereMetric, other.sphereMetric);
        }
    }

    private static final class SphereSeed {
        private final IntArrayList memberIds = new IntArrayList();
        private int leaderId;
        private double metricTotal;

        private void addMember(int allianceId, Int2DoubleMap metricByAlliance) {
            memberIds.add(allianceId);
            metricTotal += metricByAlliance.get(allianceId);
            if (leaderId == 0 || compareAllianceIds(allianceId, leaderId, (Int2DoubleOpenHashMap) metricByAlliance) > 0) {
                leaderId = allianceId;
            }
        }

        private void sortMembers(Int2DoubleOpenHashMap metricByAlliance) {
            memberIds.sort((left, right) -> compareAllianceIds(right, left, metricByAlliance));
            if (!memberIds.isEmpty()) {
                leaderId = memberIds.getInt(0);
            }
        }

        private static SphereSeed singleton(int allianceId, Int2DoubleMap metricByAlliance) {
            SphereSeed seed = new SphereSeed();
            seed.addMember(allianceId, metricByAlliance);
            return seed;
        }
    }

    private enum TieLevel {
        NONE(0d, false),
        OPTIONAL(3d, true),
        MANDATORY(5d, true),
        PROTECTORATE(4d, false),
        OFFSHORE(4d, false),
        SAME_ALLIANCE(6d, true);

        private final double weight;
        private final boolean mutual;

        TieLevel(double weight, boolean mutual) {
            this.weight = weight;
            this.mutual = mutual;
        }

        public boolean isMutual() {
            return mutual;
        }

        public boolean isDirectBinding() {
            return this == SAME_ALLIANCE || this == OFFSHORE || this == PROTECTORATE;
        }

        public static TieLevel best(TieLevel left, TieLevel right) {
            return left.weight >= right.weight ? left : right;
        }
    }

    private static final class DirectedTie {
        private static final DirectedTie NONE = new DirectedTie(TieLevel.NONE, 0d);

        private final TieLevel level;
        private final double weight;

        private DirectedTie(TieLevel level, double weight) {
            this.level = level;
            this.weight = weight;
        }
    }

    private static final class MutableTie {
        private TieLevel level = TieLevel.NONE;

        private void merge(TieLevel candidate) {
            if (candidate.weight > level.weight) {
                level = candidate;
            }
        }

        private DirectedTie freeze() {
            return new DirectedTie(level, level.weight);
        }
    }

    private static final class TreatyGraph {
        private final IntArrayList sortedAllianceIds;
        private final Int2DoubleOpenHashMap metricByAlliance;
        private final Int2ObjectOpenHashMap<Int2ObjectOpenHashMap<DirectedTie>> outgoing;
        private final Int2ObjectOpenHashMap<IntSet> mutualNeighbors;

        private TreatyGraph(IntArrayList sortedAllianceIds,
                            Int2DoubleOpenHashMap metricByAlliance,
                            Int2ObjectOpenHashMap<Int2ObjectOpenHashMap<DirectedTie>> outgoing,
                            Int2ObjectOpenHashMap<IntSet> mutualNeighbors) {
            this.sortedAllianceIds = sortedAllianceIds;
            this.metricByAlliance = metricByAlliance;
            this.outgoing = outgoing;
            this.mutualNeighbors = mutualNeighbors;
        }

        private static TreatyGraph build(ObjectArrayList<DBAlliance> seedAlliances,
                                         Int2DoubleOpenHashMap metricByAlliance) {
            IntOpenHashSet includedAllianceIds = new IntOpenHashSet(seedAlliances.size());
            for (DBAlliance alliance : seedAlliances) {
                includedAllianceIds.add(alliance.getAlliance_id());
            }

            IntArrayList sortedIds = new IntArrayList(includedAllianceIds);
            sortedIds.sort((left, right) -> compareAllianceIds(right, left, metricByAlliance));

            Int2ObjectOpenHashMap<Int2ObjectOpenHashMap<MutableTie>> mutableOutgoing = new Int2ObjectOpenHashMap<>();
            Int2ObjectOpenHashMap<IntSet> mutualNeighbors = new Int2ObjectOpenHashMap<>();

            for (Treaty treaty : Locutus.imp().getNationDB().getTreaties()) {
                int fromId = treaty.getFromId();
                int toId = treaty.getToId();
                if (!includedAllianceIds.contains(fromId) || !includedAllianceIds.contains(toId)) {
                    continue;
                }
                TieLevel level = normalize(treaty.getType());
                if (level == TieLevel.NONE) {
                    continue;
                }
                if (level == TieLevel.MANDATORY || level == TieLevel.OPTIONAL || level == TieLevel.SAME_ALLIANCE) {
                    connectMutual(mutableOutgoing, mutualNeighbors, fromId, toId, level);
                } else {
                    connectDirected(mutableOutgoing, fromId, toId, level);
                }
            }

            for (DBAlliance alliance : seedAlliances) {
                DBAlliance parent = alliance.getCachedParentOfThisOffshore();
                if (parent != null && includedAllianceIds.contains(parent.getAlliance_id())) {
                    connectDirected(mutableOutgoing, alliance.getAlliance_id(), parent.getAlliance_id(), TieLevel.OFFSHORE);
                }
            }

            Int2ObjectOpenHashMap<Int2ObjectOpenHashMap<DirectedTie>> outgoing = new Int2ObjectOpenHashMap<>();
            for (Int2ObjectMap.Entry<Int2ObjectOpenHashMap<MutableTie>> entry : mutableOutgoing.int2ObjectEntrySet()) {
                Int2ObjectOpenHashMap<DirectedTie> ties = new Int2ObjectOpenHashMap<>();
                for (Int2ObjectMap.Entry<MutableTie> tieEntry : entry.getValue().int2ObjectEntrySet()) {
                    ties.put(tieEntry.getIntKey(), tieEntry.getValue().freeze());
                }
                outgoing.put(entry.getIntKey(), ties);
            }

            return new TreatyGraph(sortedIds, metricByAlliance, outgoing, mutualNeighbors);
        }

        private DirectedTie tie(int fromId, int toId) {
            Int2ObjectOpenHashMap<DirectedTie> ties = outgoing.get(fromId);
            if (ties == null) {
                return DirectedTie.NONE;
            }
            DirectedTie tie = ties.get(toId);
            return tie == null ? DirectedTie.NONE : tie;
        }
    }

    private static TieLevel normalize(TreatyType type) {
        if (type == null) {
            return TieLevel.NONE;
        }
        return switch (type) {
            case MDP, MDOAP -> TieLevel.MANDATORY;
            case ODP, ODOAP -> TieLevel.OPTIONAL;
            case EXTENSION -> TieLevel.SAME_ALLIANCE;
            case OFFSHORE -> TieLevel.OFFSHORE;
            case PROTECTORATE -> TieLevel.PROTECTORATE;
            case NONE, PIAT, NAP, NPT -> TieLevel.NONE;
        };
    }

    private static void connectMutual(Int2ObjectOpenHashMap<Int2ObjectOpenHashMap<MutableTie>> outgoing,
                                      Int2ObjectOpenHashMap<IntSet> mutualNeighbors,
                                      int leftId,
                                      int rightId,
                                      TieLevel level) {
        connectDirected(outgoing, leftId, rightId, level);
        connectDirected(outgoing, rightId, leftId, level);
        mutualNeighbors.computeIfAbsent(leftId, ignored -> new IntOpenHashSet()).add(rightId);
        mutualNeighbors.computeIfAbsent(rightId, ignored -> new IntOpenHashSet()).add(leftId);
    }

    private static void connectDirected(Int2ObjectOpenHashMap<Int2ObjectOpenHashMap<MutableTie>> outgoing,
                                        int fromId,
                                        int toId,
                                        TieLevel level) {
        Int2ObjectOpenHashMap<MutableTie> ties = outgoing.computeIfAbsent(fromId, ignored -> new Int2ObjectOpenHashMap<>());
        ties.computeIfAbsent(toId, ignored -> new MutableTie()).merge(level);
    }

    private static final class DisjointSet {
        private final Int2IntOpenHashMap parent = new Int2IntOpenHashMap();

        private DisjointSet() {
            parent.defaultReturnValue(Integer.MIN_VALUE);
        }

        private void add(int value) {
            if (!parent.containsKey(value)) {
                parent.put(value, value);
            }
        }

        private int find(int value) {
            int parentValue = parent.get(value);
            if (parentValue == Integer.MIN_VALUE || parentValue == value) {
                return value;
            }
            int root = find(parentValue);
            parent.put(value, root);
            return root;
        }

        private void union(int left, int right) {
            add(left);
            add(right);
            int leftRoot = find(left);
            int rightRoot = find(right);
            if (leftRoot != rightRoot) {
                parent.put(rightRoot, leftRoot);
            }
        }
    }
}
