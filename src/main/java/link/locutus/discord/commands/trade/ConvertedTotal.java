package link.locutus.discord.commands.trade;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PW;
import link.locutus.discord.apiv1.enums.ResourceType;
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
    public List<CommandRef> getSlashReference() {
        return List.of(CM.trade.value.cmd);
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
        return """
                Get the total monetary value of the provided resources.
                Add `-n` to normalize the resources (i.e. remove negatives and multiply by a factor so it is worth the same amount)
                Add `-b` to use current market buy price (instead of average)
                Add `-s` to use current market sell price (instead of average)""";
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() != 1 || args.get(0).isEmpty()) return usage(args.size(), 1, channel);

        Map<ResourceType, Double> transfer = ResourceType.parseResources(args.get(0));

        boolean showWorth = true;

        if (flags.contains('n')) {
            double total = ResourceType.convertedTotal(transfer);
            if (total <= 0) {
                return "Total is negative";
            }

            double negativeTotal = 0;

            Iterator<Map.Entry<ResourceType, Double>> iter = transfer.entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry<ResourceType, Double> entry = iter.next();
                if (entry.getValue() < 0) {
                    negativeTotal += Locutus.imp().getTradeManager().getHigh(entry.getKey()) * entry.getValue() * -1;
                    iter.remove();
                }
            }
            double postiveTotal = ResourceType.convertedTotal(transfer);

            double factor = total / postiveTotal;

            for (ResourceType type : ResourceType.values()) {
                Double value = transfer.get(type);
                if (value == null || value == 0) continue;

                transfer.put(type, value * factor);
            }
        }

        StringBuilder result = new StringBuilder("```" + ResourceType.toString(transfer) + "```");

        double value = ResourceType.convertedTotal(transfer);
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