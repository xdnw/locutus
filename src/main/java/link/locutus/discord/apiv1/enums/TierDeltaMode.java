package link.locutus.discord.apiv1.enums;

public enum TierDeltaMode {
    START_COUNT("Count @ Baseline"),
    END_COUNT("Count @ Current"),
    NET("Net Change"),
    GAINED("Gained"),
    LOST("Lost"),
    TURNOVER("Turnover (Gained + Lost)");

    private final String label;

    TierDeltaMode(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }
}