package link.locutus.discord.web.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.DBMain;
import link.locutus.discord.db.DBMainV3;
import link.locutus.discord.web.commands.binding.AuthBindings;
import link.locutus.discord.web.commands.binding.DBAuthRecord;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;

import javax.annotation.Nullable;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.jooq.impl.DSL.asterisk;

public class WebDB extends DBMainV3 {

    private final Map<UUID, DBAuthRecord> authByUUID = new ConcurrentHashMap<>();
    private final Map<Long, DBAuthRecord> authByUserId = new ConcurrentHashMap<>();
    private final Map<Integer, DBAuthRecord> authByNationId = new ConcurrentHashMap<>();

    public WebDB() throws SQLException, ClassNotFoundException {
        super(Settings.INSTANCE.DATABASE, "web", false);
        load();
    }

    public DBAuthRecord get(UUID uuid) {
        System.out.println("GET " + uuid + " | " + authByUUID.keySet());
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
        ctx().execute("CREATE TABLE IF NOT EXISTS `AUTH` (`least` BIGINT NOT NULL, `most` BIGINT NOT NULL, `NATION_ID` BIGINT, `USER_ID` BIGINT, `TIMESTAMP` BIGINT NOT NULL, PRIMARY KEY (`least`, `most`))");
        deleteOldTempAuth();
    }

    private void load() {
        ctx().select(asterisk()).from("AUTH").where("TIMESTAMP > ?", getCutoff()).fetch().forEach(row -> {
            long small = row.get("least", Long.class);
            long big = row.get("most", Long.class);
            Integer nationId = row.get("NATION_ID", Integer.class);
            Long userId = row.get("USER_ID", Long.class);
            UUID uuid = new UUID(big, small);
            long timestamp = row.get("TIMESTAMP", Long.class);
            DBAuthRecord record = new DBAuthRecord(userId, nationId, uuid, timestamp);
            System.out.println("Loaded " + record + " | " + uuid);
            this.authByUUID.put(uuid, record);
            if (userId != null) {
                this.authByUserId.put(userId, record);
            }
            if (nationId != null) {
                this.authByNationId.put(nationId, record);
            }
        });
    }

    public void removeToken(@Nullable UUID uuid, @Nullable Integer nationId, @Nullable Long userId) {
        if (userId == null && nationId == null && uuid == null) {
            throw new IllegalArgumentException("All of userId and nationId and uuid cannot be null");
        }
        if (nationId != null) {
            DBAuthRecord record = authByNationId.remove(nationId);
            if (record != null) {
                authByUUID.remove(record.token);
                if (record.getUserIdRaw() != null) authByUserId.remove(record.getUserIdRaw());
            }
        }
        if (userId != null) {
            DBAuthRecord record = authByUserId.remove(userId);
            if (record != null) {
                authByUUID.remove(record.token);
                if (record.getNationIdRaw() != null) authByNationId.remove(record.getNationIdRaw());
            }
        }
        if (uuid != null) {
            DBAuthRecord record = authByUUID.remove(uuid);
            if (record != null) {
                if (record.getUserIdRaw() != null) authByUserId.remove(record.getUserIdRaw());
                if (record.getNationIdRaw() != null) authByNationId.remove(record.getNationIdRaw());
            }
        }
        StringBuilder query = new StringBuilder("DELETE FROM `AUTH` WHERE ");
        List<String> where = new ArrayList<>();
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
        ctx().execute(query.toString());
    }

    public synchronized DBAuthRecord updateToken(UUID uuid, @Nullable Integer nation, @Nullable Long userId) {
        removeToken(uuid, nation, userId);
        DBAuthRecord record = new DBAuthRecord(userId, nation, uuid, System.currentTimeMillis());
        addToken(uuid, record);
        return record;
    }

    public synchronized void addToken(UUID uuid, DBAuthRecord auth) {
        long small = uuid.getLeastSignificantBits();
        long big = uuid.getMostSignificantBits();
        Integer nationId = auth.getNationIdRaw();
        Long userId = auth.getUserIdRaw();
        removeToken(uuid, nationId, userId);
        ctx().execute("INSERT OR REPLACE INTO `AUTH` (`least`, `most`, `NATION_ID`, `USER_ID`, `TIMESTAMP`) VALUES (?, ?, ?, ?, ?) ", small, big, nationId, userId, auth.timestamp);
        if (nationId != null) {
            authByNationId.put(nationId, auth);
        }
        if (userId != null) {
            authByUserId.put(userId, auth);
        }
        authByUUID.put(uuid, auth);
    }

    public void deleteOldTempAuth() {
        ctx().execute("DELETE FROM `AUTH` WHERE `TIMESTAMP` < ?;", getCutoff());
    }
}
