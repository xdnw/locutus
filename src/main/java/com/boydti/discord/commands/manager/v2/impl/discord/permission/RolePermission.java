package com.boydti.discord.commands.manager.v2.impl.discord.permission;

import com.boydti.discord.Locutus;
import com.boydti.discord.db.GuildDB;
import com.boydti.discord.user.Roles;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;

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
}
