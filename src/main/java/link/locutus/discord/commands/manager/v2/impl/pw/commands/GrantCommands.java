package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.annotation.Switch;
import link.locutus.discord.commands.manager.v2.binding.annotation.Timediff;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.WhitelistPermission;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.Coalition;
import link.locutus.discord.pnw.NationList;
import link.locutus.discord.util.offshore.Grant;
import link.locutus.discord.pnw.DBNation;
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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class GrantCommands {

    @Command
    @RolePermission(Roles.MEMBER)
    public String unit() {

    }

    @Command
    @RolePermission(Roles.MEMBER)
    public String resources() {

    }

    @Command
    @RolePermission(Roles.MEMBER)
    public String resources() {

    }

    @Command
    @RolePermission(Roles.MEMBER)
    public String city() {

    }

    @Command
    @RolePermission(Roles.MEMBER)
    public String project() {

    }

    @Command
    @RolePermission(Roles.MEMBER)
    public String infra() {

    }

    @Command
    @RolePermission(Roles.MEMBER)
    public String land(NationList nations, double landUpTo, @Default CityFilter cities, @Switch('m') boolean onlyMissingFunds, @Switch('e') int expireAfterDays, @Switch('f') boolean bypassChecks) {

    }

    @WhitelistPermission
    @Command
    @RolePermission(value = {Roles.ECON_LOW_GOV, Roles.ECON, Roles.ECON_GRANT_SELF})
    public String approveEscrowed(@Me MessageChannel channel, @Me GuildDB db, @Me DBNation me, @Me User author, DBNation receiver, Map<ResourceType, Double> deposits, Map<ResourceType, Double> escrowed) throws IOException {

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
            String cmd = Settings.INSTANCE.DISCORD.COMMAND.COMMAND_PREFIX + "approveEscrowed " + receiver.getNationUrl() + " " + PnwUtil.resourcesToString(actualDeposits) + " " + PnwUtil.resourcesToString(escrowed);

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
        response.append("timer[city]=" + nation.cityTimerTurns() + " timer[project]=" + nation.projectTimerTurns() + "\n");
        response.append("slots[project]=" + nation.getNumProjects() + "/" + nation.projectSlots() + "\n");
        response.append("activity[turn]=" + MathMan.format(nation.avg_daily_login_turns() * 100) + "%\n");
        response.append("activity[day]=" + MathMan.format(nation.avg_daily_login() * 100) + "%\n");
        return response + "<" + StringMan.join(pages, ">\n<") + ">";
    }

    private Set<Integer> disabledNations = new HashSet<>();

    @WhitelistPermission
    @Command
    @RolePermission(Roles.ECON_LOW_GOV)
    public synchronized String approveGrant(@Me MessageChannel channel, @Me DBNation banker, @Me Message message, @Me GuildDB db, UUID key, @Switch('f') boolean force) {
        try {
            Grant grant = Grant.getApprovedGrant(db.getIdLong(), key);
            if (grant == null) {
                return "Invalid Token. Please try again";
            }
            DBNation receiver = grant.getNation();

            receiver.updateProjects();
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
