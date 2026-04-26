package link.locutus.discord.sim.combat.state;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.city.project.Project;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class BasicCombatantView implements CombatantView {
    private final int nationId;
    private final int cities;
    private final int researchBits;
    private final int[] units;
    private final int[] capacities;
    private final double[] infraAttackModifiers;
    private final double[] infraDefendModifiers;
    private final double groundLooterModifier;
    private final double nonGroundLooterModifier;
    private final double lootModifier;
    private final boolean blitzkrieg;
    private final Set<Project> projects;
    private final List<CombatCityView> cityViews;

    private BasicCombatantView(Builder builder) {
        this.nationId = builder.nationId;
        this.cities = Math.max(0, builder.cities);
        this.researchBits = builder.researchBits;
        this.units = builder.units.clone();
        this.capacities = builder.capacities.clone();
        this.infraAttackModifiers = builder.infraAttackModifiers.clone();
        this.infraDefendModifiers = builder.infraDefendModifiers.clone();
        this.groundLooterModifier = builder.groundLooterModifier;
        this.nonGroundLooterModifier = builder.nonGroundLooterModifier;
        this.lootModifier = builder.lootModifier;
        this.blitzkrieg = builder.blitzkrieg;
        this.projects = builder.projects.isEmpty()
                ? Collections.emptySet()
            : Collections.unmodifiableSet(new LinkedHashSet<>(builder.projects));
        this.cityViews = builder.cityViews.isEmpty()
                ? Collections.emptyList()
                : List.copyOf(builder.cityViews);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public int nationId() {
        return nationId;
    }

    @Override
    public int cities() {
        return cities;
    }

    @Override
    public int researchBits() {
        return researchBits;
    }

    @Override
    public int getUnitCapacity(MilitaryUnit unit) {
        return capacities[unit.ordinal()];
    }

    @Override
    public Collection<? extends CombatCityView> getCityViews() {
        return cityViews;
    }

    @Override
    public double infraAttackModifier(AttackType type) {
        return infraAttackModifiers[type.ordinal()];
    }

    @Override
    public double infraDefendModifier(AttackType type) {
        return infraDefendModifiers[type.ordinal()];
    }

    @Override
    public double looterModifier(boolean ground) {
        return ground ? groundLooterModifier : nonGroundLooterModifier;
    }

    @Override
    public double lootModifier() {
        return lootModifier;
    }

    @Override
    public boolean isBlitzkrieg() {
        return blitzkrieg;
    }

    @Override
    public boolean hasProject(Project project) {
        return projects.contains(project);
    }

    @Override
    public int getUnits(MilitaryUnit unit) {
        return units[unit.ordinal()];
    }

    public static final class Builder {
        private int nationId;
        private int cities;
        private int researchBits;
        private final int[] units = new int[MilitaryUnit.values.length];
        private final int[] capacities = new int[MilitaryUnit.values.length];
        private final double[] infraAttackModifiers = new double[AttackType.values.length];
        private final double[] infraDefendModifiers = new double[AttackType.values.length];
        private double groundLooterModifier = 1d;
        private double nonGroundLooterModifier = 1d;
        private double lootModifier = 1d;
        private boolean blitzkrieg;
        private final Set<Project> projects = new LinkedHashSet<>();
        private final List<CombatCityView> cityViews = new ArrayList<>();

        private Builder() {
            Arrays.fill(infraAttackModifiers, 1d);
            Arrays.fill(infraDefendModifiers, 1d);
            Arrays.fill(capacities, 0);
            capacities[MilitaryUnit.MONEY.ordinal()] = Integer.MAX_VALUE;
            capacities[MilitaryUnit.INFRASTRUCTURE.ordinal()] = Integer.MAX_VALUE;
        }

        public Builder nationId(int nationId) {
            this.nationId = nationId;
            return this;
        }

        public Builder cities(int cities) {
            this.cities = cities;
            return this;
        }

        public Builder researchBits(int researchBits) {
            this.researchBits = researchBits;
            return this;
        }

        public Builder unit(MilitaryUnit unit, int amount) {
            units[unit.ordinal()] = Math.max(0, amount);
            return this;
        }

        public Builder capacity(MilitaryUnit unit, int capacity) {
            capacities[unit.ordinal()] = Math.max(0, capacity);
            return this;
        }

        public Builder infraAttackModifier(AttackType type, double value) {
            infraAttackModifiers[type.ordinal()] = value <= 0d ? 1d : value;
            return this;
        }

        public Builder infraDefendModifier(AttackType type, double value) {
            infraDefendModifiers[type.ordinal()] = value <= 0d ? 1d : value;
            return this;
        }

        public Builder infraAttackModifierAll(double value) {
            Arrays.fill(infraAttackModifiers, value <= 0d ? 1d : value);
            return this;
        }

        public Builder infraDefendModifierAll(double value) {
            Arrays.fill(infraDefendModifiers, value <= 0d ? 1d : value);
            return this;
        }

        public Builder looterModifier(boolean ground, double value) {
            if (ground) {
                this.groundLooterModifier = value <= 0d ? 1d : value;
            } else {
                this.nonGroundLooterModifier = value <= 0d ? 1d : value;
            }
            return this;
        }

        public Builder lootModifier(double value) {
            this.lootModifier = value <= 0d ? 1d : value;
            return this;
        }

        public Builder blitzkrieg(boolean blitzkrieg) {
            this.blitzkrieg = blitzkrieg;
            return this;
        }

        public Builder addProject(Project project) {
            if (project != null) {
                projects.add(project);
            }
            return this;
        }

        public Builder addProjects(Collection<Project> projects) {
            if (projects != null) {
                for (Project project : projects) {
                    addProject(project);
                }
            }
            return this;
        }

        public Builder city(CombatCityView city) {
            Objects.requireNonNull(city, "city");
            cityViews.add(city);
            return this;
        }

        public Builder citiesWithUniformInfra(double infra, int count) {
            for (int i = 0; i < Math.max(0, count); i++) {
                city(BasicCombatCityView.ofInfra(infra));
            }
            return this;
        }

        public BasicCombatantView build() {
            return new BasicCombatantView(this);
        }
    }
}
