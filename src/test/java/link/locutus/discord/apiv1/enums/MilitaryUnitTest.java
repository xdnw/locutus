package link.locutus.discord.apiv1.enums;

import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MilitaryUnitTest {

    @Test
    void maxPerDayIncludesResearchAndProjectBonuses() {
        int researchBits = Research.toBits(Map.of(Research.GROUND_EFFICIENCY, 2));
        Predicate<Project> hasProject = project -> project == Projects.PROPAGANDA_BUREAU;

        int actual = MilitaryUnit.SOLDIER.getMaxPerDay(
                10,
                hasProject,
                research -> research.getLevel(researchBits)
        );

        assertEquals(59_000, actual);
    }
}