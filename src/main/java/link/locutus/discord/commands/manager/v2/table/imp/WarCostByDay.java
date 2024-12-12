package link.locutus.discord.commands.manager.v2.table.imp;

import de.erichseifert.gral.data.DataTable;
import de.erichseifert.gral.data.Row;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AbstractCursor;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.SuccessType;
import link.locutus.discord.apiv1.enums.WarCostByDayMode;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Switch;
import link.locutus.discord.commands.manager.v2.binding.annotation.Timestamp;
import link.locutus.discord.commands.manager.v2.table.TableNumberFormat;
import link.locutus.discord.commands.manager.v2.table.TimeDualNumericTable;
import link.locutus.discord.commands.manager.v2.table.TimeFormat;
import link.locutus.discord.commands.manager.v2.table.TimeNumericTable;
import link.locutus.discord.db.entities.AttackCost;
import link.locutus.discord.db.entities.WarParser;
import link.locutus.discord.db.entities.WarStatus;
import link.locutus.discord.pnw.NationOrAlliance;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.web.commands.binding.value_types.GraphType;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

public class WarCostByDay extends SimpleTable<AttackCost> {
    private final long min;
    private final long max;
    private final WarCostByDayMode type;
    private final Map<Long, AttackCost> warCostByDay;
    private final BiFunction<AttackCost, Boolean, Double> costFunc;
    private final boolean running_total;

    public WarCostByDay(
                        String col1Str,
                        String col2Str,
                        Set<NationOrAlliance> coalition1,
                        Set<NationOrAlliance> coalition2,
                        WarCostByDayMode type,
                        @Timestamp long time_start,
                        @Default @Timestamp Long time_end,
                        @Switch("o") boolean running_total,
                        @Switch("s") Set<WarStatus> allowedWarStatus,
                        @Switch("w") Set<WarType> allowedWarTypes,
                        @Switch("a") Set<AttackType> allowedAttackTypes,
                        @Switch("v") Set<SuccessType> allowedVictoryTypes) {
        if (time_end == null) time_end = Long.MAX_VALUE;
        this.type = type;
        this.running_total = running_total;

        WarParser parser = WarParser.of(coalition1, coalition2, time_start, time_end)
                .allowWarStatuses(allowedWarStatus)
                .allowedWarTypes(allowedWarTypes)
                .allowedAttackTypes(allowedAttackTypes)
                .allowedSuccessTypes(allowedVictoryTypes);

        List<AbstractCursor> attacks = parser.getAttacks();

        this.warCostByDay = new Long2ObjectLinkedOpenHashMap<>();

        attacks.sort(Comparator.comparingLong(o -> o.getDate()));

        Function<AbstractCursor, Boolean> isPrimary = parser.getAttackPrimary();
        Function<AbstractCursor, Boolean> isSecondary = parser.getAttackSecondary();


        long now = System.currentTimeMillis();
        for (AbstractCursor attack : attacks) {
            if (attack.getDate() > now) continue;
            long turn = TimeUtil.getTurn(attack.getDate());
            long day = turn / 12;
            AttackCost cost = warCostByDay.computeIfAbsent(day, f -> new AttackCost(col1Str, col2Str, type == WarCostByDayMode.BUILDING, false, false, false, type.isAttackType));
            cost.addCost(attack, Objects.requireNonNull(isPrimary), Objects.requireNonNull(isSecondary));
        }

        this.min = Collections.min(warCostByDay.keySet());
        this.max = Collections.max(warCostByDay.keySet());
        this.costFunc = type.apply();

        setTitle(type.toString() + " by Day");
        setLabelX("Day");
        setLabelY(type.toString());
        setLabels(col1Str, col2Str);

        writeData();
    }


    @Override
    protected SimpleTable<AttackCost> writeData() {
        AttackCost nullCost = new AttackCost("", "", type == WarCostByDayMode.BUILDING, false, false, true, type.isAttackType);
        for (long day = min; day <= max; day++) {
            long dayOffset = day - min;
            AttackCost cost = warCostByDay.get(day);
            if (cost == null) {
                cost = nullCost;
            }
            add(dayOffset, cost);
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
    public void add(long day, AttackCost cost) {
        this.add(day - min, costFunc.apply(cost, true), costFunc.apply(cost, false));
        if (running_total) processTotal(this);
    }

    public static void processTotal(TimeNumericTable table) {
        DataTable data = table.getData();
        if (data.getRowCount() <= 1) return;
        Row row1 = data.getRow(data.getRowCount() - 2);
        Row row2 = data.getRow(data.getRowCount() - 1);

        Long day = (Long) row2.get(0);
        Double cost1A = (Double) row1.get(1);
        Double cost1B = (Double) row1.get(2);
        Double cost2A = (Double) row2.get(1);
        Double cost2B = (Double) row2.get(2);

        data.removeLast();
        data.add(day, cost1A + cost2A, cost1B + cost2B);
    }
}
