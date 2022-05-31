package com.boydti.discord.util.task.balance;

import com.boydti.discord.config.Settings;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.util.discord.DiscordUtil;
import com.boydti.discord.util.FileUtil;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.concurrent.Callable;

public class GetDateTask implements Callable<Map<Integer, DBNation>> {
    private final String url;
    private final Map<Integer, DBNation> nations;
    private final LinkedHashSet<DBNation> unset;
    private final boolean force;

    public GetDateTask(Map<Integer, DBNation> nations, boolean force) {
        this(nations, "" + Settings.INSTANCE.PNW_URL() + "/index.php?id=15&od=ASC&maximum=%s&minimum=%s&vmode=true", force);
    }

    public GetDateTask(Map<Integer, DBNation> nations, String url, boolean force) {
        this.nations = nations;
        this.unset = new LinkedHashSet<>();
        for (Map.Entry<Integer, DBNation> entry : nations.entrySet()) {
            DBNation nation = entry.getValue();
            if (nation.getDate() == null && nation.getVm_turns() == 0) {
                unset.add(nation);
            }
        }
        this.url = url;
        this.force = force;
    }

    @Override
    public Map<Integer, DBNation> call() throws IOException, ParseException {
        if (unset.isEmpty() && !force) return nations;

        SimpleDateFormat format = new SimpleDateFormat("MM/dd/yyyy");

        // find minDate for a nation
        long maxDate = 0;
        for (Map.Entry<Integer, DBNation> entry : nations.entrySet()) {
            DBNation other = entry.getValue();
            if (other.getDate() != null) {
                maxDate = Math.max(maxDate, other.getDate());
            }
        }

        int start = 0;
        // find nations below that date
        if (!force && maxDate != 0) {
            long maxDay = (maxDate / 65536) * 65536;
            for (Map.Entry<Integer, DBNation> entry : nations.entrySet()) {
                DBNation other = entry.getValue();
                if (other.getDate() != null && (other.getDate() / 65536) < maxDay) {
                    start++;
                }
            }
            start = Math.max(0, start - 5);
        }

        long lastDays = 0;
        long dayIndex = 0;

        int pageSize = 50;

        for (int j = start; j < nations.size() + 1; j += pageSize) {
            String fetchUrl = String.format(url, j + pageSize, j);

            String html = FileUtil.readStringFromURL(fetchUrl);

            Document dom = Jsoup.parse(html);
            Element table = dom.getElementsByClass("nationtable").get(0);
            Elements rows = table.getElementsByTag("tr");
            for (int i = 1; i < rows.size(); i++) {
                Element row = rows.get(i);
                Elements columns = row.getElementsByTag("td");
                String nationUrl = columns.get(1).getElementsByTag("a").get(0).attr("href");
                Integer nationId = DiscordUtil.parseNationId(nationUrl);

                DBNation nation = nations.get(nationId);
                if (nation == null) {
                    continue;
                }

                String dateStr = columns.get(2).text().trim();
                ZonedDateTime date = ZonedDateTime.ofInstant(format.parse(dateStr).toInstant(), ZoneOffset.UTC);
                long days = ChronoUnit.DAYS.between(Instant.EPOCH, date);
                if (days != lastDays) {
                    lastDays = days;
                    dayIndex = 0;
                }

                long dateIndex = (days * 65536) + dayIndex;

                boolean wasNull = (nation.getDate() == null);
                nation.setDate(dateIndex);

                if (wasNull) {
                    unset.remove(nation);
                    if (unset.isEmpty()) {
                        break;
                    }
                }

                dayIndex += 1;
            }
        }

        return nations;
    }
}
