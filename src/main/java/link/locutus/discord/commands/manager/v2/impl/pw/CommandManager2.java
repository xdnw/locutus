package link.locutus.discord.commands.manager.v2.impl.pw;

import com.google.gson.JsonObject;
import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.LocalValueStore;
import link.locutus.discord.commands.manager.v2.binding.SimpleValueStore;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.bindings.PrimitiveBindings;
import link.locutus.discord.commands.manager.v2.binding.bindings.PrimitiveValidators;
import link.locutus.discord.commands.manager.v2.binding.validator.ValidatorStore;
import link.locutus.discord.commands.manager.v2.command.ArgumentStack;
import link.locutus.discord.commands.manager.v2.command.CommandCallable;
import link.locutus.discord.commands.manager.v2.command.CommandGroup;
import link.locutus.discord.commands.manager.v2.command.CommandUsageException;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.command.ParameterData;
import link.locutus.discord.commands.manager.v2.command.ParametricCallable;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordHookIO;
import link.locutus.discord.commands.manager.v2.impl.discord.binding.DiscordBindings;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.PWBindings;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.PermissionBinding;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.SheetBindings;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.StockBinding;
import link.locutus.discord.commands.manager.v2.impl.pw.commands.*;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.NationPlaceholders;
import link.locutus.discord.commands.manager.v2.perm.PermissionHandler;
import link.locutus.discord.config.Settings;
import link.locutus.discord.config.yaml.file.YamlConfiguration;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.web.test.TestCommands;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import org.json.JSONObject;

import java.io.File;
import java.util.AbstractMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CommandManager2 {
    private final CommandGroup commands;
    private final ValueStore<Object> store;
    private final ValidatorStore validators;
    private final PermissionHandler permisser;
    private final NationPlaceholders nationPlaceholders;

    public CommandManager2() {
        this.store = new SimpleValueStore<>();
        new PrimitiveBindings().register(store);
        new DiscordBindings().register(store);
        new PWBindings().register(store);
        new SheetBindings().register(store);
        new StockBinding().register(store);

        this.validators = new ValidatorStore();
        new PrimitiveValidators().register(validators);

        this.permisser = new PermissionHandler();
        new PermissionBinding().register(permisser);

        this.nationPlaceholders = new NationPlaceholders(store, validators, permisser);

        this.commands = CommandGroup.createRoot(store, validators);
    }

    public CommandManager2 registerDefaults() {

        StockCommands stock = new StockCommands();

        CommandGroup legacy = CommandGroup.createRoot(store, validators);
        //        this.commands.registerSubCommands(new ExchangeCommands(), "exchange", "corp", "corporation");
        legacy.registerSubCommands(stock, "stock");
        legacy.registerCommands(new UtilityCommands());

        legacy.registerCommands(new BankCommands());
        legacy.registerCommands(new StatCommands());
        legacy.registerCommands(new IACommands());
        legacy.registerCommands(new AttackCommands());
        legacy.registerCommands(new AdminCommands());
        legacy.registerCommands(new DiscordCommands());
        legacy.registerCommands(new FACommands());
        legacy.registerCommands(new FunCommands());
        legacy.registerCommands(new PlayerSettingCommands());
        legacy.registerCommands(new LoanCommands());
        legacy.registerCommands(new TradeCommands());
        legacy.registerCommands(new WarCommands());
        legacy.registerSubCommands(new GrantCommands(), "grant");
        legacy.registerCommands(new TestCommands());
        legacy.registerCommands(new UnsortedCommands());
        legacy.registerSubCommands(new ReportCommands(), "report");

        YamlConfiguration commandRemapConfig = loadDefaultMapping();
        if (commandRemapConfig != null) {
            this.commands.registerCommandsWithMapping(legacy, commandRemapConfig, 2, false);
        }

        legacy.registerSubCommands(new BuildCommands(), "build");

        StringBuilder output = new StringBuilder();
        this.commands.generatePojo("", output, 0);
        System.out.println(output);

        return this;
    }

    public YamlConfiguration loadDefaultMapping() {
        File file = new File("config" + File.separator + "commands.yaml");
        if (file.exists()) {
            return YamlConfiguration.loadConfiguration(file);
        } else return null;
    }

    public ValueStore<Object> getStore() {
        return store;
    }

    public PermissionHandler getPermisser() {
        return permisser;
    }

    public ValidatorStore getValidators() {
        return validators;
    }

    public NationPlaceholders getNationPlaceholders() {
        return nationPlaceholders;
    }
    public CommandGroup getCommands() {
        return commands;
    }

    public CommandCallable getCallable(List<String> args) {
        return commands.getCallable(args);
    }

    public Map.Entry<CommandCallable,String> getCallableAndPath(List<String> args) {
        CommandCallable root = commands;
        List<String> path = new ArrayList<>();

        Queue<String> stack = new ArrayDeque<>(args);
        while (!stack.isEmpty()) {
            String arg = stack.poll();
            path.add(arg);
            if (root instanceof CommandGroup) {
                root = ((CommandGroup) root).get(arg);
            } else {
                throw new IllegalArgumentException("Command: " + root.getPrimaryCommandId() + " of type " + root.getClass().getSimpleName() + " has no subcommand: " + arg);
            }
        }
        return new AbstractMap.SimpleEntry<>(root, StringMan.join(path, " "));
    }

    public void run(MessageReceivedEvent event) {
        run(event, true);
    }

    public void run(MessageReceivedEvent event, boolean async) {
        Guild guild = event.isFromGuild() ? event.getGuild() : null;
        DiscordChannelIO io = new DiscordChannelIO(event.getChannel(), event::getMessage);
        User user = event.getAuthor();
        String fullCmdStr = DiscordUtil.trimContent(event.getMessage().getContentRaw()).trim();
        if (fullCmdStr.startsWith(Settings.commandPrefix(false))) {
            fullCmdStr = fullCmdStr.substring(Settings.commandPrefix(false).length());
        }
        System.out.println("remove:|| full " + fullCmdStr);
        run(guild, event.getChannel(), user, event.getMessage(), io, fullCmdStr, async);
    }

    private LocalValueStore createLocals(Guild guild, MessageChannel channel, User user, Message message, IMessageIO io, Map<String, String> fullCmdStr) {
        LocalValueStore<Object> locals = new LocalValueStore<>(store);

        locals.addProvider(Key.of(PermissionHandler.class), permisser);
        locals.addProvider(Key.of(ValidatorStore.class), validators);

        locals.addProvider(Key.of(User.class, Me.class), user);
        locals.addProvider(Key.of(IMessageIO.class, Me.class), io);
        locals.addProvider(Key.of(JSONObject.class, Me.class), new JSONObject(fullCmdStr));
        if (channel != null) locals.addProvider(Key.of(MessageChannel.class, Me.class), channel);
        if (message != null) locals.addProvider(Key.of(Message.class, Me.class), message);
        if (guild != null) {
            Member member = guild.getMember(user);
            if (member != null) locals.addProvider(Key.of(Member.class, Me.class), member);
            locals.addProvider(Key.of(Guild.class, Me.class), guild);
            locals.addProvider(Key.of(GuildDB.class, Me.class), Locutus.imp().getGuildDB(guild));
        }
        return locals;
    }

    public void run(Guild guild, MessageChannel channel, User user, Message message, IMessageIO io, String fullCmdStr, boolean async) {
        if (fullCmdStr.startsWith("{")) {
            JSONObject json = new JSONObject(fullCmdStr);
            Map<String, Object> arguments = json.toMap();
            Map<String, String> stringArguments = new HashMap<>();
            for (Map.Entry<String, Object> entry : arguments.entrySet()) {
                stringArguments.put(entry.getKey(), entry.getValue().toString());
            }

            String pathStr = arguments.remove("").toString();
            run(guild, channel, user, message, io, pathStr, stringArguments, async);
            return;
        }
        Runnable task = new Runnable() {
            @Override
            public void run() {
                List<String> args = StringMan.split(fullCmdStr, ' ');
                List<String> original = new ArrayList<>(args);
                CommandCallable callable = commands.getCallable(args, true);
                if (callable == null) {
                    System.out.println("No cmd found for " + StringMan.getString(original));
                    return;
                }

                LocalValueStore<Object> locals = createLocals(guild, channel, user, message, io, Collections.emptyMap());

                if (callable instanceof ParametricCallable parametric) {

                    try {
                        ArgumentStack stack = new ArgumentStack(args, locals, validators, permisser);
                        handleCall(io, () -> {
                            Map<ParameterData, Map.Entry<String, Object>> map = parametric.parseArgumentsToMap(stack);
                            Object[] parsed = parametric.argumentMapToArray(map);

                            return parametric.call(null, locals, parsed);
                        });
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }
                } else if (callable instanceof CommandGroup group) {
                    System.out.println("Remove:|| group " + group.getPrimaryCommandId());
                    handleCall(io, group, locals);
                } else throw new IllegalArgumentException("Invalid command class " + callable.getClass());
            }
        };
        if (async) Locutus.imp().getExecutor().submit(task);
        else task.run();
    }
    public void run(Guild guild, MessageChannel channel, User user, Message message, IMessageIO io, String path, Map<String, String> arguments, boolean async) {
        Runnable task = new Runnable() {
            @Override
            public void run() {
                try {
                    CommandCallable callable = commands.get(Arrays.asList(path.split(" ")));
                    if (callable == null) {
                        System.out.println("No cmd found for " + path);
                        io.create().append("No command found for " + path).send();
                        return;
                    }

                    Map<String, String> argsAndCmd = new HashMap<>(arguments);
                    argsAndCmd.put("", path);
                    Map<String, String> finalArguments = new LinkedHashMap<>(arguments);
                    finalArguments.remove("");

                    LocalValueStore<Object> locals = createLocals(guild, channel, user, message, io, argsAndCmd);
                    if (callable instanceof ParametricCallable parametric) {
                        if (message == null) {
                            String cmdRaw = (path + " " + parametric.stringifyArgumentMap(finalArguments, " ")).trim();
                            Message embedMessage = new MessageBuilder().setContent(Settings.commandPrefix(false) + cmdRaw).build();
                            locals.addProvider(Key.of(Message.class, Me.class), embedMessage);
                        }
                        handleCall(io, () -> {
                            Object[] parsed = parametric.parseArgumentMap(finalArguments, locals, validators, permisser);
                            return parametric.call(null, locals, parsed);
                        });
                    } else if (callable instanceof CommandGroup group) {
                        handleCall(io, group, locals);
                    } else throw new IllegalArgumentException("Invalid command class " + callable.getClass());
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        };
        if (async) Locutus.imp().getExecutor().submit(task);
        else task.run();
    }

    private void handleCall(IMessageIO io, Supplier<Object> call) {
        try {
            Object result = call.get();
            if (result != null) {
                io.create().append(result.toString()).send();
            }
        } catch (CommandUsageException e) {
            e.printStackTrace();
            String title = e.getMessage();
            StringBuilder body = new StringBuilder();
            if (e.getHelp() != null) {
                body.append("`/" + e.getHelp() + "`");
            }
            if (e.getDescription() != null && !e.getDescription().isEmpty()) {
                body.append("\n" + e.getDescription());
            }
            if (title == null || title.isEmpty()) title = e.getClass().getSimpleName();

            io.create().embed(title, body.toString()).send();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            io.create().append(e.getMessage()).send();
        } catch (Throwable e) {
            Throwable root = e;
            while (root.getCause() != null) root = root.getCause();

            root.printStackTrace();
            io.create().append("Error: " + root.getMessage()).send();
        }
    }

    private void handleCall(IMessageIO io, CommandGroup group, ValueStore store) {
        handleCall(io, () -> group.call(new ArgumentStack(new ArrayList<>(), store, validators, permisser)));
    }

//    private void handleCall(IMessageIO io, ParametricCallable callable, ValueStore store, Object[] parsed) {
//        //callable.call(null, store, parsed);
//        handleCall(io, () -> callable.call(null, store, parsed));
//    }

    public static Map<String, String> parseArguments(Set<String> params, String input, boolean checkUnbound) {
        Map<String, String> lowerCase = new HashMap<>();
        for (String param : params) {
            lowerCase.put(param.toLowerCase(Locale.ROOT), param);
        }


        Map<String, String> result = new LinkedHashMap<>();
        Pattern pattern = Pattern.compile("(?i)(^| )(" + StringMan.join(lowerCase.keySet(), "|") + "): [^ ]");
        Matcher matcher = pattern.matcher(input);

        Pattern fuzzyArg = !checkUnbound ? null : Pattern.compile("(?i) ([a-zA-Z]+): [^ ]");

        Map<String, Integer> argStart = new LinkedHashMap<>();
        Map<String, Integer> argEnd = new LinkedHashMap<>();
        String lastArg = null;
        while (matcher.find()) {
            String argName = matcher.group(2).toLowerCase(Locale.ROOT);
            int index = matcher.end(2) + 2;
            Integer existing = argStart.put(argName, index);
            if (existing != null) throw new IllegalArgumentException("Duplicate argument `" + argName + "` in `" + input + "`");

            if (lastArg != null) {
                argEnd.put(lastArg, matcher.start(2) - 1);
            }
            lastArg = argName;
        }
        if (lastArg != null) {
            argEnd.put(lastArg, input.length());
        }

        for (Map.Entry<String, Integer> entry : argStart.entrySet()) {
            String id = entry.getKey();
            int start = entry.getValue();
            int end = argEnd.get(id);
            String value = input.substring(start, end);

            if (fuzzyArg != null) {
                Matcher valueMatcher = fuzzyArg.matcher(value);
                if (valueMatcher.find()) {
                    String fuzzyArgName = valueMatcher.group(1);
                    throw new IllegalArgumentException("Invalid argument: `" + fuzzyArgName + "` for `" + input + "` options: " + StringMan.getString(params));
                }
            }
            result.put(lowerCase.get(id), value);
        }

        if (argStart.isEmpty()) {
            throw new IllegalArgumentException("No arguments found` for `" + input + "` options: " + StringMan.getString(params));
        }

        return result;
    }

    public static void main(String[] args) {
        boolean checkUnbound = true;
        Set<String> params = new HashSet<>(Arrays.asList("hello", "world"));
        String input = "hello: test testing world: blah something";
        Map<String, String> result = parseArguments(params, input, checkUnbound);
        System.out.println("Result " + StringMan.getString(result));
    }

    public Map<String, String> validateSlashCommand(String input, boolean strict) {
        String original = input;
        CommandGroup root = commands;
        while (true) {
            int index = input.indexOf(' ');
            if (index < 0) throw new IllegalArgumentException("No parametric command found for " + original + " only found root: " + root.getFullPath());
            String arg0 = input.substring(0, index);
            input = input.substring(index + 1).trim();

            CommandCallable next = root.get(arg0);
            if (next instanceof ParametricCallable parametric) {
                if (!input.isEmpty()) {
                    return parseArguments(parametric.getUserParameterMap().keySet(), input, strict);
                }
                return new HashMap<>();
            } else if (next instanceof CommandGroup group) {
                root = group;
                continue;
            } else if (next == null) {
                throw new IllegalArgumentException("No parametric command found for " + original + " (" + arg0 + ")");
            } else {
                throw new UnsupportedOperationException("Invalid command class " + next.getClass());
            }
        }
    }
}
