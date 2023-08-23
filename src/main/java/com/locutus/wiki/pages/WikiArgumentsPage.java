package com.locutus.wiki.pages;

import com.locutus.wiki.CommandWikiPages;
import com.locutus.wiki.WikiGen;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;

public class WikiArgumentsPage extends WikiGen {
    public WikiArgumentsPage(CommandManager2 manager) {
        super(manager, "arguments");
    }

    @Override
    public String generateMarkdown() {
        return CommandWikiPages.printParsers(getManager().getStore());
    }
}
