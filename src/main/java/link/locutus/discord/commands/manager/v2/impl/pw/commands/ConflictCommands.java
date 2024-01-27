package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv1.enums.TreatyType;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.annotation.Switch;
import link.locutus.discord.commands.manager.v2.binding.annotation.Timestamp;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.db.Conflict;
import link.locutus.discord.db.ConflictManager;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.entities.Treaty;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.FileUtil;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.math.ArrayUtil;
import net.dv8tion.jda.api.entities.User;
import org.json.JSONObject;
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
import java.text.Normalizer;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ConflictCommands {
    //- list conflict command

    @Command
    public String listConflicts(@Me IMessageIO io, @Me JSONObject command, ConflictManager manager, @Switch("i") boolean includeInactive) {
        Map<Integer, Conflict> conflicts = ArrayUtil.sortMap(manager.getConflictMap(), (o1, o2) -> {
            if (o1.getEnd() == Long.MAX_VALUE || o2.getEnd() == Long.MAX_VALUE) {
                return Long.compare(o2.getEnd(), o1.getEnd());
            }
            return Long.compare(o2.getStart(), o1.getStart());
        });
        if (!includeInactive) {
            conflicts.entrySet().removeIf(entry -> entry.getValue().getEnd() != Long.MAX_VALUE);
        }
        if (conflicts.isEmpty()) {
            io.create().confirmation("No active conflicts",
                    "Press `list inactive` to show inactive conflicts",
                    command,
                    "list inactive").send();
            return null;
        }
        StringBuilder response = new StringBuilder();
        // Name - Start date - End date (bold underline
        // - Coalition1:
        // - Coalition2:
        for (Map.Entry<Integer, Conflict> entry : conflicts.entrySet()) {
            Conflict conflict = entry.getValue();
            response.append("**").append(conflict.getName()).append("** - ")
                    .append(TimeUtil.DD_MM_YYYY.format(conflict.getStart())).append(" - ");
            if (conflict.getEnd() == Long.MAX_VALUE) {
                response.append("Present");
            } else {
                response.append(TimeUtil.DD_MM_YYYY.format(conflict.getEnd()));
            }
            response.append("\n");
            response.append("- Coalition 1: ").append(conflict.getCoalition2().stream().map(f -> PnwUtil.getName(f, true)).collect(Collectors.joining(","))).append("\n");
            response.append("- Coalition 2: ").append(conflict.getCoalition2().stream().map(f -> PnwUtil.getName(f, true)).collect(Collectors.joining(","))).append("\n");
            response.append("---\n");
        }
        return response.toString();
    }

    @Command
    @RolePermission(value = Roles.ADMIN, root = true)
    public String setConflictEnd(ConflictManager manager, Conflict conflict, @Timestamp long time) {
        conflict.setEnd(time);
        return "Set `" + conflict.getName() + "` end to " + TimeUtil.DD_MM_YYYY.format(time);
    }

    @Command
    @RolePermission(value = Roles.ADMIN, root = true)
    public String setConflictName(ConflictManager manager, Conflict conflict, String name) {
        String previousName = conflict.getName();
        conflict.setName(name);
        return "Set `" + previousName  + "` name to `" + name + "`";
    }

    @Command
    @RolePermission(value = Roles.ADMIN, root = true)
    public String setConflictStart(ConflictManager manager, Conflict conflict, @Timestamp long time) {
        conflict.setStart(time);
        return "Set `" + conflict.getName() + "` start to " + TimeUtil.DD_MM_YYYY.format(time);
    }

    private int getFighting(DBAlliance alliance, Set<DBAlliance> enemyCoalition) {
        return (int) alliance.getActiveWars().stream().filter(war -> {
            int defAllianceId = war.getAttacker_aa() == alliance.getId() ? war.getDefender_aa() : war.getAttacker_aa();
            if (!enemyCoalition.contains(DBAlliance.getOrCreate(defAllianceId))) return false;
            DBNation attacker = war.getNation(true);
            DBNation defender = war.getNation(false);
            if (attacker == null || defender == null) return false;
            if (defender.active_m() > 2880 || defender.getPositionEnum() == Rank.APPLICANT)
                return false;
            return true;
        }).count();
    }

    @Command
    @RolePermission(value = Roles.ADMIN, root = true)
    public String addConflict(ConflictManager manager, @Me User user, Set<DBAlliance> coalition1, Set<DBAlliance> coalition2, @Switch("n") String conflictName, @Switch("d")@Timestamp Long start) {
        boolean isAdmin = Roles.ADMIN.hasOnRoot(user);
        if (!isAdmin) {
            if (conflictName != null) {
                throw new IllegalArgumentException("Cannot specify `conflictName` " + Roles.ADMIN.toDiscordRoleNameElseInstructions(Locutus.imp().getServer()));
            }
            if (start != null && (start > System.currentTimeMillis() || start < System.currentTimeMillis() - TimeUnit.DAYS.toMillis(2) - 1000)) {
                throw new IllegalArgumentException("Date for `start` must be within 2 days");
            }
            // coalition1 and coalition 2 cannot have overlap
            if (coalition1.stream().filter(coalition2::contains).count() > 0) {
                throw new IllegalArgumentException("Cannot have overlap between `coalition1` and `coalition2`");
            }
            // each alliance must have at least 1 war with an enemy alliance
            int totalWars = 0;
            for (DBAlliance aa : coalition1) {
                int wars = getFighting(aa, coalition2);
                if (wars == 0) {
                    throw new IllegalArgumentException("Alliance " + aa.getMarkdownUrl() + " does not have any active wars with an alliance in `coalition2`");
                }
                totalWars += wars;
            }
            for (DBAlliance aa : coalition2) {
                if (getFighting(aa, coalition1) == 0) {
                    throw new IllegalArgumentException("Alliance " + aa.getMarkdownUrl() + " does not have any active wars with an alliance in `coalition1`");
                }
            }
            // ensure
            if (totalWars <= 15) {
                throw new IllegalArgumentException("Total wars between `coalition1` and `coalition2` must be greater than 15");
            }
        }
        if (conflictName == null) {
            DBAlliance largest1 = coalition1.stream().max(Comparator.comparingDouble(DBAlliance::getScore)).orElse(null);
            DBAlliance largest2 = coalition2.stream().max(Comparator.comparingDouble(DBAlliance::getScore)).orElse(null);
            conflictName = largest1.getName() + " sphere VS " + largest2.getName() + " sphere ";
            if (manager.getConflict(conflictName) != null) {
                conflictName = conflictName + "(" + Calendar.getInstance().get(Calendar.YEAR) + ")";
            }
        }
        if (manager.getConflict(conflictName) != null) {
            throw new IllegalArgumentException("Conflict with name `" + conflictName + "` already exists");
        }
        Conflict conflict = manager.addConflict(conflictName, start, Long.MAX_VALUE);
        StringBuilder response = new StringBuilder();
        response.append("Created conflict `" + conflictName + "`\n");
        // add coalitions
        for (DBAlliance alliance : coalition1) conflict.addParticipant(alliance.getId(), true);
        for (DBAlliance alliance : coalition2) conflict.addParticipant(alliance.getId(), false);
        response.append("- Coalition1: `").append(coalition1.stream().map(f -> f.getName()).collect(Collectors.joining(","))).append("`\n");
        response.append("- Coalition2: `").append(coalition2.stream().map(f -> f.getName()).collect(Collectors.joining(",")));
        return response.toString();
    }

    @Command
    @RolePermission(value = Roles.ADMIN, root = true)
    public String removeCoalition(Conflict conflict, Set<DBAlliance> alliances) {
        Set<DBAlliance> notRemoved = new LinkedHashSet<>();
        for (DBAlliance alliance : alliances) {
            if (conflict.getCoalition1().contains(alliance.getId()) || conflict.getCoalition2().contains(alliance.getId())) {
                conflict.removeParticipant(alliance.getId());
            } else {
                notRemoved.add(alliance);
            }
        }
        List<String> errors = new ArrayList<>();
        if (notRemoved.size() > 0) {
            if (notRemoved.size() < 10) {
                errors.add("The alliances you specified " + StringMan.join(notRemoved.stream().map(DBAlliance::getName).collect(Collectors.toList()), ", ") + " are not in the conflict");
            } else {
                errors.add(notRemoved.size() + " alliances you specified are not in the conflict");
            }
            if (notRemoved.size() == alliances.size()) {
                throw new IllegalArgumentException(StringMan.join(errors, "\n"));
            }
        }
        String msg = "Removed " + (alliances.size() - notRemoved.size()) + " alliances from the conflict";
        if (!errors.isEmpty()) {
            msg += "\n- " + StringMan.join(errors, "\n- ");
        }
        return msg;
    }

    @Command
    public String addCoalition(@Me User user, Conflict conflict, Set<DBAlliance> alliances, @Switch("col1") boolean isCoalition1, @Switch("col2") boolean isCoalition2) {
        boolean hasAdmin = Roles.ADMIN.hasOnRoot(user);
        if (isCoalition1 && isCoalition2) {
            throw new IllegalArgumentException("Cannot specify both `isCoalition1` and `isCoalition2`");
        }
        Set<DBAlliance> addCol1 = new HashSet<>();
        Set<DBAlliance> addCol2 = new HashSet<>();

        for (DBAlliance alliance : alliances) {
            if (hasAdmin) {
                if (isCoalition1) {
                    addCol1.add(alliance);
                    continue;
                } else if (isCoalition2) {
                    addCol2.add(alliance);
                    continue;
                }
            }

            Map<Integer, Treaty> treaties = alliance.getTreaties(TreatyType::isMandatoryDefensive, false);
            boolean hasTreatyCol1 = false;
            boolean hasTreatyCol2 = false;
            for (Map.Entry<Integer, Treaty> entry : treaties.entrySet()) {
                if (conflict.getCoalition1().contains(entry.getKey())) {
                    hasTreatyCol1 = true;
                } else if (conflict.getCoalition2().contains(entry.getKey())) {
                    hasTreatyCol2 = true;
                }
            }
            int fightingCol1 = getFighting(alliance, conflict.getCoalition1Obj());
            int fightingCol2 = getFighting(alliance, conflict.getCoalition2Obj());

            if (hasTreatyCol1 && !hasTreatyCol2 && fightingCol1 == 0) {
                if (isCoalition2) throw new IllegalArgumentException("Alliance " + alliance.getMarkdownUrl() + " has a treaty with a member of coalition 1");
                addCol1.add(alliance);
            } else if (hasTreatyCol2 && !hasTreatyCol1 && fightingCol2 == 0) {
                if (isCoalition1) throw new IllegalArgumentException("Alliance " + alliance.getMarkdownUrl() + " has a treaty with a member of coalition 2");
                addCol2.add(alliance);
            } else if (fightingCol1 > 0) {
                if (fightingCol2 > 0) {
                    throw new IllegalArgumentException("Alliance " + alliance.getMarkdownUrl() + " is fighting both coalitions");
                }
                if (hasTreatyCol1 && !hasTreatyCol2) {
                    throw new IllegalArgumentException("Alliance " + alliance.getMarkdownUrl() + " has a treaty with a member of coalition 1");
                }
                if (isCoalition1) throw new IllegalArgumentException("Alliance " + alliance.getMarkdownUrl() + " is fighting coalition 1");
                addCol2.add(alliance);
            } else if (fightingCol2 > 0) {
                if (isCoalition2) throw new IllegalArgumentException("Alliance " + alliance.getMarkdownUrl() + " is fighting coalition 2");
                if (hasTreatyCol2 && !hasTreatyCol1) {
                    throw new IllegalArgumentException("Alliance " + alliance.getMarkdownUrl() + " has a treaty with a member of coalition 2");
                }
                addCol1.add(alliance);
            } else if (hasAdmin) {
                throw new IllegalArgumentException("Please specify either `isCoalition1` or `isCoalition2`");
            } else {
                throw new IllegalArgumentException("Alliance " + alliance.getMarkdownUrl() + " does not have active wars with the conflict participants. Please contact an administrator");
            }
        }
        for (DBAlliance aa : addCol1) {
            conflict.addParticipant(aa.getId(), true);
        }
        for (DBAlliance aa : addCol2) {
            conflict.addParticipant(aa.getId(), false);
        }
        return "Added " + addCol1.size() + " alliances to coalition 1 and " + addCol2.size() + " alliances to coalition 2";
    }

    //- Import conflicts from ctowned
    @Command
    @RolePermission(value = Roles.ADMIN, root = true)
    public static String importCtowned() throws IOException {
        loadConflicts("conflicts", "conflicts");
        loadConflicts("conflicts/micros", "conflicts-micros");
        return null;
    }

    private static void loadConflicts(String urlStub, String fileName) throws IOException {
        String fileStr = "files/" + fileName + ".html";

        Document document;
        if (new File(fileName).exists()) {
            document = Jsoup.parse(Files.readString(Path.of(fileStr)));
        } else {
            String url = "https://ctowned.net/" + urlStub;
            document = Jsoup.connect(url).timeout(60000).sslSocketFactory(socketFactory()).ignoreContentType(true).get();
            String html = document.html();
            Files.write(Path.of(fileStr), html.getBytes());
        }
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
            String conflictName = aElement.text();

            String startDateStr = row.select("td").get(6).text();
            String endDateStr = row.select("td").get(7).text();
            TemporalAccessor startDate = TimeUtil.YYYY_MM_DD.parse(startDateStr);
            TemporalAccessor endDate = endDateStr.contains("Ongoing") ? null : TimeUtil.YYYY_MM_DD.parse(endDateStr);

            // Call saveConflict(url)
            String conflictHtml = getConflict(cellUrl, conflictName);
            Document conflictDom = Jsoup.parse(conflictHtml);
            Elements elements = conflictDom.select("span[data-toggle=tooltip]");
            String toolTip1 = elements.get(0).attr("title");
            String toolTip2 = elements.get(1).attr("title");

            System.out.println(conflictName + " | " + startDate + "|  " + endDate);
            System.out.println("Title1 " + toolTip1);
            System.out.println("Title2 " + toolTip2);
            System.out.println("\n\n---\n");
        }
    }

    private static String getConflict(String url, String text) throws IOException {
        text = Normalizer.normalize(text, Normalizer.Form.NFKD);
        text = text.replaceAll("[^a-zA-Z0-9\\.\\-_ ]", "");

        String cacheFileStr = "files/" + text + ".html";
        Path path = Paths.get(cacheFileStr);
        if (new File(cacheFileStr).exists()) {
            System.out.println("Reading " + cacheFileStr);
            return Files.readString(path, StandardCharsets.ISO_8859_1);
        }

        String urlFull = "https://ctowned.net" + url;
        String html = Jsoup.connect(urlFull).timeout(60000).sslSocketFactory(socketFactory()).ignoreContentType(true).get().html();
        Files.write(path, html.getBytes());
        return html;
    }

    static private SSLSocketFactory socketFactory() {
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

    public static void main(String[] args) throws IOException {
        importCtowned();
    }
}
