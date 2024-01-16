package gg.jte.generated.ondemand;
import java.util.List;
import java.util.Map;
import link.locutus.discord.commands.manager.v2.binding.WebStore;
import link.locutus.discord.web.jooby.WebRoot;
import link.locutus.discord.config.Settings;
import gg.jte.Content;
public final class JtemainGenerated {
	public static final String JTE_NAME = "main.jte";
	public static final int[] JTE_LINE_INFO = {0,0,1,2,3,4,5,7,7,7,19,19,19,65,65,78,78,96,96,101,101,103,103,104,104,105,105,108,108,108,7,8,9,10,10,10,10};
	public static void render(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, WebStore ws, Content content, String title, List<Map.Entry<String, String>> navbar) {
		jteOutput.writeContent("\r\n<!DOCTYPE html>\r\n<html lang=\"en\">\r\n<head>\r\n    <meta charset=\"utf-8\">\r\n    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1, shrink-to-fit=no\">\r\n    <meta name=\"color-scheme\" content=\"light dark\">\r\n\r\n    <title>");
		jteOutput.writeUserContent(title);
		jteOutput.writeContent("</title>\r\n     <link rel=\"stylesheet\"  media=\"print\" onload=\"this.media='all'\" href=\"https://cdn.jsdelivr.net/npm/bootstrap@5.1.1/dist/css/bootstrap.min.css\">\r\n\r\n     <link rel=\"stylesheet\"  media=\"print\" onload=\"this.media='all'\" href=\"https://cdn.jsdelivr.net/npm/bootstrap-icons@1.5.0/font/bootstrap-icons.css\">\r\n\r\n    <!-- Latest compiled and minified CSS -->\r\n     <link rel=\"stylesheet\"  media=\"print\" onload=\"this.media='all'\" href=\"https://cdn.jsdelivr.net/npm/bootstrap-select@1.14.0-beta2/dist/css/bootstrap-select.min.css\">\r\n\r\n     <link rel=\"stylesheet\"  media=\"print\" onload=\"this.media='all'\" type=\"text/css\" href=\"/css/all.css\">\r\n     <link rel=\"stylesheet\"  media=\"print\" onload=\"this.media='all'\" type=\"text/css\" href=\"/css/dark.css\">\r\n     <link rel=\"stylesheet\"  media=\"print\" onload=\"this.media='all'\" type=\"text/css\" href=\"/css/command.css\">\r\n     <link rel=\"stylesheet\"  media=\"print\" onload=\"this.media='all'\" type=\"text/css\" href=\"/css/embed.css\">\r\n     <link rel=\"stylesheet\"  media=\"print\" onload=\"this.media='all'\" type=\"text/css\" href=\"/css/datatables.css\">\r\n\r\n     <link rel=\"stylesheet\"  media=\"print\" onload=\"this.media='all'\" type=\"text/css\" href=\"https://leeoniya.github.io/uPlot/dist/uPlot.min.css\">\r\n\r\n    <script src=\"https://code.jquery.com/jquery-3.6.0.min.js\"></script>\r\n    <script src=\"https://cdnjs.cloudflare.com/ajax/libs/popper.js/2.4.4/umd/popper.js\"></script>\r\n    <script src=\"https://cdn.jsdelivr.net/npm/bootstrap@5.1.1/dist/js/bootstrap.bundle.min.js\"></script>\r\n    <!-- Latest compiled and minified JavaScript -->\r\n    <script src=\"https://cdn.jsdelivr.net/npm/bootstrap-select@1.14.0-beta2/dist/js/bootstrap-select.min.js\"></script>\r\n    <script src=\"https://cdnjs.cloudflare.com/ajax/libs/highlight.js/10.4.1/highlight.min.js\"></script>\r\n    <script src=\"https://unpkg.com/twemoji@14.0.2/dist/twemoji.min.js\"></script>\r\n\r\n    <script src=\"https://cdn.datatables.net/1.11.3/js/jquery.dataTables.min.js\"></script>\r\n\r\n    <script src=\"https://leeoniya.github.io/uPlot/dist/uPlot.iife.min.js\"></script>\r\n\r\n    <script src=\"/js/default.js\"></script>\r\n    <script src=\"/js/command.js\"></script>\r\n    <script src=\"/js/embed.js\"></script>\r\n    <script src=\"/js/components.js\"></script>\r\n    <script src=\"/js/pnwtable.js\"></script>\r\n\r\n    <script src=\"/js/colorutil.js\"></script>\r\n    <script src=\"/js/pnwtimechart.js\"></script>\r\n\r\n    <script src=\"https://cdn.jsdelivr.net/npm/chart.js\"></script>\r\n\r\n     <link rel=\"stylesheet\"  media=\"print\" onload=\"this.media='all'\" href=\"https://cdn.rawgit.com/afeld/bootstrap-toc/v0.3.0/dist/bootstrap-toc.min.css\">\r\n</head>\r\n<body>\r\n<div>\r\n<nav class=\"navbar navbar-expand-lg navbar-light bg-primary bg-gradient shadow p-0 mb-3 border-bottom border-3 border-dark text-white\">\r\n    <div class=\"container-fluid\">\r\n        <img src=\"https://cdn.discordapp.com/avatars/672237266940198960/0d78b819d401a8f983ab16242de195da.webp\" width=\"30\" height=\"30\" class=\"d-inline-block\" alt=\"\">\r\n        <a class=\"navbar-brand text-white bold\" href=\"");
		jteOutput.writeUserContent(WebRoot.REDIRECT);
		jteOutput.writeContent("\">\r\n            Locutus-web\r\n        </a>\r\n        <button class=\"navbar-toggler\" type=\"button\" data-bs-toggle=\"collapse\" data-bs-target=\"#navbarSupportedContent\" aria-controls=\"navbarSupportedContent\" aria-expanded=\"false\" aria-label=\"Toggle navigation\">\r\n            <span class=\"navbar-toggler-icon\"></span>\r\n        </button>\r\n        <div class=\"collapse navbar-collapse\" id=\"navbarSupportedContent\">\r\n            <ul class=\"navbar-nav me-auto mb-2 mb-lg-0\">\r\n            </ul>\r\n            <form class=\"d-flex\" id=\"navbar-search-form\" onsubmit=\"return search()\">\r\n                <input id=\"navbar-search\" class=\"form-control me-2\" type=\"search\" placeholder=\"Search\" aria-label=\"Search\" required>\r\n                <button class=\"btn btn-success shadow text-light\" type=\"submit\">submit</button>\r\n            </form>\r\n            <a class=\"btn btn-danger shadow text-light\" href=\"");
		jteOutput.writeUserContent(WebRoot.REDIRECT);
		jteOutput.writeContent("/logout\">Logout</a>\r\n        </div>\r\n    </div>\r\n</nav>\r\n</div>\r\n<script>\r\nvar pathname = window.location.pathname;\r\nif (pathname.startsWith(\"/\")) pathname = pathname.substring(1);\r\nvar pathSplit = pathname.split(\"/\");\r\n\r\nvar guild_id = \"\";\r\nif (/^[0-9]+$/.test(pathSplit[0])) {\r\n    guild_id = (pathSplit[0]);\r\n}\r\nif (guild_id == \"\") document.getElementById(\"navbar-search-form\").remove();\r\n\r\nfunction search() {\r\n    var value = document.getElementById(\"navbar-search\").value;\r\n    var url = \"");
		jteOutput.writeUserContent(WebRoot.REDIRECT);
		jteOutput.writeContent("/\" + guild_id + \"/search/\" + value;\r\n    window.location.href = url;\r\n    return false;\r\n}\r\n</script>\r\n<h1 class=\"display-4 m-0 text-white font-weight-bold d-flex justify-content-center\">");
		jteOutput.writeUserContent(title);
		jteOutput.writeContent("</h1>\r\n<div class=\"container-fluid\">\r\n");
		if (content != null) {
			jteOutput.writeContent("\r\n    ");
			jteOutput.writeUserContent(content);
			jteOutput.writeContent("\r\n");
		}
		jteOutput.writeContent("\r\n</div>\r\n</body>\r\n</html>");
	}
	public static void renderMap(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, java.util.Map<String, Object> params) {
		WebStore ws = (WebStore)params.get("ws");
		Content content = (Content)params.get("content");
		String title = (String)params.get("title");
		List<Map.Entry<String, String>> navbar = (List<Map.Entry<String, String>>)params.get("navbar");
		render(jteOutput, jteHtmlInterceptor, ws, content, title, navbar);
	}
}
