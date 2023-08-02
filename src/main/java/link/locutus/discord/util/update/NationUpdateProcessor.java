package link.locutus.discord.util.update;

import com.google.common.eventbus.Subscribe;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.SuccessType;
import link.locutus.discord.apiv3.enums.AlliancePermission;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.NationFilter;
import link.locutus.discord.commands.trade.subbank.BankAlerts;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.*;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.event.nation.NationBlockadedEvent;
import link.locutus.discord.event.nation.*;
import link.locutus.discord.event.nation.NationUnblockadedEvent;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.pnw.PNWUser;
import link.locutus.discord.util.AlertUtil;
import link.locutus.discord.util.FileUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.io.PagePriority;
import link.locutus.discord.util.scheduler.CaughtRunnable;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.battle.BlitzGenerator;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AbstractCursor;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.util.PnwUtil;
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
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.nio.ByteBuffer;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class NationUpdateProcessor {
    // TODO update war rooms

    public static void updateBlockades() {
        long now = System.currentTimeMillis();

        Map<Integer, DBWar> wars = Locutus.imp().getWarDb().getWarsSince(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(10));

        List<AbstractCursor> attacks = Locutus.imp().getWarDb().queryAttacks().withWars(wars).withType(AttackType.NAVAL).afterDate(now - TimeUnit.DAYS.toMillis(10)).getList();

        Collections.sort(attacks, Comparator.comparingLong(o -> o.getDate()));

        Map<Integer, Map<Integer, Integer>> blockadedByNationByWar = new HashMap<>(); // map of nations getting blockaded
        Map<Integer, Map<Integer, Integer>> blockadingByNationByWar = new HashMap<>(); // map of nations blockading

        for (AbstractCursor attack : attacks) {
            if (attack.getAttack_type() != AttackType.NAVAL) continue;

            DBNation defender = DBNation.getById(attack.getDefender_id());
            if (defender == null) continue;

            if (attack.getSuccess() == SuccessType.IMMENSE_TRIUMPH) {
                Map<Integer, Integer> defenderBlockades = blockadingByNationByWar.get(attack.getDefender_id());
                if (defenderBlockades != null && !defenderBlockades.isEmpty()) {
                    for (Map.Entry<Integer, Integer> entry : defenderBlockades.entrySet()) {
                        int blockaded = entry.getKey();
                        int warId = entry.getValue();
                        blockadedByNationByWar.getOrDefault(blockaded, Collections.emptyMap()).remove(attack.getDefender_id());
                    }
                    defenderBlockades.clear();
                }

                DBWar war = wars.get(attack.getWar_id());
                // Only if war is active
                if (war != null && (war.status == WarStatus.ACTIVE || war.status == WarStatus.DEFENDER_OFFERED_PEACE || war.status == WarStatus.ATTACKER_OFFERED_PEACE)) {
                    blockadedByNationByWar.computeIfAbsent(attack.getDefender_id(), f -> new HashMap<>()).put(attack.getAttacker_id(), attack.getWar_id());
                    blockadingByNationByWar.computeIfAbsent(attack.getAttacker_id(), f -> new HashMap<>()).put(attack.getDefender_id(), attack.getWar_id());
                }
            }
            if (attack.getSuccess().ordinal() >= SuccessType.MODERATE_SUCCESS.ordinal()) {
                blockadedByNationByWar.getOrDefault(attack.getAttacker_id(), Collections.emptyMap()).remove(attack.getDefender_id());
                blockadingByNationByWar.getOrDefault(attack.getDefender_id(), Collections.emptyMap()).remove(attack.getAttacker_id());
            }
        }

        Set<Integer> nationIds = new HashSet<>();
        nationIds.addAll(blockadedByNationByWar.keySet());
        nationIds.addAll(blockadingByNationByWar.keySet());

        // Remove if nation is deleted
        for (Integer nationId : nationIds) {
            DBNation nation = DBNation.getById(nationId);
            if (nation == null) {
                Map<Integer, Integer> blockading = blockadingByNationByWar.remove(nationId);
                if (blockading != null && !blockading.isEmpty()) {
                    for (Map.Entry<Integer, Integer> entry : blockading.entrySet()) {
                        Integer blockadedId = entry.getKey();
                        blockadedByNationByWar.getOrDefault(blockadedId, Collections.emptyMap()).remove(nationId);
                    }
                }
            }
        }

        Map<Integer, Set<Integer>> previous = Locutus.imp().getWarDb().getBlockadedByNation(true);
        Set<Long> previousBlockadedBlockaderPair = new HashSet<>();
        Set<Long> currentBlockadedBlockaderPair = new HashSet<>();

        for (Map.Entry<Integer, Set<Integer>> entry : previous.entrySet()) {
            int blockaded = entry.getKey();
            for (Integer blockader : entry.getValue()) {
                long pair = MathMan.pairInt(blockaded, blockader);
                previousBlockadedBlockaderPair.add(pair);
            }
        }

        for (Map.Entry<Integer, Map<Integer, Integer>> entry : blockadedByNationByWar.entrySet()) {
            int blockaded = entry.getKey();
            for (Map.Entry<Integer, Integer> entry2 : entry.getValue().entrySet()) {
                int blockader = entry2.getKey();
                long pair = MathMan.pairInt(blockaded, blockader);
                currentBlockadedBlockaderPair.add(pair);
            }
        }

        for (long pair : previousBlockadedBlockaderPair) {
            if (!currentBlockadedBlockaderPair.contains(pair)) {
                int blockaded = MathMan.unpairIntX(pair);
                int blockader = MathMan.unpairIntY(pair);

                Locutus.imp().getWarDb().deleteBlockaded(blockaded, blockader);

                new NationUnblockadedEvent(blockaded, blockader, blockadedByNationByWar.getOrDefault(blockaded, Collections.emptyMap())).post();
            }
        }
        for (long pair : currentBlockadedBlockaderPair) {
            if (!previousBlockadedBlockaderPair.contains(pair)) {
                int blockaded = MathMan.unpairIntX(pair);
                int blockader = MathMan.unpairIntY(pair);

                new NationBlockadedEvent(blockaded, blockader, blockadedByNationByWar.getOrDefault(blockaded, Collections.emptyMap())).post();

                Locutus.imp().getWarDb().addBlockaded(blockaded, blockader);
            }
        }
    }

    private static Map<Integer, Integer> ACTIVITY_ALERTS = new PassiveExpiringMap<Integer, Integer>(240, TimeUnit.MINUTES);

    public static void onActivityCheck() {
        Map<Integer, Integer> membersByAA = new HashMap<>(); // only <7d non vm nations
        Map<Integer, Integer> activeMembersByAA = new HashMap<>();
        Map<Integer, Double> averageMilitarization = new HashMap<>();
        for (Map.Entry<Integer, DBNation> entry : Locutus.imp().getNationDB().getNations().entrySet()) {
            DBNation nation = entry.getValue();
            if (nation.getActive_m() > 7200 || nation.getPosition() <= Rank.APPLICANT.id || nation.getVm_turns() > 0) continue;
            int aaId = nation.getAlliance_id();
            membersByAA.put(aaId, membersByAA.getOrDefault(aaId, 0) + 1);
            boolean active = nation.getActive_m() < 30;
            if (!active && nation.getActive_m() < 1440 && nation.getVm_turns() == 0 && nation.getPositionEnum().id > Rank.APPLICANT.id) {
                User user = nation.getUser();
                if (user != null) {
                    List<Guild> mutual = user.getMutualGuilds();
                    if (!mutual.isEmpty()) {
                        Member member = mutual.get(0).getMember(user);
                        if (member != null && member.getOnlineStatus() == OnlineStatus.ONLINE) {
                            active = true;
                        }
                    }
                }
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
                String message = ("This is your login alert for:\n" + nation.toEmbedString(true));

                for (Map.Entry<Long, Long> entry : notifyMap.entrySet()) {
                    Long userId = entry.getKey();
                    User user = Locutus.imp().getDiscordApi().getUserById(userId);
                    DBNation attacker = DiscordUtil.getNation(userId);
                    if (user == null || attacker == null) continue;


                    boolean hasActiveWar = false;
                    for (DBWar war : nation.getActiveWars()) {
                        if (war.attacker_id == attacker.getNation_id() || war.defender_id == attacker.getNation_id()) {
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
            Locutus.imp().getNationDB().setSpyActivity(nation.getNation_id(), nation.getProjectBitMask(), nation.getSpies(), activeMinute, nation.getWarPolicy());

            if (previous.active_m() > 360 && Settings.INSTANCE.TASKS.AUTO_FETCH_UID) {
                Locutus.imp().getExecutor().submit(new CaughtRunnable() {
                    @Override
                    public void runUnsafe() throws Exception {
                        nation.fetchUid();
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
                    body.append("\nReroll of: " + PnwUtil.getNationUrl(rerollId));
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
        if (current == null || previous == null || previous.getAlliance_id() == current.getAlliance_id() || current.getActive_m() > 4880) return;
        if (previous.getPosition() < Rank.OFFICER.id) {
            DBAlliancePosition position = previous.getAlliancePosition();
            if (position == null || !position.hasAnyAdminPermission()) return;
        }
        DBAlliance alliance = previous.getAlliance(false);

        if (alliance != null && alliance.getRank() < 50)
        {
            String title = current.getNation() + " (" + Rank.byId(previous.getPosition()) + ") leaves " + previous.getAllianceName();
            String body = current.toEmbedString(false);
            AlertUtil.forEachChannel(f -> true, GuildKey.ORBIS_OFFICER_LEAVE_ALERTS, new BiConsumer<MessageChannel, GuildDB>() {
                @Override
                public void accept(MessageChannel channel, GuildDB guildDB) {
                    DiscordUtil.createEmbedCommand(channel, title, body);
                }
            });
        }
    }

    @Subscribe
    public void onNationDelete(NationDeleteEvent event) {
        DBNation previous = event.getPrevious();
        DBNation current = event.getCurrent();

        handleOfficerDelete(previous, current);
        processDeletion(previous, current);
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
        if (previous.isBeige() && !current.isBeige() && current.getActive_m() < 10000) {
            title = "Left Beige: " + current.getNation() + " | " + current.getAllianceName();
            leftVMBeige = true;
        } else if (previous.getVm_turns() > 0 && current.getVm_turns() == 0) {
            title = "Left VM: " + current.getNation() + " | " + current.getAllianceName();
            leftVMBeige = true;
        } else if (previous.getActive_m() <= 10080 && current.getActive_m() > 10080) {
            title = "Inactive: " + current.getNation() + " | " + current.getAllianceName();
            leftVMBeige = true;
        } else if (previous.getAlliance_id() != 0 && current.getAlliance_id() == 0 && current.getActive_m() > 1440) {
            title = "Removed: " + current.getNation() + " | " + current.getAllianceName();
            leftVMBeige = true;
        } else if (previous.getDef() >= 3 && current.getDef() < 3 && !current.isBeige()) {
            title = "Unslotted: " + current.getNation() + " | " + current.getAllianceName();
//                    leftVMBeige = true;
        } else {
            return false;
        }

        double minScore = current.getScore() / 1.75;
        double maxScore = current.getScore() / 0.75;

        AlertUtil.forEachChannel(GuildDB::isValidAlliance, GuildKey.ENEMY_ALERT_CHANNEL, new BiConsumer<MessageChannel, GuildDB>() {
            @Override
            public void accept(MessageChannel channel, GuildDB guildDB) {
                EnemyAlertChannelMode mode = guildDB.getOrNull(GuildKey.ENEMY_ALERT_CHANNEL_MODE);
                if (mode == null) mode = EnemyAlertChannelMode.PING_USERS_IN_RANGE;

                NationFilter filter = GuildKey.ENEMY_ALERT_FILTER.getOrNull(guildDB);
                if (filter != null && !filter.test(current)) return;

                double strength = BlitzGenerator.getAirStrength(current, false);
                Set<Integer> enemies = guildDB.getCoalition(Coalition.ENEMIES);
                if (!enemies.isEmpty() && (enemies.contains(current.getAlliance_id()))) {
                    boolean inRange = false;
                    Set<DBNation> nations = guildDB.getMemberDBNations();
                    for (DBNation nation : nations) {
                        if (nation.getScore() >= minScore && nation.getScore() <= maxScore && nation.getActive_m() < 1440 && nation.getOff() < nation.getMaxOff() && BlitzGenerator.getAirStrength(nation, true) > strength * 0.7) {
                            inRange = true;
                        }
                    }
                    if (!inRange && mode.requireInRange()) return;

                    String cardInfo = current.toEmbedString(true);
                    DiscordChannelIO io = new DiscordChannelIO(channel);

                    IMessageBuilder msg = io.create();
                    msg = msg.embed(title, cardInfo);

                    Guild guild = guildDB.getGuild();
                    Role bountyRole = Roles.BEIGE_ALERT.toRole(guild);
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
                            if (attacker.getActive_m() > 15 && (status == OnlineStatus.OFFLINE || status == OnlineStatus.INVISIBLE))
                                continue;
                            if (optOut != null && member.getRoles().contains(optOut)) continue;

                            if (/* attacker.getActive_m() > 1440 || */attacker.getDef() >= 3 || attacker.getVm_turns() != 0 || attacker.isBeige())
                                continue;
                            if (attacker.getScore() < minScore || attacker.getScore() > maxScore) continue;
                            if (attacker.getOff() > 4) continue;
                            if (attacker.hasUnsetMil() || current.hasUnsetMil()) continue;
                            int planeCap = Buildings.HANGAR.cap(attacker::hasProject) * Buildings.HANGAR.max() * attacker.getCities();
                            if (attacker.getAircraft() < planeCap * 0.8) continue;

                            double attStr = BlitzGenerator.getAirStrength(attacker, true, true);
                            double defStr = BlitzGenerator.getAirStrength(current, false, true);

                            AbstractMap.SimpleEntry<DBNation, Member> entry = new AbstractMap.SimpleEntry<>(attacker, member);

                            if (attacker.getCities() < current.getCities() * 0.66 && (current.getActive_m() < 3000))
                                continue;
                            if (attacker.getCities() < current.getCities() * 0.70 && (current.getActive_m() < 2440))
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
        if (defender.getActive_m() > 260 * 60 * 24) return false;
        if (defender.isBeige()) return false;
        double loot = defender.lootTotal();
        if (loot < 10000000) {
            return false;
        }
        String msg = defender.toMarkdown(true, true, true, true, false);
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

                if (guildDB.violatesDNR(defender) || (defender.getPosition() > 1 && defender.getActive_m() < 10000)) return;

                Guild guild = guildDB.getGuild();
                Set<Integer> ids = guildDB.getAllianceIds(false);

                Role beigeAlert = Roles.BEIGE_ALERT.toRole(guild);
                if (beigeAlert == null) return;

                List<Member> members = guild.getMembersWithRoles(beigeAlert);
                StringBuilder mentions = new StringBuilder();

                double minScore = defender.getScore() / 1.75;
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

                    NationMeta.BeigeAlertMode mode = attacker.getBeigeAlertMode(NationMeta.BeigeAlertMode.NONES);
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
        if (current != null || previous == null || previous.getActive_m() > 10000) return;
        if (previous.getPosition() < Rank.OFFICER.id) {
            DBAlliancePosition position = previous.getAlliancePosition();
            if (position == null || !position.hasAnyAdminPermission()) return;
        }
        DBAlliance alliance = previous.getAlliance(false);
        if (alliance.getRank() < 50) {
            String title = previous.getNation() + " (" + Rank.byId(previous.getPosition()) + ") deleted from " + previous.getAllianceName();
            String body = previous.toEmbedString(false);
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

        Locutus.imp().getNationDB().addRemove(current.getNation_id(), previous.getAlliance_id(), System.currentTimeMillis(), rank);
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

        Locutus.imp().getNationDB().addRemove(current.getNation_id(), previous.getAlliance_id(), System.currentTimeMillis(), Rank.byId(previous.getPosition()));
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
        if (current == null || previous == null || previous.getAlliance_id() == current.getAlliance_id() || current.getActive_m() > 4880 || previous.getAlliance_id() == 0 || previous.getPosition() == 1) return;
        DBAlliance alliance = previous.getAlliance(false);
        if (alliance == null || alliance.getRank() > 120) return;

        List<String> departureInfo = new ArrayList<>();
        departureInfo.add(PnwUtil.getMarkdownUrl(current.getId(), false) + ", cities: " + current.getCities() + ", " + Rank.byId(previous.getPosition()));
        int memberRemoves = 0;

        double scoreDrop = 0;
        Map<Integer, Map.Entry<Long, Rank>> removes = alliance.getRemoves();
        long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1);
        for (Map.Entry<Integer, Map.Entry<Long, Rank>> entry : removes.entrySet()) {
            if (entry.getValue().getKey() < cutoff) continue;
            DBNation nation = Locutus.imp().getNationDB().getNation(entry.getKey());
            if (nation == null) continue;

            if (nation.getAlliance_id() == alliance.getAlliance_id()) continue;
            scoreDrop += nation.getScore();

            if (nation.getActive_m() > 4880 || nation.getVm_turns() > 0 || nation.getCities() <= 3) continue;

            departureInfo.add(PnwUtil.getMarkdownUrl(nation.getId(), false) + ", cities: " + nation.getCities() + ", " + entry.getValue().getValue());


            memberRemoves++;
        }

        if (memberRemoves >= 5) {
            String title = memberRemoves + " departures from " + alliance.getName();
            String body = PnwUtil.getMarkdownUrl(alliance.getId(), true) +
                    "(-" + MathMan.format(scoreDrop) + " score)" +
                    "\n" + StringMan.join(departureInfo, "\n");
            AlertUtil.forEachChannel(f -> true, GuildKey.ORBIS_ALLIANCE_EXODUS_ALERTS, new BiConsumer<MessageChannel, GuildDB>() {
                @Override
                public void accept(MessageChannel channel, GuildDB guildDB) {
                    DiscordUtil.createEmbedCommand(channel, title, body);
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
            String name = PnwUtil.getBBUrl(otherId, true);

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
            String name = PnwUtil.getBBUrl(otherId, false);

            double value = offer.getPpu() > 1 ? offer.getQuantity() * offer.getPpu() : Locutus.imp().getTradeManager().getLow(offer.getResource()) * offer.getQuantity();
            map.put(name, map.getOrDefault(name, 0d) + value);

            total += value;
        }
        if (total > 10000000) {
            StringBuilder body = new StringBuilder();
            body.append(PnwUtil.getBBUrl(current.getNation_id(), false) + " | " + PnwUtil.getBBUrl(current.getAlliance_id(), true) + " | " + current.getNation_id());
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
                    Integer perm = guildDB.getPermission(BankAlerts.class);
                    if (perm == null || perm <= 0) continue;
                    MessageChannel channel = guildDB.getOrNull(GuildKey.BANK_ALERT_CHANNEL, false);
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
        if (previous.getActive_m() < 14400 && current == null) {
            processVMTransfers(previous, previous);

            List<String> adminInfo = new ArrayList<>();

            String type = "DELETION";
            String body = previous.toEmbedString(false);
            String url = previous.getNationUrl();
            try {
                String html = FileUtil.readStringFromURL(PagePriority.DELETION_ALERT_BAN.ordinal(), url);
                Document dom = Jsoup.parse(html);
                String alert = PnwUtil.getAlert(dom);
                if (alert != null && alert.startsWith("This nation was banned")) {
                    alert = alert.replace("Visit your nation, or go to the search page.",  "");
                    type = "BAN";
                    body += "\n" + alert;

                    if (previous.getAlliance_id() != 0 && previous.getPosition() > Rank.APPLICANT.id) {
                        GuildDB db = Locutus.imp().getGuildDBByAA(previous.getAlliance_id());
                        if (db != null && db.getOffshore() == Locutus.imp().getRootBank()) {
                            body += "\n" + previous.getAllianceUrlMarkup(true) + " " + previous.getPositionEnum();
                            Locutus.imp().getRootDb().addCoalition(previous.getAlliance_id(), Coalition.FROZEN_FUNDS);
                            adminInfo.add(previous.getAllianceUrlMarkup(true));
                            AlertUtil.error(previous.getAlliance_id() + " frozen", previous.getAllianceUrlMarkup(true) + " " + previous.getPositionEnum() + " " + previous.getNationUrlMarkup(true) + " " + previous.getNation());
                        }
                    }
                    User user = previous.getUser();
                    for (Guild guild : user.getMutualGuilds()) {
                        Member member = guild.getMember(user);
                        if (member != null && member.hasPermission(Permission.ADMINISTRATOR)) {
                            GuildDB db = Locutus.imp().getGuildDB(guild);
                            if (db != null && db.getOffshore() == Locutus.imp().getRootBank()) {
                                Locutus.imp().getRootDb().addCoalition(guild.getIdLong(), Coalition.FROZEN_FUNDS);
                                body += "\nguild: " + guild;
                                adminInfo.add(guild.toString());
                                AlertUtil.error(guild.getIdLong() + " frozen", guild.toString() + " " + previous.getNationUrlMarkup(true) + " " + previous.getNation());
                            }
                        }
                    }
                }
            } catch (Throwable ignore) {}

            String finalType = type;
            String finalBody = body;
            String title = "Detected " + finalType + ": " + previous.getNation() + " | " + "" + Settings.INSTANCE.PNW_URL() + "/nation/id=" + previous.getNation_id() + " | " + previous.getAllianceName();
            AlertUtil.forEachChannel(BankAlerts.class, GuildKey.DELETION_ALERT_CHANNEL, new BiConsumer<MessageChannel, GuildDB>() {
                @Override
                public void accept(MessageChannel channel, GuildDB db) {
                    AlertUtil.displayChannel(title, finalBody, channel.getIdLong());
                }
            });

            if (!adminInfo.isEmpty()) {
                MessageChannel channel = Locutus.imp().getRootDb().getResourceChannel(0);
                if (channel != null) {
                    String newBody = finalBody + "<@217897994375266304>\n" +
                            "See " + CM.offshore.unlockTransfers.cmd.toSlashMention() + "\n" +
                            "See `!coalitions FROZEN_FUNDS`";
                    new DiscordChannelIO(channel).create().embed(title, newBody).send();
                }
            }
        }
    }
}