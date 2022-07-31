package link.locutus.discord.db;

import com.ptsmods.mysqlw.query.SelectResults;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.core.ApiKeyPool;
import link.locutus.discord.apiv1.entities.ApiRecord;
import link.locutus.discord.apiv3.PoliticsAndWarV3;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.DiscordMeta;
import link.locutus.discord.pnw.PNWUser;
import link.locutus.discord.util.scheduler.ThrowingConsumer;
import link.locutus.discord.util.offshore.EncryptionUtil;
import com.google.api.client.util.Base64;
import com.google.api.client.util.Sets;
import com.google.gson.Gson;
import com.politicsandwar.graphql.model.ApiKeyDetails;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.dv8tion.jda.api.entities.User;

import java.lang.reflect.Type;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkNotNull;

public class DiscordDB extends DBMainV2 {
    public DiscordDB() throws SQLException, ClassNotFoundException {
        super("locutus");
        if (tableExists("credentials")) migrateCredentials();
    }

    @Override
    public void createTables() {
            executeStmt("CREATE TABLE IF NOT EXISTS `USERS` (`nation_id` INT NOT NULL, `discord_id` INT, `discord_name` VARCHAR, PRIMARY KEY(discord_id))");
            executeStmt("CREATE TABLE IF NOT EXISTS `UUIDS` (`nation_id` INT NOT NULL, `uuid` BLOB NOT NULL, `date` INT NOT NULL, PRIMARY KEY(nation_id, uuid, date))");
//            executeStmt("CREATE TABLE IF NOT EXISTS `CREDENTIALS` (`discordid` INT NOT NULL PRIMARY KEY, `user` VARCHAR NOT NULL, `password` VARCHAR NOT NULL, `salt` VARCHAR NOT NULL)");

            executeStmt("CREATE TABLE IF NOT EXISTS `CREDENTIALS2` (`discordid` INT NOT NULL PRIMARY KEY, `user` VARCHAR NOT NULL, `password` VARCHAR NOT NULL, `salt` VARCHAR NOT NULL)");

            executeStmt("CREATE TABLE IF NOT EXISTS `VERIFIED` (`nation_id` INT NOT NULL PRIMARY KEY)");

        executeStmt("CREATE TABLE IF NOT EXISTS `DISCORD_META` (`key` INT NOT NULL, `id` INT NOT NULL, `value` BLOB NOT NULL, PRIMARY KEY(`key`, `id`))");
        executeStmt("CREATE TABLE IF NOT EXISTS `API_KEYS2`(`nation_id` INT NOT NULL PRIMARY KEY, `api_key` INT, `bot_key` INT)");
        setupApiKeys();
    }

    private void setupApiKeys() {
        initInfo();

        Type type = new com.google.gson.reflect.TypeToken<ApiRecord>() {}.getType();
        Gson gson = new Gson();

        Map<Long, byte[]> keys = info.getOrDefault(DiscordMeta.API_KEY, Collections.emptyMap());
        for (byte[] jsonBytes : keys.values()) {
            String json = new String(jsonBytes, StandardCharsets.UTF_8);
            ApiRecord record = gson.fromJson(json, type);

            if (record.getNationId() == null || record.getApiKey() == null) continue;
            int nationId = record.getNationId();
            String key = record.getApiKey();

            addApiKey(nationId, key);
        }
        deleteInfo(DiscordMeta.API_KEY);

        try {
            if (tableExists("API_KEYS")) {
                try (ResultSet rs = getDb().selectBuilder("API_KEYS").select("*").executeRaw()) {
                    while (rs.next()) {
                        int nationId = rs.getInt("nation_id");
                        String key = Long.toHexString(rs.getLong("api_key"));
                        addApiKey(nationId, key);
                    }
                }
                getDb().drop("API_KEYS");
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void addApiKey(int nationId, String key) {
        long keyId = new BigInteger(key.toLowerCase(Locale.ROOT), 16).longValue();
        update("INSERT OR REPLACE INTO `API_KEYS2`(`nation_id`, `api_key`) VALUES(?, ?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, nationId);
            stmt.setLong(2, keyId);

        });
    }

    public void addBotKey(int nationId, String key) {
        long keyId = new BigInteger(key.toLowerCase(Locale.ROOT), 16).longValue();
        update("INSERT OR REPLACE INTO `API_KEYS2`(`nation_id`, `bot_key`) VALUES(?, ?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, nationId);
            stmt.setLong(2, keyId);

        });
    }

    public void addApiKey(int nationId, String key, String botKey) {
        if (botKey == null) {
            addApiKey(nationId, key);
            return;
        }
        long keyId = new BigInteger(key, 16).longValue();
        long botId = new BigInteger(botKey, 16).longValue();
        update("INSERT OR REPLACE INTO `API_KEYS2`(`nation_id`, `api_key`, `bot_key`) VALUES(?, ?, ?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, nationId);
            stmt.setLong(2, keyId);
            stmt.setLong(3, botId);

        });
    }

    public ApiKeyPool.ApiKey getApiKey(int nationId) {
        if (nationId == Settings.INSTANCE.NATION_ID && !Settings.INSTANCE.API_KEY_PRIMARY.isEmpty()) {
            return new ApiKeyPool.ApiKey(Settings.INSTANCE.NATION_ID, Settings.INSTANCE.API_KEY_PRIMARY, Settings.INSTANCE.ACCESS_KEY);
        }
        try (PreparedStatement stmt = prepareQuery("select * FROM API_KEYS2 WHERE nation_id = ?")) {
            stmt.setInt(1, nationId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Long keyId = getLong(rs, "api_key");
                    if (keyId == null) return null;

                    String key = Long.toHexString(keyId);
                    Long botKeyId = getLong(rs, "bot_key");
                    String botKey = botKeyId == null ? null : Long.toHexString(botKeyId);
                    return new ApiKeyPool.ApiKey(nationId, key, botKey);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void deleteApiKeyPairByNation(int nationId) {
        update("DELETE FROM `API_KEYS2` WHERE nation_id = ? ", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setLong(1, nationId);

        });
    }

    public void deleteApiKey(String key) {
        long keyId = new BigInteger(key.toLowerCase(Locale.ROOT), 16).longValue();
        update("UPDATE API_KEYS2 SET api_key = NULL WHERE api_key = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setLong(1, keyId);

        });
    }

    public void deleteBotKey(String key) {
        long keyId = new BigInteger(key.toLowerCase(Locale.ROOT), 16).longValue();
        update("UPDATE API_KEYS2 SET bot_key = NULL WHERE bot_key = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setLong(1, keyId);

        });
    }

    public Integer getNationFromApiKey(String key) {
        if (Settings.INSTANCE.API_KEY_PRIMARY.equalsIgnoreCase(key) && Settings.INSTANCE.NATION_ID > 0) {
            return Settings.INSTANCE.NATION_ID;
        }
        try (PreparedStatement stmt = prepareQuery("select * FROM API_KEYS2 WHERE api_key = ?")) {
            long keyId = new BigInteger(key.toLowerCase(Locale.ROOT), 16).longValue();
            stmt.setLong(1, keyId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("nation_id");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        ApiKeyDetails keyStats = new PoliticsAndWarV3(key, null).getApiKeyStats();
        if (keyStats != null && keyStats.getNation() != null && keyStats.getNation().getId() != null) {
            int natId = keyStats.getNation().getId();
            addApiKey(natId, keyStats.getKey());
            return natId;
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

    public void logout(long discordId) {
        update("DELETE FROM `CREDENTIALS2` where `discordid` = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setLong(1, discordId);
        });
    }

    private void migrateCredentials() {
        System.out.println("Migrate");
        Set<Long> ids = new HashSet<>();
        String query = getDb().selectBuilder("credentials").select("discordId").buildQuery();
        query(query, stmt -> {},
                (ThrowingConsumer<ResultSet>) r -> ids.add(r.getLong(1)));
        for (long discordId : ids) {
            System.out.println(" - mig " + discordId);
            Map.Entry<String, String> userPass = getUserPass(discordId, "credentials", EncryptionUtil.Algorithm.LEGACY);
            addUserPass(discordId, userPass.getKey(), userPass.getValue());
        }

        System.out.println("Drop");

        executeStmt("DROP TABLE CREDENTIALS");
    }

    public void addUserPass(long discordId, String user, String password) {
        try {
            String secretStr = Settings.INSTANCE.CLIENT_SECRET;
            if (secretStr == null || secretStr.isEmpty()) secretStr = Settings.INSTANCE.BOT_TOKEN;
            byte[] secret = secretStr.getBytes(StandardCharsets.ISO_8859_1);
            byte[] salt = EncryptionUtil.generateKey();

            byte[] userEnc = EncryptionUtil.encrypt2(EncryptionUtil.encrypt2(user.getBytes(StandardCharsets.ISO_8859_1), secret), salt);
            byte[] passEnc = EncryptionUtil.encrypt2(EncryptionUtil.encrypt2(password.getBytes(StandardCharsets.ISO_8859_1), secret), salt);

            update("INSERT OR REPLACE INTO `CREDENTIALS2` (`discordid`, `user`, `password`, `salt`) VALUES(?, ?, ?, ?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
                stmt.setLong(1, discordId);
                stmt.setString(2, Base64.encodeBase64String(userEnc));
                stmt.setString(3, Base64.encodeBase64String(passEnc));
                stmt.setString(4, Base64.encodeBase64String(salt));
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Map.Entry<String, String> getUserPass(long discordId) {
        return getUserPass(discordId, "credentials2", EncryptionUtil.Algorithm.DEFAULT);
    }

    private Map.Entry<String, String> getUserPass(long discordId, String table, EncryptionUtil.Algorithm algorithm) {
        if (discordId == Settings.INSTANCE.ADMIN_USER_ID && !Settings.INSTANCE.USERNAME.isEmpty() && !Settings.INSTANCE.PASSWORD.isEmpty()) {
            return Map.entry(Settings.INSTANCE.USERNAME, Settings.INSTANCE.PASSWORD);
        }
        try (PreparedStatement stmt = prepareQuery("select * FROM " + table + " WHERE `discordid` = ?")) {
            stmt.setLong(1, discordId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
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

                    return new AbstractMap.SimpleEntry<>(user, pass);
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
        update("INSERT OR IGNORE INTO `UUIDS` (`nation_id`, `uuid`, `date`) VALUES(?, ?, ?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
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
                list.add(new AbstractMap.SimpleEntry<>(nationId, date));
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
                    list.add(new AbstractMap.SimpleEntry<>(nationId, new AbstractMap.SimpleEntry<>(date, new BigInteger(bytes))));
                }
            }
            return list;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public Set<Integer> getVerified() {
            HashSet<Integer> set = new HashSet<>();
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
            HashSet<Integer> set = new HashSet<>();
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
                    list.add(new AbstractMap.SimpleEntry<>(start, end));
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

                    Map.Entry<Long, Long> timePair = new AbstractMap.SimpleEntry<>(start, end);
                    Map.Entry<BigInteger, Map.Entry<Long, Long>> uuidPair = new AbstractMap.SimpleEntry<>(uuid, timePair);
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
            Set<Integer> list = new LinkedHashSet<>();
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

//    public void addGuild(Guild guild, int allianceId, String username, String password) {
////        addTask(TaskType.GUILD, allianceId, new UniqueStatement(allianceId) {
////            @Override
////            public PreparedStatement get() throws SQLException {
////                return getConnection().prepareStatement("INSERT OR REPLACE INTO `GUILDS`(`server`, `alliance`) VALUES(?, ?)");
////            }
////
////            @Override
////            public void set(PreparedStatement stmt) throws SQLException {
////                stmt.setLong(1, guild.getIdLong());
////                stmt.setInt(2, min);
////                stmt.setInt(3, max);
////                stmt.setString(4, build);
////            }
////        });
//    }


    public void addUser(PNWUser user) {
        if (user.getDiscordId() != null) {
            updateUserCache();
            userCache.put(user.getDiscordId(), user);
            userNationCache.put(user.getNationId(), user);
        }
        update("INSERT OR REPLACE INTO `USERS`(`nation_id`, `discord_id`, `discord_name`) VALUES(?, ?, ?)", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, user.getNationId());
            if (user.getDiscordId() == null) {
                stmt.setNull(2, Types.INTEGER);
            } else {
                stmt.setLong(2, user.getDiscordId());
            }
            stmt.setString(3, user.getDiscordName());
        });
    }

    public Map<Long, PNWUser> getCachedUsers() {
        updateUserCache();
        return Collections.unmodifiableMap(userCache);
    }

    private List<PNWUser> getUsersRaw() {
        ArrayList<PNWUser> list = new ArrayList<>();
        try (PreparedStatement stmt = prepareQuery("select * FROM USERS")) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int nationId = rs.getInt("nation_id");
                    Long discordId = getLong(rs, "discord_id");
                    String name = rs.getString("discord_name");
                    list.add(new PNWUser(nationId, discordId, name));
                }
            }
            return list;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public PNWUser getUserFromNationId(int nationId) {
        updateUserCache();
        return userNationCache.get(nationId);
//
//        try (PreparedStatement stmt = prepareQuery("select * FROM USERS WHERE `nation_id` = ? order by case when discord_id is null then 1 else 0 end")) {
//            stmt.setInt(1, nationId);
//            try (ResultSet rs = stmt.executeQuery()) {
//                while (rs.next()) {
//                    Long discordId = getLong(rs, "discord_id");
//                    String name = rs.getString("discord_name");
//                    return new PNWUser(nationId, discordId, name);
//                }
//            }
//            return null;
//        } catch (SQLException e) {
//            e.printStackTrace();
//            return null;
//        }
    }

    public PNWUser getUserFromDiscordId(long discordId) {
        updateUserCache();
        return userCache.get(discordId);
//        try (PreparedStatement stmt = prepareQuery("select * FROM USERS WHERE `discord_id` = ?")) {
//            stmt.setLong(1, discordId);
//            try (ResultSet rs = stmt.executeQuery()) {
//                while (rs.next()) {
//                    int nationId = rs.getInt("nation_id");
//                    String name = rs.getString("discord_name");
//                    return new PNWUser(nationId, discordId, name);
//                }
//            }
//            return null;
//        } catch (SQLException e) {
//            e.printStackTrace();
//            return null;
//        }
    }

    private ConcurrentHashMap<Long, PNWUser> userCache = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Integer, PNWUser> userNationCache = new ConcurrentHashMap<>();

    public void updateUserCache() {
        if (!userCache.isEmpty()) return;
        synchronized (this) {
            if (!userCache.isEmpty()) return;
            for (PNWUser user : getUsersRaw()) {
                Long id = user.getDiscordId();
                if (id != null) {
                    userCache.put(user.getDiscordId(), user);
                }
                userNationCache.put(user.getNationId(), user);
            }
        }
    }

    public PNWUser getUser(User user) {
        return getCachedUsers().get(user.getIdLong());
//        return getUser(user.getIdLong(), user.getName(), user.getName() + "#" + user.getDiscriminator());
//        return getUser(user.getIdLong(), null, null);
    }

    public PNWUser getUser(Long discordId, String name, String nameWithDesc) {
        Map<Long, PNWUser> cached = getCachedUsers();
        if (discordId != null) {
            return cached.get(discordId);
        }
        List<PNWUser> secondary = null;
        for (Map.Entry<Long, PNWUser> entry : cached.entrySet()) {
            PNWUser user = entry.getValue();
            if (user.getDiscordId() == null) continue;

            if (nameWithDesc != null && nameWithDesc.equalsIgnoreCase(user.getDiscordName())) {
                user.setDiscordId(user.getDiscordId());
                return user;
            }
            if (name != null && user.getDiscordName() != null) {
                if (user.getDiscordName().contains("#")) {
                    if (discordId == null && user.getDiscordName().startsWith(name + "#")) {
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
        if (discordId != null) userCache.remove(discordId);
        if (nationId != null) {
            PNWUser user = getUserFromNationId(nationId);
            if (user != null && user.getDiscordId() != null) {
                userCache.remove(user.getDiscordId());
            }
        }
        update("DELETE FROM `USERS` WHERE nation_id = ? OR discord_id = ?", (ThrowingConsumer<PreparedStatement>) stmt -> {
            stmt.setInt(1, nationId == null ? -1 : nationId);
            stmt.setLong(2, discordId == null ? -1 : discordId);
        });
    }
}
