package link.locutus.discord.commands.manager.v2.impl.pw.filter;

import link.locutus.discord.commands.manager.v2.command.CommandCallable;
import link.locutus.discord.commands.manager.v2.command.ParametricCallable;
import link.locutus.discord.db.AllianceLookup;
import link.locutus.discord.db.BankDB;
import link.locutus.discord.db.BaseballDB;
import link.locutus.discord.db.DiscordDB;
import link.locutus.discord.db.ForumDB;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.INationSnapshot;
import link.locutus.discord.db.NationDB;
import link.locutus.discord.db.ReportManager;
import link.locutus.discord.db.bank.TaxBracketLookup;
import link.locutus.discord.db.TradeDB;
import link.locutus.discord.db.WarDB;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.LoanManager;
import link.locutus.discord.db.entities.nation.DBNationData;
import link.locutus.discord.db.entities.nation.SimpleDBNation;
import link.locutus.discord.pnw.PNWUser;
import link.locutus.discord.commands.stock.StockDB;
import link.locutus.discord.util.discord.GuildShardManager;
import link.locutus.discord.util.trade.TradeManager;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.LongFunction;
import java.util.function.Supplier;

/**
 * Explicit runtime dependency surface for command, binding, autocomplete, and placeholder code.
 *
 * <p>This is intentionally separate from the app loader lifecycle contract. Callers that need a
 * running command/runtime environment should depend on these capabilities directly instead of on
 * {@code ILoader}, which also owns startup and partial-resolution concerns.</p>
 *
 * <p>When a helper overlaps a concrete runtime dependency exposed here, the helper forwards through
 * that concrete service by default unless the builder is given an explicit override. That keeps the
 * seam explicit without forcing callers to wire the same capability twice.</p>
 */
public final class CommandRuntimeServices implements CommandRuntimeLookupContext, CommandRuntimeCommandContext {
    private final NationSnapshotService nationSnapshots;
    private final CommandRuntimeLookupService lookupService;
    private final AllianceLookup allianceLookup;
    private final TaxBracketLookup taxBracketLookup;
    private final LongFunction<User> discordUserByIdResolver;
    private final LongFunction<PNWUser> discordRegistrationByIdResolver;
    private final BiFunction<String, String, PNWUser> discordUserResolver;
    private final BiFunction<String, Guild, User> discordUserLookupResolver;
    private final Supplier<Set<DBAlliance>> alliancesResolver;
    private final Function<Guild, GuildDB> guildDbResolver;
    private final LongFunction<GuildDB> guildDbByIdResolver;
    private final LongFunction<Guild> guildResolver;
    private final Supplier<Collection<GuildDB>> guildDatabasesResolver;
    private final Function<User, Set<Guild>> mutualGuildsResolver;
    private final Supplier<GuildDB> rootCoalitionServerSupplier;
    private final Supplier<GuildShardManager> shardManagerSupplier;
    private final Function<List<String>, CommandCallable> commandLookup;
    private final BiFunction<String, Boolean, Map<String, String>> slashCommandValidator;
    private final Supplier<Collection<ParametricCallable<?>>> parametricCommandsSupplier;
    private final Supplier<NationDB> nationDbSupplier;
    private final Supplier<WarDB> warDbSupplier;
    private final Supplier<BankDB> bankDbSupplier;
    private final Supplier<StockDB> stockDbSupplier;
    private final Supplier<BaseballDB> baseballDbSupplier;
    private final Supplier<ForumDB> forumDbSupplier;
    private final Supplier<DiscordDB> discordDbSupplier;
    private final Supplier<ReportManager> reportManagerSupplier;
    private final Supplier<LoanManager> loanManagerSupplier;
    private final Supplier<TradeManager> tradeManagerSupplier;
    private final Supplier<TradeDB> tradeDbSupplier;
    private final IntFunction<DBNation> nationByIdResolver;
    private final IntFunction<DBNation> nationOrCreateResolver;
    private final IntFunction<DBAlliance> allianceByIdResolver;
    private final IntFunction<DBAlliance> allianceOrCreateResolver;
    private final IntConsumer nationDirtyMarker;

    private CommandRuntimeServices(Builder builder) {
        this.nationSnapshots = Objects.requireNonNull(builder.nationSnapshots, "nationSnapshots");
        this.discordUserByIdResolver = builder.discordUserByIdResolver;
        this.discordRegistrationByIdResolver = builder.discordRegistrationByIdResolver;
        this.discordUserResolver = builder.discordUserResolver;
        this.discordUserLookupResolver = builder.discordUserLookupResolver;
        this.alliancesResolver = builder.alliancesResolver;
        this.guildDbResolver = builder.guildDbResolver;
        this.guildDbByIdResolver = builder.guildDbByIdResolver;
        this.guildResolver = builder.guildResolver;
        this.guildDatabasesResolver = builder.guildDatabasesResolver;
        this.mutualGuildsResolver = builder.mutualGuildsResolver;
        this.rootCoalitionServerSupplier = builder.rootCoalitionServerSupplier;
        this.shardManagerSupplier = builder.shardManagerSupplier;
        this.commandLookup = builder.commandLookup;
        this.slashCommandValidator = builder.slashCommandValidator;
        this.parametricCommandsSupplier = builder.parametricCommandsSupplier;
        this.nationDbSupplier = builder.nationDbSupplier;
        this.warDbSupplier = builder.warDbSupplier;
        this.bankDbSupplier = builder.bankDbSupplier;
        this.stockDbSupplier = builder.stockDbSupplier;
        this.baseballDbSupplier = builder.baseballDbSupplier;
        this.forumDbSupplier = builder.forumDbSupplier;
        this.discordDbSupplier = builder.discordDbSupplier;
        this.reportManagerSupplier = builder.reportManagerSupplier;
        this.loanManagerSupplier = builder.loanManagerSupplier;
        this.tradeManagerSupplier = builder.tradeManagerSupplier;
        this.tradeDbSupplier = builder.tradeDbSupplier;
        this.nationByIdResolver = builder.nationByIdResolver;
        this.nationOrCreateResolver = builder.nationOrCreateResolver;
        this.allianceByIdResolver = builder.allianceByIdResolver;
        this.allianceOrCreateResolver = builder.allianceOrCreateResolver;
        this.nationDirtyMarker = builder.nationDirtyMarker;
        this.lookupService = new CommandRuntimeLookupService(createLookupResolver());
        this.taxBracketLookup = createTaxBracketLookup();
        this.allianceLookup = this.taxBracketLookup;
    }

    private TaxBracketLookup createTaxBracketLookup() {
        return new TaxBracketLookup() {
            @Override
            public Set<DBNation> getNationsByAlliance(Set<Integer> alliances) {
                INationSnapshot snapshot = CommandRuntimeServices.this.nationSnapshots().resolve(null);
                return snapshot.getNationsByAlliance(alliances);
            }

            @Override
            public DBAlliance getAlliance(int allianceId) {
                return CommandRuntimeServices.this.resolveAllianceById(allianceId);
            }

            @Override
            public Set<DBNation> getNationsByBracket(int taxId) {
                INationSnapshot snapshot = CommandRuntimeServices.this.nationSnapshots().resolve(null);
                return snapshot.getNationsByBracket(taxId);
            }
        };
    }

    private CommandRuntimeLookupResolver createLookupResolver() {
        return new CommandRuntimeLookupResolver() {
            @Override
            public NationSnapshotService nationSnapshots() {
                return CommandRuntimeServices.this.nationSnapshots();
            }

            @Override
            public User getDiscordUserById(long userId) {
                return CommandRuntimeServices.this.getDiscordUserById(userId);
            }

            @Override
            public PNWUser getRegisteredUserById(long userId) {
                return CommandRuntimeServices.this.getRegisteredUserById(userId);
            }

            @Override
            public PNWUser getRegisteredUser(String userName, String fullTag) {
                return CommandRuntimeServices.this.getRegisteredUser(userName, fullTag);
            }

            @Override
            public User findDiscordUser(String search, Guild guild) {
                return CommandRuntimeServices.this.findDiscordUser(search, guild);
            }

            @Override
            public DBNation getNationOrCreate(int nationId) {
                return CommandRuntimeServices.this.resolveNationOrCreate(nationId);
            }

            @Override
            public DBAlliance getAllianceById(int allianceId) {
                return CommandRuntimeServices.this.resolveAllianceById(allianceId);
            }

            @Override
            public DBAlliance getAllianceOrCreate(int allianceId) {
                return CommandRuntimeServices.this.resolveAllianceOrCreate(allianceId);
            }
        };
    }

    public static Builder builder(NationSnapshotService nationSnapshots) {
        return new Builder(nationSnapshots);
    }

    public Builder toBuilder() {
        Builder builder = new Builder(this.nationSnapshots);
        if (this.discordUserByIdResolver != null) {
            builder.discordUserById(this.discordUserByIdResolver);
        }
        if (this.discordRegistrationByIdResolver != null) {
            builder.discordRegistrationById(this.discordRegistrationByIdResolver);
        }
        if (this.discordUserResolver != null) {
            builder.discordUser(this.discordUserResolver);
        }
        if (this.discordUserLookupResolver != null) {
            builder.discordUserLookup(this.discordUserLookupResolver);
        }
        if (this.alliancesResolver != null) {
            builder.alliances(this.alliancesResolver);
        }
        if (this.guildDbResolver != null) {
            builder.guildDb(this.guildDbResolver);
        }
        if (this.guildDbByIdResolver != null) {
            builder.guildDbById(this.guildDbByIdResolver);
        }
        if (this.guildResolver != null) {
            builder.guild(this.guildResolver);
        }
        if (this.guildDatabasesResolver != null) {
            builder.guildDatabases(this.guildDatabasesResolver);
        }
        if (this.mutualGuildsResolver != null) {
            builder.mutualGuilds(this.mutualGuildsResolver);
        }
        if (this.rootCoalitionServerSupplier != null) {
            builder.rootCoalitionServer(this.rootCoalitionServerSupplier);
        }
        if (this.shardManagerSupplier != null) {
            builder.shardManager(this.shardManagerSupplier);
        }
        if (this.commandLookup != null) {
            builder.commandLookup(this.commandLookup);
        }
        if (this.slashCommandValidator != null) {
            builder.slashCommandValidator(this.slashCommandValidator);
        }
        if (this.parametricCommandsSupplier != null) {
            builder.parametricCommands(this.parametricCommandsSupplier);
        }
        if (this.nationDbSupplier != null) {
            builder.nationDb(this.nationDbSupplier);
        }
        if (this.warDbSupplier != null) {
            builder.warDb(this.warDbSupplier);
        }
        if (this.bankDbSupplier != null) {
            builder.bankDb(this.bankDbSupplier);
        }
        if (this.stockDbSupplier != null) {
            builder.stockDb(this.stockDbSupplier);
        }
        if (this.baseballDbSupplier != null) {
            builder.baseballDb(this.baseballDbSupplier);
        }
        if (this.forumDbSupplier != null) {
            builder.forumDb(this.forumDbSupplier);
        }
        if (this.discordDbSupplier != null) {
            builder.discordDb(this.discordDbSupplier);
        }
        if (this.reportManagerSupplier != null) {
            builder.reportManager(this.reportManagerSupplier);
        }
        if (this.loanManagerSupplier != null) {
            builder.loanManager(this.loanManagerSupplier);
        }
        if (this.tradeManagerSupplier != null) {
            builder.tradeManager(this.tradeManagerSupplier);
        }
        if (this.tradeDbSupplier != null) {
            builder.tradeDb(this.tradeDbSupplier);
        }
        if (this.nationByIdResolver != null) {
            builder.nationById(this.nationByIdResolver);
        }
        if (this.nationOrCreateResolver != null) {
            builder.nationOrCreate(this.nationOrCreateResolver);
        }
        if (this.allianceByIdResolver != null) {
            builder.allianceById(this.allianceByIdResolver);
        }
        if (this.allianceOrCreateResolver != null) {
            builder.allianceOrCreate(this.allianceOrCreateResolver);
        }
        if (this.nationDirtyMarker != null) {
            builder.markNationDirty(this.nationDirtyMarker);
        }
        return builder;
    }

    /**
     * Composition roots finish wiring command catalog access after the root command tree exists.
     */
    public CommandRuntimeServices withCommandRuntime(Function<List<String>, CommandCallable> resolver,
            BiFunction<String, Boolean, Map<String, String>> validator,
            Supplier<Collection<ParametricCallable<?>>> parametricCommands) {
        return toBuilder()
                .commandLookup(resolver)
                .slashCommandValidator(validator)
                .parametricCommands(parametricCommands)
                .build();
    }

    @Override
    public NationSnapshotService nationSnapshots() {
        return nationSnapshots;
    }

    public AllianceLookup allianceLookup() {
        return allianceLookup;
    }

    @Override
    public TaxBracketLookup taxBracketLookup() {
        return taxBracketLookup;
    }

    @Override
    public CommandRuntimeLookupService lookup() {
        return lookupService;
    }

    public User getDiscordUserById(long userId) {
        if (discordUserByIdResolver != null) {
            return discordUserByIdResolver.apply(userId);
        }
        if (shardManagerSupplier != null) {
            return shardManager().getUserById(userId);
        }
        throw unsupported("discord user by id");
    }

    public PNWUser getRegisteredUserById(long userId) {
        if (discordRegistrationByIdResolver != null) {
            return discordRegistrationByIdResolver.apply(userId);
        }
        if (discordDbSupplier != null) {
            return discordDb().getUserFromDiscordId(userId, availableDiscordUserByIdLookup());
        }
        throw unsupported("discord registration by id");
    }

    public PNWUser getRegisteredUser(String userName, String fullTag) {
        if (discordUserResolver != null) {
            return discordUserResolver.apply(userName, fullTag);
        }
        if (discordDbSupplier != null) {
            return discordDb().getUser(null, userName, fullTag);
        }
        throw unsupported("discord user");
    }

    public PNWUser getDiscordUser(String userName, String fullTag) {
        return getRegisteredUser(userName, fullTag);
    }

    public User findDiscordUser(String search, Guild guild) {
        if (discordUserLookupResolver != null) {
            return discordUserLookupResolver.apply(search, guild);
        }
        if (shardManagerSupplier != null) {
            return shardManager().getUserByName(search, true, guild,
                    discordDbSupplier == null ? null : () -> discordDb().getRegisteredUsers());
        }
        throw unsupported("discord user lookup");
    }

    @Override
    public Set<DBAlliance> getAlliances() {
        if (alliancesResolver != null) {
            return alliancesResolver.get();
        }
        if (nationDbSupplier != null) {
            return nationDb().getAlliances();
        }
        throw unsupported("alliances");
    }

    @Override
    public GuildDB getGuildDb(Guild guild) {
        if (guildDbResolver != null) {
            return guildDbResolver.apply(guild);
        }
        if (guild == null) {
            return null;
        }
        if (guildDbByIdResolver != null) {
            return guildDbByIdResolver.apply(guild.getIdLong());
        }
        throw unsupported("guild db");
    }

    @Override
    public GuildDB getGuildDb(long guildId) {
        if (guildDbByIdResolver != null) {
            return guildDbByIdResolver.apply(guildId);
        }
        if (guildDbResolver != null) {
            return guildDbResolver.apply(getGuild(guildId));
        }
        throw unsupported("guild db by id");
    }

    @Override
    public Guild getGuild(long guildId) {
        if (guildResolver != null) {
            return guildResolver.apply(guildId);
        }
        if (shardManagerSupplier != null) {
            return shardManager().getGuildById(guildId);
        }
        throw unsupported("guild");
    }

    @Override
    public Collection<GuildDB> getGuildDatabases() {
        if (guildDatabasesResolver != null) {
            return guildDatabasesResolver.get();
        }
        throw unsupported("guild databases");
    }

    @Override
    public Set<Guild> getMutualGuilds(User user) {
        if (mutualGuildsResolver != null) {
            return mutualGuildsResolver.apply(user);
        }
        if (shardManagerSupplier != null) {
            return shardManager().getMutualGuilds(user);
        }
        throw unsupported("mutual guilds");
    }

    @Override
    public GuildDB getRootCoalitionServer() {
        if (rootCoalitionServerSupplier != null) {
            return rootCoalitionServerSupplier.get();
        }
        throw unsupported("root coalition server");
    }

    @Override
    public GuildShardManager shardManager() {
        if (shardManagerSupplier != null) {
            return shardManagerSupplier.get();
        }
        throw unsupported("discord shard manager");
    }

    @Override
    public CommandCallable getCommand(List<String> args) {
        if (commandLookup != null) {
            return commandLookup.apply(args);
        }
        throw unsupported("command lookup");
    }

    @Override
    public Map<String, String> validateSlashCommand(String input, boolean strict) {
        if (slashCommandValidator != null) {
            return slashCommandValidator.apply(input, strict);
        }
        throw unsupported("slash command validator");
    }

    @Override
    public Collection<ParametricCallable<?>> getParametricCommands() {
        if (parametricCommandsSupplier != null) {
            return parametricCommandsSupplier.get();
        }
        throw unsupported("parametric commands");
    }

    public NationDB nationDb() {
        if (nationDbSupplier != null) {
            return nationDbSupplier.get();
        }
        throw unsupported("nation db");
    }

    public WarDB warDb() {
        if (warDbSupplier != null) {
            return warDbSupplier.get();
        }
        throw unsupported("war db");
    }

    public BankDB bankDb() {
        if (bankDbSupplier != null) {
            return bankDbSupplier.get();
        }
        throw unsupported("bank db");
    }

    public StockDB stockDb() {
        if (stockDbSupplier != null) {
            return stockDbSupplier.get();
        }
        throw unsupported("stock db");
    }

    public BaseballDB baseballDb() {
        if (baseballDbSupplier != null) {
            return baseballDbSupplier.get();
        }
        throw unsupported("baseball db");
    }

    public ForumDB forumDb() {
        if (forumDbSupplier != null) {
            return forumDbSupplier.get();
        }
        throw unsupported("forum db");
    }

    public DiscordDB discordDb() {
        if (discordDbSupplier != null) {
            return discordDbSupplier.get();
        }
        throw unsupported("discord db");
    }

    public ReportManager reportManager() {
        if (reportManagerSupplier != null) {
            return reportManagerSupplier.get();
        }
        if (nationDbSupplier != null) {
            return nationDb().getReportManager();
        }
        throw unsupported("report manager");
    }

    public LoanManager loanManager() {
        if (loanManagerSupplier != null) {
            return loanManagerSupplier.get();
        }
        if (nationDbSupplier != null) {
            return nationDb().getLoanManager();
        }
        throw unsupported("loan manager");
    }

    public TradeManager tradeManager() {
        if (tradeManagerSupplier != null) {
            return tradeManagerSupplier.get();
        }
        throw unsupported("trade manager");
    }

    public TradeDB tradeDb() {
        if (tradeDbSupplier != null) {
            return tradeDbSupplier.get();
        }
        if (tradeManagerSupplier != null) {
            return tradeManager().getTradeDb();
        }
        throw unsupported("trade db");
    }

    DBNation resolveNationById(int nationId) {
        if (nationByIdResolver != null) {
            return nationByIdResolver.apply(nationId);
        }
        if (nationDbSupplier != null) {
            return nationDb().getNationById(nationId);
        }
        throw unsupported("nation by id");
    }

    DBNation resolveNationOrCreate(int nationId) {
        if (nationOrCreateResolver != null) {
            return nationOrCreateResolver.apply(nationId);
        }
        if (nationDbSupplier != null) {
            DBNation nation = nationDb().getNationById(nationId);
            if (nation != null) {
                return nation;
            }
            return createPlaceholderNation(nationId);
        }
        throw unsupported("nation by id or create");
    }

    DBAlliance resolveAllianceById(int allianceId) {
        if (allianceByIdResolver != null) {
            return allianceByIdResolver.apply(allianceId);
        }
        if (nationDbSupplier != null) {
            return nationDb().getAlliance(allianceId);
        }
        if (alliancesResolver != null) {
            for (DBAlliance alliance : alliancesResolver.get()) {
                if (alliance.getAlliance_id() == allianceId) {
                    return alliance;
                }
            }
            return null;
        }
        throw unsupported("alliance by id");
    }

    DBAlliance resolveAllianceOrCreate(int allianceId) {
        if (allianceOrCreateResolver != null) {
            return allianceOrCreateResolver.apply(allianceId);
        }
        if (nationDbSupplier != null) {
            if (allianceId == 0) {
                return createPlaceholderAlliance(0);
            }
            return nationDb().getOrCreateAlliance(allianceId);
        }
        throw unsupported("alliance by id or create");
    }

    @Override
    public void markNationDirty(int nationId) {
        if (nationDirtyMarker != null) {
            nationDirtyMarker.accept(nationId);
            return;
        }
        if (nationDbSupplier != null) {
            nationDb().markNationDirty(nationId);
            return;
        }
        throw unsupported("mark nation dirty");
    }

    private static IllegalStateException unsupported(String name) {
        return new IllegalStateException("Command runtime service not configured: " + name);
    }

    private LongFunction<User> availableDiscordUserByIdLookup() {
        if (discordUserByIdResolver != null || shardManagerSupplier != null) {
            return this::getDiscordUserById;
        }
        return null;
    }

    private static DBNation createPlaceholderNation(int nationId) {
        SimpleDBNation placeholder = new SimpleDBNation(new DBNationData());
        placeholder.edit().setNation_id(nationId);
        return placeholder;
    }

    private static DBAlliance createPlaceholderAlliance(int allianceId) {
        return new DBAlliance(allianceId, allianceId == 0 ? "None" : "AA:" + allianceId,
                "", "", "", "", "", 0L, link.locutus.discord.apiv1.enums.NationColor.GRAY, null);
    }

    public static final class Builder {
        private final NationSnapshotService nationSnapshots;
        private LongFunction<User> discordUserByIdResolver;
        private LongFunction<PNWUser> discordRegistrationByIdResolver;
        private BiFunction<String, String, PNWUser> discordUserResolver;
        private BiFunction<String, Guild, User> discordUserLookupResolver;
        private Supplier<Set<DBAlliance>> alliancesResolver;
        private Function<Guild, GuildDB> guildDbResolver;
        private LongFunction<GuildDB> guildDbByIdResolver;
        private LongFunction<Guild> guildResolver;
        private Supplier<Collection<GuildDB>> guildDatabasesResolver;
        private Function<User, Set<Guild>> mutualGuildsResolver;
        private Supplier<GuildDB> rootCoalitionServerSupplier;
        private Supplier<GuildShardManager> shardManagerSupplier;
        private Function<List<String>, CommandCallable> commandLookup;
        private BiFunction<String, Boolean, Map<String, String>> slashCommandValidator;
        private Supplier<Collection<ParametricCallable<?>>> parametricCommandsSupplier;
        private Supplier<NationDB> nationDbSupplier;
        private Supplier<WarDB> warDbSupplier;
        private Supplier<BankDB> bankDbSupplier;
        private Supplier<StockDB> stockDbSupplier;
        private Supplier<BaseballDB> baseballDbSupplier;
        private Supplier<ForumDB> forumDbSupplier;
        private Supplier<DiscordDB> discordDbSupplier;
        private Supplier<ReportManager> reportManagerSupplier;
        private Supplier<LoanManager> loanManagerSupplier;
        private Supplier<TradeManager> tradeManagerSupplier;
        private Supplier<TradeDB> tradeDbSupplier;
        private IntFunction<DBNation> nationByIdResolver;
        private IntFunction<DBNation> nationOrCreateResolver;
        private IntFunction<DBAlliance> allianceByIdResolver;
        private IntFunction<DBAlliance> allianceOrCreateResolver;
        private IntConsumer nationDirtyMarker;

        private Builder(NationSnapshotService nationSnapshots) {
            this.nationSnapshots = nationSnapshots;
        }

        public Builder discordUserById(LongFunction<User> resolver) {
            this.discordUserByIdResolver = Objects.requireNonNull(resolver);
            return this;
        }

        public Builder discordRegistrationById(LongFunction<PNWUser> resolver) {
            this.discordRegistrationByIdResolver = Objects.requireNonNull(resolver);
            return this;
        }

        public Builder discordUser(BiFunction<String, String, PNWUser> resolver) {
            this.discordUserResolver = Objects.requireNonNull(resolver);
            return this;
        }

        public Builder discordUserLookup(BiFunction<String, Guild, User> resolver) {
            this.discordUserLookupResolver = Objects.requireNonNull(resolver);
            return this;
        }

        public Builder alliances(Supplier<Set<DBAlliance>> resolver) {
            this.alliancesResolver = Objects.requireNonNull(resolver);
            return this;
        }

        public Builder guildDb(Function<Guild, GuildDB> resolver) {
            this.guildDbResolver = Objects.requireNonNull(resolver);
            return this;
        }

        public Builder guildDbById(LongFunction<GuildDB> resolver) {
            this.guildDbByIdResolver = Objects.requireNonNull(resolver);
            return this;
        }

        public Builder guild(LongFunction<Guild> resolver) {
            this.guildResolver = Objects.requireNonNull(resolver);
            return this;
        }

        public Builder guildDatabases(Supplier<Collection<GuildDB>> resolver) {
            this.guildDatabasesResolver = Objects.requireNonNull(resolver);
            return this;
        }

        public Builder mutualGuilds(Function<User, Set<Guild>> resolver) {
            this.mutualGuildsResolver = Objects.requireNonNull(resolver);
            return this;
        }

        public Builder rootCoalitionServer(Supplier<GuildDB> supplier) {
            this.rootCoalitionServerSupplier = Objects.requireNonNull(supplier);
            return this;
        }

        public Builder shardManager(Supplier<GuildShardManager> supplier) {
            this.shardManagerSupplier = Objects.requireNonNull(supplier);
            return this;
        }

        public Builder commandLookup(Function<List<String>, CommandCallable> resolver) {
            this.commandLookup = Objects.requireNonNull(resolver);
            return this;
        }

        public Builder slashCommandValidator(BiFunction<String, Boolean, Map<String, String>> validator) {
            this.slashCommandValidator = Objects.requireNonNull(validator);
            return this;
        }

        public Builder parametricCommands(Supplier<Collection<ParametricCallable<?>>> supplier) {
            this.parametricCommandsSupplier = Objects.requireNonNull(supplier);
            return this;
        }

        public Builder nationDb(Supplier<NationDB> supplier) {
            this.nationDbSupplier = Objects.requireNonNull(supplier);
            return this;
        }

        public Builder warDb(Supplier<WarDB> supplier) {
            this.warDbSupplier = Objects.requireNonNull(supplier);
            return this;
        }

        public Builder bankDb(Supplier<BankDB> supplier) {
            this.bankDbSupplier = Objects.requireNonNull(supplier);
            return this;
        }

        public Builder stockDb(Supplier<StockDB> supplier) {
            this.stockDbSupplier = Objects.requireNonNull(supplier);
            return this;
        }

        public Builder baseballDb(Supplier<BaseballDB> supplier) {
            this.baseballDbSupplier = Objects.requireNonNull(supplier);
            return this;
        }

        public Builder forumDb(Supplier<ForumDB> supplier) {
            this.forumDbSupplier = Objects.requireNonNull(supplier);
            return this;
        }

        public Builder discordDb(Supplier<DiscordDB> supplier) {
            this.discordDbSupplier = Objects.requireNonNull(supplier);
            return this;
        }

        public Builder reportManager(Supplier<ReportManager> supplier) {
            this.reportManagerSupplier = Objects.requireNonNull(supplier);
            return this;
        }

        public Builder loanManager(Supplier<LoanManager> supplier) {
            this.loanManagerSupplier = Objects.requireNonNull(supplier);
            return this;
        }

        public Builder tradeManager(Supplier<TradeManager> supplier) {
            this.tradeManagerSupplier = Objects.requireNonNull(supplier);
            return this;
        }

        public Builder tradeDb(Supplier<TradeDB> supplier) {
            this.tradeDbSupplier = Objects.requireNonNull(supplier);
            return this;
        }

        public Builder nationById(IntFunction<DBNation> resolver) {
            this.nationByIdResolver = Objects.requireNonNull(resolver);
            return this;
        }

        public Builder nationOrCreate(IntFunction<DBNation> resolver) {
            this.nationOrCreateResolver = Objects.requireNonNull(resolver);
            return this;
        }

        public Builder allianceById(IntFunction<DBAlliance> resolver) {
            this.allianceByIdResolver = Objects.requireNonNull(resolver);
            return this;
        }

        public Builder allianceOrCreate(IntFunction<DBAlliance> resolver) {
            this.allianceOrCreateResolver = Objects.requireNonNull(resolver);
            return this;
        }

        public Builder markNationDirty(IntConsumer consumer) {
            this.nationDirtyMarker = Objects.requireNonNull(consumer);
            return this;
        }

        public CommandRuntimeServices build() {
            return new CommandRuntimeServices(this);
        }
    }
}