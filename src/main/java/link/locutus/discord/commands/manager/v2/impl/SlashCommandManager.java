package link.locutus.discord.commands.manager.v2.impl;

import it.unimi.dsi.fastutil.objects.*;
import link.locutus.discord.Locutus;
import link.locutus.discord.Logg;
import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.LocalValueStore;
import link.locutus.discord.commands.manager.v2.binding.Parser;
import link.locutus.discord.commands.manager.v2.binding.annotation.*;
import link.locutus.discord.commands.manager.v2.binding.bindings.autocomplete.PrimitiveCompleter;
import link.locutus.discord.commands.manager.v2.command.*;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordHookIO;
import link.locutus.discord.commands.manager.v2.impl.discord.binding.DiscordBindings;
import link.locutus.discord.commands.manager.v2.impl.discord.binding.autocomplete.DiscordCompleter;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.autocomplete.GPTCompleter;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.autocomplete.PWCompleter;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.autocomplete.SheetCompleter;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.menu.AppMenu;
import link.locutus.discord.user.Roles;
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
import net.dv8tion.jda.api.events.interaction.command.*;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.AutoCompleteQuery;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
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
import java.util.function.Supplier;

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

    private final Supplier<CommandManager2> provider;

    private CommandManager2 commandsOrNull;
    private Set<String> ephemeralOrNull = Collections.emptySet();
    private Set<String> userCommandsOrNull = Collections.emptySet();
    private Set<String> messageCommandsOrNull = Collections.emptySet();
    private final Map<String, Long> commandIds = new Object2LongOpenHashMap<>();
    private final Set<Key> bindingKeys = new HashSet<>();
    private final boolean registerAdminCmds;

    private final Map<ParametricCallable, String> commandNames = Collections.synchronizedMap(new Object2ObjectOpenHashMap<>());

    public SlashCommandManager(boolean registerAdminCmds, Supplier<CommandManager2> provider) {
        this.registerAdminCmds = registerAdminCmds;
        this.provider = provider;
    }

    public Set<String> getUserCommandsOrNull() {
        return userCommandsOrNull;
    }

    public Set<String> getMessageCommandsOrNull() {
        return messageCommandsOrNull;
    }

    private CommandManager2 getCommands() {
        if (commandsOrNull == null) {
            synchronized (this) {
                if (commandsOrNull == null) {
                    commandsOrNull = provider.get();
                    this.ephemeralOrNull = generateEphemeral(commandsOrNull.getCommands());
                    this.userCommandsOrNull = generateUserCommands(commandsOrNull.getCommands());
                    this.messageCommandsOrNull = generateMessageCommands(commandsOrNull.getCommands());
                }
            }
        }
        return commandsOrNull;
    }

    private Set<String> generateEphemeral(CommandGroup group) {
        Set<String> ephemeral = new ObjectOpenHashSet<>();
        for (ParametricCallable cmd : group.getParametricCallables(f -> true)) {
            if (cmd.getAnnotation(Ephemeral.class) != null) {
                ephemeral.add(cmd.getFullPath().toLowerCase(Locale.ROOT));
            }
        }
        return ephemeral;
    }

    public Set<String> generateUserCommands(CommandGroup group) {
        Set<String> cmds = new ObjectLinkedOpenHashSet<>();
        for (ParametricCallable cmd : group.getParametricCallables(f -> true)) {
            if (cmd.getAnnotation(UserCommand.class) != null) {
                cmds.add(cmd.getFullPath().toLowerCase(Locale.ROOT));
            }
        }
        cmds.add("...more");
        return cmds;
    }

    public Set<String> generateMessageCommands(CommandGroup group) {
        Set<String> cmds = new ObjectLinkedOpenHashSet<>();
        for (ParametricCallable cmd : group.getParametricCallables(f -> true)) {
            if (cmd.getAnnotation(MessageCommand.class) != null) {
                cmds.add(cmd.getFullPath().toLowerCase(Locale.ROOT));
            }
        }
        cmds.add("...more");
        return cmds;
    }

    private boolean isAdmin(ParametricCallable cmd) {
        RolePermission rolePerm = cmd.getAnnotation(RolePermission.class);
        if (rolePerm == null) return false;
        return rolePerm.root() && rolePerm.value().length == 1 && rolePerm.value()[0] == Roles.ADMIN;
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

    public List<CommandData>  generateCommandData() {
        CommandManager2 commands = getCommands();
        new PrimitiveCompleter().register(commands.getStore());
        new DiscordCompleter().register(commands.getStore());
        new PWCompleter().register(commands.getStore());
        new SheetCompleter().register(commands.getStore());
        new GPTCompleter().register(commands.getStore());

        List<CommandData> toRegister = new ObjectArrayList<>();
        for (Map.Entry<String, CommandCallable> entry : commands.getCommands().getSubcommands().entrySet()) {
            CommandCallable callable = entry.getValue();
            String id = entry.getKey();
            try {
                List<UpdateCommandDesc> updaters = new ObjectArrayList<>();
                SlashCommandData cmd = adaptCommands(commandNames, callable, id, null, null, updaters);
                UpdateCommandDesc.updateAll(updaters, 100, 100, false, true, true, true, true, true);
                if (getSize(cmd) > 8000) {
                    UpdateCommandDesc.updateAll(updaters, 100, 100, false, true, true, false, true, true);
                }
                if (getSize(cmd) > 8000) {
                    UpdateCommandDesc.updateAll(updaters, 100, 100, false, true, true, false, false, true);
                }
                if (getSize(cmd) > 8000) {
                    UpdateCommandDesc.updateAll(updaters, 100, 100, false, true, false, false, false, true);
                }
                if (getSize(cmd) > 8000) {
                    UpdateCommandDesc.updateAll(updaters, 100, 100, true, true, false, false, false, true);
                }
                if (getSize(cmd) > 8000) {
                    UpdateCommandDesc.updateAll(updaters, 100, 100, true, true, false, false, false, false);
                }
                if (getSize(cmd) > 8000) {
                    UpdateCommandDesc.updateAll(updaters, 0, 0, true, true, false, false, false, false);
                }
                toRegister.add(cmd);
            } catch (Throwable e) {
                System.out.println("Slash command error: " + id + " | " + callable.getFullPath());
                if (callable instanceof ParametricCallable parametric) {
                    System.out.println(parametric.getMethod().getName() + " | " + parametric.getMethod().getDeclaringClass());
                }
                throw e;
            }
        }
        if (userCommandsOrNull != null) {
            for (String cmd : userCommandsOrNull) {
                toRegister.add(Commands.user(cmd));
            }
        }
        if (messageCommandsOrNull != null) {
            for (String cmd : messageCommandsOrNull) {
                toRegister.add(Commands.message(cmd));
            }
        }

        for (CommandData cmd : toRegister) {
            if (cmd instanceof SlashCommandData slash) {
                int size = getSize(slash);
                if (size > 8000) {
                    StringBuilder builder = new StringBuilder();
                    printSize(slash, builder, 0);
                    System.out.println(builder);
                    System.out.println(cmd);
                    throw new IllegalArgumentException("Command " + cmd.getName() + " is too large: " + size + "");
                }
            }
        }
        return toRegister;
    }

    public void registerCommandData(JDA jda) {
        List<CommandData> generate = generateCommandData();
        registerCommandData(jda, generate);
    }

    public void register(Guild guild) {
        List<CommandData> toRegister = generateCommandData();
        if (!toRegister.isEmpty()) {
            List<net.dv8tion.jda.api.interactions.commands.Command> commands = RateLimitUtil.complete(guild.updateCommands().addCommands(toRegister));
            for (net.dv8tion.jda.api.interactions.commands.Command command : commands) {
                String path = command.getName();
                commandIds.put(path, command.getIdLong());
            }
        }
    }

    public void registerCommandData(JDA jda, List<CommandData> toRegister) {
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

    // int maxDescription,
    // int maxOption,
    // boolean breakNewlines,
    // boolean includeTypes,
    // boolean includeExample,
    // boolean includeRepeatedTypes,
    // boolean includeDescForChoices,
    // boolean includeOptionDesc

    private static abstract class UpdateCommandDesc {
        public static void updateAll(List<UpdateCommandDesc> updaters, int maxDescription, int maxOption, boolean breakNewlines, boolean includeTypes, boolean includeExample, boolean includeRepeatedTypes, boolean includeDescForChoices, boolean includeOptionDesc) {
            for (UpdateCommandDesc updater : updaters) {
                updater.update(maxDescription, maxOption, breakNewlines, includeTypes, includeExample, includeRepeatedTypes, includeDescForChoices, includeOptionDesc);
            }
        }
        protected final ConcurrentHashMap<Long, String> cache;
        private List<SerializableData> data;

        public UpdateCommandDesc() {
            this.cache = new ConcurrentHashMap<>();
            this.data = new ObjectArrayList<>();
        }

        public UpdateCommandDesc addCommandData(SerializableData data) {
            this.data.add(data);
            return this;
        }

        public void update(int maxDescription, int maxOption, boolean breakNewlines, boolean includeTypes, boolean includeExample, boolean includeRepeatedTypes, boolean includeDescForChoices, boolean includeOptionDesc) {
            setDesc(generate(maxDescription, maxOption, breakNewlines, includeTypes, includeExample, includeRepeatedTypes, includeDescForChoices, includeOptionDesc));
        }

        public UpdateCommandDesc setDesc(String desc) {
            if (desc.isEmpty()) desc = "_";
            for (SerializableData data : this.data) {
                if (data instanceof  SlashCommandData slash) {
                    slash.setDescription(desc);
                } else if (data instanceof SubcommandData sub) {
                    sub.setDescription(desc);
                } else if (data instanceof SubcommandGroupData group) {
                    group.setDescription(desc);
                } else if (data instanceof OptionData optionData) {
                    optionData.setDescription(desc);
                } else {
                    throw new IllegalArgumentException("Unknown data type: " + data);
                }
            }
            return this;
        }

        protected abstract String generate(int maxDescription, int maxOption, boolean breakNewlines, boolean includeTypes, boolean includeExample, boolean includeRepeatedTypes, boolean includeDescForChoices, boolean includeOptionDesc);
    }

    private SlashCommandData adaptCommands(Map<ParametricCallable, String> finalMappings, CommandCallable callable, String id, SlashCommandData root, SubcommandGroupData discGroup, List<UpdateCommandDesc> updaters) {
        String finalDesc = callable.simpleDesc();
        UpdateCommandDesc descUpdater = new UpdateCommandDesc() {
            @Override
            public String generate(int maxDescription, int maxOption, boolean breakNewlines, boolean includeTypes, boolean includeExample, boolean includeRepeatedTypes, boolean includeDescForChoices, boolean includeOptionDesc) {
                if (finalDesc.isEmpty()) return finalDesc;
                long hash = (breakNewlines ? 1 : 0) + ((long) maxDescription << 1);
                return cache.computeIfAbsent(hash, f -> {
                    String desc = finalDesc;
                    if (desc.length() >= maxDescription) {
                        desc = desc.split("\n")[0];
                    }
                    if (desc.length() >= maxDescription) {
                        desc = desc.substring(0, maxDescription);
                    }
                    if (breakNewlines && desc.contains("\n")) desc = desc.split("\n")[0];
                    if (desc.isEmpty()) desc = "_";
                    return desc;
                });
            }
        };

        SerializableData current = null;
        if (root == null) {
            root = Commands.slash(id.toLowerCase(Locale.ROOT), "_");
            updaters.add(descUpdater.addCommandData(root));
            current = root;
        }
        if (callable instanceof ICommandGroup group) {
            if (current == null) {
                if (discGroup == null) {
                    discGroup = new SubcommandGroupData(id, "_");
                    updaters.add(descUpdater.addCommandData(discGroup));
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
                adaptCommands(finalMappings, subCmd, subId, root, discGroup, updaters);
            }
        } else if (callable instanceof ParametricCallable parametric) {
            if (!registerAdminCmds && isAdmin(parametric)) return root;
            List<OptionData> options = createOptions(parametric, updaters);
            String fullPath = "";
            fullPath += root.getName() + " ";
            if (discGroup != null) fullPath += discGroup.getName() + " ";
            if (root != current) fullPath += id;
            fullPath = fullPath.trim();
            try {
                if (current == null) {
                    SubcommandData discSub = new SubcommandData(id, "_");
                    updaters.add(descUpdater.addCommandData(discSub));
                    current = discSub;

                    if (discGroup != null) {
                        discGroup.addSubcommands(discSub);
                    } else {
                        root.addSubcommands(discSub);
                    }
                }
                if (current instanceof SlashCommandData) {
                    ((SlashCommandData) current).addOptions(options);
                } else {
                    ((SubcommandData) current).addOptions(options);
                }
            } catch (Throwable e) {
                System.out.println("Error creating command: " + fullPath);
                for (OptionData option : options) {
                    System.out.println("- option " + option.getName() + " | " + option.getType() + " | " + option.getDescription());
                }
                e.printStackTrace();
                throw e;
            }
            finalMappings.put(parametric, fullPath);
        }
        root.setDefaultPermissions(DefaultMemberPermissions.ENABLED);
        return root;
    }

    private List<OptionData> createOptions(ParametricCallable cmd, List<UpdateCommandDesc> updaters) {
        final List<OptionData> result = new ArrayList<>();
        final Set<Type> paramTypes = new HashSet<>();
        final List<ParameterData> params = cmd.getUserParameters();
        for (int l = 0; l < params.size(); l++) {
            final ParameterData param = params.get(l);
            final Type type = param.getType();
            final String paramName = param.getName();

            Range range = param.getAnnotation(Range.class);
            Step step = param.getAnnotation(Step.class);
            Timestamp timestamp = param.getAnnotation(Timestamp.class);
            Timediff timediff = param.getAnnotation(Timediff.class);
            ArgChoice choiceAnn = param.getAnnotation(ArgChoice.class);

            OptionType optionType = (timestamp != null || timediff != null) ? OptionType.STRING : createType(type);
            OptionData option = new OptionData(optionType, paramName.toLowerCase(Locale.ROOT), "_");

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
                        Parser parser = getCommands().getStore().get(parserKey);
                        if (parser != null && !parser.getKey().equals(emptyKey)) {
                            option.setAutoComplete(true);
                        } else {
                            Key completerKey = binding.getKey().append(Autocomplete.class);
                            parser = getCommands().getStore().get(completerKey);
                            if (parser != null && !parser.getKey().equals(emptyKey)) {
                                option.setAutoComplete(true);
                            } else {
                                if (bindingKeys.add(completerKey) && Settings.INSTANCE.LEGACY_SETTINGS.PRINT_MISSING_AUTOCOMPLETE) {
                                    Logg.text("No autocomplete binding: " + binding.getKey());
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
            boolean isRepeatedType = (!paramTypes.add(param.getType()));
            if ((paramName.length() == 1 && l > 0 && params.get(l - 1).getType().equals(type) && params.size() > 20)) {
                option.setDescription("_");
            } else {
                UpdateCommandDesc update = new UpdateCommandDesc() {
                    private String expandedNoExample;
                    private String expandedExample;
                    private String simpleDesc;
                    private boolean hasSimpleDesc;
                    private String typeStr;

                    @Override
                    public String generate(int maxDescription, int maxOption, boolean breakNewlines, boolean includeTypes, boolean includeExample, boolean includeRepeatedTypes, boolean includeDescForChoices, boolean includeOptionDesc) {
                        if (isRepeatedType && !includeRepeatedTypes) {
                            return "";
                        }
                        if (!includeDescForChoices && !option.getChoices().isEmpty()) {
                            return "";
                        }
                        String desc = includeExample ?
                                (expandedExample == null ? (expandedExample = param.getExpandedDescription(false, true, true)) : expandedExample) :
                                (expandedNoExample == null ? (expandedNoExample = param.getExpandedDescription(false, false, true)) : expandedNoExample);
                        if (desc.length() > maxOption) {
                            if (!hasSimpleDesc) {
                                hasSimpleDesc = true;
                                simpleDesc = param.getDescription();
                            }
                            if (simpleDesc != null) {
                                desc = simpleDesc;
                            }
                        }
                        if (breakNewlines) {
                            long hash = ((includeExample ? 1 : 0)) + ((long) maxOption << 1);
                            String finalDesc = desc;
                            desc = cache.computeIfAbsent(hash, f -> {
                                if (finalDesc.contains("\n")) {
                                    return finalDesc.split("\n")[0];
                                }
                                return finalDesc;
                            });
                        }
                        if (desc.length() > maxOption) {
                            long hash = (breakNewlines ? 1 : 0) + ((includeExample ? 1 : 0) << 1) + ((long) maxOption << 2);
                            String finalDesc = desc;
                            desc = cache.computeIfAbsent(hash, f -> {
                                String tmp = finalDesc;
                                if (tmp.contains("\n")) {
                                    tmp = finalDesc.split("\n")[0];
                                }
                                if (tmp.length() > maxOption) {
                                    tmp = tmp.substring(0, maxOption);
                                }
                                return tmp;
                            });
                        }
                        if (desc.trim().isEmpty() && includeTypes) {
                            desc = typeStr == null ? (typeStr = param.getType().getTypeName().replaceAll("[a-z_A-Z0-9.]+\\.([a-z_A-Z0-9]+)", "$1").replaceAll("[a-z_A-Z0-9]+\\$([a-z_A-Z0-9]+)", "$1")) : typeStr;
                        }
                        return desc;
                    }
                };
                updaters.add(update.addCommandData(option));
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
        CommandManager2 manager = getCommands();
        long startNanos = System.nanoTime();
        User user = event.getUser();
        userIdToAutoCompleteTimeNs.put(user.getIdLong(), startNanos);

        String path = event.getFullCommandName().replaceAll("/", " ");
        AutoCompleteQuery option = event.getFocusedOption();
        String optionName = option.getName();

        List<String> pathArgs = StringMan.split(path, ' ');
        Map.Entry<CommandCallable, String> cmdAndPath = manager.getCommands().getCallableAndPath(pathArgs);
        CommandCallable cmd = cmdAndPath.getKey();

        if (cmd == null) {
            Logg.text("[Autocomplete]" + user + " | No command found for: `" + path + "`");
            return;
        }

        if (!(cmd instanceof ParametricCallable parametric)) {
            Logg.text("[Autocomplete]" + user + " | Cannot provide completions for a command group: `" + path + "`");
            return;
        }

        Map<String, ParameterData> map = parametric.getUserParameterMap();
        ParameterData param = map.get(optionName);
        if (param == null) {
            Logg.text("[Autocomplete]" + user + " | No parameter found for `" + optionName + "` (args: `" + map.keySet() + "`) at `" + path + "`");
            return;
        }

        /*
        Dont post if they have run the command
        Dont post if they have typed something else since then
         */
        ExecutorService executor = Locutus.imp().getExecutor();
        executor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    boolean autoParse = true;
                    Parser binding = param.getBinding();
                    Key key = binding.getKey();
                    Key parserKey = key.append(Autoparse.class);
                    Parser parser = manager.getStore().get(parserKey);

                    if (parser == null) {
                        autoParse = false;
                        Key completerKey = key.append(Autocomplete.class);
                        parser = manager.getStore().get(completerKey);
                    }

                    if (parser == null) {
                        Logg.text("[Autocomplete]" + user + " | No parser or completer found for `" + key + "` at `" + path + "`");
                        return;
                    }

                    LocalValueStore<Object> locals = new LocalValueStore<>(manager.getStore());
                    locals.addProvider(Key.of(User.class, Me.class), event.getUser());
                    if (event.isFromGuild()) {
                        locals.addProvider(Key.of(Guild.class, Me.class), event.getGuild());
                    }
                    if (event.getMessageChannel() != null) {
                        locals.addProvider(Key.of(MessageChannel.class, Me.class), event.getMessageChannel());
                    }

                    // Option with current value
                    List<String> args = new ArrayList<>(List.of(option.getValue()));
                    ArgumentStack stack = new ArgumentStack(args, locals, manager.getValidators(), manager.getPermisser());
                    locals.addProvider(stack);

                    List<Choice> choices = new ArrayList<>();
                    if (autoParse) {
                        binding.apply(stack);
                    } else {
                        Object result = parser.apply(stack);
                        if (!(result instanceof List) || ((List) result).isEmpty()) {
                            long diff = System.currentTimeMillis() - (startNanos / 1_000_000);
                            Logg.text("[Autocomplete]" + user + " | No results for `" + option.getValue() + "` at `" + path + "` took " + diff + "ms");
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
                            Logg.text("[Autocomplete]" + user + " | Results for `" + option.getValue() + "` at `" + path + "` took " + diff + "ms");
                        }
                        long newCompleteTime = userIdToAutoCompleteTimeNs.get(user.getIdLong());
                        if (newCompleteTime != startNanos) {
                            return;
                        }
                        RateLimitUtil.queue(event.replyChoices(choices));
                    }
                } catch (Throwable e) {
                    e.printStackTrace();
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
                if (isEphemeral(path)) {
                    RateLimitUtil.complete(event.deferReply(true));
                    hook.setEphemeral(true);
                } else {
                    RateLimitUtil.queue(event.deferReply(false));
                }
            }

            Map<String, String> combined = new HashMap<>();
            List<OptionMapping> options = event.getOptions();
            for (OptionMapping option : options) {
                combined.put(option.getName(), option.getAsString());
            }

            DiscordHookIO io = new DiscordHookIO(hook, event);
            if (isModal) {
                io.setIsModal(event);
            }
            Guild guild = event.isFromGuild() ? event.getGuild() : null;
            Locutus.imp().getCommandManager().getV2().run(guild, channel, event.getUser(), null, io, path, combined, true);
            long end = System.currentTimeMillis();
            if (end - start > 15) {
                Logg.text("[Slash Command] Slash interaction `" + path + "` | `" + combined + "` took " + (end - start) + "ms");
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public boolean isEphemeral(String path) {
        return ephemeralOrNull.contains(path.toLowerCase(Locale.ROOT));
    }

    @Override
    public void onUserContextInteraction(UserContextInteractionEvent event) {
        handleContext(event, event.getTarget(), event.getTarget().getAsMention(), null, true);
    }

    public <T extends ISnowflake> void handleContext(GenericContextInteractionEvent event, T target, String mention, Supplier<String> getMsg, boolean isUser) {
        String path = event.getFullCommandName().replace("/", " ").toLowerCase(Locale.ROOT);
        MessageChannel channel = event.getMessageChannel();
        InteractionHook hook = event.getHook();
        Guild guild = event.isFromGuild() ? event.getGuild() : null;

        DiscordHookIO io = new DiscordHookIO(hook, event);
        RateLimitUtil.complete(event.deferReply(true));
        hook.setEphemeral(true);

        User user = event.getUser();

        String fullCmdStr;
        if (guild == null) {
            fullCmdStr = path + " " + mention;
        } else {
            GuildDB db = Locutus.imp().getGuildDB(guild);
            AppMenu menu = DiscordBindings.menu(io, db, user, isUser ? "user" : "message");

            if (isUser) {
                menu.targetUser = target.getIdLong();
            }
            else {
                menu.targetMessage = mention;
                menu.targetContent = getMsg.get();
            }
            fullCmdStr = menu.buttons.get(path.toLowerCase(Locale.ROOT));
            System.out.println("Get path " + path + " | " + fullCmdStr + " | " + menu.buttons);
            if ((path.equalsIgnoreCase("...more") && fullCmdStr == null) || "...more".equalsIgnoreCase(fullCmdStr)) {
                fullCmdStr = menu.formatCommand(guild, user, CM.menu.open.cmd.menu(isUser ? "user" : "message").toCommandArgs());
            } else if (fullCmdStr == null) {
                fullCmdStr = path + " " + mention;
            }
            fullCmdStr = menu.formatCommand(guild, user, fullCmdStr);
        }
        Locutus.imp().getCommandManager().getV2().run(guild, channel, user, null, io, fullCmdStr, true, true);
    }

    @Override
    public void onMessageContextInteraction(MessageContextInteractionEvent event) {
        handleContext(event, event.getTarget(), event.getTarget().getJumpUrl(), () -> {
            try {
                return event.getTarget().getContentRaw();
            } catch (RuntimeException e) {
                e.printStackTrace();
                return (String) null;
            }
        }, false);
    }
}