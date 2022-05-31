package com.boydti.discord.event;

import com.boydti.discord.pnw.Alliance;
import com.boydti.discord.pnw.DBNation;

import java.util.List;
import java.util.Map;

public class AllianceCreateEvent {
    private final Map<Integer, String> previousAlliances;
    private final int allianceId;
    private final List<DBNation> members;
    private final String name;

    public AllianceCreateEvent(String name, int allianceId, List<DBNation> members, Map<Integer, String> previousAlliances) {
        this.name = name;
        this.allianceId = allianceId;
        this.previousAlliances = previousAlliances;
        this.members = members;
    }

    public int getAllianceId() {
        return allianceId;
    }

    public String getName() {
        return name;
    }

    public Map<Integer, String> getPreviousAlliances() {
        return previousAlliances;
    }

    public List<DBNation> getMembers() {
        return members;
    }
}
