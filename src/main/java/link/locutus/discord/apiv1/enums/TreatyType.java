package link.locutus.discord.apiv1.enums;

import link.locutus.discord.commands.manager.v2.binding.annotation.Command;

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
    EXTENSION(9, "Extension"),

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

    @Command(desc = "Get the name of the treaty.")
    public String getName() {
        return id;
    }

    public String getId() {
        return id;
    }

    @Command(desc = "Get the numeric strength of the treaty")
    public int getStrength() {
        return strength;
    }

    @Command(desc = "If this is a defensive treaty")
    public boolean isDefensive() {
        return this == MDP || this == MDOAP || this == ODP || this == ODOAP || this == PROTECTORATE || this == EXTENSION;
    }

    @Command(desc = "If this is an offensive treaty")
    public boolean isOffensive() {
        return this == MDOAP || this == ODOAP;
    }

    public static TreatyType[] values = values();

    public static TreatyType parse(String arg) {
        return TreatyType.valueOf(arg.toUpperCase());
    }
}