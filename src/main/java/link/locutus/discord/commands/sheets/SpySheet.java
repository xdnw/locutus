package link.locutus.discord.commands.sheets;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.Activity;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.guild.SheetKey;
import link.locutus.discord.pnw.Spyop;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.SpyCount;
import link.locutus.discord.util.io.PagePriority;
import link.locutus.discord.util.sheet.SpreadSheet;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

public class SpySheet extends Command {
    public SpySheet() {
        super(CommandCategory.MILCOM, CommandCategory.GOV, CommandCategory.GAME_INFO_AND_TOOLS);
    }
    @Override
    public boolean checkPermission(Guild server, User user) {
        return super.checkPermission(server, user) && Roles.MILCOM.has(user, server);
    }

    @Override
    public String help() {
        return super.help() + " <attacking-coalition> <defending-coalition> [op-types]";
    }

    @Override
    public String desc() {
        return "Generate a spy blitz sheet.\n" +
                "Add `-f` to force update\n" +
                "Add `-s` to check slots\n" +
                "Add e.g. `-9` to remove attackers with less than 9 spies\n" +
                "Add `-c` to use the cache for a lot of low enemy spy counts\n" +
                "Add `-k` to priotize kills instead of damage";
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        String sheetUrl = DiscordUtil.parseArg(args, "sheet");
        Integer minSpies = -1;
        Iterator<String> iter = args.iterator();
        while (iter.hasNext()) {
            String arg = iter.next();
            if (arg.charAt(0) == '-' && MathMan.isInteger(arg)) {
                minSpies = Math.abs(Integer.parseInt(arg));
                iter.remove();
            }
        }

        if (args.size() < 2) return usage(args.size(), 2, channel);
        Integer turns = Integer.MAX_VALUE;
        Set<SpyCount.Operation> allowedOpTypes = new HashSet<>();
        if (args.size() == 3) {
            for (String type : args.get(2).split(",")) {
                allowedOpTypes.add(SpyCount.Operation.valueOf(type.toUpperCase()));
            }
        } else {
            allowedOpTypes.addAll(Arrays.asList(SpyCount.Operation.values()));
            allowedOpTypes.remove(SpyCount.Operation.INTEL);
            allowedOpTypes.remove(SpyCount.Operation.SOLDIER);
        }

        Set<DBNation> attackers = DiscordUtil.parseNations(guild, author, me, args.get(0), false, false);
        Set<DBNation> defenders = DiscordUtil.parseNations(guild, author, me, args.get(1), false, false);
        attackers.removeIf(f -> f.hasUnsetMil());
        defenders.removeIf(f -> f.hasUnsetMil());

        attackers.removeIf(t -> t.getVm_turns() > 0 || t.active_m() > 1880 || t.getPosition() <= 1);
        if (minSpies != -1) {
            Integer finalMinSpies1 = minSpies;
            attackers.removeIf(t -> t.getSpies() < finalMinSpies1 - 3);
        }
        defenders.removeIf(t -> t.getVm_turns() > 0 || t.active_m() > 2880);

        BiFunction<Double, Double, Integer> attRange = PW.getIsNationsInSpyRange(attackers);
        BiFunction<Double, Double, Integer> defSpyRange = PW.getIsNationsInSpyRange(defenders);

        BiFunction<Double, Double, Integer> attScoreRange = PW.getIsNationsInScoreRange(attackers);
        BiFunction<Double, Double, Integer> defScoreRange = PW.getIsNationsInScoreRange(defenders);

        boolean isProlonged = turns >= 7 * 12;

        if (!isProlonged) {
            defenders.removeIf(n -> attScoreRange.apply(n.getScore() * 0.75, n.getScore() / 0.75) == 0);
        }

        Set<Integer> updateSpies = new HashSet<>();

        if (flags.contains('f')) {
            for (DBNation defender : defenders) {
                if (defender.getSpies() <= 3 && flags.contains('c')) continue;
                defender.updateSpies(PagePriority.ESPIONAGE_ODDS_BULK, true);
            }
            for (DBNation attacker : attackers) attacker.updateSpies(PagePriority.ESPIONAGE_ODDS_BULK, true);
        }

        Map<DBNation, Double> loginProb = new HashMap<>();
        for (DBNation defender : defenders) {
            Activity activity = defender.getActivity(14 * 12);
            double chance = 0;
            if (isProlonged) {
                for (double v : activity.getByDay()) chance += v;
                chance /= 7d;
            } else {
                chance = activity.loginChance(turns * 12, true);
            }
            loginProb.put(defender, chance);
        }

        // Spies are valued by their square
        BiFunction<Double, Double, Double> attSpyGraph = PW.getXInRange(attackers, n -> Math.pow(n.updateSpies(PagePriority.ESPIONAGE_ODDS_BULK, false, false).doubleValue(), 2));
        BiFunction<Double, Double, Double> defSpyGraph = PW.getXInRange(defenders, n -> Math.pow(n.updateSpies(PagePriority.ESPIONAGE_ODDS_BULK, false, false).doubleValue(), 2));

        Integer finalMinSpies = minSpies;
        attackers.removeIf(n -> n.getSpies() <= finalMinSpies);

        // higher = higher value target
        Function<Double, Double> enemySpyRatio = new Function<Double, Double>() {
            @Override
            public Double apply(Double scoreAttacker) {
                double attSpies = attSpyGraph.apply(scoreAttacker * 0.4, scoreAttacker * 1.5);
                double defSpies = defSpyGraph.apply(scoreAttacker * 0.4, scoreAttacker * 1.5);
                return defSpies / attSpies;
            }
        };

        if (flags.contains('s')) {
            defenders.removeIf(DBNation::isEspionageFull);
        }

        List<Spyop> ops = new ArrayList<>();

        for (DBNation attacker : attackers) {
            Activity activity = null;
            Integer mySpies = attacker.getSpies();
            if (mySpies == null || mySpies == 0) continue;
            for (DBNation defender : defenders) {
                if (attacker.isInSpyRange(defender) && defender.getSpies() * 0.66 <= attacker.getSpies()) {
                    double loginRatio = loginProb.get(defender);

                    double spyRatio = enemySpyRatio.apply(defender.getScore());
                    for (SpyCount.Operation operation : allowedOpTypes) {
                        if (defender.getSpies() > Math.min(6, attacker.getSpies() / 3d) && operation != SpyCount.Operation.SPIES) continue;
                        if (operation.unit == null) continue;
                        if (operation != SpyCount.Operation.SPIES) {
                            int units = defender.getUnits(operation.unit);
                            if (units == 0) continue;
                            switch (operation.unit) {
                                case SOLDIER:
                                case TANK:
                                case AIRCRAFT:
                                case SHIP:
                                case MONEY:
                                    if (units * operation.unit.getConvertedCost() * 0.05 < 300000) continue;
                                    break;
                            }
                        }
                        SpyCount.Operation[] opTypes = new SpyCount.Operation[]{operation};
                        Map.Entry<SpyCount.Operation, Map.Entry<Integer, Double>> best = SpyCount.getBestOp(!flags.contains('k'), mySpies, defender, opTypes);

                        if (best == null) continue;

                        Map.Entry<Integer, Double> bestValue = best.getValue();
                        double opNetDamage = bestValue.getValue();

                        opNetDamage *= loginRatio;

                        if (operation == SpyCount.Operation.SPIES) {
                            opNetDamage *= spyRatio;
                            if (defender.hasProject(Projects.INTELLIGENCE_AGENCY)) {
                                opNetDamage *= 10;
                            }
                            if (defender.hasProject(Projects.SPY_SATELLITE)) {
                                opNetDamage *= 10;
                            }
                        }
                        if (operation == SpyCount.Operation.NUKE) {
                            if (defender.getNukes() == 1) {
                                ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
                                int minute = now.getHour() * 60 + now.getMinute();
                                if (minute > 30) {
                                    if (defender.active_m() < minute) {
                                        continue;
                                    }
                                } else {
                                    if (activity == null) activity = attacker.getActivity(12 * 14);
                                    double attLogin = activity.loginChance(1, true);
                                    if (attacker.active_m() < 60) attLogin = (60 - attacker.active_m()) / 60d;
                                    opNetDamage += opNetDamage * attLogin * 5;
                                }
                            } else {
                                opNetDamage *= 2;
                            }
                        }
                        if (operation == SpyCount.Operation.MISSILE) {
                            Integer missileCap = MilitaryUnit.MISSILE.getMaxPerDay(defender.getCities(), defender::hasProject);
                            if (defender.getMissiles() == (missileCap)) {
                                ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
                                int minute = now.getHour() * 60 + now.getMinute();
                                if (minute > 30) {
                                    if (defender.active_m() < minute) {
                                        continue;
                                    }
                                } else {
                                    if (activity == null) activity = attacker.getActivity(12 * 14);
                                    double attLogin = activity.loginChance(1, true);
                                    if (attacker.active_m() < 60) attLogin = (60 - attacker.active_m()) / 60d;
                                    opNetDamage += opNetDamage * attLogin * 5;
                                }
                            } else {
                                opNetDamage *= 2;
                            }
                        }
                        if (activity == null) activity = attacker.getActivity(12 * 14);
                        opNetDamage += 0.2 * opNetDamage * activity.loginChance(2, true);
                        if (defender.getNukes() > 3) {
                            opNetDamage *= 2;
                        }
                        if (defender.getMissiles() > 3) {
                            opNetDamage *= 1.5;
                        }

                        if (operation.unit != MilitaryUnit.AIRCRAFT && operation.unit != MilitaryUnit.SPIES) opNetDamage /= 2;

                        int safety = bestValue.getKey();

                        Integer defSpies = defender.updateSpies(PagePriority.ESPIONAGE_ODDS_BULK, false, false);
                        if (defSpies < 48 && operation == SpyCount.Operation.NUKE) defSpies += 3;
                        int numSpies = (int) Math.ceil(Math.min(mySpies, SpyCount.getRequiredSpies(defSpies, safety, operation, defender)));

                        double opCost = SpyCount.opCost(numSpies, safety);
                        // todo check if they can afford it
                        Spyop spyOp = new Spyop(attacker, defender, numSpies, best.getKey(), opNetDamage, safety);
                        ops.add(spyOp);
                    }
                }
            }
        }

        Collections.sort(ops, new Comparator<Spyop>() {
            @Override
            public int compare(Spyop o1, Spyop o2) {
                return Double.compare(o2.netDamage, o1.netDamage);
            }
        });

        Map<DBNation, List<Spyop>> opsAgainstNations = new LinkedHashMap<>();
        Map<DBNation, List<Spyop>> opsByNations = new LinkedHashMap<>();

        Function<DBNation, Integer> getNumOps = new Function<DBNation, Integer>() {
            @Override
            public Integer apply(DBNation nation) {
                return nation.hasProject(Projects.INTELLIGENCE_AGENCY) ? 2 : 1;
            }
        };

        for (Spyop op : ops) {
            List<Spyop> attOps = opsByNations.computeIfAbsent(op.attacker, f -> new ArrayList<>());
            if (attOps.size() >= getNumOps.apply(op.attacker)) {
                continue;
            }
            List<Spyop> defOps = opsAgainstNations.computeIfAbsent(op.defender, f -> new ArrayList<>());
            if (defOps.size() == 3) {
                continue;
            }
            if (defOps.size() > 0) {
                int units = op.defender.getUnits(op.operation.unit);
                for (Spyop other : defOps) {
                    if (other.operation != op.operation) continue;
                    units -= Math.ceil(SpyCount.getKills(other.spies, other.defender, other.operation, other.safety));
                }
                int kills = (int) Math.ceil(SpyCount.getKills(op.spies, op.defender, op.operation, op.safety));
                if (units < kills || kills == 0) continue;

            }

            defOps.add(op);
            attOps.add(op);
        }

        GuildDB db = Locutus.imp().getGuildDB(guild);
        SpreadSheet sheet;
        if (sheetUrl != null) {
            sheet = SpreadSheet.create(sheetUrl);
        } else {
            sheet = SpreadSheet.create(db, SheetKey.SPYOP_SHEET);
        }

        sheet.updateClearCurrentTab();
        generateSpySheet(sheet, opsAgainstNations);
        sheet.updateWrite();

        sheet.attach(channel.create(), "spy").send();
        return null;
    }

    public static void generateSpySheet(SpreadSheet sheet, Map<DBNation, List<Spyop>> opsAgainstNations) {
        List<Object> header = new ArrayList<>(Arrays.asList(
                "nation",
                "alliance",
                "\uD83C\uDFD9", // cities
                "\uD83C\uDFD7", // avg_infra
                "score",
                "\uD83D\uDD0D",
                "\uD83D\uDC82",
                "\u2699",
                "\u2708",
                "\u26F5",
                "\uD83D\uDE80", // rocket
                "\u2622\uFE0F", // rads
                "att1",
                "att2",
                "att3"
        ));

        sheet.setHeader(header);

        boolean multipleAAs = false;
        DBNation prevAttacker = null;
        for (List<Spyop> spyOpList : opsAgainstNations.values()) {
            for (Spyop spyop : spyOpList) {
                DBNation attacker = spyop.attacker;
                if (prevAttacker != null && prevAttacker.getAlliance_id() != attacker.getAlliance_id()) {
                    multipleAAs = true;
                }
                prevAttacker = attacker;
            }
        }

        for (Map.Entry<DBNation, List<Spyop>> entry : opsAgainstNations.entrySet()) {
            DBNation nation = entry.getKey();

            ArrayList<Object> row = new ArrayList<>();
            row.add(MarkupUtil.sheetUrl(nation.getNation(), PW.getUrl(nation.getNation_id(), false)));
            row.add(MarkupUtil.sheetUrl(nation.getAllianceName(), PW.getUrl(nation.getAlliance_id(), true)));
            row.add(nation.getCities());
            row.add(nation.getAvg_infra());
            row.add(nation.getScore());
            row.add("" + nation.getSpies());

            row.add(nation.getSoldiers());
            row.add(nation.getTanks());
            row.add(nation.getAircraft());
            row.add(nation.getShips());
            row.add(nation.getMissiles());
            row.add(nation.getNukes());

            for (Spyop spyop : entry.getValue()) {
                DBNation attacker = spyop.attacker;
                String attStr = MarkupUtil.sheetUrl(attacker.getNation(), PW.getUrl(attacker.getNation_id(), false));
                String safety = spyop.safety == 3 ? "covert" : spyop.safety == 2 ? "normal" : "quick";
                attStr += "& \"|" + spyop.operation.name() + "|" + safety + "|" + spyop.spies + "\"";

                if (multipleAAs) {
                    attStr += "& \"|" + spyop.operation.name() + "|" + safety + "|" + spyop.spies + "|" + attacker.getAllianceName() + "\"";
                } else {
                    attStr += "& \"|" + spyop.operation.name() + "|" + safety + "|" + spyop.spies + "\"";
                }

                row.add(attStr);
            }

            sheet.addRow(row);
        }
    }
}
