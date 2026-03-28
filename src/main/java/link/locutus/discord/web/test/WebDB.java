package link.locutus.discord.web.test;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.DBMainV3;
import link.locutus.discord.web.commands.binding.DBAuthRecord;

import javax.annotation.Nullable;
import java.io.File;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class WebDB extends DBMainV3 {

    private final Map<UUID, DBAuthRecord> authByUUID = new ConcurrentHashMap<>();
    private final Map<Long, DBAuthRecord> authByUserId = new ConcurrentHashMap<>();
    private final Map<Integer, DBAuthRecord> authByNationId = new ConcurrentHashMap<>();

    public WebDB() throws SQLException, ClassNotFoundException {
        super(new File(Settings.INSTANCE.DATABASE.SQLITE.DIRECTORY), "web", true, false, 0, 0, 5);
        createTables();
        load();
    }

    public DBAuthRecord get(UUID uuid) {
        return authByUUID.get(uuid);
    }

    public DBAuthRecord get(long userId) {
        return authByUserId.get(userId);
    }

    public DBAuthRecord get(int nationId) {
        return authByNationId.get(nationId);
    }

    private long getCutoff() {
        return System.currentTimeMillis() - TimeUnit.DAYS.toMillis(Settings.INSTANCE.WEB.SESSION_TIMEOUT_DAYS);
    }

    @Override
    public void createTables() {
        jdbi().useHandle(handle ->
                handle.execute("CREATE TABLE IF NOT EXISTS `AUTH` (`least` BIGINT NOT NULL, `most` BIGINT NOT NULL, `NATION_ID` BIGINT, `USER_ID` BIGINT, `TIMESTAMP` BIGINT NOT NULL, PRIMARY KEY (`least`, `most`))")
        );
        deleteOldTempAuth();
    }

    private void load() {
        List<StoredAuthRecord> rows = jdbi().withHandle(handle -> handle.createQuery(
                        "SELECT least, most, NATION_ID, USER_ID, TIMESTAMP FROM `AUTH` WHERE `TIMESTAMP` > :cutoff")
                .bind("cutoff", getCutoff())
                .map((rs, ctx) -> new StoredAuthRecord(
                        rs.getLong("least"),
                        rs.getLong("most"),
                        (Integer) rs.getObject("NATION_ID"),
                        (Long) rs.getObject("USER_ID"),
                        rs.getLong("TIMESTAMP")
                ))
                .list());

        for (StoredAuthRecord row : rows) {
            UUID uuid = new UUID(row.most(), row.least());
            DBAuthRecord record = new DBAuthRecord(
                    row.userId() == null ? 0 : row.userId(),
                    row.nationId() == null ? 0 : row.nationId(),
                    uuid,
                    row.timestamp()
            );
            authByUUID.put(uuid, record);
            if (row.userId() != null) {
                authByUserId.put(row.userId(), record);
            }
            if (row.nationId() != null) {
                authByNationId.put(row.nationId(), record);
            }
        }
    }

    public void removeToken(boolean resolve, @Nullable UUID uuid, @Nullable Integer nationId, @Nullable Long userId) {
        if (userId == null && nationId == null && uuid == null) {
            throw new IllegalArgumentException("All of userId and nationId and uuid cannot be null");
        }
        if (nationId != null) {
            DBAuthRecord record = authByNationId.remove(nationId);
            if (record != null) {
                authByUUID.remove(record.token);
                if (resolve && record.getUserId() != null) {
                    userId = record.getUserIdRaw();
                    authByUserId.remove(userId);
                }
            }
        }
        if (userId != null) {
            DBAuthRecord record = authByUserId.remove(userId);
            if (record != null) {
                authByUUID.remove(record.token);
                if (resolve && record.getNationId() != null) {
                    nationId = record.getNationIdRaw();
                    authByNationId.remove(nationId);
                }
            }
        }
        if (uuid != null) {
            DBAuthRecord record = authByUUID.remove(uuid);
            if (record != null && resolve) {
                if (userId == null && record.getUserId() != null) {
                    userId = record.getUserIdRaw();
                    authByUserId.remove(userId);
                }
                if (nationId == null && record.getNationId() != null) {
                    nationId = record.getNationIdRaw();
                    authByNationId.remove(nationId);
                }
            }
        }
        StringBuilder query = new StringBuilder("DELETE FROM `AUTH` WHERE ");
        List<String> where = new ObjectArrayList<>(3);
        if (userId != null) {
            where.add("`USER_ID` = " + userId);
        }
        if (nationId != null) {
            where.add("`NATION_ID` = " + nationId);
        }
        if (uuid != null) {
            long small = uuid.getLeastSignificantBits();
            long big = uuid.getMostSignificantBits();
            where.add("(`least` = " + small + " AND `most` = " + big + ")");
        }
        query.append(String.join(" OR ", where));
        jdbi().useHandle(handle -> handle.execute(query.toString()));
    }

    public synchronized DBAuthRecord updateToken(UUID uuid, @Nullable Integer nation, @Nullable Long userId) {
        DBAuthRecord record = new DBAuthRecord(userId == null ? 0 : userId, nation == null ? 0 : nation, uuid, System.currentTimeMillis());
        record.getUserId();
        record.getNationId();
        addToken(uuid, record);
        return record;
    }

    public synchronized void addToken(UUID uuid, DBAuthRecord auth) {
        long small = uuid.getLeastSignificantBits();
        long big = uuid.getMostSignificantBits();
        Integer nationId = auth.getNationIdRaw();
        Long userId = auth.getUserIdRaw();
        jdbi().useHandle(handle -> handle.createUpdate(
                        "INSERT OR REPLACE INTO `AUTH` (`least`, `most`, `NATION_ID`, `USER_ID`, `TIMESTAMP`) VALUES (:least, :most, :nationId, :userId, :timestamp)")
                .bind("least", small)
                .bind("most", big)
                .bind("nationId", nationId)
                .bind("userId", userId)
                .bind("timestamp", auth.timestamp)
                .execute());
        if (nationId != null) {
            authByNationId.put(nationId, auth);
        }
        if (userId != null) {
            authByUserId.put(userId, auth);
        }
        authByUUID.put(uuid, auth);
    }

    public void deleteOldTempAuth() {
        jdbi().useHandle(handle -> handle.createUpdate("DELETE FROM `AUTH` WHERE `TIMESTAMP` < :cutoff")
                .bind("cutoff", getCutoff())
                .execute());
    }

    public Set<UUID> getAuthKeys() {
        return authByUUID.keySet();
    }

    private record StoredAuthRecord(long least, long most, Integer nationId, Long userId, long timestamp) {
    }
}
