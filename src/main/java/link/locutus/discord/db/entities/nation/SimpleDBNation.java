package link.locutus.discord.db.entities.nation;

import link.locutus.discord.db.entities.DBNation;

public class SimpleDBNation extends DBNation {
    private final DBNationData data;

    public SimpleDBNation(DBNationData data) {
        this.data = data;
    }

    @Override
    public DBNationGetter data() {
        return data;
    }

    @Override
    public DBNationSetter edit() {
        return data;
    }

    public DBNationData getData() {
        return data;
    }

    @Override
    public DBNation copy() {
        return new SimpleDBNation(new DBNationData(data));
    }
}
