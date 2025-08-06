package link.locutus.discord.db;

import com.google.api.client.util.Base64;
import com.google.api.client.util.Sets;
import com.google.common.base.Predicates;
import com.politicsandwar.graphql.model.ApiKeyDetails;
import com.politicsandwar.graphql.model.Nation;
import com.politicsandwar.graphql.model.NationsQueryRequest;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.Logg;
import link.locutus.discord.apiv1.core.ApiKeyPool;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv3.PoliticsAndWarV3;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DiscordBan;
import link.locutus.discord.db.entities.DiscordMeta;
import link.locutus.discord.db.handlers.SyncableDatabase;
import link.locutus.discord.event.Event;
import link.locutus.discord.event.nation.NationRegisterEvent;
import link.locutus.discord.pnw.PNWUser;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.offshore.EncryptionUtil;
import link.locutus.discord.util.scheduler.KeyValue;
import link.locutus.discord.util.scheduler.ThrowingBiConsumer;
import link.locutus.discord.util.scheduler.ThrowingConsumer;
import link.locutus.discord.util.task.multi.MultiResult;
import link.locutus.discord.util.task.multi.NetworkRow;
import link.locutus.discord.util.task.multi.SameNetworkTrade;
import net.dv8tion.jda.api.entities.User;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;

public class DiscordDB extends DBMainV2 implements SyncableDatabase {

    public DiscordDB() throws SQLException, ClassNotFoundException {
        this("locutus");
    }
    public DiscordDB(String name) throws SQLException, ClassNotFoundException {
        super(name);
        if (tableExists("credentials")) migrateCredentials();
    }

    @Override
    public Set<String> getTablesAllowingDeletion() {
        // all tables in getTablesToSync
        return Set.of("USERS", "CREDENTIALS2", "API_KEYS3");
    }

    @Override
    public Map<String, String> getTablesToSync() {
        return Map.of(
                "USERS", "date_updated",
                "CREDENTIALS2", "date_updated",
                "API_KEYS3", "date_updated"
        );
    }

    @Override
    public void createTables() {
        executeStmt("CREATE TABLE IF NOT EXISTS `USERS` (`nation_id` INT NOT NULL, `discord_id` BIGINT NOT NULL, `discord_name` VARCHAR, `date_updated` BIGINT NOT NULL, PRIMARY KEY(discord_id))");
        executeStmt("CREATE TABLE IF NOT EXISTS `UUIDS` (`nation_id` INT NOT NULL, `uuid` BLOB NOT NULL, `date` BIGINT NOT NULL, PRIMARY KEY(nation_id, uuid, date))");
        executeStmt("CREATE TABLE IF NOT EXISTS `CREDENTIALS2` (`discordid` BIGINT NOT NULL PRIMARY KEY, `user` VARCHAR NOT NULL, `password` VARCHAR NOT NULL, `salt` VARCHAR NOT NULL, `date_updated` BIGINT NOT NULL)");
        executeStmt("CREATE TABLE IF NOT EXISTS `VERIFIED` (`nation_id` INT NOT NULL PRIMARY KEY)");
        executeStmt("CREATE TABLE IF NOT EXISTS `DISCORD_META` (`key` BIGINT NOT NULL, `id` BIGINT NOT NULL, `value` BLOB NOT NULL, PRIMARY KEY(`key`, `id`))");
        executeStmt("CREATE TABLE IF NOT EXISTS `API_KEYS3`(`nation_id` INT NOT NULL PRIMARY KEY, `api_key` BLOB, `bot_key` BLOB, `date_updated` BIGINT NOT NULL)");
        executeStmt("CREATE TABLE IF NOT EXISTS `DISCORD_BANS`(`user` BIGINT NOT NULL, `server` BIGINT NOT NULL, `date` BIGINT NOT NULL, `reason` VARCHAR, PRIMARY KEY(`user`, `server`))");

        executeStmt("CREATE TABLE IF NOT EXISTS `NetworkRow2` (`id1` INTEGER NOT NULL, `id2` INTEGER NOT NULL, `lastAccessFromSharedIP` BIGINT NOT NULL, `numberOfSharedIPs` INTEGER NOT NULL, `lastActiveMs` BIGINT NOT NULL, `allianceId` INTEGER NOT NULL, `dateCreated` BIGINT NOT NULL, PRIMARY KEY (`id1`, `id2`))");
        executeStmt("DROP TABLE IF EXISTS `NetworkRow`");
//        executeStmt("DROP TABLE IF EXISTS `MultiReportLastUpdated`"); // todo remove
        executeStmt("CREATE TABLE IF NOT EXISTS `SameNetworkTrade`(`sellingNation` INTEGER NOT NULL, `buyingNation` INTEGER NOT NULL, `dateOffered` BIGINT NOT NULL, `resource` INTEGER NOT NULL, `amount` INTEGER NOT NULL, `ppu` INTEGER NOT NULL, PRIMARY KEY (`sellingNation`, `buyingNation`, `dateOffered`, `resource`, `amount`, `ppu`))");
        executeStmt("CREATE INDEX IF NOT EXISTS idx_sellingNation ON SameNetworkTrade(sellingNation)");
        executeStmt("CREATE INDEX IF NOT EXISTS idx_buyingNation ON SameNetworkTrade(buyingNation)");

//        executeStmt("DELETE FROM `SameNetworkTrade` WHERE `dateOffered` <= " + Integer.MAX_VALUE, true);

        // last updated by id MultiReportLastUpdated int id, long date
        executeStmt("CREATE TABLE IF NOT EXISTS `MultiReportLastUpdated`(`id` INTEGER PRIMARY KEY, `date` INTEGER NOT NULL)");

        for (String table : new String[]{"USERS", "CREDENTIALS2", "API_KEYS3"}) {
            if (getTableColumns(table).stream().noneMatch(c -> c.equalsIgnoreCase("date_updated"))) {
                executeStmt("ALTER TABLE " + table + " ADD COLUMN date_updated BIGINT NOT NULL DEFAULT " + System.currentTimeMillis(), true);
            }
        }

        setupApiKeys();

        createDeletionsTables();
    }

    public void addMultiReportLastUpdated(int id, long date) {
        update("INSERT OR REPLACE INTO `MultiReportLastUpdated`(`id`, `date`) VALUES(?, ?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, id);
            stmt.setLong(2, date);
        });
    }

    public long getMultiReportLastUpdated(int id) {
        try (PreparedStatement stmt = prepareQuery("select * FROM MultiReportLastUpdated WHERE id = ?")) {
            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("date");
                }
            }
            return 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }

    public Map<Integer, Long> getMultiReportLastUpdated(Predicate<Integer> allowId) {
        try (PreparedStatement stmt = prepareQuery("select * FROM MultiReportLastUpdated")) {
            try (ResultSet rs = stmt.executeQuery()) {
                Map<Integer, Long> map = new Int2LongOpenHashMap();
                while (rs.next()) {
                    int id = rs.getInt("id");
                    if (allowId.test(id)) {
                        map.put(id, rs.getLong("date"));
                    }
                }
                return map;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public Map<Integer, NetworkRow> getNetworkRows(int nationId) {
        Map<Integer, NetworkRow> map = new Int2ObjectOpenHashMap<>();
        try (PreparedStatement stmt = prepareQuery("select * FROM NetworkRow2 WHERE id1 = ?")) {
            stmt.setInt(1, nationId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    NetworkRow row = new NetworkRow(
                            rs.getInt("id2"),
                            rs.getLong("lastAccessFromSharedIP"),
                            rs.getInt("numberOfSharedIPs"),
                            rs.getLong("lastActiveMs"),
                            rs.getInt("allianceId"),
                            rs.getLong("dateCreated")
                    );
                    map.put(row.id, row);
                }
            }
            return map;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public List<SameNetworkTrade> getSameNetworkTrades(int nationId) {
        Set<SameNetworkTrade> list = new ObjectLinkedOpenHashSet<>();
        try (PreparedStatement stmt = prepareQuery("select * FROM SameNetworkTrade WHERE sellingNation = ? OR buyingNation = ?")) {
            stmt.setInt(1, nationId);
            stmt.setInt(2, nationId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    SameNetworkTrade trade = new SameNetworkTrade(
                            rs.getInt("sellingNation"),
                            rs.getInt("buyingNation"),
                            rs.getLong("dateOffered"),
                            ResourceType.values[rs.getInt("resource")],
                            rs.getInt("amount"),
                            rs.getInt("ppu")
                    );
                    list.add(trade);
                }
            }
            return new ObjectArrayList<>(list);
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public void addNetworks(int nationId, List<NetworkRow> data) {
        String query = "INSERT OR REPLACE INTO `NetworkRow2`(`id1`, `id2`, `lastAccessFromSharedIP`, `numberOfSharedIPs`, `lastActiveMs`, `allianceId`, `dateCreated`) VALUES (?, ?, ?, ?, ?, ?, ?)";
        executeBatch(data, query, new ThrowingBiConsumer<NetworkRow, PreparedStatement>() {
            @Override
            public void acceptThrows(NetworkRow row, PreparedStatement stmt) throws Exception {
                stmt.setInt(1, nationId);
                stmt.setInt(2, row.id);
                stmt.setLong(3, row.lastAccessFromSharedIP);
                stmt.setInt(4, row.numberOfSharedIPs);
                stmt.setLong(5, row.lastActiveMs);
                stmt.setInt(6, row.allianceId);
                stmt.setLong(7, row.dateCreated);
            }
        });
    }

    public void addSameNetworkTrades(List<SameNetworkTrade> data) {
        String query = "INSERT OR REPLACE INTO `SameNetworkTrade`(`sellingNation`, `buyingNation`, `dateOffered`, `resource`, `amount`, `ppu`) VALUES (?, ?, ?, ?, ?, ?)";
        executeBatch(data, query, new ThrowingBiConsumer<SameNetworkTrade, PreparedStatement>() {
            @Override
            public void acceptThrows(SameNetworkTrade trade, PreparedStatement stmt) throws Exception {
                stmt.setInt(1, trade.sellingNation);
                stmt.setInt(2, trade.buyingNation);
                stmt.setLong(3, trade.dateOffered);
                stmt.setInt(4, trade.resource.ordinal());
                stmt.setInt(5, trade.amount);
                stmt.setInt(6, trade.ppu);
            }
        });
    }

    public MultiResult getMultiResult(int nationId) {
        long lastUpdated = getMultiReportLastUpdated(nationId);
        if (lastUpdated == 0) return new MultiResult(nationId);
        Map<Integer, NetworkRow> networkRows = getNetworkRows(nationId);
        List<SameNetworkTrade> sameNetworkTrades = getSameNetworkTrades(nationId);
        return new MultiResult(nationId, networkRows, sameNetworkTrades).setDateFetched(lastUpdated);
    }

    public List<DiscordBan> getBans(long userId) {
        try (PreparedStatement stmt = prepareQuery("SELECT * FROM `DISCORD_BANS` WHERE `user` = ?")) {
            stmt.setLong(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                List<DiscordBan> bans = new ArrayList<>();
                while (rs.next()) {
                    bans.add(new DiscordBan(rs));
                }
                return bans;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<DiscordBan> getBans() {
        try (PreparedStatement stmt = prepareQuery("SELECT * FROM `DISCORD_BANS`")) {
            try (ResultSet rs = stmt.executeQuery()) {
                List<DiscordBan> bans = new ArrayList<>();
                while (rs.next()) {
                    bans.add(new DiscordBan(rs));
                }
                return bans;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void addBans(List<DiscordBan> bans) {
        String query = "INSERT OR REPLACE INTO `DISCORD_BANS`(`user`, `server`, `date`, `reason`) VALUES (?, ?, ?, ?)";
        executeBatch(bans, query, new ThrowingBiConsumer<DiscordBan, PreparedStatement>() {
            @Override
            public void acceptThrows(DiscordBan ban, PreparedStatement stmt) throws Exception {
                stmt.setLong(1, ban.user);
                stmt.setLong(2, ban.server);
                stmt.setLong(3, ban.date);
                stmt.setString(4, ban.reason);
            }
        });
    }

    public void migrateKeys() throws SQLException {
        // from API_KEYS2 `api_key` BIGINT, `bot_key` BIGINT
        // to API_KEYS3 `api_key` BLOB, `bot_key` BLOB
        if (!tableExists("API_KEYS2")) return;
        // iterate rows
        try (PreparedStatement stmt = prepareQuery("select * FROM API_KEYS2")) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Long keyId = getLong(rs, "api_key");
                    if (keyId == null) continue;

                    String key = String.format("%014X", keyId);
                    Long botKeyId = getLong(rs, "bot_key");
                    String botKey = botKeyId == null ? null : String.format("%016X", botKeyId);

                    int nationId = rs.getInt("nation_id");

                    byte[] keyBytes = SQLUtil.hexStringToByteArray(key);
                    byte[] botKeyBytes = botKey == null ? null : SQLUtil.hexStringToByteArray(botKey);

                    // insert into API_KEYS3
                    try (PreparedStatement stmt2 = prepareQuery("INSERT OR REPLACE INTO API_KEYS3 VALUES (?, ?, ?, ?)")) {
                        stmt2.setInt(1, nationId);
                        stmt2.setBytes(2, keyBytes);
                        stmt2.setBytes(3, botKeyBytes);
                        stmt2.setLong(4, System.currentTimeMillis());
                        stmt2.executeUpdate();
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void setupApiKeys() {
        initInfo();
    }

    public void addApiKey(int nationId, String key) {
        byte[] keyId = SQLUtil.hexStringToByteArray(key);
        update("INSERT OR REPLACE INTO `API_KEYS3`(`nation_id`, `api_key`, `date_updated`) VALUES(?, ?, ?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, nationId);
            stmt.setBytes(2, keyId);
            stmt.setLong(3, System.currentTimeMillis());
        });
    }

    public void addBotKey(int nationId, String key) {
        byte[] keyId = SQLUtil.hexStringToByteArray(key);
        update("INSERT OR REPLACE INTO `API_KEYS3`(`nation_id`, `bot_key`, `date_updated`) VALUES(?, ?, ?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, nationId);
            stmt.setBytes(2, keyId);
            stmt.setLong(3, System.currentTimeMillis());
        });
    }

    public void addApiKey(int nationId, String key, String botKey) {
        if (botKey == null) {
            addApiKey(nationId, key);
            return;
        }
        byte[] keyId = SQLUtil.hexStringToByteArray(key);
        byte[] botId = SQLUtil.hexStringToByteArray(botKey);
        update("INSERT OR REPLACE INTO `API_KEYS3`(`nation_id`, `api_key`, `bot_key`, `date_updated`) VALUES(?, ?, ?, ?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, nationId);
            stmt.setBytes(2, keyId);
            stmt.setBytes(3, botId);
            stmt.setLong(4, System.currentTimeMillis());
        });
    }

    public ApiKeyPool.ApiKey getApiKey(int nationId) {
        if (nationId == Settings.INSTANCE.NATION_ID && !Settings.INSTANCE.API_KEY_PRIMARY.isEmpty()) {
            return new ApiKeyPool.ApiKey(Settings.INSTANCE.NATION_ID, Settings.INSTANCE.API_KEY_PRIMARY, Settings.INSTANCE.ACCESS_KEY);
        }
        try (PreparedStatement stmt = prepareQuery("select * FROM API_KEYS3 WHERE nation_id = ?")) {
            stmt.setInt(1, nationId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    byte[] keyId = getBytes(rs, "api_key");
                    if (keyId == null) return null;

                    byte[] botKeyId = getBytes(rs, "bot_key");
                    // byte[] to hex string

                    String key = SQLUtil.byteArrayToHexString(keyId);
                    String botKey = botKeyId == null ? null : SQLUtil.byteArrayToHexString(botKeyId);
                    return new ApiKeyPool.ApiKey(nationId, key, botKey);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void deleteApiKeyPairByNation(int nationId) {
        synchronized (this) {
            logDeletion("API_KEYS3", System.currentTimeMillis(), "nation_id", nationId);
            update("DELETE FROM `API_KEYS3` WHERE nation_id = ? ", (ThrowingConsumer<PreparedStatement>) stmt -> {
                stmt.setLong(1, nationId);
            });
        }
    }

    public void deleteApiKey(String key) {
        update("UPDATE API_KEYS3 SET api_key = NULL, `date_updated` = ? WHERE api_key = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setLong(1, System.currentTimeMillis());
            stmt.setBytes(2, SQLUtil.hexStringToByteArray(key));

        });
    }

    public void deleteBotKey(String key) {
        update("UPDATE API_KEYS3 SET bot_key = NULL, `date_updated` = ? WHERE bot_key = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setLong(1, System.currentTimeMillis());
            stmt.setBytes(2, SQLUtil.hexStringToByteArray(key));

        });
    }

    public Integer getNationFromApiKey(String key) {
        return getNationFromApiKey(key, true);
    }
    public Integer getNationFromApiKey(String key, boolean allowFetch) {
        if (Settings.INSTANCE.API_KEY_PRIMARY.equalsIgnoreCase(key) && Settings.INSTANCE.NATION_ID > 0) {
            return Settings.INSTANCE.NATION_ID;
        }
        try (PreparedStatement stmt = prepareQuery("select * FROM API_KEYS3 WHERE api_key = ?")) {
            byte[] keyId = SQLUtil.hexStringToByteArray(key);
            stmt.setBytes(1, keyId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("nation_id");
                    if (id > 0) return id;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } catch (Throwable e) {
            e.printStackTrace();
            throw new RuntimeException(StringMan.stripApiKey(e.getMessage()).toLowerCase().replace(key.toLowerCase(), "<redacted>"));
        }
        if (allowFetch) {
            ApiKeyDetails keyStats = new PoliticsAndWarV3(ApiKeyPool.builder().addKeyUnsafe(key).build()).getApiKeyStats();
            if (keyStats != null && keyStats.getNation() != null && keyStats.getNation().getId() != null) {
                if (keyStats.getNation().getId() > 0) {
                    int natId = keyStats.getNation().getId();
                    addApiKey(natId, keyStats.getKey());
                    return natId;
                } else {
                    Logg.info("No nation found for api key. Invalid nation id " + keyStats);
                }
            }
        }
        return null;
    }

    private Map<DiscordMeta, Map<Long, byte[]>> info;



    public ByteBuffer getInfo(DiscordMeta meta, long id) {
        if (info == null) {
            initInfo();
        }
        byte[] bytes = info.getOrDefault(meta, Collections.emptyMap()).get(id);
        return bytes == null ? null : ByteBuffer.wrap(bytes);
    }

    public void setInfo(DiscordMeta meta, long id, byte[] value) {
        checkNotNull(meta);
        checkNotNull(value);
        initInfo();
        synchronized (this) {
            update("INSERT OR REPLACE INTO `DISCORD_META`(`key`, `id`, `value`) VALUES(?, ?, ?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
                stmt.setInt(1, meta.ordinal());
                stmt.setLong(2, id);
                stmt.setBytes(3, value);

            });
            info.computeIfAbsent(meta, f -> new Long2ObjectOpenHashMap<>()).put(id, value);
        }
    }

    public void deleteInfo(DiscordMeta meta) {
        update("DELETE FROM DISCORD_META WHERE key = ?",
                (ThrowingConsumer<PreparedStatement>) stmt -> stmt.setInt(1, meta.ordinal()));
    }

    private synchronized void initInfo() {
//        for (DiscordMeta meta : DiscordMeta.values()) {
//            if (meta.getDeleteBefore() > 0) {
//                String update = "DELETE FROM DISCORD_META WHERE key = ? and id < ?";
//                update(update, new ThrowingConsumer<PreparedStatement>() {
//                    @Override
//                    public void acceptThrows(PreparedStatement stmt) throws Exception {
//                        stmt.setInt(1, meta.ordinal());
//                        stmt.setLong(2, meta.getDeleteBefore());
//                    }
//                });
//            }
//        }

        if (info == null) {
            synchronized (this) {
                if (info == null) {
                    info = new ConcurrentHashMap<>();

                    try (PreparedStatement stmt = prepareQuery("select * FROM DISCORD_META")) {
                        try (ResultSet rs = stmt.executeQuery()) {
                            while (rs.next()) {
                                int key = rs.getInt("key");
                                DiscordMeta meta = DiscordMeta.values[key];
                                long id = rs.getLong("id");
                                byte[] data = rs.getBytes("value");

                                info.computeIfAbsent(meta, f -> new Long2ObjectOpenHashMap<>()).put(id, data);
                            }
                        }
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    public void addVerified(int nationId) {
        update("INSERT OR IGNORE INTO `VERIFIED` (`nation_id`) VALUES(?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, nationId);
        });
    }

    public void logout(long nationId) {
        synchronized (this) {
            logDeletion("CREDENTIALS2", System.currentTimeMillis(), "discordid", nationId);
            update("DELETE FROM `CREDENTIALS2` where `discordid` = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
                stmt.setLong(1, nationId);
            });
        }
    }

    private void migrateCredentials() {
        Set<Long> ids = new LongOpenHashSet();
        String query = getDb().selectBuilder("credentials").select("discordId").buildQuery();
        query(query, stmt -> {},
                (ThrowingConsumer<ResultSet>) r -> ids.add(r.getLong(1)));
        for (long discordId : ids) {
            Map.Entry<String, String> userPass = getUserPass2(discordId, "credentials", EncryptionUtil.Algorithm.LEGACY);
            addUserPass2(discordId, userPass.getKey(), userPass.getValue());
        }

        executeStmt("DROP TABLE CREDENTIALS");
    }

    public void addUserPass2(long nationId, String user, String password) {
        try {
            String secretStr = Settings.INSTANCE.CLIENT_SECRET;
            if (secretStr == null || secretStr.isEmpty()) secretStr = Settings.INSTANCE.BOT_TOKEN;
            byte[] secret = secretStr.getBytes(StandardCharsets.ISO_8859_1);
            byte[] salt = EncryptionUtil.generateKey();

            byte[] userEnc = EncryptionUtil.encrypt2(EncryptionUtil.encrypt2(user.getBytes(StandardCharsets.ISO_8859_1), secret), salt);
            byte[] passEnc = EncryptionUtil.encrypt2(EncryptionUtil.encrypt2(password.getBytes(StandardCharsets.ISO_8859_1), secret), salt);

            update("INSERT OR REPLACE INTO `CREDENTIALS2` (`discordid`, `user`, `password`, `salt`, `date_updated`) VALUES(?, ?, ?, ?, ?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
                stmt.setLong(1, nationId);
                stmt.setString(2, Base64.encodeBase64String(userEnc));
                stmt.setString(3, Base64.encodeBase64String(passEnc));
                stmt.setString(4, Base64.encodeBase64String(salt));
                stmt.setLong(5, System.currentTimeMillis());
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Map.Entry<String, String> getUserPass2(long nationId) {
        return getUserPass2(nationId, "credentials2", EncryptionUtil.Algorithm.DEFAULT);
    }

    public Map.Entry<String, String> getUserPass2(long nationId, String table, EncryptionUtil.Algorithm algorithm) {
        if ((nationId == Settings.INSTANCE.ADMIN_USER_ID || nationId == Settings.INSTANCE.NATION_ID) && nationId > 0 && !Settings.INSTANCE.USERNAME.isEmpty() && !Settings.INSTANCE.PASSWORD.isEmpty()) {
            return KeyValue.of(Settings.INSTANCE.USERNAME, Settings.INSTANCE.PASSWORD);
        }
        try (PreparedStatement stmt = prepareQuery("select * FROM " + table + " WHERE `discordid` = ?")) {
            stmt.setLong(1, nationId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String secretStr = Settings.INSTANCE.CLIENT_SECRET;
                    if (secretStr == null || secretStr.isEmpty()) secretStr = Settings.INSTANCE.BOT_TOKEN;
                    byte[] secret = secretStr.getBytes(StandardCharsets.ISO_8859_1);
                    byte[] salt = Base64.decodeBase64(rs.getString("salt"));
                    byte[] userEnc = Base64.decodeBase64(rs.getString("user"));
                    byte[] passEnc = Base64.decodeBase64(rs.getString("password"));

                    byte[] userBytes = EncryptionUtil.decrypt2(EncryptionUtil.decrypt2(userEnc, salt, algorithm), secret, algorithm);
                    byte[] passBytes = EncryptionUtil.decrypt2(EncryptionUtil.decrypt2(passEnc, salt, algorithm), secret, algorithm);
                    String user = new String(userBytes, StandardCharsets.ISO_8859_1);
                    String pass = new String(passBytes, StandardCharsets.ISO_8859_1);

                    return new KeyValue<>(user, pass);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public void addUUID(int nationId, BigInteger uuid) {
        Map<BigInteger, List<Map.Entry<Long, Long>>> existing = getUuids(nationId);
        if (existing != null && !existing.isEmpty()) {
            Map.Entry<BigInteger, List<Map.Entry<Long, Long>>> first = existing.entrySet().iterator().next();
            Map.Entry<Long, Long> firstTime = first.getValue().get(0);
            if (first.getKey().equals(uuid)) return;
        }
        update("INSERT OR REPLACE INTO `UUIDS` (`nation_id`, `uuid`, `date`) VALUES(?, ?, ?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, nationId);
            stmt.setBytes(2, uuid.toByteArray());
            stmt.setLong(3, System.currentTimeMillis());
        });
    }

    public List<Map.Entry<Integer, Long>> getUuids(BigInteger uuid) {
        ArrayList<Map.Entry<Integer, Long>> list = new ArrayList<>();
        String query = "SELECT * FROM UUIDS WHERE uuid = ? ORDER BY date DESC";
        query(query, new ThrowingConsumer<PreparedStatement>() {
            @Override
            public void acceptThrows(PreparedStatement stmt) throws SQLException {
                stmt.setBytes(1, uuid.toByteArray());
            }
        }, new ThrowingConsumer<ResultSet>() {
            @Override
            public void acceptThrows(ResultSet rs) throws SQLException {
                int nationId = rs.getInt("nation_id");
                byte[] bytes = rs.getBytes("uuid");
                long date = rs.getLong("date");
                list.add(new KeyValue<>(nationId, date));
            }
        });
        return list;
    }

    public List<Map.Entry<Integer, Map.Entry<Long, BigInteger>>> getUuids() {
        ArrayList<Map.Entry<Integer, Map.Entry<Long, BigInteger>>> list = new ArrayList<>();
        try (PreparedStatement stmt = prepareQuery("select * FROM UUIDS ORDER BY date DESC")) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int nationId = rs.getInt("nation_id");
                    byte[] bytes = rs.getBytes("uuid");
                    long date = rs.getLong("date");
                    list.add(new KeyValue<>(nationId, new KeyValue<>(date, new BigInteger(bytes))));
                }
            }
            return list;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public Map<Integer, BigInteger> getLatestUidByNation() {
        Map<Integer, BigInteger> latestUuidsMap = new HashMap<>();
        String query = """
                SELECT t1.nation_id, t1.uuid
                FROM UUIDS t1
                INNER JOIN (
                    SELECT nation_id, MAX(date) AS max_date
                    FROM UUIDS
                    GROUP BY nation_id
                ) t2 ON t1.nation_id = t2.nation_id AND t1.date = t2.max_date;""";
        try (PreparedStatement preparedStatement = prepareQuery(query);
             ResultSet resultSet = preparedStatement.executeQuery()) {
            int i = 0;
            while (resultSet.next()) {
                i++;
                int nationId = resultSet.getInt("nation_id");
                byte[] uuidBytes = resultSet.getBytes("uuid");
                BigInteger uuidBigInteger = new BigInteger(uuidBytes);
                latestUuidsMap.put(nationId, uuidBigInteger);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
        return latestUuidsMap;
    }

    public Set<Integer> getVerified() {
            Set<Integer> set = new IntOpenHashSet();
        try (PreparedStatement stmt = prepareQuery("select * FROM VERIFIED")) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int nationId = rs.getInt("nation_id");
                    set.add(nationId);
                }
            }
            return set;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public boolean isVerified(int nationId) {
        try (PreparedStatement stmt = prepareQuery("select * FROM VERIFIED WHERE nation_id = ?")) {
            stmt.setInt(1, nationId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public Set<Integer> getVerified(Set<Integer> nationIds) {
        if (nationIds.isEmpty()) return Collections.emptySet();
        if (nationIds.size() == 1) {
            int nationId = nationIds.iterator().next();
            if (isVerified(nationId)) {
                return Collections.singleton(nationId);
            }
            return Collections.emptySet();
        }
        if (nationIds.size() > 1000) {
            return getVerified();
        }
        Set<Integer> set = new IntOpenHashSet();
        List<Integer> nationIdsSorted = new ArrayList<>(nationIds);
        Collections.sort(nationIdsSorted);
        try (PreparedStatement stmt = prepareQuery("select * FROM VERIFIED WHERE nation_id IN " + StringMan.getString(nationIdsSorted))) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int nationId = rs.getInt("nation_id");
                    set.add(nationId);
                }
            }
            return set;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public Map<BigInteger, Set<Integer>> getUuidMap() {
            Map<BigInteger, Set<Integer>> multis = new HashMap<>();
        try (PreparedStatement stmt = prepareQuery("select * FROM UUIDS")) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int nationId = rs.getInt("nation_id");
                    byte[] bytes = rs.getBytes("uuid");
                    BigInteger uuid = new BigInteger(bytes);
                    multis.computeIfAbsent(uuid, i -> Sets.newHashSet()).add(nationId);
                }
            }
            return multis;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public BigInteger getLatestUuid(int nationId) {
        Map<BigInteger, List<Map.Entry<Long, Long>>> uids = getUuids(nationId);
        for (Map.Entry<BigInteger, List<Map.Entry<Long, Long>>> uidEntry : uids.entrySet()) {
            for (Map.Entry<Long, Long> timeEntry : uidEntry.getValue()) {
                if (timeEntry.getValue() == Long.MAX_VALUE) return uidEntry.getKey();
            }
        }
        return null;
    }

    public void deleteUid(BigInteger uid) {
        update("DELETE FROM UUIDS WHERE uuid = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setBytes(1, uid.toByteArray());
        });
    }

    public Map<BigInteger, List<Map.Entry<Long, Long>>> getUuids(int nationId) {
        long end = Long.MAX_VALUE;

        Map<BigInteger, List<Map.Entry<Long, Long>>> result = new LinkedHashMap<>();

        try (PreparedStatement stmt = prepareQuery("select * FROM UUIDS WHERE nation_id = ? ORDER BY date DESC")) {
            stmt.setInt(1, nationId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    byte[] bytes = rs.getBytes("uuid");
                    long start = rs.getLong("date");

                    BigInteger uuid = new BigInteger(bytes);

                    List<Map.Entry<Long, Long>> list = result.computeIfAbsent(uuid, k -> new ArrayList<>());
                    list.add(new KeyValue<>(start, end));
                    end = start;
                }
            }
            return result;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public List<Map.Entry<BigInteger, Map.Entry<Long, Long>>> getUuidsByTime(int nationId) {
            long end = Long.MAX_VALUE;

            List<Map.Entry<BigInteger, Map.Entry<Long, Long>>> list = new ArrayList<>();

        try (PreparedStatement stmt = prepareQuery("select * FROM UUIDS WHERE nation_id = ? ORDER BY date DESC")) {
            stmt.setInt(1, nationId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    byte[] bytes = rs.getBytes("uuid");
                    long start = rs.getLong("date");

                    BigInteger uuid = new BigInteger(bytes);

                    Map.Entry<Long, Long> timePair = new KeyValue<>(start, end);
                    Map.Entry<BigInteger, Map.Entry<Long, Long>> uuidPair = new KeyValue<>(uuid, timePair);
                    list.add(uuidPair);
                    end = start;
                }
            }
            return list;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public Set<Integer> getMultis(BigInteger uuid) {
            Set<Integer> list = new ObjectLinkedOpenHashSet<>();
        try (PreparedStatement stmt = prepareQuery("select * FROM UUIDS WHERE uuid = ?")) {
            stmt.setBytes(1, uuid.toByteArray());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int nationId = rs.getInt("nation_id");
                    list.add(nationId);
                }
            }
            return list;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }


    public int updateUserIdsSince(int minutes, boolean overrideExisting) {
        List<Integer> toFetch = Locutus.imp().getNationDB().getNationsMatching(f -> f.getVm_turns() == 0 && f.active_m() < minutes).stream().map(DBNation::getNation_id).collect(Collectors.toList());

        return Locutus.imp().returnEventsAsync(events -> {
            int updated = 0;
            for (int i = 0; i < toFetch.size(); i += 500) {
                List<Integer> subList = toFetch.subList(i, Math.min(i + 500, toFetch.size()));
                updated += updateUserIds(overrideExisting, f -> f.setId(subList), events);
            }
            return updated;
        });
    }

    public int updateUserIds(boolean overrideExisting, Consumer<NationsQueryRequest> query, Consumer<Event> eventConsumer) {
        int updated = 0;
        for (Nation nation : Locutus.imp().getApiPool().fetchNations(false, query::accept, r -> {
            r.id();
            r.discord();
            r.discord_id();
        })) {
            if (nation.getDiscord_id() == null || nation.getDiscord_id().isEmpty()) continue;
            long discordId = Long.parseLong(nation.getDiscord_id());

            PNWUser existingUser = getUserFromNationId(nation.getId());
            if (existingUser != null && (!overrideExisting || existingUser.getDiscordId() == (discordId))) {
                continue;
            }
            if (eventConsumer != null) {
                User user = Locutus.imp().getDiscordApi().getUserById(discordId);
                eventConsumer.accept(new NationRegisterEvent(nation.getId(), null, user, existingUser == null));
            }
            addUser(new PNWUser(nation.getId(), discordId, nation.getDiscord()));
            updated++;
        }
        return updated;
    }

    public Map<Long, PNWUser> getRegisteredUsers() {
        updateUserCache();
        synchronized (userCache2) {
            return new Long2ObjectOpenHashMap<>(userCache2);
        }
    }

    public void addUser(PNWUser user) {
        if (user.getDiscordId() == Settings.INSTANCE.ADMIN_USER_ID || user.getNationId() == Settings.INSTANCE.NATION_ID) return;
        updateUserCache();
        PNWUser existing;
        synchronized (userCache2) {
            userCache2.put(user.getDiscordId(), user);
            existing = userNationCache2.put(user.getNationId(), user);
        }
        if (existing != null && existing.getDiscordId() == user.getDiscordId() && user.getNationId() == existing.getNationId()) return;

        update("INSERT OR REPLACE INTO `USERS`(`nation_id`, `discord_id`, `discord_name`, `date_updated`) VALUES(?, ?, ?, ?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, user.getNationId());
            stmt.setLong(2, user.getDiscordId());
            stmt.setString(3, user.getDiscordName());
            stmt.setLong(4, System.currentTimeMillis());
        });
    }

    public void addUsers(Collection<PNWUser> users) {
        updateUserCache();

        List<PNWUser> usersFiltered = new ObjectArrayList<>(users.size());
        synchronized (userCache2) {
            for (PNWUser user : users) {
                if (user.getDiscordId() == Settings.INSTANCE.ADMIN_USER_ID || user.getNationId() == Settings.INSTANCE.NATION_ID) {
                    continue;
                }
                PNWUser existing = userNationCache2.get(user.getNationId());
                if (existing != null && existing.getDiscordId() == user.getDiscordId()) {
                    continue;
                }
                usersFiltered.add(user);
            }
        }

        if (usersFiltered.isEmpty()) return;
        executeBatch(usersFiltered, "INSERT OR REPLACE INTO `USERS`(`nation_id`, `discord_id`, `discord_name`, `date_updated`) VALUES(?, ?, ?, ?)", new ThrowingBiConsumer<PNWUser, PreparedStatement>() {
            @Override
            public void acceptThrows(PNWUser user, PreparedStatement stmt) throws Exception {
                stmt.setInt(1, user.getNationId());
                stmt.setLong(2, user.getDiscordId());
                stmt.setString(3, user.getDiscordName());
                stmt.setLong(4, System.currentTimeMillis());
            }
        });
    }

    public Map<Long, PNWUser> getCachedUsers() {
        updateUserCache();
        return Collections.unmodifiableMap(Long2ObjectMaps.synchronize(userCache2, userCache2));
    }

    private List<PNWUser> getUsersRaw() {
        Set<Integer> nationsToDelete = new IntOpenHashSet();
        ArrayList<PNWUser> list = new ArrayList<>();
        try (PreparedStatement stmt = prepareQuery("select * FROM USERS")) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int nationId = rs.getInt("nation_id");
                    Long discordId = getLong(rs, "discord_id");
                    if (discordId == null) {
                        nationsToDelete.add(nationId);
                        continue;
                    }
                    String name = rs.getString("discord_name");
                    list.add(new PNWUser(nationId, discordId, name));
                }
            }
            if (!nationsToDelete.isEmpty()) {
                for (int id : nationsToDelete) {
                    unregister(id, null);
                }
            }
            return list;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public PNWUser getUserFromNationId(int nationId) {
        if (nationId == Settings.INSTANCE.NATION_ID && nationId > 0 && Settings.INSTANCE.ADMIN_USER_ID > 0) {
            long userId = Settings.INSTANCE.ADMIN_USER_ID;
            User user = Locutus.imp().getDiscordApi().getUserById(userId);
            return new PNWUser(nationId, Settings.INSTANCE.ADMIN_USER_ID, user == null ? null : DiscordUtil.getFullUsername(user));
        }
        updateUserCache();
        synchronized (userCache2) {
            return userNationCache2.get(nationId);
        }
    }

    public PNWUser getUserFromDiscordId(long discordId) {
        if (discordId == Settings.INSTANCE.ADMIN_USER_ID && Settings.INSTANCE.NATION_ID > 0 && discordId > 0) {
            User user = Locutus.imp().getDiscordApi().getUserById(discordId);
            return new PNWUser(Settings.INSTANCE.NATION_ID, Settings.INSTANCE.ADMIN_USER_ID, user == null ? null : DiscordUtil.getFullUsername(user));
        }
        updateUserCache();
        synchronized (userCache2) {
            return userCache2.get(discordId);
        }
    }

    public Integer getNationId(long userId) {
        if (userId == Settings.INSTANCE.ADMIN_USER_ID && Settings.INSTANCE.NATION_ID > 0) {
            return Settings.INSTANCE.NATION_ID;
        }
        updateUserCache();
        PNWUser user;
        synchronized (userCache2) {
            user = userCache2.get(userId);
        }
        if (user == null) return null;
        return user.getNationId();
    }

    private final Long2ObjectOpenHashMap<PNWUser> userCache2 = new Long2ObjectOpenHashMap<>();
    private final Int2ObjectOpenHashMap<PNWUser> userNationCache2 = new Int2ObjectOpenHashMap<>();

    public void updateUserCache() {
        if (!userCache2.isEmpty()) return;
        synchronized (userCache2) {
            if (!userCache2.isEmpty()) return;

            List<PNWUser> users = getUsersRaw();
            for (PNWUser user : users) {
                long id = user.getDiscordId();
                userCache2.put(user.getDiscordId(), user);
                PNWUser existing = userNationCache2.put(user.getNationId(), user);
                if (existing != null && existing.getDiscordId() != id) {
                    if (existing.getDiscordId() > id) {
                        userNationCache2.put(user.getNationId(), existing);
                    }
                }
            }
        }
    }

    public PNWUser getUser(User user) {
        return getCachedUsers().get(user.getIdLong());
    }

    public PNWUser getUser(Long discordId, String name, String nameWithDesc) {
        Map<Long, PNWUser> cached = getCachedUsers();
        if (discordId != null) {
            return cached.get(discordId);
        }
        List<PNWUser> secondary = null;
        for (Map.Entry<Long, PNWUser> entry : cached.entrySet()) {
            PNWUser user = entry.getValue();
            if (nameWithDesc != null && nameWithDesc.equalsIgnoreCase(user.getDiscordName())) {
                user.setDiscordId(user.getDiscordId());
                return user;
            }
            if (name != null && user.getDiscordName() != null) {
                if (user.getDiscordName().contains("#")) {
                    if (user.getDiscordName().startsWith(name + "#")) {
                        if (secondary == null) secondary = new ArrayList<>();
                        secondary.add(user);
                    }
                } else if (name.equalsIgnoreCase(user.getDiscordName())) {
                    if (secondary == null) secondary = new ArrayList<>();
                    secondary.add(user);
                }
            }
        }
        if (secondary != null && secondary.size() == 1) {
            return secondary.get(0);
        }
        return null;
    }

    public void unregister(Integer nationId, Long discordId) {
        if (nationId == null && discordId == null) throw new IllegalArgumentException("A nation id or discord id must be provided");
        if (discordId != null) {
            synchronized (userCache2) {
                userCache2.remove((long) discordId);
            }
        }
        if (nationId != null) {
            PNWUser user;
            synchronized (userCache2) {
                user = userNationCache2.remove((int) nationId);
            }
            if (user != null) {
                discordId = user.getDiscordId();
                synchronized (userCache2) {
                    userCache2.remove((long) discordId);
                }
            }
        }
        if (discordId != null) {
            synchronized (this) {
                logDeletion("USERS", System.currentTimeMillis(), "discord_id", discordId);
                Long finalDiscordId = discordId;
                update("DELETE FROM `USERS` WHERE discord_id = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
                    stmt.setLong(1, finalDiscordId == null ? -1 : finalDiscordId);
                });
            }
        }
    }

    public double getCityAverage(double def) {
        ByteBuffer value = getInfo(DiscordMeta.CITY_AVERAGE, 0);
        if (value != null) return value.getDouble();
        return def;
    }

    public void setCityAverage(double value) {
        setInfo(DiscordMeta.CITY_AVERAGE, 0, ByteBuffer.allocate(Double.BYTES).putDouble(value).array());
    }

    public Map<Integer, ApiKeyPool.ApiKey> getApiKeys(boolean deleteInvalid, boolean filterInactive, int maxResults) {
        Set<Integer> toDelete = new IntOpenHashSet();
        Map<Integer, ApiKeyPool.ApiKey> result = new Int2ObjectOpenHashMap<>();

        long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7);
        Predicate<DBNation> filter = filterInactive ? f -> f.getVm_turns() == 0 && f.lastActiveMs() > cutoff  : Predicates.alwaysTrue();

        try (PreparedStatement stmt = prepareQuery("select * FROM API_KEYS3 WHERE nation_id != ? AND api_key IS NOT NULL")) {
            stmt.setInt(1, Settings.INSTANCE.NATION_ID);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int nationId = rs.getInt("nation_id");
                    byte[] keyId = getBytes(rs, "api_key");
                    DBNation nation = DBNation.getById(nationId);
                    if (nation == null) {
                        if (deleteInvalid) {
                            toDelete.add(nationId);
                        }
                        continue;
                    }
                    if (!filter.test(nation)) {
                        continue;
                    }
                    ApiKeyPool.ApiKey apiKey = new ApiKeyPool.ApiKey(nationId, SQLUtil.byteArrayToHexString(keyId), null);
                    result.put(nationId, apiKey);
                    if (maxResults > 0 && result.size() >= maxResults) {
                        break;
                    }
                }
            }

            if (!toDelete.isEmpty()) {
                synchronized (this) {
                    executeStmt("DELETE FROM API_KEYS3 WHERE nation_id IN " + StringMan.getString(toDelete));
                }
            }

            return result;
        } catch (SQLException e) {
            return result;
        }
    }
}
