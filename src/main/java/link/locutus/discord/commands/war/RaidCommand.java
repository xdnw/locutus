package link.locutus.discord.commands.war;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.bindings.PlaceholderCache;
import link.locutus.discord.commands.manager.v2.builder.RankBuilder;
import link.locutus.discord.commands.manager.v2.builder.SummedMapRankBuilder;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
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
                Map<Integer, Double> aas = new RankBuilder<>(Locutus.imp().getNationDB().getAllNations()).group(DBNation::getAlliance_id).sumValues(DBNation::getScore).sort().get();
                for (Map.Entry<Integer, Double> entry : aas.entrySet()) {
                    if (entry.getKey() == 0) continue;
                    if (topX-- <= 0) break;
                    int allianceId = entry.getKey();
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
                channel.sendMessage("**WARNING: VIOLATING THE DO NOT RAID LIST IS PROHIBITED**. It has been changed to `-donotraid`. Use only if you are sure");
            } else if (next.equalsIgnoreCase("-donotraid")) {
                channel.sendMessage("**WARNING: VIOLATING THE DO NOT RAID LIST IS PROHIBITED**. Only use `-donotraid` if you aree sure it is okay to violate.");
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

        Set<DBNation> allNations = new ObjectLinkedOpenHashSet<>(Locutus.imp().getNationDB().getAllNations());
        Set<DBNation> nations;

        switch (args.size()) {
            default:
//                return "Usage: `!raid [alliance|coalition|none] [max]";
            case 2:
            case 1:
                aa = args.get(0);
            case 0:
                if (aa == null) {
                    nations = new ObjectLinkedOpenHashSet<>(allNations);
                    nations.removeIf(new Predicate<DBNation>() {
                        @Override
                        public boolean test(DBNation nation) {
                            return nation.getPosition() > 1;
                        }
                    });
                    nations.addAll(Locutus.imp().getNationDB().getNationsByAlliance(enemyAAs));
                } else if (aa.equalsIgnoreCase("*")) {
                    nations = new ObjectLinkedOpenHashSet<>(allNations);
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
            channel = new DiscordChannelIO(RateLimitUtil.complete(user.openPrivateChannel()));
        }
        CompletableFuture<IMessageBuilder> msgFuture = (channel.sendMessage("Please wait..."));

        List<Map.Entry<DBNation, Map.Entry<Double, Double>>> nationNetValues = getNations(db, me, nations, weakground, vm, slots, beige, useDnr, ignoreAlliances, includeAlliances, active, minutesInactive, score, minLoot, beigeTurns, ignoreBank, ignoreCity, numResults);

        if (nationNetValues.isEmpty()) {
            channel.sendMessage("No results. Try using " + CM.war.find.raid.cmd.targets("*").numResults("15") + " or " +
                    CM.war.find.raid.cmd.targets("*").numResults("15").beigeTurns("10")
                    +" (and plan raids out). Ping milcom for (assistance");
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
        Locutus.imp().getExecutor().submit(() -> {
            try {
                IMessageBuilder msg = msgFuture.get();
                if (msg != null && msg.getId() > 0) {
                    finalChannel.delete(msg.getId());
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

        channel.send(response.toString());
        return null;
    }

    public static List<Map.Entry<DBNation, Map.Entry<Double, Double>>> getNations(GuildDB db, DBNation me, Set<DBNation> nations, boolean weakground,
                                                                           int vm, int slots, boolean beige,
                                                                           boolean useDnr, Set<Integer> ignoreAlliances, boolean includeAlliances, boolean active,
                                                                           long minutesInactive, double score, double minLoot, int beigeTurns, boolean ignoreBank, boolean ignoreCity, int numResults) {
        Set<Integer> enemyAAs = db == null ? Collections.emptySet() : db.getCoalition("enemies");

        if (weakground) nations.removeIf(f -> f.getGroundStrength(true, true) > me.getGroundStrength(true, true) * 0.4);

        nations.removeIf(nation ->
                nation.active_m() < 12000 &&
                        nation.getGroundStrength(true, false) > me.getGroundStrength(true, false) &&
                        nation.getAircraft() > me.getAircraft() &&
                        nation.getShips() > me.getShips() + 2);




        double minScore = score * PW.WAR_RANGE_MIN_MODIFIER;
        double maxScore = score * PW.WAR_RANGE_MAX_MODIFIER;

        int count = 0;

        Function<DBNation, Boolean> canRaidDNR = db == null ? f -> true : db.getCanRaid();

        List<Map.Entry<DBNation, Map.Entry<Double, Double>>> nationNetValues = new ObjectArrayList<>();

        Map<Integer, Double> allianceScores = new HashMap<>();

        Set<DBWar> wars = Locutus.imp().getWarDb().getWarsByNation(me.getNation_id(), WarStatus.ACTIVE);
        Map<Integer, List<DBWar>> attackingWars = new RankBuilder<>(wars).group(DBWar::getDefender_id).get();

        Map<Integer, List<DBBounty>> allBounties = Locutus.imp().getWarDb().getBountiesByNation();

        // 38w4d
        ArrayList<DBNation> enemies = new ArrayList<>();
        for (DBNation enemy : nations) {
            if (enemy.active_m() > 385920) continue;
            if (enemy.getScore() >= maxScore || enemy.getScore() <= minScore) continue;
            if (enemy.getVm_turns() > vm) continue;
            if ((slots == -1 && enemy.getDef() >= 3) || (slots != -1 && 3 - enemy.getDef() != slots)) continue;
            if (enemy.hasUnsetMil()) continue;
            if (!beige && enemy.isBeige()) continue;
            if (useDnr && !canRaidDNR.apply(enemy)) continue;
//                    if (dnr.contains(enemy.getAlliance_id())) continue;
//                    if (enemy.active_m() < 10000 && dnr_active.contains(enemy.getAlliance_id())) continue;
//                    if ((enemy.active_m() < 10000 || enemy.getPosition() > 1) && dnr_member.contains(enemy.getAlliance_id())) continue;
            if ((ignoreAlliances.contains(enemy.getAlliance_id()) != includeAlliances) && (includeAlliances || enemy.getPosition() > 1))
                continue;
            if (attackingWars.containsKey(enemy.getNation_id())) continue;
            if (!active && enemy.active_m() < minutesInactive) continue;
            if (enemy.hasUnsetMil() || me.hasUnsetMil()) continue;
            enemies.add(enemy);
        }

        double infraCost = PW.City.Infra.calculateInfra(me.getAvg_infra(), me.getAvg_infra() - me.getAvg_infra() * 0.05) * me.getCities();

        double foodFactor = db != null && db.getOffshore() == Locutus.imp().getRootBank() ? 2 : 1;

        double lootPct = 0.14;

        Map<DBNation, Double> lootTotal = new HashMap<>();
        Map<DBNation, Double> lootEst = new HashMap<>();

        long turn = TimeUtil.getTurn();
        ValueStore<DBNation> cacheStore = PlaceholderCache.createCache(enemies, DBNation.class);
        for (DBNation enemy : enemies) {
            double value = 0;
            {
                LootEntry loot = enemy.getBeigeLoot(cacheStore);
                if (loot != null) {
                    double[] total = loot.getTotal_rss();
                    for (ResourceType type : ResourceType.values) {
                        double amt = total[type.ordinal()];
                        if (amt > 0) {
                            value += ResourceType.convertedTotal(type, type == ResourceType.FOOD ? amt * foodFactor : amt);
                        }
                    }
                }

                double revenueEst = 0;
                if (!ignoreCity) {
                    long turnInactive = TimeUtil.getTurn(enemy.lastActiveMs());
                    if (loot != null) {
                        long lootTurn = TimeUtil.getTurn(loot.getDate());
                        if (lootTurn > turnInactive) turnInactive = lootTurn;
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
                value += revenueEst;

                lootTotal.put(enemy, value);
                lootEst.put(enemy, value * lootPct * enemy.lootModifier());
            }
        }

        lootEst = new SummedMapRankBuilder<>(lootEst).sort().get();

        int i = 0;
        for (Map.Entry<DBNation, Double> lootEntry : lootEst.entrySet()) {
            DBNation enemy = lootEntry.getKey();
            double value = lootEntry.getValue();

            double bankLootEst = 0;

            if (!ignoreBank) {
                DBAlliance alliance = enemy.getAlliance();
                if (alliance != null && enemy.getPositionEnum() != Rank.APPLICANT) {
                    LootEntry aaLoot = alliance.getLoot();
                    if (aaLoot != null) {
                        Double allianceScore = allianceScores.get(enemy.getAlliance_id());
                        if (allianceScore == null) {
                            allianceScores.put(enemy.getAlliance_id(), allianceScore = alliance.getScore());
                        }

                        double ratio = ((score * 10000) / allianceScore) / 2d;
                        double percent = Math.min(Math.min(ratio, 10000) / 30000, 0.33);
                        double convertedTotal = 0;
                        for (ResourceType type : ResourceType.values) {
                            double amt = aaLoot.getTotal_rss()[type.ordinal()];
                            if (amt > 0) {
                                convertedTotal += ResourceType.convertedTotal(type, type == ResourceType.FOOD ? amt * foodFactor : amt);
                            }
                        }
                        bankLootEst = convertedTotal * percent;
                        value += bankLootEst;
                    }
                }
            }

            List<DBBounty> natBounties = allBounties.get(enemy.getNation_id());
            if (natBounties != null && !natBounties.isEmpty()) {
                Map<WarType, Double> total = new HashMap<>();
                for (WarType type : WarType.values) {
                    switch (type) {
                        case RAID:
                            total.put(type, value);
                            break;
                        case ORD:
                            total.put(type, value * 0.5);
                            break;
                        case ATT:
                            total.put(type, value * 0.25);
                            break;
                    }
                }
                for (DBBounty bounty : natBounties) {
                    if (bounty.getType() == WarType.NUCLEAR) {
                        if (me.getNukes() > 0) {
                            long amount = bounty.getAmount() - 1000000;
                            for (WarType type : WarType.values) {
                                if (type == WarType.NUCLEAR) continue;
                                total.put(type, total.getOrDefault(bounty.getType(), 0d) + amount);
                            }
                        }
                        continue;
                    }
                    total.put(bounty.getType(), total.getOrDefault(bounty.getType(), 0d) + bounty.getAmount());
                }
                for (Map.Entry<WarType, Double> entry : total.entrySet()) {
                    value = Math.max(value, entry.getValue());
                }
            }

            double originalValue = value;
            double winChance = 1;
            double costIncurred = 0;
            double activeChance = enemy.active_m() < 10000 ? (enemy.active_m() < 3000 ? 1 : 0.5) : 0;

            if (value <= minLoot) continue;

            double counterChance = -1;
            long myMilValue = me.militaryValue(null, false);

            if (enemy.active_m() < 10000 && enemy.getAlliance_id() != 0 && !enemyAAs.contains(enemy.getAlliance_id())) {
                int turns = 2 * 12;
                long startTurn = TimeUtil.getTurn() - 70 * 12;
                Activity activity = new Activity(enemy.getNation_id(), startTurn, Long.MAX_VALUE);
                activeChance = activity.loginChance(turns, true);
                if (me.getShips() <= enemy.getShips()) {
                    value -= Math.max(0, value - bankLootEst) * 0.75 * activeChance;
                } else {
                    value -= Math.max(0, value - bankLootEst) * 0.25 * activeChance;
                }

                long enemyMilValue = enemy.militaryValue(null, false);
                long minMilValue = (long) (Math.min(myMilValue, enemyMilValue) * 0.5);

                if (enemy.getGroundStrength(true, false) > me.getGroundStrength(false, true)) {
                    if (enemy.getGroundStrength(true, false) > me.getGroundStrength(true, true)) {
                        winChance -= winChance * activeChance;
                    } else {
                        winChance -= winChance * activeChance;
                    }
                }

                costIncurred += minMilValue * activeChance;
//
                Set<DBWar> enemyWars = Locutus.imp().getWarDb().getWarsByNation(enemy.getNation_id());
                if (!enemyWars.isEmpty()) {
                    DBWar lastWar = null;
                    for (DBWar war : enemyWars) {
                        if (war.getDefender_id() == enemy.getNation_id()) {
                            lastWar = war;
                            if (war.getStatus() != WarStatus.ACTIVE) {
                                break;
                            }
                            CounterStat counter = Locutus.imp().getWarDb().getCounterStat(lastWar);
                            if (counter != null && counter.type == CounterType.GETS_COUNTERED) {
                                counterChance = 1;
                                break;
                            }
                        }
                    }
                    if (lastWar != null) {
                        if (lastWar.getStatus() == WarStatus.DEFENDER_VICTORY) {
                            winChance *= 0.5;
                        } else if (lastWar.getStatus() != WarStatus.ATTACKER_VICTORY) {
                            winChance *= 0.9;
                        }
                    }
                } else {
                    winChance *= 0.8;
                }
            }

            if (counterChance != -1) {
                costIncurred += Math.min(myMilValue, 1000000 * enemy.getCities()) * counterChance;
                double enemyGS = enemy.getGroundStrength(true, false);
                double myGs = me.getGroundStrength(false, true);
                if (enemyGS * 2 > myGs) {
                    double ratio = enemyGS / myGs;
                    winChance = winChance - winChance * Math.min(0.8, ratio * counterChance * activeChance);
                }
            }

            costIncurred += (1 - winChance) * infraCost;

            value = value * winChance;
            value -= costIncurred;

            nationNetValues.add(new KeyValue<>(enemy, new KeyValue<>(value, originalValue)));
        }

        nationNetValues.sort((o1, o2) -> Double.compare(o2.getValue().getKey(), o1.getValue().getKey()));

        nationNetValues.removeIf(f -> f.getKey().isBeige() && beigeTurns != -1 && beigeTurns != Integer.MAX_VALUE && f.getKey().getBeigeTurns() >= beigeTurns);

        // cap nationNetValues at numResults
        if (nationNetValues.size() > numResults) {
            nationNetValues = nationNetValues.subList(0, numResults);
        }

        return nationNetValues;
    }
}
