package com.locutus.wiki.pages;

import com.locutus.wiki.WikiGen;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.db.entities.DBLoan;
import link.locutus.discord.db.guild.GuildKey;

import java.util.Arrays;
import java.util.stream.Collectors;

public class WikiLoanPage extends WikiGen {
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
                """,
                "# Valid loan status\n" +
                Arrays.stream(DBLoan.Status.values()).map(f -> "- " + f.name()).collect(Collectors.joining("\n")),
                "# Adding, updating and removing a single loan",
                commandMarkdown(CM.report.loan.add.cmd),
                commandMarkdown(CM.report.loan.update.cmd),
                commandMarkdown(CM.report.loan.remove.cmd),
                "# Viewing loans",
                commandMarkdown(CM.report.loan.sheet.cmd),
                "# Using Sheets to bulk add, update or replace loans",
                commandMarkdown(CM.report.loan.upload.cmd),
                "# Bulk deleting loans",
                commandMarkdown(CM.report.loan.purge.cmd),
                "# Marking loan information as updated",
                "Active loans which have been reported will be flagged as outdated if they have not been updated in over 7 days",
                "To mark all loans as updated, use the following command",
                commandMarkdown(CM.report.loan.update_all.cmd),
                "# Global Loan and Report alert channel",
                commandMarkdown(CM.settings_orbis_alerts.REPORT_ALERT_CHANNEL.cmd),
                "# Player bank discord server",
                "`Orbis Business & Innovation Forum` is a player run discord server for fostering communication between banks",
                "- <https://discord.gg/j4yFQeEd5W>"
        );
    }
}
