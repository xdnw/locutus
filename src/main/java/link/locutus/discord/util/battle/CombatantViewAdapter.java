package link.locutus.discord.util.battle;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.sim.combat.state.BasicCombatCityView;
import link.locutus.discord.sim.combat.state.BasicCombatantView;
import link.locutus.discord.sim.combat.state.CombatantView;

import java.util.Map;
import java.util.Objects;

public final class CombatantViewAdapter {
    private CombatantViewAdapter() {
    }

    public static CombatantView of(DBNation nation) {
        return of(nation, null);
    }

    public static CombatantView of(DBNation nation, Double maxCityInfraOverride) {
        Objects.requireNonNull(nation, "nation");

        BasicCombatantView.Builder builder = BasicCombatantView.builder()
                .nationId(nation.getNation_id())
                .cities(nation.getCities())
                .researchBits(nation.getResearchBits(null))
            .blitzkrieg(nation.isBlitzkrieg())
                .lootModifier(nation.lootModifier())
                .looterModifier(true, nation.looterModifier(true))
                .looterModifier(false, nation.looterModifier(false));

        for (AttackType type : AttackType.values) {
            builder.infraAttackModifier(type, nation.infraAttackModifier(type));
            builder.infraDefendModifier(type, nation.infraDefendModifier(type));
        }

        builder.addProjects(nation.getProjects());

        for (MilitaryUnit unit : MilitaryUnit.values) {
            builder.unit(unit, safeUnits(nation, unit));
            builder.capacity(unit, safeCapacity(nation, unit));
        }

        addCityViews(builder, nation, maxCityInfraOverride);
        return builder.build();
    }

    private static void addCityViews(BasicCombatantView.Builder builder, DBNation nation, Double maxCityInfraOverride) {
        int declaredCities = Math.max(1, nation.getCities());
        var cityMap = nation.getCityMap(false);
        if (cityMap != null && !cityMap.isEmpty()) {
            if (maxCityInfraOverride == null) {
                for (var city : cityMap.values()) {
                    builder.city(new BasicCombatCityView(
                            city.getInfra(),
                            city.getMissileDamage(nation::hasProject),
                            city.getNukeDamage(nation::hasProject)
                    ));
                }
            } else {
                double infra = Math.max(0d, maxCityInfraOverride);
                var first = cityMap.values().iterator().next();
                Map.Entry<Integer, Integer> missile = first.getMissileDamage(nation::hasProject);
                Map.Entry<Integer, Integer> nuke = first.getNukeDamage(nation::hasProject);
                int cityCount = Math.max(declaredCities, cityMap.size());
                for (int i = 0; i < cityCount; i++) {
                    builder.city(new BasicCombatCityView(infra, missile, nuke));
                }
            }
            return;
        }

        double infra = maxCityInfraOverride != null
                ? Math.max(0d, maxCityInfraOverride)
                : Math.max(0d, nation.maxCityInfra());

        for (int i = 0; i < declaredCities; i++) {
            builder.city(new BasicCombatCityView(infra, Map.entry(0, 0), Map.entry(0, 0)));
        }
    }

    private static int safeUnits(DBNation nation, MilitaryUnit unit) {
        try {
            return Math.max(0, nation.getUnits(unit));
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private static int safeCapacity(DBNation nation, MilitaryUnit unit) {
        try {
            return Math.max(0, unit.getCap(nation, false));
        } catch (RuntimeException ignored) {
            return unit == MilitaryUnit.MONEY || unit == MilitaryUnit.INFRASTRUCTURE ? Integer.MAX_VALUE : 0;
        }
    }
}
