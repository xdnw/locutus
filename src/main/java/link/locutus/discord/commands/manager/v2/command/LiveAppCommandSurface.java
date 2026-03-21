package link.locutus.discord.commands.manager.v2.command;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks command/helper types that intentionally remain on the live app command surface.
 *
 * <p>Neutral runtime, placeholder, and binding seams must not depend on these types directly.
 * The annotation exists to make the remaining app-only ownership explicit while adjacent runtime
 * slices are migrated away from singleton-backed helpers.</p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface LiveAppCommandSurface {
    String value() default "";
}