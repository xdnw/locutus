package link.locutus.discord.db.entities;

import link.locutus.discord.apiv1.domains.subdomains.SCityContainer;

import java.sql.ResultSet;
import java.sql.SQLException;

public class CityInfraLand {
    public int cityId;
    public int nationId;
    public double infra;
    public double land;

    public CityInfraLand(ResultSet rs) throws SQLException {
        cityId = rs.getInt("id");
        nationId = rs.getInt("nation");
        infra = rs.getLong("infra") / 100d;
        land = rs.getLong("land") / 100d;
    }

    public CityInfraLand(int cityId, int nationId, double infra, double land) {
        this.cityId = cityId;
        this.nationId = nationId;
        this.infra = infra;
        this.land = land;
    }

    public CityInfraLand(SCityContainer city) {
        this(Integer.parseInt(city.getCityId()), Integer.parseInt(city.getNationId()), Double.parseDouble(city.getInfrastructure()), Double.parseDouble(city.getLand()));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CityInfraLand that = (CityInfraLand) o;

        if (cityId != that.cityId) return false;
        if (nationId != that.nationId) return false;
        if (Double.compare(that.infra, infra) != 0) return false;
        return Double.compare(that.land, land) == 0;
    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        result = cityId;
        result = 31 * result + nationId;
        temp = Double.doubleToLongBits(infra);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(land);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        return result;
    }
}
