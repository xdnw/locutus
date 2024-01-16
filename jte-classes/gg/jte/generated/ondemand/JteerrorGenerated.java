package gg.jte.generated.ondemand;
public final class JteerrorGenerated {
	public static final String JTE_NAME = "error.jte";
	public static final int[] JTE_LINE_INFO = {0,0,0,0,4,4,4,4,9,9,10,10,13,13,13,13,13,0,1,2,2,2,2};
	public static void render(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, link.locutus.discord.commands.manager.v2.binding.WebStore ws, String error, String stacktrace) {
		jteOutput.writeContent("\r\n");
		gg.jte.generated.ondemand.JtemainGenerated.render(jteOutput, jteHtmlInterceptor, ws, new gg.jte.Content() {
			public void writeTo(gg.jte.TemplateOutput jteOutput) {
				jteOutput.writeContent("\r\n<link rel=\"stylesheet\"  media=\"print\" onload=\"this.media='all'\" href=\"https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.7.0/styles/default.min.css\">\r\n<script src=\"https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.7.0/highlight.min.js\"></script>\r\n<script src=\"https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.7.0/languages/java.min.js\"></script>\r\n<div class=\"bg-white container mt-3 rounded shadow py-1\">\r\n    <h1 class=\"text-primary\">");
				jteOutput.writeUserContent(error);
				jteOutput.writeContent("</h1>\r\n    <pre><code class=\"language-java\">");
				jteOutput.writeUserContent(stacktrace);
				jteOutput.writeContent("</code></pre>\r\n    <script>hljs.highlightAll();</script>\r\n</div>\r\n");
			}
		}, "Home", null);
	}
	public static void renderMap(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, java.util.Map<String, Object> params) {
		link.locutus.discord.commands.manager.v2.binding.WebStore ws = (link.locutus.discord.commands.manager.v2.binding.WebStore)params.get("ws");
		String error = (String)params.get("error");
		String stacktrace = (String)params.get("stacktrace");
		render(jteOutput, jteHtmlInterceptor, ws, error, stacktrace);
	}
}
