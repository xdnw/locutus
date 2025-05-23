package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.apiv1.enums.Research;
import link.locutus.discord.apiv1.enums.ResearchGroup;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.annotation.Switch;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.guild.SheetKey;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.sheet.SpreadSheet;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class ResearchCommands {

    @Command(desc = "Get the cost of a research upgrade", viewable = true)
    public String researchCost(Map<Research, Integer> end_level, @Default Map<Research, Integer> start_level, @Default Boolean military_doctrine) {
        if (start_level == null) start_level = new Object2IntOpenHashMap<>();
        double factor = military_doctrine == Boolean.TRUE ? 0.95 : 1;

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

            int treeUpgrades = byGroup.getOrDefault(r.getGroup(), 0);
            Map<ResourceType, Double> addCost = r.getCost(treeUpgrades, totalUpgrades, startValue, endValue, factor);
            cost = ResourceType.add(cost, addCost);
            response.append("**" + r.name() + ":** " + startValue + " -> " + endValue + ": worth ~$" + MathMan.format(ResourceType.convertedTotal(addCost)) + "\n");
            response.append("- cost: `" + ResourceType.toString(addCost) + "`\n");


            totalUpgrades += endValue - startValue;
            byGroup.put(r.getGroup(), treeUpgrades + endValue - startValue);

            numResearch++;
        }

        if (numResearch > 0) {
            response.append("\n**Total Cost:** worth~$" + MathMan.format(ResourceType.convertedTotal(cost)) + "\n");
            response.append("- cost: `" + ResourceType.toString(cost) + "`\n");
        } else if (numResearch == 0) {
            return "No research upgrades specified";
        }
        return response.toString();
    }

    @Command(desc = "Show the research a nation has")
    public String getResearch(DBNation nation) {
        return nation.getMarkdownUrl() +"\n- Research: `" + nation.getResearchLevels() + "`\n" +
                "- cost: `" + ResourceType.toString(nation.getResearchCost()) + "`\n" +
                "- worth: ~$" + ResourceType.convertedTotal(nation.getResearchCost());
    }

    // research sheet
    @Command(desc = "Get the research sheet")
    @RolePermission(value = Roles.MEMBER, onlyInGuildAlliance = true)
    public String researchSheet(
            @Me IMessageIO io, @Me @Default GuildDB db,
            Set<DBNation> nations, @Switch("s") SpreadSheet sheet) throws GeneralSecurityException, IOException {
        if (sheet == null) sheet = SpreadSheet.create(db, SheetKey.RESEARCH_SHEET);

        List<String> header = new ArrayList<>(Arrays.asList(
                "nation",
                "alliance",
                "cities",
                "total",
                "cost_raw",
                "value"
        ));
        for (Research r : Research.values()) {
            header.add(r.name());
        }

        sheet.setHeader(header);

        CompletableFuture<IMessageBuilder> msgFuture = (io.sendMessage("Please wait..."));
        long start = System.currentTimeMillis();
        for (DBNation nation : nations) {
            if (start + 10000 < System.currentTimeMillis()) {
                start = System.currentTimeMillis();
                io.updateOptionally(msgFuture, "Updating research for " + nation.getMarkdownUrl());
            }
            List<Object> row = new ArrayList<>();
            row.add(nation.getSheetUrl());
            DBAlliance alliance = nation.getAlliance();
            row.add(alliance == null ? "" : alliance.getSheetUrl());
            row.add(nation.getCities());
            Map<Research, Integer> levels = nation.getResearchLevels();
            row.add(levels.values().stream().mapToInt(Integer::intValue).sum());
            Map<ResourceType, Double> cost = Research.cost(Collections.emptyMap(), levels, nation.getResearchCostFactor());
            row.add(ResourceType.toString(cost));
            row.add(ResourceType.convertedTotal(cost));
            for (Research r : Research.values()) {
                row.add(levels.getOrDefault(r, 0));
            }
            sheet.addRow(row);
        }

        sheet.updateClearCurrentTab();
        sheet.updateWrite();

        sheet.attach(io.create(), "research").send();
        return null;
    }

    @Command(desc = "Generate a sheet of the max research level buyable for each city cost")
    public void researchCityTable(@Me IMessageIO io, @Me @Default GuildDB db, Set<Research> research, @Default("100") Double percent_of_city_cost, @Switch("s") SpreadSheet sheet,
                                  @Switch("g") boolean gov_support_agency,
                                  @Switch("b") boolean domestic_affairs,
                                  @Switch("d") boolean military_doctrine) throws GeneralSecurityException, IOException {
        if (sheet == null) {
            sheet = SpreadSheet.create(db, SheetKey.RESEARCH_CITY);
        }
        percent_of_city_cost *= 0.01;

        List<String> header = new ArrayList<>(Arrays.asList(
                "cities",
                "city_cost",
                "research_level",
                "research_cost"
        ));
        sheet.setHeader(header);
        List<Research> researchList = new ObjectArrayList<>(research);

        double costFactor = military_doctrine ? 0.95 : 1;
        for (int cities = 1; cities < 70; cities++) {
            double cityCost = PW.City.cityCost(cities - 1, cities, true, false, false, false, gov_support_agency, domestic_affairs);
            double maxCost = cityCost * percent_of_city_cost;
            Map<Research, Integer> levels = Research.findLevels(researchList, maxCost, costFactor);
            Map<ResourceType, Double> cost = Research.cost(Collections.emptyMap(), levels, costFactor);//.get(0, 0, 0, level, costFactor);
            double costConverted = ResourceType.convertedTotal(cost);

            header.set(0, String.valueOf(cities));
            header.set(1, (MathMan.format(cityCost)));
            header.set(2, StringMan.getString(levels));
            header.set(3, String.valueOf(costConverted));
            sheet.addRow(header);

            if (!levels.isEmpty() && levels.values().stream().allMatch(f -> f == Research.MAX_LEVEL)) {
                break;
            }
        }
        sheet.updateClearCurrentTab();
        sheet.updateWrite();

        sheet.attach(io.create(), "research_city").send();
    }
}
