package link.locutus.discord.apiv1.enums.city;

import link.locutus.discord.apiv1.enums.Continent;

public record BatchEntry(
        ICity source,
        Continent continent,
        int numCities,
        long projectBits,
        double rads,
        double grossModifier
) {}