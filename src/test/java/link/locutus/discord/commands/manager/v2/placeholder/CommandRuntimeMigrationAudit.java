package link.locutus.discord.commands.manager.v2.placeholder;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import link.locutus.discord.apiv1.enums.NationColor;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.LocalValueStore;
import link.locutus.discord.commands.manager.v2.binding.Parser;
import link.locutus.discord.commands.manager.v2.binding.bindings.LiveAppPlaceholderRegistry;
import link.locutus.discord.commands.manager.v2.binding.bindings.Placeholders;
import link.locutus.discord.commands.manager.v2.command.LiveAppCommandSurface;
import link.locutus.discord.commands.manager.v2.command.WebOption;
import link.locutus.discord.commands.manager.v2.impl.pw.commands.AdminCommands;
import link.locutus.discord.commands.manager.v2.impl.pw.commands.IACommands;
import link.locutus.discord.commands.manager.v2.impl.pw.commands.UtilityCommands;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.PWAppBindings;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.PWBindings;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.autocomplete.PWCompleter;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.AlliancePlaceholders;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.NationPlaceholders;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.NationSnapshotService;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.CommandRuntimeServices;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap;
import link.locutus.discord.commands.manager.v2.binding.validator.ValidatorStore;
import link.locutus.discord.commands.manager.v2.perm.PermissionHandler;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.DiscordDB;
import link.locutus.discord.db.INationSnapshot;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.nation.DBNationData;
import link.locutus.discord.db.entities.nation.SimpleDBNation;
import link.locutus.discord.db.entities.EmbeddingSource;
import link.locutus.discord.pnw.NationList;
import link.locutus.discord.pnw.PNWUser;
import link.locutus.discord.util.discord.GuildShardManager;
import link.locutus.discord.web.jooby.PageHandler;
import link.locutus.discord.web.jooby.adapter.TsEndpointGenerator;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;

import java.io.File;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * High-level audit harness for the command runtime migration.
 *
 * It is intentionally split into:
 * 1. runtime smoke scenarios that exercise explicit injected bootstraps
 * 2. source audits that flag forbidden singleton/static access in the target seam
 *
 * Default mode prints a report and exits zero so it can be used during migration.
 * Pass --strict to return exit code 1 when any audit scenario fails.
 * 
 * Note: String search may miss some forbidden access patterns, this should, as soon as feasible, be shifted to a more robust static analysis.
 */
public final class CommandRuntimeMigrationAudit {
    private static final long REGISTERED_BETA_DISCORD_ID = 900000000000001L;
    private static final String MAIN_SOURCE_ROOT = "src/main/java";
    private static final int MAX_MATCH_SAMPLES_PER_FILE = 3;
    private static final int MAX_OWNER_HOTSPOTS = 10;
    private static final List<String> LOCUTUS_PATTERNS = List.of("Locutus.imp(", "Locutus.cmd(");

    private static final List<SourceRule> SOURCE_RULES = List.of(
            new SourceRule(
            "Placeholders stays neutral",
            "src/main/java/link/locutus/discord/commands/manager/v2/binding/bindings/Placeholders.java",
                    List.of("Placeholders<T, M> init(", "registerEntityCommands(", "getAppOnlyEntityCommandTypes(")),
        new SourceRule(
            "PlaceholderRegistry stays neutral",
            "src/main/java/link/locutus/discord/commands/manager/v2/binding/bindings/PlaceholderRegistry.java",
            List.of("getAppOnlyEntityCommandTypes(")),
        new SourceRule(
            "LiveAppPlaceholderExtension stays app-only",
            "src/main/java/link/locutus/discord/commands/manager/v2/binding/bindings/LiveAppPlaceholderExtension.java",
            List.of("Locutus.imp(", "Locutus.cmd(")),
        new SourceRule(
            "TaxBracket explicit lookup seam",
            "src/main/java/link/locutus/discord/db/entities/TaxBracket.java",
            List.of("Locutus.imp(", "DBAlliance.get(", "DBAlliance.getOrCreate(")),
        new SourceRule(
            "PlaceholderEngine no app entity bootstrap",
            "src/main/java/link/locutus/discord/commands/manager/v2/binding/bindings/PlaceholderEngine.java",
            List.of("initEntityCommands(", "getAppOnlyEntityCommandTypes(", "LiveAppPlaceholderRegistry.class")),
        new SourceRule(
            "INationSnapshot no Locutus fallback",
            "src/main/java/link/locutus/discord/db/INationSnapshot.java",
            List.of("Locutus.imp(")),
        new SourceRule(
            "DiscordUtil no command runtime adapters",
            "src/main/java/link/locutus/discord/util/discord/DiscordUtil.java",
            List.of("CommandRuntimeServices services", "RuntimeDiscordIdentityLookup")),
        new SourceRule(
                    "PermissionBinding no Locutus",
                    "src/main/java/link/locutus/discord/commands/manager/v2/impl/pw/binding/PermissionBinding.java",
            List.of("Locutus.imp(", "Locutus.cmd(")),
            new SourceRule(
                    "DiscordBindings no Locutus",
                    "src/main/java/link/locutus/discord/commands/manager/v2/impl/discord/binding/DiscordBindings.java",
            List.of("Locutus.imp(", "Locutus.cmd(")),
            new SourceRule(
                    "PWCompleter no Locutus",
                    "src/main/java/link/locutus/discord/commands/manager/v2/impl/pw/binding/autocomplete/PWCompleter.java",
            List.of("Locutus.imp(", "Locutus.cmd(")),
            new SourceRule(
                    "DiscordCompleter no Locutus",
                    "src/main/java/link/locutus/discord/commands/manager/v2/impl/discord/binding/autocomplete/DiscordCompleter.java",
            List.of("Locutus.imp(", "Locutus.cmd(")),
            new SourceRule(
                    "GPTBindings no Locutus",
                    "src/main/java/link/locutus/discord/commands/manager/v2/impl/pw/binding/GPTBindings.java",
            List.of("Locutus.imp(", "Locutus.cmd(")),
            new SourceRule(
            "PWAppBindings no Locutus",
            "src/main/java/link/locutus/discord/commands/manager/v2/impl/pw/binding/PWAppBindings.java",
            List.of("Locutus.imp(", "Locutus.cmd(")),
            new SourceRule(
            "AlliancePlaceholders no static entity registry",
            "src/main/java/link/locutus/discord/commands/manager/v2/impl/pw/filter/AlliancePlaceholders.java",
            List.of("DBAlliance.getOrCreate(")),
        new SourceRule(
                    "PlaceholdersMap no static entity registry",
                    "src/main/java/link/locutus/discord/commands/manager/v2/impl/pw/filter/PlaceholdersMap.java",
            List.of("DBNation.getById(", "DBNation.getOrCreate(", "DBAlliance.get(", "DBAlliance.getOrCreate(")),
            new SourceRule(
                    "PWBindings no static entity registry",
                    "src/main/java/link/locutus/discord/commands/manager/v2/impl/pw/binding/PWBindings.java",
            List.of("DBNation.getById(", "DBNation.getOrCreate(", "DBAlliance.get(", "DBAlliance.getOrCreate(")),
        new SourceRule(
            "NationPlaceholders no DiscordUtil runtime parsing",
            "src/main/java/link/locutus/discord/commands/manager/v2/impl/pw/filter/NationPlaceholders.java",
            List.of("DiscordUtil.parseNation(", "DiscordUtil.getNationByUser(", "DiscordUtil.getUser(")),
        new SourceRule(
            "PlaceholdersMap no DiscordUtil runtime parsing",
            "src/main/java/link/locutus/discord/commands/manager/v2/impl/pw/filter/PlaceholdersMap.java",
            List.of("DiscordUtil.parseNation(", "DiscordUtil.getNationByUser(", "DiscordUtil.getUser(",
                "DiscordUtil.parseUserId(")),
        new SourceRule(
            "PWBindings no DiscordUtil runtime parsing",
            "src/main/java/link/locutus/discord/commands/manager/v2/impl/pw/binding/PWBindings.java",
            List.of("DiscordUtil.parseNation(", "DiscordUtil.getNationByUser(", "DiscordUtil.getUser(",
                "DiscordUtil.parseNations(", "DiscordUtil.getNation(")),
        new SourceRule(
            "PWBindings no raw app-service bindings",
            "src/main/java/link/locutus/discord/commands/manager/v2/impl/pw/binding/PWBindings.java",
            List.of("WarDB warDB(CommandRuntimeServices services)",
                "NationDB nationDB(CommandRuntimeServices services)",
                "BankDB bankDB(CommandRuntimeServices services)",
                "StockDB stockDB(CommandRuntimeServices services)",
                "BaseballDB baseballDB(CommandRuntimeServices services)",
                "ForumDB forumDB(CommandRuntimeServices services)",
                "DiscordDB discordDB(CommandRuntimeServices services)",
                "ReportManager ReportManager(CommandRuntimeServices services)",
                "LoanManager loanManager(CommandRuntimeServices services)",
                "GuildDB guildDb(CommandRuntimeServices services, long guildId)",
                "Guild guild(CommandRuntimeServices services, long guildId)",
                "ExecutorService executor(CommandRuntimeServices services)",
                "TradeManager tradeManager(CommandRuntimeServices services)",
                "TradeDB tradeDB(CommandRuntimeServices services)")),
        new SourceRule(
            "PWBindings no full runtime bundle",
            "src/main/java/link/locutus/discord/commands/manager/v2/impl/pw/binding/PWBindings.java",
            List.of("CommandRuntimeServices")),
        new SourceRule(
            "PlaceholdersMap no direct NationList bridge",
            "src/main/java/link/locutus/discord/commands/manager/v2/impl/pw/filter/PlaceholdersMap.java",
            List.of("registerCommandsClass(NationList.class)")),
        new SourceRule(
            "HelpCommands no Locutus v2 access",
            "src/main/java/link/locutus/discord/commands/manager/v2/impl/pw/commands/HelpCommands.java",
            List.of("Locutus.imp().getCommandManager().getV2()", "Locutus.cmd().getV2()")),
        new SourceRule(
            "GPTCommands no Locutus v2 access",
            "src/main/java/link/locutus/discord/commands/manager/v2/impl/pw/commands/GPTCommands.java",
            List.of("Locutus.imp().getCommandManager().getV2()", "Locutus.cmd().getV2()")),
        new SourceRule(
            "IACommands no Locutus v2 access",
            "src/main/java/link/locutus/discord/commands/manager/v2/impl/pw/commands/IACommands.java",
            List.of("Locutus.imp().getCommandManager().getV2()", "Locutus.cmd().getV2()")),
        new SourceRule(
            "IACommands no sheet leader Locutus fallback",
            "src/main/java/link/locutus/discord/commands/manager/v2/impl/pw/commands/IACommands.java",
            List.of("Locutus.imp().getNationDB().getNationByLeader(")),
        new SourceRule(
            "UtilityCommands no sheet leader Locutus fallback",
            "src/main/java/link/locutus/discord/commands/manager/v2/impl/pw/commands/UtilityCommands.java",
            List.of("Locutus.imp().getNationDB().getNationByLeader(")),
        new SourceRule(
            "AdminCommands no sheet identity singleton fallback",
            "src/main/java/link/locutus/discord/commands/manager/v2/impl/pw/commands/AdminCommands.java",
            List.of("Locutus.imp().getNationDB().getNationByLeader(", "DBNation.getByUser(user)")),
        new SourceRule(
            "ParametricCallable no global slash validation",
            "src/main/java/link/locutus/discord/commands/manager/v2/command/ParametricCallable.java",
            List.of("Locutus.imp().getCommandManager().getV2().validateSlashCommand(")),
        new SourceRule(
            "TsEndpointGenerator no Locutus bootstrap",
            "src/main/java/link/locutus/discord/web/jooby/adapter/TsEndpointGenerator.java",
            List.of("Locutus.imp(", "Locutus.cmd(")),
        new SourceRule(
            "WebOptionBindings no concrete placeholder map parser",
            "src/main/java/link/locutus/discord/web/commands/options/WebOptionBindings.java",
            List.of("getParser(PlaceholdersMap map)")),
        new SourceRule(
            "WebOptionBindings no Locutus v2 access",
            "src/main/java/link/locutus/discord/web/commands/options/WebOptionBindings.java",
            List.of("Locutus.imp().getCommandManager().getV2()", "Locutus.cmd().getV2()")),
        new SourceRule(
            "WebPWBindings no Locutus v2 placeholder access",
            "src/main/java/link/locutus/discord/web/commands/binding/WebPWBindings.java",
            List.of("Locutus.cmd().getV2().getPlaceholders()", "Locutus.cmd().getV2().getNationPlaceholders()")),
        new SourceRule(
            "MCPCommands no Locutus v2 placeholder access",
            "src/main/java/link/locutus/discord/web/commands/mcp/MCPCommands.java",
            List.of("Locutus.cmd().getV2().getPlaceholders()")),
        new SourceRule(
            "StatEndpoints no Locutus v2 placeholder access",
            "src/main/java/link/locutus/discord/web/commands/api/StatEndpoints.java",
            List.of("Locutus.cmd().getV2().getPlaceholders()")),
        new SourceRule(
            "AllianceList explicit alliance lookup seam",
            "src/main/java/link/locutus/discord/pnw/AllianceList.java",
            List.of("Locutus.imp(", "DBAlliance.get(", "DBAlliance.getOrCreate("))
    );

    public static void main(String[] args) {
        AuditOptions options = AuditOptions.parse(args);
        AuditReport report = new CommandRuntimeMigrationAudit().run(options);
        report.print(options.strict);
        if (options.strict && report.hasFailures()) {
            System.exit(1);
        }
    }

    private AuditReport run(AuditOptions options) {
        AuditReport report = new AuditReport();
        if (!options.sourceOnly) {
            report.add(runScenario("parser bootstrap without Locutus", this::parserBootstrapSmoke));
            report.add(runScenario("command bootstrap without Locutus", this::commandBootstrapSmoke));
            report.add(runScenario("app command bootstrap explicit bridges", this::appCommandBootstrapSmoke));
            report.add(runScenario("live app placeholder registry stays opt-in", this::liveAppRegistryPublicationSmoke));
            report.add(runScenario("page handler web option bootstrap without concrete placeholder map", this::pageHandlerWebOptionSmoke));
            report.add(runScenario("ts endpoint generator standalone bootstrap", this::tsEndpointStandaloneSmoke));
            report.add(runScenario("discord forwarding without Locutus", this::discordForwardingSmoke));
            report.add(runScenario("nation autocomplete without Locutus", this::autocompleteSmoke));
        }
        if (!options.runtimeOnly) {
            report.add(runScenario("live app command helpers explicitly marked", this::liveAppCommandHelperAudit));
            report.add(runScenario("placeholder owner Locutus access scoped to app bootstrap", this::placeholderOwnerSourceAudit));
            for (SourceRule rule : SOURCE_RULES) {
                report.add(runScenario(rule.name(), () -> sourceAudit(rule)));
            }
        }
        return report;
    }

    private ScenarioResult parserBootstrapSmoke() {
        AuditFixture fixture = AuditFixture.create();
        PlaceholdersMap map = fixture.createMap(false);
        LocalValueStore locals = map.createLocals();

        NationPlaceholders nationPlaceholders = (NationPlaceholders) (Placeholders<?, ?>) map.get(DBNation.class);
        Set<String> nationNames = nationPlaceholders.parseSet(locals, "Alpha,aa:10", fixture.snapshot, true)
                .stream()
                .map(DBNation::getName)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> boundNationNames = PWBindings.nations(locals, null, "Alpha,aa:10")
            .stream()
            .map(DBNation::getName)
            .collect(Collectors.toCollection(LinkedHashSet::new));

        AlliancePlaceholders alliancePlaceholders = (AlliancePlaceholders) (Placeholders<?, ?>) map.get(DBAlliance.class);
        Set<Integer> allianceIds = alliancePlaceholders.parseSet(locals, "*", null)
                .stream()
                .map(DBAlliance::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        DBNation mentionedNation = fixture.services.lookup().parseNation(
            fixture.snapshot,
            "<@" + REGISTERED_BETA_DISCORD_ID + ">",
            false,
            false,
            true,
            null);

        if (!nationNames.equals(Set.of("Alpha", "Gamma"))) {
            return ScenarioResult.fail("expected nation selection [Alpha, Gamma] but got " + nationNames);
        }
        if (!boundNationNames.equals(Set.of("Alpha", "Gamma"))) {
            return ScenarioResult.fail("expected PWBindings nation selection [Alpha, Gamma] but got "
                    + boundNationNames);
        }
        if (!allianceIds.equals(Set.of(10, 20))) {
            return ScenarioResult.fail("expected alliance selection [10, 20] but got " + allianceIds);
        }
        if (mentionedNation == null || mentionedNation.getId() != 2) {
            return ScenarioResult.fail("expected snapshot-backed Discord mention parsing to resolve Beta but got "
                + (mentionedNation == null ? "null" : mentionedNation.getName()));
        }
        return ScenarioResult.pass(
            "initParsing(), PWBindings nation-set binding, and snapshot mention parsing work with injected services only");
    }

    private ScenarioResult commandBootstrapSmoke() {
        AuditFixture fixture = AuditFixture.create();
        PlaceholdersMap map = fixture.createMap(true);
        LocalValueStore locals = map.createLocals();

        NationPlaceholders nationPlaceholders = (NationPlaceholders) (Placeholders<?, ?>) map.get(DBNation.class);
        AlliancePlaceholders alliancePlaceholders = (AlliancePlaceholders) (Placeholders<?, ?>) map.get(DBAlliance.class);
        Set<String> names = nationPlaceholders.parseSet(locals, "LeAdEr:LeaderB,aa:10", fixture.snapshot, true)
                .stream()
                .map(DBNation::getName)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (!names.equals(Set.of("Beta", "Alpha", "Gamma"))) {
            return ScenarioResult.fail("expected command bootstrap nation selection [Beta, Alpha, Gamma] but got " + names);
        }
        if (nationPlaceholders.getCommands().get("estimateGNI") != null) {
            return ScenarioResult.fail("neutral initCommands() still exposes DBNation entity command `estimateGNI`");
        }
        if (alliancePlaceholders.getCommands().get("getNations") != null) {
            return ScenarioResult.fail("neutral initCommands() still exposes DBAlliance entity command `getNations`");
        }
        if (alliancePlaceholders.getCommands().get("getAverageMMR") != null) {
            return ScenarioResult.fail("neutral initCommands() still exposes legacy alliance NationList command `getAverageMMR`");
        }
        return ScenarioResult.pass(
                "initCommands() keeps selector parsing working while leaving DBNation/DBAlliance entity commands out of the neutral runtime");
    }

    private ScenarioResult appCommandBootstrapSmoke() {
        AuditFixture fixture = AuditFixture.create();
        PlaceholdersMap map = fixture.createAppMap();
        NationPlaceholders nationPlaceholders = (NationPlaceholders) (Placeholders<?, ?>) map.get(DBNation.class);
        AlliancePlaceholders alliancePlaceholders = (AlliancePlaceholders) (Placeholders<?, ?>) map.get(DBAlliance.class);

        if (nationPlaceholders.getCommands().get("estimateGNI") == null) {
            return ScenarioResult.fail(
                "initAppCommands() should still expose DBNation entity command `estimateGNI`");
        }
        if (alliancePlaceholders.getCommands().get("getAverageMMR") == null) {
            return ScenarioResult.fail(
                    "initAppCommands() no longer exposes legacy alliance NationList command `getAverageMMR`");
        }
        return ScenarioResult.pass(
            "initAppCommands() keeps default entity commands plus the legacy alliance NationList bridge on the live app path");
    }

    private ScenarioResult liveAppRegistryPublicationSmoke() {
        AuditFixture fixture = AuditFixture.create();
        PlaceholdersMap neutralMap = fixture.createMap(true);
        if (LiveAppPlaceholderRegistry.resolve(neutralMap.getStore()) != null) {
            return ScenarioResult.fail(
                    "neutral placeholder bootstrap should not publish the live-app placeholder registry contract");
        }

        PlaceholdersMap appMap = fixture.createAppMap();
        LiveAppPlaceholderRegistry liveRegistry = LiveAppPlaceholderRegistry.resolve(appMap.getStore());
        if (liveRegistry == null) {
            return ScenarioResult.fail("initAppCommands() should publish the live-app placeholder registry contract");
        }

        Set<Class<?>> ownerTypes = liveRegistry.getAppOnlyEntityCommandTypes();
        if (!ownerTypes.contains(DBNation.class) || !ownerTypes.contains(DBAlliance.class)
                || !ownerTypes.contains(NationList.class)) {
            return ScenarioResult.fail("expected live-app placeholder owner metadata to include DBNation, DBAlliance, and NationList but got "
                    + ownerTypes);
        }
        if (!ownerTypes.contains(Project.class)) {
            return ScenarioResult.fail(
                "expected live-app placeholder owner metadata to keep default placeholder owner types such as Project but got "
                    + ownerTypes);
        }

        return ScenarioResult.pass(
                "the value store only publishes live-app placeholder owner metadata from the explicit app bootstrap path");
    }

    private ScenarioResult pageHandlerWebOptionSmoke() {
        AuditFixture fixture = AuditFixture.create();
        PageHandler handler = new PageHandler(fixture.createMap(false));
        Parser<WebOption> parser = handler.getWebOptionStore().get(Key.of(Parser.class));
        if (parser == null) {
            return ScenarioResult.fail("expected the web option store to expose a Parser web option binding");
        }
        WebOption option = parser.apply(handler.getStore(), null);
        if (option == null || option.getOptions() == null || option.getOptions().isEmpty()) {
            return ScenarioResult.fail("expected Parser web option metadata to be generated from the current value store");
        }
        Parser<WebOption> embeddingSourceParser = handler.getWebOptionStore().get(Key.of(EmbeddingSource.class));
        if (embeddingSourceParser == null) {
            return ScenarioResult.fail("expected the web option store to expose an EmbeddingSource web option binding");
        }
        WebOption embeddingSourceOption = embeddingSourceParser.apply(handler.getStore(), null);
        if (embeddingSourceOption == null) {
            return ScenarioResult.fail("expected EmbeddingSource web option metadata to resolve without GPT binding recursion");
        }
        return ScenarioResult.pass(
                "PageHandler web option parser discovery resolves through the provided value store without requiring a concrete PlaceholdersMap parser or recursive GPT handler binding");
    }

    private ScenarioResult tsEndpointStandaloneSmoke() throws IOException {
        Path outputDir = Files.createTempDirectory("ts-endpoint-audit-");
        try {
            PageHandler handler = TsEndpointGenerator.createStandalonePageHandler();
            TsEndpointGenerator.writeFiles(handler, outputDir.toFile(), true, false);
            Path endpointFile = outputDir.resolve("lib").resolve("endpoints.ts");
            if (!Files.exists(endpointFile)) {
                return ScenarioResult.fail("expected standalone TsEndpointGenerator bootstrap to write lib/endpoints.ts");
            }
            String content = Files.readString(endpointFile);
            if (!content.contains("export const ENDPOINTS")) {
                return ScenarioResult.fail("expected standalone TsEndpointGenerator output to contain ENDPOINTS export");
            }
            return ScenarioResult.pass("TsEndpointGenerator can bootstrap endpoint generation without Locutus.imp()");
        } finally {
            deleteTree(outputDir);
        }
    }

    private ScenarioResult autocompleteSmoke() {
        AuditFixture fixture = AuditFixture.create();
        PlaceholdersMap map = fixture.createMap(false);
        PWCompleter completer = new PWCompleter();

        List<String> suggestions = completer.nationCompleter(map.getStore(), "Al");
        boolean containsAlpha = suggestions.stream().anyMatch(f -> f.toLowerCase(Locale.ROOT).contains("alpha"));
        if (!containsAlpha) {
            return ScenarioResult.fail("expected nation autocomplete suggestions containing Alpha, got " + suggestions);
        }
        return ScenarioResult.pass("PWCompleter nation autocomplete resolves through the injected placeholder registry");
    }

    private ScenarioResult discordForwardingSmoke() throws Exception {
        String dbName = "command-runtime-audit-forwarding-" + System.nanoTime();
        DiscordDB discordDb = new DiscordDB(dbName);
        File dbFile = discordDb.getFile();
        long originalAdminUserId = Settings.INSTANCE.ADMIN_USER_ID;
        int originalNationId = Settings.INSTANCE.NATION_ID;
        long guildId = 123456789012345L;
        long adminDiscordId = 900000000000777L;
        try {
            discordDb.addUser(new PNWUser(2, REGISTERED_BETA_DISCORD_ID, "Beta#0001"));

            User betaUser = user(REGISTERED_BETA_DISCORD_ID, "Beta");
            User adminUser = user(adminDiscordId, "Admin");
            Guild guild = guild(guildId, List.of(member(betaUser, null)));
            GuildShardManager shardManager = new GuildShardManager(jda(List.of(betaUser, adminUser), List.of(guild)));

            Settings.INSTANCE.ADMIN_USER_ID = adminDiscordId;
            Settings.INSTANCE.NATION_ID = 7;

            CommandRuntimeServices services = CommandRuntimeServices.builder(modifier -> null)
                    .discordDb(() -> discordDb)
                    .shardManager(() -> shardManager)
                    .build();

            User resolvedUser = services.findDiscordUser("bEtA", null);
            if (resolvedUser == null || resolvedUser.getIdLong() != REGISTERED_BETA_DISCORD_ID) {
                return ScenarioResult.fail("expected registered-user cache-miss lookup to resolve Beta but got "
                        + (resolvedUser == null ? "null" : resolvedUser.getName()));
            }

            PNWUser adminRegistration = services.getRegisteredUserById(adminDiscordId);
            if (adminRegistration == null || adminRegistration.getNationId() != 7
                    || adminRegistration.getDiscordId() != adminDiscordId
                    || !"Admin".equals(adminRegistration.getDiscordName())) {
                return ScenarioResult.fail("expected admin-id forwarding to resolve a configured admin registration but got "
                        + (adminRegistration == null ? "null" : adminRegistration.getDiscordName()));
            }

            Set<Long> mutualGuildIds = services.getMutualGuilds(betaUser).stream()
                    .map(Guild::getIdLong)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (!mutualGuildIds.equals(Set.of(guildId))) {
                return ScenarioResult.fail("expected mutual guild lookup to stay inside shard state and return [" + guildId
                        + "] but got " + mutualGuildIds);
            }

            return ScenarioResult.pass(
                    "forwarded Discord cache-miss, admin-id, and mutual-guild helper paths resolve through injected concrete services only");
        } finally {
            Settings.INSTANCE.ADMIN_USER_ID = originalAdminUserId;
            Settings.INSTANCE.NATION_ID = originalNationId;
            discordDb.close();
            deleteDbFiles(dbFile);
        }
    }

    private ScenarioResult placeholderOwnerSourceAudit() throws IOException {
        return summarizePlaceholderOwnerAudit(scanPlaceholderOwners());
    }

    private PlaceholderOwnerAuditSummary scanPlaceholderOwners() throws IOException {
        AuditFixture fixture = AuditFixture.create();
        PlaceholdersMap map = fixture.createAppMap();
        LiveAppPlaceholderRegistry liveRegistry = LiveAppPlaceholderRegistry.resolve(map.getStore());
        if (liveRegistry == null) {
            throw new IllegalStateException(
                    "expected live-app placeholder registry metadata to be available from the app bootstrap path");
        }
        Set<Class<?>> appOnlyOwnerTypes = new LinkedHashSet<>(liveRegistry.getAppOnlyEntityCommandTypes());
        List<Class<?>> auditedTypes = new ArrayList<>();
        auditedTypes.addAll(map.getTypes());
        auditedTypes.addAll(appOnlyOwnerTypes);
        auditedTypes = auditedTypes.stream().distinct().sorted(Comparator.comparing(Class::getName)).toList();

        List<String> missingCoverage = new ArrayList<>();
        List<PlaceholderOwnerFailure> neutralFailures = new ArrayList<>();
        List<PlaceholderOwnerFailure> appOnlyClassified = new ArrayList<>();
        for (Class<?> type : auditedTypes) {
            if (!type.getName().startsWith("link.locutus.")) {
                continue;
            }

            Path file = sourceFileForType(type);
            String relativePath = relativePath(file);
            if (!Files.exists(file)) {
                missingCoverage.add(type.getName() + " -> " + relativePath);
                continue;
            }

            List<SourceMatch> matches = findForbiddenMatches(file, LOCUTUS_PATTERNS);
            if (!matches.isEmpty()) {
                PlaceholderOwnerFailure failure = new PlaceholderOwnerFailure(type.getName(), relativePath, matches);
                if (appOnlyOwnerTypes.contains(type)) {
                    appOnlyClassified.add(failure);
                } else {
                    neutralFailures.add(failure);
                }
            }
        }

        return new PlaceholderOwnerAuditSummary(missingCoverage, neutralFailures, appOnlyClassified);
    }

    static ScenarioResult summarizePlaceholderOwnerAudit(PlaceholderOwnerAuditSummary summary) {
        if (summary.hasAuditFailures()) {
            StringBuilder detail = new StringBuilder(
                    "placeholder owner source files still depend on Locutus singletons outside the declared live app entity bootstrap");
            if (!summary.missingCoverage().isEmpty()) {
                detail.append("\nmissing source coverage:");
                for (String missing : summary.missingCoverage()) {
                    detail.append("\n- ").append(missing);
                }
            }
            if (!summary.neutralFailures().isEmpty()) {
                appendPlaceholderOwnerFailures(detail,
                        "files with unclassified direct Locutus access:",
                        summary.neutralFailures());
            }
            if (!summary.appOnlyClassified().isEmpty()) {
                appendPlaceholderOwnerFailures(detail,
                        "classified live app owner debt also still exists:",
                        summary.appOnlyClassified());
            }
            return ScenarioResult.fail(detail.toString());
        }

        if (summary.appOnlyClassified().isEmpty()) {
            return ScenarioResult.pass("placeholder owner source files are free of direct Locutus singleton access");
        }

        return ScenarioResult.unresolved(formatPlaceholderOwnerDebtDetail(summary.appOnlyClassified()));
    }

    static String formatPlaceholderOwnerDebtDetail(List<PlaceholderOwnerFailure> appOnlyClassified) {
        StringBuilder detail = new StringBuilder(
                "classified live-app placeholder owners remain unresolved migration work");
        detail.append("\nowner-layer debt summary: ")
                .append(appOnlyClassified.size())
                .append(" files, ")
                .append(totalMatchCount(appOnlyClassified))
                .append(" direct Locutus singleton call sites");
        appendPlaceholderOwnerFailures(detail, "hotspots:", appOnlyClassified);
        return detail.toString();
    }

    private static void appendPlaceholderOwnerFailures(StringBuilder detail,
            String heading,
            List<PlaceholderOwnerFailure> failures) {
        detail.append("\n").append(heading);
        List<PlaceholderOwnerFailure> ordered = failures.stream()
                .sorted(Comparator.comparingInt(PlaceholderOwnerFailure::matchCount)
                        .reversed()
                        .thenComparing(PlaceholderOwnerFailure::relativePath))
                .toList();
        int limit = Math.min(MAX_OWNER_HOTSPOTS, ordered.size());
        for (int i = 0; i < limit; i++) {
            PlaceholderOwnerFailure failure = ordered.get(i);
            detail.append("\n- ").append(failure.typeName())
                    .append(" -> ").append(failure.relativePath())
                    .append(" (").append(failure.matchCount()).append(" matches)");
            appendMatchSamples(detail, failure.matches());
        }
        if (ordered.size() > limit) {
            detail.append("\n- ... ").append(ordered.size() - limit).append(" more owner files");
        }
    }

    private static int totalMatchCount(List<PlaceholderOwnerFailure> failures) {
        return failures.stream().mapToInt(PlaceholderOwnerFailure::matchCount).sum();
    }

    private ScenarioResult liveAppCommandHelperAudit() {
        List<Class<?>> helperTypes = List.of(AdminCommands.class, IACommands.class, UtilityCommands.class);
        List<String> missingMarkers = new ArrayList<>();
        List<String> classifiedHelpers = new ArrayList<>();
        for (Class<?> type : helperTypes) {
            LiveAppCommandSurface marker = type.getAnnotation(LiveAppCommandSurface.class);
            if (marker == null) {
                missingMarkers.add(type.getName());
                continue;
            }

            String detail = type.getName();
            if (!marker.value().isBlank()) {
                detail += " -> " + marker.value();
            }
            classifiedHelpers.add(detail);
        }

        if (!missingMarkers.isEmpty()) {
            return ScenarioResult.fail(
                    "intentionally live-app-only helper commands must be marked with @LiveAppCommandSurface:\n- "
                            + String.join("\n- ", missingMarkers));
        }

        return ScenarioResult.pass("intentionally live-app-only helper commands are explicitly marked:\n- "
                + String.join("\n- ", classifiedHelpers));
    }

    private ScenarioResult sourceAudit(SourceRule rule) throws IOException {
        Path file = projectRoot().resolve(rule.relativePath());
        if (!Files.exists(file)) {
            return ScenarioResult.fail("missing file: " + rule.relativePath());
        }

        List<SourceMatch> matches = findForbiddenMatches(file, rule.forbiddenPatterns());

        if (!matches.isEmpty()) {
            return ScenarioResult.fail(rule.relativePath() + " still contains forbidden patterns:\n- "
                    + matches.stream().map(SourceMatch::describe).collect(Collectors.joining("\n- ")));
        }
        return ScenarioResult.pass(rule.relativePath() + " is clean");
    }

    private List<SourceMatch> findForbiddenMatches(Path file, List<String> forbiddenPatterns) throws IOException {
        List<String> lines = Files.readAllLines(file);
        List<SourceMatch> matches = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();
            if (trimmed.isEmpty()
                    || trimmed.startsWith("//")
                    || trimmed.startsWith("/*")
                    || trimmed.startsWith("*")
                    || trimmed.startsWith("*/")) {
                continue;
            }
            int hitCount = 0;
            for (String forbidden : forbiddenPatterns) {
                hitCount += countOccurrences(line, forbidden);
            }
            if (hitCount > 0) {
                matches.add(new SourceMatch(i + 1, trimmed, hitCount));
            }
        }
        return matches;
    }

    private static int countOccurrences(String line, String token) {
        int count = 0;
        int start = 0;
        while (start >= 0) {
            start = line.indexOf(token, start);
            if (start >= 0) {
                count++;
                start += token.length();
            }
        }
        return count;
    }

    private static Path sourceFileForType(Class<?> type) {
        String typeName = type.getName();
        int nestedTypeIndex = typeName.indexOf('$');
        if (nestedTypeIndex >= 0) {
            typeName = typeName.substring(0, nestedTypeIndex);
        }
        return projectRoot().resolve(MAIN_SOURCE_ROOT)
                .resolve(typeName.replace('.', File.separatorChar) + ".java");
    }

    private static String relativePath(Path file) {
        return projectRoot().relativize(file).toString().replace('\\', '/');
    }

    private static void appendMatchSamples(StringBuilder detail, List<SourceMatch> matches) {
        int sampleCount = Math.min(MAX_MATCH_SAMPLES_PER_FILE, matches.size());
        for (int i = 0; i < sampleCount; i++) {
            detail.append("\n  ").append(matches.get(i).describe());
        }
        if (matches.size() > sampleCount) {
            detail.append("\n  ... ").append(matches.size() - sampleCount).append(" more matching lines");
        }
    }

    private ScenarioResult runScenario(String name, ScenarioSupplier supplier) {
        try {
            ScenarioResult result = supplier.run();
            return result.withName(name);
        } catch (Throwable t) {
            return ScenarioResult.fail(name,
                    t.getClass().getSimpleName() + (t.getMessage() == null ? "" : ": " + t.getMessage()));
        }
    }

    private static Path projectRoot() {
        return Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
    }

    @FunctionalInterface
    private interface ScenarioSupplier {
        ScenarioResult run() throws Exception;
    }

    record AuditOptions(boolean strict, boolean runtimeOnly, boolean sourceOnly) {
        private static AuditOptions parse(String[] args) {
            boolean strict = false;
            boolean runtimeOnly = false;
            boolean sourceOnly = false;
            for (String arg : args) {
                switch (arg) {
                    case "--strict" -> strict = true;
                    case "--runtime-only" -> runtimeOnly = true;
                    case "--source-only" -> sourceOnly = true;
                    default -> throw new IllegalArgumentException("Unknown argument: " + arg);
                }
            }
            if (runtimeOnly && sourceOnly) {
                runtimeOnly = false;
                sourceOnly = false;
            }
            return new AuditOptions(strict, runtimeOnly, sourceOnly);
        }
    }

    record SourceRule(String name, String relativePath, List<String> forbiddenPatterns) {
    }

    record SourceMatch(int lineNumber, String line, int hitCount) {
        String describe() {
            if (hitCount == 1) {
                return "line " + lineNumber + ": " + line;
            }
            return "line " + lineNumber + " (" + hitCount + " hits): " + line;
        }
    }

    record PlaceholderOwnerFailure(String typeName, String relativePath, List<SourceMatch> matches) {
        int matchCount() {
            return matches.stream().mapToInt(SourceMatch::hitCount).sum();
        }
    }

    record PlaceholderOwnerAuditSummary(List<String> missingCoverage,
            List<PlaceholderOwnerFailure> neutralFailures,
            List<PlaceholderOwnerFailure> appOnlyClassified) {
        boolean hasAuditFailures() {
            return !missingCoverage.isEmpty() || !neutralFailures.isEmpty();
        }
    }

    enum ScenarioStatus {
        PASS("[PASS] "),
        UNRESOLVED("[UNRESOLVED] "),
        FAIL("[FAIL] ");

        private final String prefix;

        ScenarioStatus(String prefix) {
            this.prefix = prefix;
        }

        String prefix() {
            return prefix;
        }
    }

    static final class AuditReport {
        private final List<ScenarioResult> results = new ArrayList<>();

        void add(ScenarioResult result) {
            results.add(result);
        }

        boolean hasFailures() {
            return results.stream().anyMatch(ScenarioResult::failed);
        }

        boolean hasUnresolved() {
            return results.stream().anyMatch(ScenarioResult::unresolved);
        }

        String render(boolean strictMode) {
            long passed = results.stream().filter(ScenarioResult::passed).count();
            long unresolved = results.stream().filter(ScenarioResult::unresolved).count();
            long failed = results.stream().filter(ScenarioResult::failed).count();
            String newline = System.lineSeparator();
            StringBuilder out = new StringBuilder();

            out.append("Command Runtime Migration Audit").append(newline);
            out.append("Project root: ").append(projectRoot()).append(newline);
            out.append("Mode: ").append(strictMode ? "strict" : "report").append(newline);
            out.append(newline);

            for (ScenarioResult result : results) {
                out.append(result.status().prefix()).append(result.name()).append(newline);
                if (result.detail() != null && !result.detail().isBlank()) {
                    for (String line : result.detail().split("\\R")) {
                        out.append("  ").append(line).append(newline);
                    }
                }
                out.append(newline);
            }

            out.append("Summary: ")
                    .append(passed).append(" passed, ")
                    .append(unresolved).append(" unresolved, ")
                    .append(failed).append(" failed").append(newline);
            if (unresolved > 0) {
                out.append("Unresolved scenarios remain migration work and are reported without changing the exit code.")
                        .append(newline);
            }
            if (!strictMode && failed > 0) {
                out.append("Strict mode is off, so failures are reported without changing the exit code.")
                        .append(newline);
            }
            return out.toString();
        }

        private void print(boolean strictMode) {
            System.out.print(render(strictMode));
        }
    }

    record ScenarioResult(String name, ScenarioStatus status, String detail) {
        private static ScenarioResult pass(String detail) {
            return new ScenarioResult("", ScenarioStatus.PASS, detail);
        }

        private static ScenarioResult unresolved(String detail) {
            return new ScenarioResult("", ScenarioStatus.UNRESOLVED, detail);
        }

        private static ScenarioResult fail(String detail) {
            return new ScenarioResult("", ScenarioStatus.FAIL, detail);
        }

        private static ScenarioResult fail(String name, String detail) {
            return new ScenarioResult(name, ScenarioStatus.FAIL, detail);
        }

        boolean passed() {
            return status == ScenarioStatus.PASS;
        }

        boolean unresolved() {
            return status == ScenarioStatus.UNRESOLVED;
        }

        boolean failed() {
            return status == ScenarioStatus.FAIL;
        }

        private ScenarioResult withName(String updatedName) {
            return new ScenarioResult(updatedName, status, detail);
        }
    }

    private static final class AuditFixture {
        private final TestNationSnapshot snapshot;
        private final CommandRuntimeServices services;
        private final ValidatorStore validators;
        private final PermissionHandler permisser;
        private final link.locutus.discord.commands.manager.v2.binding.ValueStore store;

        private AuditFixture(TestNationSnapshot snapshot,
                CommandRuntimeServices services,
                link.locutus.discord.commands.manager.v2.binding.ValueStore store,
                ValidatorStore validators,
                PermissionHandler permisser) {
            this.snapshot = snapshot;
            this.services = services;
            this.store = store;
            this.validators = validators;
            this.permisser = permisser;
        }

        private static AuditFixture create() {
            TestNationSnapshot snapshot = new TestNationSnapshot(Set.of(
                    nation(1, "Alpha", "LeaderA", 10),
                    nation(2, "Beta", "LeaderB", 20),
                    nation(3, "Gamma", "LeaderC", 10)));
            Set<DBAlliance> alliances = Set.of(
                    alliance(10, "Rose"),
                    alliance(20, "Eclipse"));
            CommandRuntimeServices services = CommandRuntimeServices.builder(NationSnapshotService.fixed(snapshot))
                    .discordUserById(userId -> null)
                    .discordRegistrationById(userId -> userId == REGISTERED_BETA_DISCORD_ID
                        ? new PNWUser(2, userId, "Beta#0001")
                        : null)
                    .alliances(() -> alliances)
                    .build();
            link.locutus.discord.commands.manager.v2.binding.ValueStore store = PWBindings.createDefaultStore();
            new PWCompleter().register(store);
            return new AuditFixture(
                    snapshot,
                    services,
                store,
                    PWBindings.createDefaultValidators(),
                    PWBindings.createDefaultPermisser());
        }

        private PlaceholdersMap createMap(boolean initCommands) {
            PlaceholdersMap map = new PlaceholdersMap(store, validators, permisser, services);
            return initCommands ? map.initCommands() : map.initParsing();
        }

        private PlaceholdersMap createAppMap() {
            new PWAppBindings().register(store);
            return new PlaceholdersMap(store, validators, permisser, services).initAppCommands();
        }

        private static DBNation nation(int id, String name, String leader, int allianceId) {
            SimpleDBNation nation = new SimpleDBNation(new DBNationData());
            nation.setNation_id(id);
            nation.setNation(name);
            nation.setLeader(leader);
            nation.setAlliance_id(allianceId);
            return nation;
        }

        private static DBAlliance alliance(int id, String name) {
            return new DBAlliance(id, name, "", "", "", "", "", 0L, NationColor.GRAY,
                    (Int2ObjectOpenHashMap<byte[]>) null);
        }
    }

    private static User user(long id, String name) {
        return proxy(User.class, (proxy, method, args) -> switch (method.getName()) {
            case "getIdLong" -> id;
            case "getId" -> Long.toString(id);
            case "getName" -> name;
            case "getGlobalName" -> null;
            case "isBot", "isSystem" -> false;
            default -> defaultValue(proxy, method, args);
        });
    }

    private static Member member(User user, String nickname) {
        return proxy(Member.class, (proxy, method, args) -> switch (method.getName()) {
            case "getUser" -> user;
            case "getIdLong" -> user.getIdLong();
            case "getId" -> user.getId();
            case "getNickname" -> nickname;
            default -> defaultValue(proxy, method, args);
        });
    }

    private static Guild guild(long id, List<Member> members) {
        return proxy(Guild.class, (proxy, method, args) -> switch (method.getName()) {
            case "getIdLong" -> id;
            case "getId" -> Long.toString(id);
            case "getMembers" -> members;
            case "isMember" -> members.stream().anyMatch(member -> member.getUser().getIdLong() == ((User) args[0]).getIdLong());
            default -> defaultValue(proxy, method, args);
        });
    }

    private static JDA jda(List<User> users, List<Guild> guilds) {
        Map<Long, User> usersById = new LinkedHashMap<>();
        for (User user : users) {
            usersById.put(user.getIdLong(), user);
        }
        Map<Long, Guild> guildsById = new LinkedHashMap<>();
        for (Guild guild : guilds) {
            guildsById.put(guild.getIdLong(), guild);
        }
        return proxy(JDA.class, (proxy, method, args) -> switch (method.getName()) {
            case "getGuilds" -> guilds;
            case "getUsers" -> users;
            case "getUserById" -> usersById.get(idArg(args[0]));
            case "getGuildById" -> guildsById.get(idArg(args[0]));
            default -> defaultValue(proxy, method, args);
        });
    }

    private static long idArg(Object arg) {
        if (arg instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(arg));
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
    }

    private static Object defaultValue(Object proxy, Method method, Object[] args) {
        if (method.getDeclaringClass() == Object.class) {
            return switch (method.getName()) {
                case "equals" -> proxy == args[0];
                case "hashCode" -> System.identityHashCode(proxy);
                case "toString" -> method.getDeclaringClass().getSimpleName() + "Proxy";
                default -> null;
            };
        }
        Class<?> returnType = method.getReturnType();
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == byte.class) {
            return (byte) 0;
        }
        if (returnType == short.class) {
            return (short) 0;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == float.class) {
            return 0f;
        }
        if (returnType == double.class) {
            return 0d;
        }
        if (returnType == char.class) {
            return '\0';
        }
        return null;
    }

    private static void deleteDbFiles(File dbFile) {
        if (dbFile == null) {
            return;
        }
        deleteIfPresent(dbFile);
        deleteIfPresent(new File(dbFile.getAbsolutePath() + "-wal"));
        deleteIfPresent(new File(dbFile.getAbsolutePath() + "-shm"));
    }

    private static void deleteIfPresent(File file) {
        if (file.exists() && !file.delete()) {
            throw new IllegalStateException("Failed to delete test database file: " + file);
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static final class TestNationSnapshot implements INationSnapshot {
        private final Map<Integer, DBNation> nationsById;

        private TestNationSnapshot(Collection<DBNation> nations) {
            this.nationsById = new LinkedHashMap<>();
            for (DBNation nation : nations) {
                nationsById.put(nation.getId(), nation);
            }
        }

        @Override
        public DBNation getNationById(int id) {
            return nationsById.get(id);
        }

        @Override
        public Set<DBNation> getNationsByAlliance(Set<Integer> alliances) {
            return nationsById.values().stream()
                    .filter(nation -> alliances.contains(nation.getAlliance_id()))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }

        @Override
        public DBNation getNationByLeader(String input) {
            return nationsById.values().stream()
                    .filter(nation -> nation.getLeader().equalsIgnoreCase(input))
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public DBNation getNationByName(String input) {
            return nationsById.values().stream()
                    .filter(nation -> nation.getName().equalsIgnoreCase(input))
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public Set<DBNation> getAllNations() {
            return new LinkedHashSet<>(nationsById.values());
        }

        @Override
        public Set<DBNation> getNationsByBracket(int taxId) {
            return Set.of();
        }

        @Override
        public Set<DBNation> getNationsByAlliance(int id) {
            return nationsById.values().stream()
                    .filter(nation -> nation.getAlliance_id() == id)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }

        @Override
        public Set<DBNation> getNationsByColor(NationColor color) {
            return Set.of();
        }
    }
}
