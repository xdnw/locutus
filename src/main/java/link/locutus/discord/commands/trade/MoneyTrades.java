package link.locutus.discord.commands.trade;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.trade.Offer;
import com.google.common.collect.Maps;
import link.locutus.discord.apiv1.enums.ResourceType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

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
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() != 2) return usage(event);
        Integer user = DiscordUtil.parseNationId(args.get(0));
        if (user == null) {
            return "invalid user `" + args.get(0) + "`";
        }
        long timeDiff = TimeUtil.timeToSec(args.get(1)) * 1000L;
        if (timeDiff == 0) return "Invalid time: `" + args.get(1) + "`";
        long cuttOff = System.currentTimeMillis() - timeDiff;

        if (flags.contains('f')) {
            Locutus.imp().getTradeManager().updateTradeList(false, true);
        }

        Map<Integer, Map<ResourceType, Long>> netInflows = new HashMap<>();

        List<Offer> trades = Locutus.imp().getTradeManager().getTradeDb().getOffers(user, cuttOff);
        for (Offer offer : trades) {
            if (offer.getResource() == ResourceType.CREDITS) continue;
            int max = offer.getResource() == ResourceType.FOOD ? 1000 : 10000;
            if (offer.getPpu() > 1 && offer.getPpu() < max) continue;

            int sign = offer.isBuy() ? -1 : 1;
            int per = offer.getPpu();

            Integer client = (offer.getSeller().equals(user)) ? offer.getBuyer() : offer.getSeller();

            Map<ResourceType, Long> existing = netInflows.computeIfAbsent(client,  integer -> Maps.newLinkedHashMap());

            if (per <= 1) {
                existing.put(offer.getResource(), (long) (offer.getAmount() * sign + existing.getOrDefault(offer.getResource(), 0L)));
            } else {
                existing.put(ResourceType.MONEY, (long) (sign * offer.getTotal()) + existing.getOrDefault(ResourceType.MONEY, 0L));
            }
        }

        if (netInflows.isEmpty()) return "No trades found for " + user + " in the past " + TimeUtil.secToTime(TimeUnit.MILLISECONDS, timeDiff);

        StringBuilder response = new StringBuilder("Your net inflows from:");
        for (Map.Entry<Integer, Map<ResourceType, Long>> entry : netInflows.entrySet()) {
            Integer clientId = entry.getKey();
            DBNation client = Locutus.imp().getNationDB().getNation(clientId);
            String name = PnwUtil.getName(clientId, false);
            if (flags.contains('a')) {
                response.append("\n**" + name);
                if (client != null) response.append(" | " + client.getAllianceName());
                response.append(":**\n");
                String url = "" + Settings.INSTANCE.PNW_URL() + "/nation/id=" + clientId;
                response.append("```" + Settings.commandPrefix(true) + "addbalance " + url + " " + PnwUtil.resourcesToString(entry.getValue()) + " #deposit```");
            } else {
                response.append('\n').append("```").append(name).append(" | ");
                if (client != null && client.getAlliance_id() != 0) {
                    response.append(String.format("%16s", client.getAllianceName()));
                }
                response.append(String.format("%16s", PnwUtil.resourcesToString(entry.getValue())))
                        .append("```");
            }

        }
        return response.toString().trim();
    }
}
