package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import link.locutus.discord.commands.manager.v2.binding.annotation.*;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.WhitelistPermission;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.Coalition;
import link.locutus.discord.db.entities.MMRDouble;
import link.locutus.discord.db.entities.MMRMatcher;
import link.locutus.discord.pnw.NationList;
import link.locutus.discord.util.offshore.Grant;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.AlertUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.offshore.OffshoreInstance;
import link.locutus.discord.web.jooby.WebRoot;
import link.locutus.discord.apiv1.enums.ResourceType;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.User;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class GrantCommands {

    // Units
    // mmr
    // city
    // build
    // infra
    // land
    // project
    // resources
    // warchest

    /*
    "Add `-i` to build grants to exclude infra cost\n" +
                "Add `-l` to build grants to exclude land cost\n" +
                "Add `-e` or `#expire=60d` to have a grant's debt expire\n" +
                "Add `-c` to have a grant count as cash value in `" + Settings.commandPrefix(true) + "deposits`\n" +
                "Add `-o` to only send what funds they are missing for a grant\n" +
                "Add `-m` to multiply the grant per city";
     */

    @Command(desc = "Calculate and send funds for military units")
    @RolePermission(Roles.MEMBER)
    public String unit(@Me GuildDB db, @Me User author, @Me DBNation me, Set<DBNation> nations, MilitaryUnit unit, @Switch("q") Integer quantity,
                       @Switch("p") @Range(min =0, max=100) Double percent,
                       @Switch("a") double sendAttackConsumption,
                       @Switch("b") boolean capAtUnitLimit,
                       @Switch("s") boolean capAtSingleCityLimit,
                       @Switch("u") boolean capAtMMREquals5553,
                       @Switch("c") boolean multiplyPerCity,
                       @Switch("m") boolean onlySendMissingUnits,
                       @Switch("o") boolean onlySendMissingFunds,
                       @Switch("e") @Timestamp Long expire,
                       @Switch("n") @Default("#grant") String note) {
        if ((quantity == null) == (percent == null)) throw new IllegalArgumentException("Please specify `percent` OR `quantity`, not both");
        Map<DBNation, double[]> amountsToSend = new HashMap<>();

        for (DBNation nation : nations) {
            Double natQuantity = (double) quantity;
            if (percent != null) {
                natQuantity = unit.getBuilding().max() * unit.getBuilding().cap(nation::hasProject) * percent / 100d;
            }
            if (multiplyPerCity) {
                natQuantity *= nation.getCities();
            }
            if (capAtUnitLimit) {
                int cap = unit.getCap(nation, false);
                natQuantity = Math.min(natQuantity, cap);
            }
            if (capAtSingleCityLimit) {
                int cap = unit.getBuilding().max() * unit.getBuilding().cap(nation::hasProject);
                natQuantity = Math.min(natQuantity, cap);
            }
            if (capAtMMREquals5553) {
                int cap = unit.getBuilding().max() * unit.getBuilding().cap(nation::hasProject) * nation.getCities();
                natQuantity = Math.min(natQuantity, cap);
            }

            int unitsToSend = (int) (onlySendMissingUnits ? Math.max(natQuantity - nation.getUnits(unit), 0) : natQuantity);

            double[] fundsToSend = unit.getCost(unitsToSend);
            if (sendAttackConsumption > 0) {
                fundsToSend = ResourceType.add(fundsToSend, PnwUtil.multiply(unit.getConsumption(), sendAttackConsumption));
            }

            if (ResourceType.isEmpty(fundsToSend)) continue;

            amountsToSend.put(nation, fundsToSend);
        }

        return send(db, author, me, amountsToSend, onlySendMissingFunds, expire, note);
    }

    public String send(@Me GuildDB db, @Me User author, @Me DBNation me, Map<DBNation, double[]> fundsToSend, boolean onlyMissingFunds, Long expire, String note) {
        // if no econ perms, only 1 nation, and has to be self
        //
        return "TODO";
    }

//    @Command
//    public String mmr(@Me GuildDB db, @Me User author, @Me DBNation me, Set<DBNation> nations,
//                      MMRDouble grantMMR,
//                      @Switch("r") MMRDouble rebuyMMR,
//                      @Switch("b") boolean switchBuildFunds,
//                      @Switch("a") Map<MilitaryUnit, Double> sendConsumptionForAttacks,
//                      @Switch("o") boolean onlySendMissingFunds,
//                      @Switch("e") @Timestamp Long expire,
//                      @Switch("n") String note) {
//        Map<DBNation, double[]> amountsToSend = new HashMap<>();
//        for (DBNation nation : nations) {
//            Map<MilitaryUnit, Integer> units = new HashMap<>();
//
//
//        }
//
//    }
//
//    @Command
//    @RolePermission(Roles.MEMBER)
//    public String resources() {
//
//    }
//
//    @Command
//    @RolePermission(Roles.MEMBER)
//    public String resources() {
//
//    }
//
//    @Command
//    @RolePermission(Roles.MEMBER)
//    public String city() {
//
//    }
//
//    @Command
//    @RolePermission(Roles.MEMBER)
//    public String project() {
//
//    }
//
//    @Command
//    @RolePermission(Roles.MEMBER)
//    public String infra() {
//
//    }
//
//    @Command
//    @RolePermission(Roles.MEMBER)
//    public String land(NationList nations, double landUpTo, @Default CityFilter cities, @Switch("m") boolean onlyMissingFunds, @Switch("e") int expireAfterDays, @Switch("f") boolean bypassChecks) {
//
//    }

    @WhitelistPermission
    @Command
    @RolePermission(value = {Roles.ECON_LOW_GOV, Roles.ECON, Roles.ECON_GRANT_SELF})
    public String approveEscrowed(@Me MessageChannel channel, @Me GuildDB db, @Me DBNation me, @Me User author, DBNation receiver, Map<ResourceType, Double> deposits, Map<ResourceType, Double> escrowed) throws IOException {
        if (true) return "Not implemented";
        /*
        Member: Can only send funds in their deposits
         */

        boolean memberCanApprove = db.getOrNull(GuildDB.Key.MEMBER_CAN_WITHDRAW) == Boolean.TRUE && (db.getCoalition(Coalition.ENEMIES).isEmpty() || db.getOrNull(GuildDB.Key.MEMBER_CAN_WITHDRAW_WARTIME) == Boolean.TRUE);
        boolean checkDepoValue = !Roles.ECON_LOW_GOV.has(author, db.getGuild());
        boolean checkDepoResource = db.getOrNull(GuildDB.Key.RESOURCE_CONVERSION) != Boolean.TRUE;
        boolean allowExtra = Roles.ECON.has(author, db.getGuild());

        double[] expectedDeposits = PnwUtil.resourcesToArray(deposits);
        double[] actualDeposits;
        boolean depositsMatch = true;
        synchronized (OffshoreInstance.BANK_LOCK) {
            actualDeposits = receiver.getNetDeposits(db, false);

            for (int i = 0; i < actualDeposits.length; i++) {
                if (actualDeposits[i] < expectedDeposits[i]) {
                    depositsMatch = false;
                }
            }

            if (depositsMatch) {

            }
        }

        if (!depositsMatch) {
            String title = "[Error] Outdated. Please try again";

            String body = db.generateEscrowedCard(receiver);
            body += "\nCommand run by: " + author.getAsMention();
//            db.getEscrowed()
            String cmd = Settings.commandPrefix(false) + "approveEscrowed " + receiver.getNationUrl() + " " + PnwUtil.resourcesToString(actualDeposits) + " " + PnwUtil.resourcesToString(escrowed);

            String emoji = "\u2705";
            DiscordUtil.createEmbedCommand(channel, title, body, emoji, cmd);
            return null;
        }
        return null;

        // check deposits match provided deposits
    }

    @WhitelistPermission
    @Command
    @RolePermission(Roles.MEMBER)
    public synchronized String grants(@Me GuildDB db, DBNation nation) {
        String baseUrl = WebRoot.REDIRECT + "/" + db.getIdLong() + "/";
        List<String> pages = Arrays.asList(
                baseUrl + "infragrants/" + nation.getNation_id(),
                baseUrl + "landgrants/" + nation.getNation_id(),
                baseUrl + "citygrants/" + nation.getNation_id(),
                baseUrl + "projectgrants/" + nation.getNation_id()
        );
        StringBuilder response = new StringBuilder();
        response.append(nation.toMarkdown() + "\n");
        response.append("color=" + nation.getColor() + "\n");
        response.append("mmr[unit]=" + nation.getMMR() + "\n");
        response.append("mmr[build]=" + nation.getMMRBuildingStr() + "\n");
        response.append("timer[city]=" + nation.getCityTurns() + " timer[project]=" + nation.getProjectTurns() + "\n");
        response.append("slots[project]=" + nation.getNumProjects() + "/" + nation.projectSlots() + "\n");
        response.append("activity[turn]=" + MathMan.format(nation.avg_daily_login_turns() * 100) + "%\n");
        response.append("activity[day]=" + MathMan.format(nation.avg_daily_login() * 100) + "%\n");
        return response + "<" + StringMan.join(pages, ">\n<") + ">";
    }

    private Set<Integer> disabledNations = new HashSet<>();

    @WhitelistPermission
    @Command
    @RolePermission(Roles.ECON_LOW_GOV)
    public synchronized String approveGrant(@Me MessageChannel channel, @Me DBNation banker, @Me Message message, @Me GuildDB db, UUID key, @Switch("f") boolean force) {
        try {
            Grant grant = Grant.getApprovedGrant(db.getIdLong(), key);
            if (grant == null) {
                return "Invalid Token. Please try again";
            }
            DBNation receiver = grant.getNation();

            receiver.updateTransactions();
            receiver.getCityMap(true);

            Set<Grant.Requirement> requirements = grant.getRequirements();
            Set<Grant.Requirement> failed = new HashSet<>();
            Set<Grant.Requirement> override = new HashSet<>();
            for (Grant.Requirement requirement : requirements) {
                Boolean result = requirement.apply(receiver);
                if (!result) {
                    if (requirement.canOverride()) {
                        override.add(requirement);
                    } else {
                        failed.add(requirement);
                    }
                }
            }

            if (!failed.isEmpty()) {
                StringBuilder result = new StringBuilder("Grant could not be approved.\n");
                if (!failed.isEmpty()) {
                    result.append("\nFailed checks:\n - " + StringMan.join(failed.stream().map(f -> f.getMessage()).collect(Collectors.toList()), "\n - ") + "\n");
                }
                if (!override.isEmpty()) {
                    result.append("\nFailed checks that you have permission to bypass:\n - " + StringMan.join(override.stream().map(f -> f.getMessage()).collect(Collectors.toList()), "\n - ") + "\n");
                }
                return result.toString();
            }

            if (!force) {
                String title = grant.title();
                StringBuilder body = new StringBuilder();

                body.append("Receiver: " + receiver.getNationUrlMarkup(true) + " | " + receiver.getAllianceUrlMarkup(true)).append("\n");
                body.append("Note: " + grant.getNote()).append("\n");
                body.append("Amt: " + grant.getAmount()).append("\n");
                body.append("Cost: `" + PnwUtil.resourcesToString(grant.cost())).append("\n\n");

                if (!override.isEmpty()) {
                    body.append("**" + override.size() + " failed checks (you have admin override)**\n - ");
                    body.append(StringMan.join(override.stream().map(f -> f.getMessage()).collect(Collectors.toList()), "\n - ") + "\n\n");
                }

                DiscordUtil.pending(channel, message, title, body.toString(), 'f');
                return null;
            }
            if (disabledNations.contains(receiver.getNation_id())) {
                return "There was an error processing the grant. Please contact an administrator";
            }

            Grant.deleteApprovedGrant(db.getIdLong(), key);

            disabledNations.add(receiver.getNation_id());

            Map.Entry<OffshoreInstance.TransferStatus, String> result = db.getOffshore().transferFromDeposits(banker, db, receiver, grant.cost(), grant.getNote());
            OffshoreInstance.TransferStatus status = result.getKey();

            StringBuilder response = new StringBuilder();
            if (status == OffshoreInstance.TransferStatus.SUCCESS || status == OffshoreInstance.TransferStatus.ALLIANCE_BANK) {
                response.append("**Transaction:** ").append(result.getValue()).append("\n");
                response.append("**Instructions:** ").append(grant.getInstructions());

                Locutus.imp().getExecutor().submit(new Runnable() {
                    @Override
                    public void run() {
                        receiver.updateTransactions();
                        for (Grant.Requirement requirement : grant.getRequirements()) {
                            if (!requirement.apply(receiver)) {
                                disabledNations.remove(receiver.getNation_id());
                                return;
                            }
                        }
                        AlertUtil.error("Grant is still eligable",grant.getType() + " | " + grant.getNote() + " | " + grant.getAmount() + " | " + grant.getTitle());
                    }
                });

            } else {
                if (status != OffshoreInstance.TransferStatus.OTHER) {
                    disabledNations.remove(receiver.getNation_id());
                }
                response.append(status + ": " + result.getValue());
            }


            return response.toString();
        } catch (Throwable e) {
            e.printStackTrace();
            throw e;
        }
    }
}
