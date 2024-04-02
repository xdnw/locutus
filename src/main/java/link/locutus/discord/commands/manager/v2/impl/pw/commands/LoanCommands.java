package link.locutus.discord.commands.manager.v2.impl.pw.commands;

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
}
