package link.locutus.discord.db.entities.grant;

import com.google.api.client.util.Sets;
import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.apiv1.enums.DomesticPolicy;
import link.locutus.discord.commands.manager.v2.impl.pw.NationFilter;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.util.offshore.Grant;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public class CityTemplate extends AGrantTemplate{

    private final int min_city;
    private final int max_city;
    public CityTemplate(GuildDB db, boolean isEnabled, String name, NationFilter nationFilter, long econRole, long selfRole, int fromBracket, boolean useReceiverBracket, int maxTotal, int maxDay, int maxGranterDay, int maxGranterTotal, ResultSet rs) throws SQLException {
        this(db, isEnabled, name, nationFilter, econRole, selfRole, fromBracket, useReceiverBracket, maxTotal, maxDay, maxGranterDay, maxGranterTotal, rs.getInt("min_city"), rs.getInt("max_city"));
    }

    // create new constructor  with typed parameters instead of resultset
    public CityTemplate(GuildDB db, boolean isEnabled, String name, NationFilter nationFilter, long econRole, long selfRole, int fromBracket, boolean useReceiverBracket, int maxTotal, int maxDay, int maxGranterDay, int maxGranterTotal, int min_city, int max_city) {
        super(db, isEnabled, name, nationFilter, econRole, selfRole, fromBracket, useReceiverBracket, maxTotal, maxDay, maxGranterDay, maxGranterTotal);
        this.min_city = min_city;
        this.max_city = max_city;
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
        stmt.setInt(12, min_city);
        stmt.setInt(13, max_city);
    }

    @Override
    public List<Grant.Requirement> getDefaultRequirements(DBNation sender) {
        List<Grant.Requirement> list = super.getDefaultRequirements(sender);

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

        // require city policy
        list.add(new Grant.Requirement("Requires domestic policy to be " + DomesticPolicy.MANIFEST_DESTINY, false, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation receiver) {
                return receiver.getDomesticPolicy() == DomesticPolicy.MANIFEST_DESTINY;
            }
        }));

        // no city in past 10 days
        list.add(new Grant.Requirement("Already received a grant for that city", false, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation receiver) {
                // get transactions matching #city
            }
        }));
        // has not received a grant for this city

        return list;
    }

    //add flags to enforce UP, AUP, MP, MD, or GSA for this template or the base template
    //add flags to the template database
}
