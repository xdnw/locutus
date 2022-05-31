package com.boydti.discord.commands.manager;

import com.boydti.discord.Locutus;
import com.boydti.discord.commands.external.account.Login;
import com.boydti.discord.commands.external.account.Logout;
import com.boydti.discord.commands.external.guild.CardCommand;
import com.boydti.discord.commands.external.guild.ChannelCommand;
import com.boydti.discord.commands.external.guild.ClearNicks;
import com.boydti.discord.commands.external.guild.ClearRoles;
import com.boydti.discord.commands.external.guild.CopyPasta;
import com.boydti.discord.commands.external.guild.ImportEmoji;
import com.boydti.discord.commands.external.guild.Meta;
import com.boydti.discord.commands.external.guild.SyncBounties;
import com.boydti.discord.commands.external.guild.UpdateEmbed;
import com.boydti.discord.commands.external.guild.WarCat;
import com.boydti.discord.commands.external.guild.WarPin;
import com.boydti.discord.commands.external.guild.WarRoom;
import com.boydti.discord.commands.account.Embassy;
import com.boydti.discord.commands.account.GuildInfo;
import com.boydti.discord.commands.account.HasRole;
import com.boydti.discord.commands.account.question.Interview;
import com.boydti.discord.commands.account.RunAllNations;
import com.boydti.discord.commands.account.Runall;
import com.boydti.discord._test.command.wip.AllianceBuildupSheet;
import com.boydti.discord.commands.alliance.Dm;
import com.boydti.discord.commands.alliance.LeftAA;
import com.boydti.discord.commands.alliance.ModifyTreaty;
import com.boydti.discord.commands.alliance.SendTreaty;
import com.boydti.discord.commands.alliance.SetBracket;
import com.boydti.discord.commands.sync.SyncTreaties;
import com.boydti.discord.commands.alliance.Unregister;
import com.boydti.discord.commands.bank.AddBalance;
import com.boydti.discord.commands.bank.AddTaxBracket;
import com.boydti.discord.commands.bank.TransferResources;
import com.boydti.discord.commands.info.CounterStats;
import com.boydti.discord.commands.bank.FindOffshore;
import com.boydti.discord.commands.external.guild.KickLocutus;
import com.boydti.discord.commands.info.FindSpyOp;
import com.boydti.discord.commands.external.guild.Mask;
import com.boydti.discord.commands.sync.SyncMail;
import com.boydti.discord.commands.war.WarCategory;
import com.boydti.discord.commands.fun.Borgomas;
import com.boydti.discord.commands.fun.Commend;
import com.boydti.discord.commands.fun.Kev;
import com.boydti.discord.commands.fun.Lury;
import com.boydti.discord.commands.fun.Nev;
import com.boydti.discord.commands.fun.SriCommand;
import com.boydti.discord._test.command.wip.Audit;
import com.boydti.discord.commands.info.ChannelCount;
import com.boydti.discord.commands.info.CityCost;
import com.boydti.discord.commands.info.DummyCommand;
import com.boydti.discord.commands.info.InfraCost;
import com.boydti.discord.commands.info.Invite;
import com.boydti.discord.commands.info.LandCost;
import com.boydti.discord.commands.info.ProjectSlots;
import com.boydti.discord.commands.info.Rebuy;
import com.boydti.discord.commands.info.Score;
import com.boydti.discord.commands.info.Treaties;
import com.boydti.discord.commands.info.TurnTimer;
import com.boydti.discord.commands.info.UnitHistory;
import com.boydti.discord.commands.info.WeeklyInterest;
import com.boydti.discord.commands.manager.dummy.DelegateMessage;
import com.boydti.discord.commands.manager.dummy.DelegateMessageEvent;
import com.boydti.discord.commands.manager.v2.impl.pw.CommandManager2;
import com.boydti.discord.commands.rankings.AllianceAttackTypeRanking;
import com.boydti.discord.commands.rankings.AllianceLootLosses;
import com.boydti.discord.commands.rankings.AttackTypeBreakdownAB;
import com.boydti.discord.commands.rankings.LargestBanks;
import com.boydti.discord.commands.rankings.MilitaryRanking;
import com.boydti.discord.commands.rankings.MyLoot;
import com.boydti.discord.commands.rankings.WarCostByDay;
import com.boydti.discord.commands.rankings.WarRanking;
import com.boydti.discord.commands.rankings.WarCostRankingByDay;
import com.boydti.discord.commands.rankings.WarsByTier;
import com.boydti.discord.commands.sheets.ActivitySheet;
import com.boydti.discord.commands.sheets.AllianceSheet;
import com.boydti.discord.commands.sheets.CoalitionSheet;
import com.boydti.discord.commands.sheets.CounterSheet;
import com.boydti.discord.commands.sheets.DepositsSheet;
import com.boydti.discord.commands.sheets.DeserterSheet;
import com.boydti.discord.commands.sheets.FASheet;
import com.boydti.discord.commands.sheets.IASheet;
import com.boydti.discord.commands.sheets.IntelOpSheet;
import com.boydti.discord.commands.sheets.InterviewSheet;
import com.boydti.discord.commands.sheets.MMRSheet;
import com.boydti.discord.commands.sheets.MailTargets;
import com.boydti.discord.commands.sheets.NationSheet;
import com.boydti.discord.commands.sheets.NoteSheet;
import com.boydti.discord.commands.sheets.ProjectSheet;
import com.boydti.discord.commands.sheets.ROI;
import com.boydti.discord.commands.bank.SafekeepCommand;
import com.boydti.discord.commands.external.guild.Setup;
import com.boydti.discord.commands.war.WarInfo;
import com.boydti.discord.commands.external.guild.CheckPermission;
import com.boydti.discord.commands.external.guild.KeyStore;
import com.boydti.discord.commands.external.guild.Permission;
import com.boydti.discord.commands.external.account.ForumScrape;
import com.boydti.discord.commands.external.account.LoadUsers;
import com.boydti.discord.commands.account.Say;
import com.boydti.discord.commands.bank.Warchest;
import com.boydti.discord._test.command.wip.debug.EmptyAATest;
import com.boydti.discord.commands.fun.Jokes;
import com.boydti.discord.commands.info.BeigeTurns;
import com.boydti.discord.commands.info.ListMultisByAlliance;
import com.boydti.discord.commands.info.Multi;
import com.boydti.discord.commands.info.optimal.OptimalBuild;
import com.boydti.discord.commands.rankings.AAMembers;
import com.boydti.discord.commands.rankings.AllianceLootRanking;
import com.boydti.discord.commands.bank.Inflows;
import com.boydti.discord.commands.bank.SyncBanks;
import com.boydti.discord.commands.bank.BankWith;
import com.boydti.discord.commands.info.Reroll;
import com.boydti.discord.commands.account.CheckMail;
import com.boydti.discord.commands.info.PendingCommand;
import com.boydti.discord.commands.info.HelpCommand;
import com.boydti.discord.commands.fun.Tag;
import com.boydti.discord.commands.account.AutoRole;
import com.boydti.discord.commands.account.RegisterCommand;
import com.boydti.discord.commands.account.RoleAlias;
import com.boydti.discord.commands.coalition.GetCoalitions;
import com.boydti.discord.commands.coalition.RemoveCoalition;
import com.boydti.discord.commands.coalition.SetCoalition;
import com.boydti.discord.commands.fun.BorgCommand;
import com.boydti.discord.commands.rankings.InactiveAlliances;
import com.boydti.discord.commands.rankings.NationLootRanking;
import com.boydti.discord.commands.rankings.NetProfitPerWar;
import com.boydti.discord.commands.rankings.UnitRanking;
import com.boydti.discord.commands.rankings.ProlificOffshores;
import com.boydti.discord.commands.rankings.TopAABeigeLoot;
import com.boydti.discord.commands.rankings.WarCostRanking;
import com.boydti.discord.commands.rankings.WarCostAB;
import com.boydti.discord.commands.rankings.WarLossesPerCity;
import com.boydti.discord.commands.sheets.SpySheet;
import com.boydti.discord.commands.sheets.StockpileSheet;
import com.boydti.discord.commands.sheets.TaxBracketSheet;
import com.boydti.discord.commands.sheets.ValidateBlitzSheet;
import com.boydti.discord.commands.sheets.ValidateSpyBlitzSheet;
import com.boydti.discord.commands.sheets.WarCitySheet;
import com.boydti.discord.commands.sheets.WarCostByAASheet;
import com.boydti.discord.commands.sheets.WarCostByResourceSheet;
import com.boydti.discord.commands.sheets.WarCostSheet;
import com.boydti.discord.commands.sheets.CombatantSheet;
import com.boydti.discord.commands.sheets.StrengthCitySheet;
import com.boydti.discord.commands.sheets.WarSheet;
import com.boydti.discord.commands.sync.SyncTaxes;
import com.boydti.discord.commands.sync.SyncTrade;
import com.boydti.discord.commands.sync.SyncUid;
import com.boydti.discord.commands.sync.SyncWarRooms;
import com.boydti.discord.commands.sync.SyncWars;
import com.boydti.discord.commands.trade.FindProducer;
import com.boydti.discord.commands.trade.FindTrader;
import com.boydti.discord.commands.trade.GlobalTradeVolume;
import com.boydti.discord.commands.trade.Inactive;
import com.boydti.discord.commands.trade.TradeId;
import com.boydti.discord.commands.trade.TradeMargin;
import com.boydti.discord.commands.trade.TradePriceCmd;
import com.boydti.discord.commands.trade.GlobalTradeAverage;
import com.boydti.discord.commands.trade.ConvertedTotal;
import com.boydti.discord.commands.trade.TradeRanking;
import com.boydti.discord.commands.trade.Trending;
import com.boydti.discord.commands.trade.sub.AlertTrades;
import com.boydti.discord.commands.trade.sub.CheckTradesCommand;
import com.boydti.discord.commands.trade.sub.TradeSubscriptions;
import com.boydti.discord.commands.trade.sub.UnsubTrade;
import com.boydti.discord.commands.trade.subbank.BankAlerts;
import com.boydti.discord.commands.trade.subbank.BankSubscriptions;
import com.boydti.discord.commands.trade.subbank.UnsubBank;
import com.boydti.discord.commands.war.Counter;
import com.boydti.discord.commands.war.CounterSpy;
import com.boydti.discord.commands.war.Damage;
import com.boydti.discord.commands.war.IntelOp;
import com.boydti.discord.commands.war.Loot;
import com.boydti.discord.commands.external.guild.MsgInfo;
import com.boydti.discord.commands.war.Simulate;
import com.boydti.discord.commands.account.Sudo;
import com.boydti.discord.commands.fun.TagCommand;
import com.boydti.discord.commands.sheets.OptimalTrades;
import com.boydti.discord.commands.buildcmd.AddBuild;
import com.boydti.discord.commands.bank.Bank;
import com.boydti.discord.commands.buildcmd.AssignBuild;
import com.boydti.discord.commands.buildcmd.DeleteBuild;
import com.boydti.discord.commands.buildcmd.GetBuild;
import com.boydti.discord.commands.war.WarCommand;
import com.boydti.discord.commands.info.MeCommand;
import com.boydti.discord.commands.trade.MoneyTrades;
import com.boydti.discord.commands.info.Revenue;
import com.boydti.discord.commands.war.SpyCommand;
import com.boydti.discord.commands.trade.TradeProfit;
import com.boydti.discord.commands.war.Spyops;
import com.boydti.discord.commands.info.Who;
import com.boydti.discord.commands.compliance.CheckCities;
import com.boydti.discord.commands.sync.SyncAttacks;
import com.boydti.discord.commands.sync.SyncCommand;
import com.boydti.discord.commands.bank.Deposits;
import com.boydti.discord.commands.bank.Disperse;
import com.boydti.discord.commands.alliance.EditAlliance;
import com.boydti.discord.commands.alliance.MailCommand;
import com.boydti.discord.commands.bank.Offshore;
import com.boydti.discord.commands.alliance.SetRank;
import com.boydti.discord.commands.sheets.BlitzSheet;
import com.boydti.discord.config.Messages;
import com.boydti.discord.config.Settings;
import com.boydti.discord.db.DiscordDB;
import com.boydti.discord.db.GuildDB;
import com.boydti.discord.db.entities.DiscordMeta;
import com.boydti.discord.db.entities.NationMeta;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.util.*;
import com.boydti.discord.util.scheduler.CaughtRunnable;
import com.boydti.discord.user.Roles;
import com.boydti.discord.util.discord.DiscordUtil;
import com.boydti.discord.util.task.balance.loan.LoanCommand;
import com.boydti.discord.apiv1.enums.WarPolicy;
import com.boydti.discord.web.jooby.adapter.JoobyChannel;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import com.boydti.discord.commands.bank.GrantCmd;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

public class CommandManager {
    private final char prefix1;
    private final char prefix2;
    private final Locutus locutus;
    private Tag tag;
    private final ScheduledThreadPoolExecutor executor;
    private Map<String, Command> commandMap;

    private final CommandManager2 modernized;

    public CommandManager(Locutus locutus) {
        this.locutus = locutus;
        this.prefix1 = '!';
        this.prefix2 = '$';
        this.commandMap = new LinkedHashMap<>();
        this.executor =new ScheduledThreadPoolExecutor(64);

        modernized = new CommandManager2().registerDefaults();
    }

    public ScheduledExecutorService getExecutor() {
        return executor;
    }

    public void register(Command command) {
        for (String alias : command.getAliases()) {
            commandMap.put(alias.toLowerCase(), command);
        }
    }

    public Map<String, Command> getCommandMap() {
        return commandMap;
    }

    public Tag getTag() {
        if (tag == null) tag = new Tag();
        return tag;
    }

    public boolean run(MessageReceivedEvent event) {
        return run(event, true, true);
    }



    public CommandManager2 getV2() {
        return modernized;
    }

    public boolean run(final MessageReceivedEvent event, final boolean async, final boolean noPermMsg) {
        if (Settings.INSTANCE.ENABLED_COMPONENTS.TAG){
            getTag().checkTag(event);
        }

        User msgUser = event.getAuthor();
        if (msgUser.isSystem() || msgUser.isBot()) {
            return false;
        }

        Message message = event.getMessage();
        String content = DiscordUtil.trimContent(message.getContentRaw());
        if (content.length() == 0) {
            return false;
        }

        if (content.equalsIgnoreCase("!threads")) {
            threadDump();
            return false;
        }

        char char0 = content.charAt(0);
        if (char0 != (prefix1) && char0 != prefix2) {
            handleWarRoomSync(event);

            if (content.contains("You successfully gathered intelligence about")) {
                handleIntelOp(event, content);
                return false;
            }
            if (content.contains("of your spies were captured and executed.")) {
                handleSpyOp(event, content);
                return false;
            }

            return false;
        }

        Guild msgGuild = event.isFromGuild() ? event.getGuild() : null;
        // Channel blacklisting / whitelisting
        MessageChannel channel = event.getChannel();
        if (msgGuild != null && channel instanceof TextChannel && !(channel instanceof JoobyChannel)) {
            GuildDB db = Locutus.imp().getGuildDB(msgGuild);
            Set<MessageChannel> blacklist = db.getOrNull(GuildDB.Key.CHANNEL_BLACKLIST);
            Set<MessageChannel> whitelist = db.getOrNull(GuildDB.Key.CHANNEL_WHITELIST);
            if (blacklist != null && blacklist.contains(channel) && !Roles.ADMIN.has(event.getMember())) {
                RateLimitUtil.queue(event.getChannel().sendMessage("Please use the member bot channel (`!KeyStore CHANNEL_BLACKLIST`)"));
                return false;
            }
            if (whitelist != null && !whitelist.isEmpty() && !whitelist.contains(channel) && !Roles.ADMIN.has(event.getMember())) {
                RateLimitUtil.queue(event.getChannel().sendMessage("Please use the member bot channel (`!KeyStore CHANNEL_WHITELIST`)"));
                return false;
            }
        }

        if (char0 == prefix2) {
            modernized.run(event, async);
            return false;
        }

        Runnable task = new Runnable() {
            @Override
            public void run() {
                try {
                    Message message = event.getMessage();
                    String content = DiscordUtil.trimContent(message.getContentRaw());
                    MessageReceivedEvent finalEvent = event;

                    String arg0 = content.indexOf(' ') != -1 ? content.substring(0, content.indexOf(' ')) : content;
                    if (arg0.isEmpty() || arg0.charAt(0) != prefix1) {
                        return;
                    }
                    arg0 = arg0.substring(1);

                    Command cmd = commandMap.get(arg0.toLowerCase());

                    if (!cmd.checkPermission(msgGuild, msgUser)) {
                        if (noPermMsg) {
                            DBNation nation = DiscordUtil.getNation(msgUser);
                            if (nation == null) {
                                RateLimitUtil.queue(event.getChannel().sendMessage("Please use `!verify <nation-id>`"));
                                return;
                            }
                            if (msgGuild != null) {
                                Member member = msgGuild.getMember(msgUser);
                                if (member != null) {
                                    if (sendPermissionMessage(cmd, event)) {
                                        return;
                                    }
                                }
                            }
                            RateLimitUtil.queue(event.getChannel().sendMessage(Messages.NOT_MEMBER));
                        }
                        return;
                    }

                    DBNation nation = DiscordUtil.getNation(event);
                    if (nation != null && !(cmd instanceof RegisterCommand) && !(cmd instanceof Unregister) && !(cmd instanceof MeCommand) && !(cmd instanceof HelpCommand) && !(cmd instanceof Who) && !(cmd instanceof Embassy)) {
                        if (Settings.INSTANCE.MODERATION.BANNED_ALLIANCES.contains(nation.getAlliance_id()) || Settings.INSTANCE.MODERATION.BANNED_NATIONS.contains(nation.getNation_id()) || Settings.INSTANCE.MODERATION.BANNED_USERS.contains(msgUser.getIdLong()))
                            return;
                    }

                    if (!(cmd instanceof Noformat)) {
                        String formatted = DiscordUtil.format(msgGuild, channel, msgUser, nation, content);
                        if (!content.equals(formatted)) {
                            message = DelegateMessage.create(message, formatted, msgGuild, msgUser);
                            finalEvent = new DelegateMessageEvent(msgGuild, event.getResponseNumber(), message);
                        }
                        content = formatted;
                    }

                    List<String> split = StringMan.split(content, ' ');
//            for (int i = 0; i < split.size(); i++) {
//                String word = split.get(i);
////                split.set(i, word.replaceAll("\"", ""));
//            }

                    if (split.isEmpty()) {
                        return;
                    }

                    List<String> args = split.subList(1, split.size());

                    String result;
                    try {
//                    DBNation me = DiscordUtil.getNation(finalEvent);
                        System.out.println("Nation " + finalEvent.getAuthor() + ": " + content);
                        result = cmd.onCommand(finalEvent, args);
                    } catch (Throwable e) { // IllegalArgumentException | UnsupportedOperationException |
                        e.printStackTrace();
                        result = e.getClass().getSimpleName() + ": " + e.getMessage();
//                } catch (Throwable e) {
//                    finalEvent.getChannel().sendMessage(" <@!664156861033086987> (see error (log)"));
//                    e.printStackTrace();
//                    return;
                    }
                    if (result != null && !result.isEmpty()) {
                        for (String key : Locutus.imp().getPnwApi().getApiKeyUsageStats().keySet()) {
                            result = result.replaceAll(key, "");
                        }
                        DiscordUtil.sendMessage(finalEvent.getChannel(), result);
                    }
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        };
        if (async) {
            executor.submit(task);
        } else {
            task.run();
        }
        return false;
    }

    private void handleWarRoomSync(MessageReceivedEvent event) {
        User msgUser = event.getAuthor();
        // War room message sync
        if (!event.isFromGuild()) return;
        Guild msgGuild = event.getGuild();
        MessageChannel channel = event.getChannel();
        if (!(channel instanceof ICategorizableChannel)) return;
        ICategorizableChannel GuildMessageChannel = (ICategorizableChannel) channel;

        Category category = GuildMessageChannel.getParentCategory();
        if (category == null) return;
        if (!category.getName().startsWith("warcat")) return;
        GuildDB db = Locutus.imp().getGuildDB(msgGuild);
        if (db == null) return;
        if (!db.isWhitelisted() && db.getOrNull(GuildDB.Key.ENABLE_WAR_ROOMS) != Boolean.TRUE) return;

        Integer aaId = db.getOrNull(GuildDB.Key.ALLIANCE_ID);
        if (aaId == null || aaId == 0) return;

        WarCategory.WarRoom room = WarCategory.getGlobalWarRoom(channel);
        if (room == null || room.target == null) return;

        Set<WarCategory.WarRoom> rooms = WarCategory.getGlobalWarRooms(room.target);
        if (rooms == null) return;
        ByteBuffer optOut = DiscordMeta.OPT_OUT.get(msgUser.getIdLong());
        if (optOut != null && optOut.get() != 0) return;

        for (WarCategory.WarRoom other : rooms) {
            if (other == room || other.channel == null) continue;

            String userPrefix = msgUser.getName() + "#" + msgUser.getDiscriminator();
            DBNation authorNation = DiscordUtil.getNation(msgUser);
            if (authorNation != null) {
                userPrefix = msgGuild.getName() + "|" + authorNation.getNation() + "/`" + userPrefix + "`";
            }
            String msg = userPrefix + ": " + DiscordUtil.trimContent(event.getMessage().getContentRaw());
            msg = msg.replaceAll("@everyone", "@ everyone");
            msg = msg.replaceAll("@here", "@ here");
            msg = msg.replaceAll("<@&", "<@ &");
            RateLimitUtil.queue(other.channel.sendMessage(msg));
        }
    }

    private boolean sendPermissionMessage(Command cmd, MessageReceivedEvent event) {
        Member member = event.getMember();
        Guild msgGuild = event.isFromGuild() ? event.getGuild() : null;
        {
            Role registeredRole = Roles.REGISTERED.toRole(msgGuild);
            if (registeredRole == null) {
                RateLimitUtil.queue(event.getChannel().sendMessage("No registered role set, please have an admin use `!aliasrole REGISTERED @someRole`"));
                return true;
            } else if (!member.getRoles().contains(registeredRole)) {
                RateLimitUtil.queue(event.getChannel().sendMessage("Please use `!verify` to get masked with the role: " + registeredRole.getName()));
                return true;
            }
        }
        {
            Role memberRole = Roles.MEMBER.toRole(msgGuild);
            if (memberRole == null) {
                RateLimitUtil.queue(event.getChannel().sendMessage("No member role set, please have an admin use `!aliasrole MEMBER @someRole`"));
                return true;
            } else if (!member.getRoles().contains(memberRole)) {
                RateLimitUtil.queue(event.getChannel().sendMessage("You do not have the role: " + memberRole.getName()));
                return true;
            }
        }
        if (cmd.getCategories().contains(CommandCategory.ADMIN)) {
            Role adminRole = Roles.ADMIN.toRole(msgGuild);
            if (adminRole == null) {
                if (!member.hasPermission(net.dv8tion.jda.api.Permission.ADMINISTRATOR)) {
                    RateLimitUtil.queue(event.getChannel().sendMessage("No admin role set, please have an admin use `!aliasrole ADMIN @someRole`"));
                    return true;
                }
            } else if (!member.getRoles().contains(adminRole)) {
                RateLimitUtil.queue(event.getChannel().sendMessage("You do not have the role: " + adminRole.getName()));
                return true;
            }
        }
        if (cmd.getCategories().contains(CommandCategory.GOV)) {
            if (cmd.getCategories().contains(CommandCategory.MILCOM)) {
                Role milcomRole = Roles.MILCOM.toRole(msgGuild);
                if (milcomRole == null) {
                    RateLimitUtil.queue(event.getChannel().sendMessage("No milcom role set, please have an admin use `!aliasrole MILCOM (@someRole`"));
                    return true;
                } else if (!member.getRoles().contains(milcomRole)) {
                    RateLimitUtil.queue(event.getChannel().sendMessage("You do not have the role: " + milcomRole.getName()));
                    return true;
                }
            }

            if (cmd.getCategories().contains(CommandCategory.ECON)) {
                Role role = Roles.ECON.toRole(msgGuild);
                if (role == null) {
                    RateLimitUtil.queue(event.getChannel().sendMessage("No " + Roles.ECON + " role set, please have an admin use `!aliasrole " + Roles.ECON + " @someRole`"));
                    return true;
                } else if (!member.getRoles().contains(role)) {
                    RateLimitUtil.queue(event.getChannel().sendMessage("You do not have the role: " + role.getName()));
                    return true;
                }
            }

            if (cmd.getCategories().contains(CommandCategory.INTERNAL_AFFAIRS)) {
                Role role = Roles.INTERNAL_AFFAIRS.toRole(msgGuild);
                if (role == null) {
                    RateLimitUtil.queue(event.getChannel().sendMessage("No " + Roles.INTERNAL_AFFAIRS + " role set, please have an admin use `!aliasrole " + Roles.INTERNAL_AFFAIRS + " @someRole`"));
                    return true;
                } else if (!member.getRoles().contains(role)) {
                    RateLimitUtil.queue(event.getChannel().sendMessage("You do not have the role: " + role.getName()));
                    return true;
                }
            }

            if (cmd.getCategories().contains(CommandCategory.FOREIGN_AFFAIRS)) {
                Role role = Roles.FOREIGN_AFFAIRS.toRole(msgGuild);
                if (role == null) {
                    RateLimitUtil.queue(event.getChannel().sendMessage("No " + Roles.FOREIGN_AFFAIRS + " role set, please have an admin use `!aliasrole " + Roles.FOREIGN_AFFAIRS + " @someRole`"));
                    return true;
                } else if (!member.getRoles().contains(role)) {
                    RateLimitUtil.queue(event.getChannel().sendMessage("You do not have the role: " + role.getName()));
                    return true;
                }
            }
        }
        return false;
    }

    private void handleSpyOp(MessageReceivedEvent event, String content) {

    }

    private void handleIntelOp(MessageReceivedEvent event, String content) {
        this.executor.submit(new CaughtRunnable() {
            @Override
            public void runUnsafe() {
                try {
                    DBNation attacker = DiscordUtil.getNation(event);
                    Map.Entry<DBNation, double[]> value = SpyCount.parseSpyReport(attacker, content);
                    if (value != null) {
                        if (attacker != null) {
                            attacker.setMeta(NationMeta.INTERVIEW_SPYOP, (byte) 1);
                        }
                        DBNation nation = value.getKey();
                        if (nation != null) {
                            GuildDB db = Locutus.imp().getGuildDB(event.getGuild());
                            if (db.isWhitelisted()) {
                                RateLimitUtil.queue(event.getMessage().addReaction("\u2705"));
                            }
                        }
                    }

                    Map.Entry<DBNation, double[]> entry = PnwUtil.parseIntelRss(content, null);
                    if (entry == null) {
                        System.out.println("Failed to parse `" + content + "`");
                        return;
                    }
                    if (attacker != null) {
                        attacker.setMeta(NationMeta.INTERVIEW_SPYOP, (byte) 1);
                    }

                    DBNation nation = entry.getKey();
                    if (nation != null) {
                        long turn = TimeUtil.getTurn();
                        Locutus.imp().getNationDB().setLoot(nation.getNation_id(), turn, entry.getValue());
                        GuildDB db = Locutus.imp().getGuildDB(event.getGuild());
                        if (db != null && db.isWhitelisted()) {
                            RateLimitUtil.queue(event.getMessage().addReaction("\u2705"));
                            double converted = PnwUtil.convertedTotal(value.getValue());
                            double pct = attacker == null ? 0.10 : attacker.getWarPolicy() == WarPolicy.PIRATE ? 0.14 : 0.1;
                            RateLimitUtil.queue(event.getMessage().getChannel().sendMessage(nation.getNation() + " worth: ~$" + MathMan.format(converted) + ". You would loot $" + MathMan.format(converted * pct)));
                        }
                        return;
                    }
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void threadDump() {
        Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
        for (Thread thread : threadSet) {
            System.err.println(Arrays.toString(thread.getStackTrace()));
        }
        System.err.print("\n\nQueue: " + executor.getQueue().size() + " | Active: " + executor.getActiveCount() + " | task count: " + executor.getTaskCount());
        executor.submit(new Runnable() {
            @Override
            public void run() {
                System.err.println(" - COMMAND EXECUTOR RAN SUCCESSFULLY!!!");
            }
        });
    }

    public void registerCommands(DiscordDB db) {
        // unknown ??
        this.register(new PendingCommand());
        // admin not needed

        // locutus legacy commands
//        this.register(new DebugGetBank());
//        this.register(new DebugLostWarTest());
//        this.register(new DebugGraphTest());
//        this.register(new DebugBeigeTest());
//        this.register(new DebugGetRss());
//        this.register(new DebugBoughtRss());
//        this.register(new DebugFindInactiveOnes());
        //        this.register(new DebugMail());
//        this.register(new FindZeroes());
//        this.register(new DebugScoreRange());
//        this.register(new DebugTyping());
        this.register(new ForumScrape());
        this.register(new LoadUsers());
        this.register(new KickLocutus());

        this.register(new Sudo());
        this.register(new MsgInfo());
        this.register(new Runall());
        this.register(new RunAllNations());

        this.register(new SyncBounties());
        this.register(new SyncWarRooms());
        this.register(new SyncTreaties());
        this.register(new SyncCommand());
        this.register(new SyncAttacks());
        this.register(new SyncWars());
        this.register(new SyncTrade());
        this.register(new SyncUid());
        this.register(new SyncTaxes());
        this.register(new SyncMail());
        this.register(new SyncBanks());

        this.register(new SafekeepCommand());
        this.register(new Permission());
        this.register(new CheckPermission());
        this.register(new Meta());
        this.register(new CheckMail());
        this.register(new AddTaxBracket());

        // unfinished
        this.register(new EmptyAATest());
        this.register(new AllianceBuildupSheet());
        this.register(new Audit());
        this.register(new LoanCommand());
        this.register(new Setup());

        this.register(new AlertTrades());
        this.register(new CheckTradesCommand());
        this.register(new UnsubTrade());
        this.register(new TradeSubscriptions());
        this.register(new BankAlerts());
        this.register(new BankSubscriptions());
        this.register(new UnsubBank());

        this.register(new Commend("commend", true));
        this.register(new Commend("denounce", false));

        /// not useful
        this.register(new GuildInfo());
        this.register(new Kev());
        this.register(new Nev());
        this.register(new SriCommand());
        this.register(new TagCommand(this));
        this.register(new Lury());
        this.register(new TradeId());

        this.register(new WarCitySheet());
        this.register(new StrengthCitySheet());

        ///////// Added
        this.register(new WeeklyInterest()); //
        this.register(new ImportEmoji());
        this.register(new Interview());
        this.register(new DummyCommand());
        this.register(new HasRole());
        this.register(new FindTrader());
        this.register(new RegisterCommand(db));
        this.register(new Unregister());
        this.register(new Embassy());
        this.register(new BorgCommand());
        this.register(new Jokes());
        this.register(new Invite());
        this.register(new CardCommand());
        this.register(new UpdateEmbed());
        this.register(new ChannelCommand());
        this.register(new WarCat());
        this.register(new WarPin());
        this.register(new WarRoom());

        SpyCommand spy;
        this.register(spy = new SpyCommand());
        this.register(new Who(spy));
        this.register(new Treaties());
        this.register(new MeCommand(db, spy));
        this.register(new BeigeTurns());

        this.register(new Multi());
        this.register(new AutoRole());
        this.register(new RoleAlias());
        this.register(new RemoveCoalition());
        this.register(new SetCoalition());
        this.register(new GetCoalitions());
        this.register(new WarInfo());
        this.register(new DepositsSheet());
        this.register(new TaxBracketSheet());
        this.register(new StockpileSheet());
        this.register(new CounterSheet());
        this.register(new Say());
        this.register(new WarSheet());
        this.register(new BlitzSheet());
        this.register(new ValidateBlitzSheet());
        this.register(new MailTargets());
        this.register(new ValidateSpyBlitzSheet());
        this.register(new CombatantSheet());
        this.register(new DeserterSheet());
        this.register(new MMRSheet());
        this.register(new NationSheet());
        this.register(new AllianceSheet());
        this.register(new ActivitySheet());
        this.register(new ProjectSheet());
        this.register(new SpySheet());
        this.register(new IntelOpSheet());
        this.register(new WarCostSheet());
        this.register(new WarCostByAASheet());
        this.register(new WarCostByResourceSheet());
        this.register(new Spyops());
        this.register(new CounterSpy());
        this.register(new Damage());
        this.register(new Inactive());
        this.register(new Counter());
        this.register(new WarCommand());
        this.register(new IntelOp());
        this.register(new Loot());
        this.register(new CityCost());
        this.register(new WarRanking());
        this.register(new InfraCost());
        this.register(new ProjectSlots());
        this.register(new TurnTimer());
        this.register(new Score());
        this.register(new LandCost());
        this.register(new EditAlliance());
        this.register(new SetRank());
        this.register(new SendTreaty());
        this.register(new ModifyTreaty("CancelTreaty", false));
        this.register(new ModifyTreaty("ApproveTreaty", true));
        this.register(new SetBracket());
        this.register(new MailCommand());
        this.register(new Dm());
        this.register(new MoneyTrades());
        this.register(new TradeProfit());
        this.register(new Trending());
        this.register(new Mask());
        this.register(new Borgomas());
        this.register(new TradeRanking());
        this.register(new TradePriceCmd());
        this.register(new TradeMargin());
        this.register(new ConvertedTotal());
        this.register(new GlobalTradeVolume());
        this.register(new GlobalTradeAverage());
        BankWith bankWith = new BankWith();
//        this.register(new NAPViolations());
//        this.register(new NAPDown()); // Outdated
        this.register(new CounterStats());
        this.register(new FindSpyOp());
        this.register(new FindOffshore());
        this.register(new Disperse(bankWith));
        this.register(new ChannelCount());
        this.register(new TransferResources(bankWith));
        this.register(bankWith);
        this.register(new Offshore());
        this.register(new Bank());
        this.register(new Inflows());

        this.register(new Login());
        this.register(new Logout());
        this.register(new AAMembers());
        this.register(new ClearRoles());
        this.register(new ClearNicks());
        this.register(new Revenue());
        this.register(new OptimalBuild());
        this.register(new Warchest());
        this.register(new CopyPasta());
        this.register(new CheckCities());
        this.register(new Reroll());
        this.register(new UnitHistory());
        this.register(new Rebuy());
        this.register(new LeftAA());
        this.register(new FindProducer());
        this.register(new KeyStore());
        //
        this.register(new AddBalance());
        this.register(new Deposits());
        this.register(new GrantCmd(bankWith));
        this.register(new HelpCommand(this));
        WarCostAB warCost = new WarCostAB();
        this.register(warCost);
        this.register(new WarCostRanking());
        this.register(new ROI());
        this.register(new MyLoot(warCost));

        this.register(new IASheet());
        this.register(new WarCostByDay());
        this.register(new WarCostRankingByDay());

        this.register(new MilitaryRanking());
        this.register(new AllianceLootRanking());
        this.register(new NationLootRanking());
        this.register(new WarLossesPerCity());
        this.register(new NetProfitPerWar());
        this.register(new UnitRanking());
        this.register(new TopAABeigeLoot());
        this.register(new AllianceLootLosses());
        this.register(new AllianceAttackTypeRanking());
        this.register(new WarsByTier());
        this.register(new AttackTypeBreakdownAB());

        this.register(new ProlificOffshores());
        this.register(new LargestBanks());

        this.register(new InactiveAlliances());

        this.register(new NoteSheet());
        this.register(new CoalitionSheet());
        this.register(new InterviewSheet());

        this.register(new FASheet());
        this.register(new OptimalTrades());

        //// later
        this.register(new ListMultisByAlliance());

        this.register(new AddBuild());
        this.register(new DeleteBuild());
        this.register(new GetBuild());
        this.register(new AssignBuild());

        this.register(new Simulate());

    }

    public String getPrefix() {
        return prefix1 + "";
    }

    @Deprecated
    public String run(Guild guild, String content, DBNation nation, User user) throws Exception {
        return DiscordUtil.withNation(nation, new Callable<String>() {
            @Override
            public String call() {
                List<String> split = StringMan.split(content, ' ');
                for (int i = 0; i < split.size(); i++) {
                    split.set(i, split.get(i).replaceAll("\"", ""));
                }

                if (split.isEmpty()) {
                    return null;
                }

                String arg0 = split.get(0);
                if (arg0.isEmpty() || arg0.charAt(0) != prefix1) {
                    return null;
                }
                arg0 = arg0.substring(1);

                Command cmd = commandMap.get(arg0.toLowerCase());
                List<String> args = split.subList(1, split.size());

                if (cmd == null) {
                    return null;
                }
                Member member = guild.getMember(user);
                if (!cmd.checkPermission(guild, user)) {
                    return Messages.NOT_MEMBER;
                }

                MessageReceivedEvent dummy = dummyEvent(null, content, member, 0);

                try {
                    return cmd.onCommand(dummy, args);
                } catch (Exception e) {
                    e.printStackTrace();
                    return e.getMessage();
                }
            }
        });
    }

    public static MessageReceivedEvent dummyEvent(Message parent, String cmd, Member member, long response) {
        Message cmdMessage = DelegateMessage.create(parent, cmd, member.getGuild(), member.getUser());
        return new DelegateMessageEvent(member.getGuild(), response, cmdMessage);
    }
}
