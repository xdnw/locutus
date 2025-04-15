package link.locutus.discord.apiv1.enums;

import link.locutus.discord.apiv1.domains.subdomains.attack.v3.AbstractCursor;
import link.locutus.discord.db.entities.DBWar;
import link.locutus.discord.util.scheduler.TriFunction;

import java.util.function.BiFunction;
import java.util.function.Function;

public enum RssConvertMode {
    ALL,
    NEGATIVE,
}
