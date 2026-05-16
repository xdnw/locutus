package link.locutus.discord.sim.planners;

import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.sim.SimWar;
import link.locutus.discord.sim.combat.MutableAttackResult;
import link.locutus.discord.sim.combat.SuperiorityFlagDelta;
import link.locutus.discord.sim.combat.UnitEconomy;

final class OpeningMetricSummary {
    private static final double DOMAIN_DAMAGE_SCALE = 6.0d;
    private static final double REBUILD_DELAY_SCALE = 2.5d;
    private static final double GROUND_CAPABILITY_WEIGHT = 1.20d;
    private static final double AIR_CAPABILITY_WEIGHT = 1.75d;
    private static final double NAVAL_CAPABILITY_WEIGHT = 0.82d;
    private static final double MISSILE_CAPABILITY_WEIGHT = 0.70d;
    private static final double NUKE_CAPABILITY_WEIGHT = 0.90d;
    private static final double SPY_CAPABILITY_WEIGHT = 0.35d;
    private static final double GROUND_CONTROL_IMPACT_WEIGHT = 1.20d;
    private static final double AIR_CONTROL_IMPACT_WEIGHT = 1.45d;
    private static final double BLOCKADE_IMPACT_WEIGHT = 1.00d;
    private static final double GROUND_REFERENCE = 120_000d;
    private static final double AIR_REFERENCE = 2_500d;
    private static final double NAVAL_REFERENCE = 250d;
    private static final double MISSILE_REFERENCE = 18d;
    private static final double NUKE_REFERENCE = 12d;
    private static final double SPY_REFERENCE = 60d;
    private static final double DEFENDER_CONTROL_PRESSURE_REFERENCE = 250_000d;
    private static final double STRATEGIC_PRESSURE_REFERENCE = 3_600d;
    private static final double LOG1P_GROUND_REFERENCE = Math.log1p(GROUND_REFERENCE);
    private static final double LOG1P_AIR_REFERENCE = Math.log1p(AIR_REFERENCE);
    private static final double LOG1P_NAVAL_REFERENCE = Math.log1p(NAVAL_REFERENCE);
    private static final double LOG1P_MISSILE_REFERENCE = Math.log1p(MISSILE_REFERENCE);
    private static final double LOG1P_NUKE_REFERENCE = Math.log1p(NUKE_REFERENCE);
    private static final double LOG1P_SPY_REFERENCE = Math.log1p(SPY_REFERENCE);
    private static final double LOG1P_DEFENDER_CONTROL_PRESSURE_REFERENCE = Math.log1p(DEFENDER_CONTROL_PRESSURE_REFERENCE);
    private static final double LOG1P_STRATEGIC_PRESSURE_REFERENCE = Math.log1p(STRATEGIC_PRESSURE_REFERENCE);

    private OpeningMetricSummary() {
    }

    static double immediateHarm(
        DBNationSnapshot defender,
        MutableAttackResult result,
        boolean attackerHadAirControl,
        boolean attackerHasAirControl,
        boolean defenderHadGroundSuperiority,
        boolean defenderHadAirControl,
        boolean defenderHadBlockade
    ) {
    return expectedCapabilityDamage(
            defender,
            result.defenderLossesEv(),
            attackerHadAirControl,
            attackerHasAirControl,
            defenderHadGroundSuperiority,
            defenderHadAirControl,
            defenderHadBlockade
        )
        + defenderControlImpact(
            defender,
            result.controlDelta(),
            result.defenderLossesEv(),
            defenderHadGroundSuperiority,
            defenderHadAirControl,
            defenderHadBlockade
        );
    }

    static double tacticalMomentumScore(int defenderResistance) {
        // Measures how much of the defender's resistance has been drained (0 = full, 1 = exhausted).
        // Captures tactical transition propensity: a fully drained war allows the attacker to pivot
        // to new engagements, but carries no action-space quality information.
        return clamp01(
                (SimWar.INITIAL_RESISTANCE - Math.max(0, defenderResistance)) / (double) SimWar.INITIAL_RESISTANCE
        );
    }

    static double actionSpaceQuality(
            double initialAttackerGround,
            double currentAttackerGround,
            double initialDefenderGround,
            double currentDefenderGround,
            double initialAttackerAir,
            double currentAttackerAir,
            double initialDefenderAir,
            double currentDefenderAir,
            double initialAttackerNaval,
            double currentAttackerNaval,
            double initialDefenderNaval,
            double currentDefenderNaval
    ) {
        double initialQuality = combatOptionQuality(
                initialAttackerGround,
                initialDefenderGround,
                initialAttackerAir,
                initialDefenderAir,
                initialAttackerNaval,
                initialDefenderNaval
        );
        double currentQuality = combatOptionQuality(
                currentAttackerGround,
                currentDefenderGround,
                currentAttackerAir,
                currentDefenderAir,
                currentAttackerNaval,
                currentDefenderNaval
        );
        return Math.max(0d, currentQuality - initialQuality);
    }

    static double targetPressure(
            double attackerGround,
            double defenderGround,
            double attackerAir,
            double defenderAir,
            double attackerNaval,
            double defenderNaval
    ) {
        return defenderControlPressure(defenderGround, defenderAir, defenderNaval);
    }

    static double defenderControlPressure(
            double defenderGround,
            double defenderAir,
            double defenderNaval
    ) {
        // Prices the defender's contribution to enemy control without consulting attacker odds
        // or nation score. Stronger defenders are worth pressuring more regardless of which
        // attacker is asking.
        double defenderMilitary = defenderGround + (3d * defenderAir) + (2d * defenderNaval);
        if (!(defenderMilitary > 0d)) {
            return 0d;
        }
        double absoluteThreat = Math.min(2.5d, Math.log1p(defenderMilitary) / LOG1P_DEFENDER_CONTROL_PRESSURE_REFERENCE);
        return 12d * absoluteThreat;
    }

    static double defenderControlPressure(DBNationSnapshot defender) {
        double ground = groundStrength(
                defender.unit(MilitaryUnit.SOLDIER),
                defender.unit(MilitaryUnit.TANK),
                false
        );
        double basePressure = defenderControlPressure(
                ground,
                defender.unit(MilitaryUnit.AIRCRAFT),
                defender.unit(MilitaryUnit.SHIP)
        );
        if (!(basePressure > 0d)) {
            return 0d;
        }
        return basePressure + strategicPressureBonus(defender);
    }

    static double groundStrength(double soldiers, double tanks, boolean underAir) {
        return UnitEconomy.groundStrengthRaw(
                clampNonNegativeRound(soldiers),
                clampNonNegativeRound(tanks),
                true,
                underAir
        );
    }

        static double expectedCapabilityDamage(
            DBNationSnapshot nation,
            double[] losses,
            boolean enemyHadAirControl,
            boolean enemyHasAirControl,
            boolean hadGroundSuperiority,
            boolean hadAirControl,
            boolean hadBlockade
        ) {
        double remainingSoldiers = remainingUnits(nation, losses, MilitaryUnit.SOLDIER);
        double remainingTanks = remainingUnits(nation, losses, MilitaryUnit.TANK);
        double remainingAircraft = remainingUnits(nation, losses, MilitaryUnit.AIRCRAFT);
        double remainingShips = remainingUnits(nation, losses, MilitaryUnit.SHIP);
        double remainingMissiles = remainingUnits(nation, losses, MilitaryUnit.MISSILE);
        double remainingNukes = remainingUnits(nation, losses, MilitaryUnit.NUKE);
        double remainingSpies = remainingUnits(nation, losses, MilitaryUnit.SPIES);

        double initialGround = groundStrength(
            nation.unit(MilitaryUnit.SOLDIER),
            nation.unit(MilitaryUnit.TANK),
            enemyHadAirControl
        );
        double currentGround = groundStrength(remainingSoldiers, remainingTanks, enemyHasAirControl);
        double initialAir = nation.unit(MilitaryUnit.AIRCRAFT);
        double currentAir = remainingAircraft;
        double initialNaval = nation.unit(MilitaryUnit.SHIP);
        double currentNaval = remainingShips;
        double initialMissiles = nation.unit(MilitaryUnit.MISSILE);
        double currentMissiles = remainingMissiles;
        double initialNukes = nation.unit(MilitaryUnit.NUKE);
        double currentNukes = remainingNukes;
        double initialSpies = nation.unit(MilitaryUnit.SPIES);
        double currentSpies = remainingSpies;

        double total = capabilityDomainDamage(initialGround, currentGround, GROUND_CAPABILITY_WEIGHT, LOG1P_GROUND_REFERENCE)
            + capabilityDomainDamage(initialAir, currentAir, AIR_CAPABILITY_WEIGHT, LOG1P_AIR_REFERENCE)
            + capabilityDomainDamage(initialNaval, currentNaval, NAVAL_CAPABILITY_WEIGHT, LOG1P_NAVAL_REFERENCE)
            + capabilityDomainDamage(initialMissiles, currentMissiles, MISSILE_CAPABILITY_WEIGHT, LOG1P_MISSILE_REFERENCE)
            + capabilityDomainDamage(initialNukes, currentNukes, NUKE_CAPABILITY_WEIGHT, LOG1P_NUKE_REFERENCE)
            + capabilityDomainDamage(initialSpies, currentSpies, SPY_CAPABILITY_WEIGHT, LOG1P_SPY_REFERENCE);

        double groundLossFraction = lossFraction(initialGround, currentGround);
        double airLossFraction = lossFraction(initialAir, currentAir);
        double navalLossFraction = lossFraction(initialNaval, currentNaval);

        total += rebuildDelayScore(
            remainingCapacity(nation, MilitaryUnit.SOLDIER),
            losses[MilitaryUnit.SOLDIER.ordinal()],
            groundLossFraction,
            GROUND_CAPABILITY_WEIGHT,
            initialGround,
            LOG1P_GROUND_REFERENCE
        );
        total += rebuildDelayScore(
            remainingCapacity(nation, MilitaryUnit.TANK),
            losses[MilitaryUnit.TANK.ordinal()],
            groundLossFraction,
            GROUND_CAPABILITY_WEIGHT,
            initialGround,
            LOG1P_GROUND_REFERENCE
        );
        total += rebuildDelayScore(
            remainingCapacity(nation, MilitaryUnit.AIRCRAFT),
            losses[MilitaryUnit.AIRCRAFT.ordinal()],
            airLossFraction,
            AIR_CAPABILITY_WEIGHT,
            initialAir,
            LOG1P_AIR_REFERENCE
        );
        total += rebuildDelayScore(
            remainingCapacity(nation, MilitaryUnit.SHIP),
            losses[MilitaryUnit.SHIP.ordinal()],
            navalLossFraction,
            NAVAL_CAPABILITY_WEIGHT,
            initialNaval,
            LOG1P_NAVAL_REFERENCE
        );

        total += holdabilityLossScore(
            hadGroundSuperiority,
            canHoldGround(remainingSoldiers, remainingTanks),
            initialGround,
            LOG1P_GROUND_REFERENCE,
            GROUND_CONTROL_IMPACT_WEIGHT
        );
        total += holdabilityLossScore(
            hadAirControl,
            remainingAircraft > 0d,
            initialAir,
            LOG1P_AIR_REFERENCE,
            AIR_CONTROL_IMPACT_WEIGHT
        );
        total += holdabilityLossScore(
            hadBlockade,
            remainingShips > 0d,
            initialNaval,
            LOG1P_NAVAL_REFERENCE,
            BLOCKADE_IMPACT_WEIGHT
        );

        return total;
        }

        private static double defenderControlImpact(
            DBNationSnapshot defender,
            SuperiorityFlagDelta controlDelta,
            double[] losses,
            boolean defenderHadGroundSuperiority,
            boolean defenderHadAirControl,
            boolean defenderHadBlockade
        ) {
        SuperiorityFlagDelta delta = controlDelta == null ? SuperiorityFlagDelta.NONE : controlDelta;
        double score = 0d;
        score += controlImpactScore(
            delta.groundSuperiority() > 0
                || delta.clearGroundSuperiority()
                || (defenderHadGroundSuperiority && !canHoldGround(
                    remainingUnits(defender, losses, MilitaryUnit.SOLDIER),
                    remainingUnits(defender, losses, MilitaryUnit.TANK)
                )),
            groundStrength(defender.unit(MilitaryUnit.SOLDIER), defender.unit(MilitaryUnit.TANK), false),
            LOG1P_GROUND_REFERENCE,
            GROUND_CONTROL_IMPACT_WEIGHT
        );
        score += controlImpactScore(
            delta.airSuperiority() > 0
                || delta.clearAirSuperiority()
                || (defenderHadAirControl && !(remainingUnits(defender, losses, MilitaryUnit.AIRCRAFT) > 0d)),
            defender.unit(MilitaryUnit.AIRCRAFT),
            LOG1P_AIR_REFERENCE,
            AIR_CONTROL_IMPACT_WEIGHT
        );
        score += controlImpactScore(
            delta.blockade() > 0
                || delta.clearBlockade()
                || (defenderHadBlockade && !(remainingUnits(defender, losses, MilitaryUnit.SHIP) > 0d)),
            defender.unit(MilitaryUnit.SHIP),
            LOG1P_NAVAL_REFERENCE,
            BLOCKADE_IMPACT_WEIGHT
        );
        return score;
    }

    private static double combatOptionQuality(
            double attackerGround,
            double defenderGround,
            double attackerAir,
            double defenderAir,
            double attackerNaval,
            double defenderNaval
    ) {
        return (1.15d * domainOptionQuality(attackerGround, defenderGround))
                + (1.35d * domainOptionQuality(attackerAir, defenderAir))
                + (0.80d * domainOptionQuality(attackerNaval, defenderNaval));
    }

    private static double domainOptionQuality(double attackerStrength, double defenderStrength) {
        if (!(attackerStrength > 0d)) {
            return 0d;
        }
        if (!(defenderStrength > 0d)) {
            return 1d;
        }
        return clamp01(attackerStrength / (attackerStrength + defenderStrength));
    }

    private static double lossFraction(double initial, double current) {
        if (!(initial > 0d)) {
            return 0d;
        }
        return clamp01((initial - Math.max(0d, current)) / initial);
    }

    private static double strategicPressureBonus(DBNationSnapshot defender) {
        double eliteCityBonus = 2.5d * clamp01((defender.cities() - 35d) / 15d);
        double strategicValueBonus = 1.5d * normalizedLogWithReferenceLog(
                StrategicCapabilityReducer.slotCapabilityValue(PlannerStrategicValue.capabilityVector(defender)),
                LOG1P_STRATEGIC_PRESSURE_REFERENCE
        );
        return eliteCityBonus + strategicValueBonus;
    }

    private static double normalizedLogWithReferenceLog(double value, double referenceLog) {
        if (!(value > 0d) || !(referenceLog > 0d)) {
            return 0d;
        }
        return clamp01(Math.log1p(value) / referenceLog);
    }

    private static double capabilityDomainDamage(
            double initialCapability,
            double currentCapability,
            double weight,
            double referenceLog
    ) {
        double lossFraction = lossFraction(initialCapability, currentCapability);
        if (!(lossFraction > 0d)) {
            return 0d;
        }
        return DOMAIN_DAMAGE_SCALE
                * weight
                * lossFraction
                * (0.50d + normalizedLogWithReferenceLog(initialCapability, referenceLog));
    }

    private static double rebuildDelayScore(
            int remainingCapacity,
            double losses,
            double domainLossFraction,
            double weight,
            double initialCapability,
            double referenceLog
    ) {
        if (!(losses > 0d) || !(domainLossFraction > 0d)) {
            return 0d;
        }
        double cappedRecoveryPressure = clamp01(losses / Math.max(1d, remainingCapacity * 2d));
        return REBUILD_DELAY_SCALE
                * weight
                * domainLossFraction
                * cappedRecoveryPressure
                * (0.35d + normalizedLogWithReferenceLog(initialCapability, referenceLog));
    }

    private static double holdabilityLossScore(
            boolean heldBefore,
            boolean canHoldAfter,
            double initialCapability,
            double referenceLog,
            double weight
    ) {
        if (!heldBefore || canHoldAfter) {
            return 0d;
        }
        return controlImpactScore(true, initialCapability, referenceLog, weight);
    }

    private static double controlImpactScore(
            boolean lostControl,
            double initialCapability,
            double referenceLog,
            double weight
    ) {
        if (!lostControl) {
            return 0d;
        }
        return weight * (0.75d + normalizedLogWithReferenceLog(initialCapability, referenceLog));
    }

    private static int remainingCapacity(DBNationSnapshot nation, MilitaryUnit unit) {
        return Math.max(0, nation.dailyBuyCap(unit) - nation.unitsBoughtToday(unit) - nation.pendingBuysNextTurn(unit));
    }

    private static double remainingUnits(
            DBNationSnapshot nation,
            double[] losses,
            MilitaryUnit unit
    ) {
        return Math.max(0d, nation.unit(unit) - losses[unit.ordinal()]);
    }

    private static boolean canHoldGround(double soldiers, double tanks) {
        return soldiers > 0d || tanks > 0d;
    }

    private static int clampNonNegativeRound(double value) {
        return Math.max(0, (int) Math.round(value));
    }

    private static double clamp01(double value) {
        if (value <= 0d) {
            return 0d;
        }
        if (value >= 1d) {
            return 1d;
        }
        return value;
    }
}
