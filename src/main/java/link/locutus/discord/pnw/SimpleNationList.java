package link.locutus.discord.pnw;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.commands.manager.v2.impl.pw.NationFilter;
import link.locutus.discord.db.entities.DBNation;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class SimpleNationList implements NationList {
    private final Collection<DBNation> nations;
    public String filter;

    public SimpleNationList(Collection<DBNation> nations) {
        this.nations = nations;
    }

    public NationFilter toFilter() {
        Set<DBNation> nations2 = new ObjectOpenHashSet<>(this.nations);
        return new NationFilter() {
            @Override
            public String getFilter() {
                return filter;
            }

            @Override
            public boolean test(DBNation nation) {
                return nations2.contains(nation);
            }
        };
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
    public Collection<DBNation> getNations() {
        return nations;
    }

    public double[] getRevenue() {
        double[] total = ResourceType.getBuffer();
        for (DBNation nation : nations) {
            total = ResourceType.add(total, nation.getRevenue());
        }
        return total;
    }
}
