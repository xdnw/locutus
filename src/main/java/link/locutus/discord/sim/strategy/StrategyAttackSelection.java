package link.locutus.discord.sim.strategy;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.sim.DecisionContext;
import link.locutus.discord.sim.SimNation;
import link.locutus.discord.sim.SimSide;
import link.locutus.discord.sim.SimWar;
import link.locutus.discord.sim.SimWorld;
import link.locutus.discord.sim.combat.CombatKernel;
import link.locutus.discord.sim.combat.AttackOutcome;
import link.locutus.discord.sim.combat.LiveAttackContext;
import link.locutus.discord.sim.combat.ResolutionMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class StrategyAttackSelection {

    enum CasualtyFamily {
        GROUND,
        AIR,
        NAVAL,
        INFRA,
        LOOT,
        SPECIAL
    }

    record AttackChoice(AttackType attackType, AttackOutcome outcome, CasualtyFamily family, double score) {
    }

    @FunctionalInterface
    interface AttackChoiceScorer {
        double score(SimWorld world, SimNation self, SimWar war, DecisionContext ctx, AttackChoice choice);
    }

    private StrategyAttackSelection() {
    }

    static AttackChoice selectBestChoice(
            SimWorld world,
            SimNation self,
            SimWar war,
            DecisionContext ctx,
            AttackChoiceScorer scorer
    ) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(self, "self");
        Objects.requireNonNull(war, "war");
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(scorer, "scorer");

        SimNation defender = world.requireNation(war.defenderNationId());
        LiveAttackContext attackContext = new LiveAttackContext().bind(self, defender, war, SimSide.ATTACKER);

        AttackChoice best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (AttackType attackType : candidateAttackTypes(self)) {
            AttackOutcome outcome = CombatKernel.resolve(attackContext, attackType, ResolutionMode.DETERMINISTIC_EV);
            AttackChoice candidate = new AttackChoice(attackType, outcome, familyFor(attackType), Double.NEGATIVE_INFINITY);
            double score = scorer.score(world, self, war, ctx, candidate);
            if (score > bestScore) {
                bestScore = score;
                best = new AttackChoice(attackType, outcome, candidate.family(), score);
            }
        }

        return best;
    }

    static AttackChoice bestDamageChoice(SimWorld world, SimNation self, SimWar war, DecisionContext ctx) {
        return selectBestChoice(world, self, war, ctx, (candidateWorld, attacker, candidateWar, candidateCtx, choice) -> netDamageScore(choice));
    }

    static List<AttackType> candidateAttackTypes(SimNation self) {
        List<AttackType> candidates = new ArrayList<>();
        if (self.units(MilitaryUnit.SOLDIER) > 0 || self.units(MilitaryUnit.TANK) > 0) {
            candidates.add(AttackType.GROUND);
        }
        if (self.units(MilitaryUnit.AIRCRAFT) > 0) {
            candidates.add(AttackType.AIRSTRIKE_INFRA);
            candidates.add(AttackType.AIRSTRIKE_SOLDIER);
            candidates.add(AttackType.AIRSTRIKE_TANK);
            candidates.add(AttackType.AIRSTRIKE_MONEY);
            candidates.add(AttackType.AIRSTRIKE_SHIP);
            candidates.add(AttackType.AIRSTRIKE_AIRCRAFT);
        }
        if (self.units(MilitaryUnit.SHIP) > 0) {
            candidates.add(AttackType.NAVAL);
            candidates.add(AttackType.NAVAL_INFRA);
            candidates.add(AttackType.NAVAL_AIR);
            candidates.add(AttackType.NAVAL_GROUND);
        }
        if (self.units(MilitaryUnit.MISSILE) > 0 && self.hasProject(Projects.MISSILE_LAUNCH_PAD)) {
            candidates.add(AttackType.MISSILE);
        }
        if (self.units(MilitaryUnit.NUKE) > 0 && self.hasProject(Projects.NUCLEAR_RESEARCH_FACILITY)) {
            candidates.add(AttackType.NUKE);
        }
        return candidates;
    }

    static double netDamageScore(AttackChoice choice) {
        return expectedScoreLoss(choice.outcome(), false) - expectedScoreLoss(choice.outcome(), true);
    }

    private static double expectedScoreLoss(AttackOutcome outcome, boolean attackerSide) {
        double total = attackerSide ? 0d : outcome.infraDestroyed() * MilitaryUnit.INFRASTRUCTURE.getScore(1);
        for (MilitaryUnit unit : MilitaryUnit.values) {
            if (unit == MilitaryUnit.MONEY || unit == MilitaryUnit.INFRASTRUCTURE) {
                continue;
            }
            double losses = attackerSide ? outcome.attackerLossEv(unit) : outcome.defenderLossEv(unit);
            if (losses > 0d) {
                total += losses * unit.getScore(1);
            }
        }
        return total;
    }

    private static CasualtyFamily familyFor(AttackType attackType) {
        return switch (attackType) {
            case GROUND, AIRSTRIKE_SOLDIER, AIRSTRIKE_TANK, NAVAL_GROUND -> CasualtyFamily.GROUND;
            case AIRSTRIKE_AIRCRAFT, NAVAL_AIR -> CasualtyFamily.AIR;
            case AIRSTRIKE_SHIP, NAVAL -> CasualtyFamily.NAVAL;
            case AIRSTRIKE_INFRA, NAVAL_INFRA -> CasualtyFamily.INFRA;
            case AIRSTRIKE_MONEY -> CasualtyFamily.LOOT;
            case MISSILE, NUKE -> CasualtyFamily.SPECIAL;
            default -> CasualtyFamily.SPECIAL;
        };
    }
}