package link.locutus.discord.pnw;

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
}
