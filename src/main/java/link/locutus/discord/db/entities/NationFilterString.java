package link.locutus.discord.db.entities;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.LocalValueStore;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.impl.pw.NationFilter;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.NationPlaceholders;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class NationFilterString implements NationFilter {
    private final String filter;
    private final Predicate<DBNation> predicate;
    private final Guild guild;
    private Set<Integer> cachedNations;
    private long dateCached;

    public NationFilterString(String filter, Guild guild, User user, DBNation nation) {
        NationPlaceholders placeholders = Locutus.cmd().getV2().getNationPlaceholders();
        LocalValueStore store = placeholders.createLocals(guild, user, nation);
        this.predicate = placeholders.parseFilter(store, filter);

        this.filter = filter;
        this.guild = guild;
    }

    @Override
    public String getFilter() {
        return filter;
    }

    @Override
    public boolean test(DBNation nation) {
        String localFilter = "#nation_id=" + nation.getNation_id() + "," + filter;
        Set<DBNation> nations = DiscordUtil.parseNations(guild, localFilter);
        return nations != null && nations.contains(nation);
    }

    @Override
    public Predicate<DBNation> toCached(long expireAfter) {
        return nation -> {
            if (cachedNations == null || System.currentTimeMillis() - dateCached > expireAfter) {
                Set<DBNation> nations = DiscordUtil.parseNations(guild, filter);
                cachedNations = nations.stream().map(DBNation::getNation_id).collect(Collectors.toSet());
                dateCached = System.currentTimeMillis();
            }
            return cachedNations.contains(nation.getNation_id());
        };
    }
}
