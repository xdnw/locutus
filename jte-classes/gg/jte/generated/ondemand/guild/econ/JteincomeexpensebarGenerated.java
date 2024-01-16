package gg.jte.generated.ondemand.guild.econ;
import link.locutus.discord.commands.manager.v2.binding.WebStore;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.MathMan;
public final class JteincomeexpensebarGenerated {
	public static final String JTE_NAME = "guild/econ/incomeexpensebar.jte";
	public static final int[] JTE_LINE_INFO = {0,0,1,2,4,4,4,10,10,10,10,10,11,11,11,11,11,11,13,13,13,13,13,13,16,16,16,16,17,17,17,17,17,17,19,19,19,19,19,19,22,22,22,4,5,6,7,8,8,8,8};
	public static void render(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, WebStore ws, String titleA, String titleB, double a, double b) {
		jteOutput.writeContent("<div class=\"progress\" style=\"height:20px\">\r\n<div class=\"progress-bar bg-success\" role=\"progressbar\" style=\"width: ");
		jteOutput.writeUserContent(100 * a / (a + b));
		jteOutput.writeContent("%\" aria-valuenow=\"");
		jteOutput.writeUserContent(100 * a / (a + b));
		jteOutput.writeContent("\" aria-valuemin=\"0\" aria-valuemax=\"100\"\r\n     data-bs-toggle=\"tooltip\" data-bs-placement=\"bottom\" title=\"");
		jteOutput.writeUserContent(titleA);
		jteOutput.writeContent(": ");
		jteOutput.writeUserContent(MathMan.format(a));
		jteOutput.writeContent(" (");
		jteOutput.writeUserContent(MathMan.format(100 * a / (a + b)));
		jteOutput.writeContent("%)\">\r\n    <div style=\"width:0!important\" class=\"text-center\">\r\n        ");
		jteOutput.writeUserContent(titleA);
		jteOutput.writeContent(": ");
		jteOutput.writeUserContent(MathMan.format(a));
		jteOutput.writeContent(" (");
		jteOutput.writeUserContent(MathMan.format(100 * a / (a + b)));
		jteOutput.writeContent("%)\r\n    </div>\r\n</div>\r\n<div class=\"progress-bar bg-danger\" role=\"progressbar\" style=\"width: ");
		jteOutput.writeUserContent(100 * b / (a + b));
		jteOutput.writeContent("%\" aria-valuenow=\"");
		jteOutput.writeUserContent(100 * b / (a + b));
		jteOutput.writeContent("\" aria-valuemin=\"0\" aria-valuemax=\"100\"\r\n     data-bs-toggle=\"tooltip\" data-bs-placement=\"bottom\" title=\"");
		jteOutput.writeUserContent(titleB);
		jteOutput.writeContent(": ");
		jteOutput.writeUserContent(MathMan.format(b));
		jteOutput.writeContent(" (");
		jteOutput.writeUserContent(MathMan.format(100 * b / (a + b)));
		jteOutput.writeContent("%)\">\r\n    <div style=\"width:0!important\" class=\"text-center\">\r\n        ");
		jteOutput.writeUserContent(titleB);
		jteOutput.writeContent(": ");
		jteOutput.writeUserContent(MathMan.format(b));
		jteOutput.writeContent(" (");
		jteOutput.writeUserContent(MathMan.format(100 * b / (a + b)));
		jteOutput.writeContent("%)\r\n    </div>\r\n</div>\r\n</div>");
	}
	public static void renderMap(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, java.util.Map<String, Object> params) {
		WebStore ws = (WebStore)params.get("ws");
		String titleA = (String)params.get("titleA");
		String titleB = (String)params.get("titleB");
		double a = (double)params.get("a");
		double b = (double)params.get("b");
		render(jteOutput, jteHtmlInterceptor, ws, titleA, titleB, a, b);
	}
}
