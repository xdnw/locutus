package link.locutus.discord.sim.combat.state;

import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.sim.combat.CombatKernel;

import java.util.Collection;

public interface CombatantView extends CombatKernel.NationState {
    int getUnitCapacity(MilitaryUnit unit);

    @Override
    Collection<? extends CombatCityView> getCityViews();

    default int getUnitMaxPerDay(MilitaryUnit unit) {
        return unit.getMaxPerDay(cities(), this::hasProject, research -> research.getLevel(researchBits()));
    }

    default double getUnitConvertedCost(MilitaryUnit unit) {
        return unit.getConvertedCost(researchBits());
    }
}