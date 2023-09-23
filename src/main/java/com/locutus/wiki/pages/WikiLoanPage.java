package com.locutus.wiki.pages;

import com.locutus.wiki.BotWikiGen;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.db.entities.DBLoan;

import java.util.Arrays;
import java.util.stream.Collectors;

public class WikiLoanPage extends BotWikiGen {
    public WikiLoanPage(CommandManager2 manager) {
        super(manager, "loan reporting");
    }

    @Override
    public String generateMarkdown() {
        return build(
            "# Loan Reporting Overview",
                """
                The loan reporting system allows alliances and player banks to share and access information about active or defaulted loans.
                Loan information can be useful to avoid loaning to over burdened players with active loan obligations or a history of fraud
                
                Anyone with the INTERNAL_AFFAIRS role can create loans.
                All loan information is publicly viewable.
                Loans are tied to the server/alliance creating them.  
                Only those with the `INTERNAL_AFFAIRS` role can update or remove a loan.
                """,
                "# Valid loan status\n" +
                Arrays.stream(DBLoan.Status.values()).map(f -> "- " + f.name()).collect(Collectors.joining("\n")),
                "# Adding, updating and removing a single loan",
                commandMarkdownSpoiler(CM.report.loan.add.cmd),
                commandMarkdownSpoiler(CM.report.loan.update.cmd),
                commandMarkdownSpoiler(CM.report.loan.remove.cmd),
                "# Viewing loans",
                commandMarkdownSpoiler(CM.report.loan.sheet.cmd),
                "# Using Sheets to bulk add, update or replace loans",
                commandMarkdownSpoiler(CM.report.loan.upload.cmd),
                "# Bulk deleting loans",
                commandMarkdownSpoiler(CM.report.loan.purge.cmd),
                "# Marking loan information as updated",
                "Active loans which have been reported will be flagged as outdated if they have not been updated in over 7 days",
                "To mark all loans as updated, use the following command",
                commandMarkdownSpoiler(CM.report.loan.update_all.cmd),
                "# Global Loan and Report alert channel",
                commandMarkdownSpoiler(CM.settings_orbis_alerts.REPORT_ALERT_CHANNEL.cmd),
                "# Player bank discord server",
                "`Orbis Business & Innovation Forum` is a player run discord server for fostering communication between banks",
                "- <https://discord.gg/j4yFQeEd5W>"
        );
    }
}
