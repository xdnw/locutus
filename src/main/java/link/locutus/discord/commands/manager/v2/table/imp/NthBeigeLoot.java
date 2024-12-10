package link.locutus.discord.commands.manager.v2.table.imp;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.table.TableNumberFormat;
import link.locutus.discord.commands.manager.v2.table.TimeFormat;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.PW;
import link.locutus.discord.web.commands.binding.value_types.GraphType;

import java.util.*;
import java.util.function.BiConsumer;

public class NthBeigeLoot extends SimpleTable<PriorityQueue<Double>> {
    private final int n;
    private final Map<Integer, PriorityQueue<Double>> lootByScore;
    private final int min, max;

    public NthBeigeLoot(Set<DBNation> nationsSet, @Default("5") int n) {
        this.n = n;
        nationsSet.removeIf(f -> f.getVm_turns() != 0);
        if (nationsSet.isEmpty()) throw new IllegalArgumentException("No nations provided");

        lootByScore = new Int2ObjectOpenHashMap<>();
        BiConsumer<Double, PriorityQueue<Double>> compareAndAddTop = (value, topValues) -> {
            if (topValues.size() < n) {
                topValues.add(value);
            } else if (value > topValues.peek()) {
                topValues.poll();
                topValues.add(value);
            }
        };

        for (DBNation nation : nationsSet) {
            double score = nation.getScore();
            double loot = nation.getBeigeLootTotal();
            if (loot == 0) continue;
            int min = (int) (score / PW.WAR_RANGE_MAX_MODIFIER);
            int max = (int) (score / PW.WAR_RANGE_MIN_MODIFIER);
            for (int i = min; i <= max; i++) {
                PriorityQueue<Double> queue = lootByScore.computeIfAbsent(i, f -> new PriorityQueue<>(n));
                compareAndAddTop.accept(loot, queue);
            }
        }

        this.min = lootByScore.keySet().stream().min(Integer::compare).orElse(0);
        this.max = lootByScore.keySet().stream().max(Integer::compare).orElse(0);

        String[] labels = {"Nth", "Median", "Mean", "Max"};
        setTitle("Raid income by score");
        setLabelX("score");
        setLabelY("loot");
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
        return TimeFormat.DECIMAL_ROUNDED;
    }

    @Override
    public GraphType getGraphType() {
        return GraphType.LINE;
    }

    @Override
    public void add(long score, PriorityQueue<Double> values) {
        if (values.isEmpty()) return;
        List<Double> sortedValues = new ArrayList<>(values);
        if (sortedValues.size() < n) {
            for (int i = sortedValues.size(); i < n; i++) {
                sortedValues.add(0, 0d);
            }
        }
        double min = sortedValues.get(0);
        double median;
        int size = sortedValues.size();
        if (size % 2 == 0) {
            median = (sortedValues.get(size / 2 - 1) + sortedValues.get(size / 2)) / 2.0;
        } else {
            median = sortedValues.get(size / 2);
        }
        double sum = 0;
        for (double value : sortedValues) {
            sum += value;
        }
        double mean = sum / size;
        double max = sortedValues.get(size - 1);
        add(score - this.min, min, median, mean, max);
    }

    @Override
    protected NthBeigeLoot writeData() {
        for (int score = min; score <= max; score++) {
            add(score, lootByScore.getOrDefault(score, new PriorityQueue<>()));
        }
        return this;
    }
}
