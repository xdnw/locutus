package link.locutus.discord.commands.manager.v2.command;

import link.locutus.discord.commands.manager.v2.command.shrink.EmbedShrink;
import link.locutus.discord.commands.manager.v2.command.shrink.ShrinkableField;
import link.locutus.discord.commands.manager.v2.table.TableNumberFormat;
import link.locutus.discord.commands.manager.v2.table.TimeFormat;
import link.locutus.discord.web.commands.binding.value_types.GraphType;
import link.locutus.discord.web.commands.binding.value_types.WebGraph;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class McpMessageContentAdapter {
    private static final int RESOURCE_FRAGMENT_BYTES = 64 * 1024;

    private McpMessageContentAdapter() {
    }

    public static List<Map<String, Object>> fromBuilder(IMessageBuilder builder) {
        if (builder == null) {
            return List.of();
        }
        if (builder instanceof AMessageBuilder richBuilder) {
            return richBuilder.toMcpContentItems();
        }
        String text = builder.toString();
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return List.of(textItem(text));
    }

    public static List<Map<String, Object>> fromMessages(Collection<? extends IMessageBuilder> messages) {
        List<Map<String, Object>> content = new ArrayList<>();
        if (messages == null) {
            return content;
        }
        for (IMessageBuilder message : messages) {
            content.addAll(fromBuilder(message));
        }
        return content;
    }

    public static void appendResultObject(List<Map<String, Object>> content, Object result) {
        if (result == null) {
            return;
        }
        if (result instanceof IMessageBuilder builder) {
            content.addAll(fromBuilder(builder));
            return;
        }
        if (result instanceof Collection<?> collection) {
            for (Object entry : collection) {
                appendResultObject(content, entry);
            }
            return;
        }
        if (result instanceof Object[] array) {
            for (Object entry : array) {
                appendResultObject(content, entry);
            }
            return;
        }
        String text = result.toString();
        if (!text.isBlank()) {
            content.add(textItem(text));
        }
    }

    public static Map<String, Object> toolResult(Collection<? extends IMessageBuilder> messages, Object result) {
        List<Map<String, Object>> content = fromMessages(messages);
        appendResultObject(content, result);
        if (content.isEmpty()) {
            content.add(textItem("<no-output>"));
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("content", content);
        payload.put("metadata", Map.of(
                "messageCount", messages == null ? 0 : messages.size(),
                "itemCount", content.size()
        ));
        return payload;
    }

    public static Map<String, Object> textItem(String text) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", "text");
        item.put("text", text == null ? "" : text);
        return item;
    }

    static Map<String, Object> actionItem(String label, String command) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", "action");
        item.put("label", label);
        item.put("command", command);
        return item;
    }

    static Map<String, Object> linkItem(String label, String url) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", "link");
        item.put("label", label);
        item.put("url", url);
        return item;
    }

    static Map<String, Object> embedTextItem(EmbedShrink embed) {
        EmbedShrink shrunk = new EmbedShrink(embed).shrinkDefault();

        Map<String, Object> embedShape = new LinkedHashMap<>();
        embedShape.put("title", shrunk.getTitle().get());
        String description = shrunk.getDescription() == null ? "" : shrunk.getDescription().get();
        if (!description.isEmpty()) {
            embedShape.put("description", description);
        }
        if (shrunk.getFooter() != null && !shrunk.getFooter().isEmpty()) {
            embedShape.put("footer", shrunk.getFooter().get());
        }
        if (!shrunk.getFields().isEmpty()) {
            List<Map<String, Object>> fields = new ArrayList<>();
            for (ShrinkableField field : shrunk.getFields()) {
                fields.add(Map.of(
                        "name", field.name.get(),
                        "value", field.value.get(),
                        "inline", field.inline
                ));
            }
            embedShape.put("fields", fields);
        }

        StringBuilder text = new StringBuilder();
        if (embedShape.containsKey("title")) {
            text.append("## ").append(embedShape.get("title")).append("\n");
        }
        if (embedShape.containsKey("description")) {
            text.append(embedShape.get("description")).append("\n");
        }
        if (embedShape.containsKey("fields")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> fields = (List<Map<String, Object>>) embedShape.get("fields");
            for (Map<String, Object> field : fields) {
                text.append("- ")
                        .append(field.get("name"))
                        .append(": ")
                        .append(field.get("value"))
                        .append("\n");
            }
        }
        if (embedShape.containsKey("footer")) {
            text.append("_").append(embedShape.get("footer")).append("_\n");
        }

        Map<String, Object> item = textItem(text.toString().trim());
        item.put("format", "embed");
        item.put("embed", embedShape);
        return item;
    }

    static Map<String, Object> imageItem(String name, byte[] data) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", "image");
        item.put("name", name);
        item.put("mimeType", mimeType(name));
        item.put("encoding", "base64");
        item.put("data", Base64.getEncoder().encodeToString(data));
        return item;
    }

    static List<Map<String, Object>> resourceItems(String name, byte[] data) {
        List<Map<String, Object>> fragments = new ArrayList<>();
        if (data == null || data.length == 0) {
            fragments.add(resourceFragmentItem(name, new byte[0], 0, 1));
            return fragments;
        }

        int fragmentCount = (data.length + RESOURCE_FRAGMENT_BYTES - 1) / RESOURCE_FRAGMENT_BYTES;
        for (int i = 0; i < fragmentCount; i++) {
            int start = i * RESOURCE_FRAGMENT_BYTES;
            int end = Math.min(data.length, start + RESOURCE_FRAGMENT_BYTES);
            byte[] fragment = new byte[end - start];
            System.arraycopy(data, start, fragment, 0, fragment.length);
            fragments.add(resourceFragmentItem(name, fragment, i, fragmentCount));
        }
        return fragments;
    }

    private static Map<String, Object> resourceFragmentItem(String name, byte[] data, int index, int count) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", "resource");
        item.put("name", name);
        item.put("mimeType", mimeType(name));
        item.put("encoding", "base64");
        item.put("fragmentIndex", index);
        item.put("fragmentCount", count);
        item.put("data", Base64.getEncoder().encodeToString(data));
        return item;
    }

    static Map<String, Object> graphItem(IMessageBuilder.GraphMessageInfo tableInfo) {
        GraphType requestedType = tableInfo.type();
        GraphType resolvedType = requestedType == null
                ? (tableInfo.table().isBar() ? GraphType.SIDE_BY_SIDE_BAR : GraphType.LINE)
                : requestedType;

        WebGraph graph = tableInfo.table().toHtmlJson(
                tableInfo.timeFormat(),
                tableInfo.numberFormat(),
                resolvedType,
                tableInfo.origin()
        );

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("x", graph.x);
        schema.put("y", graph.y);
        schema.put("labels", graph.labels == null ? List.of() : Arrays.asList(graph.labels));

        TimeFormat tf = graph.time_format;
        TableNumberFormat nf = graph.number_format;
        GraphType gt = graph.type;

        Map<String, Object> hints = new LinkedHashMap<>();
        hints.put("title", graph.title);
        hints.put("timeFormat", tf == null ? null : tf.name());
        hints.put("numberFormat", nf == null ? null : nf.name());
        hints.put("type", gt == null ? null : gt.name());
        hints.put("origin", graph.origin);

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", "graph");
        item.put("data", graph.data);
        item.put("schema", schema);
        item.put("hints", hints);
        return item;
    }

    static Map<String, Object> graphErrorItem(IMessageBuilder.GraphMessageInfo tableInfo, IOException error) {
        String tableName = tableInfo.table() == null ? "graph" : tableInfo.table().getName();
        String message = "Graph rendering failed for " + tableName + ": " + error.getMessage();
        Map<String, Object> item = textItem(message);
        item.put("format", "error");
        item.put("source", "graph");
        return item;
    }

    private static String mimeType(String name) {
        String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        if (lower.endsWith(".svg")) {
            return "image/svg+xml";
        }
        if (lower.endsWith(".json")) {
            return "application/json";
        }
        if (lower.endsWith(".csv")) {
            return "text/csv";
        }
        if (lower.endsWith(".txt") || lower.endsWith(".log") || lower.endsWith(".md")) {
            return "text/plain";
        }
        return "application/octet-stream";
    }

    static Map<String, Object> structuredTextItem(String text, String format) {
        Map<String, Object> item = textItem(text);
        if (format != null) {
            item.put("format", format);
        }
        return item;
    }

    static Map<String, Object> objectTextItem(Object value) {
        return structuredTextItem(String.valueOf(value), "result");
    }
}