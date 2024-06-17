package link.locutus.wiki.pages;

import link.locutus.wiki.BotWikiGen;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.offshore.test.IACategory;

import java.util.Arrays;
import java.util.stream.Collectors;

public class WikiInterviewPage extends BotWikiGen {
    public WikiInterviewPage(CommandManager2 manager) {
        super(manager, "interviews");
    }

    @Override
    public String generateMarkdown() {
        return build(
            "# Prerequisites",
            "- Register the server to an alliance: " + CM.settings_default.registerAlliance.cmd.toString(),
            "## Create discord roles",
            "Register a role using: " + CM.role.setAlias.cmd.locutusRole("LOCUTUS_ROLE").discordRole("@discordRole"),
            "The following Locutus roles can be set",
            "- " + Roles.APPLICANT.toString(),
            "- " + Roles.GRADUATED.toString(),
            "- " + Roles.INTERVIEWER.toString(),
            "- " + Roles.MENTOR.toString(),
            "- " + Roles.INTERNAL_AFFAIRS_STAFF.toString(),
            "## Create `interview` category",
            "Create a category (or several) that start with `interview` or `interview-`.",
            "The following keywords are supported for sorting (i.e. `interview-KEYWORD`):",
            Arrays.stream(IACategory.SortedCategory.values()).map(f -> "- `" + f.name() +"`: `" + f.getDesc() + "`").collect(Collectors.joining("\n")),
            "Sort with: " + CM.interview.sortInterviews.cmd.toString(),
            "### Set category management channels",
                commandMarkdownSpoiler(CM.settings_interview.INTERVIEW_PENDING_ALERTS.cmd),
                commandMarkdownSpoiler(CM.settings_interview.ARCHIVE_CATEGORY.cmd),
                commandMarkdownSpoiler(CM.settings_interview.INTERVIEW_INFO_SPAM.cmd),
            "# Set interview message (optional)",
            "Such as for the initial questions or instructions",
            commandMarkdownSpoiler(CM.interview.questions.set.cmd),
            commandMarkdownSpoiler(CM.interview.questions.view.cmd),
            "# How do applicant's apply?",
            "The command to open an interview is " + CM.interview.create.cmd.toString(),
            "To bind this command to a button, see: " + linkPage("embeds#create-an-embed-with-title-and-description"),
            "OR use a discord bot such as MEE6 to give an applicant role",
            "# Managing channels",
            "You can manually delete channels, or use commands",
            commandMarkdownSpoiler(CM.interview.syncInterviews.cmd),
            commandMarkdownSpoiler(CM.channel.delete.current.cmd),
            commandMarkdownSpoiler(CM.channel.delete.inactive.cmd),
            "## Change an interview's category",
            commandMarkdownSpoiler(CM.interview.iacat.cmd),
            "## Listing channels",
            commandMarkdownSpoiler(CM.interview.iachannels.cmd),
            "# Managing mentees",
            commandMarkdownSpoiler(CM.interview.setReferrer.cmd),
            commandMarkdownSpoiler(CM.interview.mymentees.cmd),
            commandMarkdownSpoiler(CM.interview.mentee.cmd),
            commandMarkdownSpoiler(CM.interview.mentor.cmd),
            commandMarkdownSpoiler(CM.interview.listMentors.cmd),
            commandMarkdownSpoiler(CM.interview.unassignMentee.cmd),
            "# Mentee Announcements",
            "1. Create a channel and manually ping the applicant role",
            "2. Use the announce command (if opsec is needed): " + linkPage("announcements_and_opsec"),
            "3. Send message in interview channels: ",
                commandMarkdownSpoiler(CM.interview.interviewMessage.cmd)

//                "# Reward mentors",
//                // TODO broken
//                // This is only if you use the sorted categories listed above
//                commandMarkdownSpoiler(CM.settings_reward.REWARD_REFERRAL.cmd),
//                // runs on sort command
//                commandMarkdownSpoiler(CM.settings_reward.REWARD_MENTOR.cmd),
//                /interview adRanking
                //                /interview recruitmentRankings
//
        );
    }

}
