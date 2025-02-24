package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.annotation.*;
import link.locutus.discord.commands.manager.v2.binding.annotation.Timestamp;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.HasOffshore;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.IsAlliance;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.WhitelistPermission;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.manager.v2.builder.SummedMapRankBuilder;
import link.locutus.discord.commands.manager.v2.table.imp.RssTradeByDay;
import link.locutus.discord.commands.manager.v2.table.imp.StockpileValueByDay;
import link.locutus.discord.commands.manager.v2.table.imp.TradeMarginByDay;
import link.locutus.discord.commands.manager.v2.table.imp.TradePriceByDay;
import link.locutus.discord.commands.trade.TradeRanking;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.TradeDB;
import link.locutus.discord.db.entities.*;
import link.locutus.discord.db.guild.SheetKey;
import link.locutus.discord.pnw.NationOrAllianceOrGuild;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.math.ArrayUtil;
import link.locutus.discord.util.offshore.OffshoreInstance;
import link.locutus.discord.util.sheet.SpreadSheet;
import link.locutus.discord.util.trade.TradeManager;
import com.google.common.collect.Maps;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.web.WebUtil;
import link.locutus.discord.web.commands.WM;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import org.apache.commons.collections4.CollectionUtils;
import org.json.JSONObject;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;
import java.util.stream.Collectors;

public class TradeCommands {

    public static final long BULK_TRADE_SERVER = 672217848311054346L; // 1080313938937389207L
    public static final long BULK_TRADE_CHANNEL = 672310912090243092L; // 1080573769048932372L

//    @RolePermission(value=Roles.MEMBER, guild=BULK_TRADE_SERVER)
//    @Command(desc = "List the bot offers nations have on discord for you selling a given resource")
//    public String topList(TradeManager tMan, @Me DBNation me) {
//        return null;
//    }

    @RolePermission(value=Roles.MEMBER, guild=BULK_TRADE_SERVER)
    @Command(desc = "List the bot trade offers you have on discord", viewable = true)
    public String myOffers(TradeManager tMan, @Me DBNation me) {
        Set<TradeDB.BulkTradeOffer> offers = tMan.getBulkOffers(f -> f.nation == me.getNation_id());
        if (offers.isEmpty()) {
            return "You have no offers";
        }
        StringBuilder response = new StringBuilder();
        response.append("**" + me.getNation() + " has " + offers.size() + " bulk trade offers:**\n");
        for (TradeDB.BulkTradeOffer offer : offers) {
            response.append(offer.toSimpleString() + "\n");
        }
        return response.toString();
    }

    @RolePermission(value=Roles.MEMBER, guild=BULK_TRADE_SERVER)
    @Command(desc = "List the bot offers nations have on discord for you selling a given resource", viewable = true)
    public String sellList(TradeManager tMan,
                           ResourceType youSell,
                           @Default("MONEY") ResourceType youReceive,
                           @Default Set<DBNation> allowedTraders,
                           @Arg("Sort the offers by the lowest minimum offer price\n" +
                                   "Comparison prices for resources are converted to weekly average cash equivalent")
                           @Switch("l") boolean sortByLowestMinPrice,
                           @Arg("Sort the offers by the lowest maximum offer price\n" +
                                   "Comparison prices for resources are converted to weekly average cash equivalent")
                           @Switch("h") boolean sortByLowestMaxPrice) {
        if (sortByLowestMaxPrice && sortByLowestMinPrice) {
            return "You can't sort by both lowest min and max price (pick one)";
        }
        Set<ResourceType> youSellSet = Collections.singleton(youSell);
        Set<ResourceType> youReceiveSet = Collections.singleton(youReceive);
        Set<TradeDB.BulkTradeOffer> offers = tMan.getBulkOffers(youSell, f ->
                CollectionUtils.containsAny(f.getSelling(), youReceiveSet) &&
                        CollectionUtils.containsAny(f.getBuying(), youSellSet)
                        && (allowedTraders == null || allowedTraders.contains(f.getNation()))
        );
        if (offers.isEmpty()) {
            return "No offers found";
        }

        List<TradeDB.BulkTradeOffer> offersSorted = new ArrayList<>(offers);
        if (sortByLowestMaxPrice) {
            offersSorted.sort(Comparator.comparingDouble(f -> f.getPriceRange(youReceive, youSell).getValue()));
        } else if (sortByLowestMinPrice) {
            offersSorted.sort(Comparator.comparingDouble(f -> f.getPriceRange(youReceive, youSell).getKey()));
        } else {
            offersSorted.sort(Comparator.comparingDouble(f -> average(f.getPriceRange(youReceive, youSell))));
        }
        StringBuilder response = new StringBuilder("**" + offers.size() + " offers found:**\n");
        for (TradeDB.BulkTradeOffer offer : offersSorted) {
            response.append(offer.toSimpleString(youSell, youReceive, sortByLowestMinPrice, sortByLowestMaxPrice) + "\n");
        }
        return response.toString();
    }

    private double average(Map.Entry<Double, Double> pair) {
        return (pair.getKey() + pair.getValue()) / 2;
    }

    @RolePermission(value=Roles.MEMBER, guild=BULK_TRADE_SERVER)
    @Command(desc = "List the bot offers nations have on discord for you buying a given resource", viewable = true)
    public String buyList(TradeManager tMan, ResourceType youBuy, @Default("MONEY") ResourceType youProvide, @Default Set<DBNation> allowedTraders,
                          @Arg("Sort the offers by the lowest minimum offer price\n" +
                                  "Comparison prices for resources are converted to weekly average cash equivalent")
                          @Switch("l") boolean sortByLowestMinPrice,
                          @Arg("Sort the offers by the lowest maximum offer price\n" +
                                  "Comparison prices for resources are converted to weekly average cash equivalent")
                          @Switch("h") boolean sortByLowestMaxPrice) {
        if (sortByLowestMaxPrice && sortByLowestMinPrice) {
            return "You can't sort by both lowest min and max price (pick one)";
        }
        Set<ResourceType> youSellSet = Collections.singleton(youProvide);
        Set<ResourceType> youReceiveSet = Collections.singleton(youBuy);
        Set<TradeDB.BulkTradeOffer> offers = tMan.getBulkOffers(youBuy, f ->
                CollectionUtils.containsAny(f.getSelling(), youReceiveSet) &&
                        CollectionUtils.containsAny(f.getBuying(), youSellSet)
                        && (allowedTraders == null || allowedTraders.contains(f.getNation()))
        );
        if (offers.isEmpty()) {
            return "No offers found";
        }

        List<TradeDB.BulkTradeOffer> offersSorted = new ArrayList<>(offers);
        if (sortByLowestMaxPrice) {
            offersSorted.sort(Comparator.comparingDouble(f -> f.getPriceRange(youBuy, youProvide).getValue()));
        } else if (sortByLowestMinPrice) {
            offersSorted.sort(Comparator.comparingDouble(f -> f.getPriceRange(youBuy, youProvide).getKey()));
        } else {
            offersSorted.sort(Comparator.comparingDouble(f -> average(f.getPriceRange(youBuy, youProvide))));
        }
        StringBuilder response = new StringBuilder("**" + offers.size() + " offers found:**\n");
        for (TradeDB.BulkTradeOffer offer : offersSorted) {
            response.append(offer.toSimpleString(youProvide, youBuy, sortByLowestMinPrice, sortByLowestMaxPrice) + "\n");
        }
        return response.toString();
    }

    @RolePermission(value=Roles.MEMBER, guild=BULK_TRADE_SERVER)
    @Command(desc = "Update one of your bot trade offers on discord")
    @HasOffshore
    public String updateOffer(@Me JSONObject command, TradeManager tMan, @Me DBNation me, @Me IMessageIO io,
                              @Arg("The id of your trade offer")
                              int offerId,
                              @Arg("The quantity of the resource you are exchanging")
                              Long quantity,
                              @Arg("The minimum price per unit you are exchanging for")
                              @Switch("minPPU") Integer minPPU,
                              @Arg("The maximum price per unit you are exchanging for")
                              @Switch("maxPPU") Integer maxPPU,
                              @Arg("If prices are negotiable")
                              @Switch("n") Boolean negotiable,
                              @Arg("When the offer is no longer available")
                              @Switch("e") @Timediff Long expire,
                              @Arg("The resources you will accept in return")
                              @Switch("x") Set<ResourceType> exchangeFor,
                              @Arg("The equivalent price per unit you will accept for each resource")
                              @Switch("p") Map<ResourceType, Double> exchangePPU,
                              @Switch("f") boolean force) {
        TradeDB.BulkTradeOffer offer = tMan.getBulkOffer(offerId);
        if (offer == null) {
            return "No offer found with ID " + offerId;
        }
        offer = new TradeDB.BulkTradeOffer(offer);
        if (offer.nation != me.getNation_id()) {
            return "You are not the owner of offer " + offerId + " (owner: " + PW.getName(offer.nation, false) + ")";
        }
        if (quantity != null) {
            if (quantity <= 0) {
                return "Quantity must be greater than 0 (not " + quantity + ")";
            }
            offer.quantity = quantity;
        }
        if (minPPU != null) {
            if (minPPU <= 0) {
                return "Minimum price per unit must be greater than 0 (not " + minPPU + ")";
            }
            offer.minPPU = minPPU;
        }
        if (maxPPU != null) {
            if (maxPPU <= 0) {
                return "Maximum price per unit must be greater than 0 (not " + maxPPU + ")";
            }
            offer.maxPPU = maxPPU;
        }
        if (negotiable != null) {
            offer.negotiable = negotiable;
        }
        if (expire != null) {
            if (expire <= 0) {
                return "Expiration must be greater than 0 (not " + expire + ")";
            }
            offer.expire = System.currentTimeMillis() + expire;
        }

        if (expire > TimeUnit.DAYS.toMillis(30)) {
            return "Expiry cannot be longer than 30 days.";
        }
        if ((exchangeFor != null && exchangeFor.contains(offer.getResource())) || (exchangePPU != null && exchangePPU.containsKey(offer.getResource()))) {
            return "You cannot exchange for the same resource.";
        }
        if ((exchangeFor != null && exchangeFor.contains(ResourceType.CREDITS)) || (exchangePPU != null && exchangePPU.containsKey(ResourceType.CREDITS))) {
            return "You cannot exchange for credits.";
        }
        if ((exchangeFor != null && exchangeFor.contains(ResourceType.MONEY)) || (exchangePPU != null && exchangePPU.containsKey(ResourceType.MONEY))) {
            return "You cannot buy money. Create a sell offer instead.";
        }
        if (exchangePPU != null) {
            for (Map.Entry<ResourceType, Double> entry : exchangePPU.entrySet()) {
                if (entry.getValue() < 0 || !Double.isFinite(entry.getValue())) {
                    return "Exchange PPU must be positive number (value provided: " +  entry.getKey() + " at "  + entry.getValue() + ")";
                }
            }
        }

        if (exchangePPU != null || exchangeFor != null) {
            offer.setExchangeFor(exchangeFor, ResourceType.resourcesToArray(exchangePPU));
        }

        if (!force) {
            io.create().confirmation(offer.getTitle(), offer.toPrettyString(), command).send();
            return null;
        }

        tMan.updateBulkOffer(offer);
        io.create().embed("Updated: " + offer.getTitle(), offer.toPrettyString()).send();
        return null;
    }

    @RolePermission(value=Roles.MEMBER, guild=BULK_TRADE_SERVER)
    @Command(desc = "View the details of a bot trade offer on discord", viewable = true)
    public String offerInfo(@Me JSONObject command, TradeManager tMan, @Me IMessageIO io,
                            @Arg("The id of a trade offer")
                            int offerId) {
        TradeDB.BulkTradeOffer offer = tMan.getBulkOffer(offerId);
        if (offer == null) {
            return "No offer found with ID " + offerId;
        }
        String title = offer.getTitle();
        String body = offer.toPrettyString();
        io.create().embed(title, body).commandButton(command, "Refresh").send();
        return null;
    }


    @RolePermission(value=Roles.MEMBER, guild=BULK_TRADE_SERVER)
    @Command(desc = "Delete one of your bot trade offers on discord")
    public String deleteOffer(TradeManager tMan, @Me DBNation me,
                              @Arg("The resource you want to remove all your offers of")
                              @Default ResourceType deleteResource,
                              @Arg("Remove BUYING or SELLING of that resource")
                              @Default @ArgChoice(value = {"BUYING", "SELLING"}) String buyOrSell,
                              @Arg("The offer id you want to delete")
                              @Switch("i") Integer deleteId) {
        Set<Integer> idsToDelete = new HashSet<>();
        if (deleteId != null) {
            TradeDB.BulkTradeOffer offer = tMan.getBulkOffer(deleteId);
            if (offer == null) {
                return "No offer found with ID " + deleteId;
            }
            if (offer.nation != me.getNation_id()) {
                return "You can only delete your own offers (offer by: " + PW.getName(offer.nation, false) + ")";
            }
            idsToDelete.add(deleteId);
        }
        if (deleteResource != null) {
            boolean isBuy = buyOrSell != null && buyOrSell.equalsIgnoreCase("BUYING");
            Set<TradeDB.BulkTradeOffer> offers = tMan.getBulkOffers(f ->
                    f.nation == me.getNation_id() &&
                            f.getResource() == deleteResource &&
                            (buyOrSell == null || (isBuy && f.isBuy)));
            offers.forEach(f -> idsToDelete.add(f.id));
        }
        if (idsToDelete.isEmpty()) {
            return "No offers found to delete";
        }
        tMan.deleteBulkMarketOffers(idsToDelete, true);
        return "Deleted " + idsToDelete.size() + " offers:";
    }


    @RolePermission(value=Roles.MEMBER, guild=BULK_TRADE_SERVER)
    @Command(desc = "Create a bot trade offer on discord for buying a resource")
    @HasOffshore
    public String buyOffer(@Me IMessageIO io, TradeManager tMan, @Me JSONObject command, @Me DBNation me, ResourceType resource,
                           @Arg("The quantity of the resource you are receiving")
                               Long quantity,
                           @Arg("The minimum price per unit you are exchanging for")
                               @Switch("minPPU") Integer minPPU,
                           @Arg("The maximum price per unit you are exchanging for")
                               @Switch("maxPPU") Integer maxPPU,
                           @Arg("If prices are negotiable")
                               @Switch("n") Boolean negotiable,
                           @Arg("When the offer is no longer available")
                               @Switch("e") @Timediff Long expire,
                           @Arg("The resources you will exchange for")
                               @Switch("x") Set<ResourceType> exchangeFor,
                           @Arg("The equivalent price per unit you will accept for each resource")
                               @Switch("p") Map<ResourceType, Double> exchangePPU,
                           @Switch("f") boolean force) {
        if (expire > TimeUnit.DAYS.toMillis(30)) {
            return "Expiry cannot be longer than 30 days.";
        }
        if ((exchangeFor != null && exchangeFor.contains(resource)) || (exchangePPU != null && exchangePPU.containsKey(resource))) {
            return "You cannot exchange for the same resource.";
        }
        if (resource == ResourceType.CREDITS || (exchangeFor != null && exchangeFor.contains(ResourceType.CREDITS)) || (exchangePPU != null && exchangePPU.containsKey(ResourceType.CREDITS))) {
            return "You cannot exchange for credits.";
        }
        if (resource == ResourceType.MONEY || (exchangeFor != null && exchangeFor.contains(ResourceType.MONEY)) || (exchangePPU != null && exchangePPU.containsKey(ResourceType.MONEY))) {
            return "You cannot buy money. Create a sell offer instead.";
        }
        if (exchangePPU != null) {
            for (Map.Entry<ResourceType, Double> entry : exchangePPU.entrySet()) {
                if (entry.getValue() < 0 || !Double.isFinite(entry.getValue())) {
                    return "Exchange PPU must be positive number (value provided: " +  entry.getKey() + " at "  + entry.getValue() + ")";
                }
            }
        }
        if (resource != ResourceType.FOOD && quantity < 100000) {
            return "Quantity must be at least 100,000";
        }
        if (resource == ResourceType.FOOD && quantity < 1000000) {
            return "Quantity must be at least 1,000,000";
        }
        if ((minPPU != null && minPPU <= 0) || (maxPPU != null && maxPPU <= 0)) {
            return "min/maxPPU must be positive number";
        }
        long expireMs = System.currentTimeMillis() + expire;

        // int id, int resourceId, int nation, int quantity, boolean isBuy, int minPPU, int maxPPU, boolean negotiable, long expire, long exchangeForBits, double[] exchangePPU
        if (minPPU == null) minPPU = 0;
        if (maxPPU == null) maxPPU = 0;
        double[] exchangePPUDouble = exchangePPU == null ? null : ResourceType.resourcesToArray(exchangePPU);
        // int resourceId, int nation, int quantity, boolean isBuy, int minPPU, int maxPPU, boolean negotiable, long expire, Set<ResourceType> exchangeFor, double[] exchangePPU
        TradeDB.BulkTradeOffer offer = new TradeDB.BulkTradeOffer(resource.ordinal(), me.getNation_id(), quantity, true, minPPU, maxPPU, negotiable, expireMs, exchangeFor, exchangePPUDouble);

        String title = offer.getTitle();
        String body = offer.toPrettyString();
        if (!force) {
            io.create().confirmation("Pending: " + title, body, command).send();
            return null;
        }

        Set<TradeDB.BulkTradeOffer> removed = tMan.addBulkOffer(offer, true, true);

        StringBuilder response = new StringBuilder();

        if (!removed.isEmpty()) {
            response.append("Removed ").append(removed.size()).append(" old offers:\n");
            for (TradeDB.BulkTradeOffer o : removed) {
                response.append("- " + o.toSimpleString()).append("\n");
            }
        }

        // post to channel
        {
            long channelIdTmp = BULK_TRADE_CHANNEL;
            GuildMessageChannel channel = Locutus.imp().getDiscordApi().getGuildChannelById(channelIdTmp);
            if (channel != null) {
                DiscordUtil.createEmbedCommand(channel, offer.getTitle(), body); // TODO refresh cmd
            }
        }

        io.create().embed("Posted: " + offer.getTitle(), body).append(response.toString()).send();
        return null;
    }

    @RolePermission(value=Roles.MEMBER, guild=BULK_TRADE_SERVER)
    @Command(desc = "Create a bot trade offer on discord for selling a resource")
    @HasOffshore
    public String sellOffer(@Me IMessageIO io, TradeManager tMan, @Me JSONObject command, @Me DBNation me, ResourceType resource,
                            @Arg("The quantity of the resource you are sending")
                            Long quantity,
                            @Arg("The minimum price per unit you are exchanging for")
                                @Switch("minPPU") Integer minPPU,
                            @Arg("The maximum price per unit you are exchanging for")
                                @Switch("maxPPU") Integer maxPPU,
                            @Arg("If prices are negotiable")
                                @Switch("n") Boolean negotiable,
                            @Arg("When the offer is no longer available")
                                @Switch("e") @Timediff Long expire,
                            @Arg("The resources you will exchange for")
                                @Switch("x") Set<ResourceType> exchangeFor,
                            @Arg("The equivalent price per unit you will accept for each resource")
                                @Switch("p") Map<ResourceType, Double> exchangePPU,
                            @Switch("f") boolean force) {
        if (expire > TimeUnit.DAYS.toMillis(30)) {
            return "Expiry cannot be longer than 30 days.";
        }
        if ((exchangeFor != null && exchangeFor.contains(resource)) || (exchangePPU != null && exchangePPU.containsKey(resource))) {
            return "You cannot exchange for the same resource.";
        }
        if (resource == ResourceType.CREDITS || (exchangeFor != null && exchangeFor.contains(ResourceType.CREDITS)) || (exchangePPU != null && exchangePPU.containsKey(ResourceType.CREDITS))) {
            return "You cannot exchange for credits.";
        }
        if (resource == ResourceType.MONEY || (exchangeFor != null && exchangeFor.contains(ResourceType.MONEY)) || (exchangePPU != null && exchangePPU.containsKey(ResourceType.MONEY))) {
            return "You cannot buy money. Create a buy offer instead.";
        }
        if (exchangePPU != null) {
            for (Map.Entry<ResourceType, Double> entry : exchangePPU.entrySet()) {
                if (entry.getValue() < 0 || !Double.isFinite(entry.getValue())) {
                    return "Exchange PPU must be positive number (value provided: " +  entry.getKey() + " at "  + entry.getValue() + ")";
                }
            }
        }
        if (resource != ResourceType.FOOD && quantity < 100000) {
            return "Quantity must be at least 100,000";
        }
        if (resource == ResourceType.FOOD && quantity < 1000000) {
            return "Quantity must be at least 1,000,000";
        }
        if ((minPPU != null && minPPU <= 0) || (maxPPU != null && maxPPU <= 0)) {
            return "min/maxPPU must be positive number";
        }
        long expireMs = System.currentTimeMillis() + expire;

        // int id, int resourceId, int nation, int quantity, boolean isBuy, int minPPU, int maxPPU, boolean negotiable, long expire, long exchangeForBits, double[] exchangePPU
        if (minPPU == null) minPPU = 0;
        if (maxPPU == null) maxPPU = 0;
        double[] exchangePPUDouble = exchangePPU == null ? null : ResourceType.resourcesToArray(exchangePPU);
        // int resourceId, int nation, int quantity, boolean isBuy, int minPPU, int maxPPU, boolean negotiable, long expire, Set<ResourceType> exchangeFor, double[] exchangePPU
        TradeDB.BulkTradeOffer offer = new TradeDB.BulkTradeOffer(resource.ordinal(), me.getNation_id(), quantity, false, minPPU, maxPPU, negotiable, expireMs, exchangeFor, exchangePPUDouble);

        String title = offer.getTitle();
        String body = offer.toPrettyString();
        if (!force) {
            io.create().confirmation("Pending: " + title, body, command).send();
            return null;
        }

        Set<TradeDB.BulkTradeOffer> removed = tMan.addBulkOffer(offer, true, true);

        StringBuilder response = new StringBuilder();

        if (!removed.isEmpty()) {
            response.append("Removed ").append(removed.size()).append(" old offers:\n");
            for (TradeDB.BulkTradeOffer o : removed) {
                response.append("- " + o.toSimpleString()).append("\n");
            }
        }

        // post to channel
        {
            long channelIdTmp = BULK_TRADE_CHANNEL;
            GuildMessageChannel channel = Locutus.imp().getDiscordApi().getGuildChannelById(channelIdTmp);
            if (channel != null) {
                DiscordUtil.createEmbedCommand(channel, offer.getTitle(), body); // TODO refresh cmd
            }
        }

        io.create().embed("Posted: " + offer.getTitle(), body).append(response.toString()).send();
        return null;


    }


    @Command(aliases = {"GlobalTradeAverage", "gta", "tradeaverage"}, desc = "Get the average trade price of resources over a period of time", viewable = true)
    public String GlobalTradeAverage(@Me JSONObject command, @Me IMessageIO channel,
                                     @Arg("Time to average over (from today)")
                                     @Timestamp long time) {
        Map.Entry<Map<ResourceType, Double>, Map<ResourceType, Double>> averages = Locutus.imp().getTradeManager().getAverage(time);

        Map<ResourceType, Double> lowMap = averages.getKey();
        Map<ResourceType, Double> highMap = averages.getValue();

        List<String> resourceNames = new ArrayList<>();
        List<String> low = new ArrayList<>();
        List<String> high = new ArrayList<>();

        for (ResourceType type : ResourceType.values) {
            if (type == ResourceType.MONEY) continue;

            resourceNames.add(MarkupUtil.markdownUrl(type.name().toLowerCase(), type.url(true, true)));

            int i = type.ordinal();

            double avgLow = lowMap.getOrDefault(type, 0d);
            low.add(MathMan.format(avgLow));

            double avgHigh = highMap.getOrDefault(type, 0d);
            high.add(MathMan.format(avgHigh));
        }

        String timeStr = TimeUtil.secToTime(TimeUnit.MILLISECONDS, System.currentTimeMillis() - time);
        MessageEmbed embed = new EmbedBuilder()
                .appendDescription("low: `" + ResourceType.toString(lowMap) + "`\n")
                .appendDescription("high: `" + ResourceType.toString(highMap) + "`\n")
                .setTitle("Global Trade Average " + timeStr)
                .addField("Resource", StringMan.join(resourceNames, "\n"), true)
                .addField("Low", StringMan.join(low, "\n"), true)
                .addField("High", StringMan.join(high, "\n"), true).build();
        channel.create()
                .embed(embed)
                .commandButton(command, "Refresh")
                .send();
        return null;
    }

    @Command(aliases = {"GlobalTradeVolume", "gtv", "tradevolume"},
            desc = "Get the change in trade volume of each resource over a period of time", viewable = true)
    public String GlobalTradeVolume(@Me JSONObject command, @Me IMessageIO channel) {
        TradeManager trader = Locutus.imp().getTradeManager();

        List<String> resourceNames = new ArrayList<>();
        List<String> daily = new ArrayList<>();
        List<String> weekly = new ArrayList<>();

        for (ResourceType type : ResourceType.values()) {
            if (type.getGraphId() <= 0) continue;
            long[] volume = trader.getVolumeHistory(type);

            int i = volume.length - 1;
            double dailyChangePct = 100 * (volume[i] - volume[i - 2]) / (double) volume[i];

            long weeklyTotalChange = 0;
            for (int j = 0; j < 7; j++) {
                weeklyTotalChange += volume[i - j] - volume[i - j - 1];
            }
            long averageWeeklyChange = weeklyTotalChange / 7;
            double weeklyChangePct = 100 * (averageWeeklyChange / (double) volume[i]);

            String name = type.name().toLowerCase();
            if (type == ResourceType.MUNITIONS) name = "\n" + name;
            resourceNames.add("[" + name + "](" + type.url(weeklyChangePct <= 0, true) + ")\n");

            String dayPrefix = (int) (dailyChangePct * 100) > 0 ? "+" : "";
            String weekPrefix = (int) (weeklyChangePct * 100) > 0 ? "+" : "";
            daily.add("```diff\n" + dayPrefix + MathMan.format(dailyChangePct) + "%```");
            weekly.add("```diff\n" + weekPrefix + MathMan.format(weeklyChangePct) + "%```");
        }

        channel.create().embed(new EmbedBuilder()
                .setTitle("Global Trade Volume")
                .addField("Resource", "\u200B\n" + StringMan.join(resourceNames, "\n"), true)
                .addField("Daily", StringMan.join(daily, " "), true)
                .addField("Weekly", StringMan.join(weekly, " "), true)
                .build()
        ).commandButton(command, "Refresh").send();

        return null;
    }

    @Command(desc = "Show the total market value of resource amounts", aliases = {"resourcevalue", "convertedtotal"}, viewable = true)
    public String convertedTotal(Map<ResourceType, Double> resources,
                                 @Arg("Remove negative amounts and return the scaled resource amounts of equivalent value")
                                 @Switch("n") boolean normalize,
                                 @Arg("Use the average buying price")
                                 @Switch("b") boolean useBuyPrice,
                                 @Arg("Use the average selling price")
                                 @Switch("s") boolean useSellPrice,
                                 @Arg("Show total value in a resource instead of money")
                                 @Switch("t") ResourceType convertType) {
        if (normalize) {
            double total = ResourceType.convertedTotal(resources);
            if (total <= 0) {
                return "Total is negative";
            }

            double negativeTotal = 0;

            Iterator<Map.Entry<ResourceType, Double>> iter = resources.entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry<ResourceType, Double> entry = iter.next();
                if (entry.getValue() < 0) {
                    negativeTotal += Locutus.imp().getTradeManager().getHigh(entry.getKey()) * entry.getValue().doubleValue() * -1;
                    iter.remove();
                }
            }
            double postiveTotal = ResourceType.convertedTotal(resources);


            double factor = total / postiveTotal;
//            factor = Math.min(factor, postiveTotal / (negativeTotal + postiveTotal));

            for (ResourceType type : ResourceType.values()) {
                Double value = resources.get(type);
                if (value == null || value == 0) continue;

                resources.put(type, value * factor);
            }
        }

        StringBuilder result = new StringBuilder("```" + ResourceType.toString(resources) + "```");

        double value = ResourceType.convertedTotal(resources);
        if (useBuyPrice || useSellPrice) {
            value = 0;
            boolean buy = useBuyPrice;
            for (Map.Entry<ResourceType, Double> entry : resources.entrySet()) {
                int price = Locutus.imp().getTradeManager().getPrice(entry.getKey(), buy);
                value += price * entry.getValue();
            }
        }
        result.append("\n" + "Worth: $" + MathMan.format(value));
        if (convertType != null && convertType != ResourceType.MONEY) {
            double convertTypeValue = ResourceType.convertedTotal(convertType, 1);
            double amtConvertType = value / convertTypeValue;
            result.append(" OR " + MathMan.format(amtConvertType) + "x " + convertType);
        }

        return result.toString();
    }

    @Command(desc = "Get the margin between buy and sell for each resource", viewable = true)
    public static String tradeMargin(@Me JSONObject command, @Me IMessageIO channel, TradeManager trader,
                              @Arg("Display the margin percent instead of absolute difference")
                              @Switch("p") boolean usePercent) {
        String refreshEmoji = "Refresh";

        Map<ResourceType, Double> low = trader.getLow().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().doubleValue()));
        Map<ResourceType, Double> high = trader.getHigh().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().doubleValue()));
        List<ResourceType> resources = new ArrayList<>(ResourceType.valuesList);
        resources.remove(ResourceType.MONEY);

        List<String> resourceNames = resources.stream().map(r -> r.name().toLowerCase()).collect(Collectors.toList());

        ArrayList<String> diffList = new ArrayList<>();

        for (ResourceType type : resources) {
            Double o1 = low.get(type);
            Double o2 = high.get(type);

            double diff = o1 == null || o2 == null || !Double.isFinite(o1) || !Double.isFinite(o2) ? Double.NaN : (o2 - o1);

            if (usePercent) {
                diff = 100 * diff / o2;
            }

            diffList.add(o1 == null ? "" : (MathMan.format(diff) + (usePercent ? "%" : "")));
        }

        channel.create().embed(new EmbedBuilder()
                .setTitle("Trade Margin")
                .addField("Resource", StringMan.join(resourceNames, "\n"), true)
                .addField("margin", StringMan.join(diffList, "\n"), true)
                .build()
        ).commandButton(command, "Refresh").send();

        return null;
    }

    @Command(desc = "Get the current top buy and sell price of each resource", viewable = true)
    public String tradePrice(@Me JSONObject command, @Me IMessageIO channel, TradeManager manager) {
        Map<ResourceType, Double> low = manager.getLow().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().doubleValue()));
        Map<ResourceType, Double> high = manager.getHigh().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().doubleValue()));

        String lowKey = "Low";
        String highKey = "High";

        List<ResourceType> resources = new ArrayList<>(ResourceType.valuesList);
        resources.remove(ResourceType.MONEY);

        List<String> resourceNames = resources.stream().map(r -> r.name().toLowerCase()).collect(Collectors.toList());

        ArrayList<String> lowList = new ArrayList<>();
        ArrayList<String> highList = new ArrayList<>();

        for (ResourceType type : resources) {
            Double o1 = low.get(type);
            Double o2 = high.get(type);

            lowList.add(o1 == null ? "" : MathMan.format(o1));
            highList.add(o2 == null ? "" : MathMan.format(o2));
        }

        channel.create().embed(new EmbedBuilder()
                .setTitle("Trade Price")
                .appendDescription("low: `" + ResourceType.toString(low) + "`\n")
                .appendDescription("high: `" + ResourceType.toString(high) + "`\n")
                .addField("Resource", StringMan.join(resourceNames, "\n"), true)
                .addField(lowKey, StringMan.join(lowList, "\n"), true)
                .addField(highKey, StringMan.join(highList, "\n"), true)
                .build()
        ).commandButton(command, "Refresh").send();
        return null;
    }

    @Command(desc = "View an accumulation of all the net trades a nation made, grouped by nation.", aliases = {"TradeRanking", "TradeProfitRanking"}, viewable = true)
    public String tradeRanking(@Me IMessageIO channel, @Me JSONObject command, Set<DBNation> nations,
                               @Arg("Date to start from")
                               @Timestamp long time,
                               @Arg("Group by alliance instead of nation")
                               @Switch("a") boolean groupByAlliance, @Switch("u") boolean uploadFile) {
        Function<DBNation, Integer> groupBy = groupByAlliance ? groupBy = f -> f.getAlliance_id() : f -> f.getNation_id();
        Set<Integer> nationIds = nations.stream().map(f -> f.getNation_id()).collect(Collectors.toSet());
        Map<Integer, TradeRanking.TradeProfitContainer> tradeContainers = new HashMap<>();

        List<DBTrade> trades = nationIds.size() > 1000 ? Locutus.imp().getTradeManager().getTradeDb().getTrades(time) : Locutus.imp().getTradeManager().getTradeDb().getTrades(nationIds, time);

        for (DBTrade trade : trades) {
            Integer buyer = trade.getBuyer();
            Integer seller = trade.getSeller();

            if (!nationIds.contains(buyer) && !nationIds.contains(seller)) {
                continue;
            }

            double per = trade.getPpu();
            ResourceType type = trade.getResource();

            if (per <= 1 || (per > 10000 || (type == ResourceType.FOOD && per > 1000))) {
                continue;
            }

            for (int nationId : new int[]{buyer, seller}) {
                if (!nationIds.contains(nationId)) continue;
                DBNation nation = Locutus.imp().getNationDB().getNationById(nationId);
                if (nation == null) continue;
                if (groupByAlliance && nation.getAlliance_id() == 0) continue;
                int groupId = groupBy.apply(nation);

                int sign = (nationId == seller ^ trade.isBuy()) ? 1 : -1;
                long total = trade.getQuantity() * (long) trade.getPpu();

                TradeRanking.TradeProfitContainer container = tradeContainers.computeIfAbsent(groupId, f -> new TradeRanking.TradeProfitContainer());

                if (sign > 0) {
                    container.inflows.put(type, trade.getQuantity() + container.inflows.getOrDefault(type, 0L));
                    container.sales.put(type, trade.getQuantity() + container.sales.getOrDefault(type, 0L));
                    container.salesPrice.put(type, total + container.salesPrice.getOrDefault(type, 0L));
                } else {
                    container.outflow.put(type, trade.getQuantity() + container.inflows.getOrDefault(type, 0L));
                    container.purchases.put(type, trade.getQuantity() + container.purchases.getOrDefault(type, 0L));
                    container.purchasesPrice.put(type, total + container.purchasesPrice.getOrDefault(type, 0L));
                }

                container.netOutflows.put(type, ((-1) * sign * trade.getQuantity()) + container.netOutflows.getOrDefault(type, 0L));
                container.netOutflows.put(ResourceType.MONEY, (sign * total) + container.netOutflows.getOrDefault(ResourceType.MONEY, 0L));
            }
        }

        Map<Integer, Double> profitByGroup = new HashMap<>();
        for (Map.Entry<Integer, TradeRanking.TradeProfitContainer> containerEntry : tradeContainers.entrySet()) {
            TradeRanking.TradeProfitContainer container = containerEntry.getValue();
            Map<ResourceType, Double> ppuBuy = new HashMap<>();
            Map<ResourceType, Double> ppuSell = new HashMap<>();

            for (Map.Entry<ResourceType, Long> entry : container.purchases.entrySet()) {
                ResourceType type = entry.getKey();
                ppuBuy.put(type, (double) container.purchasesPrice.get(type) / entry.getValue());
            }

            for (Map.Entry<ResourceType, Long> entry : container.sales.entrySet()) {
                ResourceType type = entry.getKey();
                ppuSell.put(type, (double) container.salesPrice.get(type) / entry.getValue());
            }

            double profitTotal = ResourceType.convertedTotal(container.netOutflows);
            double profitMin = 0;
            for (Map.Entry<ResourceType, Long> entry : container.netOutflows.entrySet()) {
                profitMin += -ResourceType.convertedTotal(entry.getKey(), -entry.getValue());
            }
            profitTotal = Math.min(profitTotal, profitMin);
            profitByGroup.put(containerEntry.getKey(), profitTotal);
        }


        String title = (groupByAlliance ? "Alliance" : "") + "trade profit (" + profitByGroup.size() + ")";
        new SummedMapRankBuilder<>(profitByGroup).sort().nameKeys(id -> PW.getName(id, groupByAlliance)).build(channel, command, title, uploadFile);
        return null;
    }

    @Command(desc = "Generate a google sheet of the amount traded of each resource at each price point over a period of time\n" +
            "Credits are grouped by 10,000, food by 10, everything else is actual price", viewable = true)
    @RolePermission(Roles.MEMBER)
    @Similar(CM.register.class)
    public String trending(@Me IMessageIO channel, @Me GuildDB db,
                           @Arg("Date to start from")
                           @Timestamp long time) throws GeneralSecurityException, IOException {
        Map<ResourceType, Map<Integer, LongAdder>> sold = new EnumMap<>(ResourceType.class);
        Map<ResourceType, Map<Integer, LongAdder>> bought = new EnumMap<>(ResourceType.class);

        Map.Entry<Map<ResourceType, Double>, Map<ResourceType, Double>> averages = Locutus.imp().getTradeManager().getAverage(time);

        link.locutus.discord.db.TradeDB tradeDB = Locutus.imp().getTradeManager().getTradeDb();
        for (DBTrade offer : tradeDB.getTrades(time)) {
            // Ignore outliers
            int ppu = offer.getPpu();


            double lowCutoff = averages.getKey().get(offer.getResource()) * 0.5;
            double highCutoff = averages.getValue().get(offer.getResource()) * 2;

            if (ppu < lowCutoff || ppu > highCutoff) continue;

            if (offer.getResource() == ResourceType.CREDITS) {
                ppu /= 10000;
            } else if (offer.getResource() != ResourceType.FOOD) {
                ppu /= 10;
            }

            Map<ResourceType, Map<Integer, LongAdder>> map = offer.isBuy() ? sold : bought;
            Map<Integer, LongAdder> rssMap = map.get(offer.getResource());
            if (rssMap == null) {
                rssMap = new HashMap<>();
                map.put(offer.getResource(), rssMap);
            }
            LongAdder cumulative = rssMap.get(ppu);
            if (cumulative == null) {
                cumulative = new LongAdder();
                rssMap.put(ppu, cumulative);
            }
            cumulative.add(offer.getQuantity());
        }

        SpreadSheet sheet = SpreadSheet.create(db, SheetKey.TRADE_VOLUME_SHEET);

        List<Object> header = new ArrayList<>();
        header.add("PPU (adjusted)");
        for (ResourceType value : ResourceType.values) {
            if (value != ResourceType.MONEY) {
                header.add(value.name() + " BUY");
                header.add(value.name() + " SELL");
            }
        }
        sheet.setHeader(header);

        Map<ResourceType, Long> soldPrevious = new HashMap<>();
        Map<ResourceType, Long> boughtPrevious = new HashMap<>();

        for (int i = 30; i < 10000; i += 5) {
            header.set(0, Integer.toString(i));
            for (int j = 1; j < ResourceType.values.length; j++) {
                ResourceType value = ResourceType.values[j];
                {
                    int headerIndex = (j - 1) * 2 + 1;
                    Map<Integer, LongAdder> soldByType = sold.getOrDefault(value, Collections.emptyMap());
                    LongAdder amt = soldByType.getOrDefault(i, new LongAdder());
                    if (amt.longValue() == 0) {
                        header.set(headerIndex, "");
                    } else {
                        Long previous = soldPrevious.getOrDefault(value, 0L);
                        header.set(headerIndex, previous + amt.longValue());
                        soldPrevious.put(value, previous + amt.longValue());
                    }
                }
                {
                    int headerIndex = (j - 1) * 2 + 2;
                    Map<Integer, LongAdder> soldByType = bought.getOrDefault(value, Collections.emptyMap());
                    LongAdder amt = soldByType.getOrDefault(i, new LongAdder());
                    if (amt.longValue() == 0) {
                        header.set(headerIndex, "");
                    } else {
                        Long previous = boughtPrevious.getOrDefault(value, 0L);
                        header.set(headerIndex, previous + amt.longValue());
                        boughtPrevious.put(value, previous + amt.longValue());
                    }
                }
            }

            sheet.addRow(header);
        }

        sheet.updateWrite();

        sheet.attach(channel.create(), "trending").send();
        return null;
    }

    @Command(desc = "View an accumulation of all the net trades nations have made over a time period", viewable = true)
    public String tradeProfit(Set<DBNation> nations,
                              @Arg("Date to start from")
                              @Timestamp long time) throws GeneralSecurityException, IOException {
        Set<Integer> nationIds = nations.stream().map(f -> f.getNation_id()).collect(Collectors.toSet());

        List<DBTrade> trades = nationIds.size() > 1000 ? Locutus.imp().getTradeManager().getTradeDb().getTrades(time) : Locutus.imp().getTradeManager().getTradeDb().getTrades(nationIds, time);

        Map<ResourceType, Long> netOutflows = new HashMap<>();

        Map<ResourceType, Long> inflows = new HashMap<>();
        Map<ResourceType, Long> outflow = new HashMap<>();

        Map<ResourceType, Long> purchases = new HashMap<>();
        Map<ResourceType, Long> purchasesPrice = new HashMap<>();

        Map<ResourceType, Long> sales = new HashMap<>();

        Map<ResourceType, Long> salesPrice = new HashMap<>();

        for (DBTrade trade : trades) {
            Integer buyer = trade.getBuyer();
            Integer seller = trade.getSeller();

            if (!nationIds.contains(buyer) && !nationIds.contains(seller)) {
                continue;
            }

            double per = trade.getPpu();
            ResourceType type = trade.getResource();

            if (per <= 1 || (per > 10000 || (type == ResourceType.FOOD && per > 1000))) {
                continue;
            }

            int sign = (nationIds.contains(seller) ^ trade.isBuy()) ? 1 : -1;
            long total = trade.getQuantity() * (long) trade.getPpu();

            if (sign > 0) {
                inflows.put(type, trade.getQuantity() + inflows.getOrDefault(type, 0L));
                sales.put(type, trade.getQuantity() + sales.getOrDefault(type, 0L));
                salesPrice.put(type, total + salesPrice.getOrDefault(type, 0L));
            } else {
                outflow.put(type, trade.getQuantity() + outflow.getOrDefault(type, 0L));
                purchases.put(type, trade.getQuantity() + purchases.getOrDefault(type, 0L));
                purchasesPrice.put(type, total + purchasesPrice.getOrDefault(type, 0L));
            }

            netOutflows.put(type, ((-1) * sign * trade.getQuantity()) + netOutflows.getOrDefault(type, 0L));
            netOutflows.put(ResourceType.MONEY, (sign * total) + netOutflows.getOrDefault(ResourceType.MONEY, 0L));
        }

        Map<ResourceType, Double> ppuBuy = new HashMap<>();
        Map<ResourceType, Double> ppuSell = new HashMap<>();

        for (Map.Entry<ResourceType, Long> entry : purchases.entrySet()) {
            ResourceType type = entry.getKey();
            ppuBuy.put(type, (double) purchasesPrice.get(type) / entry.getValue());
        }

        for (Map.Entry<ResourceType, Long> entry : sales.entrySet()) {
            ResourceType type = entry.getKey();
            ppuSell.put(type, (double) salesPrice.get(type) / entry.getValue());
        }

        double profitTotal = ResourceType.convertedTotal(netOutflows);
        double profitMin = 0;
        for (Map.Entry<ResourceType, Long> entry : netOutflows.entrySet()) {
            profitMin += -ResourceType.convertedTotal(entry.getKey(), -entry.getValue());
        }
        profitTotal = Math.min(profitTotal, profitMin);

        HashMap<ResourceType, Long> totalVolume = new LinkedHashMap<>();
        for (ResourceType type : ResourceType.values()) {
            long in  = inflows.getOrDefault(type, 0L);
            long out  = outflow.getOrDefault(type, 0L);
            long total = Math.abs(in) + Math.abs(out);
            if (total != 0) totalVolume.put(type, total);
        }
//
//        if (createSpreadsheet) {
//            SpreadSheet sheet = SpreadSheet.create(db, SheetKeys.NATION_SHEET);
//        }

        StringBuilder response = new StringBuilder();
        response
                .append('\n').append("Buy (PPU):```")
                .append(String.format("%16s", ResourceType.toString(ppuBuy)))
                .append("```")
                .append(' ').append("Sell (PPU):```")
                .append(String.format("%16s", ResourceType.toString(ppuSell)))
                .append("```")
                .append(' ').append("Net inflows:```")
                .append(String.format("%16s", ResourceType.toString(netOutflows)))
                .append("```")
                .append(' ').append("Total Volume:```")
                .append(String.format("%16s", ResourceType.toString(totalVolume)))
                .append("```");
        response.append("Profit total: $").append(MathMan.format(profitTotal));
        return response.toString().trim();
    }


    @Command(desc = "View an accumulation of all the net money trades a nation made, grouped by nation\n" +
            "Money trades are selling resources for close to $0 or buying for very large money amounts", viewable = true)
    public String moneyTrades(TradeManager manager, DBNation nation,
                              @Arg("Date to start from")
                              @Timestamp long time, @Switch("f") boolean forceUpdate,
                              @Arg("Return a deposits add command for each grouping")
                              @Switch("a") boolean addBalance) throws IOException {
        if (forceUpdate) {
            Locutus.imp().runEventsAsync(events -> manager.updateTradeList(events));
        }

        Map<Integer, Map<ResourceType, Long>> netInflows = new HashMap<>();

        List<DBTrade> trades = Locutus.imp().getTradeManager().getTradeDb().getTrades(nation.getNation_id(), time);
        for (DBTrade offer : trades) {
            if (offer.getResource() == ResourceType.CREDITS) continue;
            int max = offer.getResource() == ResourceType.FOOD ? 1000 : 10000;
            if (offer.getPpu() > 1 && offer.getPpu() < max) continue;

            int sign = offer.isBuy() ? -1 : 1;
            int per = offer.getPpu();

            Integer client = (offer.getSeller() == (nation.getNation_id())) ? offer.getBuyer() : offer.getSeller();

            Map<ResourceType, Long> existing = netInflows.computeIfAbsent(client,  integer -> Maps.newLinkedHashMap());

            if (per <= 1) {
                existing.put(offer.getResource(), (long) (offer.getQuantity() * sign + existing.getOrDefault(offer.getResource(), 0L)));
            } else {
                existing.put(ResourceType.MONEY, (long) (sign * offer.getTotal()) + existing.getOrDefault(ResourceType.MONEY, 0L));
            }
        }

        if (netInflows.isEmpty()) return "No trades found for " + nation.getNation() + " in the past " + TimeUtil.secToTime(TimeUnit.MILLISECONDS, System.currentTimeMillis() - time);

        StringBuilder response = new StringBuilder("Your net inflows from:");
        for (Map.Entry<Integer, Map<ResourceType, Long>> entry : netInflows.entrySet()) {
            Integer clientId = entry.getKey();
            DBNation client = Locutus.imp().getNationDB().getNationById(clientId);
            String name = PW.getName(clientId, false);
            if (addBalance) {
                response.append("\n**" + name);
                if (client != null) response.append(" | " + client.getAllianceName());
                response.append(":**\n");
                String url = "" + Settings.PNW_URL() + "/nation/id=" + clientId;
                response.append(CM.deposits.add.cmd.accounts(url).amount(ResourceType.toString(entry.getValue())).note("#deposit").toSlashCommand());
            } else {
                response.append('\n').append("```").append(name).append(" | ");
                if (client != null && client.getAlliance_id() != 0) {
                    response.append(String.format("%16s", client.getAllianceName()));
                }
                response.append(String.format("%16s", ResourceType.toString(entry.getValue())))
                        .append("```");
            }

        }
        return response.toString().trim();
    }

    public static void generateSheet(OffshoreInstance offshore, SpreadSheet sheet, Set<Long> coalitions, double[] allDeposits, List<String> errors) {
        List<String> header = new ArrayList<>(Arrays.asList(
                "name",
                "id",
                "amount",
                "value"
        ));
        for (ResourceType type : ResourceType.values) {
            if (type != ResourceType.CREDITS) header.add(type.name().toLowerCase());
        }
        sheet.setHeader(header);

        Set<Long> testedIds = new HashSet<>();
        for (Long coalition : coalitions) {
            GuildDB otherDb;
            if (coalition > Integer.MAX_VALUE) {
                otherDb = Locutus.imp().getGuildDB(coalition);
            } else {
                otherDb = Locutus.imp().getGuildDBByAA(coalition.intValue());
            }
            if (otherDb != null) {
                Set<Integer> otherAAIds = otherDb.getAllianceIds();
                for (Integer aaId : otherAAIds) {
                    if (!testedIds.add(aaId.longValue())) {
                        Set<Long> guildsMatching = new HashSet<>();
                        for (GuildDB value : Locutus.imp().getGuildDatabases().values()) {
                            if (value.isAllianceId(aaId)) guildsMatching.add(value.getIdLong());
                        }
                        errors.add("Duplicate aa: " + otherDb.getGuild() + " <| " + aaId + " | " + StringMan.getString(guildsMatching));
                        continue;
                    }
                }
                if (!testedIds.add(otherDb.getIdLong())) {
                    errors.add("Duplicate guild: " + otherDb.getGuild());
                    continue;
                }
                Map<NationOrAllianceOrGuild, double[]> byAA = offshore.getDepositsByAA(otherDb, f -> true, false);
                double[] deposits = ResourceType.getBuffer();
                byAA.forEach((a, b) -> ResourceType.add(deposits, b));
                allDeposits = ResourceType.add(allDeposits, deposits);

                for (Map.Entry<NationOrAllianceOrGuild, double[]> entry : byAA.entrySet()) {
                    NationOrAllianceOrGuild account = entry.getKey();
                    double[] amount = entry.getValue();
                    if (ResourceType.isZero(amount)) continue;
                    header.set(0, MarkupUtil.sheetUrl(account.getQualifiedName(), account.getUrl()));
                    header.set(1, account.getId() + "");
                    header.set(2, ResourceType.toString(amount));
                    header.set(3, MathMan.format(ResourceType.convertedTotal(amount)));
                    int i = 4;
                    for (ResourceType type : ResourceType.values) {
                        if (type != ResourceType.CREDITS) header.set(i++, MathMan.format(amount[type.ordinal()]));
                    }
                    sheet.addRow(header);
                }

            }
        }
    }

    public static Set<Long> getOffshoreCoalitions(GuildDB db) {
        Set<Long> coalitions = ArrayUtil.sort(db.getCoalitionRaw(Coalition.OFFSHORING), true);
        coalitions.remove(db.getIdLong());
        for (int id : db.getAllianceIds()) {
            coalitions.remove((long) id);
        }
        coalitions.removeIf(f -> Locutus.imp().getGuildDB(f) == null && Locutus.imp().getGuildDBByAA(f.intValue()) == null);
        return coalitions;
    }

    @Command(desc = "Compare the stockpile in the offshore alliance in-game bank to the total account balances of all offshoring alliances/guilds", viewable = true)
    @RolePermission(Roles.ECON)
    @IsAlliance
    public static String compareOffshoreStockpile(@Me IMessageIO channel, @Me GuildDB db, @Switch("s") SpreadSheet sheet) throws IOException, GeneralSecurityException {
        Map.Entry<GuildDB, Integer> offshoreDb = db.getOffshoreDB();
        if (offshoreDb == null || offshoreDb.getKey() != db) throw new IllegalArgumentException("This command must be run in the offshore server");
        if (sheet == null) {
            sheet = SpreadSheet.create(db, SheetKey.OFFSHORE_DEPOSITS);
        }

        channel.send("Please wait...");

        OffshoreInstance offshore = db.getOffshore();
        offshore.sync();
        Map<ResourceType, Double> stockpile = db.getAllianceList().getStockpile();

        Set<Long> coalitions = getOffshoreCoalitions(db);
        String title = "Compare Stockpile to Deposits (" + coalitions.size() + ")";
        StringBuilder body = new StringBuilder();
        Set<Long> aaIds = ArrayUtil.sort(coalitions.stream().filter(f -> f.intValue() == f).collect(Collectors.toSet()), true);
        Set<Long> corpIds = ArrayUtil.sort(coalitions.stream().filter(f -> f.intValue() != f).collect(Collectors.toSet()), true);
        body.append("Alliances: " + StringMan.join(aaIds, ",")).append("\n");
        body.append("Corporations: " + StringMan.join(corpIds, ",")).append("\n");
        body.append("Stockpile: `" + ResourceType.toString(stockpile) + "`\n");
        body.append("- worth: ~$" + MathMan.format(ResourceType.convertedTotal(stockpile))).append("\n");

        double[] allDeposits = ResourceType.getBuffer();
        List<String> errors = new ArrayList<>();
        generateSheet(offshore, sheet, coalitions, allDeposits, errors);

        sheet.updateClearCurrentTab();
        sheet.updateWrite();

        double[] diff = ResourceType.subtract(allDeposits.clone(), ResourceType.resourcesToArray(stockpile));

        body.append("Offshored Deposits: `" + ResourceType.toString(allDeposits) + "`\n");
        body.append("- worth: ~$" + MathMan.format(ResourceType.convertedTotal(allDeposits))).append("\n");

        body.append("Diff: `" + ResourceType.toString(diff) + "`\n");
        body.append("- worth: ~$" + MathMan.format(ResourceType.convertedTotal(diff))).append("\n");

        String emoji = "Show Day Graph";

        body.append("\nPress `" + emoji + "` to compare by day (200 days)");


        CommandRef cmd = CM.trade.compareStockpileValueByDay.cmd.stockpile1(ResourceType.toString(stockpile)).stockpile2(ResourceType.toString(allDeposits)).numDays("200");

        IMessageBuilder msg = channel.create().embed(title, body.toString())
                .commandButton(cmd, "Show Graph (200d)")
                .append("Done!");
        sheet.attach(msg, "balances");
        if (!errors.isEmpty()) {
            msg.append("\n- " + StringMan.join(errors, "\n- "));
        }
        msg.send();

        return null;
    }

    @Command(desc = "Generate a graph comparing market values of two resource amounts by day", viewable = true)
    public String compareStockpileValueByDay(@Me GuildDB db, @Me IMessageIO channel, TradeManager manager, link.locutus.discord.db.TradeDB tradeDB, @Me JSONObject command,
                                             Map<ResourceType, Double> stockpile1,
                                             Map<ResourceType, Double> stockpile2,
                                             @Range(min=1, max=3000) int numDays,
                                             @Switch("j") boolean attachJson,
                                             @Switch("c") boolean attachCsv, @Switch("ss") boolean attach_sheet) throws IOException, GeneralSecurityException {
        IMessageBuilder msg = new StockpileValueByDay(stockpile1, stockpile2, numDays).writeMsg(channel.create(), attachJson, attachCsv, attach_sheet ? db : null, SheetKey.STOCKPILE_VALUE_DAY);
        if (Settings.INSTANCE.ENABLED_COMPONENTS.WEB) {
            msg.append("\n**See also:** " + WebUtil.frontendUrl("view_graph/" + WM.api.compareStockpileValueByDay.cmd.getName(), command));
        }
        msg.send();
        return "Done!";
    }

    @Command(desc = "Generate a graph of average buy and sell trade price by day")
    public String tradepricebyday(@Me GuildDB db, @Me IMessageIO channel, TradeManager manager, link.locutus.discord.db.TradeDB tradeDB, @Me JSONObject command,
                                  Set<ResourceType> resources,
                                  int numDays,
                                  @Switch("j") boolean attachJson,
                                  @Switch("c") boolean attachCsv, @Switch("ss") boolean attach_sheet) throws IOException, GeneralSecurityException {
        TradePriceByDay graph = new TradePriceByDay(resources, numDays);
        IMessageBuilder msg = graph.writeMsg(channel.create(), attachJson, attachCsv, attach_sheet ? db : null, SheetKey.TRADE_PRICE_DAY);
        if (Settings.INSTANCE.ENABLED_COMPONENTS.WEB) {
            msg.append("\n**See also:** " + WebUtil.frontendUrl("view_graph/" + WM.api.tradePriceByDay.cmd.getName(), command));
        }
        msg.send();
        return "Done!";
    }

    @Command(desc = "Generate a graph of average trade buy and sell margin by day", viewable = true)
    public String trademarginbyday(@Me GuildDB db, @Me IMessageIO channel, TradeManager manager,
                                   @Timestamp long start, @Default @Timestamp Long end, @Me JSONObject command,
                                   @Arg("Use the margin percent instead of absolute difference")
                                   @Default("true") boolean percent,
                                   @Switch("j") boolean attachJson,
                                   @Switch("c") boolean attachCsv, @Switch("ss") boolean attach_sheet) throws IOException, GeneralSecurityException {
        Set<ResourceType> allowed = new HashSet<>(Arrays.asList(ResourceType.values));
        List<DBTrade> trades = TradeMarginByDay.getTradesByResources(allowed, start, end);

        List<ResourceType[]> tableTypes = new ArrayList<>();

        if (allowed.contains(ResourceType.FOOD)) tableTypes.add(new ResourceType[]{ResourceType.FOOD});
        tableTypes.add(new ResourceType[]{
                ResourceType.COAL,
                ResourceType.OIL,
                ResourceType.URANIUM,
                ResourceType.LEAD,
                ResourceType.IRON,
                ResourceType.BAUXITE,
        });
        tableTypes.add(new ResourceType[] {
                ResourceType.GASOLINE,
                ResourceType.MUNITIONS,
                ResourceType.STEEL,
                ResourceType.ALUMINUM
        });

        for (ResourceType[] types : tableTypes) {
            boolean[] rssIds = new boolean[ResourceType.values.length];
            for (ResourceType type : types) rssIds[type.ordinal()] = true;
            List<DBTrade> filtered = new ObjectArrayList<>();
            for (DBTrade trade : trades) {
                if (rssIds[trade.getResource().ordinal()]) {
                    filtered.add(trade);
                }
            }
            TradeMarginByDay table = new TradeMarginByDay(filtered, new HashSet<>(Arrays.asList(types)), percent);
            IMessageBuilder msg = table.writeMsg(channel.create(), attachJson, attachCsv, attach_sheet ? db : null, SheetKey.TRADE_MARGIN_DAY);
            if (Settings.INSTANCE.ENABLED_COMPONENTS.WEB) {
                JSONObject commandRss = new JSONObject(command);
                commandRss.put("resources", Arrays.stream(types).map(ResourceType::name).collect(Collectors.joining(",")));
                msg.append("\n**See also:** " + WebUtil.frontendUrl("view_graph/" + WM.api.tradeMarginByDay.cmd.getName(), commandRss));
            }
            msg.send();
        }
        return null;
    }

    @Command(desc = "Generate a graph of average trade buy and sell volume by day", viewable = true)
    public String tradevolumebyday(@Me GuildDB db, @Me IMessageIO channel, TradeManager manager, @Me JSONObject command,
                                   @Timestamp long start, @Default @Timestamp Long end,
                                   @Switch("j") boolean attachJson,
                                   @Switch("c") boolean attachCsv, @Switch("ss") boolean attach_sheet,
                                   @Switch("r") Set<ResourceType> resources) throws IOException, GeneralSecurityException {
        String title = "volume by day";
        rssTradeByDay(title, channel, start, end, offers -> manager.volumeByResource(offers), attachJson, attachCsv, attach_sheet ? db : null,
                SheetKey.TRADE_VOLUME_DAY, resources, command, WM.api.tradeVolumeByDay.cmd);
        return null;
    }

    @Command(desc = "Generate a graph of average trade buy and sell total by day", viewable = true)
    public String tradetotalbyday(@Me GuildDB db, @Me IMessageIO channel, TradeManager manager, @Me JSONObject command,
                                  @Timestamp long start, @Default @Timestamp Long end,
                                  @Switch("j") boolean attachJson,
                                  @Switch("c") boolean attachCsv, @Switch("ss") boolean attach_sheet,
                                  @Switch("r") Set<ResourceType> resources) throws IOException, GeneralSecurityException {
        String title = "total by day";
        rssTradeByDay(title, channel, start, end, offers -> manager.totalByResource(offers), attachJson, attachCsv, attach_sheet ? db : null,
                SheetKey.TRADE_TOTAL_DAY, resources, command, WM.api.tradeTotalByDay.cmd);
        return null;
    }

    public static void rssTradeByDay(String title, IMessageIO channel, long start, Long end, Function<Collection<DBTrade>, long[]> rssFunction, boolean
            attachJson,
                                     boolean attachCsv, GuildDB db, SheetKey key, Set<ResourceType> resources, JSONObject command, CommandRef ref) throws IOException {
        if (end == null) end = Long.MAX_VALUE;
        if (resources == null) resources = new LinkedHashSet<>(Arrays.asList(ResourceType.values));
        resources.remove(ResourceType.CREDITS);
        resources.remove(ResourceType.MONEY);

        Map<Long, Map<ResourceType, Map.Entry<Long, Long>>> offers = RssTradeByDay.getVolumeByDay(resources, rssFunction, start, end);

        for (ResourceType type : resources) {
            if (type == ResourceType.CREDITS || type == ResourceType.MONEY) continue;
            RssTradeByDay graph = new RssTradeByDay(title, offers, type);
            IMessageBuilder msg = graph.writeMsg(channel.create(), attachJson, attachCsv, db, key);
            if (Settings.INSTANCE.ENABLED_COMPONENTS.WEB) {
                JSONObject commandForRss = new JSONObject(command);
                commandForRss.remove("resources");
                commandForRss.put("resource", type.name());
                msg.append("\n**See also:** " + WebUtil.frontendUrl("view_graph/" + ref.getName(), commandForRss));
            }
            msg.send();
        }
    }

    @Command(desc = "List nations who have bought and sold the most of a resource over a period", viewable = true)
    public String findTrader(@Me IMessageIO channel, @Me JSONObject command, TradeManager manager, link.locutus.discord.db.TradeDB db,
                             ResourceType type,
                             @Arg("Date to start from")
                             @Timestamp long cutoff,
                             @Default @ArgChoice(value = {"SOLD", "BOUGHT"}) String buyOrSell,
                             @Arg("Group rankings by each nation's current alliance")
                             @Switch("a") boolean groupByAlliance,
                             @Arg("Include trades done outside of standard market prices")
                             @Switch("p") boolean includeMoneyTrades,
                             @Switch("n") Set<DBNation> nations) {
        if (type == ResourceType.MONEY || type == ResourceType.CREDITS) return "Invalid resource";
        List<DBTrade> offers;
        if (nations == null) {
            offers = db.getTrades(cutoff);
        } else {
            Set<Integer> nationIds = nations.stream().map(f -> f.getNation_id()).collect(IntOpenHashSet::new, IntOpenHashSet::add, IntOpenHashSet::addAll);
            offers = db.getTrades(nationIds, cutoff);
        }
        if (!includeMoneyTrades) {
            offers.removeIf(f -> manager.isTradeOutsideNormPrice(f.getPpu(), f.getResource()));
        }
        int findsign = buyOrSell.equalsIgnoreCase("SOLD") ? 1 : -1;

        Collection<Transfer> transfers = manager.toTransfers(offers, false);
        Map<Integer, double[]> inflows = manager.inflows(transfers, groupByAlliance);
        Map<Integer, double[]> ppu = manager.ppuByNation(offers, groupByAlliance);

        Map<Integer, Double> newMap = new HashMap<>();
        for (Map.Entry<Integer, double[]> entry : inflows.entrySet()) {
            double value = entry.getValue()[type.ordinal()];
            if (value != 0 && Math.signum(value) == findsign) {
                newMap.put(entry.getKey(), value);
            }
        }
        SummedMapRankBuilder<Integer, Double> builder = new SummedMapRankBuilder<>(newMap);
        Map<Integer, Double> sorted = (findsign == 1 ? builder.sort() : builder.sortAsc()).get();

        List<String> nationName = new ArrayList<>();
        List<String> amtList = new ArrayList<>();
        List<String> ppuList = new ArrayList<>();

        int i = 0;
        for (Map.Entry<Integer, Double> entry : sorted.entrySet()) {
            if (i++ >= 25) break;
            int nationId = entry.getKey();
            double amount = Math.abs(entry.getValue());
            double myPpu = ppu.get(nationId)[type.ordinal()];
//                nationName.add(MarkupUtil.markdownUrl(PW.getName(nationId, false), PW.getUrl(nationId, false)));
            nationName.add(PW.getName(nationId, groupByAlliance));
            amtList.add(MathMan.format(amount));
            ppuList.add("$" + MathMan.format(myPpu));
        }

        channel.create().embed(new EmbedBuilder()
                .setTitle("Trade Price")
                .addField("Nation", StringMan.join(nationName, "\n"), true)
                .addField("Amt", StringMan.join(amtList, "\n"), true)
                .addField("Ppu", StringMan.join(ppuList, "\n"), true)
                .build()
        ).commandButton(command, "Refresh").send();
        return null;
    }

    @Command(desc = "Create an alert when an in-game trade for a resource at an absolute price point")
    @RolePermission(Roles.TRADE_ALERT)
    @WhitelistPermission
    public String tradeAlertAbsolute(TradeDB db, @Me User  author, ResourceType resource, @ArgChoice(value={"BUY", "SELL"}) String buyOrSell, @ArgChoice(value={">", ">=", "<", "<="}) String aboveOrBelow,
                                     @Arg("Price per unit")
                                     int ppu,
                                     @Arg("How long to subscribe for")
                                     @Timediff long duration) {
        boolean isBuy = buyOrSell.equalsIgnoreCase("buy");
        if (!isBuy && !buyOrSell.equalsIgnoreCase("sell")) {
            return "Invalid category `" + buyOrSell + "`" + ". Must be either `buy` or `sell`";
        }

        boolean above = aboveOrBelow.contains(">");
        int offset = aboveOrBelow.contains("=") ? (above ? -1 : 1) : 0;
        ppu += offset;

        long date = System.currentTimeMillis() + duration;
        if (duration > TimeUnit.DAYS.toMillis(30)) {
            return "You can only subscribe for a maximum of 30 days";
        }

        db.subscribe(author, resource, date, isBuy, above, ppu, TradeDB.TradeAlertType.ABSOLUTE);

        return "Subscribed to `ABSOLUTE: " + resource + " " + buyOrSell + " PPU " + aboveOrBelow + " $" + ppu + " for " + TimeUtil.secToTime(TimeUnit.MILLISECONDS, duration) + "`" +
                "\nCheck your subscriptions with: " + CM.alerts.trade.list.cmd.toSlashMention();
    }

    @Command(desc = "Create an alert when an in-game trade for a resource is past the top price point of the opposite buy or sell offer")
    @RolePermission(Roles.MEMBER)
    @WhitelistPermission
    public String tradeAlertMistrade(TradeDB db, @Me User  author, Set<ResourceType> resources, @ArgChoice(value={">", ">=", "<", "<="}) String aboveOrBelow,
                                     @Arg("Price per unit")
                                     int ppu,
                                     @Arg("How long to subscribe for")
                                     @Timediff long duration) {
        long date = System.currentTimeMillis() + duration;
        if (duration > TimeUnit.DAYS.toMillis(30)) {
            return "You can only subscribe for a maximum of 30 days";
        }
        boolean above = aboveOrBelow.contains(">");
        int offset = aboveOrBelow.contains("=") ? (above ? -1 : 1) : 0;
        ppu += offset;
        StringBuilder response = new StringBuilder();
        for (ResourceType resource : resources) {
            db.subscribe(author, resource, date, true, above, ppu, TradeDB.TradeAlertType.MISTRADE);
            response.append("Subscribed to `MISTRADE: " + resource + " disparity " + aboveOrBelow + " $" + ppu + " for " + TimeUtil.secToTime(TimeUnit.MILLISECONDS, duration) + "`\n");
        }
        response.append("Check your subscriptions with: " + CM.alerts.trade.list.cmd.toSlashMention());
        return response.toString();
    }

    @Command(desc = "Create an alert for specific differences between buy and sell prices for in-game resource trades")
    @RolePermission(Roles.MEMBER)
    @WhitelistPermission
    public String tradeAlertDisparity(TradeDB db, @Me User  author, Set<ResourceType> resources, @ArgChoice(value={">", ">=", "<", "<="}) String aboveOrBelow,
                                      @Arg("Price per unit")
                                      int ppu,
                                       @Arg("How long to subscribe for")
                                      @Timediff long duration) {
        long date = System.currentTimeMillis() + duration;
        if (duration > TimeUnit.DAYS.toMillis(30)) {
            return "You can only subscribe for a maximum of 30 days";
        }
        boolean above = aboveOrBelow.contains(">");
        int offset = aboveOrBelow.contains("=") ? (above ? -1 : 1) : 0;
        ppu += offset;
        StringBuilder response = new StringBuilder();
        for (ResourceType resource : resources) {
            db.subscribe(author, resource, date, true, above, ppu, TradeDB.TradeAlertType.DISPARITY);
            response.append("Subscribed to `DISPARITY: " + resource + " ppu " + aboveOrBelow + " $" + ppu + " for " + TimeUtil.secToTime(TimeUnit.MILLISECONDS, duration) + "`\n");
        }
        response.append("Check your subscriptions with: " + CM.alerts.trade.list.cmd.toSlashMention());
        return response.toString();
    }

    @Command(desc = "Create an alert when there are no standing offers for resources in-game")
    @RolePermission(Roles.MEMBER)
    @WhitelistPermission
    public String tradeAlertNoOffer(TradeDB db, @Me User  author, Set<ResourceType> resources,
                                    @Arg("How long to subscribe for")
                                    @Timediff long duration) {
        long date = System.currentTimeMillis() + duration;
        if (duration > TimeUnit.DAYS.toMillis(30)) {
            return "You can only subscribe for a maximum of 30 days";
        }
        StringBuilder response = new StringBuilder();
        for (ResourceType resource : resources) {
            db.subscribe(author, resource, date, true, true, 0, TradeDB.TradeAlertType.NO_OFFER);
            response.append("Subscribed to `NO_OFFER: " + resource + " for " + TimeUtil.secToTime(TimeUnit.MILLISECONDS, duration) + "`\n");
        }
        response.append("Check your subscriptions with: " + CM.alerts.trade.list.cmd.toSlashMention());
        return response.toString();
    }

    @Command(desc = "Create an alert when a top offer you have in-game is undercut by another nation")
    @RolePermission(Roles.MEMBER)
    @WhitelistPermission
    public String tradeAlertUndercut(TradeDB db, @Me User  author, Set<ResourceType> resources, @ArgChoice(value={"BUY", "SELL", "*"}) String buyOrSell,
                                     @Arg("How long to subscribe for")
                                     @Timediff long duration) {
        long date = System.currentTimeMillis() + duration;
        if (duration > TimeUnit.DAYS.toMillis(30)) {
            return "You can only subscribe for a maximum of 30 days";
        }
        boolean isBuy = buyOrSell.equalsIgnoreCase("buy");
        StringBuilder response = new StringBuilder();
        for (ResourceType resource : resources) {
            db.subscribe(author, resource, date, isBuy, true, 0, TradeDB.TradeAlertType.UNDERCUT);
            response.append("Subscribed to `UNDERCUT: " + resource + " " + buyOrSell + " for " + TimeUtil.secToTime(TimeUnit.MILLISECONDS, duration) + "`\n");
        }
        response.append("Check your subscriptions with: " + CM.alerts.trade.list.cmd.toSlashMention());
        return response.toString();
    }

    @Command(desc = "Unsubscribe from trade alerts")
    public String unsubTrade(@Me User author, ResourceType resource) {
        TradeDB db = Locutus.imp().getTradeManager().getTradeDb();
        db.unsubscribe(author, resource);
        return "Unsubscribed from " + resource + " alerts";
    }

    @Command(desc = "View your trade alert subscriptions", viewable = true)
    public String tradeSubs(@Me User author, @Me IMessageIO io) {
        List<TradeSubscription> subscriptions = Locutus.imp().getTradeManager().getTradeDb().getSubscriptions(author.getIdLong());
        if (subscriptions.isEmpty()) {
            return "No subscriptions. Subscribe to get alerts using:\n" +
                    "- " + CM.alerts.trade.margin.cmd.toSlashMention() + "\n" +
                    "- " + CM.alerts.trade.price.cmd.toSlashMention() + "\n" +
                    "- " + CM.alerts.trade.mistrade.cmd.toSlashMention() + "\n" +
                    "- " + CM.alerts.trade.no_offers.cmd.toSlashMention() + "\n" +
                    "- " + CM.alerts.trade.undercut.cmd.toSlashMention();
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
            CM.alerts.trade.unsubscribe unsubCommand = CM.alerts.trade.unsubscribe.cmd.resource(type.name());

            body.append("\n\n").append("*Press `" + emoji + "` to unsubscribe*");

            io.create().embed(title, body.toString()).commandButton(unsubCommand, emoji).send();
        }

        return null;
    }
}