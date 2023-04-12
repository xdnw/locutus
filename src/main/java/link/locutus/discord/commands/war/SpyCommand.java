package link.locutus.discord.commands.war;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.domains.Nation;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.NationMeta;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.SpyCount;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SpyCommand extends Command {
    public SpyCommand() {
        super("spy", "spies", CommandCategory.MILCOM, CommandCategory.GAME_INFO_AND_TOOLS, CommandCategory.MEMBER);
    }

    @Override
    public String help() {
        return Settings.commandPrefix(true) + "spy <nation> [spies-used]";
    }

    @Override
    public String desc() {
        return "Calculate spies for a nation.\n" +
                "Nation argument can be nation name, id, link, or discord tag\n" +
                "If `spies-used` is provided, it will cap the odds at using that number of spies\n" +
                "`[safety]` defaults to what has the best net. Options: quick, normal, covert";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        GuildDB db = Locutus.imp().getGuildDB(server);
        return super.checkPermission(server, user) || Roles.MILCOM.has(user, server) || (Roles.MEMBER.has(user, server) && db.isValidAlliance());
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (me == null) return "Please use " + CM.register.cmd.toSlashMention() + "";
        if (args.size() < 1 || args.size() > 3) {
            return "Usage: `" + Settings.commandPrefix(true) + "spy <nation-link> [num-used] [safety]`";
        }

        Integer nationId = DiscordUtil.parseNationId(args.get(0));
        if (nationId == null) {
            return "invalid user/nation: " + nationId;
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

        DBNation nation = DBNation.byId(nationId);
        int result = nation.updateSpies(true, true);
        Long turnUpdate = nation.getTurnUpdatedSpies();
        long turnsAgo = TimeUtil.getTurn() - (turnUpdate == null ? 0 : turnUpdate);

        StringBuilder response = new StringBuilder(nation.getNation() + " has " + result + " spies (updated: " + turnsAgo + " turns ago)");
        response.append("\nRecommended:");

        int minSafety = requiredSafety == null ? 1 : requiredSafety;
        int maxSafety = requiredSafety == null ? 3 : requiredSafety;

        for (SpyCount.Operation op : SpyCount.Operation.values()) {
            Map.Entry<SpyCount.Operation, Map.Entry<Integer, Double>> best = SpyCount.getBestOp(true, op != SpyCount.Operation.SOLDIER, 60, nation, minSafety, maxSafety, op);
            if (best == null) continue;

            Map.Entry<Integer, Double> bestVal = best.getValue();
            Integer safetyOrd = bestVal.getKey();
            int recommended = SpyCount.getRecommendedSpies(60, result, safetyOrd, op, nation);
            recommended = Math.min(cap, recommended);

            double odds = SpyCount.getOdds(recommended, result, safetyOrd, op, nation);

            if (op == SpyCount.Operation.SOLDIER && nation.getSoldiers() == 0) op = SpyCount.Operation.INTEL;
            response.append("\n - ").append(op.name()).append(": ");

            String safety = safetyOrd == 3 ? "covert" : safetyOrd == 2 ? "normal" : "quick";

            response.append(recommended + " spies on " + safety + " = " + MathMan.format(Math.min(95, odds)) + "%");
        }
        if (nation.getMissiles() > 0 || nation.getNukes() > 0) {
            long dcTime = TimeUtil.getTimeFromTurn(TimeUtil.getTurn() - (TimeUtil.getTurn() % 12));

            int maxMissile = nation.hasProject(Projects.SPACE_PROGRAM) ? 2 : 1;
            if (nation.getMissiles() == maxMissile) {
                Map<Long, Integer> purchases = nation.getUnitPurchaseHistory(MilitaryUnit.MISSILE, dcTime);
                if (!purchases.isEmpty()) {
                    response.append("\n`note: bought missile today`");
                }
            }

            if (nation.getNukes() == 1) {
                Map<Long, Integer> purchases = nation.getUnitPurchaseHistory(MilitaryUnit.NUKE, dcTime);
                if (!purchases.isEmpty()) {
                    response.append("\n`note: bought nuke today`");
                }
            }
        }

        return response.toString();
    }
}