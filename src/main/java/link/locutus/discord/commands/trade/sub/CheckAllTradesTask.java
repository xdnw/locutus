package link.locutus.discord.commands.trade.sub;

import link.locutus.discord.Locutus;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.scheduler.CaughtRunnable;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.AlertUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.trade.TradeDB;
import link.locutus.discord.apiv1.enums.ResourceType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.Role;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;

public class CheckAllTradesTask extends CaughtRunnable implements Callable<Boolean> {
    private final Set<ResourceType> values;

    public CheckAllTradesTask(ResourceType... values) {
        this.values = new HashSet<>(Arrays.asList(values));
    }

    @Override
    public Boolean call() throws Exception {
        try {
            for (ResourceType type : values) {
                if (type == ResourceType.MONEY) continue;

                new CheckTradesTask(type, new TradeAlertConsumer() {
                    @Override
                    public void accept(Set<Long> pings, link.locutus.discord.db.TradeDB.TradeAlertType alertType, TradeAlert alert, boolean checkRole) {
                        if (alertType == link.locutus.discord.db.TradeDB.TradeAlertType.MIXUP) {
                            if (alert.getCurrentHighNation() != null)
                                return;
                            if (alert.getCurrentLowNation() != null)
                                return;
                        }
                        String title = type.name() + " " + alertType.name();
                        if (pings == null) {
                            if (alertType == link.locutus.discord.db.TradeDB.TradeAlertType.MIXUP && Settings.INSTANCE.LEGACY_SETTINGS.OPEN_DESKTOP_FOR_MISTRADES) {
                                String url;
                                url = type.url(alert.getPreviousHigh() != alert.getPreviousHigh(), false);
                                AlertUtil.openDesktop(url);
                            }
                            AlertUtil.forEachChannel(AlertTrades.class, GuildDB.Key.TRADE_ALERT_CHANNEL, new BiConsumer<MessageChannel, GuildDB>() {
                                @Override
                                public void accept(MessageChannel channel, GuildDB guildDB) {
                                    alert.toCard(title, channel);
                                    Guild guild = guildDB.getGuild();
                                    Role role = Roles.TRADE_ALERT.toRole(guild);
                                    if (role != null) {
                                        RateLimitUtil.queue(channel.sendMessage(role.getAsMention()));
                                    }
                                }
                            });
                        } else if (!pings.isEmpty()) {
                            AlertUtil.forEachChannel(AlertTrades.class, GuildDB.Key.TRADE_ALERT_CHANNEL, pings, new BiConsumer<Map.Entry<Guild, MessageChannel>, Set<Member>>() {
                                @Override
                                public void accept(Map.Entry<Guild, MessageChannel> entry, Set<Member> members) {
                                    if (members.isEmpty()) return;
                                    MessageChannel channel = entry.getValue();
                                    StringBuilder pingStr = new StringBuilder();
                                    Role role = Roles.TRADE_ALERT.toRole(entry.getKey());
                                    if (!checkRole || role != null) {
                                        for (Member member : members) {
                                            if (!checkRole || member.getRoles().contains(role)) {
                                                pingStr.append(member.getAsMention()).append(" ");
                                            }
                                        }
                                        if (!members.isEmpty() && pingStr.length() != 0) {
                                            alert.toCard(title, channel);
                                            RateLimitUtil.queue(channel.sendMessage(pingStr));
                                        }
                                    }
                                }
                            });
                        }
                    }
                }).call();
//            new CheckTradesTask(type, new BiConsumer<TradeDB.TradeAlertType, TradeAlert>() {
//                @Override
//                public void accept(TradeDB.TradeAlertType alertType, TradeAlert alert) {
//                    switch (alertType) {
//                        case MIXUP:
//                            Set<Long> subs = db.getSubscriptions(type, alertType);
//                            break;
//                        case UNDERCUT:
//                            break;
//                        case DISPARITY:
//                            break;
//                        case ABSOLUTE:
//                            break;
//                        case NO_LOW:
//                            break;
//                        case NO_HIGH:
//                            break;
//                    }
////                    Map.Entry<Boolean, Integer> key = entry.getKey();
////
////                    String url;
////                    String title;
////                    if (key == null) {
////                        title = "Trade mixup for " + type.name();
////                        url = url(type, true);
////                    } else {
////                        title = (key.getKey() ? "BUY" : "SELL") + " " + type.name() + " @ $" + key.getValue();
////                        url = url(type, key.getKey());
////                    }
////
////                    HashSet<Long> mentionIds = new HashSet<>(entry.getValue());
////                    mentionIds.add(Settings.INSTANCE.Discord.BORG_USER_ID);
////
////                    int low = Locutus.imp().getTradeManager().getLow(type);
////                    int high = Locutus.imp().getTradeManager().getHigh(type);
////
////                    StringBuilder body = new StringBuilder();
////                    body.append(MarkupUtil.markdownUrl("Low: $" + MathMan.format(low), url(type, true))).append("\n");
////                    body.append(MarkupUtil.markdownUrl("High: $" + MathMan.format(high), url(type, false)));
////
////                    AlertUtil.forEachChannel(AlertTrades.class, GuildDB.Key.TRADE_ALERT_CHANNEL, mentionIds, new BiConsumer<MessageChannel, Set<Member>>() {
////                        @Override
////                        public void accept(MessageChannel channel, Set<Member> members) {
////                            StringBuilder mentions = new StringBuilder();
////                            for (Member member : members) {
////                                mentions.append(member.getAsMention()).append(" ");
////                            }
////                            if (!members.isEmpty() || key == null) {
////                                DiscordUtil.createEmbedCommand(channel, title, body.toString());
////                                link.locutus.discord.util.RateLimitUtil.queue(channel.sendMessage(mentions.toString()));
////                            }
////                        }
////                    });
//                }
//            }).call();
            }
            if (this.values.contains(ResourceType.CREDITS)) {
                TradeDB tradeMan = Locutus.imp().getTradeManager();
                int max = 0;
                ResourceType maxType = null;
                for (ResourceType type : ResourceType.values) {
                    if (type == ResourceType.MONEY || type == ResourceType.CREDITS) continue;
                    int low = tradeMan.getLow(type);
                    if (((type == ResourceType.FOOD) ? (low * 30) : (low)) > max) {
                        max = low;
                        maxType = type;
                    }
                }
                double amt = maxType == ResourceType.FOOD ? 150000 : 5000;
                int creditPrice = tradeMan.getHigh(ResourceType.CREDITS);

                if (creditPrice / amt < max) {
                    long currentTurn = TimeUtil.getTurn();

                    if (creditTurn != currentTurn) {
                        creditTurn = currentTurn;
                        String title = "BUY CREDITS @ $" + MathMan.format(creditPrice);
                        String body = "convert to: " + maxType.name() + " @ $" + MathMan.format(creditPrice);
                        AlertUtil.forEachChannel(AlertTrades.class, GuildDB.Key.TRADE_ALERT_CHANNEL, new BiConsumer<MessageChannel, GuildDB>() {
                            @Override
                            public void accept(MessageChannel channel, GuildDB guildDB) {
                                DiscordUtil.createEmbedCommand(channel, title, body);
                            }
                        });
                    }
                }
            }
            return true;
        } catch (Throwable e) {
            e.printStackTrace();
            AlertUtil.error("Unable to fetch trades", e);
            return false;
        }
    }

    private long creditTurn = 0;

    public String url(ResourceType type, boolean isBuy) {
        String url = "" + Settings.INSTANCE.PNW_URL() + "/index.php?id=90&display=world&resource1=%s&buysell=" + (isBuy ? "buy" : "sell") + "&ob=price&od=DEF";
        return String.format(url, type.name().toLowerCase());
    }

    @Override
    public void runUnsafe() {
        try {
            call();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
