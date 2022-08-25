package link.locutus.discord.util.update;

import com.google.common.eventbus.Subscribe;
import com.politicsandwar.graphql.model.TradeType;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.TradeDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DBTrade;
import link.locutus.discord.db.entities.TradeSubscription;
import link.locutus.discord.event.trade.BulkTradeSubscriptionEvent;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.Role;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class TradeListener {

    @Subscribe
    public void onTradeSubscription(BulkTradeSubscriptionEvent event) {
        List<TradeSubscription> subscriptions = event.getSubscriptions();

        Map<GuildDB, Map<ResourceType, List<TradeSubscription>>> guildIdToUserSubscription = new HashMap<>();

        for (TradeSubscription subscription : subscriptions) {
            if (subscription.isRole()) {
                for (GuildDB db : Locutus.imp().getGuildDatabases().values()) {
                    MessageChannel channel = db.getOrNull(GuildDB.Key.TRADE_ALERT_CHANNEL);
                    if (channel == null) continue;
                    Role role = subscription.toRole(db);
                    if (role != null) {
                        guildIdToUserSubscription.computeIfAbsent(db, k -> new HashMap<>())
                                .computeIfAbsent(subscription.getResource(), f -> new ArrayList<>())
                                .add(subscription);
                    }
                }

            } else {
                long user = subscription.getUser();
                DBNation nation = DiscordUtil.getNation(user);
                if (nation == null) continue;
                GuildDB db = nation.getGuildDB();
                if (db != null) {
                    guildIdToUserSubscription.computeIfAbsent(db, k -> new HashMap<>())
                            .computeIfAbsent(subscription.getResource(), f -> new ArrayList<>())
                            .add(subscription);
                }
            }
        }

        for (Map.Entry<GuildDB, Map<ResourceType, List<TradeSubscription>>> guildEntry : guildIdToUserSubscription.entrySet()) {
            GuildDB db = guildEntry.getKey();
            Map<ResourceType, List<TradeSubscription>> subscriptionsForGuild = guildEntry.getValue();

            // get trade alert channel
            MessageChannel channel = db.getOrNull(GuildDB.Key.TRADE_ALERT_CHANNEL);
            if (channel == null) continue;

            for (Map.Entry<ResourceType, List<TradeSubscription>> rssEntry : subscriptionsForGuild.entrySet()) {
                ResourceType resource = rssEntry.getKey();
                List<TradeSubscription> rssSubs = rssEntry.getValue();

                Set<TradeDB.TradeAlertType> types = rssSubs.stream().map(f -> f.getType()).collect(Collectors.toSet());

                String title = resource + " Alert: " + StringMan.getString(types);

                StringBuilder body = new StringBuilder();

                DBTrade oldSell = event.getPreviousTop(false);
                DBTrade oldBuy = event.getPreviousTop(true);
                DBTrade newSell = event.getCurrentTop(false);
                DBTrade newBuy = event.getCurrentTop(true);

                boolean sellChange = !Objects.equals(oldSell, newSell);
                boolean buyChange = !Objects.equals(oldBuy, newBuy);

                if (sellChange) body.append("**");
                body.append(MarkupUtil.markdownUrl("Selling", resource.url(false, true)) + ": ");
                body.append(toPriceString(oldSell) + " -> " + toPriceString(newSell));
                if (sellChange) body.append("**");
                body.append("\n");

                body.append("\n\n");

                if (buyChange) body.append("**");
                body.append(MarkupUtil.markdownUrl("Buying", resource.url(true, true)) + ": ");
                body.append(toPriceString(oldBuy) + " -> " + toPriceString(newBuy));
                if (buyChange) body.append("**");

                List<String> pings = new ArrayList<>();
                for (TradeSubscription sub : rssSubs) {
                    String ping = sub.getPing(db) + " " + sub.getType() + " " + (sub.isBuy() ? "buying" : "selling") + " " + (sub.isAbove() ? "above" : "below") + " $" + sub.getPpu() + " (expires: " + TimeUtil.secToTime(TimeUnit.MILLISECONDS, sub.getDate() - System.currentTimeMillis()) + ")";
                    pings.add(ping);
                }

                Message message = new MessageBuilder()
                        .setEmbeds(new EmbedBuilder().setTitle(title).appendDescription(body.toString()).build())
                        .setContent(StringMan.join(pings, "\n")).build();
                RateLimitUtil.queue(channel.sendMessage(message));
            }
        }
    }

    private String toPriceString(DBTrade trade) {
        if (trade == null) return "(none)";
        return trade.getPpu() + "ppu (" + trade.getQuantity() + "x)";
    }
}
