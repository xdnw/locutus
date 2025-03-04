package link.locutus.discord.util.offshore;

import link.locutus.discord.Locutus;
import link.locutus.discord.Logg;
import link.locutus.discord.apiv1.core.ApiKeyPool;
import link.locutus.discord.apiv3.enums.AlliancePermission;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.*;
import link.locutus.discord.network.IProxy;
import link.locutus.discord.network.PassthroughProxy;
import link.locutus.discord.pnw.NationOrAlliance;
import link.locutus.discord.pnw.json.CityBuild;
import link.locutus.discord.util.FileUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.TreatyType;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.util.io.PagePriority;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.CookieManager;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.ReentrantLock;

import static java.util.Collections.emptyMap;

public class Auth {
    private final String password;
    private final String username;
    private String apiKey;
    private boolean valid;
    private CookieManager msCookieManager = new CookieManager();
    private IProxy proxy;

    private final ReentrantLock lock = new ReentrantLock();
    private final int nationId;

    public Auth(int nationId, String username, String password) {
        this.username = username;
        this.password = password;
        this.nationId = nationId;
        this.valid = true;
        this.proxy = PassthroughProxy.INSTANCE;
    }

    public void setProxy(IProxy proxy) {
        this.proxy = proxy;
    }

    private boolean loggedIn = false;

    public String getToken(PagePriority priority, String url) throws IOException {
        String html = readStringFromURL(priority, url, emptyMap());
        Document dom = Jsoup.parse(html);
        return dom.select("input[name=token]").attr("value");
    }

    public String readStringFromURL(PagePriority priority, String urlStr, Map<String, String> arguments) throws IOException {
        return readStringFromURL(priority, urlStr, arguments, true);
    }

    public String readStringFromURL(PagePriority priority, String urlStr, Map<String, String> arguments, boolean post) throws IOException {
        synchronized (this)
        {
            login(false);
            String result = FileUtil.get(FileUtil.readStringFromURL(priority, urlStr, arguments, post, msCookieManager, proxy, i -> {}));
            if (result.contains("<!--Logged Out-->")) {
                msCookieManager.getCookieStore().removeAll();
                login(true);
                result = FileUtil.get(FileUtil.readStringFromURL(priority, urlStr, arguments, post, msCookieManager, proxy, i -> {}));
                if (result.contains("<!--Logged Out-->")) {
                    throw new IllegalArgumentException("Failed to login to PNW");
                }
            }
            if (result.toLowerCase().contains("authenticate your request")) {
                new Exception().printStackTrace();
            }
            return result;
        }
    }

    public void login(boolean force) throws IOException {
        if (!force && loggedIn) return;
        synchronized (this)
        {
            System.out.println("Logging in to " + this.getUsername());
            Map<String, String> userPass = new HashMap<>();
            userPass.put("email", this.getUsername());
            userPass.put("password", this.getPassword());
            userPass.put("loginform", "Login");
            userPass.put("rememberme", "1");
            String url = "" + Settings.PNW_URL() + "/login/";

            String loginResult = FileUtil.get(FileUtil.readStringFromURL(PagePriority.LOGIN, url, userPass, this.getCookieManager()));
            if (!loginResult.contains("Login Successful")) {
                throw new IllegalArgumentException("Error: " + PW.parseDom(Jsoup.parse(loginResult), "columnheader"));
            }
            loggedIn = true;
        }
    }

    public String logout() throws IOException {
        String logout = FileUtil.readStringFromURL(PagePriority.LOGOUT, "" + Settings.PNW_URL() + "/logout/");
        Document dom = Jsoup.parse(logout);
        clearCookies();
        return PW.getAlert(dom);
    }

    public void clearCookies() {
        msCookieManager.getCookieStore().removeAll();
    }

    public CookieManager getCookieManager() {
        return msCookieManager;
    }

    public int getNationId() {
        return nationId;
    }

    public DBNation getNation() {
        return Locutus.imp().getNationDB().getNationById(nationId);
    }

    public int getAllianceId() {
        DBNation n = getNation();
        return n == null ? 0 : n.getAlliance_id();
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public boolean isValid() {
        return valid;
    }

    public ApiKeyPool.ApiKey getApiKey() {
        ApiKeyPool.ApiKey key = Locutus.imp().getDiscordDB().getApiKey(nationId);
        if (key != null) return key;
        return fetchApiKey();
    }

    public synchronized ApiKeyPool.ApiKey fetchApiKey() {
        if (apiKey == null || apiKey.isEmpty()) {
            String url = "" + Settings.PNW_URL() + "/";
            apiKey = PW.withLogin(new Callable<String>() {
                @Override
                public String call() throws Exception {
                    try {
                        String html = Auth.this.readStringFromURL(PagePriority.FETCH_KEY, url + "account/", emptyMap());
                        Document dom = Jsoup.parse(html);
                        Elements tables = dom.select(".nationtable");
                        if (tables.isEmpty()) {
                            String alerts = PW.getAlert(dom);
                            if (alerts == null || alerts.isEmpty()) {
                                Logg.text("Unable to confirm login\n\n---START BODY (1)---\n\n" + html + "\n\n---END BODY---\n\n");
                            }
                            throw new IllegalArgumentException("Error: " + alerts);
                        }
                        Element apiTable = tables.get(tables.size() - 1);
                        return apiTable.select(".center").first().text();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    return null;
                }
            }, this);
        }
        if (apiKey == null || apiKey.isEmpty()) throw new IllegalArgumentException("Unable to fetch api key");
        Locutus.imp().getDiscordDB().addApiKey(getNationId(), apiKey);
        return Locutus.imp().getDiscordDB().getApiKey(nationId);
    }

    public String createDepositTrade(DBNation receiver, ResourceType resource, int num) {
        boolean isBuy;
        int price; // aka PPU
        int amount;
        if (resource == ResourceType.MONEY) {
            resource = ResourceType.FOOD;
            isBuy = false;
            if (num > 50000000) {
                // amount must be whole number
                // ppu * amount must be a whole number
                // ppu * amount = num (or slightly below, due to rounding)
                amount = (int) Math.ceil(num / 50000000d);
                price = (int) Math.floor((double) num / amount);
            } else {
                price = num;
                amount = 1;
            }
        } else {
            isBuy = true;
            price = 0;
            amount = num;
        }
        return createTrade(receiver, resource, amount, price, isBuy);
    }

    public String createTrade(DBNation receiver, ResourceType resource, int amount, int ppu, boolean isBuy) {
        String leadername = receiver.getLeader();
        String leaderUrlEscape = URLEncoder.encode(leadername, StandardCharsets.UTF_8);
        String url = "" + Settings.PNW_URL() + "/nation/trade/create";
        Map<String, String> post = new HashMap<>();
        post.put("resourceoffer", resource.name().toLowerCase());
        post.put("offeramount", "" + amount);
        post.put("wantamount", "" + ppu);
        post.put("offertype", "personal");
        post.put("leaderpersonal", leadername);
        post.put("submit", isBuy ? "Buy" : "Sell");

        return PW.withLogin(() -> {
            String result = Auth.this.readStringFromURL(PagePriority.BANK_TRADE, url, emptyMap());
            Document dom = Jsoup.parse(result);
            String token = dom.select("input[name=sesh]").attr("value");
            post.put("sesh", token);

            result = Auth.this.readStringFromURL(PagePriority.TOKEN, url, post);
            dom = Jsoup.parse(result);
            String alert = PW.getAlert(dom);
            if (alert == null) {
                if (result.contains("/human/")) {
                    throw new IllegalArgumentException("Nation " + PW.getMarkdownUrl(nationId, false) + " has a captcha to complete: " + Settings.PNW_URL() + "/human/");
                }
            }
            if (alert == null || alert.isEmpty()) {
                return "(no output)";
            }
            return alert;
        }, this);

    }

    public String createAllianceEmbargo(int embargoFrom, NationOrAlliance embargo, String message) {
        Map<String, String> post = new HashMap<>();

        post.put("create_embargo_target", embargo.isAlliance() ? embargo.getName() : embargo.asNation().getLeader());
        post.put("create_embargo_type", embargo.isNation() ? "3" : "4");
        post.put("create_embargo_reason", message);
        post.put("create_embargo", "");
        post.put("validation_token", "");

        String url = "" + Settings.PNW_URL() + "/alliance/id=" + embargoFrom + "&display=embargoes";
        return PW.withLogin(new Callable<String>() {
            @Override
            public String call() throws Exception {
                String result = Auth.this.readStringFromURL(PagePriority.EMBARGO, url, post);
                return PW.getAlert(Jsoup.parse(result));
            }
        }, this);


    }

    public String setCityName(int id, String name) throws IOException {
        String url = PW.City.getCityUrl(id);
        String result = readStringFromURL(PagePriority.CITY_NAME, url, Collections.emptyMap());
        Document dom = Jsoup.parse(result);
        String token = dom.select("input[name=token]").attr("value");

        Map<String, String> post = new HashMap<>();
        post.put("newcityname", name);
        post.put("rename", "Rename");
        post.put("token", token);

        return readStringFromURL(PagePriority.TOKEN, url, post);
    }

    public String setBounty(DBNation target, WarType type, long amount) {
        return PW.withLogin(new Callable<String>() {
            @Override
            public String call() throws Exception {
                String url = "" + Settings.PNW_URL() + "/world/bounties";

                String result = Auth.this.readStringFromURL(PagePriority.PLACE_BOUNTY, url, emptyMap());
                Document dom = Jsoup.parse(result);
                String token = dom.getElementsByAttributeValue("name", "token").get(0).attr("value");

                Map<String, String> post = new HashMap<>();
                post.put("nation_name", target.getNation());
                post.put("war_type", type.getBountyName());
                post.put("bounty", amount + "");
                post.put("check", "true");
                post.put("post_bounty", "Post Bounty");
                post.put("token", token);

                result = Auth.this.readStringFromURL(PagePriority.TOKEN, url, post);
                return PW.getAlert(Jsoup.parse(result));
            }
        }, this);
    }

    public String importCityBuild(CityBuild build, int cityId) throws IOException {
        Map<String, String> post = new HashMap<>();
        post.put("imp_import", build.toString());
        post.put("imp_import_execute", "Execute Operation");

        String url = "" + Settings.PNW_URL() + "/city/improvements/import/id=" + cityId;

        return PW.withLogin(() -> {
            return readStringFromURL(PagePriority.IMPORT_BUILD, url, post);
        }, this);
    }

    public String modifyTreaty(int treatyId, boolean approve) {
        int aaId = getAllianceId();
        String url = "" + Settings.PNW_URL() + "/alliance/id=" + aaId + "&display=acp#treaties";

        Map<String, String> post = new HashMap<>();
        if (approve) post.put("approveTreatyId", "" + treatyId);
        else post.put("cancelTreatyId", "" + treatyId);

        return PW.withLogin(() -> {
            String html = Auth.this.readStringFromURL(PagePriority.MODIFY_TREATY_UNUSED, url, post);
            Document dom = Jsoup.parse(html);
            return PW.getAlert(dom);
        }, this);
    }

    public String sendTreaty(int allianceId, TreatyType type, String message, int days) {
        String aaName = PW.getName(allianceId, true);
        if (aaName == null) throw new IllegalArgumentException("Invalid aa: " + allianceId);

        Map<String, String> post = new HashMap<>();
        post.put("alliance_name", aaName);
        post.put("treaty_type", type.getId());
        post.put("treaty_url", message);
        post.put("treaty_length", days + "");
        post.put("propose_treaty", "Propose Treaty");

        int aaId = getAllianceId();
        String url = "" + Settings.PNW_URL() + "/alliance/id=" + aaId + "&display=acp#treaties";

        return PW.withLogin(() -> {
            String html = Auth.this.readStringFromURL(PagePriority.MODIFY_TREATY_UNUSED, url, post);
            Document dom = Jsoup.parse(html);
            return PW.getAlert(dom);
        }, this);
    }

    public List<PendingTreaty> getTreaties() {
        int aaId = getAllianceId();
        String url = "" + Settings.PNW_URL() + "/alliance/id=" + aaId + "&display=acp";
        return PW.withLogin(() -> {
            List<PendingTreaty> result = new ArrayList<>();
            String html = Auth.this.readStringFromURL(PagePriority.GET_TREATIES_UNUSED, url, emptyMap());
            Document dom = Jsoup.parse(html);
            Element table = dom.getElementsByClass("nationtable").get(1);
            Elements rows = table.getElementsByTag("tr");

            List<String> header = new ArrayList<>();
            for (Element child : rows.get(0).children()) {
                header.add(child.text());
            }

            for (int i = 1; i < rows.size(); i++) {
                Element row = rows.get(i);

                Map<String, Element> rowMap = new HashMap<>();

                Elements columns = row.getElementsByTag("td");
                for (int j = 0; j < header.size(); j++) {
                    if (columns.size() > j) {
                        String key = header.get(j);
                        Element value = columns.get(j);
                        rowMap.put(key, value);
                    }
                }

                PendingTreaty treaty = new PendingTreaty(aaId, rowMap);
                result.add(treaty);
            }
            return result;
        }, this);
    }

    public String safekeep(boolean login, double[] amountToDeposit, String note) throws Exception {
        DBNation nation = getNation();
        int fromBank = nation.getAlliance_id();

        Map<String, String> post = new HashMap<>();

        for (ResourceType type : ResourceType.values) {
            if (type == ResourceType.CREDITS) continue;
            double amt = amountToDeposit[type.ordinal()];
            post.put("dep" + type.name().toLowerCase(), MathMan.format(amt).replace(",", ""));
        }

        post.put("depnote", note);
        post.put("depsubmit", "Deposit");


        Callable<String> task = new Callable<>() {
            @Override
            public String call() throws IOException {
                String result = Auth.this.readStringFromURL(PagePriority.BANK_DEPOSIT, "" + Settings.PNW_URL() + "/alliance/id=" + fromBank + "&display=bank", emptyMap());
                Document dom = Jsoup.parse(result);
                String token = dom.select("input[name=token]").attr("value");
                post.put("token", token);

                result = Auth.this.readStringFromURL(PagePriority.TOKEN, "" + Settings.PNW_URL() + "/alliance/id=" + fromBank + "&display=bank", post);
                dom = Jsoup.parse(result);
                String alert = PW.getAlert(dom);
                if (alert.length() == 0) {
                    return "(no output)";
                }
                return alert;
            }
        };
        if (login) {
            return PW.withLogin(task, this);
        } else {
            return task.call();
        }
    }

    public String setRank(DBNation nation, DBAlliancePosition position) {
        return PW.withLogin(() -> {
            String url = "" + Settings.PNW_URL() + "/alliance/id=" + getAllianceId() + "&display=acp#assign_positions";
            String result = readStringFromURL(PagePriority.RANK_SET, url, Collections.emptyMap());


            Document dom = Jsoup.parse(result);
            // https://politicsandwar.com/alliance/id=7452&display=acp&appID=1233&action=1&auth=eb9d09acc1a3c686ff070e68440ab8a7

            StringBuilder response = new StringBuilder();

            if (nation.getPositionEnum() == Rank.APPLICANT && position != DBAlliancePosition.APPLICANT) {
                String title = position == DBAlliancePosition.REMOVE ? "Remove" : "Make Member";
                Elements elems = dom.select("img[title=" + title + "]");
                for (Element elem : elems) {
                    if (elem.parent().parent().nextElementSibling().nextElementSibling().html().contains(nation.getUrl())) {
                        String acceptUrl = elem.parent().attr("href");

                        result = readStringFromURL(PagePriority.TOKEN, acceptUrl, Collections.emptyMap());
                        response.append(PW.getAlert(Jsoup.parse(result)));

                        if (position == DBAlliancePosition.REMOVE) {
                            nation.update(false);
                            return response.toString();
                        }
                    }
                }
                Logg.text("Failed to set rank:\n\n---START BODY (3)---\n\n" + result + "\n\n---END BODY---\n\n");
            }
            result = readStringFromURL(PagePriority.TOKEN, url, Collections.emptyMap());
            dom = Jsoup.parse(result);

            String token = dom.select("input[name=validation_token]").attr("value");
            if (token == null || token.isEmpty()) {
                if (result.contains("/human/")) {
                    throw new IllegalArgumentException("Captcha required (nation: " + PW.getName(this.nationId, false) + "/" + this.nationId + ") " + Settings.PNW_URL() + "/human/");
                }
                String alert = PW.getAlert(dom);
                if (alert == null || alert.isEmpty()) {
                    System.out.println(":||REmove no alert found setrank " + result);;
                }
                throw new IllegalArgumentException("No token found: " + PW.getAlert(dom));
            }

            Map<String, String> post = new HashMap<>();
            post.put("alliance_positions_member", nation.getNation_id() + "");
            post.put("alliance_positions_member_name_type", "nation_id");
            post.put("alliance_positions_new_position_select", position.getInputName());
            post.put("validation_token", token);
            post.put("alliance_positions_assign_submit", "Save Position Assignment");

            result = readStringFromURL(PagePriority.TOKEN, url, post);
            dom = Jsoup.parse(result);
            int alerts = 0;
            for (Element element : dom.getElementsByClass("alert")) {
                String text = element.text();
                if (text.startsWith("Player Advertisement by ")) {
                    continue;
                }
                alerts++;
                response.append('\n').append(element.text());
            }
            if (alerts == 0) {
                response.append('\n').append("Set player rank ingame (probably? please confirm ingame). Remember to also set the rank on discord.");
            }

            nation.update(false);

            return response.toString().trim();
        }, this);
    }

    private Map<Integer, TaxBracket> cachedBrackets;

    public Map<Integer, TaxBracket> getTaxBrackets(boolean useCache) {
        if (useCache && cachedBrackets != null) {
            return cachedBrackets;
        }
        int aaId = getAllianceId();
        String url = "" + Settings.PNW_URL() + "/alliance/id=" + aaId + "&display=taxes";
        return PW.withLogin(() -> {
            Map<Integer, TaxBracket> result = new HashMap<>();
            String html = Auth.this.readStringFromURL(PagePriority.GET_BRACKETS_UNUSED, url, emptyMap());
            Document dom = Jsoup.parse(html);
            Element table = dom.getElementsByClass("nationtable").get(0);
            Elements rows = table.getElementsByTag("tr");

            List<String> header = new ArrayList<>();
            for (Element child : rows.get(0).children()) {
                header.add(child.text());
            }

            for (int i = 1; i < rows.size(); i++) {
                Element row = rows.get(i);

                Map<String, String> rowMap = new HashMap<>();

                Elements columns = row.getElementsByTag("td");
                for (int j = 0; j < header.size(); j++) {
                    if (columns.size() > j) {
                        String key = header.get(j);
                        String value = columns.get(j).text();
                        rowMap.put(key, value);
                    }
                }

                TaxBracket bracket = new TaxBracket(aaId, rowMap);
                result.put(bracket.taxId, bracket);
            }
            return cachedBrackets = result;
        }, this);
    }

    public Set<TradeResult> acceptTrades(int expectedNationId, boolean login) throws Exception {
        if (expectedNationId == getNationId()) throw new IllegalArgumentException("Buyer and seller cannot be the same");
        Auth auth = this;
        if (!TimeUtil.checkTurnChange()) return Collections.singleton(new TradeResult("cannot accept during turn change", TradeResultType.BLOCKADED));

        DBNation me = auth.getNation();
        if (me == null) throw new IllegalArgumentException("Invalid nation: " + auth.getNationId());

        if (me.isBlockaded()) return Collections.singleton(new TradeResult("receiver is blockaded", TradeResultType.BLOCKADED));
        Callable<Set<TradeResult>> task = new Callable<Set<TradeResult>>() {
            @Override
            public Set<TradeResult> call() throws Exception {
                Set<TradeResult> responses = new LinkedHashSet<>();

                boolean moreTrades = true;
                int max = 14;
                while (moreTrades && (max-- > 0)) {
                    moreTrades = false;

                    String url = "" + Settings.PNW_URL() + "/nation/trade/";
                    String html = Auth.this.readStringFromURL(PagePriority.BANK_TRADE, url, emptyMap());
                    Document dom = Jsoup.parse(html);

                    Elements tables = dom.getElementsByClass("nationtable");
                    if (tables.size() == 0) {
                        Logg.text("Error fetching trades\n\n---BODY TEXT\n\n" + html + "\n\n---\n\n");
                        return Collections.singleton(new TradeResult("Could not load trade page", TradeResultType.CAPTCHA));
                    }
                    Element table = tables.get(0);
                    Elements rows = table.getElementsByTag("tr");

                    if (rows.size() < 2) {
                        return Collections.singleton(new TradeResult("No trades found", TradeResultType.NO_TRADES));
                    }

                    if (!rows.get(0).child(1).text().contains("Selling Nation") || !rows.get(0).child(2).text().contains("Buying Nation")) {
                        return Collections.singleton(new TradeResult("No trade table found", TradeResultType.NO_TRADES));
                    }

                    for (int i = 1; i < rows.size(); i++) {
                        Element row = rows.get(i);
                        Elements forms = row.getElementsByClass("tradeForm");
                        if (forms.isEmpty()) {
                            continue;
                        }
                        Element form = forms.get(0).parent();
                        String ver = form.getElementsByAttributeValue("name", "ver").get(0).attr("value");
                        String token = form.getElementsByAttributeValue("name", "token").get(0).attr("value");
                        if (ver == null || token == null) continue;
                        long tradeaccid = Long.parseLong(form.getElementsByAttributeValue("name", "tradeaccid").get(0).attr("value"));

                        Elements columns = row.getElementsByTag("td");

                        String senderUrl = columns.get(1).getElementsByTag("a").first().attr("href");
                        String receiverUrl = columns.get(2).getElementsByTag("a").first().attr("href");
                        DBNation seller = DiscordUtil.parseNation(senderUrl);
                        DBNation buyer = DiscordUtil.parseNation(receiverUrl);

                        if (columns.get(6).text().toLowerCase().contains("accepted")) continue;

                        if (seller == null) continue;
                        if (buyer == null) continue;
                        if (seller.getNation_id() != expectedNationId && buyer.getNation_id() != expectedNationId) {
                            continue;
                        }
                        if (seller.getNation_id() != auth.getNationId() && buyer.getNation_id() != auth.getNationId()) {
                            continue;
                        }
                        DBNation other = seller.getNation_id() == me.getNation_id() ? buyer : seller;

                        Integer amount = MathMan.parseInt(columns.get(4).text().trim());

                        String rssSrc = columns.get(4).children().first().attr("src");
                        String[] rssSplit = rssSrc.split("[\\./]");
                        String rssName = rssSplit[rssSplit.length - 2];
                        ResourceType type = ResourceType.parse(rssName);

                        Integer ppu = MathMan.parseInt(columns.get(5).text().split("/")[0].trim());

                        TradeResult response = new TradeResult(seller, buyer);
                        response.setAmount(amount);
                        response.setResource(type);
                        response.setPPU(ppu);
                        responses.add(response);

                        if (amount == null || amount <= 0) {
                            response.setMessage("Invalid trade amount offered").setResult(TradeResultType.RUNTIME_ERROR);
                            continue;
                        }
                        if (type == null) {
                            response.setMessage("Invalid type: " + rssSrc).setResult(TradeResultType.RUNTIME_ERROR);
                            continue;
                        }
                        if (ppu == null) {
                            response.setMessage("Invalid trade ppu offered").setResult(TradeResultType.RUNTIME_ERROR);
                            continue;
                        }
                        if (type == ResourceType.CREDITS) {
                            response.setMessage("You cannot deposit credits").setResult(TradeResultType.CANNOT_DEPOSIT_CREDITS);
                            continue;
                        }

                        if (other.isBlockaded()) {
                            response.setMessage(other.getNation() + " is blockaded").setResult(TradeResultType.BLOCKADED);
                            continue;
                        }

                        if (ppu == 0) {
                            if (buyer.getNation_id() != auth.getNationId()) {
                                response.setMessage("Trade must be sent as a sell offer").setResult(TradeResultType.NOT_A_SELL_OFFER);
                                continue;
                            }
                        } else if (ppu >= 100000) {
                            if (type != ResourceType.FOOD) {
                                response.setMessage("Money trades must be sent as a food trade").setResult(TradeResultType.NOT_A_FOOD_TRADE);
                                continue;
                            }
                            if (buyer.getNation_id() == me.getNation_id() || seller.getNation_id() == other.getNation_id()) {
                                response.setMessage("Money trades, must be buying food, not selling").setResult(TradeResultType.NOT_A_SELL_OFFER);
                                continue;
                            }
                        } else {
                            if (seller.getNation_id() == auth.getNationId()) {
                                if (type != ResourceType.FOOD) {
                                    response.setMessage("Money trades must be at least $100,000 and use food").setResult(TradeResultType.NOT_A_FOOD_TRADE);
                                }
                                response.setMessage("Money trades must be at least $100,000").setResult(TradeResultType.INCORRECT_PPU);
                            } else {
                                response.setMessage("Sell offers must be at $0 ppu").setResult(TradeResultType.INCORRECT_PPU);
                            }
                            continue;
                        }


                        Map<String, String> post = new HashMap<>();
                        post.put("rcustomamount", amount + "");
                        post.put("tradeaccid", tradeaccid + "");
                        post.put("ver", ver);
                        post.put("token", token);
                        post.put("acctrade", "");

                        String tradeAlert = PW.getAlert(Jsoup.parse(Auth.this.readStringFromURL(PagePriority.TOKEN, url, post)));
                        if (tradeAlert == null) {
                            response.setResult(TradeResultType.UNKNOWN_ERROR);
                            continue;
                        }

                        response.setMessage(tradeAlert);

                        if (tradeAlert.contains("You can't accept trade offers because a nation has a naval blockade on you.")) {
                            response.setResult(TradeResultType.BLOCKADED);
                            continue;
                        }
                        if (tradeAlert.contains("You can't enter in an amount greater than the offer.")
                                || tradeAlert.contains("You do not have enough ")
                                || tradeAlert.contains("The nation posting that trade offer does not have enough resources on hand to sell.")) {
                            response.setResult(TradeResultType.INSUFFICIENT_RESOURCES);
                        }
                        if (tradeAlert.contains("You successfully accepted a trade offer")) {
                            response.setResult(TradeResultType.SUCCESS);
                        } else {
                            response.setResult(TradeResultType.UNCATEGORIZED_ERROR);
                        }

                        moreTrades = true;
                        break;
                    }
                }

                return responses;
            }
        };
        return login ? PW.withLogin(task, auth) : task.call();
    }

    public String withdrawResources(DBAlliance alliance, NationOrAlliance receiver, double[] amount, String note) {
        Map<String, String> post = ResourceType.resourcesToJson(receiver, ResourceType.resourcesToMap(amount), note);
        int fromBank = alliance.getAlliance_id();

        return PW.withLogin(() -> {
            String result = readStringFromURL(PagePriority.BANK_DEPOSIT, "" + Settings.INSTANCE.PNW_URL() + "/alliance/id=" + fromBank + "&display=bank", emptyMap());
            Document dom = Jsoup.parse(result);
            String token = dom.select("input[name=token]").attr("value");
            post.put("token", token);
            StringBuilder response = new StringBuilder();

            result = readStringFromURL(PagePriority.TOKEN, "" + Settings.INSTANCE.PNW_URL() + "/alliance/id=" + fromBank + "&display=bank", post);
            dom = Jsoup.parse(result);
            for (Element element : dom.getElementsByClass("alert")) {
                String text = element.text();
                if (text.startsWith("Player Advertisement by ")) {
                    continue;
                }
                response.append('\n').append(text);
            }
            if (response.length() == 0) {
                return "(not output)";
            }
            return response.toString();
        }, this);
    }

    public String leaveAlliance(DBAlliance offshoreAA) {
        int aaId = offshoreAA.getId();

        Map<String, String> post = new HashMap<>();
        post.put("disband_name", offshoreAA.getName());
        post.put("password", getPassword());
        post.put("leave_alliance", "true");
        post.put("disband_confirm_1", "true");
        post.put("disband_confirm_2", "true");
        post.put("disband_confirm_3", "true");

        return PW.withLogin(() -> {
            String result = readStringFromURL(PagePriority.BANK_DEPOSIT, "" + Settings.PNW_URL() + "/alliance/leave/id=" + aaId, emptyMap());
            Document dom = Jsoup.parse(result);
            String token = dom.select("input[name=token]").attr("value");
            post.put("token", token);
            StringBuilder response = new StringBuilder();

            result = readStringFromURL(PagePriority.TOKEN, "" + Settings.PNW_URL() + "/alliance/leave/id=" + aaId, post);
            dom = Jsoup.parse(result);
            for (Element element : dom.getElementsByClass("alert")) {
                String text = element.text();
                if (text.startsWith("Player Advertisement by ")) {
                    continue;
                }
                response.append('\n').append(text);
            }
            if (response.length() == 0) {
                return "(not output)";
            }
            return response.toString();
        }, this);
    }

    public String apply(DBAlliance alliance) {
        Map<String, String> post = new HashMap<>();
        // submit: Apply
        post.put("submit", "Apply");
        // /alliance/join/id=
        return PW.withLogin(() -> {
            String result = readStringFromURL(PagePriority.BANK_DEPOSIT, "" + Settings.PNW_URL() + "/alliance/join/id=" + alliance.getId(), emptyMap());
            Document dom = Jsoup.parse(result);
            String token = dom.select("input[name=token]").attr("value");
            post.put("token", token);
            StringBuilder response = new StringBuilder();

            result = readStringFromURL(PagePriority.TOKEN, "" + Settings.PNW_URL() + "/alliance/join/id=" + alliance.getId(), post);
            dom = Jsoup.parse(result);
            for (Element element : dom.getElementsByClass("alert")) {
                String text = element.text();
                if (text.startsWith("Player Advertisement by ")) {
                    continue;
                }
                response.append('\n').append(text);
            }
            if (response.length() == 0) {
                return "(not output)";
            }
            return response.toString();
        }, this);
    }

    ///////////////////////

    public enum TradeResultType {
        RUNTIME_ERROR,
        BLOCKADED,
        MULTI,
        INSUFFICIENT_RESOURCES,
        CAPTCHA,
        TURN_CHANGE,
        UNKNOWN_ERROR,
        UNCATEGORIZED_ERROR,

        NO_TRADES,
        NOT_A_SELL_OFFER,
        NOT_A_BUY_OFFER,
        NOT_A_FOOD_TRADE,
        INCORRECT_PPU,
        CANNOT_DEPOSIT_CREDITS,

        NO_BANK_ACCESS,

        SUCCESS,
    }

    public static class TradeResult {
        private final DBNation seller;
        private final DBNation buyer;

        private ResourceType resource;
        private String message;
        private int amount;
        private int ppu;
        private TradeResultType result;

        public TradeResult(DBNation seller, DBNation buyer) {
            this.seller = seller;
            this.buyer = buyer;
        }

        public TradeResult(String message, TradeResultType result) {
            this((DBNation) null, null);
            setMessage(message);
            setResult(result);
        }

        public DBNation getBuyer() {
            return buyer;
        }

        public DBNation getSeller() {
            return seller;
        }

        public void setResource(ResourceType resource) {
            this.resource = resource;
        }

        public TradeResult setMessage(String message) {
            this.message = message;
            return this;
        }



        @Override
        public String toString() {
            if (amount == 0 && resource == null && ppu == 0) {
                return result + ": " + message;
            }
            return amount + "x" + resource + "@" + ppu + "->" + result + ": " + message;
        }

        public String getMessage() {
            return message;
        }

        public ResourceType getResource() {
            return resource;
        }

        public int getAmount() {
            return amount;
        }

        public int getPpu() {
            return ppu;
        }

        public TradeResultType getResult() {
            return result;
        }

        public TradeResult setResult(TradeResultType result) {
            this.result = result;
            return this;
        }

        public TradeResult setAmount(Integer amount) {
            this.amount = amount == null ? 0 : amount;
            return this;
        }

        public TradeResult setPPU(Integer ppu) {
            this.ppu = ppu == null ? 0 : ppu;
            return this;
        }
    }

    private static String parse(double mine, ResourceType type, Map<ResourceType, Double> warchest) {
        return "" + (long) Math.max(0, mine - warchest.getOrDefault(type, 0d));
    }



    public String safekeep(Map<ResourceType, Double> warchest) throws IOException {
        DBNation nation = getNation();
        int fromBank = nation.getAlliance_id();
        ApiKeyPool.ApiKey key = getApiKey();

        Map<ResourceType, Double> stockpile = nation.getStockpile();
        Map<String, String> post = new HashMap<>();
        for (Map.Entry<ResourceType, Double> entry : stockpile.entrySet()) {
            ResourceType type = entry.getKey();
            double amount = entry.getValue();
            if (type == ResourceType.CREDITS || amount <= 0) continue;
            post.put("dep" + type.name().toLowerCase(), parse(amount, type, warchest));
        }
        post.put("depnote", "");
        post.put("depsubmit", "Deposit");

        double total = 0;
        for (ResourceType type : ResourceType.values()) {
            String amtStr = post.get("dep" + type.getName().toLowerCase());
            if (amtStr != null) {
                Double amt = MathMan.parseDouble(amtStr);
                amt = Math.max(0, amt - warchest.getOrDefault(type, 0d));
                total += Locutus.imp().getTradeManager().getHighAvg(type) * amt;
            }
        }
        if (total < 3000000) return null;
        return PW.withLogin(() -> {
            String result = readStringFromURL(PagePriority.BANK_DEPOSIT, "" + Settings.PNW_URL() + "/alliance/id=" + fromBank + "&display=bank", emptyMap());
            Document dom = Jsoup.parse(result);
            String token = dom.select("input[name=token]").attr("value");
            post.put("token", token);
            StringBuilder response = new StringBuilder();

            result = readStringFromURL(PagePriority.TOKEN, "" + Settings.PNW_URL() + "/alliance/id=" + fromBank + "&display=bank", post);
            dom = Jsoup.parse(result);
            for (Element element : dom.getElementsByClass("alert")) {
                String text = element.text();
                if (text.startsWith("Player Advertisement by ")) {
                    continue;
                }
                response.append('\n').append(text);
            }
            if (response.length() == 0) {
                return "(not output)";
            }
            return response.toString();
        }, this);
    }

    public Map.Entry<double[], String> acceptAndOffshoreTrades(GuildDB currentDB, int expectedNationId) throws Exception {
        synchronized (OffshoreInstance.BANK_LOCK) {
            if (!TimeUtil.checkTurnChange()) {
                throw new IllegalArgumentException("Turn change");
            }
            DBNation nation = this.getNation();
            if (nation.getPosition() <= Rank.APPLICANT.id) {
                throw new IllegalArgumentException("Receiver is not member");
            }
            DBAlliancePosition position = nation.getAlliancePosition();
            if (nation.getPositionEnum().id < Rank.HEIR.id && (position == null || !position.hasPermission(AlliancePermission.WITHDRAW_BANK) || !position.hasPermission(AlliancePermission.VIEW_BANK))) {
                throw new IllegalArgumentException("Receiver does not have bank access");
            }
            DBNation senderNation = DBNation.getById(expectedNationId);
            if (senderNation == null) throw new IllegalArgumentException("Sender is null");
//            if (nation.isBlockaded()) throw new IllegalArgumentException("Receiver is blockaded");
//            if (senderNation.isBlockaded()) throw new IllegalArgumentException("Sender is blockaded");

            OffshoreInstance offshore = currentDB.getOffshore();

            GuildDB authDb = Locutus.imp().getGuildDBByAA(nation.getAlliance_id());
            if (authDb == null) throw new IllegalArgumentException("Receiver is not in a server with this Bot: " + nation.getAlliance_id());
            OffshoreInstance receiverOffshore = authDb.getOffshore();
            if (receiverOffshore == null) {
                throw new IllegalArgumentException("Receiver does not have a registered offshore");
            }
            if (receiverOffshore != offshore) {
                throw new IllegalArgumentException("Receiver offshore does not match this guilds offshore");
            }
            Set<Integer> aaIds = currentDB.getAllianceIds();
            long senderId;
            int senderType;
            if (aaIds.isEmpty()) {
                senderId = currentDB.getIdLong();
                senderType = currentDB.getReceiverType();
            } else if (aaIds.size() == 1) {
                senderId = aaIds.iterator().next();
                senderType = nation.getAlliance().getReceiverType();
            } else if (aaIds.contains(senderNation.getAlliance_id())){
                senderId = senderNation.getAlliance_id();
                senderType = senderNation.getAlliance().getReceiverType();
            } else {
                throw new IllegalArgumentException("Sender " + senderNation.getQualifiedId() + " is not in alliances: " + StringMan.getString(aaIds));
            }

            StringBuilder response = new StringBuilder("Checking trades...");

            double[] amtDeposited = PW.withLogin(new Callable<double[]>() {
                @Override
                public double[] call() throws Exception {
                    Set<Auth.TradeResult> trades = Auth.this.acceptTrades(expectedNationId, false);
                    double[] toDeposit = ResourceType.getBuffer();
                    for (Auth.TradeResult trade : trades) {
                        response.append("\n" + trade.toString());
                        if (trade.getResult() == Auth.TradeResultType.SUCCESS) {
                            int sign = trade.getBuyer().getNation_id() == Auth.this.getNationId() ? 1 : -1;
                            toDeposit[trade.getResource().ordinal()] += trade.getAmount() * sign;
                            toDeposit[ResourceType.MONEY.ordinal()] += ((long) trade.getPpu()) * trade.getAmount() * sign * -1;
                        }
                    }
                    if (ResourceType.convertedTotal(toDeposit) > 0) {
                        String safekeepResult = Auth.this.safekeep(false, toDeposit, "#ignore");
                        if (!safekeepResult.contains("You successfully made a deposit into the alliance bank.")) {
                            response.append("\n- " + "Could not safekeep: " + safekeepResult);
                            return ResourceType.getBuffer();
                        }
                    }
                    if (ResourceType.convertedTotal(toDeposit) == 0) return ResourceType.getBuffer();

                    OffshoreInstance bank = nation.getAlliance().getBank();
                    if (bank != offshore) {
                        for (int i = 0; i < toDeposit.length; i++) {
                            if (toDeposit[i] < 0) toDeposit[i] = 0;
                        }
                        TransferResult transferResult = bank.transfer(offshore.getAlliance(), ResourceType.resourcesToMap(toDeposit), "#ignore", null);
                        response.append("Offshore " + transferResult.toLineString());
                        if (!transferResult.getStatus().isSuccess()) {
                            return ResourceType.getBuffer();
                        }

                        // subtract from alliance
                    }

                    // add balance to guilddb
                    long tx_datetime = System.currentTimeMillis();
                    String note = "#deposit";

                    response.append("\nAdding deposits:");

                    offshore.getGuildDB().addTransfer(tx_datetime, senderId, senderType, offshore.getAlliance(), Auth.this.getNationId(), note, toDeposit);
                    response.append("\n- Added " + ResourceType.toString(toDeposit) + " to " + currentDB.getGuild());
                    // add balance to expectedNation
                    currentDB.addTransfer(tx_datetime, senderNation, senderId, senderType, Auth.this.getNationId(), note, toDeposit);
                    response.append("\n- Added " + ResourceType.toString(toDeposit) + " to " + senderNation.getUrl());

                    MessageChannel logChannel = offshore.getGuildDB().getResourceChannel(0);
                    if (logChannel != null) {
                        RateLimitUtil.queue(logChannel.sendMessage(response));
                    }

                    return toDeposit;
                }
            }, Auth.this);

            return new AbstractMap.SimpleEntry<>(amtDeposited, response.toString());
        }
    }
}
