package link.locutus.discord.gpt.mcp;

import java.lang.reflect.ParameterizedType;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import link.locutus.discord.commands.manager.v2.binding.MethodParser;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.command.ParameterData;
import link.locutus.discord.commands.manager.v2.command.ParametricCallable;
import link.locutus.discord.commands.manager.v2.command.WebOption;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.web.commands.binding.value_types.WebOptions;


import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.Parser;
import net.dv8tion.jda.api.entities.User;
import java.lang.reflect.Type;
import java.text.Normalizer;
import java.util.*;
import java.util.function.Function;
public class MCPUtil {
    
    public static Map<String, Object> toJsonSchema(Type type, boolean supportUnknown) {
        Map<String, Object> schema = new Object2ObjectLinkedOpenHashMap<>();

        if (type instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) type;
            Class<?> raw = (Class<?>) pt.getRawType();

            // Handle Map<K,V>
            if (Map.class.isAssignableFrom(raw)) {
                schema.put("type", "object");
                Type valueType = pt.getActualTypeArguments()[1];
                Map<String, Object> child = toJsonSchema(valueType, supportUnknown);
                if (child == null) return null;
                schema.put("additionalProperties", child);
                return schema;
            }

            // Handle Collection<T> (List, Set)
            if (Collection.class.isAssignableFrom(raw)) {
                schema.put("type", "array");
                Type elementType = pt.getActualTypeArguments()[0];
                Map<String, Object> child = toJsonSchema(elementType, supportUnknown);
                if (child == null) return null;
                schema.put("items", child);
                return schema;
            }

            if (!supportUnknown) return null;

            // Fallback: treat as object
            schema.put("type", "object");
            return schema;
        }

        // Handle raw classes (non-parameterized)
        if (type instanceof Class) {
            Class<?> cls = (Class<?>) type;
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
                // For any other custom class, treat as object
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
        if (type instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) type;
            Class<?> raw = (Class<?>) pt.getRawType();

            // Handle Map<K,V>
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
                        entry.put("value", e.name());
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

    public static Map<String, Object> toJsonSchema(ValueStore store, ValueStore htmlOptionsStore, ValueStore schemaStore, Collection<ParametricCallable<?>> commands, GuildDB guildDB, User user, DBNation nation) {
        Map<String, Object> root = new Object2ObjectLinkedOpenHashMap<>();

        Map<Key<?>, Map<String, Object>> primitiveCache = new Object2ObjectLinkedOpenHashMap<>();

        Set<Parser<?>> argTypes = new ObjectLinkedOpenHashSet<>();
        Set<Key<?>> visited = new ObjectOpenHashSet<>();

        for (ParametricCallable<?> command : commands) {
            List<ParameterData> params = command.getUserParameters();
            for (ParameterData data : params) {
                Parser<?> parser = data.getBinding();
                Key<?> key = parser.getKey();
                if (!visited.add(key)) continue;

                Key<?> webType = parser.getWebTypeOrNull();
                try {
                    if (webType != null) {
                        parser = store.get(webType);
                        if (parser == null) {
                            throw new IllegalArgumentException("[Gpt-Tool] No parser for webType: " + webType + " (from " + key + ") used in command: " + command.getFullPath());
                        }
                        key = parser.getKey();
                    }
                    if (data.getAnnotations().length == 0) {
                        Map<String, Object> existing = primitiveCache.get(key);
                        if (existing == null) {
                            Map<String, Object> primitiveSchema = toJsonSchema(data.getType(), false);
                            primitiveCache.put(key, primitiveSchema);
                        }
                        continue;
                    }
                    argTypes.add(parser);
                } catch (IllegalArgumentException e) {
                    System.err.println("[Gpt-Tool] " + key + ": " + e.getMessage());
                }
            }
        }

        Map<String, Object> definitions = new Object2ObjectLinkedOpenHashMap<>();

        System.out.println("Found: " + argTypes.size() + " unique argument types for JSON schema generation.");

        for (Parser<?> parser : argTypes) {
            Key<?> key = parser.getKey();
            Map<String, Object> argumentDef = new Object2ObjectLinkedOpenHashMap<>();

            String typeName = getJsonName(key.toSimpleString());
            Parser<?> schemaBinding = schemaStore.get(key);

            String typeDesc = schemaBinding == null ? null : schemaBinding.getDescription();
            if (typeDesc == null || typeDesc.isEmpty()) typeDesc = parser.getDescription();
            String[] examples = parser.getExamples();

            argumentDef.put("type", typeName);
            if (!typeDesc.isEmpty()) argumentDef.put("description", typeDesc);
            else {
                String classAndMethodOrNull = (parser instanceof MethodParser<?> methodParser) ?
                        methodParser.getMethod().getDeclaringClass().getSimpleName() + "." + methodParser.getMethod().getName()
                        : null;
                System.err.println("[Gpt-Tool] " + "Error: No description for type " + key + " in JSON schema generation | " + classAndMethodOrNull);
            }
            if (examples.length > 0) argumentDef.put("examples", Arrays.asList(examples));

            if (schemaBinding != null) {
                Map<String, Object> map = (Map<String, Object>) schemaBinding.apply(store, null);
                argumentDef.putAll(map);
                continue;
            }

            Function<Key<?>, Map<String, Object>> getOptions = t -> {
                Parser<?> toolOptions = schemaStore.get(t);
                if (toolOptions != null) {
                    return (Map<String, Object>) toolOptions.apply(store, null);
                }
                if (t instanceof ParameterizedType) {
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
                                        if (desc.length() > 0) desc.append(" - ");
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
                            } else {
                                return Map.of(
                                        "type", "string",
                                        "enum", optionMeta.key_string
                                );
                            }
                        }
                    } catch (IllegalArgumentException e) {
                        // ignore for now
                    }
                }

                return null;
            };

            // generateSchema for the type
            try {
                Map<String, Object> schema = generateSchema(key, getOptions);

//                Object mock = MockSchema.generate(schema);
//                System.out.println("\n\n------\n\n");
//                System.out.println("MOCK " + key.toSimpleString() + ":\n"
//                        + WebUtil.GSON.toJson(mock) + "\n\n" +
//                        "SCHEMA:\n" +
//                        WebUtil.GSON.toJson(schema));
//                System.out.println("\n\n------\n\n");

                argumentDef.putAll(schema);
            } catch (IllegalArgumentException e) {
                System.err.println("[Gpt-Tool] " + key + " (2): " + e.getMessage());
            }
            // enum

            definitions.put(typeName, argumentDef);
        }
        List<Map<String, Object>> tools = new ObjectArrayList<>();
        for (ParametricCallable<?> command : commands) {
            String cmdName = command.getFullPath().toLowerCase();
            try {
                Map<String, Object> toolSchema = command.toJsonSchema(primitiveCache);
                
                // INJECT $defs into the tool's inputSchema directly
                if (!definitions.isEmpty()) {
                    Map<String, Object> inputSchema = (Map<String, Object>) toolSchema.get("inputSchema");
                    if (inputSchema != null) {
                        inputSchema.put("$defs", definitions);
                    }
                }
                
                tools.add(toolSchema);
            } catch (IllegalArgumentException e) {
                System.err.println("[Gpt-Tool] Skipping command " + cmdName + " in JSON schema generation: " + e.getMessage());
            }
        }
        
        // Return JUST the tools list. In MCP, we don't wrap this in a custom root object,
        // we return it as the result of the tools/list JSON-RPC call.
        Map<String, Object> mcpResult = new Object2ObjectLinkedOpenHashMap<>();
        mcpResult.put("tools", tools);
        return mcpResult; 
    }
}
