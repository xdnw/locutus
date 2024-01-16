package gg.jte.generated.ondemand.guild;
import link.locutus.discord.commands.manager.v2.binding.WebStore;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import com.fizzed.rocker.RenderingException;
import com.fizzed.rocker.runtime.DefaultRockerTemplate;
import com.fizzed.rocker.runtime.PlainTextUnloadedClassLoader;
import link.locutus.discord.apiv1.enums.SuccessType;
public final class JteoddssuccessGenerated {
	public static final String JTE_NAME = "guild/oddssuccess.jte";
	public static final int[] JTE_LINE_INFO = {0,0,1,2,3,4,5,6,9,9,9,12,12,13,13,15,15,17,17,19,19,21,21,21,21,21,21,23,23,23,23,26,26,26,26,9,10,11,11,11,11};
	public static void render(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, WebStore ws, double odds, int success) {
		if (odds > 0) {
			jteOutput.writeContent("\r\n<div class=\"overflow-hidden progress-bar ");
			if (success == 0) {
				jteOutput.writeContent("\r\n        bg-danger\r\n    ");
			} else if (success == 1) {
				jteOutput.writeContent("\r\n        bg-warning\r\n    ");
			} else if (success == 2) {
				jteOutput.writeContent("\r\n        bg-info\r\n    ");
			} else if (success == 3) {
				jteOutput.writeContent("\r\n        bg-primary\r\n    ");
			}
			jteOutput.writeContent("\" role=\"progressbar\" style=\"width: ");
			jteOutput.writeUserContent(odds);
			jteOutput.writeContent("%\" aria-valuenow=\"");
			jteOutput.writeUserContent(odds);
			jteOutput.writeContent("\" aria-valuemin=\"0\" aria-valuemax=\"100\">\r\n    <div style=\"width:0!important\">\r\n        ");
			jteOutput.writeUserContent((int) odds);
			jteOutput.writeContent("% ");
			jteOutput.writeUserContent(SuccessType.values[success]);
			jteOutput.writeContent("\r\n    </div>\r\n    </div>\r\n");
		}
	}
	public static void renderMap(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, java.util.Map<String, Object> params) {
		WebStore ws = (WebStore)params.get("ws");
		double odds = (double)params.get("odds");
		int success = (int)params.get("success");
		render(jteOutput, jteHtmlInterceptor, ws, odds, success);
	}
}
