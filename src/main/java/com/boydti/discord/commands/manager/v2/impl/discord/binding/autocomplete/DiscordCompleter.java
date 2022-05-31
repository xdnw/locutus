package com.boydti.discord.commands.manager.v2.impl.discord.binding.autocomplete;

import com.boydti.discord.commands.manager.v2.binding.BindingHelper;
import com.boydti.discord.commands.manager.v2.binding.FunctionConsumerParser;
import com.boydti.discord.commands.manager.v2.binding.Key;
import com.boydti.discord.commands.manager.v2.binding.ValueStore;
import com.boydti.discord.commands.manager.v2.binding.annotation.Binding;
import com.boydti.discord.commands.manager.v2.binding.annotation.Me;
import com.boydti.discord.commands.manager.v2.impl.discord.binding.DiscordBindings;
import com.boydti.discord.user.Roles;
import com.boydti.discord.util.StringMan;
import com.boydti.discord.commands.manager.v2.binding.annotation.Autocomplete;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Category;
import net.dv8tion.jda.api.entities.Channel;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.IMentionable;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.lang.reflect.Type;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DiscordCompleter extends BindingHelper {
    @Autocomplete
    @Binding(types={Category.class})
    public List<String> category(@Me Member member, @Me Guild guild, String input) {
        List<Category> categories = new ArrayList<>(guild.getCategories());
        categories.removeIf(f -> !f.getMembers().contains(member));
        if (!input.isEmpty()) {
            categories = StringMan.getClosest(input, categories, Channel::getName, OptionData.MAX_CHOICES, true);
        }

        return categories.stream().map(f -> f.getAsMention()).collect(Collectors.toList());
    }

    @Autocomplete
    @Binding(types={Guild.class})
    public List<Map.Entry<String, String>> Guild(@Me User user, String input) {
        List<Guild> options = user.getMutualGuilds();
        options = StringMan.getClosest(input, options, Guild::getName, OptionData.MAX_CHOICES, true, true);
        return options.stream().map(f -> new AbstractMap.SimpleEntry<>(f.getName(), f.getIdLong() + "")).collect(Collectors.toList());
    }

    @Autocomplete
    @Binding(types={Roles.class})
    public List<String> Roles(String input) {
        return StringMan.completeEnum(input, Roles.class);
    }

    @Autocomplete
    @Binding(types={Permission.class})
    public List<String> Permission(String input) {
        return StringMan.completeEnum(input, Permission.class);
    }


    @Autocomplete
    @Binding(types={OnlineStatus.class})
    public List<String> onlineStatus(String input) {
        return StringMan.completeEnum(input, OnlineStatus.class);
    }

    @Autocomplete
    @Binding(types={Role.class})
    public List<Map.Entry<String, String>> role(@Me Guild guild, String input) {
        List<Role> options = guild.getRoles();
        List<Role> closest = StringMan.getClosest(input, options, true);
        return StringMan.autocompletePairs(closest, f -> f.getName(), f -> f.getAsMention());
    }

    public final Set<Role> ROLES_KEY = null;
    public final Set<Member> MEMBERS_KEY = null;


    {
        try {
            {
                Type type = getClass().getDeclaredField("MEMBERS_KEY").getGenericType();
                Key key = Key.of(type, Autocomplete.class);
                addBinding(store -> {
                    store.addParser(key, new FunctionConsumerParser(key, (BiFunction<ValueStore, Object, Object>) (valueStore, input) -> {
                        Guild guild = (Guild) valueStore.getProvided(Key.of(Guild.class, Me.class));

                        List<Member> options = guild.getMembers();
                        return StringMan.autocompleteComma(input.toString(), options, new Function<String, Member>() {
                            @Override
                            public Member apply(String s) {
                                return DiscordBindings.member(guild, s);
                            }
                        }, Member::getEffectiveName, IMentionable::getAsMention, OptionData.MAX_CHOICES);
                    }));
                });
            }
            {
                Type type = getClass().getDeclaredField("ROLES_KEY").getGenericType();
                Key key = Key.of(type, Autocomplete.class);
                addBinding(store -> {
                    store.addParser(key, new FunctionConsumerParser(key, (BiFunction<ValueStore, Object, Object>) (valueStore, input) -> {
                        Guild guild = (Guild) valueStore.getProvided(Key.of(Guild.class, Me.class));

                        List<Role> options = guild.getRoles();
                        return StringMan.autocompleteComma(input.toString(), options, new Function<String, Role>() {
                            @Override
                            public Role apply(String s) {
                                return DiscordBindings.role(guild, s);
                            }
                        }, Role::getName, IMentionable::getAsMention, OptionData.MAX_CHOICES);
                    }));
                });
            }
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }


    //// TODO start here
//
//    @Binding
//    public Set<Member> members(@Me Guild guild, String input) {
//        Set<Member> members = new LinkedHashSet<>();
//        for (String arg : input.split("[|]+")) {
//            if (arg.equalsIgnoreCase("*")) {
//                members.addAll(guild.getMembers());
//            } else if (arg.equalsIgnoreCase("*,#verified=0") || arg.equalsIgnoreCase("#verified=0,*")) {
//                for (Member member : guild.getMembers()) {
//                    if (DiscordUtil.getNation(member.getUser()) == null) {
//                        members.add(member);
//                    }
//                }
//            } else {
//                Set<DBNation> nations = DiscordUtil.parseNations(guild, arg);
//                for (Member member : guild.getMembers()) {
//                    DBNation nation = DiscordUtil.getNation(member.getUser());
//                    if (nation != null && nations.contains(nation)) {
//                        members.add(member);
//                    }
//                }
//            }
//        }
//        return members;
//    }
//
//    @Binding
//    public GuildShardManager shardManager() {
//        return Locutus.imp().getDiscordApi();
//    }
//
//    @Binding
//    public Guild guild(long guildId) {
//        Guild guild = Locutus.imp().getDiscordApi().getGuildById(guildId);
//        if (guild == null) throw new IllegalArgumentException("No guild found for: " + guildId);
//        return guild;
//    }
//
//    @Binding
//    @Me
//    public Guild guild() {
//        throw new IllegalStateException("No guild set in command locals");
//    }
//
//    @Binding
//    public MessageChannel channel(Guild guild, String channel) {
//        MessageChannel GuildMessageChannel = DiscordUtil.getChannel(guild, channel);
//        if (GuildMessageChannel == null) throw new IllegalArgumentException("No channel found for " + channel);
//        return GuildMessageChannel;
//    }
//
//    @Binding
//    public Message message(String message) {
//        return DiscordUtil.getMessage(message);
//    }
//
//    @Binding
//    public MessageReceivedEvent event() {
//        throw new IllegalStateException("No event set in command locals");
//    }
//
//    @Binding
//    @Me
//    public MessageChannel channel() {
//        throw new IllegalStateException("No channel set in command locals");
//    }
//
////    @Binding
////    @Me
////    public Member member(@Me Guild guild, User user) {
////        Member member = guild.getMember(user);
////        if (member == null) throw new IllegalStateException("No member found for " + user.getName());
////        return member;
////    }
//
//    @Binding
//    @Me
//    public Member member(@Me Guild guild, @Me User user) {
//        Member member = guild.getMember(user);
//        if (member == null) throw new IllegalStateException("No member found for " + user.getName());
//        return member;
//    }
//
//    @Binding
//    @Me
//    public User user() {
//        throw new IllegalStateException("No user set in command locals");
//    }
//
//    @Binding
//    @Me
//    public Message message() {
//        throw new IllegalStateException("No message set in command locals");
//    }
}
