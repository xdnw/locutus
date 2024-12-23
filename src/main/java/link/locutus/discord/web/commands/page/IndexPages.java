package link.locutus.discord.web.commands.page;

import com.google.gson.Gson;
import gg.jte.generated.precompiled.JteindexGenerated;
import gg.jte.generated.precompiled.auth.JteunregisterGenerated;
import gg.jte.generated.precompiled.auth.JtenationpickerGenerated;
import gg.jte.generated.precompiled.auth.JtelogoutGenerated;
import gg.jte.generated.precompiled.command.JteguildindexGenerated;
import gg.jte.generated.precompiled.auth.JtenationpickedGenerated;
import gg.jte.generated.precompiled.auth.JtepickerGenerated;
import gg.jte.generated.precompiled.command.JtesearchGenerated;
import gg.jte.generated.precompiled.guild.JteguildsGenerated;
import gg.jte.generated.precompiled.guild.JtememberindexGenerated;
import io.javalin.http.HandlerType;
import io.javalin.http.HttpStatus;
import io.javalin.http.RedirectResponse;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.WebStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.annotation.NoForm;
import link.locutus.discord.commands.manager.v2.command.CommandGroup;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.commands.manager.v2.command.ArgumentStack;
import link.locutus.discord.commands.manager.v2.command.CommandCallable;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.announce.Announcement;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.task.ia.IACheckup;
import link.locutus.discord.util.task.war.WarCard;
import link.locutus.discord.web.WebUtil;
import link.locutus.discord.web.commands.binding.AuthBindings;
import link.locutus.discord.web.commands.binding.DBAuthRecord;
import link.locutus.discord.web.commands.search.SearchResult;
import link.locutus.discord.web.commands.search.SearchType;
import link.locutus.discord.web.jooby.PageHandler;
import link.locutus.discord.web.jooby.WebRoot;
import link.locutus.discord.config.Settings;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AbstractCursor;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import io.javalin.http.Context;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.text.WordUtils;

import java.io.IOException;
import java.net.URLDecoder;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class IndexPages extends PageHelper {
    @Command
    @NoForm
    public Object index(WebStore ws, Context context, @Me @Default DBAuthRecord auth) throws IOException {
        if (auth == null) {
            String discordAuthUrl = AuthBindings.getDiscordAuthUrl();
            String mailAuthUrl = WebRoot.REDIRECT + "/page/login?nation";
            return WebStore.render(f -> JtepickerGenerated.render(f, null, ws, discordAuthUrl, mailAuthUrl));
        }
        return WebStore.render(f -> JteindexGenerated.render(f, null, ws));
    }

    @Command
    @NoForm
    public Object search(WebStore ws, String term) {
        term = URLDecoder.decode(term).trim();

        double nameEquals = 100;
        double nameContains = 50;
        double descContainsWord = 20;
        double descContainsText = 10;

        String termLow = term.toLowerCase();

        String urlBase = WebRoot.REDIRECT + "/";

        List<SearchResult> results = new ArrayList<>();

        BiConsumer<String, CommandCallable> cmdSearch = new BiConsumer<String, CommandCallable>() {
            @Override
            public void accept(String cmdPrefix, CommandCallable callable) {
                double val = 0;
                for (String alias : callable.aliases()) {
                    if (alias.equals(termLow)) {
                        val = nameEquals;
                        break;
                    }
                    if (alias.contains(termLow)) {
                        val = Math.max(val, nameContains - Math.abs(termLow.length() - alias.length()));
                    }
                }
                {
                    int count = StringMan.countWords(callable.simpleDesc() + " " + callable.simpleHelp(), termLow);
                    if (count > 0) val = Math.max(val, count + descContainsWord);
                }
                if (val == 0) {
                    int count = StringUtils.countMatches(callable.simpleDesc() + callable.simpleHelp(), termLow);
                    if (count > 0) val = Math.max(val, count + descContainsText);
                }

                if (val > 0) {
                    String url = cmdPrefix + callable.getFullPath("/");

                    StringBuilder body = new StringBuilder();
                    body.append(callable.simpleDesc());

                    results.add(new SearchResult(callable.getFullPath(" "),
                            callable.simpleDesc(),
                            url,
                            val,
                            SearchType.COMMAND));
                }
            }
        };

        recursive(Locutus.imp().getCommandManager().getV2().getCommands(), f -> cmdSearch.accept(urlBase + "command/", f));
        recursive(WebRoot.getInstance().getPageHandler().getCommands(), f -> cmdSearch.accept(urlBase + "page/", f));


        for (DBNation nation : Locutus.imp().getNationDB().getAllNations()) {
            double val = 0;
            if ((nation.getNation_id() + "").equals(termLow)) {
                val = 200;
            } else if (nation.getNation().equalsIgnoreCase(termLow)) {
                val = nameEquals;
            } else if (nation.getLeader().equalsIgnoreCase(termLow)) {
                val = nameEquals - 0.5;
            } else if (nation.getVm_turns() == 0 && nation.active_m() < 10000 && termLow.length() > 3) {
                if (nation.getNation().toLowerCase().contains(termLow)) {
                    val = nameContains - Math.abs(termLow.length() - nation.getNation().length());
                } else if (nation.getLeader().toLowerCase().contains(termLow)) {
                    val = nameContains - Math.abs(termLow.length() - nation.getLeader().length());
                }
            }

            if (val > 0) {
                String url = nation.getUrl();
                String body = "leader: " + nation.getLeader() + "<br>" + MarkupUtil.markdownToHTML(nation.toMarkdown());
                results.add(new SearchResult(nation.getNation(),
                        body,
                        url,
                        val,
                        SearchType.NATION));
            }
        }

        for (DBAlliance alliance : Locutus.imp().getNationDB().getAlliances(false, false, false, 9999)) {
            double val = 0;
            String name = alliance.getName();
            if ((alliance.getId() + "").equals(termLow)) {
                val = 200;
            } else if (name.equalsIgnoreCase(termLow)) {
                val = nameEquals;
            } else if (name.replaceAll("[a-z ]", "").equalsIgnoreCase(termLow)) {
                val = nameEquals - 0.5;
            } else if (WordUtils.capitalizeFully(name).replaceAll("[a-z ]", "").equalsIgnoreCase(termLow)) {
                val = nameEquals - 0.6;
            } else if (WordUtils.capitalizeFully(name).replaceAll("The", "").replaceAll("[a-z ]", "").equalsIgnoreCase(termLow)) {
                val = nameEquals - 0.7;
            } else if (name.startsWith(termLow)) {
                val = nameEquals - 0.8;
            } else if (termLow.length() > 3 && name.toLowerCase().contains(termLow)) {
                val = nameContains - Math.abs(termLow.length() - name.length());
            }

            if (val > 0) {
                String url = alliance.getUrl();
                StringBuilder body = new StringBuilder();
                Set<DBNation> nations = alliance.getNations(true, 0, true);
                DBNation total = alliance.getMembersTotal();
                body.append("Nations: " + nations.size()).append("<br>");
                body.append("Score: " + total.getScore()).append("<br>");
                body.append("Cities: " + total.getCities()).append("<br>");
                results.add(new SearchResult(name,
                        body.toString(),
                        url,
                        val,
                        SearchType.ALLIANCE));
            }
        }


        Collections.sort(results, (o1, o2) -> Double.compare(o2.match - o2.type.ordinal(), o1.match - o1.type.ordinal()));

        PageHandler pageHandler = WebRoot.getInstance().getPageHandler();
//        pageHandler.getCommands()
        String finalTerm = term;
        return WebStore.render(f -> JtesearchGenerated.render(f, null, ws, finalTerm, results));
    }

    private void recursive(CommandCallable root, Consumer<CommandCallable> onEach) {
        onEach.accept(root);
        if (root instanceof CommandGroup){
            CommandGroup group = (CommandGroup) root;
            for (CommandCallable cmd : new HashSet<>(group.getSubcommands().values())) {
                recursive(cmd, onEach);
            }
        }
    }

    @Command()
    @NoForm
    public Object guildindex(WebStore ws, @Me GuildDB db, @Me User user, ArgumentStack stack) {
        CommandGroup commands = Locutus.imp().getCommandManager().getV2().getCommands();
        CommandGroup pages = WebRoot.getInstance().getPageHandler().getCommands();

        StringBuilder result = new StringBuilder();
        String cmdEndpoint = WebRoot.REDIRECT + "/command/";
        String pageEndpoint = WebRoot.REDIRECT + "/";

        result.append(
                commands.toHtml(ws, stack.getPermissionHandler(), cmdEndpoint, false)
        );
        result.append(
                pages.toHtml(ws, stack.getPermissionHandler(), pageEndpoint, false)
        );

        return WebStore.render(f -> JteguildindexGenerated.render(f, null, ws, stack.getPermissionHandler(), cmdEndpoint, commands, pageEndpoint, pages));
    }

    @Command()
    @NoForm
    public Object register(WebStore ws, Context context, @Default @Me User user) throws IOException {
        if (user == null) {
            return PageHelper.redirect(ws, context, AuthBindings.getDiscordAuthUrl(), false);
        }
        DBAuthRecord auth = AuthBindings.getAuth(ws, context, true, true, true);
        return WebStore.render(f -> JteunregisterGenerated.render(f, null, ws));
    }

    @Command()
    @NoForm
    public Object login_mail(WebStore ws, Context context, @Default Integer nationId, @Default Integer allianceId) throws IOException {
        DBNation nation = nationId == null ? null : DBNation.getById(nationId);
        if (context.method() != HandlerType.POST || nation == null) {
            List<String> errors = null;
            if (nationId != null) {
                if (nation != null) {
                    return WebStore.render(f -> JtenationpickedGenerated.render(f, null, ws, nation));
                }
                errors = List.of("nation: " + nationId + " not found");
            }
            Set<Integer> allianceIdFilter = allianceId == null ? null : Set.of(allianceId);
            return AuthBindings.nationPicker(ws, errors, allianceIdFilter);
        }
        String mailUrl = WebUtil.mailLogin(nation, true,true);
        throw new RedirectResponse(HttpStatus.SEE_OTHER, mailUrl);
    }

    @Command()
    @NoForm
    public Object login(WebStore ws, Context context, @Default @Me GuildDB current, @Default @Me User user, @Default @Me DBNation nation, @Default String token) throws IOException {
        Map<String, List<String>> queries = context.queryParamMap();
        boolean requireNation = queries.containsKey("nation");
        boolean requireUser = queries.containsKey("discord");
        DBAuthRecord auth = AuthBindings.getAuth(ws, context, true, requireNation, requireUser);
        if (auth != null) {
            String url = AuthBindings.getRedirect(context, true);
            if (url != null) {
                return PageHelper.redirect(ws, context, url, true);
            }
            Map<String, Object> result = new HashMap<>();
            if (nation != null) {
                result.put("nation", nation.getId());
            }
            if (user != null) {
                result.put("discord", user.getId());
            }
            if (current != null) {
                result.put("guild", current.getId());
            }
            throw new RedirectResponse(HttpStatus.SEE_OTHER, url);
//            return WebUtil.GSON.toJson(result);
        } else {
            Map<String, String> result = new HashMap<>();
            result.put("error", "You are not logged in, add `nation` or `discord` to the query string to require login");
            return WebUtil.GSON.toJson(result);
        }
    }

    @Command()
    public Object setguild(WebStore ws, Context context, Guild guild) {
        AuthBindings.setGuild(context, guild);
        return PageHelper.redirect(ws, context, AuthBindings.getRedirect(context, true), false);
    }

    @Command()
    @NoForm
    public Object guildselect(WebStore ws, Context context, ValueStore store, @Default @Me GuildDB current, @Default @Me User user, @Default @Me DBNation nation) throws IOException {
        if (user == null && nation == null) {
            String discordAuthUrl = AuthBindings.getDiscordAuthUrl();
            String mailAuthUrl = WebRoot.REDIRECT + "/page/login?nation";
            return WebStore.render(f -> JtepickerGenerated.render(f, null, ws, discordAuthUrl, mailAuthUrl));
        }
        JDA jda = Locutus.imp().getDiscordApi().getApis().iterator().next();
        String registerLink = (user == null || nation == null) ? CM.register.cmd.toCommandUrl() : null;
        String locutusInvite = null;
        String joinLink = nation != null && nation.getAlliance_id() == 0 ? Settings.INSTANCE.PNW_URL() + "/alliances/" : null;

        Set<GuildDB> dbs = new LinkedHashSet<>();

        if (current != null) {
            dbs.add(current);
        }

        GuildDB allianceDb = null;

        if (nation != null) {
            DBAlliance alliance = nation.getAlliance();
            if (alliance != null) {
                allianceDb = alliance.getGuildDB();
                if (allianceDb != null) {
                    dbs.add(allianceDb);
                    locutusInvite = jda.getInviteUrl(Permission.ADMINISTRATOR);
                }
            }
        }
        if (user != null) {
            for (Guild guild : user.getMutualGuilds()) {
                GuildDB db = Locutus.imp().getGuildDB(guild);
                dbs.add(db);
            }
        }
        GuildDB finalAllianceDb = allianceDb;
        String finalLocutusInvite = locutusInvite;
        return WebStore.render(f -> JteguildsGenerated.render(f, null, ws, dbs, current, finalAllianceDb, registerLink, finalLocutusInvite, joinLink));
    }

    @Command()
    public Object logout(WebStore ws, Context context, @Me @Default DBAuthRecord auth) throws IOException {
        if (auth == null) {
            return "You are not logged in";
        }
        if (context.method() != HandlerType.POST) {
            return WebStore.render(f -> JtelogoutGenerated.render(f, null, ws, auth));
        } else {
            AuthBindings.logout(ws, context, auth, true);
            return null;
        }
    }

    @Command
    @NoForm
    public Object unregister(WebStore ws, Context context, @Me @Default DBAuthRecord auth) throws IOException {
        if (auth == null) {
            return "You are not logged in";
        }
        if (context.method() != HandlerType.POST) {
            return WebStore.render(f -> JteunregisterGenerated.render(f, null, ws));
        } else {
            Integer nationId = auth.getNationId();
            Long userId = auth.getUserId();
            if (nationId == null || userId == null) {
                return "You are not registered. On discord, use: " + CM.register.cmd.toString();
            }
            Locutus.imp().getDiscordDB().unregister(nationId, userId);
            Locutus.imp().getDiscordDB().deleteApiKeyPairByNation(nationId);
            AuthBindings.logout(ws, context, auth, true);
            return "Unregistering. If you are not redirected, please visit <a href=\"" + WebRoot.REDIRECT + "\">" + WebRoot.REDIRECT + "</a>";
        }
    }

    @Command()
    @RolePermission(Roles.MEMBER)
    @NoForm
    public Object guildMemberIndex(WebStore ws, @Me Guild guild, @Me GuildDB db, @Me DBNation me, @Me User author, @Default DBNation nation) throws IOException {
        if (nation == null) nation = me;
        if (nation.getNation_id() != me.getNation_id() && !Roles.INTERNAL_AFFAIRS_STAFF.has(author, guild) && !Roles.MILCOM.has(author, guild)) {
            return "You do not have permission to view another nations page";
        }
        Map<DBWar, DBNation> offensiveWars = new LinkedHashMap<>();
        Map<DBWar, DBNation> defensiveWars = new LinkedHashMap<>();

        Map<DBWar, WarCard> warCards = new HashMap<>();
        Map<DBWar, AttackType> recommendedAttack = new HashMap<>();

        List<DBWar> myWars = new ObjectArrayList<>(nation.getActiveWars());
//        myWars = nation.getWars().subList(0, 5);

        Collections.sort(myWars, Comparator.comparingLong(o -> o.getDate()));
        Collections.reverse(myWars);
        List<AbstractCursor> attacks = myWars.isEmpty() ? Collections.emptyList() : Locutus.imp().getWarDb().getAttacksByWars(myWars);
        boolean isFightingActives = false;

        Collection<JavaCity> cities = myWars.isEmpty() ? null : nation.getCityMap(false, false,false).values();

        for (DBWar war : myWars) {
            List<AbstractCursor> warAttacks = attacks.stream().filter(f -> f.getWar_id() == war.warId).collect(Collectors.toList());
            WarCard warcard = new WarCard(war, warAttacks, false, false, false);
            warCards.put(war, warcard);

            boolean isAttacker = war.isAttacker(nation);
            DBNation other = war.getNation(!isAttacker);

            if (other.active_m() < 1440) {
                isFightingActives = true;
            }

            if (isAttacker) {
                offensiveWars.put(war, other);
            } else {
                defensiveWars.put(war, other);
            }
        }
        Map<IACheckup.AuditType, Map.Entry<Object, String>> checkupResult = new HashMap<>();
        if (db.isWhitelisted() && db.hasAlliance()) {
            try {
                IACheckup checkup = new IACheckup(db, db.getAllianceList(), true);
                checkupResult = checkup.checkup(null, nation, true, true);
                checkupResult.entrySet().removeIf(f -> f.getValue() == null || f.getValue().getValue() == null);
            } catch (InterruptedException | ExecutionException | IOException e) {
                e.printStackTrace();
            }
        }
        double[] deposits = nation.getNetDeposits(db, -1L, true);

        List<Announcement.PlayerAnnouncement> announcements = db.getPlayerAnnouncementsByNation(nation.getNation_id(), true);

        DBNation finalNation = nation;
        Map<IACheckup.AuditType, Map.Entry<Object, String>> finalCheckupResult = checkupResult;
        boolean finalIsFightingActives = isFightingActives;
        return WebStore.render(f -> JtememberindexGenerated.render(f, null, ws, db.getGuild(), db, finalNation, author, deposits, finalCheckupResult, cities, finalIsFightingActives, offensiveWars, defensiveWars, warCards, recommendedAttack, announcements));
    }
//
//    @Command()
//    @RolePermission(Roles.MEMBER)
//    public Object allianceIndex(WebStore ws, @Me User user, int allianceId) {
//        DBAlliance alliance = DBAlliance.getOrCreate(allianceId);
//        GuildDB db = alliance.getGuildDB();
//        Guild guild = db != null ? db.getGuild() : null;
//
//        String url = alliance.getUrl();
//        Set<DBNation> nations = alliance.getNations();
//
//        return WebStore.render(f -> JteallianceindexGenerated.render(f, null, ws, db, guild, alliance, user));
//    }
}