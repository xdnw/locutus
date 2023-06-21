package link.locutus.discord.commands.account.question;

import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.NationMeta;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class QuestionCommand<T extends Question> extends Command {
    private final T[] questions;
    private final NationMeta meta;

    public QuestionCommand(NationMeta meta, T[] questions) {
        super(CommandCategory.INTERNAL_AFFAIRS, CommandCategory.USER_COMMANDS);
        this.questions = questions;
        this.meta = meta;
    }

    @Override
    public String usage() {
        return super.usage() + " <user> <steps>";
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (args.isEmpty()) return usage(event);
        DBNation sudoer = null;

        Iterator<String> iter = args.iterator();
        while (iter.hasNext()) {
            String next = iter.next();
            User user = DiscordUtil.getUser(next);
            if (user != null) {
                iter.remove();
                author = user;
                sudoer = me;
                me = DiscordUtil.getNation(user);
            }
        }

        String input = null;
        int index = 0;
        switch (args.size()) {
            case 2:
                input = args.get(1);
            case 1:
                index = Integer.parseInt(args.get(0));
            case 0:
                break;
            default:
                return usage(event);
        }

        T question = questions[index];

        String body = null;

        while (question.isValidateOnInit() || input != null) {
            GuildMessageChannel channel = event.getGuildChannel();
            try {
                if (question.validate(guild, author, me, sudoer, channel, input)) {
                    question = questions[++index];
                } else {
                    break;
                }
            } catch (IllegalArgumentException ignore) {
                body = ignore.getMessage();
                break;
            }
            input = null;
        }

        if (me != null) {
            me.setMeta(meta, question.ordinal());
        }

        String title = "Query " + index;
        if (body == null) body = question.getContent();
        body = question.format(guild, author, me, event.getGuildChannel(), body);

        List<String> reactions = new ArrayList<>();
        String[] options = question.getOptions();
        String cmdBase = Settings.commandPrefix(true) + "interview " + (index) + " " + author.getAsMention();

        if (options.length == 0) {
            cmdBase += " Y";
            String emoji = "Next";
            reactions.add(emoji);
            reactions.add(cmdBase);

            body += "\n\nPress `" + emoji + "` to continue";
        } else {
            for (String option : options) {
                String emojo = option.toLowerCase();
                if (option.length() == 1 && Character.isLetter(option.charAt(0))) {
                    emojo = "\uD83C" + ((char) ('\uDDE6' + (Character.toLowerCase(option.charAt(0)) - 'a')));
                }
                reactions.add(emojo);
                reactions.add(cmdBase + " \"" + option + "\"");
            }
        }

        DiscordUtil.createEmbedCommand(event.getChannel(), title, body, reactions.toArray(new String[0]));

//        return "ping";
        return null;
    }
}
