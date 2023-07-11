package link.locutus.discord.db.entities.grant;

import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.NationFilter;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.TimeUtil;
import org.jooq.meta.derby.sys.Sys;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class RawsTemplate extends AGrantTemplate<Integer>{
    //long days
    //long overdraw_percent_cents
    private final long days;
    private final long overdrawPercentCents;
    public RawsTemplate(GuildDB db, boolean isEnabled, String name, NationFilter nationFilter, long econRole, long selfRole, int fromBracket, boolean useReceiverBracket, int maxTotal, int maxDay, int maxGranterDay, int maxGranterTotal, ResultSet rs) throws SQLException {
        this(db, isEnabled, name, nationFilter, econRole, selfRole, fromBracket, useReceiverBracket, maxTotal, maxDay, maxGranterDay, maxGranterTotal, rs.getLong("date_created"), rs.getLong("days"), rs.getLong("overdraw_percent_cents"));
    }

    // create new constructor  with typed parameters instead of resultset
    public RawsTemplate(GuildDB db, boolean isEnabled, String name, NationFilter nationFilter, long econRole, long selfRole, int fromBracket, boolean useReceiverBracket, int maxTotal, int maxDay, int maxGranterDay, int maxGranterTotal, long dateCreated, long days, long overdrawPercentCents) {
        super(db, isEnabled, name, nationFilter, econRole, selfRole, fromBracket, useReceiverBracket, maxTotal, maxDay, maxGranterDay, maxGranterTotal, dateCreated);
        this.days = days;
        this.overdrawPercentCents = overdrawPercentCents;
    }

    @Override
    public String getCommandString(String name, String allowedRecipients, String build, String mmr, String only_new_cities, String allow_after_days, String allow_after_offensive, String allow_after_infra, String allow_all, String allow_after_land_or_project, String econRole, String selfRole, String bracket, String useReceiverBracket, String maxTotal, String maxDay, String maxGranterDay, String maxGranterTotal, String force) {
        return CM.grant_template.create.raws.cmd.create(name,
                allowedRecipients,
                days + "",
                overdrawPercentCents <= 0 ? null : overdrawPercentCents + "",
                econRole,
                selfRole,
                bracket,
                useReceiverBracket,
                maxTotal,
                maxDay,
                maxGranterDay,
                maxGranterTotal,
                null).toSlashCommand();
    }

    @Override
    public String toInfoString(DBNation sender, DBNation receiver,  Integer parsed) {

        StringBuilder message = new StringBuilder();
        message.append("Days: " + days);
        message.append("Overdraw Percentage: " + overdrawPercentCents);

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
        stmt.setLong(13, days);
        stmt.setLong(14, overdrawPercentCents);
    }

    @Override
    public double[] getCost(DBNation sender, DBNation receiver, Integer parsed) {

        long minDate = Long.MAX_VALUE;
        long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days);
        double[] revenue = receiver.getRevenue();
        PnwUtil.multiply(revenue, 1.2);
        ResourceType.ResourcesBuilder receivedBuilder = ResourceType.builder();
        Map<ResourceType, Double> stockpile = receiver.getStockpile();
        Map<ResourceType, Double> needed = receiver.getResourcesNeeded(stockpile, parsed, false);

        //TODO also check transactions
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
            double[] revenueOverTime = PnwUtil.multiply(revenue, (TimeUtil.getTurn() - TimeUtil.getTimeFromTurn(minDate)) / 12d);
            double[] expectedStockpile = ResourceType.subtract(totalReceived, revenueOverTime);

            for (ResourceType type : ResourceType.values) {
                double expected = expectedStockpile[type.ordinal()];
                double actual = stockpile.getOrDefault(type, 0d);

                if (needed.getOrDefault(type, 0D) > 0 && expected > actual)
                    throw new IllegalArgumentException("The nation has already received a raw grant within the past " + days + " days");
            }
        }

        return PnwUtil.resourcesToArray(needed);
    }

    @Override
    public DepositType.DepositTypeInfo getDepositType(DBNation receiver, Integer parsed) {
        return DepositType.RAWS.withValue();
    }

    @Override
    public String getInstructions(DBNation sender, DBNation receiver, Integer parsed) {
        return "Go to: https://politicsandwar.com/nation/revenue/\nAnd check your revenue to make sure it matches up with the resources sent";
    }

    @Override
    public Class<Integer> getParsedType() {
        return  Integer.class;
    }
}
