package link.locutus.discord.db;

import com.politicsandwar.graphql.model.*;
import com.ptsmods.mysqlw.query.QueryCondition;
import com.ptsmods.mysqlw.query.QueryOrder;
import com.ptsmods.mysqlw.query.builder.SelectBuilder;
import com.ptsmods.mysqlw.table.ColumnType;
import com.ptsmods.mysqlw.table.TablePreset;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.*;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.apiv3.PoliticsAndWarV3;
import link.locutus.discord.apiv3.enums.NationLootType;
import link.locutus.discord.commands.rankings.builder.RankBuilder;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.*;
import link.locutus.discord.db.entities.Treaty;
import link.locutus.discord.db.handlers.ActiveWarHandler;
import link.locutus.discord.event.Event;
import link.locutus.discord.event.city.CityNukeEvent;
import link.locutus.discord.event.nation.NationChangeColorEvent;
import link.locutus.discord.event.war.AttackEvent;
import link.locutus.discord.event.bounty.BountyCreateEvent;
import link.locutus.discord.event.bounty.BountyRemoveEvent;
import link.locutus.discord.event.war.WarStatusChangeEvent;
import link.locutus.discord.util.scheduler.ThrowingBiConsumer;
import link.locutus.discord.util.scheduler.ThrowingConsumer;
import link.locutus.discord.util.AlertUtil;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.math.ArrayUtil;
import link.locutus.discord.util.update.NationUpdateProcessor;
import link.locutus.discord.util.update.WarUpdateProcessor;
import link.locutus.discord.apiv1.domains.subdomains.DBAttack;
import link.locutus.discord.apiv1.domains.subdomains.SWarContainer;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class WarDB extends DBMainV2 {


    private ActiveWarHandler activeWars = new ActiveWarHandler();
    private Map<Integer, DBWar> warsById;
    private Map<Integer, Map<Integer, DBWar>> warsByAllianceId;
    private Map<Integer, Map<Integer, DBWar>> warsByNationId;

    private final Queue<DBAttack> allAttacks = new ConcurrentLinkedQueue<>();
    public WarDB() throws SQLException {
        super(Settings.INSTANCE.DATABASE, "war", true);
    }

    public void load() {
        warsById = getWars();
        warsByAllianceId = new Int2ObjectOpenHashMap<>();
        warsByNationId = new Int2ObjectOpenHashMap<>();
        for (Map.Entry<Integer, DBWar> entry : warsById.entrySet()) {
            DBWar war = entry.getValue();
            if (war.attacker_aa != 0) warsByAllianceId.computeIfAbsent(war.attacker_aa, f -> new Int2ObjectOpenHashMap<>()).put(war.warId, war);
            if (war.defender_aa != 0) warsByAllianceId.computeIfAbsent(war.defender_aa, f -> new Int2ObjectOpenHashMap<>()).put(war.warId, war);
            warsByNationId.computeIfAbsent(war.attacker_id, f -> new Int2ObjectOpenHashMap<>()).put(war.warId, war);
            warsByNationId.computeIfAbsent(war.defender_id, f -> new Int2ObjectOpenHashMap<>()).put(war.warId, war);
        }

        List<DBWar> wars = getWarByStatus(WarStatus.ACTIVE, WarStatus.ATTACKER_OFFERED_PEACE, WarStatus.DEFENDER_OFFERED_PEACE);

        long currentTurn = TimeUtil.getTurn();
        for (DBWar war : wars) {
            long warTurn = TimeUtil.getTurn(war.date);
            if (currentTurn - warTurn < 60) {
                activeWars.addActiveWar(war);
            }
        }
    }

    public Map<Integer, DBWar> getWarsForNationOrAlliance(Predicate<Integer> nations, Predicate<Integer> alliances, Predicate<DBWar> warFilter) {
        Map<Integer, DBWar> result = new Int2ObjectOpenHashMap<>();
        if (alliances != null) {
            synchronized (warsByAllianceId) {
                for (Map.Entry<Integer, Map<Integer, DBWar>> entry : warsByAllianceId.entrySet()) {
                    if (alliances.test(entry.getKey())) {
                        if (warFilter != null) {
                            for (Map.Entry<Integer, DBWar> warEntry : entry.getValue().entrySet()) {
                                if (warFilter.test(warEntry.getValue())) {
                                    result.put(warEntry.getKey(), warEntry.getValue());
                                }
                            }
                        } else {
                            result.putAll(entry.getValue());
                        }
                    }
                }
            }
        }
        if (nations != null) {
            synchronized (warsByNationId) {
                for (Map.Entry<Integer, Map<Integer, DBWar>> entry : warsByNationId.entrySet()) {
                    if (nations.test(entry.getKey())) {
                        if (warFilter != null) {
                            for (Map.Entry<Integer, DBWar> warEntry : entry.getValue().entrySet()) {
                                if (warFilter.test(warEntry.getValue())) {
                                    result.put(warEntry.getKey(), warEntry.getValue());
                                }
                            }
                        } else {
                            result.putAll(entry.getValue());
                        }
                    }
                }
            }
        }
        else if (alliances == null) {
            synchronized (warsById) {
                if (warFilter == null) {

                    result.putAll(warsById);
                } else {
                    for (Map.Entry<Integer, DBWar> warEntry : warsById.entrySet()) {
                        if (warFilter.test(warEntry.getValue())) {
                            result.put(warEntry.getKey(), warEntry.getValue());
                        }
                    }
                }
            }
        }
        return result;
    }

    public Map<Integer, DBWar> getWars(Predicate<DBWar> filter) {
        return getWarsForNationOrAlliance(null, null, filter);
    }

    public void loadNukeDates() {
        long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(12);

        List<Integer> attackIds = new ArrayList<>();
        query("SELECT war_attack_id FROM attacks2 WHERE `date` > ? AND attack_type = ? AND `success` != 0", new ThrowingConsumer<PreparedStatement>() {
            @Override
            public void acceptThrows(PreparedStatement stmt) throws Exception {
                stmt.setLong(1, cutoff);
                stmt.setInt(2, AttackType.NUKE.ordinal());
            }
        }, new ThrowingConsumer<ResultSet>() {
            @Override
            public void acceptThrows(ResultSet rs) throws Exception {
                while (rs.next()) {
                    int id = rs.getInt(1);
                    attackIds.add(id);
                }
            }
        });
        if (attackIds.isEmpty()) return;//no nule data?s

        for (int i = 0; i < attackIds.size(); i += 500) {
            List<Integer> subList = attackIds.subList(i, Math.min(i + 500, attackIds.size()));
            for (WarAttack attack : Locutus.imp().getV3().fetchAttacks(f -> f.setId(subList), new Consumer<WarAttackResponseProjection>() {
                @Override
                public void accept(WarAttackResponseProjection proj) {
                    proj.def_id();
                    proj.city_id();
                    proj.date();
                }
            })) {
                int nationId = attack.getDef_id();
                int cityId = attack.getCity_id();
                long date = attack.getDate().toEpochMilli();
                Locutus.imp().getNationDB().setCityNukeFromAttack(nationId, cityId, date, null);
            }
        }
    }

    @Override
    public void createTables() {
        {
            TablePreset.create("BOUNTIES_V3")
                    .putColumn("id", ColumnType.INT.struct().setPrimary(true).setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("date", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("nation_id", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("posted_by", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("attack_type", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("amount", ColumnType.BIGINT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .create(getDb());

            String subCatQuery = TablePreset.create("ATTACK_SUBCATEGORY_CACHE")
                    .putColumn("attack_id", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("subcategory_id", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("war_id", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .buildQuery(getDb().getType());
            subCatQuery = subCatQuery.replace(");", ", PRIMARY KEY(attack_id, subcategory_id));");
            getDb().executeUpdate(subCatQuery);
        }

        {
            String create = "CREATE TABLE IF NOT EXISTS `WARS` (`id` INT NOT NULL PRIMARY KEY, `attacker_id` INT NOT NULL, `defender_id` INT NOT NULL, `attacker_aa` INT NOT NULL, `defender_aa` INT NOT NULL, `war_type` INT NOT NULL, `status` INT NOT NULL, `date` BIGINT NOT NULL)";
            try (Statement stmt = getConnection().createStatement()) {
                stmt.addBatch(create);
                stmt.executeBatch();
                stmt.clearBatch();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        };

        {
            String create = "CREATE TABLE IF NOT EXISTS `BLOCKADED` (`blockader`, `blockaded`, PRIMARY KEY(`blockader`, `blockaded`))";
            try (Statement stmt = getConnection().createStatement()) {
                stmt.addBatch(create);
                stmt.executeBatch();
                stmt.clearBatch();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        };

        executeStmt("CREATE INDEX IF NOT EXISTS index_WARS_date ON WARS (date);");
        executeStmt("CREATE INDEX IF NOT EXISTS index_WARS_attacker ON WARS (attacker_id);");
        executeStmt("CREATE INDEX IF NOT EXISTS index_WARS_defender ON WARS (defender_id);");
        executeStmt("CREATE INDEX IF NOT EXISTS index_WARS_status ON WARS (status);");

        {
            String create = "CREATE TABLE IF NOT EXISTS `COUNTER_STATS` (`id` INT NOT NULL PRIMARY KEY, `type` INT NOT NULL, `active` INT NOT NULL)";
            try (Statement stmt = getConnection().createStatement()) {
                stmt.addBatch(create);
                stmt.executeBatch();
                stmt.clearBatch();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        };

        {
            String nations = "CREATE TABLE IF NOT EXISTS `attacks2` (" +
                    "`war_attack_id` INT NOT NULL PRIMARY KEY, " +
                    "`date` BIGINT NOT NULL, " +
                    "war_id INT NOT NULL, " +
                    "attacker_nation_id INT NOT NULL, " +
                    "defender_nation_id INT NOT NULL, " +
                    "attack_type INT NOT NULL, " +
                    "victor INT NOT NULL, " +
                    "success INT NOT NULL," +
                    "attcas1 INT NOT NULL," +
                    "attcas2 INT NOT NULL," +
                    "defcas1 INT NOT NULL," +
                    "defcas2 INT NOT NULL," +
                    "defcas3 INT NOT NULL," +
                    "city_id INT NOT NULL," + // Not used anymore
                    "infra_destroyed INT," +
                    "improvements_destroyed INT," +
                    "money_looted BIGINT," +
                    "looted INT," +
                    "loot BLOB," +
                    "pct_looted INT," +
                    "city_infra_before INT," +
                    "infra_destroyed_value INT," +
                    "att_gas_used INT," +
                    "att_mun_used INT," +
                    "def_gas_used INT," +
                    "def_mun_used INT" +
                    ")";
            try (Statement stmt = getConnection().createStatement()) {
                stmt.addBatch(nations);
                stmt.executeBatch();
                stmt.clearBatch();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        };
        executeStmt("CREATE INDEX IF NOT EXISTS index_attack_warid ON attacks2 (war_id);");
        executeStmt("CREATE INDEX IF NOT EXISTS index_attack_attacker_nation_id ON attacks2 (attacker_nation_id);");
        executeStmt("CREATE INDEX IF NOT EXISTS index_attack_defender_nation_id ON attacks2 (defender_nation_id);");
        executeStmt("CREATE INDEX IF NOT EXISTS index_attack_date ON attacks2 (date);");
    }

    public Set<DBWar> getActiveWars() {
        return activeWars.getActiveWars();
    }

    public List<DBWar> getActiveWars(int nationId) {
        return activeWars.getActiveWars(nationId);
    }

    public Map<Integer, DBWar> getActiveWars(Predicate<Integer> nationId, Predicate<DBWar> warPredicate) {
        return activeWars.getActiveWars(nationId, warPredicate);
    }

    public void addSubCategory(List<WarAttackSubcategoryEntry> entries) {
        if (entries.isEmpty()) return;
        String query = "INSERT OR IGNORE INTO `ATTACK_SUBCATEGORY_CACHE`(`attack_id`, `subcategory_id`, `war_id`) VALUES(?, ?, ?)";

        ThrowingBiConsumer<WarAttackSubcategoryEntry, PreparedStatement> setStmt = (entry, stmt) -> {
            stmt.setInt(1, entry.attack_id);
            stmt.setLong(2, entry.subcategory.ordinal());
            stmt.setInt(3, entry.war_id);
        };
        if (entries.size() == 1) {
            WarAttackSubcategoryEntry value = entries.iterator().next();
            update(query, stmt -> setStmt.accept(value, stmt));
        } else {
            executeBatch(entries, query, setStmt);
        }
    }

    public void deleteBlockaded(int blockaded) {
        update("DELETE FROM BLOCKADED WHERE blockaded = ?", new ThrowingConsumer<PreparedStatement>() {
            @Override
            public void acceptThrows(PreparedStatement stmt) throws Exception {
                stmt.setInt(1, blockaded);
            }
        });
    }

    public void deleteBlockaded(int blockaded, int blockader) {
        if (blockadedMap != null) {
            synchronized (blockadeLock) {
                blockadedMap.getOrDefault(blockaded, Collections.emptySet()).remove(blockader);
                blockaderMap.getOrDefault(blockader, Collections.emptySet()).remove(blockaded);
            }
        }
        update("DELETE FROM BLOCKADED WHERE blockaded = ? AND blockader = ?", new ThrowingConsumer<PreparedStatement>() {
            @Override
            public void acceptThrows(PreparedStatement stmt) throws Exception {
                stmt.setInt(1, blockaded);
                stmt.setInt(2, blockader);
            }
        });
    }

    public void addBlockaded(int blockaded, int blockader) {
        if (blockadedMap != null) {
            synchronized (blockadeLock) {
                blockadedMap.computeIfAbsent(blockaded, f -> new HashSet<>()).add(blockader);
                blockaderMap.computeIfAbsent(blockader, f -> new HashSet<>()).add(blockaded);
            }
        }
        update("INSERT OR REPLACE INTO `BLOCKADED`(`blockaded`, `blockader`) VALUES(?,?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, blockaded);
            stmt.setInt(2, blockader);
        });
    }

    private Object blockadeLock = new Object();
    private Map<Integer, Set<Integer>> blockadedMap = null;
    private Map<Integer, Set<Integer>> blockaderMap = null;

    public Map<Integer, Set<Integer>> getBlockadedByNation(boolean update) {
        updateBlockaded(update);
        return blockadedMap;
    }

    public Map<Integer, Set<Integer>> getBlockaderByNation(boolean update) {
        updateBlockaded(update);
        return blockaderMap;
    }

    public void updateBlockaded(boolean force) {
        if (!force && blockadedMap != null) {
            return;
        }
        Map<Integer, Set<Integer>> blockadedMap = new ConcurrentHashMap<>();
        Map<Integer, Set<Integer>> blockaderMap = new ConcurrentHashMap<>();
        try (PreparedStatement stmt= prepareQuery("SELECT * FROM BLOCKADED")) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int blockader = rs.getInt("blockader");
                    int blockaded = rs.getInt("blockaded");

                    blockadedMap.computeIfAbsent(blockaded, f -> new HashSet<>()).add(blockader);
                    blockaderMap.computeIfAbsent(blockader, f -> new HashSet<>()).add(blockaded);
                }
            }
            synchronized (blockadeLock) {
                this.blockadedMap = blockadedMap;
                this.blockaderMap = blockaderMap;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * active prob / inactive prob (0-1)
     * @param allianceId
     * @return
     */
    public Map.Entry<Double, Double> getAACounterStats(int allianceId) {
        List<Map.Entry<DBWar, CounterStat>> counters = Locutus.imp().getWarDb().getCounters(Collections.singleton(allianceId));
        if (counters.isEmpty()) {
            for (Map.Entry<Integer, Treaty> entry : Locutus.imp().getNationDB().getTreaties(allianceId).entrySet()) {
                Treaty treaty = entry.getValue();
                switch (treaty.getType()) {
                    case MDP:
                    case MDOAP:
                    case ODP:
                    case ODOAP:
                    case PROTECTORATE:
                        int other = treaty.getFromId() == allianceId ? treaty.getToId() : treaty.getFromId();
                        counters.addAll(Locutus.imp().getWarDb().getCounters(Collections.singleton(other)));
                }
            }
            if (counters.isEmpty()) return null;
        }

        int[] uncontested = new int[2];
        int[] countered = new int[2];
        int[] counter = new int[2];
        for (Map.Entry<DBWar, CounterStat> entry : counters) {
            CounterStat stat = entry.getValue();
            DBWar war = entry.getKey();
            switch (stat.type) {
                case ESCALATION:
                case IS_COUNTER:
                    countered[stat.isActive ? 1 : 0]++;
                    continue;
                case UNCONTESTED:
                    if (war.status == WarStatus.ATTACKER_VICTORY) {
                        uncontested[stat.isActive ? 1 : 0]++;
                    } else {
                        counter[stat.isActive ? 1 : 0]++;
                    }
                    break;
                case GETS_COUNTERED:
                    counter[stat.isActive ? 1 : 0]++;
                    break;
            }
        }

        int totalActive = counter[1] + uncontested[1];
        int totalInactive = counter[0] + uncontested[0];

        double chanceActive = ((double) counter[1] + 1) / (totalActive + 1);
        double chanceInactive = ((double) counter[0] + 1) / (totalInactive + 1);

        if (!Double.isFinite(chanceActive)) chanceActive = 0.5;
        if (!Double.isFinite(chanceInactive)) chanceInactive = 0.5;

        return new AbstractMap.SimpleEntry<>(chanceActive, chanceInactive);
    }

    public List<Map.Entry<DBWar, CounterStat>> getCounters(Collection<Integer> alliances) {
        Map<Integer, DBWar> wars = getWarsForNationOrAlliance(null, alliances::contains, f -> alliances.contains(f.defender_aa));
        String queryStr = "SELECT * FROM COUNTER_STATS id IN " + StringMan.getString(wars.values().stream().map(f -> f.warId).collect(Collectors.toList()));
        try (PreparedStatement stmt= getConnection().prepareStatement(queryStr)) {
            try (ResultSet rs = stmt.executeQuery()) {
                List<Map.Entry<DBWar, CounterStat>> result = new ArrayList<>();
                while (rs.next()) {
                    int id = rs.getInt("id");
                    CounterStat stat = new CounterStat();
                    stat.isActive = rs.getBoolean("active");
                    stat.type = CounterType.values[rs.getInt("type")];
                    DBWar war = create(rs);
                    AbstractMap.SimpleEntry<DBWar, CounterStat> entry = new AbstractMap.SimpleEntry<>(war, stat);
                    result.add(entry);
                }
                return result;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public CounterStat getCounterStat(DBWar war) {
        try (PreparedStatement stmt= prepareQuery("SELECT * FROM COUNTER_STATS WHERE id = ?")) {
            stmt.setInt(1, war.warId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    CounterStat stat = new CounterStat();
                    stat.isActive = rs.getBoolean("active");
                    stat.type = CounterType.values[rs.getInt("type")];
                    return stat;
                }
            }
            return updateCounter(war);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public CounterStat updateCounter(DBWar war) {
        DBNation attacker = Locutus.imp().getNationDB().getNation(war.attacker_id);
        DBNation defender = Locutus.imp().getNationDB().getNation(war.defender_id);
        if (war.attacker_aa == 0 && attacker != null) {
            war.attacker_aa = attacker.getAlliance_id();
        }
        if (war.defender_aa == 0 && defender != null) {
            war.defender_aa = defender.getAlliance_id();
        }
        if (war.attacker_aa == 0 || war.defender_aa == 0) {
            CounterStat stat = new CounterStat();
            stat.type = CounterType.UNCONTESTED;
            stat.isActive = defender != null && defender.getActive_m() < 2880;
            return stat;
        }
        int warId = war.warId;
        List<DBAttack> attacks = Locutus.imp().getWarDb().getAttacksByWarId(warId);

        long startDate = war.date;
        long startTurn = TimeUtil.getTurn(startDate);

        long endTurn = startTurn + 60 - 1;
        long endDate = TimeUtil.getTimeFromTurn(endTurn + 1);

        boolean isOngoing = war.status == WarStatus.ACTIVE || war.status == WarStatus.DEFENDER_OFFERED_PEACE || war.status == WarStatus.ATTACKER_OFFERED_PEACE;

        boolean isAllianceLooted = false;
        boolean isActive = war.status == WarStatus.DEFENDER_OFFERED_PEACE || war.status == WarStatus.DEFENDER_VICTORY || war.status == WarStatus.ATTACKER_OFFERED_PEACE;
        for (DBAttack attack : attacks) {
            if (attack.attack_type == AttackType.VICTORY && attack.attacker_nation_id == war.attacker_id) {
                war.status = WarStatus.ATTACKER_VICTORY;
            }
            if (attack.attack_type == AttackType.A_LOOT && attack.attacker_nation_id == war.attacker_id) {
                isAllianceLooted = true;
            }
            if (attack.attacker_nation_id == war.defender_id) isActive = true;
            switch (attack.attack_type) {
                case A_LOOT:
                case VICTORY:
                case PEACE:
                    endTurn = TimeUtil.getTurn(attack.epoch);
                    endDate = attack.epoch;
                    break;
            }
        }

        Set<Integer> attAA = new HashSet<>(Collections.singleton(war.attacker_aa));
        for (Map.Entry<Integer, Treaty> entry : Locutus.imp().getNationDB().getTreaties(war.attacker_aa).entrySet()) {
            switch (entry.getValue().getType()) {
                case MDP:
                case MDOAP:
                case ODP:
                case ODOAP:
                case PROTECTORATE:
                    attAA.add(entry.getKey());
            }
        }

        Set<Integer> defAA = new HashSet<>(Collections.singleton(war.defender_aa));
        for (Map.Entry<Integer, Treaty> entry : Locutus.imp().getNationDB().getTreaties(war.defender_aa).entrySet()) {
            switch (entry.getValue().getType()) {
                case MDP:
                case MDOAP:
                case ODP:
                case ODOAP:
                case PROTECTORATE:
                    defAA.add(entry.getKey());
            }
        }

        Set<Integer> counters = new HashSet<>();
        Set<Integer> isCounter = new HashSet<>();

        List<DBWar> possibleCounters = Locutus.imp().getWarDb().getWars(war.attacker_id, war.defender_id, startDate - TimeUnit.DAYS.toMillis(5), endDate);
        for (DBWar other : possibleCounters) {
            if (other.warId == war.warId) continue;
            if (attAA.contains(other.attacker_aa) || !(defAA.contains(other.attacker_aa))) continue;
            if (other.date < war.date) {
                if (other.attacker_id == war.defender_id && attAA.contains(other.defender_aa)) {
                    isCounter.add(other.warId);
                }
            } else if (other.defender_id == war.attacker_id) {
                counters.add(other.warId);
            }
        }

        boolean isEscalated = !counters.isEmpty() && !isCounter.isEmpty();

        CounterType type;
        if (isEscalated) {
            type = CounterType.ESCALATION;
        } else if (!counters.isEmpty()) {
            type = CounterType.GETS_COUNTERED;
        } else if (!isCounter.isEmpty()) {
            type = CounterType.IS_COUNTER;
        } else {
            type = CounterType.UNCONTESTED;
        }

        boolean finalIsActive = isActive;
        if (!isOngoing) {
            update("INSERT OR REPLACE INTO `COUNTER_STATS`(`id`, `type`, `active`) VALUES(?, ?, ?)", new ThrowingConsumer<PreparedStatement>() {
                @Override
                public void acceptThrows(PreparedStatement stmt) throws Exception {
                    stmt.setInt(1, warId);
                    stmt.setInt(2, type.ordinal());
                    stmt.setBoolean(3, finalIsActive);
                }
            });
        }

        CounterStat stat = new CounterStat();
        stat.type = type;
        stat.isActive = isActive;
        return stat;
    }

    public Set<DBBounty> getBounties(int nationId) {
        LinkedHashSet<DBBounty> result = new LinkedHashSet<>();

        query("SELECT * FROM `BOUNTIES_V3` WHERE nation_id = ? ORDER BY date DESC", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, nationId);
        }, (ThrowingConsumer<ResultSet>) rs -> {
            while (rs.next()) {
                DBBounty bounty = new DBBounty(rs);
                result.add(bounty);
            }
        });
        return result;
    }

    public Map<Integer, List<DBBounty>> getBountiesByNation() {
        return getBounties().stream().collect(Collectors.groupingBy(DBBounty::getId, Collectors.toList()));
    }

    public Set<DBBounty> getBounties() {
        LinkedHashSet<DBBounty> result = new LinkedHashSet<>();
        query("SELECT * FROM `BOUNTIES_V3` ORDER BY date DESC", (ThrowingConsumer<PreparedStatement>) stmt -> {},
                (ThrowingConsumer<ResultSet>) rs -> {
            while (rs.next()) {
                DBBounty bounty = new DBBounty(rs);
                result.add(bounty);
            }
        });
        return result;
    }

    public synchronized void updateBountiesV3() throws IOException {
        Set<DBBounty> removedBounties = getBounties();
        Set<DBBounty> newBounties = new LinkedHashSet<>();

        boolean callEvents = !removedBounties.isEmpty();

        PoliticsAndWarV3 v3 = Locutus.imp().getV3();
        Collection<Bounty> bounties = v3.fetchBounties(null, f -> f.all$(-1));

        if (bounties.isEmpty()) return;
        bounties = new HashSet<>(bounties); // Ensure uniqueness (in case of pagination concurrency issues)

        for (Bounty bounty : bounties) {
            WarType type = WarType.parse(bounty.getType().name());
            long date = bounty.getDate().toEpochMilli();
            int id = bounty.getId();
            int nationId = bounty.getNation_id();
            long amount = bounty.getAmount();

            int postedBy = 0;

            DBBounty dbBounty = new DBBounty(id, date, nationId, postedBy, type, amount);
            if (removedBounties.contains(dbBounty)) {
                removedBounties.remove(dbBounty);
                continue;
            } else {
                newBounties.add(dbBounty);
            }
        }

        for (DBBounty bounty : removedBounties) {
            removeBounty(bounty);
            if (callEvents) new BountyRemoveEvent(bounty).post();
        }
        for (DBBounty bounty : newBounties) {
            addBounty(bounty);
            if (Settings.INSTANCE.LEGACY_SETTINGS.DEANONYMIZE_BOUNTIES) {
                // TODO remove this
            }
            if (callEvents) new BountyCreateEvent(bounty).post();
        }
    }

    public void addBounty(DBBounty bounty) {
        update("INSERT OR REPLACE INTO `BOUNTIES_V3`(`id`, `date`, `nation_id`, `posted_by`, `attack_type`, `amount`) VALUES(?, ?, ?, ?, ?, ?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setLong(1, bounty.getId());
            stmt.setLong(2, bounty.getDate());
            stmt.setLong(3, bounty.getNationId());
            stmt.setInt(4, bounty.getPostedBy());
            stmt.setInt(5, bounty.getType().ordinal());
            stmt.setLong(6, bounty.getAmount());
        });
    }

    public void removeBounty(DBBounty bounty) {
        update("DELETE FROM `BOUNTIES_V3` where `id` = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setLong(1, bounty.getId());
        });
    }

    public boolean updateWars(boolean events) throws IOException {
        if (!events || activeWars.isEmpty()) return updateAllWars(null);

        ArrayDeque<Event> eventQueue = new ArrayDeque<>();
        boolean result = updateAllWars(eventQueue::add);
        if (!eventQueue.isEmpty()) {
            Locutus.imp().getExecutor().submit(() -> {
                for (Event event : eventQueue) {
                    event.post();
                }
            });
        }
        return result;
    }

    public boolean updateAllWars(Consumer<Event> eventConsumer) throws IOException {
        List<SWarContainer> wars = Locutus.imp().getPnwApi().getWarsByAmount(5000).getWars();
        List<DBWar> dbWars = new ArrayList<>();
        int minId = Integer.MAX_VALUE;
        int maxId = 0;
        for (SWarContainer container : wars) {
            if (container == null) continue;
            DBWar war = new DBWar(container);
            dbWars.add(war);
            minId = Math.min(minId, war.warId);
            maxId = Math.max(maxId, war.warId);
        }

        if (dbWars.isEmpty()) {
            AlertUtil.error("Unable to fetch wars", new Exception());
            return false;
        }
        Set<Integer> fetchedWarIds = dbWars.stream().map(DBWar::getWarId).collect(Collectors.toSet());
        Map<Integer, DBWar> activeWarsById = activeWars.getActiveWarsById();

        // Find deleted wars
        for (int id = minId; id <= maxId; id++) {
            if (fetchedWarIds.contains(id)) continue;
            DBWar war = activeWarsById.get(id);
            if (war == null) continue;

            DBWar newWar = new DBWar(war);
            newWar.status = WarStatus.EXPIRED;
            dbWars.add(newWar);
        }

        boolean result = updateWars(dbWars, eventConsumer);
        return result;

    }

    public boolean updateActiveWars(Consumer<Event> eventConsumer) throws IOException {
        if (activeWars.isEmpty()) {
            return updateAllWars(eventConsumer);
        }
        int newWarsToFetch = 100;
        int numToUpdate = Math.min(999, PoliticsAndWarV3.WARS_PER_PAGE);

        List<DBWar> mostActiveWars = new ArrayList<>(activeWars.getActiveWars());
        if (mostActiveWars.isEmpty()) return false;

        int latestWarId = 0;

        Function<DBWar, Long> getLastActive = war -> {
            DBNation nat1 = war.getNation(true);
            DBNation nat2 = war.getNation(false);
            return Math.max(nat1 == null ? 0 : nat1.lastActiveMs(), nat2 == null ? 0 : nat2.lastActiveMs());
        };
        mostActiveWars.sort((o1, o2) -> Long.compare(getLastActive.apply(o2), getLastActive.apply(o1)));

        List<Integer> warIdsToUpdate = new ArrayList<>(999);
        for (DBWar war : mostActiveWars) latestWarId = Math.max(latestWarId, war.warId);


        for (int i = latestWarId + 1; i <= latestWarId + newWarsToFetch; i++) {
            warIdsToUpdate.add(i);
        }

        Set<Integer> activeWarsToFetch = new HashSet<>();

        for (int i = 0; i < mostActiveWars.size(); i++) {
            int warId = mostActiveWars.get(i).getWarId();
            warIdsToUpdate.add(warId);
            activeWarsToFetch.add(warId);
            if (warIdsToUpdate.size() >= numToUpdate) break;
        }

        Collections.sort(warIdsToUpdate);

        PoliticsAndWarV3 api = Locutus.imp().getV3();
        List<War> wars = api.fetchWarsWithInfo(r -> {
            r.setId(warIdsToUpdate);
            r.setActive(false); // needs to be set otherwise inactive wars wont be fetched
        });
        if (wars.isEmpty()) {
            AlertUtil.error("Failed to fetch wars", new Exception());
            return false;
        }

        List<DBWar> dbWars = wars.stream().map(DBWar::new).collect(Collectors.toList());
        updateWars(dbWars, eventConsumer);
        int numActive = activeWarsToFetch.size();
        for (DBWar war : dbWars) {
            activeWarsToFetch.remove(war.getWarId());
        }
        if (activeWarsToFetch.size() > 0) {
            int notDeleted = 0;
            for (int warId : activeWarsToFetch) {
                DBWar war = activeWars.getWar(warId);
                if (war == null) {
                    // no issue
                    continue;
                }
                if (war.getNation(true) != null && war.getNation(false) != null) {
                    notDeleted++;
                }
                DBWar copy = new DBWar(war);
                war.status = WarStatus.EXPIRED;
                activeWars.makeWarInactive(war);
                saveWars(Collections.singleton(war));
                if (eventConsumer != null) {
                    eventConsumer.accept(new WarStatusChangeEvent(copy, war));
                }
            }
            if (notDeleted > 0) {
                AlertUtil.error("Unable to fetch " + notDeleted + "/" + numActive + " active wars:", new RuntimeException("Ignore if these wars correspond to deleted nations:\n" + StringMan.getString(activeWarsToFetch)));
            }
        }

        return true;
    }

    public boolean updateWars(List<DBWar> dbWars, Consumer<Event> eventConsumer) {
        List<DBWar> prevWars = new ArrayList<>();
        List<DBWar> newWars = new ArrayList<>();
        Set<Integer> newWarIds = new LinkedHashSet<>();

        for (DBWar war : dbWars) {
            {
                Locutus.imp().getNationDB().setNationActive(war.attacker_id, war.date, eventConsumer);
            }
            DBWar existing = activeWars.getWar(war.attacker_id, war.warId);

            if ((existing == null && !war.isActive()) || war.equals(existing)) continue;

            prevWars.add(existing);
            newWars.add(war);
            newWarIds.add(war.getWarId());

            DBNation attacker = Locutus.imp().getNationDB().getNation(war.attacker_id);
            DBNation defender = Locutus.imp().getNationDB().getNation(war.defender_id);
            if (war.attacker_aa == 0 && attacker != null) {
                war.attacker_aa = attacker.getAlliance_id();
            }
            if (war.defender_aa == 0 && defender != null) {
                war.defender_aa = defender.getAlliance_id();
            }
        }

        long currentTurn = TimeUtil.getTurn();
        for (DBWar war : activeWars.getActiveWars()) {
            if (!newWarIds.add(war.getWarId())) continue;

            long warTurn = TimeUtil.getTurn(war.date);

            if (currentTurn - warTurn >= 60) {
                prevWars.add(new DBWar(war));
                war.status = WarStatus.EXPIRED;
                newWars.add(war);

            } else if (war.status != WarStatus.EXPIRED){
                DBNation attacker = Locutus.imp().getNationDB().getNation(war.attacker_id);
                DBNation defender = Locutus.imp().getNationDB().getNation(war.defender_id);
                if ((attacker == null || defender == null) && war.date < System.currentTimeMillis() - TimeUnit.HOURS.toMillis(2)) {

                    prevWars.add(new DBWar(war));
                    war.status = WarStatus.EXPIRED;
                    newWars.add(war);
                }
            }
        }

        saveWars(newWars);

        List<Map.Entry<DBWar, DBWar>> warUpdatePreviousNow = new ArrayList<>();

        for (int i = 0 ; i < prevWars.size(); i++) {
            DBWar previous = prevWars.get(i);
            DBWar newWar = newWars.get(i);
            if (newWar.isActive()) {
                activeWars.addActiveWar(newWar);
            } else {
                activeWars.makeWarInactive(newWar);
            }

            warUpdatePreviousNow.add(new AbstractMap.SimpleEntry<>(previous, newWar));
        }

        if (!warUpdatePreviousNow.isEmpty() && eventConsumer != null) {
            try {
                WarUpdateProcessor.processWars(warUpdatePreviousNow, eventConsumer);
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }

        return true;
    }

    public void saveWars(Collection<DBWar> values) {
        Map<Integer, DBNation> nationSnapshots = new HashMap<>();
        for (DBWar war : values) {
            DBNation attacker = war.getNation(true);
            DBNation defender = war.getNation(false);
            if (attacker != null) nationSnapshots.put(war.getWarId(), attacker);
            if (defender != null) nationSnapshots.put(war.getWarId(), defender);
            Locutus.imp().getNationDB().saveNationWarSnapshots(nationSnapshots);
        }


        String query = "INSERT OR REPLACE INTO `wars`(`id`, `attacker_id`, `defender_id`, `attacker_aa`, `defender_aa`, `war_type`, `status`, `date`) VALUES(?, ?, ?, ?, ?, ?, ?, ?)";

        ThrowingBiConsumer<DBWar, PreparedStatement> setStmt = (war, stmt) -> {
            stmt.setInt(1, war.warId);
            stmt.setLong(2, war.attacker_id);
            stmt.setInt(3, war.defender_id);
            stmt.setInt(4, war.attacker_aa);
            stmt.setInt(5, war.defender_aa);
            stmt.setInt(6, war.warType.ordinal());
            stmt.setInt(7, war.status.ordinal());
            stmt.setLong(8, war.date);
        };
        if (values.size() == 1) {
            DBWar value = values.iterator().next();
            update(query, stmt -> setStmt.accept(value, stmt));
        } else {
            executeBatch(values, query, setStmt);
        }
    }

    public Map<Integer, DBWar> getWars(WarStatus status) {
        return getWars(f -> f.status == status);
    }

    public Map<Integer, DBWar> getWarsSince(long date) {
        return getWars(f -> f.date > date);
    }

    public Map<Integer, DBWar> getWars() {
        synchronized (warsById) {
            return new Int2ObjectOpenHashMap<>(warsById);
        }
    }

    public Map<Integer, List<DBWar>> getActiveWarsByAttacker(Set<Integer> attackers, Set<Integer> defenders, WarStatus... statuses) {
        Set<Integer> all = new HashSet<>();

        Map<Integer, List<DBWar>> map = new Int2ObjectOpenHashMap<>();
        activeWars.getActiveWars(f -> all.contains(f), new Predicate<DBWar>() {
            @Override
            public boolean test(DBWar war) {
                if (attackers.contains(war.attacker_id) || defenders.contains(war.defender_id)) {
                    List<DBWar> list = map.computeIfAbsent(war.attacker_id, k -> new ArrayList<>());
                    list.add(war);
                }
                return false;
            }
        });
        return map;
    }

    private DBWar create(ResultSet rs) throws SQLException {
        int warId = rs.getInt("id");
        //  `attacker_id`, `defender_id`, `attacker_aa`, `defender_aa`, `war_type`, `status`, `date`
        int attacker_id = rs.getInt("attacker_id");
        int defender_id = rs.getInt("defender_id");
        int attacker_aa = rs.getInt("attacker_aa");
        int defender_aa = rs.getInt("defender_aa");
        WarType war_type = WarType.values[rs.getInt("war_type")];
        WarStatus status = WarStatus.values[rs.getInt("status")];
        long date = rs.getLong("date");

        return new DBWar(warId, attacker_id, defender_id, attacker_aa, defender_aa, war_type, status, date);
    }

    public DBWar getWar(int warId) {
        return warsById.get(warId);
    }

    public List<DBWar> getWars(int nation1, int nation2, long start, long end) {
        List<DBWar> list = new ArrayList<>();
        synchronized (warsByNationId) {
            Map<Integer, DBWar> wars = warsByNationId.get(nation1);
            if (wars != null) {
                for (DBWar war : wars.values()) {
                    if ((war.defender_id == nation2 || war.attacker_id == nation1) && war.date > start && war.date < end) {
                        list.add(war);
                    }
                }
            }
        }
        return list;
    }

    public DBWar getActiveWarByNation(int attacker, int defender) {
        for (DBWar war : activeWars.getActiveWars(attacker)) {
            if (war.attacker_id == attacker && war.defender_id == defender) {
                return war;
            }
        }
        return null;
    }

    public List<DBWar> getWarsByNation(int nation, WarStatus status) {
        if (status == WarStatus.ACTIVE || status == WarStatus.ATTACKER_OFFERED_PEACE || status == WarStatus.DEFENDER_OFFERED_PEACE) {
            return activeWars.getActiveWars(nation).stream().filter(f -> f.status == status).collect(Collectors.toList());
        }
        synchronized (warsByNationId) {
            return (warsByNationId.getOrDefault(nation, Collections.emptyMap()).values().stream().filter(f -> f.status == status).collect(Collectors.toList()));
        }
    }

    public List<DBWar> getActiveWarsByAlliance(Set<Integer> attackerAA, Set<Integer> defenderAA) {
        return new ArrayList<>(activeWars.getActiveWars(f -> true, f -> (attackerAA == null || attackerAA.contains(f.attacker_aa)) && (defenderAA == null || defenderAA.contains(f.defender_aa))).values());
    }

    public List<DBWar> getWarsByAlliance(int attacker) {
        synchronized (warsByAllianceId) {
            return new ArrayList<>(warsByAllianceId.get(attacker).values());
        }
    }

    public List<DBWar> getWarsByNation(int nationId) {
        synchronized (warsByNationId) {
            return new ArrayList<>(warsByNationId.getOrDefault(nationId, Collections.emptyMap()).values());
        }
    }

    public DBWar getLastOffensiveWar(int nation) {
        return getWarsByNation(nation).stream().filter(f -> f.attacker_id == nation).max(Comparator.comparingInt(o -> o.warId)).orElse(null);
    }

    public DBWar getLastDefensiveWar(int nation) {
        getWarsByNation(nation).stream().filter(f -> f.defender_id == nation).max(Comparator.comparingInt(o -> o.warId)).orElse(null);
    }

    public DBWar getLastWar(int nationId) {
        return getWarsByNation(nationId).stream().max(Comparator.comparingInt(o -> o.warId)).orElse(null);
    }

    public List<DBAttack> selectAttacks(Consumer<SelectBuilder> query) {
        List<DBAttack> list = new ArrayList<>();
        SelectBuilder builder = getDb().selectBuilder("attacks2")
                .select("*");
        if (query != null) query.accept(builder);
        try (ResultSet rs = builder.executeRaw()) {
            while (rs.next()) {
                list.add(createAttack(rs));
            }
            return list;
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public List<DBWar> getWarsByNation(int nation, WarStatus... statuses) {
        if (statuses.length == 0) return getWarsByNation(nation);
        if (statuses.length == 1) return getWarsByNation(nation, statuses[0]);
        Set<WarStatus> statusSet = new HashSet<>(Arrays.asList(statuses));

        synchronized (warsByNationId) {
            return (warsByNationId.getOrDefault(nation, Collections.emptyMap()).values().stream().filter(f -> statusSet.contains(f.status)).collect(Collectors.toList()));
        }
    }

    public List<DBWar> getActiveWars(Set<Integer> alliances, WarStatus... statuses) {
        Set<WarStatus> statusSet = new HashSet<>(Arrays.asList(statuses));
        return new ArrayList<>(activeWars.getActiveWars(f -> true, f -> (alliances.contains(f.attacker_aa) || alliances.contains(f.defender_aa)) && statusSet.contains(f.status)).values());
    }

    public List<DBWar> getWarByStatus(WarStatus... statuses) {
        Set<WarStatus> statusSet = new HashSet<>(Arrays.asList(statuses));
        return new ArrayList<>(getWars(f -> statusSet.contains(f.status)).values());
    }

    public List<DBWar> getWars(Set<Integer> alliances, long start) {
        return getWars(alliances, start, Long.MAX_VALUE);
    }

    public List<DBWar> getWars(Set<Integer> alliances, long start, long end) {
        return new ArrayList<>(getWarsForNationOrAlliance(null, f -> alliances.contains(f), f -> f.date > start && f.date < end).values());
    }

    public List<DBWar> getWarsById(Set<Integer> warIds) {
        String query = "SELECT * from wars WHERE `id` in " + StringMan.getString(warIds);
        List<DBWar> list = new ArrayList<>();
        try (PreparedStatement stmt= getConnection().prepareStatement(query)) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    DBWar war = create(rs);
                    list.add(war);
                }
            }
            return list;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public List<DBAttack> getAttacks(Collection<Integer> coal1Alliances, Collection<Integer> coal1Nations, Collection<Integer> coal2Alliances, Collection<Integer> coal2Nations, long start, long end, boolean union) {
        if (coal1Alliances.isEmpty() && coal1Nations.isEmpty() && coal2Alliances.isEmpty() && coal2Nations.isEmpty()) return Collections.emptyList();

//        String warQuery = generateWarQuery("b.", coal1Alliances, coal1Nations, coal2Alliances, coal2Nations, start, end, union);
//        warQuery = warQuery.split(" WHERE ")[1];
//        String attQuery = "SELECT a.* " +
////                "FROM attacks2 a " +
////                "LEFT OUTER JOIN wars b on " +
////                "b.id = a.war_id WHERE " + warQuery;

        String warQuery = generateWarQuery("", coal1Alliances, coal1Nations, coal2Alliances, coal2Nations, start, end, union);
        warQuery = warQuery.split(" WHERE ")[1];
        String attQuery = "SELECT * FROM attacks2 WHERE war_id in (SELECT id from wars WHERE " + warQuery + ")";

        ArrayList<DBAttack> list = new ArrayList<>();
        try (PreparedStatement stmt= getConnection().prepareStatement(attQuery)) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(createAttack(rs));
                }
            }
            return list;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public Map<Integer, DBWar> getWars(Collection<Integer> coal1Alliances, Collection<Integer> coal1Nations, Collection<Integer> coal2Alliances, Collection<Integer> coal2Nations, long start, long end, boolean union) {
        if (coal1Alliances.isEmpty() && coal1Nations.isEmpty() && coal2Alliances.isEmpty() && coal2Nations.isEmpty()) return Collections.emptyMap();

        String query = generateWarQuery("", coal1Alliances, coal1Nations, coal2Alliances, coal2Nations, start, end, union);

        Map<Integer, DBWar> wars = new LinkedHashMap<>();
        try (PreparedStatement stmt= getConnection().prepareStatement(query)) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    DBWar war = create(rs);
                    wars.put(war.warId, war);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return wars;
    }

    private String generateWarQuery(String prefix, Collection<Integer> coal1Alliances, Collection<Integer> coal1Nations, Collection<Integer> coal2Alliances, Collection<Integer> coal2Nations, long start, long end, boolean union) {
        List<String> requirements = new ArrayList<>();
        if (start > 0) {
            requirements.add(prefix + "date > " + start);
        }
        if (end < System.currentTimeMillis()) {
            requirements.add(prefix + "date < " + end);
        }

        List<String> attReq = new ArrayList<>();
        if (!coal1Alliances.isEmpty()) {
            if (coal1Alliances.size() == 1) {
                Integer id = coal1Alliances.iterator().next();
                attReq.add(prefix + "attacker_aa = " + id);
            } else {
                attReq.add(prefix + "attacker_aa in " + StringMan.getString(coal1Alliances));
            }
        }
        if (!coal1Nations.isEmpty()) {
            if (coal1Nations.size() == 1) {
                Integer id = coal1Nations.iterator().next();
                attReq.add(prefix + "attacker_id = " + id);
            } else {
                attReq.add(prefix + "attacker_id in " + StringMan.getString(coal1Nations));
            }
        }

        List<String> defReq = new ArrayList<>();
        if (!coal2Alliances.isEmpty()) {
            if (coal2Alliances.size() == 1) {
                Integer id = coal2Alliances.iterator().next();
                defReq.add(prefix + "defender_aa = " + id);
            } else {
                defReq.add(prefix + "defender_aa in " + StringMan.getString(coal2Alliances));
            }
        }
        if (!coal2Nations.isEmpty()) {
            if (coal2Nations.size() == 1) {
                Integer id = coal2Nations.iterator().next();
                defReq.add(prefix + "defender_id = " + id);
            } else {
                defReq.add(prefix + "defender_id in " + StringMan.getString(coal2Nations));
            }
        }

        List<String> natOrAAReq = new ArrayList<>();
        if (!attReq.isEmpty()) natOrAAReq.add("(" + StringMan.join(attReq, " AND ") + ")");
        if (!defReq.isEmpty()) natOrAAReq.add("(" + StringMan.join(defReq, " AND ") + ")");
        String natOrAAReqStr = "(" + StringMan.join(natOrAAReq, union ? " AND " : " OR ") + ")";
        requirements.add(natOrAAReqStr);


        String query = "SELECT * from wars WHERE " + StringMan.join(requirements, " AND ");
        return query;
    }

    public void saveAttacks(Collection<DBAttack> values) {
        for (DBAttack attack : values) {
            if (attack.attack_type != AttackType.VICTORY && attack.attack_type != AttackType.A_LOOT) continue;

            Map<ResourceType, Double> loot = attack.getLoot();
            if (loot == null || loot.isEmpty()) continue;
            Double pct = attack.getLootPercent();
            if (pct == 0) pct = 0.1;
            double factor = 1/pct;

            if (attack.loot != null) {
                double[] lootCopy = attack.loot.clone();
                for (int i = 0; i < lootCopy.length; i++) {
                    lootCopy[i] = (lootCopy[i] * factor) - lootCopy[i];
                }

                if (attack.attack_type == AttackType.VICTORY) {
                    Locutus.imp().getNationDB().saveLoot(attack.defender_nation_id, attack.epoch, lootCopy, NationLootType.WAR_LOSS);
                } else if (attack.attack_type == AttackType.A_LOOT) {
                    Integer allianceId = attack.getLooted();
                    if (allianceId != null) {
                        Locutus.imp().getNationDB().saveAllianceLoot(allianceId, attack.epoch, lootCopy, NationLootType.WAR_LOSS);
                    }
                }
            }
        }
        synchronized (allAttacks) {
            getAttacks();
            allAttacks.addAll(values);
        }
        // ctiy_id not used anymore, but sqlite doesn't allow removing
        String query = "INSERT OR REPLACE INTO `attacks2`(`war_attack_id`, `date`, `war_id`, `attacker_nation_id`, `defender_nation_id`, `attack_type`, `victor`, `success`, attcas1, attcas2, defcas1, defcas2, defcas3,city_id,infra_destroyed,improvements_destroyed,money_looted,looted,loot,pct_looted,city_infra_before,infra_destroyed_value,att_gas_used,att_mun_used,def_gas_used,def_mun_used) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        ThrowingBiConsumer<DBAttack, PreparedStatement> setStmt = (attack, stmt) -> {
            stmt.setInt(1, attack.war_attack_id);
            stmt.setLong(2, attack.epoch);
            stmt.setInt(3, attack.war_id);
            stmt.setInt(4, attack.attacker_nation_id);
            stmt.setInt(5, attack.defender_nation_id);
            stmt.setInt(6, attack.attack_type.ordinal());
            stmt.setInt(7, attack.victor);
            stmt.setInt(8, attack.success);
            stmt.setInt(9, attack.attcas1);
            stmt.setInt(10, attack.attcas2);
            stmt.setInt(11, attack.defcas1);
            stmt.setInt(12, attack.defcas2);
            stmt.setInt(13, attack.defcas3);
            stmt.setLong(14, attack.city_cached);
            stmt.setLong(15, (long) (attack.infra_destroyed * 100));
            stmt.setLong(16, attack.improvements_destroyed);
            stmt.setLong(17, (long) (attack.money_looted * 100));

            stmt.setInt(18, attack.looted);

            if (attack.loot == null) stmt.setNull(19, Types.BLOB);
            else stmt.setBytes(19, ArrayUtil.toByteArray(attack.loot));

            stmt.setInt(20, (int) (attack.lootPercent * 100 * 100));

            stmt.setLong(21, (int) (attack.city_infra_before * 100));
            stmt.setLong(22, (long) (attack.infra_destroyed_value * 100));
            stmt.setLong(23, (int) (attack.att_gas_used * 100));
            stmt.setLong(24, (int) (attack.att_mun_used * 100));
            stmt.setLong(25, (int) (attack.def_gas_used * 100));
            stmt.setLong(26, (int) (attack.def_mun_used * 100));
        };
        if (values.size() == 1) {
            DBAttack value = values.iterator().next();
            update(query, stmt -> setStmt.accept(value, stmt));
        } else {
            executeBatch(values, query, setStmt);
        }
    }

    public boolean updateAttacks(Consumer<Event> eventConsumer) {
        if (eventConsumer == null) return updateAttacks(null, false, null);

        return updateAttacks(null, true, eventConsumer);
    }

    private DBAttack getLatestAttack() {
        List<DBAttack> attacks = selectAttacks(s -> s.order("war_attack_id", QueryOrder.OrderDirection.DESC).limit(1));
        return attacks.isEmpty() ? null : attacks.get(0);
    }

    public synchronized boolean updateAttacks(Integer maxId, boolean runAlerts, Consumer<Event> eventConsumer) {
        DBAttack latest = getLatestAttack();
        if (maxId == null) maxId = latest == null ? null : latest.war_attack_id;
        if (maxId == null || maxId == 0) runAlerts = false;

        PoliticsAndWarV3 v3 = Locutus.imp().getV3();
        // Dont run events if attacks are > 1 day old
        if (latest == null || latest.epoch < System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)) {
            System.out.println("No recent attack data in DB. Updating attacks without event handling: " + maxId);
            List<DBAttack> attackList = new ArrayList<>();
            v3.fetchAttacksSince(maxId, new Predicate<WarAttack>() {
                @Override
                public boolean test(WarAttack v3Attack) {
                    DBAttack attack = new DBAttack(v3Attack);
                    synchronized (attackList) {
                        attackList.add(attack);
                        if (attackList.size() > 1000) {
                            System.out.println("Save " + attack.war_attack_id);
                            saveAttacks(attackList);
                            attackList.clear();
                            System.out.println("Save end");
                        }
                    }
                    return false;
                }
            });
            saveAttacks(attackList);
            return true;
        }

        List<DBAttack> newAttacks = v3
                .fetchAttacksSince(maxId, f -> true).stream().map(DBAttack::new)
                .collect(Collectors.toList());

        Map<DBAttack, Double> attackInfraPctMembers = new HashMap<>();
        List<DBAttack> dbAttacks = new ArrayList<>();

        List<DBAttack> dirtyCities = new ArrayList<>();

        long now = System.currentTimeMillis();
        for (DBAttack attack : newAttacks) {
            if (runAlerts)
            {
                Locutus.imp().getNationDB().setNationActive(attack.attacker_nation_id, attack.epoch, eventConsumer);
                Map<MilitaryUnit, Integer> attLosses = attack.getUnitLosses(true);
                Map<MilitaryUnit, Integer> defLosses = attack.getUnitLosses(false);
                if (!attLosses.isEmpty()) {
                    Locutus.imp().getNationDB().updateNationUnits(attack.attacker_nation_id, attack.epoch, attLosses, eventConsumer);
                }
                if (!defLosses.isEmpty()) {
                    Locutus.imp().getNationDB().updateNationUnits(attack.defender_nation_id, attack.epoch, defLosses, eventConsumer);
                }
            }

            if (attack.attack_type == AttackType.NUKE && attack.success > 0 && attack.city_cached != 0) {
                Locutus.imp().getNationDB().setCityNukeFromAttack(attack.defender_nation_id, attack.city_cached, attack.epoch, eventConsumer);
            }

            if (attack.attack_type == AttackType.VICTORY) {
                DBWar war = activeWars.getWar(attack.attacker_nation_id, attack.war_id);
                if (war != null) {

                    if (runAlerts) {
                        DBNation defender = DBNation.byId(attack.defender_nation_id);
                        if (defender != null && defender.getLastFetchedUnitsMs() < attack.epoch) {
                            DBNation copyOriginal = null;
                            if (!defender.isBeige()) copyOriginal = new DBNation(defender);
                            defender.setColor(NationColor.BEIGE);
                            defender.setBeigeTimer(defender.getBeigeAbsoluteTurn() + 24);
                            if (copyOriginal != null && eventConsumer != null) eventConsumer.accept(new NationChangeColorEvent(copyOriginal, defender));
                        }
                        DBWar oldWar = new DBWar(war);
                        war.status = war.attacker_id == attack.attacker_nation_id ? WarStatus.ATTACKER_VICTORY : WarStatus.DEFENDER_VICTORY;
                        if (eventConsumer != null) {
                            eventConsumer.accept(new WarStatusChangeEvent(oldWar, war));
                        }
                    }
                }
            }

            if (attack.city_infra_before > 0 && attack.infra_destroyed > 0 && attack.city_cached != 0 && attack.attack_type != AttackType.VICTORY && attack.attack_type != AttackType.A_LOOT) {
                double infra = attack.city_infra_before - attack.infra_destroyed;
                Locutus.imp().getNationDB().setCityInfraFromAttack(attack.defender_nation_id, attack.city_cached, infra, attack.epoch, eventConsumer);
            }
            if (attack.improvements_destroyed > 0 && attack.city_cached != 0) {
                dirtyCities.add(attack);
            }
            if (attack.epoch > now) {
                attack.epoch = now;
            }
            if (attack.attack_type == AttackType.GROUND && attack.money_looted != 0 && Settings.INSTANCE.LEGACY_SETTINGS.ATTACKER_DESKTOP_ALERTS.contains(attack.attacker_nation_id)) {
                AlertUtil.openDesktop(attack.toUrl());
            }
            if ((attack.attack_type == AttackType.NUKE || attack.attack_type == AttackType.MISSILE) && attack.success == 0) {
                attack.infra_destroyed_value = 0;
            }

            Set<DBWar> warsToSave = new LinkedHashSet<>();

            {
                if (attack.attack_type == AttackType.VICTORY && attack.infraPercent_cached > 0) {
                    DBNation defender = Locutus.imp().getNationDB().getNation(attack.defender_nation_id);
                    DBWar war = getWar(attack.getWar_id());
                    if (war != null) {
                        war.status = attack.victor == attack.attacker_nation_id ? WarStatus.ATTACKER_VICTORY : WarStatus.DEFENDER_VICTORY;
                        warsToSave.add(war);
                    }

                    if (defender != null && attack.infra_destroyed_value == 0) {
                        double pct = attack.infraPercent_cached / 100d;

                        if (runAlerts) {
                            attackInfraPctMembers.put(attack, pct);
                        }
                    }
                }
            }

            saveWars(warsToSave);

            dbAttacks.add(attack);
        }

        if (!attackInfraPctMembers.isEmpty()) { // update infra

            // get infra before
            for (Map.Entry<DBAttack, Double> entry : attackInfraPctMembers.entrySet()) {
                DBAttack attack = entry.getKey();
                double pct = entry.getValue();
                Map<Integer, DBCity> cities = Locutus.imp().getNationDB().getCitiesV3(attack.defender_nation_id);
                if (cities != null && !cities.isEmpty()) {
                    attack.infra_destroyed = 0d;
                    attack.infra_destroyed_value = 0d;
                    for (DBCity city : cities.values()) {
                        double infraStart, infraEnd;
                        if (city.fetched > attack.epoch) {
                            infraStart = city.infra / (1 - pct);
                            infraEnd = city.infra;
                        } else {
                            infraStart = city.infra;
                            infraEnd = (city.infra) * (1 - pct);
                        }
                        attack.infra_destroyed += infraStart - infraEnd;
                        if (infraStart > infraEnd) {
                            attack.infra_destroyed_value += PnwUtil.calculateInfra(infraEnd, infraStart);
                            Locutus.imp().getNationDB().setCityInfraFromAttack(attack.defender_nation_id, city.id, infraEnd, attack.epoch, eventConsumer);
                        }
                    }
                }
            }
        }

        if (runAlerts) {
            for (DBAttack attack : dirtyCities) {
                Locutus.imp().getNationDB().markCityDirty(attack.defender_nation_id, attack.city_cached, attack.epoch);
            }
        }


        { // add to db
            saveAttacks(dbAttacks);
        }

        if (runAlerts && eventConsumer != null) {
            long start = System.currentTimeMillis();
            NationUpdateProcessor.updateBlockades();
            long diff = System.currentTimeMillis() - start;

            if (diff > 200) {
                System.err.println("Took too long to update blockades (" + diff + "ms)");
            }

            for (DBAttack attack : dbAttacks) {
                eventConsumer.accept(new AttackEvent(attack));
            }
        }
        return true;
    }

//    public List<DBAttack> getAttacks(List<Alliance> coal1Alliances, List<DBNation> coal1Nations, List<Alliance> coal2Alliances, List<DBNation> coal2Nations, long start, long end) {
//
//    }

    public List<DBAttack> getAttacksByWarId(int warId) {
        ArrayList<DBAttack> list = new ArrayList<>();
        try (PreparedStatement stmt= prepareQuery("select * FROM `attacks2` WHERE war_id = ?")) {
            stmt.setInt(1, warId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(createAttack(rs));
                }
            }
            if (list.size() > 1) {
                list.sort(Comparator.comparingInt(o -> o.war_attack_id));
            }
            return list;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public Collection<DBAttack> getAttacks() {
        if (allAttacks.isEmpty()) {
            synchronized (allAttacks) {
                if (allAttacks.isEmpty()) {

                    try (PreparedStatement stmt= prepareQuery("select * FROM `attacks2`")) {
                        try (ResultSet rs = stmt.executeQuery()) {
                            while (rs.next()) {
                                allAttacks.add(createAttack(rs));
                            }
                        }
                        return allAttacks;
                    } catch (SQLException e) {
                        e.printStackTrace();
                        return null;
                    }
                }
            }
        }
        return allAttacks;
    }

    public Map<Integer, List<DBAttack>> getAttacksByWar(int nationId, long cuttoff) {
        List<DBAttack> attacks = getAttacks(nationId, cuttoff);
        return new RankBuilder<>(attacks).group(a -> a.war_id).get();
    }

    public List<DBAttack> getAttacks(Predicate<DBAttack> filter) {
        int amt = 0;
        for (DBAttack attack : getAttacks()) {
            if (filter.test(attack)) amt++;
        }
        ObjectArrayList<DBAttack> list = new ObjectArrayList<>(amt);
        for (DBAttack attack : allAttacks) {
            if (filter.test(attack)) list.add(attack);
        }
        return list;
    }

    public List<DBAttack> getAttacks(long cuttoffMs) {
        return getAttacks(cuttoffMs, Long.MAX_VALUE);
    }
    public List<DBAttack> getAttacks(long start, long end) {
        if (start < System.currentTimeMillis() - TimeUnit.DAYS.toMillis(20)) {
            System.out.println("remove:||Get from cache");
            return getAttacks(f -> f.epoch > start && f.epoch < end);
        }
        System.out.println("remove:||get from db");
        ArrayList<DBAttack> list = new ArrayList<>();
        try (PreparedStatement stmt= prepareQuery("select * FROM `attacks2` WHERE date > ?" + (end < Long.MAX_VALUE ? " AND date < ?" : ""))) {
            stmt.setLong(1, start);
            if (end < Long.MAX_VALUE) {
                stmt.setLong(2, end);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(createAttack(rs));
                }
            }
            return list;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public void removeLoot(Set<Integer> ids) {
        try (Statement stmt = getConnection().createStatement()) {
            for (int attack_id : ids) {
                stmt.addBatch("UPDATE `attacks2` SET loot = NULL where war_attack_id = " + attack_id);
            }
            stmt.executeBatch();
            stmt.clearBatch();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public DBAttack createAttack(ResultSet rs) throws SQLException {
        DBAttack attack = new DBAttack();
        attack.war_attack_id = rs.getInt(1);
        attack.epoch = rs.getLong(2);
        attack.war_id = rs.getInt(3);
        attack.attacker_nation_id = rs.getInt(4);
        attack.defender_nation_id = rs.getInt(5);
        attack.attack_type = AttackType.values[rs.getInt(6)];
        attack.success = rs.getInt(8);

        if (attack.success > 0 || attack.attack_type == AttackType.VICTORY)
        {
            attack.victor = attack.attacker_nation_id;
            attack.looted = attack.defender_nation_id;

            attack.infra_destroyed = getLongDef0(rs, 15) * 0.01;
            if (attack.infra_destroyed > 0) {
                attack.improvements_destroyed = getIntDef0(rs, 16);
                attack.city_infra_before = getLongDef0(rs, 21) * 0.01;
                attack.infra_destroyed_value = getLongDef0(rs, 22) * 0.01;
            }
        }

        switch (attack.attack_type) {
            case GROUND:
                if (attack.success < 0) break;
            case VICTORY:
            case A_LOOT:
                attack.money_looted = getLongDef0(rs, 17) * 0.01;
        }

        if (attack.attack_type == AttackType.VICTORY || attack.attack_type == AttackType.A_LOOT) {
            attack.looted = rs.getInt(18);
            byte[] lootBytes = getBytes(rs, 19);
            if (lootBytes != null) {
                attack.loot = ArrayUtil.toDoubleArray(lootBytes);
                attack.lootPercent = rs.getInt(20) * 0.0001;
            }
            attack.victor = rs.getInt(7);
        }

        switch (attack.attack_type) {
            case VICTORY:
            case FORTIFY:
            case A_LOOT:
            case PEACE:
                break;
            default:
                attack.att_gas_used = getLongDef0(rs, 23) * 0.01;
                attack.att_mun_used = getLongDef0(rs, 24) * 0.01;
                attack.def_gas_used = getLongDef0(rs, 25) * 0.01;
                attack.def_mun_used = getLongDef0(rs, 26) * 0.01;
            case MISSILE:
            case NUKE:
                attack.attcas1 = rs.getInt(9);
                attack.attcas2 = rs.getInt(10);
                attack.defcas1 = rs.getInt(11);
                attack.defcas2 = rs.getInt(12);
                attack.defcas3 = rs.getInt(13);
                break;
        }

        return attack;
    }
//
//    public DBAttack createLegacy(ResultSet rs) throws SQLException {
//        DBAttack attack = new DBAttack();
//        attack.war_attack_id = rs.getInt("war_attack_id");
//        attack.epoch = rs.getLong("date");
//        attack.war_id = rs.getInt("war_id");
//        attack.attacker_nation_id = rs.getInt("attacker_nation_id");
//        attack.defender_nation_id = rs.getInt("defender_nation_id");
//        attack.attack_type = AttackType.values[rs.getInt("attack_type")];
//        attack.victor = rs.getInt("victor");
//        attack.success = rs.getInt("success");
//        attack.attcas1 = rs.getInt("attcas1");
//        attack.attcas2 = rs.getInt("attcas2");
//        attack.defcas1 = rs.getInt("defcas1");
//        attack.defcas2 = rs.getInt("defcas2");
//        attack.defcas3 = 0;
//        attack.city_id = rs.getInt("city_id");
//        Long infra_destroyed = getLong(rs, "infra_destroyed");
//        attack.infra_destroyed = infra_destroyed == null ? null : infra_destroyed / 100d;
//        attack.improvements_destroyed = getInt(rs, "improvements_destroyed");
//        Long money_looted = getLong(rs, "money_looted");
//        attack.money_looted = money_looted == null ? null : money_looted / 100d;
//
//        // looted,loot,pct_looted
//        String note = rs.getString("note");
//        if (note != null) {
//            attack.parseLootLegacy(note);
//        }
//
//        Long city_infra_before = getLong(rs, "city_infra_before");
//        attack.city_infra_before = city_infra_before == null ? null : city_infra_before / 100d;
//        Long infra_destroyed_value = getLong(rs, "infra_destroyed_value");
//        attack.infra_destroyed_value = infra_destroyed_value == null ? null : infra_destroyed_value / 100d;
//        Long att_gas_used = getLong(rs, "att_gas_used");
//        attack.att_gas_used = att_gas_used == null ? null : att_gas_used / 100d;
//        Long att_mun_used = getLong(rs, "att_mun_used");
//        attack.att_mun_used = att_mun_used == null ? null : att_mun_used / 100d;
//        Long def_gas_used = getLong(rs, "def_gas_used");
//        attack.def_gas_used = def_gas_used == null ? null : def_gas_used / 100d;
//        Long def_mun_used = getLong(rs, "def_mun_used");
//        attack.def_mun_used = def_mun_used == null ? null : def_mun_used / 100d;
//
//        return attack;
//    }

    public Map<ResourceType, Double> getAllianceBankEstimate(int allianceId, double nationScore) {
        DBAlliance alliance = DBAlliance.get(allianceId);
        if (allianceId == 0 || alliance == null) return Collections.emptyMap();
        LootEntry lootInfo = Locutus.imp().getNationDB().getAllianceLoot(allianceId);
        if (lootInfo == null) return Collections.emptyMap();


        double[] allianceLoot = lootInfo.getTotal_rss();

        double aaScore = alliance.getScore();
        if (aaScore == 0) return Collections.emptyMap();

        double ratio = (nationScore / aaScore) / (5);
        double percent = Math.min(ratio, 0.33);
        Map<ResourceType, Double> yourLoot = PnwUtil.resourcesToMap(allianceLoot);
        yourLoot = PnwUtil.multiply(yourLoot, percent);
        return yourLoot;
    }

    public Map<Integer, Map.Entry<Long, double[]>> getNationLootFromAttacksLegacy() {
        Map<Integer, Map.Entry<Long, double[]>> nationLoot = new ConcurrentHashMap<>();

        // `attacker_nation_id`, `defender_nation_id`
        String nationReq = "";
        try (PreparedStatement stmt= prepareQuery("select * FROM (SELECT * FROM `attacks2` WHERE `attack_type` = 1" + nationReq + " ORDER BY date DESC) AS tmp_table GROUP BY case when victor = attacker_nation_id then defender_nation_id else attacker_nation_id end")) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    DBAttack attack = createAttack(rs);
                    if (attack.loot == null) continue;

                    int looted = attack.getLooted();
                    int looter = attack.getLooter();

//                    double factor = 0.1;
//                    if (victor != null && victor.getPolicy().equalsIgnoreCase("Pirate")) {
//                        factor = 0.14;
//                    }
//                    if (loser != null && loser.getPolicy().equalsIgnoreCase("Moneybags")) {
//                        factor *= 0.6;
//                    }
                    double factor = 1 / attack.lootPercent;
                    double[] lootCopy = attack.loot.clone();
                    for (int i = 0; i < lootCopy.length; i++) {
                        lootCopy[i] = lootCopy[i] * factor - lootCopy[i];
                    }

                    AbstractMap.SimpleEntry<Long, double[]> newEntry = new AbstractMap.SimpleEntry<>(attack.epoch, lootCopy);
                    Map.Entry<Long, double[]> existing = nationLoot.put(looted, newEntry);
                    if (existing != null && existing.getKey() > attack.epoch) {
                        nationLoot.put(looted, existing);
                    }
                }
            }
            return nationLoot;
            // nation loot
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public List<DBAttack> getDuplicateVictories() {
        ArrayList<DBAttack> list = new ArrayList<>();
        try (PreparedStatement stmt= prepareQuery("select * FROM `attacks2` WHERE (attack_type = 1) group by war_id having count(*) > 1")) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(createAttack(rs));
                }
            }
            return list;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public List<DBAttack> getAttacks(int nation_id) {
        ArrayList<DBAttack> list = new ArrayList<>();
        try (PreparedStatement stmt= prepareQuery("select * FROM `attacks2` WHERE (attacker_nation_id = ? OR defender_nation_id = ?)")) {
            stmt.setInt(1, nation_id);
            stmt.setInt(2, nation_id);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(createAttack(rs));
                }
            }
            return list;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }
    public List<DBAttack> getAttacks(int nation_id, long cuttoffMs) {
        return getAttacks(nation_id, cuttoffMs, Long.MAX_VALUE);
    }
    public List<DBAttack> getAttacks(int nation_id, long start, long end) {
        if (start <= 0 && end == Long.MAX_VALUE) return getAttacks(nation_id);
        ArrayList<DBAttack> list = new ArrayList<>();
        try (PreparedStatement stmt= prepareQuery("select * FROM `attacks2` WHERE date > ? " + (end < Long.MAX_VALUE ? "AND date < ? " : "") +" AND (attacker_nation_id = ? OR defender_nation_id = ?)")) {
            int i = 1;
            stmt.setLong(i++, start);
            if (end < Long.MAX_VALUE) {
                stmt.setLong(i++, end);
            }
            stmt.setInt(i++, nation_id);
            stmt.setInt(i++, nation_id);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(createAttack(rs));
                }
            }
            return list;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public List<DBAttack> getAttacks(long cuttoffMs, AttackType type) {
        ArrayList<DBAttack> list = new ArrayList<>();
        try (PreparedStatement stmt= prepareQuery("select * FROM `attacks2` WHERE date > ? AND attack_type = ?")) {
            stmt.setLong(1, cuttoffMs);
            stmt.setInt(2, type.ordinal());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(createAttack(rs));
                }
            }
            return list;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public List<DBAttack> getAttacksByWarIds(Collection<Integer> warIds, long start, long end) {
        if (warIds.size() > 100) {
            IntOpenHashSet warIdsFast = new IntOpenHashSet(warIds);
            return getAttacks(f -> f.epoch >= start && f.epoch <= end && warIdsFast.contains(f.war_id));
        }
        List<String> requirements = new ArrayList<>();
        if (start > 0) {
            requirements.add("date > " + start);
        }
        if (end < System.currentTimeMillis()) {
            requirements.add("date < " + end);
        }
        String ids = StringMan.getString(warIds).replaceAll(" ", "");
        requirements.add("(war_id in " + ids + ")");
        ArrayList<DBAttack> list = new ArrayList<>();
        try (PreparedStatement stmt= prepareQuery("select * FROM `attacks2` WHERE " + StringMan.join(requirements, " AND "))) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(createAttack(rs));
                }
            }
            return list;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public List<DBAttack> getAttacksByWarIds(Collection<Integer> warIds) {
        if (warIds.size() > 100) {
            IntOpenHashSet warIdsFast = new IntOpenHashSet(warIds);
            return getAttacks(f -> warIdsFast.contains(f.war_id));
        }
        ArrayList<DBAttack> list = new ArrayList<>();
        String ids = StringMan.getString(warIds).replaceAll(" ", "");
        try (PreparedStatement stmt= prepareQuery("select * FROM `attacks2` WHERE (war_id in " + ids + ")")) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(createAttack(rs));
                }
            }
            return list;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public List<DBAttack> getAttacksByWars(List<DBWar> wars, long cuttoffMs) {
        return getAttacksByWars(wars, cuttoffMs, Long.MAX_VALUE);
    }

    public List<DBAttack> getAttacksByWars(List<DBWar> wars, long start, long end) {
        Set<Integer> warIds = new HashSet<>();
        for (DBWar war : wars) warIds.add(war.warId);
        return getAttacksByWarIds(warIds, start, end);
    }

    public List<DBAttack> getAttacks(Set<Integer> nationIds, long cuttoffMs) {
        return getAttacks(nationIds, cuttoffMs, Long.MAX_VALUE);
    }
    public List<DBAttack> getAttacks(Set<Integer> nationIds, long start, long end) {
        ArrayList<DBAttack> list = new ArrayList<>();
        String ids = StringMan.getString(nationIds).replaceAll(" ", "");
        try (PreparedStatement stmt= prepareQuery("select * FROM `attacks2` WHERE date > ? " + (end < Long.MAX_VALUE ? "AND date < ? " : "") + "AND (attacker_nation_id in " + ids + " AND defender_nation_id in " + ids + ")")) {
            stmt.setLong(1, start);
            if (end < Long.MAX_VALUE) {
                stmt.setLong(2, end);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(createAttack(rs));
                }
            }
            return list;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public List<DBAttack> getAttacksAny(Set<Integer> nationIds, long cuttoffMs) {
        return getAttacksAny(nationIds, cuttoffMs, Long.MAX_VALUE);
    }
    public List<DBAttack> getAttacksAny(Set<Integer> nationIds, long start, long end) {
        ArrayList<DBAttack> list = new ArrayList<>();
        String ids = StringMan.getString(nationIds).replaceAll(" ", "");
        try (PreparedStatement stmt= prepareQuery("select * FROM `attacks2` WHERE date > ? " + (end < Long.MAX_VALUE ? "AND date < ? " : "") + "AND (attacker_nation_id in " + ids + " OR defender_nation_id in " + ids + ")")) {
            stmt.setLong(1, start);
            if (end < Long.MAX_VALUE) {
                stmt.setLong(2, end);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(createAttack(rs));
                }
            }
            return list;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public int countWarsByNation(int nation_id, long date) {
        String query = "SELECT COUNT(*) from wars WHERE date > ? AND (attacker_id = ? OR defender_id = ?)";
        int[] result = new int[1];
        query(query, (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setLong(1, date);
            stmt.setInt(2, nation_id);
            stmt.setInt(3, nation_id);
        }, (ThrowingConsumer<ResultSet>) elem -> result[0] = elem.getInt(1));
        return result[0];
    }

    public int countOffWarsByNation(int nation_id, long date) {
        String query = "SELECT COUNT(*) from wars WHERE attacker_id = ? AND date > ?";
        int[] result = new int[1];
        query(query, (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, nation_id);
            stmt.setLong(2, date);
        }, (ThrowingConsumer<ResultSet>) elem -> result[0] = elem.getInt(1));
        return result[0];
    }

    public int countDefWarsByNation(int nation_id, long date) {
        String query = "SELECT COUNT(*) from wars WHERE defender_id = ? AND date > ?";
        int[] result = new int[1];
        query(query, (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, nation_id);
            stmt.setLong(2, date);
        }, (ThrowingConsumer<ResultSet>) elem -> result[0] = elem.getInt(1));
        return result[0];
    }

    public int countWarsByAlliance(int alliance_id, long date) {
        String query = "SELECT COUNT(*) from wars WHERE date > ? AND (attacker_aa = ? OR defender_a = ?)";
        int[] result = new int[1];
        query(query, (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setLong(1, date);
            stmt.setInt(2, alliance_id);
            stmt.setInt(3, alliance_id);
        }, (ThrowingConsumer<ResultSet>) elem -> result[0] = elem.getInt(1));
        return result[0];
    }
}
