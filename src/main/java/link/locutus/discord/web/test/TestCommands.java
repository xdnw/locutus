package link.locutus.discord.web.test;

import cn.easyproject.easyocr.ImageType;
import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.commands.manager.v2.binding.annotation.Arg;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.command.ICommand;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.command.IModalBuilder;
import link.locutus.discord.util.ImageUtil;
import link.locutus.discord.util.PnwUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TestCommands {

    @Command(desc = "Dummy command. No output")
    public String dummy() {
        return null;
    }

    @Command(desc = "Create a discord modal for a bot command\n" +
            "This will make a popup prompting for the command arguments you specify and submit any defaults you provide\n" +
            "Note: This is intended to be used in conjuction with the card command")
    public String modal(@Me IMessageIO io, ICommand command,
                        @Arg("A comma separated list of the command arguments to prompt for") List<String> arguments,
                        @Arg("The json string of the default arguments and values you want to submit to the command")
                        @Default String defaults) {
        Map<String, String> args = defaults == null ? new HashMap<>() : PnwUtil.parseMap(defaults);
        io.modal().create(command, args, arguments).send();
        return null;
    }

    @Command(desc = "Get the text from a discord image\n" +
            "It is recommended to crop the image first")
    public String ocr(String discordImageUrl) {
        String text = ImageUtil.getText(discordImageUrl);
        return "```\n" +text + "\n```\n";
    }
}