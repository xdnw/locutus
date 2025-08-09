package link.locutus.discord.gpt.pw;

import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.gpt.imps.text2text.IText2Text;
import link.locutus.discord.gpt.imps.ProviderType;
import net.dv8tion.jda.api.entities.User;

import java.io.Closeable;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public abstract class GPTProvider implements Closeable {
    private final IText2Text text2Text;

    public GPTProvider(IText2Text text2Text) {
        this.text2Text = text2Text;
    }

    public IText2Text getText2Text() {
        return text2Text;
    }

    public abstract String toString(GuildDB db, User user);

    public abstract void setUsageLimits(int turnLimit, int dayLimit, int guildTurnLimit, int guildDayLimit);

    public abstract boolean hasPermission(GuildDB db, User user, boolean checkLimits);
    public abstract CompletableFuture<String> submit(GuildDB db, User user, DBNation nation, Map<String, String> options, String input);

    public String getId() {
        return text2Text.getId();
    }

    public Map<String, String> getOptions() {
        return text2Text.getOptions();
    }

    public int getSize(String text) {
        return text2Text.getSize(text);
    }

    public int getSizeCap() {
        return text2Text.getSizeCap();
    }

    public abstract ProviderType getType();

    public abstract boolean checkAdminPermission(GuildDB db, User user, boolean throwError);

    public abstract boolean isPaused();

    public abstract Throwable getPauseError();

    public abstract void resume();

    public abstract void pause(Throwable e);
}
