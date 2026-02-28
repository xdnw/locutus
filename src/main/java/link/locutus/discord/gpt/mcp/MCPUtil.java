package link.locutus.discord.gpt.mcp;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
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

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class MCPUtil {

    public record CanonicalSchemaType(Key<?> schemaKey, Parser<?> schemaParser) {
    }

    public interface SchemaDebugObserver {
        void onToolSchema(String toolName, Map<String, Object> toolSchema);

        void onToolSchemaError(String toolName, String path, String message, String value);

        void onToolsListSchema(Map<String, Object> toolsListSchema,
                               Map<String, Object> definitions,
                               Map<Key<?>, Map<String, Object>> primitiveCache,
                               Map<String, Integer> perToolDefinitionCounts);
    }

    private static final int MAX_INLINE_ENUM_VALUES = 64;
    private static final int MAX_INLINE_ONEOF_VALUES = 48;
    private static final Set<String> VALID_JSON_SCHEMA_TYPES = Set.of("object", "array", "string", "number", "integer", "boolean", "null");
    private static final Set<String> STRUCTURAL_SCHEMA_KEYS = Set.of(
            "$ref", "type", "enum", "const", "oneOf", "anyOf", "allOf", "not",
            "items", "properties", "additionalProperties", "required"
    );
    private static final Set<String> DISALLOWED_SCHEMA_KEYS = Set.of("help", "annotations", "flag", "name");

    public static String getToolName(ParametricCallable<?> command) {
        return command.getFullPath("-").toLowerCase(Locale.ROOT);
    }

    public static List<String> getToolPathTokens(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(toolName.toLowerCase(Locale.ROOT).trim().split("-"))
                .filter(part -> part != null && !part.isBlank())
                .toList();
    }

    public static String canonicalToolName(String toolName) {
        List<String> tokens = getToolPathTokens(toolName);
        return tokens.isEmpty() ? "" : String.join("-", tokens);
    }

    public static Map<String, Object> toJsonSchema(Type type, boolean supportUnknown) {
        Map<String, Object> schema = new Object2ObjectLinkedOpenHashMap<>();

        if (type instanceof ParameterizedType pt) {
            Class<?> raw = (Class<?>) pt.getRawType();

            if (Map.class.isAssignableFrom(raw)) {
                schema.put("type", "object");
                Type valueType = pt.getActualTypeArguments()[1];
                Map<String, Object> child = toJsonSchema(valueType, supportUnknown);
                if (child == null) return null;
                schema.put("additionalProperties", child);
                return schema;
            }

            if (Collection.class.isAssignableFrom(raw)) {
                schema.put("type", "array");
                Type elementType = pt.getActualTypeArguments()[0];
                Map<String, Object> child = toJsonSchema(elementType, supportUnknown);
                if (child == null) return null;
                schema.put("items", child);
                return schema;
            }

            if (!supportUnknown) return null;
            schema.put("type", "object");
            return schema;
        }

        if (type instanceof Class<?> cls) {
            if (cls.equals(String.class)) {
                schema.put("type", "string");
            } else if (cls.equals(Integer.class) || cls.equals(int.class)
                    || cls.equals(Long.class) || cls.equals(long.class)) {
                schema.put("type", "integer");
            } else if (cls.equals(Double.class) || cls.equals(double.class)
                    || cls.equals(Float.class) || cls.equals(float.class)) {
                schema.put("type", "number");
            } else if (cls.equals(Boolean.class) || cls.equals(boolean.class)) {
                schema.put("type", "boolean");
            } else if (cls.equals(Void.class) || cls.equals(void.class)) {
                schema.put("type", "null");
            } else if (cls.isArray()) {
                schema.put("type", "array");
                Map<String, Object> child = toJsonSchema(cls.getComponentType(), supportUnknown);
                if (child == null) return null;
                schema.put("items", child);
            } else {
                if (!supportUnknown) return null;
                schema.put("type", "object");
            }
            return schema;
        }

        if (!supportUnknown) return null;
        schema.put("type", "object");
        return schema;
    }

    public static String getJsonName(String keyName) {
        String name = keyName;
        name = name.replaceAll(" ", "")
                .replaceAll("<", "_")
                .replaceAll(">", "")
                .replaceAll(",", "_to_")
                .replaceAll("\\[", "_")
                .replaceAll("]", "")
                .toLowerCase(Locale.ROOT);
        return name;
    }

    public static Map<String, Object> generateSchema(Key<?> key, Function<Key<?>, Map<String, Object>> getOptions) {
        Map<String, Object> options = getOptions.apply(key);
        if (options != null) {
            if (options.isEmpty()) {
                throw new IllegalArgumentException("getOptions returned empty schema for type: " + key);
            }
            return options;
        }

        Map<String, Object> schema = new Object2ObjectLinkedOpenHashMap<>();

        Type type = key.getType();
        if (type instanceof ParameterizedType pt) {
            Class<?> raw = (Class<?>) pt.getRawType();

            if (Map.class.isAssignableFrom(raw)) {
                schema.put("type", "object");
                Type valueType = pt.getActualTypeArguments()[1];
                Map<String, Object> child = generateSchema(Key.of(valueType), getOptions);
                schema.put("additionalProperties", child);
                return schema;
            }

            if (Collection.class.isAssignableFrom(raw)) {
                schema.put("type", "array");
                Type elementType = pt.getActualTypeArguments()[0];
                Map<String, Object> child = generateSchema(Key.of(elementType), getOptions);
                schema.put("items", child);
                return schema;
            }

            throw new IllegalArgumentException("Unsupported parameterized type: " + type.getTypeName());
        }

        if (type instanceof Class<?> cls) {
            if (cls.isEnum()) {
                schema.put("type", "string");
                Object[] constants = cls.getEnumConstants();
                if (constants.length == 0) {
                    schema.put("enum", Collections.emptyList());
                    return schema;
                }

                Enum<?> first = (Enum<?>) constants[0];
                boolean anyDiff = !first.name().equals(first.toString());

                if (!anyDiff) {
                    List<String> enumValues = new ArrayList<>(constants.length);
                    for (Object constant : constants) {
                        enumValues.add(((Enum<?>) constant).name());
                    }
                    schema.put("enum", enumValues);
                } else {
                    List<Map<String, Object>> oneOfList = new ArrayList<>(constants.length);
                    for (Object constant : constants) {
                        Enum<?> e = (Enum<?>) constant;
                        Map<String, Object> entry = new LinkedHashMap<>();
                        entry.put("const", e.name());
                        entry.put("description", constant.toString());
                        oneOfList.add(entry);
                    }
                    schema.put("oneOf", oneOfList);
                }
                return schema;
            }
            if (cls.equals(String.class)) {
                schema.put("type", "string");
            } else if (cls.equals(Integer.class) || cls.equals(int.class)
                    || cls.equals(Long.class) || cls.equals(long.class)) {
                schema.put("type", "integer");
            } else if (cls.equals(Double.class) || cls.equals(double.class)
                    || cls.equals(Float.class) || cls.equals(float.class)) {
                schema.put("type", "number");
            } else if (cls.equals(Boolean.class) || cls.equals(boolean.class)) {
                schema.put("type", "boolean");
            } else if (cls.equals(Void.class) || cls.equals(void.class)) {
                schema.put("type", "null");
            } else if (cls.isArray()) {
                schema.put("type", "array");
                Map<String, Object> child = generateSchema(Key.of(cls.getComponentType()), getOptions);
                schema.put("items", child);
            } else {
                throw new IllegalArgumentException("Unsupported class type: " + cls.getTypeName());
            }
            return schema;
        }

        throw new IllegalArgumentException("Unsupported type: " + type.getTypeName());
    }

    public static Map<String, Object> inlineJsonSchemaOrNull(Key<?> schemaKey, Type fallbackType) {
        Type type = schemaKey == null ? fallbackType : schemaKey.getType();
        if (type == null) {
            return null;
        }

        Map<String, Object> primitive = toJsonSchema(type, false);
        if (primitive != null) {
            return primitive;
        }
        if (type instanceof Class<?> cls && cls.isEnum()) {
            return enumSchema(cls);
        }
        return null;
    }

    private static Map<String, Object> enumSchema(Class<?> enumClass) {
        Map<String, Object> schema = new Object2ObjectLinkedOpenHashMap<>();
        schema.put("type", "string");
        Object[] constants = enumClass.getEnumConstants();
        List<String> values = new ArrayList<>(constants.length);
        for (Object constant : constants) {
            values.add(((Enum<?>) constant).name());
        }
        schema.put("enum", values);
        return schema;
    }

    private static boolean hasStructuralSchemaKeys(Map<String, Object> schema) {
        for (String key : STRUCTURAL_SCHEMA_KEYS) {
            if (schema.containsKey(key)) {
                return true;
            }
        }
        return false;
    }

    private static void sanitizeSchemaNode(Object node) {
        if (node instanceof Map<?, ?> map) {
            map.remove("help");
            map.remove("annotations");
            map.remove("flag");

            Object type = map.get("type");
            if (type instanceof String typeName && !VALID_JSON_SCHEMA_TYPES.contains(typeName)) {
                map.remove("type");
            }

            if (map.containsKey("name") && !map.containsKey("properties") && !map.containsKey("$defs") && !map.containsKey("$ref")) {
                map.remove("name");
            }

            for (Object value : map.values()) {
                sanitizeSchemaNode(value);
            }
            return;
        }
        if (node instanceof Collection<?> list) {
            for (Object item : list) {
                sanitizeSchemaNode(item);
            }
        }
    }

    public static Map<String, Object> toJsonSchema(ValueStore store,
                                                    ValueStore htmlOptionsStore,
                                                    ValueStore schemaStore,
                                                    Collection<ParametricCallable<?>> commands,
                                                    GuildDB guildDB,
                                                    User user,
                                                    DBNation nation) {
        return toJsonSchema(store, htmlOptionsStore, schemaStore, commands, guildDB, user, nation, null);
    }

    public static Map<String, Object> toJsonSchema(ValueStore store,
                                                    ValueStore htmlOptionsStore,
                                                    ValueStore schemaStore,
                                                    Collection<ParametricCallable<?>> commands,
                                                    GuildDB guildDB,
                                                    User user,
                                                    DBNation nation,
                                                    SchemaDebugObserver schemaDebugObserver) {
        
        Map<Key<?>, Map<String, Object>> primitiveCache = new Object2ObjectLinkedOpenHashMap<>();

        Map<Key<?>, Parser<?>> argTypes = new Object2ObjectLinkedOpenHashMap<>();
        Set<Key<?>> seenPrimitiveKeys = new ObjectOpenHashSet<>();
        Set<Key<?>> seenDefinitionKeys = new ObjectOpenHashSet<>();

        for (ParametricCallable<?> command : commands) {
            List<ParameterData> params = command.getUserParameters();
            for (ParameterData data : params) {
                Parser<?> parser = data.getBinding();
                Key<?> key = parser.getKey();

                try {
                    CanonicalSchemaType canonical = resolveCanonicalSchemaType(store, data, command.getFullPath());
                    parser = canonical.schemaParser();
                    key = canonical.schemaKey();

                    Map<String, Object> inlineSchema = inlineJsonSchemaOrNull(key, key.getType());
                    if (inlineSchema != null) {
                        if (seenPrimitiveKeys.add(key)) {
                            primitiveCache.put(key, inlineSchema);
                        }
                        continue;
                    }
                    if (!seenDefinitionKeys.add(key)) {
                        continue;
                    }
                    argTypes.putIfAbsent(key, parser);
                } catch (IllegalArgumentException e) {
                    System.err.println("[Gpt-Tool] " + key + ": " + e.getMessage());
                }
            }
        }

        Map<String, Object> definitions = new Object2ObjectLinkedOpenHashMap<>();

        System.out.println("Found: " + argTypes.size() + " unique argument types for JSON schema generation.");

        for (Map.Entry<Key<?>, Parser<?>> entry : argTypes.entrySet()) {
            Key<?> key = entry.getKey();
            Parser<?> parser = entry.getValue();
            Map<String, Object> argumentDef = new Object2ObjectLinkedOpenHashMap<>();

            String typeName = getJsonName(key.toSimpleString());
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
                if (map != null) {
                    sanitizeSchemaNode(map);
                    if (hasStructuralSchemaKeys((Map<String, Object>) map)) {
                        argumentDef.putAll(map);
                        sanitizeSchemaNode(argumentDef);
                        definitions.put(typeName, argumentDef);
                        continue;
                    }
                }
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
                return optionToConciseSchema(option, guildDB, user, nation);
            };

            try {
                Map<String, Object> schema = generateSchema(key, getOptions);
                argumentDef.putAll(schema);
            } catch (IllegalArgumentException e) {
                System.err.println("[Gpt-Tool] " + key + " (2): " + e.getMessage());
            }

            sanitizeSchemaNode(argumentDef);

            definitions.put(typeName, argumentDef);
        }

        List<Map<String, Object>> tools = new ObjectArrayList<>();
        Map<String, Integer> perToolDefinitionCounts = new Object2ObjectLinkedOpenHashMap<>();

        for (ParametricCallable<?> command : commands) {
            String cmdName = getToolName(command);
            try {
                Map<String, Object> toolSchema = command.toJsonSchema(primitiveCache, store);
                Map<String, Object> inputSchema = (Map<String, Object>) toolSchema.get("inputSchema");

                if (inputSchema != null) {
                    Set<String> directRefs = collectDefinitionRefs(inputSchema);
                    Map<String, Object> localClosure = buildDefinitionClosure(definitions, directRefs);
                    if (!localClosure.isEmpty()) {
                        inputSchema.put("$defs", localClosure);
                        perToolDefinitionCounts.put(cmdName, localClosure.size());
                    } else {
                        perToolDefinitionCounts.put(cmdName, 0);
                    }
                } else {
                    perToolDefinitionCounts.put(cmdName, 0);
                }

                sanitizeSchemaNode(inputSchema);

                if (schemaDebugObserver != null) {
                    schemaDebugObserver.onToolSchema(cmdName, toolSchema);
                }

                tools.add(toolSchema);
            } catch (IllegalArgumentException e) {
                System.err.println("[Gpt-Tool] Skipping command " + cmdName + " in JSON schema generation: " + e.getMessage());
                if (schemaDebugObserver != null) {
                    schemaDebugObserver.onToolSchemaError(cmdName, "$.tool[" + cmdName + "]", "command.toJsonSchema failed", e.getMessage());
                }
            }
        }

        Map<String, Object> mcpResult = new Object2ObjectLinkedOpenHashMap<>();
        mcpResult.put("tools", tools);

        if (schemaDebugObserver != null) {
            schemaDebugObserver.onToolsListSchema(mcpResult, definitions, primitiveCache, perToolDefinitionCounts);
        }

        return mcpResult;
    }

    public static CanonicalSchemaType resolveCanonicalSchemaType(ValueStore store, ParameterData parameterData, String commandPath) {
        return resolveCanonicalSchemaType(store, parameterData.getBinding(), commandPath);
    }

    public static CanonicalSchemaType resolveCanonicalSchemaType(ValueStore store, Parser<?> parser, String commandPath) {
        Key<?> sourceKey = parser.getKey();
        Key<?> webType = parser.getWebTypeOrNull();
        if (webType == null) {
            return new CanonicalSchemaType(sourceKey, parser);
        }
        if (store == null) {
            return new CanonicalSchemaType(webType, parser);
        }

        Parser<?> parserForWebType = store.get(webType);
        if (parserForWebType == null) {
            throw new IllegalArgumentException("[Gpt-Tool] No parser for webType: " + webType + " (from " + sourceKey + ") used in command: " + commandPath);
        }
        return new CanonicalSchemaType(parserForWebType.getKey(), parserForWebType);
    }

    private static Map<String, Object> optionToConciseSchema(WebOption option,
                                                              GuildDB guildDB,
                                                              User user,
                                                              DBNation nation) {
        List<String> options = option.getOptions();
        if (options != null && !options.isEmpty()) {
            if (options.size() > MAX_INLINE_ENUM_VALUES) {
                return Map.of("type", "string", "description", "Free-form string; suggested options omitted due to size.");
            }
            if (option.isAllowCustomOption()) {
                return Map.of(
                        "anyOf", List.of(
                                Map.of("type", "string", "enum", options),
                                Map.of("type", "string")
                        )
                );
            }
            return Map.of("type", "string", "enum", options);
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
                        List<Map<String, Object>> oneOf = new ObjectArrayList<>();
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

                        if (oneOf.size() > MAX_INLINE_ONEOF_VALUES) {
                            return Map.of(
                                    "type", isNumeric ? "number" : "string",
                                    "description", "Value set is large; concise typed schema emitted."
                            );
                        }
                        return Map.of("type", isNumeric ? "number" : "string", "oneOf", oneOf);
                    }

                    if (isNumeric) {
                        if (optionMeta.key_numeric != null && optionMeta.key_numeric.size() > MAX_INLINE_ENUM_VALUES) {
                            return Map.of("type", "number");
                        }
                        return Map.of("type", "number", "enum", optionMeta.key_numeric);
                    }
                    if (optionMeta.key_string != null && optionMeta.key_string.size() > MAX_INLINE_ENUM_VALUES) {
                        return Map.of("type", "string");
                    }
                    return Map.of("type", "string", "enum", optionMeta.key_string);
                }
            } catch (IllegalArgumentException ignored) {
            }
        }

        return null;
    }

    private static Set<String> collectDefinitionRefs(Object node) {
        Set<String> refs = new ObjectLinkedOpenHashSet<>();
        collectDefinitionRefs(node, refs);
        return refs;
    }

    private static void collectDefinitionRefs(Object node, Set<String> refs) {
        if (node instanceof Map<?, ?> map) {
            Object ref = map.get("$ref");
            if (ref instanceof String refValue && refValue.startsWith("#/$defs/")) {
                refs.add(refValue.substring("#/$defs/".length()));
            }
            for (Object value : map.values()) {
                collectDefinitionRefs(value, refs);
            }
            return;
        }
        if (node instanceof Collection<?> list) {
            for (Object item : list) {
                collectDefinitionRefs(item, refs);
            }
        }
    }

    private static Map<String, Object> buildDefinitionClosure(Map<String, Object> allDefinitions, Set<String> initialRefs) {
        if (allDefinitions == null || allDefinitions.isEmpty() || initialRefs == null || initialRefs.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Object> closure = new Object2ObjectLinkedOpenHashMap<>();
        Deque<String> queue = new ArrayDeque<>(initialRefs);
        Set<String> visited = new ObjectOpenHashSet<>();

        while (!queue.isEmpty()) {
            String name = queue.removeFirst();
            if (!visited.add(name)) {
                continue;
            }
            Object def = allDefinitions.get(name);
            if (!(def instanceof Map<?, ?> defMap)) {
                continue;
            }
            closure.put(name, (Map<String, Object>) defMap);
            Set<String> nestedRefs = collectDefinitionRefs(defMap);
            for (String nested : nestedRefs) {
                if (!visited.contains(nested)) {
                    queue.addLast(nested);
                }
            }
        }

        return closure;
    }
}
