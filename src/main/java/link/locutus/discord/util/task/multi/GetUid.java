package link.locutus.discord.util.task.multi;

import link.locutus.discord.Locutus;
import link.locutus.discord.Logg;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.AlertUtil;
import link.locutus.discord.util.FileUtil;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.io.PagePriority;
import link.locutus.discord.util.update.NationUpdateProcessor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
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
            NationUpdateProcessor.NationUpdate update = NationUpdateProcessor.updateNation(nation, dom);

            return update.uuid;
        } catch (Throwable e) {
            e.printStackTrace();
            AlertUtil.error("Failed to fetch uid", PW.getAlert(dom));
        }
        return null;
    }
}
