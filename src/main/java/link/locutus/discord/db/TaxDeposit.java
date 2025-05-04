package link.locutus.discord.db;

import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.NoFormat;
import link.locutus.discord.commands.manager.v2.binding.bindings.TypedFunction;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.math.ArrayUtil;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Date;
import java.util.Map;

public class TaxDeposit {
    public int allianceId;
    public long date;
    public int index;
    public int nationId;
    public int moneyRate;
    public int resourceRate;
    public double[] resources;
    public int internalMoneyRate;
    public int internalResourceRate;
    public int tax_id;

    public TaxDeposit(int allianceId, long date, int index, int tax_id, int nationId, int moneyRate, int resourceRate, int internalMoneyRate, int internalResourceRate, double[] resources) {
        this.allianceId = allianceId;
        this.date = date;
        this.index = index;
        this.nationId = nationId;
        this.moneyRate = moneyRate;
        this.resourceRate = resourceRate;
        this.resources = resources;
        this.internalMoneyRate = internalMoneyRate;
        this.internalResourceRate = internalResourceRate;
        this.tax_id = tax_id;
    }

    @Command(desc = "Get market value of tax record")
    public double getMarketValue() {
        return ResourceType.convertedTotal(this.resources);
    }

    @Command(desc = "The alliance class for this tax record")
    public DBAlliance getAlliance() {
        return DBAlliance.getOrCreate(allianceId);
    }

    @Command(desc = "The nation class for this tax record")
    public DBNation getNation() {
        return DBNation.getOrCreate(nationId);
    }

    @Command(desc = "Get the amount of a resource for this tax record")
    public double getAmount(ResourceType type) {
        return resources[type.ordinal()];
    }

    @Command(desc = "Get an attribute for the nation of this tax record")
    public String getNationInfo(@NoFormat TypedFunction<DBNation, String> nationFunction) {
        return nationFunction.applyCached(DBNation.getOrCreate(nationId));
    }

    @Command(desc = "Get an attribute for the alliance of this tax record")
    public String getAllianceInfo(@NoFormat TypedFunction<DBAlliance, String> allianceFunction) {
        return allianceFunction.applyCached(DBAlliance.getOrCreate(allianceId));
    }

    @Command(desc = "The alliance id of the tax deposit")
    public int getAllianceId() {
        return allianceId;
    }

    @Command(desc = "The number of turns ago this tax deposit was made")
    public long getTurnsOld() {
        return TimeUtil.getTurn() - TimeUtil.getTurn(date);
    }

    @Command(desc = "Get the date as unix timestamp in milliseconds")
    public long getDateMs() {
        return date;
    }

    @Command(desc = "Get the date formatted as dd/mm/yyyy hh")
    public String getDateStr() {
        return TimeUtil.DD_MM_YYYY_HH.format(new Date(date));
    }

    @Command(desc = "Get id of the tax record")
    public int getId() {
        return index;
    }

    @Command(desc = "Get the nation ID")
    public int getNationId() {
        return nationId;
    }

    @Command(desc = "Get the money rate")
    public int getMoneyRate() {
        return moneyRate;
    }

    @Command(desc = "Get the resource rate")
    public int getResourceRate() {
        return resourceRate;
    }

    @Command(desc = "Get the resources array")
    public double[] getResourcesArray() {
        return resources;
    }

    @Command(desc = "Get the resources map")
    public Map<ResourceType, Double> getResourcesMap() {
        return ResourceType.resourcesToMap(resources);
    }

    @Command(desc = "Get the resources json")
    public String getResourcesJson() {
        return ResourceType.toString(resources);
    }

    @Command(desc = "Get the internal money rate")
    public int getInternalMoneyRate() {
        return internalMoneyRate;
    }

    @Command(desc = "Get the internal resource rate")
    public int getInternalResourceRate() {
        return internalResourceRate;
    }

    @Command(desc = "Get the tax ID")
    public int getTaxId() {
        return tax_id;
    }

    public static TaxDeposit of(ResultSet rs) throws SQLException {
        int money = rs.getInt("moneyrate");
        int rss = rs.getInt("resoucerate");
        int id = rs.getInt("id");
        long date = rs.getLong("date");
        // round date for legacy reasons
        if (date > 1656153134000L && date < 1657449182000L) {
            date = TimeUtil.getTimeFromTurn(TimeUtil.getTurn(date));
        }

        long[] cents = ArrayUtil.toLongArray(rs.getBytes("resources"));
        double[] deposit = new double[cents.length];
        for (int i = 0; i < cents.length; i++) deposit[i] = cents[i] / 100d;

        int alliance = rs.getInt("alliance");
        int nation = rs.getInt("nation");

        short internalTaxRatePair = rs.getShort("internal_taxrate");

        byte internalMoneyRate = MathMan.unpairShortX(internalTaxRatePair);
        byte internalResourceRate = MathMan.unpairShortY(internalTaxRatePair);

        int tax_id = rs.getInt("tax_id");

        return new TaxDeposit(alliance, date, id, tax_id, nation, money, rss, internalMoneyRate, internalResourceRate, deposit);
    }

    public static TaxDeposit of(org.example.jooq.bank.tables.records.TaxDepositsDateRecord rs) {
        int money = rs.getMoneyrate();
        int rss = rs.getResoucerate();
        int id = rs.getId();
        long date = rs.getDate();
        // round date for legacy reasons
        if (date > 1656153134000L && date < 1657449182000L) {
            date = TimeUtil.getTimeFromTurn(TimeUtil.getTurn(date));
        }
        byte[] bytes = rs.getResources();
        double[] deposit = ArrayUtil.toDoubleArrayCents(bytes);

        int alliance = rs.getAlliance();
        int nation = rs.getNation();

        short internalTaxRatePair = rs.getInternalTaxrate().shortValue();

        byte internalMoneyRate = MathMan.unpairShortX(internalTaxRatePair);
        byte internalResourceRate = MathMan.unpairShortY(internalTaxRatePair);
        if (internalMoneyRate > 100 || internalMoneyRate < -1)
            throw new IllegalStateException("Internal money rate is invalid: " + internalMoneyRate + " #" + id);
        if (internalResourceRate > 100 || internalResourceRate < -1)
            throw new IllegalStateException("Internal rss rate is too high: " + internalResourceRate + " #" + id);

        int tax_id = rs.getTaxId();

        return new TaxDeposit(alliance, date, id, tax_id, nation, money, rss, internalMoneyRate, internalResourceRate, deposit);
    }

    /**
     * Money added to deposits
     *
     * @param taxBase
     * @return
     */
    public double getPctMoney(int[] taxBase) {
        return getPct(moneyRate, taxBase[0]);
    }

    /**
     * Rss added to deposits
     *
     * @param taxBase
     * @return
     */
    public double getPctResource(int[] taxBase) {
        return getPct(resourceRate, taxBase[1]);
    }

    private double getPct(double rate, int taxBase) {
        return (rate > taxBase ?
                Math.max(0, (rate - taxBase) / rate)
                : 0);
    }

    /**
     * Remainder after subtracting tax base
     *
     * @param taxBase
     */
    public void multiplyBase(int[] taxBase) {
        double pctMoney = getPctMoney(taxBase);
        double pctRss = getPctResource(taxBase);
        resources[0] *= pctMoney;
        for (int i = 1; i < resources.length; i++) {
            resources[i] *= pctRss;
        }
    }

    public void multiplyBaseInverse(int[] taxBase) {
        double pctMoney = 1 - getPctMoney(taxBase);
        double pctRss = 1 - getPctResource(taxBase);
        resources[0] *= pctMoney;
        for (int i = 1; i < resources.length; i++) {
            resources[i] *= pctRss;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TaxDeposit that = (TaxDeposit) o;

        if (allianceId != that.allianceId) return false;
        if (date != that.date) return false;
        if (nationId != that.nationId) return false;
        if (Double.compare(that.moneyRate, moneyRate) != 0) return false;
        if (Double.compare(that.resourceRate, resourceRate) != 0) return false;
        return Arrays.equals(resources, that.resources);
    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        result = allianceId;
        result = 31 * result + (int) (date ^ (date >>> 32));
        result = 31 * result + nationId;
        temp = Double.doubleToLongBits(moneyRate);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(resourceRate);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        result = 31 * result + Arrays.hashCode(resources);
        return result;
    }

    @Override
    public String toString() {
        return "TaxDeposit{" +
                "allianceId=" + allianceId +
                ", date=" + date +
                ", nationId=" + nationId +
                ", moneyRate=" + moneyRate +
                ", resourceRate=" + resourceRate +
                ", resources=" + Arrays.toString(resources) +
                '}';
    }

    public long getTurn() {
        return TimeUtil.getTurn(date);
    }
}
