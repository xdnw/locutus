package link.locutus.discord.commands.bank;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.apiv1.enums.EscrowMode;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.binding.bindings.PrimitiveBindings;
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
import link.locutus.discord.pnw.NationOrAlliance;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.apiv1.enums.ResourceType;
import net.dv8tion.jda.api.entities.*;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class TransferCommand extends Command {
    boolean disabled = false;
    public TransferCommand() {
        super("transfer", "withdraw", CommandCategory.ECON);
    }

    @Override
    public List<CommandRef> getSlashReference() {
        return List.of(CM.transfer.resources.cmd);
    }
    @Override
    public String help() {
        return Settings.commandPrefix(true) + "transfer <alliance|nation> <resource> <amount> <note>";
    }

    @Override
    public String desc() {
        return """
                withdraw from the alliance bank
                Use `-f` to bypass all checks
                Use `expire:60d` to have expiry
                Use `decay:60d` to have linear debt decay
                Use `-c` to convert cash
                Use `nation:Borg` to specify nation account
                Use `alliance:Rose` to specify alliance account
                Use `offshore:AllianceName` to specify offshore
                Use `tax_id:1234` to specify tax account
                Use `-t` to specify receiver's tax account
                Use `-o` to subtract their existing funds from the transfer amount
                Add `escrow=WHEN_BLOCKADED` or `escrow=ALWAYS` to escrow the transfer
                """;
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        if (Roles.ECON.hasOnRoot(user)) return true;
        if (!Roles.MEMBER.has(user, server)) return false;
        GuildDB db = Locutus.imp().getGuildDB(server);
        return db.getOffshore() != null;
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        GuildDB guildDb = Locutus.imp().getGuildDB(guild);
        String decayStr = DiscordUtil.parseArg(args, "decay");
        if (decayStr == null) decayStr = DiscordUtil.parseArg(args, "#decay");
        Long decay = decayStr == null ? null : PrimitiveBindings.timediff(decayStr);
        String expireStr = DiscordUtil.parseArg(args, "expire");
        if (expireStr == null) expireStr = DiscordUtil.parseArg(args, "#expire");
        Long expire = expireStr == null ? null : PrimitiveBindings.timediff(expireStr);

        String escrowModeStr = DiscordUtil.parseArg(args, "escrow");
        EscrowMode escrowMode = escrowModeStr != null ? PWBindings.EscrowMode(escrowModeStr) : null;

        DBNation nationAccount = null;
        DBAlliance allianceAccount = null;
        DBAlliance offshoreAccount = null;
        TaxBracket taxAccount = null;

        String nationAccountStr = DiscordUtil.parseArg(args, "nation");
        if (nationAccountStr != null) {
            nationAccount = PWBindings.nation(author, guild, nationAccountStr);
        }

        String allianceAccountStr = DiscordUtil.parseArg(args, "alliance");
        if (allianceAccountStr != null) {
            allianceAccount = PWBindings.alliance(allianceAccountStr);
        }

        String offshoreAccountStr = DiscordUtil.parseArg(args, "offshore");
        if (offshoreAccountStr != null) {
            offshoreAccount = PWBindings.alliance(offshoreAccountStr);
        }

        String taxIdStr = DiscordUtil.parseArg(args, "tax_id");
        if (taxIdStr == null) taxIdStr = DiscordUtil.parseArg(args, "bracket");
        if (taxIdStr != null) {
            taxAccount = PWBindings.bracket(guildDb, "tax_id=" + taxIdStr);
        }

        if (args.size() < 3) return usage();
        NationOrAlliance receiver = PWBindings.nationOrAlliance(args.get(0), guild);
        String rssStr = args.size() > 3 ? args.get(1) + " " + args.get(2) : args.get(1);
        Map<ResourceType, Double> transfer = ResourceType.parseResources(rssStr);
        String noteStr = args.get(args.size() - 1);
        DepositType.DepositTypeInfo depositType = PWBindings.DepositTypeInfo(noteStr);

        if (flags.contains('t')) {
            if (taxAccount != null) return "You can't specify both `tax_id` and `-t`";
            if (!receiver.isNation()) return "You can only specify `-t` for a nation";
            taxAccount = receiver.asNation().getTaxBracket();
        }

        boolean onlyMissingFunds = flags.contains('o');
        boolean convertCash = flags.contains('c');
        boolean bypassChecks = flags.contains('f');

        UUID token = null;
        // String receiver, String transfer, String depositType, String nationAccount, String senderAlliance, String allianceAccount, String onlyMissingFunds, String expire, String token, String convertCash, String bypassChecks
        JSONObject command = CM.transfer.resources.cmd.receiver(
                receiver.getUrl()).transfer(
                ResourceType.toString(transfer)).depositType(
                depositType.toString()).nationAccount(
                nationAccount != null ? nationAccount.getUrl() : null).senderAlliance(
                allianceAccount != null ? allianceAccount.getUrl() : null).allianceAccount(
                offshoreAccount != null ? offshoreAccount.getUrl() : null).taxAccount(
                taxAccount != null ? taxAccount.getQualifiedId() : null).onlyMissingFunds(
                String.valueOf(onlyMissingFunds)).expire(
                expire == null ? null : TimeUtil.secToTime(TimeUnit.MILLISECONDS, expire)).decay(
                decay == null ? null : TimeUtil.secToTime(TimeUnit.MILLISECONDS, decay)).token(
                token == null ? null : token.toString()).convertCash(
                String.valueOf(convertCash)).escrow_mode(
                escrowMode == null ? null : escrowMode.name()).bypassChecks(
                String.valueOf(bypassChecks)
        ).toJson();

        return BankCommands.transfer(channel, command, author, me, guildDb, receiver, transfer, depositType, nationAccount, allianceAccount, offshoreAccount, taxAccount, false, onlyMissingFunds, expire, decay, convertCash, escrowMode, bypassChecks, false, false, null);
    }
}
