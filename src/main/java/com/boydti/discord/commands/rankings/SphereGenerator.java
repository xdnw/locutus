package com.boydti.discord.commands.rankings;

import com.boydti.discord.Locutus;
import com.boydti.discord.pnw.Alliance;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.pnw.NationList;
import com.boydti.discord.pnw.SimpleNationList;
import com.boydti.discord.util.PnwUtil;
import com.boydti.discord.util.discord.DiscordUtil;
import com.boydti.discord.util.math.CIEDE2000;

import java.awt.Color;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SphereGenerator {
    private final Map<Integer, Alliance> alliances;
    private final Map<Integer, Map<Integer, NationList>> sphereAllianceMembers;
    private final Map<Integer, Color> sphereColors;
    private final Map<Integer, Double> sphereScore;
    private final Map<Integer, List<Alliance>> alliancesBySphere;
    private final List<Integer> spheresRanked;
    private final Map<Integer, String> sphereNames = new HashMap<>();

    public SphereGenerator(int topX) {
        this(Locutus.imp().getNationDB().getAlliances(true, true, true, topX));
    }

    public Map.Entry<Integer, List<Alliance>> getSphere(Alliance alliance) {
        for (Map.Entry<Integer, List<Alliance>> entry : alliancesBySphere.entrySet()) {
            if (entry.getValue().contains(alliance)) {
                return new AbstractMap.SimpleEntry<>(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
        }
        return null;
    }

    public SphereGenerator(Collection<Alliance> alliances) {
        Map<Integer, Alliance> aaCache = new HashMap<>();
        this.alliances = new HashMap<>();
        this.sphereAllianceMembers = new HashMap<>();
        this.sphereColors = new HashMap<>();
        this.sphereScore = new HashMap<>();
        this.alliancesBySphere = new HashMap<>();

        for (Alliance alliance : alliances) {
            this.alliances.put(alliance.getId(), alliance);
            List<Alliance> sphere = alliance.getSphereRankedCached(aaCache);
            int sphereId = sphere.get(0).getAlliance_id();

            Color color = sphereColors.computeIfAbsent(sphereId, f -> CIEDE2000.randomColor(sphereId, DiscordUtil.BACKGROUND_COLOR, sphereColors.values()));

            sphereColors.put(sphereId, color);
            alliancesBySphere.put(sphereId, sphere);

            {
                List<DBNation> nations = alliance.getNations(true, 7200, true);
                SimpleNationList nationList = new SimpleNationList(nations);
                sphereAllianceMembers.computeIfAbsent(sphereId, f -> new HashMap<>()).put(alliance.getAlliance_id(), nationList);
            }

            if (!sphereScore.containsKey(sphereId)) {
                List<DBNation> nations = new ArrayList<>();
                for (Alliance other : sphere) {
                    nations.addAll(other.getNations(true, 7200, true));
                }
                SimpleNationList nationList = new SimpleNationList(nations);

                sphereScore.put(sphereId, nationList.getScore());
                if (sphere.size() > 1) {
                    sphereAllianceMembers.computeIfAbsent(sphereId, f -> new HashMap<>()).put(0, nationList);
                }
                sphereNames.put(sphereId, PnwUtil.getSphereName(sphereId));
            }

            String colorStr = "#" + Integer.toHexString(color.getRGB()).substring(2);
        }

        this.spheresRanked = new ArrayList<>(sphereScore.keySet());
        spheresRanked.sort((o1, o2) -> Double.compare(sphereScore.get(o2), sphereScore.get(o1)));

    }

    public Alliance getAlliance(int allianceId) {
        return alliances.get(allianceId);
    }

    public Map<Integer, Alliance> getAlliancesMap() {
        return alliances;
    }

    public Set<Alliance> getAlliances() {
        return new HashSet<>(alliances.values());
    }

    public String getSphereName(int sphereId) {
        return sphereNames.get(sphereId);
    }

    /**
     * 0 = sphere id
     * the rest of the keys are alliance ids
     * @param sphereId
     * @return
     */
    public Map<Integer, NationList> getSphereAllianceMembers(int sphereId) {
        return sphereAllianceMembers.get(sphereId);
    }

    public Map<Integer, Map<Integer, NationList>> getSphereAllianceMembers() {
        return sphereAllianceMembers;
    }

    public Color getColor(int sphereId) {
        return sphereColors.get(sphereId);
    }

    public double getScore(int sphereId) {
        return sphereScore.getOrDefault(sphereId, 0d);
    }

    public List<Alliance> getAlliances(int sphereId) {
        return alliancesBySphere.get(sphereId);
    }

    public List<Integer> getSpheres() {
        return spheresRanked;
    }


}
