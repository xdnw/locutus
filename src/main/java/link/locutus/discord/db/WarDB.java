package link.locutus.discord.db;

import com.google.common.collect.Lists;
import com.politicsandwar.graphql.model.*;
import com.ptsmods.mysqlw.table.ColumnType;
import com.ptsmods.mysqlw.table.TablePreset;
import it.unimi.dsi.fastutil.ints.*;
import it.unimi.dsi.fastutil.io.FastByteArrayOutputStream;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.Logg;
import link.locutus.discord.apiv1.domains.subdomains.WarAttacksContainer;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AbstractCursor;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AttackCursorFactory;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.IAttack;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.cursors.ALootCursor;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.cursors.VictoryCursor;
import link.locutus.discord.apiv1.enums.*;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.apiv3.csv.DataDumpParser;
import link.locutus.discord.apiv3.PoliticsAndWarV3;
import link.locutus.discord.apiv3.enums.NationLootType;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.conflict.ConflictManager;
import link.locutus.discord.db.entities.*;
import link.locutus.discord.db.entities.Treaty;
import link.locutus.discord.db.handlers.ActiveWarHandler;
import link.locutus.discord.db.handlers.AttackQuery;
import link.locutus.discord.event.Event;
import link.locutus.discord.event.nation.NationChangeColorEvent;
import link.locutus.discord.event.nation.NationChangeDefEvent;
import link.locutus.discord.event.war.AttackEvent;
import link.locutus.discord.event.bounty.BountyCreateEvent;
import link.locutus.discord.event.bounty.BountyRemoveEvent;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.io.PagePriority;
import link.locutus.discord.util.scheduler.ThrowingBiConsumer;
import link.locutus.discord.util.scheduler.ThrowingConsumer;
import link.locutus.discord.util.AlertUtil;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.math.ArrayUtil;
import link.locutus.discord.util.scheduler.ThrowingFunction;
import link.locutus.discord.util.update.WarUpdateProcessor;
import link.locutus.discord.apiv1.domains.subdomains.attack.DBAttack;
import link.locutus.discord.apiv1.domains.subdomains.SWarContainer;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.ParseException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class WarDB extends DBMainV2 {


    private final  ActiveWarHandler activeWars = new ActiveWarHandler(this);
    private final ObjectOpenHashSet<DBWar> warsById = new ObjectOpenHashSet<>();
    private final Int2ObjectOpenHashMap<Object> warsByAllianceId = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectOpenHashMap<Object> warsByNationId = new Int2ObjectOpenHashMap<>();
    private final Object warsByNationLock = new Object();
    private final Int2ObjectOpenHashMap<List<byte[]>> attacksByWarId2 = new Int2ObjectOpenHashMap<>();
    private ConflictManager conflictManager;
    public WarDB() throws SQLException {
        this("war");
    }

    public WarDB(String name) throws SQLException {
        super(Settings.INSTANCE.DATABASE, name);
    }

    public List<DBAttack> getLegacyVictory() {
        List<DBAttack> attacks = new ArrayList<>();
        try (PreparedStatement stmt= getConnection().prepareStatement("select * FROM `attacks2` WHERE `attack_type` = ? ORDER BY `war_attack_id` ASC", ResultSet.TYPE_FORWARD_ONLY,ResultSet.CONCUR_READ_ONLY)) {
            stmt.setFetchSize(2 << 16);
            stmt.setInt(1, AttackType.VICTORY.ordinal());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    DBAttack legacy = createAttack(rs);
                    attacks.add(legacy);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return attacks;
    }

    public void testAttackSerializingTime() throws IOException {
        Map<AttackType, Integer> countByType = new EnumMap<>(AttackType.class);
        int num_attacks = 0;
        int numErrors = 0;

        AttackCursorFactory cursorManager = new AttackCursorFactory(this);

        FastByteArrayOutputStream baos = new FastByteArrayOutputStream();
        long totalBytes = 0;

        int max_attacks = 12695101;
        int skipped = 0;
        int notSkipped = 0;

        long start = System.currentTimeMillis();
        try (PreparedStatement stmt= getConnection().prepareStatement("select * FROM `attacks2` ORDER BY `war_attack_id` ASC", ResultSet.TYPE_FORWARD_ONLY,ResultSet.CONCUR_READ_ONLY)) {
            stmt.setFetchSize(2 << 16);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    num_attacks++;
                    DBAttack legacy = createAttack(rs);
                    AbstractCursor cursor = cursorManager.load(legacy, true);
                    DBWar war = getWar(legacy.getWar_id());

                    if ((num_attacks) % 100000 == 0) {
                        // and print %
                        Logg.text("Loaded " + num_attacks + " attacks | " + skipped + " skipped | " + MathMan.format((num_attacks) / (double) max_attacks * 100) + "%");
                    }
                    // serialize
                    byte[] bytes = cursorManager.toBytes(cursor);
                    // add byte length to count
                    countByType.compute(legacy.getAttack_type(), (k, v) -> v == null ? bytes.length : v + bytes.length);
                    totalBytes += bytes.length;
                    if (bytes.length < 4) {
                        throw new RuntimeException("bytes.length < 4 " + bytes.length + " | " + legacy.getAttack_type());
                    }
//                    // write
//                    baos.write(bytes);
//
//                    // deserialize
                    if (war == null) {
                        skipped++;
                        continue;
                    }
                    AbstractCursor cursor2 = cursorManager.load(war, bytes, true);
                    notSkipped++;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        long diff = System.currentTimeMillis() - start;
        // print time, num and skipped
        Logg.text("Took " + diff + "ms to load " + num_attacks + " attacks (" + skipped + " skipped). Total bytes: " + totalBytes);
        // print total by type
        for (Map.Entry<AttackType, Integer> entry : countByType.entrySet()) {
            Logg.text(entry.getKey() + ": " + entry.getValue());
        }

    }

    private void validateAttack(DBWar war, DBAttack legacy, AbstractCursor cursor) {
        // remove this later
        if (legacy.getLootPercent() > 1) {
            throw new IllegalArgumentException("Loot percent > 1 " + legacy.getLootPercent());
        }

        // check all values match
        // which are:
        //    private int war_attack_id;
        if (legacy.getWar_attack_id() != cursor.getWar_attack_id()) {
            throw new IllegalArgumentException("War attack id mismatch");
        }
        //    private long date;
        if (legacy.getDate() != cursor.getDate() && legacy.getDate() != 0) {
            throw new IllegalArgumentException("Date mismatch " + legacy.getDate() + " | " + (legacy.getDate() < TimeUtil.getOrigin()));
        }
        //    private int war_id;
        if (legacy.getWar_id() != cursor.getWar_id()) {
            throw new IllegalArgumentException("War id mismatch");
        }
        //    private int attacker_nation_id;
        if (legacy.getAttacker_id() != cursor.getAttacker_id() && (war == null || war.getAttacker_id() != cursor.getAttacker_id())) {
            // print att/def of legacy and cursor
            Logg.text("Legacy att/def: " + legacy.getAttacker_id() + " | " + legacy.getDefender_id());
            Logg.text("Cursor att/def: " + cursor.getAttacker_id() + " | " + cursor.getDefender_id());
            throw new IllegalArgumentException("Attacker nation id mismatch " + cursor.getAttacker_id() + " != " + legacy.getAttacker_id());
        }
        //    private int defender_nation_id;
        if (legacy.getDefender_id() != cursor.getDefender_id() && (war == null || war.getDefender_id() != cursor.getDefender_id())) {
            throw new IllegalArgumentException("Defender nation id mismatch");
        }
        //    private AttackType attack_type;
        if (legacy.getAttack_type() != cursor.getAttack_type()) {
            throw new IllegalArgumentException("Attack type mismatch");
        }
        //    private int victor;
//                    int newVictor = cursor.getVictor();
//                    if (legacy.getVictor() != newVictor) {
//                        // print type
//                        // print attacker / defender
//                        // print success
//                        Logg.text("Victor mismatch " + legacy.getVictor() + " | " + newVictor);
//                        Logg.text("Type: " + cursor.getAttack_type());
//                        Logg.text("Attacker: " + legacy.getAttacker_id() + " | " + cursor.getAttacker_id());
//                        Logg.text("Defender: " + legacy.getDefender_id() + " | " + cursor.getDefender_id());
//                        Logg.text("Success: " + SuccessType.values[legacy.getSuccess()] + " | " + cursor.getSuccess());
//                        throw new IllegalArgumentException("Victor mismatch " + legacy.getVictor() + " | " + newVictor);
//                    }
        //    private int success;
        if (legacy.getSuccess() != cursor.getSuccess().ordinal() && legacy.getAttack_type() != AttackType.PEACE && legacy.getAttack_type() != AttackType.FORTIFY && legacy.getAttack_type() != AttackType.VICTORY) {
            // print type and success
            Logg.text("Success mismatch " + legacy.getSuccess() + " | " + cursor.getSuccess().ordinal());
            Logg.text("Type: " + cursor.getAttack_type());
            throw new IllegalArgumentException("Success mismatch");
        }
        //    private int attcas1;
        if (legacy.getAttcas1() != cursor.getAttcas1()) {
            if ((legacy.getAttack_type() != AttackType.MISSILE && legacy.getAttack_type() != AttackType.NUKE) || legacy.getAttcas1() != 0) {
                Logg.text("Attcas1 mismatch " + legacy.getAttcas1() + " | " + cursor.getAttcas1());
                Logg.text("Type: " + cursor.getAttack_type());
                throw new IllegalArgumentException("Attcas1 mismatch");
            }
        }
        //    private int attcas2;
        if (legacy.getAttcas2() != cursor.getAttcas2()) {
            throw new IllegalArgumentException("Attcas2 mismatch");
        }
        //    private int defcas1;
        if (legacy.getDefcas1() != cursor.getDefcas1()) {
            throw new IllegalArgumentException("Defcas1 mismatch");
        }
        //    private int defcas2;
        if (legacy.getDefcas2() != cursor.getDefcas2()) {
            throw new IllegalArgumentException("Defcas2 mismatch");
        }
        //    private int defcas3;
        if (legacy.getDefcas3() != cursor.getDefcas3()) {
            throw new IllegalArgumentException("Defcas3 mismatch");
        }
        //    private double infra_destroyed;
//        if (Math.round(legacy.getInfra_destroyed() * 100) != Math.round(cursor.getInfra_destroyed() * 100)) {
//            // print type and amounts
//            Logg.text("Infra destroyed mismatch " + legacy.getAttack_type() + " " + legacy.getInfra_destroyed() + " | " + cursor.getInfra_destroyed());
//            throw new IllegalArgumentException("Infra destroyed mismatch");
//        }
        //    private int improvements_destroyed;
        if (legacy.getImprovements_destroyed() != cursor.getImprovements_destroyed()) {
            throw new IllegalArgumentException("Improvements destroyed mismatch");
        }
        //    private double money_looted;
        if (Math.round(legacy.getMoney_looted() * 100) != Math.round(cursor.getMoney_looted() * 100)) {
            if (legacy.getAttack_type() == AttackType.VICTORY && cursor.getLoot() != null && (Math.round(cursor.getLoot()[ResourceType.MONEY.ordinal()] * 100) == Math.round(100 * legacy.getMoney_looted()))) {
                if (legacy.getMoney_looted() > 0 && legacy.loot != null && legacy.getMoney_looted() != legacy.loot[ResourceType.MONEY.ordinal()]) {
                    throw new IllegalArgumentException("Money looted mismatch 2");
                }
            } else if (legacy.getMoney_looted() ==0 || cursor.getLoot() == null || cursor.getLoot()[ResourceType.MONEY.ordinal()] == 0) {
                // print type and amounts
                Logg.text("Money looted mismatch " + legacy.getAttack_type() + " " + legacy.getMoney_looted() + " | " + cursor.getMoney_looted());
                Logg.text("Loot : " + (legacy.loot != null) + " | " + (cursor.getLoot() != null));
                if (legacy.loot != null) {
                    Logg.text("loot l " + ResourceType.toString(legacy.loot));
                }
                if (cursor.getLoot() != null) {
                    Logg.text("loot c " + ResourceType.toString(cursor.getLoot()));
                }
                throw new IllegalArgumentException("Money looted mismatch");
            }
        }
        //    public double[] loot;
        if (legacy.loot != null && !ResourceType.isZero(legacy.loot)) {
            if (cursor.getLoot() == null || !Arrays.equals(legacy.loot, cursor.getLoot())) {
                throw new IllegalArgumentException("Loot mismatch");
            }
        }
        //    private int looted;
        if (legacy.getLooted() != null && legacy.getLooted() > 0 && legacy.getAttack_type() == AttackType.A_LOOT && legacy.getLooted() != cursor.getAllianceIdLooted()) {
            throw new IllegalArgumentException("Looted mismatch");
        }
        //    private double lootPercent;
        if (Math.round(legacy.getLootPercent() * 100) != Math.round(cursor.getLootPercent() * 100) && legacy.loot != null && !ResourceType.isZero(legacy.loot)) {
            // print type, percent and loot amounts
            Logg.text("Tyoe " + legacy.getAttack_type() + " | " + cursor.getAttack_type());
            Logg.text("Loot percent mismatch " + legacy.getAttack_type() + " " + legacy.getLootPercent() + " | " + cursor.getLootPercent());
            if (legacy.loot != null) {
                // print
                Logg.text("loot l " + ResourceType.toString(legacy.loot));
            }
            if (cursor.getLoot() != null) {
                // print
                Logg.text("loot c " + ResourceType.toString(cursor.getLoot()));
            }
            // print count by type (or 0)
            throw new IllegalArgumentException("Loot percent mismatch");
        }
        //    private double city_infra_before;
        if (Math.round(legacy.getCity_infra_before() * 100) != Math.round(cursor.getCity_infra_before() * 100) && legacy.getCity_infra_before() > 0 && legacy.getAttack_type() != AttackType.VICTORY) {
            //print
            Logg.text("City infra before mismatch " + legacy.getCity_infra_before() + " | " + cursor.getCity_infra_before());
            Logg.text("Type " + legacy.getAttack_type());
            throw new IllegalArgumentException("City infra before mismatch");
        }
        //    private double infra_destroyed_value;
//                    {
//                        double valueLegacy = legacy.getInfra_destroyed_value();
//                        double valueCursor = cursor.getInfra_destroyed_value();
//                        // if within 10% of each other, ignore
//                        if (Math.abs(valueLegacy - valueCursor) > 0.1 * Math.max(valueLegacy, valueCursor) && legacy.getCity_infra_before() > 0) {
//                            // print type and amounts
//                            Logg.text("final: " + legacy.getCity_infra_before() + " - " + cursor.getCity_infra_before());
//                            // after
//                            Logg.text("before: " + (legacy.getCity_infra_before() - legacy.getInfra_destroyed()) + " - " + (cursor.getCity_infra_before() - cursor.getInfra_destroyed()));
//                            Logg.text("Type " + legacy.getAttack_type());
////                            throw new IllegalArgumentException("Infra destroyed value mismatch " + valueLegacy + " | " + valueCursor);
//                        }
//                    }
        //    private double att_gas_used;
        if (Math.round(legacy.getAtt_gas_used() * 100) != Math.round(cursor.getAtt_gas_used() * 100)) {
            throw new IllegalArgumentException("Att gas used mismatch");
        }
        //    private double att_mun_used;
        if (Math.round(legacy.getAtt_mun_used() * 100) != Math.round(cursor.getAtt_mun_used() * 100)) {
            throw new IllegalArgumentException("Att mun used mismatch");
        }
        //    private double def_gas_used;
        if (Math.round(legacy.getDef_gas_used() * 100) != Math.round(cursor.getDef_gas_used() * 100)) {
            Logg.text("Def gas used mismatch " + legacy.getDef_gas_used() + " | " + cursor.getDef_gas_used());
            Logg.text("Type " + legacy.getAttack_type());
            throw new IllegalArgumentException("Def gas used mismatch");
        }
        //    private double def_mun_used;
        if (Math.round(legacy.getDef_mun_used() * 100) != Math.round(cursor.getDef_mun_used() * 100)) {
            throw new IllegalArgumentException("Def mun used mismatch");
        }
        //    public double infraPercent_cached;
        if (legacy.infraPercent_cached > 0 && Math.round(legacy.infraPercent_cached * 100) != Math.round(cursor.getInfra_destroyed_percent() * 100)) {
            throw new IllegalArgumentException("Infra percent cached mismatch");
        }
        //    public int city_cached;
        if (legacy.city_cached > 0 && legacy.city_cached != cursor.getCity_id()) {
            throw new IllegalArgumentException("City cached mismatch");
        }
    }

    public void importLegacyAttacks() {
        try {
            // if attacks2 does not exist, return
            if (!tableExists("attacks2")) {
                return;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        // load attacks
        // convert to attack cursor
        AttackCursorFactory cursorManager = new AttackCursorFactory(this);
        List<AbstractCursor> attacks = new ArrayList<>();
        try (PreparedStatement stmt= getConnection().prepareStatement("select * FROM `attacks2` ORDER BY `war_attack_id` ASC", ResultSet.TYPE_FORWARD_ONLY,ResultSet.CONCUR_READ_ONLY)) {
            stmt.setFetchSize(2 << 16);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    DBAttack legacy = createAttack(rs);
                    AbstractCursor cursor = cursorManager.load(legacy, true);
                    attacks.add(cursor);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        String query = "INSERT OR IGNORE INTO `ATTACKS3` (`id`, `war_id`, `attacker_nation_id`, `defender_nation_id`, `date`, `data`) VALUES (?, ?, ?, ?, ?, ?)";
        executeBatch(attacks, query, new ThrowingBiConsumer<AbstractCursor, PreparedStatement>() {
            @Override
            public void acceptThrows(AbstractCursor attack, PreparedStatement stmt) throws SQLException {
                stmt.setInt(1, attack.getWar_attack_id());
                stmt.setInt(2, attack.getWar_id());
                stmt.setInt(3, attack.getAttacker_id());
                stmt.setInt(4, attack.getDefender_id());
                stmt.setLong(5, attack.getDate());
                byte[] data = cursorManager.toBytes(attack);
                stmt.setBytes(6, data);
            }
        });

        // if table sizes match, drop attacks2
        int countRows = 0;
        try (PreparedStatement stmt = getConnection().prepareStatement("SELECT COUNT(*) FROM `attacks3`")) {
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    countRows = rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        if (countRows >= attacks.size() && countRows > 0) {
            executeStmt("DROP TABLE `attacks2`");
        }
    }

    private void setWar(DBWar war) {
        synchronized (warsByAllianceId) {
            if (war.getAttacker_aa() != 0)
                ArrayUtil.addElement(DBWar.class, warsByAllianceId, war.getAttacker_aa(), war);
            if (war.getDefender_aa() != 0)
                ArrayUtil.addElement(DBWar.class, warsByAllianceId, war.getDefender_aa(), war);
        }
        synchronized (warsByNationLock) {
            ArrayUtil.addElement(DBWar.class, warsByNationId, war.getAttacker_id(), war);
            ArrayUtil.addElement(DBWar.class, warsByNationId, war.getDefender_id(), war);
        }
        synchronized (warsById) {
            warsById.add(war);
        }
    }

    private void setWars(List<DBWar> allWars, boolean clear, boolean sync) {
        if (clear) {
            synchronized (warsById) {
                warsById.clear();
            }
            synchronized (warsByAllianceId) {
                warsByAllianceId.clear();
            }
            synchronized (warsByNationLock) {
                warsByNationId.clear();
            }
        }
        Int2IntOpenHashMap numWarsByAlliance = new Int2IntOpenHashMap();
        Int2IntOpenHashMap numWarsByNation = new Int2IntOpenHashMap();
        for (DBWar war : allWars) {
            if (war.getAttacker_aa() != 0) numWarsByAlliance.addTo(war.getAttacker_aa(), 1);
            if (war.getDefender_aa() != 0) numWarsByAlliance.addTo(war.getDefender_aa(), 1);
            numWarsByNation.addTo(war.getAttacker_id(), 1);
            numWarsByNation.addTo(war.getDefender_id(), 1);
        }
        synchronized (warsById) {
            warsById.addAll(allWars);
        }
        if (sync) {
            synchronized (warsByNationLock) {
                for (DBWar war : allWars) {
                    setWar(war, war.getAttacker_id(), numWarsByNation.get(war.getAttacker_id()), this.warsByNationId);
                    setWar(war, war.getDefender_id(), numWarsByNation.get(war.getDefender_id()), this.warsByNationId);
                }
            }
            synchronized (warsByAllianceId) {
                for (DBWar war : allWars) {
                    if (war.getAttacker_aa() != 0) setWar(war, war.getAttacker_aa(), numWarsByAlliance.get(war.getAttacker_aa()), this.warsByAllianceId);
                    if (war.getDefender_aa() != 0) setWar(war, war.getDefender_aa(), numWarsByAlliance.get(war.getDefender_aa()), this.warsByAllianceId);
                }
            }
        } else {
            for (DBWar war : allWars) {
                if (war.getAttacker_aa() != 0) setWar(war, war.getAttacker_aa(), numWarsByAlliance.get(war.getAttacker_aa()), this.warsByAllianceId);
                if (war.getDefender_aa() != 0) setWar(war, war.getDefender_aa(), numWarsByAlliance.get(war.getDefender_aa()), this.warsByAllianceId);
                setWar(war, war.getAttacker_id(), numWarsByNation.get(war.getAttacker_id()), this.warsByNationId);
                setWar(war, war.getDefender_id(), numWarsByNation.get(war.getDefender_id()), this.warsByNationId);
            }
        }
    }

    private void setWar(DBWar war, int id, int size, Int2ObjectOpenHashMap<Object> map) {
        if (size == 1) {
            map.put(id, war);
        } else {
            Object o = map.get(id);
            if (o instanceof ObjectOpenHashSet set) {
                set.add(war);
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

    public void loadWarCityCountsLegacy() throws IOException, ParseException {
        DataDumpParser parser = Locutus.imp().getDataDumper(true).load();
        Map<Long, Map<Integer, Byte>> counts = parser.getUtil().backCalculateCityCounts();
        Set<DBWar> toSave = new ObjectOpenHashSet<>();
        AtomicLong failed = new AtomicLong();
        synchronized (warsById) {
            warsById.forEach(war -> {
                if (war.getAttCities() != 0 && war.getDefCities() != 0) return;
                long day = TimeUtil.getDay(war.getDate());
                Map<Integer, Byte> map = counts.get(day);
                if (map != null) {
                    boolean modified = false;
                    if (war.getAttCities() == 0) {
                        Byte attCities = map.get(war.getAttacker_id());
                        if (attCities != null) {
                            war.setAttCities(attCities & 0xff);
                            modified = true;
                        }
                    }
                    if (war.getDefCities() == 0) {
                        Byte defCities = map.get(war.getDefender_id());
                        if (defCities != null) {
                            war.setDefCities(defCities & 0xff);
                            modified = true;
                        }
                    }
                    if (modified) {
                        toSave.add(war);
                    }
                } else {
                    failed.incrementAndGet();
                }
            });
        }
        Logg.text("Saving " + toSave.size() + " wars");
        Logg.text("Failed to find " + failed.get() + " wars");
        saveWars(toSave, false);
    }

    public WarDB load() {
        loadWars(Settings.INSTANCE.TASKS.UNLOAD_WARS_AFTER_TURNS);
        if (Settings.INSTANCE.TASKS.LOAD_ACTIVE_ATTACKS) {
            importLegacyAttacks();
            loadAttacks(Settings.INSTANCE.TASKS.LOAD_INACTIVE_ATTACKS, Settings.INSTANCE.TASKS.LOAD_ACTIVE_ATTACKS);

            if (Settings.INSTANCE.ENABLED_COMPONENTS.REPEATING_TASKS) {
                if ((warsByAllianceId.isEmpty() || activeWars.isEmpty()) && Settings.INSTANCE.TASKS.ALL_WAR_SECONDS > 0) {
                    updateAllWars(null);
                }

                if (attacksByWarId2.isEmpty() && Settings.INSTANCE.TASKS.ALL_WAR_SECONDS > 0) {
                    Logg.text("Fetching all attacks");
                    AttackCursorFactory factory = new AttackCursorFactory(this);
                    List<WarAttack> attacks = Locutus.imp().getV3().readSnapshot(PagePriority.ACTIVE_PAGE, WarAttack.class);
                    List<AbstractCursor> attackList = new ObjectArrayList<>(attacks.size());
                    for (WarAttack v3Attack : attacks) {
                        attackList.add(factory.load(v3Attack, true));
                    }
                    saveAttacks(attackList, null);
                }

                if (Settings.INSTANCE.TASKS.BOUNTY_UPDATE_SECONDS > 0 && !hasAnyBounties()) {
                    updateBountiesV3();
                }
            }
        }

        activeWars.syncBlockades();
        if (conflictManager != null) {
            conflictManager.loadConflicts();
        }

        return this;
    }

    public Set<Integer> getNationsBlockadedBy(int nationId) {
        return activeWars.getNationsBlockadedBy(nationId);
    }

    public Set<Integer> getNationsBlockading(int nationId) {
        return activeWars.getNationsBlockading(nationId);
    }

    public void loadWars(int turns) {
        if (turns > 0 && turns < 120) turns = 120;
        long currentTurn = TimeUtil.getTurn();
        long date = TimeUtil.getTimeFromTurn(currentTurn - turns);
        long activeWarCutoff = TimeUtil.getTimeFromTurn(currentTurn - 60);
        String whereClause = turns > 0 ? " WHERE date > " + date : "";

        AtomicInteger setStatusCount = new AtomicInteger();

        List<DBWar> wars = new ObjectArrayList<>();
        List<DBWar> saveWars = new ObjectArrayList<>();
        query("SELECT id, attacker_id, defender_id, attacker_aa, defender_aa, war_type, status, date, attCities, defCities, research FROM wars " + whereClause, f -> {
        }, (ThrowingConsumer<ResultSet>) rs -> {
            while (rs.next()) {
                DBWar war = create(rs);
                wars.add(war);
//                setWar(war);
                if (war.getDate() > activeWarCutoff) {
                    activeWars.addActiveWar(war);
                } else if (war.isActive()) {
                    war.setStatus(WarStatus.EXPIRED);
                    saveWars.add(war);
                    setStatusCount.incrementAndGet();
                }
            }
        });
        if (!wars.isEmpty()) {
            setWars(wars, false, false);
        }
        if (!saveWars.isEmpty()) {
            saveWars(saveWars, false);
        }
    }

    public List<AbstractCursor> getAttacks(Collection<DBWar> wars, Predicate<AttackType> attackTypeFilter,  Predicate<AbstractCursor> preliminaryFilter, Predicate<AbstractCursor> attackFilter) {
        List<AbstractCursor> result = new ObjectArrayList<>();
        final BiFunction<DBWar, byte[], AbstractCursor> loader = createLoader(attackTypeFilter, preliminaryFilter);
        final Consumer<AbstractCursor> attackAdder = attackFilter == null ? result::add : cursor -> {
            if (attackFilter.test(cursor)) {
                result.add(cursor);
            }
        };
        iterateAttacks(wars, loader, attackAdder);
        return result;
    }

    public BiFunction<DBWar, byte[], AbstractCursor> createLoader(Predicate<AttackType> attackTypeFilter,  Predicate<AbstractCursor> preliminaryFilter) {
        if (attackTypeFilter != null) {
            if (preliminaryFilter != null) {
                return (war, data) -> attackCursorFactory.loadWithTypePretest(war, data, true, attackTypeFilter, preliminaryFilter);
            } else {
                return (war, data) -> attackCursorFactory.loadWithType(war, data, true, attackTypeFilter);
            }
        } else if (preliminaryFilter != null) {
            return (war, data) -> attackCursorFactory.loadWithPretest(war, data, true, preliminaryFilter);
        } else {
            return (war, data) -> attackCursorFactory.load(war, data, true);
        }
    }

    public void iterateAttacks(Iterable<DBWar> wars, Predicate<AttackType> attackTypeFilter,  Predicate<AbstractCursor> preliminaryFilter, Consumer<AbstractCursor> forEachAttack) {
        BiFunction<DBWar, byte[], AbstractCursor> loader = createLoader(attackTypeFilter, preliminaryFilter);
        iterateAttacks(wars, loader, forEachAttack);
    }

    public void iterateWarAttacks(Iterable<DBWar> wars, Predicate<AttackType> attackTypeFilter,  Predicate<AbstractCursor> preliminaryFilter, BiConsumer<DBWar, AbstractCursor> forEachAttack) {
        BiFunction<DBWar, byte[], AbstractCursor> loader = createLoader(attackTypeFilter, preliminaryFilter);
        iterateWarAttacks(wars, loader, forEachAttack);
    }

    public void iterateAttacks(Iterable<DBWar> wars, BiFunction<DBWar, byte[], AbstractCursor> loader, Consumer<AbstractCursor> forEachAttack) {
        if (forEachAttack != null) {
            BiFunction<DBWar, byte[], AbstractCursor> parent = loader;
            loader = (war, data) -> {
                AbstractCursor cursor = parent.apply(war, data);
                if (cursor != null) {
                    forEachAttack.accept(cursor);
                }
                return cursor;
            };
        }
        iterateAttacks(wars, loader);
    }

    public void iterateWarAttacks(Iterable<DBWar> wars, BiFunction<DBWar, byte[], AbstractCursor> loader, BiConsumer<DBWar, AbstractCursor> forEachAttack) {
        if (forEachAttack != null) {
            BiFunction<DBWar, byte[], AbstractCursor> parent = loader;
            loader = (war, data) -> {
                AbstractCursor cursor = parent.apply(war, data);
                if (cursor != null) {
                    forEachAttack.accept(war, cursor);
                }
                return cursor;
            };
        }
        iterateAttacks(wars, loader);
    }

    public void iterateAttacks(Iterable<DBWar> wars, BiFunction<DBWar, byte[], AbstractCursor> loader) {
        List<Integer> warIdsFetch = null;

        boolean fetchFromDB = !Settings.INSTANCE.TASKS.LOAD_INACTIVE_ATTACKS;

        synchronized (attacksByWarId2) {
            long cutoffDate = TimeUtil.getTimeFromTurn(TimeUtil.getTurn() - 120);
            for (DBWar war : wars) {
                int warId = war.getWarId();
                List<byte[]> attacks = attacksByWarId2.get(warId);

                if (attacks == null) {
                    if (fetchFromDB && war.getDate() < cutoffDate) {
                        if (warIdsFetch == null) warIdsFetch = new ObjectArrayList<>();
                        warIdsFetch.add(warId);
                    }
                    continue;
                }
                if (attacks.isEmpty()) continue;
                for (byte[] data : attacks) {
                    AbstractCursor cursor = loader.apply(war, data);
                }
            }
        }

        if (warIdsFetch != null) {
            String whereClause;
            if (warIdsFetch.size() == 1) {
                whereClause = " WHERE `war_id` = " + warIdsFetch.get(0);
            } else if (warIdsFetch.size() > 100000) {
                Set<Integer> warIdsSet = new ObjectOpenHashSet<>(warIdsFetch);
                String query = "SELECT * FROM `attacks3` ORDER BY `id` ASC";
                try (PreparedStatement stmt = prepareQuery(query)) {
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            int warId = rs.getInt("war_id");
                            if (!warIdsSet.contains(warId)) continue;
                            DBWar war = getWar(warId);
                            if (war == null) {
                                continue;
                            }
                            byte[] data = applyAdminFix(warId, rs.getBytes("data"));
                            AbstractCursor cursor = loader.apply(war, data);
                        }
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                }
                return;
            } else {
                Collections.sort(warIdsFetch);
                whereClause = " WHERE `war_id` IN " + StringMan.getString(warIdsFetch);
            }
            String query = "SELECT * FROM `attacks3` " + whereClause + " ORDER BY `id` ASC";
            try (PreparedStatement stmt = prepareQuery(query)) {
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        int warId = rs.getInt("war_id");
                        DBWar war = getWar(warId);
                        if (war == null) {
                            continue;
                        }
                        byte[] data = applyAdminFix(warId, rs.getBytes("data"));
                        AbstractCursor cursor = loader.apply(war, data);
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public Set<IAttack> getAttacksById(Set<Integer> ids) {
        if (ids.isEmpty()) return Collections.emptySet();
        List<Integer> idsSorted = new IntArrayList(ids);
        Collections.sort(idsSorted);
        Set<IAttack> attacks = new ObjectOpenHashSet<>();
        String query = "SELECT * FROM `attacks3` WHERE `id` ";
        if (ids.size() == 1) {
            query += " = " + idsSorted.get(0);
        } else {
            query += " IN " + StringMan.getString(idsSorted);
        }
        try (PreparedStatement stmt = prepareQuery(query)) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int warId = rs.getInt("war_id");
                    DBWar war = getWar(warId);
                    if (war == null) {
                        continue;
                    }
                    byte[] data = applyAdminFix(warId, rs.getBytes("data"));
                    attacks.add(attackCursorFactory.load(war, data, true));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return attacks;
    }

    public Map<DBWar, List<AbstractCursor>> getAttacksByWar(Collection<DBWar> wars, Predicate<AttackType> attackTypeFilter,  Predicate<AbstractCursor> preliminaryFilter, Predicate<AbstractCursor> attackFilter) {
        Map<DBWar, List<AbstractCursor>> result = new Object2ObjectOpenHashMap<>();
        final BiFunction<DBWar, byte[], AbstractCursor> loader;
        if (attackTypeFilter != null) {
            if (preliminaryFilter != null) {
                loader = (war, data) -> attackCursorFactory.loadWithTypePretest(war, data, true, attackTypeFilter, preliminaryFilter);
            } else {
                loader = (war, data) -> attackCursorFactory.loadWithType(war, data, true, attackTypeFilter);
            }
        } else if (preliminaryFilter != null) {
            loader = (war, data) -> attackCursorFactory.loadWithPretest(war, data, true, preliminaryFilter);
        } else {
            loader = (war, data) -> attackCursorFactory.load(war, data, true);
        }
        final Consumer<AbstractCursor> attackAdder = attackFilter == null ? cursor -> {
            List<AbstractCursor> list = result.computeIfAbsent(cursor.getWar(this), f -> new ObjectArrayList<>());
            list.add(cursor);
        } : cursor -> {
            if (attackFilter.test(cursor)) {
                List<AbstractCursor> list = result.computeIfAbsent(cursor.getWar(this), f -> new ObjectArrayList<>());
                list.add(cursor);
            }
        };
        iterateAttacks(wars, loader, attackAdder);
        return result;
    }

    public List<AbstractCursor> getAttacksByWarId2(DBWar war, boolean loadInactive) {
        return getAttacksByWarId2(war, attackCursorFactory, loadInactive);
    }

    public List<AbstractCursor> getAttacksByWarId2(DBWar war, AttackCursorFactory factory, boolean loadInactive) {
        List<byte[]> attacks;
        synchronized (attacksByWarId2) {
            attacks = attacksByWarId2.get(war.warId);
            if (loadInactive && attacks == null && !Settings.INSTANCE.TASKS.LOAD_INACTIVE_ATTACKS && !war.isActive()) {
                String query = "SELECT * FROM `attacks3` WHERE `war_id` = " + war.warId;
                try (PreparedStatement stmt = prepareQuery(query)) {
                    try (ResultSet rs = stmt.executeQuery()) {
                        attacks = new ObjectArrayList<>();
                        while (rs.next()) {
                            attacks.add(applyAdminFix(war.warId, rs.getBytes("data")));
                        }
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
        if (attacks == null || attacks.isEmpty()) return Collections.emptyList();
        // use guava transform
        List<AbstractCursor> list = Lists.transform(attacks, input -> factory.load(war, input, true));
        return list;
    }

    public Set<DBWar> getWarsForNationOrAlliance(Set<Integer> nations, Set<Integer> alliances) {
        Set<DBWar> result = new ObjectOpenHashSet<>();
        if (alliances != null && !alliances.isEmpty()) {
            synchronized (warsByAllianceId) {
                for (int id : alliances) {
                    Object wars = warsByAllianceId.get(id);
                    if (wars != null) {
                        ArrayUtil.iterateElements(DBWar.class, wars, result::add);
                    }
                }
            }
        }
        if (nations != null && !nations.isEmpty()) {
            synchronized (warsByNationLock) {
                for (int id : nations) {
                    Object wars = warsByNationId.get(id);
                    if (wars != null) {
                        ArrayUtil.iterateElements(DBWar.class, wars, result::add);
                    }
                }
            }
        }
        return result;
    }

    public Map<Integer, DBWar> getWarsForNationOrAlliance(Predicate<Integer> nations, Predicate<Integer> alliances, Predicate<DBWar> warFilter) {
        Map<Integer, DBWar> result = new Int2ObjectOpenHashMap<>();
        if (alliances != null) {
            synchronized (warsByAllianceId) {
                for (Map.Entry<Integer, Object> entry : warsByAllianceId.entrySet()) {
                    if (alliances.test(entry.getKey())) {
                        if (warFilter != null) {
                            ArrayUtil.iterateElements(DBWar.class, entry.getValue(), war -> {
                                if (warFilter.test(war)) {
                                    result.put(war.warId, war);
                                }
                            });
                        } else {
                            ArrayUtil.iterateElements(DBWar.class, entry.getValue(), war -> {
                                result.put(war.warId, war);
                            });
                        }
                    }
                }
            }
        }
        if (nations != null) {
            synchronized (warsByNationLock) {
                for (Map.Entry<Integer, Object> entry : warsByNationId.entrySet()) {
                    if (nations.test(entry.getKey())) {
                        if (warFilter != null) {
                            ArrayUtil.iterateElements(DBWar.class, entry.getValue(), war -> {
                                if (warFilter.test(war)) {
                                    result.put(war.warId, war);
                                }
                            });
                        } else {
                            ArrayUtil.iterateElements(DBWar.class, entry.getValue(), war -> {
                                result.put(war.warId, war);
                            });
                        }
                    }
                }
            }
        }
        else if (alliances == null) {
            synchronized (warsById) {
                if (warFilter == null) {
                    warsById.forEach((war) -> result.put(war.warId, war));
                } else {
                    for (DBWar war : warsById) {
                        if (warFilter.test(war)) {
                            result.put(war.warId, war);
                        }
                    }
                }
            }
        }
        return result;
    }

    public List<AbstractCursor> getAttacksByWars(Collection<DBWar> wars, long cuttoffMs) {
        return getAttacksByWars(wars, cuttoffMs, Long.MAX_VALUE);
    }

    public List<AbstractCursor> getAttacks(Set<Integer> nationIds, long cuttoffMs) {
        return getAttacks(nationIds, cuttoffMs, Long.MAX_VALUE);
    }

    public List<AbstractCursor> getAttacks(Set<Integer> nationIds, long start, long end) {
        Set<DBWar> allWars = new ObjectOpenHashSet<>();
        long startWithExpire = TimeUtil.getTimeFromTurn(TimeUtil.getTurn(start) - 60);
        synchronized (warsByNationLock) {
            for (int nationId : nationIds) {
                Object natWars = warsByNationId.get(nationId);
                if (natWars != null) {
                    ArrayUtil.iterateElements(DBWar.class, natWars, war -> {
                        if (!nationIds.contains(war.getAttacker_id()) || !nationIds.contains(war.getDefender_id())) return;
                        if (war.getDate() < startWithExpire || war.getDate() > end) return;
                        allWars.add(war);
                    });
                }
            }
        }
        return getAttacksByWars(allWars, start, end);
    }

    public List<AbstractCursor> getAttacksEither(Set<Integer> nationIds, long cuttoffMs) {
        return getAttacksEither(nationIds, cuttoffMs, Long.MAX_VALUE);
    }

    public List<AbstractCursor> getAttacksEither(Set<Integer> nationIds, long start, long end) {
        Set<DBWar> allWars = new LinkedHashSet<>();
        long startWithExpire = TimeUtil.getTimeFromTurn(TimeUtil.getTurn(start) - 60);
        synchronized (warsByNationLock) {
            for (int nationId : nationIds) {
                Object natWars = warsByNationId.get(nationId);
                if (natWars != null) {
                    ArrayUtil.iterateElements(DBWar.class, natWars, war -> {
                        if (war.getDate() < startWithExpire || war.getDate() > end) return;
                        allWars.add(war);
                    });
                }
            }
        }
        return getAttacksByWars(allWars, start, end);
    }

    public List<AbstractCursor> getAttacksAny(Set<Integer> nationIds, long cuttoffMs) {
        return getAttacksAny(nationIds, cuttoffMs, Long.MAX_VALUE);
    }
    public List<AbstractCursor> getAttacksAny(Set<Integer> nationIds, long start, long end) {
        Set<DBWar> allWars = new LinkedHashSet<>();
        long startWithExpire = TimeUtil.getTimeFromTurn(TimeUtil.getTurn(start) - 60);
        synchronized (warsByNationLock) {
            for (int nationId : nationIds) {
                Object natWars = warsByNationId.get(nationId);
                if (natWars != null) {
                    ArrayUtil.iterateElements(DBWar.class, natWars, war -> {
                        if (war.getDate() < startWithExpire || war.getDate() > end) return;
                        allWars.add(war);
                    });
                }
            }
        }
        return getAttacksByWars(allWars, start, end);
    }

    public Map<Integer, DBWar> getWars(Predicate<DBWar> filter) {
        return getWarsForNationOrAlliance(null, null, filter);
    }

    public void loadNukeDates() {
        long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(12);

        List<Integer> attackIds = new ArrayList<>();
        iterateAttacks(cutoff, Long.MAX_VALUE, f -> true, attack -> {
            if (attack.getAttack_type() == AttackType.NUKE && attack.getSuccess() != SuccessType.UTTER_FAILURE) {
                attackIds.add(attack.getWar_attack_id());
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
            String create = "CREATE TABLE IF NOT EXISTS `WARS` (`id` INT NOT NULL PRIMARY KEY, `attacker_id` INT NOT NULL, `defender_id` INT NOT NULL, `attacker_aa` INT NOT NULL, `defender_aa` INT NOT NULL, `war_type` INT NOT NULL, `status` INT NOT NULL, `date` BIGINT NOT NULL, `attCities` INT NOT NULL, `defCities` INT NOT NULL, `research` INT NOT NULL)";
            executeStmt(create);
            executeStmt("ALTER TABLE `WARS` ADD COLUMN `attCities` INT NOT NULL DEFAULT 0", true);
            executeStmt("ALTER TABLE `WARS` ADD COLUMN `defCities` INT NOT NULL DEFAULT 0", true);
            executeStmt("ALTER TABLE `WARS` ADD COLUMN `research` INT NOT NULL DEFAULT 0", true);
        };

        {
            executeStmt("CREATE TABLE IF NOT EXISTS `BLOCKADED` (`blockader`, `blockaded`, PRIMARY KEY(`blockader`, `blockaded`))");
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
//            String nations = "CREATE TABLE IF NOT EXISTS `attacks2` (" +
//                    "`war_attack_id` INT NOT NULL PRIMARY KEY, " +
//                    "`date` BIGINT NOT NULL, " +
//                    "war_id INT NOT NULL, " +
//                    "attacker_nation_id INT NOT NULL, " +
//                    "defender_nation_id INT NOT NULL, " +
//                    "attack_type INT NOT NULL, " +
//                    "victor INT NOT NULL, " +
//                    "success INT NOT NULL," +
//                    "attcas1 INT NOT NULL," +
//                    "attcas2 INT NOT NULL," +
//                    "defcas1 INT NOT NULL," +
//                    "defcas2 INT NOT NULL," +
//                    "defcas3 INT NOT NULL," +
//                    "city_id INT NOT NULL," + // Not used anymore
//                    "infra_destroyed INT," +
//                    "improvements_destroyed INT," +
//                    "money_looted BIGINT," +
//                    "looted INT," +
//                    "loot BLOB," +
//                    "pct_looted INT," +
//                    "city_infra_before INT," +
//                    "infra_destroyed_value INT," +
//                    "att_gas_used INT," +
//                    "att_mun_used INT," +
//                    "def_gas_used INT," +
//                    "def_mun_used INT" +
//                    ")";
//            try (Statement stmt = getConnection().createStatement()) {
//                stmt.addBatch(nations);
//                stmt.executeBatch();
//                stmt.clearBatch();
//            } catch (SQLException e) {
//                e.printStackTrace();
//            }
//            executeStmt("CREATE INDEX IF NOT EXISTS index_attack_warid ON attacks2 (war_id);");
//            executeStmt("CREATE INDEX IF NOT EXISTS index_attack_attacker_nation_id ON attacks2 (attacker_nation_id);");
//            executeStmt("CREATE INDEX IF NOT EXISTS index_attack_defender_nation_id ON attacks2 (defender_nation_id);");
//            executeStmt("CREATE INDEX IF NOT EXISTS index_attack_date ON attacks2 (date);");

            // if not exist,
            // id (int)
            // war_id (int)
            // attacker_nation_id (int)
            // defender_nation_id (int)
            // date (long)
            // data (byte[])
            // create index for war_id, attacker_nation_id, defender_nation_id, date

            String attacksTable = "CREATE TABLE IF NOT EXISTS `ATTACKS3` (" +
                    "`id` INTEGER PRIMARY KEY, " +
                    "`war_id` INT NOT NULL, " +
                    "`attacker_nation_id` INT NOT NULL, " +
                    "`defender_nation_id` INT NOT NULL, " +
                    "`date` BIGINT NOT NULL, " +
                    "`data` BLOB NOT NULL)";
            executeStmt(attacksTable);
            executeStmt("CREATE INDEX IF NOT EXISTS index_attack_warid ON ATTACKS3 (war_id);");
            executeStmt("CREATE INDEX IF NOT EXISTS index_attack_attacker_nation_id ON ATTACKS3 (attacker_nation_id);");
            executeStmt("CREATE INDEX IF NOT EXISTS index_attack_defender_nation_id ON ATTACKS3 (defender_nation_id);");
            executeStmt("CREATE INDEX IF NOT EXISTS index_attack_date ON ATTACKS3 (date);");
        }

        // create custom bounties table
        {
            String create = "CREATE TABLE IF NOT EXISTS `CUSTOM_BOUNTIES` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "`placed_by` INT NOT NULL, " +
                    "`date_created` BIGINT NOT NULL, " +
                    "`claimed_by` BIGINT NOT NULL, " +
                    "`amount` BLOB NOT NULL, " +
                    "`nations` BLOB NOT NULL, " +
                    "`alliances` BLOB NOT NULL, " +
                    "`filter` VARCHAR NOT NULL, " +
                    "`total_damage` BIGINT NOT NULL, " +
                    "`infra_damage` BIGINT NOT NULL, " +
                    "`unit_damage` BIGINT NOT NULL, " +
                    "`only_offensives` INT NOT NULL, " +
                    "`unit_kills` BLOB NOT NULL, " +
                    "`unit_attacks` BLOB NOT NULL, " +
                    "`allowed_war_types` BIGINT NOT NULL, " +
                    "`allowed_war_status` BIGINT NOT NULL, " +
                    "`allowed_attack_types` BIGINT NOT NULL, " +
                    "`allowed_attack_rolls` BIGINT NOT NULL)";
            try (Statement stmt = getConnection().createStatement()) {
                stmt.addBatch(create);
                stmt.executeBatch();
                stmt.clearBatch();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        };
        boolean enableConflicts = !Settings.INSTANCE.WEB.S3.ACCESS_KEY.isEmpty() &&
                !Settings.INSTANCE.WEB.S3.SECRET_ACCESS_KEY.isEmpty() &&
                !Settings.INSTANCE.WEB.S3.REGION.isEmpty() &&
                !Settings.INSTANCE.WEB.S3.BUCKET.isEmpty();
        conflictManager = enableConflicts ? new ConflictManager(this) : null;
        if (conflictManager != null) {
            this.conflictManager.createTables();
        }
    }

    public ConflictManager getConflicts() {
        return conflictManager;
    }

    public ObjectOpenHashSet<DBWar> getActiveWars() {
        return activeWars.getActiveWars();
    }

    public Set<DBWar> getActiveWars(int nationId) {
        return activeWars.getActiveWars(nationId);
    }

    public ObjectOpenHashSet<DBWar> getActiveWars(Predicate<Integer> nationId, Predicate<DBWar> warPredicate) {
        return activeWars.getActiveWars(nationId, warPredicate);
    }

    public void addCustomBounty(CustomBounty bounty) {
        String query = "INSERT OR IGNORE INTO `CUSTOM_BOUNTIES`(" +
                "`placed_by`, " +
                "`date_created`, " +
                "`claimed_by`, " +
                "`amount`, " +
                "`nations`, " +
                "`alliances`, " +
                "`filter`, " +
                "`total_damage`, " +
                "`infra_damage`, " +
                "`unit_damage`, " +
                "`only_offensives`, " +
                "`unit_kills`, " +
                "`unit_attacks`, " +
                "`allowed_war_types`, " +
                "`allowed_war_status`, " +
                "`allowed_attack_types`, " +
                "`allowed_attack_rolls`) " +
                " VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";


        ThrowingBiConsumer<CustomBounty, PreparedStatement> setStmt = (bounty1, stmt) -> {
            stmt.setInt(1, bounty1.placedBy);
            stmt.setLong(2, bounty1.date);
            stmt.setLong(3, bounty1.claimedBy);

            stmt.setBytes(4, ArrayUtil.toByteArray(bounty1.resources));
            stmt.setBytes(5, ArrayUtil.writeIntSet(bounty1.nations));
            stmt.setBytes(6, ArrayUtil.writeIntSet(bounty1.alliances));

            stmt.setString(7, bounty1.filter2.toString());
            stmt.setLong(8, (long) bounty1.totalDamage);
            stmt.setLong(9, (long) bounty1.infraDamage);
            stmt.setLong(10, (long) bounty1.unitDamage);
            stmt.setInt(11, bounty1.onlyOffensives ? 1 : 0);

            stmt.setBytes(12, ArrayUtil.writeEnumMap(bounty1.unitKills));
            stmt.setBytes(13, ArrayUtil.writeEnumMap(bounty1.unitAttacks));
            stmt.setLong(14, ArrayUtil.writeEnumSet(bounty1.allowedWarTypes));
            stmt.setLong(15, ArrayUtil.writeEnumSet(bounty1.allowedWarStatus));
            stmt.setLong(16, ArrayUtil.writeEnumSet(bounty1.allowedAttackTypes));
            stmt.setLong(17, ArrayUtil.writeEnumSet(bounty1.allowedAttackRolls));
        };

        // insert and get generated id
        try (PreparedStatement stmt = getConnection().prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
            setStmt.accept(bounty, stmt);

            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    bounty.id = rs.getInt(1);
                } else {
                    throw new SQLException("Creating offer failed, no ID obtained.");
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void updateBounty(CustomBounty bounty) {
        String query = "UPDATE `CUSTOM_BOUNTIES`(" +
                "SET `placed_by` = ?, " +
                "`date_created` = ?, " +
                "`claimed_by` = ?, " +
                "`amount` = ?, " +
                "`nations` = ?, " +
                "`alliances` = ?, " +
                "`filter` = ?, " +
                "`total_damage` = ?, " +
                "`infra_damage` = ?, " +
                "`unit_damage` = ?, " +
                "`only_offensives` = ?, " +
                "`unit_kills` = ?, " +
                "`unit_attacks` = ?, " +
                "`allowed_war_types` = ?, " +
                "`allowed_war_status` = ?, " +
                "`allowed_attack_types` = ?, " +
                "`allowed_attack_rolls` = ? " +
                "WHERE `id` = ?";
        ThrowingBiConsumer<CustomBounty, PreparedStatement> setStmt = (bounty1, stmt) -> {
            stmt.setInt(1, bounty1.placedBy);
            stmt.setLong(2, bounty1.date);
            stmt.setLong(3, bounty1.claimedBy);

            stmt.setBytes(4, ArrayUtil.toByteArray(bounty1.resources));
            stmt.setBytes(5, ArrayUtil.writeIntSet(bounty1.nations));
            stmt.setBytes(6, ArrayUtil.writeIntSet(bounty1.alliances));

            stmt.setString(7, bounty1.filter2.toString());
            stmt.setLong(8, (long) bounty1.totalDamage);
            stmt.setLong(9, (long) bounty1.infraDamage);
            stmt.setLong(10, (long) bounty1.unitDamage);
            stmt.setInt(11, bounty1.onlyOffensives ? 1 : 0);

            stmt.setBytes(12, ArrayUtil.writeEnumMap(bounty1.unitKills));
            stmt.setBytes(13, ArrayUtil.writeEnumMap(bounty1.unitAttacks));
            stmt.setLong(14, ArrayUtil.writeEnumSet(bounty1.allowedWarTypes));
            stmt.setLong(15, ArrayUtil.writeEnumSet(bounty1.allowedWarStatus));
            stmt.setLong(16, ArrayUtil.writeEnumSet(bounty1.allowedAttackTypes));
            stmt.setLong(17, ArrayUtil.writeEnumSet(bounty1.allowedAttackRolls));
            stmt.setInt(18, bounty1.id);
        };
        update(query, stmt -> setStmt.accept(bounty, stmt));
    }

    public List<CustomBounty> getCustomBounties() {
        List<CustomBounty> list = new ArrayList<>();
        String query = "SELECT * `CUSTOM_BOUNTIES`";
        query(query, f -> {}, new ThrowingConsumer<ResultSet>() {
            @Override
            public void acceptThrows(ResultSet rs) throws SQLException, IOException {
                CustomBounty bounty = new CustomBounty();

                bounty.id = rs.getInt("id");
                bounty.placedBy = rs.getInt("placed_by");
                bounty.date = rs.getLong("date_created");
                bounty.claimedBy = rs.getLong("claimed_by");
                bounty.resources = ArrayUtil.toDoubleArray(rs.getBytes("amount"));
                bounty.nations = ArrayUtil.readIntSet(rs.getBytes("nations"));
                bounty.alliances = ArrayUtil.readIntSet(rs.getBytes("alliances"));
                bounty.filter2 = new NationFilterString(rs.getString("filter"), null, null, null);
                bounty.totalDamage = rs.getLong("total_damage");
                bounty.infraDamage = rs.getLong("infra_damage");
                bounty.unitDamage = rs.getLong("unit_damage");
                bounty.onlyOffensives = rs.getInt("only_offensives") == 1;
                bounty.unitKills = ArrayUtil.readEnumMap(rs.getBytes("unit_kills"), MilitaryUnit.class);
                bounty.unitAttacks = ArrayUtil.readEnumMap(rs.getBytes("unit_attacks"), MilitaryUnit.class);
                bounty.allowedWarTypes = ArrayUtil.readEnumSet(rs.getLong("allowed_war_types"), WarType.class);
                bounty.allowedWarStatus = ArrayUtil.readEnumSet(rs.getLong("allowed_war_status"), WarStatus.class);
                bounty.allowedAttackTypes = ArrayUtil.readEnumSet(rs.getLong("allowed_attack_types"), AttackType.class);
                bounty.allowedAttackRolls = ArrayUtil.readEnumSet(rs.getLong("allowed_attack_rolls"), SuccessType.class);
                list.add(bounty);
            }
        });
        return list;
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
                    case EXTENSION:
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
                    if (war.getStatus() == WarStatus.ATTACKER_VICTORY) {
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
        Map<Integer, DBWar> wars = getWarsForNationOrAlliance(null, alliances::contains, f -> alliances.contains(f.getDefender_aa()));
        String queryStr = "SELECT * FROM COUNTER_STATS WHERE id IN " + StringMan.getString(wars.values().stream().map(f -> f.warId).collect(Collectors.toList()));
        try (PreparedStatement stmt= getConnection().prepareStatement(queryStr)) {
            try (ResultSet rs = stmt.executeQuery()) {
                List<Map.Entry<DBWar, CounterStat>> result = new ArrayList<>();
                while (rs.next()) {
                    int id = rs.getInt("id");
                    CounterStat stat = new CounterStat();
                    stat.isActive = rs.getBoolean("active");
                    stat.type = CounterType.values[rs.getInt("type")];
                    DBWar war = getWar(id);
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
                if (rs.next()) {
                    int id = rs.getInt("id");
                    CounterStat stat = new CounterStat();
                    stat.isActive = rs.getBoolean("active");
                    stat.type = CounterType.values[rs.getInt("type")];
                    return stat;
                }
            }
            return updateCounter(war, f -> Locutus.imp().runEventsAsync(List.of(f)));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public CounterStat updateCounter(DBWar war, Consumer<Event> eventConsumer) {
        DBNation attacker = Locutus.imp().getNationDB().getNationById(war.getAttacker_id());
        DBNation defender = Locutus.imp().getNationDB().getNationById(war.getDefender_id());
        if (war.getAttacker_aa() == 0 || war.getDefender_aa() == 0) {
            CounterStat stat = new CounterStat();
            stat.type = CounterType.UNCONTESTED;
            stat.isActive = defender != null && defender.active_m() < 2880;
            return stat;
        }
        int warId = war.warId;
        List<AbstractCursor> attacks = getAttacksByWarId2(war, false);

        long startDate = war.getDate();
        long startTurn = TimeUtil.getTurn(startDate);

        long endTurn = startTurn + 60 - 1;
        long endDate = TimeUtil.getTimeFromTurn(endTurn + 1);

        boolean isOngoing = war.getStatus() == WarStatus.ACTIVE || war.getStatus() == WarStatus.DEFENDER_OFFERED_PEACE || war.getStatus() == WarStatus.ATTACKER_OFFERED_PEACE;
        boolean isActive = war.getStatus() == WarStatus.DEFENDER_OFFERED_PEACE || war.getStatus() == WarStatus.DEFENDER_VICTORY || war.getStatus() == WarStatus.ATTACKER_OFFERED_PEACE;
        for (AbstractCursor attack : attacks) {
//            if (attack.getAttack_type() == AttackType.VICTORY && attack.getAttacker_id() == war.getAttacker_id() && war.getStatus() != WarStatus.ATTACKER_VICTORY) {
//                DBWar oldWar = new DBWar(war);
//                war.setStatus(WarStatus.ATTACKER_VICTORY);
//                if (eventConsumer != null) {
//                    eventConsumer.accept(new WarStatusChangeEvent(oldWar, war));
//                }
//                activeWars.makeWarInactive(war);
//                activeWars.processWarChange(oldWar, war, eventConsumer);
//            }
            if (attack.getAttacker_id() == war.getDefender_id()) isActive = true;
            switch (attack.getAttack_type()) {
                case A_LOOT:
                case VICTORY:
                case PEACE:
                    endTurn = TimeUtil.getTurn(attack.getDate());
                    endDate = attack.getDate();
                    break;
            }
        }

        Set<Integer> attAA = new HashSet<>(Collections.singleton(war.getAttacker_aa()));
        for (Map.Entry<Integer, Treaty> entry : Locutus.imp().getNationDB().getTreaties(war.getAttacker_aa()).entrySet()) {
            switch (entry.getValue().getType()) {
                case EXTENSION:
                case MDP:
                case MDOAP:
                case ODP:
                case ODOAP:
                case PROTECTORATE:
                    attAA.add(entry.getKey());
            }
        }

        Set<Integer> defAA = new HashSet<>(Collections.singleton(war.getDefender_aa()));
        for (Map.Entry<Integer, Treaty> entry : Locutus.imp().getNationDB().getTreaties(war.getDefender_aa()).entrySet()) {
            switch (entry.getValue().getType()) {
                case EXTENSION:
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

        Set<Integer> nationIds = new HashSet<>(Arrays.asList(war.getAttacker_id(), war.getDefender_id()));
        long finalEndDate = endDate;
        Collection<DBWar> possibleCounters = getWarsForNationOrAlliance(nationIds::contains, null,
                f -> f.getDate() >= startDate - TimeUnit.DAYS.toMillis(5) && f.getDate() <= finalEndDate).values();
        for (DBWar other : possibleCounters) {
            if (other.warId == war.warId) continue;
            if (war.isActive() && !other.isActive() && (System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(15) < war.getDate())) continue;
            if (attAA.contains(other.getAttacker_aa()) || !(defAA.contains(other.getAttacker_aa()))) continue;
            if (other.getDate() < war.getDate()) {
                if (other.getAttacker_id() == war.getDefender_id() && attAA.contains(other.getDefender_aa())) {
                    isCounter.add(other.warId);
                }
            } else if (other.getDefender_id() == war.getAttacker_id()) {
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

    public DBBounty getBountyById(int id) {
        String query = "SELECT * FROM `BOUNTIES_V3` WHERE id = ?";
        try (PreparedStatement stmt = getConnection().prepareStatement(query)) {
            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new DBBounty(rs);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
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

    public boolean hasAnyBounties() {
        return select("SELECT 1 FROM `BOUNTIES_V3` LIMIT 1", f -> {}, (ThrowingFunction<ResultSet, Boolean>) ResultSet::next);
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

    private final Object bountyLock = new Object();

    public void updateBountiesV3() {
        List<Event> events = null;
        synchronized (bountyLock) {
            Set<DBBounty> removedBounties = getBounties();
            Set<DBBounty> newBounties = new LinkedHashSet<>();

            boolean callEvents = !removedBounties.isEmpty();

            PoliticsAndWarV3 v3 = Locutus.imp().getV3();
            Collection<Bounty> bounties;
            if (Settings.INSTANCE.ENABLED_COMPONENTS.SNAPSHOTS) {
                bounties = v3.readSnapshot(PagePriority.API_BOUNTIES, Bounty.class);
            } else {
                bounties = v3.fetchBountiesWithInfo();
            }

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
                if (callEvents) {
                    (events == null ? (events = new ArrayList<>()) : events).add(new BountyRemoveEvent(bounty));
                }
            }
            for (DBBounty bounty : newBounties) {
                addBounty(bounty);
                if (Settings.INSTANCE.LEGACY_SETTINGS.DEANONYMIZE_BOUNTIES) {
                    // TODO remove this
                }
                if (callEvents) {
                    (events == null ? (events = new ArrayList<>()) : events).add(new BountyCreateEvent(bounty));
                }
            }
        }
        if (events != null) {
            Locutus.imp().runEventsAsync(events);
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

    private long lastAllWars = System.currentTimeMillis();

    public boolean updateAllWars(Consumer<Event> eventConsumer) {
        long now = System.currentTimeMillis();
        long diff = now - lastAllWars;
        if (diff < TimeUnit.MINUTES.toMillis(10) || !Settings.INSTANCE.ENABLED_COMPONENTS.SNAPSHOTS) {
            lastAllWars = now;
            long start = TimeUtil.getTimeFromTurn(TimeUtil.getTurn() - 61);
            return updateWarsSince(eventConsumer, start);
        }
        List<DBWar> wars = Locutus.imp().getV3().readSnapshot(PagePriority.API_WARS, War.class)
                .stream().map(f -> new DBWar(f, false)).toList();
        Set<Integer> activeWarIds = getActiveWars().stream().map(DBWar::getWarId).collect(Collectors.toSet());
        return updateWars(wars, activeWarIds, eventConsumer, eventConsumer != null);
    }

    public boolean updateWarsSince(Consumer<Event> eventConsumer, long date) {
        Set<Integer> activeWarsToFetch = new LinkedHashSet<>(getWarsSince(date).keySet());
        PoliticsAndWarV3 api = Locutus.imp().getV3();
        List<War> wars = api.fetchWarsWithInfo(r -> {
            r.setAfter(new Date(date));
            r.setActive(false); // needs to be set otherwise inactive wars wont be fetched
        });

        if (wars.isEmpty()) {
            AlertUtil.error("Failed to fetch wars", new Exception());
            return false;
        }

        List<DBWar> dbWars = wars.stream().map(DBWar::new).collect(Collectors.toList());
        int numActive = activeWarsToFetch.size();
        for (DBWar war : dbWars) {
            activeWarsToFetch.remove(war.getWarId());
        }
        updateWars(dbWars, null, eventConsumer, true);

        if (!activeWarsToFetch.isEmpty()) {
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
            }

            if (notDeleted > 0) {
                AlertUtil.error("Unable to fetch " + notDeleted + "/" + numActive + " active wars:", new RuntimeException("Ignore if these wars correspond to deleted nations:\n" + StringMan.getString(activeWarsToFetch)));
            }
        }
        return true;
    }

    public boolean updateAllWarsV2(Consumer<Event> eventConsumer) throws IOException {
        List<SWarContainer> wars = Locutus.imp().getPnwApiV2().getWarsByAmount(5000).getWars();
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
        ObjectOpenHashSet<DBWar> activeWarsById = activeWars.getActiveWarsById();

        // Find deleted wars
        for (int id = minId; id <= maxId; id++) {
            if (fetchedWarIds.contains(id)) continue;
            DBWar war = activeWarsById.get(new DBWar.DBWarKey(id));
            if (war == null) continue;

            DBWar newWar = new DBWar(war);
            newWar.setStatus(WarStatus.EXPIRED);
            dbWars.add(newWar);
        }

        boolean result = updateWars(dbWars, null, eventConsumer, true);
        return result;
    }

    public boolean updateActiveWars(Consumer<Event> eventConsumer, boolean useV2) throws IOException {
        if (activeWars.isEmpty()) {
            if (useV2) {
                return updateAllWarsV2(eventConsumer);
            } else {
                return true;
            }
        }
        ObjectOpenHashSet<DBWar> wars = activeWars.getActiveWars();
        List<Integer> ids = new IntArrayList(wars.size());
        for (DBWar war : wars) {
            ids.add(war.getWarId());
        }
        fetchWarsById(ids, eventConsumer);
        return true;
    }

    public void fetchWarsById(Collection<Integer> ids, Consumer<Event> eventConsumer) {
        List<Integer> idsSorted = new ArrayList<>(ids);
        Collections.sort(idsSorted);
        int chunkSize = PoliticsAndWarV3.WARS_PER_PAGE;
        if (idsSorted.size() % chunkSize != 0) {
            int toAdd = chunkSize - (idsSorted.size() % chunkSize);
            int maxId = idsSorted.get(idsSorted.size() - 1);
            for (int i = 0; i < toAdd; i++) {
                idsSorted.add(maxId + i + 1);
            }
        }
        for (int i = 0; i < idsSorted.size(); i += chunkSize) {
            int end = Math.min(i + chunkSize, idsSorted.size());
            List<Integer> subList = idsSorted.subList(i, end);

            PoliticsAndWarV3 api = Locutus.imp().getV3();
            List<War> warsQL = api.fetchWarsWithInfo(r -> {
                r.setId(subList);
                r.setActive(false);
            });

            List<DBWar> wars = warsQL.stream().map(DBWar::new).collect(Collectors.toList());
            updateWars(wars, subList, eventConsumer, true);
        }
    }

    public void fetchNewWars(Consumer<Event> eventConsumer) {
        int maxId = activeWars.getActiveWars().stream().mapToInt(f -> f.warId).max().orElse(0);
        if (maxId == 0) {
            return;
        }
        PoliticsAndWarV3 api = Locutus.imp().getV3();
        List<War> warsQl = api.fetchWarsWithInfo(r -> {
            r.setMin_id(maxId + 1);
            r.setActive(false);
        });
        if (warsQl.isEmpty()) return;
        List<DBWar> wars = warsQl.stream().map(DBWar::new).collect(Collectors.toList());
        updateWars(wars, null, eventConsumer, true);
    }

    public boolean updateMostActiveWars(Consumer<Event> eventConsumer) throws IOException {
        int newWarsToFetch = 100;
        int numToUpdate = Math.min(999, PoliticsAndWarV3.WARS_PER_PAGE);

        List<DBWar> mostActiveWars = new ObjectArrayList<>(activeWars.getActiveWars());
        if (mostActiveWars.isEmpty()) return false;

        int latestWarId = 0;

        Map<DBWar, Long> lastActive = new HashMap<>();
        for (DBWar war : mostActiveWars) {
            DBNation nat1 = war.getNation(true);
            DBNation nat2 = war.getNation(false);
            long date = Math.max(nat1 == null ? 0 : nat1.lastActiveMs(), nat2 == null ? 0 : nat2.lastActiveMs());
            lastActive.put(war, date);
        }
        mostActiveWars.sort((o1, o2) -> Long.compare(lastActive.get(o2), lastActive.get(o1)));

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
        updateWars(dbWars, warIdsToUpdate, eventConsumer, true);

        return true;
    }

    public boolean updateWars(List<DBWar> dbWars, Collection<Integer> expectedIds, Consumer<Event> eventConsumer, boolean handleNationStatus) {
        List<DBWar> prevWars = new ArrayList<>();
        List<DBWar> newWars = new ArrayList<>();
        Set<Integer> expectedIdsSet = expectedIds == null ? null : expectedIds instanceof Set ? (Set<Integer>) expectedIds : new ObjectOpenHashSet<>(expectedIds);
        Set<Integer> idsFetched = dbWars.stream().map(DBWar::getWarId).collect(Collectors.toSet());

        for (DBWar war : dbWars) {
            DBWar existing = warsById.get(war);
            if ((existing == null && !war.isActive()) || (existing != null && (war.getStatus() == existing.getStatus() ||
                    (!existing.isActive() && (existing.getStatus() != WarStatus.EXPIRED || existing.getTurnsLeft() <= 0))))) continue;

            prevWars.add(existing == null ? null : new DBWar(existing));
            newWars.add(war);
            war.setCities(existing, true);
            war.setResearch(existing, true);

            if (handleNationStatus && existing == null && war.getDate() > System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(15) && war.isActive()) {
                Locutus.imp().getNationDB().setNationActive(war.getAttacker_id(), war.getDate(), eventConsumer);
                DBNation attacker = war.getNation(true);
                if (attacker != null && attacker.isBeige()) {
                    DBNation copy = eventConsumer == null ? null : attacker.copy();
                    attacker.setColor(NationColor.GRAY);
                    if (eventConsumer != null) eventConsumer.accept(new NationChangeColorEvent(copy, attacker));
                }
            }
            if (existing != null && existing.getStatus() != war.getStatus()) {
                existing.setStatus(war.getStatus());
            }
        }

        long cannotExpireWithin15m = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(15);
        for (DBWar war : activeWars.getActiveWars()) {
            if (war.getDate() >= cannotExpireWithin15m) continue;
            if (expectedIdsSet != null && expectedIdsSet.contains(war.getWarId()) && !idsFetched.contains(war.getWarId())) {
                prevWars.add(new DBWar(war));
                war.setStatus(WarStatus.EXPIRED);
                newWars.add(war);
                continue;
            }
        }

        for (DBWar war : newWars) {
            setWar(war);
        }

        List<Map.Entry<DBWar, DBWar>> warUpdatePreviousNow = new ArrayList<>();

        for (int i = 0 ; i < prevWars.size(); i++) {
            DBWar previous = prevWars.get(i);
            DBWar newWar = newWars.get(i);
            if (newWar.isActive()) {
                activeWars.addActiveWar(newWar);
            } else {
                activeWars.makeWarInactive(newWar);
                if (handleNationStatus && previous != null && previous.isActive() && !newWar.isActive()) {
                    boolean isAttacker = newWar.getStatus() == WarStatus.ATTACKER_VICTORY;
                    DBNation defender = newWar.getNation(!isAttacker);
                    if (defender != null) {
                        if (newWar.getStatus() == WarStatus.DEFENDER_VICTORY || newWar.getStatus() == WarStatus.ATTACKER_VICTORY) {
                            if (defender.getColor() != NationColor.BEIGE) {
                                DBNation copyOriginal = defender.copy();
                                defender.setColor(NationColor.BEIGE);
                                defender.setBeigeTimer(TimeUtil.getTurn() + 24);
                                if (eventConsumer != null)
                                    eventConsumer.accept(new NationChangeColorEvent(copyOriginal, defender));
                            }
                        } else if (newWar.getStatus() == WarStatus.EXPIRED) {
                            if (eventConsumer != null) {
                                eventConsumer.accept(new NationChangeDefEvent(defender, defender));
                            }
                        }
                    }
                }
            }

            warUpdatePreviousNow.add(new AbstractMap.SimpleEntry<>(previous, newWar));
        }

        saveWars(newWars, false);

        if (!warUpdatePreviousNow.isEmpty() && eventConsumer != null) {
            try {
                WarUpdateProcessor.processWars(warUpdatePreviousNow, eventConsumer);
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }

        return true;
    }

    public void saveWars(Collection<DBWar> values, boolean addToMap) {
        if (values.isEmpty()) return;
        if (addToMap) {
            for (DBWar war : values) {
                setWar(war);
            }
        }
//        List<Map.Entry<Integer, DBNation>> nationSnapshots = new ArrayList<>();
//        for (DBWar war : values) {
//            DBNation attacker = war.getNation(true);
//            DBNation defender = war.getNation(false);
//            if (attacker != null) {
//                nationSnapshots.add(Map.entry(war.getWarId(), attacker));
//            }
//            if (defender != null) {
//                nationSnapshots.add(Map.entry(war.getWarId(), defender));
//            }
//        }

        String query = "INSERT OR REPLACE INTO `wars`(`id`, `attacker_id`, `defender_id`, `attacker_aa`, `defender_aa`, `war_type`, `status`, `date`, `attCities`, `defCities`) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        ThrowingBiConsumer<DBWar, PreparedStatement> setStmt = (war, stmt) -> {
            stmt.setInt(1, war.warId);
            stmt.setLong(2, war.getAttacker_id());
            stmt.setInt(3, war.getDefender_id());
            stmt.setInt(4, war.getAttacker_aa());
            stmt.setInt(5, war.getDefender_aa());
            stmt.setInt(6, war.getWarType().ordinal());
            stmt.setInt(7, war.getStatus().ordinal());
            stmt.setLong(8, war.getDate());
            stmt.setInt(9, war.getAttCities());
            stmt.setInt(10, war.getDefCities());
        };
        if (values.size() == 1) {
            DBWar value = values.iterator().next();
            update(query, stmt -> setStmt.accept(value, stmt));
        } else {
            executeBatch(values, query, setStmt);
        }
    }

    public Map<Integer, DBWar> getWars(WarStatus status) {
        return getWars(f -> f.getStatus() == status);
    }

    public Map<Integer, DBWar> getWarsSince(long date) {
        return getWars(f -> f.getDate() > date);
    }

    public ObjectOpenHashSet<DBWar> getWars() {
        synchronized (warsById) {
            return new ObjectOpenHashSet<>(warsById);
        }
    }

    public Map<Integer, List<DBWar>> getActiveWarsByAttacker(Set<Integer> attackers, Set<Integer> defenders, WarStatus... statuses) {
        Set<Integer> all = new HashSet<>();

        Map<Integer, List<DBWar>> map = new Int2ObjectOpenHashMap<>();
        activeWars.getActiveWars(f -> all.contains(f), new Predicate<DBWar>() {
            @Override
            public boolean test(DBWar war) {
                if (attackers.contains(war.getAttacker_id()) || defenders.contains(war.getDefender_id())) {
                    List<DBWar> list = map.computeIfAbsent(war.getAttacker_id(), k -> new ArrayList<>());
                    list.add(war);
                }
                return false;
            }
        });
        return map;
    }

    private DBWar create(ResultSet rs) throws SQLException {
        int warId = rs.getInt(1);
        int attacker_id = rs.getInt(2);
        int defender_id = rs.getInt(3);
        int attacker_aa = rs.getInt(4);
        int defender_aa = rs.getInt(5);
        WarType war_type = WarType.values[rs.getInt(6)];
        WarStatus status = WarStatus.values[rs.getInt(7)];
        long date = rs.getLong(8);
        int attCities = rs.getInt(9);
        int defCities = rs.getInt(10);
        int research = rs.getInt(11);
        return new DBWar(warId, attacker_id, defender_id, attacker_aa, defender_aa, war_type, status, date, attCities, defCities, research);
    }

    public DBWar getWar(int warId) {
        return warsById.get(new DBWar.DBWarKey(warId));
    }

    public List<DBWar> getWars(int nation1, int nation2, long start, long end) {
        List<DBWar> list = new ArrayList<>();
        synchronized (warsByNationLock) {
            Object wars = warsByNationId.get(nation1);
            if (wars != null) {
                ArrayUtil.iterateElements(DBWar.class, wars, dbWar -> {
                    if ((dbWar.getDefender_id() == nation2 || dbWar.getAttacker_id() == nation1) && dbWar.getDate() > start && dbWar.getDate() < end) {
                        list.add(dbWar);
                    }
                });
            }
        }
        return list;
    }

    public DBWar getActiveWarByNation(int attacker, int defender) {
        for (DBWar war : activeWars.getActiveWars(attacker)) {
            if (war.getAttacker_id() == attacker && war.getDefender_id() == defender) {
                return war;
            }
        }
        return null;
    }

    public Set<DBWar> getWarsByNation(int nation, WarStatus status) {
        if (status == WarStatus.ACTIVE || status == WarStatus.ATTACKER_OFFERED_PEACE || status == WarStatus.DEFENDER_OFFERED_PEACE) {
            Set<DBWar> wars = new ObjectOpenHashSet<>();
            for (DBWar war : activeWars.getActiveWars(nation)) {
                if (war.getStatus() == status) {
                    wars.add(war);
                }
            }
            return wars;
        }
        Set<DBWar> list;
        synchronized (warsByNationLock) {
            Object wars = warsByNationId.get(nation);
            if (wars == null) return Collections.emptySet();
            list = new ObjectOpenHashSet<>();
            ArrayUtil.iterateElements(DBWar.class, wars, dbWar -> {
                if (dbWar.getStatus() == status) {
                    list.add(dbWar);
                }
            });
        }
        return list;
    }

    public Set<DBWar> getActiveWarsByAlliance(Set<Integer> attackerAA, Set<Integer> defenderAA) {
        return activeWars.getActiveWars(f -> true, f -> (attackerAA == null || attackerAA.contains(f.getAttacker_aa())) && (defenderAA == null || defenderAA.contains(f.getDefender_aa())));
    }

    public Set<DBWar> getWarsByAlliance(int attacker) {
        synchronized (warsByAllianceId) {
            Object wars = warsByAllianceId.get(attacker);
            if (wars == null) return Collections.emptySet();
            return ArrayUtil.toSet(DBWar.class, wars);
        }
    }

    public Set<DBWar> getWarsByNation(int nationId) {
        Set<DBWar> result;
        synchronized (warsByNationLock) {
            Object amt = warsByNationId.get(nationId);
            result = ArrayUtil.toSet(DBWar.class, amt);
        }
        return result;
    }

    public Set<DBWar> getWarsByNationMatching(int nationId, Predicate<DBWar> filter) {
        Set<DBWar> list;
        synchronized (warsByNationLock) {
            Object wars = warsByNationId.get(nationId);
            if (wars == null) return Collections.emptySet();
            list = new ObjectOpenHashSet<>();
            ArrayUtil.iterateElements(DBWar.class, wars, dbWar -> {
                if (filter.test(dbWar)) {
                    list.add(dbWar);
                }
            });
        }
        return list;
    }

    public DBWar getLastOffensiveWar(int nation) {
        return getWarsByNation(nation).stream().filter(f -> f.getAttacker_id() == nation).max(Comparator.comparingInt(o -> o.warId)).orElse(null);
    }

    public DBWar getLastOffensiveWar(int nation, Long beforeDate) {
        Stream<DBWar> filter = getWarsByNation(nation).stream().filter(f -> f.getAttacker_id() == nation);
        if (beforeDate != null) filter = filter.filter(f -> f.getDate() < beforeDate);
        return filter.max(Comparator.comparingInt(o -> o.warId)).orElse(null);
    }

    public DBWar getLastDefensiveWar(int nation) {
        return getWarsByNation(nation).stream().filter(f -> f.getDefender_id() == nation).max(Comparator.comparingInt(o -> o.warId)).orElse(null);
    }

    public DBWar getLastDefensiveWar(int nation, Long beforeDate) {
        Stream<DBWar> filter = getWarsByNation(nation).stream().filter(f -> f.getDefender_id() == nation);
        if (beforeDate != null) filter = filter.filter(f -> f.getDate() < beforeDate);
        return filter.max(Comparator.comparingInt(o -> o.warId)).orElse(null);

    }

    public DBWar getLastWar(int nationId, Long snapshot) {
        Stream<DBWar> filter = getWarsByNation(nationId).stream();
        if (snapshot != null) filter = filter.filter(f -> f.getDate() < snapshot);
        return filter.max(Comparator.comparingInt(o -> o.warId)).orElse(null);
    }

    public Set<DBWar> getWarsByNation(int nation, WarStatus... statuses) {
        if (statuses.length == 0) return getWarsByNation(nation);
        if (statuses.length == 1) return getWarsByNation(nation, statuses[0]);
        Set<WarStatus> statusSet = new HashSet<>(Arrays.asList(statuses));

        Set<DBWar> set;
        synchronized (warsByNationLock) {
            Object wars = warsByNationId.get(nation);
            set = new ObjectOpenHashSet<>();
            ArrayUtil.iterateElements(DBWar.class, wars, dbWar -> {
                if (statusSet.contains(dbWar.getStatus())) {
                    set.add(dbWar);
                }
            });
        }
        return set;
    }

    public Set<DBWar> getActiveWars(Set<Integer> alliances, WarStatus... statuses) {
        if (statuses.length == 0) {
            return Collections.emptySet();
        }
        // ordinal to boolean array
        boolean[] warStatuses = WarStatus.toArray(statuses);
        // enum set?
        return activeWars.getActiveWars(f -> true, f -> (alliances.contains(f.getAttacker_aa()) || alliances.contains(f.getDefender_aa())) && warStatuses[f.getStatus().ordinal()]);
    }

    public Set<DBWar> getWarByStatus(WarStatus... statuses) {
        boolean[] warStatuses = WarStatus.toArray(statuses);
        return new ObjectOpenHashSet<>(getWars(f -> warStatuses[f.getStatus().ordinal()]).values());
    }

    public Set<DBWar> getWars(Set<Integer> alliances, long start) {
        return getWars(alliances, start, Long.MAX_VALUE);
    }

    public Set<DBWar> getWars(Set<Integer> alliances, long start, long end) {
        Map<Integer, DBWar> wars = getWarsForNationOrAlliance(null, alliances::contains, f -> f.getDate() > start && f.getDate() < end);
        return new ObjectOpenHashSet<>(wars.values());
    }

    public Set<DBWar> getWarsById(Set<Integer> warIds) {
        Set<DBWar> result = new ObjectOpenHashSet<>();
        synchronized (warsById) {
            for (Integer id : warIds) {
                DBWar war = warsById.get(new DBWar.DBWarKey(id));
                if (war != null) result.add(war);
            }
        }
        return result;
    }
    public Map<Integer, DBWar> getWars(Collection<Integer> coal1Alliances, Collection<Integer> coal1Nations, Collection<Integer> coal2Alliances, Collection<Integer> coal2Nations, long start, long end) {
        if (coal1Alliances.isEmpty() && coal1Nations.isEmpty() && coal2Alliances.isEmpty() && coal2Nations.isEmpty()) return Collections.emptyMap();

        Set<Integer> alliances = new IntOpenHashSet();
        alliances.addAll(coal1Alliances);
        alliances.addAll(coal2Alliances);
        Set<Integer> nations = new IntOpenHashSet();
        nations.addAll(coal1Nations);
        nations.addAll(coal2Nations);

        Predicate<DBWar> isAllowed;
        if (coal1Alliances.isEmpty() && coal1Nations.isEmpty()) {
            isAllowed = new Predicate<DBWar>() {
                @Override
                public boolean test(DBWar war) {
                    if (war.getDate() < start || war.getDate() > end) return false;
                    return coal2Alliances.contains(war.getAttacker_aa()) || coal2Nations.contains(war.getAttacker_id()) || coal2Alliances.contains(war.getDefender_aa()) || coal2Nations.contains(war.getDefender_id());
                }
            };
        } else if (coal2Alliances.isEmpty() && coal2Nations.isEmpty()) {
            isAllowed = new Predicate<DBWar>() {
                @Override
                public boolean test(DBWar war) {
                    if (war.getDate() < start || war.getDate() > end) return false;
                    return coal1Alliances.contains(war.getAttacker_aa()) || coal1Nations.contains(war.getAttacker_id()) || coal1Alliances.contains(war.getDefender_aa()) || coal1Nations.contains(war.getDefender_id());
                }
            };
        } else {
            isAllowed = new Predicate<DBWar>() {
                @Override
                public boolean test(DBWar war) {
                    if (war.getDate() < start || war.getDate() > end) return false;
                    return ((coal1Alliances.contains(war.getAttacker_aa()) || coal1Nations.contains(war.getAttacker_id())) && (coal2Alliances.contains(war.getDefender_aa()) || coal2Nations.contains(war.getDefender_id()))) ||
                            ((coal1Alliances.contains(war.getDefender_aa()) || coal1Nations.contains(war.getDefender_id())) && (coal2Alliances.contains(war.getAttacker_aa()) || coal2Nations.contains(war.getAttacker_id())));
                }
            };
        }

        return getWarsForNationOrAlliance(nations.isEmpty() ? null : nations::contains, alliances.isEmpty() ? null : alliances::contains, isAllowed);
    }
//
//    private String generateWarQuery(String prefix, Collection<Integer> coal1Alliances, Collection<Integer> coal1Nations, Collection<Integer> coal2Alliances, Collection<Integer> coal2Nations, long start, long end, boolean union) {
//        List<String> requirements = new ArrayList<>();
//        if (start > 0) {
//            requirements.add(prefix + "date > " + start);
//        }
//        if (end < System.currentTimeMillis()) {
//            requirements.add(prefix + "date < " + end);
//        }
//
//        List<String> attReq = new ArrayList<>();
//        if (!coal1Alliances.isEmpty()) {
//            if (coal1Alliances.size() == 1) {
//                Integer id = coal1Alliances.iterator().next();
//                attReq.add(prefix + "attacker_aa = " + id);
//            } else {
//                attReq.add(prefix + "attacker_aa in " + StringMan.getString(coal1Alliances));
//            }
//        }
//        if (!coal1Nations.isEmpty()) {
//            if (coal1Nations.size() == 1) {
//                Integer id = coal1Nations.iterator().next();
//                attReq.add(prefix + "attacker_id = " + id);
//            } else {
//                attReq.add(prefix + "attacker_id in " + StringMan.getString(coal1Nations));
//            }
//        }
//
//        List<String> defReq = new ArrayList<>();
//        if (!coal2Alliances.isEmpty()) {
//            if (coal2Alliances.size() == 1) {
//                Integer id = coal2Alliances.iterator().next();
//                defReq.add(prefix + "defender_aa = " + id);
//            } else {
//                defReq.add(prefix + "defender_aa in " + StringMan.getString(coal2Alliances));
//            }
//        }
//        if (!coal2Nations.isEmpty()) {
//            if (coal2Nations.size() == 1) {
//                Integer id = coal2Nations.iterator().next();
//                defReq.add(prefix + "defender_id = " + id);
//            } else {
//                defReq.add(prefix + "defender_id in " + StringMan.getString(coal2Nations));
//            }
//        }
//
//        List<String> natOrAAReq = new ArrayList<>();
//        if (!attReq.isEmpty()) natOrAAReq.add("(" + StringMan.join(attReq, " AND ") + ")");
//        if (!defReq.isEmpty()) natOrAAReq.add("(" + StringMan.join(defReq, " AND ") + ")");
//        String natOrAAReqStr = "(" + StringMan.join(natOrAAReq, union ? " AND " : " OR ") + ")";
//        requirements.add(natOrAAReqStr);
//
//
//        String query = "SELECT * from wars WHERE " + StringMan.join(requirements, " AND ");
//        return query;
//    }

    private final AttackCursorFactory attackCursorFactory = new AttackCursorFactory(this);
    private long lastUnloadAttacks = 0;

    public void saveAttacks(Collection<AbstractCursor> values, Consumer<Event> eventConsumer) {
        if (values.isEmpty()) return;

        // sort attacks
        ArrayList<AbstractCursor> valuesList = new ArrayList<>(values);
        valuesList.sort(Comparator.comparingInt(AbstractCursor::getWar_attack_id));
        values = valuesList;

        List<LootEntry> lootList = null;
        for (AbstractCursor attack : values) {
            if (attack.getAttack_type() != AttackType.VICTORY && attack.getAttack_type() != AttackType.A_LOOT) continue;

            double[] loot = attack.getLoot();
            double pct;
            if (loot == null) {
                pct = 1d;
            } else {
                pct = attack.getLootPercent();
            }
            if (pct == 0) pct = 0.1;
            double factor = 1/pct;

            double[] lootCopy;
            if (loot != null) {
                lootCopy = loot.clone();
                for (int i = 0; i < lootCopy.length; i++) {
                    lootCopy[i] = (lootCopy[i] * factor) - lootCopy[i];
                }
            } else {
                lootCopy = ResourceType.getBuffer();
            }
            if (attack.getAttack_type() == AttackType.VICTORY) {
                (lootList == null ? lootList = new ObjectArrayList<>() : lootList).add(
                    LootEntry.forNation(attack.getDefender_id(), attack.getDate(), lootCopy, NationLootType.WAR_LOSS));
            } else if (attack.getAttack_type() == AttackType.A_LOOT) {
                int allianceId = attack.getAllianceIdLooted();
                if (allianceId > 0) {
                    (lootList == null ? lootList = new ObjectArrayList<>() : lootList).add(
                            LootEntry.forAlliance(allianceId, attack.getDate(), lootCopy, NationLootType.WAR_LOSS));
                }
            }
        }

        if (lootList != null) {
            Locutus.imp().getNationDB().saveLoot(lootList, eventConsumer);
        }

        List<AttackEntry> toSave = new ArrayList<>();
        Map<Integer, Set<Integer>> attackIdsByWarId = new Int2ObjectOpenHashMap<>();

        // add to attacks map
        synchronized (attacksByWarId2) {
            for (AbstractCursor attack : values) {
                // AttackEntry(int id, int war_id, int attacker_id, int defender_id, long date, byte[] data) {
                toSave.add(new AttackEntry(attack.getWar_attack_id(), attack.getWar_id(), attack.getAttacker_id(), attack.getDefender_id(), attack.getDate(), attackCursorFactory.toBytes(attack)));
                List<byte[]> attacks = attacksByWarId2.get(attack.getWar_id());

                Set<Integer> attackIds = attackIdsByWarId.get(attack.getWar_id());
                if (attackIds == null && attacks != null && !attacks.isEmpty()) {
                    for (byte[] data : attacks) {
                        int id = attackCursorFactory.getId(data);
                        attackIds = new IntOpenHashSet();
                        attackIds.add(id);
                        attackIdsByWarId.put(attack.getWar_id(), attackIds);
                    }
                }
                if (attackIds != null && attackIds.contains(attack.getWar_attack_id())) continue;
                if (attacks == null) {
                    attacks = new ObjectArrayList<>();
                    attacksByWarId2.put(attack.getWar_id(), attacks);
                }

                byte[] data = attackCursorFactory.toBytes(attack);
                attacks.add(data);
            }
            if (!Settings.INSTANCE.TASKS.LOAD_INACTIVE_ATTACKS && values.size() > 1) {
                long turn = TimeUtil.getTurn();
                if (turn > lastUnloadAttacks) {
                    lastUnloadAttacks = turn;
                    Set<Integer> toRemove = null;
                    long timeCutoff = TimeUtil.getTimeFromTurn(turn - 120);
                    for (int warId : attacksByWarId2.keySet()) {
                        DBWar war = getWar(warId);
                        if (war != null && !war.isActive() && war.getDate() < timeCutoff) {
                            if (toRemove == null) toRemove = new IntOpenHashSet();
                            toRemove.add(warId);
                        }
                    }
                    if (toRemove != null) {
                        for (int warId : toRemove) {
                            attacksByWarId2.remove(warId);
                        }
                    }
                }
            }
        }

        // String query = "INSERT OR IGNORE INTO `ATTACKS3` (`war_id`, `attacker_nation_id`, `defender_nation_id`, `date`, `data`) VALUES (?, ?, ?, ?, ?)";
        String query = "INSERT OR REPLACE INTO `ATTACKS3` (`id`, `war_id`, `attacker_nation_id`, `defender_nation_id`, `date`, `data`) VALUES (?, ?, ?, ?, ?, ?)";
        executeBatch(toSave, query, new ThrowingBiConsumer<AttackEntry, PreparedStatement>() {
            @Override
            public void acceptThrows(AttackEntry attack, PreparedStatement stmt) throws SQLException {
                stmt.setInt(1, attack.id());
                stmt.setInt(2, attack.war_id());
                stmt.setInt(3, attack.attacker_id());
                stmt.setInt(4, attack.defender_id());
                stmt.setLong(5, attack.date());
                stmt.setBytes(6, attack.data());
            }
        });
    }

    public boolean updateAttacks(Consumer<Event> eventConsumer) {
        return updateAttacks( eventConsumer != null, eventConsumer);
    }

    private AbstractCursor getLatestAttack() {
        // active wars
        AbstractCursor[] latest = new AbstractCursor[1];
        getAttacks(activeWars.getActiveWarsById(), null, new Predicate<AbstractCursor>() {
            int latestId = 0;
            @Override
            public boolean test(AbstractCursor cursor) {
                if (cursor.getWar_attack_id() > latestId) {
                    latestId = cursor.getWar_attack_id();
                    latest[0] = cursor;
                    return true;
                }
                return false;
            }
        }, f -> false);
        return latest[0];
    }
    public boolean updateAttacks(boolean runAlerts, Consumer<Event> eventConsumer) {
        return updateAttacks(runAlerts, eventConsumer, false);
    }

    public boolean updateAttacks(boolean runAlerts, Consumer<Event> eventConsumer, boolean v2) {
        AbstractCursor latest = getLatestAttack();
        return updateAttacks(latest, runAlerts, eventConsumer, v2);
    }

    private boolean updateAttacks(AbstractCursor latest, boolean runAlerts, Consumer<Event> eventConsumer, boolean v2) {
        Integer maxId = latest == null ? null : latest.getWar_attack_id();
        if (maxId == null || maxId == 0) runAlerts = false;

        AttackCursorFactory factory = new AttackCursorFactory(this);

        // Dont run events if attacks are > 1 day old
        if (!v2 && (latest == null || latest.getDate() < System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1))) {
            PoliticsAndWarV3 v3 = Locutus.imp().getV3();
            Logg.text("No recent attack data in DB. Updating attacks without event handling: " + maxId);
            List<AbstractCursor> attackList = new ArrayList<>();
            v3.fetchAttacksSince(maxId, new Predicate<WarAttack>() {
                @Override
                public boolean test(WarAttack v3Attack) {
                    AbstractCursor attack = factory.load(v3Attack, true);
                    synchronized (attackList) {
                        attackList.add(attack);
                        if (attackList.size() > 1000) {
                            saveAttacks(attackList, eventConsumer);
                            attackList.clear();
                        }
                    }
                    return false;
                }
            });
            saveAttacks(attackList, eventConsumer);
            return true;
        }
        List<AbstractCursor> dbAttacks = new ArrayList<>();
        List<AbstractCursor> newAttacks;
        Set<DBWar> warsToSave = new LinkedHashSet<>();
        List<Map.Entry<DBWar, DBWar>> warsToProcess = new ArrayList<>();
        List<AbstractCursor> dirtyCities = new ArrayList<>();

        synchronized (activeWars) {
            if (v2) {
                try {
                    List<WarAttacksContainer> attacksv2;
                    if (maxId == null) {
                        attacksv2 = Locutus.imp().getPnwApiV2().getWarAttacks().getWarAttacksContainers();
                    } else {
                        attacksv2 = Locutus.imp().getPnwApiV2().getWarAttacksByMinWarAttackId(maxId).getWarAttacksContainers();
                    }
                    newAttacks = attacksv2.stream().map(DBAttack::new).map(f -> factory.load(f, true)).collect(Collectors.toList());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else {
                PoliticsAndWarV3 v3 = Locutus.imp().getV3();
                newAttacks = v3
                        .fetchAttacksSince(maxId, f -> true)
                        .stream().map(f -> factory.load(f, true)).toList();
            }

            // remove existing
            {
                Set<Integer> warIds = newAttacks.stream().map(AbstractCursor::getWar_id).collect(Collectors.toSet());
                Set<Integer> existingAttackIds = new IntOpenHashSet();
                synchronized (attacksByWarId2) {
                    for (int warId : warIds) {
                        List<byte[]> attackData = attacksByWarId2.get(warId);
                        if (attackData == null || attackData.isEmpty()) continue;
                        for (byte[] data : attackData) {
                            existingAttackIds.add(factory.getId(data));
                        }
                    }
                }
                newAttacks = new ObjectArrayList<>(newAttacks);
                newAttacks.removeIf(f -> existingAttackIds.contains(f.getWar_attack_id()));
            }

            long now = System.currentTimeMillis();

            for (AbstractCursor attack : newAttacks) {
                if (runAlerts) {
                    Locutus.imp().getNationDB().setNationActive(attack.getAttacker_id(), attack.getDate(), eventConsumer);
                    Map<MilitaryUnit, Integer> attLosses = attack.getUnitLosses2(true);
                    Map<MilitaryUnit, Integer> defLosses = attack.getUnitLosses2(false);
                    if (!attLosses.isEmpty()) {
                        Locutus.imp().getNationDB().updateNationUnits(attack.getAttacker_id(), attack.getDate(), attLosses, eventConsumer);
                    }
                    if (!defLosses.isEmpty()) {
                        Locutus.imp().getNationDB().updateNationUnits(attack.getDefender_id(), attack.getDate(), defLosses, eventConsumer);
                    }
                }

                if (attack.getAttack_type() == AttackType.NUKE && attack.getSuccess() != SuccessType.UTTER_FAILURE && attack.getCity_id() != 0) {
                    Locutus.imp().getNationDB().setCityNukeFromAttack(attack.getDefender_id(), attack.getCity_id(), attack.getDate(), eventConsumer);
                }

                if (attack.getAttack_type() == AttackType.VICTORY) {
                    DBWar war = activeWars.getWar(attack.getAttacker_id(), attack.getWar_id());
                    if (war != null) {
                        if (runAlerts) {
                            DBNation defender = DBNation.getById(attack.getDefender_id());
                            if (defender != null && defender.getColor() != NationColor.BEIGE && attack.getDate() > now - TimeUnit.MINUTES.toMillis(15)) {
                                DBNation copyOriginal = copyOriginal = defender.copy();
                                defender.setColor(NationColor.BEIGE);
                                defender.setBeigeTimer(TimeUtil.getTurn() + 24);
                                if (copyOriginal != null && eventConsumer != null)
                                    eventConsumer.accept(new NationChangeColorEvent(copyOriginal, defender));
                            }
                            DBWar oldWar = new DBWar(war);
                            WarStatus newStatus = war.getAttacker_id() == attack.getAttacker_id() ? WarStatus.ATTACKER_VICTORY : WarStatus.DEFENDER_VICTORY;
                            if (war.getStatus() != newStatus) {
                                war.setStatus(newStatus);
                                warsToSave.add(war);
                                if (eventConsumer != null) {
                                    warsToProcess.add(new AbstractMap.SimpleEntry<>(oldWar, war));
                                }
                                if (!war.isActive()) {
                                    activeWars.makeWarInactive(war);
                                }
                            }
                        }
                    }
                }

                if (attack.getImprovements_destroyed() > 0) {
                    dirtyCities.add(attack);
                }
                dbAttacks.add(attack);
            }

        }

        saveWars(warsToSave, true);
        if (runAlerts && dirtyCities.size() > 0) {
            for (AbstractCursor attack : dirtyCities) {
                // check improvements and modify city
                Set<Integer> cityIds = attack.getCityIdsDamaged();
                for (int id : cityIds) {
                    Locutus.imp().getNationDB().markCityDirty(attack.getDefender_id(), id, attack.getDate());
                }
            }
        }

        { // add to db
            saveAttacks(newAttacks, eventConsumer);
        }

        if (runAlerts && eventConsumer != null) {

            for (AbstractCursor attack : newAttacks) {
                activeWars.processAttackChange(attack, eventConsumer);
            }

            WarUpdateProcessor.processWars(warsToProcess, eventConsumer);

            long start2 = System.currentTimeMillis();
            long diff = System.currentTimeMillis() - start2;

            if (diff > 200) {
                System.err.println("Took too long to update blockades (" + diff + "ms)");
            }

            for (AbstractCursor attack : dbAttacks) {
                eventConsumer.accept(new AttackEvent(attack));
            }
        }
        return true;
    }

    public void loadAttacks(boolean loadInactive, boolean loadActive) {
        if (!loadActive) return;
        String whereClause;
        if (!loadInactive) {
            long dateCutoff = TimeUtil.getTimeFromTurn(TimeUtil.getTurn() - 120);
            List<Integer> warIds = new IntArrayList();
            synchronized (warsById) {
                for (DBWar war : warsById) {
                    if (war.getDate() < dateCutoff) continue;
                    warIds.add(war.getWarId());
                }
            }
            Collections.sort(warIds);
            whereClause = " WHERE war_id in " + StringMan.getString(warIds);
        } else {
            whereClause = "";
        }
        String query = "SELECT war_id, data FROM `attacks3` " + whereClause + " ORDER BY `id` ASC";

        IntArrayList warIds = new IntArrayList();
        List<byte[]> attacks = new ObjectArrayList<>();
        Int2IntOpenHashMap numAttacksByWarId = new Int2IntOpenHashMap();

        try (PreparedStatement stmt = prepareQuery(query)) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int warId = rs.getInt(1);
                    if (!warsById.contains(new DBWar.DBWarKey(warId))) continue;
                    byte[] bytes = applyAdminFix(warId, rs.getBytes(2));
                    warIds.add(warId);
                    attacks.add(bytes);
                    numAttacksByWarId.addTo(warId, 1);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        for (int i = 0; i < attacks.size(); i++) {
            byte[] data = attacks.get(i);
            int warId = warIds.getInt(i);
            int size = numAttacksByWarId.get(warId);
            attacksByWarId2.computeIfAbsent(warId, f -> new ObjectArrayList<>(size)).add(data);
        }
        Logg.text("Loaded " + attacks.size() + " attacks " + attacksByWarId2.containsKey(2114621));
    }

    private byte[] addLoot(int warId, byte[] data, double[] loot) {
        DBWar war = getWar(warId);
        if (war != null) {
            boolean modified = false;
            AbstractCursor attack = attackCursorFactory.load(war, data, true);
            if (attack instanceof ALootCursor aLoot && aLoot.looted != null) {
                aLoot.looted = ResourceType.add(aLoot.looted, loot);
                modified = true;
            } else if (attack instanceof VictoryCursor victory && victory.looted != null) {
                victory.looted = ResourceType.add(victory.looted, loot);
                modified = true;
            } else {
                Logg.text("Unable to add loot to attack in war " + warId + " - " + attack.getClass().getSimpleName() + ": " + ResourceType.toString(loot));
            }
            if (modified) {
                Logg.text("Added loot to attack in war " + warId + ": " + ResourceType.toString(loot));
                return attackCursorFactory.toBytes(attack);
            }
            if (!modified) {
                Logg.text("Unable to find attack in war " + warId);
            }
        } else {
            Logg.text("Unable to find war " + warId);
        }
        return data;
    }

    private final byte[] applyAdminFix(int warId, byte[] data) {
        switch (warId) {
            case 2114621: {
                int attackId = attackCursorFactory.getId(data);
                if (attackId == 24412523) {
                    double[] addLoot = ResourceType.getBuffer();
                    addLoot[ResourceType.MONEY.ordinal()] = 14_778_120_852.20;
                    addLoot[ResourceType.FOOD.ordinal()] = 11_159_408.43;
                    addLoot[ResourceType.COAL.ordinal()] = 110_354.05;
                    addLoot[ResourceType.OIL.ordinal()] = 122_216.34;
                    addLoot[ResourceType.URANIUM.ordinal()] = 191_876.88;
                    addLoot[ResourceType.LEAD.ordinal()] = 124_750.57;
                    addLoot[ResourceType.IRON.ordinal()] = 143_982.64;
                    addLoot[ResourceType.BAUXITE.ordinal()] = 117_833.70;
                    addLoot[ResourceType.GASOLINE.ordinal()] = 172_996.36;
                    addLoot[ResourceType.MUNITIONS.ordinal()] = 221_376.84;
                    addLoot[ResourceType.STEEL.ordinal()] = 231_940.75;
                    addLoot[ResourceType.ALUMINUM.ordinal()] = 359_765.25;
                    return addLoot(warId, data, addLoot);
                }
                }
            case 2114734: { // 24413762
                int attackId = attackCursorFactory.getId(data);
                if (attackId == 24413762) {
                    double[] addLoot = ResourceType.getBuffer();
                    addLoot[ResourceType.MONEY.ordinal()] = 738_906_042.61;
                    addLoot[ResourceType.FOOD.ordinal()] = 557_970.42;
                    addLoot[ResourceType.COAL.ordinal()] = 5_517.70;
                    addLoot[ResourceType.OIL.ordinal()] = 6_110.82;
                    addLoot[ResourceType.URANIUM.ordinal()] = 9_593.84;
                    addLoot[ResourceType.LEAD.ordinal()] = 6_237.53;
                    addLoot[ResourceType.IRON.ordinal()] = 7_199.13;
                    addLoot[ResourceType.BAUXITE.ordinal()] = 5_891.69;
                    addLoot[ResourceType.GASOLINE.ordinal()] = 8_649.82;
                    addLoot[ResourceType.MUNITIONS.ordinal()] = 11_068.84;
                    addLoot[ResourceType.STEEL.ordinal()] = 11_597.04;
                    addLoot[ResourceType.ALUMINUM.ordinal()] = 17_988.26;
                    return addLoot(warId, data, addLoot);
                }
            }
            case 2114618: {//24412909
                int attackId = attackCursorFactory.getId(data);
                if (attackId == 24412909) {
                    double[] addLoot = ResourceType.getBuffer();
                    addLoot[ResourceType.MONEY.ordinal()] = 701_960_740.48;
                    addLoot[ResourceType.FOOD.ordinal()] = 530_071.90;
                    addLoot[ResourceType.COAL.ordinal()] = 5_241.82;
                    addLoot[ResourceType.OIL.ordinal()] = 5_805.28;
                    addLoot[ResourceType.URANIUM.ordinal()] = 9_114.15;
                    addLoot[ResourceType.LEAD.ordinal()] = 5_925.65;
                    addLoot[ResourceType.IRON.ordinal()] = 6_839.18;
                    addLoot[ResourceType.BAUXITE.ordinal()] = 5_597.10;
                    addLoot[ResourceType.GASOLINE.ordinal()] = 8_217.33;
                    addLoot[ResourceType.MUNITIONS.ordinal()] = 10_515.40;
                    addLoot[ResourceType.STEEL.ordinal()] = 11_017.19;
                    addLoot[ResourceType.ALUMINUM.ordinal()] = 17_088.85;
                    return addLoot(warId, data, addLoot);
                }
            }
        }
        return data;
    }

//    public Map<Integer, List<AbstractCursor>> getAttacksByNationGroupWar2(int nationId, long startDate) {
//        Map<Integer, List<AbstractCursor>> result = new Int2ObjectOpenHashMap<>();
//        // attacks start within 5 days after a war
//        long cutoff = TimeUtil.getTimeFromTurn(TimeUtil.getTurn(startDate) + 60);
//        synchronized (warsByNationLock) {
//            Map<Integer, DBWar> warMap = warsByNationId.get(nationId);
//            if (warMap != null) {
//                for (DBWar war : warMap.values()) {
//                    if (war.date > cutoff) {
//                        continue;
//                    }
//                    List<AbstractCursor> list = getAttacksByWarId(war, new AttackCursorFactory(this));
//                    if (list != null && !list.isEmpty()) {
//                        result.put(war.warId, list);
//                    }
//                }
//            }
//        }
//        return result;
//    }

    public void iterateAttacks(long start, long end, Predicate<DBWar> ifWar, Consumer<AbstractCursor> consumer) {
        if (start > end) return;
        AttackCursorFactory factory = new AttackCursorFactory(this);
        Predicate<AbstractCursor> pretest = attack -> attack.getDate() >= start && attack.getDate() <= end;
        synchronized (warsById) {
            iterateAttacks(ArrayUtil.select(warsById,
                            war -> war.getDate() <= end && war.possibleEndDate() >= start && ifWar.test(war)),
                    (war, data) -> factory.loadWithPretest(war, data, true, pretest), consumer);
        }
    }

    public List<AbstractCursor> getAttacks(long start, long end, Predicate<DBWar> ifWar, Predicate<AbstractCursor> filter) {
        if (start > end) return Collections.emptyList();
        List<AbstractCursor> list = new ObjectArrayList<>();
        AttackCursorFactory factory = new AttackCursorFactory(this);

        Predicate<AbstractCursor> pretest = attack -> attack.getDate() >= start && attack.getDate() <= end;
        if (filter != null) {
            pretest = pretest.and(filter);
        }
        synchronized (warsById) {
            Predicate<AbstractCursor> finalPretest = pretest;
            iterateAttacks(ArrayUtil.select(warsById,
                            war -> war.getDate() <= end && war.possibleEndDate() >= start && ifWar.test(war)),
                    (war, data) -> factory.loadWithPretest(war, data, true, finalPretest), list::add);
        }
        return list;
    }

    public List<AbstractCursor> getAttacks(long start, Predicate<DBWar> ifWar, Predicate<AbstractCursor> filter) {
        return getAttacks(start, Long.MAX_VALUE, ifWar, filter);
    }

    public List<AbstractCursor> getAttacks(long start, Predicate<DBWar> ifWar) {
        return getAttacks(start, Long.MAX_VALUE, ifWar);
    }
    public List<AbstractCursor> getAttacks(long start, long end, Predicate<DBWar> ifWar) {
        ObjectArrayList<AbstractCursor> list = new ObjectArrayList<>();
        iterateAttacks(start, end, ifWar, list::add);
        return list;
    }

    private DBAttack createAttack(ResultSet rs) throws SQLException {
        DBAttack attack = new DBAttack();
        attack.setWar_attack_id(rs.getInt(1));
        attack.setDate(rs.getLong(2));
        attack.setWar_id(rs.getInt(3));
        attack.setAttacker_nation_id(rs.getInt(4));
        attack.setDefender_nation_id(rs.getInt(5));
        attack.setAttack_type(AttackType.values[rs.getInt(6)]);
        attack.setSuccess(rs.getInt(8));

        if (attack.getSuccess() > 0 || attack.getAttack_type() == AttackType.VICTORY)
        {
            attack.setLooted(attack.getDefender_id());

            attack.setInfra_destroyed(getLongDef0(rs, 15) * 0.01);
            if (attack.getInfra_destroyed() > 0) {
                attack.setImprovements_destroyed(getIntDef0(rs, 16));
                attack.setCity_infra_before(getLongDef0(rs, 21) * 0.01);
                attack.setInfra_destroyed_value(getLongDef0(rs, 22) * 0.01);
            }
        }

        switch (attack.getAttack_type()) {
            case GROUND:
                if (attack.getSuccess() < 0) break;
            case VICTORY:
            case A_LOOT:
                attack.setMoney_looted(getLongDef0(rs, 17) * 0.01);
        }

        if (attack.getAttack_type() == AttackType.VICTORY || attack.getAttack_type() == AttackType.A_LOOT) {
            attack.setLooted(rs.getInt(18));
            byte[] lootBytes = getBytes(rs, 19);
            if (lootBytes != null) {
                attack.setLoot(ArrayUtil.toDoubleArray(lootBytes));
                attack.setLootPercent(rs.getInt(20) * 0.0001);
            }
        }

        switch (attack.getAttack_type()) {
            case VICTORY:
            case FORTIFY:
            case A_LOOT:
            case PEACE:
                break;
            default:
                attack.setAtt_gas_used(getLongDef0(rs, 23) * 0.01);
                attack.setAtt_mun_used(getLongDef0(rs, 24) * 0.01);
                attack.setDef_gas_used(getLongDef0(rs, 25) * 0.01);
                attack.setDef_mun_used(getLongDef0(rs, 26) * 0.01);
            case MISSILE:
            case NUKE:
                attack.setAttcas1(rs.getInt(9));
                attack.setAttcas2(rs.getInt(10));
                attack.setDefcas1(rs.getInt(11));
                attack.setDefcas2(rs.getInt(12));
                attack.setDefcas3(rs.getInt(13));
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
        LootEntry lootInfo = alliance.getLoot();
        if (lootInfo == null) return Collections.emptyMap();

        double[] allianceLoot = lootInfo.getTotal_rss();

        double aaScore = alliance.getScore();
        if (aaScore == 0) return Collections.emptyMap();

        double ratio = ((nationScore * 10000) / aaScore) / 2d;
        double percent = Math.min(Math.min(ratio, 10000) / 30000, 0.33);
        Map<ResourceType, Double> yourLoot = ResourceType.resourcesToMap(allianceLoot);
        yourLoot = PW.multiply(yourLoot, percent);
        return yourLoot;
    }

    public Map<Integer, Map.Entry<Long, double[]>> getNationLootFromAttacksLegacy(long time) {
        Map<Integer, Map.Entry<Long, double[]>> nationLoot = new ConcurrentHashMap<>();

        AttackCursorFactory factory = new AttackCursorFactory(this);
        // iterate all victory attacks
        iterateAttacks(getWarsSince(time).values(), type -> type == AttackType.VICTORY, null, attack -> {
            int looted = attack.getDefender_id();
            Map.Entry<Long, double[]> existing = nationLoot.get(looted);
            if (existing == null || existing.getKey() < attack.getDate()) {
                double[] loot = attack.getLoot();
                if (loot == null) loot = ResourceType.getBuffer();
                double factor = 1 / attack.getLootPercent();
                double[] lootCopy = loot.clone();
                for (int j = 0; j < lootCopy.length; j++) {
                    lootCopy[j] = lootCopy[j] * factor - lootCopy[j];
                }
                nationLoot.put(looted, new AbstractMap.SimpleEntry<>(attack.getDate(), lootCopy));
            }
        });
        return nationLoot;
    }

    public List<AbstractCursor> getAttacks(int nation_id) {
        Set<DBWar> wars = getWarsByNation(nation_id);
        return getAttacksByWars(wars);
    }

    public List<AbstractCursor> getAttacks(int nation_id, long cuttoffMs) {
        return getAttacks(nation_id, cuttoffMs, Long.MAX_VALUE);
    }

    public List<AbstractCursor> getAttacks(int nation_id, long start, long end) {
        if (start <= 0 && end == Long.MAX_VALUE) return getAttacks(nation_id);

        Set<DBWar> wars = getWarsByNation(nation_id);
        // remove wars outside the date
        long startWithExpire = TimeUtil.getTimeFromTurn(TimeUtil.getTurn(start) - 60);
        wars.removeIf(f -> f.getDate() < startWithExpire || f.getDate() > end);
        List<AbstractCursor> attacks = getAttacksByWars(wars, start, end);
        return attacks;
    }

    public List<AbstractCursor> getAttacks(long cuttoffMs, AttackType type) {
        return queryAttacks().withAllWars().afterDate(cuttoffMs).withType(type).getList();
    }

    public List<AbstractCursor> getAttacksByWars(Collection<DBWar> wars) {
        return getAttacksByWars(wars, 0, Long.MAX_VALUE);
    }
    public List<AbstractCursor> getAttacksByWars(Collection<DBWar> wars, long start, long end) {
        AttackQuery query = queryAttacks().withWars(wars);
        return ((start != 0 || end != Long.MAX_VALUE) ? query.between(start, end) : query).getList();
    }

    public int countWarsByNation(int nation_id, long date, Long endDate) {
        if (endDate == null || endDate == Long.MAX_VALUE) return countWarsByNation(nation_id, date);
        int result;
        synchronized (warsByNationLock) {
            Object wars = warsByNationId.get(nation_id);
            result = ArrayUtil.countElements(DBWar.class, wars, f -> f.getDate() >= date && f.getDate() <= endDate);
        }
        return result;
    }

    public int countWarsByNation(int nation_id, long date) {
        if (date == 0) {
            int result;
            synchronized (warsByNationLock) {
                Object wars = warsByNationId.get(nation_id);
                result = ArrayUtil.countElements(DBWar.class, wars);
            }
            return result;
        }
        int result;
        synchronized (warsByNationLock) {
            Object wars = warsByNationId.get(nation_id);
            if (wars == null) return 0;
            result = ArrayUtil.countElements(DBWar.class, wars, war -> war.getDate() > date);
        }
        return result;
    }

    public int countOffWarsByNation(int nation_id, long startDate, long endDate) {
        int result;
        synchronized (warsByNationLock) {
            Object wars = warsByNationId.get(nation_id);
            if (wars == null) return 0;
            result = ArrayUtil.countElements(DBWar.class, wars, war -> war.getAttacker_id() == nation_id && war.getDate() > startDate && war.getDate() < endDate);
        }
        return result;
    }

    public int countDefWarsByNation(int nation_id, long startDate, long endDate) {
        int result;
        synchronized (warsByNationLock) {
            Object wars = warsByNationId.get(nation_id);
            if (wars == null) return 0;
            result = ArrayUtil.countElements(DBWar.class, wars, war -> war.getDefender_id() == nation_id && war.getDate() > startDate && war.getDate() < endDate);
        }
        return result;
    }

    public int countWarsByAlliance(int alliance_id, long date) {
        if (date == 0) {
            synchronized (warsByAllianceId) {
                Object wars = warsByAllianceId.get(alliance_id);
                return ArrayUtil.countElements(DBWar.class, wars);
            }
        }
        synchronized (warsByAllianceId) {
            Object wars = warsByAllianceId.get(alliance_id);
            if (wars == null) return 0;
            return ArrayUtil.countElements(DBWar.class, wars, war -> war.getDate() > date);
        }
    }

    public AttackQuery queryAttacks() {
        return new AttackQuery(this);
    }

    public void syncBlockades() {
        activeWars.syncBlockades();
    }
}
