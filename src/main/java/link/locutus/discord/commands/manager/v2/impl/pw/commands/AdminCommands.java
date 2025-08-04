package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import com.politicsandwar.graphql.model.ApiKeyDetails;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntLinkedOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.*;
import link.locutus.discord.Locutus;
import link.locutus.discord.Logg;
import link.locutus.discord.apiv1.core.ApiKeyPool;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.Continent;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv3.PoliticsAndWarV3;
import link.locutus.discord.apiv3.RequestTracker;
import link.locutus.discord.apiv3.csv.DataDumpParser;
import link.locutus.discord.apiv3.enums.AlliancePermission;
import link.locutus.discord.apiv3.enums.NationLootType;
import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.LocalValueStore;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.*;
import link.locutus.discord.commands.manager.v2.binding.annotation.Timestamp;
import link.locutus.discord.commands.manager.v2.binding.bindings.PlaceholderCache;
import link.locutus.discord.commands.manager.v2.command.CommandBehavior;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.command.StringMessageBuilder;
import link.locutus.discord.commands.manager.v2.impl.discord.DiscordChannelIO;
import link.locutus.discord.commands.manager.v2.impl.discord.binding.DiscordBindings;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.HasApi;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.commands.manager.v2.impl.pw.CommandManager2;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.PWBindings;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.NationPlaceholders;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.AllianceCommands;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.NationCommands;
import link.locutus.discord.commands.sync.SyncTaxes;
import link.locutus.discord.commands.war.WarCatReason;
import link.locutus.discord.commands.war.WarCategory;
import link.locutus.discord.commands.war.WarRoom;
import link.locutus.discord.commands.war.WarRoomUtil;
import link.locutus.discord.config.Messages;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.ForumDB;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.NationDB;
import link.locutus.discord.db.TaxDeposit;
import link.locutus.discord.db.entities.*;
import link.locutus.discord.db.entities.announce.AnnounceType;
import link.locutus.discord.db.entities.announce.Announcement;
import link.locutus.discord.db.entities.metric.AllianceMetric;
import link.locutus.discord.db.entities.nation.DBNationSnapshot;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.db.guild.GuildSetting;
import link.locutus.discord.db.guild.SheetKey;
import link.locutus.discord.db.handlers.GuildCustomMessageHandler;
import link.locutus.discord.event.Event;
import link.locutus.discord.gpt.GPTUtil;
import link.locutus.discord.pnw.AllianceList;
import link.locutus.discord.pnw.CityRanges;
import link.locutus.discord.pnw.NationList;
import link.locutus.discord.pnw.PNWUser;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.*;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.io.PagePriority;
import link.locutus.discord.util.io.PageRequestQueue;
import link.locutus.discord.util.offshore.Auth;
import link.locutus.discord.util.offshore.OffshoreInstance;
import link.locutus.discord.util.offshore.TransferResult;
import link.locutus.discord.util.sheet.SpreadSheet;
import link.locutus.discord.util.task.EditAllianceTask;
import link.locutus.discord.util.task.mail.AlertMailTask;
import link.locutus.discord.util.task.mail.MailApiResponse;
import link.locutus.discord.util.task.mail.MailApiSuccess;
import link.locutus.discord.util.task.multi.GetUid;
import link.locutus.discord.util.task.multi.SnapshotMultiData;
import link.locutus.discord.util.task.roles.AutoRoleInfo;
import link.locutus.discord.util.update.AllianceListener;
import link.locutus.discord.util.update.WarUpdateProcessor;
import link.locutus.discord.web.commands.WM;
import link.locutus.discord.web.jooby.WebRoot;
import link.locutus.discord.web.jooby.handler.CommandResult;
import link.locutus.wiki.WikiGenHandler;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.NewsChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildMessageChannel;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Type;
import java.math.BigInteger;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.sql.SQLException;
import java.text.ParseException;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static link.locutus.discord.commands.manager.v2.binding.annotation.Kw.*;

public class AdminCommands {
    @Command
    @RolePermission(value = Roles.ADMIN, root = true)
    public String cullInactiveGuilds(@Me JSONObject command, @Me GuildDB thisDb, @Me IMessageIO io,
                                     @Default Set<GuildDB> servers, @Switch("s") SpreadSheet sheet, @Switch("f") boolean force) throws GeneralSecurityException, IOException {
        if (servers != null) {
            if (!force) {
                io.create()
                        .embed("Guild Culling Confirmation",
                                "You are about to leave " + servers.size() + " guilds.\n" +
                                        "This action is irreversible and will remove the bot from these guilds.\n" +
                                        "If you are sure, use the `!cull <guilds> -f` command to force this action.")
                        .confirmation(command).send();
                return null;
            }
            int delay = 1;
            for (GuildDB db : servers) {
                Locutus.cmd().getExecutor().schedule(() -> {
                    TextChannel channel = db.getNotifcationChannel();
                    if (channel != null && channel.canTalk()) {
                        RateLimitUtil.queue(channel.sendMessage("Leaving this guild due to inactivity. Please re-invite this bot if you want to use it again!\n" +
                                "<https://discord.com/api/oauth2/authorize?client_id=" + Settings.INSTANCE.APPLICATION_ID + "&permissions=395606879321&scope=bot>\n" +
                                "- <https://github.com/xdnw/locutus/wiki>"));
                    }
                    RateLimitUtil.queue(db.getGuild().leave());
                }, delay, TimeUnit.SECONDS);
                delay += 2; // Increase delay for each guild to avoid hitting rate limits
            }
            return "Left all specified servers";
        }

        List<GuildDB> toCheck = new ObjectArrayList<>(Locutus.imp().getGuildDatabases().values());
        if (toCheck.isEmpty()) {
            throw new IllegalArgumentException("No guilds to check");
        }

        if (sheet == null) {
            sheet = SpreadSheet.create(thisDb, SheetKey.GUILD_CULLING);
        }

        sheet.setHeader(List.of(
                "guild_id",
                "Guild Name",
                "Member Count",
                "Alliance Ids",
                "Cannot Talk",
                "No Settings",
                "Member Count",
                "Alliance Deleted",
                "Has Registered User"
        ));

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime oneYearAgo = now.minusDays(365);
        long oneYearAgoMs = oneYearAgo.toEpochSecond() * 1000L;

        int numGuildsToCull = 0;
        for (GuildDB other : toCheck) {
            boolean hasNoSettings = other.getInfoMap().isEmpty();

            boolean hasRecentMessage = false;
            boolean cannotTalk = true;
            {
                for (GuildMessageChannel channel : other.getGuild().getTextChannels()) {
                    if (cannotTalk && channel.canTalk()) {
                        cannotTalk = false;
                    }
                    long latestSnowflake = channel.getLatestMessageIdLong();
                    if (latestSnowflake != 0) {
                        long latestMs = net.dv8tion.jda.api.utils.TimeUtil.getTimeCreated(latestSnowflake).toEpochSecond() * 1000L;
                        if (latestMs > oneYearAgoMs) {
                            hasRecentMessage = true;
                        }
                    }
                    long channelCreated = channel.getTimeCreated().toEpochSecond() * 1000L;
                    if (channelCreated > oneYearAgoMs) {
                        hasRecentMessage = true;
                    }
                }
            }

            int memberCount = 0;

            boolean allianceDeleted = GuildKey.ALLIANCE_ID.getOrNull(other) != null && !other.isValidAlliance();
            boolean isValidAlliance = other.isValidAlliance();


            boolean joinedLongTimeAgo = other.getGuild().getSelfMember().getTimeJoined().isBefore(oneYearAgo);

            boolean hasRecentMember = false;
            boolean hasRecentAdmin = false;
            boolean hasRegisteredAdmin = false;
            boolean hasRegisteredUser = false;

            for (Member member : other.getGuild().getMembers()) {
                if (member.getUser().isBot() || member.getUser().isSystem()) continue;
                memberCount++;
                DBNation nation = DiscordUtil.getNation(member.getIdLong());
                if (nation != null) {
                    if (!hasRegisteredAdmin && (member.isOwner() || member.getRoles().stream().anyMatch(r -> r.getPermissions().contains(Permission.ADMINISTRATOR)))) {
                        hasRegisteredAdmin = true;
                    }
                    hasRegisteredUser = true;
                }
                if (member.getUser().isBot()) continue;
                if (member.getTimeJoined().isAfter(oneYearAgo)) {
                    hasRecentMember = true;
                    if (member.isOwner() || member.getRoles().stream().anyMatch(r -> r.getPermissions().contains(Permission.ADMINISTRATOR))) {
                        hasRecentAdmin = true;
                    }
                }
            }

            boolean hasRecentRole = false;
            for (Role role : other.getGuild().getRoles()) {
                if (role.getTimeCreated().toEpochSecond() * 1000L > oneYearAgoMs) {
                    hasRecentRole = true;
                    break;
                }
            }

            boolean hasOffshoreAccount = other.getOffshore() != null;

            if (hasRecentMessage || isValidAlliance || !joinedLongTimeAgo || hasRecentMember || hasRecentAdmin || hasRegisteredAdmin || hasRecentRole || hasOffshoreAccount) {
                continue;
            }

            boolean lowMemberCount = memberCount < 4; // Arbitrary threshold, can be adjusted

            List<Object> row = List.of(
                    other.getIdLong() + "",
                    other.getGuild().getName(),
                    memberCount,
                    StringMan.getString(other.getAllianceIds()),
                    cannotTalk,
                    hasNoSettings,
                    !lowMemberCount,
                    allianceDeleted,
                    hasRegisteredUser
            );
            sheet.addRow(row);
            numGuildsToCull++;
        }

        sheet.updateClearCurrentTab();
        sheet.updateWrite();

        String title = numGuildsToCull + " guilds to cull";
        String body = "Checked " + toCheck.size() + " guilds.\n" +
                "Found " + numGuildsToCull + " guilds that are inactive or have no settings.\n" +
                "Please review the sheet for details";

        IMessageBuilder msg = sheet.attach(io.create(), "guild_culling");
        msg.embed(title, body);
        msg.send();

        return null;
    }

    @Command
    @RolePermission(value = Roles.ADMIN, root = true)
    public String newOffshore(@Me IMessageIO io, @Me DBNation nation, DBAlliance alliance) throws IOException {
        if (!Settings.INSTANCE.TEST) {
            throw new IllegalArgumentException("Creating new offshore is disabled in production");
        }
        Auth auth = nation.getAuth(true);
        if (alliance.getId() != nation.getAlliance_id()) {
            throw new IllegalArgumentException("Your nation is not in the alliance you specified (if it is, please use the `!sync <nation>` command and try again)");
        }
        if (alliance.getMemberDBNations().size() > 1) {
            throw new IllegalArgumentException("Alliance has more than 1 member. Please create a new alliance with only your nation");
        }
        // Send funds to new alliance
        OffshoreInstance offshore = Locutus.imp().getRootBank();
        DBAlliance offshoreAA = offshore.getAlliance();
        Auth offshoreAuth = offshoreAA.getAuth(AlliancePermission.WITHDRAW_BANK);
        User offshoreUser = offshoreAuth.getNation().getUser();

        Map<ResourceType, Double> stockpile = offshoreAA.getStockpile();
        if (stockpile.isEmpty()) {
            throw new IllegalArgumentException("Offshore alliance has no stockpile (send $1 to it for the purposes of this command)");
        }
        io.send("Checked stockpile");
        TransferResult result = offshore.transferUnsafe(offshoreAuth, alliance, stockpile, "#ignore");
        io.send("Sent stockpile " + result.toLineString());
        if (!result.getStatus().isSuccess()) {
            throw new IllegalArgumentException("Failed to send funds to new alliance (send $1 to the offshore and try again?):\n" + result.toEmbedString());
        }
        Map<ResourceType, Double> stockpileTest = offshoreAA.getStockpile();
        io.send("Checked new stockpile: " + ResourceType.toString(stockpileTest));
        if (!stockpileTest.isEmpty()) {
            throw new IllegalArgumentException("Stockpile was sent, but AA is not empty. Please contact support, or try again.");
        }
        // leave alliance ingame and apply to new aa
        offshoreAuth.leaveAlliance(offshoreAA);
        io.send("Left offshore alliance");
        offshoreAuth.apply(alliance);
        io.send("Applied to new alliance");
        return BankCommands.addOffshore(io, offshoreUser, offshore.getGuildDB(), offshoreAuth.getNation(), null, alliance, false, false, true);
    }

    @Command
    @RolePermission(value = Roles.ADMIN, root = true)
    public String upsertCommands(@Me Guild guild) {
        Locutus.imp().getSlashCommands().register(guild);
        return "Done! (Restart your discord client to see changes)";
    }

    @Command
    @RolePermission(value = Roles.ADMIN, root = true)
    public String syncCityAvg(@Default Double force_value) {
        if (force_value != null) {
            PW.City.CITY_AVERAGE = force_value;
            Locutus.imp().getDiscordDB().setCityAverage(force_value);
            return "Force set city average to " + force_value;
        } else {
            PW.City.updateCityAverage();
            return "Updated city average to " + PW.City.CITY_AVERAGE;
        }
    }

    @Command
    @RolePermission(value = Roles.ADMIN, root = true)
    public String syncAlliances() {
        Locutus.imp().getNationDB().updateAlliances(f -> {}, Event::post);
        return "Done!";
    }


    @Command(desc = "Sync city refund data")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String syncCityRefund() throws IOException, ParseException {
        Set<DBNation> toSave = new HashSet<>();

        DataDumpParser snapshot = Locutus.imp().getDataDumper(true);
        for (Map.Entry<Integer, DBNationSnapshot> entry : snapshot.getNations(TimeUtil.getDay()).entrySet()) {
            DBNationSnapshot nation = entry.getValue();
            double reduction = nation._costReduction();
            if (reduction <= 0) continue;
            DBNation real = DBNation.getById(entry.getKey());
            if (real == null) continue;
            if (real.getCities() > nation.getCities()) {
                reduction -= PW.City.cityCost(real, nation.getCities(), real.getCities());
            }
            if (reduction <= 0) continue;
            real.edit().setCostReduction(reduction);
            toSave.add(real);
        }
        Locutus.imp().getNationDB().saveNations(toSave);
        return "Updated and saved " + toSave.size() + " nations";
    }

    @Command(desc = "Force update of research data")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String updateResearch(@Me IMessageIO io, Set<DBNation> nations) throws IOException, ParseException {
        CompletableFuture<IMessageBuilder> msgFuture = (io.sendMessage("Updating research. Please wait..."));
        long start = System.currentTimeMillis();
        List<DBNation> nationList = new ArrayList<>(nations);
        int backoff = 1000;
        for (int i = 0; i < nationList.size(); i++) {
            DBNation nation = nationList.get(i);
            try {
                if (start + 10000 < System.currentTimeMillis()) {
                    start = System.currentTimeMillis();
                    io.updateOptionally(msgFuture, "Updating research for " + nation.getMarkdownUrl() + " (" + (i + 1) + "/" + nationList.size() + ")");
                }
                nation.updateResearch();
                Thread.sleep(350);
                backoff = 1000;
            } catch (Exception e) {
                e.printStackTrace();
                io.create().append("Failed to update research for " + nation.getMarkdownUrl() + ": " + e.getMessage()).send();
                try {
                    Thread.sleep(backoff);
                    backoff += 1000;
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
            }
        }
        Locutus.imp().getNationDB().saveNations(nations);
        return "Updated and saved " + nations.size() + " nations";
    }

    @Command(desc = "Set bot profile picture")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String setProfile(String url) throws IOException, ParseException {
        if (!ImageUtil.isDiscordImage(url)) {
            throw new IllegalArgumentException("Invalid discord image url: `" + url + "`");
        }
        BufferedImage img = ImageUtil.image(url);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        baos.flush();
        baos.close();
        byte[] imageInByte = baos.toByteArray();

        Locutus.imp().getServer().getJDA().getSelfUser().getManager().setAvatar(Icon.from(imageInByte)).complete();

        return "Set avatar to `" + url + "`";
    }

    @Command(desc = "Set bot name")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String setBotName(String name) throws IOException, ParseException {
        Locutus.imp().getServer().getJDA().getSelfUser().getManager().setName(name).complete();
        return "Set bot name to `" + name + "`";
    }

        @Command(desc = "Sync and debug war rooms",
    keywords = {WAR, ROOM, SYNC, CHANNEL, UPDATE, WARCAT, CATEGORY})
    @RolePermission(value = {Roles.ADMIN, Roles.MILCOM}, any = true)
    public String syncWarrooms(@Me IMessageIO io, @Me JSONObject command, @Me GuildDB db, @Switch("f") boolean force) throws IOException {
        long start = System.currentTimeMillis();

        StringBuilder response = new StringBuilder();
        List<String> errors = new ArrayList<>();
        List<String> notes = new ArrayList<>();

        WarCategory cat = db.getWarChannel(true);
        Guild guild = cat.getGuild();

        Set<Integer> aaIds = cat.getTrackedAllianceIds();
        if (aaIds.isEmpty()) {
            errors.add("No alliances being tracked. Set the `" + Coalition.ALLIES.name() + "` coalition: " + CM.coalition.add.cmd.toSlashMention());
        } else {
            response.append("**Alliances:**: `");
            response.append(aaIds.stream().map(f -> DBAlliance.getOrCreate(f).getMarkdownUrl()).collect(Collectors.joining(",")));
            response.append("`\n");
        }

        if (guild.getChannels().size() >= 500) {
            errors.add("Server at max channels (500)");
        }
        if (guild.getCategories().size() >= 50) {
            errors.add("Server at max categories (50)");
        }

        response.append("**Server:** `" + guild.getName() + "` | `" + guild.getId() + "`");
        if (guild.getIdLong() != db.getIdLong()) response.append(" (WAR_SERVER is set)");
        response.append("\n");

        // list the categories
        Member self = guild.getSelfMember();
        Set<Category> categories = cat.getCategories();

        Map<Category, Set<Permission>> permissionsMissing = new LinkedHashMap<>();
        Map<Category, CityRanges> ranges = new LinkedHashMap<>();

        Permission[] catPerms = WarRoomUtil.CATEGORY_PERMISSIONS;
        for (Category category : categories) {
            EnumSet<Permission> selfPerms = self.getPermissions(category);
            for (Permission perm : catPerms) {
                if (!selfPerms.contains(perm)) {
                    permissionsMissing.computeIfAbsent(category, k -> new ObjectLinkedOpenHashSet<>()).add(perm);
                }
            }
            CityRanges range = WarRoomUtil.getRangeFromCategory(category);
            if (range != null) {
                ranges.put(category, range);
            }
        }

        if (categories.isEmpty()) {
            errors.add("No categories found. " +
                    "Create one starting with `warcat`\n" +
                    "Grant the bot the perms: `" + Arrays.stream(catPerms).map(Permission::getName).collect(Collectors.joining(", ")) + "`\n");
        } else {
            response.append("**" + categories.size() + " categories:**\n");
            for (Category category : categories) {
                response.append("- " + category.getName());
                CityRanges cityRange = ranges.get(category);
                if (cityRange != null) {
                    response.append(" | city:" + cityRange);
                }
                Set<Permission> lacking = permissionsMissing.getOrDefault(category, Collections.emptySet());
                if (!lacking.isEmpty()) {
                    response.append(" | missing: `" + lacking.stream().map(Permission::getName).collect(Collectors.joining(",")) + "`");
                }
                response.append("\n");
            }
        }

        // Say War rooms can be sorted by cities by naming the category e.g. `warcat-c1-10`
        if (ranges.isEmpty()) {
            notes.add("No city ranges found. Create a category starting with `warcat` and ending with a city range e.g. `warcat-c1-10`");
            notes.add("If a sorted category is full, the next free category will be used, even if a room does not match the filter");
            notes.add("You may create multiple categories with the same or overlapping filters");
        }

        Map<DBWar, WarCatReason> warsLog = new LinkedHashMap<>();
        Map<DBNation, WarCatReason> inactiveRoomLog = new LinkedHashMap<>();
        Map<DBNation, WarCatReason> activeRoomLog = new LinkedHashMap<>();
        Set<DBNation> toCreate = new ObjectLinkedOpenHashSet<>();
        Map<Integer, WarCatReason> toDelete = new LinkedHashMap<>();
        Map<DBNation, TextChannel> toReassign = new LinkedHashMap<>();
        Map<Integer, Set<TextChannel>> duplicates = new LinkedHashMap<>();

        cat.sync(warsLog, inactiveRoomLog, activeRoomLog, toCreate, toDelete, toReassign, duplicates, force);
        if (!warsLog.isEmpty()) {
            response.append("\n**" + warsLog.size() + " wars:**\n");
            for (Map.Entry<DBWar, WarCatReason> entry : warsLog.entrySet()) {
                DBWar war = entry.getKey();
                WarCatReason reason = entry.getValue();
                response.append("- " + war.warId + ": " + reason.name() + " - " + reason.getReason() + "\n");
            }
        }
        if (!inactiveRoomLog.isEmpty()) {
            response.append("\n**" + inactiveRoomLog.size() + " inactive rooms:**\n");
            for (Map.Entry<DBNation, WarCatReason> entry : inactiveRoomLog.entrySet()) {
                DBNation nation = entry.getKey();
                WarCatReason reason = entry.getValue();
                response.append("- " + nation.getNation() + ": " + reason.name() + " - " + reason.getReason() + "\n");
            }
        }
        if (!activeRoomLog.isEmpty()) {
            response.append("\n**" + activeRoomLog.size() + " active rooms:**\n");
            for (Map.Entry<DBNation, WarCatReason> entry : activeRoomLog.entrySet()) {
                DBNation nation = entry.getKey();
                WarCatReason reason = entry.getValue();
                response.append("- " + nation.getNation() + ": " + reason.name() + " - " + reason.getReason() + "\n");
            }
        }
        if (!toCreate.isEmpty()) {
            response.append("\n**" + toCreate.size() + " rooms to create:**\n");
            for (DBNation nation : toCreate) {
                response.append("- " + nation.getMarkdownUrl() + "\n");
            }
        }
        if (!toDelete.isEmpty()) {
            response.append("\n**" + toDelete.size() + " rooms to delete:**\n");
            for (Map.Entry<Integer, WarCatReason> entry : toDelete.entrySet()) {
                int id = entry.getKey();
                WarCatReason reason = entry.getValue();
                response.append("- " + PW.getMarkdownUrl(id, false) + ": " + reason.name() + " - " + reason.getReason() + "\n");
            }
        }
        if (!toReassign.isEmpty()) {
            response.append("\n**" + toReassign.size() + " rooms to reassign:**\n");
            for (Map.Entry<DBNation, TextChannel> entry : toReassign.entrySet()) {
                DBNation nation = entry.getKey();
                TextChannel channel = entry.getValue();
                response.append("- " + nation.getMarkdownUrl() + " -> " + channel.getAsMention() + "\n");
            }
        }
        if (!duplicates.isEmpty()) {
            response.append("\n**" + duplicates.size() + " duplicate channels:**\n");
            for (Map.Entry<Integer, Set<TextChannel>> entry : duplicates.entrySet()) {
                int id = entry.getKey();
                Set<TextChannel> channels = entry.getValue();
                response.append("- " + PW.getMarkdownUrl(id, false) + ": " + channels.stream().map(Channel::getAsMention).collect(Collectors.joining(", ")) + "\n");
            }
        }

        StringBuilder full = new StringBuilder();
        if (!errors.isEmpty()) {
            full.append("\n**" + errors.size() + " errors:**\n");
            for (String error : errors) {
                full.append("- " + error + "\n");
            }
        }
        if (!notes.isEmpty()) {
            full.append("\n**" + notes.size() + " notes:**\n");
            for (String note : notes) {
                full.append("- " + note + "\n");
            }
        }
        full.append(response);

        if (!force) {
            String title = "Confirm sync war rooms";
            String body = "See the attached log file for details on room creation, deletion";
            io.create().confirmation(title, body, command).file("warcat.txt", full.toString()).send();
            return null;
        }
        long diff = System.currentTimeMillis() - start;
        io.create().append("Sync war rooms complete. Took: " + diff + "ms\n" +
                "See the attached log file for task output. To sort rooms, see: " + CM.war.room.sort.cmd.toSlashMention())
                .file("warcat.txt", full.toString()).send();

        return null;
    }

    @Command(desc = "Regenerate the static command classes")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String savePojos() throws IOException {
        CommandManager2 manager = Locutus.cmd().getV2();
        manager.getCommands().savePojo(null, CM.class, "CM");
        manager.getNationPlaceholders().getCommands().savePojo(null, NationCommands.class, "NationCommands");
        manager.getAlliancePlaceholders().getCommands().savePojo(null, AllianceCommands.class, "AllianceCommands");
        if (WebRoot.getInstance() != null) {
            WebRoot.getInstance().getPageHandler().getCommands().savePojo(null, WM.class, "WM");
        }
        return "Done!";
    }
    @Command(desc = "Run the militarization alerts task")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String runMilitarizationAlerts() {
        AllianceListener.runMilitarizationAlerts();
        return "Done! (see console)";
    }

    @Command(desc = "Switch a channel to a subscription channel in multiple servers")
    @RolePermission(value = Roles.ADMIN, root = true)
    @Ephemeral
    public String unsetNews(@Me IMessageIO io, @Me JSONObject command,
            GuildSetting setting, Set<GuildDB> guilds, MessageChannel news_channel,
            @Switch("e") boolean unset_on_error,
            @Switch("f") boolean force) {
        if (!(news_channel instanceof NewsChannel)) {
            throw new IllegalArgumentException("Invalid channel type: " + news_channel.getType() + ". Must be a news channel");
        }
        NewsChannel subscribe = (NewsChannel) news_channel;
        GuildDB thisDb = Locutus.imp().getGuildDB(subscribe.getGuild());
        Invite invite = thisDb.getInvite(true);
        // ensure type is instance of Channel
        if (!setting.isChannelType()) {
            return "Invalid setting type: " + setting.getType() + ". Not a channel";
        }

        String errorMessage = "Failed to subscribe to channel: " + subscribe.getAsMention() + "(#" + subscribe.getName() + ") in " + thisDb.getGuild() + " " + invite.getUrl() + "\n" +
                "Please join the server and subscribe manually for future updates";

        List<String> infoMsgs = new ArrayList<>();
        List<String> errorMsgs = new ArrayList<>();

        for (GuildDB otherDb : guilds) {
            if (subscribe.getGuild().getIdLong() == otherDb.getIdLong()) continue;
            String raw = setting.getRaw(otherDb, false);
            if (raw == null) continue;
            Object value = setting.getOrNull(otherDb);
            if (value == null) {
                String msg = otherDb.getGuild().toString() + ": Invalid value `" + raw + "`. See " + CM.admin.settings.unset.cmd.toSlashMention();
                if (unset_on_error) {
                    otherDb.deleteInfo(setting);
                    msg += " (deleted setting)";
                    TextChannel backupChannel = otherDb.getNotifcationChannel();
                    if (backupChannel != null) RateLimitUtil.queue(backupChannel.sendMessage(msg + "\n" + errorMessage));
                }
                errorMsgs.add(msg);
                continue;
            }
            Channel channel = (Channel) value;
            Member self = otherDb.getGuild().getSelfMember();
            if (!(channel instanceof GuildMessageChannel gmc) || !gmc.canTalk()) {
                String msg = otherDb.getGuild().toString() + ": Bot does not have access to " + channel.getAsMention();
                if (force && unset_on_error) {
                    otherDb.deleteInfo(setting);
                    msg += " (deleted setting)";
                    TextChannel backupChannel = otherDb.getNotifcationChannel();
                    if (backupChannel != null) RateLimitUtil.queue(backupChannel.sendMessage(msg + "\n" + errorMessage));
                }
                errorMsgs.add(msg);
                continue;
            }
            if (!(gmc instanceof TextChannel tc)) {
                String msg = otherDb.getGuild().toString() + ": Not set to a Text Channel " + channel.getAsMention();
                if (force && unset_on_error) {
                    otherDb.deleteInfo(setting);
                    msg += " (deleted setting)";
                    TextChannel backupChannel = otherDb.getNotifcationChannel();
                    if (backupChannel != null) RateLimitUtil.queue(backupChannel.sendMessage(msg + "\n" + errorMessage));
                }
                errorMsgs.add(msg);
                continue;
            }
            if (!self.hasPermission(Permission.MANAGE_WEBHOOKS)) {
                String msg = otherDb.getGuild().toString() + ": Bot does not have permission to manage webhooks in " + channel.getAsMention();
                if (force && unset_on_error) {
                    otherDb.deleteInfo(setting);
                    msg += " (deleted setting)";
                    RateLimitUtil.queue(gmc.sendMessage(msg + "\n" + errorMessage));
                }
                errorMsgs.add(msg);
                continue;
            }

            String successMsg = otherDb.getGuild().toString() + ": Unset " + setting.name() + " from " + channel.getAsMention();
            if (!force) {
                infoMsgs.add(successMsg);
                continue;
            }
            try {
                Webhook.WebhookReference result = RateLimitUtil.complete(subscribe.follow(tc));
                otherDb.deleteInfo(setting);
                infoMsgs.add(successMsg + " | " + result);
            } catch (Exception e) {
                String msg = otherDb.getGuild().toString() + ": " + e.getMessage();
                errorMsgs.add(msg);
                if (unset_on_error) {
                    otherDb.deleteInfo(setting);
                    msg += " (deleted setting)";
                    RateLimitUtil.queue(gmc.sendMessage(msg + "\n" + errorMessage));
                }
                continue;

            }
        }
        if (!force) {
            String title = "Confirm unset news";
            StringBuilder body = new StringBuilder("Using news from " + subscribe.getAsMention() + " in " + thisDb.getGuild() + "\n");
            body.append("Unset on error: `" + unset_on_error + "`\n");
            body.append("Servers Subscribed: `" + infoMsgs.size() + "`\n");
            body.append("Errors: `" + errorMsgs.size() + "`\n");
            io.create().confirmation(title, body.toString(), command)
                    .file("info.txt", String.join("\n", infoMsgs))
                    .file("errors.txt", String.join("\n", errorMsgs))
                    .send();
            return null;
        }
        io.create().append("Done! " + infoMsgs.size() + " servers subscribed" +
                " | " + errorMsgs.size() + " errors\n" +
                        "See attached files for details")
                .file("info.txt", String.join("\n", infoMsgs))
                .file("errors.txt", String.join("\n", errorMsgs))
                .send();
        return null;
    }

    @Command(desc = "Bulk unset a guild setting in multiple servers which are invalid based on the provided options")
    @RolePermission(value = Roles.ADMIN, root = true)
    @Ephemeral
    public String unsetKeys(@Me IMessageIO io, @Me JSONObject command,
            Set<GuildSetting> settings, Set<GuildDB> guilds,
                                @Switch("t") boolean unset_cant_talk,
                                @Switch("i") boolean unset_null,
                                @Switch("p") boolean unset_key_no_perms,
                                @Switch("g") boolean unset_invalid_aa,
                                @Switch("a") boolean unset_all,
                                @Switch("v") boolean unset_validate,
                                @Switch("m") String unsetMessage,
                                @Switch("f") boolean force) {
        Map<Guild, Map<GuildSetting, Set<String>>> unsetReasons = new LinkedHashMap<>();

        Set<GuildSetting> channelTypes = new ObjectLinkedOpenHashSet<>();
        Set<GuildSetting> nonChanTypes = new ObjectLinkedOpenHashSet<>();
        for (GuildSetting setting : settings) {
            Type type = setting.getType().getType();
            if (setting.isChannelType()) {
                channelTypes.add(setting);
            } else {
                nonChanTypes.add(setting);
            }
        }
        for (GuildDB otherDb : guilds) {
            Map<GuildSetting, Boolean> isUnset = new LinkedHashMap<>();
            BiConsumer<GuildSetting, String> unset = (setting, reason) -> {
                if (force) {
                    if (isUnset.getOrDefault(setting, false)) return;
                    isUnset.put(setting, true);
                    String previousValue = setting.getRaw(otherDb, false);
                    Object value = setting.getOrNull(otherDb, false);

                    otherDb.deleteInfo(setting);

                    String message = setting.name() + ": " + reason + ": " + (unsetMessage == null ? "" : unsetMessage) + "\nPrevious value: `" + previousValue + "`";
                    TextChannel sendTo = null;
                    if (value instanceof TextChannel tc && tc.canTalk()) sendTo = tc;
                    if (sendTo == null) sendTo = otherDb.getNotifcationChannel();
                    if (sendTo != null) {
                        try {
                            RateLimitUtil.queue(sendTo.sendMessage(message));
                        } catch (Exception ignore) {
                        }
                    }
                }
            };

            Guild otherGuild = otherDb.getGuild();
            Map<GuildSetting, Set<String>> byGuild = unsetReasons.computeIfAbsent(otherGuild, k -> new LinkedHashMap<>());
            // only channel modes
            for (GuildSetting setting : channelTypes) {
                Channel channel = (Channel) setting.getOrNull(otherDb);
                if (channel == null) continue;
                if (unset_cant_talk) {
                    if (!(channel instanceof GuildMessageChannel gmc) || !gmc.canTalk()) {
                        if (force) unset.accept(setting, "No Talk Permissions in " + channel.getAsMention());
                        byGuild.computeIfAbsent(setting, k -> new ObjectLinkedOpenHashSet<>()).add("Can't talk");
                        continue;
                    }
                }
            }
//            // only non channel modes
//            for (GuildSetting setting : nonChanTypes) {
//
//            }
            // all type modes
            for (GuildSetting setting : settings) {
                String raw = setting.getRaw(otherDb, false);
                if (raw == null) continue;
                Object value = setting.getOrNull(otherDb);
                if (unset_null) {
                    if (value == null) {
                        if (force) unset.accept(setting, "Invalid value (null)");
                        byGuild.computeIfAbsent(setting, k -> new ObjectLinkedOpenHashSet<>()).add("Null");
                        continue;
                    }
                }
                if (unset_key_no_perms) {
                    String notAllowedReason = "Not allowed";
                    boolean allowed = false;
                    try {
                        allowed = setting.allowed(otherDb, true);
                    } catch (IllegalArgumentException e) {
                        notAllowedReason = e.getMessage();
                    }
                    if (!allowed) {
                        if (force) unset.accept(setting, notAllowedReason);
                        byGuild.computeIfAbsent(setting, k -> new ObjectLinkedOpenHashSet<>()).add(notAllowedReason);
                        continue;
                    }
                }
                if (unset_validate) {
                    String validateReason = "Invalid Value (validation error)";
                    boolean valid = false;
                    try {
                        setting.validate(otherDb, null, value);
                        valid = true;
                    } catch (IllegalArgumentException e) {
                        validateReason = e.getMessage();
                    }
                    if (!valid) {
                        if (force) unset.accept(setting, validateReason);
                        byGuild.computeIfAbsent(setting, k -> new ObjectLinkedOpenHashSet<>()).add(validateReason);
                        continue;
                    }

                }
                if (unset_invalid_aa) {
                    if (!otherDb.isValidAlliance()) {
//                        if (force) otherDb.deleteInfo(setting);
                        if (force) unset.accept(setting, "No valid Alliance registered");
                        byGuild.computeIfAbsent(setting, k -> new ObjectLinkedOpenHashSet<>()).add("Invalid AA");
                        continue;
                    }
                }
                if (unset_all) {
//                    if (force) otherDb.deleteInfo(setting);
                    if (force) unset.accept(setting, "Setting removed by administrator.");
                    byGuild.computeIfAbsent(setting, k -> new ObjectLinkedOpenHashSet<>()).add("All");
                }
            }
        }
        StringBuilder msg = new StringBuilder();
        unsetReasons.entrySet().removeIf(e -> e.getValue().isEmpty());
        for (Map.Entry<Guild, Map<GuildSetting, Set<String>>> entry : unsetReasons.entrySet()) {
            Guild guild = entry.getKey();
            Map<GuildSetting, Set<String>> reasons = entry.getValue();
            msg.append(guild.toString()).append(":\n");
            for (Map.Entry<GuildSetting, Set<String>> reason : reasons.entrySet()) {
                GuildSetting setting = reason.getKey();
                Set<String> reasonSet = reason.getValue();
                msg.append("- ").append(setting.name()).append(": ").append(String.join(", ", reasonSet)).append("\n");
            }
        }

        if (!force) {
            String title = "Confirm unset keys";
            StringBuilder body = new StringBuilder("Unset " + settings.size() + " keys for " + guilds.size() + " servers\n");
            body.append("Unset on error: `" + unset_null + "`\n");
            body.append("Servers affected: `" + unsetReasons.size() + "`\n");
            io.create().confirmation(title, body.toString(), command)
                    .file("unset.txt", msg.toString())
                    .send();
            return null;
        }
        io.create().append("Done! " + settings.size() + " keys unset" +
                " | " + unsetReasons.size() + " servers affected\n" +
                "See attached file for details")
                .file("unset.txt", msg.toString())
                .send();
        return null;
    }

    @Command(desc = "Generate a google spreadsheet for a guild setting value for a set of discord servers")
    @RolePermission(value = Roles.ADMIN, root = true)
    @Ephemeral
    public String infoBulk(@Me GuildDB db, @Me IMessageIO io, GuildSetting setting, Set<GuildDB> guilds, @Switch("s") SpreadSheet sheet) throws GeneralSecurityException, IOException {
        if (sheet == null) {
            sheet = SpreadSheet.create(db, SheetKey.SETTINGS_SERVERS);
        }

        // admin bulk_setting key
        //- include alliances registered column (alliance ids)
        //- have column for coalitions guild is in
        //- have column for isValidAlliance
        //- have column for number of active members 7200 in the alliance

        List<String> header = new ArrayList<>(Arrays.asList(
            "guild",
            "guild_name",
            "owner",
            "owner_id",
            "setting",
            "raw",
            "readable",
            "is_invalid",
            "lack_perms",
            "root_col",
            "alliances",
            "aa_valid",
            "active_aa_members"
        ));

        sheet.setHeader(header);

        GuildDB root = Locutus.imp().getRootDb();


        for (GuildDB otherDb : guilds) {
            String raw = setting.getRaw(otherDb, false);
            if (raw == null) continue;
            Object value = setting.getOrNull(otherDb, false);
            String readable = value == null ? "![NULL]" : setting.toReadableString(db, value);
            boolean noPerms = !setting.allowed(db);

            header.set(0, otherDb.getIdLong() + "");
            header.set(1, otherDb.getGuild().getName());
            long ownerId = otherDb.getGuild().getOwnerIdLong();
            header.set(2, DiscordUtil.getUserName(ownerId));
            header.set(3, ownerId + "");
            header.set(4, setting.name());
            header.set(5, raw);
            header.set(6, readable);
            header.set(7, value == null ? "true" : "false");
            header.set(8, noPerms ? "true" : "false");
            Set<Coalition> hasOnRoot = Arrays.stream(Coalition.values()).filter(otherDb::hasCoalitionPermsOnRoot).collect(Collectors.toSet());
            header.set(9, hasOnRoot.stream().map(Coalition::name).collect(Collectors.joining(",")));
            Set<Integer> aaIds = otherDb.getAllianceIds();
            header.set(10, aaIds == null ? "" : aaIds.toString());
            header.set(11, otherDb.isValidAlliance() ? "true" : "false");
            AllianceList aaList = otherDb.getAllianceList();
            int activeMembers = aaList == null ? -1 : aaList.getNations(true, 7200, true).size();
            header.set(12, activeMembers + "");

            sheet.addRow(header);
        }

        sheet.updateClearCurrentTab();
        sheet.updateWrite();

        sheet.attach(io.create(), "setting_servers").send();
        return null;
    }

    @Command(desc = "Run the escalation alerts task")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String checkActiveConflicts() {
        WarUpdateProcessor.checkActiveConflicts();
        return "Done! (see console)";
    }

    @Command(desc = "Fetch and update the bans from the API")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String syncBans(@Default("false") boolean discordBans) throws SQLException {
        Locutus.imp().getNationDB().updateBans(Event::post);
        if (discordBans) {
            int size = syncDiscordBans();
            return "Done! Synced " + size + " bans";
        }
        return "Done! (see console)";
    }

    private int syncDiscordBans() {
        List<Guild> checkGuilds = new ArrayList<>();
        for (GuildDB db : Locutus.imp().getGuildDatabases().values()) {
            if (db.isAlliance()) {
                checkGuilds.add(db.getGuild());
            }
        }
        List<DiscordBan> toAdd = new ArrayList<>();
        for (Guild guild : checkGuilds) {
            Role botRole = guild.getBotRole();
            if (botRole == null) continue;
            if (!botRole.hasPermission(Permission.BAN_MEMBERS)) continue;
            try {
                List<Guild.Ban> bans = RateLimitUtil.complete(guild.retrieveBanList());
                for (Guild.Ban ban : bans) {
                    User user = ban.getUser();
                    String reason = ban.getReason();
                    if (reason == null) reason = "";

                    // long user, long server, long date, String reason
                    DiscordBan discordBan = new DiscordBan(user.getIdLong(), guild.getIdLong(), System.currentTimeMillis(), reason);
                    toAdd.add(discordBan);
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }

        Locutus.imp().getDiscordDB().addBans(toAdd);
        return toAdd.size();
    }

    @Ephemeral
    @Command(desc = "Dump the URL requests to console")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String showFileQueue(
            @Me GuildDB db,
            @Me IMessageIO io,
                                @Arg("Specify a timestamp to attach a tallied log of requests over a timeframe\n" +
                                        "Instead of just a summary of current items in the queue")
                                @Default @Timestamp Long timestamp,
                                @Arg("The number of top results to include\n" +
                                        "Default: 25")
                                @Switch("r") Integer numResults) throws URISyntaxException {
        if (db != Locutus.imp().getRootDb()) {
            throw new IllegalArgumentException("This command is only available in the root server");
        }
        PageRequestQueue handler = FileUtil.getPageRequestQueue();
        List<PageRequestQueue.PageRequestTask<?>> jQueue = handler.getQueue();

        Map<PagePriority, Integer> pagePriorities = new Object2IntOpenHashMap<>();
        int unknown = 0;
        int size = 0;
        synchronized (jQueue) {
            ArrayList<PageRequestQueue.PageRequestTask<?>> copy = new ArrayList<>(jQueue);
            size = copy.size();
            for (PageRequestQueue.PageRequestTask<?> task : copy) {
                long priority = task.getPriority();
                int ordinal = (int) (priority / Integer.MAX_VALUE);
                if (ordinal >= PagePriority.values.length) unknown++;
                else {
                    PagePriority pagePriority = PagePriority.values[ordinal];
                    pagePriorities.put(pagePriority, pagePriorities.getOrDefault(pagePriority, 0) + 1);
                }
            }
        }
        List<Map.Entry<PagePriority, Integer>> entries = new ArrayList<>(pagePriorities.entrySet());
        // sort
        entries.sort((o1, o2) -> o2.getValue() - o1.getValue());
        if (numResults == null) numResults = 25;

        StringBuilder sb = new StringBuilder();
        sb.append("**File Queue:** " + size + "\n");
        for (Map.Entry<PagePriority, Integer> entry : entries) {
            sb.append(entry.getKey().name()).append(": ").append(entry.getValue()).append("\n");
        }
        if (unknown > 0) {
            sb.append("Unknown: ").append(unknown).append("\n");
        }

        if (timestamp != null) {
            RequestTracker tracker = handler.getTracker();
            Map<String, Integer> byDomain = tracker.getCountByDomain(timestamp);
            Map<String, Integer> byUrl = tracker.getCountByUrl(timestamp);

            sb.append("\n**By Domain:**\n");
            int domainI = 1;
            for (Map.Entry<String, Integer> entry : byDomain.entrySet()) {
                sb.append("- " + entry.getKey()).append(": ").append(entry.getValue()).append("\n");
                if (domainI++ >= numResults) break;
            }

            sb.append("\n**By URL:**\n");
            int urlI = 1;
            for (Map.Entry<String, Integer> entry : byUrl.entrySet()) {
                sb.append("- " + entry.getKey()).append(": ").append(entry.getValue()).append("\n");
                if (urlI++ >= numResults) break;
            }

            RequestTracker v3Tracker = PoliticsAndWarV3.getRequestTracker();
            Map<String, Integer> v3Request = v3Tracker.getCountByDomain(timestamp);
            if (!v3Request.isEmpty()) {
                sb.append("\n**V3 By Domain:**\n");
                int v3I = 1;
                for (Map.Entry<String, Integer> entry : v3Request.entrySet()) {
                    sb.append("- " + entry.getKey()).append(": ").append(entry.getValue()).append("\n");
                    if (v3I++ >= numResults) break;
                }
            }

        }


        if (numResults > 25) {
            io.create().file("queue.txt", sb.toString()).send();
            return null;
        } else {
            return sb.toString();
        }
    }

    @Command(desc = "Generate and save the wiki pages for the bot")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String dumpWiki(@Default String pathRelative) throws IOException, InvocationTargetException, IllegalAccessException {
        if (pathRelative == null) pathRelative = "../locutus.wiki";
        CommandManager2 manager = Locutus.imp().getCommandManager().getV2();
        WikiGenHandler generator = new WikiGenHandler(pathRelative, manager);
        generator.writeDefaults();

        return "Done!";
    }


    @Command(desc = "Fetch and update the treasure data from the API")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String syncTreasures() {
        Locutus.imp().getNationDB().updateTreasures(Event::post);
        return "Done!";
    }

    @Command(desc = "Generate a sheet of recorded login times for a set of nations within a time range", viewable = true,
    keywords = {LOGIN, TIMES, USER, ACTIVITY, SESSION, HISTORY, TRACK, RECORD, TIMESTAMP, SPREADSHEET})
    @RolePermission(value = Roles.MEMBER, onlyInGuildAlliance = true)
    public String loginTimes(@Me @Default GuildDB db, @Me IMessageIO io, Set<DBNation> nations, @Timestamp long cutoff, @Switch("s") SpreadSheet sheet) throws IOException, GeneralSecurityException {
        if (sheet == null) {
            sheet = SpreadSheet.create(db, SheetKey.NATION_SHEET);
        }
        // time cutoff < 30d
        if (System.currentTimeMillis() - cutoff > TimeUnit.DAYS.toMillis(30)) {
            return "Cutoff must be within the last 30 days";
        }
        if (nations.size() > 30) {
            return "Too many nations (max: 30)";
        }
        List<String> header = new ArrayList<>(Arrays.asList(
                "nation",
                "time"
        ));
        sheet.setHeader(header);
        for (DBNation nation : nations) {
            List<DBSpyUpdate> activity = Locutus.imp().getNationDB().getSpyActivityByNation(nation.getNation_id(), cutoff);
            for (DBSpyUpdate update : activity) {
                header.set(0, String.valueOf(nation.getNation_id()));
                header.set(1, TimeUtil.YYYY_MM_DD_HH_MM_SS.format(new Date(update.timestamp)));
                sheet.addRow(header);
            }
        }

        sheet.updateClearCurrentTab();
        sheet.updateWrite();

        sheet.attach(io.create(), "login_times").send();
        return null;
    }

    @Command(desc = "Pull registered nations from locutus\n" +
            "(or a provided url)")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String syncDiscordWithLocutus(@Default String url) throws IOException {
        if (url == null) {
            url = WebRoot.REDIRECT + "/discordids";
        }
        int count = 0;
        // read string from url
        String csvTabSeparated = FileUtil.readStringFromURL(PagePriority.DISCORD_IDS_ENDPOINT, url);
        // split into lines
        String[] lines = csvTabSeparated.split("\n");
        // iterate each line
        List<PNWUser> toAdd = new ArrayList<>();

        for (String line : lines) {
            String[] columns = line.split("\t");
            int nationId = Integer.parseInt(columns[0]);
            long discordId = Long.parseLong(columns[1]);
            PNWUser existing = Locutus.imp().getDiscordDB().getUserFromDiscordId(discordId);
            if (existing != null && existing.getNationId() == nationId && existing.getDiscordId() == discordId) {
                continue;
            }

            String username = null;
            if (columns.length > 2) {
                username = columns[2];
                if (username.isEmpty()) {
                    username = null;
                }
            }
            if (username == null) username = discordId + "";

            // register the user
            count++;
            toAdd.add(new PNWUser(nationId, discordId, username));
        }
        Locutus.imp().getDiscordDB().addUsers(toAdd);
        return "Done! Imported " + count + "/" + lines.length + " users from " + url;
    }

    @Command(desc = "Fetch updated wars from the API")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String syncWars(@Switch("c") boolean updateCityCounts) throws IOException, ParseException {
        if (updateCityCounts) {
            Locutus.imp().getWarDb().loadWarCityCountsLegacy();
            return "Done (city counts)";
        }
        Locutus.imp().getWarDb().updateAllWars(Event::post);
        return "Done!";
    }

    @Command(desc = "Reload the bot's config.yaml file")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String reloadConfig() {
        Settings.INSTANCE.reload(Settings.INSTANCE.getDefaultFile());
        return "Done!";
    }

    @Command(desc = "Remove all guild settings that correspond to channels the bot cannot access")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String deleteAllInaccessibleChannels(@Switch("f") boolean force) {
        Map<GuildDB, List<GuildSetting>> toUnset = new LinkedHashMap<>();

        for (GuildDB db : Locutus.imp().getGuildDatabases().values()) {
            if (force) {
                List<GuildSetting> keys = db.listInaccessibleChannelKeys();
                if (!keys.isEmpty()) {
                    toUnset.put(db, keys);
                }
            } else {
                db.unsetInaccessibleChannels();
            }
        }

        if (toUnset.isEmpty()) {
            return "No keys to unset";
        }
        StringBuilder response = new StringBuilder();
        for (Map.Entry<GuildDB, List<GuildSetting>> entry : toUnset.entrySet()) {
            response.append(entry.getKey().getGuild().toString() + ":\n");
            List<String> keys = entry.getValue().stream().map(GuildSetting::name).collect(Collectors.toList());
            response.append("- " + StringMan.join(keys, "\n- "));
            response.append("\n");
        }
        String footer = "Rerun the command with `-f` to confirm";
        return response + footer;
    }


    @Command(desc = "Reset city names")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String resetCityNames(@Me DBNation me, @Me Auth auth, String name) throws IOException {
        for (int id : me.getCityMap(false).keySet()) {
            auth.setCityName(id, name);
        }
        return "Done!";
    }


    @Command(desc = "Terminate running tasks and attempt to stop the bot")
    @RolePermission(value = Roles.ADMIN, root = true)
    public void stop(boolean save) {
        Locutus.imp().stop();
    }

    @Command(desc = "Run the guild referral task for the nations in the alliance, and apply rewards\n" +
            "Only works if those are configured")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String syncReferrals(@Me GuildDB db) {
        if (!db.isValidAlliance()) return "Not in an alliance";
        Collection<DBNation> nations = db.getAllianceList().getNations(true, 10000, true);
        for (DBNation nation : nations) {
            db.getHandler().onRefer(nation);
        }
        return "Done!";
    }

    @Command(desc = "Set the archive status of the bot's announcement")
    @RolePermission(any = true, value = {Roles.INTERNAL_AFFAIRS, Roles.MILCOM, Roles.ADMIN, Roles.FOREIGN_AFFAIRS, Roles.ECON})
    public String archiveAnnouncement(@Me GuildDB db, int announcementId, @Default Boolean archive) {
        if (archive == null) archive = true;
        db.setAnnouncementActive(announcementId, !archive);
        return (archive ? "Archived" : "Unarchived") + " announcement with id: #" + announcementId;
    }

    @Command(desc = "Find the announcement for the closest matching invite", viewable = true)
    @RolePermission(Roles.ADMIN)
    @NoFormat
    public String find_invite(@Me GuildDB db, String invite) throws IOException {
        List<Announcement.PlayerAnnouncement> matches = db.getPlayerAnnouncementsContaining(invite);
        if (matches.isEmpty()) {
            return "No announcements found with content: `" + invite + "`";
        } else {
            return "Found " + matches.size() + " matches:\n- " +
                    matches.stream().map(f -> "{ID:" + f.ann_id + ", receiver:" + f.receiverNation + "}").collect(Collectors.joining("\n- "));
        }
    }

    @Command(desc = "Find the announcement closest matching a message", viewable = true)
    @RolePermission(Roles.ADMIN)
    @NoFormat
    public String find_announcement(@Me GuildDB db, int announcementId, String message) throws IOException {
        List<Announcement.PlayerAnnouncement> announcements = db.getPlayerAnnouncementsByAnnId(announcementId);
        if (announcements.isEmpty()) {
            return "No announcements found with id: #" + announcementId;
        }
        long diffMin = Long.MAX_VALUE;
        List<Announcement.PlayerAnnouncement> matches = new ArrayList<>();
        for (Announcement.PlayerAnnouncement announcement : announcements) {
            String content = announcement.getContent();
            if (message.equalsIgnoreCase(content)) {
                return "Announcement sent to nation id: " + announcement.receiverNation;
            }
            byte[] diff = StringMan.getDiffBytes(message, content);
            if (diff.length < diffMin) {
                diffMin = diff.length;
                matches.clear();
                matches.add(announcement);
            } else if (diff.length == diffMin) {
                matches.add(announcement);
            }
        }

        if (matches.isEmpty()) {
            return "No announcements found with id: #" + announcementId;
        } else if (matches.size() == 1) {
            Announcement.PlayerAnnouncement match = matches.get(0);
            return "Closest match: " + match.receiverNation + " with " + diffMin + " differences:\n```\n" + match.getContent() + "\n```";
        } else {
            StringBuilder response = new StringBuilder();
            response.append(matches.size() + " matches with " + diffMin + " differences:\n");
            for (Announcement.PlayerAnnouncement match : matches) {
                response.append("- " + match.receiverNation + "\n");
                // content in ```
                response.append("```\n" + match.getContent() + "\n```\n");
            }
            return response.toString();
        }
    }

    @Command(desc = "Send an announcement to multiple nations, with random variations for each receiver\n")
    @RolePermission(Roles.ADMIN)
    @HasApi
    @NoFormat
    public String announce(@Me GuildDB db, @Me Guild guild, @Me JSONObject command, @Me IMessageIO currentChannel,
                           @Me User author,
                           NationList sendTo,
                           @Arg("The subject used for sending an in-game mail if a discord direct message fails") String subject,
                           @Arg("The message you want to send") @TextArea String announcement,
                           @Arg("""
                                   Lines of replacement words or phrases, separated by `|` for each variation
                                   Add multiple lines for each replacement you want
                                   You can use \\n for newline for discord slash commands""") @TextArea String replacements,
                           @Arg("The channel to post the announcement to (must be same server)") @Switch("c") MessageChannel channel,
                           @Arg("The text to post in the channel below the hidden announcement (e.g. mentions)") @Switch("b") String bottomText,
                           @Arg("The required number of differences between each message") @Switch("v") @Default("0") Integer requiredVariation,
                           @Arg("The required depth of changes from the original message") @Switch("r") @Default("0") Integer requiredDepth,
                           @Arg("Variation seed. The same seed will produce the same variations, otherwise results are random") @Switch("s") Long seed,
                           @Arg("If messages are sent in-game") @Switch("m") boolean sendMail,
                           @Arg("If messages are sent via discord direct message") @Switch("d") boolean sendDM,
                           @Switch("f") boolean force) throws IOException {
        // ensure channel is in same server or null
        if (channel != null && ((GuildMessageChannel) channel).getGuild().getIdLong() != guild.getIdLong()) {
            throw new IllegalArgumentException("Channel must be in the same server: " + ((GuildMessageChannel) channel).getGuild() + " != " + guild);
        }
        if (bottomText != null && channel == null) {
            throw new IllegalArgumentException("Bottom text requires a channel");
        }

        ApiKeyPool keys = (sendMail || sendDM) ? db.getMailKey() : null;
        if ((sendMail || sendDM) && keys == null) throw new IllegalArgumentException("No API_KEY set, please use " + GuildKey.API_KEY.getCommandMention());
        Set<Integer> aaIds = db.getAllianceIds();
        if (sendMail || sendDM) {
            GPTUtil.checkThrowModeration(announcement + "\n" + replacements);
        }

        List<String> errors = new ArrayList<>();
        Collection<DBNation> nations = sendTo.getNations();
        for (DBNation nation : nations) {
            User user = nation.getUser();
            if (user == null) {
                errors.add("Cannot find user for `" + nation.getNation() + "`");
            } else if (guild.getMember(user) == null) {
                errors.add("Cannot find member in guild for `" + nation.getNation() + "` | `" + user.getName() + "`");
            } else {
                continue;
            }
            if (!aaIds.isEmpty() && !aaIds.contains(nation.getAlliance_id())) {
                throw new IllegalArgumentException("Cannot send to nation not in alliance: " + nation.getNation() + " | " + user);
            }
            if (!force) {
                if (nation.active_m() > 20000)
                    return "The " + nations.size() + " receivers includes inactive for >2 weeks. Use `" + sendTo.getFilter() + ",#active_m<20000` or set `force` to confirm";
                if (nation.getVm_turns() > 0)
                    return "The " + nations.size() + " receivers includes vacation mode nations. Use `" + sendTo.getFilter() + ",#vm_turns=0` or set `force` to confirm";
                if (nation.getPosition() < 1) {
                    return "The " + nations.size() + " receivers includes applicants. Use `" + sendTo.getFilter() + ",#position>1` or set `force` to confirm";
                }
            }
        }

        List<String> replacementLines = Announcement.getReplacements(replacements);
        Random random = seed == null ? new Random() : new Random(seed);
        Set<String> results = StringMan.enumerateReplacements(announcement, replacementLines, nations.size() + 1000, requiredVariation, requiredDepth);

        if (results.size() < nations.size()) return "Not enough entropy. Please provide more replacements";

        if (!force) {
            StringBuilder confirmBody = new StringBuilder();
            if (!sendDM && !sendMail) confirmBody.append("**Warning: No ingame or direct message option has been specified**\n");
            confirmBody.append("Send DM (`-d`): " + sendDM).append("\n");
            confirmBody.append("Send Ingame (`-m`): " + sendMail).append("\n");
            if (!errors.isEmpty()) {
                confirmBody.append("\n**Errors**:\n- " + StringMan.join(errors, "\n- ")).append("\n");
            }
//            DiscordUtil.createEmbedCommand(currentChannel, "Send to " + nations.size() + " nations", confirmBody + "\nPress to confirm", );
            DiscordUtil.pending(currentChannel, command, "Send to " + nations.size() + " nations", confirmBody + "\nPress to confirm");
            return null;
        }

        currentChannel.send("Please wait...");

        List<String> resultsArray = new ArrayList<>(results);
        Collections.shuffle(resultsArray, random);

        resultsArray = resultsArray.subList(0, nations.size());

        List<Integer> failedToDM = new ArrayList<>();
        List<Integer> failedToMail = new ArrayList<>();

        StringBuilder output = new StringBuilder();

        Map<DBNation, String> sentMessages = new HashMap<>();

        int i = 0;
        for (DBNation nation : nations) {
            String replaced = resultsArray.get(i++);
            String personal = replaced + "\n\n- " + author.getAsMention() + " " + guild.getName();

            boolean result = sendDM && nation.sendDM(personal);
            if (!result && sendDM) {
                failedToDM.add(nation.getNation_id());
            }
            if ((!result && sendDM) || sendMail) {
                try {
                    nation.sendMail(keys, subject, personal, false);
                } catch (IllegalArgumentException e) {
                    failedToMail.add(nation.getNation_id());
                }
            }

            sentMessages.put(nation, replaced);

            output.append("\n\n```" + replaced + "```" + "^ " + nation.getNation());
        }

        output.append("\n\n------\n");
        if (errors.size() > 0) {
            output.append("Errors:\n- " + StringMan.join(errors, "\n- "));
        }
        if (failedToDM.size() > 0) {
            output.append("\nFailed DM (sent ingame): " + StringMan.getString(failedToDM));
        }
        if (failedToMail.size() > 0) {
            output.append("\nFailed Mail: " + StringMan.getString(failedToMail));
        }

        int annId = db.addAnnouncement(AnnounceType.MESSAGE, author, subject, announcement, replacements, sendTo.getFilter(), false);
        output.append("\n\nAnnouncement ID: " + annId);
        for (Map.Entry<DBNation, String> entry : sentMessages.entrySet()) {
            byte[] diff = StringMan.getDiffBytes(announcement, entry.getValue());
            db.addPlayerAnnouncement(entry.getKey(), annId, diff);
        }

        if (channel != null) {
            IMessageBuilder msg = new DiscordChannelIO(channel).create();
            StringBuilder body = new StringBuilder();
            body.append("From: " + author.getAsMention() + "\n");
            body.append("To: `" + sendTo.getFilter() + "`\n");

            if (sendMail) {
                body.append("- A copy of this announcement has been sent ingame\n");
            }
            if (sendDM) {
                body.append("- A copy of this announcement has been sent as a direct message\n");
            }

            body.append("\n\nPress `view` to view the announcement");

            msg = msg.embed("[#" + annId + "] " + subject, body.toString());
            if (bottomText != null && !bottomText.isEmpty()) {
                msg = msg.append(bottomText);
            }

            CM.announcement.view cmd = CM.announcement.view.cmd.ann_id(annId + "");
            msg.commandButton(CommandBehavior.EPHEMERAL, cmd, "view").send();
        }

        return output.toString().trim();
    }



//    @Command
//    @RolePermission(value = Roles.ADMIN, root = true)
//    public String whitelistAuto(double requiredBank, double requiredDeposit, long timeFrame) {
//        GuildDB rootDb = Locutus.imp().getRootDb();
//        OffshoreInstance offshore = rootDb.getOffshore();
//
//        long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(10);
//
//        Set<Integer> currentWhitelist = rootDb.getCoalition(Coalition.WHITELISTED_AUTO);
//        Set<Integer> offshoring = rootDb.getCoalition(Coalition.OFFSHORING);
//
//        Map<Integer, Double> totalDepositByAA = new HashMap<>();
//        Map<Integer, Double> totalTransferByAA = new HashMap<>();
//
//        for (int allianceId : offshoring) {
//            GuildDB other = Locutus.imp().getGuildDBByAA(allianceId);
//            if (other == null || other.getOffshoreDB() != rootDb) continue;
//
//            double[] deposits = offshore.getDeposits(other, false);
//            double total = PW.convertedTotal(deposits);
//
//            List<Transaction2> tx = offshore.getTransactionsAA(allianceId, false);
//            tx.removeIf(f -> f.tx_datetime < cutoff);
//            tx.removeIf(f -> f.sender_id != allianceId);
//            double txWeek = tx.stream().mapToDouble(f -> f.convertedTotal()).sum();
//
//
//
//        }
//    }

    @Command(desc = """
            Add or remove a role from a set of members on discord based on a spreadsheet
            By default only roles will be added, specify `removeRoles` to remove roles from users not assigned the role in the sheet
            Specify `listMissing` to list nations that are not assigned a role in the sheet
            Columns:
            - `nation`, `leader`, `user`, `member` (at least one)
            - `role`, `role1`, `roleN` (multiple, or use comma separated values in one cell)""")
    @RolePermission(value = Roles.ADMIN)
    public String maskSheet(@Me IMessageIO io, @Me GuildDB db, @Me Guild guild, @Me JSONObject command,
                            SpreadSheet sheet,
                            @Arg("Remove these roles from users not assigned the role in the sheet")
                            @Switch("u") Set<Role> removeRoles,
                            @Arg("Remove all roles mentioned in the sheet")
                            @Switch("ra") boolean removeAll,
                            @Arg("List nations that are not assigned a role in the sheet")
                            @Switch("ln") Set<DBNation> listMissing,
                            @Switch("f") boolean force) {
        sheet.loadValues(null, true);
        List<Object> nations = sheet.findColumn("nation");
        List<Object> leaders = sheet.findColumn("leader");
        List<Object> users = sheet.findColumn(-1, f -> {
            String lower = f.toLowerCase(Locale.ROOT);
            return lower.startsWith("user") || lower.startsWith("member");
        });
        if (nations == null && leaders == null && users == null) {
            throw new IllegalArgumentException("Expecting column `nation` or `leader` or `user` or `member`");
        }
        Map<String, List<Object>> roles = sheet.findColumn(-1, f -> f.startsWith("role"), true);
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("Expecting at least one column starting with `role`");
        }
        if (removeAll) {
            Set<String> parsed = new ObjectLinkedOpenHashSet<>();
            for (Map.Entry<String, List<Object>> entry : roles.entrySet()) {
                List<Object> roleValues = entry.getValue();
                if (roleValues == null || roleValues.isEmpty()) {
                    continue;
                }
                for (Object roleCell : roleValues) {
                    if (roleCell == null) {
                        continue;
                    }
                    String roleNameList = roleCell.toString();
                    for (String roleName : roleNameList.split(",")) {
                        roleName = roleName.trim();
                        if (parsed.contains(roleName)) continue;
                        parsed.add(roleName);
                        try {
                            Role role = DiscordBindings.role(guild, roleName);
                            if (removeRoles == null) removeRoles = new ObjectLinkedOpenHashSet<>();
                            removeRoles.add(role);
                        } catch (IllegalArgumentException e) {
                            continue;
                        }
                    }
                }
            }
        }

        List<String> errors = new ArrayList<>();
        Map<Member, Set<Role>> existingRoles = new LinkedHashMap<>();
        Map<Member, Set<Role>> rolesAllowed = new LinkedHashMap<>();
        Map<Member, Set<Role>> rolesAdded = new LinkedHashMap<>();
        Map<Member, Set<Role>> rolesRemoved = new LinkedHashMap<>();

        Set<DBNation> nationsInSheet = new ObjectLinkedOpenHashSet<>();

        int max = Math.max(Math.max(nations == null ? 0 : nations.size(), leaders == null ? 0 : leaders.size()), users == null ? 0 : users.size());
        for (int i = 0; i < max; i++) {
            Object nationObj = nations == null || nations.size() < i ? null : nations.get(i);
            String nationStr = nationObj == null ? null : nationObj.toString();

            Object leaderObj = leaders == null || leaders.size() < i ? null : leaders.get(i);
            String leaderStr = leaderObj == null ? null : leaderObj.toString();

            Object userObj = users == null || users.size() < i ? null : users.get(i);
            String userStr = userObj == null ? null : userObj.toString();

            String input = nationStr == null ? leaderStr == null ? userStr : leaderStr : nationStr;

            User user = null;
            try {
                if (userStr != null) {
                    user = DiscordBindings.user(null, db.getGuild(), userStr);
                } else if (nationStr != null) {
                    DBNation nation = PWBindings.nation(null, db.getGuild(), nationStr);
                    if (nation != null) {
                        user = nation.getUser();
                        if (user == null) {
                            errors.add("[Row:" + (i + 2) + "] Nation has no user: " + nation.getMarkdownUrl());
                        }
                    } else {
                        errors.add("[Row:" + (i + 2) + "] Nation not found: `" + nationStr + "`");
                    }
                } else if (leaderStr != null) {
                    DBNation nation = Locutus.imp().getNationDB().getNationByLeader(leaderStr);
                    if (nation != null) {
                        user = nation.getUser();
                        if (user == null) {
                            errors.add("[Row:" + (i + 2) + "] Nation has no user: " + nation.getMarkdownUrl());
                        }
                    } else {
                        errors.add("[Row:" + (i + 2) + "] Nation Leader not found: `" + leaderStr + "`");
                    }
                }
            } catch (IllegalArgumentException e) {
                errors.add("[Row:" + (i + 2) + "] " + e.getMessage());
            }
            if (user == null) continue;
            if (listMissing != null) {
                DBNation nation = DBNation.getByUser(user);
                if (nation != null) {
                    nationsInSheet.add(nation);
                }
            }
            Member member = guild.getMember(user);
            if (member == null) {
                errors.add("[Row:" + (i + 2) + "] User `" + user.getName() + " not found ` in " + guild.toString());
                continue;
            }

            for (Map.Entry<String, List<Object>> entry : roles.entrySet()) {
                String columnName = entry.getKey();
                List<Object> roleValues = entry.getValue();
                if (roleValues == null || roleValues.isEmpty() || roleValues.size() < i) {
                    continue;
                }
                Object roleCell = roleValues.get(i);
                if (roleCell == null) {
                    continue;
                }
                String roleNameList = roleCell.toString();
                Set<Role> memberRoles = null;
                for (String roleName : roleNameList.split(",")) {
                    roleName = roleName.trim();
                    try {
                        Role role = DiscordBindings.role(guild, roleName);
                        rolesAllowed.computeIfAbsent(member, f -> new ObjectLinkedOpenHashSet<>()).add(role);
                        if (memberRoles == null) {
                            memberRoles = new ObjectLinkedOpenHashSet<>(member.getUnsortedRoles());
                        }
                        Set<Role> finalMemberRoles = memberRoles;
                        if (existingRoles.computeIfAbsent(member, f -> finalMemberRoles).contains(role)) {
                            continue;
                        }
                        rolesAdded.computeIfAbsent(member, f -> new ObjectLinkedOpenHashSet<>()).add(role);
                    } catch (IllegalArgumentException e) {
                        errors.add("[Row:" + (i + 2) + ",Column:" + columnName + "] `" + input + "` -> `" + roleName + "`: " + e.getMessage());
                        continue;
                    }
                }
            }
        }

        if (removeRoles != null && !removeRoles.isEmpty()) {
            for (Member member : guild.getMembers()) {
                if (member.getUser().isBot()) continue;
                Set<Role> granted = rolesAllowed.getOrDefault(member, Collections.emptySet());
                Set<Role> memberRoles = null;
                for (Role role : removeRoles) {
                    if (!granted.contains(role)) {
                        if (memberRoles == null) {
                            memberRoles = new ObjectLinkedOpenHashSet<>(member.getUnsortedRoles());
                        }
                        Set<Role> finalMemberRoles = memberRoles;
                        if (existingRoles.computeIfAbsent(member, f -> finalMemberRoles).contains(role)) {
                            rolesRemoved.computeIfAbsent(member, f -> new ObjectLinkedOpenHashSet<>()).add(role);
                        }
                    }
                }
            }
        }

        StringBuilder body = new StringBuilder();
        body.append("**Sheet**: <" + sheet.getURL() + ">\n");
        IMessageBuilder msg = io.create();

        if (listMissing != null) {
            StringBuilder listMissingMessage = new StringBuilder();
            Set<DBNation> missingNations = listMissing.stream().filter(f -> !nationsInSheet.contains(f)).collect(Collectors.toSet());
            if (!missingNations.isEmpty()) {
                listMissingMessage.append("nation,leader,url,username,user_id\n");
                for (DBNation nation : missingNations) {
                    String name = nation.getName();
                    String leader = nation.getLeader();
                    String url = nation.getUrl();
                    String user = nation.getUserDiscriminator();
                    Long userId = nation.getUserId();
                    listMissingMessage.append(name).append(",")
                            .append(leader).append(",")
                            .append(url).append(",")
                            .append(user == null ? "" : user).append(",")
                            .append(userId == null ? "" : userId).append("\n");
                }
                body.append("**listMissing**: `").append(missingNations.size() + "`\n");
                msg = msg.file("missing_nations.csv", listMissingMessage.toString());
            } else {
                body.append("**listMissing**: No missing nations\n");
            }
        }

        if (removeRoles != null) {
            body.append("**removeRoles**: `").append(removeRoles.stream().map(Role::getName).collect(Collectors.joining(","))).append("`\n");
        }

        if (rolesRemoved.isEmpty() && rolesAdded.isEmpty()) {
            msg.append("\n**Result**: No roles to add or remove").send();
            return null;
        }

        AutoRoleInfo info = new AutoRoleInfo(db, body.toString());
        for (Map.Entry<Member, Set<Role>> entry : rolesAdded.entrySet()) {
            Member member = entry.getKey();
            for (Role role : entry.getValue()) {
                info.addRoleToMember(member, role);
            }
        }
        for (Map.Entry<Member, Set<Role>> entry : rolesRemoved.entrySet()) {
            Member member = entry.getKey();
            for (Role role : entry.getValue()) {
                info.removeRoleFromMember(member, role);
            }
        }
        if (!force) {
            String changeStr = info.toString();
            String separator = "\n\n------------\n\n";
            if (body.length() + changeStr.length() + separator.length() > MessageEmbed.DESCRIPTION_MAX_LENGTH) {
                msg = msg.file("role_changes.txt", changeStr);
            } else {
                body.append(separator + changeStr);
            }
            msg.confirmation("Confirm bulk role change", body.toString(), command).send();
            return null;
        }
        io.send("Please wait...");
        info.execute();
        return info.getChangesAndErrorMessage();
    }

    @Command(desc = "Add or remove a role from a set of members on discord")
    @RolePermission(Roles.ADMIN)
    public String mask(@Me Member me, @Me GuildDB db, Set<Member> members, Role role, boolean value, @Arg("If the role should be added or removed from all other members\n" +
            "If `value` is true, the role will be removed, else added") @Switch("r") boolean toggleMaskFromOthers) {
        List<String> response = new ArrayList<>();
        for (Member member : members) {
            User user = member.getUser();
            Set<Role> roles = member.getUnsortedRoles();
            if (value && roles.contains(role)) {
                response.add(user.getName() + " already has the role: `" + role + "`");
                continue;
            } else if (!value && !roles.contains(role)) {
                response.add(user.getName() + ": does not have the role: `" + role + "`");
                continue;
            }
            if (value) {
                RateLimitUtil.queue(db.getGuild().addRoleToMember(member, role));
                response.add(user.getName() + ": Added role to member");
            } else {
                RateLimitUtil.queue(db.getGuild().removeRoleFromMember(member, role));
                response.add(user.getName() + ": Removed role from member");
            }
        }
        if (toggleMaskFromOthers) {
            for (Member member : db.getGuild().getMembers()) {
                if (members.contains(member)) continue;
                Set<Role> memberRoles = member.getUnsortedRoles();
                if (value) {
                    if (memberRoles.contains(role)) {
                        RateLimitUtil.queue(db.getGuild().removeRoleFromMember(member, role));
                        response.add(member.getUser().getName() + ": Removed role from member");
                    }
                } else {
                    if (!memberRoles.contains(role)) {
                        RateLimitUtil.queue(db.getGuild().addRoleToMember(member, role));
                        response.add(member.getUser().getName() + ": Added role to member");
                    }
                }
            }
        }
        return StringMan.join(response, "\n").trim();
    }

    @Command(desc = "Send a direct message on discord to the nations specified\n" +
            "If they are not registered, then no message will be sent")
    @RolePermission(value = Roles.MAIL, root = true)
    public String dm(@Me User author, @Me Guild guild, @Me IMessageIO io, @Me JSONObject command, Set<DBNation> nations, String message, @Switch("f") boolean force) {
        if (nations.size() > 500) {
            throw new IllegalArgumentException("Too many nations: " + nations.size() + " (max 500)");
        }
        if (!force) {
            String title = "Send " + nations.size() + " messages";
            Set<Integer> alliances = new IntLinkedOpenHashSet();
            for (DBNation nation : nations) alliances.add(nation.getAlliance_id());
            String embedTitle = title + " to nations.";
            if (alliances.size() != 1) embedTitle += " in " + alliances.size() + " alliances.";
            String dmMsg = "content: ```" + message + "```";
            io.create().embed(embedTitle, dmMsg).confirmation(command).send();
            return null;
        }
        boolean hasAdmin = Roles.ADMIN.hasOnRoot(author);
        List<String> errors = new ArrayList<>();
        List<User> users = new ArrayList<>();
        for (DBNation nation : nations) {
            User user = nation.getUser();
            if (user == null) {
                errors.add("No user found for " + nation.getNation());
            } else {
                if (!hasAdmin) {
                    Member member = guild.getMember(user);
                    if (member == null) {
                        errors.add("No member found for " + nation.getNation() + " in guild " + guild);
                        continue;
                    }
                }

                users.add(user);
            }
        }
        if (users.isEmpty()) {
            return "No users found. Are they registered? " + CM.register.cmd.toSlashMention();
        }
        GPTUtil.checkThrowModeration(message);
        CompletableFuture<IMessageBuilder> msgFuture = io.sendMessage("Sending " + users.size() + " with " + errors.size() + " errors\n" + StringMan.join(errors, "\n"));
        for (User mention : users) {
            mention.openPrivateChannel().queue(f -> RateLimitUtil.queue(f.sendMessage(author.getAsMention() + " said: " + message + "\n\n(no reply)")));
        }
        io.sendMessage("Done! Sent " + users.size() + " messages");
        return null;
    }

    @Command(desc = """
            Edit an attribute of your in-game alliance
            Attributes match the in-game fields and are case sensitive
            Run the command without arguments to get a list of attributes"""
    )
    @RolePermission(Roles.ADMIN)
    public String editAlliance(@Me GuildDB db, DBAlliance alliance, @Default String attribute, @Default @TextArea String value) throws Exception {
        if (!db.isAllianceId(alliance.getAlliance_id())) {
            return "Alliance: " + alliance.getAlliance_id() + " not registered to guild " + db.getGuild() + ". See: " + CM.settings.info.cmd.toSlashMention() + " with key: " + GuildKey.ALLIANCE_ID.name();
        }

        Rank rank = attribute != null && attribute.toLowerCase().contains("bank") ? Rank.HEIR : Rank.OFFICER;
        Auth auth = alliance.getAuth(AlliancePermission.EDIT_ALLIANCE_INFO);
        if (auth == null) return "No authorization set";

        StringBuilder response = new StringBuilder();

        EditAllianceTask task = new EditAllianceTask(auth.getNation(), new Consumer<Map<String, String>>() {
            @Override
            public void accept(Map<String, String> post) {
                if (attribute == null || value == null) {
                    throw new IllegalArgumentException("Currently set: " + StringMan.getString(post));
                }
                if (post.containsKey(attribute.toLowerCase()) || attribute.equals("acceptmem")) {
                    post.put(attribute.toLowerCase(), value);
                    response.append("Attribute has been set.");
                } else {
                    response.append("Invalid key: " + attribute + ". Options: " + StringMan.getString(post));
                }
            }
        });
        task.call();
        return response.toString();
    }

    @Command(desc = "Remove a discord role the bot uses for command permissions")
    @RolePermission(Roles.ADMIN)
    public String unregisterRole(@Me GuildDB db, Roles locutusRole, @Arg("Only remove a role mapping for this alliance") @Default DBAlliance alliance) {
        return aliasRole(db, locutusRole, null, alliance, true);
    }

    private static String mappingToString(Map<Long, Role> mapping) {
        List<String> response = new ArrayList<>();
        for (Map.Entry<Long, Role> entry : mapping.entrySet()) {
            Role role = entry.getValue();
            long aaId = entry.getKey();
            if (aaId == 0) {
                response.add("*:" + role.getName());
            } else {
                response.add(aaId + ": " + role.getName());
            }
        }
        if (response.isEmpty()) return "";
        return "- " + StringMan.join(response, "\n- ");
    }

    @Command(desc = "Set the discord roles the bot uses for command permissions")
    @RolePermission(Roles.ADMIN)
    public static String aliasRole(@Me GuildDB db, @Default Roles locutusRole, @Default() Role discordRole, @Arg("If the role mapping is only for a specific alliance (WIP)") @Default() DBAlliance alliance, @Arg("Remove the existing mapping instead of setting it") @Switch("r") boolean removeRole) {
        if (alliance != null && !removeRole && discordRole != null) {
            if (!db.isAllianceId(alliance.getAlliance_id())) {
                return "Alliance: " + alliance.getAlliance_id() + " not registered to guild " + db.getGuild() + ". See: " + CM.settings.info.cmd.toSlashMention() + " with key: " + GuildKey.ALLIANCE_ID.name();
            }
            if (locutusRole != null && !locutusRole.allowAlliance()) {
                throw new IllegalArgumentException("The role `" + locutusRole.name() + "` does not allow alliance specific mappings, please leave `alliance` blank");
            }
            if (db.getAllianceIds().size() < 2) {
                throw new IllegalArgumentException("Alliance specific roles are only supported when multiple alliances are registered to a guild. Please register more alliances or leave `alliance` blank");
            }
        }
        StringBuilder response = new StringBuilder();
        boolean showGlobalMappingInfo = false;

        if (locutusRole == null) {
            if (discordRole != null) {
                List<String> rolesListStr = new ArrayList<>();
                Map<Roles, Map<Long, Long>> allMapping = db.getMappingRaw();
                if (removeRole) {
                    // remove all roles registered to it
                    for (Map.Entry<Roles, Map<Long, Long>> locEntry : allMapping.entrySet()) {
                        for (Map.Entry<Long, Long> discEntry : locEntry.getValue().entrySet()) {
                            long aaId =discEntry.getKey();
                            if (alliance != null && aaId != alliance.getAlliance_id()) continue;
                            if (discEntry.getValue() == discordRole.getIdLong()) {
                                String aaStr =  aaId == 0 ? "*" : PW.getName(aaId, true);
                                rolesListStr.add("Removed " + locEntry.getKey().name() + " from " + discordRole.getName() + " (AA:" + aaId + ")");
                                db.deleteRole(locEntry.getKey(), aaId);
                            }
                        }
                    }
                    if (rolesListStr.isEmpty()) {
                        return "No aliases found for " + discordRole.getName();
                    }
                    response.append("Removed aliases for " + discordRole.getName() + ":\n- ");
                    response.append(StringMan.join(rolesListStr, "\n- "));
                    response.append("\n\nUse " + CM.role.setAlias.cmd.toSlashMention() + " to view current role aliases");
                    return response.toString();
                }

                for (Map.Entry<Roles, Map<Long, Long>> locEntry : allMapping.entrySet()) {
                    Map<Long, Long> aaToRoleMap = locEntry.getValue();
                    showGlobalMappingInfo |= aaToRoleMap.size() > 1 && aaToRoleMap.containsKey(0L);
                    for (Map.Entry<Long, Long> discEntry : aaToRoleMap.entrySet()) {
                        if (discEntry.getValue() == discordRole.getIdLong()) {
                            Roles role = locEntry.getKey();
                            long aaId = discEntry.getKey();
                            if (aaId == 0) {
                                rolesListStr.add("*:" + role.name());
                            } else {
                                rolesListStr.add(DBAlliance.getOrCreate((int) aaId).getName() + "/" + aaId + ":" + role.name());
                            }
                        }
                    }
                }
                if (rolesListStr.isEmpty()) {
                    return "No aliases found for " + discordRole.getName();
                }
                response.append("Aliases for " + discordRole.getName() + ":\n- ");
                response.append(StringMan.join(rolesListStr, "\n- "));
                if (showGlobalMappingInfo) response.append("\n`note: " + Messages.GLOBAL_ROLE_MAPPING_INFO + "`");
                return response.toString();
            }

            List<String> registeredRoles = new ArrayList<>();
            List<String> unregisteredRoles = new ArrayList<>();
            for (Roles role : Roles.values) {
                Map<Long, Role> mapping = db.getAccountMapping(role);
                if (mapping != null && !mapping.isEmpty()) {
                    registeredRoles.add(role + ":\n" + mappingToString(mapping));
                    continue;
                }
                if (role.getKey() != null && db.getOrNull(role.getKey()) == null) continue;
                unregisteredRoles.add(role + ":\n" + mappingToString(mapping));
            }

            if (!registeredRoles.isEmpty()) {
                response.append("**Registered Roles**:\n" + StringMan.join(registeredRoles, "\n") + "\n");
            }
            if (!unregisteredRoles.isEmpty()) {
                response.append("**Unregistered Roles**:\n" + StringMan.join(unregisteredRoles, "\n") + "\n");
            }
            response.append("""
                    Provide a value for `locutusRole` for specific role information.
                    Provide a value for `discordRole` to register a role.
                    """);

            return response.toString();
        }

        if (discordRole == null) {
            if (removeRole) {
                Role alias = db.getRole(locutusRole, alliance != null ? (long) alliance.getAlliance_id() : null);
                if (alias == null) {
                    String allianceStr = alliance != null ? alliance.getName() + "/" + alliance.getAlliance_id() : "*";
                    return "No role alias found for " + allianceStr + ":" + locutusRole.name();
                }
                if (alliance != null) {
                    db.deleteRole(locutusRole, alliance.getAlliance_id());
                } else {
                    db.deleteRole(locutusRole);
                }
                response.append("Removed role alias for " + locutusRole.name() + ":\n");
            }
            Map<Long, Role> mapping = db.getAccountMapping(locutusRole);
            response.append("**" + locutusRole.name() + "**:\n");
            response.append("`" + locutusRole.getDesc() + "`\n");
            if (mapping.isEmpty()) {
                response.append("> No value set.\n");
            } else {
                response.append("```\n" + mappingToString(mapping) + "```\n");
            }
            response.append("Provide a value for `discordRole` to register a role.\n");
            if (mapping.size() > 1 && mapping.containsKey(0L)) {
                response.append("`note: " + Messages.GLOBAL_ROLE_MAPPING_INFO + "`");
            }
            return response.toString().trim();
        }

        if (removeRole) {
            throw new IllegalArgumentException("Cannot remove role alias with this command. Use " + CM.role.unregister.cmd.locutusRole(locutusRole.name()).toSlashCommand());
        }


        int aaId = alliance == null ? 0 : alliance.getAlliance_id();
        String allianceStr = alliance == null ? "*" : alliance.getName() + "/" + aaId;
        db.addRole(locutusRole, discordRole, aaId);
        return "Added role alias: " + locutusRole.name().toLowerCase() + " to " + discordRole.getName() + " for alliance " + allianceStr + "\n" +
                "To unregister, use " + CM.role.unregister.cmd.locutusRole(locutusRole.name()).toSlashCommand();
    }

    public String printApiStats(ApiKeyPool keys) {
        Map<String, String> map = new LinkedHashMap<>();
        for (ApiKeyPool.ApiKey key : keys.getKeys()) {
            PoliticsAndWarV3 v3 = new PoliticsAndWarV3(ApiKeyPool.create(key));
            try {
                ApiKeyDetails stats = v3.getApiKeyStats();
                map.put(key.getKey(), StringMan.formatJsonLikeText(stats.toString()));
            } catch (Throwable e) {
                map.put(key.getKey(), e.getMessage());
            }
        }
        StringBuilder response = new StringBuilder();

        // Convert map to simple message (newline for each / header)
        for (Map.Entry<String, String> entry : map.entrySet()) {
            response.append("**Key `" + entry.getKey() + "`:\n```json\n" + entry.getValue() + "\n```\n\n");
        }
        return response.toString();
    }

    @Command(desc = "Print the usage stats the api for your alliance to console")
    @Ephemeral
    @RolePermission(value = Roles.ADMIN, root = true)
    public String apiUsageStats(@Me DBAlliance alliance) {
        ApiKeyPool keys = alliance.getApiKeys();
        Logg.info(printApiStats(keys));
        return "Done! (see console)";
    }

    @Command(desc = "Import api keys from the guild API_KEY setting, so they can be validated")
    @Ephemeral
    @RolePermission(value = Roles.ADMIN, root = true)
    public String importGuildKeys() {
        StringBuilder response = new StringBuilder();
        for (GuildDB db : Locutus.imp().getGuildDatabases().values()) {
            List<String> keys = db.getOrNull(GuildKey.API_KEY);
            if (keys == null) return "No keys found for guild " + db.getGuild().getName() + " (" + db.getGuild().getId() + ")";
            for (String key : keys) {
                try {
                    ApiKeyDetails stats = new PoliticsAndWarV3(ApiKeyPool.builder().addKeyUnsafe(key).build()).getApiKeyStats();
                    Locutus.imp().getDiscordDB().addApiKey(stats.getNation().getId(), key);

                    response.append(key + ": success" + "\n");
                } catch (Throwable e) {
                    response.append(key + ": " + e.getMessage() + "\n");
                }
            }
        }
        Logg.text(response.toString());
        return "Done! (see console)";
    }

//    @Command(desc = "Check if current api keys are valid")
//    @RolePermission(value = Roles.ADMIN, root = true)
//    public String validateAPIKeys() {
//        // Validate v3 keys used in the guild db?
//        return "TODO";
//        Set<String> keys = Locutus.imp().getPnwApiV2().getApiKeyUsageStats().keySet();
//        Map<String, String> failed = new LinkedHashMap<>();
//        Map<String, ApiKeyDetails> success = new LinkedHashMap<>();
//        for (String key : keys) {
//            try {
//                ApiKeyDetails stats = new PoliticsAndWarV3(ApiKeyPool.builder().addKeyUnsafe(key).build()).getApiKeyStats();
//                if (stats != null && stats.getNation() != null && stats.getNation().getId() != null) {
//                    success.put(key, stats);
//                } else {
//                    failed.put(key, "Error: null (1)");
//                }
//            } catch (Throwable e) {
//                failed.put(key, e.getMessage());
//            }
//        }
//        StringBuilder response = new StringBuilder();
//        for (Map.Entry<String, String> e : failed.entrySet()) {
//            response.append(e.getKey() + ": " + e.getValue() + "\n");
//        }
//        for (Map.Entry<String, ApiKeyDetails> e : success.entrySet()) {
//            String key = e.getKey();
//            ApiKeyDetails record = e.getValue();
//            int natId = record.getNation().getId();
//            DBNation nation = DBNation.getById(natId);
//            if (nation != null) {
//                response.append(key + ": " + record.toString() + " | " + nation.getNation() + " | " + nation.getAllianceName() + " | " + nation.getPosition() + "\n");
//            } else {
//                response.append(e.getKey() + ": " + e.getValue() + "\n");
//            }
//        }
//        System.out.println(response); // keep
//        return "Done (see console)";
//    }

    @Command(desc = "Test your alliance recruitment message by sending it to the bot creator's nation")
    @RolePermission(value = Roles.ADMIN)
    public String testRecruitMessage(@Me GuildDB db) throws IOException {
        MailApiResponse response = db.sendRecruitMessage(Locutus.imp().getNationDB().getNationById(Locutus.loader().getNationId()));
        if (response.status() != MailApiSuccess.SUCCESS) {
            return response.status() + ": " + response.error();
        } else {
            return response.status().name() + ": See in-game to confirm message formatting";
        }
    }

    @Command(desc = "Purge a category's channels older than the time specified")
    @RolePermission(value = Roles.ADMIN)
    public String debugPurgeChannels(Category category, @Range(min=60) @Timestamp long cutoff) {
        int deleted = 0;
        for (GuildMessageChannel GuildMessageChannel : category.getTextChannels()) {
            if (GuildMessageChannel.getLatestMessageIdLong() > 0) {
                long message = GuildMessageChannel.getLatestMessageIdLong();
                try {
                    long created = net.dv8tion.jda.api.utils.TimeUtil.getTimeCreated(message).toEpochSecond() * 1000L;
                    if (created > cutoff) {
                        continue;
                    }
                } catch (Throwable ignore) {}
            }
            RateLimitUtil.queue(GuildMessageChannel.delete());
            deleted++;
            continue;
        }
        return "Deleted " + deleted + " channels";
    }

    @Command(desc = "List guilds which have not sent a recent message\n" +
            "Note: Deprecated. Not reliable if message content intent is disabled", viewable = true)
    @Ephemeral
    @RolePermission(value = Roles.ADMIN, root = true)
    public String listExpiredGuilds(boolean checkMessages) {
        StringBuilder response = new StringBuilder();
        for (GuildDB db : Locutus.imp().getGuildDatabases().values()) {
            Guild guild = db.getGuild();
            Member owner = db.getGuild().getOwner();
            DBNation nation = DiscordUtil.getNation(owner.getUser());

            Set<Integer> aaIds = db.getAllianceIds();

            if (nation != null && nation.active_m() > 30000) {
                response.append(guild + "/" + StringMan.getString(aaIds) + ": owner (nation:" + nation.getNation_id() + ") is inactive " + TimeUtil.secToTime(TimeUnit.MINUTES, nation.active_m()) + "\n");
                continue;
            }
            // In an alliance with inactive leadership (1 month)
            if (!aaIds.isEmpty() && !db.isValidAlliance()) {
                response.append(guild + "/" + StringMan.getString(aaIds) + ": alliance is invalid (nation:" + (nation == null ? "" : nation.getNation_id() + ")\n"));
                continue;
            }

            if (aaIds.isEmpty() && nation == null && checkMessages) {
                long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30);
                boolean error = false;
                long last = 0;

                outer:
                for (GuildMessageChannel channel : guild.getTextChannels()) {
                    if (channel.getLatestMessageIdLong() == 0) continue;
                    try {
                        long latestSnowflake = channel.getLatestMessageIdLong();
                        long latestMs = net.dv8tion.jda.api.utils.TimeUtil.getTimeCreated(latestSnowflake).toEpochSecond() * 1000L;
                        if (latestMs > cutoff) {
                            List<Message> messages = RateLimitUtil.complete(channel.getHistory().retrievePast(5));
                            for (Message message : messages) {
                                if (message.getAuthor().isSystem() || message.getAuthor().isBot() || guild.getMember(message.getAuthor()) == null) {
                                    continue;
                                }
                                last = Math.max(last, message.getTimeCreated().toEpochSecond() * 1000L);
                                if (last > cutoff) {
                                    break outer;
                                }
                            }
                        }
                    } catch (Throwable e) {
                        error = true;
                    }
                }
                if (last < cutoff) {
                    response.append(guild + ": has no recent messages\n");
                    continue;
                }
            }
        }
        return response.toString();
    }

    @Command(desc = "Remove deleted alliances or guilds from a coalition\n" +
            "Note: Do not remove deleted offshores or banks if you want to use their previous transactions in deposit calculations")
    @RolePermission(value = Roles.ADMIN)
    public String removeInvalidOffshoring(@Me GuildDB db, Coalition coalition) {
        Set<Long> toRemove = new LongOpenHashSet();
        for (long id : db.getCoalitionRaw(coalition)) {
            GuildDB otherDb;
            if (id > Integer.MAX_VALUE) {
                otherDb = Locutus.imp().getGuildDB(id);
            } else {
                otherDb = Locutus.imp().getGuildDBByAA((int) id);
            }
            if (otherDb == null) {
                toRemove.add(id);
            }
        }
        for (long id : toRemove) {
            db.removeCoalition(id,coalition);
        }
        return "Removed `" + StringMan.join(toRemove, ",") + "` from " + Coalition.OFFSHORING;

    }

    @Command(desc = "Make the bot leave the server with the specified ID")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String leaveServer(long guildId) {
        GuildDB db = Locutus.imp().getGuildDB(guildId);
        if (db == null) return "Server not found " + guildId;
        Guild guild = db.getGuild();
        RateLimitUtil.queue(guild.leave());
        return "Leaving " + guild.getName();
    }

    @Command(desc = "List accounts with the offshore which are inactive, as well as details about last owner/alliance activity and last transfer info", viewable = true)
    @Ephemeral
    @RolePermission(value = Roles.ADMIN, root = true)
    public String listExpiredOffshores() {
        OffshoreInstance offshore = Locutus.imp().getRootBank();
        GuildDB db = offshore.getGuildDB();
        Set<Long> coalitions = db.getCoalitionRaw(Coalition.OFFSHORING);

        Map<Long, List<String>> notices = new Long2ObjectOpenHashMap<>();
        Set<Long> printDeposits = new LongOpenHashSet();
        Set<Long> hasError = new LongOpenHashSet();

        for (Long id : coalitions) {
            List<String> notice = notices.computeIfAbsent(id, f -> new ArrayList<>());
            GuildDB otherDb = (id > Integer.MAX_VALUE) ? Locutus.imp().getGuildDB(id) : Locutus.imp().getGuildDBByAA(id.intValue());
            if (otherDb == null) {
                notice.add("- No database found");
                hasError.add(id);
                continue;
            }

            if (id > Integer.MAX_VALUE) {
                notice.add(" **CORPORATION**");
            } else {
                DBAlliance alliance = DBAlliance.get(id.intValue());
                if (alliance == null || !alliance.exists()) {
                    notice.add("\n- AA does not exist: " + id);
                    printDeposits.add(id);
                } else {
                   notice.add(" **ALLIANCE**");
                }
            }

            notice.add("\n- Guild: `" + otherDb.getGuild().toString() + "`");
            Set<Integer> aaIds = otherDb.getAllianceIds();
            if (!aaIds.isEmpty()) {
                List<String> aaNames = new ArrayList<>();
                for (int aaId : aaIds) {
                    DBAlliance aa = DBAlliance.get(aaId);
                    if (aa == null) {
                        aaNames.add(aaId + "");
                    } else {
                        aaNames.add(aa.getName() + "/" + aaId);
                    }
                }
                notice.add("\n- Alliance: `" + StringMan.getString(aaNames) + "`");
            }

            List<Transaction2> transactions;
            if (!aaIds.isEmpty()) {
                transactions = offshore.getTransactionsAA(aaIds, false, 0, Long.MAX_VALUE);
            } else {
                transactions = offshore.getTransactionsGuild(id, false, 0L, Long.MAX_VALUE);
            }
            transactions.removeIf(f -> f.tx_datetime > System.currentTimeMillis());
            transactions.removeIf(f -> f.receiver_id == f.banker_nation && f.tx_id > 0);
            long latestTx = transactions.isEmpty() ? 0 : transactions.stream().mapToLong(f -> f.tx_datetime).max().getAsLong();
            if (latestTx < System.currentTimeMillis() - TimeUnit.DAYS.toMillis(14)) {
                String timeStr = TimeUtil.secToTime(TimeUnit.MILLISECONDS, System.currentTimeMillis() - latestTx);
                notice.add("\n- Latest Transfer: `" + timeStr + "`");
                hasError.add(id);
                printDeposits.add(id);
            }

            Member owner = otherDb.getGuild().getOwner();

            if (!aaIds.isEmpty()) {
                AllianceList alliance = new AllianceList(aaIds);
                Set<DBNation> nations = new HashSet<>(alliance.getNations());
                nations.removeIf(f -> f.getPosition() < Rank.LEADER.id);
                long minActiveM = Long.MAX_VALUE;
                DBNation latestNation = null;
                for (DBNation nation : nations) {
                    if (nation.active_m() < minActiveM) {
                        minActiveM = nation.active_m();
                        latestNation = nation;
                    }
                }
                if (minActiveM > 10000) {
                    notice.add("\n- Inactive Leadership: `" + (latestNation != null ? "<" + latestNation.getUrl() + ">" : null) + " | " + TimeUtil.secToTime(TimeUnit.MINUTES, minActiveM) + "`");
                    printDeposits.add(id);
                }
            }

            DBNation nation = owner != null ? DiscordUtil.getNation(owner.getIdLong()) : null;
            if (nation == null) {
                notice.add("\n- Owner is Unregistered");
                printDeposits.add(id);
            } else if (nation.active_m() > 10000) {
                notice.add("\n- Owner is inactive: <@" + owner.getIdLong() + "> | <" + nation.getUrl() + "> | `" + TimeUtil.secToTime(TimeUnit.MINUTES, nation.active_m()) + "`");
                printDeposits.add(id);
            }
        }

        StringBuilder response = new StringBuilder();
        for (long id : coalitions) {
            if (!hasError.contains(id)) continue;
            List<String> notes = notices.get(id);
            response.append("\n\n**").append(id).append("**");
            for (String note : notes) {
                response.append(note);
            }
            if (printDeposits.contains(id)) {
                Map<ResourceType, Double> depo;
                if (id > Integer.MAX_VALUE) {
                    depo = offshore.getDeposits(id, false);
                } else {
                    depo = offshore.getDeposits((int) id, false);
                }
                response.append("\n- Deposits: `" + ResourceType.toString(depo) + "` worth: `$" + MathMan.format(ResourceType.convertedTotal(depo)) + "`");
            }
            response.append("\n\n");
        }

        return response.toString();
    }

    @Command(desc = "List the owners of the guilds Locutus is connected to", viewable = true)
    @RolePermission(value = Roles.ADMIN, root = true)
    public String listGuildOwners() {
        ArrayList<GuildDB> guilds = new ArrayList<>(Locutus.imp().getGuildDatabases().values());
        guilds.sort(new Comparator<GuildDB>() {
            @Override
            public int compare(GuildDB o1, GuildDB o2) {
                return Long.compare(o1.getGuild().getIdLong(), o2.getGuild().getIdLong());
            }
        });
        StringBuilder result = new StringBuilder();
        for (GuildDB value : guilds) {
            Guild guild = value.getGuild();
            User owner = Locutus.imp().getDiscordApi().getUserById(guild.getOwnerIdLong());
            result.append(guild.getIdLong() + " | " + guild.getName() + " | " + owner.getName()).append("\n");
        }
        return result.toString();
    }


    @Command(desc = "Calculate this turns alliance metrics for the top alliances (default 80) and save them")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String syncMetrics(@Default("80") int topX) throws IOException, ParseException {
        AllianceMetric.update(topX);
        return "Updated metrics for top " + topX + " alliances";
    }

    @Command(desc = "Fetch and update the cities of all nations using the API, and run associated events")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String syncCities(NationDB db) throws IOException, ParseException {
        StringBuilder result = new StringBuilder();
        result.append("Outdated cities: " + db.getDirtyCities().size() + "\n");

        List<Event> events = new ArrayList<>();
        db.updateAllCities(events::add);
        if (events.size() > 0) {
            Locutus.imp().getExecutor().submit(() -> {
                for (Event event : events) event.post();;
            });
        }
        result.append("events: " + events.size() + "\n");
        result.append("Outdated cities: " + db.getDirtyCities().size() + "\n");
        result.append("Updated all cities. " + events.size() + " changes detected");
        return result.toString();
    }

    @Command()
    @RolePermission(value = Roles.ADMIN, root = true)
    public String syncCitiesTest(NationDB db) throws IOException, ParseException {
        StringBuilder result = new StringBuilder();
        result.append("Outdated cities: " + db.getDirtyCities().size() + "\n");

        List<Event> events = new ArrayList<>();
        db.updateAllCities(events::add);
        if (events.size() > 0) {
            Locutus.imp().getExecutor().submit(() -> {
                for (Event event : events) event.post();;
            });
        }
        result.append("events: " + events.size() + "\n");
        result.append("Outdated cities: " + db.getDirtyCities().size() + "\n");
        result.append("Updated all cities. " + events.size() + " changes detected");
        return result.toString();
    }

//    @Command()
//    @RolePermission(value = Roles.ADMIN, root = true)
//    public String syncCitiesTest2(NationDB db, @Me DBNation me) throws IOException, ParseException {
//        Map<Integer, JavaCity> cities = me.getCityMap(true);
//        StringBuilder result = new StringBuilder();
//        result.append("Dirty cities: " + db.getDirtyCities().size() + "\n");
//
//        List<Event> events = new ArrayList<>();
//        db.updateAllCities(events::add);
//        if (events.size() > 0) {
//            Locutus.imp().getExecutor().submit(() -> {
//                for (Event event : events) event.post();;
//            });
//        }
//        result.append("events: " + events.size() + "\n");
//        result.append("Dirty cities: " + db.getDirtyCities().size() + "\n");
//        result.append("Updated all cities. " + events.size() + " changes detected");
//        return result.toString();
//    }


    @Command(desc = """
            Fetch and update nations from the API
            If no nations are specified, then all will be fetched
            Note: This does not update cities""")
    public String syncNations(NationDB db, @Default @AllowDeleted Set<DBNation> nations, @Switch("d") boolean dirtyNations) throws IOException, ParseException {
        if (dirtyNations) {
            db.updateDirtyNations(Event::post, Integer.MAX_VALUE);
        }
        List<Event> events = new ArrayList<>();
        Set<Integer> updatedIds;
        if (nations != null && !nations.isEmpty()) {
            updatedIds = db.updateNations(nations.stream().map(DBNation::getId).toList(), events::add);
        } else {
            updatedIds = db.updateAllNations(events::add, true);
            db.updateAlliances(null, events::add);
        }
        if (events.size() > 0) {
            Locutus.imp().getExecutor().submit(() -> {
                for (Event event : events) event.post();;
            });
        }
        return "Updated " + updatedIds.size() + " nations. " + events.size() + " changes detected";
    }

    @Command(desc = """
            Fetch and update bank records
            If no alliance is specified, only public bank records are fetched
            Alliance records are restricted by API limitations, typically 14 days, regardless of the timestamp specified""")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String syncBanks(@Me GuildDB db, @Me IMessageIO channel, @Default DBAlliance alliance, @Default @Timestamp Long timestamp) throws IOException, ParseException {
        if (alliance != null) {
            db = alliance.getGuildDB();
            if (db == null) throw new IllegalArgumentException("No guild found for AA:" + alliance);

            channel.send("Syncing banks for " + db.getGuild() + "...");
            OffshoreInstance bank = alliance.getBank();
            bank.sync(timestamp, false);
        }

        Locutus.imp().getBankDB().updateBankRecs(false, Event::post);
        return "Done!";
    }

    @Command(desc = "Recalculate blockade flags for all nations, and run associated events")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String syncBlockades() throws IOException, ParseException {
        Locutus.imp().getWarDb().syncBlockades();
        return "Done!";
    }

    @Command(desc = "Fetch and save forum post topic names, optionally for a specific section id/section name")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String syncForum(@Default Integer sectionId, @Default String sectionName) throws IOException, ParseException, SQLException {
        ForumDB forumDB = Locutus.imp().getForumDb();
        if (sectionId != null) {
            if (sectionName == null) sectionName = forumDB.getSectionName(sectionId);
            if (sectionName == null) {
                throw new IllegalArgumentException("No section found for id: " + sectionId);
            }
            forumDB.scrapeTopic(sectionId, sectionName);
        } else {
            forumDB.update();
        }
        return "Done!";
    }

    @Command(desc = "List users in the guild that have provided login credentials to locutus")
    @Ephemeral
    @RolePermission(value = Roles.ADMIN, root = true)
    public String listAuthenticated(@Me GuildDB db) {
        List<Member> members = db.getGuild().getMembers();

        Map<DBNation, Rank> registered = new LinkedHashMap<>();
        Map<DBNation, String> errors = new HashMap<>();

        Set<Integer> alliances = db.getAllianceIds(false);
        for (Member member : members) {
            DBNation nation = DiscordUtil.getNation(member.getUser());
            if (nation != null && (alliances.isEmpty() || alliances.contains(nation.getAlliance_id()))) {
                try {
                    Auth auth = nation.getAuth(true);
                    registered.put(nation, Rank.byId(nation.getPosition()));
                    try {
                        ApiKeyPool.ApiKey key = auth.fetchApiKey();
                    } catch (Throwable e) {
                        errors.put(nation, e.getMessage());
                    }
                } catch (IllegalArgumentException ignore) {}
            }
        }

        if (registered.isEmpty()) {
            return "No registered users";
        }
        StringBuilder result = new StringBuilder();
        for (Map.Entry<DBNation, Rank> entry : registered.entrySet()) {
            result.append(entry.getKey().getNation() + "- " + entry.getValue());
            String error = errors.get(entry.getKey());
            if (error != null) {
                result.append(": Could not validate: " + error);
            }
            result.append("\n");
        }
        return result.toString().trim();
    }

    @Command(desc = "Generate a sheet of nation customization (or lack thereof), and mark specific nation ids in the sheet with the ids provided", viewable = true)
    @RolePermission(value = Roles.MEMBER, onlyInGuildAlliance = true)
    public String multiInfoSheet(@Me IMessageIO io, @Me GuildDB db, Set<DBNation> nations, @Switch("s") SpreadSheet sheet, @Switch("m") Set<DBNation> mark) throws IOException, ParseException, GeneralSecurityException {
        Set<Integer> nationIds = new IntOpenHashSet(nations.stream().map(DBNation::getId).collect(Collectors.toSet()));
        Map<Integer, String> discords = new Int2ObjectOpenHashMap<>();
        Map<Integer, Set<Long>> discordIds = new Int2ObjectOpenHashMap<>();

        for (DBNation nation : Locutus.imp().getNationDB().getAllNations()) {
            Long discordId = nation.getUserId();
            if (discordId != null) {
                discordIds.computeIfAbsent(nation.getId(), k -> new LongOpenHashSet()).add(discordId);
            }
            String discordStr = nation.getDiscordString();
            if (discordStr != null) {
                discords.put(nation.getId(), discordStr);
            }
        }

        Map<String, Set<Integer>> duplicateDiscordNames = new Object2ObjectOpenHashMap<>();
        {
            for (Map.Entry<Integer, String> entry : discords.entrySet()) {
                duplicateDiscordNames.computeIfAbsent(entry.getValue(), k -> new IntOpenHashSet()).add(entry.getKey());
            }
            duplicateDiscordNames.entrySet().removeIf(e -> e.getValue().size() == 1);
        }
        Map<Long, Set<Integer>> duplicateDiscordIds = new Object2ObjectOpenHashMap<>();
        {
            for (Map.Entry<Integer, Set<Long>> entry : discordIds.entrySet()) {
                for (long discordId : entry.getValue()) {
                    duplicateDiscordIds.computeIfAbsent(discordId, k -> new IntOpenHashSet()).add(entry.getKey());
                }
            }
            duplicateDiscordIds.entrySet().removeIf(e -> e.getValue().size() == 1);
        }

        SnapshotMultiData orbisMultiData = new SnapshotMultiData();

        Map<BigInteger, Integer> uidCounts = new Object2IntOpenHashMap<>();
        Map<Integer, BigInteger> uidByNation = new Object2ObjectOpenHashMap<>();
        for (DBNation nation : nations) {
            BigInteger uid = nation.getLatestUid(true);
            if (uid != null) {
                uidCounts.merge(uid, 1, Integer::sum);
            }
            uidByNation.put(nation.getId(), uid);
        }

        long now = System.currentTimeMillis();
        List<String> header = new ArrayList<>(Arrays.asList(
                "nation",
                "leader",
                "cities",
                "alliance",
                "position",
                "continent",
                "active",
                "discord",
                "picked_land", // if their location doesn't match the most common location
                "custom_flag", // if flagCounts.get(flag) <= 1
                "currency",
                "portrait",
                "leader_title",
                "nation_title",
                "domestic_policy",
                "war_policy",
                "uid_match",
                "bot_validated",
                "irl_verified",
                "mark"
        ));

        if (sheet == null) {
            sheet = SpreadSheet.create(db, SheetKey.MULTI_BULK);
        }
        sheet.setHeader(header);

        ValueStore<DBNation> cacheStore = PlaceholderCache.createCache(nations, DBNation.class);

        for (DBNation nation : nations) {
            SnapshotMultiData.MultiData data = orbisMultiData.data.get(nation.getNation_id());
            Long locationPair = data == null ? null : (Long) data.location();
            Continent continent = nation.getContinent();
            boolean isMostCommon = locationPair != null && orbisMultiData.mostCommonLocation.get(continent).equals(locationPair);
            String mostCommonMsg = locationPair == null ? "null" : (isMostCommon ? "Default" : "Custom");

            String flagUrl = data == null ? null : (String) data.flagUrl();
            String customFlagMsg = flagUrl == null ? "null" : (orbisMultiData.flagCounts.getOrDefault(flagUrl, 0) <= 1 ? "Custom" : "Default");
            String currency = data == null ? "null" : (String) data.currency();
            String portrait = data == null ? "null" : (String) data.portraitUrl();
            String leaderTitle = data == null ? "null" : (String) data.leaderTitle();
            String nationTitle = data == null ? "null" : (String) data.nationTitle();

            String discord = discords.getOrDefault(nation.getId(), "null");

            ArrayList<Object> row = new ArrayList<>();

            row.add(MarkupUtil.sheetUrl(nation.getName(), nation.getUrl()));
            row.add(nation.getLeader());
            row.add(nation.getCities());
            row.add(MarkupUtil.sheetUrl(nation.getAllianceName(), nation.getAllianceUrl()));
            row.add(nation.getPosition());
            row.add(nation.getContinent().name());
            row.add(now - nation.lastActiveMs());
            row.add(discord);
            row.add(mostCommonMsg);
            row.add(customFlagMsg);
            row.add(currency);
            row.add(portrait);
            row.add(leaderTitle);
            row.add(nationTitle);
            row.add(nation.getDomesticPolicy().name());
            row.add(nation.getWarPolicy().name());
            row.add(uidCounts.getOrDefault(uidByNation.get(nation.getId()), 0) - 1);
            row.add(nation.isVerified());
            row.add(nation.hasProvidedIdentity(cacheStore));
            row.add(mark != null && mark.contains(nation) ? "X" : null);

            sheet.addRow(row);
        }

        sheet.updateClearCurrentTab();
        sheet.updateWrite();

        IMessageBuilder msg = sheet.attach(io.create(), "login_times");
        // append duplicate discord ids
        if (!duplicateDiscordNames.isEmpty()) {
            StringBuilder discordMsg = new StringBuilder("\n### Duplicate Discord Names\n");
            for (Map.Entry<String, Set<Integer>> entry : duplicateDiscordNames.entrySet()) {
                discordMsg.append(entry.getKey()).append(": ").append(StringMan.join(entry.getValue(), ",")).append("\n");
            }
            msg.append(discordMsg.toString());
        }
        if (!duplicateDiscordIds.isEmpty()) {
            StringBuilder discordMsg = new StringBuilder("\n### Duplicate Discord IDs\n");
            for (Map.Entry<Long, Set<Integer>> entry : duplicateDiscordIds.entrySet()) {
                discordMsg.append(entry.getKey()).append(": ").append(StringMan.join(entry.getValue(), ",")).append("\n");
            }
            msg.append(discordMsg.toString());
        }
        msg.send();
        return null;
    }

    @Command(desc = "Recalculate bans of nations sharing the same network concurrently")
    @RolePermission(value = Roles.ADMIN, root = true)
    public synchronized String importLinkedBans() throws IOException {
        Locutus.imp().getNationDB().importMultiBans();
        return "Done";
    }

    private static ConcurrentHashMap<Integer, Long> UPDATED_UID = new ConcurrentHashMap<>();

    @Command(desc = "List players currently sharing a network or an active ban", viewable = true)
    @RolePermission(Roles.INTERNAL_AFFAIRS)
    public synchronized String hasSameNetworkAsBan(@Me IMessageIO io, @Me @Default User author, Set<DBNation> nations, @Switch("e") boolean listExpired,
                                                   @Switch("a") boolean onlySameAlliance,
                                                   @Switch("t") @Timediff Long onlySimilarTime,
                                                   @Switch("d") boolean sortByAgeDays,
                                                   @Switch("l") boolean sortByLogin,
                                                   @Switch("f") boolean forceUpdate) throws IOException {
        if (forceUpdate && nations.size() > 300 && !Roles.ADMIN.hasOnRoot(author)) {
            throw new IllegalArgumentException("Too many nations to update");
        }
        Map<Integer, BigInteger> latestUids = Locutus.imp().getDiscordDB().getLatestUidByNation();
        Map<BigInteger, Set<Integer>> uidsByNation = new HashMap<>();
        Map<BigInteger, Set<DBNation>> uidsByNationExisting = new HashMap<>();
        for (Map.Entry<Integer, BigInteger> entry : latestUids.entrySet()) {
            BigInteger uid = entry.getValue();
            int nationId = entry.getKey();
            uidsByNation.computeIfAbsent(uid, k -> new HashSet<>()).add(nationId);
            DBNation nation = DBNation.getById(nationId);
            if (nation != null) {
                uidsByNationExisting.computeIfAbsent(uid, k -> new HashSet<>()).add(nation);
            }
        }

        Map<Long, Set<DBNation>> sharesDiscord = new Object2ObjectOpenHashMap<>();
        for (DBNation nation : nations) {
            Long userId = nation.getUserId();
            if (userId != null) {
                sharesDiscord.computeIfAbsent(userId, k -> new HashSet<>()).add(nation);
            }
        }

        CompletableFuture<IMessageBuilder> msgFuture = io.sendMessage("Updating...");
        IMessageBuilder msg = null;
        long start = System.currentTimeMillis();

        Set<Integer> nationIdsWithUids = new IntOpenHashSet();
        for (Set<DBNation> nationSet : uidsByNationExisting.values()) {
            for (DBNation nation : nationSet) {
                nationIdsWithUids.add(nation.getId());
            }
        }
        List<DBNation> nationsList = new ArrayList<>(nations);
        long now = System.currentTimeMillis();
        for (int i = 0; i < nationsList.size(); i++) {
            DBNation nation = nationsList.get(i);
            if (!forceUpdate && nation.getVm_turns() > 0 || nation.active_m() > 10080) continue;
            long dateUpdated = UPDATED_UID.getOrDefault(nation.getId(), 0L);
            if (dateUpdated != 0 && (!forceUpdate || now - dateUpdated < TimeUnit.DAYS.toMillis(1))) continue;
            if (!nationIdsWithUids.contains(nation.getId())) {
                if (System.currentTimeMillis() - start > 10000) {
                    msg = io.updateOptionally(msgFuture, "Fetching " + nation.getNation() + "(" + i + "/" + nationsList.size() + ")");
                    start = System.currentTimeMillis();
                }
                UPDATED_UID.put(nation.getId(), now);
                BigInteger uid = nation.getLatestUid(true);
                if (uid == null) {
                    uid = nation.getLatestUid(false);
                    if (i + 1 < nationsList.size()) {
                        // sleep
                        try {
                            Thread.sleep(ThreadLocalRandom.current().nextInt(200, 400));
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
                if (uid != null) {
                    uidsByNationExisting.computeIfAbsent(uid, k -> new HashSet<>()).add(nation);
                }
            }
        }

        if (forceUpdate) {
            Set<DBNation> nationsToUpdate = new HashSet<>();
            for (Set<DBNation> nationSet : uidsByNationExisting.values()) {
                nationsToUpdate.addAll(nationSet);
            }
            int i = 1;
            for (DBNation nation : nationsToUpdate) {
                long dateUpdated = UPDATED_UID.getOrDefault(nation.getId(), 0L);
                if (System.currentTimeMillis() - start > 10000) {
                    msg = io.updateOptionally(msgFuture, "Updating " + nation.getNation() + "(" + i + "/" + nationsToUpdate.size() + ")");
                    start = System.currentTimeMillis();
                }
                if (System.currentTimeMillis() - start > 10000) {
                    msg = io.updateOptionally(msgFuture, "Updating " + nation.getNation() + "(" + i + "/" + nationsToUpdate.size() + ")");
                    start = System.currentTimeMillis();
                }
                nation.fetchUid(true);
                try {
                    Thread.sleep(ThreadLocalRandom.current().nextInt(200, 400));
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                i++;
            }
            if (msg != null && msg.getId() > 0) io.delete(msg.getId());
            return hasSameNetworkAsBan(io, author, nations, listExpired, onlySameAlliance, onlySimilarTime, sortByAgeDays, sortByLogin, false);
        }

        sharesDiscord.entrySet().removeIf(entry -> entry.getValue().size() <= 1);

        // remove uidsBynationExisting when values size <= 1
        uidsByNationExisting.entrySet().removeIf(entry -> entry.getValue().size() <= 1);
        uidsByNationExisting.entrySet().removeIf(entry -> {
            boolean contains = false;
            for (DBNation nation : entry.getValue()) {
                if (nations.contains(nation)) {
                    contains = true;
                    break;
                }
            }
            if (onlySameAlliance) {
                Set<Integer> aaIds = entry.getValue().stream().map(DBNation::getAlliance_id).collect(Collectors.toSet());
                entry.getValue().removeIf(nation -> nation.getAlliance_id() == 0 || !aaIds.contains(nation.getAlliance_id()));
            }
            if (onlySimilarTime != null) {
                Map<Integer, Boolean> hasSimilarTime = new Object2BooleanOpenHashMap<>();
                for (DBNation nation : entry.getValue()) {
                    for (DBNation other : entry.getValue()) {
                        if (nation == other) continue;
                        if (Math.abs(nation.lastActiveMs() - other.lastActiveMs()) < onlySimilarTime) {
                            hasSimilarTime.put(nation.getId(), true);
                            hasSimilarTime.put(other.getId(), true);
                        }
                    }
                }
                entry.getValue().removeIf(nation -> !hasSimilarTime.getOrDefault(nation.getId(), false));
            }
            return !contains;
        });

        // get the bans
        Map<Integer, DBBan> bans = Locutus.imp().getNationDB().getBansByNation();

        Map<DBNation, Set<DBBan>> sameNetworkBans = new HashMap<>();

        for (DBNation nation : nations) {
            BigInteger uid = latestUids.get(nation.getId());
            if (uid == null) continue;
            Set<Integer> nationIds = uidsByNation.get(uid);

            List<DBBan> natBans = nation.getBans();
            if (!listExpired) natBans.removeIf(DBBan::isExpired);

            if (!natBans.isEmpty()) {
                sameNetworkBans.put(nation, new HashSet<>(natBans));
            }

            for (int id : nationIds) {
                if (id == nation.getId()) continue;
                DBBan ban = bans.get(id);
                if (ban != null && (listExpired || !ban.isExpired())) {
                    sameNetworkBans.computeIfAbsent(nation, k -> new HashSet<>()).add(ban);
                }
            }
        }

        StringBuilder response = new StringBuilder();

        if (!sharesDiscord.isEmpty()) {
            response.append("## Active nations sharing the same discord:\n");
            for (Map.Entry<Long, Set<DBNation>> entry : sharesDiscord.entrySet()) {
                if (entry.getValue().isEmpty()) continue;
                response.append(entry.getKey()).append(":\n");
                for (DBNation nation : entry.getValue()) {
                    response.append("- ").append(nation.getUrl());
                    if (nation.getAlliance_id() != 0) {
                        response.append(" | " + nation.getAllianceName());
                    }
                    response.append(" | " + nation.active_m() + "m");
                    response.append(" | " + nation.getAgeDays() + "d");
                    response.append("\n");
                }
            }
            response.append("\n");
        }

        if (!uidsByNationExisting.isEmpty()) {
            response.append("## Active nations sharing the same network:\n");
            for (Map.Entry<BigInteger, Set<DBNation>> entry : uidsByNationExisting.entrySet()) {
                if (entry.getValue().isEmpty()) continue;
                response.append(entry.getKey().toString(16)).append(":\n");
                List<DBNation> sorted = new ArrayList<>(entry.getValue());
                if (sortByAgeDays) {
                    sorted.sort(Comparator.comparingLong(DBNation::getAgeDays));
                } else if (sortByLogin) {
                    sorted.sort(Comparator.comparingLong(DBNation::lastActiveMs));
                } else {
                    sorted.sort(Comparator.comparingLong(DBNation::getId));
                }
                for (DBNation nation : sorted) {
                    response.append("- ").append(nation.getUrl());
                    if (nation.getAlliance_id() != 0) {
                        response.append(" | " + nation.getAllianceName());
                    }
                    response.append(" | " + nation.active_m() + "m");
                    response.append(" | " + nation.getAgeDays() + "d");
                    response.append("\n");
                }
            }
            response.append("\n");
        }
        if (!sameNetworkBans.isEmpty()) {
            response.append("## Bans on the same network:\n");
            for (Map.Entry<DBNation, Set<DBBan>> entry : sameNetworkBans.entrySet()) {
                DBNation nation = entry.getKey();
                if (!nations.contains(nation)) continue;
                // Key then dot points, with nation url
                response.append(entry.getKey().getUrl()).append(":\n");
                for (DBBan ban : entry.getValue()) {
                    StringBuilder banStr = new StringBuilder("nation:" + ban.nation_id);
                    if (ban.discord_id > 0) {
                        banStr.append(" discord:").append(ban.discord_id);
                    }
                    if (ban.isExpired()) {
                        banStr.append(" (expired)");
                    } else {
                        banStr.append(" (expires ").append(TimeUtil.secToTime(TimeUnit.MILLISECONDS, ban.getTimeRemaining())).append(")");
                    }
                    banStr.append(": `" + ban.reason.replace("\n", " ") + "`");
                    response.append("- ").append(banStr).append("\n");
                }
            }
        }

        response.append("\nDone!");

        return response.toString();
    }

    @Command(desc = "Recalculate the alliance and nation loots from the attacks stored in the database")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String syncLootFromAttacks() {
        int found = 0;
        int added = 0;
        List<LootEntry> loots = new ObjectArrayList<>();
        Locutus.imp().getWarDb().iterateAttacks(0, AttackType.A_LOOT, (war, attack) -> {
            if (attack.getAllianceIdLooted() > 0) {
                LootEntry existing = Locutus.imp().getNationDB().getAllianceLoot(attack.getAllianceIdLooted());
                if (existing != null && existing.getDate() < attack.getDate()) {
                    Double pct = attack.getLootPercent();
                    if (pct == 0) pct = 0.01;
                    double factor = 1/pct;
                    double[] loot = attack.getLoot();

                    double[] lootCopy = loot == null ? ResourceType.getBuffer() : loot.clone();
                    for (int i = 0; i < lootCopy.length; i++) {
                        lootCopy[i] = (lootCopy[i] * factor) - lootCopy[i];
                    }

                    loots.add(LootEntry.forAlliance(attack.getAllianceIdLooted(), attack.getDate(), lootCopy, NationLootType.WAR_LOSS));
                }
            }
        });
        if (loots.isEmpty()) return "No new loot found";
        Locutus.imp().runEventsAsync(events -> Locutus.imp().getNationDB().saveLoot(loots, events));
        return "Done!";
    }

    @Command(desc = """
            Global toggles for conditional message settings
            setMeta = If nation meta is set so that multiple messages cannot be sent to the same person, or to older nations
            sendMessages = If message sending is enabled
            run = Force send the messages now to applicable nations""")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String conditionalMessageSettings(boolean setMeta, boolean sendMessages, boolean run) {
        GuildCustomMessageHandler messageHandler = Locutus.imp().getMessageHandler();
        messageHandler.setMeta(setMeta);
        messageHandler.setSendMessages(sendMessages);
        if (run) {
            messageHandler.run();
        }
        return "Done!";
    }


    @Command(desc = """
            Returns a list of forum profiles and their respective nation id / discord tag
            Deprecated because it is an unauthenticated list, anyone can set their discord or nation on the forums
            Information purposes only""")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String syncForumProfiles(@Me GuildDB guildDB, @Me IMessageIO io, @Default SpreadSheet sheet) throws GeneralSecurityException, IOException {
        if (sheet == null) {
            sheet = SpreadSheet.create(guildDB, SheetKey.FORUM_PROFILES);
        }
        String urlRaw = "https://forum.politicsandwar.com/index.php?/profile/%s-ignore/";

        List<String> header = new ArrayList<>(Arrays.asList(
                "profile",
                "discord",
                "discord_id",
                "nation_id"
        ));
        sheet.setHeader(header);

        for (int i = 0; i < 15000; i++) {
            String url = String.format(urlRaw, i);

            try {
                String html = FileUtil.readStringFromURL(PagePriority.FORUM_PAGE, url);
                Document dom = Jsoup.parse(html);
                int nationId = Integer.parseInt(dom.select("strong:matches(Nation ID)").first().parent().nextElementSibling().text());
                String discordId = dom.select("strong:matches(Discord Name)").first().parent().nextElementSibling().text();

                if (nationId != 0) {
                    String[] split = discordId.split("#");
                    User user = null;
                    if (split.length == 2) {
                        Long userId;
                        for (PNWUser dbUser : Locutus.imp().getDiscordDB().getRegisteredUsers().values()) {
                            if (dbUser.getDiscordName() != null && dbUser.getDiscordName().equalsIgnoreCase(discordId)) {
                                userId = dbUser.getDiscordId();
                                user = Locutus.imp().getDiscordApi().getUserById(userId);
                                break;
                            }
                        }
                    }
                    if (user == null && !discordId.contains("#")) {
                        user = Locutus.imp().getDiscordApi().getUserByName(discordId, true, guildDB.getGuild());
                    }

                    header.set(0, i + "");
                    header.set(1, discordId);
                    header.set(2, user == null ? "" : user.getId());
                    header.set(3, Integer.toString(nationId));

                    sheet.addRow(header);
                }
            } catch (Throwable ignore) {
            }
        }

        sheet.updateClearCurrentTab();
        sheet.updateWrite();

        sheet.attach(io.create(), "login_times").send();
        return null;
    }

    //    SyncBounties
    @Command(desc = "Force a fetch and update of bounties from the api")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String syncBounties() throws IOException {
        Locutus.imp().getWarDb().updateBountiesV3();
        return "Done!";
    }


//    SyncWarRooms
    @Command(desc = "Force a fetch and update of war rooms for each guild")
    @RolePermission(value = Roles.MILCOM)
    public String purgeWarRooms( // war room delete_all
            @Me GuildDB db,
            @Me IMessageIO io,
            @Me User user,
            @Arg("Only delete a single channel") @Switch("c") MessageChannel channel) throws IOException {

        WarCategory warCat = db.getWarChannel(true);
        if (channel == null) {
            channel = io instanceof DiscordChannelIO ? ((DiscordChannelIO) io).getChannel() : null;
        }
        if (channel != null) {
            Guild chanGuild = ((GuildMessageChannel) channel).getGuild();
            if (!Roles.MILCOM.has(user, chanGuild)) {
                throw new IllegalArgumentException("Missing " + Roles.MILCOM.toDiscordRoleNameElseInstructions(chanGuild));
            }
        }
        WarRoom room = channel instanceof GuildMessageChannel mC ? WarRoomUtil.getGlobalWarRoom(mC, WarCatReason.PURGE_COMMAND) : null;
        if (channel != null && room == null) {
            throw new IllegalArgumentException("Channel is not a war room");
        }
        if (room != null) {
            warCat.deleteRoom(room, "Deleted by " + DiscordUtil.getFullUsername(user));
            return "Deleted " + channel.getName();
        } else {
            Set<Category> categories = new HashSet<>();
            Iterator<Map.Entry<Integer, WarRoom>> iter = warCat.getWarRoomMap().entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry<Integer, WarRoom> entry = iter.next();
                StandardGuildMessageChannel guildChan = entry.getValue().getChannel();
                if (guildChan != null) {
                    Category category = guildChan.getParentCategory();
                    if (category != null) categories.add(category);
                    RateLimitUtil.queue(guildChan.delete());
                }
                iter.remove();
            }
            for (Category category : categories) {
                if (category.getName().toLowerCase().startsWith("warcat-")) {
                    RateLimitUtil.queue(category.delete());
                }
            }
            return "Deleted war rooms! See also: " + CM.admin.sync.warrooms.cmd.toSlashMention();
        }
    }
//    SyncTreaties
    @Command(desc = "Force a fetch and update of treaties from the api")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String syncTreaties() throws IOException {
        Locutus.imp().getNationDB().updateTreaties(Event::post);
        return "Updated treaties!";
    }
//    SyncAttacks
    @Command(desc = "Force a fetch and update of attacks from the api")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String syncAttacks(boolean runAlerts, @Switch("f") boolean fixAttacks) throws IOException {
        if (fixAttacks) {
            Locutus.imp().getWarDb().reEncodeBadAttacks();
            return "Done!";
        } else {
            WarUpdateProcessor.checkActiveConflicts();
            Locutus.imp().getWarDb().updateAttacksAndWarsV3(runAlerts, Event::post, Settings.USE_V2);
            return "Done!";
        }
    }
//    SyncTrade
    @Command(desc = "Force a fetch and update of trades from the api")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String syncTrade() throws IOException {
        Locutus.imp().getTradeManager().updateTradeList(Event::post);
        return "Done!";
    }
//    SyncUid [all]
    @Command(desc = "Force a fetch and update of uids from the api")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String syncUid(boolean all) throws IOException {
        if (all) {
            Collection<DBNation> nations = Locutus.imp().getNationDB().getAllNations();
            for (DBNation nation : nations) {
                if (!Locutus.imp().getDiscordDB().getUuids(nation.getNation_id()).isEmpty()) continue;
                BigInteger uid = new GetUid(nation, false).call();
            }
        } else {
            Map<BigInteger, Set<Integer>> map = Locutus.imp().getDiscordDB().getUuidMap();
            for (Map.Entry<BigInteger, Set<Integer>> entry : map.entrySet()) {
                if (entry.getValue().size() <= 1) continue;

                for (int nationId : entry.getValue()) {
                    DBNation nation = Locutus.imp().getNationDB().getNationById(nationId);
                    if (nation != null) {
                        new GetUid(nation, false).call();
                    }
                }
            }
        }
        return "Done! See also " + CM.admin.list.multis.cmd.toSlashMention();
    }
//    SyncTaxes
    @Command(desc = "Force a fetch and update of taxes from the api",
    groups = {
            "Alliance to update",
            "Update via sheet tax records",
            "Update via login"
    })
    @RolePermission(value = Roles.ADMIN, root = true)
    public String syncTaxes(
            @Me GuildDB db, @Me IMessageIO io,
            @Arg(value = "Specify other alliances, instead of the ones registered to this guild", group = 0)
            @Switch("a") DBAlliance alliance,
            @Arg(value = "The timeframe to update")
            @Switch("t") @Timestamp Long timestamp,
            @Arg(value = "Update using values in a spreadsheet\n" +
                    "Deprecated, use the api instead (i.e. no arguments)", group = 1)
            @Switch("s") SpreadSheet sheet_deprecated,
            @Arg(value = "Use the legacy deprecated method to update via login (not recommended)", group = 2)
            @Switch("l") boolean legacy_deprecated
            ) throws GeneralSecurityException, IOException, ExecutionException, InterruptedException {
        if (legacy_deprecated && sheet_deprecated != null) {
            throw new IllegalArgumentException("Cannot use both `sheet_deprecated` and `legacy_deprecated`");
        }

        Set<Integer> aaIds = alliance != null ? Set.of(alliance.getId()): db.getAllianceIds();
        if (aaIds.size() > 1) {
            throw new IllegalArgumentException("Too many alliances to update (max 1). Please specify an alliance");
        }
        if (aaIds.isEmpty()) {
            throw new IllegalArgumentException("No alliances to update. Please specify an alliance");
        }
        int aaId = aaIds.iterator().next();

        if (sheet_deprecated != null) {
            if (timestamp != null) {
                throw new IllegalArgumentException("The `timestamp` argument is not supported with `sheet_deprecated`");
            }
            return SyncTaxes.updateTaxesLegacy(db, null, aaId);
        }
        if (legacy_deprecated) {
            DBAlliance aa = DBAlliance.get(aaId);
            if (aa == null) {
                throw new IllegalArgumentException("Alliance AA:" + aaId + " is not registered to guild: " + aaId);
            }
            CompletableFuture<IMessageBuilder> msgFuture = (io.sendMessage("Syncing taxes for " + aaId + ". Please wait..."));

            int taxesCount = aa.updateTaxesLegacy(timestamp);

            IMessageBuilder msg = msgFuture.get();
            if (msg != null && msg.getId() > 0) {
                io.delete(msg.getId());
            }
            return "Updated " + taxesCount + " records.\n"
                    + "<" + SyncTaxes.updateTurnGraph(db, aaId) + ">";
        }
        AllianceList aaList = db.getAllianceList();
        if (aaList == null) {
            return "No alliance registered to this guild. See " + GuildKey.ALLIANCE_ID.getCommandMention();
        }
        List<TaxDeposit> taxes = aaList.updateTaxes(timestamp);
        return "Updated " + taxes.size() + " records.";
    }

//    SyncMail /mail check
    @Command(desc = "Force a fetch and update of mail for a nation")
    @RolePermission(value = Roles.MAIL)
    public String syncMail(@Me User user, @Me IMessageIO io, @Me DBNation nation, @Default DBNation account) throws IOException {
        if (account != null && account.getId() != nation.getId()) {
            GuildDB db = account.getGuildDB();
            if (db != null) {
                if (!Roles.MAIL.has(user, db.getGuild())) {
                    throw new IllegalArgumentException("Missing " + Roles.MAIL.toDiscordRoleNameElseInstructions(db.getGuild()));
                }
            } else {
                if (!Roles.ADMIN.hasOnRoot(user)) {
                    throw new IllegalArgumentException("Missing " + Roles.ADMIN.toDiscordRoleNameElseInstructions(Locutus.imp().getServer()));
                }
            }
        }
        if (account == null) account = nation;
        new AlertMailTask(account.getAuth(true), io.getIdLong()).run();
        return "Done!";
    }

    @Command(desc = "Force a fetch and update of banks from the api")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String syncOffshore(DBAlliance alliance) throws IOException {
        OffshoreInstance bank = alliance.getBank();
        if (bank == null) throw new IllegalArgumentException("No bank found for " + alliance + ". Set one with " + CM.offshore.add.cmd.toSlashMention());
        bank.sync(0L, false);
        return "Done!";
    }

    @Command(desc = "View info about trades with a given id", viewable = true)
    public String tradeId(Set<Integer> ids) {
        List<DBTrade> offers = new ArrayList<>();
        for (int id : ids) {
            DBTrade trade = Locutus.imp().getTradeManager().getTradeDb().getTradeById(id);
            if (trade != null) offers.add(trade);
        }
        return "- " + StringMan.join(offers, "\n- ");
    }

    @Command(desc = "View info about a guild with a given id", viewable = true)
    @RolePermission(value = Roles.ADMIN, root = true)
    public String guildInfo(Guild guild) {
        return guild.getName() + "/" + guild.getIdLong() + "\n" +
                "Owner: " + guild.getOwner() + "\n" +
                "Members: " + StringMan.getString(guild.getMembers());
    }

    @Command(desc = "View meta information about a nation in the bot's database", viewable = true)
    @RolePermission(value = Roles.ADMIN, root = true)
    public String nationMeta(DBNation nation, NationMeta meta) {
        ByteBuffer buf = nation.getMeta(meta);
        if (buf == null) return "No value set.";

        byte[] arr = new byte[buf.remaining()];
        buf.get(arr);
        buf = ByteBuffer.wrap(arr);

        switch (arr.length) {
            case 0 -> {
                return "" + (buf.get() & 0xFF);
            }
            case 4 -> {
                return "" + (buf.getInt());
            }
            case 8 -> {
                ByteBuffer buf2 = ByteBuffer.wrap(arr);
                return buf.getLong() + "/" + MathMan.format(buf2.getDouble());
            }
            default -> {
                return new String(arr, StandardCharsets.ISO_8859_1);
            }
        }
    }

    @NoFormat
    @Command(desc = "Run a command as another user")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String sudo(@Me Guild guild, @Me IMessageIO io, String command,
                       @Switch("u") User user,
                       @Switch("n") DBNation nation) {
        if (user == null && nation == null) {
            throw new IllegalArgumentException("Specify a user or nation");
        }
        if (user != null && nation != null) {
            throw new IllegalArgumentException("Specify only a user or nation");
        }
        CommandManager2 v2 = Locutus.cmd().getV2();
        if (user != null) {
            v2.run(guild, io, user, command, false, true);
        } else {
            MessageChannel channel = io instanceof DiscordChannelIO dio ? dio.getChannel() : null;
            Message message = io instanceof DiscordChannelIO dio ? dio.getUserMessage() : null;
            LocalValueStore locals = v2.createLocals(null, guild, channel, null, message, io, null);
            locals.addProvider(Key.of(DBNation.class, Me.class), nation);
            v2.run(locals, io, command, false, true);
        }
        return "Done!";
    }

    @NoFormat
    @Command(desc = "Run multiple commands")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String runMultiple(@Me Guild guild, @Me IMessageIO io, @Me User user, @TextArea String commands) {
        commands = commands.replace("\\n", "\n");
        String[] split = commands.split("\\r?\\n" + "[" + Settings.commandPrefix(false) + "|" + "/]");

        for (String cmd : split) {
            Locutus.cmd().getV2().run(guild, io, user, cmd, false, true);
        }
        return "Done!";
    }

    @NoFormat
    @Command(desc = "Format a command for each nation, and run it as yourself")
    @RolePermission(value = Roles.ADMIN)
    public String runForNations(@Me GuildDB db, @Me User user, @Me DBNation me, @Me IMessageIO io, NationPlaceholders placeholders, ValueStore store,
                              Set<DBNation> nations, String command) {
        if (!db.hasAlliance()) {
            throw new IllegalArgumentException("No alliance registered to this guild. " + CM.settings_default.registerAlliance.cmd.toSlashMention());
        }
        for (DBNation nation : nations) {
            if (!db.isAllianceId(nation.getAlliance_id())) {
                throw new IllegalArgumentException("Nation " + nation.getMarkdownUrl() + " is not in the alliance/s " + db.getAllianceIds());
            }
        }
        if (nations.size() > 300) {
            throw new IllegalArgumentException("Too many nations to update (max: 300, provided: " + nations.size() + ")");
        }

        PlaceholderCache<DBNation> cache = new PlaceholderCache<>(nations);
        Function<DBNation, String> formatFunc = placeholders.getFormatFunction(store, command, cache, true);
        StringMessageBuilder condensed = new StringMessageBuilder(db.getGuild());
        Runnable sendTask = () -> {
            if (!condensed.isEmpty()) {
                IMessageBuilder msg = io.create();
                condensed.flatten();
                condensed.writeTo(msg);
                msg.send();
                condensed.clear();
            }
        };

        long start = System.currentTimeMillis();
        for (DBNation nation : nations) {
            try {
                String formattedCmd = formatFunc.apply(nation);
                Map.Entry<CommandResult, List<StringMessageBuilder>> response = me.runCommandInternally(db.getGuild(), user, formattedCmd);
                condensed.append("# " + nation.getMarkdownUrl() + ": " + response.getKey() + "\n");
                for (StringMessageBuilder msg : response.getValue()) {
                    msg.writeTo(condensed);
                }
            } catch (Throwable e) {
                condensed.append("# " + nation.getMarkdownUrl() + ": " + StringMan.stripApiKey(e.getMessage()) + "\n");
            }
            if (-start + (start = System.currentTimeMillis()) > 5000) {
                sendTask.run();
            }
        }
        sendTask.run();
        return "Done!";
    }

    @NoFormat
    @Command(desc = "Run a command as multiple nations")
    @RolePermission(value = Roles.ADMIN, root = true)
    public String sudoNations(@Me GuildDB db, @Me IMessageIO io, NationPlaceholders placeholders, ValueStore store,
                              Set<DBNation> nations, String command) {
        PlaceholderCache<DBNation> cache = new PlaceholderCache<>(nations);
        Function<DBNation, String> formatFunc = placeholders.getFormatFunction(store, command, cache, true);

        StringMessageBuilder condensed = new StringMessageBuilder(db.getGuild());
        Runnable sendTask = () -> {
            if (!condensed.isEmpty()) {
                IMessageBuilder msg = io.create();
                condensed.flatten();
                condensed.writeTo(msg);
                msg.send();
                condensed.clear();
            }
        };

        long start = System.currentTimeMillis();
        for (DBNation nation : nations) {
            String formattedCmd = formatFunc.apply(nation);
            User nationUser = nation.getUser();
            try {
                Map.Entry<CommandResult, List<StringMessageBuilder>> response = nation.runCommandInternally(db.getGuild(), nationUser, formattedCmd);
                condensed.append("# " + nation.getMarkdownUrl() + ": " + response.getKey() + "\n");
                for (StringMessageBuilder msg : response.getValue()) {
                    msg.writeTo(condensed);
                }
            } catch (Throwable e) {
                condensed.append("# " + nation.getMarkdownUrl() + ": " + StringMan.stripApiKey(e.getMessage()) + "\n");
            }
            if (-start + (start = System.currentTimeMillis()) > 5000) {
                sendTask.run();
            }

        }
        sendTask.run();
        return "Done!";
    }

    @Command(desc = "Set the v2 flag")
    public String setV2(boolean value) {
        Settings.USE_V2 = value;
        return "Done! Set v2 to " + value;
    }

}