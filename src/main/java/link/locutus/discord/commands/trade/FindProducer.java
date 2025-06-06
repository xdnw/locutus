package link.locutus.discord.commands.trade;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.builder.NumericGroupRankBuilder;
import link.locutus.discord.commands.manager.v2.builder.RankBuilder;
import link.locutus.discord.commands.manager.v2.builder.SummedMapRankBuilder;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.command.shrink.IShrink;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.PWBindings;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBCity;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.*;
import java.util.stream.Collectors;

public class FindProducer extends Command {
    public FindProducer() {
        super("FindProducer", "FindProducers", CommandCategory.ECON, CommandCategory.GAME_INFO_AND_TOOLS);
    }

    @Override
    public List<CommandRef> getSlashReference() {
        return List.of(CM.trade.findProducer.cmd);
    }


    @Override
    public String help() {
        return super.help() + " <resource> [nations]";
    }

    @Override
    public String desc() {
        return """
                List alliances most producing a resource
                Use `*` as all resources converted to weekly market median
                Use `-m` to not include military upkeep
                Use `-t` to not include trade bonus
                Use `-b` to not include new nation bonus
                Use `-n` to include consumption
                Use `-a` to list by nation instead of alliance
                Use `-s` to list the average instead of the sum
                Use `-i` to include gray/beige""";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() == 0 || args.size() > 2) {
            return usage(args.size(), 1, 2, channel);
        }

        boolean militaryUpkeep = !flags.contains('m');
        boolean tradeBonus = !flags.contains('t');
        boolean newNationBonus = !flags.contains('b');
        boolean includeNegative = flags.contains('n');

        Set<ResourceType> types = args.get(0).equals("*") ? new HashSet<>(ResourceType.valuesList) : PWBindings.rssTypes(args.get(0));
        if (types.isEmpty()) return "Please provide more than one resource type: `" + args.get(0) + "`";

        List<DBNation> nations;
        if (args.size() >= 2) {
            nations = new ArrayList<>(DiscordUtil.parseNations(guild, author, me, args.get(1), false, false));
        } else {
            nations = new ArrayList<>(Locutus.imp().getNationDB().getAllNations());
        }
        if (args.size() == 1 || args.get(0).equalsIgnoreCase("*")) {
            int topX = 80;
            Set<Integer> allowedAAs = new IntOpenHashSet();
            Set<DBAlliance> topAlliances = Locutus.imp().getNationDB().getAlliances(true, true, true, topX);
            for (DBAlliance topAlliance : topAlliances) allowedAAs.add(topAlliance.getAlliance_id());
            nations.removeIf(f -> !allowedAAs.contains(f.getAlliance_id()) || f.getPosition() <= 1);

            nations.removeIf(n -> n.getAlliance_id() == 0 || n.getVm_turns() != 0 ||
                    n.getPosition() <= 1);
        }
        if (!flags.contains('i')) {
            nations.removeIf(f -> !f.isTaxable());
        }

        channel.sendMessage("Fetching cities for " + nations.size() + " nations. Please wait...");
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
            double[] profit = nation.getRevenue(12, true, militaryUpkeep, tradeBonus, newNationBonus, false, false, nation.getTreasureBonusPct(), false);
            double value;
            if (types.size() == 1) {
                value = profit[types.iterator().next().ordinal()];
            } else {
                value = 0;
                for (ResourceType type : types) {
                    value += ResourceType.convertedTotal(type, profit[type.ordinal()]);
                }
            }
            if (value > 0 || includeNegative) {
                profitByNation.put(nation, value);
            }
        }

        boolean listAlliances = !flags.contains('a');
        boolean average = flags.contains('s');

        SummedMapRankBuilder<Integer, Number> byNation = new SummedMapRankBuilder<>(profitByNation).adaptKeys((n, v) -> n.getNation_id());
        RankBuilder<IShrink> ranks;
        if (listAlliances) {
            NumericGroupRankBuilder<Integer, Number> byAAMap = byNation.group((entry, builder) -> {
                DBNation nation = Locutus.imp().getNationDB().getNationById(entry.getKey());
                if (nation != null) {
                    builder.put(nation.getAlliance_id(), entry.getValue());
                }
            });
            SummedMapRankBuilder<Integer, Number> byAA = average ? byAAMap.average() : byAAMap.sum();

            // Sort descending
            ranks = byAA.sort()
                    // Change key to alliance name
                    .nameKeys(id -> DBAlliance.getOrCreate(id).toShrink());
        } else {
            ranks = byNation.sort()
                    // Change key to alliance name
                    .nameKeys(id -> DBNation.getOrCreate(id).toShrink());
        }

        String title = "Daily " + args.get(0) + " production";
        if (listAlliances && average) title += " per member";
        if (types.size() > 1) title += " (market value)";
        ranks.build(author, channel, fullCommandRaw, title, true);
        return null;
    }
}
