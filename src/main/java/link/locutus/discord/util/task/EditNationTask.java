package link.locutus.discord.util.task;

import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.io.PagePriority;
import link.locutus.discord.util.offshore.Auth;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

public class EditNationTask implements Callable<String> {
    private final Consumer<Map<String, String>> modifier;
    private final Auth auth;

    public EditNationTask(DBNation account, Consumer<Map<String, String>> modifier) {
        this.auth = account.getAuth(null);
        this.modifier = modifier;
    }

    public EditNationTask(Auth auth, Consumer<Map<String, String>> modifier) {
        this.auth = auth;
        this.modifier = modifier;
    }

    @Override
    public synchronized String call() throws Exception {
        return PnwUtil.withLogin(new Callable<String>() {
            @Override
            public String call() throws Exception {
                String result = auth.readStringFromURL(PagePriority.NATION_EDIT, "" + Settings.INSTANCE.PNW_URL() + "/nation/edit", Collections.emptyMap());
                Document dom = Jsoup.parse(result);

                String nattitle = dom.select("input[name=nattitle]").attr("value");
                String title = dom.select("input[name=title]").attr("value");
                String flag = dom.select("select[name=flag]").select("option:matches(Current)").attr("value");
                String flagUpload = dom.select("input[name=flagUpload]").attr("value");
                String motto = dom.select("input[name=motto]").attr("value");

                String map_zoom = dom.select("select[name=map_zoom] > option[selected]").attr("value");
                String map_height = dom.select("select[name=map_height] > option[selected]").attr("value");
                String map_fill_color = dom.select("select[name=map_fill_color] > option[selected]").attr("value");
                String map_fill_color_custom = dom.select("input[name=map_fill_color_custom]").attr("value");

                String map_stroke_color = dom.select("select[name=map_stroke_color] > option[selected]").attr("value");
                String map_stroke_color_custom = dom.select("input[name=map_stroke_color_custom]").attr("value");
                String map_opacity = dom.select("select[name=map_opacity] > option[selected]").attr("value");

                String govtype = dom.select("select[name=govtype] > option[selected]").attr("value");
                String dompolicy = dom.select("select[name=dompolicy] > option[selected]").attr("value");
                String warpolicy = dom.select("select[name=warpolicy] > option[selected]").attr("value");

                String color = dom.select("select[name=color] > option[selected]").attr("value");

                String religion_name = dom.select("input[name=religion_name]").attr("value");
                String religion = dom.select("select[name=religion] > option[selected]").attr("value");
                String hd_custom_currency = dom.select("input[name=hd_custom_currency]").attr("value");
                String currency = dom.select("select[name=currency] > option[selected]").attr("value");
                String custom_currency = dom.select("input[name=custom_currency]").attr("value");
//                String currency_image = dom.select("input[name=custom_currency]").

                String token = dom.select("input[name=token]").attr("value");

                Map<String, String> post = new HashMap<>();



                post.put("token", token);
                post.put("submiteditform", "Save Changes");

                HashMap<String, String> copy = new HashMap<>(post);
                modifier.accept(post);
                if (copy.equals(post)) {
                    return "";
                }

                StringBuilder response = new StringBuilder();

                result = auth.readStringFromURL(PagePriority.TOKEN, "" + Settings.INSTANCE.PNW_URL() + "/nation/edit", post);
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
