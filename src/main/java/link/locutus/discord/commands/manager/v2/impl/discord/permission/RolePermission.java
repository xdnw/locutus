package link.locutus.discord.commands.manager.v2.impl.discord.permission;

import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.user.Roles;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER, ElementType.METHOD})
public @interface RolePermission {
    /**
     * The roles required to execute the command.
     * (all are required unless any = true)
     * @return
     */
    @Command(desc = "The roles required to execute the command\n(all are required unless any = true)")
    Roles[] value() default {};

    /**
     * If to use the root guild
     * (not compatible with guild )
     * @return
     */
    @Command(desc = "If to use the root guild\n(not compatible with guild )")
    boolean root() default false;

    /**
     * The guild to check roles in (default = same as command)
     * @return
     */
    @Command(desc = "The guild to check roles in (default = same as command)")
    long guild() default 0;

    /**
     * Only one of the provided roles are needed to pass
     * @return
     */
    @Command(desc = "Only one of the provided roles are needed to pass")
    boolean any() default false;

    /**
     * Whether to allow any alliance specific roles
     * Otherwise only the guild global roles are allowed
     * @return
     */
    @Command(desc = "Whether to allow any alliance specific roles\nOtherwise only the guild global roles are allowed")
    boolean alliance() default false;

    @Command(desc = "Only if this role is required if running in a guild that has a registered alliance")
    boolean onlyInGuildAlliance() default false;
}
