package link.locutus.discord.commands.manager.v2.impl.discord.permission;

import link.locutus.discord.apiv3.enums.AlliancePermission;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER, ElementType.METHOD})
public @interface CmdAlliancePermission {
    @Command(desc = "The alliance permissions required")
    AlliancePermission[] value() default {};

    boolean any() default false;
}
