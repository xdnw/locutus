package link.locutus.discord.util.task;

import link.locutus.discord.config.Settings;
import link.locutus.discord.pnw.DBNation;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.offshore.Auth;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

public class EditAllianceTask implements Callable<String> {
    private final Consumer<Map<String, String>> modifier;
    private final int allianceId;
    private final Auth auth;

    public EditAllianceTask(DBNation account, Consumer<Map<String, String>> modifier) {
        this.allianceId = account.getAlliance_id();
        this.auth = account.getAuth(null);
        this.modifier = modifier;
    }

    public EditAllianceTask(int allianceId, Auth auth, Consumer<Map<String, String>> modifier) {
        this.allianceId = allianceId;
        this.auth = auth;
        this.modifier = modifier;
    }

    @Override
    public synchronized String call() throws Exception {
        return PnwUtil.withLogin(new Callable<String>() {
            @Override
            public String call() throws Exception {
                String result = auth.readStringFromURL("" + Settings.INSTANCE.PNW_URL() + "/alliance/edit/id=" + allianceId, Collections.emptyMap());
                Document dom = Jsoup.parse(result);

                String flag = dom.select("select[name=flag]").select("option:matches(Current)").attr("value");
                String flagUpload = dom.select("input[name=flagUpload]").attr("value");
                boolean acceptmem = dom.select("input[name=acceptmem]").hasAttr("checked");
                String color = dom.select("select[name=color] > option[selected]").attr("value");
                String acr = dom.select("input[name=acr]").attr("value");
                String recruit = dom.select("textarea[name=recruit]").text();
                String desc = dom.select("textarea[name=desc]").text();
                String heading = dom.select("textarea[name=heading]").text();
                String bankview = dom.select("select[name=bankview] > option[selected]").attr("value");
                String bankaccess = dom.select("select[name=bankaccess] > option[selected]").attr("value");
                String forumlink = dom.select("input[name=forumlink]").attr("value");
                String irc = dom.select("input[name=irc]").attr("value");
                String leadertitle = dom.select("input[name=leadertitle]").attr("value");
                String heirtitle = dom.select("input[name=heirtitle]").attr("value");
                String admintitle = dom.select("input[name=admintitle]").attr("value");
                String membertitle = dom.select("input[name=membertitle]").attr("value");

                String wmsg = dom.select("textarea[name=wmsg]").text();

                Elements anthemDom = dom.select("input[name=anthem]");
                Elements bgurlDom = dom.select("input[name=bgurl]");

                String token = dom.select("input[name=token]").attr("value");

                Map<String, String> post = new HashMap<>();
                post.put("flag", flag);
                post.put("flagUpload", flagUpload);
                if (acceptmem) post.put("acceptmem", "1");
                post.put("color", color);
                post.put("acr", acr);
                post.put("recruit", recruit);
                post.put("desc", desc);
                post.put("heading", heading);
                post.put("bankview", bankview);
                post.put("bankaccess", bankaccess);
                post.put("forumlink", forumlink);
                post.put("irc", irc);
                post.put("leadertitle", leadertitle);
                post.put("heirtitle", heirtitle);
                post.put("admintitle", admintitle);
                post.put("membertitle", membertitle);
                post.put("wmsg", wmsg);
                post.put("token", token);
                post.put("submit", "Save Changes");

                if (anthemDom != null && !anthemDom.isEmpty()) post.put("anthem", anthemDom.attr("value"));
                if (bgurlDom != null && !bgurlDom.isEmpty()) post.put("bgurl", bgurlDom.attr("value"));

                HashMap<String, String> copy = new HashMap<>(post);
                modifier.accept(post);
                if (copy.equals(post)) {
                    return "";
                }

                StringBuilder response = new StringBuilder();

                result = auth.readStringFromURL("" + Settings.INSTANCE.PNW_URL() + "/alliance/edit/id=" + allianceId, post);
                dom = Jsoup.parse(result);
                for (Element element : dom.getElementsByClass("alert")) {
                    response.append('\n').append(element.text());
                }

                copy = new HashMap<>(post);
                copy.remove(token);

                response.append('\n').append("```" + StringMan.getString(copy) + "```");
                return response.toString();
            }
        }, auth);
    }
}
