package link.locutus.discord.commands.manager.v2.binding.bindings;

public enum MethodEnum {
    // DBNation methods
    daysSinceCreationNotLoggedIn,
    getAuditRaw,
    getStockpile,
    getDeposits,
    hasProvidedIdentity,
    getTradeQuantity,
    getTradeAvgPpu,
    getLastUnitBuy,
    getUpdateTZ,
    getFreeOffSpyOps,
    getTradeValue,
    getBeigeLoot,
    lootTotal,
    getResearch,

    // GuildDB methods
    getTrackedBanks,

    // DBAlliance methods
    getMetricAt,
    getMetricsAt,
    getGrowthSummary,

    ;

    private final MethodIdentity identity;

    MethodEnum() {
        this.identity = MethodIdentity.of(this);
    }

    public MethodIdentity get() {
        return identity;
    }

    public MethodIdentity of(Object... args) {
        return MethodIdentity.of(this, args);
    }
}
