package link.locutus.discord.sim.planners;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;

class PlannerCityInfraOverlayTest {

    @Test
    void applyToRejectsMismatchedNationId() {
        PlannerCityInfraOverlay overlay = new PlannerCityInfraOverlay(101, Map.of(0, 900d));
        DBNationSnapshot snapshot = DBNationSnapshot.synthetic(202)
                .cityInfra(new double[]{1_200d, 1_100d})
                .build();

        assertThrows(IllegalArgumentException.class, () -> overlay.applyTo(snapshot));
    }
}
