package link.locutus.discord.util.task.multi;

import link.locutus.discord.Locutus;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.AlertUtil;
import link.locutus.discord.util.FileUtil;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.util.io.PagePriority;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.math.BigInteger;
import java.util.concurrent.Callable;

public class GetUid implements Callable<BigInteger> {
    private final DBNation nation;
    private final boolean priority;
    private boolean verified;
    private BigInteger uuid;

    public GetUid(DBNation nation, boolean priority) {
        this.nation = nation;
        this.priority = priority;
    }
    @Override
    public BigInteger call() throws IOException {
        String url = nation.getUrl();
        PagePriority pp = (priority ? PagePriority.NATION_UID_MANUAL : PagePriority.NATION_UID_AUTO);
        String html = FileUtil.readStringFromURL(pp.ordinal(), pp.getAllowedBufferingMs(), pp.getAllowableDelayMs(), url);

        Document dom = Jsoup.parse(html);
        try {
            Elements uuidTd = dom.select("td:contains(Unique ID)");
            if (!uuidTd.isEmpty()) {
                String hexString = uuidTd.first().nextElementSibling().text();
                this.uuid = new BigInteger(hexString, 16);
                this.verified = dom.select(".fa-check-circle").size() > 0;
                if (verified) {
                    Locutus.imp().getDiscordDB().addVerified(nation.getNation_id());
                }
                Locutus.imp().getDiscordDB().addUUID(nation.getNation_id(), uuid);
                System.out.println("Fetched uid for " + nation.getNation_id() + " (" + uuid.toString(16) + ")");
            } else {
                System.out.println("Failed to fetch uid for " + nation.getNation_id() + " (not found)");
            }
            return uuid;
        } catch (Throwable e) {
            e.printStackTrace();
            AlertUtil.error("Failed to fetch uid", PnwUtil.getAlert(dom));
        }
        return null;
    }
}
