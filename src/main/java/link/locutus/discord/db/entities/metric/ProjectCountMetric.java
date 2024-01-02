package link.locutus.discord.db.entities.metric;

import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.db.entities.DBNation;

import java.util.function.Function;

public class ProjectCountMetric extends CountNationMetric{
    public ProjectCountMetric(Project project) {
        super(f -> f.hasProject(project) ? 1 : 0);
    }
}
