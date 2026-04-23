package link.locutus.discord.sim.combat;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.SuccessType;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.db.entities.DBCity;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.nation.DBNationData;
import link.locutus.discord.db.entities.nation.SimpleDBNation;
import link.locutus.discord.sim.combat.state.CombatantView;
import link.locutus.discord.util.battle.CombatantViewAdapter;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttackResolverTest {

    /**
     * DBNation minimal enough for {@link AttackType#getCasualties} without a Locutus runtime:
     * stubs {@link DBNation#_getCitiesV3()} to an empty map and seeds {@link WarPolicy#ATTRITION}
     * so the policy-switch branches in {@code infraAttackModifier} don't NPE on a null policy.
     */
    private static DBNation nation(int cities, int soldiers, int tanks, int aircraft, int ships) {
                return nation(cities, soldiers, tanks, aircraft, ships, WarPolicy.ATTRITION, 0L);
        }

        private static DBNation nation(
                        int cities,
                        int soldiers,
                        int tanks,
                        int aircraft,
                        int ships,
                        WarPolicy warPolicy,
                        long warPolicyTimer
        ) {
        DBNationData data = new DBNationData();
                data.setWar_policy(warPolicy);
                data.setWarPolicyTimer(warPolicyTimer);
        SimpleDBNation n = new SimpleDBNation(data) {
            @Override
            public Map<Integer, DBCity> _getCitiesV3() {
                return Collections.emptyMap();
            }

            @Override
            public double maxCityInfra() {
                return 0;
            }
        };
        n.edit().setCities(cities);
        n.edit().setSoldiers(soldiers);
        n.edit().setTanks(tanks);
        n.edit().setAircraft(aircraft);
        n.edit().setShips(ships);
        return n;
    }

        private static DBNation nationWithInfrastructure(int cities, int soldiers, int tanks, int aircraft, int ships, int infrastructure) {
                return nationWithResources(
                                cities,
                                soldiers,
                                tanks,
                                aircraft,
                                ships,
                                infrastructure,
                                0,
                                WarPolicy.ATTRITION,
                                0L
                );
        }

        private static DBNation nationWithResources(
                        int cities,
                        int soldiers,
                        int tanks,
                        int aircraft,
                        int ships,
                        int infrastructure,
                        int money,
                        WarPolicy warPolicy,
                        long warPolicyTimer
        ) {
                DBNationData data = new DBNationData();
                data.setWar_policy(warPolicy);
                data.setWarPolicyTimer(warPolicyTimer);
                SimpleDBNation n = new SimpleDBNation(data) {
                        @Override
                        public Map<Integer, DBCity> _getCitiesV3() {
                                return Collections.emptyMap();
                        }

                        @Override
                        public double maxCityInfra() {
                                return infrastructure;
                        }

                        @Override
                        public int getUnits(MilitaryUnit unit) {
                                if (unit == MilitaryUnit.INFRASTRUCTURE) {
                                        return infrastructure;
                                }
                                if (unit == MilitaryUnit.MONEY) {
                                        return money;
                                }
                                return super.getUnits(unit);
                        }
                };
                n.edit().setCities(cities);
                n.edit().setSoldiers(soldiers);
                n.edit().setTanks(tanks);
                n.edit().setAircraft(aircraft);
                n.edit().setShips(ships);
                return n;
        }

    @Test
    void deterministicEvAttackerLossesEqualProbabilityWeightedMidpoint() {
        DBNation attacker = nation(10, 50_000, 2_000, 600, 30);
        DBNation defender = nation(10, 40_000, 1_800, 500, 25);
        AttackResolver.Flags flags = AttackResolver.Flags.defaults();

        AttackOutcome outcome = resolve(
                attacker, defender, AttackType.GROUND, WarType.RAID,
                flags, ResolutionMode.DETERMINISTIC_EV);

        double[] odds = oddsVector(attacker, defender, AttackType.GROUND, flags);
        double expectedSoldierLoss = 0;
        for (int s = 0; s < SuccessType.values.length; s++) {
            SuccessType success = SuccessType.values[s];
            var casualties = AttackType.GROUND.getCasualties(attacker, defender, success, WarType.RAID,
                    false, false, false, true, true, false);
            Map.Entry<Integer, Integer> range = casualties.getKey().get(MilitaryUnit.SOLDIER);
            if (range == null) continue;
            expectedSoldierLoss += odds[s] * (range.getKey() + range.getValue()) * 0.5;
        }

        assertEquals(expectedSoldierLoss,
                outcome.attackerLossEv(MilitaryUnit.SOLDIER),
                1e-9,
                "deterministic attacker soldier loss should equal weighted midpoint");
    }

    @Test
    void deterministicEvUsesArgmaxSuccessHint() {
        DBNation attacker = nation(5, 10_000, 500, 100, 5);
        DBNation defender = nation(5, 9_000, 450, 90, 4);
        AttackResolver.Flags flags = AttackResolver.Flags.defaults();

        AttackOutcome outcome = resolve(
                attacker, defender, AttackType.GROUND, WarType.RAID,
                flags, ResolutionMode.DETERMINISTIC_EV);

        double[] odds = oddsVector(attacker, defender, AttackType.GROUND, flags);
        int argmax = 0;
        for (int i = 1; i < odds.length; i++) {
            if (odds[i] > odds[argmax]) {
                argmax = i;
            }
        }

        assertEquals(SuccessType.values[argmax], outcome.success(),
                "deterministic EV mode should carry the argmax success as a hint");
    }

    @Test
    void mostLikelyReportsArgmaxSuccess() {
        DBNation attacker = nation(10, 100_000, 5_000, 1_000, 50);
        DBNation defender = nation(10, 10_000, 500, 100, 5);
        AttackResolver.Flags flags = AttackResolver.Flags.defaults();

        AttackOutcome outcome = resolve(
                attacker, defender, AttackType.GROUND, WarType.RAID,
                flags, ResolutionMode.MOST_LIKELY);

        double[] odds = oddsVector(attacker, defender, AttackType.GROUND, flags);
        int argmax = 0;
        for (int i = 1; i < odds.length; i++) if (odds[i] > odds[argmax]) argmax = i;
        assertEquals(SuccessType.values[argmax], outcome.success());
    }

    @Test
    void stochasticSameSeedProducesSameOutcome() {
        DBNation attacker = nation(10, 40_000, 2_000, 500, 25);
        DBNation defender = nation(10, 40_000, 2_000, 500, 25);
        RandomSource rng = RandomSource.splittable(42);
        long streamKey = 12345L;

        AttackOutcome a = resolve(attacker, defender,
                AttackType.GROUND, WarType.RAID,
                AttackResolver.Flags.defaults(), ResolutionMode.STOCHASTIC, rng, streamKey);
        AttackOutcome b = resolve(attacker, defender,
                AttackType.GROUND, WarType.RAID,
                AttackResolver.Flags.defaults(), ResolutionMode.STOCHASTIC, rng, streamKey);

        assertEquals(a, b, "same rng + streamKey must yield identical outcomes");
    }

    @Test
    void stochasticDifferentSeedsDiffer() {
        DBNation attacker = nation(10, 40_000, 2_000, 500, 25);
        DBNation defender = nation(10, 40_000, 2_000, 500, 25);
        AttackResolver.Flags flags = AttackResolver.Flags.defaults();

        AttackOutcome a = resolve(attacker, defender,
                AttackType.GROUND, WarType.RAID, flags,
                ResolutionMode.STOCHASTIC, RandomSource.splittable(1), 100L);
        AttackOutcome b = resolve(attacker, defender,
                AttackType.GROUND, WarType.RAID, flags,
                ResolutionMode.STOCHASTIC, RandomSource.splittable(2), 100L);

        // Distinct seeds should (with overwhelming probability) change at least one sampled loss.
        assertNotEquals(a, b);
    }

    @Test
    void resolveIntoMatchesImmutableMostLikelyAndEv() {
        DBNation attacker = nation(9, 42_000, 1_900, 620, 21);
        DBNation defender = nation(9, 39_000, 1_700, 590, 19);

        CombatKernel.AttackContext context = new ViewAttackContext(
                cv(attacker),
                cv(defender),
                AttackResolver.Flags.defaults().toWarState(WarType.ORD)
        );
        AttackScratch scratch = new AttackScratch();
        MutableAttackResult out = new MutableAttackResult();

        AttackOutcome expectedMostLikely = AttackResolver.resolve(
                cv(attacker),
                cv(defender),
                AttackResolver.Flags.defaults().toWarState(WarType.ORD),
                AttackType.GROUND,
                ResolutionMode.MOST_LIKELY
        );
        AttackResolver.resolveInto(context, AttackType.GROUND, ResolutionMode.MOST_LIKELY, scratch, out);
        assertEquivalent(expectedMostLikely, out);

        AttackOutcome expectedEv = AttackResolver.resolve(
                cv(attacker),
                cv(defender),
                AttackResolver.Flags.defaults().toWarState(WarType.ORD),
                AttackType.GROUND,
                ResolutionMode.DETERMINISTIC_EV
        );
        AttackResolver.resolveInto(context, AttackType.GROUND, ResolutionMode.DETERMINISTIC_EV, scratch, out);
        assertEquivalent(expectedEv, out);
    }

        @Test
        void writeConsumptionMatchesLegacyConsumptionMap() {
                DBNation attacker = nation(8, 30_000, 1_500, 400, 20);
                double[] written = new double[ResourceType.values.length];

                AttackType.GROUND.writeConsumption(cv(attacker)::getUnits, true, written);

                assertArrayEquals(
                                ResourceType.resourcesToArray(AttackType.GROUND.getConsumption(cv(attacker)::getUnits, true)),
                                written,
                                1e-9
                );
        }

    @Test
    void mapCostMatchesAttackType() {
        DBNation attacker = nation(5, 10_000, 500, 100, 5);
        DBNation defender = nation(5, 9_000, 450, 90, 4);

        AttackOutcome ground = resolve(attacker, defender,
                AttackType.GROUND, WarType.RAID, AttackResolver.Flags.defaults(),
                ResolutionMode.DETERMINISTIC_EV);
        AttackOutcome airstrike = resolve(attacker, defender,
                AttackType.AIRSTRIKE_INFRA, WarType.RAID, AttackResolver.Flags.defaults(),
                ResolutionMode.DETERMINISTIC_EV);

        assertEquals(AttackType.GROUND.getMapUsed(), ground.mapCost());
        assertEquals(AttackType.AIRSTRIKE_INFRA.getMapUsed(), airstrike.mapCost());
    }

    @Test
    void resistanceDeltaIsNonPositiveForDefenderOnSuccessfulGround() {
        DBNation attacker = nation(10, 80_000, 4_000, 800, 40);
        DBNation defender = nation(10, 20_000, 1_000, 200, 10);

        AttackOutcome outcome = resolve(attacker, defender,
                AttackType.GROUND, WarType.RAID, AttackResolver.Flags.defaults(),
                ResolutionMode.MOST_LIKELY);

        // Defender's resistance should drop (negative delta) when the attacker is expected to win.
        assertTrue(outcome.defenderResistanceDelta() <= 0,
                "defender resistance delta should be <= 0 for an expected-victorious attack, got "
                        + outcome.defenderResistanceDelta());
    }

    @Test
    void injectedOddsModelOverridesPwOdds() {
        DBNation attacker = nation(10, 50_000, 2_000, 600, 30);
        DBNation defender = nation(10, 40_000, 1_800, 500, 25);
        AttackResolver.Flags flags = AttackResolver.Flags.defaults();

        // OddsModel that pins all probability mass on UTTER_FAILURE (ordinal 0).
        OddsModel pinnedFailure = (att, def, type) -> {
            double[] dist = new double[SuccessType.values.length];
            dist[SuccessType.UTTER_FAILURE.ordinal()] = 1.0;
            return dist;
        };
        // OddsModel that pins all mass on IMMENSE_TRIUMPH (ordinal 3).
        OddsModel pinnedTriumph = (att, def, type) -> {
            double[] dist = new double[SuccessType.values.length];
            dist[SuccessType.IMMENSE_TRIUMPH.ordinal()] = 1.0;
            return dist;
        };

        AttackOutcome failure = resolve(attacker, defender,
                AttackType.GROUND, WarType.RAID, flags, ResolutionMode.MOST_LIKELY,
                null, 0L, pinnedFailure);
        AttackOutcome triumph = resolve(attacker, defender,
                AttackType.GROUND, WarType.RAID, flags, ResolutionMode.MOST_LIKELY,
                null, 0L, pinnedTriumph);

        assertEquals(SuccessType.UTTER_FAILURE, failure.success(),
                "MOST_LIKELY should report the success the injected OddsModel pinned");
        assertEquals(SuccessType.IMMENSE_TRIUMPH, triumph.success());
        // Pinned distinct success levels should produce distinct casualty/resistance outcomes,
        // proving the injected OddsModel reaches combat truth, not just planner-side scoring.
        assertNotEquals(failure, triumph,
                "different injected OddsModel distributions must produce different outcomes");
    }

    @Test
    void defaultOddsModelMatchesExplicitDefault() {
        DBNation attacker = nation(8, 30_000, 1_500, 400, 20);
        DBNation defender = nation(8, 28_000, 1_400, 380, 18);
        AttackResolver.Flags flags = AttackResolver.Flags.defaults();

        AttackOutcome implicit = resolve(attacker, defender,
                AttackType.GROUND, WarType.RAID, flags, ResolutionMode.DETERMINISTIC_EV);
        AttackOutcome explicit = resolve(attacker, defender,
                AttackType.GROUND, WarType.RAID, flags, ResolutionMode.DETERMINISTIC_EV,
                null, 0L, OddsModel.DEFAULT);

        assertEquals(implicit, explicit,
                "OddsModel.DEFAULT must produce the same outcome as the no-model overload");
    }

    @Test
    void unusedLossArraySlotsAreZero() {
        DBNation attacker = nation(5, 10_000, 500, 100, 5);
        DBNation defender = nation(5, 9_000, 450, 90, 4);

        AttackOutcome outcome = resolve(attacker, defender,
                AttackType.GROUND, WarType.RAID, AttackResolver.Flags.defaults(),
                ResolutionMode.DETERMINISTIC_EV);

        assertEquals(0d, outcome.attackerLossEv(MilitaryUnit.SHIP), 0d,
                "GROUND EV outcomes should leave unrelated attacker loss slots at zero");
        assertEquals(0d, outcome.defenderLossEv(MilitaryUnit.SHIP), 0d,
                "GROUND EV outcomes should leave unrelated defender loss slots at zero");
    }

    @Test
    void airstrikeInfraPreservesInfrastructureDamageOnDbNationPath() {
        DBNation attacker = nationWithInfrastructure(8, 25_000, 1_000, 400, 12, 1_600);
        DBNation defender = nationWithInfrastructure(8, 20_000, 900, 350, 10, 1_600);

        AttackOutcome outcome = resolve(attacker, defender,
                AttackType.AIRSTRIKE_INFRA, WarType.RAID, AttackResolver.Flags.defaults(),
                ResolutionMode.MOST_LIKELY);

        assertTrue(outcome.infraDestroyed() > 0d,
                "DBNation AIRSTRIKE_INFRA resolution should surface defender infrastructure damage");
    }

    @Test
    void attackerPolicySwitchPirateVsAttritionChangesLootInExpectedDirection() {
        DBNation attackerAttrition = nationWithResources(
                10, 55_000, 2_100, 700, 30, 1_900, 1_000_000, WarPolicy.ATTRITION, 0L
        );
        DBNation attackerPirate = nationWithResources(
                10, 55_000, 2_100, 700, 30, 1_900, 1_000_000, WarPolicy.PIRATE, 0L
        );
        DBNation defender = nationWithResources(
                10, 45_000, 1_700, 600, 22, 1_900, 6_000_000, WarPolicy.ATTRITION, 0L
        );

        AttackOutcome attrition = resolve(
                attackerAttrition,
                defender,
                AttackType.GROUND,
                WarType.RAID,
                AttackResolver.Flags.defaults(),
                ResolutionMode.DETERMINISTIC_EV
        );
        AttackOutcome pirate = resolve(
                attackerPirate,
                defender,
                AttackType.GROUND,
                WarType.RAID,
                AttackResolver.Flags.defaults(),
                ResolutionMode.DETERMINISTIC_EV
        );

        assertTrue(
                pirate.loot() > attrition.loot(),
                "Pirate policy should increase expected loot vs attrition for the same ground raid"
        );
    }

    @Test
    void defenderPolicySwitchTurtleVsAttritionChangesInfraInExpectedDirection() {
        DBNation attacker = nationWithResources(
                10, 60_000, 2_300, 900, 28, 2_100, 1_000_000, WarPolicy.ATTRITION, 0L
        );
        DBNation defenderAttrition = nationWithResources(
                10, 47_000, 1_900, 650, 23, 2_100, 1_000_000, WarPolicy.ATTRITION, 0L
        );
        DBNation defenderTurtle = nationWithResources(
                10, 47_000, 1_900, 650, 23, 2_100, 1_000_000, WarPolicy.TURTLE, 0L
        );

        AttackOutcome vsAttrition = resolve(
                attacker,
                defenderAttrition,
                AttackType.AIRSTRIKE_INFRA,
                WarType.ORD,
                AttackResolver.Flags.defaults(),
                ResolutionMode.DETERMINISTIC_EV
        );
        AttackOutcome vsTurtle = resolve(
                attacker,
                defenderTurtle,
                AttackType.AIRSTRIKE_INFRA,
                WarType.ORD,
                AttackResolver.Flags.defaults(),
                ResolutionMode.DETERMINISTIC_EV
        );

        assertTrue(
                vsTurtle.infraDestroyed() < vsAttrition.infraDestroyed(),
                "Turtle policy should reduce incoming infra damage vs attrition for identical inputs"
        );
    }

    @Test
    void pinnedSuccessPolicyScenariosMatchDirectCasualtyMidpoints() {
        WarPolicy[] attackerPolicies = {WarPolicy.ATTRITION, WarPolicy.PIRATE};
        WarPolicy[] defenderPolicies = {WarPolicy.ATTRITION, WarPolicy.TURTLE, WarPolicy.MONEYBAGS};

        for (WarPolicy attackerPolicy : attackerPolicies) {
            for (WarPolicy defenderPolicy : defenderPolicies) {
                DBNation attacker = nation(10, 52_000, 2_000, 750, 27, attackerPolicy, 0L);
                DBNation defender = nation(10, 48_000, 1_850, 680, 24, defenderPolicy, 0L);

                for (SuccessType success : SuccessType.values) {
                    OddsModel pinned = (attStr, defStr, type) -> {
                        double[] dist = new double[SuccessType.values.length];
                        dist[success.ordinal()] = 1d;
                        return dist;
                    };

                    AttackOutcome outcome = resolve(
                            attacker,
                            defender,
                            AttackType.GROUND,
                            WarType.RAID,
                            AttackResolver.Flags.defaults(),
                            ResolutionMode.MOST_LIKELY,
                            null,
                            0L,
                            pinned
                    );

                    assertEquals(success, outcome.success(),
                            "pinned odds should force MOST_LIKELY success for " + attackerPolicy + " vs " + defenderPolicy);

                    var casualties = AttackType.GROUND.getCasualties(
                            attacker,
                            defender,
                            success,
                            WarType.RAID,
                            false,
                            false,
                            false,
                            true,
                            true,
                            false
                    );

                    int expectedAttackerSoldier = (int) Math.round(midpoint(casualties.getKey(), MilitaryUnit.SOLDIER));
                    int expectedDefenderSoldier = (int) Math.round(midpoint(casualties.getValue(), MilitaryUnit.SOLDIER));
                    double expectedInfra = midpoint(casualties.getValue(), MilitaryUnit.INFRASTRUCTURE);
                    double expectedLoot = midpoint(casualties.getValue(), MilitaryUnit.MONEY);

                    assertEquals(
                            expectedAttackerSoldier,
                            outcome.attackerLoss(MilitaryUnit.SOLDIER),
                            "attacker soldier midpoint should match for " + attackerPolicy + " vs " + defenderPolicy + " @ " + success
                    );
                    assertEquals(
                            expectedDefenderSoldier,
                            outcome.defenderLoss(MilitaryUnit.SOLDIER),
                            "defender soldier midpoint should match for " + attackerPolicy + " vs " + defenderPolicy + " @ " + success
                    );
                    assertEquals(
                            expectedInfra,
                            outcome.infraDestroyed(),
                            1e-9,
                            "infra midpoint should match for " + attackerPolicy + " vs " + defenderPolicy + " @ " + success
                    );
                    assertEquals(
                            expectedLoot,
                            outcome.loot(),
                            1e-9,
                            "loot midpoint should match for " + attackerPolicy + " vs " + defenderPolicy + " @ " + success
                    );
                }
            }
        }
    }

    private static double midpoint(
            Map<MilitaryUnit, Map.Entry<Integer, Integer>> ranges,
            MilitaryUnit unit
    ) {
        Map.Entry<Integer, Integer> range = ranges.get(unit);
        if (range == null) {
            return 0d;
        }
        return (range.getKey() + range.getValue()) * 0.5d;
    }

        private static void assertEquivalent(AttackOutcome expected, MutableAttackResult actual) {
                assertEquals(expected.success(), actual.success());
                assertEquals(expected.mode(), actual.mode());
                assertEquals(expected.infraDestroyed(), actual.infraDestroyed(), 1e-9);
                assertEquals(expected.loot(), actual.loot(), 1e-9);
                assertEquals(expected.attackerResistanceDelta(), actual.attackerResistanceDelta(), 1e-9);
                assertEquals(expected.defenderResistanceDelta(), actual.defenderResistanceDelta(), 1e-9);
                assertEquals(expected.mapCost(), actual.mapCost());
                assertEquals(expected.controlDelta(), actual.controlDelta());
                if (expected.isExpectedValue()) {
                        assertArrayEquals(expected.attackerLossesEv(), actual.attackerLossesEv(), 1e-9);
                        assertArrayEquals(expected.defenderLossesEv(), actual.defenderLossesEv(), 1e-9);
                } else {
                        assertArrayEquals(expected.attackerLosses(), actual.attackerLosses());
                        assertArrayEquals(expected.defenderLosses(), actual.defenderLosses());
                }
                assertArrayEquals(expected.consumption(), actual.consumption(), 1e-9);
        }

    // --- Bridge helpers: convert DBNation+Flags into the neutral-view API ---

    private static CombatantView cv(DBNation n) {
        return CombatantViewAdapter.of(n);
    }

    private static AttackOutcome resolve(
            DBNation att, DBNation def,
            AttackType type, WarType wt,
            AttackResolver.Flags flags, ResolutionMode mode) {
        return AttackResolver.resolve(cv(att), cv(def), flags.toWarState(wt), type, mode);
    }

    private static AttackOutcome resolve(
            DBNation att, DBNation def,
            AttackType type, WarType wt,
            AttackResolver.Flags flags, ResolutionMode mode,
            RandomSource rng, long streamKey) {
        return AttackResolver.resolve(cv(att), cv(def), flags.toWarState(wt), type, mode, rng, streamKey);
    }

    private static AttackOutcome resolve(
            DBNation att, DBNation def,
            AttackType type, WarType wt,
            AttackResolver.Flags flags, ResolutionMode mode,
            RandomSource rng, long streamKey, OddsModel oddsModel) {
        return AttackResolver.resolve(cv(att), cv(def), flags.toWarState(wt), type, mode,
                flags.toEngagementOptions(), rng, streamKey, oddsModel);
    }

    private static double[] oddsVector(
            DBNation att, DBNation def,
            AttackType type, AttackResolver.Flags flags) {
        // WarType does not affect strength-based odds; ORD is a safe default.
        return AttackResolver.oddsVector(cv(att), cv(def), type, flags.toWarState(WarType.ORD));
    }
}
