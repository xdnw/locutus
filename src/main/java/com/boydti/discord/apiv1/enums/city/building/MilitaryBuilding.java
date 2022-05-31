package com.boydti.discord.apiv1.enums.city.building;

import com.boydti.discord.apiv1.enums.MilitaryUnit;

public interface MilitaryBuilding extends Building{
    MilitaryUnit unit();

    /**
     * @return max military this military building can purchase a day
     */
    int perDay();

    /**
     * @return max units this military building can hold
     */
    int max();

    /**
     * @return required citizens per unit
     */
    double requiredCitizens();
}
