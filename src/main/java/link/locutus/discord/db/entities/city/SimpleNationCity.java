package link.locutus.discord.db.entities.city;

import com.politicsandwar.graphql.model.City;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.city.ICity;
import link.locutus.discord.apiv1.enums.city.INationCity;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import link.locutus.discord.db.entities.DBCity;
import link.locutus.discord.util.PW;
import link.locutus.discord.util.TimeUtil;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

public class SimpleNationCity extends SimpleDBCity implements INationCity {
    private final BiConsumer<DBCity, double[]> getRevenue;
    private final ToDoubleFunction<DBCity> getRevenueConverted;
    public SimpleNationCity(DBCity copy, BiConsumer<DBCity, double[]> getRevenue, ToDoubleFunction<DBCity> getRevenueConverted) {
        super(copy);
        this.getRevenue = getRevenue;
        this.getRevenueConverted = getRevenueConverted;
    }

    @Override
    public double getRevenueConverted() {
        return this.getRevenueConverted.applyAsDouble(this);
    }

    @Override
    public double[] getProfit(double[] buffer) {
        if (buffer == null) {
            buffer = ResourceType.getBuffer();
        }
        getRevenue.accept(this, buffer);
        return buffer;
    }
}
