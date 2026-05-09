package link.locutus.discord.sim.planners;

/**
 * Primitive planner-local capability snapshot used by opening, slot-denial,
 * projection, and exact nation-value reducers.
 */
record StrategicCapabilityVector(
        double groundCapability,
        double airCapability,
        double navalCapability,
        double missileCapability,
        double nukeCapability,
        int soldierRemainingRecovery,
        int soldierDailyCap,
        int tankRemainingRecovery,
        int tankDailyCap,
        int airRemainingRecovery,
        int airDailyCap,
        int navalRemainingRecovery,
        int navalDailyCap
) {
    StrategicCapabilityVector {
        groundCapability = Math.max(0d, groundCapability);
        airCapability = Math.max(0d, airCapability);
        navalCapability = Math.max(0d, navalCapability);
        missileCapability = Math.max(0d, missileCapability);
        nukeCapability = Math.max(0d, nukeCapability);
        soldierRemainingRecovery = Math.max(0, soldierRemainingRecovery);
        soldierDailyCap = Math.max(0, soldierDailyCap);
        tankRemainingRecovery = Math.max(0, tankRemainingRecovery);
        tankDailyCap = Math.max(0, tankDailyCap);
        airRemainingRecovery = Math.max(0, airRemainingRecovery);
        airDailyCap = Math.max(0, airDailyCap);
        navalRemainingRecovery = Math.max(0, navalRemainingRecovery);
        navalDailyCap = Math.max(0, navalDailyCap);
    }
}
