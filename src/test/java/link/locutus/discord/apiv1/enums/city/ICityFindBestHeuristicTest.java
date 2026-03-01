package link.locutus.discord.apiv1.enums.city;

import link.locutus.discord.apiv1.enums.Continent;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.db.entities.DBCity;
import link.locutus.discord.db.entities.city.SimpleDBCity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

import static org.junit.jupiter.api.Assertions.*;

class ICityFindBestHeuristicTest {

    private static final Predicate<Project> NO_PROJECTS = project -> false;

    @Test
    void findBestFromDonorsEnforcesHardInvariantsAndGoalAfterNormalization() {
        JavaCity origin = originCity(1000, 1300);

        List<DBCity> donors = List.of(
                donor(1, 900, 900, 22),
                donor(2, 1200, 700, 16)
        );

        ToDoubleFunction<INationCity> value = city -> city.getBuilding(Buildings.FARM);
        Predicate<INationCity> goal = city -> city.getInfra() == 1100
                && city.getLand() == 1300
                && city.getBuilding(Buildings.BARRACKS) == origin.getBuilding(Buildings.BARRACKS)
                && city.getBuilding(Buildings.FACTORY) == origin.getBuilding(Buildings.FACTORY)
                && city.getBuilding(Buildings.HANGAR) == origin.getBuilding(Buildings.HANGAR)
                && city.getBuilding(Buildings.DRYDOCK) == origin.getBuilding(Buildings.DRYDOCK);

        INationCity best = origin.findBestFromDonors(
                Continent.NORTH_AMERICA,
                15,
                value,
                goal,
                NO_PROJECTS,
                0,
                1,
                1100d,
                donors
        );

        assertNotNull(best, "Expected a valid fallback candidate");
        assertEquals(1100, best.getInfra(), 0.0001);
        assertEquals(1300, best.getLand(), 0.0001);
        assertTrue(best.canBuild(Continent.NORTH_AMERICA, NO_PROJECTS, false));
        assertTrue(best.getRequiredInfra() <= best.getInfra());
        assertTrue(best.getPoweredInfra() >= best.getInfra());

        assertEquals(origin.getBuilding(Buildings.BARRACKS), best.getBuilding(Buildings.BARRACKS));
        assertEquals(origin.getBuilding(Buildings.FACTORY), best.getBuilding(Buildings.FACTORY));
        assertEquals(origin.getBuilding(Buildings.HANGAR), best.getBuilding(Buildings.HANGAR));
        assertEquals(origin.getBuilding(Buildings.DRYDOCK), best.getBuilding(Buildings.DRYDOCK));
    }

    @Test
    void heuristicExpansionFindsBetterThanExactSlotOnly() {
        JavaCity origin = originCity(1000, 1200);

        List<DBCity> donors = List.of(
                donor(1, 900, 900, 16),
                donor(2, 900, 900, 15)
        );

        ToDoubleFunction<INationCity> value = city -> -Math.abs(city.getBuilding(Buildings.FARM) - 15);

        INationCity exactOnly = origin.findBestExactSlotOnlyFromDonors(
                Continent.NORTH_AMERICA,
                15,
                value,
                null,
                NO_PROJECTS,
                0,
                1,
                1100d,
                donors
        );

        INationCity heuristic = origin.findBestFromDonors(
                Continent.NORTH_AMERICA,
                15,
                value,
                null,
                NO_PROJECTS,
                0,
                1,
                1100d,
                donors
        );

        assertNotNull(exactOnly);
        assertNotNull(heuristic);
        assertTrue(value.applyAsDouble(heuristic) >= value.applyAsDouble(exactOnly));
        assertEquals(15, heuristic.getBuilding(Buildings.FARM));
    }

    @Test
    void exactSlotStillWinsWhenItHasHigherValue() {
        JavaCity origin = originCity(1000, 1200);

        List<DBCity> donors = List.of(
                donor(1, 900, 900, 16),
                donor(2, 900, 900, 15)
        );

        ToDoubleFunction<INationCity> value = city -> city.getBuilding(Buildings.FARM);

        INationCity heuristic = origin.findBestFromDonors(
                Continent.NORTH_AMERICA,
                15,
                value,
                null,
                NO_PROJECTS,
                0,
                1,
                1100d,
                donors
        );

        assertNotNull(heuristic);
        assertEquals(16, heuristic.getBuilding(Buildings.FARM));
    }

    private static JavaCity originCity(double infra, double land) {
        JavaCity city = new JavaCity();
        city.setInfra(infra);
        city.setLand(land);
        city.setBuilding(Buildings.BARRACKS, 2);
        city.setBuilding(Buildings.FACTORY, 1);
        city.setBuilding(Buildings.HANGAR, 1);
        city.setBuilding(Buildings.DRYDOCK, 1);
        city.setOptimalPower(Continent.NORTH_AMERICA);
        city.setDateCreated(1_700_000_000_000L);
        return city;
    }

    private static DBCity donor(int id, double infra, double land, int farmCount) {
        SimpleDBCity donor = new SimpleDBCity(1);
        donor.setId(id);
        donor.setInfra(infra);
        donor.setLand(land);
        donor.setCreated(1_700_000_000_000L);
        donor.setPowered(true);
        donor.setBuilding(Buildings.FARM, farmCount);
        return donor;
    }
}
