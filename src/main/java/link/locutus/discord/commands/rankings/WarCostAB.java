package link.locutus.discord.commands.rankings;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.IAttack;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.SuccessType;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.binding.BindingHelper;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.annotation.Switch;
import link.locutus.discord.commands.manager.v2.binding.bindings.Placeholders;
import link.locutus.discord.commands.manager.v2.binding.bindings.PrimitiveBindings;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.PWBindings;
import link.locutus.discord.commands.manager.v2.impl.pw.commands.StatCommands;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.CoalitionWarStatus;
import link.locutus.discord.db.entities.CounterStat;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.entities.Transaction2;
import link.locutus.discord.db.entities.WarAttackParser;
import link.locutus.discord.db.entities.AttackCost;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.WarStatus;
import link.locutus.discord.pnw.NationOrAlliance;
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
                "Add `-u` to exclude unit cost\n" +
                "Add `-i` to exclude infra cost\n" +
                "Add `-c` to exclude consumption\n" +
                "Add `-l` to exclude loot\n" +
                "Add `-b` to exclude buildings\n" +
                "Add `-w` to list the wars (txt file)\n" +
                "Add `-t` to list the war types\n" +
                "Add `-s` to list war status\n" +
                "Add `-o` to only include wars declared by coalition1\n" +
                "Add `-d` to only include wars declared by coalition2\n" +
                "Add e.g `attack_type:GROUND,VICTORY` to filter by attack type\n" +
                "Add `war_type:RAID` to filter by war type\n" +
                "Add `status:EXPIRED` to filter by war status\n" +
                "Add `success:0` to filter by e.g. `utter failure`";
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        String attackTypeStr = DiscordUtil.parseArg(args, "attack_type");
        String attackSuccesStr = DiscordUtil.parseArg(args, "success");
        String warTypeStr = DiscordUtil.parseArg(args, "war_type");
        String warStatusStr = DiscordUtil.parseArg(args, "status");
        if (args.size() != 1 && args.size() != 3 && args.size() != 4 && (args.size() < 2 || !args.get(0).equalsIgnoreCase(args.get(1)))) {
            return usage(args.size(), 1, 4, channel);
        }
        String arg0 = args.get(0);
        if (args.size() == 1 && arg0.contains("/war=")) {
            // throw error for invalid flags (w, t, s)
            if (flags.contains('w') || flags.contains('t') || flags.contains('s')) {
                return "Cannot use flags: `-w`, `-t`, `-s` with a war url. See also " + CM.war.info.cmd.toSlashMention();
            }
            DBWar war = PWBindings.war(arg0);
            return StatCommands.warCost(author, guild, channel,
                    war,
                    flags.contains('u'),
                    flags.contains('i'),
                    flags.contains('c'),
                    flags.contains('l'),
                    flags.contains('b'));
        }

        Set<NationOrAlliance> col1 = args.get(0).equalsIgnoreCase("*") ? null : PWBindings.nationOrAlliance(null, guild, args.get(0), author, me);
        Set<NationOrAlliance> col2 = args.get(1).equalsIgnoreCase("*") ? null : PWBindings.nationOrAlliance(null, guild, args.get(1), author, me);
        long start = PrimitiveBindings.timestamp(args.get(2));
        long end = args.size() == 4 ? PrimitiveBindings.timestamp(args.get(3)) : null;

        Placeholders<IAttack> phAttacks = Locutus.imp().getCommandManager().getV2().getPlaceholders().get(IAttack.class);
        Set<AttackType> attackTypes = attackTypeStr == null ? null : PWBindings.AttackTypes(phAttacks.createLocals(guild, author, me), attackTypeStr);
        Set<SuccessType> successTypes = attackSuccesStr == null ? null : PWBindings.SuccessTypes(attackSuccesStr);
        Set<WarType> warTypes = warTypeStr == null ? null : PWBindings.WarTypes(warTypeStr);
        Set<WarStatus> warStatuses = warStatusStr == null ? null : PWBindings.WarStatuses(warStatusStr);

        return StatCommands.warsCost(
                channel, null,
                col1,
                col2,
                start,
                end,
                flags.contains('u'),
                flags.contains('i'),
                flags.contains('c'),
                flags.contains('l'),
                flags.contains('b'),
                flags.contains('w'),
                flags.contains('t'),
                warTypes,
                warStatuses,
                attackTypes,
                successTypes,
                flags.contains('o'),
                flags.contains('d'),
                false,
                false
        );
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