package link.locutus.discord.commands.manager.v2.impl.pw.ranking;

import java.math.BigDecimal;
import java.util.Objects;

public record RankingNumericValue(String exactValue, RankingNumericType numericType) {
    public RankingNumericValue {
        exactValue = Objects.requireNonNull(exactValue, "exactValue");
        numericType = Objects.requireNonNull(numericType, "numericType");
    }

    public static RankingNumericValue ofLong(long value) {
        return new RankingNumericValue(Long.toString(value), RankingNumericType.INTEGER);
    }

    public static RankingNumericValue ofNumber(Number value, RankingNumericType numericType) {
        if (value == null) {
            throw new IllegalArgumentException("value cannot be null");
        }
        if (numericType == RankingNumericType.INTEGER) {
            return new RankingNumericValue(new BigDecimal(value.toString()).stripTrailingZeros().toBigIntegerExact().toString(), numericType);
        }
        String exact = new BigDecimal(value.toString()).stripTrailingZeros().toPlainString();
        if ("-0".equals(exact)) {
            exact = "0";
        }
        return new RankingNumericValue(exact, numericType);
    }

    public BigDecimal toBigDecimal() {
        return new BigDecimal(exactValue);
    }
}
