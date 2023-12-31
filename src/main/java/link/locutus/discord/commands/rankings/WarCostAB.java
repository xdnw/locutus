package link.locutus.discord.commands.rankings;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.binding.BindingHelper;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.CoalitionWarStatus;
import link.locutus.discord.db.entities.CounterStat;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.entities.Transaction2;
import link.locutus.discord.db.entities.WarAttackParser;
import link.locutus.discord.db.entities.AttackCost;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.WarType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.*;
import java.util.stream.Collectors;

public class WarCostAB extends Command {
    public WarCostAB() {
        super("warcost", "WarCostRankingAB", CommandCategory.GAME_INFO_AND_TOOLS, CommandCategory.MILCOM);
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
        return "Get the war cost between two entities.\n" +
                "Add -u to exclude unit cost\n" +
                "Add -i to exclude infra cost\n" +
                "Add -c to exclude consumption\n" +
                "Add -l to exclude loot\n" +
                "Add -b to exclude buildings\n" +
                "Add -w to list the wars (txt file)\n" +
                "Add -t to list the war types\n" +
                "Add `-s` to list war status\n" +
                "Add e.g `attack_type:GROUND,VICTORY` to filter by attack type\n" +
                "Add `war_type:RAID` to filter by war type\n" +
                "Add `success:0` to filter by e.g. `utter failure`";
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        String attackTypeStr = DiscordUtil.parseArg(args, "attack_type");
        String attackSuccesStr = DiscordUtil.parseArg(args, "success");
        String warTypeStr = DiscordUtil.parseArg(args, "war_type");
        if (args.isEmpty() || args.size() > 4 || (args.size() >= 3 && args.get(0).equalsIgnoreCase(args.get(1)))) {
            return usage(args.size(), 1, 4, channel);
        }

        String arg0 = args.get(0);

        WarAttackParser parser = new WarAttackParser(guild, author, me, args, flags);
        if (attackTypeStr != null) {
            Set<AttackType> options = new HashSet<>(BindingHelper.emumList(AttackType.class, attackTypeStr.toUpperCase(Locale.ROOT)));
            parser.getAttacks().removeIf(f -> !options.contains(f.getAttack_type()));
        }
        if (attackSuccesStr != null) {
            Set<Integer> options = Arrays.stream(attackSuccesStr.split(",")).mapToInt(Integer::parseInt).boxed().collect(Collectors.toSet());
            parser.getAttacks().removeIf(f -> !options.contains(f.getSuccess().ordinal()));
        }
        if (warTypeStr != null) {
            Set<WarType> options = new HashSet<>(BindingHelper.emumList(WarType.class, warTypeStr.toUpperCase(Locale.ROOT)));
            parser.getAttacks().removeIf(f -> {
                DBWar war = f.getWar();
                return war == null || !options.contains(war.getWarType());
            });
        }

        AttackCost cost = parser.toWarCost(!flags.contains('b'), true, flags.contains('s'), flags.contains('w') || flags.contains('t'), false);

        StringBuilder result = new StringBuilder(cost.toString(!flags.contains('u'), !flags.contains('i'), !flags.contains('c'), !flags.contains('l'), !flags.contains('b')));
        IMessageBuilder msg = channel.create();
        if (flags.contains('w')) {
            msg.file(cost.getNumWars() + " wars", "- " + StringMan.join(cost.getWarIds(), "\n- "));
        }
        if (flags.contains('t')) {
            Set<DBWar> wars = Locutus.imp().getWarDb().getWarsById(cost.getWarIds());
            Map<WarType, Integer> byType = new HashMap<>();
            for (DBWar war : wars) {
                byType.put(war.getWarType(), byType.getOrDefault(war.getWarType(), 0) + 1);
            }
            StringBuilder response = new StringBuilder();
            for (Map.Entry<WarType, Integer> entry : byType.entrySet()) {
                response.append("\n" + entry.getKey() + ": " + entry.getValue());
            }
            msg.embed("War Types", response.toString());
        }
        if (flags.contains('s')) {
            Set<DBWar> wars = Locutus.imp().getWarDb().getWarsById(cost.getWarIds());
            Map<CoalitionWarStatus, Integer> byStatus = new HashMap<>();
            for (DBWar war : wars) {
                CoalitionWarStatus status = null;
                switch (war.getStatus()) {
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
            msg.embed("War Status", response.toString());
        }

        if (Roles.ECON.has(author, guild)) {
            if (arg0.contains("/war=")) {
                arg0 = arg0.split("war=")[1];
                int warId = Integer.parseInt(arg0);
                DBWar warUrl = Locutus.imp().getWarDb().getWar(warId);
                reimburse(cost, warUrl, guild, channel);
            }
        }
        msg.append(result.toString());
        msg.send();
        return null;
    }

    public static void reimburse(AttackCost cost, DBWar warUrl, Guild guild, IMessageIO io) {
        if (warUrl == null) {
            return;
        }

        GuildDB db = Locutus.imp().getGuildDB(guild);
        Set<Integer> aaIds = db.getAllianceIds();
        if (aaIds.isEmpty()) {
            return;
        }

        DBNation nation = null;
        if (aaIds.contains(warUrl.getAttacker_aa())) nation = Locutus.imp().getNationDB().getNation(warUrl.getAttacker_id());
        else if (aaIds.contains(warUrl.getDefender_aa())) nation = Locutus.imp().getNationDB().getNation(warUrl.getDefender_id());
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

        String totalStr = PnwUtil.resourcesToString(total);

        String note = "#counter=" + warUrl.warId;
        List<Transaction2> transactions = db.getTransactionsByNote(note, false);
        if (!transactions.isEmpty()) {
            io.send("Already reimbursed:\n" + totalStr +" to " + warUrl.toUrl());
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
}