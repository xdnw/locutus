package link.locutus.discord.commands.manager;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.external.account.Login;
import link.locutus.discord.commands.external.account.Logout;
import link.locutus.discord.commands.external.guild.CardCommand;
import link.locutus.discord.commands.external.guild.ChannelCommand;
import link.locutus.discord.commands.external.guild.ClearNicks;
import link.locutus.discord.commands.external.guild.ClearRoles;
import link.locutus.discord.commands.external.guild.CopyPasta;
import link.locutus.discord.commands.external.guild.ImportEmoji;
import link.locutus.discord.commands.external.guild.Meta;
import link.locutus.discord.commands.external.guild.SyncBounties;
import link.locutus.discord.commands.external.guild.UpdateEmbed;
import link.locutus.discord.commands.external.guild.WarCat;
import link.locutus.discord.commands.external.guild.WarPin;
import link.locutus.discord.commands.external.guild.WarRoom;
import link.locutus.discord.commands.account.Embassy;
import link.locutus.discord.commands.account.GuildInfo;
import link.locutus.discord.commands.account.HasRole;
import link.locutus.discord.commands.account.question.Interview;
import link.locutus.discord.commands.account.RunAllNations;
import link.locutus.discord.commands.account.Runall;
import link.locutus.discord.commands.alliance.Dm;
import link.locutus.discord.commands.alliance.LeftAA;
import link.locutus.discord.commands.alliance.ModifyTreaty;
import link.locutus.discord.commands.alliance.SendTreaty;
import link.locutus.discord.commands.alliance.SetBracket;
import link.locutus.discord.commands.sync.SyncTreaties;
import link.locutus.discord.commands.alliance.Unregister;
import link.locutus.discord.commands.bank.AddBalance;
import link.locutus.discord.commands.bank.AddTaxBracket;
import link.locutus.discord.commands.bank.TransferResources;
import link.locutus.discord.commands.info.CounterStats;
import link.locutus.discord.commands.bank.FindOffshore;
import link.locutus.discord.commands.external.guild.KickLocutus;
import link.locutus.discord.commands.info.FindSpyOp;
import link.locutus.discord.commands.external.guild.Mask;
import link.locutus.discord.commands.sync.SyncMail;
import link.locutus.discord.commands.war.WarCategory;
import link.locutus.discord.commands.fun.Borgomas;
import link.locutus.discord.commands.fun.Commend;
import link.locutus.discord.commands.fun.Kev;
import link.locutus.discord.commands.fun.Lury;
import link.locutus.discord.commands.fun.Nev;
import link.locutus.discord.commands.fun.SriCommand;
import link.locutus.discord.commands.info.ChannelCount;
import link.locutus.discord.commands.info.CityCost;
import link.locutus.discord.commands.info.DummyCommand;
import link.locutus.discord.commands.info.InfraCost;
import link.locutus.discord.commands.info.Invite;
import link.locutus.discord.commands.info.LandCost;
import link.locutus.discord.commands.info.ProjectSlots;
import link.locutus.discord.commands.info.Rebuy;
import link.locutus.discord.commands.info.Score;
import link.locutus.discord.commands.info.Treaties;
import link.locutus.discord.commands.info.TurnTimer;
import link.locutus.discord.commands.info.UnitHistory;
import link.locutus.discord.commands.info.WeeklyInterest;
import link.locutus.discord.commands.manager.dummy.DelegateMessage;
import link.locutus.discord.commands.manager.dummy.DelegateMessageEvent;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.commands.rankings.AllianceAttackTypeRanking;
import link.locutus.discord.commands.rankings.AllianceLootLosses;
import link.locutus.discord.commands.rankings.AttackTypeBreakdownAB;
import link.locutus.discord.commands.rankings.LargestBanks;
import link.locutus.discord.commands.rankings.MilitaryRanking;
import link.locutus.discord.commands.rankings.MyLoot;
import link.locutus.discord.commands.rankings.WarCostByDay;
import link.locutus.discord.commands.rankings.WarRanking;
import link.locutus.discord.commands.rankings.WarCostRankingByDay;
import link.locutus.discord.commands.rankings.WarsByTier;
import link.locutus.discord.commands.sheets.ActivitySheet;
import link.locutus.discord.commands.sheets.AllianceSheet;
import link.locutus.discord.commands.sheets.CoalitionSheet;
import link.locutus.discord.commands.sheets.CounterSheet;
import link.locutus.discord.commands.sheets.DepositsSheet;
import link.locutus.discord.commands.sheets.DeserterSheet;
import link.locutus.discord.commands.sheets.FASheet;
import link.locutus.discord.commands.sheets.IASheet;
import link.locutus.discord.commands.sheets.IntelOpSheet;
import link.locutus.discord.commands.sheets.InterviewSheet;
import link.locutus.discord.commands.sheets.MMRSheet;
import link.locutus.discord.commands.sheets.MailTargets;
import link.locutus.discord.commands.sheets.NationSheet;
import link.locutus.discord.commands.sheets.NoteSheet;
import link.locutus.discord.commands.sheets.ProjectSheet;
import link.locutus.discord.commands.sheets.ROI;
import link.locutus.discord.commands.bank.SafekeepCommand;
import link.locutus.discord.commands.external.guild.Setup;
import link.locutus.discord.commands.war.WarInfo;
import link.locutus.discord.commands.external.guild.CheckPermission;
import link.locutus.discord.commands.external.guild.KeyStore;
import link.locutus.discord.commands.external.guild.Permission;
import link.locutus.discord.commands.external.account.ForumScrape;
import link.locutus.discord.commands.external.account.LoadUsers;
import link.locutus.discord.commands.account.Say;
import link.locutus.discord.commands.bank.Warchest;
import link.locutus.discord.commands.fun.Jokes;
import link.locutus.discord.commands.info.BeigeTurns;
import link.locutus.discord.commands.info.ListMultisByAlliance;
import link.locutus.discord.commands.info.Multi;
import link.locutus.discord.commands.info.optimal.OptimalBuild;
import link.locutus.discord.commands.rankings.AAMembers;
import link.locutus.discord.commands.rankings.AllianceLootRanking;
import link.locutus.discord.commands.bank.Inflows;
import link.locutus.discord.commands.bank.SyncBanks;
import link.locutus.discord.commands.bank.BankWith;
import link.locutus.discord.commands.info.Reroll;
import link.locutus.discord.commands.account.CheckMail;
import link.locutus.discord.commands.info.PendingCommand;
import link.locutus.discord.commands.info.HelpCommand;
import link.locutus.discord.commands.fun.Tag;
import link.locutus.discord.commands.account.AutoRole;
import link.locutus.discord.commands.account.RegisterCommand;
import link.locutus.discord.commands.account.RoleAlias;
import link.locutus.discord.commands.coalition.GetCoalitions;
import link.locutus.discord.commands.coalition.RemoveCoalition;
import link.locutus.discord.commands.coalition.SetCoalition;
import link.locutus.discord.commands.fun.BorgCommand;
import link.locutus.discord.commands.rankings.InactiveAlliances;
import link.locutus.discord.commands.rankings.NationLootRanking;
import link.locutus.discord.commands.rankings.NetProfitPerWar;
import link.locutus.discord.commands.rankings.UnitRanking;
import link.locutus.discord.commands.rankings.ProlificOffshores;
import link.locutus.discord.commands.rankings.TopAABeigeLoot;
import link.locutus.discord.commands.rankings.WarCostRanking;
import link.locutus.discord.commands.rankings.WarCostAB;
import link.locutus.discord.commands.rankings.WarLossesPerCity;
import link.locutus.discord.commands.sheets.SpySheet;
import link.locutus.discord.commands.sheets.StockpileSheet;
import link.locutus.discord.commands.sheets.TaxBracketSheet;
import link.locutus.discord.commands.sheets.ValidateBlitzSheet;
import link.locutus.discord.commands.sheets.ValidateSpyBlitzSheet;
import link.locutus.discord.commands.sheets.WarCitySheet;
import link.locutus.discord.commands.sheets.WarCostByAASheet;
import link.locutus.discord.commands.sheets.WarCostByResourceSheet;
import link.locutus.discord.commands.sheets.WarCostSheet;
import link.locutus.discord.commands.sheets.CombatantSheet;
import link.locutus.discord.commands.sheets.StrengthCitySheet;
import link.locutus.discord.commands.sheets.WarSheet;
import link.locutus.discord.commands.sync.SyncTaxes;
import link.locutus.discord.commands.sync.SyncTrade;
import link.locutus.discord.commands.sync.SyncUid;
import link.locutus.discord.commands.sync.SyncWarRooms;
import link.locutus.discord.commands.sync.SyncWars;
import link.locutus.discord.commands.trade.FindProducer;
import link.locutus.discord.commands.trade.FindTrader;
import link.locutus.discord.commands.trade.GlobalTradeVolume;
import link.locutus.discord.commands.trade.Inactive;
import link.locutus.discord.commands.trade.TradeId;
import link.locutus.discord.commands.trade.TradeMargin;
import link.locutus.discord.commands.trade.TradePriceCmd;
import link.locutus.discord.commands.trade.GlobalTradeAverage;
import link.locutus.discord.commands.trade.ConvertedTotal;
import link.locutus.discord.commands.trade.TradeRanking;
import link.locutus.discord.commands.trade.Trending;
import link.locutus.discord.commands.trade.sub.AlertTrades;
import link.locutus.discord.commands.trade.sub.CheckTradesCommand;
import link.locutus.discord.commands.trade.sub.TradeSubscriptions;
import link.locutus.discord.commands.trade.sub.UnsubTrade;
import link.locutus.discord.commands.trade.subbank.BankAlerts;
import link.locutus.discord.commands.trade.subbank.BankSubscriptions;
import link.locutus.discord.commands.trade.subbank.UnsubBank;
import link.locutus.discord.commands.war.Counter;
import link.locutus.discord.commands.war.CounterSpy;
import link.locutus.discord.commands.war.Damage;
import link.locutus.discord.commands.war.IntelOp;
import link.locutus.discord.commands.war.Loot;
import link.locutus.discord.commands.external.guild.MsgInfo;
import link.locutus.discord.commands.war.Simulate;
import link.locutus.discord.commands.account.Sudo;
import link.locutus.discord.commands.fun.TagCommand;
import link.locutus.discord.commands.sheets.OptimalTrades;
import link.locutus.discord.commands.buildcmd.AddBuild;
import link.locutus.discord.commands.bank.Bank;
import link.locutus.discord.commands.buildcmd.AssignBuild;
import link.locutus.discord.commands.buildcmd.DeleteBuild;
import link.locutus.discord.commands.buildcmd.GetBuild;
import link.locutus.discord.commands.war.WarCommand;
import link.locutus.discord.commands.info.MeCommand;
import link.locutus.discord.commands.trade.MoneyTrades;
import link.locutus.discord.commands.info.Revenue;
import link.locutus.discord.commands.war.SpyCommand;
import link.locutus.discord.commands.trade.TradeProfit;
import link.locutus.discord.commands.war.Spyops;
import link.locutus.discord.commands.info.Who;
import link.locutus.discord.commands.compliance.CheckCities;
import link.locutus.discord.commands.sync.SyncAttacks;
import link.locutus.discord.commands.sync.SyncCommand;
import link.locutus.discord.commands.bank.Deposits;
import link.locutus.discord.commands.bank.Disperse;
import link.locutus.discord.commands.alliance.EditAlliance;
import link.locutus.discord.commands.alliance.MailCommand;
import link.locutus.discord.commands.bank.Offshore;
import link.locutus.discord.commands.alliance.SetRank;
import link.locutus.discord.commands.sheets.BlitzSheet;
import link.locutus.discord.config.Messages;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.DiscordDB;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DiscordMeta;
import link.locutus.discord.db.entities.NationMeta;
import link.locutus.discord.pnw.DBNation;
import link.locutus.discord.util.*;
import link.locutus.discord.util.scheduler.CaughtRunnable;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.task.balance.loan.LoanCommand;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.web.jooby.adapter.JoobyChannel;
import link.locutus.discord.commands.bank.GrantCmd;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.SpyCount;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
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
        this.prefix1 = Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX.charAt(0);
        this.prefix2 = Settings.INSTANCE.DISCORD.COMMAND.COMMAND_PREFIX.charAt(0);
        this.commandMap = new LinkedHashMap<>();
            this.executor = new ScheduledThreadPoolExecutor(256);

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

        if (content.equalsIgnoreCase(Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "threads")) {
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
            if (db != null) {
                Set<MessageChannel> blacklist = db.getOrNull(GuildDB.Key.CHANNEL_BLACKLIST);
                Set<MessageChannel> whitelist = db.getOrNull(GuildDB.Key.CHANNEL_WHITELIST);
                if (blacklist != null && blacklist.contains(channel) && !Roles.ADMIN.has(event.getMember())) {
                    RateLimitUtil.queue(event.getChannel().sendMessage("Please use the member bot channel (`" + Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "KeyStore CHANNEL_BLACKLIST`)"));
                    return false;
                }
                if (whitelist != null && !whitelist.isEmpty() && !whitelist.contains(channel) && !Roles.ADMIN.has(event.getMember())) {
                    RateLimitUtil.queue(event.getChannel().sendMessage("Please use the member bot channel (`" + Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "KeyStore CHANNEL_WHITELIST`)"));
                    return false;
                }
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
                    if (cmd == null) return;

                    if (!cmd.checkPermission(msgGuild, msgUser)) {
                        if (noPermMsg) {
                            DBNation nation = DiscordUtil.getNation(msgUser);
                            if (nation == null) {
                                RateLimitUtil.queue(event.getChannel().sendMessage("Please use `" + Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "verify <nation-id>`"));
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
                RateLimitUtil.queue(event.getChannel().sendMessage("No registered role set, please have an admin use `" + Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "aliasrole REGISTERED @someRole`"));
                return true;
            } else if (!member.getRoles().contains(registeredRole)) {
                RateLimitUtil.queue(event.getChannel().sendMessage("Please use `" + Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "verify` to get masked with the role: " + registeredRole.getName()));
                return true;
            }
        }
        {
            Role memberRole = Roles.MEMBER.toRole(msgGuild);
            if (memberRole == null) {
                RateLimitUtil.queue(event.getChannel().sendMessage("No member role set, please have an admin use `" + Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "aliasrole MEMBER @someRole`"));
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
                    RateLimitUtil.queue(event.getChannel().sendMessage("No admin role set, please have an admin use `" + Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "aliasrole ADMIN @someRole`"));
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
                    RateLimitUtil.queue(event.getChannel().sendMessage("No milcom role set, please have an admin use `" + Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "aliasrole MILCOM (@someRole`"));
                    return true;
                } else if (!member.getRoles().contains(milcomRole)) {
                    RateLimitUtil.queue(event.getChannel().sendMessage("You do not have the role: " + milcomRole.getName()));
                    return true;
                }
            }

            if (cmd.getCategories().contains(CommandCategory.ECON)) {
                Role role = Roles.ECON.toRole(msgGuild);
                if (role == null) {
                    RateLimitUtil.queue(event.getChannel().sendMessage("No " + Roles.ECON + " role set, please have an admin use `" + Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "aliasrole " + Roles.ECON + " @someRole`"));
                    return true;
                } else if (!member.getRoles().contains(role)) {
                    RateLimitUtil.queue(event.getChannel().sendMessage("You do not have the role: " + role.getName()));
                    return true;
                }
            }

            if (cmd.getCategories().contains(CommandCategory.INTERNAL_AFFAIRS)) {
                Role role = Roles.INTERNAL_AFFAIRS.toRole(msgGuild);
                if (role == null) {
                    RateLimitUtil.queue(event.getChannel().sendMessage("No " + Roles.INTERNAL_AFFAIRS + " role set, please have an admin use `" + Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "aliasrole " + Roles.INTERNAL_AFFAIRS + " @someRole`"));
                    return true;
                } else if (!member.getRoles().contains(role)) {
                    RateLimitUtil.queue(event.getChannel().sendMessage("You do not have the role: " + role.getName()));
                    return true;
                }
            }

            if (cmd.getCategories().contains(CommandCategory.FOREIGN_AFFAIRS)) {
                Role role = Roles.FOREIGN_AFFAIRS.toRole(msgGuild);
                if (role == null) {
                    RateLimitUtil.queue(event.getChannel().sendMessage("No " + Roles.FOREIGN_AFFAIRS + " role set, please have an admin use `" + Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "aliasrole " + Roles.FOREIGN_AFFAIRS + " @someRole`"));
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
        this.register(new AddBalance());
        this.register(new Deposits());
        //
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
//        this.register(new OptimalTrades());

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
