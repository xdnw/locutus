package com.locutus.wiki.pages;

import com.locutus.wiki.BotWikiGen;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.user.Roles;

public class WikiAuditingPage extends BotWikiGen {
    public WikiAuditingPage(CommandManager2 manager) {
        super(manager, "auditing");
    }

    @Override
    public String generateMarkdown() {
        return build(
        """
        - Generate and send out audit reports
        - Create sheets of nation info you can review
        - Bans, multis, player reports
        - Timers and slots
        - And more!""",
        "# Auditing MMR",
        commandMarkdownSpoiler(CM.settings_audit.REQUIRED_MMR.cmd),
        commandMarkdownSpoiler(CM.settings_audit.addRequiredMMR.cmd),
        commandMarkdownSpoiler(CM.sheets_milcom.MMRSheet.cmd),
        "# Auditing Warchest",
        commandMarkdownSpoiler(CM.settings_audit.WARCHEST_PER_CITY.cmd),
        commandMarkdownSpoiler(CM.sheets_econ.warchestSheet.cmd),
        "# Join Leave Alerts",
        commandMarkdownSpoiler(CM.settings_audit.MEMBER_LEAVE_ALERT_CHANNEL.cmd),
        "# Automatic Attack Audits",
        "Set an opt out role on discord",
        CM.role.setAlias.cmd.create(Roles.AUDIT_ALERT_OPT_OUT.name(), "@audit_opt_out", null, null).toString(),
        commandMarkdownSpoiler(CM.settings_audit.MEMBER_AUDIT_ALERTS.cmd),
        commandMarkdownSpoiler(CM.alerts.audit.optout.cmd),
        "# Create or send audit reports",
        "Run audits on a nation, multiple nations, and optionally mail results",
        commandMarkdownSpoiler(CM.audit.run.cmd),
        commandMarkdownSpoiler(CM.settings_audit.DISABLED_MEMBER_AUDITS.cmd),
        commandMarkdownSpoiler(CM.settings_audit.MEMBER_REBUY_INFRA_ALERT.cmd),
        "# New applicant auditing",
        commandMarkdownSpoiler(CM.report.analyze.cmd),
        commandMarkdownSpoiler(CM.nation.departures.cmd),
        commandMarkdownSpoiler(CM.nation.list.bans.cmd),
        commandMarkdownSpoiler(CM.nation.list.multi.cmd),
        "# Econ related auditing",
        commandMarkdownSpoiler(CM.sheets_econ.revenueSheet.cmd),
        commandMarkdownSpoiler(CM.sheets_econ.stockpileSheet.cmd),
        commandMarkdownSpoiler(CM.sheets_econ.ProjectSheet.cmd),
        commandMarkdownSpoiler(CM.project.slots.cmd),
        commandMarkdownSpoiler(CM.nation.TurnTimer.cmd),
        commandMarkdownSpoiler(CM.build.get.cmd),
        commandMarkdownSpoiler(CM.sheets_econ.taxBracketSheet.cmd),
        commandMarkdownSpoiler(CM.city.optimalBuild.cmd),
        "# Day change",
        commandMarkdownSpoiler(CM.sheets_ia.daychange.cmd),
        "# Espionage",
        "## Nations not buying spies",
        commandMarkdownSpoiler(CM.audit.hasNotBoughtSpies.cmd),
        "## Nations not using spy ops",
        commandMarkdownSpoiler(CM.spy.sheet.free_ops.cmd)
        );
    }
}
