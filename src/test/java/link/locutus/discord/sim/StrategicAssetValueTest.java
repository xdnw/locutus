package link.locutus.discord.sim;

import link.locutus.discord.apiv1.enums.MilitaryUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class StrategicAssetValueTest {
    @Test
    void higherCityTierAndRangeCoverageIncreaseStrategicValue() {
        StrategicAssetValue.StrategicRelevance frontline = StrategicAssetValue.relevanceForWarRange(
                28,
                1_000d,
                1,
                4,
                index -> new double[]{900d, 1_250d, 1_800d, 2_400d}[index]
        );
        StrategicAssetValue.StrategicRelevance fringe = StrategicAssetValue.relevanceForWarRange(
                6,
                1_000d,
                0,
                4,
                index -> new double[]{200d, 320d, 410d, 2_700d}[index]
        );

        StrategicAssetValue.NationValueBreakdown frontlineValue = StrategicAssetValue.contextualMilitaryValue(
                unit -> unit == MilitaryUnit.AIRCRAFT ? 50 : 0,
                unit -> 0,
                unit -> unit == MilitaryUnit.AIRCRAFT ? 10 : 0,
                unit -> unit == MilitaryUnit.AIRCRAFT ? 50 : 0,
                0,
                false,
                frontline
        );
        StrategicAssetValue.NationValueBreakdown fringeValue = StrategicAssetValue.contextualMilitaryValue(
                unit -> unit == MilitaryUnit.AIRCRAFT ? 50 : 0,
                unit -> 0,
                unit -> unit == MilitaryUnit.AIRCRAFT ? 10 : 0,
                unit -> unit == MilitaryUnit.AIRCRAFT ? 50 : 0,
                0,
                false,
                fringe
        );

        assertTrue(frontlineValue.contextualMilitaryValue() > fringeValue.contextualMilitaryValue(),
                "The contextual layer should value the same force more highly when it belongs to a higher-city nation that can materially reach the opposing tier");
        assertTrue(frontlineValue.totalValue() > fringeValue.totalValue(),
                "Strategic totals should increase when city tier and war-range coverage make the force more outcome-relevant");
    }

    @Test
    void exhaustedRebuyMakesCurrentHoldingsMoreStrategicallyScarce() {
        StrategicAssetValue.NationValueBreakdown fresh = StrategicAssetValue.contextualMilitaryValue(
                unit -> unit == MilitaryUnit.AIRCRAFT ? 50 : 0,
                unit -> 0,
                unit -> unit == MilitaryUnit.AIRCRAFT ? 0 : 0,
                unit -> unit == MilitaryUnit.AIRCRAFT ? 50 : 0,
                0,
                false
        );
        StrategicAssetValue.NationValueBreakdown exhausted = StrategicAssetValue.contextualMilitaryValue(
                unit -> unit == MilitaryUnit.AIRCRAFT ? 50 : 0,
                unit -> 0,
                unit -> unit == MilitaryUnit.AIRCRAFT ? 50 : 0,
                unit -> unit == MilitaryUnit.AIRCRAFT ? 50 : 0,
                0,
                false
        );

        assertTrue(exhausted.totalValue() > fresh.totalValue(),
                "Holdings with no remaining daily recovery should carry higher immediate strategic value than the same holdings with full remaining rebuy");
        assertTrue(fresh.recoveryValue() > 0d,
                "Available daily recovery should surface as explicit recovery value rather than disappearing into flat replacement cost");
    }

    @Test
    void activeWarsIncreaseWarOutcomeLeverage() {
        StrategicAssetValue.NationValueBreakdown inactive = StrategicAssetValue.contextualMilitaryValue(
                unit -> unit == MilitaryUnit.TANK ? 500 : 0,
                unit -> 0,
                unit -> unit == MilitaryUnit.TANK ? 500 : 0,
                unit -> unit == MilitaryUnit.TANK ? 500 : 0,
                0,
                false
        );
        StrategicAssetValue.NationValueBreakdown engaged = StrategicAssetValue.contextualMilitaryValue(
                unit -> unit == MilitaryUnit.TANK ? 500 : 0,
                unit -> 0,
                unit -> unit == MilitaryUnit.TANK ? 500 : 0,
                unit -> unit == MilitaryUnit.TANK ? 500 : 0,
                0,
                true
        );

        assertTrue(engaged.warOutcomeLeverage() > 0d);
        assertTrue(engaged.totalValue() > inactive.totalValue(),
                "Identical military state should be worth more when it is already engaged in active wars");
    }

    @Test
    void currentWarStateModulatesOutcomeLeverage() {
        StrategicAssetValue.NationValueBreakdown winningControl = StrategicAssetValue.contextualMilitaryValue(
                unit -> unit == MilitaryUnit.AIRCRAFT ? 500 : 0,
                unit -> 0,
                unit -> unit == MilitaryUnit.AIRCRAFT ? 500 : 0,
                unit -> unit == MilitaryUnit.AIRCRAFT ? 500 : 0,
                0,
                StrategicAssetValue.ActiveWarContext.fromRelativeWarState(
                        1,
                        1.0d,
                        12,
                        3,
                        82,
                        38,
                        2,
                        0
                ),
                StrategicAssetValue.StrategicRelevance.DEFAULT
        );
        StrategicAssetValue.NationValueBreakdown lostControl = StrategicAssetValue.contextualMilitaryValue(
                unit -> unit == MilitaryUnit.AIRCRAFT ? 500 : 0,
                unit -> 0,
                unit -> unit == MilitaryUnit.AIRCRAFT ? 500 : 0,
                unit -> unit == MilitaryUnit.AIRCRAFT ? 500 : 0,
                0,
                StrategicAssetValue.ActiveWarContext.fromRelativeWarState(
                        1,
                        1.0d,
                        0,
                        12,
                        20,
                        82,
                        0,
                        3
                ),
                StrategicAssetValue.StrategicRelevance.DEFAULT
        );

        assertTrue(winningControl.warOutcomeLeverage() > lostControl.warOutcomeLeverage(),
                "MAP, resistance, and control state should suppress leverage for wars that are no longer tenable");
    }

    @Test
    void slotPressureRaisesActiveWarLeverage() {
        StrategicAssetValue.NationValueBreakdown oneOpenWar = StrategicAssetValue.contextualMilitaryValue(
                unit -> unit == MilitaryUnit.SHIP ? 20 : 0,
                unit -> 0,
                unit -> unit == MilitaryUnit.SHIP ? 0 : 0,
                unit -> unit == MilitaryUnit.SHIP ? 5 : 0,
                0,
                StrategicAssetValue.ActiveWarContext.fromSlots(1, 3, 0, 1),
                StrategicAssetValue.StrategicRelevance.DEFAULT
        );
        StrategicAssetValue.NationValueBreakdown saturatedWars = StrategicAssetValue.contextualMilitaryValue(
                unit -> unit == MilitaryUnit.SHIP ? 20 : 0,
                unit -> 0,
                unit -> unit == MilitaryUnit.SHIP ? 0 : 0,
                unit -> unit == MilitaryUnit.SHIP ? 5 : 0,
                0,
                StrategicAssetValue.ActiveWarContext.fromSlots(3, 3, 3, 6),
                StrategicAssetValue.StrategicRelevance.DEFAULT
        );

        assertTrue(saturatedWars.warOutcomeLeverage() > oneOpenWar.warOutcomeLeverage(),
                "Units committed into saturated active-war slots should carry more war-outcome leverage than the same units in a low-pressure war state");
    }

    @Test
    void infrastructureValueDependsOnTenableWarState() {
        double[] cityInfra = {2_500d, 2_500d, 2_500d};
        StrategicAssetValue.StrategicRelevance relevance = new StrategicAssetValue.StrategicRelevance(18, 3, 4, 1);

        double favorable = StrategicAssetValue.infrastructureValue(
                cityInfra,
                StrategicAssetValue.ActiveWarContext.fromRelativeWarState(
                        1,
                        1.0d,
                        12,
                        3,
                        86,
                        42,
                        2,
                        0
                ),
                relevance
        );
        double noActiveWar = StrategicAssetValue.infrastructureValue(
                cityInfra,
                StrategicAssetValue.ActiveWarContext.NONE,
                relevance
        );
        double lostControl = StrategicAssetValue.infrastructureValue(
                cityInfra,
                StrategicAssetValue.ActiveWarContext.fromRelativeWarState(
                        1,
                        1.0d,
                        0,
                        12,
                        18,
                        86,
                        0,
                        3
                ),
                relevance
        );

        assertTrue(favorable > noActiveWar,
                "Infra should be worth more when current control and resistance make preservation strategically tenable");
        assertTrue(noActiveWar > lostControl,
                "Lost-control states should suppress delayed infra preservation instead of pricing it as flat replacement value");
    }

    @Test
    void recoverableLossesArePricedBelowExhaustedLosses() {
        double recoverableLossValue = StrategicAssetValue.contextualLossValue(
                unit -> unit == MilitaryUnit.SOLDIER ? 100_000 : 0,
                unit -> unit == MilitaryUnit.SOLDIER ? 20_000 : 0,
                unit -> unit == MilitaryUnit.SOLDIER ? 0 : 0,
                unit -> unit == MilitaryUnit.SOLDIER ? 100_000 : 0,
                0,
                false
        );
        double exhaustedLossValue = StrategicAssetValue.contextualLossValue(
                unit -> unit == MilitaryUnit.SOLDIER ? 100_000 : 0,
                unit -> unit == MilitaryUnit.SOLDIER ? 20_000 : 0,
                unit -> unit == MilitaryUnit.SOLDIER ? 100_000 : 0,
                unit -> unit == MilitaryUnit.SOLDIER ? 100_000 : 0,
                0,
                false
        );

        assertTrue(exhaustedLossValue > recoverableLossValue,
                "Losses should be priced as more strategically harmful when the nation cannot replenish them in the current buy window");
    }

    @Test
    void marginalLossValueDiminishesWhenFutureActionSpaceIsLost() {
        StrategicAssetValue.ActiveWarContext tenable = StrategicAssetValue.ActiveWarContext.fromRelativeWarState(
                1,
                1.0d,
                12,
                3,
                86,
                42,
                2,
                0
        );
        StrategicAssetValue.ActiveWarContext lost = StrategicAssetValue.ActiveWarContext.fromRelativeWarState(
                1,
                1.0d,
                0,
                12,
                18,
                86,
                0,
                3
        );

        double tenableLoss = StrategicAssetValue.marginalLossValue(
                unit -> unit == MilitaryUnit.AIRCRAFT ? 500 : 0,
                unit -> unit == MilitaryUnit.AIRCRAFT ? 100 : 0,
                unit -> unit == MilitaryUnit.AIRCRAFT ? 500 : 0,
                unit -> unit == MilitaryUnit.AIRCRAFT ? 500 : 0,
                0,
                tenable,
                StrategicAssetValue.StrategicRelevance.DEFAULT
        );
        double lostLoss = StrategicAssetValue.marginalLossValue(
                unit -> unit == MilitaryUnit.AIRCRAFT ? 500 : 0,
                unit -> unit == MilitaryUnit.AIRCRAFT ? 100 : 0,
                unit -> unit == MilitaryUnit.AIRCRAFT ? 500 : 0,
                unit -> unit == MilitaryUnit.AIRCRAFT ? 500 : 0,
                0,
                lost,
                StrategicAssetValue.StrategicRelevance.DEFAULT
        );

        assertTrue(tenableLoss > lostLoss,
                "The same casualties should carry less marginal strategic value once they no longer change future action space");
        assertTrue(lostLoss > 0d,
                "Diminishing returns should retain an economic damage floor instead of zeroing casualties");
    }

    @Test
    void controlRegimeSuppressesTinySwingsWhenDurableControlIsUnattainable() {
        double lostBaseline = StrategicAssetValue.controlRegimeScore(
                18,
                86,
                0,
                3,
                0d
        );
        double lostTinySwing = StrategicAssetValue.controlRegimeScore(
                18,
                86,
                1,
                2,
                0d
        );
        double tenableBaseline = StrategicAssetValue.controlRegimeScore(
                65,
                55,
                0,
                0,
                0d
        );
        double tenableControlGain = StrategicAssetValue.controlRegimeScore(
                65,
                55,
                1,
                0,
                0d
        );

        double lostSwingValue = lostTinySwing - lostBaseline;
        double tenableSwingValue = tenableControlGain - tenableBaseline;

        assertTrue(lostSwingValue < 1.0d,
                "A tiny control fluctuation in a decisive lost-control state should not look like durable control progress");
        assertTrue(tenableSwingValue > lostSwingValue * 4.0d,
                "When resistance state is tenable again, the same control gain should regain strategic value");
    }

    @Test
    void controlRegimeDoesNotLetStrategicEdgeOverrideLostControlState() {
        double lostWithStrongStrategicEdge = StrategicAssetValue.controlRegimeScore(
                18,
                86,
                1,
                2,
                1d
        );
        double tenableWithStrongStrategicEdge = StrategicAssetValue.controlRegimeScore(
                70,
                55,
                1,
                0,
                1d
        );

        assertTrue(lostWithStrongStrategicEdge < 0d,
                "A large strategic-value edge should not by itself make a tactically lost active war look controlled");
        assertTrue(tenableWithStrongStrategicEdge > 0d,
                "Strategic edge should still matter once the active war state is tactically tenable");
    }

    @Test
    void frontlineLossesArePricedAboveFringeLosses() {
        StrategicAssetValue.StrategicRelevance frontline = StrategicAssetValue.relevanceForWarRange(
                26,
                1_200d,
                1,
                3,
                index -> new double[]{950d, 1_450d, 2_100d}[index]
        );
        StrategicAssetValue.StrategicRelevance fringe = StrategicAssetValue.relevanceForWarRange(
                7,
                1_200d,
                0,
                3,
                index -> new double[]{250d, 330d, 3_100d}[index]
        );

        double frontlineLossValue = StrategicAssetValue.contextualLossValue(
                unit -> unit == MilitaryUnit.TANK ? 2_000 : 0,
                unit -> unit == MilitaryUnit.TANK ? 400 : 0,
                unit -> unit == MilitaryUnit.TANK ? 200 : 0,
                unit -> unit == MilitaryUnit.TANK ? 2_000 : 0,
                0,
                false,
                frontline
        );
        double fringeLossValue = StrategicAssetValue.contextualLossValue(
                unit -> unit == MilitaryUnit.TANK ? 2_000 : 0,
                unit -> unit == MilitaryUnit.TANK ? 400 : 0,
                unit -> unit == MilitaryUnit.TANK ? 200 : 0,
                unit -> unit == MilitaryUnit.TANK ? 2_000 : 0,
                0,
                false,
                fringe
        );

        assertTrue(frontlineLossValue > fringeLossValue,
                "Losses should price higher when the same units belong to a higher-tier nation that can materially contest the opposing range");
    }
}
