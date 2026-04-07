package link.locutus.discord.web.commands.api;

import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.annotation.Switch;
import link.locutus.discord.commands.manager.v2.binding.annotation.Timestamp;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.IsAlliance;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.pnw.NationList;
import link.locutus.discord.user.Roles;
import link.locutus.discord.web.commands.ReturnType;
import link.locutus.discord.web.commands.binding.value_types.TaxExpenseBracketRows;
import link.locutus.discord.web.commands.binding.value_types.TaxExpenseNation;
import link.locutus.discord.web.commands.binding.value_types.TaxExpenseTime;
import link.locutus.discord.web.commands.binding.value_types.TaxExpenseTimeBracket;
import link.locutus.discord.web.commands.binding.value_types.TaxExpenseTimeResources;
import link.locutus.discord.web.commands.binding.value_types.TaxExpenses;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.json.JSONObject;

public class TaxEndpoints {
    private static long resolveEnd(@Nullable Long end) {
        return end == null ? Long.MAX_VALUE : end;
    }

    @Command(desc = "Show cumulative tax expenses over a period by nation/bracket", viewable = true)
    @IsAlliance
    @RolePermission(Roles.ECON_STAFF)
    @ReturnType(TaxExpenses.class)
    public TaxExpenses tax_expense(@Me GuildDB db,
                                   @Me JSONObject command,
                                   @Timestamp long start,
                                   @Default @Timestamp Long end,
                                   @Switch("n") NationList nationList,
                                   @Switch("g") boolean dontRequireGrant,
                                   @Switch("t") boolean dontRequireTagged,
                                   @Switch("e") boolean dontRequireExpiry,
                                   @Switch("d") boolean includeDeposits) throws Exception {
        return TaxExpenseDatasets.getSummaryDataset(
                db,
                command,
                start,
                resolveEnd(end),
                nationList,
                dontRequireGrant,
                dontRequireTagged,
                dontRequireExpiry,
                includeDeposits
        ).toResponse();
    }

    @Command(desc = "Get dedicated bracket rows for a cached tax expense section", viewable = true)
    @IsAlliance
    @RolePermission(Roles.ECON_STAFF)
    @ReturnType(TaxExpenseBracketRows.class)
    public TaxExpenseBracketRows tax_expense_bracket_rows(@Me GuildDB db,
                                                          int datasetId,
                                                          int taxId) throws Exception {
        return TaxExpenseDatasets.requireSummaryDataset(db, datasetId).getBracketRows(taxId);
    }

    @Command(desc = "Get merged detail and transaction data for a cached tax expense nation", viewable = true)
    @IsAlliance
    @RolePermission(Roles.ECON_STAFF)
    @ReturnType(TaxExpenseNation.class)
    public TaxExpenseNation tax_expense_nation(@Me GuildDB db,
                                               int datasetId,
                                               int taxId,
                                               int nation) throws Exception {
        return TaxExpenseDatasets.requireSummaryDataset(db, datasetId).getNation(taxId, nation);
    }

    @Command(desc = "Get total tax expense resource series for a cached by-time dataset", viewable = true)
    @IsAlliance
    @RolePermission(Roles.ECON_STAFF)
    @ReturnType(TaxExpenseTimeResources.class)
    public TaxExpenseTimeResources tax_expense_by_time_resources(@Me GuildDB db,
                                                                 int datasetId) throws Exception {
        return TaxExpenseDatasets.requireTimeDataset(db, datasetId).getResources();
    }

    @Command(desc = "Get a cached by-time chart series for a single tax expense bracket", viewable = true)
    @IsAlliance
    @RolePermission(Roles.ECON_STAFF)
    @ReturnType(TaxExpenseTimeBracket.class)
    public TaxExpenseTimeBracket tax_expense_by_time_bracket(@Me GuildDB db,
                                                             int datasetId,
                                                             int taxId) throws Exception {
        return TaxExpenseDatasets.requireTimeDataset(db, datasetId).getBracket(taxId);
    }

    @Command(desc = "Show running tax expenses by turn by bracket", viewable = true)
    @IsAlliance
    @RolePermission(Roles.ECON_STAFF)
    @ReturnType(TaxExpenseTime.class)
    public TaxExpenseTime tax_expense_by_time(@Me GuildDB db,
                                              @Me JSONObject command,
                                              @Default Integer datasetId,
                                              @Default @Timestamp Long start,
                                              @Default @Timestamp Long end,
                                              @Default NationList nationFilter,
                                              @Switch("g") boolean dontRequireGrant,
                                              @Switch("t") boolean dontRequireTagged,
                                              @Switch("e") boolean dontRequireExpiry,
                                              @Switch("d") boolean includeDeposits) throws Exception {
        if (datasetId != null) {
            try {
                return TaxExpenseDatasets.requireTimeDataset(db, datasetId).toResponse();
            } catch (IllegalArgumentException ignored) {
                if (start == null) {
                    throw ignored;
                }
            }
        }
        if (start == null) {
            throw new IllegalArgumentException("start is required when datasetId is not provided");
        }
        return TaxExpenseDatasets.getTimeDataset(
                db,
                command,
                start,
                resolveEnd(end),
                nationFilter,
                dontRequireGrant,
                dontRequireTagged,
                dontRequireExpiry,
                includeDeposits
        ).toResponse();
    }
}
