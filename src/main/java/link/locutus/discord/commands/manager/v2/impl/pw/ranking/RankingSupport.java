package link.locutus.discord.commands.manager.v2.impl.pw.ranking;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class RankingSupport {
    private static final Pattern NON_KEY = Pattern.compile("[^a-z0-9]+");

    private RankingSupport() {
    }

    public static RankingMetricDescriptor metricDescriptor(
            String key,
            String label,
            RankingValueFormat valueFormat,
            RankingNumericType numericType
    ) {
        return new RankingMetricDescriptor(machineKey(key), label, numericType, valueFormat);
    }

    public static RankingQueryField field(String key, String label, Object value) {
        return new RankingQueryField(key, label, String.valueOf(value));
    }

    public static List<RankingQueryField> sectionMetadata(String sourceType, RankingAggregationMode aggregationMode) {
        return List.of(
                field("source_type", "Source Type", sourceType),
                field("aggregation_mode", "Aggregation Mode", aggregationMode.name())
        );
    }

    public static String machineKey(String value) {
        String normalized = NON_KEY.matcher(value.toLowerCase(Locale.ROOT)).replaceAll("_");
        normalized = normalized.replaceAll("^_+|_+$", "");
        return normalized.isEmpty() ? "value" : normalized;
    }
}
