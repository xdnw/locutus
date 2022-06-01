package link.locutus.discord.web.commands;

import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Switch;
import link.locutus.discord.commands.manager.v2.binding.annotation.Timediff;
import link.locutus.discord.commands.manager.v2.binding.annotation.Timestamp;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.NationMetric;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.NationMetricDouble;
import link.locutus.discord.commands.rankings.SphereGenerator;
import link.locutus.discord.commands.rankings.table.TimeNumericTable;
import link.locutus.discord.db.entities.AllianceMetric;
import link.locutus.discord.pnw.Alliance;
import link.locutus.discord.pnw.DBNation;
import link.locutus.discord.pnw.NationList;
import link.locutus.discord.pnw.NationOrAlliance;
import link.locutus.discord.pnw.SimpleNationList;
import link.locutus.discord.util.TimeUtil;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class StatPages {
    /**
     * TODO
     *  - War stats page
     *  - Compare page
     */

    @Command(desc = "Show war costs between two coalitions")
    public Object warCost(Set<NationOrAlliance> coalition1, Set<NationOrAlliance> coalition2, @Timediff long timeStart, @Timediff long timeEnd) {
        return "TODO";
    }

    @Command()
    public Object globalStats(Set<AllianceMetric> metrics, @Timestamp long start, @Timestamp long end, int topX) {
        if (topX > 250) return "Treaty information is not available for those alliances (outside top 80)";

        long startTurn = TimeUtil.getTurn(start);
        long endTurn = TimeUtil.getTurn(end);

        SphereGenerator spheres = new SphereGenerator(topX);
        Set<Alliance> alliances = spheres.getAlliances();
        Map<Alliance, Map<AllianceMetric, Map<Long, Double>>> metricMap = AllianceMetric.getMetrics(metrics, startTurn, endTurn, alliances);

        return views.guild.milcom.globalmilitarization.template(spheres, alliances, metricMap, metrics, startTurn, endTurn).render().toString();
    }

    @Command()
    public Object aaStats(Set<AllianceMetric> metrics, @Timestamp long start, @Timestamp long end, Set<Alliance> coalition) {
        String title = "aaStats";
        String coalitionName = coalition.stream().map(Alliance::getName).collect(Collectors.joining(","));

        long startTurn = TimeUtil.getTurn(start);
        long endTurn = TimeUtil.getTurn(end);

        if (startTurn < endTurn - Short.MAX_VALUE) throw new IllegalArgumentException("Time range too large");
        if (endTurn > TimeUtil.getTurn()) throw new IllegalArgumentException("End turn must be a current or previous time");

        TimeNumericTable table = AllianceMetric.generateTable(metrics, startTurn, endTurn, coalitionName, coalition);
        JsonObject json = table.convertTurnsToEpochSeconds(startTurn).toHtmlJson();
        return views.data.timechartdatasrcpage.template(title, json, true).render().toString();
    }

    @Command()
    public Object globalTierStats(Set<NationMetricDouble> metrics, int topX, @Default("getCities") NationMetricDouble groupBy, @Switch('t') boolean total) {
        if (topX > 250) return "Treaty information is not available for those alliances (outside top 80)";

        boolean removeVM = true;
        int removeActiveM = 7200;
        boolean removeApps = true;

        SphereGenerator spheres = new SphereGenerator(topX);
        Set<Alliance> alliances = spheres.getAlliances();

        return views.guild.milcom.globaltierstats.template(spheres, alliances, metrics, groupBy, total, removeVM, removeActiveM, removeApps).render().toString();
    }

    @Command()
    public Object metricByGroup(Set<NationMetricDouble> metrics, Set<DBNation> coalition, @Default("getCities") NationMetricDouble groupBy, @Switch('i') boolean includeInactives, @Switch('i') boolean includeApplicants, @Switch('t') boolean total) {
        coalition.removeIf(f -> f.getVm_turns() != 0 || (!includeApplicants && f.getPosition() <= 1) || (!includeInactives && f.getActive_m() > 4880));
        NationMetricDouble[] metricsArr = metrics.toArray(new NationMetricDouble[0]);
        String[] labels = metrics.stream().map(NationMetric::getName).toArray(String[]::new);

        NationList coalitionList = new SimpleNationList(coalition);

        Function<DBNation, Integer> groupByInt = nation -> (int) Math.round(groupBy.apply(nation));
        Map<Integer, NationList> byTier = coalitionList.groupBy(groupByInt);
        int min = coalitionList.stream(groupByInt).min(Integer::compare).get();
        int max = coalitionList.stream(groupByInt).max(Integer::compare).get();

        double[] buffer = new double[metricsArr.length];
        String labelY = labels.length == 1 ? labels[0] : "metric";
        String title = (total ? "Total" : "Average") + " " + labelY + " by " + groupBy.getName();
        TimeNumericTable<NationList> table = new TimeNumericTable<>(title, groupBy.getName(), labelY, labels) {
            @Override
            public void add(long key, NationList nations) {
                if (nations == null) {
                    Arrays.fill(buffer, 0);
                } else {
                    for (int i = 0; i < metricsArr.length; i++) {
                        NationMetricDouble metric = metricsArr[i];
                        double valueTotal = 0;
                        int count = 0;

                        for (DBNation nation : nations.getNations()) {
                            if (nation.hasUnsetMil()) continue;
                            count++;
                            valueTotal += metric.apply(nation);
                        }
                        if (count > 1 && !total) {
                            valueTotal /= count;
                        }

                        buffer[i] = valueTotal;
                    }
                }
                add(key, buffer);
            }
        };

        for (int key = min; key <= max; key++) {
            NationList nations = byTier.get(key);
            table.add(key, nations);
        }

        JsonObject json = table.toHtmlJson();

        return views.data.barchartsingle.template(title, json, false).render().toString();
    }

    @Command(desc = "Compare the tier stats of up to 10 alliances/nations on a single graph")
    public Object compareTierStats(NationMetricDouble metric, NationMetricDouble groupBy,
                                   Set<Alliance> coalition1,
                                   @Default Set<Alliance> coalition2,
                                   @Default Set<Alliance> coalition3,
                                   @Default Set<Alliance> coalition4,
                                   @Default Set<Alliance> coalition5,
                                   @Default Set<Alliance> coalition6,
                                   @Default Set<Alliance> coalition7,
                                   @Default Set<Alliance> coalition8,
                                   @Default Set<Alliance> coalition9,
                                   @Default Set<Alliance> coalition10,
                                   @Switch('t') boolean total,
                                   @Switch('b') boolean barGraph) {

        List<Set<Alliance>> coalitions = new ArrayList<>();

        coalitions.add(coalition1);
        coalitions.add(coalition2);
        coalitions.add(coalition3);
        coalitions.add(coalition4);
        coalitions.add(coalition5);
        coalitions.add(coalition6);
        coalitions.add(coalition7);
        coalitions.add(coalition8);
        coalitions.add(coalition9);
        coalitions.add(coalition10);
        coalitions.removeIf(f -> f== null || f.isEmpty());

        String title = "";

        boolean removeVM = true;
        int removeActiveM = 7200;
        boolean removeApps = true;

        TimeNumericTable table = TimeNumericTable.create(title, metric, coalitions, groupBy, total, removeVM, removeActiveM, removeApps);

        JsonObject data = table.toHtmlJson();
        title = table.getName();

        if (coalitions.size() <= 2 || barGraph) {
            return views.data.barchartsingle.template(title, data, false).render().toString();
        } else {
            return views.data.timechartdatasrcpage.template(title, data, false).render().toString();
        }
    }

    @Command(desc = "Compare the stats of up to 10 alliances/coalitions on a single time graph")
    public Object compareStats(AllianceMetric metric,  @Timestamp long start, @Timestamp long end,
                                   Set<Alliance> coalition1,
                                   Set<Alliance> coalition2,
                                   @Default Set<Alliance> coalition3,
                                   @Default Set<Alliance> coalition4,
                                   @Default Set<Alliance> coalition5,
                                   @Default Set<Alliance> coalition6,
                                   @Default Set<Alliance> coalition7,
                                   @Default Set<Alliance> coalition8,
                                   @Default Set<Alliance> coalition9,
                                   @Default Set<Alliance> coalition10) {

        List<Set<Alliance>> coalitions = new ArrayList<>();

        coalitions.add(coalition1);
        coalitions.add(coalition2);
        coalitions.add(coalition3);
        coalitions.add(coalition4);
        coalitions.add(coalition5);
        coalitions.add(coalition6);
        coalitions.add(coalition7);
        coalitions.add(coalition8);
        coalitions.add(coalition9);
        coalitions.add(coalition10);
        coalitions.removeIf(f -> f== null || f.isEmpty());

        List<String> coalitionNames = new ArrayList<>();
        for (Set<Alliance> coalition : coalitions) {
            String coalitionName = coalition.stream().map(Alliance::getName).collect(Collectors.joining(","));
            coalitionNames.add(coalitionName);
        }

        Set<Alliance>[] coalitionsArray = coalitions.toArray(new Set[0]);

        String title = "";

        long startTurn = TimeUtil.getTurn(start);
        long endTurn = TimeUtil.getTurn(end);

        if (startTurn < endTurn - Short.MAX_VALUE) throw new IllegalArgumentException("Time range too large");
        if (endTurn > TimeUtil.getTurn()) throw new IllegalArgumentException("End turn must be a current or previous time");


        TimeNumericTable table = AllianceMetric.generateTable(metric, startTurn, endTurn, coalitionNames, coalitionsArray);
        JsonObject json = table.toHtmlJson();
        title = table.getName();
        return views.data.timechartdatasrcpage.template(title, json, true).render().toString();
    }
}
