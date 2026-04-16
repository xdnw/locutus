package link.locutus.discord.commands.manager.v2.impl.pw.ranking;

import link.locutus.discord.commands.manager.v2.command.CommandBehavior;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.Locutus;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.RateLimitedSources;
import net.dv8tion.jda.api.entities.User;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DiscordRankingAdapter {
    private static final int MAX_EMBED_DESCRIPTION_LENGTH = 4096;
    private static final ThreadLocal<DecimalFormat> INT_FORMAT = ThreadLocal.withInitial(() -> new DecimalFormat("#,###"));
    private static final ThreadLocal<DecimalFormat> DECIMAL_FORMAT = ThreadLocal.withInitial(() -> new DecimalFormat("#,###.00"));

    private DiscordRankingAdapter() {
    }

    public record RenderOptions(Integer rowLimit, boolean uploadFile, User mentionUser) {
        public int effectiveRowLimit() {
            return rowLimit == null || rowLimit < 1 ? 25 : rowLimit;
        }
    }

    public static void send(IMessageIO channel, JSONObject command, RankingResult result, RenderOptions options) {
        int rowLimit = options == null ? 25 : options.effectiveRowLimit();
        boolean wantFile = options != null && options.uploadFile();

        IMessageBuilder message = channel.create();
        List<String> fileLines = wantFile ? new ArrayList<>() : null;
        boolean attachFile = false;

        for (RankingSection section : result.sections()) {
            String title = result.sections().size() == 1 ? result.title() : result.title() + " | " + section.title();
            String body = renderSectionBody(section, rowLimit);

            if (body.length() > MAX_EMBED_DESCRIPTION_LENGTH) {
                body = body.substring(0, MAX_EMBED_DESCRIPTION_LENGTH - 3) + "...";
                attachFile = true;
            }

            message.embed(title, body);

            if (wantFile) {
                fileLines.add("== " + title + " ==");
                if (!section.notes().isEmpty()) {
                    fileLines.addAll(section.notes());
                    fileLines.add("");
                }
                fileLines.addAll(renderAllRows(section));
                fileLines.add("");
            }

            if (section.rowCount() > rowLimit) {
                attachFile = true;
            }
        }

        if (options != null && options.mentionUser() != null) {
            message.append("\n" + options.mentionUser().getAsMention());
        }
        if (command != null) {
            message.commandButton(CommandBehavior.DELETE_MESSAGE, null, command.toString(), "Refresh");
        }
        if (wantFile && attachFile) {
            message.file(result.responseKey() + ".txt", String.join("\n", fileLines));
        }
        message.send(RateLimitedSources.COMMAND_RESULT);
    }

    private static String renderSectionBody(RankingSection section, int rowLimit) {
        List<String> lines = new ArrayList<>();
        if (!section.notes().isEmpty()) {
            lines.addAll(section.notes());
        }
        if (section.rows().isEmpty()) {
            lines.add("No rows.");
            return String.join("\n", lines);
        }
        if (!lines.isEmpty()) {
            lines.add("");
        }

        Map<String, RankingMetricDescriptor> metricByKey = metricByKey(section.metrics());
        boolean singleMetric = section.metrics().size() == 1;
        List<RankingRow> rows = section.rows();
        int visible = Math.min(rowLimit, rows.size());
        for (int i = 0; i < visible; i++) {
            lines.add(renderRow(i, rows.get(i), metricByKey, singleMetric));
        }

        boolean addedEllipsis = false;
        for (int i = rowLimit; i < rows.size(); i++) {
            RankingRow row = rows.get(i);
            if (!row.highlighted()) {
                continue;
            }
            if (!addedEllipsis) {
                lines.add("...");
                addedEllipsis = true;
            }
            lines.add(renderRow(i, row, metricByKey, singleMetric));
        }
        return String.join("\n", lines);
    }

    private static List<String> renderAllRows(RankingSection section) {
        Map<String, RankingMetricDescriptor> metricByKey = metricByKey(section.metrics());
        boolean singleMetric = section.metrics().size() == 1;
        List<String> lines = new ArrayList<>(section.rows().size());
        for (int i = 0; i < section.rows().size(); i++) {
            lines.add(renderRow(i, section.rows().get(i), metricByKey, singleMetric));
        }
        return lines;
    }

    private static Map<String, RankingMetricDescriptor> metricByKey(List<RankingMetricDescriptor> metrics) {
        Map<String, RankingMetricDescriptor> metricByKey = new HashMap<>();
        for (RankingMetricDescriptor metric : metrics) {
            metricByKey.put(metric.metricKey(), metric);
        }
        return metricByKey;
    }

    private static String renderRow(int index, RankingRow row, Map<String, RankingMetricDescriptor> metricByKey, boolean singleMetric) {
        List<String> renderedValues = new ArrayList<>(row.metricValues().size());
        for (RankingMetricValue metricValue : row.metricValues()) {
            RankingMetricDescriptor metric = metricByKey.get(metricValue.metricKey());
            String rendered = renderNumeric(metricValue.value(), metric == null ? RankingValueFormat.NUMBER : metric.valueFormat());
            if (!singleMetric && metric != null) {
                rendered = metric.label() + ": " + rendered;
            }
            renderedValues.add(rendered);
        }

        String line = (index + 1) + ". " + entityMarkup(row.entity()) + ": " + String.join(" | ", renderedValues);
        if (row.annotation() != null && !row.annotation().isBlank()) {
            line += " | " + row.annotation();
        }
        return row.highlighted() ? "**" + line + "**" : line;
    }

    private static String entityMarkup(RankingEntityRef entity) {
        if (Locutus.imp() != null) {
            if (entity.entityType() == RankingEntityType.ALLIANCE) {
                DBAlliance alliance = DBAlliance.getOrCreate((int) entity.entityId());
                if (alliance != null) {
                    return alliance.getMarkdownUrl();
                }
            } else if (entity.entityType() == RankingEntityType.NATION) {
                DBNation nation = DBNation.getOrCreate((int) entity.entityId());
                if (nation != null) {
                    return nation.getMarkdownUrl();
                }
            }
        }
        if (entity.displayHint() != null && !entity.displayHint().isBlank()) {
            return entity.displayHint();
        }
        return entity.entityKey();
    }

    private static String renderNumeric(RankingNumericValue value, RankingValueFormat format) {
        BigDecimal numeric = value.toBigDecimal();
        return switch (format) {
            case MONEY -> "$" + formatDecimal(numeric);
            case PERCENT -> formatDecimal(numeric.multiply(BigDecimal.valueOf(100))) + "%";
            case COUNT -> formatInteger(numeric);
            case NUMBER -> value.numericType() == RankingNumericType.INTEGER ? formatInteger(numeric) : formatDecimal(numeric);
        };
    }

    private static String formatInteger(BigDecimal value) {
        return INT_FORMAT.get().format(value.toBigInteger());
    }

    private static String formatDecimal(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        if (stripped.scale() <= 0) {
            return INT_FORMAT.get().format(stripped.toBigInteger());
        }
        return DECIMAL_FORMAT.get().format(value);
    }
}
