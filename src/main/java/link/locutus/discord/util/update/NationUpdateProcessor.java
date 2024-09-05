package link.locutus.discord.util.update;

import com.google.common.eventbus.Subscribe;
import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv3.enums.AlliancePermission;
import link.locutus.discord.commands.manager.v2.command.CommandBehavior;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.NationFilter;
import link.locutus.discord.commands.manager.v2.table.TimeFormat;
import link.locutus.discord.commands.manager.v2.table.TimeNumericTable;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.*;
import link.locutus.discord.db.entities.metric.AllianceMetric;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.event.nation.*;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.pnw.PNWUser;
import link.locutus.discord.util.AlertUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.scheduler.CaughtRunnable;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.battle.BlitzGenerator;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.task.roles.AutoRoleInfo;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import org.apache.commons.collections4.map.PassiveExpiringMap;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static link.locutus.discord.db.guild.GuildKey.BAN_ALERT_CHANNEL;

public class NationUpdateProcessor {
    private static Map<Integer, Integer> ACTIVITY_ALERTS = new PassiveExpiringMap<Integer, Integer>(240, TimeUnit.MINUTES);

    public static void onActivityCheck() {
        Map<Integer, Integer> membersByAA = new Int2IntOpenHashMap(); // only <7d non vm nations
        Map<Integer, Integer> activeMembersByAA = new Int2IntOpenHashMap();
        Map<Integer, Double> averageMilitarization = new Int2DoubleOpenHashMap();

        Set<Integer> online = new IntOpenHashSet();
        Set<Long> checkedUser = new LongOpenHashSet();
        for (Guild guild : Locutus.imp().getDiscordApi().getGuilds()) {
            for (Member member : guild.getMemberCache()) {
                long userId = member.getIdLong();
                if (checkedUser.contains(userId)) continue;
                checkedUser.add(userId);
                DBNation nation = DiscordUtil.getNation(member.getUser());
                if (nation == null) continue;
                if (member.getOnlineStatus() == OnlineStatus.ONLINE) {
                    online.add(nation.getId());
                }
            }
        }

        long inactive7d = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(7200);
        long inactive1d = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(1440);
        long turnNow = TimeUtil.getTurn();

        for (Map.Entry<Integer, DBNation> entry : Locutus.imp().getNationDB().getNations().entrySet()) {
            DBNation nation = entry.getValue();
            if (nation.lastActiveMs() < inactive7d || nation.getPosition() <= Rank.APPLICANT.id || nation.getLeaving_vm() > turnNow) continue;
            int aaId = nation.getAlliance_id();
            membersByAA.put(aaId, membersByAA.getOrDefault(aaId, 0) + 1);
            boolean active = nation.active_m() < 30;
            if (!active && nation.lastActiveMs() > inactive1d && nation.getLeaving_vm() <= turnNow && nation.getPositionEnum().id > Rank.APPLICANT.id) {
                active = online.contains(nation.getId());
            }
            if (active) {
                activeMembersByAA.put(aaId, activeMembersByAA.getOrDefault(aaId, 0) + 1);
                double value = 0;
                DBNation previous = DBNation.getById(nation.getNation_id());
                if (previous != null) {
                    value = (previous.getAircraftPct() + previous.getTankPct() + previous.getSoldierPct()) / 3d;
                }
                averageMilitarization.put(aaId, averageMilitarization.getOrDefault(aaId, 0d) + value);
            }
        }
        Locutus.imp().getExecutor().submit(new Runnable() {
            @Override
            public void run() {
                for (Map.Entry<Integer, Integer> entry : membersByAA.entrySet()) {
                    int aaId = entry.getKey();
                    int members = entry.getValue();
                    int active = activeMembersByAA.getOrDefault(aaId, 0);
                    if (members < 10 || active < 4) continue;
                    double avgMMR = averageMilitarization.getOrDefault(aaId, 0d) / (double) active;

                    int required = (int) Math.floor(Math.pow(members, 0.7) * 1.5);
                    if (active < required) {
                        continue;
                    }
                    if (avgMMR < 0.5 && members < required * 2) {
                        continue;
                    }

                    Integer previous = ACTIVITY_ALERTS.get(aaId);
                    if (previous != null && previous + 10 >= active) continue;
                    ACTIVITY_ALERTS.put(aaId, active);

                    DBAlliance alliance = Locutus.imp().getNationDB().getOrCreateAlliance(aaId);
                    String title = alliance.getName() + " is " + MathMan.format(100d * active / members) + "% online";
                    StringBuilder body = new StringBuilder();
                    body.append(alliance.getMarkdownUrl()).append("\n");
                    body.append("Members: " + members).append("\n");
                    body.append("Online: " + active).append("\n");
                    body.append("Avg Military: " + MathMan.format(100 * avgMMR) + "%").append("\n");

                    AlertUtil.forEachChannel(f -> true, GuildKey.ACTIVITY_ALERTS, new BiConsumer<MessageChannel, GuildDB>() {
                        @Override
                        public void accept(MessageChannel channel, GuildDB guildDB) {
                            DiscordUtil.createEmbedCommand(channel, title, body.toString());
                        }
                    });


                }
            }
        });
    }

    @Subscribe
    public void onNationChangeActive(NationChangeActiveEvent event) {
        DBNation previous = event.getPrevious();
        DBNation nation = event.getCurrent();

        {
            long activeTurn = TimeUtil.getTurn(nation.lastActiveMs());
            Locutus.imp().getNationDB().setActivity(nation.getNation_id(), activeTurn);
        }

        Map<Long, Long> notifyMap = nation.getLoginNotifyMap();
        if (notifyMap != null) {
            nation.deleteMeta(NationMeta.LOGIN_NOTIFY);
            if (!notifyMap.isEmpty()) {
                String message = ("This is your login alert for:\n" + nation.toEmbedString());

                for (Map.Entry<Long, Long> entry : notifyMap.entrySet()) {
                    Long userId = entry.getKey();
                    User user = Locutus.imp().getDiscordApi().getUserById(userId);
                    DBNation attacker = DiscordUtil.getNation(userId);
                    if (user == null || attacker == null) continue;


                    boolean hasActiveWar = false;
                    for (DBWar war : nation.getActiveWars()) {
                        if (war.getAttacker_id() == attacker.getNation_id() || war.getDefender_id() == attacker.getNation_id()) {
                            hasActiveWar = true;
                            break;
                        }
                    }
                    String messageCustom = message;
                    if (hasActiveWar) {
                        messageCustom += "\n**You have an active war with this nation.**";
                    } else {
                        messageCustom += "\n**You do NOT have an active war with this nation.**";
                    }

                    try {
                        DiscordChannelIO channel = new DiscordChannelIO(RateLimitUtil.complete(user.openPrivateChannel()), null);
                        channel.send(messageCustom);
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        if (nation.active_m() < 3) {
            long activeMinute = nation.lastActiveMs();
            // round to nearest minute
            activeMinute = (activeMinute / 60_000) * 60_000;
//            Locutus.imp().getNationDB().setSpyActivity(nation.getNation_id(), nation.getProjectBitMask(), nation.getSpies(), activeMinute, nation.getWarPolicy());

            if (previous.active_m() > 360 && Settings.INSTANCE.TASKS.AUTO_FETCH_UID) {
                Locutus.imp().getExecutor().submit(new CaughtRunnable() {
                    @Override
                    public void runUnsafe() throws Exception {
                        nation.fetchUid(false);
                    }
                });
            }
        }
    }

    @Subscribe
    public void onNationCreate(NationCreateEvent event) {
        DBNation current = event.getCurrent();

        // Reroll alerts (run on another thread since fetching UID takes time)
        Locutus.imp().getExecutor().submit(() -> {
            int rerollId = current.isReroll(true);
            if (rerollId > 0) {
                String title = "Detected reroll: " + current.getNation();
                StringBuilder body = new StringBuilder(current.getNationUrlMarkup(true));
                if (rerollId != current.getNation_id()) {
                    body.append("\nReroll of: " + PW.getNationUrl(rerollId));
                }

                AlertUtil.forEachChannel(f -> true, GuildKey.REROLL_ALERT_CHANNEL, new BiConsumer<MessageChannel, GuildDB>() {
                    @Override
                    public void accept(MessageChannel channel, GuildDB guildDB) {
                        DiscordUtil.createEmbedCommand(channel, title, body.toString());
                    }
                });
            }
        });
    }

    @Subscribe
    public void onNationPositionChange(NationChangeRankEvent event) {
        DBNation previous = event.getPrevious();
        DBNation current = event.getCurrent();

        checkOfficerChange(previous, current);
    }

    private void checkOfficerChange(DBNation previous, DBNation current) {
        if (current == null || previous == null || previous.getAlliance_id() == current.getAlliance_id() || current.active_m() > 4880) return;
        if (previous.getPosition() < Rank.OFFICER.id) {
            DBAlliancePosition position = previous.getAlliancePosition();
            if (position == null || !position.hasAnyAdminPermission()) return;
        }
        DBAlliance alliance = previous.getAlliance(false);

        if (alliance != null && alliance.getRank() < 50)
        {
            String title = current.getNation() + " (" + Rank.byId(previous.getPosition()) + ") leaves " + previous.getAllianceName();
            String body = current.toEmbedString();
            AlertUtil.forEachChannel(f -> true, GuildKey.ORBIS_OFFICER_LEAVE_ALERTS, new BiConsumer<MessageChannel, GuildDB>() {
                @Override
                public void accept(MessageChannel channel, GuildDB guildDB) {
                    DiscordUtil.createEmbedCommand(channel, title, body);
                }
            });
        }
    }

    private Map<Integer, DBNation> deletedNationCache = new ConcurrentHashMap<>();

    @Subscribe
    public void onNationDelete(NationDeleteEvent event) {
        DBNation previous = event.getPrevious();
        DBNation current = event.getCurrent();

        if (previous != null && current == null) {
            DBNation copy = new DBNation(previous);
            deletedNationCache.put(previous.getNation_id(), copy);
        }

        handleOfficerDelete(previous, current);
        processDeletion(previous, current);
    }

    @Subscribe
    public void onNationBan(NationBanEvent event) {
        DBBan ban = event.getBan();
        DBNation nation = DBNation.getById(ban.nation_id);
        if (nation == null) {
            nation = deletedNationCache.remove(ban.nation_id);
        }

        List<String> adminInfo = new ArrayList<>();

        StringBuilder body = new StringBuilder();
        if (nation != null) {
            body.append(nation.toEmbedString());

            if (nation.getAlliance_id() != 0 && nation.getPosition() > Rank.APPLICANT.id) {
                GuildDB db = Locutus.imp().getGuildDBByAA(nation.getAlliance_id());
                if (db != null && db.getOffshore() == Locutus.imp().getRootBank()) {
                    body.append("\n" + nation.getAllianceUrlMarkup(true) + " " + nation.getPositionEnum());
                    Locutus.imp().getRootDb().addCoalition(nation.getAlliance_id(), Coalition.FROZEN_FUNDS);
                    adminInfo.add(nation.getAllianceUrlMarkup(true));
                    AlertUtil.error(nation.getAlliance_id() + " frozen", nation.getAllianceUrlMarkup(true) + " " + nation.getPositionEnum() + " " + nation.getNationUrlMarkup(true) + " " + nation.getNation(), true);
                }
            }
        } else {
            body.append("Nation ID: " + ban.nation_id);
        }
        body.append("\n" + ban.reason + "\nDays Left: `" + ban.days_left + "`\n");

        String title = "Nation Banned: " + PW.getName(ban.nation_id, false) + "/" + ban.nation_id;

        if (ban.discord_id != 0) {
            User user = DiscordUtil.getUser(ban.discord_id);
            if (user != null) {
                for (Guild guild : user.getMutualGuilds()) {
                    Member member = guild.getMember(user);
                    if (member != null && member.hasPermission(Permission.ADMINISTRATOR)) {
                        GuildDB db = Locutus.imp().getGuildDB(guild);
                        if (db != null && db.getOffshore() == Locutus.imp().getRootBank()) {
                            Locutus.imp().getRootDb().addCoalition(guild.getIdLong(), Coalition.FROZEN_FUNDS);
                            body.append("\nguild: " + guild);
                            adminInfo.add(guild.toString());
                            String alertMsg = guild.toString() + " <@" + ban.discord_id + "> " + ban.reason;
                            if (nation != null) {
                                alertMsg += "\n" + nation.getNationUrlMarkup(true) + " " + nation.getNation();
                            }
                            AlertUtil.error(guild.getIdLong() + " frozen", alertMsg, true);
                        }
                    }
                }
            }
        }

        if (!adminInfo.isEmpty()) {
            MessageChannel channel = Locutus.imp().getRootDb().getResourceChannel(0);
            if (channel != null) {
                String newBody = body + "\n" +
                        "See " + CM.offshore.unlockTransfers.cmd.toSlashMention() + "\n" +
                        "See `!coalitions FROZEN_FUNDS`";
                new DiscordChannelIO(channel).create().embed(title, newBody).append("<@217897994375266304> <@664156861033086987>").send();
            }
        }

        AlertUtil.forEachChannel(f -> true, BAN_ALERT_CHANNEL, (channel, guildDB) -> DiscordUtil.createEmbedCommand(channel, title, body.toString()));
    }

    @Subscribe
    public void onNationLeaveBeige(NationLeaveBeigeEvent event) {
        DBNation nation = event.getCurrent();
        if (nation.getVm_turns() == 0) {
            raidAlert(nation);
            enemyAlert(event.getPrevious(), nation);
        }
    }

    @Subscribe
    public void onVM(NationChangeVacationEvent event) {
        DBNation previous = event.getPrevious();
        DBNation current = event.getCurrent();
        if (previous.getVm_turns() == 0 && current.getVm_turns() > 0) {
            processVMTransfers(previous, current);
        }
    }

    @Subscribe
    public void onNationLeaveVacation(NationLeaveVacationEvent event) {
        DBNation nation = event.getCurrent();
        if (nation.getVm_turns() == 0 && !nation.isBeige()) {
            raidAlert(nation);
            enemyAlert(event.getPrevious(), nation);
        }
    }

    private final PassiveExpiringMap<Long, Boolean> pingFlag = new PassiveExpiringMap<>(60, TimeUnit.MINUTES);

    private boolean enemyAlert(DBNation previous, DBNation current) {
        if (current.active_m() > 7200) return false;

        boolean leftVMBeige = false;
        String title;
        if (previous.isBeige() && !current.isBeige() && current.active_m() < 10000) {
            title = "Left Beige: " + current.getNation() + " | " + current.getAllianceName();
            leftVMBeige = true;
        } else if (previous.getVm_turns() > 0 && current.getVm_turns() == 0) {
            title = "Left VM: " + current.getNation() + " | " + current.getAllianceName();
            leftVMBeige = true;
        } else if (previous.active_m() <= 10080 && current.active_m() > 10080) {
            title = "Inactive: " + current.getNation() + " | " + current.getAllianceName();
            leftVMBeige = true;
        } else if (previous.getAlliance_id() != 0 && current.getAlliance_id() == 0 && current.active_m() > 1440) {
            title = "Removed: " + current.getNation() + " | " + current.getAllianceName();
            leftVMBeige = true;
        } else if (previous.getDef() >= 3 && current.getDef() < 3 && !current.isBeige()) {
            title = "Unslotted: " + current.getNation() + " | " + current.getAllianceName();
//                    leftVMBeige = true;
        } else {
            return false;
        }

        double minScore = current.getScore() / PW.WAR_RANGE_MAX_MODIFIER;
        double maxScore = current.getScore() / 0.75;

        AlertUtil.forEachChannel(GuildDB::isValidAlliance, GuildKey.ENEMY_ALERT_CHANNEL, new BiConsumer<MessageChannel, GuildDB>() {
            @Override
            public void accept(MessageChannel channel, GuildDB guildDB) {
                EnemyAlertChannelMode mode = guildDB.getOrNull(GuildKey.ENEMY_ALERT_CHANNEL_MODE);
                if (mode == null) mode = EnemyAlertChannelMode.PING_USERS_IN_RANGE;

                NationFilter filter = GuildKey.ENEMY_ALERT_FILTER.getOrNull(guildDB);
                if (filter != null && !filter.recalculate(10000).test(current)) return;

                double strength = BlitzGenerator.getAirStrength(current, false);
                Set<Integer> enemies = guildDB.getCoalition(Coalition.ENEMIES);
                if (!enemies.isEmpty() && (enemies.contains(current.getAlliance_id()))) {
                    boolean inRange = false;
                    Set<DBNation> nations = guildDB.getMemberDBNations();
                    for (DBNation nation : nations) {
                        if (nation.getScore() >= minScore && nation.getScore() <= maxScore && nation.active_m() < 1440 && nation.getOff() < nation.getMaxOff() && BlitzGenerator.getAirStrength(nation, true) > strength * 0.7) {
                            inRange = true;
                        }
                    }
                    if (!inRange && mode.requireInRange()) return;

                    String cardInfo = current.toEmbedString();
                    DiscordChannelIO io = new DiscordChannelIO(channel);

                    IMessageBuilder msg = io.create();
                    msg = msg.embed(title, cardInfo);

                    Guild guild = guildDB.getGuild();
                    Role bountyRole = Roles.BEIGE_ALERT.toRole(guild);
                    Role bountyRoleOffline = Roles.ENEMY_ALERT_OFFLINE.toRole(guild);
                    boolean checkDiscord = true;
                    if (bountyRole == null) {
                        bountyRole = bountyRoleOffline;
                        checkDiscord = false;
                    }
                    if (bountyRole == null) {
                        msg.send();
                        return;
                    }

                    if (mode.pingUsers()) {
                        List<Member> members = guild.getMembersWithRoles(bountyRole);

                        Role optOut = Roles.WAR_ALERT_OPT_OUT.toRole(guild);

                        List<Map.Entry<DBNation, Member>> priority1 = new ArrayList<>(); // stronger with more cities no wars
                        List<Map.Entry<DBNation, Member>> priority2 = new ArrayList<>(); // stronger with no wars
                        List<Map.Entry<DBNation, Member>> priority3 = new ArrayList<>(); // stronger
                        List<Map.Entry<DBNation, Member>> priority4 = new ArrayList<>(); // weaker

                        for (Member member : members) {
                            PNWUser pnwUser = Locutus.imp().getDiscordDB().getUserFromDiscordId(member.getIdLong());
                            if (pnwUser == null) continue;

                            DBNation attacker = Locutus.imp().getNationDB().getNation(pnwUser.getNationId());
                            if (attacker == null) continue;

                            OnlineStatus status = member.getOnlineStatus();
                            if (attacker.active_m() > 15 && checkDiscord && (status == OnlineStatus.OFFLINE || status == OnlineStatus.INVISIBLE)) {
                                if (bountyRoleOffline == null || !member.getRoles().contains(bountyRoleOffline)) {
                                    continue;
                                }
                            }
                            if (optOut != null && member.getRoles().contains(optOut)) continue;

                            if (/* attacker.active_m() > 1440 || */attacker.getDef() >= 3 || attacker.getVm_turns() != 0 || attacker.isBeige())
                                continue;
                            if (attacker.getScore() < minScore || attacker.getScore() > maxScore) continue;
                            if (attacker.getOff() > 4) continue;
                            if (attacker.hasUnsetMil() || current.hasUnsetMil()) continue;
                            int planeCap = Buildings.HANGAR.cap(attacker::hasProject) * Buildings.HANGAR.getUnitCap() * attacker.getCities();
                            if (attacker.getAircraft() < planeCap * 0.8) continue;

                            double attStr = BlitzGenerator.getAirStrength(attacker, true, true);
                            double defStr = BlitzGenerator.getAirStrength(current, false, true);

                            AbstractMap.SimpleEntry<DBNation, Member> entry = new AbstractMap.SimpleEntry<>(attacker, member);

                            if (attacker.getCities() < current.getCities() * 0.66 && (current.active_m() < 3000))
                                continue;
                            if (attacker.getCities() < current.getCities() * 0.70 && (current.active_m() < 2440))
                                continue;
                            if (attacker.getCities() < current.getCities() * 0.75 && (current.getSoldiers() > attacker.getSoldiers() * 0.33 || current.getAircraft() > attacker.getAircraft() * 0.66))
                                continue;
                            if (attacker.getNumWars() == 0) {
                                if (attStr > defStr) {
                                    if (attacker.getCities() > current.getCities()) {
                                        priority1.add(entry);
                                        continue;
                                    } else {
                                        priority2.add(entry);
                                        continue;
                                    }
                                }
                            }
                            if (attStr > defStr && attacker.getShips() > current.getShips() && attacker.getSoldiers() > current.getSoldiers()) {
                                priority3.add(entry);
                                continue;
//                                    } else if (defStr * 0.66 >= defStr) {
//                                        priority4.add(entry);
//                                        continue;
                            }
                        }
                        if (!priority1.isEmpty()) {
                            String mentions = StringMan.join(priority1.stream().map(e -> e.getValue().getAsMention()).collect(Collectors.toList()), ",");
                            msg.append("priority1: " + mentions);
                        }
                        if (!priority2.isEmpty()) {
                            String mentions = StringMan.join(priority2.stream().map(e -> e.getValue().getAsMention()).collect(Collectors.toList()), ",");
                            msg.append("priority2: " + mentions);
                        }
                        if (!priority3.isEmpty()) {
                            String mentions = StringMan.join(priority3.stream().map(e -> e.getValue().getAsMention()).collect(Collectors.toList()), ",");
                            msg.append("priority3: " + mentions);
                        }
                        if (!priority4.isEmpty()) {
                            String mentions = StringMan.join(priority4.stream().map(e -> e.getValue().getAsMention()).collect(Collectors.toList()), ",");
                            msg.append("priority4: " + mentions);
                        }
                        if (!priority1.isEmpty() || !priority2.isEmpty() || !priority3.isEmpty() || !priority4.isEmpty()) {
                            msg.append(" (see: " + CM.alerts.enemy.optout.cmd.toSlashMention() + " to opt out)");
                        }
                    } else if (mode.pingRole()) {
                        msg.append(bountyRole.getAsMention());
                    }
                    msg.send();
                }
            }
        });
        return true;
    }

    private boolean raidAlert(DBNation defender) {
        if (defender.getDef() > 2) return false;
        if (defender.active_m() > 260 * 60 * 24) return false;
        if (defender.isBeige()) return false;
        double loot = defender.lootTotal();
        if (loot < 10000000) {
            return false;
        }
        String msg = defender.toMarkdown(true, true, true, true, true, false);
        String title = "Target: " + defender.getNation() + ": You can loot: ~$" + MathMan.format(loot);

        String url = "https://politicsandwar.com/nation/war/declare/id=" + defender.getNation_id();
        if (defender.getCities() >= 10) {
            msg += "\n" + url;
        }

        String finalMsg = msg;
        AlertUtil.forEachChannel(f -> f.hasCoalitionPermsOnRoot(Coalition.RAIDPERMS), GuildKey.BEIGE_ALERT_CHANNEL, new BiConsumer<MessageChannel, GuildDB>() {
            @Override
            public void accept(MessageChannel channel, GuildDB guildDB) {
                if (!guildDB.isWhitelisted() || !guildDB.hasCoalitionPermsOnRoot(Coalition.RAIDPERMS)) return;

                if (guildDB.violatesDNR(defender) || (defender.getPosition() > 1 && defender.active_m() < 10000)) return;

                Guild guild = guildDB.getGuild();
                Set<Integer> ids = guildDB.getAllianceIds(false);

                Role beigeAlert = Roles.BEIGE_ALERT.toRole(guild);
                if (beigeAlert == null) return;

                List<Member> members = guild.getMembersWithRoles(beigeAlert);
                StringBuilder mentions = new StringBuilder();

                double minScore = defender.getScore() / PW.WAR_RANGE_MAX_MODIFIER;
                double maxScore = defender.getScore() / 0.75;

                Role beigeAlertOptOut = Roles.BEIGE_ALERT_OPT_OUT.toRole(guild);
                int membersInRange = 0;

                Function<DBNation, Boolean> canRaid = guildDB.getCanRaid();

                Map<DBNation, Double> lootEstimateByNation = new HashMap<>();

                Map<DBNation, Double> scoreLeewayMap = new HashMap<>();
                Function<DBNation, Double> scoreLeewayFunc = f -> scoreLeewayMap.computeIfAbsent(f, n -> {
                    ByteBuffer buf = n.getMeta(NationMeta.BEIGE_ALERT_SCORE_LEEWAY);
                    return buf == null ? 0 : buf.getDouble();
                });

                for (Member member : members) {
                    DBNation attacker = DiscordUtil.getNation(member.getUser());
                    if (attacker == null || attacker.getOff() >= attacker.getMaxOff()) continue;
                    if (attacker.getScore() < minScore || attacker.getScore() > maxScore) continue;

                    if (!LeavingBeigeAlert.testBeigeAlertAuto(guildDB, member, beigeAlert, beigeAlertOptOut, ids, false, false)) {
                        continue;
                    }

                    NationMeta.BeigeAlertMode mode = attacker.getBeigeAlertMode(NationMeta.BeigeAlertMode.NO_ALERTS);
                    if (mode == NationMeta.BeigeAlertMode.NO_ALERTS) continue;
                    if (mode == null) mode = NationMeta.BeigeAlertMode.NONES;

                    membersInRange++;

                    double requiredLoot = attacker.getBeigeAlertRequiredLoot();
                    if (!LeavingBeigeAlert.testBeigeAlertAuto(attacker, defender, requiredLoot, mode, canRaid, scoreLeewayFunc, lootEstimateByNation, false)) {
                        continue;
                    }

                    long pair = MathMan.pairInt(attacker.getNation_id(), defender.getNation_id());
                    if (!pingFlag.containsKey(pair)) {
                        mentions.append(member.getAsMention() + " ");
                    }
                    pingFlag.put(pair, true);
                }
                if (membersInRange > 0) {
                    DiscordUtil.createEmbedCommand(channel, title, finalMsg);
                    if (mentions.length() != 0) {
                        RateLimitUtil.queueWhenFree(channel.sendMessage("^ " + mentions + " (Opt out via: " + CM.alerts.beige.beigeAlertOptOut.cmd.toSlashMention() + ")"));
                    }
                }
            }
        });
        return true;
    }

    private void handleOfficerDelete(DBNation previous, DBNation current) {
        if (current != null || previous == null || previous.active_m() > 10000) return;
        if (previous.getPosition() < Rank.OFFICER.id) {
            DBAlliancePosition position = previous.getAlliancePosition();
            if (position == null || !position.hasAnyAdminPermission()) return;
        }
        DBAlliance alliance = previous.getAlliance(false);
        if (alliance != null && alliance.getRank() < 50) {
            String title = previous.getNation() + " (" + Rank.byId(previous.getPosition()) + ") deleted from " + previous.getAllianceName();
            String body = previous.toEmbedString();
            AlertUtil.forEachChannel(f -> true, GuildKey.ORBIS_OFFICER_LEAVE_ALERTS, new BiConsumer<MessageChannel, GuildDB>() {
                @Override
                public void accept(MessageChannel channel, GuildDB guildDB) {
                    DiscordUtil.createEmbedCommand(channel, title, body);
                }
            });
        }
    }

    @Subscribe
    public void onAllianceChange(NationChangeAllianceEvent event) {
        DBNation previous = event.getPrevious();
        DBNation current = event.getCurrent();

        checkExodus(previous, current);
        Rank rank = previous.getPositionEnum();
        if (rank == Rank.MEMBER) {
            DBAlliancePosition position = previous.getAlliancePosition();
            if (position != null) {
                if (position.hasAnyOfficerPermissions()) rank = Rank.OFFICER;
                if (position.hasPermission(AlliancePermission.PROMOTE_SELF_TO_LEADER)) rank = Rank.HEIR;
            }
        }
        if (previous.getAlliance_id() != current.getAlliance_id() || rank != current.getPositionEnum()) {
            Locutus.imp().getNationDB().addRemove(current.getNation_id(), previous.getAlliance_id(), current.getAlliance_id(), rank, current.getPositionEnum(), event.getTimeCreated());
        }
    }

    @Subscribe
    public void onNationChangeName(NationChangeNameEvent event) {
        User user = event.getCurrent().getUser();
        if (user != null) {
            for (Guild guild : user.getMutualGuilds()) {
                GuildDB db = Locutus.imp().getGuildDB(guild);
                if (db == null) continue;
                Member member = guild.getMember(user);
                if (member == null) continue;

                if (db.getOrNull(GuildKey.AUTONICK) == GuildDB.AutoNickOption.NATION) {
                    try {
                        AutoRoleInfo task = db.getAutoRoleTask().autoRole(member, event.getCurrent());
                        task.execute();
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    @Subscribe
    public void onNationChangeLeader(NationChangeLeaderEvent event) {
        User user = event.getCurrent().getUser();
        if (user != null) {
            for (Guild guild : user.getMutualGuilds()) {
                GuildDB db = Locutus.imp().getGuildDB(guild);
                if (db == null) continue;
                Member member = guild.getMember(user);
                if (member == null) continue;

                if (db.getOrNull(GuildKey.AUTONICK) == GuildDB.AutoNickOption.LEADER) {
                    try {
                        AutoRoleInfo task = db.getAutoRoleTask().autoRole(member, event.getCurrent());
                        task.execute();
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    @Subscribe
    public void onPositionChange(NationChangePositionEvent event) {
        DBNation current = event.getCurrent();
        DBNation previous = event.getPrevious();

        if (current.getAlliance_id() == previous.getAlliance_id() && previous.getPositionEnum() != current.getPositionEnum()) {
            Locutus.imp().getNationDB().addRemove(current.getNation_id(), previous.getAlliance_id(), current.getAlliance_id(), previous.getPositionEnum(), current.getPositionEnum(), event.getTimeCreated());
        }
    }

    @Subscribe
    public void onNationChangeMilitary(NationChangeUnitEvent event) {
        DBNation current = event.getCurrent();
        DBNation previous = event.getPrevious();

        MilitaryUnit unit = event.getUnit();
        int currentVal = current.getUnits(unit);
        int previousVal = previous.getUnits(unit);
        if (currentVal != previousVal) {
            Locutus.imp().getNationDB().setMilChange(event.getTimeCreated(), current.getNation_id(), unit, previousVal, currentVal);
        }

        if (currentVal > previousVal) {
            // TODO post unit buy to war rooms
        }
    }

    private static void checkExodus(DBNation previous, DBNation current) {
        if (current == null || previous == null || previous.getAlliance_id() == current.getAlliance_id() || current.active_m() > 4880 || previous.getAlliance_id() == 0 || previous.getPosition() == 1) return;
        DBAlliance alliance = previous.getAlliance(false);
        if (alliance == null || alliance.getRank() > 120) return;

        Set<DBAlliance> joinedAlliances = new LinkedHashSet<>(Arrays.asList(alliance));

        List<String> departureInfo = new ArrayList<>();
        int memberRemoves = 0;

        double scoreDrop = 0;
        long now = System.currentTimeMillis();
        long cutoff = now - TimeUnit.DAYS.toMillis(1);
        List<AllianceChange> removes = new ArrayList<>(alliance.getRankChanges(cutoff));
        Map<Integer, AllianceChange> changesByNation = new LinkedHashMap<>();
        for (AllianceChange change : removes) {
            changesByNation.put(change.getNationId(), change);
        }
        changesByNation.put(current.getNation_id(), new AllianceChange(previous, current, now));

        for (Map.Entry<Integer, AllianceChange> entry : changesByNation.entrySet()) {
            int nationId = entry.getKey();
            AllianceChange change = entry.getValue();
            if (change.getDate() < cutoff) continue;
            DBNation nation = Locutus.imp().getNationDB().getNation(nationId);
            if (nation == null) continue;

            if (nation.getAlliance_id() == alliance.getAlliance_id()) continue;
            scoreDrop += nation.getScore();

            if (nation.active_m() > 4880 || nation.getVm_turns() > 0 || nation.getCities() <= 3) continue;

            String line = PW.getMarkdownUrl(nation.getId(), false) + ", c" + nation.getCities() + ", " + change.getFromRank().name();
            if (nation.getAlliance_id() > 0) {
                line += "-> " + nation.getAllianceUrlMarkup(true);
            } else {
                line += " -> None";
            }
            departureInfo.add(line);
            memberRemoves++;

            DBAlliance joinedAlliance = nation.getAlliance();
            if (joinedAlliance != null && !joinedAlliance.equals(alliance)) {
                joinedAlliances.add(joinedAlliance);
            }
        }

        CM.alliance.departures cmd = CM.alliance.departures.cmd.nationOrAlliance(alliance.getQualifiedId()).time("7d").filter("*,#alliance_id!=" + alliance.getId()).ignoreInactives("true").ignoreVM("true");

        if (memberRemoves >= 5) {
            Map<DBAlliance, Integer> ranks = Locutus.imp().getNationDB().getAllianceRanks(f -> f.getVm_turns() == 0 && f.getPositionEnum().id > Rank.MEMBER.id, true);
            int aaRank = ranks.getOrDefault(alliance, -1);
            String title = memberRemoves + " departures from " + alliance.getName();
            byte[] graphData = null;
            {
                joinedAlliances.removeIf(f -> f.getRank() > 80);
                if (!joinedAlliances.isEmpty()) {
                    long endTurn = TimeUtil.getTurn();
                    long startTurn = endTurn - 120;
                    List<String> coalitions = joinedAlliances.stream().map(DBAlliance::getName).collect(Collectors.toList());
                    List<Set<DBAlliance>> alliances = joinedAlliances.stream().map(Collections::singleton).toList();
                    TimeNumericTable table = AllianceMetric.generateTable(AllianceMetric.MEMBERS, startTurn, endTurn, coalitions, alliances.toArray(new Set[0]));
                    try {
                        graphData = table.write(TimeFormat.TURN_TO_DATE, AllianceMetric.MEMBERS.getFormat());
                    } catch (IOException e) {
                    }
                }
            }

            String body = PW.getMarkdownUrl(alliance.getId(), true) +
                    "(-" + MathMan.format(scoreDrop) + " score)";

            int remaining = 3950 - body.length();
            for (int i = departureInfo.size() - 1; i >= 0; i--) {
                String line = departureInfo.get(i);
                if (remaining - line.length() - 1 < 0) {
                    departureInfo = departureInfo.subList(i + 1, departureInfo.size());
                    break;
                }
                remaining -= line.length() + 1;
            }
            body += "\n" + StringMan.join(departureInfo, "\n");
            String finalBody = body;
            byte[] finalGraphData = graphData;
            AlertUtil.forEachChannel(f -> true, GuildKey.ORBIS_ALLIANCE_EXODUS_ALERTS, new BiConsumer<MessageChannel, GuildDB>() {
                @Override
                public void accept(MessageChannel channel, GuildDB guildDB) {
                    Integer topX = GuildKey.ALLIANCE_EXODUS_TOP_X.getOrNull(guildDB);
                    if (topX != null && topX < aaRank) return;
                    IMessageBuilder msg = new DiscordChannelIO(channel).create().embed(title, finalBody).commandButton(CommandBehavior.EPHEMERAL, cmd, "list departures");
                    if (finalGraphData != null) {
                        msg = msg.image("members.png", finalGraphData);
                    }
                    msg.sendWhenFree();
                }
            });
        }
    }

    private static void processVMTransfers(DBNation previous, DBNation current) {
        long cutoffMs = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(14);
        List<Transaction2> transfers = Locutus.imp().getBankDB().getNationTransfers(current.getNation_id(), cutoffMs);
        List<DBTrade> trades = Locutus.imp().getTradeManager().getTradeDb().getTrades(current.getNation_id(), cutoffMs);

        long total = 0;

        Map<String, Double> outflows = new HashMap<>();
        Map<String, Double> inflows = new HashMap<>();
        for (Transaction2 transfer : transfers) {
            Map<String, Double> map;
            int otherId;
            if (transfer.getReceiver() == current.getNation_id()) {
                map = inflows;
                otherId = (int) transfer.getSender();
            } else {
                map = outflows;
                otherId = (int) transfer.getReceiver();
            }
            String name = PW.getBBUrl(otherId, true);

            double value = transfer.convertedTotal();
            map.put(name, map.getOrDefault(name, 0d) + value);

            total += value;
        }

        for (DBTrade offer : trades) {
            if (offer.getPpu() > 1 && offer.getPpu() < 10000) {
                continue;
            }
            Integer buyer = offer.getBuyer();
            Integer seller = offer.getSeller();
            int sign = (seller.equals(current.getNation_id()) ^ offer.isBuy()) ? 1 : -1;
            Map<String, Double> map;
            if (sign > 0) {
                map = inflows;
            } else {
                map = outflows;
            }
            int otherId = seller.equals(current.getNation_id()) ? buyer : seller;
            String name = PW.getBBUrl(otherId, false);

            double value = offer.getPpu() > 1 ? offer.getQuantity() * offer.getPpu() : Locutus.imp().getTradeManager().getLow(offer.getResource()) * offer.getQuantity();
            map.put(name, map.getOrDefault(name, 0d) + value);

            total += value;
        }
        if (total > 10000000) {
            StringBuilder body = new StringBuilder();
            body.append(PW.getBBUrl(current.getNation_id(), false) + " | " + PW.getBBUrl(current.getAlliance_id(), true) + " | " + current.getNation_id());
            if (current.getDate() != 0) {
                long ageDays = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis()) - current.getDate() / 65536;
                body.append("\nDays old: " + ageDays);
            }
            body.append("\n\nInflows (14d / converted value): ");
            for (Map.Entry<String, Double> entry : inflows.entrySet()) {
                body.append("\n").append(entry.getKey()).append(": $").append(MathMan.format(entry.getValue()));
            }

            body.append("\n\nOutflows (14d / converted value): ");
            for (Map.Entry<String, Double> entry : outflows.entrySet()) {
                body.append("\n").append(entry.getKey()).append(": $").append(MathMan.format(entry.getValue()));
            }

            String type = current.getVm_turns() != 0 && previous.getVm_turns() == 0 ? "VM" : "DELETION";
            String title = type + " | " + current.getNation() + " | " + current.getAllianceName();

            try {
                for (Map.Entry<Long, GuildDB> entry : Locutus.imp().getGuildDatabases().entrySet()) {
                    GuildDB guildDB = entry.getValue();
                    MessageChannel channel = guildDB.getOrNull(GuildKey.VM_ALERT_CHANNEL, false);
                    if (channel == null) {
                        continue;
                    }
                    DiscordUtil.createEmbedCommand(channel, title, body.substring(0, Math.min(body.length(), 2000)));
                }

            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
    }

    private static void processLeaderChange(DBNation previous, DBNation current) {
        if (previous == null || current == null) return;
        if (previous.getPosition() == current.getPosition() && previous.getAlliance_id() == current.getAlliance_id()) return;
        if (previous.getPosition() != Rank.LEADER.id && current.getPosition() != Rank.LEADER.id) return;

        DBAlliance aa1 = previous.getAlliance();
        DBAlliance aa2 = current.getAlliance();

        boolean isRelevant = (previous.getAlliance_id() != 0 && aa1.getRank() < 80)
                || (current.getAlliance_id() != 0 && current.getAlliance_id() != previous.getAlliance_id() && aa2.getRank() < 80);

        if (isRelevant) {
            AlertUtil.forEachChannel(f -> true, GuildKey.ORBIS_LEADER_CHANGE_ALERT, new BiConsumer<MessageChannel, GuildDB>() {
                @Override
                public void accept(MessageChannel channel, GuildDB guildDB) {
                    String title = current.getNation() + " | " + Rank.byId(previous.getPosition()) + "->" + Rank.byId(current.getPosition());
                    String body = previous.getNationUrlMarkup(true) + "\n" +
                            "From: " + previous.getAllianceUrlMarkup(true) + " | " + Rank.byId(previous.getPosition()) + "\n" +
                            "To: " + current.getAllianceUrlMarkup(true) + " | " + Rank.byId(current.getPosition());

                    DiscordUtil.createEmbedCommand(channel, title, body);
                }
            });
        }
    }

    public static void processDeletion(DBNation previous, DBNation current) {
        if (previous.active_m() < 14400 && current == null) {
            processVMTransfers(previous, previous);

            String type = "DELETION";
            StringBuilder body = new StringBuilder(previous.toEmbedString());
            String finalType = type;
            String finalBody = body.toString();
            String title = "Detected " + finalType + ": " + previous.getNation() + " | " + "" + Settings.INSTANCE.PNW_URL() + "/nation/id=" + previous.getNation_id() + " | " + previous.getAllianceName();
            AlertUtil.forEachChannel(f -> true, GuildKey.DELETION_ALERT_CHANNEL, new BiConsumer<MessageChannel, GuildDB>() {
                @Override
                public void accept(MessageChannel channel, GuildDB db) {
                    AlertUtil.displayChannel(title, finalBody, channel.getIdLong());
                }
            });
        }
    }
}