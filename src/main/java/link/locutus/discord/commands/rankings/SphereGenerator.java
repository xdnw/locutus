package link.locutus.discord.commands.rankings;

import link.locutus.discord.Locutus;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.pnw.NationList;
import link.locutus.discord.pnw.SimpleNationList;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.math.CIEDE2000;
import link.locutus.discord.util.scheduler.KeyValue;

import java.awt.*;
import java.util.List;
import java.util.*;

public class SphereGenerator {
    private final Map<Integer, DBAlliance> alliances;
    private final Map<Integer, Map<Integer, NationList>> sphereAllianceMembers;
    private final Map<Integer, Color> sphereColors;
    private final Map<Integer, Double> sphereScore;
    private final Map<Integer, List<DBAlliance>> alliancesBySphere;
    private final List<Integer> spheresRanked;
    private final Map<Integer, String> sphereNames = new HashMap<>();

    public SphereGenerator(int topX) {
        this(Locutus.imp().getNationDB().getAlliances(true, true, true, topX));
    }

    public SphereGenerator(Collection<DBAlliance> alliances) {
        Map<Integer, DBAlliance> aaCache = new HashMap<>();
        this.alliances = new HashMap<>();
        this.sphereAllianceMembers = new HashMap<>();
        this.sphereColors = new HashMap<>();
        this.sphereScore = new HashMap<>();
        this.alliancesBySphere = new HashMap<>();

        for (DBAlliance alliance : alliances) {
            this.alliances.put(alliance.getId(), alliance);
            List<DBAlliance> sphere = alliance.getSphereRankedCached(aaCache);
            int sphereId = sphere.get(0).getAlliance_id();

            Color color = sphereColors.computeIfAbsent(sphereId, f -> CIEDE2000.randomColor(sphereId, DiscordUtil.BACKGROUND_COLOR, sphereColors.values()));

            sphereColors.put(sphereId, color);
            List<DBAlliance> root = alliancesBySphere.computeIfAbsent(sphereId, k -> new ArrayList<>());
            int originalRootSize = root.size();
            for (DBAlliance toAdd : sphere) {
                if (!root.contains(toAdd)) root.add(toAdd);
            }
            if (originalRootSize != 0 && originalRootSize != root.size()) {
                root.sort((o1, o2) -> Double.compare(o2.getScore(), o1.getScore()));
            }

            {
                Set<DBNation> nations = alliance.getNations(true, 7200, true);
                SimpleNationList nationList = new SimpleNationList(nations);
                sphereAllianceMembers.computeIfAbsent(sphereId, f -> new HashMap<>()).put(alliance.getAlliance_id(), nationList);
            }

            if (!sphereScore.containsKey(sphereId)) {
                List<DBNation> nations = new ArrayList<>();
                for (DBAlliance other : sphere) {
                    nations.addAll(other.getNations(true, 7200, true));
                }
                SimpleNationList nationList = new SimpleNationList(nations);

                sphereScore.put(sphereId, nationList.getScore());
                if (sphere.size() > 1) {
                    sphereAllianceMembers.computeIfAbsent(sphereId, f -> new HashMap<>()).put(0, nationList);
                }
                sphereNames.put(sphereId, PW.getSphereName(sphereId));
            }

            String colorStr = "#" + Integer.toHexString(color.getRGB()).substring(2);
        }

        this.spheresRanked = new ArrayList<>(sphereScore.keySet());
        spheresRanked.sort((o1, o2) -> Double.compare(sphereScore.get(o2), sphereScore.get(o1)));

        Map<Integer, Integer> sphereByAA = new HashMap<>();
        for (Map.Entry<Integer, List<DBAlliance>> entry : alliancesBySphere.entrySet()) {
            for (DBAlliance alliance : entry.getValue()) {
                sphereByAA.put(alliance.getId(), entry.getKey());
            }

        }

    }

    public Map.Entry<Integer, List<DBAlliance>> getSphere(DBAlliance alliance) {
        for (Map.Entry<Integer, List<DBAlliance>> entry : alliancesBySphere.entrySet()) {
            if (entry.getValue().contains(alliance)) {
                return new KeyValue<>(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
        }
        return null;
    }

    public DBAlliance getAlliance(int allianceId) {
        return alliances.get(allianceId);
    }

    public Map<Integer, DBAlliance> getAlliancesMap() {
        return alliances;
    }

    public Set<DBAlliance> getAlliances() {
        return new HashSet<>(alliances.values());
    }

    public String getSphereName(int sphereId) {
        return sphereNames.get(sphereId);
    }

    /**
     * 0 = sphere id
     * the rest of the keys are alliance ids
     *
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

    public List<DBAlliance> getAlliances(int sphereId) {
        return alliancesBySphere.get(sphereId);
    }

    public List<Integer> getSpheres() {
        return spheresRanked;
    }


}
