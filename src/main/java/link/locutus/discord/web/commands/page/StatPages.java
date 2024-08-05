package link.locutus.discord.web.commands.page;

import gg.jte.generated.precompiled.data.JtebarchartsingleGenerated;
import gg.jte.generated.precompiled.data.JtetimechartdatasrcpageGenerated;
import gg.jte.generated.precompiled.guild.milcom.JteglobalmilitarizationGenerated;
import gg.jte.generated.precompiled.guild.milcom.JteglobaltierstatsGenerated;
import link.locutus.discord.apiv1.enums.Continent;
import link.locutus.discord.commands.manager.v2.binding.WebStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.NoFormat;
import link.locutus.discord.commands.manager.v2.binding.annotation.Switch;
import link.locutus.discord.commands.manager.v2.binding.annotation.Timestamp;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.NationAttributeDouble;
import link.locutus.discord.commands.rankings.SphereGenerator;
import link.locutus.discord.commands.rankings.table.TableNumberFormat;
import link.locutus.discord.commands.rankings.table.TimeFormat;
import link.locutus.discord.commands.rankings.table.TimeNumericTable;
import link.locutus.discord.db.entities.metric.AllianceMetric;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.TimeUtil;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class StatPages {

//    @Command(desc = "Show stats for a conflict")
//    public Object conflict(ConflictManager manager, WebStore ws, Conflict conflict) {
//        long start = System.currentTimeMillis();
//        byte[] b64 = conflict.getB64Gzip(manager);
//        long diff = System.currentTimeMillis() - start;
//        System.out.println("Took " + diff + "ms (b64)");
//        return WebStore.render(f -> JteconflictGenerated.render(f, null, ws, conflict.getName(), b64));
//    }
//
//    @Command(desc = "Show active conflicts")
//    public Object conflicts(ConflictManager manager, WebStore ws) {
//        long start = System.currentTimeMillis();
//        TableBuilder<Conflict> table = new TableBuilder<>(ws);
//        table.addColumn("Conflict", true, true, f -> createArrayObj(f.getId(), f.getName()));
//        table.addColumn("Start", true, true, f -> TimeUtil.getTimeFromTurn(f.getStartTurn()));
//        table.addColumn("End", true, true, f -> f.getEndTurn() == Long.MAX_VALUE ? -1 : TimeUtil.getTimeFromTurn(f.getEndTurn()));
//        table.addColumn("Wars", true, false, Conflict::getTotalWars);
//        table.addColumn("Active wars", true, false, Conflict::getActiveWars);
//        table.addColumn("C1 Dealt", true, false, f -> (long) f.getDamageConverted(true));
//        table.addColumn("C2 Dealt", true, false, f -> (long) f.getDamageConverted(false));
//        table.addColumn("C1", false, false, f -> f.getCoalition1().stream().map(manager::getAllianceName).collect(Collectors.joining(",")));
//        table.addColumn("C2", false, false, f -> f.getCoalition2().stream().map(manager::getAllianceName).collect(Collectors.joining(",")));
//        table.sort(2, true);
//        table.setRenderer(0, "renderUrl");
//        table.setRenderer(1, "renderTime");
//        table.setRenderer(2, "renderTime");
//        table.setRenderer(5, "renderMoney");
//        table.setRenderer(6, "renderMoney");
//
//        String b64 = table.buildJsonEncodedString(new ArrayList<>(manager.getConflictMap().values()));
//        long diff = System.currentTimeMillis() - start;
//        System.out.println("Took " + diff + "ms");
//        return WebStore.render(f -> JteconflictsGenerated.render(f, null, ws, "Conflicts", b64));
//    }

    @Command()
    public Object globalStats(WebStore ws, Set<AllianceMetric> metrics, @Timestamp long start, @Timestamp long end, int topX) {
        if (topX > 250) return "Treaty information is not available for those alliances (outside top 80)";

        long startTurn = TimeUtil.getTurn(start);
        long endTurn = TimeUtil.getTurn(end);

        SphereGenerator spheres = new SphereGenerator(topX);
        Set<DBAlliance> alliances = spheres.getAlliances();
        Map<DBAlliance, Map<AllianceMetric, Map<Long, Double>>> metricMap = AllianceMetric.getMetrics(metrics, startTurn, endTurn, alliances);

        return WebStore.render(f -> JteglobalmilitarizationGenerated.render(f, null, ws, spheres, alliances, metricMap, metrics, startTurn, endTurn));
    }

    @Command()
    public Object radiationStats(WebStore ws, Set<Continent> continents, @Timestamp long start, @Timestamp long end) {
        long startTurn = TimeUtil.getTurn(start);
        TimeNumericTable<Void> table = TimeNumericTable.createForContinents(continents, start, end);
        JsonObject json = table.convertTurnsToEpochSeconds(startTurn).toHtmlJson(TimeFormat.TURN_TO_DATE, TableNumberFormat.SI_UNIT, startTurn);
        return WebStore.render(f -> JtetimechartdatasrcpageGenerated.render(f, null, ws, "Radiation by Time", json, true));
    }

    @Command()
    public Object aaStats(WebStore ws, Set<AllianceMetric> metrics, @Timestamp long start, @Timestamp long end, Set<DBAlliance> coalition) {
        String title = "aaStats";
        String coalitionName = coalition.stream().map(DBAlliance::getName).collect(Collectors.joining(","));

        long startTurn = TimeUtil.getTurn(start);
        long endTurn = TimeUtil.getTurn(end);

        if (startTurn < endTurn - Short.MAX_VALUE) throw new IllegalArgumentException("Time range too large");
        if (endTurn > TimeUtil.getTurn()) throw new IllegalArgumentException("End turn must be a current or previous time");

        Set<TableNumberFormat> formats = metrics.stream().map(AllianceMetric::getFormat).collect(Collectors.toSet());
        TableNumberFormat format = formats.size() == 1 ? formats.iterator().next() : TableNumberFormat.SI_UNIT;

        TimeNumericTable table = AllianceMetric.generateTable(metrics, startTurn, endTurn, coalitionName, coalition);
        JsonObject json = table.convertTurnsToEpochSeconds(startTurn).toHtmlJson(TimeFormat.TURN_TO_DATE, format, startTurn);
        return WebStore.render(f -> JtetimechartdatasrcpageGenerated.render(f, null, ws, title, json, true));
    }

    @Command()
    @NoFormat
    public Object globalTierStats(WebStore ws, Set<NationAttributeDouble> metrics, int topX, @Default("getCities") NationAttributeDouble groupBy, @Switch("t") boolean total) {
        if (topX > 250) return "Treaty information is not available for those alliances (outside top 80)";

        boolean removeVM = true;
        int removeActiveM = 7200;
        boolean removeApps = true;

        SphereGenerator spheres = new SphereGenerator(topX);
        Set<DBAlliance> alliances = spheres.getAlliances();

        return WebStore.render(f -> JteglobaltierstatsGenerated.render(f, null, ws, spheres, alliances, metrics, groupBy, total, removeVM, removeActiveM, removeApps));
    }

    @Command()
    public Object metricByGroup(WebStore ws, Set<NationAttributeDouble> metrics, Set<DBNation> coalition, @Default("getCities") NationAttributeDouble groupBy, @Switch("i") boolean includeInactives, @Switch("a") boolean includeApplicants, @Switch("t") boolean total) {
        TimeNumericTable table = TimeNumericTable.metricByGroup(metrics, coalition, groupBy, includeInactives, includeApplicants, total);
        JsonObject json = table.toHtmlJson();
        return WebStore.render(f -> JtebarchartsingleGenerated.render(f, null, ws, table.getName(), json, false));
    }

    @Command(desc = "Compare the tier stats of up to 10 alliances/nations on a single graph")
    public Object compareTierStats(WebStore ws, NationAttributeDouble metric, NationAttributeDouble groupBy,
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

        JsonObject data = table.toHtmlJson(TimeFormat.SI_UNIT, TableNumberFormat.SI_UNIT, 0);
        title = table.getName();

        String finalTitle = title;
        if (coalitions.size() <= 2 || barGraph) {
            return WebStore.render(f -> JtebarchartsingleGenerated.render(f, null, ws, finalTitle, data, false));
        } else {
            return WebStore.render(f -> JtetimechartdatasrcpageGenerated.render(f, null, ws, finalTitle, data, false));
        }
    }

    @Command(desc = "Compare the stats of up to 10 alliances/coalitions on a single time graph")
    public Object compareStats(WebStore ws, AllianceMetric metric,  @Timestamp long start, @Timestamp long end,
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
        JsonObject json = table.toHtmlJson(TimeFormat.TURN_TO_DATE, metric.getFormat(), startTurn);
        title = table.getName();
        String finalTitle = title;
        return WebStore.render(f -> JtetimechartdatasrcpageGenerated.render(f, null, ws, finalTitle, json, true));
    }
}
