package link.locutus.discord.sim.planners;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.sim.ScenarioActionPolicy;

import java.util.EnumSet;
import java.util.Objects;

/**
 * Exact-validator script profile for planner-local declared-war validation.
 *
 * <p>This profile keeps script phases explicit on the planner-local mark/apply/rollback seam
 * without requiring SimWorld actor stepping.</p>
 */
public record PlannerExactValidatorScripts(
        boolean declareWarScript,
        boolean openerSequenceScript,
        boolean followUpAttackScript,
        boolean rebuildScript,
        boolean idleWaitScript,
        boolean policyBuyScript,
        boolean mapReserveScript,
        AttackSequenceProfile attackSequenceProfile,
        int mapReserveFloor,
        EnumSet<AttackType> allowedAttackTypes
) {
    public enum AttackSequenceProfile {
        CONVENTIONAL,
        SPECIALIST_FIRST
    }

    public static final PlannerExactValidatorScripts DEFAULT = new PlannerExactValidatorScripts(
            true,
            true,
            true,
            true,
            true,
            false,
            false,
            AttackSequenceProfile.CONVENTIONAL,
            0,
            EnumSet.allOf(AttackType.class)
    );

    public PlannerExactValidatorScripts {
        if (mapReserveFloor < 0) {
            throw new IllegalArgumentException("mapReserveFloor must be >= 0");
        }
                Objects.requireNonNull(attackSequenceProfile, "attackSequenceProfile");
        Objects.requireNonNull(allowedAttackTypes, "allowedAttackTypes");
        allowedAttackTypes = allowedAttackTypes.clone();
    }

    public EnumSet<AttackType> allowedAttackTypes() {
        return allowedAttackTypes.clone();
    }

        /**
         * Maps the exact-validator script profile onto local turn-advance semantics.
         *
         * <p>Rebuild governs pending-buy materialization and policy-buy governs policy cooldown aging.
         * Idle waiting still advances through the horizon loop,
         * but the flag now also controls whether non-greedy attack scripts may deliberately skip
         * current-turn attacks to preserve MAP for a later scripted sequence.</p>
         */
        PlannerTransitionSemantics transitionSemantics() {
                return new PlannerTransitionSemantics(
                                policyBuyScript,
                                rebuildScript
                );
        }

    PlannerExactValidatorScripts constrainedByPolicies(
            ScenarioActionPolicy.NationActionPolicy attackerPolicy,
            ScenarioActionPolicy.NationActionPolicy defenderPolicy
    ) {
        ScenarioActionPolicy.NationActionPolicy attacker = attackerPolicy == null
                ? ScenarioActionPolicy.NationActionPolicy.allowAll()
                : attackerPolicy;
        ScenarioActionPolicy.NationActionPolicy defender = defenderPolicy == null
                ? ScenarioActionPolicy.NationActionPolicy.allowAll()
                : defenderPolicy;

        EnumSet<AttackType> allowedAttacks = this.allowedAttackTypes();
        allowedAttacks.retainAll(attacker.allowedAttackTypes());
        boolean allowDeclares = this.declareWarScript && attacker.allowDeclares();
        boolean opener = allowDeclares && this.openerSequenceScript && !allowedAttacks.isEmpty();
        boolean followUp = opener && this.followUpAttackScript;
        boolean allowBuys = this.policyBuyScript && (attacker.allowBuys() || defender.allowBuys());
        boolean reserve = this.mapReserveScript && attacker.allowMapReservations();
        int reserveFloor = reserve ? this.mapReserveFloor : 0;

        return new PlannerExactValidatorScripts(
                allowDeclares,
                opener,
                followUp,
                this.rebuildScript,
                this.idleWaitScript,
                allowBuys,
                reserve,
                this.attackSequenceProfile,
                reserveFloor,
                allowedAttacks
        );
    }
}
