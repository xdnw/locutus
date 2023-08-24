package com.locutus.wiki.pages;

import com.locutus.wiki.WikiGen;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;

public class WikiCustomSheetsPage extends WikiGen {
    public WikiCustomSheetsPage(CommandManager2 manager) {
        super(manager, "custom spreadsheets");
    }

    @Override
    public String generateMarkdown() {
       /*
       nationsheet
       alliancesheet
       alliancenationssheet
        */
    }
}
