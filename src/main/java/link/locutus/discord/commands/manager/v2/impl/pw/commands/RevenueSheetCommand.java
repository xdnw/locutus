package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import link.locutus.discord.commands.manager.v2.command.CommandMessagePriority;
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
import java.util.function.ToDoubleFunction;

public class RevenueSheetCommand {
    @Command(desc = "Get a sheet of nations and their revenue (compared to batch-heuristic city builds)", viewable = true)
    @RolePermission(value = Roles.MEMBER, onlyInGuildAlliance = true)
        public String revenueSheet(
            ValueStore store,
            @Me IMessageIO io,
            @Me @Default GuildDB db,
            NationList nations,
            @Switch("s") SpreadSheet sheet,
            @Switch("i") boolean include_untaxable,
            @Switch("t") @Timestamp Long snapshotTime
    ) throws GeneralSecurityException, IOException, ExecutionException, InterruptedException {

        long start = System.currentTimeMillis();
        if (nations.getNations().size() > 100 && (db == null || !db.isValidAlliance())) {
            throw new IllegalArgumentException(
                    "Too many nations: " + nations.getNations().size() + " (max: 100 outside of an alliance guild)"
            );
        }
        System.out.println("[RevenueSheet] Starting revenue command: " + ((-start) + (start = System.currentTimeMillis())) + "ms");

        Set<DBNation> nationSet = new LinkedHashSet<>(PW.getNationsSnapshot(
                nations.getNations(),
                nations.getFilter(),
                snapshotTime,
                db == null ? null : db.getGuild()
        ));

        System.out.println("[RevenueSheet] Create nations: " + ((-start) + (start = System.currentTimeMillis())) + "ms");

        if (sheet == null) {
            sheet = SpreadSheet.create(db, SheetKey.REVENUE_SHEET);
        }

        System.out.println("[RevenueSheet] Create sheet: " + ((-start) + (start = System.currentTimeMillis())) + "ms");

        List<String> footer = new ArrayList<>();
        int before = nationSet.size();

        if (db != null) {
            Set<Integer> allianceIds = db.getAllianceIds(false);
            nationSet.removeIf(n ->
                    n.getPosition() <= Rank.APPLICANT.id ||
                            (!allianceIds.isEmpty() && !allianceIds.contains(n.getAlliance_id()))
            );
            int removed = before - nationSet.size();
            if (removed > 0) {
                footer.add(removed + " nations were removed for not being members of the guild's alliances");
            }
            before = nationSet.size();
        }

        nationSet.removeIf(n -> n.getVm_turns() > 0);
        int removedVm = before - nationSet.size();
        if (removedVm > 0) {
            footer.add(removedVm + " nations were removed for being in vacation mode");
        }
        before = nationSet.size();

        if (!include_untaxable) {
            nationSet.removeIf(n -> !n.isTaxable());
        }
        int removedUntaxable = before - nationSet.size();
        if (removedUntaxable > 0) {
            footer.add(removedUntaxable + " nations were removed for being untaxable");
        }

        if (nationSet.isEmpty()) {
            return footer.isEmpty()
                    ? "No nations to process."
                    : "No nations to process.\n" + String.join("\n", footer);
        }

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
            if (type != ResourceType.CREDITS) {
                header.add(type.name());
            }
        }
        sheet.setHeader(header);

        CompletableFuture<IMessageBuilder> msgFuture = io.sendIfFree("Please wait...", CommandMessagePriority.PROGRESS);
        List<DBNation> nationList = new ArrayList<>(nationSet);
        ValueStore cacheStore = PlaceholderCache.createCache(store, nationSet, DBNation.class);

        ToDoubleFunction<INationCity> valueFunction = INationCity::getRevenueConverted;

        List<NationRowData> data = new ArrayList<>(nationList.size());
        List<BatchEntry> batch = new ArrayList<>();
        List<Double> currentBatchRevenue = new ArrayList<>();

        System.out.println("[RevenueSheet] Cache: " + ((-start) + (start = System.currentTimeMillis())) + "ms");

        for (DBNation nation : nationList) {
            double[] revenue = nation.getRevenue(cacheStore);
            Map<Integer, DBCity> cities = nation._getCitiesV3();

            double disease = 0;
            double crime = 0;
            double pollution = 0;
            double population = 0;
            double currentProfit = 0;

            int batchStart = batch.size();

            for (DBCity city : cities.values()) {
                double cityRevenue = valueFunction.applyAsDouble(city);

                disease += city.getDisease();
                crime += city.getCrime();
                pollution += city.getPollution();
                population += city.getPopulation();
                currentProfit += cityRevenue;

                batch.add(new BatchEntry(
                        city,
                        nation.getContinent(),
                        nation.getCities(),
                        nation.getProjectBitMask(),
                        nation.getRads(),
                        nation.getGrossModifier()
                ));
                currentBatchRevenue.add(cityRevenue);
            }

            int cityCount = cities.size();
            data.add(new NationRowData(
                    nation,
                    revenue,
                    cityCount == 0 ? 0 : disease / cityCount,
                    cityCount == 0 ? 0 : crime / cityCount,
                    cityCount == 0 ? 0 : pollution / cityCount,
                    cityCount == 0 ? 0 : population / cityCount,
                    currentProfit,
                    batchStart,
                    cityCount
            ));
        }

        System.out.println("[RevenueSheet] Data: " + ((-start) + (start = System.currentTimeMillis())) + "ms");

        INationCity[] best = null;
        if (!batch.isEmpty()) {
            io.updateOptionally(msgFuture, "Running city heuristic for " + batch.size() + " cities across " + data.size() + " nations...", CommandMessagePriority.PROGRESS);
            try {
                best = CityFallbackHeuristic.findBestBatch(
                        batch.toArray(new BatchEntry[0]),
                        valueFunction,
                        null, // TODO goal
                        null, // TODO infraLow
                        Locutus.imp().getNationDB().getCities() // TODO donors if needed
                );
            } catch (Exception e) {
                best = null;
            }
        }

        System.out.println("[RevenueSheet] Find Best: " + ((-start) + (start = System.currentTimeMillis())) + "ms");

        for (NationRowData rowData : data) {
            double optimalProfit = 0;
            int end = rowData.batchStart + rowData.batchLen;

            for (int i = rowData.batchStart; i < end; i++) {
                double current = currentBatchRevenue.get(i);
                double chosen = current;

                if (best != null && i < best.length) {
                    INationCity candidate = best[i];
                    if (candidate != null) {
                        double candidateRevenue = valueFunction.applyAsDouble(candidate);
                        if (Double.isFinite(candidateRevenue)) {
                            chosen = Math.max(current, candidateRevenue);
                        }
                    }
                }

                optimalProfit += chosen;
            }

            rowData.optimalProfit = optimalProfit;
        }

        System.out.println("[RevenueSheet] Optimal Profit: " + ((-start) + (start = System.currentTimeMillis())) + "ms");

        for (NationRowData rowData : data) {
            DBNation nation = rowData.nation;
            double[] revenue = rowData.revenue;

            double revenueConverted = ResourceType.convertedTotal(revenue);
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

            double commerceConverted = revenue[0];
            double optimalPct = rowData.optimalProfit <= 0
                    ? 100d
                    : 100d * Math.min(1d, rowData.currentProfit / rowData.optimalProfit);

            List<String> row = new ArrayList<>(Collections.nCopies(header.size(), ""));
            row.set(0, MarkupUtil.sheetUrl(nation.getNation(), PW.getUrl(nation.getNation_id(), false)));
            row.set(1, String.valueOf(nation.getTax_id()));
            row.set(2, String.valueOf(nation.getCities()));
            row.set(3, MathMan.format(nation.getAvg_infra()));
            row.set(4, MathMan.format(nation.getAvgLand()));
            row.set(5, MathMan.format(nation.getAvgBuildings()));
            row.set(6, MathMan.format(rowData.avgDisease));
            row.set(7, MathMan.format(rowData.avgCrime));
            row.set(8, MathMan.format(rowData.avgPollution));
            row.set(9, MathMan.format(rowData.avgPopulation));
            row.set(10, "=\"" + nation.getMMR() + "\"");
            row.set(11, "=\"" + nation.getMMRBuildingStr() + "\"");
            row.set(12, MathMan.format(revenueConverted));
            row.set(13, MathMan.format(revenueConverted == 0 ? 0 : 100d * rawConverted / revenueConverted));
            row.set(14, MathMan.format(revenueConverted == 0 ? 0 : 100d * manufacturedConverted / revenueConverted));
            row.set(15, MathMan.format(revenueConverted == 0 ? 0 : 100d * commerceConverted / revenueConverted));
            row.set(16, MathMan.format(optimalPct));

            int col = 17;
            for (ResourceType type : ResourceType.values) {
                if (type != ResourceType.CREDITS) {
                    row.set(col++, MathMan.format(revenue[type.ordinal()]));
                }
            }

            sheet.addRow(row);
        }

        System.out.println("[RevenueSheet] Add rows: " + ((-start) + (start = System.currentTimeMillis())) + "ms");

        sheet.updateClearCurrentTab();
        System.out.println("[RevenueSheet] clear tab: " + ((-start) + (start = System.currentTimeMillis())) + "ms");
        sheet.updateWrite();
        System.out.println("[RevenueSheet] Write Rows: " + ((-start) + (start = System.currentTimeMillis())) + "ms");

        IMessageBuilder result = sheet.attach(io.create(), "revenue");
        if (!footer.isEmpty()) {
            result.append("\n" + String.join("\n", footer));
        }
        result.send(CommandMessagePriority.RESULT);

        System.out.println("[RevenueSheet] Send msg: " + ((-start) + (start = System.currentTimeMillis())) + "ms");

        return null;
    }

    private static final class NationRowData {
        private final DBNation nation;
        private final double[] revenue;
        private final double avgDisease;
        private final double avgCrime;
        private final double avgPollution;
        private final double avgPopulation;
        private final double currentProfit;
        private final int batchStart;
        private final int batchLen;
        private double optimalProfit;

        private NationRowData(
                DBNation nation,
                double[] revenue,
                double avgDisease,
                double avgCrime,
                double avgPollution,
                double avgPopulation,
                double currentProfit,
                int batchStart,
                int batchLen
        ) {
            this.nation = nation;
            this.revenue = revenue;
            this.avgDisease = avgDisease;
            this.avgCrime = avgCrime;
            this.avgPollution = avgPollution;
            this.avgPopulation = avgPopulation;
            this.currentProfit = currentProfit;
            this.batchStart = batchStart;
            this.batchLen = batchLen;
        }
    }
}
