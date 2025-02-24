package link.locutus.discord.db.entities.grant;

import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.apiv1.enums.DomesticPolicy;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.NationFilter;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.Transaction2;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.offshore.Grant;

import javax.annotation.Nullable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class CityTemplate extends AGrantTemplate<Integer> {

    private final int min_city;
    private final int max_city;

    public CityTemplate(GuildDB db, boolean isEnabled, String name, NationFilter nationFilter, long econRole, long selfRole, int fromBracket, boolean useReceiverBracket, int maxTotal, int maxDay, int maxGranterDay, int maxGranterTotal, ResultSet rs) throws SQLException {
        this(db, isEnabled, name, nationFilter, econRole, selfRole, fromBracket, useReceiverBracket, maxTotal, maxDay, maxGranterDay, maxGranterTotal, rs.getLong("date_created"), rs.getInt("min_city"), rs.getInt("max_city"),
                rs.getLong("expire"),
                rs.getLong("decay"),
                rs.getBoolean("allow_ignore"));
    }

    // create new constructor  with typed parameters instead of resultset
    public CityTemplate(GuildDB db, boolean isEnabled, String name, NationFilter nationFilter, long econRole, long selfRole, int fromBracket, boolean useReceiverBracket, int maxTotal, int maxDay, int maxGranterDay, int maxGranterTotal, long dateCreated, int min_city, int max_city, long expiryOrZero, long decayOrZero, boolean allowIgnore) {
        super(db, isEnabled, name, nationFilter, econRole, selfRole, fromBracket, useReceiverBracket, maxTotal, maxDay, maxGranterDay, maxGranterTotal, dateCreated, expiryOrZero, decayOrZero, allowIgnore, -1);
        this.min_city = min_city;
        this.max_city = max_city;
    }

    @Override
    public String toListString() {
        return super.toListString() + " | c" + min_city + "-" + max_city;
    }

    @Override
    public TemplateTypes getType() {
        return TemplateTypes.CITY;
    }

    @Override
    public List<String> getQueryFields() {
        List<String> list = getQueryFieldsBase();
        list.add("min_city");
        list.add("max_city");
        return list;
    }

    @Override
    public void setValues(PreparedStatement stmt) throws SQLException {
        stmt.setInt(17, min_city);
        stmt.setInt(18, max_city);
    }

    @Override
    public String getCommandString(String name, String allowedRecipients, String econRole, String selfRole, String bracket, String useReceiverBracket, String maxTotal, String maxDay, String maxGranterDay, String maxGranterTotal, String allowExpire, String allowDecay, String allowIgnore, String repeatable) {
        return CM.grant_template.create.city.cmd.name(name).allowedRecipients(
        allowedRecipients).minCity(
                min_city + "").maxCity(
                max_city + "").econRole(
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
                allowIgnore).toString();
    }

    @Override
    public String toInfoString(DBNation sender, DBNation receiver,  Integer parsed) {
        StringBuilder message = new StringBuilder();
        message.append("Min City: `" + min_city + "`\n");
        message.append("Max City: `" + max_city + "`\n");
        if (parsed != null && parsed != 1) {
            message.append("\nAmount: `" + parsed + "`");
        }

        return message.toString();
    }

    //add flags to the template database
    @Override
    public List<Grant.Requirement> getDefaultRequirements(GuildDB db, @Nullable DBNation sender, @Nullable DBNation receiver, Integer amount, boolean confirm) {
        List<Grant.Requirement> list = super.getDefaultRequirements(db, sender, receiver, amount, confirm);
        list.addAll(getRequirements(db, sender, receiver, this, amount));
        return list;
    }

    public static List<Grant.Requirement> getRequirements(GuildDB db, DBNation sender, DBNation receiver, CityTemplate template, Integer parsed) {
        if (parsed == null) parsed = 1;
        List<Grant.Requirement> list = new ArrayList<>();

        // amount cannot be <= 0
        int finalAmount = parsed;
        list.add(new Grant.Requirement("Amount must be greater than 0 not `" + (template == null ? "`{amount}`" : finalAmount) + "`", false, f -> finalAmount > 0));

        // 0 - 10 (can buy up to 10 with amount)
        // 10+ = 1 amount max
        list.add(new Grant.Requirement("Must not exceed 1 city at a time past city 20", false, f -> {
            if(f.getCities() < 20)
                return finalAmount <= 20 - f.getCities();
            else
                return finalAmount <= 1;
        }));

        int currentCities = receiver == null ? 0 : receiver.getCities();
        list.add(new Grant.Requirement("Nation must NOT purchase a city whilst this grant is being sent. Please try again", false, f -> f.getCities() == currentCities));

        list.add(new Grant.Requirement("Nation must have at least " + (template == null ? "`{min_city}`" : template.min_city) + " cities (inclusive)", false, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation receiver) {
                if (template == null) return true;
                return receiver.getCities() >= template.min_city;
            }
        }));

        list.add(new Grant.Requirement("Nation must have below " + (template == null ? "`{max_city}`" : template.max_city) + " cities", false, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation receiver) {
                if (template == null) return true;
                return receiver.getCities() < template.max_city;
            }
        }));

        // no city timer
        list.add(new Grant.Requirement("Nation must NOT have a city timer", false, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation receiver) {
                return receiver.getCityTurns() <= 0;
            }
        }));

//        //c11 or higher and no UP
//        list.add(new Grant.Requirement("Must have the project: `" + Projects.URBAN_PLANNING + "` (when c" + Projects.URBAN_PLANNING.requiredCities() + " or higher)", true, new Function<DBNation, Boolean>() {
//            @Override
//            public Boolean apply(DBNation receiver) {
//                if(receiver.getCities() < Projects.URBAN_PLANNING.requiredCities())
//                    return true;
//
//                return receiver.hasProject(Projects.URBAN_PLANNING);
//            }
//        }));
//
//        //c16 or higher and no AUP
//        list.add(new Grant.Requirement("Must have the project: `" + Projects.ADVANCED_URBAN_PLANNING + "` (when c" + Projects.ADVANCED_URBAN_PLANNING.requiredCities() + " or higher)", true, new Function<DBNation, Boolean>() {
//            @Override
//            public Boolean apply(DBNation receiver) {
//                if(receiver.getCities() < Projects.ADVANCED_URBAN_PLANNING.requiredCities())
//                    return true;
//
//                return receiver.hasProject(Projects.ADVANCED_URBAN_PLANNING);
//            }
//        }));
//
//        //c21 or higher and no MP
//        list.add(new Grant.Requirement("Must have the project: `" + Projects.METROPOLITAN_PLANNING + "` (when c" + Projects.METROPOLITAN_PLANNING.requiredCities() + " or higher)", true, new Function<DBNation, Boolean>() {
//            @Override
//            public Boolean apply(DBNation receiver) {
//                if(receiver.getCities() < Projects.METROPOLITAN_PLANNING.requiredCities())
//                    return true;
//
//                return receiver.hasProject(Projects.METROPOLITAN_PLANNING);
//            }
//        }));

        // require city policy
        list.add(new Grant.Requirement("Requires domestic policy to be `" + DomesticPolicy.MANIFEST_DESTINY + "`", false, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation receiver) {
                return receiver.getDomesticPolicy() == DomesticPolicy.MANIFEST_DESTINY;
            }
        }));

        // no city grant in past 10 days
        list.add(new Grant.Requirement("Must NOT have received a city grant in past 10 days", false, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation receiver) {
                long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(10);
                List<GrantTemplateManager.GrantSendRecord> recordedGrants = db.getGrantTemplateManager().getRecordsByReceiver(receiver.getId());
                List<GrantTemplateManager.GrantSendRecord> cityGrants = recordedGrants.stream().filter(f -> f.grant_type == TemplateTypes.CITY && f.date > cutoff).toList();
                return cityGrants.isEmpty();
            }
        }));

        list.add(new Grant.Requirement("Must NOT have received a grant for that city", false, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation nation) {
                List<Transaction2> transactions = nation.getTransactions(-1, true);
                return !Grant.hasGrantedCity(nation, transactions, currentCities + 1);
            }
        }));

        return list;
    }

    @Override
    public Integer parse(DBNation receiver, String value) {
        if (value == null) return 1;

        Integer result = super.parse(receiver, value);
        if (result == null) result = 1;
        int currentCities = receiver.getCities();
        int maxToGrant = currentCities < 10 ? 10 - currentCities : 1;
        if (result > maxToGrant) {
            throw new IllegalArgumentException("Cannot grant more than " + maxToGrant + " cities");
        }
        if (result <= 0) {
            throw new IllegalArgumentException("Must grant at least 1 city");
        }
        if (currentCities + result > max_city) {
            throw new IllegalArgumentException("Cannot grant more than " + max_city + " cities");
        }
        return result;
    }

    @Override
    public double[] getCost(GuildDB db, DBNation sender, DBNation receiver, Integer amount) {
        if (amount == null) amount = 1;
        int cities = receiver.getCities();
        double cost = PW.City.nextCityCost(receiver, amount);
        return ResourceType.MONEY.toArray(cost);
    }

    @Override
    public DepositType.DepositTypeInfo getDepositType(DBNation receiver, Integer amount) {
        if (amount == null) amount = 1;
        return DepositType.CITY.withAmount(receiver.getCities() + amount);
    }

    @Override
    public Class<Integer> getParsedType() {
        return Integer.class;
    }

    @Override
    public String getInstructions(DBNation sender, DBNation receiver, Integer parsed) {
        if (parsed != null && parsed > 1) {
            return "Go to: " + Settings.PNW_URL() + "/city/create/\n" +
                    "And buy " + parsed + " cities";
        }
        return "Go to: " + Settings.PNW_URL() + "/city/create/\n" +
                "And buy city your next city";
    }
}
