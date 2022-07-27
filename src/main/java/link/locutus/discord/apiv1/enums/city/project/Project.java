package link.locutus.discord.apiv1.enums.city.project;

import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.apiv1.enums.ResourceType;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface Project {
    Map<ResourceType, Double> cost();

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