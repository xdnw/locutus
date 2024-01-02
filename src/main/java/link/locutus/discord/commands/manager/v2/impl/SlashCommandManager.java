package link.locutus.discord.commands.manager.v2.impl;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.LocalValueStore;
import link.locutus.discord.commands.manager.v2.binding.Parser;
import link.locutus.discord.commands.manager.v2.binding.annotation.*;
import link.locutus.discord.commands.manager.v2.binding.bindings.autocomplete.PrimitiveCompleter;
import link.locutus.discord.commands.manager.v2.command.*;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordHookIO;
import link.locutus.discord.commands.manager.v2.impl.discord.binding.autocomplete.DiscordCompleter;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.autocomplete.GPTCompleter;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.autocomplete.PWCompleter;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.autocomplete.SheetCompleter;
import link.locutus.discord.config.Settings;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.StringMan;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.NewsChannel;
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel;
import net.dv8tion.jda.api.entities.channel.concrete.StageChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.sticker.Sticker;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.AutoCompleteQuery;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.*;
import net.dv8tion.jda.api.utils.WidgetUtil;
import net.dv8tion.jda.api.utils.data.SerializableData;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import javax.security.auth.login.LoginException;
import java.lang.reflect.Type;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

public class SlashCommandManager extends ListenerAdapter {
    private static final Map<Type, Collection<ChannelType>> CHANNEL_TYPES = new ConcurrentHashMap<>();
    private static final Map<Type, OptionType> OPTION_TYPES = new ConcurrentHashMap<>();

    static {
        CHANNEL_TYPES.put(GuildMessageChannel.class, Collections.singleton(ChannelType.TEXT));
        CHANNEL_TYPES.put(PrivateChannel.class, Collections.singleton(ChannelType.PRIVATE));
        CHANNEL_TYPES.put(VoiceChannel.class, Collections.singleton(ChannelType.VOICE));
//        channelTypes.put(TODO unused, ChannelType.GROUP);
        CHANNEL_TYPES.put(Category.class, Collections.singleton(ChannelType.CATEGORY));
        CHANNEL_TYPES.put(NewsChannel.class, Collections.singleton(ChannelType.NEWS));
        CHANNEL_TYPES.put(StageChannel.class, Collections.singleton(ChannelType.STAGE));
        CHANNEL_TYPES.put(ThreadChannel.class, List.of(ChannelType.GUILD_PUBLIC_THREAD, ChannelType.GUILD_PRIVATE_THREAD));
    }

    static {
        OPTION_TYPES.put(int.class, OptionType.INTEGER);
        OPTION_TYPES.put(short.class, OptionType.INTEGER);
        OPTION_TYPES.put(byte.class, OptionType.INTEGER);
        OPTION_TYPES.put(Integer.class, OptionType.INTEGER);
        OPTION_TYPES.put(Short.class, OptionType.INTEGER);
        OPTION_TYPES.put(Byte.class, OptionType.INTEGER);

        OPTION_TYPES.put(long.class, OptionType.STRING);
        OPTION_TYPES.put(Long.class, OptionType.STRING);
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
        OPTION_TYPES.put(StageChannel.class, OptionType.CHANNEL);
        OPTION_TYPES.put(PrivateChannel.class, OptionType.CHANNEL);

        OPTION_TYPES.put(User.class, OptionType.USER);
        OPTION_TYPES.put(Member.class, OptionType.USER);

        OPTION_TYPES.put(Role.class, OptionType.ROLE);

        OPTION_TYPES.put(IMentionable.class, OptionType.MENTIONABLE);
        // Redundant but added for clarity:
        OPTION_TYPES.put(Emoji.class, OptionType.MENTIONABLE);
        OPTION_TYPES.put(Sticker.class, OptionType.MENTIONABLE);
        OPTION_TYPES.put(ThreadMember.class, OptionType.MENTIONABLE);

        // Fallback, default
        OPTION_TYPES.put(Object.class, OptionType.STRING);
    }

    private final Locutus root;
    private final CommandManager2 commands;
    private final Map<String, Long> commandIds = new HashMap<>();
    private final Set<Key> bindingKeys = new HashSet<>();

    private Map<ParametricCallable, String> commandNames = new HashMap<>();

    public SlashCommandManager(Locutus locutus) {
        this.root = locutus;
        this.commands = root.getCommandManager().getV2();
    }

    public static Collection<ChannelType> getChannelType(Type type) {
        if (type instanceof Class tClass) {
            while (tClass != null) {
                Collection<ChannelType> found = CHANNEL_TYPES.get(tClass);
                if (found != null) return found;
                tClass = tClass.getSuperclass();
            }
        }
        return null;
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
        response.append(indentStr).append(cmd.getName()).append("(desc:").append(cmd.getDescription().length()).append(" option:").append(getOptionSize(cmd.getOptions())).append(" sub:").append(getSubSize(cmd.getSubcommands())).append(" group:").append(getGroupSize(cmd.getSubcommandGroups())).append(")\n");
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
        response.append(indentStr).append(group.getName()).append("(desc:").append(group.getDescription().length()).append(" sub: ").append(getSubSize(group.getSubcommands())).append(")\n");
        for (SubcommandData sub : group.getSubcommands()) {
            printSize(sub, response, indent + 2);
        }
    }

    public void printSize(SubcommandData sub, StringBuilder response, int indent) {
        String indentStr = StringMan.repeat(" ", indent);
        response.append(indentStr).append("sub:").append(sub.getName()).append("(desc:").append(sub.getDescription().length()).append(" option: ").append(getOptionSize(sub.getOptions())).append(")\n");
        for (OptionData option : sub.getOptions()) {
            printSize(option, response, indent + 2);
        }
    }

    public void printSize(OptionData option, StringBuilder response, int indent) {
        String indentStr = StringMan.repeat(" ", indent);
        response.append(indentStr).append("opt:").append(option.getName()).append("(desc:").append(option.getDescription().length()).append(")\n");
    }

    public List<SlashCommandData>  generateCommandData() {
        new PrimitiveCompleter().register(commands.getStore());
        new DiscordCompleter().register(commands.getStore());
        new PWCompleter().register(commands.getStore());
        new SheetCompleter().register(commands.getStore());
        new GPTCompleter().register(commands.getStore());

        List<SlashCommandData> toRegister = new ArrayList<>();
        for (Map.Entry<String, CommandCallable> entry : commands.getCommands().getSubcommands().entrySet()) {
            CommandCallable callable = entry.getValue();
            String id = entry.getKey();
            AtomicInteger size = new AtomicInteger();
            try {
                SlashCommandData cmd = adaptCommands(commandNames, callable, id, null, null, 100, 100, false, true, true, true, true, true);
                if (getSize(cmd) > 4000) {
                    cmd = adaptCommands(commandNames, callable, id, null, null, 100, 100, false, true, true, false, true, true);
                }
                if (getSize(cmd) > 4000) {
                    cmd = adaptCommands(commandNames, callable, id, null, null, 100, 100, false, true, true, false, false, true);
                }
                if (getSize(cmd) > 4000) {
                    cmd = adaptCommands(commandNames, callable, id, null, null, 100, 100, false, true, false, false, false, true);
                }
                if (getSize(cmd) > 4000) {
                    cmd = adaptCommands(commandNames, callable, id, null, null, 100, 100, true, true, false, false, false, true);
                }
                if (getSize(cmd) > 4000) {
                    cmd = adaptCommands(commandNames, callable, id, null, null, 100, 100, true, true, false, false, false, false);
                }
                if (getSize(cmd) > 4000) {
                    cmd = adaptCommands(commandNames, callable, id, null, null, 0, 0, true, true, false, false, false, false);
                }
                toRegister.add(cmd);
            } catch (Throwable e) {
                // print command
                System.out.println("Error: " + id + " | " + callable.getFullPath());
                if (callable instanceof ParametricCallable parametric) {
                    // print method and class
                    System.out.println(parametric.getMethod().getName() + " | " + parametric.getMethod().getDeclaringClass());
                }
                throw e;
            }
        }

        for (SlashCommandData cmd : toRegister) {
            int size = getSize(cmd);
            if (size > 4000) {
                StringBuilder builder = new StringBuilder();
                printSize(cmd, builder, 0);
                System.out.println(builder);
                throw new IllegalArgumentException("Command " + cmd.getName() + "too large: " + size + "");
            }
        }

        return toRegister;
    }

    public void registerCommandData(JDA jda) {
        registerCommandData(jda, generateCommandData());
    }

    public void registerCommandData(JDA jda, List<SlashCommandData> toRegister) {
        if (!toRegister.isEmpty()) {
            List<net.dv8tion.jda.api.interactions.commands.Command> commands = RateLimitUtil.complete(jda.updateCommands().addCommands(toRegister));
            for (net.dv8tion.jda.api.interactions.commands.Command command : commands) {
                String path = command.getName();
                commandIds.put(path, command.getIdLong());
            }
        }
    }

    private static String getCommandPath(ParametricCallable callable) {
        SlashCommandManager slash = Locutus.imp().getSlashCommands();
        return slash == null ? null : slash.commandNames.get(callable);
    }

    public static String getSlashMention(ParametricCallable callable) {
        String path = getCommandPath(callable);
        if (path == null) {
            return "/" + callable.getFullPath();
        }
        return getSlashMention(path);
    }

    public static String getSlashCommand(ParametricCallable callable, Map<String, String> arguments, boolean backTicks) {
        String path = getCommandPath(callable);
        if (path == null) {
            path = callable.getFullPath();
        }
        return getSlashCommand(path, arguments, backTicks);
    }

    public static String getSlashCommand(String path, Map<String, String> arguments, boolean backTicks) {
        StringBuilder builder = new StringBuilder();
        builder.append("/").append(path.toLowerCase(Locale.ROOT));
        if (!arguments.isEmpty()) {
            // join on " "
            for (Map.Entry<String, String> entry : arguments.entrySet()) {
                if (entry.getValue() == null) continue;
                builder.append(" ").append(entry.getKey().toLowerCase(Locale.ROOT)).append(": ").append(entry.getValue());
            }
        }
        if (backTicks) return "`" + builder + "`";
        return builder.toString();
    }

    public static String getSlashMention(String path) {
        Locutus lc = Locutus.imp();
        if (lc != null) {
            SlashCommandManager slash = lc.getSlashCommands();
            if (slash != null) {
                Long id = slash.getCommandId(path);
                if (id != null && id > 0) {
                    return "</" + path.toLowerCase(Locale.ROOT) + ":" + id + ">";
                }
            }
        }
        return getSlashCommand(path, Collections.emptyMap(), true);
    }

    private Long getCommandId(String path) {
        Long id = commandIds.get(path);
        if (id == null) id = commandIds.get(path.split(" ")[0]);
        return id;
    }

    public SlashCommandData adaptCommands(Map<ParametricCallable, String> finalMappings, CommandCallable callable, String id, SlashCommandData root, SubcommandGroupData discGroup, int maxDescription, int maxOption, boolean breakNewlines, boolean includeTypes, boolean includeExample, boolean includeRepeatedTypes, boolean includeDescForChoices, boolean includeOptionDesc) {
        String desc = callable.simpleDesc();
        if (desc.length() >= maxDescription) {
            desc = desc.split("\n")[0];
        }
        if (desc.length() >= maxDescription) {
            desc = desc.substring(0, maxDescription);
        }
        if (breakNewlines && desc.contains("\n")) desc = desc.split("\n")[0];
        if (desc.isEmpty()) desc = "_";

        SerializableData current = null;
        if (root == null) {
            root = Commands.slash(id.toLowerCase(Locale.ROOT), desc);
            current = root;
        }
        if (callable instanceof ICommandGroup group) {
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
                adaptCommands(finalMappings, subCmd, subId, root, discGroup, maxDescription, maxOption, breakNewlines, includeTypes, includeExample, includeRepeatedTypes, includeDescForChoices, includeOptionDesc);
            }
        } else if (callable instanceof ParametricCallable parametric) {
            List<OptionData> options = createOptions(parametric, maxOption, breakNewlines, includeTypes, includeExample, includeRepeatedTypes, includeDescForChoices, includeOptionDesc);

            String fullPath = "";
            fullPath += root.getName() + " ";
            if (discGroup != null) fullPath += discGroup.getName() + " ";
            if (root != current) fullPath += id;
            fullPath = fullPath.trim();

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
                } else {
                    ((SubcommandData) current).addOptions(options);
                }
            } catch (Throwable e) {
                System.out.println("Error creating command: " + id);
                for (OptionData option : options) {
                    System.out.println("- option " + option.getName() + " | " + option.getType() + " | " + option.getDescription());
                }
                e.printStackTrace();
                throw e;
            }
            finalMappings.put(parametric, fullPath);
        }
        return root;
    }

    public List<OptionData> createOptions(ParametricCallable cmd, int maxOption, boolean breakNewlines, boolean includeTypes, boolean includeExample, boolean includeRepeatedTypes, boolean includeDescForChoices, boolean includeDesc) {
        List<OptionData> result = new ArrayList<>();
        Set<Type> paramTypes = new HashSet<>();
        List<ParameterData> params = cmd.getUserParameters();
        for (int l = 0; l < params.size(); l++) {
            ParameterData param = params.get(l);
            Type type = param.getType();
            String paramName = param.getName();
            String desc;
            if (paramName.length() == 1 && l > 0 && params.get(l - 1).getType().equals(type) && params.size() > 20) {
                desc = "_";
            } else {
                desc = !includeDesc ? "" : param.getExpandedDescription(false, includeExample, includeDesc);
                String simpleDesc = includeDesc ? param.getDescription() : "";
                if (desc.length() > maxOption && simpleDesc != null) {
                    desc = simpleDesc;
                }
                if (breakNewlines && desc.contains("\n")) desc = desc.split("\n")[0];
                if (desc.length() > maxOption) {
                    desc = desc.substring(0, maxOption);
                }
                if (breakNewlines && desc.contains("\n")) desc = desc.split("\n")[0];
                if (desc.trim().isEmpty() && includeTypes) {
                    desc = param.getType().getTypeName().replaceAll("[a-z_A-Z0-9.]+\\.([a-z_A-Z0-9]+)", "$1").replaceAll("[a-z_A-Z0-9]+\\$([a-z_A-Z0-9]+)", "$1");
                }
                if (!includeRepeatedTypes) {
                    if (!paramTypes.add(param.getType())) {
                        desc = "";
                    }
                }
                if (desc.isEmpty()) {
                    desc = "_";
                }
            }

            Range range = param.getAnnotation(Range.class);
            Step step = param.getAnnotation(Step.class);
            Timestamp timestamp = param.getAnnotation(Timestamp.class);
            Timediff timediff = param.getAnnotation(Timediff.class);
            ArgChoice choiceAnn = param.getAnnotation(ArgChoice.class);

            OptionType optionType = (timestamp != null || timediff != null) ? OptionType.STRING : createType(type);
            OptionData option = new OptionData(optionType, paramName.toLowerCase(Locale.ROOT), desc);

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
                if (type instanceof Class clazz) {
                    if (clazz.isEnum()) {
                        Object[] values = clazz.getEnumConstants();
                        if (values.length <= OptionData.MAX_CHOICES) {
                            isEnumChoice = true;
                            for (Object value : values) {
                                String name = ((Enum) value).name();
                                option.addChoice(name, name);
                            }
                        }
                    }
                    if (choiceAnn != null && choiceAnn.value().length <= OptionData.MAX_CHOICES) {
                        isEnumChoice = true;
                        for (String name : choiceAnn.value()) {
                            option.addChoice(name, name);
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
                if (options > 0 && options < 16) {
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

            option.setRequired(!param.isOptional() && !param.isFlag());

            result.add(option);
        }
        return result;
    }

    public OptionType createType(Type type) {
        if (type instanceof Class tClass) {
            while (tClass != null) {
                OptionType found = OPTION_TYPES.get(tClass);
                if (found != null) return found;
                tClass = tClass.getSuperclass();
            }
        }
        return OptionType.STRING;
    }

    private Map<Long, Long> userIdToAutoCompleteTimeNs = new ConcurrentHashMap<>();

    @Override
    public void onCommandAutoCompleteInteraction(@Nonnull CommandAutoCompleteInteractionEvent event) {
        long startNanos = System.nanoTime();
        User user = event.getUser();
        userIdToAutoCompleteTimeNs.put(user.getIdLong(), startNanos);

        String path = event.getFullCommandName().replaceAll("/", " ");
        AutoCompleteQuery option = event.getFocusedOption();
        String optionName = option.getName();

        List<String> pathArgs = StringMan.split(path, ' ');
        Map.Entry<CommandCallable, String> cmdAndPath = commands.getCallableAndPath(pathArgs);
        CommandCallable cmd = cmdAndPath.getKey();

        if (cmd == null) {
            // No command found
            System.out.println("remove:||No command found: " + path);
            return;
        }

        if (!(cmd instanceof ParametricCallable parametric)) {
            System.out.println("remove:||Not parametric: " + path);
            return;
        }

        Map<String, ParameterData> map = parametric.getUserParameterMap();
        ParameterData param = map.get(optionName);
        if (param == null) {
            System.out.println("remove:||No parameter found for " + optionName + " | " + map.keySet());
            return;
        }

        if (event.getUser().getIdLong() == Settings.INSTANCE.ADMIN_USER_ID) {
            System.out.println("remove:||Admin runs complete " + path + " | " + option.getValue());
        }

        /*
        Dont post if they have run the command
        Dont post if they have typed something else since then
         */
        ExecutorService executor = Locutus.imp().getExecutor();
        executor.submit(new Runnable() {
            @Override
            public void run() {
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
                if (event.isFromGuild()) {
                    locals.addProvider(Key.of(Guild.class, Me.class), event.getGuild());
                }
                if (event.getMessageChannel() != null) {
                    locals.addProvider(Key.of(MessageChannel.class, Me.class), event.getMessageChannel());
                }

                // Option with current value
                List<String> args = new ArrayList<>(List.of(option.getValue()));
                ArgumentStack stack = new ArgumentStack(args, locals, commands.getValidators(), commands.getPermisser());
                locals.addProvider(stack);

                List<Choice> choices = new ArrayList<>();
                if (autoParse) {
                    binding.apply(stack);
                } else {
                    Object result = parser.apply(stack);
                    if (!(result instanceof List) || ((List) result).isEmpty()) {
                        long diff = System.currentTimeMillis() - (startNanos / 1_000_000);
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
                        if (o instanceof Map.Entry<?, ?> entry) {
                            name = entry.getKey().toString();
                            value = entry.getValue().toString();
                        } else {
                            name = o.toString();
                            value = o.toString();
                        }
                        choices.add(new Choice(name, value));
                    }
                }
                if (!choices.isEmpty()) {
                    double diff = (System.nanoTime() - startNanos) / 1_000_000d;
                    if (diff > 15) {
                        System.out.println("remove:||results for " + option.getValue() + " | " + key + " | " + MathMan.format(diff));
                    }
                    long newCompleteTime = userIdToAutoCompleteTimeNs.get(user.getIdLong());
                    if (newCompleteTime != startNanos) {
                        return;
                    }
                    RateLimitUtil.queue(event.replyChoices(choices));
                }
            }
        });
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        try {
            long start = System.currentTimeMillis();
            userIdToAutoCompleteTimeNs.put(event.getUser().getIdLong(), System.nanoTime());

            MessageChannel channel = event.getChannel();
            InteractionHook hook = event.getHook();

            boolean isModal = true;

            String path = event.getFullCommandName().replace("/", " ").toLowerCase(Locale.ROOT);
            if (!path.contains("modal")) {
                isModal = false;
                if (path.equalsIgnoreCase("announcement view")) {
                    RateLimitUtil.complete(event.deferReply(true));
                    hook.setEphemeral(true);
                } else {
                    System.out.println("Normal reply");
                    RateLimitUtil.queue(event.deferReply(false));
                }
            }

            Map<String, String> combined = new HashMap<>();
            List<OptionMapping> options = event.getOptions();
            for (OptionMapping option : options) {
                combined.put(option.getName(), option.getAsString());
            }

            System.out.println("Path: " + path + " | values=" + combined);

            DiscordHookIO io = new DiscordHookIO(hook, event);
            if (isModal) {
                io.setIsModal(event);
            }
            Guild guild = event.isFromGuild() ? event.getGuild() : null;
            Locutus.imp().getCommandManager().getV2().run(guild, channel, event.getUser(), null, io, path, combined, true);
            long end = System.currentTimeMillis();
            if (end - start > 15) {
                System.out.println("remove:||Slash command took " + (end - start) + "ms");
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }
}