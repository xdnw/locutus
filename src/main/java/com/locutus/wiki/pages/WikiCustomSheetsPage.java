package com.locutus.wiki.pages;

import com.locutus.wiki.BotWikiGen;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.commands.manager.v2.impl.pw.commands.CustomSheetCommands;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.StringMan;

import java.util.Arrays;

public class WikiCustomSheetsPage extends BotWikiGen {
    public WikiCustomSheetsPage(CommandManager2 manager) {
        super(manager, "custom spreadsheets");
    }

    @Override
    public String generateMarkdown() {
        return build(
                """
                        - Use premade selections and sheet templates (WIP)
                        - Select the data you want in your sheet
                        - Select the columns you want for that data
                        - Add it as a tab in a custom sheet
                        - Update the sheet's tabs using a command
                        - Update your selection or columns at any time
                        """,
                "# Premade sheet templates/selections",
                "To be added. Cannot be used alongside custom sheet templates. See the various disunified sheet commands below",
                MarkupUtil.spoiler("Internal Affairs Sheets",
                        StringMan.join(Arrays.asList(
                CM.audit.sheet.cmd.toString(),
                CM.sheets_ia.daychange.cmd.toString(),
                CM.sheets_ia.ActivitySheet.cmd.toString(),
                CM.sheets_ia.ActivitySheetFromId.cmd.toString()), "\n")),
                "## For milcom",
                        MarkupUtil.spoiler("Milcom Sheets",
                                StringMan.join(Arrays.asList(
                CM.sheets_milcom.MMRSheet.cmd.toString(),
                CM.sheets_milcom.warchestSheet.cmd.toString(),
                CM.sheets_milcom.warSheet.cmd.toString(),
                CM.war.sheet.blitzSheet.cmd.toString(),
                CM.war.sheet.validate.cmd.toString(),
                CM.sheets_milcom.combatantSheet.cmd.toString(),
                CM.sheets_milcom.IntelOpSheet.cmd.toString(),
                CM.sheets_milcom.SpySheet.cmd.toString(),
                CM.sheets_milcom.listSpyTargets.cmd.toString(),
                CM.sheets_milcom.convertdtcspysheet.cmd.toString(),
                CM.sheets_milcom.convertHidudeSpySheet.cmd.toString(),
                CM.sheets_milcom.convertTKRSpySheet.cmd.toString(),
                CM.sheets_milcom.validateSpyBlitzSheet.cmd.toString(),
                CM.audit.hasNotBoughtSpies.cmd.toString(),
                CM.sheets_milcom.WarCostByAllianceSheet.cmd.toString(),
                CM.sheets_milcom.WarCostByResourceSheet.cmd.toString(),
                CM.sheets_milcom.WarCostSheet.cmd.toString(),
                CM.sheets_milcom.lootValueSheet.cmd.toString(),
                CM.sheets_milcom.DeserterSheet.cmd.toString()), "\n")),
                MarkupUtil.spoiler("Econ Sheets",
                        StringMan.join(Arrays.asList(
                CM.tax.deposits.cmd.toString(),
                CM.escrow.view_sheet.cmd.toString(),
                CM.sheets_econ.warchestSheet.cmd.toString(),
                CM.sheets_econ.stockpileSheet.cmd.toString(),
                CM.sheets_econ.revenueSheet.cmd.toString(),
                CM.sheets_econ.taxBracketSheet.cmd.toString(),
                CM.sheets_econ.taxRevenue.cmd.toString(),
                CM.sheets_econ.taxRecords.cmd.toString(),
                CM.sheets_econ.ProjectSheet.cmd.toString(),
                CM.sheets_econ.projectCostCsv.cmd.toString(),
                CM.bank.records.cmd.toString(),
                CM.sheets_econ.getIngameNationTransfers.cmd.toString(),
                CM.sheets_econ.getIngameTransactions.cmd.toString(),
                CM.sheets_econ.IngameNationTransfersByReceiver.cmd.toString(),
                CM.sheets_econ.IngameNationTransfersBySender.cmd.toString(),

                CM.sheets_econ.warReimburseByNationCsv.cmd.toString()), "\n")),
                MarkupUtil.spoiler("General sheets",
                        StringMan.join(Arrays.asList(
                CM.nation.sheet.NationSheet.cmd.toString(),
                CM.sheets_ia.AllianceSheet.cmd.toString(),
                CM.alliance.stats.allianceNationsSheet.cmd.toString(),
                CM.report.sheet.generate.cmd.toString(),
                CM.report.loan.sheet.cmd.toString()), "\n")),

                //////listSheetTemplates
////sheet_template list
//                this.commands.registerMethod(new CustomSheetCommands(), List.of("sheet_template"), "listSheetTemplates", "list");
//////listSelectionAliases
////selection_alias list
//        this.commands.registerMethod(new CustomSheetCommands(), List.of("selection_alias"), "listSelectionAliases", "list");
//////listCustomSheets
////sheet_custom list
//        this.commands.registerMethod(new CustomSheetCommands(), List.of("sheet_custom"), "listCustomSheets", "list");
//////deleteSelectionAlias
////selection_alias remove
//        this.commands.registerMethod(new CustomSheetCommands(), List.of("selection_alias"), "deleteSelectionAlias", "remove");
//////viewTemplate
////sheet_template view
//        this.commands.registerMethod(new CustomSheetCommands(), List.of("sheet_template"), "viewTemplate", "view");
//////deleteTemplate
////sheet_template remove
//        this.commands.registerMethod(new CustomSheetCommands(), List.of("sheet_template"), "deleteTemplate", "remove");
//////deleteColumns
////sheet_template remove_column
//        this.commands.registerMethod(new CustomSheetCommands(), List.of("sheet_template"), "deleteColumns", "remove_column");
//////addTab
////sheet_custom add
//        this.commands.registerMethod(new CustomSheetCommands(), List.of("sheet_custom"), "addTab", "add_tab");
//////updateSheet
////sheet_custom update
//        this.commands.registerMethod(new CustomSheetCommands(), List.of("sheet_custom"), "updateSheet", "update");
//////deleteTab
////sheet_custom remove_tab
//        this.commands.registerMethod(new CustomSheetCommands(), List.of("sheet_custom"), "deleteTab", "remove_tab");
//////info
////sheet_custom view
//        this.commands.registerMethod(new CustomSheetCommands(), List.of("sheet_custom"), "info", "view");
                ""
        );
       /*
       nationsheet
       alliancesheet
       alliancenationssheet
        */
    }
}
