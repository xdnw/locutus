package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.city.BatchEntry;
import link.locutus.discord.apiv1.enums.city.CityFallbackHeuristic;
import link.locutus.discord.apiv1.enums.city.INationCity;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.annotation.Switch;
import link.locutus.discord.commands.manager.v2.binding.annotation.Timestamp;
import link.locutus.discord.commands.manager.v2.binding.bindings.PlaceholderCache;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBCity;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.guild.SheetKey;
import link.locutus.discord.pnw.NationList;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.sheet.SpreadSheet;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.ToDoubleFunction;

public class RevenueSheetCommand {
    @Command(desc = "Get a sheet of nations and their revenue (compared to batch-heuristic city builds)", viewable = true)
    @RolePermission(value = Roles.MEMBER, onlyInGuildAlliance = true)
    public String revenueSheet(
            @Me IMessageIO io,
            @Me @Default GuildDB db,
            NationList nations,
            @Switch("s") SpreadSheet sheet,
            @Switch("i") boolean includeUntaxable,
            @Switch("t") @Timestamp Long snapshotTime
    ) throws GeneralSecurityException, IOException, ExecutionException, InterruptedException {

        validateNationCount(nations, db);

        Set<DBNation> snapshot = PW.getNationsSnapshot(
                nations.getNations(),
                nations.getFilter(),
                snapshotTime,
                db == null ? null : db.getGuild()
        );

        SpreadSheet targetSheet = (sheet != null)
                ? sheet
                : SpreadSheet.create(db, SheetKey.REVENUE_SHEET);

        FilterResult filtered = filterNations(snapshot, db, includeUntaxable);
        if (filtered.nations.isEmpty()) {
            return appendFooter("No nations to process.", filtered.footer);
        }

        List<String> header = buildRevenueHeader();
        targetSheet.setHeader(header);

        CompletableFuture<IMessageBuilder> progressMessage = io.sendMessage("Please wait...");
        AtomicLong lastProgressUpdate = new AtomicLong(System.currentTimeMillis());

        ValueStore<DBNation> cacheStore = PlaceholderCache.createCache(filtered.nations, DBNation.class);

        List<CompletableFuture<List<String>>> rowFutures = new ArrayList<>(filtered.nations.size());
        for (DBNation nation : filtered.nations) {
            rowFutures.add(CompletableFuture.supplyAsync(
                    () -> buildRevenueRow(
                            nation,
                            header.size(),
                            cacheStore,
                            io,
                            progressMessage,
                            lastProgressUpdate
                    ),
                    Locutus.imp().getExecutor()
            ));
        }

        for (CompletableFuture<List<String>> future : rowFutures) {
            targetSheet.addRow(future.get());
        }

        targetSheet.updateClearCurrentTab();
        targetSheet.updateWrite();

        IMessageBuilder result = targetSheet.attach(io.create(), "revenue");
        if (!filtered.footer.isEmpty()) {
            result.append("\n" + String.join("\n", filtered.footer));
        }
        result.send();

        return null;
    }

    private void validateNationCount(NationList nations, GuildDB db) {
        int requested = nations.getNations().size();
        boolean isAllianceGuild = db != null && db.isValidAlliance();

        if (requested > 100 && !isAllianceGuild) {
            throw new IllegalArgumentException(
                    "Too many nations: " + requested + " (max: 100 outside of an alliance guild)"
            );
        }
    }

    private FilterResult filterNations(Set<DBNation> input, GuildDB db, boolean includeUntaxable) {
        Set<DBNation> nations = new LinkedHashSet<>(input);
        List<String> footer = new ArrayList<>();

        int before = nations.size();

        int removedNotAlliance = 0;
        if (db != null) {
            Set<Integer> allianceIds = db.getAllianceIds(false);
            nations.removeIf(nation ->
                    nation.getPosition() <= Rank.APPLICANT.id
                            || (!allianceIds.isEmpty() && !allianceIds.contains(nation.getAlliance_id()))
            );
            removedNotAlliance = before - nations.size();
            before = nations.size();
        }

        nations.removeIf(nation -> nation.getVm_turns() > 0);
        int removedVm = before - nations.size();
        before = nations.size();

        if (!includeUntaxable) {
            nations.removeIf(nation -> !nation.isTaxable());
        }
        int removedUntaxable = before - nations.size();

        if (removedNotAlliance > 0) {
            footer.add(removedNotAlliance + " nations were removed for not being members of the guild's alliances");
        }
        if (removedVm > 0) {
            footer.add(removedVm + " nations were removed for being in vacation mode");
        }
        if (removedUntaxable > 0) {
            footer.add(removedUntaxable + " nations were removed for being untaxable");
        }

        return new FilterResult(nations, footer);
    }

    private List<String> buildRevenueHeader() {
        List<String> header = new ArrayList<>(Arrays.asList(
                "nation",
                "tax_id",
                "cities",
                "avg_infra",
                "avg_land",
                "avg_buildings",
                "avg_disease",
                "avg_crime",
                "avg_pollution",
                "avg_population",
                "mmr[unit]",
                "mmr[build]",
                "revenue[converted]",
                "raws %",
                "manu %",
                "commerce %",
                "optimal %"
        ));

        for (ResourceType type : ResourceType.values) {
            if (type == ResourceType.CREDITS) {
                continue;
            }
            header.add(type.name());
        }

        return header;
    }

    private List<String> buildRevenueRow(
            DBNation nation,
            int rowSize,
            ValueStore<DBNation> cacheStore,
            IMessageIO io,
            CompletableFuture<IMessageBuilder> progressMessage,
            AtomicLong lastProgressUpdate
    ) {
        updateProgressIfNeeded(nation, io, progressMessage, lastProgressUpdate);

        double[] revenue = nation.getRevenue(cacheStore);
        Map<Integer, DBCity> cityMap = nation._getCitiesV3();

        CityAverages averages = calculateCityAverages(nation, cityMap);
        RevenueBreakdown revenueBreakdown = calculateRevenueBreakdown(revenue);
        String optimalPercent = calculateBatchOptimalPercent(nation, cityMap);

        List<String> row = new ArrayList<>(Collections.nCopies(rowSize, ""));

        row.set(0, MarkupUtil.sheetUrl(nation.getNation(), PW.getUrl(nation.getNation_id(), false)));
        row.set(1, String.valueOf(nation.getTax_id()));
        row.set(2, String.valueOf(nation.getCities()));
        row.set(3, MathMan.format(nation.getAvg_infra()));
        row.set(4, MathMan.format(nation.getAvgLand()));
        row.set(5, MathMan.format(nation.getAvgBuildings()));
        row.set(6, MathMan.format(averages.disease));
        row.set(7, MathMan.format(averages.crime));
        row.set(8, MathMan.format(averages.pollution));
        row.set(9, MathMan.format(averages.population));
        row.set(10, asSheetText(nation.getMMR()));
        row.set(11, asSheetText(nation.getMMRBuildingStr()));
        row.set(12, MathMan.format(revenueBreakdown.convertedTotal));
        row.set(13, formatPercent(revenueBreakdown.rawConverted, revenueBreakdown.convertedTotal));
        row.set(14, formatPercent(revenueBreakdown.manufacturedConverted, revenueBreakdown.convertedTotal));
        row.set(15, formatPercent(revenueBreakdown.commerceConverted, revenueBreakdown.convertedTotal));
        row.set(16, optimalPercent);

        int column = 17;
        for (ResourceType type : ResourceType.values) {
            if (type == ResourceType.CREDITS) {
                continue;
            }
            row.set(column++, MathMan.format(revenue[type.ordinal()]));
        }

        return row;
    }

    private void updateProgressIfNeeded(
            DBNation nation,
            IMessageIO io,
            CompletableFuture<IMessageBuilder> progressMessage,
            AtomicLong lastProgressUpdate
    ) {
        long now = System.currentTimeMillis();
        long last = lastProgressUpdate.get();

        if (now - last < 10_000L) {
            return;
        }

        if (lastProgressUpdate.compareAndSet(last, now)) {
            io.updateOptionally(progressMessage, "Updating build for " + nation.getMarkdownUrl());
        }
    }

    private CityAverages calculateCityAverages(DBNation nation, Map<Integer, DBCity> cityMap) {
        if (cityMap.isEmpty()) {
            return new CityAverages(0, 0, 0, 0);
        }

        double disease = 0;
        double crime = 0;
        double pollution = 0;
        double population = 0;

        for (DBCity city : cityMap.values()) {
            disease += city.getDisease();
            crime += city.getCrime();
            pollution += city.getPollution();
            population += city.getPopulation();
        }

        int cityCount = cityMap.size();
        return new CityAverages(
                disease / cityCount,
                crime / cityCount,
                pollution / cityCount,
                population / cityCount
        );
    }

    private RevenueBreakdown calculateRevenueBreakdown(double[] revenue) {
        double convertedTotal = ResourceType.convertedTotal(revenue);
        double rawConverted = 0;
        double manufacturedConverted = 0;

        for (ResourceType type : ResourceType.values) {
            if (type.isManufactured()) {
                manufacturedConverted += ResourceType.convertedTotal(type, revenue[type.ordinal()]);
            }
            if (type.isRaw()) {
                rawConverted += ResourceType.convertedTotal(type, revenue[type.ordinal()]);
            }
        }

        // Preserved from original behavior: revenue[0] is the commerce/money bucket in this codebase.
        double commerceConverted = revenue[0];

        return new RevenueBreakdown(convertedTotal, rawConverted, manufacturedConverted, commerceConverted);
    }

    /**
     * Replaces old single-city optimalBuild(...) logic with batch optimization for all cities in the nation.
     *
     * "optimal %" now means:
     *   current total city profit / batch-heuristic best total city profit
     *
     * Placeholders:
     * - the exact valueFunction type may differ in your branch
     * - goal and donors are set as placeholders because their concrete types were not provided
     */
    private String calculateBatchOptimalPercent(DBNation nation, Map<Integer, DBCity> cityMap) {
        try {
            List<DBCity> currentCities = new ObjectArrayList<>(cityMap.values());

            BatchEntry[] batch = currentCities.stream()
                    .map(city -> new BatchEntry(
                            city,
                            nation.getContinent(),
                            nation.getCities(),
                            nation.getProjectBitMask(),
                            nation.getRads(),
                            nation.getGrossModifier()
                    ))
                    .toArray(BatchEntry[]::new);

            ToDoubleFunction<INationCity> valueFunction = INationCity::getRevenueConverted;

            double currentProfit = currentCities.stream()
                    .mapToDouble(DBCity::getRevenueConverted)
                    .sum();

            INationCity[] optimalCities = findBestBatch(batch, valueFunction);

            double optimalProfit = 0d;
            for (INationCity optimalCity : optimalCities) {
                optimalProfit += optimalCity.getRevenueConverted();
            }

            if (optimalProfit <= 0d) {
                return MathMan.format(100d);
            }

            double ratio = Math.min(1d, currentProfit / optimalProfit);
            return MathMan.format(100d * ratio);

        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
    }

    /**
     * Wrapper around CityFallbackHeuristic.findBestBatch(...) so placeholders stay isolated.
     *
     * Replace the placeholder nulls with the actual values your implementation expects.
     */
    private INationCity[] findBestBatch(
            BatchEntry[] batch,
            ToDoubleFunction<INationCity> valueFunction
    ) {
        return CityFallbackHeuristic.findBestBatch(
                batch,
                valueFunction,
                null,
                null,
                Locutus.imp().getNationDB().getCities()
        );
    }

    private String formatPercent(double value, double total) {
        if (total == 0d) {
            return MathMan.format(0d);
        }
        return MathMan.format(100d * value / total);
    }

    private String asSheetText(String value) {
        return "=\"" + value + "\"";
    }

    private String appendFooter(String message, List<String> footer) {
        if (footer == null || footer.isEmpty()) {
            return message;
        }
        return message + "\n" + String.join("\n", footer);
    }

    private static final class FilterResult {
        private final Set<DBNation> nations;
        private final List<String> footer;

        private FilterResult(Set<DBNation> nations, List<String> footer) {
            this.nations = nations;
            this.footer = footer;
        }
    }

    private static final class CityAverages {
        private final double disease;
        private final double crime;
        private final double pollution;
        private final double population;

        private CityAverages(double disease, double crime, double pollution, double population) {
            this.disease = disease;
            this.crime = crime;
            this.pollution = pollution;
            this.population = population;
        }
    }

    private static final class RevenueBreakdown {
        private final double convertedTotal;
        private final double rawConverted;
        private final double manufacturedConverted;
        private final double commerceConverted;

        private RevenueBreakdown(double convertedTotal, double rawConverted, double manufacturedConverted, double commerceConverted) {
            this.convertedTotal = convertedTotal;
            this.rawConverted = rawConverted;
            this.manufacturedConverted = manufacturedConverted;
            this.commerceConverted = commerceConverted;
        }
    }
}
