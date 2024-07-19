package link.locutus.discord.commands.account.question;

import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.CommandRef;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
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
import java.util.Map;
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
    public List<CommandRef> getSlashReference() {
        return List.of(CM.interview.questions.view.cmd);
    }

    @Override
    public String usage() {
        return super.usage() + " <user> <steps>";
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.isEmpty()) return usage(args.size(), 1, channel);
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
                return usage(args.size(), 0, 2, channel);
        }

        T question = questions[index];

        String body = null;

        while (question.isValidateOnInit() || input != null) {
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
        body = question.format(guild, author, me, channel, body);

        String[] options = question.getOptions();
        String cmdBase = Settings.commandPrefix(true) + "interview " + (index) + " " + author.getAsMention();

        List<Map.Entry<String, String>> labelCommandList = new ArrayList<>();
        if (options.length == 0) {
            cmdBase += " Y";
            String emoji = "Next";
            labelCommandList.add(Map.entry(emoji, cmdBase));

            body += "\n\nPress `" + emoji + "` to continue";
        } else {
            for (String option : options) {
                String emojo = option.toLowerCase();
                if (option.length() == 1 && Character.isLetter(option.charAt(0))) {
                    emojo = "\uD83C" + ((char) ('\uDDE6' + (Character.toLowerCase(option.charAt(0)) - 'a')));
                }
                labelCommandList.add(Map.entry(emojo, cmdBase + " \"" + option + "\""));
            }
        }

        IMessageBuilder msg = channel.create().embed(title, body);
        for (Map.Entry<String, String> entry : labelCommandList) {
            msg = msg.commandButton(entry.getValue(), entry.getKey());
        }
        msg.send();

//        return "ping";
        return null;
    }
}
