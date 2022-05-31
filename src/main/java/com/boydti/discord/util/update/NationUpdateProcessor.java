package com.boydti.discord.util.update;

import com.boydti.discord.Locutus;
import com.boydti.discord.db.GuildHandler;
import com.boydti.discord.commands.war.WarCategory;
import com.boydti.discord.commands.trade.subbank.BankAlerts;
import com.boydti.discord.config.Settings;
import com.boydti.discord.db.GuildDB;
import com.boydti.discord.db.NationDB;
import com.boydti.discord.db.entities.Coalition;
import com.boydti.discord.db.entities.DBWar;
import com.boydti.discord.db.entities.Transaction2;
import com.boydti.discord.db.entities.WarStatus;
import com.boydti.discord.event.*;
import com.boydti.discord.pnw.Alliance;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.pnw.PNWUser;
import com.boydti.discord.util.scheduler.CaughtRunnable;
import com.boydti.discord.user.Roles;
import com.boydti.discord.util.*;
import com.boydti.discord.util.discord.DiscordUtil;
import com.boydti.discord.util.battle.BlitzGenerator;
import com.boydti.discord.util.trade.Offer;
import com.google.gson.JsonObject;
import com.boydti.discord.apiv1.domains.subdomains.DBAttack;
import com.boydti.discord.apiv1.enums.AttackType;
import com.boydti.discord.apiv1.enums.DomesticPolicy;
import com.boydti.discord.apiv1.enums.MilitaryUnit;
import com.boydti.discord.apiv1.enums.Rank;
import com.boydti.discord.apiv1.enums.ResourceType;
import com.boydti.discord.apiv1.enums.city.JavaCity;
import com.boydti.discord.apiv1.enums.city.building.Buildings;
import com.boydti.discord.apiv1.enums.city.project.Projects;
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
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.boydti.discord.db.GuildDB.Key.BANK_ALERT_CHANNEL;
import static com.boydti.discord.db.GuildDB.Key.ESCALATION_ALERTS;

public class NationUpdateProcessor implements Runnable {
    public static void updateBlockades() {
        long now = System.currentTimeMillis();

        Map<Integer, DBWar> wars = Locutus.imp().getWarDb().getActiveWars();

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


    @Override
    public void run() {
        synchronized (checkBankQueue) {
            while (!checkBankBuffer.isEmpty()) {
                checkBankQueue.add(checkBankBuffer.poll());
            }
            int i = 0;
            while (!checkBankQueue.isEmpty()) {
                Iterator<Integer> iter = checkBankQueue.iterator();
                if (iter.hasNext()) {
                    Integer nationId = iter.next();
                    iter.remove();

                    try {
                        DBNation nation = Locutus.imp().getNationDB().getNation(nationId);
                        if (nation == null) continue;
                        if (nation.getVm_turns() > 0) continue;
                        if (nation.getScore() < 500 || nation.getCities() < 10) continue;
                        if (nation.isBeige() && nation.getBeigeTurns() > 12) continue;
                        if (nation.isBlockaded()) continue;

                        if (nation.getActive_m() < 15) {
                            queueBankUpdate(nation);
                            continue;
                        }

                        nation.getTransactions(1);
                    } catch (Throwable e) {
                        e.printStackTrace();
//                        checkBankQueue.add(nationId);
                    }
                }
            }
        }
    }

    private static Set<Integer> checkBankQueue = new LinkedHashSet<>();
    private static Queue<Integer> checkBankBuffer = new ConcurrentLinkedQueue<>();

    public static void queueBankUpdate(DBNation nation) {
        checkBankBuffer.add(nation.getNation_id());
    }

    private static Map<Integer, DBNation> TURN_CACHE;

    public static DBNation getPrevious(DBNation current) {
        if (TURN_CACHE == null) {
            return current;
        }
        return TURN_CACHE.getOrDefault(current.getNation_id(), current);
    }

    private static Map<Integer, Integer> ACTIVITY_ALERTS = new PassiveExpiringMap<Integer, Integer>(120, TimeUnit.MINUTES);

    public static void checkBlitzActivity(Map<Integer, DBNation> update) {
        Map<Integer, Integer> membersByAA = new HashMap<>(); // only <7d non vm nations
        Map<Integer, Integer> activeMembersByAA = new HashMap<>();
        Map<Integer, Double> averageMilitarization = new HashMap<>();
        for (Map.Entry<Integer, DBNation> entry : update.entrySet()) {
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

                    Alliance alliance = new Alliance(aaId);
                    String title = alliance.getName() + " is " + MathMan.format(100d * active / members) + "% online";
                    StringBuilder body = new StringBuilder();
                    body.append(alliance.getMarkdownUrl()).append("\n");
                    body.append("Members: " + members).append("\n");
                    body.append("Online: " + active).append("\n");
                    body.append("Avg Military: " + MathMan.format(100 * avgMMR) + "%").append("\n");

                    AlertUtil.forEachChannel(f -> true, ESCALATION_ALERTS, new BiConsumer<MessageChannel, GuildDB>() {
                        @Override
                        public void accept(MessageChannel channel, GuildDB guildDB) {
                            DiscordUtil.createEmbedCommand(channel, title, body.toString());
                        }
                    });

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

    public static void process(Map<Integer, DBNation> cache, Map<Integer, DBNation> update, boolean initial, UpdateType type, long timestamp) {
        if (type == UpdateType.INITIAL) {
            checkBlitzActivity(update);
        }
        if (initial) {
            long timeMinute = (timestamp / 60000) * 60000;
            TURN_CACHE = new HashMap<>(cache);
            Locutus.imp().getNationDB().setSpyActivity(0, 0L, 0, timeMinute, 0);

            checkAACreation(cache, update);

            for (Map.Entry<Integer, DBNation> entry : update.entrySet()) {
                DBNation nation = entry.getValue();
                int active = nation.getActive_m();
                if (active == 0 && nation.getVm_turns() == 0 && nation.getCities() > 5) {
                    Integer spies = nation.getSpies();
                    if (spies == null) continue;
                    Locutus.imp().getNationDB().setSpyActivity(nation.getNation_id(), nation.getProjectBitMask(), spies, timeMinute, 0);
                }
            }
        }

        if (!update.isEmpty()) {
            LinkedList<Runnable> taskList = new LinkedList<>();
            try {
                for (Map.Entry<Integer, DBNation> entry : update.entrySet()) {
                    DBNation currentNation = entry.getValue();
                    DBNation previous = cache.get(entry.getKey());
                    if (previous != null) {
                        currentNation.fillBlanks(previous);
                    }
                    process(previous, currentNation, taskList, timestamp, type);


                    GuildDB guild1 = previous != null ? Locutus.imp().getGuildDBByAA(previous.getAlliance_id()) : null;
                    GuildDB guild2 = currentNation != null ? Locutus.imp().getGuildDBByAA(currentNation.getAlliance_id()) : null;
                    try {
                        if (guild1 != null && guild1 != guild2) {
                            guild1.getHandler().onMemberNationUpdate(previous, currentNation, initial, type, timestamp);
                        }
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }
                    try {
                        if (guild2 != null) {
                            guild2.getHandler().onMemberNationUpdate(previous, currentNation, initial, type, timestamp);
                        }
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }
                }
            } catch (Throwable e2) {
                e2.printStackTrace();
            }

            Locutus.imp().getNationDB().updateNations(update);

            if (!taskList.isEmpty()) {
                for (Runnable task : taskList) {
                    try {
                        task.run();
                    } catch (Throwable e) {
                        e.printStackTrace();
                        AlertUtil.displayChannel("Error", e.getMessage(), Settings.INSTANCE.DISCORD.CHANNEL.ADMIN_ALERTS);
                    }
                }
            }
        }
    }

    private static void checkAACreation(Map<Integer, DBNation> cache, Map<Integer, DBNation> update) {
        if (cache.isEmpty()) return;

        Map<Integer, String> previousAAs = new HashMap<>();
        for (DBNation nation : cache.values()) previousAAs.put(nation.getAlliance_id(), nation.getAlliance());

        if (previousAAs.isEmpty()) return;

        Set<Integer> createdAlliances = new HashSet<>();
        for (DBNation nation : update.values()) {
            if (nation.getVm_turns() != 0) continue;
            if (!previousAAs.containsKey(nation.getAlliance_id())) {
                createdAlliances.add(nation.getAlliance_id());
            }
        }

        if (createdAlliances.isEmpty()) return;

        for (Integer aaId : createdAlliances) {
            List<DBNation> members = new ArrayList<>();
            String name = null;
            for (DBNation value : update.values()) {
                if (value.getAlliance_id() == aaId) {
                    name = value.getAlliance();
                    members.add(value);
                }
            }
            if (name == null) {
                PnwUtil.getName(aaId, true);
            }
            Locutus.post(new AllianceCreateEvent(name, aaId, members, previousAAs));
        }

    }

    public enum UpdateType {
        INITIAL,
        DATE,
        CHANGE,
        MILITARY,
        DONE,
        ;
    }

    public static void runGuildNationTasks(long lastTurn, long currentTurn) {
        Consumer<GuildDB> task = new Consumer<>() {
            Map<Integer, List<DBNation>> byAA = null;
            @Override
            public void accept(GuildDB db) {
                if (byAA == null) {
                    synchronized (this) {
                        if (byAA == null) {
                            byAA = Locutus.imp().getNationDB().getNationsByAlliance(false, false, false, false);
                        }
                    }
                }
                int aaId = db.getOrNull(GuildDB.Key.ALLIANCE_ID, false);
                db.getHandler().processTurnUpdate(lastTurn, currentTurn, byAA.getOrDefault(aaId, new ArrayList<>()));
            }
        };

        for (Map.Entry<Long, GuildDB> entry : Locutus.imp().getGuildDatabases().entrySet()) {
            GuildDB db = entry.getValue();
            Integer aaId = db.getOrNull(GuildDB.Key.ALLIANCE_ID, false);
            if (aaId == null || aaId == 0) continue;
            // Only run if custom guild handler
            if (db.getHandler().getClass() != GuildHandler.class) {
                Locutus.imp().getExecutor().submit(() -> task.accept(db));
            }
        }
    }

    private static Map<Integer, Integer> projectUpdateCache = new HashMap<>();
    private static Set<Integer> sentMail = new HashSet<>();
    private static Set<Long> blacklistedGuilds = new HashSet<>();

    public static void process(DBNation previous, DBNation current, List<Runnable> taskList, long start, UpdateType type) {
        if (previous == current) {
            return;
        }
        if (previous == null && current != null && type == UpdateType.INITIAL) {
            if (!sentMail.contains(current.getNation_id())) {
                sentMail.add(current.getNation_id());
                current.setDate(start);
                current.setBeigeTimer(TimeUtil.getTurn() + 14 * 12);

                ArrayList<GuildDB> databases = new ArrayList<>(Locutus.imp().getGuildDatabases().values());

                for (GuildDB db : databases) {
                    if (db.isDelegateServer()) continue;
                    GuildMessageChannel output = db.getOrNull(GuildDB.Key.RECRUIT_MESSAGE_OUTPUT, false);
                    if (output == null) continue;

                    Integer aaId = db.getOrNull(GuildDB.Key.ALLIANCE_ID, false);
                    if (aaId == null) continue;

                    List<DBNation> members = Locutus.imp().getNationDB().getNations(Collections.singleton(aaId));
                    members.removeIf(f -> f.getPosition() < Rank.LEADER.id);
                    members.removeIf(f -> f.getActive_m() > 2880);
                    if (members.isEmpty()) continue;

                    if (!GuildDB.Key.RECRUIT_MESSAGE_OUTPUT.allowed(db)) {
                        try {
                            RateLimitUtil.queue(output.sendMessage("Only whitelisted or top 25 alliances (active member score) are (eligable"));
                        } catch (Throwable e) {
                            db.deleteInfo(GuildDB.Key.RECRUIT_MESSAGE_OUTPUT);
                            e.printStackTrace();
                        }
                        continue;
                    }

                    Long delay = db.getOrNull(GuildDB.Key.RECRUIT_MESSAGE_DELAY);
                    Runnable task = new CaughtRunnable() {
                        @Override
                        public void runUnsafe() {
                            try {
                                JsonObject response = db.sendRecruitMessage(current);
                                RateLimitUtil.queueMessage(output, (current.getNation() + ": " + response), true);
                            } catch (Throwable e) {
                                try {
                                    if (blacklistedGuilds.contains(db.getIdLong())) {
                                        blacklistedGuilds.add(db.getIdLong());
                                        RateLimitUtil.queueMessage(output, (current.getNation() + " (error): " + e.getMessage()), true);
                                    }
                                } catch (Throwable e2) {
                                    db.deleteInfo(GuildDB.Key.RECRUIT_MESSAGE_OUTPUT);
                                }
                            }
                        }
                    };
                    if (delay == null || delay <= 60) task.run();
                    else {
                        Locutus.imp().getCommandManager().getExecutor().schedule(task, delay, TimeUnit.SECONDS);
                    }
                }
            }
        }

        checkBuyCity(previous, current);
        checkOfficerLeave(previous, current);
        checkOfficerDelete(previous, current);
        checkExodus(previous, current);
        checkKick(previous, current);

        if (current != null && !current.hasUnsetMil() && type == UpdateType.DONE && current.getActive_m() < 60 && current.getActive_m() > 15) {
            if (Math.abs(Math.round(current.estimateScore(true)) - Math.round(current.getScore())) > 19) {
                if (projectUpdateCache.getOrDefault(current.getNation_id(), 0) == 1) {
                    projectUpdateCache.put(current.getNation_id(), 2);
                    current.updateProjects();
                    boolean incorrect = Math.abs(Math.round(current.estimateScore(true)) - Math.round(current.getScore())) > 19;
                } else {
                    projectUpdateCache.put(current.getNation_id(), 1);
                }
            }
        }
        if (current != null && Settings.INSTANCE.LEGACY_SETTINGS.ATTACKER_DESKTOP_ALERTS.contains(current.getNation_id()) && (current.getSoldiers() > previous.getSoldiers() || current.getShips() > previous.getShips())) {
            AlertUtil.openDesktop("https://politicsandwar.com/nation/war/");
        }

        boolean externalTaskList = taskList != null;
        int sizeStart = taskList != null ? taskList.size() : 0;

        if (previous == null && type == UpdateType.INITIAL) {
            new NationChangePositionEvent(previous, current).post();
            taskList = init(taskList, () -> reroll(previous, current));
            if (current.getAlliance_id() == 0 && current.getVm_turns() == 0) {
//                taskList = init(taskList, () -> mail(previous, current));
            }
        } else {
            current.getDate();

            if (previous.isBeige() && !current.isBeige()) {
                current.setBeigeTimer(0L);
            }
            if (current.getCities() > previous.getCities()) {
                current.setCityTimer(TimeUtil.getTurn() + 120);
                boolean up = current.hasProject(Projects.URBAN_PLANNING);
                boolean aup = current.hasProject(Projects.ADVANCED_URBAN_PLANNING);
                boolean manifest = current.getDomesticPolicy() == DomesticPolicy.MANIFEST_DESTINY || previous.getDomesticPolicy() == DomesticPolicy.MANIFEST_DESTINY;
                double cost = 0;
                for (int i = previous.getCities(); i < current.getCities(); i++) {
                    cost += PnwUtil.nextCityCost(i, manifest, up, aup);
                    current.addExpense(Collections.singletonMap(ResourceType.MONEY, cost));
                }
                new NationBuyCityEvent(previous, current).post();
            }

            if (!current.getNation().equalsIgnoreCase(previous.getNation())
            || !current.getLeader().equalsIgnoreCase(previous.getLeader())) {
                PNWUser pnwUser = DiscordUtil.getUser(current);
                if (pnwUser != null) {
                    User user = pnwUser.getUser();
                    if (user != null) {
                        for (Guild guild : user.getMutualGuilds()) {
                            GuildDB db = Locutus.imp().getGuildDB(guild);
                            if (db != null) {
                                Member member = guild.getMember(user);
                                if (member != null) {
                                    try {
                                        db.getAutoRoleTask().autoRole(member, System.out::println);
                                    } catch (Throwable e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (!current.hasUnsetMil() && !previous.hasUnsetMil()) {
                for (MilitaryUnit unit : MilitaryUnit.values) {
                    switch (unit) {
                        case SOLDIER:
                        case TANK:
                        case AIRCRAFT:
                        case SHIP:
                        case MISSILE:
                        case NUKE:
                            int currentVal = current.getUnits(unit);
                            int previousVal = previous.getUnits(unit);
                            if (currentVal != previousVal) {
                                Locutus.imp().getNationDB().setMilChange(start, current.getNation_id(), unit, previousVal, currentVal);
                            }
                    }
                }
            }

            processMemberChange(previous, current);
            processLeaderChange(previous, current);

            if (previous.getActive_m() > 5 && current.getActive_m() <= 5) {
                new NationActiveEvent5(previous, current).post();
            }

            if (previous.getActive_m() > 120 && current.getActive_m() <= 5) {
                queueBankUpdate(current);
                new NationActiveEvent120(previous, current).post();
            }

            if (current.getActive_m() < previous.getActive_m() || current.getActive_m() < 5) {
                NationDB db = Locutus.imp().getNationDB();
                long activeTimestamp = start - TimeUnit.MINUTES.toMillis(current.getActive_m());

                long currentTurn = TimeUtil.getTurn();
                long activeTurn = TimeUtil.getTurn(activeTimestamp);
                Locutus.imp().getNationDB().setActivity(current.getNation_id(), activeTurn);
            }

            if (current.getVm_turns() == 0 && !current.isBeige()) {
                boolean leftVMBeige = false;
                String title;
                if (previous.isBeige() && !current.isBeige() && current.getActive_m() < 10000) {
                    title = "Left Beige: " + current.getNation() + " | " + current.getAlliance();
                    leftVMBeige = true;
                } else if (previous.getVm_turns() > 0 && current.getVm_turns() == 0) {
                    title = "Left VM: " + current.getNation() + " | " + current.getAlliance();
                    leftVMBeige = true;
                } else if (previous.getActive_m() <= 10080 && current.getActive_m() > 10080) {
                    title = "Inactive: " + current.getNation() + " | " + current.getAlliance();
                    leftVMBeige = true;
                } else if (previous.getAlliance_id() != 0 && current.getAlliance_id() == 0 && current.getActive_m() > 1440) {
                    title = "Removed: " + current.getNation() + " | " + current.getAlliance();
                    leftVMBeige = true;
                } else if (previous.getDef() >= 3 && current.getDef() < 3 && !current.isBeige()) {
                    title = "Unslotted: " + current.getNation() + " | " + current.getAlliance();
//                    leftVMBeige = true;
                } else {
                    title = null;
                }
                if (leftVMBeige && current.getActive_m() > 7200 && current.getVm_turns() == 0 && current.getDef() < 3) {
                    RaidUpdateProcessor processor = Locutus.imp().getRaidProcessor();
                    if (processor != null) processor.checkSlot(current.getNation_id());
                } else if (current.getDef() < 3 && title != null) {

                    double minScore = current.getScore() / 1.75;
                    double maxScore = current.getScore() / 0.75;

                    AlertUtil.forEachChannel(f -> f.isWhitelisted(), GuildDB.Key.ENEMY_ALERT_CHANNEL, new BiConsumer<MessageChannel, GuildDB>() {
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
                                    if (attacker.getOff() == 0 && attacker.getDef() == 0) {
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
                }
            }
            if (previous.getAlliance_id() != current.getAlliance_id() || previous.getPosition() != current.getPosition()) {
                try {
                    Locutus.imp().autoRole(current);
                    Locutus.imp().getNationDB().addRemove(current.getNation_id(), previous.getAlliance_id(), System.currentTimeMillis(), Rank.byId(previous.getPosition()));
                    new NationChangePositionEvent(previous, current).post();
                } catch (Throwable ignore) {
                }
            }
            if (previous.getCities() > current.getCities()) {
                current.setDate(null);
                taskList = init(taskList, () -> reroll(previous, current));
            } else if (previous.getDate() != null && current.getDate() != null) {
                if (Math.abs(previous.getDate() - current.getDate()) > TimeUnit.DAYS.toMillis(1)) {
                    taskList = init(taskList, () -> reroll(previous, current));
                }
            }

            if (current.getVm_turns() != 0 && previous.getVm_turns() == 0) {
                processVMTransfers(previous, current);
            }
        }

            int sizeEnd = taskList != null ? taskList.size() : 0;

        if (!externalTaskList && sizeStart != sizeEnd) {
            for (Runnable task : taskList) {
                try {
                    task.run();
                } catch (Throwable e) {
                    e.printStackTrace();
                    AlertUtil.displayChannel("Error", e.getMessage(), Settings.INSTANCE.DISCORD.CHANNEL.ADMIN_ALERTS);
                }
            }
        }
    }

    private static void checkBuyCity(DBNation previous, DBNation current) {
        if (previous == null || current == null || current.getCities() == previous.getCities()) return;

        if (current.getPosition() > 1) {
            GuildDB db = current.getGuildDB();
            if (db != null) {
                db.getHandler().onBuySellCity(previous, current);
            }
        }
    }

    private static void checkKick(DBNation previous, DBNation current) {
        if (previous == null || current == null) return;
        if (current.getActive_m() < 10000 && current.getActive_m() > 10 && current.getAlliance_id() == 0 && previous.getAlliance_id() != 0) {
            new NationKickedFromAllianceEvent(previous, current).post();
        }
    }

    private static void checkOfficerLeave(DBNation previous, DBNation current) {
        if (current == null || previous == null || previous.getPosition() < Rank.OFFICER.id || previous.getAlliance_id() == current.getAlliance_id() || current.getActive_m() > 4880) return;
        Alliance alliance = new Alliance(previous.getAlliance_id());

        if (alliance.getRank() < 50) {
            String title = current.getNation() + " (" + Rank.byId(previous.getPosition()) + ") leaves " + previous.getAlliance();
            String body = current.toEmbedString(false);
            AlertUtil.forEachChannel(f -> true, GuildDB.Key.ORBIS_OFFICER_LEAVE_ALERTS, new BiConsumer<MessageChannel, GuildDB>() {
                @Override
                public void accept(MessageChannel channel, GuildDB guildDB) {
                    DiscordUtil.createEmbedCommand(channel, title, body);
                }
            });
        }
    }

    private static void checkOfficerDelete(DBNation previous, DBNation current) {
        if (current != null || previous == null || previous.getPosition() < Rank.OFFICER.id || current.getActive_m() > 10000) return;
        Alliance alliance = new Alliance(previous.getAlliance_id());
        if (alliance.getRank() < 50) {
            String title = previous.getNation() + " (" + Rank.byId(previous.getPosition()) + ") leaves " + previous.getAlliance();
            String body = previous.toEmbedString(false);
            AlertUtil.forEachChannel(f -> true, GuildDB.Key.ORBIS_OFFICER_LEAVE_ALERTS, new BiConsumer<MessageChannel, GuildDB>() {
                @Override
                public void accept(MessageChannel channel, GuildDB guildDB) {
                    DiscordUtil.createEmbedCommand(channel, title, body);
                }
            });
        }
    }

    private static void checkExodus(DBNation previous, DBNation current) {
        if (current == null || previous == null || previous.getAlliance_id() == current.getAlliance_id() || current.getActive_m() > 4880 || previous.getAlliance_id() == 0 || previous.getPosition() == 1) return;
        Alliance alliance = new Alliance(previous.getAlliance_id());
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
            if (current.getDate() != null) {
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
            String title = type + " | " + current.getNation() + " | " + current.getAlliance();

            try {
                for (Map.Entry<Long, GuildDB> entry : Locutus.imp().getGuildDatabases().entrySet()) {
                    GuildDB guildDB = entry.getValue();
                    Integer perm = guildDB.getPermission(BankAlerts.class);
                    if (perm == null || perm <= 0) continue;
                    String channelId = guildDB.getInfo(BANK_ALERT_CHANNEL, false);
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

    private static List<Runnable> init(List<Runnable> list, Runnable add) {
        if (list == null) {
            list = new LinkedList<>();
        }
        list.add(add);
        return list;
    }

    private static void reroll(DBNation previous, DBNation current) {
        if (previous != null) previous.setDate(null);
        else if (!current.isReroll() || current.getVm_turns() != 0 || current.getActive_m() > 10000) return;
        if (previous != null && (previous.getVm_turns() != 0 || current.getActive_m() > 10000)) return;

        current.setDate(null);

        String url = "" + Settings.INSTANCE.PNW_URL() + "/index.php?id=132&name=%s&type=nation&date=%s&submit=Go";
        ZonedDateTime yesterday = ZonedDateTime.now(ZoneOffset.UTC).minusDays(1);
        String dateStr = DateTimeFormatter.ofPattern("yyyy-MM-dd").format(yesterday);
        if (previous != null) url = String.format(url, previous.getNation(), dateStr);
        else url = String.format(url, current.getNation(), dateStr);

        String title = "Detected reroll: " + current.getNation();
        String body = "" + Settings.INSTANCE.PNW_URL() + "/nation/id=" + current.getNation_id() + "\n" +
                "https://politicsandwar.com/index.php?id=178&nation_id=" + current.getNation_id() + "\n" +
                url;
        AlertUtil.forEachChannel(f -> true, GuildDB.Key.REROLL_ALERT_CHANNEL, new BiConsumer<MessageChannel, GuildDB>() {
            @Override
            public void accept(MessageChannel channel, GuildDB guildDB) {
                DiscordUtil.createEmbedCommand(channel, title, body);
            }
        });
    }

    private static void processLeaderChange(DBNation previous, DBNation current) {
        if (previous == null || current == null) return;
        if (previous.getPosition() == current.getPosition() && previous.getAlliance_id() == current.getAlliance_id()) return;
        if (previous.getPosition() != Rank.LEADER.id && current.getPosition() != Rank.LEADER.id) return;

        Alliance aa1 = new Alliance(previous.getAlliance_id());
        Alliance aa2 = new Alliance(current.getAlliance_id());

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
                    String title = "Detected " + finalType + ": " + previous.getNation() + " | " + "" + Settings.INSTANCE.PNW_URL() + "/nation/id=" + previous.getNation_id() + " | " + previous.getAlliance();
                    AlertUtil.displayChannel(title, finalBody, channel.getIdLong());
                }
            });
        }
    }

    public static void processMemberChange(DBNation previous, DBNation current) {
        if (previous == null) return;

        if (current == null || current.getAlliance_id() == 0) return;

        if (current.getPosition() <= 1) return;

        GuildDB guildDb = Locutus.imp().getGuildDBByAA(current.getAlliance_id());
        if (guildDb == null || !guildDb.isAllyOfRoot()) return;

        Guild guild = guildDb.getGuild();
        if (guild == null) return;

        WarCategory warChannel = guildDb.getWarChannel();
        if (warChannel != null) {
            warChannel.update(previous, current);
        }

        processMemberInfra(previous, current);
    }

    public static void processMemberInfra(DBNation previous, DBNation current) {
        int threshold = current.getCities() <= 5 ? 1700 : 1700;
        if (previous.getAvg_infra() <= threshold && current.getAvg_infra() > threshold && (current.getCities() < 8 || current.getOff() == 5)) {
            AlertUtil.auditAlert(current, AuditType.HIGH_INFRA, (f) -> AuditType.HIGH_INFRA.message
            );
        }
    }

    public static void processCity(JavaCity previous, JavaCity current, int cityId, DBNation nation) {
        if (nation.getAlliance_id() == 0 || nation.getPosition() <= 1) return;
        if (previous == null || current == null) return;

        if (nation.getCities() < 10) {
            if (current.get(Buildings.FACTORY) > 0 && previous.get(Buildings.FACTORY) == 0) {
                String msg = AuditType.RAIDING_W_TANKS.message;
                AlertUtil.auditAlert(nation, AuditType.RAIDING_W_TANKS, (f) -> " ```" + msg + "```");
            }
            if (current.get(Buildings.FARM) > previous.get(Buildings.FARM) && current.getInfra() <= 2000) {
                String msg = AuditType.UNPROFITABLE_FARMS.message;
                AlertUtil.auditAlert(nation, AuditType.UNPROFITABLE_FARMS, (f) -> " ```" + msg + "```");
            }
            if (current.get(Buildings.WIND_POWER) > previous.get(Buildings.WIND_POWER) && current.get(Buildings.WIND_POWER) > 1) {
                String msg = AuditType.WIND_POWER.message;
                AlertUtil.auditAlert(nation, AuditType.WIND_POWER, (f) -> " ```" + msg + "```");
            }
//                    if (current.getInfra() > previous.getInfra() && nation.getOff() > 0) {
//                        if (current.getInfra() > 1000 && nation.getCities() < 8 && nation.getCities() > 4) {
//                            msgs.add("As you are raiding (and at risk of counter attacks), it is recommended not to build past 1000 infra in each city (as it will otherwise become costly to lose)");
//                        }
//                    }
            if (current.getInfra() <= 1000 && nation.getCities() < 8 && current.get(Buildings.HANGAR) > previous.get(Buildings.HANGAR)) {
//                        msgs.add("It is not recommended to raid with planes (if you have low infra), as they are costly and increase your losses from counters.");
            }
        }
    }
}