package link.locutus.discord.apiv1.enums.city;

import link.locutus.discord.apiv1.enums.Continent;
import link.locutus.discord.apiv1.enums.city.building.Building;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.db.entities.DBCity;
import link.locutus.discord.db.entities.city.SimpleDBCity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OptimalBuildCapRegressionTest {

    private static final Predicate<Project> NO_PROJECTS = p -> false;

    @Test
    void cityBranchOptimalBuildDoesNotExceedAnyBuildingCap() {
        JavaCity origin = new JavaCity()
                .setInfra(2000)
                .setLand(2500d)
                .setAge(1500);
        origin.setOptimalPower(Continent.NORTH_AMERICA);

        ToDoubleFunction<INationCity> extremeObjective = city ->
                (city.getBuilding(Buildings.STADIUM) * 100_000d)
                        + city.getRevenueConverted();

        JavaCity optimized = origin.optimalBuild(
                Continent.NORTH_AMERICA,
                20,
                extremeObjective,
                null,
                NO_PROJECTS,
                3_000,
                0d,
                false,
                false,
                1d,
                null);

        assertNotNull(optimized);
        for (Building building : Buildings.values()) {
            int cap = building.cap(NO_PROJECTS);
            int amt = optimized.getBuilding(building);
            assertTrue(amt <= cap,
                    () -> building.name() + " exceeds cap: " + amt + " > " + cap);
        }
    }

    @Test
    void fallbackBeamPathShouldNotReturnOverCapDonorWhenSlotsAlreadyMatch() {
        JavaCity source = new JavaCity()
                .setInfra(500)
                .setLand(1500d)
                .setAge(800);

        JavaCity invalidDonor = new JavaCity()
                .setInfra(500)
                .setLand(1500d)
                .setAge(800);
        invalidDonor.setBuilding(Buildings.STADIUM, 9);

        JavaCity validDonor = new JavaCity()
                .setInfra(500)
                .setLand(1500d)
                .setAge(800);
        int cap = Buildings.STADIUM.cap(NO_PROJECTS);
        validDonor.setBuilding(Buildings.STADIUM, cap);

        List<DBCity> donors = List.of(new SimpleDBCity(invalidDonor), new SimpleDBCity(validDonor));

        INationCity best = source.findBestFromDonors(
                Continent.NORTH_AMERICA,
                10,
                city -> city.getBuilding(Buildings.STADIUM),
                null,
                NO_PROJECTS,
                0d,
                1d,
                null,
                donors);

        assertNotNull(best);
        assertTrue(best.getBuilding(Buildings.STADIUM) <= cap,
                "Fallback beam path returned over-cap stadium count: " + best.getBuilding(Buildings.STADIUM) + " > " + cap);
    }

    @Test
    void fallbackBeamPathRepairsSingleInvalidDonorIntoValidCandidate() {
        JavaCity source = new JavaCity()
                .setInfra(500)
                .setLand(1500d)
                .setAge(800);

        JavaCity invalidDonor = new JavaCity()
                .setInfra(500)
                .setLand(1500d)
                .setAge(800);
        invalidDonor.setBuilding(Buildings.STADIUM, 9);

        INationCity best = source.findBestFromDonors(
                Continent.NORTH_AMERICA,
                10,
                city -> city.getBuilding(Buildings.STADIUM),
                null,
                NO_PROJECTS,
                0d,
                1d,
                null,
                List.of(new SimpleDBCity(invalidDonor)));

        assertNotNull(best);
        int cap = Buildings.STADIUM.cap(NO_PROJECTS);
        assertTrue(best.getBuilding(Buildings.STADIUM) <= cap,
                "Fallback repair produced over-cap stadium count: " + best.getBuilding(Buildings.STADIUM) + " > " + cap);
    }

    @Test
    void fallbackExactSlotOnlyRejectsOverCapDonor() {
        JavaCity source = new JavaCity()
                .setInfra(500)
                .setLand(1500d)
                .setAge(800);

        JavaCity donor = new JavaCity()
                .setInfra(500)
                .setLand(1500d)
                .setAge(800);
        donor.setBuilding(Buildings.STADIUM, 9);

        INationCity best = source.findBestExactSlotOnlyFromDonors(
                Continent.NORTH_AMERICA,
                10,
                city -> city.getBuilding(Buildings.STADIUM),
                null,
                NO_PROJECTS,
                0d,
                1d,
                null,
                List.of(new SimpleDBCity(donor)));

        assertNull(best);
    }
}
