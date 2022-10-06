package link.locutus.discord.apiv1.enums.city.project;

import link.locutus.discord.apiv1.enums.DomesticPolicy;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.util.PnwUtil;
import rocker.grant.project;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface Project {
    Map<ResourceType, Double> cost();

    default double[] cost(boolean technologicalAdvancement) {
        double[] cost = PnwUtil.resourcesToArray(cost());
        if (technologicalAdvancement) {
            cost = PnwUtil.multiply(cost, 0.95);
        }
        return cost;
    }

    default int get(DBNation nation) {
        return nation.hasProject(this) ? 1 : 0;
    }

    String name();

    String getApiName();

    int ordinal();

    ResourceType getOutput();

    default boolean has(long bitMask) {
        return (bitMask & (1L << ordinal())) != 0;
    }

    @Deprecated
    boolean hasLegacy(long bitMask);

    String getImageName();

    public Set<Project> requiredProjects();

    public int requiredCities();

    public int maxCities();

    default boolean canBuild(DBNation nation) {
        if (nation.getCities() < requiredCities()) return false;
        for (Project project : requiredProjects()) {
            if (!nation.hasProject(project)) return false;
        }
        return true;
    }

    default String getImageUrl() {
        String name = getImageName();
        return "" + Settings.INSTANCE.PNW_URL() + "/img/projects/" + name + (name.contains(".") ? "" : ".jpg");
    }
}