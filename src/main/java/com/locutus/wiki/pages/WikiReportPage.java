package com.locutus.wiki.pages;

import com.locutus.wiki.WikiGen;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;

public class WikiReportPage extends WikiGen {
    public WikiReportPage(CommandManager2 manager) {
        super(manager, "reporting");
    }

    @Override
    public String generateMarkdown() {
       /*
       state purpose of report system

       The goal of the report system is for alliances and player corps to have accurate information about problematic behavior of a user/nation.
e.g. Scamming, loan defaults, multis, OOC.

I've imported the OCB report sheet as a starting point, though removed all the "declaring war" reports since those are pointless.

I do have concerns that people will try reporting innacurate or false information. The way it's coded currently

Reports are pending until an admin approves it.
The report system has commands /report comment add
As well as reports requiring either a forum post, or a post on one of the public news servers.


The management commands (e.g. ban,unban,purge,upload) require the IA role on the Unicomplex server

       TODO update wiki commands.md so it has the report commands

       report alert channel

       /report sheet generate
Generate a sheet of reports
/report import legacy
Import the legacy reports
/report sheet loans
Generate a sheet of loans
/report import loans
Import loans from a sheet
/report create
Create a report for a nation or user
/report remove
Remove a report
/report approve
Approve a report
/report comment add
Add a comment to a report
/report purge
Purge all reports by a user
/report ban
Ban a nation from creating reports
/report unban
unban a nation from creating reports
/report search
Find reports by or on a nation or user
/report show
Show the information and comments for a specific report
/report analyze
Show an analysis of a nation's risk factors including: Reports, loans, discord & game bans, money trades and proximity with blacklisted nations, multi info, user account age, inactivity predictors
        */
    }
}
