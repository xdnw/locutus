package link.locutus.discord.commands.manager.v2.impl.pw.ranking;

import java.util.Objects;

public record RankingSectionRange(
        RankingSectionKind kind,
        int rowOffset,
        int rowCount
) {
    public RankingSectionRange {
        kind = Objects.requireNonNull(kind, "kind");
        if (rowOffset < 0) {
            throw new IllegalArgumentException("rowOffset cannot be negative");
        }
        if (rowCount < 0) {
            throw new IllegalArgumentException("rowCount cannot be negative");
        }
    }
}
