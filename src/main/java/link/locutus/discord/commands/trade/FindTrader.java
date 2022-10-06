package link.locutus.discord.commands.trade;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.rankings.builder.SummedMapRankBuilder;
import link.locutus.discord.db.entities.DBTrade;
import link.locutus.discord.db.entities.Transfer;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.trade.TradeManager;
import link.locutus.discord.apiv1.enums.ResourceType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FindTrader extends Command {
    public FindTrader() {
        super("findtrader", "trader", "resourcetraderanking", CommandCategory.ECON, CommandCategory.GAME_INFO_AND_TOOLS);
    }
    @Override
    public String help() {
        return super.help() + " <resource> <buy|sell> <days>";
    }

    @Override
    public String desc() {
        return "List the nations most buying or selling a resource";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() != 3) {
            return usage(event);
        }
        Integer days = MathMan.parseInt(args.get(2));
        if (days == null) {
            return "Invalid number of days: `" + args.get(2) + "`";
        }
        int findsign = 0;
        if (args.get(1).equalsIgnoreCase("buy")) {
            findsign = 1;
        } else if (args.get(1).equalsIgnoreCase("sell")) {
            findsign = -1;
        } else {
            return "Must be either buy/sell: `" + args.get(1) + "`";
        }
        ResourceType type = ResourceType.parse(args.get(0));
        if (type == ResourceType.MONEY || type == ResourceType.CREDITS) return "Invalid resource";

        TradeManager manager = Locutus.imp().getTradeManager();
        link.locutus.discord.db.TradeDB db = manager.getTradeDb();
        long cutoff = ZonedDateTime.now(ZoneOffset.UTC).minusDays(days).toEpochSecond() * 1000L;
        List<DBTrade> offers = db.getTrades(cutoff);

        Collection<Transfer> transfers = manager.toTransfers(offers, false);
        Map<Integer, double[]> inflows = manager.inflows(transfers, flags.contains('a'));
        Map<Integer, double[]> ppu = manager.ppuByNation(offers, flags.contains('a'));

//        for (ResourceType type : ResourceType.values()) {

            Map<Integer, Double> newMap = new HashMap<>();
            for (Map.Entry<Integer, double[]> entry : inflows.entrySet()) {
                double value = entry.getValue()[type.ordinal()];
                if (value != 0 && Math.signum(value) == findsign) {
                    newMap.put(entry.getKey(), value);
                }
            }
        SummedMapRankBuilder<Integer, Double> builder = new SummedMapRankBuilder<>(newMap);
        Map<Integer, Double> sorted = (findsign == 1 ? builder.sort() : builder.sortAsc()).get();

        DiscordUtil.createEmbedCommand(event.getChannel(), b -> {
            List<String> nationName = new ArrayList<>();
            List<String> amtList = new ArrayList<>();
            List<String> ppuList = new ArrayList<>();

            int i = 0;
            for (Map.Entry<Integer, Double> entry : sorted.entrySet()) {
                if (i++ >= 25) break;
                int nationId = entry.getKey();
                double amount = entry.getValue();
                double myPpu = ppu.get(nationId)[type.ordinal()];
//                nationName.add(MarkupUtil.markdownUrl(PnwUtil.getName(nationId, false), PnwUtil.getUrl(nationId, false)));
                nationName.add(PnwUtil.getName(nationId, flags.contains('a')));
                amtList.add(MathMan.format(amount));
                ppuList.add("$" + MathMan.format(myPpu));
            }
            b.addField("Nation", StringMan.join(nationName, "\n"), true);
            b.addField("Amt", StringMan.join(amtList, "\n"), true);
            b.addField("Ppu", StringMan.join(ppuList, "\n"), true);
        }, "Refresh", DiscordUtil.trimContent(event.getMessage().getContentRaw()));
        return null;
    }
}
