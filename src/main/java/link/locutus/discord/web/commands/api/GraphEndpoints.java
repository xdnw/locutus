package link.locutus.discord.web.commands.api;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import link.locutus.discord.apiv1.enums.Continent;
import link.locutus.discord.commands.manager.v2.binding.WebStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.*;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.NationAttributeDouble;
import link.locutus.discord.commands.manager.v2.table.TableNumberFormat;
import link.locutus.discord.commands.manager.v2.table.TimeFormat;
import link.locutus.discord.commands.manager.v2.table.TimeNumericTable;
import link.locutus.discord.commands.manager.v2.table.imp.*;
import link.locutus.discord.commands.rankings.SphereGenerator;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.metric.AllianceMetric;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.web.commands.ReturnType;
import link.locutus.discord.web.commands.binding.value_types.CoalitionGraph;
import link.locutus.discord.web.commands.binding.value_types.CoalitionGraphs;
import link.locutus.discord.web.commands.binding.value_types.GraphType;
import link.locutus.discord.web.commands.binding.value_types.WebGraph;

import java.util.*;
import java.util.stream.Collectors;

public class GraphEndpoints {
    @Command()
    @ReturnType(WebGraph.class)
    public WebGraph radiationStats(WebStore ws, Set<Continent> continents, @Timestamp long start, @Timestamp long end) {
        long startTurn = TimeUtil.getTurn(start);
        TimeNumericTable<Void> table = new RadiationByTurn(continents, start, end);
        WebGraph graph = table.convertTurnsToEpochSeconds(startTurn).toHtmlJson(TimeFormat.SECONDS_TO_DATE, TableNumberFormat.SI_UNIT, GraphType.LINE, TimeUtil.getTimeFromTurn(startTurn) / 1000L);
        return graph;
    }

    @Command()
    @ReturnType(WebGraph.class)
    public WebGraph aaStats(WebStore ws, Set<AllianceMetric> metrics, @Timestamp long start, @Timestamp long end, Set<DBAlliance> coalition) {
        String title = "aaStats";
        String coalitionName = coalition.stream().map(DBAlliance::getName).collect(Collectors.joining(","));

        long startTurn = TimeUtil.getTurn(start);
        long endTurn = TimeUtil.getTurn(end);

        if (startTurn < endTurn - Short.MAX_VALUE) throw new IllegalArgumentException("Time range too large");
        if (endTurn > TimeUtil.getTurn()) throw new IllegalArgumentException("End turn must be a current or previous time");

        Set<TableNumberFormat> formats = metrics.stream().map(AllianceMetric::getFormat).collect(Collectors.toSet());
        TableNumberFormat format = formats.size() == 1 ? formats.iterator().next() : TableNumberFormat.SI_UNIT;

        TimeNumericTable table = CoalitionMetricsGraph.create(metrics, startTurn, endTurn, coalitionName, coalition);
        WebGraph graph = table.convertTurnsToEpochSeconds(startTurn).toHtmlJson(TimeFormat.SECONDS_TO_DATE, format, GraphType.LINE, TimeUtil.getTimeFromTurn(startTurn) / 1000L);
        return graph;
    }

    @Command()
    @ReturnType(WebGraph.class)
    public WebGraph metricByGroup(WebStore ws, Set<NationAttributeDouble> metrics, Set<DBNation> coalition, @Default("getCities") NationAttributeDouble groupBy, @Switch("i") boolean includeInactives, @Switch("a") boolean includeApplicants, @Switch("t") boolean total) {
        TimeNumericTable table = new MetricByGroup(metrics, coalition, groupBy, includeInactives, includeApplicants, total);
        WebGraph graph = table.toHtmlJson(TimeFormat.SI_UNIT, TableNumberFormat.SI_UNIT, GraphType.LINE, 0);
        return graph;
    }

    @Command(desc = "Compare the tier stats of up to 10 alliances/nations on a single graph")
    @ReturnType(WebGraph.class)
    public WebGraph compareTierStats(WebStore ws, NationAttributeDouble metric, NationAttributeDouble groupBy,
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

        TimeNumericTable table = new EntityGroup(title, metric, nations, coalitionNames, groupBy, total);

        WebGraph graph = table.toHtmlJson(TimeFormat.SI_UNIT, TableNumberFormat.SI_UNIT, barGraph ? GraphType.SIDE_BY_SIDE_BAR : GraphType.LINE, 0);
        return graph;
    }

    @Command(desc = "Compare the stats of up to 10 alliances/coalitions on a single time graph")
    @ReturnType(WebGraph.class)
    public WebGraph compareStats(WebStore ws, AllianceMetric metric,  @Timestamp long start, @Timestamp long end,
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


        TimeNumericTable table = new MultiCoalitionMetricGraph(metric, startTurn, endTurn, coalitionNames, coalitionsArray);
        WebGraph graph = table.toHtmlJson(TimeFormat.TURN_TO_DATE, metric.getFormat(), GraphType.LINE, startTurn);
        return graph;
    }
}
