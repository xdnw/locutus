package link.locutus.discord.commands.manager.v2.impl.pw.ranking;

import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DBTrade;
import link.locutus.discord.util.trade.TradeManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.IntUnaryOperator;

public final class TradeRankingService {
    private TradeRankingService() {
    }

    public record Request(
            ResourceType resource,
            long cutoffMs,
            TradeFlowDirection direction,
            boolean groupByAlliance,
            boolean includeOutsideMarketPrices,
            boolean absoluteTransfersOnly,
            Set<Integer> nationIds
    ) {
        public Request {
            resource = Objects.requireNonNull(resource, "resource");
            direction = Objects.requireNonNull(direction, "direction");
            nationIds = nationIds == null ? Set.of() : Set.copyOf(nationIds);
            if (resource == ResourceType.MONEY || resource == ResourceType.CREDITS) {
                throw new IllegalArgumentException("Invalid resource");
            }
            if (cutoffMs < 0L) {
                throw new IllegalArgumentException("cutoffMs cannot be negative");
            }
        }
    }

    public static RankingResult ranking(TradeManager manager, Collection<DBTrade> offers, Request request) {
        Objects.requireNonNull(manager, "manager");
        Objects.requireNonNull(request, "request");
        return ranking(offers, request, TradeRankingService::allianceIdForNation,
                (ppu, resource) -> manager.isTradeOutsideNormPrice(ppu, resource));
    }

    public static RankingResult ranking(TradeManager manager, link.locutus.discord.db.TradeDB db, Request request) {
        Objects.requireNonNull(db, "db");
        Collection<DBTrade> offers = request.nationIds().isEmpty()
                ? db.getTrades(request.resource(), request.cutoffMs(), Long.MAX_VALUE)
                : db.getTrades(request.nationIds(), request.cutoffMs());
        return ranking(manager, offers, request);
    }

    static RankingResult ranking(
            Collection<DBTrade> offers,
            Request request,
            IntUnaryOperator allianceResolver,
            BiPredicate<Integer, ResourceType> outsideMarketPrice
    ) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(allianceResolver, "allianceResolver");
        Objects.requireNonNull(outsideMarketPrice, "outsideMarketPrice");

        List<DBTrade> filteredOffers = new ArrayList<>();
        if (offers != null) {
            for (DBTrade offer : offers) {
                if (offer == null || offer.getResource() != request.resource() || offer.getDate() < request.cutoffMs()) {
                    continue;
                }
                if (!request.nationIds().isEmpty()
                        && !request.nationIds().contains(offer.getSeller())
                        && !request.nationIds().contains(offer.getBuyer())) {
                    continue;
                }
                if (!request.includeOutsideMarketPrices() && outsideMarketPrice.test(offer.getPpu(), offer.getResource())) {
                    continue;
                }
                filteredOffers.add(offer);
            }
        }

        Int2DoubleOpenHashMap signedAmounts = new Int2DoubleOpenHashMap();
        Int2DoubleOpenHashMap tradedValue = new Int2DoubleOpenHashMap();
        Int2DoubleOpenHashMap tradedVolume = new Int2DoubleOpenHashMap();
        long asOfMs = 0L;

        boolean includeSender = !request.absoluteTransfersOnly() || request.direction() == TradeFlowDirection.SOLD;
        boolean includeReceiver = !request.absoluteTransfersOnly() || request.direction() == TradeFlowDirection.BOUGHT;

        for (DBTrade offer : filteredOffers) {
            int senderId = offer.isBuy() ? offer.getBuyer() : offer.getSeller();
            int receiverId = offer.isBuy() ? offer.getSeller() : offer.getBuyer();
            if (request.groupByAlliance()) {
                senderId = allianceResolver.applyAsInt(senderId);
                receiverId = allianceResolver.applyAsInt(receiverId);
            }
            double quantity = offer.getQuantity();
            double total = offer.getTotal();

            if (includeSender && senderId != 0) {
                signedAmounts.addTo(senderId, -quantity);
                tradedValue.addTo(senderId, total);
                tradedVolume.addTo(senderId, quantity);
            }
            if (includeReceiver && receiverId != 0) {
                signedAmounts.addTo(receiverId, quantity);
                tradedValue.addTo(receiverId, total);
                tradedVolume.addTo(receiverId, quantity);
            }
            asOfMs = Math.max(asOfMs, offer.getDate());
        }

        Int2DoubleOpenHashMap amounts = new Int2DoubleOpenHashMap();
        Int2DoubleOpenHashMap ppuByEntity = new Int2DoubleOpenHashMap();
        for (int entityId : new LinkedHashSet<>(signedAmounts.keySet())) {
            double signedAmount = signedAmounts.get(entityId);
            if (signedAmount == 0d || Math.signum(signedAmount) != request.direction().sign()) {
                continue;
            }
            double absoluteAmount = Math.abs(signedAmount);
            if (absoluteAmount == 0d) {
                continue;
            }
            amounts.put(entityId, absoluteAmount);

            double volume = tradedVolume.get(entityId);
            double averagePpu = volume == 0d ? 0d : tradedValue.get(entityId) / volume;
            ppuByEntity.put(entityId, averagePpu);
        }

        RankingEntityType entityType = request.groupByAlliance() ? RankingEntityType.ALLIANCE : RankingEntityType.NATION;
        return RankingBuilders.multiMetricRanking(
                RankingKind.TRADE_FLOW,
                entityType,
                List.of(RankingBuilders.multiMetricSection(
                        RankingSectionKind.forEntityType(entityType),
                        RankingSortDirection.DESC,
                        List.of(
                                RankingBuilders.metricColumn(RankingValueKind.AMOUNT, RankingValueFormat.COUNT, amounts),
                                RankingBuilders.metricColumn(RankingValueKind.PRICE_PER_UNIT, RankingValueFormat.MONEY, ppuByEntity)
                        )
                )),
                null,
                asOfMs == 0L ? System.currentTimeMillis() : asOfMs
        );
    }

    private static int allianceIdForNation(int nationId) {
        DBNation nation = DBNation.getById(nationId);
        return nation == null ? 0 : nation.getAlliance_id();
    }
}
