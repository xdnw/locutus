package link.locutus.discord.sim.combat.state;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.sim.combat.NationCombatProfile;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Shared boundary owner for immutable CombatantView snapshots.
 */
public final class CombatantSnapshots {
    private static final double DEFAULT_SYNTHETIC_CITY_INFRA = 2_000d;

    private CombatantSnapshots() {
    }

    public static CombatantView snapshotOf(CombatantView source, NationCombatProfile combatProfile, Collection<Project> projects) {
        Objects.requireNonNull(source, "source");
        NationCombatProfile validatedProfile = Objects.requireNonNull(combatProfile, "combatProfile");
        Set<Project> projectSet = snapshotProjects(projects);
        Predicate<Project> hasProject = projectSet::contains;

        BasicCombatantView.Builder builder = baseBuilder(
                source.nationId(),
                source.cities(),
                validatedProfile,
                projectSet
        );
        for (MilitaryUnit unit : MilitaryUnit.values) {
            builder.unit(unit, source.getUnits(unit));
            builder.capacity(unit, source.getUnitCapacity(unit));
        }
        copyCityViews(builder, source.getCityViews(), hasProject);
        return builder.build();
    }

    public static CombatantView snapshotOf(DBNation nation) {
        return snapshotOf(nation, null);
    }

    public static CombatantView snapshotOf(DBNation nation, Double maxCityInfraOverride) {
        Objects.requireNonNull(nation, "nation");

        NationCombatProfile combatProfile = dbCombatProfile(nation);
        Set<Project> projectSet = snapshotProjects(nation.getProjects());
        Predicate<Project> hasProject = projectSet::contains;
        BasicCombatantView.Builder builder = baseBuilder(
                nation.getNation_id(),
                nation.getCities(),
                combatProfile,
                projectSet
        );

        for (MilitaryUnit unit : MilitaryUnit.values) {
            builder.unit(unit, safeUnits(nation, unit));
            builder.capacity(unit, safeCapacity(nation, unit));
        }
        addDbCityViews(builder, nation, maxCityInfraOverride, hasProject);
        return builder.build();
    }

    public static CombatantView syntheticSingleCity(
            int nationId,
            int soldiers,
            int tanks,
            int aircraft,
            int ships
    ) {
        return BasicCombatantView.builder()
                .nationId(nationId)
                .cities(1)
                .unit(MilitaryUnit.SOLDIER, soldiers)
                .unit(MilitaryUnit.TANK, tanks)
                .unit(MilitaryUnit.AIRCRAFT, aircraft)
                .unit(MilitaryUnit.SHIP, ships)
                .capacity(MilitaryUnit.SOLDIER, soldiers)
                .capacity(MilitaryUnit.TANK, tanks)
                .capacity(MilitaryUnit.AIRCRAFT, aircraft)
                .capacity(MilitaryUnit.SHIP, ships)
                .city(BasicCombatCityView.ofInfra(DEFAULT_SYNTHETIC_CITY_INFRA))
                .build();
    }

    private static BasicCombatantView.Builder baseBuilder(
            int nationId,
            int cities,
            NationCombatProfile combatProfile,
            Collection<Project> projects
    ) {
        BasicCombatantView.Builder builder = BasicCombatantView.builder()
                .nationId(nationId)
                .cities(cities)
                .researchBits(combatProfile.researchBits())
                .blitzkrieg(combatProfile.blitzkriegActive())
                .lootModifier(combatProfile.lootModifier())
                .looterModifier(true, combatProfile.groundLooterModifier())
                .looterModifier(false, combatProfile.nonGroundLooterModifier())
                .addProjects(projects);
        for (AttackType type : AttackType.values) {
            builder.infraAttackModifier(type, combatProfile.infraAttackModifier(type));
            builder.infraDefendModifier(type, combatProfile.infraDefendModifier(type));
        }
        return builder;
    }

    private static void copyCityViews(
            BasicCombatantView.Builder builder,
            Collection<? extends CombatCityView> cityViews,
            Predicate<Project> hasProject
    ) {
        for (CombatCityView city : cityViews) {
            Map.Entry<Integer, Integer> missileDamage = city.getMissileDamage(hasProject);
            Map.Entry<Integer, Integer> nukeDamage = city.getNukeDamage(hasProject);
            builder.city(BasicCombatCityView.of(
                    city.getInfra(),
                    missileDamage.getKey(),
                    missileDamage.getValue(),
                    nukeDamage.getKey(),
                    nukeDamage.getValue()
            ));
        }
    }

    private static void addDbCityViews(
            BasicCombatantView.Builder builder,
            DBNation nation,
            Double maxCityInfraOverride,
            Predicate<Project> hasProject
    ) {
        int declaredCities = Math.max(1, nation.getCities());
        var cityMap = nation.getCityMap(false);
        if (cityMap != null && !cityMap.isEmpty()) {
            if (maxCityInfraOverride == null) {
                int addedCities = 0;
                for (var city : cityMap.values()) {
                    Map.Entry<Integer, Integer> missileDamage = city.getMissileDamage(hasProject);
                    Map.Entry<Integer, Integer> nukeDamage = city.getNukeDamage(hasProject);
                    builder.city(BasicCombatCityView.of(
                            city.getInfra(),
                            missileDamage.getKey(),
                            missileDamage.getValue(),
                            nukeDamage.getKey(),
                            nukeDamage.getValue()
                    ));
                    addedCities++;
                }
                if (addedCities < declaredCities) {
                    addFallbackCityViews(builder, nation, declaredCities - addedCities, null);
                }
            } else {
                double infra = Math.max(0d, maxCityInfraOverride);
                var first = cityMap.values().iterator().next();
                Map.Entry<Integer, Integer> missileDamage = first.getMissileDamage(hasProject);
                Map.Entry<Integer, Integer> nukeDamage = first.getNukeDamage(hasProject);
                int cityCount = Math.max(declaredCities, cityMap.size());
                for (int i = 0; i < cityCount; i++) {
                    builder.city(BasicCombatCityView.of(
                            infra,
                            missileDamage.getKey(),
                            missileDamage.getValue(),
                            nukeDamage.getKey(),
                            nukeDamage.getValue()
                    ));
                }
            }
            return;
        }

        addFallbackCityViews(builder, nation, declaredCities, maxCityInfraOverride);
    }

    private static void addFallbackCityViews(
            BasicCombatantView.Builder builder,
            DBNation nation,
            int cityCount,
            Double maxCityInfraOverride
    ) {
        double infra = maxCityInfraOverride != null
                ? Math.max(0d, maxCityInfraOverride)
                : Math.max(0d, nation.maxCityInfra());
        for (int i = 0; i < cityCount; i++) {
            builder.city(BasicCombatCityView.ofInfra(infra));
        }
    }

    private static NationCombatProfile dbCombatProfile(DBNation nation) {
        double[] attackModifiers = new double[AttackType.values.length];
        double[] defendModifiers = new double[AttackType.values.length];
        for (AttackType type : AttackType.values) {
            attackModifiers[type.ordinal()] = nation.infraAttackModifier(type);
            defendModifiers[type.ordinal()] = nation.infraDefendModifier(type);
        }
        return new NationCombatProfile(
                nation.getResearchBits(null),
                nation.isBlitzkrieg(),
                attackModifiers,
                defendModifiers,
                nation.looterModifier(true),
                nation.looterModifier(false),
                nation.lootModifier()
        );
    }

    private static Set<Project> snapshotProjects(Collection<Project> projects) {
        if (projects == null || projects.isEmpty()) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(projects));
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