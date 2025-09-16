package link.locutus.discord.db.entities.grant;

import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.apiv1.enums.DepositTypeInfo;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.NationFilter;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.Transaction2;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.offshore.Grant;
import org.jetbrains.annotations.Nullable;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class RawsTemplate extends AGrantTemplate<Integer>{
    //long days
    //long overdraw_percent_cents
    private final long days;
    private final long overdrawPercent;
    public RawsTemplate(GuildDB db, boolean isEnabled, String name, NationFilter nationFilter, long econRole, long selfRole, int fromBracket, boolean useReceiverBracket, int maxTotal, int maxDay, int maxGranterDay, int maxGranterTotal, ResultSet rs) throws SQLException {
        this(db, isEnabled, name, nationFilter, econRole, selfRole, fromBracket, useReceiverBracket, maxTotal, maxDay, maxGranterDay, maxGranterTotal, rs.getLong("date_created"), rs.getLong("days"), rs.getLong("overdraw_percent_cents"),
                rs.getLong("expire"),
                rs.getLong("decay"),
                rs.getBoolean("allow_ignore"),
                rs.getLong("repeatable"));
    }

    // create new constructor  with typed parameters instead of resultset
    public RawsTemplate(GuildDB db, boolean isEnabled, String name, NationFilter nationFilter, long econRole, long selfRole, int fromBracket, boolean useReceiverBracket, int maxTotal, int maxDay, int maxGranterDay, int maxGranterTotal, long dateCreated, long days, long overdrawPercentCents, long expiryOrZero, long decayOrZero, boolean allowIgnore, long repeatable_time) {
        super(db, isEnabled, name, nationFilter, econRole, selfRole, fromBracket, useReceiverBracket, maxTotal, maxDay, maxGranterDay, maxGranterTotal, dateCreated, expiryOrZero, decayOrZero, allowIgnore, repeatable_time);
        this.days = days;
        this.overdrawPercent = overdrawPercentCents;
    }

    @Override
    public String getCommandString(String name, String allowedRecipients, String econRole, String selfRole, String bracket, String useReceiverBracket, String maxTotal, String maxDay, String maxGranterDay, String maxGranterTotal, String allowExpire, String allowDecay, String allowIgnore, String repeatable) {
        return CM.grant_template.create.raws.cmd.name(name).allowedRecipients(
                allowedRecipients).days(
                days + "").overdrawPercent(
                overdrawPercent <= 0 ? null : overdrawPercent + "").econRole(
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
                allowIgnore).repeatable_time(
                getRepeatable() <= 0 ? null : TimeUtil.secToTime(TimeUnit.MILLISECONDS, getRepeatable())).toString();
    }

    @Override
    public List<Grant.Requirement> getDefaultRequirements(GuildDB db, @Nullable DBNation sender, @Nullable DBNation receiver, Integer parsed, boolean confirm) {
        List<Grant.Requirement> list = super.getDefaultRequirements(db, sender, receiver, parsed, confirm);
        list.addAll(getRequirements(db, sender, receiver, this, parsed));
        return list;
    }

    public static List<Grant.Requirement> getRequirements(GuildDB db, DBNation sender, DBNation receiver, RawsTemplate template, Integer parsed) {
        List<Grant.Requirement> list = new ArrayList<>();
        list.add(new Grant.Requirement("Days granted must NOT exceed: " + (template == null ? "`{days}`" : template.days), false, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation nation) {
                return parsed == null || parsed.longValue() <= template.days;
            }
        }));
        return list;
    }

    @Override
    public String toInfoString(DBNation sender, DBNation receiver,  Integer parsed) {

        StringBuilder message = new StringBuilder();
        message.append("Days: `" + days + "`\n");
        message.append("Overdraw Percentage: `" + overdrawPercent + "`\n");
        if (parsed != null && parsed.longValue() != days) {
            message.append("Days Granted: `" + parsed + "`\n");
        }

        return message.toString();
    }

    @Override
    public String toListString() {
        return super.toListString() + " | " + days + "d";
    }

    @Override
    public TemplateTypes getType() {
        return TemplateTypes.RAWS;
    }

    @Override
    public List<String> getQueryFields() {
        List<String> list = getQueryFieldsBase();
        list.add("days");
        list.add("overdraw_percent_cents");
        return list;
    }

    @Override
    public void setValues(PreparedStatement stmt) throws SQLException {
        stmt.setLong(17, days);
        stmt.setLong(18, overdrawPercent);
    }

    @Override
    public double[] getCost(GuildDB db, DBNation sender, DBNation receiver, Integer parsed) {
        long minDate = Long.MAX_VALUE;
        long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days);
        double[] revenue = receiver.getRevenue();
        if (overdrawPercent > 0) {
            PW.multiply(revenue, 1 + (overdrawPercent / 100d));
        }
        ResourceType.ResourcesBuilder receivedBuilder = ResourceType.builder();
        if (!db.isAllianceId(receiver.getAlliance_id())) {
            throw new IllegalArgumentException("Receiver (" + receiver.getMarkdownUrl() + "/" + receiver.getAllianceUrlMarkup() + ") is not in an alliance registered to this guild. (currently: " + db.getAllianceIds() + ")");
        }
        Map<ResourceType, Double> stockpile = receiver.getStockpile();
        Map<ResourceType, Double> needed = receiver.getResourcesNeeded(stockpile, parsed, false);

        for (Transaction2 record : receiver.getTransactions(-1, true, 0L, Long.MAX_VALUE)) {
            if(record.tx_datetime > cutoff && record.note != null && record.sender_id == receiver.getId()) {
                Map<DepositType, Object> noteMap = record.getNoteMap();
                if (noteMap.containsKey(DepositType.RAWS) || noteMap.containsKey(DepositType.TAX)) {
                    minDate = Math.min(record.tx_datetime, minDate);
                    receivedBuilder.add(record.resources);
                }
            }
        }
        for (GrantTemplateManager.GrantSendRecord record : getDb().getGrantTemplateManager().getRecordsByReceiver(receiver.getId())) {
            if (record.grant_type == TemplateTypes.RAWS) {
                if(record.date > cutoff) {
                    minDate = Math.min(record.date, minDate);
                    receivedBuilder.add(record.amount);
                }
            }
        }

        if (minDate != Long.MAX_VALUE) {
            double[] totalReceived = receivedBuilder.build();
            double[] revenueOverTime = PW.multiply(revenue, (TimeUtil.getTurn() - TimeUtil.getTimeFromTurn(minDate)) / 12d);
            double[] expectedStockpile = ResourceType.subtract(totalReceived, revenueOverTime);

            for (ResourceType type : ResourceType.values) {
                double expected = expectedStockpile[type.ordinal()];
                double actual = stockpile.getOrDefault(type, 0d);

                if (needed.getOrDefault(type, 0D) > 0 && expected > actual)
                    throw new IllegalArgumentException("The nation has already received a raw grant within the past " + days + " days");
            }
        }

        return ResourceType.resourcesToArray(needed);
    }

    @Override
    public Integer parse(DBNation receiver, String value) {
        if (value == null) return (int) days;
        Integer result = super.parse(receiver, value);
        if (result == null) result = Math.toIntExact(days);
        if (result > days) {
            throw new IllegalArgumentException("Amount cannot be greater than the template days `" + result + ">" + days + "`");
        }
        if (result < 1) {
            throw new IllegalArgumentException("Amount cannot be less than 1");
        }
        return result;
    }

    @Override
    public DepositTypeInfo getDepositType(DBNation receiver, Integer parsed) {
        return DepositType.RAWS.withValue();
    }

    @Override
    public String getInstructions(DBNation sender, DBNation receiver, Integer parsed) {
        return "Go to: " + Settings.PNW_URL() + "/nation/revenue/\nAnd check your revenue to make sure it matches up with the resources sent";
    }

    @Override
    public Class<Integer> getParsedType() {
        return  Integer.class;
    }
}
