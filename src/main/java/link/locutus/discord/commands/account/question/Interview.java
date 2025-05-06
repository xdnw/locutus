package link.locutus.discord.commands.account.question;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.account.question.questions.InterviewQuestion;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.NationMeta;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.offshore.test.IACategory;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.attribute.ICategorizableChannel;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

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
        return "Create a channel for IA to interview that user, or move the current interview channel\n";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return Roles.INTERNAL_AFFAIRS.has(user, server) || Roles.INTERNAL_AFFAIRS_STAFF.has(user, server);
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() != 1) return usage();
        GuildDB db = Locutus.imp().getGuildDB(guild);
        IACategory iaCat = db.getIACategory(true, true, true);
        if (iaCat == null) {
            throw new IllegalArgumentException("No interview category found.");
        }
        if (iaCat.getCategories().isEmpty()) {
            return "No categories found starting with: `interview` or set via " + GuildKey.INTERVIEW_CATEGORY.getCommandMention();
        }

        User user = DiscordUtil.getUser(args.get(0), guild);
        MessageChannel dChannel;
        if (user == null) {
            DiscordChannelIO cio = (channel instanceof DiscordChannelIO tmp) ? tmp : null;
            if (cio == null) {
                throw new IllegalArgumentException("Command was not run from a channel message");
            }
            dChannel = cio.getChannel();
            boolean isIAChan = iaCat.isInCategory(dChannel);

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
                        return "Moving " + dChannel.getAsMention() + " to " + category.getName();
                    }
                }
                return "No category found for: `" + arg0 + "`";
            }

            DBNation nation = DiscordUtil.parseNation(args.get(0), true, guild);
            if (nation != null) {
                user = nation.getUser();
            }
            if (user == null || guild.getMember(user) == null) {
                throw new IllegalArgumentException("No user found for `" + args.get(0) + "`");
            }
        }

        dChannel = iaCat.getOrCreate(user, true);
        if (dChannel == null) {
            String reason;

            Member member = guild.getMember(user);
            if (member == null) {
                reason = "Member not found on discord.";
            } else if (iaCat.getActiveCategories().isEmpty()) {
                reason = "No interview category found.";
            } else if (iaCat.getFreeCategory(iaCat.getActiveCategories()) == null) {
                reason = "Interview category is full.";
            } else {
                reason = "Unknown reason.";
            }
            return "Unable to find or create channel: " + reason;
        }

        Role applicantRole = Roles.APPLICANT.toRole(user, db);
        if (applicantRole != null) {
            Member member = guild.getMember(user);
            if (member == null || !member.getRoles().contains(applicantRole)) {
                RateLimitUtil.queue(guild.addRoleToMember(user, applicantRole));
            }
        }
        return dChannel.getAsMention();
    }
}