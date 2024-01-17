package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import com.google.common.util.concurrent.AtomicDouble;
import com.opencsv.CSVWriter;
import de.siegmar.fastcsv.reader.CsvRow;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.apiv3.DataDumpParser;
import link.locutus.discord.commands.manager.v2.binding.annotation.Arg;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.annotation.NoFormat;
import link.locutus.discord.commands.manager.v2.binding.annotation.Switch;
import link.locutus.discord.commands.manager.v2.binding.annotation.Timestamp;
import link.locutus.discord.commands.manager.v2.binding.bindings.TypedFunction;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.NationAttributeDouble;
import link.locutus.discord.commands.rankings.table.TableNumberFormat;
import link.locutus.discord.commands.rankings.table.TimeFormat;
import link.locutus.discord.commands.rankings.table.TimeNumericTable;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBCity;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.metric.AllianceMetric;
import link.locutus.discord.db.entities.metric.AllianceMetricMode;
import link.locutus.discord.db.entities.metric.CountNationMetric;
import link.locutus.discord.db.entities.metric.IAllianceMetric;
import link.locutus.discord.pnw.NationList;
import link.locutus.discord.pnw.NationOrAlliance;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.math.ArrayUtil;
import link.locutus.discord.util.scheduler.TriConsumer;
import org.jooq.meta.derby.sys.Sys;

import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.io.StringWriter;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class AllianceMetricCommands {

    private static String projectsStr = """
            Metropolitan Planning
            Mars Landing
            Space Program
            Moon Landing
            Telecommunications Satellite
            Advanced Urban Planning
            Spy Satellite
            Surveillance Network
            Advanced Engineering Corps
            Research and Development Center
            Uranium Enrichment Program
            Nuclear Research Facility
            Urban Planning
            Activity Center
            Advanced Pirate Economy
            Arable Land Agency
            Arms Stockpile
            Bauxiteworks
            Bureau of Domestic Affairs
            Center for Civil Engineering
            Clinical Research Center
            Emergency Gasoline Reserve
            Fallout Shelter
            Government Support Agency
            Green Technologies
            Intelligence Agency
            International Trade Center
            Iron Dome
            Ironworks
            Mass Irrigation
            Military Salvage
            Missile Launch Pad
            Pirate Economy
            Propaganda Bureau
            Recycling Initiative
            Specialized Police Training Program
            Vital Defense System""";

    private static String costsStr = """
            0	1,500,000	7,000	7,000	7,000	7,000	7,000	7,000	0	0	0	0
            200,000,000	0	0	20,000	20,000	0	0	0	20,000	20,000	20,000	20,000
            50,000,000	0	0	0	0	0	0	0	0	0	0	25,000
            50,000,000	0	0	5,000	10,000	0	0	0	5,000	5,000	5,000	5,000
            300,000,000	0	0	10,000	10,000	0	10,000	0	0	0	0	10,000
            0	1,000,000	5,000	5,000	5,000	5,000	5,000	5,000	0	0	0	0
            20,000,000	0	10,000	10,000	0	10,000	10,000	10,000	0	0	0	0
            50,000,000	0	15,000	0	0	15,000	15,000	15,000	0	0	0	50,000
            50,000,000	0	0	0	1,000	0	0	0	10,000	10,000	0	0
            50,000,000	100,000	0	0	1,000	0	0	0	0	0	0	5,000
            25,000,000	0	500	500	2,500	500	500	500	0	0	0	0
            75,000,000	0	0	0	5,000	0	0	0	5,000	0	0	5,000
            0	500,000	3,000	3,000	3,000	3,000	3,000	3,000	0	0	0	0
            500,000	1,000	0	0	0	0	0	0	0	0	0	0
            50,000,000	0	10,000	10,000	0	10,000	10,000	10,000	0	0	0	0
            3,000,000	0	1,500	0	0	1,500	0	0	0	0	0	0
            10,000,000	0	500	500	0	500	500	500	0	0	0	0
            10,000,000	0	500	500	0	500	500	500	0	0	0	0
            20,000,000	100,000	5,000	5,000	0	5,000	5,000	5,000	0	0	0	0
            3,000,000	0	0	1,000	0	0	1,000	1,000	0	0	0	0
            10,000,000	100,000	0	0	0	0	0	0	0	0	0	0
            10,000,000	0	500	500	0	500	500	500	0	0	0	0
            25,000,000	100,000	0	0	0	10,000	0	0	0	0	0	15,000
            20,000,000	200,000	0	0	0	0	0	0	0	0	0	10,000
            50,000,000	100,000	0	10,000	0	0	10,000	0	0	0	0	10,000
            5,000,000	0	0	0	0	0	0	0	500	0	500	0
            50,000,000	0	0	0	0	0	0	0	0	0	0	10,000
            15,000,000	0	0	0	0	0	0	0	0	5,000	0	0
            10,000,000	0	500	500	0	500	500	500	0	0	0	0
            10,000,000	50,000	500	500	0	500	500	500	0	0	0	0
            20,000,000	0	0	0	0	0	0	0	5,000	0	5,000	5,000
            15,000,000	0	0	0	0	0	0	0	5,000	5,000	0	5,000
            25,000,000	0	7,500	7,500	0	7,500	7,500	7,500	0	0	0	0
            10,000,000	0	0	0	0	0	0	0	2,000	2,000	2,000	2,000
            10,000,000	100,000	0	0	0	0	0	0	0	0	0	0
            50,000,000	250,000	0	0	0	0	0	0	0	0	0	5,000
            40,000,000	0	0	0	0	0	0	0	5,000	5,000	5,000	5,000""";

    private static Map<Project, double[]> newCosts = new HashMap<>();

    private static double[] cost(Project project, boolean isNew) {
        if (isNew) {
            if (newCosts.isEmpty()) {
                String[] projects = projectsStr.split("\n");
                String[] costs = costsStr.split("\n");
                for (int i = 0; i < projects.length; i++) {
                    String projectStr = projects[i];
                    String costStr = costs[i];
                    double[] cost = PnwUtil.resourcesToArray(PnwUtil.parseResources(costStr));
                    Project curr = Projects.get(projectStr);
                    String[] costSplit = costStr.split("\t");
                    newCosts.put(curr, cost);
                }
            }
            double[] newCost = newCosts.get(project);
            if (newCost != null) return newCost;
        }
        return PnwUtil.resourcesToArray(project.cost());
    }

//    public static void main(String[] args) throws IOException, ParseException, SQLException, LoginException, InterruptedException, ClassNotFoundException {
//        Settings.INSTANCE.reload(Settings.INSTANCE.getDefaultFile());
//        Settings.INSTANCE.WEB.PORT_HTTPS = 0;
//        Settings.INSTANCE.WEB.PORT_HTTP = 8000;
//        Settings.INSTANCE.ENABLED_COMPONENTS.disableListeners();
//        Settings.INSTANCE.ENABLED_COMPONENTS.disableTasks();
//        Settings.INSTANCE.ENABLED_COMPONENTS.DISCORD_BOT = false;
//        Settings.INSTANCE.ENABLED_COMPONENTS.EVENTS = false;
//        Settings.INSTANCE.ENABLED_COMPONENTS.WEB = false;
//        Settings.INSTANCE.ENABLED_COMPONENTS.SLASH_COMMANDS = false;
//        Settings.INSTANCE.ENABLED_COMPONENTS.MESSAGE_COMMANDS = false;
//        Settings.INSTANCE.ENABLED_COMPONENTS.CREATE_DATABASES_ON_STARTUP = true;
//
//        Locutus locutus = Locutus.create();
//        locutus.start();
//
//        // get unit purchases in the past year
//        Locutus.imp().getNationDB().unit
//        // get attacks for past year
//        // get times each war ends
//
//    }

    public static void main(String[] args) throws IOException, ParseException, SQLException, LoginException, InterruptedException, ClassNotFoundException {
        Settings.INSTANCE.reload(Settings.INSTANCE.getDefaultFile());
        Settings.INSTANCE.WEB.PORT_HTTPS = 0;
        Settings.INSTANCE.WEB.PORT_HTTP = 8000;
        Settings.INSTANCE.ENABLED_COMPONENTS.disableListeners();
        Settings.INSTANCE.ENABLED_COMPONENTS.disableTasks();
        Settings.INSTANCE.ENABLED_COMPONENTS.DISCORD_BOT = false;
        Settings.INSTANCE.ENABLED_COMPONENTS.EVENTS = false;
        Settings.INSTANCE.ENABLED_COMPONENTS.WEB = false;
        Settings.INSTANCE.ENABLED_COMPONENTS.SLASH_COMMANDS = false;
        Settings.INSTANCE.ENABLED_COMPONENTS.MESSAGE_COMMANDS = false;
        Settings.INSTANCE.ENABLED_COMPONENTS.CREATE_DATABASES_ON_STARTUP = true;

        Locutus locutus = Locutus.create();
        locutus.start();
        DataDumpParser parser = locutus.getDataDumper(true);

        AtomicLong previousDay = new AtomicLong();
        Map<Integer, Set<Project>> previousProjects = new HashMap<>();
        Map<Integer, Set<Project>> currentProjects = new HashMap<>();
        Map<Integer, Integer> previousCities = new HashMap<>();
        Map<Integer, Integer> currentCities = new HashMap<>();

        Map<Integer, double[]> oldCostByTier = new HashMap<>();
        Map<Integer, double[]> newCostByTier = new HashMap<>();
        Map<Integer, Double> citiesByTier = new HashMap<>();

        Map<Project, Map<Integer, Integer>> amtByTier = new HashMap<>();
        Map<Project, Integer> numProject = new HashMap<>();


        Consumer<Long> dayTask = new Consumer<Long>() {
            @Override
            public void accept(Long day) {
                previousDay.set(day);
                previousProjects.clear();
                previousProjects.putAll(currentProjects);
                currentProjects.clear();
                previousCities.clear();
                previousCities.putAll(currentCities);
                currentCities.clear();
            }
        };

        double[] revenueTotal = ResourceType.getBuffer();
        double[] revenueTaxable = ResourceType.getBuffer();

        System.out.println("day\tvalue_old\tvalue_new\tcity_cost");
        double[] totalOld = ResourceType.getBuffer();
        double[] totalNew = ResourceType.getBuffer();
        AtomicDouble cityTotal = new AtomicDouble();

        long dayStart = TimeUtil.getDay() - 365;
        parser.iterateAll(f -> f > dayStart, new TriConsumer<Long, DataDumpParser.NationHeader, CsvRow>() {

            private double[] dailyOld = ResourceType.getBuffer();
            private double[] dailyNew = ResourceType.getBuffer();
            private double citExpenses = 0;

            @Override
            public void consume(Long day, DataDumpParser.NationHeader header, CsvRow row) {
                if (day != previousDay.get()) {
                    System.out.println(
                            TimeUtil.DD_MM_YYYY.format(TimeUtil.getTimeFromDay(day)) + "\t" +
                                    MathMan.format(PnwUtil.convertedTotal(dailyOld)) + "\t" +
                                    MathMan.format(PnwUtil.convertedTotal(dailyNew)) + "\t" +
                                    MathMan.format(citExpenses)
                    );

                    dayTask.accept(day);

                    Arrays.fill(dailyOld, 0);
                    Arrays.fill(dailyNew, 0);
                    citExpenses = 0;
                    previousDay.set(day);
                }

                long now = TimeUtil.getTimeFromDay(day);
                try {
                    DBNation nation = parser.loadNation(header, row, f -> true, f -> true, true, true, now);
                    currentProjects.put(nation.getId(), nation.getProjects());
                    currentCities.put(nation.getId(), nation.getCities());
                    Set<Project> previous = previousProjects.get(nation.getId());
                    if (previous == null) return;
                    int numCities = previousCities.getOrDefault(nation.getId(), 0);
                    if (numCities < nation.getCities()) {
                        double cityCost = PnwUtil.cityCost(nation, numCities, nation.getCities());
                        citExpenses += cityCost;
                        cityTotal.addAndGet(cityCost);
                        citiesByTier.merge(numCities, cityCost, Double::sum);
                    }

                    for (Project project : nation.getProjects()) {
                        if (!previous.contains(project)) {
                            double[] oldCost = cost(project, false).clone();
                            double[] newCost = cost(project, true).clone();

                            // add to maps
                            PnwUtil.add(dailyOld, oldCost.clone());
                            PnwUtil.add(dailyNew, newCost.clone());
                            // add to totals
                            PnwUtil.add(totalOld, oldCost.clone());
                            PnwUtil.add(totalNew, newCost.clone());

                            PnwUtil.add(oldCostByTier.computeIfAbsent(nation.getCities(), f -> ResourceType.getBuffer()), oldCost.clone());
                            PnwUtil.add(newCostByTier.computeIfAbsent(nation.getCities(), f -> ResourceType.getBuffer()), newCost.clone());

                            amtByTier.computeIfAbsent(project, f -> new HashMap<>()).merge(nation.getCities(), 1, Integer::sum);
                            numProject.merge(project, 1, Integer::sum);
                        }
                    }

                } catch (ParseException e) {
                    throw new RuntimeException(e);
                }

            }
        }, null, new Consumer<Long>() {
            @Override
            public void accept(Long day) {

            }
        });
        // print output
        System.out.println("Total annual revenue:");
        System.out.println("Total " + PnwUtil.resourcesToString(revenueTotal));
        System.out.println("Taxable " + PnwUtil.resourcesToString(revenueTaxable));

        // print totals
        System.out.println("\n\n");
        System.out.println("Old " + PnwUtil.resourcesToString(totalOld));
        System.out.println("New " + PnwUtil.resourcesToString(totalNew));
        System.out.println("City " + MathMan.format(cityTotal.get()));
        // Note: Revenue may be innaccurate

        System.out.println("\n\n\n");

        // print cost by tier (c1 -> c70)
        System.out.println("tier\told\tnew\tcities\told_map\tnew_map");
        for (int i = 1; i <= 70; i++) {
            double[] oldCost = oldCostByTier.get(i);
            double[] newCost = newCostByTier.get(i);
            if (oldCost == null) oldCost = ResourceType.getBuffer();
            if (newCost == null) newCost = ResourceType.getBuffer();
            System.out.println(i + "\t" + PnwUtil.convertedTotal(oldCost) + "\t" +
                    PnwUtil.convertedTotal(newCost) + "\t" +
                    citiesByTier.getOrDefault(i, 0D) + "\t" +
                    PnwUtil.resourcesToString(oldCost) + "\t" +
                    PnwUtil.resourcesToString(newCost));
        }

        // print

        System.out.println("\n\n\n\n");
        System.out.println("project\tamt\tspent\t%");
        ResourceType type = ResourceType.FOOD;
        double totalUranium = totalOld[type.ordinal()];
        for (Project project : Projects.values) {
            double uraniumCost = project.cost().getOrDefault(type, 0d);
            if (uraniumCost > 0) {
                int amtBuilt = numProject.getOrDefault(project, 0);
                double totalUraniumSpent = amtBuilt * uraniumCost;
                double pctUranium = 100 * totalUraniumSpent / totalUranium;

                System.out.println(project.name() + "\t" + amtBuilt + "\t" + MathMan.format(totalUraniumSpent) + "\t" + MathMan.format(pctUranium) + "%");
            }
        }


    }


    private Predicate<Long> dayFilter(Long start, Long end) {
        if (start == null) start = 0L;
        if (end == null) end = Long.MAX_VALUE;
        Long finalStart = start;
        Long finalEnd = end;
        return day -> {
            long time = TimeUtil.getTimeFromDay(day);
            return time >= finalStart && time <= finalEnd;
        };
    }

    @Command()
    public void metricByGroup(@Me IMessageIO io, @Me GuildDB db,
                                Set<NationAttributeDouble> metrics,
                                NationList nations,
                                @Default("getCities") NationAttributeDouble groupBy,
                                @Switch("i") boolean includeInactives,
                                @Switch("a") boolean includeApplicants,
                                @Switch("t") boolean total,
                                @Switch("s") @Timestamp Long snapshotDate,
                                @Switch("j") boolean attachJson,
                                @Switch("c") boolean attachCsv) throws IOException {
        Set<DBNation> nationsSet = PnwUtil.getNationsSnapshot(nations.getNations(), nations.getFilter(), snapshotDate, db.getGuild(), false);
        TimeNumericTable table = TimeNumericTable.metricByGroup(metrics, nationsSet, groupBy, includeInactives, includeApplicants, total);
        table.write(io, TimeFormat.SI_UNIT, TableNumberFormat.SI_UNIT, attachJson, attachCsv);
    }

    @Command
    @RolePermission(value = Roles.ADMIN, root = true)
    public String saveMetrics(Set<AllianceMetric> metrics, @Default @Timestamp Long start, @Default @Timestamp Long end, @Switch("o") boolean overwrite, @Switch("t") boolean saveAllTurns) throws IOException, ParseException {
        if (metrics.isEmpty()) throw new IllegalArgumentException("No metrics provided");
        if (start == null) start = 0L;
        if (end == null) end = Long.MAX_VALUE;
        DataDumpParser parser = Locutus.imp().getDataDumper(true);
        Predicate<Long> dayFilter = dayFilter(start, end);
        Map.Entry<Integer, Integer> changesDays = AllianceMetric.saveDataDump(parser, new ArrayList<>(metrics), dayFilter, overwrite, saveAllTurns);
        return "Done. " + changesDays.getKey() + " changes made for " + changesDays.getValue() + " days.";
    }

    @Command
    @RolePermission(value = Roles.ADMIN, root = true)
    @NoFormat
    public String AlliancesDataByDay(@Me IMessageIO io, TypedFunction<DBNation, Double> metric, @Timestamp long start, @Timestamp long end, AllianceMetricMode mode, @Arg ("The alliances to include. Defaults to top 80") @Default Set<DBAlliance> alliances, @Switch("g") boolean graph) throws IOException, ParseException {
        if (alliances == null) alliances = Locutus.imp().getNationDB().getAlliances(true, true, true, 80);
        if (graph && alliances.size() > 8) {
            throw new IllegalArgumentException("Cannot graph more than 8 alliances.");
        }
        if (alliances.size() > 100) {
            alliances.removeIf(f -> f.getNations(true, 10080, true).isEmpty());
        }
        if (alliances.isEmpty()) return "No alliances found";
        Set<Integer> aaIds = alliances.stream().map(DBAlliance::getAlliance_id).collect(Collectors.toSet());
        List<IAllianceMetric> metrics = new ArrayList<>(List.of(new CountNationMetric(metric::apply, null, mode, f -> aaIds.contains(f.getAlliance_id())).allianceFilter(aaIds::contains)));
        Predicate<Long> dayFilter = dayFilter(start, end);

        List<String> header = new ArrayList<>(List.of("date"));
        Map<Integer, Integer> allianceIdToHeaderI = new HashMap<>();
        for (DBAlliance alliance : alliances) {
            allianceIdToHeaderI.put(alliance.getAlliance_id(), header.size());
            header.add(alliance.getName());
        }
        // new csv writer
        StringWriter stringWriter = new StringWriter();
        CSVWriter csvWriter = new CSVWriter(stringWriter, ',');

        // write header
        csvWriter.writeNext(header.toArray(new String[0]));

        CompletableFuture<IMessageBuilder> msg = io.send("Please wait...");
        AtomicLong timer = new AtomicLong(System.currentTimeMillis());
        AtomicLong counter = new AtomicLong(0);

        DataDumpParser parser = Locutus.imp().getDataDumper(true);
        Map<Long, double[]> valuesByDay = new Long2ObjectOpenHashMap<>();
        Set<DBAlliance> finalAlliances = alliances;
        AllianceMetric.runDataDump(parser, metrics, dayFilter, (imetric, day, value) -> {
            long timestamp = TimeUtil.getTimeFromDay(day);
            SimpleDateFormat format = TimeUtil.DD_MM_YYYY;
            String dateStr = format.format(timestamp);
            String[] row = new String[header.size()];
            row[0] = dateStr;
            for (Map.Entry<Integer, Double> entry : value.entrySet()) {
                int allianceId = entry.getKey();
                int headerI = allianceIdToHeaderI.get(allianceId);
                row[headerI] = entry.getValue().toString();
            }
            if (graph) {
                double[] values = valuesByDay.computeIfAbsent(day, f -> new double[finalAlliances.size()]);
                for (Map.Entry<Integer, Double> entry : value.entrySet()) {
                    int allianceId = entry.getKey();
                    int headerI = allianceIdToHeaderI.get(allianceId);
                    values[headerI - 1] = entry.getValue();
                }
            }
            csvWriter.writeNext(row);
            metric.clearCache();

            if (System.currentTimeMillis() - timer.get() > 10000) {
                timer.getAndSet(System.currentTimeMillis());
                io.updateOptionally(msg, "Processing day " + counter.incrementAndGet() + "...");
            }
        });
        IMessageBuilder msg2 = io.create().file("alliance_data.csv", stringWriter.toString().getBytes());
        if (graph) {
            long minDay = valuesByDay.keySet().stream().min(Long::compareTo).orElse(0L);
            long maxDay = valuesByDay.keySet().stream().max(Long::compareTo).orElse(0L);
            String[] labels = alliances.stream().map(DBAlliance::getName).toArray(String[]::new);
            double[] buffer = new double[labels.length];
            TimeNumericTable<Void> table = new TimeNumericTable<>(metric.getName() + " by day", "day", metric.getName(), labels) {
                @Override
                public void add(long day, Void cost) {
                    double[] values = valuesByDay.get(day);
                    if (values != null) {
                        System.arraycopy(values, 0, buffer, 0, values.length);
                    }
                    add(day, buffer);
                }
            };
            for (long day = minDay; day <= maxDay; day++) {
                table.add(day, (Void) null);
            }
            table.writeMsg(msg2, TimeFormat.DAYS_TO_DATE, TableNumberFormat.SI_UNIT, false, false);
        }
        msg2.send();
        return null;
    }

    @Command
    public String AlliancesCityDataByDay(@Me IMessageIO io, TypedFunction<DBCity, Double> metric, @Timestamp long start, @Timestamp long end, AllianceMetricMode mode, @Default Set<DBAlliance> alliances) {
        return null;
    }

    @Command
    public String AllianceDataCsvByDay(@Me IMessageIO io, TypedFunction<DBNation, Double> metric, @Timestamp long start, @Timestamp long end, AllianceMetricMode mode, @Default Set<DBAlliance> alliances) {
        List<IAllianceMetric> metrics = new ArrayList<>();
        return null;
    }

    // then equivalent nation, city, nations metrics

}
