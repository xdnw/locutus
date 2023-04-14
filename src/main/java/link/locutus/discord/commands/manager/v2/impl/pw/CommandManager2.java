package link.locutus.discord.commands.manager.v2.impl.pw;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.LocalValueStore;
import link.locutus.discord.commands.manager.v2.binding.SimpleValueStore;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.bindings.PrimitiveBindings;
import link.locutus.discord.commands.manager.v2.binding.bindings.PrimitiveValidators;
import link.locutus.discord.commands.manager.v2.binding.validator.ValidatorStore;
import link.locutus.discord.commands.manager.v2.command.*;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.commands.manager.v2.impl.discord.binding.DiscordBindings;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.PWBindings;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.PermissionBinding;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.SheetBindings;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.StockBinding;
import link.locutus.discord.commands.manager.v2.impl.pw.commands.*;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.AlliancePlaceholders;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.NationPlaceholders;
import link.locutus.discord.commands.manager.v2.perm.PermissionHandler;
import link.locutus.discord.config.Settings;
import link.locutus.discord.config.yaml.file.YamlConfiguration;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.web.test.TestCommands;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.json.JSONObject;

import javax.annotation.Nullable;
import java.io.File;
import java.util.*;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CommandManager2 {
    private final CommandGroup commands;
    private final ValueStore<Object> store;
    private final ValidatorStore validators;
    private final PermissionHandler permisser;
    private final NationPlaceholders nationPlaceholders;
    private final AlliancePlaceholders alliancePlaceholders;

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
        this.alliancePlaceholders = new AlliancePlaceholders(store, validators, permisser);

        this.commands = CommandGroup.createRoot(store, validators);
    }

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
            if (existing != null)
                throw new IllegalArgumentException("Duplicate argument `" + argName + "` in `" + input + "`");

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
        String input = "hello: test testing world: blah something.";
        Map<String, String> result = parseArguments(params, input, checkUnbound);
        System.out.println("Result " + StringMan.getString(result));
    }

    public CommandManager2 registerDefaults() {
        this.commands.registerCommandsWithMapping(CM.class, false, false);
        this.commands.registerMethod(new TestCommands(), List.of("test"), "embed", "embed");

                this.commands.registerMethod(new BankCommands(), List.of("tax"), "taxInfo", "info");
//        this.commands.registerSubCommands(new BuildCommands(), "build");
//        this.commands.registerMethod(new StatCommands(), List.of("stats_other"), "radiationByTurn", null);
//
//        this.commands.registerMethod(new WarCommands(), List.of("sheets_milcom"), "convertTKRSpySheet", "convertTKRSpySheet");
//        this.commands.registerMethod(new WarCommands(), List.of("spy", "sheet"), "convertTKRSpySheet", "convertTKR");
//
//        this.commands.registerMethod(new AdminCommands(), List.of("admin", "sync"), "syncLootFromAttacks", "syncLootFromAttacks");
//        this.commands.registerMethod(new AdminCommands(), List.of("admin", "sync"), "syncTreasures", "treasures");
//        this.commands.registerMethod(new AdminCommands(), List.of("admin", "sync"), "syncDiscordWithLocutus", "discord");
//
//        this.commands.registerMethod(new TradeCommands(), List.of("alerts", "trade"), "tradeAlertAbsolute", "price");
//        this.commands.registerMethod(new TradeCommands(), List.of("alerts", "trade"), "tradeAlertMistrade", "mistrade");
//        this.commands.registerMethod(new TradeCommands(), List.of("alerts", "trade"), "tradeAlertDisparity", "margin");
//        this.commands.registerMethod(new TradeCommands(), List.of("alerts", "trade"), "tradeAlertNoOffer", "no_offers");
//        this.commands.registerMethod(new TradeCommands(), List.of("alerts", "trade"), "tradeAlertUndercut", "undercut");
//        this.commands.registerMethod(new AdminCommands(), List.of("admin"), "removeInvalidOffshoring", "removeinvalidoffshoring");
//        this.commands.registerMethod(new AdminCommands(), List.of("admin"), "leaveServer", "leaveServer");
//        this.commands.registerMethod(new UtilityCommands(), List.of("color"), "calculateColorRevenue", "revenue");
//
//        this.commands.registerMethod(new UnsortedCommands(), List.of("sheets_econ"), "taxRevenueSheet", "taxRevenue");
//
//        this.commands.registerMethod(new TradeCommands(), List.of("trade", "offer"), "sellOffer", "sell");
//        this.commands.registerMethod(new TradeCommands(), List.of("trade", "offer"), "buyOffer", "buy");
//        this.commands.registerMethod(new TradeCommands(), List.of("trade", "offer"), "deleteOffer", "delete");
//        this.commands.registerMethod(new TradeCommands(), List.of("trade", "offer"), "offerInfo", "info");
//        this.commands.registerMethod(new TradeCommands(), List.of("trade", "offer"), "updateOffer", "update");
//
//        this.commands.registerMethod(new TradeCommands(), List.of("trade", "offer"), "buyList", "buy_list");
//        this.commands.registerMethod(new TradeCommands(), List.of("trade", "offer"), "sellList", "sell_list");
//        this.commands.registerMethod(new TradeCommands(), List.of("trade", "offer"), "myOffers", "my_offers");

        this.commands.registerMethod(new AdminCommands(), List.of("admin"), "loginTimes", "list_login_times");

        this.commands.registerMethod(new StatCommands(), List.of("stats_war"), "warAttacksByDay", "warattacksbyday");
        this.commands.registerMethod(new FunCommands(), List.of("fun"), "stealBorgsCity", "stealborgscity");

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

    public AlliancePlaceholders getAlliancePlaceholders() {
        return alliancePlaceholders;
    }

    public CommandGroup getCommands() {
        return commands;
    }

    public CommandCallable getCallable(List<String> args) {
        return commands.getCallable(args);
    }

    public Map.Entry<CommandCallable, String> getCallableAndPath(List<String> args) {
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

    private LocalValueStore createLocals(@Nullable LocalValueStore<Object> existingLocals, @Nullable Guild guild, @Nullable MessageChannel channel, @Nullable User user, @Nullable Message message, IMessageIO io, @Nullable Map<String, String> fullCmdStr) {
        if (guild != null && Settings.INSTANCE.MODERATION.BANNED_GUILDS.contains(guild.getIdLong()))
            throw new IllegalArgumentException("Unsupported");

        LocalValueStore<Object> locals = existingLocals == null ? new LocalValueStore<>(store) : existingLocals;

        locals.addProvider(Key.of(PermissionHandler.class), permisser);
        locals.addProvider(Key.of(ValidatorStore.class), validators);

        if (user != null) {
            if (Settings.INSTANCE.MODERATION.BANNED_USERS.contains(user.getIdLong()))
                throw new IllegalArgumentException("Unsupported");
            DBNation nation = DiscordUtil.getNation(user);
            if (nation != null) {
                if (Settings.INSTANCE.MODERATION.BANNED_NATIONS.contains(nation.getId())
                        || Settings.INSTANCE.MODERATION.BANNED_ALLIANCES.contains(nation.getAlliance_id())) {
                    throw new IllegalArgumentException("Unsupported");
                }
            }
            locals.addProvider(Key.of(User.class, Me.class), user);
        }

        locals.addProvider(Key.of(IMessageIO.class, Me.class), io);
        if (fullCmdStr != null) {
            locals.addProvider(Key.of(JSONObject.class, Me.class), new JSONObject(fullCmdStr));
        }
        if (channel != null) locals.addProvider(Key.of(MessageChannel.class, Me.class), channel);
        if (message != null) locals.addProvider(Key.of(Message.class, Me.class), message);
        if (guild != null) {
            if (user != null) {
                Member member = guild.getMember(user);
                if (member != null) locals.addProvider(Key.of(Member.class, Me.class), member);
            }
            locals.addProvider(Key.of(Guild.class, Me.class), guild);
            GuildDB db = Locutus.imp().getGuildDB(guild);
            if (db != null) {
                for (int id : db.getAllianceIds(true)) {
                    if (Settings.INSTANCE.MODERATION.BANNED_ALLIANCES.contains(id))
                        throw new IllegalArgumentException("Unsupported");
                }
            }
            locals.addProvider(Key.of(GuildDB.class, Me.class), db);
        }
        return locals;
    }

    public void run(@Nullable Guild guild, @Nullable MessageChannel channel, @Nullable User user, @Nullable Message message, IMessageIO io, String fullCmdStr, boolean async) {
        LocalValueStore existingLocals = createLocals(null, guild, channel, user, message, io, null);
        run(existingLocals, io, fullCmdStr, async);
    }

    public void run(LocalValueStore<Object> existingLocals, IMessageIO io, String fullCmdStr, boolean async) {
        Runnable task = () -> {
            if (fullCmdStr.startsWith("{")) {
                JSONObject json = new JSONObject(fullCmdStr);
                Map<String, Object> arguments = json.toMap();
                Map<String, String> stringArguments = new HashMap<>();
                for (Map.Entry<String, Object> entry : arguments.entrySet()) {
                    stringArguments.put(entry.getKey(), entry.getValue().toString());
                }

                String pathStr = arguments.remove("").toString();
                run(existingLocals, io, pathStr, stringArguments, async);
                return;
            }
            List<String> args = StringMan.split(fullCmdStr, ' ');
            List<String> original = new ArrayList<>(args);
            CommandCallable callable = commands.getCallable(args, true);
            if (callable == null) {
                System.out.println("No cmd found for " + StringMan.getString(original));
                return;
            }

            LocalValueStore locals = createLocals(existingLocals, null, null, null, null, io, null);

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
                handleCall(io, group, locals);
            } else throw new IllegalArgumentException("Invalid command class " + callable.getClass());
        };
        if (async) Locutus.imp().getExecutor().submit(task);
        else task.run();
    }

    public void run(@Nullable Guild guild, @Nullable MessageChannel channel, @Nullable User user, @Nullable Message message, IMessageIO io, String path, Map<String, String> arguments, boolean async) {
        LocalValueStore existingLocals = createLocals(null, guild, channel, user, message, io, null);
        run(existingLocals, io, path, arguments, async);
    }

    public void run(LocalValueStore<Object> existingLocals, IMessageIO io, String path, Map<String, String> arguments, boolean async) {
        Runnable task = () -> {
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

                LocalValueStore<Object> locals = createLocals(existingLocals, null, null, null, null, io, argsAndCmd);
                if (callable instanceof ParametricCallable parametric) {
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
                body.append("`/").append(e.getHelp()).append("`");
            }
            if (e.getDescription() != null && !e.getDescription().isEmpty()) {
                body.append("\n").append(e.getDescription());
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

    public Map<String, String> validateSlashCommand(String input, boolean strict) {
        String original = input;
        CommandGroup root = commands;
        while (true) {
            int index = input.indexOf(' ');
            if (index < 0)
                throw new IllegalArgumentException("No parametric command found for " + original + " only found root: " + root.getFullPath());
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
            } else if (next == null) {
                throw new IllegalArgumentException("No parametric command found for " + original + " (" + arg0 + ")");
            } else {
                throw new UnsupportedOperationException("Invalid command class " + next.getClass());
            }
        }
    }
}
