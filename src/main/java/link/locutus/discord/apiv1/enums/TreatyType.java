package link.locutus.discord.apiv1.enums;

import link.locutus.discord.commands.manager.v2.binding.annotation.Command;

import java.awt.Color;

public enum TreatyType {
    NONE(0),
    MDP(7, "MDP", "#ced518"),
    MDOAP(8, "MDoAP", "#c10007"),
    ODP(4, "ODP", "#00aeef"),
    ODOAP(5, "ODoAP", "#0d2572"),
    PROTECTORATE(6, "Protectorate", "#65c765"),
    PIAT(1, "PIAT", "#ffb6c1"),
    NAP(2, "NAP", "#ffb6c1"),
    NPT(3, "NPT", "#ff0000"),
    EXTENSION(9, "Extension", "#9333ea"),

    ;

    private final int strength;
    private final String id;
    private final String color;

    TreatyType(int strength) {
        this.strength = strength;
        this.id = name();
        this.color = null;
    }

    TreatyType(int strength, String id, String color) {
        this.strength = strength;
        this.id = id;
        this.color = color;
    }

    @Command(desc = "Get the name of the treaty.")
    public String getName() {
        return id;
    }

    @Command(desc = "Hex Color of treaty")
    public String getColor() {
        return color;
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

    @Command(desc = "If this is a defensive treaty")
    public boolean isMandatoryDefensive() {
        return this == MDP || this == MDOAP || this == PROTECTORATE || this == EXTENSION;
    }

    @Command(desc = "If this is an offensive treaty")
    public boolean isOffensive() {
        return this == MDOAP || this == ODOAP;
    }

    public static final TreatyType[] values = values();

    public static TreatyType parse(String arg) {
        return TreatyType.valueOf(arg.toUpperCase());
    }
}