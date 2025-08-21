package link.locutus.discord.gpt.pw;

import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import net.dv8tion.jda.api.entities.User;

import java.io.Closeable;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public abstract class GptLimitTracker implements Closeable {

    public abstract String toString(GuildDB db, User user);

    public abstract void setUsageLimits(int turnLimit, int dayLimit, int guildTurnLimit, int guildDayLimit);

    public abstract boolean hasPermission(GuildDB db, User user, boolean checkLimits);
    public abstract CompletableFuture<String> submit(GuildDB db, User user, DBNation nation, Map<String, String> options, String input);

    public Map<String, String> getOptions() {
        return Map.of();
    }

    public abstract boolean checkAdminPermission(GuildDB db, User user, boolean throwError);

    public abstract boolean isPaused();

    public abstract Throwable getPauseError();

    public abstract void resume();

    public abstract void pause(Throwable e);
}
