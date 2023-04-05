package link.locutus.discord.apiv1.enums;

public enum TreatyType {
    NONE(0),
    MDP(7),
    MDOAP(8, "MDoAP"),
    ODP(4),
    ODOAP(5, "ODoAP"),
    PROTECTORATE(6, "Protectorate"),
    PIAT(1),
    NAP(2),

    NPT(3),


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