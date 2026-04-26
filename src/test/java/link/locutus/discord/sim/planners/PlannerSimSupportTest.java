package link.locutus.discord.sim.planners;

import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.sim.SimTuning;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlannerSimSupportTest {
    @Test
    void compileActivityWeightsHonorsOverridesAndSnapshotWartimeState() {
        DBNationSnapshot wartimeAuto = nation(1)
                .currentOffensiveWars(1)
                .build();
        DBNationSnapshot forcedActive = nation(2).build();
        DBNationSnapshot forcedInactive = nation(3).build();

        OverrideSet overrides = OverrideSet.builder()
                .active(2, OverrideSet.ActiveOverride.TRUE)
                .active(3, OverrideSet.ActiveOverride.FALSE)
                .build();
        SimTuning defaults = SimTuning.defaults();
        SimTuning tuning = new SimTuning(
                defaults.intraTurnPasses(),
                defaults.turn1DeclarePolicy(),
                0.25,
                defaults.activityActThreshold(),
                defaults.policyCooldownTurns(),
                defaults.localSearchBudgetMs(),
                defaults.localSearchMaxIterations(),
                defaults.candidatesPerAttacker(),
                defaults.beigeTurnsOnDefeat(),
                defaults.stateResolutionMode(),
                defaults.stochasticSeed(),
                defaults.stochasticSampleCount()
        );
        Map<Integer, Float> weights = PlannerSimSupport.compileActivityWeights(
                SnapshotActivityProvider.BASELINE.withWartimeUplift(tuning.wartimeActivityUplift()),
                0,
                overrides,
                List.of(wartimeAuto, forcedActive, forcedInactive)
        );

        assertEquals(0.75f, weights.get(1));
        assertEquals(1.0f, weights.get(2));
        assertEquals(0.0f, weights.get(3));
    }

        @Test
        void compileActivityWeightsClampsAutoProviderToUnitInterval() {
                DBNationSnapshot nation = nation(4).build();

                Map<Integer, Float> weights = PlannerSimSupport.compileActivityWeights(
                                (snapshot, turn) -> -0.25,
                                0,
                                OverrideSet.EMPTY,
                                List.of(nation)
                );

                assertEquals(0.0f, weights.get(4));
        }

    private static DBNationSnapshot.Builder nation(int nationId) {
        return DBNationSnapshot.synthetic(nationId)
                .teamId(nationId)
                .allianceId(nationId)
                .score(1_000)
                .cities(5)
                .nonInfraScoreBase(1_000)
                .cityInfra(new double[]{1_000, 1_000, 1_000, 1_000, 1_000})
                .warPolicy(WarPolicy.ATTRITION);
    }
}