package link.locutus.discord.commands.manager.v2.impl.pw.ranking;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record RankingColumnSpec(
        RankingValueKind kind,
        RankingValueFormat format,
    Map<Integer, BigDecimal> values
) {
    public RankingColumnSpec {
        kind = Objects.requireNonNull(kind, "kind");
        format = Objects.requireNonNull(format, "format");
        values = values == null || values.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
