package link.locutus.discord.apiv1.enums.city;

import link.locutus.discord.apiv1.enums.Continent;
import link.locutus.discord.apiv1.enums.city.project.Project;

import java.util.function.Predicate;

public interface INationCity extends ICity {
    double getRevenueConverted();

    double[] getProfit(double[] buffer);
}
