package link.locutus.discord.sim.planners;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public record ScoreSummary(
        double mean,
        double p10,
        double p50,
        double p90,
        int sampleCount
) {
    public ScoreSummary {
        if (sampleCount <= 0) {
            throw new IllegalArgumentException("sampleCount must be > 0");
        }
    }

    public static ScoreSummary identical(double value) {
        return new ScoreSummary(value, value, value, value, 1);
    }

    public static ScoreSummary fromSamples(List<Double> samples) {
        if (samples == null || samples.isEmpty()) {
            throw new IllegalArgumentException("samples must not be empty");
        }
        ArrayList<Double> sorted = new ArrayList<>(samples);
        sorted.sort(Comparator.naturalOrder());
        double sum = 0.0;
        for (double sample : sorted) {
            sum += sample;
        }
        return new ScoreSummary(
                sum / sorted.size(),
                percentile(sorted, 0.10),
                percentile(sorted, 0.50),
                percentile(sorted, 0.90),
                sorted.size()
        );
    }

    private static double percentile(List<Double> sorted, double quantile) {
        if (sorted.size() == 1) {
            return sorted.get(0);
        }
        double index = quantile * (sorted.size() - 1);
        int lower = (int) Math.floor(index);
        int upper = (int) Math.ceil(index);
        if (lower == upper) {
            return sorted.get(lower);
        }
        double fraction = index - lower;
        return sorted.get(lower) + (sorted.get(upper) - sorted.get(lower)) * fraction;
    }
}