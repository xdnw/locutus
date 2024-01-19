package gg.jte.generated.ondemand.data;
import link.locutus.discord.commands.manager.v2.binding.WebStore;
import java.util.*;
public final class JtetabletestGenerated {
	public static final String JTE_NAME = "data/tabletest.jte";
	public static final int[] JTE_LINE_INFO = {0,0,1,3,3,3,6,6,6,7,7,8,8,8,8,8,3,4,5,5,5,5};
	public static void render(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, WebStore ws, String title, String data) {
		gg.jte.generated.ondemand.JtemainGenerated.render(jteOutput, jteHtmlInterceptor, ws, new gg.jte.Content() {
			public void writeTo(gg.jte.TemplateOutput jteOutput) {
				jteOutput.writeContent("\r\n");
				gg.jte.generated.ondemand.data.Jtetable_dataGenerated.render(jteOutput, jteHtmlInterceptor, ws, title, data);
				jteOutput.writeContent("\r\n");
			}
		}, "Hello World", null);
	}
	public static void renderMap(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, java.util.Map<String, Object> params) {
		WebStore ws = (WebStore)params.get("ws");
		String title = (String)params.get("title");
		String data = (String)params.get("data");
		render(jteOutput, jteHtmlInterceptor, ws, title, data);
	}
}
