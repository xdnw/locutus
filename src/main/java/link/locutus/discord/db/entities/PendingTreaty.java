package link.locutus.discord.db.entities;

import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.apiv1.enums.TreatyType;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.text.ParseException;
import java.util.Map;

public class PendingTreaty extends Treaty {
    public final int treatyId;
    public final String remaining;
    public final TreatyStatus status;

    public PendingTreaty(int from, Map<String, Element> data) throws ParseException {
        super(from, parseName(data.get("Alliance Name")), parseType(data.get("Treaty Type")), parseDate(data.get("Date")));

        Elements buttons = data.get("").getElementsByTag("button");

        remaining = data.get("Time Remaining").text();

        if (buttons.size() == 0) {
            treatyId = 0;
        } else {
            treatyId = Integer.parseInt(buttons.get(0).attr("value"));
        }
        this.status = TreatyStatus.valueOf(data.get("Status").text().toUpperCase().replaceAll(" ", "_"));
    }

    private static long parseDate(Element date) throws ParseException {
        return TimeUtil.F_YYYY_MM_DD.parse(date.text()).getTime();
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
        return "PendingTreaty{" +
                "treatyId=" + treatyId +
                ", remaining=" + remaining +
                ", status=" + status +
                ", from=" + from +
                ", to=" + to +
                ", type=" + type +
                ", date=" + date +
                '}';
    }
}
