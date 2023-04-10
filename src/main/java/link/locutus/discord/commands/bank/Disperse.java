package link.locutus.discord.commands.bank;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
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
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class Disperse extends Command {
    public Disperse(TransferCommand withdrawCommand) {
        super("disburse", "disperse", CommandCategory.ECON);
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
        return "Disburse funds.\n" +
                "add `-d` to assume daily cash login bonus\n" +
                "add `-c` to skip sending cash\n" +
                "add `-m` to convert to money\n" +
                "Add `-b` to bypass checks\n" +
                "Add e.g. `nation:blah` to specify a nation account\n" +
                "Add e.g. `alliance:blah` to specify an alliance account\n" +
                "Add e.g. `offshore:blah` to specify an offshore account\n" +
                "Add e.g. `tax_id:blah` to specify a tax bracket\n" +
                "Use `-t` to specify receiver's tax account";
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        GuildDB guildDb = Locutus.imp().getGuildDB(guild);
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

        if (args.size() != 3) return usage(event);
        if (me == null) {
            return "Please use " + CM.register.cmd.toSlashMention() + "";
        }


        double daysDefault = Integer.parseInt(args.get(1));
        boolean ignoreInactives = !flags.contains('i');

        DepositType.DepositTypeInfo type = PWBindings.DepositTypeInfo(args.get(2));

        String arg = args.get(0);
        List<DBNation> nations = new ArrayList<>(DiscordUtil.parseNations(event.getGuild(), arg));
        if (nations.size() != 1 || !flags.contains('b')) {
            nations.removeIf(n -> n.getPosition() <= 1);
            nations.removeIf(n -> n.getVm_turns() != 0);
            nations.removeIf(n -> n.getActive_m() > 2880);
            nations.removeIf(n -> n.isGray() && n.getOff() == 0);
            nations.removeIf(n -> n.isBeige() && n.getCities() <= 4);
        }
        if (nations.isEmpty()) {
            return "No nations found (add `-f` to force send)";
        }
        return BankCommands.disburse(
                author,
                guildDb,
                new DiscordChannelIO(event.getChannel()),
                me,
                new SimpleNationList(nations),
                daysDefault,
                type,
                flags.contains('d'),
                flags.contains('c'),
                nationAccount,
                allianceAccount,
                offshoreAccount,
                taxAccount,
                flags.contains('t'),
                null,
                flags.contains('m'),
                flags.contains('b'),
                flags.contains('f'));
    }
}