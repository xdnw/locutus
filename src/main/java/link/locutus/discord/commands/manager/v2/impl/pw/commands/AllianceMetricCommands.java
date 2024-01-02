package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Timestamp;
import link.locutus.discord.commands.manager.v2.binding.bindings.TypedFunction;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBCity;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.metric.AllianceMetricMode;
import link.locutus.discord.db.entities.metric.IAllianceMetric;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class AllianceMetricCommands {
    @Command
    public void AllianceDataByDay(TypedFunction<DBNation, Double> metric, @Timestamp long start, @Timestamp long end, AllianceMetricMode mode, @Default Set<DBAlliance> alliances) {
        List<IAllianceMetric> metrics = new ArrayList<>();
    }

    @Command
    public void AlliancesCityDataByDay(TypedFunction<DBCity, Double> metric, @Timestamp long start, @Timestamp long end, AllianceMetricMode mode, @Default Set<DBAlliance> alliances) {

    }

    @Command
    public void AllianceDataCsvByDay(TypedFunction<DBNation, Double> metric, @Timestamp long start, @Timestamp long end, AllianceMetricMode mode, @Default Set<DBAlliance> alliances) {
        List<IAllianceMetric> metrics = new ArrayList<>();
    }

}
