package link.locutus.discord.db;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.core.ApiKeyPool;
import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.apiv1.enums.city.building.Building;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.NationFilter;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.NationPlaceholders;
import link.locutus.discord.commands.war.WarCategory;
import link.locutus.discord.commands.manager.v2.impl.pw.TaxRate;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.entities.*;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.event.Event;
import link.locutus.discord.event.city.CityBuildingChangeEvent;
import link.locutus.discord.event.city.CityCreateEvent;
import link.locutus.discord.event.city.CityInfraBuyEvent;
import link.locutus.discord.event.game.TurnChangeEvent;
import link.locutus.discord.event.nation.*;
import link.locutus.discord.event.guild.NewApplicantOnDiscordEvent;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.event.war.WarPeaceStatusEvent;
import link.locutus.discord.pnw.AllianceList;
import link.locutus.discord.pnw.BeigeReason;
import link.locutus.discord.pnw.CityRanges;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.AlertUtil;
import link.locutus.discord.util.AutoAuditType;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.RateLimitUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.offshore.Grant;
import link.locutus.discord.util.offshore.test.IACategory;
import link.locutus.discord.util.offshore.test.IAChannel;
import link.locutus.discord.util.scheduler.CaughtRunnable;
import link.locutus.discord.util.task.war.WarCard;
import com.google.common.eventbus.Subscribe;
import com.google.gson.JsonObject;
import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AbstractCursor;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.NationColor;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.attribute.ICategorizableChannel;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.guild.invite.GuildInviteCreateEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static link.locutus.discord.pnw.BeigeReason.BEIGE_CYCLE;

public class GuildHandler {
    private final Guild guild;
    private final GuildDB db;
    private final boolean trackInvites;
    private Map<String, Integer> inviteUses = new ConcurrentHashMap<>();
    private Map<String, Boolean> ignoreInvites = new ConcurrentHashMap<>();
    private final Set<Long> ignorePermanentInvitesFrom = new HashSet<>();
    private final Set<Integer> ignoreIncentivesForNations = new HashSet<>();

    public GuildHandler(Guild guild, GuildDB db) {
        this(guild, db, false);
    }

    public GuildHandler(Guild guild, GuildDB db, boolean trackInvites) {
        this.guild = guild;
        this.db = db;
        Locutus.imp().getExecutor().submit(() -> {
            if (db.isWhitelisted()) setupApplicants();
        });
        this.trackInvites = trackInvites;
        if (trackInvites) {
            db.getGuild().retrieveInvites().queue(new Consumer<List<Invite>>() {
                @Override
                public void accept(List<Invite> invites) {
                    for (Invite invite : invites) {
                        addInvite(invite);
                    }

                }
            });
        }
    }

    @Subscribe
    public void testEvent(String evnet) {
        System.out.println("Guild " + guild + " received " + evnet);
    }

    public void onGuildInviteCreate(GuildInviteCreateEvent event) {
        addInvite(event.getInvite());
    }

    private void addInvite(Invite invite) {
        if (ignorePermanentInvitesFrom.contains(invite.getInviter().getIdLong())) {
            if (invite.getMaxUses() <= 0) ignoreInvites.put(invite.getCode(), true);
        }
        inviteUses.put(invite.getCode(), invite.getUses());
    }

    public void ignoreInviteFromUser(long user) {
        ignorePermanentInvitesFrom.add(user);
    }

    public void ignoreIncentiveForNation(int nationId) {
        ignoreIncentivesForNations.add(nationId);
    }

    public void setupApplicants() {
        MessageChannel alertChannel = getDb().getOrNull(GuildKey.INTERVIEW_PENDING_ALERTS);
        if (alertChannel == null) return;

        Role appRole = Roles.APPLICANT.toRole(getGuild());
        if (appRole == null) return;

        List<Member> members = getGuild().getMembersWithRoles(appRole);

        for (Member member : members) {
            ByteBuffer meta = getDb().getMeta(member.getIdLong(), NationMeta.DISCORD_APPLICANT);
            if (meta == null) {
                newApplicantOnDiscord(member.getUser());
            }
        }
    }

    public Guild getGuild() {
        return guild;
    }

    public GuildDB getDb() {
        return db;
    }

    public Map<DBNation, Map.Entry<TaxBracket, String>> setNationTaxBrackets(Set<DBNation> nations, Consumer<String> responses) throws Exception {
        return setNationTaxBrackets(nations, db.getOrThrow(GuildKey.REQUIRED_TAX_BRACKET), responses);
    }

    public Map<DBNation, Map.Entry<TaxBracket, String>> setNationTaxBrackets(Set<DBNation> nations, Map<NationFilter, Integer> requiredTaxBracket, Consumer<String> responses) throws Exception {
        Set<Integer> aaIds = db.getAllianceIds();
        nations.removeIf(f -> f.isGray() || f.getPosition() <= 1 || f.isBeige() || !aaIds.contains(f.getAlliance_id()) || f.getVm_turns() > 0);

        AllianceList allianceList = db.getAllianceList();

        Map<Integer, TaxBracket> brackets = allianceList.getTaxBrackets(false);
        Map<DBNation, TaxBracket> bracketsByNation = new HashMap<>();

        Map<DBNation, Map.Entry<TaxBracket, String>> nationsMovedBracket = new HashMap<>();

        for (Map.Entry<Integer, TaxBracket> entry : brackets.entrySet()) {
            TaxBracket bracket = entry.getValue();
            Set<DBNation> bracketNations = bracket.getNations();
            for (DBNation nation : bracketNations) {
                bracketsByNation.put(nation, bracket);
            }
        }

        for (DBNation nation : nations) {
            TaxBracket current = bracketsByNation.get(nation);

            String reason = null;
            TaxBracket required = null;
            for (Map.Entry<NationFilter, Integer> entry : requiredTaxBracket.entrySet()) {
                if (entry.getKey().test(nation)) {
                    Integer requiredId = entry.getValue();
                    reason = entry.getKey().getFilter();
                    required = brackets.get(requiredId);
                    break;
                }
            }
            if (required != null && (current == null || current.taxId != required.taxId)) {
                String response = allianceList.setTaxBracket(required, nation);
                responses.accept(nation.getNation() + ": " + response);
                nationsMovedBracket.put(nation, new AbstractMap.SimpleEntry<>(required, reason));
                Locutus.imp().getNationDB().markNationDirty(nation.getId());
            }
        }
        Locutus.imp().runEventsAsync(Locutus.imp().getNationDB()::updateDirtyNations);
        return nationsMovedBracket;
    }

    public Map<DBNation, Map.Entry<TaxRate, String>> setNationInternalTaxRate(Set<DBNation> nations, Consumer<String> responses) throws Exception {
        return setNationInternalTaxRate(nations, db.getOrThrow(GuildKey.REQUIRED_INTERNAL_TAXRATE), responses);
    }

    public Map<DBNation, Map.Entry<TaxRate, String>> setNationInternalTaxRate(Set<DBNation> nations, Map<NationFilter, TaxRate> requiredTaxRates, Consumer<String> responses) throws Exception {
        Set<Integer> aaIds = db.getAllianceIds();
        nations.removeIf(f -> f.isGray() || f.getPosition() <= 1 || f.isBeige() || !aaIds.contains(f.getAlliance_id()) || f.getVm_turns() > 0);

        Map<DBNation, Map.Entry<TaxRate, String>> nationsMovedRate = new HashMap<>();

        for (DBNation nation : nations) {
            TaxRate current = getInternalTaxrate(nation.getNation_id());

            String reason = null;
            TaxRate required = null;
            for (Map.Entry<NationFilter, TaxRate> entry : requiredTaxRates.entrySet()) {
                if (entry.getKey().test(nation)) {
                    reason = entry.getKey().getFilter();
                    required = entry.getValue();
                    break;
                }
            }
            if (required != null && (current == null || current.money != required.money || current.resources != required.resources)) {
                getDb().setInternalTaxRate(nation, required);
                nationsMovedRate.put(nation, new AbstractMap.SimpleEntry<>(required, reason));
            }
        }
        return nationsMovedRate;
    }

    public void onGuildMemberRoleAdd(GuildMemberRoleAddEvent event) {
        Guild guild = event.getGuild();
        GuildDB db = Locutus.imp().getGuildDB(guild);

        MessageChannel alertChannel = db.getOrNull(GuildKey.INTERVIEW_PENDING_ALERTS);
        if (alertChannel == null) return;

        List<Role> roles = event.getRoles();
        Role appRole = Roles.APPLICANT.toRole(guild);
        if (!roles.contains(appRole)) return;

        newApplicantOnDiscord(event.getUser());
    }

    public void newApplicantOnDiscord(User author) {
        newApplicantOnDiscord(author, true);
    }

    public boolean newApplicantOnDiscord(User author, boolean sendDM) {
        new NewApplicantOnDiscordEvent(getGuild(), author).post();

        Guild guild = getGuild();
        GuildDB db = getDb();
        Set<Integer> aaIds = db.getAllianceIds();

        MessageChannel alertChannel = db.getOrNull(GuildKey.INTERVIEW_PENDING_ALERTS);
        if (alertChannel == null) return false;

        db.setMeta(author.getIdLong(), NationMeta.DISCORD_APPLICANT, new byte[]{1});

        Role interviewerRole = Roles.INTERVIEWER.toRole(guild);
        if (interviewerRole == null) interviewerRole = Roles.MENTOR.toRole(guild);
        if (interviewerRole == null) interviewerRole = Roles.INTERNAL_AFFAIRS_STAFF.toRole(guild);
        if (interviewerRole == null) interviewerRole = Roles.INTERNAL_AFFAIRS.toRole(guild);

        if (interviewerRole == null) return false;
        boolean mentionInterviewer = true;

        String title = "New applicant";
        String emoji = "Claim";

        StringBuilder body = new StringBuilder();
        body.append("User: " + author.getAsMention() + "\n");
        DBNation nation = DiscordUtil.getNation(author);
        if (nation != null) {
            if (nation.getActive_m() > 7200) return false;

            body.append("nation: " + MarkupUtil.markdownUrl(nation.getNation(), nation.getNationUrl()) + "\n");
            if (nation.getPosition() > 1 && aaIds.contains(nation.getAlliance_id())) {
                body.append("\n\n**ALREADY MEMBER OF " + nation.getAllianceName() + "**\n\n");
                mentionInterviewer = false;
            }
            if (nation.getAlliance_id() != 0 && !aaIds.contains(nation.getAlliance_id())) {
                Locutus.imp().getNationDB().updateNations(List.of(nation.getNation_id()), Event::post);
                if (nation.getAlliance_id() != 0 && !aaIds.contains(nation.getAlliance_id())) {
                    body.append("\n\n**Already member of AA: " + nation.getAllianceName() + "**\n\n");
                    mentionInterviewer = false;
                    RateLimitUtil.queue(RateLimitUtil.complete(author.openPrivateChannel()).sendMessage("As you're already a member of another alliance, message or ping @" + interviewerRole.getName() + " to (proceed"));
                } else {
                    RateLimitUtil.queue(RateLimitUtil.complete(author.openPrivateChannel()).sendMessage("Thank you for applying. People may be busy with irl things, so please be patient. An IA representative will proceed with your application as soon as they are able."));
                }
            }
        }

        body.append("The first on the trigger, react with the " + emoji + " emoji");

        String pending = "_" + Settings.commandPrefix(true) + "UpdateEmbed 'description:{description}\n" +
                "\n" +
                "Assigned to {usermention} in {timediff}'\n" +
                CM.interview.create.cmd.create(author.getAsMention()).toCommandArgs();

        IMessageBuilder msg = new DiscordChannelIO(alertChannel).create().embed(title, body.toString()).commandButton(pending, emoji);
        if (mentionInterviewer) {
            msg.append("^ " + interviewerRole.getAsMention());
        }
        msg.send();
        return true;
    }

    public boolean onMessageReceived(MessageReceivedEvent event) {
        handleIAMessageLogging(event);
        return true;
    }

    private void handleIAMessageLogging(MessageReceivedEvent event) {
        if (event.isWebhookMessage() || db.getExistingIACategory() == null) return;
        // not bot or system or fake user
        // channel starts with id
        // channel parent starts with `interview`
        // submit task to add to database
        User author = event.getAuthor();
        if (author.isSystem() || author.isBot()) return;

        GuildMessageChannel channel = event.getGuildChannel();
        if (!(channel instanceof ICategorizableChannel)) return;
        Category category = ((ICategorizableChannel) channel).getParentCategory();
        if (category == null || !category.getName().toLowerCase().startsWith("interview")) return;
        if (!db.isWhitelisted()) return;

        long date = event.getMessage().getTimeCreated().toInstant().toEpochMilli();
        db.addInterviewMessage(event.getMessage(), false);
    }

    @Subscribe
    public void onNationColorChange(NationChangeColorEvent event) {
        DBNation previous = event.getPrevious();
        DBNation current = event.getCurrent();

        if (current.getPositionEnum() == Rank.APPLICANT && (previous.isGray() || previous.isBeige()) && !current.isGray() && !current.isBeige()) {
            MessageChannel channel = db.getOrNull(GuildKey.MEMBER_LEAVE_ALERT_CHANNEL);
            if (channel != null) {
                String type = "Applicant changed color from " + previous.getColor() + " to " + current.getColor();
                User user = current.getUser();
                if (user != null) {
                    type += " | " + user.getAsMention();
                }
                String title = type + ": " + current.getNation() + " | " + "" + Settings.INSTANCE.PNW_URL() + "/nation/id=" + current.getNation_id() + " | " + current.getAllianceName();
                AlertUtil.displayChannel(title, current.toString(), channel.getIdLong());
            }
        }
    }

    @Subscribe
    public void onNationChangeRankEvent(NationChangeRankEvent event) {
        DBNation current = event.getCurrent();
        DBNation previous = event.getPrevious();

        if (previous.getAlliance_id() == current.getAlliance_id() &&
                previous.getPositionEnum().id > Rank.APPLICANT.id &&
                current.getPositionEnum() == Rank.APPLICANT) {

            MessageChannel channel = db.getOrNull(GuildKey.MEMBER_LEAVE_ALERT_CHANNEL);
            if (channel != null) {
                String type;
                String title;
                if (current.getVm_turns() == 0 && (current.active_m() < 2880 || (!current.isGray() && !current.isBeige()))) {
                    type = "ACTIVE NATION SET TO APPLICANT";
                    title = type + ": " + current.getNation() + " | " + "" + Settings.INSTANCE.PNW_URL() + "/nation/id=" + current.getNation_id() + " | " + current.getAllianceName();
                } else {
                    if (current.getColor() == NationColor.GRAY) {
                        type = "INACTIVE GRAY NATION SET TO APPLICANT";
                    } else {
                        type = "INACTIVE TAXABLE NATION SET TO APPLICANT";
                    }
                    title = type + ": " + current.getNation() + " | " + "" + Settings.INSTANCE.PNW_URL() + "/nation/id=" + current.getNation_id() + " | " + current.getAllianceName();
                }
                String message = "**" + title + "**\n" + current.toString() + "\n";
                RateLimitUtil.queueMessage(channel, message, true, 60);
            }
        }
    }

    @Subscribe
    public void onNationChangeColor(NationChangeColorEvent event) {
        DBNation current = event.getCurrent();
        DBNation previous = event.getPrevious();

        if (current.getPositionEnum().id > Rank.APPLICANT.id && current.isGray() && !previous.isGray() && current.active_m() < 10000) {
            String extra = (current.isGray()) ? "" : ", set your color to match the alliance ";

            AlertUtil.auditAlert(current, AutoAuditType.GRAY, f ->
                    "Please go to <https://politicsandwar.com/nation/edit/>" + extra + " and click save (so that you receive color trade bloc revenue)"
            );
        }
    }

//    @Subscribe
//    public void onCityCreate(CityCreateEvent event) {
//        DBNation nation = event.getNation();
//        if (nation != null) {
//            // Auto role
//            User user = nation.getUser();
//            if (user != null) {
//                Member member = guild.getMember(user);
//                if (member != null) {
//                    db.getAutoRoleTask().autoRoleCities(member, nation);
//                }
//            }
//        }
//    }

    @Subscribe
    public void onNationChangeCities(NationChangeCitiesEvent event) {
        DBNation nation = event.getCurrent();
        if (nation.getPositionEnum().id > Rank.APPLICANT.id) {
            User user = nation.getUser();
            if (user != null) {
                Member member = guild.getMember(user);
                if (member != null) {
                    db.getAutoRoleTask().autoRoleCities(member, nation);
                }
            }
        }
    }

    @Subscribe
    public void onMemberEnterVM(NationChangeVacationEvent event) {
        DBNation previous = event.getPrevious();
        DBNation current = event.getCurrent();

        if (previous.getVm_turns() == 0 && current.getVm_turns() > 0) {
            MessageChannel channel = db.getOrNull(GuildKey.MEMBER_LEAVE_ALERT_CHANNEL);
            if (channel != null) {
                Rank rank = Rank.byId(previous.getPosition());
                String title = previous.getNation() + " (" + rank.name() + ") VM";
                StringBuilder body = new StringBuilder();
                body.append(MarkupUtil.markdownUrl(current.getNation(), current.getNationUrl()));
                body.append("\nActive: " + TimeUtil.secToTime(TimeUnit.MINUTES, current.getActive_m()));
                User user = current.getUser();
                if (user != null) {
                    body.append("\nUser: " + user.getAsMention());
                }
                DiscordUtil.createEmbedCommand(channel, title, body.toString());
            }
        }
    }

    @Subscribe
    public void onMemberLeaveVM(NationLeaveVacationEvent event) {
        DBNation previous = event.getPrevious();
        DBNation current = event.getCurrent();
        if (current != null && current.getActive_m() > 10000) return;

        MessageChannel channel = db.getOrNull(GuildKey.MEMBER_LEAVE_ALERT_CHANNEL);
        if (channel != null) {
            Rank rank = Rank.byId(previous.getPosition());
            String title = previous.getNation() + " (" + rank.name() + ") left VM";
            StringBuilder body = new StringBuilder();
            body.append(MarkupUtil.markdownUrl(current.getNation(), current.getNationUrl()));
            body.append("\nActive: " + TimeUtil.secToTime(TimeUnit.MINUTES, current.getActive_m()));
            User user = current.getUser();
            if (user != null) {
                body.append("\nUser: " + user.getAsMention());
            }
            DiscordUtil.createEmbedCommand(channel, title, body.toString());
        }
    }

    private void onNewApplicant(DBNation current) {
        Set<Integer> aaIds = db.getAllianceIds();
        if (!aaIds.isEmpty()) {

            // New applicant
            if (current.getPositionEnum() == Rank.APPLICANT && aaIds.contains(current.getAlliance_id()) && !aaIds.contains(current.getAlliance_id()) && current.active_m() < 2880) {
                MessageChannel channel = db.getOrNull(GuildKey.MEMBER_LEAVE_ALERT_CHANNEL);
                if (channel != null) {
                    String type = "New Applicant Ingame";
                    User user = current.getUser();
                    if (user != null) {
                        type += " | " + user.getAsMention();
                    }
                    String title = type + ": " + current.getNation() + " | " + "" + Settings.INSTANCE.PNW_URL() + "/nation/id=" + current.getNation_id() + " | " + current.getAllianceName();

                    String message = "**" + title + "**" + "\n" + current.toString() + "\n";
                    RateLimitUtil.queueMessage(channel, message, true, 60);
                }
            }
        }
    }

    @Subscribe
    public void onBuyInfra(CityInfraBuyEvent event) {
        DBNation nation = event.getNation();
        if (nation != null) {

            int threshold = nation.getCities() <= 5 ? 1700 : 1700;
            DBCity previous = event.getPrevious();
            DBCity current = event.getCurrent();

            if (previous.getInfra() <= threshold && current.getInfra() > threshold && (nation.getCities() < 8 || nation.getOff() >= 5)) {
                AlertUtil.auditAlert(nation, AutoAuditType.HIGH_INFRA, (f) -> AutoAuditType.HIGH_INFRA.message + "\n" + current.getUrl());
            }
        }
    }

    @Subscribe
    public void onCityChange(CityBuildingChangeEvent event) {
        DBNation nation = event.getNation();
        if (nation != null) {
            DBCity city = event.getCurrent();
            for (Map.Entry<Building, Integer> entry : event.getChange().entrySet()) {
                Building building = entry.getKey();
                Integer amt = entry.getValue();

                if (amt > 0) {
                    if (building == Buildings.FARM && !nation.hasProject(Projects.MASS_IRRIGATION) && nation.getAvgLand() < 2000) {
                        String msg = AutoAuditType.UNPROFITABLE_FARMS.message;
                        AlertUtil.auditAlert(nation, AutoAuditType.UNPROFITABLE_FARMS, (f) -> " ```" + msg + "```" + "\n" + city.getUrl());
                    } else if (building == Buildings.WIND_POWER && (amt > 1 || (city.getInfra() <= 2000 || city.getInfra() > 2250))) {
                        String msg = AutoAuditType.WIND_POWER.message;
                        AlertUtil.auditAlert(nation, AutoAuditType.WIND_POWER, (f) -> " ```" + msg + "```" + "\n" + city.getUrl());
                    }
                }
            }

        }
    }

    @Subscribe
    public void onNationChangeAlliance(NationChangeAllianceEvent event) {
        DBNation current = event.getCurrent();
        DBNation previous = event.getPrevious();

        onNewApplicant(current);
        autoRoleMemberApp(current);
        Set<Integer> aaIds = db.getAllianceIds();
        if (!aaIds.isEmpty()) {
            if (aaIds.contains(previous.getAlliance_id()) && current.getAlliance_id() != previous.getAlliance_id()) {
                MessageChannel channel = db.getOrNull(GuildKey.MEMBER_LEAVE_ALERT_CHANNEL);
                if (channel != null) {
                    addLeaveMessage(channel, previous, current);
                }
            }
        }
    }

    private void autoRoleMemberApp(DBNation current) {
        if (current == null) return;
        User user = current.getUser();
        if (user == null) return;
        Member member = db.getGuild().getMember(user);
        if (member == null) return;
        try {
            db.getAutoRoleTask().autoRoleMemberApp(member, current);
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
    }

    private void addLeaveMessage(MessageChannel channel, DBNation previous, DBNation current) {
        // Ignore if inactive and no user in discord
        User user = current.getUser();
        Member member = user == null ? null : db.getGuild().getMember(user);
        if (member == null && current.active_m() > 7200) return;

        Rank rank = Rank.byId(previous.getPosition());
        String title = previous.getNation() + " (" + rank.name() + ") left";
        StringBuilder body = new StringBuilder();
        body.append(MarkupUtil.markdownUrl(current.getNation(), current.getNationUrl()));

        body.append("\nActive: " + TimeUtil.secToTime(TimeUnit.MINUTES, current.getActive_m()));
        if (user != null) {
            body.append("\nUser: " + user.getAsMention());
        }

        DiscordChannelIO io = new DiscordChannelIO(channel);

        if (user != null && current.getActive_m() < 2880) {
            try {
                double[] depoTotal = current.getNetDeposits(db, false);
                body.append("\n\nPlease check the following:\n" +
                        "- Discord roles\n" +
                        "- Deposits: `" + PnwUtil.resourcesToString(depoTotal) + "` worth: ~$" + MathMan.format(PnwUtil.convertedTotal(depoTotal)));
            } catch (IOException e) {
                e.printStackTrace();
            }
            String emoji = "Claim";
            String pending = "_" + Settings.commandPrefix(true) + "UpdateEmbed 'description:{description}\n" +
                    "\n" +
                    "Assigned to {usermention} in {timediff}'";

            io.create().embed(title, body.toString())
                    .commandButton(pending, emoji).send();
        } else {
            RateLimitUtil.queueMessage(io, new Function<IMessageBuilder, Boolean>() {
                @Override
                public Boolean apply(IMessageBuilder msg) {
                    msg.embed(title, body.toString());
                    return true;
                }
            }, true, 60);
        }
    }

    @Subscribe
    public void onNationDelete(NationDeleteEvent event) {
        autoRoleMemberApp(event.getPrevious().getUser(), null);

        DBNation previous = event.getPrevious();
        MessageChannel channel = db.getOrNull(GuildKey.MEMBER_LEAVE_ALERT_CHANNEL);
        if (channel != null) {
            Rank rank = Rank.byId(previous.getPosition());
            String title = previous.getNation() + " (" + rank.name() + ") deleted";
            StringBuilder body = new StringBuilder();
            body.append(MarkupUtil.markdownUrl(previous.getNation(), previous.getNationUrl()));
            body.append("\nActive: " + TimeUtil.secToTime(TimeUnit.MINUTES, previous.getActive_m()));
            User user = previous.getUser();
            if (user != null) {
                body.append("\nUser: " + user.getAsMention());
            }
            DiscordUtil.createEmbedCommand(channel, title, body.toString());
        }
    }

    private void autoRoleMemberApp(User user, DBNation nation) {
        if (user == null) return;
        Member member = getGuild().getMember(user);
        if (member == null) return;
        try {
            db.getAutoRoleTask().autoRoleMemberApp(member, nation);
        } catch (RuntimeException ignore) {
            ignore.printStackTrace();
        }
    }

    public Map.Entry<String, String> getRecruitMessagePair(DBNation to) {
        String subject = getDb().getOrThrow(GuildKey.RECRUIT_MESSAGE_SUBJECT);
        String message = getDb().getOrThrow(GuildKey.RECRUIT_MESSAGE_CONTENT);
        return new AbstractMap.SimpleEntry<>(subject, message);
    }

    public JsonObject sendRecruitMessage(DBNation to) throws IOException {
        MessageChannel output = getDb().getOrThrow(GuildKey.RECRUIT_MESSAGE_OUTPUT);
        Map.Entry<String, String> pair = getRecruitMessagePair(to);
        String subject = pair.getKey();
        String message = pair.getValue();
        ApiKeyPool keys = getDb().getMailKey();
        if (keys == null) {
            boolean hasKey = getDb().getOrNull(GuildKey.API_KEY) != null;
            if (!hasKey) {
                throw new IllegalArgumentException("Please set `API_KEY` with " + CM.settings.info.cmd.toSlashMention());
            }
            throw new IllegalArgumentException("No valid `API_KEY` was found. Please ensure a valid one is set with " + CM.settings.info.cmd.toSlashMention());
        }

        NationPlaceholders formatter = Locutus.imp().getCommandManager().getV2().getNationPlaceholders();
        if (message.contains("%") || message.contains("{")) {
            message = formatter.format2(guild, null, null, message, to, false);
        }
        if (subject.contains("%") || subject.contains("{")) {
            subject = formatter.format2(guild, null, null, subject, to, false);
        }

        return to.sendMail(keys, subject, message, false);
    }

    public Double getWithdrawLimit(int banker) {
        ByteBuffer nationLimitBytes = db.getMeta(banker, NationMeta.BANKER_WITHDRAW_LIMIT);
        if (nationLimitBytes != null) {
            return nationLimitBytes.getDouble();
        }
        Long defaultWithdrawLimit = getDb().getOrNull(GuildKey.BANKER_WITHDRAW_LIMIT);
        if (defaultWithdrawLimit != null) {
            return defaultWithdrawLimit.doubleValue();
        }
        return null;
    }

    public void setWithdrawLimit(int banker, double amt) {
        ByteBuffer buf = ByteBuffer.allocate(Double.BYTES);
        buf.putDouble(amt);
        db.setMeta(banker, NationMeta.BANKER_WITHDRAW_LIMIT, buf.array());
    }

    public TaxRate getInternalTaxrate(int nationId) {
        ByteBuffer taxRate = db.getMeta(nationId, NationMeta.TAX_RATE);
        int moneyRate = -1;
        int resourceRate = -1;
        if (taxRate != null) {
            moneyRate = taxRate.get();
            resourceRate = taxRate.get();
        }
        TaxRate taxBase = db.getOrNull(GuildKey.TAX_BASE);
        if (taxBase != null) {
            if (moneyRate == -1) moneyRate = taxBase.money;
            if (resourceRate == -1) resourceRate = taxBase.resources;
        }
        return new TaxRate(moneyRate, resourceRate);
    }

    @Subscribe
    public void onNationActive(NationChangeActiveEvent event) {
        DBNation previous = event.getPrevious();
        DBNation current = event.getCurrent();

        if (previous.active_m() > 7200 && previous.getPositionEnum() == Rank.APPLICANT && current.getVm_turns() == 0 && current.active_m() < 15) {
            MessageChannel channel = db.getOrNull(GuildKey.MEMBER_LEAVE_ALERT_CHANNEL);
            if (channel != null) {
                String title = "Inactive Applicant " + current.getNation() + " logged in (just now)";
                StringBuilder body = new StringBuilder();
                body.append(MarkupUtil.markdownUrl(current.getNation(), current.getNationUrl()));
                body.append("\n").append("Color: " + current.getColor());
                body.append("\n").append("Cities: " + current.getCities());
                body.append("\n").append("MMR: " + current.getMMR());
                body.append("\n").append("Defensive Wars: " + current.getDef());
                body.append("\n").append("VM: " + current.getVm_turns());
                DiscordUtil.createEmbedCommand(channel, title, body.toString());
            }
        }
    }

    public Set<Project> getRecommendedProjects(DBNation nation) {
        if (!nation.hasProject(Projects.INTELLIGENCE_AGENCY)) return Collections.singleton(Projects.INTELLIGENCE_AGENCY);
        if (!nation.hasProject(Projects.PROPAGANDA_BUREAU)) return Collections.singleton(Projects.PROPAGANDA_BUREAU);

        if (nation.getCities() == 11 && !nation.hasProject(Projects.URBAN_PLANNING)) return Collections.singleton(Projects.URBAN_PLANNING);

        if (nation.hasProject(Projects.URBAN_PLANNING) && nation.getCities() == 16 && !nation.hasProject(Projects.ADVANCED_URBAN_PLANNING)) return Collections.singleton(Projects.ADVANCED_URBAN_PLANNING);

        if (nation.getCities() < 11 && !nation.hasProject(Projects.URBAN_PLANNING) && nation.getNumProjects() >= 4) return Collections.singleton(Projects.URBAN_PLANNING);
        if (nation.hasProject(Projects.URBAN_PLANNING) && nation.getCities() < 16 && !nation.hasProject(Projects.ADVANCED_URBAN_PLANNING) && nation.getNumProjects() >= 6) return Collections.singleton(Projects.ADVANCED_URBAN_PLANNING);

        List<Project> resourceProjects = new ArrayList<>(Arrays.asList(
                Projects.IRON_WORKS,
                Projects.EMERGENCY_GASOLINE_RESERVE,
                Projects.BAUXITEWORKS,
                Projects.ARMS_STOCKPILE
        ));
        if (Buildings.URANIUM_MINE.canBuild(nation.getContinent())) {
            resourceProjects.add(Projects.URANIUM_ENRICHMENT_PROGRAM);
        }
        Set<Project> potentialRssProjects = new LinkedHashSet<>();
        int numRss = 0;
        for (Project project : resourceProjects) {
            if (nation.hasProject(project)) numRss++;
            else potentialRssProjects.add(project);
        }

        if (numRss < 2) {
            return potentialRssProjects;
        }

        if (!nation.hasProject(Projects.IRON_DOME)) return Collections.singleton(Projects.IRON_DOME);

        if (!nation.hasProject(Projects.MISSILE_LAUNCH_PAD)) return Collections.singleton(Projects.MISSILE_LAUNCH_PAD);

        return potentialRssProjects;
    }

//    /**
//     * A common set of grants for members (not enabled by default)
//     *
//     * @param nation
//     * @param type
//     * @param overrideSafe
//     * @param overrideUnsafe
//     * @return
//     */
//    public Set<Grant> getBaseEligableGrants(DBNation nation, DepositType type, boolean overrideSafe, boolean overrideUnsafe) {
//        Set<Grant> grants = new HashSet<>();
//
//        User user = nation.getUser();
//        if (user == null) throw new IllegalArgumentException("Nation is not verified: " + CM.register.cmd.toSlashMention() + "");
//        Member member = getGuild().getMember(user);
//        if (member == null) throw new IllegalArgumentException("There was an error verifying the nation");
//
//        AllianceList alliance = getDb().getAllianceList();
//        Set<Grant.Requirement> baseRequirements = new HashSet<>();
//
//        baseRequirements.add(new Grant.Requirement("This guild is not part of an alliance", false, f -> alliance != null));
//        baseRequirements.add(new Grant.Requirement("Nation is not a member of an alliance", overrideUnsafe, f -> f.getPosition() > 1));
//        baseRequirements.add(new Grant.Requirement("Nation is in VM", overrideUnsafe, f -> f.getVm_turns() == 0));
//        baseRequirements.add(new Grant.Requirement("Nation is not in the alliance: " + StringMan.getString(alliance.getIds()), overrideUnsafe, f -> alliance.isInAlliance(f)));
//
//        Role temp = Roles.TEMP.toRole(getGuild());
//        baseRequirements.add(new Grant.Requirement("Nation not eligible for grants", overrideSafe, f -> !member.getRoles().contains(temp)));
//
//        baseRequirements.add(new Grant.Requirement("Nation is not active in past 24h", overrideSafe, f -> f.getActive_m() < 1440));
//        baseRequirements.add(new Grant.Requirement("Nation is not active in past 7d", overrideUnsafe, f -> f.getActive_m() < 10000));
//
//        baseRequirements.add(new Grant.Requirement("Nation does not have 5 raids going", overrideSafe, f -> f.getCities() >= 10 || f.getOff() >= 5));
//
//        if (nation.getCities() >= 10) {
//            baseRequirements.add(new Grant.Requirement("Nation is not on the grant tax bracket", overrideSafe, f -> {
//                TaxRate taxRate = getInternalTaxrate(f.getNation_id());
//                if (taxRate == null || taxRate.money < 70 || taxRate.resources < 70) return false;
//                return true;
//            }));
//        }
//
//        if (nation.getCities() < 10 && type != DepositType.WARCHEST) {
//            // mmr = 5000
//            baseRequirements.add(new Grant.Requirement("Nation is not mmr=5000 (5 barracks, 0 factories, 0 hangars, 0 drydocks in each city)\n" +
//                    "(peacetime raiding below city 10)", overrideSafe, f -> f.getMMRBuildingStr().startsWith("5000")));
//        }
//        if (nation.getNumWars() > 0) {
//            // require max barracks
//            baseRequirements.add(new Grant.Requirement("Nation does not have 5 barracks in each city (raiding)", overrideSafe, f -> f.getMMRBuildingStr().charAt(0) == '5'));
//        }
//        if (nation.getCities() >= 10 && nation.getNumWars() == 0) {
//            // require 5 hangars
//            baseRequirements.add(new Grant.Requirement("Nation does not have 5 hangars in each city (peacetime)", overrideSafe, f -> f.getMMRBuildingStr().charAt(2) == '5'));
//            if (type == DepositType.CITY || type == DepositType.INFRA || type == DepositType.LAND) {
//                baseRequirements.add(new Grant.Requirement("Nation does not have 0 factories in each city (peacetime)", overrideSafe, f -> f.getMMRBuildingStr().charAt(1) == '0'));
//                baseRequirements.add(new Grant.Requirement("Nation does not have max aircraft", overrideSafe, f -> f.getMMR().charAt(2) == '5'));
//            }
//        }
//
//        baseRequirements.add(new Grant.Requirement("Nation is beige", overrideSafe, f -> !f.isBeige()));
//        baseRequirements.add(new Grant.Requirement("Nation is gray", overrideSafe, f -> !f.isGray()));
//        baseRequirements.add(new Grant.Requirement("Nation is blockaded", overrideSafe, f -> !f.isBlockaded()));
////        baseRequirements.add(new Grant.Requirement("Nation is beige", overrideSafe, f -> !f.isBeige()));
//
//        // TODO no disburse past 5 days during wartime
//        // TODO 2d seniority and 5 won wars for initial 1.7k infra grants
//        baseRequirements.add(new Grant.Requirement("Nation does not have 10d seniority", overrideSafe, f -> {
//            Map.Entry<Integer, Rank> previousAA = f.getAlliancePosition(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(10));
//            return alliance.contains(previousAA.getKey()) && previousAA.getValue().id > Rank.APPLICANT.id;
//        }));
//
//        baseRequirements.add(new Grant.Requirement("Nation does not have 80% daily logins (past 1 weeks)", overrideSafe, f -> nation.avg_daily_login_week() > 0.8));
//
//        switch (type) {
//            default:
//                throw new UnsupportedOperationException("TODO: This type of grant is not supported currently");
//            case CITY: {
//                baseRequirements.add(new Grant.Requirement("Nation has 20 cities", overrideSafe, f -> f.getCities() < 20));
//                baseRequirements.add(new Grant.Requirement("Domestic policy must be set to MANIFEST_DESTINY for city grants: <https://politicsandwar.com/nation/edit/>", overrideSafe, f -> f.getDomesticPolicy() == DomesticPolicy.MANIFEST_DESTINY));
//
//                // must be gov ossoona or have project timer up as well
//                // City 11-12 = Encourage them to pass the test and get UP grant
//                // TODO must not have received grant for this city yet
//                // city grant past c10 in past 10 days
//
//                baseRequirements.add(new Grant.Requirement("Nation still has a city timer", overrideUnsafe, f -> f.getCities() < 10 || f.getCityTurns() <= 0));
//
//                int currentCities = nation.getCities();
//                baseRequirements.add(new Grant.Requirement("Nation has built a city, please run the grant command again", false, f -> f.getCities() == currentCities));
//
//                baseRequirements.add(new Grant.Requirement("Nation does not have 10 cities", overrideSafe, f -> f.getCities() >= 10));
//
////                if (nation.getCities() < 9) {
////                    // require 10 cities + 2k infra in deppsits
////                    int amt = 10 - nation.getCities();
////                    grants.add(new Grant(nation, DepositType.CITY)
////                            .setAmount(amt)
////                            .addCity(currentCities)
//////                        .setInstructions(grantCity(pnwNation, me, (int) amt, resources, force))
////                            .setCost(new Function<DBNation, double[]>() {
////                                @Override
////                                public double[] apply(DBNation nation) {
////                                    double cityCost = PnwUtil.nextCityCost(nation, amt);
////                                    double factor = nation.getDomesticPolicy() == DomesticPolicy.MANIFEST_DESTINY ? 0.95 : 1;
////                                    return ResourceType.MONEY.toArray(cityCost * factor);
////                                }
////                            })
////                    );
////                }
//                baseRequirements.add(new Grant.Requirement("Nation received city grant in past 10 days", overrideUnsafe, new Function<DBNation, Boolean>() {
//                    @Override
//                    public Boolean apply(DBNation nation) {
//                        long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(10);
//                        List<Transaction2> transactions = nation.getTransactions(-1L);
//                        return (!Grant.hasReceivedGrantWithNote(transactions, "#city", cutoff));
//                    }
//                }));
//
//                Grant grant = new Grant(nation, DepositType.CITY)
//                        .setAmount(1)
//                        .addCity(currentCities)
////                        .setInstructions(grantCity(pnwNation, me, (int) amt, resources, force))
//                        .setCost(new Function<DBNation, double[]>() {
//                            @Override
//                            public double[] apply(DBNation nation) {
//                                double cityCost = PnwUtil.nextCityCost(nation, 1);
//                                double factor = nation.getDomesticPolicy() == DomesticPolicy.MANIFEST_DESTINY ? 0.95 : 1;
//                                return ResourceType.MONEY.toArray(cityCost * factor);
//                            }
//                        });
//
//                // city 10 day unsafe
//                // city safe
//
//                grant.addRequirement(new Grant.Requirement("Already received a grant for a city", overrideSafe, new Function<DBNation, Boolean>() {
//                    @Override
//                    public Boolean apply(DBNation nation) {
//                        List<Transaction2> transactions = nation.getTransactions(-1);
//                        return !Grant.hasGrantedCity(nation, transactions, currentCities + 1);
//                    }
//                }));
//                grants.add(grant);
//                break;
//            }
//            case PROJECT: {
//
//                baseRequirements.add(new Grant.Requirement("Domestic policy must be set to TECHNOLOGICAL_ADVANCEMENT for project grants: <https://politicsandwar.com/nation/edit/>", overrideSafe, f -> f.getDomesticPolicy() == DomesticPolicy.TECHNOLOGICAL_ADVANCEMENT));
//                baseRequirements.add(new Grant.Requirement("Nation still has a city timer", overrideUnsafe, f -> f.getProjectTurns() <= 0));
//                baseRequirements.add(new Grant.Requirement("Nation has no free project slot", overrideUnsafe, f -> f.projectSlots() > f.getNumProjects()));
//                // project grant in past 10 days
//
//                // TODO must not have received grant for this project yet
//
//                Set<Project> allowedProjects = getAllowedProjectGrantSet(nation);
//                Set<Project> allProjects = new HashSet<>(Arrays.asList(Projects.values));
//                Set<Project> alreadyHasProject = nation.getProjects();
//
//                for (Project project : allProjects) {
//                    Grant grant = new Grant(nation, DepositType.PROJECT)
//                            .setAmount(project.name())
//                            .setCost(f -> PnwUtil.resourcesToArray(PnwUtil.multiply(project.cost(), f.getDomesticPolicy() == DomesticPolicy.TECHNOLOGICAL_ADVANCEMENT ? 0.95 : 1)));
//
//                    grant.addRequirement(new Grant.Requirement("Not eligable for project", overrideSafe, f -> allowedProjects.contains(project)));
//                    grant.addRequirement(new Grant.Requirement("Already have project", overrideUnsafe, f -> {
//                        return !f.getProjects().contains(project);
//                    }));
//                    if (project == Projects.URBAN_PLANNING || project == Projects.ADVANCED_URBAN_PLANNING) {
//                        grant.addRequirement(new Grant.Requirement("Please contact econ gov to approve this grant (as its expensive)", overrideSafe, f -> {
//                            return !f.getProjects().contains(project);
//                        }));
//                    }
//
//                    grant.addRequirement(new Grant.Requirement("Already received a grant for a project in past 10 days", overrideUnsafe, new Function<DBNation, Boolean>() {
//                        @Override
//                        public Boolean apply(DBNation nation) {
//                            long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(10);
//
//                            List<Transaction2> transactions = nation.getTransactions(-1L);
//
//                            if (Grant.hasReceivedGrantWithNote(transactions, "#project", cutoff)) return false;
//
//                            if (Grant.getProjectsGranted(nation, transactions).contains(project)) return false;
//                            return true;
//                        }
//                    }));
//
//                    grants.add(grant);
//                }
//
//                break;
//            }
//            case INFRA: {
//                Map<Integer, JavaCity> cities = nation.getCityMap(true, true);
//
//                // Grant for each city, as well as all cities (not currently granted)
//                int[] options;
//                if (getDb().getCoalition(Coalition.ENEMIES).isEmpty() && nation.getDef() == 0) {
//                    options = new int[]{1200, 1700, 2000};
//                } else {
//                    options = new int[]{1200, 1500};
//                }
//
//                Grant.Requirement noWarRequirement = new Grant.Requirement("Higher infra grants require approval when fighting wars", overrideSafe, new Function<DBNation, Boolean>() {
//                    @Override
//                    public Boolean apply(DBNation nation) {
//                        if (nation.getDef() > 0) return false;
//                        if (nation.getNumWars() > 0 && nation.getNumWarsAgainstActives() > 0) return false;
//                        return true;
//                    }
//                });
//
//                Grant.Requirement seniority = new Grant.Requirement("Nation does not have 3 days alliance seniority", overrideSafe, f -> f.allianceSeniority() < 3);
//
//                for (int infraLevel : options) {
//                    List<Grant.Requirement> localRequirement = new ArrayList<>();
//
//                    switch (infraLevel) {
//                        case 1200:
//                            break;
//                        case 1700:
//                        case 1500:
//                            localRequirement.add(seniority);
//                            localRequirement.add(new Grant.Requirement("Infra grants are restricted during wartime. Please contact econ (or remove the `enemies` coalition)", overrideSafe, f -> getDb().getCoalition2(Coalition.ENEMIES).isEmpty()));
//                            localRequirement.add(noWarRequirement);
//                            break;
//                        case 2000:
//                            localRequirement.add(seniority);
//                            localRequirement.add(noWarRequirement);
//                            localRequirement.add(new Grant.Requirement("Nation does not have 10 cities", overrideSafe, f -> f.getCities() >= 10));
//                            localRequirement.add(new Grant.Requirement("Infra grants are restricted during wartime. Please contact econ", overrideSafe, f -> getDb().getCoalition2(Coalition.ENEMIES).isEmpty()));
//                            localRequirement.add(new Grant.Requirement("Domestic policy must be set to URBANIZATION for infra grants above 1700: <https://politicsandwar.com/nation/edit/>", overrideSafe, f -> f.getDomesticPolicy() == DomesticPolicy.URBANIZATION));
//                            localRequirement.add(new Grant.Requirement("Infra grants above 1700 whilst raiding/warring require econ approval", overrideSafe, f -> {
//                                if (f.getDef() > 0) return false;
//                                if (f.getOff() > 0) {
//                                    for (DBWar war : f.getActiveWars()) {
//                                        DBNation other = war.getNation(war.isAttacker(f));
//                                        if (other.getActive_m() < 1440 || other.getPosition() >= Rank.APPLICANT.id) {
//                                            return false;
//                                        }
//                                    }
//                                }
//                                return true;
//                            }));
//                            break;
//                        default:
//                            throw new IllegalArgumentException("Invalid infra level");
//                    }
//                    {
//                        boolean hasLowerInfra = false;
//                        boolean hasLowerBuildings = false;
//                        for (Map.Entry<Integer, JavaCity> entry : cities.entrySet()) {
//                            JavaCity city = entry.getValue();
//                            if (city.getInfra() < infraLevel) {
//                                hasLowerInfra = true;
//                            }
//                            double currentInfra = Math.max(city.getRequiredInfra(), city.getInfra());
//                            if (currentInfra < infraLevel) {
//                                hasLowerBuildings = true;
//                                break;
//                            }
//                        }
//
//                        if (hasLowerBuildings) {
//                            Grant grant = new Grant(nation, DepositType.INFRA);
//                            for (Integer cityId : cities.keySet()) grant.addCity(cityId);
//                            grant.addRequirement(localRequirement);
//                            grant.setAmount(infraLevel);
//                            grant.setCost(new Function<DBNation, double[]>() {
//                                @Override
//                                public double[] apply(DBNation nation) {
//                                    double cost = 0;
//                                    List<Transaction2> transactions = nation.getTransactions(-1L);
////                                    Map<Integer, Double> byCity = Grant.getInfraGrantsByCity(nation, transactions);
//                                    for (Map.Entry<Integer, JavaCity> entry : cities.entrySet()) {
//                                        double currentInfra = Grant.getCityInfraGranted(nation, entry.getKey(), transactions);
//                                        JavaCity city = entry.getValue();
//                                        currentInfra = Math.max(currentInfra, Math.max(city.getRequiredInfra(), city.getInfra()));
//                                        if (currentInfra < infraLevel) {
//                                            cost += PnwUtil.calculateInfra(currentInfra, infraLevel);
//                                        }
//                                    }
//
//                                    boolean urban = nation.getDomesticPolicy() == DomesticPolicy.URBANIZATION;
//                                    boolean cce = nation.hasProject(Projects.CENTER_FOR_CIVIL_ENGINEERING);
//                                    boolean aec = nation.hasProject(Projects.ADVANCED_ENGINEERING_CORPS);
//                                    double discountFactor = 1;
//                                    if (urban) discountFactor -= 0.05;
//                                    if (cce) discountFactor -= 0.05;
//                                    if (aec) discountFactor -= 0.05;
//                                    cost = cost * discountFactor;
//
//                                    return ResourceType.MONEY.toArray(cost);
//                                }
//                            });
//                            grants.add(grant);
//                        }
//                    }
//
//                    for (Map.Entry<Integer, JavaCity> entry : nation.getCityMap(true).entrySet()) {
//                        int id = entry.getKey();
//                        JavaCity city = entry.getValue();
//                        double cityInfra = Math.max(city.getInfra(), city.getRequiredInfra());
//                        if (cityInfra >= infraLevel) continue;
//
//                        Grant grant = new Grant(nation, DepositType.INFRA);
//                        grant.addRequirement(new Grant.Requirement("You have already received infra of that level for that city", overrideSafe, new Function<DBNation, Boolean>() {
//                            @Override
//                            public Boolean apply(DBNation nation) {
//                                List<Transaction2> transactions = nation.getTransactions(-1L);
//                                double maxGranted = Grant.getCityInfraGranted(nation, id, transactions);
//                                if (maxGranted >= infraLevel) return false;
//                                return true;
//                            }
//                        }));
//                        grant.addCity(id);
//                        grant.addRequirement(localRequirement);
//                        grant.setAmount(infraLevel);
//                        grant.setCost(new Function<DBNation, double[]>() {
//                            @Override
//                            public double[] apply(DBNation nation) {
//                                List<Transaction2> transactions = nation.getTransactions(-1L);
//                                double maxGranted = Grant.getCityInfraGranted(nation, id, transactions);
//
//                                double cost = PnwUtil.calculateInfra(maxGranted, infraLevel);
//                                boolean urban = nation.getDomesticPolicy() == DomesticPolicy.URBANIZATION;
//                                boolean cce = nation.hasProject(Projects.CENTER_FOR_CIVIL_ENGINEERING);
//                                boolean aec = nation.hasProject(Projects.ADVANCED_ENGINEERING_CORPS);
//                                double discountFactor = 1;
//                                if (urban) discountFactor -= 0.05;
//                                if (cce) discountFactor -= 0.05;
//                                if (aec) discountFactor -= 0.05;
//                                cost = cost * discountFactor;
//                                return ResourceType.MONEY.toArray(cost);
//                            }
//                        });
//                        grants.add(grant);
//                    }
//                }
//                break;
//            }
//            case LAND:
//                int amt = nation.getCities() < 10 ? 1500 : 2000;
//                Grant grant = new Grant(nation, DepositType.LAND);
//                Map<Integer, JavaCity> cities = nation.getCityMap(true, true);
//                boolean missingLand = false;
//                for (Map.Entry<Integer, JavaCity> entry : cities.entrySet()) {
//                    grant.addCity(entry.getKey());
//                    if (entry.getValue().getLand() < amt) missingLand = true;
//                }
//                if (missingLand) {
//                    grant.setAmount(amt);
//                    grant.setCost(new Function<DBNation, double[]>() {
//                        @Override
//                        public double[] apply(DBNation nation) {
//                            List<Transaction2> transactions = nation.getTransactions(-1L);
//                            Map<Integer, Double> byCity = Grant.getLandGrantedByCity(nation, transactions);
//
//                            double total = 0;
//                            for (Map.Entry<Integer, JavaCity> entry : cities.entrySet()) {
//                                JavaCity city = entry.getValue();
//                                int cityId = entry.getKey();
//                                double currentLand = Math.max(city.getLand(), byCity.getOrDefault(cityId, 0d));
//                                PnwUtil.calculateLand(currentLand, amt);
//                            }
//
//                            return ResourceType.MONEY.toArray(total);
//                        }
//                    });
//                } else {
//                    throw new IllegalArgumentException("You already have " + amt + " land in each city");
//                }
//                break;
//            case UNIT:
//                throw new IllegalArgumentException("Units are not granted. Please get a warchest grant");
//            case BUILD:
//                throw new IllegalArgumentException("Please use " + CM.city.optimalBuild.cmd.toSlashMention() + "");
//                // MMR_RAIDING:
//                // MMR_WARTIME:
//                // MMR_PEACE: c1-10=5001,c11+=5553
//
//                // builds: PEACETIME, NON_COMMERCE, SELF_SUFFICIENT
//            case WARCHEST:
//                baseRequirements.add(new Grant.Requirement("Nation does not have 10 cities", overrideSafe, f -> f.getCities() >= 10));
//                baseRequirements.add(new Grant.Requirement("Nation is losing", overrideSafe, f -> f.getRelativeStrength() < 1));
//                baseRequirements.add(new Grant.Requirement("Nation is on low military", overrideSafe, f -> f.getAircraftPct() < 0.7));
//                baseRequirements.add(new Grant.Requirement("Already received warchest since last war", overrideUnsafe, new Function<DBNation, Boolean>() {
//                    @Override
//                    public Boolean apply(DBNation nation) {
//                        long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(3);
//                        long latestWarTime = 0;
//                        DBWar latestWar = null;
//                        for (DBWar war : nation.getWars()) {
//                            if (war.date > latestWarTime) {
//                                latestWarTime = war.date;
//                                latestWar = war;
//                            }
//                        }
//                        if (latestWar != null) {
//                            for (AbstractCursor attack : latestWar.getAttacks()) {
//                                latestWarTime = Math.max(latestWarTime, attack.epoch);
//                            }
//                            cutoff = Math.min(latestWarTime, cutoff);
//                        }
//                        List<Transaction2> transactions = nation.getTransactions(-1L);
//                        for (Transaction2 transaction : transactions) {
//                            if (transaction.tx_datetime < cutoff) continue;
//                            if (transaction.note != null && transaction.note.toLowerCase().contains("#warchest")) {
//                                return false;
//                            }
//                        }
//                        return true;
//                    }
//                }));
//
//
//                // has not received warchest in past 3 days
//                // is assigned to a counter
//
//                // fighting an enemy, or there are enemies
//
//                boolean isCountering = false;
//                Set<Integer> allies = getDb().getAllies(true);
//                WarCategory warChannel = getDb().getWarChannel();
//                for (Map.Entry<Integer, WarCategory.WarRoom> entry : warChannel.getWarRoomMap().entrySet()) {
//                    WarCategory.WarRoom room = entry.getValue();
//                    if (room.isParticipant(nation, false)) {
//                        boolean isDefending = false;
//                        boolean isEnemyAttackingMember = false;
//                        for (DBWar war : room.target.getActiveWars()) {
//                            if (allies.contains(war.defender_aa)) {
//                                isEnemyAttackingMember = true;
//                            }
//                            if (war.defender_id == nation.getNation_id()) {
//                                isDefending = true;
//                            }
//                        }
//                        if (!isDefending && isEnemyAttackingMember) {
//                            isCountering = true;
//                        }
//                    }
//                }
//
//                boolean isLosing = nation.getRelativeStrength() < 1;
//
//                boolean hasEnemy = false;
//                Set<Integer> enemies = getDb().getCoalition(Coalition.ENEMIES);
//
//                boolean correctMMR = PnwUtil.matchesMMR(nation.getMMRBuildingStr(), "555X");
//
//                // is assigned counter
//                // OR
//                // enemies AND mmr=555X
//
//                // TODO
//                // - funds for only a specific unit
//                // - limit 24h
//                // - dont allow more than 5 since last war
//                // - dont allow more than 1 in 5 days if no units were bought in last X days
//
//                /*
//                Nation has max aircraft and 3% tanks
//                 */
//                if (!enemies.isEmpty() && nation.getRelativeStrength() >= 1) {
//                    String mmr = nation.getMMRBuildingStr();
//                    // 80% planes + 50%
//                }
//                // has enemies, or ordered to counter
//
//                /*
//                    Has enemies and has not received warchest in the past 5 days
//                    Added to a war room as an attacker
//                    Has not received warchest in the past 5 days
//                 */
//                break;
//            case RESOURCES:
//                // disburse up to 5 days?
//                    MessageChannel channel = getDb().getResourceChannel(nation.getAlliance_id());
//                    if (channel != null) {
//                        throw new IllegalArgumentException("Please use " + CM.transfer.self.cmd.toSlashMention() + " or " + CM.transfer.raws.cmd.toSlashMention() + " in " + channel.getAsMention() + " to request funds from your deposits");
//                    }
//                throw new IllegalArgumentException("Please request resources in the resource request channel");
//        }
//        for (Grant grant : grants) {
//            grant.addRequirement(baseRequirements);
//        }
//
//        return grants;
//    }

//    public double[] getDeposits(Alliance alliance, boolean update) {
//
//        Map<ResourceType, Double> aaDeposits = offshore.getDeposits(alliance.getAlliance_id(), update);
//        return PnwUtil.resourcesToArray(aaDeposits);
//    }
//
//    public double[] getDepositsFromAccount(String account, boolean update) {
//        return null; // TODO
//    }
//
//    public Map<DepositType, double[]> getDeposits(DBNation nation, boolean useTaxBase) {
//        Set<Integer> trackedAlliances = new HashSet<>();
//
//        Map<ResourceType, Map<String, Double>> depositsByNote = offset ? db.getDepositOffset(getNation_id()) : new HashMap<>();
//
//        boolean includeWarchest = true;
////        if (db != null) {
////            Integer aaId = db.getOrNull(GuildKey.ALLIANCE_ID);
////            Integer warCostFactor = db.getOrNull(GuildKey.DEPOSITS_INCLUDE_WARCOST);
////            if (aaId != null && warCostFactor != null) {
////
////            }
////        }
//
//        if (allianceId != null && allowedLabels.apply("#tax", null)) {
//            Set<BankDB.TaxDeposit> taxesPaid = new HashSet<>(Locutus.imp().getBankDB().getTaxesPaid(getNation_id(), allianceId));
//            int[] taxBase = useTaxBase ? db.getOrNull(GuildKey.TAX_BASE) : null;
//            if (taxBase == null) taxBase = new int[] {0, 0};
//            for (BankDB.TaxDeposit deposit : taxesPaid) {
//                if (deposit.date < cutOff) continue;
//                double pctMoney = (deposit.moneyRate > taxBase[0] ?
//                        Math.max(0, (deposit.moneyRate - taxBase[0]) / deposit.moneyRate)
//                        : 0);
//                double pctRss = (deposit.resourceRate > taxBase[1] ?
//                        Math.max(0, (deposit.resourceRate - taxBase[1]) / deposit.resourceRate)
//                        : 0);
//
//                deposit.resources[0] *= pctMoney;
//                for (int i = 1; i < deposit.resources.length; i++) {
//                    deposit.resources[i] *= pctRss;
//                }
//
//                tax = PnwUtil.addResourcesToA(tax, PnwUtil.resourcesToMap(deposit.resources));
//            }
//        }
//
//        if (!tracked.isEmpty()) {
//            List<Transaction2> records = getTransactions(updateThreshold);
//            for (Transaction2 transfer : records) {
//                String note = transfer.note;
//                if (note != null && !note.isEmpty() && (note.charAt(0) == '!' || note.charAt(0) == '#')) {
//                } else {
//                    note = null;
//                }
//                int sign;
//                if (tracked.contains(transfer.receiver_id)) {
//                    sign = 1;
//                } else if (tracked.contains(transfer.sender_id)) {
//                    sign = -1;
//                } else {
//                    continue;
//                }
//
//                if (transfer.tx_datetime < cutOff) continue;
//
//                double[] rss = transfer.resources;
//                for (ResourceType type : ResourceType.values) {
//                    Map<String, Double> notes = depositsByNote.computeIfAbsent(type, f -> new HashMap<>());
//                    Double current = notes.computeIfAbsent(note, f -> 0d);
//                    notes.put(note, current + sign * rss[type.ordinal()]);
//                }
//            }
//        }
//
//        for (Map.Entry<ResourceType, Map<String, Double>> entry : depositsByNote.entrySet()) {
//            ResourceType rss = entry.getKey();
//            for (Map.Entry<String, Double> noteEntry : entry.getValue().entrySet()) {
//                Map<ResourceType, Double> currentMap = deposits;
//                String note = noteEntry.getKey();
//                if (note == null) note = "#deposit";
//                String noteArg = note.split("[ |\n]")[0];
//                if (!allowedLabels.apply(noteArg, null)) {
//                    continue;
//                }
//                switch (noteArg.toLowerCase()) {
//                    case "#ignore":
//                        continue;
//                    case "#raws":
//                    case "#raw":
//                    case "#tax":
//                    case "#taxes":
//                    case Settings.commandPrefix(true) + "disperse":
//                    case Settings.commandPrefix(true) + "disburse":
//                        currentMap = tax;
//                        break;
//                    case Settings.commandPrefix(true) + "warchest":
//                    case "#warchest":
//                        if (!includeWarchest) continue;
////                        currentMap = warCost;
////                        break;
//                    case "#trade":
//                    case "#trades":
//                    case "#trading":
//                    case "#credits":
//                    case "#buy":
//                    case "#sell":
//                    case "#deposit":
//                    case "#deposits":
//                        break;
//                    default:
//                        // units
//                        //projects
//                        // bu
//                        currentMap = debt;
//                        labels.add(noteArg);
//                        break;
//                }
//                currentMap.put(rss, currentMap.getOrDefault(rss, 0d) + noteEntry.getValue());
//            }
//        }
//
//        Integer aaId = db.getOrNull(GuildKey.ALLIANCE_ID);
//        Set<Integer> tracked = db.getCoalition("offshore");
//        if (aaId != null) tracked.add(aaId);
//
//        int[] taxBase = db.getOrNull(GuildKey.TAX_BASE);
//        if (taxBase == null) taxBase = new int[] {0, 0};
//
//        // #ignore
//        // #account <account>
//        // #nation <nation>
//        // #alliance <alliance>
//
////        Map<DepositType, double[]> depo = nation.getDeposits(getDb(), new BiFunction<DepositType, Transaction2, Transaction2>() {
////            @Override
////            public Transaction2 apply(DepositType type, Transaction2 transfer) {
////
////
////                // taxes
////
////                return null;
////            }
////        }, 0);
//
//        return null;
//    }

    public void onAttack(DBNation memberNation, AbstractCursor root) {
        Set<Integer> aaIds = db.getAllianceIds();
        if (!aaIds.isEmpty()) {
            if (root.getAttack_type() == AttackType.VICTORY) {
                DBNation attacker = Locutus.imp().getNationDB().getNation(root.getAttacker_id());
                handleLostWars(attacker, aaIds, root, memberNation);

                DBNation defender = Locutus.imp().getNationDB().getNation(root.getDefender_id());
                handleWonWars(defender, aaIds, root, memberNation);

            }
        }
    }

    private void handleWonWars(DBNation enemy, Set<Integer> aaIds, AbstractCursor root, DBNation memberNation) {
        if (enemy == null || aaIds.contains(enemy.getAlliance_id()) || enemy.getNation_id() == memberNation.getNation_id()) return;
        MessageChannel channel = db.getOrNull(GuildKey.WON_WAR_CHANNEL);
        if (enemy.getActive_m() > 1440 || enemy.getVm_turns() > 0) return;

        if (channel == null) return;

        DBWar war = Locutus.imp().getWarDb().getWar(root.getWar_id());
        if (war == null) return;
        String title = "War Won";
        createWarInfoEmbed(title, war, enemy, memberNation, channel);
    }

    private void handleLostWars(DBNation enemy, Set<Integer> aaIds, AbstractCursor root, DBNation memberNation) {
        if (enemy == null || aaIds.contains(enemy.getAlliance_id()) || enemy.getNation_id() == memberNation.getNation_id()) return;
        MessageChannel channel = db.getOrNull(GuildKey.LOST_WAR_CHANNEL);

        if (channel == null) return;
        DBWar war = Locutus.imp().getWarDb().getWar(root.getWar_id());
        if (war == null) return;
        String title = "War Lost";
        createWarInfoEmbed(title, war, enemy, memberNation, channel);
    }

    private void createWarInfoEmbed(String title, DBWar war, DBNation enemy, DBNation memberNation, MessageChannel channel) {
        boolean isAttacker = war.isAttacker(memberNation);

        WarCard card = new WarCard(war, false);

        String subInfo = card.condensedSubInfo(false);
        WarAttackParser parser = war.toParser(isAttacker);
        AttackTypeBreakdown breakdown = parser.toBreakdown();

        String infoEmoji = "War Info";
        String infoCommand = "." + Settings.commandPrefix(true) + "warinfo " + war.toUrl();

        String costEmoji = "War Cost";
        String costCommand = "." + Settings.commandPrefix(true) + "WarCost " + war.toUrl();

        String assignEmoji = "Claim";
        String assignCmd = "." + Settings.commandPrefix(true) + "UpdateEmbed 'description:{description}\n" +
                "\n" +
                "Assigned to {usermention} in {timediff}'";

        DiscordChannelIO io = new DiscordChannelIO(channel);
        IMessageBuilder msg = io.create();

        StringBuilder builder = new StringBuilder();
        builder.append(war.toUrl() + "\n");

        builder.append(enemy.getNationUrlMarkup(true))
                .append(" | ").append(enemy.getAllianceUrlMarkup(true)).append(":");
        builder.append(enemy.toCityMilMarkedown());

        String typeStr = isAttacker ? "\uD83D\uDD2A" : "\uD83D\uDEE1";
        builder.append(typeStr);
        builder.append(memberNation.getNationUrlMarkup(true) + " (member):");
        builder.append("\n").append(memberNation.toCityMilMarkedown());

        String attStr = card.condensedSubInfo(isAttacker);
        String defStr = card.condensedSubInfo(!isAttacker);
        builder.append("```\n" + attStr + "|" + defStr + "```\n");

        msg.writeTable(title, breakdown.toTableList(), false, builder.toString());
        msg.commandButton(infoCommand, infoEmoji);
        msg.commandButton(costCommand, costEmoji);
        msg.commandButton(assignCmd, assignEmoji);
        msg.cancelButton();
        msg.send();
    }

    public void onDefensiveWarAlert(List<Map.Entry<DBWar, DBWar>> wars, boolean rateLimit) {
        MessageChannel channel = getDb().getOrNull(GuildKey.DEFENSE_WAR_CHANNEL);
        if (channel == null) return;
        onWarAlert(channel, wars, rateLimit, false);
    }

    public void onOffensiveWarAlert(List<Map.Entry<DBWar, DBWar>> wars, boolean rateLimit) {
        MessageChannel channel = getDb().getOrNull(GuildKey.OFFENSIVE_WAR_CHANNEL);
        if (channel == null) return;
        onWarAlert(channel, wars, rateLimit, true);

        // TODO audit for raiding inactive with terrible loot
        handleBadLootAudit(wars);
    }

    public void handleBadLootAudit(List<Map.Entry<DBWar, DBWar>> wars) {
        if (true) return;
        for (Map.Entry<DBWar, DBWar> entry : wars) {
            DBWar war = entry.getValue();
            if (entry.getKey() != null || war == null) continue;
            DBNation defender = war.getNation(false);
            if (defender == null) continue;
            DBNation attacker = war.getNation(true);
            if (attacker == null) continue;

            LootEntry lootInfo = defender.getBeigeLoot();
            if (lootInfo == null) continue;
            if (defender.lastActiveMs() > lootInfo.getDate()) continue;

            Set<DBNation> targets = Locutus.imp().getNationDB().getNationsMatching(f -> f.getAlliance_id() == 0 && attacker.isInWarRange(f));
            if (targets.isEmpty()) continue;




            double revenue = PnwUtil.convertedTotal(defender.getRevenue());


        }
    }

    public void onWarAlert(MessageChannel channel, List<Map.Entry<DBWar, DBWar>> wars, boolean rateLimit, boolean offensive) {
        if (wars.isEmpty()) return;

        Set<Integer> enemies = db.getCoalition(Coalition.ENEMIES);
        Set<Integer> dnrAAs = db.getCoalition(Coalition.DNR);
        Set<Integer> counterAAs = db.getCoalition(Coalition.COUNTER);
        Set<Integer> ignoreFA = db.getCoalition(Coalition.IGNORE_FA);

        Map<DBWar, Set<String>> pingUserOrRoles = new HashMap<>();
        Set<DBWar> dnrViolations = new HashSet<>();

        Function<DBNation, Boolean> dnr = getDb().getCanRaid();

        Guild alertGuild = (channel instanceof GuildMessageChannel) ? ((GuildMessageChannel) channel).getGuild() : guild;

        Role milcomRole = Roles.MILCOM.toRole(alertGuild);
        Role faRole = Roles.FOREIGN_AFFAIRS.toRole(alertGuild);

        Set<Integer> aaIds = db.getAllianceIds();
        WarCategory warCat = db.getWarChannel();

        for (Map.Entry<DBWar, DBWar> entry : wars) {
            DBWar war = entry.getValue();
            DBNation attacker = war.getNation(true);
            DBNation defender = war.getNation(false);

            if (offensive) {
                if (defender != null && !dnr.apply(defender)) {
                    boolean violation = true;
                    {
                        if (defender != null && warCat != null) {
                            WarCategory.WarRoom warRoom = warCat.get(defender, false, false, false);
                            if (warRoom != null && warRoom.channel != null && warRoom.isParticipant(attacker, false)) {
                                violation = false;
                            }
                        }
                        if (violation) {
                            CounterStat counterStat = war.getCounterStat();
                            if (counterStat != null && counterStat.type == CounterType.IS_COUNTER) violation = false;
                            if (violation) {
                                dnrViolations.add(war);
                                if (faRole != null) {
                                    pingUserOrRoles.computeIfAbsent(war, f -> new HashSet<>()).add(faRole.getAsMention());
                                }
                                User user = attacker.getUser();
                                if (user != null) {
                                    Member member = guild.getMember(user);
                                    if (member != null) {
                                        pingUserOrRoles.computeIfAbsent(war, f -> new HashSet<>()).add(member.getAsMention());
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                if (defender != null) {
                    boolean pingMilcom = defender.getPosition() >= Rank.MEMBER.id || defender.getActive_m() < 2440 || (aaIds.contains(defender.getAlliance_id()) && defender.getActive_m() < 7200);
                    if (pingMilcom && milcomRole != null) {
                        CounterStat counterStat = war.getCounterStat();
                        if (counterStat != null && counterStat.type == CounterType.IS_COUNTER && !enemies.contains(attacker.getAlliance_id())) {
                            pingMilcom = false;
                        }
                        if (pingMilcom && attacker != null && enemies.contains(attacker.getAlliance_id()) && attacker.getDef() >= 3) {
                            pingMilcom = false;
                        }
                        if (pingMilcom) {
                            NationFilter allowedMentions = db.getOrNull(GuildKey.MENTION_MILCOM_FILTER);
                            if (allowedMentions != null) {
                                if (!allowedMentions.test(attacker) && !allowedMentions.test(defender)) {
                                    pingMilcom = false;
                                }
                            }
                        }
                        if (pingMilcom) {
                            if (getDb().getCoalition(Coalition.FA_FIRST).contains(attacker.getAlliance_id()) && faRole != null) {
                                String response = "```" + PnwUtil.getName(attacker.getAlliance_id(), true) + " is marked as FA_FIRST:\n" +
                                        "- Solicit peace in that alliance embassy before taking military action\n" +
                                        "- Set a reminder for 24h, to remind milcom if peace has not been obtained\n" +
                                        "- React to this message```\n" + faRole.getAsMention();
                                pingUserOrRoles.computeIfAbsent(war, f -> new HashSet<>()).add(response);
                            } else {
                                pingUserOrRoles.computeIfAbsent(war, f -> new HashSet<>()).add(milcomRole.getAsMention());
                            }
                        }
                    }
                    User user = defender.getUser();
                    if (user != null) {
                        Member member = guild.getMember(user);
                        if (member != null) {
                            pingUserOrRoles.computeIfAbsent(war, f -> new HashSet<>()).add(member.getAsMention());
                        }
                    }
                    if (attacker != null && defender.getPosition() == Rank.APPLICANT.id && !enemies.contains(attacker.getAlliance_id())) {
                        if (counterAAs.contains(attacker.getAlliance_id())) {
                            String msg = "```" + PnwUtil.getName(attacker.getAlliance_id(), true) + " is added to the COUNTER coalition which forbids raiding inactive applicants (typically due to them countering for their own applicants)```";
                            pingUserOrRoles.computeIfAbsent(war, f -> new HashSet<>()).add(msg);
                            if (milcomRole != null) {
                                pingUserOrRoles.computeIfAbsent(war, f -> new HashSet<>()).add(milcomRole.getAsMention());
                            }
                            if (faRole != null && !counterAAs.contains(attacker.getAlliance_id())) {
                                pingUserOrRoles.computeIfAbsent(war, f -> new HashSet<>()).add(faRole.getAsMention());
                            }
                        } else if (dnrAAs.contains(attacker.getAlliance_id())) {
                            String msg = "```" + PnwUtil.getName(attacker.getAlliance_id(), true) + " is added to DNR coalition which forbids raiding inactive applicants```";
                            pingUserOrRoles.computeIfAbsent(war, f -> new HashSet<>()).add(msg);
                            if (counterAAs.contains(attacker.getAlliance_id()) && milcomRole != null) {
                                pingUserOrRoles.computeIfAbsent(war, f -> new HashSet<>()).add(milcomRole.getAsMention());
                            }
                            if (faRole != null && !counterAAs.contains(attacker.getAlliance_id()) && !ignoreFA.contains(attacker.getAlliance_id())) {
                                pingUserOrRoles.computeIfAbsent(war, f -> new HashSet<>()).add(faRole.getAsMention() + " (add enemy to IGNORE_FA coalition to not ping FA- or add to COUNTER to ping milcom)");
                            }
                        }
                    }
                }
            }
        }

        Locutus.imp().getExecutor().submit(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!dnrViolations.isEmpty()) {
                        Locutus.imp().getExecutor().submit(() -> {
                            try {
                                ApiKeyPool key = db.getMailKey();
                                for (DBWar war : dnrViolations) {
                                    DBNation nation = war.getNation(true);
                                    if (nation == null) continue;
                                    User user = nation.getUser();
                                    if ((!aaIds.contains(war.getAttacker_aa())) && (user == null || guild.getMember(user) == null))
                                        continue;

                                    String title = "Do Not Raid/" + channel.getIdLong();
                                    String message = MarkupUtil.htmlUrl(war.toUrl(), war.toUrl()) + " violates the `Do Not Raid` list. If you were not asked to attack, please offer peace\n\nNote: This is an automated message";

                                    nation.sendMail(key, title, message, false);
                                }
                            } catch (Throwable e) {
                                e.printStackTrace();
                            }
                        });
                    }

                    if (rateLimit && wars.size() > 1) {
                        String title = wars.size() + " new " + (offensive ? "Offensive" : "Defensive") + " wars";
                        StringBuilder body = new StringBuilder();
                        StringBuilder footer = new StringBuilder();
                        StringBuilder bodyRaw = new StringBuilder();

                        for (Map.Entry<DBWar, DBWar> pair : wars) {
                            DBWar war = pair.getValue();
                            DBNation attacker = war.getNation(true);
                            DBNation defender = war.getNation(false);

                            CounterStat counterStat = war.getCounterStat();
                            String counterStatStr = counterStat == null ? "" : counterStat.type.getDescription();

                            String attUrl = attacker != null ? attacker.getNationUrl() : PnwUtil.getNationUrl(war.getAttacker_id());
                            String defUrl = defender != null ? defender.getNationUrl() : PnwUtil.getNationUrl(war.getDefender_id());

                            String attAAUrl = attacker != null ? attacker.getAllianceUrl() : PnwUtil.getNationUrl(war.getAttacker_aa());
                            String defAAUrl = defender != null ? defender.getAllianceUrl() : PnwUtil.getNationUrl(war.getDefender_aa());
                            {
                                if (!offensive && !counterStatStr.isEmpty())
                                    body.append("**" + counterStatStr + "**\n");
                                if (offensive && dnrViolations.contains(war)) body.append("**DNR VIOLATION**\n");
                                body.append(MarkupUtil.markdownUrl("War Link", war.toUrl()) + "\n");
                                if (attacker != null) {
                                    body.append(attacker.getNationUrlMarkup(true));
                                    if (attacker.getAlliance_id() != 0) {
                                        body.append(" | " + attacker.getAllianceUrlMarkup(true));
                                        if (attacker.getPosition() < Rank.MEMBER.id) {
                                            body.append("- " + Rank.byId(attacker.getPosition()));
                                        }
                                    }
                                    body.append(" ```\nc" + attacker.getCities() + " | " + attacker.getAvg_infra() + "\uD83C\uDFD7 | " + MathMan.format(attacker.getScore()) + "ns``` ");
                                    body.append("``` " + attacker.getSoldiers()).append(" \uD83D\uDC82").append(" | ");
                                    body.append(attacker.getTanks()).append(" \u2699").append(" | ");
                                    body.append(attacker.getAircraft()).append(" \u2708").append(" | ");
                                    body.append(attacker.getShips()).append(" \u26F5").append("```");
                                }
                                if (defender != null) {
                                    body.append(defender.getNationUrlMarkup(true));
                                    if (defender.getAlliance_id() != 0) {
                                        body.append(" | " + defender.getAllianceUrlMarkup(true));
                                        if (defender.getActive_m() > 1440) {
                                            body.append(" | " + TimeUtil.secToTime(TimeUnit.MINUTES, defender.getActive_m()));
                                        }
                                        if (defender.getPosition() < Rank.MEMBER.id) {
                                            body.append("- " + Rank.byId(defender.getPosition()));
                                        }
                                    }
                                    if (!offensive && defender.getPosition() >= Rank.MEMBER.id) {
                                        User user = defender.getUser();
                                        if (user != null) {
                                            body.append(" | " + user.getAsMention());
                                        }
                                    }
                                    body.append(" ```\nc" + defender.getCities() + " | " + defender.getAvg_infra() + "\uD83C\uDFD7 | " + MathMan.format(defender.getScore()) + "ns``` ");
                                    body.append("```" + defender.getSoldiers()).append(" \uD83D\uDC82").append(" | ");
                                    body.append(defender.getTanks()).append(" \u2699").append(" | ");
                                    body.append(defender.getAircraft()).append(" \u2708").append(" | ");
                                    body.append(defender.getShips()).append(" \u26F5").append("```");
                                    body.append("\n");
                                }
                                body.append("\n");
                            }
                            {
                                if (!offensive && !counterStatStr.isEmpty())
                                    bodyRaw.append("**" + counterStatStr + "**\n");
                                if (offensive && dnrViolations.contains(war)) body.append("**DNR VIOLATION**\n");
                                bodyRaw.append("**War**: <" + war.toUrl() + ">\n");
                                bodyRaw.append("Attacker: <" + attUrl + ">");
                                if (attacker != null && attacker.getPosition() > 0) {
                                    bodyRaw.append("- " + Rank.byId(attacker.getPosition()));
                                }
                                bodyRaw.append("\n");
                                if (!offensive && war.getAttacker_aa() != 0) {
                                    bodyRaw.append("AA: <" + attAAUrl + "> (" + PnwUtil.getName(war.getAttacker_aa(), true) + ")\n");
                                }
                                if (attacker != null) {
                                    bodyRaw.append(attacker.toMarkdown(false, true, true));
                                }

                                bodyRaw.append("Defender: <" + defUrl + ">");
                                if (defender != null && defender.getPosition() > 0) {
                                    bodyRaw.append("- " + Rank.byId(defender.getPosition()));
                                }
                                bodyRaw.append("\n");
                                if (offensive && war.getAttacker_aa() != 0) {
                                    bodyRaw.append("AA: <" + defAAUrl + "> (" + PnwUtil.getName(war.getDefender_aa(), true) + ")\n");
                                }
                                if (defender != null) {
                                    bodyRaw.append(defender.toMarkdown(false, true, true));
                                }
                                if (dnrViolations.contains(war)) {
                                    bodyRaw.append("^ violates the `Do Not Raid` list. If you were not asked to attack (e.g. as a counter), please offer peace (Note: This is an automated message)\n");
                                }
                                Set<String> mentions = pingUserOrRoles.get(war);
                                if (mentions != null && !mentions.isEmpty()) {
                                    bodyRaw.append("^ " + StringMan.join(mentions, "\n")).append("\n");
                                }
                                bodyRaw.append("\n");
                            }
                        }

                        if (!dnrViolations.isEmpty()) {
                            footer.append("To modify the `Do Not Raid` see: " + CM.coalition.list.cmd.toSlashMention() + " / " + CM.settings.info.cmd.toSlashMention() + " with key `" + GuildKey.DO_NOT_RAID_TOP_X.name() + "`\n");
                        }

                        RateLimitUtil.queueMessage(new DiscordChannelIO(channel), new Function<IMessageBuilder, Boolean>() {
                            @Override
                            public Boolean apply(IMessageBuilder msg) {
                                if (title.length() + 10 + body.length() < 2000) {
                                    msg.embed(title, body.toString());

                                    Set<String> allMentions = new HashSet<>();
                                    for (Set<String> pings : pingUserOrRoles.values()) allMentions.addAll(pings);
                                    if (!allMentions.isEmpty()) {
                                        footer.append(StringMan.join(allMentions, " "));
                                    }

                                    if (footer.length() > 0) {
                                        msg.append(footer.toString());
                                    }
                                } else {
                                    String full = "__**" + title + "**__\n" + bodyRaw.toString();
                                    msg.append(full);
                                }
                                return true;
                            }
                        }, true, null);
                    } else {
                        for (Map.Entry<DBWar, DBWar> entry : wars) {
                            DBWar war = entry.getValue();
                            WarCard card = new WarCard(war.warId);
                            CounterStat stat = card.getCounterStat();
                            IMessageBuilder msg = card.embed(new DiscordChannelIO(channel).create(), false, true, false);


                            StringBuilder footer = new StringBuilder();
                            if (dnrViolations.contains(war)) {
                                footer.append("^ violates the `Do Not Raid` (DNR) list. If you were not asked to attack (e.g. as a counter), please offer peace (Note: This is an automated message)\n");
                                footer.append("(To modify the DNR: " + CM.coalition.list.cmd.toSlashMention() + " / " + CM.settings.info.cmd.toSlashMention() + " with key `" + GuildKey.DO_NOT_RAID_TOP_X.name() + "`\n");
                            }
                            List<String> tips = new ArrayList<>();

                            Set<String> mentions = pingUserOrRoles.get(war);
                            if (mentions != null && !mentions.isEmpty()) {
                                footer.append(StringMan.join(mentions, " ")).append("\n");
                            }


                            DBNation attacker = war.getNation(true);
                            DBNation defender = war.getNation(false);
                            if (!offensive && attacker != null && defender != null && footer.length() > 0 && defender.getPosition() >= Rank.MEMBER.id && db.isWhitelisted() && defender.getActive_m() < 15000 && !mentions.isEmpty()) {
                                Set<Integer> enemies = db.getCoalition(Coalition.ENEMIES);

                                if (stat != null && stat.type == CounterType.IS_COUNTER && !enemies.contains(attacker.getAlliance_id())) {
                                    tips.add("This is a counter for one of your wars. We can provide solely military advice and diplomatic assistance");
                                }

                                if (!enemies.contains(attacker.getAlliance_id())) {
                                    int unraidable = 50000 * defender.getCities();

                                    if (attacker.getGroundStrength(true, false) > defender.getSoldiers() * 1.7_5) {
                                        String bankUrl = "" + Settings.INSTANCE.PNW_URL() + "/alliance/id=" + defender.getAlliance_id() + "&display=bank";
                                        tips.add("Deposit your excess money in the bank or it will be stolen: " + bankUrl + " (only $" + MathMan.format(unraidable) + " is unraidable)");
                                    }
                                    if (faRole != null) {
                                        tips.add("Please ping @\u200B" + faRole.getName() + " to get help negotiating peace");
                                    }
                                    if (milcomRole != null) {
                                        tips.add("Please ping @\u200B" + milcomRole.getName() + " to get military advice");
                                    }
                                }
                                Map<Integer, JavaCity> defCities = defender.getCityMap(false, false);
                                if (attacker.getSoldiers() > defender.getSoldiers()) {
                                    for (JavaCity value : defCities.values()) {
                                        if (value.get(Buildings.BARRACKS) != 5) {
                                            tips.add("Buy max barracks and soldiers (you may need to sell off some mines)");
                                            break;
                                        }
                                    }
                                }
                                if (attacker.getShips() > 0 && defender.getShips() == 0 && defender.getAvg_infra() >= 1450 && defender.getAircraft() > attacker.getAircraft()) {
                                    boolean hasDock = false;
                                    for (JavaCity value : defCities.values()) {
                                        if (value.get(Buildings.DRYDOCK) != 0) {
                                            hasDock = true;
                                            break;
                                        }
                                    }
                                    if (!hasDock) {
                                        tips.add("Buy a drydock. When enemy ships have been reduced enough you can buy ships to break/prevent a blockade");
                                    }
                                }
                            }

                            if (footer.length() > 0 || !tips.isEmpty()) {
                                String footerMsg = footer.toString().trim();
                                if (!tips.isEmpty()) {
                                    footerMsg += " ```- " + StringMan.join(tips, "\n- ") + "```";
                                }
                                msg.append(footerMsg);
                            }
                            msg.send();
                        }
                    }
                } catch (InsufficientPermissionException e) {
                    if (offensive) {
                        db.deleteInfo(GuildKey.OFFENSIVE_WAR_CHANNEL);
                    } else {
                        db.deleteInfo(GuildKey.DEFENSE_WAR_CHANNEL);
                    }
                }
            }
        });
    }

    public BiFunction<DBWar, DBWar, Boolean> shouldAlertWar() {
        return new BiFunction<>() {
            private Set<Integer> trackedOff;
            private Set<Integer> trackedDef;

            @Override
            public Boolean apply(DBWar previous, DBWar current) {
                if (previous != null) return false;
                DBNation attacker = current.getNation(true);
                DBNation defender = current.getNation(false);
                if (attacker == null || defender == null) {
                    return false;
                }
                if (defender.getActive_m() > 10000 && defender.getAlliance_id() == 0) {
                    return false;
                }
                Boolean hideApps = db.getOrNull(GuildKey.HIDE_APPLICANT_WARS);
                if (hideApps == null && !db.hasAlliance()) {
                    hideApps = true;
                }

                if (trackedDef == null) {
                    trackedDef = getTrackedWarAlliances(false);
                }

                if (trackedDef.contains(current.getDefender_aa())) {
                    // defensive
                    if (hideApps == Boolean.TRUE && defender.getPosition() <= 1) {
                        return false;
                    }
                } else {
                    if (trackedOff == null) {
                        trackedOff = getTrackedWarAlliances(true);
                    }
                    if (trackedOff.contains(current.getAttacker_aa())) {
                        // offensive
                    }
                }
                return true;
            }
        };
    }

    public Set<Integer> getTrackedWarAlliances(boolean offensive) {
        Set<Integer> tracked = new HashSet<>();
        if (db.getOrNull(GuildKey.WAR_ALERT_FOR_OFFSHORES) != Boolean.FALSE) {
            tracked.addAll(db.getCoalition("offshore"));
        }

        Set<Integer> aaIds = db.getAllianceIds();
        if (!aaIds.isEmpty()) {
            for (int id : aaIds) {
                if (id != 0) tracked.add(id);
            }
        }

        if (offensive && (aaIds.isEmpty() || db.getOrNull(GuildKey.SHOW_ALLY_OFFENSIVE_WARS) == Boolean.TRUE)) {
            tracked.addAll(db.getCoalition(Coalition.ALLIES));
        }
        if (!offensive && (aaIds.isEmpty() || db.getOrNull(GuildKey.SHOW_ALLY_DEFENSIVE_WARS) == Boolean.TRUE)) {
            tracked.addAll(db.getCoalition(Coalition.ALLIES));
        }

        Set<Integer> untracked = db.getCoalition(Coalition.UNTRACKED);
        tracked.removeAll(untracked);
        tracked.remove(0);
        return tracked;
    }

    public Set<Project> getAllowedProjectGrantSet(DBNation nation) {
        Map<Project, Set<Grant.Requirement>> projects = getAllowedProjectGrants(nation, false);
        Set<Project> allowed = new HashSet<>();
        outer:
        for (Map.Entry<Project, Set<Grant.Requirement>> entry : projects.entrySet()) {
            for (Grant.Requirement requirement : entry.getValue()) {
                if (!requirement.apply(nation)) continue outer;
            }
            allowed.add(entry.getKey());
        }

        return allowed;
    }

    /**
     * @param nation
     * @param type
     * @param overrideSafe - Allow some safe-ish overrides (beige, TEMP, 10 cities, 10d activity, seniority, policy, reduces infra check to 10d, reduces land check to 10d, reduces warchest check to 1d, allows city cost + 30m worth of resource transfers)
     * @param overrideUnsafe - Allow some unsafe overrides (not in alliance, inactive, gray, applicant, all timeframe restrictions etc.)
     * @return
     */
    public Set<Grant> getEligableGrants(DBNation nation, DepositType type, boolean overrideSafe, boolean overrideUnsafe) {
        return new HashSet<>();
        // TODO add Grant#validateGrants() - which updates transactions and cities and city count
    }

    /*
    List of projects the alliance grants (does not check free slots)
     */
    public Map<Project, Set<Grant.Requirement>> getAllowedProjectGrants(DBNation nation, boolean overrideSafe) {
        Set<Project> projects = nation.getProjects();
        Map<Project, Set<Grant.Requirement>> allowed = new HashMap<>();

        if (nation.getCities() <= Projects.ACTIVITY_CENTER.maxCities()) allowed.put(Projects.ACTIVITY_CENTER, Collections.EMPTY_SET);

        if (nation.getCities() >= Projects.ACTIVITY_CENTER.maxCities() || nation.hasProject(Projects.ACTIVITY_CENTER)) {
            allowed.put(Projects.URANIUM_ENRICHMENT_PROGRAM, new Grant.Requirement("must be on a continent with uranium", overrideSafe, f -> Buildings.URANIUM_MINE.canBuild(f.getContinent())).toSet());
            allowed.put(Projects.ARMS_STOCKPILE, Collections.EMPTY_SET);
            allowed.put(Projects.BAUXITEWORKS, Collections.EMPTY_SET);
            allowed.put(Projects.IRON_WORKS, Collections.EMPTY_SET);
            allowed.put(Projects.EMERGENCY_GASOLINE_RESERVE, Collections.EMPTY_SET);
            allowed.put(Projects.PROPAGANDA_BUREAU, Collections.EMPTY_SET);
            allowed.put(Projects.INTELLIGENCE_AGENCY, Collections.EMPTY_SET);

            if (projects.contains(Projects.ARMS_STOCKPILE) &&
                    projects.contains(Projects.BAUXITEWORKS) &&
                    projects.contains(Projects.IRON_WORKS) &&
                    projects.contains(Projects.EMERGENCY_GASOLINE_RESERVE) &&
                    projects.contains(Projects.PROPAGANDA_BUREAU) &&
                    projects.contains(Projects.INTELLIGENCE_AGENCY)) {
            }

            allowed.put(Projects.MISSILE_LAUNCH_PAD, new Grant.Requirement("Please get the following projects first:\n" +
                    "- ARMS_STOCKPILE\n" +
                    "- BAUXITEWORKS\n" +
                    "- IRON_WORKS\n" +
                    "- EMERGENCY_GASOLINE_RESERVE\n" +
                    "- PROPAGANDA_BUREAU\n" +
                    "- INTELLIGENCE_AGENCY", overrideSafe, f -> (projects.contains(Projects.ARMS_STOCKPILE) &&
                    projects.contains(Projects.BAUXITEWORKS) &&
                    projects.contains(Projects.IRON_WORKS) &&
                    projects.contains(Projects.EMERGENCY_GASOLINE_RESERVE) &&
                    projects.contains(Projects.PROPAGANDA_BUREAU) &&
                    projects.contains(Projects.INTELLIGENCE_AGENCY))).toSet());

            Grant.Requirement cityReq = new Grant.Requirement("Must have more than 16 cities", overrideSafe, f -> f.getCities() > 16);
            {

                // c16+, 2 rss projects, mlp, iron dome
                allowed.put(Projects.ARABLE_LAND_AGENCY, cityReq.toSet());
                allowed.put(Projects.CLINICAL_RESEARCH_CENTER, cityReq.toSet());
                allowed.put(Projects.SPECIALIZED_POLICE_TRAINING_PROGRAM, cityReq.toSet());
                allowed.put(Projects.CENTER_FOR_CIVIL_ENGINEERING, cityReq.toSet());

                Grant.Requirement infraReq = new Grant.Requirement("Must have 2k infra and 2k land", false, f -> f.getAvg_infra() >= 2000);
                allowed.put(Projects.MASS_IRRIGATION, infraReq.toSet(cityReq));

                allowed.put(Projects.RECYCLING_INITIATIVE,
                        new Grant.Requirement("Must have CFCE", false, f -> projects.contains(Projects.CENTER_FOR_CIVIL_ENGINEERING)).toSet(cityReq, infraReq));
            }
        }

        return allowed;
    }

    public String getBeigeCyclingInfo(Set<BeigeReason> allowedReasons, boolean isViolation) {
        StringBuilder explanation = new StringBuilder("**What is beige?**\n" +
                "A nation defeated gets 2 more days of being on the beige color. Beige protects from new war declarations. We want to have active enemies always in war, so they don't have the opportunity to build back up.\n" +
                "\n" +
                "**Tips for avoiding unnecessary attacks:**\n" +
                "- Don't open with navals if they have units which are a threat. Ships can't attack planes, tanks or soldiers.\n" +
                "- Dont naval if you already have them blockaded\n" +
                "- Never airstrike infra, cash, or small amounts of units- wait for them to build more units\n" +
                "- If they just have some soldiers and can't get a victory against you, don't spam ground attacks.\n" +
                "- If the enemy only has soldiers (no tanks) and you have max planes. Airstriking soldiers kills more soldiers than a ground attack will.\n" +
                "- Missiles/Nukes do NOT kill any units\n\n" +
                "note: You can do some unnecessary attacks if the war is going to expire, or you need to beige them as part of a beige cycle\n\n");

        if (allowedReasons.contains(BEIGE_CYCLE)) {
            explanation.append("**What is beige cycling?**\n" +
                    "Beige cycling is when we have a weakened enemy, and 3 strong nations declared on that enemy- then 1 nation defeats them, whilst the other two sit on them whilst they are on beige\n" +
                    "When their 2 days of beige from the defeat ends, another nation declares on the enemies free slot and the next nation defeats the enemy.\n" +
                    "\n" +
                    "**Beige cycling checklist:**\n" +
                    "1. Is the enemy military mostly weakened/gone?\n" +
                    "2. Is the enemy not currently on beige?\n" +
                    "3. Do they have 3 defensive wars, with the other two attackers having enough military?\n" +
                    "4. Are you the first person to have declared?\n\n" +
                    "Tip: Save your MAP. Avoid going below 40 resistance until you are GO for beiging them\n\n");
        }
        if (!allowedReasons.isEmpty() && (allowedReasons.size() > 1 || !allowedReasons.contains(BEIGE_CYCLE))) {
            explanation.append("**Allowed beige reasons:**");
            for (BeigeReason allowedReason : allowedReasons) {
                explanation.append("\n- " + allowedReason.name() +": " + allowedReason.getDescription());
            }
            if (isViolation) {
                explanation.append("\n\n**note for members**: These are informational guidelines provided to be a war aid and in no way intended to shame anyone. Locutus isn't always correct, or necessarily accounting for the nuances of the situation. \n" +
                        "Gov members are also just as capable of not knowing something or unnecessarily beiging by not paying attention.");
            }
        }
        return explanation.toString();
    }

    public Set<BeigeReason> getAllowedReasons(int cityCount) {
        Map<CityRanges, Set<BeigeReason>> allowedReasonsMap = db.getOrNull(GuildKey.ALLOWED_BEIGE_REASONS);
        Set<BeigeReason> allowedReasons = null;
        if (allowedReasonsMap != null) {
            for (Map.Entry<CityRanges, Set<BeigeReason>> entry : allowedReasonsMap.entrySet()) {
                if (entry.getKey().contains(cityCount)) {
                    allowedReasons = entry.getValue();
                }
            }
        }
        if (allowedReasons == null) {
            allowedReasons = new HashSet<>(Arrays.asList(BeigeReason.values()));
            allowedReasons.remove(BeigeReason.NO_REASON);
            allowedReasons.remove(BeigeReason.OFFENSIVE_WAR);
            allowedReasons.remove(BeigeReason.NO_ENEMY_OFFENSIVE_WARS);
            allowedReasons.remove(BeigeReason.UNDER_C10_SLOG);
        }
        return allowedReasons;
    }

    public void beigeAlert(AbstractCursor root) {
        Set<Integer> enemies = db.getCoalition("enemies");
        if (enemies.isEmpty()) return;

        MessageChannel channelAllowed = db.getOrNull(GuildKey.ENEMY_BEIGED_ALERT);
        MessageChannel channelViolation = db.getOrNull(GuildKey.ENEMY_BEIGED_ALERT_VIOLATIONS);
        if (channelAllowed == null && channelViolation == null) return;

        DBNation attacker = Locutus.imp().getNationDB().getNation(root.getAttacker_id());
        DBNation defender = Locutus.imp().getNationDB().getNation(root.getDefender_id());
        if (!enemies.contains(defender.getAlliance_id())) return;

        DBWar war = Locutus.imp().getWarDb().getWar(root.getWar_id());

        if (channelAllowed == null) channelAllowed = channelViolation;
        if (channelViolation == null) channelViolation = channelAllowed;

        Set<BeigeReason> allowedReasons = getAllowedReasons(defender.getCities());

        Set<BeigeReason> reasons = BeigeReason.getBeigeReason(db, attacker, war, root);
        boolean allowed = false;
        for (BeigeReason reason : reasons) {
            if (allowedReasons.contains(reason)) allowed = true;
        }

        String title = "Enemy Beiged in " + (war.isAttacker(attacker) ? "Offensive" : "Defensive") + " war";;

        StringBuilder body = new StringBuilder();
        // attacker
        // defender
        // war link | defensive wars
        // defender cities
        body.append(MarkupUtil.markdownUrl("War Link", war.toUrl()));
        body.append("\nAlly: " + MarkupUtil.markdownUrl(attacker.getNation(), attacker.getNationUrl()) + " | " + MarkupUtil.markdownUrl(attacker.getAllianceName(), attacker.getAllianceUrl()));
        User user = attacker.getUser();
        if (user != null) body.append("\n").append(user.getAsMention());
        body.append("\n- Cities: " + attacker.getCities());
        body.append("\nEnemy: " + MarkupUtil.markdownUrl(defender.getNation(), defender.getNationUrl()) + " | " + MarkupUtil.markdownUrl(defender.getAllianceName(), defender.getAllianceUrl()));
        body.append("\n- Cities: " + defender.getCities());

        Map.Entry<Integer, Integer> res = war.getResistance(war.getAttacks2());
        int otherRes = war.isAttacker(attacker) ? res.getKey() : res.getValue();
        body.append("\nMy Resistance: " + otherRes);

        double lootValue = PnwUtil.convertedTotal(root.getLoot());
        if (lootValue > 0) {
            body.append("\nLoot Value: $" + MathMan.format(lootValue));
        }

        if (reasons.size() > 0) {
            body.append("\n\n**Categorization**");
            for (BeigeReason reason : reasons) {
                if (allowedReasons.contains(reason)) {
                    body.append("\n- " + reason + " -> ALLOWED");
                }
            }
            for (BeigeReason reason : reasons) {
                if (!allowedReasons.contains(reason)) {
                    body.append("\n- " + reason);
                }
            }
        }

        MessageChannel channel;
        if (allowed) {
            channel = channelAllowed;
            body.append("\n\n**Status: ALLOWED**");
        } else {
            channel = channelViolation;
            body.append("\n\n**Status: VIOLATION**");
        }

        body.append("\n\nPress 0 for war info, 1 for defender info");

        //
        String warInfoEmoji = 0 + "War Info";
        String warInfoCmd = "~" + Settings.commandPrefix(true) + "warinfo " + root.getWar_id();
        String defInfoEmoji = 1 + "Defender Info";
        String defInfoCmd = "~" + Settings.commandPrefix(true) + "warinfo " + defender.getNationUrl();

        String emoji = "Claim";
        String pending = "_" + Settings.commandPrefix(true) + "UpdateEmbed 'description:{description}\n" +
                "\n" +
                "Assigned to {usermention} in {timediff}'";
        body.append("\nPress `" + emoji + "` to assign yourself");

        DiscordUtil.createEmbedCommand(channel, title, body.toString(), warInfoEmoji, warInfoCmd, defInfoEmoji, defInfoCmd, emoji, pending);

        Role milcom = Roles.ENEMY_BEIGE_ALERT_AUDITOR.toRole(db.getGuild());
        if (!allowed) {
            boolean sendMail = GuildKey.BEIGE_VIOLATION_MAIL.getOrNull(db) != Boolean.FALSE;

            String ping = "";
            if (milcom != null) ping += milcom.getAsMention();
            if (user != null) ping += user.getAsMention();

            String explanation = getBeigeCyclingInfo(allowedReasons, !allowed);

            if (!ping.isEmpty()) {
                RateLimitUtil.queueWhenFree(channel.sendMessage("^" + ping));
                DiscordUtil.sendMessage(channel, explanation.toString());
            }

            if (sendMail) {
                DBNation nation = DBNation.getById(root.getAttacker_id());
                if (nation != null) {
                    ApiKeyPool keys = db.getMailKey();
                    if (keys != null) {
                        try {
                            nation.sendMail(keys, "Beige Cycle Violation", explanation, false);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }

//            if (!allowed) {
//                User user = attacker.getUser();
//                if (user != null) {
//                    Member member = db.getGuild().getMember(user);
//                    if (member != null) {
//                        channel.sendMessage("^ " + user.getAsMention() + " Discuss the reason for beiging with milcom. If you need any assistance in your wars please let us (know!"));
//                    }
//                }
//            }
    }

    public void setReferrer(User user, DBNation referrer) {
        ByteBuffer buf = ByteBuffer.allocate(Integer.BYTES + Long.BYTES);
        buf.putInt(referrer.getNation_id());
        buf.putLong(System.currentTimeMillis());
        db.setMeta(user.getIdLong(), NationMeta.REFERRER, buf.array());
    }

    public Map.Entry<Integer, Long> getNationTimestampReferrer(long userId) {
        ByteBuffer meta = db.getMeta(userId, NationMeta.REFERRER);
        if (meta == null) return null;

        return new AbstractMap.SimpleEntry<>(meta.getInt(), meta.getLong());
    }

    public void reward(DBNation referred, NationMeta meta, boolean onlyOnce, double[] amt, String message, Supplier<DBNation> referrerSupplier) {
        ByteBuffer metaBuf = getDb().getNationMeta(referred.getNation_id(), meta);
        if (metaBuf != null && onlyOnce) return;
        if (referred.getActive_m() > 2880) return;

        DBNation referrer = referrerSupplier.get();
        if (referrer == null) return;
        User referrerUser = referrer.getUser();
        if (referrerUser == null || !Roles.MEMBER.has(referrerUser, guild)) return;

        ByteBuffer buf = ByteBuffer.allocate(Integer.SIZE + Long.SIZE);
        buf.putInt(referrer.getNation_id());
        buf.putLong(System.currentTimeMillis());

        getDb().setMeta(referred.getNation_id(), meta, buf.array());

        if (!ignoreIncentivesForNations.contains(referrer.getNation_id())) {
            message = "Incentive Log: " + meta.name() + " for " + referred.getNation() + "\n- " + message;
            String note = "#deposit #incentive=" + meta.name();
            if (!Arrays.equals(amt, ResourceType.getBuffer())) {
                getDb().addBalance(System.currentTimeMillis(), referrer, referred.getNation_id(), note, amt);
                message += "\n- Added `" + PnwUtil.resourcesToString(amt) + "` worth: ~$" + MathMan.format(PnwUtil.convertedTotal(amt)) + " to " + referrer.getNation() + "'s account";
            }
            MessageChannel output = getDb().getResourceChannel(0);
            if (output != null) {
                message += "\n" + referrerUser.getAsMention();
                RateLimitUtil.queueWhenFree(output.sendMessage(message));
            }
        }
    }

    public GuildDB getOffshoreDB() {
        Set<Integer> aaIds = getDb().getAllianceIds();

        Set<Integer> offshores = db.getCoalition(Coalition.OFFSHORE);
        for (Integer offshoreId : offshores) {
            DBAlliance aa = DBAlliance.get(offshoreId);
            if (aa == null || !aa.exists()) continue;

            GuildDB otherDb = Locutus.imp().getGuildDBByAA(offshoreId);
            if (otherDb == null) continue;

            Set<Long> offshoring = otherDb.getCoalitionRaw(Coalition.OFFSHORING);
            if (aaIds.isEmpty()) {
                if (offshoring.contains(getDb().getIdLong())) return otherDb;
            } else {
                for (int aaId : aaIds) {
                    if (offshoring.contains((long) aaId)) return otherDb;
                }
            }
        }
        return null;
    }

    public void onInfraPurchase(DBNation nation, CityInfraLand existing, CityInfraLand newCity) {

        MessageChannel channel = db.getOrNull(GuildKey.MEMBER_REBUY_INFRA_ALERT);
        if (channel != null) {
            if (existing.infra > 100 && Math.round(existing.infra) % 50 != 0 && newCity.infra > 1000) {
                JavaCity cityBuild = nation.getCityMap(false).get(existing.cityId);
                if (cityBuild != null && cityBuild.getImpTotal() > 30) {
                    String title = "Rebuilt infra: " + existing.infra + "->" + newCity.infra;
                    StringBuilder body = new StringBuilder();
                    body.append(MarkupUtil.markdownUrl("City Link", PnwUtil.getCityUrl(existing.cityId)));
                    body.append("\n").append(nation.getNationUrlMarkup(true));
                    User user = nation.getUser();
                    if (user != null) {
                        body.append(" | ").append(user.getAsMention());
                    }
                    body.append("\nImprovement total: ").append(cityBuild.getImpTotal());

                    DiscordUtil.createEmbedCommand(channel, title, body.toString());
                }

            }
        }
    }

    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        if (trackInvites) {
            User user = event.getUser();
            getGuild().retrieveInvites().queue(new Consumer<List<Invite>>() {
                @Override
                public void accept(List<Invite> invites) {
                    Map.Entry<Integer, Long> referPair = getNationTimestampReferrer(user.getIdLong());
                    Invite inviteUsed = null;
                    boolean foundInvite = false;

                    for (Invite invite : invites) {
                        Integer previousUses = inviteUses.get(invite.getCode());
                        if (previousUses != null && previousUses == invite.getUses() - 1) {
                            if (foundInvite) {
                                inviteUsed = null;
                            } else {
                                foundInvite = true;
                                inviteUsed = invite;
                            }
                        }
                        addInvite(invite);
                    }
                    if (inviteUsed != null && referPair == null) {
                        if (!ignoreInvites.getOrDefault(inviteUsed.getCode(), false)) {
                            User inviter = inviteUsed.getInviter();
                            DBNation inviterNation = DiscordUtil.getNation(inviter);
                            if (inviterNation != null) {
                                setReferrer(user, inviterNation);
                            }
                        }
                    }
                }
            });
        }
    }

    @Subscribe
    public void onTurnChange(TurnChangeEvent event) {
        handleInactiveAudit();
    }

    public void handleInactiveAudit() {
        if (!GuildKey.MEMBER_AUDIT_ALERTS.has(db, false)) return;
        Set<AutoAuditType> disabledAudits = db.getOrNull(GuildKey.DISABLED_MEMBER_AUDITS);
        if (disabledAudits != null && disabledAudits.contains(AutoAuditType.INACTIVE)) return;

        AllianceList alliance = db.getAllianceList();
        if (alliance == null || alliance.isEmpty()) return;
        long turnStart = TimeUtil.getTurn() - 12 * 3;
        long timeCheckStart = TimeUtil.getTimeFromTurn(turnStart - 1);
        long timeCheckEnd = TimeUtil.getTimeFromTurn(turnStart);

        alliance.getNations(f -> f.getPositionEnum().id > Rank.APPLICANT.id && f.getVm_turns() == 0 && f.lastActiveMs() > timeCheckStart && f.lastActiveMs() < timeCheckEnd).forEach(nation -> {
            AlertUtil.auditAlert(nation, AutoAuditType.INACTIVE, f ->
                    AutoAuditType.INACTIVE.message
            );
        });
    }

    private final Set<Integer> sentMail = new HashSet<>();
    private static Set<Long> guildsFailedMailSend = new HashSet<>();

    @Subscribe
    public void onNationCreate(NationCreateEvent event) {
        onNewApplicant(event.getCurrent());
    }

    public void onGlobalNationCreate(NationCreateEvent event) {
        sendMail(event.getCurrent());
    }

    private boolean sentNoIAMessage = false;

    private void sendMail(DBNation current) {
        if (current.getPositionEnum().id > Rank.APPLICANT.id || current.getAgeDays() > 10) return;
        if (!sentMail.contains(current.getNation_id())) {
            sentMail.add(current.getNation_id());

            if (db.isDelegateServer()) return;
            MessageChannel output = db.getOrNull(GuildKey.RECRUIT_MESSAGE_OUTPUT, false);
            if (output == null) return;

            GuildDB root = Locutus.imp().getRootDb();
            boolean isWhitelisted = root != null && root.getCoalitionRaw("whitelisted_mail").contains(getDb().getIdLong());

            if (!isWhitelisted) {
                Set<Integer> aaIds = db.getAllianceIds();
                if (aaIds.isEmpty()) return;

                Set<DBNation> members = Locutus.imp().getNationDB().getNations(aaIds);
                members.removeIf(f -> f.getPosition() < Rank.MEMBER.id);
                members.removeIf(f -> f.getActive_m() > 2880);
                members.removeIf(f -> f.getVm_turns() > 0);
                members.removeIf(DBNation::isGray);

                if (members.isEmpty()) {
                    try {
                        RateLimitUtil.queueWhenFree(output.sendMessage("No active members found in the alliance.\n" +
                                "- Disabling `" + GuildKey.RECRUIT_MESSAGE_OUTPUT.name() + "`: enable with " + GuildKey.RECRUIT_MESSAGE_OUTPUT.getCommandMention()));
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }
                    db.deleteInfo(GuildKey.RECRUIT_MESSAGE_OUTPUT);
                    return;
                }
                if (members.size() < 10 && !db.isWhitelisted()) {
                    boolean allowed = false;
                    Map<Long, Role> roleMap = Roles.INTERNAL_AFFAIRS.toRoleMap(db);
                    Set<Member> membersWithRoles = new HashSet<>();
                    if (!roleMap.isEmpty()) {
                        Set<Role> iaRoles = new HashSet<>(roleMap.values());
                        for (Role role : iaRoles) {
                            membersWithRoles.addAll(guild.getMembersWithRoles(role));
                        }
                        for (Member member : membersWithRoles) {
                            if (member.getOnlineStatus() == OnlineStatus.ONLINE) {
                                allowed = true;
                            }
                        }
                    }
                    if (membersWithRoles.isEmpty()) {
                        try {
                            RateLimitUtil.queueWhenFree(output.sendMessage("Please set " + CM.role.setAlias.cmd.create(Roles.INTERNAL_AFFAIRS.name(), null, null, null) + " and assign it to an active gov member\n" +
                                    "- Disabling `" + GuildKey.RECRUIT_MESSAGE_OUTPUT.name() + "`: enable with " + GuildKey.RECRUIT_MESSAGE_OUTPUT.getCommandMention()));
                            db.deleteInfo(GuildKey.RECRUIT_MESSAGE_OUTPUT);
                        } catch (Throwable e) {
                            e.printStackTrace();
                        }
                        return;
                    }
                    if (!allowed) {
                        if (!sentNoIAMessage) {
                            try {
                                sentNoIAMessage = true;
                                RateLimitUtil.queueWhenFree(output.sendMessage("No INTERNAL_AFFAIRS is currently offline (note: This restriction only applies to alliances with 9 or less active members. To avoid recruitment graveyards)\n" +
                                        "- Disabling `" + GuildKey.RECRUIT_MESSAGE_OUTPUT.name() + "`: enable with " + GuildKey.RECRUIT_MESSAGE_OUTPUT.getCommandMention()));
                            } catch (Throwable e) {
                                db.deleteInfo(GuildKey.RECRUIT_MESSAGE_OUTPUT);
                                e.printStackTrace();
                            }
                        }
                        return;
                    }
                }
            }

            if (!GuildKey.RECRUIT_MESSAGE_OUTPUT.allowed(db)) {
                try {
                    RateLimitUtil.queueWhenFree(output.sendMessage("Only existant alliances can send messages\n" +
                            "- Disabling `" + GuildKey.RECRUIT_MESSAGE_OUTPUT.name() + "`: enable with " + GuildKey.RECRUIT_MESSAGE_OUTPUT.getCommandMention()));
                    db.deleteInfo(GuildKey.RECRUIT_MESSAGE_OUTPUT);
                } catch (Throwable e) {
                    e.printStackTrace();
                }
                return;
            }

            Long delay = db.getOrNull(GuildKey.RECRUIT_MESSAGE_DELAY);
            Runnable task = new CaughtRunnable() {
                @Override
                public void runUnsafe() {
                    try {
                        JsonObject response = db.sendRecruitMessage(current);
                        RateLimitUtil.queueMessage(output, (current.getNation() + ": " + response), true, 5 * 60);
                    } catch (Throwable e) {
                        try {
                            if (!guildsFailedMailSend.contains(db.getIdLong())) {
                                guildsFailedMailSend.add(db.getIdLong());
                                RateLimitUtil.queueMessage(output, (current.getNation() + " (error): " + e.getMessage()), true, 5 * 60);
                            }
                        } catch (Throwable e2) {
                            db.deleteInfo(GuildKey.RECRUIT_MESSAGE_OUTPUT);
                        }
                    }
                }
            };
            if (delay == null || delay <= 60) task.run();
            else {
                Locutus.imp().getCommandManager().getExecutor().schedule(task, delay, TimeUnit.MILLISECONDS);
            }
        }
    }

    @Subscribe
    public void onNationChangePosition(NationChangePositionEvent event) {
        DBNation nation = event.getCurrent();
        onRefer(nation);
        autoRoleMemberApp(event.getCurrent());
    }

    public void onRefer(DBNation nation) {
        if (nation.getVm_turns() > 0 || nation.getActive_m() > 2880 || nation.isGray() || nation.isBeige()) return;

        Set<Integer> aaIds = getDb().getAllianceIds();
        if (!aaIds.isEmpty() && nation != null && aaIds.contains(nation.getAlliance_id()) && nation.getPosition() > Rank.APPLICANT.id) {
            Map<ResourceType, Double> amtMap = getDb().getOrNull(GuildKey.REWARD_REFERRAL);
            if (amtMap == null) return;
            double[] amt = PnwUtil.resourcesToArray(amtMap);

            Locutus.imp().getExecutor().submit(new Runnable() {
                @Override
                public void run() {
                    { // Reward referrer
                        User user = nation.getUser();
                        if (user != null) {
                            Map.Entry<Integer, Long> pair = getNationTimestampReferrer(user.getIdLong());
                            if (pair != null) {
                                DBNation referrer = DBNation.getById(pair.getKey());
                                reward(nation, NationMeta.INCENTIVE_REFERRER, true, amt, "Inviting a nation to the alliance", () -> referrer);
                            }
                        }
                    }
                    { // Reward interviewer
                        reward(nation, NationMeta.INCENTIVE_INTERVIEWER, true, amt, "Interviewing an applicant", () -> {
                            IACategory iaCat = getDb().getIACategory();
                            if (iaCat != null) {
                                IAChannel iaChan = iaCat.get(nation);
                                if (iaChan != null) {
                                    return iaChan.getLastActiveGov(true);
                                }
                            }
                            return null;
                        });
                    }
                }
            });
        }
    }



    @Subscribe
    public void onBlockade(NationBlockadedEvent event) {
        DBNation nation = event.getBlockadedNation();
        MessageChannel channel = getDb().getOrNull(GuildKey.BLOCKADED_ALERTS);
        Role role = Roles.BLOCKADED_ALERT.toRole(guild);
        blockadeAlert(nation, event.getBlockaderNation(), channel, role, null, null, "blockaded");
    }

    private void blockadeAlert(DBNation blockaded, DBNation blockader, MessageChannel channel, Role role, Role govRole, Role escrowRole, String titleSuffix) {
        if (channel == null) return;

        IMessageIO io = new DiscordChannelIO(channel);

        String title = blockaded.getNation() + " " + titleSuffix;
        StringBuilder body = new StringBuilder();
        body.append("**Defender:** " + blockaded.getNationUrlMarkup(true) + " | " + blockaded.getAllianceUrlMarkup(true)).append("\n");
        body.append(blockaded.toMarkdown(true, true, false, true, false, false)).append("\n");
        body.append(blockaded.toMarkdown(true, true, false, false, true, false)).append("\n");
        body.append("\n");

        if (blockader != null) {
            body.append("**Blockader:** " + blockader.getNationUrlMarkup(true) + " | " + blockader.getAllianceUrlMarkup(true)).append("\n");
            body.append(blockader.toMarkdown(true, true, false, true, false, false)).append("\n");
            body.append(blockader.toMarkdown(true, true, false, false, true, false)).append("\n");
        }

        IMessageBuilder msg = io.create().embed(title, body.toString());

        if (govRole != null) {
            msg.append("\n(see below) " + govRole.getAsMention());
        }

        User user = blockaded.getUser();
        if (user != null && role != null) {
            Member member = getGuild().getMember(user);
            if (member != null && member.getRoles().contains(role)) {
                msg.append("\n(see below) " + member.getAsMention());
            }
        }
        if (escrowRole != null) {
            try {
                Map.Entry<double[], Long> escrow = getDb().getEscrowed(blockaded);
                if (escrow != null && !ResourceType.isZero(escrow.getKey())) {
                    msg.append("\nHas Escrow " + escrowRole.getAsMention());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        msg.send();
    }

    @Subscribe
    public void onUnblockade(NationUnblockadedEvent event) {
        DBNation nation = event.getBlockadedNation();
        boolean blockaded = nation.isBlockaded();
        String title;
        if (blockaded) {
            title = "Unblockaded by " + PnwUtil.getName(event.getBlockader(), false) + " (but still blockaded)";
        } else {
            title = "Unblockaded";
        }
        MessageChannel channel = getDb().getOrNull(GuildKey.UNBLOCKADED_ALERTS);
        Role role = blockaded ? null : Roles.UNBLOCKADED_ALERT.toRole(guild);
        Role govRole = blockaded ? null : Roles.UNBLOCKADED_GOV_ROLE_ALERT.toRole(guild);
        Role escrowRole = blockaded ? null : Roles.ESCROW_GOV_ALERT.toRole(guild);

        blockadeAlert(nation, event.getBlockaderNation(), channel, role, govRole, escrowRole, title);

        processEscrow(nation);

        if (!blockaded) {
            nation.deleteMeta(NationMeta.UNBLOCKADE_REASON);
        }
    }
//
//    public void procesRewards() {
//        if (!db.hasAlliance()) return;
//        Map<NationFilterString, double[]> rewards = getDb().getOrNull(GuildKey.MEMBER_REWARDS);
//        if (rewards == null || rewards.isEmpty()) return;
//        MessageChannel rssChannel = getDb().getResourceChannel(0);
//        if (rssChannel == null) return;
//        Set<Integer> aaIds = db.getAllianceIds(true);
//        if (aaIds.isEmpty()) return;
//
//        if (true) throw new UnsupportedOperationException("TODO FIXME MULTI ALLIANCE SUPPORT");
//        Set<DBNation> nations = new HashSet<>(); // TODO fixme
//    }

    public void processEscrow(DBNation receiver) {
        MessageChannel channel = getDb().getResourceChannel(0);
        if (channel != null) {
            try {
                boolean positive = false;
                Map.Entry<double[], Long> escrowedPair = getDb().getEscrowed(receiver);
                if (escrowedPair == null || ResourceType.isZero(escrowedPair.getKey())) return;

                double[] escrowed = escrowedPair.getKey();
                for (int i = 0; i < escrowed.length; i++) {
                    double amt = escrowed[i];
                    if (amt < 0) escrowed[i] = 0;
                    else if (amt > 0) {
                        positive = true;
                    }
                }
                if (positive) {
                    List<String> mentions = new ArrayList<>();
                    Role econRole = Roles.ECON.toRole(getGuild());
                    if (econRole != null) mentions.add(econRole.getAsMention());
                    User receiverUser = receiver.getUser();
                    if (receiverUser != null) mentions.add(receiverUser.getAsMention());

                    String title = "Approve Queued Transfer";
                    String body = db.generateEscrowedCard(receiver);
                    body += "\n\nSee: " + CM.escrow.withdraw.cmd.toSlashMention();

                    IMessageIO io = new DiscordChannelIO(channel);
                    io.create().embed(title, body).send();
                }

            } catch (IOException e) {
                AlertUtil.error(e.getMessage(), e);
            }
        }
    }

    public void onSetRank(User author, IMessageIO channel, DBNation nation, DBAlliancePosition rank) {

    }

    @Subscribe
    public void onPeaceChange(WarPeaceStatusEvent event) {
        MessageChannel channel = db.getOrNull(GuildKey.WAR_PEACE_ALERTS);
        if (channel == null) return;

        DBWar previous = event.getPrevious();
        DBWar current = event.getCurrent();

        DBNation attacker = current.getNation(true);
        DBNation defender = current.getNation(false);
        if (attacker != null && defender != null) {
            boolean attInactiveNone = attacker.getAlliance_id() == 0 && attacker.active_m() > 2880;
            boolean defInactiveNone = defender.getAlliance_id() == 0 && defender.active_m() > 2880;
            if (attInactiveNone || defInactiveNone) {
                return;
            }
            Boolean hideApps = db.getOrNull(GuildKey.HIDE_APPLICANT_WARS);
            if (hideApps == Boolean.TRUE && (attacker.getPosition() <= Rank.APPLICANT.id || defender.getPosition() <= Rank.APPLICANT.id)) {
               return;
            }
        }

        String title = "War " + previous.getStatus() + " -> " + current.getStatus();
        StringBuilder body = new StringBuilder();
        body.append("War: " + MarkupUtil.markdownUrl("Click Here", current.toUrl())).append("\n");
        body.append("ATT: " + PnwUtil.getMarkdownUrl(current.getAttacker_id(), false) +
                " | " + PnwUtil.getMarkdownUrl(current.getAttacker_aa(), true)).append("\n");
        body.append("DEF: " + PnwUtil.getMarkdownUrl(current.getDefender_id(), false) +
                " | " + PnwUtil.getMarkdownUrl(current.getDefender_aa(), true)).append("\n");

        System.out.println("Create peace alert");
        DiscordUtil.createEmbedCommand(channel, title, body.toString());
    }
}
