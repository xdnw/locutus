package link.locutus.discord.web.commands.binding.protos;

import org.checkerframework.checker.nullness.qual.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import link.locutus.discord.apiv1.enums.ResourceType;
public class Continent_Web {
    @Nullable public List<ResourceType> getResources;
    @Nullable public double getSeasonModifierDate;
    @Nullable public Set<Building_Web> getBuildings;
    @Nullable public boolean hasResource;
    @Nullable public double getResource;
    @Nullable public double getSeasonModifier;
    @Nullable public String getName;
    @Nullable public double getRadIndex;
    @Nullable public double getResourceValue;
    @Nullable public double getAverage;
    @Nullable public boolean isNorth;
    @Nullable public int getNumNations;
    @Nullable public int getOrdinal;
    @Nullable public double getTotal;
    @Nullable public double getFoodRatio;
    @Nullable public double getAveragePer;
    @Nullable public boolean canBuild;
}