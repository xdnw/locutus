package link.locutus.discord.commands.war;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.commands.rankings.builder.RankBuilder;
import link.locutus.discord.commands.rankings.builder.SummedMapRankBuilder;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.Activity;
import link.locutus.discord.db.entities.Coalition;
import link.locutus.discord.db.entities.CounterStat;
import link.locutus.discord.db.entities.CounterType;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBBounty;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.entities.LootEntry;
import link.locutus.discord.db.entities.NationMeta;
import link.locutus.discord.db.entities.Treaty;
import link.locutus.discord.db.entities.WarStatus;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.offshore.test.IACategory;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.apiv1.enums.TreatyType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.io.IOException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    public String help() {
        return "!raid [alliance|coalition|*] [options...]";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        DBNation nation = DiscordUtil.getNation(user);
        if (nation == null) return false;
        return Roles.MEMBER.has(user, server);
    }

    @Override
    public String desc() {
        return "Find a raid target, with optional alliance and sorting (default: inactive None or applicants, sorted by estimated monetary value).\n\t" +
                "To see a list of coalitions, use `!coalitions`.\n\t" +
                "To get more than 5 results (e.g. 10 results): `!raid * 10`\n\t" +
                "To list active nations use `!raid * -a`\n\t" +
                "To instead list inactives for X time, add `-3d` (change 3d to the time)\n\t" +
                "To filter out the top X alliances, add e.g. `-50`\n\t" +
                "To list only weak neations, add `-weak`\n\t" +
                "To also list nations on beige use e.g. `-beige` or `-beige<24` (for <24 turns of beige)\n\t" +
                "To use a custom score, add e.g. `score=1500`\n\t" +
                "To only list by free defensive slots, add e.g. `slots=0`\n\t" +
                "To also list nations in vm, add e.g. `vm<3d`\n\t" +
                "To ignore the Do Not Raid list use `-donotraid`\n\t" +
                "Add `-l` to ignore bank loot\n\t" +
                "Add `-c` to ignore city revenue";
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

        long start = System.currentTimeMillis();

        int results = 5;

        String aa = null;

        Set<Integer> ignoreAlliances = new HashSet<>();
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
                Map<Integer, Double> aas = new RankBuilder<>(Locutus.imp().getNationDB().getNations().values()).group(DBNation::getAlliance_id).sumValues(DBNation::getScore).sort().get();
                for (Map.Entry<Integer, Double> entry : aas.entrySet()) {
                    if (entry.getKey() == 0) continue;
                    if (topX-- <= 0) break;
                    int allianceId = entry.getKey();
                    Map<Integer, Treaty> treaties = Locutus.imp().getNationDB().getTreaties(allianceId);
                    for (Map.Entry<Integer, Treaty> aaTreatyEntry : treaties.entrySet()) {
                        switch (aaTreatyEntry.getValue().getType()) {
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

        System.out.println(((-start) + (start = System.currentTimeMillis())) + "ms (1)");

        Set<DBNation> allNations = new LinkedHashSet<>(Locutus.imp().getNationDB().getNations().values());
        Set<DBNation> nations;

        switch (args.size()) {
            default:
//                return "Usage: `!raid [alliance|coalition|none] [max]";
            case 2:
            case 1:
                aa = args.get(0);
            case 0:
                if (aa == null) {
                    nations = new LinkedHashSet<>(allNations);
                    nations.removeIf(new Predicate<DBNation>() {
                        @Override
                        public boolean test(DBNation nation) {
                            return nation.getPosition() > 1;
                        }
                    });
                    nations.addAll(Locutus.imp().getNationDB().getNations(enemyAAs));
                } else if (aa.equalsIgnoreCase("*")) {
                    nations = new LinkedHashSet<>(allNations);
                } else {
                    String arg = "#warrange=" + score + "," + aa;
                    if (!beige) arg = "#isbeige=0," + arg;
                    if (slots == -1) arg = "#def<3," + arg;
                    nations = DiscordUtil.parseNations(guild, arg);
                }
        }

        System.out.println(((-start) + (start = System.currentTimeMillis())) + "ms (3)");
        nations.removeIf(f -> f.hasUnsetMil());
        if (nations.isEmpty()) {
            return "Invalid AA or Coalition (case sensitive): " + aa + ". @see also: `!coalitions`";
        }

        return onCommand2(channel, user, db, me, nations, allNations, weakground, dms, vm, slots, beige, useDnr, ignoreAlliances, includeAlliances, active, minutesInactive, score, minLoot, beigeTurns, ignoreBank, ignoreCity, results);
    }

    public String onCommand2(IMessageIO channel, User user, GuildDB db, DBNation me, Set<DBNation> nations, Set<DBNation> allNations, boolean weakground,
                             boolean dms, int vm, int slots, boolean beige,
                             boolean useDnr, Set<Integer> ignoreAlliances, boolean includeAlliances, boolean active,
                             long minutesInactive, double score, double minLoot, int beigeTurns, boolean ignoreBank, boolean ignoreCity, int numResults) throws ExecutionException, InterruptedException {
        Set<Integer> enemyAAs = db.getCoalition("enemies");

        if (weakground) nations.removeIf(f -> f.getGroundStrength(true, true) > me.getGroundStrength(true, true) * 0.4);

        nations.removeIf(nation ->
                nation.getActive_m() < 12000 &&
                        nation.getGroundStrength(true, false) > me.getGroundStrength(true, false) &&
                        nation.getAircraft() > me.getAircraft() &&
                        nation.getShips() > me.getShips() + 2);


        if (dms) {
            channel = new DiscordChannelIO(RateLimitUtil.complete(user.openPrivateChannel()));
        }

        CompletableFuture<IMessageBuilder> msgFuture = (channel.sendMessage("Please wait..."));

        double minScore = score * 0.75;
        double maxScore = score * 1.75;

        int count = 0;

        Function<DBNation, Boolean> canRaidDNR = db.getCanRaid();

        List<Map.Entry<DBNation, Map.Entry<Double, Double>>> nationNetValues = new ArrayList<>();

        Map<Integer, Double> allianceScores = new HashMap<>();

        List<DBWar> wars = Locutus.imp().getWarDb().getWarsByNation(me.getNation_id(), WarStatus.ACTIVE);
        Map<Integer, List<DBWar>> attackingWars = new RankBuilder<>(wars).group(DBWar::getDefender_id).get();

        Map<Integer, List<DBBounty>> allBounties = Locutus.imp().getWarDb().getBountiesByNation();

        // 38w4d
        ArrayList<DBNation> enemies = new ArrayList<>();
        for (DBNation enemy : nations) {
            if (enemy.getActive_m() > 385920) continue;
            if (enemy.getScore() >= maxScore || enemy.getScore() <= minScore) continue;
            if (enemy.getVm_turns() > vm) continue;
            if ((slots == -1 && enemy.getDef() >= 3) || (slots != -1 && 3 - enemy.getDef() != slots)) continue;
            if (enemy.hasUnsetMil()) continue;
            if (!beige && enemy.isBeige()) continue;
            if (useDnr && !canRaidDNR.apply(enemy)) continue;
//                    if (dnr.contains(enemy.getAlliance_id())) continue;
//                    if (enemy.getActive_m() < 10000 && dnr_active.contains(enemy.getAlliance_id())) continue;
//                    if ((enemy.getActive_m() < 10000 || enemy.getPosition() > 1) && dnr_member.contains(enemy.getAlliance_id())) continue;
            if ((ignoreAlliances.contains(enemy.getAlliance_id()) == !includeAlliances) && (includeAlliances || enemy.getPosition() > 1))
                continue;
            if (attackingWars.containsKey(enemy.getNation_id())) continue;
            if (!active && enemy.getActive_m() < minutesInactive) continue;
            if (enemy.hasUnsetMil() || me.hasUnsetMil()) continue;
            enemies.add(enemy);
        }

        double infraCost = PnwUtil.calculateInfra(me.getAvg_infra(), me.getAvg_infra() - me.getAvg_infra() * 0.05) * me.getCities();

        long diffRevenue = 0;
        long diffBankLootEst = 0;
        long diffWars = 0;
        long diffCounter = 0;

        double lootPct = 0.14;

        double[] buffer = new double[ResourceType.values.length];
        Map<DBNation, Double> lootTotal = new HashMap<>();
        Map<DBNation, Double> lootEst = new HashMap<>();

        long turn = TimeUtil.getTurn();
        for (DBNation enemy : enemies) {
            double value = 0;
            {
                LootEntry loot = enemy.getBeigeLoot();
                if (loot != null) {
                    value = loot.convertedTotal();
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
                        double[] revenue = enemy.getRevenue(turnsInactive + 24, true, true, false, true, false, false, false);
                        if (loot != null) {
                            revenue = PnwUtil.capManuFromRaws(revenue, loot.getTotal_rss());
                        }
                        revenueEst = PnwUtil.convertedTotal(revenue);
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
                long start4 = System.nanoTime();
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
                        bankLootEst = aaLoot.convertedTotal() * percent;
                        value += bankLootEst;
                    }
                }
                diffBankLootEst += System.nanoTime() - start4;
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
                System.out.println("Value2 " + MathMan.format(value));
            }

            double originalValue = value;
            double winChance = 1;
            double costIncurred = 0;
            double activeChance = enemy.getActive_m() < 10000 ? (enemy.getActive_m() < 3000 ? 1 : 0.5) : 0;

            if (value <= minLoot) continue;

            double counterChance = -1;
            long myMilValue = me.militaryValue(false);

            long start5 = System.nanoTime();
            if (enemy.getActive_m() < 10000 && enemy.getAlliance_id() != 0 && !enemyAAs.contains(enemy.getAlliance_id())) {
                int turns = 2 * 12;
                Activity activity = new Activity(enemy.getNation_id(), 70 * 12);
                activeChance = activity.loginChance(turns, true);
                if (me.getShips() <= enemy.getShips()) {
                    value -= Math.max(0, value - bankLootEst) * 0.75 * activeChance;
                } else {
                    value -= Math.max(0, value - bankLootEst) * 0.25 * activeChance;
                }

                long enemyMilValue = enemy.militaryValue(false);
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
                List<DBWar> enemyWars = Locutus.imp().getWarDb().getWarsByNation(enemy.getNation_id());
                if (!enemyWars.isEmpty()) {
                    DBWar lastWar = null;
                    for (DBWar war : enemyWars) {
                        if (war.defender_id == enemy.getNation_id()) {
                            lastWar = war;
                            if (war.status != WarStatus.ACTIVE) {
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
                        if (lastWar.status == WarStatus.DEFENDER_VICTORY) {
                            winChance *= 0.5;
                        } else if (lastWar.status != WarStatus.ATTACKER_VICTORY) {
                            winChance *= 0.9;
                        }
                    }
                } else {
                    winChance *= 0.8;
                }
            }
            diffWars += System.nanoTime() - start5;

            long start6 = System.nanoTime();
            diffCounter += System.nanoTime() - start6;

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

            nationNetValues.add(new AbstractMap.SimpleEntry<>(enemy, new AbstractMap.SimpleEntry<>(value, originalValue)));
        }

        System.out.println("Diffloop:" +
                "\n- diffRevenue " + (diffRevenue / TimeUnit.MILLISECONDS.toNanos(1)) +
                "\n- diffBankLootEst " + (diffBankLootEst / TimeUnit.MILLISECONDS.toNanos(1)) +
                "\n- diffWars " + (diffWars / TimeUnit.MILLISECONDS.toNanos(1)) +
                "\n- diffCounter " + (diffCounter / TimeUnit.MILLISECONDS.toNanos(1))
        );

        nationNetValues.sort((o1, o2) -> Double.compare(o2.getValue().getKey(), o1.getValue().getKey()));

        StringBuilder response = new StringBuilder("**Results for " + me.getNation() + "**:");

        for (Map.Entry<DBNation, Map.Entry<Double, Double>> entry : nationNetValues) {
            DBNation nation = entry.getKey();
            if (nation.isBeige() && beigeTurns != -1) {
                if (nation.getBeigeTurns() >= beigeTurns) continue;
            }
            if (count++ == numResults) break;

            // formatter.format(entry.getValue()) // net loot

            Map.Entry<Double, Double> valueEst = entry.getValue();
            String moneyStr = "\nweighted $" + MathMan.formatSig(valueEst.getKey()) + "/actual $" + MathMan.formatSig(valueEst.getValue()) + "";
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

        if (count == 0) {
            channel.sendMessage("No results. Try using `!raid * 15` or `!raid * 15 -beige` (and plan raids out). Ping milcom for (assistance");
            return null;
        }

        if (db.isAllyOfRoot()) {
            StringBuilder warnings = new StringBuilder();
            if (me.getAvg_infra() > 1000 && me.getCities() > 7) {
                warnings.append("- You have high infra (>1000) which will likely be lost to counters.\n");
            }
            if (me.getSoldiers() == 0) {
                warnings.append("- You do not have any soldiers, which should be used for raiding as other units aren't cost effective.\n");
            }
            if (me.getTanks() != 0) {
                warnings.append("- We don't recommend raiding with tanks because they are unable to loot nations with any cost efficiency.\n");
            }
            if (beige) {
                warnings.append("- Set a reminder for yourself to hit nations on beige. You can declare during turn change\n");
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
}
