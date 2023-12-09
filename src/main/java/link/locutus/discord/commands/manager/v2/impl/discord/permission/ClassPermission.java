package link.locutus.discord.commands.manager.v2.impl.discord.permission;

import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.config.yaml.Config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER, ElementType.METHOD})
public @interface ClassPermission {
    @Command(desc = "The class required to execute the command")
    Class[] value() default {};
}
