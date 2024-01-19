package gg.jte.generated.ondemand.auth;
import link.locutus.discord.commands.manager.v2.binding.WebStore;
import link.locutus.discord.user.Roles;
import link.locutus.discord.db.GuildDB;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import link.locutus.discord.db.entities.DBAlliance;
import java.util.List;
import com.google.gson.JsonArray;
import link.locutus.discord.web.jooby.WebRoot;
import link.locutus.discord.config.Settings;
public final class JtenationpickerGenerated {
	public static final String JTE_NAME = "auth/nationpicker.jte";
	public static final int[] JTE_LINE_INFO = {0,0,1,2,3,4,5,6,7,8,9,11,11,11,16,16,16,16,47,47,48,48,49,49,116,116,116,116,116,11,12,13,14,14,14,14};
	public static void render(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, WebStore ws, List<String> errors, JsonArray nationNames, JsonArray nationIds) {
		jteOutput.writeContent("\r\n");
		gg.jte.generated.ondemand.JtemainGenerated.render(jteOutput, jteHtmlInterceptor, ws, new gg.jte.Content() {
			public void writeTo(gg.jte.TemplateOutput jteOutput) {
				jteOutput.writeContent("\r\n<script src=\"https://cdnjs.cloudflare.com/ajax/libs/clusterize.js/0.19.0/clusterize.min.js\" integrity=\"sha512-sCslfbDbPoJepZJxo6S3mdJwYYt0SX+C9G1SYez6/yGuXcPrZXM9tqZQMpujvMZlujVve98JSzimWbYAlQzvFQ==\" crossorigin=\"anonymous\" referrerpolicy=\"no-referrer\"></script>\r\n<link rel=\"stylesheet\" href=\"https://cdnjs.cloudflare.com/ajax/libs/clusterize.js/0.19.0/clusterize.min.css\" integrity=\"sha512-8KLHxyeJ2I3BzL2ma1RZxwT1cc/U5Rz/uJg+G25tCrQ8sFfPz3MfJdKZegZDPijTxK2A3+b4kAXvzyK/OLLU5A==\" crossorigin=\"anonymous\" referrerpolicy=\"no-referrer\" />\r\n    <div class=\"container rounded shadow bg-white p-1\">\r\n    <!--  TODO error alerts  -->\r\n\r\n    <h3>Select your nation</h3>\r\n    <input class=\"form-control form-control-sm\" id=\"myInput\" type=\"text\" placeholder=\"Search..\">\r\n\r\n    <div class=\"clusterize\">\r\n        <table class=\"table\" style=\"background-color:#fff;color:#333\">\r\n            <thead>\r\n            <tr>\r\n                <th>Nation</th>\r\n                <th>ID</th>\r\n            </tr>\r\n            </thead>\r\n        </table>\r\n        <div id=\"scrollArea\" class=\"clusterize-scroll\">\r\n            <table class=\"table\" style=\"background-color:#fff;color:#333\">\r\n                <tbody id=\"contentArea\" class=\"clusterize-content\">\r\n                <tr class=\"clusterize-no-data\">\r\n                    <td>Loading data…</td>\r\n                </tr>\r\n                </tbody>\r\n            </table>\r\n        </div>\r\n    </div>\r\n</div>\r\n\r\n<script>\r\nvar nationNames = ");
				jteOutput.writeUnsafeContent(nationNames.toString());
				jteOutput.writeContent(";\r\nvar nationIds = ");
				jteOutput.writeUnsafeContent(nationIds.toString());
				jteOutput.writeContent(";\r\nvar baseUrl = \"");
				jteOutput.writeUserContent(WebRoot.REDIRECT);
				jteOutput.writeContent("\";\r\n\r\n// JavaScript\r\nvar rows = [],\r\n    search = document.getElementById('myInput');\r\n\r\n/* Fill array with data\r\n *\r\n * Params:\r\n * values *array*  - value of each field (in case use of table)\r\n *        example: ['1st TD content', '2nd TD content'] for table\r\n *                 ['list's LI item content'] for list\r\n * markup *string* - markup that will be added to the DOM\r\n * active *bool*   - specifies if row is suitable by search phrase\r\n*/\r\n\r\nvar tbody = document.getElementById(\"myTable\");\r\nvar bodyhtml = \"\";\r\nfor (var i = 0; i < nationNames.length; i++) {\r\n    var nationName = nationNames[i];\r\n    var nationId = nationIds[i];\r\n\r\n    rows.push({\r\n    values: [nationId, nationName],\r\n    markup: \"<tr><td><a href='\" + baseUrl + \"/page/login?nation=\" + nationId + \"'>\" + nationName + \"</a></td><td>\" + nationId + \"</td></tr>\",\r\n    active: true\r\n  });\r\n\r\n}\r\n\r\n/*\r\n* Fetch suitable rows\r\n*/\r\nvar filterRows = function(rows) {\r\n  var results = [];\r\n  for(var i = 0, ii = rows.length; i < ii; i++) {\r\n    if(rows[i].active) results.push(rows[i].markup)\r\n  }\r\n  return results;\r\n}\r\n\r\n/*\r\n* Init clusterize.js\r\n*/\r\nvar clusterize = new Clusterize({\r\n  rows: filterRows(rows),\r\n  scrollId: 'scrollArea',\r\n  contentId: 'contentArea'\r\n});\r\n\r\n/*\r\n* Attach listener to search input tag and filter list on change\r\n*/\r\nvar onSearch = function() {\r\n  for(var i = 0, ii = rows.length; i < ii; i++) {\r\n    var suitable = false;\r\n    for(var j = 0, jj = rows[i].values.length; j < jj; j++) {\r\n      if(rows[i].values[j].toString().indexOf(search.value) + 1)\r\n        suitable = true;\r\n    }\r\n    rows[i].active = suitable;\r\n  }\r\n  clusterize.update(filterRows(rows));\r\n}\r\nsearch.oninput = onSearch;\r\n\r\n</script>\r\n");
			}
		}, "Select your nation", null);
	}
	public static void renderMap(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, java.util.Map<String, Object> params) {
		WebStore ws = (WebStore)params.get("ws");
		List<String> errors = (List<String>)params.get("errors");
		JsonArray nationNames = (JsonArray)params.get("nationNames");
		JsonArray nationIds = (JsonArray)params.get("nationIds");
		render(jteOutput, jteHtmlInterceptor, ws, errors, nationNames, nationIds);
	}
}
