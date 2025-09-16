package link.locutus.discord.commands.bank;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.DepositTypeInfo;
import link.locutus.discord.apiv1.enums.EscrowMode;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.PWBindings;
import link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.TaxBracket;
import link.locutus.discord.pnw.SimpleNationList;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class Disperse extends Command {
    public Disperse(TransferCommand withdrawCommand) {
        super("disburse", "disperse", CommandCategory.ECON);
    }

    @Override
    public List<CommandRef> getSlashReference() {
        return List.of(CM.transfer.raws.cmd);
    }
    @Override
    public boolean checkPermission(Guild server, User user) {
        return Roles.MEMBER.has(user, server) && (checkPermission(server, user, true) || Locutus.imp().getGuildDB(server).getOffshore() != null);
    }

    @Override
    public String help() {
        return Settings.commandPrefix(true) + "disburse <tax-bracket-link|nation> <days> <note>";
    }

    @Override
    public String desc() {
        return """
                Disburse funds.
                add `-d` to assume daily cash login bonus
                add `-c` to skip sending cash
                add `-m` to convert to money
                Add `-b` to bypass checks
                Add e.g. `nation:blah` to specify a nation account
                Add e.g. `alliance:blah` to specify an alliance account
                Add e.g. `offshore:blah` to specify an offshore account
                Add e.g. `tax_id:blah` to specify a tax bracket
                Use `-t` to specify receiver's tax account
                Add `escrow=WHEN_BLOCKADED` or `escrow=ALWAYS` to escrow the transfer
                """;
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        String escrowModeStr = DiscordUtil.parseArg(args, "escrow");
        EscrowMode escrowMode = escrowModeStr != null ? PWBindings.EscrowMode(escrowModeStr) : null;

        GuildDB guildDb = Locutus.imp().getGuildDB(guild);
        DBNation nationAccount = null;
        DBAlliance ingame_bank = null;
        DBAlliance offshoreAccount = null;
        TaxBracket tax_account = null;

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
            offshoreAccount = PWBindings.alliance(offshoreAccountStr);
        }

        String taxIdStr = DiscordUtil.parseArg(args, "tax_id");
        if (taxIdStr == null) taxIdStr = DiscordUtil.parseArg(args, "bracket");
        if (taxIdStr != null) {
            tax_account = PWBindings.bracket(guildDb, "tax_id=" + taxIdStr);
        }

        if (args.size() != 3) return usage(args.size(), 3, channel);
        if (me == null) {
            return "Please use " + CM.register.cmd.toSlashMention();
        }


        int daysDefault = Integer.parseInt(args.get(1));
        boolean ignoreInactives = !flags.contains('i');

        DepositTypeInfo type = PWBindings.DepositTypeInfo(args.get(2));

        String arg = args.get(0);
        List<DBNation> nations = new ArrayList<>(DiscordUtil.parseNations(guild, author, me, arg, false, false));
        if (nations.isEmpty()) {
            return "No nations found (add `-b` to force send)";
        }

        CM.transfer.raws command = CM.transfer.raws.cmd
                .nationList(arg)
                .days(daysDefault + "")
                .no_daily_cash(flags.contains('d') ? "true" : null)
                .no_cash(flags.contains('c') ? "true" : null)
                .bank_note(type.toString())
                .expire(null)
                .decay(null)
                .deduct_as_cash(flags.contains('m') ? "true" : null)
                .nation_account(nationAccount == null ? null : nationAccount.getQualifiedId())
                .escrow_mode(escrowMode == null ? null : escrowMode.name())
                .ingame_bank(ingame_bank == null ? null : ingame_bank.getQualifiedId())
                .offshore_account(offshoreAccount == null ? null : offshoreAccount.getQualifiedId())
                .tax_account(tax_account == null ? null : tax_account.getQualifiedId())
                .use_receiver_tax_account(flags.contains('t') ? "true" : null)
                .bypass_checks(flags.contains('b') ? "true" : null)
                .ping_when_sent(null)
                .ping_role(null)
                .force(flags.contains('f') ? "true" : null);

        return BankCommands.disburse(
                author,
                command.toJson(),
                guildDb,
                channel,
                me,
                new SimpleNationList(nations),
                daysDefault,
                flags.contains('d'),
                flags.contains('c'),
                type,
                null,
                null,
                flags.contains('m'),
                nationAccount,
                escrowMode,
                ingame_bank,
                offshoreAccount,
                tax_account,
                flags.contains('t'),
                flags.contains('b'),
                false, null,
                flags.contains('f'));
    }
}