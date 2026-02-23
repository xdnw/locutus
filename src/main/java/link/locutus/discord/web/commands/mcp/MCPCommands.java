package link.locutus.discord.web.commands.mcp;

import link.locutus.discord.commands.manager.v2.binding.annotation.Command;

public class MCPCommands {
    @Command(desc="Test command")
    public String test() {
        return "Test command executed. Secret word is: watermelon";
    }
}
