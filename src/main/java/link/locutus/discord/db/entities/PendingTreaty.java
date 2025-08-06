package link.locutus.discord.db.entities;

import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.apiv1.enums.TreatyType;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.text.ParseException;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class PendingTreaty extends Treaty {
    public final TreatyStatus status;

    public PendingTreaty(int from, Map<String, Element> data) throws ParseException {
        super(parseTreatyId(data),
                parseDate(data.get("Date")),
                parseType(data.get("Treaty Type")),
                from,
                parseName(data.get("Alliance Name")),
                TimeUtil.getTurn() + parseTurns(data)
                );

        this.status = TreatyStatus.valueOf(data.get("Status").text().toUpperCase().replaceAll(" ", "_"));
    }

    public static long parseTurns(Map<String, Element> data) {
        String timeStr = data.get("Time Remaining").text().replace(" ", "");
        long diff = TimeUtil.timeToSec(timeStr);
        long numTurns = (TimeUnit.SECONDS.toHours(diff) / 2);
        return numTurns;
    }

    private static int parseTreatyId(Map<String, Element> data) {
        Elements buttons = data.get("").getElementsByTag("button");
        if (buttons.size() == 0) return 0;
        return Integer.parseInt(buttons.get(0).attr("value"));
    }

    private static long parseDate(Element date) throws ParseException {
        return TimeUtil.parseDate(TimeUtil.F_YYYY_MM_DD, date.text());
    }

    private static TreatyType parseType(Element type) {
        return TreatyType.parse(type.text().toUpperCase().replaceAll(" ", "_"));
    }

    private static int parseName(Element elem) {
        return Integer.parseInt(elem.child(0).attr("href").split("=")[1]);
    }

    public enum TreatyStatus {
        PENDING,
        ACTIVE,
        THEY_CANCELED,
        WE_CANCELED,
        EXPIRED
    }

    @Override
    public String toString() {
        return "Treaty{" +
                "id=" + getId() +
                "status=" + status +
                ", date=" + new Date(getDate()) +
                ", type=" + getType() +
                ", from=" + getFromId() +
                ", to=" + getTo() +
                ", turn_ends=" + getTurnEnds() +
                '}';
    }
}
