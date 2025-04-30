package link.locutus.discord.commands.manager.v2.table.imp;

import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.Attribute;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.NationAttributeDouble;
import link.locutus.discord.commands.manager.v2.table.TableNumberFormat;
import link.locutus.discord.commands.manager.v2.table.TimeFormat;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.web.commands.binding.value_types.GraphType;

import java.util.*;
import java.util.function.Function;

public class EntityTable<T> extends SimpleTable<Void> {
    private final double[] buffer;
    private final List<Attribute<T, Double>> metricsList;
    private int min, max;
    private final boolean total;
    private final Map<Integer, List<T>> byTier;

         public static EntityTable<DBNation> create(String title, Set<NationAttributeDouble> metrics, Collection<DBAlliance> alliances, NationAttributeDouble groupBy, boolean total, boolean removeVM, int removeActiveM, boolean removeApps) {
            List<DBNation> nations = toNations(Collections.singletonList(new ObjectLinkedOpenHashSet<>(alliances)), removeVM, removeActiveM, removeApps).get(0);
            return new EntityTable<>(title, (Set) metrics, nations, groupBy, total);
        }

        public static <T> EntityTable<T> create(String title, Set<Attribute<T, Double>> metrics, Collection<T> coalition, Attribute<T, Double> groupBy, boolean total) {
            Set<T> nations = new HashSet<>(coalition);
            return new EntityTable<>(title, metrics, nations, groupBy, total);
        }

    public EntityTable(String title, Set<Attribute<T, Double>> metrics, Collection<T> coalition, Attribute<T, Double> groupBy, boolean total) {
        this.metricsList = new ArrayList<>(metrics);
        String[] labels = metrics.stream().map(Attribute::getName).toArray(String[]::new);

        Function<T, Integer> groupByInt = nation -> (int) Math.round(groupBy.apply(nation));
        this.byTier = new HashMap<>();
        this.min = Integer.MAX_VALUE;
        this.max = Integer.MIN_VALUE;
        for (T t : coalition) {
            int tier = groupByInt.apply(t);
            min = Math.min(min, tier);
            max = Math.max(max, tier);
            byTier.computeIfAbsent(tier, f -> new ArrayList<>()).add(t);
        }

        this.total = total;

        this.buffer = new double[metricsList.size()];
        String labelY = labels.length == 1 ? labels[0] : "metric";
        title += (total ? "Total" : "Average") + " " + labelY + " by " + groupBy.getName();
        setTitle(title);
        setLabelX(groupBy.getName());
        setLabelY(labelY);
        setLabels(labels);

        writeData();
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
    public long getOrigin() {
        return min;
    }

    @Override
    public void add(long key, Void ignore) {
        List<T> nations = byTier.get((int) key);
        if (nations == null) {
            Arrays.fill(buffer, 0);
        } else {
            for (int i = 0; i < metricsList.size(); i++) {
                Attribute<T, Double> metric = metricsList.get(i);
                double valueTotal = 0;
                int count = 0;

                for (T nation : nations) {
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
    protected SimpleTable<Void> writeData() {
        for (int key = min; key <= max; key++) {
            add(key, (Void) null);
        }
        return this;
    }
}
