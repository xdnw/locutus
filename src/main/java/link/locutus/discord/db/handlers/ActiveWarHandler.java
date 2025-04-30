package link.locutus.discord.db.handlers;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AbstractCursor;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.SuccessType;
import link.locutus.discord.db.WarDB;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.event.Event;
import link.locutus.discord.event.nation.NationBlockadedEvent;
import link.locutus.discord.event.nation.NationUnblockadedEvent;
import link.locutus.discord.util.TimeUtil;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class ActiveWarHandler {
    private final Map<Integer, DBWar[]> activeWars = new Int2ObjectOpenHashMap<>();
    private final WarDB warDB;
    private volatile int numActiveWars = 0;

    public ActiveWarHandler(WarDB warDB) {
        this.warDB = warDB;
    }

    private void makeWarInactive(int nationId, int warId) {
        synchronized (activeWars) {
            DBWar[] wars = activeWars.get(nationId);
            if (wars != null && wars.length > 0) {
                Set<DBWar> newWars = new HashSet<>(Arrays.asList(wars));
                int originalSize = newWars.size();
                if (newWars.removeIf(f -> f.getWarId() == warId)) {
                    if (newWars.isEmpty()) {
                        activeWars.remove(nationId);
                    } else {
                        activeWars.put(nationId, newWars.toArray(new DBWar[0]));
                    }
                    numActiveWars += newWars.size() - originalSize;
                }
            }
        }
    }

    public boolean isEmpty() {
        return activeWars.isEmpty();
    }
    public void makeWarInactive(DBWar war) {
        makeWarInactive(war.getAttacker_id(), war.warId);
        makeWarInactive(war.getDefender_id(), war.warId);
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
            int originalSize = newWars.size();
            newWars.removeIf(f -> f.getWarId() == war.getWarId());
            newWars.add(war);
            activeWars.put(nationId, newWars.toArray(new DBWar[0]));
            numActiveWars += newWars.size() - originalSize;
        }
    }
    public void addActiveWar(DBWar war) {
        addActiveWar(war.getAttacker_id(), war);
        addActiveWar(war.getDefender_id(), war);
    }

    public ObjectOpenHashSet<DBWar> getActiveWarsById() {
        ObjectOpenHashSet<DBWar> result = new ObjectOpenHashSet<>(numActiveWars >> 1);
        synchronized (activeWars) {
            for (DBWar[] nationWars : activeWars.values()) {
                result.addAll(Arrays.asList(nationWars));
            }
        }
        return result;
    }

    public void iterateActiveWars(Consumer<DBWar> consumer) {
        synchronized (activeWars) {
            for (DBWar[] nationWars : activeWars.values()) {
                for (DBWar war : nationWars) {
                    consumer.accept(war);
                }
            }
        }
    }

    public ObjectOpenHashSet<DBWar> getActiveWars(Predicate<Integer> nationId, Predicate<DBWar> warPredicate) {
        ObjectOpenHashSet<DBWar> result = new ObjectOpenHashSet<>();
        synchronized (activeWars) {
            for (Map.Entry<Integer, DBWar[]> entry : activeWars.entrySet()) {
                if (nationId == null || nationId.test(entry.getKey())) {
                    for (DBWar war : entry.getValue()) {
                        if (warPredicate == null || warPredicate.test(war)) {
                            result.add(war);
                        }
                    }
                }
            }
        }
        return result;
    }

    public ObjectOpenHashSet<DBWar> getActiveWars() {
        return getActiveWarsById();
    }

    public Set<DBWar> getActiveWars(int nationId) {
        synchronized (activeWars) {
            DBWar[] wars = activeWars.get(nationId);
            return wars == null ? Collections.emptySet() : new ObjectOpenHashSet<>(wars);
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

    private final Object blockadeLock = new Object();
    private final Map<Integer, Map<Integer, Long>> defenderToBlockader = new Int2ObjectOpenHashMap<>();
    private final Map<Integer, Map<Integer, Long>> blockaderToDefender = new Int2ObjectOpenHashMap<>();

    public Set<Integer> getNationsBlockadedBy(int nationId) {
        Set<DBWar> wars = getActiveWars(nationId);
        if (wars.isEmpty()) return Collections.emptySet();
        synchronized (blockadeLock) {
            // nations that are blockaded by nationId
            Map<Integer, Long> map = blockaderToDefender.getOrDefault(nationId, Collections.emptyMap());
            if (map.isEmpty()) return Collections.emptySet();

            Set<Integer> result = new IntOpenHashSet(Math.min(map.size(), wars.size()));
            for (DBWar war : wars) {
                int otherId = war.isAttacker(nationId) ? war.getDefender_id() : war.getAttacker_id();
                if (map.containsKey(otherId)) {
                    result.add(otherId);
                }
            }
            return result;
        }
    }

    public Set<Integer> getNationsBlockading(int nationId) {
        Set<DBWar> wars = getActiveWars(nationId);
        if (wars.isEmpty()) return Collections.emptySet();
        synchronized (blockadeLock) {
            // nations that are blockading nationId
            Map<Integer, Long> map = defenderToBlockader.getOrDefault(nationId, Collections.emptyMap());
            if (map.isEmpty()) return Collections.emptySet();

            Set<Integer> result = new IntOpenHashSet(Math.min(map.size(), wars.size()));
            for (DBWar war : wars) {
                int otherId = war.isAttacker(nationId) ? war.getDefender_id() : war.getAttacker_id();
                if (map.containsKey(otherId)) {
                    result.add(otherId);
                }
            }
            return result;
        }
    }

    public void processWarChange(DBWar previous, DBWar current, Consumer<Event> eventConsumer) {
        if (!current.isActive()) {
            // remove from blockadedMap
            // remove from blockaderMap
            synchronized (blockadeLock) {
                long now = System.currentTimeMillis();
                removeBlockade(previous.getAttacker_id(), previous.getDefender_id(), Long.MAX_VALUE, eventConsumer);
                removeBlockade(previous.getDefender_id(), previous.getAttacker_id(), Long.MAX_VALUE, eventConsumer);
            }
        }
    }

    public void processAttackChange(AbstractCursor attack, Consumer<Event> eventConsumer) {
        if (attack.getAttack_type() == AttackType.VICTORY) {
            removeBlockade(attack.getAttacker_id(), attack.getDefender_id(), attack.getDate(), eventConsumer);
            removeBlockade(attack.getDefender_id(), attack.getAttacker_id(), attack.getDate(), eventConsumer);
            // remove blockade
        } else if (attack.getAttack_type() == AttackType.NAVAL) {
            if (attack.getSuccess() == SuccessType.IMMENSE_TRIUMPH) {
                addBlockade(attack.getAttacker_id(), attack.getDefender_id(), attack.getDate(), eventConsumer);
            } else if (attack.getSuccess() != SuccessType.UTTER_FAILURE) {
                removeBlockade(attack.getAttacker_id(), attack.getDefender_id(), attack.getDate(), eventConsumer);
            }
        }
    }

    public void removeAllBlockades(int blockaderId, long date, Consumer<Event> eventConsumer) {
        List<Event> events = null;
        synchronized (blockadeLock) {
            Map<Integer, Long> defenders2 = blockaderToDefender.get(blockaderId);
            if (defenders2 != null && !defenders2.isEmpty()) {
                Iterator<Map.Entry<Integer, Long>> iter = defenders2.entrySet().iterator();
                while (iter.hasNext()) {
                    Map.Entry<Integer, Long> entry = iter.next();
                    int defenderId = entry.getKey();
                    if (entry.getValue() <= date) {
                        iter.remove();
                        Map<Integer, Long> existing = defenderToBlockader.get(defenderId);
                        if (existing != null) {
                            existing.remove(blockaderId);
                            if (existing.isEmpty()) {
                                defenderToBlockader.remove(defenderId);
                                if (eventConsumer != null) {
                                    if (events == null) events = new LinkedList<>();
                                    events.add(new NationUnblockadedEvent(defenderId, blockaderId));
                                }
                            }
                        }
                    }
                    if (defenders2.isEmpty()) {
                        blockaderToDefender.remove(blockaderId);
                        break;
                    }
                }
            }
        }
        if (events != null) events.forEach(eventConsumer);
    }

    public void addBlockade(int attacker, int defender, long date, Consumer<Event> eventConsumer) {
        List<Event> events = null;
        synchronized (blockadeLock) {
            // get the nations defender is blockading and remove those before date
            removeAllBlockades(defender, date, eventConsumer);
            // add it to both maps only if greater than existing date, merge max?

            Map<Integer, Long> existing = defenderToBlockader.get(defender);
            if (existing == null || existing.isEmpty()) {
                if (eventConsumer != null) {
                    events = new LinkedList<>();
                    events.add(new NationBlockadedEvent(defender, attacker));
                }
            }
            defenderToBlockader.computeIfAbsent(defender, k -> new Int2ObjectOpenHashMap<>()).merge(attacker, date, Math::max);
            blockaderToDefender.computeIfAbsent(attacker, k -> new Int2ObjectOpenHashMap<>()).merge(defender, date, Math::max);
        }
        if (events != null) events.forEach(eventConsumer);
    }

    public void removeBlockade(int defender, int attacker, long date, Consumer<Event> eventConsumer) {
        Event event = null;
        synchronized (blockadeLock) {
            // remove from both maps, only if the date >= existing date
            Map<Integer, Long> defenders = blockaderToDefender.get(attacker);
            if (defenders != null) {
                Long existingDate = defenders.get(defender);
                if (existingDate != null && existingDate <= date) {
                    defenders.remove(defender);

                    Map<Integer, Long> defenderMap = defenderToBlockader.get(defender);
                    if (defenderMap != null) {
                        defenderMap.remove(attacker);
                        if (defenderMap.isEmpty()) {
                            defenderToBlockader.remove(defender);
                            if (eventConsumer != null) {
                                event = new NationUnblockadedEvent(defender, attacker);
                            }
                        }
                    }
                }
            }
        }
        if (event != null) eventConsumer.accept(event);
    }

    public void syncBlockades() {
        long now = System.currentTimeMillis();
        Map<Integer, DBWar> wars = warDB.getWarsSince(TimeUtil.getTimeFromTurn(TimeUtil.getTurn() - 120));
        ObjectOpenHashSet<DBWar> activeWars2 = getActiveWarsById();
        AttackQuery builder = warDB.queryAttacks().withWars(wars).withType(AttackType.NAVAL).afterDate(now - TimeUnit.DAYS.toMillis(10));
        synchronized (blockadeLock) {
            defenderToBlockader.clear();
            blockaderToDefender.clear();
            builder.iterateAttacks((war, attack) -> {
                if (attack.getAttack_type() != AttackType.NAVAL) return;
                boolean isWarActive = activeWars2.contains(new DBWar.DBWarKey(attack.getWar_id()));

                if (attack.getSuccess() == SuccessType.IMMENSE_TRIUMPH) {
                    if (isWarActive) {
                        addBlockade(attack.getAttacker_id(), attack.getDefender_id(), attack.getDate(), null);
                    } else {
                        removeAllBlockades(attack.getDefender_id(), attack.getDate(), null);
                    }
                } else if (attack.getSuccess() != SuccessType.UTTER_FAILURE) {
                    removeBlockade(attack.getAttacker_id(), attack.getDefender_id(), attack.getDate(), null);
                }
            });
        }
    }
}
