package gg.jte.generated.ondemand.data;
import com.google.gson.JsonObject;
import link.locutus.discord.commands.manager.v2.binding.WebStore;
public final class JtetimechartdatasrcpageGenerated {
	public static final String JTE_NAME = "data/timechartdatasrcpage.jte";
	public static final int[] JTE_LINE_INFO = {0,0,1,3,3,3,7,7,7,9,9,11,11,11,11,11,3,4,5,6,6,6,6};
	public static void render(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, WebStore ws, String title, JsonObject data, boolean time) {
		gg.jte.generated.ondemand.JtemainGenerated.render(jteOutput, jteHtmlInterceptor, ws, new gg.jte.Content() {
			public void writeTo(gg.jte.TemplateOutput jteOutput) {
				jteOutput.writeContent("\r\n<div class=\"bg-white container mt-3 rounded shadow py-1\">\r\n    ");
				gg.jte.generated.ondemand.data.JtetimechartdatasrcGenerated.render(jteOutput, jteHtmlInterceptor, ws, title, data, time);
				jteOutput.writeContent("\r\n</div>\r\n");
			}
		}, title, null);
	}
	public static void renderMap(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, java.util.Map<String, Object> params) {
		WebStore ws = (WebStore)params.get("ws");
		String title = (String)params.get("title");
		JsonObject data = (JsonObject)params.get("data");
		boolean time = (boolean)params.get("time");
		render(jteOutput, jteHtmlInterceptor, ws, title, data, time);
	}
}
