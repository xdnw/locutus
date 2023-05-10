package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import link.locutus.discord.commands.manager.v2.binding.annotation.Binding;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.annotation.Timediff;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.binding.annotation.GuildCoalition;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.HasOffshore;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.AllianceLoan;
import link.locutus.discord.db.entities.AllianceLoanManager;
import link.locutus.discord.db.entities.DBLoan;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.apiv1.enums.ResourceType;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.MessageEmbed;
import rocker.guild.ia.message;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LoanCommands {

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

    @Command(desc = "View information about the loan service")
    @RolePermission(value= Roles.ECON)
    @HasOffshore
    public void info(@Me IMessageIO io) {
        // TODO replace with CM
        String title = "**Free Automatic Loans**";
        String body = """
                > This is intended as a FREE automated loan service to help new alliances meet their grant, warchest and rebuild needs
                > Each alliance has an allowance of free loans see: `/offshore_loan allowance`
                > To help expand shared funds, loans with interest are also available
                > Anonymous metadata of your loan will be publicly available, including:
                >   - Loan amount, due date, and status
                
                > For a list of donors to the loan fund, use: `/offshore_loan donor_ranking`
                > To donate to the loan fund, use: `/offshore_loan donate <resources>`
                > 
                > Ready? `/offshore_loan create <resources> <time>`""";
        io.create().embed(title, body).send();
    }

    @Command(desc = "View information about the loan service")
    @RolePermission(value= Roles.ECON)
    @HasOffshore
    public void listGlobal(@Me IMessageIO io, @Me GuildDB db, @Me AllianceLoanManager manager, @Default List<ResourceType> resources) {
    }
}
