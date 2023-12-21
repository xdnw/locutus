package link.locutus.discord.apiv1.enums;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.city.building.Building;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Timestamp;
import link.locutus.discord.commands.manager.v2.impl.pw.NationFilter;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.NationAttributeDouble;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.util.trade.TradeManager;
import org.apache.commons.lang3.text.WordUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

import static link.locutus.discord.apiv1.enums.ResourceType.BAUXITE;
import static link.locutus.discord.apiv1.enums.ResourceType.COAL;
import static link.locutus.discord.apiv1.enums.ResourceType.FOOD;
import static link.locutus.discord.apiv1.enums.ResourceType.IRON;
import static link.locutus.discord.apiv1.enums.ResourceType.LEAD;
import static link.locutus.discord.apiv1.enums.ResourceType.OIL;
import static link.locutus.discord.apiv1.enums.ResourceType.URANIUM;

public enum BuildingType {
    POWER,
    RAW,
    MANUFACTURING,
    CIVIL,
    COMMERCE,
    MILITARY,

    ;

}
