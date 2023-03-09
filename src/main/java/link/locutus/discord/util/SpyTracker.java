package link.locutus.discord.util;

import com.politicsandwar.graphql.model.Nation;
import com.politicsandwar.graphql.model.NationResponseProjection;
import com.politicsandwar.graphql.model.NationsQueryRequest;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.core.ApiKeyPool;
import link.locutus.discord.apiv1.domains.subdomains.DBAttack;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.apiv3.PoliticsAndWarV3;
import link.locutus.discord.apiv3.enums.AlliancePermission;
import link.locutus.discord.apiv3.subscription.PnwPusherEvent;
import link.locutus.discord.apiv3.subscription.PnwPusherFilter;
import link.locutus.discord.apiv3.subscription.PnwPusherHandler;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.util.FileUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.SpyCount;
import link.locutus.discord.util.TimeUtil;
import net.dv8tion.jda.api.entities.MessageChannel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class SpyTracker {
    public SpyTracker() {

    }

    public void loadCasualties(Integer allianceId) {
        System.out.println("Loading casualties " + allianceId);
        PoliticsAndWarV3 api = Locutus.imp().getV3();
        if (allianceId != null) {
            api = DBAlliance.getOrCreate(allianceId).getApi(false, AlliancePermission.SEE_SPIES);
            if (api == null) return;
        }
        List<Nation> nations = api.fetchNations(new Consumer<NationsQueryRequest>() {
            @Override
            public void accept(NationsQueryRequest f) {
                f.setVmode(false);
                if (allianceId != null) {
                    f.setAlliance_id(List.of(allianceId));
                }
            }
        }, new Consumer<NationResponseProjection>() {
            @Override
            public void accept(NationResponseProjection f) {
                f.id();
                f.score();

                f.soldiers();
                f.tanks();
                f.aircraft();
                f.ships();
                f.missiles();
                f.aircraft();
                f.spies();

                f.last_active();
                f.soldier_kills();
                f.soldier_casualties();
                f.tank_kills();
                f.tank_casualties();
                f.aircraft_kills();
                f.aircraft_casualties();
                f.ship_kills();
                f.ship_casualties();
                f.missile_kills();
                f.missile_casualties();
                f.nuke_kills();
                f.nuke_casualties();
                if (allianceId != null) {
                    f.spy_casualties();
                    f.spy_kills();
                }
            }
        });
        System.out.println(" - Fetched nations " + nations.size());
        try {
            updateCasualties(nations);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        System.out.println(" - updated casualties " + killTracker.size() + " | " + casualtyTracker.size());
    }

    private final Map<Integer, Map<MilitaryUnit, Integer>> casualtyTracker = new ConcurrentHashMap<>();
    private final Map<Integer, Map<MilitaryUnit, Integer>> unitTotalTracker = new ConcurrentHashMap<>();
    private final Map<Integer, Map<MilitaryUnit, Integer>> killTracker = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<SpyActivity> queue = new ConcurrentLinkedQueue<>();

    private AtomicLong lastRun = new AtomicLong();

    public void updateCasualties(List<Nation> nations) throws IOException {
        System.out.println("Called update casualties " + nations.size());
        long timestamp = System.currentTimeMillis();
        for (Nation nation : nations) {
            updateCasualties(nation, timestamp);
        }
        System.out.println(" queue1 " + queue.size());
        checkActive();
        System.out.println(" queue2 " + queue.size());
        if (queue.isEmpty()) return;

        long now = System.currentTimeMillis();
        synchronized (lastRun) {
            long lastRunMs = lastRun.get();
            if (lastRunMs - TimeUnit.MINUTES.toMillis(1) < now) {
                long timeToRun = lastRunMs + TimeUnit.MINUTES.toMillis(1);
                long delay = timeToRun - now;
                lastRun.set(timeToRun);
                Locutus.imp().getCommandManager().getExecutor().schedule(new Runnable() {
                    @Override
                    public void run() {
                        System.out.println("Process queue start");
                        long start = System.currentTimeMillis();
                        try {
                            processQueue();
                        } catch (Throwable e) {
                            e.printStackTrace();
                        }
                        long diff= System.currentTimeMillis() - start;
                        System.out.println("SpyTracker took " + diff + "ms to process queue");
                    }
                }, delay, TimeUnit.MILLISECONDS);
            }
        }
        // run every 1m

        // todo post alerts
    }

    public class SpyActivity {
        private final int nationId;
        private final MilitaryUnit unit;
        private final int change;
        private final long timestamp;
        private final double score;
        private final boolean isKill;
        private List<Nation> nationActiveInfo;

        public SpyActivity(int nationId, MilitaryUnit unit, int change, long timestamp, double score, boolean isKill) {
            this.nationId = nationId;
            this.unit = unit;
            this.change = change;
            this.timestamp = timestamp;
            this.score = score;
            this.isKill = isKill;
        }
    }

    private AtomicBoolean checkActiveFlag = new AtomicBoolean();

    public void addStat(int nationId, MilitaryUnit unit, int kills, int losses, int currentUnits, long timestamp, double score) {
        Map<MilitaryUnit, Integer> nationKills = killTracker.computeIfAbsent(nationId, k -> new ConcurrentHashMap<>());
        Map<MilitaryUnit, Integer> nationLosses = casualtyTracker.computeIfAbsent(nationId, k -> new ConcurrentHashMap<>());
        Map<MilitaryUnit, Integer> nationUnits = unitTotalTracker.computeIfAbsent(nationId, k -> new ConcurrentHashMap<>());

        Integer previousKills = nationKills.get(unit);
        Integer previousLosses = nationLosses.get(unit);
        Integer previousUnits = nationUnits.get(unit);

        if (previousKills == null || kills > previousKills) nationKills.put(unit, kills);
        if (previousLosses == null || losses > previousLosses) nationLosses.put(unit, losses);
        nationUnits.put(unit, currentUnits);

        if (previousKills != null && kills > previousKills) {
            int change = kills - previousKills;
            queue.add(new SpyActivity(nationId, unit, change, timestamp, score, true));
            System.out.println("Add activity kill " + nationId + " | " + unit + " | " + change + " | " + timestamp + " | " + score);
        }

        if (previousLosses != null && losses > previousLosses) {
            int change = losses - previousLosses;
            if (unit == MilitaryUnit.SPIES) {
                SpyActivity activity = new SpyActivity(nationId, unit, change, timestamp, score, false);
                System.out.println("Add activity loss " + nationId + " | " + unit + " | " + change + " | " + timestamp + " | " + score);
                queue.add(activity);

                checkActiveFlag.set(true);
            } else {
                System.out.println("Ignore activity loss " + nationId + " | " + unit + " | " + change + " | " + timestamp + " | " + score);
            }
        }

        if (previousUnits != null && currentUnits < previousUnits && (previousLosses != null && previousLosses == losses)) {
            // unit sold or spied
            if (unit != MilitaryUnit.SPIES) {
                int change = previousUnits - currentUnits;
                if (unit != MilitaryUnit.MISSILE && unit != MilitaryUnit.NUKE) {
                    int max = (int) Math.ceil(previousUnits * 0.99);
                    int min = (int) Math.floor(previousUnits * 0.85);

                    // 1 spy op kills minimum 1% and 3 spy ops kill maximum of 15%
                    if (currentUnits < min || currentUnits > max) return;
                }

                SpyActivity activity = new SpyActivity(nationId, unit, change, timestamp, score, false);
                System.out.println("Add activity sold " + nationId + " | " + unit + " | " + change + " | " + timestamp + " | " + score);
                queue.add(activity);
            }
        }
    }

    public synchronized long removeMatchingAttacks() {
        long cutoff = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(20);
        long requiredProximityMs = TimeUnit.SECONDS.toMillis(1);
        List<DBAttack> allAttacks = Locutus.imp().getWarDb().getAttacks(cutoff);

        Map<Integer, List<DBAttack>> attacksByNation = new HashMap<>();

        long latestAttackMs = 0;
        for (DBAttack attack : allAttacks) {
            if (attack.epoch > latestAttackMs) {
                latestAttackMs = attack.epoch;
            }
            attacksByNation.computeIfAbsent(attack.attacker_nation_id, k -> new ArrayList<>()).add(attack);
            attacksByNation.computeIfAbsent(attack.defender_nation_id, k -> new ArrayList<>()).add(attack);
        }
        Iterator<SpyActivity> iter = queue.iterator();
        while (iter.hasNext()) {
            SpyActivity activity = iter.next();
            if (activity.unit != MilitaryUnit.SPIES) {
                List<DBAttack> attacks = attacksByNation.get(activity.nationId);
                if (attacks != null) {
                    for (DBAttack attack : attacks) {
                        boolean isAttacker = attack.attacker_nation_id == activity.nationId;
                        boolean checkNation = activity.isKill != isAttacker;
                        Map<MilitaryUnit, Integer> losses = attack.getUnitLosses(checkNation);
                        Integer loss = losses.get(activity.unit);
                        if (loss != null) {
                            if (loss == activity.change) {
                                iter.remove();
                                break;
                            }
                            if (Math.abs(attack.epoch - activity.timestamp) < requiredProximityMs) {
                                iter.remove();
                                break;
                            }
                        } else {
                            if (Math.abs(attack.epoch - activity.timestamp) < requiredProximityMs && !activity.isKill) {
                                System.out.println("Ignore loss " + attack.war_id + " " + activity.unit + " " + activity.change + " | " + attack.attack_type + " | " + Math.abs(attack.epoch - activity.timestamp));
                                iter.remove();
                                break;
                            }
                        }
                    }
                }
            }
        }
        return latestAttackMs;
    }

    public synchronized void processQueue() throws IOException {
        if (queue.isEmpty()) return;
        checkActive();
        long latestAttackMs = removeMatchingAttacks();
        if (queue.isEmpty()) return;
        System.out.println("Processing queue1: " + queue.size());

        long checkDefensiveMaxMs = latestAttackMs - TimeUnit.MINUTES.toMillis(5);
        long deleteOffensiveBelowMs = checkDefensiveMaxMs - TimeUnit.MINUTES.toMillis(20);
        long maxActivityDiffMs = TimeUnit.SECONDS.toMillis(60);
        long maxActivitySpiesDiffMs = TimeUnit.SECONDS.toMillis(60 * 5);

        Map<MilitaryUnit, List<SpyActivity>> offensiveByUnit = new HashMap<>();
        Map<MilitaryUnit, List<SpyActivity>> defensiveByUnit = new HashMap<>();

        Iterator<SpyActivity> iter = queue.iterator();
        while (iter.hasNext()) {
            SpyActivity activity = iter.next();
            if (activity.isKill) {
                offensiveByUnit.computeIfAbsent(activity.unit, k -> new ArrayList<>()).add(activity);
                if (activity.timestamp < deleteOffensiveBelowMs) {
                    iter.remove();
                }
            } else if (activity.timestamp < checkDefensiveMaxMs) {
                defensiveByUnit.computeIfAbsent(activity.unit, k -> new ArrayList<>()).add(activity);
                iter.remove();
            }
        }

        if (offensiveByUnit.isEmpty() && defensiveByUnit.isEmpty()) return;

        System.out.println("Processing queue2 " + offensiveByUnit.size() + " | " + defensiveByUnit.size() + " | " + checkDefensiveMaxMs);

        // sort defensives
        for (Map.Entry<MilitaryUnit, List<SpyActivity>> entry : defensiveByUnit.entrySet()) {
            entry.getValue().sort(Comparator.comparingLong(o -> o.timestamp));
        }
        // sort offensives
        for (Map.Entry<MilitaryUnit, List<SpyActivity>> entry : offensiveByUnit.entrySet()) {
            entry.getValue().sort(Comparator.comparingLong(o -> o.timestamp));
        }

        List<SpyAlert> alerts = new ArrayList<>();

        for (Map.Entry<MilitaryUnit, List<SpyActivity>> entry : defensiveByUnit.entrySet()) {
            MilitaryUnit unit = entry.getKey();
            List<SpyActivity> defensives = entry.getValue();
            // get offensives
            List<SpyActivity> offensives = offensiveByUnit.get(unit);
            if (offensives == null) {
                if (unit == MilitaryUnit.SPIES) offensives = Collections.emptyList();
                else continue;
            }

            // iterate
            for (SpyActivity defensive : defensives) {
                DBNation defender = DBNation.byId(defensive.nationId);
                if (defender == null) continue;

                System.out.println("Finding match for " + defender.getNation_id() + " c" + defender.getCities() + " | " + defensive.change + "x" + defensive.unit + " | " + defensive.timestamp + " | " + defensive.score);

                SpyAlert alert = new SpyAlert(defender, unit, defensive.change, defensive.timestamp);

                if (unit == MilitaryUnit.SPIES) {
                    for (SpyActivity offensive : offensives) {
                        if (Math.abs(offensive.timestamp - defensive.timestamp) > maxActivitySpiesDiffMs) continue;
                        if (!SpyCount.isInScoreRange(offensive.score, defensive.score)) continue;
                        if (offensive.nationId == defensive.nationId) continue;

                        if (offensive.change == defensive.change) {
                            alert.exact.add(offensive);
                        } else {
                            alert.close.add(offensive);
                        }
                    }
                    if (defensive.nationActiveInfo != null && !defensive.nationActiveInfo.isEmpty()) {
                        for (Nation nation : defensive.nationActiveInfo) {
                            int id = nation.getId();
                            if (id == defensive.nationId) continue;
                            DBNation cachedNation = DBNation.byId(nation.getId());
                            if (cachedNation == null) continue;
                            if (!SpyCount.isInScoreRange(cachedNation.getScore(), defensive.score)) continue;
                            long activeMs = nation.getLast_active().toEpochMilli();
                            long diff = Math.max(0, defensive.timestamp - activeMs);
                            alert.online.add(Map.entry(cachedNation, diff));
                        }
                        defensive.nationActiveInfo = null;
                    }
                } else {
                    for (SpyActivity offensive : offensives) {
                        if (Math.abs(offensive.timestamp - defensive.timestamp) > maxActivityDiffMs) continue;
                        if (!SpyCount.isInScoreRange(offensive.score, defensive.score)) continue;
                        if (offensive.nationId == defensive.nationId) continue;
                        if (offensive.change == defensive.change) {
                            alert.exact.add(offensive);
                        } else {
                            alert.close.add(offensive);
                        }
                    }

                    Set<Integer> enemies = new HashSet<>();
                    for (DBWar war : defender.getActiveWars()) {
                        enemies.add(war.attacker_id);
                        enemies.add(war.defender_id);
                    }
                    if (!enemies.isEmpty() && alert.close.removeIf(o -> enemies.contains(o.nationId))) {
                        alert.close.clear();
                    }
                }
                if (alert.exact.isEmpty() && alert.close.isEmpty() && alert.online.isEmpty()) {
                    System.out.println("Failed to find op for " + defender.getNation_id() + " c" + defender.getCities() + " | " + defensive.change + "x" + defensive.unit + " | " + defensive.timestamp + " | " + defensive.score);
                    continue;
                }

                alerts.add(alert);
            }
        }

        for (SpyAlert alert : alerts) {
            MilitaryUnit unit = alert.unit;
            DBNation defender = alert.defender;
            defender.updateSpies();

            if (alert.exact.size() == 1) {
                // TODO Log the spy op
            }

            String title = "Possible " + alert.change + " x " + unit + " spied (Note: False positives are common)";
            StringBuilder body = new StringBuilder("**" + title + "**:\n");
            body.append("\nDefender (" + defender.updateSpies(alert.unit == MilitaryUnit.SPIES ? 1 : 24) + " spies):" + defender.toMarkdown(false, true, true, true, true));
            body.append("\ntimestamp:" + alert.timestamp + " (" + TimeUtil.YYYY_MM_DDTHH_MM_SSX.format(new Date(alert.timestamp)) + ")");

            // display recent wars (nation)
            // display current wars (nations / alliances)


            if (unit.getBuilding() != null) {
                int defUnits = defender.getUnits(unit) + Math.abs(alert.change);
                Map.Entry<Integer, Integer> killRangeNoSat = SpyCount.getUnitKillRange(60, 0, unit, defUnits, false);
                Map.Entry<Integer, Integer> killRangeSat = SpyCount.getUnitKillRange(60, 0, unit, defUnits, true);

                body.append("\n\n**" + unit + " kill range:** ");
                body.append(killRangeNoSat.getKey() + " - " + killRangeNoSat.getValue() + "(no SAT) | " + killRangeSat.getKey() + " - " + killRangeSat.getValue() + "(SAT)");
            }

            if (!alert.exact.isEmpty()) {
                body.append("\nAttackers (high probability):");
                for (SpyActivity offensive : alert.exact) {
                    body.append("\n - " + alert.entryToString(offensive, null));
                }
            } else if (!alert.close.isEmpty()) {
                body.append("\nAttackers (medium probability):");
                for (SpyActivity offensive : alert.close) {
                    body.append("\n - " + alert.entryToString(offensive, null));
                }
            } else {
                int defSpies = defender.getSpies();
                if (unit == MilitaryUnit.SPIES) {
                    defender.updateSpies(12);
                    Long spiesUpdated = defender.getTurnUpdatedSpies();
                    if (spiesUpdated != null && spiesUpdated >= TimeUtil.getTurn(System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(20))) {
                        defSpies = Math.min(defender.maxSpies(), defSpies + Math.abs(alert.change));
                    }

                    // int spiesKilled, int defSpies, boolean spySat
                    int killed = Math.abs(alert.change);
                    Map.Entry<Integer, Integer> rangeNoSat = SpyCount.getSpiesUsedRange(killed, defSpies, false);
                    Map.Entry<Integer, Integer> rangeSat = SpyCount.getSpiesUsedRange(killed, defSpies, true);
                    body.append("\n**Attacker Spies Estimate:** ");
                    body.append(rangeNoSat.getKey() + " - " + rangeNoSat.getValue() + "(no SAT) | " + rangeSat.getKey() + " - " + rangeSat.getValue() + "(SAT)");
                    body.append("\n - Note: Unit counts may be incorrect (outdated/two attacks in quick succession)");
                    body.append("\n - See: <https://politicsandwar.fandom.com/wiki/Spies>");
                }

                body.append("\nAttackers Online (low probability):");
                for (Map.Entry<DBNation, Long> entry : alert.online) {
                    DBNation attacker = entry.getKey();
                    body.append("\n - " + alert.entryToString(attacker, null, entry.getValue()));
                }
            }

            System.out.println(body);

            GuildDB db = defender.getGuildDB();
            if (db == null) continue;
            MessageChannel channel = db.getOrNull(GuildDB.Key.ESPIONAGE_ALERT_CHANNEL);
            if (channel == null && (!alert.exact.isEmpty() || unit == MilitaryUnit.SPIES)) {
                channel = db.getOrNull(GuildDB.Key.DEFENSE_WAR_CHANNEL);
                body.append("\nSee: " + CM.settings.cmd.toSlashMention() + " with key `" + GuildDB.Key.ESPIONAGE_ALERT_CHANNEL + "`");
            }
            if (channel == null) continue;

            body.append("\n---");
            new DiscordChannelIO(channel).send(body.toString());
        }
    }

    public class SpyAlert {
        public final List<SpyActivity> exact = new ArrayList<>();
        public final List<SpyActivity> close = new ArrayList<>();
        public final List<Map.Entry<DBNation, Long>> online = new ArrayList<>();
        private final DBNation defender;
        private final MilitaryUnit unit;
        private final int change;
        private final long timestamp;

        public SpyAlert(DBNation defender, MilitaryUnit unit, int change, long timestamp) {
            this.defender = defender;
            this.unit = unit;
            this.change = change;
            this.timestamp = timestamp;
        }


        public String entryToString(SpyActivity offensive, Map.Entry<Integer, Integer> killRange) {
            DBNation attacker = DBNation.byId(offensive.nationId);
            long diff = Math.abs(offensive.timestamp - timestamp);
            return entryToString(attacker, killRange, diff);
        }

        public String entryToString(DBNation attacker, Map.Entry<Integer, Integer> killRange, long diff) {
            int defSpies = defender.getSpies();
            int attSpies = attacker.updateSpies(24);;

            double odds = SpyCount.getOdds(attSpies, defSpies, 3, SpyCount.Operation.getByUnit(unit), defender);



            StringBuilder message = new StringBuilder();
            message.append("<" + attacker.getNationUrl() + ">|" + attacker.getNation() + " | " + attacker.getAlliance() + " | " + MathMan.format(odds) + "%");

            if (killRange != null) {
                message.append(" | " + killRange.getKey() + "-" + killRange.getValue() + " max kills");
            }

            if (attacker.hasProject(Projects.SPY_SATELLITE)) message.append(" | SAT");
            if (attacker.hasProject(Projects.INTELLIGENCE_AGENCY)) message.append(" | IA");
            message.append(" | " + attSpies + " \uD83D\uDD75");
            message.append(" | " + TimeUtil.secToTime(TimeUnit.MILLISECONDS, diff));
            return message.toString();
        }
    }

    public void checkActive() throws IOException {
        if (!checkActiveFlag.getAndSet(false)) return;
        List<SpyActivity> activitiesToFlag = new ArrayList<>();
        for (SpyActivity activity : queue) {
            if (activity.unit == MilitaryUnit.SPIES && !activity.isKill && activity.nationActiveInfo == null) {
                activitiesToFlag.add(activity);
            }
        }
        if (activitiesToFlag.isEmpty()) return;
        System.out.println(":||remove SpyTracker Checking active");

        String url = "https://politicsandwar.com/index.php?id=15&keyword=&cat=everything&ob=lastactive&od=DESC&maximum=50&minimum=0&search=Go&vmode=false";
        String html = FileUtil.readStringFromURL(url);

        List<Integer> nationIds = PnwUtil.getNationsFromTable(html, 0);
        Map<Integer, Integer> nationIdIndex = new HashMap<>();
        for (int i = 0; i < nationIds.size(); i++) {
            nationIdIndex.put(nationIds.get(i), i);
        }

        List<Nation> nationActiveData = new ArrayList<>(Locutus.imp().getV3().fetchNationActive(nationIds));
        // sort by order in nationIds
        nationActiveData.sort(Comparator.comparingInt(o -> nationIdIndex.get(o.getId())));

        for (SpyActivity activity : activitiesToFlag) {
            activity.nationActiveInfo = nationActiveData;
        }
    }

    public void updateCasualties(Nation nation, long timestamp) {
        double score = nation.getScore();
        // missiles
        if (nation.getMissile_casualties() != null && nation.getMissile_kills() != null && nation.getMissiles() != null) {
            addStat(nation.getId(), MilitaryUnit.MISSILE, nation.getMissile_kills(), nation.getMissile_casualties(), nation.getMissiles(), timestamp, score);
        }
        // nukes
        if (nation.getNuke_casualties() != null && nation.getNuke_kills() != null && nation.getNukes() != null) {
            addStat(nation.getId(), MilitaryUnit.NUKE, nation.getNuke_kills(), nation.getNuke_casualties(), nation.getNukes(), timestamp, score);
        }
        // soldiers
        if (nation.getSoldier_casualties() != null && nation.getSoldier_kills() != null && nation.getSoldiers() != null) {
            addStat(nation.getId(), MilitaryUnit.SOLDIER, nation.getSoldier_kills(), nation.getSoldier_casualties(), nation.getSoldiers(), timestamp, score);
        }
        // tanks
        if (nation.getTank_casualties() != null && nation.getTank_kills() != null && nation.getTanks() != null) {
            addStat(nation.getId(), MilitaryUnit.TANK, nation.getTank_kills(), nation.getTank_casualties(), nation.getTanks(), timestamp, score);
        }
        // aircraft
        if (nation.getAircraft_casualties() != null && nation.getAircraft_kills() != null && nation.getAircraft() != null) {
            addStat(nation.getId(), MilitaryUnit.AIRCRAFT, nation.getAircraft_kills(), nation.getAircraft_casualties(), nation.getAircraft(), timestamp, score);
        }
        // ships
        if (nation.getShip_casualties() != null && nation.getShip_kills() != null && nation.getShips() != null) {
            addStat(nation.getId(), MilitaryUnit.SHIP, nation.getShip_kills(), nation.getShip_casualties(), nation.getShips(), timestamp, score);
        }
        // spies
        if (nation.getSpy_kills() != null && nation.getSpy_casualties() != null && nation.getSpies() != null) {
            addStat(nation.getId(), MilitaryUnit.SPIES, nation.getSpy_kills(), nation.getSpy_casualties(), nation.getSpies(), timestamp, score);
        }
    }
}