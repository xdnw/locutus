package link.locutus.discord.commands.manager.v2.impl.pw.ranking;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record RankingSectionSpec(
        RankingSectionKind sectionKind,
        RankingSortDirection sortDirection,
        Map<Integer, BigDecimal> values
) {
    public RankingSectionSpec {
        sectionKind = Objects.requireNonNull(sectionKind, "sectionKind");
        sortDirection = Objects.requireNonNull(sortDirection, "sortDirection");
        values = values == null || values.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
