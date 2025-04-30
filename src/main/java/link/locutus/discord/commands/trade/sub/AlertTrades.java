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
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.apiv1.enums.ResourceType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class AlertTrades extends Command {
    public AlertTrades() {
        super("Alert-Trades", "AlertTrades", "SubscribeTrades", "Subscribe-Trades", "Alert-Trade", "AlertTrade", "SubscribeTrade", "Subscribe-Trade", "Subscribe-Trade", "SubscribeTrade", "Sub-Trade", "SubTrade",
                CommandCategory.ECON, CommandCategory.MEMBER, CommandCategory.GAME_INFO_AND_TOOLS);
    }

    @Override
    public List<CommandRef> getSlashReference() {
        return List.of(
                CM.alerts.trade.price.cmd,
                CM.alerts.trade.margin.cmd,
                CM.alerts.trade.mistrade.cmd,
                CM.alerts.trade.undercut.cmd,
                CM.alerts.trade.no_offers.cmd
        );
    }

    @Override
    public String help() {
        return Settings.commandPrefix(true) + "alert-trades <resource> <buy|sell> >/< <ppu> <duration>";
    }

    @Override
    public String desc() {
        return "Subscribe (for a duration) to get alerts about trades e.g. `" + Settings.commandPrefix(true) + "alert-trades food buy < 75 3days`\n" +
                "- Where `BUY` = BUYER WANTED page, and `SELL` = SELLER WANTED page";
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() != 5) {
            return usage(args.size(), 5, channel);
        }
        ResourceType resource;
        try {
            resource = ResourceType.parse(args.get(0));
        } catch (IllegalArgumentException e) {
            return "Invalid resource type: `" + args.get(0) + "`" + ". Valid values are: " + StringMan.getString(ResourceType.values);
        }

        boolean isBuy = args.get(1).equalsIgnoreCase("buy");
        if (!isBuy && !args.get(1).equalsIgnoreCase("sell")) {
            return "Invalid category `" + args.get(1) + "`" + ". Must be either `buy` or `sell`";
        }

        boolean above;
        int offset = 0;

        switch (args.get(2)) {
            case ">":
                above = true;
                break;
            case ">=":
                offset = -1;
                above = true;
                break;
            case "<":
                above = false;
                break;
            case "<=":
                above = false;
                offset = 1;
                break;
            default:
                return "Invalid conditional: `" + args.get(2) + "`" + ". Must be either `>` or `<`";
        }

        Integer ppu = MathMan.parseInt(args.get(3));
        if (ppu == null) {
            return "Invalid ppu (number): `" + args.get(3) + "`";
        }
        ppu += offset;

        long now = System.currentTimeMillis();
        long msOffset = TimeUtil.timeToSec(args.get(4)) * 1000;
        long date = now + msOffset;
        if (msOffset > TimeUnit.DAYS.toMillis(30)) {
            return "You can only subscribe for a maximum of 30 days";
        }

        TradeDB db = Locutus.imp().getTradeManager().getTradeDb();
        User user = author;

        db.subscribe(user, resource, date, isBuy, above, ppu, TradeDB.TradeAlertType.ABSOLUTE);

        return "Subscribed to `" + DiscordUtil.trimContent(fullCommandRaw).toUpperCase() + "`" +
                "\nCheck your subscriptions with: `" + Settings.commandPrefix(true) + "trade-subs`";
    }
}
