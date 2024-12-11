package link.locutus.discord.web.commands.api;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.*;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.WebStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.*;
import link.locutus.discord.commands.manager.v2.binding.annotation.Timestamp;
import link.locutus.discord.commands.manager.v2.binding.bindings.PlaceholderCache;
import link.locutus.discord.commands.manager.v2.binding.bindings.Placeholders;
import link.locutus.discord.commands.manager.v2.binding.bindings.TypedFunction;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.NationAttribute;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.NationAttributeDouble;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap;
import link.locutus.discord.commands.manager.v2.table.TableNumberFormat;
import link.locutus.discord.commands.manager.v2.table.TimeFormat;
import link.locutus.discord.commands.manager.v2.table.TimeNumericTable;
import link.locutus.discord.commands.manager.v2.table.imp.*;
import link.locutus.discord.commands.rankings.SphereGenerator;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.NationDB;
import link.locutus.discord.db.entities.*;
import link.locutus.discord.db.entities.metric.AllianceMetric;
import link.locutus.discord.db.entities.metric.AllianceMetricMode;
import link.locutus.discord.db.entities.metric.OrbisMetric;
import link.locutus.discord.pnw.NationList;
import link.locutus.discord.pnw.NationOrAlliance;
import link.locutus.discord.pnw.SimpleNationList;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.io.PagePriority;
import link.locutus.discord.util.trade.TradeManager;
import link.locutus.discord.web.commands.ReturnType;
import link.locutus.discord.web.commands.binding.value_types.*;
import net.dv8tion.jda.api.entities.User;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.reflect.Type;
import java.security.GeneralSecurityException;
import java.text.ParseException;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class StatEndpoints {
    // EntityTable custom
    // EntityGroup
    // TaxCategoryGraph


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

    @Command(desc = "Get alliance attributes by day\n" +
            "If your metric does not relate to cities, set `skipCityData` to true to speed up the process.")
    @RolePermission(value = Roles.ADMIN, root = true)
    @NoFormat
    @ReturnType(WebGraph.class)
    public WebGraph AlliancesDataByDay(
                                     TypedFunction<DBNation, Double> metric,
                                     @Timestamp long start,
                                     @Timestamp long end,
                                     AllianceMetricMode mode,
                                     @Arg ("The alliances to include. Defaults to top 15") @Default Set<DBAlliance> alliances,
                                     @Default Predicate<DBNation> filter, @Switch("g") boolean graph, @Switch("a") boolean includeApps) throws IOException, ParseException {
        return AlliancesNationMetricByDay.create(metric, start, end, mode, alliances, filter, includeApps).toHtmlJson();
    }

    @Command(desc = "Compare the metric over time between multiple alliances")
    @ReturnType(WebGraph.class)
    public WebGraph militarizationTime(DBAlliance alliance, @Default("7d") @Timestamp long start_time,
                                     @Switch("e") @Timestamp Long end_time) throws IOException {
        if (end_time == null) end_time = System.currentTimeMillis();
        long endTurn = Math.min(TimeUtil.getTurn(), TimeUtil.getTurn(end_time));
        long startTurn = TimeUtil.getTurn(start_time);

        List<AllianceMetric> metrics = new ArrayList<>(Arrays.asList(AllianceMetric.SOLDIER_PCT, AllianceMetric.TANK_PCT, AllianceMetric.AIRCRAFT_PCT, AllianceMetric.SHIP_PCT));
        CoalitionMetricsGraph table = CoalitionMetricsGraph.create(metrics, startTurn, endTurn, alliance.getName(), Collections.singleton(alliance));
        return table.toHtmlJson();
    }

    @Command(desc = "Generate a graph of spy counts by city count between two coalitions\n" +
            "Nations which are applicants, in vacation mode or inactive (2 days) are excluded")
    @ReturnType(WebGraph.class)
    public WebGraph spyTierGraph(NationList coalition1,
                               NationList coalition2,
                               @Switch("i") boolean includeInactives,
                               @Switch("a") boolean includeApplicants,
                               @Arg("Graph the total spies instead of average per nation")
                               @Switch("t") boolean total,
                               @Switch("b") boolean barGraph) throws IOException {
        Collection<DBNation> coalition1Nations = coalition1.getNations();
        Collection<DBNation> coalition2Nations = coalition2.getNations();
        coalition1Nations.removeIf(f -> f.getVm_turns() != 0 || (!includeApplicants && f.getPosition() <= 1) || (!includeInactives && f.active_m() > 2880));
        coalition2Nations.removeIf(f -> f.getVm_turns() != 0 || (!includeApplicants && f.getPosition() <= 1) || (!includeInactives && f.active_m() > 2880));

        NationAttribute<Double> attribute = new NationAttribute<>("spies", "", double.class, f -> (double) f.updateSpies(PagePriority.ESPIONAGE_ODDS_BULK, 1));
        List<List<DBNation>> coalitions = List.of(new ArrayList<>(coalition1Nations), new ArrayList<>(coalition2Nations));
        List<String> names = List.of(coalition1.getFilter(), coalition2.getFilter());
        NationAttribute<Double> groupBy = new NationAttribute<>("cities", "", double.class, f -> (double) f.getCities());

        EntityGroup<DBNation> graph = new EntityGroup<DBNation>(null, attribute, coalitions, names, groupBy, total);
        return graph.setGraphType(barGraph ? GraphType.SIDE_BY_SIDE_BAR : GraphType.LINE).toHtmlJson();
    }

    @Command(desc = "Generate a bar char comparing the nation at each city count (tiering) between two coalitions")
    @ReturnType(WebGraph.class)
    public WebGraph cityTierGraph(@Me GuildDB db, NationList coalition1, NationList coalition2,
                                @Switch("i") boolean includeInactives,
                                @Switch("b") boolean barGraph,
                                @Switch("a") boolean includeApplicants,
                                @Switch("s") @Timestamp Long snapshotDate) throws IOException {
        Set<DBNation> nations1 = PW.getNationsSnapshot(coalition1.getNations(), coalition1.getFilter(), snapshotDate, db.getGuild());
        Set<DBNation> nations2 = PW.getNationsSnapshot(coalition2.getNations(), coalition2.getFilter(), snapshotDate, db.getGuild());
        nations1.removeIf(f -> f.getVm_turns() != 0 || (!includeApplicants && f.getPosition() <= 1) || (!includeInactives && f.active_m() > 4880));
        nations2.removeIf(f -> f.getVm_turns() != 0 || (!includeApplicants && f.getPosition() <= 1) || (!includeInactives && f.active_m() > 4880));

        NationAttribute<Double> attribute = new NationAttribute<>("nations", "", double.class, f -> 1d);
        List<List<DBNation>> coalitions = List.of(new ArrayList<>(nations1), new ArrayList<>(nations2));
        List<String> names = List.of(coalition1.getFilter(), coalition2.getFilter());
        NationAttribute<Double> groupBy = new NationAttribute<>("city", "", double.class, f -> (double) f.getCities());

        EntityGroup<DBNation> graph = new EntityGroup<DBNation>(null, attribute, coalitions, names, groupBy, true);
        return graph.setGraphType(barGraph ? GraphType.SIDE_BY_SIDE_BAR : GraphType.LINE).toHtmlJson();
    }

    @Command(desc = "Graph a set of nation metrics for the specified nations over a period of time based on daily nation and city snapshots")
    @ReturnType(WebGraph.class)
    public WebGraph metricByGroup(@Me IMessageIO io, @Me GuildDB db,
                              Set<NationAttributeDouble> metrics,
                              NationList nations,
                              @Default("getCities") NationAttributeDouble groupBy,
                              @Switch("i") boolean includeInactives,
                              @Switch("a") boolean includeApplicants,
                              @Switch("t") boolean total,
                              @Switch("s") @Timestamp Long snapshotDate,
                              @Switch("j") boolean attachJson,
                              @Switch("c") boolean attachCsv) throws IOException {
        Set<DBNation> nationsSet = PW.getNationsSnapshot(nations.getNations(), nations.getFilter(), snapshotDate, db.getGuild());
        return new MetricByGroup(metrics, nationsSet, groupBy, includeInactives, includeApplicants, total).toHtmlJson();
    }

    @Command(desc = "Compare the metric over time between multiple alliances")
    @ReturnType(WebGraph.class)
    public WebGraph allianceMetricsCompareByTurn(AllianceMetric metric, Set<DBAlliance> alliances,
                                               @Arg("Date to start from")
                                               @Timestamp long time) throws IOException {
        long turnStart = TimeUtil.getTurn(time);
        Set<DBAlliance>[] coalitions = alliances.stream().map(Collections::singleton).toList().toArray(new Set[0]);
        List<String> coalitionNames = alliances.stream().map(DBAlliance::getName).collect(Collectors.toList());
        MultiCoalitionMetricGraph table = MultiCoalitionMetricGraph.create(metric, turnStart, coalitionNames, coalitions);
        return table.toHtmlJson();
    }

    @Command(desc = "Graph an alliance metric over time for two coalitions")
    @ReturnType(WebGraph.class)
    public WebGraph allianceMetricsAB(AllianceMetric metric, Set<DBAlliance> coalition1, Set<DBAlliance> coalition2,
                                    @Arg("Date to start from")
                                    @Timestamp long time) throws IOException {
        long turnStart = TimeUtil.getTurn(time);
        MultiCoalitionMetricGraph table = MultiCoalitionMetricGraph.create(metric, turnStart, null, coalition1, coalition2);
        return table.toHtmlJson();
    }

    @Command(desc = "Graph the metric over time for a coalition")
    @ReturnType(WebGraph.class)
    public WebGraph allianceMetricsByTurn(AllianceMetric metric, Set<DBAlliance> coalition,
                                        @Arg("Date to start from")
                                        @Timestamp long time) throws IOException {
        long turnStart = TimeUtil.getTurn(time);
        List<String> coalitionNames = List.of(metric.name());
        MultiCoalitionMetricGraph table = MultiCoalitionMetricGraph.create(metric, turnStart, coalitionNames, coalition);
        return table.toHtmlJson();
    }

    @Command(desc = "Get nth loot beige graph by score range")
    @ReturnType(WebGraph.class)
    public WebGraph NthBeigeLootByScoreRange(@Me GuildDB db, @Default NationList nations, @Default("5") int n,
                                             @Default @Timestamp Long snapshotDate) throws IOException {
        if (n <= 0) throw new IllegalArgumentException("N must be greater than 0");
        String filter;
        if (nations != null) {
            filter = nations.getFilter();
        } else {
            filter = "*,#active_m>7200,#vm_turns=0,#position<=1";
            nations = new SimpleNationList(Locutus.imp().getNationDB().getNationsMatching(f ->
                    f.active_m() > 7200 && f.getVm_turns() == 0 && f.getPositionEnum().id <= Rank.APPLICANT.id));
        }
        Set<DBNation> nationsSet = PW.getNationsSnapshot(nations.getNations(), filter, snapshotDate, db.getGuild());
        return new NthBeigeLoot(nationsSet, n).toHtmlJson();
    }

    @Command(desc = "Get a game graph by day")
    @ReturnType(WebGraph.class)
    public WebGraph orbisStatByDay(Set<OrbisMetric> metrics, @Default @Timestamp Long start, @Default @Timestamp Long end) throws IOException {
        OrbisMetricGraph graph = new OrbisMetricGraph(metrics, start, end);
        return graph.toHtmlJson();
    }

    @Command(desc = "Generate a graph of average trade buy and sell volume by day")
    @ReturnType(WebGraph.class)
    public WebGraph tradevolumebyday(TradeManager manager,
                                   ResourceType resource,
                                   @Timestamp long start, @Default @Timestamp Long end) throws IOException, GeneralSecurityException {
        String title = "volume by day";
        return rssTradeByDay(title, start, end, offers -> manager.volumeByResource(offers), resource);
    }

    public static WebGraph rssTradeByDay(String title, long start, Long end, Function<Collection<DBTrade>, long[]> rssFunction, ResourceType resource) throws IOException {
        if (end == null) end = Long.MAX_VALUE;
        if (resource == ResourceType.CREDITS || resource == ResourceType.MONEY) {
            throw new IllegalArgumentException("Cannot graph credits or money");
        }
        Map<Long, Map<ResourceType, Map.Entry<Long, Long>>> offers = RssTradeByDay.getVolumeByDay(Set.of(resource), rssFunction, start, end);

        RssTradeByDay graph = new RssTradeByDay(title, offers, resource);
        return graph.toHtmlJson();
    }

    @Command(desc = "Generate a graph of average trade buy and sell total by day")
    @ReturnType(WebGraph.class)
    public WebGraph tradetotalbyday(@Me IMessageIO channel, TradeManager manager,
                                   ResourceType resource,
                                  @Timestamp long start, @Default @Timestamp Long end) throws IOException, GeneralSecurityException {
        String title = "total by day";
        return rssTradeByDay(title, start, end, offers -> manager.totalByResource(offers), resource);
    }

    @Command(desc = "Generate a graph of nation counts by score between two coalitions", aliases = {"scoreTierGraph", "scoreTierSheet"})
    @ReturnType(WebGraph.class)
    public WebGraph scoreTierGraph(@Me GuildDB db,
                                 NationList coalition1,
                                 NationList coalition2,
                                 @Switch("i") boolean includeInactives,
                                 @Switch("a") boolean includeApplicants,
                                 @Switch("s") @Timestamp Long snapshotDate) throws IOException {
        Set<DBNation> coalition1Nations = PW.getNationsSnapshot(coalition1.getNations(), coalition1.getFilter(), snapshotDate, db.getGuild());
        Set<DBNation> coalition2Nations = PW.getNationsSnapshot(coalition2.getNations(), coalition2.getFilter(), snapshotDate, db.getGuild());

        return new ScoreTierGraph(
                coalition1.getFilter(),
                coalition2.getFilter(),
                coalition1Nations,
                coalition2Nations,
                includeInactives,
                includeApplicants
        ).toHtmlJson();
    }

    @Command(desc = "Generate a graph comparing market values of two resource amounts by day")
    @ReturnType(WebGraph.class)
    public WebGraph compareStockpileValueByDay(Map<ResourceType, Double> stockpile1,
                                             Map<ResourceType, Double> stockpile2,
                                             @Range(min=1, max=3000) int numDays) throws IOException, GeneralSecurityException {
        return new StockpileValueByDay(stockpile1, stockpile2, numDays).toHtmlJson();
    }

    @Command(desc = "Generate a graph of nation military strength by score between two coalitions\n" +
            "1 tank = 1/32 aircraft for strength calculations\n" +
            "Effective score range is limited to 1.75x with a linear reduction of strength up to 40% to account for up-declares", aliases = {"strengthTierGraph"})
    @ReturnType(WebGraph.class)
    public WebGraph strengthTierGraph(@Me GuildDB db,
                                    NationList coalition1,
                                    NationList coalition2,
                                    @Switch("i") boolean includeInactives,
                                    @Switch("n") boolean includeApplicants,
                                    @Arg("Use the score/strength of coalition 1 nations at specific military unit levels") @Switch("a") MMRDouble col1MMR,
                                    @Arg("Use the score/strength of coalition 2 nations at specific military unit levels") @Switch("b") MMRDouble col2MMR,
                                    @Arg("Use the score of coalition 1 nations at specific average infrastructure levels") @Switch("c") Double col1Infra,
                                    @Arg("Use the score of coalition 2 nations at specific average infrastructure levels") @Switch("d") Double col2Infra,
                                    @Switch("s") @Timestamp Long snapshotDate) throws IOException {
        Set<DBNation> coalition1Nations = PW.getNationsSnapshot(coalition1.getNations(), coalition1.getFilter(), snapshotDate, db.getGuild());
        Set<DBNation> coalition2Nations = PW.getNationsSnapshot(coalition2.getNations(), coalition2.getFilter(), snapshotDate, db.getGuild());
        return new StrengthTierGraph(
                coalition1.getFilter(),
                coalition2.getFilter(),
                coalition1Nations,
                coalition2Nations,
                includeInactives,
                includeApplicants,
                col1MMR,
                col2MMR,
                col1Infra,
                col2Infra
        ).toHtmlJson();
    }

    @Command(desc = "Generate a graph of average trade buy and sell margin by day")
    @ReturnType(WebGraph.class)
    public WebGraph trademarginbyday(Set<ResourceType> resources, @Timestamp long start, @Default @Timestamp Long end,
                                   @Arg("Use the margin percent instead of absolute difference")
                                   @Default("true") boolean percent) throws IOException, GeneralSecurityException {
        Set<ResourceType> all = new HashSet<>(Arrays.asList(ResourceType.values()));
        List<DBTrade> trades = TradeMarginByDay.getTradesByResources(all, start, end);

        boolean[] rssIds = new boolean[ResourceType.values.length];
        for (ResourceType type : resources) rssIds[type.ordinal()] = true;
        List<DBTrade> filtered = new ObjectArrayList<>();
        for (DBTrade trade : trades) {
            if (rssIds[trade.getResource().ordinal()]) {
                filtered.add(trade);
            }
        }
        TradeMarginByDay table = new TradeMarginByDay(trades, resources, start, end, percent);
        return table.toHtmlJson();
    }

    /// TODO FIXME update or remove the trade price endpoint in other class
    @Command(desc = "Generate a graph of average buy and sell trade price by day")
    @ReturnType(WebGraph.class)
    public WebGraph tradepricebyday(
                                  Set<ResourceType> resources,
                                  int numDays) throws IOException, GeneralSecurityException {
        TradePriceByDay graph = new TradePriceByDay(resources, numDays);
        return graph.toHtmlJson();
    }

    @Command(desc = "Display a graph of the number of attacks by the specified nations per day over a time period")
    @ReturnType(WebGraph.class)
    public WebGraph warAttacksByDay(@Default Set<DBNation> nations,
                                  @Arg("Period of time to graph") @Default @Timestamp Long cutoff,
                                  @Arg("Restrict to a list of attack types") @Default Set<AttackType> allowedTypes) throws IOException {
        WarAttacksByDay table = new WarAttacksByDay(nations, cutoff, allowedTypes);
        return table.toHtmlJson();
    }

    @Command(desc = "Get a line graph by day of the war stats between two coalitions")
    @ReturnType(WebGraph.class)
    public WebGraph warCostsByDay(@Me JSONObject command,
                                Set<NationOrAlliance> coalition1, Set<NationOrAlliance> coalition2,
                                WarCostByDayMode type,
                                @Timestamp long time_start,
                                @Default @Timestamp Long time_end,
                                @Switch("o") boolean running_total,
                                @Switch("s") Set<WarStatus> allowedWarStatus,
                                @Switch("w") Set<WarType> allowedWarTypes,
                                @Switch("a") Set<AttackType> allowedAttackTypes,
                                @Switch("v") Set<SuccessType> allowedVictoryTypes) throws IOException {
        String nameA = command.getString("coalition1");
        String nameB = command.getString("coalition2");
        return new link.locutus.discord.commands.manager.v2.table.imp.WarCostByDay(
                nameA,
                nameB,
                coalition1,
                coalition2,
                type,
                time_start,
                time_end,
                running_total,
                allowedWarStatus,
                allowedWarTypes,
                allowedAttackTypes,
                allowedVictoryTypes
        ).toHtmlJson();
    }

    @Command(desc = "Graph of cost by day of each coalitions wars vs everyone")
    @ReturnType(WebGraph.class)
    public WebGraph warsCostRankingByDay(@Me JSONObject command,
                                       WarCostByDayMode type,
                                       WarCostMode mode,
                                       @Timestamp long time_start,
                                       @Default @Timestamp Long time_end,

                                       @Switch("c1") Set<NationOrAlliance> coalition1,
                                       @Switch("c2") Set<NationOrAlliance> coalition2,
                                       @Switch("c3") Set<NationOrAlliance> coalition3,
                                       @Switch("c4") Set<NationOrAlliance> coalition4,
                                       @Switch("c5") Set<NationOrAlliance> coalition5,
                                       @Switch("c6") Set<NationOrAlliance> coalition6,
                                       @Switch("c7") Set<NationOrAlliance> coalition7,
                                       @Switch("c8") Set<NationOrAlliance> coalition8,
                                       @Switch("c9") Set<NationOrAlliance> coalition9,
                                       @Switch("c10") Set<NationOrAlliance> coalition10,

                                       @Switch("o") boolean running_total,
                                       @Switch("s") Set<WarStatus> allowedWarStatus,
                                       @Switch("w") Set<WarType> allowedWarTypes,
                                       @Switch("a") Set<AttackType> allowedAttackTypes,
                                       @Switch("v") Set<SuccessType> allowedVictoryTypes
    ) throws IOException {
        if (time_end == null) time_end = Long.MAX_VALUE;
        Map<String, Set<NationOrAlliance>> args = new LinkedHashMap<>();
        args.put(command.has("coalition1") ? command.getString("coalition1") : null, coalition1);
        args.put(command.has("coalition2") ? command.getString("coalition2") : null, coalition2);
        args.put(command.has("coalition3") ? command.getString("coalition3") : null, coalition3);
        args.put(command.has("coalition4") ? command.getString("coalition4") : null, coalition4);
        args.put(command.has("coalition5") ? command.getString("coalition5") : null, coalition5);
        args.put(command.has("coalition6") ? command.getString("coalition6") : null, coalition6);
        args.put(command.has("coalition7") ? command.getString("coalition7") : null, coalition7);
        args.put(command.has("coalition8") ? command.getString("coalition8") : null, coalition8);
        args.put(command.has("coalition9") ? command.getString("coalition9") : null, coalition9);
        args.put(command.has("coalition10") ? command.getString("coalition10") : null, coalition10);
        args.entrySet().removeIf(f -> f.getValue() == null);

        return new WarCostRankingByDay(
                type,
                mode,
                time_start,
                time_end,
                args,
                running_total,
                allowedWarStatus,
                allowedWarTypes,
                allowedAttackTypes,
                allowedVictoryTypes
        ).toHtmlJson();
    }
}
