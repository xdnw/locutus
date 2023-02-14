package link.locutus.discord.apiv1.enums.city.building.imp;

import link.locutus.discord.apiv1.domains.Nation;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.city.building.Building;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;

import java.util.function.Function;
import java.util.function.Predicate;

public class FarmBuilding extends AResourceBuilding {
    public FarmBuilding(BuildingBuilder parent) {
        super(parent, ResourceType.FOOD.getBaseInput(), ResourceType.FOOD.getBoostFactor(), ResourceType.FOOD, ResourceType.FOOD.getInputs());
    }

    @Override
    public int pollution(Predicate<Project> hasProject) {
        return super.pollution(hasProject);
    }
}
