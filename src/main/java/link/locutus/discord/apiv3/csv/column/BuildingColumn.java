package link.locutus.discord.apiv3.csv.column;

import link.locutus.discord.apiv1.enums.city.building.Building;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv3.csv.header.DataHeader;
import link.locutus.discord.db.entities.DBCity;
import link.locutus.discord.db.entities.DBNation;

public class BuildingColumn extends ByteColumn<DBCity> {
    private final Building building;

    public BuildingColumn(DataHeader<DBCity> header, Building building) {
        super(header, (city, value) -> city.setBuilding(building, value));
        this.building = building;
    }

    public Building getBuilding() {
        return building;
    }
}
