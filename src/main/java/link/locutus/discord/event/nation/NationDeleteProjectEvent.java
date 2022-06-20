package link.locutus.discord.event.nation;

import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.db.entities.DBNation;

public class NationDeleteProjectEvent extends NationChangeEvent2 {
    private final Project project;

    public NationDeleteProjectEvent(DBNation original, DBNation changed, Project project) {
        super(original, changed);
        this.project = project;
    }

    public Project getProject() {
        return project;
    }
}
