package link.locutus.discord.web.test;

import cn.easyproject.easyocr.ImageType;
import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Arg;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.command.ICommand;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.command.IModalBuilder;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.commands.manager.v2.impl.pw.NationPlaceholder;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.NationPlaceholders;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.pnw.SimpleNationList;
import link.locutus.discord.util.ImageUtil;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.StringMan;
import net.dv8tion.jda.api.entities.User;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TestCommands {

    @Command(desc = "Dummy command. No output")
    public String dummy() {
        return null;
    }

    @Command(desc = "Create a discord modal for a bot command\n" +
            "This will make a popup prompting for the command arguments you specify and submit any defaults you provide\n" +
            "Note: This is intended to be used in conjuction with the card command")
    public String modal(@Me IMessageIO io, ICommand command,
                        @Arg("A comma separated list of the command arguments to prompt for") String arguments,
                        @Arg("The default arguments and values you want to submit to the command\n" +
                                "Example: `myarg1:myvalue1 myarg2:myvalue2`")
                        @Default String defaults) {
        Map<String, String> args;
        if (defaults == null) {
            args = new HashMap<>();
        } else if (defaults.startsWith("{") && defaults.endsWith("}")) {
            args = PnwUtil.parseMap(defaults);
        } else {
            args = CommandManager2.parseArguments(command.getUserParameterMap().keySet(), defaults, true);
        }
        io.modal().create(command, args, StringMan.split(arguments, ',')).send();
        return null;
    }

    @Command(desc = "Get the text from a discord image\n" +
            "It is recommended to crop the image first")
    public String ocr(String discordImageUrl) {
        String text = ImageUtil.getText(discordImageUrl);
        return "```\n" +text + "\n```\n";
    }

    @Command
    public String test(NationPlaceholders placeholders, ValueStore store, String input, @Me DBNation me, @Me User user) {
        if (me != null) {
            System.out.println("Me " + me.getNationUrl());
        } else {
            System.out.println("Me is null");
        }
        DBNation t = (DBNation) store.getProvided(Key.of(DBNation.class, Me.class));
        if (t != null) {
            System.out.println("Nation " + t.getMarkdownUrl());
        }
        System.out.println("Store " + store.toString());

        Set<DBNation> nations = placeholders.parseSet(store, input);
        
        StringBuilder response = new StringBuilder();
        response.append(nations.size() + " nations found\n");
        if (nations.size() < 100) {
            // print each
            for (DBNation nation : nations) {
                response.append(nation.getNationUrlMarkup(false) + " | " + nation.getAllianceUrlMarkup(false)).append("\n");
            }
        }
        return response.toString() + " | " + me.getNationUrlMarkup(false) + " | " + user.getAsMention();
    }
}