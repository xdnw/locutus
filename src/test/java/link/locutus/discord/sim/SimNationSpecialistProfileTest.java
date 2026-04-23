package link.locutus.discord.sim;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.sim.combat.SpecialistCityProfile;
import link.locutus.discord.sim.combat.state.CombatantView;
import link.locutus.discord.sim.input.NationInit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimNationSpecialistProfileTest {

    @Test
    void defaultCombatViewUsesStoredProjectBitsAndSpecialistProfiles() {
        SpecialistCityProfile profile = new SpecialistCityProfile(1_900d, 180, 92, 18, 7d, 5d);
        long projectBits = (1L << Projects.GUIDING_SATELLITE.ordinal()) | (1L << Projects.MISSILE_LAUNCH_PAD.ordinal());
        NationInit init = new NationInit(
                77,
                77,
                WarPolicy.ATTRITION,
                ResourceType.getBuffer(),
                100d,
                new double[]{1_650d},
                5,
                (byte) 0,
                projectBits,
                new SpecialistCityProfile[]{profile}
        );

        SimNation nation = new SimNation(init);
        CombatantView view = nation.asCombatantView();
        var city = view.getCityViews().iterator().next();
        var expectedMissile = profile.missileDamage(1_650d, nation::hasProject);
        var expectedNuke = profile.nukeDamage(1_650d, nation::hasProject);
        var actualMissile = city.getMissileDamage(view::hasProject);
        var actualNuke = city.getNukeDamage(view::hasProject);

        assertTrue(nation.hasProject(Projects.GUIDING_SATELLITE));
        assertTrue(view.hasProject(Projects.GUIDING_SATELLITE));
        assertEquals(expectedMissile.getKey(), actualMissile.getKey());
        assertEquals(expectedMissile.getValue(), actualMissile.getValue());
        assertEquals(expectedNuke.getKey(), actualNuke.getKey());
        assertEquals(expectedNuke.getValue(), actualNuke.getValue());
        assertEquals(0.8d, view.looterModifier(false), 1e-12);
        assertEquals(1.1d, view.infraAttackModifier(AttackType.GROUND), 1e-12);
        assertEquals(1.0d, view.infraAttackModifier(AttackType.VICTORY), 1e-12);
    }
}