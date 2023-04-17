package link.locutus.discord.web.commands.page;

import link.locutus.discord.apiv1.enums.Continent;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Switch;
import link.locutus.discord.commands.manager.v2.binding.annotation.Timediff;
import link.locutus.discord.commands.manager.v2.binding.annotation.Timestamp;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.NationAttribute;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.NationAttributeDouble;
import link.locutus.discord.commands.rankings.SphereGenerator;
import link.locutus.discord.commands.rankings.table.TimeNumericTable;
import link.locutus.discord.db.entities.AllianceMetric;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.pnw.NationList;
import link.locutus.discord.pnw.NationOrAlliance;
import link.locutus.discord.pnw.SimpleNationList;
import link.locutus.discord.util.TimeUtil;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
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
        Set<DBAlliance> alliances = spheres.getAlliances();
        Map<DBAlliance, Map<AllianceMetric, Map<Long, Double>>> metricMap = AllianceMetric.getMetrics(metrics, startTurn, endTurn, alliances);

        return rocker.guild.milcom.globalmilitarization.template(spheres, alliances, metricMap, metrics, startTurn, endTurn).render().toString();
    }

    @Command()
    public Object radiationStats(Set<Continent> continents, @Timestamp long start, @Timestamp long end) {
        long startTurn = TimeUtil.getTurn(start);
        TimeNumericTable<Void> table = TimeNumericTable.createForContinents(continents, start, end);
        JsonObject json = table.convertTurnsToEpochSeconds(startTurn).toHtmlJson();
        return rocker.data.timechartdatasrcpage.template("Radiation by Time", json, true).render().toString();
    }

    @Command()
    public Object aaStats(Set<AllianceMetric> metrics, @Timestamp long start, @Timestamp long end, Set<DBAlliance> coalition) {
        String title = "aaStats";
        String coalitionName = coalition.stream().map(DBAlliance::getName).collect(Collectors.joining(","));

        long startTurn = TimeUtil.getTurn(start);
        long endTurn = TimeUtil.getTurn(end);

        if (startTurn < endTurn - Short.MAX_VALUE) throw new IllegalArgumentException("Time range too large");
        if (endTurn > TimeUtil.getTurn()) throw new IllegalArgumentException("End turn must be a current or previous time");

        TimeNumericTable table = AllianceMetric.generateTable(metrics, startTurn, endTurn, coalitionName, coalition);
        JsonObject json = table.convertTurnsToEpochSeconds(startTurn).toHtmlJson();
        return rocker.data.timechartdatasrcpage.template(title, json, true).render().toString();
    }

    @Command()
    public Object globalTierStats(Set<NationAttributeDouble> metrics, int topX, @Default("getCities") NationAttributeDouble groupBy, @Switch("t") boolean total) {
        if (topX > 250) return "Treaty information is not available for those alliances (outside top 80)";

        boolean removeVM = true;
        int removeActiveM = 7200;
        boolean removeApps = true;

        SphereGenerator spheres = new SphereGenerator(topX);
        Set<DBAlliance> alliances = spheres.getAlliances();

        return rocker.guild.milcom.globaltierstats.template(spheres, alliances, metrics, groupBy, total, removeVM, removeActiveM, removeApps).render().toString();
    }

    @Command()
    public Object metricByGroup(Set<NationAttributeDouble> metrics, Set<NationOrAlliance> coalition, @Default("getCities") NationAttributeDouble groupBy, @Switch("i") boolean includeInactives, @Switch("i") boolean includeApplicants, @Switch("t") boolean total) {
        Set<DBNation> coalitionNations = new HashSet<>();
        for (NationOrAlliance natOrAA : coalition) {
            coalitionNations.addAll(natOrAA.getDBNations());
        }
        coalitionNations.removeIf(f -> f.getVm_turns() != 0 || (!includeApplicants && f.getPosition() <= 1) || (!includeInactives && f.getActive_m() > 4880));
        NationAttributeDouble[] metricsArr = metrics.toArray(new NationAttributeDouble[0]);
        String[] labels = metrics.stream().map(NationAttribute::getName).toArray(String[]::new);

        NationList coalitionList = new SimpleNationList(coalitionNations);

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
                        NationAttributeDouble metric = metricsArr[i];
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

        return rocker.data.barchartsingle.template(title, json, false).render().toString();
    }

    @Command(desc = "Compare the tier stats of up to 10 alliances/nations on a single graph")
    public Object compareTierStats(NationAttributeDouble metric, NationAttributeDouble groupBy,
                                   Set<DBAlliance> coalition1,
                                   @Default Set<DBAlliance> coalition2,
                                   @Default Set<DBAlliance> coalition3,
                                   @Default Set<DBAlliance> coalition4,
                                   @Default Set<DBAlliance> coalition5,
                                   @Default Set<DBAlliance> coalition6,
                                   @Default Set<DBAlliance> coalition7,
                                   @Default Set<DBAlliance> coalition8,
                                   @Default Set<DBAlliance> coalition9,
                                   @Default Set<DBAlliance> coalition10,
                                   @Switch("t") boolean total,
                                   @Switch("b") boolean barGraph) {

        List<Set<DBAlliance>> coalitions = new ArrayList<>();

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

        List<String> coalitionNames = TimeNumericTable.toCoalitionNames(coalitions);
        List<List<DBNation>> nations = TimeNumericTable.toNations(coalitions, removeVM, removeActiveM, removeApps);

        TimeNumericTable table = TimeNumericTable.create(title, metric, nations, coalitionNames, groupBy, total);

        JsonObject data = table.toHtmlJson();
        title = table.getName();

        if (coalitions.size() <= 2 || barGraph) {
            return rocker.data.barchartsingle.template(title, data, false).render().toString();
        } else {
            return rocker.data.timechartdatasrcpage.template(title, data, false).render().toString();
        }
    }

    @Command(desc = "Compare the stats of up to 10 alliances/coalitions on a single time graph")
    public Object compareStats(AllianceMetric metric,  @Timestamp long start, @Timestamp long end,
                                   Set<DBAlliance> coalition1,
                                   Set<DBAlliance> coalition2,
                                   @Default Set<DBAlliance> coalition3,
                                   @Default Set<DBAlliance> coalition4,
                                   @Default Set<DBAlliance> coalition5,
                                   @Default Set<DBAlliance> coalition6,
                                   @Default Set<DBAlliance> coalition7,
                                   @Default Set<DBAlliance> coalition8,
                                   @Default Set<DBAlliance> coalition9,
                                   @Default Set<DBAlliance> coalition10) {

        List<Set<DBAlliance>> coalitions = new ArrayList<>();

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
        for (Set<DBAlliance> coalition : coalitions) {
            String coalitionName = coalition.stream().map(DBAlliance::getName).collect(Collectors.joining(","));
            coalitionNames.add(coalitionName);
        }

        Set<DBAlliance>[] coalitionsArray = coalitions.toArray(new Set[0]);

        String title = "";

        long startTurn = TimeUtil.getTurn(start);
        long endTurn = TimeUtil.getTurn(end);

        if (startTurn < endTurn - Short.MAX_VALUE) throw new IllegalArgumentException("Time range too large");
        if (endTurn > TimeUtil.getTurn()) throw new IllegalArgumentException("End turn must be a current or previous time");


        TimeNumericTable table = AllianceMetric.generateTable(metric, startTurn, endTurn, coalitionNames, coalitionsArray);
        JsonObject json = table.toHtmlJson();
        title = table.getName();
        return rocker.data.timechartdatasrcpage.template(title, json, true).render().toString();
    }
}
