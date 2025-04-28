package link.locutus.discord.commands.war;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.PWBindings;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.NationMeta;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.SpyCount;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.util.io.PagePriority;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class SpyCommand extends Command {
    public SpyCommand() {
        super("spy", "spies", CommandCategory.MILCOM, CommandCategory.GAME_INFO_AND_TOOLS, CommandCategory.MEMBER);
    }

    @Override
    public List<CommandRef> getSlashReference() {
        return List.of(CM.nation.spies.cmd);
    }

    @Override
    public String help() {
        return Settings.commandPrefix(true) + "spy <nation> [spies-used]";
    }

    @Override
    public String desc() {
        return """
                Calculate spies for a nation.
                Nation argument can be nation name, id, link, or discord tag
                If `spies-used` is provided, it will cap the odds at using that number of spies
                `[safety]` defaults to what has the best net. Options: quick, normal, covert""";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        GuildDB db = Locutus.imp().getGuildDB(server);
        return super.checkPermission(server, user) || Roles.MILCOM.has(user, server) || (Roles.MEMBER.has(user, server) && db.isValidAlliance());
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (me == null) return "Please use " + CM.register.cmd.toSlashMention();
        if (args.size() < 1 || args.size() > 3) {
            return "Usage: `" + Settings.commandPrefix(true) + "spy <nation-link> [num-used] [safety]`";
        }

        DBNation nation = PWBindings.nation(null, args.get(0));
        if (nation == null) {
            return "invalid user/nation: `" + args.get(0) + "`";
        }
        Integer cap = 60;
        if (args.size() >= 2) cap = MathMan.parseInt(args.get(1));
        if (cap == null) return "Invalid number of spies used: `" + args.get(1) + "`";

        Integer requiredSafety = null;
        if (args.size() >= 3) {
            switch (args.get(2).toLowerCase()) {
                case "1":
                case "quick":
                    requiredSafety = 1;
                    break;
                case "2":
                case "normal":
                    requiredSafety = 2;
                    break;
                case "3":
                case "covert":
                    requiredSafety = 3;
                    break;
                default:
                    throw new IllegalArgumentException("Invalid safety: " + args.get(2));
            }
        }

        me.setMeta(NationMeta.INTERVIEW_SPIES, (byte) 1);


        int result = nation.updateSpies(PagePriority.ESPIONAGE_ODDS_SINGLE, true, true);
        Long timeUpdated = nation.getTimeUpdatedSpies();
        long timeAgo = System.currentTimeMillis() - (timeUpdated == null ? 0 : timeUpdated);
        String timeAgoStr = timeUpdated == null || timeUpdated == 0 ? "Unknown" :  TimeUtil.secToTime(TimeUnit.MILLISECONDS, timeAgo);

        StringBuilder response = new StringBuilder(nation.getNation() + " has " + result + " spies (updated: " + timeAgoStr + " ago)");
        response.append("\nRecommended:");

        int minSafety = requiredSafety == null ? 1 : requiredSafety;
        int maxSafety = requiredSafety == null ? 3 : requiredSafety;

        for (SpyCount.Operation op : SpyCount.Operation.values()) {
            Map.Entry<SpyCount.Operation, Map.Entry<Integer, Double>> best = SpyCount.getBestOp(true, op != SpyCount.Operation.SOLDIER, 60, nation, minSafety, maxSafety, me.hasProject(Projects.SPY_SATELLITE), op);
            if (best == null) continue;

            Map.Entry<Integer, Double> bestVal = best.getValue();
            Integer safetyOrd = bestVal.getKey();
            int recommended = SpyCount.getRecommendedSpies(60, result, safetyOrd, op, nation);
            recommended = Math.min(cap, recommended);

            double odds = SpyCount.getOdds(recommended, result, safetyOrd, op, nation);

            if (op == SpyCount.Operation.SOLDIER && nation.getSoldiers() == 0) op = SpyCount.Operation.INTEL;
            response.append("\n- ").append(op.name()).append(": ");

            String safety = safetyOrd == 3 ? "covert" : safetyOrd == 2 ? "normal" : "quick";

            response.append(recommended + " spies on " + safety + " = " + MathMan.format(Math.min(95, odds)) + "%");
        }
        if (nation.getMissiles() > 0 || nation.getNukes() > 0) {
            long dcTime = TimeUtil.getTimeFromTurn(TimeUtil.getTurn() - (TimeUtil.getTurn() % 12));

            int maxMissile = MilitaryUnit.MISSILE.getMaxPerDay(nation.getCities(), nation::hasProject);
            if (nation.getMissiles() == maxMissile) {
                Map<Long, Integer> purchases = nation.getUnitPurchaseHistory(MilitaryUnit.MISSILE, dcTime);
                if (!purchases.isEmpty()) {
                    response.append("\n`note: bought missile today`");
                }
            }

            int nukePerDay = MilitaryUnit.NUKE.getMaxPerDay(nation.getCities(), nation::hasProject);
            if (nation.getNukes() <= nukePerDay) {
                Map<Long, Integer> purchases = nation.getUnitPurchaseHistory(MilitaryUnit.NUKE, dcTime);
                if (!purchases.isEmpty()) {
                    response.append("\n`note: bought nuke today`");
                }
            }
        }

        return response.toString();
    }
}