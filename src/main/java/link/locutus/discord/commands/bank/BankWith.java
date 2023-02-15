package link.locutus.discord.commands.bank;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.PermissionBinding;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.pnw.NationOrAlliance;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.offshore.OffshoreInstance;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.*;

public class BankWith extends Command {
    public static final Set<UUID> authorized = new HashSet<>();
    boolean disabled = false;

    public BankWith() {
        super("transfer", "withdraw", CommandCategory.ECON);
    }

    @Override
    public String help() {
        return Settings.commandPrefix(true) + "transfer <alliance|nation> <resource> <amount>";
    }

    @Override
    public String desc() {
        return """
                withdraw from the alliance bank
                Use `-f` to bypass all checks
                Use `-o` to subtract their existing funds from the transfer amount.""";
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
        if (args.size() < 2) {
            return usage(event);
        }

        final GuildDB guildDb = Locutus.imp().getGuildDB(event);

        boolean isAdmin = Roles.ECON.hasOnRoot(author);

        OffshoreInstance offshore = guildDb.getOffshore();
        if (offshore == null) {
            PermissionBinding.hasOffshore(guildDb, null);
            return "No offshore is set.";
        }

        List<String> otherNotes = new ArrayList<>();
        String primaryNote = "";
        boolean onlyRequired = flags.contains('o');
        boolean force = flags.contains('f');
        boolean grant = false;

        Collection<String> allowedLabels = Arrays.asList("#grant", "#deposit", "#trade", "#ignore", "#tax", "#warchest", "#account");

        if (!isAdmin) {
            GuildDB offshoreGuild = Locutus.imp().getGuildDBByAA(offshore.getAllianceId());
            if (offshoreGuild != null) {
                isAdmin = Roles.ECON.has(author, offshoreGuild.getGuild());
            }
        }

        if (!isAdmin && disabled) return "Please wait...";

        // parse notes
        for (Iterator<String> iter = args.iterator(); iter.hasNext(); ) {
            String arg = iter.next().toLowerCase();
            if (arg.toLowerCase().startsWith("#account=")) {
                primaryNote = arg;
                iter.remove();
            } else if (arg.startsWith("-expire") || arg.startsWith("#expire")) {
                otherNotes.add("#expire=" + arg.split("[:|=]")[1]);
                iter.remove();
            } else if (arg.startsWith("note:")) {
                arg = arg.split(":", 2)[1];
                if (!allowedLabels.contains(arg)) {
                    otherNotes.add(arg);
                } else {
                    primaryNote = arg;
                }
                iter.remove();
            } else if (arg.startsWith("#")) {
                String[] split = arg.toLowerCase().split(" ");
                String arg0 = split[0];
                if (!allowedLabels.contains(arg0)) {
                    otherNotes.add(arg0);
                } else {
                    primaryNote = arg0;
                    if (split.length > 1) {
                        otherNotes.addAll(Arrays.asList(split).subList(1, split.length));
                    }
                }
                iter.remove();
            } else if (arg.startsWith("-g:")) {
                UUID uuid = UUID.fromString(arg.split(":")[1]);
                if (!authorized.contains(uuid)) return "Invalid token";
                grant = true;
                if (primaryNote.isEmpty()) primaryNote = "#grant";
                iter.remove();
            } else if (arg.length() > 1 && arg.startsWith("-") && Character.isDigit(arg.charAt(1))) {
                arg = arg.substring(1);
                if (MathMan.isInteger(arg)) arg += "d";
                otherNotes.add("#expire=" + arg);
                iter.remove();
            }
        }

        if (primaryNote.isEmpty() && (!flags.contains('n') || guildDb.getOrNull(GuildDB.Key.ALLIANCE_ID) == null)) { //  && !grant
            return "Please use `" + Settings.commandPrefix(true) + "grant`, or one of the following labels: " + StringMan.getString(allowedLabels) + "\n" +
                    "note: You can add extra information after the label e.g. `\"#deposit ...\"`";
        }

        String arg = args.get(0);
        Integer alliance = arg.contains("/nation/") ? null : PnwUtil.parseAllianceId(arg);
        NationOrAlliance receiver;
        if (alliance == null || DBAlliance.get(alliance) == null) {
            Integer nationId = DiscordUtil.parseNationId(arg);
            if (nationId == null) {
                return "Invalid nation or alliance: " + arg;
            }
            receiver = Locutus.imp().getNationDB().getNation(nationId);
            if (receiver == null) {
                return "Invalid nation , Try again later.";
            }

        } else {
            receiver = DBAlliance.getOrCreate(alliance);
        }

        if (args.size() == 2 && args.get(1).contains("$")) {
            args.set(1, args.get(1).replace("$", ""));
            args.add(1, "money");
        }

        if (receiver.isAlliance() && onlyRequired) {
            return "Option `-o` only applicable for nations.";
        }

        Map<ResourceType, Double> transfer;
        if (args.size() == 3) {
            ResourceType resource;
            try {
                resource = ResourceType.valueOf(args.get(1).toUpperCase());
            } catch (IllegalArgumentException e) {
                return e.getMessage();
            }
            Double withdrawAmount = MathMan.parseDouble(args.get(2));
            if (withdrawAmount == null) {
                return "Invalid number amount: `" + args.get(2) + "`";
            }
            transfer = new HashMap<>();
            transfer.put(resource, withdrawAmount);
        } else {
            String content = DiscordUtil.trimContent(event.getMessage().getContentRaw());
            int jsonStart = content.indexOf('{');
            int jsonEnd = content.lastIndexOf('}');
            if (jsonStart == -1 || jsonEnd == -1) {
                return usage(event);
            }
            transfer = PnwUtil.parseResources(args.get(1));
            transfer.entrySet().removeIf(entry -> entry.getValue() <= 0);
        }
        for (Map.Entry<ResourceType, Double> entry : transfer.entrySet()) {
            if (entry.getValue() < 0 || entry.getValue().isNaN())
                return "The amount of " + entry.getKey() + " provided is invalid, are you sure it' s a positive number?";
        }

        {
            String name = author.getName();
            String msg = name + ": " + DiscordUtil.trimContent(event.getMessage().getContentRaw()) + " in " + "<#" + event.getChannel().getId() + "> " + event.getGuild().getName();
            MessageChannel logChannel = offshore.getGuildDB().getOrNull(GuildDB.Key.RESOURCE_REQUEST_CHANNEL);
            if (logChannel != null) {
                RateLimitUtil.queue(logChannel.sendMessage(msg));
            }
        }

        // TODO remove this
        // Allow certain ppl to bypass command cap (still limited by the account balance)
        long userId = event.getAuthor().getIdLong();
        if (PnwUtil.convertedTotal(transfer) > 1000000000L
                && userId != Settings.INSTANCE.ADMIN_USER_ID
                && !Settings.INSTANCE.LEGACY_SETTINGS.WHITELISTED_BANK_USERS.contains(userId)
                && !grant
        ) {
            return "No permission (1)";
        }

        DBNation banker = DiscordUtil.getNation(event);
        if (banker == null) {
            return "Please use " + Settings.commandPrefix(true) + "validate";
        }

        if (flags.contains('e')) {
            otherNotes.add("#expire=60d");
        }
        if (flags.contains('c')) {
            otherNotes.add("#cash=" + MathMan.format(PnwUtil.convertedTotal(transfer)));
        }

        String note = primaryNote;

        Integer aaId3 = guildDb.getOrNull(GuildDB.Key.ALLIANCE_ID);
        long senderId = aaId3 == null ? guild.getIdLong() : aaId3;
        if (!flags.contains('n')) note += "=" + senderId;
        if (!otherNotes.isEmpty()) note += " " + StringMan.join(otherNotes, " ");
        note = note.trim();

        if (note.contains("#cash") && !Roles.ECON.has(author, guild)) {
            return "You must have `ECON` Role to send with `#cash`.";
        }

        {
            if (receiver.isAlliance() && !note.contains("#ignore") && !flags.contains('f')) {
                return "Please include `#ignore` in note when transferring to alliances.";
            }
            if (receiver.isNation() && !note.contains("#deposit=") && !note.contains("#grant=") && !note.contains("#ignore")) {
                if (aaId3 == null)
                    return "Please *include* `#ignore` or `#deposit` or `#grant` in note when transferring to nations.";
                if (aaId3 != receiver.asNation().getAlliance_id())
                    return "Please include `#ignore` or `#deposit` or `#grant` in note when transferring to nations not in your alliance.";
            }
        }


        if (!force) {

            if (receiver.isNation() && receiver.asNation().getVm_turns() > 0)
                return "Receiver is in Vacation Mode, add `-f` to bypass.";
            if (receiver.isNation() && receiver.asNation().isGray()) return "Receiver is Gray, add `-f` to bypass.";
            if (receiver.isNation() && receiver.asNation().getNumWars() > 0 && receiver.asNation().isBlockaded())
                return "Receiver is blockaded, add `-f` to bypass.";
            if (receiver.isNation() && receiver.asNation().getActive_m() > 10000) {
                RateLimitUtil.queue(event.getChannel().sendMessage("!! **WARN**: Receiver is inactive, add `-f` to bypass."));
                String command = DiscordUtil.trimContent(event.getMessage().getContentRaw()) + " -f";
                List<String> forceErrors = new ArrayList<>();
                if (receiver.isNation() && receiver.asNation().getVm_turns() > 0)
                    forceErrors.add("Receiver is in Vacation Mode");
                if (receiver.isNation() && receiver.asNation().isGray()) forceErrors.add("Receiver is Gray");
                if (receiver.isNation() && receiver.asNation().getNumWars() > 0 && receiver.asNation().isBlockaded())
                    forceErrors.add("Receiver is blockaded");
                if (receiver.isNation() && receiver.asNation().getActive_m() > 10000)
                    forceErrors.add(("!! **WARN**: Receiver is inactive"));
                if (!forceErrors.isEmpty()) {
                    String title = forceErrors.size() + " **ERRORS**!";
                    String body = StringMan.join(forceErrors, "\n");
                    DiscordUtil.createEmbedCommand(event.getChannel(), title, body, "Send Anyway", command);
                    return null;
                }
            }

            // confirmation prompt
            String command = DiscordUtil.trimContent(event.getMessage().getContentRaw()) + " -f";

            String title;
            if (transfer.size() == 1) {
                Map.Entry<ResourceType, Double> entry = transfer.entrySet().iterator().next();
                title = MathMan.format(entry.getValue()) + " x " + entry.getKey();
                if (entry.getKey() == ResourceType.MONEY) title = "$" + title;
            } else {
                title = PnwUtil.resourcesToString(transfer);
            }
            title += " to " + (receiver.isAlliance() ? "AA " : "") + receiver.getName();
            if (receiver.isNation()) title += " | " + receiver.asNation().getAllianceName();
            String body = note + (note.isEmpty() ? "" : "\n") + "Press `Confirm` to confirm.";

            DiscordUtil.createEmbedCommand(event.getChannel(), title, body, "Confirm", command);
            return null;

        }
        return primaryNote;
    }
}
