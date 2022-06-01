package link.locutus.discord.commands.trade.sub;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.db.TradeDB;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.apiv1.enums.ResourceType;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;

public class UnsubTrade extends Command {
    public UnsubTrade() {
        super("UnsubTrade", "Unsub-Trade", "UnsubscribeTrade", "Unsubscribe-Trade",
                CommandCategory.ECON, CommandCategory.MEMBER, CommandCategory.GAME_INFO_AND_TOOLS);
    }

    @Override
    public String help() {
        return "!unsub-trade <resource>";
    }

    @Override
    public String desc() {
        return "Unsubscribe from trade alerts. Available resources: " + StringMan.getString(ResourceType.values);
    }

    @Override
    public String onCommand(MessageReceivedEvent event, List<String> args) throws Exception {
        if (args.isEmpty()) {
            return usage(event);
        }
        TradeDB db = Locutus.imp().getTradeManager().getTradeDb();
        ResourceType type = ResourceType.parse(args.get(0));

        db.unsubscribe(event.getAuthor(), type);
        return "Unsubscribed from " + type + " alerts";
    }
}
