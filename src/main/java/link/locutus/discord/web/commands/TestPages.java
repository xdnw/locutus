package link.locutus.discord.web.commands;

import link.locutus.discord.commands.manager.v2.binding.annotation.Command;

public class TestPages {
    @Command(desc = "Show running tax expenses by day by bracket")
    public Object testIndex() {
        return "Test index";
    }

    @Command
    public Object testPost(String argument) {
        return "Test post";
    }
}
