package link.locutus.discord.db.handlers;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import link.locutus.discord.db.entities.DBWar;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class ActiveWarHandler {
    private final Map<Integer, DBWar[]> activeWars = new Int2ObjectOpenHashMap<>();

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
        if (!war.isActive()) return;
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
        return getActiveWars();
    }

    public Map<Integer, DBWar> getActiveWars(Predicate<Integer> nationId, Predicate<DBWar> warPredicate) {
        Map<Integer, DBWar> result = new Int2ObjectOpenHashMap<>();
        synchronized (activeWars) {
            for (Map.Entry<Integer, DBWar[]> entry : activeWars.entrySet()) {
                if (nationId == null || nationId.test(entry.getKey())) {
                    for (DBWar war : entry.getValue()) {
                        if (warPredicate == null || warPredicate.test(war)) {
                            result.put(war.warId, war);
                        }
                    }
                }
            }
        }
        return result;
    }

    public Map<Integer, DBWar> getActiveWars() {
        Int2ObjectOpenHashMap<DBWar> result = new Int2ObjectOpenHashMap<>();
        synchronized (activeWars) {
            for (DBWar[] nationWars : activeWars.values()) {
                for (DBWar war : nationWars) {
                    result.put(war.warId, war);
                }
            }
        }
        return result;
    }

    public List<DBWar> getActiveWars(int nationId) {
        synchronized (activeWars) {
            DBWar[] wars = activeWars.get(nationId);
            return wars == null ? Collections.emptyList() : Arrays.asList(wars);
        }
    }

    public DBWar getWar(int warId) {
        synchronized (activeWars) {
            for (DBWar[] nationWars : activeWars.values()) {
                for (DBWar war : nationWars) {
                    if (war.warId == warId) return war;
                }
            }
        }
        return null;
    }
}
