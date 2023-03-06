package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import com.google.gson.JsonObject;
import com.politicsandwar.graphql.model.ApiKeyDetails;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.core.ApiKeyPool;
import link.locutus.discord.apiv1.domains.subdomains.DBAttack;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv2.PoliticsAndWarV2;
import link.locutus.discord.apiv3.PoliticsAndWarV3;
import link.locutus.discord.apiv3.enums.AlliancePermission;
import link.locutus.discord.apiv3.enums.NationLootType;
import link.locutus.discord.commands.manager.v2.binding.annotation.Timestamp;
import link.locutus.discord.commands.manager.v2.binding.annotation.*;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.HasApi;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.IsAuthenticated;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.NationDB;
import link.locutus.discord.db.entities.*;
import link.locutus.discord.event.Event;
import link.locutus.discord.pnw.NationList;
import link.locutus.discord.pnw.PNWUser;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.*;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.offshore.Auth;
import link.locutus.discord.util.offshore.OffshoreInstance;
import link.locutus.discord.util.task.EditAllianceTask;
import link.locutus.discord.util.update.NationUpdateProcessor;
import net.dv8tion.jda.api.entities.*;
import org.json.JSONObject;

import java.io.IOException;
import java.text.ParseException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class AdminCommands {

    @Command
    @RolePermission(value = Roles.ADMIN, root = true)
    public String syncTreasures() {
        Locutus.imp().getNationDB().updateTreasures(Event::post);
        return "Done!";
    }

    @Command(desc = "Pull registered nation from locutus.")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String syncDiscordWithLocutus(@Default String url) throws IOException {
        if (url == null) {
            url = "https://locutus.link/discordids";
        }
        int count = 0;
        // read string from url
        String csvTabSeparated = FileUtil.readStringFromURL(url);
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
        Map<GuildDB, List<GuildDB.Key>> toUnset = new LinkedHashMap<>();

        for (GuildDB db : Locutus.imp().getGuildDatabases().values()) {
            if (force) {
                List<GuildDB.Key> keys = db.listInaccessibleChannelKeys();
                if (!keys.isEmpty()) {
                    toUnset.put(db, keys);
                }
            } else {
                db.unsetInaccessibleChannels();
            }
        }

        if (toUnset.isEmpty()) {
            return "No keys to unset.";
        }
        StringBuilder response = new StringBuilder();
        for (Map.Entry<GuildDB, List<GuildDB.Key>> entry : toUnset.entrySet()) {
            response.append(entry.getKey().getGuild().toString()).append(":\n");
            List<GuildDB.Key> keys = entry.getValue();
            response.append(" - ").append(StringMan.join(keys, "\n - "));
            response.append("\n");
        }
        String footer = "Re-run the command with `-f` to confirm.";
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
        if (!db.isValidAlliance()) return "Not in an alliance.";
        Collection<DBNation> nations = db.getAlliance().getNations(true, 10000, true);
        for (DBNation nation : nations) {
            db.getHandler().onRefer(nation);
        }
        return "Done!";
    }

    @Command
    @RolePermission(any = true, value = {Roles.INTERNAL_AFFAIRS, Roles.MILCOM, Roles.ADMIN, Roles.FOREIGN_AFFAIRS, Roles.ECON})
    public String archiveAnnouncement(@Me GuildDB db, int announcementId, @Default Boolean archive) {
        if (archive == null) archive = true;
        db.setAnnouncementActive(announcementId, !archive);
        return (archive ? "Archived" : "Unarchived") + " announcement with id: #" + announcementId;
    }

    @Command(desc = "Send an announcement to multiple nations, with random variations for opsec.")
    @RolePermission(Roles.ADMIN)
    @HasApi
    public String announce(@Me GuildDB db, @Me Guild guild, @Me JSONObject command, @Me IMessageIO currentChannel, @Me User author, NationList nationList, @Arg("The subject used if DM fails") String subject, String announcement, String replacements, @Switch("v") @Default("0") Integer requiredVariation, @Switch("r") @Default("0") Integer requiredDepth, @Switch("s") Long seed, @Switch("m") boolean sendMail, @Switch("d") boolean sendDM, @Switch("f") boolean force) throws IOException {
        ApiKeyPool keys = db.getMailKey();
        if (keys == null)
            throw new IllegalArgumentException("No API_KEY set, please use " + CM.credentials.addApiKey.cmd.toSlashMention() + "");
        Integer aaId = db.getOrNull(GuildDB.Key.ALLIANCE_ID);

        List<String> errors = new ArrayList<>();
        Collection<DBNation> nations = nationList.getNations();
        for (DBNation nation : nations) {
            User user = nation.getUser();
            if (user == null) {
                errors.add("Cannot find user for `" + nation.getNation() + "`");
            } else if (guild.getMember(user) == null) {
                errors.add("Cannot find member in guild for `" + nation.getNation() + "` | `" + user.getName() + "`");
            } else {
                continue;
            }
            if (aaId != null && nation.getAlliance_id() != aaId) {
                throw new IllegalArgumentException("Cannot send to nation not in alliance: " + nation.getNation() + " | " + user);
            }
            if (!force) {
                if (nation.getActive_m() > 20000)
                    return "The " + nations.size() + " receivers includes inactive for >2 weeks. use `-f` to confirm.";
                if (nation.getVm_turns() > 0)
                    return "The " + nations.size() + " receivers includes vacation mode nations. use `-f` to confirm.";
                if (nation.getPosition() < 1) {
                    return "The " + nations.size() + " receivers includes applicants. use `-f` to confirm.";
                }
            }
        }

        List<String> replacementLines = Arrays.asList(replacements.split("\n"));

        Random random = seed == null ? new Random() : new Random(seed);

        Set<String> results = StringMan.enumerateReplacements(announcement, replacementLines, nations.size() + 1000, requiredVariation, requiredDepth);

        if (results.size() < nations.size()) return "Not enough entropy, Please provide more replacements.";

        if (!force) {
            StringBuilder confirmBody = new StringBuilder();
            if (!sendDM && !sendMail)
                confirmBody.append("**Warning: No in-game or direct message option has been specified.**\n");
            confirmBody.append("Send DM (`-d`): ").append(sendDM).append("\n");
            confirmBody.append("Send Ingame (`-m`): ").append(sendMail).append("\n");
            if (!errors.isEmpty()) {
                confirmBody.append("\n**Errors**:\n - ").append(StringMan.join(errors, "\n - ")).append("\n");
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
            String personal = replaced + "\n\n - " + author.getAsMention() + " " + guild.getName();

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

            output.append("\n\n```").append(replaced).append("```").append("^ ").append(nation.getNation());
        }

        output.append("\n\n------\n");
        if (errors.size() > 0) {
            output.append("Errors:\n - ").append(StringMan.join(errors, "\n - "));
        }
        if (failedToDM.size() > 0) {
            output.append("\nFailed DM (sent in-game): ").append(StringMan.getString(failedToDM));
        }
        if (failedToMail.size() > 0) {
            output.append("\nFailed Mail: ").append(StringMan.getString(failedToMail));
        }

        int annId = db.addAnnouncement(author, subject, announcement, replacements, nationList.getFilter());
        for (Map.Entry<DBNation, String> entry : sentMessages.entrySet()) {
            byte[] diff = StringMan.getDiffBytes(announcement, entry.getValue());
            db.addPlayerAnnouncement(entry.getKey(), annId, diff);
        }

        return output.toString().trim();
    }

    @Command
    @RolePermission(Roles.ADMIN)
    public String mask(@Me Member me, @Me GuildDB db, Set<Member> members, Role role, boolean value, String reason) {
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
                response.add(user.getName() + ": Added role to member.");
            } else {
                RateLimitUtil.queue(db.getGuild().removeRoleFromMember(member, role));
                response.add(user.getName() + ": Removed role from member.");
            }
        }
        return StringMan.join(response, "\n").trim();
    }

    @Command
    @RolePermission(value = Roles.ADMIN, root = true)
    public String dm(@Me User author, DBNation nation, String message) {
        User user = nation.getUser();
        if (user == null) return "No user found for " + nation.getNation();

        user.openPrivateChannel().queue(channel -> RateLimitUtil.queue(channel.sendMessage(author.getAsMention() + " said: " + message + "\n\n(no reply)")));
        return "Done!";
    }

    @Command
    @RolePermission(Roles.ADMIN)
    @IsAuthenticated
    public String editAlliance(@Me GuildDB db, @Me User author, @Default String attribute, @Default @TextArea String value) throws Exception {

        Rank rank = attribute != null && attribute.toLowerCase().contains("bank") ? Rank.HEIR : Rank.OFFICER;
        Auth auth = db.getAuth(AlliancePermission.EDIT_ALLIANCE_INFO);
        if (auth == null) return "No authorization set.";

        StringBuilder response = new StringBuilder();

        EditAllianceTask task = new EditAllianceTask(auth.getNation(), post -> {
            if (attribute == null || value == null) {
                throw new IllegalArgumentException("Currently set: " + StringMan.getString(post));
            }
            if (post.containsKey(attribute.toLowerCase()) || attribute.equals("acceptmem")) {
                post.put(attribute.toLowerCase(), value);
                response.append("Attribute has been set.");
            } else {
                response.append("Invalid key: ").append(attribute).append(". Options: ").append(StringMan.getString(post));
            }
        });
        task.call();
        return response.toString();
    }

    @Command(desc = "Remove a discord role Locutus uses.")
    @RolePermission(Roles.ADMIN)
    public String unregisterRole(@Me Guild guild, @Me GuildDB db, Roles locutusRole) {
        db.deleteRole(locutusRole);
        return "Unregistered " + locutusRole;
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

            response.append("**").append(id).append("**:\n");
            for (Map.Entry<Class, Integer> permEntry : perms.entrySet()) {
                response.append(" - ").append(permEntry.getKey().getSimpleName()).append("=").append(permEntry.getValue()).append("\n");
            }
            response.append("\n");
        }

        if (response.length() == 0) return "No permissions are set.";
        return response.toString();
    }

    @Command(desc = "Set the discord roles Locutus uses.")
    @RolePermission(Roles.ADMIN)
    public String aliasRole(@Me User author, @Me Guild guild, @Me GuildDB db, Roles locutusRole, @Default() Role discordRole) {
        if (discordRole == null) {
            int invalidRoles = 0;
            List<Map.Entry<Roles, Long>> roles = db.getRoles();
            StringBuilder response = new StringBuilder("Current aliases:").append('\n');
            for (Map.Entry<Roles, Long> role : roles) {
                Roles locRole = role.getKey();
                GuildDB.Key key = locRole.getKey();

                discordRole = guild.getRoleById(role.getValue());
                String roleName = discordRole == null ? "null" : discordRole.getName();
                response.append(" - ").append(role.getKey().name().toLowerCase()).append(" > ").append(roleName);

                if (key != null && db.getOrNull(key) == null) {
                    response.append(" (missing: ").append(key.name()).append(")");
                }
                response.append('\n');
            }
            response.append("Available aliases: ").append(Roles.getValidRolesStringList()).append('\n');
            response.append("Please provide `locutusRole` and `discordRole` to set an alias.");
            return response.toString().trim();
        }

        Member member = guild.getMember(author);

        db.addRole(locutusRole, discordRole.getIdLong());
        return "Added role alias: " + locutusRole.name().toLowerCase() + " to " + discordRole.getName() + "\n" +
                "To unregister, use " + CM.role.unregister.cmd.create(locutusRole.name()).toSlashCommand() + "";
    }

    public String printApiStats(PoliticsAndWarV2 api) {
        Map<String, AtomicInteger> methodUsage = api.getMethodUsageStats();
        List<Map.Entry<String, AtomicInteger>> sorted = new ArrayList<>(methodUsage.entrySet());
        sorted.sort((o1, o2) -> Integer.compare(o2.getValue().intValue(), o1.getValue().intValue()));

        StringBuilder response = new StringBuilder("API usage by method:\n");
        for (Map.Entry<String, AtomicInteger> entry : sorted) {
            response.append(" - ").append(entry.getKey()).append(": ").append(entry.getValue().intValue()).append("\n");
        }
        response.append("\n\n------------------------\n\nApi stacktraces, too much calls.");
        for (Map.Entry<String, AtomicInteger> entry : sorted) {
            if (entry.getValue().intValue() < 100) break;

            response.append("\n\n**").append(entry.getKey()).append("** |||| ==== |||| :\n");
            Set<List<StackTraceElement>> traces = api.getMethodToStacktrace().get(entry.getKey());
            for (List<StackTraceElement> trace : traces) {
                response.append("\nTrace:\n").append(StringMan.stacktraceToString(trace.toArray(new StackTraceElement[0])));
            }

        }
        return "```" + response + "```";
    }

    @Command()
    @RolePermission(value = Roles.ADMIN, root = true)
    public String testAlert(MessageChannel channel) {
        return channel.sendMessage("Hello World").complete() + "";
    }

    @Command()
    @RolePermission(value = Roles.ADMIN, root = true)
    public String rootApiUsageStats() {
        PoliticsAndWarV2 api = Locutus.imp().getRootPnwApi();
        return printApiStats(api);
    }

    @Command()
    @RolePermission(value = Roles.ADMIN, root = true)
    public String apiUsageStats(@Me Guild guild, boolean cached) {
        PoliticsAndWarV2 api = Locutus.imp().getGuildDB(guild).getApi(false, false);
        return printApiStats(api);
    }

    @Command(desc = "Check if current api keys are valid.")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String importGuildKeys() {
        StringBuilder response = new StringBuilder();
        for (GuildDB db : Locutus.imp().getGuildDatabases().values()) {
            String[] keys = db.getOrNull(GuildDB.Key.API_KEY);
            for (String key : keys) {
                try {
                    ApiKeyDetails stats = new PoliticsAndWarV3(ApiKeyPool.builder().addKeyUnsafe(key).build()).getApiKeyStats();
                    Locutus.imp().getDiscordDB().addApiKey(stats.getNation().getId(), key);

                    response.append(key).append(": success").append("\n");
                } catch (Throwable e) {
                    response.append(key).append(": ").append(e.getMessage()).append("\n");
                }
            }
        }
        return "Done!";
    }

    @Command(desc = "Check if current api keys are valid")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String validateAPIKeys() {
        Set<String> keys = Locutus.imp().getPnwApi().getApiKeyUsageStats().keySet();
        Map<String, String> failed = new LinkedHashMap<>();
        Map<String, ApiKeyDetails> success = new LinkedHashMap<>();
        for (String key : keys) {
            try {
                ApiKeyDetails stats = new PoliticsAndWarV3(ApiKeyPool.builder().addKeyUnsafe(key).build()).getApiKeyStats();
                if (stats != null && stats.getNation() != null && stats.getNation().getId() != null) {
                    success.put(key, stats);
                } else {
                    failed.put(key, "Error: null");
                }
            } catch (Throwable e) {
                failed.put(key, e.getMessage());
            }
        }
        StringBuilder response = new StringBuilder();
        for (Map.Entry<String, String> e : failed.entrySet()) {
            response.append(e.getKey()).append(": ").append(e.getValue()).append("\n");
        }
        for (Map.Entry<String, ApiKeyDetails> e : success.entrySet()) {
            String key = e.getKey();
            ApiKeyDetails record = e.getValue();
            int natId = record.getNation().getId();
            DBNation nation = DBNation.byId(natId);
            if (nation != null) {
                response.append(key).append(": ").append(record).append(" | ").append(nation.getNation()).append(" | ").append(nation.getAllianceName()).append(" | ").append(nation.getPosition()).append("\n");
            } else {
                response.append(e.getKey()).append(": ").append(e.getValue()).append("\n");
            }
        }
        System.out.println(response); // keep
        return "Done (see console)";
    }

    @Command()
    @RolePermission(value = Roles.ADMIN)
    public String testRecruitMessage(@Me GuildDB db) throws IOException {
        JsonObject response = db.sendRecruitMessage(Locutus.imp().getNationDB().getNation(Settings.INSTANCE.NATION_ID));
        return response.toString();
    }

    @Command(desc = "Purge channels older than the time specified.")
    @RolePermission(value = Roles.ADMIN)
    public String debugPurgeChannels(Category category, @Range(min = 60) @Timestamp long cutoff) {
        long now = System.currentTimeMillis();
        int deleted = 0;
        for (GuildMessageChannel GuildMessageChannel : category.getTextChannels()) {
            if (GuildMessageChannel.hasLatestMessage()) {
                long message = GuildMessageChannel.getLatestMessageIdLong();
                try {
                    long created = net.dv8tion.jda.api.utils.TimeUtil.getTimeCreated(message).toEpochSecond() * 1000L;
                    if (created > cutoff) {
                        continue;
                    }
                } catch (Throwable ignore) {
                }
            }
            RateLimitUtil.queue(GuildMessageChannel.delete());
            deleted++;
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
            assert owner != null;
            DBNation nation = DiscordUtil.getNation(owner.getUser());

            Integer alliance = db.getOrNull(GuildDB.Key.ALLIANCE_ID);

            if (nation != null && nation.getActive_m() > 30000) {
                response.append(guild).append("/AA:").append(alliance).append(": owner (nation:").append(nation.getNation_id()).append(") is inactive ").append(TimeUtil.secToTime(TimeUnit.MINUTES, nation.getActive_m())).append("\n");
                continue;
            }
            // In an alliance with inactive leadership (1 month)
            if (alliance != null && !db.isValidAlliance()) {
                response.append(guild).append("/AA:").append(alliance).append(": alliance is invalid (nation:").append(nation == null ? "" : nation.getNation_id() + ")\n");
                continue;
            }

            if (alliance == null && nation == null && checkMessages) {
                long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30);
                boolean error = false;
                long last = 0;

                outer:
                for (GuildMessageChannel channel : guild.getTextChannels()) {
                    if (!channel.hasLatestMessage()) continue;
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
                    response.append(guild).append(": has no recent messages.\n");
                }
            }
        }
        return response.toString();
    }

    @Command
    @RolePermission(value = Roles.ADMIN)
    public String removeInvalidOffshoring(@Me GuildDB db) {
        Set<Long> toRemove = new HashSet<>();
        for (long id : db.getCoalitionRaw(Coalition.OFFSHORING)) {
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
            db.removeCoalition(id, Coalition.OFFSHORING);
        }
        return "Removed `" + StringMan.join(toRemove, ",") + "` from " + Coalition.OFFSHORING;

    }

    @Command
    @RolePermission(value = Roles.ADMIN, root = true)
    public String leaveServer(long guildId) {
        GuildDB db = Locutus.imp().getGuildDB(guildId);
        if (db == null) return "Server not found " + guildId;
        Guild guild = db.getGuild();
        guild.leave().queue();
        return "Leaving " + guild.getName();
    }

    @Command
    @RolePermission(value = Roles.ADMIN, root = true)
    public String listExpiredOffshores() {
        StringBuilder response = new StringBuilder();

        OffshoreInstance offshore = Locutus.imp().getRootBank();
        GuildDB db = offshore.getGuildDB();
        Set<Long> coalitions = db.getCoalitionRaw(Coalition.OFFSHORING);
        Set<Long> invalidIds = new HashSet<>();
        Set<Long> inactiveIds = new HashSet<>();
        Set<Long> unregistered = new HashSet<>();
        Set<Long> threeMonthIds = new HashSet<>();
        Set<Long> sixMonthIds = new HashSet<>();
        Set<Long> nineMonthIds = new HashSet<>();

        for (Long id : coalitions) {
            GuildDB otherDb = (id > Integer.MAX_VALUE) ? Locutus.imp().getGuildDB(id) : Locutus.imp().getGuildDBByAA(id.intValue());
            if (otherDb == null) {
                invalidIds.add(id);
                response.append("\nOther db is null: ").append(id);
                continue;
            }
            Integer aaId = otherDb.getOrNull(GuildDB.Key.ALLIANCE_ID);

            List<Transaction2> transactions;
            if (aaId != null) {
                transactions = offshore.getTransactionsAA(aaId, false);
            } else {
                transactions = offshore.getTransactionsGuild(id, false);
            }
            transactions.removeIf(f -> f.tx_datetime > System.currentTimeMillis());
            transactions.removeIf(f -> f.receiver_id == f.banker_nation && f.tx_id > 0);
            long latest = transactions.isEmpty() ? 0 : transactions.stream().mapToLong(f -> f.tx_datetime).max().getAsLong();
            String timeStr = TimeUtil.secToTime(TimeUnit.MILLISECONDS, System.currentTimeMillis() - latest);
            if (latest < System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30)) {
                threeMonthIds.add(id);
            }
            if (latest < System.currentTimeMillis() - TimeUnit.DAYS.toMillis(60)) {
                sixMonthIds.add(id);
            }
            if (latest < System.currentTimeMillis() - TimeUnit.DAYS.toMillis(90)) {
                nineMonthIds.add(id);
            }

            if (id > Integer.MAX_VALUE) {
                Member owner = otherDb.getGuild().getOwner();

                if (aaId != null) {
                    DBAlliance alliance = DBAlliance.getOrCreate(aaId);
                    Set<DBNation> nations = new HashSet<>(alliance.getNations());
                    nations.removeIf(f -> f.getPosition() < Rank.LEADER.id);
                    nations.removeIf(f -> f.getActive_m() > 10000);

                    if (nations.isEmpty()) {
                        inactiveIds.add(id);
                        assert owner != null;
                        response.append("Inactive alliance (as guild) ").append(aaId).append(" | ").append(db.getGuild().toString()).append(" | owner: ").append(owner.getIdLong());
                    }
                }

                assert owner != null;
                DBNation nation = DiscordUtil.getNation(owner.getUser());
                if (nation == null) {
                    unregistered.add(id);
                    response.append("\nowner is unregistered: ").append(id).append(" | ").append(owner.getIdLong());
                    continue;
                }
                if (nation.getActive_m() > 10000) {
                    inactiveIds.add(id);
                    response.append("\nowner is inactive: ").append(id).append(" | ").append(owner.getIdLong()).append(" | ").append(nation.getNationUrl()).append(" | ").append(nation.getActive_m()).append("m");
                }
            } else {
                otherDb = Locutus.imp().getGuildDBByAA(id.intValue());
                if (otherDb == null) {
                    invalidIds.add(id);
                    response.append("\nAA Other db is null: ").append(id);
                    continue;
                }

                Member owner = otherDb.getGuild().getOwner();

                if (aaId != null) {
                    DBAlliance alliance = DBAlliance.getOrCreate(aaId);
                    Set<DBNation> nations = new HashSet<>(alliance.getNations());
                    nations.removeIf(f -> f.getPosition() < Rank.LEADER.id);
                    nations.removeIf(f -> f.getActive_m() > 10000);

                    if (nations.isEmpty()) {
                        inactiveIds.add(id);
                        assert owner != null;
                        response.append("Inactive alliance (as guild) ").append(aaId).append(" | ").append(db.getGuild().toString()).append(" | owner: ").append(owner.getIdLong()).append(" | (").append(timeStr).append(")");
                    }
                }

                assert owner != null;
                DBNation nation = DiscordUtil.getNation(owner.getUser());
                if (nation == null) {
                    if (aaId != null) {
                        DBAlliance alliance = DBAlliance.get(aaId);
                        if (alliance != null) {
                            Set<DBNation> nations = alliance.getNations(f -> {
                                if (f.active_m() > 10000) return false;
                                if (f.getPosition() > Rank.MEMBER.id) return true;
                                DBAlliancePosition p = f.getAlliancePosition();
                                return p != null && p.hasPermission(AlliancePermission.WITHDRAW_BANK);
                            });
                            if (!nations.isEmpty()) nation = nations.iterator().next();
                        }
                    }
                    if (nation == null) {
                        unregistered.add(id);
                        response.append("\nAA owner is unregistered: ").append(id).append(" | ").append(owner.getIdLong()).append(" | (").append(timeStr).append(")");
                        continue;
                    }
                }
                if (nation.getActive_m() > 10000) {
                    inactiveIds.add(id);
                    response.append("\nAA owner is inactive: ").append(id).append(" | ").append(owner.getIdLong()).append(" | ").append(nation.getNationUrl()).append(" | ").append(nation.getActive_m()).append("m").append(" | (").append(timeStr).append(")");
                    continue;
                }
                DBAlliance alliance = DBAlliance.get(id.intValue());
                if (alliance == null || !alliance.exists()) {
                    invalidIds.add(id);
                    response.append("\nAA does not exist: ").append(id).append(" | (").append(timeStr).append(")");
                }
            }
        }

        double[] totalInvalid = ResourceType.getBuffer();
        double[] totalInactive = ResourceType.getBuffer();
        double[] totalUnregistered = ResourceType.getBuffer();

        double[] threeMonth = ResourceType.getBuffer();
        double[] sixMonth = ResourceType.getBuffer();
        double[] nineMonth = ResourceType.getBuffer();

        for (long id : inactiveIds) {
            Map<ResourceType, Double> depo;
            if (id > Integer.MAX_VALUE) {
                depo = offshore.getDeposits(id, false);
            } else {
                depo = offshore.getDeposits((int) id, false);
            }
            totalInactive = PnwUtil.add(totalInactive, PnwUtil.resourcesToArray(depo));
        }

        for (long id : invalidIds) {
            Map<ResourceType, Double> depo;
            if (id > Integer.MAX_VALUE) {
                depo = offshore.getDeposits(id, false);
            } else {
                depo = offshore.getDeposits((int) id, false);
            }
            totalInvalid = PnwUtil.add(totalInvalid, PnwUtil.resourcesToArray(depo));
        }

        for (long id : unregistered) {
            Map<ResourceType, Double> depo;
            if (id > Integer.MAX_VALUE) {
                depo = offshore.getDeposits(id, false);
            } else {
                depo = offshore.getDeposits((int) id, false);
            }
            totalUnregistered = PnwUtil.add(totalUnregistered, PnwUtil.resourcesToArray(depo));
        }

        for (long id : threeMonthIds) {
            Map<ResourceType, Double> depo;
            if (id > Integer.MAX_VALUE) {
                depo = offshore.getDeposits(id, false);
            } else {
                depo = offshore.getDeposits((int) id, false);
            }
            threeMonth = PnwUtil.add(threeMonth, PnwUtil.resourcesToArray(depo));
        }

        for (long id : sixMonthIds) {
            Map<ResourceType, Double> depo;
            if (id > Integer.MAX_VALUE) {
                depo = offshore.getDeposits(id, false);
            } else {
                depo = offshore.getDeposits((int) id, false);
            }
            sixMonth = PnwUtil.add(sixMonth, PnwUtil.resourcesToArray(depo));
        }

        for (long id : nineMonthIds) {
            Map<ResourceType, Double> depo;
            if (id > Integer.MAX_VALUE) {
                depo = offshore.getDeposits(id, false);
            } else {
                depo = offshore.getDeposits((int) id, false);
            }
            nineMonth = PnwUtil.add(nineMonth, PnwUtil.resourcesToArray(depo));
        }

        response.append("\n\nTotal invalid: ").append(PnwUtil.resourcesToString(totalInvalid));
        response.append("\nTotal inactive: ").append(PnwUtil.resourcesToString(totalInactive));
        response.append("\nTotal unregistered: ").append(PnwUtil.resourcesToString(totalUnregistered));
        response.append("\n1 month: ").append(PnwUtil.resourcesToString(threeMonth));
        response.append("\n2 month: ").append(PnwUtil.resourcesToString(sixMonth));
        response.append("\n3 month: ").append(PnwUtil.resourcesToString(nineMonth));


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

            response.append(db.getName()).append(" | ").append(db.getIdLong()).append("\n");
            for (Map.Entry<Class, Integer> entry : perms.entrySet()) {
                response.append(" - ").append(entry.getKey()).append("=").append(entry.getValue()).append("\n");
            }
            response.append("\n");

        }
        return response.toString();
    }

    @Command()
    @RolePermission(value = Roles.ADMIN, root = true)
    public String listGuildOwners() {
        ArrayList<GuildDB> guilds = new ArrayList<>(Locutus.imp().getGuildDatabases().values());
        guilds.sort(Comparator.comparingLong(o -> o.getGuild().getIdLong()));
        StringBuilder result = new StringBuilder();
        for (GuildDB value : guilds) {
            Guild guild = value.getGuild();
            User owner = Locutus.imp().getDiscordApi().getUserById(guild.getOwnerIdLong());
            result.append(guild.getIdLong()).append(" | ").append(guild.getName()).append(" | ").append(owner.getName()).append("\n");
        }
        return result.toString();
    }

    @Command()
    @RolePermission(value = Roles.ADMIN, root = true)
    public String syncInfraLand() {
        List<Event> events = new ArrayList<>();
        Locutus.imp().getNationDB().updateCitiesV2(events::add);
        if (events.size() > 0) {
            Locutus.imp().getExecutor().submit(() -> {
                for (Event event : events) event.post();
            });
        }
        return "Updated city infra land. " + events.size() + " changes detected.";
    }

    @Command()
    @RolePermission(value = Roles.ADMIN, root = true)
    public String syncMetrics(@Default("80") int topX) {
        AllianceMetric.update(topX);
        return "Updated metrics for top " + topX + " alliances.";
    }

    @Command()
    @RolePermission(value = Roles.ADMIN, root = true)
    public String syncCities(NationDB db) {
        List<Event> events = new ArrayList<>();
        db.updateAllCities(events::add);
        if (events.size() > 0) {
            Locutus.imp().getExecutor().submit(() -> {
                for (Event event : events) event.post();
            });
        }
        return "Updated all cities. " + events.size() + " changes detected.";
    }


    @Command()
    @RolePermission(value = Roles.ADMIN, root = true)
    public String syncNations(NationDB db, @Default Set<DBNation> nations) {
        List<Event> events = new ArrayList<>();
        Set<Integer> updatedIds;
        if (nations != null && !nations.isEmpty()) {
            updatedIds = db.updateNations(nations.stream().map(DBNation::getId).toList(), events::add);
        } else {
            updatedIds = db.updateAllNations(events::add);
        }
        if (events.size() > 0) {
            Locutus.imp().getExecutor().submit(() -> {
                for (Event event : events) event.post();
            });
        }
        return "Updated " + updatedIds.size() + " nations. " + events.size() + " changes detected.";
    }

    @Command()
    @RolePermission(value = Roles.ADMIN, root = true)
    public String syncBanks(@Me GuildDB db, @Me IMessageIO channel, @Default DBAlliance alliance, @Default @Timestamp Long timestamp) {
        if (alliance != null) {
            db = alliance.getGuildDB();
            if (db == null) throw new IllegalArgumentException("No guild found for AA:" + alliance);
        }
        channel.send("Syncing banks for " + db.getGuild() + "...");
        OffshoreInstance bank = db.getHandler().getBank();
        bank.sync(timestamp, false);

        Locutus.imp().getBankDB().updateBankRecs(Event::post);
            return "Banks has been synced.";
    }

    @Command()
    @RolePermission(value = Roles.ADMIN, root = true)
    public String syncBlockades(@Me IMessageIO channel) {
        NationUpdateProcessor.updateBlockades();
        return "All blockades events has been synced.";
    }

    @Command(aliases = {"syncforum", "syncforums"})
    @RolePermission(value = Roles.ADMIN, root = true)
    public String syncForum(@Me IMessageIO channel) throws IOException, ParseException {
        Locutus.imp().getForumDb().update();
        return "Forum events has been synced.";
    }

    @Command(desc = "List authenticated users in the guild.")
    @RolePermission(value = Roles.ADMIN)
    public String listAuthenticated(@Me GuildDB db) {
        List<Member> members = db.getGuild().getMembers();

        Map<DBNation, Rank> registered = new LinkedHashMap<>();
        Map<DBNation, String> errors = new HashMap<>();

        Set<Integer> alliances = db.getAllianceIds(false);
        for (Member member : members) {
            DBNation nation = DiscordUtil.getNation(member.getUser());
            if (nation != null && (alliances.isEmpty() || alliances.contains(nation.getAlliance_id()))) {
                try {
                    Auth auth = nation.getAuth(null);
                    registered.put(nation, Rank.byId(nation.getPosition()));
                    try {
                        ApiKeyPool.ApiKey key = auth.fetchApiKey();
                    } catch (Throwable e) {
                        errors.put(nation, e.getMessage());
                    }
                } catch (IllegalArgumentException ignore) {
                }
            }
        }

        if (registered.isEmpty()) {
            return "No registered users.";
        }
        StringBuilder result = new StringBuilder();
        for (Map.Entry<DBNation, Rank> entry : registered.entrySet()) {
            result.append(entry.getKey().getNation()).append(" - ").append(entry.getValue());
            String error = errors.get(entry.getValue());
            if (error != null) {
                result.append(": Could not validate: ").append(error);
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
            if (attack.looted > 0) {
                LootEntry existing = Locutus.imp().getNationDB().getAllianceLoot(attack.looted);
                if (existing != null && existing.getDate() < attack.epoch) {
                    Double pct = attack.getLootPercent();
                    if (pct == 0) pct = 0.01;
                    double factor = 1 / pct;

                    double[] lootCopy = attack.loot == null ? ResourceType.getBuffer() : attack.loot.clone();
                    for (int i = 0; i < lootCopy.length; i++) {
                        lootCopy[i] = (lootCopy[i] * factor) - lootCopy[i];
                    }

                    Locutus.imp().getNationDB().saveAllianceLoot(attack.looted, attack.epoch, lootCopy, NationLootType.WAR_LOSS);
                }
            }
        }
        return "Loot from attacks has been synced.";

    }
}
