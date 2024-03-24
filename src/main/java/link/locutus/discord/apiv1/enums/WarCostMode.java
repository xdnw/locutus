package link.locutus.discord.apiv1.enums;

public enum WarCostMode {
    DEALT(true, false, true, false),
    NET_DEALT(true, true, true, false),
    PROFIT(true, true, false, true),
    LOSSES(false, true, false, true),
    NET_LOSSES(true, true, false, true),
    ;

    private final boolean includeDealt;
    private final boolean includeReceived;
    private final boolean addDealt;
    private final boolean addReceived;

    WarCostMode(boolean includeDealt, boolean includeReceived, boolean addDealt, boolean addReceived) {
        this.includeDealt = includeDealt;
        this.includeReceived = includeReceived;
        this.addDealt = addDealt;
        this.addReceived = addReceived;
    }

    public boolean includeDealt() {
        return includeDealt;
    }

    public boolean includeReceived() {
        return includeReceived;
    }

    public boolean addDealt() {
        return addDealt;
    }

    public boolean addReceived() {
        return addReceived;
    }


}
