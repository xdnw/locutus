package link.locutus.discord.commands.trade.sub;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.TradeDB;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.apiv1.enums.ResourceType;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.Date;
import java.util.List;
import java.util.Set;

public class TradeSubscriptions extends Command {
    public TradeSubscriptions() {
        super("TradeSubscriptions", "Trade-Subscriptions", "TradeSubs", "Trade-Subs",
                CommandCategory.ECON, CommandCategory.MEMBER, CommandCategory.GAME_INFO_AND_TOOLS);
    }

    @Override
    public String desc() {
        return "View your trade alert subscriptions";
    }

    @Override
    public String onCommand(MessageReceivedEvent event, List<String> args) throws Exception {
        Set<TradeDB.Subscription> subscriptions = Locutus.imp().getTradeManager().getTradeDb().getSubscriptions(event.getAuthor().getIdLong());
        if (subscriptions.isEmpty()) {
            return "No subscriptions. Subscribe to get alerts using `" + Settings.commandPrefix(true) + "alert-trade`";
        }

        for (ResourceType type : ResourceType.values) {
            String title = type.name();
            StringBuilder body = new StringBuilder();

            for (TradeDB.Subscription subscription : subscriptions) {
                if (subscription.resource == type) {
                    String buySell = subscription.isBuy ? "Buy" : "Sell";
                    String operator = subscription.above ? ">" : "<";

                    String msg = buySell + " " + subscription.resource.name().toLowerCase() + " " + operator + " " + subscription.ppu;

                    body.append('\n').append(msg);
                    String dateStr = TimeUtil.YYYY_MM_DD_HH_MM_SS.format(new Date(subscription.endDate)) + " (UTC)";
                    body.append(" until ").append(dateStr);
                }
            }
            if (body.length() == 0) continue;

            String emoji = "\u274c";
            String unsubCommand = Settings.commandPrefix(true) + "unsub-trade " + type.name();

            body.append("\n\n").append("*Press " + emoji + " to unsubscribe*");

            DiscordUtil.createEmbedCommand(event.getChannel(), title, body.toString(), emoji, unsubCommand);
        }

        return null;
    }
}
