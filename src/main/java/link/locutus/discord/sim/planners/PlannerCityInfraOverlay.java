package link.locutus.discord.sim.planners;

import java.util.Arrays;
import java.util.Map;

/**
 * Sparse per-city infra overlay for one nation.
 *
 * <p>Values are absolute per-city infra values (not deltas) keyed by city index.</p>
 */
final class PlannerCityInfraOverlay {
    private final int nationId;
    private final int[] cityIndexes;
    private final double[] cityInfraValues;

    PlannerCityInfraOverlay(int nationId, Map<Integer, Double> cityInfraByIndex) {
        DenseOverlay dense = DenseOverlay.from(cityInfraByIndex);
        this.nationId = nationId;
        this.cityIndexes = dense.cityIndexes();
        this.cityInfraValues = dense.cityInfraValues();
        validate();
    }

    PlannerCityInfraOverlay(int nationId, int[] cityIndexes, double[] cityInfraValues) {
        this.nationId = nationId;
        this.cityIndexes = Arrays.copyOf(cityIndexes, cityIndexes.length);
        this.cityInfraValues = Arrays.copyOf(cityInfraValues, cityInfraValues.length);
        validate();
    }

    private void validate() {
        if (nationId <= 0) {
            throw new IllegalArgumentException("nationId must be > 0");
        }
        if (cityIndexes.length != cityInfraValues.length) {
            throw new IllegalArgumentException("cityIndexes and cityInfraValues must have matching lengths");
        }
        for (int i = 0; i < cityIndexes.length; i++) {
            int cityIndex = cityIndexes[i];
            double cityInfra = cityInfraValues[i];
            if (cityIndex < 0) {
                throw new IllegalArgumentException("city index must be >= 0");
            }
            if (cityInfra < 0d) {
                throw new IllegalArgumentException("city infra must be >= 0");
            }
            if (i > 0 && cityIndexes[i - 1] >= cityIndex) {
                throw new IllegalArgumentException("city indexes must be strictly increasing");
            }
        }
    }

    int nationId() {
        return nationId;
    }

    int size() {
        return cityIndexes.length;
    }

    int cityIndexAt(int position) {
        return cityIndexes[position];
    }

    double cityInfraValueAt(int position) {
        return cityInfraValues[position];
    }

    boolean isEmpty() {
        return cityIndexes.length == 0;
    }

    DBNationSnapshot applyTo(DBNationSnapshot baseSnapshot) {
        if (isEmpty()) {
            return baseSnapshot;
        }
        if (baseSnapshot.nationId() != nationId) {
            throw new IllegalArgumentException("Overlay nationId does not match snapshot nationId");
        }
        double[] baseCityInfra = baseSnapshot.cityInfraRaw();
        double[] cityInfra = Arrays.copyOf(baseCityInfra, baseCityInfra.length);
        for (int i = 0; i < cityIndexes.length; i++) {
            int cityIndex = cityIndexes[i];
            if (cityIndex >= cityInfra.length) {
                continue;
            }
            cityInfra[cityIndex] = cityInfraValues[i];
        }
        return baseSnapshot.toBuilder().cityInfra(cityInfra).build();
    }

    PlannerCityInfraOverlay merge(PlannerCityInfraOverlay newer) {
        if (newer.nationId != nationId) {
            throw new IllegalArgumentException("Cannot merge overlays from different nations");
        }
        if (isEmpty()) {
            return newer;
        }
        if (newer.isEmpty()) {
            return this;
        }

        int[] mergedIndexes = new int[cityIndexes.length + newer.cityIndexes.length];
        double[] mergedValues = new double[mergedIndexes.length];
        int left = 0;
        int right = 0;
        int size = 0;
        while (left < cityIndexes.length || right < newer.cityIndexes.length) {
            if (right >= newer.cityIndexes.length) {
                mergedIndexes[size] = cityIndexes[left];
                mergedValues[size] = cityInfraValues[left];
                left++;
                size++;
                continue;
            }
            if (left >= cityIndexes.length) {
                mergedIndexes[size] = newer.cityIndexes[right];
                mergedValues[size] = newer.cityInfraValues[right];
                right++;
                size++;
                continue;
            }
            int leftIndex = cityIndexes[left];
            int rightIndex = newer.cityIndexes[right];
            if (leftIndex == rightIndex) {
                mergedIndexes[size] = rightIndex;
                mergedValues[size] = newer.cityInfraValues[right];
                left++;
                right++;
            } else if (leftIndex < rightIndex) {
                mergedIndexes[size] = leftIndex;
                mergedValues[size] = cityInfraValues[left];
                left++;
            } else {
                mergedIndexes[size] = rightIndex;
                mergedValues[size] = newer.cityInfraValues[right];
                right++;
            }
            size++;
        }
        return new PlannerCityInfraOverlay(
                nationId,
                Arrays.copyOf(mergedIndexes, size),
                Arrays.copyOf(mergedValues, size)
        );
    }

    PlannerCityInfraOverlay compactAgainst(double[] baseCityInfra) {
        if (isEmpty()) {
            return this;
        }
        int keep = 0;
        for (int i = 0; i < cityIndexes.length; i++) {
            int cityIndex = cityIndexes[i];
            if (cityIndex < baseCityInfra.length && baseCityInfra[cityIndex] == cityInfraValues[i]) {
                continue;
            }
            keep++;
        }
        if (keep == cityIndexes.length) {
            return this;
        }
        int[] compactIndexes = new int[keep];
        double[] compactValues = new double[keep];
        int next = 0;
        for (int i = 0; i < cityIndexes.length; i++) {
            int cityIndex = cityIndexes[i];
            if (cityIndex < baseCityInfra.length && baseCityInfra[cityIndex] == cityInfraValues[i]) {
                continue;
            }
            compactIndexes[next] = cityIndex;
            compactValues[next] = cityInfraValues[i];
            next++;
        }
        return new PlannerCityInfraOverlay(nationId, compactIndexes, compactValues);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PlannerCityInfraOverlay that)) {
            return false;
        }
        return nationId == that.nationId
                && Arrays.equals(cityIndexes, that.cityIndexes)
                && Arrays.equals(cityInfraValues, that.cityInfraValues);
    }

    @Override
    public int hashCode() {
        int result = Integer.hashCode(nationId);
        result = 31 * result + Arrays.hashCode(cityIndexes);
        result = 31 * result + Arrays.hashCode(cityInfraValues);
        return result;
    }

    @Override
    public String toString() {
        return "PlannerCityInfraOverlay{"
                + "nationId=" + nationId
                + ", cityIndexes=" + Arrays.toString(cityIndexes)
                + ", cityInfraValues=" + Arrays.toString(cityInfraValues)
                + '}';
    }

    private record DenseOverlay(int[] cityIndexes, double[] cityInfraValues) {
        private static DenseOverlay from(Map<Integer, Double> cityInfraByIndex) {
            int[] indexes = cityInfraByIndex.keySet().stream().mapToInt(Integer::intValue).sorted().toArray();
            double[] values = new double[indexes.length];
            for (int i = 0; i < indexes.length; i++) {
                Double cityInfra = cityInfraByIndex.get(indexes[i]);
                if (cityInfra == null) {
                    throw new IllegalArgumentException("city infra must not be null");
                }
                values[i] = cityInfra;
            }
            return new DenseOverlay(indexes, values);
        }
    }
}
