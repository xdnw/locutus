package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import com.opencsv.CSVWriter;
import link.locutus.discord.apiv3.DataDumpParser;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.annotation.Switch;
import link.locutus.discord.commands.manager.v2.binding.annotation.Timestamp;
import link.locutus.discord.commands.manager.v2.binding.bindings.TypedFunction;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBCity;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.metric.AllianceMetric;
import link.locutus.discord.db.entities.metric.AllianceMetricMode;
import link.locutus.discord.db.entities.metric.CountNationMetric;
import link.locutus.discord.db.entities.metric.IAllianceMetric;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.TimeUtil;
import org.jooq.meta.derby.sys.Sys;

import java.io.IOException;
import java.io.StringWriter;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class AllianceMetricCommands {

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

    @Command
    @RolePermission(value = Roles.ADMIN, root = true)
    public String saveMetrics(Set<AllianceMetric> metrics, @Default @Timestamp Long start, @Default @Timestamp Long end, @Switch("o") boolean overwrite) throws IOException, ParseException {
        if (start == null) start = 0L;
        if (end == null) end = Long.MAX_VALUE;
        DataDumpParser parser = new DataDumpParser().load();
        Predicate<Long> dayFilter = dayFilter(start, end);
        AllianceMetric.saveDataDump(parser, new ArrayList<>(metrics), dayFilter, overwrite);
        return "Done";
    }

    @Command
    public String AllianceDataByDay(@Me IMessageIO io, TypedFunction<DBNation, Double> metric, @Timestamp long start, @Timestamp long end, AllianceMetricMode mode, @Default Set<DBAlliance> alliances) throws IOException, ParseException {
        if (alliances.size() > 100) {
            alliances.removeIf(f -> f.getNations(true, 10080, true).isEmpty());
        }
        if (alliances.isEmpty()) return "No alliances found";
        Set<Integer> aaIds = alliances.stream().map(DBAlliance::getAlliance_id).collect(Collectors.toSet());
        List<IAllianceMetric> metrics = new ArrayList<>(List.of(new CountNationMetric(metric::apply, null, mode, f -> aaIds.contains(f.getAlliance_id()))));
        Predicate<Long> dayFilter = dayFilter(start, end);

        List<String> header = new ArrayList<>(Arrays.asList("date"));
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

        DataDumpParser parser = new DataDumpParser().load();
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
            csvWriter.writeNext(row);
        });
        io.create().file("alliance_data.csv", stringWriter.toString().getBytes()).send();
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

    public static void main(String[] args) {
        long now = System.currentTimeMillis();
        SimpleDateFormat format = TimeUtil.DD_MM_YYYY;
        System.out.println(format.format(now));
    }

    // then equivalent nation, city, nations metrics

}