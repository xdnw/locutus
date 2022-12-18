package link.locutus.discord.commands.trade;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.db.entities.DBTrade;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.StringMan;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.ArrayList;
import java.util.List;

public class TradeId extends Command {
    public TradeId() {
        super(CommandCategory.DEBUG);
    }
    @Override
    public String help() {
        return super.help() + " <trade ids>";
    }

    @Override
    public String desc() {
        return "Output raw info on a trade, given a provided trade_id";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String onCommand(MessageReceivedEvent event, List<String> args) throws Exception {
        if (args.size() != 1) return usage(event);

        List<DBTrade> offers = new ArrayList<>();
        for (String idStr : args.get(0).split(",")) {
            Integer id = MathMan.parseInt(idStr);
            if (id == null) return "Invalid trade id: " + idStr;
            DBTrade trade = Locutus.imp().getTradeManager().getTradeDb().getTradeById(id);
            if (trade != null) offers.add(trade);
        }
        return " - " + StringMan.join(offers, "\n - ");
    }
}
