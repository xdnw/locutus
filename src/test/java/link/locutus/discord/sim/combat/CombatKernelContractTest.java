package link.locutus.discord.sim.combat;

import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.SuccessType;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.db.entities.DBCity;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.nation.DBNationData;
import link.locutus.discord.db.entities.nation.SimpleDBNation;
import link.locutus.discord.sim.SimNation;
import link.locutus.discord.sim.SimSide;
import link.locutus.discord.sim.SimWar;
import link.locutus.discord.sim.combat.state.CombatantView;
import link.locutus.discord.util.battle.CombatantViewAdapter;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatKernelContractTest {

    @Test
    void deterministicEvWritesExpectedLossesOnly() {
        DBNation attacker = nation(10, 55_000, 2_100, 610, 35);
        DBNation defender = nation(10, 43_000, 1_750, 560, 27);

        CombatKernel.AttackContext context = new ViewAttackContext(
                cv(attacker),
                cv(defender),
                AttackResolver.Flags.defaults().toWarState(WarType.ORD)
        );

        AttackScratch scratch = new AttackScratch();
        MutableAttackResult out = new MutableAttackResult();
        CombatKernel.resolveInto(context, AttackType.GROUND, ResolutionMode.DETERMINISTIC_EV, scratch, out);

        assertEquals(ResolutionMode.DETERMINISTIC_EV, out.mode());
        assertAllZero(out.attackerLosses());
        assertAllZero(out.defenderLosses());
        assertHasPositiveComponent(out.attackerLossesEv());
        assertHasPositiveComponent(out.defenderLossesEv());
    }

    @Test
    void mostLikelyWritesDiscreteLossesOnly() {
        DBNation attacker = nation(10, 55_000, 2_100, 610, 35);
        DBNation defender = nation(10, 43_000, 1_750, 560, 27);

        CombatKernel.AttackContext context = new ViewAttackContext(
                cv(attacker),
                cv(defender),
                AttackResolver.Flags.defaults().toWarState(WarType.ORD)
        );

        AttackScratch scratch = new AttackScratch();
        MutableAttackResult out = new MutableAttackResult();
        CombatKernel.resolveInto(context, AttackType.GROUND, ResolutionMode.MOST_LIKELY, scratch, out);

        assertEquals(ResolutionMode.MOST_LIKELY, out.mode());
        assertAllZero(out.attackerLossesEv());
        assertAllZero(out.defenderLossesEv());
    }

    @Test
    void primitiveCityAccessSupportsCityBasedCasualties() {
        PrimitiveNationState attacker = new PrimitiveNationState(
            1,
            new int[MilitaryUnit.values.length],
            new double[]{1_300d, 1_050d},
            ranges(Map.entry(120, 180), Map.entry(90, 140)),
            ranges(Map.entry(320, 500), Map.entry(250, 420))
        );
        PrimitiveNationState defender = new PrimitiveNationState(
            2,
            new int[MilitaryUnit.values.length],
            new double[]{1_500d, 1_150d},
            ranges(Map.entry(140, 210), Map.entry(100, 160)),
            ranges(Map.entry(360, 560), Map.entry(280, 450))
        );

        Map.Entry<Map<MilitaryUnit, Map.Entry<Integer, Integer>>, Map<MilitaryUnit, Map.Entry<Integer, Integer>>> missileCasualties =
                AttackType.MISSILE.getCasualties(
                        attacker,
                        defender,
                        SuccessType.IMMENSE_TRIUMPH,
                        WarType.ORD,
                        false,
                        false,
                        false,
                        true,
                        true,
                        false
                );

        Map.Entry<Integer, Integer> infraRange = missileCasualties.getValue().get(MilitaryUnit.INFRASTRUCTURE);
        assertEquals(Map.entry(70, 105), infraRange);
        assertTrue(defender.maxCityInfra() >= 1_500d);
    }

        @Test
        void specialistAdmissionSignalRequiresProjectAndUsesSharedCityDamage() {
        int[] attackerUnits = new int[MilitaryUnit.values.length];
        attackerUnits[MilitaryUnit.MISSILE.ordinal()] = 1;
        PrimitiveNationState attacker = new PrimitiveNationState(
            1,
            attackerUnits,
            new double[]{1_200d},
            ranges(Map.entry(0, 0)),
            ranges(Map.entry(0, 0)),
            0,
            Projects.MISSILE_LAUNCH_PAD
        );
        PrimitiveNationState defender = new PrimitiveNationState(
            2,
            new int[MilitaryUnit.values.length],
            new double[]{1_500d, 1_000d},
            ranges(Map.entry(300, 450), Map.entry(80, 120)),
            ranges(Map.entry(0, 0), Map.entry(0, 0))
        );
        TestWarBuffer warBuffer = new TestWarBuffer(WarType.ORD, false, false, false, false, 6, 6, 100, 100, CombatKernel.AttackContext.BLOCKADE_NONE);
        SimpleAttackContext context = new SimpleAttackContext(attacker, defender, warBuffer);

        double signal = CombatKernel.specialistAdmissionSignal(context, AttackType.MISSILE, new AttackScratch(), new MutableAttackResult());
        double blocked = CombatKernel.specialistAdmissionSignal(
            new SimpleAttackContext(
                new PrimitiveNationState(3, attackerUnits, new double[]{1_200d}, ranges(Map.entry(0, 0)), ranges(Map.entry(0, 0))),
                defender,
                warBuffer
            ),
            AttackType.MISSILE,
            new AttackScratch(),
            new MutableAttackResult()
        );

        assertTrue(signal > 0.0d);
        assertEquals(0.125d, signal, 1e-12);
        assertEquals(0.0d, blocked, 1e-12);
        assertTrue(CombatKernel.isLegalSpecialistAttack(attacker, AttackType.MISSILE));
        assertFalse(CombatKernel.isLegalSpecialistAttack(attacker, AttackType.GROUND));
        }

        @Test
        void projectileDefenseOwnerAppliesSharedInterceptionAndProtection() {
        int[] attackerUnits = new int[MilitaryUnit.values.length];
        attackerUnits[MilitaryUnit.MISSILE.ordinal()] = 1;
        PrimitiveNationState attacker = new PrimitiveNationState(
            4,
            attackerUnits,
            new double[]{1_200d},
            ranges(Map.entry(0, 0)),
            ranges(Map.entry(0, 0)),
            0,
            Projects.GUIDING_SATELLITE
        );
        PrimitiveNationState defender = new PrimitiveNationState(
            5,
            new int[MilitaryUnit.values.length],
            new double[]{1_500d},
            ranges(Map.entry(300, 450)),
            ranges(Map.entry(0, 0)),
            0,
            Projects.IRON_DOME
        );
        TestWarBuffer warBuffer = new TestWarBuffer(WarType.ORD, false, false, false, false, 6, 6, 100, 100, CombatKernel.AttackContext.BLOCKADE_NONE);
        SimpleAttackContext context = new SimpleAttackContext(attacker, defender, warBuffer);

        double[] odds = AttackResolver.oddsVector(
            context,
            AttackType.MISSILE,
            AttackResolver.EngagementOptions.defaults(),
            OddsModel.DEFAULT
        );
        AttackOutcome outcome = CombatKernel.resolve(context, AttackType.MISSILE, ResolutionMode.DETERMINISTIC_EV);

        assertEquals(0.30d, ProjectileDefenseMath.interceptionChance(AttackType.MISSILE, defender), 1e-12);
        assertEquals(1, ProjectileDefenseMath.preventedImprovementLosses(AttackType.MISSILE, attacker, defender));
        assertEquals(1, ProjectileDefenseMath.improvementLossesOnHit(AttackType.MISSILE, attacker, defender));
        assertEquals(0.30d, odds[SuccessType.UTTER_FAILURE.ordinal()], 1e-12);
        assertEquals(0.70d, odds[SuccessType.IMMENSE_TRIUMPH.ordinal()], 1e-12);
        assertEquals(131.25d, outcome.infraDestroyed(), 1e-12);
        }

    @Test
    void bufferBackedContextMatchesAccessorOdds() {
        int unitStride = MilitaryUnit.values.length;
        int[] unitsFlat = new int[unitStride * 2];
        unitsFlat[MilitaryUnit.SOLDIER.ordinal()] = 50_000;
        unitsFlat[MilitaryUnit.TANK.ordinal()] = 2_000;
        unitsFlat[MilitaryUnit.AIRCRAFT.ordinal()] = 600;
        unitsFlat[unitStride + MilitaryUnit.SOLDIER.ordinal()] = 40_000;
        unitsFlat[unitStride + MilitaryUnit.TANK.ordinal()] = 1_800;
        unitsFlat[unitStride + MilitaryUnit.AIRCRAFT.ordinal()] = 500;

        double[] cityInfraFlat = new double[]{1_300d, 1_050d, 1_500d, 1_150d};
        int[] cityOffsets = new int[]{0, 2};
        int[] cityCounts = new int[]{2, 2};
        int[] unitOffsets = new int[]{0, unitStride};

        TestNationBuffer nationBuffer = new TestNationBuffer(unitsFlat, unitOffsets, cityInfraFlat, cityOffsets, cityCounts);
        BufferNationState attacker = new BufferNationState(1, nationBuffer, 0);
        BufferNationState defender = new BufferNationState(2, nationBuffer, 1);

        PrimitiveNationState accessorAttacker = new PrimitiveNationState(
            1,
            unitsFlat,
            new double[]{1_300d, 1_050d},
            ranges(Map.entry(120, 180), Map.entry(90, 140)),
            ranges(Map.entry(320, 500), Map.entry(250, 420)),
            0
        );
        PrimitiveNationState accessorDefender = new PrimitiveNationState(
            2,
            unitsFlat,
            new double[]{1_500d, 1_150d},
            ranges(Map.entry(140, 210), Map.entry(100, 160)),
            ranges(Map.entry(360, 560), Map.entry(280, 450)),
            unitStride
        );

        TestWarBuffer warBuffer = new TestWarBuffer(WarType.ORD, false, false, false, false, 6, 6, 100, 100, CombatKernel.AttackContext.BLOCKADE_NONE);
        CombatKernel.AttackContext bufferContext = new BufferAttackContext(attacker, defender, warBuffer);
        CombatKernel.AttackContext accessorContext = new SimpleAttackContext(accessorAttacker, accessorDefender, warBuffer);

        double[] expected = AttackResolver.oddsVector(
            accessorContext,
            AttackType.GROUND,
            AttackResolver.EngagementOptions.defaults(),
            OddsModel.DEFAULT
        );
        double[] actual = AttackResolver.oddsVector(
            bufferContext,
            AttackType.GROUND,
            AttackResolver.EngagementOptions.defaults(),
            OddsModel.DEFAULT
        );
        assertArrayEquals(expected, actual, 1e-12);
    }

    @Test
    void simNationDirectKernelStateMatchesSnapshotViewOdds() {
        SimNation attacker = simNation(1, 50_000, 2_000, 600, 30, 1_350d, 1_100d);
        SimNation defender = simNation(2, 40_000, 1_800, 500, 25, 1_500d, 1_250d);
        defender.addResource(ResourceType.MONEY, 1_000_000d);
        TestWarBuffer warBuffer = new TestWarBuffer(
                WarType.ORD,
                false,
                false,
                false,
                false,
                6,
                6,
                100,
                100,
                CombatKernel.AttackContext.BLOCKADE_NONE
        );

        CombatKernel.AttackContext directContext = new SimpleAttackContext(attacker, defender, warBuffer);
        CombatKernel.AttackContext snapshotContext = new ViewAttackContext(
                attacker.asCombatantView(),
                defender.asCombatantView(),
                AttackResolver.Flags.defaults().toWarState(WarType.ORD)
        );

        double[] expected = AttackResolver.oddsVector(
                snapshotContext,
                AttackType.GROUND,
                AttackResolver.EngagementOptions.defaults(),
                OddsModel.DEFAULT
        );
        double[] actual = AttackResolver.oddsVector(
                directContext,
                AttackType.GROUND,
                AttackResolver.EngagementOptions.defaults(),
                OddsModel.DEFAULT
        );

        assertArrayEquals(expected, actual, 1e-12);

        AttackOutcome expectedMoneyStrike = CombatKernel.resolve(snapshotContext, AttackType.AIRSTRIKE_MONEY, ResolutionMode.DETERMINISTIC_EV);
        AttackOutcome actualMoneyStrike = CombatKernel.resolve(directContext, AttackType.AIRSTRIKE_MONEY, ResolutionMode.DETERMINISTIC_EV);
        assertEquals(expectedMoneyStrike.loot(), actualMoneyStrike.loot(), 1e-12);
    }

    @Test
    void liveAttackContextMatchesSnapshotWarViewForBothPerspectives() {
        SimNation warAttacker = simNation(11, 48_000, 1_900, 650, 26, 1_400d, 1_175d);
        SimNation warDefender = simNation(12, 44_000, 1_700, 580, 28, 1_520d, 1_210d);
        SimWar war = new SimWar(7, warAttacker.nationId(), warDefender.nationId(), WarType.ATT);
        war.reserveMaps(warAttacker.nationId(), 2);
        war.reserveMaps(warDefender.nationId(), 1);
        war.applyAttack(warAttacker.nationId(), AttackType.FORTIFY);
        war.applyAttack(warDefender.nationId(), AttackType.FORTIFY);
        war.applyControlFlagChanges(warAttacker.nationId(), 1, -1, 1);

        assertLiveParity(warAttacker, warDefender, war, SimSide.ATTACKER, AttackType.GROUND);
        assertLiveParity(warDefender, warAttacker, war, SimSide.DEFENDER, AttackType.AIRSTRIKE_AIRCRAFT);
    }

    private static void assertAllZero(int[] values) {
        for (int value : values) {
            assertEquals(0, value);
        }
    }

    private static void assertAllZero(double[] values) {
        for (double value : values) {
            assertEquals(0.0d, value, 1e-12);
        }
    }

    private static void assertHasPositiveComponent(double[] values) {
        for (double value : values) {
            if (value > 0d) {
                return;
            }
        }
        throw new AssertionError("Expected at least one positive EV loss component");
    }

    private static CombatantView cv(DBNation nation) {
        return CombatantViewAdapter.of(nation);
    }

        private static void assertLiveParity(
            SimNation actingNation,
            SimNation opposingNation,
            SimWar war,
            SimSide actorSide,
            AttackType attackType
        ) {
        CombatKernel.AttackContext liveContext = new LiveAttackContext().bind(actingNation, opposingNation, war, actorSide);
        CombatKernel.AttackContext snapshotContext = new ViewAttackContext(
            actingNation.asCombatantView(),
            opposingNation.asCombatantView(),
            war.asWarStateViewFor(actorSide)
        );

        double[] expectedOdds = AttackResolver.oddsVector(
            snapshotContext,
            attackType,
            AttackResolver.EngagementOptions.defaults(),
            OddsModel.DEFAULT
        );
        double[] actualOdds = AttackResolver.oddsVector(
            liveContext,
            attackType,
            AttackResolver.EngagementOptions.defaults(),
            OddsModel.DEFAULT
        );
        assertArrayEquals(expectedOdds, actualOdds, 1e-12);

        AttackOutcome expected = CombatKernel.resolve(snapshotContext, attackType, ResolutionMode.DETERMINISTIC_EV);
        AttackOutcome actual = CombatKernel.resolve(liveContext, attackType, ResolutionMode.DETERMINISTIC_EV);
        assertEquals(expected.controlDelta(), actual.controlDelta());
        assertEquals(expected.mapCost(), actual.mapCost());
        assertEquals(expected.attackerResistanceDelta(), actual.attackerResistanceDelta(), 1e-12);
        assertEquals(expected.defenderResistanceDelta(), actual.defenderResistanceDelta(), 1e-12);
        assertEquals(expected.infraDestroyed(), actual.infraDestroyed(), 1e-12);
        }

    private static SimNation simNation(
            int nationId,
            int soldiers,
            int tanks,
            int aircraft,
            int ships,
            double... cityInfra
    ) {
        SimNation nation = new SimNation(nationId, WarPolicy.ATTRITION, new double[ResourceType.values.length], 0d, cityInfra, 5);
        for (MilitaryUnit unit : new MilitaryUnit[]{
                MilitaryUnit.SOLDIER,
                MilitaryUnit.TANK,
                MilitaryUnit.AIRCRAFT,
                MilitaryUnit.SHIP,
                MilitaryUnit.MISSILE,
                MilitaryUnit.NUKE
        }) {
            nation.setUnitCap(unit, Integer.MAX_VALUE);
            nation.setDailyBuyCap(unit, Integer.MAX_VALUE);
        }
        nation.setUnitCount(MilitaryUnit.SOLDIER, soldiers);
        nation.setUnitCount(MilitaryUnit.TANK, tanks);
        nation.setUnitCount(MilitaryUnit.AIRCRAFT, aircraft);
        nation.setUnitCount(MilitaryUnit.SHIP, ships);
        return nation;
    }

    @SafeVarargs
    private static Map.Entry<Integer, Integer>[] ranges(Map.Entry<Integer, Integer>... values) {
        return values;
    }

    private static final class PrimitiveNationState implements CombatKernel.PrimitiveCityAccess {
        private final int nationId;
        private final int[] units;
        private final double[] cityInfra;
        private final Map.Entry<Integer, Integer>[] missileDamage;
        private final Map.Entry<Integer, Integer>[] nukeDamage;
        private final int unitBaseOffset;
        private final long projectBits;

        private PrimitiveNationState(
                int nationId,
                int[] units,
                double[] cityInfra,
                Map.Entry<Integer, Integer>[] missileDamage,
                Map.Entry<Integer, Integer>[] nukeDamage
        ) {
            this(nationId, units, cityInfra, missileDamage, nukeDamage, 0, new Project[0]);
        }

        private PrimitiveNationState(
                int nationId,
                int[] units,
                double[] cityInfra,
                Map.Entry<Integer, Integer>[] missileDamage,
                Map.Entry<Integer, Integer>[] nukeDamage,
                int unitBaseOffset
        ) {
            this(nationId, units, cityInfra, missileDamage, nukeDamage, unitBaseOffset, new Project[0]);
        }

        private PrimitiveNationState(
                int nationId,
                int[] units,
                double[] cityInfra,
                Map.Entry<Integer, Integer>[] missileDamage,
                Map.Entry<Integer, Integer>[] nukeDamage,
                int unitBaseOffset,
                Project... projects
        ) {
            this.nationId = nationId;
            this.units = units;
            this.cityInfra = cityInfra;
            this.missileDamage = missileDamage;
            this.nukeDamage = nukeDamage;
            this.unitBaseOffset = unitBaseOffset;
            this.projectBits = projectBits(projects);
        }

        @Override
        public int nationId() {
            return nationId;
        }

        @Override
        public int cities() {
            return cityInfra.length;
        }

        @Override
        public int researchBits() {
            return 0;
        }

        @Override
        public double cityInfra(int cityIndex) {
            return cityInfra[cityIndex];
        }

        @Override
        public Map.Entry<Integer, Integer> cityMissileDamage(int cityIndex) {
            return missileDamage[cityIndex];
        }

        @Override
        public Map.Entry<Integer, Integer> cityNukeDamage(int cityIndex) {
            return nukeDamage[cityIndex];
        }

        @Override
        public double infraAttackModifier(AttackType type) {
            return 1d;
        }

        @Override
        public double infraDefendModifier(AttackType type) {
            return 1d;
        }

        @Override
        public double looterModifier(boolean ground) {
            return 1d;
        }

        @Override
        public double lootModifier() {
            return 1d;
        }

        @Override
        public boolean isBlitzkrieg() {
            return false;
        }

        @Override
        public boolean hasProject(Project project) {
            return (projectBits & (1L << project.ordinal())) != 0L;
        }

        @Override
        public int getUnits(MilitaryUnit unit) {
            return units[unitBaseOffset + unit.ordinal()];
        }
    }

    private static long projectBits(Project... projects) {
        long bits = 0L;
        if (projects == null) {
            return bits;
        }
        for (Project project : projects) {
            bits |= 1L << project.ordinal();
        }
        return bits;
    }

    private static final class TestNationBuffer implements CombatKernel.PrimitiveNationBuffer {
        private final int[] unitsFlat;
        private final int[] unitOffsets;
        private final double[] cityInfraFlat;
        private final int[] cityOffsets;
        private final int[] cityCounts;

        private TestNationBuffer(
                int[] unitsFlat,
                int[] unitOffsets,
                double[] cityInfraFlat,
                int[] cityOffsets,
                int[] cityCounts
        ) {
            this.unitsFlat = unitsFlat;
            this.unitOffsets = unitOffsets;
            this.cityInfraFlat = cityInfraFlat;
            this.cityOffsets = cityOffsets;
            this.cityCounts = cityCounts;
        }

        @Override
        public int[] unitsFlat() {
            return unitsFlat;
        }

        @Override
        public int unitBaseOffset(int nationIndex) {
            return unitOffsets[nationIndex];
        }

        @Override
        public double[] cityInfraFlat() {
            return cityInfraFlat;
        }

        @Override
        public int cityInfraBaseOffset(int nationIndex) {
            return cityOffsets[nationIndex];
        }

        @Override
        public int cityCount(int nationIndex) {
            return cityCounts[nationIndex];
        }
    }

    private static final class BufferNationState implements CombatKernel.BufferBackedNationState {
        private final int nationId;
        private final TestNationBuffer nationBuffer;
        private final int nationIndex;

        private BufferNationState(int nationId, TestNationBuffer nationBuffer, int nationIndex) {
            this.nationId = nationId;
            this.nationBuffer = nationBuffer;
            this.nationIndex = nationIndex;
        }

        @Override
        public int nationId() {
            return nationId;
        }

        @Override
        public TestNationBuffer nationBuffer() {
            return nationBuffer;
        }

        @Override
        public int nationIndex() {
            return nationIndex;
        }

        @Override
        public int researchBits() {
            return 0;
        }

        @Override
        public Map.Entry<Integer, Integer> cityMissileDamage(int cityIndex) {
            return Map.entry(0, 0);
        }

        @Override
        public Map.Entry<Integer, Integer> cityNukeDamage(int cityIndex) {
            return Map.entry(0, 0);
        }

        @Override
        public double infraAttackModifier(AttackType type) {
            return 1d;
        }

        @Override
        public double infraDefendModifier(AttackType type) {
            return 1d;
        }

        @Override
        public double looterModifier(boolean ground) {
            return 1d;
        }

        @Override
        public double lootModifier() {
            return 1d;
        }

        @Override
        public boolean isBlitzkrieg() {
            return false;
        }

        @Override
        public boolean hasProject(Project project) {
            return false;
        }
    }

    private record TestWarBuffer(
            WarType warType,
            boolean attackerAirControl,
            boolean defenderAirControl,
            boolean attackerGroundControl,
            boolean defenderGroundControl,
            int attackerMaps,
            int defenderMaps,
            int attackerResistance,
            int defenderResistance,
            int blockadeOwner
    ) implements CombatKernel.PrimitiveWarBuffer {
        @Override
        public WarType warType(int warIndex) {
            return warType;
        }

        @Override
        public boolean attackerHasAirControl(int warIndex) {
            return attackerAirControl;
        }

        @Override
        public boolean defenderHasAirControl(int warIndex) {
            return defenderAirControl;
        }

        @Override
        public boolean attackerHasGroundControl(int warIndex) {
            return attackerGroundControl;
        }

        @Override
        public boolean defenderHasGroundControl(int warIndex) {
            return defenderGroundControl;
        }

        @Override
        public boolean attackerFortified(int warIndex) {
            return false;
        }

        @Override
        public boolean defenderFortified(int warIndex) {
            return false;
        }

        @Override
        public int attackerMaps(int warIndex) {
            return attackerMaps;
        }

        @Override
        public int defenderMaps(int warIndex) {
            return defenderMaps;
        }

        @Override
        public int attackerResistance(int warIndex) {
            return attackerResistance;
        }

        @Override
        public int defenderResistance(int warIndex) {
            return defenderResistance;
        }

        @Override
        public int blockadeOwner(int warIndex) {
            return blockadeOwner;
        }
    }

    private record BufferAttackContext(
            CombatKernel.NationState attacker,
            CombatKernel.NationState defender,
            CombatKernel.PrimitiveWarBuffer warBuffer
    ) implements CombatKernel.BufferBackedAttackContext {
        @Override
        public int warIndex() {
            return 0;
        }
    }

    private record SimpleAttackContext(
            CombatKernel.NationState attacker,
            CombatKernel.NationState defender,
            TestWarBuffer war
    ) implements CombatKernel.AttackContext {
        @Override
        public WarType warType() {
            return war.warType(0);
        }

        @Override
        public boolean attackerHasAirControl() {
            return war.attackerHasAirControl(0);
        }

        @Override
        public boolean defenderHasAirControl() {
            return war.defenderHasAirControl(0);
        }

        @Override
        public boolean attackerHasGroundControl() {
            return war.attackerHasGroundControl(0);
        }

        @Override
        public boolean defenderHasGroundControl() {
            return war.defenderHasGroundControl(0);
        }

        @Override
        public boolean attackerFortified() {
            return war.attackerFortified(0);
        }

        @Override
        public boolean defenderFortified() {
            return war.defenderFortified(0);
        }

        @Override
        public int attackerMaps() {
            return war.attackerMaps(0);
        }

        @Override
        public int defenderMaps() {
            return war.defenderMaps(0);
        }

        @Override
        public int attackerResistance() {
            return war.attackerResistance(0);
        }

        @Override
        public int defenderResistance() {
            return war.defenderResistance(0);
        }

        @Override
        public int blockadeOwner() {
            return war.blockadeOwner(0);
        }
    }

    private static DBNation nation(int cities, int soldiers, int tanks, int aircraft, int ships) {
        DBNationData data = new DBNationData();
        data.setWar_policy(WarPolicy.ATTRITION);
        data.setWarPolicyTimer(0L);
        SimpleDBNation nation = new SimpleDBNation(data) {
            @Override
            public Map<Integer, DBCity> _getCitiesV3() {
                return Collections.emptyMap();
            }

            @Override
            public double maxCityInfra() {
                return 0;
            }
        };
        nation.edit().setCities(cities);
        nation.edit().setSoldiers(soldiers);
        nation.edit().setTanks(tanks);
        nation.edit().setAircraft(aircraft);
        nation.edit().setShips(ships);
        return nation;
    }
}
