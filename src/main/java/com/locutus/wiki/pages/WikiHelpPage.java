package com.locutus.wiki.pages;

import com.locutus.wiki.WikiGen;
import com.locutus.wiki.WikiGenHandler;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.util.MarkupUtil;

import java.util.ArrayList;
import java.util.List;

public class WikiHelpPage extends WikiGen {
    private final List<WikiGen> pages;
    private final String urlPrefix;

    public WikiHelpPage(CommandManager2 manager, List<WikiGen>pages) {
        super(manager, "home");
        this.pages = new ArrayList<>(pages);
        this.urlPrefix = "../wiki/";
    }

    @Override
    public String generateMarkdown() {
        StringBuilder pageList = new StringBuilder();
        for (WikiGen page : pages) {
            if (page.generateMarkdown().trim().isEmpty()) continue;
            String url = urlPrefix + page.getPageName().replace(" ", "_");
            pageList.append("### " + MarkupUtil.markdownUrl(page.getPageName(), url)).append("\n");
            pageList.append("> " + page.getDescription().replace("\n", "\n> ")).append("\n");
        }
        return build(
                "# Command Syntax",
                """
                - `<arg>` - A required parameter
                - `[arg]` - An optional parameter
                - `<arg1|arg2>` - Multiple parameters options
                - `<arg=value>` - Default or suggested value
                - `[-f flag]` - A optional command argument flag""",
                "# Using the help commands",
                CM.help.command.cmd.getCallable(true).simpleDesc(),
                CM.help.command.cmd.toSlashCommand(true),
                "\n\n---\n\n",
                CM.help.find_command.cmd.getCallable(true).simpleDesc(),
                CM.help.find_command.cmd.toSlashCommand(true),
                "\n\n---\n\n",
                CM.help.find_nation_placeholder.cmd.getCallable(true).simpleDesc(),
                CM.help.find_nation_placeholder.cmd.toSlashCommand(true),
                "\n\n---\n\n",
                CM.help.find_setting.cmd.getCallable(true).simpleDesc(),
                CM.help.find_setting.cmd.toSlashCommand(true),
                "\n\n---\n\n",
                CM.help.nation_placeholder.cmd.getCallable(true).simpleDesc(),
                CM.help.nation_placeholder.cmd.toSlashCommand(true),
                "## For server owners",
                "List available settings",
                CM.settings.info.cmd.toSlashCommand(true),
                "\n\n---\n\n",
                "List ALL settings",
                CM.settings.info.cmd.create(null, null, Boolean.TRUE + "").toSlashCommand(true),
                "\n\n---\n\n",
                "View a setting",
                "For example, the `" + GuildKey.ALLIANCE_ID.name() + "` settings",
                CM.settings.info.cmd.create(GuildKey.ALLIANCE_ID.name(), null, null).toSlashCommand(true),
            "# Overview of this Wiki",
            pageList.toString()
        );
    }
}
