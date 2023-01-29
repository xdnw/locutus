package link.locutus.discord.commands.manager.v2.impl.discord.permission;

import link.locutus.discord.db.GuildDB;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER, ElementType.METHOD})
public @interface HasKey {
    GuildDB.Key[] value() default {};
    boolean checkPermission() default true;
}
