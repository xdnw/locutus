package link.locutus.discord.web.commands.page;

import link.locutus.discord.commands.manager.v2.binding.annotation.Command;

public class EndpointPages extends PageHelper {
    @Command
    public String test() {
        return "Hello World";
    }
}
