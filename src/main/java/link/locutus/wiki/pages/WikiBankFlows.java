package link.locutus.wiki.pages;

import link.locutus.wiki.BotWikiGen;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;

public class WikiBankFlows extends BotWikiGen {
    public WikiBankFlows(CommandManager2 manager) {
        super(manager, "bank_flows");
    }

    @Override
    public String generateMarkdown() {
        return build(
                "See first:" + linkPage("deposits"),
                "# Balance Flow types",
                "- INTERNAL - Balance changes due to discord commands (such as " + CM.deposits.add.cmd.toSlashCommand(true) + ")\n" +
                "- WITHDRAWAL - Transfers received by the nation\n" +
                "- DEPOSIT - Transfers deposited by the nation\n",
                "# Viewing Balance Flow breakdown",
                "## For a single nation", 
                commandMarkdownSpoiler(CM.deposits.flows.cmd),
                "e.g. with `note` set to `TRADE` to only show the flow for #trade",
                CM.deposits.flows.cmd.create("Borg", "TRADE").toSlashCommand(true),
                "## For multiple nations",
                """
                - `flow_internal` - funds added or removed via commands
                - `flow_withdrawal`  - funds withdrawn
                - `flow_deposit` - funds deposited
                
                By default flow breakdown will use all transfers, to specify a note, set  `useFlowNote` argument to only show the flow breakdown for that specific note (e.g. `TRADE`)""",
                CM.deposits.sheet.cmd.create(null, null, null, null, null, null, null, null, null, null, "TRADE").toString(),
                "# Adjusting Balance Flows",
                "Shift the transfer note notegory flows for a nation.",
                "Does not change overall or note balance unless it is shifted to `#ignore`",
                "By default this will shift it to `DEPOSIT`, though you can specify `noteTo`.",
                "Use a negative amount to subtract, and positive to add.",
                commandMarkdownSpoiler(CM.deposits.shiftFlow.cmd),
                "# Money Trades",
                "A money trade is between nations buying food for large $ amounts, such as $50m per food.",
                "Use the command to show the net money trades for a nation, categorized by receiver:",
                commandMarkdownSpoiler(CM.trade.moneyTrades.cmd)
        );
    }
}
