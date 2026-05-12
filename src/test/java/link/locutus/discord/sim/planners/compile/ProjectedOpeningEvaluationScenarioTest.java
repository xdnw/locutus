package link.locutus.discord.sim.planners.compile;

import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.sim.planners.DBNationSnapshot;
import link.locutus.discord.sim.planners.OverrideSet;
import link.locutus.discord.sim.planners.TreatyProvider;
import link.locutus.discord.util.PW;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ProjectedOpeningEvaluationScenarioTest {
    private final ScenarioCompiler compiler = new ScenarioCompiler();

    @Test
    void projectedScenarioMatchesOpeningOnlyCompiledScenarioForConsumedBehaviors() {
        DBNationSnapshot attacker = nation(1, 1_000.0)
                .activeOpponentNationId(101)
                .build();
        DBNationSnapshot sameAllianceInRange = nation(101, 950.0).allianceId(77).build();
        DBNationSnapshot sameAllianceOutOfRange = nation(102, 4_000.0).allianceId(77).build();
        DBNationSnapshot otherAlliance = nation(103, 900.0).allianceId(88).build();

        ProjectedOpeningEvaluationScenario projected = ProjectedOpeningEvaluationScenario.create(
                List.of(attacker),
                List.of(sameAllianceInRange, sameAllianceOutOfRange, otherAlliance)
        );
        CompiledScenario compiled = compiler.compileForOpeningEvaluation(
                List.of(attacker),
                List.of(sameAllianceInRange, sameAllianceOutOfRange, otherAlliance),
                OverrideSet.EMPTY,
                TreatyProvider.NONE,
                Map.of()
        );

        assertEquals(compiled.attackerCount(), projected.attackerCount());
        assertEquals(compiled.defenderCount(), projected.defenderCount());

        List<Integer> compiledDefenders = new ArrayList<>();
        compiled.forEachDefenderIndexInRange(0, compiledDefenders::add);
        List<Integer> projectedDefenders = new ArrayList<>();
        projected.forEachDefenderIndexInRange(0, projectedDefenders::add);
        assertEquals(Set.copyOf(compiledDefenders), Set.copyOf(projectedDefenders));

        for (int defenderIndex = 0; defenderIndex < compiled.defenderCount(); defenderIndex++) {
            assertEquals(compiled.hasActivePairConflict(0, defenderIndex), projected.hasActivePairConflict(0, defenderIndex));
            assertFalse(projected.isTreated(0, defenderIndex));
            assertEquals(
                    compiled.estimateAllianceCounterRisk(0, defenderIndex),
                    projected.estimateAllianceCounterRisk(0, defenderIndex),
                    1e-9
            );
        }
    }

    private static DBNationSnapshot.Builder nation(int nationId, double score) {
        int cities = 5;
        double staticScore = PW.computeStaticScoreComponent(cities, 0, 0);
        double infraPerCity = Math.max(0d, ((score - staticScore) * 40.0d) / cities);
        return DBNationSnapshot.synthetic(nationId)
                .teamId(nationId)
                .allianceId(nationId)
                .cities(cities)
                .cityInfra(new double[]{infraPerCity, infraPerCity, infraPerCity, infraPerCity, infraPerCity})
                .warPolicy(WarPolicy.ATTRITION);
    }
}