package link.locutus.discord.commands.bank;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands;
import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.binding.bindings.PrimitiveBindings;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.PWBindings;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.TaxBracket;
import link.locutus.discord.pnw.NationOrAlliance;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.apiv1.enums.ResourceType;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class TransferCommand extends Command {
    boolean disabled = false;
    public TransferCommand() {
        super("transfer", "withdraw", CommandCategory.ECON);
    }

    @Override
    public String help() {
        return Settings.commandPrefix(true) + "transfer <alliance|nation> <resource> <amount>";
    }

    @Override
    public String desc() {
        return "withdraw from the alliance bank\n" +
                "Use `-f` to bypass all checks\n" +
                "Use `expire:60d` to have expiry\n" +
                "Use `-c` to convert cash\n" +
                "Use `nation:Borg` to specify nation account\n" +
                "Use `alliance:Rose` to specify alliance account\n" +
                "Use `offshore:AllianceName` to specify offshore\n" +
                "Use `tax_id:1234` to specify tax account\n" +
                "Use `-t` to specify receiver's tax account" +
                "Use `-o` to subtract their existing funds from the transfer amount";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        if (Roles.ECON.hasOnRoot(user)) return true;
        if (!Roles.MEMBER.has(user, server)) return false;
        GuildDB db = Locutus.imp().getGuildDB(server);
        return db.getOffshore() != null;
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        GuildDB guildDb = Locutus.imp().getGuildDB(guild);
        String expireStr = DiscordUtil.parseArg(args, "expire");
        Long expire = expireStr == null ? null : PrimitiveBindings.Long(expireStr);

        DBNation nationAccount = null;
        DBAlliance allianceAccount = null;
        DBAlliance offshoreAccount = null;
        TaxBracket taxAccount = null;

        String nationAccountStr = DiscordUtil.parseArg(args, "nation");
        if (nationAccountStr != null) {
            nationAccount = PWBindings.nation(author, nationAccountStr);
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
        IMessageIO channel = new DiscordChannelIO(event.getChannel());
        NationOrAlliance receiver = PWBindings.nationOrAlliance(args.get(0));
        String rssStr = args.size() > 3 ? args.get(1) + " " + args.get(2) : args.get(1);
        Map<ResourceType, Double> transfer = PnwUtil.parseResources(rssStr);
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
        JSONObject command = CM.transfer.resources.cmd.create(
                receiver.getUrl(),
                PnwUtil.resourcesToString(transfer),
                depositType.toString(),
                nationAccount != null ? nationAccount.getUrl() : null,
                allianceAccount != null ? allianceAccount.getUrl() : null,
                offshoreAccount != null ? offshoreAccount.getUrl() : null,
                taxAccount != null ? taxAccount.getQualifiedName() : null,
                null,
                String.valueOf(onlyMissingFunds),
                expire == null ? null : ("timestamp:" + expire),
                token == null ? null : token.toString(),
                String.valueOf(convertCash),
                String.valueOf(bypassChecks),
                null
        ).toJson();

        return BankCommands.transfer(channel, command, author, me, guildDb, receiver, transfer, depositType, nationAccount, allianceAccount, offshoreAccount, taxAccount, false, onlyMissingFunds, expire, token, convertCash, bypassChecks, false);
    }
}
