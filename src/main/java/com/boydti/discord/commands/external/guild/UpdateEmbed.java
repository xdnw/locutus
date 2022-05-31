package com.boydti.discord.commands.external.guild;

import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.commands.manager.CommandCategory;
import com.boydti.discord.config.Settings;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.user.Roles;
import com.boydti.discord.util.MathMan;
import com.boydti.discord.util.StringMan;
import com.boydti.discord.util.TimeUtil;
import com.boydti.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.awt.Color;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

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
        return "Updates a Locutus embed title/body/reactions";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (args.isEmpty()) return usage();

        Message message = event.getMessage();
        if (message.getAuthor().getIdLong() != Settings.INSTANCE.APPLICATION_ID) return "This command can only be run when bound to a Locutus embed";

        String requiredRole = DiscordUtil.parseArg(args, "role");
        if (requiredRole != null) {
            String roleRaw = requiredRole.replaceAll("[<@>]", "");
            if (MathMan.isInteger(roleRaw)) {
                Role role = guild.getRoleById(roleRaw);
                if (role == null || !event.getMember().getRoles().contains(role)) {
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
            builder.setTitle(parse(setTitle.replace(("{title}"), embed.getTitle()), embed, message));
        }

        String setDesc = DiscordUtil.parseArg(args, "description");
        if (setDesc != null) {
            builder.setDescription(parse(setDesc.replace(("{description}"), embed.getDescription()), embed, message));
        }

//        Map<String, String> reactions = DiscordUtil.getReactions(embed);
//        if (reactions == null) reactions = new LinkedHashMap<>();

        if (!args.isEmpty()) {
            return "Invalid arguments: `" + StringMan.getString(args) + "`";
        }

        DiscordUtil.updateEmbed(builder, null, new Function<EmbedBuilder, Message>() {
            @Override
            public Message apply(EmbedBuilder builder) {
                 return com.boydti.discord.util.RateLimitUtil.complete(message.getChannel().editMessageEmbedsById(message.getIdLong(), builder.build()));
            }
        });

        return super.onCommand(event, guild, author, me, args, flags);
    }

    public String parse(String arg, MessageEmbed embed, Message message) {
        long timestamp = message.getTimeCreated().toEpochSecond() * 1000L;
        long diff = System.currentTimeMillis() - timestamp;
        arg = arg.replace("{timediff}", TimeUtil.secToTime(TimeUnit.MILLISECONDS, diff));
        return arg;
    }

}
