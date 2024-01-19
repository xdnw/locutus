package gg.jte.generated.ondemand.guild.econ;
import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.WebStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.annotation.Switch;
import link.locutus.discord.commands.manager.v2.binding.annotation.Timestamp;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.IsAlliance;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.WhitelistPermission;
import link.locutus.discord.db.BankDB;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.Coalition;
import link.locutus.discord.db.entities.TaxBracket;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.db.entities.Transaction2;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.math.ArrayUtil;
import link.locutus.discord.util.offshore.Auth;
import link.locutus.discord.util.offshore.test.IACategory;
import link.locutus.discord.apiv1.enums.ResourceType;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.dv8tion.jda.api.entities.Guild;
import java.util.List;
import java.util.Map;
import link.locutus.discord.apiv1.enums.ResourceType;
import java.util.UUID;
public final class JtetaxexpensesbreakdownGenerated {
	public static final String JTE_NAME = "guild/econ/taxexpensesbreakdown.jte";
	public static final int[] JTE_LINE_INFO = {0,0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,32,32,32,38,38,38,39,39,40,40,42,42,44,44,44,44,48,48,48,48,49,49,51,51,52,52,53,53,54,54,55,55,60,60,60,60,32,33,34,35,36,36,36,36};
	public static void render(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, WebStore ws, Object id, double[] a, double[] b, boolean showByResource) {
		jteOutput.writeContent("\r\n");
		gg.jte.generated.ondemand.guild.econ.JteincomeexpensebarGenerated.render(jteOutput, jteHtmlInterceptor, ws, "Monetary Income", "Monetary Expense", PnwUtil.convertedTotal(a), PnwUtil.convertedTotal(b));
		jteOutput.writeContent("\r\n");
		if (showByResource) {
			jteOutput.writeContent("\r\n    <div class=\"accordion\" id=\"Accordion");
			jteOutput.writeUserContent(id + "");
			jteOutput.writeContent("\">\r\n        <div class=\"accordion-item\">\r\n            <h2 class=\"accordion-header\" id=\"heading");
			jteOutput.writeUserContent(id + "");
			jteOutput.writeContent("\">\r\n                <button class=\"accordion-button collapsed p-1 btn-sm\" type=\"button\" data-bs-toggle=\"collapse\"\r\n                        data-bs-target=\"#collapse");
			jteOutput.writeUserContent(id + "");
			jteOutput.writeContent("\" aria-expanded=\"false\" aria-controls=\"collapse");
			jteOutput.writeUserContent(id + "");
			jteOutput.writeContent("\">\r\n                    Show by resource\r\n                </button>\r\n            </h2>\r\n            <div id=\"collapse");
			jteOutput.writeUserContent(id + "");
			jteOutput.writeContent("\" class=\"accordion-collapse collapse\" aria-labelledby=\"heading");
			jteOutput.writeUserContent(id + "");
			jteOutput.writeContent("\"\r\n                 data-bs-parent=\"#Accordion");
			jteOutput.writeUserContent(id + "");
			jteOutput.writeContent("\">\r\n                <div class=\"accordion-body bg-light\">\r\n                    ");
			for (ResourceType type : ResourceType.values) {
				jteOutput.writeContent("\r\n                        ");
				if (a[type.ordinal()] != 0 || b[type.ordinal()] != 0) {
					jteOutput.writeContent("\r\n                            ");
					gg.jte.generated.ondemand.guild.econ.JteincomeexpensebarGenerated.render(jteOutput, jteHtmlInterceptor, ws, type + " income", type + " expense", a[type.ordinal()], b[type.ordinal()]);
					jteOutput.writeContent("\r\n                        ");
				}
				jteOutput.writeContent("\r\n                    ");
			}
			jteOutput.writeContent("\r\n                </div>\r\n            </div>\r\n        </div>\r\n    </div>\r\n");
		}
	}
	public static void renderMap(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, java.util.Map<String, Object> params) {
		WebStore ws = (WebStore)params.get("ws");
		Object id = (Object)params.get("id");
		double[] a = (double[])params.get("a");
		double[] b = (double[])params.get("b");
		boolean showByResource = (boolean)params.get("showByResource");
		render(jteOutput, jteHtmlInterceptor, ws, id, a, b, showByResource);
	}
}
