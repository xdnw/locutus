package link.locutus.discord.commands.rankings;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.CoalitionWarStatus;
import link.locutus.discord.db.entities.CounterStat;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.entities.Transaction2;
import link.locutus.discord.db.entities.WarAttackParser;
import link.locutus.discord.db.entities.AttackCost;
import link.locutus.discord.pnw.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.WarType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildMessageChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class WarCostAB extends Command {
    public WarCostAB() {
        super("warcost", "WarCostRankingAB", CommandCategory.GAME_INFO_AND_TOOLS, CommandCategory.MILCOM);
    }

    @Override
    public String help() {
        return "`" + super.help() + " <alliance|coalition> <alliance|coalition> <days>` OR `" + super.help() + " <war-url>`";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String desc() {
        return "Get the war cost between two entities.\n" +
                "Add -u to exclude unit cost\n" +
                "Add -i to exclude infra cost\n" +
                "Add -c to exclude consumption\n" +
                "Add -l to exclude loot\n" +
                "Add -w to list the wars (txt file)\n" +
                "Add -t to list the war types\n" +
                "Add `-s` to list war status";
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (args.isEmpty() || args.size() > 3 || (args.size() == 3 && args.get(0).equalsIgnoreCase(args.get(1)))) {
            return usage(event);
        }

        String arg0 = args.get(0);

        WarAttackParser parser = new WarAttackParser(guild, args, flags);

        AttackCost cost = parser.toWarCost();

        String withPirateStr = DiscordUtil.parseArg(args, "w_pirate");
        String withTypeStr = DiscordUtil.parseArg(args, "w_type");
        Boolean withPirate = withPirateStr == null ? null : Boolean.parseBoolean(withPirateStr);
        WarType withType = withTypeStr == null ? null : WarType.parse(withTypeStr);

        StringBuilder result = new StringBuilder(cost.toString(!flags.contains('u'), !flags.contains('i'), !flags.contains('c'), !flags.contains('l')));
        if (flags.contains('w')) {
            DiscordUtil.upload(event.getChannel(), cost.getNumWars() + " wars", " - " + StringMan.join(cost.getWarIds(), "\n - "));
        }
        if (flags.contains('t')) {
            List<DBWar> wars = Locutus.imp().getWarDb().getWarsById(cost.getWarIds());
            Map<WarType, Integer> byType = new HashMap<>();
            for (DBWar war : wars) {
                byType.put(war.getWarType(), byType.getOrDefault(war.getWarType(), 0) + 1);
            }
            StringBuilder response = new StringBuilder();
            for (Map.Entry<WarType, Integer> entry : byType.entrySet()) {
                response.append("\n" + entry.getKey() + ": " + entry.getValue());
            }
            DiscordUtil.createEmbedCommand(event.getChannel(), "War Types", response.toString());
        }
        if (flags.contains('s')) {
            List<DBWar> wars = Locutus.imp().getWarDb().getWarsById(cost.getWarIds());
            Map<CoalitionWarStatus, Integer> byStatus = new HashMap<>();
            for (DBWar war : wars) {
                CoalitionWarStatus status = null;
                switch (war.status) {
                    case ATTACKER_OFFERED_PEACE:
                    case DEFENDER_OFFERED_PEACE:
                    case ACTIVE:
                        status = CoalitionWarStatus.ACTIVE;
                        break;
                    case PEACE:
                        status = CoalitionWarStatus.PEACE;
                        break;
                    case EXPIRED:
                        status = CoalitionWarStatus.EXPIRED;
                        break;
                }
                if (status != null) {
                    byStatus.put(status, byStatus.getOrDefault(status, 0) + 1);
                }
            }
            int attVictory = cost.getVictories(true).size();
            int defVictory = cost.getVictories(false).size();
            byStatus.put(CoalitionWarStatus.COL1_VICTORY, attVictory);
            byStatus.put(CoalitionWarStatus.COL1_DEFEAT, defVictory);

            StringBuilder response = new StringBuilder();
            for (Map.Entry<CoalitionWarStatus, Integer> entry : byStatus.entrySet()) {
                response.append("\n" + entry.getKey() + ": " + entry.getValue());
            }
            DiscordUtil.createEmbedCommand(event.getChannel(), "War Status", response.toString());
        }

        if (flags.contains('l')) {
            Set<Integer> wars = cost.getWarIds();
        }

        if (Roles.ECON.has(author, guild)) {
            if (arg0.contains("/war=")) {
                arg0 = arg0.split("war=")[1];
                int warId = Integer.parseInt(arg0);
                DBWar warUrl = Locutus.imp().getWarDb().getWar(warId);
                reimburse(cost, warUrl, event);
            }
        }
        return result.toString();
    }

    private void reimburse(AttackCost cost, DBWar warUrl, MessageReceivedEvent event) {
        Guild guild = event.isFromGuild() ? event.getGuild() : null;
        if (guild == null) return;
        if (warUrl == null) {
            return;
        }

        GuildDB db = Locutus.imp().getGuildDB(guild);
        Integer aaId = db.getOrNull(GuildDB.Key.ALLIANCE_ID);
        if (aaId == null) {
            return;
        }

        DBNation nation = null;
        if (warUrl.attacker_aa == aaId) nation = Locutus.imp().getNationDB().getNation(warUrl.attacker_id);
        else if (warUrl.defender_aa == aaId) nation = Locutus.imp().getNationDB().getNation(warUrl.defender_id);
        else {
            return;
        }
        boolean primary = warUrl.isAttacker(nation);

        Map<ResourceType, Double> total = cost.getTotal(primary);
        if (total.isEmpty()) {
            return;
        }

        CounterStat counterStats = Locutus.imp().getWarDb().getCounterStat(warUrl);
        if (counterStats == null || !counterStats.isActive) return;

        String offDefStr = primary ? "offensive" : "defensive";
        String type = offDefStr + " counter";

        switch (counterStats.type) {
            case UNCONTESTED:
                type = "Uncontested " + offDefStr + " war";
                break;
            case GETS_COUNTERED:
                if (primary) {
                    return;
                }
                break;
            case IS_COUNTER:
                if (!primary) {
                    return;
                }
                break;
            case ESCALATION:
                type = "Contested " + offDefStr + " war";
                break;
        }

        GuildMessageChannel channel = event.getGuildChannel();
        String totalStr = PnwUtil.resourcesToString(total);

        String note = "#counter=" + warUrl.warId;
        List<Transaction2> transactions = db.getTransactionsByNote(note, false);
        if (!transactions.isEmpty()) {
            DiscordUtil.createEmbedCommand(channel, "Reimbursed", "Already reimbursed:\n" + totalStr);
            return;
        }

        String title = "Reimburse: ~$" + MathMan.format(PnwUtil.convertedTotal(total));
        String body = "Type: " + type + "\n" + "Amt: " + totalStr;

        String cmd = "!addbalance " + nation.getNationUrl() + " " + totalStr + " \"" + note + "\"";

        String infoEmoji = "\u2139";
        String infoCmd = "!warinfo " + warUrl.toUrl();

        DiscordUtil.createEmbedCommand(channel, title, body, "\u2705", cmd, infoEmoji, infoCmd);
    }
}