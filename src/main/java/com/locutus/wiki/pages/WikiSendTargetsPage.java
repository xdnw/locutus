package com.locutus.wiki.pages;

import com.locutus.wiki.BotWikiGen;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.util.MarkupUtil;

public class WikiSendTargetsPage extends BotWikiGen {
    public WikiSendTargetsPage(CommandManager2 manager) {
        super(manager, "sending_targets");
    }

    @Override
    public String generateMarkdown() {
        return build(
                """
                        - Converting other sheet formats to Locutus
                        - Copy part of a spy or war sheet by a nation filter
                        - Validate war or spy sheets
                        - Send war or spy target sheets to nations
                        - Send via ingame mail, discord dms
                        - Send only part of a sheet
                        """,
                "# Converting spy sheet formats",
                commandMarkdownSpoiler(CM.spy.sheet.convertdtc.cmd),
                commandMarkdownSpoiler(CM.spy.sheet.convertTKR.cmd),
                commandMarkdownSpoiler(CM.spy.sheet.convertHidude.cmd),
                "## Copying or Inverting a sheet",
                "To invert, set `groupByAttacker` to `true`",
                commandMarkdownSpoiler(CM.spy.sheet.copyForAlliance.cmd),
                "# Sheet validation",
                commandMarkdownSpoiler(CM.war.sheet.validate.cmd),
                commandMarkdownSpoiler(CM.spy.sheet.validate.cmd),
                "# Sending sheets",
                """
                - Requires the api key to be set (see: {api_key_wiki})
                - Send war blitz sheets, spy sheets, or both at the same time
                - Discord Direct Message (`dm`) requires `mail` role on the ROOT Locutus server
                - Set `allowedNations` to only mail to specific nations (See: {nations_wiki})
                - To replace default message, set `header` and set `hideDefaultBlurb: True` (HTML formatting. To convert: {converter_link}->)
                - Requires the `MAIL` role to set a custom message (see: {role_wiki})
                """.replace("{role_wiki}",
                        MarkupUtil.markdownUrl("Registering Roles", "../wiki/initial_setup#creating-and-registering-roles"))
                    .replace("{api_key_wiki}",
                        MarkupUtil.markdownUrl("Setting the API Key", "../wiki/initial_setup#setting-your-api-key"))
                    .replace("{nations_wiki}",
                        MarkupUtil.markdownUrl("Nation Filters", "../wiki/Nation_placeholders"))
                    .replace("{converter_link}",
                        MarkupUtil.markdownUrl("Markdown", "https://euangoddard.github.io/clipboard2markdown/") + " | " +
                                MarkupUtil.markdownUrl("HTML", "https://markdowntohtml.com/"))
                ,
                commandMarkdownSpoiler(CM.mail.targets.cmd),
                "# See also",
                "You can generate war rooms from a war blitz sheet, and ping nations with their targets, and a prewritten instruction",
                linkPage("war_rooms")
        );
       /*
       Ensure api key is set for mailing

       Auto targets
        - beige alerts page

        Generating targets
        - blitz targets (link to page)
        - spy targets (intel, or kill spies/units) (link to page)

        Using the mail targets command

        Using the mailcommandoutput command
        */
    }
}
