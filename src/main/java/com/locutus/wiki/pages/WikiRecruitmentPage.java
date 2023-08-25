package com.locutus.wiki.pages;

import com.locutus.wiki.WikiGen;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.user.Roles;

public class WikiRecruitmentPage extends WikiGen {
    public WikiRecruitmentPage(CommandManager2 manager) {
        super(manager, "recruitment");
    }

    @Override
    public String generateMarkdown() {
        return build(
                """
                        Locutus can send recruitment messages. This does NOT require login/username/password.\s
                        Messages will only be sent if you have an active nation in a leadership position.
                        Alliances with 9 or less members (non inactive) will also require an online nation on discord with the INTERNAL_AFFAIRS role
                        """,
                "# Set an `" + Roles.INTERNAL_AFFAIRS.name() + "` role",
                CM.role.setAlias.cmd.create(Roles.INTERNAL_AFFAIRS.name(), "@discordRole", null, null).toString(),
                "# Setup a recruitment message",
                "First ensure you have your alliance and api key set",
                commandMarkdown(CM.settings_default.registerAlliance.cmd),
                commandMarkdown(CM.settings_default.registerApiKey.cmd),
                "Then set the message subject, content, and output channel",
                commandMarkdown(CM.settings_recruit.RECRUIT_MESSAGE_SUBJECT.cmd),
                commandMarkdown(CM.settings_recruit.RECRUIT_MESSAGE_CONTENT.cmd),
                commandMarkdown(CM.settings_recruit.RECRUIT_MESSAGE_OUTPUT.cmd),
                "Optional:",
                commandMarkdown(CM.settings_recruit.RECRUIT_MESSAGE_DELAY.cmd),
                "# Send a test message",
                commandMarkdown(CM.mail.recruit.cmd),
                """
                # Other recruitment strategies
                - Members inviting their friends to the game
                - Talking to and meeting people in game Discords
                - Advertising on other Discord servers, such as news servers
                - Using the in-game advertisements <https://politicsandwar.com/donate/advertisement/>"""
        );
    }
}
