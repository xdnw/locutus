package link.locutus.discord.commands.manager.v2.impl.pw.ranking;

public enum TradeFlowDirection {
    SOLD(-1),
    BOUGHT(1);

    private final int sign;

    TradeFlowDirection(int sign) {
        this.sign = sign;
    }

    public int sign() {
        return sign;
    }
}
