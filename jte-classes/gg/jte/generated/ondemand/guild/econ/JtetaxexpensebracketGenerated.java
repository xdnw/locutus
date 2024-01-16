package gg.jte.generated.ondemand.guild.econ;
import link.locutus.discord.Locutus;
import java.util.ArrayList;
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
public final class JtetaxexpensebracketGenerated {
	public static final String JTE_NAME = "guild/econ/taxexpensebracket.jte";
	public static final int[] JTE_LINE_INFO = {0,0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,33,33,33,49,49,49,50,50,50,50,50,50,50,50,51,51,51,51,52,52,53,53,54,54,56,56,57,57,58,58,58,58,59,59,61,61,77,77,79,79,79,79,80,80,81,81,81,81,82,82,84,84,86,86,88,88,90,90,92,92,93,93,93,93,94,94,94,94,95,95,96,96,97,97,98,98,100,100,101,101,101,101,101,101,104,104,107,107,107,108,108,110,110,111,111,112,112,113,113,115,115,116,116,117,117,118,118,121,121,122,122,124,124,124,124,124,124,127,127,127,129,129,131,131,133,133,136,136,138,138,139,139,141,141,146,146,148,148,148,33,34,35,36,37,38,39,40,41,42,43,44,45,46,46,46,46};
	public static void render(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, WebStore ws, Object id, String title, GuildDB db, TaxBracket bracket, Map<Integer, TaxBracket> bracketsByNation, List<DBNation> nationsByBracket, Map<Integer, Integer> bracketToNationDepositCount, double[] income, Map<Integer,double[]> incomeByNation, Map<Integer, List<Transaction2>> transactionsByNation, List<Transaction2> transactions, double[] expense, Map<Integer,double[]> expensesByNation) {
		jteOutput.writeContent("\r\n<div class=\"bg-white container mt-3 rounded shadow py-1\">\r\n    ");
		if (bracket != null) {
			jteOutput.writeContent("\r\n        <h3><a href=\"https://politicsandwar.com/index.php?id=15&tax_id=");
			jteOutput.writeUserContent(bracket.taxId);
			jteOutput.writeContent("\">");
			jteOutput.writeUserContent(title);
			jteOutput.writeContent(" #");
			jteOutput.writeUserContent(bracket.taxId);
			jteOutput.writeContent(" ");
			jteOutput.writeUserContent(bracket.getName());
			jteOutput.writeContent("</a></h3>\r\n        <p>Taxrate: ");
			jteOutput.writeUserContent(bracket.moneyRate);
			jteOutput.writeContent("/");
			jteOutput.writeUserContent(bracket.rssRate);
			jteOutput.writeContent("</p><br>\r\n    ");
		} else {
			jteOutput.writeContent("\r\n        <h3>");
			jteOutput.writeUserContent(title);
			jteOutput.writeContent("</h3>\r\n    ");
		}
		jteOutput.writeContent("\r\n    <h4>Bracket Income/Expense breakdown</h4>\r\n    ");
		gg.jte.generated.ondemand.guild.econ.JtetaxexpensesbreakdownGenerated.render(jteOutput, jteHtmlInterceptor, ws, id, income, expense, true);
		jteOutput.writeContent("\r\n    ");
		if (!nationsByBracket.isEmpty()) {
			jteOutput.writeContent("\r\n    <a class=\"btn btn-primary btn-sm\" data-bs-toggle=\"collapse\" href=\"#collapseNations");
			jteOutput.writeUserContent(id + "");
			jteOutput.writeContent("\" role=\"button\" aria-expanded=\"false\" aria-controls=\"collapseNations");
			jteOutput.writeUserContent(id + "");
			jteOutput.writeContent("\">\r\n        Show ");
			jteOutput.writeUserContent(nationsByBracket.size());
			jteOutput.writeContent(" nations\r\n    </a>\r\n    <div class=\"collapse\" id=\"collapseNations");
			jteOutput.writeUserContent(id + "");
			jteOutput.writeContent("\">\r\n        <div class=\"card card-body\">\r\n        <table class=\"table\">\r\n            <thead>\r\n                <th>Nation</th>\r\n                <th>Cities</th>\r\n                <th>Off/Def</th>\r\n                <th>Timers[City/Project]</th>\r\n                <th>Project Slots</th>\r\n                <th>mmr[unit]</th>\r\n                <th>mmr[build]</th>\r\n                <th>avg_infra</th>\r\n                <th>avg_land</th>\r\n                <th>action</th>\r\n            </thead>\r\n            <tbody>\r\n                ");
			for (DBNation nation : nationsByBracket) {
				jteOutput.writeContent("\r\n                <tr>\r\n                    <td><a href=\"");
				jteOutput.writeUserContent(nation.getNationUrl());
				jteOutput.writeContent("\">");
				jteOutput.writeUserContent(nation.getNation());
				jteOutput.writeContent("</a></td>\r\n                    <td>");
				jteOutput.writeUserContent(nation.getCities());
				jteOutput.writeContent("</td>\r\n                    <td>");
				jteOutput.writeUserContent(nation.getOff());
				jteOutput.writeContent("/");
				jteOutput.writeUserContent(nation.getDef());
				jteOutput.writeContent("</td>\r\n                    ");
				if (nation.getPosition() <= 1) {
					jteOutput.writeContent("\r\n                    <td colspan=\"100\" class=\"bg-warning text-center text-bold\">nation is applicant</td>\r\n                    ");
				} else if (nation.getVm_turns() > 0) {
					jteOutput.writeContent("\r\n                    <td colspan=\"100\" class=\"bg-warning text-center text-bold\">nation is VM</td>\r\n                    ");
				} else if (nation.getActive_m() > 7200) {
					jteOutput.writeContent("\r\n                    <td colspan=\"100\" class=\"bg-warning text-center text-bold\">nation is inactive</td>\r\n                    ");
				} else if (nation.isGray()) {
					jteOutput.writeContent("\r\n                    <td colspan=\"100\" class=\"bg-warning text-center text-bold\">nation is gray (untaxable)</td>\r\n                    ");
				} else if (nation.isBeige()) {
					jteOutput.writeContent("\r\n                    <td colspan=\"100\" class=\"bg-warning text-center text-bold\">nation is beige (untaxable)</td>\r\n                    ");
				} else {
					jteOutput.writeContent("\r\n                    <td>");
					jteOutput.writeUserContent(nation.getCityTurns());
					jteOutput.writeContent("/");
					jteOutput.writeUserContent(nation.getProjectTurns());
					jteOutput.writeContent("</td>\r\n                    <td>");
					jteOutput.writeUserContent(nation.getNumProjects());
					jteOutput.writeContent("/");
					jteOutput.writeUserContent(nation.projectSlots());
					jteOutput.writeContent("</td>\r\n                    <td>");
					jteOutput.writeUserContent(nation.getMMR());
					jteOutput.writeContent("</td>\r\n                    <td>");
					jteOutput.writeUserContent(nation.getMMRBuildingStr());
					jteOutput.writeContent("</td>\r\n                    <td>");
					jteOutput.writeUserContent(nation.getAvg_infra());
					jteOutput.writeContent("</td>\r\n                    <td>");
					jteOutput.writeUserContent((int) nation.getAvgLand());
					jteOutput.writeContent("</td>\r\n                    <td>\r\n                        ");
					if (incomeByNation.containsKey(nation.getNation_id()) || expensesByNation.containsKey(nation.getNation_id())) {
						jteOutput.writeContent("\r\n                        <a class=\"btn btn-secondary btn-sm\" data-bs-toggle=\"collapse\" href=\"#collapseInfo");
						jteOutput.writeUserContent(id + "");
						jteOutput.writeUserContent(nation.getNation_id());
						jteOutput.writeContent("\" role=\"button\" aria-expanded=\"false\" aria-controls=\"collapseInfo");
						jteOutput.writeUserContent(id + "");
						jteOutput.writeUserContent(nation.getNation_id());
						jteOutput.writeContent("\">\r\n                            more info\r\n                        </a>\r\n                        ");
					}
					jteOutput.writeContent("\r\n                    </td>\r\n                </tr>\r\n                <tr colspan=\"100\" class=\"collapse border border-secondary rounded\" id=\"collapseInfo");
					jteOutput.writeUserContent(id + "");
					jteOutput.writeUserContent(nation.getNation_id());
					jteOutput.writeContent("\">\r\n                    ");
					if (incomeByNation.containsKey(nation.getNation_id()) || expensesByNation.containsKey(nation.getNation_id())) {
						jteOutput.writeContent("\r\n                    <td colspan=\"100\">\r\n                        ");
						if (bracket == null) {
							jteOutput.writeContent("\r\n                        ");
							if (bracketsByNation.containsKey(nation.getNation_id())) {
								jteOutput.writeContent("\r\n                        <b>Current taxrate: </b> ");
								jteOutput.writeUserContent(bracketsByNation.get(nation.getNation_id()).taxId);
								jteOutput.writeContent("<br>\r\n                        ");
							} else {
								jteOutput.writeContent("\r\n                        <b>Unknown taxrate</b><br>\r\n                        ");
							}
							jteOutput.writeContent("\r\n                        ");
						}
						jteOutput.writeContent("\r\n                        <b># tax records (turns): </b> ");
						jteOutput.writeUserContent(bracketToNationDepositCount.getOrDefault(nation.getNation_id(), 0));
						jteOutput.writeContent("<br>\r\n                        <b># transactions (AA -&gt; nation): </b> ");
						jteOutput.writeUserContent(transactionsByNation.getOrDefault(nation.getNation_id(), new ArrayList<>()).size());
						jteOutput.writeContent("<br>\r\n                        <hr>\r\n                        <b>nation income/expense breakdown:</b>\r\n                        ");
						gg.jte.generated.ondemand.guild.econ.JtetaxexpensesbreakdownGenerated.render(jteOutput, jteHtmlInterceptor, ws, id + "" + nation.getNation_id(), incomeByNation.computeIfAbsent(nation.getNation_id(), f -> ResourceType.getBuffer()), expensesByNation.computeIfAbsent(nation.getNation_id(), f -> ResourceType.getBuffer()), true);
						jteOutput.writeContent("\r\n                        ");
						if (transactionsByNation.containsKey(nation.getNation_id())) {
							jteOutput.writeContent("\r\n                        <hr>\r\n                        <a class=\"btn btn-primary btn-sm\" data-bs-toggle=\"collapse\" href=\"#collapseTX");
							jteOutput.writeUserContent(id + "");
							jteOutput.writeUserContent(nation.getNation_id());
							jteOutput.writeContent("\" role=\"button\" aria-expanded=\"false\" aria-controls=\"collapseTX");
							jteOutput.writeUserContent(id + "");
							jteOutput.writeUserContent(nation.getNation_id());
							jteOutput.writeContent("\">\r\n                            Show transactions\r\n                        </a>\r\n                        <div class=\"collapse border border-secondary rounded\" id=\"collapseTX");
							jteOutput.writeUserContent(id + "");
							jteOutput.writeUserContent(nation.getNation_id());
							jteOutput.writeContent("\">\r\n                            <table class=\"table card card-body\">\r\n                                ");
							for (Transaction2 transfer : transactionsByNation.get(nation.getNation_id())) {
								jteOutput.writeContent("\r\n                                <tr>\r\n                                    <td>");
								jteOutput.writeUserContent(transfer.toSimpleString());
								jteOutput.writeContent("</td>\r\n                                </tr>\r\n                                ");
							}
							jteOutput.writeContent("\r\n                            </table>\r\n                        </div>\r\n                        ");
						}
						jteOutput.writeContent("\r\n                    </td>\r\n                        ");
					}
					jteOutput.writeContent("\r\n                    ");
				}
				jteOutput.writeContent("\r\n                </tr>\r\n                ");
			}
			jteOutput.writeContent("\r\n            </tbody>\r\n        </table>\r\n        </div>\r\n    </div>\r\n    ");
		}
		jteOutput.writeContent("\r\n\r\n</div>");
	}
	public static void renderMap(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, java.util.Map<String, Object> params) {
		WebStore ws = (WebStore)params.get("ws");
		Object id = (Object)params.get("id");
		String title = (String)params.get("title");
		GuildDB db = (GuildDB)params.get("db");
		TaxBracket bracket = (TaxBracket)params.get("bracket");
		Map<Integer, TaxBracket> bracketsByNation = (Map<Integer, TaxBracket>)params.get("bracketsByNation");
		List<DBNation> nationsByBracket = (List<DBNation>)params.get("nationsByBracket");
		Map<Integer, Integer> bracketToNationDepositCount = (Map<Integer, Integer>)params.get("bracketToNationDepositCount");
		double[] income = (double[])params.get("income");
		Map<Integer,double[]> incomeByNation = (Map<Integer,double[]>)params.get("incomeByNation");
		Map<Integer, List<Transaction2>> transactionsByNation = (Map<Integer, List<Transaction2>>)params.get("transactionsByNation");
		List<Transaction2> transactions = (List<Transaction2>)params.get("transactions");
		double[] expense = (double[])params.get("expense");
		Map<Integer,double[]> expensesByNation = (Map<Integer,double[]>)params.get("expensesByNation");
		render(jteOutput, jteHtmlInterceptor, ws, id, title, db, bracket, bracketsByNation, nationsByBracket, bracketToNationDepositCount, income, incomeByNation, transactionsByNation, transactions, expense, expensesByNation);
	}
}
