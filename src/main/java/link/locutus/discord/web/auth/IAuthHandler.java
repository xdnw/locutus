package link.locutus.discord.web.auth;

import io.javalin.http.Context;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.User;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public interface IAuthHandler {
    void logout(Context ctx);

    Auth getAuth(Context ctx);

    public record Auth(Integer nationId, Long userId, long timestamp) {

        public User getUser() {
            return userId == null ? null : DiscordUtil.getUser(userId);
        }

        public DBNation getNation() {
            return nationId == null ? null : DBNation.byId(nationId);
        }

        public boolean isValid() {
            return getUser() != null || getNation() != null;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - timestamp > TimeUnit.MINUTES.toMillis(30);
        }
    }
}
