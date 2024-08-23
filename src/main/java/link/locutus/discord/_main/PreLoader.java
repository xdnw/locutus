package link.locutus.discord._main;

import link.locutus.discord.Locutus;
import link.locutus.discord.Logg;
import link.locutus.discord.apiv1.PoliticsAndWarBuilder;
import link.locutus.discord.apiv1.core.ApiKeyPool;
import link.locutus.discord.apiv2.PoliticsAndWarV2;
import link.locutus.discord.apiv3.PoliticsAndWarV3;
import link.locutus.discord.apiv3.subscription.PnwPusherShardManager;
import link.locutus.discord.commands.manager.CommandManager;
import link.locutus.discord.commands.manager.v2.impl.SlashCommandManager;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.commands.stock.StockDB;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.*;
import link.locutus.discord.db.handlers.GuildCustomMessageHandler;
import link.locutus.discord.pnw.PNWUser;
import link.locutus.discord.util.FileUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.offshore.Auth;
import link.locutus.discord.util.scheduler.ThrowingSupplier;
import link.locutus.discord.util.trade.TradeManager;
import link.locutus.discord.web.jooby.WebRoot;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.Compression;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public class PreLoader implements ILoader {
    private final ExecutorService executor;
    private final Locutus locutus;
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
    // futures
    private List<Future<?>> resolvers;
    private volatile FinalizedLoader finalized;
    // fields
    private final Future<SlashCommandManager> slashCommandManager;
    private final Future<JDA> jda;

    public PreLoader(Locutus locutus, ExecutorService executor) {
        this.executor = executor;
        this.resolvers = new ArrayList<>();
        this.locutus = locutus;

        // todo fixme remove calls of Locutus.imp()

        this.slashCommandManager = add("Slash Command Manager", new ThrowingSupplier<SlashCommandManager>() {
            @Override
            public SlashCommandManager getThrows() throws Exception {
                return new SlashCommandManager(Settings.INSTANCE.ENABLED_COMPONENTS.REGISTER_ADMIN_SLASH_COMMANDS, () -> Locutus.cmd().getV2());
            }
        });
        this.jda = add("Discord Hook", this::buildJDA);
        this.discordDB = add("Discord Database", () -> new DiscordDB());
        this.nationDB = add("Nation Database", () -> new NationDB().load());
        this.warDb = add("War Database", () -> new WarDB().load());
        this.stockDB = add("Stock Database", () -> new StockDB());
        this.bankDb = add("Bank Database", () -> new BankDB());
        this.tradeManager = add("Trade Database", () -> new TradeManager().load());
        if (Settings.INSTANCE.FORUM_FEED_SERVER > 0) {
            this.forumDb = add("Forum Database", () -> new ForumDB(Settings.INSTANCE.FORUM_FEED_SERVER));
        } else {
            forumDb = CompletableFuture.completedFuture(null);
        }
        this.commandManager = add("Command Handler", () -> new CommandManager(locutus));

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
                    Integer nationIdFromKey = getDiscordDB().getNationFromApiKey(Settings.INSTANCE.API_KEY_PRIMARY);
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
                    PNWUser adminPnwUser = getDiscordDB().getUserFromNationId(Settings.INSTANCE.NATION_ID);
                    if (adminPnwUser != null) {
                        Settings.INSTANCE.ADMIN_USER_ID = adminPnwUser.getDiscordId();
                    }
                    return () -> Settings.INSTANCE.ADMIN_USER_ID;
                }
            });
        } else {
            this.adminUserId = CompletableFuture.completedFuture(() -> Settings.INSTANCE.ADMIN_USER_ID);
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
    public ILoader resolveFully() {
        List<Future<?>> tmp = resolvers;
        if (finalized != null) return finalized;
        synchronized (this) {
            if (finalized != null) {
                return  finalized;
            }
            for (Future<?> resolver : tmp) {
                try {
                    resolver.get();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            resolvers = null;
            this.finalized = new FinalizedLoader(this);
            locutus.setLoader(finalized);
            return finalized;
        }
    }

    private <T> Future<T> add(String taskName, ThrowingSupplier<T> supplier) {
        Future<T> future = executor.submit(new Callable<T>() {
            @Override
            public T call() throws Exception {
                try {
                    Logg.text("Loading `TASK:" + taskName + "`");
                    long start = System.currentTimeMillis();
                    T result = supplier.get();
                    long end = System.currentTimeMillis();
                    if (end - start > 15 || true) {
                        Logg.text("Completed `TASK:" + taskName + "` in " + MathMan.format((end - start) / 1000d) + "s");
                    }

                    return result;
                } catch (Throwable e) {
                    e.printStackTrace();
                    throw e;
                }
            }
        });
        resolvers.add(future);
        return future;
    }

    private JDA buildJDA() throws ExecutionException, InterruptedException {
        JDABuilder builder = JDABuilder.createLight(Settings.INSTANCE.BOT_TOKEN, GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_MESSAGES, GatewayIntent.DIRECT_MESSAGES);
        if (Settings.INSTANCE.ENABLED_COMPONENTS.SLASH_COMMANDS) {
            builder.addEventListeners(slashCommandManager.get());
        }
        if (Settings.INSTANCE.ENABLED_COMPONENTS.MESSAGE_COMMANDS) {
            builder.addEventListeners(this);
        }
        builder
                .setChunkingFilter(ChunkingFilter.NONE)
                .setBulkDeleteSplittingEnabled(false)
                .setCompression(Compression.ZLIB)
                .setLargeThreshold(250)
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
        return builder.build();
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
}
