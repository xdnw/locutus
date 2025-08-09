package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import com.google.common.util.concurrent.AtomicDouble;
import com.opencsv.CSVWriter;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.apiv3.csv.DataDumpParser;
import link.locutus.discord.apiv3.csv.file.CitiesFile;
import link.locutus.discord.apiv3.csv.file.NationsFile;
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
import link.locutus.discord.commands.manager.v2.table.imp.AlliancesNationMetricByDay;
import link.locutus.discord.commands.manager.v2.table.imp.MetricByGroup;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.metric.AllianceMetric;
import link.locutus.discord.db.entities.metric.AllianceMetricMode;
import link.locutus.discord.db.entities.nation.DBNationSnapshot;
import link.locutus.discord.db.guild.SheetKey;
import link.locutus.discord.pnw.NationList;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.scheduler.ThrowingTriConsumer;
import link.locutus.discord.web.WebUtil;
import link.locutus.discord.web.commands.WM;
import org.json.JSONObject;

import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.io.StringWriter;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.*;

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
                    double[] cost = ResourceType.resourcesToArray(ResourceType.parseResources(costStr));
                    Project curr = Projects.get(projectStr);
                    String[] costSplit = costStr.split("\t");
                    newCosts.put(curr, cost);
                }
            }
            double[] newCost = newCosts.get(project);
            if (newCost != null) return newCost;
        }
        return ResourceType.resourcesToArray(project.cost());
    }

    public static void main(String[] args) throws IOException, ParseException, SQLException, LoginException, InterruptedException, ClassNotFoundException {
        Settings.INSTANCE.reload(Settings.INSTANCE.getDefaultFile());
        Settings.INSTANCE.WEB.PORT = 0;
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
        Map<Integer, Set<Project>> previousProjects = new Int2ObjectOpenHashMap<>();
        Map<Integer, Set<Project>> currentProjects = new Int2ObjectOpenHashMap<>();
        Map<Integer, Integer> previousCities = new Int2ObjectOpenHashMap<>();
        Map<Integer, Integer> currentCities = new Int2ObjectOpenHashMap<>();

        Map<Integer, double[]> oldCostByTier = new Int2ObjectOpenHashMap<>();
        Map<Integer, double[]> newCostByTier = new Int2ObjectOpenHashMap<>();
        Map<Integer, Double> citiesByTier = new Int2ObjectOpenHashMap<>();

        Map<Project, Map<Integer, Integer>> amtByTier = new HashMap<>();
        Map<Project, Integer> numProject = new Object2IntOpenHashMap<>();


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

        parser.load().iterateFiles(new ThrowingTriConsumer<Long, NationsFile, CitiesFile>() {
            private final double[] dailyOld = ResourceType.getBuffer();
            private final double[] dailyNew = ResourceType.getBuffer();
            private double citExpenses = 0;

            @Override
            public void acceptThrows(Long day, NationsFile nf, CitiesFile cf) throws IOException {
                if (day <= dayStart) return;
                Map<Integer, DBNationSnapshot> nations = nf.readNations(cf);

                System.out.println(
                        TimeUtil.format(TimeUtil.DD_MM_YYYY, TimeUtil.getTimeFromDay(day)) + "\t" +
                                MathMan.format(ResourceType.convertedTotal(dailyOld)) + "\t" +
                                MathMan.format(ResourceType.convertedTotal(dailyNew)) + "\t" +
                                MathMan.format(citExpenses)
                );
                dayTask.accept(day);
                Arrays.fill(dailyOld, 0);
                Arrays.fill(dailyNew, 0);
                citExpenses = 0;
                previousDay.set(day);

                for (Map.Entry<Integer, DBNationSnapshot> entry : nations.entrySet()) {
                    DBNationSnapshot nation = entry.getValue();
                    int nationId = entry.getKey();
                    int cities = nation.getCities();
                    Set<Project> projects = nation.getProjects();

                    currentProjects.put(nationId, projects);
                    currentCities.put(nationId, cities);
                    Set<Project> previous = previousProjects.get(nationId);
                    if (previous == null) return;
                    int numCities = previousCities.getOrDefault(nationId, 0);
                    if (numCities < cities) {
                        double cityCost = PW.City.cityCost(nation, numCities, cities);
                        citExpenses += cityCost;
                        cityTotal.addAndGet(cityCost);
                        citiesByTier.merge(numCities, cityCost, Double::sum);
                    }

                    for (Project project : projects) {
                        if (!previous.contains(project)) {
                            double[] oldCost = cost(project, false).clone();
                            double[] newCost = cost(project, true).clone();

                            // add to maps
                            ResourceType.add(dailyOld, oldCost.clone());
                            ResourceType.add(dailyNew, newCost.clone());
                            // add to totals
                            ResourceType.add(totalOld, oldCost.clone());
                            ResourceType.add(totalNew, newCost.clone());

                            ResourceType.add(oldCostByTier.computeIfAbsent(cities, f -> ResourceType.getBuffer()), oldCost.clone());
                            ResourceType.add(newCostByTier.computeIfAbsent(cities, f -> ResourceType.getBuffer()), newCost.clone());

                            amtByTier.computeIfAbsent(project, f -> new HashMap<>()).merge(cities, 1, Integer::sum);
                            numProject.merge(project, 1, Integer::sum);
                        }
                    }
                }

            }
        });

        // print output
        System.out.println("Total annual revenue:");
        System.out.println("Total " + ResourceType.toString(revenueTotal));
        System.out.println("Taxable " + ResourceType.toString(revenueTaxable));

        // print totals
        System.out.println("\n\n");
        System.out.println("Old " + ResourceType.toString(totalOld));
        System.out.println("New " + ResourceType.toString(totalNew));
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
            System.out.println(i + "\t" + ResourceType.convertedTotal(oldCost) + "\t" +
                    ResourceType.convertedTotal(newCost) + "\t" +
                    citiesByTier.getOrDefault(i, 0D) + "\t" +
                    ResourceType.toString(oldCost) + "\t" +
                    ResourceType.toString(newCost));
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

    @Command(desc = "Graph a set of nation metrics for the specified nations over a period of time based on daily nation and city snapshots")
    public void metricByGroup(@Me IMessageIO io, @Me @Default GuildDB db, @Me JSONObject command,
                                Set<NationAttributeDouble> metrics,
                                NationList nations,
                                @Default("getCities") NationAttributeDouble groupBy,
                                @Switch("i") boolean includeInactives,
                                @Switch("a") boolean includeApplicants,
                                @Switch("t") boolean total,
                                @Switch("s") @Timestamp Long snapshotDate,
                                @Switch("j") boolean attachJson,
                                @Switch("c") boolean attachCsv, @Switch("ss") boolean attach_sheet) throws IOException {
        Set<DBNation> nationsSet = PW.getNationsSnapshot(nations.getNations(), nations.getFilter(), snapshotDate, db == null ? null : db.getGuild());
        IMessageBuilder msg = new MetricByGroup(metrics, nationsSet, groupBy, includeInactives, includeApplicants, total)
                .writeMsg(io.create(), attachJson, attachCsv, attach_sheet ? db : null, SheetKey.METRIC_BY_GROUP);
        if (Settings.INSTANCE.ENABLED_COMPONENTS.WEB) {
            msg.append("\n**See also:** " + WebUtil.frontendUrl("view_graph/" + WM.api.metricByGroup.cmd.getName(), command));
        }
        msg.send();
    }

    @Command(desc = "Generate and save the alliance metrics over a period of time, using nation and city snapshots to calculate the metrics")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String saveMetrics(Set<AllianceMetric> metrics, @Default @Timestamp Long start, @Default @Timestamp Long end, @Switch("o") boolean overwrite, @Switch("t") boolean saveAllTurns) throws IOException, ParseException {
        if (metrics.isEmpty()) throw new IllegalArgumentException("No metrics provided");
        if (start == null) start = 0L;
        if (end == null) end = Long.MAX_VALUE;
        DataDumpParser parser = Locutus.imp().getDataDumper(true).load();
        Predicate<Long> dayFilter = dayFilter(start, end);
        Map.Entry<Integer, Integer> changesDays = AllianceMetric.saveDataDump(parser, new ArrayList<>(metrics), dayFilter, overwrite, saveAllTurns);
        return "Done. " + changesDays.getKey() + " changes made for " + changesDays.getValue() + " days.";
    }

    private static final ReentrantLock ALLIANCE_DATA_LOCK = new ReentrantLock();

    @Command(desc = "Get alliance attributes by day\n" +
            "If your metric does not relate to cities, set `skipCityData` to true to speed up the process.", viewable = true)
    @NoFormat
    public String AlliancesDataByDay(@Me @Default GuildDB db, @Me IMessageIO io, @Me JSONObject command,
                                     TypedFunction<DBNation, Double> metric,
                                     @Timestamp long start,
                                     @Timestamp long end,
                                     AllianceMetricMode mode,
                                     @Arg ("The alliances to include. Defaults to top 15") @Default Set<DBAlliance> alliances,
                                     @Default Predicate<DBNation> filter, @Switch("g") boolean graph, @Switch("a") boolean includeApps, @Switch("s") boolean attach_sheet) throws IOException, ParseException {
        if (!ALLIANCE_DATA_LOCK.tryLock()) {
            throw new IllegalArgumentException("Another instance of this command is running either by you or another user. Please wait and try again.");
        }
        try {
        CompletableFuture<IMessageBuilder> msg = io.send("Please wait...");
        Map<Long, double[]> valuesByDay = AlliancesNationMetricByDay.generateData(new Consumer<Long>() {
            @Override
            public void accept(Long day) {
                io.updateOptionally(msg, "Processing day " + day + "...");
            }
        }, metric, start, end, mode, alliances, filter, includeApps);

        List<String> header = new ArrayList<>(List.of("date"));
        Map<Integer, Integer> headerIndexByAAId = new Int2IntOpenHashMap();
        for (DBAlliance alliance : alliances) {
            headerIndexByAAId.put(alliance.getAlliance_id(), header.size());
            header.add(alliance.getName());
        }

        // new csv writer
        StringWriter stringWriter = new StringWriter();
        CSVWriter csvWriter = new CSVWriter(stringWriter);
        // write header
        csvWriter.writeNext(header.toArray(new String[0]));

        for (Map.Entry<Long, double[]> entry : valuesByDay.entrySet()) {
            long day = entry.getKey();
            double[] values = entry.getValue();
            String dateStr = TimeUtil.format(TimeUtil.DD_MM_YYYY, TimeUtil.getTimeFromDay(day));
            String[] row = new String[header.size()];
            row[0] = dateStr;
            for (int i = 0; i < values.length; i++) {
                int headerIndex = headerIndexByAAId.get(i);
                row[headerIndex] = Double.toString(values[i]);
            }
            csvWriter.writeNext(row);
        }

        IMessageBuilder msg2 = io.create().file("alliance_data.csv", stringWriter.toString().getBytes());
        if (graph) {
            Set<DBAlliance> finalAlliances = AlliancesNationMetricByDay.resolveAlliances(alliances);
            AlliancesNationMetricByDay graphObj = new AlliancesNationMetricByDay(valuesByDay, metric, start, end, finalAlliances);
            graphObj.writeMsg(msg2, false, false, attach_sheet ? db : null, SheetKey.ALLIANCE_METRIC_DAY);
        }
        if (Settings.INSTANCE.ENABLED_COMPONENTS.WEB) {
            msg2.append("\n**See also:** " + WebUtil.frontendUrl("view_graph/" + WM.api.AlliancesDataByDay.cmd.getName(), command));
        }
        msg2.send();
        } finally {
            ALLIANCE_DATA_LOCK.unlock();
        }
        return null;
    }

//    @Command
//    public String AlliancesCityDataByDay(@Me IMessageIO io, TypedFunction<DBCity, Double> metric, @Timestamp long start, @Timestamp long end, AllianceMetricMode mode, @Default Set<DBAlliance> alliances) {
//        return null;
//    }
//
//    @Command
//    public String AllianceDataCsvByDay(@Me IMessageIO io, TypedFunction<DBNation, Double> metric, @Timestamp long start, @Timestamp long end, AllianceMetricMode mode, @Default Set<DBAlliance> alliances) {
//        List<IAllianceMetric> metrics = new ArrayList<>();
//        return null;
//    }

    // then equivalent nation, city, nations metrics

}
