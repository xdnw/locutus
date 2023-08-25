package com.locutus.wiki.pages;

import com.locutus.wiki.WikiGen;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MarkupUtil;

public class WikiAntiLeakPage extends WikiGen {
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
                commandMarkdown(CM.settings_default.registerAlliance.cmd),
                commandMarkdown(CM.settings_default.registerApiKey.cmd),
                "Set a role for who can use the mail commands",
                CM.role.setAlias.cmd.create(Roles.MAIL.name(), "@discordRole", null, null).toString(),
                "# Bulk mail",
                commandMarkdown(CM.mail.send.cmd),
                // /mail reply
                "# Reply to messages",
                commandMarkdown(CM.mail.reply.cmd),
                // /mail command
                "# Mail command results",
                commandMarkdown(CM.mail.command.cmd),
                "# Mail a google spreadsheet",
                commandMarkdown(CM.mail.sheet.cmd),
                "# Sending unique announcements",
                "Supports sending dms, mail and clickable discord embeds",
                commandMarkdown(CM.announcement.create.cmd),
                "# Sending unique invites",
                commandMarkdown(CM.announcement.invite.cmd),
                "# Converting a screenshot to text",
                commandMarkdown(CM.announcement.ocr.cmd),
                "# Finding a nation from a message or invite",
                commandMarkdown(CM.announcement.find.cmd),
                commandMarkdown(CM.announcement.find_invite.cmd),
                "# Other announcement commands",
                commandMarkdown(CM.announcement.archive.cmd),
                commandMarkdown(CM.announcement.read.cmd),
                commandMarkdown(CM.announcement.view.cmd)
        );
    }
}
