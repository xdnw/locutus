package link.locutus.discord;

import com.google.common.eventbus.AsyncEventBus;
import link.locutus.discord.apiv1.core.ApiKeyPool;
import link.locutus.discord.apiv2.PoliticsAndWarV2;
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
import link.locutus.discord.db.entities.AllianceMetric;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DiscordBan;
import link.locutus.discord.db.entities.DiscordMeta;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.event.Event;
import link.locutus.discord.event.game.TurnChangeEvent;
import link.locutus.discord.network.ProxyHandler;
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
import link.locutus.discord.util.task.ia.MapFullTask;
import link.locutus.discord.util.task.mail.AlertMailTask;
import link.locutus.discord.util.task.roles.AutoRoleInfo;
import link.locutus.discord.util.trade.TradeManager;
import link.locutus.discord.util.update.*;
import link.locutus.discord.web.jooby.WebRoot;
import com.google.common.eventbus.EventBus;
import link.locutus.discord.apiv1.PoliticsAndWarBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
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
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Component;
import net.dv8tion.jda.api.interactions.components.ItemComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.modals.ModalInteraction;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.Compression;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.apache.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
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
import java.util.logging.Logger;

public final class Locutus extends ListenerAdapter {
    private static Locutus INSTANCE;
    private final CommandManager commandManager;
    private final Logger logger;
    private final StockDB stockDB;
    private PnwPusherShardManager pusher;
    private ForumDB forumDb;

    private GuildShardManager manager = new GuildShardManager();

    private final String discordToken;

    private final DiscordDB discordDB;
    private final NationDB nationDB;
    private Guild server;

    private final PoliticsAndWarV2 pnwApi;

    private final PoliticsAndWarV3 v3;

    private final TradeManager tradeManager;
    private final WarDB warDb;
    private BaseballDB baseBallDB;
    private final BankDB bankDb;
    private final ExecutorService executor;

    private final Map<Long, GuildDB> guildDatabases = new ConcurrentHashMap<>();
    private EventBus eventBus;
    private SlashCommandManager slashCommands;

    private ProxyHandler proxyHandler;

    public static synchronized Locutus create() {
        if (INSTANCE != null) throw new IllegalStateException("Already initialized");
        try {
            return new Locutus();
        } catch (SQLException | ClassNotFoundException | LoginException | InterruptedException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    private void setInfoLogging() {
        org.apache.log4j.Logger.getRootLogger().setLevel(Level.INFO);
//        Configurator.setRootLevel(org.apache.logging.log4j.Level.INFO);

        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();
        LoggerConfig loggerConfig = config.getLoggerConfig(LogManager.ROOT_LOGGER_NAME);
        loggerConfig.setLevel(org.apache.logging.log4j.Level.INFO);
        ctx.updateLoggers();

        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
        root.setLevel(ch.qos.logback.classic.Level.INFO);
    }

    private Locutus() throws SQLException, ClassNotFoundException, LoginException, InterruptedException, NoSuchMethodException {
        setInfoLogging();

        if (INSTANCE != null) throw new IllegalStateException("Already running.");
        INSTANCE = this;
        long start = System.currentTimeMillis();

        this.proxyHandler = new ProxyHandler();

        System.out.println("remove:|| proxyhandler " + (((-start)) + (start = System.currentTimeMillis())));


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

        this.logger = Logger.getLogger("LOCUTUS");
        this.eventBus = new AsyncEventBus("locutus", Runnable::run);

        System.out.println("remove:|| eventbus " + (((-start)) + (start = System.currentTimeMillis())));

        this.executor = Executors.newCachedThreadPool();

        System.out.println("remove:|| executor " + (((-start)) + (start = System.currentTimeMillis())));
        this.discordDB = new DiscordDB();
        System.out.println("remove:|| discorddb " + (((-start)) + (start = System.currentTimeMillis())));
        this.nationDB = new NationDB();
        System.out.println("remove:|| nationdb " + (((-start)) + (start = System.currentTimeMillis())));
        this.warDb = new WarDB();
        System.out.println("remove:|| wardb " + (((-start)) + (start = System.currentTimeMillis())));
        this.stockDB = new StockDB();
        System.out.println("remove:|| stockdb " + (((-start)) + (start = System.currentTimeMillis())));
        this.bankDb = new BankDB("bank");
        System.out.println("remove:|| bankdb " + (((-start)) + (start = System.currentTimeMillis())));
        this.tradeManager = new TradeManager();
        System.out.println("remove:|| trademanager " + (((-start)) + (start = System.currentTimeMillis())));

        this.commandManager = new CommandManager(this);
        System.out.println("remove:|| commandmanager " + (((-start)) + (start = System.currentTimeMillis())));
        this.commandManager.registerCommands(discordDB);
        System.out.println("remove:|| registercommands " + (((-start)) + (start = System.currentTimeMillis())));
        if (Settings.INSTANCE.BOT_TOKEN.isEmpty()) {
            throw new IllegalStateException("Please set BOT_TOKEN in " + Settings.INSTANCE.getDefaultFile());
        }
        this.discordToken = Settings.INSTANCE.BOT_TOKEN;

        if (Settings.INSTANCE.API_KEY_PRIMARY.isEmpty()) {
            if (Settings.INSTANCE.USERNAME.isEmpty() || Settings.INSTANCE.PASSWORD.isEmpty()) {
                throw new IllegalStateException("Please set API_KEY_PRIMARY or USERNAME/PASSWORD in " + Settings.INSTANCE.getDefaultFile());
            }
            Auth auth = new Auth(0, Settings.INSTANCE.USERNAME, Settings.INSTANCE.PASSWORD);
            ApiKeyPool.ApiKey key = auth.fetchApiKey();
            Settings.INSTANCE.API_KEY_PRIMARY = key.getKey();
        }
        System.out.println("remove:|| apikey " + (((-start)) + (start = System.currentTimeMillis())));

        if (Settings.INSTANCE.API_KEY_PRIMARY.isEmpty()) {
            throw new IllegalStateException("Please set API_KEY_PRIMARY or USERNAME/PASSWORD in " + Settings.INSTANCE.getDefaultFile());
        }

        Settings.INSTANCE.NATION_ID = 0;
        Integer nationIdFromKey = Locutus.imp().getDiscordDB().getNationFromApiKey(Settings.INSTANCE.API_KEY_PRIMARY);
        if (nationIdFromKey == null) {
            throw new IllegalStateException("Could not get NATION_ID from key. Please ensure a valid API_KEY is set in " + Settings.INSTANCE.getDefaultFile());
        }
        Settings.INSTANCE.NATION_ID = nationIdFromKey;

        System.out.println("remove:|| nationid " + (((-start)) + (start = System.currentTimeMillis())));

        {
            PNWUser adminPnwUser = Locutus.imp().getDiscordDB().getUserFromNationId(Settings.INSTANCE.NATION_ID);
            if (adminPnwUser != null) {
                Settings.INSTANCE.ADMIN_USER_ID = adminPnwUser.getDiscordId();
            }
        }
        System.out.println("remove:|| adminuserid " + (((-start)) + (start = System.currentTimeMillis())));

        List<String> pool = new ArrayList<>();
        pool.addAll(Settings.INSTANCE.API_KEY_POOL);
        if (pool.isEmpty()) {
            pool.add(Settings.INSTANCE.API_KEY_PRIMARY);
        }

        this.pnwApi = new PoliticsAndWarBuilder().addApiKeys(pool.toArray(new String[0])).setEnableCache(false).setTestServerMode(Settings.INSTANCE.TEST).build();
        System.out.println("remove:|| pnwapi " + (((-start)) + (start = System.currentTimeMillis())));

        ApiKeyPool v3Pool = ApiKeyPool.builder().addKey(Settings.INSTANCE.NATION_ID, Settings.INSTANCE.API_KEY_PRIMARY,Settings.INSTANCE.ACCESS_KEY).build();
        this.v3 = new PoliticsAndWarV3(v3Pool);
        System.out.println("remove:|| v3 " + (((-start)) + (start = System.currentTimeMillis())));

        if (Settings.INSTANCE.ENABLED_COMPONENTS.EVENTS) {
            this.registerEvents();
        }

        System.out.println("remove:|| events " + (((-start)) + (start = System.currentTimeMillis())));

        this.nationDB.load();
        System.out.println("remove:|| nationdbload " + (((-start)) + (start = System.currentTimeMillis())));
        this.warDb.load();
        System.out.println("remove:|| wardbload " + (((-start)) + (start = System.currentTimeMillis())));
        this.tradeManager.load();
        System.out.println("remove:|| trademanagerload " + (((-start)) + (start = System.currentTimeMillis())));
    }

    public ProxyHandler getProxyHandler() {
        return proxyHandler;
    }

    public static void post(Object event) {
        imp().eventBus.post(event);
    }

    public void registerEvents() {
        eventBus.register(new TreatyUpdateProcessor());
        eventBus.register(new NationUpdateProcessor());
        eventBus.register(new TradeListener());
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
        long start = System.currentTimeMillis();
        backup();
        System.out.println("remove:|| backup " + (((-start)) + (start = System.currentTimeMillis())));
        if (Settings.INSTANCE.ENABLED_COMPONENTS.DISCORD_BOT) {
            JDABuilder builder = JDABuilder.createLight(discordToken, GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_MESSAGES, GatewayIntent.DIRECT_MESSAGES);
            if (Settings.INSTANCE.ENABLED_COMPONENTS.SLASH_COMMANDS) {
                this.slashCommands = new SlashCommandManager(this);
                builder.addEventListeners(slashCommands);
            }
            if (Settings.INSTANCE.ENABLED_COMPONENTS.MESSAGE_COMMANDS) {
                builder.addEventListeners(this);
            }
            builder
                    .setChunkingFilter(ChunkingFilter.NONE)
                    .setBulkDeleteSplittingEnabled(false)
                    .setCompression(Compression.ZLIB)
                    .setMemberCachePolicy(MemberCachePolicy.ALL);
            if (Settings.INSTANCE.DISCORD.INTENTS.GUILD_MEMBERS) {
                builder.enableIntents(GatewayIntent.GUILD_MEMBERS);
            }
            if (Settings.INSTANCE.DISCORD.INTENTS.MESSAGE_CONTENT) {
                builder.enableIntents(GatewayIntent.MESSAGE_CONTENT);
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
                builder.enableIntents(GatewayIntent.GUILD_EMOJIS_AND_STICKERS);
            }
            if (Settings.INSTANCE.DISCORD.CACHE.MEMBER_OVERRIDES) {
                builder.enableCache(CacheFlag.MEMBER_OVERRIDES);
            }
            if (Settings.INSTANCE.DISCORD.CACHE.ONLINE_STATUS) {
                builder.enableCache(CacheFlag.ONLINE_STATUS);
            }
            if (Settings.INSTANCE.DISCORD.CACHE.EMOTE) {
                builder.enableCache(CacheFlag.EMOJI);
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

            System.out.println("remove:|| building " + (((-start)) + (start = System.currentTimeMillis())));
            JDA jda = builder.build();
            if (slashCommands != null) slashCommands.registerCommandData(jda);
            System.out.println(":||Remove build " + jda.getStatus() + " " + (((-start)) + (start = System.currentTimeMillis())));
            setSelfUser(jda);
            System.out.println(":||Remove Setup slash commands " + jda.getStatus() + " " + (((-start)) + (start = System.currentTimeMillis())));
            manager.put(jda);
            jda.awaitStatus(JDA.Status.LOADING_SUBSYSTEMS);
            System.out.println(":||Remove subsystems " + jda.getStatus() + " " + (((-start)) + (start = System.currentTimeMillis())));
            manager.awaitReady();
            setSelfUser(jda);
            if (Settings.INSTANCE.ENABLED_COMPONENTS.CREATE_DATABASES_ON_STARTUP) {
                initDBPartial(true);
            }
            System.out.println(":||Remove init db " + jda.getStatus() + " " + (((-start)) + (start = System.currentTimeMillis())));
            Guild rootGuild = manager.getGuildById(Settings.INSTANCE.ROOT_SERVER);
            if (rootGuild != null) {
                System.out.println(":||Remove get guild " + jda.getStatus() + " " + (((-start)) + (start = System.currentTimeMillis())));
                this.server = rootGuild;
                System.out.println("remove:|| init db " + (((-start)) + (start = System.currentTimeMillis())));
            } else {
                throw new IllegalStateException("Invalid guild: " + Settings.INSTANCE.ROOT_SERVER + " as `root-server` in " + Settings.INSTANCE.getDefaultFile().getAbsolutePath());
            }

            System.out.println(":||Remove ready " + jda.getStatus() + " " + (((-start)) + (start = System.currentTimeMillis())));



            System.out.println(":||Remove init db 2 " + jda.getStatus() + " " + (((-start)) + (start = System.currentTimeMillis())));

            if (Settings.INSTANCE.FORUM_FEED_SERVER > 0) {
                Guild forumGuild = getDiscordApi().getGuildById(Settings.INSTANCE.FORUM_FEED_SERVER);
                if (forumGuild == null) throw new IllegalStateException("Invalid guild: " + Settings.INSTANCE.FORUM_FEED_SERVER + " as FORUM_FEED_SERVER in " + Settings.INSTANCE.getDefaultFile());
                this.forumDb = new ForumDB(forumGuild);
            }

            System.out.println("remove:|| init forum db " + (((-start)) + (start = System.currentTimeMillis())));

            if (Settings.INSTANCE.ENABLED_COMPONENTS.REPEATING_TASKS) {
                initRepeatingTasks();
            }

            System.out.println("remove:|| init repeating tasks " + (((-start)) + (start = System.currentTimeMillis())));

            for (long guildId : Settings.INSTANCE.MODERATION.BANNED_GUILDS) {
                Guild guild = getDiscordApi().getGuildById(guildId);
                if (guild != null) {
                    link.locutus.discord.util.RateLimitUtil.queue(guild.leave());
                }
            }

            System.out.println("remove:|| banned guilds " + (((-start)) + (start = System.currentTimeMillis())));

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
            System.out.println("remove:|| banned alliances " + (((-start)) + (start = System.currentTimeMillis())));

            // load members
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
                            System.out.println("Done loading guild members");
                            return;
                        }
                        Guild guild = db.getGuild();
                        System.out.println(":||Remove Loading members for " + guild);
                        guild.loadMembers().onSuccess(f -> {
                            System.out.println("Loaded " + f.size() + " members for " + guild);
                            queueFunc[0].run();
                        }).onError(f -> {
                            System.out.println("Failed to load members for " + guild);
                            queueFunc[0].run();
                        });
                    }
                };
                queueFunc[0].run();
            }
        }

        if (Settings.INSTANCE.ENABLED_COMPONENTS.WEB && (Settings.INSTANCE.WEB.PORT_HTTP > 0 || Settings.INSTANCE.WEB.PORT_HTTPS > 0)) {
            try {
                new WebRoot(Settings.INSTANCE.WEB.PORT_HTTP, Settings.INSTANCE.WEB.PORT_HTTPS);
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
        if (Settings.INSTANCE.ENABLED_COMPONENTS.REPEATING_TASKS && Settings.INSTANCE.ENABLED_COMPONENTS.SUBSCRIPTIONS) {
            this.pusher = new PnwPusherShardManager();
            executor.submit(new Runnable() {
                @Override
                public void run() {
                    System.out.println("Loading pusher");
                    pusher.load();
                    System.out.println("Loaded pusher");
                    pusher.subscribeDefaultEvents();
                    System.out.println("Subscribed to default events");
                }
            });
        }

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

    private void backup() {
            int turnsCheck = Settings.INSTANCE.BACKUP.TURNS;
            String script = Settings.INSTANCE.BACKUP.SCRIPT;
        try {
            Backup.backup(script, turnsCheck);
        } catch (IOException e) {
            e.printStackTrace();
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
        Auth auth = getNationDB().getNation(Settings.INSTANCE.NATION_ID).getAuth(true);
        if (auth != null) auth.setApiKey(Settings.INSTANCE.API_KEY_PRIMARY);
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
            } catch (SQLException | ClassNotFoundException e) {
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
        return slashCommands;
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

    public TradeManager getTradeManager() {
        this.tradeManager.load();
        return tradeManager;
    }

    public CommandManager getCommandManager() {
        return commandManager;
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

    public void runEventsAsync(ThrowingConsumer<Consumer<Event>> eventHandler) {
        ArrayDeque<Event> events = new ArrayDeque<>();
        eventHandler.accept(events::add);
        runEventsAsync(events);
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
        if ((Settings.INSTANCE.TASKS.ACTIVE_NATION_SECONDS > 0 || Settings.INSTANCE.TASKS.COLORED_NATIONS_SECONDS > 0 || Settings.INSTANCE.TASKS.ALL_NON_VM_NATIONS_SECONDS > 0) && nationDB.getNations().isEmpty()) {
            logger.info("No nations found. Updating all nations");
            if (Settings.USE_V2) {
                nationDB.updateNationsV2(true, null);
            } else {
                nationDB.updateAllNations(null);
            }
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
            addTaskSeconds(new CaughtTask() {
                @Override
                public void runUnsafe() throws Exception {
                    NationUpdateProcessor.onActivityCheck();
                }
            }, 60);
        }
        if (Settings.USE_V2) {
            addTaskSeconds(() -> {
                runEventsAsync(events -> nationDB.updateNationsV2(false, events));
            }, Settings.INSTANCE.TASKS.COLORED_NATIONS_SECONDS);

            addTaskSeconds(() -> {
                runEventsAsync(events -> nationDB.updateNationsV2(true, events));
            }, Settings.INSTANCE.TASKS.ALL_NON_VM_NATIONS_SECONDS);

            addTaskSeconds(() -> {
                runEventsAsync(events -> nationDB.updateCitiesV2(events));
            }, Settings.INSTANCE.TASKS.ALL_NON_VM_NATIONS_SECONDS);

            addTaskSeconds(() -> {
                synchronized (warUpdateLock)
                {
                    System.out.println("Start update wars 1");
                    long start = System.currentTimeMillis();
                    runEventsAsync(warDb::updateAllWarsV2);
                    System.out.println("Update wars 1.1 took " + ( - start + (start = System.currentTimeMillis())));
                    runEventsAsync(e -> warDb.updateAttacks(true, e, true));
                    System.out.println("Update wars 1.2 took " + ( - start + (start = System.currentTimeMillis())));
                }
            }, Settings.INSTANCE.TASKS.ALL_WAR_SECONDS);

        } else {
            addTaskSeconds(() -> {
                runEventsAsync(events -> nationDB.updateMostActiveNations(490, events));
            }, Settings.INSTANCE.TASKS.ACTIVE_NATION_SECONDS);

            addTaskSeconds(() -> {
                runEventsAsync(nationDB::updateTreaties);
            }, Settings.INSTANCE.TASKS.TREATY_UPDATE_SECONDS);

            addTaskSeconds(() -> {
                runEventsAsync(f -> bankDb.updateBankRecs(false, f));
            }, Settings.INSTANCE.TASKS.BANK_RECORDS_INTERVAL_SECONDS);

            addTaskSeconds(() -> {
                runEventsAsync(nationDB::updateRecentNations);
            }, Settings.INSTANCE.TASKS.COLORED_NATIONS_SECONDS);

            addTaskSeconds(() -> {
                runEventsAsync(events -> nationDB.updateNonVMNations(events));
            }, Settings.INSTANCE.TASKS.ALL_NON_VM_NATIONS_SECONDS);

            addTaskSeconds(() -> {
                runEventsAsync(f -> nationDB.updateDirtyCities(false, f));
            }, Settings.INSTANCE.TASKS.OUTDATED_CITIES_SECONDS);

            if (Settings.INSTANCE.TASKS.FETCH_SPIES_INTERVAL_SECONDS > 0) {
                SpyUpdater spyUpdate = new SpyUpdater();
                addTaskSeconds(() -> {
                    spyUpdate.run();
                }, Settings.INSTANCE.TASKS.FETCH_SPIES_INTERVAL_SECONDS);
            }

            addTaskSeconds(() -> {
                try {
                    System.out.println("Start update wars 1");
                    synchronized (warUpdateLock) {
                        System.out.println("Start update wars 1.1");
                        long start = System.currentTimeMillis();
                        System.out.println("Start update wars 1.2");
                        runEventsAsync(f -> warDb.updateActiveWars(f, false));
                        System.out.println("Start update wars 1.3");
                        runEventsAsync(warDb::fetchNewWars);
                        System.out.println("Start update wars 1.4");
                        runEventsAsync(warDb::updateAttacks);
                        System.out.println("Start update wars 1.5");
                    }
                    System.out.println("End update wars 1.5");
                } catch (Throwable e) {
                    e.printStackTrace();
                }

            }, Settings.INSTANCE.TASKS.ACTIVE_WAR_SECONDS);

//            addTaskSeconds(() -> {
//                synchronized (warUpdateLock) {
//                    System.out.println("Start update wars");
//                    long start1 = System.currentTimeMillis();
//                    runEventsAsync(warDb::updateAllWars);
//                    runEventsAsync(warDb::updateAttacks);
//                    long diff1 = System.currentTimeMillis() - start1;
//                    {
//                        System.out.println("Update wars took " + diff1);
//                    }
//
//                    if (Settings.INSTANCE.TASKS.ESCALATION_ALERTS) {
//                        long start = System.currentTimeMillis();
//                        WarUpdateProcessor.checkActiveConflicts();
//                        long diff = System.currentTimeMillis() - start;
//                        if (diff > 500) {
//                            AlertUtil.error("Took too long for checkActiveConflicts (" + diff + "ms)", new Exception());
//                        }
//                    }
//                }
//            }, Settings.INSTANCE.TASKS.ALL_WAR_SECONDS);

            checkMailTasks();

            if (Settings.INSTANCE.TASKS.BOUNTY_UPDATE_SECONDS > 0) {
                addTaskSeconds(() -> Locutus.imp().getWarDb().updateBountiesV3(), Settings.INSTANCE.TASKS.BOUNTY_UPDATE_SECONDS);
            }
            if (Settings.INSTANCE.TASKS.TREASURE_UPDATE_SECONDS > 0) {
                addTaskSeconds(() -> {
                    runEventsAsync(Locutus.imp().getNationDB()::updateTreasures);
                }, Settings.INSTANCE.TASKS.TREASURE_UPDATE_SECONDS);
            }

            if (Settings.INSTANCE.TASKS.BASEBALL_SECONDS > 0) {
                addTaskSeconds(() -> {
                    runEventsAsync(getBaseballDB()::updateGames);
                }, Settings.INSTANCE.TASKS.BASEBALL_SECONDS);
            }

            if (Settings.INSTANCE.TASKS.COMPLETED_TRADES_SECONDS > 0) {
                addTaskSeconds(() -> {
                            runEventsAsync(getTradeManager()::updateTradeList);
                        },
                        Settings.INSTANCE.TASKS.COMPLETED_TRADES_SECONDS);
            }

            if (Settings.INSTANCE.TASKS.NATION_DISCORD_SECONDS > 0) {
                addTask(() ->
                                Locutus.imp().getDiscordDB().updateUserIdsSince(Settings.INSTANCE.TASKS.NATION_DISCORD_SECONDS, false),
                        Settings.INSTANCE.TASKS.NATION_DISCORD_SECONDS, TimeUnit.SECONDS);
            }
        }

        if (Settings.INSTANCE.TASKS.BEIGE_REMINDER_SECONDS > 0) {
            LeavingBeigeAlert beigeAlerter = new LeavingBeigeAlert();

            addTaskSeconds(beigeAlerter::run, Settings.INSTANCE.TASKS.BEIGE_REMINDER_SECONDS);
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
                    runEventsAsync(events -> nationDB.updateNonVMNations(events));
                }
                {
                    runEventsAsync(events -> nationDB.updateMostActiveNations(490, events));
                }
                {
                    runEventsAsync(events -> nationDB.updateAlliances(null, events));
                }

                runEventsAsync(nationDB::deleteExpiredTreaties);
                runEventsAsync(nationDB::updateTreaties);
                runEventsAsync(nationDB::updateBans);

                nationDB.saveAllCities(); // TODO save all cities


                tradeManager.updateColorBlocs(); // TODO move to configurable task
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
                commandManager.getExecutor().scheduleWithFixedDelay(alertMailTask, 60, task.FETCH_INTERVAL_SECONDS, TimeUnit.SECONDS);
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

    public PoliticsAndWarV2 getPnwApiV2() {
        return pnwApi;
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
        System.out.println(":||Remove load members guild join");
        event.getGuild().loadMembers();
    }

    @Override
    public void onGuildAvailable(@NotNull GuildAvailableEvent event) {
        System.out.println(":||Remove load members guild available");
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
            Guild guild = event.getGuild();
            GuildDB db = getGuildDB(guild);

            DBNation nation = DiscordUtil.getNation(event.getUser());
            AutoRoleInfo task = db.getAutoRoleTask().autoRole(event.getMember(), nation);
            task.execute();
            db.getHandler().onGuildMemberJoin(event);

            eventBus.post(event);
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

            try {
                for (Map.Entry<String, String> entry : IModalBuilder.DEFAULT_VALUES.get(uuid).entrySet()) {
                    args.putIfAbsent(entry.getKey(), entry.getValue());
                }
            } catch (ExecutionException e) {
                e.printStackTrace();
            }

            for (ModalMapping value : values) {
                Component.Type type = value.getType();
                String valueId = value.getId();
                String input = value.getAsString();
                args.put(valueId, input);
            }

            DiscordHookIO io = new DiscordHookIO(hook, null);

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
            // Only process locutus buttons

            Button button = event.getButton();
            System.out.println("Button press " + button.getId() + " | " + button.getLabel());

            if (message.getAuthor().getIdLong() != Settings.INSTANCE.APPLICATION_ID) {
                System.out.println("Author not application");
                return;
            }


            User user = event.getUser();
            Guild guild = event.isFromGuild() ? event.getGuild() : message.isFromGuild() ? message.getGuild() : null;
            MessageChannel channel = event.getChannel();
            List<MessageEmbed> embeds = message.getEmbeds();
            MessageEmbed embed = !embeds.isEmpty() ? embeds.get(0) : null;

            InteractionHook hook = event.getHook();
            IMessageIO io = new DiscordHookIO(hook, event);
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
                    }

                    if (!id.isEmpty() && (id.startsWith(Settings.commandPrefix(true)) || commandManager.isModernPrefix(id.charAt(0)))) {
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
                io.send(e.getMessage());
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
            commandManager.run(guild, io, author, message, true, true);
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
        if (event.getUser().getIdLong() == Settings.INSTANCE.ADMIN_USER_ID) {
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
            cmdObject = commandManager.getCommandMap().get(cmdLabel.toLowerCase());
            if (cmdObject == null) {
                return false;
            }
        }
        System.out.println("CMD1 " + cmd);
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
        System.out.println("Run " + io.getClass());
        commandManager.run(guild, io, user, cmd, async, false);
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

        String[] split = raw.split("\\r?\\n(?=[" + StringMan.join(commandManager.getAllPrefixes(), "|") + "|{])");
        System.out.println("Split " + StringMan.getString(split));
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
        return this.bankDb;
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
            if (commandManager != null) commandManager.getExecutor().shutdownNow();

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

            System.out.println("\n == Ignore the following if the thread doesn't relate to anything modifying persistent data");
            for (Map.Entry<Thread, StackTraceElement[]> thread : Thread.getAllStackTraces().entrySet()) {
                System.out.println("Thread did not close after 5s: " + thread.getKey() + "\n- " + StringMan.stacktraceToString(thread.getValue()));
            }

            System.exit(1);
        }
    }
}
