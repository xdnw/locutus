package link.locutus.discord.commands.fun;

import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.CommandManager;
import link.locutus.discord.config.Settings;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.Arrays;
import java.util.List;

public class TagCommand extends Command {
    private final List<String> random;
    private final CommandManager manager;

    public TagCommand(CommandManager manager) {
        super("tag", "titan", CommandCategory.FUN);
        this.random = Arrays.asList(
                "Titan? More like loosen, amiright? lol, jk, please don't hurt me",
                "Tighten your umbilical cord, because Titan is here!",
                "What's the difference between Titan the moon and our supreme overlord Titan? One is colder than the moons of Saturn, the other is... a moon of Saturn.",
                "Titan:\n1. any of a family of giants in Greek mythology born of Uranus and Gaea and ruling the earth until overthrown by the Olympian gods.\n2. Some bottom feeder who plays the clicker game PoliticsAndWar",
                "Performing SEO optimization... Titan, Titanium, Metal, Disco, Discovery...",
                "Did you mean, `Titan!`?",
                "What's the difference between Titan and the Titanic? One displaces 52,310 tones of water, the other is an ocean liner at the bottom of the Atlantic.",
                "Titan's cities are 100% radioactive green. All nuclear waste goes only in the finest lakes and rivers.",
                "titan the ropes, we're going for a bumpy ride!"
        );
        this.manager = manager;
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String help() {
        return Settings.commandPrefix(true) + "tag";
    }

    @Override
    public String desc() {
        return "Play a game of tag";
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        String tagResult = manager.getTag().tag(event);
        if (tagResult != null) {
            return tagResult;
        }
        User it = event.getJDA().getUserById(manager.getTag().getIt());
        if (it != null) return it.getName() + " is it!";
        return "No-one is playing tag right now";
//        String msg = random.get(new Random().nextInt(random.size()));
//        return msg;
    }
}
