package gg.jte.generated.ondemand.guild.milcom;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import link.locutus.discord.commands.manager.v2.binding.WebStore;
import link.locutus.discord.commands.rankings.SphereGenerator;
import link.locutus.discord.db.entities.metric.AllianceMetric;
import link.locutus.discord.db.entities.DBAlliance;
import java.util.Collections;
public final class JteglobalmilitarizationGenerated {
	public static final String JTE_NAME = "guild/milcom/globalmilitarization.jte";
	public static final int[] JTE_LINE_INFO = {0,0,1,2,3,4,5,6,7,8,9,10,12,12,12,20,20,20,20,26,26,27,27,28,28,30,30,31,31,31,31,32,32,35,35,35,35,35,35,37,37,39,44,44,44,44,47,47,48,48,49,49,51,55,55,55,57,57,58,58,63,63,64,64,75,75,75,75,75,12,13,14,15,16,17,18,18,18,18};
	public static void render(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, WebStore ws, SphereGenerator spheres, Set<DBAlliance> alliances, Map<DBAlliance, Map<AllianceMetric, Map<Long, Double>>> metricMap, Set<AllianceMetric> metrics, long startTurn, long endTurn) {
		jteOutput.writeContent("\r\n");
		gg.jte.generated.ondemand.JtemainGenerated.render(jteOutput, jteHtmlInterceptor, ws, new gg.jte.Content() {
			public void writeTo(gg.jte.TemplateOutput jteOutput) {
				jteOutput.writeContent("\r\n\r\n\r\n<h2 class=\"text-white\">Search</h2>\r\n<input class=\"form-control form-control-sm\" id=\"myInput\" type=\"text\" placeholder=\"Search..\">\r\n\r\n");
				for (Integer sphereId : spheres.getSpheres()) {
					jteOutput.writeContent("\r\n    ");
					if (!Collections.disjoint(metricMap.keySet(), spheres.getAlliances(sphereId))) {
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
							gg.jte.generated.ondemand.data.JtetimechartdatasrcGenerated.render(jteOutput, jteHtmlInterceptor, ws, spheres.getSphereName(sphereId) + " total", AllianceMetric.generateTable(metricMap, metrics, startTurn, endTurn,
                        spheres.getSphereName(sphereId),
                        new HashSet<>(spheres.getAlliances(sphereId))
                        ).convertTurnsToEpochSeconds(startTurn).toHtmlJson(), true);
							jteOutput.writeContent("\r\n                    </div>\r\n                    <hr>\r\n                    ");
						}
						jteOutput.writeContent("\r\n                    ");
						for (DBAlliance alliance : spheres.getAlliances(sphereId)) {
							jteOutput.writeContent("\r\n                    ");
							if (metricMap.containsKey(alliance)) {
								jteOutput.writeContent("\r\n                    <div class=\"card searchable\" style=\"display:inline-block;width:36rem;min-height:18rem;vertical-align: top;\">\r\n                        ");
								gg.jte.generated.ondemand.data.JtetimechartdatasrcGenerated.render(jteOutput, jteHtmlInterceptor, ws, alliance.getName(), AllianceMetric.generateTable(metricMap, metrics, startTurn, endTurn, alliance.getName(),
                        Collections.singleton(alliance)
                        ).convertTurnsToEpochSeconds(startTurn).toHtmlJson(), true);
								jteOutput.writeContent("\r\n                    </div>\r\n                    ");
							}
							jteOutput.writeContent("\r\n                    ");
						}
						jteOutput.writeContent("\r\n                </div>\r\n            </div>\r\n        </div>\r\n    </div>\r\n    ");
					}
					jteOutput.writeContent("\r\n");
				}
				jteOutput.writeContent("\r\n<script>\r\n$(document).ready(function(){\r\n    $(\"#myInput\").on(\"keyup\", function() {\r\n        var value = $(this).val().toLowerCase();\r\n        $(\".searchable\").filter(function() {\r\n        $(this).toggle($(this).text().toLowerCase().indexOf(value) > -1)\r\n        });\r\n    });\r\n});\r\n</script>\r\n");
			}
		}, "Global Stats", null);
	}
	public static void renderMap(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, java.util.Map<String, Object> params) {
		WebStore ws = (WebStore)params.get("ws");
		SphereGenerator spheres = (SphereGenerator)params.get("spheres");
		Set<DBAlliance> alliances = (Set<DBAlliance>)params.get("alliances");
		Map<DBAlliance, Map<AllianceMetric, Map<Long, Double>>> metricMap = (Map<DBAlliance, Map<AllianceMetric, Map<Long, Double>>>)params.get("metricMap");
		Set<AllianceMetric> metrics = (Set<AllianceMetric>)params.get("metrics");
		long startTurn = (long)params.get("startTurn");
		long endTurn = (long)params.get("endTurn");
		render(jteOutput, jteHtmlInterceptor, ws, spheres, alliances, metricMap, metrics, startTurn, endTurn);
	}
}
