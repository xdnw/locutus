package link.locutus.discord.db.entities;

import it.unimi.dsi.fastutil.ints.Int2BooleanOpenHashMap;
import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.LocalValueStore;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.impl.pw.NationFilter;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.NationPlaceholders;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.math.ArrayUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class NationFilterString implements NationFilter {
    private final String filter;
    private final Predicate<DBNation> predicate;
    private Predicate<DBNation> cached;
    private long cacheDate;

    public NationFilterString(String filter, Guild guild, User user, DBNation nation) {
        NationPlaceholders placeholders = Locutus.cmd().getV2().getNationPlaceholders();
        LocalValueStore store = placeholders.createLocals(guild, user, nation);
        this.predicate = placeholders.parseFilter(store, filter);

        this.filter = filter;
    }

    @Override
    public String getFilter() {
        return filter;
    }

    @Override
    public boolean test(DBNation nation) {
        return predicate.test(nation);
    }

    @Override
    public Predicate<DBNation> toCached(long expireAfter) {
        long now = System.currentTimeMillis();
        Predicate<DBNation> tmp = this.cached;
        if (tmp != null && now - cacheDate < expireAfter) {
            return tmp;
        }
        tmp = new Predicate<>() {
            private final Map<Integer, Boolean> result = new Int2BooleanOpenHashMap();

            @Override
            public boolean test(DBNation nation) {
                int nationId = nation.getNation_id();
                Boolean existing = result.get(nationId);
                if (existing != null) {
                    return existing;
                }
                existing = predicate.test(nation);
                result.put(nationId, existing);
                return existing;
            }
        };
        cached = tmp;
        cacheDate = now;
        return tmp;
    }
}
