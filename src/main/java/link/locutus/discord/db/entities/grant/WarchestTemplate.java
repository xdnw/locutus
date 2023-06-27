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
    public WarchestTemplate(GuildDB db, int id, String name, NationFilter nationFilter, long econRole, long selfRole, int fromBracket, boolean useReceiverBracket, int maxTotal, int maxDay, int maxGranterDay, int maxGranterTotal, ResultSet rs) throws SQLException {
        super(db, id, name, nationFilter, econRole, selfRole, fromBracket, useReceiverBracket, maxTotal, maxDay, maxGranterDay, maxGranterTotal);
        this.allowancePerCity = ArrayUtil.toDoubleArray(rs.getBytes("allowance_per_city"));
        this.trackDays = rs.getLong("track_days");
        this.subtractExpenditure = rs.getBoolean("subtract_expenditure");
        this.overdrawPercentCents = rs.getLong("overdraw_percent_cents");
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
        stmt.setBytes(11, ArrayUtil.toByteArray(allowancePerCity));
        stmt.setLong(12, trackDays);
        stmt.setBoolean(13, subtractExpenditure);
        stmt.setLong(14, overdrawPercentCents);
    }
}
