package link.locutus.discord.commands.manager.v2.impl;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.LocalValueStore;
import link.locutus.discord.commands.manager.v2.binding.Parser;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.annotation.Range;
import link.locutus.discord.commands.manager.v2.binding.annotation.Step;
import link.locutus.discord.commands.manager.v2.binding.bindings.autocomplete.PrimitiveCompleter;
import link.locutus.discord.commands.manager.v2.command.ArgumentStack;
import link.locutus.discord.commands.manager.v2.command.CommandCallable;
import link.locutus.discord.commands.manager.v2.command.ICommandGroup;
import link.locutus.discord.commands.manager.v2.command.ParameterData;
import link.locutus.discord.commands.manager.v2.command.ParametricCallable;
import link.locutus.discord.commands.manager.v2.impl.discord.binding.autocomplete.DiscordCompleter;
import link.locutus.discord.commands.manager.v2.impl.discord.HookMessageChannel;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.autocomplete.PWCompleter;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.commands.manager.v2.binding.annotation.Autocomplete;
import link.locutus.discord.commands.manager.v2.binding.annotation.Autoparse;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Category;
import net.dv8tion.jda.api.entities.Channel;
import net.dv8tion.jda.api.entities.ChannelType;
import net.dv8tion.jda.api.entities.Emoji;
import net.dv8tion.jda.api.entities.Emote;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildChannel;
import net.dv8tion.jda.api.entities.IMentionable;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.NewsChannel;
import net.dv8tion.jda.api.entities.PrivateChannel;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.StageChannel;
import net.dv8tion.jda.api.entities.StoreChannel;
import net.dv8tion.jda.api.entities.GuildMessageChannel;
import net.dv8tion.jda.api.entities.ThreadChannel;
import net.dv8tion.jda.api.entities.ThreadMember;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.AutoCompleteQuery;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData;
import net.dv8tion.jda.api.interactions.commands.privileges.CommandPrivilege;
import net.dv8tion.jda.api.utils.WidgetUtil;
import net.dv8tion.jda.api.utils.data.SerializableData;

import javax.annotation.Nonnull;
import javax.security.auth.login.LoginException;
import java.awt.Color;
import java.lang.reflect.Type;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class SlashCommandManager extends ListenerAdapter {
    private final Locutus root;
    private final CommandManager2 commands;

    public SlashCommandManager(Locutus locutus) {
        this.root = locutus;
        this.commands = root.getCommandManager().getV2();
    }

    public static void main(String[] args) throws LoginException, InterruptedException, SQLException, ClassNotFoundException {
        Settings.INSTANCE.reload(Settings.INSTANCE.getDefaultFile());

        Settings.INSTANCE.ENABLED_COMPONENTS.disableTasks();
        Settings.INSTANCE.ENABLED_COMPONENTS.disableListeners();

        Settings.INSTANCE.ENABLED_COMPONENTS.SLASH_COMMANDS = true;

        Locutus locutus = Locutus.create().start();
        // TODO test the command?
//        System.exit(1);
    }

    private int getSize(SlashCommandData cmd) {
        return cmd.getName().length() + cmd.getDescription().length() + getOptionSize(cmd.getOptions()) + getGroupSize(cmd.getSubcommandGroups()) + getSubSize(cmd.getSubcommands());
    }

    private int getOptionSize(List<OptionData> options) {
        int total = 0;
        for (OptionData option : options) total += getSize(option);
        return total;
    }

    private int getSize(SubcommandGroupData group) {
        return group.getName().length() + group.getDescription().length() + getSubSize(group.getSubcommands());
    }

    private int getGroupSize(List<SubcommandGroupData> groups) {
        int total = 0;
        for (SubcommandGroupData group : groups) total += getSize(group);
        return total;
    }

    private int getSubSize(List<SubcommandData> subcommands) {
        int total = 0;
        for (SubcommandData subcommand : subcommands) total += getSize(subcommand);
        return total;
    }

    public int getSize(SubcommandData subcommand) {
        return subcommand.getName().length() + subcommand.getDescription().length() + getOptionSize(subcommand.getOptions());
    }

    public int getSize(OptionData option) {
        int total = option.getName().length() + option.getDescription().length();
        for (Choice choice : option.getChoices()) {
            total += choice.getName().length();
            total += choice.getAsString().length();
        }
        return total;
    }

    public void printSize(SlashCommandData cmd, StringBuilder response, int indent) {
        String indentStr = StringMan.repeat(" ", indent);
        response.append(indentStr + cmd.getName() + "(desc:" + cmd.getDescription().length() + " option:" + getOptionSize(cmd.getOptions()) + " sub:" + getSubSize(cmd.getSubcommands()) + " group:" + getGroupSize(cmd.getSubcommandGroups()) + ")\n");
        for (OptionData option : cmd.getOptions()) {
            printSize(option, response, indent + 2);
        }
        for (SubcommandGroupData group : cmd.getSubcommandGroups()) {
            printSize(group, response, indent + 2);
        }
        for (SubcommandData sub : cmd.getSubcommands()) {
            printSize(sub, response, indent + 2);
        }
    }

    public void printSize(SubcommandGroupData group, StringBuilder response, int indent) {
        String indentStr = StringMan.repeat(" ", indent);
        response.append(indentStr + group.getName() + "(desc:" + group.getDescription().length() + " sub: " + getSubSize(group.getSubcommands()) + ")\n");
        for (SubcommandData sub : group.getSubcommands()) {
            printSize(sub, response, indent + 2);
        }
    }

    public void printSize(SubcommandData sub, StringBuilder response, int indent) {
        String indentStr = StringMan.repeat(" ", indent);
        response.append(indentStr + "sub:" + sub.getName() + "(desc:" + sub.getDescription().length() + " option: " + getOptionSize(sub.getOptions()) + ")\n");
        for (OptionData option : sub.getOptions()) {
            printSize(option, response, indent + 2);
        }
    }

    public void printSize(OptionData option, StringBuilder response, int indent) {
        String indentStr = StringMan.repeat(" ", indent);
        response.append(indentStr + "opt:" + option.getName() + "(desc:" + option.getDescription().length() + ")\n");
    }

    public void setupCommands() {
        new PrimitiveCompleter().register(commands.getStore());
        new DiscordCompleter().register(commands.getStore());
        new PWCompleter().register(commands.getStore());

//        commands.registerDefaults();
//        commands.getCommands().registerCommands(this);

        List<SlashCommandData> toRegister = new ArrayList<>();
        for (Map.Entry<String, CommandCallable> entry : commands.getCommands().getSubcommands().entrySet()) {
            CommandCallable callable = entry.getValue();
            String id = entry.getKey();
            AtomicInteger size = new AtomicInteger();
            SlashCommandData cmd = adaptCommands(callable, id, null, null, 100, 100, false, true, true, true, true, true);
            if (getSize(cmd) > 4000) {
                cmd = adaptCommands(callable, id, null, null, 100, 100, false, true, true, false, true, true);
            }
            if (getSize(cmd) > 4000) {
                cmd = adaptCommands(callable, id, null, null, 100, 100, false, true, true, false, false, true);
            }
            if (getSize(cmd) > 4000) {
                cmd = adaptCommands(callable, id, null, null, 100, 100, false, true, false, false, false, true);
            }
            if (getSize(cmd) > 4000) {
                cmd = adaptCommands(callable, id, null, null, 100, 100, true, true, false, false, false, true);
            }
            if (getSize(cmd) > 4000) {
                cmd = adaptCommands(callable, id, null, null, 100, 100, true, true, false, false, false, false);
            }
            toRegister.add(cmd);
        }

        StringBuilder builder = new StringBuilder();
        for (SlashCommandData cmd : toRegister) {
            int size = getSize(cmd);
            if (size > 4000) {
                printSize(cmd, builder, 0);
                throw new IllegalArgumentException("Command " + cmd.getName() + "too large: " + size + "");
            }
        }
        System.out.println(builder);

//        for (SlashCommandData cmd : toRegister) {
//            String cmdData = cmd.toData().toString();
//            if (cmdData.length() > 4000) {
//                System.out.println("Command too long: " + cmd.getName() + " | " + cmdData.length());
//                System.out.println(cmdData);
//                System.exit(0);
//            }
//        }

        Guild guild = root.getDiscordApi().getGuildById(Settings.INSTANCE.ROOT_SERVER); // testing
        System.out.println("remove:||Guild " + guild + " | " + toRegister.size());
        if (!toRegister.isEmpty()) {
            Map<String, ? extends Collection<CommandPrivilege>> map = new HashMap<>();
//            CommandPrivilege.
//            guild.updateCommandPrivileges(map);
            Locutus.imp().getDiscordApi(guild.getIdLong()).updateCommands().addCommands(toRegister).queue();
//            guild.updateCommands().addCommands(toRegister).queue();
        }
    }

    public SlashCommandData adaptCommands(CommandCallable callable, String id, SlashCommandData root, SubcommandGroupData discGroup, int maxDescription, int maxOption, boolean breakNewlines, boolean includeTypes, boolean includeExample, boolean includeRepeatedTypes, boolean includeDescForChoices, boolean includeOptionDesc) {
//        String id = callable.getPrimaryAlias().toLowerCase(Locale.ROOT);
//        String desc = callable.desc(commands.getStore());
//        if (desc == null) desc = "";
        String desc = callable.simpleDesc();
//        String help = callable.help(commands.getStore());
//        if (desc.length() >= maxDescription && simpleDesc != null) {
//            System.out.println("Long desc " + desc);
//            desc = simpleDesc;
//        }
        if (desc.length() >= maxDescription) {
            desc = desc.split("\n")[0];
        }
        if (desc.length() >= maxDescription) {
            System.out.println("Long desc2 " + desc);
            desc = desc.substring(0, maxDescription);
        }
        if (breakNewlines && desc.contains("\n")) desc = desc.split("\n")[0];
        if (desc.isEmpty()) desc = "_";

        SerializableData current = null;
        if (root == null) {
            root = Commands.slash(id.toLowerCase(Locale.ROOT), desc);
            current = root;
        }
        if (callable instanceof ICommandGroup) {
            ICommandGroup group = (ICommandGroup) callable;

            if (current == null) {
                if (discGroup == null) {
                    discGroup = new SubcommandGroupData(id, desc);
                    current = discGroup;
                    root.addSubcommandGroups(discGroup);
                } else {
                    String path = root.getName() + " " + discGroup.getName() + " " + id;
                    throw new IllegalArgumentException("Cannot nest discord commands past a depth of 2: " + path);
                }
            }

            for (Map.Entry<String, CommandCallable> entry : group.getSubcommands().entrySet()) {
                String subId = entry.getKey();
                CommandCallable subCmd = entry.getValue();
                adaptCommands(subCmd, subId, root, discGroup, maxDescription, maxOption, breakNewlines, includeTypes, includeExample, includeRepeatedTypes, includeDescForChoices, includeOptionDesc);
            }
        } else if (callable instanceof ParametricCallable) {
            ParametricCallable parametric = (ParametricCallable) callable;
            List<OptionData> options = createOptions(parametric, maxOption, breakNewlines, includeTypes, includeExample, includeRepeatedTypes, includeDescForChoices, includeOptionDesc);
            if (current == null) {
                SubcommandData discSub = new SubcommandData(id, desc);
                current = discSub;

                if (discGroup != null) {
                    discGroup.addSubcommands(discSub);
                } else {
                    root.addSubcommands(discSub);
                }
            }
            try {
                if (current instanceof SlashCommandData) {
                    ((SlashCommandData) current).addOptions(options);
                } else if (current instanceof SubcommandData) {
                    ((SubcommandData) current).addOptions(options);
                }
            } catch (Throwable e) {
                System.out.println("Error creating command: " + id);
                e.printStackTrace();
                throw e;
            }
        }
        return root;
    }

    private Set<Key> bindingKeys = new HashSet<>();

    public List<OptionData> createOptions(ParametricCallable cmd, int maxOption, boolean breakNewlines, boolean includeTypes, boolean includeExample, boolean includeRepeatedTypes, boolean includeDescForChoices, boolean includeDesc) {
        List<OptionData> result = new ArrayList<>();
        Set<Type> paramTypes = new HashSet<>();
        for (ParameterData param : cmd.getUserParameters()) {
            Type type = param.getType();

            String id = param.getName().toLowerCase(Locale.ROOT);
            String desc = param.getExpandedDescription(false, includeExample, includeDesc);
            String simpleDesc = param.getDescription();
            if (desc.length() > maxOption && simpleDesc != null) {
                desc = simpleDesc;
            }
            if (breakNewlines && desc.contains("\n")) desc = desc.split("\n")[0];
            if (desc.length() > maxOption) {
                System.out.println("Long option desc2 " + desc);
                desc = desc.substring(0, maxOption);
            }
            if (breakNewlines && desc.contains("\n")) desc = desc.split("\n")[0];
            if (!includeRepeatedTypes) {
                if (paramTypes.add(param.getType())) {
                    desc = "";
                }
            }
            if (desc.trim().isEmpty() && includeTypes) {
                String[] split = param.getType().getTypeName().split("\\.");
                desc = split[split.length - 1];
            }
            if (desc.isEmpty()) {
                System.out.println("remove:||Desc is empty " + cmd.getMethod().getName() + " | " + param.getName());
                desc = "_";
            }


            Range range = param.getAnnotation(Range.class);
            Step step = param.getAnnotation(Step.class);

            OptionType optionType = createType(type);
            OptionData option = new OptionData(optionType, id, desc);

            option.setAutoComplete(false);
            if (optionType == OptionType.CHANNEL) {
                Collection<ChannelType> types = getChannelType(type);
                if (types != null) {
                    option.setChannelTypes(types);
                }
            } else if (optionType == OptionType.STRING) {
                // enum
                // add choice if <25 options, else autocomplete
                boolean isEnumChoice = false;
                if (type instanceof Class) {
                    Class clazz = (Class) type;
                    if (clazz.isEnum()) {
                        Object[] values = clazz.getEnumConstants();
                        if (values.length <= OptionData.MAX_CHOICES) {
                            isEnumChoice = true;
                            for (Object value : values) {
                                String name = value.toString();
                                option.addChoice(name, name);
                            }
                        }
                    }
                }

                if (!isEnumChoice) {
                    Parser binding = param.getBinding();

                    Key<Object> emptyKey = Key.of(String.class);
                    if (!binding.getKey().equals(emptyKey)) {
                        Key parserKey = binding.getKey().append(Autoparse.class);
                        Parser parser = commands.getStore().get(parserKey);
                        if (parser != null && !parser.getKey().equals(emptyKey)) {
                            option.setAutoComplete(true);
                        } else {
                            Key completerKey = binding.getKey().append(Autocomplete.class);
                            parser = commands.getStore().get(completerKey);
                            if (parser != null && !parser.getKey().equals(emptyKey)) {
                                System.out.println("Enable auto complete for " + completerKey);
                                option.setAutoComplete(true);
                            } else {
                                if (bindingKeys.add(completerKey)) {
                                    System.out.println("No binding: " + binding.getKey());
                                }
                            }
                        }
                    }
                }
            }
            if (range != null && (optionType == OptionType.INTEGER || (optionType == OptionType.NUMBER && step != null))) {
                double stepVal = 1;
                if (step != null) stepVal = step.value();
                long options = (long) Math.ceil((range.max() - range.min()) / stepVal);
                if (options > 0 && options < OptionData.MAX_CHOICES) {
                    for (double i = range.min(); i <= range.max(); i += stepVal) {
                        if (optionType == OptionType.INTEGER) {
                            option.addChoice(((int) i) + "", (int) i);
                        } else {
                            option.addChoice(MathMan.format(i), i);
                        }
                    }
                }
            }
            if (option.getChoices().isEmpty() && range != null) { // Discord throws an error if you have choices and a range
                if (optionType == OptionType.INTEGER) {
                    if (range.max() != Double.POSITIVE_INFINITY) {
                        option.setMaxValue((long) range.max());
                    }
                    if (range.min() != Double.NEGATIVE_INFINITY) {
                        option.setMinValue((long) range.min());
                    }
                } else if (optionType == OptionType.NUMBER) {
                    if (range.max() != Double.POSITIVE_INFINITY) {
                        option.setMaxValue(range.max());
                    }
                    if (range.min() != Double.NEGATIVE_INFINITY) {
                        option.setMinValue(range.min());
                    }
                }
            }
            if (!includeDescForChoices && !option.getChoices().isEmpty()) {
                option.setDescription("_");
            }

            if (param.isOptional() || param.isFlag()) {
                option.setRequired(false);
            } else {
                option.setRequired(true);
            }

            result.add(option);
        }
        return result;
    }

    private static final Map<Type, Collection<ChannelType>> CHANNEL_TYPES = new ConcurrentHashMap<>();
    static {
        CHANNEL_TYPES.put(GuildMessageChannel.class, Collections.singleton(ChannelType.TEXT));
        CHANNEL_TYPES.put(PrivateChannel.class, Collections.singleton(ChannelType.PRIVATE));
        CHANNEL_TYPES.put(VoiceChannel.class, Collections.singleton(ChannelType.VOICE));
//        channelTypes.put(TODO unused, ChannelType.GROUP);
        CHANNEL_TYPES.put(Category.class, Collections.singleton(ChannelType.CATEGORY));
        CHANNEL_TYPES.put(NewsChannel.class, Collections.singleton(ChannelType.NEWS));
        CHANNEL_TYPES.put(StoreChannel.class, Collections.singleton(ChannelType.STORE));
        CHANNEL_TYPES.put(StageChannel.class, Collections.singleton(ChannelType.STAGE));
        CHANNEL_TYPES.put(ThreadChannel.class, List.of(ChannelType.GUILD_PUBLIC_THREAD, ChannelType.GUILD_PRIVATE_THREAD));
    }

    public static Collection<ChannelType> getChannelType(Type type) {
        if (type instanceof Class) {
            Class tClass = (Class) type;
            while (tClass != null) {
                Collection<ChannelType> found = CHANNEL_TYPES.get(tClass);
                if (found != null) return found;
                tClass = tClass.getSuperclass();
            }
        }
        return null;
    }

    private static final Map<Type, OptionType> OPTION_TYPES = new ConcurrentHashMap<>();
    static {
        OPTION_TYPES.put(int.class, OptionType.INTEGER);
        OPTION_TYPES.put(short.class, OptionType.INTEGER);
        OPTION_TYPES.put(byte.class, OptionType.INTEGER);
        OPTION_TYPES.put(long.class, OptionType.INTEGER);
        OPTION_TYPES.put(Integer.class, OptionType.INTEGER);
        OPTION_TYPES.put(Short.class, OptionType.INTEGER);
        OPTION_TYPES.put(Byte.class, OptionType.INTEGER);
        OPTION_TYPES.put(Long.class, OptionType.INTEGER);

        OPTION_TYPES.put(double.class, OptionType.NUMBER);
        OPTION_TYPES.put(float.class, OptionType.NUMBER);
        OPTION_TYPES.put(Double.class, OptionType.NUMBER);
        OPTION_TYPES.put(Float.class, OptionType.NUMBER);

        OPTION_TYPES.put(boolean.class, OptionType.BOOLEAN);
        OPTION_TYPES.put(Boolean.class, OptionType.BOOLEAN);

        OPTION_TYPES.put(Channel.class, OptionType.CHANNEL);
        // Redundant, since it checks superclass, but added for clarity anyway
        OPTION_TYPES.put(Category.class, OptionType.CHANNEL);
        OPTION_TYPES.put(MessageChannel.class, OptionType.CHANNEL);
        OPTION_TYPES.put(GuildMessageChannel.class, OptionType.CHANNEL);
        OPTION_TYPES.put(GuildChannel.class, OptionType.CHANNEL);
        OPTION_TYPES.put(TextChannel.class, OptionType.CHANNEL);
        OPTION_TYPES.put(ThreadChannel.class, OptionType.CHANNEL);
        OPTION_TYPES.put(VoiceChannel.class, OptionType.CHANNEL);
        OPTION_TYPES.put(NewsChannel.class, OptionType.CHANNEL);
        OPTION_TYPES.put(StoreChannel.class, OptionType.CHANNEL);
        OPTION_TYPES.put(StageChannel.class, OptionType.CHANNEL);
        OPTION_TYPES.put(PrivateChannel.class, OptionType.CHANNEL);

        OPTION_TYPES.put(User.class, OptionType.USER);
        OPTION_TYPES.put(Member.class, OptionType.USER);

        OPTION_TYPES.put(Role.class, OptionType.ROLE);

        OPTION_TYPES.put(IMentionable.class, OptionType.MENTIONABLE);
        // Redundant but added for clarity:
        OPTION_TYPES.put(Emoji.class, OptionType.MENTIONABLE);
        OPTION_TYPES.put(Emote.class, OptionType.MENTIONABLE);
        OPTION_TYPES.put(ThreadMember.class, OptionType.MENTIONABLE);
        OPTION_TYPES.put(WidgetUtil.Widget.Member.class, OptionType.MENTIONABLE);

        // Fallback, default
        OPTION_TYPES.put(Object.class, OptionType.STRING);
    }

    public OptionType createType(Type type) {
        if (type instanceof Class) {
            Class tClass = (Class) type;
            while (tClass != null) {
                OptionType found = OPTION_TYPES.get(tClass);
                if (found != null) return found;
                tClass = tClass.getSuperclass();
            }
        }
        return OptionType.STRING;
    }

    @Override
    public void onCommandAutoCompleteInteraction(@Nonnull CommandAutoCompleteInteractionEvent event) {
        long start = System.currentTimeMillis();
        String path = event.getCommandPath();
        AutoCompleteQuery option = event.getFocusedOption();
        String optionName = option.getName();

        List<String> pathArgs = StringMan.split(path, '/');
        Map.Entry<CommandCallable, String> cmdAndPath = commands.getCallableAndPath(pathArgs);
        CommandCallable cmd = cmdAndPath.getKey();

        if (cmd == null) {
            // No command found
            System.out.println("remove:||No command found: " + path);
            return;
        }

        if (!(cmd instanceof ParametricCallable)) {
            System.out.println("remove:||Not parametric: " + path);
            return;
        }

        ParametricCallable parametric = (ParametricCallable) cmd;
        Map<String, ParameterData> map = parametric.getUserParameterMap();
        ParameterData param = map.get(optionName);
        if (param == null) {
            System.out.println("remove:||No parameter found for " + optionName + " | " + map.keySet());
            return;
        }

        boolean autoParse = true;
        Parser binding = param.getBinding();
        Key key = binding.getKey();
        Key parserKey = key.append(Autoparse.class);
        Parser parser = commands.getStore().get(parserKey);

        if (parser == null) {
            autoParse = false;
            Key completerKey = key.append(Autocomplete.class);
            parser = commands.getStore().get(completerKey);
        }

        if (parser == null) {
            System.out.println("remove:||No completer found for " + key);
            return;
        }

        LocalValueStore<Object> locals = new LocalValueStore<>(commands.getStore());
        locals.addProvider(Key.of(User.class, Me.class), event.getUser());
        locals.addProvider(Key.of(Guild.class, Me.class), event.getGuild());
        locals.addProvider(Key.of(MessageChannel.class, Me.class), event.getMessageChannel());

        // Option with current value
        List<String> args = new ArrayList<>(Arrays.asList(option.getValue()));
        ArgumentStack stack = new ArgumentStack(args, locals, commands.getValidators(), commands.getPermisser());
        locals.addProvider(stack);

        List<Choice> choices = new ArrayList<>();
        if (autoParse) {
            binding.apply(stack);
        } else {
            Object result = parser.apply(stack);
            if (!(result instanceof List) || ((List) result).isEmpty()) {
                long diff = System.currentTimeMillis() - start;
                System.out.println("remove:||No results for " + option.getValue() + " | " + diff);
                return;
            }
            List<Object> resultList = (List<Object>) result;
            if (resultList.size() > OptionData.MAX_CHOICES) {
                resultList = resultList.subList(0, OptionData.MAX_CHOICES);
            }
            for (Object o : resultList) {
                String name;
                String value;
                if (o instanceof Map.Entry) {
                    Map.Entry<?, ?> entry = ((Map.Entry<?, ?>) o);
                    name = entry.getKey().toString();
                    value = entry.getKey().toString();
                } else {
                    name = o.toString();
                    value = o.toString();
                }
                choices.add(new Choice(name, value));
            }
        }
        if (!choices.isEmpty()) {
            event.replyChoices(choices).queue();
        }
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event)
    {
        try {
            System.out.println("Slash command interaction");

            MessageChannel channel = event.getChannel();
            SlashCommandInteraction interaction = event.getInteraction();
            InteractionHook hook = event.getHook();

            event.deferReply(false).queue();

            HookMessageChannel hookChannel = new HookMessageChannel(channel, hook);

            String path = event.getCommandPath();
            // TODO get command by path

            Map<String, String> combined = new HashMap<>();
            List<OptionMapping> options = event.getOptions();
            for (OptionMapping option : options) {
                combined.put(option.getName(), option.getAsString());
            }

            System.out.println("Path: " + path + " | values=" + combined);

            List<String> pathArgs = StringMan.split(path, '/');
            Map.Entry<CommandCallable, String> cmdAndPath = commands.getCallableAndPath(pathArgs);
            CommandCallable cmd = cmdAndPath.getKey();
            if (cmd == null) throw new IllegalArgumentException("No command found for " + StringMan.getString(pathArgs));

            LocalValueStore<Object> locals = new LocalValueStore<>(commands.getStore());
            locals.addProvider(Key.of(User.class, Me.class), event.getUser());
            locals.addProvider(Key.of(Guild.class, Me.class), event.getGuild());
            locals.addProvider(Key.of(MessageChannel.class, Me.class), hookChannel);

            List<String> args = new ArrayList<>(); // empty because we are using a arg map instead of parsing a string
            ArgumentStack stack = new ArgumentStack(args, locals, commands.getValidators(), commands.getPermisser());
            locals.addProvider(stack);

            try {
                cmd.validatePermissions(stack.getStore(), stack.getPermissionHandler());
            } catch (Throwable e) {
                e.printStackTrace();
                RateLimitUtil.queue(hook.sendMessage("No permission: " + e.getMessage()));
                return;
            }

            if (cmd instanceof ParametricCallable) {
                ParametricCallable parametric = (ParametricCallable) cmd;

                String cmdRaw = (StringMan.join(pathArgs, " ") + " " + parametric.stringifyArgumentMap(combined, " ")).trim();
                System.out.println("CMD " + cmdRaw);
                Message embedMessage = new MessageBuilder().setContent(Settings.commandPrefix(false) + cmdRaw).build();
                locals.addProvider(Key.of(Message.class, Me.class), embedMessage);

                String formatted = null;
                try {
                    Object[] parsed = parametric.parseArgumentMap(combined, stack);
                    System.out.println("Combined " + StringMan.getString(combined) + " | " + StringMan.getString(parsed));
                    Object result = parametric.call(null, stack.getStore(), parsed);
                    if (result != null) {
                        formatted = (result + "").trim(); // MarkupUtil.markdownToHTML
                    }
                } catch (Throwable e) {
                    while (e.getCause() != null) {
                        e = e.getCause();
                    }
                    e.printStackTrace();
                    formatted = e.getClass().getSimpleName() + ": " + e.getMessage();
                }
                if (formatted != null && !formatted.isEmpty()) {
                    for (String key : Locutus.imp().getPnwApi().getApiKeyUsageStats().keySet()) {
                        formatted = formatted.replaceAll(key, "");
                    }
                    DiscordUtil.sendMessage(hook, formatted);
                } else {
                    RateLimitUtil.queue(hook.sendMessage("(no output)"));
                }
            } else {
                RateLimitUtil.queue(hook.sendMessage("Invalid command: " + StringMan.getString(args)));
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    @Command(desc = "Locutus command")
    public String locutus2(OnlineStatus status, Color color, Map<ResourceType, Double> rss, DBNation nation, Set<Role> roles) {
        // delegate command
        return "Hello World ";
    }
}
