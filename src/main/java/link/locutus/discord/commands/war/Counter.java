package link.locutus.discord.commands.war;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.Activity;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.entities.NationMeta;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.pnw.PNWUser;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.battle.sim.SimulatedWar;
import link.locutus.discord.util.battle.sim.SimulatedWarNode;
import link.locutus.discord.apiv1.domains.War;
import link.locutus.discord.apiv1.domains.subdomains.WarContainer;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class Counter extends Command {
    public Counter() {
        super("counter", CommandCategory.MILCOM, CommandCategory.GAME_INFO_AND_TOOLS, CommandCategory.MEMBER);
    }
    @Override
    public String help() {
        return Settings.commandPrefix(true) + "counter <war> [alliance|coalition|role]";
    }

    @Override
    public String desc() {
        return "Get a list of nations to counter\n" +
                "Add `-o` to ignore nations with 5 offensive slots\n" +
                "Add `-w` to filter out weak attackers\n" +
                "Add `-a` to only list active nations (past hour)";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return Roles.MEMBER.has(user, server);
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.isEmpty() || args.size() > 2) {
            return usage(args.size(), 1, 2, channel);
        }
        if (me == null) {
            return "Please use " + CM.register.cmd.toSlashMention();
        }
        DBNation counter;
        int defenderId;

        String arg0 = args.get(0);
        if (!arg0.startsWith("" + Settings.INSTANCE.PNW_URL() + "/nation/war/") || !arg0.contains("=")) {
            defenderId = 0;
            Integer counterId = DiscordUtil.parseNationId(arg0);
            if (counterId == null) {
                return "Invalid `war-url` or `nation`:`" + arg0 + "`";
            }
            counter = Locutus.imp().getNationDB().getNation(counterId);
        } else {
            int warId = Integer.parseInt(arg0.split("=")[1].replaceAll("/", ""));
            DBWar war = Locutus.imp().getWarDb().getWar(warId);
            int counterId = war.getAttacker_id();
            counter = Locutus.imp().getNationDB().getNation(counterId);

            if (counter.getAlliance_id() == me.getAlliance_id() || (guild != null && Locutus.imp().getGuildDB(guild).getCoalition("allies").contains(counter.getAlliance_id()))) {
                counterId = (war.getDefender_id());
                counter = Locutus.imp().getNationDB().getNation(counterId);
                defenderId = (war.getAttacker_id());
            } else {
                defenderId = (war.getDefender_id());
            }
        }

        double score = counter.getScore();
        double scoreMin = score / 1.75;
        double scoreMax = score / 0.75;

        boolean filterApps = false;
        Set<DBNation> pool;

        if (args.size() == 2) {
            if (args.get(1).equalsIgnoreCase("*")) {
                Set<Integer> aaIds = Locutus.imp().getGuildDB(guild).getAllianceIds();
                Set<Integer> allies = Locutus.imp().getGuildDB(guild).getCoalition("allies");
                if (!aaIds.isEmpty()) allies.addAll(aaIds);
                pool = Locutus.imp().getNationDB().getNations(allies);
            } else {
                try {
                    pool = DiscordUtil.parseNations(guild, args.get(1));
                } catch (Throwable e) {
                    e.printStackTrace();
                    throw e;
                }
            }
        } else {
            filterApps = true;
            Set<Integer> aaIds = Locutus.imp().getGuildDB(guild).getAllianceIds();
            if (aaIds.isEmpty()) {
                Set<Integer> allies = Locutus.imp().getGuildDB(guild).getCoalition("allies");
                pool = Locutus.imp().getNationDB().getNations(allies);
            }
            else {
                pool = Locutus.imp().getNationDB().getNations(aaIds);
            }
        }
        if (filterApps) {
            pool.removeIf(f -> f.getPosition() <= 1);
        }
        if (flags.contains('a')) {
            pool.removeIf(f -> !f.isOnline());
        }

        if (pool.isEmpty()) {
            return "Invalid nation or alliance.";
        }

        pool.removeIf(nation -> nation.getScore() < scoreMin || nation.getScore() > scoreMax);
        pool.removeIf(nation -> nation.getOff() >= (nation.getMaxOff()) && !flags.contains('o'));
        pool.removeIf(nation -> nation.getNation_id() == defenderId);
        DBNation finalCounter = counter;
        pool.removeIf(nation -> nation.getAlliance_id() == finalCounter.getAlliance_id());
        pool.removeIf(nation -> nation.getAlliance_id() == 0);
        pool.removeIf(nation -> nation.getActive_m() > TimeUnit.DAYS.toMinutes(2));
        pool.removeIf(nation -> nation.getVm_turns() != 0);
        pool.removeIf(f -> f.getAircraft() < finalCounter.getAircraft() * 0.6 && finalCounter.getAircraft() > 100);
        if (flags.contains('w')) pool.removeIf(nation -> nation.getStrength() < finalCounter.getStrength());

        Iterator<DBNation> iter = pool.iterator();
        outer:
        while (iter.hasNext()) {
            DBNation nation = iter.next();
            if (nation.getDef() == 0 && nation.getOff() == 0) {
                continue;
            } else {
                double totalStr = 0;
                int numWars = 0;

                Set<DBWar> wars = nation.getActiveWars();
                if (wars.isEmpty()) {
                    continue;
                }
                for (DBWar war : wars) {
                    DBNation other = war.getNation(!war.isAttacker(nation));
                    if (other == null || other.hasUnsetMil()) continue;
                    if (other.getAircraft() > nation.getAircraft() && counter.getAircraft() > nation.getAircraft()) {
                        iter.remove();
                        continue outer;
                    }
                }
            }
        }

        if (pool.isEmpty()) {
            return "No nations in range";
        }

        List<Map.Entry<DBNation, Double>> nationNetValues = new ArrayList<>();
        long time = 5000 / pool.size();

        long currentTurn = TimeUtil.getTurn();

        for (DBNation nation : pool) {
            if (nation.hasUnsetMil()) continue;
            String type = counter.getAvg_infra() > 1500 ? "attrition" : counter.getAvg_infra() > 1000 ? "ordinary" : "raid";
            SimulatedWarNode origin = SimulatedWarNode.of(nation, counter.getNation_id() + "", nation.getNation_id() + "", type);
            double value;
            SimulatedWarNode solution;
            if (counter.getActive_m() > 10000) {
                Function<SimulatedWarNode, Double> valueFunction = simulatedWarNode -> -simulatedWarNode.raidDistance(origin);
                Function<SimulatedWarNode, SimulatedWarNode.WarGoal> goal = node -> {
                    if (node.getAggressor().getResistance() <= 0 || node.getDefender().getResistance() <= 0 || node.getTurnsLeft() <= 0) {
                        return SimulatedWarNode.WarGoal.SUCCESS;
                    }
                    return SimulatedWarNode.WarGoal.CONTINUE;
                };
                SimulatedWar warSim = new SimulatedWar(origin, valueFunction, goal);
                solution = warSim.solve();
            } else {
                solution = origin.minimax(true, time);
            }
            if (solution != null) {
                value = solution.warDistance(origin);
            } else {
                value = nation.getAircraft() * 100 + nation.getGroundStrength(true, false);
            }
            value *= Math.max(0.5, Math.min(5, nation.getRelativeStrength()));

            Activity activity = nation.getActivity(14 * 12);
            double loginChance = activity.loginChance((int) Math.max(1, (12 - (currentTurn % 12))), true);
            User user = nation.getUser();
            if (user != null) {
                Member member = guild.getMember(user);
                if (member != null) {
                    OnlineStatus status = member.getOnlineStatus();
                    switch (status) {
                        case ONLINE:
                            loginChance = Math.max(loginChance, 1);
                            break;
                        case IDLE:
                            loginChance = Math.max(loginChance, 0.75);
                            break;
                        case DO_NOT_DISTURB:
                            loginChance = Math.max(loginChance, 0.5);
                            break;
                    }
                }
            }
            value += 0.5 * value * loginChance;

            double infraRatio = 1 + (Math.max(0, nation.getAvg_infra() - 1000) / 2000d);
            value += 0.25 * value * Math.sqrt(infraRatio);

            value += 0.25 * value * nation.getPosition();

            nationNetValues.add(new AbstractMap.SimpleEntry<>(nation, value));
        }

        nationNetValues.sort(Comparator.comparingDouble(Map.Entry::getValue));

        StringBuilder response = new StringBuilder();
        response.append("**Enemy: **").append(counter.toMarkdown()).append("\n**Counters**\n");

        boolean ping = flags.contains('p');

        int count = 0;
        int maxResults = 25;
        for (Map.Entry<DBNation, Double> entry : nationNetValues) {
            DBNation nation = entry.getKey();
            if (count++ == maxResults) break;

            PNWUser user = DiscordUtil.getUser(nation);
            if (user != null) {
                String statusStr = "";
                if(guild != null) {
                    Member member = guild.getMemberById(user.getDiscordId());
                    if (member != null) {
                        OnlineStatus status = member.getOnlineStatus();
                        if (status != OnlineStatus.OFFLINE && status != OnlineStatus.UNKNOWN) {
                            statusStr = status.name() +" | ";
                        }
                    }
                }
                response.append(statusStr);
                response.append(user.getDiscordName() + " / ");
                if (ping) response.append(user.getAsMention());
                else response.append("`" + user.getAsMention() + "` ");
            }
            response.append(nation.toMarkdown()).append('\n');
        }

        if (count == 0) {
            return "No results. Please ping a target advisor";
        }

        me.setMeta(NationMeta.INTERVIEW_COUNTER, (byte) 1);

        if (!flags.contains('w')) response.append("\n- add `-w` to filter out weak attackers");
        if (!flags.contains('o')) response.append("\n- add `-o` to include nations with 5 wars");

        return response.toString().trim();
    }
}