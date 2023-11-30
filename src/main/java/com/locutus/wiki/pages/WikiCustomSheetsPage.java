package com.locutus.wiki.pages;

import com.locutus.wiki.BotWikiGen;
import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.commands.manager.v2.impl.pw.commands.CustomSheetCommands;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.StringMan;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

public class WikiCustomSheetsPage extends BotWikiGen {
    public WikiCustomSheetsPage(CommandManager2 manager) {
        super(manager, "custom spreadsheets");
    }

    @Override
    public String generateMarkdown() {
        return build(
                """
                        <bold style="color:red">NOT PUBLICLY AVAILABLE. The documentation below applies to a feature currently in development, and not yet available for public use</bold>
                        - Use premade selections and sheet templates (WIP)
                        - Select the data you want in your sheet
                        - Select the columns you want for that data
                        - Add it as a tab in a custom sheet
                        - Update the sheet's tabs using a command
                        - Update your selection or columns at any time
                        """,
                "# Premade sheet templates/selections",
                "To be added. NOT currently working with custom sheet templates. For now: See the various disunified sheet commands below",
                MarkupUtil.spoiler("Internal Affairs Sheets",
                        MarkupUtil.markdownToHTML(StringMan.join(Arrays.asList(
                CM.audit.sheet.cmd.toString(),
                CM.sheets_ia.daychange.cmd.toString(),
                CM.sheets_ia.ActivitySheet.cmd.toString(),
                CM.sheets_ia.ActivitySheetFromId.cmd.toString()), "\n"))),
                        MarkupUtil.spoiler("Milcom Sheets",
                                MarkupUtil.markdownToHTML(StringMan.join(Arrays.asList(
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
                CM.sheets_milcom.DeserterSheet.cmd.toString()), "\n"))),
                MarkupUtil.spoiler("Econ Sheets",
                        MarkupUtil.markdownToHTML(StringMan.join(Arrays.asList(
                CM.escrow.view_sheet.cmd.toString(),
                CM.sheets_econ.warchestSheet.cmd.toString(),
                CM.sheets_econ.stockpileSheet.cmd.toString(),
                CM.sheets_econ.revenueSheet.cmd.toString(),
                CM.sheets_econ.taxBracketSheet.cmd.toString(),
                CM.sheets_econ.taxRevenue.cmd.toString(),
                CM.sheets_econ.taxRecords.cmd.toString(),
                CM.tax.deposits.cmd.toString(),
                CM.sheets_econ.ProjectSheet.cmd.toString(),
                CM.sheets_econ.projectCostCsv.cmd.toString(),
                CM.bank.records.cmd.toString(),
                CM.sheets_econ.getIngameNationTransfers.cmd.toString(),
                CM.sheets_econ.getIngameTransactions.cmd.toString(),
                CM.sheets_econ.IngameNationTransfersByReceiver.cmd.toString(),
                CM.sheets_econ.IngameNationTransfersBySender.cmd.toString(),

                CM.sheets_econ.warReimburseByNationCsv.cmd.toString()), "\n"))),
                MarkupUtil.spoiler("General sheets",
                        MarkupUtil.markdownToHTML(StringMan.join(Arrays.asList(
                CM.nation.sheet.NationSheet.cmd.toString(),
                CM.sheets_ia.AllianceSheet.cmd.toString(),
                CM.alliance.stats.allianceNationsSheet.cmd.toString(),
                CM.report.sheet.generate.cmd.toString(),
                CM.report.loan.sheet.cmd.toString()), "\n"))),
                "## List and configure",
//                commandMarkdownSpoiler(CM.sheet_template.list.cmd),
                "# Statistic sheets",
                "Add `attachCsv: True` for any graph command to attach a csv file of the selected data",
                "# Selections",
                "Selections are used for sheets, and as inputs to certain commands (such as a selection of nations)",
                "Use a comma separated list of ids/names, alongside filters to select records, entities, or types",
                "See a page below for syntax and a list of supported filters",
                "## Selection types",
                "- " + Locutus.cmd().getV2().getPlaceholders().getTypes().stream()
                        .map(f -> MarkupUtil.markdownUrl(PlaceholdersMap.getClassName(f), "../wiki/" + PlaceholdersMap.getClassName(f).toLowerCase(Locale.ROOT) + "_placeholders"))
                        .sorted()
                        .collect(Collectors.joining("\n- ")),
                "## Selection alias",
                "A name you set for a selection",
                "To reference your named selection, use e.g. `$myAlias` or `select:myAlias`",
                "### Add an alias",
                commandMarkdownSpoiler(CM.selection_alias.add.nation.cmd, false) +
                commandMarkdownSpoiler(CM.selection_alias.add.alliance.cmd, false) +
                commandMarkdownSpoiler(CM.selection_alias.add.nationoralliance.cmd, false) +
                commandMarkdownSpoiler(CM.selection_alias.add.continent.cmd, false) +
                commandMarkdownSpoiler(CM.selection_alias.add.guild.cmd, false) +
                commandMarkdownSpoiler(CM.selection_alias.add.project.cmd, false) +
                commandMarkdownSpoiler(CM.selection_alias.add.treaty.cmd, false) +
                commandMarkdownSpoiler(CM.selection_alias.add.ban.cmd, false) +
                commandMarkdownSpoiler(CM.selection_alias.add.resourcetype.cmd, false) +
                commandMarkdownSpoiler(CM.selection_alias.add.attacktype.cmd, false) +
                commandMarkdownSpoiler(CM.selection_alias.add.militaryunit.cmd, false) +
                commandMarkdownSpoiler(CM.selection_alias.add.treatytype.cmd, false) +
                commandMarkdownSpoiler(CM.selection_alias.add.treasure.cmd, false) +
                commandMarkdownSpoiler(CM.selection_alias.add.nationcolor.cmd, false) +
                commandMarkdownSpoiler(CM.selection_alias.add.building.cmd, false) +
                commandMarkdownSpoiler(CM.selection_alias.add.audittype.cmd, false) +
                commandMarkdownSpoiler(CM.selection_alias.add.nationlist.cmd, false) +
                commandMarkdownSpoiler(CM.selection_alias.add.bounty.cmd, false) +
                commandMarkdownSpoiler(CM.selection_alias.add.city.cmd, false) +
                commandMarkdownSpoiler(CM.selection_alias.add.taxbracket.cmd, false) +
                commandMarkdownSpoiler(CM.selection_alias.add.user.cmd, false),
//                commandMarkdownSpoiler(CM.selection_alias.add.transaction.cmd),
//                commandMarkdownSpoiler(CM.selection_alias.add.trade.cmd),
//                commandMarkdownSpoiler(CM.selection_alias.add.attack.cmd),
//                commandMarkdownSpoiler(CM.selection_alias.add.war.cmd),
//                commandMarkdownSpoiler(CM.selection_alias.add.taxdeposit.cmd),
                "### List or remove aliases",
                commandMarkdownSpoiler(CM.selection_alias.list.cmd),
                commandMarkdownSpoiler(CM.selection_alias.remove.cmd),
                "# Sheet templates",
                "A list of columns",
                "See the type pages above for supported placeholders",
                "Use `{row}` and `{column}` to reference the current row and column",
                "Templates are used alongside a selection to create a sheet tab",
                "## Add columns to a template",
                "A template will be created if one does not already exist",
                "Use the add command multiple times to add more than 25 columns",
                commandMarkdownSpoiler(CM.sheet_template.add.nation.cmd, false) +
                commandMarkdownSpoiler(CM.sheet_template.add.alliance.cmd, false) +
                commandMarkdownSpoiler(CM.sheet_template.add.nationoralliance.cmd, false) +
                commandMarkdownSpoiler(CM.sheet_template.add.continent.cmd, false) +
                commandMarkdownSpoiler(CM.sheet_template.add.guild.cmd, false) +
                commandMarkdownSpoiler(CM.sheet_template.add.project.cmd, false) +
                commandMarkdownSpoiler(CM.sheet_template.add.treaty.cmd, false) +
                commandMarkdownSpoiler(CM.sheet_template.add.ban.cmd, false) +
                commandMarkdownSpoiler(CM.sheet_template.add.resourcetype.cmd, false) +
                commandMarkdownSpoiler(CM.sheet_template.add.attacktype.cmd, false) +
                commandMarkdownSpoiler(CM.sheet_template.add.militaryunit.cmd, false) +
                commandMarkdownSpoiler(CM.sheet_template.add.treatytype.cmd, false) +
                commandMarkdownSpoiler(CM.sheet_template.add.treasure.cmd, false) +
                commandMarkdownSpoiler(CM.sheet_template.add.nationcolor.cmd, false) +
                commandMarkdownSpoiler(CM.sheet_template.add.building.cmd, false) +
                commandMarkdownSpoiler(CM.sheet_template.add.audittype.cmd, false) +
                commandMarkdownSpoiler(CM.sheet_template.add.nationlist.cmd, false) +
                commandMarkdownSpoiler(CM.sheet_template.add.bounty.cmd, false) +
                commandMarkdownSpoiler(CM.sheet_template.add.city.cmd, false) +
                commandMarkdownSpoiler(CM.sheet_template.add.taxbracket.cmd, false) +
                commandMarkdownSpoiler(CM.sheet_template.add.user.cmd, false),
//                commandMarkdownSpoiler(CM.sheet_template.add.transaction.cmd),
//                commandMarkdownSpoiler(CM.sheet_template.add.trade.cmd),
//                commandMarkdownSpoiler(CM.sheet_template.add.attack.cmd),
//                commandMarkdownSpoiler(CM.sheet_template.add.war.cmd),
//                commandMarkdownSpoiler(CM.sheet_template.add.taxdeposit.cmd),
                "### View, list, remove or modify a template",
                commandMarkdownSpoiler(CM.sheet_template.view.cmd),
                commandMarkdownSpoiler(CM.sheet_template.list.cmd),
                commandMarkdownSpoiler(CM.sheet_template.remove.cmd),
                commandMarkdownSpoiler(CM.sheet_template.remove_column.cmd),
//                commandMarkdownSpoiler(CM.sheet_template.rename.cmd),
                "# Creating tabbed sheet",
                commandMarkdownSpoiler(CM.sheet_custom.add_tab.cmd),
                commandMarkdownSpoiler(CM.sheet_custom.update.cmd),
                "## List and view custom sheets",
                commandMarkdownSpoiler(CM.sheet_custom.list.cmd),
                commandMarkdownSpoiler(CM.sheet_custom.view.cmd),
                "## Remove a tab from a sheet",
                commandMarkdownSpoiler(CM.sheet_custom.remove_tab.cmd),
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
