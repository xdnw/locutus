package link.locutus.discord.db.entities.grant;

import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.commands.manager.v2.impl.pw.NationFilter;
import link.locutus.discord.db.GuildDB;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class InfraTemplate extends AGrantTemplate{
    private final long level;
    private final boolean onlyNewCities;
    private final boolean track_days;
    private final long require_n_offensives;
    private final boolean allow_rebuild;

    public InfraTemplate(GuildDB db, int id, String name, NationFilter nationFilter, long econRole, long selfRole, int fromBracket, boolean useReceiverBracket, int maxTotal, int maxDay, int maxGranterDay, int maxGranterTotal, ResultSet rs) throws SQLException {
        super(db, id, name, nationFilter, econRole, selfRole, fromBracket, useReceiverBracket, maxTotal, maxDay, maxGranterDay, maxGranterTotal);
        this.level = rs.getLong("level");
        this.onlyNewCities = rs.getBoolean("only_new_cities");
        this.track_days = rs.getBoolean("track_days");
        this.require_n_offensives = rs.getLong("require_n_offensives");
        this.allow_rebuild = rs.getBoolean("allow_rebuild");
    }

    @Override
    public TemplateTypes getType() {
        return TemplateTypes.INFRA;
    }

    @Override
    public List<String> getQueryFields() {
        List<String> list = getQueryFieldsBase();
        list.add("level");
        list.add("only_new_cities");
        list.add("track_days");
        list.add("require_n_offensives");
        list.add("allow_rebuild");
        return list;
    }

    @Override
    public void setValues(PreparedStatement stmt) throws SQLException {
        stmt.setLong(11, level);
        stmt.setBoolean(12, onlyNewCities);
        stmt.setBoolean(13, track_days);
        stmt.setLong(14, require_n_offensives);
        stmt.setBoolean(15, allow_rebuild);
    }
}
