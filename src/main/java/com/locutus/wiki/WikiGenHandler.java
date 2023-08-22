package com.locutus.wiki;

import com.locutus.wiki.pages.WikiHelpPage;
import com.locutus.wiki.pages.WikiSetupPage;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.gpt.GptHandler;
import link.locutus.discord.gpt.pwembed.PWGPTHandler;

import java.util.ArrayList;
import java.util.List;

public class WikiGenHandler {

    private final CommandManager2 manager;
    private final PWGPTHandler gpt;
    private final String pathRelative;

    public WikiGenHandler(String pathRelative, CommandManager2 manager, PWGPTHandler gpt) {
        this.manager = manager;
        this.gpt = gpt;
        this.registerDefaults(manager.getStore());
        this.pathRelative = pathRelative;
    }

    private void registerDefaults(ValueStore store) {
        List<WikiGen> pages = new ArrayList<>();

        // commands

        // arguments

        // nation_placeholders

        // alliance_placeholders

        // register defaults
        pages.add(new WikiSetupPage(store));

        // todo convert the command pages to wiki pages

        //War Alerts
        //Auto masking
        //- self roles
        //Deposits System
        //Setup an offshore or bank
        //Interview System
        //Auditing System / Alliance MMR requirements
        //- Audit command
        //- Mailing results
        //- Audit channels
        //- hasnotbought spies
        //- Optimalbuild
        //DNR system
        //Coalitions system
        //War Room System
        //Recruitment System
        //Countering
        //Finding Targets / War panels
        //Tax Bracket Automation
        //Escrow System
        //Plan a blitz
        //- get coalitions
        //- Counter blitz
        //Send out targets
        //Spy war
        //Blockade System
        //Beige cycling tutorial
        //Loan System
        //Report System
        //Anti Leak System
        //Making custom spreadsheets
        //Statistics System
        //Trade System (use trading guide)
        //- Link DocScripts trade tool (cause useful)


        pages.add(new WikiHelpPage(store, pages));
    }
}
