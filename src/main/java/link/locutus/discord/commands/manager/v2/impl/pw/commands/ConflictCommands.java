package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import com.amazonaws.services.s3.model.S3ObjectSummary;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.commands.manager.v2.binding.annotation.*;
import link.locutus.discord.commands.manager.v2.binding.annotation.Timestamp;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.CoalitionPermission;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.IsGuild;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.WhitelistPermission;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.ForumDB;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.conflict.CoalitionSide;
import link.locutus.discord.db.conflict.CtownedFetcher;
import link.locutus.discord.db.entities.*;
import link.locutus.discord.gpt.pw.WikiPagePW;
import link.locutus.discord.util.*;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.wiki.game.PWWikiPage;
import link.locutus.wiki.game.PWWikiUtil;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv1.enums.TreatyType;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.db.conflict.Conflict;
import link.locutus.discord.db.conflict.ConflictManager;
import link.locutus.discord.db.entities.conflict.ConflictCategory;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.math.ArrayUtil;
import link.locutus.discord.web.jooby.AwsManager;
import link.locutus.discord.web.jooby.WebRoot;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import retrofit2.http.HEAD;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ConflictCommands {
    @Command(desc = "View a conflict's configured information")
    public String info(ConflictManager manager, Conflict conflict, boolean showParticipants,
                       @Switch("d") boolean hideDeleted,
                       @Switch("i") boolean showIds) {
        if (hideDeleted && !showParticipants) {
            throw new IllegalArgumentException("Cannot hide deleted conflicts without `showParticipants:True`");
        }
        StringBuilder response = new StringBuilder();

        response.append("**__" + conflict.getName() + " - `#" + conflict.getId() + "` - " + conflict.getCategory().name() + "__**\n");
        response.append("wiki: `" + (conflict.getWiki().isEmpty() ? "N/A" : conflict.getWiki()) + "`\n");
        response.append("Start: " + DiscordUtil.timestamp(TimeUtil.getTimeFromTurn(conflict.getStartTurn()), null) + "\n");
        response.append("End: " + (conflict.getEndTurn() == Long.MAX_VALUE ? "Ongoing" : DiscordUtil.timestamp(TimeUtil.getTimeFromTurn(conflict.getEndTurn()), null)) + "\n");
        response.append("Casus Belli: `" + (conflict.getCasusBelli().isEmpty() ? "N/A" : conflict.getCasusBelli().isEmpty()) + "`\n");
        response.append("Status: `" + (conflict.getStatusDesc().isEmpty() ? "N/A" : conflict.getStatusDesc().isEmpty()) + "`\n");
        response.append("Num wars: `" + conflict.getTotalWars() + "`");
        if (conflict.getEndMS() > System.currentTimeMillis()) {
            response.append(" (`" + conflict.getActiveWars() + "` active)");
        }
        response.append("\n");

        List<CoalitionSide> sides = Arrays.asList(conflict.getSide(true), conflict.getSide(false));
        boolean hasDeleted = false;
        if (showParticipants) {
            int i = 1;
            for (CoalitionSide side : sides) {
                response.append("\n**" + side.getName() + "** (coalition " + (i++) + ")\n");
                Set<Integer> ids = new LinkedHashSet<>();
                for (int aaId : side.getAllianceIdsSorted()) {
                    if (DBAlliance.get(aaId) == null) {
                        if (hideDeleted) continue;
                        hasDeleted = true;
                    }
                    if (showIds) {
                        ids.add(aaId);
                        continue;
                    }
                    long start = conflict.getStartTurn(aaId);
                    long end = conflict.getEndTurn(aaId);
                    String aaName = manager.getAllianceName(aaId);
                    response.append("- " + MarkupUtil.markdownUrl(aaName, PW.getAllianceUrl(aaId)) + " | ");
                    if (start == conflict.getStartTurn()) {
                        response.append("Initial");
                    } else {
                        response.append(DiscordUtil.timestamp(TimeUtil.getTimeFromTurn(start), null));
                    }
                    response.append(" -> ");
                    if (end >= conflict.getEndTurn()) {
                        response.append("Present");
                    } else {
                        response.append(DiscordUtil.timestamp(TimeUtil.getTimeFromTurn(end), null));
                    }
                    response.append("\n");
                }
                if (!ids.isEmpty()) {
                    response.append("Participants: `" + StringMan.join(ids, ",") + "`\n");
                }

            }

        } else {
            for (CoalitionSide side : sides) {
                response.append(side.getName() + ": `" + StringMan.join(side.getAllianceIdsSorted(), ",") + "`\n");
            }
            response.append("Use `showParticipants:True` to list participants\n");
        }
        if (hasDeleted && !hideDeleted) {
            response.append("\nUse `hideDeleted:True` to hide deleted alliances");
        }

        return response.toString() + "\n\n<" + Settings.INSTANCE.WEB.S3.SITE + "/conflict?id=" + conflict.getId() + ">";
    }

    @Command(desc = "Sets the wiki page for a conflict")
    @RolePermission(value = Roles.MILCOM)
    @CoalitionPermission(Coalition.MANAGE_CONFLICTS)
    public String setWiki(ConflictManager manager, Conflict conflict, String url) throws IOException {
        if (url.startsWith("http")) {
            if (url.endsWith("/")) url = url.substring(0, url.length() - 1);
            url = url.substring(url.lastIndexOf("/") + 1);
        }
        conflict.setWiki(url.replace(" ", "_"));
//        String importResult = importWikiPage(db, manager, conflict.getName(), url, false, true);
        conflict.push(manager, null, false, false);
        return "Set wiki to: `" + url + "`. To pull additional wiki information, use: " + CM.conflict.sync.wiki_page.cmd.toSlashMention();
    }

    @Command(desc = "Sets the wiki page for a conflict")
    @RolePermission(Roles.MILCOM)
    @CoalitionPermission(Coalition.MANAGE_CONFLICTS)
    public String setStatus(ConflictManager manager, Conflict conflict, String status) throws IOException {
        conflict.setStatus(status.replace("\\n", "\n"));
        conflict.push(manager, null, false, false);
        return "Done! Set the status of `" + conflict.getName() + "` to `" + status + "`";
    }

    @Command(desc = "Sets the wiki page for a conflict")
    @RolePermission(Roles.MILCOM)
    @CoalitionPermission(Coalition.MANAGE_CONFLICTS)
    public String setCB(ConflictManager manager, Conflict conflict, String casus_belli) throws IOException {
        conflict.setCasusBelli(casus_belli.replace("\\n", "\n"));
        conflict.push(manager, null, false, false);
        return "Done! Set the casus belli of `" + conflict.getName() + "` to `" + casus_belli + "`";
    }

    @Command(desc = "Sets the wiki page for a conflict")
    @RolePermission(Roles.MILCOM)
    @CoalitionPermission(Coalition.MANAGE_CONFLICTS)
    public String setCategory(ConflictManager manager, Conflict conflict, ConflictCategory category) throws IOException {
        conflict.setCategory(category);
        conflict.push(manager, null, false, true);
        return "Done! Set the category of `" + conflict.getName() + "` to `" + category + "`";
    }

    @Command(desc = "Pushes conflict data to the AWS bucket for the website")
    @RolePermission(Roles.MILCOM)
    @CoalitionPermission(Coalition.MANAGE_CONFLICTS)
    public String syncConflictData(@Me IMessageIO io, ConflictManager manager, @Default Set<Conflict> conflicts, @Switch("u") boolean upload_graph, @Switch("w") boolean reinitialize_wars, @Switch("g") boolean reinitialize_graphs) throws IOException, ParseException {
        AwsManager aws = manager.getAws();
        if (aws == null) {
            throw new IllegalArgumentException("AWS is not configured in `config.yaml`");
        }
        CompletableFuture<IMessageBuilder> msgFuture;
        if (reinitialize_wars) {
            msgFuture = io.send("Initializing wars...");
            manager.loadConflictWars(conflicts, true);
        } else {
            msgFuture = io.send("Please wait...");
        }
        if (reinitialize_graphs) {
            for (Conflict conflict : conflicts) {
                io.updateOptionally(msgFuture, "Initializing graphs for " + conflict.getName() + "...");
                conflict.updateGraphsLegacy(manager);
            }
        }
        if (conflicts != null) {
            List<String> urls = new ArrayList<>();
            for (Conflict conflict : conflicts) {
                io.updateOptionally(msgFuture, "Pushing " + conflict.getName() + "...");
                urls.addAll(conflict.push(manager, null, upload_graph, false));
            }
            io.updateOptionally(msgFuture, "Pushing index...");
            urls.add(manager.pushIndex());
            return "Done! See:\n- <" + StringMan.join(urls, ">\n- <") + ">";
        } else {
            if (!manager.pushDirtyConflicts()) {
                manager.pushIndex();
            }
            return "Done! See: <" + Settings.INSTANCE.WEB.S3.SITE + ">";
        }
    }

    @Command(desc = "Delete a conflict from the database\n" +
            "Does not push changes to the website")
    @RolePermission(Roles.MILCOM)
    @CoalitionPermission(Coalition.MANAGE_CONFLICTS)
    public String deleteConflict(ConflictManager manager, @Me IMessageIO io, @Me JSONObject command, Conflict conflict, @Switch("f") boolean force) {
        if (!force) {
            String title = "Delete conflict " + conflict.getName();
            StringBuilder body = new StringBuilder();
            body.append("ID: `" + conflict.getId() + "`\n");
            body.append("Start: `" + TimeUtil.turnsToTime(conflict.getStartTurn()) + "`\n");
            body.append("End: `" + (conflict.getEndTurn() == Long.MAX_VALUE ? "Ongoing..." : TimeUtil.turnsToTime(conflict.getEndTurn())) + "`\n");
            body.append("Col1: `" + conflict.getCoalition2Obj().stream().map(DBAlliance::getName).collect(Collectors.joining(",")) + "`\n");
            body.append("Col2: `" + conflict.getCoalition2Obj().stream().map(DBAlliance::getName).collect(Collectors.joining(",")) + "`\n");
            io.create().confirmation(title, body.toString(), command).send();
            return null;
        }
        manager.deleteConflict(conflict);
        manager.pushIndex();
        return "Deleted conflict: #" + conflict.getId() + " - `" + conflict.getName() + "`" +
                "\nNote: this does not push the data to the site";
    }

    @Command(desc = "Get a list of the conflicts in the database")
    public String listConflicts(@Me IMessageIO io, @Me JSONObject command, ConflictManager manager, @Switch("i") boolean includeInactive) {
        Map<Integer, Conflict> conflicts = ArrayUtil.sortMap(manager.getConflictMap(), (o1, o2) -> {
            if (o1.getEndTurn() == Long.MAX_VALUE || o2.getEndTurn() == Long.MAX_VALUE) {
                return Long.compare(o2.getEndTurn(), o1.getEndTurn());
            }
            return Long.compare(o2.getStartTurn(), o1.getStartTurn());
        });
        if (!includeInactive) {
            conflicts.entrySet().removeIf(entry -> entry.getValue().getEndTurn() != Long.MAX_VALUE);
        }
        if (conflicts.isEmpty()) {
            if (includeInactive) {
                return "No conflicts";
            }
            io.create().confirmation("No active conflicts",
                    "Press `list inactive` to show inactive conflicts",
                    command,
                    "includeInactive", "list inactive").send();
            return null;
        }
        StringBuilder response = new StringBuilder(conflicts.size() + " conflicts:\n");
        // Name - Start date - End date (bold underline
        // - Coalition1:
        // - Coalition2:
        for (Map.Entry<Integer, Conflict> entry : conflicts.entrySet()) {
            Conflict conflict = entry.getValue();
            response.append("**# " + conflict.getId() + ": ").append(conflict.getName()).append("** - ")
                    .append(TimeUtil.DD_MM_YYYY.format(TimeUtil.getTimeFromTurn(conflict.getStartTurn()))).append(" - ");
            if (conflict.getEndTurn() == Long.MAX_VALUE) {
                response.append("Present");
            } else {
                response.append(TimeUtil.DD_MM_YYYY.format(TimeUtil.getTimeFromTurn(conflict.getEndTurn())));
            }
            response.append("\n");
            response.append("- " + conflict.getTotalWars() + " wars\n");
            response.append("- Coalition 1: ").append(conflict.getCoalition1().stream().map(f -> PW.getName(f, true)).collect(Collectors.joining(","))).append("\n");
            response.append("- Coalition 2: ").append(conflict.getCoalition2().stream().map(f -> PW.getName(f, true)).collect(Collectors.joining(","))).append("\n");
            response.append("---\n");
        }
        return response.toString();
    }

    @Command(desc = "Set the end date for a conflict\n" +
            "Use a value of `-1` to specify no end date\n" +
            "This does not push the data to the site")
    @RolePermission(Roles.MILCOM)
    @CoalitionPermission(Coalition.MANAGE_CONFLICTS)
    public String setConflictEnd(ConflictManager manager, @Me JSONObject command, Conflict conflict, @Timestamp long time, @Arg("Only set the end date for a single alliance") @Switch("a") DBAlliance alliance) {
        String timeStr = command.getString("time");
        if (MathMan.isInteger(timeStr) && Long.parseLong(timeStr) < 0) {
            time = Long.MAX_VALUE;
        }
        if (alliance != null) {
            Boolean side = conflict.isSide(alliance.getId());
            if (side == null) {
                throw new IllegalArgumentException("Alliance " + alliance.getMarkdownUrl() + " is not in the conflict");
            }
            conflict.addParticipant(alliance.getAlliance_id(), side, null, time);
            return "Set `" + conflict.getName() + "` end to " + TimeUtil.DD_MM_YYYY.format(time) + " for " + alliance.getMarkdownUrl();
        }
        conflict.setEnd(time);
        conflict.push(manager, null, false, true);
        return "Set `" + conflict.getName() + "` end to " + TimeUtil.DD_MM_YYYY.format(time) +
                "\nNote: this does not recalculate conflict data";
    }

    @Command(desc = "Set the start date for a conflict\n" +
            "Use a value of `-1` to specify no start date (if prividing an alliance)\n" +
            "This does not push the data to the site")
    @RolePermission(Roles.MILCOM)
    @CoalitionPermission(Coalition.MANAGE_CONFLICTS)
    public String setConflictStart(ConflictManager manager, @Me JSONObject command, Conflict conflict, @Timestamp long time, @Switch("a") DBAlliance alliance) {
        String timeStr = command.getString("time");
        if (MathMan.isInteger(timeStr) && Long.parseLong(timeStr) < 0) {
            if (alliance == null) {
                throw new IllegalArgumentException("Cannot set start date to NULL without specifying an alliance");
            }
            time = Long.MAX_VALUE;
        }
        if (alliance != null) {
            Boolean side = conflict.isSide(alliance.getId());
            if (side == null) {
                throw new IllegalArgumentException("Alliance " + alliance.getMarkdownUrl() + " is not in the conflict");
            }
            conflict.addParticipant(alliance.getAlliance_id(), side, time, null);
            return "Set `" + conflict.getName() + "` start to " + TimeUtil.DD_MM_YYYY.format(time) + " for " + alliance.getMarkdownUrl();
        }
        conflict.setStart(time);
        conflict.push(manager, null, false, true);
        return "Set `" + conflict.getName() + "` start to " + TimeUtil.DD_MM_YYYY.format(time) +
                "\nNote: this does not recalculate conflict data";
    }

    @Command(desc = "Set the name of a conflict, or the name of a conflict's coalition")
    @RolePermission(Roles.MILCOM)
    @CoalitionPermission(Coalition.MANAGE_CONFLICTS)
    public String setConflictName(ConflictManager manager, Conflict conflict, String name, @Switch("col1") boolean isCoalition1, @Switch("col2") boolean isCoalition2) {
        if (isCoalition1 && isCoalition2) {
            throw new IllegalArgumentException("Cannot specify both `isCoalition1` and `isCoalition2`");
        }
        if (!name.matches("[a-zA-Z0-9_. &+/$-]+")) {
            throw new IllegalArgumentException("Conflict name must be alphanumeric (`" + name + "`)");
        }
        if (MathMan.isInteger(name)) {
            throw new IllegalArgumentException("Conflict name cannot be a number (`" + name + "`)");
        }
        String previousName = isCoalition1 ? conflict.getSide(true).getName() : isCoalition2 ? conflict.getSide(false).getName() : conflict.getName();
        String sideName;
        if (isCoalition1) {
            sideName = "coalition 1";
            conflict.setName(name, true);
        } else if (isCoalition2) {
            sideName = "coalition 2";
            conflict.setName(name, false);
        } else {
            sideName = "conflict";
            conflict.setName(name);
        }
        conflict.push(manager, null, false, true);
        return "Changed " + sideName + " name `" + previousName  + "` => `" + name + "` and pushed to the site";
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

    @Command(desc = "Manually create an ongoing conflict between two coalitions\n" +
            "Use `-1` for end date to specify no end date\n")
    @RolePermission(Roles.MILCOM)
    @CoalitionPermission(Coalition.MANAGE_CONFLICTS)
    public String addConflict(@Me GuildDB db, ConflictManager manager, @Me JSONObject command, @Me IMessageIO io, ConflictCategory category, Set<DBAlliance> coalition1, Set<DBAlliance> coalition2, @Timestamp long start, @Timestamp long end, @Switch("n") String conflictName) throws IOException, ParseException {
        String timeStr = command.getString("end");
        if (MathMan.isInteger(timeStr) && Long.parseLong(timeStr) < 0) {
            end = Long.MAX_VALUE;
        }
        if (end <= start) {
            throw new IllegalArgumentException("End date must be after start date (" + start + " >= " + end + ")");
        }
        if (conflictName != null) {
            if (MathMan.isInteger(conflictName)) {
                throw new IllegalArgumentException("Conflict name cannot be a number (`" + conflictName + "`)");
            }
            if (!conflictName.matches("[a-zA-Z0-9_. ]+")) {
                throw new IllegalArgumentException("Conflict name must be alphanumeric (`" + conflictName + "`)");
            }
        }
        if (coalition1.stream().filter(coalition2::contains).count() > 0) {
            throw new IllegalArgumentException("Cannot have overlap between `coalition1` and `coalition2`");
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
        Conflict conflict = manager.addConflict(conflictName, db.getIdLong(), category, "Coalition 1", "Coalition 2", "", "", "", TimeUtil.getTurn(start), Long.MAX_VALUE);
        StringBuilder response = new StringBuilder();
        response.append("Created conflict `" + conflictName + "`\n");
        // add coalitions
        for (DBAlliance alliance : coalition1) conflict.addParticipant(alliance.getId(), true, null, null);
        for (DBAlliance alliance : coalition2) conflict.addParticipant(alliance.getId(), false, null, null);
        response.append("- Coalition1: `").append(coalition1.stream().map(f -> f.getName()).collect(Collectors.joining(","))).append("`\n");
        response.append("- Coalition2: `").append(coalition2.stream().map(f -> f.getName()).collect(Collectors.joining(","))).append("`");
        String reinitializeGraphsArg = null;
        {
            CompletableFuture<IMessageBuilder> msgFuture = io.send("Loading conflict stats");
            manager.loadVirtualConflict(conflict, false);
            long diff = end - start;
            if (diff < TimeUnit.DAYS.toMillis(90)) {
                io.updateOptionally(msgFuture, "Updating graphs...");
                conflict.updateGraphsLegacy(manager);
            } else {
                reinitializeGraphsArg = "true";
            }
        }
        manager.clearAllianceCache();
        return response.toString() +
                "\nThen to initialize the stats and push to the site:\n" +
                CM.conflict.sync.website.cmd.conflicts(conflict.getId() + "").upload_graph("true").reinitialize_graphs(reinitializeGraphsArg);
    }

    @Command(desc = "Remove a set of alliances from a conflict\n" +
            "This does NOT update conflict stats")
    @RolePermission(Roles.MILCOM)
    @CoalitionPermission(Coalition.MANAGE_CONFLICTS)
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
        return msg + "\nThis does NOT update conflict stats.";
    }

    @Command(desc = "Add a set of alliances to a conflict\n" +
            "This does NOT update conflict stats")
    @RolePermission(Roles.MILCOM)
    @CoalitionPermission(Coalition.MANAGE_CONFLICTS)
    public String addCoalition(ConflictManager manager, @Me User user, Conflict conflict, Set<DBAlliance> alliances, @Switch("col1") boolean isCoalition1, @Switch("col2") boolean isCoalition2) {
        boolean hasAdmin = Roles.ADMIN.hasOnRoot(user);
        if (isCoalition1 && isCoalition2) {
            throw new IllegalArgumentException("Cannot specify both `isCoalition1` and `isCoalition2`");
        }
        Set<DBAlliance> addCol1 = new HashSet<>();
        Set<DBAlliance> addCol2 = new HashSet<>();

        for (DBAlliance alliance : alliances) {
            if (conflict.getCoalition1().contains(alliance.getId())) {
                throw new IllegalArgumentException("Alliance " + alliance.getMarkdownUrl() + " is already in coalition 1");
            }
            if (conflict.getCoalition2().contains(alliance.getId())) {
                throw new IllegalArgumentException("Alliance " + alliance.getMarkdownUrl() + " is already in coalition 2");
            }
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
            conflict.addParticipant(aa.getId(), true, null, null);
        }
        for (DBAlliance aa : addCol2) {
            conflict.addParticipant(aa.getId(), false, null, null);
        }
        manager.clearAllianceCache();
        return "Added " + addCol1.size() + " alliances to coalition 1 and " + addCol2.size() + " alliances to coalition 2\n" +
                "Note: this does NOT update conflict stats";
    }

    @Command(desc = "Import ctowned conflicts into the database\n" +
            "This does not push the data to the site")
    @RolePermission(value = Roles.MILCOM, root = true)
//    @CoalitionPermission(Coalition.MANAGE_CONFLICTS)
    public String importCtowned(@Me GuildDB db, ConflictManager manager,
                                @Default("true") @Arg("If the cached version of the site is used")
                                boolean useCache) throws SQLException, IOException, ParseException, ClassNotFoundException {
        CtownedFetcher fetcher = new CtownedFetcher(manager);
        String response1 = fetcher.loadCtownedConflicts(db, useCache, ConflictCategory.NON_MICRO, "conflicts", "conflicts");
        String response2 = fetcher.loadCtownedConflicts(db, useCache, ConflictCategory.MICRO, "conflicts/micros", "conflicts-micros");
        List<String> warnings = new ArrayList<>();
        if (!response1.isEmpty()) warnings.add(response1);
        if (!response2.isEmpty()) warnings.add(response2);
        String msg = "Done!\nNote: this does not push the data to the site";
        if (!warnings.isEmpty()) {
            msg += ". See the log below:\n\n" + StringMan.join(warnings, "\n");
        }
        return msg;
    }

    @Command(desc = "Import alliance names (to match with the ids of deleted alliances)\n" +
            "This does not push the data to the site")
    @RolePermission(Roles.MILCOM)
    @CoalitionPermission(Coalition.MANAGE_CONFLICTS)
    public String importAllianceNames(ConflictManager manager) throws IOException, ParseException {
        manager.saveDataCsvAllianceNames();
        for (Map.Entry<String, Integer> entry : PWWikiUtil.getWikiAllianceIds().entrySet()) {
            if (manager.getAllianceName(entry.getValue()) != null) continue;
            if (manager.getAllianceId(entry.getKey(), Long.MAX_VALUE) != null) continue;
            manager.addLegacyName(entry.getValue(), entry.getKey(), 0);
        }
        return "Done!\nNote: this does not push the data to the site";
    }

    @Command(desc = "Import a wiki page as a conflict\n" +
            "This does not push the data to the site")
    @RolePermission(Roles.MILCOM)
    @CoalitionPermission(Coalition.MANAGE_CONFLICTS)
    public String importWikiPage(@Me GuildDB db, ConflictManager manager,
                                 @Arg("The name of the wiki page") String name,
                                 @Arg("The url of the wiki page") @Default String url,
                                 @Default("true") boolean useCache,
                                 @Switch("p") boolean skipPushToSite) throws IOException, ParseException {
        if (name.contains("http")) return "Please specify the name of the wiki page, not the URL for `name`";
        if (url == null) {
            url = Normalizer.normalize(name.replace(" ", "_"), Normalizer.Form.NFKC);
            url = URLEncoder.encode(url, StandardCharsets.UTF_8);
        } else {
            if (url.endsWith("/")) url = url.substring(0, url.length() - 1);
            url = url.substring(url.lastIndexOf("/") + 1);
        }
        Map<String, String> errors = new HashMap<>();
        Conflict conflict = PWWikiUtil.loadWikiConflict(name, url, errors, useCache);
        String footer = "";

        StringBuilder response = new StringBuilder();
        if (conflict == null) {
            response.append("Failed to load conflict `" + name + "` with url `https://politicsandwar.com/wiki/" + url + "`\n");
        } else {
            if (conflict.getStartTurn() < TimeUtil.getTurn(1577836800000L)) {
                response.append("Conflict `" + name + "` before the war cutoff date of " + DiscordUtil.timestamp(1577836800000L, null) + "\n");
            } else {
                Set<Conflict> conflicts = loadWikiConflicts(db, manager, new ArrayList<>(List.of(conflict)), true, true);
                List<String> conflictNamesAndIds = conflicts.stream().map(f -> f.getName() + " - #" + f.getId()).toList();
                response.append("Loaded conflict `" + name + "` with url `https://politicsandwar.com/wiki/" + url + "`\n");
                if (conflictNamesAndIds.isEmpty()) {
                    response.append("No conflicts created\n");
                } else {
                    response.append("Created loaded\n- " + StringMan.join(conflictNamesAndIds, "\n- ") + "\n");
                }
                response.append("See: " +
                        CM.conflict.info.cmd.toSlashMention() +
                        "\n\n");

                for (Conflict dbCon : conflicts) {
                    if (!skipPushToSite && dbCon.getId() != -1) {
                        dbCon.push(manager, null, false, true);
                        footer = "\n\nNote: this does not push the data to the site";
                    }
                }
            }
        }
        if (!errors.isEmpty()) {
            response.append("Errors: " + StringMan.join(errors.values(), "\n"));
        }

        return response.toString() + footer;
    }

    private Set<Conflict> loadWikiConflicts(GuildDB db, ConflictManager manager, List<Conflict> wikiConflicts, boolean loadWars, boolean includeGraphs) throws IOException, ParseException {
        Map<String, Set<Conflict>> conflictsByWiki = manager.getConflictMap().values().stream().collect(Collectors.groupingBy(Conflict::getWiki, Collectors.toSet()));
        // Cutoff date
        wikiConflicts.removeIf(f -> f.getStartTurn() < TimeUtil.getTurn(1577836800000L));
        // print all ongoing conflicts
        Set<Conflict> loaded = new LinkedHashSet<>();
        for (Conflict wikiConflict : wikiConflicts) {
            Set<Conflict> existingSet = conflictsByWiki.get(wikiConflict.getWiki());
            if (existingSet == null) {
                Conflict conflict = manager.addConflict(wikiConflict.getName(), db.getIdLong(), wikiConflict.getCategory(), wikiConflict.getSide(true).getName(), wikiConflict.getSide(false).getName(), wikiConflict.getWiki(), wikiConflict.getCasusBelli(), wikiConflict.getStatusDesc(), wikiConflict.getStartTurn(), wikiConflict.getEndTurn());
                existingSet = Set.of(conflict);
                if (loadWars) {
                    manager.loadVirtualConflict(conflict, true);
                    if (includeGraphs) {
                        conflict.updateGraphsLegacy(manager);
                    }
                }
            }
            for (Conflict existing : existingSet) {
                existing.set(wikiConflict, existingSet.size() == 1 && manager.getConflict(wikiConflict.getName()) == null);
            }
            loaded.addAll(existingSet);
        }
        return loaded;
    }

    @Command(desc = "Import all wiki pages as conflicts\n" +
            "This does not push the data to the site")
    @RolePermission(Roles.MILCOM)
    @CoalitionPermission(Coalition.MANAGE_CONFLICTS)
    public String importWikiAll(@Me GuildDB db, ConflictManager manager, @Default("true") boolean useCache) throws IOException, ParseException {
        Map<String, String> errors = new LinkedHashMap<>();
        List<Conflict> conflicts = PWWikiUtil.loadWikiConflicts(errors, useCache);
        Set<Conflict> loaded = loadWikiConflicts(db, manager, new ArrayList<>(conflicts), false, false);
        return "Loaded " + loaded.size() + " conflicts from the wiki. Use " + CM.conflict.sync.website.cmd.conflicts("*").upload_graph("true").reinitialize_wars("true").reinitialize_graphs("true") + " recalculate stats and push to the website.";
    }


    @Command(desc = "Recalculate the table data for a set of conflicts\n" +
            "This does not push the data to the site")
    @RolePermission(Roles.MILCOM)
    @CoalitionPermission(Coalition.MANAGE_CONFLICTS)
    public String recalculateTables(ConflictManager manager, Set<Conflict> conflicts) {
        manager.loadConflictWars(conflicts, true);
        return "Done!\nNote: this does not push the data to the site";
    }

    @Command(desc = "Recalculate the graph data for a set of conflicts\n" +
            "This does not push the data to the site")
    @RolePermission(Roles.MILCOM)
    @CoalitionPermission(Coalition.MANAGE_CONFLICTS)
    public String recalculateGraphs(@Me IMessageIO io, ConflictManager manager, Set<Conflict> conflicts) throws IOException, ParseException {
        CompletableFuture<IMessageBuilder> future = io.send("Please wait...");
        return recalculateGraphs2(io, future, manager, conflicts);
    }

    private String recalculateGraphs2(@Me IMessageIO io, CompletableFuture<IMessageBuilder> updateMsg, ConflictManager manager, Set<Conflict> conflicts) throws IOException, ParseException {
        for (Conflict conflict : conflicts) {
            io.updateOptionally(updateMsg, "Updating " + conflict.getName());
            conflict.updateGraphsLegacy(manager);
        }
        return "Done!\nNote: this does not push the data to the site";
    }

    @Command(desc = "Bulk import conflict data from multiple sources\n" +
            "Including ctowned, wiki, graph data, alliance names or ALL\n" +
            "This does not push the data to the site unless `all` is used")
    @RolePermission(Roles.MILCOM)
    @CoalitionPermission(Coalition.MANAGE_CONFLICTS)
    public String importConflictData(@Me IMessageIO io, @Me GuildDB db, ConflictManager manager,
                                     @Switch("c") boolean ctowned,
                                     @Switch("g") Set<Conflict> graphData,
                                     @Switch("a") boolean allianceNames,
                                     @Switch("w") boolean wiki,
                                     @Switch("s") boolean all) throws IOException, SQLException, ClassNotFoundException, ParseException {
        CompletableFuture<IMessageBuilder> msgFuture = io.send("Please wait...");

        boolean loadGraphData = false;
        if (all) {
            Locutus.imp().getWarDb().loadWarCityCountsLegacy();
            allianceNames = true;
//            ctowned = true;
            wiki = true;
            loadGraphData = true;
        }
        if (allianceNames) {
            io.updateOptionally(msgFuture, "Importing alliance names");
            importAllianceNames(manager);
        }
        if (ctowned) {
            io.updateOptionally(msgFuture, "Importing from ctowned.net");
            importCtowned(db, manager, true);
        }
        if (wiki) {
            io.updateOptionally(msgFuture, "Importing from wiki");
            importWikiAll(db, manager, true);
        }
        if (loadGraphData && graphData == null) {
            io.updateOptionally(msgFuture, "Recaculating tables");
            graphData = new LinkedHashSet<>(manager.getConflictMap().values());
            recalculateTables(manager, graphData);
        }
        if (graphData != null) {
            io.updateOptionally(msgFuture, "Recaculating graphs");
            recalculateGraphs2(io, msgFuture, manager, graphData);
            if (all && !graphData.isEmpty()) {
                io.updateOptionally(msgFuture, "Pushing data to the site");
                for (Conflict conflict : graphData) {
                    conflict.push(manager, null, true, false);
                }
                manager.pushIndex();
                return "Done!";
            }
        }
        return "Done!\n" +
                "Note: this does not push the data to the site\n" +
                "See: " + CM.conflict.sync.website.cmd.toSlashMention();
    }

    @Command(desc = "Configure the guilds and conflict ids that will be featured by this guild\n" +
            "By default all conflicts are featured\n" +
            "Specify either a set of conflicts, or a guild to feature all conflicts from that guild")
    @RolePermission(Roles.MILCOM)
    @CoalitionPermission(Coalition.MANAGE_CONFLICTS)
    public String featureConflicts(ConflictManager manager, @Me GuildDB db, @Default Set<Conflict> conflicts, @Default Guild guild) {
        if (conflicts == null && guild == null) {
            throw new IllegalArgumentException("Please specify either `conflicts` or `guild`");
        }
        List<String> messages = new ArrayList<>();
        if (conflicts != null) {
            for (Conflict conflict : conflicts) {
                manager.addSource(db.getIdLong(), conflict.getId(), 0);
            }
            messages.add("Added " + conflicts.size() + " conflicts to the featured list");
        }
        if (guild != null) {
            manager.addSource(db.getIdLong(), guild.getIdLong(), 1);
            messages.add("Added all conflicts from " + guild.toString() + " to the featured list");
        }
        manager.pushIndex();
        messages.add("Pushed changed to site index");
        return StringMan.join(messages, "\n");
    }

    @Command(desc = "Configure the guilds and conflict ids that will be featured by this guild\n" +
            "By default all conflicts are featured\n" +
            "Specify either a set of conflicts, or a guild to feature all conflicts from that guild")
    @RolePermission(Roles.MILCOM)
    @CoalitionPermission(Coalition.MANAGE_CONFLICTS)
    public String removeFeature(ConflictManager manager, @Me GuildDB db, @Default Set<Conflict> conflicts, @Default Guild guild) {
        if (conflicts == null && guild == null) {
            throw new IllegalArgumentException("Please specify either `conflicts` or `guild`");
        }
        List<String> messages = new ArrayList<>();
        if (conflicts != null) {
            for (Conflict conflict : conflicts) {
                manager.removeSource(db.getIdLong(), conflict.getId(), 0);
            }
            messages.add("Removed " + conflicts.size() + " conflicts from the featured list");
        }
        if (guild != null) {
            manager.removeSource(db.getIdLong(), guild.getIdLong(), 1);
            messages.add("Removed all conflicts from " + guild.toString() + " from the featured list");
        }
        manager.pushIndex();
        messages.add("Pushed changed to site index");
        messages.add("See: " + CM.conflict.featured.list_rules.cmd.toSlashMention());
        return StringMan.join(messages, "\n");
    }

    @Command(desc = "List the ruleset for which conflicts are featured by this guild (if any are set)\n" +
            "This consists of a list of either guilds that created the conflict, or individual conflicts")
    @CoalitionPermission(Coalition.MANAGE_CONFLICTS)
    public String listFeaturedRuleset(ConflictManager manager, @Me GuildDB db) {
        List<Long> config = manager.getSourceSets().get(db.getIdLong());
        if (config == null || config.isEmpty()) {
            return "This guild has no customized featured conflict ruleset. All conflicts are featured by default\n" +
                    "Use " + CM.conflict.featured.list_rules.cmd.toSlashMention();
        }

        List<String> items = new ArrayList<>();

        for (long id : config) {
            String name;
            if (id > Long.MAX_VALUE) {
                GuildDB guild = Locutus.imp().getGuildDB(id);
                name = guild == null ? "guild:" + id : guild.getGuild().toString();
            } else {
                Conflict conflict = manager.getConflictById((int) id);
                name = conflict == null ? "conflict:" + id : conflict.getName() + "/" + id;
            }
            items.add(name);
        }
        return "Featured conflicts:\n" + StringMan.join(items, "\n");
    }

    @Command(desc = "Purge permenent conflicts that aren't in the database")
    @RolePermission(Roles.MILCOM)
    @CoalitionPermission(Coalition.MANAGE_CONFLICTS)
    public String purgeFeatured(ConflictManager manager, @Me IMessageIO io, @Me JSONObject command, @Default @Timestamp Long olderThan, @Switch("f") boolean force) {
        Set<String> deleted = new LinkedHashSet<>();
        Set<String> kept = new LinkedHashSet<>();
        for (S3ObjectSummary object : manager.getAws().getObjects()) {
            Date date = object.getLastModified();
            if (olderThan != null && olderThan < date.getTime()) {
                continue;
            }
            String key = object.getKey();
            boolean matches = key.matches("conflicts/graphs/[0-9]+\\.gzip") || key.matches("conflicts/[0-9]+\\.gzip");
            if (!matches) continue;
            int id = Integer.parseInt(key.replaceAll("[^0-9]", ""));
            Conflict conflict = manager.getConflictById(id);
            if (conflict == null) {
                deleted.add(id + "");
                if (force) {
                    manager.getAws().deleteObject(key);
                }
            } else {
                kept.add(id + "");
            }
        }
        if (deleted.isEmpty()) {
            if (kept.isEmpty()) {
                return "No featured conflicts found on the website matching the database";
            } else {
                return "No featured conflicts found on the website missing from the database\n" +
                        "Kept: " + StringMan.join(kept, ", ");
            }
        }
        if (force) {
            return "**Deleted:**\n- " + StringMan.join(deleted, "\n- ") + "\n" +
                    "**Kept:**\n- " + StringMan.join(kept, "\n- ");
        } else {
            StringBuilder body = new StringBuilder();
            body.append("Deleted " + deleted.size() + " conflicts\n");
            body.append("Kept: " + kept.size() + "/" + manager.getConflictMap().size() + "\n");
            io.create().confirmation("Deleted " + deleted.size() + " conflicts", body.toString(), command)
                    .file("deleted.txt", StringMan.join(deleted, "\n"))
                    .send();
            return null;
        }
    }

    @Command(desc = "Purge permenent conflicts that aren't in the database")
    @RolePermission(Roles.MILCOM)
    @CoalitionPermission(Coalition.MANAGE_CONFLICTS)
    public String addAnnouncement(ConflictManager manager, Conflict conflict, String url, @Default String desc) throws SQLException, IOException {
        String urlPattern = "https://forum\\.politicsandwar\\.com/index\\.php\\?/topic/[0-9]+-.+/";
        if (!url.matches(urlPattern)) {
            return "Invalid URL format. Please provide a URL in the format: https://forum.politicsandwar.com/index.php?/topic/{topicId}-{topicUrlStub}/";
        }
        String[] urlParts = url.split("/");
        String topicInfo = urlParts[urlParts.length - 1]; // This should be topicId-topicUrlStub
        String[] topicParts = topicInfo.split("-", 2);
        int topicId = Integer.parseInt(topicParts[0]);
        String topicUrlStub = topicParts[1];

        List<String> msgs = new ArrayList<>();
        DBTopic topic = Locutus.imp().getForumDb().loadTopic(topicId, topicUrlStub);
        if (conflict.hasAnnouncement(topic.topic_id)) {
            conflict.deleteAnnouncement(topic.topic_id);
            msgs.add("Deleted previous announcement for " + topic.topic_name);
        }
        conflict.addAnnouncement(desc == null ? topic.topic_name : desc, topic, true);
        msgs.add("Added announcement for " + topic.topic_name + "/" + topic.topic_id + " at " + DiscordUtil.timestamp(topic.timestamp, null));
        conflict.push(manager, null, false, false);
        return StringMan.join(msgs, "\n");
    }

    @Command(desc = "Purge permenent conflicts on the website that aren't in the database")
    @RolePermission(Roles.MILCOM)
    @CoalitionPermission(Coalition.MANAGE_CONFLICTS)
    public String purgeTemporaryConflicts(ConflictManager manager, @Me IMessageIO io, @Me JSONObject command, @Timestamp long olderThan, @Switch("f") boolean force) {
        List<String> deleted = new ArrayList<>();
        List<String> kept = new ArrayList<>();
        for (S3ObjectSummary object : manager.getAws().getObjects()) {
            String key = object.getKey();
            boolean matches = key.matches("conflicts/n/[0-9]+/[a-z0-9-]+\\.gzip") || key.matches("conflicts/graphs/n/[0-9]+/[a-z0-9-]+\\.gzip");
            if (!matches) continue;

            int nationId = Integer.parseInt(key.replaceAll("[^0-9]", ""));
            String uuidStr = key.substring(key.lastIndexOf("/") + 1, key.lastIndexOf("."));
            String nameStr = PW.getMarkdownUrl(nationId, false) + "/" + uuidStr;
            Date date = object.getLastModified();
            if (olderThan < date.getTime()) {
                kept.add(nameStr);
                continue;
            }
            deleted.add(nameStr);
            if (force) {
                manager.getAws().deleteObject(key);
            }
        }
        if (deleted.isEmpty()) {
            if (kept.isEmpty()) {
                return "No featured conflicts found on the website matching the database";
            } else {
                return "No featured conflicts found on the website missing from the database\n" +
                        "Kept: " + StringMan.join(kept, ", ");
            }
        }
        if (force) {
            return "**Deleted:**\n- " + StringMan.join(deleted, "\n- ") + "\n" +
                    "**Kept:**\n- " + StringMan.join(kept, "\n- ");
        } else {
            StringBuilder body = new StringBuilder();
            body.append("Deleted " + deleted.size() + " conflicts\n");
            body.append("Kept: " + kept.size() + "/" + manager.getConflictMap().size() + "\n");
            io.create().confirmation("Deleted " + deleted.size() + " conflicts", body.toString(), command)
                    .file("deleted.txt", StringMan.join(deleted, "\n"))
                    .send();
            return null;
        }
    }

    @Command(desc = "Import from an external database file\n" +
            "Requires a restart of the bot to complete the import")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String importExternal(ConflictManager manager, String fileLocation, @Me IMessageIO io) throws SQLException {
        File file = new File(fileLocation);
        if (!file.exists()) throw new IllegalArgumentException("File does not exist");
        if (!file.getName().equalsIgnoreCase("war.db")) {
            throw new IllegalArgumentException("File must be named `war.db`");
        }
        CompletableFuture<IMessageBuilder> msgFuture = io.send("Importing from " + file.getName() + "...");
        manager.importFromExternal(file);
        return "Done! A restart is required to load the new data.";
    }
    @Command(desc = "Add all wars declared from none to a conflict if the opposing alliance matches a conflict's participants")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String addManualWars(ConflictManager manager, Conflict conflict, DBNation nation, DBAlliance mark_as_alliance) {
        long start = conflict.getStartMS();
        long end = conflict.getEndMS();
        Set<DBWar> wars = Locutus.imp().getWarDb().getWarsByNationMatching(nation.getId(), f -> f.getDate() >= start && f.getDate() <= end);

        CoalitionSide side1 = conflict.getSide(true);
        CoalitionSide side2 = conflict.getSide(false);
        CoalitionSide markSide = null;
        if (side1.hasAlliance(mark_as_alliance.getId())) {
            markSide = side1;
        } else if (side2.hasAlliance(mark_as_alliance.getId())) {
            markSide = side2;
        }
        if (markSide == null) {
            throw new IllegalArgumentException("Alliance " + mark_as_alliance.getMarkdownUrl() + " is not a participant in the conflict");
        }
        CoalitionSide other = markSide == side1 ? side2 : side1;

        List<DBWar> toAdd = new ObjectArrayList<>();
        for (DBWar war : wars) {
            if (war.getAttacker_aa() == 0 && war.getDefender_aa() == 0) continue;
            if (war.getAttacker_aa() != 0 && war.getDefender_aa() != 0) continue;
            int aaId = war.getAttacker_aa() == 0 ? war.getDefender_aa() : war.getAttacker_aa();
            if (!other.hasAlliance(aaId)) continue;
            toAdd.add(war);
        }
        if (toAdd.isEmpty()) {
            throw new IllegalArgumentException("No wars found to add");
        }
        manager.addManualWar(conflict.getId(), toAdd, mark_as_alliance.getId());
        return "Added " + toAdd.size() + " wars to the conflict.\n" +
                "Note: this does not push the data to the site\n" +
                "See: " + CM.conflict.sync.website.cmd.toSlashMention();
    }
}
