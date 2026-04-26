package link.locutus.discord.sim.combat;

import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.util.PW;

public final class WarOutcomeMath {
    private static final double VICTORY_LOOT_BASE_PERCENT = 0.10;
    private static final double VICTORY_INFRA_BASE_PERCENT = 0.04;

    private WarOutcomeMath() {
    }

    public static double combinedLootModifier(
            double attackerLooterModifier,
            double defenderLootModifier,
            WarType warType,
            boolean attackerIsOriginalAttacker
    ) {
        return (1 + (attackerLooterModifier - 1) + (defenderLootModifier - 1))
                * WarRoleModifiers.lootModifier(warType, attackerIsOriginalAttacker);
    }

    public static double victoryNationLootPercent(double combinedLootModifier) {
        return VICTORY_LOOT_BASE_PERCENT * combinedLootModifier;
    }

    public static double combinedVictoryInfraModifier(
            double attackerInfraAttackModifier,
            double defenderInfraDefendModifier,
            WarType warType,
            boolean attackerIsOriginalAttacker
    ) {
        return (1 + (attackerInfraAttackModifier - 1) + (defenderInfraDefendModifier - 1))
                * WarRoleModifiers.infraModifier(warType, attackerIsOriginalAttacker);
    }

    public static double victoryInfraPercent(double combinedInfraModifier) {
        return VICTORY_INFRA_BASE_PERCENT * combinedInfraModifier;
    }

    public static double victoryInfraPercent(
            double attackerInfraAttackModifier,
            double defenderInfraDefendModifier,
            WarType warType,
            boolean attackerIsOriginalAttacker
    ) {
        return victoryInfraPercent(
                combinedVictoryInfraModifier(
                        attackerInfraAttackModifier,
                        defenderInfraDefendModifier,
                        warType,
                        attackerIsOriginalAttacker
                )
        );
    }

    public static double victoryNationLootPercent(
            double attackerLooterModifier,
            double defenderLootModifier,
            WarType warType,
            boolean attackerIsOriginalAttacker
    ) {
        return victoryNationLootPercent(
                combinedLootModifier(attackerLooterModifier, defenderLootModifier, warType, attackerIsOriginalAttacker)
        );
    }

    public static double victoryNationLootTransferAmount(
            double loserMoney,
            double attackerLooterModifier,
            double defenderLootModifier,
            WarType warType,
            boolean attackerIsOriginalAttacker
    ) {
        if (!Double.isFinite(loserMoney) || loserMoney <= 0d) {
            return 0d;
        }
        double lootPercent = victoryNationLootPercent(
                attackerLooterModifier,
                defenderLootModifier,
                warType,
                attackerIsOriginalAttacker
        );
        if (!Double.isFinite(lootPercent) || lootPercent <= 0d) {
            return 0d;
        }
        return loserMoney * lootPercent;
    }

    public static int victoryInfraPercentMilli(double infraDestroyedPercent) {
        if (!Double.isFinite(infraDestroyedPercent) || infraDestroyedPercent <= 0d) {
            return 0;
        }
        if (infraDestroyedPercent >= 1d) {
            return 1_000;
        }
        return (int) Math.round(infraDestroyedPercent * 1_000d);
    }

    public static int victoryInfraAfterCents(int beforeCents, int infraPercentMilli) {
        if (beforeCents <= 0) {
            return 0;
        }
        if (infraPercentMilli <= 0) {
            return beforeCents;
        }
        if (infraPercentMilli >= 1_000) {
            return 0;
        }
        double remainingFactor = 1d - infraPercentMilli * 0.001d;
        return Math.max(0, (int) Math.round(beforeCents * remainingFactor));
    }

    public static VictoryInfraTotals victoryInfraTotals(Iterable<? extends Number> cityInfraBeforeCents, int infraPercentMilli) {
        if (cityInfraBeforeCents == null || infraPercentMilli <= 0) {
            return VictoryInfraTotals.ZERO;
        }

        int infraDestroyedCents = 0;
        long infraDestroyedValueCents = 0;
        for (Number value : cityInfraBeforeCents) {
            if (value == null) {
                continue;
            }
            int beforeCents = Math.max(0, value.intValue());
            if (beforeCents == 0) {
                continue;
            }
            int afterCents = victoryInfraAfterCents(beforeCents, infraPercentMilli);
            if (afterCents >= beforeCents) {
                continue;
            }
            infraDestroyedCents += beforeCents - afterCents;
            double infraValue = PW.City.Infra.calculateInfra(afterCents * 0.01d, beforeCents * 0.01d);
            infraDestroyedValueCents += Math.round(infraValue * 100d);
        }
        return new VictoryInfraTotals(infraDestroyedCents, infraDestroyedValueCents);
    }

    public static VictoryInfraTotals victoryInfraTotals(double[] cityInfraBefore, double infraDestroyedPercent) {
        if (cityInfraBefore == null || cityInfraBefore.length == 0) {
            return VictoryInfraTotals.ZERO;
        }
        int infraPercentMilli = victoryInfraPercentMilli(infraDestroyedPercent);
        if (infraPercentMilli <= 0) {
            return VictoryInfraTotals.ZERO;
        }

        int infraDestroyedCents = 0;
        long infraDestroyedValueCents = 0;
        for (double before : cityInfraBefore) {
            if (!Double.isFinite(before) || before <= 0d) {
                continue;
            }
            int beforeCents = Math.max(0, (int) Math.round(before * 100d));
            int afterCents = victoryInfraAfterCents(beforeCents, infraPercentMilli);
            if (afterCents >= beforeCents) {
                continue;
            }
            infraDestroyedCents += beforeCents - afterCents;
            double infraValue = PW.City.Infra.calculateInfra(afterCents * 0.01d, beforeCents * 0.01d);
            infraDestroyedValueCents += Math.round(infraValue * 100d);
        }
        return new VictoryInfraTotals(infraDestroyedCents, infraDestroyedValueCents);
    }

    public static double allianceLootPercent(double attackerScore, double allianceScore, double randomFactor) {
        if (attackerScore <= 0 || allianceScore <= 0) {
            return 0;
        }
        double clampedRandom = Math.max(0.0, Math.min(1.0, randomFactor));
        double ratio = ((attackerScore * 10000) / allianceScore) * clampedRandom;
        return Math.min(Math.min(ratio, 10000) / 30000, 0.33);
    }

    public static double expectedAllianceLootPercent(double attackerScore, double allianceScore) {
        return allianceLootPercent(attackerScore, allianceScore, 0.5);
    }

    public record VictoryInfraTotals(int infraDestroyedCents, long infraDestroyedValueCents) {
        static final VictoryInfraTotals ZERO = new VictoryInfraTotals(0, 0L);

        public double infraDestroyed() {
            return infraDestroyedCents * 0.01d;
        }

        public double infraDestroyedValue() {
            return infraDestroyedValueCents * 0.01d;
        }
    }
}