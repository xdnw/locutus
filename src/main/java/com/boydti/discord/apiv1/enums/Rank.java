package com.boydti.discord.apiv1.enums;

import java.util.HashMap;
import java.util.Map;

public enum Rank {
    LEADER("leader", 5),
    HEIR("heir", 4),
    OFFICER("officer", 3),
    MEMBER("member", 2),
    APPLICANT("applicant", 1),
    REMOVE("remove", 0),
    BAN("ban", -1),
    UNBAN("unban", -2),
    INVITE("invite", -3),
    UNINVITE("uninvite", -3),

        ;

    public final String key;
    public final int id;

    Rank(String key, int id) {
        this.key = key;
        this.id = id;
    }

    @Override
    public String toString() {
        return key;
    }

    private static Map<Integer, Rank> byId = new HashMap<>();

    static {
        for (Rank rank : values()) {
            byId.put(rank.id, rank);
        }
    }

    public static Rank byId(int id) {
        return byId.get(id);
    }
}
