package link.locutus.discord.web.commands;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.annotation.Switch;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.config.Settings;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.Transaction2;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MarkupUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.math.ArrayUtil;
import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.util.PnwUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static link.locutus.discord.util.PnwUtil.convertedTotal;

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

        Collection<DBNation> nations;

        Integer allianceId = db.getOrNull(GuildDB.Key.ALLIANCE_ID);
        if (allianceId != null) {
            nations = Locutus.imp().getNationDB().getNations(Collections.singleton(allianceId));
            nations.removeIf(n -> n.getPosition() <= 1);
        } else {
            Role role = Roles.MEMBER.toRole(guild);
            if (role == null) throw new IllegalArgumentException("No `" + Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "KeyStore ALLIANCE_ID` set, or `" + Settings.INSTANCE.DISCORD.COMMAND.LEGACY_COMMAND_PREFIX + "aliasRole MEMBER` set");
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
            row.set(3, MathMan.format(PnwUtil.convertedTotal(deposits.getOrDefault(DepositType.DEPOSITS, buffer))));
            row.set(4, MathMan.format(PnwUtil.convertedTotal(deposits.getOrDefault(DepositType.TAX, buffer))));
            row.set(5, MathMan.format(PnwUtil.convertedTotal(deposits.getOrDefault(DepositType.LOAN, buffer))));
            row.set(6, MathMan.format(PnwUtil.convertedTotal(deposits.getOrDefault(DepositType.GRANT, buffer))));

            double[] total = ResourceType.getBuffer();
            for (double[] value : deposits.values()) total = ArrayUtil.apply(ArrayUtil.DOUBLE_ADD, total, value);
            row.set(7, MathMan.format(PnwUtil.convertedTotal(total)));

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
