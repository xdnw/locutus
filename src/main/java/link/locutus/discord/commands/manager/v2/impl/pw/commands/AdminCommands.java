package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.core.ApiKeyPool;
import link.locutus.discord.apiv1.domains.subdomains.attack.DBAttack;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import link.locutus.discord.apiv2.PoliticsAndWarV2;
import link.locutus.discord.apiv3.PoliticsAndWarV3;
import link.locutus.discord.apiv3.enums.AlliancePermission;
import link.locutus.discord.apiv3.enums.NationLootType;
import link.locutus.discord.commands.manager.v2.binding.annotation.*;
import link.locutus.discord.commands.manager.v2.binding.annotation.Timestamp;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.HasApi;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.config.Messages;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.NationDB;
import link.locutus.discord.db.entities.*;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.guild.GuildSetting;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.db.guild.SheetKeys;
import link.locutus.discord.event.Event;
import link.locutus.discord.db.entities.AllianceMetric;
import link.locutus.discord.db.entities.Coalition;
import link.locutus.discord.pnw.AllianceList;
import link.locutus.discord.pnw.NationList;
import link.locutus.discord.pnw.PNWUser;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.DocPrinter2;
import link.locutus.discord.util.FileUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.io.PagePriority;
import link.locutus.discord.util.io.PageRequestQueue;
import link.locutus.discord.util.offshore.Auth;
import link.locutus.discord.util.offshore.OffshoreInstance;
import link.locutus.discord.util.sheet.SpreadSheet;
import link.locutus.discord.util.task.EditAllianceTask;
import link.locutus.discord.util.update.NationUpdateProcessor;
import com.google.gson.JsonObject;
import link.locutus.discord.apiv1.enums.Rank;
import com.politicsandwar.graphql.model.ApiKeyDetails;
import link.locutus.discord.util.update.WarUpdateProcessor;
import link.locutus.discord.web.jooby.WebRoot;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.User;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class AdminCommands {

    @Command
    @RolePermission(value = Roles.ADMIN, root = true)
    public String checkActiveConflicts() {
        WarUpdateProcessor.checkActiveConflicts();
        return "Done! (see console)";
    }

    @Command
    @RolePermission(value = Roles.ADMIN, root = true)
    public String showFileQueue() {
        PageRequestQueue handler = FileUtil.getPageRequestQueue();
        PriorityQueue<PageRequestQueue.PageRequestTask<?>> jQueue = handler.getQueue();

        Map<PagePriority, Integer> pagePriorities = new HashMap<>();
        int unknown = 0;
        synchronized (jQueue) {
            for (PageRequestQueue.PageRequestTask<?> task : jQueue) {
                long priority = task.getPriority();
                int ordinal = (int) (priority / Integer.MAX_VALUE);
                if (ordinal >= PagePriority.values.length) unknown++;
                else {
                    PagePriority pagePriority = PagePriority.values[ordinal];
                    pagePriorities.put(pagePriority, pagePriorities.getOrDefault(pagePriority, 0) + 1);
                }
            }
        }
        List<Map.Entry<PagePriority, Integer>> entries = new ArrayList<>(pagePriorities.entrySet());
        // sort
        entries.sort((o1, o2) -> o2.getValue() - o1.getValue());

        StringBuilder sb = new StringBuilder();
        sb.append("File Queue:\n");
        for (Map.Entry<PagePriority, Integer> entry : entries) {
            sb.append(entry.getKey().name()).append(": ").append(entry.getValue()).append("\n");
        }
        if (unknown > 0) {
            sb.append("Unknown: ").append(unknown).append("\n");
        }
        return sb.toString();
    }

    @Command
    @RolePermission(value = Roles.ADMIN, root = true)
    public String dumpWiki(@Default String pathRelative) throws IOException, InvocationTargetException, IllegalAccessException {
        if (pathRelative == null) pathRelative = "../locutus.wiki";
        CommandManager2 manager = Locutus.imp().getCommandManager().getV2();

        String commandsStr = DocPrinter2.printCommands(manager.getCommands(), manager.getStore(), manager.getPermisser());
        String argumentsStr = DocPrinter2.printParsers(manager.getStore());
        String placeholderStr = DocPrinter2.printPlaceholders(manager.getNationPlaceholders(), manager.getStore());
        String aaPlaceholderStr = DocPrinter2.printPlaceholders(manager.getNationPlaceholders(), manager.getStore());

        // write commandsStr to file `path` + File.separator + "commands.md"
        File file = new File(pathRelative + File.separator + "commands.md");
        Files.write(file.toPath(), commandsStr.getBytes());

        // write argumentsStr   to file `path` + File.separator + "arguments.md"
        file = new File(pathRelative + File.separator + "arguments.md");
        Files.write(file.toPath(), argumentsStr.getBytes());

        // write placeholderStr to file `path` + File.separator + "kingdom_placeholders.md"
        file = new File(pathRelative + File.separator + "nation_placeholders.md");
        Files.write(file.toPath(), placeholderStr.getBytes());

        file = new File(pathRelative + File.separator + "alliance_placeholders.md");
        Files.write(file.toPath(), aaPlaceholderStr.getBytes());

        return "Done!";
    }


    @Command
    @RolePermission(value = Roles.ADMIN, root = true)
    public String syncTreasures() {
        Locutus.imp().getNationDB().updateTreasures(Event::post);
        return "Done!";
    }

    @Command(desc = "Generate a sheet of recorded login times for a set of nations within a time range")
    public String loginTimes(@Me GuildDB db, @Me IMessageIO io, Set<DBNation> nations, @Timestamp long cutoff, @Switch("s") SpreadSheet sheet) throws IOException, GeneralSecurityException {
        if (sheet == null) {
            sheet = SpreadSheet.create(db, SheetKeys.NATION_SHEET);
        }
        // time cutoff < 30d
        if (System.currentTimeMillis() - cutoff > TimeUnit.DAYS.toMillis(30)) {
            return "Cutoff must be within the last 30 days";
        }
        if (nations.size() > 30) {
            return "Too many nations (max: 30)";
        }
        List<String> header = new ArrayList<>(Arrays.asList(
                "nation",
                "time"
        ));
        sheet.addRow(header);
        for (DBNation nation : nations) {
            List<DBSpyUpdate> activity = Locutus.imp().getNationDB().getSpyActivityByNation(nation.getNation_id(), cutoff);
            for (DBSpyUpdate update : activity) {
                header.set(0, String.valueOf(nation.getNation_id()));
                header.set(1, TimeUtil.YYYY_MM_DD_HH_MM_SS.format(new Date(update.timestamp)));
                sheet.addRow(header);
            }
        }

        sheet.clearAll();
        sheet.set(0, 0);

        sheet.attach(io.create()).send();
        return null;
    }

    @Command(desc = "Pull registered nations from locutus\n" +
            "(or a provided url)")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String syncDiscordWithLocutus(@Default String url) throws IOException {
        if (url == null) {
            url = WebRoot.REDIRECT + "/discordids";
        }
        int count = 0;
        // read string from url
        String csvTabSeparated = FileUtil.readStringFromURL(PagePriority.DISCORD_IDS_ENDPOINT.ordinal(), url);
        // split into lines
        String[] lines = csvTabSeparated.split("\n");
        // iterate each line
        for (String line : lines) {
            String[] columns = line.split("\t");
            int nationId = Integer.parseInt(columns[0]);
            long discordId = Long.parseLong(columns[1]);
            PNWUser existing = Locutus.imp().getDiscordDB().getUserFromDiscordId(discordId);
            if (existing != null && existing.getNationId() == nationId && existing.getDiscordId() == discordId) {
                continue;
            }

            String username = null;
            if (columns.length > 2) {
                username = columns[2];
                if (username.isEmpty()) {
                    username = null;
                }
            }
            if (username == null) username = discordId + "";

            // register the user
            count++;
            Locutus.imp().getDiscordDB().addUser(new PNWUser(nationId, discordId, username));
        }
        return "Done! Imported " + count + "/" + lines.length + " users from " + url;
    }
    @Command
    @RolePermission(value = Roles.ADMIN, root = true)
    public String deleteAllInaccessibleChannels(@Switch("f") boolean force) {
        Map<GuildDB, List<GuildSetting>> toUnset = new LinkedHashMap<>();

        for (GuildDB db : Locutus.imp().getGuildDatabases().values()) {
            if (force) {
                List<GuildSetting> keys = db.listInaccessibleChannelKeys();
                if (!keys.isEmpty()) {
                    toUnset.put(db, keys);
                }
            } else {
                db.unsetInaccessibleChannels();
            }
        }

        if (toUnset.isEmpty()) {
            return "No keys to unset";
        }
        StringBuilder response = new StringBuilder();
        for (Map.Entry<GuildDB, List<GuildSetting>> entry : toUnset.entrySet()) {
            response.append(entry.getKey().getGuild().toString() + ":\n");
            List<String> keys = entry.getValue().stream().map(f -> f.name()).collect(Collectors.toList());
            response.append("- " + StringMan.join(keys, "\n- "));
            response.append("\n");
        }
        String footer = "Rerun the command with `-f` to confirm";
        return response + footer;
    }


    @Command(desc = "Reset city names")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String resetCityNames(@Me DBNation me, @Me Auth auth, String name) throws IOException {
        for (int id : me.getCityMap(false).keySet()) {
            auth.setCityName(id, name);
        }
        return "Done!";
    }


    @Command
    @RolePermission(value = Roles.ADMIN, root = true)
    public void stop(boolean save) {
        Locutus.imp().stop();
    }

    @Command
    @RolePermission(value = Roles.ADMIN, root = true)
    public String syncReferrals(@Me GuildDB db) {
        if (!db.isValidAlliance()) return "Not in an alliance";
        Collection<DBNation> nations = db.getAllianceList().getNations(true, 10000, true);
        for (DBNation nation : nations) {
            db.getHandler().onRefer(nation);
        }
        return "Done!";
    }

    @Command(desc = "Set the archive status of the bot's announcement")
    @RolePermission(any = true, value = {Roles.INTERNAL_AFFAIRS, Roles.MILCOM, Roles.ADMIN, Roles.FOREIGN_AFFAIRS, Roles.ECON})
    public String archiveAnnouncement(@Me GuildDB db, int announcementId, @Default Boolean archive) {
        if (archive == null) archive = true;
        db.setAnnouncementActive(announcementId, !archive);
        return (archive ? "Archived" : "Unarchived") + " announcement with id: #" + announcementId;
    }

    @Command(desc = "Send an announcement to multiple nations, with random variations for each receiver\n")
    @RolePermission(Roles.ADMIN)
    @HasApi
    @NoFormat
    public String announce(@Me GuildDB db, @Me Guild guild, @Me JSONObject command, @Me IMessageIO currentChannel,
                           @Me User author,
                           NationList sendTo,
                           @Arg("The subject used for sending an in-game mail if a discord direct message fails") String subject,
                           @Arg("The message you want to send") @TextArea String announcement,
                           @Arg("Lines of replacement words or phrases, separated by `|` for each variation\n" +
                                   "Add multiple lines for each replacement you want") @TextArea String replacements,
                           @Arg("The required number of differences between each message") @Switch("v") @Default("0") Integer requiredVariation,
                           @Arg("The required depth of changes from the original message") @Switch("r") @Default("0") Integer requiredDepth,
                           @Arg("Variation seed. The same seed will produce the same variations, otherwise results are random") @Switch("s") Long seed,
                           @Arg("If messages are sent in-game") @Switch("m") boolean sendMail,
                           @Arg("If messages are sent via discord direct message") @Switch("d") boolean sendDM,
                           @Switch("f") boolean force) throws IOException {
        ApiKeyPool keys = db.getMailKey();
        if (keys == null) throw new IllegalArgumentException("No API_KEY set, please use " + CM.credentials.addApiKey.cmd.toSlashMention() + "");
        Set<Integer> aaIds = db.getAllianceIds();

        List<String> errors = new ArrayList<>();
        Collection<DBNation> nations = sendTo.getNations();
        for (DBNation nation : nations) {
            User user = nation.getUser();
            if (user == null) {
                errors.add("Cannot find user for `" + nation.getNation() + "`");
            } else if (guild.getMember(user) == null) {
                errors.add("Cannot find member in guild for `" + nation.getNation() + "` | `" + user.getName() + "`");
            } else {
                continue;
            }
            if (!aaIds.isEmpty() && !aaIds.contains(nation.getAlliance_id())) {
                throw new IllegalArgumentException("Cannot send to nation not in alliance: " + nation.getNation() + " | " + user);
            }
            if (!force) {
                if (nation.getActive_m() > 20000)
                    return "The " + nations.size() + " receivers includes inactive for >2 weeks. Use `" + sendTo.getFilter() + ",#active_m<20000` or set `force` to confirm";
                if (nation.getVm_turns() > 0)
                    return "The " + nations.size() + " receivers includes vacation mode nations. Use `" + sendTo.getFilter() + ",#vm_turns=0` or set `force` to confirm";
                if (nation.getPosition() < 1) {
                    return "The " + nations.size() + " receivers includes applicants. Use `" + sendTo.getFilter() + ",#position>1` or set `force` to confirm";
                }
            }
        }

        List<String> replacementLines = Arrays.asList(replacements.split("\n"));

        Random random = seed == null ? new Random() : new Random(seed);

        Set<String> results = StringMan.enumerateReplacements(announcement, replacementLines, nations.size() + 1000, requiredVariation, requiredDepth);

        if (results.size() < nations.size()) return "Not enough entropy. Please provide more replacements";

        if (!force) {
            StringBuilder confirmBody = new StringBuilder();
            if (!sendDM && !sendMail) confirmBody.append("**Warning: No ingame or direct message option has been specified**\n");
            confirmBody.append("Send DM (`-d`): " + sendDM).append("\n");
            confirmBody.append("Send Ingame (`-m`): " + sendMail).append("\n");
            if (!errors.isEmpty()) {
                confirmBody.append("\n**Errors**:\n- " + StringMan.join(errors, "\n- ")).append("\n");
            }
//            DiscordUtil.createEmbedCommand(currentChannel, "Send to " + nations.size() + " nations", confirmBody + "\nPress to confirm", );
            DiscordUtil.pending(currentChannel, command, "Send to " + nations.size() + " nations", confirmBody + "\nPress to confirm");
            return null;
        }

        currentChannel.send("Please wait...");

        List<String> resultsArray = new ArrayList<>(results);
        Collections.shuffle(resultsArray, random);

        resultsArray = resultsArray.subList(0, nations.size());

        List<Integer> failedToDM = new ArrayList<>();
        List<Integer> failedToMail = new ArrayList<>();

        StringBuilder output = new StringBuilder();

        Map<DBNation, String> sentMessages = new HashMap<>();

        int i = 0;
        for (DBNation nation : nations) {
            String replaced = resultsArray.get(i++);
            String personal = replaced + "\n\n- " + author.getAsMention() + " " + guild.getName();

            boolean result = sendDM && nation.sendDM(personal);
            if (!result && sendDM) {
                failedToDM.add(nation.getNation_id());
            }
            if (!result || sendMail) {
                try {
                    nation.sendMail(keys, subject, personal);
                } catch (IllegalArgumentException e) {
                    failedToMail.add(nation.getNation_id());
                }
            }

            sentMessages.put(nation, replaced);

            output.append("\n\n```" + replaced + "```" + "^ " + nation.getNation());
        }

        output.append("\n\n------\n");
        if (errors.size() > 0) {
            output.append("Errors:\n- " + StringMan.join(errors, "\n- "));
        }
        if (failedToDM.size() > 0) {
            output.append("\nFailed DM (sent ingame): " + StringMan.getString(failedToDM));
        }
        if (failedToMail.size() > 0) {
            output.append("\nFailed Mail: " + StringMan.getString(failedToMail));
        }

        int annId = db.addAnnouncement(author, subject, announcement, replacements, sendTo.getFilter());
        for (Map.Entry<DBNation, String> entry : sentMessages.entrySet()) {
            byte[] diff = StringMan.getDiffBytes(announcement, entry.getValue());
            db.addPlayerAnnouncement(entry.getKey(), annId, diff);
        }

        return output.toString().trim();
    }

//    @Command
//    @RolePermission(value = Roles.ADMIN, root = true)
//    public String whitelistAuto(double requiredBank, double requiredDeposit, long timeFrame) {
//        GuildDB rootDb = Locutus.imp().getRootDb();
//        OffshoreInstance offshore = rootDb.getOffshore();
//
//        long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(10);
//
//        Set<Integer> currentWhitelist = rootDb.getCoalition(Coalition.WHITELISTED_AUTO);
//        Set<Integer> offshoring = rootDb.getCoalition(Coalition.OFFSHORING);
//
//        Map<Integer, Double> totalDepositByAA = new HashMap<>();
//        Map<Integer, Double> totalTransferByAA = new HashMap<>();
//
//        for (int allianceId : offshoring) {
//            GuildDB other = Locutus.imp().getGuildDBByAA(allianceId);
//            if (other == null || other.getOffshoreDB() != rootDb) continue;
//
//            double[] deposits = offshore.getDeposits(other, false);
//            double total = PnwUtil.convertedTotal(deposits);
//
//            List<Transaction2> tx = offshore.getTransactionsAA(allianceId, false);
//            tx.removeIf(f -> f.tx_datetime < cutoff);
//            tx.removeIf(f -> f.sender_id != allianceId);
//            double txWeek = tx.stream().mapToDouble(f -> f.convertedTotal()).sum();
//
//
//
//        }
//    }

    @Command(desc = "Add or remove a role from a set of members on discord")
    @RolePermission(Roles.ADMIN)
    public String mask(@Me Member me, @Me GuildDB db, Set<Member> members, Role role, boolean value, @Arg("If the role should be added or removed from all other members\n" +
            "If `value` is true, the role will be removed, else added") @Switch("r") boolean toggleMaskFromOthers) {
        List<Role> myRoles = me.getRoles();
        List<String> response = new ArrayList<>();
        for (Member member : members) {
            User user = member.getUser();
            List<Role> roles = member.getRoles();
            if (value && roles.contains(role)) {
                response.add(user.getName() + " already has the role: `" + role + "`");
                continue;
            } else if (!value && !roles.contains(role)) {
                response.add(user.getName() + ": does not have the role: `" + role + "`");
                continue;
            }
            if (value) {
                RateLimitUtil.queue(db.getGuild().addRoleToMember(member, role));
                response.add(user.getName() + ": Added role to member");
            } else {
                RateLimitUtil.queue(db.getGuild().removeRoleFromMember(member, role));
                response.add(user.getName() + ": Removed role from member");
            }
        }
        if (toggleMaskFromOthers) {
            for (Member member : db.getGuild().getMembers()) {
                if (members.contains(member)) continue;
                List<Role> memberRoles = member.getRoles();
                if (value) {
                    if (memberRoles.contains(role)) {
                        RateLimitUtil.queue(db.getGuild().removeRoleFromMember(member, role));
                        response.add(member.getUser().getName() + ": Removed role from member");
                    }
                } else {
                    if (!memberRoles.contains(role)) {
                        RateLimitUtil.queue(db.getGuild().addRoleToMember(member, role));
                        response.add(member.getUser().getName() + ": Added role to member");
                    }
                }
            }
        }
        return StringMan.join(response, "\n").trim();
    }

    @Command
    @RolePermission(value = Roles.ADMIN, root = true)
    public String dm(@Me User author, DBNation nation, String message) {
        User user = nation.getUser();
        if (user == null) return "No user found for " + nation.getNation();

        user.openPrivateChannel().queue(new Consumer<PrivateChannel>() {
            @Override
            public void accept(PrivateChannel channel) {
                RateLimitUtil.queue(channel.sendMessage(author.getAsMention() + " said: " + message + "\n\n(no reply)"));
            }
        });
        return "Done!";
    }

    @Command(desc = "Edit an attribute of your in-game alliance\n" +
            "Attributes match the in-game fields and are case sensitive\n" +
            "Run the command without arguments to get a list of attributes"
    )
    @RolePermission(Roles.ADMIN)
    public String editAlliance(@Me GuildDB db, @Me User author, DBAlliance alliance, @Default String attribute, @Default @TextArea String value) throws Exception {
        if (!db.isAllianceId(alliance.getAlliance_id())) {
            return "Alliance: " + alliance.getAlliance_id() + " not registered to guild " + db.getGuild() + ". See: " + CM.settings.info.cmd.toSlashMention() + " with key: " + GuildKey.ALLIANCE_ID.name();
        }

        Rank rank = attribute != null && attribute.toLowerCase().contains("bank") ? Rank.HEIR : Rank.OFFICER;
        Auth auth = alliance.getAuth(AlliancePermission.EDIT_ALLIANCE_INFO);
        if (auth == null) return "No authorization set";

        StringBuilder response = new StringBuilder();

        EditAllianceTask task = new EditAllianceTask(auth.getNation(), new Consumer<Map<String, String>>() {
            @Override
            public void accept(Map<String, String> post) {
                if (attribute == null || value == null) {
                    throw new IllegalArgumentException("Currently set: " + StringMan.getString(post));
                }
                if (post.containsKey(attribute.toLowerCase()) || attribute.equals("acceptmem")) {
                    post.put(attribute.toLowerCase(), value);
                    response.append("Attribute has been set.");
                } else {
                    response.append("Invalid key: " + attribute + ". Options: " + StringMan.getString(post));
                }
            }
        });
        task.call();
        return response.toString();
    }

    @Command(desc = "Remove a discord role the bot uses for command permissions")
    @RolePermission(Roles.ADMIN)
    public String unregisterRole(@Me User user, @Me Guild guild, @Me GuildDB db, Roles locutusRole, @Arg("Only remove a role mapping for this alliance") @Default DBAlliance alliance) {
        return aliasRole(user, guild, db, locutusRole, null, alliance, true);
    }

    @Command()
    @RolePermission(value = Roles.ADMIN, root = true)
    public String listGuildPerms() {
        StringBuilder response = new StringBuilder();
        for (Map.Entry<Long, GuildDB> entry : Locutus.imp().getGuildDatabases().entrySet()) {
            Long id = entry.getKey();
            GuildDB db = entry.getValue();
            Map<Class, Integer> perms = db.getPermissions();
            if (perms.isEmpty()) continue;

            response.append("**" + id + "**:\n");
            for (Map.Entry<Class, Integer> permEntry : perms.entrySet()) {
                response.append("- " + permEntry.getKey().getSimpleName() + "=" + permEntry.getValue() + "\n");
            }
            response.append("\n");
        }

        if (response.length() == 0) return "No permissions are set";
        return response.toString();
    }

    private static String mappingToString(Map<Long, Role> mapping) {
        List<String> response = new ArrayList<>();
        for (Map.Entry<Long, Role> entry : mapping.entrySet()) {
            Role role = entry.getValue();
            long aaId = entry.getKey();
            if (aaId == 0) {
                response.add("*:" + role.getName());
            } else {
                response.add(aaId + ": " + role.getName());
            }
        }
        if (response.isEmpty()) return "";
        return "- " + StringMan.join(response, "\n- ");
    }

    @Command(desc = "Set the discord roles the bot uses for command permissions")
    @RolePermission(Roles.ADMIN)
    public static String aliasRole(@Me User author, @Me Guild guild, @Me GuildDB db, @Default Roles locutusRole, @Default() Role discordRole, @Arg("If the role mapping is only for a specific alliance (WIP)") @Default() DBAlliance alliance, @Arg("Remove the existing mapping instead of setting it") @Switch("r") boolean removeRole) {
        if (alliance != null && !db.isAllianceId(alliance.getAlliance_id())) {
            return "Alliance: " + alliance.getAlliance_id() + " not registered to guild " + db.getGuild() + ". See: " + CM.settings.info.cmd.toSlashMention() + " with key: " + GuildKey.ALLIANCE_ID.name();
        }
        StringBuilder response = new StringBuilder();
        boolean showGlobalMappingInfo = false;

        if (locutusRole == null) {
            if (discordRole != null) {
                List<String> rolesListStr = new ArrayList<>();
                Map<Roles, Map<Long, Long>> allMapping = db.getMappingRaw();
                if (removeRole) {
                    // remove all roles registered to it
                    for (Map.Entry<Roles, Map<Long, Long>> locEntry : allMapping.entrySet()) {
                        for (Map.Entry<Long, Long> discEntry : locEntry.getValue().entrySet()) {
                            long aaId =discEntry.getKey();
                            if (alliance != null && aaId != alliance.getAlliance_id()) continue;
                            if (discEntry.getValue() == discordRole.getIdLong()) {
                                String aaStr =  aaId == 0 ? "*" : PnwUtil.getName(aaId, true);
                                rolesListStr.add("Removed " + locEntry.getKey().name() + " from " + discordRole.getName() + " (AA:" + aaId + ")");
                                db.deleteRole(locEntry.getKey(), aaId);
                            }
                        }
                    }
                    if (rolesListStr.isEmpty()) {
                        return "No aliases found for " + discordRole.getName();
                    }
                    response.append("Removed aliases for " + discordRole.getName() + ":\n- ");
                    response.append(StringMan.join(rolesListStr, "\n- "));
                    response.append("\n\nUse " + CM.role.setAlias.cmd.toSlashMention() + " to view current role aliases");
                    return response.toString();
                }

                for (Map.Entry<Roles, Map<Long, Long>> locEntry : allMapping.entrySet()) {
                    Map<Long, Long> aaToRoleMap = locEntry.getValue();
                    showGlobalMappingInfo |= aaToRoleMap.size() > 1 && aaToRoleMap.containsKey(0L);
                    for (Map.Entry<Long, Long> discEntry : aaToRoleMap.entrySet()) {
                        if (discEntry.getValue() == discordRole.getIdLong()) {
                            Roles role = locEntry.getKey();
                            long aaId = discEntry.getKey();
                            if (aaId == 0) {
                                rolesListStr.add("*:" + role.name());
                            } else {
                                rolesListStr.add(DBAlliance.getOrCreate((int) aaId).getName() + "/" + aaId + ":" + role.name());
                            }
                        }
                    }
                }
                if (rolesListStr.isEmpty()) {
                    return "No aliases found for " + discordRole.getName();
                }
                response.append("Aliases for " + discordRole.getName() + ":\n- ");
                response.append(StringMan.join(rolesListStr, "\n- "));
                if (showGlobalMappingInfo) response.append("\n`note: " + Messages.GLOBAL_ROLE_MAPPING_INFO + "`");
                return response.toString();
            }

            List<String> registeredRoles = new ArrayList<>();
            List<String> unregisteredRoles = new ArrayList<>();
            for (Roles role : Roles.values) {
                Map<Long, Role> mapping = db.getAccountMapping(role);
                if (mapping != null && !mapping.isEmpty()) {
                    registeredRoles.add(role + ":\n" + mappingToString(mapping));
                    continue;
                }
                if (role.getKey() != null && db.getOrNull(role.getKey()) == null) continue;
                unregisteredRoles.add(role + ":\n" + mappingToString(mapping));
            }

            if (!registeredRoles.isEmpty()) {
                response.append("**Registered Roles**:\n" + StringMan.join(registeredRoles, "\n") + "\n");
            }
            if (!unregisteredRoles.isEmpty()) {
                response.append("**Unregistered Roles**:\n" + StringMan.join(unregisteredRoles, "\n") + "\n");
            }
            response.append("Provide a value for `locutusRole` for specific role information.\n" +
                    "Provide a value for `discordRole` to register a role.\n");

            return response.toString();
        }

        if (discordRole == null) {
            if (removeRole) {
                Role alias = db.getRole(locutusRole, alliance != null ? (long) alliance.getAlliance_id() : null);
                if (alias == null) {
                    String allianceStr = alliance != null ? alliance.getName() + "/" + alliance.getAlliance_id() : "*";
                    return "No role alias found for " + allianceStr + ":" + locutusRole.name();
                }
                if (alliance != null) {
                    db.deleteRole(locutusRole, alliance.getAlliance_id());
                } else {
                    db.deleteRole(locutusRole);
                }
                response.append("Removed role alias for " + locutusRole.name() + ":\n");
            }
            Map<Long, Role> mapping = db.getAccountMapping(locutusRole);
            response.append("**" + locutusRole.name() + "**:\n");
            response.append("`" + locutusRole.getDesc() + "`\n");
            if (mapping.isEmpty()) {
                response.append("No value set.");
            } else {
                response.append("```\n" + mappingToString(mapping) + "```\n");
            }
            response.append("Provide a value for `discordRole` to register a role.\n");
            if (mapping.size() > 1 && mapping.containsKey(0L)) {
                response.append("`note: " + Messages.GLOBAL_ROLE_MAPPING_INFO + "`");
            }
            return response.toString().trim();
        }

        if (removeRole) {
            throw new IllegalArgumentException("Cannot remove role alias with this command. Use " + CM.role.unregister.cmd.create(locutusRole.name(), null).toSlashCommand() + "");
        }


        int aaId = alliance == null ? 0 : alliance.getAlliance_id();
        String allianceStr = alliance == null ? "*" : alliance.getName() + "/" + aaId;
        db.addRole(locutusRole, discordRole, aaId);
        return "Added role alias: " + locutusRole.name().toLowerCase() + " to " + discordRole.getName() + " for alliance " + allianceStr + "\n" +
                "To unregister, use " + CM.role.unregister.cmd.create(locutusRole.name(), null).toSlashCommand() + "";
    }

    public String printApiStats(PoliticsAndWarV2 api) {
        Map<String, AtomicInteger> methodUsage = api.getMethodUsageStats();
        List<Map.Entry<String, AtomicInteger>> sorted = new ArrayList<>(methodUsage.entrySet());
        sorted.sort((o1, o2) -> Integer.compare(o2.getValue().intValue(), o1.getValue().intValue()));

        StringBuilder response = new StringBuilder("API usage by method:\n");
        for (Map.Entry<String, AtomicInteger> entry : sorted) {
            response.append("- " + entry.getKey() + ": " + entry.getValue().intValue() + "\n");
        }
        response.append("\n\n------------------------\n\nApi stacktraces (>100 calls)");
        for (Map.Entry<String, AtomicInteger> entry : sorted) {
            if (entry.getValue().intValue() < 100) break;

            response.append("\n\n**" + entry.getKey() + "** |||| ==== |||| :\n");
            Set<List<StackTraceElement>> traces = api.getMethodToStacktrace().get(entry.getKey());
            for (List<StackTraceElement> trace : traces) {
                response.append("\nTrace:\n" + StringMan.stacktraceToString(trace.toArray(new StackTraceElement[0])));
            }

        }
        return "```" + response.toString() + "```";
    }

    @Command()
    @RolePermission(value = Roles.ADMIN, root = true)
    public String rootApiUsageStats() {
        PoliticsAndWarV2 api = Locutus.imp().getRootPnwApiV2();
        System.out.println(printApiStats(api));
        return "Done! (see console)";
    }

    @Command()
    @RolePermission(value = Roles.ADMIN, root = true)
    public String apiUsageStats(@Me DBAlliance alliance, boolean cached) {
        ApiKeyPool keys = alliance.getApiKeys();
        PoliticsAndWarV2 api = new PoliticsAndWarV2(keys, Settings.INSTANCE.TEST, cached);
        System.out.println(printApiStats(api));
        return "Done! (see console)";
    }

    @Command(desc = "Check if current api keys are valid")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String importGuildKeys() {
        StringBuilder response = new StringBuilder();
        for (GuildDB db : Locutus.imp().getGuildDatabases().values()) {
            List<String> keys = db.getOrNull(GuildKey.API_KEY);
            if (keys == null) return "No keys found for guild " + db.getGuild().getName() + " (" + db.getGuild().getId() + ")";
            for (String key : keys) {
                try {
                    ApiKeyDetails stats = new PoliticsAndWarV3(ApiKeyPool.builder().addKeyUnsafe(key).build()).getApiKeyStats();
                    Locutus.imp().getDiscordDB().addApiKey(stats.getNation().getId(), key);

                    response.append(key + ": success" + "\n");
                } catch (Throwable e) {
                    response.append(key + ": " + e.getMessage() + "\n");
                }
            }
        }
        return "Done!";
    }

    @Command(desc = "Check if current api keys are valid")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String validateAPIKeys() {
        Set<String> keys = Locutus.imp().getPnwApiV2().getApiKeyUsageStats().keySet();
        Map<String, String> failed = new LinkedHashMap<>();
        Map<String, ApiKeyDetails> success = new LinkedHashMap<>();
        for (String key : keys) {
            try {
                ApiKeyDetails stats = new PoliticsAndWarV3(ApiKeyPool.builder().addKeyUnsafe(key).build()).getApiKeyStats();
                if (stats != null && stats.getNation() != null && stats.getNation().getId() != null) {
                    success.put(key, stats);
                } else {
                    failed.put(key, "Error: null (1)");
                }
            } catch (Throwable e) {
                failed.put(key, e.getMessage());
            }
        }
        StringBuilder response = new StringBuilder();
        for (Map.Entry<String, String> e : failed.entrySet()) {
            response.append(e.getKey() + ": " + e.getValue() + "\n");
        }
        for (Map.Entry<String, ApiKeyDetails> e : success.entrySet()) {
            String key = e.getKey();
            ApiKeyDetails record = e.getValue();
            int natId = record.getNation().getId();
            DBNation nation = DBNation.getById(natId);
            if (nation != null) {
                response.append(key + ": " + record.toString() + " | " + nation.getNation() + " | " + nation.getAllianceName() + " | " + nation.getPosition() + "\n");
            } else {
                response.append(e.getKey() + ": " + e.getValue() + "\n");
            }
        }
        System.out.println(response); // keep
        return "Done (see console)";
    }

    @Command(desc = "Test your alliance recruitment message by sending it to the bot creator's nation")
    @RolePermission(value = Roles.ADMIN)
    public String testRecruitMessage(@Me GuildDB db) throws IOException {
        JsonObject response = db.sendRecruitMessage(Locutus.imp().getNationDB().getNation(Settings.INSTANCE.NATION_ID));
        return response.toString();
    }

    @Command(desc = "Purge a category's channels older than the time specified")
    @RolePermission(value = Roles.ADMIN)
    public String debugPurgeChannels(Category category, @Range(min=60) @Timestamp long cutoff) {
        long now = System.currentTimeMillis();
        int deleted = 0;
        for (GuildMessageChannel GuildMessageChannel : category.getTextChannels()) {
            if (GuildMessageChannel.getLatestMessageIdLong() > 0) {
                long message = GuildMessageChannel.getLatestMessageIdLong();
                try {
                    long created = net.dv8tion.jda.api.utils.TimeUtil.getTimeCreated(message).toEpochSecond() * 1000L;
                    if (created > cutoff) {
                        continue;
                    }
                } catch (Throwable ignore) {}
            }
            RateLimitUtil.queue(GuildMessageChannel.delete());
            deleted++;
            continue;
        }
        return "Deleted " + deleted + " channels";
    }

    @Command
    @RolePermission(value = Roles.ADMIN, root = true)
    public String listExpiredGuilds(boolean checkMessages) {
        StringBuilder response = new StringBuilder();
        for (GuildDB db : Locutus.imp().getGuildDatabases().values()) {
            Guild guild = db.getGuild();
            Member owner = db.getGuild().getOwner();
            DBNation nation = DiscordUtil.getNation(owner.getUser());

            Set<Integer> aaIds = db.getAllianceIds();

            if (nation != null && nation.getActive_m() > 30000) {
                response.append(guild + "/" + StringMan.getString(aaIds) + ": owner (nation:" + nation.getNation_id() + ") is inactive " + TimeUtil.secToTime(TimeUnit.MINUTES, nation.getActive_m()) + "\n");
                continue;
            }
            // In an alliance with inactive leadership (1 month)
            if (!aaIds.isEmpty() && !db.isValidAlliance()) {
                response.append(guild + "/" + StringMan.getString(aaIds) + ": alliance is invalid (nation:" + (nation == null ? "" : nation.getNation_id() + ")\n"));
                continue;
            }

            if (aaIds.isEmpty() && nation == null && checkMessages) {
                long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30);
                boolean error = false;
                long last = 0;

                outer:
                for (GuildMessageChannel channel : guild.getTextChannels()) {
                    if (channel.getLatestMessageIdLong() == 0) continue;
                    try {
                        long latestSnowflake = channel.getLatestMessageIdLong();
                        long latestMs = net.dv8tion.jda.api.utils.TimeUtil.getTimeCreated(latestSnowflake).toEpochSecond() * 1000L;
                        if (latestMs > cutoff) {
                            List<Message> messages = RateLimitUtil.complete(channel.getHistory().retrievePast(5));
                            for (Message message : messages) {
                                if (message.getAuthor().isSystem() || message.getAuthor().isBot() || guild.getMember(message.getAuthor()) == null) {
                                    continue;
                                }
                                last = Math.max(last, message.getTimeCreated().toEpochSecond() * 1000L);
                                if (last > cutoff) {
                                    break outer;
                                }
                            }
                        }
                    } catch (Throwable e) {
                        error = true;
                    }
                }
                if (last < cutoff) {
                    response.append(guild + ": has no recent messages\n");
                    continue;
                }
            }
        }
        return response.toString();
    }

    @Command(desc = "Remove deleted alliances or guilds from a coalition\n" +
            "Note: Do not remove deleted offshores or banks if you want to use their previous transactions in deposit calculations")
    @RolePermission(value = Roles.ADMIN)
    public String removeInvalidOffshoring(@Me GuildDB db, Coalition coalition) {
        Set<Long> toRemove = new HashSet<>();
        for (long id : db.getCoalitionRaw(coalition)) {
            GuildDB otherDb;
            if (id > Integer.MAX_VALUE) {
                otherDb = Locutus.imp().getGuildDB(id);
            } else {
                otherDb = Locutus.imp().getGuildDBByAA((int) id);
            }
            if (otherDb == null) {
                toRemove.add(id);
            }
        }
        System.out.println(StringMan.getString(toRemove));
        for (long id : toRemove) {
            db.removeCoalition(id,coalition);
        }
        return "Removed `" + StringMan.join(toRemove, ",") + "` from " + Coalition.OFFSHORING;

    }

    @Command
    @RolePermission(value = Roles.ADMIN, root = true)
    public String leaveServer(long guildId) {
        GuildDB db = Locutus.imp().getGuildDB(guildId);
        if (db == null) return "Server not found " + guildId;
        Guild guild = db.getGuild();
        RateLimitUtil.queue(guild.leave());
        return "Leaving " + guild.getName();
    }

    @Command
    @RolePermission(value = Roles.ADMIN, root = true)
    public String listExpiredOffshores() {
        OffshoreInstance offshore = Locutus.imp().getRootBank();
        GuildDB db = offshore.getGuildDB();
        Set<Long> coalitions = db.getCoalitionRaw(Coalition.OFFSHORING);

        Map<Long, List<String>> notices = new HashMap<>();
        Set<Long> printDeposits = new HashSet<>();
        Set<Long> hasError = new HashSet<>();

        for (Long id : coalitions) {
            List<String> notice = notices.computeIfAbsent(id, f -> new ArrayList<>());
            GuildDB otherDb = (id > Integer.MAX_VALUE) ? Locutus.imp().getGuildDB(id) : Locutus.imp().getGuildDBByAA(id.intValue());
            if (otherDb == null) {
                notice.add("- No database found");
                hasError.add(id);
                continue;
            }

            if (id > Integer.MAX_VALUE) {
                notice.add(" **CORPORATION**");
            } else {
                DBAlliance alliance = DBAlliance.get(id.intValue());
                if (alliance == null || !alliance.exists()) {
                    notice.add("\n- AA does not exist: " + id);
                    printDeposits.add(id);
                } else {
                   notice.add(" **ALLIANCE**");
                }
            }

            notice.add("\n- Guild: `" + otherDb.getGuild().toString() + "`");
            Set<Integer> aaIds = otherDb.getAllianceIds();
            if (!aaIds.isEmpty()) {
                List<String> aaNames = new ArrayList<>();
                for (int aaId : aaIds) {
                    DBAlliance aa = DBAlliance.get(aaId);
                    if (aa == null) {
                        aaNames.add(aaId + "");
                    } else {
                        aaNames.add(aa.getName() + "/" + aaId);
                    }
                }
                notice.add("\n- Alliance: `" + StringMan.getString(aaNames) + "`");
            }

            List<Transaction2> transactions;
            if (!aaIds.isEmpty()) {
                transactions = offshore.getTransactionsAA(aaIds, false);
            } else {
                transactions = offshore.getTransactionsGuild(id, false);
            }
            transactions.removeIf(f -> f.tx_datetime > System.currentTimeMillis());
            transactions.removeIf(f -> f.receiver_id == f.banker_nation && f.tx_id > 0);
            long latestTx = transactions.isEmpty() ? 0 : transactions.stream().mapToLong(f -> f.tx_datetime).max().getAsLong();
            if (latestTx < System.currentTimeMillis() - TimeUnit.DAYS.toMillis(14)) {
                String timeStr = TimeUtil.secToTime(TimeUnit.MILLISECONDS, System.currentTimeMillis() - latestTx);
                notice.add("\n- Latest Transfer: `" + timeStr + "`");
                hasError.add(id);
                printDeposits.add(id);
            }

            Member owner = otherDb.getGuild().getOwner();

            if (!aaIds.isEmpty()) {
                AllianceList alliance = new AllianceList(aaIds);
                Set<DBNation> nations = new HashSet<>(alliance.getNations());
                nations.removeIf(f -> f.getPosition() < Rank.LEADER.id);
                long minActiveM = Long.MAX_VALUE;
                DBNation latestNation = null;
                for (DBNation nation : nations) {
                    if (nation.active_m() < minActiveM) {
                        minActiveM = nation.active_m();
                        latestNation = nation;
                    }
                }
                if (minActiveM > 10000) {
                    notice.add("\n- Inactive Leadership: `" + (latestNation != null ? "<" + latestNation.getUrl() + ">" : null) + " | " + TimeUtil.secToTime(TimeUnit.MINUTES, minActiveM) + "`");
                    printDeposits.add(id);
                }
            }

            DBNation nation = owner != null ? DiscordUtil.getNation(owner.getIdLong()) : null;
            if (nation == null) {
                notice.add("\n- Owner is Unregistered");
                printDeposits.add(id);
            } else if (nation.getActive_m() > 10000) {
                notice.add("\n- Owner is inactive: <@" + owner.getIdLong() + "> | <" + nation.getNationUrl() + "> | `" + TimeUtil.secToTime(TimeUnit.MINUTES, nation.active_m()) + "`");
                printDeposits.add(id);
            }
        }

        StringBuilder response = new StringBuilder();
        for (long id : coalitions) {
            if (!hasError.contains(id)) continue;
            List<String> notes = notices.get(id);
            response.append("\n\n**").append(id).append("**");
            for (String note : notes) {
                response.append(note);
            }
            if (printDeposits.contains(id)) {
                Map<ResourceType, Double> depo;
                if (id > Integer.MAX_VALUE) {
                    depo = offshore.getDeposits(id, false);
                } else {
                    depo = offshore.getDeposits((int) id, false);
                }
                response.append("\n- Deposits: `" + PnwUtil.resourcesToString(depo) + "` worth: `$" + MathMan.format(PnwUtil.convertedTotal(depo)) + "`");
            }
            response.append("\n\n");
        }

        return response.toString();
    }

    @Command()
    @RolePermission(value = Roles.ADMIN, root = true)
    public String displayGuildPerms() {
        StringBuilder response = new StringBuilder();
        for (Map.Entry<Long, GuildDB> longGuildDBEntry : Locutus.imp().getGuildDatabases().entrySet()) {
            GuildDB db = longGuildDBEntry.getValue();
            Map<Class, Integer> perms = new HashMap<>(db.getPermissions());
            perms.entrySet().removeIf(f -> f.getValue() <= 0);
            if (perms.isEmpty()) continue;

            response.append(db.getName() + " | " + db.getIdLong() + "\n");
            for (Map.Entry<Class, Integer> entry : perms.entrySet()) {
                response.append("- " + entry.getKey() + "=" + entry.getValue() + "\n");
            }
            response.append("\n");

        }
        return response.toString();
    }

    @Command()
    @RolePermission(value = Roles.ADMIN, root = true)
    public String listGuildOwners() {
        ArrayList<GuildDB> guilds = new ArrayList<>(Locutus.imp().getGuildDatabases().values());
        guilds.sort(new Comparator<GuildDB>() {
            @Override
            public int compare(GuildDB o1, GuildDB o2) {
                return Long.compare(o1.getGuild().getIdLong(), o2.getGuild().getIdLong());
            }
        });
        StringBuilder result = new StringBuilder();
        for (GuildDB value : guilds) {
            Guild guild = value.getGuild();
            User owner = Locutus.imp().getDiscordApi().getUserById(guild.getOwnerIdLong());
            result.append(guild.getIdLong() + " | " + guild.getName() + " | " + owner.getName()).append("\n");
        }
        return result.toString();
    }

    @Command()
    @RolePermission(value = Roles.ADMIN, root = true)
    public String syncInfraLand() throws IOException, ParseException {
        List<Event> events = new ArrayList<>();
        Locutus.imp().getNationDB().updateCitiesV2(events::add);
        if (events.size() > 0) {
            Locutus.imp().getExecutor().submit(() -> {
                for (Event event : events) event.post();;
            });
        }
        return "Updated city infra land. " + events.size() + " changes detected";
    }

    @Command()
    @RolePermission(value = Roles.ADMIN, root = true)
    public String syncMetrics(@Default("80") int topX) throws IOException, ParseException {
        AllianceMetric.update(topX);
        return "Updated metrics for top " + topX + " alliances";
    }

    @Command()
    @RolePermission(value = Roles.ADMIN, root = true)
    public String syncCities(NationDB db) throws IOException, ParseException {
        StringBuilder result = new StringBuilder();
        result.append("Dirty cities: " + db.getDirtyCities().size() + "\n");

        List<Event> events = new ArrayList<>();
        db.updateAllCities(events::add);
        if (events.size() > 0) {
            Locutus.imp().getExecutor().submit(() -> {
                for (Event event : events) event.post();;
            });
        }
        result.append("events: " + events.size() + "\n");
        result.append("Dirty cities: " + db.getDirtyCities().size() + "\n");
        result.append("Updated all cities. " + events.size() + " changes detected");
        return result.toString();
    }

    @Command()
    @RolePermission(value = Roles.ADMIN, root = true)
    public String syncCitiesTest(NationDB db) throws IOException, ParseException {
        StringBuilder result = new StringBuilder();
        result.append("Dirty cities: " + db.getDirtyCities().size() + "\n");

        List<Event> events = new ArrayList<>();
        db.updateCitiesV2(events::add);
        if (events.size() > 0) {
            Locutus.imp().getExecutor().submit(() -> {
                for (Event event : events) event.post();;
            });
        }
        result.append("events: " + events.size() + "\n");
        result.append("Dirty cities: " + db.getDirtyCities().size() + "\n");
        result.append("Updated all cities. " + events.size() + " changes detected");
        return result.toString();
    }

    @Command()
    @RolePermission(value = Roles.ADMIN, root = true)
    public String syncCitiesTest2(NationDB db, @Me DBNation me) throws IOException, ParseException {
        Map<Integer, JavaCity> cities = me.getCityMap(true);
        StringBuilder result = new StringBuilder();
        result.append("Dirty cities: " + db.getDirtyCities().size() + "\n");

        List<Event> events = new ArrayList<>();
        db.updateCitiesV2(events::add);
        if (events.size() > 0) {
            Locutus.imp().getExecutor().submit(() -> {
                for (Event event : events) event.post();;
            });
        }
        result.append("events: " + events.size() + "\n");
        result.append("Dirty cities: " + db.getDirtyCities().size() + "\n");
        result.append("Updated all cities. " + events.size() + " changes detected");
        return result.toString();
    }


    @Command()
    @RolePermission(value = Roles.ADMIN, root = true)
    public String syncNations(NationDB db, @Default Set<DBNation> nations) throws IOException, ParseException {
        List<Event> events = new ArrayList<>();
        Set<Integer> updatedIds;
        if (nations != null && !nations.isEmpty()) {
            updatedIds = db.updateNations(nations.stream().map(DBNation::getId).toList(), events::add);
        } else {
            updatedIds = db.updateAllNations(events::add);
        }
        if (events.size() > 0) {
            Locutus.imp().getExecutor().submit(() -> {
                for (Event event : events) event.post();;
            });
        }
        return "Updated " + updatedIds.size() + " nations. " + events.size() + " changes detected";
    }

    @Command()
    @RolePermission(value = Roles.ADMIN, root = true)
    public String syncBanks(@Me GuildDB db, @Me IMessageIO channel, @Default DBAlliance alliance, @Default @Timestamp Long timestamp) throws IOException, ParseException {
        if (alliance != null) {
            db = alliance.getGuildDB();
            if (db == null) throw new IllegalArgumentException("No guild found for AA:" + alliance);
        }
        channel.send("Syncing banks for " + db.getGuild() + "...");
        OffshoreInstance bank = alliance.getBank();
        bank.sync(timestamp, false);

        Locutus.imp().getBankDB().updateBankRecs(Event::post);
        return "Done!";
    }

    @Command()
    @RolePermission(value = Roles.ADMIN, root = true)
    public String syncBlockades(@Me IMessageIO channel) throws IOException, ParseException {
        NationUpdateProcessor.updateBlockades();;
        return "Done!";
    }

    @Command(aliases = {"syncforum", "syncforums"})
    @RolePermission(value = Roles.ADMIN, root = true)
    public String syncForum(@Me IMessageIO channel) throws IOException, ParseException {
        Locutus.imp().getForumDb().update();
        return "Done!";
    }

    @Command(desc = "List users in the guild that have provided login credentials to locutus")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String listAuthenticated(@Me GuildDB db) {
        List<Member> members = db.getGuild().getMembers();

        Map<DBNation, Rank> registered = new LinkedHashMap<>();
        Map<DBNation, String> errors = new HashMap<>();

        Set<Integer> alliances = db.getAllianceIds(false);
        for (Member member : members) {
            DBNation nation = DiscordUtil.getNation(member.getUser());
            if (nation != null && (alliances.isEmpty() || alliances.contains(nation.getAlliance_id()))) {
                try {
                    Auth auth = nation.getAuth(true);
                    registered.put(nation, Rank.byId(nation.getPosition()));
                    try {
                        ApiKeyPool.ApiKey key = auth.fetchApiKey();
                    } catch (Throwable e) {
                        errors.put(nation, e.getMessage());
                    }
                } catch (IllegalArgumentException ignore) {}
            }
        }

        if (registered.isEmpty()) {
            return "No registered users";
        }
        StringBuilder result = new StringBuilder();
        for (Map.Entry<DBNation, Rank> entry : registered.entrySet()) {
            result.append(entry.getKey().getNation() + "- " + entry.getValue());
            String error = errors.get(entry.getValue());
            if (error != null) {
                result.append(": Could not validate: " + error);
            }
            result.append("\n");
        }
        return result.toString().trim();
    }

    @Command
    @RolePermission(value = Roles.ADMIN, root = true)
    public String syncLootFromAttacks() {
        int found = 0;
        int added = 0;
        List<DBAttack> attacks = Locutus.imp().getWarDb().getAttacks(0, AttackType.A_LOOT);
        for (DBAttack attack : attacks) {
            if (attack.getLooted() > 0) {
                LootEntry existing = Locutus.imp().getNationDB().getAllianceLoot(attack.getLooted());
                if (existing != null && existing.getDate() < attack.getDate()) {
                    Double pct = attack.getLootPercent();
                    if (pct == 0) pct = 0.01;
                    double factor = 1/pct;

                    double[] lootCopy = attack.loot == null ? ResourceType.getBuffer() : attack.loot.clone();
                    for (int i = 0; i < lootCopy.length; i++) {
                        lootCopy[i] = (lootCopy[i] * factor) - lootCopy[i];
                    }

                    Locutus.imp().getNationDB().saveAllianceLoot(attack.getLooted(), attack.getDate(), lootCopy, NationLootType.WAR_LOSS);
                }
            }
        }
        return "Done!";

    }
}