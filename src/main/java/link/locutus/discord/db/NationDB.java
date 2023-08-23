package link.locutus.discord.db;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.politicsandwar.graphql.model.*;
import com.ptsmods.mysqlw.query.builder.SelectBuilder;
import com.ptsmods.mysqlw.table.ColumnType;
import com.ptsmods.mysqlw.table.TablePreset;
import it.unimi.dsi.fastutil.ints.*;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.domains.subdomains.SNationContainer;
import link.locutus.discord.db.entities.DBTreasure;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.apiv3.enums.AlliancePermission;
import link.locutus.discord.apiv3.enums.NationLootType;
import link.locutus.discord.commands.rankings.builder.*;
import link.locutus.discord.db.entities.DBCity;
import link.locutus.discord.apiv3.PoliticsAndWarV3;
import link.locutus.discord.db.entities.*;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.Treaty;
import link.locutus.discord.event.Event;
import link.locutus.discord.event.alliance.AllianceCreateEvent;
import link.locutus.discord.event.alliance.AllianceDeleteEvent;
import link.locutus.discord.event.bank.LootInfoEvent;
import link.locutus.discord.event.city.*;
import link.locutus.discord.event.nation.*;
import link.locutus.discord.event.position.PositionCreateEvent;
import link.locutus.discord.event.position.PositionDeleteEvent;
import link.locutus.discord.event.treasure.TreasureUpdateEvent;
import link.locutus.discord.event.treaty.*;
import link.locutus.discord.pnw.PNWUser;
import link.locutus.discord.util.discord.DiscordUtil;
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
import org.slf4j.LoggerFactory;;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;

public class NationDB extends DBMainV2 {
    private static final Logger LOGGER = LoggerFactory.getLogger(NationDB.class.getSimpleName());
    private final Map<Integer, DBNation> nationsById = new Int2ObjectOpenHashMap<>();
    private final Map<Integer, Map<Integer, DBNation>> nationsByAlliance = new Int2ObjectOpenHashMap<>();
    private final Map<Integer, DBAlliance> alliancesById = new Int2ObjectOpenHashMap<>();
    private final Map<Integer, Map<Integer, DBCity>> citiesByNation = new Int2ObjectOpenHashMap<>();
    private final Map<Integer, DBAlliancePosition> positionsById = new Int2ObjectOpenHashMap<>();
    private final Map<Integer, Map<Integer, DBAlliancePosition>> positionsByAllianceId = new Int2ObjectOpenHashMap<>();
    private final Map<Integer, Map<Integer, Treaty>> treatiesByAlliance = new Int2ObjectOpenHashMap<>();
    private final Set<Integer> dirtyCities = Collections.synchronizedSet(new LinkedHashSet<>());
    private final Set<Integer> dirtyNations = Collections.synchronizedSet(new LinkedHashSet<>());

    private final Map<Integer, DBTreasure> treasuresByNation = new Int2ObjectOpenHashMap<>();
    private final Map<String, DBTreasure> treasuresByName = new ConcurrentHashMap<>();
    private ReportManager reportManager;
    private LoanManager loanManager;

    public NationDB() throws SQLException, ClassNotFoundException {
        super("nations");
    }

    public ReportManager getReportManager() {
        return reportManager;
    }

    public LoanManager getLoanManager() {
        return loanManager;
    }

    public void load() throws SQLException {
        { // Legacy
            if (tableExists("NATIONS")) {
                LOGGER.info("Updating legacy nations");
                updateLegacyNations();
            }
            if (tableExists("TREATIES")) getDb().drop("TREATIES");
            if (tableExists("CITY_INFRA_LAND")) getDb().drop("CITY_INFRA_LAND");
        }

        loadPositions();
        LOGGER.info("Loaded " + positionsById.size() + " positions");
        loadAlliances();
        LOGGER.info("Loaded " + alliancesById.size() + " alliances");
        loadNations();
        LOGGER.info("Loaded " + nationsById.size() + " nations");
        int cities = loadCities();
        LOGGER.info("Loaded " + cities + " cities");
        int treaties = loadTreaties();
        LOGGER.info("Loaded " + treaties + " treaties");

        importLegacyNationLoot(true);

        markDirtyIncorrectNations(true, true);

        // Update nuke dates if missing
        query("SELECT COUNT(*) FROM CITY_BUILDS WHERE nuke_date > 0", f -> {
        }, (ThrowingConsumer<ResultSet>) rs -> {
            if (!rs.next() || rs.getInt(1) == 0) {
                Locutus.imp().getWarDb().loadNukeDates();
            }
        });

        System.out.println("Loading meta...");
        loadAndPurgeMeta();
        System.out.println("Done loading nations/meta");

        loadTreasures();
    }

    public void deleteExpiredTreaties(Consumer<Event> eventConsumer) {
        Set<Treaty> expiredTreaties = new HashSet<>();
        long currentTurn = TimeUtil.getTurn();
        synchronized (treatiesByAlliance) {
            for (Map<Integer, Treaty> allianceTreaties : treatiesByAlliance.values()) {
                for (Treaty treaty : allianceTreaties.values()) {
                    if (treaty.getTurnEnds() < currentTurn) {
                        expiredTreaties.add(treaty);
                    }
                }
            }
        }

        if (!expiredTreaties.isEmpty()) {
            deleteTreaties(expiredTreaties, eventConsumer);
        }
    }

    public DBCity getDBCity(int nationId, int cityId) {
        synchronized (citiesByNation) {
            Map<Integer, DBCity> nationCities = citiesByNation.get(nationId);
            if (nationCities != null) {
                return nationCities.get(cityId);
            }
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
                    if (eventConsumer != null) eventConsumer.accept(new NationChangeUnitEvent(copyOriginal, nation, entry.getKey(), true));
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
                long previousLastActive = nation.lastActiveMs();
                nation.setLastActive(active);

                // only call a new event if it's > 1 minute difference
                if (previousLastActive < active - TimeUnit.MINUTES.toMillis(1)) {
                    if (eventConsumer != null) eventConsumer.accept(new NationChangeActiveEvent(previous, nation));
                }
                return true;
            }
        }
        return false;
    }

    public boolean setCityNukeFromAttack(int nationId, int cityId, long timestamp, Consumer<Event> eventConsumer) {
        if (timestamp <= System.currentTimeMillis() - TimeUnit.DAYS.toMillis(11)) return false;

        DBCity city = Locutus.imp().getNationDB().getDBCity(nationId, cityId);
        if (city != null && city.nuke_date < timestamp) {

            DBCity copyOriginal = eventConsumer == null ? null : new DBCity(city);
            city.nuke_date = timestamp;
            if (copyOriginal != null) eventConsumer.accept(new CityNukeEvent(nationId, copyOriginal, city));

            saveCities(List.of(Map.entry(nationId, city)));
            return true;
        }
        return false;
    }

    public boolean setCityInfraFromAttack(int nationId, int cityId, double infra, long timestamp, Consumer<Event> eventConsumer) {
        DBCity city = getDBCity(nationId, cityId);
        if (city != null && city.fetched < timestamp && Math.round(infra * 100) != Math.round(city.infra * 100)) {
            DBCity previous = new DBCity(city);
            city.infra = infra;
            if (eventConsumer != null) {
                if (Math.round(infra * 100) != Math.round(previous.infra * 100)) {
                    eventConsumer.accept(new CityInfraDamageEvent(nationId, previous, city));
                }
            }
            saveCities(List.of(Map.entry(nationId, city)));
            return true;
        }
        return false;
    }

    public void markNationDirty(int nationId) {
        dirtyNations.add(nationId);
    }

    public void markCityDirty(int nationId, int cityId, long timestamp) {
        if (nationId > 0) {
            DBCity city = getDBCity(nationId, cityId);
            if (city != null && city.fetched > timestamp) return;
        }
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
        int[] updates = saveNations(nationsToSave);
        int numUpdates = Arrays.stream(updates).sum();

        if (!nationsToSave.isEmpty() && numUpdates > 0) {
            executeStmt("DROP TABLE NATIONS");
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
                            if (position == null || !position.hasPermission(perm)) continue middle;
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
        return updateNewAlliances(activeAlliances, eventConsumer);
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

    public void updateAlliances(Consumer<AlliancesQueryRequest> filter, Consumer<Event> eventConsumer) {
        Set<Integer> toDelete = filter == null ? getAlliances().stream().map(DBAlliance::getId).collect(Collectors.toSet()) : new HashSet<>();

        List<Alliance> alliances = Locutus.imp().getV3().fetchAlliances(filter, true, true);
        if (alliances.isEmpty()) return;
        Set<Integer> updated = processUpdatedAlliances(alliances, eventConsumer);
        toDelete.removeAll(updated);

        if (!toDelete.isEmpty()) deleteAlliances(toDelete, eventConsumer);
    }

    private Set<Integer> updateAlliancesById(List<Integer> ids, Consumer<Event> eventConsumer) {
        PoliticsAndWarV3 v3 = Locutus.imp().getV3();

        Set<Integer> fetched = new LinkedHashSet<>();
        if (ids.isEmpty()) return fetched;
        ids = new ArrayList<>(ids);
        Collections.sort(ids);
        for (int i = 0; i < ids.size(); i += 500) {
            int end = Math.min(i + 500, ids.size());
            List<Integer> toFetch = ids.subList(i, end);


            List<Alliance> alliances = v3.fetchAlliances(req -> req.setId(toFetch), true, true);
            fetched.addAll(processUpdatedAlliances(alliances, eventConsumer));
        }

        // delete alliances not returned
        Set<Integer> toDelete = new HashSet<>(ids);
        toDelete.removeAll(fetched);
        if (!toDelete.isEmpty()) {
            deleteAlliances(toDelete, eventConsumer);
        }

        return fetched;
    }

    public void deleteAlliances(Set<Integer> ids, Consumer<Event> eventConsumer) {
        Set<Treaty> treatiesToDelete = new LinkedHashSet<>();
        Set<Integer> positionsToDelete = new LinkedHashSet<>();
        Set<DBNation> dirtyNations = new HashSet<>();

        for (int id : ids) {
            DBAlliance alliance = getAlliance(id);
            if (alliance != null && eventConsumer != null) {
                eventConsumer.accept(new AllianceDeleteEvent(alliance));
            }
            synchronized (nationsByAlliance) {
                Map<Integer, DBNation> nations = nationsByAlliance.remove(id);
                if (nations != null) {
                    for (DBNation nation : nations.values()) {
                        if (nation.getAlliance_id() == id) {
                            DBNation copy = eventConsumer == null ? null : new DBNation(nation);

                            nation.setAlliance_id(0);
                            nation.setAlliancePositionId(0);

                            if (copy != null) eventConsumer.accept(new NationChangeAllianceEvent(copy, nation));
                            dirtyNations.add(nation);
                        }
                    }
                }
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
        if (!treatiesToDelete.isEmpty()) deleteTreaties(treatiesToDelete, eventConsumer);
        if (!dirtyNations.isEmpty()) saveNations(dirtyNations);

        deleteAlliancesInDB(ids);
    }

    public void deleteTreaties(Set<Treaty> treaties, Consumer<Event> eventConsumer) {
        long turn = TimeUtil.getTurn();
        for (Treaty treaty : treaties) {
            boolean removed = false;
            synchronized (treatiesByAlliance) {
                removed |= treatiesByAlliance.getOrDefault(treaty.getFromId(), Collections.EMPTY_MAP).remove(treaty.getToId()) != null;
                removed |= treatiesByAlliance.getOrDefault(treaty.getToId(), Collections.EMPTY_MAP).remove(treaty.getFromId()) != null;
            }
            if (removed && eventConsumer != null) {
                if (treaty.getTurnEnds() <= turn + 1) {
                    eventConsumer.accept(new TreatyExpireEvent(treaty));
                } else {
                    eventConsumer.accept(new TreatyCancelEvent(treaty));
                }
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

    public Set<Integer> processUpdatedAlliances(List<Alliance> alliances, Consumer<Event> eventConsumer) {
        if (alliances.isEmpty()) return Collections.emptySet();

        List<DBAlliance> dirtyAlliances = new ArrayList<>();
        List<DBAlliancePosition> dirtyPositions = new ArrayList<>();
        Set<DBNation> saveNations = new HashSet<>();

        List<DBAlliance> createdAlliances = new ArrayList<>();
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
                    createdAlliances.add(existing);
//                    if (alliance.getDate().getEpochSecond() > System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7))
                    dirtyAlliances.add(existing);
                } else {
                    if (existing.set(alliance, eventConsumer)) {
                        dirtyAlliances.add(existing);
                    }
                }
            }
            if (alliance.getAlliance_positions() != null) {
                Set<Integer> positionIds = alliance.getAlliance_positions().stream().map(f -> f.getId()).collect(Collectors.toSet());
                synchronized (nationsByAlliance) {
                    Map<Integer, DBNation> nations = nationsByAlliance.get(alliance.getId());
                    if (nations != null) {
                        for (DBNation nation : nations.values()) {
                            if (nation.getAlliancePositionId() == 0) continue;
                            if (!positionIds.contains(nation.getAlliancePositionId())) {
                                DBNation copy = eventConsumer == null ? null : new DBNation(nation);
                                nation.setAlliancePositionId(0);
                                if (copy != null) eventConsumer.accept(new NationChangePositionEvent(copy, nation));
                                saveNations.add(nation);
                            }
                        }
                    }
                }

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
                        existing = new DBAlliancePosition(alliance.getId(), v3Position);
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

        if (!createdAlliances.isEmpty() && eventConsumer != null) {
            for (DBAlliance alliance : createdAlliances) {
                eventConsumer.accept(new AllianceCreateEvent(alliance));
            }
        }

        if (!dirtyAlliances.isEmpty()) {
            saveAlliances(dirtyAlliances);
        }
        if (!dirtyPositions.isEmpty()) {
            savePositions(dirtyPositions);
        }
        if (!saveNations.isEmpty()) saveNations(saveNations);

        return alliances.stream().map(Alliance::getId).collect(Collectors.toSet());
    }

    public void saveAlliances(List<DBAlliance> alliances) {
        if (alliances.isEmpty()) return;
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
        if (positions.isEmpty()) return;
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
        if (treaties.isEmpty()) return;
        executeBatch(treaties, "INSERT OR REPLACE INTO `TREATIES2`(`id`, `date`, `type`, `from_id`, `to_id`, `turn_ends`) VALUES(?, ?, ?, ?, ?, ?)",
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
                if (aaId == 0) continue;

                if (alliances) {
                    DBAlliance alliance = getAlliance(aaId);
                    if (alliance == null) {
                        alliancesToFetch.add(aaId);
                    }
                }
                if (positions) {
                    int posId = nation.getAlliancePositionId();
                    if (posId == 0 || nation.getPositionEnum() == Rank.APPLICANT) continue;
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
        PoliticsAndWarV3 v3 = Locutus.imp().getV3();
        List<com.politicsandwar.graphql.model.Treaty> treatiesV3 = v3.fetchTreaties(r -> {});
        if (treatiesV3.isEmpty()) throw new IllegalStateException("No treaties returned from API! (updateTreaties())");

        // Don't call events if first time
        if (treatiesByAlliance.isEmpty()) eventConsumer = f -> {};

        updateTreaties(treatiesV3, eventConsumer, true);
    }

    public void updateTreaties(List<com.politicsandwar.graphql.model.Treaty> treatiesV3, Consumer<Event> eventConsumer, boolean deleteMissing) {
        updateTreaties(treatiesV3, eventConsumer, deleteMissing ? f -> true : f -> deleteMissing);
    }

    public void updateTreaties(List<com.politicsandwar.graphql.model.Treaty> treatiesV3, Consumer<Event> eventConsumer, Predicate<Treaty> deleteMissing) {
        Map<Integer, Map<Integer, Treaty>> treatiesByAACopy = new HashMap<>();
        long turn = TimeUtil.getTurn();
        for (com.politicsandwar.graphql.model.Treaty treaty : treatiesV3) {
            Treaty dbTreaty = new Treaty(treaty);
            if (dbTreaty.isPending()) continue;
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
                    if (deleteMissing.test(previous)) toDelete.add(previous);
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
                        } else if (current.getTurnEnds() > previous.getTurnEnds() + 2) {
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

        if (!toDelete.isEmpty()) deleteTreaties(toDelete, eventConsumer);
    }


    public Set<Integer> updateRecentNations(Consumer<Event> eventConsumer) {
        // get the 1k most recent nations
        List<DBNation> allNations = new ArrayList<>(this.getNationsMatching(f -> f.getVm_turns() == 0));
        // sort by last active (desc)
        allNations.sort(Comparator.comparingLong(DBNation::lastActiveMs).reversed());
        // get the first 1k
        allNations = allNations.subList(0, Math.min(1000, allNations.size()));
        long diff;
        if (allNations.isEmpty()) {
            // 15m
            diff = 15 * 60 * 1000;
        } else {
            // get most recent last active
            long dateStart = allNations.get(0).lastActiveMs();
            // get least
            long dateEnd = allNations.get(allNations.size() - 1).lastActiveMs();
            // get diff
            diff = dateStart - dateEnd;
        }
        // datestart = now - diff
        long dateStart = System.currentTimeMillis() - diff;

        return updateNations(r -> {
            r.setVmode(false);
            r.setActive_since(new Date(dateStart));
        }, eventConsumer);
    }

    public List<Integer> getMostActiveNationIds(int amt) {
        List<Map.Entry<Integer, Long>> nationIdActive = new ArrayList<>();

        long turn = TimeUtil.getTurn();
        Set<Integer> visited = new HashSet<>();
        for (int i = 0; i < amt && !dirtyNations.isEmpty(); i++) {
            try {
                Iterator<Integer> iter = dirtyNations.iterator();
                int nationId = iter.next();
                iter.remove();
                if (visited.add(nationId)) {
                    nationIdActive.add(Map.entry(nationId, Long.MAX_VALUE)); // Always update dirty nations
                }
            } catch (NoSuchElementException ignore) {}
        }

        long now = System.currentTimeMillis();
        if (nationIdActive.size() < amt) {
            synchronized (nationsById) {
                for (DBNation nation : nationsById.values()) {
                    if (nation.getLeaving_vm() > turn) continue;

                    long lastFetched = nation.getLastFetchedUnitsMs();
                    long active = nation.lastActiveMs();
                    long diff = Math.max(active - lastFetched, 0) + Math.max(now - lastFetched, 0);
                    nationIdActive.add(Map.entry(nation.getNation_id(), diff));
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
        return updateNations(ids, true, eventConsumer);
    }
    public Set<Integer> updateNations(List<Integer> ids, boolean allowPadding, Consumer<Event> eventConsumer) {
        Set<Integer> fetched = new LinkedHashSet<>();
        if (ids.isEmpty()) return fetched;
        ids = new ArrayList<>(ids);

        int pad = 500 - (ids.size() % 500);
        if (pad != 500 && allowPadding) {
            ids.addAll(getNewNationIds(pad, new HashSet<>(ids)));
        }
        Collections.sort(ids);

        for (int i = 0; i < ids.size(); i += 500) {
            int end = Math.min(i + 500, ids.size());
            List<Integer> toFetch = ids.subList(i, end);
            fetched.addAll(updateNationsById(toFetch, eventConsumer));
        }
        return fetched;
    }

    public void updateDirtyNations(Consumer<Event> eventConsumer) {
        List<Integer> ids;
        synchronized (dirtyNations) {
            ids = new ArrayList<>(dirtyNations);
            dirtyNations.clear();
        }
        System.out.println("Dirty nation Ids " + ids.size());
        updateNations(ids, eventConsumer);
    }

    private Set<Integer> updateNationsById(List<Integer> ids, Consumer<Event> eventConsumer) {
        if (ids.isEmpty()) return new HashSet<>();

        List<Integer> idsFinal = new ArrayList<>(ids);
        Collections.sort(idsFinal);
        Set<Integer> fetched;
        if (idsFinal.size() > 500) {
            fetched = new LinkedHashSet<>();
            for (int i = 0; i < idsFinal.size(); i += 500) {
                int end = Math.min(i + 500, idsFinal.size());
                List<Integer> toFetch = idsFinal.subList(i, end);
                fetched.addAll(updateNationsById(toFetch, eventConsumer));
            }
        } else {
            fetched = updateNations(r -> r.setId(idsFinal), eventConsumer);
        }
        if (ids.size() >= 500 && fetched.isEmpty()) {
            System.out.println("No nations fetched");
            return fetched;
        }

        Set<Integer> deleted = new HashSet<>();
        for (int id : idsFinal) {
            if (!fetched.contains(id)) deleted.add(id); // getNation(id) == null
        }
        if (!deleted.isEmpty()) deleteNations(deleted, eventConsumer);
        return fetched;
    }

    private List<Integer> getMostOutdatedCities(int amt, Set<Integer> ignoreTheseCityIds) {
        long turn = TimeUtil.getTurn();

        Map<Integer, Long> nationIdFetchDiff = new Int2LongArrayMap();
        Map<Integer, Map<Integer, DBCity>> nationCities = new Int2ObjectOpenHashMap<>();

        synchronized (nationsById) {
            for (DBNation nation : nationsById.values()) {
                if (nation.getLeaving_vm() > turn) continue;
                if (nation.active_m() > 7200) continue; // Ignore nations that are inactive

                Map<Integer, DBCity> cities = getCitiesV3(nation.getNation_id());
                long maxDiff = 0;
                for (DBCity city : cities.values()) {
                    long diff = nation.lastActiveMs() - city.fetched;
                    maxDiff = Math.max(diff, maxDiff);
                }
                if (maxDiff > 0) {
                    nationCities.put(nation.getNation_id(), cities);
                    nationIdFetchDiff.merge(nation.getNation_id(), maxDiff, Math::max);
                }
            }
        }
        List<Map.Entry<Integer, Long>> sorted = new ArrayList<>(nationIdFetchDiff.entrySet());
        sorted.sort((o1, o2) -> Long.compare(o2.getValue(), o1.getValue()));

        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < sorted.size() && amt > 0; i++) {
            int nationId = sorted.get(i).getKey();
            Map<Integer, DBCity> cities = nationCities.get(nationId);
            if (result.size() + cities.size() > amt) continue;
            for (int id : cities.keySet()) {
                if (ignoreTheseCityIds.contains(id)) continue;
                result.add(id);
            }
        }
        return result;
    }

    private List<Integer> getMostActiveNationIdsLimitCities(int numCitiesToFetch, Set<Integer> ignoreTheseNations) {
        long turn = TimeUtil.getTurn();

        Map<Integer, Long> nationIdFetchDiff = new Int2LongArrayMap();
        Map<Integer, Integer> nationCityCount = new Int2IntArrayMap();

        synchronized (nationsById) {
            for (DBNation nation : nationsById.values()) {
                if (nation.getLeaving_vm() > turn) continue;
                if (ignoreTheseNations.contains(nation.getNation_id())) continue;
//                if (nation.active_m() > 7200) continue; // Ignore nations that are inactive
                long maxDiff = 0;
                Map<Integer, DBCity> cities = getCitiesV3(nation.getNation_id());
                if (cities.isEmpty()) {
                    maxDiff = Long.MAX_VALUE;
                } else {
                    for (DBCity city : cities.values()) {
                        long diff = nation.lastActiveMs() - city.fetched;
                        maxDiff = Math.max(diff, maxDiff);
                    }
                }
                if (maxDiff > 0) {
                    nationCityCount.put(nation.getNation_id(), nation.getCities());
                    nationIdFetchDiff.merge(nation.getNation_id(), maxDiff, Math::max);
                }
            }
        }
        List<Map.Entry<Integer, Long>> sorted = new ArrayList<>(nationIdFetchDiff.entrySet());
        sorted.sort((o1, o2) -> Long.compare(o2.getValue(), o1.getValue()));

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
                DBNation nation = getNation(natId);
                if (nation != null && nation.getVm_turns() > 0) continue;
                Map<Integer, DBCity> cities = natEntry.getValue();
                synchronized (cities) {
                    for (DBCity city : cities.values()) {
                        citiesToDeleteToNationId.put(city.id, natId);
                    }
                }
            }
        }

        List<SCityContainer> cities;
        try {
            cities = Locutus.imp().getPnwApiV2().getAllCities().getAllCities();
        } catch (IOException e) {
            e.printStackTrace();
            AlertUtil.error("Failed to fetch cities v2", e);
            return;
        }

        if (cities.isEmpty()) {
            // Nothing fetched??
            AlertUtil.error("Failed to fetch cities v2", "No cities fetched");
            return;
        }

        DBCity buffer = new DBCity();

        int originalDirtySize = dirtyCities.size();

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
                synchronized (citiesByNation) {
                    citiesByNation.computeIfAbsent(nationId, f -> new Int2ObjectOpenHashMap<>())
                            .put(cityId, existing);
                }
            } else {
                double maxInfra = Double.parseDouble(city.getMaxinfra());

                buffer.set(existing);
                existing.set(city);

                if (existing.runChangeEvents(cityId, buffer, eventConsumer)) {
                    dirtyCities.add(cityId);
                }
            }
        }

        int newDirty = dirtyCities.size() - originalDirtySize;
        if (newDirty > 500) {
            System.out.println("Dirty cities " + newDirty);
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
            System.out.println("Delete cities 1 " + citiesToDeleteToNationId.size());
            deleteCitiesInDB(citiesToDeleteToNationId.keySet());
        }
    }

    public void updateAllCities(Consumer<Event> eventConsumer) {
        PoliticsAndWarV3 v3 = Locutus.imp().getV3();
        List<City> cities = v3.fetchCitiesWithInfo(null, true);
        Map<Integer, Map<Integer, City>> nationIdCityIdCityMap = new Int2ObjectOpenHashMap<>();
        for (City city : cities) {
            nationIdCityIdCityMap.computeIfAbsent(city.getNation_id(), f -> new Int2ObjectOpenHashMap<>())
                    .put(city.getId(), city);
        }
        updateCities(nationIdCityIdCityMap, eventConsumer);
    }

    public void updateCitiesOfNations(Set<Integer> nationIds, boolean isAuto, boolean bulk, Consumer<Event> eventConsumer) {
        PoliticsAndWarV3 v3 = Locutus.imp().getV3();

        List<Integer> idList = new ArrayList<>(nationIds);
        int estimatedCitiesToFetch = 0;
        for (int nationId : idList) {
            DBNation nation = getNation(nationId);
            if (nation == null) estimatedCitiesToFetch++;
            else estimatedCitiesToFetch += nation.getCities();
        }

        if (bulk) {
            // Pad with most outdated cities, to make full use of api call
            if (estimatedCitiesToFetch < 490) { // Slightly below 500 to avoid off by 1 errors with api
                int numToFetch = 490 - idList.size();
                List<Integer> mostActiveNations = getMostActiveNationIdsLimitCities(numToFetch, new HashSet<>(idList));
                System.out.println("Fetching active nation cities: " + mostActiveNations.size());
                idList.addAll(mostActiveNations);
            }
        }

        for (int i = 0; i < 500 && !idList.isEmpty(); i += 500) {
            int end = Math.min(i + 500, idList.size());
            List<Integer> toFetch = idList.subList(i, end);
            List<City> cities = v3.fetchCitiesWithInfo(r -> r.setNation_id(toFetch), true);
            Map<Integer, Map<Integer, City>> completeCities = new Int2ObjectOpenHashMap<>();
            System.out.println("Return cities " + cities.size());
            for (City city : cities) {
                completeCities.computeIfAbsent(city.getNation_id(), f -> new Int2ObjectOpenHashMap<>())
                        .put(city.getId(), city);
            }
            updateCities(completeCities, eventConsumer);
        }
    }

    public boolean updateDirtyCities(Consumer<Event> eventConsumer) {
        List<Integer> cityIds = new ArrayList<>();
        PoliticsAndWarV3 v3 = Locutus.imp().getV3();

        while (!dirtyCities.isEmpty()) {
            try {
                Iterator<Integer> iter = dirtyCities.iterator();
                int cityId = iter.next();
                iter.remove();
                cityIds.add(cityId);
            } catch (NoSuchElementException ignore) {
            }
        }

        int pad = 500 - (cityIds.size() % 500);
        if (pad != 500) {
            if (pad <= 10) {
                cityIds.addAll(getNewCityIds(pad, new HashSet<>(cityIds)));
            } else {
                cityIds.addAll(getMostOutdatedCities(pad - 10, new HashSet<>(cityIds)));
            }
        }

        Set<Integer> deletedCities = new HashSet<>();
        Collections.sort(cityIds);

        for (int i = 0; i < 500 && !cityIds.isEmpty(); i += 500) {
            int end = Math.min(i + 500, cityIds.size());
            List<Integer> toFetch = cityIds.subList(i, end);
            List<City> cities = v3.fetchCitiesWithInfo(r -> r.setId(toFetch), true);
            deletedCities.addAll(toFetch);
            for (City city : cities) deletedCities.remove(city.getId());
            updateCities(cities, eventConsumer);
        }
        if (!deletedCities.isEmpty()) {
            System.out.println("Delete cities 2 " + deletedCities.size());
            deleteCities(deletedCities, eventConsumer);
        }
        return true;
    }

    private int lastNewCityFetched = 0;

    private List<Integer> getNewCityIds(int amt, Set<Integer> ignoreIds) {
        Set<Integer> cityIds = new HashSet<>(ignoreIds);
        int maxId = 0;
        synchronized (citiesByNation) {
            for (Map<Integer, DBCity> cityMap : citiesByNation.values()) {
                synchronized (cityMap) {
                    for (DBCity city : cityMap.values()) {
                        maxId = Math.max(city.id, maxId);
                        cityIds.add(city.id);
                    }
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
        PoliticsAndWarV3 v3 = Locutus.imp().getV3();
        List<Integer> newIds = getNewCityIds(500, new HashSet<>());
        Collections.sort(newIds);
        List<City> cities = v3.fetchCitiesWithInfo(r -> r.setId(newIds), true);
        updateCities(cities, eventConsumer);
    }

    /**
     * Update the cities in cache and database by the v3 object
     * @param completeCitiesByNation map of cities by nation id. If an entry is in the map, then all cities for thata nation must be included
     * @return if success
     */
    private void updateCities(Map<Integer, Map<Integer, City>> completeCitiesByNation, Consumer<Event> eventConsumer) {
        updateCities(completeCitiesByNation, true, eventConsumer);
    }
    private void updateCities(Map<Integer, Map<Integer, City>> completeCitiesByNation, boolean deleteMissing, Consumer<Event> eventConsumer) {
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

            Map<Integer, DBCity> existingMap = Collections.EMPTY_MAP;
            synchronized (citiesByNation) {
                Map<Integer, DBCity> map = citiesByNation.get(nationId);
                if (map != null) {
                    existingMap = new HashMap<>(map);
                }
            }

            if (deleteMissing) {
                for (Map.Entry<Integer, DBCity> cityEntry : existingMap.entrySet()) {
                    City city = cities.get(cityEntry.getKey());
                    if (city == null) {
                        synchronized (citiesByNation) {
                            Map<Integer, DBCity> map = citiesByNation.get(nationId);
                            if (map != null) {
                                map.remove(cityEntry.getKey());
                            }
                        }
                        citiesToDelete.add(cityEntry.getKey());
                        if (eventConsumer != null) {
                            eventConsumer.accept(new CityDeleteEvent(nationId, cityEntry.getValue()));
                        }
                    }
                }
            }
            for (Map.Entry<Integer, City> cityEntry : cities.entrySet()) {
                City city = cityEntry.getValue();
                dirtyFlag.set(false);
                DBCity dbCity = processCityUpdate(city, buffer, eventConsumer, dirtyFlag);
                if (dirtyFlag.get()) {
                    dirtyCities.add(Map.entry(city.getNation_id(), dbCity));
                }
            }
        }
        if (!citiesToDelete.isEmpty()) {
            System.out.println("Delete cities 3 " + citiesToDelete.size());
            deleteCitiesInDB(citiesToDelete);
        }
        if (!dirtyCities.isEmpty()) {
            saveCities(dirtyCities);
        }
    }


    public boolean deleteCities(Set<Integer> cityIds, Consumer<Event> eventConsumer) {
        Map<Integer, Integer> cityIdNationId = new HashMap<>();
        synchronized (citiesByNation) {
            for (Map.Entry<Integer, Map<Integer, DBCity>> entry : citiesByNation.entrySet()) {
                Map<Integer, DBCity> nationCities = entry.getValue();
                for (Map.Entry<Integer, DBCity> cityEntry : nationCities.entrySet()) {
                    if (cityIds.contains(cityEntry.getKey())) {
                        cityIdNationId.put(cityEntry.getKey(), entry.getKey());
                    }
                }
            }
        }
        return deleteCities2(cityIdNationId, eventConsumer);
    }

    public boolean deleteCities(Collection<City> cities, Consumer<Event> eventConsumer) {
        Map<Integer, Integer> cityIdNationId = new HashMap<>();
        for (City city : cities) {
            cityIdNationId.put(city.getId(), city.getNation_id());
        }
        return deleteCities2(cityIdNationId, eventConsumer);
    }
    public boolean deleteCities2(Map<Integer, Integer> cityIdNationId, Consumer<Event> eventConsumer) {
        if (cityIdNationId.isEmpty()) return true;
        boolean success = true;
        for (Map.Entry<Integer, Integer> entry : cityIdNationId.entrySet()) {
            int nationId = entry.getValue();
            int cityId = entry.getKey();
            DBCity existing = null;
            synchronized (citiesByNation) {
                Map<Integer, DBCity> map = citiesByNation.get(nationId);
                if (map != null) {
                    existing = map.remove(cityId);
                }
            }
            if (eventConsumer != null && existing != null) {
                eventConsumer.accept(new CityDeleteEvent(nationId, existing));
            }
        }
        deleteCitiesInDB(cityIdNationId.keySet());
        return success;
    }

    private void deleteCitiesInDB(Collection<Integer> ids) {
        if (ids.size() == 1) {
            int id = ids.iterator().next();
            executeStmt("DELETE FROM CITY_BUILDS WHERE id = " + id);
        } else {
            List<Integer> idsSorted = new ArrayList<>(ids);
            Collections.sort(idsSorted);
            executeStmt("DELETE FROM CITY_BUILDS WHERE `id` in " + StringMan.getString(idsSorted));
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

    public Set<DBAlliancePosition> getPositions(int allianceId) {
        if (allianceId == 0) return Collections.emptySet();
        synchronized (positionsByAllianceId) {
            Map<Integer, DBAlliancePosition> positions = positionsByAllianceId.get(allianceId);
            if (positions != null) {
                return new HashSet<>(positions.values());
            }
            return Collections.emptySet();
        }
    }

    private void deletePositionsInDB(Set<Integer> ids) {
        if (ids.size() == 1) {
            int id = ids.iterator().next();
            executeStmt("DELETE FROM POSITIONS WHERE id = " + id);
        } else {
            executeStmt("DELETE FROM POSITIONS WHERE `id` in " + StringMan.getString(ids));
        }
    }

    public void updateCities(List<City> cities, Consumer<Event> eventConsumer) {
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
            if (city.getNation_id() == 510930) System.out.println("Remove:|| Updating city " + city.getId() + " " + city.getName() + " " + city.getNation_id());
            buffer.set(existing);
            existing.set(city);
            if (existing.runChangeEvents(city.getNation_id(), buffer, eventConsumer)) {
                dirtyFlag.set(true);
            }
        } else {
            existing = new DBCity(city);
            if (city.getNation_id() == 510930) System.out.println("Remove:||  New city " + city.getId() + " " + city.getName() + " " + city.getNation_id());
            synchronized (citiesByNation) {
                Map<Integer, DBCity> map = citiesByNation.computeIfAbsent(city.getNation_id(), f -> new Int2ObjectOpenHashMap<>());
                map.put(city.getId(), existing);
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
            NationColor.GRAY, null);
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

    private int loadTreaties() throws SQLException {
        int total = 0;
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
                if (currentTurn > treaty.getTurnEnds()) {
                    if (Locutus.imp() != null) {
                        new TreatyExpireEvent(treaty).post();
                    }
                    treatiesToDelete.add(treaty.getId());
                    continue;
                }

                treatiesByAlliance.computeIfAbsent(treaty.getFromId(), f -> new Int2ObjectOpenHashMap<>()).put(treaty.getToId(), treaty);
                treatiesByAlliance.computeIfAbsent(treaty.getToId(), f -> new Int2ObjectOpenHashMap<>()).put(treaty.getFromId(), treaty);
                total++;
            }
        }

        deleteTreatiesInDB(treatiesToDelete);
        return total;
    }

    private Treaty createTreaty(ResultSet rs) throws SQLException {
        return new Treaty(
                rs.getInt("id"),
                rs.getLong("date"),
                TreatyType.values[rs.getInt("type")],
                rs.getInt("from_id"),
                rs.getInt("to_id"),
                rs.getLong("turn_ends")
        );
    }

    private int loadCities() throws SQLException {
        int total = 0;
        SelectBuilder builder = getDb().selectBuilder("CITY_BUILDS").select("*");
        try (ResultSet rs = builder.executeRaw()) {
            while (rs.next()) {
                Map.Entry<Integer, DBCity> entry = createCity(rs);
                int nationId = entry.getKey();
                DBCity city = entry.getValue();
                citiesByNation.computeIfAbsent(nationId, f -> new Int2ObjectOpenHashMap<>()).put(city.id, city);
                total++;
            }
        }
        return total;
    }

    /**
     * @param rs
     * @return (nation id, city)
     * @throws SQLException
     */
    private Map.Entry<Integer, DBCity> createCity(ResultSet rs) throws SQLException {
        int nationId = rs.getInt("nation");
        DBCity data = new DBCity(rs);
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
                rs.getInt("tax_id"),
                rs.getLong("gdp") / 100d,
                0d
        );
    }

    public void updateNationsV2(boolean includeVM, Consumer<Event> eventConsumer) {
        try {
            List<SNationContainer> nations = Locutus.imp().getPnwApiV2().getNationsByScore(includeVM, 999999, -1).getNationsContainer();

            List<DBNation> toSave = new ArrayList<>();
            Set<Integer> expected = new LinkedHashSet<>(nations.size());
            synchronized (nationsById) {
                for (Map.Entry<Integer, DBNation> entry : nationsById.entrySet()) {
                    if (entry.getValue().getVm_turns() <= 0) expected.add(entry.getValue().getNation_id());
                }
            }
            Set<Integer> dirtyNationCities = new HashSet<>();
            for (SNationContainer nation : nations) {
                DBNation existing = getNation(nation.getNationid());
                if (existing == null) {
                    existing = new DBNation(nation);
                    synchronized (nationsById) {
                        nationsById.put(existing.getNation_id(), existing);
                        if (existing.getAlliance_id() != 0) {
                            synchronized (nationsByAlliance) {
                                nationsByAlliance.computeIfAbsent(existing.getAlliance_id(), f -> new Int2ObjectOpenHashMap<>()).put(existing.getNation_id(), existing);
                            }
                        }
                    }
                    if (eventConsumer != null) {
                        eventConsumer.accept(new NationCreateEvent(null, existing));
                    }
                    toSave.add(existing);
                    System.out.println("Existing is null dirty");
                    dirtyNations.add(nation.getNationid());
                } else {
                    expected.remove(existing.getNation_id());
                    int oldAAId = existing.getAlliance_id();
                    if (existing.updateNationInfo(nation, eventConsumer)) {
                        if (oldAAId != existing.getAlliance_id()) {
                            processNationAllianceChange(oldAAId, existing);
                        }
//                        dirtyNations.add(existing.getNation_id());
                        toSave.add(existing);
                    } else if (Math.round(100 * nation.getInfrastructure()) != Math.round(100 * existing.getInfra())) {
                        dirtyNationCities.add(existing.getNation_id());
                    }
                }
            }

            if (!toSave.isEmpty()) saveNations(toSave);

            if (!dirtyNationCities.isEmpty()) {
                System.out.println("Dirty cities " + dirtyNationCities.size());
                updateCitiesOfNations(dirtyNationCities, true, eventConsumer);
            }

            if (!expected.isEmpty()) {
                System.out.println("Add dirty " + expected.size());
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
    private final Object newNationLock = new Object();
    public List<Integer> getNewNationIds(int amt, Set<Integer> ignoreIds) {
        synchronized (newNationLock) {
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
    }

    public Set<Integer> updateNewNationsById(Consumer<Event> eventConsumer) {
        List<Integer> newIds = getNewNationIds(500, new HashSet<>());
        return updateNationsById(newIds, eventConsumer);
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
        Set<Integer> fetched = updateNations(r -> r.setCreated_after(new Date(minDate)), eventConsumer);
        expected.removeAll(fetched);
        dirtyNations.addAll(expected);
        return fetched;
    }

    public Set<Integer> updateNonVMNations(Consumer<Event> eventConsumer) {
        Set<Integer> currentNations = new LinkedHashSet<>();
        synchronized (nationsById) {
            for (Map.Entry<Integer, DBNation> entry : nationsById.entrySet()) {
                int id = entry.getKey();
                DBNation nation = entry.getValue();
                if (nation.getVm_turns() == 0) {
                    currentNations.add(id);
                }
            }
        }
        Set<Integer> deleted = new HashSet<>();
        Set<Integer> fetched = updateNations(f -> f.setVmode(false), eventConsumer);
        for (int id : currentNations) {
            if (!fetched.contains(id)) deleted.add(id);
        }
        for (int id : deleted) {
            markNationDirty(id);;
        }
        return fetched;
    }

    public Set<Integer> updateAllNations(Consumer<Event> eventConsumer) {
        Set<Integer> currentNations;
        synchronized (nationsById) {
            currentNations = new HashSet<>(nationsById.keySet());
        }
        Set<Integer> deleted = new HashSet<>();
        Set<Integer> fetched = updateNations(f -> {}, eventConsumer);
        for (int id : currentNations) {
            if (!fetched.contains(id)) deleted.add(id);
        }
        for (int id : deleted) {
            markNationDirty(id);
        }
//        deleteNations(deleted, eventConsumer);
        return fetched;
    }

    public void markDirtyIncorrectNations(boolean score, boolean cities) {
        int originalSize = dirtyNations.size();
        for (DBNation nation : getNationsMatching(f -> f.getVm_turns() == 0)) {
            if (score && Math.round(100 * (nation.estimateScore() - nation.getScore())) > 0.01) {
                dirtyNations.add(nation.getNation_id());
            } else if (cities && nation.getCities() != getCitiesV3(nation.getNation_id()).size()) {
                dirtyNations.add(nation.getNation_id());
            }
        }
        int added = dirtyNations.size() - originalSize;
        if (added > 10) {
            System.out.println("Added " + added + " nations to dirty list");
        }
    }

    public Set<Integer> updateNations(Collection<Nation> nations, Consumer<Event> eventConsumer) {
        Map<DBNation, DBNation> nationChanges = new LinkedHashMap<>();
        Set<Integer> nationsFetched = new HashSet<>();
        for (Nation nation : nations) {
            if (nation.getId() != null) {
                nationsFetched.add(nation.getId());
                if (!NationDB.this.dirtyNations.isEmpty()) {
                    NationDB.this.dirtyNations.remove(nation.getId());
                }
            }
            updateNation(nation, eventConsumer, nationChanges::put);
        }
        updateNationCitiesAndPositions(nationChanges, eventConsumer);
        return nationsFetched;

    }

    public Set<Integer> updateNations(Consumer<NationsQueryRequest> filter, Consumer<Event> eventConsumer) {

        Map<DBNation, DBNation> nationChanges = new LinkedHashMap<>();
        Set<Integer> nationsFetched = new HashSet<>();

        Predicate<Nation> onEachNation = nation -> {
            if (nation.getId() != null) {
                nationsFetched.add(nation.getId());
                if (!NationDB.this.dirtyNations.isEmpty()) {
                    NationDB.this.dirtyNations.remove(nation.getId());
                }
            }
            updateNation(nation, eventConsumer, (prev, curr) -> nationChanges.put(curr, prev));
            return false;
        };
        PoliticsAndWarV3 v3 = Locutus.imp().getV3();
        v3.fetchNationsWithInfo(filter, onEachNation);

        updateNationCitiesAndPositions(nationChanges, eventConsumer);
        return nationsFetched;
    }

    public void updateNationCitiesAndPositions(Map<DBNation, DBNation> nationChanges,
                                      Consumer<Event> eventConsumer) {
        if (nationChanges.isEmpty()) return;

        boolean fetchCitiesIfNew = true;
        boolean fetchCitiesIfOutdated = true;
        boolean fetchAlliancesIfOutdated = true;
        boolean fetchPositionsIfOutdated = true;
        boolean fetchMostActiveIfNoneOutdated = true;

        Set<DBNation> nationsToSave = new LinkedHashSet<>();
        for (Map.Entry<DBNation, DBNation> entry : nationChanges.entrySet()) {
            DBNation nation = entry.getKey();
            if (nation == null) nation = entry.getValue();
            nationsToSave.add(nation);
        }
        saveNations(nationsToSave);

        Set<Integer> fetchCitiesOfNations = new HashSet<>();

        if (fetchCitiesIfNew) {
            // num cities has changed
            for (Map.Entry<DBNation, DBNation> entry : nationChanges.entrySet()) {
                DBNation prev = entry.getValue();
                DBNation curr = entry.getKey();
                if (prev == null || curr.getCities() != prev.getCities()) {
                    fetchCitiesOfNations.add(curr.getNation_id());
                }
            }
        }
        if (fetchCitiesIfOutdated) {
            for (Map.Entry<DBNation, DBNation> entry : nationChanges.entrySet()) {
                DBNation prev = entry.getValue();
                DBNation curr = entry.getKey();
                if (prev == null || (Math.round((curr.getScore() - prev.getScore()) * 100) != 0 && Math.round(100 * (curr.estimateScore() - curr.getScore())) != 0)) {
                    fetchCitiesOfNations.add(curr.getNation_id());
                }
            }
        }

        System.out.println("Updated nations.\n" +
                "Changes: " + nationChanges.size() + "\n" +
                "Cities: " + fetchCitiesOfNations.size());


        if (!fetchCitiesOfNations.isEmpty() || (fetchMostActiveIfNoneOutdated)) {
            System.out.println("Update cities " + fetchCitiesOfNations.size());
            updateCitiesOfNations(fetchCitiesOfNations, true, eventConsumer);
        }

        if (fetchAlliancesIfOutdated || fetchPositionsIfOutdated) {
            updateOutdatedAlliances(fetchPositionsIfOutdated, eventConsumer);
        }
    }

    /**
     *
     * @param nation the nation
     * @param eventConsumer any nation events to call
     * @param nationsToSave (previous, current)
     */
    private void updateNation(Nation nation, Consumer<Event> eventConsumer, BiConsumer<DBNation, DBNation> nationsToSave) {
        dirtyNations.remove(nation.getId());

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
        processNationAllianceChange(previous != null ? previous.getAlliance_id() : null, current);
    }
    private void processNationAllianceChange(Integer previousAA, DBNation current) {
        if (previousAA == null) {
            synchronized (nationsById) {
                nationsById.put(current.getNation_id(), current);
            }
            if (current.getAlliance_id() != 0) {
                synchronized (nationsByAlliance) {
                    nationsByAlliance.computeIfAbsent(current.getAlliance_id(),
                            f -> new Int2ObjectOpenHashMap<>()).put(current.getNation_id(), current);
                }
            }
        } else if (previousAA != current.getAlliance_id()) {
            synchronized (nationsByAlliance) {
                if (previousAA != 0) {
                    nationsByAlliance.getOrDefault(previousAA, Collections.EMPTY_MAP).remove(current.getNation_id());
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

    private DBNation updateNationInfo(DBNation base, Nation nation, Consumer<Event> eventConsumer, AtomicBoolean markDirty) {
        DBNation copyOriginal = base == null ? null : new DBNation(base);
        if (base == null) {
            markDirty.set(true);
            base = new DBNation();
        }
        if (base.updateNationInfo(copyOriginal, nation, eventConsumer)) {
            markDirty.set(true);
        }
        processNationAllianceChange(copyOriginal, base);
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
                NationColor.values[rs.getInt("color")],
                null
        );
    }

    public void createTables() {
        {
            // loot_estimates int nation_id, double[] min, double[] max, double[] offset, long lastTurnRevenue, int tax_id
            TablePreset nationTable = TablePreset.create("LOOT_ESTIMATE")
                    .putColumn("nation_id", ColumnType.INT.struct().setPrimary(true).setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("last_turn_revenue", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("last_resolved", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("tax_id", ColumnType.BINARY.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("min", ColumnType.BINARY.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("max", ColumnType.BINARY.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("offset", ColumnType.BINARY.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .create(getDb());

            // negatives apply to nation
            // positives apply to taxes
        }
        {
            TablePreset nationTable = TablePreset.create("NATIONS2")
                    .putColumn("nation_id", ColumnType.INT.struct().setPrimary(true).setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("nation", ColumnType.VARCHAR.struct().setNullAllowed(false).configure(f -> f.apply(32)))
                    .putColumn("leader", ColumnType.VARCHAR.struct().setNullAllowed(false).configure(f -> f.apply(32)))
                    .putColumn("alliance_id", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("last_active", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
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
                    .putColumn("entered_vm", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("leaving_vm", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("color", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("date", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("position", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("alliancePosition", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("continent", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("projects", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("cityTimer", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("projectTimer", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("beigeTimer", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("warPolicyTimer", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("domesticPolicyTimer", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("colorTimer", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("espionageFull", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("dc_turn", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("wars_won", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("wars_lost", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("tax_id", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("gdp", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)));

            nationTable.create(getDb());

            // add gdp column to NATIONS2 BIGINT NOT NULL default 0
            try {
                try (PreparedStatement close = prepareQuery("ALTER TABLE NATIONS2 ADD COLUMN `gdp` BIGINT NOT NULL DEFAULT 0")) {
                    close.execute();
                }
            } catch (SQLException ignore) {
            }

            update("DROP TABLE IF EXISTS NATIONS_WAR_SNAPSHOT");

            TablePreset.create("POSITIONS")
                    .putColumn("id", ColumnType.INT.struct().setPrimary(true).setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("alliance_id", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("name", ColumnType.VARCHAR.struct().setNullAllowed(false).configure(f -> f.apply(32)))
                    .putColumn("date_created", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("position_level", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("rank", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("permission_bits", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .create(getDb());

            TablePreset.create("ALLIANCES")
                    .putColumn("id", ColumnType.INT.struct().setPrimary(true).setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("name", ColumnType.VARCHAR.struct().setNullAllowed(false).configure(f -> f.apply(32)))
                    .putColumn("acronym", ColumnType.VARCHAR.struct().setNullAllowed(false).configure(f -> f.apply(32)))
                    .putColumn("flag", ColumnType.VARCHAR.struct().setNullAllowed(false).configure(f -> f.apply(256)))
                    .putColumn("forum_link", ColumnType.VARCHAR.struct().setNullAllowed(false).configure(f -> f.apply(256)))
                    .putColumn("discord_link", ColumnType.VARCHAR.struct().setNullAllowed(false).configure(f -> f.apply(256)))
                    .putColumn("wiki_link", ColumnType.VARCHAR.struct().setNullAllowed(false).configure(f -> f.apply(256)))
                    .putColumn("dateCreated", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("color", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .create(getDb());

            TablePreset.create("TREATIES2")
                    .putColumn("id", ColumnType.INT.struct().setPrimary(true).setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("date", ColumnType.BIGINT.struct().setPrimary(false).setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("type", ColumnType.INT.struct().setPrimary(false).setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("from_id", ColumnType.INT.struct().setPrimary(false).setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("to_id", ColumnType.INT.struct().setPrimary(false).setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("turn_ends", ColumnType.BIGINT.struct().setPrimary(false).setNullAllowed(false).configure(f -> f.apply(null)))
                    .create(getDb());

            String nationLoot = TablePreset.create("NATION_LOOT3")
                    .putColumn("id", ColumnType.INT.struct().setPrimary(false).setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("total_rss", ColumnType.BINARY.struct().setPrimary(false).setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("date", ColumnType.BIGINT.struct().setPrimary(false).setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("type", ColumnType.INT.struct().setPrimary(false).setNullAllowed(false).configure(f -> f.apply(null)))
                    .buildQuery(getDb().getType());
            nationLoot = nationLoot.replace(");", ", PRIMARY KEY(id, type));");
            getDb().executeUpdate(nationLoot);
        }
        ;

        {
            String query = "CREATE TABLE IF NOT EXISTS `BEIGE_REMINDERS` (`target` INT NOT NULL, `attacker` INT NOT NULL, `turn` BIGINT NOT NULL, PRIMARY KEY(target, attacker))";
            executeStmt(query);
        }

        {
            String nations = "CREATE TABLE IF NOT EXISTS `NATION_META` (`id` BIGINT NOT NULL, `key` BIGINT NOT NULL, `meta` BLOB NOT NULL, PRIMARY KEY(id, key))";
            try (Statement stmt = getConnection().createStatement()) {
                stmt.addBatch(nations);
                stmt.executeBatch();
                stmt.clearBatch();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        ;
        {
            String milup = "CREATE TABLE IF NOT EXISTS `NATION_MIL_HISTORY` (`id` INT NOT NULL, `date` BIGINT NOT NULL, `unit` INT NOT NULL, `amount` INT NOT NULL, PRIMARY KEY(id,date))";
            try (Statement stmt = getConnection().createStatement()) {
                stmt.addBatch(milup);
                stmt.executeBatch();
                stmt.clearBatch();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        ;
        executeStmt("CREATE INDEX IF NOT EXISTS index_mil_unit ON NATION_MIL_HISTORY (unit);");
        executeStmt("CREATE INDEX IF NOT EXISTS index_mil_amount ON NATION_MIL_HISTORY (amount);");
        executeStmt("CREATE INDEX IF NOT EXISTS index_mil_date ON NATION_MIL_HISTORY (date);");
        {
            executeStmt("CREATE TABLE IF NOT EXISTS `CITY_BUILDS` (`id` INT NOT NULL PRIMARY KEY, `nation` INT NOT NULL, `created` BIGINT NOT NULL, `infra` INT NOT NULL, `land` INT NOT NULL, `powered` BOOLEAN NOT NULL, `improvements` BLOB NOT NULL, `update_flag` BIGINT NOT NULL, nuke_date BIGINT NOT NULL)");
            try (PreparedStatement stmt = getConnection().prepareStatement("ALTER TABLE CITY_BUILDS ADD COLUMN nuke_date BIGINT NOT NULL DEFAULT 0")) {
                stmt.executeUpdate();
            } catch (SQLException ignore) {
            }
        }

        String kicks = "CREATE TABLE IF NOT EXISTS `KICKS` (`nation` INT NOT NULL, `alliance` INT NOT NULL, `date` BIGINT NOT NULL, `type` INT NOT NULL)";
        try (Statement stmt = getConnection().createStatement()) {
            stmt.addBatch(kicks);
            stmt.executeBatch();
            stmt.clearBatch();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        executeStmt("CREATE INDEX IF NOT EXISTS index_kicks_nation ON KICKS (nation);");
        executeStmt("CREATE INDEX IF NOT EXISTS index_kicks_alliance ON KICKS (alliance);");
        executeStmt("CREATE INDEX IF NOT EXISTS index_kicks_alliance_date ON KICKS (alliance,date);");

        String spies = "CREATE TABLE IF NOT EXISTS `SPIES_BUILDUP` (`nation` INT NOT NULL, `spies` INT NOT NULL, `day` BIGINT NOT NULL, PRIMARY KEY(nation, day))";
        try (Statement stmt = getConnection().createStatement()) {
            stmt.addBatch(spies);
            stmt.executeBatch();
            stmt.clearBatch();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        String activity = "CREATE TABLE IF NOT EXISTS `activity` (`nation` INT NOT NULL, `turn` BIGINT NOT NULL, PRIMARY KEY(nation, turn))";
        try (Statement stmt = getConnection().createStatement()) {
            stmt.addBatch(activity);
            stmt.executeBatch();
            stmt.clearBatch();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        String activity_m = "CREATE TABLE IF NOT EXISTS `spy_activity` (`nation` INT NOT NULL, `timestamp` BIGINT NOT NULL, `projects` BIGINT NOT NULL, `change` BIGINT NOT NULL, `spies` INT NOT NULL, PRIMARY KEY(nation, timestamp))";
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

        executeStmt("CREATE TABLE IF NOT EXISTS ALLIANCE_METRICS (alliance_id INT NOT NULL, metric INT NOT NULL, turn BIGINT NOT NULL, value DOUBLE NOT NULL, PRIMARY KEY(alliance_id, metric, turn))");
        executeStmt("CREATE TABLE IF NOT EXISTS RADIATION_BY_TURN (continent INT NOT NULL, radiation INT NOT NULL, turn BIGINT NOT NULL, PRIMARY KEY(continent, turn))");

        executeStmt("CREATE TABLE IF NOT EXISTS NATION_DESCRIPTIONS (id INT NOT NULL PRIMARY KEY, description TEXT NOT NULL)");

        executeStmt("CREATE TABLE IF NOT EXISTS TREASURES4 (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, color INT, continent INT, bonus INT NOT NULL, spawn_date BIGINT NOT NULL, nation_id INT NOT NULL, respawn_alert BIGINT NOT NULL)");

        // banned_nations
        //   nation_id: Int
        //  reason: String
        //  date: DateTimeAuto
        //  days_left: Int
        executeStmt("CREATE TABLE IF NOT EXISTS banned_nations (nation_id INT NOT NULL PRIMARY KEY, discord_id BIGINT NOT NULL, reason TEXT NOT NULL, date BIGINT NOT NULL, days_left INT NOT NULL)");
        executeStmt("CREATE INDEX IF NOT EXISTS index_banned_nations_discord_id ON banned_nations (discord_id);");

        purgeOldBeigeReminders();

        //Create table IMPORTED_LOANS
        executeStmt("CREATE TABLE IF NOT EXISTS IMPORTED_LOANS (" +
                        "allianceOrGuild BIGINT NOT NULL, " +
                        "nation_id INT NOT NULL, " +
                        "loan_date BIGINT NOT NULL, " +
                        "loaner_user BIGINT NOT NULL, " +
                        "status INT NOT NULL, " +
                        "principal BLOB NOT NULL, " +
                        "remaining BLOB NOT NULL, " +
                        "date_submitted BIGINT NOT NULL, " +
                        "PRIMARY KEY(allianceOrGuild, nation_id))");
        //Add index for nation_id
        executeStmt("CREATE INDEX IF NOT EXISTS index_imported_loans_nation_id ON IMPORTED_LOANS (nation_id);");

        this.reportManager = new ReportManager(this);

        this.loanManager = new LoanManager(this);
    }

    public void importMultiBans() {
        Map<Integer, DBBan> newBans = new HashMap<>();
        Map<Integer, DBBan> bans = getBansByNation();
        for (Map.Entry<Integer, DBBan> entry : bans.entrySet()) {
            DBBan ban = entry.getValue();
            if (ban.reason == null || ban.reason.isEmpty()) continue;

            String regex = "https://politicsandwar.com/nation/id=(\\d+)";

            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(ban.reason);

            while (matcher.find()) {
                String idStr = matcher.group(1);
                int id = Integer.parseInt(idStr);
                if (bans.containsKey(id)) continue;

                String newReason = "Link to ban of nation: " + ban.nation_id + "\n" + ban.reason;

                DBBan existing = newBans.get(id);
                if (existing == null) {
                    long discordId = ban.discord_id;

                    PNWUser newDiscordId = Locutus.imp().getDiscordDB().getUserFromNationId(id);
                    if (newDiscordId != null) {
                        discordId = newDiscordId.getDiscordId();
                    }

                    existing = new DBBan(id, discordId, newReason, ban.date, 0);
                    newBans.put(id, existing);
                } else {
                    existing.reason = existing.reason + "\n\n" + newReason;
                }
            }
        }
        addBans(new ArrayList<>(newBans.values()), null);
    }




    public void addBans(List<DBBan> bans, Consumer<Event> eventConsumer) {
        String query = "INSERT OR REPLACE INTO banned_nations (nation_id, discord_id, reason, date, days_left) VALUES (?, ?, ?, ?, ?)";
        executeBatch(bans, query, (ThrowingBiConsumer<DBBan, PreparedStatement>) (ban, stmt) -> {
            // use fields, not getters for ban
            stmt.setInt(1, ban.nation_id);
            stmt.setLong(2, ban.discord_id);
            stmt.setString(3, ban.reason);
            stmt.setLong(4, ban.date);
            stmt.setInt(5, ban.days_left);
        });

        if (eventConsumer != null) {
            for (DBBan ban : bans) {
                eventConsumer.accept(new NationBanEvent(ban));
            }
        }
    }

    public List<DBBan> getBansForNation(int nationId) {
        List<DBBan> results = new ObjectArrayList<>();
        String select = "SELECT * FROM banned_nations WHERE nation_id = ?";
        try (PreparedStatement stmt = getConnection().prepareStatement(select)) {
            stmt.setInt(1, nationId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                DBBan ban = new DBBan(rs);
                results.add(ban);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return results;
    }

    public Map<Integer, DBBan> getBansByNation() {
        Map<Integer, DBBan> results = new Object2ObjectOpenHashMap<>();
        String select = "SELECT * FROM banned_nations";
        try (PreparedStatement stmt = getConnection().prepareStatement(select)) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                DBBan ban = new DBBan(rs);
                results.put(ban.nation_id, ban);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return results;
    }


    public List<DBBan> getBansForUser(long discordId) {
        return getBansForUser(discordId, null);
    }

    public List<DBBan> getBansForUser(long discordId, Integer nationIdOrNull) {
        List<DBBan> results = new ObjectArrayList<>();
        if (nationIdOrNull == null) {
            DBNation nation = DiscordUtil.getNation(discordId);
            if (nation != null) {
                nationIdOrNull = nation.getId();
            }
        }
        String select = "SELECT * FROM banned_nations WHERE discord_id = ?";
        if (nationIdOrNull != null) {
            select += " OR nation_id = ?";
        }
        try (PreparedStatement stmt = getConnection().prepareStatement(select)) {
            stmt.setLong(1, discordId);
            if (nationIdOrNull != null) {
                stmt.setInt(2, nationIdOrNull);
            }
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                DBBan ban = new DBBan(rs);
                results.add(ban);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return results;
    }

    private long getLatestBanDate() {
        String select = "SELECT MAX(date) FROM banned_nations";
        try (PreparedStatement stmt = getConnection().prepareStatement(select)) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public void updateBans(Consumer<Event> eventConsumer) throws SQLException {
        // get latest date from bans
        PoliticsAndWarV3 v3 = Locutus.imp().getV3();
        long latestBanDate = getLatestBanDate();
        List<BannedNation> newBans = v3.getBansSince(latestBanDate);
        List<DBBan> bans = new ArrayList<>();
        for (BannedNation newBan : newBans) {
            DBBan ban = new DBBan(newBan);
            bans.add(ban);
        }
        addBans(bans, eventConsumer);

        importMultiBans();
    }

    public void updateTreasures(Consumer<Event> eventConsumer) {
        // get api v3 instance
        PoliticsAndWarV3 v3 = Locutus.imp().getV3();

        // get treasures fetchTreasures
        Set<DBTreasure> treasuresToSave = new HashSet<>();
        List<Treasure> newTreasures = v3.fetchTreasures();
        for (Treasure newTreasure : newTreasures) {
            String name = newTreasure.getName();
            DBTreasure existing = treasuresByName.get(name);
            if (existing == null) {
                existing = new DBTreasure().set(newTreasure);
                treasuresToSave.add(existing);
                treasuresByName.put(existing.getName(), existing);
                if (existing.getNation_id() > 0) {
                    treasuresByNation.put(existing.getNation_id(), existing);
                }
            }
            DBTreasure copy = existing.copy();
            existing.set(newTreasure);

            if (copy != null && !copy.equalsExact(existing)) {
                if (copy.getNation_id() != existing.getNation_id()) {
                    if (copy.getNation_id() > 0) {
                        treasuresByNation.remove(copy.getNation_id(), existing);
                    }
                    if (existing.getNation_id() > 0) {
                        treasuresByNation.put(existing.getNation_id(), existing);
                    }
                }

                treasuresToSave.add(existing);

                // new treasure event
                if (eventConsumer != null) eventConsumer.accept(new TreasureUpdateEvent(copy, existing));
            }
        }
        saveTreasures(treasuresToSave);
    }

    public synchronized void saveTreasures(Collection<DBTreasure> treasures) {
        String insert = "INSERT OR REPLACE INTO TREASURES4 (id, name, color, continent, bonus, spawn_date, nation_id, respawn_alert) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        for (DBTreasure treasure : treasures) {
            try (PreparedStatement stmt = getConnection().prepareStatement(insert, Statement.RETURN_GENERATED_KEYS)) {
                if (treasure.getId() >= 0) {
                    stmt.setInt(1, treasure.getId());
                } else {
                    stmt.setNull(1, Types.INTEGER);
                }
                stmt.setString(2, treasure.getName());
                if (treasure.getColor() == null) {
                    stmt.setNull(3, Types.INTEGER);
                } else {
                    stmt.setInt(3, treasure.getColor().ordinal());
                }
                if (treasure.getContinent() == null) {
                    stmt.setNull(4, Types.INTEGER);
                } else {
                    stmt.setInt(4, treasure.getContinent().ordinal());
                }
                stmt.setInt(5, treasure.getBonus());
                stmt.setLong(6, treasure.getSpawnDate());
                stmt.setInt(7, treasure.getNation_id());
                stmt.setLong(8, treasure.getRespawnAlertDate());
                stmt.executeUpdate();

                try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        treasure.setId(generatedKeys.getInt(1));
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
    }

    public DBTreasure getTreasure(int nationId) {
        return treasuresByNation.get(nationId);
    }

    public Map<String, DBTreasure> getTreasuresByName() {
        return Collections.unmodifiableMap(treasuresByName);
    }

    public void loadTreasures() {
        treasuresByName.clear();
        treasuresByNation.clear();
        String select = "SELECT * FROM TREASURES4";
        try (PreparedStatement stmt = getConnection().prepareStatement(select)) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                int id = rs.getInt("id");
                String name = rs.getString("name");

                Integer colorOrd = getInt(rs, "color");
                NationColor color = colorOrd != null ? NationColor.values[colorOrd] : null;
                Integer continentOrd = getInt(rs, "continent");
                Continent continent = continentOrd != null ? Continent.values[continentOrd] : null;
                int bonus = rs.getInt("bonus");
                long spawnDate = rs.getLong("spawn_date");
                int nation_id = rs.getInt("nation_id");
                long respawnAlert = rs.getLong("respawn_alert");
                DBTreasure treasure = new DBTreasure(id, name, color, bonus, continent, nation_id, spawnDate, respawnAlert);
                treasuresByName.put(treasure.getName(), treasure);
                if (nation_id > 0) {
                    treasuresByNation.put(nation_id, treasure);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public synchronized void addDescription(int id, String description) {
        String query = "INSERT INTO NATION_DESCRIPTIONS (id, description) VALUES (?, ?)";
        try (PreparedStatement stmt = getConnection().prepareStatement(query)) {
            stmt.setInt(1, id);
            stmt.setString(2, description);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public Set<Integer> getDescriptionIds() {
        Set<Integer> ids = new HashSet<>();
        String query = "SELECT id FROM NATION_DESCRIPTIONS";
        try (Statement stmt = getConnection().createStatement()) {
            ResultSet rs = stmt.executeQuery(query);
            while (rs.next()) {
                ids.add(rs.getInt(1));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return ids;
    }

    public Map<Integer, String> getDescriptions() {
        Map<Integer, String> descriptions = new HashMap<>();
        String query = "SELECT * FROM NATION_DESCRIPTIONS";
        try (Statement stmt = getConnection().createStatement()) {
            ResultSet rs = stmt.executeQuery(query);
            while (rs.next()) {
                descriptions.put(rs.getInt("id"), rs.getString("description"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public Map<Long, Map<Continent, Double>> getRadiationByTurns() {
        Map<Long, Map<Continent, Double>> result = new Long2ObjectOpenHashMap<>();
        try (PreparedStatement stmt = getConnection().prepareStatement("SELECT continent, radiation, turn FROM RADIATION_BY_TURN")) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Continent continent = Continent.values[(rs.getInt(1))];
                    double radiation = rs.getInt(2) / 100d;
                    long turn = rs.getLong(3);
                    result.computeIfAbsent(turn, f -> new Object2ObjectOpenHashMap<>()).put(continent, radiation);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return result;
    }
    public Map<Continent, Double> getRadiationByTurn(long turn) {
        Map<Continent, Double> result = new Object2ObjectOpenHashMap<>();
        try (PreparedStatement stmt = getConnection().prepareStatement("SELECT continent, radiation FROM RADIATION_BY_TURN where turn = ?")) {
            stmt.setLong(1,turn);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Continent continent = Continent.values[(rs.getInt(1))];
                    double radiation = rs.getInt(2) / 100d;
                    result.put(continent, radiation);
                }
            }
            return result;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
    public synchronized void addRadiationByTurn(Continent continent, long turn, double radiation) {
        try (PreparedStatement stmt = getConnection().prepareStatement("INSERT OR IGNORE INTO RADIATION_BY_TURN (continent, radiation, turn) VALUES (?, ?, ?)")) {
            stmt.setInt(1, continent.ordinal());
            stmt.setInt(2, (int) (radiation * 100));
            stmt.setLong(3, turn);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public DBNation getNation(String nameOrLeader) {
        synchronized (nationsById) {
            for (DBNation nation : nationsById.values()) {
                if (nation.getNation().equalsIgnoreCase(nameOrLeader)) {
                    return nation;
                }
            }
            for (DBNation nation : nationsById.values()) {
                if (nation.getLeader().equalsIgnoreCase(nameOrLeader)) {
                    return nation;
                }
            }
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

    public Map<Integer, Integer> getAllianceIdByTaxId() {
        Map<Integer, Integer> alliancesByTaxId = new HashMap<>();
        synchronized (nationsByAlliance) {
            for (Map.Entry<Integer, Map<Integer, DBNation>> entry : nationsByAlliance.entrySet()) {
                for (DBNation nation : entry.getValue().values()) {
                    if (nation.getTax_id() > 0) {
                        alliancesByTaxId.put(nation.getTax_id(), entry.getKey());
                    }
                }
            }
        }
        return alliancesByTaxId;
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
                    if (removeUntaxable && (nation.isGray() || nation.isBeige())) continue;
                    if (removeInactive && nation.active_m() > 7200) continue;
                    if ((removeUntaxable || removeInactive) && nation.getVm_turns() > 0) continue;
                    score.merge(aaId, nation.getScore(), Double::sum);
                }
            }
        }
        System.out.println("Score " + score.size());
        // Sorted
        score = new SummedMapRankBuilder<>(score).sort().get();
        System.out.println("Score 2 " + score.size());
        Set<DBAlliance> result = new LinkedHashSet<>();
        for (int aaId : score.keySet()) {
            DBAlliance alliance = getAlliance(aaId);
            if (alliance != null) result.add(alliance);
            if (result.size() >= topX) break;
        }
        return result;
    }

    public void loadAndPurgeMeta() {
        List<Integer> toDelete = new ArrayList<>();

        try (PreparedStatement stmt = prepareQuery("select * FROM NATION_META")) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    int key = rs.getInt("key");
                    byte[] data = rs.getBytes("meta");

                    if (id > 0) {
                        DBNation nation = nationsById.get(id);
                        if (nation != null) {
                            nation.setMetaRaw(key, data);
                        } else {
                            toDelete.add(id);
                        }
                    } else {
                        int idAbs = Math.abs(id);
                        DBAlliance alliance = alliancesById.get(idAbs);
                        if (alliance != null) {
                            alliance.setMetaRaw(key, data);
                        } else {
                            toDelete.add(id);
                        }
                    }

                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        update("DELETE FROM NATION_META where id in " + StringMan.getString(toDelete));
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

    public void deleteMeta(int nationId, NationMeta key) {
        deleteMeta(nationId, key.ordinal());
    }

    public void deleteMeta(int nationId, int keyId) {
        update("DELETE FROM NATION_META where id = ? AND key = ?", new ThrowingConsumer<PreparedStatement>() {
            @Override
            public void acceptThrows(PreparedStatement stmt) throws Exception {
                stmt.setInt(1, nationId);
                stmt.setInt(2, keyId);
            }
        });
    }


    public void deleteMeta(AllianceMeta key) {
        update("DELETE FROM NATION_META where key = ? AND id < 0", new ThrowingConsumer<PreparedStatement>() {
            @Override
            public void acceptThrows(PreparedStatement stmt) throws Exception {
                stmt.setInt(1, key.ordinal());
            }
        });
    }

    public void deleteMeta(NationMeta key) {
        update("DELETE FROM NATION_META where key = ? AND id > 0", new ThrowingConsumer<PreparedStatement>() {
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
                    DBNation other = DBNation.getById(attackerId);
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
                    DBNation other = DBNation.getById(attackerId);
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
        addMetric(alliance, metric, turn, value, false);
    }

    public void addMetric(DBAlliance alliance, AllianceMetric metric, long turn, double value, boolean ignore) {
        checkNotNull(metric);
        String query = "INSERT OR " + (ignore ? "IGNORE" : "REPLACE") + " INTO `ALLIANCE_METRICS`(`alliance_id`, `metric`, `turn`, `value`) VALUES(?, ?, ?, ?)";

        if (!Double.isFinite(value)) {
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

    public Map<DBAlliance, Map<AllianceMetric, Map<Long, Double>>> getMetrics(Set<Integer> allianceIds, AllianceMetric metric, long turn) {
        if (allianceIds.isEmpty()) throw new IllegalArgumentException("No metrics provided");
        String allianceQueryStr = StringMan.getString(allianceIds);

        Map<DBAlliance, Map<AllianceMetric, Map<Long, Double>>> result = new HashMap<>();

        String query = "SELECT * FROM ALLIANCE_METRICS WHERE alliance_id in " + allianceQueryStr + " AND metric = ? and turn <= ? GROUP BY alliance_id ORDER BY turn DESC LIMIT " + allianceIds.size();
        query(query, new ThrowingConsumer<PreparedStatement>() {
            @Override
            public void acceptThrows(PreparedStatement stmt) throws Exception {
                stmt.setInt(1, metric.ordinal());
                stmt.setLong(2, turn);
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
                    if (!result.containsKey(alliance)) {
                        result.computeIfAbsent(alliance, f -> new HashMap<>()).computeIfAbsent(metric, f -> new HashMap<>()).put(turn, value);
                    }
                }
            }
        });
        return result;
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

    private ConcurrentHashMap<Integer, Long> turnActivityCache = new ConcurrentHashMap<>();

    public void setActivity(int nationId, long turn) {
        if (turnActivityCache.computeIfAbsent(nationId, f -> 0L) >= turn) return; // already set (or newer
        update("INSERT OR REPLACE INTO `ACTIVITY` (`nation`, `turn`) VALUES(?, ?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, nationId);
            stmt.setLong(2, turn);
        });
    }

    private int[] saveNationLoot(List<LootEntry> entries) {
        if (entries.isEmpty()) return new int[0];
        /*
         .putColumn("id", ColumnType.INT.struct().setPrimary(false).setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("total_rss", ColumnType.INT.struct().setPrimary(false).setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("date", ColumnType.INT.struct().setPrimary(false).setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("type", Col
         */
        long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1);
        for (LootEntry entry : entries) {
            if (entry.getDate() < cutoff) continue;
            new LootInfoEvent(entry).post();
        }

        String query = "INSERT OR REPLACE INTO `NATION_LOOT3` (`id`, `total_rss`, `date`, `type`) VALUES(?,?,?,?)";
        ThrowingBiConsumer<LootEntry, PreparedStatement> setLoot = new ThrowingBiConsumer<LootEntry, PreparedStatement>() {
            @Override
            public void acceptThrows(LootEntry entry, PreparedStatement stmt) throws Exception {
                stmt.setInt(1, entry.isAlliance() ? -entry.getId() : entry.getId());
                stmt.setBytes(2, ArrayUtil.toByteArray(entry.getTotal_rss()));
                stmt.setLong(3, entry.getDate());
                stmt.setInt(4, entry.getType().ordinal());
            }
        };
        if (entries.size() == 1) {
            LootEntry entry = entries.iterator().next();
            return new int[]{update(query, stmt -> setLoot.accept(entry, stmt))};
        } else {
            return executeBatch(entries, query, setLoot);
        }
    }

    private void importLegacyNationLoot(boolean fromAttacks) throws SQLException {
        List<LootEntry> lootInfoList = new ArrayList<>();
        if (tableExists("NATION_LOOT")) {

            try (PreparedStatement stmt = prepareQuery("select * FROM NATION_LOOT WHERE id IN (SELECT nation_id FROM NATIONS2)")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        byte[] lootBytes = rs.getBytes("loot");
                        double[] loot = ArrayUtil.toDoubleArray(lootBytes);
                        long turn = rs.getLong("turn");
                        int id = rs.getInt("id");

                        lootInfoList.add(new LootEntry(
                                id,
                                loot,
                                TimeUtil.getTimeFromTurn(turn),
                                NationLootType.ESPIONAGE
                        ));
                    }
                }
            }

            synchronized (this) {
                getDb().drop("NATION_LOOT");
            }
        }

        if (fromAttacks) {
            Map<Integer, Map.Entry<Long, double[]>> nationLoot = Locutus.imp().getWarDb().getNationLootFromAttacksLegacy(0);
            for (Map.Entry<Integer, Map.Entry<Long, double[]>> entry : nationLoot.entrySet()) {
                int nationId = entry.getKey();
                long date = entry.getValue().getKey();
                double[] loot = entry.getValue().getValue();
                NationLootType type = NationLootType.WAR_LOSS;
                lootInfoList.add(new LootEntry(nationId, loot, date, type));
            }

            if (!lootInfoList.isEmpty()) {
                saveNationLoot(lootInfoList);
            }
        }
    }

    public void saveAllianceLoot(int allianceId, long date, double[] loot, NationLootType type) {
        LootEntry entry = new LootEntry(-allianceId, loot, date, type);
        DBAlliance aa = DBAlliance.get(allianceId);
        if (aa != null) {
            aa.setLoot(entry);
        }
        saveNationLoot(List.of(entry));
    }
    public void saveLoot(int nationId, long date, double[] loot, NationLootType type) {
        LootEntry entry = new LootEntry(nationId, loot, date, type);
        saveNationLoot(List.of(entry));
    }

    public LootEntry getAllianceLoot(int allianceId) {
        return getLoot(-allianceId);
    }

    public LootEntry getLoot(int nationId) {
        try (PreparedStatement stmt = prepareQuery("select * FROM NATION_LOOT3 WHERE id = ? ORDER BY `date` DESC LIMIT 1")) {
            stmt.setInt(1, nationId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    return new LootEntry(rs);
                }
            }
            return null;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }


    public Map<Integer, LootEntry> getNationLootMap() {
        Map<Integer, LootEntry> result = new Int2ObjectOpenHashMap<>();
        try (PreparedStatement stmt = prepareQuery("select * FROM NATION_LOOT3 WHERE id > 0")) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    LootEntry entry = new LootEntry(rs);
                    LootEntry existing = result.get(entry.getId());
                    if (existing == null || existing.getDate() < entry.getDate()) {
                        result.put(entry.getId(), entry);
                    }
                }
            }
            return result;
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

    public Map<Long, Set<Integer>> getActivityByDay(long minDate, Predicate<Integer> allowNation) {
        long minTurn = TimeUtil.getTurn(minDate);
        try (PreparedStatement stmt = prepareQuery("select nation, (`turn`/12) FROM ACTIVITY WHERE turn > ?")) {
            stmt.setLong(1, minTurn);

            Map<Long, Set<Integer>> result = new Long2ObjectOpenHashMap<>();

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt(1);
                    if (!allowNation.test(id)) continue;
                    long day = rs.getLong(2);
                    result.computeIfAbsent(day, f -> new IntOpenHashSet()).add(id);
                }
            }
            return result;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
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

    public List<DBSpyUpdate> getSpyActivityByNation(int nationId, long mindate) {
        try (PreparedStatement stmt = prepareQuery("select * FROM spy_activity WHERE nation = ? AND timestamp > ? ORDER BY timestamp DESC")) {
            stmt.setLong(1, nationId);
            stmt.setLong(2, mindate);

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

    public Map<Integer, List<DBNation>> getNationsByAlliance(Collection<DBNation> nationSet, boolean removeUntaxable, boolean removeInactive, boolean removeApplicants, boolean sortByScore) {
        final Int2DoubleMap scoreMap = new Int2DoubleOpenHashMap();
        Int2ObjectOpenHashMap<List<DBNation>> nationsByAllianceFiltered = new Int2ObjectOpenHashMap<>();

        long activeCutoff = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(7200);
        long turnNow = TimeUtil.getTurn();
        for (DBNation nation : nationSet) {
            int aaId = nation.getAlliance_id();
            if (aaId == 0) continue;
            if (removeApplicants && nation.getPositionEnum().id <= Rank.APPLICANT.id) continue;
            if (removeUntaxable && (nation.isGray() || nation.isBeige())) continue;
            if (removeInactive && (nation.lastActiveMs() < activeCutoff)) continue;
            if ((removeUntaxable || removeInactive) && nation.getLeaving_vm() > turnNow) continue;
            nationsByAllianceFiltered.computeIfAbsent(aaId, f -> new ObjectArrayList<>()).add(nation);
            // merge nation.getScore() into scoreMap
            scoreMap.merge(aaId, nation.getScore(), Double::sum);
        }
        if (sortByScore) {
            IntArrayList aaIds = new IntArrayList(scoreMap.keySet());
            aaIds.sort((IntComparator) (id1, id2) -> Double.compare(scoreMap.get(id2), scoreMap.get(id1)));
            Int2ObjectLinkedOpenHashMap<List<DBNation>> sortedMap = new Int2ObjectLinkedOpenHashMap<>(nationsByAllianceFiltered.size());
            for (int aaId : aaIds) {
                sortedMap.put(aaId, nationsByAllianceFiltered.get(aaId));
            }
            return sortedMap;
        } else {
            return nationsByAllianceFiltered;
        }
    }

    public Map<Integer, List<DBNation>> getNationsByAlliance(boolean removeUntaxable, boolean removeInactive, boolean removeApplicants, boolean sortByScore) {
        final Int2DoubleMap scoreMap = new Int2DoubleOpenHashMap();
        Int2ObjectOpenHashMap<List<DBNation>> nationsByAllianceFiltered = new Int2ObjectOpenHashMap<>();
        synchronized (nationsByAlliance) {
            long activeCutoff = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(7200);
            long turnNow = TimeUtil.getTurn();
            for (Map.Entry<Integer, Map<Integer, DBNation>> entry : nationsByAlliance.entrySet()) {
                int aaId = entry.getKey();
                Map<Integer, DBNation> nationMap = entry.getValue();
                double score = 0;
                for (DBNation nation : nationMap.values()) {
                    if (removeApplicants && nation.getPositionEnum().id <= Rank.APPLICANT.id) continue;
                    if (removeUntaxable && (nation.isGray() || nation.isBeige())) continue;
                    if (removeInactive && (nation.lastActiveMs() < activeCutoff)) continue;
                    if ((removeUntaxable || removeInactive) && nation.getLeaving_vm() > turnNow) continue;
                    score += nation.getScore();
                    nationsByAllianceFiltered.computeIfAbsent(aaId, f -> new ObjectArrayList<>()).add(nation);
                }
                scoreMap.put(aaId, score);
            }
        }
        if (sortByScore) {
            IntArrayList aaIds = new IntArrayList(scoreMap.keySet());
            aaIds.sort((IntComparator) (id1, id2) -> Double.compare(scoreMap.get(id2), scoreMap.get(id1)));
            Int2ObjectLinkedOpenHashMap<List<DBNation>> sortedMap = new Int2ObjectLinkedOpenHashMap<>(nationsByAllianceFiltered.size());
            for (int aaId : aaIds) {
                sortedMap.put(aaId, nationsByAllianceFiltered.get(aaId));
            }
            return sortedMap;
        } else {
            return nationsByAllianceFiltered;
        }
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

    public long getAllianceMemberSeniorityTimestamp(DBNation nation, Long snapshotDate) {
        long now = System.currentTimeMillis();
        if (nation.getPosition() < Rank.MEMBER.id) return Long.MAX_VALUE;
        try (PreparedStatement stmt = prepareQuery("select * FROM KICKS WHERE nation = ? " + (snapshotDate != null ? "AND DATE < " + snapshotDate : "") + " ORDER BY date DESC LIMIT 1")) {
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

    public Map<Integer, List<Map.Entry<Long, Map.Entry<Integer, Rank>>>> getRemovesByAlliance(Set<Integer> alliances, long cutoff) {
        return getRemovesByAlliance(Locutus.imp().getNationDB().getRemovesByNationAlliance(alliances, cutoff));
    }
    public Map<Integer, List<Map.Entry<Long, Map.Entry<Integer, Rank>>>> getRemovesByAlliance(Map<Integer, Map<Integer, Map.Entry<Long, Rank>>> removes) {
        Map<Integer, List<Map.Entry<Long, Map.Entry<Integer, Rank>>>> removesByAlliance = new HashMap<>();
        for (Map.Entry<Integer, Map<Integer, Map.Entry<Long, Rank>>> entry : removes.entrySet()) {
            int nationId = entry.getKey();
            Map<Integer, Map.Entry<Long, Rank>> allianceRanks = entry.getValue();

            for (Map.Entry<Integer, Map.Entry<Long, Rank>> allianceRank : allianceRanks.entrySet()) {
                int allianceId = allianceRank.getKey();
                Map.Entry<Long, Rank> dateRank = allianceRank.getValue();
                long date = dateRank.getKey();
                Rank rank = dateRank.getValue();

                AbstractMap.SimpleEntry<Integer, Rank> natRank = new AbstractMap.SimpleEntry<>(nationId, rank);
                AbstractMap.SimpleEntry<Long, Map.Entry<Integer, Rank>> dateNationRank = new AbstractMap.SimpleEntry<>(date, natRank);

                List<Map.Entry<Long, Map.Entry<Integer, Rank>>> kickDates = removesByAlliance.computeIfAbsent(allianceId, k -> new ArrayList<>());
                kickDates.add(dateNationRank);
            }
        }
        return removesByAlliance;
    }

    public Map<Integer, Map<Integer, Map.Entry<Long, Rank>>> getRemovesByNationAlliance(Set<Integer> alliances, long cutoff) {
        if (alliances.isEmpty()) return Collections.emptyMap();
        Map<Integer, Map<Integer, Map.Entry<Long, Rank>>> kickDates = new LinkedHashMap<>();

        if (alliances.size() == 1) {
            int alliance = alliances.iterator().next();
            Map<Integer, Map.Entry<Long, Rank>> result = getRemovesByAlliance(alliance, cutoff);
            // map to: nation -> alliance - > Long, Rank
            for (Map.Entry<Integer, Map.Entry<Long, Rank>> entry : result.entrySet()) {
                kickDates.computeIfAbsent(entry.getKey(), k -> new LinkedHashMap<>()).put(alliance, entry.getValue());
            }
        } else {
            try (PreparedStatement stmt = prepareQuery("select * FROM KICKS WHERE alliance in " + StringMan.getString(alliances) + " " + (cutoff > 0 ? " AND date > ? " : "") + "ORDER BY date DESC")) {
                if (cutoff > 0) {
                    stmt.setLong(1, cutoff);
                }
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        int nationId = rs.getInt("nation");
                        int alliance = rs.getInt("alliance");
                        long date = rs.getLong("date");
                        int type = rs.getInt("type");
                        Rank rank = Rank.byId(type);
                        kickDates.computeIfAbsent(nationId, k -> new LinkedHashMap<>()).put(alliance, new AbstractMap.SimpleEntry<>(date, rank));
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
        return kickDates;
    }

    public Map<Integer, Map.Entry<Long, Rank>> getRemovesByAlliance(int allianceId) {
        return getRemovesByAlliance(allianceId, 0L);
    }
    public Map<Integer, Map.Entry<Long, Rank>> getRemovesByAlliance(int allianceId, long cutoff) {
        try (PreparedStatement stmt = prepareQuery("select * FROM KICKS WHERE alliance = ? " + (cutoff > 0 ? " AND date > ? " : "") + "ORDER BY date DESC")) {
            stmt.setInt(1, allianceId);
            if (cutoff > 0) {
                stmt.setLong(2, cutoff);
            }

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
            return Collections.unmodifiableMap(citiesByNation.getOrDefault(nation_id, Collections.EMPTY_MAP));
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

    public Map.Entry<Integer, DBCity> getCitiesV3ByCityId(int cityId) {
        return getCitiesV3ByCityId(cityId, false, null);
    }

    public Map.Entry<Integer, DBCity> getCitiesV3ByCityId(int cityId, boolean fetch, Consumer<Event> eventConsumer) {
        for (Map.Entry<Integer, Map<Integer, DBCity>> entry : citiesByNation.entrySet()) {
            Map<Integer, DBCity> cities = entry.getValue();
            if (cities.containsKey(cityId)) {
                DBCity city = cities.get(cityId);
                return Map.entry(entry.getKey(), city);
            }
        }
        if (fetch) {
            synchronized (dirtyCities) {
                dirtyCities.add(cityId);
            }
            updateDirtyCities(eventConsumer);
            return getCitiesV3ByCityId(cityId, false, eventConsumer);
        }
        return null;
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
        if (cities.isEmpty()) return;
        executeBatch(cities, "INSERT OR REPLACE INTO `CITY_BUILDS`(`id`, `nation`, `created`, `infra`, `land`, `powered`, `improvements`, `update_flag`, `nuke_date`) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?)", new ThrowingBiConsumer<Map.Entry<Integer, DBCity>, PreparedStatement>() {
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
                stmt.setLong(9, city.nuke_date);
            }
        });
    }

    public void saveNation(DBNation nations) {
        saveNations(Collections.singleton(nations));
    }

    public Map<Integer, Set<DBNation>> getWarSnapshots(Set<Integer> warIds) {
        if (warIds.isEmpty()) return Collections.emptyMap();
        String query = "SELECT * FROM NATIONS_WAR_SNAPSHOT WHERE war_id IN (" + warIds.stream().map(String::valueOf).collect(Collectors.joining(",")) + ")";

        Map<Integer, Set<DBNation>> result = new HashMap<>();
        try (PreparedStatement stmt = prepareQuery(query)) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int warId = rs.getInt("war_id");
                    DBNation nation = createNation(rs);
                    Set<DBNation> nations = result.computeIfAbsent(warId, f -> new HashSet<>());
                    nations.add(nation);
                }
            }
            return result;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }

    }

    private ThrowingBiConsumer<DBNation, PreparedStatement> setNation() {
        return (nation, stmt1) -> {
            stmt1.setInt(1, nation.getNation_id());
            stmt1.setString(2, nation.getNation());
            stmt1.setString(3, nation.getLeader());
            stmt1.setInt(4, nation.getAlliance_id());
            stmt1.setLong(5, nation.lastActiveMs());
            stmt1.setLong(6, Math.round(nation.getScore() * 100d));
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
            stmt1.setLong(27, nation.getBeigeAbsoluteTurn());
            stmt1.setLong(28, nation.getWarPolicyAbsoluteTurn());
            stmt1.setLong(29, nation.getDomesticPolicyAbsoluteTurn());
            stmt1.setLong(30, nation.getColorAbsoluteTurn());
            stmt1.setLong(31, nation.getEspionageFullTurn());
            stmt1.setInt(32, nation.getDc_turn());
            stmt1.setInt(33, nation.getWars_won());
            stmt1.setInt(34, nation.getWars_lost());
            stmt1.setInt(35, nation.getTax_id());
            stmt1.setLong(36, Math.round(100 * nation.getGNI()));
        };
    }

    public int[] saveNations(Collection<DBNation> nations) {
        if (nations.isEmpty()) return new int[0];
        String query = "INSERT OR REPLACE INTO `NATIONS2`(nation_id,nation,leader,alliance_id,last_active,score,cities,domestic_policy,war_policy,soldiers,tanks,aircraft,ships,missiles,nukes,spies,entered_vm,leaving_vm,color,`date`,position,alliancePosition,continent,projects,cityTimer,projectTimer,beigeTimer,warPolicyTimer,domesticPolicyTimer,colorTimer,espionageFull,dc_turn,wars_won,wars_lost,tax_id,gdp) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        ThrowingBiConsumer<DBNation, PreparedStatement> setNation = setNation();
        if (nations.size() == 1) {
            DBNation nation = nations.iterator().next();
            return new int[]{update(query, stmt -> setNation.accept(nation, stmt))};
        } else {
            return executeBatch(nations, query, setNation);
        }
    }

    public void deleteNations(Set<Integer> ids, Consumer<Event> eventConsumer) {
        Set<Integer> citiesToDelete = new HashSet<>();
        Set<Integer> deleteInDb = new HashSet<>();
        for (int id : new HashSet<>(ids)) {
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
            } else {
                ids.remove(id);
            }
        }
        if (ids.isEmpty()) return;

        if (!citiesToDelete.isEmpty()) {
            System.out.println("Delete cities 3 " + citiesToDelete.size());
            deleteCitiesInDB(citiesToDelete);
        }
        System.out.println("Delete nations " + StringMan.getString(ids));
        deleteNationsInDB(ids);
    }

    private synchronized void deleteNationsInDB(Set<Integer> ids) {
        if (ids.size() == 1) {
            int id = ids.iterator().next();
            executeStmt("DELETE FROM NATIONS2 WHERE nation_id = " + id);
        } else {
            try (PreparedStatement stmt2 = getConnection().prepareStatement("DELETE FROM NATIONS2 WHERE `nation_id` in " + StringMan.getString(ids))) {
                stmt2.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private synchronized void deleteTreatiesInDB(Set<Integer> ids) {
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

    public int getDirtyNations() {
        return dirtyNations.size();
    }

    public Set<Integer> getDirtyCities() {
        return dirtyCities;
    }
}
