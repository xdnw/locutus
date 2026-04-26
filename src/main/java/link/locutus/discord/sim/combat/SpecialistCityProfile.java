package link.locutus.discord.sim.combat;

import link.locutus.discord.apiv1.enums.city.ICity;
import link.locutus.discord.apiv1.enums.city.building.Building;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.util.PW;

import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Compact immutable city shape used to keep specialist city-damage and capacity math on the shared owner path.
 *
 * <p>The sim/planner state keeps infra as the only mutable city value. The remaining fields capture
 * the static inputs needed to recompute population-sensitive missile/nuke damage and exact
 * military-capacity math as infra changes, without carrying a full mutable city build into the
 * planner/runtime substrate.</p>
 */
public record SpecialistCityProfile(
        double land,
        int ageDays,
        int commerce,
        int pollution,
        double hospitalModifier,
        double policeModifier,
        int barracks,
        int factories,
        int hangars,
        int drydocks
) {
    public static final double DEFAULT_LAND = 250d;
    public static final SpecialistCityProfile DEFAULT = new SpecialistCityProfile(DEFAULT_LAND, 1, 0, 0, 0d, 0d, 0, 0, 0, 0);

    public SpecialistCityProfile(
            double land,
            int ageDays,
            int commerce,
            int pollution,
            double hospitalModifier,
            double policeModifier
    ) {
        this(land, ageDays, commerce, pollution, hospitalModifier, policeModifier, 0, 0, 0, 0);
    }

    public SpecialistCityProfile {
        land = Double.isFinite(land) && land > 0d ? land : DEFAULT_LAND;
        ageDays = Math.max(1, ageDays);
        commerce = Math.max(0, commerce);
        pollution = Math.max(0, pollution);
        hospitalModifier = Math.max(0d, hospitalModifier);
        policeModifier = Math.max(0d, policeModifier);
        barracks = Math.max(0, barracks);
        factories = Math.max(0, factories);
        hangars = Math.max(0, hangars);
        drydocks = Math.max(0, drydocks);
    }

    public static SpecialistCityProfile[] defaults(int cityCount) {
        SpecialistCityProfile[] profiles = new SpecialistCityProfile[Math.max(0, cityCount)];
        java.util.Arrays.fill(profiles, DEFAULT);
        return profiles;
    }

    public static SpecialistCityProfile fromCity(ICity city, Predicate<Project> hasProject) {
        Objects.requireNonNull(city, "city");
        Objects.requireNonNull(hasProject, "hasProject");
        return new SpecialistCityProfile(
                city.getLand(),
                city.getAgeDays(),
                city.calcCommerce(hasProject),
                city.calcPollution(hasProject),
                buildingModifier(city, Buildings.HOSPITAL, hasProject.test(Projects.CLINICAL_RESEARCH_CENTER) ? 3.5d : 2.5d),
                buildingModifier(city, Buildings.POLICE_STATION, hasProject.test(Projects.SPECIALIZED_POLICE_TRAINING_PROGRAM) ? 3.5d : 2.5d),
                city.getBuilding(Buildings.BARRACKS),
                city.getBuilding(Buildings.FACTORY),
                city.getBuilding(Buildings.HANGAR),
                city.getBuilding(Buildings.DRYDOCK)
        );
    }

    public ICity cityView(double infra) {
        return view(infra);
    }

    public Map.Entry<Integer, Integer> missileDamage(double infra, Predicate<Project> hasProject) {
        return projectileDamage(infra, hasProject, true);
    }

    public Map.Entry<Integer, Integer> nukeDamage(double infra, Predicate<Project> hasProject) {
        return projectileDamage(infra, hasProject, false);
    }

    public int missileDamageMin(double infra, Predicate<Project> hasProject) {
        return projectileDamageBound(infra, hasProject, true, true);
    }

    public int missileDamageMax(double infra, Predicate<Project> hasProject) {
        return projectileDamageBound(infra, hasProject, true, false);
    }

    public int nukeDamageMin(double infra, Predicate<Project> hasProject) {
        return projectileDamageBound(infra, hasProject, false, true);
    }

    public int nukeDamageMax(double infra, Predicate<Project> hasProject) {
        return projectileDamageBound(infra, hasProject, false, false);
    }

    public int population(double infra) {
        long infraCents = Math.round(Math.max(0d, infra) * 100d);
        double crime = crime(infraCents);
        double disease = disease(infraCents);
        return PW.City.getPopulation(infraCents, crime, disease, ageDays);
    }

    private double crime(long infraCents) {
        return Math.max(0d, ((Math.pow(103d - commerce, 2d) + infraCents) * 0.000009d) - policeModifier);
    }

    private double disease(long infraCents) {
        double landCents = Math.max(1d, Math.round(land * 100d));
        double densityTerm = infraCents / (landCents * 0.01d + 0.001d);
        return Math.max(
                0d,
                ((0.01d * densityTerm * densityTerm - 25d) * 0.01d)
                        + (infraCents * 0.00001d)
                        - hospitalModifier
                        + pollution * 0.05d
        );
    }

    private Map.Entry<Integer, Integer> projectileDamage(double infra, Predicate<Project> hasProject, boolean missile) {
        double normalizedInfra = Math.max(0d, infra);
        int population = population(normalizedInfra);
        boolean guidingSatellite = hasProject.test(Projects.GUIDING_SATELLITE);
        return missile
                ? ICity.missileDamageRange(normalizedInfra, land, population, guidingSatellite)
                : ICity.nukeDamageRange(normalizedInfra, land, population, guidingSatellite);
    }

    private int projectileDamageBound(double infra, Predicate<Project> hasProject, boolean missile, boolean minBound) {
        double normalizedInfra = Math.max(0d, infra);
        int population = population(normalizedInfra);
        boolean guidingSatellite = hasProject.test(Projects.GUIDING_SATELLITE);
        if (missile) {
            return minBound
                    ? ICity.missileDamageMin(normalizedInfra, land, population, guidingSatellite)
                    : ICity.missileDamageMax(normalizedInfra, land, population, guidingSatellite);
        }
        return minBound
                ? ICity.nukeDamageMin(normalizedInfra, land, population, guidingSatellite)
                : ICity.nukeDamageMax(normalizedInfra, land, population, guidingSatellite);
    }

    private ProfileCityView view(double infra) {
        return new ProfileCityView(this, Math.max(0d, infra));
    }

    private static double buildingModifier(ICity city, Building building, double modifierPerBuilding) {
        int count = Math.max(0, city.getBuilding(building));
        return count == 0 ? 0d : count * modifierPerBuilding;
    }

    private record ProfileCityView(SpecialistCityProfile profile, double infra) implements ICity {
        @Override
        public Boolean getPowered() {
            return Boolean.TRUE;
        }

        @Override
        public int getPoweredInfra() {
            return (int) Math.ceil(infra);
        }

        @Override
        public double getInfra() {
            return infra;
        }

        @Override
        public double getLand() {
            return profile.land();
        }

        @Override
        public int getBuilding(Building building) {
            if (building == Buildings.BARRACKS) {
                return profile.barracks();
            }
            if (building == Buildings.FACTORY) {
                return profile.factories();
            }
            if (building == Buildings.HANGAR) {
                return profile.hangars();
            }
            if (building == Buildings.DRYDOCK) {
                return profile.drydocks();
            }
            return 0;
        }

        @Override
        public int getBuildingOrdinal(int ordinal) {
            if (ordinal == Buildings.BARRACKS.ordinal()) {
                return profile.barracks();
            }
            if (ordinal == Buildings.FACTORY.ordinal()) {
                return profile.factories();
            }
            if (ordinal == Buildings.HANGAR.ordinal()) {
                return profile.hangars();
            }
            if (ordinal == Buildings.DRYDOCK.ordinal()) {
                return profile.drydocks();
            }
            return 0;
        }

        @Override
        public int calcCommerce(Predicate<Project> hasProject) {
            return profile.commerce();
        }

        @Override
        public int calcPopulation(Predicate<Project> hasProject) {
            return profile.population(infra);
        }

        @Override
        public double calcDisease(Predicate<Project> hasProject) {
            return profile.disease(Math.round(infra * 100d));
        }

        @Override
        public double calcCrime(Predicate<Project> hasProject) {
            return profile.crime(Math.round(infra * 100d));
        }

        @Override
        public int calcPollution(Predicate<Project> hasProject) {
            return profile.pollution();
        }

        @Override
        public long getCreated() {
            return 0L;
        }

        @Override
        public int getNumBuildings() {
            return profile.barracks() + profile.factories() + profile.hangars() + profile.drydocks();
        }

        @Override
        public int getNuke_turn() {
            return 0;
        }
    }
}
