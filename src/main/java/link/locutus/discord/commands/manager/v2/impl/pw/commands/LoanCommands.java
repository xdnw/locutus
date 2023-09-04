package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import link.locutus.discord.commands.manager.v2.binding.annotation.Binding;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.annotation.Timediff;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.config.Messages;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBLoan;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.apiv1.enums.ResourceType;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.MessageEmbed;
import rocker.guild.ia.message;

import java.util.Map;

public class LoanCommands {

    /*
Loan amount (money, or resources)
time_period
purpose
collateral
cosigner
additional_notes

A note the user wants to add to the request
     */
    @Command
    public String requestInfo(@Me IMessageIO io, @Me GuildDB db, @Me User user, @Me DBNation nation) {
        String msg = Messages.LOAN_REQUEST + "\n\nTo open a request, use: TODO CM REF;

    }


//    @Binding
//    @RolePermission(value= Roles.ECON)
//    public String create(@Me GuildDB db, @Me Guild guild, @Me IMessageIO channel, DBNation nation, Map<ResourceType, Double> resources, @Timediff long time) {
//        String title = "Generating loan for " + nation;
//        StringBuilder body = new StringBuilder();
//        body.append("Please wait...");
//
//        DiscordUtil.createEmbedCommand(channel, title, body.toString());
//        long due = System.currentTimeMillis() + time;
//
//        DBLoan loan = new DBLoan(-1, guild.getIdLong(), message.getIdLong(), nation.getNation_id(), PnwUtil.resourcesToArray(resources), due, DBLoan.Status.OPEN);
//        db.addLoan(loan);
//
//        loan = db.getLoanByMessageId(message.getIdLong());
//        if (loan == null) {
//            return "Error: Failed to generate loan";
//        }
//        MessageEmbed embed = message.getEmbeds().get(0);
//        EmbedBuilder builder = new EmbedBuilder(embed);
////        updateLoan(loan, builder, event.getChannel(), message.getIdLong());
//
//        return null;
//    }
}
