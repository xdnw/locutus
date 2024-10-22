package link.locutus.discord.db;

import link.locutus.discord.Locutus;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.pnw.PNWUser;
import net.dv8tion.jda.api.entities.User;

import java.util.Set;

public interface INationSnapshot {
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

    DBNation getNationByName(String input); // PWBindings.nation(null, input);
    // DiscordUtil.parseNation(name, true)

    Set<DBNation> getAllNations();

    Set<DBNation> getNationsByBracket(int taxId);

    Set<DBNation> getNationsByAlliance(int id);
//    DiscordUtil.getNation(
}
