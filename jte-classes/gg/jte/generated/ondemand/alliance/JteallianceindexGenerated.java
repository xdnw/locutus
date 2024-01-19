package gg.jte.generated.ondemand.alliance;
import link.locutus.discord.commands.manager.v2.binding.WebStore;
import link.locutus.discord.user.Roles;
import link.locutus.discord.db.GuildDB;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import link.locutus.discord.db.entities.DBAlliance;
public final class JteallianceindexGenerated {
	public static final String JTE_NAME = "alliance/allianceindex.jte";
	public static final int[] JTE_LINE_INFO = {0,0,1,2,3,4,5,7,7,7,13,13,13,13,14,14,15,15,16,16,16,16,16,7,8,9,10,11,11,11,11};
	public static void render(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, WebStore ws, GuildDB db, Guild guild, DBAlliance alliance, User user) {
		jteOutput.writeContent("\r\n");
		gg.jte.generated.ondemand.JtemainGenerated.render(jteOutput, jteHtmlInterceptor, ws, new gg.jte.Content() {
			public void writeTo(gg.jte.TemplateOutput jteOutput) {
				jteOutput.writeContent("\r\n    <a class=\"m-1 btn btn-primary btn-lg\" href=\"allianceleaves/");
				jteOutput.writeUserContent(alliance.getId());
				jteOutput.writeContent("\">View Rank Changes</a>\r\n    <a class=\"m-1 btn btn-primary btn-lg\" href=\"alliancewars/");
				jteOutput.writeUserContent(alliance.getId());
				jteOutput.writeContent("\">View Rank Changes</a>\r\n");
			}
		}, alliance.getName() + " Alliance Index", null);
	}
	public static void renderMap(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, java.util.Map<String, Object> params) {
		WebStore ws = (WebStore)params.get("ws");
		GuildDB db = (GuildDB)params.get("db");
		Guild guild = (Guild)params.get("guild");
		DBAlliance alliance = (DBAlliance)params.get("alliance");
		User user = (User)params.get("user");
		render(jteOutput, jteHtmlInterceptor, ws, db, guild, alliance, user);
	}
}
