package link.locutus.discord.commands.war;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.binding.bindings.PrimitiveBindings;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.Activity;
import link.locutus.discord.db.entities.Coalition;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.battle.BlitzGenerator;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.BiFunction;

public class WarCommand extends Command {

    public WarCommand() {
        super("war", CommandCategory.MILCOM, CommandCategory.GAME_INFO_AND_TOOLS, CommandCategory.MEMBER);
    }

    @Override
    public String help() {
        return super.help() + " <alliance|coalition|none|*>";
    }

    @Override
    public String desc() {
        return "Find a weaker war target that you can hit, who is in a specified alliance/coalition/none/*\n" +
                "Defualts to `enemies` coalition\n" +
                "Add `score:1234` to specify attacker score\n" +
                "Add `-i` to include inactives\n" +
                "Add `-a` to include applicants\n" +
                "Add `-s` to include strong enemies\n" +
                "Add `-p` to only include priority targets\n" +
                "Add `-w` to only list weak enemies\n" +
                "Add `-c` to only list enemies with less cities\n" +
                "Add `-e` to prioritize easier targets";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return Roles.MEMBER.has(user, server);
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        String numStr = DiscordUtil.parseArg(args, "results");
        int num = numStr == null ? 8 : Integer.parseInt(numStr);
        String nationArg = DiscordUtil.parseArg(args, "nation");
        if (nationArg == null && args.size() == 1 && MathMan.isInteger(args.get(0))) {
            nationArg = args.get(0);
            args.clear();
        }

        if (nationArg != null) me = DiscordUtil.parseNation(nationArg);
        if (me == null) {
            return "Invalid nation? Are you sure you are registered?" + author.getAsMention();
        }

        String scoreStr = DiscordUtil.parseArg(args, "score");
        double score = scoreStr == null ? me.getScore() : PrimitiveBindings.Double(scoreStr);
        double minScore = score * 0.75;
        double maxScore = score * 1.75;

        if (flags.contains('d')) {
            channel = new DiscordChannelIO(RateLimitUtil.complete(author.openPrivateChannel()));
        }

        boolean includeInactives = flags.contains('i');
        boolean includeApplicants = flags.contains('a');
        boolean priority = flags.contains('p');
        boolean includeStrong = flags.contains('s');
        boolean filteredUpdeclare = false;

        GuildDB db = Locutus.imp().getGuildDB(guild);
        String aa = null;

        switch (args.size()) {
            default:
                return usage(args.size(), 0, 1, channel);
            case 1:
                aa = args.get(0);
            case 0:
                Collection<DBNation> nations;
                if (aa == null) {
                    Set<Integer> enemies = db.getCoalition(Coalition.ENEMIES);
                    if (enemies == null || enemies.isEmpty()) {
                        return "No enemies set. Please use `" + Settings.commandPrefix(true) + "setcoalition <alliance> enemies` or specify an enemy alliance/coalition as your second parameter";
                    }
                    nations = Locutus.imp().getNationDB().getNations(new HashSet<>(enemies));
                    if (!includeApplicants) {
                        nations.removeIf(n -> n.getPosition() <= 1);
                    }
                } else {
                    nations = DiscordUtil.parseNations(guild, aa);
                }

                if (!includeInactives) nations.removeIf(n -> n.getActive_m() >= 2440);
                nations.removeIf(n -> n.getVm_turns() != 0);
//                nations.removeIf(n -> n.isBeige());

                ArrayList<DBNation> tmp = new ArrayList<>();
                for (DBNation nation : nations) {
                    if (nation.getScore() >= maxScore || nation.getScore() <= minScore) continue;
                    if (nation.getActive_m() > 2440 && !includeInactives) continue;
                    if (nation.getVm_turns() != 0) continue;
                    if (nation.getDef() >= 3) continue;
                    if (nation.getCities() >= me.getCities() * 1.5 && !includeStrong && me.getGroundStrength(false, true) > nation.getGroundStrength(true, false) * 2) continue;
                    if (nation.getCities() >= me.getCities() * 1.8 && !includeStrong && nation.getActive_m() < 2880) continue;
                    if (nation.active_m() < 2880 && nation.getCities() > Math.ceil(me.getCities() * 1.33) && me.getCities() >= 10 && !(flags.contains('f'))) {
                        filteredUpdeclare = true;
                        continue;
                    }
                    tmp.add(nation);
                }
                nations = tmp;

                if (priority) {
                    nations.removeIf(f -> f.getNumWars() == 0);
                    nations.removeIf(f -> f.getRelativeStrength() <= 1);
                }

                DBNation finalMe = me;
                if (flags.contains('w')) {
                    nations.removeIf(f -> f.getGroundStrength(true, false) > finalMe.getGroundStrength(true, false));
                    nations.removeIf(f -> f.getAircraft() > finalMe.getAircraft());
                }
                if (flags.contains('c')) {
                    nations.removeIf(f -> f.getCities() > finalMe.getCities());
                }

                Set<DBWar> wars = me.getActiveWars();
                for (DBWar war : wars) {
                    nations.remove(war.getNation(true));
                    nations.remove(war.getNation(false));
                }

                CompletableFuture<IMessageBuilder> msgFuture = channel.sendMessage("Please wait... ");

                Set<Integer> allies = db.getAllies();
                Set<Integer> enemies = db.getCoalition(Coalition.ENEMIES);

                BiFunction<Double, Double, Double> allyGraph = (a, b) -> 1d;
                BiFunction<Double, Double, Double> enemyGraph = (a, b) -> 1d;

                if (allies != null && enemies != null && !allies.isEmpty() && !enemies.isEmpty() && me.getCities() >= 10) {
//                    List<DBNation> allyNations = Locutus.imp().getNationDB().getNations(allies);
//                    List<DBNation> enemyNations = Locutus.imp().getNationDB().getNations(enemies);
//
//                    allyNations.removeIf(f -> f.getAircraftPct() < 0.8 || f.getActive_m() > 2880 || f.getVm_turns() > 0 || f.getCities() < 10);
//                    enemyNations.removeIf(f -> f.getAircraftPct() < 0.8 || f.getActive_m() > 2880 || f.getVm_turns() > 0 || f.getCities() < 10);
//
//                    allyGraph = PnwUtil.getXInRange(allyNations, n -> Math.pow(n.getStrength(), 3));
//                    enemyGraph = PnwUtil.getXInRange(enemyNations, n -> Math.pow(n.getStrength(), 3));
                }

                try {
                    int mySoldierRebuy = me.getCities() * Buildings.BARRACKS.max() * 5 * 2;

                    long currentTurn = TimeUtil.getTurn();

                    List<Map.Entry<DBNation, Double>> nationNetValues = new ArrayList<>();

                    GuildDB rootDB = Locutus.imp().getGuildDB(Locutus.imp().getServer());

                    double scoreLow = score * 0.75;
                    double scoreHigh = score * 1.25;
                    double currentStrengthRatio = enemyGraph.apply(minScore, scoreHigh) / allyGraph.apply(minScore, scoreHigh);
                    double belowStrengthRatio = enemyGraph.apply(scoreLow * 0.24, scoreLow) / allyGraph.apply(scoreLow * 0.24, scoreLow);
                    double aboveStrengthRatio = enemyGraph.apply(scoreHigh, scoreHigh * 1.25) / allyGraph.apply(scoreHigh, scoreHigh * 1.25);

                    for (DBNation nation : nations) {
                        if (nation.isBeige()) continue;
//                        SimulatedWarNode origin = SimulatedWarNode.of(nation, me.getNation_id() + "", nation.getNation_id() + "", "raid");

                        double value;
                        if (flags.contains('e')) {
                            value = BlitzGenerator.getAirStrength(nation, true);
                        } else {
                            value = BlitzGenerator.getAirStrength(nation, true);
                            value *= 2 * (nation.getCities() / (double) me.getCities());
                            if (nation.getOff() > 0) value /= 4;
                            if (nation.getShips() > 1 && nation.getOff() > 0 && nation.isBlockader()) value /= 2;
                            if (nation.getDef() <= 1) value /= (1.05 + (0.1 * nation.getDef()));
                            if (nation.getActive_m() > 1440) value *= 1 + Math.sqrt(nation.getActive_m() - 1440) / 250;
                            value /= (1 + nation.getOff() * 0.1);
                            if (nation.getScore() > score * 1.25) value /= 2;
                            if (nation.getOff() > 0) value /= nation.getRelativeStrength();

                        }

//                        SimulatedWarNode solution;
//
//                        if (nation.getActive_m() > 10000) {
//                            Function<SimulatedWarNode, Double> valueFunction = simulatedWarNode -> -simulatedWarNode.raidDistance(origin);
//                            Function<SimulatedWarNode, SimulatedWarNode.WarGoal> goal = node -> {
//                                if (node.getAggressor().getResistance() <= 0 || node.getDefender().getResistance() <= 0 || node.getTurnsLeft() <= 0) {
//                                    return SimulatedWarNode.WarGoal.SUCCESS;
//                                }
//                                return SimulatedWarNode.WarGoal.CONTINUE;
//                            };
//                            SimulatedWar warSim = new SimulatedWar(origin, valueFunction, goal);
//                            solution = warSim.solve();
//                        } else {
//                            solution = origin.minimax(true, time);
//                        }
//
//                        double value;
//                        if (solution != null) {
//                            value = solution.raidDistance(origin);
//                        } else if (nation.getSoldiers() <= mySoldierRebuy || nation.getShips() <= 1) {
//                            value = nation.getCities() * 500000;
//                        } else {
//                            continue;
//                        }
//
//                        if (nation.getSoldiers() <= mySoldierRebuy) {
//                            value = value - value * 0.5 * (1 - nation.getSoldiers() / (double) mySoldierRebuy);
//                        }
//
//                        if (nation.getShips() == 0) {
//                            value /= 1.25;
//                        }
//
//                        Activity activity = nation.getActivity(14 * 12);
//                        double loginChance = activity.loginChance((int) Math.max(1, (12 - (currentTurn % 12))), true);
//
//                        value += 0.1 * value * Math.sqrt(loginChance);
//
//                        double infraRatio = 1 - (Math.max(0, nation.getAvg_infra() - 1000) / 2000d);
//                        value += 0.1 * value * Math.sqrt(infraRatio);
//
//                        value += 0.1 * value * nation.getPosition();
//
//                        value += 0.25 * value * (3 - nation.getDef());

                        nationNetValues.add(new AbstractMap.SimpleEntry<>(nation, value));
                    }

                    Map<DBNation, Integer> beigeTurns = new HashMap<>();

                    if (nationNetValues.isEmpty()) {
                        for (DBNation nation : nations) {
                            if (nation.isBeige()) {
                                int turns = beigeTurns.computeIfAbsent(nation, f -> f.getBeigeTurns());
                                nationNetValues.add(new AbstractMap.SimpleEntry<>(nation, (double) turns));
                            }
                        }
                        if (nationNetValues.isEmpty()) {
                            String message;
                            if (flags.contains('p')) {
                                message = "No targets found. Try `" + Settings.commandPrefix(true) + "war`";
                            } else {
                                message = "No targets found:\n" +
                                        "- Add `-i` to include inactives\n" +
                                        "- Add `-a` to include applicants\n" +
                                        "e.g. `" + Settings.commandPrefix(true) + "war -i -a`";
                            }
                            channel.sendMessage(message);
                            return null;
                        }
                    }

                    nationNetValues.sort(Comparator.comparingDouble(Map.Entry::getValue));

                    StringBuilder response = new StringBuilder("**Results for " + me.getNation() + "** (highest priority is first)");

                    int count = 0;

                    boolean whitelisted = db.isWhitelisted();
                    boolean isUpdeclare = false;

                    for (Map.Entry<DBNation, Double> nationNetValue : nationNetValues) {
                        if (count++ == num) break;

                        DBNation nation = nationNetValue.getKey();

                        response.append('\n')
                                .append("<" + Settings.INSTANCE.PNW_URL() + "/nation/id=" + nation.getNation_id() + ">")
                                .append(" | " + String.format("%16s", nation.getNation()))
                                .append(" | " + String.format("%16s", nation.getAllianceName()));

                        if (whitelisted) {
                            double total = nation.lootTotal();
                            if (total != 0) {
                                response.append(": $" + MathMan.format(total));
                            }
                        }

                        response.append("\n```")
//                            .append(String.format("%5s", (int) nation.getScore())).append(" ns").append(" | ")
                                .append(String.format("%2s", nation.getCities())).append(" \uD83C\uDFD9").append(" | ")
//                                .append(String.format("%5s", nation.getAvg_infra())).append(" \uD83C\uDFD7").append(" | ")
                                .append(String.format("%6s", nation.getSoldiers())).append(" \uD83D\uDC82").append(" | ")
                                .append(String.format("%5s", nation.getTanks())).append(" \u2699").append(" | ")
                                .append(String.format("%5s", nation.getAircraft())).append(" \u2708").append(" | ")
                                .append(String.format("%4s", nation.getShips())).append(" \u26F5").append(" | ")
//                            .append(String.format("%1s", nation.getOff())).append(" \uD83D\uDDE1").append(" | ")
                                .append(String.format("%1s", nation.getDef())).append(" \uD83D\uDEE1");
//                                .append(String.format("%2s", nation.getSpies())).append(" \uD83D\uDD0D");

                        if (nation.isBeige()) {
                            int turns = beigeTurns.computeIfAbsent(nation, f -> f.getBeigeTurns());
                            if (turns > 0) {
                                response.append(" | ").append("beige=" + turns);
                            }
                        }

                        if (nation.active_m() < 2880 && nation.getCities() > Math.ceil(me.getCities() * 1.33) && me.getCities() >= 10) {
                            isUpdeclare = true;
                        }

                        Activity activity = nation.getActivity(14 * 12);
                        double loginChance = activity.loginChance((int) Math.max(1, (12 - (currentTurn % 12))), true);
                        int loginPct = (int) (loginChance * 100);

                        response.append(" | login=" + loginPct + "%");
                        response.append("```");
                    }
                    if (filteredUpdeclare) {
                        response.append("\n**note: Updeclares have been removed. Add `-f` to bypass**");
                    } else if (isUpdeclare) {
                        response.append("\n**note: some of the targets have a lot more cities than you and may be unsuitable. It is not recommended to updeclare past " + Math.round(me.getCities() * 1.2) + " cities if there are more suitable attackers**");
                    }

                    if (count == 0) {
                        channel.sendMessage("No results. Please ping a target (advisor");
                        return null;
                    }

                    channel.send(response.toString().trim());
                    return null;
                } finally {
                    try {
                        IMessageBuilder msg = msgFuture.get();
                        if (msg != null && msg.getId() > 0) {
                            channel.delete(msg.getId());
                        }
                    } catch (InterruptedException | ExecutionException e) {
                        e.printStackTrace();
                    }
                }
        }
    }
}
