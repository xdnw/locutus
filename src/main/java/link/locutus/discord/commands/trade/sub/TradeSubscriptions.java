package link.locutus.discord.commands.trade.sub;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.TradeDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.TradeSubscription;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.apiv1.enums.ResourceType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
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
    public List<CommandRef> getSlashReference() {
        return List.of(CM.alerts.trade.list.cmd);
    }

    @Override
    public String desc() {
        return "View your trade alert subscriptions";
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        List<TradeSubscription> subscriptions = Locutus.imp().getTradeManager().getTradeDb().getSubscriptions(author.getIdLong());
        if (subscriptions.isEmpty()) {
            return "No subscriptions. Subscribe to get alerts using `" + Settings.commandPrefix(true) + "alert-trade`";
        }

        for (ResourceType type : ResourceType.values) {
            String title = type.name();
            StringBuilder body = new StringBuilder();

            for (TradeSubscription subscription : subscriptions) {
                if (subscription.getResource() == type) {
                    String buySell = subscription.isBuy() ? "Buy" : "Sell";
                    String operator = subscription.isAbove() ? ">" : "<";

                    String msg = buySell + " " + subscription.getResource().name().toLowerCase() + " " + operator + " " + subscription.getPpu();

                    body.append('\n').append(msg);
                    String dateStr = TimeUtil.YYYY_MM_DD_HH_MM_SS.format(new Date(subscription.getDate())) + " (UTC)";
                    body.append(" until ").append(dateStr);
                }
            }
            if (body.length() == 0) continue;

            String emoji = "Unsubscribe";
            String unsubCommand = Settings.commandPrefix(true) + "unsub-trade " + type.name();

            body.append("\n\n").append("*Press `" + emoji + "` to unsubscribe*");

            channel.create().embed(title, body.toString()).commandButton(unsubCommand, emoji).send();
        }

        return null;
    }
}
