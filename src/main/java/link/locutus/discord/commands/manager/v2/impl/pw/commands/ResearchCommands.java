package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import link.locutus.discord.apiv1.enums.Research;
import link.locutus.discord.apiv1.enums.ResearchGroup;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Switch;

import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Collectors;

public class ResearchCommands {

    @Command(desc = "Get the cost of a research upgrade", viewable = true)
    public String researchCost(Map<Research, Integer> start_level, Map<Research, Integer> end_level) {
        StringBuilder response = new StringBuilder();
        Map<ResourceType, Double> cost = new EnumMap<>(ResourceType.class);

        int totalUpgrades = start_level.values().stream().mapToInt(Integer::intValue).sum();
        Map<ResearchGroup, Integer> byGroup = start_level.entrySet().stream().collect(
                Collectors.groupingBy(e -> e.getKey().getGroup(), Collectors.summingInt(Map.Entry::getValue)));

        int numResearch = 0;

        for (Map.Entry<Research, Integer> entry : end_level.entrySet()) {
            Research r = entry.getKey();
            int startValue = start_level.getOrDefault(entry.getKey(), 0);
            int endValue = entry.getValue();
            if (startValue == endValue) continue;
            if (startValue > endValue) throw new IllegalArgumentException("Cannot decrease research level");

            int treeUpgrades = byGroup.get(r.getGroup());
            Map<ResourceType, Double> addCost = r.getCost(treeUpgrades, totalUpgrades, startValue, endValue);
            response.append("**" + r.name() + ":** " + startValue + " -> " + endValue + ": worth ~$" + ResourceType.convertedTotal(addCost) + "\n");
            response.append("- cost: `" + ResourceType.toString(addCost) + "`\n");

            totalUpgrades += endValue - startValue;
            byGroup.put(r.getGroup(), treeUpgrades + endValue - startValue);

            numResearch++;
        }

        if (numResearch > 0) {
            response.append("\n**Total Cost:** worth~$" + ResourceType.convertedTotal(cost) + "\n");
            response.append("- cost: `" + ResourceType.toString(cost) + "`\n");
        } else if (numResearch == 0) {
            return "No research upgrades specified";
        }
        return response.toString();
    }
}
