package link.locutus.discord.db;

import com.politicsandwar.graphql.model.*;
import com.ptsmods.mysqlw.table.ColumnType;
import com.ptsmods.mysqlw.table.TableIndex;
import com.ptsmods.mysqlw.table.TablePreset;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv3.PoliticsAndWarV3;
import link.locutus.discord.commands.external.guild.SyncBounties;
import link.locutus.discord.commands.rankings.builder.RankBuilder;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.CityInfraLand;
import link.locutus.discord.db.entities.CounterStat;
import link.locutus.discord.db.entities.CounterType;
import link.locutus.discord.db.entities.DBBounty;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.entities.Treaty;
import link.locutus.discord.db.entities.WarStatus;
import link.locutus.discord.event.AttackEvent;
import link.locutus.discord.event.BountyCreateEvent;
import link.locutus.discord.event.BountyRemoveEvent;
import link.locutus.discord.pnw.DBNation;
import link.locutus.discord.pnw.PNWUser;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.scheduler.ThrowingConsumer;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.AlertUtil;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.FileUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.math.ArrayUtil;
import link.locutus.discord.util.update.NationUpdateProcessor;
import link.locutus.discord.util.update.WarUpdateProcessor;
import link.locutus.discord.apiv2.PoliticsAndWarV2;
import link.locutus.discord.apiv1.domains.subdomains.DBAttack;
import link.locutus.discord.apiv1.domains.subdomains.SWarContainer;
import link.locutus.discord.apiv1.domains.subdomains.WarAttacksContainer;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.WarType;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.apiv1.enums.TreatyType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.Role;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.util.AbstractMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class WarDB extends DBMainV2 {
    private final ArrayDeque<DBAttack> allAttacks = new ArrayDeque<>();
    public WarDB() throws SQLException, ClassNotFoundException {
        super(Settings.INSTANCE.DATABASE, "war");
    }

    @Override
    public void createTables() {
        {
            TablePreset.create("BOUNTIES_V3")
                    .putColumn("id", ColumnType.INT.struct().setPrimary(true).setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("date", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("nation_id", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("posted_by", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("attack_type", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .putColumn("amount", ColumnType.INT.struct().setNullAllowed(false).configure(f -> f.apply(null)))
                    .create(getDb());
        }

        {
            String create = "CREATE TABLE IF NOT EXISTS `WARS` (`id` INT NOT NULL PRIMARY KEY, `attacker_id` INT NOT NULL, `defender_id` INT NOT NULL, `attacker_aa` INT NOT NULL, `defender_aa` INT NOT NULL, `war_type` INT NOT NULL, `status` INT NOT NULL, `date` INT NOT NULL)";
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
                    "`date` INT NOT NULL, " +
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
                    "money_looted INT," +
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
                switch (treaty.type) {
                    case MDP:
                    case MDOAP:
                    case ODP:
                    case ODOAP:
                    case PROTECTORATE:
                        int other = treaty.from == allianceId ? treaty.to : treaty.from;
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
        String queryStr = "SELECT * FROM COUNTER_STATS LEFT JOIN WARS ON COUNTER_STATS.id = WARS.id WHERE COUNTER_STATS.id IN (SELECT id FROM WARS WHERE defender_aa IN " + StringMan.getString(alliances) + ")";
        try (PreparedStatement stmt= getConnection().prepareStatement(queryStr)) {
            try (ResultSet rs = stmt.executeQuery()) {
                List<Map.Entry<DBWar, CounterStat>> wars = new ArrayList<>();
                while (rs.next()) {
                    int id = rs.getInt("id");
                    CounterStat stat = new CounterStat();
                    stat.isActive = rs.getBoolean("active");
                    stat.type = CounterType.values[rs.getInt("type")];
                    DBWar war = create(rs);
                    AbstractMap.SimpleEntry<DBWar, CounterStat> entry = new AbstractMap.SimpleEntry<>(war, stat);
                    wars.add(entry);
                }
                return wars;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void updateCounters() {
        String queryStr = "SELECT * FROM WARS WHERE status != 0 AND attacker_aa != 0 AND defender_aa != 0 AND id NOT IN (SELECT id FROM COUNTER_STATS)";

        try (PreparedStatement stmt= getConnection().prepareStatement(queryStr)) {
            try (ResultSet rs = stmt.executeQuery()) {
                List<DBWar> wars = new ArrayList<>();
                while (rs.next()) {
                    DBWar war = create(rs);
                    wars.add(war);
                }
                for (int i = 0; i < wars.size(); i++) {
                    DBWar war = wars.get(i);
                    updateCounter(war);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
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
            switch (entry.getValue().type) {
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
            switch (entry.getValue().type) {
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

    @Deprecated
    private Map<Integer, Set<DBBounty>> getBounties_legacy() {
            Map<Integer, Set<DBBounty>> map = new HashMap<>();
        query("SELECT * FROM `BOUNTIES` ORDER BY date DESC", (ThrowingConsumer<PreparedStatement>) stmt -> {

        }, (ThrowingConsumer<ResultSet>) rs -> {
            while (rs.next()) {
                DBBounty bounty = DBBounty.fromLegacy(rs);
                map.computeIfAbsent(bounty.getNationId(), f -> new LinkedHashSet<>()).add(bounty);
            }
        });
        return map;
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

        PoliticsAndWarV3 v3 = Locutus.imp().getPnwApi().getV3();
        Collection<Bounty> bounties = v3.fetchBounties(null, f -> f.all$(0));

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
        update("INSERT OR REPLACE INTO `BOUNTIES_V3`(`id, `date`, `nation_id`, `posted_by`, `attack_type`, `amount`) VALUES(?, ?, ?, ?, ?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
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

    public boolean updateWars() throws IOException {
        List<SWarContainer> wars = Locutus.imp().getPnwApi().getWarsByAmount(5000).getWars();
        List<DBWar> dbWars = new ArrayList<>();
        int minId = Integer.MAX_VALUE;
        for (SWarContainer container : wars) {
            if (container == null) continue;
            DBWar war = new DBWar(container);
            dbWars.add(war);
            minId = Math.min(minId, war.warId - 1);
        }
        Map<Integer, DBWar> lastActive = getWarsAbove(minId);

        List<DBWar> prevWars = new ArrayList<>();
        List<DBWar> newWars = new ArrayList<>();

        for (DBWar war : dbWars) {
            DBWar existing = lastActive.get(war.warId);
            if (war.equals(existing)) continue;
            prevWars.add(existing);
            newWars.add(war);

            DBNation attacker = Locutus.imp().getNationDB().getNation(war.attacker_id);
            DBNation defender = Locutus.imp().getNationDB().getNation(war.defender_id);
            if (war.attacker_aa == 0 && attacker != null) {
                war.attacker_aa = attacker.getAlliance_id();
            }
            if (war.defender_aa == 0 && defender != null) {
                war.defender_aa = defender.getAlliance_id();
            }

            addWar(war);
        }

        List<DBWar> active = getWarByStatus(WarStatus.ACTIVE, WarStatus.DEFENDER_OFFERED_PEACE, WarStatus.ATTACKER_OFFERED_PEACE);
        Map<Integer, DBWar> warMap = new HashMap<>();
        for (DBWar war : active) warMap.put(war.warId, war);
        List<DBAttack> attacks = Locutus.imp().getWarDb().getAttacksByWars(active, 0);
        for (DBAttack attack : attacks) {
            if (attack.attack_type != AttackType.VICTORY) continue;
            DBWar war = warMap.get(attack.war_id);
            war.status = attack.victor == war.attacker_id ? WarStatus.ATTACKER_VICTORY : WarStatus.DEFENDER_VICTORY;
            addWar(war);
            active.remove(war);
        }

        Map<DBWar, DBWar> expiredWars = new HashMap<>();

        for (DBWar war : active) {
            if (war.warId >= minId || war.status == WarStatus.EXPIRED) continue;

            long currentTurn = TimeUtil.getTurn();
            long turn = TimeUtil.getTurn(war.date);
            if (currentTurn - turn >= 60) {
                expiredWars.put(new DBWar(war), war);
                war.status = WarStatus.EXPIRED;
                addWar(war);
            } else if (war.status != WarStatus.EXPIRED){
                DBNation attacker = Locutus.imp().getNationDB().getNation(war.attacker_id);
                DBNation defender = Locutus.imp().getNationDB().getNation(war.defender_id);
                if ((attacker == null || defender == null) && war.date < System.currentTimeMillis() - TimeUnit.HOURS.toMillis(2)) {
                    war.status = WarStatus.EXPIRED;
                    expiredWars.put(new DBWar(war), war);
                    addWar(war);
                }
            }
        }

        List<Map.Entry<DBWar, DBWar>> warUpdatePreviousNow = new ArrayList<>();

        for (int i = 0 ; i < prevWars.size(); i++) {
            DBWar previous = prevWars.get(i);
            DBWar newWar = newWars.get(i);
            if (previous == null) {
                DBNation attacker = newWar.getNation(true);
                DBNation defender = newWar.getNation(false);
                if (attacker.getOff() == 0) attacker.setOff(1);
                if (defender.getDef() == 0) defender.setDef(1);
            }

            warUpdatePreviousNow.add(new AbstractMap.SimpleEntry<>(previous, newWar));
        }

        for (Map.Entry<DBWar, DBWar> entry : expiredWars.entrySet()) {
            warUpdatePreviousNow.add(new AbstractMap.SimpleEntry<>(entry.getKey(), entry.getValue()));
        }

        if (!warUpdatePreviousNow.isEmpty()) {
            try {
                WarUpdateProcessor.processWars(warUpdatePreviousNow);
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }

        return true;
    }

    public void addWar(DBWar war) {
        update("INSERT OR REPLACE INTO `wars`(`id`, `attacker_id`, `defender_id`, `attacker_aa`, `defender_aa`, `war_type`, `status`, `date`) VALUES(?, ?, ?, ?, ?, ?, ?, ?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, war.warId);
            stmt.setLong(2, war.attacker_id);
            stmt.setInt(3, war.defender_id);
            stmt.setInt(4, war.attacker_aa);
            stmt.setInt(5, war.defender_aa);
            stmt.setInt(6, war.warType.ordinal());
            stmt.setInt(7, war.status.ordinal());
            stmt.setLong(8, war.date);
        });
    }

    public Map<Integer, DBWar> getWars(WarStatus status) {
            Map<Integer, DBWar> map = new HashMap<>();
        try (PreparedStatement stmt= prepareQuery("SELECT * FROM `wars` WHERE status = ?")) {
            stmt.setInt(1, status.ordinal());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    DBWar war = create(rs);
                    map.put(war.warId, war);
                }
            }
            return map;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public Map<Integer, DBWar> getActiveWars() {
        return getWarsSince(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(5));
    }

    public Map<Integer, DBWar> getWarsSince(long date) {
        Map<Integer, DBWar> map = new HashMap<>();
        try (PreparedStatement stmt= prepareQuery("SELECT * FROM `wars` WHERE date > ?")) {
            stmt.setLong(1, date);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    DBWar war = create(rs);
                    map.put(war.warId, war);
                }
            }
            return map;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public Map<Integer, DBWar> getWarsAbove(int minId) {
            Map<Integer, DBWar> map = new HashMap<>();
        try (PreparedStatement stmt= prepareQuery("SELECT * FROM `wars` WHERE id > ?")) {
            stmt.setInt(1, minId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    DBWar war = create(rs);
                    map.put(war.warId, war);
                }
            }
            return map;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public Map<Integer, DBWar> getWars() {
            Map<Integer, DBWar> map = new HashMap<>();
        try (PreparedStatement stmt= prepareQuery("SELECT * FROM `wars` ORDER BY date ASC")) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    DBWar war = create(rs);
                    map.put(war.warId, war);
                }
            }
            return map;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public Map<Integer, List<DBWar>> getWars(Set<Integer> attackers, Set<Integer> defenders) {
        Map<Integer, List<DBWar>> map = new HashMap<>();
        try (PreparedStatement stmt= prepareQuery("SELECT * FROM `wars` ORDER BY date ASC")) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int attacker = rs.getInt("attacker_id");
                    int defender = rs.getInt("defender_id");
                    if (attackers.contains(attacker) || defenders.contains(defender)) {
                        DBWar war = create(rs);
                        List<DBWar> list = map.get(attacker);
                        if (list == null) {
                            map.put(attacker, list = new LinkedList<>());
                        }
                        list.add(war);
                    }
                }
            }
            return map;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public Map<Integer, List<DBWar>> getActiveWarsByAttacker(Set<Integer> attackers, Set<Integer> defenders, WarStatus... statuses) {
        Map<Integer, List<DBWar>> map = new HashMap<>();
        Set<Integer> statusIds = new HashSet<>();
        for (WarStatus status : statuses) {
            statusIds.add(status.ordinal());
        }
        try (PreparedStatement stmt= prepareQuery("SELECT * FROM `wars` WHERE status in " + StringMan.getString(statusIds) + " ORDER BY date ASC")) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int attacker = rs.getInt("attacker_id");
                    int defender = rs.getInt("defender_id");
                    if (attackers.contains(attacker) || defenders.contains(defender)) {
                        DBWar war = create(rs);
                        List<DBWar> list = map.get(attacker);
                        if (list == null) {
                            map.put(attacker, list = new LinkedList<>());
                        }
                        list.add(war);
                    }
                }
            }
            return map;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
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
        try (PreparedStatement stmt= prepareQuery("SELECT * FROM `wars` WHERE `id` = ?")) {
            stmt.setInt(1, warId);
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

    public List<DBWar> getWars(int nation1, int nation2, long start, long end) {
            List<DBWar> list = new ArrayList<>();
        try (PreparedStatement stmt= prepareQuery("SELECT * FROM `wars` WHERE (attacker_id = ? OR defender_id = ? OR attacker_id = ? OR defender_id = ?) AND DATE > ? AND DATE < ?")) {

            stmt.setInt(1, nation1);
            stmt.setInt(2, nation2);
            stmt.setInt(3, nation2);
            stmt.setInt(4, nation1);
            stmt.setLong(5, start);
            stmt.setLong(6, end);
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

    public DBWar getActiveWarByNation(int attacker, int defender) {
        long cutOff = TimeUtil.getTimeFromTurn(TimeUtil.getTurn() - 61);
        String query = "SELECT * from `wars` where date > ? AND attacker_id = ? AND defender_id = ? AND status = ? ORDER BY date DESC";
        try (PreparedStatement stmt= getConnection().prepareStatement(query)) {
            stmt.setLong(1, cutOff);
            stmt.setInt(2, attacker);;
            stmt.setInt(3, defender);;
            stmt.setInt(4, WarStatus.ACTIVE.ordinal());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    DBWar war = create(rs);
                    if (war.isActive()) return war;
                }
            }
            return null;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public List<DBWar> getWarsByNation(int nation, WarStatus status) {
        List<DBWar> list = new ArrayList<>();
        try (PreparedStatement stmt= prepareQuery("select * FROM `wars` WHERE status = ? AND (attacker_id = ? OR defender_id = ?)")) {
            stmt.setInt(1, status.ordinal());
            stmt.setInt(2, nation);
            stmt.setInt(3, nation);
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

    public List<DBWar> getActiveWarsByAlliance(Set<Integer> attackerAA, Set<Integer> defenderAA) {
        List<DBWar> list = new ArrayList<>();

        StringBuilder query = new StringBuilder();
        if (attackerAA != null) query.append("attacker_aa in " + StringMan.getString(attackerAA));
        if (defenderAA != null) {
            if (attackerAA != null) query.append(" AND ");
            query.append("defender_aa in " + StringMan.getString(defenderAA));
        }

        try (PreparedStatement stmt= prepareQuery("select * FROM `wars` WHERE (" + query +" AND (status = ?)) ORDER BY date DESC")) {
            stmt.setInt(1, WarStatus.ACTIVE.ordinal());
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

    public List<DBWar> getWarsByAlliance(Set<Integer> attackerAA, Set<Integer> defenderAA) {
        List<DBWar> list = new ArrayList<>();

        StringBuilder query = new StringBuilder();
        if (attackerAA != null) query.append("attacker_aa in " + StringMan.getString(attackerAA));
        if (defenderAA != null) {
            if (attackerAA != null) query.append(" AND ");
            query.append("defender_aa in " + StringMan.getString(defenderAA));
        }

        try (PreparedStatement stmt= prepareQuery("select * FROM `wars` WHERE (" + query +") ORDER BY date DESC")) {
            stmt.setInt(1, WarStatus.ACTIVE.ordinal());
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

    public List<DBWar> getWarsByAlliance(int attacker) {
        List<DBWar> list = new ArrayList<>();
        try (PreparedStatement stmt= prepareQuery("select * FROM `wars` WHERE (attacker_aa = ? OR defender_aa = ?) ORDER BY date DESC")) {
            stmt.setInt(1, attacker);
            stmt.setInt(2, attacker);
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

    public List<DBWar> getWarsByNation(int nationId) {
        List<DBWar> list = new ArrayList<>();
        query("SELECT * FROM `wars` WHERE attacker_id = ? OR defender_id = ? ORDER BY DATE DESC", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, nationId);
            stmt.setInt(2, nationId);
        }, (ThrowingConsumer<ResultSet>) rs -> {
            while (rs.next()) {
                DBWar war = create(rs);
                list.add(war);
            }
        });
        return list;
    }

    public List<DBWar> getWarsByNation(int attacker, int defender, long cutOff) {
        List<DBWar> list = new ArrayList<>();
        try (PreparedStatement stmt= prepareQuery("select * FROM `wars` WHERE date > ? AND (attacker_id = ? OR defender_id = ?) ORDER BY DATE DESC")) {
            stmt.setLong(1, cutOff);
            stmt.setInt(2, attacker);
            stmt.setInt(3, defender);
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

    public DBWar getLastWar(int attacker, int defender) {
        try (PreparedStatement stmt= prepareQuery("select * FROM `wars` WHERE attacker_id = ? OR defender_id = ? ORDER BY DATE DESC LIMIT 1")) {
            stmt.setInt(1, attacker);
            stmt.setInt(2, defender);
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

    public DBWar getLastWar(int attacker) {
        try (PreparedStatement stmt= prepareQuery("select * FROM `wars` WHERE attacker_id = ? ORDER BY DATE DESC LIMIT 1")) {
            stmt.setInt(1, attacker);
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

    public int getWarsWon(int nationId) {
        String query = "select count(case status when " + WarStatus.ATTACKER_VICTORY.ordinal() + " then 1 else null end) FROM `wars` WHERE (attacker_id = ? OR defender_id = ?)";
        try (PreparedStatement stmt= prepareQuery(query)) {
            stmt.setInt(1, nationId);
            stmt.setInt(2, nationId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }

    public int getWarsLost(int nationId) {
        String query = "select count(case status when " + WarStatus.DEFENDER_VICTORY.ordinal() + " then 1 else null end) FROM `wars` WHERE (attacker_id = ? OR defender_id = ?)";
        try (PreparedStatement stmt= prepareQuery(query)) {
            stmt.setInt(1, nationId);
            stmt.setInt(2, nationId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }

    public int getWarsWonOrLost(int nationId) {
        String query = "select count(case status when " + WarStatus.ATTACKER_VICTORY.ordinal() + " then 1 when " + WarStatus.DEFENDER_VICTORY.ordinal() + " then 1 else null end) FROM `wars` WHERE (attacker_id = ? OR defender_id = ?)";
        try (PreparedStatement stmt= prepareQuery(query)) {
            stmt.setInt(1, nationId);
            stmt.setInt(2, nationId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }

    public List<DBWar> getWarsByNation(int nationId, WarStatus... statuses) {
            List<DBWar> list = new ArrayList<>();
            Set<Integer> statusIds = new HashSet<>();
            for (WarStatus status : statuses) {
                statusIds.add(status.ordinal());
            }
        try (PreparedStatement stmt= prepareQuery("select * FROM `wars` WHERE (attacker_id = ? OR defender_id = ?) AND status in " + StringMan.getString(statusIds) + " ORDER BY DATE DESC")) {
            stmt.setInt(1, nationId);
            stmt.setInt(2, nationId);
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

    public List<DBWar> getActiveWars(Set<Integer> alliances, WarStatus... statuses) {
        long cutOff = TimeUtil.getTimeFromTurn(TimeUtil.getTurn() - 61);
        String aaArg = StringMan.getString(alliances);
        Set<Integer> statusIds = new HashSet<>();
        for (WarStatus status : statuses) {
            statusIds.add(status.ordinal());
        }
        String query = "SELECT * from `wars` where date > ? AND (attacker_aa in " + aaArg + " or defender_aa in " + aaArg + ") AND status in " + StringMan.getString(statusIds);
        List<DBWar> list = new ArrayList<>();
        try (PreparedStatement stmt= getConnection().prepareStatement(query)) {
            stmt.setLong(1, cutOff);
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

    public List<DBWar> getWarByStatus(WarStatus... statuses) {
        Set<Integer> statusIds = new HashSet<>();
        for (WarStatus status : statuses) {
            statusIds.add(status.ordinal());
        }
        String query = "SELECT * from `wars` WHERE status in " + StringMan.getString(statusIds);
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

    public List<DBWar> getWars(Set<Integer> alliances, long cutOff) {
        String aaArg = StringMan.getString(alliances);
        String query = "SELECT * from `wars` where date > ? AND (attacker_aa in " + aaArg + " or defender_aa in " + aaArg + ")";
            List<DBWar> list = new ArrayList<>();
        try (PreparedStatement stmt= getConnection().prepareStatement(query)) {
            stmt.setLong(1, cutOff);
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

    public List<DBWar> getWarsById(Set<Integer> warIds) {
        String query = "SELECT * from `wars` WHERE `id` in " + StringMan.getString(warIds);
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


        String query = "SELECT * FROM `wars` WHERE " + StringMan.join(requirements, " AND ");
        return query;
    }

    public void addAttack(DBAttack attack) {
        synchronized (allAttacks) {
            getAttacks();
            allAttacks.add(attack);
        }
        // ctiy_id not used anymore, but sqlite doesn't allow removing
        String query = "INSERT OR REPLACE INTO `attacks2`(`war_attack_id`, `date`, `war_id`, `attacker_nation_id`, `defender_nation_id`, `attack_type`, `victor`, `success`, attcas1, attcas2, defcas1, defcas2, defcas3,city_id,infra_destroyed,improvements_destroyed,money_looted,looted,loot,pct_looted,city_infra_before,infra_destroyed_value,att_gas_used,att_mun_used,def_gas_used,def_mun_used) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        update(query, (ThrowingConsumer<PreparedStatement>) stmt -> {
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
            stmt.setLong(14, 0);
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
        });
    }

    public boolean updateAttacks() {
        return updateAttacks(null, true);
    }

    private Integer getLatestAttackId() {
        try (PreparedStatement stmt= prepareQuery("select war_attack_id FROM attacks2 ORDER BY war_attack_id DESC LIMIT 1")) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    return rs.getInt("war_attack_id");
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    private List<DBAttack> getAttackSince(Integer maxId) {
        PoliticsAndWarV3 v3 = Locutus.imp().getPnwApi().getV3();
        List<WarAttack> attacks = v3.fetchAttacks(r -> {
            if (maxId != null) r.setMin_id(maxId + 1);
            QueryWarattacksOrderByOrderByClause order = QueryWarattacksOrderByOrderByClause.builder()
                    .setColumn(QueryWarattacksOrderByColumn.ID)
                    .setOrder(SortOrder.ASC)
                    .build();
            r.setOrderBy(List.of(order));
        }, p -> {
            p.id();
            p.date();
            p.war_id();
            p.att_id();
            p.def_id();
            p.type();
            p.victor();
            p.success();
            p.attcas1();
            p.attcas2();
            p.defcas1();
            p.defcas2();
            p.aircraft_killed_by_tanks();
            p.infra_destroyed();
            p.improvements_lost();
            p.money_stolen();
            p.loot_info();
            p.city_infra_before();
            p.infra_destroyed_value();
            p.att_gas_used();
            p.att_mun_used();
            p.def_gas_used();
            p.def_mun_used();
        });
        return attacks.stream().map(DBAttack::new).collect(Collectors.toList());
    }

    public synchronized boolean updateAttacks(Integer maxId, boolean runAlerts) {
        if (maxId == null) maxId = getLatestAttackId();

        List<DBAttack> newAttacks = getAttackSince(maxId);

        Map<DBAttack, Double> attackInfraPctMembers = new HashMap<>();
        Map<DBAttack, Double> attackInfraPctOthers = new HashMap<>();
        List<DBAttack> dbAttacks = new ArrayList<>();

        long now = System.currentTimeMillis();
        for (DBAttack attack : newAttacks) {
            if (attack.epoch > now) {
                attack.epoch = now;
            }
            if (attack.attack_type == AttackType.GROUND && attack.money_looted != 0 && Settings.INSTANCE.LEGACY_SETTINGS.ATTACKER_DESKTOP_ALERTS.contains(attack.attacker_nation_id)) {
                AlertUtil.openDesktop(attack.toUrl());
            }
            if ((attack.attack_type == AttackType.NUKE || attack.attack_type == AttackType.MISSILE) && attack.success == 0) {
                attack.infra_destroyed_value = 0;
            }

            {
                if (attack.attack_type == AttackType.VICTORY && attack.infraPercent_cached > 0) {
                    DBNation defender = Locutus.imp().getNationDB().getNation(attack.defender_nation_id);
                    DBWar war = Locutus.imp().getWarDb().getWar(attack.getWar_id());

                    war.status = attack.victor == attack.attacker_nation_id ? WarStatus.ATTACKER_VICTORY : WarStatus.DEFENDER_VICTORY;
                    Locutus.imp().getWarDb().addWar(war);

                    if (defender != null && attack.infra_destroyed_value == 0) {
                        double pct = attack.infraPercent_cached;

                        DBAttack existingAttack = null;//attackMap.get(attack.war_attack_id);
                        if (existingAttack == null) {
                            if (defender.getPosition() > 1) {
                                attackInfraPctMembers.put(attack, pct);
                            } else {
                                attackInfraPctOthers.put(attack, pct);
                            }
                        } else {
                            attack.infra_destroyed = existingAttack.infra_destroyed;
                            attack.infra_destroyed_value = existingAttack.infra_destroyed_value;
                            attack.city_infra_before = existingAttack.city_infra_before;
                        }
                    }
                }
            }

            updateLootEstimate(attack, allianceBankEstimateCache, nationLootEstimateCache);
            dbAttacks.add(attack);
        }

        if (!attackInfraPctMembers.isEmpty() || !attackInfraPctOthers.isEmpty())
        { // update infra
            if (!attackInfraPctMembers.isEmpty()) {
                attackInfraPctMembers.putAll(attackInfraPctOthers);
                attackInfraPctOthers.clear();

                Map<Integer, Map<Integer, CityInfraLand>> cityInfraByNation = Locutus.imp().getNationDB().updateCities();
                for (Map.Entry<DBAttack, Double> entry : attackInfraPctMembers.entrySet()) {
                    DBAttack attack = entry.getKey();
                    double pct = entry.getValue();
                    Map<Integer, CityInfraLand> cities = cityInfraByNation.get(attack.defender_nation_id);
                    if (cities == null || cities.isEmpty()) {
                        attackInfraPctOthers.put(attack, pct);
                        continue;
                    }
                    attack.infra_destroyed = 0d;
                    for (Map.Entry<Integer, CityInfraLand> cityEntry : cities.entrySet()) {
                        CityInfraLand city = cityEntry.getValue();
                        double infraStart = city.infra;
                        double infraEnd = (city.infra) / (1 - pct);

                        attack.infra_destroyed += infraEnd - infraStart;
                        if (infraEnd > infraStart) {
                            attack.infra_destroyed_value += PnwUtil.calculateInfra(infraStart, infraEnd);
                        }
                    }
                }
            }

            for (Map.Entry<DBAttack, Double> entry : attackInfraPctOthers.entrySet()) {
                DBAttack attack = entry.getKey();
                double pct = entry.getValue();
                DBNation defender = Locutus.imp().getNationDB().getNation(attack.defender_nation_id);
                if (defender == null) continue;
                int avgInfra = defender.getAvg_infra();
                long totalInfra = avgInfra * defender.getCities();
                attack.infra_destroyed = totalInfra * pct;
                attack.infra_destroyed_value = PnwUtil.calculateInfra(avgInfra, avgInfra / (1d - pct)) * defender.getCities();
            }
        }

        { // add to db
            for (DBAttack attack : dbAttacks) {
                addAttack(attack);
            }
        }

        if (runAlerts) {
            Locutus.imp().getExecutor().submit(new Runnable() {
                @Override
                public void run() {
                    for (DBAttack attack : dbAttacks) {
                        if (attack.attack_type == AttackType.VICTORY) {
                            DBNation loser = Locutus.imp().getNationDB().getNation(attack.defender_nation_id);
                            if (loser == null) continue;
                            loser.getBeigeTurns(true);
                        }
                    }
                }
            });

            Locutus.imp().getExecutor().submit(new Runnable() {
                @Override
                public void run() {
                    for (DBAttack attack : dbAttacks) {
                        Locutus.post(new AttackEvent(attack));
                    }

                    NationUpdateProcessor.updateBlockades();
                }
            });
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
        if (cuttoffMs < System.currentTimeMillis() - TimeUnit.DAYS.toMillis(20)) {
            return getAttacks(f -> f.epoch > cuttoffMs);
        }
        ArrayList<DBAttack> list = new ArrayList<>();
        try (PreparedStatement stmt= prepareQuery("select * FROM `attacks2` WHERE date > ?")) {
            stmt.setLong(1, cuttoffMs);
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

    private Map<Integer, double[]> allianceBankEstimateCache = null;
    private final Object aaEstCache = new Object();

    public Map<Integer, double[]> getAllianceBankEstimate() {
        if (allianceBankEstimateCache != null) {
            return allianceBankEstimateCache;
        }
        synchronized (aaEstCache) {
            if (allianceBankEstimateCache != null) return allianceBankEstimateCache;
            return allianceBankEstimateCache = getAllianceBankEstimate(0, true);
        }
    }

    public Map<ResourceType, Double> getLootEstimate(DBAttack attack) {
        Map<ResourceType, Double> loot = new HashMap<>(attack.getLoot());
        Double pct = attack.getLootPercent();
        if (pct == 0) pct = 0.1;
        double factor = 1d / pct;

        for (Map.Entry<ResourceType, Double> entry : loot.entrySet()) {
            entry.setValue((entry.getValue() * factor - entry.getValue()));
        }
        return loot;
    }

    public void updateLootEstimate(DBAttack attack, Map<Integer, double[]> allianceLoot, Map<Integer, Map.Entry<Long, double[]>> nationLoot) {
        updateLootEstimate(attack, allianceLoot, nationLoot, true);
    }

    public void updateLootEstimate(DBAttack attack, Map<Integer, double[]> allianceLoot, Map<Integer, Map.Entry<Long, double[]>> nationLoot, boolean override) {
        if (attack.attack_type == AttackType.VICTORY && nationLoot != null && attack.loot != null) {
            Map<ResourceType, Double> loot = attack.getLoot();
            Integer looted = attack.getLooted();
            if (looted != null && looted != 0) {
                Double pct = attack.getLootPercent();
                if (pct == 0) pct = 0.1;
                double factor = 1/pct;

                double[] lootCopy = attack.loot.clone();
                for (int i = 0; i < lootCopy.length; i++) {
                    lootCopy[i] = (lootCopy[i] * factor) - lootCopy[i];
                }

                if (override) {
                    nationLoot.put(looted, new AbstractMap.SimpleEntry<>(attack.epoch, lootCopy));
                } else {
                    nationLoot.putIfAbsent(looted, new AbstractMap.SimpleEntry<>(attack.epoch, lootCopy));
                }
            }
        }
        if (attack.attack_type == AttackType.A_LOOT && allianceLoot != null) {
            Map<ResourceType, Double> loot = attack.getLoot();
            Integer allianceId = attack.getLooted();
            if (allianceId != null && allianceId != 0) {
                Double pct = attack.getLootPercent();
                if (pct == 0) pct = 0.1;
                double factor = 1d / pct;

                double[] lootArr = PnwUtil.resourcesToArray(loot);
                for (int i = 0; i < lootArr.length; i++) {
                    lootArr[i] = lootArr[i] * factor - lootArr[i];
                }
//                loot = new HashMap<>(loot);
//                for (Map.Entry<ResourceType, Double> entry : loot.entrySet()) {
//                    entry.setValue((entry.getValue() * factor - entry.getValue()));
//                }
                if (override) {
                    allianceLoot.put(allianceId, lootArr);
                } else {
                    allianceLoot.putIfAbsent(allianceId, lootArr);
                }
            }
        }
    }

    public Map<ResourceType, Double> getAllianceBankEstimate(long cutOff, boolean full, int allianceId, double score) {
        if (allianceId == 0) return Collections.emptyMap();

        double[] allianceLoot = null;

        Map<Integer, double[]> tmp = allianceBankEstimateCache;
        if (tmp != null) {
            allianceLoot = tmp.get(allianceId);
        }

        if (allianceLoot == null) {
            allianceLoot = Locutus.imp().getWarDb().getAllianceBankEstimate(cutOff, false).get(allianceId);
        }
        if (allianceLoot == null) {
            return Collections.emptyMap();
        }
        List<DBNation> nations = Locutus.imp().getNationDB().getNations(Collections.singleton(allianceId));
        nations.removeIf(n -> n.getPosition() <= 1);
        double aaScore = 0;
        for (DBNation nation : nations) {
            aaScore += nation.getScore();
        }
        if (aaScore == 0) return Collections.emptyMap();

        double ratio = (score / aaScore) / (5);
        double percent = Math.min(ratio, 0.33);
        Map<ResourceType, Double> yourLoot = PnwUtil.resourcesToMap(allianceLoot);
        yourLoot = PnwUtil.multiply(yourLoot, percent);
        return yourLoot;
    }

    public Map.Entry<Long, Map<ResourceType, Double>> getDateAndAllianceBankEstimate(int allianceId) {
        try (PreparedStatement stmt= prepareQuery("select * FROM `attacks2` WHERE `attack_type` = 3 AND looted = ? ORDER BY date DESC LIMIT 1")) {
            stmt.setLong(1, allianceId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    DBAttack attack = createAttack(rs);
                    return new AbstractMap.SimpleEntry<>(attack.epoch, getLootEstimate(attack));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public Map<Integer, double[]> getAllianceBankEstimate(long cutOff, boolean full) {
        Map<Integer, double[]> allianceLoot = new ConcurrentHashMap<>();
        try (PreparedStatement stmt= prepareQuery("select * FROM `attacks2` WHERE `attack_type` = 3 AND date > ? ORDER BY date DESC")) {
            stmt.setLong(1, cutOff);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    if (!full) {
                        int victor = rs.getInt("victor");
                        int attacker_nation_id = rs.getInt("attacker_nation_id");
                        int defender_nation_id = rs.getInt("defender_nation_id");
                        int loserId = victor == attacker_nation_id ? defender_nation_id : attacker_nation_id;
                        DBNation loserNation = Locutus.imp().getNationDB().getNation(loserId);
                        if (loserNation == null|| allianceLoot.containsKey(loserNation.getNation_id()));
                    }
                    DBAttack attack = createAttack(rs);
                    updateLootEstimate(attack, allianceLoot, new HashMap<>(), false);
                }
            }
            return allianceLoot;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public List<DBAttack> getAllianceLoot() {
        try (PreparedStatement stmt= prepareQuery("select * FROM `attacks2` WHERE `attack_type` = 3 ORDER BY date DESC")) {
            List<DBAttack> result = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    DBAttack attack = createAttack(rs);
                    result.add(attack);
                }
            }
            return result;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }


    private Map<Integer, Map.Entry<Long, double[]>> nationLootEstimateCache = null;
    private final Object natEstCache = new Object();

    protected void cacheSpyLoot(int nation, long epoch, double[] loot) {
        Map<Integer, Map.Entry<Long, double[]>> tmp = nationLootEstimateCache;
        if (tmp != null) {
            Map.Entry<Long, double[]> existing = tmp.get(nation);
            if (existing == null || existing.getKey() < epoch) {
                existing = tmp.put(nation, new AbstractMap.SimpleEntry<>(epoch, loot));
                if (existing != null && existing.getKey() > epoch) {
                    tmp.put(nation, existing);
                }
            }
        }
    }

    public Map<Integer, Map.Entry<Long, double[]>> getNationLoot() {
        if (nationLootEstimateCache != null) {
            return nationLootEstimateCache;
        }
        synchronized (natEstCache) {
            if (nationLootEstimateCache != null) return nationLootEstimateCache;
            return nationLootEstimateCache = getNationLoot(null, true);
        }
    }

    public Map<Integer, Map.Entry<Long, double[]>> getNationLoot(Integer nationId, boolean includeSpyOps) {
        Map<Integer, Map.Entry<Long, double[]>> tmp = nationLootEstimateCache;
        if (tmp != null) return tmp;
        Map<Integer, Map.Entry<Long, double[]>> nationLoot;

        if (includeSpyOps) {
            Map<Integer, Map.Entry<Long, double[]>> spyLoot = Locutus.imp().getNationDB().getLoot();
            nationLoot = new ConcurrentHashMap<>(spyLoot.size());

            for (Map.Entry<Integer, Map.Entry<Long, double[]>> entry : spyLoot.entrySet()) {
                Map.Entry<Long, double[]> lootPair = entry.getValue();
                Map.Entry<Long, double[]> epochLootPair = Map.entry(TimeUtil.getTimeFromTurn(lootPair.getKey()), lootPair.getValue());
                Map.Entry<Long, double[]> existing = nationLoot.put(entry.getKey(), epochLootPair);
                if (existing.getKey() > epochLootPair.getKey()) nationLoot.put(entry.getKey(), existing);
            }
        } else {
            nationLoot = new ConcurrentHashMap<>();
        }

        // `attacker_nation_id`, `defender_nation_id`
        String nationReq = "";
        if (nationId != null) nationReq = " AND victor != ? AND (attacker_nation_id = ? OR defender_nation_id = ?)";
        try (PreparedStatement stmt= prepareQuery("select * FROM (SELECT * FROM `attacks2` WHERE `attack_type` = 1" + nationReq + " ORDER BY date DESC) AS tmp_table GROUP BY case when victor = attacker_nation_id then defender_nation_id else attacker_nation_id end")) {
            if (nationId != null) {
                stmt.setInt(1, nationId);
                stmt.setInt(2, nationId);
                stmt.setInt(3, nationId);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    DBAttack attack = createAttack(rs);
                    if (attack.loot == null) continue;

                    int looted = attack.getLooted();
                    int looter = attack.getLooter();

                    NationDB db = Locutus.imp().getNationDB();
                    DBNation victor = db.getNation(looter);
                    DBNation loser = db.getNation(looted);

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
        if (cuttoffMs <= 0) return getAttacks(nation_id);
        ArrayList<DBAttack> list = new ArrayList<>();
        try (PreparedStatement stmt= prepareQuery("select * FROM `attacks2` WHERE date > ? AND (attacker_nation_id = ? OR defender_nation_id = ?)")) {
            stmt.setLong(1, cuttoffMs);
            stmt.setInt(2, nation_id);
            stmt.setInt(3, nation_id);
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
        Set<Integer> warIds = new HashSet<>();
        for (DBWar war : wars) warIds.add(war.warId);
        return getAttacksByWarIds(warIds, cuttoffMs, Long.MAX_VALUE);
    }

    public List<DBAttack> getAttacks(Set<Integer> nationIds, long cuttoffMs) {
        ArrayList<DBAttack> list = new ArrayList<>();
        String ids = StringMan.getString(nationIds).replaceAll(" ", "");
        try (PreparedStatement stmt= prepareQuery("select * FROM `attacks2` WHERE date > ? AND (attacker_nation_id in " + ids + " AND defender_nation_id in " + ids + ")")) {
            stmt.setLong(1, cuttoffMs);
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
        ArrayList<DBAttack> list = new ArrayList<>();
        String ids = StringMan.getString(nationIds).replaceAll(" ", "");
        try (PreparedStatement stmt= prepareQuery("select * FROM `attacks2` WHERE date > ? AND (attacker_nation_id in " + ids + " OR defender_nation_id in " + ids + ")")) {
            stmt.setLong(1, cuttoffMs);
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
        String query = "SELECT COUNT(*) FROM `wars` WHERE date > ? AND (attacker_id = ? OR defender_id = ?)";
        int[] result = new int[1];
        query(query, (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setLong(1, date);
            stmt.setInt(2, nation_id);
            stmt.setInt(3, nation_id);
        }, (ThrowingConsumer<ResultSet>) elem -> result[0] = elem.getInt(1));
        return result[0];
    }

    public int countOffWarsByNation(int nation_id, long date) {
        String query = "SELECT COUNT(*) FROM `wars` WHERE attacker_id = ? AND date > ?";
        int[] result = new int[1];
        query(query, (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, nation_id);
            stmt.setLong(2, date);
        }, (ThrowingConsumer<ResultSet>) elem -> result[0] = elem.getInt(1));
        return result[0];
    }

    public int countDefWarsByNation(int nation_id, long date) {
        String query = "SELECT COUNT(*) FROM `wars` WHERE defender_id = ? AND date > ?";
        int[] result = new int[1];
        query(query, (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, nation_id);
            stmt.setLong(2, date);
        }, (ThrowingConsumer<ResultSet>) elem -> result[0] = elem.getInt(1));
        return result[0];
    }

    public int countWarsByAlliance(int alliance_id, long date) {
        String query = "SELECT COUNT(*) FROM `wars` WHERE date > ? AND (attacker_aa = ? OR defender_a = ?)";
        int[] result = new int[1];
        query(query, (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setLong(1, date);
            stmt.setInt(2, alliance_id);
            stmt.setInt(3, alliance_id);
        }, (ThrowingConsumer<ResultSet>) elem -> result[0] = elem.getInt(1));
        return result[0];
    }
}
