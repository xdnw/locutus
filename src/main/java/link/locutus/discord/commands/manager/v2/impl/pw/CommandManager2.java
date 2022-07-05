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
import link.locutus.discord.commands.manager.v2.command.ArgumentStack;
import link.locutus.discord.commands.manager.v2.command.CommandCallable;
import link.locutus.discord.commands.manager.v2.command.CommandGroup;
import link.locutus.discord.commands.manager.v2.command.CommandUsageException;
import link.locutus.discord.commands.manager.v2.impl.discord.binding.DiscordBindings;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.PWBindings;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.PermissionBinding;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.SheetBindings;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.StockBinding;
import link.locutus.discord.commands.manager.v2.impl.pw.commands.*;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.NationPlaceholders;
import link.locutus.discord.commands.manager.v2.perm.PermissionHandler;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.web.test.TestCommands;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.AbstractMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

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

        this.commands.registerSubCommands(stock, "stock");
//        this.commands.registerCommands(stock);
        this.commands.registerCommands(new UtilityCommands());
        this.commands.registerCommands(new BankCommands());
        this.commands.registerCommands(new StatCommands());
        this.commands.registerCommands(new IACommands());
        this.commands.registerCommands(new AttackCommands());
        this.commands.registerCommands(new AdminCommands());
        this.commands.registerCommands(new DiscordCommands());
        this.commands.registerCommands(new FACommands());
        this.commands.registerCommands(new FunCommands());
        this.commands.registerCommands(new PlayerSettingCommands());
//        this.commands.registerSubCommands(new ExchangeCommands(), "exchange", "corp", "corporation");
        this.commands.registerCommands(new LoanCommands());
        this.commands.registerCommands(new TradeCommands());
        this.commands.registerCommands(new WarCommands());
        this.commands.registerCommands(new GrantCommands());

        this.commands.registerCommands(new TestCommands());
//        this.commands.registerCommands(new UnsortedCommands());

        return this;
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
        Message message = event.getMessage();
        String content = DiscordUtil.trimContent(message.getContentRaw());
        if (content.isEmpty() || content.charAt(0) != Settings.INSTANCE.DISCORD.COMMAND.COMMAND_PREFIX.charAt(0)) return;

        Runnable task = new Runnable() {
            @Override
            public void run() {
                try {
                    List<String> args = StringMan.split(content.substring(1), ' ');
                    String arg0 = args.get(0);
                    CommandCallable cmd = commands.get(arg0);
                    if (cmd == null) {
                        System.out.println("No cmd found for " + arg0);
                        return;
                    }

                    LocalValueStore<Object> locals = new LocalValueStore<>(store);

                    // Discord locals
                    locals.addProvider(Key.of(User.class, Me.class), event.getAuthor());
                    locals.addProvider(Key.of(Member.class, Me.class), event.getMember());
                    locals.addProvider(Key.of(MessageChannel.class, Me.class), event.getChannel());
                    locals.addProvider(Key.of(Message.class, Me.class), event.getMessage());
                    if (event.isFromGuild()) {
                        locals.addProvider(Guild.class, event.getGuild()); // TODO remove
                        locals.addProvider(Key.of(Guild.class, Me.class), event.getGuild());
                        locals.addProvider(Key.of(GuildDB.class, Me.class), Locutus.imp().getGuildDB(event.getGuild()));
                    }
                    locals.addProvider(MessageReceivedEvent.class, event);

                    ArgumentStack stack = new ArgumentStack(args, locals, validators, permisser);
                    locals.addProvider(ArgumentStack.class, stack);

                    MessageChannel channel = event.getChannel();

                    try {
                        Object result = commands.call(stack);
                        if (result != null) {
                            DiscordUtil.sendMessage(channel, result.toString());
                        }
                    } catch (CommandUsageException e) {
                        e.printStackTrace();
                        String title = e.getMessage();
                        StringBuilder body = new StringBuilder();
                        if (e.getHelp() != null) {
                            body.append("`" + Settings.INSTANCE.DISCORD.COMMAND.COMMAND_PREFIX + e.getHelp() + "`");
                        }
                        if (e.getDescription() != null && !e.getDescription().isEmpty()) {
                            body.append("\n" + e.getDescription());
                        }
                        if (title == null || title.isEmpty()) title = e.getClass().getSimpleName();
                        DiscordUtil.createEmbedCommand(event.getChannel(), title, body.toString());
                    } catch (IllegalArgumentException e) {
                        e.printStackTrace();
                        RateLimitUtil.queue(channel.sendMessage(e.getMessage()));
                    } catch (Throwable e) {
                        Throwable root = e;
                        while (e.getCause() != null) e = e.getCause();

                        e.printStackTrace();
                        RateLimitUtil.queue(channel.sendMessage("Error: " + e.getMessage()));
                    }
                } catch ( Throwable e) {
                    e.printStackTrace();
                }
            }
        };
        if (async) {
            Locutus.imp().getExecutor().submit(task);
        } else {
            task.run();
        }

    }
}
