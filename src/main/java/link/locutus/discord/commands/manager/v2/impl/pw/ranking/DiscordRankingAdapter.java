package link.locutus.discord.commands.manager.v2.impl.pw.ranking;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.command.CommandBehavior;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.RateLimitedSources;
import net.dv8tion.jda.api.entities.User;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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

        String baseTitle = resolveTitle(command, result);
        if (result.sectionRanges().isEmpty()) {
            message.embed(baseTitle, "No rows.");
            if (options != null && options.mentionUser() != null) {
                message.append("\n" + options.mentionUser().getAsMention());
            }
            if (command != null) {
                message.commandButton(CommandBehavior.DELETE_MESSAGE, null, command.toString(), "Refresh");
            }
            message.send(RateLimitedSources.COMMAND_RESULT);
            return;
        }

        for (int sectionIndex = 0; sectionIndex < result.sectionRanges().size(); sectionIndex++) {
            String title = result.sectionRanges().size() == 1
                    ? baseTitle
                    : baseTitle + " | " + renderSectionLabel(result.sectionRanges().get(sectionIndex).kind());
            String body = renderSectionBody(result, sectionIndex, rowLimit);

            if (body.length() > MAX_EMBED_DESCRIPTION_LENGTH) {
                body = body.substring(0, MAX_EMBED_DESCRIPTION_LENGTH - 3) + "...";
                attachFile = true;
            }

            message.embed(title, body);

            if (wantFile) {
                fileLines.add("== " + title + " ==");
                fileLines.addAll(renderAllRows(result, sectionIndex));
                fileLines.add("");
            }

            if (result.sectionRanges().get(sectionIndex).rowCount() > rowLimit) {
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
            message.file(result.kind().name().toLowerCase(Locale.ROOT) + ".txt", String.join("\n", fileLines));
        }
        message.send(RateLimitedSources.COMMAND_RESULT);
    }

    private static String renderSectionBody(RankingResult result, int sectionIndex, int rowLimit) {
        RankingSectionRange section = result.sectionRanges().get(sectionIndex);
        if (section.rowCount() == 0) {
            return "No rows.";
        }

        List<String> lines = new ArrayList<>();
        int visible = Math.min(rowLimit, section.rowCount());
        for (int i = 0; i < visible; i++) {
            lines.add(renderRow(result, section.rowOffset() + i, i));
        }

        Set<Long> highlights = new LinkedHashSet<>(result.highlightedIds());
        boolean addedEllipsis = false;
        for (int i = rowLimit; i < section.rowCount(); i++) {
            long keyId = result.keyIds().get(section.rowOffset() + i);
            if (!highlights.contains(keyId)) {
                continue;
            }
            if (!addedEllipsis) {
                lines.add("...");
                addedEllipsis = true;
            }
            lines.add(renderRow(result, section.rowOffset() + i, i));
        }
        return String.join("\n", lines);
    }

    private static List<String> renderAllRows(RankingResult result, int sectionIndex) {
        RankingSectionRange section = result.sectionRanges().get(sectionIndex);
        List<String> lines = new ArrayList<>(section.rowCount());
        for (int i = 0; i < section.rowCount(); i++) {
            lines.add(renderRow(result, section.rowOffset() + i, i));
        }
        return lines;
    }

    private static String renderRow(RankingResult result, int globalRowIndex, int sectionRowIndex) {
        boolean multipleColumns = result.valueColumns().size() > 1;
        List<String> renderedValues = new ArrayList<>(result.valueColumns().size());
        for (RankingValueColumn column : result.valueColumns()) {
            BigDecimal value = column.values().get(globalRowIndex);
            String renderedNumeric = renderNumeric(value, column.format());
            String label = multipleColumns ? renderValueLabel(column.kind()) : null;
            renderedValues.add(label == null ? renderedNumeric : label + " " + renderedNumeric);
        }

        long keyId = result.keyIds().get(globalRowIndex);
        String line = (sectionRowIndex + 1) + ". " + entityMarkup(result.keyType(), keyId) + ": " + String.join(" | ", renderedValues);
        return result.highlightedIds().contains(keyId) ? "**" + line + "**" : line;
    }

    private static String entityMarkup(RankingEntityType entityType, long entityId) {
        if (Locutus.imp() != null) {
            if (entityType == RankingEntityType.ALLIANCE) {
                DBAlliance alliance = DBAlliance.getOrCreate((int) entityId);
                if (alliance != null) {
                    return alliance.getMarkdownUrl();
                }
            } else if (entityType == RankingEntityType.NATION) {
                DBNation nation = DBNation.getOrCreate((int) entityId);
                if (nation != null) {
                    return nation.getMarkdownUrl();
                }
            }
        }
        return entityType.name().toLowerCase(Locale.ROOT) + ":" + entityId;
    }

    private static String renderNumeric(BigDecimal numeric, RankingValueFormat format) {
        return switch (format) {
            case MONEY -> "$" + formatDecimal(numeric);
            case PERCENT -> formatDecimal(numeric.multiply(BigDecimal.valueOf(100L))) + "%";
            case COUNT -> formatInteger(numeric);
            case NUMBER -> formatDecimal(numeric);
        };
    }

    private static String renderSectionLabel(RankingSectionKind kind) {
        return humanize(kind.name());
    }

    private static String renderValueLabel(RankingValueKind kind) {
        return switch (kind) {
            case PRIMARY -> null;
            case AMOUNT -> "Amt";
            case PRICE_PER_UNIT -> "PPU";
        };
    }

    private static String resolveTitle(JSONObject command, RankingResult result) {
        if (command != null) {
            String title = command.optString("title", null);
            if (title != null && !title.isBlank()) {
                return title;
            }
        }
        return humanize(result.kind().name());
    }

    private static String humanize(String value) {
        if (value == null || value.isBlank()) {
            return "Ranking";
        }
        String normalized = value.replace('.', '_').replace('-', '_').replace(' ', '_');
        String[] parts = normalized.split("_+");
        List<String> words = new ArrayList<>(parts.length);
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            words.add(Character.toUpperCase(part.charAt(0)) + part.substring(1).toLowerCase(Locale.ROOT));
        }
        return words.isEmpty() ? value : String.join(" ", words);
    }

    private static String formatInteger(BigDecimal value) {
        return INT_FORMAT.get().format(value);
    }

    private static String formatDecimal(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        if (stripped.scale() <= 0) {
            return INT_FORMAT.get().format(stripped);
        }
        return DECIMAL_FORMAT.get().format(value);
    }
}
