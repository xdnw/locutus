package link.locutus.discord.util;

import com.politicsandwar.graphql.model.Bounty;
import com.politicsandwar.graphql.model.Nation;
import com.politicsandwar.graphql.model.NationResponseProjection;
import com.politicsandwar.graphql.model.NationsQueryRequest;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.Locutus;
import link.locutus.discord.Logg;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AbstractCursor;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.apiv3.PoliticsAndWarV3;
import link.locutus.discord.apiv3.enums.AlliancePermission;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.db.handlers.AttackQuery;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.io.PagePriority;
import link.locutus.discord.util.scheduler.CaughtRunnable;
import link.locutus.discord.util.scheduler.KeyValue;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class SpyTracker {
    public SpyTracker() {
        long delay = TimeUnit.MINUTES.toMillis(1);
        Locutus.imp().getRepeatingTasks().addTask("Spy Tracker (Queue)" , new CaughtRunnable() {
            @Override
            public void runUnsafe() throws IOException {
                processQueue();
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    public void loadCasualties(Integer allianceId) {
        PoliticsAndWarV3 api = null;
        if (allianceId != null) {
            api = DBAlliance.getOrCreate(allianceId).getApi(AlliancePermission.SEE_SPIES);
            if (api == null) return;
        } else {
            api = Locutus.imp().getApiPool();
        }
        List<Nation> nations = api.fetchNations(false, new Consumer<NationsQueryRequest>() {
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
                f.nukes();
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
        if (Settings.INSTANCE.LEGACY_SETTINGS.PRINT_ESPIONAGE_DEBUG) Logg.text("- Fetched nations " + nations.size());
        try {
            updateCasualties(nations, false);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (Settings.INSTANCE.LEGACY_SETTINGS.PRINT_ESPIONAGE_DEBUG) Logg.text("- updated casualties " + killTracker.size() + " | " + casualtyTracker.size());
    }

    private final Map<Integer, Map<MilitaryUnit, Integer>> casualtyTracker = new ConcurrentHashMap<>();
    private final Map<Integer, Map<MilitaryUnit, Integer>> unitTotalTracker = new ConcurrentHashMap<>();
    private final Map<Integer, Map<MilitaryUnit, Integer>> killTracker = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<SpyActivity> queue = new ConcurrentLinkedQueue<>();

    public void updateCasualties(List<Nation> nations, boolean isGLobal) throws IOException {
        if (Settings.INSTANCE.LEGACY_SETTINGS.PRINT_ESPIONAGE_DEBUG) Logg.text("Called update casualties " + nations.size());
        long timestamp = System.currentTimeMillis();
        for (Nation nation : nations) {
            updateCasualties(nation, timestamp, isGLobal);
        }
        if (Settings.INSTANCE.LEGACY_SETTINGS.PRINT_ESPIONAGE_DEBUG) Logg.text(" queue1 " + queue.size());
        checkActive();
    }

    public static class SpyActivity {
        private final int nationId;
        private final MilitaryUnit unit;
        private final int originalSpies;
        private final int originalOfUnit;
        private final int change;
        private final long timestamp;
        private final double score;
        private final boolean isKill;
        private List<Nation> nationActiveInfo;

        public SpyActivity(int nationId, MilitaryUnit unit, int originalSpies, int originalOfUnit, int change, long timestamp, double score, boolean isKill) {
            this.nationId = nationId;
            this.unit = unit;
            this.originalSpies = originalSpies;
            this.originalOfUnit = originalOfUnit;
            this.change = change;
            this.timestamp = timestamp;
            this.score = score;
            this.isKill = isKill;
        }
    }

    private AtomicBoolean checkActiveFlag = new AtomicBoolean();

    public void addStat(int nationId, MilitaryUnit unit, int kills, int losses, int currentUnits, int currentSpies, long timestamp, double score) {
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
            queue.add(new SpyActivity(nationId, unit, currentUnits, currentSpies, change, timestamp, score, true));
            if (Settings.INSTANCE.LEGACY_SETTINGS.PRINT_ESPIONAGE_DEBUG) Logg.text("Add activity kill " + nationId + " | " + unit + " | " + change + " | " + timestamp + " | " + score);
        }

        if (previousLosses != null && losses > previousLosses) {
            int change = losses - previousLosses;
            if (unit == MilitaryUnit.SPIES) {
                SpyActivity activity = new SpyActivity(nationId, unit, currentUnits + change, currentUnits + change, change, timestamp, score, false);
                if (Settings.INSTANCE.LEGACY_SETTINGS.PRINT_ESPIONAGE_DEBUG) Logg.text("Add activity loss " + nationId + " | " + unit + " | " + change + " | " + timestamp + " | " + score);
                queue.add(activity);

                checkActiveFlag.set(true);
            } else {
                if (Settings.INSTANCE.LEGACY_SETTINGS.PRINT_ESPIONAGE_DEBUG) Logg.text("Ignore activity loss " + nationId + " | " + unit + " | " + change + " | " + timestamp + " | " + score + "| " + previousLosses + " | " + losses);
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

                SpyActivity activity = new SpyActivity(nationId, unit, currentUnits + change, change, currentSpies, timestamp, score, false);
                if (Settings.INSTANCE.LEGACY_SETTINGS.PRINT_ESPIONAGE_DEBUG) Logg.text("Add activity sold " + nationId + " | " + unit + " | " + change + " | " + timestamp + " | " + score);
                queue.add(activity);
            }
        }
    }

    public synchronized long removeMatchingAttacks() {
        long cutoff = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(20);
        long requiredProximityMs = TimeUnit.SECONDS.toMillis(1);

        Set<Integer> nationIds = new IntOpenHashSet();
        for (SpyActivity activity : queue) {
            nationIds.add(activity.nationId);
        }
        AttackQuery query = Locutus.imp().getWarDb().queryAttacks().withWarMap(f -> f.getWars(
                Collections.emptySet(),
                nationIds,
                Collections.emptySet(),
                Collections.emptySet(),
                cutoff,
                Long.MAX_VALUE
        )).appendPreliminaryFilter(f -> f.getDate() >= cutoff);

        Map<Integer, List<AbstractCursor>> attacksByNation = new Int2ObjectOpenHashMap<>();

        long[] latestAttackMs = {0};
        query.iterateAttacks((war, attack) -> {
            if (attack.getDate() > latestAttackMs[0]) {
                latestAttackMs[0] = attack.getDate();
            }
            attacksByNation.computeIfAbsent(attack.getAttacker_id(), k -> new ObjectArrayList<>()).add(attack);
            attacksByNation.computeIfAbsent(attack.getDefender_id(), k -> new ObjectArrayList<>()).add(attack);
        });

        Iterator<SpyActivity> iter = queue.iterator();
        while (iter.hasNext()) {
            SpyActivity activity = iter.next();
            if (activity.unit != MilitaryUnit.SPIES) {
                List<AbstractCursor> attacks = attacksByNation.get(activity.nationId);
                if (attacks != null) {
                    for (AbstractCursor attack : attacks) {
                        boolean isAttacker = attack.getAttacker_id() == activity.nationId;
                        boolean checkNation = activity.isKill != isAttacker;
                        Map<MilitaryUnit, Integer> losses = attack.getUnitLosses2(checkNation);
                        Integer loss = losses.get(activity.unit);
                        if (loss != null) {
                            if (loss == activity.change) {
                                iter.remove();
                                break;
                            }
                            if (Math.abs(attack.getDate() - activity.timestamp) < requiredProximityMs) {
                                iter.remove();
                                break;
                            }
                        } else {
                            if (Math.abs(attack.getDate() - activity.timestamp) < requiredProximityMs && !activity.isKill) {
                                if (Settings.INSTANCE.LEGACY_SETTINGS.PRINT_ESPIONAGE_DEBUG) Logg.text("Ignore loss " + attack.getWar_id() + " " + activity.unit + " " + activity.change + " | " + attack.getAttack_type() + " | " + Math.abs(attack.getDate() - activity.timestamp));
                                iter.remove();
                                break;
                            }
                        }
                    }
                }
            }
        }
        return latestAttackMs[0];
    }

    public synchronized void processQueue() throws IOException {
        if (queue.isEmpty()) return;
        checkActive();
        long latestAttackMs = removeMatchingAttacks();
        if (queue.isEmpty()) return;
        if (Settings.INSTANCE.LEGACY_SETTINGS.PRINT_ESPIONAGE_DEBUG) Logg.text("Processing queue-1: " + queue.size());

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

        if (Settings.INSTANCE.LEGACY_SETTINGS.PRINT_ESPIONAGE_DEBUG) Logg.text("Processing queue2 " + offensiveByUnit.size() + " | " + defensiveByUnit.size() + " | " + checkDefensiveMaxMs);

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
                DBNation defender = DBNation.getById(defensive.nationId);
                if (defender == null) continue;
                DBAlliance defAA = defender.getAlliance();
                Set<Integer> treaties;
                if (defAA == null) {
                    treaties = Collections.emptySet();
                } else {
                    treaties = new IntOpenHashSet(defAA.getTreaties().keySet());
                    treaties.add(defAA.getId());
                }

                if (Settings.INSTANCE.LEGACY_SETTINGS.PRINT_ESPIONAGE_DEBUG) Logg.text("Finding match for " + defender.getNation_id() + " c" + defender.getCities() + " | " + defensive.change + "x" + defensive.unit + " | " + defensive.timestamp + " | " + defensive.score);

                SpyAlert alert = new SpyAlert(defender, unit, defensive.originalOfUnit, defensive.originalSpies, defensive.change, defensive.timestamp);

                if (unit == MilitaryUnit.SPIES) {
                    for (SpyActivity offensive : offensives) {
                        if (Math.abs(offensive.timestamp - defensive.timestamp) > maxActivitySpiesDiffMs) continue;
                        if (!SpyCount.isInScoreRange(offensive.score, defensive.score)) continue;
                        if (offensive.nationId == defensive.nationId) continue;
                        if (Settings.INSTANCE.LEGACY_SETTINGS.ESPIONAGE_ALWAYS_ONLINE.contains(offensive.nationId)) continue;

                        DBNation attacker = DBNation.getById(offensive.nationId);
                        if (attacker != null && (attacker.getAlliance_id() == defender.getAlliance_id() || treaties.contains(attacker.getAlliance_id()))) continue;
                        if (attacker != null && Settings.INSTANCE.LEGACY_SETTINGS.ESPIONAGE_ALWAYS_ONLINE_AA.contains(attacker.getAlliance_id())) continue;

                        if (offensive.change == defensive.change) {
                            alert.high.add(offensive);
                        } else {
                            alert.medium.add(offensive);
                        }
                    }
                    if (defensive.nationActiveInfo != null && !defensive.nationActiveInfo.isEmpty()) {
                        for (Nation nation : defensive.nationActiveInfo) {
                            int id = nation.getId();
                            if (id == defensive.nationId) continue;
                            DBNation cachedNation = DBNation.getById(nation.getId());
                            if (cachedNation == null) continue;
                            if (!SpyCount.isInScoreRange(cachedNation.getScore(), defensive.score)) continue;
                            long activeMs = nation.getLast_active().toEpochMilli();
                            long diff = Math.max(0, defensive.timestamp - activeMs);
                            alert.low.add(KeyValue.of(cachedNation, diff));
                        }
                        defensive.nationActiveInfo = null;
                    }
                } else {
                    for (SpyActivity offensive : offensives) {
                        if (Math.abs(offensive.timestamp - defensive.timestamp) > maxActivityDiffMs) continue;
                        if (!SpyCount.isInScoreRange(offensive.score, defensive.score)) continue;
                        if (offensive.nationId == defensive.nationId) continue;
                        if (offensive.change == defensive.change) {
                            alert.high.add(offensive);
                        } else {
                            alert.medium.add(offensive);
                        }
                    }

                    Set<Integer> enemies = new IntOpenHashSet();
                    for (DBWar war : defender.getActiveWars()) {
                        enemies.add(war.getAttacker_id());
                        enemies.add(war.getDefender_id());
                    }
                    if (!enemies.isEmpty() && alert.medium.removeIf(o -> enemies.contains(o.nationId))) {
                        alert.medium.clear();
                    }
                }
                if (alert.high.isEmpty() && alert.medium.isEmpty() && alert.low.isEmpty()) {
                    if (Settings.INSTANCE.LEGACY_SETTINGS.PRINT_ESPIONAGE_DEBUG) Logg.text("Failed to find op for " + defender.getNation_id() + " c" + defender.getCities() + " | " + defensive.change + "x" + defensive.unit + " | " + defensive.timestamp + " | " + defensive.score);
                    continue;
                }

                alerts.add(alert);
            }
        }

        for (SpyAlert alert : alerts) {
            MilitaryUnit unit = alert.unit;
            DBNation defender = alert.defender;

            if (alert.high.size() == 1) {
                // TODO Log the spy op
            }

            String defSpiesStr;
            if (alert.unit == MilitaryUnit.SPIES) {
                defSpiesStr = alert.originalUnit + "->" + (alert.originalUnit - alert.change);
            } else {
                defender.updateSpies(PagePriority.ESPIONAGE_ODDS_BULK, 24);
                defSpiesStr = "" + defender.getSpies();
            }

            String title = alert.change + " x " + unit + " Spied";
            StringBuilder body = new StringBuilder("## " + title + ":\n");
            body.append("-# note1: False positives are common with nations that are __always online__\n");
            body.append("-# note2: Nations active **BEFORE** the minute displayed in-game (UTC) are improbable\n");
            body.append("-# note3: Consider the `SAT` and `no SAT` kill ranges and `Spies Estimate`\n");
            body.append("-# note4: Two attacks in quick succession may throw off estimates; [See Wiki](<https://politicsandwar.fandom.com/wiki/Spies>)\n");

            body.append("\n**Defender** (" + defSpiesStr + " spies):" + defender.toMarkdown(false, false, true, true, true, true));
            body.append("\n- Time: `" + TimeUtil.format(TimeUtil.YYYY_MM_DDTHH_MM_SSX, alert.timestamp) + "` | `timestamp:" + alert.timestamp + "`");

            int defUnits = alert.originalUnit;
            Map.Entry<Integer, Integer> killRangeNoSat, killRangeSat;
            killRangeNoSat = SpyCount.getUnitKillRange(60, alert.originalSpies, unit, defUnits, false, defender.hasProject(Projects.SURVEILLANCE_NETWORK));
            killRangeSat = SpyCount.getUnitKillRange(60, alert.originalSpies, unit, defUnits, true, defender.hasProject(Projects.SURVEILLANCE_NETWORK));
            body.append("\n\n**" + unit + " kill range:** ");
            body.append(killRangeNoSat.getKey() + "- " + killRangeNoSat.getValue() + "(no SAT) | " + killRangeSat.getKey() + "- " + killRangeSat.getValue() + "(SAT)");

            int defUnitBefore = alert.originalUnit;
            int defUnitNow = alert.originalUnit - alert.change;
            Runnable appendSpySatInfo = () -> {
                int killed = Math.abs(alert.change);
                Map.Entry<Integer, Integer> rangeNoSat = SpyCount.getSpiesUsedRange(killed, defUnitBefore, false);
                Map.Entry<Integer, Integer> rangeSat = SpyCount.getSpiesUsedRange(killed, defUnitBefore, true);
                body.append("\n**Attacker Spies Estimate:** ");
                body.append(rangeNoSat.getKey() + "- " + rangeNoSat.getValue() + "(no SAT) | " + rangeSat.getKey() + "- " + rangeSat.getValue() + "(SAT)");
            };
            if (unit == MilitaryUnit.SPIES) {
                appendSpySatInfo.run();
            }

            if (!alert.high.isEmpty()) {
                body.append("\n__Attackers (high %):__");
                for (SpyActivity offensive : alert.high) {
                    body.append("\n- " + alert.entryToString(offensive, null));
                }
            } else {
                if (!alert.medium.isEmpty()) {
                    body.append("\n__Attackers (med %):__");
                    for (SpyActivity offensive : alert.medium) {
                        body.append("\n- " + alert.entryToString(offensive, null));
                    }
                }
                body.append("\n__Attackers (low %):__");
                for (Map.Entry<DBNation, Long> entry : alert.low) {
                    DBNation attacker = entry.getKey();
                    body.append("\n- " + alert.entryToString(attacker, null, entry.getValue()));
                }
            }

            GuildDB db = defender.getGuildDB();
            if (db == null) continue;
            MessageChannel channel = db.getOrNull(GuildKey.ESPIONAGE_ALERT_CHANNEL);
            if (channel == null && (!alert.high.isEmpty() || unit == MilitaryUnit.SPIES)) {
                channel = db.getOrNull(GuildKey.DEFENSE_WAR_CHANNEL);
                body.append("\nSpy kills are not enabled (only units). See: " + CM.settings.info.cmd.toSlashMention() + " with key `" + GuildKey.ESPIONAGE_ALERT_CHANNEL.name() + "`");
            }
            if (channel == null) continue;
            body.append("\n---");
            Role role = Roles.ESPIONAGE_ALERTS.toRole2(db);
            if (role != null) body.append(role.getAsMention());
            new DiscordChannelIO(channel).send(body.toString());
        }
    }

    public static class SpyAlert {
        public final List<SpyActivity> high = new ArrayList<>();
        public final List<SpyActivity> medium = new ArrayList<>();
        public final List<Map.Entry<DBNation, Long>> low = new ArrayList<>();
        private final DBNation defender;
        private final MilitaryUnit unit;
        private final int originalUnit;
        private final int originalSpies;
        private final int change;
        private final long timestamp;

        public SpyAlert(DBNation defender, MilitaryUnit unit, int originalUnit, int originalSpies, int change, long timestamp) {
            this.defender = defender;
            this.unit = unit;
            this.originalUnit = originalUnit;
            this.originalSpies = originalSpies;
            this.change = change;
            this.timestamp = timestamp;
        }

        public int getOriginalUnit() {
            return originalUnit;
        }

        public int getOriginalSpies() {
            return originalSpies;
        }

        public String entryToString(SpyActivity offensive, Map.Entry<Integer, Integer> killRange) {
            DBNation attacker = DBNation.getById(offensive.nationId);
            long diff = Math.abs(offensive.timestamp - timestamp);
            return entryToString(attacker, killRange, diff);
        }

        public String entryToString(DBNation attacker, Map.Entry<Integer, Integer> killRange, long diff) {
            int attSpies = attacker.updateSpies(PagePriority.ESPIONAGE_ODDS_BULK, 24);;

            double odds = SpyCount.getOdds(attSpies, originalSpies, 3, SpyCount.Operation.getByUnit(unit), defender);

            StringBuilder message = new StringBuilder();
            message.append(attacker.getMarkdownUrl() + " | " + attacker.getAllianceUrlMarkup() + " | " + MathMan.format(odds) + "%");

            if (killRange != null) {
                message.append(" | kill:" + killRange.getKey() + "-" + killRange.getValue());
            }

            if (attacker.hasProject(Projects.SPY_SATELLITE)) message.append(" | SAT");
            if (attacker.hasProject(Projects.INTELLIGENCE_AGENCY)) message.append(" | IA");
            if (attacker.getWarPolicy() == WarPolicy.ARCANE) {
                message.append(" | ARC");
            }
            if (attacker.getWarPolicy() == WarPolicy.COVERT) {
                message.append(" | COV");
            }

            String fullTimestamp = TimeUtil.format(TimeUtil.MMDD_HH_MM_A, timestamp);

            message.append(" | " + attSpies + " \uD83D\uDD75");
            String timeStr = TimeUtil.format(TimeUtil.MMDD_HH_MM_SS_A, timestamp);
            String diffStr = TimeUtil.secToTime(TimeUnit.MILLISECONDS, diff);
            if (diffStr.isEmpty()) diffStr = "Now";
            message.append(" | " + timeStr + " | " + diffStr);
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

        // sort by order in nationIds
        List<Nation> nationActiveData = Locutus.imp().getNationDB().getActive(true, true);

        for (SpyActivity activity : activitiesToFlag) {
            activity.nationActiveInfo = nationActiveData;
        }
    }

    public void checkBounties(List<Bounty> all) throws IOException {
        long start = System.currentTimeMillis();
        Map<GuildDB, Set<Bounty>> bountiesByGuild = new LinkedHashMap<>();
        for (Bounty bounty : all) {
            DBNation nation = DBNation.getById(bounty.getNation_id());
            if (nation == null || nation.getPositionEnum().id <= Rank.APPLICANT.id) continue;
            int aaId = nation.getAlliance_id();
            GuildDB db = nation.getGuildDB();
            if (db != null) {
                MessageChannel espionage = GuildKey.ESPIONAGE_ALERT_CHANNEL.getOrNull(db);
                if (espionage != null) {
                    bountiesByGuild.computeIfAbsent(db, k -> new HashSet<>()).add(bounty);
                }
            }
        }
        if (bountiesByGuild.isEmpty()) return;
        List<Nation> active = Locutus.imp().getNationDB().getActive(true, true);
        for (Map.Entry<GuildDB, Set<Bounty>> entry : bountiesByGuild.entrySet()) {
            GuildDB db = entry.getKey();
            MessageChannel channel = GuildKey.ESPIONAGE_ALERT_CHANNEL.getOrNull(db);
            if (channel == null) continue;

            Set<Bounty> bounties = entry.getValue();

            long total = bounties.stream().mapToLong(Bounty::getAmount).sum();
            String title = "$" + MathMan.format(total) + " BOUNTY placed on ";
            if (bounties.size() == 1) {
                Bounty bounty = bounties.iterator().next();
                title += PW.getName(bounty.getNation_id(), false);
            } else {
                title += bounties.size() + " nations";
            }

            long minTime = Long.MAX_VALUE;
            StringBuilder body = new StringBuilder();
            body.append("**Bounties:**\n");
            for (Bounty bounty : bounties) {
                long timeMs = bounty.getDate().toEpochMilli();
                minTime = Math.min(timeMs, minTime);
                String time = TimeUtil.format(TimeUtil.MMDD_HH_MM_SS_A, timeMs);
                body.append("\n- " + PW.getMarkdownUrl(bounty.getNation_id(), false) + ": `$" + MathMan.format(bounty.getAmount()) + "` - " + time);
            }
            body.append("\n<" + Settings.PNW_URL() + "/world/bounties/>\n\n**Active Nations**:\n");
            for (Nation nation : active) {
                int id = nation.getId();
                long activeMs = start - nation.getLast_active().toEpochMilli();
                if (activeMs < minTime - 15000) continue;
                String activeStr = TimeUtil.format(TimeUtil.MMDD_HH_MM_SS_A, activeMs);
                body.append("- " + PW.getMarkdownUrl(id, false) + " | ");
                DBNation dbNation = DBNation.getById(id);
                int allianceId = dbNation == null ? 0 : dbNation.getAlliance_id();
                body.append(PW.getMarkdownUrl(allianceId, true) + " | ");
                body.append(activeStr + "\n");
            }
            body.append("\n\n-# Note: Nations active **BEFORE** the bounty datetime listed here are improbable");
            try {
                new DiscordChannelIO(channel).send("**__" + title + "__**\n" + body);
            } catch (InsufficientPermissionException permE) {
                db.deleteInfo(GuildKey.ESPIONAGE_ALERT_CHANNEL);
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
    }

    public void updateCasualties(Nation nation, long timestamp, boolean isGlobal) {
        DBNation nationLocal = null;
        double score = nation.getScore();
        Integer spies = nation.getSpies();
        if (spies == null) {
            if (nationLocal == null) nationLocal = DBNation.getOrCreate(nation.getId());
            spies = nationLocal.getSpies();
        }
        // missiles
        if (nation.getMissile_casualties() != null && nation.getMissile_kills() != null && nation.getMissiles() != null) {
            addStat(nation.getId(), MilitaryUnit.MISSILE, nation.getMissile_kills(), nation.getMissile_casualties(), nation.getMissiles(), spies, timestamp, score);
        }
        // nukes
        if (nation.getNuke_casualties() != null && nation.getNuke_kills() != null && nation.getNukes() != null) {
            addStat(nation.getId(), MilitaryUnit.NUKE, nation.getNuke_kills(), nation.getNuke_casualties(), nation.getNukes(), spies, timestamp, score);
        }
        // soldiers
        if (nation.getSoldier_casualties() != null && nation.getSoldier_kills() != null && nation.getSoldiers() != null) {
            addStat(nation.getId(), MilitaryUnit.SOLDIER, nation.getSoldier_kills(), nation.getSoldier_casualties(), nation.getSoldiers(), spies, timestamp, score);
        }
        // tanks
        if (nation.getTank_casualties() != null && nation.getTank_kills() != null && nation.getTanks() != null) {
            addStat(nation.getId(), MilitaryUnit.TANK, nation.getTank_kills(), nation.getTank_casualties(), nation.getTanks(), spies, timestamp, score);
        }
        // aircraft
        if (nation.getAircraft_casualties() != null && nation.getAircraft_kills() != null && nation.getAircraft() != null) {
            addStat(nation.getId(), MilitaryUnit.AIRCRAFT, nation.getAircraft_kills(), nation.getAircraft_casualties(), nation.getAircraft(), spies, timestamp, score);
        }
        // ships
        if (nation.getShip_casualties() != null && nation.getShip_kills() != null && nation.getShips() != null) {
            addStat(nation.getId(), MilitaryUnit.SHIP, nation.getShip_kills(), nation.getShip_casualties(), nation.getShips(), spies, timestamp, score);
        }
        // spies
        if (nation.getSpy_kills() != null && nation.getSpy_casualties() != null && nation.getSpies() != null) {
            addStat(nation.getId(), MilitaryUnit.SPIES, nation.getSpy_kills(), nation.getSpy_casualties(), spies, spies, timestamp, score);
        }
    }
}