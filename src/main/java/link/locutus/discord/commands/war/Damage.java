package link.locutus.discord.commands.war;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.*;
import java.util.stream.Collectors;

public class Damage extends Command {
    public Damage() {
        super("damage", "whales", CommandCategory.MILCOM, CommandCategory.GAME_INFO_AND_TOOLS, CommandCategory.MEMBER);
    }

    @Override
    public String help() {
        return Settings.commandPrefix(true) + "damage <alliance|coalition|*> [options...]";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String desc() {
        return "Find a raid target, with optional alliance and sorting (default: active nations, sorted by top city infra).\n\t" +
                "To see a list of coalitions, use `" + Settings.commandPrefix(true) + "coalitions`.\n\t" +
                "Add `-a` To include applicants\n" +
                "Add `-i` to include inactives\n" +
                "Add `-w` to filter out nations with strong ground\n" +
                "Add `-s` to filter out nations with >2 ships\n" +
                "Add `-n:40` to filter out nations with more than 40% of your ships\n" +
                "Add `-m` to sort by mean infra instead of city max\n" +
                "Add `-c` to sort by city max instead of damage estimate\n" +
                "Add `-b` to include beige targets" +
                "Add `score:1234` to filter by war range";
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        Double score = DiscordUtil.parseArgDouble(args, "score");
        if (args.size() != 1) return usage();

        boolean includeApps = flags.contains('a');
        boolean includeInactives = flags.contains('i');
        boolean filterWeak = flags.contains('w');
        boolean filterNoShips = flags.contains('s');
        boolean mean = flags.contains('m');
        boolean includeBeige = flags.contains('b');

        Set<DBNation> nations;
        if (args.get(0).equalsIgnoreCase("counters")) {
            nations = new HashSet<>();
            for (DBWar war : me.getActiveWars()) {
                if (war.getAttacker_id() != me.getNation_id() && war.getAttacker_aa() != 0) {
                    nations.addAll(Locutus.imp().getNationDB().getNations(Collections.singleton(war.getAttacker_aa())));
                }
            }
        } else {
            nations = DiscordUtil.parseNations(guild, args.get(0));
        }
        nations.removeIf(f -> f.getDef() >= 3);
        nations.removeIf(f -> f.getVm_turns() != 0);
        if (!includeApps) nations.removeIf(f -> f.getPosition() <= 1);
        if (!includeInactives) nations.removeIf(f -> f.getActive_m() > (f.getCities() > 11 ? 5 : 2) * 1440);
        if (filterNoShips) nations.removeIf(f -> f.getShips() > 2);
        if (!includeBeige) nations.removeIf(f -> f.isBeige());

        if (score == null) score = me.getScore();
        double minScore = score * 0.75;
        double maxScore = score * 1.75;

        nations.removeIf(f -> f.getScore() <= minScore || f.getScore() >= maxScore);

        me = DiscordUtil.getNation(author);
        if (me == null) return "Please use " + CM.register.cmd.toSlashMention() + "";
        double str = me.getGroundStrength(false, true);
        str = Math.max(str, me.getCities() * 15000);
        if (filterWeak) {
            double finalStr = str;
            nations.removeIf(f -> f.getGroundStrength(true, false) > finalStr * 0.4);
        }


        Map<Integer, Double> maxInfraByNation = new HashMap<>();
        Map<Integer, Double> damageEstByNation = new HashMap<>();
        Map<Integer, Double> avgInfraByNation = new HashMap<>();

        Set<Integer> nationIds = nations.stream().map(f -> f.getNation_id()).collect(Collectors.toSet());
        Map<Integer, List<Double>> cityInfraByNation = new HashMap<>();

        {
            for (DBNation nation : nations) {
                Collection<JavaCity> cities = nation.getCityMap(false, false, false).values();
                List<Double> allInfra = cities.stream().map(f -> f.getInfra()).collect(Collectors.toList());
                double max = Collections.max(allInfra);
                double average = allInfra.stream().mapToDouble(f -> f).average().orElse(0);
                avgInfraByNation.put(nation.getNation_id(), average);
                maxInfraByNation.put(nation.getNation_id(), max);
                cityInfraByNation.put(nation.getNation_id(), allInfra);
            }
        }

        {
            for (Map.Entry<Integer, List<Double>> entry : cityInfraByNation.entrySet()) {
                double cost = damageEstimate(me, entry.getKey(), entry.getValue());
                if (cost <= 0) continue;
                damageEstByNation.put(entry.getKey(), cost);
            }

        }

        Map<Integer, Double> valueFunction;
        if (flags.contains('m')) valueFunction = avgInfraByNation;
        else if (flags.contains('c')) valueFunction = maxInfraByNation;
        else valueFunction = damageEstByNation;

        if (flags.contains('d')) {
            channel = new DiscordChannelIO(RateLimitUtil.complete(author.openPrivateChannel()));
        }

        if (valueFunction.isEmpty()) {
            channel.sendMessage("No results (found");
            return null;
        }

        List<Map.Entry<DBNation, Double>>  maxInfraSorted = new ArrayList<>();
        for (Map.Entry<Integer, Double> entry : valueFunction.entrySet()) {
            DBNation nation = DBNation.getById(entry.getKey());
            double amt = entry.getValue();
            maxInfraSorted.add(new AbstractMap.SimpleEntry<>(nation, amt));
        }
        maxInfraSorted.sort((o1, o2) -> Double.compare(o2.getValue(), o1.getValue()));

        StringBuilder response = new StringBuilder("**Results for " + me.getNation() + ":**\n");
        for (int i = 0; i < Math.min(15, maxInfraSorted.size()); i++) {
            Map.Entry<DBNation, Double> entry = maxInfraSorted.get(i);
            DBNation nation = entry.getKey();

            double numCities = 2;
            if (nation.getGroundStrength(true, false) < me.getGroundStrength(true, false) * 0.4) {
                numCities++;
                if (nation.getAircraft() <= me.getAircraft()) numCities += 5;
            }
            if (nation.getActive_m() > 2440) numCities++;
            if (nation.getShips() <= 1 && me.getShips() > 1) numCities += 0.3;
            if (nation.getCities() <= me.getCities() * 0.5) numCities++;
            if (nation.getActive_m() > 10000) numCities++;

            List<Double> cityInfra = new ArrayList<>();

            double cost = damageEstByNation.getOrDefault(nation.getNation_id(), 0d);
            String moneyStr = "$" + MathMan.format(cost);
            response.append(moneyStr + " | " + nation.toMarkdown(true));
        }
        channel.send(response.toString().trim());
        return null;
    }

    public double damageEstimate(DBNation me, int nationId, List<Double> cityInfra) {
        DBNation nation = DBNation.getById(nationId);
        if (nation == null) return 0;


        double numCities = 0;
        if (me.hasProject(Projects.MISSILE_LAUNCH_PAD)) {
            numCities += 0.5;
            if (nation.hasProject(Projects.IRON_DOME)) numCities -= 0.25;
        }
        if (me.hasProject(Projects.NUCLEAR_RESEARCH_FACILITY)) {
            numCities += 1.5;
            if (nation.hasProject(Projects.VITAL_DEFENSE_SYSTEM)) numCities -= 0.3;
        }
        if (nation.getGroundStrength(true, false) < me.getGroundStrength(true, false) * 0.4) {
            numCities++;
            if (nation.getAircraft() <= me.getAircraft()) numCities += 5;
        }
        if (nation.getActive_m() > 2440) numCities+=0.5;
        if (nation.getActive_m() > 4880) numCities+=0.5;
        if (nation.getShips() <= 1 && me.getShips() > 1) numCities += 0.3;
        if (nation.getCities() <= me.getCities() * 0.5) numCities++;
        if (nation.getActive_m() > 10000) numCities += 10;

        if (numCities == 0) return 0;

        double cost = 0;
        Collections.sort(cityInfra);
        int i = cityInfra.size() - 1;
        while (i >= 0 && numCities > 0) {
            Double infra = cityInfra.get(i);
            if (infra <= 600) break;
            double factor = Math.min(numCities, 1);
            cost += factor * PnwUtil.calculateInfra(infra * 0.6-500, infra);

            i--;
            numCities--;
        }
        return cost;
    }
}
