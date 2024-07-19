package link.locutus.discord.commands.war;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.SpyCount;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.io.PagePriority;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class CounterSpy extends Command {
    public CounterSpy() {
        super(CommandCategory.MILCOM, CommandCategory.GAME_INFO_AND_TOOLS, CommandCategory.MEMBER);
    }

    @Override
    public List<CommandRef> getSlashReference() {
        return List.of(CM.spy.counter.cmd);
    }

    @Override
    public String help() {
        return super.help() + " <enemy> <type> [alliances]";
    }

    @Override
    public String desc() {
        return "Find a nation to do a spy op against the specified enemy\n" +
                "Op types: " + StringMan.getString(SpyCount.Operation.values()) + " or `*` (for all op types)\n" +
                "The alliance argument is optional\n" +
                "Use `success>80` to specify a cutoff for spyop success";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return Roles.MEMBER.has(user, server);
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        double minSuccess = 0;

        Iterator<String> iterator = args.iterator();
        while (iterator.hasNext()) {
            String arg = iterator.next();
            String[] split = arg.split(">");
            if (split.length == 2) {
                switch (split[0].toLowerCase()) {
                    case "success": {
                        minSuccess = MathMan.parseDouble(split[1]);
                        iterator.remove();
                    }
                }
            }
        }
        if (args.size() != 2 && args.size() != 3) return usage(args.size(), 2, 3, channel);

        DBNation enemy;
        if (args.get(0).startsWith("" + Settings.INSTANCE.PNW_URL() + "/nation/war/")) {
            // war url
            int warId = Integer.parseInt(args.get(0).split("=")[1].replaceAll("/", ""));
            DBWar war = Locutus.imp().getWarDb().getWar(warId);

            int counterId = war.getAttacker_id();
            DBNation counter = Locutus.imp().getNationDB().getNation(counterId);

            int defenderId;
            if (counter.getAlliance_id() == me.getAlliance_id() || (guild != null && Locutus.imp().getGuildDB(guild).getAllies().contains(counter.getAlliance_id()))) {
                counterId = (war.getDefender_id());
                counter = Locutus.imp().getNationDB().getNation(counterId);
                defenderId = (war.getAttacker_id());
            } else {
                defenderId = (war.getDefender_id());
            }
            enemy = counter;
        } else {
            enemy = DiscordUtil.parseNation(args.get(0));
        }

        if (enemy == null) return "Invalid enemy: `" + args.get(1) + "`";
        SpyCount.Operation operation;
        if (args.get(1).equalsIgnoreCase("*")) {
            operation = SpyCount.Operation.INTEL;
        } else {
            try {
                operation = SpyCount.Operation.valueOf(args.get(1).toUpperCase());
            }catch (IllegalArgumentException e) {
                return "Unknown op: `" + args.get(1) + "`" + ". Valid options are: " + StringMan.getString(SpyCount.Operation.values());
            }
        }
        Set<DBNation> toCounter;
        if (args.size() == 3) {
            toCounter = DiscordUtil.parseNations(guild, author, me, args.get(2), false, false);
        } else {
            if (me.getAlliance_id() == 0) return usage("You are not in an alliance", channel);
            toCounter = Locutus.imp().getNationDB().getNations(Collections.singleton(me.getAlliance_id()));
        }
        toCounter.removeIf(n -> n.getSpies() == 0 || !n.isInSpyRange(enemy) || n.active_m() > TimeUnit.DAYS.toMinutes(2));

        List<Map.Entry<DBNation, Map.Entry<SpyCount.Operation, Map.Entry<Integer, Double>>>> netDamage = new ArrayList<>();

        CompletableFuture<IMessageBuilder> msgFuture = channel.sendMessage("Please wait...");

        try {
            Integer enemySpies = enemy.updateSpies(PagePriority.ESPIONAGE_ODDS_SINGLE);

            for (DBNation counterWith : toCounter) {
                Integer mySpies = counterWith.getSpies();
                if (mySpies == null) {
                    continue;
                }

                if (enemySpies == -1) {
                    return "Unknown enemy spies";
                }
                switch (operation) {
                    case SPIES:
                        if (enemySpies == 0) return "No enemy spies";
                        break;
                    case INTEL:

                }

                SpyCount.Operation[] opTypes = SpyCount.Operation.values();
                if (operation != SpyCount.Operation.INTEL) {
                    opTypes = new SpyCount.Operation[] {operation};
                }
                Map.Entry<SpyCount.Operation, Map.Entry<Integer, Double>> best = SpyCount.getBestOp(mySpies, enemy, counterWith.hasProject(Projects.SPY_SATELLITE), opTypes);
                if (best != null) {
                    netDamage.add(new AbstractMap.SimpleEntry<>(counterWith, best));
                }
            }

            Collections.sort(netDamage, (o1, o2) -> Double.compare(o2.getValue().getValue().getValue(), o1.getValue().getValue().getValue()));

            if (netDamage.isEmpty()) {
                return "No nations found";
            }

            String title = "Recommended ops";
            StringBuilder body = new StringBuilder();

            int nationCount = 0;
            for (int i = 0; i < netDamage.size(); i++) {
                Map.Entry<DBNation, Map.Entry<SpyCount.Operation, Map.Entry<Integer, Double>>> entry = netDamage.get(i);

                Map.Entry<SpyCount.Operation, Map.Entry<Integer, Double>> opinfo = entry.getValue();
                SpyCount.Operation op = opinfo.getKey();
                Map.Entry<Integer, Double> safetyDamage = opinfo.getValue();

                DBNation nation = entry.getKey();
                Integer safety = safetyDamage.getKey();
                Double damage = safetyDamage.getValue();

                int attacking = entry.getKey().getSpies();
                int spiesUsed = attacking;
                if (operation != SpyCount.Operation.SPIES) {
                    spiesUsed = SpyCount.getRecommendedSpies(attacking, enemy.getSpies(), safety, operation, enemy);
                }

                double odds = SpyCount.getOdds(spiesUsed, enemySpies, safety, op, enemy);
                if (odds <= minSuccess) continue;
                if (++nationCount >= 10) break;

                double kills = SpyCount.getKills(spiesUsed, enemy, op, nation.hasProject(Projects.SPY_SATELLITE));

                String nationUrl = PW.getBBUrl(nation.getNation_id(), false);
                String allianceUrl = PW.getBBUrl(nation.getAlliance_id(), true);
                body.append(nationUrl).append(" | ")
                        .append(allianceUrl).append("\n");

                String safetyStr = safety == 3 ? "covert" : safety == 2 ? "normal" : "quick";

                body.append(op.name())
                        .append(" (" + safetyStr + ") with ")
                        .append(nation.updateSpies(PagePriority.ESPIONAGE_ODDS_SINGLE) + " spies (")
                        .append(MathMan.format(odds) + "% for $")
                        .append(MathMan.format(damage) + "net damage)")
                        .append(" killing " + MathMan.format(kills) + " " + operation.unit.getName())
                        .append("\n")
                ;
            }

            body.append("**Enemy:** ")
                    .append(PW.getBBUrl(enemy.getNation_id(), false))
                    .append(" | ")
                    .append(PW.getBBUrl(enemy.getAlliance_id(), true))
                    .append("\n**Spies: **").append(enemySpies).append("\n")
                    .append(enemy.toMarkdown(true, true, false, true, false, false))
                    .append(enemy.toMarkdown(true, true, false, false, true, true))
                    ;


            channel.create().embed(title, body.toString()).send();
        } finally {
        }
        return null;
    }
}
