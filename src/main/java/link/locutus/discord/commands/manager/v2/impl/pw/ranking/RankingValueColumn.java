package link.locutus.discord.commands.manager.v2.impl.pw.ranking;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public record RankingValueColumn(
        RankingValueFormat format,
        List<Double> values
) {
    public RankingValueColumn {
        format = Objects.requireNonNull(format, "format");
        values = immutableList(values);
    }

    private static List<Double> immutableList(List<Double> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        return Collections.unmodifiableList(new ArrayList<>(source));
    }
}
