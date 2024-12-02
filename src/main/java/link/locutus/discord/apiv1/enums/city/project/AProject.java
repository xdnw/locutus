package link.locutus.discord.apiv1.enums.city.project;

import link.locutus.discord.apiv1.domains.Nation;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.db.entities.DBNation;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class AProject implements Project {
    private final Map<ResourceType, Double> cost;
    private final double[] costArr;
    private final ResourceType output;
    private final String apiName;
    private final String imageName;
    private final int id;
    private final Supplier<Project[]> reqProjects;
    private final int requiredCities;
    private final Predicate<DBNation> otherRequirements;
    private final int maxCities;
    private String name;
    private int index;

    public AProject(int id, String apiName, String imageName, Map<ResourceType, Double> cost, ResourceType output, int requiredCities, int maxCities, Supplier<Project[]> reqProjects, Predicate<DBNation> otherRequirements) {
        this.id = id;
        this.cost = cost;
        this.costArr = ResourceType.resourcesToArray(cost);
        this.output = output;
        this.apiName = apiName;
        this.imageName = imageName;
        this.requiredCities = requiredCities;
        this.maxCities = maxCities;
        this.reqProjects = reqProjects;
        this.otherRequirements = otherRequirements;
    }

    @Override
    public Set<Project> requiredProjects() {
        return reqProjects == null ? Collections.emptySet() : new LinkedHashSet<>(Arrays.asList(reqProjects.get()));
    }

    @Override
    public int requiredCities() {
        return requiredCities;
    }

    @Override
    public int maxCities() {
        return maxCities;
    }

    @Override
    public boolean canBuild(DBNation nation) {
        return Project.super.canBuild(nation) && (otherRequirements == null || otherRequirements.test(nation));
    }

    @Override
    public Map<ResourceType, Double> cost() {
        return cost;
    }

    @Override
    public double[] costArr() {
        return costArr;
    }

    @Override
    public ResourceType getOutput() {
        return output;
    }

    @Override
    public String getApiName() {
        return apiName;
    }

    @Override
    public String name() {
        return name;
    }

    public void setName(String name, int index) {
        this.name = name;
        this.index = index;
    }

    public int getId() {
        return id;
    }

    @Override
    public String getImageName() {
        return imageName;
    }

    @Override
    public String toString() {
        return name();
    }

    @Override
    public int ordinal() {
        return id;
    }

    @Override
    public boolean hasLegacy(long bitMask) {
        return (bitMask & (1L << index + 1)) != 0;
    }

    public int getLegacyIndex() {
        return index;
    }
}
