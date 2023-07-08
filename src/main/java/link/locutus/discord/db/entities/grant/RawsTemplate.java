package link.locutus.discord.db.entities.grant;

import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.commands.manager.v2.impl.pw.NationFilter;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class RawsTemplate extends AGrantTemplate<Integer>{
    //long days
    //long overdraw_percent_cents
    private final long days;
    private final long overdrawPercentCents;
    public RawsTemplate(GuildDB db, boolean isEnabled, String name, NationFilter nationFilter, long econRole, long selfRole, int fromBracket, boolean useReceiverBracket, int maxTotal, int maxDay, int maxGranterDay, int maxGranterTotal, ResultSet rs) throws SQLException {
        this(db, isEnabled, name, nationFilter, econRole, selfRole, fromBracket, useReceiverBracket, maxTotal, maxDay, maxGranterDay, maxGranterTotal, rs.getLong("days"), rs.getLong("overdraw_percent_cents"));
    }

    // create new constructor  with typed parameters instead of resultset
    public RawsTemplate(GuildDB db, boolean isEnabled, String name, NationFilter nationFilter, long econRole, long selfRole, int fromBracket, boolean useReceiverBracket, int maxTotal, int maxDay, int maxGranterDay, int maxGranterTotal, long days, long overdrawPercentCents) {
        super(db, isEnabled, name, nationFilter, econRole, selfRole, fromBracket, useReceiverBracket, maxTotal, maxDay, maxGranterDay, maxGranterTotal);
        this.days = days;
        this.overdrawPercentCents = overdrawPercentCents;
    }

    @Override
    public String toListString() {
        return super.toListString() + " | " + days + "d";
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
        stmt.setLong(12, days);
        stmt.setLong(13, overdrawPercentCents);
    }

    @Override
    public double[] getCost(DBNation sender, DBNation receiver, Integer parsed) {

    }

    @Override
    public DepositType.DepositTypeInfo getDepositType(DBNation receiver, Integer parsed) {

    }

    @Override
    public String getInstructions(DBNation sender, DBNation receiver, Integer parsed) {

    }

    @Override
    public Class<Integer> getParsedType() {

    }
}
