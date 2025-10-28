package link.locutus.discord.util.task.multi;

import link.locutus.discord.apiv1.entities.PwUid;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.AlertUtil;
import link.locutus.discord.util.FileUtil;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.io.PagePriority;
import link.locutus.discord.util.update.NationUpdateProcessor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.Callable;

public class GetUid implements Callable<PwUid> {
    private final DBNation nation;
    private final boolean priority;
    private boolean verified;
    private PwUid uuid;

    public GetUid(DBNation nation, boolean priority) {
        this.nation = nation;
        this.priority = priority;
    }
    @Override
    public PwUid call() throws IOException {
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
