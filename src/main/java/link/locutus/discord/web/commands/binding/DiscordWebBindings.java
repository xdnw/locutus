package link.locutus.discord.web.commands.binding;

import cn.easyproject.easyocr.ImageType;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.commands.manager.v2.binding.annotation.Binding;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Filter;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.command.CommandBehavior;
import link.locutus.discord.commands.manager.v2.command.CommandCallable;
import link.locutus.discord.commands.manager.v2.command.ParameterData;
import link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.web.WebUtil;
import link.locutus.discord.web.commands.HtmlInput;
import net.dv8tion.jda.api.entities.channel.attribute.IPositionableChannel;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.attribute.ICategorizableChannel;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.User;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class DiscordWebBindings extends WebBindingHelper {

    @HtmlInput
    @Binding(types = CommandCallable.class)
    public String command(@Me User user, @Default ParameterData param) {
        List<CommandCallable> options = new ArrayList<>(Locutus.imp().getCommandManager().getV2().getCommands().getParametricCallables(f -> true));
        return WebUtil.generateSearchableDropdown(param, options, (obj, names, values, subtext) -> {
            names.add(obj.getFullPath());
            subtext.add(obj.simpleDesc().split("\n")[0]);
        });
    }


    public static String formatGuildName(Guild guild) {
        return DiscordUtil.toDiscordChannelString(guild.getName());
    }

    @HtmlInput
    @Binding(types = {User.class})
    public String user(@Me Guild guild, ParameterData param) {
        Set<User> users = guild.getMembers().stream().map(f -> f.getUser()).collect(Collectors.toSet());
        return WebUtil.generateSearchableDropdown(param, users, (obj, names, values, subtext) -> {
            names.add(obj.getName());
            values.add(obj.getAsMention());
            DBNation nation = DiscordUtil.getNation(obj);
            if (nation != null) {
                subtext.add(nation.getNation() + "- " + nation.getAllianceName() + "- " + Rank.byId(nation.getPosition()));
            } else {
                subtext.add("");
            }
        });
    }

    @HtmlInput
    @Binding(types = {Member.class})
    public String member(@Me User user, @Me Guild guild, @Default ParameterData param) {
        List<Member> options = guild.getMembers();
        return WebUtil.generateSearchableDropdown(param, options, (obj, names, values, subtext) -> {
            names.add(obj.getEffectiveName());
            values.add(obj.getAsMention());
            DBNation nation = DiscordUtil.getNation(obj.getUser());
            if (nation != null) {
                subtext.add(nation.getNation() + "- " + nation.getAllianceName() + "- " + Rank.byId(nation.getPosition()));
            } else {
                subtext.add("");
            }
        });
    }

    @HtmlInput
    @Binding(types= Category.class)
    public String category(@Me Guild guild, @Default ParameterData param) {
        return categories(guild, param, false);
    }

    @HtmlInput
    @Binding(types= {Set.class, Category.class}, multiple = true)
    public String categories(@Me Guild guild, @Default ParameterData param) {
        return categories(guild, param, true);
    }

    public String categories(@Me Guild guild, @Default ParameterData param, boolean multiple) {
        List<Category> options = guild.getCategories();
        Filter filter = param.getAnnotation(Filter.class);
        options = new ArrayList<>(options);
        options.removeIf(f -> !f.getName().matches(filter.value()));
        return WebUtil.generateSearchableDropdown(param, options, (obj, names, values, subtext) -> {
            names.add(obj.getName());
            values.add(obj.getIdLong());
        }, multiple);
    }

    @HtmlInput
    @Binding(types=Guild.class)
    public String guild(@Me User user, @Default ParameterData param) {
        List<Guild> options = user.getMutualGuilds();
        return WebUtil.generateSearchableDropdown(param, options, (obj, names, values, subtext) -> {
            names.add(formatGuildName(obj) + "/" + obj.getIdLong());
            values.add(obj.getIdLong());

            String sub = "<img class='guild-icon-inline' src='" + obj.getIconUrl() + "'>";
            Set<Integer> alliances = Locutus.imp().getGuildDB(obj).getAllianceIds();
            if (!alliances.isEmpty()) sub += "AA:" + StringMan.join(alliances, ",AA:");
            subtext.add(sub);
        });
    }

    @HtmlInput
    @Binding(types= GuildDB.class)
    public String guildDB(@Me User user, @Default ParameterData param) {
        return guild(user, param);
    }

    @HtmlInput
    @Binding(types= TextChannel.class)
    public String textChannel(@Me Guild guild, @Me User user, @Default ParameterData param) {
        List<MessageChannel> options = getGuildChannels(guild, user);
        options.removeIf(f -> !(f instanceof TextChannel));
        return channel(guild, user, options, param);
    }

    @HtmlInput
    @Binding(types = CommandBehavior.class)
    public String cmdBehavior(@Default ParameterData param) {
        return multipleSelect(param, Arrays.asList(CommandBehavior.values()), rank -> new AbstractMap.SimpleEntry<>(rank.name(), rank.name()));
    }

    @HtmlInput
    @Binding(types = ImageType.class)
    public String ImageType(@Default ParameterData param) {
        return multipleSelect(param, Arrays.asList(ImageType.values()), rank -> new AbstractMap.SimpleEntry<>(rank.name(), rank.name()));
    }

    @HtmlInput
    @Binding(types= ICategorizableChannel.class)
    public String categorizableChannel(@Me Guild guild, @Me User user, @Default ParameterData param) {
        List<MessageChannel> options = getGuildChannels(guild, user);
        options.removeIf(f -> !(f instanceof ICategorizableChannel));
        return channel(guild, user, options, param);
    }

    @HtmlInput
    @Binding(types=MessageChannel.class)
    public String channel(@Me Guild guild, @Me User user, @Default ParameterData param) {
        return channel(guild, user, getGuildChannels(guild, user), param);
    }

    public List<MessageChannel> getGuildChannels(Guild guild, User user) {
        Member member = guild.getMember(user);
        if (member == null) throw new IllegalArgumentException("You are not a member");
        List<MessageChannel> options = new ArrayList<>();
        for (GuildChannel channel : guild.getChannels()) {
            if (!(channel instanceof MessageChannel)) continue;
            MessageChannel mc = (MessageChannel) channel;
            if (member.hasAccess(channel)) {
                options.add(mc);
            }
        }
        return options;
    }

    public String channel(@Me Guild guild, @Me User user, List<MessageChannel> options, @Default ParameterData param) {
        if (options.isEmpty()) throw new IllegalArgumentException("You cannot view any channels");
        Collections.sort(options, (o1, o2) -> {
            GuildMessageChannel tc1 = (GuildMessageChannel) o1;
            GuildMessageChannel tc2 = (GuildMessageChannel) o2;
            Category cat1 = (tc1 instanceof ICategorizableChannel) ? ((ICategorizableChannel) tc1).getParentCategory() : null;
            Category cat2 = (tc2 instanceof ICategorizableChannel) ? ((ICategorizableChannel) tc2).getParentCategory() : null;

            if (cat1 != cat2) {
                if (cat1 == null) return 1;
                if (cat2 == null) return -1;
                return Integer.compare(cat1.getPositionRaw(), cat2.getPositionRaw());
            }
            int pos1 = (tc1 instanceof IPositionableChannel) ? ((IPositionableChannel) tc1).getPositionRaw() : -1;
            int pos2 = (tc2 instanceof IPositionableChannel) ? ((IPositionableChannel) tc2).getPositionRaw() : -1;
            if (pos1 != pos2) {
                return Integer.compare(pos1, pos2);
            }
            return Long.compare(tc1.getIdLong(), tc2.getIdLong());
        });
        return WebUtil.generateSearchableDropdown(param, options, (obj, names, values, subtext) -> {
            names.add("#" + obj.getName());
            GuildMessageChannel tc = (GuildMessageChannel) obj;
            values.add(tc.getAsMention());
            Category cat = (tc instanceof ICategorizableChannel) ? ((ICategorizableChannel) tc).getParentCategory() : null;
            if (cat != null) {
                subtext.add(cat.getName());
            } else {
                subtext.add("");
            }
        });
    }

    @HtmlInput
    @Binding(types= Message.class)
    public String message(ParameterData param) {
        String pattern = "https\\:\\/\\/discord\\.com\\/channels\\/[0-9]+\\/[0-9]+\\/[0-9]+";
        return WebUtil.createInput(WebUtil.InputType.text, param, "pattern='" + pattern + "'");
    }
}
