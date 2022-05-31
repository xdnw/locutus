package com.boydti.discord.commands.manager.v2.impl.pw.commands;

import com.boydti.discord.commands.manager.v2.binding.annotation.Binding;
import com.boydti.discord.commands.manager.v2.binding.annotation.Me;
import com.boydti.discord.commands.manager.v2.binding.annotation.Timediff;
import com.boydti.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import com.boydti.discord.db.GuildDB;
import com.boydti.discord.db.entities.DBLoan;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.user.Roles;
import com.boydti.discord.util.PnwUtil;
import com.boydti.discord.util.discord.DiscordUtil;
import com.boydti.discord.apiv1.enums.ResourceType;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.MessageEmbed;

import java.util.Map;

public class LoanCommands {

    @Binding
    @RolePermission(value= Roles.ECON)
    public String create(@Me GuildDB db, @Me Guild guild, @Me MessageChannel channel, DBNation nation, Map<ResourceType, Double> resources, @Timediff long time) {
        String title = "Generating loan for " + nation;
        StringBuilder body = new StringBuilder();
        body.append("Please wait...");

        Message message = DiscordUtil.createEmbedCommand(channel, title, body.toString());
        long due = System.currentTimeMillis() + time;

        DBLoan loan = new DBLoan(-1, guild.getIdLong(), message.getIdLong(), nation.getNation_id(), PnwUtil.resourcesToArray(resources), due, DBLoan.Status.OPEN);
        db.addLoan(loan);

        loan = db.getLoanByMessageId(message.getIdLong());
        if (loan == null) {
            return "Error: Failed to generate loan";
        }
        MessageEmbed embed = message.getEmbeds().get(0);
        EmbedBuilder builder = new EmbedBuilder(embed);
//        updateLoan(loan, builder, event.getChannel(), message.getIdLong());

        return null;
    }
}
