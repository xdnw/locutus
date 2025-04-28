package link.locutus.discord.web.commands.binding;

import link.locutus.discord.Locutus;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.pnw.PNWUser;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.web.commands.binding.value_types.WebSession;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class DBAuthRecord {
    private final long userId;
    private final int nationId;
    public final UUID token;
    public final long timestamp;

    public DBAuthRecord(long userId, int nationId, UUID token, long timestamp) {
        this.userId = userId;
        this.nationId = nationId;
        this.token = token;
        this.timestamp = timestamp;
    }

    public User getUser(boolean fetch) {
        User user = userId == 0 ? null : DiscordUtil.getUser(userId);
        if (user == null && fetch && nationId != 0) {
            DBNation nation = DBNation.getById(nationId);
            if (nation != null) {
                user = nation.getUser();
            }
        }
        return user;
    }

    public DBNation getNation(boolean fetch) {
        DBNation nation = nationId == 0 ? null : DBNation.getById(nationId);
        if (nation == null && fetch && userId != 0) {
            User user = DiscordUtil.getUser(userId);
            if (user != null) {
                nation = DiscordUtil.getNation(user);
            }
        }
        return nation;
    }

    public boolean isValid() {
        return getUser(false) != null || getNation(false) != null;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() - timestamp > TimeUnit.DAYS.toMillis(Settings.INSTANCE.WEB.SESSION_TIMEOUT_DAYS);
    }

    /**
     * Convert this record to a map for JSON serialization
     */
    public WebSession toMap() {
        WebSession data = new WebSession();
        Long userId = getUserId();
        if (userId != null) {
            data.user = userId + "";
            User user = DiscordUtil.getUser(userId);
            data.user_valid = (user != null);
            if (user != null) {
                data.user_name = DiscordUtil.getFullUsername(user);
                data.user_icon = user.getEffectiveAvatarUrl();
            }
        }
        Integer nationId = getNationId();
        if (nationId != null) {
            data.nation = nationId;
            DBNation nation = DBNation.getById(nationId);
            data.nation_valid = (nation != null);
            if (nation != null) {
                data.nation_name = nation.getName();
                if (nation.getAlliance_id() != 0) {
                    data.alliance = nation.getAlliance_id();
                    data.alliance_name = nation.getAllianceName();
                }
            }
            if (userId != null) {
                PNWUser registeredNation = Locutus.imp().getDiscordDB().getUserFromDiscordId(userId);
                if (registeredNation != null) {
                    data.registered = true;
                    data.registered_nation = registeredNation.getNationId();
                }
            }
        }
        data.expires = timestamp;
        return data;
    }

    public Long getUserId() {
        if (userId == 0) {
            if (nationId != 0) {
                PNWUser user = Locutus.imp().getDiscordDB().getUserFromNationId(nationId);
                if (user != null) {
                    return user.getDiscordId();
                }
            }
            return null;
        }
        return userId;
    }

    public Integer getNationId() {
        if (nationId == 0) {
            if (userId != 0) {
                DBNation nation = DiscordUtil.getNation(userId);
                if (nation != null) {
                    return nation.getNation_id();
                }
            }
            return null;
        }
        return nationId;
    }

    public UUID getUUID() {
        return token;
    }

    public Integer getNationIdRaw() {
        return nationId == 0 ? null : nationId;
    }

    public Long getUserIdRaw() {
        return userId == 0 ? null : userId;
    }

    public Guild getDefaultGuild() {
        DBNation nation = getNation(true);
        if (nation != null) {
            GuildDB db = nation.getGuildDB();
            if (db != null) {
                return db.getGuild();
            }
        }
        return null;
    }
}
