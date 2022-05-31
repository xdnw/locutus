package com.boydti.discord.util.task.balance;

import com.boydti.discord.config.Settings;
import com.boydti.discord.db.entities.Transaction2;
import com.boydti.discord.db.entities.Transfer;
import com.boydti.discord.util.PnwUtil;
import com.boydti.discord.util.offshore.Auth;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Function;

public class GetDepositTask implements Callable<List<Transaction2>> {
    private final int allianceId;
    private final Auth auth;
    private final long cutoffMs;

    public GetDepositTask(Auth auth, int allianceId, long cutoffMs) {
        this.allianceId = allianceId;
        this.auth = auth;
        this.cutoffMs = cutoffMs;
    }

    @Override
    public synchronized List<Transaction2> call()  {
        String bankUrl = String.format("" + Settings.INSTANCE.PNW_URL() + "/alliance/id=%s&display=bank", allianceId);

        long now = System.currentTimeMillis();

        return PnwUtil.withLogin(new Callable<List<Transaction2>>() {

            List<Transaction2> transfers = new ArrayList<>();

            @Override
            public List<Transaction2> call() throws Exception {
                GetPageTask task = new GetPageTask(auth, bankUrl, -1);
                task.consume(new Function<Element, Boolean>() {
                    int total = 0;
                    @Override
                    public Boolean apply(Element element) {
                        total++;
                        Transaction2 rowTransfer = Transaction2.ofBankRecord(element);
                        if (rowTransfer.getDate() >= cutoffMs) {
                            transfers.add(rowTransfer);
                            return false;
                        }
                        return true;
                    }
                });
                return transfers;
            }
        }, auth);
    }
}
