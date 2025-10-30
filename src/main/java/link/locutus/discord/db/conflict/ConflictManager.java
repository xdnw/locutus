package link.locutus.discord.db.conflict;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Predicates;
import com.google.common.eventbus.Subscribe;
import com.ptsmods.mysqlw.Database;
import it.unimi.dsi.fastutil.ints.Int2ByteOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import link.locutus.discord.Locutus;
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
import link.locutus.discord.web.jooby.AwsManager;
import link.locutus.discord.web.jooby.JteUtil;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static link.locutus.discord.db.conflict.ConflictField.*;

public class ConflictManager {
    private final WarDB db;
    private final AwsManager aws;
    private boolean conflictsLoaded = false;

    private final Map<Integer, Conflict> conflictById = new Int2ObjectOpenHashMap<>();
    private Conflict[] conflictArr;
    private final Set<Integer> activeConflictsOrd = new IntOpenHashSet();
    private long lastTurn = 0;
    private final Map<Integer, Set<Integer>> activeConflictOrdByAllianceId = new Int2ObjectOpenHashMap<>();
    private final Map<Long, Map<Integer, int[]>> mapTurnAllianceConflictOrd = new Long2ObjectOpenHashMap<>();

    private final Map<Integer, String> legacyNames2 = new Int2ObjectOpenHashMap<>();
    private final Map<String, Map<Long, Integer>> legacyIdsByDate = new ConcurrentHashMap<>();

    private final Set<Integer> conflictAlliances = new IntOpenHashSet();

    private volatile boolean conflictHeaderHashInitialized = false;
    private volatile long conflictHeaderHash = 0L;

    public ConflictManager(WarDB db) {
        this.db = db;
        this.aws = setupAws();
    }

    public WarDB getDb() {
        return db;
    }

    private AwsManager setupAws() {
        String key = Settings.INSTANCE.WEB.S3.ACCESS_KEY;
        String secret = Settings.INSTANCE.WEB.S3.SECRET_ACCESS_KEY;
        String region = Settings.INSTANCE.WEB.S3.REGION;
        String bucket = Settings.INSTANCE.WEB.S3.BUCKET;
        if (!key.isEmpty() && !secret.isEmpty() && !region.isEmpty() && !bucket.isEmpty()) {
            return new AwsManager(key, secret, bucket, region);
        }
        return null;
    }

    public void createTables() {

        db.executeStmt("CREATE TABLE IF NOT EXISTS conflict_announcements2 (conflict_id INTEGER NOT NULL, topic_id INTEGER NOT NULL, description VARCHAR NOT NULL, PRIMARY KEY (conflict_id, topic_id), FOREIGN KEY(conflict_id) REFERENCES conflicts(id))");
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
                        CREATOR + " BIGINT NOT NULL"
                ) + ")";
        db.executeStmt(createConflicts);
        db.executeStmt("ALTER TABLE conflicts ADD COLUMN creator BIGINT DEFAULT 0", true);
        db.executeStmt("ALTER TABLE conflicts ADD COLUMN wiki VARCHAR DEFAULT ''", true);
        db.executeStmt("ALTER TABLE conflicts ADD COLUMN cb VARCHAR DEFAULT ''", true);
        db.executeStmt("ALTER TABLE conflicts ADD COLUMN status VARCHAR DEFAULT ''", true);
        db.executeStmt("ALTER TABLE conflicts ADD COLUMN category INTEGER DEFAULT 0", true);

        db.executeStmt("CREATE TABLE IF NOT EXISTS conflict_participant (conflict_id INTEGER NOT NULL, alliance_id INTEGER NOT NULL, side BOOLEAN, start BIGINT NOT NULL, end BIGINT NOT NULL, PRIMARY KEY (conflict_id, alliance_id), FOREIGN KEY(conflict_id) REFERENCES conflicts(id))");
        db.executeStmt("CREATE TABLE IF NOT EXISTS legacy_names2 (id INTEGER NOT NULL, name VARCHAR NOT NULL, date BIGINT DEFAULT 0, PRIMARY KEY (id, name, date))");

        db.executeStmt("CREATE TABLE IF NOT EXISTS conflict_graphs2 (conflict_id INTEGER NOT NULL, side BOOLEAN NOT NULL, alliance_id INT NOT NULL, metric INTEGER NOT NULL, turn BIGINT NOT NULL, city INTEGER NOT NULL, value INTEGER NOT NULL, PRIMARY KEY (conflict_id, alliance_id, metric, turn, city), FOREIGN KEY(conflict_id) REFERENCES conflicts(id))");

        db.executeStmt("CREATE TABLE IF NOT EXISTS source_sets (guild BIGINT NOT NULL, source_id BIGINT NOT NULL, source_type INT NOT NULL, PRIMARY KEY (guild, source_id, source_type))");

        // attack_subtypes attack id int primary key, subtype int not null
        db.executeStmt("CREATE TABLE IF NOT EXISTS attack_subtypes (attack_id INT PRIMARY KEY, subtype INT NOT NULL)");

        // create table if not exists MANUAL_WARS war_id, conflict_id, int alliance, primary key (war_id)
        db.executeStmt("CREATE TABLE IF NOT EXISTS MANUAL_WARS (war_id INT PRIMARY KEY, conflict_id INT NOT NULL, alliance INT NOT NULL)");

        db.executeStmt("CREATE TABLE IF NOT EXISTS conflict_flat_cache (" +
                "conflict_id INTEGER PRIMARY KEY, " +
                "flat_gzip BLOB, " +
                "graph_gzip BLOB, " +
                "updated_ms BIGINT NOT NULL DEFAULT 0" +
                ")");

        db.executeStmt("CREATE TABLE IF NOT EXISTS conflict_row_cache (" +
                "conflict_id INTEGER PRIMARY KEY, " +
                "header_hash BIGINT NOT NULL, " +
                "row_data BLOB NOT NULL, " +
                "FOREIGN KEY(conflict_id) REFERENCES conflicts(id))");
    }

    public byte[] loadFlatCacheGzip(int conflictId) {
        final byte[][] out = new byte[1][];
        db.query("SELECT flat_gzip FROM conflict_flat_cache WHERE conflict_id = ?",
                (ThrowingConsumer<PreparedStatement>)stmt -> stmt.setInt(1, conflictId),
                (ThrowingConsumer<ResultSet>)rs -> { if (rs.next()) out[0] = rs.getBytes(1); });
        return out[0];
    }
    public byte[] loadGraphCacheGzip(int conflictId) {
        final byte[][] out = new byte[1][];
        db.query("SELECT graph_gzip FROM conflict_flat_cache WHERE conflict_id = ?",
                (ThrowingConsumer<PreparedStatement>)stmt -> stmt.setInt(1, conflictId),
                (ThrowingConsumer<ResultSet>)rs -> { if (rs.next()) out[0] = rs.getBytes(1); });
        return out[0];
    }
    public void saveFlatGraphCache(int conflictId, byte[] flat, byte[] graph) {
        db.update("INSERT OR REPLACE INTO conflict_flat_cache (conflict_id, flat_gzip, graph_gzip, updated_ms) VALUES (?, ?, ?, ?)",
                (ThrowingConsumer<PreparedStatement>) stmt -> {
                    stmt.setInt(1, conflictId);
                    stmt.setBytes(2, flat);
                    stmt.setBytes(3, graph);
                    stmt.setLong(4, System.currentTimeMillis());
                });
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

    public String pushIndex() {
        String key = "conflicts/index.gzip";
        byte[] value = getPsonGzip();
        aws.putObject(key, value,  60);
        return aws.getLink(key);
    }

    public boolean pushDirtyConflicts() {
        boolean hasDirty = false;
        for (Conflict conflict : getActiveConflicts()) {
            if (conflict.isDirty()) {
                conflict.push(this, null, false, false);
                hasDirty = true;
            }
        }
        if (hasDirty) {
            pushIndex();
            return true;
        }
        return false;
    }

    private synchronized void initTurn() {
        long currTurn = TimeUtil.getTurn();
        if (lastTurn != currTurn) {
            Iterator<Integer> iter = activeConflictsOrd.iterator();
            activeConflictsOrd.removeIf(f -> {
                Conflict conflict = conflictArr[f];
                return (conflict == null || conflict.getEndTurn() <= currTurn);
            });
            recreateConflictsByAlliance();
            for (Conflict conflict : conflictArr) {
                long startTurn = Math.max(lastTurn + 1, conflict.getStartTurn());
                long endTurn = Math.min(currTurn + 1, conflict.getEndTurn());
                addAllianceTurn(conflict, startTurn, endTurn);
            }
            lastTurn = currTurn;
        }
    }

    private void addAllianceTurn(Conflict conflict, long startTurn, long endTurn) {
        if (startTurn >= endTurn) return;
        synchronized (mapTurnAllianceConflictOrd) {
            Set<Integer> aaIds = conflict.getAllianceIds();
            for (long turn = startTurn; turn < endTurn; turn++) {
                Map<Integer, int[]> conflictIdsByAA = mapTurnAllianceConflictOrd.computeIfAbsent(turn, k -> new Int2ObjectOpenHashMap<>());
                for (int aaId : aaIds) {
                    addAllianceTurn(conflict, aaId, turn, conflictIdsByAA);
                }
            }
        }
    }

    private void addAllianceTurn(Conflict conflict, int aaId, long turn,  Map<Integer, int[]> conflictIdsByAA) {
        if (conflict.getStartTurn(aaId) > turn) return;
        if (conflict.getEndTurn(aaId) <= turn) return;
        int[] currIds = conflictIdsByAA.get(aaId);
        if (currIds == null) {
            currIds = new int[]{conflict.getOrdinal()};
        } else {
            if (Arrays.binarySearch(currIds, conflict.getOrdinal()) >= 0) return;
            int[] newIds = new int[currIds.length + 1];
            System.arraycopy(currIds, 0, newIds, 0, currIds.length);
            newIds[currIds.length] = conflict.getOrdinal();
            Arrays.sort(newIds);
            currIds = newIds;
        }
        conflictIdsByAA.put(aaId, currIds);
    }

    private void addAllianceTurn(Conflict conflict, int aaId, long turn) {
        Map<Integer, int[]> conflictIdsByAA = mapTurnAllianceConflictOrd.computeIfAbsent(turn, k -> new Int2ObjectOpenHashMap<>());
        addAllianceTurn(conflict, aaId, turn, conflictIdsByAA);
    }

    public void clearAllianceCache() {
        synchronized (mapTurnAllianceConflictOrd) {
            mapTurnAllianceConflictOrd.clear();
            lastTurn = 0;
            recreateConflictsByAlliance();
        }
    }


    private boolean applyConflicts(Predicate<Integer> allowed, long turn, int allianceId1, int allianceId2, Consumer<Conflict> conflictConsumer) {
        if (allianceId1 == 0 || allianceId2 == 0) return false;
        synchronized (mapTurnAllianceConflictOrd)
        {
            Map<Integer, int[]> conflictOrdsByAA = mapTurnAllianceConflictOrd.get(turn);
            if (conflictOrdsByAA == null) return false;
            int[] conflictIds1 = conflictOrdsByAA.get(allianceId1);
            int[] conflictIds2 = conflictOrdsByAA.get(allianceId2);
            if (conflictIds1 != null && conflictIds2 != null) {
                if (conflictIds1.length == 1) {
                    int conflictId1 = conflictIds1[0];
                    if (conflictIds2.length == 1) {
                        if (conflictId1 == conflictIds2[0]) {
                            applyConflictConsumer(allowed, conflictIds1[0], conflictConsumer);
                            return true;
                        }
                    } else {
                        boolean result = false;
                        for (int conflictId : conflictIds2) {
                            if (conflictId == conflictId1) {
                                applyConflictConsumer(allowed, conflictId, conflictConsumer);
                                result = true;
                            }
                        }
                        return result;
                    }
                    return false;
                } else if (conflictIds2.length == 1) {
                    int conflictId2 = conflictIds2[0];
                    boolean result = false;
                    for (int conflictId : conflictIds1) {
                        if (conflictId == conflictId2) {
                            applyConflictConsumer(allowed, conflictId, conflictConsumer);
                            result = true;
                        }
                    }
                    return result;
                } else {
                    int i = 0, j = 0;
                    while (i < conflictIds1.length && j < conflictIds2.length) {
                        int id1 = conflictIds1[i];
                        int id2 = conflictIds2[j];
                        if (id1 < id2) {
                            i++;
                        } else if (id1 > id2) {
                            j++;
                        } else {
                            applyConflictConsumer(allowed, id1, conflictConsumer);
                            i++;
                            j++;
                        }
                    }
                }
            }
            return true;
        }
    }

    private void applyConflictConsumer(Predicate<Integer> allowedOrd, int conflictOrd, Consumer<Conflict> conflictConsumer) {
        if (allowedOrd.test(conflictOrd)) {
            Conflict conflict = conflictArr[conflictOrd];
            conflictConsumer.accept(conflict);
        }
    }

    public boolean updateWar(DBWar previous, DBWar current, Predicate<Integer> allowedConflictords) {
        long turn = TimeUtil.getTurn(current.getDate());
        if (turn > lastTurn) initTurn();
        return applyConflicts(allowedConflictords, turn, current.getAttacker_aa(), current.getDefender_aa(), f -> f.updateWar(previous, current, turn));
    }

    @Subscribe
    public void onAttack(AttackEvent event) {
        AbstractCursor attack = event.getAttack();
        DBWar war = event.getWar();
        if (war != null) {
            updateAttack(war, attack, Predicates.alwaysTrue(), f -> {
                AttackTypeSubCategory cat = f.getSubCategory(DBNation::getActive_m);
                saveSubTypes(Map.of(attack.getWar_attack_id(), cat == null ? -1 : (byte) cat.ordinal()));
                return cat;
            });
        }
    }

    public void updateAttack(DBWar war, AbstractCursor attack, Predicate<Integer> allowed, Function<IAttack, AttackTypeSubCategory> getCached) {
        long turn = TimeUtil.getTurn(war.getDate());
        if (turn > lastTurn) initTurn();
        applyConflicts(allowed, turn, war.getAttacker_aa(), war.getDefender_aa(), f -> f.updateAttack(war, attack, turn, getCached));
    }

    @Subscribe
    public void onTurnChange(TurnChangeEvent event) {
        if (!conflictsLoaded) return;
        long turn = event.getCurrent();
        List<Conflict> active = getActiveConflicts();
        if (!active.isEmpty()) {
            for (Conflict conflict : active) {
                conflict.getSide(true).updateTurnChange(this, turn, true);
                conflict.getSide(false).updateTurnChange(this, turn, true);
            }
            for (Conflict conflict : active) {
                conflict.push(this, null, true, false);
            }
            pushIndex();
        }
        for (Conflict conflict : conflictArr) {
            synchronized (loadConflictLock) {
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
                Iterator<Map.Entry<Integer, Set<Integer>>> iter = activeConflictOrdByAllianceId.entrySet().iterator();
                while (iter.hasNext()) {
                    Map.Entry<Integer, Set<Integer>> entry = iter.next();
                    entry.getValue().remove(conflict.getOrdinal());
                    if (entry.getValue().isEmpty()) {
                        iter.remove();
                    }
                }
                synchronized (mapTurnAllianceConflictOrd) {
                    Iterator<Map.Entry<Long, Map<Integer, int[]>>> iter2 = mapTurnAllianceConflictOrd.entrySet().iterator();
                    while (iter2.hasNext()) {
                        Map.Entry<Long, Map<Integer, int[]>> entry = iter2.next();
                        Iterator<Map.Entry<Integer, int[]>> iter3 = entry.getValue().entrySet().iterator();
                        while (iter3.hasNext()) {
                            Map.Entry<Integer, int[]> entry2 = iter3.next();
                            int[] value = entry2.getValue();
                            if (Arrays.binarySearch(value, conflict.getOrdinal()) >= 0) {
                                int[] newIds = new int[value.length - 1];
                                if (newIds.length == 0) {
                                    iter3.remove();
                                } else {
                                    for (int i = 0, j = 0; i < value.length; i++) {
                                        if (value[i] != conflict.getOrdinal()) {
                                            newIds[j++] = value[i];
                                        }
                                    }
                                    entry2.setValue(newIds);
                                }
                            }
                        }
                        if (entry.getValue().isEmpty()) {
                            iter2.remove();
                        }
                    }
                }
            }
            if (conflict.isActive()) {
                for (int aaId : conflict.getAllianceIds()) {
                    activeConflictOrdByAllianceId.computeIfAbsent(aaId, k -> new IntArraySet()).add(conflict.getOrdinal());
                }
            }
            addAllianceTurn(conflict, conflict.getStartTurn(), Math.min(TimeUtil.getTurn(), conflict.getEndTurn()));
        }
    }

    private void loadConflictParticipantsAndAnnouncements(List<Integer> conflictIds, boolean loadAllParticipants, boolean initInactive) {
        Collections.sort(conflictIds);
        String inClause = conflictIds.size() == 1 ? " = " + conflictIds.get(0) : " IN " + StringMan.getString(conflictIds);

        if (initInactive) {
            db.query("SELECT * FROM conflicts WHERE id " + inClause, stmt -> {
            }, (ThrowingConsumer<ResultSet>) rs -> {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    if (!conflictIds.contains(id)) continue;
                    Conflict conflict = conflictById.get(id);
                    if (conflict != null) {
                        conflict.initData(rs);
                    }
                }
            });
        }

        if (loadAllParticipants || !conflictIds.isEmpty()) {
            String whereClause = loadAllParticipants ? "" : " WHERE conflict_id " + inClause;
            db.query("SELECT * FROM conflict_participant" + whereClause, stmt -> {
            }, (ThrowingConsumer<ResultSet>) rs -> {
                while (rs.next()) {
                    int conflictId = rs.getInt("conflict_id");
                    Conflict conflict = conflictById.get(conflictId);
                    if (conflict == null) continue;
                    int allianceId = rs.getInt("alliance_id");
                    if (loadAllParticipants) conflictAlliances.add(allianceId);

                    Conflict.ConflictData data = conflict.getData(false);
                    if (data == null) continue;
                    boolean side = rs.getBoolean("side");
                    long startTurn = rs.getLong("start");
                    long endTurn = rs.getLong("end");
                    conflict.addParticipant(allianceId, side, false, true, startTurn, endTurn);
                }
            });
        }

        if (!conflictIds.isEmpty()) {
            if (Locutus.imp().getForumDb() != null) {
                Map<Integer, Map<Integer, String>> conflictsByTopic = new Int2ObjectOpenHashMap<>();
                db.query("SELECT * FROM conflict_announcements2 WHERE conflict_id in " + inClause, stmt -> {
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

    public void loadConflicts() {
        List<Conflict> conflicts = new ArrayList<>();
        List<Integer> activeConflictIds = new IntArrayList();
        conflictById.clear();
        db.query("SELECT * FROM conflicts", stmt -> {
        }, (ThrowingConsumer<ResultSet>) rs -> {
            int ordinal = 0;
            while (rs.next()) {
                int id = rs.getInt("id");
                String name = rs.getString("name");
                long startTurn = rs.getLong("start");
                long endTurn = rs.getLong("end");
                Conflict conflict = new Conflict(id, ordinal++, name, startTurn, endTurn);
                if (conflict.isActive()) {
                    conflict.initData(rs);
                    activeConflictIds.add(conflict.getId());
                }
                conflicts.add(conflict);
                conflictById.put(id, conflict);
            }
        });
        this.conflictArr = conflicts.toArray(new Conflict[0]);

        db.query("SELECT * FROM legacy_names2", stmt -> {
        }, (ThrowingConsumer<ResultSet>) rs -> {
            while (rs.next()) {
                int id = rs.getInt("id");
                String name = rs.getString("name");
                long date = rs.getLong("date");
                legacyNames2.put(id, name);
                String nameLower = name.toLowerCase(Locale.ROOT);
                legacyIdsByDate.computeIfAbsent(nameLower, k -> new Long2IntOpenHashMap()).put(date, id);
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

        loadConflictParticipantsAndAnnouncements(activeConflictIds, true, false);

        Locutus.imp().getExecutor().submit(() -> {
            List<Conflict> actives = getActiveConflicts();
            if (!actives.isEmpty()) {
                loadConflictWars(actives, false);
            }
            for (Conflict conflict : conflictArr) {
                if (!conflict.isActive()) {
                    synchronized (loadConflictLock) {
                        conflict.tryUnload();
                    }
                }
            }
            Locutus.imp().getRepeatingTasks().addTask("Conflict Website", () -> {
                if (!conflictsLoaded) return;
                pushDirtyConflicts();
            }, Settings.INSTANCE.TASKS.WAR_STATS_PUSH_INTERVAL, TimeUnit.MINUTES);
        });
    }

    public void loadVirtualConflict(Conflict conflict, boolean clearBeforeUpdate) {
        if (clearBeforeUpdate) {
            conflict.clearWarData();
        }
        long start = TimeUtil.getTimeFromTurn(conflict.getStartTurn());
        long end = conflict.getEndTurn() == Long.MAX_VALUE ? Long.MAX_VALUE : TimeUtil.getTimeFromTurn(conflict.getEndTurn() + 60);

        Set<Integer> aaIds = conflict.getAllianceIds();

        AttackQuery query = Locutus.imp().getWarDb().queryAttacks()
                .withWarsForNationOrAlliance(null, aaIds::contains, f -> f.getDate() >= start && f.getDate() <= end);
        Set<DBWar> wars = new ObjectOpenHashSet<>();
        for (DBWar war : query.wars) {
            if (conflict.updateWar(null, war, TimeUtil.getTurn(war.getDate()))) {
                wars.add(war);
            }
        }
        db.iterateWarAttacks(wars, Predicates.alwaysTrue(), Predicates.alwaysTrue(), (war, attack) -> {
            long turn = TimeUtil.getTurn(attack.getDate());
            if (TimeUtil.getTurn(war.getDate()) <= turn) {
                conflict.updateAttack(war, attack, turn, f -> null);
            }
        });
    }

    private final Object loadConflictLock = new Object();

    public void loadConflictWars(Collection<Conflict> conflicts2, boolean clearBeforeUpdate) {
        Collection<Conflict> conflictsFinal;
        synchronized (conflictArr) {
            conflictsFinal = conflicts2 == null ? Arrays.asList(conflictArr) : conflicts2;
        }
        if (conflictsFinal.isEmpty()) return;

        synchronized (loadConflictLock) {
            try {
                initTurn();
                if (clearBeforeUpdate) {
                    for (Conflict conflict : conflicts2) {
                        conflict.clearWarData();
                    }
                }
                // get the ids
                // load conflcit data of those
                {
                    List<Integer> ids = conflictsFinal.stream().map(Conflict::getId).collect(Collectors.toList());
                    loadConflictParticipantsAndAnnouncements(ids, false, true);
                }

                long startMs, endMs;
                Predicate<Integer> allowedConflicts;
                if (conflicts2 != null) {
                    long startTurn = Long.MAX_VALUE;
                    long endTurn = 0;
                    for (Conflict conflict : conflictsFinal) {
                        startTurn = Math.min(startTurn, conflict.getStartTurn());
                        endTurn = Math.max(endTurn, conflict.getEndTurn());
                    }
                    if (endTurn == 0) return;
                    startMs = TimeUtil.getTimeFromTurn(startTurn);
                    endMs = endTurn == Long.MAX_VALUE ? Long.MAX_VALUE : TimeUtil.getTimeFromTurn(endTurn);

                    boolean[] allowedConflictOrdsArr = new boolean[conflictArr.length];
                    for (Conflict conflict : conflictsFinal) {
                        allowedConflictOrdsArr[conflict.getOrdinal()] = true;
                    }
                    allowedConflicts = f -> allowedConflictOrdsArr[f];
                } else {
                    startMs = 0;
                    endMs = Long.MAX_VALUE;
                    allowedConflicts = Predicates.alwaysTrue();
                }

                Set<DBWar> wars = new ObjectOpenHashSet<>();
                for (DBWar war : this.db.getWars()) {
                    if (war.getDate() >= startMs && war.getDate() <= endMs) {
                        if (updateWar(null, war, allowedConflicts)) {
//                        if (war.isActive() && TimeUtil.getTurn(war.getDate()) + 61 < currentTurn) {
//                            System.out.println("INVALID WAR EXPIRED " + war.getWarId() + " | " + war.getDate() + " | " + war.getStatus());
//                        }
                            wars.add(war);
                        }
                    }
                }

                Set<DBWar> manualWars = loadManualWars(conflictsFinal);
                for (DBWar war : manualWars) {
                    if (updateWar(null, war, allowedConflicts)) {
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
                    db.iterateWarAttacks(wars, Predicates.alwaysTrue(), Predicates.alwaysTrue(), (war, attack) -> {
                        if (TimeUtil.getTurn(war.getDate()) <= TimeUtil.getTurn(attack.getDate())) {
                            updateAttack(war, attack, allowedConflicts, a -> {
                                int id = a.getWar_attack_id();
                                Byte cached = subTypes.get(id);
                                if (cached != null) {
                                    return cached == -1 ? null : AttackTypeSubCategory.values[cached];
                                }
                                AttackTypeSubCategory sub = a.getSubCategory(activityCache);
                                newSubTypes.put(id, sub == null ? -1 : (byte) sub.ordinal());
                                return sub;
                            });
                        }
                    });
                    if (!newSubTypes.isEmpty()) {
                        saveSubTypes(newSubTypes);
                    }
                }

                {
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
                                conflict.getSide(side).addGraphData(metric, allianceId, turnOrDay, city, value);
                            }
                        }
                    });
                }
                conflictsLoaded = true;
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

    public void addManualWar(int conflictId, List<DBWar> wars, int allianceId) {
        String query = "INSERT INTO MANUAL_WARS (war_id, conflict_id, alliance) VALUES (?, ?, ?)";
        db.executeBatch(wars, query, (ThrowingBiConsumer<DBWar, PreparedStatement>) (war, stmt) -> {
            stmt.setInt(1, war.getWarId());
            stmt.setInt(2, conflictId);
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
        invalidateConflictRowCache(conflictId);
        db.update("UPDATE conflicts SET status = ? WHERE id = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setString(1, status);
            stmt.setInt(2, conflictId);
        });
    }

    public void setCb(int conflictId, String cb) {
        invalidateConflictRowCache(conflictId);
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
                Conflict conflict = new Conflict(id, conflictArr.length, name, turnStart, turnEnd);
                conflict.trySetData(col1, col2, creator, category, wiki, cb, status);
                conflictById.put(id, conflict);
                conflictArr = Arrays.copyOf(conflictArr, conflictArr.length + 1);
                conflictArr[conflictArr.length - 1] = conflict;

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
        invalidateConflictRowCache(conflict.getId());
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
        db.update("UPDATE conflicts SET start = ?, end = ? WHERE id = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setLong(1, start);
            stmt.setLong(2, end);
            stmt.setInt(3, conflict.getId());
        });
    }

    public void updateConflictName(int conflictId, String name) {
        invalidateConflictRowCache(conflictId);
        db.update("UPDATE conflicts SET name = ? WHERE id = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setString(1, name);
            stmt.setInt(2, conflictId);
        });
    }

    public void updateConflictName(int conflictId, String name, boolean isPrimary) {
        invalidateConflictRowCache(conflictId);
        db.update("UPDATE conflicts SET `col" + (isPrimary ? "1" : "2") + "` = ? WHERE id = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setString(1, name);
            stmt.setInt(2, conflictId);
        });
    }


    public void updateConflictWiki(int conflictId, String wiki) {
        invalidateConflictRowCache(conflictId);
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
    }

    protected void removeParticipant(Conflict conflict, int allianceId) {
        db.update("DELETE FROM conflict_participant WHERE alliance_id = ? AND conflict_id = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, allianceId);
            stmt.setInt(2, conflict.getId());
        });
        addConflictsByAlliance(conflict, true);
    }

    public Map<Integer, Conflict> getConflictMap() {
        synchronized (conflictById) {
            return new Int2ObjectOpenHashMap<>(conflictById);
        }
    }

    public List<Conflict> getActiveConflicts() {
        long cutoff = TimeUtil.getTurn() - 60;
        return conflictById.values().stream().filter(conflict -> conflict.getEndTurn() >= cutoff).toList();
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
        invalidateConflictRowCache(conflict.getId());
        synchronized (activeConflictsOrd) {
            synchronized (conflictById) {
                if (conflictById.remove(conflict.getId()) != null) {
                    ArrayList<Conflict> conflictList = new ArrayList<>(Arrays.asList(conflictArr));
                    conflictList.remove(conflict);
                    conflictArr = conflictList.toArray(new Conflict[0]);

                    activeConflictsOrd.remove(conflict.getOrdinal());

                    recreateConflictsByAlliance();
                }
            }
        }
        db.update("DELETE FROM conflicts WHERE id = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, conflict.getId());
        });
        db.update("DELETE FROM conflict_participant WHERE conflict_id = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, conflict.getId());
        });
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

    public void invalidateFlatGraphCache(int conflictId) {
        db.update("DELETE FROM conflict_flat_cache WHERE conflict_id = ?",
                (ThrowingConsumer<PreparedStatement>) stmt -> stmt.setInt(1, conflictId));
    }

    public byte[] getPsonGzip() {
        Map<String, Function<Conflict, Object>> headerFuncs = Conflict.createHeaderFuncs();

        List<String> headers = new ObjectArrayList<>();
        List<Function<Conflict, Object>> funcs = new ObjectArrayList<>();
        for (Map.Entry<String, Function<Conflict, Object>> entry : headerFuncs.entrySet()) {
            headers.add(entry.getKey());
            funcs.add(entry.getValue());
        }

        Map<Integer, Conflict> map = getConflictMap();

        Map<Integer, String> aaNameById = new Int2ObjectOpenHashMap<>();
        synchronized (conflictAlliances) {
            for (int allianceId : conflictAlliances) {
                String name = getAllianceNameOrNull(allianceId);
                if (name != null) {
                    aaNameById.put(allianceId, name);
                }
            }
        }
        List<Integer> allianceIds = new ArrayList<>(aaNameById.keySet());
        Collections.sort(allianceIds);
        List<String> aaNames = allianceIds.stream().map(aaNameById::get).toList();

        Map<Long, List<Long>> sourceSets = getSourceSets();

        ObjectMapper mapper = JteUtil.getSerializer();
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try (JsonGenerator gen = mapper.getFactory().createGenerator(out)) {
            gen.writeStartObject();

            gen.writeFieldName("headers");
            mapper.writeValue(gen, headers);

            long headerHash = ensureConflictHeaderHash(headers);
            gen.writeFieldName("conflicts");
            gen.writeStartArray();
            for (Conflict conflict : map.values()) {
                boolean cacheable = conflict.getId() > 0 && !conflict.isActive();
                byte[] cached = cacheable ? loadConflictRowCache(conflict.getId(), headerHash) : null;
                if (cached != null) {
                    gen.writeRawValue(new String(cached, StandardCharsets.UTF_8));
                    continue;
                }
                        List<Object> row = new ArrayList<>();
                for (Function<Conflict, Object> func : funcs) {
                    row.add(func.apply(conflict));
                }
                String rowJson = mapper.writeValueAsString(row);
                gen.writeRawValue(rowJson);
                if (cacheable) {
                    saveConflictRowCache(conflict.getId(), headerHash, rowJson.getBytes(StandardCharsets.UTF_8));
                }
            }
            gen.writeEndArray();

            // write the rest of the fields in the same fashion…
            gen.writeFieldName("alliance_ids");
            mapper.writeValue(gen, allianceIds);

            // the rest
            gen.writeFieldName("alliance_names");
            mapper.writeValue(gen, aaNames);

            gen.writeFieldName("source_sets");
            mapper.writeValue(gen, getSourceSetStrings(sourceSets));
            gen.writeFieldName("source_names");
            mapper.writeValue(gen, getSourceNames(sourceSets.keySet()));

            gen.writeEndObject();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return JteUtil.compress(out.toByteArray());
    }

    private long ensureConflictHeaderHash(List<String> headers) {
        if (headers == null) throw new IllegalArgumentException("headers cannot be null");
        long computed = StringMan.hash(headers);

        if (!conflictHeaderHashInitialized || conflictHeaderHash != computed) {
            synchronized (this) {
                if (!conflictHeaderHashInitialized || conflictHeaderHash != computed) {
                    conflictHeaderHashInitialized = true;
                    long previous = conflictHeaderHash;
                    conflictHeaderHash = computed;

                    if (previous != computed) {
                        db.update("DELETE FROM conflict_row_cache WHERE header_hash != ?",
                                (ThrowingConsumer<PreparedStatement>) stmt -> stmt.setLong(1, computed));
                    }
                }
            }
        }
        return conflictHeaderHash;
    }

    private byte[] loadConflictRowCache(int conflictId, long expectedHash) {
        final byte[][] rowData = new byte[1][];
        final boolean[] stale = new boolean[1];

        db.query("SELECT header_hash, row_data FROM conflict_row_cache WHERE conflict_id = ?",
                (ThrowingConsumer<PreparedStatement>) stmt -> stmt.setInt(1, conflictId),
                (ThrowingConsumer<ResultSet>) rs -> {
                    if (rs.next()) {
                        long storedHash = rs.getLong("header_hash");
                        if (storedHash == expectedHash) {
                            rowData[0] = rs.getBytes("row_data");
                        } else {
                            stale[0] = true;
                        }
                    }
                });

        if (stale[0]) {
            invalidateConflictRowCache(conflictId);
        }
        return rowData[0];
    }

    private void saveConflictRowCache(int conflictId, long headerHash, byte[] rowData) {
        if (rowData == null) return;
        db.update("INSERT OR REPLACE INTO conflict_row_cache (conflict_id, header_hash, row_data) VALUES (?, ?, ?)",
                (ThrowingConsumer<PreparedStatement>) stmt -> {
                    stmt.setInt(1, conflictId);
                    stmt.setLong(2, headerHash);
                    stmt.setBytes(3, rowData);
                });
    }

    public void invalidateConflictRowCache(int conflictId) {
        db.update("DELETE FROM conflict_row_cache WHERE conflict_id = ?",
                (ThrowingConsumer<PreparedStatement>) stmt -> stmt.setInt(1, conflictId));
    }

    public void invalidateInactiveConflictCache(Conflict conflict) {
        if (conflict == null) return;
        if (conflict.getId() > 0 && !conflict.isActive()) {
            invalidateConflictRowCache(conflict.getId());
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
        invalidateConflictRowCache(conflictId);
        db.update("UPDATE conflicts SET category = ? WHERE id = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, category.ordinal());
            stmt.setInt(2, conflictId);
        });
    }

    public AwsManager getAws() {
        return aws;
    }

    public void removeAnnouncement(int id, int topicId) {
        db.update("DELETE FROM conflict_announcements2 WHERE conflict_id = ? AND topic_id = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, id);
            stmt.setInt(2, topicId);
        });
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
                startTimes.put(allianceId, rs.getLong("start"));
                endTimes.put(allianceId, rs.getLong("end"));
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
