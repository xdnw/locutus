package com.locutus.wiki.pages;

import com.locutus.wiki.WikiGen;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;

public class WikiTaxBracketPage extends WikiGen {
    public WikiTaxBracketPage(CommandManager2 manager) {
        super(manager, "tax_automation");
    }

    @Override
    public String generateMarkdown() {
        return build(
    """
            - Viewing tax records
            - Viewing tax revenue
            - Tax bracket accounts for growth circles
            - Including all or a portion of taxes in member deposits using internal tax rates
             - Default or per nation internal tax rates
             - Setting a nations tax bracket or internal tax rate
            - Adjusting or resetting deposits
            - Automated conditional assigning of member tax brackets and internal tax rates
            - Letting members change their bracket""",
            "# Prerequisites",
            "Register your alliance id and api key",
            commandMarkdownSpoiler(CM.settings_default.registerAlliance.cmd),
            commandMarkdownSpoiler(CM.settings_default.registerApiKey.cmd),
            "# Viewing tax records for a nation",
            commandMarkdownSpoiler(CM.tax.info.cmd),
            commandMarkdownSpoiler(CM.tax.records.cmd),
            commandMarkdownSpoiler(CM.tax.deposits.cmd),
            "# View tax brackets / revenue",
            commandMarkdownSpoiler(CM.sheets_econ.taxRevenue.cmd),
            commandMarkdownSpoiler(CM.sheets_econ.taxBracketSheet.cmd),
            "# Setting the tax bracket for a nation",
            commandMarkdownSpoiler(CM.nation.set.taxbracket.cmd),
            "# Tax bracket accounts",
            "By default, all taxes collected go into the tax bracket account",
            "This can be useful to tracking how much members pay into a growth circle",
            "### Checking bracket balance",
            "Obtain the id of the tax bracket from the alliance's tax page",
            CM.deposits.check.cmd.create("#tax_id=1234", null, null, null, null, Boolean.TRUE + "", null, null, null, null).toString(),
            "### Withdrawing from the tax bracket account",
            "Use e.g. `taxAccount: #tax_id=1234` argument in any transfer slash command e.g.",
            CM.transfer.resources.cmd.create("Borg", "food=100", null, null, null, null, "#tax_id=1234", null, null, null, null, null, null, null, null).toString(),
            "Or use e.g. `existingTaxAccount: True` argument to withdraw from the bracket the receiver is currently on",
            CM.transfer.resources.cmd.create("Alex", "{coal=100,money=10}", null, null, null, null, null, "True", null, null, null, null, null, null, Boolean.TRUE + "").toString(),
            "For the legacy transfer commands `!` add e.g. `tax_id:1234` for a specific tax account, or add `-t` for the receiver's tax account",
            "### Adjusting the balance of a tax bracket account",
            "Use the deposits add command, use a negative amount to subtract",
            CM.deposits.add.cmd.create("#tax_id=1234", "money=100", "#tax", null).toString(),
            "# Internal tax rates",
            "- Internal tax rates are used to calculate the amount of taxes to exclude from member balances",
            "- By default, all taxes are excluded in member balances (internal tax rate of 100/100)",
            "- Internal tax rates are absolute threshold. That means only in-game tax rates above the internal rate will go into member balances",
            " - For example, an internal rate of 25/25, and an in-game tax rate of 25/25 will have ZERO taxes included in member balances",
            " - An internal tax rate of 0/0 will include all taxes in member balances",
            "## Setting a default internal tax rate",
            "This setting is retroactive, and will affect all previous tax records that lack an internal tax rate",
            "You can make a copy of member deposits beforehand, and correct any member deposits that were affected by the change. See: " + linkPage("deposits"),
            commandMarkdownSpoiler(CM.settings_tax.TAX_BASE.cmd),
            "## Per nation internal tax rate",
            "The internal tax rate can be viewed in the tax bracket sheet (see above)",
            commandMarkdownSpoiler(CM.nation.set.taxinternal.cmd),
            "## Adjusting taxes in deposits",
            "Use the deposits add command, use a negative amount to subtract",
            CM.deposits.add.cmd.create("https://politicsandwar.com/nation/id=189573", "$12.5m", "#tax", null).toString(),
            "## Resetting taxes in deposits",
            CM.deposits.reset.cmd.create("Borg", "True", "True", "False", "True", "True", null).toString(),
            "### See also: " + linkPage("deposits"),
            "# Automated conditional tax brackets",
            commandMarkdownSpoiler(CM.settings_tax.REQUIRED_TAX_BRACKET.cmd),
            commandMarkdownSpoiler(CM.tax.listBracketAuto.cmd),
            commandMarkdownSpoiler(CM.tax.setNationBracketAuto.cmd),
            "# Automated conditional internal rates",
            commandMarkdownSpoiler(CM.settings_tax.REQUIRED_INTERNAL_TAXRATE.cmd),
            commandMarkdownSpoiler(CM.tax.listBracketAuto.cmd),
            commandMarkdownSpoiler(CM.nation.set.taxinternalAuto.cmd),
            "# Letting members change their tax bracket",
            commandMarkdownSpoiler(CM.settings_tax.MEMBER_CAN_SET_BRACKET.cmd)
        );
    }
}
