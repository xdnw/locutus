package link.locutus.discord.web.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import link.locutus.discord.db.DBMain;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;

import java.sql.SQLException;
import java.util.AbstractMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.example.jooq.Tables.TOKENS3;
import static org.jooq.impl.DSL.asterisk;

public class WebDB extends DBMain {

    private final DSLContext ctx;

    public WebDB() throws SQLException, ClassNotFoundException {
        super("web");
        purgeTokens(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(28));
        this.ctx = DSL.using(this.getConnection(), SQLDialect.SQLITE);
    }

    @Override
    public void createTables() {
        this.ctx.createTable(TOKENS3).execute();
    }

    public void purgeTokens(long cutoff) {
        ctx.delete(TOKENS3).where(TOKENS3.TIMESTAMP.lessThan(cutoff)).execute();
    }

    public void addToken(long userId, String token, JsonObject obj) {
        long now = System.currentTimeMillis();
        ctx.insertInto(TOKENS3)
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

        ctx.select(asterisk()).from(TOKENS3).where(TOKENS3.TIMESTAMP.greaterThan(cutoff)).fetch().forEach(row -> {
            long userId = row.get(TOKENS3.USER_ID);
            String token = row.get(TOKENS3.TOKEN_ID);
            String value = row.get(TOKENS3.VALUE);
            JsonObject obj = JsonParser.parseString(value).getAsJsonObject();
            result.put(userId, new AbstractMap.SimpleEntry<>(token, obj));
        });
        return result;
    }
}
