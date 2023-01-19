package link.locutus.discord.commands.manager.v2.impl.discord.permission;

import link.locutus.discord.user.Roles;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER, ElementType.METHOD})
public @interface RolePermission {
    Roles[] value() default {};
    boolean root() default false;
    long guild() default 0;
    boolean any() default false;
    boolean alliance() default false;
}
