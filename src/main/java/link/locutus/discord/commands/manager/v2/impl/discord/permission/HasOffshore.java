package link.locutus.discord.commands.manager.v2.impl.discord.permission;

import link.locutus.discord.commands.manager.v2.binding.annotation.Command;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER, ElementType.METHOD})
public @interface HasOffshore {
    @Command(desc = "A list of alliance IDs allowed as the offshore (default = any)")
    long[] value() default {};
}
