package com.boydti.discord.web.commands;

import com.boydti.discord.commands.manager.v2.binding.annotation.Command;
import com.boydti.discord.commands.manager.v2.binding.annotation.Default;
import com.boydti.discord.commands.manager.v2.binding.annotation.Me;
import com.boydti.discord.commands.manager.v2.impl.discord.permission.IsAlliance;
import com.boydti.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import com.boydti.discord.commands.manager.v2.impl.discord.permission.WhitelistPermission;
import com.boydti.discord.db.GuildDB;
import com.boydti.discord.db.entities.InterviewMessage;
import com.boydti.discord.db.entities.Transaction2;
import com.boydti.discord.pnw.Alliance;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.user.Roles;
import com.boydti.discord.util.StringMan;
import com.boydti.discord.util.TimeUtil;
import com.boydti.discord.util.discord.DiscordUtil;
import com.boydti.discord.util.offshore.test.IACategory;
import com.boydti.discord.util.offshore.test.IAChannel;
import com.boydti.discord.util.task.ia.IACheckup;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.dv8tion.jda.api.entities.Category;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.GuildMessageChannel;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import org.json.JSONObject;

import javax.swing.text.Document;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class IAPages {

    @Command()
    @RolePermission(Roles.INTERNAL_AFFAIRS_STAFF)
    @IsAlliance
    public String iaChannels(@Me GuildDB db, @Me DBNation me, @Me User author) throws ExecutionException, InterruptedException {
        long start = System.currentTimeMillis();

        IACategory iaCat = db.getIACategory();
        iaCat.load();
        CompletableFuture<Void> future = iaCat.updateChannelMessages();
        future.get();

        Map<Long, List<InterviewMessage>> messages = db.getInterviewMessages();

        Map<GuildMessageChannel, User> interviewUsers = new HashMap<>();
        Map<GuildMessageChannel, DBNation> interviewNation = new HashMap<>();
        Set<GuildMessageChannel> myChannels = new HashSet<>();
        Set<Long> existingChannelIds = new HashSet<>();

        for (GuildMessageChannel channel : iaCat.getAllChannels()) {
            List<InterviewMessage> chanMessages = messages.get(channel.getIdLong());
            if (chanMessages != null) {
                existingChannelIds.add(channel.getIdLong());
                for (InterviewMessage msg : chanMessages) {
                    if (msg.sender == author.getIdLong()) {
                        myChannels.add(channel);
                    }
                }
            }
            Map.Entry<DBNation, User> userNation = iaCat.getNationUser(channel);

            if (userNation != null) {
                User user = userNation.getValue();
                DBNation nation = userNation.getKey();

                interviewUsers.put(channel, user);
                interviewNation.put(channel, nation);
            }
        }
        messages.entrySet().removeIf(f -> !existingChannelIds.contains(f.getKey()));

        List<TextChannel> channelsSorted = iaCat.getAllChannels();
        DiscordUtil.sortInterviewChannels(channelsSorted, messages, interviewUsers);

        Map<GuildMessageChannel, IACategory.SortedCategory> categoryMap = new HashMap<>();
        Map<IACategory.SortedCategory, List<GuildMessageChannel>> channelsByCategory = new LinkedHashMap<>();
        for (TextChannel channel : channelsSorted) {
            IACategory.SortedCategory category = iaCat.getSortedCategory(channel);
            categoryMap.put(channel, category);

            channelsByCategory.computeIfAbsent(category, f -> new LinkedList<>()).add(channel);
        }

        List<IACategory.SortedCategory> categories = new ArrayList<>(Arrays.asList(IACategory.SortedCategory.values()));
        categories.add(null);

//        for (Map.Entry<GuildMessageChannel, IACategory.SortedCategory> entry : categoryMap.entrySet()) {
//
//        }
//
//        for (Map.Entry<IACategory.SortedCategory, List<GuildMessageChannel>> entry : channelsByCategory.entrySet()) {
//
//        }

        // json data
        // avatars (Map String Url (string))
        // messages {Map <channel id, list< JsonArray > >

        Map<String, JsonArray> messagesMap = new HashMap<>();
        Map<String, String> usernames = new HashMap<>();
        Set<Long> senderIds = new HashSet<>();
        for (Map.Entry<Long, List<InterviewMessage>> entry : messages.entrySet()) {
            JsonArray channelMessagesJson = new JsonArray();
            for (InterviewMessage msg : entry.getValue()) {
                if (senderIds.add(msg.sender)) {
                    usernames.put(msg.sender + "", msg.getUsername());
                }

                JsonArray messageJson = new JsonArray();
                messageJson.add(msg.message_id + "");
                messageJson.add(msg.sender + "");
                messageJson.add(msg.date_created + "");
                messageJson.add(msg.message);

                channelMessagesJson.add(messageJson);
            }

            messagesMap.put(entry.getKey() + "", channelMessagesJson);
        }

        Map<String, String> avatarUrls = new HashMap<>();
        for (Long id : senderIds) {
            User user = DiscordUtil.getUser(id);
            if (user != null) {
                avatarUrls.put(id + "", user.getAvatarId());
            }
        }
        Gson gson = new Gson();

        JsonElement avatarsJson = gson.toJsonTree(avatarUrls);
        JsonElement usersJson = gson.toJsonTree(usernames);
        JsonElement messagesJson = gson.toJsonTree(messagesMap);

        long diff = System.currentTimeMillis() - start;
        System.out.println("Diff " + diff + "ms");

        return views.guild.ia.iachannels.template(db, me, author, iaCat, categories, categoryMap, channelsByCategory, interviewNation, interviewUsers, avatarsJson, usersJson, messagesJson, myChannels).render().toString();
    }

    @Command()
    @RolePermission(Roles.INTERNAL_AFFAIRS_STAFF)
    @IsAlliance
    public Object memberAuditIndex(@Me GuildDB db) throws IOException {
        IACheckup checkup = new IACheckup(db, db.getAlliance_id(), true);
        Map<IACheckup.AuditType, Map<DBNation, String>> allianceAuditResults = new LinkedHashMap<>();

        Alliance alliance = db.getAlliance();
        List<DBNation> allNations = alliance.getNations(true, 0, true);
        Collections.sort(allNations, Comparator.comparingInt(DBNation::getCities));

        List<DBNation> inactive = allNations.stream().filter(f -> f.getActive_m() > 4320).collect(Collectors.toList());
        allNations.removeIf(f -> f.getActive_m() > 4320);

        Map<DBNation, String> inactiveMap = inactive.stream().collect(Collectors.toMap(f -> f,
                f -> f.getNation() + " is " + TimeUtil.minutesToTime(f.getActive_m()) + " inactive"));
        allianceAuditResults.put(IACheckup.AuditType.INACTIVE, inactiveMap);

        for (DBNation nation : allNations) {
            Map<IACheckup.AuditType, Map.Entry<Object, String>> audit = checkup.checkupSafe(nation, true, true);
            for (Map.Entry<IACheckup.AuditType, Map.Entry<Object, String>> entry : audit.entrySet()) {
                Map<DBNation, String> nationMap = allianceAuditResults.computeIfAbsent(entry.getKey(), f -> new HashMap<>());
                nationMap.put(nation, entry.getValue().getValue());
            }
        }
        Map<IACheckup.AuditType, Map<DBNation, String>> allianceAuditResultsSorted = new LinkedHashMap<>();

        List<IACheckup.AuditType> auditTypes = new ArrayList<>(Arrays.asList(IACheckup.AuditType.values()));
        Collections.sort(auditTypes, (o1, o2) -> Integer.compare(o2.severity.ordinal(), o1.severity.ordinal()));
        for (IACheckup.AuditType type : auditTypes) {
            Map<DBNation, String> values = allianceAuditResults.get(type);
            if (values != null) allianceAuditResultsSorted.put(type, values);
        }


        return views.guild.ia.audits.template(db, alliance, allianceAuditResultsSorted).render().toString();
    }

    @Command()
    @IsAlliance
    @RolePermission(Roles.INTERNAL_AFFAIRS_STAFF)
    public Object mentorIndex(IACategory iaCat, @Me Guild guild, @Me GuildDB db, @Me DBNation me) throws IOException {
        List<DBNation> mentors = new ArrayList<>();
        Roles[] mentorRoles = new Roles[]{Roles.INTERVIEWER, Roles.MENTOR};
        for (Roles mentorRole : mentorRoles) {
            Role discordRole = mentorRole.toRole(guild);
            if (discordRole != null) {
                for (Member member : guild.getMembersWithRoles(discordRole)) {
                    DBNation nation = DiscordUtil.getNation(member.getUser());
                    if (nation != null) {
                        mentors.add(nation);
                    }
                }
            }
        }

        boolean includeAudit = true;
        long timediff = TimeUnit.DAYS.toMillis(14);

        Set<DBNation> mentees = new HashSet<>(db.getAlliance().getNations(true, 10000, true));

        IACheckup checkup = includeAudit ? new IACheckup(db, db.getAlliance_id(), true) : null;
        Map<DBNation, List<DBNation>> mentorMenteeMap = new HashMap<>();

        Map<DBNation, IACategory.AssignedMentor> menteeMentorMap = new HashMap<>();

        Map<DBNation, IACategory.SortedCategory> categoryMap = new HashMap<>();
        Map<DBNation, Boolean> passedMap = new HashMap<>();
        Map<DBNation, Integer> numPassedMap = new HashMap<>();
        List<DBNation> membersUnverified = new ArrayList<>();
        List<DBNation> membersNotOnDiscord = new ArrayList<>();
        List<DBNation> nationsNoIAChan = new ArrayList<>();
        List<DBNation> noMentor = new ArrayList<>();
        List<DBNation> idleMentors = new ArrayList<>();
        Map<Integer, Long> lastMentorTxByNationId = new HashMap<>();

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


            IACategory.AssignedMentor mentorInfo = iaCat.getMentor(mentee, timediff);
            if (mentorInfo != null) {
                mentorMenteeMap.computeIfAbsent(mentorInfo.mentor, f -> new ArrayList<>()).add(mentee);
                menteeMentorMap.put(mentee, mentorInfo);
            }
        }

        if (mentorMenteeMap.isEmpty()) return "No mentees found";

        List<Map.Entry<DBNation, List<DBNation>>> mentorsSorted = new ArrayList<>(mentorMenteeMap.entrySet());
        mentorsSorted.sort(new Comparator<Map.Entry<DBNation, List<DBNation>>>() {
            @Override
            public int compare(Map.Entry<DBNation, List<DBNation>> o1, Map.Entry<DBNation, List<DBNation>> o2) {
                return Integer.compare(o2.getValue().size(), o1.getValue().size());
            }
        });

        for (Map.Entry<DBNation, List<DBNation>> entry : mentorsSorted) {
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
            numPassedMap.put(entry.getKey(), numPassed);
            myMentees.removeIf(f -> passedMap.getOrDefault(f, false));
            entry.setValue(myMentees);
        }

        long requiredMentorActivity = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(20);
        List<Transaction2> transactions = db.getTransactions(requiredMentorActivity, false);

        for (Transaction2 transaction : transactions) {
            if (!transaction.isSenderNation() || transaction.note == null || !transaction.note.contains("#incentive")) continue;
            int mentorId = (int) transaction.sender_id;
            Long last = lastMentorTxByNationId.get(mentorId);
            if (last != null && last > transaction.tx_datetime) continue;
            lastMentorTxByNationId.put(mentorId, transaction.tx_datetime);
        }

        Alliance alliance = db.getAlliance();
        List<DBNation> members = alliance.getNations(true, 2880, true);
        members.removeIf(f -> !mentees.contains(f));

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

        for (DBNation mentor : mentors) {
            List<DBNation> myMentees = mentorMenteeMap.getOrDefault(mentor, Collections.emptyList());
            myMentees.removeIf(f -> f.getActive_m() > 4880 || f.getVm_turns() > 0 || passedMap.getOrDefault(f, false));
            if (myMentees.isEmpty()) {
                idleMentors.add(mentor);
            }
        }

        return views.guild.ia.mentors.template(iaCat, db, mentorsSorted, menteeMentorMap, categoryMap, passedMap, lastMentorTxByNationId,
                mentors, numPassedMap, membersUnverified, membersNotOnDiscord, nationsNoIAChan, noMentor, idleMentors,
                checkup).render().toString();
    }
}
