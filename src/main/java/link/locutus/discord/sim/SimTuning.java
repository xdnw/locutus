package link.locutus.discord.sim;

import link.locutus.discord.sim.combat.ResolutionMode;

public record SimTuning(
        int intraTurnPasses,
        Turn1DeclarePolicy turn1DeclarePolicy,
        double wartimeActivityUplift,
        double activityActThreshold,
        int policyCooldownTurns,
        long localSearchBudgetMs,
        int localSearchMaxIterations,
        int candidatesPerAttacker,
        int beigeTurnsOnDefeat,
        ResolutionMode stateResolutionMode,
        long stochasticSeed,
        int stochasticSampleCount
) {
    public static final int DEFAULT_INTRA_TURN_PASSES = 2;
    public static final Turn1DeclarePolicy DEFAULT_TURN1_DECLARE_POLICY =
            Turn1DeclarePolicy.ATTACKERS_OPEN_THEN_FREE_DEFENDERS_COUNTER;
    public static final double DEFAULT_WARTIME_ACTIVITY_UPLIFT = 0.15d;
    public static final double DEFAULT_ACTIVITY_ACT_THRESHOLD = 0.5d;
    public static final int DEFAULT_POLICY_COOLDOWN_TURNS = 60;
    public static final long DEFAULT_LOCAL_SEARCH_BUDGET_MS = 250L;
    public static final int DEFAULT_LOCAL_SEARCH_MAX_ITERATIONS = 500;
    public static final int DEFAULT_CANDIDATES_PER_ATTACKER = 8;
    public static final int DEFAULT_BEIGE_TURNS_ON_DEFEAT = 24;
    public static final ResolutionMode DEFAULT_STATE_RESOLUTION_MODE = ResolutionMode.MOST_LIKELY;
    public static final long DEFAULT_STOCHASTIC_SEED = 1L;
    public static final int DEFAULT_STOCHASTIC_SAMPLE_COUNT = 16;

    public SimTuning {
        if (intraTurnPasses <= 0) {
            throw new IllegalArgumentException("intraTurnPasses must be > 0");
        }
        if (turn1DeclarePolicy == null) {
            throw new IllegalArgumentException("turn1DeclarePolicy must not be null");
        }
        if (Double.isNaN(wartimeActivityUplift) || Double.isInfinite(wartimeActivityUplift)) {
            throw new IllegalArgumentException("wartimeActivityUplift must be finite");
        }
        if (activityActThreshold < 0.0 || activityActThreshold > 1.0) {
            throw new IllegalArgumentException("activityActThreshold must be in [0, 1]");
        }
        if (policyCooldownTurns <= 0) {
            throw new IllegalArgumentException("policyCooldownTurns must be > 0");
        }
        if (localSearchBudgetMs <= 0L) {
            throw new IllegalArgumentException("localSearchBudgetMs must be > 0");
        }
        if (localSearchMaxIterations <= 0) {
            throw new IllegalArgumentException("localSearchMaxIterations must be > 0");
        }
        if (candidatesPerAttacker <= 0) {
            throw new IllegalArgumentException("candidatesPerAttacker must be > 0");
        }
        if (beigeTurnsOnDefeat <= 0) {
            throw new IllegalArgumentException("beigeTurnsOnDefeat must be > 0");
        }
        if (stateResolutionMode == null) {
            throw new IllegalArgumentException("stateResolutionMode must not be null");
        }
        if (stochasticSampleCount <= 0) {
            throw new IllegalArgumentException("stochasticSampleCount must be > 0");
        }
    }

    public SimTuning(
            int intraTurnPasses,
            Turn1DeclarePolicy turn1DeclarePolicy,
            double wartimeActivityUplift,
            double activityActThreshold,
            int policyCooldownTurns,
            long localSearchBudgetMs,
            int localSearchMaxIterations,
            int candidatesPerAttacker,
            int beigeTurnsOnDefeat
    ) {
        this(
                intraTurnPasses,
                turn1DeclarePolicy,
                wartimeActivityUplift,
                activityActThreshold,
                policyCooldownTurns,
                localSearchBudgetMs,
                localSearchMaxIterations,
                candidatesPerAttacker,
                beigeTurnsOnDefeat,
                DEFAULT_STATE_RESOLUTION_MODE,
                DEFAULT_STOCHASTIC_SEED,
                DEFAULT_STOCHASTIC_SAMPLE_COUNT
        );
    }

    public SimTuning(
            int intraTurnPasses,
            Turn1DeclarePolicy turn1DeclarePolicy,
            double wartimeActivityUplift,
            double activityActThreshold,
            int policyCooldownTurns,
            long localSearchBudgetMs,
            int localSearchMaxIterations,
            int candidatesPerAttacker,
            int beigeTurnsOnDefeat,
            ResolutionMode stateResolutionMode,
            long stochasticSeed
    ) {
        this(
                intraTurnPasses,
                turn1DeclarePolicy,
                wartimeActivityUplift,
                activityActThreshold,
                policyCooldownTurns,
                localSearchBudgetMs,
                localSearchMaxIterations,
                candidatesPerAttacker,
                beigeTurnsOnDefeat,
                stateResolutionMode,
                stochasticSeed,
                DEFAULT_STOCHASTIC_SAMPLE_COUNT
        );
    }

    public SimTuning(int policyCooldownTurns) {
        this(
                DEFAULT_INTRA_TURN_PASSES,
                DEFAULT_TURN1_DECLARE_POLICY,
                DEFAULT_WARTIME_ACTIVITY_UPLIFT,
                DEFAULT_ACTIVITY_ACT_THRESHOLD,
                policyCooldownTurns,
                DEFAULT_LOCAL_SEARCH_BUDGET_MS,
                DEFAULT_LOCAL_SEARCH_MAX_ITERATIONS,
                DEFAULT_CANDIDATES_PER_ATTACKER,
                DEFAULT_BEIGE_TURNS_ON_DEFEAT,
                DEFAULT_STATE_RESOLUTION_MODE,
                DEFAULT_STOCHASTIC_SEED,
                DEFAULT_STOCHASTIC_SAMPLE_COUNT
        );
    }

    public SimTuning(int policyCooldownTurns, int beigeTurnsOnDefeat) {
        this(
                DEFAULT_INTRA_TURN_PASSES,
                DEFAULT_TURN1_DECLARE_POLICY,
                DEFAULT_WARTIME_ACTIVITY_UPLIFT,
                DEFAULT_ACTIVITY_ACT_THRESHOLD,
                policyCooldownTurns,
                DEFAULT_LOCAL_SEARCH_BUDGET_MS,
                DEFAULT_LOCAL_SEARCH_MAX_ITERATIONS,
                DEFAULT_CANDIDATES_PER_ATTACKER,
                beigeTurnsOnDefeat,
                DEFAULT_STATE_RESOLUTION_MODE,
                DEFAULT_STOCHASTIC_SEED,
                DEFAULT_STOCHASTIC_SAMPLE_COUNT
        );
    }

    public SimTuning(ResolutionMode stateResolutionMode) {
        this(
                DEFAULT_INTRA_TURN_PASSES,
                DEFAULT_TURN1_DECLARE_POLICY,
                DEFAULT_WARTIME_ACTIVITY_UPLIFT,
                DEFAULT_ACTIVITY_ACT_THRESHOLD,
                DEFAULT_POLICY_COOLDOWN_TURNS,
                DEFAULT_LOCAL_SEARCH_BUDGET_MS,
                DEFAULT_LOCAL_SEARCH_MAX_ITERATIONS,
                DEFAULT_CANDIDATES_PER_ATTACKER,
                DEFAULT_BEIGE_TURNS_ON_DEFEAT,
                stateResolutionMode,
                DEFAULT_STOCHASTIC_SEED,
                DEFAULT_STOCHASTIC_SAMPLE_COUNT
        );
    }

    public SimTuning withStochasticSeed(long seed) {
        return new SimTuning(
                intraTurnPasses,
                turn1DeclarePolicy,
                wartimeActivityUplift,
                activityActThreshold,
                policyCooldownTurns,
                localSearchBudgetMs,
                localSearchMaxIterations,
                candidatesPerAttacker,
                beigeTurnsOnDefeat,
                stateResolutionMode,
                seed,
                stochasticSampleCount
        );
    }

    public SimTuning withStateResolutionMode(ResolutionMode resolutionMode) {
        return new SimTuning(
                intraTurnPasses,
                turn1DeclarePolicy,
                wartimeActivityUplift,
                activityActThreshold,
                policyCooldownTurns,
                localSearchBudgetMs,
                localSearchMaxIterations,
                candidatesPerAttacker,
                beigeTurnsOnDefeat,
                resolutionMode,
                stochasticSeed,
                stochasticSampleCount
        );
    }

    public SimTuning withStochasticSampleCount(int sampleCount) {
        return new SimTuning(
                intraTurnPasses,
                turn1DeclarePolicy,
                wartimeActivityUplift,
                activityActThreshold,
                policyCooldownTurns,
                localSearchBudgetMs,
                localSearchMaxIterations,
                candidatesPerAttacker,
                beigeTurnsOnDefeat,
                stateResolutionMode,
                stochasticSeed,
                sampleCount
        );
    }

    public static SimTuning defaults() {
        return new SimTuning(
                DEFAULT_INTRA_TURN_PASSES,
                DEFAULT_TURN1_DECLARE_POLICY,
                DEFAULT_WARTIME_ACTIVITY_UPLIFT,
                DEFAULT_ACTIVITY_ACT_THRESHOLD,
                DEFAULT_POLICY_COOLDOWN_TURNS,
                DEFAULT_LOCAL_SEARCH_BUDGET_MS,
                DEFAULT_LOCAL_SEARCH_MAX_ITERATIONS,
                DEFAULT_CANDIDATES_PER_ATTACKER,
                DEFAULT_BEIGE_TURNS_ON_DEFEAT,
                DEFAULT_STATE_RESOLUTION_MODE,
                DEFAULT_STOCHASTIC_SEED,
                DEFAULT_STOCHASTIC_SAMPLE_COUNT
        );
    }
}