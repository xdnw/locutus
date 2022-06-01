package link.locutus.discord.commands.stock;

import link.locutus.discord.Locutus;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;

import java.sql.ResultSet;
import java.sql.SQLException;

public class StockTrade {
    public int tradeId;
    public int company;
    public int buyer;
    public int seller;
    public boolean is_buying;
    public int resource;
    public long amount;
    public long price;
    public long date_offered;
    public long date_bought;

    public StockTrade(int company, int trader, boolean is_buying, int resource, long amount, long price) {
        this.company = company;
        this.buyer = is_buying ? trader : 0;
        this.seller = is_buying ? 0 : trader;
        this.is_buying = is_buying;
        this.resource = resource;
        this.amount = amount;
        this.price = price;
        this.date_offered = System.currentTimeMillis();
    }

    public StockTrade(ResultSet rs) throws SQLException {
        tradeId = rs.getInt("id");
        company = rs.getInt("company");
        buyer = rs.getInt("buyer");
        seller = rs.getInt("seller");
        is_buying = rs.getBoolean("is_buying");
        resource = rs.getInt("resource");
        price = rs.getLong("price");
        price = rs.getLong("amount");
        date_offered = rs.getLong("date_offered");
        date_bought = rs.getLong("date_bought");
    }

    public StockTrade(StockTrade trade) {
        this.tradeId = trade.tradeId;
        this.company = trade.company;
        this.buyer = trade.buyer;
        this.seller = trade.seller;
        this.is_buying = trade.is_buying;
        this.resource = trade.resource;
        this.amount = trade.amount;
        this.price = trade.price;
        this.date_offered = trade.date_offered;
        this.date_bought = trade.date_bought;
    }

    @Override
    public String toString() {
        StringBuilder desc = new StringBuilder();
        if (seller != 0) desc.append(PnwUtil.getName(seller, false));
        else desc.append("SELLER WANTED");
        desc.append(" - ");
        if (buyer != 0) desc.append(PnwUtil.getName(buyer, false));
        else desc.append("BUYER WANTED");
        desc.append(": ");
        desc.append(MathMan.format(amount / 100d) + "x ");
        desc.append(Locutus.imp().getStockDB().getExchange(company).symbol);
        desc.append(" for " + MathMan.format(price / 100d));
        return desc.toString();
    }
}
