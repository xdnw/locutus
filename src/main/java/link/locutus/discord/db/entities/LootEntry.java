package link.locutus.discord.db.entities;

import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv3.enums.NationLootType;
import link.locutus.discord.util.PW;
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

    public double[] getAllianceLootValue(double score) {
        if (!isAlliance()) return ResourceType.getBuffer();
        DBAlliance aa = DBAlliance.get(getId());
        if (aa == null) return ResourceType.getBuffer();
        double aaScore = aa.getScore();
        if (aaScore == 0) return ResourceType.getBuffer();

        double ratio = ((score * 10000) / aaScore) / 2d;
        double percent = Math.min(Math.min(ratio, 10000) / 30000, 0.33);
        double[] yourLoot = total_rss.clone();
        return PW.multiply(yourLoot, percent);
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
        return ResourceType.convertedTotal(getTotal_rss());
    }
}
