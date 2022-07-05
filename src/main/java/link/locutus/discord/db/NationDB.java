package link.locutus.discord.db;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.politicsandwar.graphql.model.*;
import com.ptsmods.mysqlw.query.builder.SelectBuilder;
import com.ptsmods.mysqlw.table.ColumnType;
import com.ptsmods.mysqlw.table.TablePreset;
import it.unimi.dsi.fastutil.ints.*;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.domains.subdomains.SNationContainer;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.apiv3.enums.AlliancePermission;
import link.locutus.discord.commands.rankings.builder.*;
import link.locutus.discord.db.entities.DBCity;
import link.locutus.discord.apiv3.PoliticsAndWarV3;
import link.locutus.discord.db.entities.*;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.Treaty;
import link.locutus.discord.event.Event;
import link.locutus.discord.event.alliance.AllianceCreateEvent;
import link.locutus.discord.event.alliance.AllianceDeleteEvent;
import link.locutus.discord.event.city.CityCreateEvent;
import link.locutus.discord.event.city.CityDeleteEvent;
import link.locutus.discord.event.city.CityInfraBuyEvent;
import link.locutus.discord.event.city.CityInfraDamageEvent;
import link.locutus.discord.event.nation.*;
import link.locutus.discord.event.position.PositionCreateEvent;
import link.locutus.discord.event.position.PositionDeleteEvent;
import link.locutus.discord.event.treaty.*;
import link.locutus.discord.util.scheduler.ThrowingBiConsumer;
import link.locutus.discord.util.scheduler.ThrowingConsumer;
import link.locutus.discord.util.*;
import link.locutus.discord.util.math.ArrayUtil;
import link.locutus.discord.apiv1.domains.subdomains.SCityContainer;
import link.locutus.discord.apiv1.enums.Continent;
import link.locutus.discord.apiv1.enums.DomesticPolicy;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.NationColor;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv1.enums.TreatyType;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.apiv1.enums.city.JavaCity;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;

public class NationDB extends DBMainV2 {
    private final Map<Integer, DBNation> nationsById = new Int2ObjectOpenHashMap<>();
    private final Map<Integer, Map<Integer, DBNation>> nationsByAlliance = new Int2ObjectOpenHashMap<>();
    private final Map<Integer, DBAlliance> alliancesById = new Int2ObjectOpenHashMap<>();
    private final Map<Integer, Map<Integer, DBCity>> citiesByNation = new Int2ObjectOpenHashMap<>();
    private final Map<Integer, DBAlliancePosition> positionsById = new Int2ObjectOpenHashMap<>();
    private final Map<Integer, Map<Integer, DBAlliancePosition>> positionsByAllianceId = new Int2ObjectOpenHashMap<>();
    private final Map<Integer, Map<Integer, Treaty>> treatiesByAlliance = new Int2ObjectOpenHashMap<>();
    private final Set<Integer> dirtyCities = Collections.synchronizedSet(new LinkedHashSet<>());
    private final Set<Integer> dirtyNations = Collections.synchronizedSet(new LinkedHashSet<>());

    public NationDB() throws SQLException, ClassNotFoundException {
        super("nations");

        { // Legacy
            updateLegacyNations();
            executeStmt("DROP TABLE TREATIES"); // legacy, use treaties2
            executeStmt("DROP TABLE CITY_INFRA_LAND");
        }

        loadPositions();
        loadAlliances();
        loadNations();
        loadCities();
        loadTreaties();
    }

    public void deleteExpiredTreaties(Consumer<Event> eventConsumer) {
        Set<Treaty> expiredTreaties = new HashSet<>();
        long currentTurn = TimeUtil.getTurn();
        synchronized (treatiesByAlliance) {
            for (Map<Integer, Treaty> allianceTreaties : treatiesByAlliance.values()) {
                for (Treaty treaty : allianceTreaties.values()) {
                    if (treaty.getTurnEnds() <= currentTurn) {
                        expiredTreaties.add(treaty);
                    }
                }
            }
        }

        if (!expiredTreaties.isEmpty()) {
            for (Treaty treaty : expiredTreaties) {
                if (eventConsumer != null) {
                    eventConsumer.accept(new TreatyExpireEvent(treaty));
                }
            }
            deleteTreaties(expiredTreaties);
        }
    }

    public DBCity getDBCity(int nationId, int cityId) {
        synchronized (citiesByNation) {
            Map<Integer, DBCity> nationCities = citiesByNation.get(nationId);
            if (nationCities != null) return nationCities.get(cityId);
        }
        return null;
    }

    public boolean updateNationUnits(int nationId, long timestamp, Map<MilitaryUnit, Integer> losses, Consumer<Event> eventConsumer) {
        DBNation nation = getNation(nationId);
        if (nation != null) {
            long lastUpdatedMs = nation.getLastFetchedUnitsMs();
            if (lastUpdatedMs < timestamp) {
                for (Map.Entry<MilitaryUnit, Integer> entry : losses.entrySet()) {
                    int amt = Math.max(0, nation.getUnits(entry.getKey()) - entry.getValue());
                    DBNation copyOriginal = eventConsumer == null ? null : new DBNation(nation);
                    nation.setUnits(entry.getKey(), amt);
                    if (eventConsumer != null) eventConsumer.accept(new NationChangeUnitEvent(copyOriginal, nation, entry.getKey()));
                    return true;
                }
                nation.setLastFetchedUnitsMs(timestamp);
            }
        }
        return false;
    }

    public boolean setNationActive(int nationId, long active, Consumer<Event> eventConsumer) {
        DBNation nation = getNation(nationId);
        if (nation != null) {
            if (nation.lastActiveMs() < active) {
                DBNation previous = eventConsumer == null ? null : new DBNation(nation);
                nation.setLastActive(active);

                // only call a new event if it's > 1 minute difference
                if (nation.lastActiveMs() < active - TimeUnit.MINUTES.toMillis(1)) {
                    if (eventConsumer != null) eventConsumer.accept(new NationChangeActiveEvent(previous, nation));
                }
                return true;
            }
        }
        return false;
    }

    public boolean setCityInfraFromAttack(int nationId, int cityId, double infra, long timestamp, Consumer<Event> eventConsumer) {
        DBCity city = getDBCity(nationId, cityId);
        if (city != null && city.fetched < timestamp && infra != city.infra) {
            DBCity previous = new DBCity(city);
            city.infra = infra;
            if (eventConsumer != null) {
                if (infra < previous.infra) {
                    eventConsumer.accept(new CityInfraBuyEvent(nationId, previous, city));
                } else if (infra > previous.infra) {
                    eventConsumer.accept(new CityInfraDamageEvent(nationId, previous, city));
                }
            }
            return true;
        }
        return false;
    }

    public void markNationDirty(int nationId) {
        dirtyNations.add(nationId);
    }

    public void markCityDirty(int nationId, int cityId, long timestamp) {
        DBCity city = getDBCity(nationId, cityId);
        if (city.fetched > timestamp) return;

        synchronized (dirtyCities) {
            dirtyCities.add(cityId);
        }
    }

    public void updateLegacyNations() throws SQLException {
        List<DBNation> nationsToSave = new ArrayList<>();
        SelectBuilder builder = getDb().selectBuilder("NATIONS").select("*");
        try (ResultSet rs = builder.executeRaw()) {
            while (rs.next()) {
                DBNation nation = createNationLegacy(rs);
                nationsToSave.add(nation);
            }
        }
        for (DBNation nation : nationsToSave) {
            if (nation.getVm_turns() > 0 || nation.getAlliance_id() != 0 || nation.active_m() > 10000 || nation.getPosition() <= 1) continue;
            Map<Integer, Long> dcTimes = nation.findDayChange();
            if (dcTimes.size() == 1) {
                int turn = dcTimes.keySet().iterator().next();
                nation.setDc_turn(turn);
            }
        }
        saveNations(nationsToSave);

        executeStmt("DROP TABLE NATIONS");
    }

    private void updateLoop(boolean turnChange) {
        long now = System.currentTimeMillis();
        long turn = TimeUtil.getTurn(now);

        List<Event> events = new ArrayList<>();
        Consumer<Event> eventConsumer = events::add;

        try {
            List<Future> updateTask = new ArrayList<>();

            if (nationsById.isEmpty()) {
                updateAllNations( null);
                updateOutdatedAlliances(true, null);
                updateTreaties(null);
            } else {
                if (turnChange) {
                    // update all nations v2 (including vm)
//                    updateNations(f -> {}, eventConsumer);
                    // update all alliances
                    // updateAlliances(eventConsumer);
                    updateTreaties(eventConsumer);
                    saveAllCities(); // TODO save all cities
                } else if (TimeUtil.getTurn(now + TimeUnit.MINUTES.toMillis(3)) != turn) {
                    // Don't update right before turn change
                    if (TimeUtil.getTurn(now + TimeUnit.SECONDS.toMillis(10)) != turn) {
                        return;
                    }
                    // only update active nations during turn change
                    updateMostActiveNations(500, eventConsumer);
                } else {
                    // update active nations
                        // Have a portion go towards updating new nations

                    // every 5 minutes
                        // update nations v2 (non vm)
                        // Update colored nations
                        // update treaties

                    // update new nations

                    // update new cities

                    // update new alliances

                    /*
                    updateColoredNations(eventConsumer);

        updateNewNations();
        updateNewCities();
        updateOutdatedAlliances();
        updateNewAlliances()
        updateTreaties(true);

        deleteVacantAlliances();
                     */
                }
            }
            for (Future future : updateTask) {
                future.get();
            }
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            Locutus.imp().getExecutor().submit(() -> {
                for (Event event : events) {
                    event.post();
                }
            });
        }
    }

    private List<Integer> getActiveAlliancesToUpdate(int amount, Rank requiredRank, AlliancePermission... requiredPerms) {
        if (amount <= 0) return new ArrayList<>();

        Set<Integer> allianceIds = new HashSet<>();
        List<Map.Entry<Integer, Long>> allianceIdActiveMs = new ArrayList<>();
        synchronized (nationsByAlliance) {
            for (Map<Integer, DBNation> entry : nationsByAlliance.values()) {
                middle:
                for (DBNation nation : entry.values()) {
                    if (nation.getAlliance_id() == 0) continue;
                    if (nation.getPositionEnum().id < requiredRank.id) continue;
                    if (nation.getVm_turns() > 0) continue;
                    DBAlliance alliance = nation.getAlliance(false);
                    if (alliance != null) {
                        if (alliance.getLastUpdated() > nation.lastActiveMs()) continue;
                        for (AlliancePermission perm : requiredPerms) {
                            DBAlliancePosition position = nation.getAlliancePosition();
                            if (!position.hasPermission(perm)) continue middle;
                        }
                    }
                    if (allianceIds.add(nation.getAlliance_id())) {
                        allianceIdActiveMs.add(Map.entry(nation.getAlliance_id(), nation.lastActiveMs()));
                    }
                }
            }
        }
        allianceIdActiveMs.sort((o1, o2) -> Long.compare(o2.getValue(), o1.getValue()));
        if (allianceIdActiveMs.size() > amount) allianceIdActiveMs = allianceIdActiveMs.subList(0, amount);
        return allianceIdActiveMs.stream().map(Map.Entry::getKey).collect(Collectors.toList());
    }

    public Set<Integer> updateActiveAlliances(long activeSince, Rank requiredRank, Consumer<Event> eventConsumer) {
        Set<Integer> activeAlliances = new HashSet<>();
        synchronized (nationsByAlliance) {
            for (Map<Integer, DBNation> entry : nationsByAlliance.values()) {
                for (DBNation nation : entry.values()) {
                    if (nation.getAlliance_id() == 0) continue;
                    if (nation.lastActiveMs() < activeSince) continue;
                    if (nation.getPositionEnum().id < requiredRank.id) continue;
                    activeAlliances.add(nation.getAlliance_id());
                }
            }
        }
        return updateAlliancesById(new ArrayList<>(activeAlliances), eventConsumer);
    }

    private int lastNewAllianceFetched = 0;

    public Set<Integer> updateNewAlliances(Consumer<Event> eventConsumer) {
        return updateNewAlliances(Collections.emptySet(), eventConsumer);
    }

    public Set<Integer> updateNewAlliances(Set<Integer> alsoFetch, Consumer<Event> eventConsumer) {
        Set<Integer> newIds = new LinkedHashSet<>(alsoFetch);

        int pad = PoliticsAndWarV3.ALLIANCES_PER_PAGE - newIds.size() % PoliticsAndWarV3.ALLIANCES_PER_PAGE;
        if (pad != PoliticsAndWarV3.ALLIANCES_PER_PAGE || newIds.isEmpty()) {
            int maxSize = newIds.size() + pad;
            synchronized (alliancesById) {
                int maxId = 0;
                for (int id : alliancesById.keySet()) {
                    maxId = Math.max(maxId, id);
                }
                if (lastNewAllianceFetched == 0) {
                    lastNewAllianceFetched = maxId;
                } else {
                    lastNewAllianceFetched = Math.min(maxId, lastNewAllianceFetched);
                }
            }
            while (newIds.size() < maxSize) {
                int id = ++lastNewAllianceFetched;
                if (getAlliance(id) == null) newIds.add(id);
            }
        }
        return updateAlliancesById(new ArrayList<>(newIds), eventConsumer);
    }

    private Set<Integer> updateAlliancesById(List<Integer> ids, Consumer<Event> eventConsumer) {
        PoliticsAndWarV3 v3 = Locutus.imp().getPnwApi().getV3();

        Set<Integer> fetched = new LinkedHashSet<>();
        if (ids.isEmpty()) return fetched;
        for (int i = 0; i < ids.size(); i++) {
            int end = Math.min(i + 500, ids.size());
            List<Integer> toFetch = ids.subList(i, end);


            List<Alliance> alliances = v3.fetchAlliances(req -> req.setId(toFetch), true, true);
            fetched.addAll(updateAlliances(alliances, eventConsumer));
        }

        // delete alliances not returned
        Set<Integer> toDelete = new HashSet<>(ids);
        toDelete.removeAll(fetched);
        if (!toDelete.isEmpty()) deleteAlliances(toDelete, eventConsumer);

        return fetched;
    }

    private void deleteAlliances(Set<Integer> ids, Consumer<Event> eventConsumer) {
        Set<Treaty> treatiesToDelete = new LinkedHashSet<>();
        Set<Integer> positionsToDelete = new LinkedHashSet<>();

        for (int id : ids) {
            DBAlliance alliance = getAlliance(id);
            if (alliance != null && eventConsumer != null) {
                eventConsumer.accept(new AllianceDeleteEvent(alliance));
            }
            synchronized (alliancesById) {
                alliancesById.remove(id);
            }
            synchronized (treatiesByAlliance) {
                Map<Integer, Treaty> removedTreaties = treatiesByAlliance.remove(id);
                if (removedTreaties != null && !removedTreaties.isEmpty()) {
                    treatiesToDelete.addAll(removedTreaties.values());
                }
            }
        }

        if (!positionsToDelete.isEmpty()) deletePositions(positionsToDelete, null);
        if (!treatiesToDelete.isEmpty()) deleteTreaties(treatiesToDelete);

        deleteAlliancesInDB(ids);
    }

    public void deleteTreaties(Set<Treaty> treaties) {
        for (Treaty treaty : treaties) {
            synchronized (treatiesByAlliance) {
                treatiesByAlliance.getOrDefault(treaty.getFromId(), Collections.EMPTY_MAP).remove(treaty.getId());
                treatiesByAlliance.getOrDefault(treaty.getToId(), Collections.EMPTY_MAP).remove(treaty.getId());
            }
        }
        Set<Integer> ids = treaties.stream().map(f -> f.getId()).collect(Collectors.toSet());
        deleteTreatiesInDB(ids);
    }

    private void deleteAlliancesInDB(Set<Integer> ids) {
        if (ids.isEmpty()) return;
        if (ids.size() == 1) {
            int id = ids.iterator().next();
            executeStmt("DELETE FROM ALLIANCES WHERE id = " + id);
        } else {
            executeStmt("DELETE FROM ALLIANCES WHERE `id` in " + StringMan.getString(ids));
        }
    }

    private Set<Integer> updateAlliances(List<Alliance> alliances, Consumer<Event> eventConsumer) {
        if (alliances.isEmpty()) return Collections.emptySet();

        List<DBAlliance> dirtyAlliances = new ArrayList<>();
        List<DBAlliancePosition> dirtyPositions = new ArrayList<>();

        for (Alliance alliance : alliances) {

            if (alliance.getDate() != null && alliance.getName() != null) { // Essential components of an alliance
                DBAlliance existing;
                synchronized (alliancesById) {
                    existing = alliancesById.get(alliance.getId());
                }
                if (existing == null) {
                    existing = new DBAlliance(alliance);
                    synchronized (alliancesById) {
                        alliancesById.put(alliance.getId(), existing);
                    }
                    if (eventConsumer != null) eventConsumer.accept(new AllianceCreateEvent(existing));
                    dirtyAlliances.add(existing);
                } else {
                    if (existing.set(alliance, eventConsumer)) {
                        dirtyAlliances.add(existing);
                    }
                }
            }

            if (alliance.getAlliance_positions() != null) {
                Set<Integer> positionsToRemove = new HashSet<>();
                synchronized (positionsByAllianceId) {
                    Map<Integer, DBAlliancePosition> existingByAA = positionsByAllianceId.get(alliance.getId());
                    if (existingByAA != null) {
                        positionsToRemove.addAll(existingByAA.keySet());
                    }
                }

                for (AlliancePosition v3Position : alliance.getAlliance_positions()) {
                    synchronized (positionsByAllianceId) {
                        positionsToRemove.remove(v3Position.getId());
                    }
                    DBAlliancePosition existing;
                    synchronized (positionsById) {
                        existing = positionsById.get(v3Position.getId());
                    }
                    if (existing == null) {
                        existing = new DBAlliancePosition(v3Position);
                        synchronized (positionsById) {
                            positionsById.put(v3Position.getId(), existing);
                            positionsByAllianceId.computeIfAbsent(alliance.getId(), f -> new Int2ObjectOpenHashMap<>())
                                    .put(v3Position.getId(), existing);
                        }
                        if (eventConsumer != null) eventConsumer.accept(new PositionCreateEvent(existing));
                        dirtyPositions.add(existing);
                    } else {
                        if (existing.set(v3Position, eventConsumer)) {
                            dirtyPositions.add(existing);
                        }
                    }
                }

                if (!positionsToRemove.isEmpty()) {
                    deletePositions(positionsToRemove, eventConsumer);
                }
            }
        }

        if (!dirtyAlliances.isEmpty()) {
            saveAlliances(dirtyAlliances);
        }
        if (!dirtyPositions.isEmpty()) {
            savePositions(dirtyPositions);
        }

        return alliances.stream().map(f -> f.getId()).collect(Collectors.toSet());
    }

    public void saveAlliances(List<DBAlliance> alliances) {
        executeBatch(alliances, "INSERT OR REPLACE INTO `ALLIANCES`(`id`, `name`, `acronym`, `flag`, `forum_link`, `discord_link`, `wiki_link`, `dateCreated`, `color`) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?)",
                (ThrowingBiConsumer<DBAlliance, PreparedStatement>) (alliance, stmt) -> {
            stmt.setInt(1, alliance.getId());
                    stmt.setString(2, alliance.getName());
                    stmt.setString(3, alliance.getAcronym());
                    stmt.setString(4, alliance.getFlag());
                    stmt.setString(5, alliance.getForum_link());
                    stmt.setString(6, alliance.getDiscord_link());
                    stmt.setString(7, alliance.getWiki_link());
                    stmt.setLong(8, alliance.getDateCreated());
                    stmt.setInt(9, alliance.getColor().ordinal());
        });
    }

    public void savePositions(List<DBAlliancePosition> positions) {
        executeBatch(positions, "INSERT OR REPLACE INTO `POSITIONS`(`id`, `alliance_id`, `name`, `date_created`, `position_level`, `rank`, `permission_bits`) VALUES(?, ?, ?, ?, ?, ?, ?)",
                (ThrowingBiConsumer<DBAlliancePosition, PreparedStatement>) (position, stmt) -> {
            stmt.setInt(1, position.getId());
            stmt.setInt(2, position.getAlliance_id());
            stmt.setString(3, position.getName());
            stmt.setLong(4, position.getDate_created());
            stmt.setInt(5, position.getPosition_level());
            stmt.setInt(6, position.getRank().ordinal());
            stmt.setLong(7, position.getPermission_bits());
        });
    }

    public void saveTreaties(Collection<Treaty> treaties) {
        executeBatch(treaties, "INSERT OR REPLACE INTO `TREATIES2`(`id`, `date`, `type`, `from`, `to`, `turn_ends`) VALUES(?, ?, ?, ?, ?, ?)",
                (ThrowingBiConsumer<Treaty, PreparedStatement>) (treaty, stmt) -> {
                    stmt.setInt(1, treaty.getId());
                    stmt.setLong(2, treaty.getDate());
                    stmt.setInt(3, treaty.getType().ordinal());
                    stmt.setInt(4, treaty.getFromId());
                    stmt.setInt(5, treaty.getToId());
                    stmt.setLong(6, treaty.getTurnEnds());
                });
    }

    private Set<Integer> getUnknownPositionAlliances(boolean positions, boolean alliances) {
        Set<Integer> alliancesToFetch = new LinkedHashSet<>();
//        Map<Integer, Set<Integer>> positionsToFetchByAlliance = new Int2ObjectOpenHashMap<>();
        synchronized (nationsById) {
            for (Map.Entry<Integer, DBNation> entry : nationsById.entrySet()) {
                DBNation nation = entry.getValue();
                int aaId = nation.getAlliance_id();

                if (alliances) {
                    DBAlliance alliance = getAlliance(aaId);
                    if (alliance == null) {
                        alliancesToFetch.add(aaId);
                    }
                }
                if (positions) {
                    int posId = nation.getAlliancePositionId();
                    if (aaId == 0 || posId == 0 || nation.getPositionEnum() == Rank.APPLICANT) continue;
                    DBAlliancePosition existing = getPosition(posId, aaId, false);
                    if (existing == null) {
                        alliancesToFetch.add(aaId);
//                    positionsToFetchByAlliance.computeIfAbsent(aaId, f -> new IntOpenHashSet()).add(posId);
                    }
                }
            }
        }
        return alliancesToFetch;
//        if (positionsToFetchByAlliance.isEmpty()) return Collections.emptySet();
    }

    public Set<Integer> updateOutdatedAlliances(boolean positions, Consumer<Event> eventConsumer) {
        Set<Integer> ids = getUnknownPositionAlliances(positions, true);
        if (ids.isEmpty()) return Collections.emptySet();
        int amt = PoliticsAndWarV3.ALLIANCES_PER_PAGE - ids.size();

        int pad = PoliticsAndWarV3.ALLIANCES_PER_PAGE - ids.size() % PoliticsAndWarV3.ALLIANCES_PER_PAGE;
        if (pad != PoliticsAndWarV3.ALLIANCES_PER_PAGE) {
            // Add most active alliances as padding
            ids.addAll(getActiveAlliancesToUpdate(pad, Rank.MEMBER, AlliancePermission.EDIT_ALLIANCE_INFO, AlliancePermission.CHANGE_PERMISSIONS));
        }

        // Add new alliances as padding
        return updateNewAlliances(ids, eventConsumer);
    }

    public Set<Integer> updateUnknownPositions(Consumer<Event> eventConsumer) {
        Set<Integer> ids = getUnknownPositionAlliances(true, false);
        if (ids.isEmpty()) return Collections.emptySet();
        return updateAlliancesById(new ArrayList<>(ids), eventConsumer);
    }

    public void updateTreaties(Consumer<Event> eventConsumer) {
        PoliticsAndWarV3 v3 = Locutus.imp().getPnwApi().getV3();
        List<com.politicsandwar.graphql.model.Treaty> treatiesV3 = v3.fetchTreaties(r -> {});

        // Don't call events if first time
        if (treatiesByAlliance.isEmpty()) eventConsumer = f -> {};

        updateTreaties(treatiesV3, eventConsumer);
    }

    public void updateTreaties(List<com.politicsandwar.graphql.model.Treaty> treatiesV3, Consumer<Event> eventConsumer) {
        Map<Integer, Map<Integer, Treaty>> treatiesByAACopy = new HashMap<>();
        long turn = TimeUtil.getTurn();
        for (com.politicsandwar.graphql.model.Treaty treaty : treatiesV3) {
            Treaty dbTreaty = new Treaty(treaty);
            if (dbTreaty.getTurnEnds() <= turn) continue;

            DBAlliance fromAA = getAlliance(dbTreaty.getFromId());
            DBAlliance toAA = getAlliance(dbTreaty.getToId());
            if (fromAA == null || toAA == null) continue;
            treatiesByAACopy.computeIfAbsent(fromAA.getId(), f -> new Int2ObjectOpenHashMap<>()).put(toAA.getId(), dbTreaty);
            treatiesByAACopy.computeIfAbsent(toAA.getId(), f -> new Int2ObjectOpenHashMap<>()).put(fromAA.getId(), dbTreaty);
        }

        Set<Integer> allIds = new HashSet<>(treatiesByAACopy.keySet());
        synchronized (treatiesByAlliance) {
            allIds.addAll(treatiesByAlliance.keySet());
        }
        List<Treaty> dirtyTreaties = new ArrayList<>();
        Set<Treaty> toDelete = new LinkedHashSet<>();

        for (int aaId : allIds) {
            Map<Integer, Treaty> previousMap = new HashMap<>(treatiesByAlliance.getOrDefault(aaId, Collections.EMPTY_MAP));
            Map<Integer, Treaty> currentMap = treatiesByAACopy.getOrDefault(aaId, Collections.EMPTY_MAP);

            for (Map.Entry<Integer, Treaty> entry : previousMap.entrySet()) {
                Treaty previous = entry.getValue();
                Treaty current = currentMap.get(entry.getKey());

                int otherId = previous.getFromId() == aaId ? previous.getToId() : previous.getFromId();

                if (current == null) {
                    toDelete.add(previous);
                    if (eventConsumer != null && previous.getFromId() == aaId) {
                        if (previous.getTurnEnds() >= turn) {
                            eventConsumer.accept(new TreatyExpireEvent(previous));
                        } else {
                            eventConsumer.accept(new TreatyCancelEvent(previous));
                        }
                    }
                } else {
                    synchronized (treatiesByAlliance) {
                        treatiesByAlliance.computeIfAbsent(aaId, f -> new Int2ObjectOpenHashMap<>()).put(otherId, current);
                    }
                    if (!current.equals(previous)) {
                        dirtyTreaties.add(current);
                    }
                    if (eventConsumer != null) {
                        if (current.getType() != previous.getType()) {
                            if (current.getType().getStrength() > previous.getType().getStrength()) {
                                eventConsumer.accept(new TreatyUpgradeEvent(previous, current));
                            } else {
                                eventConsumer.accept(new TreatyDowngradeEvent(previous, current));
                            }
                        } else if (current.getTurnEnds() > previous.getTurnEnds() + 1) {
                            eventConsumer.accept(new TreatyExtendEvent(previous, current));
                        }
                    }
                }
            }

            for (Map.Entry<Integer, Treaty> entry : currentMap.entrySet()) {
                int otherAAId = entry.getKey();
                Treaty treaty = entry.getValue();
                if (!previousMap.containsKey(otherAAId)) {
                    dirtyTreaties.add(treaty);

                    synchronized (treatiesByAlliance) {
                        treatiesByAlliance.computeIfAbsent(aaId, f -> new Int2ObjectOpenHashMap<>()).put(otherAAId, treaty);
                    }
                    // Only run event if it's the from alliance (so you dont double run treaty events)
                    if (eventConsumer != null && treaty.getFromId() == aaId) {
                        eventConsumer.accept(new TreatyCreateEvent(treaty));
                    }
                }
            }
        }

        saveTreaties(dirtyTreaties);

        if (!toDelete.isEmpty()) deleteTreaties(toDelete);
    }


    public Set<Integer> updateColoredNations(Consumer<Event> eventConsumer) {
        List<String> colors = new ArrayList<>();
        for (NationColor color : NationColor.values) {
            if (color == NationColor.GRAY || color == NationColor.BEIGE) continue;
            colors.add(color.name().toLowerCase(Locale.ROOT));
        }
        return updateNations(r -> {
            r.setVmode(false);
            r.setColor(colors);
        }, true, true, true, true, true, eventConsumer);
    }

    public List<Integer> getMostActiveNationIds(int amt) {
        List<Map.Entry<Integer, Long>> nationIdActive = new ArrayList<>();

        Set<Integer> visited = new HashSet<>();
        for (int i = 0; i < amt && !dirtyNations.isEmpty(); i++) {
            try {
                int nationId = dirtyNations.iterator().next();
                if (visited.add(nationId)) {
                    nationIdActive.add(Map.entry(nationId, Long.MAX_VALUE)); // Always update dirty nations
                }
            } catch (NoSuchElementException ignore) {}
        }

        if (nationIdActive.size() < amt) {
            synchronized (nationsById) {
                for (DBNation nation : nationsById.values()) {
                    if (nation.getVm_turns() > 0) continue;

                    long active = nation.lastActiveMs();
                    nationIdActive.add(Map.entry(nation.getNation_id(), active));
                }
            }
        }
        Collections.sort(nationIdActive, (o1, o2) -> Long.compare(o2.getValue(), o1.getValue()));
        if (nationIdActive.size() > amt) nationIdActive = nationIdActive.subList(0, amt);
        List<Integer> mostActive = nationIdActive.stream().map(Map.Entry::getKey).collect(Collectors.toList());
        return mostActive;
    }

    public Set<Integer> updateMostActiveNations(int amt, Consumer<Event> eventConsumer) {
        return updateNations(getMostActiveNationIds(amt), eventConsumer);
    }

    public Set<Integer> updateNations(List<Integer> ids, Consumer<Event> eventConsumer) {
        Set<Integer> fetched = new LinkedHashSet<>();
        if (ids.isEmpty()) return fetched;

        int pad = 500 - ids.size() % 500;
        if (pad != 500) {
            getNewNationIds(pad, new HashSet<>(ids));
        }

        for (int i = 0; i < ids.size(); i++) {
            int end = Math.min(i + 500, ids.size());
            List<Integer> toFetch = ids.subList(i, end);
            fetched.addAll(updateNationsById(toFetch, eventConsumer));
        }
        return fetched;
    }

    public Set<Integer> updateNationsById(List<Integer> ids, Consumer<Event> eventConsumer) {
        Set<Integer> fetched = updateNations(r -> r.setId(ids), true, true, true, true, true, eventConsumer);
        Set<Integer> deleted = new HashSet<>();
        for (int id : ids) {
            if (!fetched.contains(id) && getNation(id) == null) deleted.add(id);
        }
        if (!deleted.isEmpty()) deleteNations(deleted, eventConsumer);
        return fetched;
    }

    private List<Integer> getMostActiveNationIdsLimitCities(int numCitiesToFetch, Set<Integer> ignoreTheseNations) {
        long turn = TimeUtil.getTurn();

        Map<Integer, Long> nationIdFetchDiff = new Int2LongArrayMap();
        Map<Integer, Integer> nationCityCount = new Int2IntArrayMap();

        synchronized (nationsById) {
            for (DBNation nation : nationsById.values()) {
                if (nation.getLeaving_vm() > turn) continue;
                if (ignoreTheseNations.contains(nation.getNation_id())) continue;
                if (nation.active_m() > 7200) continue; // Ignore nations that are inactive

                Map<Integer, DBCity> cities = getCitiesV3(nation.getNation_id());
                long maxDiff = 0;
                for (DBCity city : cities.values()) {
                    long diff = nation.lastActiveMs() - city.fetched;
                    maxDiff = Math.max(diff, maxDiff);
                }
                if (maxDiff > 0) {
                    nationCityCount.put(nation.getNation_id(), nation.getCities());
                    nationIdFetchDiff.merge(nation.getNation_id(), maxDiff, Math::max);
                }
            }
        }
        List<Map.Entry<Integer, Long>> sorted = new ArrayList<>(nationIdFetchDiff.entrySet());
        sorted.sort((o1, o2) -> Long.compare(o1.getValue(), o2.getValue()));

        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < sorted.size() && numCitiesToFetch > 0; i++) {
            int nationId = sorted.get(i).getKey();
            int cities = nationCityCount.get(nationId);
            if (numCitiesToFetch < cities) continue;
            result.add(nationId);
            numCitiesToFetch -= cities;
        }
        return result;
    }

    public void updateCitiesV2(Consumer<Event> eventConsumer) {
        Map<Integer, Integer> citiesToDeleteToNationId = new HashMap<>();
        synchronized (citiesByNation) {
            for (Map.Entry<Integer, Map<Integer, DBCity>> natEntry : citiesByNation.entrySet()) {
                int natId = natEntry.getKey();
                for (DBCity city : natEntry.getValue().values()) {
                    citiesToDeleteToNationId.put(city.id, natId);
                }
            }
        }

        List<SCityContainer> cities;
        try {
            cities = Locutus.imp().getPnwApi().getAllCities().getAllCities();
        } catch (IOException e) {
            e.printStackTrace();
            AlertUtil.error("Failed to fetch cities v2", e);
            return;
        }

        if (cities.isEmpty()) {
            // Nothing fetched??
            return;
        }

        DBCity buffer = new DBCity();

        for (SCityContainer city : cities) {
            int nationId = Integer.parseInt(city.getNationId());
            int cityId = Integer.parseInt(city.getCityId());

            // City was fetched, do not delete it
            citiesToDeleteToNationId.remove(cityId);

            DBCity existing = getDBCity(nationId, cityId);
            if (existing == null) {
                existing = new DBCity();

                if (eventConsumer != null) eventConsumer.accept(new CityCreateEvent(nationId, existing));

                dirtyCities.add(cityId);
            } else {
                double maxInfra = Double.parseDouble(city.getMaxinfra());

                buffer.set(existing);
                existing.set(city);

                if (existing.runChangeEvents(cityId, buffer, eventConsumer)) {
                    dirtyCities.add(cityId);
                }
            }
        }

        if (!citiesToDeleteToNationId.isEmpty()) {
            for (Map.Entry<Integer, Integer> entry : citiesToDeleteToNationId.entrySet()) {
                int cityId = entry.getKey();
                int nationId = entry.getValue();
                synchronized (citiesByNation) {
                    Map<Integer, DBCity> nationCities = citiesByNation.get(nationId);
                    if (nationCities != null) {
                        nationCities.remove(cityId);
                    }
                }
            }

            deleteCitiesInDB(citiesToDeleteToNationId.keySet());
        }
    }

    public void updateAllCities(Consumer<Event> eventConsumer) {
        PoliticsAndWarV3 v3 = Locutus.imp().getPnwApi().getV3();
        List<City> cities = v3.fetchCitiesWithInfo(null, true);
        Map<Integer, Map<Integer, City>> nationIdCityIdCityMap = new Int2ObjectOpenHashMap<>();
        for (City city : cities) {
            nationIdCityIdCityMap.computeIfAbsent(city.getNation_id(), f -> new Int2ObjectOpenHashMap<>())
                    .put(city.getId(), city);
        }
        updateCities(nationIdCityIdCityMap, eventConsumer);
    }

    public void updateCitiesOfNations(Set<Integer> nationIds, Consumer<Event> eventConsumer) {
        PoliticsAndWarV3 v3 = Locutus.imp().getPnwApi().getV3();

        List<Integer> idList = new ArrayList<>(nationIds);
        int estimatedCitiesToFetch = 0;
        for (int nationId : idList) {
            DBNation nation = getNation(nationId);
            if (nation == null) estimatedCitiesToFetch++;
            else estimatedCitiesToFetch += nation.getCities();
        }

        // Pad with most outdated cities, to make full use of api call
        if (estimatedCitiesToFetch < 490) { // Slightly below 500 to avoid off by 1 errors with api
            int numToFetch = 490 - idList.size();
            List<Integer> mostActiveNations = getMostActiveNationIdsLimitCities(numToFetch, new HashSet<>(idList));
            System.out.println("Fetching active nation cities: " + mostActiveNations.size());
            idList.addAll(mostActiveNations);
        }

        for (int i = 0; i < 500 && !idList.isEmpty(); i++) {
            int end = Math.min(i + 500, idList.size());
            List<Integer> toFetch = idList.subList(i, end);
            List<City> cities = v3.fetchCitiesWithInfo(r -> r.setNation_id(toFetch), true);
            Map<Integer, Map<Integer, City>> completeCities = new HashMap<>();
            for (City city : cities) {
                completeCities.computeIfAbsent(city.getNation_id(), f -> new HashMap<>())
                        .put(city.getId(), city);
            }
            updateCities(completeCities, eventConsumer);
        }
    }

    public boolean updateDirtyCities(Consumer<Event> eventConsumer) {
        if (dirtyCities.isEmpty()) return false;

        PoliticsAndWarV3 v3 = Locutus.imp().getPnwApi().getV3();
        List<Integer> cityIds = new ArrayList<>();
        while (!dirtyCities.isEmpty()) {
            try {
                int cityId = dirtyCities.iterator().next();
                cityIds.add(cityId);
            } catch (NoSuchElementException ignore) {}
        }

        int pad = 500 - cityIds.size() % 500;
        if (pad != 500) {
            cityIds.addAll(getNewCityIds(pad, new HashSet<>(cityIds)));
        }

        Set<Integer> deletedCities = new HashSet<>(cityIds);

        for (int i = 0; i < 500 && !cityIds.isEmpty(); i++) {
            int end = Math.min(i + 500, cityIds.size());
            List<Integer> toFetch = cityIds.subList(i, end);
            List<City> cities = v3.fetchCitiesWithInfo(r -> r.setId(toFetch), true);
            deletedCities.addAll(toFetch);
            for (City city : cities) deletedCities.remove(city.getId());
            updateCities(cities, eventConsumer);
        }
        if (!deletedCities.isEmpty()) deleteCitiesInDB(deletedCities);

        return true;
    }

    private int lastNewCityFetched = 0;

    private List<Integer> getNewCityIds(int amt, Set<Integer> ignoreIds) {
        Set<Integer> cityIds = new HashSet<>(ignoreIds);
        int maxId = 0;
        synchronized (citiesByNation) {
            for (Map<Integer, DBCity> cityMap : citiesByNation.values()) {
                for (DBCity city : cityMap.values()) {
                    maxId = Math.max(city.id, maxId);
                    cityIds.add(city.id);
                }
            }
        }
        if (lastNewCityFetched == 0) {
            lastNewCityFetched = maxId;
        } else {
            lastNewCityFetched = Math.min(maxId, lastNewCityFetched);
        }
        List<Integer> newIds = new ArrayList<>();
        while (newIds.size() < amt) {
            int id = ++lastNewCityFetched;
            if (cityIds.contains(id)) continue;
            newIds.add(id);
        }
        return newIds;
    }

    public void updateNewCities(Consumer<Event> eventConsumer) {
        PoliticsAndWarV3 v3 = Locutus.imp().getPnwApi().getV3();
        List<Integer> newIds = getNewCityIds(500, new HashSet<>());
        List<City> cities = v3.fetchCitiesWithInfo(r -> r.setId(newIds), true);
        updateCities(cities, eventConsumer);
    }

    /**
     * Update the cities in cache and database by the v3 object
     * @param completeCitiesByNation map of cities by nation id. If an entry is in the map, then all cities for thata nation must be included
     * @return if success
     */
    private void updateCities(Map<Integer, Map<Integer, City>> completeCitiesByNation, Consumer<Event> eventConsumer) {
        DBCity buffer = new DBCity();
        List<Map.Entry<Integer, DBCity>> dirtyCities = new ArrayList<>(); // List<nation id, db city>
        AtomicBoolean dirtyFlag = new AtomicBoolean();

        Set<Integer> citiesToDelete = new HashSet<>();

        for (Map.Entry<Integer, Map<Integer, City>> nationEntry : completeCitiesByNation.entrySet()) {
            int nationId = nationEntry.getKey();
            Map<Integer, City> cities = nationEntry.getValue();
            if (cities.isEmpty()) continue;
            if (!NationDB.this.dirtyCities.isEmpty()) {
                for (City city : cities.values()) {
                    NationDB.this.dirtyCities.remove(city.getId());
                }
            }

            Map<Integer, DBCity> existingMap;
            synchronized (citiesByNation) {
                existingMap = new HashMap<>(citiesByNation.getOrDefault(nationId, Collections.EMPTY_MAP));
            }
            for (Map.Entry<Integer, DBCity> cityEntry : existingMap.entrySet()) {
                City city = cities.get(cityEntry.getKey());

                if (city != null) {
                    dirtyFlag.set(false);
                    DBCity dbCity = processCityUpdate(city, buffer, eventConsumer, dirtyFlag);
                    if (dirtyFlag.get()) {
                        dirtyCities.add(Map.entry(city.getNation_id(), dbCity));
                    }
                } else {
                    synchronized (citiesByNation) {
                        citiesByNation.getOrDefault(nationId, Collections.EMPTY_MAP).remove(cityEntry.getKey());
                    }
                    citiesToDelete.add(cityEntry.getKey());
                    if (eventConsumer != null) {
                        eventConsumer.accept(new CityDeleteEvent(nationId, cityEntry.getValue()));
                    }
                }
            }
            for (Map.Entry<Integer, City> cityEntry : cities.entrySet()) {
                City city = cityEntry.getValue();
                if (!existingMap.containsKey(city.getId())) {
                    dirtyFlag.set(false);
                    DBCity dbCity = processCityUpdate(city, buffer, eventConsumer, dirtyFlag);
                    if (dirtyFlag.get()) {
                        dirtyCities.add(Map.entry(city.getNation_id(), dbCity));
                    }
                }
            }
        }
        if (!citiesToDelete.isEmpty()) {
            deleteCitiesInDB(citiesToDelete);
        }
        if (!dirtyCities.isEmpty()) {
            saveCities(dirtyCities);
        }
    }

    private void deleteCitiesInDB(Set<Integer> ids) {
        if (ids.size() == 1) {
            int id = ids.iterator().next();
            executeStmt("DELETE FROM CITY_BUILDS WHERE id = " + id);
        } else {
            executeStmt("DELETE FROM CITY_BUILDS WHERE `id` in " + StringMan.getString(ids));
        }
    }

    private void deletePositions(Set<Integer> ids, Consumer<Event> eventConsumer) {
        for (int id : ids) {
            DBAlliancePosition position;
            synchronized (positionsById) {
                position = positionsById.remove(id);
            }
            if (position != null) {
                synchronized (positionsByAllianceId) {
                    positionsByAllianceId.getOrDefault(position.getId(), Collections.EMPTY_MAP).remove(position.getId());
                }
                if (eventConsumer != null) {
                    PositionDeleteEvent event = new PositionDeleteEvent(position);
                    eventConsumer.accept(event);
                }
            }
        }

        deletePositionsInDB(ids);;
    }

    private void deletePositionsInDB(Set<Integer> ids) {
        if (ids.size() == 1) {
            int id = ids.iterator().next();
            executeStmt("DELETE FROM POSITIONS WHERE id = " + id);
        } else {
            executeStmt("DELETE FROM POSITIONS WHERE `id` in " + StringMan.getString(ids));
        }
    }

    private void updateCities(List<City> cities, Consumer<Event> eventConsumer) {
        DBCity buffer = new DBCity();
        List<Map.Entry<Integer, DBCity>> dirtyCities = new ArrayList<>(); // List<nation id, db city>
        AtomicBoolean dirtyFlag = new AtomicBoolean();

        for (City city : cities) {
            if (!NationDB.this.dirtyCities.isEmpty()) {
                NationDB.this.dirtyCities.remove(city.getId());
            }
            dirtyFlag.set(false);
            DBCity dbCity = processCityUpdate(city, buffer, eventConsumer, dirtyFlag);
            if (dirtyFlag.get()) {
                dirtyCities.add(Map.entry(city.getNation_id(), dbCity));
            }
        }

        if (!dirtyCities.isEmpty()) {
            saveCities(dirtyCities);
        }
    }

    /**
     *
     * @param city
     * @param buffer
     * @param eventConsumer
     * @return
     */
    private DBCity processCityUpdate(City city, DBCity buffer, Consumer<Event> eventConsumer, AtomicBoolean dirtyFlag) {
        DBCity existing = getDBCity(city.getNation_id(), city.getId());

        if (existing != null) {
            buffer.set(existing);
            existing.set(city);
            if (existing.runChangeEvents(city.getNation_id(), existing, eventConsumer)) {
                dirtyFlag.set(true);
            }
        } else {
            existing = new DBCity(city);
            synchronized (citiesByNation) {
                citiesByNation.computeIfAbsent(city.getNation_id(), f -> new Int2ObjectOpenHashMap<>())
                        .put(city.getId(), existing);
            }
            if (existing.runChangeEvents(city.getNation_id(), null, eventConsumer)) {
                dirtyFlag.set(true);
            }
        }
        return existing;
    }
    public DBAlliance getAlliance(int id) {
        synchronized (alliancesById) {
            return alliancesById.get(id);
        }
    }

    public DBAlliance getOrCreateAlliance(int id) {
        DBAlliance existing = getAlliance(id);
        if (existing == null) {
            existing = new DBAlliance(id,
            "AA:" + id,
            "",
            "",
            "",
            "",
            "",
            System.currentTimeMillis(),
            NationColor.GRAY);
        }
        return existing;
    }

    private static final Cache<String, DBAlliance> allianceByNameCache = CacheBuilder.newBuilder()
            .weakKeys()
            .weakValues()
            .build();

    public String getAllianceName(int aaId) {
        DBAlliance existing = getAlliance(aaId);
        if (existing != null) return existing.getName();
        for (Map.Entry<String, DBAlliance> entry : allianceByNameCache.asMap().entrySet()) {
            if (entry.getValue().getAlliance_id() == aaId) {
                return entry.getKey();
            }
        }
        return "AA:" + aaId;
    }
    public DBAlliance getAllianceByName(String name) {
        {
            DBAlliance alliance = allianceByNameCache.getIfPresent(name.toLowerCase(Locale.ROOT));
            if (alliance != null && alliance.getName().equalsIgnoreCase(name)) {
                return alliance;
            }
        }
        DBAlliance result = null;
        synchronized (alliancesById.values()) {
            for (DBAlliance alliance : alliancesById.values()) {
                if (alliance.getName().equalsIgnoreCase(name)) {
                    allianceByNameCache.put(alliance.getName().toLowerCase(Locale.ROOT), alliance);
                    return alliance;
                }
            }
        }
        return null;
    }

    private void loadTreaties() throws SQLException {
        Set<Integer> treatiesToDelete = new LinkedHashSet<>();
        long currentTurn = TimeUtil.getTurn();

        SelectBuilder builder = getDb().selectBuilder("TREATIES2").select("*");
        try (ResultSet rs = builder.executeRaw()) {
            while (rs.next()) {
                Treaty treaty = createTreaty(rs);
                DBAlliance from = getAlliance(treaty.getFromId());
                DBAlliance to = getAlliance(treaty.getToId());
                if (from == null && to == null) {
                    treatiesToDelete.add(treaty.getId());
                    continue;
                }
                if (currentTurn >= treaty.getTurnEnds()) {
                    new TreatyExpireEvent(treaty).post();
                    treatiesToDelete.add(treaty.getId());
                    continue;
                }

                treatiesByAlliance.computeIfAbsent(treaty.getFromId(), f -> new Int2ObjectOpenHashMap<>()).put(treaty.getToId(), treaty);
                treatiesByAlliance.computeIfAbsent(treaty.getToId(), f -> new Int2ObjectOpenHashMap<>()).put(treaty.getFromId(), treaty);
            }
        }

        deleteTreatiesInDB(treatiesToDelete);
    }

    private Treaty createTreaty(ResultSet rs) throws SQLException {
        return new Treaty(
                rs.getInt("id"),
                rs.getLong("date"),
                TreatyType.values[rs.getInt("type")],
                rs.getInt("from"),
                rs.getInt("to"),
                rs.getLong("turn_ends")
        );
    }

    private void loadCities() throws SQLException {
        SelectBuilder builder = getDb().selectBuilder("CITY_BUILDS").select("*");
        try (ResultSet rs = builder.executeRaw()) {
            while (rs.next()) {
                Map.Entry<Integer, DBCity> entry = createCity(rs);
                int nationId = entry.getKey();
                DBCity city = entry.getValue();
                citiesByNation.computeIfAbsent(nationId, f -> new Int2ObjectOpenHashMap<>()).put(nationId, city);
            }
        }
    }

    /**
     * @param rs
     * @return (nation id, city)
     * @throws SQLException
     */
    private Map.Entry<Integer, DBCity> createCity(ResultSet rs) throws SQLException {
        int nationId = rs.getInt("nation");
        DBCity data = new DBCity();
        data.id = rs.getInt("id");
        data.created = rs.getLong("created");
        data.infra = rs.getInt("infra") / 100d;
        data.land = rs.getInt("land") / 100d;
        data.powered = rs.getBoolean("powered");
        data.buildings = rs.getBytes("improvements");
        data.fetched = rs.getLong("update_flag");
        return Map.entry(nationId, data);
    }

    private void loadPositions() throws SQLException {
        SelectBuilder builder = getDb().selectBuilder("POSITIONS").select("*");
        try (ResultSet rs = builder.executeRaw()) {
            while (rs.next()) {
                DBAlliancePosition position = createPosition(rs);
                positionsById.put(position.getId(), position);
                positionsByAllianceId.computeIfAbsent(position.getAlliance_id(), f -> new Int2ObjectOpenHashMap<>())
                        .put(position.getId(), position);
            }
        }
    }

    private DBAlliancePosition createPosition(ResultSet rs) throws SQLException {
        return new DBAlliancePosition(
                rs.getInt("id"),
                rs.getInt("alliance_id"),
                rs.getString("name"),
                rs.getLong("date_created"),
                rs.getInt("position_level"),
                Rank.byId(rs.getInt("position_level")),
                rs.getLong("permission_bits")
        );
    }

    public DBAlliancePosition getPosition(int id, int alliance_id, boolean create) {
        DBAlliancePosition position = positionsById.get(id);
        if (position == null) {
            if (position == null && create) {
                position = new DBAlliancePosition(id, alliance_id, null, -1, -1, Rank.MEMBER, 0);
            }
        }
        return position;
    }

    private void loadNations() throws SQLException {
        SelectBuilder builder = getDb().selectBuilder("NATIONS2").select("*");
        try (ResultSet rs = builder.executeRaw()) {
            while (rs.next()) {
                DBNation nation = createNation(rs);
                nationsById.put(nation.getNation_id(), nation);
                if (nation.getAlliance_id() != 0) {
                    nationsByAlliance.computeIfAbsent(nation.getAlliance_id(),
                            f -> new Int2ObjectOpenHashMap<>()).put(nation.getNation_id(), nation);
                }
            }
        }
    }

    private DBNation createNation(ResultSet rs) throws SQLException {
        return new DBNation(
                rs.getInt("nation_id"),
                rs.getString("nation"),
                rs.getString("leader"),
                rs.getInt("alliance_id"),
                rs.getLong("last_active"),
                rs.getLong("score") / 100d,
                rs.getInt("cities"),
                DomesticPolicy.values[rs.getInt("domestic_policy")],
                WarPolicy.values[rs.getInt("war_policy")],
                rs.getInt("soldiers"),
                rs.getInt("tanks"),
                rs.getInt("aircraft"),
                rs.getInt("ships"),
                rs.getInt("missiles"),
                rs.getInt("nukes"),
                rs.getInt("spies"),
                rs.getLong("entered_vm"),
                rs.getLong("leaving_vm"),
                NationColor.values[rs.getInt("color")],
                rs.getLong("date"),
                Rank.byId(rs.getInt("position")),
                rs.getInt("alliancePosition"),
                Continent.values[rs.getInt("continent")],
                rs.getLong("projects"),
                rs.getLong("cityTimer"),
                rs.getLong("projectTimer"),
                rs.getLong("beigeTimer"),
                rs.getLong("warPolicyTimer"),
                rs.getLong("domesticPolicyTimer"),
                rs.getLong("colorTimer"),
                rs.getLong("espionageFull"),
                rs.getInt("dc_turn"),
                rs.getInt("wars_won"),
                rs.getInt("wars_lost"),
                rs.getInt("tax_id")
        );
    }

    public void updateNationsV2(boolean includeVM, Consumer<Event> eventConsumer) {
        try {
            List<SNationContainer> nations = Locutus.imp().getPnwApi().getNationsByScore(includeVM, 999999, -1).getNationsContainer();

            List<DBNation> toSave = new ArrayList<>();
            Set<Integer> expected = new LinkedHashSet<>(nations.size());
            synchronized (nationsById) {
                for (Map.Entry<Integer, DBNation> entry : nationsById.entrySet()) {
                    if (entry.getValue().getVm_turns() <= 0) expected.add(entry.getValue().getNation_id());
                }
            }
            for (SNationContainer nation : nations) {
                DBNation existing = getNation(nation.getNationid());
                if (existing == null) {
                    existing = new DBNation(nation);
                    synchronized (nationsById) {
                        if (existing.getAlliance_id() != 0) {
                            nationsById.put(existing.getNation_id(), existing);
                            synchronized (nationsByAlliance) {
                                nationsByAlliance.computeIfAbsent(existing.getAlliance_id(), f -> new Int2ObjectOpenHashMap<>()).put(existing.getNation_id(), existing);
                            }
                        }
                    }
                    if (eventConsumer != null) {
                        eventConsumer.accept(new NationCreateEvent(null, existing));
                    }
                    toSave.add(existing);

                    dirtyNations.add(nation.getNationid());
                } else {
                    expected.remove(existing.getNation_id());
                    if (existing.updateNationInfo(nation, eventConsumer)) {
                        dirtyNations.add(existing.getNation_id());
                        toSave.add(existing);
                    }
                }
            }

            if (!toSave.isEmpty()) saveNations(toSave);

            if (!expected.isEmpty()) {
                dirtyNations.addAll(expected);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Map.Entry<Integer, Long> getLatestNationIdDate(Set<Integer> updated) {
        int maxId = 0;
        long maxDate = 0;
        synchronized (nationsById) {
            for (int id : updated) {
                DBNation nation = nationsById.get(id);
                if (nation != null) {
                    maxId = Math.max(maxId, nation.getId());
                    maxDate = Math.max(maxDate, nation.getDate());
                }
            }
        }
        return maxId == 0 ? null : Map.entry(maxId, maxDate);
    }

    private Map.Entry<Integer, Long> getLatestNationIdDate() {
        int maxId = 0;
        long maxDate = 0;
        synchronized (nationsById) {
            for (Map.Entry<Integer, DBNation> entry : nationsById.entrySet()) {
                DBNation nation = entry.getValue();
                maxId = Math.max(nation.getNation_id(), maxId);
                maxDate = Math.max(nation.getDate(), maxDate);
            }
        }
        return maxId == 0 ? null : Map.entry(maxId, maxDate);
    }

    private long lastNewNationDate = 0;
    private Set<Integer> updateNewNations(Consumer<Event> eventConsumer) {
        Map.Entry<Integer, Long> lastNewNation = getLatestNationIdDate();
        if (lastNewNation != null) {
            if (lastNewNationDate == 0) {
                lastNewNationDate = lastNewNation.getValue();
                lastNewNationId = lastNewNation.getKey();
            } else {
                lastNewNationId = Math.min(lastNewNationId, lastNewNation.getKey());
            }
        }
        long startDate = lastNewNationDate;
        lastNewNationDate = System.currentTimeMillis();
        return updateNewNationsByDate(startDate, eventConsumer);
    }

    private int lastNewNationId = 0;
    public List<Integer> getNewNationIds(int amt, Set<Integer> ignoreIds) {
        Map.Entry<Integer, Long> lastNewNation = getLatestNationIdDate();
        if (lastNewNation != null) {
            if (lastNewNationId == 0) {
                lastNewNationDate = lastNewNation.getValue();
                lastNewNationId = lastNewNation.getKey();
            } else {
                lastNewNationId = Math.min(lastNewNationId, lastNewNation.getKey());
            }
        }
        List<Integer> result = new ArrayList<>();
        while (result.size() < amt) {
            lastNewNationId++;
            if (getNation(lastNewNationId) != null) continue;
            if (ignoreIds.contains(lastNewNationId)) continue;
            result.add(lastNewNationId);
        }
        return result;
    }

    public Set<Integer> updateNewNationsById(Consumer<Event> eventConsumer) {
        List<Integer> newIds = getNewNationIds(500, new HashSet<>());
        Set<Integer> fetched = updateNations(r -> r.setId(newIds), true, true, true, true, true, eventConsumer);
        Set<Integer> deleted = new HashSet<>();
        for (int id : newIds) {
            if (!fetched.contains(id) && getNation(id) != null) deleted.add(id);
        }
        if (!deleted.isEmpty()) deleteNations(deleted, eventConsumer);
        return fetched;
    }

    public Set<Integer> updateNewNationsByDate(long minDate, Consumer<Event> eventConsumer) {
        Set<Integer> expected = new HashSet<>();
        synchronized (nationsById) {
            for (DBNation nation : nationsById.values()) {
                if (nation.getDate() > minDate) {
                    expected.add(nation.getNation_id());
                }
            }
        }
        Set<Integer> fetched = updateNations(r -> r.setCreated_after(new Date(minDate)), true, true, true, true, true, eventConsumer);
        expected.removeAll(fetched);
        dirtyNations.addAll(expected);
        return fetched;
    }

    public Set<Integer> updateAllNations(Consumer<Event> eventConsumer) {
        return updateAllNations(f -> {}, eventConsumer);
    }

    public Set<Integer> updateAllNations(Consumer<NationsQueryRequest> queryConsumer, Consumer<Event> eventConsumer) {
        Set<Integer> currentNations;
        synchronized (nationsById) {
            currentNations = new HashSet<>(nationsById.keySet());
        }
        Set<Integer> deleted = new HashSet<>();
        Set<Integer> fetched = updateNations(queryConsumer, eventConsumer);
        for (int id : currentNations) {
            if (!fetched.contains(id)) deleted.add(id);
        }
        deleteNations(deleted, eventConsumer);
        return fetched;
    }

    public Set<Integer> updateNations(Consumer<NationsQueryRequest> filter, Consumer<Event> eventConsumer) {
        return updateNations(filter, true, true, true, true, true, eventConsumer);
    }

    public Set<Integer> updateNations(Consumer<NationsQueryRequest> filter,
                              boolean fetchCitiesIfNew,
                              boolean fetchCitiesIfOutdated,
                              boolean fetchAlliancesIfOutdated,
                              boolean fetchPositionsIfOutdated,
                              boolean fetchMostActiveIfNoneOutdated,
                                      Consumer<Event> eventConsumer) {
        Set<Integer> nationsFetched = new HashSet<>();
        Map<DBNation, DBNation> dirtyNations = new LinkedHashMap<>(); // Map<current, previous>

        PoliticsAndWarV3 v3 = Locutus.imp().getPnwApi().getV3();

        Predicate<Nation> onEachNation = nation -> {
            if (nation.getId() != null) {
                nationsFetched.add(nation.getId());
                if (!NationDB.this.dirtyNations.isEmpty()) {
                    NationDB.this.dirtyNations.remove(nation.getId());
                }
            }
            updateNation(nation, true, eventConsumer, (prev, curr) -> dirtyNations.put(curr, prev));
            return false;
        };

        v3.fetchNationsWithInfo(filter, onEachNation);

        saveNations(dirtyNations.keySet());

        Set<Integer> fetchCitiesOfNations = new HashSet<>();

        if (fetchCitiesIfNew) {
            // num cities has changed
            for (Map.Entry<DBNation, DBNation> entry : dirtyNations.entrySet()) {
                DBNation prev = entry.getValue();
                DBNation curr = entry.getKey();
                if (prev == null || curr.getCities() != prev.getCities()) {
                    fetchCitiesOfNations.add(curr.getNation_id());
                }
            }
        }
        if (fetchCitiesIfOutdated) {
            for (Map.Entry<DBNation, DBNation> entry : dirtyNations.entrySet()) {
                DBNation nation = entry.getKey();
                if (nation.estimateScore() != nation.getScore()) {
                    fetchCitiesOfNations.add(nation.getNation_id());
                }
            }
        }

        System.out.println("Updated nations.\n" +
                "Fetched: " + nationsFetched.size() + "\n" +
                "Changes: " + dirtyNations.size() + "\n" +
                "Cities: " + fetchCitiesOfNations.size());


        if (!fetchCitiesOfNations.isEmpty() || (fetchCitiesIfNew && fetchMostActiveIfNoneOutdated)) {
            updateCitiesOfNations(fetchCitiesOfNations, eventConsumer);
        }

        if (fetchAlliancesIfOutdated || fetchPositionsIfOutdated) {
            updateOutdatedAlliances(fetchPositionsIfOutdated, eventConsumer);
        }

        return nationsFetched;
    }

    /**
     *
     * @param nation the nation
     * @param nationInfo if nation info should be checked and updated
     * @param eventConsumer any nation events to call
     * @param nationsToSave (previous, current)
     */
    private void updateNation(Nation nation, boolean nationInfo, Consumer<Event> eventConsumer, BiConsumer<DBNation, DBNation> nationsToSave) {
        if (nationInfo) {
            DBNation existing = getNation(nation.getId());
            Consumer<Event> eventHandler;
            if (existing == null) {
                eventHandler = null;
            } else {
                eventHandler = eventConsumer;
            }
            AtomicBoolean isDirty = new AtomicBoolean();
            DBNation newNation = updateNationInfo(existing, nation, eventHandler, isDirty);
            if (isDirty.get()) {
                nationsToSave.accept(existing, newNation);
            }
            if (existing == null && eventConsumer != null) {
                eventConsumer.accept(new NationCreateEvent(null, newNation));
            }
        }
        if (nation.getBankrecs() != null && !nation.getBankrecs().isEmpty()) {
            List<Bankrec> bankRecords = nation.getBankrecs();
            throw new UnsupportedOperationException("Not implemented nation bancrecs");
        }
        if (nation.getCities() != null) {
            List<City> cities = nation.getCities();
            throw new UnsupportedOperationException("Not implemented nation cities");
        }
    }

    private void processNationAllianceChange(DBNation previous, DBNation current) {
        if (previous == null) {
            synchronized (nationsById) {
                nationsById.put(current.getNation_id(), current);
            }
            if (current.getAlliance_id() != 0) {
                synchronized (nationsByAlliance) {
                    nationsByAlliance.computeIfAbsent(current.getAlliance_id(),
                            f -> new Int2ObjectOpenHashMap<>()).put(current.getNation_id(), current);
                }
            }
        } else if (previous.getAlliance_id() != current.getAlliance_id()) {
            synchronized (nationsByAlliance) {
                if (previous.getAlliance_id() != 0) {
                    nationsByAlliance.getOrDefault(previous.getAlliance_id(), Collections.EMPTY_MAP)
                            .remove(previous.getAlliance_id());
                }
                if (current.getAlliance_id() != 0) {
                    nationsByAlliance.computeIfAbsent(current.getAlliance_id(),
                            f -> new Int2ObjectOpenHashMap<>()).put(current.getNation_id(), current);
                }
            }
        }
    }

    public DBNation getNation(int id) {
        synchronized (nationsById) {
            return nationsById.get(id);
        }
    }

//    private void updateNation(DBNation base, NationMilitaryContainer nation, Consumer<Event> eventConsumer, AtomicBoolean markDirty) {
//        TODO;
//    }

    private DBNation updateNationInfo(DBNation base, Nation nation, Consumer<Event> eventConsumer, AtomicBoolean markDirty) {
        DBNation copyOriginal = base == null ? null : new DBNation(base);
        if (base == null) {
            markDirty.set(true);
            base = new DBNation();
        }
        if (base.updateNationInfo(copyOriginal, nation, eventConsumer)) {
            markDirty.set(true);
        }
        return base;
    }

    private void loadAlliances() throws SQLException {
        SelectBuilder builder = getDb().selectBuilder("ALLIANCES").select("*");
        try (ResultSet rs = builder.executeRaw()) {
            while (rs.next()) {
                DBAlliance alliance = createAlliance(rs);
                alliancesById.put(alliance.getAlliance_id(), alliance);
            }
        }
    }

    private DBAlliance createAlliance(ResultSet rs) throws SQLException {
        return new DBAlliance(
                rs.getInt("id"),
                rs.getString("name"),
                rs.getString("acronym"),
                rs.getString("flag"),
                rs.getString("forum_link"),
                rs.getString("discord_link"),
                rs.getString("wiki_link"),
                rs.getLong("dateCreated"),
                NationColor.values[rs.getInt("color")]
        );
    }



    public void createTables() {
        {
            TablePreset.create("NATIONS2")
                    .putColumn("nation_id", ColumnType.INT.struct().setPrimary(true).setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("nation", ColumnType.VARCHAR.struct().setNullAllowed(false).configure(f -> f.apply(32)))
                    .putColumn("leader", ColumnType.VARCHAR.struct().setNullAllowed(false).configure(f -> f.apply(32)))
                    .putColumn("alliance_id", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("last_active", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("score", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("cities", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("domestic_policy", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("war_policy", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("soldiers", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("tanks", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("aircraft", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("ships", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("missiles", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("nukes", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("spies", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("entered_vm", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("leaving_vm", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("color", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("date", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("position", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("alliancePosition", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("continent", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("projects", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("cityTimer", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("projectTimer", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("beigeTimer", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("warPolicyTimer", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("domesticPolicyTimer", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("colorTimer", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("espionageFull", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("dc_turn", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("wars_won", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("wars_lost", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("tax_id", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .create(getDb());

            TablePreset.create("POSITIONS")
                    .putColumn("id", ColumnType.INT.struct().setPrimary(true).setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("alliance_id", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("name", ColumnType.VARCHAR.struct().setNullAllowed(false).configure(f -> f.apply(32)))
                    .putColumn("date_created", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("position_level", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("rank", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("permission_bits", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .create(getDb());

            TablePreset.create("ALLIANCES")
                    .putColumn("id", ColumnType.INT.struct().setPrimary(true).setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("name", ColumnType.VARCHAR.struct().setNullAllowed(false).configure(f -> f.apply(32)))
                    .putColumn("acronym", ColumnType.VARCHAR.struct().setNullAllowed(false).configure(f -> f.apply(32)))
                    .putColumn("flag", ColumnType.VARCHAR.struct().setNullAllowed(false).configure(f -> f.apply(256)))
                    .putColumn("forum_link", ColumnType.VARCHAR.struct().setNullAllowed(false).configure(f -> f.apply(256)))
                    .putColumn("discord_link", ColumnType.VARCHAR.struct().setNullAllowed(false).configure(f -> f.apply(256)))
                    .putColumn("wiki_link", ColumnType.VARCHAR.struct().setNullAllowed(false).configure(f -> f.apply(256)))
                    .putColumn("dateCreated", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("color", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .create(getDb());

            TablePreset.create("TREATIES2")
                    .putColumn("id", ColumnType.INT.struct().setPrimary(true).setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("date", ColumnType.INT.struct().setPrimary(false).setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("type", ColumnType.INT.struct().setPrimary(false).setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("from", ColumnType.INT.struct().setPrimary(false).setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("to", ColumnType.INT.struct().setPrimary(false).setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("turn_ends", ColumnType.INT.struct().setPrimary(false).setNullAllowed(false).configure(f -> f.apply(null)))
                    .create(getDb());
        };

        {
            String query = "CREATE TABLE IF NOT EXISTS `BEIGE_REMINDERS` (`target` INT NOT NULL, `attacker` INT NOT NULL, `turn` INT NOT NULL, PRIMARY KEY(target, attacker))";
            executeStmt(query);
        }

        {
            String query = "CREATE TABLE IF NOT EXISTS `AUDITS` (`nation` INT NOT NULL, `guild` INT NOT NULL, `audit`VARCHAR NOT NULL, `date`  INT NOT NULL)";
            executeStmt(query);
        }

        {
            String nations = "CREATE TABLE IF NOT EXISTS `NATION_LOOT` (`id` INT NOT NULL PRIMARY KEY, `loot` BLOB NOT NULL, `turn` INT NOT NULL)";
            try (Statement stmt = getConnection().createStatement()) {
                stmt.addBatch(nations);
                stmt.executeBatch();
                stmt.clearBatch();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        };

        {
            String nations = "CREATE TABLE IF NOT EXISTS `NATION_META` (`id` INT NOT NULL, `key` INT NOT NULL, `meta` BLOB NOT NULL, PRIMARY KEY(id, key))";
            try (Statement stmt = getConnection().createStatement()) {
                stmt.addBatch(nations);
                stmt.executeBatch();
                stmt.clearBatch();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        };

        {
            String milup = "CREATE TABLE IF NOT EXISTS `SPY_DAILY` (`attacker` INT NOT NULL, `defender` INT NOT NULL, `turn` INT NOT NULL, `amount` INT NOT NULL)";
            try (Statement stmt = getConnection().createStatement()) {
                stmt.addBatch(milup);
                stmt.executeBatch();
                stmt.clearBatch();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        };
        {
            String milup = "CREATE TABLE IF NOT EXISTS `NATION_MIL_HISTORY` (`id` INT NOT NULL, `date` INT NOT NULL, `unit` INT NOT NULL, `amount` INT NOT NULL, PRIMARY KEY(id,date))";
            try (Statement stmt = getConnection().createStatement()) {
                stmt.addBatch(milup);
                stmt.executeBatch();
                stmt.clearBatch();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        };
        executeStmt("CREATE INDEX IF NOT EXISTS index_mil_unit ON NATION_MIL_HISTORY (unit);");
        executeStmt("CREATE INDEX IF NOT EXISTS index_mil_amount ON NATION_MIL_HISTORY (amount);");
        executeStmt("CREATE INDEX IF NOT EXISTS index_mil_date ON NATION_MIL_HISTORY (date);");

        String addColum = "ALTER TABLE NATIONS ADD %s INT NOT NULL DEFAULT (0)";
        for (String col : Arrays.asList("rank", "position", "continent")) {
            {
                try (Statement stmt = getConnection().createStatement()) {
                    stmt.addBatch(String.format(addColum, col));
                    stmt.executeBatch();
                    stmt.clearBatch();
                } catch (SQLException e) {
                }
            };
        }
        String cities = "CREATE TABLE IF NOT EXISTS `CITIES` (`id` INT NOT NULL PRIMARY KEY, `nation` INT NOT NULL, `created` INT NOT NULL, `land` INT NOT NULL, `improvements` BLOB NOT NULL, `update_flag` INT NOT NULL)";
        try (Statement stmt = getConnection().createStatement()) {
            stmt.addBatch(cities);
            stmt.executeBatch();
            stmt.clearBatch();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        // unit = ? AND date < ?
        executeStmt("CREATE INDEX IF NOT EXISTS index_cities_nation ON CITIES (nation);");


        {
            executeStmt("CREATE TABLE IF NOT EXISTS `CITY_BUILDS` (`id` INT NOT NULL PRIMARY KEY, `nation` INT NOT NULL, `created` INT NOT NULL, `infra` INT NOT NULL, `land` INT NOT NULL, `powered` BOOLEAN NOT NULL, `improvements` BLOB NOT NULL, `update_flag` INT NOT NULL)");
            executeStmt("CREATE INDEX IF NOT EXISTS index_city_builds_nation ON CITIES (nation);");
        }

        String kicks = "CREATE TABLE IF NOT EXISTS `KICKS` (`nation` INT NOT NULL, `alliance` INT NOT NULL, `date` INT NOT NULL, `type` INT NOT NULL)";
        try (Statement stmt = getConnection().createStatement()) {
            stmt.addBatch(kicks);
            stmt.executeBatch();
            stmt.clearBatch();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        String projects = "CREATE TABLE IF NOT EXISTS `PROJECTS` (`nation` INT NOT NULL, `project` INT NOT NULL)";
        try (Statement stmt = getConnection().createStatement()) {
            stmt.addBatch(projects);
            stmt.executeBatch();
            stmt.clearBatch();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        String spies = "CREATE TABLE IF NOT EXISTS `SPIES_BUILDUP` (`nation` INT NOT NULL, `spies` INT NOT NULL, `day` INT NOT NULL, PRIMARY KEY(nation, day))";
        try (Statement stmt = getConnection().createStatement()) {
            stmt.addBatch(spies);
            stmt.executeBatch();
            stmt.clearBatch();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        String alliances = "CREATE TABLE IF NOT EXISTS `ALLIANCES` (`id` INT NOT NULL PRIMARY KEY, `name` VARCHAR NOT NULL, `acronym` VARCHAR, `flag` VARCHAR, `forum` VARCHAR, `irc` VARCHAR)";
        try (Statement stmt = getConnection().createStatement()) {
            stmt.addBatch(alliances);
            stmt.executeBatch();
            stmt.clearBatch();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        String activity = "CREATE TABLE IF NOT EXISTS `activity` (`nation` INT NOT NULL, `turn` INT NOT NULL, PRIMARY KEY(nation, turn))";
        try (Statement stmt = getConnection().createStatement()) {
            stmt.addBatch(activity);
            stmt.executeBatch();
            stmt.clearBatch();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        String activity_m = "CREATE TABLE IF NOT EXISTS `spy_activity` (`nation` INT NOT NULL, `timestamp` INT NOT NULL, `projects` INT NOT NULL, `change` INT NOT NULL, `spies` INT NOT NULL, PRIMARY KEY(nation, timestamp))";
        try (Statement stmt = getConnection().createStatement()) {
            stmt.addBatch(activity_m);
            stmt.executeBatch();
            stmt.clearBatch();
        } catch (SQLException e) {
            e.printStackTrace();
        }

//        String treaties = "CREATE TABLE IF NOT EXISTS `treaties` (`aa_from` INT NOT NULL, `aa_to` INT NOT NULL, `type` INT NOT NULL, `date` INT NOT NULL, PRIMARY KEY(aa_from, aa_to))";
//        try (Statement stmt = getConnection().createStatement()) {
//            stmt.addBatch(treaties);
//            stmt.executeBatch();
//            stmt.clearBatch();
//        } catch (SQLException e) {
//            e.printStackTrace();
//        }
//        executeStmt("CREATE INDEX IF NOT EXISTS index_treaty_id ON `treaties` (aa_from, aa_to);");

        String purgeSpyActivity = "DELETE FROM spy_activity WHERE timestamp < ?";
        try (PreparedStatement stmt = getConnection().prepareStatement(purgeSpyActivity)) {
            stmt.setLong(1, System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7));
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        executeStmt("CREATE TABLE IF NOT EXISTS expenses (nation INT NOT NULL, date INT NOT NULL, expense INT NOT NULL)");
        executeStmt("CREATE TABLE IF NOT EXISTS loot_cache (nation INT PRIMARY KEY, date INT NOT NULL, loot BLOB NOT NULL)");
        executeStmt("CREATE TABLE IF NOT EXISTS ALLIANCE_METRICS (alliance_id INT NOT NULL, metric INT NOT NULL, turn INT NOT NULL, value DOUBLE NOT NULL, PRIMARY KEY(alliance_id, metric, turn))");

        purgeOldBeigeReminders();
    }

    public DBNation getNation(String nameOrLeader) {
        
        try (PreparedStatement stmt = prepareQuery("select id FROM NATIONS2 WHERE UPPER(`nation`) = UPPER(?) OR UPPER(`leader`) = UPPER(?)")) {
            stmt.setString(1, nameOrLeader);
            stmt.setString(2, nameOrLeader);
            DBNation leader = null;
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    leader = createNation(rs);
                    if (leader.getNation().equalsIgnoreCase(nameOrLeader)) {
                        return leader;
                    }
                }
                return leader;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public DBNation getFirstNationMatching(Predicate<DBNation> findIf) {
        synchronized (nationsById) {
            for (DBNation value : nationsById.values()) {
                if (findIf.test(value)) {
                    return value;
                }
            }
        }
        return null;
    }

    public Set<DBNation> getNationsMatching(Predicate<DBNation> findIf) {
        Set<DBNation> result = new LinkedHashSet<>();
        synchronized (nationsById) {
            for (DBNation value : nationsById.values()) {
                if (findIf.test(value)) {
                    result.add(value);
                }
            }
        }
        return result;
    }

    public DBNation getNationByName(String name) {
        return getFirstNationMatching(f -> f.getNation().equalsIgnoreCase(name));
    }

    public DBNation getNationByLeader(String leader) {
        return getFirstNationMatching(f -> f.getLeader().equalsIgnoreCase(leader));
    }

    public Map<Integer, DBNation> getNations() {
        synchronized (nationsById) {
            return new Int2ObjectOpenHashMap<>(nationsById);
        }
    }

    public void forNations(Consumer<DBNation> onEach, Set<Integer> alliances) {
        for (int aaId : alliances) {
            synchronized (nationsByAlliance) {
                Map<Integer, DBNation> nations = nationsByAlliance.get(aaId);
                if (nations != null) {
                    for (DBNation nation : nations.values()) {
                        onEach.accept(nation);
                    }
                }
            }
        }
    }
    public Set<DBNation> getNations(Set<Integer> alliances) {
        if (alliances.isEmpty()) {
            return new LinkedHashSet<>();
        }
        Set<DBNation> result;
        if (alliances.contains(0)) {
            if (alliances.size() == 1) {
                int id = alliances.iterator().next();
                return getNationsMatching(f -> f.getAlliance_id() == id);
            }
            return getNationsMatching(f -> alliances.contains(f.getAlliance_id()));
        } else {
            result = new LinkedHashSet<>();
            for (int aaId : alliances) {
                synchronized (nationsByAlliance) {
                    Map<Integer, DBNation> nations = nationsByAlliance.get(aaId);
                    if (nations != null) {
                        result.addAll(nations.values());
                    }
                }
            }
        }
        return result;
    }

    private DBNation createNationLegacy(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        String nation = rs.getString("nation");
        String leader = rs.getString("leader");
        int alliance_id = getInt(rs, "alliance_id");
        String alliance = rs.getString("alliance");
        long last_active = getLong(rs, "active_m");
        if (last_active < (2 << 29)) {
            last_active = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(last_active);
        }
        double score = rs.getDouble("score");
        Integer infra = getInt(rs, "infra");
        int cities = getInt(rs, "cities");
        Integer avg_infra = getInt(rs, "avg_infra");
        String warPolicyStr = rs.getString("policy");
        Integer soldiers = getInt(rs, "soldiers");
        Integer tanks = getInt(rs, "tanks");
        Integer aircraft = getInt(rs, "aircraft");
        Integer ships = getInt(rs, "ships");
        Integer missiles = getInt(rs, "missiles");
        Integer nukes = getInt(rs, "nukes");

        int vm_turns = getInt(rs, "vm_turns");
        String color = rs.getString("color");
        int off = getInt(rs, "off");
        int def = getInt(rs, "def");
//        Long money = getLong(rs, "money");
        Integer spies = getInt(rs, "spies");
        Long date = getLong(rs, "date");

        int alliancePosition = rs.getInt("rank");
        int position = rs.getInt("position");
        int continentId = rs.getInt("continent");
        Continent continent = Continent.values[continentId];

        Long project = getLong(rs,"projects");
        if (project == null) project = 0L;

        Long cityTimer = getLong(rs, "timer");
        Long beigeTimer = getLong(rs, "beigeTimer");
        Long projectTimer = getLong(rs, "projectTimer");
        long espionageFull = getLong(rs, "espionageFull");
        String domPolicyStr = rs.getString("dompolicy");

        WarPolicy warPolicy = WarPolicy.parse(warPolicyStr);
        DomesticPolicy domPolicy = domPolicyStr == null ? null : DomesticPolicy.parse(domPolicyStr);

        long entered_vm = 0;
        long leaving_vm = 0;
        if (vm_turns > 0) {
            leaving_vm = TimeUtil.getTurn() + vm_turns;
            entered_vm = leaving_vm;
            if (vm_turns < 14 * 12) {
                entered_vm = leaving_vm - 14 * 12;
            }
        }

        long newProjectBits = 0;
        if (project == null || project == 0) {
            newProjectBits = -1;
        } else {
            for (Project projectObj : Projects.values) {
                if (projectObj.hasLegacy(project)) {
                    newProjectBits |= projectObj.ordinal();
                }
            }
        }

        return new DBNation(
                id,
                nation,
                leader,
                alliance_id,
                last_active,
                score,
                cities,
                domPolicy,
                warPolicy,
                (int) soldiers,
                (int) tanks,
                (int) aircraft,
                (int) ships,
                (int) missiles,
                (int) nukes,
                (int) (spies == null ? 0 : spies),
                (long) entered_vm,
                (long) leaving_vm,
                NationColor.valueOf(color.toUpperCase(Locale.ROOT)),
                date == null ? 0 : date,
                Rank.byId(position),
                0,
                continent,
                newProjectBits,
                cityTimer == null ? 0 : cityTimer,
                projectTimer == null ? 0 : projectTimer,
                beigeTimer == null ? 0 : beigeTimer,
                0L,
                0L,
                0L,
                espionageFull,
                0,
                0,
                0,
                0
        );
    }

    public Set<DBAlliance> getAlliances() {
        synchronized (alliancesById) {
            return new HashSet<>(alliancesById.values());
        }
    }
    public Set<DBAlliance> getAlliances(boolean removeUntaxable, boolean removeInactive, boolean removeApplicants, int topX) {
        Map<Integer, Double> score = new Int2ObjectOpenHashMap<>();
        synchronized (nationsByAlliance) {
            for (Map.Entry<Integer, Map<Integer, DBNation>> entry : nationsByAlliance.entrySet()) {
                int aaId = entry.getKey();
                Map<Integer, DBNation> nationMap = entry.getValue();
                for (DBNation nation : nationMap.values()) {
                    if (removeApplicants && nation.getPositionEnum().id <= Rank.APPLICANT.id) continue;
                    boolean vm = nation.getVm_turns() > 0;
                    if (removeUntaxable && (nation.isGray() || nation.isBeige() || vm)) continue;
                    if (removeInactive && (vm || nation.active_m() > 7200)) continue;
                    score.merge(aaId, nation.getScore(), Double::sum);
                }
            }
        }
        // Sorted
        score = new SummedMapRankBuilder<>(score).sort().get();
        Set<DBAlliance> result = new LinkedHashSet<>();
        for (int aaId : score.keySet()) {
            DBAlliance alliance = getAlliance(aaId);
            if (alliance != null) result.add(alliance);
            if (result.size() >= topX) break;
        }
        return result;
    }
    public Map<Integer, ByteBuffer> getAllMeta(NationMeta key) {
        Map<Integer, ByteBuffer> results = new HashMap<>();
        try (PreparedStatement stmt = prepareQuery("select * FROM NATION_META where AND key = ?")) {
            stmt.setInt(1, key.ordinal());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    ByteBuffer buf = ByteBuffer.wrap(rs.getBytes("meta"));
                    results.put(id, buf);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return results;
    }

    public void setMeta(int nationId, NationMeta key, byte[] value) {
        checkNotNull(key);
        setMeta(nationId, key.ordinal(), value);
    }

    public void setMeta(int nationId, int ordinal, byte[] value) {
        checkNotNull(value);
        long pair = MathMan.pairInt(nationId, ordinal);
        update("INSERT OR REPLACE INTO `NATION_META`(`id`, `key`, `meta`) VALUES(?, ?, ?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, nationId);
            stmt.setInt(2, ordinal);
            stmt.setBytes(3, value);
        });
    }

    public byte[] getMeta(int nationId, NationMeta key) {
        return getMeta(nationId, key.ordinal());
    }
    public byte[] getMeta(int nationId, int ordinal) {
        try (PreparedStatement stmt = prepareQuery("select * FROM NATION_META where id = ? AND key = ?")) {
            stmt.setInt(1, nationId);
            stmt.setInt(2, ordinal);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    return rs.getBytes("meta");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void deleteMeta(int nationId, NationMeta key) {
        update("DELETE FROM NATION_META where id = ? AND key = ?", new ThrowingConsumer<PreparedStatement>() {
            @Override
            public void acceptThrows(PreparedStatement stmt) throws Exception {
                stmt.setInt(1, nationId);
                stmt.setInt(2, key.ordinal());
            }
        });
    }


    public void deleteMeta(NationMeta key) {
        update("DELETE FROM NATION_META where key = ?", new ThrowingConsumer<PreparedStatement>() {
            @Override
            public void acceptThrows(PreparedStatement stmt) throws Exception {
                stmt.setInt(1, key.ordinal());
            }
        });
    }

    public void deleteBeigeReminder(int attacker, int target) {
        update("DELETE FROM `BEIGE_REMINDERS` WHERE `target` = ? AND `attacker` = ?", new ThrowingConsumer<PreparedStatement>() {
            @Override
            public void acceptThrows(PreparedStatement stmt) throws Exception {
                stmt.setInt(1, target);
                stmt.setInt(2, attacker);
            }
        });
    }

    public void purgeOldBeigeReminders() {
        long minTurn = TimeUtil.getTurn() - (14 * 12 + 1);
        String queryStr = "DELETE FROM `BEIGE_REMINDERS` WHERE turn < ?";
        update(queryStr, (ThrowingConsumer<PreparedStatement>) stmt -> stmt.setLong(1, minTurn));
    }

    public void addBeigeReminder(DBNation target, DBNation attacker) {
        String query = "INSERT OR REPLACE INTO `BEIGE_REMINDERS` (`target`, `attacker`, `turn`) values(?,?,?)";
        update(query, new ThrowingConsumer<PreparedStatement>() {
            @Override
            public void acceptThrows(PreparedStatement stmt) throws SQLException {
                stmt.setInt(1, target.getNation_id());
                stmt.setInt(2, attacker.getNation_id());
                stmt.setLong(3, TimeUtil.getTurn());
            }
        });
    }

    public Set<DBNation> getBeigeRemindersByTarget(DBNation nation) {
        try (PreparedStatement stmt = prepareQuery("SELECT attacker from BEIGE_REMINDERS where target = ?")) {
            stmt.setInt(1, nation.getNation_id());

            Set<DBNation> result = new HashSet<>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int attackerId = rs.getInt(1);
                    DBNation other = DBNation.byId(attackerId);
                    if (other != null) {
                        result.add(other);
                    }
                }
            }
            return result;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public Set<DBNation> getBeigeRemindersByAttacker(DBNation nation) {
        try (PreparedStatement stmt = prepareQuery("SELECT target from BEIGE_REMINDERS where attacker = ?")) {
            stmt.setInt(1, nation.getNation_id());

            Set<DBNation> result = new HashSet<>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int attackerId = rs.getInt(1);
                    DBNation other = DBNation.byId(attackerId);
                    if (other != null) {
                        result.add(other);
                    }
                }
            }
            return result;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public void addMetric(DBAlliance alliance, AllianceMetric metric, long turn, double value) {
        checkNotNull(metric);
        String query = "INSERT OR REPLACE INTO `ALLIANCE_METRICS`(`alliance_id`, `metric`, `turn`, `value`) VALUES(?, ?, ?, ?)";

        if (Double.isNaN(value)) {
            return;
        }
        update(query, new ThrowingConsumer<PreparedStatement>() {
            @Override
            public void acceptThrows(PreparedStatement stmt) throws SQLException {
                stmt.setInt(1, alliance.getAlliance_id());
                stmt.setInt(2, metric.ordinal());
                stmt.setLong(3, turn);
                stmt.setDouble(4, value);
            }
        });
    }

    public Map<DBAlliance, Map<AllianceMetric, Map<Long, Double>>> getMetrics(Set<Integer> allianceIds, AllianceMetric metric, long turnStart, long turnEnd) {
        if (allianceIds.isEmpty()) throw new IllegalArgumentException("No metrics provided");
        String allianceQueryStr = StringMan.getString(allianceIds);
        boolean hasTurnEnd = turnEnd > 0 && turnEnd < Long.MAX_VALUE;

        Map<DBAlliance, Map<AllianceMetric, Map<Long, Double>>> result = new HashMap<>();

        String query = "SELECT * FROM ALLIANCE_METRICS WHERE alliance_id in " + allianceQueryStr + " AND metric = ? and turn >= ?" + (hasTurnEnd ? " and turn <= ?" : "");
        query(query, new ThrowingConsumer<PreparedStatement>() {
            @Override
            public void acceptThrows(PreparedStatement stmt) throws Exception {
                stmt.setInt(1, metric.ordinal());
                stmt.setLong(2, turnStart);
                if (hasTurnEnd) stmt.setLong(3, turnEnd);
            }
        }, new ThrowingConsumer<ResultSet>() {
            @Override
            public void acceptThrows(ResultSet rs) throws Exception {
                while (rs.next()) {
                    int allianceId = rs.getInt("alliance_id");
                    AllianceMetric metric = AllianceMetric.values[rs.getInt("metric")];
                    long turn = rs.getLong("turn");
                    double value = rs.getDouble("value");

                    DBAlliance alliance = getOrCreateAlliance(allianceId);
                    result.computeIfAbsent(alliance, f -> new HashMap<>()).computeIfAbsent(metric, f -> new HashMap<>()).put(turn, value);
                }
            }
        });
        return result;
    }

    public Map<DBAlliance, Map<AllianceMetric, Map<Long, Double>>> getMetrics(Set<Integer> allianceIds, Collection<AllianceMetric> metrics, long turnStart, long turnEnd) {
        if (metrics.isEmpty()) throw new IllegalArgumentException("No metrics provided");
        if (allianceIds.isEmpty()) throw new IllegalArgumentException("No metrics provided");
        String allianceQueryStr = StringMan.getString(allianceIds);
        String metricQueryStr = StringMan.getString(metrics.stream().map(Enum::ordinal).collect(Collectors.toList()));
        boolean hasTurnEnd = turnEnd > 0 && turnEnd < Long.MAX_VALUE;

        Map<DBAlliance, Map<AllianceMetric, Map<Long, Double>>> result = new HashMap<>();

        String query = "SELECT * FROM ALLIANCE_METRICS WHERE alliance_id in " + allianceQueryStr + " AND metric in " + metricQueryStr + " and turn >= ?" + (hasTurnEnd ? " and turn <= ?" : "");
        query(query, new ThrowingConsumer<PreparedStatement>() {
            @Override
            public void acceptThrows(PreparedStatement stmt) throws Exception {
                stmt.setLong(1, turnStart);
                if (hasTurnEnd) stmt.setLong(2, turnEnd);
            }
        }, new ThrowingConsumer<ResultSet>() {
            @Override
            public void acceptThrows(ResultSet rs) throws Exception {
                while (rs.next()) {
                    int allianceId = rs.getInt("alliance_id");
                    AllianceMetric metric = AllianceMetric.values[rs.getInt("metric")];
                    long turn = rs.getLong("turn");
                    double value = rs.getDouble("value");

                    DBAlliance alliance = getOrCreateAlliance(allianceId);
                    result.computeIfAbsent(alliance, f -> new HashMap<>()).computeIfAbsent(metric, f -> new HashMap<>()).put(turn, value);
                }
            }
        });
        return result;
    }
    public Map<Integer, Treaty> getTreaties(int allianceId, TreatyType... types) {
        Map<Integer, Treaty> treaties = getTreaties(allianceId);
        Set<TreatyType> typesSet = new HashSet<>(Arrays.asList(types));
        treaties.entrySet().removeIf(t -> !typesSet.contains(t.getValue().getType()));
        return treaties;
    }

    public Map<Integer, Treaty> getTreaties(int allianceId) {
        synchronized (treatiesByAlliance) {
            Map<Integer, Treaty> treaties = treatiesByAlliance.get(allianceId);
            return treaties == null || treaties.isEmpty() ? Collections.EMPTY_MAP : new Int2ObjectOpenHashMap<>(treaties);
        }
    }

    public void setActivity(int nationId, long turn) {
        update("INSERT OR REPLACE INTO `ACTIVITY` (`nation`, `turn`) VALUES(?, ?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, nationId);
            stmt.setLong(2, turn);
        });
    }

    public void setSpyDaily(int attackerId, int defenderId, int turn, int amt) {
        update("INSERT OR REPLACE INTO `SPY_DAILY` (`attacker`, `defender`, `turn`, `amount`) VALUES(?, ?, ?, ?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, attackerId);
            stmt.setInt(2, defenderId);
            stmt.setLong(3, turn);
            stmt.setInt(4, amt);
        });
        long pair = MathMan.pairInt(attackerId, defenderId);
    }

    public Map<Long, Integer> getTargetSpyDailyByTurn(int targetId, long minTurn) {
        try (PreparedStatement stmt = prepareQuery("select * FROM SPY_DAILY WHERE defender = ? AND turn >= ? ORDER BY turn ASC")) {
            stmt.setInt(1, targetId);
            stmt.setLong(2, minTurn);

            Map<Long, Integer> map = new LinkedHashMap<>();

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int attacker = rs.getInt("attacker");
                    long turn = rs.getLong("turn");
                    int amt = rs.getInt("amount");
                    map.put(turn, map.getOrDefault(turn, 0) + amt);
                }
            }
            return map;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public void setLoot(int nationId, long turn, double[] loot) {
        Locutus.imp().getWarDb().cacheSpyLoot(nationId, TimeUtil.getTimeFromTurn(turn), loot);
        update("INSERT OR REPLACE INTO `NATION_LOOT` (`id`, `loot`, `turn`) VALUES(?, ?, ?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, nationId);
            stmt.setBytes(2, ArrayUtil.toByteArray(loot));
            stmt.setLong(3, turn);
        });
    }

    public Map<Integer, Map.Entry<Long, double[]>> getLoot() {
        Map<Integer, Map.Entry<Long, double[]>> result = new LinkedHashMap<>();
        try (PreparedStatement stmt = prepareQuery("select * FROM NATION_LOOT WHERE id IN (SELECT id FROM NATIONS)")) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    byte[] lootBytes = rs.getBytes("loot");
                    double[] loot = ArrayUtil.toDoubleArray(lootBytes);
                    long turn = rs.getLong("turn");
                    int id = rs.getInt("id");

                    AbstractMap.SimpleEntry<Long, double[]> entry = new AbstractMap.SimpleEntry<>(turn, loot);
                    result.put(id, entry);
                }
            }
            return result;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public Map.Entry<Long, double[]> getLoot(int nationId) {
        try (PreparedStatement stmt = prepareQuery("select * FROM NATION_LOOT WHERE id = ? ORDER BY turn DESC LIMIT 1")) {
            stmt.setInt(1, nationId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    byte[] lootBytes = rs.getBytes("loot");
                    double[] loot = ArrayUtil.toDoubleArray(lootBytes);
                    long turn = rs.getLong("turn");

                    return new AbstractMap.SimpleEntry<>(TimeUtil.getTimeFromTurn(turn), loot);
                }
            }
            return null;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public void setSpyActivity(int nationId, long projects, int spies, long timestamp, WarPolicy policy) {
        update("INSERT OR REPLACE INTO `spy_activity` (`nation`, `timestamp`, `projects`, `change`, `spies`) VALUES(?, ?, ?, ?, ?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, nationId);
            stmt.setLong(2, timestamp);
            stmt.setLong(3, projects);
            stmt.setInt(4, policy.ordinal());
            stmt.setInt(5, spies);
        });
    }

    public Set<Long> getActivity(int nationId) {
        return getActivity(nationId, 0);
    }

    public Set<Long> getActivityByDay(int nationId, long minTurn) {
        Set<Long> result = new LinkedHashSet<>();
        for (long turn : getActivity(nationId, minTurn)) {
            result.add(turn / 12);
        }
        return result;
    }

    public Set<Long> getActivity(int nationId, long minTurn) {
        try (PreparedStatement stmt = prepareQuery("select * FROM ACTIVITY WHERE nation = ? AND turn > ? ORDER BY turn ASC")) {
            stmt.setInt(1, nationId);
            stmt.setLong(2, minTurn);

            Set<Long> set = new LinkedHashSet<>();

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    long turn = rs.getLong("turn");
                    set.add(turn);
                }
            }
            return set;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public List<DBSpyUpdate> getSpyActivityByNation(int nationId) {
        try (PreparedStatement stmt = prepareQuery("select * FROM spy_activity WHERE nation = ? ORDER BY timestamp DESC")) {
            stmt.setLong(1, nationId);

            List<DBSpyUpdate> set = new ArrayList<>();

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    DBSpyUpdate entry = new DBSpyUpdate(rs);
                    set.add(entry);
                }
            }
            return set;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public List<DBSpyUpdate> getSpyActivity(long timestamp, long range) {
        try (PreparedStatement stmt = prepareQuery("select * FROM spy_activity WHERE timestamp > ? AND timestamp < ? ORDER BY timestamp ASC")) {
            stmt.setLong(1, timestamp - range);
            stmt.setLong(2, timestamp + range);

            List<DBSpyUpdate> set = new ArrayList<>();

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    DBSpyUpdate entry = new DBSpyUpdate(rs);
                    set.add(entry);
                }
            }
            return set;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public void setCityCount(long date, int nationId, int cities) {
        setNationChange(date, nationId, -1, 0, cities);
    }

    public void setMilChange(long date, int nationId, MilitaryUnit unit, int previous, int current) {
        setNationChange(date, nationId, unit.ordinal(), previous, current);
    }

    public void setNationChange(long date, int nationId, int ordinal, int previous, int current) {
        if (previous == current) return;
        update("INSERT OR REPLACE INTO `NATION_MIL_HISTORY` (`id`, `date`, `unit`, `amount`) VALUES(?, ?, ?, ?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, nationId);
            stmt.setLong(2, date);
            stmt.setInt(3, ordinal);
//                stmt.setLong(4, previous);
            stmt.setInt(4, current);
        });
    }

    public List<Map.Entry<Long, Integer>> getMilitaryHistory(DBNation nation, MilitaryUnit unit) {
        try (PreparedStatement stmt = prepareQuery("select * FROM NATION_MIL_HISTORY WHERE id = ? AND unit = ? ORDER BY date DESC")) {
            stmt.setInt(1, nation.getNation_id());
            stmt.setInt(2, unit.ordinal());

            List<Map.Entry<Long, Integer>> result = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int amt = rs.getInt("amount");
                    long date = rs.getLong("date");
                    result.add(new AbstractMap.SimpleEntry<>(date, amt));
                }
            }

            return result;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public int getMilitary(DBNation nation, MilitaryUnit unit, long time) {
        return getMilitary(nation, unit, time, true);
    }

    public boolean hasBought(DBNation nation, MilitaryUnit unit, long time) {
        int last = nation.getUnits(unit);
        try (PreparedStatement stmt = prepareQuery("select * FROM NATION_MIL_HISTORY WHERE id = ? AND unit = ? ORDER BY date DESC")) {
            stmt.setInt(1, nation.getNation_id());
            stmt.setInt(2, unit.ordinal());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int amt = rs.getInt("amount");
                    if (amt < last) {
                        return true;
                    }
                    long date = rs.getLong("date");
                    if (date < time) {
                        break;
                    }
                    last = amt;
                }
            }
            return false;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public Integer getMilitary(DBNation nation, MilitaryUnit unit, long time, boolean useCurrent) {
        try (PreparedStatement stmt = prepareQuery("select * FROM NATION_MIL_HISTORY WHERE id = ? AND unit = ? AND date < ? ORDER BY date ASC LIMIT 1")) {
            stmt.setInt(1, nation.getNation_id());
            stmt.setInt(2, unit.ordinal());
            stmt.setLong(3, time);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    return rs.getInt("amount");
                }
            }

            return useCurrent ? nation.getUnits(unit) : null;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }



//    public Integer getMilitary(DBNation nation, int ordinal, long time, boolean useCurrent) {
//        try (PreparedStatement stmt = prepareQuery("select * FROM NATION_MIL_HISTORY WHERE date = ? AND unit = ? AND id < ? ORDER BY date DESC LIMIT 1")) {
//            stmt.setInt(1, nation.getNation_id());
//            stmt.setInt(2, ordinal);
//            stmt.setLong(3, time);
//
//            try (ResultSet rs = stmt.executeQuery()) {
//                while (rs.next()) {
//                    return rs.getInt("amount");
//                }
//            }
//
//            return useCurrent ? nation.getUnits(unit) : null;
//        } catch (SQLException e) {
//            e.printStackTrace();
//            throw new RuntimeException(e);
//        }
//    }

    public Map<Integer, List<DBNation>> getNationsByAlliance(Collection<DBNation> nationSet, boolean removeUntaxable, boolean removeInactive, boolean removeApplicants, boolean sorttByScore) {
        nationSet.removeIf(n -> n.getAlliance_id() == 0);
        if (removeUntaxable) {
            nationSet.removeIf(n -> n.getVm_turns() != 0 ||
                    n.isGray() ||
                    n.isBeige());
        } else if (removeInactive) {
            nationSet.removeIf(n -> n.getVm_turns() != 0 || n.getActive_m() > 7200);
        }
        if (removeApplicants) nationSet.removeIf(n -> n.getPosition() <= 1);
        Map<Integer, List<DBNation>> byAlliance = new RankBuilder<>(nationSet).group(DBNation::getAlliance_id).get();

        if (sorttByScore) {
            Map<Integer, Double> byScore = new GroupedRankBuilder<>(byAlliance).sumValues(n -> n.getScore()).sort().get();
            LinkedHashMap<Integer, List<DBNation>> result = new LinkedHashMap<Integer, List<DBNation>>();
            for (Map.Entry<Integer, Double> entry : byScore.entrySet()) {
                result.put(entry.getKey(), byAlliance.get(entry.getKey()));
            }
            byAlliance = result;
        }

        return byAlliance;
    }

    public Map<Integer, List<DBNation>> getNationsByAlliance(boolean removeUntaxable, boolean removeInactive, boolean removeApplicants, boolean sorttByScore) {
        final Map<Integer, Double> score = new Int2ObjectOpenHashMap<>();
        Map<Integer, List<DBNation>> nationsByAllianceFiltered = new Int2ObjectOpenHashMap<>();
        synchronized (nationsByAlliance) {
            for (Map.Entry<Integer, Map<Integer, DBNation>> entry : nationsByAlliance.entrySet()) {
                int aaId = entry.getKey();
                Map<Integer, DBNation> nationMap = entry.getValue();
                for (DBNation nation : nationMap.values()) {
                    if (removeApplicants && nation.getPositionEnum().id <= Rank.APPLICANT.id) continue;
                    boolean vm = nation.getVm_turns() > 0;
                    if (removeUntaxable && (nation.isGray() || nation.isBeige() || vm)) continue;
                    if (removeInactive && (vm || nation.active_m() > 7200)) continue;
                    score.merge(aaId, nation.getScore(), Double::sum);
                    nationsByAllianceFiltered.computeIfAbsent(aaId, f -> new ArrayList<>()).add(nation);
                }
            }
        }
        List<Map.Entry<Integer, List<DBNation>>> sorted = new ArrayList<>(nationsByAllianceFiltered.entrySet());
        sorted.sort((o1, o2) -> Double.compare(score.get(o2.getKey()), score.get(o1.getKey())));
        nationsByAllianceFiltered = new Int2ObjectOpenHashMap<>();
        for (Map.Entry<Integer, List<DBNation>> entry : sorted) {
            nationsByAllianceFiltered.put(entry.getKey(), entry.getValue());
        }
        return nationsByAllianceFiltered;
    }

    public int getMilitaryBuy(DBNation nation, MilitaryUnit unit, long time) {
        int bought = 0;
        int current = nation.getUnits(unit);
        try (PreparedStatement stmt = prepareQuery("select * FROM NATION_MIL_HISTORY WHERE id = ? AND unit = ? ORDER BY date DESC")) {
            stmt.setInt(1, nation.getNation_id());
            stmt.setInt(2, unit.ordinal());
//            stmt.setLong(3, time);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int amt = rs.getInt("amount");
                    if (amt < current) {
                        bought += current - amt;
                    }
                    current = amt;
                    long date = rs.getLong("date");
                    if (date < time) break;
                }
            }
            return bought;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
//    public Map<Long, Integer> getMilitaryBuyByTurn(DBNation nation, MilitaryUnit unit) {
//        try (PreparedStatement stmt = prepareQuery("select * FROM NATION_MIL_HISTORY WHERE id = ? AND unit = ? ORDER BY date DESC")) {
//            stmt.setInt(1, nation.getNation_id());
//            stmt.setInt(2, unit.ordinal());
//
//            Map<Long, Integer> buyMap = new LinkedHashMap<>();
//
//            long lastTurn = 0;
//            Integer lastAmt = null;
//
//            try (ResultSet rs = stmt.executeQuery()) {
//                while (rs.next()) {
//                    int amt = rs.getInt("amount");
//                    long date = rs.getLong("date");
//                    long turn = TimeUtil.getTurn(date);
//
//                    if (lastAmt == null) {
//                        lastAmt = amt;
//                        continue;
//                    }
//                    int bought = lastAmt -
//
//
//                    lastAmt = amt;
//                    lastTurn = turn;
//
//
//                }
//            }
//            return bought;
//        } catch (SQLException e) {
//            e.printStackTrace();
//            throw new RuntimeException(e);
//        }
//    }

    public int getMinMilitary(int nationId, MilitaryUnit unit, long time) {
        try (PreparedStatement stmt = prepareQuery("select * FROM NATION_MIL_HISTORY WHERE id = ? AND unit = ? AND date > ? ORDER BY amount ASC LIMIT 1")) {
            stmt.setInt(1, nationId);
            stmt.setInt(2, unit.ordinal());
            stmt.setLong(3, time);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    return rs.getInt("amount");
                }
            }
            DBNation dbNation = Locutus.imp().getNationDB().getNation(nationId);
            return dbNation != null ? dbNation.getUnits(unit) : 0;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public void setSpies(int nation, int spies) {
        long day = TimeUtil.getDay();
        update("INSERT OR REPLACE INTO `SPIES_BUILDUP` (`nation`, `spies`, `day`) VALUES(?, ?, ?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, nation);
            stmt.setInt(2, spies);
            stmt.setLong(3, day);
        });
    }

    public Map.Entry<Integer, Long> getLatestSpyCount(int nationId, long beforeDay) {
        String queryStr = "SELECT * from SPIES_BUILDUP where nation = ? AND day < ? order by day DESC limit 1";

        try (PreparedStatement stmt = prepareQuery(queryStr)) {
            stmt.setInt(1, nationId);
            stmt.setLong(2, beforeDay);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int spies = rs.getInt("spies");
                    long day = rs.getLong("day");
                    return new AbstractMap.SimpleEntry<>(spies, day);
                }
            }
            return null;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public Map<Integer, Integer> getLastSpiesByNation(Set<Integer> nationIds, long lastDay) {
        String query = "SELECT nation, spies, max(day) as day from SPIES_BUILDUP where nation in " + StringMan.getString(nationIds) + " AND day < ? GROUP BY nation";
        try (PreparedStatement stmt = prepareQuery(query)) {
            stmt.setLong(1, lastDay);

            Map<Integer, Integer> map = new LinkedHashMap<>();

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int nationId = rs.getInt("nation");
                    int spies = rs.getInt("spies");
                    long day = rs.getLong("day");
                    map.put(nationId, spies);
                }
            }
            return map;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public Map<Long, Integer> getSpiesByDay(int nationId) {
        try (PreparedStatement stmt = prepareQuery("select * FROM SPIES_BUILDUP WHERE nation = ? ORDER BY day DESC")) {
            stmt.setInt(1, nationId);

            Map<Long, Integer> map = new LinkedHashMap<>();

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int spies = rs.getInt("spies");
                    long day = rs.getLong("day");
                    map.put(day, spies);
                }
            }
            return map;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

//    public void setProjects(int nationId, Set<Project> projects) {
//        Set<Integer> projectIds = new HashSet<>();
//    }

    public void addRemove(int nationId, int allianceId, long time, Rank rank) {
        update("INSERT INTO `KICKS`(`nation`, `alliance`, `date`, `type`) VALUES(?, ?, ?, ?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, nationId);
            stmt.setInt(2, allianceId);
            stmt.setLong(3, time);
            stmt.setInt(4, rank.id);
        });
    }

    public List<AllianceChange> getNationAllianceHistory(int nationId) {
        try (PreparedStatement stmt = prepareQuery("select * FROM KICKS WHERE nation = ? ORDER BY date ASC")) {
            stmt.setInt(1, nationId);
            List<AllianceChange> list = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery()) {
                int latestAA = 0;
                Rank latestRank = null;
                long latestDate = 0;
                while (rs.next()) {
                    int alliance = rs.getInt("alliance");
                    long date = rs.getLong("date");
                    int type = rs.getInt("type");
                    Rank rank = Rank.byId(type);

                    if (latestRank != null) {
                        list.add(new AllianceChange(latestAA, alliance, latestRank, rank, latestDate));
                    }
                    latestRank = rank;
                    latestAA = alliance;
                    latestDate = date;
                }
                DBNation nation = Locutus.imp().getNationDB().getNation(nationId);
                if (latestRank != null && nation != null) {
                    int newAA = nation.getAlliance_id();
                    Rank newRank = Rank.byId(nation.getPosition());
                    if (newAA != latestAA || latestRank != newRank) {
                        list.add(new AllianceChange(latestAA, newAA, latestRank, newRank, latestDate));
                    }
                }
            }

            return list;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public long getAllianceMemberSeniorityTimestamp(DBNation nation) {
        long now = System.currentTimeMillis();
        if (nation.getPosition() < Rank.MEMBER.id) return Long.MAX_VALUE;
        try (PreparedStatement stmt = prepareQuery("select * FROM KICKS WHERE nation = ? ORDER BY date DESC LIMIT 1")) {
            stmt.setInt(1, nation.getNation_id());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    return rs.getLong("date");
                }
            }
            return Long.MAX_VALUE;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public Map<Integer, Map.Entry<Long, Rank>> getRemovesByNation(int nationId) {
        try (PreparedStatement stmt = prepareQuery("select * FROM KICKS WHERE nation = ? ORDER BY date DESC")) {
            stmt.setInt(1, nationId);

            Map<Integer, Map.Entry<Long, Rank>> kickDates = new LinkedHashMap<>();

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int alliance = rs.getInt("alliance");
                    long date = rs.getLong("date");
                    int type = rs.getInt("type");
                    Rank rank = Rank.byId(type);

                    AbstractMap.SimpleEntry<Long, Rank> dateRank = new AbstractMap.SimpleEntry<>(date, rank);

                    kickDates.putIfAbsent(alliance, dateRank);
                }
            }
            return kickDates;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public List<Map.Entry<Long, Map.Entry<Integer, Rank>>> getRankChanges(int allianceId) {
        try (PreparedStatement stmt = prepareQuery("select * FROM KICKS WHERE alliance = ? ORDER BY date ASC")) {
            stmt.setInt(1, allianceId);

            List<Map.Entry<Long, Map.Entry<Integer, Rank>>> kickDates = new ArrayList<>();

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int nationId = rs.getInt("nation");
                    long date = rs.getLong("date");
                    int type = rs.getInt("type");
                    Rank rank = Rank.byId(type);

                    AbstractMap.SimpleEntry<Integer, Rank> natRank = new AbstractMap.SimpleEntry<>(nationId, rank);
                    AbstractMap.SimpleEntry<Long, Map.Entry<Integer, Rank>> dateRank = new AbstractMap.SimpleEntry<>(date, natRank);

                    kickDates.add(dateRank);
                }
            }
            return kickDates;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public Map<Integer, Map.Entry<Long, Rank>> getRemovesByAlliance(int allianceId) {
        try (PreparedStatement stmt = prepareQuery("select * FROM KICKS WHERE alliance = ? ORDER BY date DESC")) {
            stmt.setInt(1, allianceId);

            Map<Integer, Map.Entry<Long, Rank>> kickDates = new LinkedHashMap<>();

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int nationId = rs.getInt("nation");
                    long date = rs.getLong("date");
                    int type = rs.getInt("type");
                    Rank rank = Rank.byId(type);

                    AbstractMap.SimpleEntry<Long, Rank> dateRank = new AbstractMap.SimpleEntry<>(date, rank);

                    kickDates.putIfAbsent(nationId, dateRank);
                }
            }
            return kickDates;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public Map<Integer, JavaCity> toJavaCity(Map<Integer, DBCity> cities) {
        Map<Integer, JavaCity> result = new HashMap<>();
        for (Map.Entry<Integer, DBCity> entry : cities.entrySet()) {
            result.put(entry.getKey(), entry.getValue().toJavaCity(f -> false));
        }
        return result;
    }
//    private Map.Entry<Long, Map<Integer, JavaCity>> getDBCities(int nationId) {
//        HashMap<Integer, JavaCity> cities = new HashMap<>();
//        long updateFlag = 0;
//        try (PreparedStatement stmt = prepareQuery("select * FROM CITIES WHERE nation = ?")) {
//            stmt.setInt(1, nationId);
//            try (ResultSet rs = stmt.executeQuery()) {
//                while (rs.next()) {
//                    int cityId = rs.getInt("id");
//                    long created = rs.getLong("created");
//                    int daysOld = (int) (TimeUtil.getDay() - created);
//                    double land = rs.getLong("land") / 100d;
//                    JavaCity city = JavaCity.fromBytes(rs.getBytes("improvements"));
//                    city.setAge(daysOld);
//
//                    updateFlag = rs.getLong("update_flag");
//                    cities.put(cityId, city);
//                }
//            }
//            return new AbstractMap.SimpleEntry<>(updateFlag, cities);
//        } catch (SQLException e) {
//            e.printStackTrace();
//            throw new RuntimeException(e);
//        }
//    }
//
//    public Map<Integer, Map<Integer, JavaCity>> getDBCities() {
//        Map<Integer, Map<Integer, JavaCity>> allCities = new HashMap<>();
////        HashMap<Integer, JavaCity> cities = new HashMap<>();
//        long updateFlag = 0;
//        try (PreparedStatement stmt = prepareQuery("select * FROM CITIES")) {
//            try (ResultSet rs = stmt.executeQuery()) {
//                while (rs.next()) {
//                    int nationId = rs.getInt("nation");
//                    int cityId = rs.getInt("id");
//                    long created = rs.getLong("created");
//                    int daysOld = (int) (TimeUtil.getDay() - created);
//                    double land = rs.getLong("land") / 100d;
//                    JavaCity city = JavaCity.fromBytes(rs.getBytes("improvements"));
//                    city.setAge(daysOld);
//
//                    updateFlag = rs.getLong("update_flag");
//
//                    Map<Integer, JavaCity> cities = allCities.computeIfAbsent(nationId, f -> new HashMap<>());
//                    cities.put(cityId, city);
//                }
//            }
//            return allCities;
//        } catch (SQLException e) {
//            e.printStackTrace();
//            throw new RuntimeException(e);
//        }
//    }

    public Map<Integer, DBCity> getCitiesV3(int nation_id) {
        synchronized (citiesByNation) {
            return citiesByNation.getOrDefault(nation_id, Collections.EMPTY_MAP);
        }
    }

    public Map<Integer, Map<Integer, DBCity>> getCitiesV3(Set<Integer> nationIds) {
        Map<Integer, Map<Integer, DBCity>> result = new LinkedHashMap<>();
        for (int id : nationIds) {
            Map<Integer, DBCity> cities = getCitiesV3(id);
            if (cities != null) result.put(id, cities);
        }
        return result;
    }

    public DBCity getCitiesV3ByCityId(int cityId) {
        try (PreparedStatement stmt = prepareQuery("select nation FROM CITY_BUILDS WHERE id = ?")) {
            stmt.setInt(1, cityId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int nationId = rs.getInt("nation");
                    return getDBCity(nationId, cityId);
                }
            }
            return null;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public void saveAllCities() {
        List<Map.Entry<Integer, DBCity>> allCities = new ArrayList<>();
        synchronized (citiesByNation) {
            for (Map.Entry<Integer, Map<Integer, DBCity>> entry : citiesByNation.entrySet()) {
                for (Map.Entry<Integer, DBCity> entry2 : entry.getValue().entrySet()) {
                    allCities.add(Map.entry(entry.getKey(), entry2.getValue()));
                }
            }
        }
        saveCities(allCities);
    }

    public void saveCities(List<Map.Entry<Integer, DBCity>> cities) {
        executeBatch(cities, "INSERT OR REPLACE INTO `CITY_BUILDS`(`id`, `nation`, `created`, `infra`, `land`, `powered`, `improvements`, `update_flag`) VALUES(?, ?, ?, ?, ?, ?, ?, ?)", new ThrowingBiConsumer<Map.Entry<Integer, DBCity>, PreparedStatement>() {
            @Override
            public void acceptThrows(Map.Entry<Integer, DBCity> entry, PreparedStatement stmt) throws Exception {
                int nationId = entry.getKey();
                DBCity city = entry.getValue();
                stmt.setInt(1, city.id);
                stmt.setInt(2, nationId);
                stmt.setLong(3, city.created);
                stmt.setInt(4, (int) (city.infra * 100));
                stmt.setInt(5, (int) (city.land * 100));
                stmt.setBoolean(6, city.powered);
                stmt.setBytes(7, city.buildings);
                stmt.setLong(8, city.fetched);
            }
        });
    }

    public void saveNation(DBNation nations) {
        saveNations(Collections.singleton(nations));
    }

    public void saveNations(Collection<DBNation> nations) {
        if (nations.isEmpty()) return;
        String query = "INSERT OR REPLACE INTO `NATIONS2`(nation_id,nation,leader,alliance_id,last_active,score,cities,domestic_policy,war_policy,soldiers,tanks,aircraft,ships,missiles,nukes,spies,entered_vm,leaving_vm,color,`date`,position,alliancePosition,continent,projects,cityTimer,projectTimer,beigeTimer,warPolicyTimer,domesticPolicyTimer,colorTimer,espionageFull,dc_turn,wars_won,wars_lost,tax_id) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        ThrowingBiConsumer<DBNation, PreparedStatement> setNation = (nation, stmt1) -> {
            stmt1.setInt(1, nation.getNation_id());
            stmt1.setString(2, nation.getNation());
            stmt1.setString(3, nation.getLeader());
            stmt1.setInt(4, nation.getAlliance_id());
            stmt1.setLong(5, nation.lastActiveMs());
            stmt1.setLong(6, (long) (nation.getScore() * 100));
            stmt1.setInt(7, nation.getCities());
            stmt1.setInt(8, nation.getDomesticPolicy().ordinal());
            stmt1.setInt(9, nation.getWarPolicy().ordinal());
            stmt1.setInt(10, nation.getSoldiers());
            stmt1.setInt(11, nation.getTanks());
            stmt1.setInt(12, nation.getAircraft());
            stmt1.setInt(13, nation.getShips());
            stmt1.setInt(14, nation.getMissiles());
            stmt1.setInt(15, nation.getNukes());
            stmt1.setInt(16, nation.getSpies());
            stmt1.setLong(17, nation.getEntered_vm());
            stmt1.setLong(18, nation.getLeaving_vm());
            stmt1.setInt(19, nation.getColor().ordinal());
            stmt1.setLong(20, nation.getDate());
            stmt1.setInt(21, nation.getPosition());
            stmt1.setInt(22, nation.getAlliancePositionId());
            stmt1.setInt(23, nation.getContinent().ordinal());
            stmt1.setLong(24, nation.getProjectBitMask());
            stmt1.setLong(25, nation.getCityTimerAbsoluteTurn());
            stmt1.setLong(26, nation.getProjectAbsoluteTurn());
            stmt1.setLong(28, nation.getBeigeAbsoluteTurn());
            stmt1.setLong(29, nation.getWarPolicyAbsoluteTurn());
            stmt1.setLong(30, nation.getDomesticPolicyAbsoluteTurn());
            stmt1.setLong(31, nation.getColorAbsoluteTurn());
            stmt1.setLong(32, nation.getEspionageFullTurn());
            stmt1.setInt(33, nation.getDc_turn());
            stmt1.setInt(34, nation.getWars_won());
            stmt1.setInt(35, nation.getWars_lost());
            stmt1.setInt(36, nation.getTax_id());
        };
        if (nations.size() == 1) {
            DBNation nation = nations.iterator().next();
            update(query, stmt -> setNation.accept(nation, stmt));
        } else {
            executeBatch(nations, query, setNation);
        }
    }

    private void deleteNations(Set<Integer> ids, Consumer<Event> eventConsumer) {
        Set<Integer> citiesToDelete = new HashSet<>();
        for (int id : ids) {
            DBNation nation;
            synchronized (nationsById) {
                nation = nationsById.get(id);
            }
            if (nation != null) {
                if (nation.getDate() + TimeUnit.MINUTES.toMillis(30) > System.currentTimeMillis()) {
                    // don't delete new nations
                    ids.remove(id);
                    continue;
                }
                synchronized (citiesByNation) {
                    Map<Integer, DBCity> cities = citiesByNation.remove(id);
                    if (cities != null) citiesToDelete.addAll(cities.keySet());
                }
                synchronized (nationsById) {
                    nationsById.remove(id);
                }
                if (nation.getAlliance_id() != 0) {
                    synchronized (nationsByAlliance) {
                        nationsByAlliance.getOrDefault(nation.getAlliance_id(), Collections.EMPTY_MAP)
                                .remove(id);
                    }
                }
                if (eventConsumer != null) eventConsumer.accept(new NationDeleteEvent(nation));
            }
        }
        if (ids.isEmpty()) return;

        for (int id : ids) {
            synchronized (nationsById) {
                DBNation existing = nationsById.remove(id);
                if (existing != null && existing.getAlliance_id() != 0) {
                    synchronized (nationsByAlliance) {
                        nationsByAlliance.getOrDefault(existing.getAlliance_id(), Collections.EMPTY_MAP).remove(existing.getNation_id());
                    }
                }
            }
        }
        if (!citiesToDelete.isEmpty()) {
            deleteCitiesInDB(citiesToDelete);
        }
        deleteNationsInDB(ids);
    }

    private void deleteNationsInDB(Set<Integer> ids) {
        if (ids.size() == 1) {
            int id = ids.iterator().next();
            executeStmt("DELETE FROM NATIONS2 WHERE id = " + id);
        } else {
            try (PreparedStatement stmt2 = getConnection().prepareStatement("DELETE FROM NATIONS WHERE `id` in " + StringMan.getString(ids))) {
                stmt2.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void deleteTreatiesInDB(Set<Integer> ids) {
        if (ids.size() == 1) {
            int id = ids.iterator().next();
            executeStmt("DELETE FROM TREATIES2 WHERE id = " + id);
        } else {
            try (PreparedStatement stmt2 = getConnection().prepareStatement("DELETE FROM TREATIES2 WHERE `id` in " + StringMan.getString(ids))) {
                stmt2.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
