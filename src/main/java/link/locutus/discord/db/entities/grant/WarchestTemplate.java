package link.locutus.discord.db.entities.grant;

import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.commands.manager.v2.impl.pw.NationFilter;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.util.math.ArrayUtil;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class WarchestTemplate extends AGrantTemplate{
    private final double[] allowancePerCity;
    private final long trackDays;
    private final boolean subtractExpenditure;
    private final long overdrawPercentCents;
    public WarchestTemplate(GuildDB db, boolean isEnabled, String name, NationFilter nationFilter, long econRole, long selfRole, int fromBracket, boolean useReceiverBracket, int maxTotal, int maxDay, int maxGranterDay, int maxGranterTotal, ResultSet rs) throws SQLException {
        this(db, isEnabled, name, nationFilter, econRole, selfRole, fromBracket, useReceiverBracket, maxTotal, maxDay, maxGranterDay, maxGranterTotal, ArrayUtil.toDoubleArray(rs.getBytes("allowance_per_city")), rs.getLong("track_days"), rs.getBoolean("subtract_expenditure"), rs.getLong("overdraw_percent_cents"));
    }

    // create new constructor  with typed parameters instead of resultset
    public WarchestTemplate(GuildDB db, boolean isEnabled, String name, NationFilter nationFilter, long econRole, long selfRole, int fromBracket, boolean useReceiverBracket, int maxTotal, int maxDay, int maxGranterDay, int maxGranterTotal, double[] allowancePerCity, long trackDays, boolean subtractExpenditure, long overdrawPercentCents) {
        super(db, isEnabled, name, nationFilter, econRole, selfRole, fromBracket, useReceiverBracket, maxTotal, maxDay, maxGranterDay, maxGranterTotal);
        this.allowancePerCity = allowancePerCity;
        this.trackDays = trackDays;
        this.subtractExpenditure = subtractExpenditure;
        this.overdrawPercentCents = overdrawPercentCents;
    }

    @Override
    public TemplateTypes getType() {
        return TemplateTypes.WARCHEST;
    }

    @Override
    public List<String> getQueryFields() {
        List<String> list = getQueryFieldsBase();
        list.add("allowance_per_city");
        list.add("track_days");
        list.add("subtract_expenditure");
        list.add("overdraw_percent_cents");
        return list;
    }

    @Override
    public void setValues(PreparedStatement stmt) throws SQLException {
        stmt.setBytes(12, ArrayUtil.toByteArray(allowancePerCity));
        stmt.setLong(13, trackDays);
        stmt.setBoolean(14, subtractExpenditure);
        stmt.setLong(15, overdrawPercentCents);
    }
}