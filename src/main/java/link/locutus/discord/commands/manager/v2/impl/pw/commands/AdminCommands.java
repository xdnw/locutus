package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv2.PoliticsAndWarV2;
import link.locutus.discord.apiv3.PoliticsAndWarV3;
import link.locutus.discord.commands.manager.v2.binding.annotation.Arg;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.annotation.Range;
import link.locutus.discord.commands.manager.v2.binding.annotation.Switch;
import link.locutus.discord.commands.manager.v2.binding.annotation.TextArea;
import link.locutus.discord.commands.manager.v2.binding.annotation.Timestamp;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.HasApi;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.IsAuthenticated;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.WhitelistPermission;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.NationDB;
import link.locutus.discord.db.entities.*;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.event.Event;
import link.locutus.discord.pnw.NationList;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.offshore.Auth;
import link.locutus.discord.util.offshore.OffshoreInstance;
import link.locutus.discord.util.task.EditAllianceTask;
import link.locutus.discord.util.update.NationUpdateProcessor;
import com.google.gson.JsonObject;
import link.locutus.discord.apiv1.enums.Rank;
import com.politicsandwar.graphql.model.ApiKeyDetails;
import net.dv8tion.jda.api.entities.Category;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.PrivateChannel;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.GuildMessageChannel;
import net.dv8tion.jda.api.entities.User;

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class AdminCommands {
    @Command
    @RolePermission(value = Roles.ADMIN, root = true)
    public String deleteAllInaccessibleChannels(@Switch('f') boolean force) {
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
            return "No keys to unset";
        }
        StringBuilder response = new StringBuilder();
        for (Map.Entry<GuildDB, List<GuildDB.Key>> entry : toUnset.entrySet()) {
            response.append(entry.getKey().getGuild().toString() + ":\n");
            List<GuildDB.Key> keys = entry.getValue();
            response.append(" - " + StringMan.join(keys, "\n - "));
            response.append("\n");
        }
        String footer = "Rerun the command with `-f` to confirm";
        return response + footer;
    }

    @Command
    @RolePermission(Roles.ADMIN)
    public String syncReferrals(@Me GuildDB db) {
        if (!db.isValidAlliance()) return "Not in an alliance";
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

    @Command(desc = "Send an announcement to multiple nations, with random variations for opsec")
    @RolePermission(Roles.ADMIN)
    @HasApi
    public String announce(@Me GuildDB db, @Me Guild guild, @Me Message message, @Me MessageChannel currentChannel, @Me User author, NationList nationList, @Arg("The subject used if DM fails") String subject, String announcement, String replacements, @Switch('v') @Default("0") Integer requiredVariation, @Switch('r') @Default("0") Integer requiredDepth, @Switch('s') Long seed, @Switch('m') boolean sendMail, @Switch('d') boolean sendDM, @Switch('f') boolean force) throws IOException {
        String[] keys = db.getOrThrow(GuildDB.Key.API_KEY);
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
                    return "The " + nations.size() + " receivers includes inactive for >2 weeks. use `-f` to confirm";
                if (nation.getVm_turns() > 0)
                    return "The " + nations.size() + " receivers includes vacation mode nations. use `-f` to confirm";
                if (nation.getPosition() < 1) {
                    return "The " + nations.size() + " receivers includes applicants. use `-f` to confirm";
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
                confirmBody.append("\n**Errors**:\n - " + StringMan.join(errors, "\n - ")).append("\n");
            }
            DiscordUtil.pending(currentChannel, message, "Send to " + nations.size() + " nations", confirmBody + "\nPress to confirm", 'f');
            return null;
        }

        RateLimitUtil.queue(currentChannel.sendMessage("Please wait..."));

        List<String> resultsArray = new ArrayList<>(results);
        Collections.shuffle(resultsArray, random);

        resultsArray = resultsArray.subList(0, nations.size());

        List<Integer> failedToDM = new ArrayList<>();

        StringBuilder output = new StringBuilder();

        Map<DBNation, String> sentMessages = new HashMap<>();

        int i = 0;
        for (DBNation nation : nations) {
            String replaced = resultsArray.get(i++);
            String personal = replaced + "\n\n - " + author.getAsMention() + " " + guild.getName();

            boolean result = sendDM && nation.sendDM(personal);
            if (!result) {
                failedToDM.add(nation.getNation_id());
            }
            if (!result || sendMail) {
                nation.sendMail(keys, subject, personal);
            }

            sentMessages.put(nation, replaced);

            output.append("\n\n```" + replaced + "```" + "^ " + nation.getNation());
        }

        output.append("\n\n------\n");
        if (errors.size() > 0) {
            output.append("Errors:\n - " + StringMan.join(errors, "\n - "));
        }
        if (failedToDM.size() > 0) {
            output.append("\nFailed DM (sent ingame): " + StringMan.getString(failedToDM));
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
                response.add(user.getName() + ": Added role to member");
            } else {
                RateLimitUtil.queue(db.getGuild().removeRoleFromMember(member, role));
                response.add(user.getName() + ": Removed role from member");
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

    @Command
    @RolePermission(Roles.ADMIN)
    @IsAuthenticated
    public String editAlliance(@Me GuildDB db, @Me User author, @Default String attribute, @Default @TextArea String value) throws Exception {

        Rank rank = attribute != null && attribute.toLowerCase().contains("bank") ? Rank.HEIR : Rank.OFFICER;
        Auth auth = db.getAuth(rank.id);
        if (auth == null) return "No authorization set";
        int allianceId = db.getOrThrow(GuildDB.Key.ALLIANCE_ID);

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

    @Command(desc = "Remove a discord role Locutus uses")
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

            response.append("**" + id + "**:\n");
            for (Map.Entry<Class, Integer> permEntry : perms.entrySet()) {
                response.append(" - " + permEntry.getKey().getSimpleName() + "=" + permEntry.getValue() + "\n");
            }
            response.append("\n");
        }

        if (response.length() == 0) return "No permissions are set";
        return response.toString();
    }

    @Command(desc = "Set the discord roles Locutus uses")
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
                response.append(" - " + role.getKey().name().toLowerCase() + " > " + roleName);

                if (key != null && db.getOrNull(key) == null) {
                    response.append(" (missing: " + key.name() + ")");
                }
                response.append('\n');
            }
            response.append("Available aliases: " + Roles.getValidRolesStringList()).append('\n');
            response.append("Usage: `" + Settings.INSTANCE.DISCORD.COMMAND.COMMAND_PREFIX + "aliasrole <" + StringMan.join(Arrays.asList(Roles.values()).stream().map(r -> r.name()).collect(Collectors.toList()), "|") + "> <discord-role>`");
            return response.toString().trim();
        }

        Member member = guild.getMember(author);

        db.addRole(locutusRole, discordRole.getIdLong());
        return "Added role alias: " + locutusRole.name().toLowerCase() + " to " + discordRole.getName() + "\n" +
                "To unregister, use `" + Settings.INSTANCE.DISCORD.COMMAND.COMMAND_PREFIX + "unregisterRole <locutusRole>`";
    }

    public String apiUsageStats(PoliticsAndWarV2 api) {
        Map<String, AtomicInteger> methodUsage = api.getMethodUsageStats();
        List<Map.Entry<String, AtomicInteger>> sorted = new ArrayList<>(methodUsage.entrySet());
        sorted.sort((o1, o2) -> Integer.compare(o2.getValue().intValue(), o1.getValue().intValue()));

        StringBuilder response = new StringBuilder("API usage by method:\n");
        for (Map.Entry<String, AtomicInteger> entry : sorted) {
            response.append(" - " + entry.getKey() + ": " + entry.getValue().intValue() + "\n");
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
    public String testAlert(MessageChannel channel) {
        return channel.sendMessage("Hello World").complete() + "";
    }

    @Command()
    @RolePermission(value = Roles.ADMIN, root = true)
    public String rootApiUsageStats(boolean bank) {
        PoliticsAndWarV2 api = bank ? Locutus.imp().getRootPnwApi() : Locutus.imp().getBankApi();
        return apiUsageStats(api);
    }

    @Command()
    @RolePermission(value = Roles.ADMIN, root = true)
    public String apiUsageStats(@Me Guild guild, boolean cached) {
        PoliticsAndWarV2 api = Locutus.imp().getGuildDB(guild).getApi(cached);
        return apiUsageStats(api);
    }

    @Command(desc = "Check if current api keys are valid")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String validateAPIKeys() {
        Set<String> keys = Locutus.imp().getPnwApi().getApiKeyUsageStats().keySet();
        Map<String, String> failed = new LinkedHashMap<>();
        Map<String, ApiKeyDetails> success = new LinkedHashMap<>();
        for (String key : keys) {
            try {
                ApiKeyDetails stats = new PoliticsAndWarV3(key).getApiKeyStats();
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
            DBNation nation = DBNation.byId(natId);
            if (nation != null) {
                response.append(key + ": " + record.toString() + " | " + nation.getNation() + " | " + nation.getAllianceName() + " | " + nation.getPosition() + "\n");
            } else {
                response.append(e.getKey() + ": " + e.getValue() + "\n");
            }
        }
        System.out.println(response); // keep
        return "Done (see console)";
    }

    @Command()
    @WhitelistPermission
    @RolePermission(value = Roles.ADMIN)
    public String testRecruitMessage(@Me GuildDB db) throws IOException {
        JsonObject response = db.sendRecruitMessage(Locutus.imp().getNationDB().getNation(Settings.INSTANCE.NATION_ID));
        return response.toString();
    }

    @Command
    @RolePermission(value = Roles.ADMIN)
    public String debugPurgeChannels(Category category, @Range(min=60) @Timestamp long cutoff) {
        long now = System.currentTimeMillis();
        int deleted = 0;
        for (GuildMessageChannel GuildMessageChannel : category.getTextChannels()) {
            if (GuildMessageChannel.hasLatestMessage()) {
                long message = GuildMessageChannel.getLatestMessageIdLong();
                try {
                    Message msg = RateLimitUtil.complete(GuildMessageChannel.retrieveMessageById(message));
                    long created = msg.getTimeCreated().toEpochSecond() * 1000L;
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

            Integer alliance = db.getOrNull(GuildDB.Key.ALLIANCE_ID);

            if (nation != null && nation.getActive_m() > 30000) {
                response.append(guild + "/AA:" + alliance + ": owner (nation:" + nation.getNation_id() + ") is inactive " + TimeUtil.secToTime(TimeUnit.MINUTES, nation.getActive_m()) + "\n");
                continue;
            }
            // In an alliance with inactive leadership (1 month)
            if (alliance != null && !db.isValidAlliance()) {
                response.append(guild + "/AA:" + alliance + ": alliance is invalid (nation:" + (nation == null ? "" : nation.getNation_id() + ")\n"));
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
                    response.append(guild + ": has no recent messages\n");
                    continue;
                }
            }
        }
        return response.toString();
    }

    @Command
    @RolePermission(value = Roles.ADMIN, root = true)
    public String listExpiredOffshores() {
        StringBuilder response = new StringBuilder();

        OffshoreInstance offshore = Locutus.imp().getRootBank();
        GuildDB db = offshore.getGuildDB();
        Set<Long> coalitions = db.getCoalitionRaw(Coalition.OFFSHORING);
        for (Long id : coalitions) {
            if (id > Integer.MAX_VALUE) {
                GuildDB otherDb = Locutus.imp().getGuildDB(id);
                if (otherDb == null) {
                    response.append("\nOther db is null: " + id);
                    continue;
                }
                Member owner = otherDb.getGuild().getOwner();

                Integer aaId = db.getOrNull(GuildDB.Key.ALLIANCE_ID);
                if (aaId != null) {
                    DBAlliance alliance = DBAlliance.getOrCreate(aaId);
                    Set<DBNation> nations = new HashSet<>(alliance.getNations());
                    nations.removeIf(f -> f.getPosition() < Rank.LEADER.id);
                    nations.removeIf(f -> f.getActive_m() > 10000);

                    if (nations.isEmpty()) {
                        response.append("Inactive alliance (as guild) " + aaId + " | " + db.getGuild().toString() + " | owner: " + owner.getIdLong());
                    }
                }

                DBNation nation = DiscordUtil.getNation(owner.getUser());
                if (nation == null) {
                    response.append("\nowner is unregistered: " + id + " | " + owner.getIdLong());
                    continue;
                }
                if (nation.getActive_m() > 10000) {
                    response.append("\nowner is inactive: " + id + " | " + owner.getIdLong() + " | " + nation.getNationUrl() + " | " + nation.getActive_m() + "m");
                    continue;
                }
            } else {
                GuildDB otherDb = Locutus.imp().getGuildDBByAA(id.intValue());
                if (otherDb == null) {
                    response.append("\nAA Other db is null: " + id);
                    continue;
                }

                Member owner = otherDb.getGuild().getOwner();

                Integer aaId = db.getOrNull(GuildDB.Key.ALLIANCE_ID);
                if (aaId != null) {
                    DBAlliance alliance = DBAlliance.getOrCreate(aaId);
                    Set<DBNation> nations = new HashSet<>(alliance.getNations());
                    nations.removeIf(f -> f.getPosition() < Rank.LEADER.id);
                    nations.removeIf(f -> f.getActive_m() > 10000);

                    if (nations.isEmpty()) {
                        response.append("Inactive alliance (as guild) " + aaId + " | " + db.getGuild().toString() + " | owner: " + owner.getIdLong());
                    }
                }

                DBNation nation = DiscordUtil.getNation(owner.getUser());
                if (nation == null) {
                    response.append("\nAA owner is unregistered: " + id + " | " + owner.getIdLong());
                    continue;
                }
                if (nation.getActive_m() > 10000) {
                    response.append("\nAA owner is inactive: " + id + " | " + owner.getIdLong() + " | " + nation.getNationUrl() + " | " + nation.getActive_m() + "m");
                    continue;
                }
                DBAlliance alliance = DBAlliance.get(id.intValue());
                if (alliance == null || !alliance.exists()) {
                    response.append("\nAA does not exist: " + id);
                    continue;
                }
            }
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
                response.append(" - " + entry.getKey() + "=" + entry.getValue() + "\n");
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
        List<Event> events = new ArrayList<>();
        db.updateAllCities(events::add);
        if (events.size() > 0) {
            Locutus.imp().getExecutor().submit(() -> {
                for (Event event : events) event.post();;
            });
        }
        return "Updated all cities. " + events.size() + " changes detected";
    }


    @Command()
    @RolePermission(value = Roles.ADMIN, root = true)
    public String syncNations(NationDB db) throws IOException, ParseException {
        List<Event> events = new ArrayList<>();
        Set<Integer> nations = db.updateAllNations(events::add);
        if (events.size() > 0) {
            Locutus.imp().getExecutor().submit(() -> {
                for (Event event : events) event.post();;
            });
        }
        return "Updated " + nations.size() + " nations. " + events.size() + " changes detected";
    }

    @Command()
    @RolePermission(value = Roles.ADMIN, root = true)
    public String syncBanks(@Me GuildDB db, @Me MessageChannel channel, @Default DBAlliance alliance, @Default @Timestamp Long timestamp) throws IOException, ParseException {
        if (alliance != null) {
            db = alliance.getGuildDB();
            if (db == null) throw new IllegalArgumentException("No guild found for AA:" + alliance);
        }
        Auth auth = db.getAuth(Rank.OFFICER.id);
        if (auth == null) return "No authentication found for this guild";

        channel.sendMessage("Syncing bank for " + db.getGuild());
        OffshoreInstance bank = db.getHandler().getBank();
        bank.sync(timestamp, false);
        return "Done!";
    }

    @Command()
    @RolePermission(value = Roles.ADMIN, root = true)
    public String syncBlockades(@Me MessageChannel channel) throws IOException, ParseException {
        NationUpdateProcessor.updateBlockades();;
        return "Done!";
    }

    @Command(aliases = {"syncforum", "syncforums"})
    @RolePermission(value = Roles.ADMIN, root = true)
    public String syncForum(@Me MessageChannel channel) throws IOException, ParseException {
        Locutus.imp().getForumDb().update();
        return "Done!";
    }

    @Command(desc = "List authenticated users in the guild")
    @RolePermission(value = Roles.ADMIN)
    public String listAuthenticated(@Me GuildDB db) {
        List<Member> members = db.getGuild().getMembers();

        Map<DBNation, Rank> registered = new LinkedHashMap<>();
        Map<DBNation, String> errors = new HashMap<>();

        Set<Integer> alliances = db.getAllianceIds();
        for (Member member : members) {
            DBNation nation = DiscordUtil.getNation(member.getUser());
            if (nation != null && (alliances.isEmpty() || alliances.contains(nation.getAlliance_id()))) {
                try {
                    Auth auth = nation.getAuth(null);
                    registered.put(nation, Rank.byId(nation.getPosition()));
                    try {
                        String key = auth.getApiKey();
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
            result.append(entry.getKey().getNation() + " - " + entry.getValue());
            String error = errors.get(entry.getValue());
            if (error != null) {
                result.append(": Could not validate: " + error);
            }
            result.append("\n");
        }
        return result.toString().trim();
    }
}
