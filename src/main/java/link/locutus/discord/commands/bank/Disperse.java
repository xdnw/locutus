package link.locutus.discord.commands.bank;

import link.locutus.discord.Locutus;
<<<<<<< HEAD
import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
=======
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
>>>>>>> pr/15
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
<<<<<<< HEAD
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
=======
import link.locutus.discord.util.*;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.sheet.SpreadSheet;
import link.locutus.discord.util.sheet.templates.TransferSheet;
import link.locutus.discord.util.task.DepositRawTask;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.function.Consumer;
>>>>>>> pr/15

public class Disperse extends Command {
    public Disperse(TransferCommand withdrawCommand) {
        super("disburse", "disperse", CommandCategory.ECON);
    }

<<<<<<< HEAD
=======
    public static String disperse(GuildDB db, Map<DBNation, Map<ResourceType, Double>> fundsToSendNations, Map<DBAlliance, Map<ResourceType, Double>> fundsToSendAAs, String note, IMessageIO channel, String title) throws GeneralSecurityException, IOException {
        if (fundsToSendNations.isEmpty() && fundsToSendAAs.isEmpty()) {
            return "No funds need to be sent.";
        }
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

            String body = "```" + post + "```\n" + nation.getNationUrlMarkup(true) + "\n" +
                    "Worth: $" + MathMan.format(PnwUtil.convertedTotal(total));
            channel.create().embed(title, body)
                    .commandButton(command, "Confirm")
                    .cancelButton()
                    .send();
        } else {

            String arr = JsonUtil.toPrettyFormat("[" + StringMan.join(postScript, ",") + "]");


            TransferSheet sheet = new TransferSheet(db).write(fundsToSendNations, fundsToSendAAs).build();

            String emoji = "Confirm.";
            String cmd = Settings.commandPrefix(false) + "transfer Bulk " + sheet.getSheet().getURL() + " " + note;

            StringBuilder response = new StringBuilder();
            response.append("Total: $").append(MathMan.format(PnwUtil.convertedTotal(total))).append(": `").append(PnwUtil.resourcesToString(total)).append("`\n");
            response.append("Info: Use the extension to disburse from offshore or Press `").append(emoji).append("` to run:\n").append("`").append(Settings.commandPrefix(false)).append("transfer Bulk <sheet> ").append(note).append("`");


            IMessageBuilder msg = channel.create();
            sheet.getSheet().attach(msg, response, false, 0);
            msg.file("transfer.json", arr);
            msg.embed("Disperse", response.toString());
            msg.commandButton(cmd, emoji);

            msg.send();
        }
        return null;
    }

>>>>>>> pr/15
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
<<<<<<< HEAD
        return "Disburse funds.\n" +
                "add `-d` to assume daily cash login bonus\n" +
                "add `-c` to skip sending cash\n" +
                "add `-m` to convert to money\n" +
                "Add `-b` to bypass checks\n" +
                "Add e.g. `nation:blah` to specify a nation account\n" +
                "Add e.g. `alliance:blah` to specify an alliance account\n" +
                "Add e.g. `offshore:blah` to specify an offshore account\n" +
                "Add e.g. `tax_id:blah` to specify a tax bracket";
=======
        return """
                Disburse funds.
                add `-d` to assume daily cash login bonus.
                add `-c` to skip sending cash.
                Add `-f` to force it through.""";
>>>>>>> pr/15
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
<<<<<<< HEAD
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
=======
        if (args.isEmpty()) return usage(event);
>>>>>>> pr/15
        if (me == null) {
            return "Please use " + CM.register.cmd.toSlashMention() + "";
        }

<<<<<<< HEAD

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
=======
        boolean disperse = false;
        double daysDefault = 1;
        boolean force = flags.contains('f');
        boolean ignoreInactives = !flags.contains('i');
        String note = null;
        if (args.size() >= 3) note = args.get(2);
        if (note == null) return "Please provide a note: `" + help() + "` e.g. `#ignore` or `#tax`";

        Iterator<String> iter = args.iterator();
        while (iter.hasNext()) {
            String arg = iter.next();
            if (arg.equalsIgnoreCase("-a")) {
                iter.remove();
            } else if (arg.equalsIgnoreCase("true")) {
                disperse = true;
                iter.remove();
            } else if (arg.startsWith("#") && !arg.contains("=") && !arg.contains("<") && !arg.contains(">")) {
                note = arg;
                iter.remove();
            }
        }

        if (args.size() > 3) return usage(event);

        Collection<String> allowedLabels = Arrays.asList("#grant", "#deposit", "#trade", "#ignore", "#tax", "#warchest", "#account");
        if (!allowedLabels.contains(note.split("=")[0]))
            return "Please use one of the following labels: " + StringMan.getString(allowedLabels);

        GuildDB db = Locutus.imp().getGuildDB(guild);
        Integer aaId = db.getOrNull(GuildDB.Key.ALLIANCE_ID);
        note += "=" + Objects.requireNonNullElseGet(aaId, guild::getIdLong);

        Map<DBNation, Map<ResourceType, Double>> fundsToSendNations;
        Map<DBAlliance, Map<ResourceType, Double>> fundsToSendAAs = new LinkedHashMap<>();

        String arg = args.get(0);
        if (arg.startsWith("https://docs.google.com/spreadsheets/") || arg.startsWith("sheet:")) {

            SpreadSheet sheet = SpreadSheet.create(arg);
            AddBalanceBuilder task = new AddBalanceBuilder(db);
            Map<String, Boolean> result = sheet.parseTransfers(task, false, note);
            fundsToSendNations = task.getTotalForNations();
            fundsToSendAAs = task.getTotalForAAs();
            if (!task.getTotalForGuilds().isEmpty()) return "Cannot disperse to guilds.";

            List<String> invalid = new ArrayList<>();
            for (Map.Entry<String, Boolean> entry : result.entrySet()) {
                if (!entry.getValue()) {
                    invalid.add(entry.getKey());
                }
            }
            if (!invalid.isEmpty() && !force)
                return "Invalid nations/alliance:\n - " + StringMan.join(invalid, "\n - ");
        } else {
            Message message;
            if (!disperse) {
                message = RateLimitUtil.complete(event.getChannel().sendMessage("The following is a preview, Each disbursement must be authorized by a banker via."));
            } else {
                message = RateLimitUtil.complete(event.getChannel().sendMessage("Fetching city information:"));
            }
            int allianceId;
            List<DBNation> nations = new ArrayList<>(DiscordUtil.parseNations(event.getGuild(), arg));
            if (nations.size() != 1 || !flags.contains('f')) {
                nations.removeIf(n -> n.getPosition() <= 1);
                nations.removeIf(n -> n.getVm_turns() != 0);
                nations.removeIf(n -> n.getActive_m() > 2880);
                nations.removeIf(n -> n.isGray() && n.getOff() == 0);
                nations.removeIf(n -> n.isBeige() && n.getCities() <= 4);
            }
            if (nations.isEmpty()) {
                return "No nations found, add `-f` to force send.";
            }
            allianceId = nations.get(0).getAlliance_id();

            Consumer<String> updateTask = s -> RateLimitUtil.queue(event.getChannel().editMessageById(message.getIdLong(), s));
            Consumer<String> errors = s -> RateLimitUtil.queue(event.getChannel().sendMessage(s));
            if (nations.isEmpty()) return "No nations in this tax bracket.";
            daysDefault = Integer.parseInt(args.get(args.size() - 1));
            fundsToSendNations = new DepositRawTask(nations, allianceId, updateTask, daysDefault, true, ignoreInactives, errors).setForce(force).call();
            if (nations.isEmpty()) {
                return "No nations found.";
            }
        }

        for (Map.Entry<DBNation, Map<ResourceType, Double>> entry : fundsToSendNations.entrySet()) {
            Map<ResourceType, Double> transfer = entry.getValue();
            double cash = transfer.getOrDefault(ResourceType.MONEY, 0d);
            if (flags.contains('d')) cash -= daysDefault * 500000;
            if (flags.contains('c')) cash = 0;
            cash = Math.max(0, cash);
            transfer.put(ResourceType.MONEY, cash);
        }

        try {
            String title = "Disperse raws " + "(" + daysDefault + " days)";

            String result = disperse(db, fundsToSendNations, fundsToSendAAs, note, new DiscordChannelIO(event), title);
            if (fundsToSendNations.size() > 1 || fundsToSendAAs.size() > 0) {
                RateLimitUtil.queue(event.getGuildChannel().sendMessage(author.getAsMention()));
            }
            return result;
        } catch (Throwable e) {
            e.printStackTrace();
            throw e;
>>>>>>> pr/15
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
                null,
                flags.contains('m'),
                flags.contains('b'),
                flags.contains('f'));
    }
}
