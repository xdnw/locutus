package link.locutus.discord.commands.manager.v2.impl.pw.binding;

import it.unimi.dsi.fastutil.ints.IntLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.IAttack;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.Continent;
import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.apiv1.enums.DepositTypeInfo;
import link.locutus.discord.apiv1.enums.DomesticPolicy;
import link.locutus.discord.apiv1.enums.EscrowMode;
import link.locutus.discord.apiv1.enums.FlowType;
import link.locutus.discord.apiv1.enums.MMRBuyMode;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.NationColor;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv1.enums.Research;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.RssConvertMode;
import link.locutus.discord.apiv1.enums.SuccessType;
import link.locutus.discord.apiv1.enums.TierDeltaMode;
import link.locutus.discord.apiv1.enums.TreatyType;
import link.locutus.discord.apiv1.enums.WarCostByDayMode;
import link.locutus.discord.apiv1.enums.WarCostMode;
import link.locutus.discord.apiv1.enums.WarCostStat;
import link.locutus.discord.apiv1.enums.WarPolicy;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.apiv1.enums.city.building.Building;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.apiv3.enums.AlliancePermission;
import link.locutus.discord.apiv3.enums.NationLootType;
import link.locutus.discord.commands.manager.v2.binding.BindingHelper;
import link.locutus.discord.commands.manager.v2.binding.Parser;
import link.locutus.discord.commands.manager.v2.binding.SimpleValueStore;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.AllianceDepositLimit;
import link.locutus.discord.commands.manager.v2.binding.annotation.AllowDeleted;
import link.locutus.discord.commands.manager.v2.binding.annotation.AllowEmpty;
import link.locutus.discord.commands.manager.v2.binding.annotation.Binding;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.GuildLoan;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.annotation.ReportPerms;
import link.locutus.discord.commands.manager.v2.binding.annotation.StarIsGuild;
import link.locutus.discord.commands.manager.v2.binding.bindings.MathOperation;
import link.locutus.discord.commands.manager.v2.binding.bindings.PlaceholderRegistry;
import link.locutus.discord.commands.manager.v2.binding.bindings.Placeholders;
import link.locutus.discord.commands.manager.v2.binding.bindings.PrimitiveBindings;
import link.locutus.discord.commands.manager.v2.binding.bindings.PrimitiveValidators;
import link.locutus.discord.commands.manager.v2.binding.validator.ValidatorStore;
import link.locutus.discord.commands.manager.v2.command.CommandCallable;
import link.locutus.discord.commands.manager.v2.command.ICommand;
import link.locutus.discord.commands.manager.v2.command.ICommandGroup;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.command.ParameterData;
import link.locutus.discord.commands.manager.v2.impl.discord.binding.DiscordBindings;
import link.locutus.discord.commands.manager.v2.impl.discord.binding.annotation.GuildCoalition;
import link.locutus.discord.commands.manager.v2.impl.discord.binding.annotation.NationDepositLimit;
import link.locutus.discord.commands.manager.v2.impl.pw.NationFilter;
import link.locutus.discord.commands.manager.v2.impl.pw.TaxRate;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.AlliancePlaceholders;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.CommandRuntimeCommandContext;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.CommandRuntimeLookupContext;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.CommandRuntimeLookupService;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.NationModifier;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.NationPlaceholders;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.manager.v2.perm.PermissionHandler;
import link.locutus.discord.commands.war.WarCatReason;
import link.locutus.discord.commands.war.WarCategory;
import link.locutus.discord.commands.war.WarRoom;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.AllianceLookup;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.GuildHandler;
import link.locutus.discord.db.INationSnapshot;
import link.locutus.discord.db.NationDB;
import link.locutus.discord.db.Report;
import link.locutus.discord.db.bank.TaxBracketLookup;
import link.locutus.discord.db.ReportManager;
import link.locutus.discord.db.ReportType;
import link.locutus.discord.db.WarDB;
import link.locutus.discord.db.conflict.Conflict;
import link.locutus.discord.db.conflict.ConflictManager;
import link.locutus.discord.db.conflict.ConflictUtil;
import link.locutus.discord.db.conflict.VirtualConflictStorageManager;
import link.locutus.discord.db.entities.ClearRolesEnum;
import link.locutus.discord.db.entities.Coalition;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBAlliancePosition;
import link.locutus.discord.db.entities.DBBounty;
import link.locutus.discord.db.entities.DBCity;
import link.locutus.discord.db.entities.DBLoan;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.entities.EnemyAlertChannelMode;
import link.locutus.discord.db.entities.GrantRequest;
import link.locutus.discord.db.entities.LoanManager;
import link.locutus.discord.db.entities.MMRDouble;
import link.locutus.discord.db.entities.MMRInt;
import link.locutus.discord.db.entities.MMRMatcher;
import link.locutus.discord.db.entities.MessageTrigger;
import link.locutus.discord.db.entities.NationFilterString;
import link.locutus.discord.db.entities.NationMeta;
import link.locutus.discord.db.entities.Safety;
import link.locutus.discord.db.entities.Status;
import link.locutus.discord.db.entities.TaxBracket;
import link.locutus.discord.db.entities.Treaty;
import link.locutus.discord.db.entities.WarStatus;
import link.locutus.discord.db.entities.conflict.ConflictCategory;
import link.locutus.discord.db.entities.grant.AGrantTemplate;
import link.locutus.discord.db.entities.grant.GrantTemplateManager;
import link.locutus.discord.db.entities.grant.TemplateTypes;
import link.locutus.discord.db.entities.metric.AllianceMetric;
import link.locutus.discord.db.entities.metric.AllianceMetricMode;
import link.locutus.discord.db.entities.metric.GrowthAsset;
import link.locutus.discord.db.entities.metric.MembershipChangeReason;
import link.locutus.discord.db.entities.metric.OrbisMetric;
import link.locutus.discord.db.entities.newsletter.Newsletter;
import link.locutus.discord.db.entities.newsletter.NewsletterManager;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.db.guild.GuildSetting;
import link.locutus.discord.db.guild.GuildSettingCategory;
import link.locutus.discord.db.guild.GuildSettingSubgroup;
import link.locutus.discord.event.mail.MailReceivedEvent;
import link.locutus.discord.pnw.AllianceList;
import link.locutus.discord.pnw.BeigeReason;
import link.locutus.discord.pnw.CityRanges;
import link.locutus.discord.pnw.GuildOrAlliance;
import link.locutus.discord.pnw.NationList;
import link.locutus.discord.pnw.NationOrAlliance;
import link.locutus.discord.pnw.NationOrAllianceOrGuild;
import link.locutus.discord.pnw.NationOrAllianceOrGuildOrTaxid;
import link.locutus.discord.pnw.PNWUser;
import link.locutus.discord.pnw.SimpleNationList;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.AutoAuditType;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.Operation;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.offshore.Auth;
import link.locutus.discord.util.offshore.OffshoreInstance;
import link.locutus.discord.util.offshore.test.IACategory;
import link.locutus.discord.util.scheduler.CachedSupplier;
import link.locutus.discord.util.task.ia.AuditType;
import link.locutus.discord.util.task.mail.Mail;
import link.locutus.discord.web.commands.mcp.DataQueryMode;
import link.locutus.discord.web.commands.mcp.ExecuteMode;
import link.locutus.discord.web.jooby.CloudStorage;
import link.locutus.discord.web.jooby.S3CompatibleStorage;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildMessageChannel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class PWBindings extends BindingHelper {

    private static <T, M> Placeholders<T, M> requirePlaceholders(ValueStore store, Class<T> type) {
        PlaceholderRegistry registry = PlaceholderRegistry.resolve(store);
        if (registry == null) {
            throw new IllegalStateException("PlaceholderRegistry was not provided in the current value store");
        }
        Placeholders<T, M> placeholders = registry.get(type);
        if (placeholders == null) {
            throw new IllegalStateException("No placeholders registered for type: " + type.getSimpleName());
        }
        return placeholders;
    }

    private static NationPlaceholders requireNationPlaceholders(ValueStore store) {
        Placeholders<DBNation, ?> placeholders = requirePlaceholders(store, DBNation.class);
        if (!(placeholders instanceof NationPlaceholders nationPlaceholders)) {
            throw new IllegalStateException("Registered DBNation placeholders are not NationPlaceholders");
        }
        return nationPlaceholders;
    }

    private static NationModifier liveNationSelection(boolean allowDeleted) {
        return new NationModifier(null, allowDeleted, false);
    }

    public static DBBounty parseBounty(WarDB warDb, String input) {
        int id = PrimitiveBindings.Integer(input);
        DBBounty bounty = warDb.getBountyById(id);
        if (bounty == null) {
            throw new IllegalArgumentException("No bounty found with id: `" + id + "`");
        }
        return bounty;
    }

    public static ICommand<?> parseSlashCommand(CommandRuntimeCommandContext services, String input) {
        input = stripPrefix(input);
        List<String> split = StringMan.split(input, ' ');
        CommandCallable command = services.getCommand(split);
        if (command == null)
            throw new IllegalArgumentException("No command found for `" + input + "`");
        if (command instanceof ICommandGroup group) {
            String prefix = group.getFullPath();
            if (!prefix.isEmpty())
                prefix += " ";
            String optionsStr = "- `" + prefix + String.join("`\n- `" + prefix, group.primarySubCommandIds()) + "`";
            throw new IllegalArgumentException("Command `" + input
                    + "` is a group, not an endpoint. Please specify a sub command:\n" + optionsStr);
        }
        if (!(command instanceof ICommand<?>))
            throw new IllegalArgumentException("Command `" + input + "` is not a command endpoint");
        return (ICommand<?>) command;
    }

    public static DBCity parseCityUrl(NationDB nationDb, String input) {
        int cityId;
        if (input.contains("city/id=")) {
            cityId = Integer.parseInt(input.split("=")[1]);
        } else if (MathMan.isInteger(input)) {
            cityId = Integer.parseInt(input);
        } else {
            throw new IllegalArgumentException("Not a valid city url: `" + input + "`");
        }
        DBCity cityEntry = nationDb.getCitiesV3ByCityId(cityId);
        if (cityEntry == null)
            throw new IllegalArgumentException(
                    "No city found in cache for id: " + cityId + " (expecting city id or url)");
        nationDb.markCityDirty(cityEntry.getNationId(), cityEntry.getId(), System.currentTimeMillis());
        return cityEntry;
    }

    public static DBWar parseWar(WarDB warDb, String arg0) {
        if (arg0.contains("/war=")) {
            arg0 = arg0.split("=")[1];
        }
        if (!MathMan.isInteger(arg0)) {
            throw new IllegalArgumentException("Not a valid war number: `" + arg0 + "`");
        }
        int warId = Integer.parseInt(arg0);
        DBWar war = warDb.getWar(warId);
        if (war == null)
            throw new IllegalArgumentException("No war founds for id: `" + warId + "`");
        return war;
    }

    public static DBNation parseNation(CommandRuntimeLookupContext services, User selfUser, Guild guild, String input,
            ParameterData data) {
        boolean allowDeleted = data != null && data.getAnnotation(AllowDeleted.class) != null;
        INationSnapshot snapshot = services.nationSnapshots().resolve(null);
        CommandRuntimeLookupService lookup = services.lookup();
        String errMsg = null;
        DBNation nation = null;
        try {
            nation = lookup.parseNation(snapshot, input, allowDeleted, true, guild);
        } catch (IllegalArgumentException e) {
            errMsg = e.getMessage();
        }
        if (nation == null) {
            if (selfUser != null && (input.equalsIgnoreCase("%user%") || input.equalsIgnoreCase("{usermention}"))) {
                if (allowDeleted) {
                    PNWUser user = lookup.getRegisteredUser(selfUser);
                    if (user != null) {
                        return lookup.getNationOrCreate(snapshot, user.getNationId());
                    }
                }
                nation = lookup.getNationByUser(snapshot, selfUser, false);
            } else if (MathMan.isInteger(input) && allowDeleted) {
                return lookup.getNationOrCreate(snapshot, Integer.parseInt(input));
            }
            if (nation == null) {
                if (errMsg == null || errMsg.isEmpty()) {
                    errMsg = "No such nation: `" + input + "`";
                }
                if (input.contains(",")) {
                    throw new IllegalArgumentException(
                            errMsg + " (Multiple nations are not accepted for this argument)");
                }
                throw new IllegalArgumentException(errMsg);
            }
        } else if (nation.isValid()) {
            services.markNationDirty(nation.getNation_id());
        }
        return nation;
    }

    public static NationOrAlliance parseNationOrAlliance(CommandRuntimeLookupContext services, ParameterData data, String input,
            boolean forceAllowDeleted, Guild guild) {
        String lower = input.toLowerCase();
        if (lower.startsWith("aa:") || lower.startsWith("alliance:")) {
            return alliance(services, data, input.split(":", 2)[1]);
        }
        if (lower.contains("alliance/id=")) {
            return alliance(services, data, input);
        }
        String errMsg = null;
        try {
            DBNation nation = services.lookup().parseNation(input,
                    forceAllowDeleted || (data != null && data.getAnnotation(AllowDeleted.class) != null), true, guild);
            if (nation != null) {
                if (nation.isValid()) {
                    services.markNationDirty(nation.getNation_id());
                }
                return nation;
            }
        } catch (IllegalArgumentException e) {
            errMsg = e.getMessage();
        }
        DBAlliance alliance = alliance(services, data, input);
        if (alliance != null)
            return alliance;
        throw new IllegalArgumentException(
                "No such nation or alliance: `" + input + "`" + (errMsg != null ? "\n" + errMsg : ""));
    }

    public static NationOrAllianceOrGuildOrTaxid parseNationOrAllianceOrGuildOrTaxId(CommandRuntimeLookupContext services,
            String input, boolean includeTaxId, ParameterData data, GuildDB selfDb) {
        if (data != null && input.equals("*")) {
            if (data.getAnnotation(StarIsGuild.class) != null && selfDb != null) {
                return selfDb;
            }
        }
        boolean allowDeleted = data != null && data.getAnnotation(AllowDeleted.class) != null;
        try {
            return parseNationOrAlliance(services, data, input, allowDeleted, selfDb == null ? null : selfDb.getGuild());
        } catch (IllegalArgumentException ignore) {
            if (includeTaxId && !input.startsWith("#") && input.contains("tax_id")) {
                int taxId = PW.parseTaxId(input);
                return new TaxBracket(taxId, -1, "", 0, 0, 0L).withLookup(taxBracketLookup(services));
            }
            if (input.startsWith("guild:")) {
                input = input.substring(6);
                if (!MathMan.isInteger(input)) {
                    for (GuildDB guildDb : services.getGuildDatabases()) {
                        if (guildDb.getName().equalsIgnoreCase(input)
                                || guildDb.getGuild().getName().equalsIgnoreCase(input)) {
                            return guildDb;
                        }
                    }
                }
            }
            if (MathMan.isInteger(input)) {
                long id = Long.parseLong(input);
                if (id > Integer.MAX_VALUE) {
                    GuildDB guildDb = services.getGuildDb(id);
                    if (guildDb == null) {
                        if (data != null && data.getAnnotation(AllowDeleted.class) != null) {
                            throw new IllegalArgumentException(
                                    "Not connected to guild: " + id + " (deleted guilds are not currently supported)");
                        }
                        throw new IllegalArgumentException("Not connected to guild: " + id);
                    }
                    return guildDb;
                }
            }
            for (GuildDB value : services.getGuildDatabases()) {
                if (value.getName().equalsIgnoreCase(input)) {
                    return value;
                }
            }
            throw ignore;
        }
    }

    public static GuildDB resolveGuildDb(CommandRuntimeLookupContext services, long guildId) {
        GuildDB guild = services.getGuildDb(guildId);
        if (guild == null)
            throw new IllegalStateException("No guild found for: " + guildId);
        return guild;
    }

    public static Guild resolveGuild(CommandRuntimeLookupContext services, long guildId) {
        Guild guild = services.getGuild(guildId);
        if (guild == null)
            throw new IllegalStateException("No guild found for: " + guildId);
        return guild;
    }

    public static TaxBracketLookup taxBracketLookup(CommandRuntimeLookupContext services) {
        return services.taxBracketLookup();
    }

    public static TaxBracket parseBracket(TaxBracketLookup lookup, GuildDB db, String input, long cache,
            boolean fetchFromApiIfId) {
        Integer taxId;
        if (MathMan.isInteger(input)) {
            taxId = Integer.parseInt(input);
        } else if (input.toLowerCase(Locale.ROOT).contains("tax_id")) {
            taxId = PW.parseTaxId(input);
        } else {
            taxId = null;
        }
        if ((!fetchFromApiIfId || db == null) && taxId != null) {
            int allianceId = lookup.getAllianceIdByTaxId(taxId);
            if (fetchFromApiIfId && allianceId == 0) {
                throw new IllegalArgumentException("No nation found with tax id: `" + taxId + "`");
            }
            return new TaxBracket(taxId, allianceId == 0 ? -1 : allianceId, "", -1, -1, 0L)
                    .withLookup(lookup);
        }

        Supplier<Map<Integer, TaxBracket>> bracketsSupplier = new CachedSupplier<>(() -> {
            AllianceList allianceList = db == null ? null : db.getAllianceList();
            if (allianceList == null) {
                throw new IllegalArgumentException(
                        "No alliance registered. See: " + GuildKey.ALLIANCE_ID.getCommandMention());
            }
            return allianceList.getTaxBrackets(lookup, cache);
        });

        if (input.matches("[0-9]+/[0-9]+")) {
            String[] split = input.split("/");
            int moneyRate = Integer.parseInt(split[0]);
            int rssRate = Integer.parseInt(split[1]);
            Map<Integer, TaxBracket> brackets = bracketsSupplier.get();
            for (Map.Entry<Integer, TaxBracket> entry : brackets.entrySet()) {
                TaxBracket bracket = entry.getValue();
                if (bracket.moneyRate == moneyRate && bracket.rssRate == rssRate) {
                    return bracket;
                }
            }
            throw new IllegalArgumentException(
                    "No tax bracket found with rates: `" + moneyRate + "/" + rssRate + "`");
        }
        if (taxId != null) {
            Map<Integer, TaxBracket> brackets = bracketsSupplier.get();
            TaxBracket bracket = brackets.get(taxId);
            if (bracket == null) {
                throw new IllegalArgumentException("No tax bracket found with id: `" + taxId + "`");
            }
            return bracket;
        }
        throw new IllegalArgumentException("Invalid tax bracket: `" + input + "`");
    }

    public static ValueStore createDefaultStore() {
        ValueStore store = new SimpleValueStore();
        new PrimitiveBindings().register(store);
        new DiscordBindings().register(store);
        new PWBindings().register(store);
        // Composition roots opt into raw app-service bindings explicitly.
        new GPTBindings().register(store);
        new SheetBindings().register(store);
        // new StockBinding().register(store);
        // new NewsletterBindings().register(store);
        return store;
    }

    public static ValidatorStore createDefaultValidators() {
        ValidatorStore validators = new ValidatorStore();
        new PrimitiveValidators().register(validators);
        return validators;
    }

    public static PermissionHandler createDefaultPermisser() {
        PermissionHandler permisser = new PermissionHandler();
        new PermissionBinding().register(permisser);
        return permisser;
    }

    @Binding(value = "The name of a stored conflict between two coalitions")
    public static Conflict conflict(ConflictManager manager, String nameOrId) {
        if (nameOrId != null && nameOrId.startsWith("n/")) {
            ConflictUtil.VirtualConflictId virtualId = ConflictUtil.parseVirtualConflictWebId(nameOrId);
            CloudStorage cloud = manager.getCloud();
            if (cloud == null) {
                throw new IllegalArgumentException(
                        "Cannot load virtual conflict `" + nameOrId + "`: cloud storage is not configured");
            }
            if (!(cloud instanceof S3CompatibleStorage s3)) {
                throw new IllegalArgumentException(
                        "Cannot load virtual conflict `" + nameOrId + "`: unsupported cloud storage implementation");
            }
            return new VirtualConflictStorageManager(s3).loadConflict(virtualId);
        }
        Conflict conflict = manager.getConflict(nameOrId);
        if (conflict != null) {
            return conflict;
        }
        if (MathMan.isInteger(nameOrId)) {
            int id = PrimitiveBindings.Integer(nameOrId);
            conflict = manager.getConflictById(id);
            if (conflict != null)
                return conflict;
        }
        // find by name
        for (Conflict c : manager.getConflictMap().values()) {
            if (c.getName().equalsIgnoreCase(nameOrId)) {
                return c;
            }
        }
        throw new IllegalArgumentException(
                "Unknown conflict: `" + nameOrId + "`. Options: " + StringMan.getString(manager.getConflictNames()));
    }

    @Binding(value = "The name of a stored conflict between two coalitions")
    public Set<Conflict> conflicts(ConflictManager manager, ValueStore store, String input) {
        Set<Conflict> result = requirePlaceholders(store, Conflict.class).parseSet(store, input);
        if (result == null || result.isEmpty()) {
            throw new IllegalArgumentException(
                    "No conflicts found in: " + input + ". Options: " + manager.getConflictNames());
        }
        return result;

    }

    @Binding(value = "A treaty between two alliances\n" +
            "Link two alliances, separated by a colon")
    public static Treaty treaty(CommandRuntimeLookupContext services, String input) {
        String[] split = input.split("[:><]");
        if (split.length != 2)
            throw new IllegalArgumentException(
                    "Invalid input: `" + input + "` - must be two alliances separated by a comma");
        DBAlliance aa1 = alliance(services, split[0].trim());
        DBAlliance aa2 = alliance(services, split[1].trim());
        Treaty treaty = aa1.getTreaties().get(aa2.getId());
        if (treaty == null) {
            throw new IllegalArgumentException("No treaty found between " + aa1.getName() + " and " + aa2.getName());
        }
        return treaty;
    }

    private static String stripPrefix(String input) {
        if (input.startsWith(Settings.INSTANCE.DISCORD.COMMAND.COMMAND_PREFIX)) {
            return input.substring(Settings.INSTANCE.DISCORD.COMMAND.COMMAND_PREFIX.length());
        } else {
            for (String prefix : Settings.INSTANCE.DISCORD.COMMAND.ALTERNATE_COMMAND_PREFIX) {
                if (input.startsWith(prefix)) {
                    return input.substring(prefix.length());
                }
            }
        }
        return input;
    }

    @Binding(value = "A discord slash command reference for the bot")
    public ICommand<?> slashCommand(CommandRuntimeCommandContext services, String input) {
        return parseSlashCommand(services, input);
    }

    @Binding(value = "A grant request id")
    public GrantRequest grantRequest(@Me GuildDB db, int id) {
        GrantRequest request = db.getGrantRequestById(id);
        if (request == null) {
            throw new IllegalArgumentException("No grant request found with id: `" + id + "`");
        }
        return request;
    }

    @Binding(value = """
            A map of city ranges to a list of beige reasons for defeating an enemy in war
            Priority is first to last (so put defaults at the bottom)""", examples = """
            c1-9:*
            c10+:INACTIVE,VACATION_MODE,APPLICANT""")
    public Map<CityRanges, Set<BeigeReason>> beigeReasonMap(@Me GuildDB db, String input) {
        input = input.replace("=", ":");

        Map<CityRanges, Set<BeigeReason>> result = new LinkedHashMap<>();
        String[] split = input.trim().split("\\r?\\n");
        if (split.length == 1)
            split = StringMan.split(input.trim(), ' ').toArray(new String[0]);
        for (String s : split) {
            String[] pair = s.split(":");
            if (pair.length != 2)
                throw new IllegalArgumentException("Invalid `CITY_RANGE:BEIGE_REASON` pair: `" + s + "`");
            CityRanges range = CityRanges.parse(pair[0]);
            List<BeigeReason> list = StringMan.parseEnumList(BeigeReason.class, pair[1]);
            result.put(range, new ObjectLinkedOpenHashSet<>(list));
        }
        return result;
    }

    @Binding(value = "A comma separated list of beige reasons for defeating an enemy in war")
    public Set<BeigeReason> BeigeReasons(String input) {
        return emumSet(BeigeReason.class, input);
    }

    @Binding(value = "A comma separated list of military research")
    public Set<Research> ResearchSet(String input) {
        return emumSet(Research.class, input);
    }

    @Binding(value = "A military research")
    public Research Research(String input) {
        return emum(Research.class, input);
    }

    @Binding(value = "A comma separated list of Growth Assets (cities, projects, infra, land)")
    public Set<GrowthAsset> GrowthAssets(String input) {
        return emumSet(GrowthAsset.class, input);
    }

    @Binding(value = "A Growth Asset (cities, projects, infra, land)")
    public GrowthAsset GrowthAsset(String input) {
        return emum(GrowthAsset.class, input);
    }

    @Binding(value = "A comma separated list of alliance membership change reasons")
    public Set<MembershipChangeReason> membershipChangeReasons(String input) {
        return emumSet(MembershipChangeReason.class, input);
    }

    @Binding(value = "An alliance membership change reason")
    public MembershipChangeReason membershipChangeReason(String input) {
        return emum(MembershipChangeReason.class, input);
    }

    @Binding(value = "A comma separated list of domestic policies")
    public Set<DomesticPolicy> DomesticPolicies(String input) {
        return emumSet(DomesticPolicy.class, input);
    }

    @Binding(value = "A comma separated list of beige reasons for defeating an enemy in war")
    public Set<OrbisMetric> OrbisMetrics(String input) {
        return emumSet(OrbisMetric.class, input);
    }

    @Binding(value = "A comma separated list of attack success types")
    public static Set<SuccessType> SuccessTypes(String input) {
        String[] split = input.split(",");
        Set<SuccessType> result = new ObjectLinkedOpenHashSet<>();
        for (String s : split) {
            result.add(SuccessType.parse(s));
        }
        if (result.isEmpty())
            throw new IllegalArgumentException("No valid success types found for: `" + input + "`");
        return result;
    }

    @Binding(value = "A comma separated list of deposit types")
    public Set<DepositType> DepositTypes(String input) {
        return emumSet(DepositType.class, input);
    }

    @Binding(value = "A comma separated list of the status of a nation's loan")
    public Set<Status> LoanStatuses(String input) {
        return emumSet(Status.class, input);
    }

    @Binding(value = "The status of a nation's loan")
    public Status LoanStatus(String input) {
        return emum(Status.class, input);
    }

    @Binding(value = "The guild setting category")
    public GuildSettingCategory GuildSettingCategory(String input) {
        return emum(GuildSettingCategory.class, input);
    }

    @Binding(value = "The guild setting category subgroup")
    public GuildSettingSubgroup GuildSettingSubgroup(String input) {
        return emum(GuildSettingSubgroup.class, input);
    }

    @Binding(value = "The success type of an attack")
    public SuccessType SuccessType(String input) {
        return emum(SuccessType.class, input);
    }

    @Binding(value = "The category for a conflict")
    public ConflictCategory ConflictCategory(String input) {
        return emum(ConflictCategory.class, input);
    }

    @Binding(examples = { "Borg", "alliance/id=7452", "647252780817448972",
            "tax_id=1234" }, value = "A nation or alliance name, url or id, or a guild id, or a tax id or url")
    public NationOrAllianceOrGuildOrTaxid nationOrAllianceOrGuildOrTaxId(CommandRuntimeLookupContext services, String input,
            @Default ParameterData data, @Default @Me GuildDB db) {
        return nationOrAllianceOrGuildOrTaxId(services, input, true, data, db);
    }

    public NationOrAllianceOrGuildOrTaxid nationOrAllianceOrGuildOrTaxId(CommandRuntimeLookupContext services, String input,
            boolean includeTaxId, @Default ParameterData data, @Default @Me GuildDB selfDb) {
        if (data != null && input.equals("*")) {
            if (data.getAnnotation(StarIsGuild.class) != null && selfDb != null) {
                return selfDb;
            }
        }
        boolean allowDeleted = data != null && data.getAnnotation(AllowDeleted.class) != null;
        try {
            return parseNationOrAlliance(services, data, input, allowDeleted, selfDb == null ? null : selfDb.getGuild());
        } catch (IllegalArgumentException ignore) {
            if (includeTaxId && !input.startsWith("#") && input.contains("tax_id")) {
                int taxId = PW.parseTaxId(input);
                return new TaxBracket(taxId, -1, "", 0, 0, 0L).withLookup(taxBracketLookup(services));
            }
            if (input.startsWith("guild:")) {
                input = input.substring(6);
                if (!MathMan.isInteger(input)) {
                    for (GuildDB guildDb : services.getGuildDatabases()) {
                        if (guildDb.getName().equalsIgnoreCase(input)
                                || guildDb.getGuild().getName().equalsIgnoreCase(input)) {
                            return guildDb;
                        }
                    }
                }
            }
            if (MathMan.isInteger(input)) {
                long id = Long.parseLong(input);
                if (id > Integer.MAX_VALUE) {
                    GuildDB guildDb = services.getGuildDb(id);
                    if (guildDb == null) {
                        if (data != null && data.getAnnotation(AllowDeleted.class) != null) {
                            throw new IllegalArgumentException(
                                    "Not connected to guild: " + id + " (deleted guilds are not currently supported)");
                        }
                        throw new IllegalArgumentException("Not connected to guild: " + id);
                    }
                    return guildDb;
                }
            }
            for (GuildDB value : services.getGuildDatabases()) {
                if (value.getName().equalsIgnoreCase(input)) {
                    return value;
                }
            }
            throw ignore;
        }
    }


    @Binding(value = "A guild loan by id", examples = { "1234" })
    @GuildLoan
    public DBLoan loan(LoanManager manager, String input) {
        int id = PrimitiveBindings.Integer(input);
        DBLoan loan = manager.getLoanById(id);
        if (loan == null) {
            throw new IllegalArgumentException("No loan found for id `" + id + "`");
        }
        return loan;
    }

    @Binding(value = "A reason beiging and defeating an enemy in war")
    public BeigeReason BeigeReason(String input) {
        return emum(BeigeReason.class, input);
    }

    @Binding(value = "A category for a grant template")
    public TemplateTypes GrantTemplate(String input) {
        return emum(TemplateTypes.class, input);
    }

    @Binding(value = "The name of a created grant template")
    public AGrantTemplate AGrantTemplate(GrantTemplateManager manager, @Me GuildDB db, String input) {
        if (input.contains("/")) {
            input = input.substring(input.lastIndexOf('/') + 1);
        }
        String finalInput = input;
        Set<AGrantTemplate> found = manager.getTemplateMatching(f -> f.getName().equalsIgnoreCase(finalInput));
        if (found.isEmpty())
            throw new IllegalArgumentException(
                    "No grant template found for `" + input + "` see: " + CM.grant_template.list.cmd.toSlashMention());
        if (found.size() > 1)
            throw new IllegalArgumentException("Multiple grant templates found for `" + input + "`");
        return found.iterator().next();
    }

    @Binding(value = "An alert mode for the ENEMY_ALERT_CHANNEL when enemies leave beige")
    public EnemyAlertChannelMode EnemyAlertChannelMode(String input) {
        return emum(EnemyAlertChannelMode.class, input);
    }

    @Binding(value = "A city building type")
    public static Building getBuilding(String input) {
        Building building = Buildings.get(input);
        if (building == null) {
            throw new IllegalArgumentException("No building found for `" + input + "`\n" +
                    "Options: `"
                    + Arrays.stream(Buildings.values()).map(Building::nameUpperUnd).collect(Collectors.joining("`, `"))
                    + "`");
        }
        return building;
    }

    @Binding("An string matching for a nation's military buildings (MMR)\n" +
            "In the form `505X` where `X` is any military building")
    public MMRMatcher mmrMatcher(String input) {
        return new MMRMatcher(input);
    }

    @Binding(value = """
            A map of nation filters to MMR
            Use X for any military building
            All nation filters are supported (e.g. roles)
            Priority is first to last (so put defaults at the bottom)""", examples = """
            #cities<10:505X
            #cities>=10:0250""",
    webType = {Map.class, Set.class, DBNation.class, MMRMatcher.class})
    public Map<NationFilter, MMRMatcher> mmrMatcherMap(@Me GuildDB db, String input, @Default @Me User author,
            @Default @Me DBNation nation) {
        Map<NationFilter, MMRMatcher> filterToMMR = new LinkedHashMap<>();
        for (String line : input.split("\n")) {
            String[] split = line.split("[:]");
            if (split.length != 2)
                continue;

            String filterStr = split[0].trim();

            boolean containsNation = false;
            for (String arg : filterStr.split(",")) {
                if (!arg.startsWith("#"))
                    containsNation = true;
                if (arg.contains("tax_id="))
                    containsNation = true;
                if (arg.startsWith("https://docs.google.com/spreadsheets/") || arg.startsWith("sheet:"))
                    containsNation = true;
            }
            if (!containsNation)
                filterStr += ",*";
            NationFilterString filter = new NationFilterString(filterStr, db.getGuild(), author, nation);
            MMRMatcher mmr = new MMRMatcher(split[1]);
            filterToMMR.put(filter, mmr);
        }

        return filterToMMR;
    }

    @Binding(value = """
            Auto assign roles based on conditions
            See: <https://github.com/xdnw/locutus/wiki/nation_placeholders>
            Accepts a list of filters to a role.
            In the form:
            ```
            #cities<10:@someRole
            #cities>=10:@otherRole
            ```
            Use `*` as the filter to match all nations.
            Only alliance members can be given role""",
            webType = {Map.class, Set.class, DBNation.class, Role.class})
    public Map<NationFilter, Role> conditionalRole(@Me GuildDB db, String input, @Default @Me User author,
            @Default @Me DBNation nation) {
        Map<NationFilter, Role> filterToRole = new LinkedHashMap<>();
        for (String line : input.split("\n")) {
            int index = line.lastIndexOf(":");
            if (index == -1) {
                continue;
            }
            String part1 = line.substring(0, index);
            String part2 = line.substring(index + 1);
            String filterStr = part1.trim();
            NationFilterString filter = new NationFilterString(filterStr, db.getGuild(), author, nation);
            Role role = DiscordBindings.role(db.getGuild(), part2);
            filterToRole.put(filter, role);
        }
        return filterToRole;
    }

    @Binding(value = """
            A map of nation filters to tax rates
            All nation filters are supported (e.g. roles)
            Priority is first to last (so put defaults at the bottom)""", examples = """
            #cities<10:100/100
            #cities>=10:25/25""",
            webType = {Map.class, Set.class, DBNation.class, TaxRate.class})
    public Map<NationFilter, TaxRate> taxRateMap(@Default @Me User author, @Default @Me DBNation nation, @Me GuildDB db,
            String input) {
        Map<NationFilter, TaxRate> filterToTaxRate = new LinkedHashMap<>();
        for (String line : input.split("\n")) {
            String[] split = line.split("[:]");
            if (split.length != 2)
                continue;

            String filterStr = split[0].trim();

            boolean containsNation = false;
            for (String arg : filterStr.split(",")) {
                if (!arg.startsWith("#"))
                    containsNation = true;
                if (arg.contains("tax_id="))
                    containsNation = true;
                if (arg.startsWith("https://docs.google.com/spreadsheets/") || arg.startsWith("sheet:"))
                    containsNation = true;
            }
            if (!containsNation)
                filterStr += ",*";
            NationFilterString filter = new NationFilterString(filterStr, db.getGuild(), author, nation);
            TaxRate rate = new TaxRate(split[1]);
            filterToTaxRate.put(filter, rate);
        }
        if (filterToTaxRate.isEmpty())
            throw new IllegalArgumentException("No valid nation filters provided");

        return filterToTaxRate;
    }

    @Binding(value = """
            A map of nation filters to tax ids
            All nation filters are supported (e.g. roles)
            Priority is first to last (so put defaults at the bottom)""", examples = """
            #cities<10:1
            #cities>=10:2""",
            webType = {Map.class, Set.class, DBNation.class, TaxBracket.class})
    public Map<NationFilter, TaxBracket> taxIdMap(NationDB nationDb, @Default @Me User author,
            @Default @Me DBNation nation, @Me GuildDB db,
            String input) {
        Map<NationFilter, TaxBracket> filterToBracket = new LinkedHashMap<>();
        for (String line : input.split("[\n|;]")) {
            List<String> split = StringMan.split(line, ":", 2);
            if (split.size() != 2)
                continue;

            String filterStr = split.getFirst().trim();
            NationFilterString filter = new NationFilterString(filterStr, db.getGuild(), author, nation);
            TaxBracket bracket = parseBracket(nationDb, db, split.get(1).trim(), Long.MAX_VALUE, false);
            filterToBracket.put(filter, bracket);
        }
        if (filterToBracket.isEmpty())
            throw new IllegalArgumentException("No valid nation filters provided");

        return filterToBracket;
    }

    @Binding(value = "A tax id or url", examples = { "tax_id=1234",
            "https://politicsandwar.com/index.php?id=15&tax_id=1234" })
    public TaxBracket bracket(NationDB nationDb, @Default @Me GuildDB db, String input) {
        return parseBracket(nationDb, db, input, TimeUnit.MINUTES.toMillis(1), true);
    }

    public TaxBracket bracket(NationDB nationDb, @Default @Me GuildDB db, String input, long cache) {
        return parseBracket(nationDb, db, input, cache, true);
    }

    public TaxBracket bracket(NationDB nationDb, @Default @Me GuildDB db, String input, long cache,
            boolean fetchFromApiIfId) {
        return parseBracket(nationDb, db, input, cache, fetchFromApiIfId);
    }

    @Binding(value = "City url", examples = { "city/id=371923" })
    public DBCity cityUrl(NationDB nationDb, String input) {
        return parseCityUrl(nationDb, input);
    }

    @Binding(examples = ("#grant #city=1"), value = "A DepositType optionally with a value and a city tag\n" +
            "See: <https://github.com/xdnw/locutus/wiki/deposits#transfer-notes>", webType = DepositType.class)
    public static DepositTypeInfo DepositTypeInfo(String input) {
        DepositType type = null;
        long value = 0;
        long city = 0;
        boolean ignore = false;
        for (String arg : input.split(" ")) {
            if (arg.equalsIgnoreCase("#ignore")) {
                ignore = true;
                continue;
            }
            if (arg.startsWith("#"))
                arg = arg.substring(1);
            String[] split = arg.split("[=|:]");
            String key = split[0];
            DepositType tmp = StringMan.parseUpper(DepositType.class, key.toUpperCase(Locale.ROOT));
            if (type == null || (type != tmp)) {
                type = tmp;
            } else {
                throw new IllegalArgumentException("Invalid deposit type (duplicate): `" + input + "`");
            }
            if (split.length == 2) {
                long num = Long.parseLong(split[1]);
                if (tmp == DepositType.CITY) {
                    city = num;
                } else {
                    value = num;
                }
            } else if (split.length != 1) {
                throw new IllegalArgumentException("Invalid deposit type (value): `" + input + "`");
            }
        }
        if (type == null) {
            if (ignore) {
                type = DepositType.IGNORE;
            } else {
                throw new IllegalArgumentException("Invalid deposit type (empty): `" + input + "`");
            }
        }
        if (type == DepositType.CITY) {
            value = city;
            city = 0;
        }
        if (DepositType.hasLegacyRootAccountTag(type)) {
            value = 0;
        }
        if (type == DepositType.CASH) {
            value = 0;
        }
        if (type.isReserved()) {
            throw new IllegalArgumentException(
                    "The note `" + type + "` is reserved for internal use. Please use a different note.");
        }
        return new DepositTypeInfo(type, value, city, ignore);
    }

    @Binding(value = "A range of city counts (inclusive)", examples = { "c1-10", "c11+" })
    public CityRanges CityRanges(String input) {
        return CityRanges.parse(input);
    }

    @Binding(value = "A war id or url", examples = { "https://politicsandwar.com/nation/war/timeline/war=1234" })
    public DBWar war(WarDB warDb, String arg0) {
        return parseWar(warDb, arg0);
    }

    @Binding(value = "nation id, name or url", examples = { "Borg", "<@664156861033086987>", "Danzek", "189573",
            "https://politicsandwar.com/nation/id=189573" })
    public DBNation nation(CommandRuntimeLookupContext services, @Default @Me User selfUser, @Me @Default Guild guild, String input,
            @Default ParameterData data) {
        return parseNation(services, selfUser, guild, input, data);
    }

    @Binding(value = "A nation or alliance name, url or id. Prefix with `AA:` or `nation:` to avoid ambiguity if there exists both by the same name or id", examples = {
            "Borg", "https://politicsandwar.com/alliance/id=1234", "aa:1234" })
    public NationOrAlliance nationOrAlliance(CommandRuntimeLookupContext services, String input, @Default ParameterData data,
            @Me @Default Guild guild) {
        return parseNationOrAlliance(services, data, input, false, guild);
    }

    @Binding(value = "4 whole numbers representing barracks,factory,hangar,drydock", examples = { "5553", "0/2/5/0" })
    public MMRInt mmrInt(String input) {
        return MMRInt.fromString(input);
    }

    @Binding(value = "4 decimal numbers representing barracks, factory, hangar, drydock", examples = {
            "0.0/2.0/5.0/0.0", "5553" })
    public MMRDouble mmrDouble(String input) {
        return MMRDouble.fromString(input);
    }

    @Binding(value = "A guild or alliance name, url or id. Prefix with `AA:` or `guild:` to avoid ambiguity if there exists both by the same name or id", examples = {
            "guild:216800987002699787", "aa:1234" })
    public GuildOrAlliance GuildOrAlliance(CommandRuntimeLookupContext services, ParameterData data, String input) {
        String lower = input.toLowerCase();
        if (lower.startsWith("aa:") || lower.startsWith("alliance:")) {
            return alliance(services, data, input.split(":", 2)[1]);
        }
        if (lower.contains("alliance/id=")) {
            return alliance(services, data, input);
        }
        if (lower.startsWith("guild:")) {
            input = input.substring(6);
            if (MathMan.isInteger(input)) {
                return resolveGuildDb(services, Long.parseLong(input));
            }
            throw new IllegalArgumentException("Invalid guild id: " + input);
        }
        if (MathMan.isInteger(input)) {
            long id = Long.parseLong(input);
            return resolveGuildDb(services, id);
        }
        return alliance(services, data, input);
    }

    @Binding
    public NationPlaceholders placeholders(ValueStore store) {
        return (NationPlaceholders) (Placeholders<?, ?>) requirePlaceholders(store, DBNation.class);
    }

    @Binding
    public AlliancePlaceholders aa_placeholders(ValueStore store) {
        return (AlliancePlaceholders) (Placeholders<?, ?>) requirePlaceholders(store, DBAlliance.class);
    }

    @Binding(examples = { "25/25" }, value = "A tax rate in the form of `money/rss`")
    public TaxRate taxRate(String input) {
        if (!input.contains("/"))
            throw new IllegalArgumentException("Tax rate must be in the form: 0/0");
        String[] split = input.split("/");
        int moneyRate = Integer.parseInt(split[0]);
        int rssRate = Integer.parseInt(split[1]);
        return new TaxRate(moneyRate, rssRate);
    }

    @Binding(examples = { "Borg", "alliance/id=7452",
            "647252780817448972" }, value = "A nation or alliance name, url or id, or a guild id")
    public NationOrAllianceOrGuild nationOrAllianceOrGuild(CommandRuntimeLookupContext services, String input,
            @Default ParameterData data, @Default @Me GuildDB db) {
        return (NationOrAllianceOrGuild) parseNationOrAllianceOrGuildOrTaxId(services, input, false, data, db);
    }

    public static DBAlliance alliance(CommandRuntimeLookupContext services, String input) {
        return alliance(services, null, input);
    }

    @Binding(examples = { "'Error 404'", "7413",
            "https://politicsandwar.com/alliance/id=7413" }, value = "An alliance name id or url")
    public static DBAlliance alliance(CommandRuntimeLookupContext services, ParameterData data, String input) {
        Integer aaId = PW.parseAllianceId(input);
        if (aaId == null)
            throw new IllegalArgumentException("Invalid alliance: " + input);
        return services.lookup().getAllianceOrCreate(aaId);
    }

    @Binding(value = "A comma separated list of audit types")
    public Set<AuditType> auditTypes(ValueStore store, String input) {
        Set<AuditType> audits = requirePlaceholders(store, AuditType.class).parseSet(store, input);
        if (audits == null || audits.isEmpty()) {
            throw new IllegalArgumentException(
                    "No audit types found in: " + input + ". Options: " + StringMan.getString(AuditType.values()));
        }
        return audits;
    }

    @Binding(value = "An audit type")
    public static AuditType auditType(String input) {
        return emum(AuditType.class, input);
    }

    @Binding(value = "An in-game  color bloc")
    public static NationColor NationColor(String input) {
        return emum(NationColor.class, input);
    }

    @Binding(value = "A comma separated list of auto audit types")
    public Set<AutoAuditType> autoAuditType(String input) {
        return emumSet(AutoAuditType.class, input);
    }

    @Binding(value = "A comma separated list of continents, or `*`")
    public static Set<Continent> continentTypes(ValueStore store, String input) {
        Set<Continent> result = requirePlaceholders(store, Continent.class).parseSet(store, input);
        if (result == null || result.isEmpty()) {
            throw new IllegalArgumentException(
                    "No projects found in: " + input + ". Options: " + StringMan.getString(Continent.values));
        }
        return result;
    }

    @Binding(value = "A comma separated list of spy operation types")
    public Set<Operation> opTypes(String input) {
        Set<Operation> allowedOpTypes = new ObjectLinkedOpenHashSet<>();
        for (String type : input.split(",")) {
            if (type.equalsIgnoreCase("*")) {
                allowedOpTypes.addAll(Arrays.asList(Operation.values()));
                allowedOpTypes.remove(Operation.INTEL);
            } else {
                Operation op = StringMan.parseUpper(Operation.class, type);
                allowedOpTypes.add(op);
            }
        }
        return allowedOpTypes;
    }

    @Binding(value = "A comma separated list of alliance metrics")
    public Set<AllianceMetric> metrics(String input) {
        Set<AllianceMetric> metrics = new ObjectLinkedOpenHashSet<>();
        for (String type : input.split(",")) {
            AllianceMetric arg = StringMan.parseUpper(AllianceMetric.class, type);
            metrics.add(arg);
        }
        return metrics;
    }

    @Binding(value = "A comma separated list of nation projects")
    public static Set<Project> projects(ValueStore store, String input) {
        Set<Project> result = requirePlaceholders(store, Project.class).parseSet(store, input);
        if (result == null || result.isEmpty()) {
            throw new IllegalArgumentException(
                    "No projects found in: " + input + ". Options: " + StringMan.getString(Projects.values));
        }
        return result;
    }

    @Binding(value = "A comma separated list of building types")
    public Set<Building> buildings(ValueStore store, String input) {
        Set<Building> result = requirePlaceholders(store, Building.class).parseSet(store, input);
        if (result == null || result.isEmpty()) {
            throw new IllegalArgumentException(
                    "No projects found in: " + input + ". Options: " + StringMan.getString(Buildings.values()));
        }
        return result;
    }

    @Binding(examples = "borg,AA:Cataclysm,#position>1", value = "A comma separated list of nations, alliances and filters")
    public static Set<DBNation> nations(ValueStore store, ParameterData data, String input) {
        return nations(store, data, input, false);
    }

    public static Set<DBNation> nations(ValueStore store, ParameterData data, String input,
            boolean forceAllowDeleted) {
        boolean allowDeleted = forceAllowDeleted || (data != null && data.getAnnotation(AllowDeleted.class) != null);
        Set<DBNation> nations = requireNationPlaceholders(store).parseSet(store, input, liveNationSelection(allowDeleted));
        if (nations.isEmpty() && (data == null || data.getAnnotation(AllowEmpty.class) == null)) {
            throw new IllegalArgumentException("No nations found matching: `" + input + "`");
        }
        return nations;
    }

    @Binding(examples = "borg,AA:Cataclysm,#position>1", value = "A comma separated list of nations, alliances and filters", webType = {
            Set.class, DBNation.class })
    public static NationList nationList(ValueStore store, ParameterData data, String input) {
        return new SimpleNationList(nations(store, data, input)).setFilter(input);
    }

    @Binding(examples = "#position>1,#cities<=5", value = "A comma separated list of filters (can include nations and alliances)", webType = {
            Predicate.class, DBNation.class })
    public NationFilter nationFilter(@Default @Me User author, @Default @Me DBNation nation, @Default @Me Guild guild,
            String input) {
        return new NationFilterString(input, guild, author, nation);
    }

    @Binding(examples = "borg,AA:Cataclysm", value = "A comma separated list of nations and alliances")
    public Set<NationOrAlliance> nationOrAlliance(ValueStore store, ParameterData data, @Default @Me Guild guild, String input,
            @Default @Me User author, @Default @Me DBNation me) {
        Set<NationOrAlliance> result = nationOrAlliance(store, data, guild, input, false, author, me);
        boolean allowDeleted = data != null && data.getAnnotation(AllowDeleted.class) != null;
        if (!allowDeleted) {
            result.removeIf(n -> !n.isValid());
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("No nations or alliances found matching: `" + input + "`");
        }
        return result;
    }

    public static Set<NationOrAlliance> nationOrAlliance(ValueStore store, ParameterData data, @Default @Me Guild guild,
            String input, boolean forceAllowDeleted, @Default @Me User author, @Default @Me DBNation me) {
        Placeholders<NationOrAlliance, NationModifier> placeholders = requirePlaceholders(store, NationOrAlliance.class);
        return placeholders.parseSet(guild, author, me, input);
    }

    @Binding(examples = "borg,AA:Cataclysm,647252780817448972", value = "A comma separated list of nations, alliances and guild ids")
    public Set<NationOrAllianceOrGuild> nationOrAllianceOrGuild(ValueStore store, CommandRuntimeLookupContext services,
            ParameterData data, @Default @Me Guild guild, String input, @Default @Me User author,
            @Default @Me DBNation me) {
        return nationOrAllianceOrGuildOrTaxId(store, services, data, guild, input, false, author, me).stream()
            .map(value -> (NationOrAllianceOrGuild) value)
            .collect(Collectors.toCollection(ObjectLinkedOpenHashSet::new));
    }

    @Binding(examples = "borg,AA:Cataclysm,647252780817448972", value = "A comma separated list of nations, alliances, guild ids and tax ids or urls")
    public Set<NationOrAllianceOrGuildOrTaxid> nationOrAllianceOrGuildOrTaxId(ValueStore store,
            CommandRuntimeLookupContext services, ParameterData data, @Default @Me Guild guild, String input,
            @Default @Me User author, @Default @Me DBNation me) {
        return nationOrAllianceOrGuildOrTaxId(store, services, data, guild, input, true, author, me);
    }

    public Set<NationOrAllianceOrGuildOrTaxid> nationOrAllianceOrGuildOrTaxId(ValueStore store,
            CommandRuntimeLookupContext services, ParameterData data, @Default @Me Guild guild, String input,
            boolean includeTaxId, @Default @Me User author, @Default @Me DBNation me) {
        List<String> args = StringMan.split(input, ',');
        Set<NationOrAllianceOrGuildOrTaxid> result = new ObjectLinkedOpenHashSet<>();
        List<String> remainder = new ArrayList<>();
        outer: for (String arg : args) {
            arg = arg.trim();
            if (includeTaxId && !arg.startsWith("#") && arg.contains("tax_id")) {
                int taxId = PW.parseTaxId(arg);
                TaxBracket bracket = new TaxBracket(taxId, -1, "", 0, 0, 0L)
                        .withLookup(taxBracketLookup(services));
                result.add(bracket);
                continue;
            }
            if (arg.startsWith("guild:")) {
                arg = arg.substring(6);
                if (!MathMan.isInteger(arg)) {
                    for (GuildDB guildDb : services.getGuildDatabases()) {
                        if (guildDb.getName().equalsIgnoreCase(arg) || guildDb.getGuild().getName().equalsIgnoreCase(arg)) {
                            result.add(guildDb);
                            continue outer;
                        }
                    }
                    throw new IllegalArgumentException("Unknown guild: " + arg);
                }
            }
            if (MathMan.isInteger(arg)) {
                long id = Long.parseLong(arg);
                if (id > Integer.MAX_VALUE) {
                    GuildDB guildDb = services.getGuildDb(id);
                    if (guildDb == null)
                        throw new IllegalArgumentException("Unknown guild: " + id);
                    result.add(guildDb);
                    continue;
                }
            }

            try {
                DBAlliance aa = alliance(services, data, arg);
                if (aa.exists()) {
                    result.add(aa);
                    continue;
                }
            } catch (IllegalArgumentException ignore) {
            }
            GuildDB guildDb = guild == null ? null : services.getGuildDb(guild);
            if (guildDb != null) {
                if (arg.charAt(0) == '~')
                    arg = arg.substring(1);
                Set<Integer> coalition = guildDb.getCoalition(arg);
                if (!coalition.isEmpty()) {
                    result.addAll(coalition.stream()
                            .map(id -> services.lookup().getAllianceOrCreate(id.intValue()))
                            .collect(Collectors.toSet()));
                    continue;
                }
            }
            remainder.add(arg);
        }
        if (!remainder.isEmpty()) {
            result.addAll(nations(store, data, StringMan.join(remainder, ",")));
        }
        if (result.isEmpty())
            throw new IllegalArgumentException("Invalid nations or alliances: " + input);
        return result;
    }

    public static Set<DBAlliance> alliances(ValueStore store, @Default @Me Guild guild, String input,
            @Default @Me User author, @Default @Me DBNation me) {
        Placeholders<DBAlliance, Void> placeholders = requirePlaceholders(store, DBAlliance.class);
        Set<DBAlliance> alliances = placeholders.parseSet(guild, author, me, input);
        if (alliances.isEmpty())
            throw new IllegalArgumentException("No alliances found for: `" + input + "`");
        return alliances;
    }

    @Binding(examples = "Cataclysm,790", value = "A comma separated list of alliances")
    public Set<DBAlliance> alliances(AlliancePlaceholders placeholders, ValueStore store, String input) {
        return placeholders.parseSet(store, input);
    }
    //
    // @Binding(examples = "Cataclysm,790", value = "A comma separated list of
    // alliances")
    // public static Set<DBAlliance> alliances(ValueStore store, @Default @Me Guild
    // guild, String input, AlliancePlaceholders placeholders) {
    //
    // // make function parsing non hash
    //
    // for (String arg : StringMan.split(input, ',')) {
    // arg = arg.trim();
    // if (arg.isEmpty()) {
    // throw new IllegalArgumentException("Empty argument. Did you use an extra
    // comma? (input: `" + input + "`)");
    // }
    // char char0 = arg.charAt(0);
    // if (char0 == '#') {
    // Predicate<DBAlliance> filter = placeholders.getFilter(store,
    // arg.substring(1));
    // }
    // }
    // }

    @Binding(examples = "ACTIVE,EXPIRED", value = "A comma separated list of war statuses")
    public static Set<WarStatus> WarStatuses(String input) {
        Set<WarStatus> result = new ObjectLinkedOpenHashSet<>();
        for (String s : input.split(",")) {
            result.add(WarStatus.parse(s));
        }
        return result;
    }

    @Binding(examples = "ATTRITION,RAID", value = "A comma separated list of war declaration types")
    public static Set<WarType> WarTypes(String input) {
        return emumSet(WarType.class, input);
    }

    @Binding(examples = "GROUND,VICTORY", value = "A comma separated list of attack types")
    public static Set<AttackType> AttackTypes(ValueStore store, String input) {
        Set<AttackType> result = requirePlaceholders(store, AttackType.class).parseSet(store, input);
        if (result == null || result.isEmpty()) {
            throw new IllegalArgumentException(
                    "No projects found in: " + input + ". Options: " + StringMan.getString(AttackType.values));
        }
        return result;
    }

    @Binding(examples = "SOLDIER,TANK,AIRCRAFT,SHIP,MISSILE,NUKE", value = "A comma separated list of military units")
    public Set<MilitaryUnit> MilitaryUnits(ValueStore store, String input) {
        Set<MilitaryUnit> result = requirePlaceholders(store, MilitaryUnit.class).parseSet(store,
                input);
        if (result == null || result.isEmpty()) {
            throw new IllegalArgumentException(
                    "No projects found in: " + input + ". Options: " + StringMan.getString(MilitaryUnit.values));
        }
        return result;
    }

    @Binding(examples = { "aluminum", "money", "`*`", "manu", "raws",
            "!food" }, value = "A comma separated list of resource types")
    public static Set<ResourceType> rssTypes(String input) {
        Set<ResourceType> types = new ObjectLinkedOpenHashSet<>();
        for (String arg : input.split(",")) {
            boolean remove = arg.startsWith("!");
            if (remove)
                arg = arg.substring(1);
            List<ResourceType> toAddOrRemove;
            if (arg.equalsIgnoreCase("*")) {
                toAddOrRemove = (Arrays.asList(ResourceType.values()));
            } else if (arg.equalsIgnoreCase("manu") || arg.equalsIgnoreCase("manufactured")) {
                toAddOrRemove = Arrays.asList(
                        ResourceType.GASOLINE,
                        ResourceType.MUNITIONS,
                        ResourceType.STEEL,
                        ResourceType.ALUMINUM);
            } else if (arg.equalsIgnoreCase("raws") || arg.equalsIgnoreCase("raw")) {
                toAddOrRemove = Arrays.asList(ResourceType.COAL,
                        ResourceType.OIL,
                        ResourceType.URANIUM,
                        ResourceType.LEAD,
                        ResourceType.IRON,
                        ResourceType.BAUXITE);
            } else {
                toAddOrRemove = Collections.singletonList(ResourceType.parse(arg));
            }
            if (remove)
                types.removeAll(toAddOrRemove);
            else
                types.addAll(toAddOrRemove);
        }
        return new ObjectLinkedOpenHashSet<>(types);
    }

    @AllianceDepositLimit
    @Binding(examples = { "{money=1.2,food=6}", "food 5,money 3", "5f 3$ 10.5c",
            "$53" }, value = "A comma separated list of resources and their amounts, which will be restricted by an alliance's account balance")
    public Map<ResourceType, Double> resourcesAA(String resources) {
        return resources(resources);
    }

    @NationDepositLimit
    @Binding(examples = { "{money=1.2,food=6}", "food 5,money 3", "5f 3$ 10.5c",
            "$53" }, value = "A comma separated list of resources and their amounts, which will be restricted by an nations's account balance")
    public Map<ResourceType, Double> resourcesNation(String resources) {
        return resources(resources);
    }

    @Binding(examples = { "{money=1.2,food=6}", "food 5,money 3", "5f 3$ 10.5c", "$53",
            "{food=1}*1.5" }, value = "A comma separated list of resources and their amounts")
    public Map<ResourceType, Double> resources(String resources) {
        Map<ResourceType, Double> map = ResourceType.parseResources(resources);
        if (map == null)
            throw new IllegalArgumentException("Invalid resources: " + resources);
        return map;
    }

    @Binding(examples = { "{soldiers=12,tanks=56}" }, value = "A comma separated list of units and their amounts")
    public Map<MilitaryUnit, Long> units(String input) {
        Map<MilitaryUnit, Long> map = PW.parseUnits(input);
        if (map == null)
            throw new IllegalArgumentException("Invalid units: " + input + ". Valid types: "
                    + StringMan.getString(MilitaryUnit.values()) + ". In the form: `{SOLDIERS=1234,TANKS=5678}`");
        return map;
    }

    @Binding(examples = {
            "{GROUND_COST=12,AIR_CAPACITY=2}" }, value = "A comma separated list of research and their amounts")
    public static Map<Research, Integer> research(String input) {
        Map<Research, Integer> result = Research.parseMap(input);
        for (Map.Entry<Research, Integer> entry : result.entrySet()) {
            if (entry.getValue() < 0 || entry.getValue() > 20) {
                throw new IllegalArgumentException("Invalid research value: " + entry.getValue() + " for "
                        + entry.getKey() + ". Must be between 0 and 20");
            }
        }
        return result;
    }

    @Binding(examples = { "money", "aluminum" }, value = "The name of a resource")
    public static ResourceType resource(String resource) {
        return ResourceType.parse(resource);
    }

    @Binding(value = "A note to use for a bank transfer")
    public static DepositType DepositType(String input) {
        if (input.startsWith("#"))
            input = input.substring(1);
        DepositType result = StringMan.parseUpper(DepositType.class, input.toUpperCase(Locale.ROOT));
        if (result.isReserved()) {
            throw new IllegalArgumentException(
                    "The note `" + result + "` is reserved for internal use. Please use a different note.");
        }
        return result;
    }

    @Binding(value = "The mode for escrowing funds for a transfer, such as when a receiver is blockaded")
    public static EscrowMode EscrowMode(String input) {
        return emum(EscrowMode.class, input);
    }

    @Binding(value = "A war declaration type")
    public WarType warType(String warType) {
        return WarType.parse(warType);
    }

    @Binding
    @Me
    public DBNation nationProvided(CommandRuntimeLookupContext services, @Default @Me User user) {
        if (user == null) {
            throw new IllegalStateException("No user provided in command locals");
        }
        DBNation nation = services.lookup().getNationByUser(user);
        if (nation == null)
            throw new IllegalStateException("Please use " + CM.register.cmd.toSlashMention());
        services.markNationDirty(nation.getNation_id());
        return nation;
    }

    @Binding
    public MailReceivedEvent MailReceivedEvent() {
        throw new IllegalStateException("No mail event provided in command locals");
    }

    @Binding
    public ConflictManager ConflictManager(WarDB warDb) {
        ConflictManager manager = warDb.getConflicts();
        if (manager == null) {
            throw new IllegalStateException("No conflict manager provided in command locals");
        }
        return manager;
    }

    @Binding
    public Mail Mail() {
        throw new IllegalStateException("No mail provided in command locals");
    }

    @Binding
    @Me
    public IMessageIO io() {
        throw new IllegalArgumentException("No channel io binding found");
    }

    @Binding
    @Me
    public OffshoreInstance offshore(@Me GuildDB db) {
        OffshoreInstance offshore = db.getOffshore();
        if (offshore == null)
            throw new IllegalArgumentException("No offshore is set. See: " + CM.offshore.add.cmd.toSlashMention());
        return offshore;
    }

    @Binding
    @Me
    public DBAlliance alliance(CommandRuntimeLookupContext services, @Me DBNation nation) {
        return services.lookup().getAllianceOrCreate(nation.getAlliance_id());
    }

    @Binding
    @Me
    public Map<ResourceType, Double> deposits(@Me GuildDB db, @Me DBNation nation) throws IOException {
        return ResourceType.resourcesToMap(deposits2(db, nation));
    }

    @Binding
    @Me
    public double[] deposits2(@Me GuildDB db, @Me DBNation nation) throws IOException {
        return nation.getNetDeposits(null, db, false);
    }

    @Binding
    @Me
    public Rank rank(@Me DBNation nation) {
        return Rank.byId(nation.getPosition());
    }

    @Binding(value = "A war status")
    public WarStatus status(String input) {
        return WarStatus.parse(input);
    }

    @Binding(value = "An attack type")
    public static AttackType attackType(String input) {
        return emum(AttackType.class, input);
    }

    @Binding(value = "Mode for automatically giving discord roles")
    public GuildDB.AutoRoleOption roleOption(String input) {
        return emum(GuildDB.AutoRoleOption.class, input);
    }

    @Binding(value = "Mode for automatically giving discord nicknames")
    public GuildDB.AutoNickOption nickOption(String input) {
        return emum(GuildDB.AutoNickOption.class, input);
    }

    @Binding(value = "A war policy")
    public WarPolicy warPolicy(String input) {
        return emum(WarPolicy.class, input);
    }

    @Binding
    public INationSnapshot nationSnapshot(CommandRuntimeLookupContext services) {
        return services.lookup().currentSnapshot();
    }

    @Binding
    public GrantTemplateManager grantTemplateManager(@Me GuildDB db) {
        return db.getGrantTemplateManager();
    }

    @Binding
    @Me
    public GuildDB guildDB(CommandRuntimeLookupContext services, @Me Guild guild) {
        return services.getGuildDb(guild);
    }

    @Binding
    @Me
    public GuildHandler handler(@Me GuildDB db) {
        return db.getHandler();
    }

    @Binding
    public IACategory iaCat(@Me GuildDB db) {
        IACategory iaCat = db.getIACategory();
        if (iaCat == null)
            throw new IllegalArgumentException("No IA category exists (please see: <TODO document>)");
        return iaCat;
    }

    @Binding
    @Me
    public Auth auth(@Me DBNation nation) {
        return nation.getAuth(true);
    }

    @Binding(value = "The reason for a nation's loot being known")
    public NationLootType lootType(String input) {
        return emum(NationLootType.class, input);
    }

    @Binding
    public ReportType reportType(String input) {
        return emum(ReportType.class, input);
    }

    @Binding(value = "One of the default in-game position levels")
    public Rank rank(String rank) {
        return emum(Rank.class, rank);
    }

    @Binding(value = """
            An in-game position
            When there is overlap from multiple alliances registered to the guild, the alliance id must be specified
            In the form: `<alliance>:<position>` such as `1234:Member`""")
    public static DBAlliancePosition position(AllianceLookup lookup, @Me GuildDB db, @Default @Me DBNation nation, String name) {
        AllianceList alliances = db.getAllianceList();
        if (alliances == null || alliances.isEmpty(lookup))
            throw new IllegalArgumentException("No alliances are set. See: " + CM.settings.info.cmd.toSlashMention()
                    + " with key `" + GuildKey.ALLIANCE_ID.name() + "`");

        String[] split = name.split(":", 2);
        Integer aaId = split.length == 2 ? PW.parseAllianceId(split[0]) : null;
        String positionName = split[split.length - 1];

        if (aaId != null && !alliances.contains(aaId))
            throw new IllegalArgumentException(
                    "Alliance " + aaId + " is not in the list of alliances registered to this guild: "
                            + StringMan.getString(alliances.getIds()));
        Set<Integer> aaIds = new IntLinkedOpenHashSet();
        if (aaId != null)
            aaIds.add(aaId);
        else {
            if (nation != null && alliances.contains(nation.getAlliance_id()))
                aaIds.add(nation.getAlliance_id());
            aaIds.addAll(alliances.getIds());
        }
        DBAlliancePosition result = null;
        for (int allianceId : aaIds) {
            result = DBAlliancePosition.parse(positionName, allianceId, true);
        }
        if (result == null) {
            throw new IllegalArgumentException("Unknown position: `" + name +
                    "`. Options: "
                + StringMan.getString(alliances.getPositions(lookup).stream().map(DBAlliancePosition::getQualifiedName)
                            .collect(Collectors.toList()))
                    + " / Special: remove/applicant");
        }
        return result;
    }

    @Binding(value = "In-game permission in an alliance")
    public AlliancePermission alliancePermission(String name) {
        return emum(AlliancePermission.class, name);
    }

    @Binding(value = "A comma separated list of In-game permission in an alliance")
    public Set<AlliancePermission> alliancePermissions(String input) {
        return emumSet(AlliancePermission.class, input);
    }

    @Binding(value = "Bot guild settings")
    public static GuildSetting<?> key(String input) {
        input = input.replaceAll("_", " ").toLowerCase();
        GuildSetting<?>[] constants = GuildKey.values();
        for (GuildSetting<?> constant : constants) {
            String name = constant.name().replaceAll("_", " ").toLowerCase();
            if (name.equals(input))
                return constant;
        }
        List<String> options = Arrays.asList(constants).stream().map(GuildSetting::name).collect(Collectors.toList());
        throw new IllegalArgumentException(
                "Invalid category: `" + input + "`. Options: `" + StringMan.getString(options) + "`");
    }

    @Binding(value = "A registered parser type (key)")
    public Parser<?> parser(ValueStore store, String input) {
        Map<String, Parser> parsersByName = store.getParsersByName();
        Parser<?> parser = parsersByName.get(input);
        if (parser != null) {
            return parser;
        }

        String normalized = input.replace(" ", "").toLowerCase(Locale.ROOT);
        for (Map.Entry<String, Parser> entry : parsersByName.entrySet()) {
            String key = entry.getKey();
            if (key.replace(" ", "").toLowerCase(Locale.ROOT).equals(normalized)) {
                return entry.getValue();
            }
        }
        throw new IllegalArgumentException("Unknown parser type: `" + input + "`");
    }

    @Binding(value = "Types of users to clear roles of")
    public ClearRolesEnum clearRolesEnum(String input) {
        return emum(ClearRolesEnum.class, input);
    }

    @Binding(value = "The mode for calculating war costs")
    public WarCostMode WarCostMode(String input) {
        return emum(WarCostMode.class, input);
    }

    @Binding(value = "The mode for calculating war costs")
    public DataQueryMode DataQueryMode(String input) {
        return emum(DataQueryMode.class, input);
    }

    @Binding(value = "The execution mode for the command")
    public ExecuteMode ExecuteMode(String input) {
        return emum(ExecuteMode.class, input);
    }

    @Binding(value = "The mode for calculating tier deltas")
    public TierDeltaMode TierDeltaMode(String input) {
        return emum(TierDeltaMode.class, input);
    }


    @Binding(value = "The mode for calculating resource conversion")
    public RssConvertMode RssConvertMode(String input) {
        return emum(RssConvertMode.class, input);
    }

    @Binding(value = "A war attack statistic")
    public WarCostStat WarCostStat(String input) {
        return emum(WarCostStat.class, input);
    }

    @Binding(value = "Bank transaction flow type (internal, withdrawal, depost)")
    public static FlowType FlowType(String input) {
        return emum(FlowType.class, input);
    }

    @Binding(examples = { "@role", "672238503119028224", "roleName" }, value = "A discord role name, mention or id")
    public Roles role(String role) {
        return emum(Roles.class, role);
    }

    @Binding(value = "Military unit name")
    public static MilitaryUnit unit(String unit) {
        return emum(MilitaryUnit.class, unit);
    }

    @Binding(value = "Continent name")
    public static Continent continent(String input) {
        return emum(Continent.class, input);
    }

    @Binding(value = "Math comparison operation")
    public MathOperation op(String input) {
        return emum(MathOperation.class, input);
    }

    @Binding(value = "Spy safety level")
    public Safety safety(String input) {
        return emum(Safety.class, input);
    }

    @Binding(value = "One of the default Bot coalition names")
    public Coalition coalition(String input) {
        return emum(Coalition.class, input);
    }

    @Binding(value = "A name for a default or custom Bot coalition")
    @GuildCoalition
    public String guildCoalition(@Me GuildDB db, String input) {
        String normalized = input.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("No coalition provided");
        }
        if (db.getCoalitionNames().contains(normalized)) {
            return normalized;
        }
        Coalition coalition = Coalition.parse(normalized);
        return coalition != null ? coalition.getNameLower() : normalized;
    }

    @Me
    @Binding
    public WarRoom warRoom(@Me WarCategory warCat, @Me TextChannel channel) {
        WarRoom warroom = warCat.getWarRoom((StandardGuildMessageChannel) channel, WarCatReason.COMMAND_ARGUMENT);
        if (warroom == null)
            throw new IllegalArgumentException("The command was not run in a war room");
        return warroom;
    }

    @Me
    @Binding
    public WarCategory warChannelBinding(@Me GuildDB db) {
        WarCategory warChannel = db.getWarChannel(true);
        if (warChannel == null)
            throw new IllegalArgumentException(
                    "War channels are not enabled. " + GuildKey.ENABLE_WAR_ROOMS.getCommandObj(db, true));
        return warChannel;
    }

    @Binding(value = "A project name. Replace spaces with `_`. See: <https://politicsandwar.com/nation/projects/>", examples = "ACTIVITY_CENTER")
    public static Project project(String input) {
        Project project = Projects.get(input);
        if (project == null)
            throw new IllegalArgumentException(
                    "Invalid project: `" + input + "`. Options: " + StringMan.getString(Projects.values));
        return project;
    }

    @Binding(value = "A Bot metric for alliances")
    public AllianceMetric AllianceMetric(String input) {
        return StringMan.parseUpper(AllianceMetric.class, input);
    }

    @Binding(value = "A mode for receiving alerts when a nation leaves beige")
    public NationMeta.BeigeAlertMode BeigeAlertMode(String input) {
        return StringMan.parseUpper(NationMeta.BeigeAlertMode.class, input);
    }

    @Binding(value = "A discord status for receiving alerts when a nation leaves beige")
    public NationMeta.BeigeAlertRequiredStatus BeigeAlertRequiredStatus(String input) {
        return StringMan.parseUpper(NationMeta.BeigeAlertRequiredStatus.class, input);
    }

    @Binding(value = "An in-game treaty type")
    public static TreatyType TreatyType(String input) {
        return TreatyType.parse(input);
    }

    @Binding(value = "A report id", examples = "1234")
    @ReportPerms
    public Report getReport(ReportManager manager, int id) {
        return getReportAll(manager, id);
    }

    @Binding(value = "A report id", examples = "1234")
    public Report getReportAll(ReportManager manager, int id) {
        Report report = manager.getReport(id);
        if (report == null) {
            throw new IllegalArgumentException("No report found with id: `" + id + "`");
        }
        return report;
    }

    @Binding
    public NewsletterManager manager(@Me GuildDB db) {
        NewsletterManager manager = db.getNewsletterManager();
        if (manager == null) {
            throw new IllegalArgumentException("Your guild is not whitelisted to use newsletters.");
        }
        return manager;
    }

    @Binding
    @ReportPerms
    public Newsletter getNewsletter(NewsletterManager manager, String nameOrId) {
        if (MathMan.isInteger(nameOrId)) {
            int id = Integer.parseInt(nameOrId);
            Newsletter newsletter = manager.getNewsletters().get(id);
            if (newsletter != null)
                return newsletter;
        }

        for (Newsletter value : manager.getNewsletters().values()) {
            if (value.getName().equalsIgnoreCase(nameOrId))
                return value;
        }

        List<String> options = manager.getNewsletters().values().stream().map(Newsletter::getName)
                .collect(Collectors.toList());
        throw new IllegalArgumentException("No newsletter found with name or id: `" + nameOrId + "`\n" +
                "Options: " + StringMan.getString(options));
    }

    // @Binding(examples = "'Error 404' 'Arrgh' 45d")
    // @Me
    // public WarParser wars(@Me Guild guild, String coalition1, String coalition2,
    // @Timediff long timediff) {
    // return WarParser.of(coalition1, coalition1, timediff);
    // return nation.get();
    // }

    // public DoubleArray parse(Map<ResourceType, Double> input)
    // public Map<ResourceType, Double> parse(DoubleArray input)

    @Binding
    public Predicate<DBWar> warFilter(ValueStore store, String input) {
        Placeholders<DBWar, Void> placeholders = requirePlaceholders(store, DBWar.class);
        return placeholders.parseFilter(store, input);
    }

    @Binding
    public Predicate<IAttack> attackFilter(ValueStore store, String input) {
        Placeholders<IAttack, Void> placeholders = requirePlaceholders(store, IAttack.class);
        return placeholders.parseFilter(store, input);
    }

    @Binding(value = "A nation event trigger type for sending in-game recruitment messages")
    public MessageTrigger trigger(String trigger) {
        return emum(MessageTrigger.class, trigger);
    }

    @Binding(value = "Whether to calculate purchase cost for a daily rebuy or based on the full unit cap")
    public MMRBuyMode mmrBuyMode(String trigger) {
        return emum(MMRBuyMode.class, trigger);
    }

    @Binding
    public AllianceMetricMode AllianceMetricMode(String mode) {
        return emum(AllianceMetricMode.class, mode);
    }

    @Binding
    public NationMeta meta(String input) {
        return emum(NationMeta.class, input);
    }

    @Binding
    public WarCostByDayMode WarCostByDayMode(String input) {
        return emum(WarCostByDayMode.class, input);
    }
}