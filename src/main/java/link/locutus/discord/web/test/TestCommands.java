package link.locutus.discord.web.test;

import link.locutus.discord.commands.manager.v2.binding.annotation.Command;

public class TestCommands {

    @Command(desc = "Dummy command. No output")
    public String dummy() {
        return null;
    }
}