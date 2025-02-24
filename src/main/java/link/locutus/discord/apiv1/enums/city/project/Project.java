package link.locutus.discord.apiv1.enums.city.project;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.NoFormat;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.NationAttributeDouble;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.util.PW;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public interface Project {
    @Command(desc = "Map of resource cost of the project")
    Map<ResourceType, Double> cost();

    public double[] costArr();

    @Command(desc = "Market value of project")
    default double getMarketValue() {
        return ResourceType.convertedTotal(cost());
    }

    @Command(desc = "Get cost for a specific resource")
    default double getResourceCost(ResourceType type) {
        return cost().getOrDefault(type, 0.0);
    }

    @Command(desc = "Get number of projects for a set of nations")
    default int getCount(@NoFormat @Default Set<DBNation> nations) {
        Collection<DBNation> all = nations;
        if (all == null) all = Locutus.imp().getNationDB().getAllNations();
        int count = 0;
        for (DBNation nation : all) {
            count += get(nation);
        }
        return count;
    }

    @Command(desc = "Get average attribute for nations with this project")
    default double getAvg(@NoFormat NationAttributeDouble attribute, @NoFormat @Default Set<DBNation> nations) {
        Collection<DBNation> all = nations;
        if (all == null) all = Locutus.imp().getNationDB().getAllNations();
        double total = 0;
        int count = 0;
        for (DBNation nation : all) {
            if (get(nation) == 0) continue;
            total += attribute.apply(nation);
            count++;
        }
        return total / count;
    }

    @Command(desc = "Get total attribute for nations with this project")
    default double getTotal(@NoFormat NationAttributeDouble attribute, @NoFormat @Default Set<DBNation> nations) {
        Collection<DBNation> all = nations;
        if (all == null) all = Locutus.imp().getNationDB().getAllNations();
        double total = 0;
        int count = 0;
        for (DBNation nation : all) {
            if (get(nation) == 0) continue;
            total += attribute.apply(nation);
            count++;
        }
        return total / count;
    }

    default double[] cost(boolean technologicalAdvancement, boolean governmentSupportAgency, boolean bureauOfDomesticAffairs) {
        double[] cost = ResourceType.resourcesToArray(cost());
        if (technologicalAdvancement) {
            double factor = 0.95;
            if (governmentSupportAgency) {
                factor -= 0.025;
            }
            if (bureauOfDomesticAffairs) {
                factor -= 0.0125;
            }
            cost = PW.multiply(cost, factor);
        }
        return cost;
    }


    default int get(DBNation nation) {
        return nation.hasProject(this) ? 1 : 0;
    }

    @Command(desc = "If a nation has a project")
    default boolean has(DBNation nation) {
        return nation.hasProject(this);
    }

    @Command(desc = "Name of project")
    String name();

    @Command(desc = "Api v3 name of project")
    String getApiName();

    @Command(desc = "Bot id of project")
    int ordinal();

    @Command(desc = "The resource this project boosts, or null")
    ResourceType getOutput();

    @Command(desc = "If a bitmask has this project")
    default boolean hasBit(long bitMask) {
        return (bitMask & (1L << ordinal())) != 0;
    }

    @Deprecated
    boolean hasLegacy(long bitMask);

    @Command(desc = "The name of this project's image")
    String getImageName();

    @Command(desc = "The set of required projects (empty if none)")
    public Set<Project> requiredProjects();

    @Command(desc = "If a project is required for this project")
    default boolean isRequiredProject(Project project) {
        return requiredProjects().contains(project);
    }

    @Command(desc = "If this project has any project requirements")
    default boolean hasProjectRequirements() {
        return !requiredProjects().isEmpty();
    }

    @Command(desc = "Get the nth required project")
    default Project getRequiredProject(int index) {
        return requiredProjects().stream().skip(index).findFirst().orElse(null);
    }


    @Command(desc = "Required minimum cities for project, or 0 if none")
    public int requiredCities();

    @Command(desc = "Required maximum cities for project, or Integer.MAX_VALUE if none")
    public int maxCities();

    @Command(desc = "If a nation can build this project")
    default boolean canBuild(DBNation nation) {
        if (nation.getCities() < requiredCities()) return false;
        for (Project project : requiredProjects()) {
            if (!nation.hasProject(project)) return false;
        }
        return true;
    }

    @Command(desc = "The url of this project's image")
    default String getImageUrl() {
        String name = getImageName();
        return "" + Settings.INSTANCE.PNW_URL() + "/img/projects/" + name + (name.contains(".") ? "" : ".jpg");
    }

    boolean isDisabled();
}