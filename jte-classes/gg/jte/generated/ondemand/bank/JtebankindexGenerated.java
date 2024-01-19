package gg.jte.generated.ondemand.bank;
import link.locutus.discord.commands.manager.v2.binding.WebStore;
import link.locutus.discord.user.Roles;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.guild.GuildKey;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
public final class JtebankindexGenerated {
	public static final String JTE_NAME = "bank/bankindex.jte";
	public static final int[] JTE_LINE_INFO = {0,0,1,2,3,4,5,7,7,7,12,12,12,12,14,14,15,15,16,16,17,17,19,19,21,21,22,22,23,23,24,24,24,24,24,7,8,9,10,10,10,10};
	public static void render(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, WebStore ws, GuildDB db, Guild guild, User user) {
		jteOutput.writeContent("\r\n");
		gg.jte.generated.ondemand.JtemainGenerated.render(jteOutput, jteHtmlInterceptor, ws, new gg.jte.Content() {
			public void writeTo(gg.jte.TemplateOutput jteOutput) {
				jteOutput.writeContent("\r\n    <h2>Member Pages</h2>\r\n    <a class=\"m-1 btn btn-primary btn-lg\" href=\"");
				jteOutput.writeUserContent(db.getGuild().getIdLong());
				jteOutput.writeContent("/deposits\">View Your Deposits</a>\r\n    ");
				if (db.getOrNull(GuildKey.MEMBER_CAN_WITHDRAW) == Boolean.TRUE && Roles.ECON_WITHDRAW_SELF.has(user, guild)) {
					jteOutput.writeContent("\r\n        <a class=\"m-1 btn btn-primary btn-lg\" href=\"/");
					jteOutput.writeUserContent(db.getGuild().getIdLong());
					jteOutput.writeContent("/withdrawaa\">Withdraw your deposits</a>\r\n    ");
				}
				jteOutput.writeContent("\r\n\r\n    ");
				if (Roles.ECON.has(user, db.getGuild())) {
					jteOutput.writeContent("\r\n        <h2>Econ Gov Pages</h2>\r\n        <a class=\"m-1 btn btn-primary btn-lg\" href=\"/");
					jteOutput.writeUserContent(db.getGuild().getIdLong());
					jteOutput.writeContent("/memberdeposits\">View Member Deposits</a>\r\n        <a class=\"m-1 btn btn-primary btn-lg\" href=\"/");
					jteOutput.writeUserContent(db.getGuild().getIdLong());
					jteOutput.writeContent("/withdrawaa\">Send From Offshore</a>\r\n    ");
				}
				jteOutput.writeContent("\r\n");
			}
		}, db.getGuild().getName() + " Bank Index", null);
	}
	public static void renderMap(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, java.util.Map<String, Object> params) {
		WebStore ws = (WebStore)params.get("ws");
		GuildDB db = (GuildDB)params.get("db");
		Guild guild = (Guild)params.get("guild");
		User user = (User)params.get("user");
		render(jteOutput, jteHtmlInterceptor, ws, db, guild, user);
	}
}
