package link.locutus.discord.web.commands.api;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.AccessType;
import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.apiv1.enums.DepositTypeInfo;
import link.locutus.discord.apiv1.enums.EscrowMode;
import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.annotation.Switch;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.HasOffshore;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.IsMemberIngameOrDiscord;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.entities.Transaction2;
import link.locutus.discord.db.entities.announce.AnnounceType;
import link.locutus.discord.db.entities.announce.Announcement;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.pnw.NationOrAlliance;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.offshore.OffshoreInstance;
import link.locutus.discord.util.offshore.TransferResult;
import link.locutus.discord.util.sheet.SpreadSheet;
import link.locutus.discord.util.task.ia.AuditType;
import link.locutus.discord.util.task.ia.IACheckup;
import link.locutus.discord.util.task.roles.AutoRoleBulkResult;
import link.locutus.discord.util.task.roles.AutoRoleResult;
import link.locutus.discord.util.task.roles.AutoRoleService;
import link.locutus.discord.web.commands.ReturnType;
import link.locutus.discord.web.commands.binding.value_types.CacheType;
import link.locutus.discord.web.commands.binding.value_types.WebAnnouncement;
import link.locutus.discord.web.commands.binding.value_types.WebAnnouncements;
import link.locutus.discord.web.commands.binding.value_types.WebAudit;
import link.locutus.discord.web.commands.binding.value_types.WebAudits;
import link.locutus.discord.web.commands.binding.value_types.WebBalance;
import link.locutus.discord.web.commands.binding.value_types.WebBankAccess;
import link.locutus.discord.web.commands.binding.value_types.WebInt;
import link.locutus.discord.web.commands.binding.value_types.WebMyWars;
import link.locutus.discord.web.commands.binding.value_types.WebSuccess;
import link.locutus.discord.web.commands.binding.value_types.WebTable;
import link.locutus.discord.web.commands.binding.value_types.WebTransferResult;
import link.locutus.discord.web.commands.page.PageHelper;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class IAEndpoints extends PageHelper {
    @Command(desc = "Return IA audit results for the current nation", viewable = true)
    @IsMemberIngameOrDiscord
    @ReturnType(value = WebAudits.class, cache = CacheType.SessionStorage, duration = 30)
    public Object my_audits(@Me GuildDB db, @Me DBNation nation) throws IOException, ExecutionException, InterruptedException {
        if (!db.isAllianceId(nation.getAlliance_id()) || nation.getPositionEnum().id < Rank.MEMBER.id) {
            return error("You are not a member of the guild's alliance.");
        }
        Map<AuditType, Map.Entry<Object, String>> checkupResult = new HashMap<>();
        IACheckup checkup = new IACheckup(db, db.getAllianceList(), true);
        checkupResult = checkup.checkup(null, nation, true, true);
        checkupResult.entrySet().removeIf(f -> f.getValue() == null || f.getValue().getValue() == null);


        WebAudits audits = new WebAudits();
        for (Map.Entry<AuditType, Map.Entry<Object, String>> entry : checkupResult.entrySet()) {
            AuditType type = entry.getKey();
            Map.Entry<Object, String> value = entry.getValue();
            audits.values.add(new WebAudit(type.name(), type.getSeverity().ordinal(), value.getKey().toString(), value.getValue()));
        }

        return audits;
    }

    @Command(desc = "List player announcements for the current nation", viewable = true)
    @IsMemberIngameOrDiscord
    @ReturnType(value = WebAnnouncements.class, cache = CacheType.SessionStorage, duration = 30)
    public Object announcements(@Me GuildDB db, @Me DBNation nation) {
        if (!db.isAllianceId(nation.getAlliance_id()) || nation.getPositionEnum().id < Rank.MEMBER.id) {
            return error("You are not a member of the guild's alliance.");
        }
        List<Announcement.PlayerAnnouncement> all = db.getPlayerAnnouncementsByNation(nation.getNation_id(), false);
        all.removeIf(f -> f.getParent().type != AnnounceType.MESSAGE);

        WebAnnouncements result = new WebAnnouncements();
        for (Announcement.PlayerAnnouncement announcement : all) {
            result.values.add(new WebAnnouncement(announcement.ann_id, announcement.getParent().type.ordinal(), announcement.active, announcement.getParent().title, announcement.getContent()));
        }
        return result;
    }

    @Command(desc = "Get announcement titles, optionally filtering by read state", viewable = true)
    @IsMemberIngameOrDiscord
    @ReturnType(value = WebAnnouncements.class)
    public Object announcement_titles(@Me GuildDB db, @Me DBNation nation, @Switch("r") boolean read) {
        if (!db.isAllianceId(nation.getAlliance_id()) || nation.getPositionEnum().id < Rank.MEMBER.id) {
            return error("You are not a member of the guild's alliance.");
        }
        List<Announcement.PlayerAnnouncement> all = db.getPlayerAnnouncementsByNation(nation.getNation_id(), !read);
        all.removeIf(f -> f.getParent().type != AnnounceType.MESSAGE);

        WebAnnouncements result = new WebAnnouncements();
        for (Announcement.PlayerAnnouncement announcement : all) {
            result.values.add(new WebAnnouncement(announcement.ann_id, announcement.getParent().type.ordinal(), announcement.active, announcement.getParent().title, null));
        }
        return result;
    }

    @Command(desc = "View a specific announcement and mark it read", viewable = true)
    @IsMemberIngameOrDiscord
    @ReturnType(value = WebAnnouncement.class, cache = CacheType.SessionStorage)
    public Object view_announcement(@Me GuildDB db, @Me DBNation nation, int ann_id) {
        if (!db.isAllianceId(nation.getAlliance_id()) || nation.getPositionEnum().id < Rank.MEMBER.id) {
            return error("You are not a member of the guild's alliance.");
        }
        Announcement.PlayerAnnouncement announcement = db.getPlayerAnnouncement(ann_id, nation.getNation_id());
        if (announcement == null) {
            return error("Announcement not found.");
        }
        // mark it as read
        db.setAnnouncementActive(ann_id, nation.getNation_id(), false);
        return new WebAnnouncement(announcement.ann_id, announcement.getParent().type.ordinal(), announcement.active, announcement.getParent().title, announcement.getContent());
    }

    @Command(desc = "Mark all announcements as read")
    @IsMemberIngameOrDiscord
    @ReturnType(WebSuccess.class)
    public WebSuccess mark_all_read(@Me GuildDB db, @Me DBNation nation) {
        db.setAllAnnouncementsActive(nation.getNation_id(), false);
        return success();
    }

    @Command(desc = "Mark a specific announcement as read")
    @IsMemberIngameOrDiscord
    @ReturnType(WebSuccess.class)
    public WebSuccess read_announcement(@Me GuildDB db, @Me DBNation nation, int ann_id) {
        db.setAnnouncementActive(ann_id, nation.getNation_id(), false);
        return success();
    }

    @Command(desc = "Mark a single announcement as unread")
    @RolePermission(Roles.MEMBER)
    @ReturnType(WebSuccess.class)
    public WebSuccess unread_announcement(@Me GuildDB db, @Me DBNation nation, int ann_id) {
        db.setAnnouncementActive(ann_id, nation.getNation_id(), true);
        return success();
    }

    @Command(desc = "Return count of unread announcements", viewable = true)
    @IsMemberIngameOrDiscord
    @ReturnType(WebInt.class)
    public WebInt unread_count(@Me GuildDB db, @Me DBNation nation) {
        return new WebInt(db.getPlayerAnnouncementsByNation(nation.getNation_id(), true).size());
    }


    @Command(desc = "Preview or execute autorole for a guild member")
    @RolePermission(value = {Roles.INTERNAL_AFFAIRS, Roles.INTERNAL_AFFAIRS_STAFF}, alliance = true, any = true)
    @ReturnType(AutoRoleResult.class)
    public AutoRoleResult autorole(@Me GuildDB db, Member member, @Switch("f") boolean force) {
        return AutoRoleService.autorole(db, member, force);
    }

    @Command(desc = "Preview or execute autorole for all guild members")
    @RolePermission(value = {Roles.INTERNAL_AFFAIRS, Roles.INTERNAL_AFFAIRS_STAFF}, alliance = true, any = true)
    @ReturnType(AutoRoleBulkResult.class)
    public AutoRoleBulkResult autoroleall(@Me GuildDB db, @Switch("f") boolean force) {
        return AutoRoleService.autoroleall(db, force);
    }

    @Command(desc = "Retrieve allowed bank accounts and access types", viewable = true)
    @IsMemberIngameOrDiscord
    @ReturnType(WebBankAccess.class)
    public Object bank_access(@Me GuildDB db, @Me DBNation nation, @Me @Default User user) {
        try {
            Map<Long, AccessType> allowed = db.getAllowedBankAccountsOrThrow(nation, user, nation, null, true);
            return new WebBankAccess(allowed);
        } catch (IllegalArgumentException e) {
            return error(e.getMessage());
        }
    }

    @Command(desc = "Withdraw funds from a nation bank account to another entity")
    @HasOffshore
    @IsMemberIngameOrDiscord
    @ReturnType(WebTransferResult.class)
    public Object withdraw(@Me GuildDB db, @Me User banker, @Me DBNation nationAccount, NationOrAlliance receiver, Map<ResourceType, Double> amount, DepositTypeInfo note) throws IOException {
        if (nationAccount == null) {
            return error("Please sign in with a valid nation to withdraw");
        }
        if (receiver == null || amount == null || note == null) {
            return error("Please provide a receiver, amount and note.");
        }
        double[] amtArr = ResourceType.resourcesToArray(amount);
        if (ResourceType.isZero(amtArr)) {
            return error("Please provide a non-zero amount.");
        }
        Map<Long, AccessType> allowed;
        try {
            allowed = db.getAllowedBankAccountsOrThrow(nationAccount, banker, receiver, null, true);
        Supplier<Map<Long, AccessType>> allowedIdsGet = () -> allowed;
        OffshoreInstance offshore = db.getOffshore();
        TransferResult result = offshore.transferFromNationAccountWithRoleChecks(
                allowedIdsGet,
                banker,
                nationAccount,
                null,
                null,
                db,
                null, // senderChannel
                receiver,
                amtArr,
                note,
                null,
                null,
                null,
                false,
                EscrowMode.NEVER,
                false,
                receiver.equals(nationAccount));
            return new WebTransferResult(result);
        } catch (IllegalArgumentException e) {
            return error(e.getMessage());
        }
    }


    @Command(desc = "Get bank balance breakdown for a nation", viewable = true)
    @IsMemberIngameOrDiscord
    @ReturnType(WebBalance.class)
    public WebBalance balance(@Me GuildDB db, @Me DBNation me, @Me @Default User user, @Default DBNation nation) {
        if (nation == null) nation = me;
        else if (nation.getId() != me.getId() && user == null || !Roles.ECON_STAFF.has(user, db.getGuild())) {
            throw new IllegalArgumentException("You can only view your own balance.");
        }
        WebBalance result = new WebBalance();
        result.id = nation.getId();
        result.is_aa = false;

        Map<Long, AccessType> allowed;
        try {
            allowed = db.getAllowedBankAccountsOrThrow(nation, user, nation, null, true);
        } catch (IllegalArgumentException e) {
            allowed = Collections.emptyMap();
            result.no_access_msg = e.getMessage();
        }

        Map<DepositType, double[]> breakdown = nation.getDeposits(null, db, null, true, true, -1, 0, Long.MAX_VALUE, false);
        boolean includeGrants = db.getOrNull(GuildKey.MEMBER_CAN_WITHDRAW_IGNORES_GRANTS) == Boolean.FALSE;
        double[] total = ResourceType.getBuffer();
        for (Map.Entry<DepositType, double[]> entry : breakdown.entrySet()) {
            if (includeGrants || entry.getKey() != DepositType.GRANT) {
                double[] value = entry.getValue();
                total = ResourceType.add(total, value);
            }
        }

        result.total = total;
        result.include_grants = includeGrants;
        result.access = allowed.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, f -> f.getValue().ordinal()));
        result.breakdown = breakdown.entrySet().stream().collect(Collectors.toMap(f -> f.getKey().name(), Map.Entry::getValue));
        return result;
    }
    @Command(desc = "Fetch bank transaction records for a nation", viewable = true)
    @IsMemberIngameOrDiscord
    @ReturnType(WebTable.class)
    public Object records(@Me GuildDB db, @Me @Default User user, @Me DBNation me, @Default DBNation nation) {
        if (nation == null) nation = me;
        else if (nation.getId() != me.getId() && user == null || !Roles.ECON_STAFF.has(user, db.getGuild())) {
            throw new IllegalArgumentException("You can only view your own bank records.");
        }
        List<Transaction2> transactions = BankCommands.getRecords(db, null, true, true, true, 0, Long.MAX_VALUE, nation, false);
        List<List<Object>> cells = SpreadSheet.generateTransactionsListCells(transactions, true, false, true);
        return new WebTable(cells, null, null);
    }

    @Command(desc = "Retrieve summary of nation presently involved in the most wars", viewable = true)
    @IsMemberIngameOrDiscord
    @ReturnType(WebMyWars.class)
    public WebMyWars my_wars(ValueStore store, @Me GuildDB db, @Me DBNation nation) {
        int myWars = nation.getNumWars();
        for (DBNation other : Locutus.imp().getNationDB().getAllNations()) {
            int otherWars = other.getNumWars();
            if (otherWars > myWars) {
                nation = other;
                myWars = otherWars;
                if (myWars == 9) break;
            }
        }
        Set<DBWar> wars = nation.getActiveWars();
        List<DBWar> offensives = new ObjectArrayList<>();
        List<DBWar> defensives = new ObjectArrayList<>();
        boolean isFightingActives = false;
        for (DBWar war : wars) {
            DBNation enemy;
            if (war.getAttacker_id() == nation.getId()) {
                offensives.add(war);
                enemy = war.getNation(false);
            } else {
                defensives.add(war);
                enemy = war.getNation(true);
            }
            isFightingActives |= enemy != null && enemy.active_m() < 1440;
        }
        return new WebMyWars(store, db, nation, offensives, defensives, isFightingActives);
    }

//    public WebTargets war(@Me GuildDB db, @Me DBNation me,
//            @Default("*") Set<DBNation> nations,
//                          @Default("8") int num_results,
//                          @Arg("Include inactive nations in the search\n" +
//                                  "Defaults to false")
//                          @Switch("i") boolean includeInactives,
//                          @Arg("Include applicants in the search\n" +
//                                  "Defaults to false")
//                          @Switch("a") boolean includeApplicants,
//                          @Arg("Only list targets with offensive wars they are winning")
//                          @Switch("p") boolean onlyPriority,
//                          @Arg("Only list targets weaker than you")
//                          @Switch("w") boolean onlyWeak,
//                          @Arg("Sort by easiest targets")
//                          @Switch("e") boolean onlyEasy,
//                          @Arg("Only list targets with less cities than you")
//                          @Switch("c") boolean onlyLessCities,
//                          @Arg("Include nations much stronger than you in the search\n" +
//                                  "Defaults to false")
//                          @Switch("s") boolean includeStrong) {
//        // inactives
//        // applicants
//        // priority
//        // waek
//        // easy
//        // onlylesscities
//        // includestrong
//
//    }
//
//    @Command
//    @IsMemberIngameOrDiscord
//    @ReturnType(WebMyEnemies.class)
//    public WebMyEnemies enemies(@Me GuildDB db) {
//
//    }
}
