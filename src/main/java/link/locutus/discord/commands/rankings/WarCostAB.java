package link.locutus.discord.commands.rankings;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.binding.BindingHelper;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.*;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.*;
import java.util.stream.Collectors;

public class WarCostAB extends Command {
    public WarCostAB() {
        super("warcost", "WarCostRankingAB", CommandCategory.GAME_INFO_AND_TOOLS, CommandCategory.MILCOM);
    }

    public static void reimburse(AttackCost cost, DBWar warUrl, Guild guild, IMessageIO io) {
        if (warUrl == null) {
            return;
        }

        GuildDB db = Locutus.imp().getGuildDB(guild);
        Integer aaId = db.getOrNull(GuildDB.Key.ALLIANCE_ID);
        if (aaId == null) {
            return;
        }

        DBNation nation;
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
            case UNCONTESTED -> type = "Uncontested " + offDefStr + " war";
            case GETS_COUNTERED -> {
                if (primary) {
                    return;
                }
            }
            case IS_COUNTER -> {
                if (!primary) {
                    return;
                }
            }
            case ESCALATION -> type = "Contested " + offDefStr + " war";
        }

        String totalStr = PnwUtil.resourcesToString(total);

        String note = "#counter=" + warUrl.warId;
        List<Transaction2> transactions = db.getTransactionsByNote(note, false);
        if (!transactions.isEmpty()) {
            io.send("Already reimbursed:\n" + totalStr + " to " + warUrl.toUrl());
            return;
        }

        String title = "Reimburse: ~$" + MathMan.format(PnwUtil.convertedTotal(total));
        String body = "Type: " + type + "\n" + "Amt: " + totalStr;

        String reimburseEmoji = "Reimburse";
        String cmd = Settings.commandPrefix(true) + "addbalance " + nation.getNationUrl() + " " + totalStr + " \"" + note + "\"";

        String infoEmoji = "War Info";
        String infoCmd = Settings.commandPrefix(true) + "warinfo " + warUrl.toUrl();

        io.create()
                .embed(title, body)
                .commandButton(cmd, reimburseEmoji)
                .commandButton(infoCmd, infoEmoji)
                .send();
    }

    @Override
    public String help() {
        return "`" + super.help() + " <alliance|coalition> <alliance|coalition> <days> [days-end]` OR `" + super.help() + " <war-url>`";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String desc() {
        return """
                Get the war cost between two entities.
                Add -u to exclude unit cost
                Add -i to exclude infra cost
                Add -c to exclude consumption
                Add -l to exclude loot
                Add -w to list the wars (txt file)
                Add -t to list the war types
                Add `-s` to list war status
                Add e.g `attack_type:GROUND,VICTORY` to filter by attack type
                Add `success:0` to filter by e.g. `utter failure`""";
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        String attackTypeStr = DiscordUtil.parseArg(args, "attack_type");
        String attackSuccesStr = DiscordUtil.parseArg(args, "success");
        if (args.isEmpty() || args.size() > 4 || (args.size() >= 3 && args.get(0).equalsIgnoreCase(args.get(1)))) {
            return usage(event);
        }

        String arg0 = args.get(0);

        WarAttackParser parser = new WarAttackParser(guild, args, flags);
        if (attackTypeStr != null) {
            Set<AttackType> options = new HashSet<>(BindingHelper.emumList(AttackType.class, attackTypeStr.toUpperCase(Locale.ROOT)));
            parser.getAttacks().removeIf(f -> !options.contains(f.attack_type));
        }
        if (attackSuccesStr != null) {
            Set<Integer> options = Arrays.stream(attackSuccesStr.split(",")).mapToInt(Integer::parseInt).boxed().collect(Collectors.toSet());
            parser.getAttacks().removeIf(f -> !options.contains(f.success));
        }

        AttackCost cost = parser.toWarCost();

        String result = cost.toString(!flags.contains('u'), !flags.contains('i'), !flags.contains('c'), !flags.contains('l'));
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
                response.append("\n").append(entry.getKey()).append(": ").append(entry.getValue());
            }
            DiscordUtil.createEmbedCommand(event.getChannel(), "War Types", response.toString());
        }
        if (flags.contains('s')) {
            List<DBWar> wars = Locutus.imp().getWarDb().getWarsById(cost.getWarIds());
            Map<CoalitionWarStatus, Integer> byStatus = new HashMap<>();
            for (DBWar war : wars) {
                CoalitionWarStatus status = switch (war.status) {
                    case ATTACKER_OFFERED_PEACE, DEFENDER_OFFERED_PEACE, ACTIVE -> CoalitionWarStatus.ACTIVE;
                    case PEACE -> CoalitionWarStatus.PEACE;
                    case EXPIRED -> CoalitionWarStatus.EXPIRED;
                    default -> null;
                };
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
                response.append("\n").append(entry.getKey()).append(": ").append(entry.getValue());
            }
            DiscordUtil.createEmbedCommand(event.getChannel(), "War Status", response.toString());
        }

        if (Roles.ECON.has(author, guild)) {
            if (arg0.contains("/war=")) {
                arg0 = arg0.split("war=")[1];
                int warId = Integer.parseInt(arg0);
                DBWar warUrl = Locutus.imp().getWarDb().getWar(warId);
                reimburse(cost, warUrl, event.getGuild(), new DiscordChannelIO(event));
            }
        }
        return result;
    }
}