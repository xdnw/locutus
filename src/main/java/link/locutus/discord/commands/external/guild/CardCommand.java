package link.locutus.discord.commands.external.guild;

import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.Noformat;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
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
    public String desc() {
        return "Generate a card which runs a command when users react to it.\nPut commands inside \"quotes\".\n" +
                "Prefix a command with a #channel e.g. `\"#channel " + Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "command\"` to have the command output go there\n\n" +
                "Prefix the command with:" +
                "`~" + Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "command` to remove the user's reaction upon use and keep the card\n" +
                "`_" + Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "command` to remove ALL reactions upon use and keep the card\n" +
                "`." + Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "command` to keep the card upon use\n\n" +
                "Example:\n" +
                "`" + Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "embed 'Some Title' 'My First Embed' '~" + Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "say Hello {nation}' '" + Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "say Goodbye {nation}'`";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return Roles.INTERNAL_AFFAIRS.has(user, server);
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() < 2) return usage(event);

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
                    "Note: Commands must be inside \"double quotes\", and each subsequent command separated by a space";
        }

        ArrayList<String> reactions = new ArrayList<String>();
        for (int i = 0; i < commands.size(); i++) {
            String cmd = commands.get(i);
            String codePoint = i + emoji;
            reactions.add(codePoint);
            reactions.add(cmd);
        }
        String[] reactionsArr = reactions.toArray(new String[0]);
        Message msg = DiscordUtil.createEmbedCommand(event.getChannel(), title, body, reactionsArr);
        return null;
    }
}
