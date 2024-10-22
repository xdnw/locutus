package link.locutus.discord;

import com.google.common.eventbus.AsyncEventBus;
import link.locutus.discord._main.*;
import link.locutus.discord.apiv2.PoliticsAndWarV2;
import link.locutus.discord.apiv3.csv.DataDumpParser;
import link.locutus.discord.apiv3.PoliticsAndWarV3;
import link.locutus.discord.apiv3.subscription.PnwPusherShardManager;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandManager;
import link.locutus.discord.commands.manager.Noformat;
import link.locutus.discord.commands.manager.v2.command.CommandBehavior;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.command.IModalBuilder;
import link.locutus.discord.commands.manager.v2.impl.SlashCommandManager;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordHookIO;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.NationPlaceholders;
import link.locutus.discord.commands.stock.StockDB;
import link.locutus.discord.config.Settings;
import link.locutus.discord.config.yaml.Config;
import link.locutus.discord.db.*;
import link.locutus.discord.db.conflict.ConflictManager;
import link.locutus.discord.db.entities.metric.AllianceMetric;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DiscordBan;
import link.locutus.discord.db.entities.DiscordMeta;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.db.handlers.GuildCustomMessageHandler;
import link.locutus.discord.event.Event;
import link.locutus.discord.event.game.TurnChangeEvent;
import link.locutus.discord.pnw.PNWUser;
import link.locutus.discord.util.*;
import link.locutus.discord.util.scheduler.CaughtRunnable;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.discord.GuildShardManager;
import link.locutus.discord.util.math.ArrayUtil;
import link.locutus.discord.util.offshore.Auth;
import link.locutus.discord.util.offshore.OffshoreInstance;
import link.locutus.discord.util.scheduler.CaughtTask;
import link.locutus.discord.util.scheduler.ThrowingConsumer;
import link.locutus.discord.util.scheduler.ThrowingFunction;
import link.locutus.discord.util.task.ia.MapFullTask;
import link.locutus.discord.util.task.mail.AlertMailTask;
import link.locutus.discord.util.task.roles.AutoRoleInfo;
import link.locutus.discord.util.trade.TradeManager;
import link.locutus.discord.util.update.*;
import com.google.common.eventbus.EventBus;
import link.locutus.discord.web.jooby.WebRoot;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.SelfUser;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.emoji.EmojiUnion;
import net.dv8tion.jda.api.events.guild.GuildAvailableEvent;
import net.dv8tion.jda.api.events.guild.GuildBanEvent;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.events.guild.invite.GuildInviteCreateEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.ItemComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.modals.ModalInteraction;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public final class Locutus extends ListenerAdapter {
    private static Locutus INSTANCE;
    private final RepeatingTasks taskTrack;
    private ILoader loader;

    private final ThreadPoolExecutor executor;
    private final ScheduledThreadPoolExecutor scheduler;

    private final EventBus eventBus;

    private final GuildShardManager manager = new GuildShardManager();
    private GuildCustomMessageHandler messageHandler;
    private DataDumpParser dataDumpParser;

    private final Map<Long, GuildDB> guildDatabases = new ConcurrentHashMap<>();
    private final Object dataDumpParserLock = new Object();

    private PnwPusherShardManager pusher;
    private Guild server;

    public static synchronized Locutus create() {
        if (INSTANCE != null) throw new IllegalStateException("Already initialized");
        try {
            return new Locutus();
        } catch (SQLException | ClassNotFoundException | LoginException | InterruptedException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    private Locutus() throws SQLException, ClassNotFoundException, LoginException, InterruptedException, NoSuchMethodException {
        Logg.setInfoLogging();
        if (INSTANCE != null) throw new IllegalStateException("Already running.");
        INSTANCE = this;
        long start = System.currentTimeMillis();
        this.executor = new ThreadPoolExecutor(0, 256, 60L, TimeUnit.SECONDS, new SynchronousQueue<>(), new ThreadPoolExecutor.CallerRunsPolicy());
        this.scheduler = new ScheduledThreadPoolExecutor(256, Executors.defaultThreadFactory(), new ThreadPoolExecutor.CallerRunsPolicy());
        this.taskTrack = new RepeatingTasks(scheduler);
        Logg.text("Created Executors (" + (((-start)) + (start = System.currentTimeMillis())) + "ms)");

        if (Settings.INSTANCE.ROOT_SERVER <= 0) throw new IllegalStateException("Please set ROOT_SERVER in " + Settings.INSTANCE.getDefaultFile());
        if (Settings.INSTANCE.ROOT_COALITION_SERVER <= 0) Settings.INSTANCE.ROOT_COALITION_SERVER = Settings.INSTANCE.ROOT_SERVER;
        if (Settings.commandPrefix(false).length() != 1) throw new IllegalStateException("COMMAND_PREFIX must be 1 character in " + Settings.INSTANCE.getDefaultFile());
        if (Settings.commandPrefix(true).length() != 1) throw new IllegalStateException("LEGACY_COMMAND_PREFIX must be 1 character in " + Settings.INSTANCE.getDefaultFile());
        if (Settings.commandPrefix(true).equalsIgnoreCase(Settings.commandPrefix(false))) {
            throw new IllegalStateException("LEGACY_COMMAND_PREFIX cannot equal COMMAND_PREFIX in " + Settings.INSTANCE.getDefaultFile());
        }
        if (Settings.commandPrefix(false).matches("[._~]")) {
            throw new IllegalStateException("COMMAND_PREFIX cannot be `.` or `_` or `~` in " + Settings.INSTANCE.getDefaultFile());
        }
        if (Settings.commandPrefix(true).matches("[._~]")) {
            throw new IllegalStateException("LEGACY_COMMAND_PREFIX cannot be `.` or `_` or `~` in " + Settings.INSTANCE.getDefaultFile());
        }
        if (Settings.INSTANCE.BOT_TOKEN.isEmpty()) {
            throw new IllegalStateException("Please set BOT_TOKEN in " + Settings.INSTANCE.getDefaultFile());
        }
        if (Settings.INSTANCE.API_KEY_PRIMARY.isEmpty()) {
            if (Settings.INSTANCE.USERNAME.isEmpty() || Settings.INSTANCE.PASSWORD.isEmpty()) {
                throw new IllegalStateException("Please set API_KEY_PRIMARY or USERNAME/PASSWORD in " + Settings.INSTANCE.getDefaultFile());
            }
        }

        this.eventBus = new AsyncEventBus("locutus", Runnable::run);
        Logg.text("Created Event Bus (" + (((-start)) + (start = System.currentTimeMillis())) + "ms");
        this.loader = new PreLoader(this, executor, scheduler);
        this.loader.initialize();
        Logg.text("Created and initialized Module Loader (" + (((-start)) + (start = System.currentTimeMillis())) + "ms)");
    }

    public static void post(Object event) {
        imp().eventBus.post(event);
    }

    public static ILoader loader() {
        return imp().loader;
    }

    public void registerEvents() {
        long start = System.currentTimeMillis();
        eventBus.register(new TreatyUpdateProcessor());
        Logg.text("Registered Treaty Listener (" + (((-start)) + (start = System.currentTimeMillis())) + "ms)");
        eventBus.register(new NationUpdateProcessor());
        Logg.text("Registered Nation Listener (" + (((-start)) + (start = System.currentTimeMillis())) + "ms)");
        eventBus.register(new TradeListener());
        Logg.text("Registered Trade Listener (" + (((-start)) + (start = System.currentTimeMillis())) + "ms)");
        eventBus.register(new CityUpdateProcessor());
        Logg.text("Registered City Listener (" + (((-start)) + (start = System.currentTimeMillis())) + "ms)");
        eventBus.register(new BankUpdateProcessor());
        Logg.text("Registered Bank Listener (" + (((-start)) + (start = System.currentTimeMillis())) + "ms)");
        eventBus.register(new WarUpdateProcessor());
        Logg.text("Registered War Listener (" + (((-start)) + (start = System.currentTimeMillis())) + "ms)");
        eventBus.register(new AllianceListener());
        Logg.text("Registered Alliance Listener (" + (((-start)) + (start = System.currentTimeMillis())) + "ms)");
        CommandManager cmdManager = loader.getCommandManager();
        eventBus.register(new MailListener(cmdManager.getV2().getStore(), cmdManager.getV2().getValidators(), cmdManager.getV2().getPermisser()));
        Logg.text("Registered Mail Listener (" + (((-start)) + (start = System.currentTimeMillis())) + "ms)");
        WarDB warDb = loader.getWarDB();
        ConflictManager conflictManager = warDb == null ? null : warDb.getConflicts();
        if (conflictManager != null) eventBus.register(conflictManager);
        Logg.text("Registered Conflict Manager (" + (((-start)) + (start = System.currentTimeMillis())) + "ms)");
    }

    public EventBus getEventBus() {
        return eventBus;
    }

    public GuildShardManager getDiscordApi() {
        return manager;
    }

    public Locutus start() throws InterruptedException, LoginException, SQLException, ClassNotFoundException {
        long start = System.currentTimeMillis();
        if (loader.getApiKey().isEmpty()) {
            throw new IllegalStateException("Please set API_KEY_PRIMARY or USERNAME/PASSWORD in " + Settings.INSTANCE.getDefaultFile());
        }
        if (loader.getNationId() <= 0) {
            throw new IllegalStateException("Could not get NATION_ID from key. Please ensure a valid API_KEY is set in " + Settings.INSTANCE.getDefaultFile());
        }
        if (Settings.INSTANCE.ENABLED_COMPONENTS.EVENTS) {
            this.registerEvents();
        }
        Logg.text("Registered event listener (" + (((-start)) + (start = System.currentTimeMillis())) + "ms)");
        if (Settings.INSTANCE.ENABLED_COMPONENTS.DISCORD_BOT) {
            JDA jda = loader.getJda();
            try {
                SlashCommandManager slashCommands = loader.getSlashCommandManager();
                if (slashCommands != null) {
                    executor.submit(() -> slashCommands.registerCommandData(jda));
                }
            } catch (Throwable e) {
                // sometimes happen when discord api is spotty / timeout
                e.printStackTrace();
                Logg.text("Failed to update slash commands: " + e.getMessage());
            }
            setSelfUser(jda);
            manager.put(jda);
            jda.awaitStatus(JDA.Status.LOADING_SUBSYSTEMS);
            Logg.text("Discord Gateway: " + jda.getStatus() + " (" + (((-start)) + (start = System.currentTimeMillis())) + "ms)");
            jda.awaitReady();
            Logg.text("Discord Gateway: " + jda.getStatus() + " (" + (((-start)) + (start = System.currentTimeMillis())) + "ms)");
            setSelfUser(jda);
            if (Settings.INSTANCE.ENABLED_COMPONENTS.CREATE_DATABASES_ON_STARTUP) {
                initDBPartial(true);
            }
            Logg.text("Initialized Guild Databases " + jda.getStatus() + " (" + (((-start)) + (start = System.currentTimeMillis())) + "ms)");
            Guild rootGuild = manager.getGuildById(Settings.INSTANCE.ROOT_SERVER);
            if (rootGuild != null) {
                this.server = rootGuild;
            } else {
                throw new IllegalStateException("Invalid guild: " + Settings.INSTANCE.ROOT_SERVER + " as `root-server` in " + Settings.INSTANCE.getDefaultFile().getAbsolutePath());
            }
            if (Settings.INSTANCE.ENABLED_COMPONENTS.REPEATING_TASKS) {
                initRepeatingTasks();
                Logg.text("Initialized API fetching tasks (" + (((-start)) + (start = System.currentTimeMillis())) + "ms)");
            }
            {
                List<GuildDB> queue = new ArrayList<>(guildDatabases.values());
                // sort by GuildDB.getLastModified (highest first)
                queue.sort(Comparator.comparingLong(GuildDB::getLastModified).reversed());

                AtomicInteger index = new AtomicInteger(0);
                Runnable[] queueFunc = new Runnable[1];
                queueFunc[0] = new Runnable() {
                    @Override
                    public void run() {
                        GuildDB db = queue.size() > index.get() ? queue.get(index.getAndIncrement()) : null;
                        if (db == null || !db.getGuild().isLoaded()) {
                            Logg.text("Done loading all guild members");
                            return;
                        }
                        Guild guild = db.getGuild();
                        Logg.text("Loading members for " + guild);
                        guild.loadMembers().onSuccess(f -> {
                            Logg.text("Loaded " + f.size() + " members for " + guild);
                            queueFunc[0].run();
                        }).onError(f -> {
                            Logg.text("Failed to load members for " + guild);
                            queueFunc[0].run();
                        });
                    }
                };
                queueFunc[0].run();
            }
        }

        if (Settings.INSTANCE.ENABLED_COMPONENTS.WEB && Settings.INSTANCE.WEB.PORT > 0) {
            try {
                new WebRoot(Settings.INSTANCE.WEB.PORT, Settings.INSTANCE.WEB.ENABLE_SSL);
            } catch (Throwable e) {
                e.printStackTrace();
            }
            Logg.text("Initialized Web Interface (" + (((-start)) + (start = System.currentTimeMillis())) + "ms)");
        }
        if (Settings.INSTANCE.ENABLED_COMPONENTS.REPEATING_TASKS && Settings.INSTANCE.ENABLED_COMPONENTS.SUBSCRIPTIONS) {
            this.pusher = new PnwPusherShardManager();
            executor.submit(new Runnable() {
                @Override
                public void run() {
                    Logg.text("Loading pusher");
                    pusher.load();
                    Logg.text("Loaded pusher");
                    pusher.subscribeDefaultEvents();
                    Logg.text("Subscribed to default events");
                }
            });
        }

        if (Settings.INSTANCE.TASKS.CUSTOM_MESSAGE_HANDLER) {
            this.messageHandler = new GuildCustomMessageHandler();
            // run every 5m
            taskTrack.addTask("Recruit Message Handler", () -> {
                messageHandler.run();
            }, 5 * 60, TimeUnit.SECONDS);
            Logg.text("Setup message handler (" + (((-start)) + (start = System.currentTimeMillis())) + "ms)");
        }
        Logg.text("Waiting for startup tasks to resolve...");
        try {
            loader.resolveFully(TimeUnit.MINUTES.toMillis(15));
        } catch (Throwable e) {
            e.printStackTrace();
            throw new IllegalStateException("Failed to start locutus in 15 minutes.\n\n" + loader.printStacktrace());
        }
        Logg.text("Resolved all startup tasks");
        return this;
    }

    @Override
    public void onGuildReady(@NotNull GuildReadyEvent event) {
        manager.put(event.getGuild().getIdLong(), event.getJDA());
    }

    private void setSelfUser(JDA jda) {
        SelfUser self = jda.getSelfUser();
        if (self != null) {
            long appId = self.getApplicationIdLong();
            if (appId > 0) {
                Settings.INSTANCE.APPLICATION_ID = appId;
            }
        }
    }

    public GuildDB getRootCoalitionServer() {
        GuildDB locutusStats = Locutus.imp().getGuildDB(Settings.INSTANCE.ROOT_COALITION_SERVER);
        return locutusStats;
    }

    public GuildDB getRootDb() {
        return getGuildDB(getServer());
    }

    public OffshoreInstance getRootBank() {
        DBAlliance aa = DBAlliance.get(Settings.INSTANCE.ALLIANCE_ID());
        if (aa == null) return null;
        return aa.getBank();
    }

    public Auth getRootAuth() {
        Auth auth = getNationDB().getNationById(loader.getNationId()).getAuth(true);
        if (auth != null) auth.setApiKey(loader.getApiKey());
        return auth;
    }

    public GuildDB getGuildDB(MessageReceivedEvent event) {
        return getGuildDB(event.isFromGuild() ? event.getGuild().getIdLong() : Settings.INSTANCE.ROOT_SERVER);
    }

    public Map<Long, GuildDB> getGuildDatabases() {
        return initGuildDB();
    }

    public GuildDB getGuildDB(Guild guild) {
        if (guild == null) return null;
        synchronized (guildDatabases) {
            GuildDB db = guildDatabases.get(guild.getIdLong());
            if (db != null) return db;

            try {
                db = new GuildDB(guild);
                guildDatabases.put(guild.getIdLong(), db);
                return db;
            } catch (Throwable e) {
                Logg.text("Critical error creating GuildDB for " + guild);
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
    }

    public GuildDB getGuildDB(long guildId) {
        GuildDB db = guildDatabases.get(guildId);
        if (db != null) {
            return db;
        }
        Guild guild = manager.getGuildById(guildId);
        return getGuildDB(guild);
    }

    private void initDBPartial(boolean deleteTimeLocks) {
        if (deleteTimeLocks) {
            TimeUtil.deleteOldTimeLocks();
        }
        synchronized (guildDatabases) {
            for (Guild guild : manager.getGuilds()) {
                getGuildDB(guild.getIdLong());
            }
        }
    }

    private Map<Long, GuildDB> initGuildDB() {
        if (guildDatabases.isEmpty()) return guildDatabases;
        synchronized (guildDatabases) {
            if (guildDatabases.isEmpty()) {
                initDBPartial(false);
            }
            return guildDatabases;
        }
    }

    public GuildDB getGuildDBByAA(int allianceId) {
        if (allianceId == 0) return null;
        for (Map.Entry<Long, GuildDB> entry : initGuildDB().entrySet()) {
            GuildDB db = entry.getValue();
            Set<Integer> aaIds = GuildKey.ALLIANCE_ID.getOrNull(db, false);
            if (aaIds != null && aaIds.contains(allianceId)) {
                return db;
            }
        }
        return null;
    }

    public static CommandManager cmd() {
        return imp().getCommandManager();
    }

    public SlashCommandManager getSlashCommands() {
        return loader.getSlashCommandManager();
    }

    public static Locutus imp() {
        return INSTANCE;
    }

    public static void main(String[] args) throws InterruptedException, LoginException, SQLException, ClassNotFoundException {
        Settings.INSTANCE.reload(Settings.INSTANCE.getDefaultFile());
        // load settings
        Locutus instance = Locutus.create().start();
        Settings.INSTANCE.save(Settings.INSTANCE.getDefaultFile());
    }
    public WarDB getWarDb() {
        return loader.getWarDB();
    }

    public BaseballDB getBaseballDB() {
        return loader.getBaseballDB();
    }

    public TradeManager getTradeManager() {
        return loader.getTradeManager();
    }

    public CommandManager getCommandManager() {
        return loader.getCommandManager();
    }

    public void autoRole(DBNation nation) {
        PNWUser user = Locutus.imp().getDiscordDB().getUserFromNationId(nation.getNation_id());
        if (user != null) {
            User discordUser = manager.getUserById(user.getDiscordId());
            if (discordUser != null) {
                List<Guild> guilds = discordUser.getMutualGuilds();
                for (Guild guild : guilds) {
                    GuildDB db = getGuildDB(guild);
                    Member member = guild.getMember(discordUser);
                    if (member != null) {
                        try {
                            AutoRoleInfo task = db.getAutoRoleTask().autoRole(member, nation);
                            task.execute();
                        } catch (Throwable e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }

    public StockDB getStockDB() {
        return loader.getStockDB();
    }

    public ForumDB getForumDb() {
        return loader.getForumDB();
    }

    public void runEventsAsync(ThrowingConsumer<Consumer<Event>> eventHandler) {
        ArrayDeque<Event> events = new ArrayDeque<>(0);
        eventHandler.accept(events::add);
        runEventsAsync(events);
    }

    public <T> T returnEventsAsync(ThrowingFunction<Consumer<Event>, T> eventHandler) {
        Collection<Event> events = new ArrayDeque<>(0);
        T result = eventHandler.apply(events::add);
        runEventsAsync(events);
        return result;
    }

    public void runEventsAsync(Collection<Event> events) {
        if (events.isEmpty()) return;
        getExecutor().submit(new CaughtRunnable() {
            @Override
            public void runUnsafe() {
                for (Event event : events) event.post();
            }
        });
    }

    public PnwPusherShardManager getPusher() {
        return pusher;
    }

    public void initRepeatingTasks() {
        Object warUpdateLock = new Object();
        if (Settings.INSTANCE.TASKS.ENABLE_TURN_TASKS) {
            AtomicLong lastTurn = new AtomicLong();
            taskTrack.addTask("Turn Change", new CaughtTask() {
                long lastTurnTmp;
                @Override
                public void runUnsafe() throws Exception {
                    long currentTurn = TimeUtil.getTurn();
                    if (currentTurn != lastTurn.getAndSet(currentTurn)) {
                        ByteBuffer lastTurnBytes = getDiscordDB().getInfo(DiscordMeta.TURN, 0);
                        long lastTurn = lastTurnBytes == null ? 0 : lastTurnBytes.getLong();

                        lastTurn = Math.max(lastTurnTmp, lastTurn);
                        lastTurnTmp = currentTurn;
                        if (currentTurn != lastTurn) {
                            runTurnTasks(lastTurn, currentTurn);
                        }
                    }
                }
            }, 5, TimeUnit.SECONDS);
            taskTrack.addTask("Activity Check", new CaughtTask() {
                @Override
                public void runUnsafe() throws Exception {
                    NationUpdateProcessor.onActivityCheck();
                }
            }, 60, TimeUnit.SECONDS);
        }
        if (Settings.USE_V2) {
            taskTrack.addTask("Update Nations V2 (No VM)", () -> {
                runEventsAsync(events -> getNationDB().updateNationsV2(false, events));
            }, Settings.INSTANCE.TASKS.COLORED_NATIONS_SECONDS, TimeUnit.SECONDS);

            taskTrack.addTask("Update Nations V2", () -> {
                runEventsAsync(events -> getNationDB().updateNationsV2(true, events));
            }, Settings.INSTANCE.TASKS.ALL_NATIONS_SECONDS, TimeUnit.SECONDS);

            taskTrack.addTask("City (V2)", () -> {
                runEventsAsync(events -> getNationDB().updateCitiesV2(events));
            }, Settings.INSTANCE.TASKS.ALL_CITIES_SECONDS, TimeUnit.SECONDS);

            taskTrack.addTask("War/Attack (V2)", () -> {
                synchronized (warUpdateLock)
                {
                    runEventsAsync(getWarDb()::updateAllWarsV2);
                    runEventsAsync(e -> getWarDb().updateAttacks(true, e, true));
                }
            }, Settings.INSTANCE.TASKS.ALL_WAR_SECONDS, TimeUnit.SECONDS);

        } else {
//            taskTrack.addTask("Nation (Active)", () -> {
//                try {
//                    runEventsAsync(events -> getNationDB().updateMostActiveNations(490, events));
//                } catch (Throwable e) {
//                    e.printStackTrace();
//                }
//            }, Settings.INSTANCE.TASKS.ACTIVE_NATION_SECONDS, TimeUnit.SECONDS);

            taskTrack.addTask("Treaty", () -> {
                runEventsAsync(getNationDB()::updateTreaties);
            }, Settings.INSTANCE.TASKS.TREATY_UPDATE_SECONDS, TimeUnit.SECONDS);

            taskTrack.addTask("Bank", () -> {
                runEventsAsync(f -> getBankDB().updateBankRecs(false, f));
            }, Settings.INSTANCE.TASKS.BANK_RECORDS_INTERVAL_SECONDS, TimeUnit.SECONDS);

            taskTrack.addTask("Nation (Recent)", () -> {
                runEventsAsync(getNationDB()::updateRecentNations);
            }, Settings.INSTANCE.TASKS.COLORED_NATIONS_SECONDS, TimeUnit.SECONDS);

            taskTrack.addTask("Nation", () -> {
                runEventsAsync(events -> getNationDB().updateAllNations(events, true));
            }, Settings.INSTANCE.TASKS.ALL_NATIONS_SECONDS, TimeUnit.SECONDS);

            taskTrack.addTask("Nation (Active)", () -> {
                getNationDB().getActive(false, true);
            }, Settings.INSTANCE.TASKS.ACTIVE_NATION_SECONDS, TimeUnit.SECONDS);

            taskTrack.addTask("Outdated Cities", () -> {
                runEventsAsync(f -> getNationDB().updateDirtyCities(false, f));
            }, Settings.INSTANCE.TASKS.OUTDATED_CITIES_SECONDS, TimeUnit.SECONDS);

            taskTrack.addTask("Outdated Cities", () -> {
                runEventsAsync(f -> getNationDB().updateAllCities(f));
            }, Settings.INSTANCE.TASKS.ALL_CITIES_SECONDS, TimeUnit.SECONDS);

            if (Settings.INSTANCE.TASKS.FETCH_SPIES_INTERVAL_SECONDS > 0) {
                SpyUpdater spyUpdate = new SpyUpdater();
                taskTrack.addTask("Spy Tracker", () -> {
                    spyUpdate.run();
                }, Settings.INSTANCE.TASKS.FETCH_SPIES_INTERVAL_SECONDS, TimeUnit.SECONDS);
            }

//            taskTrack.addTask("War/Attack", () -> {
//                try {
//                    synchronized (warUpdateLock) {
////                        runEventsAsync(f -> getWarDb().updateActiveWars(f, false));
//                        runEventsAsync(events -> getWarDb().updateAllWars(events));
//                        runEventsAsync(getWarDb()::updateAttacks);
//                    }
//                } catch (Throwable e) {
//                    e.printStackTrace();
//                }
//
//            }, Settings.INSTANCE.TASKS.ACTIVE_WAR_SECONDS, TimeUnit.SECONDS);

            taskTrack.addTask("All War/Attack", () -> {
                synchronized (warUpdateLock) {
                    runEventsAsync(getWarDb()::updateAllWars);
                    runEventsAsync(getWarDb()::updateAttacks);
                }
            }, Settings.INSTANCE.TASKS.ALL_WAR_SECONDS, TimeUnit.SECONDS);

            checkMailTasks();

            if (Settings.INSTANCE.TASKS.BOUNTY_UPDATE_SECONDS > 0) {
                taskTrack.addTask("Bounties", () -> Locutus.imp().getWarDb().updateBountiesV3(),
                        Settings.INSTANCE.TASKS.BOUNTY_UPDATE_SECONDS, TimeUnit.SECONDS);
            }
            if (Settings.INSTANCE.TASKS.TREASURE_UPDATE_SECONDS > 0) {
                taskTrack.addTask("Treasure", () -> {
                    runEventsAsync(Locutus.imp().getNationDB()::updateTreasures);
                }, Settings.INSTANCE.TASKS.TREASURE_UPDATE_SECONDS, TimeUnit.SECONDS);
            }

            if (Settings.INSTANCE.TASKS.BASEBALL_SECONDS > 0) {
                taskTrack.addTask("Baseball", () -> {
                    runEventsAsync(getBaseballDB()::updateGames);
                }, Settings.INSTANCE.TASKS.BASEBALL_SECONDS, TimeUnit.SECONDS);
            }

            if (Settings.INSTANCE.TASKS.COMPLETED_TRADES_SECONDS > 0) {
                taskTrack.addTask("Trade", () -> {
                            runEventsAsync(getTradeManager()::updateTradeList);
                        },
                        Settings.INSTANCE.TASKS.COMPLETED_TRADES_SECONDS, TimeUnit.SECONDS);
            }

            if (Settings.INSTANCE.TASKS.NATION_DISCORD_SECONDS > 0) {
                taskTrack.addTask("Discord User Id", () -> {
                            Locutus.imp().getDiscordDB().updateUserIdsSince(Settings.INSTANCE.TASKS.NATION_DISCORD_SECONDS, false);
                        },
                        Settings.INSTANCE.TASKS.NATION_DISCORD_SECONDS, TimeUnit.SECONDS);
            }
        }

        if (Settings.INSTANCE.TASKS.BEIGE_REMINDER_SECONDS > 0) {
            LeavingBeigeAlert beigeAlerter = new LeavingBeigeAlert();

            taskTrack.addTask("Beige Alert", beigeAlerter::run, Settings.INSTANCE.TASKS.BEIGE_REMINDER_SECONDS, TimeUnit.SECONDS);
        }

        if (getForumDb() != null && Settings.INSTANCE.TASKS.FORUM_UPDATE_INTERVAL_SECONDS > 0) {
            taskTrack.addTask("Forum", getForumDb()::update, Settings.INSTANCE.TASKS.FORUM_UPDATE_INTERVAL_SECONDS, TimeUnit.SECONDS);
        }
    }

    private void runTurnTasks(long lastTurn, long currentTurn) {
        try {
            new TurnChangeEvent(lastTurn, currentTurn).post();

            for (DBNation nation : getNationDB().getNationsByAlliance().values()) {
                nation.processTurnChange(lastTurn, currentTurn, Event::post);
            }

            if (Settings.INSTANCE.TASKS.TURN_TASKS.MAP_FULL_ALERT) {
                // See also  the war update processor has some commented out code for MAPS (near the audit stuff)
                new MapFullTask().run();
            }

            {
                // Update all nations

                {
                    runEventsAsync(events -> getNationDB().updateAllNations(events, true));
                }
                {
                    runEventsAsync(events -> getNationDB().updateAlliances(null, events));
                }

                runEventsAsync(getNationDB()::deleteExpiredTreaties);
                runEventsAsync(getNationDB()::updateTreaties);
                try {
                    runEventsAsync(getNationDB()::updateBans);
                } catch (Throwable e) {
                    e.printStackTrace();
                }

                getNationDB().saveAllCities(); // TODO save all cities

                getTradeManager().updateColorBlocs(); // TODO move to configurable task
            }

            if (Settings.INSTANCE.TASKS.TURN_TASKS.ALLIANCE_METRICS) {
                AllianceMetric.update(80);
            }
        } finally {
            getDiscordDB().setInfo(DiscordMeta.TURN, 0, ArrayUtil.longToBytes(currentTurn));
        }
    }

    private void checkMailTasks() {
        Config.ConfigBlock<Settings.TASKS.MAIL> tasks = Settings.INSTANCE.TASKS.MAIL;
        for (String section : tasks.getSections()) {
            Settings.TASKS.MAIL task = tasks.get(section);
            int nationId = task.NATION_ID;
            int interval = task.FETCH_INTERVAL_SECONDS;
            long channelId = task.CHANNEL_ID;

            if (nationId <= 0 || channelId <= 0 || interval <= 0) continue;
            DBNation nation = DBNation.getById(nationId);
            if (nation == null) {
                AlertUtil.error("Mail error", "Cannot check mail for " + section + "(nation=" + nationId + "): Unknown nation");
                continue;
            }
            GuildMessageChannel channel = getDiscordApi().getGuildChannelById(channelId);
            if (channel == null) {
                AlertUtil.error("Mail error", "Cannot check mail for " + section + "(nation=" + nationId + "): Unknown channel: " + channelId);
            }
            try {
                Auth auth = nation.getAuth(true);
                AlertMailTask alertMailTask = new AlertMailTask(auth, channelId);
                taskTrack.addTask("Mail " + section, alertMailTask, interval, TimeUnit.SECONDS);
            } catch (IllegalArgumentException e) {
                AlertUtil.error("Mail error", "Cannot check mail for " + section + "(nation=" + nationId + "): They are not authenticated (user/pass)");
            }
        }
    }

    public Guild getServer() {
        return server;
    }

    public DiscordDB getDiscordDB() {
        return loader.getDiscordDB();
    }

    public NationDB getNationDB() {
        return loader.getNationDB();
    }

    public JDA getDiscordApi(long guildId) {
        return manager.getApiByGuildId(guildId);
    }

    public PoliticsAndWarV3 getV3() {
        return loader.getApiV3();
    }

    public PoliticsAndWarV2 getPnwApiV2() {
        return loader.getApiV2();
    }

    @Override
    public void onGuildMemberRoleAdd(@Nonnull GuildMemberRoleAddEvent event) {
        Guild guild = event.getGuild();
        GuildDB db = getGuildDB(guild);
        if (!db.hasAlliance()) return;

        executor.submit(() -> db.getHandler().onGuildMemberRoleAdd(event));
    }

    @Override
    public void onGuildBan(@NotNull GuildBanEvent event) {
        executor.submit(new Runnable() {
            @Override
            public void run() {
                DiscordBan discordBan = new DiscordBan(event.getUser().getIdLong(), event.getGuild().getIdLong(), System.currentTimeMillis(), "");
                getDiscordDB().addBans(List.of(discordBan));
            }
        });
    }

    @Override
    public void onGuildJoin(@Nonnull GuildJoinEvent event) {
        manager.put(event.getGuild().getIdLong(), event.getJDA());
        event.getGuild().loadMembers();
    }

    @Override
    public void onGuildAvailable(@NotNull GuildAvailableEvent event) {
        event.getGuild().loadMembers();
    }

    @Override
    public void onGuildInviteCreate(@Nonnull GuildInviteCreateEvent event) {
        Guild guild = event.getGuild();
        GuildDB db = getGuildDB(guild);
        if (db != null) {
            db.getHandler().onGuildInviteCreate(event);
        }
    }

    @Override
    public void onGuildMemberJoin(@Nonnull GuildMemberJoinEvent event) {
        executor.submit(() -> {
            try {
                Guild guild = event.getGuild();
                GuildDB db = getGuildDB(guild);

                DBNation nation = DiscordUtil.getNation(event.getUser());
                AutoRoleInfo task = db.getAutoRoleTask().autoRole(event.getMember(), nation);
                task.execute();
                db.getHandler().onGuildMemberJoin(event);

                eventBus.post(event);
            } catch (Throwable e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent event) {
        try {
            ModalInteraction interaction = event.getInteraction();
            String id = event.getModalId();
            InteractionHook hook = event.getHook();
            List<ModalMapping> values = event.getValues();

            Map<String, String> args = new HashMap<>();

            String[] pair = id.split(" ", 2);

            UUID uuid = UUID.fromString(pair[0]);

            args.put("", pair[1]);

            Guild guild = event.isFromGuild() ? event.getGuild() : null;

            Map<String, String> keyPairs = new LinkedHashMap<>();
            for (ModalMapping value : values) {
                keyPairs.put(value.getId(), value.getAsString());
            }
            Set<String> ignoreKeys = new HashSet<>();

            Map<String, String> defaults = IModalBuilder.DEFAULT_VALUES.getIfPresent(uuid);
            if (defaults == null) defaults = new HashMap<>();
            for (Map.Entry<String, String> defEntry : defaults.entrySet()) {
                String value = defEntry.getValue();
                Map<String, String> placeholders = IModalBuilder.getPlaceholders(keyPairs.keySet(), value);
                if (!placeholders.isEmpty()) {
                    for (Map.Entry<String, String> phEntry : placeholders.entrySet()) {
                        String phKey = phEntry.getKey();
                        String userInput = keyPairs.get(phKey);
                        String keyRegex = "\\{" + phKey + "(=[^}]+)?}";
                        value = value.replaceAll(keyRegex, userInput);
                        ignoreKeys.add(phKey);
                    }
                }
                args.putIfAbsent(defEntry.getKey(), value);
            }

            for (Map.Entry<String, String> entry : keyPairs.entrySet()) {
                String key = entry.getKey();
                if (ignoreKeys.contains(key)) continue;
                String value = entry.getValue();
                args.put(key, value);
            }

            DiscordHookIO io = new DiscordHookIO(hook, null).setInteraction(true);

            event.deferReply().queue();
            Locutus.imp().getCommandManager().getV2().run(guild, event.getChannel(), event.getUser(), event.getMessage(), io, pair[1], args, true);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        try {
            Message message = event.getMessage();

            Button button = event.getButton();

            if (button.getId().equalsIgnoreCase("")) {
                RateLimitUtil.queue(message.delete());
                return;
            }

            if (message.getAuthor().getIdLong() != Settings.INSTANCE.APPLICATION_ID) {
                Logg.text("Author not application");
                return;
            }


            User user = event.getUser();
            Guild guild = event.isFromGuild() ? event.getGuild() : message.isFromGuild() ? message.getGuild() : null;
            if (Settings.INSTANCE.MODERATION.BANNED_USERS.containsKey(user.getIdLong())) return;
            if (guild != null && Settings.INSTANCE.MODERATION.BANNED_GUILDS.containsKey(guild.getIdLong())) return;

            MessageChannel channel = event.getChannel();
            List<MessageEmbed> embeds = message.getEmbeds();
            MessageEmbed embed = !embeds.isEmpty() ? embeds.get(0) : null;

            InteractionHook hook = event.getHook();
            IMessageIO io = new DiscordHookIO(hook, event).setInteraction(true);
            IMessageIO ioToUse = io;

            try {
                Map<String, List<DiscordUtil.CommandInfo>> commandMap = DiscordUtil.getCommands(guild, embed, List.of(button), message.getJumpUrl(), false);
                if (commandMap.isEmpty()) return;

                List<DiscordUtil.CommandInfo> commands = commandMap.values().iterator().next();
                if (commands.isEmpty()) return;

                boolean markedDeleted = false;
                boolean deferred = false;
                boolean success = false;
                boolean hasLegacyCommand = false;
                CommandBehavior behavior = null;
                for (DiscordUtil.CommandInfo info : commands) {
                    behavior = info.behavior;
                    if (behavior == CommandBehavior.DELETE_MESSAGE && !markedDeleted) {
                        io.setMessageDeleted();
                        markedDeleted = true;
                    }
                    if (info.command.isBlank()) {
                        continue;
                    }
                    if (info.channelId != null) {
                        if (ioToUse instanceof DiscordChannelIO channelIO && channelIO.getIdLong() == info.channelId) {
                            continue;
                        }
                        GuildMessageChannel cmdChannel = getDiscordApi().getGuildChannelById(info.channelId);
                        if (cmdChannel == null) {
                            io.send("Channel not found: <#" + info.channelId + ">");
                            continue;
                        }
                        ioToUse = new DiscordChannelIO(cmdChannel);
                    }

                    String id = info.command;
                    if (!deferred && !id.contains("modal create")) {
                        deferred = true;
                        if (info.behavior == CommandBehavior.EPHEMERAL) {
                            event.deferReply(true).queue();
                            hook.setEphemeral(true);
                        } else {
                            RateLimitUtil.queue(event.deferEdit());
                        }
                        DiscordHookIO hookIO = (DiscordHookIO) io;
                        hookIO.setIsModal(event);
                    }

                    if (!id.isEmpty() && (id.startsWith(Settings.commandPrefix(true)) || getCommandManager().isModernPrefix(id.charAt(0)))) {
                        success |= handleCommandReaction(id, message, ioToUse, user, true);
                        hasLegacyCommand = true;
                    } else if (id.startsWith("{")) {
                        getCommandManager().getV2().run(guild, channel, user, message, ioToUse, id, true, true);
                    } else if (!id.isEmpty()) {
                        RateLimitUtil.queue(event.reply("Unknown command (2): `" + id + "`"));
                        return;
                    }
                }
                if (hasLegacyCommand && !success) {
                    behavior = null;
                }
                if (behavior != null) {
                    switch (behavior) {
                        case DELETE_MESSAGE -> {
                            io.setMessageDeleted();
                            RateLimitUtil.queue(message.delete());
                        }
                        case EPHEMERAL, UNPRESS -> {
                            // unsupported
                        }
                        case DELETE_PRESSED_BUTTON -> {
                            List<ActionRow> rows = new ArrayList<>(message.getActionRows());
                            for (int i = 0; i < rows.size(); i++) {
                                ActionRow row = rows.get(i);
                                List<ItemComponent> components = new ArrayList<>(row.getComponents());
                                if (components.remove(button)) {
                                    rows.set(i, ActionRow.of(components));
                                }
                            }
                            rows.removeIf(f -> f.getComponents().isEmpty());
                            RateLimitUtil.queue(message.editMessageComponents(rows));
                        }
                        case DELETE_BUTTONS -> {
                            RateLimitUtil.queue(message.editMessageComponents(new ArrayList<>()));
                        }
                    }
                }
            } catch (IllegalArgumentException e) {
                io.send(StringMan.stripApiKey(e.getMessage()));
                return;
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        try {
            Guild guild = event.isFromGuild() ? event.getGuild() : null;
            if (guild != null) {
                GuildDB db = getGuildDB(guild);
                if (db != null && !db.getHandler().onMessageReceived(event)) {
                    return;
                }
            }

            long start = System.currentTimeMillis();
            User author = event.getAuthor();

            // Cache locutus messages to reduce lookups from message reactions
            if (author.isBot() && author.getIdLong() == Settings.INSTANCE.APPLICATION_ID) {
                isMessageLocutusMap.put(event.getMessageIdLong(), true);
            } else {
                isMessageLocutusMap.put(event.getMessageIdLong(), false);
            }

            String message = event.getMessage().getContentRaw();
            DiscordChannelIO io = new DiscordChannelIO(event.getChannel(), () -> event.getMessage());
            getCommandManager().run(guild, io, author, message, true, true);
            long diff = System.currentTimeMillis() - start;
            if (diff > 1000) {
                StringBuilder response = new StringBuilder("## Long action: " + event.getAuthor().getIdLong() + " | " + event.getAuthor().getName() + ": " + DiscordUtil.trimContent(event.getMessage().getContentRaw()));
                if (event.isFromGuild()) {
                    response.append("\n\n- " + event.getGuild().getName() + " | " + event.getGuild().getId());
                }
                new RuntimeException(response.toString()).printStackTrace();
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private Map<Long, Boolean> isMessageLocutusMap = new ConcurrentHashMap<>();

    private Message isMessageLocutus(long messageId, GuildMessageChannel channel) {
        Boolean result = isMessageLocutusMap.get(messageId);
        if (result == Boolean.FALSE) {
            return null;
        }
        boolean isLocutus = false;
        try {
            Message message = link.locutus.discord.util.RateLimitUtil.complete(channel.retrieveMessageById(messageId));
            User author = message.getAuthor();
            isLocutus = (author != null && author.getIdLong() == Settings.INSTANCE.APPLICATION_ID);
            return isLocutus ? message : null;
        } catch (Throwable e) {}
        finally {
            isMessageLocutusMap.put(messageId, isLocutus);
        }
        return null;
    }

    @Override
    public void onMessageReactionAdd(@Nonnull MessageReactionAddEvent event) {
        User author = event.getUser();
        if (author.isBot() || author.isSystem()) return;
        if (author.getIdLong() == Settings.INSTANCE.APPLICATION_ID) {
            return;
        }
        Message message = isMessageLocutus(event.getMessageIdLong(), event.getGuildChannel());
        if (message == null) return;
        EmojiUnion emote;
        if (event.getUser().getIdLong() == Locutus.loader().getAdminUserId()) {
            emote = event.getEmoji();
            if ("\uD83D\uDEAB".equals(emote.asUnicode().getAsCodepoints())) {
                link.locutus.discord.util.RateLimitUtil.queue(event.getChannel().deleteMessageById(event.getMessageIdLong()));
                return;
            }
        }
        emote = event.getEmoji();
        onMessageReact(message, event.getUser(), emote, event.getResponseNumber());
    }

    public void onMessageReact(Message message, User user, EmojiUnion emote, long responseId) {
        onMessageReact(message, user, emote, responseId, true);
    }

    private boolean handleCommandReaction(String cmd, Message message, IMessageIO io, User user, boolean async) {
        Command cmdObject = null;
        boolean legacy = cmd.charAt(0) == Settings.commandPrefix(true).charAt(0);
        if (legacy) {
            String cmdLabel = cmd.split(" ")[0].substring(1);
            cmdObject = getCommandManager().getCommandMap().get(cmdLabel.toLowerCase());
            if (cmdObject == null) {
                return false;
            }
        }
        if (!(cmdObject instanceof Noformat)) {
            DBNation nation = DiscordUtil.getNation(user);
            NationPlaceholders formatter = Locutus.imp().getCommandManager().getV2().getNationPlaceholders();
            cmd = formatter.format2(message.getGuild(), nation, user, cmd, nation, false);
        }
        Guild guild = message.isFromGuild() ? message.getGuild() : null;

        if (cmdObject != null) {
            try {
                if (!cmdObject.checkPermission(guild, user)) {
                    return false;
                }
            } catch (InsufficientPermissionException e) {
                return false;
            }
        }
        getCommandManager().run(guild, io, user, cmd, async, false);
        return true;
    }

    public void onMessageReact(Message message, User user, EmojiUnion emote, long responseId, boolean async) {
        List<MessageEmbed> embeds = message.getEmbeds();
        if (embeds.size() != 1) {
            return;
        }

        MessageEmbed embed = embeds.get(0);

        Map<String, String> map = DiscordUtil.getReactions(embed);
        if (map == null) return;

        String raw = map.getOrDefault(emote.getName(), map.get(emote.asUnicode().getAsCodepoints()));
        if (raw == null) {
            RateLimitUtil.queue(message.removeReaction(emote, user));
            return;
        } else if (raw.isEmpty()) {
            link.locutus.discord.util.RateLimitUtil.queue(message.delete());
            return;
        }

        boolean deleteMessage = false;
        boolean deleteReactions = false;
        boolean deleteReaction = false;
        boolean prefix = true;

        MessageChannel channel = message.getChannel();
        if (raw.startsWith("<#")) {
            String channelId = raw.substring(0, raw.indexOf('>') + 1);
            channel = DiscordUtil.getChannel(message.getGuild(), channelId);
            raw = raw.substring(raw.indexOf(' ') + 1);
        } else if (raw.startsWith("#")) {
            String channelName = raw.substring(0, raw.indexOf(' '));
            channel = DiscordUtil.getChannel(message.getGuild(), channelName);
            raw = raw.substring(raw.indexOf(' ') + 1);
        }

        switch (raw.charAt(0)) {
            case '_':
                deleteReactions = true;
                break;
            case '~':
                deleteReaction = true;
                break;
            case '.':
                break;
            default:
                deleteMessage = true;
                prefix = false;
                break;
        }

        if (prefix) raw = raw.substring(1);
        boolean success = false;

        DiscordChannelIO io = new DiscordChannelIO(channel, () -> message);

        String[] split = raw.split("\\r?\\n(?=[" + StringMan.join(getCommandManager().getAllPrefixes(), "|") + "|{])");
        for (String cmd : split) {
            success |= handleCommandReaction(cmd, message, io, user, async);
        }
        if (success) {
            if (deleteMessage) {
                RateLimitUtil.queue(message.delete());
            }
            if (deleteReactions) {
                RateLimitUtil.queue(message.clearReactions());
            }
            if (deleteReaction) {
                RateLimitUtil.queue(message.removeReaction(emote, user));
            }
        } else {
            RateLimitUtil.queue(message.removeReaction(emote, user));
        }
    }

    public BankDB getBankDB() {
        return loader.getBankDB();
    }

    public ExecutorService getExecutor() {
        return executor;
    }

    public void stop() {
        synchronized (OffshoreInstance.BANK_LOCK) {
//            if (raidEstimator != null) {
//              s  raidEstimator.flush();
//            }

            for (JDA api : getDiscordApi().getApis()) {
                api.shutdownNow();
            }
            // close pusher subscriptions

            executor.shutdownNow();
            CommandManager cmdManager = getCommandManager();
            if (cmdManager != null) cmdManager.getExecutor().shutdownNow();

            // join all threads
            for (Thread thread : Thread.getAllStackTraces().keySet()) {
                if (thread != Thread.currentThread()) {
                    try {
                        thread.interrupt();
                    } catch (SecurityException ignore) {}
                }
            }

            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {}

            Logg.text("\n == Ignore the following if the thread doesn't relate to anything modifying persistent data");
            for (Map.Entry<Thread, StackTraceElement[]> thread : Thread.getAllStackTraces().entrySet()) {
                Logg.text("Thread did not close after 5s: " + thread.getKey() + "\n- " + StringMan.stacktraceToString(thread.getValue()));
            }

            System.exit(1);
        }
    }

    private void backup() {
        int turnsCheck = Settings.INSTANCE.BACKUP.TURNS;
        String script = Settings.INSTANCE.BACKUP.SCRIPT;
        try {
            Backup.backup(script, turnsCheck);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public GuildCustomMessageHandler getMessageHandler() {
        return messageHandler;
    }

    public DataDumpParser getDataDumper(boolean throwError) {
        if (!Settings.INSTANCE.DATABASE.DATA_DUMP.ENABLED) {
            if (throwError) {
                throw new RuntimeException("Data CSV parser not enabled. See: `config/config.yaml` setting `database.data-dump.enabled`");
            }
            return null;
        }
        synchronized (dataDumpParserLock) {
            if (dataDumpParser == null) {
                try {
                    dataDumpParser = new DataDumpParser();
                    dataDumpParser.load();
                } catch (Throwable e) {
                    Settings.INSTANCE.DATABASE.DATA_DUMP.ENABLED = false;
                    throw new RuntimeException(e);
                }
            }
        }
        return dataDumpParser;
    }

    public void setLoader(ILoader loader) {
        this.loader = loader;
    }

    public RepeatingTasks getRepeatingTasks() {
        return taskTrack;
    }
}
