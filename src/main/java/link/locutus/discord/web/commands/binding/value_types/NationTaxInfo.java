package link.locutus.discord.web.commands.binding.value_types;

import link.locutus.discord.apiv1.enums.NationColor;
import link.locutus.discord.db.entities.DBNation;

public class NationTaxInfo {
    public final String name;
    public final int id;
    public final int cities;
    public final int off;
    public final int def;
    public final int vm_turns;
    public final int active_m;
    public final NationColor color;
    public final long city_turns;
    public final long project_turns;
    public final int num_projects;
    public final int project_slots;
    public final double[] mmr_unit;
    public final double[] mmr_build;
    public final double avg_infra;
    public final double avg_land;

    public NationTaxInfo(DBNation nation) {
        this.name = nation.getNation();
        this.id = nation.getId();
        this.cities = nation.getCities();
        this.off = nation.getOff();
        this.def = nation.getDef();
        this.vm_turns = nation.getVm_turns();
        this.active_m = nation.active_m();
        this.color = nation.getColor();
        this.city_turns = nation.getCityTurns();
        this.project_turns = nation.getProjectTurns();
        this.num_projects = nation.getNumProjects();
        this.project_slots = nation.projectSlots();
        this.mmr_unit = nation.getMMRUnitArr();
        this.mmr_build = nation.getMMRBuildingArr();
        this.avg_infra = nation.getAvg_infra();
        this.avg_land = nation.getAvgLand();
    }
}
