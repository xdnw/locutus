package link.locutus.discord.db;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.NationColor;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.nation.DBNationData;
import link.locutus.discord.db.entities.nation.SimpleDBNation;
import link.locutus.discord.pnw.PNWUser;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.Set;

public interface INationSnapshot {
    default DBNation getNationById(int id, boolean allowDeleted) {
        DBNation nation = getNationById(id);
        if (nation == null && allowDeleted) {
            nation = new SimpleDBNation(new DBNationData());
            nation.edit().setNation_id(id);
        }
        return nation;
    }
    DBNation getNationById(int id);

    default DBNation getNationByUser(User user) {
        return getNationByUser(user.getIdLong());
    }

    default DBNation getNationByUser(long userId) {
        DiscordDB disDb = Locutus.imp().getDiscordDB();
        PNWUser pnwUser = disDb.getUserFromDiscordId(userId);
        if (pnwUser != null) {
            return getNationById(pnwUser.getNationId());
        }
        return null;
    }

    Set<DBNation> getNationsByAlliance(Set<Integer> alliances);

    DBNation getNationByLeader(String input);

    DBNation getNationByName(String input);

    default DBNation getNationByNameOrLeader(String input) {
        DBNation nation = getNationByName(input);
        if (nation == null) {
            nation = getNationByLeader(input);
        }
        return nation;
    }

    default DBNation getNationByInput(String input, boolean allowDeleted, boolean throwError, Guild guildOrNull) {
        return DiscordUtil.parseNation(this, input, allowDeleted, false, throwError, guildOrNull);
    }

    Set<DBNation> getAllNations();

    Set<DBNation> getNationsByBracket(int taxId);

    Set<DBNation> getNationsByAlliance(int id);

    Set<DBNation> getNationsByColor(NationColor color);
}
