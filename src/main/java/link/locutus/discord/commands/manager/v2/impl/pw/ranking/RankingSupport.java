package link.locutus.discord.commands.manager.v2.impl.pw.ranking;

public final class RankingSupport {
    private RankingSupport() {
    }

    public static double numericValue(Number value) {
        if (value == null) {
            throw new IllegalArgumentException("value cannot be null");
        }
        double numeric = value.doubleValue();
        if (!Double.isFinite(numeric)) {
            throw new IllegalArgumentException("value must be finite");
        }
        return numeric == 0d ? 0d : numeric;
    }
}
