package link.locutus.discord.db.handlers;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import link.locutus.discord.db.entities.DBWar;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ActiveWarHandler {
    private Map<Integer, DBWar[]> activeWars = new Int2ObjectOpenHashMap<>();

    private void makeWarInactive(int nationId, int warId) {
        synchronized (activeWars) {
            DBWar[] wars = activeWars.get(nationId);
            if (wars != null && wars.length > 0) {
                Set<DBWar> newWars = new HashSet<>(Arrays.asList(wars));
                newWars.removeIf(f -> f.getWarId() == warId);
                if (newWars.isEmpty()) {
                    activeWars.remove(nationId);
                } else {
                    activeWars.put(nationId, newWars.toArray(new DBWar[0]));
                }
            }
        }
    }

    public boolean isEmpty() {
        return activeWars.isEmpty();
    }
    public void makeWarInactive(DBWar war) {
        makeWarInactive(war.attacker_id, war.warId);
        makeWarInactive(war.defender_id, war.warId);
    }

    public DBWar getWar(int nationId, int warId) {
        synchronized (activeWars) {
            DBWar[] wars = activeWars.get(nationId);
            if (wars != null) {
                for (DBWar war : wars) {
                    if (war.warId == warId) return war;
                }
            }
        }
        return null;
    }

    private void addActiveWar(int nationId, DBWar war) {
        synchronized (activeWars) {
            DBWar[] wars = activeWars.get(nationId);
            if (wars == null) wars = new DBWar[0];
            Set<DBWar> newWars = new HashSet<>(Arrays.asList(wars));
            newWars.removeIf(f -> f.getWarId() == war.getWarId());
            newWars.add(war);
            activeWars.put(nationId, newWars.toArray(new DBWar[0]));
        }
    }
    public void addActiveWar(DBWar war) {
        addActiveWar(war.attacker_id, war);
        addActiveWar(war.defender_id, war);
    }

    public Map<Integer, DBWar> getActiveWarsById() {
        return getActiveWars().stream().collect(Collectors.toMap(DBWar::getWarId, Function.identity()));
    }

    public Set<DBWar> getActiveWars() {
        Set<DBWar> wars = new LinkedHashSet<>();
        synchronized (activeWars) {
            for (DBWar[] nationWars : activeWars.values()) {
                wars.addAll(Arrays.asList(nationWars));
            }
        }
        return wars;
    }

    public List<DBWar> getActiveWars(int nationId) {
        synchronized (activeWars) {
            DBWar[] wars = activeWars.get(nationId);
            return wars == null ? Collections.emptyList() : Arrays.asList(wars);
        }
    }
}
