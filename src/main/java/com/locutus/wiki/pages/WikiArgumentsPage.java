package com.locutus.wiki.pages;

import com.locutus.wiki.CommandWikiPages;
import com.locutus.wiki.BotWikiGen;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;

public class WikiArgumentsPage extends BotWikiGen {
    public WikiArgumentsPage(CommandManager2 manager) {
        super(manager, "arguments");
    }

    @Override
    public String getDescription() {
        return "List and description of all arguments types used in commands.";
    }

    @Override
    public String generateMarkdown() {
        return CommandWikiPages.printParsers(getManager().getStore());
    }
}
