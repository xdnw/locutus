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
 * Compact immutable city shape used to keep specialist city-damage math on the shared owner path.
 *
 * <p>The sim/planner state keeps infra as the only mutable city value. The remaining fields capture
 * the static inputs needed to recompute population-sensitive missile/nuke damage as infra changes,
 * without carrying a full mutable city build into the planner/runtime substrate.</p>
 */
public record SpecialistCityProfile(
        double land,
        int ageDays,
        int commerce,
        int pollution,
        double hospitalModifier,
        double policeModifier
) {
    public static final double DEFAULT_LAND = 250d;
    public static final SpecialistCityProfile DEFAULT = new SpecialistCityProfile(DEFAULT_LAND, 1, 0, 0, 0d, 0d);

    public SpecialistCityProfile {
        land = Double.isFinite(land) && land > 0d ? land : DEFAULT_LAND;
        ageDays = Math.max(1, ageDays);
        commerce = Math.max(0, commerce);
        pollution = Math.max(0, pollution);
        hospitalModifier = Math.max(0d, hospitalModifier);
        policeModifier = Math.max(0d, policeModifier);
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
                buildingModifier(city, Buildings.POLICE_STATION, hasProject.test(Projects.SPECIALIZED_POLICE_TRAINING_PROGRAM) ? 3.5d : 2.5d)
        );
    }

    public Map.Entry<Integer, Integer> missileDamage(double infra, Predicate<Project> hasProject) {
        return view(infra).getMissileDamage(hasProject);
    }

    public Map.Entry<Integer, Integer> nukeDamage(double infra, Predicate<Project> hasProject) {
        return view(infra).getNukeDamage(hasProject);
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
            return 0;
        }

        @Override
        public int getBuildingOrdinal(int ordinal) {
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
            return 0;
        }

        @Override
        public int getNuke_turn() {
            return 0;
        }
    }
}