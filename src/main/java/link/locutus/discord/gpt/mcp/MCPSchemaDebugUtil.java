package link.locutus.discord.gpt.mcp;

import com.google.common.base.Predicates;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.MethodParser;
import link.locutus.discord.commands.manager.v2.binding.Parser;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.command.ParameterData;
import link.locutus.discord.commands.manager.v2.command.ParametricCallable;
import link.locutus.discord.commands.manager.v2.command.WebOption;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.web.commands.binding.value_types.WebOptions;
import net.dv8tion.jda.api.entities.User;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public final class MCPSchemaDebugUtil {
    private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String DEBUG_SCHEMA_PROPERTY = "locutus.mcp.schema.debug";
    private static final String DEBUG_SCHEMA_DIR_PROPERTY = "locutus.mcp.schema.debug.dir";
    private static final Set<String> VALID_JSON_SCHEMA_TYPES = Set.of("object", "array", "string", "number", "integer", "boolean", "null");
    private static volatile String lastSchemaDebugOutputDir;

    private MCPSchemaDebugUtil() {
    }

    public static void enableSchemaDebugForCurrentProcess() {
        System.setProperty(DEBUG_SCHEMA_PROPERTY, "true");
    }

    public static boolean isSchemaDebugEnabled() {
        String property = System.getProperty(DEBUG_SCHEMA_PROPERTY);
        if (property != null) {
            return Boolean.parseBoolean(property);
        }
        String env = System.getenv("LOCUTUS_MCP_SCHEMA_DEBUG");
        if (env == null) {
            return false;
        }
        String normalized = env.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("1") || normalized.equals("true") || normalized.equals("yes") || normalized.equals("on");
    }

    public static String getLastSchemaDebugOutputDir() {
        return lastSchemaDebugOutputDir;
    }

    public static void collectSchemaIssues(Object node, String path, List<Map<String, Object>> issues, String toolName) {
        if (node instanceof Map<?, ?> map) {
            Object type = map.get("type");
            if (type instanceof String typeName && !VALID_JSON_SCHEMA_TYPES.contains(typeName)) {
                issues.add(issue(toolName, path + ".type", "invalid JSON Schema type", typeName));
            }
            if (type instanceof Collection<?> typeList) {
                int idx = 0;
                for (Object t : typeList) {
                    if (!(t instanceof String ts) || !VALID_JSON_SCHEMA_TYPES.contains(ts)) {
                        issues.add(issue(toolName, path + ".type[" + idx + "]", "invalid JSON Schema type entry", String.valueOf(t)));
                    }
                    idx++;
                }
            }

            Object ref = map.get("$ref");
            if (ref instanceof String refValue && !refValue.startsWith("#/$defs/")) {
                issues.add(issue(toolName, path + ".$ref", "non-standard $ref; expected #/$defs/...", refValue));
            }

            Object oneOf = map.get("oneOf");
            if (oneOf instanceof List<?> list) {
                for (int i = 0; i < list.size(); i++) {
                    Object item = list.get(i);
                    if (item instanceof Map<?, ?> oneOfEntry && oneOfEntry.containsKey("value") && !oneOfEntry.containsKey("const")) {
                        issues.add(issue(toolName, path + ".oneOf[" + i + "]", "oneOf entry uses 'value' instead of JSON Schema 'const'", String.valueOf(oneOfEntry.get("value"))));
                    }
                }
            }

            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object key = entry.getKey();
                String childPath = key == null ? path + ".<null>" : path + "." + key;
                collectSchemaIssues(entry.getValue(), childPath, issues, toolName);
            }
            return;
        }

        if (node instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                collectSchemaIssues(list.get(i), path + "[" + i + "]", issues, toolName);
            }
        }
    }

    public static Map<String, Object> issue(String toolName, String path, String message, String value) {
        Map<String, Object> issue = new LinkedHashMap<>();
        issue.put("tool", toolName);
        issue.put("path", path);
        issue.put("message", message);
        issue.put("value", value);
        return issue;
    }

    public static MCPUtil.SchemaDebugObserver createSchemaDebugObserver(ValueStore store,
                                                                         ValueStore htmlOptionsStore,
                                                                         ValueStore schemaStore,
                                                                         GuildDB guildDB,
                                                                         User user,
                                                                         DBNation nation) {
        Map<String, Object> perToolSchemas = new LinkedHashMap<>();
        List<Map<String, Object>> schemaIssues = new ArrayList<>();

        return new MCPUtil.SchemaDebugObserver() {
            @Override
            public void onToolSchema(String toolName, Map<String, Object> toolSchema) {
                perToolSchemas.put(toolName, toolSchema);
                collectSchemaIssues(toolSchema, "$.tool[" + toolName + "]", schemaIssues, toolName);
            }

            @Override
            public void onToolSchemaError(String toolName, String path, String message, String value) {
                schemaIssues.add(issue(toolName, path, message, value));
            }

            @Override
            public void onToolsListSchema(Map<String, Object> toolsList,
                                          Map<String, Object> definitions,
                                          Map<Key<?>, Map<String, Object>> primitiveCache) {
                collectSchemaIssues(toolsList, "$.tools_list", schemaIssues, "tools/list");
                writeSchemaDebugArtifacts(
                        toolsList,
                        definitions,
                        primitiveCache,
                        perToolSchemas,
                        schemaIssues,
                        store,
                        htmlOptionsStore,
                        schemaStore,
                        guildDB,
                        user,
                        nation
                );
            }
        };
    }

    public static void writeSchemaDebugArtifacts(Map<String, Object> toolsList,
                                                 Map<String, Object> definitions,
                                                 Map<Key<?>, Map<String, Object>> primitiveCache,
                                                 Map<String, Object> perToolSchemas,
                                                 List<Map<String, Object>> schemaIssues,
                                                 ValueStore store,
                                                 ValueStore htmlOptionsStore,
                                                 ValueStore schemaStore,
                                                 GuildDB guildDB,
                                                 User user,
                                                 DBNation nation) {
        Path runDir = resolveRunDirectory();
        try {
            Files.createDirectories(runDir);

            writeJson(runDir.resolve("tools-list-schema.json"), toolsList);
            writeJson(runDir.resolve("defs-schema.json"), definitions);
            writeJson(runDir.resolve("primitive-cache-schema.json"), toPrimitiveCacheMap(primitiveCache));
            writeJson(runDir.resolve("schema-issues.json"), schemaIssues);
            writeJson(runDir.resolve("per-tool-schema.json"), perToolSchemas);

            Set<ParametricCallable<?>> discordCommands = getDiscordCommands();
            writeJson(runDir.resolve("discord-commands-schema.json"), toDiscordCommandsSchema(discordCommands));
            writeJson(runDir.resolve("argument-types-schema.json"), toArgumentTypesSchema(discordCommands, store, htmlOptionsStore, schemaStore, guildDB, user, nation));

            lastSchemaDebugOutputDir = runDir.toAbsolutePath().toString();
            System.out.println("[MCP-Schema-Debug] Wrote schema artifacts to " + lastSchemaDebugOutputDir);
            if (schemaIssues != null && !schemaIssues.isEmpty()) {
                System.err.println("[MCP-Schema-Debug] Found " + schemaIssues.size() + " schema issue(s). See schema-issues.json");
            }
        } catch (IOException e) {
            System.err.println("[MCP-Schema-Debug] Failed writing schema artifacts: " + e.getMessage());
        }
    }

    private static Set<ParametricCallable<?>> getDiscordCommands() {
        try {
            if (Locutus.imp() == null || Locutus.cmd() == null || Locutus.cmd().getV2() == null || Locutus.cmd().getV2().getCommands() == null) {
                return Set.of();
            }
            return new LinkedHashSet<>(Locutus.cmd().getV2().getCommands().getParametricCallables(Predicates.alwaysTrue()));
        } catch (Throwable t) {
            System.err.println("[MCP-Schema-Debug] Failed loading discord commands: " + t.getMessage());
            return Set.of();
        }
    }

    private static Map<String, Object> toDiscordCommandsSchema(Set<ParametricCallable<?>> discordCommands) {
        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, Object> commandSchemas = new LinkedHashMap<>();
        for (ParametricCallable<?> command : discordCommands) {
            String commandName = command.getFullPath(" ");
            try {
                commandSchemas.put(commandName, command.toJson(null, false));
            } catch (Throwable t) {
                commandSchemas.put(commandName, Map.of("error", t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage()));
            }
        }
        out.put("count", commandSchemas.size());
        out.put("commands", commandSchemas);
        return out;
    }

    private static Map<String, Object> toArgumentTypesSchema(Set<ParametricCallable<?>> discordCommands,
                                                             ValueStore store,
                                                             ValueStore htmlOptionsStore,
                                                             ValueStore schemaStore,
                                                             GuildDB guildDB,
                                                             User user,
                                                             DBNation nation) {
        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, Object> argTypes = new LinkedHashMap<>();
        Set<Key<?>> visited = new ObjectOpenHashSet<>();

        for (ParametricCallable<?> command : discordCommands) {
            for (ParameterData data : command.getUserParameters()) {
                Parser<?> parser = data.getBinding();
                Key<?> key = parser.getKey();
                Key<?> webType = parser.getWebTypeOrNull();
                try {
                    if (webType != null) {
                        Parser<?> parserForWebType = store.get(webType);
                        if (parserForWebType == null) {
                            throw new IllegalArgumentException("No parser for webType: " + webType + " (from " + key + ") used in command: " + command.getFullPath());
                        }
                        parser = parserForWebType;
                        key = parser.getKey();
                    }

                    if (!visited.add(key)) {
                        continue;
                    }

                    Map<String, Object> argumentDef = buildArgumentDefinition(parser, key, store, htmlOptionsStore, schemaStore, guildDB, user, nation);
                    argumentDef.put("key", key.toSimpleString());
                    argTypes.put(key.toSimpleString(), argumentDef);
                } catch (IllegalArgumentException e) {
                    argTypes.putIfAbsent(key.toSimpleString(), Map.of("error", e.getMessage()));
                }
            }
        }

        out.put("count", argTypes.size());
        out.put("argument_types", argTypes);
        return out;
    }

    private static Map<String, Object> buildArgumentDefinition(Parser<?> parser,
                                                               Key<?> key,
                                                               ValueStore store,
                                                               ValueStore htmlOptionsStore,
                                                               ValueStore schemaStore,
                                                               GuildDB guildDB,
                                                               User user,
                                                               DBNation nation) {
        Map<String, Object> argumentDef = new LinkedHashMap<>();

        Parser<?> schemaBinding = schemaStore.get(key);
        String typeDesc = schemaBinding == null ? null : schemaBinding.getDescription();
        if (typeDesc == null || typeDesc.isEmpty()) {
            typeDesc = parser.getDescription();
        }
        String[] examples = parser.getExamples();

        if (typeDesc != null && !typeDesc.isEmpty()) {
            argumentDef.put("description", typeDesc);
        } else {
            String classAndMethodOrNull = (parser instanceof MethodParser<?> methodParser)
                    ? methodParser.getMethod().getDeclaringClass().getSimpleName() + "." + methodParser.getMethod().getName()
                    : null;
            System.err.println("[Gpt-Tool] Error: No description for type " + key + " in JSON schema generation | " + classAndMethodOrNull);
        }
        if (examples.length > 0) {
            argumentDef.put("examples", Arrays.asList(examples));
        }

        if (schemaBinding != null) {
            Map<String, Object> map = (Map<String, Object>) schemaBinding.apply(store, null);
            argumentDef.putAll(map);
            return argumentDef;
        }

        Function<Key<?>, Map<String, Object>> getOptions = t -> {
            Parser<?> toolOptions = schemaStore.get(t);
            if (toolOptions != null) {
                return (Map<String, Object>) toolOptions.apply(store, null);
            }
            if (t.getType() instanceof ParameterizedType) {
                System.err.println("[Gpt-Tool] Not checking options for parameterized type: " + t);
                return null;
            }
            Parser<?> optionParser = htmlOptionsStore.get(t);
            if (optionParser == null) {
                System.err.println("[Gpt-Tool] No html options for " + key);
                return null;
            }
            WebOption option = (WebOption) optionParser.apply(store, null);
            List<String> options = option.getOptions();
            if (options != null && !options.isEmpty()) {
                if (option.isAllowCustomOption()) {
                    return Map.of(
                            "anyOf", List.of(
                                    Map.of(
                                            "type", "string",
                                            "enum", options
                                    ),
                                    Map.of(
                                            "type", "string"
                                    )
                            )
                    );
                }
                return Map.of(
                        "type", "string",
                        "enum", options
                );
            }
            if (!option.isLargeQuery()
                    && (!option.isRequiresGuild() || guildDB != null)
                    && (!option.isRequiresUser() || user != null)
                    && (!option.isRequiresNation() || nation != null)) {
                try {
                    WebOptions optionMeta = option.getQueryOptions(guildDB, user, nation);
                    if (optionMeta != null) {
                        boolean isNumeric = optionMeta.key_numeric != null;
                        if (optionMeta.text != null || optionMeta.subtext != null) {
                            List<Map<String, Object>> oneOf = new ArrayList<>();
                            List<?> keys = isNumeric ? optionMeta.key_numeric : optionMeta.key_string;
                            List<String> texts = optionMeta.text;
                            List<String> subtexts = optionMeta.subtext;
                            int n = keys == null ? 0 : keys.size();
                            for (int i = 0; i < n; i++) {
                                Object myConst = keys.get(i);
                                StringBuilder desc = new StringBuilder();
                                if (texts != null && i < texts.size() && texts.get(i) != null && !texts.get(i).isEmpty()) {
                                    desc.append(texts.get(i));
                                }
                                if (subtexts != null && i < subtexts.size() && subtexts.get(i) != null && !subtexts.get(i).isEmpty()) {
                                    if (desc.length() > 0) {
                                        desc.append(" - ");
                                    }
                                    desc.append(subtexts.get(i));
                                }
                                if (desc.length() > 0) {
                                    oneOf.add(Map.of("const", myConst, "description", desc.toString()));
                                } else {
                                    oneOf.add(Map.of("const", myConst));
                                }
                            }
                            return Map.of(
                                    "type", isNumeric ? "number" : "string",
                                    "oneOf", oneOf
                            );
                        }
                        if (isNumeric) {
                            return Map.of(
                                    "type", "number",
                                    "enum", optionMeta.key_numeric
                            );
                        }
                        return Map.of(
                                "type", "string",
                                "enum", optionMeta.key_string
                        );
                    }
                } catch (IllegalArgumentException e) {
                    return null;
                }
            }

            return null;
        };

        try {
            Map<String, Object> schema = MCPUtil.generateSchema(key, getOptions);
            argumentDef.putAll(schema);
        } catch (IllegalArgumentException e) {
            argumentDef.put("error", e.getMessage());
        }

        return argumentDef;
    }

    private static Path resolveRunDirectory() {
        String configuredDir = System.getProperty(DEBUG_SCHEMA_DIR_PROPERTY);
        if (configuredDir == null || configuredDir.isBlank()) {
            configuredDir = "data/mcp-schema-debug";
        }
        String runName = "run-" + Instant.now().toString().replace(':', '-');
        return Paths.get(configuredDir, runName);
    }

    private static void writeJson(Path path, Object payload) throws IOException {
        Files.writeString(path, PRETTY_GSON.toJson(payload));
    }

    private static Map<String, Object> toPrimitiveCacheMap(Map<Key<?>, Map<String, Object>> primitiveCache) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<Key<?>, Map<String, Object>> entry : primitiveCache.entrySet()) {
            out.put(entry.getKey().toSimpleString(), entry.getValue());
        }
        return out;
    }
}
