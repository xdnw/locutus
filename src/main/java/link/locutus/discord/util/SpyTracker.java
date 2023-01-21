package link.locutus.discord.util;

import com.politicsandwar.graphql.model.Nation;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.core.ApiKeyPool;
import link.locutus.discord.apiv1.domains.subdomains.DBAttack;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.apiv3.PoliticsAndWarV3;
import link.locutus.discord.apiv3.subscription.PnwPusherEvent;
import link.locutus.discord.apiv3.subscription.PnwPusherFilter;
import link.locutus.discord.apiv3.subscription.PnwPusherHandler;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.FileUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.SpyCount;
import link.locutus.discord.util.TimeUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class SpyTracker {
    public SpyTracker() {
    }

    public void run(Set<DBAlliance> alliances) {
        // initialize by fetching all kills/casualties at start
    }

    Map<Integer, Map<MilitaryUnit, Integer>> casualtyTracker = new HashMap<>();
    Map<Integer, Map<MilitaryUnit, Integer>> killTracker = new HashMap<>();
    ConcurrentLinkedQueue<SpyActivity> queue = new ConcurrentLinkedQueue<>();

    private AtomicLong lastRun = new AtomicLong();

    public void updateCasualties(List<Nation> nations) throws IOException {
        for (Nation nation : nations) {
            updateCasualties(nation);
        }
        checkActive();
        if (queue.isEmpty()) return;

        long now = System.currentTimeMillis();
        synchronized (lastRun) {
            long lastRunMs = lastRun.get();
            if (lastRunMs - TimeUnit.MILLISECONDS.toMillis(1) < now) {
                long timeToRun = lastRunMs + TimeUnit.MINUTES.toMillis(1);
                long delay = timeToRun - now;
                lastRun.set(timeToRun);
                Locutus.imp().getCommandManager().getExecutor().schedule(new Runnable() {
                    @Override
                    public void run() {
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
        private int nationId;
        private MilitaryUnit unit;
        private int change;
        private long timestamp;
        private double score;
        private boolean isKill;
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

    public void addStat(int nationId, MilitaryUnit unit, int value, long timestamp, double score, boolean isKill) {
        Map<Integer, Map<MilitaryUnit, Integer>> map = isKill ? killTracker : casualtyTracker;
        Map<MilitaryUnit, Integer> nationStats = map.computeIfAbsent(nationId, k -> new HashMap<>());
        Integer current = nationStats.get(unit);
        if (current == null || value > current) {
            nationStats.put(unit, value);

            if (current == null) return;

            int change = value - current;
            SpyActivity activity = new SpyActivity(nationId, unit, change, timestamp, score, isKill);

            queue.add(activity);

            if (unit == MilitaryUnit.SPIES) {
                if (!isKill) {
                    checkActiveFlag.set(true);
                }
            }
        }
    }

    public synchronized long removeMatchingAttacks() {
        long cutoff = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(15);
        long requiredProximityMs = TimeUnit.MINUTES.toMillis(1);
        List<DBAttack> allAttacks = Locutus.imp().getWarDb().getAttacks(cutoff);

        Map<Integer, List<DBAttack>> attacksByNation = new HashMap<>();

        long latestAttackMs = 0;
        for (DBAttack attack : allAttacks) {
            if (attack.epoch > latestAttackMs) {
                latestAttackMs = attack.epoch;
            }
            if (attack.attcas1 > 0 || attack.attcas2 > 0) {
                attacksByNation.computeIfAbsent(attack.attacker_nation_id, k -> new LinkedList<>()).add(attack);
            }
            if (attack.defcas1 > 0 || attack.defcas2 > 0 || attack.defcas3 > 0) {
                attacksByNation.computeIfAbsent(attack.defender_nation_id, k -> new LinkedList<>()).add(attack);
            }
        }
        Iterator<SpyActivity> iter = queue.iterator();
        while (iter.hasNext()) {
            SpyActivity activity = iter.next();
            if (activity.unit != MilitaryUnit.SPIES) {
                List<DBAttack> attacks = attacksByNation.get(activity.nationId);
                if (attacks != null) {
                    for (DBAttack attack : attacks) {
                        boolean attacker = attack.attacker_nation_id == activity.nationId;
                        Map<MilitaryUnit, Integer> losses = attack.getUnitLosses(attacker);
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
                            if (Math.abs(attack.epoch - activity.timestamp) < requiredProximityMs) {
                                System.out.println("Ignore loss " + attack.war_id + " " + activity.unit + " " + activity.change + " | " + attack.attack_type + " | " + Math.abs(attack.epoch - activity.timestamp));
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
        System.out.println("Processing queue: " + queue.size());

        long checkDefensiveMaxMs = latestAttackMs - TimeUnit.MINUTES.toMillis(1);
        long deleteOffensiveBelowMs = checkDefensiveMaxMs - TimeUnit.MINUTES.toMillis(2);
        long maxActivityDiffMs = TimeUnit.SECONDS.toMillis(20);

        Map<MilitaryUnit, List<SpyActivity>> offensiveByUnit = new HashMap<>();
        Map<MilitaryUnit, List<SpyActivity>> defensiveByUnit = new HashMap<>();

        Iterator<SpyActivity> iter = queue.iterator();
        while (iter.hasNext()) {
            SpyActivity activity = iter.next();
            if (activity.timestamp < checkDefensiveMaxMs) {
                if (activity.isKill) {
                    offensiveByUnit.computeIfAbsent(activity.unit, k -> new ArrayList<>()).add(activity);
                    if (activity.timestamp < deleteOffensiveBelowMs) {
                        iter.remove();
                    }
                } else {
                    iter.remove();
                    defensiveByUnit.computeIfAbsent(activity.unit, k -> new ArrayList<>()).add(activity);
                }
            }
        }

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

            // iterate
            for (SpyActivity defensive : defensives) {
                DBNation defender = DBNation.byId(defensive.nationId);
                if (defender == null) continue;

                SpyAlert alert = new SpyAlert(defender, unit, defensive.change, defensive.timestamp);

                if (unit == MilitaryUnit.SPIES) {
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
                }
                if (alert.exact.isEmpty() && alert.close.isEmpty() && alert.online.isEmpty()) continue;

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

            String title = "Possible " + alert.change + " x " + unit + " spied";
            StringBuilder body = new StringBuilder("**" + title + "**:\n");
            body.append("\nDefender: " + defender.toMarkdown(false, true, true, true, true));
            body.append("\ntimestamp:" + alert.timestamp + " (" + TimeUtil.YYYY_MM_DDTHH_MM_SSX.format(new Date(alert.timestamp)) + ")");

            if (!alert.exact.isEmpty()) {
                body.append("\nAttackers (high probability):");
                for (SpyActivity offensive : alert.exact) {
                    body.append("\n - " + alert.entryToString(offensive));
                }
            } else if (!alert.close.isEmpty()) {
                body.append("\nAttackers (medium probability):");
                for (SpyActivity offensive : alert.close) {
                    body.append("\n - " + alert.entryToString(offensive));
                }
            } else {
                body.append("\nAttackers Online (low probability):");
                for (Map.Entry<DBNation, Long> entry : alert.online) {
                    DBNation attacker = entry.getKey();
                    body.append("\n - " + alert.entryToString(attacker, entry.getValue()));
                }
            }

            System.out.println(body.toString());
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


        public String entryToString(SpyActivity offensive) {
            DBNation attacker = DBNation.byId(offensive.nationId);
            long diff = Math.abs(offensive.timestamp - timestamp);
            return entryToString(attacker, diff);
        }

        public String entryToString(DBNation attacker, long diff) {
            StringBuilder message = new StringBuilder(attacker.getNationUrlMarkup(true) + " | " + attacker.getAllianceUrlMarkup(true));
            int defSpies = defender.getSpies();
            int attSpies = attacker.updateSpies();
            double odds = SpyCount.getOdds(attSpies, defSpies, 3, SpyCount.Operation.getByUnit(unit), defender);
            message.append(MathMan.format(odds) + "%");
            if (attacker.hasProject(Projects.SPY_SATELLITE)) message.append(" | SAT");
            if (attacker.hasProject(Projects.INTELLIGENCE_AGENCY)) message.append(" | IA");
            message.append(" | " + attSpies + " spies (?)");
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

        String url = "https://politicsandwar.com/index.php?id=15&keyword=&cat=everything&ob=lastactive&od=DESC&maximum=50&minimum=0&search=Go&vmode=false";
        String html = FileUtil.readStringFromURL(url);

        List<Integer> nationIds = PnwUtil.getNationsFromTable(html, 0);

        List<Nation> nationActiveData = Locutus.imp().getV3().fetchNationActive(nationIds);

        for (SpyActivity activity : activitiesToFlag) {
            activity.nationActiveInfo = new ArrayList<>(nationActiveData);
        }
    }


    public void updateCasualties(Nation nation) {
        long timestamp = System.currentTimeMillis();
        double score = nation.getScore();
        // soldiers
        if (nation.getSoldier_casualties() != null) {
            addStat(nation.getId(), MilitaryUnit.SOLDIER, nation.getSoldier_casualties(), timestamp, score, false);
        }
        if (nation.getSoldier_kills() != null) {
            addStat(nation.getId(), MilitaryUnit.SOLDIER, nation.getSoldier_kills(), timestamp, score, true);
        }
        // tanks
        if (nation.getTank_casualties() != null) {
            addStat(nation.getId(), MilitaryUnit.TANK, nation.getTank_casualties(), timestamp, score, false);
        }
        if (nation.getTank_kills() != null) {
            addStat(nation.getId(), MilitaryUnit.TANK, nation.getTank_kills(), timestamp, score, true);
        }
        // aircraft
        if (nation.getAircraft_casualties() != null) {
            addStat(nation.getId(), MilitaryUnit.AIRCRAFT, nation.getAircraft_casualties(), timestamp, score, false);
        }
        if (nation.getAircraft_kills() != null) {
            addStat(nation.getId(), MilitaryUnit.AIRCRAFT, nation.getAircraft_kills(), timestamp, score, true);
        }
        // ships
        if (nation.getShip_casualties() != null) {
            addStat(nation.getId(), MilitaryUnit.SHIP, nation.getShip_casualties(), timestamp, score, false);
        }
        if (nation.getShip_kills() != null) {
            addStat(nation.getId(), MilitaryUnit.SHIP, nation.getShip_kills(), timestamp, score, true);
        }
        // missiles
        if (nation.getMissile_casualties() != null) {
            addStat(nation.getId(), MilitaryUnit.MISSILE, nation.getMissile_casualties(), timestamp, score, false);
        }
        if (nation.getMissile_kills() != null) {
            addStat(nation.getId(), MilitaryUnit.MISSILE, nation.getMissile_kills(), timestamp, score, true);
        }
        // nukes
        if (nation.getNuke_casualties() != null) {
            addStat(nation.getId(), MilitaryUnit.NUKE, nation.getNuke_casualties(), timestamp, score, false);
        }
        if (nation.getNuke_kills() != null) {
            addStat(nation.getId(), MilitaryUnit.NUKE, nation.getNuke_kills(), timestamp, score, true);
        }
        // spies
        if (nation.getSpy_casualties() != null) {
            addStat(nation.getId(), MilitaryUnit.SPIES, nation.getSpy_casualties(), timestamp, score, false);
        }
        if (nation.getSpy_kills() != null) {
            addStat(nation.getId(), MilitaryUnit.SPIES, nation.getSpy_kills(), timestamp, score, true);
        }
    }
}