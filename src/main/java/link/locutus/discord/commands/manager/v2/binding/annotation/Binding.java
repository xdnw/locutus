package link.locutus.discord.commands.manager.v2.binding.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Binding {
    String value() default "";

    String[] examples() default {};

    Class<?>[] types() default {};
}