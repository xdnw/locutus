package com.boydti.discord.commands.trade.sub;

import com.boydti.discord.Locutus;
import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.commands.manager.CommandCategory;
import com.boydti.discord.db.TradeDB;
import com.boydti.discord.util.StringMan;
import com.boydti.discord.apiv1.enums.ResourceType;
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
