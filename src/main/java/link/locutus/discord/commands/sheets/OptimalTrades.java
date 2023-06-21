package link.locutus.discord.commands.sheets;

import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.trade.OptimalTradeTask;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;
import java.util.Set;

public class OptimalTrades extends Command {
    public OptimalTrades() {
        super("optimaltrades", CommandCategory.ECON, CommandCategory.GENERAL_INFO_AND_TOOLS);
    }

    @Override
    public String help() {
        return Settings.commandPrefix(true) + "optimaltrades <investment> [days]";
    }

    @Override
    public String desc() {
        return "Calculate the optimal trades to make the following week";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return super.checkPermission(server, user);
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.isEmpty() || args.size() > 2) {
            return usage(args.size(), 1, 2, channel);
        }
        Double investment = MathMan.parseDouble(args.get(0));
        Integer days = MathMan.parseInt(args.size() == 1 ? null : args.get(1));

        if (investment == null) return "Invalid number: `" + args.get(0) + "`";
        if (days == null) return "Invalid number: `" + args.get(1) + "`";

        OptimalTradeTask task = new OptimalTradeTask(investment, days);
        Long profit = task.call();

        StringBuilder response = new StringBuilder();

        IMessageBuilder msg = channel.create();
        task.getSheet().attach(msg, response, false, 0);
        response.append('\n').append("Max Buy Threshold: ```" + PnwUtil.resourcesToString(task.getMaxPrice()) + "```");
        response.append('\n').append("Min Sell Threshold: ```" + PnwUtil.resourcesToString(task.getMinPrice()) + "```");
        response.append('\n').append("Profit: ").append(profit);
        response.append('\n').append("Note: Actual exploitation is probably closer to 50% (depending on activity. This is where the alerts will come in handy)");

        msg.append(response.toString()).send();
        return null;
    }
}