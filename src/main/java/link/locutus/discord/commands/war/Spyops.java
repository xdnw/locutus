package link.locutus.discord.commands.war;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.PWBindings;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.entities.WarStatus;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.battle.SpyBlitzGenerator;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.SpyCount;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.offshore.Auth;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.io.IOException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Spyops extends Command {
    public Spyops() {
        super("spyops", "spyop", CommandCategory.MILCOM, CommandCategory.GAME_INFO_AND_TOOLS, CommandCategory.MEMBER);
    }
    @Override
    public String help() {
        return super.help() + " <alliance|coaltion|*> <" + StringMan.join(SpyCount.Operation.values(), "|") + ">";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return (super.checkPermission(server, user));
    }

    @Override
    public String desc() {
        return "Find the optimal spy ops to use:\n" +
                "Use `*` for the alliance to only include active wars against allies\n" +
                "Use `*` for op type to automatically find the best op type\n" +
                "Available op types: " + StringMan.join(SpyCount.Operation.values(), ", ") + "\n" +
                "Use `success>80` to specify a cutoff for spyop success\n\n" +
                "e.g. `" + Settings.commandPrefix(true) + "spyop enemies spies` | `" + Settings.commandPrefix(true) + "spyop enemies * -s`\n" +
                "Add `-s` to force an update of nations with full spy slots\n" +
                "Add `-k` to prioritize kills\n" +
                "Add `nation:Borg` to specify nation";
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        String nationStr = DiscordUtil.parseArg(args, "nation");
        DBNation finalNation = nationStr == null ? me : PWBindings.nation(null, nationStr);

        MessageChannel channel;
        if (flags.contains('d')) {
            channel = RateLimitUtil.complete(event.getAuthor().openPrivateChannel());
        } else {
            channel = event.getGuildChannel();
        }

        CompletableFuture<Message> msg = channel.sendMessage("Please wait... ").submit();
        GuildDB db = Locutus.imp().getGuildDB(event);

        try {
            String title = "Recommended ops";
            String body = run(event, finalNation, db, args, flags);

            if (flags.contains('r')) {
                return body;
            } else {
                DiscordUtil.createEmbedCommand(channel, title, body.toString());
            }

            if (!flags.contains('f')) {
                StringBuilder response = new StringBuilder("Use `" + Settings.commandPrefix(true) + "spies <enemy>` first to ensure the results are up to date");
                if (!flags.contains('s')) {
                    response.append(". Add `-s` to remove enemies who are already spy slotted");
                }
                RateLimitUtil.queue(channel.sendMessage(response.toString()));
                return null;
            }
            return null;
        } finally {
            Message msgObj = msg.get();
            RateLimitUtil.queue(event.getChannel().deleteMessageById(msgObj.getIdLong()));
        }
    }

    public String run(MessageReceivedEvent event, DBNation me, GuildDB db, List<String> args, Set<Character> flags) throws IOException {
        double minSuccess = 50;
        int numOps = 5;

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
        if (args.size() != 2) {
            return usage(event);
        }
        if (me == null) {
            return "Please use " + CM.register.cmd.toSlashMention() + "";
        }

        SpyCount.Operation operation;
        try {
            operation = args.size() >= 1 ? SpyCount.Operation.valueOf(args.get(1).toUpperCase()) : SpyCount.Operation.INTEL;

            if (operation.unit == null && operation != SpyCount.Operation.SPIES) {
                return "Try `" + Settings.commandPrefix(true) + "loot <nation>`, `" + Settings.commandPrefix(true) + "who <nation>`, `" + Settings.commandPrefix(true) + "spies <nation>` or (buggy) `" + Settings.commandPrefix(true) + "DebugGetRss <nation>`";
            }
        } catch (IllegalArgumentException e) {
            if (!args.get(1).equalsIgnoreCase("*")) {
                return "Invalid op type: `" + args.get(1) + "`" + ". Valid options are: " + StringMan.join(SpyCount.Operation.values(), ", ");
            }
            operation = SpyCount.Operation.INTEL; // placeholder
        }

        Set<Integer> allies = new HashSet<>();
        Set<Integer> alliesCoalition = db.getCoalition("allies");
        if (alliesCoalition != null) allies.addAll(alliesCoalition);
        if (me.getAlliance_id() != 0) allies.add(me.getAlliance_id());
        Integer allianceId = db.getOrNull(GuildDB.Key.ALLIANCE_ID);
        if (allianceId != null) allies.add(allianceId);

        Set<Integer> myEnemies = Locutus.imp().getWarDb().getWarsByNation(me.getNation_id()).stream()
                .map(dbWar -> dbWar.attacker_id == me.getNation_id() ? dbWar.defender_id : dbWar.attacker_id)
                .collect(Collectors.toSet());

        Function<DBNation, Boolean> isInSpyRange = nation -> me.isInSpyRange(nation) || myEnemies.contains(nation.getNation_id());

        Function<Integer, Boolean> isInvolved = integer -> {
            if (integer == me.getNation_id()) return true;
            DBNation nation = Locutus.imp().getNationDB().getNation(integer);
            return nation != null && allies.contains(nation.getAlliance_id());
        };

        Collection<DBNation> nations;
        if (args.get(0).equalsIgnoreCase("*")) {
            Map<Integer, DBWar> active = Locutus.imp().getWarDb().getWars(WarStatus.ACTIVE);
            nations = new LinkedHashSet<>();
            for (DBWar value : active.values()) {
                int other;
                if (isInvolved.apply(value.attacker_id)) {
                    other = value.defender_id;
                } else if (isInvolved.apply(value.defender_id)) {
                    other = value.attacker_id;
                } else {
                    continue;
                }
                DBNation nation = Locutus.imp().getNationDB().getNation(other);
                if (nation != null) {
                    nations.add(nation);
                }
            }
        } else {
            nations = DiscordUtil.parseNations(db.getGuild(), args.get(0));
        }
        nations.removeIf(nation -> {
            if (!isInSpyRange.apply(nation)) return true;
            if (nation.getVm_turns() > 0) return true;
            return false;
        });

        nations.removeIf(f -> f.getActive_m() > 2880);
        nations.removeIf(f -> f.getPosition() <= Rank.APPLICANT.id);

        if (nations.isEmpty()) {
            return "No nations found (1)";
        }

        int mySpies = SpyCount.guessSpyCount(me);
        long dcTime = TimeUtil.getTimeFromTurn(TimeUtil.getTurn() - (TimeUtil.getTurn() % 12));

        List<Map.Entry<DBNation, Map.Entry<SpyCount.Operation, Map.Entry<Integer, Double>>>> netDamage = new ArrayList<>();

        for (DBNation nation : nations) {
            Integer spies = nation.updateSpies(false, flags.contains('f'));
            if (spies == null) {
                continue;
            }
            if (spies == -1) {
                continue;
            }
            switch (operation) {
                case SPIES:
                    if (spies == 0) continue;
                    break;
                case INTEL:
            }
            if (operation == SpyCount.Operation.SPIES && spies == 0) {
                continue;
            }
            SpyCount.Operation[] opTypes = SpyCount.Operation.values();
            if (operation != SpyCount.Operation.INTEL) {
                opTypes = new SpyCount.Operation[]{operation};
            }
            ArrayList<SpyCount.Operation> opTypesList = new ArrayList<>(Arrays.asList(opTypes));
            int maxMissile = nation.hasProject(Projects.SPACE_PROGRAM) ? 2 : 1;
            if (opTypesList.contains(SpyCount.Operation.MISSILE) && nation.getMissiles() == maxMissile) {
                Map<Long, Integer> purchases = nation.getUnitPurchaseHistory(MilitaryUnit.MISSILE, dcTime);
                if (!purchases.isEmpty()) opTypesList.remove(SpyCount.Operation.MISSILE);
            }

            if (opTypesList.contains(SpyCount.Operation.NUKE) && nation.getNukes() == 1) {
                Map<Long, Integer> purchases = nation.getUnitPurchaseHistory(MilitaryUnit.NUKE, dcTime);
                if (!purchases.isEmpty()) opTypesList.remove(SpyCount.Operation.NUKE);
            }
            opTypes = opTypesList.toArray(new SpyCount.Operation[0]);


            Map.Entry<SpyCount.Operation, Map.Entry<Integer, Double>> best = SpyCount.getBestOp(!flags.contains('k'), mySpies, nation, opTypes);
            if (best != null) {
                double netDamageCost = best.getValue().getValue();

                double valueModifier = SpyBlitzGenerator.estimateValue(nation, false);
                netDamageCost *= valueModifier;

                best.getValue().setValue(netDamageCost);
                netDamage.add(new AbstractMap.SimpleEntry<>(nation, best));
            }
        }

        Collections.sort(netDamage, (o1, o2) -> Double.compare(o2.getValue().getValue().getValue(), o1.getValue().getValue().getValue()));

        if (netDamage.isEmpty()) {
            return "No nations found (2)";
        }

        StringBuilder body = new StringBuilder("Results for " + me.getNation() + ":\n");

        ArrayList<Map.Entry<DBNation, Runnable>> targets = new ArrayList<>();

        for (int i = 0; i < netDamage.size(); i++) {
            Map.Entry<DBNation, Map.Entry<SpyCount.Operation, Map.Entry<Integer, Double>>> entry = netDamage.get(i);

            Map.Entry<SpyCount.Operation, Map.Entry<Integer, Double>> opinfo = entry.getValue();
            SpyCount.Operation op = opinfo.getKey();
            Map.Entry<Integer, Double> safetyDamage = opinfo.getValue();

            DBNation nation = entry.getKey();
            Integer safety = safetyDamage.getKey();
            Double damage = safetyDamage.getValue();

            int spiesUsed = mySpies;
            if (operation != SpyCount.Operation.SPIES) {
                Integer enemySpies = nation.updateSpies(false, false);
                spiesUsed = SpyCount.getRecommendedSpies(spiesUsed, enemySpies, safety, op, nation);
            }

            double kills = SpyCount.getKills(spiesUsed, nation, op, safety);

            Integer enemySpies = nation.getSpies();
            double odds = SpyCount.getOdds(spiesUsed, enemySpies, safety, op, nation);
            if (odds <= minSuccess) continue;

            int finalSpiesUsed = spiesUsed;
            Runnable task = new Runnable() {
                @Override
                public void run() {
                    String nationUrl = PnwUtil.getBBUrl(nation.getNation_id(), false);
                    String allianceUrl = PnwUtil.getBBUrl(nation.getAlliance_id(), true);
                    body.append(nationUrl).append(" | ")
                            .append(allianceUrl).append("\n");

                    body.append("Op: " + op.name()).append("\n")
                            .append("Safety: " + SpyCount.Safety.byId(safety)).append("\n")
                            .append("Enemy \uD83D\uDD0E: " + nation.getSpies()).append("\n")
                            .append("Attacker \uD83D\uDD0E: " + finalSpiesUsed).append("\n")
                            .append("Dmg: $" + MathMan.format(damage)).append("\n")
                            .append("Kills: " + MathMan.format(kills)).append("\n")
                            .append("Success: " + MathMan.format(odds)).append("%\n\n")
                    ;
                }
            };
            targets.add(new AbstractMap.SimpleEntry<>(nation, task));
        }

        targets.removeIf(f -> f.getKey().isEspionageFull());

        if (flags.contains('s')) {
            Auth auth = Locutus.imp().getRootAuth();
            PnwUtil.withLogin(new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    try {
                        int i = 0;
                        for (Map.Entry<DBNation, Runnable> target : targets) {
                            DBNation defender = target.getKey();
                            try {
                                if (defender.isEspionageFull()) continue;
                            } catch (Throwable e) {
                                e.printStackTrace();
                            }
                            target.getValue().run();
                            if (i++ >= numOps) break;
                        }
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }
                    return null;
                }
            }, auth);
        } else {
            for (int i = 0; i < Math.min(numOps, targets.size()); i++) {
                targets.get(i).getValue().run();
            }
        }
        return body.toString();
    }
}
