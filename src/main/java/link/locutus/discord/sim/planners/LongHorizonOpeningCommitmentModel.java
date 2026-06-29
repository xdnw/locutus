package link.locutus.discord.sim.planners;

import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.sim.combat.UnitEconomy;
import link.locutus.discord.sim.planners.compile.CompiledScenario;

final class LongHorizonOpeningCommitmentModel {
    private LongHorizonOpeningCommitmentModel() {
    }

    static int[] attackerCommitmentNeeds(
            CandidateEdgeTable edges,
            CompiledScenario scenario,
            int[] attackerCaps,
            int[] defenderCaps,
            double[] edgeScores
    ) {
        int attackerCount = scenario.attackerCount();
        int[] positiveEdgeCounts = new int[attackerCount];
        int[] defenderSourceCounts = new int[scenario.defenderCount()];
        for (int edgeIndex = 0; edgeIndex < edges.edgeCount(); edgeIndex++) {
            if (!(edgeScore(edgeScores, edgeIndex) > 0d)) {
                continue;
            }
            positiveEdgeCounts[edges.attackerIndex(edgeIndex)]++;
            defenderSourceCounts[edges.defenderIndex(edgeIndex)]++;
        }

        int[] defenderNeeds = defenderPressureNeeds(scenario, edges, defenderCaps);
        double totalDefenderDemand = 0d;
        for (int defenderIndex = 0; defenderIndex < defenderNeeds.length; defenderIndex++) {
            if (defenderSourceCounts[defenderIndex] <= 0) {
                continue;
            }
            totalDefenderDemand += Math.max(defenderNeeds[defenderIndex], Math.max(0, defenderCaps[defenderIndex]));
        }
        int activeAttackerSources = 0;
        for (int positiveEdgeCount : positiveEdgeCounts) {
            if (positiveEdgeCount > 0) {
                activeAttackerSources++;
            }
        }
        double sharedDemand = activeAttackerSources == 0 ? 0d : totalDefenderDemand / activeAttackerSources;
        double[] scarceAttackerDemand = new double[attackerCount];
        for (int edgeIndex = 0; edgeIndex < edges.edgeCount(); edgeIndex++) {
            if (!(edgeScore(edgeScores, edgeIndex) > 0d)) {
                continue;
            }
            int defenderIndex = edges.defenderIndex(edgeIndex);
            int defenderNeed = Math.max(1, defenderNeeds[defenderIndex]);
            int sourceCount = Math.max(1, defenderSourceCounts[defenderIndex]);
            if (sourceCount <= defenderNeed * 2) {
                scarceAttackerDemand[edges.attackerIndex(edgeIndex)] += defenderNeed / (double) sourceCount;
            }
        }

        int[] needs = new int[attackerCount];
        for (int attackerIndex = 0; attackerIndex < attackerCount; attackerIndex++) {
            int usefulCapacity = Math.min(attackerCaps[attackerIndex], positiveEdgeCounts[attackerIndex]);
            if (usefulCapacity <= 0) {
                continue;
            }
            double demand = sharedDemand + scarceAttackerDemand[attackerIndex];
            needs[attackerIndex] = Math.max(1, Math.min(usefulCapacity, (int) Math.ceil(demand)));
        }
        return needs;
    }

    static int[] defenderPressureNeeds(
            CompiledScenario scenario,
            CandidateEdgeTable edges,
            int[] defenderCaps
    ) {
        double[] strongestCandidateAttacker = new double[scenario.defenderCount()];
        for (int edgeIndex = 0; edgeIndex < edges.edgeCount(); edgeIndex++) {
            int attackerIndex = edges.attackerIndex(edgeIndex);
            int defenderIndex = edges.defenderIndex(edgeIndex);
            strongestCandidateAttacker[defenderIndex] = Math.max(
                    strongestCandidateAttacker[defenderIndex],
                    combatStrength(scenario.attacker(attackerIndex))
            );
        }

        int[] pressureNeeds = new int[scenario.defenderCount()];
        for (int defenderIndex = 0; defenderIndex < pressureNeeds.length; defenderIndex++) {
            int defenderCap = Math.max(1, defenderCaps[defenderIndex]);
            double attackerStrength = strongestCandidateAttacker[defenderIndex];
            if (!(attackerStrength > 0d) || defenderCap <= 1) {
                pressureNeeds[defenderIndex] = 1;
                continue;
            }
            double strengthRatio = combatStrength(scenario.defender(defenderIndex)) / attackerStrength;
            int strengthNeed;
            if (strengthRatio >= 2.25d) {
                strengthNeed = 3;
            } else if (strengthRatio >= 1.15d) {
                strengthNeed = 2;
            } else {
                strengthNeed = 1;
            }
            double strategicPressure = Math.max(0d, Math.min(1d, (scenario.defender(defenderIndex).cities() - 35d) / 10d));
            int strategicNeed = 1 + (int) Math.ceil(strategicPressure * Math.max(0, defenderCap - 1));
            pressureNeeds[defenderIndex] = Math.max(1, Math.min(defenderCap, Math.max(strengthNeed, strategicNeed)));
        }
        return pressureNeeds;
    }

    private static double edgeScore(double[] edgeScores, int edgeIndex) {
        return edgeScores != null && edgeIndex >= 0 && edgeIndex < edgeScores.length
                ? edgeScores[edgeIndex]
                : 0d;
    }

    private static double combatStrength(DBNationSnapshot snapshot) {
        double groundStrength = UnitEconomy.groundStrengthRaw(
                snapshot.unit(MilitaryUnit.SOLDIER),
                snapshot.unit(MilitaryUnit.TANK),
                false,
                false
        );
        return groundStrength
                + (3d * snapshot.unit(MilitaryUnit.AIRCRAFT))
                + (2d * snapshot.unit(MilitaryUnit.SHIP));
    }
}
