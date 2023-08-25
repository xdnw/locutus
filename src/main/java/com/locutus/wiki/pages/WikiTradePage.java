package com.locutus.wiki.pages;

import com.locutus.wiki.WikiGen;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;

public class WikiTradePage extends WikiGen {
    public WikiTradePage(CommandManager2 manager) {
        super(manager, "trade");
    }

    @Override
    public String generateMarkdown() {
        return build(

        );
       /*
       TODO trade guide

       TODO trade commands

       TODO trade settings

       TODO trade browser extension
        */
    }
}
