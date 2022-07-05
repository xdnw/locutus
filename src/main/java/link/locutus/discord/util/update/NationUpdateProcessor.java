package link.locutus.discord.util.update;

import com.google.common.eventbus.Subscribe;
import link.locutus.discord.Locutus;
import link.locutus.discord.db.GuildHandler;
import link.locutus.discord.commands.war.WarCategory;
import link.locutus.discord.commands.trade.subbank.BankAlerts;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.NationDB;
import link.locutus.discord.db.entities.*;
import link.locutus.discord.event.game.TurnChangeEvent;
import link.locutus.discord.event.nation.NationBlockadedEvent;
import link.locutus.discord.event.city.CityCreateEvent;
import link.locutus.discord.event.nation.*;
import link.locutus.discord.event.nation.NationUnblockadedEvent;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.pnw.PNWUser;
import link.locutus.discord.util.AlertUtil;
import link.locutus.discord.util.AuditType;
import link.locutus.discord.util.FileUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.scheduler.CaughtRunnable;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.battle.BlitzGenerator;
import link.locutus.discord.util.trade.Offer;
import com.google.gson.JsonObject;
import link.locutus.discord.apiv1.domains.subdomains.DBAttack;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.DomesticPolicy;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.RateLimitUtil;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildChannel;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.GuildMessageChannel;
import net.dv8tion.jda.api.entities.User;
import org.apache.commons.collections4.map.PassiveExpiringMap;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class NationUpdateProcessor {
    // TODO update war rooms

    public static void updateBlockades() {
        long now = System.currentTimeMillis();

        Map<Integer, DBWar> wars = Locutus.imp().getWarDb().getWarsSince(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(5));

        List<DBAttack> attacks = Locutus.imp().getWarDb().getAttacks(now - TimeUnit.DAYS.toMillis(5));

        Collections.sort(attacks, Comparator.comparingLong(o -> o.epoch));

        Map<Integer, Map<Integer, Integer>> blockadedByNationByWar = new HashMap<>(); // map of nations getting blockaded
        Map<Integer, Map<Integer, Integer>> blockadingByNationByWar = new HashMap<>(); // map of nations blockading

        for (DBAttack attack : attacks) {
            if (attack.attack_type != AttackType.NAVAL) continue;

            DBNation defender = DBNation.byId(attack.defender_nation_id);
            if (defender == null) continue;

            if (attack.success == 3) {
                Map<Integer, Integer> defenderBlockades = blockadingByNationByWar.get(attack.defender_nation_id);
                if (defenderBlockades != null && !defenderBlockades.isEmpty()) {
                    for (Map.Entry<Integer, Integer> entry : defenderBlockades.entrySet()) {
                        int blockaded = entry.getKey();
                        int warId = entry.getValue();
                        blockadedByNationByWar.getOrDefault(blockaded, Collections.emptyMap()).remove(attack.defender_nation_id);
                    }
                    defenderBlockades.clear();
                }

                DBWar war = wars.get(attack.war_id);
                // Only if war is active
                if (war != null && (war.status == WarStatus.ACTIVE || war.status == WarStatus.DEFENDER_OFFERED_PEACE || war.status == WarStatus.ATTACKER_OFFERED_PEACE)) {
                    blockadedByNationByWar.computeIfAbsent(attack.defender_nation_id, f -> new HashMap<>()).put(attack.attacker_nation_id, attack.war_id);
                    blockadingByNationByWar.computeIfAbsent(attack.attacker_nation_id, f -> new HashMap<>()).put(attack.defender_nation_id, attack.war_id);
                }
            }
            if (attack.success >= 2) {
                blockadedByNationByWar.getOrDefault(attack.attacker_nation_id, Collections.emptyMap()).remove(attack.defender_nation_id);
                blockadingByNationByWar.getOrDefault(attack.defender_nation_id, Collections.emptyMap()).remove(attack.attacker_nation_id);
            }
        }

        Set<Integer> nationIds = new HashSet<>();
        nationIds.addAll(blockadedByNationByWar.keySet());
        nationIds.addAll(blockadingByNationByWar.keySet());

        // Remove if nation is deleted
        for (Integer nationId : nationIds) {
            DBNation nation = DBNation.byId(nationId);
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

                new NationUnblockadedEvent(blockaded, blockader, blockadedByNationByWar.getOrDefault(blockaded, Collections.emptyMap())).post();

                Locutus.imp().getWarDb().deleteBlockaded(blockaded, blockader);
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

    private static Map<Integer, Integer> ACTIVITY_ALERTS = new PassiveExpiringMap<Integer, Integer>(120, TimeUnit.MINUTES);

    @Subscribe
    public void onTurnChange(TurnChangeEvent event) {
        Map<Integer, Integer> membersByAA = new HashMap<>(); // only <7d non vm nations
        Map<Integer, Integer> activeMembersByAA = new HashMap<>();
        Map<Integer, Double> averageMilitarization = new HashMap<>();
        for (Map.Entry<Integer, DBNation> entry : Locutus.imp().getNationDB().getNations().entrySet()) {
            DBNation nation = entry.getValue();
            if (nation.getActive_m() > 7200 || nation.getPosition() <= Rank.APPLICANT.id || nation.getVm_turns() > 0) continue;
            int aaId = nation.getAlliance_id();
            membersByAA.put(aaId, membersByAA.getOrDefault(aaId, 0) + 1);
            boolean active = nation.getActive_m() < 15;
            if (!active && nation.getActive_m() < 1440 && nation.getVm_turns() == 0 && nation.getPosition() > 1) {
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
                DBNation previous = DBNation.byId(nation.getNation_id());
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
                    if (active < required) continue;
                    if (avgMMR < 0.5 && members < required * 2) continue;

                    Integer previous = ACTIVITY_ALERTS.get(aaId);
                    if (previous != null && previous + 2 >= active) continue;
                    ACTIVITY_ALERTS.put(aaId, active);

                    DBAlliance alliance = Locutus.imp().getNationDB().getOrCreateAlliance(aaId);
                    String title = alliance.getName() + " is " + MathMan.format(100d * active / members) + "% online";
                    StringBuilder body = new StringBuilder();
                    body.append(alliance.getMarkdownUrl()).append("\n");
                    body.append("Members: " + members).append("\n");
                    body.append("Online: " + active).append("\n");
                    body.append("Avg Military: " + MathMan.format(100 * avgMMR) + "%").append("\n");

                    AlertUtil.forEachChannel(f -> true, GuildDB.Key.ACTIVITY_ALERTS, new BiConsumer<MessageChannel, GuildDB>() {
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

        if (nation.active_m() < 3) {
            long activeMinute = nation.lastActiveMs();
            // round to nearest minute
            activeMinute = (activeMinute / 60_000) * 60_000;
            Locutus.imp().getNationDB().setSpyActivity(nation.getNation_id(), nation.getProjectBitMask(), nation.getSpies(), activeMinute, nation.getWarPolicy());


            {
                long activeTurn = TimeUtil.getTurn(nation.lastActiveMs());
                Locutus.imp().getNationDB().setActivity(nation.getNation_id(), activeTurn);
            }
        }
    }

    @Subscribe
    public void onNationCreate(NationCreateEvent event) {
        DBNation current = event.getCurrent();

        {
            int rerollId = current.isReroll();
            if (rerollId > 0) {
                ZonedDateTime yesterday = ZonedDateTime.now(ZoneOffset.UTC).minusDays(1);
                String dateStr = DateTimeFormatter.ofPattern("yyyy-MM-dd").format(yesterday);


                String title = "Detected reroll: " + current.getNation();
                StringBuilder body = new StringBuilder(current.getNationUrlMarkup(true));
                if (rerollId != current.getNation_id()) {
                    body.append("\nReroll of: " + PnwUtil.getNationUrl(rerollId));
                }

                AlertUtil.forEachChannel(f -> true, GuildDB.Key.REROLL_ALERT_CHANNEL, new BiConsumer<MessageChannel, GuildDB>() {
                    @Override
                    public void accept(MessageChannel channel, GuildDB guildDB) {
                        DiscordUtil.createEmbedCommand(channel, title, body.toString());
                    }
                });
            }
        }
    }

    @Subscribe
    public void onNationPositionChange(NationChangeRankEvent event) {
        DBNation previous = event.getPrevious();
        DBNation current = event.getCurrent();

        checkOfficerChange(previous, current);
    }

    private void checkOfficerChange(DBNation previous, DBNation current) {
        if (current == null || previous == null || previous.getPosition() < Rank.OFFICER.id || previous.getAlliance_id() == current.getAlliance_id() || current.getActive_m() > 4880) return;
        DBAlliance alliance = previous.getAlliance(false);

        if (alliance.getRank() < 50)
        {
            String title = current.getNation() + " (" + Rank.byId(previous.getPosition()) + ") leaves " + previous.getAllianceName();
            String body = current.toEmbedString(false);
            AlertUtil.forEachChannel(f -> true, GuildDB.Key.ORBIS_OFFICER_LEAVE_ALERTS, new BiConsumer<MessageChannel, GuildDB>() {
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

        AlertUtil.forEachChannel(GuildDB::isValidAlliance, GuildDB.Key.ENEMY_ALERT_CHANNEL, new BiConsumer<MessageChannel, GuildDB>() {
            @Override
            public void accept(MessageChannel channel, GuildDB guildDB) {

                double strength = BlitzGenerator.getAirStrength(current, false);
                Set<Integer> enemies = guildDB.getCoalition(Coalition.ENEMIES);
                if (!enemies.isEmpty() && (enemies.contains(0) || (enemies.contains(current.getAlliance_id())))) {
                    boolean inRange = false;
                    Set<DBNation> nations = guildDB.getMemberDBNations();
                    for (DBNation nation : nations) {
                        if (nation.getScore() >= minScore && nation.getScore() <= maxScore && nation.getActive_m() < 1440 && nation.getOff() < nation.getMaxOff() && BlitzGenerator.getAirStrength(nation, true) > strength * 0.7) {
                            inRange = true;
                        }
                    }
                    if (!inRange) return;

                    String cardInfo = current.toEmbedString(true);
                    DiscordUtil.createEmbedCommand(channel, title, cardInfo);

                    Guild guild = guildDB.getGuild();
                    Role bountyRole = Roles.BEIGE_ALERT.toRole(guild);
                    if (bountyRole == null) {
                        return;
                    }

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
                        if (attacker.getActive_m() > 15 && (status == OnlineStatus.OFFLINE || status == OnlineStatus.INVISIBLE)) continue;
                        if (optOut != null && member.getRoles().contains(optOut)) continue;

                        if (/* attacker.getActive_m() > 1440 || */attacker.getDef() >= 3 || attacker.getVm_turns() != 0 || attacker.isBeige()) continue;
                        if (attacker.getScore() < minScore || attacker.getScore() > maxScore) continue;
                        if (attacker.getOff() > 4) continue;
                        if (attacker.hasUnsetMil() || current.hasUnsetMil()) continue;
                        int planeCap = Buildings.HANGAR.cap() * Buildings.HANGAR.max() * attacker.getCities();
                        if (attacker.getAircraft() < planeCap * 0.8) continue;

                        double attStr = BlitzGenerator.getAirStrength(attacker, true, true);
                        double defStr = BlitzGenerator.getAirStrength(current, false, true);

                        AbstractMap.SimpleEntry<DBNation, Member> entry = new AbstractMap.SimpleEntry<>(attacker, member);

                        if (attacker.getCities() < current.getCities() * 0.66 && (current.getActive_m() < 3000)) continue;
                        if (attacker.getCities() < current.getCities() * 0.70 && (current.getActive_m() < 2440)) continue;
                        if (attacker.getCities() < current.getCities() * 0.75 && (current.getSoldiers() > attacker.getSoldiers() * 0.33 || current.getAircraft() > attacker.getAircraft() * 0.66)) continue;
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
                        channel.sendMessage("^ priority1: " + mentions + "(see pins to opt out)").complete();
                    }
                    if (!priority2.isEmpty()) {
                        String mentions = StringMan.join(priority2.stream().map(e -> e.getValue().getAsMention()).collect(Collectors.toList()), ",");
                        channel.sendMessage("^ priority2: " + mentions + "(see pins to opt out)").complete();
                    }
                    if (!priority3.isEmpty()) {
                        String mentions = StringMan.join(priority3.stream().map(e -> e.getValue().getAsMention()).collect(Collectors.toList()), ",");
                        channel.sendMessage("^ priority3: " + mentions + "(see pins to opt out)").complete();
                    }
                    if (!priority4.isEmpty()) {
                        String mentions = StringMan.join(priority4.stream().map(e -> e.getValue().getAsMention()).collect(Collectors.toList()), ",");
                        channel.sendMessage("^ priority4: " + mentions + "(see pins to opt out)").complete();
                    }
                }
            }
        });
        return true;
    }

    private boolean raidAlert(DBNation defender) {
        if (defender.getDef() > 2) return false;
        if (defender.getActive_m() > 260 * 60 * 24) return false;
        double loot = defender.lootTotal();
        if (loot < 10000000 || defender.isBeige()) {
            return false;
        }
        String msg = defender.toMarkdown(true, true, true, true, false);
        String title = "Target: " + defender.getNation() + ": You can loot: ~$" + MathMan.format(loot);

        String url = "https://politicsandwar.com/nation/war/declare/id=" + defender.getNation_id();
        if (defender.getCities() >= 10) {
            msg += "\n" + url;
        }

        String finalMsg = msg;
        AlertUtil.forEachChannel(f -> f.hasCoalitionPermsOnRoot(Coalition.RAIDPERMS), GuildDB.Key.BEIGE_ALERT_CHANNEL, new BiConsumer<MessageChannel, GuildDB>() {
            @Override
            public void accept(MessageChannel channel, GuildDB guildDB) {
                if (!guildDB.isWhitelisted() || !guildDB.hasCoalitionPermsOnRoot(Coalition.RAIDPERMS)) return;

                if (guildDB.violatesDNR(defender) || (defender.getPosition() > 1 && defender.getActive_m() < 10000)) return;

                Guild guild = guildDB.getGuild();

                Role bountyRole = Roles.BEIGE_ALERT.toRole(guild);
                if (bountyRole == null) return;

                List<Member> members = guild.getMembersWithRoles(bountyRole);
                StringBuilder mentions = new StringBuilder();

                double minScore = defender.getScore() / 1.75;
                double maxScore = defender.getScore() / 0.75;

                Role optOut = Roles.BEIGE_ALERT_OPT_OUT.toRole(guild);
                int membersInRange = 0;

                for (Member member : members) {
                    PNWUser pnwUser = Locutus.imp().getDiscordDB().getUserFromDiscordId(member.getIdLong());
                    if (pnwUser == null) continue;
                    DBNation nation = Locutus.imp().getNationDB().getNation(pnwUser.getNationId());
                    if (nation == null || nation.getOff() >= nation.getMaxOff()) continue;
                    if (nation.getScore() < minScore || nation.getScore() > maxScore) continue;
                    if (optOut != null && member.getRoles().contains(optOut)) continue;
                    if (nation.getOff() > 0 && Locutus.imp().getWarDb().getActiveWarByNation(nation.getNation_id(), defender.getNation_id()) != null) continue;

                    OnlineStatus status = member.getOnlineStatus();
                    if (status == OnlineStatus.OFFLINE || status == OnlineStatus.INVISIBLE) continue;

                    membersInRange++;

                    if ((nation.getAvg_infra() > 1250 && defender.getAlliance_id() != 0 && (defender.getPosition() > 1 || nation.getCities() > 7))
                            || nation.getActive_m() > 2880
//                                        || nation.getSoldiers() < defender.getSoldiers()
                    ) continue;

                    if (optOut != null && member.getRoles().contains(optOut)) continue;

                    long pair = MathMan.pairInt(nation.getNation_id(), defender.getNation_id());
                    if (!pingFlag.containsKey(pair)) {
                        mentions.append(member.getAsMention() + " ");
                    }
                    pingFlag.put(pair, true);
                }
                if (membersInRange > 0) {
                    DiscordUtil.createEmbedCommand(channel, title, finalMsg);
                    if (mentions.length() != 0) {
                        RateLimitUtil.queueWhenFree(channel.sendMessage("^ " + mentions + "(see pins to opt (out)"));
                    }
                }
            }
        });
        return true;
    }

    private void handleOfficerDelete(DBNation previous, DBNation current) {
        if (current != null || previous == null || previous.getPosition() < Rank.OFFICER.id || previous.getActive_m() > 10000) return;
        DBAlliance alliance = previous.getAlliance(false);
        if (alliance.getRank() < 50) {
            String title = previous.getNation() + " (" + Rank.byId(previous.getPosition()) + ") deleted from " + previous.getAllianceName();
            String body = previous.toEmbedString(false);
            AlertUtil.forEachChannel(f -> true, GuildDB.Key.ORBIS_OFFICER_LEAVE_ALERTS, new BiConsumer<MessageChannel, GuildDB>() {
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

        checkOfficerChange(previous, current);

        checkExodus(previous, current);

        Locutus.imp().getNationDB().addRemove(current.getNation_id(), previous.getAlliance_id(), System.currentTimeMillis(), Rank.byId(previous.getPosition()));
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

                if (db.getOrNull(GuildDB.Key.AUTONICK) == GuildDB.AutoNickOption.NATION) {
                    try {
                        db.getAutoRoleTask().autoRole(member, System.out::println);
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

                if (db.getOrNull(GuildDB.Key.AUTONICK) == GuildDB.AutoNickOption.LEADER) {
                    try {
                        db.getAutoRoleTask().autoRole(member, System.out::println);
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
        if (alliance.getRank() > 120) return;

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
            AlertUtil.forEachChannel(f -> true, GuildDB.Key.ORBIS_ALLIANCE_EXODUS_ALERTS, new BiConsumer<MessageChannel, GuildDB>() {
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
        List<Offer> trades = Locutus.imp().getTradeManager().getTradeDb().getOffers(current.getNation_id(), cutoffMs);

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

        for (Offer offer : trades) {
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

            double value = offer.getPpu() > 1 ? offer.getAmount() * offer.getPpu() : Locutus.imp().getTradeManager().getLow(offer.getResource()) * offer.getAmount();
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
                    String channelId = guildDB.getInfo(GuildDB.Key.BANK_ALERT_CHANNEL, false);
                    if (channelId == null) {
                        continue;
                    }
                    Guild guild = guildDB.getGuild();
                    GuildChannel channel = guild.getGuildChannelById(channelId);
                    if (channel == null || !(channel instanceof GuildMessageChannel)) {
                        continue;
                    }
                    DiscordUtil.createEmbedCommand((MessageChannel) channel, title, body.substring(0, Math.min(body.length(), 2000)));
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
            AlertUtil.forEachChannel(f -> true, GuildDB.Key.ORBIS_LEADER_CHANGE_ALERT, new BiConsumer<MessageChannel, GuildDB>() {
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

            String type = "DELETION";
            String body = previous.toEmbedString(false);
            String url = previous.getNationUrl();
            try {
                String html = FileUtil.readStringFromURL(url);
                Document dom = Jsoup.parse(html);
                String alert = PnwUtil.getAlert(dom);
                if (alert.startsWith("This nation was banned")) {
                    alert = alert.replace("Visit your nation, or go to the search page.",  "");
                    type = "BAN";
                    body += "\n" + alert;
                }
            } catch (Throwable ignore) {}

            String finalType = type;
            String finalBody = body;
            AlertUtil.forEachChannel(BankAlerts.class, GuildDB.Key.DELETION_ALERT_CHANNEL, new BiConsumer<MessageChannel, GuildDB>() {
                @Override
                public void accept(MessageChannel channel, GuildDB db) {
                    String title = "Detected " + finalType + ": " + previous.getNation() + " | " + "" + Settings.INSTANCE.PNW_URL() + "/nation/id=" + previous.getNation_id() + " | " + previous.getAllianceName();
                    AlertUtil.displayChannel(title, finalBody, channel.getIdLong());
                }
            });
        }
    }
}