package com.locutus.wiki.pages;

import com.locutus.wiki.WikiGen;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;

public class WikiEscrowPage extends WikiGen {
    public WikiEscrowPage(CommandManager2 manager) {
        super(manager, "embassies");
    }

    @Override
    public String generateMarkdown() {
       /*
       State purpose of escrow system and how it generally works
       i.e. escrow funds if a nation is blockaded that they can withdraw when they leave blockade

       Escrow can be set to expire
       You can set or add to the escrow for the entire alliance
       You can modify the escrow based on nation deposits, number of cities, stockpile, or current military units

        Will show up in member deposits
        `/deposits check`

       borg: /escrow withdraw receiver: amount:
Withdraw from your escrow account
/escrow add nations:
Econ command to add to a nation's escrow
/escrow set nations:
Econ command to set nation's escrow
/escrow set_sheet sheet:
Import escrow values from a sheet
/escrow view_sheet
Get a sheet of escrow
/deposits sheet
Get a sheet of deposits + escrow
/deposits reset nations:
Reset deposits (incl escrow)
Legacy commands with an escrow argument
!warchest
!disperse
!grant
!transfer
(These typically redirect to /transfer resources or /transfer bulk)

Slash commands with escrowMode argument
/transfer bulk
/transfer resources
/transfer raws
/transfer self
/transfer warchest


Escrow modes
NEVER - Does not escrow. If a receiver is blockaded the command fails
WHEN_BLOCKADED - If blockaded puts the funds into the escrow account for the receiver to withdraw later, else transfers funds
ALWAYS - Does not transfer funds, always escrow


Note: A transfer will deduct from nation deposits (if not #ignore) even if it gets escrowed.
The escrowed funds can be withdrawn regardless of deposits (or other debt)
The alliance's offshore balance is only deducted when funds are sent ingame (i.e. escrowing funds does not deduct from offshore balance)
        */
    }
}
