package com.boydti.discord.web.commands;

import com.boydti.discord.Locutus;
import com.boydti.discord.commands.manager.v2.binding.annotation.Command;
import com.boydti.discord.commands.manager.v2.binding.annotation.Me;
import com.boydti.discord.commands.manager.v2.binding.annotation.Switch;
import com.boydti.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import com.boydti.discord.db.GuildDB;
import com.boydti.discord.db.entities.Transaction2;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.user.Roles;
import com.boydti.discord.util.MarkupUtil;
import com.boydti.discord.util.MathMan;
import com.boydti.discord.util.discord.DiscordUtil;
import com.boydti.discord.util.math.ArrayUtil;
import com.boydti.discord.apiv1.enums.DepositType;
import com.boydti.discord.apiv1.enums.ResourceType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.boydti.discord.db.GuildDB.Key.ALLIANCE_ID;
import static com.boydti.discord.util.PnwUtil.convertedTotal;
import static com.boydti.discord.apiv1.enums.DepositType.DEPOSITS;
import static com.boydti.discord.apiv1.enums.DepositType.GRANT;
import static com.boydti.discord.apiv1.enums.DepositType.LOAN;
import static com.boydti.discord.apiv1.enums.DepositType.TAX;

public class BankPages {

//    @Command
//    @RolePermission(Roles.ECON_LOW_GOV)
//    public String listIngameTransfers(NationOrAlliance sender, NationOrAlliance receiver) {
//
//
//
//        return views.basictable.template("Deposits", header, rows).render().toString();
//    }

    @Command
    @RolePermission(Roles.ECON)
    public Object memberDeposits(@Me Guild guild, @Me GuildDB db, @Me DBNation nation2, @Me User author, @Switch('f') boolean force, @Switch('b') boolean noTaxBase, @Switch('o') boolean ignoreOffset) {
        if (true) return nation2 + "<br><br>" + author + "<br><br>" + guild;
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

        List<DBNation> nations;

        Integer allianceId = db.getOrNull(ALLIANCE_ID);
        if (allianceId != null) {
            nations = Locutus.imp().getNationDB().getNations(Collections.singleton(allianceId));
            nations.removeIf(n -> n.getPosition() <= 1);
        } else {
            Role role = Roles.MEMBER.toRole(guild);
            if (role == null) throw new IllegalArgumentException("No `!KeyStore ALLIANCE_ID` set, or `!aliasRole MEMBER` set");
            nations = new ArrayList<>();
            for (Member member : guild.getMembersWithRoles(role)) {
                DBNation nation = DiscordUtil.getNation(member.getUser());
                nations.add(nation);
            }
            if (nations.isEmpty()) return "No members found";
        }

        double[] buffer = ResourceType.getBuffer();

        List<List<Object>> rows = new ArrayList<>();
        for (DBNation nation : nations) {
            long start2 = System.currentTimeMillis();

            List<Object> row = new ArrayList<>(header);
            long start = System.currentTimeMillis();
            Map<DepositType, double[]> deposits = nation.getDeposits(db, tracked, !noTaxBase, !ignoreOffset, -1, 0L);
            long diff = System.currentTimeMillis() - start;

            row.set(0, MarkupUtil.htmlUrl(nation.getNation(), nation.getNationUrl()));
            row.set(1, nation.getCities());
            row.set(2, nation.getAgeDays());
            row.set(3, MathMan.format(convertedTotal(deposits.getOrDefault(DEPOSITS, buffer))));
            row.set(4, MathMan.format(convertedTotal(deposits.getOrDefault(TAX, buffer))));
            row.set(5, MathMan.format(convertedTotal(deposits.getOrDefault(LOAN, buffer))));
            row.set(6, MathMan.format(convertedTotal(deposits.getOrDefault(GRANT, buffer))));

            double[] total = ResourceType.getBuffer();
            for (double[] value : deposits.values()) total = ArrayUtil.apply(ArrayUtil.DOUBLE_ADD, total, value);
            row.set(7, MathMan.format(convertedTotal(total)));

            List<Transaction2> transactions = nation.getTransactions(-1);

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
                row.set(8, days);
            }
            int i = 9;
            for (ResourceType type : ResourceType.values) {
                if (type == ResourceType.CREDITS) continue;
                row.set((i++), MathMan.format(total[type.ordinal()]));
            }


            rows.add(row);
        }

        return views.basictable.template("Deposits", header, rows).render().toString();
    }

    @Command
    @RolePermission(Roles.MEMBER)
    public Object bankIndex(@Me GuildDB db, @Me DBNation me, @Me User author) {
        return views.bank.bankindex.template(db, db.getGuild(), author).render().toString();
    }
}
