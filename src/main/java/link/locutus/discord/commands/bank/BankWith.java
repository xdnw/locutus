package link.locutus.discord.commands.bank;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.AllianceMeta;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.NationMeta;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.pnw.NationOrAlliance;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.JsonUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.offshore.OffshoreInstance;
import link.locutus.discord.apiv1.enums.ResourceType;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class BankWith extends Command {
    boolean disabled = false;
    public BankWith() {
        super("transfer", "withdraw", CommandCategory.ECON);
    }

    @Override
    public String help() {
        return Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "transfer <alliance|nation> <resource> <amount>";
    }

    @Override
    public String desc() {
        return "withdraw from the alliance bank\n" +
                "Use `-f` to bypass all checks\n" +
                "Use `-o` to subtract their existing funds from the transfer amount";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        if (Roles.ECON.hasOnRoot(user)) return true;
        if (!Roles.MEMBER.has(user, server)) return false;
        GuildDB db = Locutus.imp().getGuildDB(server);
        return db.getOffshore() != null;
    }

    public static final Set<UUID> authorized = new HashSet<>();

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() < 2) {
            return usage(event);
        }

        final GuildDB guildDb = Locutus.imp().getGuildDB(event);

        boolean isAdmin = Roles.ECON.hasOnRoot(author);

        OffshoreInstance offshore = guildDb.getOffshore();

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

        if (!isAdmin && disabled) return "Please wait";

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
                        for (int i = 1; i < split.length; i++) otherNotes.add(split[i]);
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
            return "Please use `" + Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "grant`, or one of the following labels: " + StringMan.getString(allowedLabels) + "\n" +
                    "note: You can add extra information after the label e.g. `\"#deposit blah\"`";
        }

        String arg = args.get(0);
        Integer alliance = arg.contains("/nation/") ? null : PnwUtil.parseAllianceId(arg);
        NationOrAlliance receiver;
        if (alliance == null || Locutus.imp().getNationDB().getAllianceName(alliance) == null) {
            Integer nationId = DiscordUtil.parseNationId(arg);
            if (nationId == null) {
                return "Invalid nation or alliance: " + arg;
            }
            receiver = Locutus.imp().getNationDB().getNation(nationId);
            if (receiver == null) {
                return "Invalid nation. Try again later";
            }

        } else {
            receiver = DBAlliance.getOrCreate(alliance);
        }

        if (args.size() == 2 && args.get(1).contains("$")) {
            args.set(1, args.get(1).replace("$", ""));
            args.add(1, "money");
        }

        if (receiver.isAlliance() && onlyRequired) {
            return "Option `-o` only applicable for nations";
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
            if (entry.getValue() < 0 || entry.getValue().isNaN()) return "The amount of " + entry.getKey() + " provided is invalid (are you sure its a positive number?)";
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
            return "Please use " + Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "validate";
        }

        if (flags.contains('e')) {
            otherNotes.add("#expire=60d");
        }
        if (flags.contains('c')) {
            otherNotes.add("#cash=" + MathMan.format(PnwUtil.convertedTotal(transfer)));
        }

        String receiverStr = receiver.getName();
        String note = primaryNote;

        Integer aaId3 = guildDb.getOrNull(GuildDB.Key.ALLIANCE_ID);
        long senderId = aaId3 == null ? guild.getIdLong() : aaId3;
        if (!flags.contains('n')) note += "=" + senderId;
        if (!otherNotes.isEmpty()) note += " " + StringMan.join(otherNotes, " ");
        note = note.trim();

        if (note.contains("#cash") && !Roles.ECON.has(author, guild)) {
            return "You must have `ECON` Role to send with `#cash`";
        }

        {
            if (receiver.isAlliance() && !note.contains("#ignore") && !flags.contains('f')) {
                return "Please include `#ignore` in note when transferring to alliances";
            }
            if (receiver.isNation() && !note.contains("#deposit=") && !note.contains("#grant=") && !note.contains("#ignore")) {
                if (aaId3 == null) return "Please *include* `#ignore` or `#deposit` or `#grant` in note when transferring to nations";
                if (aaId3 != receiver.asNation().getAlliance_id()) return "Please include `#ignore` or `#deposit` or `#grant` in note when transferring to nations not in your alliance";
            }
        }

        // transfer json if they dont have perms to do the transfer
        if (offshore == null) {
            // don't send it
            String json = PnwUtil.resourcesToJson(receiverStr, receiver.isNation(), transfer, note);
            String prettyJson = JsonUtil.toPrettyFormat("[" + json + "]");

            String nationEmoji = "\uD83E\uDD11";
            String aaEmoji = "\uD83C\uDFE6";

            StringBuilder body = new StringBuilder();

            body.append("```").append(prettyJson).append("```").append("\n");
            body.append("Total: `" + StringMan.getString(transfer) + "`").append('\n');
            body.append("Worth: ~$" + MathMan.format(PnwUtil.convertedTotal(transfer)) + "`").append("\n\n");

            String title = (receiver.isNation() ? "NATION:" : "ALLIANCE:") + receiver.getName() + " " + note;

            // send message, with reactions to send to nation or alliance
            List<String> params = new ArrayList<>();

//            String allianceUrl = PnwUtil.getUrl(guildDb.getOrThrow(GuildDB.Key.ALLIANCE_ID), true);
//            String aaCmd = Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "pending " + title + Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "transfer -f " + allianceUrl + " " + PnwUtil.resourcesToString(transfer) + " " + note;
//            params.add(aaEmoji);
//            params.add(aaCmd);
//
//            if (receiver.isNation()) {
//                String nationCmd = Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "pending " + title + Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "transfer -f " + receiver.getUrl() + " " + PnwUtil.resourcesToString(transfer) + " " + note;
//                params.add(nationEmoji);
//                params.add(nationCmd);
//            }
//
//            params.add("\uD83D\uDD04");
//            params.add("");

            DiscordUtil.createEmbedCommand(event.getChannel(), title, body.toString(), params.toArray(new String[0]));
            return "See also:\n" +
                    "> https://docs.google.com/document/d/1QkN1FDh8Z8ENMcS5XX8zaCwS9QRBeBJdCmHN5TKu_l8\n" +
                    "To add an offshore:\n" +
                    "1. (int this server) `" + Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "SetCoalition <offshore-alliance> offshore`\n" +
                    "2. (in the offshore) `" + Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "SetCoalition <alliance> offshoring`";
        }

        if (!force) {
            if (receiver.isNation() && receiver.asNation().getVm_turns() > 0) return "Receiver is in Vacation Mode (add `-f` to bypass)";
            if (receiver.isNation() && receiver.asNation().isGray()) return "Receiver is Gray (add `-f` to bypass)";
            if (receiver.isNation() && receiver.asNation().getNumWars() > 0 && receiver.asNation().isBlockaded()) return "Receiver is blockaded (add `-f` to bypass)";
            if (receiver.isNation() && receiver.asNation().getActive_m() > 10000) {
                RateLimitUtil.queue(event.getChannel().sendMessage("!! **WARN**: Receiver is inactive (add `-f` to bypass)"));
            }
        }

        // confirmation prompt
        if (!force) {
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
            String body = note + (note.isEmpty() ? "" : "\n") + "Press \u2705 to confirm";

            if (false) { // TODO alert if nation has deposits, but -e is used for temporary expiring transfer
//                if (receiver.isNation() && aaId != null && receiver.asNation().getAlliance_id() == aaId) {
//                    double[] netDeposits = receiver.asNation().getNetDeposits(guildDb);
//                    double converted = PnwUtil.convertedTotal(netDeposits);
//                    double[] requiredArr = PnwUtil.resourcesToArray(transfer);
//                    double requiredConverted = PnwUtil.convertedTotal(requiredArr);
//                    boolean hasRequired = true;
//                    for (int i = 0; i < netDeposits.length; i++) {
//                        if (requiredArr[i] > netDeposits[i]) {
//                            hasRequired = false;
//                            break;
//                        }
//                    }
//                    body.append("Deposits: `" + PnwUtil.resourcesToString(netDeposits) + "`\n");
//                    if (flags.contains('e')) {
//                        if (converted > requiredConverted) {
//
//                        }
//                    }
//                }
            }

            DiscordUtil.createEmbedCommand(event.getChannel(), title, body, "\u2705", command);
            return null;
        }

        GuildDB offshoreDb = offshore.getGuildDB();
        if (offshoreDb == null) return "Error: No guild DB set for offshore??";

        synchronized (OffshoreInstance.BANK_LOCK) {
            Map<ResourceType, Double> guildOrAADeposits;

            Integer aaId2 = guildDb.getOrNull(GuildDB.Key.ALLIANCE_ID);

            if (!isAdmin) {
                if (offshore.disabledGuilds.contains(guildDb.getGuild().getIdLong())) {
                    MessageChannel logChannel = offshore.getGuildDB().getOrNull(GuildDB.Key.RESOURCE_REQUEST_CHANNEL);
                    if (logChannel != null) {
                        String msg = "Transfer error: " + guild.toString() + " | " + aaId2 + " | <@" + Settings.INSTANCE.ADMIN_USER_ID + (">");
                        RateLimitUtil.queue(logChannel.sendMessage(msg));
                    }
                    return "An error occured. Please request an administrator transfer the funds";
                }

                if (!Roles.ECON.has(author, guild)) {
                    if (aaId2 != null) {
                        if (me.getAlliance_id() != aaId2 || me.getPosition() <= 1)
                            return "You are not a member of " + aaId2;
                    } else if (!Roles.MEMBER.has(author, guild)) {
                        Role memberRole = Roles.MEMBER.toRole(guild);
                        if (memberRole == null) return "No member role enabled (see `" + Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "AliasRole`)";
                        return "You do not have the member role: " + memberRole.getName();
                    }
                    if (guildDb.getOrNull(GuildDB.Key.MEMBER_CAN_WITHDRAW) != Boolean.TRUE)
                        return "`MEMBER_CAN_WITHDRAW` is false (see `" + Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "KeyStore` )";
                    GuildMessageChannel channel = guildDb.getOrNull(GuildDB.Key.RESOURCE_REQUEST_CHANNEL);
                    if (channel == null)
                        return "Please have an admin use. `" + Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "KeyStore RESOURCE_REQUEST_CHANNEL #someChannel`";
                    if (event.getChannel().getIdLong() != channel.getIdLong())
                        return "Please use the transfer command in " + channel.getAsMention();

                    if (!Roles.ECON_WITHDRAW_SELF.has(author, guild))
                        return "You do not have the `ECON_WITHDRAW_SELF` role. See: `" + Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "aliasrole`";
                    if (!receiver.isNation() || receiver.getId() != me.getId())
                        return "You only have permission to withdraw to yourself";

                    if (guildDb.getOrNull(GuildDB.Key.MEMBER_CAN_WITHDRAW_WARTIME) != Boolean.TRUE && aaId2 != null) {
                        if (!guildDb.getCoalition("enemies").isEmpty())
                            return "You cannot withdraw during wartime. `MEMBER_CAN_WITHDRAW_WARTIME` is false (see `" + Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "KeyStore`) and `enemies` is set (see: `" + Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "setcoalition` | `" + Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "removecoalition` | `" + Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "coalitions`)";
                        DBAlliance aaObj = DBAlliance.getOrCreate(aaId2);
                        ByteBuffer warringBuf = aaObj.getMeta(AllianceMeta.IS_WARRING);
                        if (warringBuf != null && warringBuf.get() == 1)
                            return "You cannot withdraw during wartime. `MEMBER_CAN_WITHDRAW_WARTIME` is false (see `" + Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "KeyStore`)";
                    }

                    // check that we personally have the required deposits
                    Boolean ignoreGrants = guildDb.getOrNull(GuildDB.Key.MEMBER_CAN_WITHDRAW_IGNORES_GRANTS);
                    if (ignoreGrants == null) ignoreGrants = false;

                    double[] myDeposits = me.getNetDeposits(guildDb, !ignoreGrants);
                    myDeposits = PnwUtil.normalize(myDeposits);
                    double myDepoValue = PnwUtil.convertedTotal(myDeposits, false);
                    double txValue = PnwUtil.convertedTotal(transfer);

                    if (myDepoValue <= 0)
                        return "Your deposits value (market min of $" + MathMan.format(myDepoValue) + ") is insufficient (transfer value $" + MathMan.format(txValue) + ")";

                    boolean rssConversion = guildDb.getOrNull(GuildDB.Key.RESOURCE_CONVERSION) == Boolean.TRUE;
                    boolean hasExactResources = true;
                    for (Map.Entry<ResourceType, Double> entry : transfer.entrySet()) {
                        double amt = myDeposits[entry.getKey().ordinal()];
                        if (amt + 0.01 < entry.getValue()) {
                            if (!rssConversion) {
                                return "You do not have `" + MathMan.format(entry.getValue()) + "x" + entry.getKey() + "`. (see `" + Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX +
                                        "depo @user` ). RESOURCE_CONVERSION is disabled (see `" + Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "KeyStore RESOURCE_CONVERSION`)\n" +
                                        "You may withdraw up to `" + MathMan.format(amt) + "` " + entry.getKey();
                            }
                            hasExactResources = false;
                        }
                    }
                    if (!hasExactResources && myDepoValue < txValue) {
                        return "Your deposits are worth $" + MathMan.format(myDepoValue) + "(market min) but you requested to withdraw $" + MathMan.format(txValue) + " worth of resources";
                    }
                    if (!PnwUtil.isNoteFromDeposits(note, senderId, System.currentTimeMillis())) {
                        return "Only `#deposit` is permitted as the note, you provided: `" + note + "`";
                    }
                }
            }
            double[] deposits = offshore.getDeposits(guildDb);

            MessageChannel logChannel = offshore.getGuildDB().getOrNull(GuildDB.Key.RESOURCE_REQUEST_CHANNEL);
            if (logChannel != null) {
                String msg = "Prior Deposits for: " + guild.toString() + "/" + aaId2 + ": `" + PnwUtil.resourcesToString(deposits) + ("`");
                RateLimitUtil.queue(logChannel.sendMessage(msg));
            }

            if (!isAdmin && (aaId2 == null || offshore.getAllianceId() != aaId2)) {
                for (Map.Entry<ResourceType, Double> entry : transfer.entrySet()) {
                    ResourceType rss = entry.getKey();
                    Double amt = entry.getValue();
                    if (amt > 0 && deposits[rss.ordinal()] + 0.01 < amt) {
                        return "You do not have " + MathMan.format(amt) + " x " + rss.name();
                    }
                }
            }

            double[] amount = PnwUtil.resourcesToArray(transfer);
            Map.Entry<OffshoreInstance.TransferStatus, String> result = offshore.transferFromDeposits(me, guildDb, receiver, amount, note);

            if (result.getKey() == OffshoreInstance.TransferStatus.SUCCESS) {
                banker.setMeta(NationMeta.INTERVIEW_TRANSFER_SELF, (byte) 1);
            }

            return "`" + PnwUtil.resourcesToString(transfer) + "` -> " + receiver.getUrl() + "\n**" + result.getKey() + "**: " + result.getValue();
        }
    }

    private final Map<Integer, double[]> withdrawalLimit = new HashMap<>();
}
