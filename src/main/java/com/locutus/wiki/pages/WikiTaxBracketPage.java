package com.locutus.wiki.pages;

import com.locutus.wiki.WikiGen;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;

public class WikiTaxBracketPage extends WikiGen {
    public WikiTaxBracketPage(CommandManager2 manager) {
        super(manager, "tax_automation");
    }

    @Override
    public String generateMarkdown() {
        return build(

        );
       /*
       Set api key to track taxes

       Checking tax deposits (single, multiple, time period)
       /sheets_econ taxBracketSheet
        /sheets_econ taxRecords
        /sheets_econ taxRevenue

       Internal External tax rates
       /deposits check (with the itemized)

       Including taxes in deposits
       Using the `#tax` note / addbalance
       includeBaseTaxes

       Resetting tax deposits / resetdeposits

       Tax bracket accounts
        - How to check balance
        - What funds are included (the funds not assigned to member deposits, default all of it)
        - The bracket argument for transfer commands
            - taxAccount
            - existingTaxAccount
            - give example
        - Adjusting deposits for bracket account

       Tax set bracket commands

       Tax bracket auto

       Tax internal auto

       ^ The commands for that

       Other tax commands
       /nation set taxbracket
        /nation set taxbracketAuto
        /nation set taxinternal
        /nation set taxinternalAuto

        /tax bracketsheet
        /tax deposits
        /tax info
        /tax listBracketAuto
        /tax records
        /tax setNationBracketAuto

       Other tax settings
       /settings_tax MEMBER_CAN_SET_BRACKET
        /settings_tax REQUIRED_INTERNAL_TAXRATE
        /settings_tax REQUIRED_TAX_BRACKET
        /settings_tax TAX_BASE
        /settings_tax addRequiredBracket
        /settings_tax addRequiredInternalTaxrate
       TODO
        */
    }
}
