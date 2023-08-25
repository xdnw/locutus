package com.locutus.wiki.pages;

import com.locutus.wiki.WikiGen;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;

public class WikiAuditingPage extends WikiGen {
    public WikiAuditingPage(CommandManager2 manager) {
        super(manager, "auditing");
    }

    @Override
    public String generateMarkdown() {
        return build(

        );
       /*
       TODO find other auditing commands

       TODO find auditing settings
       - opt out audits

       TODO Audit roles

 //- Audit command
//        //- Mailing results
//        //- Audit channels
//        //- hasnotbought spies
//        //- Optimalbuild
        */
    }
}
