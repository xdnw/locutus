package com.locutus.wiki.pages;

import com.locutus.wiki.BotWikiGen;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;

public class WikiSendTargetsPage extends BotWikiGen {
    public WikiSendTargetsPage(CommandManager2 manager) {
        super(manager, "sending targets");
    }

    @Override
    public String generateMarkdown() {
        return build(

        );
       /*
       Ensure api key is set for mailing

       Auto targets
        - beige alerts page

        Generating targets
        - blitz targets (link to page)
        - spy targets (intel, or kill spies/units) (link to page)

        Using the mail targets command

        Using the mailcommandoutput command
        */
    }
}
