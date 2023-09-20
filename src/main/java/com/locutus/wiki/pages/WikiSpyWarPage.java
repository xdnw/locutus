package com.locutus.wiki.pages;

import com.locutus.wiki.BotWikiGen;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;

public class WikiSpyWarPage extends BotWikiGen {
    public WikiSpyWarPage(CommandManager2 manager) {
        super(manager, "espionage");
    }

    @Override
    public String generateMarkdown() {
        return build(

        );
       /*
       TODO espionage commands

       Spy template (member one, and the gov sheet one)

       https://docs.google.com/document/d/1qlZ-J2MV2P-xbf-gPpYW4KyEMqlO7q7j_I4GMnjM9ds/edit

       See who has not bought spies
       See who has not done a recent spy op
        - note the command must be run daily to record results

        Spy counter

        Espionage channel

        Converting common sheet formats (hidude tkr rose etc.)

        Generating a sub sheet
        /sheets_milcom listSpyTargets
        */
    }
}
