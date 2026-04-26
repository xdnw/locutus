package link.locutus.discord.commands.manager.v2.impl.pw.ranking;

import java.math.BigDecimal;
import java.math.BigInteger;

public final class RankingSupport {
    private RankingSupport() {
    }

    public static BigDecimal numericValue(Number value) {
        if (value == null) {
            throw new IllegalArgumentException("value cannot be null");
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof BigInteger integer) {
            return new BigDecimal(integer);
        }
        if (value instanceof Double d) {
            if (!Double.isFinite(d)) {
                throw new IllegalArgumentException("value must be finite");
            }
            return new BigDecimal(value.toString());
        }
        if (value instanceof Float f) {
            if (!Float.isFinite(f)) {
                throw new IllegalArgumentException("value must be finite");
            }
            return new BigDecimal(value.toString());
        }
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            return BigDecimal.valueOf(value.longValue());
        }
        return new BigDecimal(value.toString());
    }
}
