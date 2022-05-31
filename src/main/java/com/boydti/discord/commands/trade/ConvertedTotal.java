package com.boydti.discord.commands.trade;

import com.boydti.discord.Locutus;
import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.commands.manager.CommandCategory;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.util.MathMan;
import com.boydti.discord.util.PnwUtil;
import com.boydti.discord.apiv1.enums.ResourceType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ConvertedTotal extends Command {
    public ConvertedTotal() {
        super("resourcevalue", "convertedtotal", CommandCategory.ECON, CommandCategory.GAME_INFO_AND_TOOLS);
    }

    @Override
    public String help() {
        return super.help() + " <resources-json>";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }


    @Override
    public String desc() {
        return "Get the total monetary value of the provided resources.\n" +
                "Add `-n` to normalize the resources (i.e. remove negatives and multiply by a factor so it is worth the same amount)\n" +
                "Add `-b` to use current market buy price (instead of average)\n" +
                "Add `-s` to use current market sell price (instead of average)";
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() != 1 || args.get(0).isEmpty()) return usage(event);

        Map<ResourceType, Double> transfer = PnwUtil.parseResources(args.get(0));

        boolean showWorth = true;

        if (flags.contains('n')) {
            double total = PnwUtil.convertedTotal(transfer);
            if (total <= 0) {
                return "Total is negative";
            }

            double negativeTotal = 0;

            Iterator<Map.Entry<ResourceType, Double>> iter = transfer.entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry<ResourceType, Double> entry = iter.next();
                if (entry.getValue() < 0) {
                    negativeTotal += Locutus.imp().getTradeManager().getHigh(entry.getKey()) * entry.getValue().doubleValue() * -1;
                    iter.remove();
                }
            }
            double postiveTotal = PnwUtil.convertedTotal(transfer);

            double factor = total / postiveTotal;

            for (ResourceType type : ResourceType.values()) {
                Double value = transfer.get(type);
                if (value == null || value == 0) continue;

                transfer.put(type, value * factor);
            }
        }

        StringBuilder result = new StringBuilder("```" + PnwUtil.resourcesToString(transfer) + "```");

        double value = PnwUtil.convertedTotal(transfer);
        if (flags.contains('b') || flags.contains('s')) {
            value = 0;
            boolean buy = flags.contains('b');
            for (Map.Entry<ResourceType, Double> entry : transfer.entrySet()) {
                int price = Locutus.imp().getTradeManager().getPrice(entry.getKey(), buy);
                value += price * entry.getValue();
            }
        }

        if (showWorth) {
            result.append("\n" + "Worth: $" + MathMan.format(value));
        }

        return result.toString();
    }
}