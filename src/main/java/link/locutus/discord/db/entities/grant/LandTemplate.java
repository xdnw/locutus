package link.locutus.discord.db.entities.grant;

import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.apiv1.enums.DomesticPolicy;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.commands.manager.v2.impl.pw.NationFilter;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.offshore.Grant;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.function.Function;

public class LandTemplate extends AGrantTemplate{
    //long level
    //boolean onlyNewCities
    private final long level;
    private final boolean onlyNewCities;
    public LandTemplate(GuildDB db, boolean isEnabled, String name, NationFilter nationFilter, long econRole, long selfRole, int fromBracket, boolean useReceiverBracket, int maxTotal, int maxDay, int maxGranterDay, int maxGranterTotal, ResultSet rs) throws SQLException {
        this(db, isEnabled, name, nationFilter, econRole, selfRole, fromBracket, useReceiverBracket, maxTotal, maxDay, maxGranterDay, maxGranterTotal, rs.getLong("level"), rs.getBoolean("only_new_cities"));
    }

    // create new constructor  with typed parameters instead of resultset
    public LandTemplate(GuildDB db, boolean isEnabled, String name, NationFilter nationFilter, long econRole, long selfRole, int fromBracket, boolean useReceiverBracket, int maxTotal, int maxDay, int maxGranterDay, int maxGranterTotal, long level, boolean onlyNewCities) {
        super(db, isEnabled, name, nationFilter, econRole, selfRole, fromBracket, useReceiverBracket, maxTotal, maxDay, maxGranterDay, maxGranterTotal);
        this.level = level;
        this.onlyNewCities = onlyNewCities;
    }

    @Override
    public TemplateTypes getType() {
        return TemplateTypes.LAND;
    }

    @Override
    public List<String> getQueryFields() {
        List<String> list = getQueryFieldsBase();
        list.add("level");
        list.add("only_new_cities");
        return list;
    }

    @Override
    public void setValues(PreparedStatement stmt) throws SQLException {
        stmt.setLong(12, level);
        stmt.setBoolean(13, onlyNewCities);
    }

    @Override
    public List<Grant.Requirement> getDefaultRequirements(DBNation sender) {
        List<Grant.Requirement> list = super.getDefaultRequirements(sender);

        //nation does not have ALA
        list.add(new Grant.Requirement("Missing the project: " + Projects.ARABLE_LAND_AGENCY, true, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation receiver) {

                return receiver.hasProject(Projects.ARABLE_LAND_AGENCY);
            }
        }));

        //nation does not have AEC
        list.add(new Grant.Requirement("Missing the project: " + Projects.ADVANCED_ENGINEERING_CORPS, true, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation receiver) {

                return receiver.hasProject(Projects.ADVANCED_ENGINEERING_CORPS);
            }
        }));

        list.add(new Grant.Requirement("Nation hasn't bought a city in the past 10 days", true, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation receiver) {

                if(onlyNewCities)
                    return receiver.getCitiesSince(TimeUtil.getTimeFromTurn(TimeUtil.getTurn() - 120)) > 0;
                else
                    return true;
            }
        }));


        // require land policy
        list.add(new Grant.Requirement("Requires domestic policy to be " + DomesticPolicy.RAPID_EXPANSION, true, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation receiver) {
                return receiver.getDomesticPolicy() != DomesticPolicy.RAPID_EXPANSION;
            }
        }));

        return list;
    }
}
