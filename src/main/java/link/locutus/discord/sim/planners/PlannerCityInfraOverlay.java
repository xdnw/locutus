package link.locutus.discord.sim.planners;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sparse per-city infra overlay for one nation.
 *
 * <p>Values are absolute per-city infra values (not deltas) keyed by city index.</p>
 */
record PlannerCityInfraOverlay(
        int nationId,
        Map<Integer, Double> cityInfraByIndex
) {
    PlannerCityInfraOverlay {
        if (nationId <= 0) {
            throw new IllegalArgumentException("nationId must be > 0");
        }
        LinkedHashMap<Integer, Double> copy = new LinkedHashMap<>();
        for (Map.Entry<Integer, Double> entry : cityInfraByIndex.entrySet()) {
            Integer cityIndex = entry.getKey();
            Double cityInfra = entry.getValue();
            if (cityIndex == null || cityIndex < 0) {
                throw new IllegalArgumentException("city index must be >= 0");
            }
            if (cityInfra == null || cityInfra < 0d) {
                throw new IllegalArgumentException("city infra must be >= 0");
            }
            copy.put(cityIndex, cityInfra);
        }
        cityInfraByIndex = Map.copyOf(copy);
    }

    boolean isEmpty() {
        return cityInfraByIndex.isEmpty();
    }

    DBNationSnapshot applyTo(DBNationSnapshot baseSnapshot) {
        if (isEmpty()) {
            return baseSnapshot;
        }
        if (baseSnapshot.nationId() != nationId) {
            throw new IllegalArgumentException("Overlay nationId does not match snapshot nationId");
        }
        double[] cityInfra = baseSnapshot.cityInfra();
        for (Map.Entry<Integer, Double> entry : cityInfraByIndex.entrySet()) {
            int cityIndex = entry.getKey();
            if (cityIndex >= cityInfra.length) {
                continue;
            }
            cityInfra[cityIndex] = entry.getValue();
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
        LinkedHashMap<Integer, Double> merged = new LinkedHashMap<>(cityInfraByIndex);
        merged.putAll(newer.cityInfraByIndex);
        return new PlannerCityInfraOverlay(nationId, merged);
    }

    PlannerCityInfraOverlay compactAgainst(double[] baseCityInfra) {
        if (isEmpty()) {
            return this;
        }
        LinkedHashMap<Integer, Double> compact = new LinkedHashMap<>();
        for (Map.Entry<Integer, Double> entry : cityInfraByIndex.entrySet()) {
            int cityIndex = entry.getKey();
            if (cityIndex < baseCityInfra.length && baseCityInfra[cityIndex] == entry.getValue()) {
                continue;
            }
            compact.put(cityIndex, entry.getValue());
        }
        return new PlannerCityInfraOverlay(nationId, compact);
    }
}
