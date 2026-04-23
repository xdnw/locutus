package link.locutus.discord.sim.strategy;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.sim.SimNation;
import link.locutus.discord.sim.combat.SpecialistCityProfile;
import link.locutus.discord.sim.input.NationInit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrategyAttackSelectionTest {

    @Test
    void specialistCandidateTypesRequireUnlockingProjects() {
        SimNation locked = nationWithSpecialists(0L);
        locked.setUnitCount(MilitaryUnit.MISSILE, 1);
        locked.setUnitCount(MilitaryUnit.NUKE, 1);

        List<AttackType> lockedCandidates = StrategyAttackSelection.candidateAttackTypes(locked);
        assertFalse(lockedCandidates.contains(AttackType.MISSILE));
        assertFalse(lockedCandidates.contains(AttackType.NUKE));

        long projectBits = (1L << Projects.MISSILE_LAUNCH_PAD.ordinal())
                | (1L << Projects.NUCLEAR_RESEARCH_FACILITY.ordinal());
        SimNation unlocked = nationWithSpecialists(projectBits);
        unlocked.setUnitCount(MilitaryUnit.MISSILE, 1);
        unlocked.setUnitCount(MilitaryUnit.NUKE, 1);

        List<AttackType> unlockedCandidates = StrategyAttackSelection.candidateAttackTypes(unlocked);
        assertTrue(unlockedCandidates.contains(AttackType.MISSILE));
        assertTrue(unlockedCandidates.contains(AttackType.NUKE));
    }

    private static SimNation nationWithSpecialists(long projectBits) {
        return new SimNation(new NationInit(
                901,
                901,
                WarPolicy.ATTRITION,
                ResourceType.getBuffer(),
                100d,
                new double[]{1_000d},
                5,
                (byte) 0,
                projectBits,
                new SpecialistCityProfile[]{SpecialistCityProfile.DEFAULT}
        ));
    }
}