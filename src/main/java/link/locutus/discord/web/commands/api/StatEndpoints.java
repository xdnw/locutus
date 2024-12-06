package link.locutus.discord.web.commands.api;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.Continent;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.WebStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.*;
import link.locutus.discord.commands.manager.v2.binding.bindings.PlaceholderCache;
import link.locutus.discord.commands.manager.v2.binding.bindings.Placeholders;
import link.locutus.discord.commands.manager.v2.binding.bindings.TypedFunction;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.NationAttributeDouble;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap;
import link.locutus.discord.commands.manager.v2.table.TableNumberFormat;
import link.locutus.discord.commands.manager.v2.table.TimeFormat;
import link.locutus.discord.commands.manager.v2.table.TimeNumericTable;
import link.locutus.discord.commands.manager.v2.table.imp.EntityGroup;
import link.locutus.discord.commands.manager.v2.table.imp.EntityTable;
import link.locutus.discord.commands.manager.v2.table.imp.MetricByGroup;
import link.locutus.discord.commands.manager.v2.table.imp.RadiationByTurn;
import link.locutus.discord.commands.rankings.SphereGenerator;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.metric.AllianceMetric;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.web.commands.ReturnType;
import link.locutus.discord.web.commands.binding.value_types.*;
import net.dv8tion.jda.api.entities.User;

import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;

public class StatEndpoints {

    // TODO validate permissions
    @Command
    @ReturnType(WebTable.class)
    public <T> WebTable table(ValueStore store, @Me @Default User user, @PlaceholderType Class type, String selection_str, @TextArea List<String> columns) {
        System.out.println("Columns " + columns.size() + " | " + columns);
        Class<T> typeCasted = (Class<T>) type;
        Map<Integer, List<WebTableError>> errors = new LinkedHashMap<>();
        int maxPerCol = 3;

        PlaceholdersMap map = Locutus.cmd().getV2().getPlaceholders();
        Placeholders<T> ph = map.get(typeCasted);

        String modifier = null;

        Set<T> selection = ph.parseSet(store, selection_str, modifier);
        ValueStore<T> cacheStore = PlaceholderCache.createCache(selection, typeCasted);

        List<String> renderers = new ObjectArrayList<>(columns.size());
        List<TypedFunction<T, ?>> formatters = new ObjectArrayList<>(columns.size());
        boolean[] isEnum = new boolean[columns.size()];
        for (int i = 0; i < columns.size(); i++) {
            String column = columns.get(i);
            try {
                TypedFunction<T, ?> result = ph.formatRecursively(cacheStore, column, null, 0, false, true);
                Type rsType = result.getType();
                formatters.add(result);
                if (rsType instanceof Class clazz) {
                    renderers.add(switch (clazz.getSimpleName()) {
                        case "double", "Double", "int", "Integer", "float", "Float" -> "comma";
                        case "NationColor" -> "color";
                        case "String" -> "normal";
                        default -> {
                            if (clazz.isEnum()) {
                                isEnum[i] = true;
                                yield "enum:" + clazz.getSimpleName();
                            } else {
                                yield null;
                            }
                        }
                    });
                } else {
                    renderers.add(null);
                }
            } catch (Exception e) {
                e.printStackTrace();
                List<WebTableError> errList = errors.computeIfAbsent(i, k -> new ObjectArrayList<>(maxPerCol));
                if (errList.size() < maxPerCol) {
                    errList.add(new WebTableError(i, null, e.getMessage()));
                }
                formatters.add(null);
                renderers.add(null);
            }
        }

        boolean[] checkedIsJson = new boolean[columns.size()];
        List<List<Object>> data = new ObjectArrayList<>(selection.size());
        data.add(new ObjectArrayList<>(columns));
        int rowI = 0;
        for (T obj : selection) {
            List<Object> row = new ObjectArrayList<>(columns.size());
            for (int i = 0; i < formatters.size(); i++) {
                TypedFunction<T, ?> formatter = formatters.get(i);
                if (formatter == null) {
                    row.add(null);
                } else {
                    try {
                        Object td = formatter.apply(obj);
                        if (td != null && td.getClass().isEnum() && isEnum[i]) {
                            row.add(((Enum<?>) td).ordinal());
                        } else {
                            Object serialized = StringMan.toSerializable(td);
                            if (!checkedIsJson[i] && serialized != null) {
                                checkedIsJson[i] = true;
                                if (renderers.get(i) == null && serialized instanceof Map || serialized instanceof List) {
                                    renderers.set(i, "json");
                                }
                            }
                            row.add(serialized);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        List<WebTableError> errList = errors.computeIfAbsent(i, k -> new ObjectArrayList<>(maxPerCol));
                        if (errList.size() < maxPerCol) {
                            errList.add(new WebTableError(i, rowI, e.getMessage()));
                        }
                        row.add(null);
                    }
                }
            }
            data.add(row);
            rowI++;
        }
        List<WebTableError> errorsArr = errors.isEmpty() ? null : errors.values().stream().collect(ObjectArrayList::new, List::addAll, List::addAll);
        return new WebTable(data, errorsArr, renderers);
    }

    @Command()
    @ReturnType(CoalitionGraphs.class)
    public CoalitionGraphs globalStats(WebStore ws, Set<AllianceMetric> metrics, @Timestamp long start, @Timestamp long end, int topX) {
        if (topX > 250) throw new IllegalArgumentException("Treaty information is not available for those alliances (outside top 250)");

        long startTurn = TimeUtil.getTurn(start);
        long endTurn = TimeUtil.getTurn(end);

        SphereGenerator spheres = new SphereGenerator(topX);
        Set<DBAlliance> alliances = spheres.getAlliances();

        Map<DBAlliance, Map<AllianceMetric, Map<Long, Double>>> metricMap = AllianceMetric.getMetrics(metrics, startTurn, endTurn, alliances);

        List<CoalitionGraph> sphereMil = new ObjectArrayList<>();

        for (int sphereId : spheres.getSpheres()) {
            String name = spheres.getSphereName(sphereId);
            List<DBAlliance> sphereAlliances = spheres.getAlliances(sphereId);

            Map<DBAlliance, Integer> alliancesMap = sphereAlliances.stream()
                    .collect(Collectors.toMap(alliance -> alliance, DBAlliance::getId));

            WebGraph overall = null;
            if (sphereAlliances.size() > 1) {
                overall = AllianceMetric.generateTable(metricMap, metrics, startTurn, endTurn, name, new ObjectLinkedOpenHashSet<>(sphereAlliances))
                        .toHtmlJson(TimeFormat.TURN_TO_DATE, TableNumberFormat.PERCENTAGE_ONE, GraphType.LINE, startTurn);
            }

            Map<Integer, WebGraph> byAlliance = new Int2ObjectOpenHashMap<>();
            for (DBAlliance alliance : sphereAlliances) {
                WebGraph graph = AllianceMetric.generateTable(metricMap, metrics, startTurn, endTurn, alliance.getName(), Collections.singleton(alliance))
                        .toHtmlJson(TimeFormat.TURN_TO_DATE, TableNumberFormat.PERCENTAGE_ONE, GraphType.LINE, startTurn);
                byAlliance.put(alliance.getId(), graph);
            }

            CoalitionGraph sphereMilitarization = new CoalitionGraph(name, alliancesMap, overall, byAlliance);
            sphereMil.add(sphereMilitarization);
        }

        CoalitionGraphs globalMil = new CoalitionGraphs(sphereMil);
        return globalMil;
    }

    @Command()
    @NoFormat
    @ReturnType(CoalitionGraphs.class)
    public CoalitionGraphs globalTierStats(WebStore ws, Set<NationAttributeDouble> metrics, int topX, @Default("getCities") NationAttributeDouble groupBy, @Switch("t") boolean total) {
        if (topX > 250) throw new IllegalArgumentException("Treaty information is not available for those alliances (outside top 80)");

        boolean removeVM = true;
        int removeActiveM = 7200;
        boolean removeApps = true;

        SphereGenerator spheres = new SphereGenerator(topX);
        List<CoalitionGraph> sphereMil = new ObjectArrayList<>();

        for (int sphereId : spheres.getSpheres()) {
            String name = spheres.getSphereName(sphereId);
            List<DBAlliance> sphereAlliances = spheres.getAlliances(sphereId);

            Map<DBAlliance, Integer> alliancesMap = sphereAlliances.stream()
                    .collect(Collectors.toMap(alliance -> alliance, DBAlliance::getId));

            WebGraph overall = null;
            if (sphereAlliances.size() > 1) {
                overall = EntityTable.create(spheres.getSphereName(sphereId) + ": ", metrics,
                        spheres.getAlliances(sphereId), groupBy, total, removeVM, removeActiveM, removeApps
                ).toHtmlJson(TimeFormat.SI_UNIT, TableNumberFormat.SI_UNIT, GraphType.SIDE_BY_SIDE_BAR, 0);
            }

            Map<Integer, WebGraph> byAlliance = new Int2ObjectOpenHashMap<>();
            for (DBAlliance alliance : sphereAlliances) {
                WebGraph graph = EntityTable.create(alliance.getName() + ": ", metrics, Collections.singleton(alliance),
                        groupBy, total, removeVM, removeActiveM, removeApps
                ).toHtmlJson(TimeFormat.SI_UNIT, TableNumberFormat.SI_UNIT, GraphType.SIDE_BY_SIDE_BAR, 0);
                byAlliance.put(alliance.getId(), graph);
            }

            CoalitionGraph sphereMilitarization = new CoalitionGraph(name, alliancesMap, overall, byAlliance);
            sphereMil.add(sphereMilitarization);
        }

        CoalitionGraphs globalMil = new CoalitionGraphs(sphereMil);
        return globalMil;
    }

    @Command()
    @ReturnType(WebGraph.class)
    public WebGraph radiationStats(WebStore ws, Set<Continent> continents, @Timestamp long start, @Timestamp long end) {
        long startTurn = TimeUtil.getTurn(start);
        TimeNumericTable<Void> table = new RadiationByTurn(continents, start, end).writeData();
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

        TimeNumericTable table = AllianceMetric.generateTable(metrics, startTurn, endTurn, coalitionName, coalition);
        WebGraph graph = table.convertTurnsToEpochSeconds(startTurn).toHtmlJson(TimeFormat.SECONDS_TO_DATE, format, GraphType.LINE, TimeUtil.getTimeFromTurn(startTurn) / 1000L);
        return graph;
    }

    @Command()
    @ReturnType(WebGraph.class)
    public WebGraph metricByGroup(WebStore ws, Set<NationAttributeDouble> metrics, Set<DBNation> coalition, @Default("getCities") NationAttributeDouble groupBy, @Switch("i") boolean includeInactives, @Switch("a") boolean includeApplicants, @Switch("t") boolean total) {
        TimeNumericTable table = new MetricByGroup(metrics, coalition, groupBy, includeInactives, includeApplicants, total).writeData();
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

        TimeNumericTable table = new EntityGroup(title, metric, nations, coalitionNames, groupBy, total).writeData();

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


        TimeNumericTable table = AllianceMetric.generateTable(metric, startTurn, endTurn, coalitionNames, coalitionsArray);
        WebGraph graph = table.toHtmlJson(TimeFormat.TURN_TO_DATE, metric.getFormat(), GraphType.LINE, startTurn);
        return graph;
    }
}
