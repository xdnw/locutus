package gg.jte.generated.ondemand.command;
import link.locutus.discord.commands.manager.v2.binding.WebStore;
import link.locutus.discord.web.commands.search.SearchResult;
import link.locutus.discord.web.commands.search.SearchType;
import java.util.List;
import org.apache.commons.lang3.text.WordUtils;
public final class JtesearchGenerated {
	public static final String JTE_NAME = "command/search.jte";
	public static final int[] JTE_LINE_INFO = {0,0,1,2,3,4,6,6,6,10,10,10,10,11,11,12,12,13,13,15,15,17,17,17,17,17,17,19,19,21,21,60,60,72,72,73,73,73,73,73,6,7,8,8,8,8};
	public static void render(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, WebStore ws, String term, List<SearchResult> results) {
		jteOutput.writeContent("\r\n");
		gg.jte.generated.ondemand.JtemainGenerated.render(jteOutput, jteHtmlInterceptor, ws, new gg.jte.Content() {
			public void writeTo(gg.jte.TemplateOutput jteOutput) {
				jteOutput.writeContent("\r\n");
				if (results.isEmpty()) {
					jteOutput.writeContent("\r\n    <div class=\"alert alert-danger\">No results found for <kbd>");
					jteOutput.writeUserContent(term);
					jteOutput.writeContent("</kbd></div>\r\n");
				} else {
					jteOutput.writeContent("\r\n    <div id=\"results\">\r\n");
					for (SearchResult result : results) {
						jteOutput.writeContent("\r\n    <div class=\"bg-white container mt-1 rounded shadow\">\r\n        <h3>");
						jteOutput.writeUserContent(WordUtils.capitalizeFully(result.type.name()));
						jteOutput.writeContent(": <a href=\"");
						jteOutput.writeUserContent(result.url);
						jteOutput.writeContent("\">");
						jteOutput.writeUserContent(result.title);
						jteOutput.writeContent("</a></h3>\r\n        <hr>\r\n        ");
						jteOutput.writeUnsafeContent(result.body);
						jteOutput.writeContent("\r\n    </div>\r\n");
					}
					jteOutput.writeContent("\r\n    </div>\r\n\r\n    <script>\r\nvar matchText = function(node, regex, callback, excludeElements) {\r\n\r\n    excludeElements || (excludeElements = ['script', 'style', 'iframe', 'canvas']);\r\n    var child = node.firstChild;\r\n\r\n    while (child) {\r\n        switch (child.nodeType) {\r\n        case 1:\r\n            if (excludeElements.indexOf(child.tagName.toLowerCase()) > -1)\r\n                break;\r\n            matchText(child, regex, callback, excludeElements);\r\n            break;\r\n        case 3:\r\n            var bk = 0;\r\n            child.data.replace(regex, function(all) {\r\n                var args = [].slice.call(arguments),\r\n                    offset = args[args.length - 2],\r\n                    newTextNode = child.splitText(offset+bk), tag;\r\n                bk -= child.data.length + all.length;\r\n\r\n                newTextNode.data = newTextNode.data.substr(all.length);\r\n                tag = callback.apply(window, [child].concat(args));\r\n                child.parentNode.insertBefore(tag, newTextNode);\r\n                child = newTextNode;\r\n            });\r\n            regex.lastIndex = 0;\r\n            break;\r\n        }\r\n\r\n        child = child.nextSibling;\r\n    }\r\n\r\n    return node;\r\n};\r\n\r\nvar term = \"");
					jteOutput.writeUserContent(term);
					jteOutput.writeContent("\";\r\nvar root = document.getElementById(\"results\");\r\n\r\n$(document).ready(function() {\r\n    matchText(root, new RegExp(\"\" + term + \"\", \"gi\"), function(node, match, offset) {\r\n        var wrap = document.createElement(\"span\");\r\n        wrap.style = \"background-color: #FFFF00\";\r\n        wrap.textContent = match;\r\n        return wrap;\r\n    });\r\n});\r\n    </script>\r\n");
				}
				jteOutput.writeContent("\r\n");
			}
		}, "Search Results", null);
	}
	public static void renderMap(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, java.util.Map<String, Object> params) {
		WebStore ws = (WebStore)params.get("ws");
		String term = (String)params.get("term");
		List<SearchResult> results = (List<SearchResult>)params.get("results");
		render(jteOutput, jteHtmlInterceptor, ws, term, results);
	}
}
