package link.locutus.discord.db.conflict;

import com.google.common.eventbus.Subscribe;
import com.ptsmods.mysqlw.Database;
import it.unimi.dsi.fastutil.ints.*;
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
import link.locutus.discord.event.war.WarCreateEvent;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.scheduler.ThrowingBiConsumer;
import link.locutus.discord.util.scheduler.ThrowingConsumer;
import link.locutus.discord.web.jooby.AwsManager;
import link.locutus.discord.web.jooby.JteUtil;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
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

public class ConflictManager {
    private final WarDB db;
    private final AwsManager aws;
    private boolean conflictsLoaded = false;

    private final Map<Integer, Conflict> conflictById = new Int2ObjectOpenHashMap<>();
    private Conflict[] conflictArr;
    private final Map<Integer, String> legacyNames2 = new Int2ObjectOpenHashMap<>();
    private final Map<String, Map<Long, Integer>> legacyIdsByDate = new ConcurrentHashMap<>();
    private final Set<Integer> activeConflictsOrd = new IntOpenHashSet();
    private long lastTurn = 0;
    private final Map<Integer, Set<Integer>> activeConflictOrdByAllianceId = new Int2ObjectOpenHashMap<>();
    private final Map<Long, Map<Integer, int[]>> mapTurnAllianceConflictOrd = new Long2ObjectOpenHashMap<>();

    public ConflictManager(WarDB db) {
        this.db = db;
        this.aws = setupAws();
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
//        // drop table conflicts
//        db.executeStmt("DROP TABLE IF EXISTS conflict_participant");
//        db.executeStmt("DROP TABLE IF EXISTS conflicts");
//        db.executeStmt("DROP TABLE IF EXISTS conflict_announcements2");
//        db.executeStmt("DROP TABLE IF EXISTS conflict_graphs2");

        db.executeStmt("CREATE TABLE IF NOT EXISTS conflict_announcements2 (conflict_id INTEGER NOT NULL, topic_id INTEGER NOT NULL, description VARCHAR NOT NULL, PRIMARY KEY (conflict_id, topic_id), FOREIGN KEY(conflict_id) REFERENCES conflicts(id))");
        db.executeStmt("CREATE TABLE IF NOT EXISTS conflicts (id INTEGER PRIMARY KEY AUTOINCREMENT, name VARCHAR NOT NULL, start BIGINT NOT NULL, end BIGINT NOT NULL, col1 VARCHAR NOT NULL, col2 VARCHAR NOT NULL, wiki VARCHAR NOT NULL, cb VARCHAR NOT NULL, status VARCHAR NOT NULL, category INTEGER NOT NULL, creator BIGINT NOT NULL)");
        // add column `creator`
        // add col1 and col2 (string) to conflicts, default ""
//        db.executeStmt("ALTER TABLE conflicts ADD COLUMN col1 VARCHAR DEFAULT ''");
//        db.executeStmt("ALTER TABLE conflicts ADD COLUMN col2 VARCHAR DEFAULT ''");
        // add wiki column, default empty
        db.executeStmt("ALTER TABLE conflicts ADD COLUMN creator BIGINT DEFAULT 0");
        db.executeStmt("ALTER TABLE conflicts ADD COLUMN wiki VARCHAR DEFAULT ''");
        db.executeStmt("ALTER TABLE conflicts ADD COLUMN cb VARCHAR DEFAULT ''");
        db.executeStmt("ALTER TABLE conflicts ADD COLUMN status VARCHAR DEFAULT ''");
        db.executeStmt("ALTER TABLE conflicts ADD COLUMN category INTEGER DEFAULT 0");

        db.executeStmt("CREATE TABLE IF NOT EXISTS conflict_participant (conflict_id INTEGER NOT NULL, alliance_id INTEGER NOT NULL, side BOOLEAN, start BIGINT NOT NULL, end BIGINT NOT NULL, PRIMARY KEY (conflict_id, alliance_id), FOREIGN KEY(conflict_id) REFERENCES conflicts(id))");
        db.executeStmt("CREATE TABLE IF NOT EXISTS legacy_names2 (id INTEGER NOT NULL, name VARCHAR NOT NULL, date BIGINT DEFAULT 0, PRIMARY KEY (id, name, date))");
        db.executeStmt("DROP TABLE legacy_names");
//        db.executeStmt("DELETE FROM conflicts");

        db.executeStmt("CREATE TABLE IF NOT EXISTS conflict_graphs2 (conflict_id INTEGER NOT NULL, side BOOLEAN NOT NULL, alliance_id INT NOT NULL, metric INTEGER NOT NULL, turn BIGINT NOT NULL, city INTEGER NOT NULL, value INTEGER NOT NULL, PRIMARY KEY (conflict_id, alliance_id, metric, turn, city), FOREIGN KEY(conflict_id) REFERENCES conflicts(id))");
        // drop conflict_graphs
        db.executeStmt("DROP TABLE conflict_graphs");

        db.executeStmt("CREATE TABLE IF NOT EXISTS source_sets (guild BIGINT NOT NULL, source_id BIGINT NOT NULL, source_type INT NOT NULL, PRIMARY KEY (guild, source_id, source_type))");

        // attack_subtypes attack id int primary key, subtype int not null
        db.executeStmt("CREATE TABLE IF NOT EXISTS attack_subtypes (attack_id INT PRIMARY KEY, subtype INT NOT NULL)");
    }

    private synchronized void importData(Database sourceDb, Database targetDb, String tableName) throws SQLException {
        System.out.println("remove:|| import: updating " + tableName);
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
        System.out.println("Remove:|| import: clearing table conflict_graphs2");
        db.executeStmt("DELETE FROM conflict_graphs2");
        System.out.println("Remove:|| import: clearing table attack_subtypes");
        db.executeStmt("DELETE FROM attack_subtypes");
        for (String table : tables) {
            // clear all rows of table
            System.out.println("Remove:|| import: clearing table " + table);
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
            System.out.println("Pushed dirty conflicts");
            return true;
        } else {
            System.out.println("No dirty conflicts");
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
        DBWar war = attack.getWar();
        if (war != null) {
            updateAttack(war, attack, f -> true, f -> {
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
        List<Conflict> conflicts = getActiveConflicts();
        if (!conflicts.isEmpty()) {
            for (Conflict conflict : conflicts) {
                conflict.getSide(true).updateTurnChange(this, turn, true);
                conflict.getSide(false).updateTurnChange(this, turn, true);
            }
            for (Conflict conflict : conflicts) {
                conflict.push(this, null, true, false);
            }
            pushIndex();
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
            for (int aaId : conflict.getAllianceIds()) {
                activeConflictOrdByAllianceId.computeIfAbsent(aaId, k -> new IntArraySet()).add(conflict.getOrdinal());
            }
            addAllianceTurn(conflict, conflict.getStartTurn(), Math.min(TimeUtil.getTurn(), conflict.getEndTurn()));
        }
    }

    public void loadConflicts() {
        System.out.println("Load conflicts");
        long start = System.currentTimeMillis();

        List<Conflict> conflicts = new ArrayList<>();
        conflictById.clear();
        db.query("SELECT * FROM conflicts", stmt -> {
        }, (ThrowingConsumer<ResultSet>) rs -> {
            int ordinal = 0;
            while (rs.next()) {
                int id = rs.getInt("id");
                String name = rs.getString("name");
                long startTurn = rs.getLong("start");
                long endTurn = rs.getLong("end");
                long createdByGuild = rs.getLong("creator");
                String wiki = rs.getString("wiki");
                String col1 = rs.getString("col1");
                String col2 = rs.getString("col2");
                ConflictCategory category = ConflictCategory.values[rs.getInt("category")];
                if (col1.isEmpty()) col1 = "Coalition 1";
                if (col2.isEmpty()) col2 = "Coalition 2";
                String cb = rs.getString("cb");
                String status = rs.getString("status");
                Conflict conflict = new Conflict(id, ordinal++, createdByGuild, category, name, col1, col2, wiki, cb, status, startTurn, endTurn);
                conflicts.add(conflict);
                conflictById.put(id, conflict);
            }
        });
        this.conflictArr = conflicts.toArray(new Conflict[0]);

        System.out.println("Loaded " + conflictArr.length + " conflicts in " + ((-start) + (start = System.currentTimeMillis()) + "ms"));

//        db.update("DELETE FROM conflict_participant WHERE alliance_id = 0");
        db.query("SELECT * FROM conflict_participant", stmt -> {
        }, (ThrowingConsumer<ResultSet>) rs -> {
            while (rs.next()) {
                int conflictId = rs.getInt("conflict_id");
                int allianceId = rs.getInt("alliance_id");
                boolean side = rs.getBoolean("side");
                long startTurn = rs.getLong("start");
                long endTurn = rs.getLong("end");
                Conflict conflict = conflictById.get(conflictId);
                if (conflict != null) {
                    conflict.addParticipant(allianceId, side, false, startTurn, endTurn);
                }
            }
        });

        System.out.println("Loaded participants in " + ((-start) + (start = System.currentTimeMillis()) + "ms"));

        // load announcements
        if (Locutus.imp().getForumDb() != null) {
            Map<Integer, Map<Integer, String>> conflictsByTopic = new Int2ObjectOpenHashMap<>();
            db.query("SELECT * FROM conflict_announcements2", stmt -> {
            }, (ThrowingConsumer<ResultSet>) rs -> {
                while (rs.next()) {
                    int conflictId = rs.getInt("conflict_id");
                    String desc = rs.getString("description");
                    int topicId = rs.getInt("topic_id");
                    Conflict conflict = conflictById.get(conflictId);
                    if (conflict != null) {
                        conflictsByTopic.computeIfAbsent(topicId, k -> new HashMap<>()).put(conflictId, desc);
                    }
                }
            });

            System.out.println("Loaded announcements in " + ((-start) + (start = System.currentTimeMillis()) + "ms"));

            Map<Integer, DBTopic> topics = Locutus.imp().getForumDb().getTopics(conflictsByTopic.keySet());
            System.out.println("Loaded topics " + topics.size());
            for (Map.Entry<Integer, Map<Integer, String>> entry : conflictsByTopic.entrySet()) {
                DBTopic topic = topics.get(entry.getKey());
                if (topic != null) {
                    for (Map.Entry<Integer, String> entry2 : entry.getValue().entrySet()) {
                        Conflict conflict = conflictById.get(entry2.getKey());
                        if (conflict != null) {
                            conflict.addAnnouncement(entry2.getValue(), topic, false);
                        }
                    }
                }
            }

            System.out.println("Loaded announcements in " + ((-start) + (start = System.currentTimeMillis()) + "ms"));
        } else {
            System.out.println("Forum db is null");
        }
        // load legacyNames
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

        System.out.println("Loaded legacy names in " + ((-start) + (start = System.currentTimeMillis()) + "ms"));

        for (Map.Entry<String, Integer> entry : getDefaultNames().entrySet()) {
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

        System.out.println("Loaded default names in " + ((-start) + (start = System.currentTimeMillis()) + "ms"));

//        Set<Integer> empty = new HashSet<>();
//        for (Map.Entry<Integer, Conflict> conflictEntry : conflictMap.entrySet()) {
//            if (conflictEntry.getValue().getAllianceIds().isEmpty()) {
//                empty.add(conflictEntry.getKey());
//            }
//        }
        // delete empty conflicts
//        db.executeStmt("DELETE FROM conflicts WHERE `id` in (" + StringMan.join(empty, ",") + ")");
//        db.executeStmt("DELETE FROM conflict_announcements2 WHERE `conflict_id` in (" + StringMan.join(empty, ",") + ")");
//        db.executeStmt("DELETE FROM conflict_participant WHERE `conflict_id` not in (" + StringMan.join(conflictMap.keySet(), ",") + ")");
//        db.executeStmt("DELETE FROM conflict_announcements2");
//        db.update("DELETE FROM conflict_graphs WHERE conflict_id = 0");
//        db.executeStmt("DELETE FROM conflict_participant");


//        System.out.println("Loaded " + conflictMap.size() + " conflicts in " + ((-start) + (start = System.currentTimeMillis()) + "ms"));
//        if (legacyNames.isEmpty()) {
//            saveDefaultNames();
//        }
        Locutus.imp().getExecutor().submit(() -> {
            loadConflictWars(null, false);
            Locutus.imp().getCommandManager().getExecutor().scheduleWithFixedDelay(() -> {
                try {
                    System.out.println("Update task " + (!conflictsLoaded) + " | " + (!TimeUtil.checkTurnChange()));
                    if (!conflictsLoaded || !TimeUtil.checkTurnChange()) return;
                    System.out.println("Pushing dirty conflicts");
                    pushDirtyConflicts();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }, 1, 1, TimeUnit.MINUTES);
        });
        System.out.println("Load graph data: " + ((-start) + (start = System.currentTimeMillis()) + "ms"));
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
        db.iterateWarAttacks(wars, f -> true, f -> true, (war, attack) -> {
            long turn = TimeUtil.getTurn(attack.getDate());
            if (TimeUtil.getTurn(war.getDate()) <= turn) {
                conflict.updateAttack(war, attack, turn, f -> null);
            }
        });
    }

    public void loadConflictWars(Collection<Conflict> conflicts, boolean clearBeforeUpdate) {
        try {
            long start = System.currentTimeMillis();
            initTurn();
            System.out.println("Init turns in " + ((-start) + (start = System.currentTimeMillis()) + "ms"));

            if (clearBeforeUpdate) {
                Collection<Conflict> tmp = conflicts == null ? Arrays.asList(conflictArr) : conflicts;
                for (Conflict conflict : tmp) {
                    conflict.clearWarData();
                }
                System.out.println("Clear war data in " + ((-start) + (start = System.currentTimeMillis()) + "ms"));
            }

            long startMs, endMs;
            Predicate<Integer> allowedConflicts;
            if (conflicts != null) {
                long startTurn = Long.MAX_VALUE;
                long endTurn = 0;
                for (Conflict conflict : conflicts) {
                    startTurn = Math.min(startTurn, conflict.getStartTurn());
                    endTurn = Math.max(endTurn, conflict.getEndTurn());
                }
                if (endTurn == 0) return;
                startMs = TimeUtil.getTimeFromTurn(startTurn);
                endMs = endTurn == Long.MAX_VALUE ? Long.MAX_VALUE : TimeUtil.getTimeFromTurn(endTurn + 60);

                boolean[] allowedConflictOrdsArr = new boolean[conflictArr.length];
                for (Conflict conflict : conflicts) {
                    allowedConflictOrdsArr[conflict.getOrdinal()] = true;
                }
                allowedConflicts = f -> allowedConflictOrdsArr[f];
            } else {
                startMs = 0;
                endMs = Long.MAX_VALUE;
                allowedConflicts = f -> true;
            }

            System.out.println("Loaded allowed conflicts in " + ((-start) + (start = System.currentTimeMillis()) + "ms"));

            Set<DBWar> wars = new ObjectOpenHashSet<>();
            long currentTurn = TimeUtil.getTurn();
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

            System.out.println("Loaded wars in " + ((-start) + (start = System.currentTimeMillis()) + "ms"));
            if (!wars.isEmpty()) {
                Map<Integer, Byte> subTypes = loadSubTypes();
                System.out.println("Loaded subtypes in " + ((-start) + (start = System.currentTimeMillis()) + "ms") + " | " + subTypes.size());
                Map<Integer, Byte> newSubTypes = new Int2ByteOpenHashMap();
                BiFunction<DBNation, Long, Integer> activityCache = new BiFunction<>() {
                    private Map<Integer, Set<Long>> activity;
                    @Override
                    public Integer apply(DBNation nation, Long dateMs) {
                        if (activity == null) {
                            long start = System.currentTimeMillis();
                            activity = Locutus.imp().getNationDB().getActivityByDay(startMs - TimeUnit.DAYS.toMillis(10), endMs);
                            System.out.println("Loaded activity in " + ((-start) + (start = System.currentTimeMillis()) + "ms"));
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
                db.iterateWarAttacks(wars, f -> true, f -> true, (war, attack) -> {
                    if (TimeUtil.getTurn(war.getDate()) <= TimeUtil.getTurn(attack.getDate())) {
                        updateAttack(war, attack, allowedConflicts, new Function<IAttack, AttackTypeSubCategory>() {
                            @Override
                            public AttackTypeSubCategory apply(IAttack a) {
                                int id = a.getWar_attack_id();
                                Byte cached = subTypes.get(id);
                                if (cached != null) {
                                    return cached == -1 ? null : AttackTypeSubCategory.values[cached];
                                }
                                AttackTypeSubCategory sub = a.getSubCategory(activityCache);
                                newSubTypes.put(id, sub == null ? -1 : (byte) sub.ordinal());
                                return sub;
                            }
                        });
                    }
                });
                System.out.println("Loaded conflict attacks and subtypes in " + ((-start) + (start = System.currentTimeMillis()) + "ms"));
                if (!newSubTypes.isEmpty()) {
                    saveSubTypes(newSubTypes);
                }
                System.out.println("Saved new conflict subtypes in " + ((-start) + (start = System.currentTimeMillis()) + "ms") + " | " + newSubTypes.size());
            }

            if (conflicts == null || conflicts.stream().anyMatch(f -> f.getId() != -1)) {
                db.query("SELECT * FROM conflict_graphs2", stmt -> {
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
                System.out.println("Loaded graph data in " + ((-start) + (start = System.currentTimeMillis()) + "ms"));
            }
            conflictsLoaded = true;
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private Map<String, Integer> getDefaultNames() {
        Map<String, Integer> legacyIds = new HashMap<>();
        legacyIds.put("Arrgh!", 913);
        legacyIds.put("The Hive", 8819);
        legacyIds.put("Zodiac", 4145);
        legacyIds.put("Resplendent Inc.", 2844);
        legacyIds.put("Phoenix", 1809);
        legacyIds.put("Hogwarts", 9110);
        legacyIds.put("Global United Nations", 107);
        legacyIds.put("Blue Moon", 3003);
        legacyIds.put("Animal Empire", 4158);
        legacyIds.put("Cobalt", 770);
        legacyIds.put("IKEA", 8777);
        legacyIds.put("Ignis Aternum", 11713);
        legacyIds.put("IronFront", 5462);
        legacyIds.put("InGen", 1634);
        legacyIds.put("Dutch East India Company", 9997);
        legacyIds.put("The Chola", 2047);
        legacyIds.put("The Kings Parliament", 1804);
        legacyIds.put("Ex Machina", 7414);
        legacyIds.put("DawnGuard", 11513);
        legacyIds.put("Dawn Rising", 8520);
        legacyIds.put("Clover Kingdom", 7094);
        legacyIds.put("New Pacific Order", 2082);
        legacyIds.put("Divine Phoenix Empire", 8173);
        legacyIds.put("Divine Phoenix", 8173);
        legacyIds.put("Aeterna", 11841);
        legacyIds.put("The Empire of the Moonlit Sakura", 4623);
        legacyIds.put("The Federation", 4638);
        legacyIds.put("The Manhattan Cartel", 5851);
        legacyIds.put("The North Western South Region of the Far East", 6703);
        legacyIds.put("The Paladin", 11568);
        legacyIds.put("The Rohirrim", 7540);
        legacyIds.put("The Ronin Empire", 7376);
        legacyIds.put("The Three Swords", 10374);
        legacyIds.put("The United Armies", 5326);
        legacyIds.put("ThunderStruck", 7635);
        legacyIds.put("Titanio", 7642);
        legacyIds.put("United Commerce Republic", 8651);
        legacyIds.put("VooDoo", 8804);
        legacyIds.put("Waffle House", 10936);
        legacyIds.put("Swords of Sanghelios", 8173);
        legacyIds.put("Terran Federation", 9110);
        legacyIds.put("The Circus", 9521);
        legacyIds.put("The Coal Mines (2nd)", 7094);
        legacyIds.put("The Coven", 9573);
        legacyIds.put("The Elites", 8429);
        legacyIds.put("Order of the White Rose", 2510);
        legacyIds.put("Polaris", 2358);
        legacyIds.put("Prima Victoria", 7570);
        legacyIds.put("Roman Empire", 9600);
        legacyIds.put("Serene Repubblica Fiorentina", 9927);
        legacyIds.put("Serene Wei", 1742);
        legacyIds.put("Roz Wei", 1742);
        legacyIds.put("Terminal Jest", 7620);
        legacyIds.put("Nuclear Knights",1634);
        legacyIds.put("Animation Domination", 4597);
        legacyIds.put("Cornerstone", 1037);
        legacyIds.put("Seven Kingdoms", 615);
        legacyIds.put("Nordic Sea Raiders", 4341);
        legacyIds.put("Soldiers of Liberty", 6215);
        legacyIds.put("Spartan Brotherhood", 6161);
        legacyIds.put("Spartan Republic", 7380);
        legacyIds.put("Sunray 1-1", 9651);
        legacyIds.put("Sunray Victoria", 10466);
        legacyIds.put("Atlas Technological Cooperative", 7306);
        legacyIds.put("Ghost Division", 9588);
        legacyIds.put("Children of the Light", 7452);
        legacyIds.put("iriririr", 12438);
        legacyIds.put("United Nations 2", 12359);
        legacyIds.put("insinsaneane", 12450);
        legacyIds.put("Noble Wei", 12382);
        legacyIds.put("Convent of Atom",7531);
        legacyIds.put("The Ampersand",5722);
        legacyIds.put("Brotherhood of the Clouds",7703);
        legacyIds.put("Bank Robbers",7923);
        legacyIds.put("The Outhouse",7990);
        legacyIds.put("Not Rohans Bank",8014);
        legacyIds.put("Democracy",8060);
        legacyIds.put("Prusso Roman Imperial Union",7920);
        legacyIds.put("Avalanche",8150);
        legacyIds.put("Sanctuary",8368);
        legacyIds.put("Union of Soviet Socialist Republics",8531);
        legacyIds.put("Ad Astra",7719);
        legacyIds.put("Otaku Shougaku",8594);
        legacyIds.put("Wizards",8624);
        legacyIds.put("MDC",8615);
        legacyIds.put("Christmas",8614);
        legacyIds.put("MySpacebarIsBroken",8678);
        legacyIds.put("Lords of Wumbology",8703);
        legacyIds.put("Paragon",8502);
        legacyIds.put("Shuba2M",8909);
        legacyIds.put("Shuba69M",8929);
        legacyIds.put("Mensa HQ",8930);
        legacyIds.put("Shuba99M",8955);
        legacyIds.put("Not A Scam",8984);
        legacyIds.put("The Dead Rabbits",7540);
        legacyIds.put("The Vatican",9321);
        legacyIds.put("High Temple",9341);
        legacyIds.put("Shuba666M",9385);
        legacyIds.put("Crimson Dragons",9406);
        legacyIds.put("Apollo",9427);
        legacyIds.put("Nibelheim",9580);
        legacyIds.put("Starfleet",9850);
        legacyIds.put("OTSN",9883);
        legacyIds.put("The Knights Of The Round Table",9830);
        legacyIds.put("Wayne Enterprises",9931);
        legacyIds.put("LegoLand",9961);
        legacyIds.put("Wayne Enterprises Inc",9971);
        legacyIds.put("Paradise",9986);
        legacyIds.put("The Afterlyfe",10060);
        legacyIds.put("Esquire Templar",10070);
        legacyIds.put("The Naughty Step",10074);
        legacyIds.put("The Cove",10104);
        legacyIds.put("Pacific Polar",10248);
        legacyIds.put("Stigma",10326);
        legacyIds.put("Sparkle Party People",10329);
        legacyIds.put("Age of Darkness",10100);
        legacyIds.put("Lunacy",9278);
        legacyIds.put("The Entente",10396);
        legacyIds.put("Crawling Crawfish Conundrum",10398);
        legacyIds.put("Western Republic",10408);
        legacyIds.put("General Patton",10411);
        legacyIds.put("Crab Creation Contraption",10416);
        legacyIds.put("The Bugs palace",10414);
        legacyIds.put("Aggravated Conch Assault",10425);
        legacyIds.put("Castle Wall",10436);
        legacyIds.put("Mukbang Lobster ASMR",10440);
        legacyIds.put("House of the Dragon",10445);
        legacyIds.put("lobster emoji",10447);
        legacyIds.put("LobsterGEDDON",10449);
        legacyIds.put("ARMENIA FOREVER",10452);
        legacyIds.put("Stigma 1",10450);
        legacyIds.put("General Custer",10454);
        legacyIds.put("Scyllarides Saloon",10464);
        legacyIds.put("Camelot Squires",10468);
        legacyIds.put("bruh momento",10467);
        legacyIds.put("Limp Lobster",10474);
        legacyIds.put("OSNAP",10472);
        legacyIds.put("AAAAAAAAA",10486);
        legacyIds.put("Alpha Lobster",10485);
        legacyIds.put("Shuba73M",10489);
        legacyIds.put("Borgs Assisted Loot Liberation Service",10504);
        legacyIds.put("Cornhub",10521);
        legacyIds.put("xXxJaredLetoFanxXx",10529);
        legacyIds.put("Anti-Horridism Obocchama Kun Fan Club",10540);
        legacyIds.put("Iraq Lobster",10552);
        legacyIds.put("Mole Rats",10574);
        legacyIds.put("God I Love Frogs",10573);
        legacyIds.put("A-HOK Fan Club Fan Club",10583);
        legacyIds.put("MyKeyboardIsBroken",10683);
        legacyIds.put("Show Me The Money",10694);
        legacyIds.put("Banana Stand London Branch",10709);
        legacyIds.put("Sparkle of the Night",10712);
        legacyIds.put("Cru Whole Hole",10716);
        legacyIds.put("Arrghs offshore",10717);
        legacyIds.put("Banana Stand New York",10720);
        legacyIds.put("Anything",10739);
        legacyIds.put("Banana Stand Los Angeles",10733);
        legacyIds.put("Wayne Foundation",10746);
        legacyIds.put("Banana Stand On The Run",10747);
        legacyIds.put("Master Basters",10759);
        legacyIds.put("Turkey land",10757);
        legacyIds.put("Theres No Place Like Home",10764);
        legacyIds.put("Drake - Hotline Bling",8520);
        legacyIds.put("Yer A Wizard Harry",10783);
        legacyIds.put("A Truth Universally Acknowledged",10805);
        legacyIds.put("An Offer He Cant Refuse",10815);
        legacyIds.put("HAHA England lost to France",10834);
        legacyIds.put("Banco dei Medici",8520);
        legacyIds.put("The Bank of Orbis",10092);
        legacyIds.put("May The Force Be With You",10839);
        legacyIds.put("Shaken Not Stirred",10845);
        legacyIds.put("Shaken Not Stired",10848);
        legacyIds.put("ET Phone Home",10854);
        legacyIds.put("Cock of destiny",10855);
        legacyIds.put("Yo Adrian",10868);
        legacyIds.put("Autocephalous Patriarchate of the Free",10869);
        legacyIds.put("Mama Always Said",10878);
        legacyIds.put("offshoreassss",10887);
        legacyIds.put("Youre Tacky and I Hate You",10905);
        legacyIds.put("O Captain My Captain",10912);
        legacyIds.put("HIDUDE GIB TIERING REPORT",10917);
        legacyIds.put("Bank of The Holy Grail",10925);
        legacyIds.put("Shuba45M",10933);
        legacyIds.put("The IX Legion",10934);
        legacyIds.put("Sparkle Forever",10946);
        legacyIds.put("BOSNIA MODE",10949);
        legacyIds.put("Jotunheimr",8429);
        legacyIds.put("Fallen Monarchy",10988);
        legacyIds.put("Gunga Ginga",11005);
        legacyIds.put("Grand Union of Nations",11018);
        legacyIds.put("Calamity",11019);
        legacyIds.put("borgborgborgborgborgborgborg",11023);
        legacyIds.put("Fargos",11027);
        legacyIds.put("Event Horizon",11039);
        legacyIds.put("CATA_IS_SO_COOL",11036);
        legacyIds.put("Pasta Factory",11042);
        legacyIds.put("MERDE",11059);
        legacyIds.put("The Black League",11066);
        legacyIds.put("United Nations Space Command",10995);
        legacyIds.put("Loopsnake alliance",11064);
        legacyIds.put("Old Praxis",11075);
        legacyIds.put("DecaDeezKnuttz",11077);
        legacyIds.put("Eurovision 2023 incoming",11090);
        legacyIds.put("Animal Pharm",11165);
        legacyIds.put("The Imperial Vault",11209);
        legacyIds.put("The House of Bugs",11288);
        legacyIds.put("Midnight Blues",11304);
        legacyIds.put("Mace & Chain",11312);
        legacyIds.put("Skull & Bones",11008);
        legacyIds.put("Swiss Account",11350);
        legacyIds.put("No offshore here",11353);
        legacyIds.put("Dunce Cap Supreme",11359);
        legacyIds.put("Aunt Jemima",11360);
        legacyIds.put("Fortuna sucks",11372);
        legacyIds.put("Home Hero",11375);
        legacyIds.put("Pharm Animal",11368);
        legacyIds.put("The Children of Yakub",11370);
        legacyIds.put("Shuba65M",11371);
        legacyIds.put("Tower of London",11376);
        legacyIds.put("Tintagel Castle",11384);
        legacyIds.put("Killer Tomatoes",11386);
        legacyIds.put("Nessa Barrett",11391);
        legacyIds.put("Legion of Dusk",11390);
        legacyIds.put("The Semimortals",11394);
        legacyIds.put("State of Orbis",11401);
        legacyIds.put("King Tiger",11398);
        legacyIds.put("Toilet Worshipping Lunatics",11403);
        legacyIds.put("The Peaceful Warmongers",11405);
        legacyIds.put("North Mexico",11407);
        legacyIds.put("Ketamine Therapy",11406);
        legacyIds.put("Kiwi Taxidermy",11420);
        legacyIds.put("Kazakhstani Tramway",11435);
        legacyIds.put("Prenadores de Burras Profesionales",11441);
        legacyIds.put("Kidney Transplant",11444);
        legacyIds.put("Shuba63m",11450);
        legacyIds.put("Knockoff Tetragrammatons",11457);
        legacyIds.put("Castillo de Coca",11454);
        legacyIds.put("Kangaroo Testicles",11473);
        legacyIds.put("New Church Republic",11494);
        legacyIds.put("Kleptomaniac Tunisians",11493);
        legacyIds.put("Shuba777M",11510);
        legacyIds.put("Kitten Toes",11514);
        legacyIds.put("Atlas Three",11515);
        legacyIds.put("Kaleidoscope Technology",11525);
        legacyIds.put("Koala Tornado",11531);
        legacyIds.put("Skylines",11533);
        legacyIds.put("Palo Mayombe",11604);
        legacyIds.put("Mouseleys Superfan Fun Cheese Corner 5",11619);
        legacyIds.put("General Area of Two Ostritches",11643);
        legacyIds.put("The Persian Empire",10671);
        legacyIds.put("Bakerstreet",11699);
        legacyIds.put("The Radiant Syndication",11719);
        legacyIds.put("Make More Monitors",11714);
        legacyIds.put("Quack",11718);
        legacyIds.put("Panama City Beach",11710);
        legacyIds.put("Shadowhunters",11715);
        legacyIds.put("Storm",11721);
        legacyIds.put("Three Inch Surprise",11730);
        legacyIds.put("Greywater Watch",11731);
        legacyIds.put("Port St Lucie",11740);
        legacyIds.put("The Orphanage",11746);
        legacyIds.put("Halo Revived",11751);
        legacyIds.put("Saint Augustine",11753);
        legacyIds.put("Orange Brotherhood",11764);
        legacyIds.put("Demon Slayer",11765);
        legacyIds.put("The Hippo Horde",11763);
        legacyIds.put("Yeehaw Junction",11769);
        legacyIds.put("House Weeb",11772);
        legacyIds.put("Ockey Multi Mass Production Facility 7",11779);
        legacyIds.put("TCM Extension",11797);
        legacyIds.put("Bohemian Grove",11811);
        legacyIds.put("House Stark Crypto Wallet",11805);
        legacyIds.put("Two Egg",11817);
        legacyIds.put("Elfers",11830);
        legacyIds.put("Gamblers Anonymous",11862);
        legacyIds.put("Humza Useless",11876);
        legacyIds.put("The Merry Men",11900);
        legacyIds.put("Jacobite Rebellion",11899);
        legacyIds.put("The Media",11912);
        legacyIds.put("World of Farce",11952);
        legacyIds.put("Lyra",12022);
        legacyIds.put("Free Alrea",12029);
        legacyIds.put("Black Banana",12031);
        legacyIds.put("Cassiopeia",12034);
        legacyIds.put("Basil Land",12036);
        legacyIds.put("Better eclipse",12037);
        legacyIds.put("Planet express",12043);
        legacyIds.put("Seven WHO",12047);
        legacyIds.put("Aquila",12057);
        legacyIds.put("Rum Raiders",12062);
        legacyIds.put("Free Alrea 3",12067);
        legacyIds.put("Zapp spammigan",12068);
        legacyIds.put("Eridanus",12064);
        legacyIds.put("Neighborhood watch alliance",12066);
        legacyIds.put("House Apathy",12069);
        legacyIds.put("Free Alrea 4",12076);
        legacyIds.put("Taurus",12084);
        legacyIds.put("Vela",12090);
        legacyIds.put("Chavez Nuestro que Estas en los Cielos",12102);
        legacyIds.put("Cygnus",12134);
        legacyIds.put("Thin Skin Singularity",12190);
        legacyIds.put("Red Wine on THT",12261);
        legacyIds.put("Cute Cats Cuddling in a Cayak",12290);
        legacyIds.put("Narutos",12318);
        legacyIds.put("insane",12344);
        legacyIds.put("enasni",12362);
        legacyIds.put("Tax Scheme",12364);
        legacyIds.put("aneins",12369);
        legacyIds.put("aneane",12380);
        legacyIds.put("insane transposed",12421);
        legacyIds.put("anti insane",12429);
        legacyIds.put("Biker Haven", 11389);
        legacyIds.put("Hegemoney", 11709);
        return legacyIds;
    }

    public void saveDataCsvAllianceNames() throws IOException, ParseException {
        Locutus.imp().getDataDumper(true).iterateAll(f -> true,
                (h, r) -> r.required(h.alliance_id, h.alliance),
                null,
                (day, header) -> {
                    int aaId = header.alliance_id.get();
                    if (aaId == 0) return;
                    String name = header.alliance.get();
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
        db.update("UPDATE conflicts SET status = ? WHERE id = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setString(1, status);
            stmt.setInt(2, conflictId);
        });
    }

    public void setCb(int conflictId, String cb) {
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
        db.update("INSERT OR IGNORE INTO conflict_announcements2 (conflict_id, topic_id, description) VALUES (?, ?, ?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
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
        db.update("DELETE FROM conflict_graphs2 WHERE conflict_id = ? AND side = ? AND metric IN (" + metric.stream().map(Enum::ordinal).map(String::valueOf).collect(Collectors.joining(",")) + ") AND turn = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
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
        String query = "INSERT INTO conflicts (name, col1, col2, wiki, start, end, category, cb, status, creator) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
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
                Conflict conflict = new Conflict(id, conflictArr.length, creator, category, name, col1, col2, wiki, cb, status, turnStart, turnEnd);
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
        db.update("UPDATE conflicts SET name = ? WHERE id = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setString(1, name);
            stmt.setInt(2, conflictId);
        });
    }

    public void updateConflictName(int conflictId, String name, boolean isPrimary) {
        db.update("UPDATE conflicts SET `col" + (isPrimary ? "1" : "2") + "` = ? WHERE id = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setString(1, name);
            stmt.setInt(2, conflictId);
        });
    }


    public void updateConflictWiki(int conflictId, String wiki) {
        db.update("UPDATE conflicts SET `wiki` = ? WHERE id = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setString(1, wiki);
            stmt.setInt(2, conflictId);
        });
    }

    protected void addParticipant(Conflict conflict, int allianceId, boolean side, long start, long end) {
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
        return conflictById.values().stream().filter(conflict -> conflict.getEndTurn() == Long.MAX_VALUE).toList();
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

    public byte[] getPsonGzip() {
        Map<String, Object> root = new Object2ObjectLinkedOpenHashMap<>();
        Map<Integer, Conflict> map = getConflictMap();
        Map<Integer, String> aaNameById = new HashMap<>();

        Map<String, Function<Conflict, Object>> headerFuncs = new LinkedHashMap<>();
        headerFuncs.put("id", Conflict::getId);
        headerFuncs.put("name", Conflict::getName);
        headerFuncs.put("c1_name", f -> f.getSide(true).getName());
        headerFuncs.put("c2_name", f -> f.getSide(false).getName());
        headerFuncs.put("start", f -> TimeUtil.getTimeFromTurn(f.getStartTurn()));
        headerFuncs.put("end", f -> f.getEndTurn() == Long.MAX_VALUE ? -1 : TimeUtil.getTimeFromTurn(f.getEndTurn()));
        headerFuncs.put("wars", Conflict::getTotalWars);
        headerFuncs.put("active_wars", Conflict::getActiveWars);
        headerFuncs.put("c1_dealt", f -> (long) f.getDamageConverted(true));
        headerFuncs.put("c2_dealt", f -> (long) f.getDamageConverted(false));
        headerFuncs.put("c1", f -> new IntArrayList(f.getCoalition1()));
        headerFuncs.put("c2", f -> new IntArrayList(f.getCoalition2()));
        headerFuncs.put("wiki", Conflict::getWiki);
        headerFuncs.put("status", Conflict::getStatusDesc);
        headerFuncs.put("cb", Conflict::getCasusBelli);
        headerFuncs.put("posts", Conflict::getAnnouncementsList);
        headerFuncs.put("source", Conflict::getGuildId);

        List<String> headers = new ObjectArrayList<>();
        List<Function<Conflict, Object>> funcs = new ObjectArrayList<>();
        for (Map.Entry<String, Function<Conflict, Object>> entry : headerFuncs.entrySet()) {
            headers.add(entry.getKey());
            funcs.add(entry.getValue());
        }
        root.put("headers", headers);

        List<List<Object>> rows = new ObjectArrayList<>();
        JteUtil.writeArray(rows, funcs, map.values());
        root.put("conflicts", rows);

        for (Conflict conflict : map.values()) {
            for (int id : conflict.getAllianceIds()) {
                if (!aaNameById.containsKey(id)) {
                    String name = getAllianceNameOrNull(id);
                    aaNameById.put(id, name == null ? "" : name);
                }
            }
        }
        List<Integer> allianceIds = new ArrayList<>(aaNameById.keySet());
        Collections.sort(allianceIds);
        List<String> aaNames = allianceIds.stream().map(aaNameById::get).toList();
        root.put("alliance_ids", allianceIds);
        root.put("alliance_names", aaNames);

        Map<Long, List<Long>> sourceSets = getSourceSets();
        root.put("source_sets", getSourceSetStrings(sourceSets));
        root.put("source_names", getSourceNames(sourceSets.keySet()));

        return JteUtil.compress(JteUtil.toBinary(root));
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
        return distance == Integer.MAX_VALUE ? null : Map.entry(similar, distance);
    }

    public void updateConflictCategory(int conflictId, ConflictCategory category) {
        db.update("UPDATE conflicts SET category = ? WHERE id = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, category.ordinal());
            stmt.setInt(2, conflictId);
        });
    }

    // updating

    public void update(AbstractCursor attack) {

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
}
