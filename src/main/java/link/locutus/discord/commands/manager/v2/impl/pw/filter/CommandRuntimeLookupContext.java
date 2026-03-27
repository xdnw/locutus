package link.locutus.discord.commands.manager.v2.impl.pw.filter;

import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.bank.TaxBracketLookup;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.util.discord.GuildShardManager;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.Collection;
import java.util.Set;

/**
 * Neutral lookup/runtime contract exposed to bindings, permissions, and autocomplete.
 */
public interface CommandRuntimeLookupContext {
    NationSnapshotService nationSnapshots();

    TaxBracketLookup taxBracketLookup();

    CommandRuntimeLookupService lookup();

    Set<DBAlliance> getAlliances();

    GuildDB getGuildDb(Guild guild);

    GuildDB getGuildDb(long guildId);

    Guild getGuild(long guildId);

    Collection<GuildDB> getGuildDatabases();

    Set<Guild> getMutualGuilds(User user);

    GuildDB getRootCoalitionServer();

    GuildShardManager shardManager();

    void markNationDirty(int nationId);
}
