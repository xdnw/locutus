package link.locutus.discord.commands.bank;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.JsonUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.sheet.SpreadSheet;
import link.locutus.discord.util.sheet.templates.TransferSheet;
import link.locutus.discord.util.task.DepositRawTask;
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
        if (args.isEmpty()) return usage(event);
        if (me == null) {
            return "Please use `" + Settings.commandPrefix(true) + "validate`";
        }

        boolean disperse = false;
        double daysDefault = 1;
        boolean force = flags.contains('f');
        boolean ignoreInactives = !flags.contains('i');
        String note = null;
        if (args.size() >= 3) note = args.get(2);

        Iterator<String> iter = args.iterator();
        while (iter.hasNext()) {
            String arg = iter.next();
            if (arg.equalsIgnoreCase("-a")) {
                // unused
//                admin = false;
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
        if (note == null) return "Please provide a note: `" + help() + "` e.g. `#ignore` or `#tax`";
        else if (note == null) note = "#tax";
        Collection<String> allowedLabels = Arrays.asList("#grant", "#deposit", "#trade", "#ignore", "#tax", "#warchest", "#account");
        if (!allowedLabels.contains(note.split("=")[0])) return "Please use one of the following labels: " + StringMan.getString(allowedLabels);

        GuildDB db = Locutus.imp().getGuildDB(guild);
        Integer aaId = db.getOrNull(GuildDB.Key.ALLIANCE_ID);
        if (aaId != null) note += "=" + aaId;
        else {
            note += "=" + guild.getIdLong();
        }

        Map<DBNation, Map<ResourceType, Double>> fundsToSendNations = new LinkedHashMap<>();
        Map<DBAlliance, Map<ResourceType, Double>> fundsToSendAAs = new LinkedHashMap<>();

        String arg = args.get(0);
        if (arg.startsWith("https://docs.google.com/spreadsheets/d/") || arg.startsWith("sheet:")) {

            String key;
            if (arg.startsWith("sheet:")) {
                key = arg.split(":")[1];
            } else {
                key = arg.split("/")[5];
            }

            SpreadSheet sheet = SpreadSheet.create(key);
            Map<String, Boolean> result = sheet.parseTransfers(fundsToSendNations, fundsToSendAAs);
            List<String> invalid = new ArrayList<>();
            for (Map.Entry<String, Boolean> entry : result.entrySet()) {
                if (!entry.getValue()) {
                    invalid.add(entry.getKey());
                }
            }
            if (!invalid.isEmpty() && !force) return "Invalid nations/alliance:\n - " + StringMan.join(invalid, "\n - ");
        } else {
            Message message;
            if (!disperse) {
                message = RateLimitUtil.complete(event.getChannel().sendMessage("The following is a preview. Each disbursement must be authorized by a banker via"));
            } else {
                message = RateLimitUtil.complete(event.getChannel().sendMessage("Fetching city information:"));
            }
            Integer allianceId = me.getAlliance_id();
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
            if (nations != null && !nations.isEmpty()) allianceId = nations.get(0).getAlliance_id();

            Consumer<String> updateTask = s -> RateLimitUtil.queue(event.getChannel().editMessageById(message.getIdLong(), s));
            Consumer<String> errors = new Consumer<String>() {
                @Override
                public void accept(String s) {
                    RateLimitUtil.queue(event.getChannel().sendMessage(s));
                }
            };
            if (nations.isEmpty()) return "No nations in tax bracket";
            daysDefault = Integer.parseInt(args.get(args.size() - 1));
            fundsToSendNations = new DepositRawTask(nations, allianceId, updateTask, daysDefault, true, ignoreInactives, errors).setForce(force).call();
            if (nations.isEmpty()) {
                return "No nations found (1)";
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

            String result = disperse(db, fundsToSendNations, fundsToSendAAs, note, event.getGuildChannel(), title);
            if (fundsToSendNations.size() > 1 || fundsToSendAAs.size() > 0) {
                RateLimitUtil.queue(event.getGuildChannel().sendMessage(author.getAsMention()));
            }
            return result;
        } catch (Throwable e) {
            e.printStackTrace();
            throw e;
        }
    }

    public static String disperse(GuildDB db, Map<DBNation, Map<ResourceType, Double>> fundsToSendNations, Map<DBAlliance, Map<ResourceType, Double>> fundsToSendAAs, String note, MessageChannel channel, String title) throws GeneralSecurityException, IOException {
        if (fundsToSendNations.isEmpty() && fundsToSendAAs.isEmpty()) {
            return "No funds need to be sent";
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

            StringBuilder body = new StringBuilder("```" + post + "```\n");
            body.append(nation.getNationUrlMarkup(true) + "\n");
            body.append("Worth: $" + MathMan.format(PnwUtil.convertedTotal(total)));
            DiscordUtil.createEmbedCommand(channel, title, body.toString(), "\u2705", command, "\uD83D\uDEAB", "");
            return null;
        } else {

            String arr = JsonUtil.toPrettyFormat("[" + StringMan.join(postScript, ",") + "]");
            DiscordUtil.upload(channel, "transfer.json", arr);

            TransferSheet sheet = new TransferSheet(db).write(fundsToSendNations, fundsToSendAAs).build();

            String emoji = "\u2705";
            String cmd = Settings.commandPrefix(false) + "transferBulk " + sheet.getURL() + " " + note;

            StringBuilder response = new StringBuilder();
            response.append("Transfer Sheet: <" + sheet.getURL() + ">").append("\n");
            response.append("Total: $" + MathMan.format(PnwUtil.convertedTotal(total)) + ": `" + PnwUtil.resourcesToString(total)).append("`\n");
            response.append("Info: Use the extension to disburse from offshore or press " + emoji + " to run:\n" +
                    "`" + Settings.commandPrefix(false) + "transferBulk <sheet> " + note + "`");

            DiscordUtil.createEmbedCommand(channel, "Disperse", response.toString(), emoji, cmd);
            return null;
        }
    }
}
