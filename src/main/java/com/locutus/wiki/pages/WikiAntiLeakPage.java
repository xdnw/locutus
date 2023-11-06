package com.locutus.wiki.pages;

import com.locutus.wiki.BotWikiGen;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.user.Roles;

public class WikiAntiLeakPage extends BotWikiGen {
    public WikiAntiLeakPage(CommandManager2 manager) {
        super(manager, "announcements and opsec");
    }

    @Override
    public String generateMarkdown() {
        return build(
                """
                This bot has tools for bulk communication, and for tracking leaks.
                - Bulk mail players using placeholders for names and other nation attributes
                - Create announcements, in-game mail, discord embeds, direct messages and invites that are unique to each user
                - Use Optical Character Recognition (OCR) to parse screenshots,
                - Search for matching or similar messages or invites""",
                "# Prerequisite settings",
                "Set your alliance and api key",
                commandMarkdownSpoiler(CM.settings_default.registerAlliance.cmd),
                commandMarkdownSpoiler(CM.settings_default.registerApiKey.cmd),
                "Set a role for who can use the mail commands",
                CM.role.setAlias.cmd.create(Roles.MAIL.name(), "@discordRole", null, null).toString(),
                "# Bulk mail",
                commandMarkdownSpoiler(CM.mail.send.cmd),
                // /mail reply
                "# Reply to messages",
                commandMarkdownSpoiler(CM.mail.reply.cmd),
                // /mail command
                "# Mail command results",
                commandMarkdownSpoiler(CM.mail.command.cmd),
                "# Mail a google spreadsheet",
                commandMarkdownSpoiler(CM.mail.sheet.cmd),
                "# Sending unique announcements",
                "Supports sending dms, mail and clickable discord embeds",
                commandMarkdownSpoiler(CM.announcement.create.cmd),
                "# Sending unique invites",
                commandMarkdownSpoiler(CM.announcement.invite.cmd),
                "# Sending unique documents",
                commandMarkdownSpoiler(CM.announcement.document.cmd),
                "# Converting a screenshot to text",
                commandMarkdownSpoiler(CM.announcement.ocr.cmd),
                "# Finding a nation from a message or invite",
                commandMarkdownSpoiler(CM.announcement.find.cmd),
                commandMarkdownSpoiler(CM.announcement.find_invite.cmd),
                "# Other announcement commands",
                commandMarkdownSpoiler(CM.announcement.archive.cmd),
                commandMarkdownSpoiler(CM.announcement.read.cmd),
                commandMarkdownSpoiler(CM.announcement.view.cmd)
        );
    }
}
