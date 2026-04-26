package link.locutus.discord.sim.planners;

/**
 * Named planner-local coordination overrides layered over the exact-validator script profile.
 *
 * <p>This keeps common MAP-reserve and reset-window wait behavior explicit at planner entry
 * points without making callers hand-build {@link PlannerExactValidatorScripts} bundles for
 * coordination-only concerns.</p>
 */
public record PlannerCoordinationPolicy(
        int mapReserveFloor,
        boolean holdForSpecialistNearReset
) {
    public static final PlannerCoordinationPolicy NONE = new PlannerCoordinationPolicy(0, false);

    public PlannerCoordinationPolicy {
        if (mapReserveFloor < 0) {
            throw new IllegalArgumentException("mapReserveFloor must be >= 0");
        }
    }

    public static PlannerCoordinationPolicy mapReserve(int mapReserveFloor) {
        return new PlannerCoordinationPolicy(mapReserveFloor, false);
    }

    public static PlannerCoordinationPolicy resetWindowSpecialistHold() {
        return new PlannerCoordinationPolicy(0, true);
    }

    public PlannerCoordinationPolicy withMapReserve(int floor) {
        return new PlannerCoordinationPolicy(floor, holdForSpecialistNearReset);
    }

    public PlannerCoordinationPolicy withResetWindowSpecialistHold() {
        if (holdForSpecialistNearReset) {
            return this;
        }
        return new PlannerCoordinationPolicy(mapReserveFloor, true);
    }

    public boolean isDefault() {
        return mapReserveFloor == 0 && !holdForSpecialistNearReset;
    }

    PlannerExactValidatorScripts applyToDefaultScripts() {
        return applyTo(PlannerExactValidatorScripts.DEFAULT);
    }

    PlannerExactValidatorScripts applyTo(PlannerExactValidatorScripts baseScripts) {
        PlannerExactValidatorScripts base = baseScripts == null
                ? PlannerExactValidatorScripts.DEFAULT
                : baseScripts;
        boolean reserve = base.mapReserveScript() || mapReserveFloor > 0;
        int reserveFloor = reserve ? Math.max(base.mapReserveFloor(), mapReserveFloor) : 0;
        boolean idleWait = base.idleWaitScript() || holdForSpecialistNearReset;
        PlannerExactValidatorScripts.AttackSequenceProfile attackSequenceProfile = holdForSpecialistNearReset
                ? PlannerExactValidatorScripts.AttackSequenceProfile.SPECIALIST_FIRST
                : base.attackSequenceProfile();

        if (reserve == base.mapReserveScript()
                && reserveFloor == base.mapReserveFloor()
                && idleWait == base.idleWaitScript()
                && attackSequenceProfile == base.attackSequenceProfile()) {
            return base;
        }

        return new PlannerExactValidatorScripts(
                base.declareWarScript(),
                base.openerSequenceScript(),
                base.followUpAttackScript(),
                base.rebuildScript(),
                idleWait,
                base.policyBuyScript(),
                base.peaceOfferScript(),
                reserve,
                attackSequenceProfile,
                reserveFloor,
                base.allowedAttackTypes()
        );
    }
}