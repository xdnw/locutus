package link.locutus.discord.sim.planners;

import link.locutus.discord.sim.SimTuning;
import link.locutus.discord.sim.Turn1DeclarePolicy;

public record SidePlannerSettings(
        int candidatesPerAttacker,
        long localSearchBudgetMs,
        int localSearchMaxIterations,
        Turn1DeclarePolicy turn1DeclarePolicy,
        double wartimeActivityUplift,
        double activityActThreshold,
        double idlePressureWeight,
        boolean useMidHorizonCounterStrengths,
    double laterDeclarationScoreThreshold,
    int maxLaterDeclarationsPerTurn,
        int projectedAuditLimit
) {
    public static final double DEFAULT_IDLE_PRESSURE_WEIGHT = 0d;
    public static final double DEFAULT_ACTING_IDLE_PRESSURE_WEIGHT = 0.35d;
    public static final boolean DEFAULT_USE_MID_HORIZON_COUNTER_STRENGTHS = false;
    public static final double DEFAULT_LATER_DECLARATION_SCORE_THRESHOLD = 8d;
    public static final int DEFAULT_MAX_LATER_DECLARATIONS_PER_TURN = Integer.MAX_VALUE;
    public static final int DEFAULT_PROJECTED_AUDIT_LIMIT = 1;

    public SidePlannerSettings {
        if (candidatesPerAttacker <= 0) {
            throw new IllegalArgumentException("candidatesPerAttacker must be > 0");
        }
        if (localSearchBudgetMs <= 0L) {
            throw new IllegalArgumentException("localSearchBudgetMs must be > 0");
        }
        if (localSearchMaxIterations <= 0) {
            throw new IllegalArgumentException("localSearchMaxIterations must be > 0");
        }
        if (turn1DeclarePolicy == null) {
            throw new IllegalArgumentException("turn1DeclarePolicy must not be null");
        }
        if (!Double.isFinite(wartimeActivityUplift)) {
            throw new IllegalArgumentException("wartimeActivityUplift must be finite");
        }
        if (!Double.isFinite(activityActThreshold) || activityActThreshold < 0d || activityActThreshold > 1d) {
            throw new IllegalArgumentException("activityActThreshold must be finite and in [0, 1]");
        }
        if (!Double.isFinite(idlePressureWeight)) {
            throw new IllegalArgumentException("idlePressureWeight must be finite");
        }
        if (!Double.isFinite(laterDeclarationScoreThreshold)) {
            throw new IllegalArgumentException("laterDeclarationScoreThreshold must be finite");
        }
        if (maxLaterDeclarationsPerTurn <= 0) {
            throw new IllegalArgumentException("maxLaterDeclarationsPerTurn must be > 0");
        }
        if (projectedAuditLimit <= 0) {
            throw new IllegalArgumentException("projectedAuditLimit must be > 0");
        }
    }

    public static SidePlannerSettings defaults() {
        return fromTuning(SimTuning.defaults());
    }

    public static SidePlannerSettings actingDefaults() {
        return defaults().withIdlePressureWeight(DEFAULT_ACTING_IDLE_PRESSURE_WEIGHT);
    }

    public static SidePlannerSettings fromTuning(SimTuning tuning) {
        SimTuning effective = tuning == null ? SimTuning.defaults() : tuning;
        return new SidePlannerSettings(
                effective.candidatesPerAttacker(),
                effective.localSearchBudgetMs(),
                effective.localSearchMaxIterations(),
                effective.turn1DeclarePolicy(),
                effective.wartimeActivityUplift(),
                effective.activityActThreshold(),
                DEFAULT_IDLE_PRESSURE_WEIGHT,
                DEFAULT_USE_MID_HORIZON_COUNTER_STRENGTHS,
                DEFAULT_LATER_DECLARATION_SCORE_THRESHOLD,
                DEFAULT_MAX_LATER_DECLARATIONS_PER_TURN,
                DEFAULT_PROJECTED_AUDIT_LIMIT
        );
    }

    public SidePlannerSettings withActivityActThreshold(double value) {
        return new SidePlannerSettings(
                candidatesPerAttacker,
                localSearchBudgetMs,
                localSearchMaxIterations,
                turn1DeclarePolicy,
                wartimeActivityUplift,
                value,
                idlePressureWeight,
                useMidHorizonCounterStrengths,
                laterDeclarationScoreThreshold,
                maxLaterDeclarationsPerTurn,
                projectedAuditLimit
        );
    }

    public SidePlannerSettings withCandidatesPerAttacker(int value) {
        return new SidePlannerSettings(
                value,
                localSearchBudgetMs,
                localSearchMaxIterations,
                turn1DeclarePolicy,
                wartimeActivityUplift,
                activityActThreshold,
                idlePressureWeight,
                useMidHorizonCounterStrengths,
                laterDeclarationScoreThreshold,
                maxLaterDeclarationsPerTurn,
                projectedAuditLimit
        );
    }

    public SidePlannerSettings withIdlePressureWeight(double value) {
        return new SidePlannerSettings(
                candidatesPerAttacker,
                localSearchBudgetMs,
                localSearchMaxIterations,
                turn1DeclarePolicy,
                wartimeActivityUplift,
                activityActThreshold,
                value,
                useMidHorizonCounterStrengths,
                laterDeclarationScoreThreshold,
                maxLaterDeclarationsPerTurn,
                projectedAuditLimit
        );
    }

    public SidePlannerSettings withLaterDeclarationScoreThreshold(double value) {
        return new SidePlannerSettings(
                candidatesPerAttacker,
                localSearchBudgetMs,
                localSearchMaxIterations,
                turn1DeclarePolicy,
                wartimeActivityUplift,
                activityActThreshold,
                idlePressureWeight,
                useMidHorizonCounterStrengths,
                value,
                maxLaterDeclarationsPerTurn,
                projectedAuditLimit
        );
    }

    public SidePlannerSettings withMaxLaterDeclarationsPerTurn(int value) {
        return new SidePlannerSettings(
                candidatesPerAttacker,
                localSearchBudgetMs,
                localSearchMaxIterations,
                turn1DeclarePolicy,
                wartimeActivityUplift,
                activityActThreshold,
                idlePressureWeight,
                useMidHorizonCounterStrengths,
                laterDeclarationScoreThreshold,
                value,
                projectedAuditLimit
        );
    }

    public SidePlannerSettings withProjectedAuditLimit(int value) {
        return new SidePlannerSettings(
                candidatesPerAttacker,
                localSearchBudgetMs,
                localSearchMaxIterations,
                turn1DeclarePolicy,
                wartimeActivityUplift,
                activityActThreshold,
                idlePressureWeight,
                useMidHorizonCounterStrengths,
                laterDeclarationScoreThreshold,
                maxLaterDeclarationsPerTurn,
                value
        );
    }
}