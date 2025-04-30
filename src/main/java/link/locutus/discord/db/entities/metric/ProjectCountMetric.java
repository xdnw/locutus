package link.locutus.discord.db.entities.metric;

import link.locutus.discord.apiv1.enums.city.project.Project;

public class ProjectCountMetric extends CountNationMetric{
    public ProjectCountMetric(Project project) {
        super(f -> f.hasProject(project) ? 1 : 0);
    }
}
