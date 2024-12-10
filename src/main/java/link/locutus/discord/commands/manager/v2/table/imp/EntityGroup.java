package link.locutus.discord.commands.manager.v2.table.imp;

import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.Attribute;
import link.locutus.discord.commands.manager.v2.table.TableNumberFormat;
import link.locutus.discord.commands.manager.v2.table.TimeFormat;
import link.locutus.discord.web.commands.binding.value_types.GraphType;

import java.util.*;
import java.util.function.Function;

public class EntityGroup<T> extends SimpleTable<Void> {
    private final double[] buffer;
    private final List<Map<Integer, List<T>>> byTierList;
    private final int min, max;
    private final boolean total;
    private final Attribute<T, Double> metric;
    private GraphType type = GraphType.LINE;

    public EntityGroup(String titlePrefix, Attribute<T, Double> metric, List<List<T>> coalitions, List<String> coalitionNames, Attribute<T, Double> groupBy, boolean total) {
        this.metric = metric;
        this.total = total;

        String[] labels = coalitionNames.toArray(new String[0]);
        Function<T, Integer> groupByInt = nation -> (int) Math.round(groupBy.apply(nation));
        this.byTierList = new ArrayList<>();
        Set<T> allNations = new ObjectLinkedOpenHashSet<>();

        for (List<T> coalition : coalitions) {
            allNations.addAll(coalition);
            Map<Integer, List<T>> byTierListCoalition = new Int2ObjectLinkedOpenHashMap<>();
            for (T nation : coalition) {
                Integer group = groupByInt.apply(nation);
                byTierListCoalition.computeIfAbsent(group, f -> new ArrayList<>()).add(nation);
            }
            byTierList.add(byTierListCoalition);
        }

        this.min = allNations.stream().map(groupByInt).min(Integer::compare).orElse(0);
        this.max = allNations.stream().map(groupByInt).max(Integer::compare).orElse(0);
        this.buffer = new double[coalitions.size()];

        String labelY = metric.getName();
        titlePrefix = (titlePrefix == null || titlePrefix.isEmpty() ? "" : titlePrefix + " ") +
                (total ? "Total" : "Average") + " " + labelY + " by " + groupBy.getName();

        setTitle(titlePrefix);
        setLabelX(groupBy.getName());
        setLabelY(labelY);
        setLabels(labels);

        writeData();
    }

    public EntityGroup<T> setGraphType(GraphType type) {
        this.type = type;
        return this;
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
        return type;
    }

    @Override
    public void add(long key, Void ignore) {
        for (int i = 0; i < byTierList.size(); i++) {
            double valueTotal = 0;
            int count = 0;

            Map<Integer, List<T>> byTier = byTierList.get(i);
            List<T> nations = byTier.get((int) key);
            if (nations == null) {
                buffer[i] = 0;
                continue;
            }
            for (T nation : nations) {
                count++;
                valueTotal += metric.apply(nation);
            }
            if (count > 1 && !total) {
                valueTotal /= count;
            }
            buffer[i] = valueTotal;
        }
        add(key - min, buffer);
    }

    @Override
    protected SimpleTable<Void> writeData() {
        for (int key = min; key <= max; key++) {
            add(key, (Void) null);
        }
        return this;
    }
}
