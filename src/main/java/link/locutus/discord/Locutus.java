package link.locutus.discord;

import link.locutus.discord.apiv1.core.ApiKeyPool;
import link.locutus.discord.apiv2.PoliticsAndWarV2;
import link.locutus.discord.apiv3.PoliticsAndWarV3;
import link.locutus.discord.apiv3.subscription.PnwPusherHandler;
import link.locutus.discord.commands.manager.Command;
import link.locutus.discord.commands.manager.CommandManager;
import link.locutus.discord.commands.manager.Noformat;
import link.locutus.discord.commands.manager.dummy.DelegateMessage;
import link.locutus.discord.commands.manager.dummy.DelegateMessageEvent;
import link.locutus.discord.commands.manager.v2.impl.SlashCommandManager;
import link.locutus.discord.commands.stock.StockDB;
import link.locutus.discord.commands.trade.sub.CheckAllTradesTask;
import link.locutus.discord.config.Settings;
import link.locutus.discord.config.yaml.Config;
import link.locutus.discord.db.*;
import link.locutus.discord.db.entities.AllianceMetric;
import link.locutus.discord.db.entities.DiscordMeta;
import link.locutus.discord.db.entities.DBNation;
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
import link.locutus.discord.util.task.ia.MapFullTask;
import link.locutus.discord.util.task.mail.AlertMailTask;
import link.locutus.discord.util.trade.TradeDB;
import link.locutus.discord.util.update.*;
import link.locutus.discord.web.jooby.WebRoot;
import com.google.common.eventbus.EventBus;
import link.locutus.discord.apiv1.PoliticsAndWarBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildMessageChannel;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.invite.GuildInviteCreateEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.Compression;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import javax.security.auth.login.LoginException;
import java.nio.ByteBuffer;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import static link.locutus.discord.apiv1.enums.ResourceType.ALUMINUM;
import static link.locutus.discord.apiv1.enums.ResourceType.BAUXITE;
import static link.locutus.discord.apiv1.enums.ResourceType.COAL;
import static link.locutus.discord.apiv1.enums.ResourceType.CREDITS;
import static link.locutus.discord.apiv1.enums.ResourceType.FOOD;
import static link.locutus.discord.apiv1.enums.ResourceType.GASOLINE;
import static link.locutus.discord.apiv1.enums.ResourceType.IRON;
import static link.locutus.discord.apiv1.enums.ResourceType.LEAD;
import static link.locutus.discord.apiv1.enums.ResourceType.MUNITIONS;
import static link.locutus.discord.apiv1.enums.ResourceType.OIL;
import static link.locutus.discord.apiv1.enums.ResourceType.STEEL;
import static link.locutus.discord.apiv1.enums.ResourceType.URANIUM;

public final class Locutus extends ListenerAdapter {
    private static Locutus INSTANCE;
    private final CommandManager commandManager;
    private final Logger logger;
    private final StockDB stockDB;
    private PnwPusherHandler pusher;
    private ForumDB forumDb;

    private final String primaryKey;

    private GuildShardManager manager = new GuildShardManager();

    private final String discordToken;

    private final DiscordDB discordDB;
    private final NationDB nationDB;
    private Guild server;

    private final PoliticsAndWarV2 pnwApi;
    private final PoliticsAndWarV2 rootPnwApi;

    private final PoliticsAndWarV3 v3;

    private final TradeDB tradeManager;
    private final WarDB warDb;
    private BaseballDB baseBallDB;
    private final BankDB bankDb;
    private final ExecutorService executor;

    private final Map<Long, GuildDB> guildDatabases = new ConcurrentHashMap<>();
    private EventBus eventBus;
    private SlashCommandManager slashCommands;

    public static synchronized Locutus create() {
        if (INSTANCE != null) throw new IllegalStateException("Already initialized");
        try {
            return new Locutus();
        } catch (SQLException | ClassNotFoundException | LoginException | InterruptedException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    private Locutus() throws SQLException, ClassNotFoundException, LoginException, InterruptedException, NoSuchMethodException {
        if (INSTANCE != null) throw new IllegalStateException("Already running.");
        INSTANCE = this;
        if (Settings.INSTANCE.ROOT_SERVER <= 0) throw new IllegalStateException("Please set ROOT_SERVER in " + Settings.INSTANCE.getDefaultFile());
        if (Settings.INSTANCE.DISCORD.COMMAND.COMMAND_PREFIX.length() != 1) throw new IllegalStateException("COMMAND_PREFIX must be 1 character in " + Settings.INSTANCE.getDefaultFile());
        if (Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX.length() != 1) throw new IllegalStateException("LEGACY_COMMAND_PREFIX must be 1 character in " + Settings.INSTANCE.getDefaultFile());
        if (Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX.equalsIgnoreCase(Settings.INSTANCE.DISCORD.COMMAND.COMMAND_PREFIX)) {
            throw new IllegalStateException("LEGACY_COMMAND_PREFIX cannot equal COMMAND_PREFIX in " + Settings.INSTANCE.getDefaultFile());
        }
        if (Settings.INSTANCE.DISCORD.COMMAND.COMMAND_PREFIX.matches("[._~]")) {
            throw new IllegalStateException("COMMAND_PREFIX cannot be `.` or `_` or `~` in " + Settings.INSTANCE.getDefaultFile());
        }
        if (Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX.matches("[._~]")) {
            throw new IllegalStateException("LEGACY_COMMAND_PREFIX cannot be `.` or `_` or `~` in " + Settings.INSTANCE.getDefaultFile());
        }

        if (Settings.INSTANCE.ENABLED_COMPONENTS.REPEATING_TASKS && Settings.INSTANCE.ENABLED_COMPONENTS.SUBSCRIPTIONS) {
            this.pusher = new PnwPusherHandler(Settings.INSTANCE.API_KEY_PRIMARY);
        }

        this.logger = Logger.getLogger("LOCUTUS");
        this.eventBus = new EventBus();

        this.executor = Executors.newCachedThreadPool();

        this.discordDB = new DiscordDB();
        this.nationDB = new NationDB();
        this.warDb = new WarDB();
        this.stockDB = new StockDB();
        this.bankDb = new BankDB();
        this.tradeManager = new TradeDB();

        this.commandManager = new CommandManager(this);
        this.commandManager.registerCommands(discordDB);
        if (Settings.INSTANCE.BOT_TOKEN.isEmpty()) {
            throw new IllegalStateException("Please set BOT_TOKEN in " + Settings.INSTANCE.getDefaultFile());
        }
        this.discordToken = Settings.INSTANCE.BOT_TOKEN;

        if (Settings.INSTANCE.API_KEY_PRIMARY.isEmpty()) {
            if (Settings.INSTANCE.USERNAME.isEmpty() || Settings.INSTANCE.PASSWORD.isEmpty()) {
                throw new IllegalStateException("Please set API_KEY_PRIMARY or USERNAME/PASSWORD in " + Settings.INSTANCE.getDefaultFile());
            }
            Auth auth = new Auth(0, Settings.INSTANCE.USERNAME, Settings.INSTANCE.PASSWORD);
            Settings.INSTANCE.API_KEY_PRIMARY = auth.getApiKey();
        }

        if (Settings.INSTANCE.API_KEY_PRIMARY.isEmpty()) {
            throw new IllegalStateException("Please set API_KEY_PRIMARY or USERNAME/PASSWORD in " + Settings.INSTANCE.getDefaultFile());
        }

        this.primaryKey = Settings.INSTANCE.API_KEY_PRIMARY;

        Settings.INSTANCE.NATION_ID = 0;
        Integer nationIdFromKey = Locutus.imp().getDiscordDB().getNationFromApiKey(this.primaryKey);
        if (nationIdFromKey == null) {
            throw new IllegalStateException("Could not get NATION_ID from key. Please ensure a valid API_KEY is set in " + Settings.INSTANCE.getDefaultFile());
        }
        Settings.INSTANCE.NATION_ID = nationIdFromKey;

        {
            PNWUser adminPnwUser = Locutus.imp().getDiscordDB().getUserFromNationId(Settings.INSTANCE.NATION_ID);
            if (adminPnwUser != null && adminPnwUser.getDiscordId() != null) {
                Settings.INSTANCE.ADMIN_USER_ID = adminPnwUser.getDiscordId();
            }
        }

        List<String> pool = new ArrayList<>();
        pool.addAll(Settings.INSTANCE.API_KEY_POOL);
        if (pool.isEmpty()) {
            pool.add(Settings.INSTANCE.API_KEY_PRIMARY);
        }

        this.pnwApi = new PoliticsAndWarBuilder().addApiKeys(pool.toArray(new String[0])).setEnableCache(false).setTestServerMode(Settings.INSTANCE.TEST).build();
        this.rootPnwApi = new PoliticsAndWarBuilder().addApiKeys(primaryKey).setEnableCache(false).setTestServerMode(Settings.INSTANCE.TEST).build();

        ApiKeyPool<Map.Entry<String, String>> v3Pool = ApiKeyPool.builder().addKey(primaryKey, Settings.INSTANCE.ACCESS_KEY).build();
        this.v3 = new PoliticsAndWarV3(v3Pool);

        if (Settings.INSTANCE.ENABLED_COMPONENTS.EVENTS) {
            this.registerEvents();
        }

        this.nationDB.load();
        this.warDb.load();
        this.tradeManager.load();
    }

    public static void post(Object event) {
        imp().eventBus.post(event);
    }

    public void registerEvents() {
        eventBus.register(new TreatyUpdateProcessor());
        eventBus.register(new NationUpdateProcessor());
        eventBus.register(new CityUpdateProcessor());
        eventBus.register(new BankUpdateProcessor());
        eventBus.register(new WarUpdateProcessor());
        eventBus.register(new AllianceListener());
    }

    public EventBus getEventBus() {
        return eventBus;
    }

    public GuildShardManager getDiscordApi() {
        return manager;
    }

    public Locutus start() throws InterruptedException, LoginException, SQLException, ClassNotFoundException {
        if (Settings.INSTANCE.ENABLED_COMPONENTS.DISCORD_BOT) {
            JDABuilder builder = JDABuilder.createLight(discordToken, GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_MESSAGES, GatewayIntent.GUILD_MESSAGE_REACTIONS, GatewayIntent.DIRECT_MESSAGES);
            if (Settings.INSTANCE.ENABLED_COMPONENTS.SLASH_COMMANDS) {
                this.slashCommands = new SlashCommandManager(this);
                builder.addEventListeners(slashCommands);
            }
            if (Settings.INSTANCE.ENABLED_COMPONENTS.MESSAGE_COMMANDS) {
                builder.addEventListeners(this);
            }
            builder
                    .setBulkDeleteSplittingEnabled(true)
                    .setCompression(Compression.ZLIB)
                    .setMemberCachePolicy(MemberCachePolicy.ALL);
            if (Settings.INSTANCE.DISCORD.INTENTS.GUILD_MEMBERS) {
                builder.enableIntents(GatewayIntent.GUILD_MEMBERS);
            }
            if (Settings.INSTANCE.DISCORD.INTENTS.GUILD_PRESENCES) {
                builder.enableIntents(GatewayIntent.GUILD_PRESENCES);
            }
            if (Settings.INSTANCE.DISCORD.INTENTS.GUILD_MESSAGES) {
                builder.enableIntents(GatewayIntent.GUILD_MESSAGES);
            }
            if (Settings.INSTANCE.DISCORD.INTENTS.GUILD_MESSAGE_REACTIONS) {
                builder.enableIntents(GatewayIntent.GUILD_MESSAGE_REACTIONS);
            }
            if (Settings.INSTANCE.DISCORD.INTENTS.DIRECT_MESSAGES) {
                builder.enableIntents(GatewayIntent.DIRECT_MESSAGES);
            }
            if (Settings.INSTANCE.DISCORD.INTENTS.EMOJI) {
                builder.enableIntents(GatewayIntent.GUILD_EMOJIS);
            }
            if (Settings.INSTANCE.DISCORD.CACHE.MEMBER_OVERRIDES) {
                builder.enableCache(CacheFlag.MEMBER_OVERRIDES);
            }
            if (Settings.INSTANCE.DISCORD.CACHE.ONLINE_STATUS) {
                builder.enableCache(CacheFlag.ONLINE_STATUS);
            }
            if (Settings.INSTANCE.DISCORD.CACHE.EMOTE) {
                builder.enableCache(CacheFlag.EMOTE);
            }
//                Set<Long> whitelist = Settings.INSTANCE.Discord.Guilds.GET().WHITELIST();
//                long[] whitelistArr = new long[whitelist.size()];
//            int numShards = 10;
//            for (int i = 0; i < numShards; i++) {
//                long guildId = whitelistArr[i];
//                JDA instance = builder.useSharding(i, numShards).build();
//                manager.put(instance);
//            }
//            rootInstance = builder.useSharding(whitelistArr.length, whitelistArr.length + 1).build();

            JDA jda = builder.build();
            manager.put(jda);
            manager.awaitReady();

            long appId = jda.getSelfUser().getApplicationIdLong();
            if (appId > 0) {
                Settings.INSTANCE.APPLICATION_ID = appId;
            }

            if (this.slashCommands != null) {
                this.slashCommands.setupCommands();
            }

            this.server = manager.getGuildById(Settings.INSTANCE.ROOT_SERVER);

            if (Settings.INSTANCE.FORUM_FEED_SERVER > 0) {
                Guild guild = getDiscordApi().getGuildById(Settings.INSTANCE.FORUM_FEED_SERVER);
                if (guild == null) throw new IllegalStateException("Invalid guild: " + Settings.INSTANCE.FORUM_FEED_SERVER + " as FORUM_FEED_SERVER in " + Settings.INSTANCE.getDefaultFile());
                this.forumDb = new ForumDB(guild);
            }

            if (Settings.INSTANCE.ENABLED_COMPONENTS.CREATE_DATABASES_ON_STARTUP) {
                try {
                    initGuildDB();
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
            if (Settings.INSTANCE.ENABLED_COMPONENTS.REPEATING_TASKS) {
                initRepeatingTasks();
            }
            if (Settings.INSTANCE.ENABLED_COMPONENTS.SUBSCRIPTIONS) {
                initSubscriptions();
            }

            for (long guildId : Settings.INSTANCE.MODERATION.BANNED_GUILDS) {
                Guild guild = getDiscordApi().getGuildById(guildId);
                if (guild != null) {
                    link.locutus.discord.util.RateLimitUtil.queue(guild.leave());
                }
            }

            if (!Settings.INSTANCE.MODERATION.BANNED_ALLIANCES.isEmpty()) {
                for (GuildDB value : getGuildDatabases().values()) {
                    Guild guild = value.getGuild();
                    if (!guild.isLoaded()) continue;
                    long owner = guild.getOwnerIdLong();
                    DBNation nation = DiscordUtil.getNation(owner);
                    if (nation != null) {
                        if (Settings.INSTANCE.MODERATION.BANNED_ALLIANCES.contains(nation.getAlliance_id())) {
                            link.locutus.discord.util.RateLimitUtil.queue(guild.leave());
                        }
                    }
                }
            }
        }

        if (Settings.INSTANCE.ENABLED_COMPONENTS.WEB && (Settings.INSTANCE.WEB.PORT_HTTP > 0 || Settings.INSTANCE.WEB.PORT_HTTPS > 0)) {
            new WebRoot(Settings.INSTANCE.WEB.PORT_HTTP, Settings.INSTANCE.WEB.PORT_HTTPS);
        }

        return this;
    }

    public GuildDB getRootCoalitionServer() {
        GuildDB locutusStats = Locutus.imp().getGuildDB(Settings.INSTANCE.ROOT_COALITION_SERVER);
        return locutusStats;
    }

    public OffshoreInstance getRootBank() {
        return getGuildDB(getServer()).getHandler().getBank();
    }

    public PoliticsAndWarV2 getApi(int alliance) {
        if (alliance == Settings.INSTANCE.ALLIANCE_ID()) {
            return Locutus.imp().getRootPnwApi();
        } else if (alliance == 0) {
            return Locutus.imp().getPnwApi();
        } else {
            GuildDB guildDb = Locutus.imp().getGuildDBByAA(alliance);
            if (guildDb == null) {
                throw new IllegalArgumentException("Invalid guild: " + alliance);
            }
            return guildDb.getApi();
        }
    }

    public String getPrimaryKey() {
        return primaryKey;
    }

    public Auth getRootAuth() {
        Auth auth = getNationDB().getNation(Settings.INSTANCE.NATION_ID).getAuth(null);
        if (auth != null) auth.setApiKey(primaryKey);
        return auth;
    }

    public GuildDB getGuildDB(MessageReceivedEvent event) {
        return getGuildDB(event.isFromGuild() ? event.getGuild().getIdLong() : Settings.INSTANCE.ROOT_SERVER);
    }

    public Map<Long, GuildDB> getGuildDatabases() {
        return initGuildDB();
    }

    public GuildDB getGuildDB(Guild guild) {
        return getGuildDB(guild != null ? guild.getIdLong() : Settings.INSTANCE.ROOT_SERVER);
    }

    public GuildDB getGuildDB(long guildId) {
        return getGuildDB(guildId, true);
    }

    public GuildDB getGuildDB(long guildId, boolean cache) {
        if (cache) initGuildDB();
        GuildDB db = guildDatabases.get(guildId);
        if (db != null) {
            return db;
        }
        Guild guild = manager.getGuildById(guildId);
        if (guild == null) return null;
        synchronized (guildDatabases) {
            db = guildDatabases.get(guildId);
            if (db != null) return db;

            try {
                db = new GuildDB(guild);
                guildDatabases.put(guildId, db);
                return db;
            } catch (SQLException | ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private Map<Long, GuildDB> initGuildDB() {
        synchronized (guildDatabases) {
            if (guildDatabases.isEmpty()) {
                for (Guild guild : manager.getGuilds()) {
                    GuildDB db = getGuildDB(guild.getIdLong(), false);
//                    String key = db.getOrNull(GuildDB.Key.API_KEY);
//                    if (key == null) continue;
//
//                    ApiRecord record;
//                    String keyInfo = Locutus.imp().getDiscordDB().getInfo("key." + key);
//                    if (keyInfo == null) {
//
//                    }
                }
            }
            return guildDatabases;
        }
    }

    public GuildDB getGuildDBByAA(int allianceId) {
        if (allianceId == 0) return null;
        for (Map.Entry<Long, GuildDB> entry : initGuildDB().entrySet()) {
            GuildDB db = entry.getValue();
            Integer aaId = db.getOrNull(GuildDB.Key.ALLIANCE_ID, false);
            if (aaId != null && aaId == allianceId && db.getOrNull(GuildDB.Key.DELEGATE_SERVER, false) == null) {
                return entry.getValue();
            }
        }
//        if (Locutus.imp().getNationDB().getAllianceName(allianceId) != null) {
//            for (Map.Entry<Long, GuildDB> entry : initGuildDB().entrySet()) {
//                GuildDB db = entry.getValue();
//                if (db.getCoalition("offshore").contains(allianceId)) {
//                    return db;
//                }
//            }
//        }
        return null;
    }

    public static CommandManager cmd() {
        return imp().getCommandManager();
    }

    public static Locutus imp() {
        return INSTANCE;
    }

    public static void main(String[] args) throws InterruptedException, LoginException, SQLException, ClassNotFoundException {
        Settings.INSTANCE.reload(Settings.INSTANCE.getDefaultFile());
        // load settings
        Locutus instance = Locutus.create().start();
    }
    public WarDB getWarDb() {
        return warDb;
    }

    public BaseballDB getBaseballDB() {
        if (this.baseBallDB == null) {
            synchronized (this) {
                if (this.baseBallDB == null) {
                    try {
                        baseBallDB = new BaseballDB(Settings.INSTANCE.DATABASE);
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
        return this.baseBallDB;
    }

    public TradeDB getTradeManager() {
        this.tradeManager.load();
        return tradeManager;
    }

    public CommandManager getCommandManager() {
        return commandManager;
    }

    public void autoRole(DBNation nation) {
        PNWUser user = Locutus.imp().getDiscordDB().getUserFromNationId(nation.getNation_id());
        if (user != null && user.getDiscordId() != null) {
            User discordUser = manager.getUserById(user.getDiscordId());
            if (discordUser != null) {
                List<Guild> guilds = discordUser.getMutualGuilds();
                for (Guild guild : guilds) {
                    GuildDB db = getGuildDB(guild);
                    Member member = guild.getMember(discordUser);
                    if (member != null) {
                        try {
                            db.getAutoRoleTask().autoRole(member, s -> {
                            });
                        } catch (Throwable e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }

    public StockDB getStockDB() {
        return stockDB;
    }

    public ForumDB getForumDb() {
        return forumDb;
    }

//    public Set<DBMain> getDatabases() {
//        Set<DBMain> databases = new HashSet<>();
//        databases.add(tradeManager.getTradeDb());
//        databases.add(forumDb);
//        databases.add(discordDB);
//        databases.add(nationDB);
//        databases.add(warDb);
//        databases.add(bankDb);
//        databases.add(stockDB);
//        databases.addAll(guildDatabases.values());
//        return databases;
//    }

    public ScheduledFuture<?> addTaskSeconds(CaughtTask task, long interval) {
        return addTask(CaughtRunnable.wrap(task), interval, TimeUnit.SECONDS);
    }
    public ScheduledFuture<?> addTask(Runnable task, long interval, TimeUnit unit) {
        if (interval <= 0) return null;
        if (!(task instanceof CaughtRunnable)) {
            Runnable parent = task;
            task = new CaughtRunnable() {
                @Override
                public void runUnsafe() {
                    parent.run();
                }
            };
        }
        return commandManager.getExecutor().scheduleWithFixedDelay(task, interval, interval, unit);
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

    public PnwPusherHandler getPusher() {
        return pusher;
    }

    public void initSubscriptions() {
        if (pusher == null) return;

        if (Settings.INSTANCE.TASKS.SUBSCRIPTIONS.CITIES) {
            nationDB.subscribeCities(pusher);
        }
    }

    public void initRepeatingTasks() {
        if ((Settings.INSTANCE.TASKS.ACTIVE_NATION_SECONDS > 0 || Settings.INSTANCE.TASKS.COLORED_NATIONS_SECONDS > 0 || Settings.INSTANCE.TASKS.ALL_NON_VM_NATIONS_SECONDS > 0) && nationDB.getNations().isEmpty()) {
            logger.info("No nations found. Updating all nations");
            nationDB.updateAllNations(null);
        }

        // Turn change
        if (Settings.INSTANCE.TASKS.ENABLE_TURN_TASKS) {
            AtomicLong lastTurn = new AtomicLong();
            addTaskSeconds(new CaughtTask() {
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
            }, 5);
        }

        addTaskSeconds(() -> {
            List<Event> events = new ArrayList<>();
            nationDB.updateMostActiveNations(490, events::add);
            runEventsAsync(events);
        }, Settings.INSTANCE.TASKS.ACTIVE_NATION_SECONDS);

        addTaskSeconds(() -> {
            List<Event> events = new ArrayList<>();
            nationDB.updateColoredNations(events::add);
            runEventsAsync(events);
        }, Settings.INSTANCE.TASKS.COLORED_NATIONS_SECONDS);

        addTaskSeconds(() -> {
            List<Event> events = new ArrayList<>();
            nationDB.updateNationsV2(false, events::add);
            runEventsAsync(events);
        }, Settings.INSTANCE.TASKS.ALL_NON_VM_NATIONS_SECONDS);

        addTaskSeconds(() -> {
            List<Event> events = new ArrayList<>();
            nationDB.updateDirtyCities(events::add);
            runEventsAsync(events);
        }, Settings.INSTANCE.TASKS.OUTDATED_CITIES_SECONDS);

        if (Settings.INSTANCE.TASKS.FETCH_SPIES_INTERVAL_SECONDS > 0) {
            SpyUpdater spyUpdate = new SpyUpdater();
            addTaskSeconds(() -> {

                spyUpdate.run();

            }, Settings.INSTANCE.TASKS.FETCH_SPIES_INTERVAL_SECONDS);
        }

        if (Settings.INSTANCE.TASKS.BEIGE_REMINDER_SECONDS > 0) {
            LeavingBeigeAlert beigeAlerter = new LeavingBeigeAlert();

            addTaskSeconds(beigeAlerter::run, Settings.INSTANCE.TASKS.BEIGE_REMINDER_SECONDS);
        }

        addTaskSeconds(() -> {
            synchronized (warDb) {
                ArrayDeque<Event> events = new ArrayDeque<>();
                warDb.updateActiveWars(events::add);
                runEventsAsync(events);
                events = new ArrayDeque<>();
                warDb.updateAttacks(events::add);
                runEventsAsync(events);
            }
        }, Settings.INSTANCE.TASKS.ACTIVE_WAR_SECONDS);

        addTaskSeconds(() -> {
            synchronized (warDb) {
                ArrayDeque<Event> events = new ArrayDeque<>();
                warDb.updateAllWars(events::add);
                runEventsAsync(events);

                events = new ArrayDeque<>();
                warDb.updateAttacks(events::add);
                runEventsAsync(events);

                if (Settings.INSTANCE.TASKS.ESCALATION_ALERTS) {
                    long start = System.currentTimeMillis();
                    WarUpdateProcessor.checkActiveConflicts();
                    long diff = System.currentTimeMillis() - start;
                    if (diff > 100) {
                        AlertUtil.error("Took too long for checkActiveConflicts (" + diff + "ms)", new Exception());
                    }
                }
            }
        }, Settings.INSTANCE.TASKS.ALL_WAR_SECONDS);

        checkMailTasks();

        addTaskSeconds(() -> Locutus.imp().getWarDb().updateBountiesV3(), Settings.INSTANCE.TASKS.BOUNTY_UPDATE_SECONDS);

        addTaskSeconds(() -> getNationDB().updateTreaties(Event::post), Settings.INSTANCE.TASKS.TREATY_UPDATE_SECONDS);

        if (Settings.INSTANCE.TASKS.BASEBALL_SECONDS > 0) {
            addTaskSeconds(() -> {
                BaseballDB db = Locutus.imp().getBaseballDB();
                Integer minId = db.getMinGameId();
                if (minId != null) minId++;
                db.updateGames(true, false, minId, null);
            }, Settings.INSTANCE.TASKS.BASEBALL_SECONDS);
        }

        if (Settings.INSTANCE.TASKS.COMPLETED_TRADES_SECONDS > 0) {
            AtomicBoolean updateTradeTask = new AtomicBoolean(false);
            addTaskSeconds(() -> getTradeManager().updateTradeList(false, true),
                    Settings.INSTANCE.TASKS.COMPLETED_TRADES_SECONDS);
        }

        // TODO update to v3
        int activeTradeSeconds = Settings.INSTANCE.TASKS.TRADE_PRICE_SECONDS;
        if (activeTradeSeconds > 0) {
            commandManager.getExecutor().scheduleWithFixedDelay(new CheckAllTradesTask(FOOD), 73, activeTradeSeconds, TimeUnit.SECONDS);
            commandManager.getExecutor().scheduleWithFixedDelay(new CheckAllTradesTask(COAL), 73, activeTradeSeconds, TimeUnit.SECONDS);
            commandManager.getExecutor().scheduleWithFixedDelay(new CheckAllTradesTask(OIL), 73, activeTradeSeconds, TimeUnit.SECONDS);
            commandManager.getExecutor().scheduleWithFixedDelay(new CheckAllTradesTask(URANIUM), 73, activeTradeSeconds, TimeUnit.SECONDS);
            commandManager.getExecutor().scheduleWithFixedDelay(new CheckAllTradesTask(LEAD), 73, activeTradeSeconds, TimeUnit.SECONDS);
            commandManager.getExecutor().scheduleWithFixedDelay(new CheckAllTradesTask(IRON), 73, activeTradeSeconds, TimeUnit.SECONDS);
            commandManager.getExecutor().scheduleWithFixedDelay(new CheckAllTradesTask(BAUXITE), 73, activeTradeSeconds, TimeUnit.SECONDS);
            commandManager.getExecutor().scheduleWithFixedDelay(new CheckAllTradesTask(GASOLINE), 73, activeTradeSeconds, TimeUnit.SECONDS);
            commandManager.getExecutor().scheduleWithFixedDelay(new CheckAllTradesTask(MUNITIONS), 73, activeTradeSeconds, TimeUnit.SECONDS);
            commandManager.getExecutor().scheduleWithFixedDelay(new CheckAllTradesTask(STEEL), 73, activeTradeSeconds, TimeUnit.SECONDS);
            commandManager.getExecutor().scheduleWithFixedDelay(new CheckAllTradesTask(ALUMINUM), 73, activeTradeSeconds, TimeUnit.SECONDS);
            commandManager.getExecutor().scheduleWithFixedDelay(new CheckAllTradesTask(CREDITS), 15 * activeTradeSeconds, 15 * activeTradeSeconds, TimeUnit.SECONDS);
        }

        if (forumDb != null && Settings.INSTANCE.TASKS.FORUM_UPDATE_INTERVAL_SECONDS > 0) {
            addTaskSeconds(forumDb::update, Settings.INSTANCE.TASKS.FORUM_UPDATE_INTERVAL_SECONDS);
        }
    }

    private void runTurnTasks(long lastTurn, long currentTurn) {
        try {
            new TurnChangeEvent(lastTurn, currentTurn).post();

            for (DBNation nation : nationDB.getNations().values()) {
                nation.processTurnChange(lastTurn, currentTurn, Event::post);
            }

            if (Settings.INSTANCE.TASKS.TURN_TASKS.MAP_FULL_ALERT) {
                // See also  the war update processor has some commented out code for MAPS (near the audit stuff)
                new MapFullTask().run();
            }

            {
                // Update all nations

                {
                    List<Event> events = new ArrayList<>();
                    nationDB.updateNationsV2(true, events::add);
                    runEventsAsync(events);
                }
                {
                    List<Event> events = new ArrayList<>();
                    nationDB.updateMostActiveNations(490, events::add);
                    runEventsAsync(events);
                }
                {
                    List<Event> events = new ArrayList<>();
                    nationDB.updateAlliances(null, events::add);
                    runEventsAsync(events);
                }

                nationDB.deleteExpiredTreaties(Event::post);
                nationDB.updateTreaties(Event::post);

                nationDB.saveAllCities(); // TODO save all cities


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
            DBNation nation = DBNation.byId(nationId);
            if (nation == null) {
                AlertUtil.error("Mail error", "Cannot check mail for " + section + "(nation=" + nationId + "): Unknown nation");
                continue;
            }
            GuildMessageChannel channel = getDiscordApi().getGuildChannelById(channelId);
            if (channel == null) {
                AlertUtil.error("Mail error", "Cannot check mail for " + section + "(nation=" + nationId + "): Unknown channel: " + channelId);
            }
            try {
                Auth auth = nation.getAuth(null);
                AlertMailTask alertMailTask = new AlertMailTask(auth, channelId);
                commandManager.getExecutor().scheduleWithFixedDelay(alertMailTask, 5 * 60, task.FETCH_INTERVAL_SECONDS, TimeUnit.SECONDS);
            } catch (IllegalArgumentException e) {
                AlertUtil.error("Mail error", "Cannot check mail for " + section + "(nation=" + nationId + "): They are not authenticated (user/pass)");
            }
        }
    }

    public Guild getServer() {
        return server;
    }

    public DiscordDB getDiscordDB() {
        return discordDB;
    }

    public NationDB getNationDB() {
        return nationDB;
    }

    public JDA getDiscordApi(long guildId) {
        return manager.getApiByGuildId(guildId);
    }

    public PoliticsAndWarV3 getV3() {
        return v3;
    }

    public PoliticsAndWarV2 getPnwApi() {
        return pnwApi;
    }

    public PoliticsAndWarV2 getRootPnwApi() {
        return rootPnwApi;
    }

    @Override
    public void onGuildMemberRoleAdd(@Nonnull GuildMemberRoleAddEvent event) {
        Guild guild = event.getGuild();
        GuildDB db = getGuildDB(guild);
        Integer aaId = db.getOrNull(GuildDB.Key.ALLIANCE_ID);
        if (aaId == null) return;

        executor.submit(() -> db.getHandler().onGuildMemberRoleAdd(event));
    }

    @Override
    public void onGuildJoin(@Nonnull GuildJoinEvent event) {
        manager.put(event.getGuild().getIdLong(), event.getJDA());
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

            Guild guild = event.getGuild();
            GuildDB db = getGuildDB(guild);

            db.getAutoRoleTask().autoRole(event.getMember(), s -> {});
            db.getHandler().onGuildMemberJoin(event);

        });
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

            commandManager.run(event);
            long diff = System.currentTimeMillis() - start;
            if (diff > 1000) {
                StringBuilder response = new StringBuilder("## Long action: " + event.getAuthor().getIdLong() + " | " + event.getAuthor().getName() + ": " + DiscordUtil.trimContent(event.getMessage().getContentRaw()));
                if (event.isFromGuild()) {
                    response.append("\n\n - " + event.getGuild().getName() + " | " + event.getGuild().getId());
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
        if (event.getUser().getIdLong() == Settings.INSTANCE.ADMIN_USER_ID) {
            MessageReaction.ReactionEmote emote = event.getReactionEmote();
            if ("\uD83D\uDEAB".equals(emote.getEmoji())) {
                link.locutus.discord.util.RateLimitUtil.queue(event.getChannel().deleteMessageById(event.getMessageIdLong()));
                return;
            }
        }
        MessageReaction.ReactionEmote emote = event.getReactionEmote();
        onMessageReact(message, event.getUser(), emote, event.getResponseNumber());
    }

    public void onMessageReact(Message message, User user, MessageReaction.ReactionEmote emote, long responseId) {
        onMessageReact(message, user, emote, responseId, true);
    }

    public void onMessageReact(Message message, User user, MessageReaction.ReactionEmote emote, long responseId, boolean async) {
        List<MessageEmbed> embeds = message.getEmbeds();
        if (embeds.size() != 1) {
            return;
        }

        MessageEmbed embed = embeds.get(0);

        Map<String, String> map = DiscordUtil.getReactions(embed);
        if (map == null) return;

        String raw = map.getOrDefault(emote.getName(), map.get(emote.getEmoji()));
        if (raw == null) {
            RateLimitUtil.queue(message.removeReaction(emote.getEmoji(), user));
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

        String[] split = raw.split("\\r?\\n(?=[" + Settings.INSTANCE.DISCORD.COMMAND.COMMAND_PREFIX + "|" + Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "])");
        for (String cmd : split) {
            Command cmdObject = null;
            boolean legacy = cmd.charAt(0) == Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX.charAt(0);
            if (legacy) {
                String cmdLabel = cmd.split(" ")[0].substring(1);
                cmdObject = commandManager.getCommandMap().get(cmdLabel.toLowerCase());
                if (cmdObject == null) {
                    continue;
                }
            }
            if (!(cmdObject instanceof Noformat)) {
                cmd = DiscordUtil.format(message.getGuild(), channel, user, DiscordUtil.getNation(user), cmd);
            }
            Message cmdMessage = DelegateMessage.create(message, cmd, channel);

            MessageReceivedEvent cmdEvent = new DelegateMessageEvent(message.getGuild(), responseId, cmdMessage) {
                @Nonnull
                @Override
                public User getAuthor() {
                    return user;
                }

                @Override
                public long getResponseNumber() {
                    return -1;
                }
            };

            if (cmdObject != null) {
                try {
                    if (!cmdObject.checkPermission(cmdEvent)) {
                        continue;
                    }
                } catch (InsufficientPermissionException e) {
                    continue;
                }
            }
            success = true;
            commandManager.run(cmdEvent, async, false);
        }
        if (success) {
            if (deleteMessage) {
                RateLimitUtil.queue(message.delete());
            }
            if (deleteReactions) {
                RateLimitUtil.queue(message.clearReactions());
            }
            if (deleteReaction) {
                RateLimitUtil.queue(message.removeReaction(emote.getEmoji(), user));
            }
        } else {
            RateLimitUtil.queue(message.removeReaction(emote.getEmoji(), user));
        }
    }

    public BankDB getBankDB() {
        return this.bankDb;
    }

    public ExecutorService getExecutor() {
        return executor;
    }
}
