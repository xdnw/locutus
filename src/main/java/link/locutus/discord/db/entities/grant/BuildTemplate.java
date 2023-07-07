package link.locutus.discord.db.entities.grant;

import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.commands.manager.v2.impl.pw.NationFilter;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.offshore.Grant;

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
    public BuildTemplate(GuildDB db, boolean isEnabled, String name, NationFilter nationFilter, long econRole, long selfRole, int fromBracket, boolean useReceiverBracket, int maxTotal, int maxDay, int maxGranterDay, int maxGranterTotal, ResultSet rs) throws SQLException {
        this(db, isEnabled, name, nationFilter, econRole, selfRole, fromBracket, useReceiverBracket, maxTotal, maxDay, maxGranterDay, maxGranterTotal, rs.getBytes("build"), rs.getBoolean("use_optimal"), rs.getLong("mmr"), rs.getLong("track_days"), rs.getBoolean("allow_switch_after_offensive"));
    }

    // create new constructor  with typed parameters instead of resultset
    public BuildTemplate(GuildDB db, boolean isEnabled, String name, NationFilter nationFilter, long econRole, long selfRole, int fromBracket, boolean useReceiverBracket, int maxTotal, int maxDay, int maxGranterDay, int maxGranterTotal, byte[] build, boolean useOptimal, long mmr, long track_days, boolean allow_switch_after_offensive) {
        super(db, isEnabled, name, nationFilter, econRole, selfRole, fromBracket, useReceiverBracket, maxTotal, maxDay, maxGranterDay, maxGranterTotal);
        this.build = build;
        this.useOptimal = useOptimal;
        this.mmr = mmr;
        this.track_days = track_days;
        this.allow_switch_after_offensive = allow_switch_after_offensive;
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
        stmt.setBytes(12, build);
        stmt.setBoolean(13, useOptimal);
        stmt.setLong(14, mmr);
        stmt.setLong(15, track_days);
        stmt.setBoolean(16, allow_switch_after_offensive);
    }

    //make build template command open to members
    //will check if member has bought a city recently
    //will also check if member has used a build grant for their new city to prevent abuse
    //should probly consider dm'ing the user to use the city build grant command once the city grant command is ran
    @Override
    public List<Grant.Requirement> getDefaultRequirements(DBNation sender) {
        return super.getDefaultRequirements(sender);

        // for single:
        // require city built in the past day
        // require no build grant since the city
    }
}
