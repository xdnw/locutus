package link.locutus.discord.commands.manager.v2.table.imp;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import link.locutus.discord.apiv1.enums.*;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Switch;
import link.locutus.discord.commands.manager.v2.binding.annotation.Timestamp;
import link.locutus.discord.commands.manager.v2.table.TableNumberFormat;
import link.locutus.discord.commands.manager.v2.table.TimeFormat;
import link.locutus.discord.commands.manager.v2.table.TimeNumericTable;
import link.locutus.discord.db.entities.AttackCost;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.WarParser;
import link.locutus.discord.db.entities.WarStatus;
import link.locutus.discord.pnw.NationOrAlliance;
import link.locutus.discord.web.commands.binding.value_types.GraphType;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

public class WarCostRankingByDay extends SimpleTable<Map<String, WarParser>> {
    private final boolean running_total;
    private long min, max;
    private final Map<String, WarParser> coalitions;
    private final Map<String, Map<Long, AttackCost>> coalitionsByDay;
    private final Function<AttackCost, Double> delegate;

    public WarCostRankingByDay(WarCostByDayMode type,
                               WarCostMode mode,
                               long time_start,
                               Long time_end,
                               Map<String, Set<NationOrAlliance>> args,
                               boolean running_total,
                               Set<WarStatus> allowedWarStatus,
                               Set<WarType> allowedWarTypes,
                               Set<AttackType> allowedAttackTypes,
                               Set<SuccessType> allowedVictoryTypes) {
        if (time_end == null) time_end = Long.MAX_VALUE;

        coalitions = new Object2ObjectLinkedOpenHashMap<>();
        this.running_total = running_total;
        this.coalitionsByDay = new Object2ObjectLinkedOpenHashMap<>();

        for (Map.Entry<String, Set<NationOrAlliance>> entry : args.entrySet()) {
            String colName = entry.getKey();
            Set<NationOrAlliance> col = entry.getValue();
            WarParser parser = WarParser.of(col, null, time_start, time_end)
                    .allowWarStatuses(allowedWarStatus)
                    .allowedWarTypes(allowedWarTypes)
                    .allowedAttackTypes(allowedAttackTypes)
                    .allowedSuccessTypes(allowedVictoryTypes);
            coalitions.put(colName, parser);

            Map<Long, AttackCost> byDay = parser.toWarCostByDay(type == WarCostByDayMode.BUILDING, false, false, false, type.isAttackType);
            coalitionsByDay.put(colName, byDay);
        }

        this.min = Long.MAX_VALUE;
        this.max = Long.MIN_VALUE;
        for (Map.Entry<String, Map<Long, AttackCost>> entry : coalitionsByDay.entrySet()) {
            min = Math.min(min, Collections.min(entry.getValue().keySet()));
            max = Math.max(max, Collections.max(entry.getValue().keySet()));
        }

        String[] labels = coalitions.keySet().toArray(new String[0]);

        List<TimeNumericTable<Map<String, WarParser>>> tables = new ArrayList<>();

        BiFunction<Double, Double, Double> costSign = mode.getCostFunc();
        BiFunction<AttackCost, Boolean, Double> statFunc = type.apply();

        this.delegate = (cost) -> {
            double a = statFunc.apply(cost, true);
            double b = statFunc.apply(cost, false);
            return costSign.apply(a, b);
        };

        setTitle(type.toString() + " Ranking by Day");
        setLabelX("Day");
        setLabelY("Rank");
        setLabels(labels);

        writeData();
    }

    @Override
    protected SimpleTable<Map<String, WarParser>> writeData() {
        for (long day = min; day <= max; day++) {
            long dayRelative = day - min;
            add(dayRelative, coalitions);
        }
        return this;
    }

    @Override
    public TimeFormat getTimeFormat() {
        return TimeFormat.DAYS_TO_DATE;
    }

    @Override
    public TableNumberFormat getNumberFormat() {
        return TableNumberFormat.SI_UNIT;
    }

    @Override
    public GraphType getGraphType() {
        return GraphType.LINE;
    }

    @Override
    public long getOrigin() {
        return min;
    }

    @Override
    public void add(long day, Map<String, WarParser> costMap) {
        WarCostByDayMode.addRanking(this, day, min, costMap, coalitionsByDay, cost -> delegate.apply(cost));
        if (running_total) WarCostByDay.processTotal(this);
    }
}
