package link.locutus.discord.commands.war;

import link.locutus.discord.util.RateLimitedSources;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.Logg;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.bindings.PlaceholderCache;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.NationDB;
import link.locutus.discord.db.entities.*;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.scheduler.KeyValue;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;

public class RaidCommand extends Command {
    private static final int MAX_RAID_INACTIVE_MINUTES = 385920;
    private static final int RAID_ACTIVITY_WINDOW_TURNS = 24;
    private static final long RAID_ACTIVITY_LOOKBACK_TURNS = 70L * 12L;
    private static final double RAID_LOOT_PCT = 0.14;
    private static final long RAID_TIMING_LOG_THRESHOLD_NS = TimeUnit.MILLISECONDS.toNanos(25);
    private static final int RAID_TIMING_LOG_INPUT_THRESHOLD = 250;
    private static final int RAID_TIMING_LOG_RISK_THRESHOLD = 25;

    public RaidCommand() {
        super("raid", CommandCategory.MILCOM, CommandCategory.GAME_INFO_AND_TOOLS, CommandCategory.MEMBER);
    }

    @Override
    public List<CommandRef> getSlashReference() {
        return List.of(CM.war.find.raid.cmd);
    }

    @Override
    public String help() {
        return "!raid [alliance|coalition|*] [options...]";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        DBNation nation = DiscordUtil.getNation(user);
        if (nation == null) return false;
        return Roles.APPLICANT.has(user, server) || Roles.MEMBER.has(user, server);
    }

    @Override
    public String desc() {
        return """
                Find a raid target, with optional alliance and sorting (default: inactive None or applicants, sorted by estimated monetary value).
                To see a list of coalitions, use `!coalitions`.
                To get more than 5 results (e.g. 10 results): `!raid * 10`
                To list active nations use `!raid * -a`
                To instead list inactives for X time, add `-3d` (change 3d to the time)
                To filter out the top X alliances, add e.g. `-50`
                To list only weak neations, add `-weak`
                To also list nations on beige use e.g. `-beige` or `-beige<24` (for <24 turns of beige)
                To use a custom score, add e.g. `score=1500`
                To only list by free defensive slots, add e.g. `slots=0`
                To also list nations in vm, add e.g. `vm<3d`
                To ignore the Do Not Raid list use `-donotraid`
                Add `-l` to ignore bank loot
                Add `-c` to ignore city revenue""";
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (me == null || me.hasUnsetMil()) {
            return "Invalid nation? Are you sure you are registered? " + author.getAsMention();
        }
        return onCommand(args, flags, me, guild, author, channel);
    }

    public String onCommand(List<String> args, Set<Character> flags, DBNation me, Guild guild, User user, IMessageIO channel) throws ExecutionException, InterruptedException {
        GuildDB db = Locutus.imp().getGuildDB(guild);
        Set<Integer> enemyAAs = db.getCoalition("enemies");

        int results = 5;

        String aa = null;

        Set<Integer> ignoreAlliances = new IntOpenHashSet();
        ignoreAlliances.add(Settings.INSTANCE.ALLIANCE_ID());
        boolean includeAlliances = false;

        double minLoot = Double.NEGATIVE_INFINITY;
        double score = me.getScore();
        long minutesInactive = 10000;
        boolean weakground = false;
        boolean beige = false;
        int beigeTurns = -1;
        int vm = 0;
        boolean active = flags.contains('a');
        boolean useDnr = true;
        int slots = -1;
        boolean dms = flags.contains('d');
        boolean force = flags.contains('f');
        boolean ignoreBank = flags.contains('l');
        boolean ignoreCity = flags.contains('c');
        Iterator<String> iterator = args.iterator();
        while (iterator.hasNext()) {
            String next = iterator.next();
            if (next.equalsIgnoreCase("-a")) {
                active = true;
                iterator.remove();
            } else if (next.equalsIgnoreCase("-f")) {
                force = true;
                iterator.remove();
            } else if (next.equalsIgnoreCase("-l")) {
                ignoreBank = true;
                iterator.remove();
            } else if (next.equalsIgnoreCase("-c")) {
                ignoreCity = true;
                iterator.remove();
            } else if ((next.charAt(0) == '-' || next.charAt(0) == '+') && MathMan.isInteger(next.substring(1))) {
                int topX = Integer.parseInt(next.substring(1));
                for (int allianceId : Locutus.imp().getNationDB().getAllianceIdsRankedByScore(topX)) {
                    Map<Integer, Treaty> treaties = Locutus.imp().getNationDB().getTreaties(allianceId);
                    for (Map.Entry<Integer, Treaty> aaTreatyEntry : treaties.entrySet()) {
                        switch (aaTreatyEntry.getValue().getType()) {
                            case EXTENSION:
                            case MDP:
                            case MDOAP:
                            case PROTECTORATE:
                                ignoreAlliances.add(aaTreatyEntry.getKey());
                        }
                    }
                    ignoreAlliances.add(allianceId);
                }
                includeAlliances = next.charAt(0) == '+';
                iterator.remove();
            } else if (next.startsWith("score=")) {
                score = MathMan.parseDouble(next.split("=")[1]);
                iterator.remove();
            } else if (next.equalsIgnoreCase("-d")) {
                dms = true;
                iterator.remove();
            } else if (next.startsWith("slots=")) {
                slots = MathMan.parseInt(next.split("=")[1]);
                iterator.remove();
            } else if (next.charAt(0) == '-' && MathMan.isInteger(next.charAt(1) + "")) {
                minutesInactive = TimeUnit.SECONDS.toMinutes(TimeUtil.timeToSec(next.substring(1)));
                iterator.remove();
            } else if (MathMan.isInteger(next)) {
                results = Math.min(25, Integer.parseInt(next));
                iterator.remove();
            } else if (next.equalsIgnoreCase("-weak")) {
                weakground = true;
                iterator.remove();
            } else if (next.equalsIgnoreCase("-dnr")) {
                channel.sendMessage("**WARNING: VIOLATING THE DO NOT RAID LIST IS PROHIBITED**. It has been changed to `-donotraid`. Use only if you are sure", RateLimitedSources.COMMAND_RESULT);
            } else if (next.equalsIgnoreCase("-donotraid")) {
                channel.sendMessage("**WARNING: VIOLATING THE DO NOT RAID LIST IS PROHIBITED**. Only use `-donotraid` if you aree sure it is okay to violate.", RateLimitedSources.COMMAND_RESULT);
                useDnr = false;
                iterator.remove();
            } else if (next.startsWith("-beige") || next.startsWith("beige")) {
                beige = true;
                String[] split = next.split("[<=]+");
                if (split.length == 2) {
                    beigeTurns = Integer.parseInt(split[1]);
                    if (next.contains("=")) beigeTurns++;
                }
                iterator.remove();
            } else if (next.startsWith("-vm")) {
                String[] split = next.split("<");
                if (split.length == 2) {
                    if (MathMan.isInteger(split[1])) {
                        vm = Integer.parseInt(split[1]) - 1;
                    } else {
                        vm = (int) (TimeUtil.timeToSec(split[1]) * 2 / (60 * 60));
                    }
                } else {
                    vm = Integer.MAX_VALUE;
                }
                iterator.remove();
            } else if (next.startsWith("loot>")) {
                minLoot = MathMan.parseDouble(next.split(">")[1]);
                iterator.remove();
            }
        }

        if (beigeTurns > 0) vm = Math.max(vm, beigeTurns);

        Set<DBNation> nations;
        double minScore = score * PW.WAR_RANGE_MIN_MODIFIER;
        double maxScore = score * PW.WAR_RANGE_MAX_MODIFIER;
        final int candidateVm = vm;
        final int candidateSlots = slots;
        final boolean candidateBeige = beige;
        final boolean candidateActive = active;
        final long candidateMinutesInactive = minutesInactive;
        final int candidateBeigeTurns = beigeTurns;
        Predicate<DBNation> wideCandidateFilter = enemy -> passesWideCandidatePrefilter(enemy, minScore, maxScore,
            candidateVm, candidateSlots, candidateBeige, candidateActive, candidateMinutesInactive, candidateBeigeTurns);
        NationDB nationDb = Locutus.imp().getNationDB();

        switch (args.size()) {
            default:
//                return "Usage: `!raid [alliance|coalition|none] [max]";
            case 2:
            case 1:
                aa = args.get(0);
            case 0:
                if (aa == null) {
                    nations = nationDb.getNationsMatching(enemy -> enemy.getPosition() <= 1 && wideCandidateFilter.test(enemy));
                    for (DBNation enemy : nationDb.getNationsByAlliance(enemyAAs)) {
                        if (wideCandidateFilter.test(enemy)) {
                            nations.add(enemy);
                        }
                    }
                } else if (aa.equalsIgnoreCase("*")) {
                    nations = nationDb.getNationsMatching(wideCandidateFilter);
                } else {
                    double min = PW.getAttackRange(true, true, true, score);
                    double max = PW.getAttackRange(true, true, false, score);
                    String arg = "#score>" + min + ",#score<" + max + "," + aa;
                    if (!beige) arg = "#isbeige=0," + arg;
                    if (slots == -1) arg = "#def<3," + arg;
                    nations = DiscordUtil.parseNations(guild, user, me, arg, false, false);
                }
        }

        nations.removeIf(DBNation::hasUnsetMil);
        if (nations.isEmpty()) {
            return "Invalid AA or Coalition (case sensitive): " + aa + ". @see also: `!coalitions`";
        }

        return onCommand2(channel, user, db, me, nations, weakground, dms, vm, slots, beige, useDnr, ignoreAlliances, includeAlliances, active, minutesInactive, score, minLoot, beigeTurns, ignoreBank, ignoreCity, results);
    }

    public String onCommand2(IMessageIO channel, User user, GuildDB db, DBNation me, Set<DBNation> nations, boolean weakground,
                             boolean dms, int vm, int slots, boolean beige,
                             boolean useDnr, Set<Integer> ignoreAlliances, boolean includeAlliances, boolean active,
                             long minutesInactive, double score, double minLoot, int beigeTurns, boolean ignoreBank, boolean ignoreCity, int numResults) throws ExecutionException, InterruptedException {
        if (dms && user != null) {
            channel = DiscordChannelIO.privateOutput(user, RateLimitedSources.COMMAND_RESULT);
        }
        CompletableFuture<IMessageBuilder> msgFuture = channel.sendMessage("Please wait...", RateLimitedSources.COMMAND_RESULT);

        List<Map.Entry<DBNation, Map.Entry<Double, Double>>> nationNetValues = getNations(db, me, nations, weakground, vm, slots, beige, useDnr, ignoreAlliances, includeAlliances, active, minutesInactive, score, minLoot, beigeTurns, ignoreBank, ignoreCity, numResults);

        if (nationNetValues.isEmpty()) {
            channel.sendMessage("No results. Try using " + CM.war.find.raid.cmd.targets("*").numResults("15") + " or " +
                    CM.war.find.raid.cmd.targets("*").numResults("15").beigeTurns("10")
                    +" (and plan raids out). Ping milcom for (assistance", RateLimitedSources.COMMAND_RESULT);
            return null;
        }

        StringBuilder response = new StringBuilder("**Results for " + me.getNation() + "**:");
        for (Map.Entry<DBNation, Map.Entry<Double, Double>> entry : nationNetValues) {
            DBNation nation = entry.getKey();
            // formatter.format(entry.getValue()) // net loot

            Map.Entry<Double, Double> valueEst = entry.getValue();
            String moneyStr = "\nweighted $" + MathMan.formatSig(valueEst.getKey()) + "/actual $" + MathMan.formatSig(valueEst.getValue());
            response.append(moneyStr + " | " + nation.toMarkdown(true, true, true, false, false));
        }

        IMessageIO finalChannel = channel;
        Locutus.imp().runBackgroundAsync(() -> {
            try {
                IMessageBuilder msg = msgFuture.get();
                if (msg != null && msg.getId() > 0) {
                    finalChannel.delete(msg.getId(), RateLimitedSources.COMMAND_PROGRESS);
                }
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        });

        if (true) {
            StringBuilder warnings = new StringBuilder();
            if (me.getAvg_infra() > 1500 && me.getCities() > 7) {
                warnings.append("- You have high infra (>1500) which will likely be lost to counters.\n");
            }
            if (me.getSoldiers() == 0) {
                warnings.append("- You do not have any soldiers, which should be used for raiding as other units aren't cost effective.\n");
            }
            if (me.getTanks() != 0) {
                warnings.append("- We don't recommend raiding with tanks because they are unable to loot nations with any cost efficiency.\n");
            }
            if (beige) {
                warnings.append("- Set a reminder for yourself to hit nations on beige.\n");
            }
            if (me.getWarPolicy() != WarPolicy.PIRATE) {
                warnings.append("- Using the pirate policy will increase loot by 40%\n");
            }
            if (warnings.length() != 0) {
                response.append("\n```").append(warnings.toString().trim()).append("```");
            }
        }

        if (beige) {
            me.setMeta(NationMeta.INTERVIEW_RAID_BEIGE, (byte) 1);
        }

        channel.send(response.toString(), RateLimitedSources.COMMAND_RESULT);
        return null;
    }

    private static final class RaidCandidate implements Comparable<RaidCandidate> {
        private final DBNation enemy;
        private final double upperBound;
        private final double bankLootEst;
        private final double enemyGroundStrength;
        private final long enemyMilitaryValue;
        private final boolean riskEligible;

        private RaidCandidate(DBNation enemy, double upperBound, double bankLootEst,
                              double enemyGroundStrength, long enemyMilitaryValue, boolean riskEligible) {
            this.enemy = enemy;
            this.upperBound = upperBound;
            this.bankLootEst = bankLootEst;
            this.enemyGroundStrength = enemyGroundStrength;
            this.enemyMilitaryValue = enemyMilitaryValue;
            this.riskEligible = riskEligible;
        }

        @Override
        public int compareTo(RaidCandidate other) {
            return Double.compare(other.upperBound, upperBound);
        }
    }

    private static final class RaidTiming {
        private final int inputCandidates;
        private int filteredCandidates;
        private int upperBoundCandidates;
        private int riskPool;
        private int riskEvaluated;
        private int resultCount;
        private long filterNs;
        private long beigeLootNs;
        private long revenueNs;
        private long bankNs;
        private long bountyNs;
        private long activityNs;
        private long counterNs;
        private long upperBoundOrderNs;
        private long finalSortNs;
        private long totalNs;

        private RaidTiming(int inputCandidates) {
            this.inputCandidates = inputCandidates;
        }

        private boolean shouldLog() {
            return totalNs >= RAID_TIMING_LOG_THRESHOLD_NS
                    || inputCandidates >= RAID_TIMING_LOG_INPUT_THRESHOLD
                    || riskEvaluated >= RAID_TIMING_LOG_RISK_THRESHOLD;
        }

        private void log() {
            Logg.text("[RaidCommand.getNations] input=" + inputCandidates
                    + " filtered=" + filteredCandidates
                    + " upperBounds=" + upperBoundCandidates
                    + " riskPool=" + riskPool
                    + " riskEvaluated=" + riskEvaluated
                    + " results=" + resultCount
                    + " filter=" + formatMillis(filterNs)
                    + " beigeLoot=" + formatMillis(beigeLootNs)
                    + " revenue=" + formatMillis(revenueNs)
                    + " bank=" + formatMillis(bankNs)
                    + " bounty=" + formatMillis(bountyNs)
                    + " activity=" + formatMillis(activityNs)
                    + " counter=" + formatMillis(counterNs)
                    + " order=" + formatMillis(upperBoundOrderNs)
                    + " finalSort=" + formatMillis(finalSortNs)
                    + " total=" + formatMillis(totalNs));
        }
    }

    static double applyBounties(DBNation me, double value, List<DBBounty> natBounties) {
        if (natBounties == null || natBounties.isEmpty()) {
            return value;
        }

        Map<WarType, Double> totals = new EnumMap<>(WarType.class);
        totals.put(WarType.RAID, value);
        totals.put(WarType.ORD, value * 0.5);
        totals.put(WarType.ATT, value * 0.25);

        for (DBBounty bounty : natBounties) {
            if (bounty.getType() == WarType.NUCLEAR) {
                if (me.getNukes() > 0) {
                    long amount = bounty.getAmount() - 1_000_000L;
                    for (WarType type : WarType.values) {
                        if (type == WarType.NUCLEAR) {
                            continue;
                        }
                        totals.put(type, totals.getOrDefault(type, 0d) + amount);
                    }
                }
                continue;
            }
            totals.put(bounty.getType(), totals.getOrDefault(bounty.getType(), 0d) + bounty.getAmount());
        }

        double bestValue = value;
        for (double total : totals.values()) {
            bestValue = Math.max(bestValue, total);
        }
        return bestValue;
    }

    private static boolean isRiskEligible(DBNation enemy, Set<Integer> enemyAAs) {
        return enemy.active_m() < 10000 && enemy.getAlliance_id() != 0 && !enemyAAs.contains(enemy.getAlliance_id());
    }

    private static double estimateAllianceBankLoot(LootEntry aaLoot, double attackerScore, double allianceScore, double foodFactor) {
        if (aaLoot == null || allianceScore <= 0) {
            return 0;
        }

        double ratio = ((attackerScore * 10000) / allianceScore) / 2d;
        double percent = Math.min(Math.min(ratio, 10000) / 30000, 0.33);
        double convertedTotal = 0;
        for (ResourceType type : ResourceType.values) {
            double amt = aaLoot.getTotal_rss()[type.ordinal()];
            if (amt > 0) {
                convertedTotal += ResourceType.convertedTotal(type, type == ResourceType.FOOD ? amt * foodFactor : amt);
            }
        }
        return convertedTotal * percent;
    }

    private static String formatMillis(long nanos) {
        return MathMan.format(nanos / 1_000_000d) + "ms";
    }

    private static boolean passesWideCandidatePrefilter(DBNation enemy, double minScore, double maxScore,
                                                        int vm, int slots, boolean beige,
                                                        boolean active, long minutesInactive, int beigeTurns) {
        if (enemy.hasUnsetMil()) {
            return false;
        }
        if (enemy.active_m() > MAX_RAID_INACTIVE_MINUTES) {
            return false;
        }
        if (enemy.getScore() >= maxScore || enemy.getScore() <= minScore) {
            return false;
        }
        if (enemy.getVm_turns() > vm) {
            return false;
        }
        if ((slots == -1 && enemy.getDef() >= 3) || (slots != -1 && 3 - enemy.getDef() != slots)) {
            return false;
        }
        if (!beige && enemy.isBeige()) {
            return false;
        }
        if (enemy.isBeige() && beigeTurns != -1 && beigeTurns != Integer.MAX_VALUE && enemy.getBeigeTurns() >= beigeTurns) {
            return false;
        }
        return active || enemy.active_m() >= minutesInactive;
    }

    public static List<Map.Entry<DBNation, Map.Entry<Double, Double>>> getNations(GuildDB db, DBNation me, Set<DBNation> nations, boolean weakground,
                                                                           int vm, int slots, boolean beige,
                                                                           boolean useDnr, Set<Integer> ignoreAlliances, boolean includeAlliances, boolean active,
                                                                           long minutesInactive, double score, double minLoot, int beigeTurns, boolean ignoreBank, boolean ignoreCity, int numResults) {
        if (numResults <= 0) {
            return Collections.emptyList();
        }
        if (me == null || me.hasUnsetMil()) {
            return Collections.emptyList();
        }

        long totalStartNs = System.nanoTime();
        RaidTiming timing = new RaidTiming(nations.size());

        Set<Integer> enemyAAs = db == null ? Collections.emptySet() : db.getCoalition("enemies");
        Function<DBNation, Boolean> canRaidDNR = db == null ? f -> true : db.getCanRaid();

        double myGroundStrong = me.getGroundStrength(true, true);
        double myGroundAggressive = me.getGroundStrength(true, false);
        double myGroundDefensive = me.getGroundStrength(false, true);
        int myShips = me.getShips();
        int myAircraft = me.getAircraft();
        long myMilValue = me.militaryValue(null, false);

        long filterStartNs = System.nanoTime();

        if (weakground) {
            nations.removeIf(f -> f.getGroundStrength(true, true) > myGroundStrong * 0.4);
        }

        nations.removeIf(nation ->
                nation.active_m() < 12000
                        && nation.getGroundStrength(true, false) > myGroundAggressive
                        && nation.getAircraft() > myAircraft
                        && nation.getShips() > myShips + 2);

        double minScore = score * PW.WAR_RANGE_MIN_MODIFIER;
        double maxScore = score * PW.WAR_RANGE_MAX_MODIFIER;

        Set<Integer> attackedDefenderIds = new IntOpenHashSet();
        for (DBWar war : Locutus.imp().getWarDb().getWarsByNation(me.getNation_id(), WarStatus.ACTIVE)) {
            attackedDefenderIds.add(war.getDefender_id());
        }

        ArrayList<DBNation> enemies = new ArrayList<>();
        Set<Integer> enemyIds = new IntOpenHashSet();
        Set<Integer> allianceIds = new IntOpenHashSet();
        for (DBNation enemy : nations) {
            if (!passesWideCandidatePrefilter(enemy, minScore, maxScore, vm, slots, beige, active, minutesInactive, beigeTurns)) continue;
            if (useDnr && !canRaidDNR.apply(enemy)) continue;
            if ((ignoreAlliances.contains(enemy.getAlliance_id()) != includeAlliances) && (includeAlliances || enemy.getPosition() > 1)) continue;
            if (attackedDefenderIds.contains(enemy.getNation_id())) continue;

            enemies.add(enemy);
            enemyIds.add(enemy.getNation_id());
            if (!ignoreBank && enemy.getAlliance_id() != 0 && enemy.getPositionEnum() != Rank.APPLICANT) {
                allianceIds.add(enemy.getAlliance_id());
            }
        }

        timing.filterNs = System.nanoTime() - filterStartNs;
        timing.filteredCandidates = enemies.size();
        if (enemies.isEmpty()) {
            timing.totalNs = System.nanoTime() - totalStartNs;
            if (timing.shouldLog()) {
                timing.log();
            }
            return Collections.emptyList();
        }

        double infraCost = PW.City.Infra.calculateInfra(me.getAvg_infra(), me.getAvg_infra() - me.getAvg_infra() * 0.05) * me.getCities();
        double foodFactor = db != null && db.getOffshore() == Locutus.imp().getRootBank() ? 2 : 1;
        long turn = TimeUtil.getTurn();

        ValueStore cacheStore = PlaceholderCache.createIsolatedCache(enemies, DBNation.class);
        List<Map.Entry<DBNation, Double>> baseLootValues = new ObjectArrayList<>(enemies.size());
        for (DBNation enemy : enemies) {
            double lootValue = 0;
            LootEntry loot;

            long beigeLootStartNs = System.nanoTime();
            loot = enemy.getBeigeLoot(cacheStore);
            if (loot != null) {
                double[] total = loot.getTotal_rss();
                for (ResourceType type : ResourceType.values) {
                    double amt = total[type.ordinal()];
                    if (amt > 0) {
                        lootValue += ResourceType.convertedTotal(type, type == ResourceType.FOOD ? amt * foodFactor : amt);
                    }
                }
            }
            timing.beigeLootNs += System.nanoTime() - beigeLootStartNs;

            long revenueStartNs = System.nanoTime();
            double revenueEst = 0;
            if (!ignoreCity) {
                long turnInactive = TimeUtil.getTurn(enemy.lastActiveMs());
                if (loot != null) {
                    long lootTurn = TimeUtil.getTurn(loot.getDate());
                    if (lootTurn > turnInactive) {
                        turnInactive = lootTurn;
                    }
                }
                long turnEntered = enemy.getEntered_vm();
                long turnEnded = enemy.getLeaving_vm();

                if (enemy.getVm_turns() > 0) {
                    if (turnEntered > turnInactive) {
                        turnInactive = turn - (turnEntered - turnInactive);
                    } else {
                        turnInactive = turn;
                    }
                } else if (turnEnded > turnInactive) {
                    turnInactive = turnEnded;
                }
                int turnsInactive = (int) (turn - turnInactive);

                if (turnsInactive > 0) {
                    double[] revenue = enemy.getRevenue(null, turnsInactive + 24, true, true, false, true, false, false, 0d, false);
                    if (loot != null) {
                        if (revenue[ResourceType.FOOD.ordinal()] > 0) {
                            revenue[ResourceType.FOOD.ordinal()] *= foodFactor;
                        }
                        revenue = PW.capManuFromRaws(revenue, loot.getTotal_rss());
                    }
                    revenueEst = ResourceType.convertedTotal(revenue);
                }
            }
            timing.revenueNs += System.nanoTime() - revenueStartNs;

            baseLootValues.add(new KeyValue<>(enemy, (lootValue + revenueEst) * RAID_LOOT_PCT * enemy.lootModifier()));
        }

        long bankStartNs = System.nanoTime();
        Map<Integer, LootEntry> allianceLootByAllianceId = ignoreBank
                ? Collections.emptyMap()
                : Locutus.imp().getNationDB().getAllianceLootMap(allianceIds);
        timing.bankNs += System.nanoTime() - bankStartNs;

        long bountyStartNs = System.nanoTime();
        Map<Integer, List<DBBounty>> bountiesByNationId = Locutus.imp().getWarDb().getBountiesByNationIds(enemyIds);
        timing.bountyNs += System.nanoTime() - bountyStartNs;

        Map<Integer, Double> allianceScores = new HashMap<>();
        List<RaidCandidate> candidateList = new ObjectArrayList<>(baseLootValues.size());
        Set<Integer> riskNationIds = new IntOpenHashSet();
        for (Map.Entry<DBNation, Double> baseEntry : baseLootValues) {
            DBNation enemy = baseEntry.getKey();
            double value = baseEntry.getValue();

            long bankCalcStartNs = System.nanoTime();
            double bankLootEst = 0;
            if (!ignoreBank && enemy.getPositionEnum() != Rank.APPLICANT) {
                LootEntry allianceLoot = allianceLootByAllianceId.get(enemy.getAlliance_id());
                if (allianceLoot != null) {
                    Double allianceScore = allianceScores.get(enemy.getAlliance_id());
                    if (allianceScore == null) {
                        allianceScore = DBAlliance.getOrCreate(enemy.getAlliance_id()).getScore();
                        allianceScores.put(enemy.getAlliance_id(), allianceScore);
                    }
                    bankLootEst = estimateAllianceBankLoot(allianceLoot, score, allianceScore, foodFactor);
                    value += bankLootEst;
                }
            }
            timing.bankNs += System.nanoTime() - bankCalcStartNs;

            long bountyCalcStartNs = System.nanoTime();
            value = applyBounties(me, value, bountiesByNationId.get(enemy.getNation_id()));
            timing.bountyNs += System.nanoTime() - bountyCalcStartNs;

            if (value <= minLoot) {
                continue;
            }

            boolean riskEligible = isRiskEligible(enemy, enemyAAs);
            if (riskEligible) {
                riskNationIds.add(enemy.getNation_id());
            }

            candidateList.add(new RaidCandidate(
                    enemy,
                    value,
                    bankLootEst,
                    riskEligible ? enemy.getGroundStrength(true, false) : 0,
                    riskEligible ? enemy.militaryValue(null, false) : 0,
                    riskEligible));
        }

        timing.upperBoundCandidates = candidateList.size();
        timing.riskPool = riskNationIds.size();
        if (candidateList.isEmpty()) {
            timing.totalNs = System.nanoTime() - totalStartNs;
            if (timing.shouldLog()) {
                timing.log();
            }
            return Collections.emptyList();
        }

        long activityStartTurn = turn - RAID_ACTIVITY_LOOKBACK_TURNS;
        long activityLoadStartNs = System.nanoTime();
        Map<Integer, Set<Long>> riskActivityTurns = riskNationIds.isEmpty()
                ? Collections.emptyMap()
                : Locutus.imp().getNationDB().getActivityByTurn(activityStartTurn, turn, riskNationIds);
        timing.activityNs += System.nanoTime() - activityLoadStartNs;

        long counterLoadStartNs = System.nanoTime();
        Set<Integer> nationsWithWars = riskNationIds.isEmpty()
                ? Collections.emptySet()
                : Locutus.imp().getWarDb().getNationIdsWithWars(riskNationIds);
        Map<Integer, DBWar> lastDefensiveWars = riskNationIds.isEmpty()
                ? Collections.emptyMap()
                : Locutus.imp().getWarDb().getLastDefensiveWarsByNationIds(riskNationIds);
        List<DBWar> activeLastWars = new ObjectArrayList<>();
        for (DBWar lastWar : lastDefensiveWars.values()) {
            if (lastWar.getStatus() == WarStatus.ACTIVE) {
                activeLastWars.add(lastWar);
            }
        }
        Map<Integer, CounterStat> counterStatsByWarId = activeLastWars.isEmpty()
                ? Collections.emptyMap()
                : Locutus.imp().getWarDb().getCounterStatsByWarIds(activeLastWars);
        timing.counterNs += System.nanoTime() - counterLoadStartNs;

        long orderStartNs = System.nanoTime();
        PriorityQueue<RaidCandidate> candidatesByUpperBound = new PriorityQueue<>(candidateList);
        timing.upperBoundOrderNs = System.nanoTime() - orderStartNs;

        Map<Integer, Activity> activityCache = new HashMap<>();
        PriorityQueue<Map.Entry<DBNation, Map.Entry<Double, Double>>> topResults =
                new PriorityQueue<>(numResults, Comparator.comparingDouble(entry -> entry.getValue().getKey()));

        while (!candidatesByUpperBound.isEmpty()) {
            RaidCandidate candidate = candidatesByUpperBound.poll();
            if (topResults.size() >= numResults && candidate.upperBound <= topResults.peek().getValue().getKey()) {
                break;
            }

            DBNation enemy = candidate.enemy;
            double value = candidate.upperBound;
            double winChance = 1;
            double costIncurred = 0;
            double activeChance = enemy.active_m() < 10000 ? (enemy.active_m() < 3000 ? 1 : 0.5) : 0;

            if (candidate.riskEligible) {
                timing.riskEvaluated++;

                long activityEvalStartNs = System.nanoTime();
                Activity activity = activityCache.computeIfAbsent(enemy.getNation_id(), ignored ->
                    Activity.fromTurns(riskActivityTurns.getOrDefault(enemy.getNation_id(), Collections.emptySet()), activityStartTurn, turn));
                activeChance = activity.loginChance(RAID_ACTIVITY_WINDOW_TURNS, true);
                timing.activityNs += System.nanoTime() - activityEvalStartNs;

                if (myShips <= enemy.getShips()) {
                    value -= Math.max(0, value - candidate.bankLootEst) * 0.75 * activeChance;
                } else {
                    value -= Math.max(0, value - candidate.bankLootEst) * 0.25 * activeChance;
                }

                long minMilValue = (long) (Math.min(myMilValue, candidate.enemyMilitaryValue) * 0.5);
                if (candidate.enemyGroundStrength > myGroundDefensive) {
                    winChance -= winChance * activeChance;
                }
                costIncurred += minMilValue * activeChance;

                long counterEvalStartNs = System.nanoTime();
                DBWar lastWar = lastDefensiveWars.get(enemy.getNation_id());
                if (lastWar != null) {
                    if (lastWar.getStatus() == WarStatus.ACTIVE) {
                        CounterStat counter = counterStatsByWarId.get(lastWar.warId);
                        if (counter != null && counter.type == CounterType.GETS_COUNTERED) {
                            costIncurred += Math.min(myMilValue, 1_000_000L * enemy.getCities());
                            if (candidate.enemyGroundStrength * 2 > myGroundDefensive) {
                                double ratio = candidate.enemyGroundStrength / myGroundDefensive;
                                winChance -= winChance * Math.min(0.8, ratio * activeChance);
                            }
                        }
                    }
                    if (lastWar.getStatus() == WarStatus.DEFENDER_VICTORY) {
                        winChance *= 0.5;
                    } else if (lastWar.getStatus() != WarStatus.ATTACKER_VICTORY) {
                        winChance *= 0.9;
                    }
                } else if (!nationsWithWars.contains(enemy.getNation_id())) {
                    winChance *= 0.8;
                }
                timing.counterNs += System.nanoTime() - counterEvalStartNs;
            }

            costIncurred += (1 - winChance) * infraCost;

            double finalValue = value * winChance - costIncurred;
            if (finalValue <= minLoot) {
                continue;
            }

            Map.Entry<DBNation, Map.Entry<Double, Double>> result = new KeyValue<>(enemy, new KeyValue<>(finalValue, candidate.upperBound));
            if (topResults.size() < numResults) {
                topResults.add(result);
            } else if (finalValue > topResults.peek().getValue().getKey()) {
                topResults.poll();
                topResults.add(result);
            }
        }

        long finalSortStartNs = System.nanoTime();
        List<Map.Entry<DBNation, Map.Entry<Double, Double>>> nationNetValues = new ObjectArrayList<>(topResults);
        nationNetValues.sort((o1, o2) -> Double.compare(o2.getValue().getKey(), o1.getValue().getKey()));
        timing.finalSortNs = System.nanoTime() - finalSortStartNs;

        timing.resultCount = nationNetValues.size();
        timing.totalNs = System.nanoTime() - totalStartNs;
        if (timing.shouldLog()) {
            timing.log();
        }

        return nationNetValues;
    }
}
