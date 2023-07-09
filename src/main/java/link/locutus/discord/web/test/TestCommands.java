package link.locutus.discord.web.test;

import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.command.ICommand;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.command.IModalBuilder;
import link.locutus.discord.util.PnwUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TestCommands {

    @Command(desc = "Dummy command. No output")
    public String dummy() {
        return null;
    }

    @Command
    public String modal(@Me IMessageIO io, ICommand command, List<String> arguments, @Default String defaults) {
        Map<String, String> args = defaults == null ? new HashMap<>() : PnwUtil.parseMap(defaults);
        io.modal().create(command, args, arguments).send();
        return null;
    }
}