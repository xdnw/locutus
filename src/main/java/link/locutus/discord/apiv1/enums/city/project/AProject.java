package link.locutus.discord.apiv1.enums.city.project;

import link.locutus.discord.apiv1.domains.Nation;
import link.locutus.discord.apiv1.enums.ResourceType;

import java.util.Map;
import java.util.function.Function;

public class AProject implements Project {
    private final Map<ResourceType, Double> cost;
    private final Function<Nation, Integer> get;
    private final ResourceType output;
    private final String apiName;
    private final String imageName;
    private final int id;
    private String name;
    private int index;

    public AProject(int id, String apiName, String imageName, Map<ResourceType, Double> cost, Function<Nation, Integer> get, ResourceType output) {
        this.id = id;
        this.cost = cost;
        this.get = get;
        this.output = output;
        this.apiName = apiName;
        this.imageName = imageName;
    }
    @Override
    public Map<ResourceType, Double> cost() {
        return cost;
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
