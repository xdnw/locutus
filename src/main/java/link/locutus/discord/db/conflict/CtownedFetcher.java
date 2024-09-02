package link.locutus.discord.db.conflict;

import link.locutus.discord.Locutus;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.conflict.ConflictCategory;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.wiki.game.PWWikiUtil;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.sql.SQLException;
import java.text.Normalizer;
import java.text.ParseException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class CtownedFetcher {
    private final ConflictManager manager;

    public CtownedFetcher(ConflictManager manager) {
        this.manager = manager;
    }
    private String getCtoConflict(String url, String text, boolean useCache) throws IOException {
        String cacheFileStr = "files/" + Normalizer.normalize(text, Normalizer.Form.NFKD) + ".html";
        Path path = Paths.get(cacheFileStr);
        if (useCache) {
            if (new File(cacheFileStr).exists()) {
                return Files.readString(path, StandardCharsets.ISO_8859_1);
            }
        }
        String urlFull = "https://ctowned.net" + url;
        String html = Jsoup.connect(urlFull).timeout(60000).sslSocketFactory(socketFactory()).ignoreContentType(true).get().html();
        Files.write(path, html.getBytes());
        return html;
    }

    private SSLSocketFactory socketFactory() {
        TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
            public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }

            public void checkClientTrusted(X509Certificate[] certs, String authType) {
            }

            public void checkServerTrusted(X509Certificate[] certs, String authType) {
            }
        }};
        try {
            SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            SSLSocketFactory result = sslContext.getSocketFactory();

            return result;
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new RuntimeException("Failed to create a SSL socket factory", e);
        }
    }

    private void loadCtownedConflict(GuildDB db, boolean useCache, String cellUrl, ConflictCategory category, String conflictName, Date startDate, Date endDate, Consumer<String> output) throws IOException {
        String conflictHtml = getCtoConflict(cellUrl, conflictName, useCache);

        long startMs = startDate.getTime();
        long endMs = endDate == null ? Long.MAX_VALUE : endDate.getTime() + TimeUnit.DAYS.toMillis(1);

        Document conflictDom = Jsoup.parse(conflictHtml);
        Elements elements = conflictDom.select("span[data-toggle=tooltip]");
        String toolTip1 = elements.get(0).attr("title");
        String toolTip2 = elements.get(1).attr("title");

        String col1Name = elements.get(0).text();
        String col2Name = elements.get(1).text();

        List<String> coalition1Names = new ArrayList<>(new HashSet<>(Arrays.asList(toolTip1.substring(1, toolTip1.length() - 1).split(", "))));
        List<String> coalition2Names = new ArrayList<>(new HashSet<>(Arrays.asList(toolTip2.substring(1, toolTip2.length() - 1).split(", "))));
        switch (conflictName) {
            case "New Year Nuke Me" -> {
                coalition2Names.remove("Bring Back Uncle Bens");
                coalition2Names.remove("Chavez Nuestro que Estas en los Cielos");
            }
            case "New Year Firework" -> {
                coalition2Names.remove("Mensa HQ");
                coalition2Names.remove("MDC");
            }
            case "Ragnarok" -> {
                coalition2Names.remove("Church Of Atom");
                coalition2Names.remove("Aurora");
                coalition2Names.remove("The Fighting Pacifists");
            }
            case "World vs Fortuna" -> {
                coalition2Names.remove("Skull & Bones");
                coalition1Names.remove("Skull & Bones");
            }
            case "Blue Balled" -> {
                coalition2Names.remove("Containment Site 453");
            }
        }
        List<String> unknown = new ArrayList<>();
        Set<String> allNames = new HashSet<>(coalition1Names);
        allNames.addAll(coalition2Names);
        for (String name : allNames) {
            if (manager.getAllianceId(name, Long.MAX_VALUE, true) == null) {
                unknown.add(name);
            }
        }
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("Unknown alliances: " + unknown.stream().collect(Collectors.joining(", ")));
        }
        Set<Integer> col1Ids = coalition1Names.stream().map(f -> manager.getAllianceId(f, startMs, true)).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Integer> col2Ids = coalition2Names.stream().map(f -> manager.getAllianceId(f, startMs, true)).filter(Objects::nonNull).collect(Collectors.toSet());
        output.accept("Adding add: " + conflictName + " | " + startDate + "|  " + endDate + "\n" +
                "- Col1: " + coalition1Names + "\n" +
                "- Col2: " + coalition2Names);
        boolean isOverLap = col1Ids.stream().anyMatch(col2Ids::contains);
        if (isOverLap) {
            output.accept("Overlap between coalitions " + coalition1Names.stream().filter(coalition2Names::contains).collect(Collectors.toList()));
            return;
        }
        if (col1Ids.isEmpty()) {
            throw new IllegalArgumentException("Coalition 1 is empty");
        }
        if (col2Ids.isEmpty()) {
            throw new IllegalArgumentException("Coalition 2 is empty");
        }
        String wiki = PWWikiUtil.getWikiUrlFromCtowned(conflictName);
        if (wiki == null) wiki = "";

        Conflict conflict = manager.getConflict(conflictName);
        if (conflict == null) {
            String finalWiki = wiki;
            conflict = manager.getConflictMap().values().stream().filter(f -> finalWiki.equalsIgnoreCase(f.getWiki())).findFirst().orElse(null);
        }
        if (conflict == null) {
            conflict = Locutus.imp().getWarDb().getConflicts().addConflict(conflictName, db.getIdLong(), category, col1Name, col2Name, wiki, "", "", TimeUtil.getTurn(startMs), endMs == Long.MAX_VALUE ? Long.MAX_VALUE : TimeUtil.getTurn(endMs));
        }
        if (conflict.getSide(true).getName().equalsIgnoreCase("coalition 1")) {
            conflict.setName(col1Name, true);
        }
        if (conflict.getSide(false).getName().equalsIgnoreCase("coalition 2")) {
            conflict.setName(col2Name, false);
        }
        if (!wiki.isEmpty() && conflict.getWiki().isEmpty()) {
            conflict.setWiki(wiki);
        }
        if (conflict.getAllianceIds().isEmpty()) {
            for (int aaId : col1Ids) conflict.addParticipant(aaId, true, null, null);
            for (int aaId : col2Ids) conflict.addParticipant(aaId, false, null, null);
        }
    }

    public String loadCtownedConflicts(GuildDB db, boolean useCache, ConflictCategory category, String urlStub, String fileName) throws IOException, SQLException, ClassNotFoundException, ParseException {
        List<String> warnings = new ArrayList<>();
        Document document = Jsoup.parse(getCtoConflict(urlStub, fileName, useCache));
        // get table id=conflicts-table
        Element table = document.getElementById("conflicts-table");
        // Skip the first row (header)
        Elements rows = table.select("tr");
        for (int i = 1; i < rows.size(); i++) {
            Element row = rows.get(i);
            // Get the first cell
            Element firstCell = row.select("td").first();

            // Get the a element
            Element aElement = firstCell.select("a").first();

            // Get the URL and text
            String cellUrl = aElement.attr("href");
            String conflictName = StringMan.normalize(aElement.text());

            String startDateStr = row.select("td").get(6).text();
            String endDateStr = row.select("td").get(7).text();
            Date startDate = TimeUtil.YYYY_MM_DD_FORMAT.parse(startDateStr);
            Date endDate = endDateStr.contains("Ongoing") ? null : TimeUtil.YYYY_MM_DD_FORMAT.parse(endDateStr);
            loadCtownedConflict(db, useCache, cellUrl, category, conflictName, startDate, endDate, warnings::add);
        }
        return warnings.isEmpty() ? "" : String.join("\n", warnings);
    }
}
