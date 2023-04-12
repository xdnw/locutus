package link.locutus.discord.pnw;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.core.ApiKeyPool;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.stock.Exchange;
import link.locutus.discord.commands.stock.StockDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.TextChannel;

import java.io.IOException;
import java.util.AbstractMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class NationOrExchange {
    private final DBNation nation;
    private final Exchange exchange;

    public NationOrExchange(DBNation nation) {
        this.nation = nation;
        this.exchange = null;
    }

    public NationOrExchange(Exchange exchange) {
        this.nation = null;
        this.exchange = exchange;
    }

    public boolean isNation() {
        return nation != null;
    }

    public boolean isExchange() {
        return exchange != null;
    }

    public DBNation getNation() {
        return nation;
    }

    public Exchange getExchange() {
        return exchange;
    }

    public int getId() {
        if (isNation()) return getNation().getNation_id();
        return -getExchange().id;
    }

    public String getName() {
        if (isNation()) return getNation().getNation();
        return getExchange().name;
    }

    public Map.Entry<Boolean, String> give(DBNation banker, NationOrExchange receiver, Exchange share, double amount, boolean anonymous) {
        if (receiver.getId() == getId()) return new AbstractMap.SimpleEntry<>(false, "You can't give to yourself");

        StockDB db = Locutus.imp().getStockDB();

        if (receiver.isExchange()) {
            Exchange receiverExchange = receiver.getExchange();
            if (receiverExchange.isResource() || receiverExchange.owner == 0) return new AbstractMap.SimpleEntry<>(false, "Receiver Exchange does not have an account holder");
            if (receiver.isNation() && !share.canView(receiver.getNation())) return new AbstractMap.SimpleEntry<>(false, receiverExchange.name + " does not have permission to use " + share.name);
        }

        if (!exchange.canView(banker)) return new AbstractMap.SimpleEntry<>(false, exchange.name + " requires you to be " + exchange.requiredRank + " to transfer");

        long amountLong = (long) (amount * 100);
        if (amountLong <= 0) throw new IllegalArgumentException("You cannot transfer negative amounts");
        Map<Exchange, Long> shares = db.getSharesByNation(this.getId());
        long existing = shares.getOrDefault(exchange, 0L);
        if (amountLong > existing) {
            new AbstractMap.SimpleEntry<>(false, receiver.getName() + " does not have " + MathMan.format(amount) + "x" + exchange.symbol +"! You only have: " + MathMan.format(existing / 100d));
        }

        if (db.transferShare(exchange.id, this.getId(), receiver.getId(), amountLong)) {
            // add a trade

            StringBuilder response = new StringBuilder();
            response.append("Successfully transfered " + MathMan.format(amount) + "x" + exchange.symbol);

            String title = "Funds received";
            TextChannel channel = exchange.getChannel();
            if (channel != null) title += "/" + channel.getIdLong();
            StringBuilder body = new StringBuilder();

            body.append("You have been sent the following:");
            if (!anonymous) {
                body.append("\nFrom: " + MarkupUtil.htmlUrl(this.getName(), this.getUrl()));
                if (this.isNation() && getNation().getAlliance_id() != 0) {
                    body.append(" | ");
                    body.append(MarkupUtil.htmlUrl(getNation().getAllianceName(), getNation().getAllianceUrl()));
                }
            }
            body.append("\nAmount: " + MathMan.format(amount) + "x" + exchange.symbol);
            if (!exchange.isResource()) {
                double price = db.getCombinedAveragePrice(exchange, System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7));
                if (price != 0) {
                    double value = price * amount;
                    body.append(" (worth ~$" + MathMan.format(value) + ")");
                }
            }
            body.append("\n\n<b>Your current account totals:</b>\n" + PnwUtil.sharesToString(db.getSharesByNation(receiver.getId())));
            body.append("\n\n<a href='https://discord.gg/TAF5zkh6WJ'>For more info, contact us on discord.</a>");
            body.append("\n\nNote: This is an automated message");

            if (receiver.isNation()) {
                DBNation nation = receiver.getNation();
                try {
                    nation.sendMail(ApiKeyPool.create(Locutus.imp().getRootAuth().getApiKey()), title, body.toString());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                Exchange company = receiver.getExchange();
                company.alert(title, MarkupUtil.htmlToMarkdown(body.toString()));
            }
            return new AbstractMap.SimpleEntry<>(true, response.toString());
        }
        return new AbstractMap.SimpleEntry<>(false, "Failed to transfer (Are you sure you have sufficient funds?)");
    }

    private String getUrl() {
        if (isNation()) return getNation().getNationUrl();
        return getExchange().getInvite().getUrl();
    }

    public String getUrlMarkup() {
        String name = this.getName();
        if (isNation()) {
            name += " | " + MarkupUtil.htmlUrl(getNation().getAllianceName(), getNation().getAllianceUrl());
        } else {
            name = "EX:" + name;
        }
        return MarkupUtil.htmlUrl(name, this.getUrl());
    }
}
