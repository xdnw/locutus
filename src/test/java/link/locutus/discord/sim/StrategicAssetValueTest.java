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