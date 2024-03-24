package link.locutus.wiki.pages;

import link.locutus.wiki.BotWikiGen;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;

public class WikiSelfRoles extends BotWikiGen {
    public WikiSelfRoles(CommandManager2 manager) {
        super(manager, "multiple_servers");
    }

    @Override
    public String generateMarkdown() {
        // /settings_role ASSIGNABLE_ROLES
        // and the commansd role self
        // add role to all members
        // mask command?
        return build(

        );
    }
}
