package link.locutus.discord.db.entities.grant;

import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.commands.manager.v2.impl.pw.NationFilter;
import link.locutus.discord.db.GuildDB;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class BuildTemplate extends AGrantTemplate{
    private final byte[] build;
    private final boolean useOptimal;
    private final long mmr;
    private final long track_days;
    private final boolean allow_switch_after_offensive;
    public BuildTemplate(GuildDB db, int id, String name, NationFilter nationFilter, long econRole, long selfRole, int fromBracket, boolean useReceiverBracket, int maxTotal, int maxDay, int maxGranterDay, int maxGranterTotal, ResultSet rs) throws SQLException {
        super(db, id, name, nationFilter, econRole, selfRole, fromBracket, useReceiverBracket, maxTotal, maxDay, maxGranterDay, maxGranterTotal);
        this.build = rs.getBytes("build");
        this.useOptimal = rs.getBoolean("use_optimal");
        this.mmr = rs.getLong("mmr");
        this.track_days = rs.getLong("track_days");
        this.allow_switch_after_offensive = rs.getBoolean("allow_switch_after_offensive");
    }

    @Override
    public TemplateTypes getType() {
        return TemplateTypes.BUILD;
    }

    @Override
    public List<String> getQueryFields() {
        List<String> list = getQueryFieldsBase();
        list.add("build");
        list.add("use_optimal");
        list.add("mmr");
        list.add("track_days");
        list.add("allow_switch_after_offensive");
        return list;
    }

    @Override
    public void setValues(PreparedStatement stmt) throws SQLException {
        stmt.setBytes(11, build);
        stmt.setBoolean(12, useOptimal);
        stmt.setLong(13, mmr);
        stmt.setLong(14, track_days);
        stmt.setBoolean(15, allow_switch_after_offensive);
    }
}
