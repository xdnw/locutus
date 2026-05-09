package link.locutus.discord.treatyvis.runtime;

import link.locutus.discord.apiv1.enums.TreatyType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

public final class TreatyVisRuntimeBuilder {
    public static final int PAYLOAD_VERSION = 1;

    private static final TreatyVisRuntimePayload.TreatyChanges EMPTY_TREATY_CHANGES =
            new TreatyVisRuntimePayload.TreatyChanges(List.of(), List.of(0), List.of(), List.of());
    private static final TreatyVisRuntimePayload.FlagChanges EMPTY_FLAG_CHANGES =
            new TreatyVisRuntimePayload.FlagChanges(List.of(), List.of(0), List.of(), List.of());
    private static final TreatyVisRuntimePayload.ScoreSnapshots EMPTY_SCORE_SNAPSHOTS =
            new TreatyVisRuntimePayload.ScoreSnapshots(List.of(), List.of(0), List.of(), List.of());

    public TreatyVisRuntimePayload build(TreatyVisRuntimeInput input) {
        Objects.requireNonNull(input, "input");

        List<Integer> allianceIds = collectAllianceIds(input);
        Map<Integer, Integer> allianceIndexById = indexByValueList(allianceIds);
        Map<Integer, String> allianceNamesById = buildAllianceNamesById(input.alliances(), allianceIndexById);
        List<String> allianceDisplayNames = allianceIds.stream().map(allianceNamesById::get).toList();

        List<TreatyType> treatyTypes = collectTreatyTypes(input);
        Map<TreatyType, Integer> treatyTypeIndexByType = indexByValueList(treatyTypes);

        List<TreatyVisRuntimeInput.TreatyEdge> edgeInputs = new ArrayList<>(input.activeTreaties());
        for (TreatyVisRuntimeInput.TreatyChange change : input.treatyChanges()) {
            if (change != null && change.edge() != null) {
                edgeInputs.add(change.edge());
            }
        }

        List<TreatyVisRuntimeInput.TreatyEdge> edges = edgeInputs.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator
                        .comparingInt(TreatyVisRuntimeInput.TreatyEdge::fromAllianceId)
                        .thenComparingInt(TreatyVisRuntimeInput.TreatyEdge::toAllianceId)
                        .thenComparing(TreatyVisRuntimeInput.TreatyEdge::treatyType, Comparator.nullsFirst(Comparator.naturalOrder())))
                .distinct()
                .toList();
        Map<TreatyEdgeIdentity, Integer> edgeIndexByIdentity = new LinkedHashMap<>(edges.size());

        List<Integer> fromAllianceIndexes = new ArrayList<>(edges.size());
        List<Integer> toAllianceIndexes = new ArrayList<>(edges.size());
        List<Integer> treatyTypeIndexes = new ArrayList<>(edges.size());
        List<Integer> activeEdgeIndexes = new ArrayList<>(input.activeTreaties().size());
        for (int index = 0; index < edges.size(); index += 1) {
            TreatyVisRuntimeInput.TreatyEdge edge = edges.get(index);
            fromAllianceIndexes.add(requireIndex(allianceIndexById, edge.fromAllianceId(), "alliance"));
            toAllianceIndexes.add(requireIndex(allianceIndexById, edge.toAllianceId(), "alliance"));
            treatyTypeIndexes.add(requireIndex(treatyTypeIndexByType, requireTreatyType(edge), "treaty type"));
            edgeIndexByIdentity.put(TreatyEdgeIdentity.from(edge), index);
        }
        for (TreatyVisRuntimeInput.TreatyEdge edge : input.activeTreaties()) {
            activeEdgeIndexes.add(requireIndex(edgeIndexByIdentity, TreatyEdgeIdentity.from(edge), "edge"));
        }

        List<TreatyVisRuntimeInput.AllianceFlag> flags = input.initialFlags().stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(TreatyVisRuntimeInput.AllianceFlag::allianceId))
                .distinct()
                .toList();

        List<Integer> flagAllianceIndexes = new ArrayList<>(flags.size());
        List<Integer> flagIndexes = new ArrayList<>(flags.size());
        for (TreatyVisRuntimeInput.AllianceFlag flag : flags) {
            validateFlagIndex(flag.flagIndex(), "initialState.flagIndexes");
            if (flag.flagIndex() <= 0) {
                continue;
            }
            flagAllianceIndexes.add(requireIndex(allianceIndexById, flag.allianceId(), "alliance"));
            flagIndexes.add(flag.flagIndex());
        }

        List<TreatyVisRuntimeInput.AllianceScore> scores = input.initialScores().stream()
                .sorted(Comparator
                        .comparingInt(TreatyVisRuntimeInput.AllianceScore::quantizedScore)
                        .reversed()
                        .thenComparingInt(TreatyVisRuntimeInput.AllianceScore::allianceId))
                .distinct()
                .toList();

        List<Integer> scoreAllianceIndexes = new ArrayList<>(scores.size());
        List<Integer> scoreQuantized = new ArrayList<>(scores.size());
        for (TreatyVisRuntimeInput.AllianceScore score : scores) {
            scoreAllianceIndexes.add(requireIndex(allianceIndexById, score.allianceId(), "alliance"));
            scoreQuantized.add(score.quantizedScore());
        }

        TreatyVisRuntimePayload.TreatyChanges treatyChanges = buildTreatyChanges(input.treatyChanges(), edgeIndexByIdentity);
        TreatyVisRuntimePayload.FlagChanges flagChanges = buildFlagChanges(input.flagChanges(), allianceIndexById);
        TreatyVisRuntimePayload.ScoreSnapshots scoreSnapshots = buildScoreSnapshots(input.scoreSnapshots(), allianceIndexById);

        return new TreatyVisRuntimePayload(
                PAYLOAD_VERSION,
                input.baseDay(),
                input.scoreQuantization(),
                new TreatyVisRuntimePayload.Alliances(allianceIds, allianceDisplayNames),
                treatyTypes.stream().map(TreatyVisRuntimeBuilder::toPayloadTreatyTypeName).toList(),
                new TreatyVisRuntimePayload.Edges(fromAllianceIndexes, toAllianceIndexes, treatyTypeIndexes),
                new TreatyVisRuntimePayload.InitialState(
                        activeEdgeIndexes,
                        flagAllianceIndexes,
                        flagIndexes,
                        scoreAllianceIndexes,
                        scoreQuantized
                ),
                treatyChanges,
                flagChanges,
                scoreSnapshots
        );
    }

    private static List<Integer> collectAllianceIds(TreatyVisRuntimeInput input) {
        TreeSet<Integer> allianceIds = new TreeSet<>();
        for (TreatyVisRuntimeInput.Alliance alliance : input.alliances()) {
            if (alliance != null && alliance.id() > 0) {
                allianceIds.add(alliance.id());
            }
        }
        for (TreatyVisRuntimeInput.TreatyEdge edge : input.activeTreaties()) {
            if (edge != null) {
                allianceIds.add(edge.fromAllianceId());
                allianceIds.add(edge.toAllianceId());
            }
        }
        for (TreatyVisRuntimeInput.AllianceFlag flag : input.initialFlags()) {
            if (flag != null) {
                allianceIds.add(flag.allianceId());
            }
        }
        for (TreatyVisRuntimeInput.AllianceScore score : input.initialScores()) {
            if (score != null) {
                allianceIds.add(score.allianceId());
            }
        }
        for (TreatyVisRuntimeInput.TreatyChange change : input.treatyChanges()) {
            if (change != null && change.edge() != null) {
                allianceIds.add(change.edge().fromAllianceId());
                allianceIds.add(change.edge().toAllianceId());
            }
        }
        for (TreatyVisRuntimeInput.FlagChange change : input.flagChanges()) {
            if (change != null) {
                allianceIds.add(change.allianceId());
            }
        }
        for (TreatyVisRuntimeInput.ScoreSnapshot snapshot : input.scoreSnapshots()) {
            if (snapshot == null || snapshot.scores() == null) {
                continue;
            }
            for (TreatyVisRuntimeInput.AllianceScore score : snapshot.scores()) {
                if (score != null) {
                    allianceIds.add(score.allianceId());
                }
            }
        }
        return List.copyOf(allianceIds);
    }

    private static Map<Integer, String> buildAllianceNamesById(
            List<TreatyVisRuntimeInput.Alliance> alliances,
            Map<Integer, Integer> allianceIndexById
    ) {
        Map<Integer, String> namesById = new LinkedHashMap<>(allianceIndexById.size());
        for (TreatyVisRuntimeInput.Alliance alliance : alliances) {
            if (alliance != null && allianceIndexById.containsKey(alliance.id())) {
                namesById.put(alliance.id(), alliance.name());
            }
        }
        return namesById;
    }

    private static List<TreatyType> collectTreatyTypes(TreatyVisRuntimeInput input) {
        LinkedHashSet<TreatyType> treatyTypes = new LinkedHashSet<>();
        for (TreatyVisRuntimeInput.TreatyEdge treaty : input.activeTreaties()) {
            treatyTypes.add(requireTreatyType(treaty));
        }
        for (TreatyVisRuntimeInput.TreatyChange change : input.treatyChanges()) {
            if (change != null && change.edge() != null) {
                treatyTypes.add(requireTreatyType(change.edge()));
            }
        }
        return treatyTypes.stream().sorted(Comparator.comparingInt(Enum::ordinal)).toList();
    }

    private static <T> Map<T, Integer> indexByValueList(List<T> values) {
        Map<T, Integer> result = new LinkedHashMap<>(values.size());
        for (int index = 0; index < values.size(); index += 1) {
            result.put(values.get(index), index);
        }
        return result;
    }

    private static <T> int requireIndex(Map<T, Integer> indexes, T value, String label) {
        Integer index = indexes.get(value);
        if (index == null) {
            throw new IllegalArgumentException("Missing " + label + " dictionary entry for " + value);
        }
        return index;
    }

    private static TreatyVisRuntimePayload.TreatyChanges buildTreatyChanges(
            List<TreatyVisRuntimeInput.TreatyChange> inputChanges,
            Map<TreatyEdgeIdentity, Integer> edgeIndexByIdentity
    ) {
        if (inputChanges.isEmpty()) {
            return EMPTY_TREATY_CHANGES;
        }

        List<TreatyVisRuntimeInput.TreatyChange> changes = inputChanges.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(TreatyVisRuntimeInput.TreatyChange::day))
                .toList();
        List<Integer> days = new ArrayList<>();
        List<Integer> rowOffsets = new ArrayList<>();
        List<Integer> edgeIndexes = new ArrayList<>();
        List<Integer> actions = new ArrayList<>();
        rowOffsets.add(0);
        Integer currentDay = null;
        for (TreatyVisRuntimeInput.TreatyChange change : changes) {
            if (change.edge() == null) {
                continue;
            }
            if (change.actionCode() < 1 || change.actionCode() > 4) {
                throw new IllegalArgumentException("Unsupported treaty change action code: " + change.actionCode());
            }
            if (!Objects.equals(currentDay, change.day())) {
                if (currentDay != null) {
                    rowOffsets.add(edgeIndexes.size());
                }
                currentDay = change.day();
                days.add(change.day());
            }
            edgeIndexes.add(requireIndex(edgeIndexByIdentity, TreatyEdgeIdentity.from(change.edge()), "edge"));
            actions.add(change.actionCode());
        }
        rowOffsets.add(edgeIndexes.size());
        return new TreatyVisRuntimePayload.TreatyChanges(days, rowOffsets, edgeIndexes, actions);
    }

    private static TreatyVisRuntimePayload.FlagChanges buildFlagChanges(
            List<TreatyVisRuntimeInput.FlagChange> inputChanges,
            Map<Integer, Integer> allianceIndexById
    ) {
        if (inputChanges.isEmpty()) {
            return EMPTY_FLAG_CHANGES;
        }

        List<TreatyVisRuntimeInput.FlagChange> changes = inputChanges.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(TreatyVisRuntimeInput.FlagChange::day))
                .toList();
        List<Integer> days = new ArrayList<>();
        List<Integer> rowOffsets = new ArrayList<>();
        List<Integer> allianceIndexes = new ArrayList<>();
        List<Integer> flagIndexes = new ArrayList<>();
        rowOffsets.add(0);
        Integer currentDay = null;
        for (TreatyVisRuntimeInput.FlagChange change : changes) {
            if (!Objects.equals(currentDay, change.day())) {
                if (currentDay != null) {
                    rowOffsets.add(allianceIndexes.size());
                }
                currentDay = change.day();
                days.add(change.day());
            }
            validateFlagIndex(change.flagIndex(), "flagChanges.flagIndexes");
            allianceIndexes.add(requireIndex(allianceIndexById, change.allianceId(), "alliance"));
            flagIndexes.add(change.flagIndex());
        }
        rowOffsets.add(allianceIndexes.size());
        return new TreatyVisRuntimePayload.FlagChanges(days, rowOffsets, allianceIndexes, flagIndexes);
    }

    private static TreatyVisRuntimePayload.ScoreSnapshots buildScoreSnapshots(
            List<TreatyVisRuntimeInput.ScoreSnapshot> inputSnapshots,
            Map<Integer, Integer> allianceIndexById
    ) {
        if (inputSnapshots.isEmpty()) {
            return EMPTY_SCORE_SNAPSHOTS;
        }

        List<TreatyVisRuntimeInput.ScoreSnapshot> snapshots = inputSnapshots.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(TreatyVisRuntimeInput.ScoreSnapshot::day))
                .toList();
        List<Integer> days = new ArrayList<>(snapshots.size());
        List<Integer> rowOffsets = new ArrayList<>();
        List<Integer> allianceIndexes = new ArrayList<>();
        List<Integer> scoresQuantized = new ArrayList<>();
        rowOffsets.add(0);
        for (TreatyVisRuntimeInput.ScoreSnapshot snapshot : snapshots) {
            days.add(snapshot.day());
            List<TreatyVisRuntimeInput.AllianceScore> orderedScores = snapshot.scores().stream()
                    .filter(Objects::nonNull)
                    .sorted(Comparator
                            .comparingInt(TreatyVisRuntimeInput.AllianceScore::quantizedScore)
                            .reversed()
                            .thenComparingInt(TreatyVisRuntimeInput.AllianceScore::allianceId))
                    .toList();
            for (TreatyVisRuntimeInput.AllianceScore score : orderedScores) {
                allianceIndexes.add(requireIndex(allianceIndexById, score.allianceId(), "alliance"));
                scoresQuantized.add(score.quantizedScore());
            }
            rowOffsets.add(allianceIndexes.size());
        }
        return new TreatyVisRuntimePayload.ScoreSnapshots(days, rowOffsets, allianceIndexes, scoresQuantized);
    }

    private static TreatyType requireTreatyType(TreatyVisRuntimeInput.TreatyEdge edge) {
        if (edge.treatyType() == null) {
            throw new IllegalArgumentException("Missing treaty type for edge " + edge.fromAllianceId() + " -> " + edge.toAllianceId());
        }
        return edge.treatyType();
    }

    private static String toPayloadTreatyTypeName(TreatyType treatyType) {
        return treatyType.getName().trim().toLowerCase(Locale.ROOT);
    }

    private static void validateFlagIndex(int flagIndex, String label) {
        if (flagIndex < 0) {
            throw new IllegalArgumentException(label + " must be non-negative.");
        }
    }

    private record TreatyEdgeIdentity(int fromAllianceId, int toAllianceId, TreatyType treatyType) {
        static TreatyEdgeIdentity from(TreatyVisRuntimeInput.TreatyEdge edge) {
            return new TreatyEdgeIdentity(edge.fromAllianceId(), edge.toAllianceId(), requireTreatyType(edge));
        }
    }
}