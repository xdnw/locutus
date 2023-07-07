package link.locutus.discord.db.entities.grant;

import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.apiv1.enums.DomesticPolicy;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.commands.manager.v2.impl.pw.NationFilter;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.offshore.Grant;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.function.Function;

public class InfraTemplate extends AGrantTemplate{
    private final long level;
    private final boolean onlyNewCities;
    private final boolean track_days;
    private final long require_n_offensives;
    private final boolean allow_rebuild;

    public InfraTemplate(GuildDB db, boolean isEnabled, String name, NationFilter nationFilter, long econRole, long selfRole, int fromBracket, boolean useReceiverBracket, int maxTotal, int maxDay, int maxGranterDay, int maxGranterTotal, ResultSet rs) throws SQLException {
        this(db, isEnabled, name, nationFilter, econRole, selfRole, fromBracket, useReceiverBracket, maxTotal, maxDay, maxGranterDay, maxGranterTotal, rs.getLong("level"), rs.getBoolean("only_new_cities"), rs.getBoolean("track_days"), rs.getLong("require_n_offensives"), rs.getBoolean("allow_rebuild"));
    }

    // create new constructor  with typed parameters instead of resultset
    public InfraTemplate(GuildDB db, boolean isEnabled, String name, NationFilter nationFilter, long econRole, long selfRole, int fromBracket, boolean useReceiverBracket, int maxTotal, int maxDay, int maxGranterDay, int maxGranterTotal, long level, boolean onlyNewCities, boolean track_days, long require_n_offensives, boolean allow_rebuild) {
        super(db, isEnabled, name, nationFilter, econRole, selfRole, fromBracket, useReceiverBracket, maxTotal, maxDay, maxGranterDay, maxGranterTotal);
        this.level = level;
        this.onlyNewCities = onlyNewCities;
        this.track_days = track_days;
        this.require_n_offensives = require_n_offensives;
        this.allow_rebuild = allow_rebuild;
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
        stmt.setLong(12, level);
        stmt.setBoolean(13, onlyNewCities);
        stmt.setBoolean(14, track_days);
        stmt.setLong(15, require_n_offensives);
        stmt.setBoolean(16, allow_rebuild);
    }

    @Override
    public List<Grant.Requirement> getDefaultRequirements(DBNation sender) {
        List<Grant.Requirement> list = super.getDefaultRequirements(sender);

        //no COCE
        list.add(new Grant.Requirement("Requires the project: " + Projects.CENTER_FOR_CIVIL_ENGINEERING, true, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation receiver) {

                return receiver.hasProject(Projects.CENTER_FOR_CIVIL_ENGINEERING);
            }
        }));

        // require infra policy
        list.add(new Grant.Requirement("Requires domestic policy to be " + DomesticPolicy.URBANIZATION, false, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation receiver) {
                return receiver.getDomesticPolicy() == DomesticPolicy.URBANIZATION;
            }
        }));

        return list;
    }
}
