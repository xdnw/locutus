package link.locutus.discord.apiv1.enums;

public enum TreatyType {
    NONE(0),
    MDP(6),
    MDOAP(7, "MDoAP"),
    ODP(3),
    ODOAP(4, "ODoAP"),
    PROTECTORATE(5, "Protectorate"),
    PIAT(1),
    NAP(2),

    ;

    private final int strength;
    private final String id;

    TreatyType(int strength) {
        this.strength = strength;
        this.id = name();
    }

    TreatyType(int strength, String id) {
        this.strength = strength;
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public int getStrength() {
        return strength;
    }

    public static TreatyType[] values = values();

    public static TreatyType parse(String arg) {
        return TreatyType.valueOf(arg.toUpperCase());
    }
}