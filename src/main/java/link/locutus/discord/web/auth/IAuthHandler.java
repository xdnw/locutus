package link.locutus.discord.web.auth;

import com.google.gson.JsonObject;
import io.javalin.http.Context;
import link.locutus.discord.db.entities.DBNation;

import java.io.IOException;
import java.util.Map;

public interface IAuthHandler {
    void login(Context ctx);

    void logout(Context ctx);

    default Long getDiscordUser(Context ctx) throws IOException {
        return getDiscordUser(ctx, false);
    }

    Long getDiscordUser(Context ctx, boolean login) throws IOException;

    DBNation getNation(Context ctx) throws IOException;
}
