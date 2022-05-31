package com.boydti.discord.db.entities;

import com.boydti.discord.pnw.Alliance;
import com.boydti.discord.apiv1.enums.Rank;

public class AllianceChange {
    public Alliance fromAA, toAA;
    public Rank fromRank, toRank;
    public long date;

    public AllianceChange(Integer fromAA, Integer toAA, Rank fromRank, Rank toRank, long date) {
        if (fromAA == null) fromAA = 0;
        if (toAA == null) toAA = 0;
        if (fromRank == null) fromRank = Rank.REMOVE;
        if (toRank == null) fromRank = Rank.REMOVE;
        this.fromAA = new Alliance(fromAA);
        this.toAA = new Alliance(toAA);
        this.fromRank = fromRank;
        this.toRank = toRank;
        this.date = date;
    }
}
