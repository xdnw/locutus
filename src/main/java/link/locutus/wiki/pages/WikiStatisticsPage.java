package link.locutus.wiki.pages;

import link.locutus.wiki.BotWikiGen;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;

public class WikiStatisticsPage extends BotWikiGen {
    public WikiStatisticsPage(CommandManager2 manager) {
        super(manager, "statistics");
    }

    @Override
    public String generateMarkdown() {
        return build(

        );
       /*
       TODO list general things the commands can do in an overview

       Give example of the attribute system
       Link the nation attributes page

       Give some examples of statistic commands

        Statistic commands
       TODO go through all the commands

       Link CTOwned? for additional war stats
        */
    }
}
