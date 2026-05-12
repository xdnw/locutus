package link.locutus.discord.sim.planners.compile;

import link.locutus.discord.sim.planners.DBNationSnapshot;

public interface OpeningEvaluationScenario {
    @FunctionalInterface
    interface DefenderIndexVisitor {
        void accept(int defenderIndex);
    }

    int attackerCount();

    int defenderCount();

    DBNationSnapshot attacker(int attackerIndex);

    DBNationSnapshot defender(int defenderIndex);

    void forEachDefenderIndexInRange(int attackerIndex, DefenderIndexVisitor visitor);

    boolean isTreated(int attackerIndex, int defenderIndex);

    boolean hasActivePairConflict(int attackerIndex, int defenderIndex);

    double estimateAllianceCounterRisk(int attackerIndex, int defenderIndex);
}