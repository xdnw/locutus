package link.locutus.discord.pnw;

import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.db.entities.DBNation;

import java.util.Collection;

public class SimpleNationList implements NationList {
    private final Collection<DBNation> nations;
    public String filter;

    public SimpleNationList(Collection<DBNation> nations) {
        this.nations = nations;
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
