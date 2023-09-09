package com.locutus.wiki.pages;

import com.locutus.wiki.BotWikiGen;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;

public class WikiBlitzPage extends BotWikiGen {
    public WikiBlitzPage(CommandManager2 manager) {
        super(manager, "blitzes");
    }

    @Override
    public String generateMarkdown() {
        return build(

        );
       /*
       TODO link to espionage page

       TODO: steps for blitzing (find my guide)

       Enemy sheet
       Enemy sheet embed

       Compare coalitions stat commands (strength tier / city tier graph)

       Militarizing / Changing mmr
        - Optimalbuild
        - Self sufficient
        - infralow
        - Radiation (no food)
        - Africa (for self sufficient nukes)
        - war policy (blitz / fortress, pirate / moneybags)

       Ally sheet
       Underutilized ally sheet

       Roll calls

       Counter blitzes

       Blitz sheet

       Validating blitz sheets
       Mailing targets

       Creating war rooms from blitz sheet

       Setting enemies coalition

       Target embed for ppl to use post blitz
       (Or have milcom assign targets)

       Practice:
       /war find blitztargets


        sub sheet
        /spy sheet copyForAlliance


        */
    }
}
