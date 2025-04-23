package link.locutus.discord.commands.war;

import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.PWBindings;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.entities.WarStatus;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.*;
import link.locutus.discord.util.battle.SpyBlitzGenerator;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.io.PagePriority;
import link.locutus.discord.util.offshore.Auth;
import link.locutus.discord.util.scheduler.KeyValue;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Spyops extends Command {
    public Spyops() {
        super("spyops", "spyop", CommandCategory.MILCOM, CommandCategory.GAME_INFO_AND_TOOLS, CommandCategory.MEMBER);
    }

    @Override
    public List<CommandRef> getSlashReference() {
        return List.of(CM.spy.find.target.cmd);
    }


    @Override
    public String help() {
        return super.help() + " <alliance|coaltion|*> <" + StringMan.join(SpyCount.Operation.values(), "|") + ">";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return Roles.MEMBER.has(user, server);
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
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        String nationStr = DiscordUtil.parseArg(args, "nation");
        DBNation finalNation = nationStr == null ? me : PWBindings.nation(null, nationStr);

        if (flags.contains('d')) {
            channel = new DiscordChannelIO(RateLimitUtil.complete(author.openPrivateChannel()));
        } else {
            channel = channel;
        }

        CompletableFuture<IMessageBuilder> msgFuture = (channel.sendMessage("Please wait... "));
        GuildDB db = Locutus.imp().getGuildDB(guild);

        try {
            String title = "Recommended ops";
            String body = run(channel, author, me, finalNation, db, args, flags);

            if (flags.contains('r')) {
                return body;
            } else {
                channel.create().embed(title, body).send();
            }

            if (!flags.contains('f')) {
                StringBuilder response = new StringBuilder("Use `" + Settings.commandPrefix(true) + "spies <enemy>` first to ensure the results are up to date");
                if (!flags.contains('s')) {
                    response.append(". Add `-s` to remove enemies who are already spy slotted");
                }
                channel.sendMessage(response.toString());
                return null;
            }
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

    public String run(IMessageIO channel, User author, DBNation me, DBNation attacker, GuildDB db, List<String> args, Set<Character> flags) throws IOException {
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
            return usage(args.size(), 2, channel);
        }
        if (attacker == null) {
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
        if (attacker.getAlliance_id() != 0) allies.add(attacker.getAlliance_id());
        Set<Integer> aaIds = db.getAllianceIds();
        if (!aaIds.isEmpty()) allies.addAll(aaIds);

        Set<Integer> myEnemies = Locutus.imp().getWarDb().getWarsByNation(attacker.getNation_id()).stream()
                .map(dbWar -> dbWar.getAttacker_id() == attacker.getNation_id() ? dbWar.getDefender_id() : dbWar.getAttacker_id())
                .collect(Collectors.toSet());

        Function<DBNation, Boolean> isInSpyRange = nation -> attacker.isInSpyRange(nation) || myEnemies.contains(nation.getNation_id());

        Function<Integer, Boolean> isInvolved = integer -> {
            if (integer == attacker.getNation_id()) return true;
            DBNation nation = Locutus.imp().getNationDB().getNationById(integer);
            return nation != null && allies.contains(nation.getAlliance_id());
        };

        Collection<DBNation> nations;
        if (args.get(0).equalsIgnoreCase("*")) {
            Map<Integer, DBWar> active = Locutus.imp().getWarDb().getWars(WarStatus.ACTIVE);
            nations = new ObjectLinkedOpenHashSet<>();
            for (DBWar value : active.values()) {
                int other;
                if (isInvolved.apply(value.getAttacker_id())) {
                    other = value.getDefender_id();
                } else if (isInvolved.apply(value.getDefender_id())) {
                    other = value.getAttacker_id();
                } else {
                    continue;
                }
                DBNation nation = Locutus.imp().getNationDB().getNationById(other);
                if (nation != null) {
                    nations.add(nation);
                }
            }
        } else {
            nations = DiscordUtil.parseNations(db.getGuild(), author, me, args.get(0), false, false);
        }
        nations.removeIf(nation -> {
            if (!isInSpyRange.apply(nation)) return true;
            if (nation.getVm_turns() > 0) return true;
            return false;
        });

        nations.removeIf(f -> f.active_m() > 2880);
        nations.removeIf(f -> f.getPosition() <= Rank.APPLICANT.id);

        if (nations.isEmpty()) {
            return "No nations found (1)";
        }

        int mySpies = attacker.updateSpies(PagePriority.ESPIONAGE_ODDS_SINGLE);
        long dcTime = TimeUtil.getTimeFromTurn(TimeUtil.getTurn() - (TimeUtil.getTurn() % 12));

        List<Map.Entry<DBNation, Map.Entry<SpyCount.Operation, Map.Entry<Integer, Double>>>> netDamage = new ArrayList<>();

        for (DBNation nation : nations) {
            Integer spies = nation.updateSpies(PagePriority.ESPIONAGE_ODDS_SINGLE, false, flags.contains('f'));
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
            int maxMissile = MilitaryUnit.MISSILE.getMaxPerDay(nation.getCities(), nation::hasProject);
            if (opTypesList.contains(SpyCount.Operation.MISSILE) && nation.getMissiles() > 0 && nation.getMissiles() <= maxMissile) {
                Map<Long, Integer> purchases = nation.getUnitPurchaseHistory(MilitaryUnit.MISSILE, dcTime);
                if (!purchases.isEmpty()) opTypesList.remove(SpyCount.Operation.MISSILE);
            }

            int maxNuke = MilitaryUnit.NUKE.getMaxPerDay(nation.getCities(), nation::hasProject);
            if (opTypesList.contains(SpyCount.Operation.NUKE) && nation.getNukes() > 0 && nation.getNukes() <= maxNuke) {
                Map<Long, Integer> purchases = nation.getUnitPurchaseHistory(MilitaryUnit.NUKE, dcTime);
                if (!purchases.isEmpty()) opTypesList.remove(SpyCount.Operation.NUKE);
            }
            opTypes = opTypesList.toArray(new SpyCount.Operation[0]);


            Map.Entry<SpyCount.Operation, Map.Entry<Integer, Double>> best = SpyCount.getBestOp(!flags.contains('k'), mySpies, nation, me.hasProject(Projects.SPY_SATELLITE), opTypes);
            if (best != null) {
                double netDamageCost = best.getValue().getValue();

                double valueModifier = SpyBlitzGenerator.estimateValue(nation, false, null);
                netDamageCost *= valueModifier;

                best.getValue().setValue(netDamageCost);
                netDamage.add(new KeyValue<>(nation, best));
            }
        }

        Collections.sort(netDamage, (o1, o2) -> Double.compare(o2.getValue().getValue().getValue(), o1.getValue().getValue().getValue()));

        if (netDamage.isEmpty()) {
            return "No nations found (2)";
        }

        StringBuilder body = new StringBuilder("Results for " + attacker.getNation() + ":\n");

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
                Integer enemySpies = nation.updateSpies(PagePriority.ESPIONAGE_ODDS_SINGLE, false, false);
                spiesUsed = SpyCount.getRecommendedSpies(spiesUsed, enemySpies, safety, op, nation);
            }

            double kills = SpyCount.getKills(spiesUsed, nation, op, attacker.hasProject(Projects.SPY_SATELLITE));

            Integer enemySpies = nation.getSpies();
            double odds = SpyCount.getOdds(spiesUsed, enemySpies, safety, op, nation);
            if (odds <= minSuccess) continue;

            int finalSpiesUsed = spiesUsed;
            Runnable task = new Runnable() {
                @Override
                public void run() {
                    String nationUrl = PW.getMarkdownUrl(nation.getNation_id(), false);
                    String allianceUrl = PW.getMarkdownUrl(nation.getAlliance_id(), true);
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
            targets.add(new KeyValue<>(nation, task));
        }

        targets.removeIf(f -> f.getKey().isEspionageFull());

        if (flags.contains('s')) {
            Auth auth = Locutus.imp().getRootAuth();
            PW.withLogin(new Callable<Void>() {
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
