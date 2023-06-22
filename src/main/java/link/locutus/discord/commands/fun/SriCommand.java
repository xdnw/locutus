package link.locutus.discord.commands.fun;

import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.DBNation;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class SriCommand extends Command {
    private final List<String> random;

    public SriCommand() {
        super("sri", CommandCategory.FUN);
        this.random = Arrays.asList(
                "There's a sprocket in my pocket; fell out of the wocket in my locket. ",
                "I'm in silky smooth 20fps baby!",
                "60% of the time I work every time",
                "Bing bang boing bop, I waste my time reading these messages.",
                "Help, I'm stuck in a message factory",
                "Like rollercoasters?, than you'll love our stock options!",
                "May contain traces of nuts and bolts",
                "Looking for a cheesy comment? This one is grate.",
                "One's ability to endure this nonesense likely stems from ignorance of alternatives",
                "99% of the admins give the rest a bad name",
                "Lucutus only eats brains, so you have nothing to worry about",
                "You this read wrong.",
                "Knowledge is power. Absolute knowledge corrupts absolutely.",
                "You can trust the authenticity of these texts- Albert Einstein",
                "Jesus may be able to walk on water, but I can swim on dry land",
                "You can get these messages in any language as long as it is English",
                "This bot is SEO optimized, optimize, optimum, boost, lift, elevator, chute, slide...",
                "The best way to accelerate a human's development is at 9.8m/s^2",
                "Do not believe free tips you did not make up yourself.",
                "Humans, I adore them. A little salt, a squeeze of lemon... perfect.",
                "Humans, Can't live with them... yup, that's it.",
                "I prefer skittles to M&M",
                "Unicomplex 1, more like Unicomplex 42069, amiright?",
                "We are the Borg. Existence, as you know it, is over.",
                "Hello Mom",
                "You ever wonder why we're here?",
                "This bot, brought to you by the inventors of plagiarism!",
                "I can speak underwater as well as other words too."
        );
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String help() {
        return Settings.commandPrefix(true) + "sri";
    }

    @Override
    public String desc() {
        return "I sri you!";
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        return random.get(new Random().nextInt(random.size()));


    }
}
