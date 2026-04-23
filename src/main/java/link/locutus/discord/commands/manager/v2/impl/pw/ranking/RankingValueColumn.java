package link.locutus.discord.commands.manager.v2.impl.pw.ranking;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

public record RankingValueColumn(
        RankingValueKind kind,
        RankingValueFormat format,
    List<BigDecimal> values
) {
    public RankingValueColumn {
        kind = Objects.requireNonNull(kind, "kind");
        format = Objects.requireNonNull(format, "format");
        values = RankingResultSupport.immutableList(values);
    }
}
