package link.locutus.discord.web.commands.api;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.*;
import link.locutus.discord.commands.manager.v2.binding.annotation.*;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.HasOffshore;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.IsMemberIngameOrDiscord;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.commands.manager.v2.impl.pw.commands.BankCommands;
import link.locutus.discord.commands.war.RaidCommand;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.db.entities.Transaction2;
import link.locutus.discord.db.entities.WarStatus;
import link.locutus.discord.db.entities.announce.AnnounceType;
import link.locutus.discord.db.entities.announce.Announcement;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.pnw.NationOrAlliance;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.offshore.OffshoreInstance;
import link.locutus.discord.util.offshore.TransferResult;
import link.locutus.discord.util.sheet.SpreadSheet;
import link.locutus.discord.util.task.ia.IACheckup;
import link.locutus.discord.web.commands.ReturnType;
import link.locutus.discord.web.commands.binding.value_types.*;
import link.locutus.discord.web.commands.page.PageHelper;
import net.dv8tion.jda.api.entities.User;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static link.locutus.discord.commands.manager.v2.impl.pw.commands.WarCommands.getCounterChance;

public class IAEndpoints extends PageHelper {
    @Command
    @IsMemberIngameOrDiscord
    @ReturnType(value = WebAudits.class, cache = CacheType.SessionStorage, duration = 30)
    public Object my_audits(@Me GuildDB db, @Me DBNation nation) throws IOException, ExecutionException, InterruptedException {
        if (!db.isAllianceId(nation.getAlliance_id()) || nation.getPositionEnum().id < Rank.MEMBER.id) {
            return error("You are not a member of the guild's alliance.");
        }
        Map<IACheckup.AuditType, Map.Entry<Object, String>> checkupResult = new HashMap<>();
        IACheckup checkup = new IACheckup(db, db.getAllianceList(), true);
        checkupResult = checkup.checkup(nation, true, true);
        checkupResult.entrySet().removeIf(f -> f.getValue() == null || f.getValue().getValue() == null);


        WebAudits audits = new WebAudits();
        for (Map.Entry<IACheckup.AuditType, Map.Entry<Object, String>> entry : checkupResult.entrySet()) {
            IACheckup.AuditType type = entry.getKey();
            Map.Entry<Object, String> value = entry.getValue();
            audits.values.add(new WebAudit(type.name(), type.getSeverity().ordinal(), value.getKey().toString(), value.getValue()));
        }

        return audits;
    }

    @Command
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

    @Command
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

    @Command
    @IsMemberIngameOrDiscord
    @ReturnType(value = WebAnnouncement.class, cache = CacheType.SessionStorage)
    public Object view_announcement(@Me GuildDB db, @Me DBNation nation, int ann_id) {
        if (!db.isAllianceId(nation.getAlliance_id()) || nation.getPositionEnum().id < Rank.MEMBER.id) {
            return error("You are not a member of the guild's alliance.");
        }
        System.out.println("Viewing announcement " + ann_id);
        Announcement.PlayerAnnouncement announcement = db.getPlayerAnnouncement(ann_id, nation.getNation_id());
        if (announcement == null) {
            return error("Announcement not found.");
        }
        // mark it as read
        db.setAnnouncementActive(ann_id, nation.getNation_id(), false);
        return new WebAnnouncement(announcement.ann_id, announcement.getParent().type.ordinal(), announcement.active, announcement.getParent().title, announcement.getContent());
    }

    @Command
    @IsMemberIngameOrDiscord
    @ReturnType(WebSuccess.class)
    public WebSuccess mark_all_read(@Me GuildDB db, @Me DBNation nation) {
        db.setAllAnnouncementsActive(nation.getNation_id(), false);
        return success();
    }

    @Command
    @IsMemberIngameOrDiscord
    @ReturnType(WebSuccess.class)
    public WebSuccess read_announcement(@Me GuildDB db, @Me DBNation nation, int ann_id) {
        db.setAnnouncementActive(ann_id, nation.getNation_id(), false);
        return success();
    }

    @Command
    @RolePermission(Roles.MEMBER)
    @ReturnType(WebSuccess.class)
    public WebSuccess unread_announcement(@Me GuildDB db, @Me DBNation nation, int ann_id) {
        db.setAnnouncementActive(ann_id, nation.getNation_id(), true);
        return success();
    }

    @Command
    @IsMemberIngameOrDiscord
    @ReturnType(WebInt.class)
    public WebInt unread_count(@Me GuildDB db, @Me DBNation nation) {
        return new WebInt(db.getPlayerAnnouncementsByNation(nation.getNation_id(), true).size());
    }
    @Command
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

    @Command
    @HasOffshore
    @IsMemberIngameOrDiscord
    @ReturnType(WebTransferResult.class)
    public Object withdraw(@Me GuildDB db, @Me User banker, @Me DBNation nationAccount, NationOrAlliance receiver, Map<ResourceType, Double> amount, DepositType.DepositTypeInfo note) throws IOException {
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


    @Command
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

        Map<DepositType, double[]> breakdown = nation.getDeposits(db, null, true, true, -1, 0, false);
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
//
    @Command
    @IsMemberIngameOrDiscord
    @ReturnType(WebTargets.class)
    public WebTargets raid(@Me GuildDB db, @Me DBNation me,
                                    @Default("*,#position<=1") Set<DBNation> nations,
                                    @Default("false") boolean weak_ground,
                                    @Default("0") int vm_turns,
                                    @Default("0") int beige_turns,
                                    @Default("false") boolean ignore_dnr,
                                    @Default("7d") @Timediff long time_inactive,
                                    @Default("-1") double min_loot,
                                    @Default("8") int num_results) throws InterruptedException {
        List<Map.Entry<DBNation, Map.Entry<Double, Double>>> raidResult = RaidCommand.getNations(
                db,
                me,
                nations,
                weak_ground,
                vm_turns,
                -1,
                beige_turns > 0,
                !ignore_dnr,
                Collections.emptySet(),
                false,
                false,
                time_inactive / TimeUnit.MINUTES.toMillis(1),
                me.getScore(),
                min_loot, beige_turns,
                false, false, num_results
        );
        List<WebTarget> targets = new ObjectArrayList<>();
        for (Map.Entry<DBNation, Map.Entry<Double, Double>> entry : raidResult) {
            DBNation nation = entry.getKey();
            double expected = entry.getValue().getKey();
            double loot = entry.getValue().getValue();
            targets.add(new WebTarget(nation, expected, loot, 0));
        }
        WebTargets result = targets(me, targets);
        return result;
    }

    @Command
    @IsMemberIngameOrDiscord
    @ReturnType(WebTargets.class)
    public WebTargets unprotected(@Me GuildDB db, @Me DBNation me,
                           @Default("*") Set<DBNation> nations,
                           @Switch("a") boolean includeAllies,
                           @Switch("o") boolean ignoreODP,
                           @Default("false") boolean ignore_dnr,
                           @Arg("The maximum allowed military strength of the target nation relative to you")
                           @Switch("s") @Default("1.2") Double maxRelativeTargetStrength,
                           @Arg("The maximum allowed military strength of counters relative to you")
                           @Switch("c") @Default("1.2") Double maxRelativeCounterStrength,
                           @Arg("Only list targets within range of ALL attackers")
                           @Default("8") int num_results) throws InterruptedException {
        Set<DBNation> nationsToBlitzWith = Set.of(me);
        List<Map.Entry<DBNation, Double>> counterChance = getCounterChance(db, nations, num_results, ignore_dnr, includeAllies, Set.of(me), maxRelativeTargetStrength, maxRelativeCounterStrength, false, ignoreODP, true);
        double myStrength = nationsToBlitzWith.stream().mapToDouble(f -> Math.pow(f.getStrength(), 3)).sum();

        if (counterChance.size() > num_results) {
            counterChance = counterChance.subList(0, num_results);
        }
        List<WebTarget> targets = new ObjectArrayList<>();
        for (Map.Entry<DBNation, Double> entry : counterChance) {
            DBNation nation = entry.getKey();
            double strength = entry.getValue();
            double loot = nation.lootTotal();
            targets.add(new WebTarget(nation, loot, loot, 100 * strength / myStrength));
        }
        WebTargets result = targets(me, targets);
        result.include_strength = true;
        return result;
    }

    public WebTargets targets(DBNation self, List<WebTarget> targets) {
        WebTargets result = new WebTargets(self);
        result.targets = targets;
        return result;
    }

    @Command
    @IsMemberIngameOrDiscord
    @ReturnType(WebTable.class)
    public Object records(@Me GuildDB db, @Me @Default User user, @Me DBNation me, @Default DBNation nation) {
        if (nation == null) nation = me;
        else if (nation.getId() != me.getId() && user == null || !Roles.ECON_STAFF.has(user, db.getGuild())) {
            throw new IllegalArgumentException("You can only view your own bank records.");
        }
        List<Transaction2> transactions = BankCommands.getRecords(db, null, true, true, 0, nation, false);
        List<List<Object>> cells = SpreadSheet.generateTransactionsListCells(transactions, true, false);
        return new WebTable(cells, null, null);
    }

    @Command
    @IsMemberIngameOrDiscord
    @ReturnType(WebMyWars.class)
    public WebMyWars my_wars(@Me GuildDB db, @Me DBNation nation) {
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
        return new WebMyWars(db, nation, offensives, defensives, isFightingActives);
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
