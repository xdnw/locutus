package com.locutus.wiki.pages;

import com.locutus.wiki.WikiGen;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;

public class WikiDNRPage extends WikiGen {
    public WikiDNRPage(CommandManager2 manager) {
        super(manager, "do not raid");
    }

    @Override
    public String generateMarkdown() {
       /*
       Note that target finding commands adhere to the DNR
       Recommend also adding this info to the war declare page of your alliance in-game

       FOREIGN_AFFAIRS
        - Will be pinged for DNR violations in the offensive war channel
       Offensive wars channel

       Members will be pinged in offensive war channel and sent an ingame mail if they violate the dnr

        Settings
       DO_NOT_RAID_TOP_X

      Coalition add/remove etc. commands

       ENEMIES
       IGNORE_FA
       COUNTER
       CAN_RAID_INACTIVE
       FA_FIRST
       CAN_RAID
       DNR_MEMBER
       DNR

       DNR_1w
       DNR_MEMBER_1w

       /war dnr

        */
    }
}
