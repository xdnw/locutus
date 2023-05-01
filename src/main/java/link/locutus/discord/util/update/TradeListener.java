package link.locutus.discord.util.update;

import com.google.common.eventbus.Subscribe;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.NationColor;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.commands.manager.v2.impl.pw.NationFilter;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.TradeDB;
import link.locutus.discord.db.entities.Coalition;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DBTrade;
import link.locutus.discord.db.entities.DBTreasure;
import link.locutus.discord.db.entities.TradeSubscription;
import link.locutus.discord.event.game.TurnChangeEvent;
import link.locutus.discord.event.trade.BulkTradeSubscriptionEvent;
import link.locutus.discord.event.treasure.TreasureUpdateEvent;
import link.locutus.discord.pnw.AllianceList;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.AlertUtil;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.Role;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

public class TradeListener {

    @Subscribe
    public void onTreasureChange(TreasureUpdateEvent event) {
        // if a treasure respawns, post an alert

        // If a nation is not DNR, the attacker has 80% of the military to attack, and there are not 3 strong counters
        long now = System.currentTimeMillis();
        long expireAfter = TimeUnit.DAYS.toMillis(60);

        DBTreasure previous = event.getPrevious();
        DBTreasure treasure = event.getCurrent();
        String respawnTime = TimeUtil.secToTime(TimeUnit.MILLISECONDS, treasure.getSpawnDate() + expireAfter - now);
        String title = "Treasure Alert: " + treasure.getName();

        StringBuilder body = new StringBuilder();
        body.append("Respawns in: " + respawnTime + "\n");
        body.append("Nation Bonus: " + treasure.getBonus() + "%\n");
        if (previous != null) {
            if (previous.getNation_id() != treasure.getNation_id()) {
                DBNation previousNation = DBNation.byId(previous.getNation_id());
                if (previousNation == null) {
                    body.append("Previous owner: " + previous.getNation_id() + " (deleted)\n");
                } else {
                    body.append("Previous owner: " + previousNation.getNationUrlMarkup(true) + " | " + previousNation.getAllianceUrlMarkup(true) + "\n");
                }
            }
        }

        DBNation currentNation = DBNation.byId(treasure.getNation_id());
        if (currentNation == null) {
            body.append("Current owner: " + treasure.getNation_id() + " (deleted)\n");
        } else {
            body.append(currentNation.toEmbedString(false));
            body.append("\nCan be attacked by: " + MathMan.format(currentNation.getScore() / 1.75) + "-" + MathMan.format(currentNation.getScore() / 0.75));
        }

        AlertUtil.forEachChannel(f -> f.isWhitelisted() && f.hasCoalitionPermsOnRoot(Coalition.RAIDPERMS), GuildDB.Key.TREASURE_ALERT_CHANNEL, new BiConsumer<MessageChannel, GuildDB>() {
            @Override
            public void accept(MessageChannel channel, GuildDB db) {
                DiscordUtil.createEmbedCommand(channel, title, body.toString());
            }
        });
    }

    @Subscribe
    public void onTurnChange(TurnChangeEvent event) {
        Set<DBTreasure> treasuresToAlert = new HashSet<>();
        long now = System.currentTimeMillis();
        long expireAfter = TimeUnit.DAYS.toMillis(60);

        // TODO list all the treasures expiring in 5 days
        // TODO dont alert to change color if they are in range of a treasure in 5 days

        boolean postAlert = false;
        for (DBTreasure treasure : Locutus.imp().getNationDB().getTreasuresByName().values()) {
            // no point alerting when no color change is necessary
            if (treasure.getColor() == null) continue;
            // post alert only if a treasure respawns in <1 day and an alert has not been posted for that treasure already
            if (treasure.getTimeUntilNextSpawn() < TimeUnit.DAYS.toMillis(1)
                && treasure.getTimeSinceLastAlert() > TimeUnit.DAYS.toMillis(6)) {
                postAlert = true;
            }
        }

        if (!postAlert) return;
        Set<NationColor> treasureColors = new HashSet<>();

        for (DBTreasure treasure : Locutus.imp().getNationDB().getTreasuresByName().values()) {
            if (treasure.getColor() == null) continue;
            if (treasure.getTimeUntilNextSpawn() < TimeUnit.DAYS.toMillis(5)
            && treasure.getTimeSinceLastAlert() > TimeUnit.DAYS.toMillis(6)) {
                treasureColors.add(treasure.getColor());
                // update last respawn alert
                treasure.setRespawnAlertDate(now);

                treasuresToAlert.add(treasure);
            }
        }
        Locutus.imp().getNationDB().saveTreasures(treasuresToAlert);

        DBNation maxNation = null;
        for (DBNation nation : Locutus.imp().getNationDB().getNations().values()) {
            if (maxNation == null || maxNation.getScore() < nation.getScore()) {
                maxNation = nation;
            }
            // add to color count
        }
        if (maxNation == null) return;

        double maxNationScore = maxNation.getScore();
        double maxScore = maxNationScore * 0.65;
        double minScore = maxNationScore * 0.15;


        long valuePerTreasure = 1_000_000_000L;
        Map<DBTreasure, Integer> nationsPerTreasure = new HashMap<>();
        Map<DBTreasure, Integer> probabilisticValues = new HashMap<>();
        for (DBTreasure treasure : treasuresToAlert) {
            int nations = treasure.getNationsInRange(maxNationScore).size();
            nationsPerTreasure.put(treasure, nations);
            probabilisticValues.put(treasure, (int) (valuePerTreasure / (1 + nations)));
        }

        boolean hasContinent = false;
        StringBuilder message = new StringBuilder();
        for (DBTreasure treasure : treasuresToAlert) {
            NationColor color = treasure.getColor();
            int nations = nationsPerTreasure.get(treasure);
            long value = probabilisticValues.get(treasure);

            message.append("\n**Treasure**: ").append(treasure.getName());
            message.append("\n - Expected Value: $").append(MathMan.format(value) + " (" + nations + " in treasure range)");
            if (color != null) {
                message.append("\n - color: " + color)
                        .append("(rev: $" + MathMan.format(color.getTurnBonus()) + "/turn OR $" +
                                MathMan.format(color.getTurnBonus() * (5 * 12)) + "/5d)");
            }
            if (treasure.getContinent() != null) {
                hasContinent = true;
                message.append("\n - continent: ").append(treasure.getContinent());
            }
            long respawnsAt = treasure.getSpawnDate() + expireAfter;
            long respawnsIn = respawnsAt - now;
            message.append("\n - respawns in: ").append(TimeUtil.secToTime(TimeUnit.MILLISECONDS, respawnsIn));
            String url = "https://www.epochconverter.com/countdown?q=" + (respawnsAt);
            message.append(" <").append(url).append(">");
        }
        message.append("\n\n");
        message.append("\n**Eligible Score Range: **" + MathMan.format(minScore) + "-" + MathMan.format(maxScore));

        message.append("\n\nNotes:");
        if (hasContinent) {
            message.append("\n - You must be on the correct continent and color to get a treasure spawn");
        }
        message.append("\n - You will NOT receive color revenue if you are NOT your alliance color (or beige)");
        message.append("\n - All Treasures: <https://politicsandwar.com/leaderboards/display=treasures>");
        message.append("\n - All Colors: <https://politicsandwar.com/leaderboards/display=color>");
        message.append("\n - Edit Color: <https://politicsandwar.com/nation/edit/>");

        AlertUtil.forEachChannel(f -> f.isWhitelisted() && f.hasCoalitionPermsOnRoot(Coalition.RAIDPERMS), GuildDB.Key.TREASURE_ALERT_CHANNEL, new BiConsumer<MessageChannel, GuildDB>() {
            @Override
            public void accept(MessageChannel channel, GuildDB db) {
                AllianceList allianceList = db.getAllianceList();
                if (allianceList == null || allianceList.isEmpty()) return;
                Set<DBAlliance> alliances = allianceList.getAlliances();
//                NationColor allianceColor = alliance.getColor();

                StringBuilder aaMessage = new StringBuilder(message);

                aaMessage.append("\n\nAlliance Color: ");
                if (alliances.size() == 1) {
                    DBAlliance alliance = alliances.iterator().next();
                    long colorRev = alliance.getColor().getTurnBonus();
                    long colorRev5Days = colorRev * 12 * 5;
                    aaMessage.append(alliance.getColor());
                    aaMessage.append(" | Rev: $").append(MathMan.format(colorRev)).append("/turn OR $").append(MathMan.format(colorRev5Days)).append("/5day");
                } else {
                    for (DBAlliance alliance : alliances) {
                        aaMessage.append("\n - ");
                        long colorRev = alliance.getColor().getTurnBonus();
                        long colorRev5Days = colorRev * 12 * 5;
                        aaMessage.append(alliance.getColor());
                        aaMessage.append(" | Rev: $").append(MathMan.format(colorRev)).append("/turn OR $").append(MathMan.format(colorRev5Days)).append("/5day");
                    }
                }
                aaMessage.append("\n - To retain color income, wait for confirmation from a gov member, leave ingame, make (or join) an alliance called e.g. `" + db.getGuild().getName() + "-<color>`, and send a Protectorate treaty. Disband the alliance after 5 days");

                Role optOut = Roles.TREASURE_ALERT_OPT_OUT.toRole(db);
                Role optIn = Roles.TREASURE_ALERT.toRole(db);
                Role memberRole = Roles.MEMBER.toRole(db);
                if (optIn != null) {
                    Guild guild = db.getGuild();
                    List<String> mentions = new ArrayList<>();

                    Set<Member> members = new HashSet<>(guild.getMembersWithRoles(memberRole));
                    members.addAll(guild.getMembersWithRoles(optIn));
                    for (Member member : members) {
                        DBNation nation = DiscordUtil.getNation(member.getUser());
                        if (nation == null || nation.getColorTurns() > 0 || nation.isBeige()) continue;
                        if (nation.getVm_turns() > 0 || nation.active_m() > 7200) continue;
                        if (nation.getNumWars() > 0 && (nation.getDef() > 0 || nation.getRelativeStrength() < 1)) continue;
                        if (treasureColors.contains(nation.getColor())) continue;

                        for (DBTreasure treasure : treasuresToAlert) {
                            if (treasure.getColor() == null || nation.getColor() == treasure.getColor()) continue;
                            NationFilter filter = treasure.getFilter(maxNationScore, false, true);
                            // not in score range or continent
                            if (!filter.test(nation)) continue;

                            String mention;
                            if ((optOut != null && member.getRoles().contains(optOut)) || !member.getRoles().contains(optIn)) {
                                mention = nation.getName();
                            } else {
                                mention = member.getAsMention();
                            }
                            mentions.add(mention);
                            break;
                        }
                    }
                    if (!mentions.isEmpty()) {
                        aaMessage.append("\n\nThe following nations are in range of a treasure spawn if they switch color:");
                        aaMessage.append("\n - ").append(String.join("\n - ", mentions));
                    }
                }

                DiscordUtil.sendMessage(channel, aaMessage.toString());
            }
        });

        for (DBTreasure treasure : treasuresToAlert) {
            // Treasure X days on red[/continent]
            // Link to epochconverter.com
            // Color bonus for alliance
            // Color bonus for new color



            // Get nations in alliance and correct continent, but wrong color
            // Remove nations in wrong score range


        }

    }

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
            if (channel == null || subscriptionsForGuild.isEmpty()) continue;

            Set<String> allPings = new LinkedHashSet<>();
            IMessageBuilder msg = new DiscordChannelIO(channel).create();

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
                body.append(MarkupUtil.markdownUrl("Selling", resource.url(true, true)) + ": ");
                body.append(toPriceString(oldSell) + " -> " + toPriceString(newSell));
                if (sellChange) body.append("**");
                body.append("\n");

                body.append("\n\n");

                if (buyChange) body.append("**");
                body.append(MarkupUtil.markdownUrl("Buying", resource.url(false, true)) + ": ");
                body.append(toPriceString(oldBuy) + " -> " + toPriceString(newBuy));
                if (buyChange) body.append("**");

                body.append("\n\n" + MarkupUtil.markdownUrl("BuySell Link", resource.url(null, true)));

                List<String> pings = new ArrayList<>();
                for (TradeSubscription sub : rssSubs) {
                    String ping = sub.getPing(db) + " " + sub.getType() + " " + (sub.isBuy() ? "buying" : "selling") + " " + (sub.isAbove() ? "above" : "below") + " $" + sub.getPpu() + (sub.getDate() != Long.MAX_VALUE ? " (expires: " + TimeUtil.secToTime(TimeUnit.MILLISECONDS, sub.getDate() - System.currentTimeMillis()) + ")" : "");
                    pings.add(ping);
                }

                allPings.addAll(pings);

                msg.append("**__## " + title + " ##__**\n" + body + "\n - " + StringMan.join(pings, "\n - ") + "\n---------\n");
            }
            msg.send();
        }
    }

    private String toPriceString(DBTrade trade) {
        if (trade == null) return "(none)";
        return trade.getPpu() + "ppu (" + trade.getQuantity() + "x)";
    }
}
