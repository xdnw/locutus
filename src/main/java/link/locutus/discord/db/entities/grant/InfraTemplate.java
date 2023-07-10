package link.locutus.discord.db.entities.grant;

import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.apiv1.enums.DomesticPolicy;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.commands.manager.v2.impl.pw.NationFilter;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBCity;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.offshore.Grant;
import net.dv8tion.jda.api.entities.Role;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class InfraTemplate extends AGrantTemplate<Double>{
    private final long level;
    private final boolean onlyNewCities;
    private final boolean track_days;
    private final long require_n_offensives;
    private final boolean allow_rebuild;

    public InfraTemplate(GuildDB db, boolean isEnabled, String name, NationFilter nationFilter, long econRole, long selfRole, int fromBracket, boolean useReceiverBracket, int maxTotal, int maxDay, int maxGranterDay, int maxGranterTotal, ResultSet rs) throws SQLException {
        this(db, isEnabled, name, nationFilter, econRole, selfRole, fromBracket, useReceiverBracket, maxTotal, maxDay, maxGranterDay, maxGranterTotal, rs.getLong("date_created"), rs.getLong("level"), rs.getBoolean("only_new_cities"), rs.getBoolean("track_days"), rs.getLong("require_n_offensives"), rs.getBoolean("allow_rebuild"));
    }

    // create new constructor  with typed parameters instead of resultset
    public InfraTemplate(GuildDB db, boolean isEnabled, String name, NationFilter nationFilter, long econRole, long selfRole, int fromBracket, boolean useReceiverBracket, int maxTotal, int maxDay, int maxGranterDay, int maxGranterTotal, long dateCreated, long level, boolean onlyNewCities, boolean track_days, long require_n_offensives, boolean allow_rebuild) {
        super(db, isEnabled, name, nationFilter, econRole, selfRole, fromBracket, useReceiverBracket, maxTotal, maxDay, maxGranterDay, maxGranterTotal, dateCreated);
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
    public String toListString() {
        StringBuilder result = new StringBuilder(super.toListString() + " | @" + MathMan.format(level));
        if (onlyNewCities) {
            result.append(" | new_cities=true");
        }
        if (allow_rebuild) {
            result.append(" | rebuild=true");
        }
        return result.toString();
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
        stmt.setLong(13, level);
        stmt.setBoolean(14, onlyNewCities);
        stmt.setBoolean(15, track_days);
        stmt.setLong(16, require_n_offensives);
        stmt.setBoolean(17, allow_rebuild);
    }

    @Override
    public List<Grant.Requirement> getDefaultRequirements(DBNation sender, DBNation receiver, Double amount) {
        List<Grant.Requirement> list = super.getDefaultRequirements(sender, receiver, amount);

        if (amount > level) {
            throw new IllegalArgumentException("Amount cannot be greater than the template level `" + amount + ">" + level + "`");
        }

        //if nation is fighting an active nation this is stronger or has nuclear research facility or missile launch pad
        list.add(new Grant.Requirement("Nation is fighting stronger nations or they have NRF/MLP", false, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation receiver) {

                List<DBWar> wars = receiver.getActiveWars();

                for(DBWar war : wars) {

                   DBNation opponent = war.getNation(!war.isAttacker(receiver));

                   if(opponent.active_m() > 1440)
                       continue;

                   if(opponent.hasProject(Projects.NUCLEAR_RESEARCH_FACILITY) || opponent.hasProject(Projects.MISSILE_LAUNCH_PAD))
                       return false;

                   if(opponent.getGroundStrength(true, false) > receiver.getGroundStrength(true, false))
                       return false;

                    if(opponent.getAircraft() > receiver.getAircraft())
                        return false;

                    if(opponent.getShips() > receiver.getShips())
                        return false;
                }

                return true;
            }
        }));

        //nation does not have COCE
        list.add(new Grant.Requirement("Missing the project: " + Projects.CENTER_FOR_CIVIL_ENGINEERING, true, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation receiver) {

                return receiver.hasProject(Projects.CENTER_FOR_CIVIL_ENGINEERING);
            }
        }));

        //nation does not have AEC
        list.add(new Grant.Requirement("Missing the project: " + Projects.ADVANCED_ENGINEERING_CORPS, true, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation receiver) {

                return receiver.hasProject(Projects.ADVANCED_ENGINEERING_CORPS);
            }
        }));


        // require infra policy
        list.add(new Grant.Requirement("Requires domestic policy to be " + DomesticPolicy.URBANIZATION, false, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation receiver) {
                return receiver.getDomesticPolicy() == DomesticPolicy.URBANIZATION;
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

        return list;
    }

    @Override
    public double[] getCost(DBNation sender, DBNation receiver, Double parsed) {

        long cost = 0;

        if(onlyNewCities) {

            long cutoff = TimeUtil.getTimeFromTurn(TimeUtil.getTurn() - 119);
            for (Map.Entry<Integer, DBCity> entry : receiver._getCitiesV3().entrySet()) {
                DBCity city = entry.getValue();
                if (city.created > cutoff) {
                    cost += receiver.infraCost(city.infra, parsed);
                }
            }

            return ResourceType.MONEY.toArray(cost);
        }

        for (Map.Entry<Integer, DBCity> entry : receiver._getCitiesV3().entrySet()) {
            DBCity city = entry.getValue();
            cost += receiver.infraCost(city.infra, parsed);
        }

        return ResourceType.MONEY.toArray(cost);
    }

    @Override
    public DepositType.DepositTypeInfo getDepositType(DBNation receiver, Double parsed) {
        return DepositType.INFRA.withValue(parsed.longValue(), onlyNewCities ? 1 : receiver.getCities());
    }

    @Override
    public String getInstructions(DBNation sender, DBNation receiver, Double parsed) {

        StringBuilder message = new StringBuilder();
        message.append("**If you have VIP**");
        message.append("Go to: https://politicsandwar.com/cities/mass-infra-purchase/\nAnd enter: " + parsed);
        message.append("");
        message.append("**If you don't have VIP**");
        message.append("Go to: https://politicsandwar.com/cities/\nAnd get each city to " + parsed + " infra");

        return  message.toString();
    }

    @Override
    public Class<Double> getParsedType() {
        return Double.class;
    }
}
