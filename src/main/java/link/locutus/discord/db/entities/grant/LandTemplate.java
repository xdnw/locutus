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
import link.locutus.discord.pnw.json.CityBuild;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.offshore.Grant;

import javax.annotation.Nullable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class LandTemplate extends AGrantTemplate<Double>{
    private final long level;
    private final boolean onlyNewCities;
    public LandTemplate(GuildDB db, boolean isEnabled, String name, NationFilter nationFilter, long econRole, long selfRole, int fromBracket, boolean useReceiverBracket, int maxTotal, int maxDay, int maxGranterDay, int maxGranterTotal, ResultSet rs) throws SQLException {
        this(db, isEnabled, name, nationFilter, econRole, selfRole, fromBracket, useReceiverBracket, maxTotal, maxDay, maxGranterDay, maxGranterTotal, rs.getLong("date_created"), rs.getLong("level"), rs.getBoolean("only_new_cities"),
                rs.getLong("expire"),
                rs.getLong("decay"),
                rs.getBoolean("allow_ignore"),
                rs.getBoolean("repeatable"));
    }

    // create new constructor  with typed parameters instead of resultset
    public LandTemplate(GuildDB db, boolean isEnabled, String name, NationFilter nationFilter, long econRole, long selfRole, int fromBracket, boolean useReceiverBracket, int maxTotal, int maxDay, int maxGranterDay, int maxGranterTotal, long dateCreated, long level, boolean onlyNewCities, long expiryOrZero, long decayOrZero, boolean allowIgnore, boolean repeatable) {
        super(db, isEnabled, name, nationFilter, econRole, selfRole, fromBracket, useReceiverBracket, maxTotal, maxDay, maxGranterDay, maxGranterTotal, dateCreated, expiryOrZero, decayOrZero, allowIgnore, repeatable);
        this.level = level;
        this.onlyNewCities = onlyNewCities;
    }

    @Override
    public TemplateTypes getType() {
        return TemplateTypes.LAND;
    }

    @Override
    public String toListString() {
        StringBuilder result = new StringBuilder(super.toListString() + " | @" + MathMan.format(level));
        if (onlyNewCities) {
            result.append(" | new_cities=true");
        }
        return result.toString();
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
        stmt.setLong(17, level);
        stmt.setBoolean(18, onlyNewCities);
    }

    @Override
    public String toInfoString(DBNation sender, DBNation receiver,  Double parsed) {

        StringBuilder message = new StringBuilder();
        message.append("level: `" + level + "`\n");
        message.append("Only New Cities: `" + onlyNewCities + "`\n");
        if (parsed != null && parsed.longValue() != (level)) {
            message.append("Amount granted: `" + MathMan.format(parsed) + "`");
        }

        return message.toString();
    }

    @Override
    public String getCommandString(String name, String allowedRecipients, String econRole, String selfRole, String bracket, String useReceiverBracket, String maxTotal, String maxDay, String maxGranterDay, String maxGranterTotal, String allowExpire, String allowDecay, String allowIgnore, String repeatable) {
        return CM.grant_template.create.land.cmd.name(name).allowedRecipients(
                allowedRecipients).level(
                        level + "").onlyNewCities(
                onlyNewCities ? "true" : null).econRole(
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

    public Map<Integer, Double> getTopCityLandGrant(DBNation receiver) {

        List<Transaction2> transactions = receiver.getTransactions(0, true);

        Map<Integer, Double> landGrants = Grant.getLandGrantedByCity(receiver, transactions);

        return landGrants;
    }

    @Override
    public double[] getCost(GuildDB db, DBNation sender, DBNation receiver, Double parsed) {

        long cutoff = TimeUtil.getTimeFromTurn(TimeUtil.getTurn() - 119);
        Map<Integer, Double> topCity = getTopCityLandGrant(receiver);
        long cost = 0;


        for (Map.Entry<Integer, DBCity> entry : receiver._getCitiesV3().entrySet()) {
            DBCity city = entry.getValue();
            double land = topCity.getOrDefault(entry.getKey(), 0D);

            if(land > city.getLand())
                throw new IllegalArgumentException("The nation with the id: " + receiver.getId() + " has already received a land grant of " + land + " land and shouldn't get the land grant of " + parsed);

            if (city.created > cutoff) {
                cost += receiver.landCost(Math.max(topCity.getOrDefault(entry.getKey(), 0D), city.getLand()), parsed);
            }
        }

        return ResourceType.MONEY.toArray(cost);
    }

    @Override
    public DepositType.DepositTypeInfo getDepositType(DBNation receiver, Double parsed) {
        return DepositType.LAND.withValue(parsed.longValue(), onlyNewCities ? 1 : receiver.getCities());
    }

    @Override
    public String getInstructions(DBNation sender, DBNation receiver, Double parsed) {
        StringBuilder message = new StringBuilder();
        message.append("**If you have VIP**: ");
        message.append("Go to: https://politicsandwar.com/cities/mass-land-purchase/\nAnd enter: " + parsed);
        message.append("\n");
        message.append("**If you don't have VIP**: ");
        message.append("Go to: https://politicsandwar.com/cities/\nAnd get each city to " + parsed + " land");

        return  message.toString();
    }

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
    public Class<Double> getParsedType() {
        return Double.class;

    }

    @Override
    public List<Grant.Requirement> getDefaultRequirements(GuildDB db, @Nullable DBNation sender, @Nullable DBNation receiver, Double parsed) {
        List<Grant.Requirement> list = super.getDefaultRequirements(db, sender, receiver, parsed);
        list.addAll(getRequirements(db, sender, receiver, this, parsed));
        return list;
    }

    public static List<Grant.Requirement> getRequirements(GuildDB db, DBNation sender, DBNation receiver, LandTemplate template, Double parsed) {
        if (parsed == null && template != null) parsed = (double) template.level;

        List<Grant.Requirement> list = new ArrayList<>();

        Double finalParsed = parsed;
        list.add(new Grant.Requirement("Land granted must NOT exceed: " + (template == null ? "`{level}`" : template.level), false, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation nation) {
                return finalParsed == null || finalParsed.longValue() <= template.level;
            }
        }));

        //nation does not have ALA
        list.add(new Grant.Requirement("Requires the project: `" + Projects.ARABLE_LAND_AGENCY + "`", true, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation receiver) {
                return receiver.hasProject(Projects.ARABLE_LAND_AGENCY);
            }
        }));

        //nation does not have AEC
        list.add(new Grant.Requirement("Requires the project: `" + Projects.ADVANCED_ENGINEERING_CORPS + "`", true, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation receiver) {
                return receiver.hasProject(Projects.ADVANCED_ENGINEERING_CORPS);
            }
        }));

        list.add(new Grant.Requirement("Must have purchased a city in the past 10 days (when `onlyNewCities: True`)", true, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation receiver) {
                if(template != null && template.onlyNewCities)
                    return receiver.getCitiesSince(TimeUtil.getTimeFromTurn(TimeUtil.getTurn() - 120)) > 0;
                else
                    return true;
            }
        }));


        // require land policy
        list.add(new Grant.Requirement("Requires domestic policy to be `" + DomesticPolicy.RAPID_EXPANSION + "`", true, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation receiver) {
                return receiver.getDomesticPolicy() == DomesticPolicy.RAPID_EXPANSION;
            }
        }));


        return list;
    }
}
