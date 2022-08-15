package link.locutus.discord.db.entities;

import com.ptsmods.mysqlw.table.ColumnType;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.db.TradeDB;
import link.locutus.discord.util.discord.DiscordUtil;
import rocker.grant.nation;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;

public class TradeSubscription {
    private long user;
    private ResourceType resource;
    private long date;
    private boolean isBuy;
    private boolean above;
    private int ppu;
    private TradeDB.TradeAlertType type;

    public TradeSubscription(long user, ResourceType resource, long date, boolean isBuy, boolean above, int ppu, TradeDB.TradeAlertType type) {
        this.user = user;
        this.resource = resource;
        this.date = date;
        this.isBuy = isBuy;
        this.above = above;
        this.ppu = ppu;
        this.type = type;
    }

    public TradeSubscription(ResultSet rs) throws SQLException {
        this.user = rs.getLong("user");
        this.resource = ResourceType.values[rs.getInt("resource")];
        this.date = rs.getLong("date");
        this.isBuy = rs.getBoolean("isBuy");
        this.above = rs.getBoolean("above");
        this.ppu = rs.getInt("ppu");
        this.type = TradeDB.TradeAlertType.values()[rs.getInt("type")];
    }

    public long getUser() {
        return user;
    }

    public ResourceType getResource() {
        return resource;
    }

    public long getDate() {
        return date;
    }

    public boolean isBuy() {
        return isBuy;
    }

    public boolean isAbove() {
        return above;
    }

    public int getPpu() {
        return ppu;
    }

    public TradeDB.TradeAlertType getType() {
        return type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TradeSubscription that = (TradeSubscription) o;
        return user == that.user && date == that.date && isBuy == that.isBuy && above == that.above && ppu == that.ppu && resource == that.resource && type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(user, resource, date, isBuy, above, ppu, type);
    }

    public boolean applies(DBTrade previousTopBuy, DBTrade previousTopSell, DBTrade currentTopBuy, DBTrade currentTopSell) {
        if (Objects.equals(previousTopBuy, currentTopBuy) && Objects.equals(previousTopSell, currentTopSell)) return false;

        BinaryOperator<DBTrade> getTrade = (buy, sell) -> this.isBuy ? buy : sell;

        switch (type) {
            case MIXUP -> {
                if (currentTopBuy == null || currentTopSell == null) return false;

                int currentDispairty = currentTopBuy.getPpu() - currentTopSell.getPpu();
                if (currentDispairty <= 0) return false;
                return above ? currentDispairty >= ppu : currentDispairty <= ppu;
            }
            case UNDERCUT -> {
                DBTrade previous = getTrade.apply(previousTopBuy, previousTopSell);
                DBTrade current = getTrade.apply(currentTopBuy, currentTopSell);
                if (previous == null || current == null) return false;

                int previousNationId = isBuy ? previous.getBuyer() : previous.getSeller();
                int currentNationId = isBuy ? current.getBuyer() : current.getSeller();
                if (currentNationId == 0 || previousNationId == 0) return false;

                DBNation nation = DiscordUtil.getNation(user);
                if (nation == null) return false;

                return (previousNationId == nation.getNation_id() && currentNationId != nation.getNation_id());
            }
            case DISPARITY -> {
                    int previousDisparity = Integer.MAX_VALUE;
                    if (previousTopSell != null && previousTopBuy != null) {
                        previousDisparity = previousTopSell.getPpu() - previousTopBuy.getPpu();
                    }
                    int currentDispairty = Integer.MAX_VALUE;
                    if (currentTopSell != null && currentTopBuy != null) {
                        currentDispairty = currentTopSell.getPpu() - currentTopBuy.getPpu();
                    }

                boolean currentValid = currentDispairty != Integer.MAX_VALUE && (above ? currentDispairty > ppu : currentDispairty < ppu);
                boolean previousValid = previousDisparity != Integer.MAX_VALUE && (above ? previousDisparity > ppu : previousDisparity < ppu);
                return (!previousValid && currentValid);
            }
            case ABSOLUTE -> {
                DBTrade previous = getTrade.apply(previousTopBuy, previousTopSell);
                DBTrade current = getTrade.apply(currentTopBuy, currentTopSell);
                boolean currentValid = current != null && (above ? current.getPpu() > ppu : current.getPpu() < ppu);
                boolean previousValid = previous != null && (above ? previous.getPpu() > ppu : previous.getPpu() < ppu);
                return currentValid && !previousValid;
            }
            case NO_OFFER -> {
                DBTrade previous = getTrade.apply(previousTopBuy, previousTopSell);
                DBTrade current = getTrade.apply(currentTopBuy, currentTopSell);
                return (current == null && previous != null);
            }
            default -> {
                throw new IllegalArgumentException("Unknown trade alert type: " + type);
            }
        }
    }
}
