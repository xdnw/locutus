package link.locutus.discord.web.test;

import link.locutus.discord.commands.manager.v2.binding.annotation.Command;

public class TestCommands {
//    @Command
//    public String test(@Me Guild guild, Set<TreatyType> treaties) throws IOException {
//        return "dummy";
//    }

    @Command(desc = "Dummy command. No output")
    public String dummy() {
        return null;
    }
}