package com.boydti.discord.commands.stock;

import java.sql.SQLException;
import java.util.Map;

public class StockTest {
    public static void main(String[] args) throws SQLException, ClassNotFoundException {
        StockDB db = new StockDB();

        for (Map.Entry<String, Exchange> e : db.getExchanges().entrySet()) {
            Exchange corp = e.getValue();
            System.out.println("Corp: " + corp.symbol + " | " + corp.id + " | " + corp.shares + " | " + corp.owner);
        }

        db.createShareholder(6, 0);
        db.createShareholder(189573, 0);
        System.out.println("Set " + db.setShares(6, 0, 5));
        System.out.println("Set " + db.setShares(189573, 0, 0));
        System.out.println(db.getSharesByNation(6, 0) + " | " + db.getSharesByNation(189573, 0));
        System.out.println("Transfer");

        db.transferShare(0, 6, 189573, 5);
        System.out.println(db.getSharesByNation(6, 0) + " | " + db.getSharesByNation(189573, 0));



//        db.createShareholder(189573, );
//        System.out.println(db.);
    }
}
