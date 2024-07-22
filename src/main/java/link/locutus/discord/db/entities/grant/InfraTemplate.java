package link.locutus.discord.db.entities.grant;

import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.apiv1.enums.DomesticPolicy;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.NationFilter;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBCity;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.Transaction2;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.offshore.Grant;

import javax.annotation.Nullable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class InfraTemplate extends AGrantTemplate<Double>{
    private final long level;
    private final boolean onlyNewCities;
    private final int require_n_offensives;
    private final boolean allow_rebuild;

    public InfraTemplate(GuildDB db, boolean isEnabled, String name, NationFilter nationFilter, long econRole, long selfRole, int fromBracket, boolean useReceiverBracket, int maxTotal, int maxDay, int maxGranterDay, int maxGranterTotal, ResultSet rs) throws SQLException {
        this(db, isEnabled, name, nationFilter, econRole, selfRole, fromBracket, useReceiverBracket, maxTotal, maxDay, maxGranterDay, maxGranterTotal, rs.getLong("date_created"), rs.getLong("level"), rs.getBoolean("only_new_cities"), rs.getInt("require_n_offensives"), rs.getBoolean("allow_rebuild"),
                rs.getLong("expire"),
                rs.getLong("decay"),
                rs.getBoolean("allow_ignore"),
                rs.getLong("repeatable"));
    }

    // create new constructor  with typed parameters instead of resultset
    public InfraTemplate(GuildDB db, boolean isEnabled, String name, NationFilter nationFilter, long econRole, long selfRole, int fromBracket, boolean useReceiverBracket, int maxTotal, int maxDay, int maxGranterDay, int maxGranterTotal, long dateCreated, long level, boolean onlyNewCities, int require_n_offensives, boolean allow_rebuild, long expiryOrZero, long decayOrZero, boolean allowIgnore, long repeatable_time) {
        super(db, isEnabled, name, nationFilter, econRole, selfRole, fromBracket, useReceiverBracket, maxTotal, maxDay, maxGranterDay, maxGranterTotal, dateCreated, expiryOrZero, decayOrZero, allowIgnore, repeatable_time);
        this.level = level;
        this.onlyNewCities = onlyNewCities;
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
        list.add("require_n_offensives");
        list.add("allow_rebuild");
        return list;
    }

    @Override
    public void setValues(PreparedStatement stmt) throws SQLException {
        stmt.setLong(17, level);
        stmt.setBoolean(18, onlyNewCities);
        stmt.setLong(19, require_n_offensives);
        stmt.setBoolean(20, allow_rebuild);
    }

    @Override
    public String toInfoString(DBNation sender, DBNation receiver,  Double parsed) {

        StringBuilder message = new StringBuilder();
        message.append("level: `" + level + "`\n");
        message.append("Only New Cities: `" + onlyNewCities + "`\n");
        message.append("Require No Offensives: `" + require_n_offensives + "`\n");
        message.append("Allow Rebuild: `" + allow_rebuild + "`\n");
        if (parsed != null && parsed.longValue() != level) {
            message.append("Amount: `" + parsed + "`");
        }

        return message.toString();
    }

    @Override
    public String getCommandString(String name, String allowedRecipients, String econRole, String selfRole, String bracket, String useReceiverBracket, String maxTotal, String maxDay, String maxGranterDay, String maxGranterTotal, String allowExpire, String allowDecay, String allowIgnore, String repeatable) {
        return CM.grant_template.create.infra.cmd.name(name).allowedRecipients(allowedRecipients).level(
                level + "").onlyNewCities(
                onlyNewCities ? "true" : null).requireNOffensives(
                require_n_offensives > 0 ? "true" : null).allowRebuild(
                allow_rebuild ? "true" : null).econRole(
                econRole).selfRole(
                selfRole).bracket(
                bracket).useReceiverBracket(
                useReceiverBracket).maxTotal(
                maxTotal).maxDay(
                maxDay).maxGranterDay(
                maxGranterDay).maxGranterTotal(
                maxGranterTotal).expireTime(
                allowExpire).decayTime(
                allowDecay).allowIgnore(
                allowIgnore).repeatable(
                repeatable).toString();
    }

    @Override
    public List<Grant.Requirement> getDefaultRequirements(GuildDB db, @Nullable DBNation sender, @Nullable DBNation receiver, Double parsed) {
        List<Grant.Requirement> list = super.getDefaultRequirements(db, sender, receiver, parsed);
        list.addAll(getRequirements(db, sender, receiver, this, parsed));
        return list;
    }

    public static List<Grant.Requirement> getRequirements(GuildDB db, DBNation sender, DBNation receiver, InfraTemplate template, Double parsed) {
        if (parsed == null && template != null) parsed = (double) template.level;
        List<Grant.Requirement> list = new ArrayList<>();

        Double finalParsed = parsed;
        list.add(new Grant.Requirement("Infra granted must NOT exceed: " + (template == null ? "`{level}`" : MathMan.format(template.level)), false, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation nation) {
                if (template == null) return true;
                return finalParsed == null || finalParsed.longValue() <= template.level;
            }
        }));

        //if nation is fighting an active nation this is stronger or has nuclear research facility or missile launch pad
        list.add(new Grant.Requirement("Requires 0 wars against nations stronger or with NRF/MLP", false, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation receiver) {
                Set<DBWar> wars = receiver.getActiveWars();

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
        list.add(new Grant.Requirement("Requires the project: `" + Projects.CENTER_FOR_CIVIL_ENGINEERING.name() + "`", true, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation receiver) {
                return receiver.hasProject(Projects.CENTER_FOR_CIVIL_ENGINEERING);
            }
        }));

        //nation does not have AEC
        list.add(new Grant.Requirement("Requires the project: `" + Projects.ADVANCED_ENGINEERING_CORPS + "`", true, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation receiver) {
                return receiver.hasProject(Projects.ADVANCED_ENGINEERING_CORPS);
            }
        }));


        // require infra policy
        list.add(new Grant.Requirement("Requires domestic policy to be `" + DomesticPolicy.URBANIZATION + "`", false, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation receiver) {
                return receiver.getDomesticPolicy() == DomesticPolicy.URBANIZATION;
            }
        }));

        list.add(new Grant.Requirement("Nation must have purchased a city in the past 10 days (when `onlyNewCities: True`)", true, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation receiver) {
                if(template != null && template.onlyNewCities)
                    return receiver.getCitiesSince(TimeUtil.getTimeFromTurn(TimeUtil.getTurn() - 120)) > 0;
                else
                    return true;
            }
        }));

        return list;
    }

    public Map<Integer, Map<Long, Double>> getTopCityInfraGrant(DBNation receiver) {

        List<Transaction2> transactions = receiver.getTransactions(0, true);

        Map<Integer, Map<Long, Double>> grants = Grant.getInfraGrantsByCityByDate(receiver, transactions);

        return grants;
    }

    @Override
    public Double parse(DBNation receiver, String value) {
        if (value == null) return (double) level;
        Double result = super.parse(receiver, value);
        if (result == null) result = (double) level;
        if (result > level) {
            throw new IllegalArgumentException("Amount cannot be greater than the template level `" + result + ">" + level + "`");
        }
        if (result <= 10) {
            throw new IllegalArgumentException("Amount cannot be less than 10");
        }
        return result;
    }

    @Override
    public double[] getCost(GuildDB db, DBNation sender, DBNation receiver, Double parsed) {
        long latestAttackDate = getLatestAttackDate(receiver, require_n_offensives);
        long cutoff = onlyNewCities ? TimeUtil.getTimeFromTurn(TimeUtil.getTurn() - 119) : 0;
        Map<Integer, Map<Long, Double>> topCity = getTopCityInfraGrant(receiver);
        long cost = 0;

        for (Map.Entry<Integer, DBCity> entry : receiver._getCitiesV3().entrySet()) {
            DBCity city = entry.getValue();

            Map<Long, Double> cityGrantHistory = topCity.getOrDefault(entry.getKey(), new HashMap<>());

            if (allow_rebuild) {
                cityGrantHistory.entrySet().removeIf(f -> f.getKey() < latestAttackDate);
            }

            // get max Double from cityGrantHistory
            double max = cityGrantHistory.values().stream().mapToDouble(f -> f).max().orElse(0);

            if (max > city.getInfra())
                throw new IllegalArgumentException("The city `" + entry.getKey() + "` has received a prior grant for " + max + " infra. The city only has " + city.getInfra() + " infra currently. The last attack date is " + latestAttackDate + " allow_rebuild=" + allow_rebuild);

            max = Math.max(city.getInfra(), max);

            if (city.created > cutoff) {
                cost += receiver.infraCost(max, parsed);
            }
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
        message.append("**If you have VIP**: ");
        message.append("Go to: https://politicsandwar.com/cities/mass-infra-purchase/\nAnd enter: " + parsed);
        message.append("\n");
        message.append("**If you don't have VIP**: ");
        message.append("Go to: https://politicsandwar.com/cities/\nAnd get each city to " + parsed + " infra");

        return  message.toString();
    }

    @Override
    public Class<Double> getParsedType() {
        return Double.class;
    }
}
