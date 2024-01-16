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
import java.util.Set;
import java.util.UUID;
import java.util.ArrayList;
import java.util.HashMap;
import link.locutus.discord.db.entities.TaxRecordCategorizer2;
public final class JtetaxexpensesbyturnGenerated {
	public static final String JTE_NAME = "guild/econ/taxexpensesbyturn.jte";
	public static final int[] JTE_LINE_INFO = {0,0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,35,35,35,44,44,44,44,45,45,47,47,49,49,50,50,50,50,50,50,52,52,52,52,53,53,55,55,56,58,60,60,61,61,63,63,64,64,64,64,68,68,68,68,68,68,70,70,71,71,73,76,76,79,79,80,80,85,85,88,88,89,89,89,89,89,35,36,37,38,39,40,41,42,42,42,42};
	public static void render(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, WebStore ws, String title, GuildDB db, long start, long end, TaxRecordCategorizer2 categorized, Map<Integer, Map<TaxRecordCategorizer2.TransactionType, double[][]>> categorizedByTurnByBracket, Map<Integer, TaxBracket> brackets) {
		jteOutput.writeContent("\r\n");
		gg.jte.generated.ondemand.JtemainGenerated.render(jteOutput, jteHtmlInterceptor, ws, new gg.jte.Content() {
			public void writeTo(gg.jte.TemplateOutput jteOutput) {
				jteOutput.writeContent("\r\n");
				for (Map.Entry<Integer, Map<TaxRecordCategorizer2.TransactionType, double[][]>> entry : categorizedByTurnByBracket.entrySet()) {
					jteOutput.writeContent("\r\n<div class=\"bg-white container mt-3 rounded shadow py-1\">\r\n    ");
					if (entry.getKey() == -1) {
						jteOutput.writeContent("\r\n        <h3>Total</h3>\r\n    ");
					} else {
						jteOutput.writeContent("\r\n        <h3><a href=\"");
						jteOutput.writeUserContent(brackets.get(entry.getKey()).getUrl());
						jteOutput.writeContent("\">Bracket: ");
						jteOutput.writeUserContent(brackets.get(entry.getKey()).getName());
						jteOutput.writeContent(" - #");
						jteOutput.writeUserContent(entry.getKey());
						jteOutput.writeContent("</a></h3>\r\n        <p>\r\n            Taxrate: ");
						jteOutput.writeUserContent(brackets.get(entry.getKey()).moneyRate);
						jteOutput.writeContent("/");
						jteOutput.writeUserContent(brackets.get(entry.getKey()).rssRate);
						jteOutput.writeContent("<br>\r\n            Nations: ");
						jteOutput.writeUserContent(brackets.get(entry.getKey()).getNations().size());
						jteOutput.writeContent("<br>\r\n        </p>\r\n    ");
					}
					jteOutput.writeContent("\r\n    ");
					gg.jte.generated.ondemand.data.JtetimechartdatasrcGenerated.render(jteOutput, jteHtmlInterceptor, ws, "", categorized.createTable("", entry.getValue(), null).convertTurnsToEpochSeconds(start).toHtmlJson(), true);
					jteOutput.writeContent("\r\n\r\n    ");
					if (entry.getKey() == -1) {
						jteOutput.writeContent("\r\n    <div class=\"bg-white mt-3 rounded shadow py-1 searchable accordion\" id=\"Accordion");
						jteOutput.writeUserContent(entry.getKey());
						jteOutput.writeContent("\">\r\n        <div class=\"accordion-item\">\r\n            <div class=\"accordion-header\" id=\"heading");
						jteOutput.writeUserContent(entry.getKey());
						jteOutput.writeContent("\">\r\n                <button class=\"accordion-button collapsed p-1 btn-lg\" type=\"button\" data-bs-toggle=\"collapse\" data-bs-target=\"#collapse");
						jteOutput.writeUserContent(entry.getKey());
						jteOutput.writeContent("\" aria-expanded=\"false\" aria-controls=\"collapse");
						jteOutput.writeUserContent(entry.getKey());
						jteOutput.writeContent("\">\r\n                    <h3>Show by resource</h3>\r\n                </button>\r\n            </div>\r\n            <div id=\"collapse");
						jteOutput.writeUserContent(entry.getKey());
						jteOutput.writeContent("\" class=\"accordion-collapse collapse\" aria-labelledby=\"heading");
						jteOutput.writeUserContent(entry.getKey());
						jteOutput.writeContent("\" data-bs-parent=\"#Accordion");
						jteOutput.writeUserContent(entry.getKey());
						jteOutput.writeContent("\">\r\n                <div class=\"accordion-body bg-light\">\r\n                    ");
						for (ResourceType type : ResourceType.values) {
							jteOutput.writeContent("\r\n                        ");
							if (type != ResourceType.CREDITS) {
								jteOutput.writeContent("\r\n                            <div class=\"bg-light border border-3 border-secondary rounded\">\r\n                                ");
								gg.jte.generated.ondemand.data.JtetimechartdatasrcGenerated.render(jteOutput, jteHtmlInterceptor, ws, type.name(), categorized.createTable(type.name(), entry.getValue(),
                                type).convertTurnsToEpochSeconds(start).toHtmlJson(), true);
								jteOutput.writeContent("\r\n                            </div>\r\n                            <hr>\r\n                        ");
							}
							jteOutput.writeContent("\r\n                    ");
						}
						jteOutput.writeContent("\r\n                </div>\r\n            </div>\r\n        </div>\r\n    </div>\r\n    ");
					}
					jteOutput.writeContent("\r\n</div>\r\n<br>\r\n");
				}
				jteOutput.writeContent("\r\n");
			}
		}, title, null);
	}
	public static void renderMap(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, java.util.Map<String, Object> params) {
		WebStore ws = (WebStore)params.get("ws");
		String title = (String)params.get("title");
		GuildDB db = (GuildDB)params.get("db");
		long start = (long)params.get("start");
		long end = (long)params.get("end");
		TaxRecordCategorizer2 categorized = (TaxRecordCategorizer2)params.get("categorized");
		Map<Integer, Map<TaxRecordCategorizer2.TransactionType, double[][]>> categorizedByTurnByBracket = (Map<Integer, Map<TaxRecordCategorizer2.TransactionType, double[][]>>)params.get("categorizedByTurnByBracket");
		Map<Integer, TaxBracket> brackets = (Map<Integer, TaxBracket>)params.get("brackets");
		render(jteOutput, jteHtmlInterceptor, ws, title, db, start, end, categorized, categorizedByTurnByBracket, brackets);
	}
}
