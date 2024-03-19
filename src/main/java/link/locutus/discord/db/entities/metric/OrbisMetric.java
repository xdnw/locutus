package link.locutus.discord.db.entities.metric;

import com.politicsandwar.graphql.model.ActivityStat;
import com.politicsandwar.graphql.model.NationResourceStat;
import com.politicsandwar.graphql.model.ResourceStat;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv3.PoliticsAndWarV3;
import link.locutus.discord.db.NationDB;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.scheduler.ThrowingBiConsumer;

import java.sql.PreparedStatement;
import java.util.List;

public enum OrbisMetric {

    TOTAL_NATIONS,
    NATIONS_CREATED,
    ACTIVE_1_DAY,
    ACTIVE_2_DAYS,
    ACTIVE_3_DAYS,
    ACTIVE_1_WEEK,
    ACTIVE_1_MONTH,

    MONEY_ON_NATIONS,
    FOOD_ON_NATIONS,
    STEEL_ON_NATIONS,
    ALUMINUM_ON_NATIONS,
    GASOLINE_ON_NATIONS,
    MUNITIONS_ON_NATIONS,
    URANIUM_ON_NATIONS,
    COAL_ON_NATIONS,
    OIL_ON_NATIONS,
    IRON_ON_NATIONS,
    BAUXITE_ON_NATIONS,
    LEAD_ON_NATIONS,

    MONEY,
    FOOD,
    STEEL,
    ALUMINUM,
    GASOLINE,
    MUNITIONS,
    URANIUM,
    COAL,
    OIL,
    IRON,
    BAUXITE,
    LEAD,

    ;

    public static final OrbisMetric[] values = values();

    private final boolean isTurn;

    OrbisMetric() {
        this(false);
    }

    OrbisMetric(boolean isTurn) {
        this.isTurn = isTurn;
    }

    public static void update(NationDB db) {
        long day = db.getLatestMetricTime(false);
        if (day >= TimeUtil.getDay() - 1) return;
        PoliticsAndWarV3 api = Locutus.imp().getV3();

        long timeMs = TimeUtil.getTimeFromDay(day);
        List<ActivityStat> activity = api.getActivityStats(timeMs);
        List<ResourceStat> resources = api.getResourceStats(timeMs);
        List<NationResourceStat> nationResources = api.getNationResourceStats(timeMs);

        List<OrbisMetricValue> metricValues = new ObjectArrayList<>();
        activity.forEach(stat -> adapt(stat, metricValues));
        resources.forEach(stat -> adapt(stat, metricValues));
        nationResources.forEach(stat -> adapt(stat, metricValues));

        saveAll(metricValues, false, false);
    }

    private static void adapt(ActivityStat stat, List<OrbisMetricValue> output) {
        long day = TimeUtil.getDay(stat.getDate().toEpochMilli());
        if (stat.getTotal_nations() != null) output.add(new OrbisMetricValue(TOTAL_NATIONS, day, stat.getTotal_nations()));
        if (stat.getNations_created() != null) output.add(new OrbisMetricValue(NATIONS_CREATED, day, stat.getNations_created()));
        if (stat.getActive_1_day() != null) output.add(new OrbisMetricValue(ACTIVE_1_DAY, day, stat.getActive_1_day()));
        if (stat.getActive_2_days() != null) output.add(new OrbisMetricValue(ACTIVE_2_DAYS, day, stat.getActive_2_days()));
        if (stat.getActive_3_days() != null) output.add(new OrbisMetricValue(ACTIVE_3_DAYS, day, stat.getActive_3_days()));
        if (stat.getActive_1_week() != null) output.add(new OrbisMetricValue(ACTIVE_1_WEEK, day, stat.getActive_1_week()));
        if (stat.getActive_1_month() != null) output.add(new OrbisMetricValue(ACTIVE_1_MONTH, day, stat.getActive_1_month()));
    }

    private static void adapt(ResourceStat stat, List<OrbisMetricValue> output) {
        long day = TimeUtil.getDay(stat.getDate().toEpochMilli());
        if (stat.getMoney() != null) output.add(new OrbisMetricValue(MONEY, day, Long.parseLong(stat.getMoney())));
        if (stat.getFood() != null) output.add(new OrbisMetricValue(FOOD, day, Long.parseLong(stat.getFood())));
        if (stat.getSteel() != null) output.add(new OrbisMetricValue(STEEL, day, Long.parseLong(stat.getSteel())));
        if (stat.getAluminum() != null) output.add(new OrbisMetricValue(ALUMINUM, day, Long.parseLong(stat.getAluminum())));
        if (stat.getGasoline() != null) output.add(new OrbisMetricValue(GASOLINE, day, Long.parseLong(stat.getGasoline())));
        if (stat.getMunitions() != null) output.add(new OrbisMetricValue(MUNITIONS, day, Long.parseLong(stat.getMunitions())));
        if (stat.getUranium() != null) output.add(new OrbisMetricValue(URANIUM, day, Long.parseLong(stat.getUranium())));
        if (stat.getCoal() != null) output.add(new OrbisMetricValue(COAL, day, Long.parseLong(stat.getCoal())));
        if (stat.getOil() != null) output.add(new OrbisMetricValue(OIL, day, Long.parseLong(stat.getOil())));
        if (stat.getIron() != null) output.add(new OrbisMetricValue(IRON, day, Long.parseLong(stat.getIron())));
        if (stat.getBauxite() != null) output.add(new OrbisMetricValue(BAUXITE, day, Long.parseLong(stat.getBauxite())));
        if (stat.getLead() != null) output.add(new OrbisMetricValue(LEAD, day, Long.parseLong(stat.getLead())));
    }

    private static void adapt(NationResourceStat stat, List<OrbisMetricValue> output) {
        long day = TimeUtil.getDay(stat.getDate().toEpochMilli());
        if (stat.getMoney() != null) output.add(new OrbisMetricValue(MONEY_ON_NATIONS, day, Long.parseLong(stat.getMoney())));
        if (stat.getFood() != null) output.add(new OrbisMetricValue(FOOD_ON_NATIONS, day, Long.parseLong(stat.getFood())));
        if (stat.getSteel() != null) output.add(new OrbisMetricValue(STEEL_ON_NATIONS, day, Long.parseLong(stat.getSteel())));
        if (stat.getAluminum() != null) output.add(new OrbisMetricValue(ALUMINUM_ON_NATIONS, day, Long.parseLong(stat.getAluminum())));
        if (stat.getGasoline() != null) output.add(new OrbisMetricValue(GASOLINE_ON_NATIONS, day, Long.parseLong(stat.getGasoline())));
        if (stat.getMunitions() != null) output.add(new OrbisMetricValue(MUNITIONS_ON_NATIONS, day, Long.parseLong(stat.getMunitions())));
        if (stat.getUranium() != null) output.add(new OrbisMetricValue(URANIUM_ON_NATIONS, day, Long.parseLong(stat.getUranium())));
        if (stat.getCoal() != null) output.add(new OrbisMetricValue(COAL_ON_NATIONS, day, Long.parseLong(stat.getCoal())));
        if (stat.getOil() != null) output.add(new OrbisMetricValue(OIL_ON_NATIONS, day, Long.parseLong(stat.getOil())));
        if (stat.getIron() != null) output.add(new OrbisMetricValue(IRON_ON_NATIONS, day, Long.parseLong(stat.getIron())));
        if (stat.getBauxite() != null) output.add(new OrbisMetricValue(BAUXITE_ON_NATIONS, day, Long.parseLong(stat.getBauxite())));
        if (stat.getLead() != null) output.add(new OrbisMetricValue(LEAD_ON_NATIONS, day, Long.parseLong(stat.getLead())));
    }

    public static OrbisMetric fromResource(ResourceType type) {
        return switch (type) {
            case MONEY -> OrbisMetric.MONEY;
            case CREDITS -> null;
            case FOOD -> OrbisMetric.FOOD;
            case COAL -> OrbisMetric.COAL;
            case OIL -> OrbisMetric.OIL;
            case URANIUM -> OrbisMetric.URANIUM;
            case LEAD -> OrbisMetric.LEAD;
            case IRON -> OrbisMetric.IRON;
            case BAUXITE -> OrbisMetric.BAUXITE;
            case GASOLINE -> OrbisMetric.GASOLINE;
            case MUNITIONS -> OrbisMetric.MUNITIONS;
            case STEEL -> OrbisMetric.STEEL;
            case ALUMINUM -> OrbisMetric.ALUMINUM;
        };
    }


    public boolean isTurn() {
        return this.isTurn;
    }

    public record OrbisMetricValue(OrbisMetric metric, long turnOrDay, double value) {}
    public static void saveAll(List<OrbisMetricValue> values, boolean replace) {
        if (values.isEmpty()) return;
        List<OrbisMetricValue> dayValues = values.stream().filter(value -> !value.metric.isTurn()).toList();
        List<OrbisMetricValue> turnValues = values.stream().filter(value -> value.metric.isTurn()).toList();
        saveAll(dayValues, false, replace);
        saveAll(turnValues, true, replace);
    }

    private static void saveAll(List<OrbisMetricValue> values, boolean isTurn, boolean replace) {
        String table = isTurn ? "ORBIS_METRICS_TURN" : "ORBIS_METRICS_DAY";
        String dayOrTurnCol = isTurn ? "turn" : "day";
        int chunkSize = 10000;
        for (int i = 0; i < values.size(); i += chunkSize) {
            int end = Math.min(i + chunkSize, values.size());
            List<OrbisMetricValue> subList = values.subList(i, end);
            String keyWord = replace ? "REPLACE" : "IGNORE";
            Locutus.imp().getNationDB().executeBatch(subList, "INSERT OR " + keyWord + " INTO `" + table + "`(`metric`, `" + dayOrTurnCol + "`, `value`) VALUES(?, ?, ?)", (ThrowingBiConsumer<OrbisMetricValue, PreparedStatement>) (value, stmt) -> {
                stmt.setInt(1, value.metric.ordinal());
                stmt.setLong(2, value.turnOrDay);
                stmt.setDouble(3, value.value);
            });
        }
    }
}
