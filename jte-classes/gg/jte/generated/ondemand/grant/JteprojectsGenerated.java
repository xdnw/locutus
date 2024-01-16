package gg.jte.generated.ondemand.grant;
import java.util.*;
import link.locutus.discord.commands.manager.v2.binding.WebStore;
import link.locutus.discord.util.offshore.Grant;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.apiv1.enums.city.project.Project;
import net.dv8tion.jda.api.entities.User;
import link.locutus.discord.db.entities.DBNation;
import java.util.List;
import java.util.UUID;
import link.locutus.discord.apiv1.enums.DomesticPolicy;
import link.locutus.discord.util.TimeUtil;
public final class JteprojectsGenerated {
	public static final String JTE_NAME = "grant/projects.jte";
	public static final int[] JTE_LINE_INFO = {0,0,1,2,3,4,5,6,7,8,9,10,12,12,12,21,21,21,21,24,24,26,26,26,26,28,28,36,36,36,36,37,37,38,38,39,39,39,39,40,40,40,40,50,50,61,61,62,62,66,66,67,67,68,68,72,72,73,73,78,78,79,79,80,80,82,82,85,85,86,86,87,87,89,89,91,91,92,92,93,93,96,96,97,97,98,98,100,100,102,102,108,108,109,109,110,110,113,113,113,113,113,12,13,14,15,16,17,18,19,19,19,19};
	public static void render(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, WebStore ws, Set<Project> recommendedProjects, Collection<Grant> grants, User user, DBNation nation, Map<Grant, List<String>> failedRequirements, Map<Grant, List<String>> overrideRequirements, Map<Grant, UUID> grantTokens) {
		jteOutput.writeContent("\r\n");
		gg.jte.generated.ondemand.grant.JtegrantsGenerated.render(jteOutput, jteHtmlInterceptor, ws, "Project", new gg.jte.Content() {
			public void writeTo(gg.jte.TemplateOutput jteOutput) {
				jteOutput.writeContent("\r\n    <div class=\"row\">\r\n        <div class=\"col-lg-3\">\r\n            ");
				if (nation.projectSlots() <= nation.getNumProjects()) {
					jteOutput.writeContent("\r\n                <div class=\"alert p-1 my-1 alert-danger border border-danger alert-dismissible fade show\" role=\"alert\">\r\n                    Nation already has ");
					jteOutput.writeUserContent(nation.getNumProjects());
					jteOutput.writeContent("/");
					jteOutput.writeUserContent(nation.projectSlots());
					jteOutput.writeContent(" projects\r\n                </div>\r\n            ");
				}
				jteOutput.writeContent("\r\n            <div class=\"alert p-1 my-1 alert-info border border-info alert-dismissible fade show\" role=\"alert\">\r\n                Projects are nation level improvements that provide benefits to all your cities.<br>\r\n                <a href=\"https://politicsandwar.com/nation/projects/\" class=\"btn btn-secondary btn-sm\">Visit Projects\r\n                    Page 🡕</a>\r\n                <button type=\"button\" class=\"btn-close\" data-bs-dismiss=\"alert\" aria-label=\"Close\"></button>\r\n            </div>\r\n            <div class=\"alert p-1 my-1 alert-info border border-info alert-dismissible fade show\" role=\"alert\">\r\n                <b>City timer: </b>");
				jteOutput.writeUserContent(nation.getCityTurns());
				jteOutput.writeContent(" turns (");
				jteOutput.writeUserContent(TimeUtil.turnsToTime(nation.getCityTurns()));
				jteOutput.writeContent(")<br>\r\n                <b>Project timer: </b>");
				jteOutput.writeUserContent(nation.getProjectTurns());
				jteOutput.writeContent(" turns\r\n                (");
				jteOutput.writeUserContent(TimeUtil.turnsToTime(nation.getProjectTurns()));
				jteOutput.writeContent(")<br>\r\n                <b>Project Slots: </b>");
				jteOutput.writeUserContent(nation.getNumProjects());
				jteOutput.writeContent("/");
				jteOutput.writeUserContent(nation.projectSlots());
				jteOutput.writeContent("<br>\r\n                <b>Infrastructure: </b>");
				jteOutput.writeUserContent(nation.getInfra());
				jteOutput.writeContent(" (");
				jteOutput.writeUserContent(5000 - ((nation.getInfra() % 5000)));
				jteOutput.writeContent(" more infra until\r\n                next\r\n                slot)<br>\r\n                <p>\r\n                    You receive a slot every 5,000 total infrastructure in your nation or when your nation has won or\r\n                    lost 100\r\n                    wars.\r\n                </p>\r\n                <button type=\"button\" class=\"btn-close\" data-bs-dismiss=\"alert\" aria-label=\"Close\"></button>\r\n            </div>\r\n            ");
				if (nation.getDomesticPolicy() != DomesticPolicy.TECHNOLOGICAL_ADVANCEMENT) {
					jteOutput.writeContent("\r\n                <div class=\"alert p-1 my-1 alert-warning border border-warning alert-dismissible fade show\"\r\n                     role=\"alert\">\r\n                    <p>\r\n                        Go to the <a href=\"https://politicsandwar.com/nation/edit/\" class=\"btn btn-secondary btn-sm\">edit\r\n                            nation\r\n                            🡕</a> page to set your domestic policy to <kbd>Technological Advancement</kbd> to save 5% on\r\n                        projects\r\n                    </p>\r\n                    <button type=\"button\" class=\"btn-close\" data-bs-dismiss=\"alert\" aria-label=\"Close\"></button>\r\n                </div>\r\n            ");
				}
				jteOutput.writeContent("\r\n            ");
				if (nation.resourcesProducedProjects().containsValue(false)) {
					jteOutput.writeContent("\r\n                <div class=\"alert p-1 my-1 alert-info border border-info alert-dismissible fade show\" role=\"alert\">\r\n                    <p>Currently producing without project:</p><br>\r\n                    <ul>\r\n                        ");
					for (Map.Entry<Project,Boolean> entry : nation.resourcesProducedProjects().entrySet()) {
						jteOutput.writeContent("\r\n                            <li>");
						jteOutput.writeUserContent(entry.getKey().name());
						jteOutput.writeContent("</li>\r\n                        ");
					}
					jteOutput.writeContent("\r\n                    </ul>\r\n                    <button type=\"button\" class=\"btn-close\" data-bs-dismiss=\"alert\" aria-label=\"Close\"></button>\r\n                </div>\r\n            ");
				}
				jteOutput.writeContent("\r\n            ");
				if (nation.getCities() > 8 && nation.getCities() <= 11 && nation.projectSlots() - nation.getNumProjects() <=1 && !nation.hasProject(Projects.URBAN_PLANNING)) {
					jteOutput.writeContent("\r\n                <div class=\"alert p-1 my-1 alert-warning border border-warning alert-dismissible fade show\"\r\n                     role=\"alert\">\r\n                    The urban planning can purchased at city 11 and reduces future cities by $50M\r\n                </div>\r\n            ");
				}
				jteOutput.writeContent("\r\n            ");
				gg.jte.generated.ondemand.grant.JtenationGenerated.render(jteOutput, jteHtmlInterceptor, ws, nation);
				jteOutput.writeContent("\r\n            ");
				if (nation.getNumProjects() > 0 || !recommendedProjects.isEmpty()) {
					jteOutput.writeContent("\r\n                <div class=\"bg-white card mt-3 rounded shadow py-1\">\r\n                    ");
					if (nation.getNumProjects() > 0) {
						jteOutput.writeContent("\r\n                        <h4>Existing Projects</h4>\r\n                        <ul class=\"list-group\">\r\n                            ");
						for (Project project : nation.getProjects()) {
							jteOutput.writeContent("\r\n                                <li class=\"list-group-item\">");
							jteOutput.writeUserContent(project.name());
							jteOutput.writeContent("</li>\r\n                            ");
						}
						jteOutput.writeContent("\r\n                        </ul>\r\n                        ");
						if (!recommendedProjects.isEmpty()) {
							jteOutput.writeContent("\r\n                            <hr>\r\n                        ");
						}
						jteOutput.writeContent("\r\n                    ");
					}
					jteOutput.writeContent("\r\n                    ");
					if (!recommendedProjects.isEmpty()) {
						jteOutput.writeContent("\r\n                        <h4>Recommended Projects</h4>\r\n                        <ul class=\"list-group\">\r\n                            ");
						for (Project project : recommendedProjects) {
							jteOutput.writeContent("\r\n                                <li class=\"list-group-item\">");
							jteOutput.writeUserContent(project.name());
							jteOutput.writeContent("</li>\r\n                            ");
						}
						jteOutput.writeContent("\r\n                        </ul>\r\n                    ");
					}
					jteOutput.writeContent("\r\n                </div>\r\n            ");
				}
				jteOutput.writeContent("\r\n        </div>\r\n        <div class=\"col-lg-9\">\r\n            <div class=\"container-fluid input-group input-group-lg\">\r\n                <input class=\"form-control form-control-lg\" id=\"myInput\" type=\"text\" placeholder=\"Filter..\">\r\n            </div>\r\n            ");
				for (Grant grant : grants) {
					jteOutput.writeContent("\r\n                ");
					gg.jte.generated.ondemand.grant.JteprojectGenerated.render(jteOutput, jteHtmlInterceptor, ws, Projects.get(grant.getAmount()), grant, user, nation, failedRequirements.get(grant), overrideRequirements.get(grant), grantTokens.get(grant));
					jteOutput.writeContent("\r\n            ");
				}
				jteOutput.writeContent("\r\n        </div>\r\n    </div>\r\n");
			}
		});
	}
	public static void renderMap(gg.jte.TemplateOutput jteOutput, gg.jte.html.HtmlInterceptor jteHtmlInterceptor, java.util.Map<String, Object> params) {
		WebStore ws = (WebStore)params.get("ws");
		Set<Project> recommendedProjects = (Set<Project>)params.get("recommendedProjects");
		Collection<Grant> grants = (Collection<Grant>)params.get("grants");
		User user = (User)params.get("user");
		DBNation nation = (DBNation)params.get("nation");
		Map<Grant, List<String>> failedRequirements = (Map<Grant, List<String>>)params.get("failedRequirements");
		Map<Grant, List<String>> overrideRequirements = (Map<Grant, List<String>>)params.get("overrideRequirements");
		Map<Grant, UUID> grantTokens = (Map<Grant, UUID>)params.get("grantTokens");
		render(jteOutput, jteHtmlInterceptor, ws, recommendedProjects, grants, user, nation, failedRequirements, overrideRequirements, grantTokens);
	}
}
