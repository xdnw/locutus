package link.locutus.discord.web.commands.mcp;

import io.javalin.http.Context;
import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.LocalValueStore;
import link.locutus.discord.commands.manager.v2.binding.Parser;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.annotation.PlaceholderType;
import link.locutus.discord.commands.manager.v2.binding.annotation.Range;
import link.locutus.discord.commands.manager.v2.binding.annotation.TextArea;
import link.locutus.discord.commands.manager.v2.binding.bindings.PlaceholderCache;
import link.locutus.discord.commands.manager.v2.binding.bindings.Placeholders;
import link.locutus.discord.commands.manager.v2.binding.bindings.TypedFunction;
import link.locutus.discord.commands.manager.v2.command.ICommand;
import link.locutus.discord.commands.manager.v2.command.ParameterData;
import link.locutus.discord.commands.manager.v2.command.ParametricCallable;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.PlaceholdersMap;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.db.guild.GuildSetting;
import link.locutus.discord.db.guild.GuildSettingCategory;
import link.locutus.discord.gpt.mcp.MCPUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.web.WebUtil;
import link.locutus.discord.web.jooby.MCPHandler;
import net.dv8tion.jda.api.entities.User;

import java.time.Instant;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class MCPCommands {
    private static final long IDEMPOTENCY_TTL_MILLIS = TimeUnit.MINUTES.toMillis(10);
    private static final String JSON_CONTENT_TYPE = "application/json";
    private static final String ERROR_IDEMPOTENCY_REQUIRED = "idempotency_key is required in execute mode; run validate first";
    private static final String ERROR_IDEMPOTENCY_EXPIRED = "idempotency_key is missing or expired; run validate again";

    private final MCPHandler handler;
    private final Map<UUID, ValidationRecord> validations = new ConcurrentHashMap<>();

    public MCPCommands(MCPHandler handler) {
        this.handler = handler;
    }

    @Command(desc = "Test MCP command")
    public Object test_command(String arg1, @Default String arg2) {
        return Map.of("arg1", arg1, "arg2", arg2);
    }

    @Command(desc = "Unified command discovery. Query omitted means browse/list; query present means ranked search", aliases = {"command_discover"})
    public Object command_discover(@Default String query,
                                   @Default String path_prefix,
                                   @Default boolean viewable_only,
                                   @Default Parser<?> supports_type,
                                   @Default @Range(min = 0) Integer cursor,
                                   @Default @Range(min = 1, max = 250) Integer limit) {
        int offset = cursor == null ? 0 : cursor;
        int pageLimit = limit == null ? MCPHandler.DEFAULT_PAGE_LIMIT : limit;

        List<CommandDiscoverItem> items = new ArrayList<>();
        for (ParametricCallable<?> cmd : handler.getToolCallables()) {
            String name = MCPUtil.getToolName(cmd);
            String fullPath = cmd.getFullPath(" ");

            if (viewable_only && !cmd.isViewable()) {
                continue;
            }
            if (path_prefix != null && !path_prefix.isBlank()) {
                String prefix = path_prefix.toLowerCase();
                boolean matchesHumanPath = fullPath.toLowerCase().startsWith(prefix);
                boolean matchesCanonicalName = name.startsWith(prefix);
                if (!matchesHumanPath && !matchesCanonicalName) {
                    continue;
                }
            }
            if (supports_type != null && !supportsTypeMatch(cmd, supports_type)) {
                continue;
            }

            int score = rankCommand(cmd, name, query, supports_type);
            if (query != null && !query.isBlank() && score <= 0) {
                continue;
            }

            List<CommandDiscoverArgument> args = new ArrayList<>();
            for (ParameterData p : cmd.getUserParameters()) {
                args.add(new CommandDiscoverArgument(
                    p.getName(),
                    p.getBinding().getKey().toSimpleString(),
                    !p.isOptional() && (p.getDefaultValue() == null || p.getDefaultValue().length == 0),
                    p.getDescription()
                ));
            }
            items.add(new CommandDiscoverItem(name, fullPath, name, cmd.simpleDesc(), args, score));
        }

        items.sort((a, b) -> {
            int scoreCmp = Integer.compare(b.score(), a.score());
            if (scoreCmp != 0) {
                return scoreCmp;
            }
            return a.name().compareToIgnoreCase(b.name());
        });

        int from = Math.min(offset, items.size());
        int to = Math.min(from + pageLimit, items.size());
        List<CommandDiscoverItemPublic> page = new ArrayList<>();
        for (CommandDiscoverItem item : items.subList(from, to)) {
            page.add(item.toPublicShape());
        }

        return new CommandDiscoverResponse(page, MCPHandler.pageInfo(from, to, items.size(), pageLimit), query);
    }

    @Command(desc = "Fetch schema and metadata for a command", aliases = {"command_get"})
    public Object command_get(ICommand<?> command) {
        ParametricCallable<?> cmd = requireParametricCommand(command);
        Map<String, Object> schema = cmd.toJsonSchema(new HashMap<>());
        schema.put("path", cmd.getFullPath());
        return schema;
    }

    @Command(desc = "Execute or validate a command through one canonical path", aliases = {"command_execute"})
    public Object command_execute(Context context,
                                  ICommand<?> command,
                                  @Default Map<String, Object> arguments,
                                  @Default ExecuteMode mode,
                                  @Default UUID idempotency_key) {
        clearExpiredValidations();

        ParametricCallable<?> cmd = requireParametricCommand(command);
        Map<String, Object> rawArgs = arguments == null ? Map.of() : arguments;
        ExecuteMode actualMode = mode == null ? ExecuteMode.EXECUTE : mode;

        MCPHandler.ParsedCommand parsed = handler.parseCommand(cmd, rawArgs, context);
        String commandName = MCPUtil.getToolName(cmd);
        String argsHash = hashPayload(parsed.normalizedArguments());

        if (actualMode == ExecuteMode.VALIDATE) {
            UUID key = idempotency_key == null ? UUID.randomUUID() : idempotency_key;
            long expiresAt = System.currentTimeMillis() + IDEMPOTENCY_TTL_MILLIS;
            validations.put(key, new ValidationRecord(commandName, argsHash, expiresAt, methodMetadata(cmd)));
            return new ValidationResponse(commandName, parsed.normalizedArguments(), true, key, Instant.ofEpochMilli(expiresAt).toString(), methodMetadata(cmd));
        }

        if (idempotency_key == null) {
            throw new IllegalArgumentException(ERROR_IDEMPOTENCY_REQUIRED);
        }
        ValidationRecord record = validations.remove(idempotency_key);
        if (record == null || record.expiresAt < System.currentTimeMillis()) {
            throw new IllegalArgumentException(ERROR_IDEMPOTENCY_EXPIRED);
        }
        if (!record.commandName.equals(commandName) || !record.argsHash.equals(argsHash)) {
            throw new IllegalArgumentException("idempotency_key does not match validated command/arguments");
        }

        Map<String, Object> output = handler.executeParsed(cmd, parsed);
        return new ExecutionResponse(commandName, idempotency_key, handler.maybeStoreLargeResult(output, JSON_CONTENT_TYPE));
    }

    @Command(desc = "Query placeholder-backed data tables with plan/sample/full modes", aliases = {"data_query"})
    public Object data_query(Context context,
                             @PlaceholderType Class<?> type,
                             @Default String selection,
                             @TextArea List<String> columns,
                             @Default DataQueryMode mode,
                             @Default @Range(min = 0) Integer cursor,
                             @Default @Range(min = 1, max = 250) Integer limit) {
        if (columns == null || columns.isEmpty()) {
            throw new IllegalArgumentException("Missing required field: columns");
        }

        String querySelection = selection == null || selection.isBlank() ? "*" : selection;
        int offset = cursor == null ? 0 : cursor;
        int pageLimit = limit == null ? MCPHandler.DEFAULT_PAGE_LIMIT : limit;
        DataQueryMode actualMode = mode == null ? DataQueryMode.SAMPLE : mode;
        if (actualMode == DataQueryMode.SAMPLE) {
            pageLimit = Math.min(pageLimit, 50);
        }

        PlaceholdersMap placeholders = Locutus.cmd().getV2().getPlaceholders();
        Placeholders<Object, Object> ph = (Placeholders<Object, Object>) placeholders.get(type);
        if (ph == null) {
            throw new IllegalArgumentException("Unknown placeholder type: " + type.getName());
        }

        LocalValueStore<?> locals = handler.createLocals(context);
        Object modifier = null;
        if (querySelection.startsWith("{") && querySelection.endsWith("}")) {
            Map<String, Object> map = WebUtil.GSON.fromJson(querySelection, Map.class);
            Map.Entry<String, ?> parsedModifier = ph.parseModifier(locals, map);
            if (parsedModifier != null && parsedModifier.getValue() != null) {
                querySelection = parsedModifier.getKey();
                modifier = parsedModifier.getValue();
            }
        }

        Set<Object> resolved = ph.parseSet(locals, querySelection, modifier);
        List<Object> entities = new ArrayList<>(resolved);

        var cacheStore = PlaceholderCache.createCache(resolved, (Class<Object>) type);
        List<TypedFunction<Object, ?>> formatters = new ArrayList<>();
        List<String> typeNames = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        for (String column : columns) {
            try {
                TypedFunction<Object, ?> fn = ph.formatRecursively(cacheStore, column, null, 0, false, true);
                formatters.add(fn);
                typeNames.add(fn.getType().getTypeName());
            } catch (Exception e) {
                warnings.add("Column `" + column + "` failed to parse: " + e.getMessage());
                formatters.add(null);
                typeNames.add("unknown");
            }
        }

        DataQuerySchema schema = new DataQuerySchema(columns, typeNames);

        if (actualMode == DataQueryMode.PLAN) {
            return new DataQueryPlan(
                type.getSimpleName(),
                querySelection,
                resolved.size(),
                (long) resolved.size() * Math.max(1, columns.size()),
                schema,
                warnings
            );
        }

        int from = Math.min(offset, entities.size());
        int to = Math.min(from + pageLimit, entities.size());

        List<Map<String, Object>> rows = buildRows(entities.subList(from, to), columns, formatters);
        DataQueryPayload payload = new DataQueryPayload(
            type.getSimpleName(),
            querySelection,
            schema,
            rows,
            warnings,
            MCPHandler.pageInfo(from, to, entities.size(), pageLimit)
        );

        if (actualMode == DataQueryMode.FULL) {
            DataQueryPayload fullData = new DataQueryPayload(
                type.getSimpleName(),
                querySelection,
                schema,
                buildRows(entities, columns, formatters),
                warnings,
                null
            );
            return handler.maybeStoreLargeResult(fullData, JSON_CONTENT_TYPE);
        }

        return payload;
    }

    @Command(desc = "Retrieve large output by result_ref", aliases = {"result_get"})
    public Object result_get(UUID result_ref,
                             @Default @Range(min = 0) Integer cursor,
                             @Default @Range(min = 1, max = 250) Integer limit) {
        return handler.getResultRef(result_ref, cursor == null ? 0 : cursor, limit == null ? MCPHandler.DEFAULT_PAGE_LIMIT : limit);
    }

    @Command(desc = "Discover settings metadata", aliases = {"settings_discover"})
    public Object settings_discover(@Default String query,
                                    @Default GuildSettingCategory category,
                                    @Default Parser<?> type,
                                    @Default @Range(min = 0) Integer cursor,
                                    @Default @Range(min = 1, max = 250) Integer limit) {
        int offset = cursor == null ? 0 : cursor;
        int pageLimit = limit == null ? MCPHandler.DEFAULT_PAGE_LIMIT : limit;

        List<SettingsDiscoverItem> items = new ArrayList<>();
        for (GuildSetting<?> setting : GuildKey.values()) {
            if (query != null && !query.isBlank()) {
                String q = query.toLowerCase();
                if (!setting.name().toLowerCase().contains(q)
                        && !setting.help().toLowerCase().contains(q)
                        && !setting.getType().toSimpleString().toLowerCase().contains(q)) {
                    continue;
                }
            }
            if (category != null && setting.getCategory() != category) {
                continue;
            }
            if (type != null && !setting.getType().equals(type.getKey())) {
                continue;
            }

            items.add(new SettingsDiscoverItem(
                setting.name(),
                setting.getType().toSimpleString(),
                setting.getCategory().name(),
                setting.help(),
                setting.getRequirementDesc()
            ));
        }

        items.sort(Comparator.comparing(SettingsDiscoverItem::name));
        int from = Math.min(offset, items.size());
        int to = Math.min(from + pageLimit, items.size());

        return new SettingsDiscoverResponse(new ArrayList<>(items.subList(from, to)), MCPHandler.pageInfo(from, to, items.size(), pageLimit));
    }

    @Command(desc = "Get a setting value and metadata", aliases = {"settings_get"})
    public Object settings_get(Context context, GuildSetting<?> setting) {
        LocalValueStore<?> locals = handler.createLocals(context);
        GuildDB db = (GuildDB) locals.getProvided(Key.of(GuildDB.class, Me.class), false);
        if (db == null) {
            throw new IllegalArgumentException("settings_get requires guild context");
        }

        Object value = setting.getOrNull(db, true);
        return new SettingValueResponse(
            setting.name(),
            setting.getType().toSimpleString(),
            setting.getCategory().name(),
            setting.help(),
            setting.getRequirementDesc(),
            StringMan.toSerializable(value)
        );
    }

    @Command(desc = "Set a setting with validate/execute modes", aliases = {"settings_set"})
    public Object settings_set(Context context,
                               GuildSetting<?> setting,
                               String value,
                               @Default ExecuteMode mode,
                               @Default UUID idempotency_key) {
        clearExpiredValidations();

        LocalValueStore<?> locals = handler.createLocals(context);
        GuildDB db = (GuildDB) locals.getProvided(Key.of(GuildDB.class, Me.class), false);
        User user = (User) locals.getProvided(Key.of(User.class, Me.class), false);
        if (db == null) {
            throw new IllegalArgumentException("settings_set requires guild context");
        }
        if (user == null) {
            throw new IllegalArgumentException("settings_set requires user context");
        }

        Object parsed = setting.parse(db, value == null ? "" : value);
        validateSettingValue(setting, db, user, parsed);

        ExecuteMode actualMode = mode == null ? ExecuteMode.EXECUTE : mode;
        String argsHash = hashPayload(StringMan.toSerializable(parsed));
        String commandName = "setting:" + setting.name();

        if (actualMode == ExecuteMode.VALIDATE) {
            UUID key = idempotency_key == null ? UUID.randomUUID() : idempotency_key;
            long expiresAt = System.currentTimeMillis() + IDEMPOTENCY_TTL_MILLIS;
            validations.put(key, new ValidationRecord(commandName, argsHash, expiresAt, null));
            return new SettingValidationResponse(setting.name(), StringMan.toSerializable(parsed), true, key, Instant.ofEpochMilli(expiresAt).toString());
        }

        if (idempotency_key == null) {
            throw new IllegalArgumentException(ERROR_IDEMPOTENCY_REQUIRED);
        }
        ValidationRecord record = validations.remove(idempotency_key);
        if (record == null || record.expiresAt < System.currentTimeMillis()) {
            throw new IllegalArgumentException(ERROR_IDEMPOTENCY_EXPIRED);
        }
        if (!record.commandName.equals(commandName) || !record.argsHash.equals(argsHash)) {
            throw new IllegalArgumentException("idempotency_key does not match validated setting/value");
        }

        String message = executeSettingSet(setting, db, user, parsed);
        return new SettingExecutionResponse(setting.name(), idempotency_key, message);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void validateSettingValue(GuildSetting<?> setting, GuildDB db, User user, Object parsed) {
        ((GuildSetting) setting).allowedAndValidate(db, user, parsed);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private String executeSettingSet(GuildSetting<?> setting, GuildDB db, User user, Object parsed) {
        return ((GuildSetting) setting).setAndValidate(db, user, parsed);
    }

    private ParametricCallable<?> requireParametricCommand(ICommand<?> command) {
        if (!(command instanceof ParametricCallable<?> parametric)) {
            throw new IllegalArgumentException("MCP requires a parametric command endpoint");
        }
        return parametric;
    }

    private int rankCommand(ParametricCallable<?> command, String canonicalName, String query, Parser<?> supportsType) {
        int score = 0;
        String path = command.getFullPath().toLowerCase();
        if (query != null && !query.isBlank()) {
            String q = query.toLowerCase();
            if (path.equals(q) || canonicalName.equals(q)) {
                score += 1000;
            } else if (path.startsWith(q) || canonicalName.startsWith(q)) {
                score += 800;
            } else if (path.contains(q) || canonicalName.contains(q)) {
                score += 600;
            }

            String desc = command.simpleDesc() == null ? "" : command.simpleDesc().toLowerCase();
            if (desc.contains(q)) {
                score += 300;
            }
        }

        if (supportsType != null && supportsTypeMatch(command, supportsType)) {
            score += 250;
        }
        return score;
    }

    private boolean supportsTypeMatch(ParametricCallable<?> command, Parser<?> supportsType) {
        for (ParameterData p : command.getUserParameters()) {
            if (p.getBinding().getKey().equals(supportsType.getKey())) {
                return true;
            }
        }
        return false;
    }

    private List<Map<String, Object>> buildRows(List<Object> entities, List<String> columns, List<TypedFunction<Object, ?>> formatters) {
        List<Map<String, Object>> rows = new ArrayList<>(entities.size());
        for (Object entity : entities) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int c = 0; c < columns.size(); c++) {
                String column = columns.get(c);
                TypedFunction<Object, ?> formatter = formatters.get(c);
                if (formatter == null) {
                    row.put(column, null);
                    continue;
                }
                try {
                    row.put(column, StringMan.toSerializable(formatter.apply(entity)));
                } catch (Exception e) {
                    row.put(column, null);
                }
            }
            rows.add(row);
        }
        return rows;
    }

    private void clearExpiredValidations() {
        long now = System.currentTimeMillis();
        validations.entrySet().removeIf(e -> e.getValue().expiresAt < now);
    }

    private String hashPayload(Object value) {
        String json = WebUtil.GSON.toJson(value == null ? "" : value);
        return Integer.toHexString(json.hashCode());
    }

    private MethodMetadata methodMetadata(ParametricCallable<?> command) {
        Method method = command.getMethod();
        if (method == null) {
            return null;
        }
        return new MethodMetadata(method.getDeclaringClass().getSimpleName(), method.getName(), method.toGenericString());
    }

    private record ValidationRecord(String commandName, String argsHash, long expiresAt, MethodMetadata method_metadata) {
    }

    private record MethodMetadata(String declaring_class, String method_name, String signature) {
    }

    private record CommandDiscoverArgument(String name, String type, boolean required, String description) {
    }

    private record CommandDiscoverItem(String name,
                                       String path,
                                       String canonical_name,
                                       String description,
                                       List<CommandDiscoverArgument> arguments,
                                       int score) {
        private CommandDiscoverItemPublic toPublicShape() {
            return new CommandDiscoverItemPublic(name, path, canonical_name, description, arguments);
        }
    }

    private record CommandDiscoverItemPublic(String name,
                                             String path,
                                             String canonical_name,
                                             String description,
                                             List<CommandDiscoverArgument> arguments) {
    }

    private record CommandDiscoverResponse(List<CommandDiscoverItemPublic> items, MCPHandler.PageInfo page_info, String query) {
    }

    private record ValidationResponse(String name, Map<String, Object> normalized_args, boolean validated, UUID idempotency_key, String expires_at, MethodMetadata method_metadata) {
    }

    private record ExecutionResponse(String name, UUID idempotency_key, Object result) {
    }

    private record SettingsDiscoverItem(String name, String type, String category, String help, List<String> requirements) {
    }

    private record SettingsDiscoverResponse(List<SettingsDiscoverItem> items, MCPHandler.PageInfo page_info) {
    }

    private record SettingValidationResponse(String name, Object validated_value, boolean validated, UUID idempotency_key, String expires_at) {
    }

    private record SettingExecutionResponse(String name, UUID idempotency_key, String result) {
    }

    private record DataQuerySchema(List<String> columns, List<String> types) {
    }

    private record DataQueryPlan(String type, String selection, int estimated_cardinality, long estimated_cost,
                                 DataQuerySchema schema, List<String> warnings) {
    }

    private record DataQueryPayload(String type, String selection, DataQuerySchema schema,
                                    List<Map<String, Object>> rows, List<String> warnings,
                                    MCPHandler.PageInfo page_info) {
    }

    private record SettingValueResponse(String name, String type, String category, String help,
                                        List<String> requirements, Object value) {
    }
}
