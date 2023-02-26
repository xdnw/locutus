package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.core.ApiKeyPool;
import link.locutus.discord.apiv3.enums.AlliancePermission;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Filter;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.annotation.Switch;
import link.locutus.discord.commands.manager.v2.binding.annotation.TextArea;
import link.locutus.discord.commands.manager.v2.binding.annotation.Timediff;
import link.locutus.discord.commands.manager.v2.binding.annotation.Timestamp;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.HasApi;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.IsAlliance;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.IsAuthenticated;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.WhitelistPermission;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.TaxRate;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.NationPlaceholders;
import link.locutus.discord.commands.rankings.builder.SummedMapRankBuilder;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.*;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.pnw.SimpleNationList;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.offshore.Auth;
import link.locutus.discord.util.offshore.test.IACategory;
import link.locutus.discord.util.offshore.test.IAChannel;
import link.locutus.discord.util.sheet.SpreadSheet;
import link.locutus.discord.util.task.MailRespondTask;
import link.locutus.discord.util.task.ia.IACheckup;
import link.locutus.discord.web.jooby.handler.CommandResult;
import com.google.gson.JsonObject;
import link.locutus.discord.apiv1.enums.Rank;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.exceptions.HierarchyException;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import org.apache.commons.collections4.map.PassiveExpiringMap;
import org.json.JSONObject;
import rocker.grant.nation;
import rocker.guild.ia.message;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.time.OffsetDateTime;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class IACommands {

    @Command(desc = "Add a role to all users in a server")
    @RolePermission(Roles.ADMIN)
    public String addRoleToAllMembers(@Me Guild guild, Role role) {
        int amt = 0;
        for (Member member : guild.getMembers()) {
            if (!member.getRoles().contains(role)) {
                RateLimitUtil.queue(guild.addRoleToMember(member, role));
                amt ++;
            }
        }
        return "Added " + amt + " roles to members (note: it may take a few minutes to update)";
    }
    @Command
    @RolePermission(Roles.ADMIN)
    public String msgInfo(@Me IMessageIO channel, Message message, @Switch("i") boolean useIds) {
        StringBuilder response = new StringBuilder();

        List<MessageReaction> reactions = message.getReactions();
        Map<User, List<String>> reactionsByUser = new LinkedHashMap<>();
        for (MessageReaction reaction : reactions) {
            String emoji = reaction.getReactionEmote().getEmoji();
            List<User> users = RateLimitUtil.complete(reaction.retrieveUsers());
            for (User user : users) {
                reactionsByUser.computeIfAbsent(user, f -> new ArrayList<>()).add(emoji);
            }
        }

        String title = "Message " + message.getIdLong();
        response.append("```" + DiscordUtil.trimContent(message.getContentRaw()).replaceAll("`", "\\`") + "```\n\n");

        if (!reactionsByUser.isEmpty()) {
            for (Map.Entry<User, List<String>> entry : reactionsByUser.entrySet()) {
                if (useIds) {
                    response.append(entry.getKey().getIdLong() + "\t" + StringMan.join(entry.getValue(), ","));
                } else {
                    response.append(entry.getKey().getAsMention() + "\t" + StringMan.join(entry.getValue(), ","));
                }
                response.append("\n");
            }
        }

        channel.create().embed(title, response.toString()).send();
        return null;
    }

    @Command(desc = "Close inactive channels in a category")
    @RolePermission(value = {
            Roles.INTERNAL_AFFAIRS_STAFF,
            Roles.INTERNAL_AFFAIRS,
            Roles.ECON_LOW_GOV,
            Roles.ECON,
            Roles.MILCOM,
            Roles.MILCOM_ADVISOR,
            Roles.FOREIGN_AFFAIRS,
            Roles.FOREIGN_AFFAIRS_STAFF,
    }, any = true)
    public String closeInactiveChannels(@Me GuildDB db, @Me IMessageIO outputChannel, Category category, @Timediff long age, @Switch("f") boolean force) {
        long cutoff = System.currentTimeMillis() - age;

        IMessageBuilder msg = outputChannel.create();
        for (GuildMessageChannel channel : category.getTextChannels()) {
            String[] split = channel.getName().split("-");
            if (!MathMan.isInteger(split[split.length - 1])) continue;

            if (channel.hasLatestMessage()) {
                long lastId = channel.getLatestMessageIdLong();
                long lastMessageTime = net.dv8tion.jda.api.utils.TimeUtil.getTimeCreated(lastId).toEpochSecond() * 1000L;
                if (lastMessageTime > cutoff) continue;
            }

            String append = close(db, channel, force);
            msg = msg.append(append);
        }
        msg.append("Done!").send();
        return null;
    }

    @Command
    public String listAssignableRoles(@Me GuildDB db, @Me Member member) {
        Map<Role, Set<Role>> assignable = db.getOrNull(GuildDB.Key.ASSIGNABLE_ROLES);
        if (assignable == null || assignable.isEmpty()) {
            return "No roles found. See `" +  CM.self.create.cmd.toSlashMention() + "`";
        }
        assignable = new HashMap<>(assignable);
        if (!Roles.ADMIN.has(member)) {
            Set<Role> myRoles = new HashSet<>(member.getRoles());
            assignable.entrySet().removeIf(f -> !myRoles.contains(f.getValue()));
        }

        if (assignable.isEmpty()) return "You do not have permission to assign any roles";


        StringBuilder response = new StringBuilder();
        for (Map.Entry<Role, Set<Role>> entry : assignable.entrySet()) {
            Role role = entry.getKey();
            response.append("\n" + role.getName() + ":\n - "
                    + StringMan.join(entry.getValue().stream().map(Role::getName).collect(Collectors.toList()), "\n - "));
        }

        return response.toString().trim();
    }

    @Command(desc = "Allow a role to add/remove roles from users")
    @RolePermission(Roles.ADMIN)
    public String addAssignableRole(@Me GuildDB db, Role govRole, Set<Role> assignableRoles) {
        Map<Role, Set<Role>> assignable = db.getOrNull(GuildDB.Key.ASSIGNABLE_ROLES);
        if (assignable == null) assignable = new HashMap<>();

        assignable.computeIfAbsent(govRole, f -> new HashSet<>()).addAll(assignableRoles);

        String value = GuildDB.Key.ASSIGNABLE_ROLES.toString(assignable);
        db.setInfo(GuildDB.Key.ASSIGNABLE_ROLES, value);

        return StringMan.getString(govRole) + " can now add/remove " + StringMan.getString(assignableRoles) + " via " + CM.role.add.cmd.toSlashMention() + " / " + CM.role.remove.cmd.toSlashMention() + "\n" +
                " - To see a list of current mappings, use " + CM.settings.cmd.create(GuildDB.Key.ASSIGNABLE_ROLES.name(), null) + "";
    }

    @Command(desc = "Remove a role from adding/removing specified roles\n" +
            "(having manage roles perm on discord overrides this)")
    @RolePermission(Roles.ADMIN)
    public String removeAssignableRole(@Me GuildDB db, Role govRole, Set<Role> assignableRoles) {
        Map<Role, Set<Role>> assignable = db.getOrNull(GuildDB.Key.ASSIGNABLE_ROLES);
        if (assignable == null) assignable = new HashMap<>();

        if (!assignable.containsKey(govRole)) {
            return govRole + " does not have any roles it can assign";
        }

        StringBuilder response = new StringBuilder();
        Set<Role> current = assignable.get(govRole);

        for (Role role : assignableRoles) {
            if (current.contains(role)) {
                current.remove(role);
                response.append("\n" + govRole + " can no longer assign " + role);
            } else {
                response.append("\nUnable to remove " + role + " (no mapping found)");
            }
        }

        String value = GuildDB.Key.ASSIGNABLE_ROLES.toString(assignable);
        db.setInfo(GuildDB.Key.ASSIGNABLE_ROLES, value);

        return response.toString() + "\n" +
                " - To see a list of current mappings, use " + CM.settings.cmd.create(GuildDB.Key.ASSIGNABLE_ROLES.name(), null) + "";
    }

    @Command(desc = "Add role to a user\n" +
            "See: `{prefix}listAssignableRoles`")
    public String addRole(@Me GuildDB db, @Me Member author, Member member, Role addRole) {
        Map<Role, Set<Role>> assignable = db.getOrNull(GuildDB.Key.ASSIGNABLE_ROLES);
        if (assignable == null) return "`!KeyStore ASSIGNABLE_ROLES` is not set`";
        boolean canAssign = Roles.ADMIN.has(author);
        if (!canAssign) {
            for (Role role : author.getRoles()) {
                if (assignable.getOrDefault(role, Collections.emptySet()).contains(addRole)) {
                    canAssign = true;
                    break;
                }
            }
        }
        if (!canAssign) {
            return "No permission to assign " + addRole + " (see: `listAssignableRoles` | ADMIN: see `" +  CM.self.create.cmd.toSlashMention() + "`)";
        }
        if (member.getRoles().contains(addRole)) {
            return member + " already has " + addRole;
        }
        RateLimitUtil.queue(db.getGuild().addRoleToMember(member, addRole));
        return "Added " + addRole + " to " + member;
    }

    @Command(desc = "Remove a role to a user\n" +
            "See: `{prefix}listAssignableRoles`")
    @RolePermission(value = {
            Roles.INTERNAL_AFFAIRS_STAFF,
            Roles.INTERNAL_AFFAIRS,
            Roles.ECON_LOW_GOV,
            Roles.ECON,
            Roles.MILCOM,
            Roles.MILCOM_ADVISOR,
            Roles.FOREIGN_AFFAIRS,
            Roles.FOREIGN_AFFAIRS_STAFF,
    }, any = true)
    public String removeRole(@Me GuildDB db, @Me Member author, Member member, Role addRole) {
        Map<Role, Set<Role>> assignable = db.getOrNull(GuildDB.Key.ASSIGNABLE_ROLES);
        if (assignable == null) return "`!KeyStore ASSIGNABLE_ROLES` is not set`";
        boolean canAssign = Roles.ADMIN.has(author);
        if (!canAssign) {
            for (Role role : author.getRoles()) {
                if (assignable.getOrDefault(role, Collections.emptySet()).contains(addRole)) {
                    canAssign = true;
                    break;
                }
            }
        }
        if (!canAssign) {
            return "No permission to assign " + addRole + " (see: `listAssignableRoles` | ADMIN: see `" +  CM.self.create.cmd.toSlashMention() + "`)";
        }
        if (!member.getRoles().contains(addRole)) {
            return member + " does not have " + addRole;
        }
        RateLimitUtil.queue(db.getGuild().removeRoleFromMember(member, addRole));
        return "Removed " + addRole + " to " + member;
    }

    @Command(desc = "Opt out of beige alerts")
    @RolePermission(Roles.MEMBER)
    public String beigeAlertOptOut(@Me Member member, @Me Guild guild) {
        Role role = Roles.BEIGE_ALERT_OPT_OUT.toRole(guild);
        if (role == null) return "No opt out role found for " + Roles.BEIGE_ALERT_OPT_OUT;
        RateLimitUtil.queue(guild.addRoleToMember(member, role));
        return "Opted out of beige alerts";
    }

    @Command(desc = "Unassign a mentee from any mentor")
    @RolePermission(Roles.INTERNAL_AFFAIRS_STAFF)
    public String unassignMentee(@Me GuildDB db, @Me Guild guild, @Me DBNation nation, DBNation mentee) {
        ByteBuffer mentorBuf = db.getNationMeta(mentee.getNation_id(), NationMeta.CURRENT_MENTOR);
        DBNation currentMentor = mentorBuf != null ?  DBNation.byId(mentorBuf.getInt()) : null;

        if (currentMentor != null && currentMentor.getActive_m() < 1440) {
            User currentMentorUser = currentMentor.getUser();
        }

        ByteBuffer buf = ByteBuffer.allocate(Integer.SIZE + Long.SIZE);
        buf.putInt(0);
        buf.putLong(System.currentTimeMillis());

        db.setMeta(mentee.getNation_id(), NationMeta.CURRENT_MENTOR, buf.array());

//        db.deleteMeta(mentee.getNation_id(), NationMeta.CURRENT_MENTOR);

        GuildMessageChannel alertChannel = db.getOrNull(GuildDB.Key.INTERVIEW_PENDING_ALERTS);
        if (alertChannel != null) {
            String message = "Mentor (" + nation.getNation() + " | " + nation.getUserDiscriminator() +
                    ") unassigned Mentee (" + mentee.getNation() + " | " + mentee.getUserDiscriminator() + ")"
                    + (currentMentor != null ? " from Mentor (" + currentMentor.getNation() + " | " + currentMentor.getUserDiscriminator() + ")" : "");
            RateLimitUtil.queue(alertChannel.sendMessage(message));
        }

        return "Set " + mentee.getNation() + "'s mentor to null";
    }

    @Command(desc = "Returns the audit excerpt and only lists nations who haven't bought spies")
    @RolePermission(Roles.INTERNAL_AFFAIRS)
    @IsAlliance
    public String hasNotBoughtSpies(@Me IMessageIO channel, @Me GuildDB db, @Me Guild guild, Set<DBNation> nations) {
        int aaId = db.getAlliance_id();
        for (DBNation nation : nations) {
            if (nation.getAlliance_id() != aaId || nation.getPosition() < 1) return "Nation is not a member: " + nation.getNationUrl() + "(see `#position>1,<args>`,";
        }

        boolean result = new SimpleNationList(nations).updateSpies(false);
        if (!result) {
            return "Could not update spies (see " + CM.settings.cmd.create("API_KEY", null).toSlashCommand() + ")";
        }

        long dayCutoff = TimeUtil.getDay() - 2;
        Set<Integer> nationIds = nations.stream().map(DBNation::getNation_id).collect(Collectors.toSet());
        Map<Integer, Integer> lastSpyCounts = Locutus.imp().getNationDB().getLastSpiesByNation(nationIds, dayCutoff);

        List<DBNation> lacking = new ArrayList<>();
        int noData = 0;
        for (DBNation nation : nations) {
            Integer spies = nation.getSpies();
            if (spies == null) {
                noData++;
                continue;
            }
            int spyCap = nation.getSpyCap();
            if (spies >= spyCap) continue;

            Integer lastSpies = lastSpyCounts.get(nation.getNation_id());
            if (lastSpies == null) {
                noData++;
                continue;
            }
            if (lastSpies.equals(spies) && spies < spyCap - 1) {
                lacking.add(nation);
            }
        }

        if (lacking.isEmpty()) {
            return "All nations have spies (no data for " + noData + "/" + nations.size() + " nations)";
        }

        Set<Integer> lackingIds = lacking.stream().map(DBNation::getNation_id).collect(Collectors.toSet());

        StringBuilder response = new StringBuilder("**Nations lacking spies**");
        response.append("\nIDS: " + StringMan.getString(lackingIds));
        for (DBNation nation : lacking) {
            response.append("\n" + nation.getNation() + ": " + nation.getSpies() + "/" + nation.getSpyCap());
        }

        return response.toString();
    }

    @Command(desc = "Assign a mentor to a mentee")
    @RolePermission(Roles.INTERNAL_AFFAIRS)
    public String mentor(@Me JSONObject command, @Me IMessageIO io, @Me GuildDB db, DBNation mentor, DBNation mentee, @Switch("f") boolean force) {
        User menteeUser = mentee.getUser();
        if (menteeUser == null) return "Mentee is not registered";

        if (!force) {
            ByteBuffer mentorBuf = db.getNationMeta(mentee.getNation_id(), NationMeta.CURRENT_MENTOR);
            if (mentorBuf != null) {
                DBNation current = DBNation.byId(mentorBuf.getInt());
                if (current != null && current.getActive_m() < 2880 && current.getVm_turns() == 0) {
                    User currentUser = current.getUser();
                    if (currentUser != null && Roles.MEMBER.has(currentUser, db.getGuild())) {
                        String title = mentee.getNation() + " already has a mentor";
                        StringBuilder body = new StringBuilder();
                        body.append("Current mentor: " + current.getNationUrlMarkup(true));
                        io.create().confirmation(title, body.toString(), command).send();
                        return null;
                    }
                }
            }
        }

        ByteBuffer buf = ByteBuffer.allocate(Integer.SIZE + Long.SIZE);
        buf.putInt(mentor.getNation_id());
        buf.putLong(System.currentTimeMillis());

        db.setMeta(mentee.getNation_id(), NationMeta.CURRENT_MENTOR, buf.array());
        return "Set " + mentee.getNation() + "'s mentor to " + mentor.getNation();
    }

    @Command(desc = "Assign yourself as someone's mentor")
    @RolePermission(Roles.INTERNAL_AFFAIRS_STAFF)
    public String mentee(@Me JSONObject command, @Me IMessageIO io, @Me GuildDB db, @Me DBNation me, DBNation mentee, @Switch("f") boolean force) {
        return mentor(command, io, db, me, mentee, force);
    }

    @Command(desc = "List mentors and their respective mentees", aliases = {"mymentees"})
    @RolePermission(value=Roles.INTERNAL_AFFAIRS)
    public String myMentees(@Me Guild guild, @Me GuildDB db, @Me DBNation me, @Default("*") Set<DBNation> mentees, @Default("2w") @Timediff long timediff) throws InterruptedException, ExecutionException, IOException {
        return listMentors(guild, db, me,Collections.singleton(me), mentees, timediff, db.isWhitelisted(), true, false);
    }

    @Command(desc = "List mentors and their respective mentees", aliases = {"listMentors", "mentors", "mentees"})
    @RolePermission(value=Roles.INTERNAL_AFFAIRS)
    public String listMentors(@Me Guild guild, @Me GuildDB db, @Me DBNation me, @Default("*") Set<DBNation> mentors, @Default("*") Set<DBNation> mentees, @Default("2w") @Timediff long timediff, @Switch("a") boolean includeAudit, @Switch("u") boolean ignoreUnallocatedMembers, @Switch("i") boolean listIdleMentors) throws IOException, ExecutionException, InterruptedException {
        if (includeAudit && !db.isWhitelisted()) return "No permission to include audits";

        IACategory iaCat = db.getIACategory();
        if (iaCat == null) return "No ia category is enabled";

        IACheckup checkup = includeAudit ? new IACheckup(db, db.getAlliance_id(), true) : null;

        Map<DBNation, List<DBNation>> mentorMenteeMap = new HashMap<>();
        Map<DBNation, DBNation> menteeMentorMap = new HashMap<>();
        Map<DBNation, IACategory.SortedCategory> categoryMap = new HashMap<>();
        Map<DBNation, Boolean> passedMap = new HashMap<>();

        for (Map.Entry<DBNation, IAChannel> entry : iaCat.getChannelMap().entrySet()) {
            DBNation mentee = entry.getKey();
            if (!mentees.contains(mentee)) continue;
            User user = mentee.getUser();
            if (user == null) continue;

            boolean graduated = Roles.hasAny(user, guild, Roles.GRADUATED, Roles.INTERNAL_AFFAIRS_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON_LOW_GOV, Roles.MILCOM);

            IAChannel iaChan = iaCat.get(mentee);
            if (iaChan != null) {
                TextChannel myChan = iaChan.getChannel();
                if (myChan != null && myChan.getParentCategory() != null) {
                    IACategory.SortedCategory category = IACategory.SortedCategory.parse(myChan.getParentCategory().getName());
                    if (category != null) {
                        categoryMap.put(mentee, category);
                        if (category == IACategory.SortedCategory.ARCHIVE) {
                            graduated = true;
                        }
                    }
                }
            }
            passedMap.put(mentee, graduated);

            IACategory.AssignedMentor mentor = iaCat.getMentor(mentee, timediff);
            if (mentor != null) {
                mentorMenteeMap.computeIfAbsent(mentor.mentor, f -> new ArrayList<>()).add(mentee);
                menteeMentorMap.put(mentee, mentor.mentee);
            }
        }

        if (mentorMenteeMap.isEmpty()) return "No mentees found";

        List<Map.Entry<DBNation, List<DBNation>>> sorted = new ArrayList<>(mentorMenteeMap.entrySet());
        sorted.sort(new Comparator<Map.Entry<DBNation, List<DBNation>>>() {
            @Override
            public int compare(Map.Entry<DBNation, List<DBNation>> o1, Map.Entry<DBNation, List<DBNation>> o2) {
                return Integer.compare(o2.getValue().size(), o1.getValue().size());
            }
        });

        long requiredMentorActivity = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(20);
        List<Transaction2> transactions = db.getTransactions(requiredMentorActivity, false);

        StringBuilder response = new StringBuilder();

        for (Map.Entry<DBNation, List<DBNation>> entry : sorted) {
            DBNation mentor = entry.getKey();
            List<DBNation> myMentees = new ArrayList<>(entry.getValue());
            Collections.sort(myMentees, new Comparator<DBNation>() {
                @Override
                public int compare(DBNation o1, DBNation o2) {
                    IACategory.SortedCategory c1 = categoryMap.get(o1);
                    IACategory.SortedCategory c2 = categoryMap.get(o2);
                    if (c1 != null && c2 != null) {
                        return Integer.compare(c1.ordinal(), c2.ordinal());
                    }
                    return Integer.compare(c1 == null ? 1 : 0, c2 == null ? 1 : 0);
                }
            });

            int numPassed = (int) myMentees.stream().filter(f -> passedMap.getOrDefault(f, false)).count();
            myMentees.removeIf(f -> passedMap.getOrDefault(f, false));
            if (myMentees.isEmpty()) {
                if (mentors.size() == 1) {
                    response.append("**No current mentors**");
                }
                continue;
            }

            response.append("\n\n**--- Mentor: " + mentor.getNation()).append("**: " + myMentees.size() + "\n");
            response.append("Graduated: " + numPassed + "\n");

            if (mentor.getActive_m() > 4880) {
                response.append("**MENTOR IS INACTIVE:** " + TimeUtil.minutesToTime(mentor.getActive_m())).append("\n");
            }
            if (mentor.getVm_turns() > 0) {
                response.append("**MENTOR IS VM:** " + TimeUtil.turnsToTime(mentor.getVm_turns())).append("\n");
            }
            User mentorUser = mentor.getUser();
            if (mentorUser == null) {
                response.append("**MENTOR IS NOT VERIFIED:** " + TimeUtil.turnsToTime(mentor.getVm_turns())).append("\n");
            } else {
                if (!Roles.MEMBER.has(mentorUser, guild)) {
                    response.append("**MENTOR IS NOT MEMBER:** ").append("\n");
                } else if (!Roles.hasAny(mentorUser, guild, Roles.INTERNAL_AFFAIRS_STAFF, Roles.INTERVIEWER)) {
                    response.append("**MENTOR IS NOT IA STAFF:** ").append("\n");
                }
            }

            long latestTX = 0;
            for (Transaction2 transaction : transactions) {
                if (transaction.note == null || !transaction.note.contains("#incentive")) continue;
                if (transaction.sender_id == mentor.getNation_id()) {
                    latestTX = Math.max(latestTX, transaction.tx_datetime);
                    break;
                }
            }

            if (latestTX == 0) {
                response.append("**MENTOR HAS NOT MENTORED**\n");
            } else if (latestTX < System.currentTimeMillis() - TimeUnit.DAYS.toMillis(8)) {
                response.append("**MENTOR LAST INCENTIVE**: " + TimeUtil.secToTime(TimeUnit.MILLISECONDS, System.currentTimeMillis() - latestTX) + "\n");
            }

            for (DBNation myMentee : myMentees) {
                IAChannel myChan = iaCat.get(myMentee);
                IACategory.SortedCategory category = categoryMap.get(myMentee);
                response.append("`" + myMentee.getNation() + "` <" + myMentee.getNationUrl() + ">\n");
                response.append(" - " + category + " | ");
                if (myChan != null && myChan.getChannel() != null) {
                    GuildMessageChannel tc = myChan.getChannel();
                    response.append(" | " + tc.getAsMention());
                    if (tc.hasLatestMessage()) {
                        long lastMessageTime = net.dv8tion.jda.api.utils.TimeUtil.getTimeCreated(tc.getLatestMessageIdLong()).toEpochSecond() * 1000L;
                        response.append(" | " + TimeUtil.secToTime(TimeUnit.MILLISECONDS, System.currentTimeMillis() - lastMessageTime));
                    }
                }
                response.append("\n - c" + myMentee.getCities() + " mmr[unit]=" + myMentee.getMMR() + " mmr[build]=" + myMentee.getMMRBuildingStr() + " off:" + myMentee.getOff());

                if (includeAudit) {
                    Map<IACheckup.AuditType, Map.Entry<Object, String>> checkupResult = checkup.checkup(myMentee, true, true);
                    checkupResult.entrySet().removeIf(f -> f.getValue() == null || f.getValue().getValue() == null);
                    if (!checkupResult.isEmpty()) {
                        response.append("\n - Failed: [" + StringMan.join(checkupResult.keySet(), ", ") + "]");
                    }
                }
                response.append("\n\n");
            }

        }

        if (db.isValidAlliance()) {
            DBAlliance alliance = db.getAlliance();
            Set<DBNation> members = alliance.getNations(true, 2880, true);
            members.removeIf(f -> !mentees.contains(f));

            List<DBNation> membersUnverified = new ArrayList<>();
            List<DBNation> membersNotOnDiscord = new ArrayList<>();
            List<DBNation> nationsNoIAChan = new ArrayList<>();
            List<DBNation> noMentor = new ArrayList<>();
            for (DBNation member : members) {
                User user = member.getUser();
                if (user == null) {
                    membersUnverified.add(member);
                    continue;
                }
                if (guild.getMember(user) == null) {
                    membersNotOnDiscord.add(member);
                    continue;
                }
                if (Roles.hasAny(user, guild, Roles.GRADUATED, Roles.ADMIN, Roles.INTERNAL_AFFAIRS_STAFF, Roles.INTERNAL_AFFAIRS, Roles.INTERVIEWER, Roles.MILCOM, Roles.MILCOM_ADVISOR, Roles.ECON, Roles.ECON_LOW_GOV, Roles.FOREIGN_AFFAIRS_STAFF, Roles.FOREIGN_AFFAIRS)) {
                    continue;
                }
                if (!passedMap.getOrDefault(member, false)) {
                    if (member.getCities() < 10 && iaCat.get(member) == null) {
                        nationsNoIAChan.add(member);
                        continue;
                    }
                    if (!menteeMentorMap.containsKey(member)) {
                        noMentor.add(member);
                        continue;
                    }
                }
            }

            if (!ignoreUnallocatedMembers) {
                if (listIdleMentors) {
                    if (mentors.size() > 100) {
                        return "Please provide a list of mentors";
                    }
                    List<DBNation> idleMentors = new ArrayList<>();
                    for (DBNation mentor : mentors) {
                        List<DBNation> myMentees = mentorMenteeMap.getOrDefault(mentor, Collections.emptyList());
                        myMentees.removeIf(f -> f.getActive_m() > 4880 || f.getVm_turns() > 0 || passedMap.getOrDefault(f, false));
                        if (myMentees.isEmpty()) {
                            idleMentors.add(mentor);
                        }
                    }
                    if (!idleMentors.isEmpty()) {
                        List<String> memberNames = idleMentors.stream().map(DBNation::getNation).collect(Collectors.toList());
                        response.append("\n**Idle mentors**").append("\n - ").append(StringMan.join(memberNames, "\n - "));
                    }
                }

                if (!membersUnverified.isEmpty()) {
                    List<String> memberNames = membersUnverified.stream().map(DBNation::getNation).collect(Collectors.toList());
                    response.append("\n**Unverified members**").append("\n - ").append(StringMan.join(memberNames, "\n - "));
                }
                if (!membersNotOnDiscord.isEmpty()) {
                    List<String> memberNames = membersNotOnDiscord.stream().map(DBNation::getNation).collect(Collectors.toList());
                    response.append("\n**Members left discord**").append("\n - ").append(StringMan.join(memberNames, "\n - "));
                }
                if (!nationsNoIAChan.isEmpty()) {
                    List<String> memberNames = nationsNoIAChan.stream().map(DBNation::getNation).collect(Collectors.toList());
                    response.append("\n**No interview channel**").append("\n - ").append(StringMan.join(memberNames, "\n - "));
                }
            }
            if (!noMentor.isEmpty()) {
                List<String> memberNames = noMentor.stream().map(DBNation::getNation).collect(Collectors.toList());
                response.append("\n**No mentor**").append("\n - ").append(StringMan.join(memberNames, "\n - "));
            }
        }

        response.append("\n\nTo assign a nation as your mentee, use " + CM.interview.mentee.cmd.toSlashMention());
        return response.toString();
    }

    @Command(desc = "Ranking of nations by how many advertisements they have registered (WIP)")
    @RolePermission(value = {Roles.INTERNAL_AFFAIRS,Roles.ECON}, any=true)
    public String adRanking(@Me User author, @Me GuildDB db, @Me IMessageIO io, @Me JSONObject command, @Switch("u") boolean uploadFile) {
        Role role = Roles.MEMBER.toRole(db);
        if (role == null) throw new IllegalArgumentException("No member role is set via " + CM.role.setAlias.cmd.toSlashMention() + "");

        Map<DBNation, Integer> rankings = new HashMap<>();

        for (Member member : db.getGuild().getMembers()) {
            DBNation nation = DiscordUtil.getNation(member.getUser());
            if (nation == null) continue;
            ByteBuffer countBuf = nation.getMeta(NationMeta.RECRUIT_AD_COUNT);
            if (countBuf == null) continue;
            rankings.put(nation, countBuf.getInt());
        }
        if (rankings.isEmpty()) return "No rankings founds";
        new SummedMapRankBuilder<>(rankings).sort().nameKeys(DBNation::getName).build(author, io, command, "Most advertisements", uploadFile);
        return null;
    }

    @Command
    @RolePermission(value = {Roles.INTERNAL_AFFAIRS,Roles.ECON}, any=true)
    public String incentiveRanking(@Me GuildDB db, @Me IMessageIO io, @Me JSONObject command, @Timestamp long timestamp) {
        List<Transaction2> transactions = db.getTransactions(timestamp, false);

        Map<String, Map<DBNation, Integer>> incentivesByGov = new HashMap<>();

        for (Transaction2 transaction : transactions) {
            if (transaction.note == null || !transaction.note.contains("#incentive")) continue;
            Map<String, String> notes = PnwUtil.parseTransferHashNotes(transaction.note);
            String incentive = notes.get("#incentive");
            DBNation member = DBNation.byId(transaction.banker_nation);
            DBNation gov = DBNation.byId((int) transaction.sender_id);

            if (gov != null) {
                Map<DBNation, Integer> byIncentive = incentivesByGov.computeIfAbsent(incentive, f -> new HashMap<>());
                byIncentive.put(gov, byIncentive.getOrDefault(gov, 0) + 1);
            }
        }

        for (Map.Entry<String, Map<DBNation, Integer>> entry : incentivesByGov.entrySet()) {
            String title = entry.getKey();
            new SummedMapRankBuilder<>(entry.getValue())
                    .sort()
                    .nameKeys(f -> f.getNation())
                    .build(io, command, title, true);
        }
        return null;
    }



    @Command
    @RolePermission(Roles.MAIL)
    @IsAlliance
    public String reply(@Me GuildDB db, @Me DBNation me, @Me User author, @Me IMessageIO channel, DBNation receiver, String url, String message, @Switch("s") DBNation sender) throws IOException {
        if (!url.contains("message/id=")) return "URL must be a message url";
        int messageId = Integer.parseInt(url.split("=")[1]);

        Auth auth;
        if (sender == null) {
            auth = db.getAuth();
        } else {
            auth = sender.getAuth(null);
            GuildDB authDB = Locutus.imp().getGuildDB(sender.getAlliance_id());
            boolean hasPerms = (Roles.INTERNAL_AFFAIRS.hasOnRoot(author)) || (authDB != null && Roles.INTERNAL_AFFAIRS.has(author, authDB.getGuild()));
            if (!hasPerms) return "You do not have permission to reply to this message";
        }
        if (auth == null) throw new IllegalArgumentException("No authentication found");


        String result = new MailRespondTask(auth, receiver.getLeader(), messageId, message, null).call();
        return "Mail: " + result;
    }

    @Command(desc = "Generate a list of nations and their expected raid loot\n" +
            "e.g. `{prefix}lootValueSheet #cities<10,#position>1,#active_m<2880,someAlliance`")
    @RolePermission(Roles.MILCOM)
    public String lootValueSheet(@Me IMessageIO io, @Me GuildDB db, Set<DBNation> attackers, @Switch("s") SpreadSheet sheet) throws GeneralSecurityException, IOException {
        attackers.removeIf(f -> f.getActive_m() > 10000);
        attackers.removeIf(f -> f.getVm_turns() > 0);
        if (attackers.size() > 200) return "Too many nations";
        if (sheet == null) {
            sheet = SpreadSheet.create(db, GuildDB.Key.CURRENT_LOOT_SHEET);
        }

        List<String> header = new ArrayList<>(Arrays.asList("nation", "cities", "avg_infra", "mmr (build)", "soldiers", "tanks", "aircraft", "ships", "off", "off_inactive", "beiged", "lootInactive", "daysSinceDeposit"));
        sheet.setHeader(header);

        Map<DBNation, List<Object>> rows = new HashMap<>();
        Map<DBNation, Double> loot = new HashMap<>();

        for (DBNation nation : attackers) {
            List<Object> row = new ArrayList<>();
            row.add(MarkupUtil.sheetUrl(nation.getNation(), PnwUtil.getUrl(nation.getNation_id(), false)));
            row.add(nation.getCities() + "");
            row.add(nation.getAvg_infra() + "");
            row.add(nation.getMMRBuildingStr() + "");
            row.add(nation.getSoldiers() + "");
            row.add(nation.getTanks() + "");
            row.add(nation.getAircraft() + "");
            row.add(nation.getShips() + "");
            row.add(nation.getOff() + "");

            List<DBWar> wars = nation.getActiveOffensiveWars();
            int offInactive = 0;
            double lootInactive = 0;
            for (DBWar war : wars) {
                DBNation other = war.getNation(false);
                boolean inactive = other.getActive_m() > TimeUnit.DAYS.toMinutes(5);
                if (inactive) {
                    offInactive++;
                    lootInactive += other.lootTotal();
                }
            }
            row.add(offInactive + "");
            row.add(nation.isBeige() + "");
            row.add(lootInactive);

            long lastDepoTime = nation.lastBankDeposit();
            if (lastDepoTime == 0) {
                row.add("NEVER");
            } else {
                row.add((System.currentTimeMillis() - lastDepoTime) / TimeUnit.DAYS.toMillis(1));
            }
            rows.put(nation, row);
            loot.put(nation, lootInactive);
        }

        List<Map.Entry<DBNation, Double>> sorted = new ArrayList<>(loot.entrySet());
        Collections.sort(sorted, Comparator.comparingDouble(Map.Entry::getValue));
        for (Map.Entry<DBNation, Double> entry : sorted) {
            sheet.addRow(rows.get(entry.getKey()));
        }
        sheet.clearAll();
        sheet.set(0, 0);

        sheet.attach(io.create()).send();
        return null;
    }

    @Command
    public String mail(@Me DBNation me, @Me JSONObject command, @Me GuildDB db, @Me IMessageIO channel, @Me User author, Set<DBNation> nations, String subject, @TextArea String message, @Switch("f") boolean confirm, @Switch("l") boolean notLocal, @Switch("a") String apiKey) throws IOException {
        message = MarkupUtil.transformURLIntoLinks(message);

        ApiKeyPool.ApiKey myKey = me.getApiKey(false);

        ApiKeyPool key = null;
        if (apiKey != null) {
            Integer nation = Locutus.imp().getDiscordDB().getNationFromApiKey(apiKey);
            if (nation == null) return "Invalid API key";
            key = ApiKeyPool.create(nation, apiKey);
        }
        if (key == null) {
            if ((notLocal || myKey == null)) {
                if (!Roles.MAIL.has(author, db.getGuild())) {
                    return "You do not have the role `MAIL` (see " + CM.role.setAlias.cmd.toSlashMention() + " OR use" + CM.credentials.addApiKey.cmd.toSlashMention() + " to add your own key";
                }
                key = db.getMailKey();
            } else if (myKey != null) {
                key = ApiKeyPool.builder().addKey(myKey).build();
            }
        }
        if (key == null){
            return "No api key found. Please use" + CM.credentials.addApiKey.cmd.toSlashMention() + "";
        }

        if (!confirm) {
            String title = "Send " + nations.size() + " messages";

            Set<Integer> alliances = new LinkedHashSet<>();
            for (DBNation nation : nations) alliances.add(nation.getAlliance_id());
            String embedTitle = title + " to ";
            if (nations.size() == 1) {
                DBNation nation = nations.iterator().next();
                embedTitle += nations.size() == 1 ? nation.getName() + " | " + nation.getAllianceName() : "nations";
            } else {
                embedTitle += " nations";
            }
            if (alliances.size() != 1) embedTitle += " in " + alliances.size() + " alliances";

            StringBuilder body = new StringBuilder();
            body.append("subject: " + subject + "\n");
            body.append("body: ```" + message + "```");

            channel.create().confirmation(embedTitle, body.toString(), command, "confirm").send();
            return null;
        }

        if (!Roles.ADMIN.hasOnRoot(author)) {
            message += "\n\n<i>This message was sent by: " + author.getName() + "</i>";
        }

        for (DBNation nation : nations) {
            String subjectF = DiscordUtil.format(db.getGuild(), null, nation.getUser(), nation, subject);
            String messageF = DiscordUtil.format(db.getGuild(), null, nation.getUser(), nation, message);
            channel.send(nation.sendMail(key, subjectF, messageF) + "");
        }

        return "Done sending mail.";
    }

    @Command(desc = "List or set your tax bracket.\n" +
            "Notes:\n" +
            " - Internal tax rate affects what portion of taxes are not included in `{prefix}deposits` (typically used when 100/100 taxes)\n" +
            " - Set the alliance internal tax rate with: `{prefix}KeyStore TAX_BASE` (retroactive)\n" +
            " - This command is not retroactive and overrides the alliance internal taxrate", aliases = {"SetBracket", "SetTaxes", "SetTaxRate", "SetTaxBracket"})
    @RolePermission(Roles.MEMBER)
    @IsAlliance
    @IsAuthenticated
    public String setBracket(@Me GuildDB db, @Me User author, @Me DBNation me, @Filter("{guild_alliance_id}") Set<DBNation> nations, @Default TaxBracket bracket, @Default TaxRate internalRate) throws IOException {
        DBNation single = nations.size() == 1 ? nations.iterator().next() : null;

        boolean isGov = Roles.ECON_LOW_GOV.has(author, db.getGuild()) || Roles.INTERNAL_AFFAIRS.has(author, db.getGuild());
        if (!isGov) {
            if (db.getOrNull(GuildDB.Key.MEMBER_CAN_SET_BRACKET) != Boolean.TRUE) return "Only ECON can set member brackets. (See also " + CM.settings.cmd.create(GuildDB.Key.MEMBER_CAN_SET_BRACKET.name(), null) + ")";
            if (!me.equals(single)) return "You are only allowed to set your own tax rate";
        }
        if (internalRate != null && !isGov) {
            return "You are only allowed to set your tax bracket";
        }

        int aaId = db.getOrThrow(GuildDB.Key.ALLIANCE_ID);
        Auth auth = db.getAuth(AlliancePermission.TAX_BRACKETS);
        if (auth == null) {
            return "No authentication with TAX_BRACKETS enabled for this guild";
        }

        if (internalRate != null) {
            if (internalRate.money < -1 || internalRate.money > 100) {
                return "Invalid internal taxrate: " + internalRate;
            }
            if (internalRate.resources < -1 || internalRate.resources > 100) {
                return "Invalid internal taxrate: " + internalRate;
            }
        }

        Map<Integer, TaxBracket> brackets = auth.getTaxBrackets();

        if (bracket == null) {
            StringBuilder response = new StringBuilder();
            response.append("Brackets:");
            for (Map.Entry<Integer, TaxBracket> entry : brackets.entrySet()) {
                bracket = entry.getValue();
                String url = bracket.getUrl();
                response.append("\n - " + MarkupUtil.markdownUrl("#" + bracket.taxId, url) + ": " + bracket.moneyRate + "/" + bracket.rssRate + " (" + bracket.getNations().size() + " nations) - " + bracket.getName());
            }
            throw new IllegalArgumentException(response.toString());
        }


        StringBuilder response = new StringBuilder();
        for (DBNation nation : nations) {
            if (aaId != nation.getAlliance_id()) {
                response.append(nation.getNation() + " is not in " + aaId).append("\n");
                continue;
            }
            TaxRate taxBase = db.getHandler().getInternalTaxrate(nation.getNation_id());
            if (isGov) taxBase = new TaxRate(-1, -1);

            if (taxBase != null && !Roles.INTERNAL_AFFAIRS.has(author, db.getGuild())) {
                if (bracket.moneyRate < taxBase.money || bracket.rssRate < taxBase.resources) {
                    response.append(nation.getNation() + ": The minimum taxrate you can set is: " + taxBase).append("\n");
                    continue;
                }
            }

            if (!isGov) {
                double depo = me.getNetDepositsConverted(db);
                if (depo < -200_000_000) {
                    if (bracket.moneyRate < 100 || bracket.rssRate < 100) {
                        response.append(nation.getNation() + ": Nations in >200m debt must have a gov change their tax rate").append("\n");
                        continue;
                    }
                }
            }

            if (internalRate != null) {
                db.setMeta(nation.getNation_id(), NationMeta.TAX_RATE, new byte[]{(byte) internalRate.money, (byte) internalRate.resources});
                response.append("Set internal taxrate to " + internalRate + "\n");
            }

            response.append(nation.setTaxBracket(bracket, auth));
        }
        response.append("\nDone!");
        return response.toString();
    }

    private static final PassiveExpiringMap<Long, Integer> demotions = new PassiveExpiringMap<Long, Integer>(60, TimeUnit.MINUTES);;

    @Command(desc = "Set the rank of a player in the alliance.", aliases = {"rank", "setrank", "rankup"})
    @RolePermission(value = {Roles.INTERNAL_AFFAIRS, Roles.INTERNAL_AFFAIRS_STAFF}, any = true)
    @IsAlliance
    public static String setRank(@Me User author, @Me IMessageIO channel, @Me GuildDB db, @Me DBNation me, DBNation nation, DBAlliancePosition position, @Switch("f") boolean force, @Switch("d") boolean doNotUpdateDiscord) throws IOException {
        int allianceId = position.getAlliance_id();
        if (allianceId <= 0) allianceId = db.getAlliance_id();
        if (!db.getAllianceIds(true).contains(nation.getAlliance_id())) return "This guild is not in the same alliance as " + nation.getAllianceName();

        if ((nation.getAlliance_id() != allianceId || nation.getAlliance_id() != position.getAlliance_id()) && position != DBAlliancePosition.APPLICANT && position != DBAlliancePosition.REMOVE) {
            return "That nation is not in the alliance: " + PnwUtil.getName(allianceId, true);
        }
        // Cannot promote above your own permissions
        DBAlliancePosition myPosition = me.getAlliancePosition();
        DBAlliancePosition nationPosition = nation.getAlliancePosition();
        if (!Roles.ADMIN.hasOnRoot(author)) {
            if (me.getAlliance_id() != allianceId || myPosition == null) {
                // cannot promote above officer unless admin
                if (!Roles.ADMIN.has(author, db.getGuild())) {
                    if (position.hasAnyOfficerPermissions()) {
                        return "You do not have permission to grant permissions you currently do not posses in the alliance";
                    }
                }
            } else {
                if (position.getPosition_level() > myPosition.getPosition_level()) {
                    return "You do not have permission to promote above your level. (" + position.getPosition_level() + " is above " + myPosition.getPosition_level() + ")";
                }
                for (AlliancePermission perm : position.getPermissions()) {
                    if (!myPosition.hasPermission(perm) && (nationPosition == null || !nationPosition.hasAllPermission(perm))) {
                        return "You can not grant permissions you do not posses (lacking " + perm + ")";
                    }
                }
                if (nationPosition != null) {
                    for (AlliancePermission perm : nationPosition.getPermissions()) {
                        if (!myPosition.hasPermission(perm) && !position.hasPermission(perm)) {
                            return "You can not remove permissions you do not posses (lacking " + perm + ")";
                        }
                    }
                }
            }

            if (position == DBAlliancePosition.REMOVE) {
                if (!Roles.ADMIN.has(author, db.getGuild())) {
                    if (nation.active_m() < 2880) {
                        return "You do not have the permission (`ADMIN`) to remove active members (set them to applicant first)";
                    }
                    if (nation.active_m() < 10000) {
                        int currentDemotions = demotions.getOrDefault(author.getIdLong(), 0);
                        if (currentDemotions > 2) {
                            return "Please get an admin to demote multiple nations, or do so ingame. " + Roles.ADMIN.toRole(db.getGuild());
                        }
                        demotions.put(author.getIdLong(), currentDemotions + 1);
                    }
                }
            }
        }
        // Cannot promote to leader, or any leader perms -> done
        if ((position.hasAnyAdminPermission() || position.getRank().id >= Rank.HEIR.id) && !Roles.ADMIN.hasOnRoot(author)) {
            return "You cannot promote to leadership positions (do this ingame)";
        }
        if ((nationPosition != null && nationPosition.hasAnyAdminPermission()) || nation.getPositionEnum().id >= Rank.HEIR.id) {
            return "You cannot adjust the position of admins (do that ingame)";
        }

        List<AlliancePermission> requiredPermissions = new ArrayList<>();
        if (position.hasAnyOfficerPermissions() || nationPosition != null) requiredPermissions.add(AlliancePermission.CHANGE_PERMISSIONS);
        if (nationPosition == null && nation.getPositionEnum() == Rank.APPLICANT) requiredPermissions.add(AlliancePermission.ACCEPT_APPLICANTS);
        if (position == DBAlliancePosition.REMOVE || position == DBAlliancePosition.APPLICANT) requiredPermissions.add(AlliancePermission.REMOVE_MEMBERS);
        Auth auth = db.getAuth(requiredPermissions.toArray(new AlliancePermission[0]));
        if (auth == null) return "No auth for this guild found for: " + StringMan.getString(requiredPermissions);
        if (auth.getNationId() == nation.getNation_id()) return "You cannot change position of the nation connected to Locutus.";

        User discordUser = nation.getUser();

        if (nationPosition == null && nation.getPositionEnum() == Rank.APPLICANT && db.isWhitelisted()) {
            if (!force) {
                List<String> checks = new ArrayList<>();
                if (nation.isGray()) {
                    checks.add("Nation is gray (use `-f` to override this)");
                }
                if (nation.getCities() < 3) {
                    checks.add("Nation has not bought up to 3 cities (use `-f` to override this)");
                }
                if (nation.getCities() < 10 && nation.getOff() < 5 && db.hasCoalitionPermsOnRoot(Coalition.RAIDPERMS)) {
                    checks.add("Nation has not declared up to 5 raids ( use `-f` to override this)");
                }
                if (nation.getCities() > 3 && nation.getCities() < 10 && nation.getSoldierPct() < 0.25) {
                    checks.add("Nation has not bought soldiers (use `-f` to override this)");
                }

                if (nation.getCities() >= 10 && nation.getAircraftPct() < 0.18) {
                    checks.add("Nation has not bought aircraft (use `-f` to override this)");
                }
                if (nation.getCities() == 10 && nation.getSoldierPct() < 0.25 && nation.getTankPct() < 0.25) {
                    checks.add("Nation has not bought tanks or soldiers (use `-f` to override this)");
                }
                if (nation.getCities() <= 5 && !nation.getMMRBuildingStr().startsWith("5")) {
                    checks.add("Nation does not have 5 barracks (use `-f` to override this)");
                }
                if (nation.getCities() >= 10) {
                    String mmr = nation.getMMRBuildingStr();
                    if (!mmr.matches("5.5.") && !mmr.matches(".[2-5]5.")) {
                        checks.add("Nation is on insufficient MMR (use `-f` to override this)");
                    }
                }

                if (!checks.isEmpty()) {
                    return "The following checks have failed:\n" + StringMan.join(checks, "\n - ");
                }

                if (db.getOffshore() != null) {
                    String title = "Disburse 3 days";
                    String body = "Use this once they have a suitable city build & color to send resources for the next 3 days";

                    CM.transfer.raws cmd = CM.transfer.raws.cmd.create(nation.getNation_id() + "", "3", "#deposit", null, null, null, "true");
                    channel.create().embed(title, body)
                                    .commandButton(cmd, "Disburse 3 days")
                                            .send();
                }
            }
        }

        StringBuilder response = new StringBuilder();
        if (discordUser != null && !doNotUpdateDiscord) {
            Member member = db.getGuild().getMember(discordUser);
            Role role = Roles.MEMBER.toRole(db.getGuild());
            if (member != null && role != null) {
                try {
                    if (nationPosition == null && nation.getPositionEnum() == Rank.APPLICANT) {
                        RateLimitUtil.queue(db.getGuild().addRoleToMember(member, role));
                    } else if (position == DBAlliancePosition.APPLICANT || position == DBAlliancePosition.REMOVE) {
                        RateLimitUtil.queue(db.getGuild().removeRoleFromMember(member, role));
                    }
                } catch (HierarchyException e) {
                    response.append(e.getMessage() + "\n");
                }
            }
        }

        String result = auth.setRank(nation, position);

        if (result.contains("Set player rank ingame.") && nationPosition == null) {
            db.getHandler().onSetRank(author, channel, nation, position);
        }
        response.append("\n(Via Account: " + auth.getNation().getNation() + ")");
        response.append(result);
        response.append("\nSee also " + CM.self.list.cmd.toSlashMention() + " / " + CM.role.add.cmd.toSlashMention());
        return response.toString();
    }


    @Command(desc = "Get the top X inactive players. Use `-a` to include applicants")
    public void inactive(@Me IMessageIO channel, @Me JSONObject command, Set<DBNation> nations, @Default("7") int days, @Switch("a") boolean includeApplicants, @Switch("v") boolean includeVacationMode, @Switch("p") int page) {
        if (!includeApplicants) nations.removeIf(f -> f.getPosition() <= 1);
        if (!includeVacationMode) nations.removeIf(f -> f.getVm_turns() > 0);

        List<DBNation> nationList = new ArrayList<>(nations);
        nationList.sort((o1, o2) -> Integer.compare(o2.getActive_m(), o1.getActive_m()));

        int perPage = 5;

        String title = "Inactive nations";
        List<String> results = nationList.stream().map(f -> f.toMarkdown()).collect(Collectors.toList());
        channel.create().paginate(title, command, page, perPage, results).send();

    }

    @Command(desc = "Set the interview category for an interview channel", aliases = {"iacat", "interviewcat", "interviewcategory"})
    @RolePermission(value = {Roles.INTERNAL_AFFAIRS, Roles.INTERNAL_AFFAIRS_STAFF}, any=true)
    public String iaCat(@Me IMessageIO channel, @Filter("interview.") Category category, @Me GuildDB db) {
        if (!(channel instanceof ICategorizableChannel)) return "This channel cannot be categorized";
        ICategorizableChannel tc = ((ICategorizableChannel) channel);
        RateLimitUtil.queue(tc.getManager().setParent(category));
        return "Moved " + tc.getAsMention() + " to " + category.getName();
    }

    @Command(desc = "Bulk send the result of a Locutus command to a list of nations")
    @RolePermission(value=Roles.ADMIN)
    public String mailCommandOutput(NationPlaceholders placeholders, ValueStore store, @Me GuildDB db, @Me Guild guild, @Me User author, @Me IMessageIO channel, Set<DBNation> nations, String subject, @TextArea String command, @TextArea String body, @Switch("s") SpreadSheet sheet) throws IOException, GeneralSecurityException {
        if (sheet == null) {
            sheet = SpreadSheet.create(db, GuildDB.Key.MAIL_RESPONSES_SHEET);
        }

        List<String> header = new ArrayList<>(Arrays.asList(
                "nation",
                "alliance",
                "mail-id",
                "subject",
                "body"
        ));

        sheet.setHeader(header);
        Map<DBNation, Map.Entry<CommandResult, String>> errors = new LinkedHashMap<>();

        if (nations.size() > 300 && !Roles.ADMIN.hasOnRoot(author)) {
            return "Max allowed: 300 nations.";
        }

        if (nations.isEmpty()) return "No nations specified";

        Future<IMessageBuilder> msgFuture = channel.send("Please wait...");
        long start = System.currentTimeMillis();

        int success = 0;
        for (DBNation nation : nations) {
            if (-start + (start = System.currentTimeMillis()) > 5000) {
                try {
                    msgFuture.get().clear().append("Running for: " + nation.getNation() + "...").send();
                } catch (InterruptedException | ExecutionException e) {
                    // ignore
                }
            }
            User nationUser = nation.getUser();
            String subjectFormat = placeholders.format(store, subject);
            String bodyFormat = placeholders.format(store, body);

            Map.Entry<CommandResult, String> response = nation.runCommandInternally(guild, nationUser, command);
            CommandResult respType = response.getKey();
            String cmdMsg = response.getValue();

            if (respType == CommandResult.SUCCESS) {
                if (!bodyFormat.isEmpty()) {
                    bodyFormat = MarkupUtil.markdownToHTML(bodyFormat) + "<br>";
                }
                bodyFormat += cmdMsg;

                header.set(0, MarkupUtil.sheetUrl(nation.getNation(), nation.getNationUrl()));
                header.set(1, MarkupUtil.sheetUrl(nation.getAllianceName(), nation.getAllianceUrl()));
                header.set(2, "");
                header.set(3, subjectFormat);
                header.set(4, bodyFormat);

                sheet.addRow(header);
                success++;
            } else{
                if (cmdMsg == null) {
                    respType = CommandResult.NO_RESPONSE;
                } else if (cmdMsg.isEmpty()) {
                    respType = CommandResult.EMPTY_RESPONSE;
                }
                errors.put(nation, new AbstractMap.SimpleEntry<>(respType, cmdMsg));
            }
        }

        sheet.clearAll();
        sheet.set(0, 0);

        List<String> errorMsgs = new ArrayList<>();
        if (!errors.isEmpty()) {
            for (Map.Entry<DBNation, Map.Entry<CommandResult, String>> entry : errors.entrySet()) {
                DBNation nation = entry.getKey();
                Map.Entry<CommandResult, String> error = entry.getValue();
                errorMsgs.add(nation.getNation() + " -> " + error.getKey() + ": " + error.getValue());
            }
        }

        String title = "Send " + success + " messages";
        StringBuilder embed = new StringBuilder();
        IMessageBuilder msg = channel.create();
        sheet.attach(msg, embed, false, 0);
        embed.append("\nPress `confirm` to confirm");
        CM.mail.sheet cmd = CM.mail.sheet.cmd.create(sheet.getURL(), null);

        msg.confirmation(title, embed.toString(), cmd).send();

        if (errorMsgs.isEmpty()) return null;
        return "Errors\n - " + StringMan.join(errorMsgs, "\n - ");
    }

    @Command(desc = "Bulk send ingame mail from a google sheet\n" +
            "Columns: nation, subject, body")
    @HasApi
    @RolePermission(Roles.ADMIN)
    public String mailSheet(@Me GuildDB db, @Me JSONObject command, @Me IMessageIO io, @Me User author, SpreadSheet sheet, @Switch("f") boolean confirm) {
        List<List<Object>> data = sheet.loadValues();

        List<Object> nationNames = sheet.findColumn(0, "nation", "id");
        List<Object> subjects = sheet.findColumn("subject");
        List<Object> bodies = sheet.findColumn("message", "body", "content");
        if (nationNames == null || nationNames.size() <= 1) return "No column found: `nation`";
        if (subjects == null) return "No column found: `subjects`";
        if (bodies == null) return "No column found: `message`";

        Set<Integer> alliances = new HashSet<>();
        int inactive = 0;
        int vm = 0;
        int noAA = 0;
        int applicants = 0;

        Map<DBNation, Map.Entry<String, String>> messageMap = new LinkedHashMap<>();

        for (int i = 1; i < nationNames.size(); i++) {
            Object nationNameObj = nationNames.get(i);
            if (nationNameObj == null) continue;
            String nationNameStr = nationNameObj.toString();
            if (nationNameStr.isEmpty()) continue;

            DBNation nation = DiscordUtil.parseNation(nationNameStr);
            if (nation == null) return "Invalid nation: `" + nationNameStr + "`";

            Object subjectObj = subjects.get(i);
            Object messageObj = bodies.get(i);
            if (subjectObj == null) {
                return "No subject found for: `" + nation.getNation() + "`";
            }
            if (messageObj == null) {
                return "No body found for: `" + nation.getNation() + "`";
            }
            Map.Entry<String, String> msgEntry = new AbstractMap.SimpleEntry<>(
                    subjectObj.toString(), messageObj.toString());

            messageMap.put(nation, msgEntry);

            // metrics
            alliances.add(nation.getAlliance_id());
            if (nation.getActive_m() > 7200) inactive++;
            if (nation.getVm_turns() > 0) vm++;
            if (nation.getAlliance_id() == 0) noAA++;
            if (nation.getPosition() <= 1) applicants++;
        }

        if (messageMap.size() > 1000 && !Roles.ADMIN.hasOnRoot(author)) {
            return "Max allowed: 1000 messages.";
        }

        if (!confirm) {
            String title = "Send " + messageMap.size() + " to nations in " + alliances.size() + " alliances";
            StringBuilder body = new StringBuilder("Messages:");
            IMessageBuilder msg = io.create();
            sheet.attach(msg, body, false, 0);

            if (inactive > 0) body.append("Inactive Receivers: " + inactive + "\n");
            if (vm > 0) body.append("vm Receivers: " + vm + "\n");
            if (noAA > 0) body.append("No Alliance Receivers: " + noAA + "\n");
            if (applicants > 0) body.append("applicant receivers: " + applicants + "\n");

            body.append("\nPress to confirm");
            msg.confirmation(title, body.toString(), command, "confirm").send();
            return null;
        }

        ApiKeyPool keys = db.getMailKey();
        if (keys == null) throw new IllegalArgumentException("No API_KEY set, please use " + CM.credentials.addApiKey.cmd.toSlashMention() + "");

        io.send("Sending to " + messageMap.size() + " nations in " + alliances.size() + " alliances. Please wait.");
        List<String> response = new ArrayList<>();
        int errors = 0;
        for (Map.Entry<DBNation, Map.Entry<String, String>> entry : messageMap.entrySet()) {
            DBNation nation = entry.getKey();
            Map.Entry<String, String> msgEntry = entry.getValue();
            String subject = msgEntry.getKey();
            String body = msgEntry.getValue();

            String result;
            try {
                JsonObject json = nation.sendMail(keys, subject, body);
                result = json + "";
            } catch (IOException e) {
                errors++;
                if (errors > 50) {
                    throw new IllegalArgumentException(">50 Errors. Aborting at `" + nation.getNation() + "`");
                }
                e.printStackTrace();
                result = "IOException: " + e.getMessage();
            }
            response.add(nation.getNationUrl() + " -> " + result);
        }
        return "Done!\n - " + StringMan.join(response, "\n - ");
    }

    private String channelMemberInfo(Integer aaId, Role memberRole, Member member) {
        DBNation nation = DiscordUtil.getNation(member.getUser());

        StringBuilder response = new StringBuilder(member.getUser().getName() + "#" + member.getUser().getDiscriminator());
        response.append(" | `" + member.getAsMention() + "`");
        if (nation != null) {
            response.append(" | N:" + nation.getNation());
            if (aaId != null && aaId != nation.getNation_id()) {
                response.append(" | AA:" + nation.getAllianceName());
            }
            if (nation.getPosition() <= 1) {
                response.append(" | applicant");
            }
            if (nation.getVm_turns() > 0) {
                response.append(" | vm=" + TimeUtil.turnsToTime(nation.getVm_turns()));
            }
            if (nation.getActive_m() > 10000) {
                response.append(" | inactive=" + TimeUtil.minutesToTime(nation.getActive_m()));
            }
        }
        if (aaId == null && !member.getRoles().contains(memberRole)) {
            response.append(" | No Member Role");
        }
        return response.toString();
    }

    @Command(desc = "List members in a channel")
    @RolePermission(value = {Roles.INTERNAL_AFFAIRS, Roles.ADMIN}, any = true)
    public String channelMembers(@Me GuildDB db, MessageChannel channel) {
        Integer aaId = db.getOrNull(GuildDB.Key.ALLIANCE_ID);
        Role memberRole = Roles.MEMBER.toRole(db.getGuild());

        List<Member> members = (channel instanceof IMemberContainer) ? ((IMemberContainer) channel).getMembers() : Collections.emptyList();
        members.removeIf(f -> f.getUser().isBot() || f.getUser().isSystem());

        List<String> results = new ArrayList<>();
        for (Member member : members) {
            results.add(channelMemberInfo(aaId, memberRole, member));
        }
        if (results.isEmpty()) return "No users found";
        return StringMan.join(results, "\n");
    }

    @Command(desc = "List all channels and what members have access to each")
    @RolePermission(value = {Roles.ADMIN}, any = true)
    public String allChannelMembers(@Me GuildDB db) {
        Integer aaId = db.getOrNull(GuildDB.Key.ALLIANCE_ID);
        Role memberRole = Roles.MEMBER.toRole(db.getGuild());

        StringBuilder result = new StringBuilder();

        for (Category category : db.getGuild().getCategories()) {
            result.append("**" + category.getName() + "**\n");
            for (TextChannel GuildMessageChannel : category.getTextChannels()) {
                result.append(GuildMessageChannel.getAsMention() + "\n");

                for (Member member : GuildMessageChannel.getMembers()) {
                    result.append(channelMemberInfo(aaId, memberRole, member) + "\n");
                }
            }
        }
        return result.toString();
    }

    @Command(desc = "List channel a member has access to")
    @RolePermission(value = {Roles.INTERNAL_AFFAIRS, Roles.ADMIN}, any = true)
    public String memberChannels(@Me Guild guild, Member member) {
        List<String> channels = guild.getTextChannels().stream().filter(f -> f.getMembers().contains(member)).map(f -> f.getAsMention()).collect(Collectors.toList());
        User user = member.getUser();
        return user.getName() + "#" + user.getDiscriminator() + " has access to:\n" +
            StringMan.join(channels, "\n");
    }

    @Command(desc = "Open a channel")
    @RolePermission(Roles.MEMBER)
    public String open(@Me GuildDB db, @Me User author, @Me Guild guild, @Me IMessageIO channel, @Default Category category) {
        if (!(channel instanceof TextChannel)) {
            return "Not a text channel";
        }
        String closeChar = "\uD83D\uDEAB";
        TextChannel tc = (TextChannel) channel;
        Category channelCategory = tc.getParentCategory();

        if (channelCategory != null && channelCategory.getName().toLowerCase().contains("archive") && category == null) {
            throw new IllegalArgumentException("Please provide a category to move this channel to");
        }
        if (category != null && channelCategory != null && !channelCategory.getName().toLowerCase().contains("archive") && !Roles.INTERNAL_AFFAIRS_STAFF.has(author, guild)) {
            throw new IllegalArgumentException("You do not have permission to move this channel: INTERNAL_AFFAIRS_STAFF");
        }

        if (tc.getName().contains(closeChar)) {
            String newName = tc.getName().replace(closeChar, "");
            RateLimitUtil.queue(tc.getManager().setName(newName));
        }
        if (category != null) {
            RateLimitUtil.queue(tc.getManager().setParent(category));
        }
        return "Reopened channel";
    }

    @Command(desc = "Close a channel")
    @RolePermission(value = {Roles.INTERNAL_AFFAIRS, Roles.MILCOM, Roles.ECON, Roles.ECON_LOW_GOV}, any=true)
    public String close(@Me GuildDB db, @Me GuildMessageChannel channel, @Switch("f") boolean forceDelete) {
        if (!(channel instanceof TextChannel)) {
            return "Not a text channel";
        }
        String closeChar = "\uD83D\uDEAB";
        long expireTime = TimeUnit.HOURS.toMillis(24);

        boolean canClose = true;

        TextChannel tc = (TextChannel) channel;

        IACategory iaCat = db.getIACategory();
        if (iaCat != null && iaCat.isInCategory(tc)) {
            canClose = true;
        } else if (tc.getParentCategory() != null && tc.getParentCategory().getName().toLowerCase().contains("warcat")) {
            canClose = true;
        } else {
            String[] split = channel.getName().split("-");
            String id = split[split.length - 1];
            if (split.length >= 2 && MathMan.isInteger(id)) {
                canClose = true;
            }
        }
        if (canClose) {
            Category parent = tc.getParentCategory();
            if (channel.getName().contains(closeChar)) {
                RateLimitUtil.queue(((GuildMessageChannel) channel).delete());
                return null;
            } else if (parent != null && (parent.getName().toLowerCase().startsWith("treasury") || parent.getName().toLowerCase().startsWith("grant"))) {
                int i = 0;
                for (GuildMessageChannel otherChannel : db.getGuild().getTextChannels()) {
                    if (otherChannel.getName().contains(closeChar) && otherChannel.hasLatestMessage()) {
                        String[] split = otherChannel.getName().split("-");
                        if (split.length == 2 && MathMan.isInteger(split[1])) {
                            long id = otherChannel.getLatestMessageIdLong();
                            OffsetDateTime created = net.dv8tion.jda.api.utils.TimeUtil.getTimeCreated(id);
                            long diff = System.currentTimeMillis() - created.toEpochSecond() * 1000L;
                            if (diff > expireTime && ++i < 5) {
                                RateLimitUtil.queue(otherChannel.delete());
                            }
                        }
                    }
                }
                RateLimitUtil.queue(((GuildMessageChannel) channel).getManager().setName(closeChar + channel.getName()));
                return "Marked channel as closed. Auto deletion in >24h. Use " + CM.channel.open.cmd.toSlashMention() + " to reopen. Use " + CM.channel.close.current.cmd.toSlashMention() + " again to force close";
            }

            Category archiveCategory = db.getOrNull(GuildDB.Key.ARCHIVE_CATEGORY);
            if (archiveCategory != null) {
                if (true || archiveCategory.equals(tc.getParentCategory()) || forceDelete) {
                    RateLimitUtil.queue(tc.delete());
                }
                else {
                    long cutoff = System.currentTimeMillis() - expireTime;
                    Locutus.imp().getExecutor().submit(new Runnable() {
                        @Override
                        public void run() {
                            for (GuildMessageChannel toDelete : archiveCategory.getTextChannels()) {
                                try {
                                    long created = net.dv8tion.jda.api.utils.TimeUtil.getTimeCreated(toDelete.getLatestMessageIdLong()).toEpochSecond() * 1000L;
                                    if (created < cutoff) {
                                        RateLimitUtil.queue(toDelete.delete());
                                    }
                                } catch (IllegalStateException ignore) {
                                    ignore.printStackTrace();
                                }
                            }
                        }
                    });
                    RateLimitUtil.queue(tc.getManager().setParent(archiveCategory));
                    for (PermissionOverride perm : tc.getMemberPermissionOverrides()) {
                        RateLimitUtil.queue(tc.putPermissionOverride(perm.getMember()).setAllow(Permission.VIEW_CHANNEL).setDeny(Permission.MESSAGE_SEND));
                    }
                    return "This channel is archived and marked for deletion after 2 days. Do not reply here";
                }
            }
            RateLimitUtil.queue(((GuildMessageChannel)channel).delete());
            return null;
        } else {
            return "You do not have permission to close this channel";
        }
    }

    @Command(desc = "Create an interview channel")
    public String interview(@Me GuildDB db, @Me User selfUser, @Default("%user%") User user) {
        IACategory iaCat = db.getIACategory(true,true,true);

        if (iaCat.getCategories().isEmpty()) {
            return "No categories found starting with: `interview`";
        }

        GuildMessageChannel channel = iaCat.getOrCreate(user, true);
        if (channel == null) return "Unable to find or create channel (does a category called `interview` exist?)";

        return channel.getAsMention();
    }

    @Command(aliases = {"syncInterviews", "syncInterview"})
    @RolePermission(value = {Roles.INTERNAL_AFFAIRS, Roles.INTERNAL_AFFAIRS_STAFF}, any=true)
    public String syncInterviews(@Me IMessageIO channel, @Me GuildDB db) {
        IACategory iaCat = db.getIACategory();
        iaCat.load();
        iaCat.purgeUnusedChannels(channel);
        iaCat.alertInvalidChannels(channel);
        return "Done!";
    }

    @Command
    @RolePermission(value = { Roles.INTERNAL_AFFAIRS, Roles.INTERNAL_AFFAIRS_STAFF, Roles.INTERVIEWER, Roles.MENTOR, Roles.RECRUITER }, any = true)
    public String setReferrer(@Me GuildDB db, @Me DBNation me, User user) {
        if (!db.isValidAlliance()) return "Note: No alliance registered to guild";
        if (me.getAlliance_id() != db.getAlliance_id()) {
            return "Note: You are not in this alliance";
        }
        if (db.getMeta(user.getIdLong(), NationMeta.REFERRER) == null) {
            db.getHandler().setReferrer(user, me);
        }
        return null;
    }

    @Command(aliases = {"sortInterviews", "sortInterview"})
    @RolePermission(value = {Roles.INTERNAL_AFFAIRS, Roles.INTERNAL_AFFAIRS_STAFF}, any=true)
    public String sortInterviews(@Me GuildMessageChannel channel, @Me IMessageIO io, @Me GuildDB db, @Default("true") boolean sortCategoried) {
        IACategory iaCat = db.getIACategory();
        iaCat.purgeUnusedChannels(io);
        iaCat.load();
        if (iaCat.isInCategory(channel)) {
            iaCat.sort(io, Collections.singleton((TextChannel) channel), sortCategoried);
        } else {
            iaCat.sort(io, iaCat.getAllChannels(), true);
        }
        return "Done!";
    }

    @Command(desc = "List the interview channels, by category + activity")
    @RolePermission(value = {Roles.INTERNAL_AFFAIRS, Roles.INTERNAL_AFFAIRS_STAFF}, any=true)
    public String iachannels(@Me User author, @Me Guild guild, @Me GuildDB db, String filter, @Default("1d") @Timediff long time) throws IOException, GeneralSecurityException {
        try {
            if (!filter.isEmpty()) filter += ",*";
            Set<DBNation> allowedNations = DiscordUtil.parseNations(guild, filter);

            Integer aaId = db.getOrNull(GuildDB.Key.ALLIANCE_ID);
            if (aaId == null) return "No alliance set " + CM.settings.cmd.create(GuildDB.Key.ALLIANCE_ID.name(), null).toSlashCommand() + "";

            IACategory cat = db.getIACategory();
            if (cat.getCategories().isEmpty()) return "No `interview` categories found";
            cat.load();

            Map<Category, List<IAChannel>> channelsByCategory = new LinkedHashMap<>();

            for (Map.Entry<DBNation, IAChannel> entry : cat.getChannelMap().entrySet()) {
                DBNation nation = entry.getKey();

                if (!allowedNations.contains(nation)) continue;
                if (nation.getAlliance_id() != aaId || nation.getActive_m() > 10000 || nation.getVm_turns() > 0) continue;
                User user = nation.getUser();
                if (user == null) continue;


                IAChannel iaChan = entry.getValue();
                TextChannel channel = iaChan.getChannel();
                Category category = channel.getParentCategory();
                String name = category.getName().toLowerCase();
                if (name.endsWith("-archive") || name.endsWith("-inactive")) continue;
                channelsByCategory.computeIfAbsent(category, f -> new ArrayList<>()).add(iaChan);
            }

            List<Category> categories = new ArrayList<>(channelsByCategory.keySet());
            Collections.sort(categories, Comparator.comparingInt(Category::getPosition));

            StringBuilder response = new StringBuilder();

            for (Category category : categories) {
                List<IAChannel> channels = channelsByCategory.get(category);
                List<Map.Entry<IAChannel, Long>> channelsByActivity = new ArrayList<>();
                Map<IAChannel, Map.Entry<Message, Message>> latestMsgs = new LinkedHashMap<>();


                for (IAChannel iaChan : channels) {
                    GuildMessageChannel channel = iaChan.getChannel();
                    DBNation nation = iaChan.getNation();
                    List<Message> messages = RateLimitUtil.complete(channel.getHistory().retrievePast(25));
                    User user = nation.getUser();

                    Message latestMessageUs = null;
                    Message latestMessageThem = null;

                    for (Message message : messages) {
                        User msgAuth = message.getAuthor();
                        if (msgAuth.isBot() || msgAuth.isSystem()) continue;
                        String content = DiscordUtil.trimContent(message.getContentRaw());
                        if (content.startsWith(Settings.commandPrefix(true) + "") || content.startsWith(Settings.commandPrefix(false) + "")) continue;

                        long msgTime = message.getTimeCreated().toEpochSecond();

                        if (!msgAuth.isBot() && !msgAuth.isSystem()) {


                            if (msgAuth.getIdLong() != user.getIdLong()) {
                                if (latestMessageUs == null || latestMessageUs.getTimeCreated().toEpochSecond() < msgTime) {
                                    latestMessageUs = message;
                                }
                            } else if (latestMessageThem == null || latestMessageThem.getTimeCreated().toEpochSecond() < msgTime) {
                                latestMessageThem = message;
                            }
                        }
                    }


                    long last = 0;
                    if (latestMessageUs != null) last = Math.max(last, latestMessageUs.getTimeCreated().toEpochSecond() * 1000L);
                    if (latestMessageThem != null) last = Math.max(last, latestMessageThem.getTimeCreated().toEpochSecond() * 1000L);
                    long now = System.currentTimeMillis();
                    long diffMsg = now - last;
                    long diffActive = TimeUnit.MINUTES.toMillis(nation.getActive_m());

                    if (last == 0 || diffMsg > diffActive + time) {
                        long activityValue = diffMsg - diffActive;
                        channelsByActivity.add(new AbstractMap.SimpleEntry<>(iaChan, activityValue));
                        latestMsgs.put(iaChan, new AbstractMap.SimpleEntry<>(latestMessageUs, latestMessageThem));
                    }
                }

                if (!channelsByActivity.isEmpty()) {

                    Collections.sort(channelsByActivity, (o1, o2) -> Long.compare(o2.getValue(), o1.getValue()));

                    String name = category.getName().toLowerCase().replaceAll("interview-", "");

                    response.append("**" + name + "**:\n");
                    for (Map.Entry<IAChannel, Long> channelInfo : channelsByActivity) {
                        IAChannel iaChan = channelInfo.getKey();
                        GuildMessageChannel channel = iaChan.getChannel();

                        DBNation nation = iaChan.getNation();


                        response.append(channel.getAsMention() + " " + "c" + nation.getCities() + " mmr:" + nation.getMMRBuildingStr() + " infra:" + nation.getAvgBuildings() + " off:" + nation.getOff() + ", " + nation.getColor() + ", " + TimeUtil.secToTime(TimeUnit.MINUTES, nation.getActive_m()));
                        response.append("\n");

                        Map.Entry<Message, Message> messages = latestMsgs.get(iaChan);
                        List<Message> msgsSorted = new ArrayList<>(2);
                        if (messages.getKey() != null) msgsSorted.add(messages.getKey());
                        if (messages.getValue() != null) msgsSorted.add(messages.getValue());
                        if (msgsSorted.size() == 2 && msgsSorted.get(1).getTimeCreated().toEpochSecond() < msgsSorted.get(0).getTimeCreated().toEpochSecond()) {
                            Collections.reverse(msgsSorted);
                        }

                        if (!msgsSorted.isEmpty()) {
                            for (Message message : msgsSorted) {
                                String msgTrimmed = DiscordUtil.trimContent(message.getContentRaw());
                                if (msgTrimmed.length() > 100) msgTrimmed = msgTrimmed.substring(0, 100) + "...";
                                long epoch = message.getTimeCreated().toEpochSecond() * 1000L;
                                long roundTo = TimeUnit.HOURS.toMillis(1);
                                long diffRounded = System.currentTimeMillis() - epoch;
                                diffRounded = (diffRounded / roundTo) * roundTo;

                                String timeStr = TimeUtil.secToTime(TimeUnit.MILLISECONDS, diffRounded);

                                response.append(" - [" + timeStr + "] **" + message.getAuthor().getName() + "**: `" + msgTrimmed + "`");
                                response.append("\n");
                            }

                        }
                    }
                }
            }
            if (response.length() == 0) return "No results found";
            return response.toString() + "\n" + author.getAsMention();
        } catch (Throwable e) {
            e.printStackTrace();
            return null;
        }
    }
}
