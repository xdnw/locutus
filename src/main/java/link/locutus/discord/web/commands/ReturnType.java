package link.locutus.discord.web.commands;

import link.locutus.discord.web.commands.binding.value_types.CacheType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface ReturnType {
    Class value();
    CacheType cache() default CacheType.None;
    long duration() default 2592000;

}

