package link.locutus.discord.db.entities.grant;

import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.commands.manager.v2.impl.pw.NationFilter;
import link.locutus.discord.db.GuildDB;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class RawsTemplate extends AGrantTemplate{
    //long days
    //long overdraw_percent_cents
    private final long days;
    private final long overdrawPercentCents;
    public RawsTemplate(GuildDB db, int id, String name, NationFilter nationFilter, long econRole, long selfRole, int fromBracket, boolean useReceiverBracket, int maxTotal, int maxDay, int maxGranterDay, int maxGranterTotal, ResultSet rs) throws SQLException {
        super(db, id, name, nationFilter, econRole, selfRole, fromBracket, useReceiverBracket, maxTotal, maxDay, maxGranterDay, maxGranterTotal);
        this.days = rs.getLong("days");
        this.overdrawPercentCents = rs.getLong("overdraw_percent_cents");
    }

    @Override
    public TemplateTypes getType() {
        return TemplateTypes.RAWS;
    }

    @Override
    public List<String> getQueryFields() {
        List<String> list = getQueryFieldsBase();
        list.add("days");
        list.add("overdraw_percent_cents");
        return list;
    }

    @Override
    public void setValues(PreparedStatement stmt) throws SQLException {
        stmt.setLong(11, days);
        stmt.setLong(12, overdrawPercentCents);
    }
}
