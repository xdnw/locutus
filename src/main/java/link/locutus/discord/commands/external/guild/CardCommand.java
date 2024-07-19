package link.locutus.discord.commands.external.guild;

import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.Noformat;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class CardCommand extends Command implements Noformat {
    public CardCommand() {
        super("card", "embed", CommandCategory.INTERNAL_AFFAIRS, CommandCategory.GOV);
    }

    @Override
    public String help() {
        return super.help() + " <title> <message> <commands>";
    }

    @Override
    public List<CommandRef> getSlashReference() {
        return List.of(
                CM.embed.create.cmd,
                CM.embed.update.cmd,
                CM.embed.add.command.cmd,
                CM.embed.add.modal.cmd,
                CM.embed.add.raw.cmd,
                CM.embed.rename.button.cmd
        );
    }
    @Override
    public String desc() {
        return "Generate a card which runs a command when users react to it.\nPut commands inside \"quotes\".\n" +
                "Prefix a command with a #channel e.g. `\"#channel " + Settings.commandPrefix(true) + "command\"` to have the command output go there\n\n" +
                "Prefix the command with:" +
                "`~" + Settings.commandPrefix(true) + "command` to remove the user's reaction upon use and keep the card\n" +
                "`_" + Settings.commandPrefix(true) + "command` to remove ALL reactions upon use and keep the card\n" +
                "`." + Settings.commandPrefix(true) + "command` to keep the card upon use\n\n" +
                "Example:\n" +
                "`" + Settings.commandPrefix(true) + "embed 'Some Title' 'My First Embed' '~" + Settings.commandPrefix(true) + "say Hello {nation}' '" + Settings.commandPrefix(true) + "say Goodbye {nation}'`";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return Roles.INTERNAL_AFFAIRS.has(user, server);
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() < 2) return usage(args.size(), 2, channel);

        String title = args.get(0);
        String body = args.get(1);

        List<String> commands = new ArrayList<>();
        for (int i = 2; i < args.size(); i++) {
            String cmd = args.get(i);
            commands.add(cmd);
        }

        String emoji = "\ufe0f\u20e3";

        if (commands.size() > 10) {
            return "Too many commands (max: 10, provided: " + commands.size() + ")\n" +
                    "Note: Commands must be inside \"double quotes\", and each subsequent command separated by a space.";
        }

        IMessageBuilder msg = channel.create().embed(title, body);
        for (int i = 0; i < commands.size(); i++) {
            String cmd = commands.get(i);
            String codePoint = i + emoji;
            msg.commandButton(cmd, codePoint);
        }
        msg.send();
        return null;
    }
}
