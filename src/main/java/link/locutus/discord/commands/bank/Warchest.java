package link.locutus.discord.commands.bank;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.DepositTypeInfo;
import link.locutus.discord.apiv1.enums.EscrowMode;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.PWBindings;
import link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.TaxBracket;
import link.locutus.discord.pnw.SimpleNationList;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.apiv1.enums.ResourceType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Warchest extends Command {
    public Warchest() {
        super(CommandCategory.ECON, CommandCategory.MILCOM);
    }
    @Override
    public boolean checkPermission(Guild server, User user) {
        return Roles.MEMBER.has(user, server) && server != null;
    }

    @Override
    public String help() {
        return Settings.commandPrefix(true) + "warchest <*|nations|tax_url> <resources> <note>";
    }


    @Override
    public List<CommandRef> getSlashReference() {
        return List.of(CM.transfer.warchest.cmd);
    }

    @Override
    public String desc() {
        return """
                Determine how much to send to each member to meet their warchest requirements (per city)
                Add `-s` to skip checking stockpile
                add `-m` to convert to money
                Add `-b` to bypass checks
                Add e.g. `nation:blah` to specify a nation account
                Add e.g. `alliance:blah` to specify an alliance account
                Add e.g. `offshore:blah` to specify an offshore account
                Add e.g. `tax_id:blah` to specify a tax bracket
                add `-t` to use receivers tax bracket account""";
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        GuildDB guildDb = Locutus.imp().getGuildDB(guild);
        DBNation nationAccount = null;
        DBAlliance ingame_bank = null;
        DBAlliance offshore_account = null;
        TaxBracket tax_account = null;

        String escrowModeStr = DiscordUtil.parseArg(args, "escrow");
        EscrowMode escrowMode = escrowModeStr != null ? PWBindings.EscrowMode(escrowModeStr) : null;

        String nationAccountStr = DiscordUtil.parseArg(args, "nation");
        if (nationAccountStr != null) {
            nationAccount = PWBindings.nation(author, guild, nationAccountStr);
        }

        String ingame_bankStr = DiscordUtil.parseArg(args, "alliance");
        if (ingame_bankStr != null) {
            ingame_bank = PWBindings.alliance(ingame_bankStr);
        }

        String offshoreAccountStr = DiscordUtil.parseArg(args, "offshore");
        if (offshoreAccountStr != null) {
            offshore_account = PWBindings.alliance(offshoreAccountStr);
        }

        String taxIdStr = DiscordUtil.parseArg(args, "tax_id");
        if (taxIdStr == null) taxIdStr = DiscordUtil.parseArg(args, "bracket");
        if (taxIdStr != null) {
            tax_account = PWBindings.bracket(guildDb, "tax_id=" + taxIdStr);
        }

        if (flags.contains('t')) {
            if (tax_account != null) return "You can't specify both `tax_id` and `-t`";
        }

        if (args.size() < 3) {
            return usage("Current warchest (per city): " + ResourceType.toString(guildDb.getPerCityWarchest(me)), channel);
        }


        Map<ResourceType, Double> perCity = ResourceType.parseResources(args.get(1));
        if (perCity.isEmpty()) return "Invalid amount: `" + args.get(1) + "`";
        boolean ignoreInactives = !flags.contains('i');

        DepositTypeInfo type = PWBindings.DepositTypeInfo(args.get(2));

        String arg = args.get(0);
        List<DBNation> nations = new ArrayList<>(DiscordUtil.parseNations(guild, author, me, arg, false, false));
        if (nations.size() != 1 || !flags.contains('b')) {
            nations.removeIf(n -> n.getPosition() <= 1);
            nations.removeIf(n -> n.getVm_turns() != 0);
            nations.removeIf(n -> n.active_m() > 2880);
            nations.removeIf(n -> n.isGray() && n.getOff() == 0);
            nations.removeIf(n -> n.isBeige() && n.getCities() <= 4);
        }
        if (nations.isEmpty()) {
            return "No nations found (add `-f` to force send)";
        }
        boolean skipStockpile = flags.contains('s');

        boolean ping_when_sent = false;

        CM.transfer.warchest command = CM.transfer.warchest.cmd.nations(arg)
                .resourcesPerCity(ResourceType.toString(perCity))
                .bank_note(type.toString())
                .skipStockpile(skipStockpile ? "true" : null)
                .nation_account(nationAccount != null ? nationAccount.getQualifiedId() : null)
                .ingame_bank(ingame_bank != null ? ingame_bank.getQualifiedId() : null)
                .offshore_account(offshore_account != null ? offshore_account.getQualifiedId() : null)
                .tax_account(tax_account != null ? tax_account.getQualifiedId() : null)
                .use_receiver_tax_account(flags.contains('t') ? "true" : null)
                .expire(null)
                .decay(null)
                .deduct_as_cash(flags.contains('m') ? "true" : null)
                .escrow_mode(escrowMode != null ? escrowMode.name() : null)
                .ping_when_sent(ping_when_sent ? "true" : null)
                .bypass_checks(flags.contains('b') ? "true" : null)
                .force(flags.contains('f') ? "true" : null);

        return UnsortedCommands.warchest(
                guildDb,
                command.toJson(),
                channel,
                guild,
                author,
                me,
                new SimpleNationList(nations),
                perCity,
                type,
                skipStockpile,
                nationAccount,
                ingame_bank,
                offshore_account,
                tax_account,
                flags.contains('t'),
                null,
                null,
                flags.contains('m'),
                escrowMode,
                ping_when_sent,
                flags.contains('b'),
                flags.contains('f'));
    }
}