package gg.jte.generated.ondemand.data;
import java.util.List;
import link.locutus.discord.commands.manager.v2.binding.WebStore;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.NationAttribute;
public final class JtenationfiltersGenerated {
	public static final String JTE_NAME = "data/nationfilters.jte";
	public static final int[] JTE_LINE_INFO = {0,0,1,2,4,4,4,14,14,14,14,4,5,6,7,8,8,8,8};
	public static void render(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, WebStore ws, List<NationAttribute> metricsDouble, List<NationAttribute> metricsInteger, List<NationAttribute> metricsBoolean, List<NationAttribute> metricsString) {
		jteOutput.writeContent("<form>\r\n\r\n</form>\r\n<script>\r\n// TODO on submit, collect all the values\r\n</script>");
	}
	public static void renderMap(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, java.util.Map<String, Object> params) {
		WebStore ws = (WebStore)params.get("ws");
		List<NationAttribute> metricsDouble = (List<NationAttribute>)params.get("metricsDouble");
		List<NationAttribute> metricsInteger = (List<NationAttribute>)params.get("metricsInteger");
		List<NationAttribute> metricsBoolean = (List<NationAttribute>)params.get("metricsBoolean");
		List<NationAttribute> metricsString = (List<NationAttribute>)params.get("metricsString");
		render(jteOutput, jteHtmlInterceptor, ws, metricsDouble, metricsInteger, metricsBoolean, metricsString);
	}
}
