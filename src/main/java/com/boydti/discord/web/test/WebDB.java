package com.boydti.discord.web.test;

import com.boydti.discord.db.DBMain;
import com.boydti.discord.util.scheduler.ThrowingConsumer;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.AbstractMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class WebDB extends DBMain {
    public WebDB() throws SQLException, ClassNotFoundException {
        super("web");
        purgeTokens(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(28));
    }

    @Override
    public void createTables() {
        executeStmt("CREATE TABLE IF NOT EXISTS `tokens3` (`user_id`, `token_id` VARCHAR NOT NULL, `value` VARCHAR NOT NULL, `timestamp` INT NOT NULL, PRIMARY KEY(user_id))");
    }

    public void purgeTokens(long cutoff) {
        update("DELETE FROM `tokens3` WHERE `timestamp` < ?", (ThrowingConsumer<PreparedStatement>) stmt -> stmt.setLong(1, cutoff));
    }

    public void addToken(long userId, String token, JsonObject obj) {
        String query = "INSERT OR REPLACE INTO `tokens3`(`user_id`, `token_id`,`value`,`timestamp`) VALUES(?, ?,?,?)";
        update(query, new ThrowingConsumer<PreparedStatement>() {
            @Override
            public void acceptThrows(PreparedStatement stmt) throws Exception {
                stmt.setLong(1, userId);
                stmt.setString(2, token);
                stmt.setString(3, obj.toString());
                stmt.setLong(4, System.currentTimeMillis());
            }
        });
    }

    public Map<Long, Map.Entry<String, JsonObject>> loadTokens() {
        Map<Long, Map.Entry<String, JsonObject>> result = new ConcurrentHashMap<>();

        long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30);
        query("SELECT * FROM `tokens3` where `timestamp` > ?", new ThrowingConsumer<PreparedStatement>() {
            @Override
            public void acceptThrows(PreparedStatement stmt) throws Exception {
                stmt.setLong(1, cutoff);
            }
        }, new ThrowingConsumer<ResultSet>() {
            @Override
            public void acceptThrows(ResultSet rs) throws SQLException {
                while (rs.next()) {
                    long userId = rs.getLong(1);
                    String key = rs.getString(2);
                    String jsonStr = rs.getString(3);
                    JsonObject json = JsonParser.parseString(jsonStr).getAsJsonObject();

                    result.put(userId, new AbstractMap.SimpleEntry<>(key, json));
                }
            }
        });
        return result;
    }
}
