package com.locutus.wiki.pages;

import com.locutus.wiki.WikiGen;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;

public class WikiCoalitionsPage extends WikiGen {
    public WikiCoalitionsPage(CommandManager2 manager) {
        super(manager, "coalitions");
    }

    @Override
    public String generateMarkdown() {
       /*

       List of all coalitions and their purpose
       Note that you can create custom coalitions called anything else

       List of coalition commands

       How to mention a coalition

/coalition add
/coalition create
/coalition delete
/coalition generate
/coalition list
/coalition remove

        */
    }
}
