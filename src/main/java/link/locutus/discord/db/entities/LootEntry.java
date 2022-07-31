package link.locutus.discord.db.entities;

import link.locutus.discord.apiv3.enums.NationLootType;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.math.ArrayUtil;

import java.sql.ResultSet;
import java.sql.SQLException;

public class LootEntry {
    private final int id;
    private final double[] total_rss;
    private final long date;
    private final NationLootType type;


    public LootEntry(int id, double[] resources, long date, NationLootType type) {
        this.id = id;
        this.total_rss = resources;
        this.date = date;
        this.type = type;
    }

    public LootEntry(ResultSet rs) throws SQLException {
        this.id = rs.getInt("id");
        byte[] lootBytes = rs.getBytes("total_rss");
        total_rss = ArrayUtil.toDoubleArray(lootBytes);
        this.date = rs.getLong("date");
        this.type = NationLootType.values()[rs.getInt("type")];

    }

    public boolean isAlliance() {
        return id < 0;
    }

    public int getId() {
        return Math.abs(id);
    }

    public double[] getTotal_rss() {
        return total_rss;
    }

    public long getDate() {
        return date;
    }

    public NationLootType getType() {
        return type;
    }

    public double convertedTotal() {
        return PnwUtil.convertedTotal(getTotal_rss());
    }
}
