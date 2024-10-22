package link.locutus.discord.web.commands.page;

import gg.jte.generated.precompiled.JtebasictableGenerated;
import gg.jte.generated.precompiled.bank.JtebankindexGenerated;
import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.WebStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.annotation.Switch;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.Transaction2;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.math.ArrayUtil;
import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.apiv1.enums.ResourceType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static link.locutus.discord.apiv1.enums.ResourceType.convertedTotal;

public class BankPages {

//    @Command
//    @RolePermission(Roles.ECON_STAFF)
//    public String listIngameTransfers(NationOrAlliance sender, NationOrAlliance receiver) {
//
//
//
//        return WebStore.render(f -> JtebasictableGenerated.render(f, null, ws, "Deposits", header, rows));
//    }

    @Command
    @RolePermission(Roles.ECON)
    public Object memberDeposits(WebStore ws, @Me Guild guild, @Me GuildDB db, @Me DBNation nation2, @Me User author, @Switch("f") boolean force, @Switch("b") boolean noTaxBase, @Switch("o") boolean ignoreOffset) {
        Set<Long> tracked = db.getTrackedBanks();

        List<String> header = new ArrayList<>(Arrays.asList(
                "nation",
                "cities",
                "age",
                "deposit",
                "tax",
                "loan",
                "grant",
                "total",
                "last_deposit_day"
        ));

        for (ResourceType type : ResourceType.values()) {
            if (type == ResourceType.CREDITS) continue;
            header.add(type.name());
        }

        Collection<DBNation> nations;

        if (db.hasAlliance()) {
            nations = Locutus.imp().getNationDB().getNationsByAlliance(db.getAllianceIds());
            nations.removeIf(n -> n.getPosition() <= 1);
        } else {
            Set<Member> members = Roles.MEMBER.getAll(db);
            if (members.isEmpty() && Roles.MEMBER.toRoles(db).isEmpty()) {
                throw new IllegalArgumentException("No " + GuildKey.ALLIANCE_ID.getCommandMention() + " set, or " + CM.role.setAlias.cmd.locutusRole(Roles.MEMBER.name()).discordRole("") + " set");
            }
            nations = new ArrayList<>();
            for (Member member : members) {
                DBNation nation = DiscordUtil.getNation(member.getUser());
                nations.add(nation);
            }
            if (nations.isEmpty()) return "No members found";
        }

        double[] buffer = ResourceType.getBuffer();

        List<List<String>> rows = new ArrayList<>();
        for (DBNation nation : nations) {
            List<String> row = new ArrayList<>(header);
            Map<DepositType, double[]> deposits = nation.getDeposits(db, tracked, !noTaxBase, !ignoreOffset, -1, 0L, true);

            row.set(0, MarkupUtil.htmlUrl(nation.getNation(), nation.getUrl()));
            row.set(1, MathMan.format(nation.getCities()));
            row.set(2, MathMan.format(nation.getAgeDays()));
            row.set(3, MathMan.format(ResourceType.convertedTotal(deposits.getOrDefault(DepositType.DEPOSIT, buffer))));
            row.set(4, MathMan.format(ResourceType.convertedTotal(deposits.getOrDefault(DepositType.TAX, buffer))));
            row.set(5, MathMan.format(ResourceType.convertedTotal(deposits.getOrDefault(DepositType.LOAN, buffer))));
            row.set(6, MathMan.format(ResourceType.convertedTotal(deposits.getOrDefault(DepositType.GRANT, buffer))));

            double[] total = ResourceType.getBuffer();
            for (double[] value : deposits.values()) total = ArrayUtil.apply(ArrayUtil.DOUBLE_ADD, total, value);
            row.set(7, MathMan.format(ResourceType.convertedTotal(total)));

            List<Transaction2> transactions = nation.getTransactions(-1, true);

            long lastDeposit = 0;
            for (Transaction2 transaction : transactions) {
                if (transaction.sender_id == nation.getNation_id()) {
                    lastDeposit = Math.max(transaction.tx_datetime, lastDeposit);
                }
            }
            if (lastDeposit == 0) {
                row.set(8, "NEVER");
            } else {
                long days = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - lastDeposit);
                row.set(8, MathMan.format(days));
            }
            int i = 9;
            for (ResourceType type : ResourceType.values) {
                if (type == ResourceType.CREDITS) continue;
                row.set((i++), MathMan.format(total[type.ordinal()]));
            }


            rows.add(row);
        }

        return WebStore.render(f -> JtebasictableGenerated.render(f, null, ws, "Deposits", header, ws.tableUnsafe(rows)));
    }

    @Command
    @RolePermission(Roles.MEMBER)
    public Object bankIndex(WebStore ws, @Me GuildDB db, @Me DBNation me, @Me User author) {
        return WebStore.render(f -> JtebankindexGenerated.render(f, null, ws, db, db.getGuild(), author));
    }
}
