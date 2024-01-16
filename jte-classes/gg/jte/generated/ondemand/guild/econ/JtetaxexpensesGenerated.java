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
public final class JtetaxexpensesGenerated {
	public static final String JTE_NAME = "guild/econ/taxexpenses.jte";
	public static final int[] JTE_LINE_INFO = {0,0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,34,34,34,60,60,60,60,62,62,64,64,65,65,67,67,68,68,70,70,71,71,74,74,75,75,75,75,76,76,81,90,92,92,93,93,94,103,104,104,105,105,106,106,106,106,106,34,35,36,37,38,39,40,41,42,43,44,45,46,47,48,49,50,51,52,53,54,55,56,57,58,58,58,58};
	public static void render(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, WebStore ws, GuildDB db, Set<Integer> alliances, boolean requireGrant, boolean requireExpiry, boolean requireTagged, Map<Integer, TaxBracket> brackets, List<BankDB.TaxDeposit> taxes, Map<Integer, TaxBracket> bracketsByNation, Map<Integer, List<DBNation>> nationsByBracket, List<DBNation> nations, Map<Integer, Map<Integer, Integer>> bracketToNationDepositCount, Map<Integer, Integer> allNationDepositCount, double[] incomeTotal, Map<Integer,double[]> incomeByBracket, Map<Integer,double[]> incomeByNation, Map<Integer, Map<Integer,double[]>> incomeByNationByBracket, Map<Integer, List<Transaction2>> transactionsByNation, Map<Integer, List<Transaction2>> transactionsByBracket, Map<Integer, Map<Integer, List<Transaction2>>> transactionsByNationByBracket, List<Transaction2> expenseTransfers, double[] expenseTotal, Map<Integer,double[]> expensesByBracket, Map<Integer,double[]> expensesByNation, Map<Integer, Map<Integer,double[]>> expensesByNationByBracket) {
		jteOutput.writeContent("\r\n");
		gg.jte.generated.ondemand.JtemainGenerated.render(jteOutput, jteHtmlInterceptor, ws, new gg.jte.Content() {
			public void writeTo(gg.jte.TemplateOutput jteOutput) {
				jteOutput.writeContent("\r\n<div class=\"bg-white container mt-3 rounded shadow py-1\">\r\n");
				if (requireGrant) {
					jteOutput.writeContent("\r\n    <kbd>requireGrant</kbd>\r\n");
				}
				jteOutput.writeContent("\r\n");
				if (requireExpiry) {
					jteOutput.writeContent("\r\n    <kbd>requireExpiry</kbd>\r\n");
				}
				jteOutput.writeContent("\r\n");
				if (requireTagged) {
					jteOutput.writeContent("\r\n    <kbd>requireTagged</kbd>\r\n");
				}
				jteOutput.writeContent("\r\n<kbd>#");
				jteOutput.writeUserContent(taxes.size());
				jteOutput.writeContent(" tax records</kbd>\r\n<h2>Alliances:</h2>\r\n<ul class=\"list-inline\">\r\n");
				for (int allianceId : alliances) {
					jteOutput.writeContent("\r\n    <li class=\"list-inline-item\"><a class=\"btn btn-sm border rounded\" href=\"https://politicsandwar.com/alliance/id=");
					jteOutput.writeUserContent(allianceId);
					jteOutput.writeContent("\">");
					jteOutput.writeUserContent(PnwUtil.getName(allianceId, true));
					jteOutput.writeContent("</a></li>\r\n");
				}
				jteOutput.writeContent("\r\n</ul>\r\n</div>\r\n\r\n<!--Total-->\r\n");
				gg.jte.generated.ondemand.guild.econ.JtetaxexpensebracketGenerated.render(jteOutput, jteHtmlInterceptor, ws, -1, "Total Tax", db, null, bracketsByNation, nations, allNationDepositCount, incomeTotal, incomeByNation, transactionsByNation, expenseTransfers, expenseTotal, expensesByNation);
				jteOutput.writeContent("\r\n\r\n");
				for (Map.Entry<Integer, TaxBracket> entry : brackets.entrySet()) {
					jteOutput.writeContent("\r\n    ");
					if (expensesByBracket.get(entry.getKey()) != null || incomeByBracket.get(entry.getKey()) != null) {
						jteOutput.writeContent("\r\n        ");
						gg.jte.generated.ondemand.guild.econ.JtetaxexpensebracketGenerated.render(jteOutput, jteHtmlInterceptor, ws, entry.getKey(), "Bracket", db, entry.getValue(), bracketsByNation, nationsByBracket.getOrDefault(entry.getKey(), new ArrayList<>()), bracketToNationDepositCount.getOrDefault(entry.getKey(), new HashMap<>()), incomeByBracket.getOrDefault(entry.getKey(), ResourceType.getBuffer()), incomeByNationByBracket.getOrDefault(entry.getKey(), new HashMap<>()), transactionsByNationByBracket.getOrDefault(entry.getKey(), new HashMap<>()), transactionsByBracket.getOrDefault(entry.getKey(), new ArrayList<>()), expensesByBracket.getOrDefault(entry.getKey(), ResourceType.getBuffer()), expensesByNationByBracket.getOrDefault(entry.getKey(), new HashMap<>()));
						jteOutput.writeContent("\r\n    ");
					}
					jteOutput.writeContent("\r\n");
				}
				jteOutput.writeContent("\r\n");
			}
		}, "Tax Expenses", null);
	}
	public static void renderMap(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, java.util.Map<String, Object> params) {
		WebStore ws = (WebStore)params.get("ws");
		GuildDB db = (GuildDB)params.get("db");
		Set<Integer> alliances = (Set<Integer>)params.get("alliances");
		boolean requireGrant = (boolean)params.get("requireGrant");
		boolean requireExpiry = (boolean)params.get("requireExpiry");
		boolean requireTagged = (boolean)params.get("requireTagged");
		Map<Integer, TaxBracket> brackets = (Map<Integer, TaxBracket>)params.get("brackets");
		List<BankDB.TaxDeposit> taxes = (List<BankDB.TaxDeposit>)params.get("taxes");
		Map<Integer, TaxBracket> bracketsByNation = (Map<Integer, TaxBracket>)params.get("bracketsByNation");
		Map<Integer, List<DBNation>> nationsByBracket = (Map<Integer, List<DBNation>>)params.get("nationsByBracket");
		List<DBNation> nations = (List<DBNation>)params.get("nations");
		Map<Integer, Map<Integer, Integer>> bracketToNationDepositCount = (Map<Integer, Map<Integer, Integer>>)params.get("bracketToNationDepositCount");
		Map<Integer, Integer> allNationDepositCount = (Map<Integer, Integer>)params.get("allNationDepositCount");
		double[] incomeTotal = (double[])params.get("incomeTotal");
		Map<Integer,double[]> incomeByBracket = (Map<Integer,double[]>)params.get("incomeByBracket");
		Map<Integer,double[]> incomeByNation = (Map<Integer,double[]>)params.get("incomeByNation");
		Map<Integer, Map<Integer,double[]>> incomeByNationByBracket = (Map<Integer, Map<Integer,double[]>>)params.get("incomeByNationByBracket");
		Map<Integer, List<Transaction2>> transactionsByNation = (Map<Integer, List<Transaction2>>)params.get("transactionsByNation");
		Map<Integer, List<Transaction2>> transactionsByBracket = (Map<Integer, List<Transaction2>>)params.get("transactionsByBracket");
		Map<Integer, Map<Integer, List<Transaction2>>> transactionsByNationByBracket = (Map<Integer, Map<Integer, List<Transaction2>>>)params.get("transactionsByNationByBracket");
		List<Transaction2> expenseTransfers = (List<Transaction2>)params.get("expenseTransfers");
		double[] expenseTotal = (double[])params.get("expenseTotal");
		Map<Integer,double[]> expensesByBracket = (Map<Integer,double[]>)params.get("expensesByBracket");
		Map<Integer,double[]> expensesByNation = (Map<Integer,double[]>)params.get("expensesByNation");
		Map<Integer, Map<Integer,double[]>> expensesByNationByBracket = (Map<Integer, Map<Integer,double[]>>)params.get("expensesByNationByBracket");
		render(jteOutput, jteHtmlInterceptor, ws, db, alliances, requireGrant, requireExpiry, requireTagged, brackets, taxes, bracketsByNation, nationsByBracket, nations, bracketToNationDepositCount, allNationDepositCount, incomeTotal, incomeByBracket, incomeByNation, incomeByNationByBracket, transactionsByNation, transactionsByBracket, transactionsByNationByBracket, expenseTransfers, expenseTotal, expensesByBracket, expensesByNation, expensesByNationByBracket);
	}
}
