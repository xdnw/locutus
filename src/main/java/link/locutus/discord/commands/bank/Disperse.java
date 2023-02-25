package link.locutus.discord.commands.bank;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.CommandBehavior;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.PWBindings;
import link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.AddBalanceBuilder;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.pnw.AllianceList;
import link.locutus.discord.pnw.NationList;
import link.locutus.discord.pnw.SimpleNationList;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.JsonUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.offshore.OffshoreInstance;
import link.locutus.discord.util.sheet.SpreadSheet;
import link.locutus.discord.util.sheet.templates.TransferSheet;
import link.locutus.discord.apiv1.enums.ResourceType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public class Disperse extends Command {
    public Disperse(BankWith withdrawCommand) {
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
                "Add `-f` to force it through";
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() != 3) return usage(event);
        if (me == null) {
            return "Please use " + CM.register.cmd.toSlashMention() + "";
        }


        double daysDefault = Integer.parseInt(args.get(1));
        boolean force = flags.contains('f');
        boolean ignoreInactives = !flags.contains('i');

        DepositType type = PWBindings.DepositType(args.get(2));

        String arg = args.get(0);
        List<DBNation> nations = new ArrayList<>(DiscordUtil.parseNations(event.getGuild(), arg));
        if (nations.size() != 1 || !flags.contains('f')) {
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
                Locutus.imp().getGuildDB(guild),
                new DiscordChannelIO(event.getChannel()),
                me,
                new SimpleNationList(nations),
                daysDefault,
                type,
                flags.contains('d'),
                flags.contains('c'),
                null,
                null,
                null,
                null,
                false,
                force);
    }

    public static String disperse(GuildDB db, Map<DBNation, Map<ResourceType, Double>> fundsToSendNations, Map<DBAlliance, Map<ResourceType, Double>> fundsToSendAAs, DepositType type, IMessageIO channel, String title) throws GeneralSecurityException, IOException {
        if (fundsToSendNations.isEmpty() && fundsToSendAAs.isEmpty()) {
            return "No funds need to be sent";
        }
        String note = "#" + type.name();
        Map<ResourceType, Double> total = new LinkedHashMap<>();
        List<String> postScript = new ArrayList<>();
        for (Map.Entry<DBNation, Map<ResourceType, Double>> entry : fundsToSendNations.entrySet()) {
            total = PnwUtil.add(total, entry.getValue());
            postScript.add(PnwUtil.getPostScript(entry.getKey().getNation(), true, entry.getValue(), note));
        }
        for (Map.Entry<DBAlliance, Map<ResourceType, Double>> entry : fundsToSendAAs.entrySet()) {
            postScript.add(PnwUtil.getPostScript(entry.getKey().getName(), false, entry.getValue(), note));
        }

        if (fundsToSendNations.size() == 1 && fundsToSendAAs.isEmpty()) {
            Map.Entry<DBNation, Map<ResourceType, Double>> entry = fundsToSendNations.entrySet().iterator().next();
            DBNation nation = entry.getKey();
            title += " to " + nation.getNation() + " | " + nation.getAllianceName();

            Map<ResourceType, Double> transfer = entry.getValue();
            String post = JsonUtil.toPrettyFormat(PnwUtil.getPostScript(nation.getNation(), true, transfer, note));

            UUID uuid = UUID.randomUUID();
            BankWith.authorized.add(uuid);
            String token = "-g:" + uuid;

            String command = "_" + Settings.commandPrefix(true) + "transfer \"" + note + "\" " + nation.getNationUrl() + " " + StringMan.getString(total) + " " + token;

            StringBuilder body = new StringBuilder("```" + post + "```\n");
            body.append(nation.getNationUrlMarkup(true) + "\n");
            body.append("Worth: $" + MathMan.format(PnwUtil.convertedTotal(total)));
            channel.create().embed(title, body.toString())
                            .commandButton(command, "Confirm")
                            .cancelButton()
                            .send();
            return null;
        } else {

            String arr = JsonUtil.toPrettyFormat("[" + StringMan.join(postScript, ",") + "]");


            TransferSheet sheet = new TransferSheet(db).write(fundsToSendNations, fundsToSendAAs).build();

            String emoji = "Confirm";
            String cmd = Settings.commandPrefix(false) + "transfer Bulk " + sheet.getSheet().getURL() + " " + type;

            StringBuilder response = new StringBuilder();
            response.append("Total: $" + MathMan.format(PnwUtil.convertedTotal(total)) + ": `" + PnwUtil.resourcesToString(total)).append("`\n");
            response.append("Info: Use the extension to disburse from offshore or Press `" + emoji + "` to run:\n" +
                    "`" + Settings.commandPrefix(false) + "transfer Bulk <sheet> " + type + "`");


            IMessageBuilder msg = channel.create();
            sheet.getSheet().attach(msg, response, false, 0);
            msg.file("transfer.json", arr);
            msg.embed("Disperse", response.toString());
            msg.commandButton(cmd, emoji);

            msg.send();
            return null;
        }
    }
}
