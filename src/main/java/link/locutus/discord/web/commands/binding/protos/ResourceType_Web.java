package link.locutus.discord.web.commands.binding.protos;

import org.checkerframework.checker.nullness.qual.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.Continent;
public class ResourceType_Web {
    @Nullable public int getBaseInput;
    @Nullable public boolean isRaw;
    @Nullable public int getUpkeep;
    @Nullable public double getManufacturingMultiplier;
    @Nullable public Map<ResourceType, Double> getProduction;
    @Nullable public Project_Web getProject;
    @Nullable public boolean canProduceInAny;
    @Nullable public double getResource;
    @Nullable public String getName;
    @Nullable public double getResourceValue;
    @Nullable public Building_Web getBuilding;
    @Nullable public int getCap;
    @Nullable public double getMarketValue;
    @Nullable public Set<Continent> getContinents;
    @Nullable public int getPollution;
    @Nullable public List<ResourceType> getInputList;
    @Nullable public double getBoostFactor;
    @Nullable public int getGraphId;
    @Nullable public boolean hasBuilding;
    @Nullable public boolean isManufactured;
}