package link.locutus.discord.commands.manager.v2.impl.discord.permission;

import link.locutus.discord.apiv1.enums.Rank;
import link.locutus.discord.apiv3.enums.AlliancePermission;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER, ElementType.METHOD})
public @interface IsAuthenticated {
    AlliancePermission[] value() default {};
}
