package link.locutus.discord.commands.manager.v2.impl.discord.permission;

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
    Roles[] value() default {};

    /**
     * If to use the root guild
     * (not compatible with guild )
     * @return
     */
    boolean root() default false;

    /**
     * The guild to check roles in (default = same as command)
     * @return
     */
    long guild() default 0;

    /**
     * Only one of the provided roles are needed to pass
     * @return
     */
    boolean any() default false;

    /**
     * Whether to allow any alliance specific roles
     * Otherwise only the guild global roles are allowed
     * @return
     */
    boolean alliance() default false;
}
