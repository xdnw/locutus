package com.boydti.discord.commands.sheets;

import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.commands.manager.CommandCategory;
import com.boydti.discord.user.Roles;
import com.boydti.discord.util.MathMan;
import com.boydti.discord.util.PnwUtil;
import com.boydti.discord.util.trade.OptimalTradeTask;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;

public class OptimalTrades extends Command {
    public OptimalTrades() {
        super("optimaltrades", CommandCategory.ECON, CommandCategory.GENERAL_INFO_AND_TOOLS);
    }

    @Override
    public String help() {
        return "!optimaltrades <investment> [days]";
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
    public String onCommand(MessageReceivedEvent event, List<String> args) throws Exception {
        if (args.isEmpty() || args.size() > 2) {
            return usage(event);
        }
        Double investment = MathMan.parseDouble(args.get(0));
        Integer days = MathMan.parseInt(args.size() == 1 ? null : args.get(1));

        if (investment == null) return "Invalid number: `" + args.get(0) + "`";
        if (days == null) return "Invalid number: `" + args.get(1) + "`";

        OptimalTradeTask task = new OptimalTradeTask(investment, days);
        Long profit = task.call();

        StringBuilder response = new StringBuilder();

        response.append('\n').append("Output: <" + task.getSheet().getURL() + ">");
        response.append('\n').append("Max Buy Threshold: ```" + PnwUtil.resourcesToString(task.getMaxPrice()) + "```");
        response.append('\n').append("Min Sell Threshold: ```" + PnwUtil.resourcesToString(task.getMinPrice()) + "```");
        response.append('\n').append("Profit: ").append(profit);
        response.append('\n').append("Note: Actual exploitation is probably closer to 50% (depending on activity. This is where the alerts will come in handy)");

        return response.toString();
    }
}