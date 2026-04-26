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

    @Test
    void genericAirCandidateSurfaceSkipsInfraAndMoneyVariants() {
        SimNation nation = nationWithSpecialists(0L);
        nation.setUnitCount(MilitaryUnit.AIRCRAFT, 8);

        List<AttackType> candidates = StrategyAttackSelection.candidateAttackTypes(nation);

        assertFalse(candidates.contains(AttackType.AIRSTRIKE_INFRA));
        assertFalse(candidates.contains(AttackType.AIRSTRIKE_MONEY));
        assertTrue(candidates.contains(AttackType.AIRSTRIKE_SOLDIER));
        assertTrue(candidates.contains(AttackType.AIRSTRIKE_TANK));
        assertTrue(candidates.contains(AttackType.AIRSTRIKE_SHIP));
        assertTrue(candidates.contains(AttackType.AIRSTRIKE_AIRCRAFT));
    }

    @Test
    void groundCandidateRequiresVerifiedSoldierFloor() {
        SimNation belowFloor = nationWithSpecialists(0L);
        belowFloor.setUnitCount(MilitaryUnit.SOLDIER, 49);
        belowFloor.setUnitCount(MilitaryUnit.TANK, 12);

        SimNation legalGround = nationWithSpecialists(0L);
        legalGround.setUnitCount(MilitaryUnit.SOLDIER, 50);
        legalGround.setUnitCount(MilitaryUnit.TANK, 12);

        assertFalse(StrategyAttackSelection.candidateAttackTypes(belowFloor).contains(AttackType.GROUND));
        assertTrue(StrategyAttackSelection.candidateAttackTypes(legalGround).contains(AttackType.GROUND));
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