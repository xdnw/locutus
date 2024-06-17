package link.locutus.discord.commands.manager;

import it.unimi.dsi.fastutil.chars.CharOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.apiv3.enums.NationLootType;
import link.locutus.discord.commands.account.*;
import link.locutus.discord.commands.account.question.Interview;
import link.locutus.discord.commands.alliance.*;
import link.locutus.discord.commands.bank.*;
import link.locutus.discord.commands.buildcmd.AddBuild;
import link.locutus.discord.commands.buildcmd.AssignBuild;
import link.locutus.discord.commands.buildcmd.DeleteBuild;
import link.locutus.discord.commands.buildcmd.GetBuild;
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
import link.locutus.discord.commands.account.RunAllNations;
import link.locutus.discord.commands.account.Runall;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.NationPlaceholders;
import link.locutus.discord.commands.sync.SyncTreaties;
import link.locutus.discord.commands.info.CounterStats;
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
import link.locutus.discord.commands.war.WarInfo;
import link.locutus.discord.commands.external.guild.CheckPermission;
import link.locutus.discord.commands.external.guild.KeyStore;
import link.locutus.discord.commands.external.guild.Permission;
import link.locutus.discord.commands.external.account.ForumScrape;
import link.locutus.discord.commands.account.Say;
import link.locutus.discord.commands.fun.Jokes;
import link.locutus.discord.commands.info.BeigeTurns;
import link.locutus.discord.commands.info.Multi;
import link.locutus.discord.commands.info.optimal.OptimalBuild;
import link.locutus.discord.commands.rankings.AllianceLootRanking;
import link.locutus.discord.commands.info.Reroll;
import link.locutus.discord.commands.account.CheckMail;
import link.locutus.discord.commands.info.PendingCommand;
import link.locutus.discord.commands.info.HelpCommand;
import link.locutus.discord.commands.account.AutoRole;
import link.locutus.discord.commands.account.RegisterCommand;
import link.locutus.discord.commands.account.RoleAlias;
import link.locutus.discord.commands.coalition.GetCoalitions;
import link.locutus.discord.commands.coalition.RemoveCoalition;
import link.locutus.discord.commands.coalition.SetCoalition;
import link.locutus.discord.commands.compliance.CheckCities;
import link.locutus.discord.commands.external.guild.*;
import link.locutus.discord.commands.fun.*;
import link.locutus.discord.commands.info.*;
import link.locutus.discord.commands.rankings.*;
import link.locutus.discord.commands.sheets.*;
import link.locutus.discord.commands.sync.*;
import link.locutus.discord.commands.trade.*;
import link.locutus.discord.commands.trade.sub.AlertTrades;
import link.locutus.discord.commands.trade.sub.TradeSubscriptions;
import link.locutus.discord.commands.trade.sub.UnsubTrade;
import link.locutus.discord.commands.trade.subbank.BankAlerts;
import link.locutus.discord.commands.trade.subbank.BankSubscriptions;
import link.locutus.discord.commands.trade.subbank.UnsubBank;
import link.locutus.discord.commands.war.*;
import link.locutus.discord.config.Messages;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.DiscordDB;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DiscordMeta;
import link.locutus.discord.db.entities.NationMeta;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.*;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.scheduler.CaughtRunnable;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.attribute.ICategorizableChannel;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

public class CommandManager {
    private final char prefix1;
    private final ScheduledThreadPoolExecutor executor;
    private final Map<String, Command> commandMap;
    private final CommandManager2 modernized;
    private final CharOpenHashSet modernPrefixes;

    public CommandManager(Locutus locutus) {
        this.prefix1 = Settings.commandPrefix(true).charAt(0);
        this.modernPrefixes = new CharOpenHashSet();
        modernPrefixes.add(Settings.commandPrefix(false).charAt(0));
        for (String prefix : Settings.INSTANCE.DISCORD.COMMAND.ALTERNATE_COMMAND_PREFIX) {
            modernPrefixes.add(prefix.charAt(0));
        }
        this.commandMap = new LinkedHashMap<>();
        this.executor = new ScheduledThreadPoolExecutor(256);

        modernized = new CommandManager2();
    }

    public boolean isModernPrefix(char prefix) {
        return modernPrefixes.contains(prefix);
    }

    public Set<Character> getAllPrefixes() {
        Set<Character> prefixes = new LinkedHashSet<>();
        prefixes.add(prefix1);
        prefixes.addAll(modernPrefixes);
        return prefixes;
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

//    public Tag getTag() {
////        if (tag == null) tag = new Tag();
//        return tag;
//    }

    public CommandManager2 getV2() {
        return modernized;
    }

    public boolean run(Guild guild, IMessageIO channel, final User msgUser, String command, final boolean async, final boolean noPermMsg) {
        if (msgUser.isSystem() || msgUser.isBot()) {
            return false;
        }

        String content = DiscordUtil.trimContent(command.trim());
        if (content.length() == 0) {
            return false;
        }

        boolean returnNotFound = (!(channel instanceof DiscordChannelIO)) || command.contains(Settings.INSTANCE.APPLICATION_ID + "");

        if (content.equalsIgnoreCase(Settings.commandPrefix(true) + "threads")) {
            threadDump();
            return true;
        }

        boolean jsonCommand = (content.startsWith("{") && content.endsWith("}"));
        char char0 = content.charAt(0);
        if (char0 != (prefix1) && !jsonCommand && !isModernPrefix(char0)) {
            handleWarRoomSync(guild, msgUser, channel, content);

            if (content.contains("You successfully gathered intelligence about")) {
                handleIntelOp(guild, msgUser, channel, content);
                return true;
            }
            if (content.contains("of your spies were captured and executed.")) {
                handleSpyOp(guild, msgUser, channel, content);
                return true;
            }

            return false;
        }

        // prioritize updating nations using commands
        DBNation nation = DiscordUtil.getNation(msgUser);
        if (nation != null) {
            Locutus.imp().getNationDB().markNationDirty(nation.getNation_id());
        }

        // Channel blacklisting / whitelisting
        if (isModernPrefix(char0) || jsonCommand) {
            try {
                modernized.run(guild, channel, msgUser, content, async, returnNotFound);
            } catch (Throwable e) {
                e.printStackTrace();
            }
            return true;
        }

        Runnable task = () -> {
            try {
                String content1 = content;

                String arg0 = content1.indexOf(' ') != -1 ? content1.substring(0, content1.indexOf(' ')) : content1;
                if (arg0.isEmpty() || arg0.charAt(0) != prefix1) {
                    return;
                }
                arg0 = arg0.substring(1);

                Command cmd = commandMap.get(arg0.toLowerCase());
                if (cmd == null) {
                    if (returnNotFound) {
                        List<String> validIds = new ArrayList<>(commandMap.keySet());
                        List<String> closest = StringMan.getClosest(arg0, validIds, false);
                        if (closest.size() > 5) closest = closest.subList(0, 5);
                        channel.send("No command found for `" + StringMan.getString(arg0) + "`\n" +
                                "Did you mean:\n- " + StringMan.join(closest, "\n- ") +
                                "\n\nSee also: " + CM.help.find_command.cmd.toSlashMention());
                    }
                    return;
                }

                if (!cmd.checkPermission(guild, msgUser)) {
                    if (noPermMsg) {
                        if (nation == null) {
                            channel.sendMessage("Please use " + CM.register.cmd.toSlashMention() + "");
                            return;
                        }
                        if (guild != null) {
                            Member member = guild.getMember(msgUser);
                            if (member != null) {
                                if (sendPermissionMessage(cmd, guild, msgUser, channel)) {
                                    return;
                                }
                            }
                        }
                        channel.sendMessage(Messages.NOT_MEMBER);
                    }
                    return;
                }

                if (nation != null && !(cmd instanceof RegisterCommand) && !(cmd instanceof Unregister) && !(cmd instanceof MeCommand) && !(cmd instanceof HelpCommand) && !(cmd instanceof Who) && !(cmd instanceof Embassy)) {
                    String allianceDenyReason = Settings.INSTANCE.MODERATION.BANNED_ALLIANCES.get(nation.getAlliance_id());
                    if (allianceDenyReason != null) {
                        channel.sendMessage("Access-Denied: " + allianceDenyReason);
                        return;
                    }
                    String nationDenyReason = Settings.INSTANCE.MODERATION.BANNED_NATIONS.get(nation.getNation_id());
                    if (nationDenyReason != null) {
                        channel.sendMessage("Access-Denied: " + nationDenyReason);
                        return;
                    }
                }
                String userDenyReason = Settings.INSTANCE.MODERATION.BANNED_USERS.get(msgUser.getIdLong());
                if (userDenyReason != null) {
                    channel.sendMessage("Access-Denied: " + userDenyReason);
                    return;
                }
                if (guild != null) {
                    String guildDenyReason = Settings.INSTANCE.MODERATION.BANNED_GUILDS.get(guild.getIdLong());
                    if (guildDenyReason != null) {
                        channel.sendMessage("Access-Denied: " + guildDenyReason);
                        return;
                    }
                    long ownerId = guild.getOwnerIdLong();
                    if (msgUser == null || msgUser.getIdLong() != ownerId) {
                        userDenyReason = Settings.INSTANCE.MODERATION.BANNED_USERS.get(ownerId);
                        if (userDenyReason != null) {
                            String userName = DiscordUtil.getUserName(ownerId);
                            if (!userName.startsWith("<@")) userName += "/" + ownerId;
                            throw new IllegalArgumentException("Access-Denied[User=" + userName + "]: " + userDenyReason);
                        }
                        DBNation ownerNation = DiscordUtil.getNation(ownerId);
                        if (ownerNation != null) {
                            String nationDenyReason = Settings.INSTANCE.MODERATION.BANNED_NATIONS.get(ownerNation.getId());
                            if (nationDenyReason != null) {
                                throw new IllegalArgumentException("Access-Denied[Nation=" + ownerNation.getId() + "]: " + nationDenyReason);
                            }
                        }
                    }
                }
                System.out.println("Content 1: " + content1);

                if (!(cmd instanceof Noformat) && nation != null && content1.indexOf('{') != -1 && content1.indexOf('}') != -1) {
                    try {
                        NationPlaceholders formatter = Locutus.imp().getCommandManager().getV2().getNationPlaceholders();
                        String formatted = formatter.format2(guild, nation, msgUser, content1, nation, false);
                        if (!content1.equals(formatted)) {
                            assert guild != null;
                        }
                        content1 = formatted;
                        System.out.println("FOrmatted to: " + content1);
                    } catch (IllegalArgumentException ignore) {}
                }

                List<String> split = StringMan.split(content1, ' ');
                if (split.isEmpty()) {
                    return;
                }

                List<String> args = split.subList(1, split.size());

                String result;
                try {
                    result = cmd.onCommand(guild, channel, msgUser, nation, content1, args);
                } catch (Throwable e) { // IllegalArgumentException | UnsupportedOperationException |
                    e.printStackTrace();
                    result = e.getClass().getSimpleName() + ": " + e.getMessage();
//                } catch (Throwable e) {
//                    finalEvent.getChannel().sendMessage(" <@!664156861033086987> (see error (log)"));
//                    e.printStackTrace();
//                    return;
                }
                if (result != null && !result.isEmpty()) {
                    result = result.replaceAll("(?i)" + Settings.INSTANCE.API_KEY_PRIMARY, "XXX");
//                    result = result.replaceAll("(?i)(?<=^|[^A-Fa-f0-9])(?:[0-9a-f]{2}){7,}(?=[^A-Fa-f0-9]|$)", "XXX");
                    channel.send(result);
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
        };
        if (async) {
            executor.submit(task);
        } else {
            task.run();
        }
        return false;
    }

    private void handleWarRoomSync(Guild guild, User msgUser, IMessageIO io, String command) {
        if (guild == null) return;
        GuildChannel channel = guild.getGuildChannelById(io.getIdLong());
        if (!(channel instanceof ICategorizableChannel GuildMessageChannel)) return;
        if (!(channel instanceof MessageChannel)) return;

        Category category = GuildMessageChannel.getParentCategory();
        if (category == null) return;
        if (!category.getName().startsWith("warcat")) return;
        GuildDB db = Locutus.imp().getGuildDB(guild);
        if (db == null) return;
        if (!db.isWhitelisted() && db.getOrNull(GuildKey.ENABLE_WAR_ROOMS) != Boolean.TRUE) return;

        if (!db.hasAlliance()) return;

        WarCategory.WarRoom room = WarCategory.getGlobalWarRoom((MessageChannel) channel);
        if (room == null || room.target == null) return;

        Set<WarCategory.WarRoom> rooms = WarCategory.getGlobalWarRooms(room.target);
        if (rooms == null) return;
        ByteBuffer optOut = DiscordMeta.OPT_OUT.get(msgUser.getIdLong());
        if (optOut != null && optOut.get() != 0) return;

        for (WarCategory.WarRoom other : rooms) {
            if (other == room || other.channel == null) continue;

            String userPrefix = DiscordUtil.getFullUsername(msgUser);
            DBNation authorNation = DiscordUtil.getNation(msgUser);
            if (authorNation != null) {
                userPrefix = guild.getName() + "|" + authorNation.getNation() + "/`" + userPrefix + "`";
            }
            String msg = userPrefix + ": " + DiscordUtil.trimContent(command);
            msg = msg.replaceAll("@everyone", "@ everyone");
            msg = msg.replaceAll("@here", "@ here");
            msg = msg.replaceAll("<@&", "<@ &");
            RateLimitUtil.queueWhenFree(other.channel.sendMessage(msg));
        }
    }

    private boolean sendPermissionMessage(Command cmd, Guild msgGuild, User msgUser, IMessageIO channel) {
        Member member = msgGuild != null && msgUser != null ? msgGuild.getMember(msgUser) : null;
        {
            Role registeredRole = Roles.REGISTERED.toRole(msgGuild);
            if (registeredRole == null) {
                channel.sendMessage("No registered role set, please have an admin use " + CM.role.setAlias.cmd.locutusRole(Roles.REGISTERED.name()).discordRole("").toSlashCommand() + "");
                return true;
            } else {
                assert member != null;
                if (!member.getRoles().contains(registeredRole)) {
                    channel.sendMessage("Please use " + CM.register.cmd.toSlashMention() + " to get masked with the role: `" + registeredRole.getName() + "`");
                    return true;
                }
            }
        }
        {
            Role memberRole = Roles.MEMBER.toRole(msgGuild);
            if (memberRole == null) {
                channel.sendMessage("No member role set, please have an admin use `" + Settings.commandPrefix(true) + "aliasrole MEMBER @someRole`");
                return true;
            } else if (!member.getRoles().contains(memberRole)) {
                channel.sendMessage("You do not have the role: " + memberRole.getName());
                return true;
            }
        }
        if (cmd.getCategories().contains(CommandCategory.ADMIN)) {
            Role adminRole = Roles.ADMIN.toRole(msgGuild);
            if (adminRole == null) {
                if (!member.hasPermission(net.dv8tion.jda.api.Permission.ADMINISTRATOR)) {
                    channel.sendMessage("No admin role set, please have an admin use `" + Settings.commandPrefix(true) + "aliasrole ADMIN @someRole`");
                    return true;
                }
            } else if (!member.getRoles().contains(adminRole)) {
                channel.sendMessage("You do not have the role: " + adminRole.getName());
                return true;
            }
        }
        if (cmd.getCategories().contains(CommandCategory.GOV)) {
            if (cmd.getCategories().contains(CommandCategory.MILCOM)) {
                Role milcomRole = Roles.MILCOM.toRole(msgGuild);
                if (milcomRole == null) {
                    channel.sendMessage("No milcom role set, please have an admin use `" + Settings.commandPrefix(true) + "aliasrole MILCOM (@someRole`");
                    return true;
                } else if (!member.getRoles().contains(milcomRole)) {
                    channel.sendMessage("You do not have the role: " + milcomRole.getName());
                    return true;
                }
            }

            if (cmd.getCategories().contains(CommandCategory.ECON)) {
                Role role = Roles.ECON.toRole(msgGuild);
                if (role == null) {
                    channel.sendMessage("No " + Roles.ECON + " role set, please have an admin use `" + Settings.commandPrefix(true) + "aliasrole " + Roles.ECON + " @someRole`");
                    return true;
                } else if (!member.getRoles().contains(role)) {
                    channel.sendMessage("You do not have the role: " + role.getName());
                    return true;
                }
            }

            if (cmd.getCategories().contains(CommandCategory.INTERNAL_AFFAIRS)) {
                Role role = Roles.INTERNAL_AFFAIRS.toRole(msgGuild);
                if (role == null) {
                    channel.sendMessage("No " + Roles.INTERNAL_AFFAIRS + " role set, please have an admin use `" + Settings.commandPrefix(true) + "aliasrole " + Roles.INTERNAL_AFFAIRS + " @someRole`");
                    return true;
                } else if (!member.getRoles().contains(role)) {
                    channel.sendMessage("You do not have the role: " + role.getName());
                    return true;
                }
            }

            if (cmd.getCategories().contains(CommandCategory.FOREIGN_AFFAIRS)) {
                Role role = Roles.FOREIGN_AFFAIRS.toRole(msgGuild);
                if (role == null) {
                    channel.sendMessage("No " + Roles.FOREIGN_AFFAIRS + " role set, please have an admin use `" + Settings.commandPrefix(true) + "aliasrole " + Roles.FOREIGN_AFFAIRS + " @someRole`");
                    return true;
                } else if (!member.getRoles().contains(role)) {
                    channel.sendMessage("You do not have the role: " + role.getName());
                    return true;
                }
            }
        }
        return false;
    }

    private void handleSpyOp(Guild guild, User user, IMessageIO channel, String content) {

    }

    private void handleIntelOp(Guild guild, User user, IMessageIO channel, String content) {
        this.executor.submit(new CaughtRunnable() {
            @Override
            public void runUnsafe() {
                try {
                    DBNation attacker = DiscordUtil.getNation(user);
                    Map.Entry<DBNation, double[]> value = SpyCount.parseSpyReport(attacker, content);
                    if (value != null) {
                        if (attacker != null) {
                            attacker.setMeta(NationMeta.INTERVIEW_SPYOP, (byte) 1);
                        }
                        DBNation nation = value.getKey();
                        if (nation != null) {
                            GuildDB db = Locutus.imp().getGuildDB(guild);
                        }
                    }

                    Map.Entry<DBNation, double[]> entry = ResourceType.parseIntelRss(content, null);
                    if (entry == null) {
                        System.out.println("Failed to parse `" + content + "`");
                        return;
                    }
                    if (attacker != null) {
                        attacker.setMeta(NationMeta.INTERVIEW_SPYOP, (byte) 1);
                    }

                    DBNation nation = entry.getKey();
                    if (nation != null) {
                        long now = System.currentTimeMillis();
                        Locutus.imp().getNationDB().saveLoot(nation.getNation_id(), now, entry.getValue(), NationLootType.ESPIONAGE);
                        GuildDB db = Locutus.imp().getGuildDB(guild);
                        if (db != null) {
                            assert value != null;
                            double converted = ResourceType.convertedTotal(value.getValue());
                            double pct = 0.1 * (1 + (attacker.looterModifier(false) - 1) + (nation.lootModifier() - 1));
                            channel.sendMessage(nation.getNation() + " worth: ~$" + MathMan.format(converted) + ". You would loot $" + MathMan.format(converted * pct));
                        }
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
        executor.submit(() -> System.err.println("- COMMAND EXECUTOR RAN SUCCESSFULLY!!!"));
    }

    public void registerCommands(DiscordDB db) {
        if (modernized != null) {
            modernized.registerDefaults();
        }
        this.register(new RaidCommand());
        this.register(new PendingCommand());
        this.register(new ForumScrape());
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

        /// not useful
        this.register(new GuildInfo());
        this.register(new Kev());
        this.register(new Nev());
        this.register(new SriCommand());
//        this.register(new TagCommand(this));
        this.register(new Lury());
        this.register(new TradeId());

        this.register(new WarCitySheet());
        this.register(new StrengthCitySheet());

        ///////// Added
        this.register(new WeeklyInterest()); //
        this.register(new ImportEmoji());
        this.register(new Interview());
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
        TransferCommand bankWith = new TransferCommand();
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
        WarCostAB warCost = new WarCostAB();
        this.register(warCost);
        this.register(new MyLoot(warCost));

        this.register(new WarCostRanking());
        this.register(new AddBuild());
        this.register(new AssignBuild());
        this.register(new DeleteBuild());
        this.register(new GetBuild());
        //
        this.register(new HelpCommand(this));
        this.register(new GrantCmd(bankWith));

        this.register(new ProlificOffshores());
        this.register(new LargestBanks());
        this.register(new InactiveAlliances());

        this.register(new WarCostByDay());
        this.register(new WarCostRankingByDay());

        this.register(new IASheet());
        this.register(new NoteSheet());
        this.register(new CoalitionSheet());
        this.register(new InterviewSheet());
        this.register(new FASheet());

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

        this.register(new ROI());

        this.register(new Simulate());

        // unfinished
        this.register(new AlertTrades());
        this.register(new Commend("commend", true));
        this.register(new Commend("denounce", false));
//        this.register(new Setup());
        this.register(new UnsubTrade());
        this.register(new TradeSubscriptions());
        this.register(new BankAlerts());
        this.register(new BankSubscriptions());
        this.register(new UnsubBank());
    }

    public String getPrefix() {
        return prefix1 + "";
    }

    @Deprecated
    public String run(Guild guild, IMessageIO io, String content, DBNation nation, User user) throws Exception {
        return DiscordUtil.withNation(nation, () -> {
            List<String> split = StringMan.split(content, ' ');
            split.replaceAll(s -> s.replaceAll("\"", ""));

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

            assert member != null;

            try {
                return cmd.onCommand(guild, io, user, nation, content, args);
            } catch (Exception e) {
                e.printStackTrace();
                return e.getMessage();
            }
        });
    }
}
