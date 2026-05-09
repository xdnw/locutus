package link.locutus.discord.treatyvis.runtime;

import java.util.List;

public record TreatyVisRuntimePayload(
        int version,
        int baseDay,
        int scoreQuantization,
        Alliances alliances,
        List<String> treatyTypes,
        Edges edges,
        InitialState initialState,
        TreatyChanges treatyChanges,
        FlagChanges flagChanges,
        ScoreSnapshots scoreSnapshots
) {
    public record Alliances(List<Integer> ids, List<String> names) {
    }

    public record Edges(
            List<Integer> fromAllianceIndexes,
            List<Integer> toAllianceIndexes,
            List<Integer> treatyTypeIndexes
    ) {
    }

    public record InitialState(
            List<Integer> activeEdgeIndexes,
            List<Integer> flagAllianceIndexes,
            List<Integer> flagIndexes,
            List<Integer> scoreAllianceIndexes,
            List<Integer> scoreQuantized
    ) {
    }

    public record TreatyChanges(
            List<Integer> days,
            List<Integer> rowOffsets,
            List<Integer> edgeIndexes,
            List<Integer> actions
    ) {
    }

    public record FlagChanges(
            List<Integer> days,
            List<Integer> rowOffsets,
            List<Integer> allianceIndexes,
            List<Integer> flagIndexes
    ) {
    }

    public record ScoreSnapshots(
            List<Integer> days,
            List<Integer> rowOffsets,
            List<Integer> allianceIndexes,
            List<Integer> scoresQuantized
    ) {
    }
}