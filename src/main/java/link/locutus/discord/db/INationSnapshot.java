package link.locutus.discord.db;

import link.locutus.discord.db.entities.DBNation;
import net.dv8tion.jda.api.entities.User;

import java.util.Set;

public interface INationSnapshot {
    DBNation getNationById(int id);

    DBNation getNationByUser(User user);

    Set<DBNation> getNationsByAlliance(Set<Integer> alliances);

    DBNation getNationByLeader(String input);

    DBNation getNationByName(String input); // PWBindings.nation(null, input);
    // DiscordUtil.parseNation(name, true)

    int getAllNations();

    Set<DBNation> getNationsByBracket(int taxId);

    Set<DBNation> getNationsByAlliance(int id);
//    DiscordUtil.getNation(
}
