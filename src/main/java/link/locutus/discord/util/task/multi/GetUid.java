package link.locutus.discord.util.task.multi;

import link.locutus.discord.Locutus;
import link.locutus.discord.pnw.DBNation;
import link.locutus.discord.util.AlertUtil;
import link.locutus.discord.util.FileUtil;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.math.BigInteger;
import java.util.concurrent.Callable;

public class GetUid implements Callable<BigInteger> {
    private final DBNation nation;
    private boolean verified;
    private BigInteger uuid;

    public GetUid(DBNation nation) {
        this.nation = nation;
    }
    @Override
    public BigInteger call() throws Exception {
        String url = nation.getNationUrl();
        String html = FileUtil.readStringFromURL(url);

        Document dom = Jsoup.parse(html);
        try {
            long projBitmask = 1;

            Elements projectsRoot = dom.select("th:contains(National Projects)");
            if (!projectsRoot.isEmpty()) {
                Element root = projectsRoot.get(0).parent();
                Element sibling = root;
                while ((sibling = sibling.nextElementSibling()) != null) {
                    Elements img = sibling.select("img");
                    if (!img.isEmpty()) {
                        String projectName = img.get(0).attr("alt");
                        Project project = Projects.get(projectName);

                        if (project == null) {
                            AlertUtil.error("Invalid project", projectName);
                        } else {
                            projBitmask |= 1 << (project.ordinal() + 1);
                        }
                    }
                }


                long previous = nation.getProjectBitMask();
                if (previous != projBitmask) {
                    nation.setProjectsRaw(projBitmask);
                    Locutus.imp().getNationDB().addNation(nation);
                }
            }

            Elements uuidTd = dom.select("td:contains(Unique ID)");
            if (!uuidTd.isEmpty()) {

                String hexString = uuidTd.first().nextElementSibling().text();
                this.uuid = new BigInteger(hexString, 16);

                BigInteger invalid = new BigInteger("cb0cf3109e61373be1c18de4", 16);
                if (!invalid.equals(uuid)) {
                    Locutus.imp().getDiscordDB().addUUID(nation.getNation_id(), uuid);
                }
                this.verified = dom.select(".fa-check-circle").size() > 0;
                if (verified) {
                    Locutus.imp().getDiscordDB().addVerified(nation.getNation_id());
                }
            }

            return uuid;
        } catch (Throwable e) {
            e.printStackTrace();
            AlertUtil.error("Failed to fetch uid", PnwUtil.getAlert(dom));
        }
        return null;
    }
}
