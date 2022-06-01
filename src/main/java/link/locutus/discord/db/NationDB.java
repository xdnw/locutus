package link.locutus.discord.db;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv3.PWApiV3;
import link.locutus.discord.commands.rankings.builder.GroupedRankBuilder;
import link.locutus.discord.commands.rankings.builder.RankBuilder;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.AllianceChange;
import link.locutus.discord.db.entities.AllianceMetric;
import link.locutus.discord.db.entities.CityInfraLand;
import link.locutus.discord.db.entities.DBSpyUpdate;
import link.locutus.discord.db.entities.NationMeta;
import link.locutus.discord.db.entities.Treaty;
import link.locutus.discord.event.TreatyUpdateEvent;
import link.locutus.discord.pnw.Alliance;
import link.locutus.discord.pnw.DBNation;
import link.locutus.discord.util.scheduler.ThrowingBiConsumer;
import link.locutus.discord.util.scheduler.ThrowingConsumer;
import link.locutus.discord.util.*;
import link.locutus.discord.util.math.ArrayUtil;
import link.locutus.discord.util.task.balance.GetCityBuilds;
import link.locutus.discord.util.update.NationUpdateProcessor;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import link.locutus.discord.apiv1.domains.subdomains.SAllianceContainer;
import link.locutus.discord.apiv1.domains.subdomains.SCityContainer;
import link.locutus.discord.apiv1.enums.Continent;
import link.locutus.discord.apiv1.enums.DomesticPolicy;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.NationColor;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.TreatyType;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.nio.ByteBuffer;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.text.ParseException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;

public class NationDB extends DBMain {
    private final Map<Integer, DBNation> nationCache = new ConcurrentHashMap<>();
    private Reference<BiMap<Integer, String>> getAlliancesCached;


    private void markDirty() {
        getAlliancesCached = null;
    }

    public NationDB() throws SQLException, ClassNotFoundException {
        super("nations");
    }

    public DBNation getNation(String nameOrLeader) {
        try (PreparedStatement stmt = prepareQuery("select * FROM NATIONS WHERE UPPER(`nation`) = UPPER(?) OR UPPER(`leader`) = UPPER(?)")) {
            stmt.setString(1, nameOrLeader);
            stmt.setString(2, nameOrLeader);
            DBNation leader = null;
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    leader = create(rs);
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

    public DBNation getNationByName(String name) {
        try (PreparedStatement stmt = prepareQuery("select * FROM NATIONS WHERE UPPER(`nation`) = UPPER(?)")) {
            stmt.setString(1, name);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    return create(rs);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public DBNation getNationByLeader(String leader) {
        try (PreparedStatement stmt = prepareQuery("select * FROM NATIONS WHERE UPPER(`leader`) = UPPER(?)")) {
            stmt.setString(1, leader);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    return create(rs);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public DBNation getNation(int nationId) {
        Map<Integer, DBNation> tmp = nationCache;
        if (!tmp.isEmpty()) {
            return tmp.get(nationId);
        }
        Map<Integer, DBNation> nations = getNations();
        DBNation result = nations.get(nationId);
        return result;
    }

    public List<DBNation> getNations(Set<Integer> alliances) {
        if (alliances.isEmpty()) {
            return new ArrayList<>(getNations().values());
        }
        List<DBNation> nations = new LinkedList<>();
        if (alliances.size() == 1) {
            int aaId = alliances.iterator().next();
            for (Map.Entry<Integer, DBNation> entry : getNations().entrySet()) {
                DBNation nation = entry.getValue();
                if (aaId == nation.getAlliance_id()) {
                    nations.add(nation);
                }
            }
        } else {
            alliances = new IntOpenHashSet(alliances);
            if (!alliances.contains(0)) {
                for (Map.Entry<Integer, DBNation> entry : getNations().entrySet()) {
                    DBNation nation = entry.getValue();
                    if (nation.getAlliance_id() != 0 && alliances.contains(nation.getAlliance_id())) {
                        nations.add(nation);
                    }
                }
            } else {
                for (Map.Entry<Integer, DBNation> entry : getNations().entrySet()) {
                    DBNation nation = entry.getValue();
                    if (alliances.contains(nation.getAlliance_id())) {
                        nations.add(nation);
                    }
                }
            }
        }
        return nations;
    }

    public List<DBNation> getNations(Set<Integer> alliances, String sort) {
            ArrayList<DBNation> result = new ArrayList<>();
        try (PreparedStatement stmt = prepareQuery("select * FROM NATIONS WHERE `alliance_id` IN " + StringMan.getString(alliances)
                    + (sort != null && !sort.isEmpty() ? " ORDER BY `" + sort + "` DESC" : "")
            )) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(create(rs));
                }
            }
            return result;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    private DBNation create(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        String nation = rs.getString("nation");
        String leader = rs.getString("leader");
        int alliance_id = getInt(rs, "alliance_id");
        String alliance = rs.getString("alliance");
        long last_active = getLong(rs, "active_m");
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

        return new DBNation(id, nation, leader, alliance_id, alliance, last_active, score, infra, cities, avg_infra, warPolicy, domPolicy, soldiers, tanks, aircraft, ships, missiles, nukes, vm_turns, color, off, def, spies, date, alliancePosition, position, continent, project, cityTimer, projectTimer, beigeTimer, espionageFull);
    }

    public Map<Integer, DBNation> getNationsSortedBy(String sort) {
            Map<Integer, DBNation> nationCache = new LinkedHashMap<>();
        try (PreparedStatement stmt = prepareQuery("select * FROM NATIONS"
                    + (sort != null && !sort.isEmpty() ? " ORDER BY `" + sort + "` DESC" : "")
            )) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    DBNation dbValue = create(rs);
                    DBNation existing = nationCache.get(dbValue.getNation());
                    if (existing != null) {
                        existing.fillBlanks(dbValue);
                    } else {
                        nationCache.putIfAbsent(dbValue.getNation_id(), dbValue);
                    }
                }
            }
            return nationCache;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public Integer getAllianceId(String alliance) {
        return getAlliances().inverse().get(alliance);
    }

    public String getAllianceName(int allianceId) {
        return getAlliances().get(allianceId);
    }

    public Set<Alliance> getAlliances(boolean removeUntaxable, boolean removeInactive, boolean removeApplicants, int topX) {
        Map<Integer, List<DBNation>> nations = getNationsByAlliance(removeUntaxable, removeInactive, removeApplicants, true);
        Set<Alliance> alliances = new LinkedHashSet<>();

        int i = 0;
        for (Map.Entry<Integer, List<DBNation>> entry : nations.entrySet()) {
            if (++i > topX) break;
            alliances.add(new Alliance(entry.getKey()));
        }

        return alliances;
    }

    public BiMap<Integer, String> getAlliances() {
        if (getAlliancesCached != null) {
            BiMap<Integer, String> tmp = getAlliancesCached.get();
            if (tmp != null) {
                return tmp;
            }
        }
        BiMap<Integer, String> allianceMap = HashBiMap.create();
        try (PreparedStatement stmt = prepareQuery("select DISTINCT `alliance_id`, `alliance` FROM NATIONS ORDER BY alliance_id DESC")) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int allianceId = rs.getInt("alliance_id");
                    String alliance = rs.getString("alliance");

                    try {
                        allianceMap.putIfAbsent(allianceId, alliance);
                    } catch (IllegalArgumentException ignore) {}
                }
            }
            getAlliancesCached = new SoftReference<>(allianceMap);
            return allianceMap;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public Map<String, List<Map.Entry<Integer, String>>> getCoalitions(Map<String, Set<Integer>> coalitions) {
        Map<Integer, String> alliances = getAlliances();
        Map<String, List<Map.Entry<Integer, String>>> namedCopy = new HashMap<>();
        for (Map.Entry<String, Set<Integer>> entry : coalitions.entrySet()) {
            ArrayList<Map.Entry<Integer, String>> namedAlliances = new ArrayList<>();
            Set<Integer> allianceIds = entry.getValue();
            for (Integer allianceId : allianceIds) {
                String alliance = alliances.getOrDefault(allianceId, allianceId + "");
                namedAlliances.add(new AbstractMap.SimpleEntry<>(allianceId, alliance));
            }
            namedCopy.put(entry.getKey(), namedAlliances);

        }
        return namedCopy;
    }

    public Set<String> getColumns() {
        try (PreparedStatement stmt = getConnection().prepareStatement("PRAGMA table_info(NATIONS);")) {
            Set<String> columns = new HashSet<>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    columns.add(rs.getString("name"));
                }
            }
            return columns;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
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

//    public List<String> getAudits(DBNation nation) {
//
//    }

    public void createTables() {
        {
            String nations = "CREATE TABLE IF NOT EXISTS `NATIONS` (`id` INT NOT NULL PRIMARY KEY, `nation` VARCHAR NOT NULL, leader VARCHAR NOT NULL, alliance_id INT NOT NULL, alliance VARCHAR NOT NULL, active_m INT NOT NULL, score REAL NOT NULL, infra INT, cities INT NOT NULL, avg_infra INT, policy VARCHAR NOT NULL, soldiers INT, tanks INT, aircraft INT, ships INT, missiles INT, nukes INT, vm_turns INT NOT NULL, color VARCHAR NOT NULL, off INT NOT NULL, def INT NOT NULL, money INT, spies INT, date INT, rank INT NOT NULL, position INT NOT NULL, continent INT NOT NULL, projects INT, timer INT, beigeTimer INT, projectTimer INT, espionageFull INT, dompolicy INT)";
            try (Statement stmt = getConnection().createStatement()) {
                stmt.addBatch(nations);
                stmt.executeBatch();
                stmt.clearBatch();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        };
        executeStmt("CREATE INDEX IF NOT EXISTS index_nation ON NATIONS (nation);");
        executeStmt("CREATE INDEX IF NOT EXISTS index_leader ON NATIONS (leader);");
        executeStmt("CREATE INDEX IF NOT EXISTS index_nation_leader ON NATIONS (nation, leader);");

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
        executeStmt("CREATE INDEX IF NOT EXISTS index_meta_id ON NATION_META (id);");
        executeStmt("CREATE INDEX IF NOT EXISTS index_meta_key ON NATION_META (key);");

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
            String milup = "CREATE TABLE IF NOT EXISTS `CITY_INFRA_LAND` (`id` INT NOT NULL, `nation` INT NOT NULL, `infra` INT NOT NULL, `land` INT NOT NULL, PRIMARY KEY(id))";
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

        String treaties = "CREATE TABLE IF NOT EXISTS `treaties` (`aa_from` INT NOT NULL, `aa_to` INT NOT NULL, `type` INT NOT NULL, `date` INT NOT NULL, PRIMARY KEY(aa_from, aa_to))";
        try (Statement stmt = getConnection().createStatement()) {
            stmt.addBatch(treaties);
            stmt.executeBatch();
            stmt.clearBatch();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        executeStmt("CREATE INDEX IF NOT EXISTS index_treaty_id ON `treaties` (aa_from, aa_to);");

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

    public void addMetric(Alliance alliance, AllianceMetric metric, long turn, double value) {
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

    public Map<Alliance, Map<AllianceMetric, Map<Long, Double>>> getMetrics(Set<Integer> allianceIds, AllianceMetric metric, long turnStart, long turnEnd) {
        if (allianceIds.isEmpty()) throw new IllegalArgumentException("No metrics provided");
        String allianceQueryStr = StringMan.getString(allianceIds);
        boolean hasTurnEnd = turnEnd > 0 && turnEnd < Long.MAX_VALUE;

        Map<Alliance, Map<AllianceMetric, Map<Long, Double>>> result = new HashMap<>();

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

                    Alliance alliance = new Alliance(allianceId);
                    result.computeIfAbsent(alliance, f -> new HashMap<>()).computeIfAbsent(metric, f -> new HashMap<>()).put(turn, value);
                }
            }
        });
        return result;
    }

    public Map<Alliance, Map<AllianceMetric, Map<Long, Double>>> getMetrics(Set<Integer> allianceIds, Collection<AllianceMetric> metrics, long turnStart, long turnEnd) {
        if (metrics.isEmpty()) throw new IllegalArgumentException("No metrics provided");
        if (allianceIds.isEmpty()) throw new IllegalArgumentException("No metrics provided");
        String allianceQueryStr = StringMan.getString(allianceIds);
        String metricQueryStr = StringMan.getString(metrics.stream().map(Enum::ordinal).collect(Collectors.toList()));
        boolean hasTurnEnd = turnEnd > 0 && turnEnd < Long.MAX_VALUE;

        Map<Alliance, Map<AllianceMetric, Map<Long, Double>>> result = new HashMap<>();

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

                    Alliance alliance = new Alliance(allianceId);
                    result.computeIfAbsent(alliance, f -> new HashMap<>()).computeIfAbsent(metric, f -> new HashMap<>()).put(turn, value);
                }
            }
        });
        return result;
    }

    public Map<Integer, Map<Integer, CityInfraLand>> updateCities() {
        try {

            Set<Integer> auditNation = new HashSet<>();

            int numPurged = purgeCityInfraLand();
            Map<Integer, CityInfraLand> previous = getCityInfraLand();

            List<SCityContainer> cities = Locutus.imp().getPnwApi().getAllCities().getAllCities();
            // delete cities that no longer exist
            Set<Integer> toRemove = new LinkedHashSet<>(previous.keySet());

            List<CityInfraLand> toUpdate = new ArrayList<>();

            Map<Integer, Map<Integer, CityInfraLand>> currentByNation = new HashMap<>();
            for (SCityContainer city : cities) {
                if (city == null) continue;
                CityInfraLand newCity = new CityInfraLand(city);
                CityInfraLand existing = previous.get(newCity.cityId);

                DBNation nation = getNation(newCity.nationId);

                toRemove.remove(newCity.cityId);

                boolean modified = false;
                double cost = 0;
                if (existing != null) {
                    if (existing.infra + 1 < newCity.infra) {
                        modified = true;
                        cost += PnwUtil.calculateInfra(existing.infra, newCity.infra);

                        if (newCity.infra % 50 != 0) {
                            if (auditNation.add(newCity.nationId)) {
                                if (nation != null) {
                                    AlertUtil.auditAlert(nation, AuditType.UNEVEN_INFRA, new Function<GuildDB, String>() {
                                        @Override
                                        public String apply(GuildDB guildDB) {
                                            int ideal = (int) (newCity.infra - newCity.infra % 50);
                                            String msg = AuditType.UNEVEN_INFRA.message
                                                    .replace("{city}", PnwUtil.getCityUrl(newCity.cityId));
                                            return "You bought uneven infra in <" + PnwUtil.getCityUrl(newCity.cityId) + "> (" + MathMan.format(newCity.infra) + " infra) but only get a building slot every `50` infra.\n" +
                                                    "You can enter e.g. `@" + ideal + "` to buy up to that amount";
                                        }
                                    });
                                }
                            }
                        }

                        if (nation != null && nation.getPosition() > 1) {
                            GuildDB db = Locutus.imp().getGuildDBByAA(nation.getAlliance_id());
                            if (db != null) {
                                try {
                                    db.getHandler().onInfraPurchase(nation, existing, newCity);
                                } catch (Throwable e) {
                                    handleError(e);
                                }
                            }
                        }
                    } else if (existing.infra - 1 > newCity.infra) {
                        modified = true;
                    }
                    if (existing.land + 1 < newCity.land) {
                        modified = true;
                        cost += PnwUtil.calculateLand(existing.land, newCity.land);
                    } else if (existing.land - 1 > newCity.land) {
                        modified = true;
                    }
                } else {
                    modified = true;
                    if (newCity.infra > 10) {
                        cost += PnwUtil.calculateInfra(10, newCity.infra);
                    }
                    if (newCity.land > 250) {
                        cost += PnwUtil.calculateLand(250, newCity.land);
                    }
                }
                if (modified) {
                    toUpdate.add(newCity);

                    if (nation != null && cost != 0) {
                        nation.addExpense(Collections.singletonMap(ResourceType.MONEY, cost));
                    }
                }
                currentByNation.computeIfAbsent(newCity.nationId, f -> new HashMap<>()).put(newCity.cityId, newCity);
            }
            addCityInfraLands(toUpdate);
            deleteCityInfraLands(toRemove);

            return currentByNation;
        } catch (IOException e) {
            AlertUtil.displayTray("Error", "UpdateCities");
            e.printStackTrace();
            return null;
        }
    }

    public int deleteCityInfraLands(Set<Integer> cityIds) {
        return update("DELETE FROM CITY_INFRA_LAND WHERE id in " + StringMan.getString(cityIds), stmt -> {});
    }

    public void addCityInfraLands(Collection<CityInfraLand> cities) {
        synchronized (this) {
            String query = "INSERT OR REPLACE INTO `CITY_INFRA_LAND` (`id`, `nation`, `infra`, `land`) VALUES(?, ?, ?, ?)";
            executeBatch(cities, query, new ThrowingBiConsumer<CityInfraLand, PreparedStatement>() {
                @Override
                public void acceptThrows(CityInfraLand city, PreparedStatement stmt) throws SQLException {
                    stmt.setInt(1, city.cityId);
                    stmt.setInt(2, city.nationId);
                    stmt.setLong(3, (long) (city.infra * 100d));
                    stmt.setLong(4, (long) (city.land * 100d));
                }
            });
        }
    }

    public int purgeCityInfraLand() {
        return update("DELETE FROM CITY_INFRA_LAND WHERE NOT EXISTS(SELECT NULL FROM NATIONS f WHERE f.id = nation)", stmt -> {});
    }

    public Map<Integer, CityInfraLand> getCityInfraLand() {
        try (PreparedStatement stmt = prepareQuery("select * FROM CITY_INFRA_LAND")) {
            Map<Integer, CityInfraLand> map = new LinkedHashMap<>();

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    int nation = rs.getInt("nation");
                    double infra = rs.getLong("infra") / 100d;
                    double land = rs.getLong("land") / 100d;
                    map.put(id, new CityInfraLand(id, nation, infra, land));
                }
            }
            return map;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public Map<Integer, Map<Integer, CityInfraLand>> getCityInfraLand(Set<Integer> nationIds) {
        try (PreparedStatement stmt = prepareQuery("select * FROM CITY_INFRA_LAND WHERE nation in " + StringMan.getString(nationIds))) {
            Map<Integer, Map<Integer, CityInfraLand>> map = new LinkedHashMap<>();

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    CityInfraLand city = new CityInfraLand(rs);
                    map.computeIfAbsent(city.nationId, f -> new LinkedHashMap<>()).put(city.cityId, city);
                }
            }
            return map;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public Map<Integer, CityInfraLand> getCityInfraLand(int nationId) {
        try (PreparedStatement stmt = prepareQuery("select * FROM CITY_INFRA_LAND WHERE nation = ?")) {
            stmt.setInt(1, nationId);
            Map<Integer, CityInfraLand> map = new LinkedHashMap<>();

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    CityInfraLand city = new CityInfraLand(rs);
                    map.put(city.cityId, city);
                }
            }
            return map;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public void updateTopTreaties(int topX) {
        try {
            List<SAllianceContainer> alliances = Locutus.imp().getPnwApi().getAlliances().getAlliances();
            alliances.removeIf(Objects::isNull);
            Collections.sort(alliances, Comparator.comparingDouble(SAllianceContainer::getScore));
            Collections.reverse(alliances);
            for (int i = 0; i < topX && i < alliances.size(); i++) {
                SAllianceContainer alliance = alliances.get(i);
                int id = Integer.parseInt(alliance.getId());
                updateTreaties(id);
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public Map<Integer, Treaty> updateTreaties(int allianceId) {
        if (allianceId == 0) return Collections.emptyMap();
        try {
            String html = FileUtil.readStringFromURL("" + Settings.INSTANCE.PNW_URL() + "/alliance/id=" + allianceId);

            String var = "var edges =";
            int varI = html.indexOf(var);
            int start = html.indexOf('[', varI);
            int end = StringMan.findMatchingBracket(html, start);
            String json = html.substring(start, end + 1)
                    .replaceAll("\\}[ ]*,[ ]*\\]", "}]")
                    .replaceAll("\\][ ]*,[ ]*\\]", "]]");

            long date = System.currentTimeMillis();

            Map<Integer, Treaty> treatyMap = new LinkedHashMap<>();

            if (!json.contains("if lt IE 7")) {
                JsonParser parser = new JsonParser();
                JsonArray treatyJson = parser.parse(json).getAsJsonArray();
                for (JsonElement elem : treatyJson) {
                    JsonObject treatyObj = elem.getAsJsonObject();
                    int from = treatyObj.getAsJsonPrimitive("from").getAsInt();
                    int to = treatyObj.getAsJsonPrimitive("to").getAsInt();
//                String color = treaty.getAsJsonPrimitive("color").getAsString();
                    String title = treatyObj.getAsJsonPrimitive("title").getAsString();
                    TreatyType type = TreatyType.parse(title);
                    Treaty treaty = new Treaty(from, to, type, date);
                    int other = treaty.from == allianceId ? treaty.to : treaty.from;
                    treatyMap.put(other, treaty);
                }
            } else {
                throw new IllegalStateException("Turn change");
            }
            Map<Integer, Treaty> oldTreaties = getTreaties(allianceId, false);

            // delete old treaties
            update("DELETE FROM treaties WHERE aa_from = ? or `aa_to` = ?", new ThrowingConsumer<PreparedStatement>() {
                @Override
                public void acceptThrows(PreparedStatement stmt) throws Exception {
                    stmt.setInt(1, allianceId);
                    stmt.setInt(2, allianceId);
                }
            });
            if (treatyMap.isEmpty()) {
                treatyMap.put(0, new Treaty(allianceId, 0, TreatyType.NONE, date));
            }

            ArrayList<Treaty> treaties = new ArrayList<>(treatyMap.values());
            Set<Integer> duplicates = new HashSet<>();
            for (Iterator<Treaty> iter = treaties.iterator(); iter.hasNext();) {
                Treaty treaty = iter.next();
                int other = treaty.from == allianceId ? treaty.to : treaty.from;
                if (!duplicates.add(other)) iter.remove();
            }

            // add new treaties
            for (Treaty treaty : treaties) {
                update("INSERT OR REPLACE INTO `treaties` (`aa_from`, `aa_to`, 'type', 'date') VALUES(?, ?, ?, ?)", (ThrowingConsumer<PreparedStatement>) stmt2 -> {
                    stmt2.setInt(1, treaty.from);
                    stmt2.setInt(2, treaty.to);
                    stmt2.setInt(3, treaty.type.ordinal());
                    stmt2.setLong(4, treaty.date);
                });
            }

            treatyMap.clear();
            for (Treaty treaty : treaties) {
                int other = treaty.from == allianceId ? treaty.to : treaty.from;
                treatyMap.put(other, treaty);

                if (other != 0) {
                    Treaty previous = oldTreaties.get(other);
                    if (previous == null || !previous.equals(treaty)) {
                        Locutus.post(new TreatyUpdateEvent(previous, treaty));
                    }
                }
            }

            for (Map.Entry<Integer, Treaty> entry : oldTreaties.entrySet()) {
                if (!treatyMap.containsKey(entry.getKey())) {
                    Locutus.post(new TreatyUpdateEvent(entry.getValue(), null));
                }
            }


            return treatyMap;
        } catch (IOException e) {
            e.printStackTrace();
            return Collections.emptyMap();
        }
    }

    public Map<Integer, Treaty> getTreaties(int allianceId, TreatyType... types) {
        Map<Integer, Treaty> treaties = getTreaties(allianceId);
        Set<TreatyType> typesSet = new HashSet<>(Arrays.asList(types));
        treaties.entrySet().removeIf(t -> !typesSet.contains(t.getValue().type));
        return treaties;
    }

    public Map<Integer, Treaty> getTreaties(int allianceId) {
        return getTreaties(allianceId, true);
    }
    public Map<Integer, Treaty> getTreaties(int allianceId, boolean update) {
        if (allianceId == 0 || getAllianceName(allianceId) == null) return Collections.emptyMap();

        try (PreparedStatement stmt = prepareQuery("select * FROM treaties WHERE aa_from = ? or `aa_to` = ?")) {
            stmt.setInt(1, allianceId);
            stmt.setInt(2, allianceId);

            Map<Integer, Treaty> map = new LinkedHashMap<>();

            long now = System.currentTimeMillis();
            long oldest = Long.MAX_VALUE;

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int from = rs.getInt("aa_from");
                    int to = rs.getInt("aa_to");
                    TreatyType type = TreatyType.values[rs.getInt("type")];
                    long date = rs.getLong("date");

                    int other = from == allianceId ? to : from;
                    Treaty treaty = new Treaty(from, to, type, date);
                    if (other == 0) {
                        oldest = treaty.date;
                    }
                    map.put(other, treaty);
                }
            }

            if (update && (map.isEmpty() || now - oldest > TimeUnit.DAYS.toMillis(5))) {
                try {
                    return updateTreaties(allianceId);
                } catch (Exception ignore) {
                    ignore.printStackTrace();
                }
            }
            map.remove(0);
            return map;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
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
        try (PreparedStatement stmt = prepareQuery("select * FROM NATION_LOOT WHERE id = ?")) {
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

    public void setSpyActivity(int nationId, long projects, int spies, long timestamp, long change) {
        update("INSERT OR REPLACE INTO `spy_activity` (`nation`, `timestamp`, `projects`, `change`, `spies`) VALUES(?, ?, ?, ?, ?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, nationId);
            stmt.setLong(2, timestamp);
            stmt.setLong(3, projects);
            stmt.setLong(4, change);
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

            List<DBSpyUpdate> set = new LinkedList<>();

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {

                    DBSpyUpdate entry = new DBSpyUpdate();
                    // `nation`, `timestamp`, `projects`, `change`, `spies`

                    entry.nation_id = rs.getInt("nation");
                    entry.timestamp = rs.getLong("timestamp");
                    entry.projects = rs.getLong("projects");
                    entry.change = rs.getLong("change");
                    entry.spies = rs.getInt("spies");

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

            List<DBSpyUpdate> set = new LinkedList<>();

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {

                    DBSpyUpdate entry = new DBSpyUpdate();
                    // `nation`, `timestamp`, `projects`, `change`, `spies`

                    entry.nation_id = rs.getInt("nation");
                    entry.timestamp = rs.getLong("timestamp");
                    entry.projects = rs.getLong("projects");
                    entry.change = rs.getLong("change");
                    entry.spies = rs.getInt("spies");

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
        Set<DBNation> nationSet = new HashSet<>(Locutus.imp().getNationDB().getNations().values());
        return getNationsByAlliance(nationSet, removeUntaxable, removeInactive, removeApplicants, sorttByScore);
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

    private final Map<Integer, Map<Integer, PWApiV3.CityDataV3>> citiesCache = new Int2ObjectOpenHashMap<>();

    public Map<Integer, JavaCity> toJavaCity(Map<Integer, PWApiV3.CityDataV3> cities) {
        Map<Integer, JavaCity> result = new HashMap<>();
        for (Map.Entry<Integer, PWApiV3.CityDataV3> entry : cities.entrySet()) {
            result.put(entry.getKey(), entry.getValue().toJavaCity(f -> false));
        }
        return result;
    }

    public Map<Integer, JavaCity> getCachedCities(int nationId) {
        synchronized (citiesCache) {
            Map<Integer, PWApiV3.CityDataV3> cached = citiesCache.get(nationId);
            if (cached != null) return toJavaCity(cached);

            DBNation nation = getNation(nationId);
            if (nation != null) {
                return getCities(nation, false, true);
            }

            Map<Integer, PWApiV3.CityDataV3> cities = getCitiesV3(nationId);
            if (cities != null && !cities.isEmpty()) {
                citiesCache.put(nationId, cities);
                return toJavaCity(cities);
            }
            return Collections.emptyMap();
        }
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

    public Map<Integer, Map<Integer, PWApiV3.CityDataV3>> getCitiesV3() {
        try (PreparedStatement stmt = prepareQuery("select * FROM CITY_BUILDS")) {
            Map<Integer, Map<Integer, PWApiV3.CityDataV3>> result = new Int2ObjectOpenHashMap<>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int nationId = rs.getInt("nation");
                    PWApiV3.CityDataV3 data = new PWApiV3.CityDataV3();
                    data.id = rs.getInt("id");
                    data.created = rs.getLong("created");
                    data.infra = rs.getInt("infra") / 100d;
                    data.land = rs.getInt("land") / 100d;
                    data.powered = rs.getBoolean("powered");
                    data.buildings = rs.getBytes("improvements");
                    data.fetched = rs.getLong("update_flag");

                    result.computeIfAbsent(nationId, f -> new HashMap<>()).put(data.id, data);
                }
            }
            return result;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public Map<Integer, Map<Integer, PWApiV3.CityDataV3>> getCitiesV3(Set<Integer> nationIds) {
        Map<Integer, Map<Integer, PWApiV3.CityDataV3>> result = new Int2ObjectOpenHashMap<>();

        Set<Integer> toFetch = new HashSet<>();
        for (Integer nationId : nationIds) {
            synchronized (citiesCache) {
                Map<Integer, PWApiV3.CityDataV3> cached = citiesCache.get(nationId);
                if (cached == null) toFetch.add(nationId);
                else result.put(nationId, cached);
            }
        }

        try (PreparedStatement stmt = prepareQuery("select * FROM CITY_BUILDS WHERE nation in " + StringMan.getString(toFetch))) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int nationId = rs.getInt("nation");
                    PWApiV3.CityDataV3 data = new PWApiV3.CityDataV3(rs);
                    synchronized (citiesCache) {
                        citiesCache.computeIfAbsent(nationId, f -> new Int2ObjectOpenHashMap<>()).put(data.id, data);
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }

        synchronized (citiesCache) {
            for (Integer nationId : toFetch) {
                result.put(nationId, citiesCache.get(nationId));
            }
        }
        return result;
    }

    public Map<Integer, PWApiV3.CityDataV3> getCitiesV3(int nation_id) {
        try (PreparedStatement stmt = prepareQuery("select * FROM CITY_BUILDS WHERE nation = ?")) {
            stmt.setInt(1, nation_id);
            Map<Integer, PWApiV3.CityDataV3> result = new Int2ObjectOpenHashMap<>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
//                    int nationId = rs.getInt("nation");
                    PWApiV3.CityDataV3 data = new PWApiV3.CityDataV3(rs);
                    result.put(data.id, data);
                }
            }
            citiesCache.put(nation_id, result);
            return result;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public PWApiV3.CityDataV3 getCitiesV3ByCityId(int cityId) {
        try (PreparedStatement stmt = prepareQuery("select * FROM CITY_BUILDS WHERE id = ?")) {
            stmt.setInt(1, cityId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
//                    int nationId = rs.getInt("nation");
                    PWApiV3.CityDataV3 data = new PWApiV3.CityDataV3(rs);
                    return data;
                }
            }
            return null;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public void purgeCities() {
        executeStmt("DELETE FROM CITY_BUILDS WHERE nation IN (SELECT CITY_BUILDS.nation FROM CITY_BUILDS LEFT JOIN NATIONS ON CITY_BUILDS.nation=NATIONS.id WHERE NATIONS.id IS NULL)");
    }

    public void addCities(Map<Integer, Map<Integer, PWApiV3.CityDataV3>> citiesByNation) throws IOException, ParseException {
        synchronized (citiesCache) {
//            NationUpdateProcessor.processCities(citiesByNation);

            Set<Integer> nationIds = new HashSet<>(citiesByNation.keySet());
            executeStmt("DELETE FROM CITY_BUILDS WHERE nation in " + StringMan.getString(nationIds));

            List<Map.Entry<Integer, PWApiV3.CityDataV3>> cities = new ArrayList<>();

            for (Map.Entry<Integer, Map<Integer, PWApiV3.CityDataV3>> entry : citiesByNation.entrySet()) {
                int nationId = entry.getKey();
                for (Map.Entry<Integer, PWApiV3.CityDataV3> cityEntry : entry.getValue().entrySet()) {
                    cities.add(new AbstractMap.SimpleEntry<>(nationId, cityEntry.getValue()));
                }
            }

            executeBatch(cities, "INSERT OR REPLACE INTO `CITY_BUILDS`(`id`, `nation`, `created`, `infra`, `land`, `powered`, `improvements`, `update_flag`) VALUES(?, ?, ?, ?, ?, ?, ?, ?)", new ThrowingBiConsumer<Map.Entry<Integer, PWApiV3.CityDataV3>, PreparedStatement>() {
                @Override
                public void acceptThrows(Map.Entry<Integer, PWApiV3.CityDataV3> entry, PreparedStatement stmt) throws Exception {
                    int nationId = entry.getKey();
                    PWApiV3.CityDataV3 city = entry.getValue();
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
    }

    public Map<Integer, JavaCity> getCities(DBNation nation, boolean updateIfOutdated, boolean force) {
        return getCities(nation, updateIfOutdated, true, force);
    }

    public Map<Integer, JavaCity> getCities(DBNation nation, boolean updateIfOutdated, boolean updateIfIncorrectSize, boolean force) {
        synchronized (citiesCache) {
            int numCities = nation.getCities();
            long now = System.currentTimeMillis();

            Map<Integer, PWApiV3.CityDataV3> cached = citiesCache.get(nation.getNation_id());
            Map<Integer, JavaCity> cities;

            long lastActive = now - TimeUnit.MINUTES.toMillis(nation.getActive_m() + 15);
            boolean outdated = true;

            if (cached == null || cached.isEmpty()) {
                cached = getCitiesV3(nation.getNation_id());

            }
            if (!cached.isEmpty()) {
                cities = toJavaCity(cached);
                long lastCityUpdated = 0;
                for (Map.Entry<Integer, PWApiV3.CityDataV3> entry : cached.entrySet()) {
                    lastCityUpdated = Math.max(lastCityUpdated, entry.getValue().fetched);
                }
                outdated = lastActive > lastCityUpdated;
            } else {
                cities = new HashMap<>();
            }

            double currentInfra = nation.getAvg_infra() * nation.getCities();
            if (cities.isEmpty() || (((outdated && updateIfOutdated) || cities.size() != numCities)) || force) {
                System.out.println("Fetch cities. outdated: " + outdated + " updateIfOutdated: " + updateIfOutdated + " size: " + cities.size() + "-" + numCities + " | force: " + force);
                try {
                    Map<Integer, JavaCity> previous = cities;
                    cities = new GetCityBuilds(nation).adapt().get(nation);

                    if (!cities.isEmpty()) {
                        if (previous != null) {
                            for (Map.Entry<Integer, JavaCity> cityEntry : previous.entrySet()) {
                                if (!cities.containsKey(cityEntry.getKey())) {
                                    NationUpdateProcessor.processCity(cityEntry.getValue(), null, cityEntry.getKey(), nation);
                                }
                            }
                        }
                        for (Map.Entry<Integer, JavaCity> cityEntry : cities.entrySet()) {
                            JavaCity previousCity = previous.get(cityEntry.getKey());
                            JavaCity currentCity = cityEntry.getValue();
                            if (!currentCity.equals(previousCity)) {
                                NationUpdateProcessor.processCity(previousCity, currentCity, cityEntry.getKey(), nation);
                            }
                        }

                    }

                    if (cities.size() != numCities) {
                        synchronized (this) {
                            try (PreparedStatement stmt = getConnection().prepareStatement("DELETE FROM CITIES WHERE `nation` = ?")) {
                                stmt.setInt(1, nation.getNation_id());
                                stmt.executeUpdate();
                            }
                        }
                    }
                    Map<Integer, PWApiV3.CityDataV3> v3Cities = toCityV3(cities);
                    Map<Integer, Map<Integer, PWApiV3.CityDataV3>> v3CitiesNationMap = Collections.singletonMap(nation.getNation_id(), v3Cities);
                    addCities(v3CitiesNationMap);

                    citiesCache.put(nation.getNation_id(), v3Cities);
                } catch (IOException | ExecutionException | InterruptedException | SQLException | ParseException e) {
                    e.printStackTrace();
                    if (!cities.isEmpty()) return cities;
                    throw new RuntimeException(e);
                }
            }
            return cities;
        }
    }

    private Map<Integer, PWApiV3.CityDataV3> toCityV3(Map<Integer, JavaCity> cities) {
        Map<Integer, PWApiV3.CityDataV3> result = new HashMap<>();
        for (Map.Entry<Integer, JavaCity> entry : cities.entrySet()) {
            JavaCity city = entry.getValue();
            PWApiV3.CityDataV3 v3 = new PWApiV3.CityDataV3(entry.getKey(), city);
            result.put(entry.getKey(), v3);
        }
        return result;
    }

//    private void addCity(int nationId, int cityId, JavaCity city) {
//        update("INSERT OR REPLACE INTO `CITIES`(`id`, `nation`, `created`, `land`, `improvements`, `update_flag`) VALUES(?, ?, ?, ?, ?, ?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
//            stmt.setInt(1, cityId);
//            stmt.setInt(2, nationId);
//            long dayCreated = TimeUtil.getDay() - city.getAge();
//            stmt.setLong(3, dayCreated);
//            stmt.setInt(4, (int) (city.getLand() * 100));
//            stmt.setBytes(5, city.toBytes());
//            stmt.setLong(6, System.currentTimeMillis());
//        });
//    }

    public synchronized void updateNations(Map<Integer, DBNation> nations) {
        int count = 0;
        if (nationCache.isEmpty()) {
            getNations(nations);
        }
        for (Map.Entry<Integer, DBNation> entry : nations.entrySet()) {
            Integer id = entry.getKey();
            DBNation updated = entry.getValue();

            DBNation cached = nationCache.get(id);
            if (cached != null) {
                updated.fillBlanks(cached);
                cached.set(updated);
            } else {
                nationCache.put(id, updated);
            }
            if (updated.hasUnsetMil()) {
                count++;
            }
        }
        addNations(nations.values());
    }

    public void addNation(DBNation nation) {
        addNation(nation.getNation_id(), nation.getNation(), nation.getLeader(), nation.getAlliance_id(), nation.getAlliance(), nation.lastActiveMs(), nation.getScore(), nation.getInfra(), nation.getCities(), nation.getAvg_infra(), nation.getWarPolicy(), nation.getDomesticPolicy(),
                nation.getSoldiers(), nation.getTanks(), nation.getAircraft(), nation.getShips(), nation.getMissiles(), nation.getNukes(), nation.getVm_turns(), nation.getColor(), nation.getOff(), nation.getDef(), nation.getSpies(),
                nation.getDate(), nation.getAlliancePosition(), nation.getPosition(), nation.getContinent(), nation.getProjectBitMask(), nation.getCityTimerEpoch(), nation.getProjectTimerEpoch(), nation.getBeigeTimer(), nation.getEspionageFullTurn());
    }

    public void addNations(Collection<DBNation> nations) {
        try {
            synchronized (this) {
                String query = "INSERT OR REPLACE INTO `NATIONS`(`id`, `nation`, `leader`, `alliance_id`, `alliance`, `active_m`, `score`, `infra`, `cities`, `avg_infra`, `policy`, `soldiers`, `tanks`, `aircraft`, `ships`, `missiles`, `nukes`, `vm_turns`, `color`, `off`, `def`, `money`, `spies`, `date`, `rank`, `position`, `continent`, `projects`, `timer`, `beigeTimer`, `projectTimer`, `espionageFull`, `dompolicy`) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                executeBatch(nations, query, new ThrowingBiConsumer<DBNation, PreparedStatement>() {
                    @Override
                    public void acceptThrows(DBNation nation, PreparedStatement stmt) throws SQLException {
                        stmt.setInt(1, nation.getNation_id());
                        stmt.setString(2, nation.getNation());
                        stmt.setString(3, nation.getLeader());
                        stmt.setInt(4, nation.getAlliance_id());
                        stmt.setString(5, nation.getAlliance());
                        stmt.setInt(6, nation.getActive_m());
                        stmt.setDouble(7, nation.getScore());
                        if (nation.getInfra() != null) stmt.setInt(8, nation.getInfra());
                        else stmt.setNull(8, Types.INTEGER);
                        stmt.setInt(9, nation.getCities());
                        if (nation.getAvg_infra() != null) stmt.setInt(10, nation.getAvg_infra());
                        else stmt.setNull(10, Types.INTEGER);
                        stmt.setString(11, nation.getWarPolicy().name().toLowerCase());
                        if (nation.getSoldiers() != null) stmt.setInt(12, nation.getSoldiers());
                        else stmt.setNull(12, Types.INTEGER);
                        if (nation.getTanks() != null) stmt.setInt(13, nation.getTanks());
                        else stmt.setNull(13, Types.INTEGER);
                        if (nation.getAircraft() != null) stmt.setInt(14, nation.getAircraft());
                        else stmt.setNull(14, Types.INTEGER);
                        if (nation.getShips() != null) stmt.setInt(15, nation.getShips());
                        else stmt.setNull(15, Types.INTEGER);
                        if (nation.getMissiles() != null) stmt.setInt(16, nation.getMissiles());
                        else stmt.setNull(16, Types.INTEGER);
                        if (nation.getNukes() != null) stmt.setInt(17, nation.getNukes());
                        else stmt.setNull(17, Types.INTEGER);
                        stmt.setInt(18, nation.getVm_turns());
                        stmt.setString(19, nation.getColor().name().toLowerCase());
                        stmt.setInt(20, nation.getOff());
                        stmt.setInt(21, nation.getDef());
//                        if (nation.getMoney() != null) stmt.setLong(22, nation.getMoney());
                        /*else */stmt.setNull(22, Types.INTEGER);
                        if (nation.getSpies() != null) stmt.setInt(23, nation.getSpies());
                        else stmt.setNull(23, Types.INTEGER);
                        if (nation.getDate() != null) stmt.setLong(24, nation.getDate());
                        else stmt.setNull(24, Types.INTEGER);

                        stmt.setInt(25, nation.getAlliancePosition());
                        stmt.setInt(26, nation.getPosition());
                        stmt.setInt(27, nation.getContinent().ordinal());
                        stmt.setLong(28, nation.getProjectBitMask());

                        if (nation.getCityTimerEpoch() != null) stmt.setLong(29, nation.getCityTimerEpoch());
                        else stmt.setNull(29, Types.INTEGER);

                        if (nation.getBeigeTimer() != null) stmt.setLong(30, nation.getBeigeTimer());
                        else stmt.setNull(30, Types.INTEGER);

                        if (nation.getProjectTimerEpoch() != null) stmt.setLong(31, nation.getProjectTimerEpoch());
                        else stmt.setNull(31, Types.INTEGER);

                        stmt.setLong(32, nation.getEspionageFullTurn());
                        stmt.setString(33, nation.getDomesticPolicy().name().toLowerCase());
                    }
                });
            }
        } catch (Throwable e) {
            e.printStackTrace();
            for (DBNation nation : nations) {
                addNation(nation);
            }
        }
    }

    public void addNation(int nation_id, String nation, String leader, int alliance_id, String alliance, long last_active, double score, Integer infra, int cities, Integer avg_infra, WarPolicy warPolicy, DomesticPolicy domPolicy, Integer soldiers, Integer tanks, Integer aircraft, Integer ships, Integer missiles, Integer nukes, int vm_turns, NationColor color, int off, int def, Integer spies, Long date, int alliancePosition, int position, Continent continent, long projects, Long cityTimer, Long projectTimer, Long beigeTimer, long espionageFullTurn) {
        markDirty();
        synchronized (this) {
            update("INSERT OR REPLACE INTO `NATIONS`(`id`, `nation`, `leader`, `alliance_id`, `alliance`, `active_m`, `score`, `infra`, `cities`, `avg_infra`, `policy`, `soldiers`, `tanks`, `aircraft`, `ships`, `missiles`, `nukes`, `vm_turns`, `color`, `off`, `def`, `money`, `spies`, `date`, `rank`, `position`, `continent`, `projects`, `timer`, `beigeTimer`, `projectTimer`, `espionageFull`, `dompolicy`) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
                stmt.setInt(1, nation_id);
                stmt.setString(2, nation);
                stmt.setString(3, leader);
                stmt.setInt(4, alliance_id);
                stmt.setString(5, alliance);
                stmt.setLong(6, last_active);
                stmt.setDouble(7, score);
                if (infra != null) stmt.setInt(8, infra);
                else stmt.setNull(8, Types.INTEGER);
                stmt.setInt(9, cities);
                if (avg_infra != null) stmt.setInt(10, avg_infra);
                else stmt.setNull(10, Types.INTEGER);
                stmt.setString(11, warPolicy.name().toLowerCase());
                if (soldiers != null) stmt.setInt(12, soldiers);
                else stmt.setNull(12, Types.INTEGER);
                if (tanks != null) stmt.setInt(13, tanks);
                else stmt.setNull(13, Types.INTEGER);
                if (aircraft != null) stmt.setInt(14, aircraft);
                else stmt.setNull(14, Types.INTEGER);
                if (ships != null) stmt.setInt(15, ships);
                else stmt.setNull(15, Types.INTEGER);
                if (missiles != null) stmt.setInt(16, missiles);
                else stmt.setNull(16, Types.INTEGER);
                if (nukes != null) stmt.setInt(17, nukes);
                else stmt.setNull(17, Types.INTEGER);
                stmt.setInt(18, vm_turns);
                stmt.setString(19, color.name().toLowerCase());
                stmt.setInt(20, off);
                stmt.setInt(21, def);
//                if (money != null) stmt.setLong(22, money);
                stmt.setNull(22, Types.INTEGER);
                if (spies != null) stmt.setInt(23, spies);
                else stmt.setNull(23, Types.INTEGER);
                if (date != null) stmt.setLong(24, date);
                else stmt.setNull(24, Types.INTEGER);

                stmt.setInt(25, alliancePosition);
                stmt.setInt(26, position);
                stmt.setInt(27, continent.ordinal());
                stmt.setLong(28, projects);

                if (cityTimer != null) stmt.setLong(29, cityTimer);
                else stmt.setNull(29, Types.INTEGER);

                if (beigeTimer != null) stmt.setLong(30, beigeTimer);
                else stmt.setNull(30, Types.INTEGER);

                if (projectTimer != null) stmt.setLong(31, projectTimer);
                else stmt.setNull(31, Types.INTEGER);

                stmt.setLong(32, espionageFullTurn);
                stmt.setString(33, domPolicy.name().toLowerCase());
            });
            markDirty();
        }
    }
    public Map<Integer, DBNation> getNations() {
        Map<Integer, DBNation> tmp = nationCache;
        if (!tmp.isEmpty()) {
            return Collections.unmodifiableMap(tmp);
        }
        return getNations(new HashMap<>());
    }

    public Map<Integer, DBNation> getNations(Map<Integer, DBNation> keep) {
        return getNations(keep, true);
    }

    public Map<Integer, DBNation> getNations(Map<Integer, DBNation> keep, boolean delete) {
        boolean empty = keep.isEmpty();
        Map<Integer, DBNation> deletIds = new HashMap<>();

        synchronized (this) {
            try (PreparedStatement stmt = prepareQuery("select * FROM NATIONS")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        DBNation dbValue = create(rs);
                        int id = dbValue.getNation_id();

                        if (empty || (keep.containsKey(id))) {
                            DBNation existing = nationCache.get(id);
                            if (existing != null) {
                                existing.fillBlanks(dbValue);
                            } else {
                                nationCache.putIfAbsent(id, dbValue);
                            }
                        } else if (delete) {
                            deletIds.put(id, dbValue);
                        }
                    }
                }
                stmt.close();
                if (!deletIds.isEmpty()) {
                    for (Map.Entry<Integer, DBNation> entry : deletIds.entrySet()) {
                        DBNation nation = entry.getValue();
                        NationUpdateProcessor.processDeletion(nation, null);
                        nationCache.remove(nation.getNation_id());
                    }
                    Set<Integer> deletIdsKeys = deletIds.keySet();
                    try (PreparedStatement stmt2 = getConnection().prepareStatement("DELETE FROM NATIONS WHERE `id` in " + StringMan.getString(deletIdsKeys))) {
                        stmt2.executeUpdate();
                    }
                }
                return Collections.unmodifiableMap(nationCache);
            } catch (SQLException e) {
                e.printStackTrace();
                return null;
            }
        }
    }
}
