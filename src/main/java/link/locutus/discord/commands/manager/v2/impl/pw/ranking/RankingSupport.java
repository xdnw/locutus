package link.locutus.discord.commands.manager.v2.impl.pw.ranking;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

public final class RankingSupport {
    private static final Pattern NON_KEY = Pattern.compile("[^a-z0-9]+");

    private RankingSupport() {
    }

    public static BigDecimal numericValue(Number value) {
        if (value == null) {
            throw new IllegalArgumentException("value cannot be null");
        }
        BigDecimal numeric = new BigDecimal(value.toString()).stripTrailingZeros();
        if (numeric.scale() < 0) {
            numeric = numeric.setScale(0);
        }
        if (numeric.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return numeric;
    }

    public static Map<String, Object> immutableMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> copy = new LinkedHashMap<>(metadata.size());
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            copy.put(Objects.requireNonNull(entry.getKey(), "metadata key"), metadataValue(entry.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }

    public static Object metadataValue(Object value) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case Enum<?> enumValue -> enumValue.name();
            case Number number -> numericValue(number);
            case Collection<?> collection -> immutableMetadataList(collection);
            default -> value;
        };
    }

    public static String machineKey(String value) {
        String normalized = NON_KEY.matcher(value.toLowerCase(Locale.ROOT)).replaceAll("_");
        normalized = normalized.replaceAll("^_+|_+$", "");
        return normalized.isEmpty() ? "value" : normalized;
    }

    private static List<Object> immutableMetadataList(Collection<?> values) {
        if (values.isEmpty()) {
            return List.of();
        }
        List<Object> copy = new ArrayList<>(values.size());
        for (Object value : values) {
            copy.add(metadataValue(value));
        }
        return Collections.unmodifiableList(copy);
    }
}
