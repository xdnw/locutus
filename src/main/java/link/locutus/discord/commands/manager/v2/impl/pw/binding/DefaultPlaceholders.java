package link.locutus.discord.commands.manager.v2.impl.pw.binding;

import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.commands.manager.v2.binding.annotation.Binding;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.util.PW;

import java.util.Map;

public class DefaultPlaceholders {

    @Command(desc = "Gets a resource amount from a map of resources")
    @Binding(examples = {"getResource({resources},food)"})
    public double getResource(Map<ResourceType, Double> resources, ResourceType resource) {
        return resources.getOrDefault(resource, 0.0);
    }

    @Command(desc = "Gets the total resource value for a map of resources")
    @Binding(examples = {"getResourceValue({resources})"})
    public double getResourceValue(Map<ResourceType, Double> resources) {
        return ResourceType.convertedTotal(resources);
    }
}
