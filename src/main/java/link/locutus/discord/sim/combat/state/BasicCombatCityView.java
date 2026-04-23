package link.locutus.discord.sim.combat.state;

import link.locutus.discord.apiv1.enums.city.project.Project;

import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

public record BasicCombatCityView(
        double infra,
        Map.Entry<Integer, Integer> missileDamage,
        Map.Entry<Integer, Integer> nukeDamage
) implements CombatCityView {

    public BasicCombatCityView {
        infra = Math.max(0d, infra);
        missileDamage = immutableRange(missileDamage);
        nukeDamage = immutableRange(nukeDamage);
    }

    public static BasicCombatCityView ofInfra(double infra) {
        return new BasicCombatCityView(infra, Map.entry(0, 0), Map.entry(0, 0));
    }

    public static BasicCombatCityView of(double infra, int missileMin, int missileMax, int nukeMin, int nukeMax) {
        return new BasicCombatCityView(
                infra,
                Map.entry(missileMin, missileMax),
                Map.entry(nukeMin, nukeMax)
        );
    }

    @Override
    public double getInfra() {
        return infra;
    }

    @Override
    public Map.Entry<Integer, Integer> getMissileDamage(Predicate<Project> hasProject) {
        return missileDamage;
    }

    @Override
    public Map.Entry<Integer, Integer> getNukeDamage(Predicate<Project> hasProject) {
        return nukeDamage;
    }

    private static Map.Entry<Integer, Integer> immutableRange(Map.Entry<Integer, Integer> range) {
        Objects.requireNonNull(range, "range");
        int min = Math.max(0, range.getKey());
        int max = Math.max(min, range.getValue());
        return Map.entry(min, max);
    }
}
