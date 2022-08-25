package link.locutus.discord.commands.trade;

import link.locutus.discord.Locutus;
import link.locutus.discord.db.entities.DBCity;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.PWBindings;
import link.locutus.discord.commands.rankings.builder.NumericGroupRankBuilder;
import link.locutus.discord.commands.rankings.builder.RankBuilder;
import link.locutus.discord.commands.rankings.builder.SummedMapRankBuilder;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class FindProducer extends Command {
    public FindProducer() {
        super("FindProducer", "FindProducers", CommandCategory.ECON, CommandCategory.GAME_INFO_AND_TOOLS);
    }
    @Override
    public String help() {
        return super.help() + " <resource> [nations]";
    }

    @Override
    public String desc() {
        return "List alliances most producing a resource\n" +
                "Use `*` as all resources converted to weekly market median\n" +
                "Use `-m` to not include military upkeep\n" +
                "Use `-t` to not include trade bonus\n" +
                "Use `-n` to not include new nation bonus\n" +
                "Use `-a` to list by nation instead of alliance\n" +
                "Use `-s` to list the average instead of the sum";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() == 0 || args.size() > 2) {
            return usage(event);
        }

        boolean militaryUpkeep = !flags.contains('m');
        boolean tradeBonus = !flags.contains('t');
        boolean newNationBonus = !flags.contains('n');

        List<ResourceType> types = args.get(0).equals("*") ? ResourceType.valuesList : PWBindings.rssTypes(args.get(0));
        if (types.isEmpty()) return "Please provide more than one resource type: `" + args.get(0) + "`";

        List<DBNation> nations;
        if (args.size() >= 2) {
            nations = new ArrayList<>(DiscordUtil.parseNations(guild, args.get(1)));
        } else {
            nations = new ArrayList<>(Locutus.imp().getNationDB().getNations().values());
        }
        if (args.size() == 1 || args.get(0).equalsIgnoreCase("*")) {
            int topX = 80;
            Set<Integer> allowedAAs = new HashSet<>();
            Set<DBAlliance> topAlliances = Locutus.imp().getNationDB().getAlliances(true, true, true, topX);
            for (DBAlliance topAlliance : topAlliances) allowedAAs.add(topAlliance.getAlliance_id());
            nations.removeIf(f -> !allowedAAs.contains(f.getAlliance_id()) || f.getPosition() <= 1);

            nations.removeIf(n -> n.getAlliance_id() == 0 || n.getVm_turns() != 0 ||
                    n.getPosition() <= 1 ||
                    n.isGray() ||
                    n.isBeige());
        } else if (flags.contains('i')) {
            nations.removeIf(n -> n.getAlliance_id() == 0 || n.getVm_turns() != 0 ||
                    n.getPosition() <= 1 ||
                    n.getActive_m() > 2440);
        }

        Message msg = RateLimitUtil.complete(event.getChannel().sendMessage("Fetching cities for " + nations.size() + " nations. Please wait..."));
        long last = System.currentTimeMillis();
        Map<DBNation, Number> profitByNation = new HashMap<>();

        Set<Integer> nationIds = nations.stream().map(DBNation::getNation_id).collect(Collectors.toSet());

        Map<Integer, Map<Integer, DBCity>> allCities = Locutus.imp().getNationDB().getCitiesV3(nationIds);
        double[] profitBuffer = ResourceType.getBuffer();
        for (DBNation nation : nations) {
            Map<Integer, DBCity> v3Cities = allCities.get(nation.getNation_id());
            if (v3Cities == null || v3Cities.isEmpty()) continue;

            Map<Integer, JavaCity> cities = Locutus.imp().getNationDB().toJavaCity(v3Cities);

            Arrays.fill(profitBuffer, 0);
            double[] profit = nation.getRevenue();
            double value;
            if (types.size() == 1) {
                value = profit[types.get(0).ordinal()];
            } else {
                value = 0;
                for (ResourceType type : types) {
                    value += PnwUtil.convertedTotal(type, profit[type.ordinal()]);
                }
            }
            if (value > 0) {
                profitByNation.put(nation, value);
            }
        }

        boolean listAlliances = !flags.contains('a');
        boolean average = flags.contains('s');

        SummedMapRankBuilder<Integer, Number> byNation = new SummedMapRankBuilder<>(profitByNation).adaptKeys((n, v) -> n.getNation_id());
        RankBuilder<String> ranks;
        if (listAlliances) {
            NumericGroupRankBuilder<Integer, Number> byAAMap = byNation.group((entry, builder) -> {
                DBNation nation = Locutus.imp().getNationDB().getNation(entry.getKey());
                if (nation != null) {
                    builder.put(nation.getAlliance_id(), entry.getValue());
                }
            });
            SummedMapRankBuilder<Integer, Number> byAA = average ? byAAMap.average() : byAAMap.sum();

            // Sort descending
            ranks = byAA.sort()
                    // Change key to alliance name
                    .nameKeys(id -> PnwUtil.getName(id, true));
        } else {
            ranks = byNation.sort()
                    // Change key to alliance name
                    .nameKeys(allianceId -> PnwUtil.getName(allianceId, false));
        }

        String title = "Daily " + args.get(0) + " production";
        if (types.size() > 1) title += " (market value)";
        ranks.build(event, title);

        if (ranks.get().size() > 25) {
            DiscordUtil.upload(event.getGuildChannel(), title, ranks.toString());
        }
        return null;
    }
}
