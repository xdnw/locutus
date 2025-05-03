package link.locutus.discord.commands.trade;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DBTrade;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.PW;
import com.google.common.collect.Maps;
import link.locutus.discord.apiv1.enums.ResourceType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class MoneyTrades extends Command {
    public MoneyTrades() {
        super("moneytrades", "trades", CommandCategory.ECON, CommandCategory.GAME_INFO_AND_TOOLS);
    }

    @Override
    public List<CommandRef> getSlashReference() {
        return List.of(CM.trade.moneyTrades.cmd);
    }

    @Override
    public String help() {
        return Settings.commandPrefix(true) + "trades <nation> <time>";
    }

    @Override
    public String desc() {
        return "View an accumulation of all the net money trades a nation made, grouped by nation.";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() != 2) return usage(args.size(), 2, channel);
        DBNation nation = DiscordUtil.parseNation(args.get(0), true, true);
        int nationId = nation.getId();
        long timeDiff = TimeUtil.timeToSec(args.get(1)) * 1000L;
        if (timeDiff == 0) return "Invalid time: `" + args.get(1) + "`";
        long cuttOff = System.currentTimeMillis() - timeDiff;

        if (flags.contains('f')) {
            Locutus.imp().runEventsAsync(Locutus.imp().getTradeManager()::updateTradeList);
        }

        Map<Integer, Map<ResourceType, Long>> netInflows = new Int2ObjectOpenHashMap<>();

        List<DBTrade> trades = Locutus.imp().getTradeManager().getTradeDb().getTrades(nation.getId(), cuttOff);
        for ( DBTrade offer : trades) {
            if (offer.getResource() == ResourceType.CREDITS) continue;
            int max = offer.getResource() == ResourceType.FOOD ? 1000 : 10000;
            if (offer.getPpu() > 1 && offer.getPpu() < max) continue;

            int sign = offer.isBuy() ? -1 : 1;
            int per = offer.getPpu();

            Integer client = (offer.getSeller() == (nationId)) ? offer.getBuyer() : offer.getSeller();

            Map<ResourceType, Long> existing = netInflows.computeIfAbsent(client,  integer -> Maps.newLinkedHashMap());

            if (per <= 1) {
                existing.put(offer.getResource(), (long) (offer.getQuantity() * sign + existing.getOrDefault(offer.getResource(), 0L)));
            } else {
                existing.put(ResourceType.MONEY, (long) (sign * offer.getTotal()) + existing.getOrDefault(ResourceType.MONEY, 0L));
            }
        }

        if (netInflows.isEmpty()) return "No trades found for `" + nationId + "` in the past " + TimeUtil.secToTime(TimeUnit.MILLISECONDS, timeDiff);

        StringBuilder response = new StringBuilder("Your net inflows from:");
        for (Map.Entry<Integer, Map<ResourceType, Long>> entry : netInflows.entrySet()) {
            Integer clientId = entry.getKey();
            DBNation client = Locutus.imp().getNationDB().getNationById(clientId);
            String name = PW.getName(clientId, false);
            if (flags.contains('a')) {
                response.append("\n**" + name);
                if (client != null) response.append(" | " + client.getAllianceName());
                response.append(":**\n");
                String url = Settings.PNW_URL() + "/nation/id=" + clientId;
                response.append("```" + Settings.commandPrefix(true) + "addbalance " + url + " " + ResourceType.toString(entry.getValue()) + " #deposit```");
            } else {
                response.append('\n').append("```").append(name).append(" | ");
                if (client != null && client.getAlliance_id() != 0) {
                    response.append(String.format("%16s", client.getAllianceName()));
                }
                response.append(String.format("%16s", ResourceType.toString(entry.getValue())))
                        .append("```");
            }

        }
        return response.toString().trim();
    }
}
