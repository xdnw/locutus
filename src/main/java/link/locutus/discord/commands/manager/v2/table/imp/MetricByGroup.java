package link.locutus.discord.commands.manager.v2.table.imp;

import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Switch;
import link.locutus.discord.commands.manager.v2.binding.bindings.TypedFunction;
import link.locutus.discord.commands.manager.v2.table.TableNumberFormat;
import link.locutus.discord.commands.manager.v2.table.TimeFormat;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.pnw.NationList;
import link.locutus.discord.pnw.SimpleNationList;
import link.locutus.discord.web.commands.binding.value_types.GraphType;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class MetricByGroup extends SimpleTable<NationList> {
    private final double[] buffer;
    private final TypedFunction<DBNation, Double>[] metricsArr;
    private final int min, max;
    private final boolean total;
    private final Map<Integer, NationList> byTier;

    public MetricByGroup(Set<TypedFunction<DBNation, Double>> metrics,
                         Set<DBNation> coalitionNations,
                         @Default("getCities") TypedFunction<DBNation, Double> groupBy,
                         @Switch("i") boolean includeInactives,
                         @Switch("a") boolean includeApplicants,
                         @Switch("t") boolean total
    ) {
        coalitionNations.removeIf(f -> f.getVm_turns() != 0 || (!includeApplicants && f.getPosition() <= 1) || (!includeInactives && f.active_m() > 4880));
        this.metricsArr = metrics.toArray(new TypedFunction[0]);
        String[] labels = metrics.stream().map(TypedFunction::getName).toArray(String[]::new);

        NationList coalitionList = new SimpleNationList(coalitionNations);

        Function<DBNation, Integer> groupByInt = nation -> (int) Math.round(groupBy.apply(nation));
        this.byTier = coalitionList.groupBy(groupByInt);
        this.min = coalitionList.stream(groupByInt).min(Integer::compare).get();
        this.max = coalitionList.stream(groupByInt).max(Integer::compare).get();
        this.total = total;

        this.buffer = new double[metricsArr.length];
        String labelY = labels.length == 1 ? labels[0] : "metric";
        setTitle((total ? "Total" : "Average") + " " + labelY + " by " + groupBy.getName());
        setLabelX(groupBy.getName());
        setLabelY(labelY);
        setLabels(labels);

        writeData();
    }

    @Override
    public long getOrigin() {
        return min;
    }

    @Override
    public TableNumberFormat getNumberFormat() {
        return TableNumberFormat.SI_UNIT;
    }

    @Override
    public TimeFormat getTimeFormat() {
        return TimeFormat.SI_UNIT;
    }

    @Override
    public GraphType getGraphType() {
        return GraphType.LINE;
    }

    @Override
    public void add(long key, NationList nations) {
        if (nations == null) {
            Arrays.fill(buffer, 0);
        } else {
            for (int i = 0; i < metricsArr.length; i++) {
                TypedFunction<DBNation, Double> metric = metricsArr[i];
                double valueTotal = 0;
                int count = 0;

                for (DBNation nation : nations.getNations()) {
                    if (nation.hasUnsetMil()) continue;
                    count++;
                    valueTotal += metric.apply(nation);
                }
                if (count > 1 && !total) {
                    valueTotal /= count;
                }

                buffer[i] = valueTotal;
            }
        }
        add(key - min, buffer);
    }

    @Override
    protected MetricByGroup writeData() {
        for (int key = min; key <= max; key++) {
            NationList nations = byTier.get(key);
            add(key, nations);
        }
        return this;
    }
}
