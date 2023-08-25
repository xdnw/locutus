package com.locutus.wiki.pages;

import com.locutus.wiki.WikiGen;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;

public class WikiWarRoomPage extends WikiGen {
    public WikiWarRoomPage(CommandManager2 manager) {
        super(manager, "war rooms");
    }

    @Override
    public String generateMarkdown() {
        return build(

        );
       /*
        TODO copy stuff from war room guide
        https://docs.google.com/document/d/1Qq6Qe7KtCy-Dlqktz8bhNfrUpcbf7oM8F6gRVNR28Dw/edit

       /settings_war_room ENABLE_WAR_ROOMS

       /settings_war_room WAR_SERVER

       Category names
       Category permissions
        - ensure the bot has permission to see and manage the war room category channels and add/remove users to the category/channels

       sync war rooms
       /war counter nation
       <target>
       [counterWith]
       [-p ping]

        War rooms auto close when a war concludes
       /channel close current

       /war counter auto

       /war room create

       /war room from_sheet

       /war room pin

       /war room setCategory

       SORTING (city ranges)

       /war room sort

       Relay messages by pining locutus

        */
    }
}
