package link.locutus.discord.db.conflict;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Predicates;
import com.google.common.eventbus.Subscribe;
import com.ptsmods.mysqlw.Database;
import it.unimi.dsi.fastutil.ints.Int2ByteOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntLinkedOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.Logg;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AbstractCursor;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.IAttack;
import link.locutus.discord.apiv3.enums.AttackTypeSubCategory;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.WarDB;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DBTopic;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.entities.conflict.ConflictCategory;
import link.locutus.discord.db.entities.conflict.ConflictMetric;
import link.locutus.discord.db.handlers.AttackQuery;
import link.locutus.discord.event.game.TurnChangeEvent;
import link.locutus.discord.event.war.AttackEvent;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.scheduler.KeyValue;
import link.locutus.discord.util.scheduler.ThrowingBiConsumer;
import link.locutus.discord.util.scheduler.ThrowingConsumer;
import link.locutus.discord.util.scheduler.ThrowingFunction;
import link.locutus.discord.web.jooby.CloudStorage;
import link.locutus.discord.web.jooby.JteUtil;
import link.locutus.discord.web.jooby.S3CompatibleStorage;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static link.locutus.discord.db.conflict.ConflictField.*;
import static link.locutus.discord.util.IOUtil.writeMsgpackBytes;
import static link.locutus.discord.util.math.ArrayUtil.ALWAYS_TRUE_INT;
import static link.locutus.discord.web.jooby.JteUtil.copyArrayElements;

public class ConflictManager {
    private final WarDB db;
    private final CloudStorage aws;

    private final Map<Integer, Conflict> conflictById = new Int2ObjectOpenHashMap<>();
    private Conflict[] conflictArr;
    private final Set<Integer> activeConflictsOrd = new IntOpenHashSet();
    private long lastTurn = 0;
    private final Object conflictArrLock = new Object();
    private final Int2ObjectMap<IntSet> activeConflictOrdByAllianceId = new Int2ObjectOpenHashMap<>();

    private final Map<Integer, String> legacyNames2 = new Int2ObjectOpenHashMap<>();
    private final Map<String, Map<Long, Integer>> legacyIdsByDate = new ConcurrentHashMap<>();

    private final Set<Integer> conflictAlliances = new IntOpenHashSet();

    private volatile boolean conflictHeaderHashInitialized = false;
    private volatile long conflictHeaderHash = 0L;

    private final Map<Integer, Map<HeaderGroup, Long>> cacheTimes = new Int2ObjectOpenHashMap<>();

    private final TurnSnapshotHolder GLOBAL_TURN_SNAPSHOT = new TurnSnapshotHolder(() -> {
        synchronized (conflictArrLock) {
            return Arrays.copyOf(conflictArr, conflictArr.length);
        }
    });

    private static ConflictManager INSTANCE;
    public static ConflictManager get() {
        if (INSTANCE != null) return INSTANCE;
        return Locutus.imp().getWarDb().getConflicts();
    }

    public ConflictManager(WarDB db) {
        if (INSTANCE != null) {
            throw new IllegalStateException("ConflictManager already initialized!");
        }
        INSTANCE = this;
        this.db = db;
        this.aws = S3CompatibleStorage.setupAuto();

        // Tables
        {
            db.executeStmt("CREATE TABLE IF NOT EXISTS conflict_announcements2 (" +
                    "conflict_id INTEGER NOT NULL, " +
                    "topic_id INTEGER NOT NULL, " +
                    "description VARCHAR NOT NULL, " +
                    "PRIMARY KEY (conflict_id, topic_id), FOREIGN KEY(conflict_id) REFERENCES conflicts(id))");

            String createConflicts = "CREATE TABLE IF NOT EXISTS conflicts (" +
                    String.join(", ",
                            ID + " INTEGER PRIMARY KEY AUTOINCREMENT",
                            NAME + " VARCHAR NOT NULL",
                            START + " BIGINT NOT NULL",
                            END + " BIGINT NOT NULL",
                            COL1 + " VARCHAR NOT NULL",
                            COL2 + " VARCHAR NOT NULL",
                            WIKI + " VARCHAR NOT NULL",
                            CB + " VARCHAR NOT NULL",
                            STATUS + " VARCHAR NOT NULL",
                            CATEGORY + " INTEGER NOT NULL",
                            CREATOR + " BIGINT NOT NULL",
                            PUSHED_INDEX + " BIGINT NOT NULL",
                            PUSHED_PAGE + " BIGINT NOT NULL",
                            PUSHED_GRAPH + " BIGINT NOT NULL",
                            RECALC_GRAPH + " BOOLEAN NOT NULL"
                    ) + ")";
            db.executeStmt(createConflicts);
            db.executeStmt("ALTER TABLE conflicts ADD COLUMN creator BIGINT DEFAULT 0", true);
            db.executeStmt("ALTER TABLE conflicts ADD COLUMN wiki VARCHAR DEFAULT ''", true);
            db.executeStmt("ALTER TABLE conflicts ADD COLUMN cb VARCHAR DEFAULT ''", true);
            db.executeStmt("ALTER TABLE conflicts ADD COLUMN status VARCHAR DEFAULT ''", true);
            db.executeStmt("ALTER TABLE conflicts ADD COLUMN category INTEGER DEFAULT 0", true);
            db.executeStmt("ALTER TABLE conflicts ADD COLUMN " + PUSHED_INDEX + " BIGINT DEFAULT 0", true);
            db.executeStmt("ALTER TABLE conflicts ADD COLUMN " + PUSHED_PAGE + " BIGINT DEFAULT 0", true);
            db.executeStmt("ALTER TABLE conflicts ADD COLUMN " + PUSHED_GRAPH + " BIGINT DEFAULT 0", true);
            db.executeStmt("ALTER TABLE conflicts ADD COLUMN " + RECALC_GRAPH + " BOOLEAN DEFAULT 0", true);

            db.executeStmt("CREATE TABLE IF NOT EXISTS conflict_participant (conflict_id INTEGER NOT NULL, " +
                    "alliance_id INTEGER NOT NULL, " +
                    "side BOOLEAN, start BIGINT NOT NULL, " +
                    "end BIGINT NOT NULL, " +
                    "PRIMARY KEY (conflict_id, alliance_id), FOREIGN KEY(conflict_id) REFERENCES conflicts(id))");
            db.executeStmt("CREATE TABLE IF NOT EXISTS legacy_names2 (id INTEGER NOT NULL, name VARCHAR NOT NULL, date BIGINT DEFAULT 0, PRIMARY KEY (id, name, date))");

            db.executeStmt("CREATE TABLE IF NOT EXISTS conflict_graphs2 (conflict_id INTEGER NOT NULL, side BOOLEAN NOT NULL, alliance_id INT NOT NULL, metric INTEGER NOT NULL, turn BIGINT NOT NULL, city INTEGER NOT NULL, value INTEGER NOT NULL, PRIMARY KEY (conflict_id, alliance_id, metric, turn, city), FOREIGN KEY(conflict_id) REFERENCES conflicts(id))");

            db.executeStmt("CREATE TABLE IF NOT EXISTS source_sets (guild BIGINT NOT NULL, source_id BIGINT NOT NULL, source_type INT NOT NULL, PRIMARY KEY (guild, source_id, source_type))");

            // attack_subtypes attack id int primary key, subtype int not null
            db.executeStmt("CREATE TABLE IF NOT EXISTS attack_subtypes (attack_id INT PRIMARY KEY, subtype INT NOT NULL)");

            // create table if not exists MANUAL_WARS war_id, conflict_id, int alliance, primary key (war_id)
            db.executeStmt("CREATE TABLE IF NOT EXISTS MANUAL_WARS (war_id INT PRIMARY KEY, conflict_id INT NOT NULL, alliance INT NOT NULL)");

            db.executeStmt("CREATE TABLE IF NOT EXISTS conflict_row_cache (" +
                    "conflict_id INTEGER, " +
                    "header_hash BIGINT NOT NULL, " +
                    "row_data BLOB NOT NULL, " +
                    "type INTEGER NOT NULL, " +
                    "updated_ms BIGINT NOT NULL DEFAULT 0, " +
                    "FOREIGN KEY(conflict_id) REFERENCES conflicts(id)," +
                    "PRIMARY KEY (conflict_id, type))");
        }
        { // load
            List<Conflict> conflicts = new ObjectArrayList<>();
            conflictById.clear();

            db.query("SELECT conflict_id, type, updated_ms from conflict_row_cache", stmt -> {
            }, (ThrowingConsumer<ResultSet>) rs -> {
                while (rs.next()) {
                    int conflictId = rs.getInt("conflict_id");
                    long updatedMs = rs.getLong("updated_ms");
                    HeaderGroup type = HeaderGroup.values[rs.getInt("type")];
                    cacheTimes.computeIfAbsent(conflictId, k -> new EnumMap<>(HeaderGroup.class)).put(type, updatedMs);
                }
            });

            db.query("SELECT * FROM conflicts", stmt -> {
            }, (ThrowingConsumer<ResultSet>) rs -> {
                int ordinal = 0;
                while (rs.next()) {
                    int id = rs.getInt("id");
                    String name = rs.getString("name");
                    long startTurn = rs.getLong("start");
                    long endTurn = rs.getLong("end");
                    long pushedIndex = rs.getLong(PUSHED_INDEX.name());
                    long pushedPage = rs.getLong(PUSHED_PAGE.name());
                    long pushedGraph = rs.getLong(PUSHED_GRAPH.name());
                    boolean recalcGraph = rs.getBoolean(RECALC_GRAPH.name());
                    Conflict conflict = new Conflict(id, ordinal++, name, startTurn, endTurn, pushedIndex, pushedPage, pushedGraph, recalcGraph);
                    if (conflict.isActive()) {
                        conflict.setLoaded(rs);
                    }
                    conflicts.add(conflict);
                    conflictById.put(id, conflict);
                }
            });
            this.conflictArr = conflicts.toArray(new Conflict[0]);
            GLOBAL_TURN_SNAPSHOT.invalidateConflictSnapshot();
            for (Conflict c : conflictArr) {
                if (c != null && c.isActive()) activeConflictsOrd.add(c.getOrdinal());
            }

            db.query("SELECT * FROM legacy_names2", stmt -> {
            }, (ThrowingConsumer<ResultSet>) rs -> {
                while (rs.next()) {
                    System.out.println("Loading legacy name: " + rs.getInt("id") + " -> " + rs.getString("name") + " at " + rs.getLong("date"));
                    int id = rs.getInt("id");
                    String name = rs.getString("name");
                    long date = rs.getLong("date");
                    legacyNames2.put(id, name);
                    String nameLower = name.toLowerCase(Locale.ROOT);
                    legacyIdsByDate.computeIfAbsent(nameLower, k -> new Long2IntOpenHashMap()).put(date, id);
                }
            });

            db.query("SELECT * FROM conflict_participant", stmt -> {
            }, (ThrowingConsumer<ResultSet>) rs -> {
                while (rs.next()) {
                    int allianceId = rs.getInt("alliance_id");
                    conflictAlliances.add(allianceId);
                }
            });

            for (Map.Entry<String, Integer> entry : LegacyAllianceNames.get().entrySet()) {
                String name = entry.getKey();
                int id = entry.getValue();
                if (!legacyNames2.containsKey(id)) {
                    legacyNames2.put(id, name);
                }
                String nameLower = name.toLowerCase(Locale.ROOT);
                Map<Long, Integer> map = legacyIdsByDate.computeIfAbsent(nameLower, k -> new Long2IntOpenHashMap());
                if (map.isEmpty()) {
                    map.put(Long.MAX_VALUE, id);
                }
            }

            List<Conflict> actives = getActiveConflicts();
            if (!actives.isEmpty()) {
                loadConflictWars(actives, false, true, true);
            }
            Locutus.imp().getRepeatingTasks().addTask("Conflict Website", this::pushDirtyConflicts, Settings.INSTANCE.TASKS.WAR_STATS_PUSH_INTERVAL, TimeUnit.MINUTES);
        }
    }

    public WarDB getDb() {
        return db;
    }

    private synchronized void importData(Database sourceDb, Database targetDb, String tableName) throws SQLException {
        Connection sourceConnection = sourceDb.getConnection();
        Connection targetConnection = targetDb.getConnection();

        targetConnection.setAutoCommit(false);

        try (Statement sourceStatement = sourceConnection.createStatement();
             ResultSet resultSet = sourceStatement.executeQuery("SELECT * FROM " + tableName)) {
            ResultSetMetaData metaData = resultSet.getMetaData();
            int columnCount = metaData.getColumnCount();
            String placeholders = String.join(", ", Collections.nCopies(columnCount, "?"));
            String targetSql = "INSERT INTO " + tableName + " VALUES (" + placeholders + ")";

            try (PreparedStatement targetStatement = targetConnection.prepareStatement(targetSql)) {
                while (resultSet.next()) {
                    for (int i = 1; i <= columnCount; i++) {
                        targetStatement.setObject(i, resultSet.getObject(i));
                    }
                    targetStatement.executeUpdate();
                }
            }
            targetConnection.commit();
        } catch (SQLException e) {
            targetConnection.rollback();
            throw e;
        } finally {
            targetConnection.setAutoCommit(true);
        }
    }

    public void importFromExternal(File file) throws SQLException {
        Database otherDb = Database.connect(file);
        List<String> tables = Arrays.asList(
            "conflicts",
                "conflict_participant",
                "conflict_announcements2",
//                "conflict_graphs2",
                "legacy_names2",
                "source_sets"
//                "attack_subtypes"
        );
        // clear conflict_graphs2 and attack_subtypes
        db.executeStmt("DELETE FROM conflict_graphs2");
        db.executeStmt("DELETE FROM attack_subtypes");
        db.executeStmt("DELETE FROM conflict_row_cache");
        for (String table : tables) {
            // clear all rows of table
            db.executeStmt("DELETE FROM " + table);
            importData(otherDb, db.getDb(), table);
        }
    }

    public String pushIndex(long time) {
        synchronized (this.loadConflictLock) {
            String key = "conflicts/index.gzip";
            byte[] value = getIndexBytesZip(time);
            aws.putObject(key, value, 60);
            return aws.getLink(key);
        }
    }

    public boolean pushDirtyConflicts() {
        long now = System.currentTimeMillis();
        boolean updateIndex = false;
        List<Conflict> activeConflicts = getActiveConflicts();
        if (activeConflicts.isEmpty()) {
            Logg.text("[Conflict] No active conflicts to push.");
            return false;
        }
        for (Conflict conflict : activeConflicts) {
            long lastPushPage = conflict.getPushedPage();
            long lastPushGraph = conflict.getPushedGraph();
            long lastPushIndex = conflict.getPushedIndex();

            long lastWarUpdate = conflict.getLatestWarAttack();
            long lastGraphUpdate = conflict.getLatestGraphMs();

            boolean updatePageMeta = false;
            boolean updateGraphMeta = false;
            boolean updatePageStats = false;
            boolean updateGraphStats = false;
            synchronized (cacheTimes) {
                Map<HeaderGroup, Long> lastCacheTimes = cacheTimes.getOrDefault(conflict.getId(), Collections.emptyMap());
                long lastPageMeta = lastCacheTimes.getOrDefault(HeaderGroup.PAGE_META, 0L);
                long lastPageStats = lastCacheTimes.getOrDefault(HeaderGroup.PAGE_STATS, 0L);
                long lastGraphMeta = lastCacheTimes.getOrDefault(HeaderGroup.GRAPH_META, 0L);
                long lastGraphData = lastCacheTimes.getOrDefault(HeaderGroup.GRAPH_DATA, 0L);

                if (lastPageMeta == 0L) updatePageMeta = true;
                if (lastWarUpdate > lastPageStats) updatePageStats = true;
                if (lastGraphMeta == 0L) updateGraphMeta = true;
                if (lastGraphUpdate > lastGraphData) updateGraphStats = true;
            }

            if (!updatePageMeta && !updatePageStats && !updateGraphMeta && !updateGraphStats) {
                Logg.text("[Conflict] No updates needed for conflict " + conflict.getName() + " (ID " + conflict.getId() + ").");
                continue;
            }

            Logg.text("[Conflict] Pushing updates for conflict " + conflict.getName() + " (ID " + conflict.getId() + "): " +
                    (updatePageMeta ? "page meta " : "") +
                    (updatePageStats ? "page stats " : "") +
                    (updateGraphMeta ? "graph meta " : "") +
                    (updateGraphStats ? "graph stats " : "")
            );

            if (updatePageMeta || updatePageStats) {
                updateIndex = true;
            }

            conflict.pushChanges(this, null, updatePageMeta, updatePageStats, updateGraphMeta, updateGraphStats, false, now);
        }
        if (updateIndex) {
            Logg.text("[Conflict] Pushing updated index.");
            pushIndex(now);
            return true;
        }
        return false;
    }

    public synchronized void initTurn() {
        long currTurn = TimeUtil.getTurn();
        if (lastTurn != currTurn) {
            activeConflictsOrd.removeIf(f -> {
                Conflict conflict = conflictArr[f];
                return (conflict == null || conflict.getEndTurn() <= currTurn);
            });
            recreateConflictsByAlliance();
            lastTurn = currTurn;
        }
    }

    private static final Conflict[] EMPTY_CONFLICTS = new Conflict[0];

    private static final class TurnSnapshot {
        private static final int BASE_ALLIANCE_CAP_EXCL = 15_000 + 1; // index by allianceId
        private static final int MIN_ACTIVE_IDS_CAP = 64;

        long turn = Long.MIN_VALUE;
        int version = Integer.MIN_VALUE;

        Conflict[] conflicts = EMPTY_CONFLICTS;

        // allianceId -> sorted ords (active on `turn`)
        int[][] ordsByAlliance = new int[BASE_ALLIANCE_CAP_EXCL][];

        // compact list of allianceIds with ordsByAlliance[allianceId] != null (for fast clearing)
        int[] activeAllianceIds = new int[MIN_ACTIVE_IDS_CAP];
        int activeAllianceCount;

        // scratch reused for building; also cleared via activeAllianceIds
        int[] countsByAlliance = new int[BASE_ALLIANCE_CAP_EXCL];

        void invalidate() {
            clearIndex();
            turn = Long.MIN_VALUE;
            version = Integer.MIN_VALUE;
            conflicts = EMPTY_CONFLICTS;
        }

        void rebuild(long turn, int version, Conflict[] conflicts) {
            clearIndex();
            this.turn = turn;
            this.version = version;
            this.conflicts = conflicts;
            buildOrdsByAlliance(turn, conflicts);
        }

        private void ensureCapacityExclusive(int neededExclusive) {
            if (neededExclusive <= ordsByAlliance.length) return;

            int newLen = ordsByAlliance.length;
            while (newLen < neededExclusive) {
                final int grown = newLen + (newLen >> 1); // ~1.5x
                if (grown <= newLen) {                   // overflow guard
                    newLen = neededExclusive;
                    break;
                }
                newLen = grown;
            }

            ordsByAlliance = Arrays.copyOf(ordsByAlliance, newLen);
            countsByAlliance = Arrays.copyOf(countsByAlliance, newLen);
        }

        private void recordActiveAlliance(int aaId) {
            if (activeAllianceCount == activeAllianceIds.length) {
                activeAllianceIds = Arrays.copyOf(activeAllianceIds, activeAllianceCount * 2);
            }
            activeAllianceIds[activeAllianceCount++] = aaId;
        }

        private void clearIndex() {
            for (int i = 0; i < activeAllianceCount; i++) {
                final int aaId = activeAllianceIds[i];
                if (aaId >= 0 && aaId < ordsByAlliance.length) {
                    ordsByAlliance[aaId] = null;
                    countsByAlliance[aaId] = 0;
                }
            }
            activeAllianceCount = 0;
        }

        private void buildOrdsByAlliance(long turn, Conflict[] conflicts) {
            int[][] ords = ordsByAlliance;
            int[] counts = countsByAlliance;

            // pass 1: count + remember active alliance ids
            for (int ord = 0; ord < conflicts.length; ord++) {
                final Conflict c = conflicts[ord];
                if (c == null) continue;

                if (c.getStartTurn() > turn || c.getEndTurn() <= turn) continue;

                for (int aaId : c.getAllianceIds()) {
                    if (aaId <= 0) continue;
                    if (c.getStartTurn(aaId) > turn || c.getEndTurn(aaId) <= turn) continue;

                    if (aaId >= ords.length) {
                        ensureCapacityExclusive(aaId + 1);
                        ords = ordsByAlliance;
                        counts = countsByAlliance;
                    }

                    if (counts[aaId]++ == 0) recordActiveAlliance(aaId);
                }
            }

            // allocate exact-sized arrays; reuse counts[] as write cursor
            for (int i = 0; i < activeAllianceCount; i++) {
                final int aaId = activeAllianceIds[i];
                ords[aaId] = new int[counts[aaId]];
                counts[aaId] = 0;
            }

            // pass 2: fill
            for (int ord = 0; ord < conflicts.length; ord++) {
                final Conflict c = conflicts[ord];
                if (c == null) continue;

                if (c.getStartTurn() > turn || c.getEndTurn() <= turn) continue;

                for (int aaId : c.getAllianceIds()) {
                    if (aaId <= 0) continue;
                    if (c.getStartTurn(aaId) > turn || c.getEndTurn(aaId) <= turn) continue;

                    // Should be in-range thanks to pass 1, but keep it safe:
                    if (aaId >= ords.length) {
                        ensureCapacityExclusive(aaId + 1);
                        ords = ordsByAlliance;
                        counts = countsByAlliance;
                        if (ords[aaId] == null) {
                            // extremely rare: aaId appeared only in pass2 due to changing Conflict impl;
                            // treat as "new active alliance"
                            ords[aaId] = new int[4];
                            recordActiveAlliance(aaId);
                        }
                    }

                    ords[aaId][counts[aaId]++] = ord;
                }
            }

            // sort per alliance (needed for merge-intersection)
            for (int i = 0; i < activeAllianceCount; i++) {
                final int aaId = activeAllianceIds[i];
                final int[] arr = ords[aaId];
                if (arr != null && arr.length > 1) it.unimi.dsi.fastutil.ints.IntArrays.quickSort(arr);
            }
        }
    }

    private static final class TurnSnapshotHolder {
        private final Supplier<Conflict[]> conflictArrSup;

        private final TurnSnapshot snapshot = new TurnSnapshot();
        private int conflictVersion;

        TurnSnapshotHolder(Supplier<Conflict[]> conflictArrSupplier) {
            this.conflictArrSup = Objects.requireNonNull(conflictArrSupplier);
        }

        synchronized void invalidateConflictSnapshot() {
            // Do under your “conflicts update” lock if multiple writers exist
            conflictVersion++;
            snapshot.invalidate();
        }

        TurnSnapshot snapshotForTurn(long turn) {
            final int v = conflictVersion;
            if (snapshot.turn == turn && snapshot.version == v) return snapshot;

            snapshot.rebuild(turn, v, conflictArrSup.get());
            return snapshot;
        }
    }

    public void invalidateConflictSnapshot() {
        synchronized (GLOBAL_TURN_SNAPSHOT) {
            GLOBAL_TURN_SNAPSHOT.invalidateConflictSnapshot();
        }
    }

    private boolean applyConflicts(
            TurnSnapshotHolder holder,
            IntPredicate allowed, long turn,
            int allianceId1, int allianceId2,
            Consumer<Conflict> consumer
    ) {
        if (allianceId1 == 0 || allianceId2 == 0) return false;

        final TurnSnapshot snap = holder.snapshotForTurn(turn);
        final int[][] ordsByAlliance = snap.ordsByAlliance;

        if (allianceId1 >= ordsByAlliance.length || allianceId2 >= ordsByAlliance.length) return false;

        final int[] a = ordsByAlliance[allianceId1];
        if (a == null) return false;

        final int[] b = ordsByAlliance[allianceId2];
        if (b == null) return false;

        final Conflict[] conflicts = snap.conflicts;

        int i = 0, j = 0;
        boolean applied = false;

        while (i < a.length && j < b.length) {
            final int oa = a[i];
            final int ob = b[j];

            if (oa < ob) { i++; continue; }
            if (oa > ob) { j++; continue; }

            i++; j++;

            if (!allowed.test(oa)) continue;

            final Conflict c = conflicts[oa];
            if (c != null) {
                consumer.accept(c);
                applied = true;
            }
        }

        return applied;
    }

    public boolean updateWar(DBWar previous, DBWar current, IntPredicate allowedConflictords) {
        synchronized (GLOBAL_TURN_SNAPSHOT) {
            return updateWar(GLOBAL_TURN_SNAPSHOT, previous, current, allowedConflictords);
        }
    }

    private boolean updateWar(TurnSnapshotHolder holder, DBWar previous, DBWar current, IntPredicate allowedConflictords) {
        long date = current.getDate();
        long turn = TimeUtil.getTurn(date);
        return applyConflicts(holder, allowedConflictords, turn, current.getAttacker_aa(), current.getDefender_aa(), f -> f.updateWar(previous, current, turn, date));
    }

    private final Function<IAttack, AttackTypeSubCategory> onAttackFunction = f -> {
        AttackTypeSubCategory cat = f.getSubCategory(DBNation::getActive_m);
        saveSubTypes(Map.of(f.getWar_attack_id(), cat == null ? -1 : (byte) cat.ordinal()));
        return cat;
    };

    @Subscribe
    public void onAttack(AttackEvent event) {
        AbstractCursor attack = event.getAttack();
        DBWar war = event.getWar();
        if (war != null) {
            synchronized (GLOBAL_TURN_SNAPSHOT) {
                updateAttack(GLOBAL_TURN_SNAPSHOT, war, attack, ALWAYS_TRUE_INT, onAttackFunction);
            }
        }
    }

    public void updateAttack(TurnSnapshotHolder holder, DBWar war, AbstractCursor attack, IntPredicate allowed, Function<IAttack, AttackTypeSubCategory> getCached) {
        long turn = TimeUtil.getTurn(war.getDate());
        initTurn();
        applyConflicts(holder, allowed, turn, war.getAttacker_aa(), war.getDefender_aa(), f -> f.updateAttack(war, attack, turn, getCached));
    }

    @Subscribe
    public void onTurnChange(TurnChangeEvent event) {
        initTurn();
        long turn = event.getCurrent();
        List<Conflict> active = getActiveConflicts();
        synchronized (loadConflictLock) {
            long now = System.currentTimeMillis();
            if (!active.isEmpty()) {
                for (Conflict conflict : active) {
                    conflict.getSide1().get().updateTurnChange(this, turn, true);
                    conflict.getSide2().get().updateTurnChange(this, turn, true);
                }
                for (Conflict conflict : active) {
                    conflict.pushChanges(this, null, true, false, false, true, false, now);
                }
                pushIndex(now);
            }
            for (Conflict conflict : conflictArr) {
                conflict.tryUnload();
            }
        }
    }

    private void recreateConflictsByAlliance() {
        synchronized (activeConflictOrdByAllianceId) {
            activeConflictOrdByAllianceId.clear();
            for (int ord : activeConflictsOrd) {
                addConflictsByAlliance(conflictArr[ord], false);
            }
        }
    }

    private void addConflictsByAlliance(Conflict conflict, boolean removeOld) {
        if (conflict == null) return;
        synchronized (activeConflictOrdByAllianceId) {
            if (removeOld) {
                ObjectIterator<Int2ObjectMap.Entry<IntSet>> iter = activeConflictOrdByAllianceId.int2ObjectEntrySet().iterator();
                while (iter.hasNext()) {
                    Int2ObjectMap.Entry<IntSet> entry = iter.next();
                    entry.getValue().remove(conflict.getOrdinal());
                    if (entry.getValue().isEmpty()) {
                        iter.remove();
                    }
                }
            }
            if (conflict.isActive()) {
                for (int aaId : conflict.getAllianceIds()) {
                    activeConflictOrdByAllianceId.computeIfAbsent(aaId, k -> new IntArraySet()).add(conflict.getOrdinal());
                }
            }
        }
    }

    private void loadConflictMeta(Collection<Conflict> conflicts, boolean isStartup, boolean initWars) {
        Set<Integer> missingMeta = new IntLinkedOpenHashSet();
        Set<Integer> missingParticipants = new IntLinkedOpenHashSet();
        Set<Integer> missingAnnouncements = new IntLinkedOpenHashSet();

        List<Integer> conflictIds = new IntArrayList();
        for (Conflict conflict : conflicts) {
            conflictIds.add(conflict.getId());
        }
        Collections.sort(conflictIds);

        for (Conflict conflict : conflicts) {
            if (!conflict.hasMetaLoaded()) missingMeta.add(conflict.getId());
            if (!conflict.hasParticipantsLoaded()) missingParticipants.add(conflict.getId());
            if (!conflict.hasAnnouncementsLoaded()) missingAnnouncements.add(conflict.getId());
            conflict.setLoaded(true, initWars);
        }

        Function<Collection<Integer>, String> toInClause = ids -> ids.size() == 1 ? " = " + ids.iterator().next() : " IN " + StringMan.getString(ids);

        if (!missingMeta.isEmpty()) {
            String inClause = toInClause.apply(missingMeta);
            String query = "SELECT * FROM conflicts WHERE id " + inClause;
            db.query(query, stmt -> {
            }, (ThrowingConsumer<ResultSet>) rs -> {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    if (!conflictIds.contains(id)) continue;
                    Conflict conflict = conflictById.get(id);
                    if (conflict != null) {
                        conflict.setLoaded(rs);
                    }
                }
            });
        }

        if (!missingParticipants.isEmpty() || isStartup) {
            String inClause = toInClause.apply(isStartup ? conflictIds : missingParticipants);
            if (isStartup) System.out.println("Loading conflict participants for conflicts: " + conflictIds);
            db.query("SELECT * FROM conflict_participant WHERE conflict_id " + inClause, stmt -> {
            }, (ThrowingConsumer<ResultSet>) rs -> {
                while (rs.next()) {
                    int conflictId = rs.getInt("conflict_id");
                    Conflict conflict = conflictById.get(conflictId);
                    if (conflict == null) continue;
                    int allianceId = rs.getInt("alliance_id");
                    if (isStartup && !missingParticipants.contains(conflictId)) {
                        // Dont initialize non active during startup, just add it to the global alliance set for caching
                        continue;
                    }

                    boolean side = rs.getBoolean("side");
                    long startTurn = rs.getLong("start");
                    long endTurn = rs.getLong("end");
                    conflict.addParticipant(allianceId, side, false, true, startTurn, endTurn);
                }
            });
        }

        if (!missingAnnouncements.isEmpty()) {
            if (Locutus.imp().getForumDb() != null) {
                Map<Integer, Map<Integer, String>> conflictsByTopic = new Int2ObjectOpenHashMap<>();
                String inClause = toInClause.apply(missingAnnouncements);
                db.query("SELECT * FROM conflict_announcements2 WHERE conflict_id " + inClause, stmt -> {
                }, (ThrowingConsumer<ResultSet>) rs -> {
                    while (rs.next()) {
                        int conflictId = rs.getInt("conflict_id");
                        String desc = rs.getString("description");
                        int topicId = rs.getInt("topic_id");
                        Conflict conflict = conflictById.get(conflictId);
                        if (conflict != null) {
                            conflictsByTopic.computeIfAbsent(topicId, k -> new Int2ObjectOpenHashMap<>()).put(conflictId, desc);
                        }
                    }
                });
                Map<Integer, DBTopic> topics = Locutus.imp().getForumDb().getTopics(conflictsByTopic.keySet());
                for (Map.Entry<Integer, Map<Integer, String>> entry : conflictsByTopic.entrySet()) {
                    DBTopic topic = topics.get(entry.getKey());
                    if (topic != null) {
                        for (Map.Entry<Integer, String> entry2 : entry.getValue().entrySet()) {
                            Conflict conflict = conflictById.get(entry2.getKey());
                            if (conflict != null) {
                                conflict.addAnnouncement(entry2.getValue(), topic, false, true);
                            }
                        }
                    }
                }
            }
        }
    }

    private void loadWarsFromQueryAndProcess(Conflict conflict, boolean clearBeforeUpdate, AttackQuery query) {
        if (clearBeforeUpdate) {
            conflict.clearWarData();
        }
        initTurn();
        Set<DBWar> wars = new ObjectOpenHashSet<>();
        for (DBWar war : query.wars) {
            long date = war.getDate();
            if (conflict.updateWar(null, war, TimeUtil.getTurn(date), date)) {
                wars.add(war);
            }
        }
        if (!wars.isEmpty()) {
            db.iterateWarAttacks(wars, Predicates.alwaysTrue(), Predicates.alwaysTrue(), (war, attack) -> {
                long turn = TimeUtil.getTurn(attack.getDate());
                if (TimeUtil.getTurn(war.getDate()) <= turn) {
                    conflict.updateAttack(war, attack, turn, f -> null);
                }
            });
        }
    }

    public void loadVirtualConflict(Conflict conflict, boolean clearBeforeUpdate) {
        long start = TimeUtil.getTimeFromTurn(conflict.getStartTurn());
        long end = conflict.getEndTurn() == Long.MAX_VALUE ? Long.MAX_VALUE : TimeUtil.getTimeFromTurn(conflict.getEndTurn() + 60);

        Set<Integer> aaIds = conflict.getAllianceIds();

        AttackQuery query = db.queryAttacks()
                .withWarsForNationOrAlliance(null, aaIds::contains, f -> f.getDate() >= start && f.getDate() <= end);

        loadWarsFromQueryAndProcess(conflict, clearBeforeUpdate, query);
    }

    public void loadConflictWars(Conflict conflict, int allianceId, Long startOrNull, Long endOrNull) {
        long start = startOrNull == null ? TimeUtil.getTimeFromTurn(conflict.getStartTurn()) : startOrNull;
        long end = endOrNull == null ? (conflict.getEndTurn() == Long.MAX_VALUE ? Long.MAX_VALUE : TimeUtil.getTimeFromTurn(conflict.getEndTurn() + 60)) : endOrNull;

        AttackQuery query = db.queryAttacks()
                .withWarSet(f -> f.getWars(Set.of(allianceId), start, end));

        loadWarsFromQueryAndProcess(conflict, false, query);
    }

    private final Object loadConflictLock = new Object();

    public void ensureLoadedFully(Set<Conflict> conflicts) {
        synchronized (loadConflictLock) {
            List<Conflict> unloaded = new ObjectArrayList<>();
            for (Conflict conflict : conflicts) {
                if (conflict.isWarsLoaded()) continue;
                unloaded.add(conflict);
            }
            if (!unloaded.isEmpty()) {
                loadConflictWars(unloaded, false, false, true);
            }
        }
    }

    public void loadConflictWars(Collection<Conflict> conflicts2, boolean clearBeforeUpdate, boolean isStartup, boolean loadGraphData) {
        System.err.println("Loading " + conflicts2.size() + "  conflict wars: " + conflicts2.stream().map(f -> f.getName() + " | " + f.getId()).toList() + "\n" +
                StringMan.stacktraceToString(new Exception().getStackTrace()));
        Collection<Conflict> conflictsFinal;
        TurnSnapshotHolder holder;
        Conflict[] conflictsFinalArr;
        synchronized (conflictArrLock) {
            conflictsFinal = conflicts2 == null ? Arrays.asList(conflictArr) : conflicts2;
            if (conflictsFinal.isEmpty()) return;
            conflictsFinalArr = conflictsFinal.toArray(new Conflict[0]);
        }
        synchronized (loadConflictLock) {
            try {
                initTurn();
                holder = new TurnSnapshotHolder(() -> conflictsFinalArr);
                if (clearBeforeUpdate) {
                    for (Conflict conflict : conflicts2) {
                        conflict.clearWarData();
                    }
                }
                loadConflictMeta(conflictsFinal, isStartup, true);

                long startMs, endMs;
                IntPredicate allowedConflicts = f -> true;
                long startTurn = Long.MAX_VALUE;
                long endTurn = 0;
                for (Conflict conflict : conflictsFinal) {
                    startTurn = Math.min(startTurn, conflict.getStartTurn());
                    endTurn = Math.max(endTurn, conflict.getEndTurn());
                }
                if (endTurn == 0) return;
                startMs = TimeUtil.getTimeFromTurn(startTurn);
                endMs = endTurn == Long.MAX_VALUE ? Long.MAX_VALUE : TimeUtil.getTimeFromTurn(endTurn);

                Set<DBWar> wars = new ObjectOpenHashSet<>();
                for (DBWar war : this.db.getWars()) {
                    if (war.getDate() >= startMs && war.getDate() <= endMs) {
                        if (updateWar(holder, null, war, allowedConflicts)) {
//                        if (war.isActive() && TimeUtil.getTurn(war.getDate()) + 61 < currentTurn) {
//                            System.out.println("INVALID WAR EXPIRED " + war.getWarId() + " | " + war.getDate() + " | " + war.getStatus());
//                        }
                            wars.add(war);
                        }
                    }
                }

                Set<DBWar> manualWars = loadManualWars(conflictsFinal);
                for (DBWar war : manualWars) {
                    if (updateWar(holder, null, war, allowedConflicts)) {
                        wars.add(war);
                    }
                }

                if (!wars.isEmpty()) {
                    Map<Integer, Byte> subTypes = loadSubTypes();
                    Map<Integer, Byte> newSubTypes = new Int2ByteOpenHashMap();
                    BiFunction<DBNation, Long, Integer> activityCache = new BiFunction<>() {
                        private Map<Integer, Set<Long>> activity;

                        @Override
                        public Integer apply(DBNation nation, Long dateMs) {
                            if (activity == null) {
                                activity = Locutus.imp().getNationDB().getActivityByDay(startMs - TimeUnit.DAYS.toMillis(10), endMs);
                            }
                            Set<Long> natAct = activity.get(nation.getId());
                            if (natAct == null) return Integer.MAX_VALUE;
                            long currDay = TimeUtil.getDay(dateMs);
                            for (long day = currDay; day >= currDay - 10; day--) {
                                if (natAct.contains(day)) {
                                    return (int) (TimeUnit.DAYS.toMinutes((int) (currDay - day)));
                                }
                            }
                            return 20000;
                        }
                    };
                    Function<IAttack, AttackTypeSubCategory> onAttack = a -> {
                        int id = a.getWar_attack_id();
                        Byte cached = subTypes.get(id);
                        if (cached != null) {
                            return cached == -1 ? null : AttackTypeSubCategory.values[cached];
                        }
                        AttackTypeSubCategory sub = a.getSubCategory(activityCache);
                        newSubTypes.put(id, sub == null ? -1 : (byte) sub.ordinal());
                        return sub;
                    };
                    db.iterateWarAttacks(wars, Predicates.alwaysTrue(), Predicates.alwaysTrue(), (war, attack) -> {
                        if (TimeUtil.getTurn(war.getDate()) <= TimeUtil.getTurn(attack.getDate())) {
                            updateAttack(holder, war, attack, allowedConflicts, onAttack);
                        }
                    });
                    if (!newSubTypes.isEmpty()) {
                        saveSubTypes(newSubTypes);
                    }
                }

                if (loadGraphData) {
                    String whereClause;
                    if (conflictsFinal.size() == 1) {
                        whereClause = "WHERE conflict_id = " + conflictsFinal.iterator().next().getId();
                    } else {
                        List<Integer> idsSorted = new IntArrayList();
                        for (Conflict conflict : conflictsFinal) {
                            idsSorted.add(conflict.getId());
                        }
                        Collections.sort(idsSorted);
                        whereClause = "WHERE conflict_id IN " + StringMan.getString(idsSorted);
                    }
                    db.query("SELECT * FROM conflict_graphs2 " + whereClause, stmt -> {
                    }, (ThrowingConsumer<ResultSet>) rs -> {
                        while (rs.next()) {
                            int conflictId = rs.getInt("conflict_id");
                            boolean side = rs.getBoolean("side");
                            int allianceId = rs.getInt("alliance_id");
                            int metricOrd = rs.getInt("metric");
                            long turnOrDay = rs.getLong("turn");
                            int city = rs.getInt("city");
                            int value = rs.getInt("value");
                            Conflict conflict = conflictById.get(conflictId);
                            if (conflict != null) {
                                ConflictMetric metric = ConflictMetric.values[metricOrd];
                                conflict.getSide(side).get().addGraphData(metric, allianceId, turnOrDay, city, value);
                            }
                        }
                    });
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
    }

    private Set<DBWar> loadManualWars(Collection<Conflict> conflicts) {
        List<Integer> conflictIds = null;
        if (conflicts != null && conflicts.size() != conflictById.size()) {
            conflictIds = new ArrayList<>();
            for (Conflict conflict : conflicts) {
                conflictIds.add(conflict.getId());
            }
            conflictIds.sort(Integer::compareTo);
        }
        Set<DBWar> result = new ObjectOpenHashSet<>();
        String query = "SELECT * FROM MANUAL_WARS";
        if (conflictIds != null) {
            query += " WHERE conflict_id IN " + StringMan.getString(conflictIds);
        }
        db.query(query, stmt -> {
        }, (ThrowingConsumer<ResultSet>) rs -> {
            while (rs.next()) {
                int warId = rs.getInt("war_id");
                DBWar war = db.getWar(warId);
                if (war == null) continue;
                int allianceId = rs.getInt("alliance");
                DBWar copy = new DBWar(war);
                if (war.getAttacker_aa() == 0) copy.setAttacker_aa(allianceId);
                else if (war.getDefender_aa() == 0) copy.setDefender_aa(allianceId);
                result.add(copy);
            }
        });
        return result;
    }

    public void addManualWar(Conflict conflict, List<DBWar> wars, int allianceId) {
        String query = "INSERT INTO MANUAL_WARS (war_id, conflict_id, alliance) VALUES (?, ?, ?)";
        db.executeBatch(wars, query, (ThrowingBiConsumer<DBWar, PreparedStatement>) (war, stmt) -> {
            stmt.setInt(1, war.getWarId());
            stmt.setInt(2, conflict.getId());
            stmt.setInt(3, allianceId);
        });

    }

    public void saveDataCsvAllianceNames() throws IOException, ParseException {
        Locutus.imp().getDataDumper(true).load().iterateAll(Predicates.alwaysTrue(),
                (h, r) -> r.required(h.alliance_id, h.alliance),
                null,
                (day, r) -> {
                    int aaId = r.header.alliance_id.get();
                    if (aaId == 0) return;
                    String name = r.header.alliance.get();
                    if (name != null && !name.isEmpty()) {
                        long date = TimeUtil.getTimeFromDay(day);
                        addLegacyName(aaId, name, date);
                    }
                }, null, null);
    }

//    private void saveDefaultNames() {
//        Map<String, Integer> legacyIds = getDefaultNames();
//        for (Map.Entry<String, Integer> entry : legacyIds.entrySet()) {
//            addLegacyName(entry.getValue(), entry.getKey());
//        }
//    }

    public void setStatus(int conflictId, String status) {
        invalidateConflictRowCache(conflictId, HeaderGroup.INDEX_META, HeaderGroup.PAGE_META);
        db.update("UPDATE conflicts SET status = ? WHERE id = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setString(1, status);
            stmt.setInt(2, conflictId);
        });
    }

    public void setCb(int conflictId, String cb) {
        invalidateConflictRowCache(conflictId, HeaderGroup.INDEX_META, HeaderGroup.PAGE_META);
        db.update("UPDATE conflicts SET cb = ? WHERE id = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setString(1, cb);
            stmt.setInt(2, conflictId);
        });
    }

    private Map<Integer, Byte> loadSubTypes() {
        Map<Integer, Byte> subTypes = new Int2ByteOpenHashMap();
        db.query("SELECT * FROM attack_subtypes", stmt -> {
        }, (ThrowingConsumer<ResultSet>) rs -> {
            while (rs.next()) {
                subTypes.put(rs.getInt("attack_id"), rs.getByte("subtype"));
            }
        });
        return subTypes;
    }

    public void saveSubTypes(Map<Integer, Byte> subTypes) {
        String query = "INSERT OR REPLACE INTO attack_subtypes (attack_id, subtype) VALUES (?, ?)";
        db.executeBatch(subTypes.entrySet(), query, (ThrowingBiConsumer<Map.Entry<Integer, Byte>, PreparedStatement>) (entry, stmt) -> {
            stmt.setInt(1, entry.getKey());
            stmt.setByte(2, entry.getValue());
        });
    }

    public Map<Long, List<Long>> getSourceSets() {
        Map<Long, List<Long>> sourceSets = new Long2ObjectOpenHashMap<>();
        db.query("SELECT * FROM source_sets", stmt -> {
        }, (ThrowingConsumer<ResultSet>) rs -> {
            while (rs.next()) {
                long guildId = rs.getLong("guild");
                long sourceId = rs.getLong("source_id");
                sourceSets.computeIfAbsent(guildId, f -> new LongArrayList()).add(sourceId);
            }
        });
        return sourceSets;
    }

    private Map<String, List<Object>> getSourceSetStrings(Map<Long, List<Long>> sourceSets) {
        Map<String, List<Object>> sourceSetStrings = new LinkedHashMap<>();
        for (Map.Entry<Long, List<Long>> entry : sourceSets.entrySet()) {
            List<Object> sourceIdList = new ArrayList<>();
            for (Long sourceId : entry.getValue()) {
                if (sourceId > Integer.MAX_VALUE) {
                    sourceIdList.add(String.valueOf(sourceId));
                } else {
                    sourceIdList.add(sourceId);
                }
            }
            sourceSetStrings.put(String.valueOf(entry.getKey()), sourceIdList);
        }
        return sourceSetStrings;
    }

    private Map<String, String> getSourceNames(Set<Long> sourceIds) {
        Map<String, String> sourceNames = new LinkedHashMap<>();
        for (long id : sourceIds) {
            GuildDB guild = Locutus.imp().getGuildDB(id);
            if (guild != null) {
                sourceNames.put(String.valueOf(id), guild.getName());
            }
        }
        return sourceNames;
    }

    /**
     * type 0 = conflict
     * type 1 = guild
     * @param guild
     * @param sourceId
     * @param sourceType
     */
    public void addSource(long guild, long sourceId, int sourceType) {
        db.update("INSERT OR IGNORE INTO source_sets (guild, source_id, source_type) VALUES (?, ?, ?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setLong(1, guild);
            stmt.setLong(2, sourceId);
            stmt.setInt(3, sourceType);
        });
    }

    public void removeSource(long guild, long sourceId, int sourceType) {
        db.update("DELETE FROM source_sets WHERE guild = ? AND source_id = ? AND source_type = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setLong(1, guild);
            stmt.setLong(2, sourceId);
            stmt.setInt(3, sourceType);
        });
    }

    public void deleteAllSources(long guild) {
        db.update("DELETE FROM source_sets WHERE guild = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setLong(1, guild);
        });
    }

    public void addAnnouncement(int conflictId, int topicId, String description) {
        db.update("INSERT OR REPLACE INTO conflict_announcements2 (conflict_id, topic_id, description) VALUES (?, ?, ?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, conflictId);
            stmt.setInt(2, topicId);
            stmt.setString(3, description);
        });
        invalidateConflictRowCache(conflictId, HeaderGroup.PAGE_META);
    }

    public void flagGraphRecalc(int conflictId) {
        db.update("UPDATE conflicts SET " + RECALC_GRAPH + " = ? WHERE id = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setLong(1, System.currentTimeMillis());
            stmt.setInt(2, conflictId);
        });
    }

    public void clearGraphData(ConflictMetric metric, int conflictId, boolean side, long turn) {
        db.update("DELETE FROM conflict_graphs2 WHERE conflict_id = ? AND side = ? AND metric = ? AND turn = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, conflictId);
            stmt.setBoolean(2, side);
            stmt.setInt(3, metric.ordinal());
            stmt.setLong(4, turn);
        });
    }

    public void clearGraphData(Collection<ConflictMetric> metric, int conflictId, boolean side, long turn) {
        if (metric.isEmpty()) return;
        if (metric.size() == 1) {
            clearGraphData(metric.iterator().next(), conflictId, side, turn);
            return;
        }
        db.update("DELETE FROM conflict_graphs2 WHERE conflict_id = ? AND side = ? AND metric IN (" + metric.stream().map(Enum::ordinal).map(java.lang.String::valueOf).collect(Collectors.joining(",")) + ") AND turn = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, conflictId);
            stmt.setBoolean(2, side);
            stmt.setLong(3, turn);
        });
    }

    public void deleteGraphData(int conflictId) {
        db.update("DELETE FROM conflict_graphs2 WHERE conflict_id = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, conflictId);
        });
    }

    public void addGraphData(List<ConflictMetric.Entry> metrics) {
        String query = "INSERT OR REPLACE INTO conflict_graphs2 (conflict_id, side, alliance_id, metric, turn, city, value) VALUES (?, ?, ?, ?, ?, ?, ?)";
        db.executeBatch(metrics, query, (ThrowingBiConsumer<ConflictMetric.Entry, PreparedStatement>) (entry, stmt) -> {
            stmt.setInt(1, entry.conflictId());
            stmt.setBoolean(2, entry.side());
            stmt.setInt(3, entry.allianceId());
            stmt.setInt(4, entry.metric().ordinal());
            stmt.setLong(5, entry.turnOrDay());
            stmt.setInt(6, entry.city());
            stmt.setInt(7, entry.value());
        });
    }

    public void addLegacyName(int id, String name, long date) {
        String nameLower = name.toLowerCase(Locale.ROOT);
        Map<Long, Integer> byDate = legacyIdsByDate.computeIfAbsent(nameLower, f -> new Long2IntOpenHashMap());
        Integer otherId = null;
        Long lastDate = null;
        for (Map.Entry<Long, Integer> entry : byDate.entrySet()) {
            long otherDate = entry.getKey();
            if (otherDate <= date && (lastDate == null || otherDate > lastDate)) {
                lastDate = otherDate;
                otherId = entry.getValue();
            }
        }
        if (otherId == null || otherId != id) {
            byDate.put(date, id);
            synchronized (legacyNames2) {
                legacyNames2.putIfAbsent(id, name);
            }
            db.update("INSERT OR IGNORE INTO legacy_names2 (id, name, date) VALUES (?, ?, ?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
                stmt.setInt(1, id);
                stmt.setString(2, name);
                stmt.setLong(3, date);
            });
        }
    }

    public Conflict addConflict(String name, long creator, ConflictCategory category, String col1, String col2, String wiki, String cb, String status, long turnStart, long turnEnd) {
        String query = "INSERT INTO conflicts (" + StringMan.join(Arrays.asList(NAME, COL1, COL2, WIKI, START, END, CATEGORY, CB, STATUS, CREATOR), ",") + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = db.getConnection().prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, name);
            stmt.setString(2, col1);
            stmt.setString(3, col2);
            stmt.setString(4, wiki);
            stmt.setLong(5, turnStart);
            stmt.setLong(6, turnEnd);
            stmt.setInt(7, category.ordinal());
            stmt.setString(8, cb);
            stmt.setString(9, status);
            stmt.setLong(10, creator);
            stmt.executeUpdate();
            ResultSet rs = stmt.getGeneratedKeys();
            if (rs.next()) {
                int id = rs.getInt(1);
                Conflict conflict = new Conflict(id, conflictArr.length, name, turnStart, turnEnd, 0, 0, 0, true );
                conflict.setLoaded(col1, col2, creator, category, wiki, cb, status);
                conflictById.put(id, conflict);
                synchronized (conflictArrLock) {
                    Conflict[] tmp = Arrays.copyOf(conflictArr, conflictArr.length + 1);
                    tmp[tmp.length - 1] = conflict;
                    conflictArr = tmp;
                    GLOBAL_TURN_SNAPSHOT.invalidateConflictSnapshot();
                }

                synchronized (activeConflictsOrd) {
                    long turn = TimeUtil.getTurn();
                    if (turnEnd > turn) {
                        activeConflictsOrd.add(conflict.getOrdinal());
                        addConflictsByAlliance(conflict, false);
                    }
                }

                return conflict;
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    protected void updateConflict(Conflict conflict, long start, long end) {
        long oldStart = conflict.getStartTurn();
        long oldEnd = conflict.getEndTurn();

        boolean expandedRange = start < oldStart || (end > oldEnd && oldEnd < TimeUtil.getTurn());
        boolean narrowedRange = start > oldStart || (end < oldEnd && end <= TimeUtil.getTurn());
        List<HeaderGroup> toInvalidate = new ObjectArrayList<>(Arrays.asList(HeaderGroup.INDEX_META, HeaderGroup.PAGE_META, HeaderGroup.GRAPH_META));
        if (expandedRange) {
            toInvalidate.add(HeaderGroup.INDEX_STATS);
            toInvalidate.add(HeaderGroup.PAGE_STATS);
            toInvalidate.add(HeaderGroup.GRAPH_DATA);

            flagGraphRecalc(conflict.getId());
        }
        invalidateConflictRowCache(conflict.getId(), toInvalidate.toArray(new HeaderGroup[0]));

        int conflictOrd = conflict.getOrdinal();
        synchronized (activeConflictsOrd) {
            if (activeConflictsOrd.contains(conflictOrd)) {
                if (end <= TimeUtil.getTurn()) {
                    activeConflictsOrd.remove(conflictOrd);
                }
            } else if (!activeConflictsOrd.contains(conflictOrd) && end == Long.MAX_VALUE || end > TimeUtil.getTurn()) {
                activeConflictsOrd.add(conflictOrd);
            }
            addConflictsByAlliance(conflict, true);
        }
        db.update("UPDATE conflicts SET " + START + " = ?, " + END + " = ? WHERE id = ?",
                (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setLong(1, start);
            stmt.setLong(2, end);
            stmt.setInt(3, conflict.getId());
        });

        if (narrowedRange && !expandedRange) {
            db.update("DELETE FROM conflict_graphs2 WHERE conflict_id = ? AND (turn < ? OR turn > ?)",
                    (ThrowingConsumer<PreparedStatement>) stmt -> {
                        stmt.setInt(1, conflict.getId());
                        stmt.setLong(2, start);
                        stmt.setLong(3, end);
                    });
        }
    }

    public void updateConflictName(int conflictId, String name) {
        invalidateConflictRowCache(conflictId, HeaderGroup.INDEX_META, HeaderGroup.PAGE_META, HeaderGroup.GRAPH_META);
        db.update("UPDATE conflicts SET name = ? WHERE id = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setString(1, name);
            stmt.setInt(2, conflictId);
        });
    }

    public void updateConflictName(int conflictId, String name, boolean isPrimary) {
        invalidateConflictRowCache(conflictId, HeaderGroup.INDEX_META, HeaderGroup.PAGE_META, HeaderGroup.GRAPH_META);
        db.update("UPDATE conflicts SET `col" + (isPrimary ? "1" : "2") + "` = ? WHERE id = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setString(1, name);
            stmt.setInt(2, conflictId);
        });
    }


    public void updateConflictWiki(int conflictId, String wiki) {
        invalidateConflictRowCache(conflictId, HeaderGroup.INDEX_META, HeaderGroup.PAGE_META);
        db.update("UPDATE conflicts SET `wiki` = ? WHERE id = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setString(1, wiki);
            stmt.setInt(2, conflictId);
        });
    }

    protected void addParticipant(Conflict conflict, int allianceId, boolean side, long start, long end) {
        synchronized (conflictAlliances) {
            this.conflictAlliances.add(allianceId);
        }
        db.update("INSERT OR REPLACE INTO conflict_participant (conflict_id, alliance_id, side, start, end) VALUES (?, ?, ?, ?, ?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, conflict.getId());
            stmt.setInt(2, allianceId);
            stmt.setBoolean(3, side);
            stmt.setLong(4, start);
            stmt.setLong(5, end);
        });
        DBAlliance aa = DBAlliance.get(allianceId);
        if (aa != null) addLegacyName(allianceId, aa.getName(), System.currentTimeMillis());
        addConflictsByAlliance(conflict, true);

        invalidateConflictRowCache(conflict.getId(), HeaderGroup.values);
    }

    protected void removeParticipant(Conflict conflict, int allianceId) {
        db.update("DELETE FROM conflict_participant WHERE alliance_id = ? AND conflict_id = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, allianceId);
            stmt.setInt(2, conflict.getId());
        });
        addConflictsByAlliance(conflict, true);
        invalidateConflictRowCache(conflict.getId(), HeaderGroup.values);
    }

    public Map<Integer, Conflict> getConflictMap() {
        synchronized (conflictById) {
            return new Int2ObjectOpenHashMap<>(conflictById);
        }
    }

    public List<Conflict> getActiveConflicts() {
        return conflictById.values().stream().filter(Conflict::isActive).toList();
    }

    public Conflict getConflict(String conflictName) {
        for (Conflict conflict : getConflictMap().values()) {
            if (conflict.getName().equalsIgnoreCase(conflictName)) {
                return conflict;
            }
        }
        return null;
    }

    public Integer getAllianceId(String name, long date, boolean parseInt) {
        Integer id = getAllianceId(name, date);
        if (id == null && parseInt && MathMan.isInteger(name)) {
            id = Integer.parseInt(name);
        }
        return id;
    }

    public Integer getAllianceId(String name, long date) {
        String nameLower = name.toLowerCase(Locale.ROOT);
        synchronized (legacyIdsByDate) {
            Map<Long, Integer> idsByDate = legacyIdsByDate.get(nameLower);
            if (idsByDate != null && !idsByDate.isEmpty()) {
                if (idsByDate.size() == 1) {
                    return idsByDate.values().iterator().next();
                }
                Long lastDate = null;
                Integer lastId = null;
                for (Map.Entry<Long, Integer> entry : idsByDate.entrySet()) {
                    long otherDate = entry.getKey();
                    if (lastDate == null || (otherDate <= date && otherDate > lastDate)) {
                        lastDate = otherDate;
                        lastId = entry.getValue();
                    }
                }
                return lastId;
            }
        }
        DBAlliance alliance = DBAlliance.parse(name, false);
        if (alliance != null) {
            addLegacyName(alliance.getId(), name, 0);
            return alliance.getId();
        }
        return null;
    }

    public String getAllianceName(int id) {
        String name = getAllianceNameOrNull(id);
        if (name == null) name = "AA:" + id;
        return name;
    }

    public String getAllianceNameOrNull(int id) {
        DBAlliance alliance = DBAlliance.get(id);
        if (alliance != null) return alliance.getName();
        String name;
        synchronized (legacyNames2) {
            name = legacyNames2.get(id);
        }
        return name;
    }

    public void deleteConflict(Conflict conflict) {
        invalidateConflictRowCache(conflict.getId(), HeaderGroup.values);
        synchronized (activeConflictsOrd) {
            synchronized (conflictById) {
                if (conflictById.remove(conflict.getId()) != null) {
                    List<Conflict> conflictList = new ArrayList<>(Arrays.asList(conflictArr));
                    conflictList.remove(conflict);
                    conflictArr = conflictList.toArray(new Conflict[0]);
                    activeConflictsOrd.remove(conflict.getOrdinal());
                    recreateConflictsByAlliance();
                    GLOBAL_TURN_SNAPSHOT.invalidateConflictSnapshot();
                }
            }
        }
        db.update("DELETE FROM conflicts WHERE id = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, conflict.getId());
        });
        db.update("DELETE FROM conflict_participant WHERE conflict_id = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, conflict.getId());
        });
        // delete cache
        invalidateConflictRowCache(conflict.getId(), HeaderGroup.values);
    }

    public Conflict getConflictById(int id) {
        synchronized (conflictById) {
            return conflictById.get(id);
        }
    }

    public Set<String> getConflictNames() {
        Set<String> names = new ObjectOpenHashSet<>();
        for (Conflict conflict : conflictArr) {
            names.add(conflict.getName());
        }
        return names;
    }

    private static final HeaderGroup[] INDEX_GROUPS = { HeaderGroup.INDEX_META, HeaderGroup.INDEX_STATS };
    private static final Set<HeaderGroup> INDEX_GROUP_SET = EnumSet.of(HeaderGroup.INDEX_META, HeaderGroup.INDEX_STATS);
    private static final List<String> ALL_HEADERS = Stream.of(HeaderGroup.values()).flatMap(g -> g.getHeaders().stream()).toList();

    public byte[] getIndexBytesZip(long time) {
        try {
            synchronized (this.loadConflictLock) {
                Map<Integer, String> aaNameById = new Int2ObjectOpenHashMap<>();
                synchronized (conflictAlliances) {
                    for (int allianceId : conflictAlliances) {
                        String name = getAllianceNameOrNull(allianceId);
                        if (name != null) aaNameById.put(allianceId, name);
                    }
                }

                List<Integer> allianceIds = new ArrayList<>(aaNameById.keySet());
                Collections.sort(allianceIds);
                List<String> aaNames = allianceIds.stream().map(aaNameById::get).toList();

                Map<Long, List<Long>> sourceSets = getSourceSets();
                ObjectMapper mapper = JteUtil.getSerializer();
                Map<Integer, Conflict> conflicts = getConflictMap();

                List<List<Object>> conflictRows = new ArrayList<>(conflicts.size());

                for (Conflict c : conflicts.values()) {
                    int id = c.getId();
                    boolean cacheable = id > 0;
                    Map<HeaderGroup, Map.Entry<Long, byte[]>> cachedByGroup =
                            cacheable ? loadConflictRowCache(id, INDEX_GROUP_SET) : Map.of();

                    List<Object> row = new ObjectArrayList<>(ALL_HEADERS.size());

                    for (HeaderGroup group : INDEX_GROUPS) {
                        Map.Entry<Long, byte[]> cached = cachedByGroup.get(group);
                        if (cached != null) {
                            List<Object> cachedValues = mapper.readValue(
                                    cached.getValue(),
                                    mapper.getTypeFactory().constructCollectionType(List.class, Object.class)
                            );
                            row.addAll(cachedValues);
                            continue;
                        }

                        Map<String, Object> groupMap = group.write(this, c);
                        List<Object> values = new ArrayList<>(groupMap.values());
                        row.addAll(values);

                        if (cacheable) {
                            byte[] part = writeMsgpackBytes(mapper, values);
                            saveConflictRowCache(id, part, group, time);
                        }
                    }

                    conflictRows.add(row);
                }

                Map<String, Object> result = new Object2ObjectLinkedOpenHashMap<>();
                result.put("headers",       ALL_HEADERS);
                result.put("conflicts",     conflictRows);
                result.put("alliance_ids",  allianceIds);
                result.put("alliance_names", aaNames);
                result.put("source_sets",   getSourceSetStrings(sourceSets));
                result.put("source_names",  getSourceNames(sourceSets.keySet()));

                ByteArrayOutputStream baos = new ByteArrayOutputStream(64 * 1024);
                mapper.writeValue(baos, result);
                return JteUtil.compress(baos.toByteArray());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private long ensureConflictHeaderHash(List<String> headers, HeaderGroup group) {
        if (headers == null) throw new IllegalArgumentException("headers cannot be null");
        long computed = StringMan.hash(headers);

        if (!conflictHeaderHashInitialized || conflictHeaderHash != computed) {
            synchronized (this) {
                if (!conflictHeaderHashInitialized || conflictHeaderHash != computed) {
                    conflictHeaderHashInitialized = true;
                    long previous = conflictHeaderHash;
                    conflictHeaderHash = computed;

                    if (previous != computed) {
                        // remove in-memory cache times for this group
                        synchronized (cacheTimes) {
                            Iterator<Map.Entry<Integer, Map<HeaderGroup, Long>>> iter = cacheTimes.entrySet().iterator();
                            while (iter.hasNext()) {
                                Map<HeaderGroup, Long> m = iter.next().getValue();
                                if (m != null) {
                                    m.remove(group);
                                    if (m.isEmpty()) {
                                        iter.remove();
                                    }
                                }
                            }
                        }
                        db.update("DELETE FROM conflict_row_cache WHERE header_hash != ? AND type = ?",
                                (ThrowingConsumer<PreparedStatement>) stmt -> {
                                    stmt.setLong(1, computed);
                                    stmt.setInt(2, group.ordinal());
                                });
                    }
                }
            }
        }
        return conflictHeaderHash;
    }

    public Map<HeaderGroup, Map.Entry<Long, byte[]>> loadConflictRowCache(int conflictId, Set<HeaderGroup> groups) {
        synchronized (cacheTimes) {
            Map<HeaderGroup, Long> currentCacheTimes = cacheTimes.get(conflictId);

            Map<HeaderGroup, Map.Entry<Long, byte[]>> result = new Object2ObjectLinkedOpenHashMap<>();
            // Determine which types the caller wants to check
            Set<HeaderGroup> typesToCheck = new LinkedHashSet<>(groups);

            // If we have no cache times, or no requested types, nothing to fetch
            if (currentCacheTimes == null || typesToCheck.isEmpty()) {
                return result;
            }

            // Only fetch the types we know exist in the DB based on currentCacheTimes
            typesToCheck.retainAll(currentCacheTimes.keySet());
            if (typesToCheck.isEmpty()) {
                return result;
            }

            List<Integer> typeOrds = typesToCheck.stream().map(Enum::ordinal).sorted().collect(Collectors.toList());
            List<Integer> staleTypes = new IntArrayList();

            String typeInOrEqual = typeOrds.size() == 1 ? "= ?" : "IN " + StringMan.getString(typeOrds);
            String sql = "SELECT header_hash, row_data, type, updated_ms FROM conflict_row_cache WHERE conflict_id = ? AND type " + typeInOrEqual;

            db.query(sql,
                    (ThrowingConsumer<PreparedStatement>) stmt -> {
                        stmt.setInt(1, conflictId);
                        if (typeOrds.size() == 1) {
                            stmt.setInt(2, typeOrds.get(0));
                        }
                    },
                    (ThrowingConsumer<ResultSet>) rs -> {
                        while (rs.next()) {
                            int typeOrd = rs.getInt("type");
                            HeaderGroup type = HeaderGroup.values[typeOrd];

                            // Ensure the DB row matches our in-memory updated time
                            long updatedMs = rs.getLong("updated_ms");
                            Long expectedUpdated = currentCacheTimes.get(type);
                            if (expectedUpdated == null || expectedUpdated.longValue() != updatedMs) {
                                staleTypes.add(typeOrd);
                                continue;
                            }

                            long storedHash = rs.getLong("header_hash");
                            long expectedHash = type.getHash();
                            if (storedHash == expectedHash) {
                                byte[] data = rs.getBytes("row_data");
                                result.put(type, Map.entry(updatedMs, data));
                            } else {
                                staleTypes.add(typeOrd);
                            }
                        }
                    });

            if (!staleTypes.isEmpty()) {
                db.executeBatch(staleTypes, "DELETE FROM conflict_row_cache WHERE conflict_id = ? AND type = ?",
                        (ThrowingBiConsumer<Integer, PreparedStatement>) (typeOrd, stmt) -> {
                            stmt.setInt(1, conflictId);
                            stmt.setInt(2, typeOrd);
                        });

                Map<HeaderGroup, Long> m = cacheTimes.get(conflictId);
                if (m != null) {
                    for (int typeOrd : staleTypes) {
                        m.remove(HeaderGroup.values[typeOrd]);
                    }
                    if (m.isEmpty()) {
                        cacheTimes.remove(conflictId);
                    }
                }
            }
            return result;
        }
    }

    public void saveConflictRowCache(int conflictId, byte[] rowData, HeaderGroup type, long time) {
        if (rowData == null || type == null) return;
        db.update("INSERT OR REPLACE INTO conflict_row_cache (conflict_id, header_hash, row_data, type, updated_ms) VALUES (?, ?, ?, ?, ?)",
        (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, conflictId);
            stmt.setLong(2, type.getHash());
            stmt.setBytes(3, rowData);
            stmt.setInt(4, type.ordinal());
            stmt.setLong(5, time);
        });
        synchronized (cacheTimes) {
            cacheTimes.computeIfAbsent(conflictId, f -> new EnumMap<>(HeaderGroup.class)).put(type, time);
        }
    }

//    public void invalidateConflictRowCache(int conflictId) {
//        db.update("DELETE FROM conflict_row_cache WHERE conflict_id = ?",
//                (ThrowingConsumer<PreparedStatement>) stmt -> stmt.setInt(1, conflictId));
//    }

    public void invalidateConflictRowCache(int conflictId, HeaderGroup... types) {
        if (types.length == 0) {
             throw new IllegalArgumentException("At least one HeaderGroup type must be specified");
        }
        String typeCheck;
        if (types == HeaderGroup.values) {
            typeCheck = "";
        } else {
            String inOrEqual = types.length == 1 ? "= ?" : "IN " + StringMan.getString(Arrays.stream(types).map(Enum::ordinal).map(String::valueOf).toList());
            typeCheck = " AND type " + inOrEqual;
        }
        db.update("DELETE FROM conflict_row_cache WHERE conflict_id = ?" + typeCheck,
                (ThrowingConsumer<PreparedStatement>) stmt -> {
                    stmt.setInt(1, conflictId);
        });
        synchronized (cacheTimes) {
            Map<HeaderGroup, Long> m = cacheTimes.get(conflictId);
            if (m != null) {
                for (HeaderGroup type : types) {
                    m.remove(type);
                }
                if (m.isEmpty()) {
                    cacheTimes.remove(conflictId);
                }
            }
        }
    }

    public Map.Entry<String, Double> getMostSimilar(String name) {
        double distance = Integer.MAX_VALUE;
        String similar = null;
        for (Map.Entry<Integer, String> entry : legacyNames2.entrySet()) {
            double d = StringMan.distanceWeightedQwertSift4(name, entry.getValue());
            if (d < distance) {
                distance = d;
                similar = entry.getValue();
            }
        }
        return distance == Integer.MAX_VALUE ? null : KeyValue.of(similar, distance);
    }

    public void updateConflictCategory(int conflictId, ConflictCategory category) {
        invalidateConflictRowCache(conflictId, HeaderGroup.INDEX_META, HeaderGroup.PAGE_META);
        db.update("UPDATE conflicts SET category = ? WHERE id = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, category.ordinal());
            stmt.setInt(2, conflictId);
        });
    }

    public CloudStorage getCloud() {
        return aws;
    }

    public void removeAnnouncement(int id, int topicId) {
        db.update("DELETE FROM conflict_announcements2 WHERE conflict_id = ? AND topic_id = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, id);
            stmt.setInt(2, topicId);
        });
        invalidateConflictRowCache(id, HeaderGroup.PAGE_META);
    }

    public Object getConflictField(int conflictId, ConflictField field) {
        return db.select("SELECT " + field.toString() + " FROM conflicts WHERE id = ?", (ThrowingConsumer<PreparedStatement>) stmt -> stmt.setInt(1, conflictId), (ThrowingFunction<ResultSet, Object>) rs -> {
            if (rs.next()) {
                return rs.getObject(1);
            }
            return null;
        });

    }

    public void loadParticipantStartEndTimes(int id, Map<Integer, Long> startTimes, Map<Integer, Long> endTimes, CoalitionSide coalition_1, CoalitionSide coalition_2) {
        String sideStr = coalition_1 != null || coalition_2 != null ? ", side" : "";
        db.query("SELECT alliance_id, start, end" + (sideStr) + " FROM conflict_participant WHERE conflict_id = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, id);
        }, (ThrowingConsumer<ResultSet>) rs -> {
            while (rs.next()) {
                int allianceId = rs.getInt("alliance_id");
                long start = rs.getLong("start");
                long end = rs.getLong("end");
                if (start != 0L) startTimes.put(allianceId, start);
                if (end != Long.MAX_VALUE) endTimes.put(allianceId, end);
                if (!sideStr.isEmpty()) {
                    boolean side = rs.getBoolean("side");
                    CoalitionSide col = side ? coalition_1 : coalition_2;
                    if (col != null) col.add(allianceId);
                }
            }
        });
    }

    public Map<String, DBTopic> loadAnnouncements(int conflictId) {
        Map<String, DBTopic> announcements = new Object2ObjectLinkedOpenHashMap<>();
        db.query("SELECT * FROM conflict_announcements2 WHERE conflict_id = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, conflictId);
        }, (ThrowingConsumer<ResultSet>) rs -> {
            while (rs.next()) {
                int topicId = rs.getInt("topic_id");
                String description = rs.getString("description");
                DBTopic topic = Locutus.imp().getForumDb().getTopic(topicId);
                if (topic != null) {
                    announcements.put(description, topic);
                }
            }
        });
        return announcements;
    }
}
