package link.locutus.discord._main;

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kobylynskyi.graphql.codegen.model.graphql.GraphQLRequestSerializer;
import link.locutus.discord.Locutus;
import link.locutus.discord.Logg;
import link.locutus.discord.apiv1.PoliticsAndWarBuilder;
import link.locutus.discord.apiv1.core.ApiKeyPool;
import link.locutus.discord.apiv2.PoliticsAndWarV2;
import link.locutus.discord.apiv3.PoliticsAndWarV3;
import link.locutus.discord.commands.manager.CommandManager;
import link.locutus.discord.commands.manager.v2.impl.SlashCommandManager;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.commands.stock.StockDB;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.*;
import link.locutus.discord.pnw.PNWUser;
import link.locutus.discord.util.FileUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.discord.GuildShardManager;
import link.locutus.discord.util.offshore.Auth;
import link.locutus.discord.util.scheduler.ThrowingSupplier;
import link.locutus.discord.util.trade.TradeManager;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.sharding.DefaultShardManager;
import net.dv8tion.jda.api.sharding.DefaultShardManagerBuilder;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.Compression;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.jetbrains.annotations.Nullable;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class PreLoader implements ILoader {
    private final ThreadPoolExecutor executor;
    private final ScheduledThreadPoolExecutor scheduler;
    private final Semaphore semaphore;
    private final Locutus locutus;
    // futures
    private final Map<String, Future<?>> resolvers;
    private final Map<String, Thread> resolverThreads;
    private final AtomicInteger numTasks = new AtomicInteger(0);
    private final boolean awaitBackup;

    private volatile FinalizedLoader finalized;
    // fields
    private final Future<SlashCommandManager> slashCommandManager;
    private final Future<JDA> jda;

    private final Future<ForumDB> forumDb;
    private final Future<DiscordDB> discordDB;
    private final Future<NationDB> nationDB;
    private final Future<WarDB> warDb;
    private final Future<StockDB> stockDB;
    private final Future<BankDB> bankDb;
    private final Future<TradeManager> tradeManager;
    private final Future<CommandManager> commandManager;
    private final Future<Supplier<String>> getApiKeyPrimary;
    private final Future<Supplier<Integer>> getNationId;
    private final Future<Supplier<Long>> adminUserId;

    private final Future<PoliticsAndWarV2> apiV2;

    private final Future<PoliticsAndWarV3> apiV3;

    private final Future<PoliticsAndWarV3> apiV3Pool;

    private final Future<Boolean> backup;

    public PreLoader(Locutus locutus, ThreadPoolExecutor executor, ScheduledThreadPoolExecutor scheduler) {
        this.executor = executor;
        this.scheduler = scheduler;
        this.semaphore = new Semaphore(0);

        this.resolvers = new ConcurrentHashMap<>();
        this.resolverThreads = new ConcurrentHashMap<>();
        this.locutus = locutus;

        this.slashCommandManager = add("Slash Command Manager", new ThrowingSupplier<SlashCommandManager>() {
            @Override
            public SlashCommandManager getThrows() throws Exception {
                return new SlashCommandManager(Settings.INSTANCE.ENABLED_COMPONENTS.REGISTER_ADMIN_SLASH_COMMANDS, () -> Locutus.cmd().getV2());
            }
        }, false);
        this.jda = add("Discord Hook", this::buildJDA, false);
        this.awaitBackup = !Settings.INSTANCE.BACKUP.SCRIPT.isEmpty();
        if (awaitBackup) {
            backup = add("Backup", () -> {
                Backup.backup();
                return true;
            }, false);
        } else {
            backup = CompletableFuture.completedFuture(false);
        }

        this.discordDB = add("Discord Database", DiscordDB::new);
        this.nationDB = add("Nation Database", () -> new NationDB().load());
        add("Flag Outdated Cities", () -> {
            getNationDB().markDirtyIncorrectNations();
            return null;
        });
        add("Initialize Nuke Dates", () -> {
            getNationDB().loadNukeDatesIfEmpty();
            return null;
        });
        add("Create Default Exchanges", () -> {
            getStockDB().createDefaultExchanges();
            return null;
        });

        this.warDb = add("War Database", () -> new WarDB().load());
        this.stockDB = add("Stock Database", StockDB::new);
        this.bankDb = add("Bank Database", BankDB::new);
        this.tradeManager = add("Trade Database", () -> new TradeManager().load());
        if (Settings.INSTANCE.FORUM_FEED_SERVER > 0) {
            this.forumDb = add("Forum Database", () -> new ForumDB(Settings.INSTANCE.FORUM_FEED_SERVER));
        } else {
            forumDb = CompletableFuture.completedFuture(null);
        }
        this.commandManager = add("Command Handler", () -> new CommandManager(scheduler));

        if (Settings.INSTANCE.API_KEY_PRIMARY.isEmpty()) {
            Auth auth = new Auth(0, Settings.INSTANCE.USERNAME, Settings.INSTANCE.PASSWORD);
            getApiKeyPrimary = add("Fetch API Key", () -> {
                ApiKeyPool.ApiKey key = auth.fetchApiKey();
                Settings.INSTANCE.API_KEY_PRIMARY = key.getKey();
                return () -> Settings.INSTANCE.API_KEY_PRIMARY;
            });
        } else {
            getApiKeyPrimary = CompletableFuture.completedFuture(() -> Settings.INSTANCE.API_KEY_PRIMARY);
        }
        if (Settings.INSTANCE.NATION_ID <= 0) {
            Settings.INSTANCE.NATION_ID = 0;
            this.getNationId = add("Fetch Nation ID", new ThrowingSupplier<Supplier<Integer>>() {
                @Override
                public Supplier<Integer> getThrows() throws Exception {
                    String apiKey = getApiKey();
                    Integer nationIdFromKey = getDiscordDB().getNationFromApiKey(apiKey);
                    if (nationIdFromKey == null) {
                        Settings.INSTANCE.NATION_ID = -1;
                    } else {
                        Settings.INSTANCE.NATION_ID = nationIdFromKey;
                    }
                    return () -> Settings.INSTANCE.NATION_ID;
                }
            });
        } else {
            this.getNationId = CompletableFuture.completedFuture(() -> Settings.INSTANCE.NATION_ID);
        }
        if (Settings.INSTANCE.ADMIN_USER_ID <= 0) {
            this.adminUserId = add("Discord Admin User ID", new ThrowingSupplier<Supplier<Long>>() {
                @Override
                public Supplier<Long> getThrows() throws Exception {
                    int nationId = getNationId();
                    PNWUser adminPnwUser = getDiscordDB().getUserFromNationId(nationId);
                    if (adminPnwUser != null) {
                        Settings.INSTANCE.ADMIN_USER_ID = adminPnwUser.getDiscordId();
                    }
                    return () -> Settings.INSTANCE.ADMIN_USER_ID;
                }
            });
        } else {
            this.adminUserId = CompletableFuture.completedFuture(() -> Settings.INSTANCE.ADMIN_USER_ID);
        }
        if (Settings.INSTANCE.CONVERSION_SECRET.equalsIgnoreCase("TODO") || Settings.INSTANCE.CONVERSION_SECRET.equalsIgnoreCase("some-keyword")) {
            Settings.INSTANCE.CONVERSION_SECRET = UUID.randomUUID().toString();
        }
        this.apiV2 = add("PW-API V2", () -> {
            List<String> pool = new ArrayList<>();
            pool.addAll(Settings.INSTANCE.API_KEY_POOL);
            if (pool.isEmpty()) {
                pool.add(Settings.INSTANCE.API_KEY_PRIMARY);
            }
            return new PoliticsAndWarBuilder().addApiKeys(pool.toArray(new String[0])).setEnableCache(false).setTestServerMode(Settings.INSTANCE.TEST).build();
        });
        this.apiV3 = add("PW-API V3", () -> {
            GraphQLRequestSerializer.OBJECT_MAPPER.registerModule(new JavaTimeModule());
            ApiKeyPool v3Pool = ApiKeyPool.builder()
                    .addKey(Settings.INSTANCE.NATION_ID,
                            Settings.INSTANCE.API_KEY_PRIMARY,
                            Settings.INSTANCE.ACCESS_KEY)
                    .build();
            return new PoliticsAndWarV3(v3Pool);
        });

        this.apiV3Pool = add("PW-API V3 Pool", () -> {
            GraphQLRequestSerializer.OBJECT_MAPPER.registerModule(new JavaTimeModule());
            Map<Integer, ApiKeyPool.ApiKey> keys = getDiscordDB().getApiKeys(true, true, 50);
            if (keys.isEmpty()) {
                keys.put(Settings.INSTANCE.NATION_ID, new ApiKeyPool.ApiKey(Settings.INSTANCE.NATION_ID, Settings.INSTANCE.API_KEY_PRIMARY, null));
            }
            ApiKeyPool v3Pool = ApiKeyPool.builder()
                    .addKey(Settings.INSTANCE.NATION_ID,
                            Settings.INSTANCE.API_KEY_PRIMARY,
                            Settings.INSTANCE.ACCESS_KEY)
                    .build();
            return new PoliticsAndWarV3(v3Pool);
        });

        add("Register Discord Commands", () -> {
            CommandManager cmdMan = getCommandManager();
            CommandManager2 v2 = cmdMan.getV2();
            v2.registerDefaults();
            DiscordDB db = getDiscordDB();
            cmdMan.registerCommands(db);
            return null;
        });
        setupMonitor();
    }

    @Nullable
    @Override
    public NationDB getCachedNationDB() {
        if (this.nationDB == null) return null;
        if (this.finalized != null) {
            return this.finalized.getNationDB();
        }
        if (this.nationDB.isDone()) {
            try {
                return this.nationDB.get();
            } catch (InterruptedException | ExecutionException e) {
                Logg.text("Failed to get NationDB: " + e.getMessage());
                return null;
            }
        }
        return null;
    }

    private void setupMonitor() {
        Logg.text("Initializing Startup Monitor");
        scheduler.schedule(() -> {
            List<String> deadlocks = detectDeadlock(resolverThreads.values().stream().map(Thread::getId).collect(Collectors.toSet()));
            if (!deadlocks.isEmpty()) {
                Logg.text("\n[Startup Monitor] " + deadlocks.size() + " Deadlocks detected\n" + deadlocks + "\n");
            } else {
                String stacktrace = printStacktrace();
                if (!stacktrace.isEmpty()) {
                    Logg.text("\n[Startup Monitor] Initializing the bot taking longer than expected, but is not hung (120s):\n" + stacktrace + "\n");
                } else {
                    Logg.text("\n[Startup Monitor] Detected no hung or incomplete startup threads (they either completed successfully or completed with errors)");
                }
            }
        }, 120, TimeUnit.SECONDS);
    }

    public static List<String> detectDeadlock(Set<Long> threadIds) {
        List<String> results = new ArrayList<>();
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        long[] deadlockedThreadIds = threadMXBean.findDeadlockedThreads();
        if (deadlockedThreadIds != null) {
            ThreadInfo[] threadInfos = threadMXBean.getThreadInfo(deadlockedThreadIds, true, true);
            List<ThreadInfo> filteredThreadInfos = filterThreadInfos(threadInfos, threadIds);
            for (ThreadInfo threadInfo : filteredThreadInfos) {
                StringBuilder info = new StringBuilder("Deadlocked thread: " + threadInfo.getThreadName() + ": " + threadInfo.getThreadState() + "\n");
                info.append("Blocked: ").append(threadInfo.getBlockedTime()).append("/").append(threadInfo.getBlockedCount()).append("\n");
                info.append("Waited: ").append(threadInfo.getWaitedTime()).append("/").append(threadInfo.getWaitedCount()).append("\n");
                info.append("Lock: ").append(threadInfo.getLockName()).append("/").append(threadInfo.getLockOwnerId()).append("/").append(threadInfo.getLockOwnerName()).append("\n");
                for (StackTraceElement ste : threadInfo.getStackTrace()) {
                    info.append("\t").append(ste).append("\n");
                }
                results.add(info.toString());
            }
        }
        return results;
    }

    private static List<ThreadInfo> filterThreadInfos(ThreadInfo[] threadInfos, Set<Long> threadIds) {
        return List.of(threadInfos).stream()
                .filter(threadInfo -> threadIds.contains(threadInfo.getThreadId()))
                .collect(Collectors.toList());
    }

    @Override
    public String getApiKey() {
        return FileUtil.get(getApiKeyPrimary).get();
    }

    @Override
    public int getNationId() {
        return FileUtil.get(getNationId).get();
    }
    @Override
    public long getAdminUserId() {
        return FileUtil.get(adminUserId).get();
    }

    @Override
    public ILoader resolveFully(long timeout) {
        Set<Map.Entry<String, Future<?>>> tmp = resolvers.entrySet();
        if (finalized != null) return finalized;
        synchronized (this) {
            if (finalized != null) {
                return  finalized;
            }
            for (Map.Entry<String, Future<?>> resolver : tmp) {
                String taskName = resolver.getKey();
                Future<?> future = resolver.getValue();
                try {
                    future.get(timeout, TimeUnit.MILLISECONDS);
                } catch (Exception e) {
                    Logg.text("Failed to resolve `TASK:" + taskName + "`: " + e.getMessage());
                    throw new RuntimeException(e);
                }
            }
            resolvers.clear();
            this.finalized = new FinalizedLoader(this);
            locutus.setLoader(finalized);
            return finalized;
        }
    }

    private <T> Future<T> add(String taskName, ThrowingSupplier<T> supplier) {
        return add(taskName, supplier, true);
    }

    private <T> Future<T> add(String taskName, ThrowingSupplier<T> supplier, boolean wait) {
        if (resolvers.containsKey(taskName)) {
            throw new IllegalArgumentException("Duplicate task: " + taskName);
        }
        Future<T> future = executor.submit(() -> {
            try {
                Thread thread = Thread.currentThread();
                thread.setName("Load-" + taskName);
                resolverThreads.put(taskName, thread);
                numTasks.incrementAndGet();

                if (wait) semaphore.acquire();
                Logg.text("Loading `" + taskName + "`");
                long start = System.currentTimeMillis();
                T result = supplier.get();
                long end = System.currentTimeMillis();
                if (end - start > 15 || true) {
                    int completed = numTasks.get() - resolverThreads.size() + 1;
                    Logg.text("Completed " + completed + "/" + numTasks + ": `" + taskName + "` in " + MathMan.format((end - start) / 1000d) + "s");
                }
                return result;
            } catch (Throwable e) {
                e.printStackTrace();
                Logg.text("Failed to load `TASK:" + taskName + "`: " + e.getMessage());
                throw e;
            } finally {
                resolverThreads.remove(taskName);
            }
        });
        resolvers.put(taskName, future);
        return future;
    }

    @Override
    public void initialize() {
        if (awaitBackup) {
            FileUtil.get(backup);
        }
        semaphore.release(Integer.MAX_VALUE);
    }

    @Override
    public String printStacktrace() {
        if (resolverThreads.isEmpty()) return "";
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, Thread> entry : resolverThreads.entrySet()) {
            builder.append(printStacktrace(entry.getValue()));
        }
        return builder.toString();
    }

    private String printStacktrace(Thread thread) {
        StringBuilder builder = new StringBuilder();
        builder.append("Thread ").append(thread.getName()).append("/").append(thread.getState()).append("\n");
        for (StackTraceElement element : thread.getStackTrace()) {
            builder.append("\tat ").append(element).append("\n");
        }
        return builder.toString();
    }

    private GuildShardManager build() {
        if (Settings.INSTANCE.SHARDS > 1) {
            try {
                return new GuildShardManager(buildShardManager());
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        } else {
            try {
                return new GuildShardManager(buildJDA());
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private JDA buildJDA() throws ExecutionException, InterruptedException {
        JDABuilder builder = JDABuilder.createLight(Settings.INSTANCE.BOT_TOKEN, GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_MESSAGES, GatewayIntent.DIRECT_MESSAGES);
        if (Settings.INSTANCE.ENABLED_COMPONENTS.SLASH_COMMANDS) {
            SlashCommandManager slash = getSlashCommandManager();
            if (slash != null) {
                builder.addEventListeners(slash);
            }
        }
        if (Settings.INSTANCE.ENABLED_COMPONENTS.MESSAGE_COMMANDS) {
            builder.addEventListeners(locutus);
        }
        builder
                .setChunkingFilter(ChunkingFilter.NONE)
                .setBulkDeleteSplittingEnabled(false)
                .setCompression(Compression.ZLIB)
                .setLargeThreshold(250)
                .setAutoReconnect(true)
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
            builder.enableIntents(GatewayIntent.GUILD_EXPRESSIONS);
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
        return builder.build();
    }

    private DefaultShardManager buildShardManager() throws ExecutionException, InterruptedException {
        DefaultShardManagerBuilder builder = DefaultShardManagerBuilder.createLight(Settings.INSTANCE.BOT_TOKEN, GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_MESSAGES, GatewayIntent.DIRECT_MESSAGES);
        if (Settings.INSTANCE.ENABLED_COMPONENTS.SLASH_COMMANDS) {
            SlashCommandManager slash = getSlashCommandManager();
            if (slash != null) {
                builder.addEventListeners(slash);
            }
        }
        if (Settings.INSTANCE.ENABLED_COMPONENTS.MESSAGE_COMMANDS) {
            builder.addEventListeners(locutus);
        }
        builder
                .setChunkingFilter(ChunkingFilter.NONE)
                .setBulkDeleteSplittingEnabled(false)
                .setCompression(Compression.ZLIB)
                .setLargeThreshold(250)
                .setAutoReconnect(true)
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
            builder.enableIntents(GatewayIntent.GUILD_EXPRESSIONS);
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
        return builder
                .setShardsTotal(Settings.INSTANCE.DISCORD.SHARDS)
                .setShards(0, Settings.INSTANCE.DISCORD.SHARDS - 1)
                .build();
    }

    @Override
    public SlashCommandManager getSlashCommandManager() {
        return FileUtil.get(slashCommandManager);
    }

    @Override
    public JDA getJda() {
        return FileUtil.get(jda);
    }

    @Override
    public ForumDB getForumDB() {
        return FileUtil.get(forumDb);
    }

    @Override
    public DiscordDB getDiscordDB() {
        return FileUtil.get(discordDB);
    }

    @Override
    public NationDB getNationDB() {
        return FileUtil.get(nationDB);
    }

    @Override
    public WarDB getWarDB() {
        return FileUtil.get(warDb);
    }

    @Override
    public BaseballDB getBaseballDB() {
        return resolveFully(Long.MAX_VALUE).getBaseballDB();
    }

    @Override
    public StockDB getStockDB() {
        return FileUtil.get(stockDB);
    }

    @Override
    public BankDB getBankDB() {
        return FileUtil.get(bankDb);
    }

    @Override
    public TradeManager getTradeManager() {
        return FileUtil.get(tradeManager);
    }

    @Override
    public CommandManager getCommandManager() {
        return FileUtil.get(commandManager);
    }

    @Override
    public PoliticsAndWarV2 getApiV2() {
        return FileUtil.get(apiV2);
    }

    @Override
    public PoliticsAndWarV3 getApiV3() {
        return FileUtil.get(apiV3);
    }

    @Override
    public PoliticsAndWarV3 getApiPool() {
        return FileUtil.get(apiV3Pool);
    }
}
