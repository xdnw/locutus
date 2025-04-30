package link.locutus.discord.apiv1.enums.city.building;

import link.locutus.discord.apiv1.enums.MilitaryUnit;

public interface MilitaryBuilding extends Building{
    MilitaryUnit getMilitaryUnit();

    /**
     * @return max military this military building can purchase a day
     */
    int getUnitDailyBuy();

    /**
     * @return max units this military building can hold
     */
    int getUnitCap();

    /**
     * @return required citizens per unit
     */
    double getCitizensPerUnit();


}
