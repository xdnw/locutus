package link.locutus.discord.commands.external.guild;

import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandCategory;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordMessageBuilder;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.awt.*;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class UpdateEmbed extends Command {
    public UpdateEmbed() {
        super(CommandCategory.INTERNAL_AFFAIRS, CommandCategory.GOV);
    }

    @Override
    public String help() {
        return super.help() + " <title> <body> <reactions>";
    }

    @Override
    public String desc() {
        return "Updates a Locutus embed title/body/reactions.";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String onCommand(Guild guild, IMessageIO channel, User author, DBNation me, String fullCommandRaw, List<String> args, Set<Character> flags) throws Exception {
        if (args.isEmpty()) return usage();

        Message message = event.getMessage();
        if (message.getAuthor().getIdLong() != Settings.INSTANCE.APPLICATION_ID)
            return "This command can only be run when bound to a Locutus embed.";

        String requiredRole = DiscordUtil.parseArg(args, "role");
        if (requiredRole != null) {
            String roleRaw = requiredRole.replaceAll("[<@>]", "");
            if (MathMan.isInteger(roleRaw)) {
                Role role = guild.getRoleById(roleRaw);
                if (role == null || !Objects.requireNonNull(guild.getMember(author)).getRoles().contains(role)) {
                    return null;
                }
            }
            Roles role = Roles.valueOf(requiredRole.toUpperCase());
            if (!role.has(author, guild)) {
                return null;
            }
        }

        List<MessageEmbed> embeds = message.getEmbeds();
        if (embeds.size() != 1) return "No embeds found";
        MessageEmbed embed = embeds.get(0);

        EmbedBuilder builder = new EmbedBuilder(embed);

        String setColor = DiscordUtil.parseArg(args, "color");
        if (setColor != null) {
            builder.setColor(Color.getColor(setColor));
        }

        String setTitle = DiscordUtil.parseArg(args, "title");
        if (setTitle != null) {
            builder.setTitle(parse(setTitle.replace(("{title}"), Objects.requireNonNull(embed.getTitle())), embed, message));
        }

        String setDesc = DiscordUtil.parseArg(args, "description");
        if (setDesc != null) {
            builder.setDescription(parse(setDesc.replace(("{description}"), Objects.requireNonNull(embed.getDescription())), embed, message));
        }


        if (!args.isEmpty()) {
            return "Invalid arguments: `" + StringMan.getString(args) + "`";
        }

        DiscordMessageBuilder discMsg = new DiscordMessageBuilder(channel, message);

        discMsg.clearEmbeds();
        discMsg.embed(builder.build());
        discMsg.send();

        return super.onCommand(event, guild, author, me, args, flags);
    }

    public String parse(String arg, MessageEmbed embed, Message message) {
        long timestamp = message.getTimeCreated().toEpochSecond() * 1000L;
        long diff = System.currentTimeMillis() - timestamp;
        arg = arg.replace("{timediff}", TimeUtil.secToTime(TimeUnit.MILLISECONDS, diff));
        return arg;
    }

}
