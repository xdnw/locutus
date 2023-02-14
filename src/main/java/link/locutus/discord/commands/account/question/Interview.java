package link.locutus.discord.commands.account.question;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.account.question.questions.InterviewQuestion;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.NationMeta;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.offshore.test.IACategory;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;
import java.util.Set;

public class Interview extends QuestionCommand<InterviewQuestion> {

    public Interview() {
        super(NationMeta.INTERVIEW_INDEX, InterviewQuestion.values());
    }

    @Override
    public String help() {
        return super.help() + " <user>";
    }


    @Override
    public String desc() {
        return "Create a channel for IA to interview that user, or move the current interview channel\n" +
                "";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return Roles.INTERNAL_AFFAIRS.has(user, server) || Roles.INTERNAL_AFFAIRS_STAFF.has(user, server);
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() != 1) return usage();
        GuildDB db = Locutus.imp().getGuildDB(guild);
        IACategory iaCat = db.getIACategory(true, true);
        if (iaCat == null) {
            throw new IllegalArgumentException("No interview category found");
        }
        if (iaCat.getCategories().isEmpty()) {
            return "No categories found starting with: `interview`";
        }

        User user = DiscordUtil.getUser(args.get(0));
        if (user == null) {
            GuildMessageChannel channel = event.getGuildChannel();
            boolean isIAChan = iaCat.isInCategory(channel);

            if (isIAChan) {
                ICategorizableChannel cc = (ICategorizableChannel) channel;
                // move to another category
                String arg0 = args.get(0).toLowerCase();
                for (Category category : iaCat.getCategories()) {
                    if (category.getName().toLowerCase().contains(arg0)) {
                        if (category.equals(cc.getParentCategory())) {
                            return "This channel is already in " + category.getName();
                        }
                        RateLimitUtil.queue(cc.getManager().setParent(category));
                        return "Moving " + channel.getAsMention() + " to " + category.getName();
                    }
                }
                return "No category found for: `" + arg0 + "`";
            }

            DBNation nation = DiscordUtil.parseNation(args.get(0));
            if (nation != null) {
                user = nation.getUser();
            }
            if (user == null || event.getGuild().getMember(user) == null) {
                throw new IllegalArgumentException("No user found for `" + args.get(0) + "`");
            }
        }

        GuildMessageChannel channel = iaCat.getOrCreate(user, true);
        if (channel == null) {
            String reason = "";

            Member member = guild.getMember(user);
            if (member == null) {
                reason = "Member not found on discord";
            } else if (iaCat.getActiveCategories().isEmpty()) {
                reason = "No interview category found";
            } else if (iaCat.getFreeCategory(iaCat.getActiveCategories()) == null) {
                reason = "Interview category is full";
            } else {
                reason = "Uknown reason";
            }
            return "Unable to find or create channel: " + reason;
        }

        return channel.getAsMention();


//        GuildDB db = Locutus.imp().getGuildDB(guild);
//        if (args.isEmpty()) {
//            Category category = db.getOrThrow(GuildDB.Key.INTERVIEW_CATEGORY);
//            GuildMessageChannel alertChannel = db.getOrNull(GuildDB.Key.INTERVIEW_PENDING_ALERTS);
//
//            Role applicantRole = Roles.APPLICANT.toRole(guild);
//            Role interviewerRole = Roles.INTERVIEWER.toRole(guild);
//
//            String channelName = author.getId();
//            GuildMessageChannel interviewChannel = null;
//
//            Member member = guild.getMember(author);
//
//            for (GuildMessageChannel GuildMessageChannel : category.getTextChannels()) {
//                if (GuildMessageChannel.getName().contains(channelName)) {
//                    interviewChannel = GuildMessageChannel;
//                    break;
//                }
//            }
//
//            if (interviewChannel == null) {
//                interviewChannel = link.locutus.discord.util.RateLimitUtil.complete(category.createTextChannel(channelName));
//
//                guild.addRoleToMember(author.getIdLong(), link.locutus.discord.util.RateLimitUtil.queue(applicantRole));
//
//                if (alertChannel != null) {
//                    String title = "New applicant";
//
//                    String emoji = "\u2705";
//
//                    StringBuilder body = new StringBuilder();
//                    body.append("User: " + author.getAsMention() + "\n");
//                    DBNation nation = DiscordUtil.getNation(author);
//                    if (nation != null) {
//                        body.append("nation: " + MarkupUtil.markdownUrl(nation.getNation(), nation.getNationUrl()) + "\n");
//                    }
//                    body.append("Channel: " + interviewChannel.getAsMention() + "\n\n");
//                    body.append("The first on the trigger, react with the " + emoji + " emoji");
//
//                    String pending = Settings.commandPrefix(true) + "pending 'Interview Assigned' '@%user% in " + interviewChannel.getAsMention() + "'";
//
//                    DiscordUtil.createEmbedCommand(alertChannel, title, body.toString(), emoji, pending);
//
//                    if (interviewerRole != null) {
//                        interviewChannel.sendMessage("^ " + interviewerRole.getAsMention());
//                    }
//                }
//                // ping interviewer
//            }
//
//            link.locutus.discord.util.RateLimitUtil.queue(interviewChannel.sendMessage(author.getAsMention()).complete().delete());
//            // ping the user in the c
//
//            return null;
//        }

    }
}