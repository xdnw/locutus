package com.boydti.discord.commands.trade;

import com.boydti.discord.Locutus;
import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.commands.manager.CommandCategory;
import com.boydti.discord.util.MathMan;
import com.boydti.discord.util.StringMan;
import com.boydti.discord.util.trade.Offer;
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

        List<Offer> offers = new ArrayList<>();
        for (String idStr : args.get(0).split(",")) {
            Integer id = MathMan.parseInt(idStr);
            if (id == null) return "Invalid trade id: " + idStr;
            Offer trade = Locutus.imp().getTradeManager().getTradeDb().getOffer(id);
            if (trade != null) offers.add(trade);
        }
        return " - " + StringMan.join(offers, "\n - ");
    }
}
