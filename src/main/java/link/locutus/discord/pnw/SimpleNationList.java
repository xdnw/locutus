package link.locutus.discord.pnw;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.bindings.PlaceholderCache;
import link.locutus.discord.db.entities.DBNation;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class SimpleNationList implements NationList {
    private final Set<DBNation> nations;
    public String filter;

    public SimpleNationList(Collection<DBNation> nations) {
        this.nations = nations instanceof Set ? (Set<DBNation>) nations : new ObjectOpenHashSet<>(nations);
    }

    public static SimpleNationList from(Collection<NationOrAlliance> nationOrAlliances) {
        Set<DBNation> nations = new HashSet<>();
        for (NationOrAlliance nationOrAlliance : nationOrAlliances) {
            if (nationOrAlliance.isNation()) {
                nations.add(nationOrAlliance.asNation());
            } else {
                nations.addAll(nationOrAlliance.asAlliance().getNations());
            }
        }
        return new SimpleNationList(nations);
    }

    public SimpleNationList setFilter(String filter) {
        this.filter = filter;
        return this;
    }

    @Override
    public String getFilter() {
        return filter;
    }

    @Override
    public Set<DBNation> getNations() {
        return nations;
    }

    public double[] getRevenue() {
        double[] total = ResourceType.getBuffer();
        ValueStore<DBNation> cacheStore = PlaceholderCache.createCache(nations, DBNation.class);
        for (DBNation nation : nations) {
            total = ResourceType.add(total, nation.getRevenue(cacheStore));
        }
        return total;
    }
}
