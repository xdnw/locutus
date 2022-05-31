package com.boydti.discord.apiv1.enums;

public enum WarPolicy {
    ATTRITION,
    TURTLE,
    BLITZKRIEG,
    FORTRESS,
    MONEYBAGS,
    PIRATE,
    TACTICIAN,
    GUARDIAN,
    COVERT,
    ARCANE,

    ;

    public static WarPolicy parse(String policy) {
        return WarPolicy.valueOf(policy.toUpperCase().replace(" ", "_"));
    }
}