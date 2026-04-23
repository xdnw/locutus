package link.locutus.discord.sim;

import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.db.entities.WarStatus;
import link.locutus.discord.sim.combat.SpecialistCityProfile;
import link.locutus.discord.sim.combat.WarOutcomeMath;
import link.locutus.discord.sim.input.NationInit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefeatHandlingTest {

    @Test
    void defenderDefeatTransitionsToAttackerVictoryAndAppliesBeigeAndLootHooks() {
        EconomyProvider economyProvider = new EconomyProvider() {
            @Override
            public void onVictoryLootTransferred(SimNation winner, SimNation loser, SimWar war, double transferredMoney) {
                double movedSteel = loser.subtractResource(ResourceType.STEEL, 200d);
                winner.addResource(ResourceType.STEEL, movedSteel);
            }

            @Override
            public void onControlFlagChange(SimWar war) {
                // No-op for this test
            }
        };

        final int[] allianceLootCalls = {0};
        AllianceLootProvider allianceLootProvider = (winner, loser, war) -> allianceLootCalls[0]++;

        SimWorld world = new SimWorld(new SimTuning(60, 6, 24), economyProvider, allianceLootProvider);
        SimNation attacker = new SimNation(1, WarPolicy.FORTRESS, 100d);
        double[] defenderResources = ResourceType.getBuffer();
        defenderResources[ResourceType.MONEY.ordinal()] = 500d;
        SimNation defender = new SimNation(2, WarPolicy.TURTLE, defenderResources, 0d, new double[]{1_000d, 800d}, 5);
        defender.addResource(ResourceType.STEEL, 500d);
        SimWar war = new SimWar(201, 1, 2, WarType.ORD);

        world.addNation(attacker);
        world.addNation(defender);
        world.addWar(war);

        war.reduceResistance(2, SimWar.INITIAL_RESISTANCE);
        world.stepTurnStart();

        assertEquals(WarStatus.ATTACKER_VICTORY, war.status());
        assertEquals(24, defender.beigeTurns());
        assertEquals(470d, defender.resource(ResourceType.MONEY));
        assertEquals(130d, attacker.resource(ResourceType.MONEY));
        assertEquals(300d, defender.resource(ResourceType.STEEL));
        assertEquals(200d, attacker.resource(ResourceType.STEEL));
        assertEquals(980d, defender.cityInfra()[0], 1e-9);
        assertEquals(784d, defender.cityInfra()[1], 1e-9);
        assertEquals(1, allianceLootCalls[0]);
    }

    @Test
    void attackerDefeatTransitionsToDefenderVictory() {
        SimWorld world = new SimWorld(new SimTuning(60, 6, 24));
        SimNation attacker = new SimNation(10, WarPolicy.FORTRESS, 300d);
        SimNation defender = new SimNation(20, WarPolicy.TURTLE, 50d);
        SimWar war = new SimWar(202, 10, 20, WarType.ORD);

        world.addNation(attacker);
        world.addNation(defender);
        world.addWar(war);

        war.reduceResistance(10, SimWar.INITIAL_RESISTANCE);
        world.stepTurnStart();

        assertEquals(WarStatus.DEFENDER_VICTORY, war.status());
        assertEquals(24, attacker.beigeTurns());
        assertThrows(IllegalStateException.class, () -> war.reduceResistance(20, 1));
    }

    @Test
    void defaultLiveVictoryLootUsesStoredCombatProfileInsteadOfProviderOwnership() {
        final double[] callbackTransferred = {Double.NaN};
        EconomyProvider economyProvider = new EconomyProvider() {
            @Override
            public void onVictoryLootTransferred(SimNation winner, SimNation loser, SimWar war, double transferredMoney) {
                callbackTransferred[0] = transferredMoney;
            }

            @Override
            public void onControlFlagChange(SimWar war) {
            }
        };

        SimWorld world = new SimWorld(new SimTuning(60, 6, 24), economyProvider, AllianceLootProvider.NO_OP);
        long pirateProjectBits = (1L << Projects.ADVANCED_PIRATE_ECONOMY.ordinal())
                | (1L << Projects.PIRATE_ECONOMY.ordinal());
        SimNation attacker = new SimNation(new NationInit(
                11,
                11,
                WarPolicy.PIRATE,
                ResourceType.getBuffer(),
                0d,
                new double[0],
                5,
                (byte) 0,
                pirateProjectBits,
                new SpecialistCityProfile[0]
        ));
        double[] defenderResources = ResourceType.getBuffer();
        defenderResources[ResourceType.MONEY.ordinal()] = 1_000d;
        SimNation defender = new SimNation(new NationInit(
                22,
                22,
                WarPolicy.MONEYBAGS,
                defenderResources,
                0d,
                new double[0],
                5,
                (byte) 0,
                0L,
                new SpecialistCityProfile[0]
        ));
        SimWar war = new SimWar(203, 11, 22, WarType.ATT);

        world.addNation(attacker);
        world.addNation(defender);
        world.addWar(war);

        war.reduceResistance(22, SimWar.INITIAL_RESISTANCE);
        world.stepTurnStart();

        double expectedTransferred = WarOutcomeMath.victoryNationLootTransferAmount(
                1_000d,
                attacker.looterModifier(false),
                defender.lootModifier(),
                WarType.ATT,
                true
        );
        assertEquals(expectedTransferred, callbackTransferred[0], 1e-9);
        assertEquals(1_000d - expectedTransferred, defender.resource(ResourceType.MONEY), 1e-9);
        assertEquals(expectedTransferred, attacker.resource(ResourceType.MONEY), 1e-9);
    }
}
