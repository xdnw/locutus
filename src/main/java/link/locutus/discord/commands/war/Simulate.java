package link.locutus.discord.commands.war;

import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.config.Settings;
import link.locutus.discord.pnw.DBNation;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.battle.sim.SimulatedWar;
import link.locutus.discord.util.battle.sim.SimulatedWarNode;
import link.locutus.discord.util.battle.sim.WarNation;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class Simulate extends Command {
    public Simulate() {
        super("simulate", CommandCategory.MILCOM, CommandCategory.GAME_INFO_AND_TOOLS, CommandCategory.MEMBER);
    }
    @Override
    public String help() {
        return "`" + Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "simulate <war>` or `" + Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "simulate <defender> <attacker> <type>`";
    }

    @Override
    public String desc() {
        return "Simulate a raid. Valid types are [raid, ordinary, attrition]\n" +
                "Use `attmmr=5553` or `defmmr=2251` to set attacker and defender mmr";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return (super.checkPermission(server, user));
    }

    @Override
    public String onCommand(MessageReceivedEvent event, List<String> args) throws Exception {
        boolean war = false;
        args = new ArrayList<>(args);
        String attmmr = null;
        String defmmr = null;
        for (int i = args.size() - 1; i >= 0; i--) {
            if (args.get(i).equalsIgnoreCase("-w")) {
                war = true;
                args.remove(i);
            } else if (args.get(i).startsWith("attmmr=")) {
                attmmr = args.get(i).split("=")[1];
                args.remove(i);
            } else if (args.get(i).startsWith("defmmr=")) {
                defmmr = args.get(i).split("=")[1];
                args.remove(i);
            }
        }
        DBNation me = DiscordUtil.getNation(event);
        if (me == null) {
            return "Please use " + Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "validate";
        }
        SimulatedWarNode origin;

        origin = SimulatedWarNode.of(me, args.toArray(new String[0]));
        if (attmmr != null) {
            origin.getAggressor().setMMR(
                    attmmr.charAt(0) - '0',
                    attmmr.charAt(1) - '0',
                    attmmr.charAt(2) - '0',
                    attmmr.charAt(3) - '0'
            );
        }
        if (defmmr != null) {
            origin.getDefender().setMMR(
                    defmmr.charAt(0) - '0',
                    defmmr.charAt(1) - '0',
                    defmmr.charAt(2) - '0',
                    defmmr.charAt(3) - '0'
            );
        }

//        event.getChannel().sendMessage("Simulating war between: " + origin.getAggressor().getNation().getNation() + " -> " + origin.getDefender().getNation().getNation()).complete();
        Message msg2 = RateLimitUtil.complete(event.getChannel().sendMessage(" - Fetching war link.locutus.discord.util.RateLimitUtil.complete(information..."));

        RateLimitUtil.queue(event.getChannel().editMessageById(msg2.getIdLong(), " - Initializing (simulation..."));

        long start = System.currentTimeMillis();

        Function<SimulatedWarNode, Double> raidFunction = new Function<SimulatedWarNode, Double>() {
            @Override
            public Double apply(SimulatedWarNode simulatedWarNode) {
                // TODO calculate military composition as well
                // TODO calculate infra destroyed from beiging
                return -simulatedWarNode.raidDistance(origin);
            }
        };

        Function<SimulatedWarNode, Double> warFunction = new Function<SimulatedWarNode, Double>() {
            @Override
            public Double apply(SimulatedWarNode node) {
                return -node.warDistance(origin);
            }
        };

        Function<SimulatedWarNode, Double> valueFunction = war ? warFunction : raidFunction;

        Function<SimulatedWarNode, SimulatedWarNode.WarGoal> goal = new Function<SimulatedWarNode, SimulatedWarNode.WarGoal>() {
            @Override
            public SimulatedWarNode.WarGoal apply(SimulatedWarNode node) {
                if (node.getAggressor().getResistance() <= 0 || node.getDefender().getResistance() <= 0 || node.getTurnsLeft() <= 0) {
                    return SimulatedWarNode.WarGoal.SUCCESS;
                }
                return SimulatedWarNode.WarGoal.CONTINUE;
            }
        };

        SimulatedWarNode solution;
        String verb;
        if (origin.getAggressor().getNation().equals(me) && origin.getDefender().getNation().getActive_m() > 10000) {
            verb = "for optimal loot";

            SimulatedWar warSim = new SimulatedWar(origin, valueFunction, goal);

            RateLimitUtil.queue(event.getChannel().editMessageById(msg2.getIdLong(), " - (Processing..."));

            solution = warSim.solve();
        } else {
            verb = "for both nations";
            double alpha = Double.NEGATIVE_INFINITY;
            double beta = Double.POSITIVE_INFINITY;
            solution = origin.minimax(start, alpha, beta, true, valueFunction, goal);
        }

        System.out.println("Took " + (System.currentTimeMillis() - start));

        if (solution == null) {
            return "**You cannot win this war with your current military composition**";
        }

        StringBuilder result = new StringBuilder();

        double total = solution.raidDistance(origin);

        List<SimulatedWarNode> solutionList = solution.toActionList();

        RateLimitUtil.queue(event.getChannel().editMessageById(msg2.getIdLong(), "**The following attack orders are recommended " + verb + "**(:"));

        int wait = 0;
        for (SimulatedWarNode node : solutionList) {
            if (node.getMethod() == WarNation.Actions.WAIT) {
                wait++;
                continue;
            }
            if (wait != 0) {
//                result.append('\n').append("Wait x" + wait);
                wait = 0;
            }
            result.append('\n').append(node.getActionString());
        }

        MessageEmbed card = solution.toString(origin);

        DiscordUtil.sendMessage(event.getChannel(), result.toString());

        event.getChannel().sendMessageEmbeds(card).complete();

        String totalMsg;
        String attName = origin.getAggressor().getNation().getNation();
        if (total > 0) {
            totalMsg = "Fighting this war will cost an estimated: $" + Math.abs((int) total) + " for " + attName;
        } else {
            totalMsg = "Fighting this war will net an estimated: $" + Math.abs((int) total) + " for " + attName + " (super inaccurate guess)";
        }
        event.getChannel().sendMessage(totalMsg).complete();

        return null;
    }
}
