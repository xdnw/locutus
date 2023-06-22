package link.locutus.discord.commands.trade.subbank;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.BankDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.TimeUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.Date;
import java.util.List;
import java.util.Set;

public class BankSubscriptions extends Command {
    public BankSubscriptions() {
        super("BankSubscriptions", "Bank-Subscriptions", "BankSubs", "Bank-Subs", "Bank-Alerts", "BankAlerts");
    }

    @Override
    public String desc() {
        return "View your trade alert subscriptions";
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        Set<BankDB.Subscription> subscriptions = Locutus.imp().getBankDB().getSubscriptions(author.getIdLong());
        if (subscriptions.isEmpty()) {
            return "No subscriptions. Subscribe to get alerts using `" + Settings.commandPrefix(true) + "alert-bank`";
        }

        for (BankDB.Subscription sub : subscriptions) {
            String name;
            String url;
            if (sub.allianceOrNation == 0) {
                name = "*";
                if (sub.type == BankDB.BankSubType.ALL) {
                    url = name;
                } else {
                    String type = sub.type == BankDB.BankSubType.NATION ? "nation" : "alliance";
                    url = "" + Settings.INSTANCE.PNW_URL() + "/" + type + "/id=" + sub.allianceOrNation;
                }
            } else if (sub.type == BankDB.BankSubType.NATION) {
                DBNation nation = Locutus.imp().getNationDB().getNation(sub.allianceOrNation);
                url = "" + Settings.INSTANCE.PNW_URL() + "/nation/id=" + sub.allianceOrNation;
                name = String.format("[%s](%s)",
                        nation == null ? sub.allianceOrNation : nation.getNation(), url);
            } else {
                String aaName = Locutus.imp().getNationDB().getAllianceName(sub.allianceOrNation);
                if (aaName == null) aaName = sub.allianceOrNation + "";
                url = "" + Settings.INSTANCE.PNW_URL() + "/alliance/id=" + sub.allianceOrNation;
                name = String.format("[%s](%s)",
                        aaName, url);
            }
            String dateStr = TimeUtil.YYYY_MM_DD_HH_MM_SS.format(new Date(sub.endDate)) + " (UTC)";
            String sendReceive = sub.isReceive ? "received" : "deposited";

            String title = name + " " + sendReceive + " > " + MathMan.format(sub.amount);

            StringBuilder body = new StringBuilder();
            body.append("Expires " + dateStr);

            String emoji = "Unsubscribe";
            String unsubCommand = Settings.commandPrefix(true) + "UnsubBank " + url;

            channel.create().embed(title, body.toString())
                            .commandButton(unsubCommand, emoji).send();
        }

        if (subscriptions.isEmpty()) {
            return "No subscriptions";
        }
        return null;
    }
}
