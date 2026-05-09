package link.locutus.discord.treatyvis.runtime;

import link.locutus.discord.apiv1.enums.TreatyType;

import java.util.List;

public record TreatyVisRuntimeInput(
        int baseDay,
        int scoreQuantization,
        List<Alliance> alliances,
        List<TreatyEdge> activeTreaties,
        List<AllianceFlag> initialFlags,
        List<AllianceScore> initialScores,
        List<TreatyChange> treatyChanges,
        List<FlagChange> flagChanges,
    List<ScoreSnapshot> scoreSnapshots
) {
    public TreatyVisRuntimeInput(
            int baseDay,
            int scoreQuantization,
            List<Alliance> alliances,
            List<TreatyEdge> activeTreaties,
            List<AllianceFlag> initialFlags,
            List<AllianceScore> initialScores
    ) {
        this(baseDay, scoreQuantization, alliances, activeTreaties, initialFlags, initialScores, List.of(), List.of(), List.of());
    }

    public record Alliance(int id, String name) {
    }

    public record TreatyEdge(int fromAllianceId, int toAllianceId, TreatyType treatyType) {
    }

    public record AllianceFlag(int allianceId, int flagIndex) {
    }

    public record AllianceScore(int allianceId, int quantizedScore) {
    }

    public record TreatyChange(int day, TreatyEdge edge, int actionCode) {
    }

    public record FlagChange(int day, int allianceId, int flagIndex) {
    }

    public record ScoreSnapshot(int day, List<AllianceScore> scores) {
    }
}