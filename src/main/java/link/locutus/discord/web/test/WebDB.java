package link.locutus.discord.web.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.DBMain;
import link.locutus.discord.db.DBMainV3;
import link.locutus.discord.web.auth.IAuthHandler;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;

import java.sql.SQLException;
import java.util.AbstractMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.example.jooq.web.Tables.TOKENS3;
import static org.jooq.impl.DSL.asterisk;

public class WebDB extends DBMainV3 {

    public WebDB() throws SQLException, ClassNotFoundException {
        super(Settings.INSTANCE.DATABASE, "web", false);
        purgeTokens(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(28));
    }

    private long getCutoff() {
        return System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30);
    }

    @Override
    public void createTables() {
        ctx().createTableIfNotExists(TOKENS3).columns(TOKENS3.USER_ID, TOKENS3.TOKEN_ID, TOKENS3.VALUE, TOKENS3.TIMESTAMP).execute();

        // create Auth db using executeStmt
        // not null long small, not null long big, long nullable nation_id, long nullable user_id, not null long timestamp
        ctx().execute("CREATE TABLE IF NOT EXISTS `AUTH` (`least` BIGINT NOT NULL, `most` BIGINT NOT NULL, `NATION_ID` BIGINT, `USER_ID` BIGINT, `TIMESTAMP` BIGINT NOT NULL, PRIMARY KEY (`least`, `most`))");

        deleteOldTempAuth();
    }

    public void purgeTokens(long cutoff) {
        ctx().delete(TOKENS3).where(TOKENS3.TIMESTAMP.lessThan(cutoff)).execute();
    }

    public void addToken(long userId, String token, JsonObject obj) {
        long now = System.currentTimeMillis();
        ctx().insertInto(TOKENS3)
                .values(userId, token, obj.toString(), now)
                .onDuplicateKeyUpdate()
                .set(TOKENS3.VALUE, obj.toString())
                .set(TOKENS3.TOKEN_ID, token)
                .set(TOKENS3.TIMESTAMP, now)
                .execute();
    }

    public Map<Long, Map.Entry<String, JsonObject>> loadTokens() {
        Map<Long, Map.Entry<String, JsonObject>> result = new ConcurrentHashMap<>();

        long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30);

        ctx().select(asterisk()).from(TOKENS3).where(TOKENS3.TIMESTAMP.greaterThan(cutoff)).fetch().forEach(row -> {
            long userId = row.get(TOKENS3.USER_ID);
            String token = row.get(TOKENS3.TOKEN_ID);
            String value = row.get(TOKENS3.VALUE);
            JsonObject obj = JsonParser.parseString(value).getAsJsonObject();
            result.put(userId, new AbstractMap.SimpleEntry<>(token, obj));
        });
        return result;
    }

    public void removeTempToken(UUID uuid) {
        long small = uuid.getLeastSignificantBits();
        long big = uuid.getMostSignificantBits();
        // delete from AUTH table
        ctx().execute("DELETE FROM `AUTH` WHERE `least` = ? AND `most` = ?;", small, big);
    }

    public void addTempToken(UUID uuid, IAuthHandler.Auth auth) {
        long timestamp = System.currentTimeMillis();
        long small = uuid.getLeastSignificantBits();
        long big = uuid.getMostSignificantBits();
        Integer nationId = auth.nationId();
        Long userId = auth.userId();
        // insert into AUTH table
        ctx().execute("INSERT INTO `AUTH` (`least`, `most`, `NATION_ID`, `USER_ID`, `TIMESTAMP`) VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE `NATION_ID` = ?, `USER_ID` = ?, `TIMESTAMP` = ?;", small, big, nationId, userId, timestamp, nationId, userId, timestamp);
    }

    public void deleteOldTempAuth() {
        // delete from AUTH table
        ctx().execute("DELETE FROM `AUTH` WHERE `TIMESTAMP` < ?;", getCutoff());
    }

    public Map<UUID, IAuthHandler.Auth> loadTempTokens() {
        Map<UUID, IAuthHandler.Auth> result = new ConcurrentHashMap<>();
        // select from AUTH table
        ctx().select(asterisk()).from("AUTH").where("TIMESTAMP > ?", getCutoff()).fetch().forEach(row -> {
            long small = row.get("least", Long.class);
            long big = row.get("most", Long.class);
            Integer nationId = row.get("NATION_ID", Integer.class);
            Long userId = row.get("USER_ID", Long.class);
            UUID uuid = new UUID(big, small);
            IAuthHandler.Auth auth = new IAuthHandler.Auth(nationId, userId, Long.MAX_VALUE);
            result.put(uuid, auth);
        });
        return result;
    }
}
