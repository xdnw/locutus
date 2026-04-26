package link.locutus.discord.util.battle;

import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.apiv3.enums.GameTimers;
import link.locutus.discord.db.entities.DBCity;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.city.SimpleDBCity;
import link.locutus.discord.db.entities.nation.DBNationData;
import link.locutus.discord.db.entities.nation.SimpleDBNation;
import link.locutus.discord.sim.combat.state.CombatCityView;
import link.locutus.discord.sim.combat.state.CombatantView;
import link.locutus.discord.util.TimeUtil;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatantViewAdapterTest {

    @Test
    void fallbackCityViewsMatchDeclaredCitiesWhenNationCityMapUnavailable() {
        DBNation nation = nationWithoutCityMap(7, 1800d);

        CombatantView view = CombatantViewAdapter.of(nation);

        Collection<? extends CombatCityView> cities = view.getCityViews();
        assertEquals(7, cities.size(), "adapter should synthesize one city view per declared city");
        assertTrue(cities.stream().allMatch(c -> c.getInfra() == 1800d));
    }

    @Test
    void infraOverrideAppliesToAllSynthesizedCities() {
        DBNation nation = nationWithoutCityMap(5, 1600d);

        CombatantView view = CombatantViewAdapter.of(nation, 1234d);

        Collection<? extends CombatCityView> cities = view.getCityViews();
        assertEquals(5, cities.size());
        assertTrue(cities.stream().allMatch(c -> c.getInfra() == 1234d));
    }

    @Test
    void partialCityMapStillProducesOneCityViewPerDeclaredCity() {
        DBNation nation = nationWithPartialCityMap(5, 1_800d, 1_250d);

        CombatantView view = CombatantViewAdapter.of(nation);

        Collection<? extends CombatCityView> cities = view.getCityViews();
        assertEquals(5, cities.size(), "adapter should fill missing city rows up to declared city count");
        assertEquals(1_250d, cities.iterator().next().getInfra());
        assertEquals(4L, cities.stream().filter(c -> c.getInfra() == 1_800d).count());
    }

    @Test
    void blitzkriegFlagUsesActiveTimerWindow() {
        long nowTurn = TimeUtil.getTurn();
        int threshold = GameTimers.WAR_POLICY.getTurns() - 12;

        DBNation inactiveBlitz = nationWithoutCityMap(
                6,
                1700d,
                WarPolicy.BLITZKRIEG,
                nowTurn + threshold
        );
        DBNation activeBlitz = nationWithoutCityMap(
                6,
                1700d,
                WarPolicy.BLITZKRIEG,
                nowTurn + threshold + 1
        );

        CombatantView inactiveView = CombatantViewAdapter.of(inactiveBlitz);
        CombatantView activeView = CombatantViewAdapter.of(activeBlitz);

        assertFalse(inactiveView.isBlitzkrieg(),
                "Blitzkrieg should be false when remaining policy turns are outside the active window");
        assertTrue(activeView.isBlitzkrieg(),
                "Blitzkrieg should be true only inside the active policy window");
    }

    private static DBNation nationWithoutCityMap(int cities, double maxInfra) {
        return nationWithoutCityMap(cities, maxInfra, WarPolicy.ATTRITION, 0L);
    }

    private static DBNation nationWithoutCityMap(int cities, double maxInfra, WarPolicy policy, long warPolicyTimer) {
        DBNationData data = new DBNationData();
        data.setWar_policy(policy);
        data.setWarPolicyTimer(warPolicyTimer);
        SimpleDBNation nation = new SimpleDBNation(data) {
            @Override
            public Map<Integer, DBCity> _getCitiesV3() {
                return Collections.emptyMap();
            }

            @Override
            public double maxCityInfra() {
                return maxInfra;
            }
        };
        nation.edit().setCities(cities);
        return nation;
    }

    private static DBNation nationWithPartialCityMap(int cities, double maxInfra, double knownCityInfra) {
        DBNationData data = new DBNationData();
        data.setWar_policy(WarPolicy.ATTRITION);
        SimpleDBCity city = new SimpleDBCity(77);
        city.setId(1);
        city.setInfra(knownCityInfra);
        SimpleDBNation nation = new SimpleDBNation(data) {
            @Override
            public Map<Integer, DBCity> _getCitiesV3() {
                return Map.of(city.getId(), city);
            }

            @Override
            public double maxCityInfra() {
                return maxInfra;
            }
        };
        nation.edit().setCities(cities);
        return nation;
    }
}
