package gg.jte.generated.ondemand.guild.milcom;
import link.locutus.discord.commands.manager.v2.binding.WebStore;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.NationAttributeDouble;
import link.locutus.discord.commands.rankings.SphereGenerator;
import link.locutus.discord.db.entities.DBAlliance;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import link.locutus.discord.commands.rankings.table.TimeNumericTable;
public final class JteglobaltierstatsGenerated {
	public static final String JTE_NAME = "guild/milcom/globaltierstats.jte";
	public static final int[] JTE_LINE_INFO = {0,0,1,2,3,4,5,6,7,9,9,9,19,19,19,19,23,23,24,24,26,26,27,27,27,27,28,28,31,31,31,31,31,31,33,33,35,39,39,39,42,42,43,43,45,49,49,49,51,51,56,56,67,67,67,67,67,9,10,11,12,13,14,15,16,17,17,17,17};
	public static void render(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, WebStore ws, SphereGenerator spheres, Set<DBAlliance> alliances, Set<NationAttributeDouble> metrics, NationAttributeDouble groupBy, boolean total, boolean removeVM, int removeActiveM, boolean removeApps) {
		jteOutput.writeContent("\r\n");
		gg.jte.generated.ondemand.JtemainGenerated.render(jteOutput, jteHtmlInterceptor, ws, new gg.jte.Content() {
			public void writeTo(gg.jte.TemplateOutput jteOutput) {
				jteOutput.writeContent("\r\n<h2 class=\"text-white\">Search</h2>\r\n<input class=\"form-control form-control-sm\" id=\"myInput\" type=\"text\" placeholder=\"Search..\">\r\n\r\n");
				for (Integer sphereId : spheres.getSpheres()) {
					jteOutput.writeContent("\r\n    <div class=\"bg-white mt-3 rounded shadow py-1 searchable accordion\" id=\"Accordion");
					jteOutput.writeUserContent(sphereId);
					jteOutput.writeContent("\">\r\n        <div class=\"accordion-item\">\r\n            <div class=\"accordion-header\" id=\"heading");
					jteOutput.writeUserContent(sphereId);
					jteOutput.writeContent("\">\r\n                <button class=\"accordion-button p-1 btn-lg\" type=\"button\" data-bs-toggle=\"collapse\" data-bs-target=\"#collapse");
					jteOutput.writeUserContent(sphereId);
					jteOutput.writeContent("\" aria-expanded=\"true\" aria-controls=\"collapse");
					jteOutput.writeUserContent(sphereId);
					jteOutput.writeContent("\">\r\n                    <h3>");
					jteOutput.writeUserContent(spheres.getSphereName(sphereId));
					jteOutput.writeContent(" total</h3>\r\n                </button>\r\n            </div>\r\n            <div id=\"collapse");
					jteOutput.writeUserContent(sphereId);
					jteOutput.writeContent("\" class=\"accordion-collapse collapse show\" aria-labelledby=\"heading");
					jteOutput.writeUserContent(sphereId);
					jteOutput.writeContent("\" data-bs-parent=\"#Accordion");
					jteOutput.writeUserContent(sphereId);
					jteOutput.writeContent("\">\r\n                <div class=\"accordion-body bg-light\">\r\n                    ");
					if (spheres.getAlliances(sphereId).size() > 1) {
						jteOutput.writeContent("\r\n                    <div class=\"bg-light border border-3 border-secondary rounded searchable\">\r\n                        ");
						gg.jte.generated.ondemand.data.JtebarchartdatasrcGenerated.render(jteOutput, jteHtmlInterceptor, ws, spheres.getSphereName(sphereId) + " total", TimeNumericTable.create(spheres.getSphereName(sphereId) + ": ", metrics,
                        spheres.getAlliances(sphereId), groupBy, total, removeVM, removeActiveM, removeApps
                        ).toHtmlJson(), false);
						jteOutput.writeContent("\r\n                    </div>\r\n                    <hr>\r\n                    ");
					}
					jteOutput.writeContent("\r\n                    ");
					for (DBAlliance alliance : spheres.getAlliances(sphereId)) {
						jteOutput.writeContent("\r\n                        <div class=\"card searchable\" style=\"display:inline-block;width:36rem;min-height:18rem;vertical-align: top;\">\r\n                            ");
						gg.jte.generated.ondemand.data.JtebarchartdatasrcGenerated.render(jteOutput, jteHtmlInterceptor, ws, alliance.getName(), TimeNumericTable.create(alliance.getName() + ": ", metrics, Collections.singleton(alliance),
                            groupBy, total, removeVM, removeActiveM, removeApps
                            ).toHtmlJson(), false);
						jteOutput.writeContent("\r\n                        </div>\r\n                    ");
					}
					jteOutput.writeContent("\r\n                </div>\r\n            </div>\r\n        </div>\r\n    </div>\r\n");
				}
				jteOutput.writeContent("\r\n<script>\r\n$(document).ready(function(){\r\n    $(\"#myInput\").on(\"keyup\", function() {\r\n        var value = $(this).val().toLowerCase();\r\n        $(\".searchable\").filter(function() {\r\n        $(this).toggle($(this).text().toLowerCase().indexOf(value) > -1)\r\n        });\r\n    });\r\n});\r\n</script>\r\n    ");
			}
		}, "Global Stats", null);
	}
	public static void renderMap(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, java.util.Map<String, Object> params) {
		WebStore ws = (WebStore)params.get("ws");
		SphereGenerator spheres = (SphereGenerator)params.get("spheres");
		Set<DBAlliance> alliances = (Set<DBAlliance>)params.get("alliances");
		Set<NationAttributeDouble> metrics = (Set<NationAttributeDouble>)params.get("metrics");
		NationAttributeDouble groupBy = (NationAttributeDouble)params.get("groupBy");
		boolean total = (boolean)params.get("total");
		boolean removeVM = (boolean)params.get("removeVM");
		int removeActiveM = (int)params.get("removeActiveM");
		boolean removeApps = (boolean)params.get("removeApps");
		render(jteOutput, jteHtmlInterceptor, ws, spheres, alliances, metrics, groupBy, total, removeVM, removeActiveM, removeApps);
	}
}
