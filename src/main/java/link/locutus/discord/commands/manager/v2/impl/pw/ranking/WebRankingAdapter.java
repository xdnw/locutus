package link.locutus.discord.commands.manager.v2.impl.pw.ranking;

import link.locutus.discord.web.commands.binding.value_types.WebRankingEntityRef;
import link.locutus.discord.web.commands.binding.value_types.WebRankingMetricDescriptor;
import link.locutus.discord.web.commands.binding.value_types.WebRankingMetricValue;
import link.locutus.discord.web.commands.binding.value_types.WebRankingNumericValue;
import link.locutus.discord.web.commands.binding.value_types.WebRankingQueryField;
import link.locutus.discord.web.commands.binding.value_types.WebRankingResult;
import link.locutus.discord.web.commands.binding.value_types.WebRankingRow;
import link.locutus.discord.web.commands.binding.value_types.WebRankingSection;
import link.locutus.discord.web.commands.binding.value_types.WebRankingSort;

public final class WebRankingAdapter {
    private WebRankingAdapter() {
    }

    public static WebRankingResult toWeb(RankingResult result) {
        return new WebRankingResult(
                result.responseKey(),
                result.title(),
                result.querySummary().stream().map(field -> new WebRankingQueryField(field.key(), field.label(), field.value())).toList(),
                result.rowCount(),
                result.asOfMs(),
                result.emptySectionPolicy().name(),
                result.sections().stream().map(WebRankingAdapter::toWeb).toList()
        );
    }

    private static WebRankingSection toWeb(RankingSection section) {
        return new WebRankingSection(
                section.sectionKey(),
                section.title(),
                section.entityType().name(),
                section.metrics().stream().map(metric -> new WebRankingMetricDescriptor(metric.metricKey(), metric.label(), metric.numericType().name(), metric.valueFormat().name())).toList(),
                new WebRankingSort(section.sort().metricKey(), section.sort().direction().name(), section.sort().tieBreaker().name()),
                section.metadata().stream().map(field -> new WebRankingQueryField(field.key(), field.label(), field.value())).toList(),
                section.notes(),
                section.rows().stream().map(WebRankingAdapter::toWeb).toList(),
                section.rowCount()
        );
    }

    private static WebRankingRow toWeb(RankingRow row) {
        return new WebRankingRow(
                new WebRankingEntityRef(row.entity().entityKey(), row.entity().entityType().name(), row.entity().entityId(), row.entity().displayHint()),
                row.metricValues().stream().map(metric -> new WebRankingMetricValue(metric.metricKey(), toWeb(metric.value()))).toList(),
                toWeb(row.sortValue()),
                row.highlighted(),
                row.annotation()
        );
    }

    private static WebRankingNumericValue toWeb(RankingNumericValue value) {
        return new WebRankingNumericValue(value.exactValue(), value.numericType().name());
    }
}
