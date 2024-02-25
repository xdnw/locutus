package com.locutus.wiki.pages;

import com.locutus.wiki.BotWikiGen;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.db.entities.Coalition;
import link.locutus.discord.util.MarkupUtil;

import java.util.Arrays;
import java.util.stream.Collectors;

public class WikiCoalitionsPage extends BotWikiGen {
    public WikiCoalitionsPage(CommandManager2 manager) {
        super(manager, "coalitions");
    }

    @Override
    public String generateMarkdown() {
        return build(
        """
                - In built coalitions have functionality, such as for do not raid, or banking.
                - Create custom coalitions for your own use.
                - Mention coalitions in commands using `~coalitionName` e.g.""",
                CM.who.cmd.create("~coalitionName,#position>1", null, null, null, null, null, null, null, null, null).toString(),
                "# In built coalitions",
                "- " + Arrays.stream(Coalition.values()).map(Coalition::toString).collect(Collectors.joining("\n- ")),
                "# Adding to in-built coalition",
                CM.coalition.add.cmd.create("AA:Rose", Coalition.ALLIES.name()).toString(),
                "# Listing your current coalitions",
                commandMarkdownSpoiler(CM.coalition.list.cmd),
                "# Removing a coalition",
                "## Removing a single alliance",
                CM.coalition.remove.cmd.create("AA:Rose", Coalition.ALLIES.name()).toString(),
                "## Removing an entire coalition",
                CM.coalition.delete.cmd.create(Coalition.ALLIES.name()).toString(),
                "# Creating a custom coaltion",
                CM.coalition.create.cmd.create("AA:Camelot,AA:Aurora", "big_bois").toString(),
                "# Generating a coalition from treaty web",
                commandMarkdownSpoiler(CM.coalition.generate.cmd),
                "# Using guilds in coalitions",
                "Guilds will resolve to their currently registered alliance when used in nation or alliance commands",
                "e.g. Offshores which frequently change alliance, but may have the same guild",
                "Copy and use the guild id in the coalition commands above: " + MarkupUtil.markdownUrl("How To Obtain Guild Id", "https://support.discord.com/hc/en-us/articles/206346498-Where-can-I-find-my-User-Server-Message-ID-"),
                "# See also",
                "- " + linkPage("do_not_raid")

        );
    }
}
