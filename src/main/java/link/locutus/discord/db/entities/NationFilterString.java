package link.locutus.discord.db.entities;

import it.unimi.dsi.fastutil.ints.Int2BooleanOpenHashMap;
import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.LocalValueStore;
import link.locutus.discord.commands.manager.v2.impl.pw.NationFilter;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.NationPlaceholders;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.Map;
import java.util.function.Predicate;

public class NationFilterString implements NationFilter {
    private final String filter;
    private final long guildId;
    private final long userId;
    private final int nationId;
    private Predicate<DBNation> predicate;
    private Predicate<DBNation> cached;
    private long cacheDate;
    private long recalcDate;

    public NationFilterString(String filter, Guild guild, User user, DBNation nation) {
        this.guildId = guild == null ? 0 : guild.getIdLong();
        this.userId = user == null ? 0 : user.getIdLong();
        this.nationId = nation == null ? 0 : nation.getNation_id();
        this.filter = filter;
        this.predicate = recalculate(guild, user, nation);
        this.recalcDate = System.currentTimeMillis();
    }

    @Override
    public NationFilter recalculate(long time) {
        long now = System.currentTimeMillis();
        if (now - recalcDate < time) {
            return this;
        }
        recalcDate = now;
        return recalculate();
    }

    @Override
    public NationFilterString recalculate() {
        Guild guild = guildId == 0 ? null : Locutus.imp().getDiscordApi().getGuildById(guildId);
        User user = userId == 0 ? null : DiscordUtil.getUser(userId);
        DBNation nation = nationId == 0 ? null : DBNation.getById(nationId);
        this.predicate = recalculate(guild, user, nation);
        return this;
    }

    private Predicate<DBNation> recalculate(Guild guild, User user, DBNation nation) {
        NationPlaceholders placeholders = Locutus.cmd().getV2().getNationPlaceholders();
        LocalValueStore store = placeholders.createLocals(guild, user, nation);
        return placeholders.parseFilter(store, filter);
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
