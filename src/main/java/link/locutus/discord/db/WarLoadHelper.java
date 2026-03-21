package link.locutus.discord.db;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import link.locutus.discord.db.entities.DBWar;

import java.util.List;

public final class WarLoadHelper {

    private final Int2ObjectOpenHashMap<Object> warsByAllianceId;
    private final Int2ObjectOpenHashMap<Object> warsByNationId;
    private final ObjectOpenHashSet<DBWar> warsById;

    public WarLoadHelper(
            Int2ObjectOpenHashMap<Object> warsByAllianceId,
            Int2ObjectOpenHashMap<Object> warsByNationId,
            ObjectOpenHashSet<DBWar> warsById
    ) {
        this.warsByAllianceId = warsByAllianceId;
        this.warsByNationId = warsByNationId;
        this.warsById = warsById;
    }

    public void setWars(List<DBWar> allWars, boolean clear) {
        if (clear) {
            warsById.clear();
            warsByAllianceId.clear();
            warsByNationId.clear();
        }

        final int n = allWars.size();
        Int2IntOpenHashMap numWarsByAlliance = new Int2IntOpenHashMap(Math.max(16, n));
        Int2IntOpenHashMap numWarsByNation = new Int2IntOpenHashMap(Math.max(16, n * 2));

        for (DBWar war : allWars) {
            final int attackerAllianceId = war.getAttacker_aa();
            final int defenderAllianceId = war.getDefender_aa();
            final int attackerNationId = war.getAttacker_id();
            final int defenderNationId = war.getDefender_id();

            if (attackerAllianceId != 0) numWarsByAlliance.addTo(attackerAllianceId, 1);
            if (defenderAllianceId != 0) numWarsByAlliance.addTo(defenderAllianceId, 1);
            numWarsByNation.addTo(attackerNationId, 1);
            numWarsByNation.addTo(defenderNationId, 1);
        }

        warsById.addAll(allWars);

        for (DBWar war : allWars) {
            final int attackerNationId = war.getAttacker_id();
            final int defenderNationId = war.getDefender_id();
            final int attackerAllianceId = war.getAttacker_aa();
            final int defenderAllianceId = war.getDefender_aa();

            setWar(war, attackerNationId, numWarsByNation.get(attackerNationId), warsByNationId);
            setWar(war, defenderNationId, numWarsByNation.get(defenderNationId), warsByNationId);

            if (attackerAllianceId != 0) {
                setWar(war, attackerAllianceId, numWarsByAlliance.get(attackerAllianceId), warsByAllianceId);
            }
            if (defenderAllianceId != 0) {
                setWar(war, defenderAllianceId, numWarsByAlliance.get(defenderAllianceId), warsByAllianceId);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void setWar(DBWar war, int id, int size, Int2ObjectOpenHashMap<Object> map) {
        if (size == 1) {
            map.put(id, war);
        } else {
            Object o = map.get(id);
            if (o instanceof ObjectOpenHashSet<?> set) {
                ((ObjectOpenHashSet<DBWar>) set).add(war);
            } else if (o == null) {
                ObjectOpenHashSet<Object> set = new ObjectOpenHashSet<>(size);
                set.add(war);
                map.put(id, set);
            } else if (o instanceof DBWar oldWar) {
                throw new IllegalStateException("Multiple wars for " + id + ": " + oldWar + " and " + war);
            } else {
                throw new IllegalStateException("Unknown object for " + id + ": " + o);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void addElement(Int2ObjectOpenHashMap<Object> map, int id, DBWar war) {
        Object existing = map.get(id);
        if (existing == null) {
            map.put(id, war);
            return;
        }
        if (existing instanceof ObjectOpenHashSet<?> set) {
            ((ObjectOpenHashSet<DBWar>) set).add(war);
            return;
        }
        if (existing instanceof DBWar existingWar) {
            if (existingWar.equals(war)) {
                map.put(id, war);
                return;
            }
            ObjectOpenHashSet<DBWar> set = new ObjectOpenHashSet<>(2);
            set.add(existingWar);
            set.add(war);
            map.put(id, set);
            return;
        }
        throw new IllegalStateException("Unexpected type for id " + id + ": " + existing.getClass());
    }
}