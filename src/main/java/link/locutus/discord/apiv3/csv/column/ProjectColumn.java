package link.locutus.discord.apiv3.csv.column;

import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.apiv3.csv.header.DataHeader;
import link.locutus.discord.db.entities.DBNation;

import java.util.function.BiConsumer;

public class ProjectColumn extends BooleanColumn<DBNation> {
    private final Project project;

    public ProjectColumn(DataHeader<DBNation> header, Project project) {
        super(header, (nation, value) -> {if (value) nation.setProject(project);});
        this.project = project;
    }

    public Project getProject() {
        return project;
    }
}
