package link.locutus.wiki.pages;

import link.locutus.wiki.BotWikiGen;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.user.Roles;

public class WikiRecruitmentPage extends BotWikiGen {
    public WikiRecruitmentPage(CommandManager2 manager) {
        super(manager, "recruitment");
    }

    @Override
    public String generateMarkdown() {
        return build(
                """
                        - Locutus can send recruitment messages.
                        - This does NOT require login/username/password.\s
                        - Messages will only be sent if you have an active nation in a leadership position.
                        - Alliances with 9 or less members (non inactive) will also require an online nation on discord with the INTERNAL_AFFAIRS role
                        """,
                "# Set an `" + Roles.INTERNAL_AFFAIRS.name() + "` role",
                CM.role.setAlias.cmd.locutusRole(Roles.INTERNAL_AFFAIRS.name()).discordRole("@discordRole").toString(),
                "# Setup a recruitment message",
                """
                - Messages will automatically send once configured. 
                - Disable by using """ + CM.settings.delete.cmd.key(GuildKey.RECRUIT_MESSAGE_OUTPUT.name()),
                "1. First ensure you have your alliance and api key set",
                commandMarkdownSpoiler(CM.settings_default.registerAlliance.cmd),
                commandMarkdownSpoiler(CM.settings_default.registerApiKey.cmd),
                "2. Then set the message subject, content, and output channel",
                commandMarkdownSpoiler(CM.settings_recruit.RECRUIT_MESSAGE_SUBJECT.cmd),
                commandMarkdownSpoiler(CM.settings_recruit.RECRUIT_MESSAGE_CONTENT.cmd),
                commandMarkdownSpoiler(CM.settings_recruit.RECRUIT_MESSAGE_OUTPUT.cmd),
                "Optional:",
                commandMarkdownSpoiler(CM.settings_recruit.RECRUIT_MESSAGE_DELAY.cmd),
                "## Example Messages",
                "- <a href=\"recruit1.png\"><img src=\"recruit1.png\" alt=\"Example 1\" width=\"100\"/></a>",
                "- <a href=\"recruit2.png\"><img src=\"recruit2.png\" alt=\"Example 2\" width=\"100\"/></a>",
                "- <a href=\"recruit3.png\"><img src=\"recruit3.png\" alt=\"Example 3\" width=\"100\"/></a>",
                "- <a href=\"recruit4.png\"><img src=\"recruit4.png\" alt=\"Example 4\" width=\"100\"/></a>",
                "- <a href=\"recruit5.png\"><img src=\"recruit5.png\" alt=\"Example 5\" width=\"100\"/></a>",
                "- <a href=\"recruit6.png\"><img src=\"recruit6.png\" alt=\"Example 6\" width=\"100\"/></a>",
                "# Send a test message",
                commandMarkdownSpoiler(CM.mail.recruit.cmd),
                "# Configure a message to send new applicants",
                commandMarkdownSpoiler(CM.settings_recruit.MAIL_NEW_APPLICANTS.cmd),
                commandMarkdownSpoiler(CM.settings_recruit.MAIL_NEW_APPLICANTS_TEXT.cmd),
                """
                # Other recruitment strategies
                - Members inviting their friends to the game
                - Talking to and meeting people in game Discords
                - Advertising on other Discord servers, such as news servers
                - Using the in-game advertisements <https://politicsandwar.com/donate/advertisement/>"""
        );
    }
}
