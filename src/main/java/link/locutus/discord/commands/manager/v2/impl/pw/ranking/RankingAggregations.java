package link.locutus.discord.commands.manager.v2.impl.pw.ranking;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

public final class RankingAggregations {
    private RankingAggregations() {
    }

    public static Map<Integer, Double> aggregateByGroup(
            Map<Integer, ? extends Number> valuesBySourceEntityId,
            Function<Integer, Integer> groupResolver,
            RankingAggregationMode aggregationMode
    ) {
        return switch (aggregationMode) {
            case SUM -> sumByGroup(valuesBySourceEntityId, groupResolver);
            case AVERAGE -> averageByGroup(valuesBySourceEntityId, groupResolver);
            default -> throw new IllegalArgumentException("Unsupported aggregation mode for grouped numeric values: " + aggregationMode);
        };
    }

    private static Map<Integer, Double> sumByGroup(
            Map<Integer, ? extends Number> valuesBySourceEntityId,
            Function<Integer, Integer> groupResolver
    ) {
        Map<Integer, Double> result = new LinkedHashMap<>();
        for (Map.Entry<Integer, ? extends Number> entry : valuesBySourceEntityId.entrySet()) {
            Integer groupId = groupResolver.apply(entry.getKey());
            if (groupId == null) {
                continue;
            }
            result.merge(groupId, entry.getValue().doubleValue(), Double::sum);
        }
        return result;
    }

    private static Map<Integer, Double> averageByGroup(
            Map<Integer, ? extends Number> valuesBySourceEntityId,
            Function<Integer, Integer> groupResolver
    ) {
        Map<Integer, Double> sums = new LinkedHashMap<>();
        Map<Integer, Integer> counts = new LinkedHashMap<>();
        for (Map.Entry<Integer, ? extends Number> entry : valuesBySourceEntityId.entrySet()) {
            Integer groupId = groupResolver.apply(entry.getKey());
            if (groupId == null) {
                continue;
            }
            sums.merge(groupId, entry.getValue().doubleValue(), Double::sum);
            counts.merge(groupId, 1, Integer::sum);
        }

        Map<Integer, Double> result = new LinkedHashMap<>();
        for (Map.Entry<Integer, Double> entry : sums.entrySet()) {
            int groupId = entry.getKey();
            int count = counts.getOrDefault(groupId, 0);
            if (count <= 0) {
                continue;
            }
            result.put(groupId, entry.getValue() / count);
        }
        return result;
    }
}
