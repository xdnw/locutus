package link.locutus.discord.commands.manager.v2.impl.pw.filter;

import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.pnw.PNWUser;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

interface CommandRuntimeLookupResolver {
    NationSnapshotService nationSnapshots();

    User getDiscordUserById(long userId);

    PNWUser getRegisteredUserById(long userId);

    PNWUser getRegisteredUser(String userName, String fullTag);

    User findDiscordUser(String search, Guild guild);

    DBNation getNationOrCreate(int nationId);

    DBAlliance getAllianceById(int allianceId);

    DBAlliance getAllianceOrCreate(int allianceId);
}
