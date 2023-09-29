package link.locutus.discord.db.entities.grant;

import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.NationFilter;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.math.ArrayUtil;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class WarchestTemplate extends AGrantTemplate<Map<ResourceType, Double>> {
    private final double[] allowancePerCity;
    private final long trackDays;
    private final boolean subtractExpenditure;
    private final long overdrawPercentCents;
    public WarchestTemplate(GuildDB db, boolean isEnabled, String name, NationFilter nationFilter, long econRole, long selfRole, int fromBracket, boolean useReceiverBracket, int maxTotal, int maxDay, int maxGranterDay, int maxGranterTotal, ResultSet rs) throws SQLException {
        this(db, isEnabled, name, nationFilter, econRole, selfRole, fromBracket, useReceiverBracket, maxTotal, maxDay, maxGranterDay, maxGranterTotal, rs.getLong("date_created"), ArrayUtil.toDoubleArray(rs.getBytes("allowance_per_city")), rs.getLong("track_days"), rs.getBoolean("subtract_expenditure"), rs.getLong("overdraw_percent_cents"),
                rs.getLong("expire"),
                rs.getBoolean("allow_ignore"));
    }

    // create new constructor  with typed parameters instead of resultset
    public WarchestTemplate(GuildDB db, boolean isEnabled, String name, NationFilter nationFilter, long econRole, long selfRole, int fromBracket, boolean useReceiverBracket, int maxTotal, int maxDay, int maxGranterDay, int maxGranterTotal, long dateCreated, double[] allowancePerCity, long trackDays, boolean subtractExpenditure, long overdrawPercentCents, long expiryOrZero, boolean allowIgnore) {
        super(db, isEnabled, name, nationFilter, econRole, selfRole, fromBracket, useReceiverBracket, maxTotal, maxDay, maxGranterDay, maxGranterTotal, dateCreated, expiryOrZero, allowIgnore);
        this.allowancePerCity = allowancePerCity;
        this.trackDays = trackDays;
        this.subtractExpenditure = subtractExpenditure;
        this.overdrawPercentCents = overdrawPercentCents;
    }

    @Override
    public String getCommandString(String name, String allowedRecipients, String econRole, String selfRole, String bracket, String useReceiverBracket, String maxTotal, String maxDay, String maxGranterDay, String maxGranterTotal, String allowExpire, String allowIgnore) {
        return CM.grant_template.create.warchest.cmd.create(name,
                allowedRecipients,
                allowancePerCity == null ? null : PnwUtil.resourcesToString(allowancePerCity),
                trackDays <= 0 ? null : trackDays + "",
                subtractExpenditure ? "true" : null,
                overdrawPercentCents <= 0 ? null : overdrawPercentCents + "",
                econRole,
                selfRole,
                bracket,
                useReceiverBracket,
                maxTotal,
                maxDay,
                maxGranterDay,
                maxGranterTotal, allowExpire, allowIgnore,
                null).toSlashCommand();
    }

    @Override
    public String toInfoString(DBNation sender, DBNation receiver, Map<ResourceType, Double> parsed) {
        StringBuilder result = new StringBuilder();
        // add the fields as "key: value"
        if (allowancePerCity != null) {
            result.append("allowance per city: " + PnwUtil.resourcesToString(allowancePerCity));
        }
        if (trackDays > 0) {
            result.append("track days: " + trackDays);
        }
        if (subtractExpenditure) {
            result.append("subtract expenditure: true");
        }
        if (overdrawPercentCents > 0) {
            result.append("overdraw percent: " + (overdrawPercentCents / 100d) + "%");
        }
        return result.toString();
    }

    public Map<ResourceType, Double> getWarchestPerCity(DBNation nation) {
        Map<ResourceType, Double> result = this.getWarchestPerCity(nation);
        if (result == null || result.isEmpty()) {
            result = getDb().getPerCityWarchest(nation);
        }
        return result;
    }

    @Override
    public Map<ResourceType, Double> parse(DBNation receiver, String value) {
        Map<ResourceType, Double> result = super.parse(receiver, value);
        Map<ResourceType, Double> perCityMax = getWarchestPerCity(receiver);
        if (result == null) {
            result = new LinkedHashMap<>(perCityMax);
        } else {
            for (Map.Entry<ResourceType, Double> entry : result.entrySet()) {
                ResourceType resource = entry.getKey();
                Double amount = entry.getValue();
                // if negative
                if (amount < 0) {
                    throw new IllegalArgumentException("Negative amount for " + resource + " in " + value);
                }
                // if greater than perCityMax
                if (amount > perCityMax.get(resource)) {
                    throw new IllegalArgumentException("Amount for " + resource + " in " + value + " is greater than per city max of " + perCityMax.get(resource));
                }
                if (resource == ResourceType.CREDITS) {
                    throw new IllegalArgumentException("Credits are not allowed in warchest grants");
                }
            }
        }

        return result;
    }

    @Override
    public String toListString() {
        StringBuilder result = new StringBuilder(super.toListString());
        if (subtractExpenditure) {
            result.append(" | expenditure");
        }
        if (allowancePerCity != null) {
            result.append(" | city=");
            result.append(PnwUtil.resourcesToString(allowancePerCity));
        }
        return result.toString();
    }

    @Override
    public TemplateTypes getType() {
        return TemplateTypes.WARCHEST;
    }

    @Override
    public List<String> getQueryFields() {
        List<String> list = getQueryFieldsBase();
        list.add("allowance_per_city");
        list.add("track_days");
        list.add("subtract_expenditure");
        list.add("overdraw_percent_cents");
        return list;
    }

    @Override
    public void setValues(PreparedStatement stmt) throws SQLException {
        stmt.setBytes(15, ArrayUtil.toByteArray(allowancePerCity));
        stmt.setLong(16, trackDays);
        stmt.setBoolean(17, subtractExpenditure);
        stmt.setLong(18, overdrawPercentCents);
    }
    @Override
    public double[] getCost(DBNation sender, DBNation receiver, Map<ResourceType, Double> parsed) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public DepositType.DepositTypeInfo getDepositType(DBNation receiver, Map<ResourceType, Double> parsed) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public String getInstructions(DBNation sender, DBNation receiver, Map<ResourceType, Double> parsed) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
    @Override
    public Class<Map<ResourceType, Double>> getParsedType() {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
