package link.locutus.discord.commands.manager.v2.impl.pw.ranking;

import java.math.BigDecimal;

public final class RankingSupport {
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
}
