package link.locutus.discord.db.entities.grant;

import com.google.api.client.util.Sets;
import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.apiv1.enums.DomesticPolicy;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.NationFilter;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.Transaction2;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.offshore.Grant;
import rocker.grant.cities;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

public class CityTemplate extends AGrantTemplate<Integer> {

    private final int min_city;
    private final int max_city;

    public CityTemplate(GuildDB db, boolean isEnabled, String name, NationFilter nationFilter, long econRole, long selfRole, int fromBracket, boolean useReceiverBracket, int maxTotal, int maxDay, int maxGranterDay, int maxGranterTotal, ResultSet rs) throws SQLException {
        this(db, isEnabled, name, nationFilter, econRole, selfRole, fromBracket, useReceiverBracket, maxTotal, maxDay, maxGranterDay, maxGranterTotal, rs.getLong("date_created"), rs.getInt("min_city"), rs.getInt("max_city"));
    }

    // create new constructor  with typed parameters instead of resultset
    public CityTemplate(GuildDB db, boolean isEnabled, String name, NationFilter nationFilter, long econRole, long selfRole, int fromBracket, boolean useReceiverBracket, int maxTotal, int maxDay, int maxGranterDay, int maxGranterTotal, long dateCreated, int min_city, int max_city) {
        super(db, isEnabled, name, nationFilter, econRole, selfRole, fromBracket, useReceiverBracket, maxTotal, maxDay, maxGranterDay, maxGranterTotal, dateCreated);
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
        stmt.setInt(13, min_city);
        stmt.setInt(14, max_city);
    }

    @Override
    public String getCommandString(String name, String allowedRecipients, String build, String mmr, String only_new_cities, String allow_after_days, String allow_after_offensive, String allow_after_infra, String allow_all, String allow_after_land_or_project, String econRole, String selfRole, String bracket, String useReceiverBracket, String maxTotal, String maxDay, String maxGranterDay, String maxGranterTotal, String force) {

        String sRole = selfRole != null ? selfRole : null;

        return CM.grant_template.create.city.cmd.create(name, allowedRecipients,  min_city + "", max_city + "", econRole, sRole, bracket, useReceiverBracket, maxTotal, maxDay, maxGranterDay, maxGranterTotal, null).toString();
    }

    @Override
    public String toFullString2(DBNation sender, DBNation receiver,  Integer parsed) {

        StringBuilder message = new StringBuilder();
        message.append("Min City: " + min_city);
        message.append("Max City: " + max_city);

        return message.toString();
    }

    //add flags to the template database
    @Override
    public List<Grant.Requirement> getDefaultRequirements(DBNation sender, DBNation receiver, Integer amount) {
        List<Grant.Requirement> list = super.getDefaultRequirements(sender, receiver, amount);

        // amount cannot be <= 0
        list.add(new Grant.Requirement("Amount must be greater than 0 not `" + amount + "`", false, f -> amount > 0));

        // 0 - 10 (can buy up to 10 with amount)
        // 10+ = 1 amount max
        list.add(new Grant.Requirement("Cannot grant more 1 city at a time past city 10", false, f -> {
            if(f.getCities() < 10)
                return amount <= 10 - f.getCities();
            else
                return amount <= 1;
        }));

        int currentCities = receiver.getCities();
        list.add(new Grant.Requirement("Nation has built a city, please run the grant command again", false, f -> f.getCities() == currentCities));

        list.add(new Grant.Requirement("Requires at least " + min_city + " cities", false, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation receiver) {
                return receiver.getCities() >= min_city;
            }
        }));

        list.add(new Grant.Requirement("Cannot grant past " + max_city + " cities", false, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation receiver) {
                return receiver.getCities() < max_city;
            }
        }));

        // no city timer
        list.add(new Grant.Requirement("Cannot have a city timer", false, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation receiver) {
                return receiver.getCityTurns() <= 0;
            }
        }));

        //c11 or higher and no UP
        list.add(new Grant.Requirement("Requires the project: " + Projects.URBAN_PLANNING, true, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation receiver) {

                if(receiver.getCities() < 11)
                    return true;

                return receiver.hasProject(Projects.URBAN_PLANNING);
            }
        }));

        //c16 or higher and no AUP
        list.add(new Grant.Requirement("Requires the project: " + Projects.ADVANCED_URBAN_PLANNING, true, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation receiver) {

                if(receiver.getCities() < 16)
                    return true;

                return receiver.hasProject(Projects.ADVANCED_URBAN_PLANNING);
            }
        }));

        //c21 or higher and no MP
        list.add(new Grant.Requirement("Requires the project: " + Projects.METROPOLITAN_PLANNING, true, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation receiver) {

                if(receiver.getCities() < 21)
                    return true;

                return receiver.hasProject(Projects.METROPOLITAN_PLANNING);
            }
        }));

        // require city policy
        list.add(new Grant.Requirement("Requires domestic policy to be " + DomesticPolicy.MANIFEST_DESTINY, false, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation receiver) {
                return receiver.getDomesticPolicy() == DomesticPolicy.MANIFEST_DESTINY;
            }
        }));

        // no city grant in past 10 days
        list.add(new Grant.Requirement("Already received a city grant in past 10 days", false, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation receiver) {
                long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(10);
                List<GrantTemplateManager.GrantSendRecord> recordedGrants = getDb().getGrantTemplateManager().getRecordsByReceiver(receiver.getId());
                List<GrantTemplateManager.GrantSendRecord> cityGrants = recordedGrants.stream().filter(f -> f.grant_type == TemplateTypes.CITY && f.date > cutoff).toList();
                return cityGrants.isEmpty();
            }
        }));

        list.add(new Grant.Requirement("Already received a grant for a city", false, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation nation) {
                List<Transaction2> transactions = nation.getTransactions(-1);
                return !Grant.hasGrantedCity(nation, transactions, currentCities + 1);
            }
        }));

        return list;
    }

    @Override
    public double[] getCost(DBNation sender, DBNation receiver, Integer amount) {
        int cities = receiver.getCities();
        double cost = PnwUtil.nextCityCost(receiver, amount);
        return ResourceType.MONEY.toArray(cost);
    }

    @Override
    public DepositType.DepositTypeInfo getDepositType(DBNation receiver, Integer amount) {
        return DepositType.CITY.withAmount(receiver.getCities() + amount);
    }

    @Override
    public Class<Integer> getParsedType() {
        return Integer.class;
    }

    @Override
    public String getInstructions(DBNation sender, DBNation receiver, Integer parsed) {
        return "Go to: https://politicsandwar.com/city/create/\nAnd buy city your next city";
    }
}
