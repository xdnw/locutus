package link.locutus.discord.db;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.politicsandwar.graphql.model.*;
import com.ptsmods.mysqlw.query.builder.SelectBuilder;
import com.ptsmods.mysqlw.table.ColumnType;
import com.ptsmods.mysqlw.table.TablePreset;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.ints.*;
import it.unimi.dsi.fastutil.longs.*;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.Logg;
import link.locutus.discord.apiv1.domains.subdomains.SAllianceContainer;
import link.locutus.discord.apiv1.domains.subdomains.SNationContainer;
import link.locutus.discord.apiv1.enums.city.building.MilitaryBuilding;
import link.locutus.discord.apiv3.csv.DataDumpParser;
import link.locutus.discord.apiv3.csv.file.CitiesFile;
import link.locutus.discord.apiv3.csv.file.NationsFile;
import link.locutus.discord.apiv3.subscription.PnwPusherShardManager;
import link.locutus.discord.commands.manager.v2.builder.SummedMapRankBuilder;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.DBTreasure;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.apiv3.enums.AlliancePermission;
import link.locutus.discord.apiv3.enums.NationLootType;
import link.locutus.discord.db.entities.DBCity;
import link.locutus.discord.apiv3.PoliticsAndWarV3;
import link.locutus.discord.db.entities.*;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.Treaty;
import link.locutus.discord.db.entities.city.SimpleDBCity;
import link.locutus.discord.db.entities.metric.AllianceMetric;
import link.locutus.discord.db.entities.metric.OrbisMetric;
import link.locutus.discord.db.entities.nation.DBNationData;
import link.locutus.discord.db.entities.nation.SimpleDBNation;
import link.locutus.discord.db.handlers.SyncableDatabase;
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
import link.locutus.discord.util.io.PagePriority;
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
import link.locutus.discord.util.scheduler.ThrowingTriConsumer;
import org.apache.commons.lang3.tuple.Triple;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.text.ParseException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;

public class NationDB extends DBMainV2 implements SyncableDatabase, INationSnapshot {
    private final Map<Integer, DBNation> nationsById = new Int2ObjectOpenHashMap<>();
    private final Map<Integer, Map<Integer, DBNation>> nationsByAlliance = new Int2ObjectOpenHashMap<>();
    private final Map<Integer, DBAlliance> alliancesById = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectOpenHashMap<Object> citiesByNation = new Int2ObjectOpenHashMap<>();
    private final Map<Integer, DBAlliancePosition> positionsById = new Int2ObjectOpenHashMap<>();
    private final Map<Integer, Map<Integer, DBAlliancePosition>> positionsByAllianceId = new Int2ObjectOpenHashMap<>();
    private final Map<Integer, Map<Integer, Treaty>> treatiesByAlliance = new Int2ObjectOpenHashMap<>();
    private final Set<Integer> dirtyCities = Collections.synchronizedSet(new LinkedHashSet<>());
    private final Set<Integer> dirtyCityNations = Collections.synchronizedSet(new LinkedHashSet<>());
    private final Set<Integer> dirtyNations = Collections.synchronizedSet(new LinkedHashSet<>());
    private final Map<Integer, Set<DBTreasure>> treasuresByNation = new Int2ObjectOpenHashMap<>();
    private final Map<String, DBTreasure> treasuresByName = new ConcurrentHashMap<>();
    private ReportManager reportManager;
    private LoanManager loanManager;

    public NationDB() throws SQLException, ClassNotFoundException {
        super("nations");
    }

    @Override
    public Set<DBNation> getAllNations() {
        synchronized (nationsById) {
            return new ObjectOpenHashSet<>(nationsById.values());
        }
    }

    public ReportManager getReportManager() {
        return reportManager;
    }

    public LoanManager getLoanManager() {
        return loanManager;
    }

    private void condenseCities() {
        synchronized (citiesByNation) {
            ObjectOpenHashSet<ByteArrayList> cityBytes = new ObjectOpenHashSet<>();
            for (Map.Entry<Integer, Object> entry : citiesByNation.entrySet()) {
                ArrayUtil.iterateElements(SimpleDBCity.class, entry.getValue(), dbCity -> {
                    ByteArrayList currBytes = new ByteArrayList(dbCity.getBuildings3());
                    ByteArrayList existing = cityBytes.get(currBytes);
                    if (existing != null) {
                        dbCity.setBuildings3(existing.elements());
                    } else {
                        cityBytes.add(currBytes);
                        dbCity.setBuildings3(currBytes.elements());
                    }
                });
            }
        }
    }

    public NationDB load() throws SQLException {
        { // Legacy
            if (tableExists("NATIONS")) {
                Logg.text("Updating legacy nations");
                updateLegacyNations();
            }
            if (tableExists("TREATIES")) getDb().drop("TREATIES");
            if (tableExists("CITY_INFRA_LAND")) getDb().drop("CITY_INFRA_LAND");
        }

        loadPositions();
        Logg.text("Loaded " + positionsById.size() + " positions");
        loadAlliances();
        Logg.text("Loaded " + alliancesById.size() + " alliances");
        loadNations();
        Logg.text("Loaded " + nationsById.size() + " nations");
        int citiesLoaded = loadCities();
        Logg.text("Loaded " + citiesLoaded + " cities");
        int treatiesLoaded = loadTreaties();
        Logg.text("Loaded " + treatiesLoaded + " treaties");

        try {
            if (tableExists("KICKS")) {
                importKicks();
            } else {
//                executeStmt("DELETE FROM KICKS2 where from_aa = to_aa AND from_rank = to_rank");
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
//        importLegacyNationLoot(true);

        loadAndPurgeMeta();
        loadTreasures();

        // Load missing data
        if (Settings.INSTANCE.ENABLED_COMPONENTS.REPEATING_TASKS) {
            if (nationsById.isEmpty() && (Settings.INSTANCE.TASKS.ACTIVE_NATION_SECONDS > 0 || Settings.INSTANCE.TASKS.COLORED_NATIONS_SECONDS > 0 || Settings.INSTANCE.TASKS.ALL_NATIONS_SECONDS > 0)) {
                Logg.text("No nations loaded, fetching all");
                updateAllNations(null, true);
                Logg.text("Done fetching all nations");
            }
            if (alliancesById.isEmpty() && (Settings.INSTANCE.TASKS.ACTIVE_NATION_SECONDS > 0 || Settings.INSTANCE.TASKS.COLORED_NATIONS_SECONDS > 0 || Settings.INSTANCE.TASKS.ALL_NATIONS_SECONDS > 0)) {
                Logg.text("No alliances loaded, fetching all");
                updateAlliances(null, null);
                Logg.text("Done fetching all alliances");
            }
            if (citiesByNation.isEmpty() && (Settings.INSTANCE.TASKS.OUTDATED_CITIES_SECONDS > 0 || Settings.INSTANCE.TASKS.ALL_CITIES_SECONDS > 0)) {
                Logg.text("No cities loaded, fetching all");
                updateAllCities(null);
                Logg.text("Done fetching all cities");
            }
            if (treatiesByAlliance.isEmpty() && (Settings.INSTANCE.TASKS.TREATY_UPDATE_SECONDS > 0)) {
                Logg.text("No treaties loaded, fetching all");
                updateTreaties(null);
                Logg.text("Done fetching all treaties");
            }
            if (treasuresByNation.isEmpty() && (Settings.INSTANCE.TASKS.TREASURE_UPDATE_SECONDS > 0)) {
                Logg.text("No treasures loaded, fetching all");
                updateTreasures(null);
                Logg.text("Done fetching all treasures");
            }
        }
        return this;
    }

    public void loadNukeDatesIfEmpty() {
        query("SELECT COUNT(*) FROM CITY_BUILDS WHERE nuke_date > 0", f -> {
        }, (ThrowingConsumer<ResultSet>) rs -> {
            if (!rs.next() || rs.getInt(1) == 0) {
                Locutus.imp().getWarDb().loadNukeDates();
            }
        });
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

    public SimpleDBCity getDBCity(int nationId, int cityId) {
        synchronized (citiesByNation) {
            Object nationCities = citiesByNation.get(nationId);
            if (nationCities != null) {
                return ArrayUtil.getElement(SimpleDBCity.class, nationCities, cityId);
            }
        }
        return null;
    }

    public boolean updateNationUnits(int nationId, long timestamp, Map<MilitaryUnit, Integer> losses, Consumer<Event> eventConsumer) {
        DBNation nation = getNationById(nationId);
        if (nation != null) {
            long lastUpdatedMs = nation.getLastFetchedUnitsMs();
            if (lastUpdatedMs < timestamp) {
                for (Map.Entry<MilitaryUnit, Integer> entry : losses.entrySet()) {
                    int amt = Math.max(0, nation.getUnits(entry.getKey()) - entry.getValue());
                    DBNation copyOriginal = eventConsumer == null ? null : nation.copy();
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
        DBNation nation = getNationById(nationId);
        if (nation != null) {
            if (nation.lastActiveMs() < active) {
                DBNation previous = eventConsumer == null ? null : nation.copy();
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
        long turnstamp = TimeUtil.getTurn(timestamp);

        DBCity city = getDBCity(nationId, cityId);
        if (city != null && city.getNuke_turn() < turnstamp) {

            DBCity copyOriginal = eventConsumer == null ? null : new SimpleDBCity(city);
            city.setNuke_turn((int) turnstamp);
            if (copyOriginal != null) eventConsumer.accept(new CityNukeEvent(nationId, copyOriginal, city));

            saveCities(List.of(city));
            return true;
        }
        return false;
    }

    public boolean setCityInfraFromAttack(int nationId, int cityId, double infra, long timestamp, Consumer<Event> eventConsumer) {
        DBCity city = getDBCity(nationId, cityId);
        if (city != null && city.getFetched() < timestamp && Math.round(infra * 100) != Math.round(city.getInfra() * 100)) {
            DBCity previous = new SimpleDBCity(city);
            city.setInfra(infra);
            if (eventConsumer != null) {
                if (Math.round(infra * 100) != Math.round(previous.getInfra() * 100)) {
                    eventConsumer.accept(new CityInfraDamageEvent(nationId, previous, city));
                }
            }
            saveCities(List.of(city));
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
            if (city != null && city.getFetched() > timestamp) return;
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
        List<Alliance> alliances;
        PoliticsAndWarV3 api = Locutus.imp().getV3();
        if (filter != null) {
            alliances = api.fetchAlliances(false, filter, true, true);
        } else {
            alliances = api.readSnapshot(PagePriority.API_ALLIANCES_AUTO, Alliance.class);
            Map<Integer, Alliance> byId = new Int2ObjectOpenHashMap<>();
            for (Alliance alliance : alliances) {
                byId.put(alliance.getId(), alliance);
            }
            List<AlliancePosition> positions = api.readSnapshot(PagePriority.API_ALLIANCES_AUTO, AlliancePosition.class);
            for (AlliancePosition position : positions) {
                Alliance alliance = byId.get(position.getAlliance_id());
                if (alliance != null) {
                    List<AlliancePosition> list = alliance.getAlliance_positions();
                    if (list == null) {
                        list = new ObjectArrayList<>();
                        alliance.setAlliance_positions(list);
                    }
                    list.add(position);
                }
            }
        }
        if (alliances.isEmpty()) return;
        if (!toDelete.isEmpty()) {
            for (Alliance alliance : alliances) {
                toDelete.remove(alliance.getId());
            }
        }
        Set<Integer> updated = processUpdatedAlliances(alliances, eventConsumer);
        if (!toDelete.isEmpty()) {
            updateAlliancesById(new ArrayList<>(toDelete), eventConsumer);
//            deleteAlliances(toDelete, eventConsumer);
        }
    }

    public Set<Integer> updateAlliancesById(List<Integer> ids, Consumer<Event> eventConsumer) {
        PoliticsAndWarV3 v3 = Locutus.imp().getV3();

        Set<Integer> fetched = new LinkedHashSet<>();
        if (ids.isEmpty()) return fetched;
        ids = new ArrayList<>(ids);
        Collections.sort(ids);

        Set<Integer> toDelete = new HashSet<>();
        for (int i = 0; i < ids.size(); i += 500) {
            int end = Math.min(i + 500, ids.size());
            List<Integer> toFetch = ids.subList(i, end);
            toDelete.addAll(toFetch);
            List<Alliance> alliances = v3.fetchAlliances(false, req -> req.setId(toFetch), true, true);
            processUpdatedAlliances(alliances, eventConsumer);
            for (Alliance alliance : alliances) {
                fetched.add(alliance.getId());
                toDelete.remove(alliance.getId());
            }
        }

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
                            DBNation copy = eventConsumer == null ? null : nation.copy();

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
            synchronized (positionsByAllianceId) {
                Map<Integer, DBAlliancePosition> positions = positionsByAllianceId.remove(id);
                if (positions != null) {
                    positionsToDelete.addAll(positions.keySet());
                    for (int posId : positions.keySet()) {
                        synchronized (this.positionsById) {
                            positionsById.remove(posId);
                        }
                    }
                }
            }
        }

        if (!positionsToDelete.isEmpty()) deletePositions(positionsToDelete, null);
        if (!treatiesToDelete.isEmpty()) deleteTreaties(treatiesToDelete, eventConsumer);
        if (!dirtyNations.isEmpty()) saveNations(dirtyNations);

        deleteAlliancesInDB(ids);
    }

    public void deleteTreaties(Set<Treaty> treaties, Consumer<Event> eventConsumer) {
        long turn = TimeUtil.getTurn(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(2));
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
                        allianceByNameCache.put(alliance.getName().toLowerCase(Locale.ROOT), alliance.getId());
                    }
                    createdAlliances.add(existing);
                    dirtyAlliances.add(existing);
                } else {
                    String originalName = existing.getName();
                    if (existing.set(alliance, eventConsumer)) {
                        dirtyAlliances.add(existing);
                        if (!originalName.equals(existing.getName())) {
                            allianceByNameCache.put(alliance.getName().toLowerCase(Locale.ROOT), alliance.getId());
                        }
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
                                DBNation copy = eventConsumer == null ? null : nation.copy();
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
//        List<com.politicsandwar.graphql.model.Treaty> treatiesV3 = v3.readSnapshot(PagePriority.API_TREATIES, com.politicsandwar.graphql.model.Treaty.class);
//        if (treatiesV3.isEmpty()) throw new IllegalStateException("No treaties returned from API! (updateTreaties())");
//        treatiesV3 = new ObjectArrayList<>(treatiesV3);
//        treatiesV3.removeIf(f -> f.getTurns_left() <= 0);

        // The snapshots seem to return incorrect results, missing existing treaties and including ones that are gone?
        List<com.politicsandwar.graphql.model.Treaty> treatiesV3 = v3.fetchTreaties(r -> {});

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
        Map<Integer, Long> lastActive = new Int2LongOpenHashMap();
        for (DBNation nation : allNations) {
            lastActive.put(nation.getId(), nation.lastActiveMs());
        }
        allNations.sort((o1, o2) -> Long.compare(lastActive.get(o2.getId()), lastActive.get(o1.getId())));
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
        }, eventConsumer, eventConsumer != null);
    }

    public List<Nation> getActive(boolean priority, boolean runEvents) throws IOException {
        String url = "https://politicsandwar.com/index.php?id=15&keyword=&cat=everything&ob=lastactive&od=DESC&maximum=50&minimum=0&search=Go&vmode=false";
        String html = FileUtil.readStringFromURL(priority ? PagePriority.ACTIVE_PAGE : PagePriority.API_NATIONS_AUTO, url);
        List<Integer> nationIds = PW.getNationsFromTable(html, 0);
        Map<Integer, Integer> nationIdIndex = new HashMap<>();
        for (int i = 0; i < nationIds.size(); i++) {
            nationIdIndex.put(nationIds.get(i), i);
        }
        List<Nation> nationActiveData = new ArrayList<>(Locutus.imp().getV3().fetchNationActive(priority, nationIds));
        nationActiveData.sort(Comparator.comparingInt(o -> nationIdIndex.get(o.getId())));

        if (runEvents) {
            Locutus.imp().runEventsAsync(events -> {
                List<Nation> filtered = nationActiveData.stream().filter(f -> getNationById(f.getId()) != null).toList();
                Locutus.imp().getNationDB().updateNations(filtered, events, -1);
            });
        }

        return nationActiveData;
    }

    public List<Integer> getMostActiveNationIds(int amt) {
        List<Map.Entry<Integer, Long>> nationIdActive = new ArrayList<>();

        long turn = TimeUtil.getTurn();
        Set<Integer> visited = new HashSet<>();
        while (!dirtyNations.isEmpty() && nationIdActive.size() < amt) {
            try {
                synchronized (dirtyNations) {
                    Iterator<Integer> iter = dirtyNations.iterator();
                    int nationId = iter.next();
                    iter.remove();
                    if (visited.add(nationId)) {
                        nationIdActive.add(Map.entry(nationId, Long.MAX_VALUE)); // Always update dirty nations
                    }
                }
            } catch (NoSuchElementException ignore) {
                ignore.printStackTrace();
            }

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
            fetched = updateNations(r -> r.setId(idsFinal), eventConsumer, eventConsumer != null);
        }
        if (ids.size() >= 500 && fetched.isEmpty()) {
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
                    long diff = nation.lastActiveMs() - city.getFetched();
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
                        long diff = nation.lastActiveMs() - city.getFetched();
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
        dirtyCities.clear();
        dirtyCityNations.clear();
        Map<Integer, Integer> citiesToDeleteToNationId = new HashMap<>();
        synchronized (citiesByNation) {
            for (Map.Entry<Integer, Object> natEntry : citiesByNation.entrySet()) {
                int natId = natEntry.getKey();
                DBNation nation = getNationById(natId);
                if (nation != null && nation.getVm_turns() > 0) continue;
                Object cities = natEntry.getValue();
                synchronized (cities) {
                    ArrayUtil.iterateElements(SimpleDBCity.class, natEntry.getValue(), city -> citiesToDeleteToNationId.put(city.getId(), natId));
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

        DBCity buffer = new SimpleDBCity(0);

        int originalDirtySize = dirtyCities.size();

        for (SCityContainer city : cities) {
            int nationId = Integer.parseInt(city.getNationId());
            int cityId = Integer.parseInt(city.getCityId());

            // City was fetched, do not delete it
            citiesToDeleteToNationId.remove(cityId);

            SimpleDBCity existing = getDBCity(nationId, cityId);
            if (existing == null) {
                existing = new SimpleDBCity(nationId);
                existing.setId(cityId);
                existing.set(city);
                if (eventConsumer != null) eventConsumer.accept(new CityCreateEvent(nationId, existing));
                dirtyCities.add(cityId);
                synchronized (citiesByNation) {
                    ArrayUtil.addElement(SimpleDBCity.class, citiesByNation, nationId, existing);
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
            System.out.println("Marking cities as outdated " + newDirty);
        }

        if (!citiesToDeleteToNationId.isEmpty()) {
            for (Map.Entry<Integer, Integer> entry : citiesToDeleteToNationId.entrySet()) {
                int cityId = entry.getKey();
                int nationId = entry.getValue();
                synchronized (citiesByNation) {
                    ArrayUtil.removeElement(SimpleDBCity.class, citiesByNation, nationId, cityId);
                }
            }
            deleteCitiesInDB(citiesToDeleteToNationId.keySet());
        }
    }

    public void updateAllCities(Consumer<Event> eventConsumer) {
        PoliticsAndWarV3 v3 = Locutus.imp().getV3();
        dirtyCities.clear();
        dirtyCityNations.clear();
        List<City> cities = v3.readSnapshot(PagePriority.API_CITIES_AUTO_ALL, City.class);
        Map<Integer, Map<Integer, City>> nationIdCityIdCityMap = new Int2ObjectOpenHashMap<>();
        for (City city : cities) {
            nationIdCityIdCityMap.computeIfAbsent(city.getNation_id(), f -> new Int2ObjectOpenHashMap<>())
                    .put(city.getId(), city);
        }
        if (!nationIdCityIdCityMap.isEmpty()) {
            updateCities(nationIdCityIdCityMap, eventConsumer);
            long cutoff = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(1);
            Set<Integer> deletedNations = getNationsMatching(f -> f.getVm_turns() == 0 && !nationIdCityIdCityMap.containsKey(f.getId()) && f.getDate() > cutoff).stream().map(DBNation::getNation_id).collect(Collectors.toSet());
            deleteNations(deletedNations, eventConsumer);
        }
    }

    private int estimateCities(Set<Integer> nationIds) {
        int estimatedCitiesToFetch = 0;
        for (int nationId : nationIds) {
            DBNation nation = getNationById(nationId);
            if (nation == null) estimatedCitiesToFetch++;
            else estimatedCitiesToFetch += nation.getCities();
        }
        return estimatedCitiesToFetch;
    }

    public void updateCitiesOfNations(Set<Integer> nationIds, boolean priority, boolean bulk, Consumer<Event> eventConsumer) {
        PoliticsAndWarV3 v3 = Locutus.imp().getV3();
        List<Integer> idList = new ArrayList<>(nationIds);
        if (bulk) markDirtyIncorrectCities(true, true);
        int estimatedCitiesToFetch = estimateCities(nationIds);
        if (estimatedCitiesToFetch >= PoliticsAndWarV3.CITIES_PER_PAGE * 30) {
            System.out.println("Updating all cities (2)");
            updateAllCities(eventConsumer);
            return;
        }
        if (bulk) {
            if (estimatedCitiesToFetch < 490) { // Slightly below 500 to avoid off by 1 errors with api
                int numToFetch = 490 - idList.size();
                List<Integer> mostActiveNations = getMostActiveNationIdsLimitCities(numToFetch, new HashSet<>(idList));
                idList.addAll(mostActiveNations);
            }
        }

        for (int i = 0; i < 500 && !idList.isEmpty(); i += 500) {
            int end = Math.min(i + 500, idList.size());
            List<Integer> toFetch = idList.subList(i, end);
            toFetch.forEach(dirtyCityNations::remove);
            List<City> cities = v3.fetchCitiesWithInfo(priority, r -> r.setNation_id(toFetch), true);
            Map<Integer, Map<Integer, City>> completeCities = new Int2ObjectOpenHashMap<>();
            for (City city : cities) {
                completeCities.computeIfAbsent(city.getNation_id(), f -> new Int2ObjectOpenHashMap<>())
                        .put(city.getId(), city);
            }
            updateCities(completeCities, eventConsumer);
        }
    }

    public boolean updateDirtyCities(boolean priority, Consumer<Event> eventConsumer) {
        List<Integer> cityIds = new ArrayList<>();
        PoliticsAndWarV3 v3 = Locutus.imp().getV3();
        markDirtyIncorrectNations(true, true);

        int dirtyNationCities = estimateCities(dirtyCityNations);
        if (dirtyCities.size() > 15000 || dirtyNationCities > 15000) {
            System.out.println("Updating all cities (dirty)");
            updateAllCities(eventConsumer);
            return true;
        }

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
            List<City> cities = v3.fetchCitiesWithInfo(priority, r -> r.setId(toFetch), true);
            deletedCities.addAll(toFetch);
            for (City city : cities) deletedCities.remove(city.getId());
            updateCities(cities, eventConsumer);
        }
        if (!deletedCities.isEmpty()) {
            deleteCities(deletedCities, eventConsumer);
        }

        if (!dirtyCityNations.isEmpty()) {
            updateCitiesOfNations(dirtyCityNations, priority, false, eventConsumer);
        }

        return true;
    }

    private int lastNewCityFetched = 0;

    private List<Integer> getNewCityIds(int amt, Set<Integer> ignoreIds) {
        Set<Integer> cityIds = new HashSet<>(ignoreIds);
        int[] maxIds = new int[1];
        synchronized (citiesByNation) {
            for (Object cityMap : citiesByNation.values()) {
                ArrayUtil.iterateElements(SimpleDBCity.class, cityMap, (city) -> {
                    maxIds[0] = Math.max(city.getId(), maxIds[0]);
                    cityIds.add(city.getId());
                });
            }
        }
        if (lastNewCityFetched == 0) {
            lastNewCityFetched = maxIds[0];
        } else {
            lastNewCityFetched = Math.min(maxIds[0], lastNewCityFetched);
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
        List<City> cities = v3.fetchCitiesWithInfo(false, r -> r.setId(newIds), true);
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
        DBCity buffer = new SimpleDBCity(0);
        List<DBCity> dirtyCities = new ArrayList<>(); // List<nation id, db city>
        AtomicBoolean dirtyFlag = new AtomicBoolean();

        Set<Integer> citiesToDelete = new HashSet<>();

        List<Event> events = null;
        for (Map.Entry<Integer, Map<Integer, City>> nationEntry : completeCitiesByNation.entrySet()) {
            int nationId = nationEntry.getKey();
            Map<Integer, City> cities = nationEntry.getValue();
            if (cities.isEmpty()) continue;
            if (!NationDB.this.dirtyCities.isEmpty()) {
                for (City city : cities.values()) {
                    NationDB.this.dirtyCities.remove(city.getId());
                }
            }
            synchronized (citiesByNation) {
                Object map = citiesByNation.get(nationId);
                if (map != null) {
                    int existingCities = ArrayUtil.countElements(SimpleDBCity.class, map);
                    if (existingCities == cities.size()) {
                        dirtyCityNations.remove(nationId);
                    }
                    if (deleteMissing) {
                        IntList toDelete = new IntArrayList();
                        ArrayUtil.iterateElements(SimpleDBCity.class, map, dbCity -> {
                            City city = cities.get(dbCity.getId());
                            if (city == null) {
                                toDelete.add(dbCity.getId());

                            }
                        });
                        for (int cityId : toDelete) {
                            citiesToDelete.add(cityId);
                            DBCity city = ArrayUtil.getElement(SimpleDBCity.class, map, cityId);
                            ArrayUtil.removeElement(SimpleDBCity.class, citiesByNation, nationId, cityId);
                            if (eventConsumer != null) {
                                if (events == null) events = new ArrayList<>();
                                events.add(new CityDeleteEvent(nationId, city));
                            }
                        }
                    }
                }
            }

            for (Map.Entry<Integer, City> cityEntry : cities.entrySet()) {
                City city = cityEntry.getValue();
                dirtyFlag.set(false);
                DBCity dbCity = processCityUpdate(city, buffer, eventConsumer, dirtyFlag);
                if (dirtyFlag.get()) {
                    dirtyCities.add(dbCity);
                }
            }
        }
        if (!citiesToDelete.isEmpty()) {
            deleteCitiesInDB(citiesToDelete);
        }
        if (!dirtyCities.isEmpty()) {
            saveCities(dirtyCities);
        }
        if (events != null) {
            events.forEach(eventConsumer);
        }
    }


    public boolean deleteCities(Set<Integer> cityIds, Consumer<Event> eventConsumer) {
        Map<Integer, Integer> cityIdNationId = new HashMap<>();
        synchronized (citiesByNation) {
            for (Map.Entry<Integer, Object> entry : citiesByNation.entrySet()) {
                ArrayUtil.iterateElements(SimpleDBCity.class, entry.getValue(), dbCity -> {
                    if (cityIds.contains(dbCity.getId())) {
                        cityIdNationId.put(dbCity.getId(), entry.getKey());
                    }
                });
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
                existing =ArrayUtil.removeElement(SimpleDBCity.class, citiesByNation, nationId, cityId);
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
        DBCity buffer = new SimpleDBCity(0);
        List<DBCity> dirtyCities = new ArrayList<>(); // List<nation id, db city>
        AtomicBoolean dirtyFlag = new AtomicBoolean();

        for (City city : cities) {
            if (!NationDB.this.dirtyCities.isEmpty()) {
                NationDB.this.dirtyCities.remove(city.getId());
            }
            dirtyFlag.set(false);
            DBCity dbCity = processCityUpdate(city, buffer, eventConsumer, dirtyFlag);
            if (dirtyFlag.get()) {
                dirtyCities.add(dbCity);
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
        SimpleDBCity existing = getDBCity(city.getNation_id(), city.getId());

        if (existing != null) {
            buffer.set(existing);
            existing.set(city);
            if (existing.runChangeEvents(city.getNation_id(), buffer, eventConsumer)) {
                dirtyFlag.set(true);
            }
        } else {
            existing = new SimpleDBCity(city);
            synchronized (citiesByNation) {
                ArrayUtil.addElement(SimpleDBCity.class, citiesByNation, city.getNation_id(), existing);
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

    private final Map<String, Integer> allianceByNameCache = new ConcurrentHashMap<>();

    public String getAllianceName(int aaId) {
        DBAlliance existing = getAlliance(aaId);
        if (existing != null) return existing.getName();

        return "AA:" + aaId;
    }

    public DBAlliance getAllianceByName(String name) {
        Integer aaId = allianceByNameCache.get(name.toLowerCase(Locale.ROOT));
        if (aaId != null) {
            synchronized (alliancesById) {
                DBAlliance aa = alliancesById.get(aaId);
                if (aa != null) return aa;
            }
        }
        return null;
    }

    private int loadTreaties() throws SQLException {
        int total = 0;
        Set<Integer> treatiesToDelete = new LinkedHashSet<>();
        long currentTurn = TimeUtil.getTurn();
        List<Event> postAsync = null;

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
                        ((postAsync == null) ? postAsync = new ArrayList<>() : postAsync).add(new TreatyExpireEvent(treaty));
                    }
                    treatiesToDelete.add(treaty.getId());
                    continue;
                }

                treatiesByAlliance.computeIfAbsent(treaty.getFromId(), f -> new Int2ObjectOpenHashMap<>()).put(treaty.getToId(), treaty);
                treatiesByAlliance.computeIfAbsent(treaty.getToId(), f -> new Int2ObjectOpenHashMap<>()).put(treaty.getFromId(), treaty);
                total++;
            }
        }
        if (postAsync != null && Locutus.imp() != null) Locutus.imp().runEventsAsync(postAsync);
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
                SimpleDBCity city = createCity(rs);
                int nationId = city.getNationId();
                ArrayUtil.addElement(SimpleDBCity.class, citiesByNation, nationId, city);
                total++;
            }
        }
        condenseCities();
        citiesByNation.trim();
        return total;
    }

    /**
     * @param rs
     * @return (nation id, city)
     * @throws SQLException
     */
    private SimpleDBCity createCity(ResultSet rs) throws SQLException {
        int nationId = rs.getInt("nation");
        return new SimpleDBCity(rs, nationId);
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
                Rank.values[rs.getInt("rank")],
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
        return new SimpleDBNation(new DBNationData(
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
        ));
    }

    public void updateAlliancesV2(Consumer<Event> eventConsumer) {
        try {
            List<SAllianceContainer> alliances = Locutus.imp().getPnwApiV2().getAlliances().getAlliances();
            Set<Integer> expected = new LinkedHashSet<>(alliances.size());
            synchronized (alliancesById) {
                expected.addAll(alliancesById.keySet());
            }

            List<Alliance> adaptedList = new ObjectArrayList<>();
            for (SAllianceContainer alliance : alliances) {
                // public Alliance(Integer id,
                Alliance adapted = new Alliance(Integer.parseInt(alliance.getId()),
                        alliance.getName(),
                        alliance.getAcronym(),
                        alliance.getScore(),
                        alliance.getColor(),
                        TimeUtil.YYYY_MM_DD_HH_MM_SS.parse(alliance.getFounddate()).toInstant(), // TODO parse
                        null,
                        null,
                        null,
                        null,
                        null,
                        alliance.getFlagurl(),
                        alliance.getForumurl(),
                        alliance.getIrcchan(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null);
                adaptedList.add(adapted);
            }

            Set<Integer> updated = processUpdatedAlliances(adaptedList, eventConsumer);
            // TODO handle deletion later

        } catch (IOException | ParseException e) {
            throw new RuntimeException(e);
        }
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
                DBNation existing = getNationById(nation.getNationid());
                if (existing == null) {
                    existing = new SimpleDBNation(new DBNationData());
                    existing.updateNationInfo(nation, null);
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
                if (getNationById(lastNewNationId) != null) continue;
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
        Set<Integer> fetched = updateNations(r -> r.setCreated_after(new Date(minDate)), eventConsumer, eventConsumer != null);
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
        Set<Integer> fetched = updateNations(f -> f.setVmode(false), eventConsumer, eventConsumer != null);
        for (int id : currentNations) {
            if (!fetched.contains(id)) deleted.add(id);
        }
        for (int id : deleted) {
            markNationDirty(id);;
        }
        return fetched;
    }

    public Set<Integer> updateAllNations(Consumer<Event> eventConsumer, boolean updateCitiesAndPositions) {
        Set<Integer> currentNations;
        synchronized (nationsById) {
            currentNations = new HashSet<>(nationsById.keySet());
        }
        Set<Integer> deleted = new HashSet<>();
        Set<Integer> fetched = updateNationsCustom(onEachNation -> {
            PoliticsAndWarV3 v3 = Locutus.imp().getV3();
            List<Nation> nations = v3.readSnapshot(PagePriority.API_NATIONS_AUTO, Nation.class);
            for (Nation nation : nations) {
                onEachNation.test(nation);
            }
        }, eventConsumer, updateCitiesAndPositions);
        long cutoff = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(2);
        for (int id : currentNations) {
            if (!fetched.contains(id)) {
                DBNation nation = getNationById(id);
                if (nation != null && nation.getDate() < cutoff) {
                    deleted.add(id);
                }
            }
        }
//        for (int id : deleted) {
//            markNationDirty(id);
//        }
        deleteNations(deleted, eventConsumer);
        return fetched;
    }

    public void markDirtyIncorrectCities(boolean score, boolean cities) {
        markDirtyIncorrectCities(getNationsMatching(f -> f.getVm_turns() == 0), score, cities);
    }

    public void markDirtyIncorrectCities(Set<DBNation> nations, boolean score, boolean cities) {
        for (DBNation nation : nations) {
            Map<Integer, DBCity> cityMap = getCitiesV3(nation.getNation_id());
            if (cities && nation.getCities() != cityMap.size()) {
                dirtyCityNations.add(nation.getNation_id());
            } else if (score && Math.round(100 * (PW.estimateScore(this, nation) - nation.getScore())) != 0) {
                cityMap.forEach((key, value) -> dirtyCities.add(key));
            }
        }
    }

    public void markDirtyIncorrectNations(boolean score, boolean cities) {
        int originalSize = dirtyNations.size();
        for (DBNation nation : getNationsMatching(f -> f.getVm_turns() == 0)) {
            if (score && Math.round(100 * (PW.estimateScore(this, nation) - nation.getScore())) != 0) {
                dirtyNations.add(nation.getNation_id());
            } else {
                Map<Integer, DBCity> cityMap = getCitiesV3(nation.getNation_id());
                if (cities && nation.getCities() != cityMap.size()) {
                    dirtyNations.add(nation.getNation_id());
                } else {
                    for (MilitaryUnit unit : new MilitaryUnit[]{MilitaryUnit.SOLDIER, MilitaryUnit.TANK, MilitaryUnit.AIRCRAFT, MilitaryUnit.SHIP}) {
                        MilitaryBuilding building = unit.getBuilding();
                        int unitCap = 0;
                        for (DBCity city : cityMap.values()) {
                            unitCap += city.getBuilding(building) * building.getUnitCap() ;
                        }
                        if (nation.getUnits(unit) > unitCap) {
                            dirtyNations.add(nation.getNation_id());
                            break;
                        }
                    }
                }
            }
        }
        int added = dirtyNations.size() - originalSize;
        if (added > 1000) {
            System.out.println("Added " + added + " nations to outdated list");
        }
    }

    public Set<Integer> updateNations(Collection<Nation> nations, Consumer<Event> eventConsumer, long timestamp) {
        Map<DBNation, DBNation> nationChanges = new LinkedHashMap<>();
        Set<Integer> nationsIdsFetched = new HashSet<>();
        Set<DBNation> nationsFetched = new HashSet<>();
        for (Nation nation : nations) {
            if (nation.getId() != null) {
                nationsIdsFetched.add(nation.getId());
                if (!NationDB.this.dirtyNations.isEmpty()) {
                    NationDB.this.dirtyNations.remove(nation.getId());
                }
            }
            updateNation(nation, eventConsumer, nationChanges::put, timestamp);
            DBNation dbNat = DBNation.getById(nation.getId());
            if (dbNat != null) {
                nationsFetched.add(dbNat);
            }
        }
        updateNationCitiesAndPositions(nationsFetched, nationChanges, eventConsumer);
        return nationsIdsFetched;

    }

    public Set<Integer> updateNations(Consumer<NationsQueryRequest> filter, Consumer<Event> eventConsumer, boolean updateCitiesAndPositions) {
        return updateNationsCustom(onEachNation -> {
            PoliticsAndWarV3 v3 = Locutus.imp().getV3();
            v3.fetchNationsWithInfo(false, filter, onEachNation);
        }, eventConsumer, updateCitiesAndPositions);
    }

    public Set<Integer> updateNationsCustom(Consumer<Predicate<Nation>> fetchNationTask, Consumer<Event> eventConsumer, boolean updateCitiesAndPositions) {
        Map<DBNation, DBNation> nationChanges = new LinkedHashMap<>();
        Set<Integer> nationsFetched = new HashSet<>();
        long timestamp = System.currentTimeMillis();
        Set<DBNation> nations = new HashSet<>();
        Predicate<Nation> onEachNation = nation -> {
            if (nation.getId() != null) {
                nationsFetched.add(nation.getId());
                if (!NationDB.this.dirtyNations.isEmpty()) {
                    NationDB.this.dirtyNations.remove(nation.getId());
                }
            }
            updateNation(nation, eventConsumer, (prev, curr) -> nationChanges.put(curr, prev), timestamp);
            DBNation dbNat = getNationById(nation.getId());
            if (dbNat != null) nations.add(dbNat);
            return false;
        };
        fetchNationTask.accept(onEachNation);
        if (updateCitiesAndPositions) {
            updateNationCitiesAndPositions(nations, nationChanges, eventConsumer);
        }
        return nationsFetched;
    }

    public void updateNationCitiesAndPositions(Collection<DBNation> allNations, Map<DBNation, DBNation> nationChanges,
                                      Consumer<Event> eventConsumer) {
        boolean fetchCitiesIfNew = true;

        if (!nationChanges.isEmpty()) {
            Set<DBNation> nationsToSave = new LinkedHashSet<>();
            for (Map.Entry<DBNation, DBNation> entry : nationChanges.entrySet()) {
                DBNation nation = entry.getKey();
                if (nation == null) nation = entry.getValue();
                nationsToSave.add(nation);
            }
            saveNations(nationsToSave);

            if (fetchCitiesIfNew) {
                for (Map.Entry<DBNation, DBNation> entry : nationChanges.entrySet()) {
                    DBNation prev = entry.getValue();
                    DBNation curr = entry.getKey();
                    if (curr == null) continue;
                    if (prev == null || curr.getCities() != prev.getCities()) {
                        dirtyCityNations.add(curr.getNation_id());
                    }
                }
            }
        }
    }

    /**
     *
     * @param nation the nation
     * @param eventConsumer any nation events to call
     * @param nationsToSave (previous, current)
     */
    private void updateNation(Nation nation, Consumer<Event> eventConsumer, BiConsumer<DBNation, DBNation> nationsToSave, long timestamp) {
        dirtyNations.remove(nation.getId());

        DBNation existing = getNationById(nation.getId());
        Consumer<Event> eventHandler;
        if (existing == null) {
            eventHandler = null;
        } else {
            eventHandler = eventConsumer;
        }
        AtomicBoolean isDirty = new AtomicBoolean();
        if (timestamp > 0) {
            PnwPusherShardManager pusher = Locutus.imp().getPusher();
            if (pusher != null) {
                SpyTracker spyTracker = pusher.getSpyTracker();
                if (spyTracker != null) {
                    spyTracker.updateCasualties(nation, timestamp);
                }
            }
        }
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

    @Override
    public DBNation getNationById(int id) {
        synchronized (nationsById) {
            return nationsById.get(id);
        }
    }

    private DBNation updateNationInfo(DBNation base, Nation nation, Consumer<Event> eventConsumer, AtomicBoolean markDirty) {
        DBNation copyOriginal = base == null ? null : base.copy();
        if (base == null) {
            markDirty.set(true);
            base = new SimpleDBNation(new DBNationData());
        }
        if (base.updateNationInfo(copyOriginal, nation, eventConsumer)) {
            markDirty.set(true);
        }
        processNationAllianceChange(copyOriginal, base);
        return base;
    }

    public void loadAlliances() throws SQLException {
        SelectBuilder builder = getDb().selectBuilder("ALLIANCES").select("*");
        try (ResultSet rs = builder.executeRaw()) {
            while (rs.next()) {
                DBAlliance alliance = createAlliance(rs);
                alliancesById.put(alliance.getAlliance_id(), alliance);
                allianceByNameCache.put(alliance.getName().toLowerCase(Locale.ROOT), alliance.getId());
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

    @Override
    public Set<String> getTablesAllowingDeletion() {
        return Set.of("NATION_META");
    }

    @Override
    public Map<String, String> getTablesToSync() {
        return Map.of("NATION_META", "date_updated");
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
            executeStmt("ALTER TABLE NATIONS2 ADD COLUMN `gdp` BIGINT NOT NULL DEFAULT 0", true);
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
            String nations = "CREATE TABLE IF NOT EXISTS `NATION_META` (`id` BIGINT NOT NULL, `key` BIGINT NOT NULL, `meta` BLOB NOT NULL, `date_updated` BIGINT NOT NULL, PRIMARY KEY(id, key))";
            try (Statement stmt = getConnection().createStatement()) {
                stmt.addBatch(nations);
                stmt.executeBatch();
                stmt.clearBatch();
            } catch (SQLException e) {
                e.printStackTrace();
            }
            if (getTableColumns("NATION_META").stream().noneMatch(c -> c.equalsIgnoreCase("date_updated"))) {
                executeStmt("ALTER TABLE NATION_META ADD COLUMN date_updated BIGINT NOT NULL DEFAULT " + System.currentTimeMillis(), true);
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
        executeStmt("CREATE INDEX IF NOT EXISTS idx_nation_mil_history_combined ON NATION_MIL_HISTORY (id, unit, date, amount);");
        {
            executeStmt("CREATE TABLE IF NOT EXISTS `CITY_BUILDS` (`id` INT NOT NULL PRIMARY KEY, `nation` INT NOT NULL, `created` BIGINT NOT NULL, `infra` INT NOT NULL, `land` INT NOT NULL, `powered` BOOLEAN NOT NULL, `improvements` BLOB NOT NULL, `update_flag` BIGINT NOT NULL, nuke_date BIGINT NOT NULL)");
            executeStmt("ALTER TABLE CITY_BUILDS ADD COLUMN nuke_date BIGINT NOT NULL DEFAULT 0", true);
        }


        createKicksTable();


        String spies = "CREATE TABLE IF NOT EXISTS `SPIES_BUILDUP` (`nation` INT NOT NULL, `spies` INT NOT NULL, `day` BIGINT NOT NULL, PRIMARY KEY(nation, day))";
        executeStmt(spies);

        String activity = "CREATE TABLE IF NOT EXISTS `activity` (`nation` INT NOT NULL, `turn` BIGINT NOT NULL, PRIMARY KEY(nation, turn))";
        executeStmt(activity);

        String activity_m = "CREATE TABLE IF NOT EXISTS `spy_activity` (`nation` INT NOT NULL, `timestamp` BIGINT NOT NULL, `projects` BIGINT NOT NULL, `change` BIGINT NOT NULL, `spies` INT NOT NULL, PRIMARY KEY(nation, timestamp))";
        executeStmt(activity_m);

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
        executeStmt("CREATE TABLE IF NOT EXISTS ORBIS_METRICS_DAY (metric INT NOT NULL, day BIGINT NOT NULL, value DOUBLE NOT NULL, PRIMARY KEY(metric, day))");
        executeStmt("CREATE TABLE IF NOT EXISTS ORBIS_METRICS_TURN (metric INT NOT NULL, turn BIGINT NOT NULL, value DOUBLE NOT NULL, PRIMARY KEY(metric, turn))");
        executeStmt("CREATE TABLE IF NOT EXISTS RADIATION_BY_TURN (continent INT NOT NULL, radiation INT NOT NULL, turn BIGINT NOT NULL, PRIMARY KEY(continent, turn))");
        executeStmt("CREATE TABLE IF NOT EXISTS NATION_DESCRIPTIONS (id INT NOT NULL PRIMARY KEY, description TEXT NOT NULL)");

//        executeStmt("DROP TABLE IF EXISTS TREASURES4"); // Remove after restart
        executeStmt("CREATE TABLE IF NOT EXISTS TREASURES4 (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, color INT, continent INT, bonus INT NOT NULL, spawn_date BIGINT NOT NULL, nation_id INT NOT NULL, respawn_alert BIGINT NOT NULL)");

        // banned_nations
        //   nation_id: Int
        //  reason: String
        //  date: DateTimeAuto
        //  days_left: Int
        executeStmt("CREATE TABLE IF NOT EXISTS banned_nations (nation_id INT NOT NULL PRIMARY KEY, discord_id BIGINT NOT NULL, reason TEXT NOT NULL, date BIGINT NOT NULL, days_left INT NOT NULL)");
        executeStmt("CREATE INDEX IF NOT EXISTS index_banned_nations_discord_id ON banned_nations (discord_id);");

        purgeOldBeigeReminders();

//        //Create table IMPORTED_LOANS
//        executeStmt("CREATE TABLE IF NOT EXISTS IMPORTED_LOANS (" +
//                        "allianceOrGuild BIGINT NOT NULL, " +
//                        "nation_id INT NOT NULL, " +
//                        "loan_date BIGINT NOT NULL, " +
//                        "loaner_user BIGINT NOT NULL, " +
//                        "status INT NOT NULL, " +
//                        "principal BLOB NOT NULL, " +
//                        "remaining BLOB NOT NULL, " +
//                        "date_submitted BIGINT NOT NULL, " +
//                        "PRIMARY KEY(allianceOrGuild, nation_id))");
//        //Add index for nation_id
//        executeStmt("CREATE INDEX IF NOT EXISTS index_imported_loans_nation_id ON IMPORTED_LOANS (nation_id);");

        this.reportManager = new ReportManager(this);

        this.loanManager = new LoanManager(this);

        createDeletionsTables();
    }

    private void createKicksTable() {
        String kicks = "CREATE TABLE IF NOT EXISTS `KICKS2` (`nation` INT NOT NULL, `from_aa` INT NOT NULL, `from_rank` INT NOT NULL, `to_aa` INT NOT NULL, `to_rank` INT NOT NULL, `date` BIGINT NOT NULL)";
        executeStmt(kicks);
        executeStmt("CREATE INDEX IF NOT EXISTS index_kicks2_nation ON KICKS2 (nation);");
        executeStmt("CREATE INDEX IF NOT EXISTS index_kicks2_from_aa ON KICKS2 (from_aa,to_aa,date);");
    }

    public void importKicks() throws IOException, ParseException {
        executeStmt("DROP TABLE IF EXISTS KICKS2");
        createKicksTable();
        // check if kicks table exists
        // get data from datacsv
        Map<Long, Map<Integer, Integer>> allianceByDay = new Long2ObjectOpenHashMap<>();
        Map<Long, Map<Integer, Rank>> rankByDay = new Long2ObjectOpenHashMap<>();

        if (Settings.INSTANCE.DATABASE.DATA_DUMP.ENABLED) {
            DataDumpParser data = Locutus.imp().getDataDumper(true).load();
            data.iterateFiles((ThrowingTriConsumer<Long, NationsFile, CitiesFile>) (day, nationsFile, citiesFile) ->
            nationsFile.reader().required(f -> List.of(f.nation_id, f.alliance_id, f.alliance_position)).read(r -> {
                int nationId = r.header.nation_id.get();
                int allianceId = r.header.alliance_id.get();
                Rank alliancePosition = r.header.alliance_position.get();
                allianceByDay.computeIfAbsent(day, f -> new Int2IntOpenHashMap()).put(nationId, allianceId);
                rankByDay.computeIfAbsent(day, f -> new Int2ObjectOpenHashMap<>()).put(nationId, alliancePosition);
            }));
        }

        List<AllianceChange> changes = new ObjectArrayList<>();

        Map<Integer, Triple<Integer, Rank, Long>> futurePosition = new Int2ObjectOpenHashMap<>();
        // get data from kicks
        String select = "SELECT * FROM KICKS ORDER BY date DESC";
        try (PreparedStatement stmt = getConnection().prepareStatement(select)) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                int nationId = rs.getInt("nation");
                int allianceId = rs.getInt("alliance");
                long date = rs.getLong("date");
                Rank rank = Rank.values()[rs.getInt("type")];

                Triple<Integer, Rank, Long> previous = futurePosition.get(nationId);
                DBNation existing = DBNation.getById(nationId);
                if (previous == null) {
                    if (existing != null) {
                        previous = Triple.of(existing.getAlliance_id(), existing.getPositionEnum(), 0L);
                    } else {
                        long day = TimeUtil.getDay(date) + 1;
                        int futureAAId = allianceByDay.getOrDefault(day, Collections.emptyMap()).getOrDefault(nationId, 0);
                        Rank alliancePosition = rankByDay.getOrDefault(day, Collections.emptyMap()).getOrDefault(nationId, Rank.REMOVE);
                        previous = Triple.of(futureAAId, alliancePosition, 0L);
                    }
                }

                if (allianceId != previous.getLeft() || rank != previous.getMiddle()) {
                    changes.add(new AllianceChange(nationId, allianceId, previous.getLeft(), rank, previous.getMiddle(), date));
                }
                futurePosition.put(nationId, Triple.of(allianceId, rank, date));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        String insert = "INSERT INTO KICKS2 (nation, from_aa, from_rank, to_aa, to_rank, date) VALUES (?, ?, ?, ?, ?, ?)";
        executeBatch(changes, insert, (ThrowingBiConsumer<AllianceChange, PreparedStatement>) (change, stmt) -> {
            stmt.setInt(1, change.getNationId());
            stmt.setInt(2, change.getFromId());
            stmt.setInt(3, change.getFromRank().ordinal());
            stmt.setInt(4, change.getToId());
            stmt.setInt(5, change.getToRank().ordinal());
            stmt.setLong(6, change.getDate());
        });
        executeStmt("DROP TABLE KICKS");
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

    public DBBan getBanById(int id) {
        String select = "SELECT * FROM banned_nations WHERE nation_id = ?";
        try (PreparedStatement stmt = getConnection().prepareStatement(select)) {
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return new DBBan(rs);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
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
        Set<DBTreasure> treasuresToUpdate = new HashSet<>();
        Set<DBTreasure> treasuresToCreate = new HashSet<>();

        List<Treasure> newTreasures = v3.fetchTreasures();
        for (Treasure newTreasure : newTreasures) {
            String name = newTreasure.getName();
            DBTreasure existing;
            synchronized (treasuresByName) {
                existing = treasuresByName.get(name.toLowerCase(Locale.ROOT));
            }
            if (existing == null) {
                existing = new DBTreasure().set(newTreasure);
                treasuresToCreate.add(existing);
                synchronized (treasuresByName) {
                    treasuresByName.put(existing.getName().toLowerCase(Locale.ROOT), existing);
                }
                if (existing.getNation_id() > 0) {
                    synchronized (treasuresByNation) {
                        treasuresByNation.computeIfAbsent(existing.getNation_id(), k -> new ObjectOpenHashSet<>()).add(existing);
                    }
                }
            }
            DBTreasure copy = existing.copy();
            existing.set(newTreasure);

            if (copy != null && !copy.equalsExact(existing)) {
                if (copy.getNation_id() != existing.getNation_id()) {
                    if (copy.getNation_id() > 0) {
                        synchronized (treasuresByNation) {
                            Set<DBTreasure> treasures = treasuresByNation.get(copy.getNation_id());
                            if (treasures != null) {
                                treasures.remove(copy);
                                if (treasures.isEmpty()) {
                                    treasuresByNation.remove(copy.getNation_id());
                                }
                            }
                        }
                    }
                    if (existing.getNation_id() > 0) {
                        synchronized (treasuresByNation) {
                            treasuresByNation.computeIfAbsent(existing.getNation_id(), k -> new ObjectOpenHashSet<>()).add(existing);
                        }
                    }
                }

                if (existing.getId() != -1) {
                    treasuresToUpdate.add(existing);
                }

                // new treasure event
                if (eventConsumer != null) eventConsumer.accept(new TreasureUpdateEvent(copy, existing));
            }
        }
        if (!treasuresToUpdate.isEmpty()) {
            updateTreasuresDB(treasuresToUpdate);
        }
        if (!treasuresToCreate.isEmpty()) {
            createTreasuresDB(treasuresToCreate);
        }
    }

    public synchronized void updateTreasuresDB(Collection<DBTreasure> treasures) {
        String update = "UPDATE TREASURES4 SET name = ?, color = ?, continent = ?, bonus = ?, spawn_date = ?, nation_id = ?, respawn_alert = ? WHERE id = ?";
        for (DBTreasure treasure : treasures) {
            try (PreparedStatement stmt = getConnection().prepareStatement(update)) {
                stmt.setString(1, treasure.getName());
                if (treasure.getColor() == null) {
                    stmt.setNull(2, Types.INTEGER);
                } else {
                    stmt.setInt(2, treasure.getColor().ordinal());
                }
                if (treasure.getContinent() == null) {
                    stmt.setNull(3, Types.INTEGER);
                } else {
                    stmt.setInt(3, treasure.getContinent().ordinal());
                }
                stmt.setInt(4, treasure.getBonus());
                stmt.setLong(5, treasure.getSpawnDate());
                stmt.setInt(6, treasure.getNation_id());
                stmt.setLong(7, treasure.getRespawnAlertDate());
                stmt.setInt(8, treasure.getId());
                stmt.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
    }

    public synchronized void createTreasuresDB(Collection<DBTreasure> treasures) {
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

    public int countTreasures(int allianceId) {
        synchronized (nationsByAlliance) {
            Map<Integer, DBNation> nations = nationsByAlliance.get(allianceId);
            if (nations == null || nations.isEmpty()) return 0;
            int count = 0;
            for (DBNation nation : nations.values()) {
                if (nation.getPositionEnum().id <= Rank.APPLICANT.id || nation.getVm_turns() > 0) continue;
                synchronized (treasuresByNation) {
                    Set<DBTreasure> treasures = treasuresByNation.get(nation.getId());
                    if (treasures == null) continue;
                    count += treasures.size();
                }
            }
            return count;
        }
    }

    public Set<DBTreasure> getTreasure(int nationId) {
        synchronized (treasuresByNation) {
            Set<DBTreasure> treasures = treasuresByNation.get(nationId);
            if (treasures == null) return Collections.emptySet();
            return new ObjectOpenHashSet<>(treasures);
        }
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
                treasuresByName.put(treasure.getName().toLowerCase(Locale.ROOT), treasure);
                if (nation_id > 0) {
                    synchronized (treasuresByNation) {
                        treasuresByNation.computeIfAbsent(nation_id, k -> new ObjectOpenHashSet<>()).add(treasure);
                    }
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

    private final Cache<Long, Map<Continent, Double>> radiationCache =
            CacheBuilder.newBuilder()
                    .expireAfterWrite(10, TimeUnit.MINUTES)
                    .build();

    public Map<Continent, Double> getRadiationByTurn(long turn) {
        Map<Continent, Double> cachedResult = radiationCache.getIfPresent(turn);
        if (cachedResult != null) {
            return cachedResult;
        }

        Map<Continent, Double> result = new Object2ObjectOpenHashMap<>();
        try (PreparedStatement stmt = getConnection().prepareStatement("SELECT continent, radiation FROM RADIATION_BY_TURN where turn = ?")) {
            stmt.setLong(1, turn);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Continent continent = Continent.values[(rs.getInt(1))];
                    double radiation = rs.getInt(2) / 100d;
                    result.put(continent, radiation);
                }
            }
            radiationCache.put(turn, result);
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

    @Override
    public DBNation getNationByName(String name) {
        synchronized (nationsById) {
            for (DBNation nation : nationsById.values()) {
                if (nation.getNation().equalsIgnoreCase(name)) {
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

    public DBNation getNationByLeader(String leader) {
        return getFirstNationMatching(f -> f.getLeader().equalsIgnoreCase(leader));
    }

    public Map<Integer, DBNation> getNationsById() {
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

    @Override
    public Set<DBNation> getNationsByAlliance(int id) {
        if (id == 0) {
            return getNationsMatching(f -> f.getAlliance_id() == 0);
        }
        synchronized (nationsByAlliance) {
            Map<Integer, DBNation> nations = nationsByAlliance.get(id);
            if (nations == null) {
                return new LinkedHashSet<>();
            }
            return new ObjectOpenHashSet<>(nations.values());
        }
    }

    @Override
    public Set<DBNation> getNationsByAlliance(Set<Integer> alliances) {
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

        return new SimpleDBNation(new DBNationData(
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
        ));
    }

    public Set<DBAlliance> getAlliances() {
        synchronized (alliancesById) {
            return new ObjectOpenHashSet<>(alliancesById.values());
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
                        DBAlliance alliance;
                        synchronized (alliancesById) {
                            alliance = alliancesById.get(idAbs);
                        }
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
        update("INSERT OR REPLACE INTO `NATION_META`(`id`, `key`, `meta`, `date_updated`) VALUES(?, ?, ?, ?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, nationId);
            stmt.setInt(2, ordinal);
            stmt.setBytes(3, value);
            stmt.setLong(4, System.currentTimeMillis());
        });
    }

    public void deleteMeta(int nationId, NationMeta key) {
        deleteMeta(nationId, key.ordinal());
    }

    public void deleteMeta(int nationId, int keyId) {
        synchronized (this) {
            logDeletion("NATION_META", System.currentTimeMillis(), new String[]{"id", "key"}, nationId, keyId);
            update("DELETE FROM NATION_META where id = ? AND key = ?", new ThrowingConsumer<PreparedStatement>() {
                @Override
                public void acceptThrows(PreparedStatement stmt) throws Exception {
                    stmt.setInt(1, nationId);
                    stmt.setInt(2, keyId);
                }
            });
        }
    }


    public void deleteMeta(AllianceMeta key) {
        String condition = "key = " + key.ordinal() + " AND id < 0";
        deleteMeta(condition);
    }

    public void deleteMeta(NationMeta key) {
        String condition = "key = " + key.ordinal() + " AND id > 0";
        deleteMeta(condition);
    }

    private void deleteMeta(String condition) {
        synchronized (this) {
            logDeletion("NATION_META", System.currentTimeMillis(), condition, "id", "key");
            update("DELETE FROM NATION_META where " + condition);
        }
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

    public void addMetric(OrbisMetric metric, long turnOrDay, double value) {
        if (!Double.isFinite(value)) {
            return;
        }
        boolean isTurn = metric.isTurn();
        String table = isTurn ? "ORBIS_METRICS_TURN" : "ORBIS_METRICS_DAY";
        String turnOrDayCol = isTurn ? "turn" : "day";
        String query = "INSERT OR REPLACE INTO `" + table + "`(`metric`, `" + turnOrDayCol + "`, `value`) VALUES(?, ?, ?)";
        update(query, (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, metric.ordinal());
            stmt.setLong(2, turnOrDay);
            stmt.setDouble(3, value);
        });
    }

    public long getLatestMetricTime(boolean isTurn) {
        String tableName = isTurn ? "ORBIS_METRICS_TURN" : "ORBIS_METRICS_DAY";
        String turnOrDayCol = isTurn ? "turn" : "day";
        String query = "SELECT MAX(" + turnOrDayCol + ") FROM " + tableName;
        try (PreparedStatement stmt = getConnection().prepareStatement(query)) {
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public Map<Long, Double> getMetrics(OrbisMetric metric, long start, long end) {
        boolean isTurn = metric.isTurn();
        String table = isTurn ? "ORBIS_METRICS_TURN" : "ORBIS_METRICS_DAY";
        String turnOrDayCol = isTurn ? "turn" : "day";
        String query = "SELECT * FROM " + table + " WHERE metric = ? AND " + turnOrDayCol + " >= ? AND " + turnOrDayCol + " <= ?";
        Map<Long, Double> result = new Long2DoubleOpenHashMap();
        query(query, (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, metric.ordinal());
            stmt.setLong(2, start);
            stmt.setLong(3, end);
        }, (ThrowingConsumer<ResultSet>) rs -> {
            while (rs.next()) {
                long turnOrDay = rs.getLong(turnOrDayCol);
                double value = rs.getDouble("value");
                result.put(turnOrDay, value);
            }
        });
        return result;
    }

    public Map<OrbisMetric, Map<Long, Double>> getMetrics(Collection<OrbisMetric> metrics, long start, long end) {
        if (metrics.isEmpty()) return new HashMap<>();
        if (metrics.size() == 1) {
            OrbisMetric metric = metrics.iterator().next();
            return Collections.singletonMap(metric, getMetrics(metric, start, end));
        }
        Map<OrbisMetric, Map<Long, Double>> result = new Object2ObjectOpenHashMap<>();
        result.putAll(getMetrics(metrics, true, start, end));
        result.putAll(getMetrics(metrics, false, start, end));
        return result;
    }

    private Map<OrbisMetric, Map<Long, Double>> getMetrics(Collection<OrbisMetric> metrics, boolean isTurn, long start, long end) {
        List<Integer> ids = new ArrayList<>(metrics.stream().filter(f -> f.isTurn() == isTurn).map(Enum::ordinal).toList());
        if (isTurn) {
            start = TimeUtil.getTurn(start);
            end = end == Long.MAX_VALUE ? end : TimeUtil.getTurn(end);
        } else {
            start = TimeUtil.getDay(start);
            end = end == Long.MAX_VALUE ? end : TimeUtil.getDay(end);
        }
        if (ids.isEmpty()) return new HashMap<>();
        if (ids.size() == 1) {
            OrbisMetric metric = metrics.iterator().next();
            return Collections.singletonMap(metric, getMetrics(metric, start, end));
        }
        ids.sort(Comparator.naturalOrder());
        String table = isTurn ? "ORBIS_METRICS_TURN" : "ORBIS_METRICS_DAY";
        String turnOrDayCol = isTurn ? "turn" : "day";
        String query = "SELECT * FROM " + table + " WHERE metric in " + StringMan.getString(ids) + " AND " + turnOrDayCol + " >= ? AND " + turnOrDayCol + " <= ?";
        Map<OrbisMetric, Map<Long, Double>> result = new Object2ObjectOpenHashMap<>();
        long finalStart = start;
        long finalEnd = end;
        query(query, (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setLong(1, finalStart);
            stmt.setLong(2, finalEnd);
        }, (ThrowingConsumer<ResultSet>) rs -> {
            while (rs.next()) {
                int metricId = rs.getInt("metric");
                OrbisMetric metric = OrbisMetric.values[metricId];
                long turnOrDay = rs.getLong(turnOrDayCol);
                double value = rs.getDouble("value");
                result.computeIfAbsent(metric, f -> new Long2DoubleOpenHashMap()).put(turnOrDay, value);
            }
        });
        return result;
    }

    public void addAllianceMetric(DBAlliance alliance, AllianceMetric metric, long turn, double value, boolean ignore) {
        checkNotNull(metric);
        if (!Double.isFinite(value)) {
            return;
        }
        String query = "INSERT OR " + (ignore ? "IGNORE" : "REPLACE") + " INTO `ALLIANCE_METRICS`(`alliance_id`, `metric`, `turn`, `value`) VALUES(?, ?, ?, ?)";
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

    public Map<DBAlliance, Map<AllianceMetric, Map<Long, Double>>> getAllianceMetrics(Set<Integer> allianceIds, AllianceMetric metric, long turn) {
        if (allianceIds.isEmpty()) throw new IllegalArgumentException("No metrics provided");
        List<Integer> alliancesSorted = new ArrayList<>(allianceIds);
        alliancesSorted.sort(Comparator.naturalOrder());
        String allianceQueryStr = StringMan.getString(alliancesSorted);

        Map<DBAlliance, Map<AllianceMetric, Map<Long, Double>>> result = new Object2ObjectOpenHashMap<>();

        String query = "SELECT * FROM ALLIANCE_METRICS WHERE alliance_id in " + allianceQueryStr + " AND metric = ? and turn <= ? ORDER BY turn DESC LIMIT " + allianceIds.size();
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
                        result.computeIfAbsent(alliance, f -> new Object2ObjectOpenHashMap<>()).computeIfAbsent(metric, f -> new Long2DoubleOpenHashMap()).put(turn, value);
                    }
                }
            }
        });
        return result;
    }

    public Map<DBAlliance, Map<Long, Double>> getAllianceMetrics(AllianceMetric metric, long startTurn) {
        Map<DBAlliance, Map<Long, Double>> result = new LinkedHashMap<>();
        String query = "SELECT * FROM ALLIANCE_METRICS WHERE metric = ? and turn >= ? ORDER BY turn ASC";
        query(query, new ThrowingConsumer<PreparedStatement>() {
            @Override
            public void acceptThrows(PreparedStatement stmt) throws Exception {
                stmt.setInt(1, metric.ordinal());
                stmt.setLong(2, startTurn);
            }
        }, new ThrowingConsumer<ResultSet>() {
            @Override
            public void acceptThrows(ResultSet rs) throws Exception {
                while (rs.next()) {
                    int allianceId = rs.getInt("alliance_id");
                    long turn = rs.getLong("turn");
                    double value = rs.getDouble("value");
                    DBAlliance alliance = getOrCreateAlliance(allianceId);
                    result.computeIfAbsent(alliance, f -> new Long2DoubleLinkedOpenHashMap()).put(turn, value);
                }
            }
        });
        return result;
    }


    public Map<DBAlliance, Map<AllianceMetric, Map<Long, Double>>> getAllianceMetrics(Set<Integer> allianceIds, AllianceMetric metric, long turnStart, long turnEnd) {
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

    public Map<DBAlliance, Map<AllianceMetric, Map<Long, Double>>> getAllianceMetrics(Set<Integer> allianceIds, Collection<AllianceMetric> metrics, long turnStart, long turnEnd) {
        if (metrics.isEmpty()) throw new IllegalArgumentException("No metrics provided");
        if (allianceIds.isEmpty()) throw new IllegalArgumentException("No metrics provided");
        Set<Integer> aaIdsFU = new IntOpenHashSet(allianceIds);

        List<Integer> alliancesSorted = new ArrayList<>(aaIdsFU);
        alliancesSorted.sort(Comparator.naturalOrder());
        String allianceQueryStr = StringMan.getString(alliancesSorted);
        String aaInClause = "alliance_id in " + allianceQueryStr + " AND ";

        String metricQueryStr = StringMan.getString(metrics.stream().map(Enum::ordinal).collect(Collectors.toList()));
        boolean hasTurnEnd = turnEnd > 0 && turnEnd < Long.MAX_VALUE;

        Map<DBAlliance, Map<AllianceMetric, Map<Long, Double>>> result = new Object2ObjectOpenHashMap<>();

        String query = "SELECT * FROM ALLIANCE_METRICS WHERE turn >= ?" + (hasTurnEnd ? " and turn <= ?" : "") + " AND " + aaInClause + "metric in " + metricQueryStr;
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
                    if (aaInClause == null && !aaIdsFU.contains(allianceId)) {
                        continue;
                    }
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

    public Set<Treaty> getTreatiesMatching(Predicate<Treaty> filter) {
        Set<Treaty> treaties = new ObjectOpenHashSet<>();
        synchronized (treatiesByAlliance) {
            for (Map<Integer, Treaty> allianceTreaties : treatiesByAlliance.values()) {
                for (Treaty treaty : allianceTreaties.values()) {
                    if (filter.test(treaty)) {
                        treaties.add(treaty);
                    }
                }
            }
        }
        return treaties;
    }

    public Set<Treaty> getTreaties() {
        Set<Treaty> treaties = new ObjectOpenHashSet<>();
        synchronized (treatiesByAlliance) {
            for (Map<Integer, Treaty> allianceTreaties : treatiesByAlliance.values()) {
                treaties.addAll(allianceTreaties.values());
            }
        }
        return treaties;
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

    public Set<Integer> getNationsActiveAtTurn(long turn) {
        Set<Integer> result = new IntOpenHashSet();
        try (PreparedStatement stmt = prepareQuery("SELECT nation FROM ACTIVITY WHERE turn = ?")) {
            stmt.setLong(1, turn);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(rs.getInt(1));
                }
            }
            return result;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public int[] saveLoot(List<LootEntry> entries, Consumer<Event> eventConsumer) {
        if (entries.isEmpty()) return new int[0];
        long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1);
        for (LootEntry entry : entries) {
            if (entry.getDate() < cutoff) continue;
            int id = entry.getId();
            if (id < 0) {
                DBAlliance aa = DBAlliance.get(-id);
                if (aa != null) {
                    aa.setLoot(entry);
                }
            }
            if (eventConsumer != null) eventConsumer.accept(new LootInfoEvent(entry));
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

    private void importLegacyNationLoot(boolean fromAttacks, Consumer<Event> eventConsumer) throws SQLException {
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
                saveLoot(lootInfoList, eventConsumer);
            }
        }
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
        return getActivity(nationId, 0, Long.MAX_VALUE);
    }

    public Map<Integer, Long> getLastActiveTurns(Set<Integer> nationIds, long turn) {
        if (nationIds.isEmpty()) return Collections.emptyMap();
        if (nationIds.size() == 1) {
            int nationId = nationIds.iterator().next();
            return Map.of(nationId, getLastActiveTurn(nationId, turn));
        }

        String query = "SELECT * " +
                "FROM Activity " +
                "WHERE (nation, turn) IN (SELECT nation, MAX(turn) " +
                "FROM Activity " +
                "WHERE turn <= CURRENT_TURN " +
                "AND nation in " + StringMan.getString(nationIds) + " " +
                "GROUP BY nation)";
        Map<Integer, Long> result = new Int2LongOpenHashMap();
        query(query, new ThrowingConsumer<PreparedStatement>() {
            @Override
            public void acceptThrows(PreparedStatement stmt) throws Exception {
            }
        }, new ThrowingConsumer<ResultSet>() {
            @Override
            public void acceptThrows(ResultSet rs) throws Exception {
                while (rs.next()) {
                    int nationId = rs.getInt("nation");
                    long turn = rs.getLong("turn");
                    result.put(nationId, turn);
                }
            }
        });
        return result;
    }

    public long getLastActiveTurn(int nationId, long turn) {
        try (PreparedStatement stmt = prepareQuery("SELECT * FROM Activity WHERE nation = ? AND turn <= ? ORDER BY turn DESC LIMIT 1")) {
            stmt.setInt(1, nationId);
            stmt.setLong(2, turn);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    return rs.getLong("turn");
                }
            }
            return 0;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public Set<Long> getActivityByDay(int nationId, long minTurn) {
        Set<Long> result = new LinkedHashSet<>();
        for (long turn : getActivity(nationId, minTurn, Long.MAX_VALUE)) {
            result.add(turn / 12);
        }
        return result;
    }

    public Map<Integer, Set<Long>> getActivityByDay(long minDate, long maxDate) {
        return getActivityByDay(minDate, maxDate, null);
    }

    public Map<Integer, Set<Long>> getActivityByDay(long minDate, long maxDate, Predicate<Integer> includeNation) {
        // dates are inclusive
        long minTurn = TimeUtil.getTurn(minDate);
        long maxTurn = TimeUtil.getTurn(maxDate);
        try (PreparedStatement stmt = prepareQuery("select nation, (`turn`/12) FROM ACTIVITY WHERE turn >= ? AND turn <= ?")) {
            stmt.setLong(1, minTurn);
            stmt.setLong(2, maxTurn);

            Map<Integer, Set<Long>> result = new Int2ObjectOpenHashMap<>();
            BiConsumer<Integer, Long> applyNation = includeNation == null ?
                    (nation, day) -> result.computeIfAbsent(nation, f -> new LongOpenHashSet()).add(day) : (nation, day) -> {
                if (includeNation.test(nation)) {
                    result.computeIfAbsent(nation, f -> new LongOpenHashSet()).add(day);
                }
            };

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt(1);
                    long day = rs.getLong(2);
                    applyNation.accept(id, day);
                }
            }
            return result;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public Map<Integer, Set<Long>> getActivityByTurn(long minTurn, long maxTurn, Predicate<Integer> includeNation) {
        try (PreparedStatement stmt = prepareQuery("select nation, `turn` FROM ACTIVITY WHERE turn >= ? AND turn <= ?")) {
            stmt.setLong(1, minTurn);
            stmt.setLong(2, maxTurn);

            Map<Integer, Set<Long>> result = new Int2ObjectOpenHashMap<>();
            BiConsumer<Integer, Long> applyNation = includeNation == null ?
                    (nation, turn) -> result.computeIfAbsent(nation, f -> new LongOpenHashSet()).add(turn) : (nation, turn) -> {
                if (includeNation.test(nation)) {
                    result.computeIfAbsent(nation, f -> new LongOpenHashSet()).add(turn);
                }
            };

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt(1);
                    long turn = rs.getLong(2);
                    applyNation.accept(id, turn);
                }
            }
            return result;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
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

    public Set<Long> getActivity(int nationId, long minTurn, long maxTurn) {
        String query = "SELECT * FROM ACTIVITY WHERE nation = ? AND turn > ?";
        if (maxTurn != Long.MAX_VALUE) {
            query += " AND turn <= ?";
        }
        query += " ORDER BY turn ASC";
        try (PreparedStatement stmt = prepareQuery(query)) {
            stmt.setInt(1, nationId);
            stmt.setLong(2, minTurn);
            if (maxTurn != Long.MAX_VALUE) stmt.setLong(3, maxTurn);

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

    public Map<Long, Map<Integer, Map<MilitaryUnit, Integer>>> getMilitaryHistoryByTurn(Set<Integer> nationIds, long startMs, long endMs) {
        Map<Integer, Map<MilitaryUnit, Map<Long, Integer>>> milHistory = getMilitaryHistory(nationIds, startMs, endMs);
        Map<Long, Map<Integer, Map<MilitaryUnit, Integer>>> milHistoryByTurn = new Long2ObjectArrayMap<>();
        for (Map.Entry<Integer, Map<MilitaryUnit, Map<Long, Integer>>> entry : milHistory.entrySet()) {
            int nationId = entry.getKey();
            Map<MilitaryUnit, Map<Long, Integer>> milHis = entry.getValue();
            for (Map.Entry<MilitaryUnit, Map<Long, Integer>> milEntry : milHis.entrySet()) {
                MilitaryUnit unit = milEntry.getKey();
                for (Map.Entry<Long, Integer> timeEntry : milEntry.getValue().entrySet()) {
                    long timeMs = timeEntry.getKey();
                    int count = timeEntry.getValue();
                    long turn = TimeUtil.getTurn(timeMs);
                    milHistoryByTurn.computeIfAbsent(turn, k -> new Int2ObjectOpenHashMap<>()).computeIfAbsent(nationId, k -> new EnumMap<>(MilitaryUnit.class)).put(unit, count);
                }
            }
        }
        return milHistoryByTurn;
    }

    public Map<Integer, Map<MilitaryUnit, Map<Long, Integer>>> getMilitaryHistory(Set<Integer> nations, long start, long end) {
        if (nations.isEmpty()) return Collections.emptyMap();
        List<Integer> nationsSorted = new ArrayList<>(nations);
        nationsSorted.sort(Comparator.naturalOrder());
        String nationQueryStr = StringMan.getString(nationsSorted);
        String query = "SELECT * FROM NATION_MIL_HISTORY WHERE id in " + nationQueryStr + " AND date >= ? AND date <= ? ORDER BY date DESC";
        Map<Integer, Map<MilitaryUnit, Map<Long, Integer>>> result = new Int2ObjectOpenHashMap<>();
        query(query, new ThrowingConsumer<PreparedStatement>() {
            @Override
            public void acceptThrows(PreparedStatement stmt) throws Exception {
                stmt.setLong(1, start);
                stmt.setLong(2, end);
            }
        }, new ThrowingConsumer<ResultSet>() {
            @Override
            public void acceptThrows(ResultSet rs) throws Exception {
                while (rs.next()) {
                    int nationId = rs.getInt("id");
                    MilitaryUnit unit = MilitaryUnit.values[rs.getInt("unit")];
                    long date = rs.getLong("date");
                    int amount = rs.getInt("amount");
                    result.computeIfAbsent(nationId, f -> new EnumMap<>(MilitaryUnit.class)).computeIfAbsent(unit, f -> new Long2IntLinkedOpenHashMap()).put(date, amount);
                }
            }
        });
        return result;
    }

    public List<Map.Entry<Long, Integer>> getMilitaryHistory(DBNation nation, MilitaryUnit unit, Long snapshot) {
        try (PreparedStatement stmt = prepareQuery("select * FROM NATION_MIL_HISTORY WHERE id = ? AND unit = ?"+ (snapshot == null ? "" : " AND date < ?") + " ORDER BY date DESC")) {
            stmt.setInt(1, nation.getNation_id());
            stmt.setInt(2, unit.ordinal());
            if (snapshot != null) stmt.setLong(3, snapshot);
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

    public Map<Integer, Map<MilitaryUnit, Long>> getLastMilitaryBuyByNationId(Set<Integer> nationIds) {
        Map<Integer, Map<MilitaryUnit, Long>> result = new Int2ObjectOpenHashMap<>();
        String query;
        if (nationIds != null && !nationIds.isEmpty() && nationIds.size() < 2000) {
            query = """
                    SELECT nmh.id, nmh.unit, MAX(nmh.date) AS last_buy_date
                    FROM NATION_MIL_HISTORY AS nmh
                    WHERE id in %IDS% AND nmh.amount > (
                        SELECT sub.amount
                        FROM NATION_MIL_HISTORY AS sub
                        WHERE sub.id = nmh.id
                            AND sub.unit = nmh.unit
                            AND sub.date < nmh.date
                        ORDER BY sub.date DESC
                        LIMIT 1
                    )
                    GROUP BY nmh.id, nmh.unit;""".replace("%IDS%", StringMan.getString(nationIds));
        } else {
            String ids = nationIds.stream().map(String::valueOf).collect(Collectors.joining(","));
            query = """
                    SELECT nmh.id, nmh.unit, MAX(nmh.date) AS last_buy_date
                    FROM NATION_MIL_HISTORY AS nmh
                    WHERE nmh.amount > (
                        SELECT sub.amount
                        FROM NATION_MIL_HISTORY AS sub
                        WHERE sub.id = nmh.id
                            AND sub.unit = nmh.unit
                            AND sub.date < nmh.date
                        ORDER BY sub.date DESC
                        LIMIT 1
                    )
                    GROUP BY nmh.id, nmh.unit;""";
        }
        try (PreparedStatement stmt = prepareQuery(query)) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int nationId = rs.getInt(1);
                    if (nationIds != null && !nationIds.contains(nationId)) continue;
                    MilitaryUnit unit = MilitaryUnit.values()[rs.getInt(2)];
                    long date = rs.getLong(3);
                    result.computeIfAbsent(nationId, k -> new EnumMap<>(MilitaryUnit.class)).put(unit, date);
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

    public boolean hasBought(DBNation nation, MilitaryUnit unit, long time, Long snapshot) {
        int last = nation.getUnits(unit);
        try (PreparedStatement stmt = prepareQuery("select * FROM NATION_MIL_HISTORY WHERE id = ? AND unit = ?" + (snapshot != null ? " AND date < ?" : "") + " ORDER BY date DESC")) {
            stmt.setInt(1, nation.getNation_id());
            stmt.setInt(2, unit.ordinal());
            if (snapshot != null) stmt.setLong(3, snapshot);
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
        try (PreparedStatement stmt = prepareQuery("select * FROM `NATION_MIL_HISTORY` WHERE `id` = ? AND `unit` = ? AND `date` < ? ORDER BY `date` DESC LIMIT 1")) {
            stmt.setInt(1, nation.getId());
            stmt.setInt(2, unit.ordinal());
            stmt.setLong(3, time);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
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

    public Map<Integer, List<DBNation>> getNationsByAlliance(boolean removeUntaxable, boolean removeInactive, boolean removeApplicants, boolean removeVM, boolean sortByScore) {
        long activeCutoff = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(7200);
        long turnNow = TimeUtil.getTurn();

        Predicate<DBNation> filter = new Predicate<DBNation>() {
            @Override
            public boolean test(DBNation nation) {
                if (removeApplicants && nation.getPositionEnum().id <= Rank.APPLICANT.id) return false;
                if (removeUntaxable && (nation.isGray() || nation.isBeige())) return false;
                if (removeInactive && (nation.lastActiveMs() < activeCutoff)) return false;
                if ((removeUntaxable || removeInactive || removeVM) && nation.getLeaving_vm() > turnNow) return false;
                return true;
            }
        };
        return getNationsByAlliance(filter, sortByScore);
    }

    public Map<DBAlliance, Integer> getAllianceRanks(Predicate<DBNation> filter, boolean sortByScore) {
        Map<Integer, List<DBNation>> nations = getNationsByAlliance(filter, sortByScore);
        Map<DBAlliance, Integer> ranks = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<DBNation>> entry : nations.entrySet()) {
            DBAlliance alliance = DBAlliance.getOrCreate(entry.getKey());
            ranks.put(alliance, ranks.size() + 1);
        }
        return ranks;
    }

    public Map<Integer, List<DBNation>> getNationsByAlliance(Predicate<DBNation> filter, boolean sortByScore) {
        final Int2DoubleMap scoreMap = new Int2DoubleOpenHashMap();
        Int2ObjectOpenHashMap<List<DBNation>> nationsByAllianceFiltered = new Int2ObjectOpenHashMap<>();
        synchronized (nationsByAlliance) {
            for (Map.Entry<Integer, Map<Integer, DBNation>> entry : nationsByAlliance.entrySet()) {
                int aaId = entry.getKey();
                Map<Integer, DBNation> nationMap = entry.getValue();
                double score = 0;
                for (DBNation nation : nationMap.values()) {
                    if (filter != null && !filter.test(nation)) continue;
                    score += nation.getScore();
                    nationsByAllianceFiltered.computeIfAbsent(aaId, f -> new ObjectArrayList<>()).add(nation);
                }
                if (score > 0) {
                    scoreMap.put(aaId, score);
                }
            }
        }
        return sortByScore ? sortByScore(scoreMap, nationsByAllianceFiltered) : nationsByAllianceFiltered;
    }

    private Int2ObjectLinkedOpenHashMap<List<DBNation>> sortByScore(Int2DoubleMap scoreMap, Int2ObjectOpenHashMap<List<DBNation>> nationsByAllianceFiltered) {
        IntArrayList aaIds = new IntArrayList(scoreMap.keySet());
        aaIds.sort((IntComparator) (id1, id2) -> Double.compare(scoreMap.get(id2), scoreMap.get(id1)));
        Int2ObjectLinkedOpenHashMap<List<DBNation>> sortedMap = new Int2ObjectLinkedOpenHashMap<>(nationsByAllianceFiltered.size());
        for (int aaId : aaIds) {
            sortedMap.put(aaId, nationsByAllianceFiltered.get(aaId));
        }
        return sortedMap;
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
            DBNation dbNation = getNationById(nationId);
            return dbNation != null ? dbNation.getUnits(unit) : 0;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

//    public void setSpies(int nation, int spies) {
//        long day = TimeUtil.getDay();
//        update("INSERT OR REPLACE INTO `SPIES_BUILDUP` (`nation`, `spies`, `day`) VALUES(?, ?, ?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
//            stmt.setInt(1, nation);
//            stmt.setInt(2, spies);
//            stmt.setLong(3, day);
//        });
//    }
//
//    public Map.Entry<Integer, Long> getLatestSpyCount(int nationId, long beforeDay) {
//        String queryStr = "SELECT * from SPIES_BUILDUP where nation = ? AND day < ? order by day DESC limit 1";
//
//        try (PreparedStatement stmt = prepareQuery(queryStr)) {
//            stmt.setInt(1, nationId);
//            stmt.setLong(2, beforeDay);
//            try (ResultSet rs = stmt.executeQuery()) {
//                while (rs.next()) {
//                    int spies = rs.getInt("spies");
//                    long day = rs.getLong("day");
//                    return new AbstractMap.SimpleEntry<>(spies, day);
//                }
//            }
//            return null;
//        } catch (SQLException e) {
//            e.printStackTrace();
//            throw new RuntimeException(e);
//        }
//    }
//
//    public Map<Integer, Integer> getLastSpiesByNation(Set<Integer> nationIds, long lastDay) {
//        String query = "SELECT nation, spies, max(day) as day from SPIES_BUILDUP where nation in " + StringMan.getString(nationIds) + " AND day < ? GROUP BY nation";
//        try (PreparedStatement stmt = prepareQuery(query)) {
//            stmt.setLong(1, lastDay);
//
//            Map<Integer, Integer> map = new LinkedHashMap<>();
//
//            try (ResultSet rs = stmt.executeQuery()) {
//                while (rs.next()) {
//                    int nationId = rs.getInt("nation");
//                    int spies = rs.getInt("spies");
//                    long day = rs.getLong("day");
//                    map.put(nationId, spies);
//                }
//            }
//            return map;
//        } catch (SQLException e) {
//            e.printStackTrace();
//            throw new RuntimeException(e);
//        }
//    }
//
//    public Map<Long, Integer> getSpiesByDay(int nationId) {
//        try (PreparedStatement stmt = prepareQuery("select * FROM SPIES_BUILDUP WHERE nation = ? ORDER BY day DESC")) {
//            stmt.setInt(1, nationId);
//
//            Map<Long, Integer> map = new LinkedHashMap<>();
//
//            try (ResultSet rs = stmt.executeQuery()) {
//                while (rs.next()) {
//                    int spies = rs.getInt("spies");
//                    long day = rs.getLong("day");
//                    map.put(day, spies);
//                }
//            }
//            return map;
//        } catch (SQLException e) {
//            e.printStackTrace();
//            throw new RuntimeException(e);
//        }
//    }

//    public void setProjects(int nationId, Set<Project> projects) {
//        Set<Integer> projectIds = new HashSet<>();
//    }

    public void addRemove(int nationId, int fromAA, int toAA, Rank fromRank, Rank toRank, long time) {
        update("INSERT INTO `KICKS2`(`nation`, `from_aa`, `to_aa`, `from_rank`, `to_rank`, `date`) VALUES(?, ?, ?, ?, ?, ?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, nationId);
            stmt.setInt(2, fromAA);
            stmt.setInt(3, toAA);
            stmt.setInt(4, fromRank.ordinal());
            stmt.setInt(5, toRank.ordinal());
            stmt.setLong(6, time);
        });
    }

    public AllianceChange getPreviousAlliance(int nationId, int currentAA) {
        String query = "select * FROM KICKS2 WHERE nation = ? AND from_aa != 0 AND from_aa != ? ORDER BY date DESC LIMIT 1";
        try (PreparedStatement stmt = prepareQuery(query)) {
            stmt.setInt(1, nationId);
            stmt.setInt(2, currentAA);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    return new AllianceChange(rs);
                }
            }
            return null;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public long getAllianceApplicantSeniorityTimestamp(DBNation nation, Long snapshotDate) {
        if (nation.getAlliance_id() == 0) return Long.MAX_VALUE;
        try (PreparedStatement stmt = prepareQuery("select * FROM KICKS2 WHERE nation = ? " + (snapshotDate != null ? "AND DATE < " + snapshotDate : "") + " AND from_aa != ? ORDER BY date DESC LIMIT 1")) {
            stmt.setInt(1, nation.getNation_id());
            stmt.setInt(2, nation.getAlliance_id());
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

    public long getAllianceMemberSeniorityTimestamp(DBNation nation, Long snapshotDate) {
        if (nation.getPosition() < Rank.MEMBER.id) return Long.MAX_VALUE;
        try (PreparedStatement stmt = prepareQuery("select * FROM KICKS2 WHERE nation = ? " + (snapshotDate != null ? "AND DATE < " + snapshotDate : "") + " AND (from_rank < 2 OR from_aa != to_aa) ORDER BY date DESC LIMIT 1")) {
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

    public List<AllianceChange> getRemovesByNation(int nationId) {
        return getRemovesByNation(nationId, null);
    }
    public List<AllianceChange> getRemovesByNation(int nationId, Long date) {
        try (PreparedStatement stmt = prepareQuery("select * FROM KICKS2 WHERE nation = ? " + (date != null && date != 0 ? "AND date > ? " : "") + "ORDER BY date DESC")) {
            stmt.setInt(1, nationId);
            if (date != null) {
                stmt.setLong(2, date);
            }

            List<AllianceChange> list = new ObjectArrayList<>();

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(new AllianceChange(rs));
                }
            }
            return list;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public Map<Integer, List<AllianceChange>> getRemovesByAlliances(long cutoff) {
        String query = "SELECT * FROM KICKS2 " + (cutoff > 0 ? "WHERE date > ? " : "") + "ORDER BY date DESC";
        Map<Integer, List<AllianceChange>> resultsByAA = new Int2ObjectOpenHashMap<>();
        try (PreparedStatement stmt = prepareQuery(query)) {
            if (cutoff > 0) {
                stmt.setLong(1, cutoff);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    AllianceChange change = new AllianceChange(rs);
                    if (change.getFromId() != 0) {
                        resultsByAA.computeIfAbsent(change.getFromId(), k -> new ObjectArrayList<>()).add(change);
                    }
                    if (change.getToId() != 0) {
                        resultsByAA.computeIfAbsent(change.getToId(), k -> new ObjectArrayList<>()).add(change);
                    }
                }
            }
            return resultsByAA;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }

    }

    public Map<Integer, List<AllianceChange>> getRemovesByAlliances(Set<Integer> alliances, long cutoff) {
        if (alliances.isEmpty()) return Collections.emptyMap();
        if (alliances.size() == 1) {
            int alliance = alliances.iterator().next();
            List<AllianceChange> result = getRemovesByAlliance(alliance, cutoff);
            return Collections.singletonMap(alliance, result);
        } else {
            Map<Integer, List<AllianceChange>> resultsByAA = new Int2ObjectOpenHashMap<>();

            Set<Integer> fastMap = new IntOpenHashSet(alliances);
            List<Integer> alliancesSorted = new ArrayList<>(alliances);
            alliancesSorted.sort(Comparator.naturalOrder());
            String query = "SELECT * FROM KICKS2 WHERE (from_aa IN " + StringMan.getString(alliancesSorted) + " OR to_aa IN " + StringMan.getString(alliancesSorted) + ")" + (cutoff > 0 ? " AND date > ? " : "") + "ORDER BY date DESC";

            try (PreparedStatement stmt = getConnection().prepareStatement(query)) {
                if (cutoff > 0) {
                    stmt.setLong(1, cutoff);
                }
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {

                    AllianceChange change = new AllianceChange(rs);
                    if (fastMap.contains(change.getFromId())) {
                        resultsByAA.computeIfAbsent(change.getFromId(), k -> new ObjectArrayList<>()).add(change);
                    }
                    if (fastMap.contains(change.getToId())) {
                        resultsByAA.computeIfAbsent(change.getToId(), k -> new ObjectArrayList<>()).add(change);
                    }
                }
                return resultsByAA;
            } catch (SQLException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
    }

    public List<AllianceChange> getRemovesByAlliance(int allianceId) {
        return getRemovesByAlliance(allianceId, 0L);
    }

    public List<AllianceChange> getRemovesByAlliance(int allianceId, long cutoff) {
        List<AllianceChange> list = new ObjectArrayList<>();
        try (PreparedStatement stmt = prepareQuery("select * FROM KICKS2 WHERE (from_aa = ? OR to_aa = ?) " + (cutoff > 0 ? "AND date > ? " : "") + "ORDER BY date DESC")) {
            stmt.setInt(1, allianceId);
            stmt.setInt(2, allianceId);
            if (cutoff > 0) {
                stmt.setLong(3, cutoff);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    AllianceChange change = new AllianceChange(rs);
                    list.add(change);
                }
            }
            return list;
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
            Object cities = citiesByNation.get(nation_id);
            return (Map) ArrayUtil.toMap(SimpleDBCity.class, cities, DBCity.GET_ID);
        }
    }

    public Set<DBCity> getCities() {
        synchronized (citiesByNation) {
            Set<DBCity> result = new ObjectOpenHashSet<>();
            for (Object cities : citiesByNation.values()) {
                ArrayUtil.iterateElements(SimpleDBCity.class, cities, result::add);
            }
            return result;
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
        return getCitiesV3ByCityId(cityId, false, null);
    }

    public DBCity getCitiesV3ByCityId(int cityId, boolean fetch, Consumer<Event> eventConsumer) {
        synchronized (citiesByNation) {
            for (Map.Entry<Integer, Object> entry : citiesByNation.entrySet()) {
                Object cities = entry.getValue();
                DBCity city = ArrayUtil.getElement(SimpleDBCity.class, cities, cityId);
                if (city != null) {
                    return city;
                }
            }
        }
        if (fetch) {
            synchronized (dirtyCities) {
                dirtyCities.add(cityId);
            }
            updateDirtyCities(true, eventConsumer);
            return getCitiesV3ByCityId(cityId, false, eventConsumer);
        }
        return null;
    }

    public void saveAllCities() {
        List<DBCity> allCities = new ArrayList<>();
        synchronized (citiesByNation) {
            for (Map.Entry<Integer, Object> entry : citiesByNation.entrySet()) {
                ArrayUtil.iterateElements(SimpleDBCity.class, entry.getValue(), allCities::add);
            }
        }
        saveCities(allCities);
    }

    public void saveCities(List<DBCity> cities) {
        if (cities.isEmpty()) return;
        executeBatch(cities, "INSERT OR REPLACE INTO `CITY_BUILDS`(`id`, `nation`, `created`, `infra`, `land`, `powered`, `improvements`, `update_flag`, `nuke_date`) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?)", new ThrowingBiConsumer<DBCity, PreparedStatement>() {
            @Override
            public void acceptThrows(DBCity city, PreparedStatement stmt) throws Exception {
                int nationId = city.getNationId();
                stmt.setInt(1, city.getId());
                stmt.setInt(2, nationId);
                stmt.setLong(3, city.getCreated());
                stmt.setInt(4, (int) (city.getInfra() * 100));
                stmt.setInt(5, (int) (city.getLand() * 100));
                stmt.setBoolean(6, city.isPowered());
                stmt.setBytes(7, city.toFull());
                stmt.setLong(8, city.getFetched());
                stmt.setLong(9, TimeUtil.getTimeFromTurn(city.getNuke_turn()));
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
                    Object cities = citiesByNation.remove(id);
                    if (cities != null) {
                        ArrayUtil.iterateElements(SimpleDBCity.class, cities, city -> citiesToDelete.add(city.getId()));
                    }
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
            deleteCitiesInDB(citiesToDelete);
        }
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

    public Set<DBNation> getNationsByBracket(int taxId) {
        if (taxId == 0) {
            return getNationsMatching(n -> n.getTax_id() == 0);
        }
        synchronized (nationsByAlliance) {
            for (Map.Entry<Integer, Map<Integer, DBNation>> entry : nationsByAlliance.entrySet()) {
                Map<Integer, DBNation> byId = entry.getValue();
                for (DBNation nation : byId.values()) {
                    if (nation.getTax_id() == taxId) {
                        return byId.values().stream().filter(n -> n.getTax_id() == taxId).collect(Collectors.toSet());
                    }
                }
            }
        }
        return Collections.emptySet();
    }
}
