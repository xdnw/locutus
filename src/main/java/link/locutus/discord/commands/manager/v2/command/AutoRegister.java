package link.locutus.discord.commands.manager.v2.command;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface AutoRegister {
    Class clazz();
    String field() default "";
    String method();
}
