package com.locutus.wiki.pages;

import com.locutus.wiki.WikiGen;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;

public class WikiAntiLeakPage extends WikiGen {
    public WikiAntiLeakPage(CommandManager2 manager) {
        super(manager, "Opsec Announcements");
    }

    @Override
    public String generateMarkdown() {
       /*
       Create discord embeds, discord direct messages, ingame mail, and invites that are unique to each user
       Unique messages or invites can be tracked to the user that leaked them using the search commands

       Announacement commands

       Invite generation

       Mail commands
        */
    }
}
