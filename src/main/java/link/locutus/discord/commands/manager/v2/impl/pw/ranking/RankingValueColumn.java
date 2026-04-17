package link.locutus.discord.commands.manager.v2.impl.pw.ranking;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public record RankingValueColumn(
        RankingValueDescriptor descriptor,
        List<BigDecimal> values
) {
    public RankingValueColumn {
        descriptor = Objects.requireNonNull(descriptor, "descriptor");
        values = immutableList(values);
    }

    private static List<BigDecimal> immutableList(List<BigDecimal> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        return Collections.unmodifiableList(new ArrayList<>(source));
    }
}
